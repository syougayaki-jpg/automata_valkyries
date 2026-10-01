package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 終焉執行統合エンジン（Terminus Engine）。
 * <p>
 * MOB（兵姫等）およびアイテム（終焉の兵装等）から呼び出され、
 * チートMOB（Witherzilla, Metapotent Flashfur等）の汎用解体および
 * プレイヤー（クリエイティブ・無敵・チート防具着用含む）の絶対即死・終焉を執行する。
 * <p>
 * 終焉執行に対象が10秒（200 ticks / 10,000ms）以上耐えた場合、
 * Eprotect+ の強化処理を10秒間発動する。
 * </p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminusEngine {

    /** 終焉耐性判定の閾値（ミリ秒: 10,000ms = 10秒） */
    public static final long RESISTANCE_THRESHOLD_MS = 10_000L;
    /** 終焉耐性判定の閾値（Ticks: 200 ticks = 10秒） */
    public static final long RESISTANCE_THRESHOLD_TICKS = 200L;

    public static final class TargetRecord {
        public final UUID uuid;
        public final WeakReference<Entity> entityRef;
        public final long startTick;
        public final long startTimeMs;
        public volatile boolean reinforcementTriggered = false;

        public TargetRecord(Entity entity, long startTick, long startTimeMs) {
            this.uuid = entity.getUUID();
            this.entityRef = new WeakReference<>(entity);
            this.startTick = startTick;
            this.startTimeMs = startTimeMs;
        }

        public Entity getEntity() {
            return entityRef.get();
        }
    }

    private static final Map<UUID, TargetRecord> TRACKED_TARGETS = new ConcurrentHashMap<>();

    private TerminusEngine() {}

    /**
     * 単一の対象（プレイヤーまたはMOB）に対して終焉を執行する。
     *
     * @param level    サーバーワールド
     * @param attacker 攻撃者（プレイヤーまたはMOB）
     * @param target   対象エンティティ
     * @return 終焉執行に成功したか
     */
    public static boolean executeTerminus(ServerLevel level, Entity attacker, Entity target) {
        if (target == null) return false;

        // 味方の保護対象（兵姫など）は誤爆防止
        if (target instanceof IEprotected) {
            return false;
        }

        // 終焉耐性トラッキングに対象を登録
        trackTarget(target, level);

        Player playerAttacker = attacker instanceof Player p ? p : null;

        // 1. プレイヤー対象の場合: TerminusPlayerExecutor による絶対死執行
        if (target instanceof ServerPlayer serverPlayer) {
            return TerminusPlayerExecutor.executeOnPlayer(level, playerAttacker, serverPlayer);
        }

        // 2. MOB 対象の場合: 汎用チート解体 + Eprotect+ 真抹殺執行
        GenericCheatMobNeutralizer.neutralize(level, target);
        return TrueAnnihilationEngine.annihilateEntity(level, playerAttacker, target);
    }

    /**
     * 範囲内の全エンティティに対して終焉を執行する。
     *
     * @param level          サーバーワールド
     * @param attacker       発動者
     * @param radius         半径（ブロック）
     * @param includePlayers プレイヤーも終焉対象に含めるか
     * @return 終焉執行したエンティティ数
     */
    public static int executeAreaTerminus(ServerLevel level, Entity attacker, double radius, boolean includePlayers) {
        AABB box = attacker.getBoundingBox().inflate(radius);
        List<Entity> targets = level.getEntities(attacker, box, e -> {
            if (e == attacker) return false;
            if (e instanceof IEprotected) return false;
            if (e instanceof Player && !includePlayers) return false;
            return e.isAlive() || e instanceof LivingEntity;
        });

        int count = 0;
        for (Entity target : targets) {
            if (executeTerminus(level, attacker, target)) {
                count++;
            }
        }

        // 範囲抹殺後の一括クリーンアップ
        AnnihilationExecutionAgent.wipeFlashfurLevelMemory();
        AnnihilationExecutionAgent.wipeFlashfurDiskSaves(level);
        AnnihilationExecutionAgent.wipeOmniMobsInternalLists();
        AnnihilationExecutionAgent.sanitizeEventBus();

        level.getLevelData().setRaining(false);
        level.setRainLevel(0.0F);
        level.setThunderLevel(0.0F);

        AutomataValkyries.LOGGER.info("[TerminusEngine] Area terminus executed by {} in radius {}: {} eliminated",
                attacker.getName().getString(), radius, count);
        return count;
    }

    // ---- 終焉耐性追跡・Eprotect+ 強化処理発動 ----------------------------------

    public static void trackTarget(Entity target, ServerLevel level) {
        if (target == null) return;
        long currentTick = level != null && level.getServer() != null ? level.getServer().getTickCount() : 0;
        TRACKED_TARGETS.put(target.getUUID(), new TargetRecord(target, currentTick, System.currentTimeMillis()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        checkResistanceAll();
    }

    /**
     * 終焉執行対象の生存状況を点検し、10秒以上耐えている対象がいれば Eprotect+ 強化処理を10秒間実行する。
     */
    public static void checkResistanceAll() {
        if (TRACKED_TARGETS.isEmpty()) return;
        long nowMs = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, TargetRecord>> it = TRACKED_TARGETS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TargetRecord> entry = it.next();
            TargetRecord record = entry.getValue();
            Entity entity = record.getEntity();

            // 対象が消滅（GC 済み・ワールドから削除・非生存）している場合は追跡解除
            if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                it.remove();
                continue;
            }

            long elapsedMs = nowMs - record.startTimeMs;
            long currentTick = entity.level().getServer() != null ? entity.level().getServer().getTickCount() : 0;
            long elapsedTicks = currentTick > 0 ? (currentTick - record.startTick) : (elapsedMs / 50L);

            // 10秒（200 ticks または 10,000ms）以上耐えている場合
            if ((elapsedTicks >= RESISTANCE_THRESHOLD_TICKS || elapsedMs >= RESISTANCE_THRESHOLD_MS)
                    && !record.reinforcementTriggered) {
                record.reinforcementTriggered = true;
                // Eprotect+ 強化処理を 10 秒間実行！
                EprotectPlusLink.triggerTerminusResistedReinforcement(entity);
                AutomataValkyries.LOGGER.warn("[TerminusEngine] Entity {} resisted Terminus for >= 10s (elapsed: {} ticks, {} ms)! Triggered Eprotect+ 10-second reinforced protection.",
                        entity.getName().getString(), elapsedTicks, elapsedMs);
            }
        }
    }

    /**
     * 手動またはシミュレーションで特定対象の耐性強化処理を発動させる（テスト用）。
     */
    public static boolean triggerResistedManually(Entity target) {
        if (target == null) return false;
        TargetRecord record = TRACKED_TARGETS.get(target.getUUID());
        if (record != null) {
            record.reinforcementTriggered = true;
        }
        EprotectPlusLink.triggerTerminusResistedReinforcement(target);
        return true;
    }

    public static boolean hasTrackedTarget(UUID uuid) {
        return TRACKED_TARGETS.containsKey(uuid);
    }

    public static int getTrackedTargetCount() {
        return TRACKED_TARGETS.size();
    }

    public static void clearTrackedTargets() {
        TRACKED_TARGETS.clear();
    }
}
