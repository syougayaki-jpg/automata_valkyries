package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityTickListAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.mixin.TrackedEntityAccessor;
import com.automatavalkyries.protect.attack.AttackLog;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTickList;

/**
 * {@link Eprotect} の Tick 停止対策: 本体の Tick が止まっていれば Tick 対象の一覧へ入れ直すか、ワールドの代わりに回す。
 * 時間窓はサーバーの Tick 数で測る（{@link Eprotect#serverClock}）
 */
final class TickStallGuard {

    /** 本体の Tick がこの Tick 数より長く止まっていたら Tick 停止とみなす */
    private static final long STALL_THRESHOLD = 3;

    private final Eprotect protect;
    private final LivingEntity owner;
    /** 最後に本体の Tick が走ったサーバーの Tick 数 */
    private long lastTick = Long.MIN_VALUE;
    /** ワールドの Tick ごと止められている（時間停止など）ので、本体の Tick を代わりに回している最中か */
    private boolean driving = false;
    /** 代わりに回している本体の Tick の実行中か（その中からの再入を防ぐ） */
    private boolean insideDriven = false;
    /** {@link Eprotect#tick()} が走った回数（代わりに回した Tick の中身まで届いたかを見分ける） */
    private int ownTickRuns = 0;
    /** この Tick 停止で、入口で止められていることを報告済みか */
    private boolean blockReported = false;
    /** 包みの入口を飛ばして回したことを記録したか（記録は 1 個体 1 回） */
    private boolean entranceBypassNoted;

    TickStallGuard(Eprotect protect) {
        this.protect = protect;
        this.owner = protect.getOwner();
    }

    /** ワールドの Tick ごと止められている（時間停止など）ので、本体の Tick を代わりに回している最中か（{@link TimeStopExemption} が免除を探す合図） */
    boolean isDriving() {
        return driving;
    }

    /** {@link Eprotect#tick()} の入口で毎回 */
    void countOwnTick() {
        ownTickRuns++;
    }

    /** 本体の Tick が走った（ワールドへの追加直後もここを起点にする） */
    void markTicked() {
        lastTick = protect.serverClock();
    }

    /**
     * ワールドには正しく載っているのに本体の Tick が止まっていれば、
     * <ul>
     *   <li>Tick 対象一覧から外されている → 一覧へ入れ直す（修復として数える）</li>
     *   <li>一覧には載っている → ワールドの Tick ごと止められている（時間停止など）か、他 MOD が反復から読み飛ばしている。
     *       入れ直しても効かないので、止まっている間は毎 Tick ここから本体の Tick を代わりに回す（修復には数えない。
     *       数えると時間停止が続くだけで退避してしまう）</li>
     * </ul>
     * {@link Eprotect#tick()} が走らない状態なので、マネージャーの周期処理（サーバー Tick の最後）から呼ばれる。
     *
     * @return 一覧へ入れ直したとき true
     */
    boolean restoreIfStopped() {
        // isRemoved() は他 MOD の ASM に呼び出し箇所ごと偽装されうるので、フィールドを直接読む
        if (!(owner.level() instanceof ServerLevel level) || ((EntityAccessor) owner).automata_valkyries$getRemovalReason() != null) return false;
        if (insideDriven) return false;
        if (com.automatavalkyries.entity.chrono.TimeStop.frozen(owner)) { // タイムルーラーの時間停止（このMOD の正当な停止）は打ち消さない
            markTicked();
            return false;
        }
        if (!Eprotect.isSafePosition(owner, owner.getX(), owner.getY(), owner.getZ())) return false;
        if (lastTick == Long.MIN_VALUE) return false; // まだワールドに追加されていない
        long stalled = protect.serverClock() - lastTick;
        if (stalled == 0) {
            // このサーバー Tick のうちに本体の Tick が自然に走った（止められていない）
            if (driving) {
                driving = false;
                blockReported = false;
                AutomataValkyries.LOGGER.info("[eprotect] {} is ticking on its own again", owner);
            }
            return false;
        }
        if (!driving && stalled <= STALL_THRESHOLD) return false;
        if (!level.isPositionEntityTicking(owner.blockPosition())) return false; // チャンク側の都合で止まっているだけ

        EntityTickList tickList = ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList();
        if (!((EntityTickListAccessor) tickList).automata_valkyries$getActive().containsKey(owner.getId())) {
            driving = false;
            tickList.add(owner);
            markTicked(); // 復帰の Tick が走るまで連続で戻さない
            AutomataValkyries.LOGGER.warn("[eprotect] Restored ticking of {}", owner);
            AttackLog.repaired(owner, "ticking");
            return true;
        }

        if (!driving) {
            driving = true;
            AutomataValkyries.LOGGER.warn("[eprotect] {} is in the tick list but is not being ticked (time stop?); ticking it directly", owner);
            AttackLog.repaired(owner, "frozen_tick");
        }
        drive(level);
        return false;
    }

    /** 本体の Tick をワールドの代わりに 1 回回す（騎乗中は乗り物が回すので触らない） */
    private void drive(ServerLevel level) {
        Entity vehicle = owner.getVehicle();
        if (vehicle != null && !vehicle.isRemoved() && vehicle.hasPassenger(owner)) return;
        int ticksBefore = owner.tickCount;
        int runsBefore = ownTickRuns;
        insideDriven = true;
        try {
            level.guardEntityTick(level::tickNonPassenger, owner);
            if (owner.tickCount == ticksBefore && !owner.isRemoved()) {
                // 包み（guardEntityTick）の入口で他 MOD に止められた（EndingLibrary の時間停止の LevelMixin.guardEntityTick 等）。
                // 包みを飛ばして中身を直接回す
                level.tickNonPassenger(owner);
                if (owner.tickCount != ticksBefore && !entranceBypassNoted) {
                    entranceBypassNoted = true;
                    AttackLog.repaired(owner, "frozen_tick:entrance_bypassed");
                }
            }
        } finally {
            insideDriven = false;
        }
        // 代わりに回しても進まない＝その入口で他 MOD に打ち切られている。打ち切れる処理を候補として 1 度だけ報告する
        // （tickCount は tickNonPassenger の先頭で進む。進んだのに tick() まで届かなければ、エンティティのクラスの tick で止められている）
        if (!blockReported) {
            TickEntranceHooks.Stage stage = owner.tickCount == ticksBefore ? TickEntranceHooks.Stage.ENTRANCE
                    : owner instanceof IEprotected && ownTickRuns == runsBefore ? TickEntranceHooks.Stage.ENTITY_TICK : null;
            if (stage != null) {
                blockReported = true;
                TickEntranceHooks.report(owner, stage);
            }
        }
        sendTrackedChanges(level);
        lastTick = protect.serverClock() - 1; // 次のサーバー Tick で自然に走ったかを見分けられるよう、自分で回した分は数えない
    }

    /**
     * 代わりに回した Tick の移動・状態の変化をクライアントへ送る（ChunkMap.tick の 1 体ぶん）。
     * 送るのは本来ワールドの Tick（ServerChunkCache.tick → ChunkMap.tick）の役目なので、ワールドの Tick ごと止められていると
     * サーバーでは動いているのにクライアントでは止まって見える。
     */
    private void sendTrackedChanges(ServerLevel level) {
        Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap().get(owner.getId());
        if (!(tracked instanceof TrackedEntityAccessor entry)) return;
        SectionPos section = SectionPos.of(owner);
        if (!section.equals(entry.automata_valkyries$getLastSectionPos())) {
            entry.automata_valkyries$updatePlayers(level.players());
            entry.automata_valkyries$setLastSectionPos(section);
        }
        entry.automata_valkyries$getServerEntity().sendChanges();
    }
}
