package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityManagerAccessor;
import com.automatavalkyries.mixin.EntityTickListAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.mixin.TrackedEntityAccessor;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackOp;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.attack.ModBlacklist;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * 個体ごとの保護コントローラー（eprotect）。
 *
 * <ul>
 *   <li>不正な消去（KILLED / DISCARDED）の拒否判定</li>
 *   <li>異常ダメージのサニタイズと 1 撃あたりの上限</li>
 *   <li>HP 異常・死亡フラグ・消去フラグの自己修復</li>
 *   <li>Tick 対象から外された場合の復帰（Tick 停止対策）</li>
 *   <li>現在いるチャンクの強制ロード</li>
 *   <li>正規退場（{@link #triggerDespawn()}）</li>
 * </ul>
 *
 * 所有エンティティは {@link IEprotected}（セリフが要るなら {@link IProviderWithSpeech}）を実装し、以下を委譲すること:
 * {@code onAddedToWorld} / {@code onRemovedFromWorld} / {@code baseTick}（サーバー側）/
 * {@code remove}（{@link #shouldBlockRemoval} で拒否）/ {@code die}（{@link #canDie} で拒否、許可時は {@link #markDefeated}）。
 */
public class Eprotect {

    /** これ以上（または NaN / 無限大）の単発ダメージは異常値とみなす */
    public static final float HUGE_DAMAGE_THRESHOLD = 1000.0F;
    /** 異常値を置き換えるダメージ */
    public static final float HUGE_DAMAGE_CLAMP = 100.0F;
    /** 実際に HP から引かれる 1 撃のダメージ上限（最大 HP に対する割合） */
    public static final float MAX_HIT_RATIO = 0.10F;
    /**
     * 被弾量を数える時間窓（Tick）。この間に HP から引かれるのは合計で 1 撃の上限（{@link #MAX_HIT_RATIO}）まで。
     * 無敵時間を消して hurt() を連打すると、1 撃ごとの上限だけでは 1 Tick のうちに正規撃破まで削られる
     */
    public static final int DAMAGE_WINDOW_TICKS = 200;
    /** ブラックリストの MOD からのダメージの上限（罰則。{@link #MAX_HIT_RATIO} の代わりに使う） */
    public static final float BLACKLIST_HIT_RATIO = 0.01F;
    /** 本体の Tick がこの Tick 数より長く止まっていたら Tick 停止とみなす */
    private static final long TICK_STALL_THRESHOLD = 3;

    private final LivingEntity owner;
    /** 正規退場中（消去を許可する） */
    private boolean despawning = false;
    /** 正規の戦闘で倒された（死亡を許可する。消去は撃破演出が終わるまで保留） */
    private boolean defeated = false;
    /** 撃破演出が終わった（ここで初めて消去を許可する） */
    private boolean deathFinished = false;
    /** hurt() の実行中か。この間の die() だけを正規の死亡として扱う */
    private int hurtDepth = 0;
    private float lastValidHealth = -1.0F;
    /** 被弾量の時間窓の開始時刻と、その窓で既に引いた量 */
    private long damageWindowStart = -DAMAGE_WINDOW_TICKS;
    private float damageInWindow = 0.0F;
    /** 入口の判定を通った次元移動の最中か */
    private boolean dimensionChangeApproved = false;
    /** 最後に本体の Tick が走ったサーバーの Tick 数（Tick 停止の検出用） */
    private long lastTickServerTick = Long.MIN_VALUE;
    /** ワールドの Tick ごと止められている（時間停止など）ので、本体の Tick を代わりに回している最中か */
    private boolean drivingFrozenTick = false;
    /** 代わりに回している本体の Tick の実行中か（その中からの再入を防ぐ） */
    private boolean insideDrivenTick = false;
    /** {@link #tick()} が走った回数（代わりに回した Tick の中身まで届いたかを見分ける） */
    private int ownTickRuns = 0;
    /** この Tick 停止で、入口で止められていることを報告済みか */
    private boolean tickBlockReported = false;

    /** 最後に確認した正常な座標（入り直しの際、壊された座標を戻す先） */
    @Nullable
    private Vec3 lastSafePos;

    @Nullable
    private ServerLevel forcedLevel;
    @Nullable
    private ChunkPos forcedChunk;

    public Eprotect(LivingEntity owner) {
        this.owner = owner;
    }

    /**
     * 座標として正常か（NaN / 無限大 / ワールド境界の遥か外 / 奈落を弾く）。
     * 奈落はそのワールドの最低高度 - 64（バニラの奈落ダメージが始まる高さ）より下。固定値だと (-999, -999, -999) のような
     * 「ぎりぎり有限」の飛ばし先をすり抜けるので、ワールドの高さで判定する。
     */
    public static boolean isSafePosition(Entity entity, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        return Math.abs(x) <= 3.0E7D && Math.abs(z) <= 3.0E7D
                && y >= entity.level().getMinBuildHeight() - 64 && y <= 2.0E7D;
    }

    public LivingEntity getOwner() {
        return owner;
    }

    /** 保護が有効か（正規退場・正規撃破の後は無効） */
    public boolean isProtectionActive() {
        return !despawning && !defeated;
    }

    /** Eprotect+ 強化処理モード（ブラックリストMOD検知または終焉耐性により発動）が有効か */
    public boolean isReinforcedActive() {
        return com.automatavalkyries.protect.plus.EprotectPlusLink.isReinforcedActive();
    }

    /** 撃破演出の最中か（倒されたが、まだ消去してはいけない） */
    public boolean isDying() {
        return defeated && !deathFinished && !despawning;
    }

    /** 消去を拒否する状態か（保護中、または撃破演出の最中） */
    public boolean isGuardingRemoval() {
        return isProtectionActive() || isDying();
    }

    public boolean isDespawning() {
        return despawning;
    }

    public boolean isDefeated() {
        return defeated;
    }

    public float getLastValidHealth() {
        return lastValidHealth > 0.0F ? lastValidHealth : owner.getMaxHealth();
    }

    public int getHurtDepth() {
        return hurtDepth;
    }

    /**
     * 現在、正規のダメージ処理中であり、かつ被弾前の体力が1撃で倒せる残量（最大HPの10%）以下かどうか
     */
    public boolean isLegitimateFatalBlow() {
        float maxSingleHit = owner.getMaxHealth() * MAX_HIT_RATIO + 1.0F;
        return hurtDepth > 0 && lastValidHealth <= maxSingleHit;
    }

    /**
     * 時間窓に使う時計。ワールドのゲーム時刻ではなくサーバーの Tick 数を使う。
     * 時間停止（No-Sugar のストップウォッチ等）はワールドの Tick ごと止めるのでゲーム時刻も止まり、
     * ゲーム時刻で測ると被弾・修復の時間窓が明けず、Tick 停止も検出できない。
     */
    private long serverClock() {
        return owner.level().getServer() != null ? owner.level().getServer().getTickCount() : owner.level().getGameTime();
    }

    // ---- ライフサイクル ----------------------------------------------------

    /**
     * 保護スタート（マネージャー登録）。
     * チャンクロックはここでは取らず、最初の {@link #tick()} に任せる。forceChunk はチャンクを同期ロードするため、
     * エンティティの読み込み処理の最中（特にサーバー終了時の saveAll）に呼ぶとサーバースレッドが自分を待ち続けて固まる。
     */
    public void onAddedToWorld() {
        if (!(owner.level() instanceof ServerLevel)) return;
        EprotectManager.getInstance().register(this);
        lastTickServerTick = serverClock(); // 追加直後に Tick 停止されても検出できるよう、ここを起点にする
        rememberHealthyState(); // 最初の Tick より前に飛ばされても戻す先があるように
    }

    public void onRemovedFromWorld() {
        if (!(owner.level() instanceof ServerLevel)) return;
        Entity.RemovalReason reason = DirectRead.removalReason(owner);
        // Mixin をすり抜けた不正消去: マネージャーが復元できるよう登録とチャンクロックを維持する
        if (isGuardingRemoval() && isIllegalRemoval(owner, reason)) return;
        // 次元移動: 移動先で同じ UUID の個体が改めて登録されるので、元の次元の記録は消す（残すと元の次元で復元されて複製になる）
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) registry().forget(owner.getUUID());
        releaseForcedChunk();
        EprotectManager.getInstance().unregister(this);
    }

    /** 毎 Tick（サーバー側）に実行する自己修復 */
    public void tick() {
        ownTickRuns++;
        hurtDepth = 0; // hurt() 中に例外が出てカウンタが残った場合の保険
        dimensionChangeApproved = false; // 次元移動の途中で例外が出て許可が残った場合の保険
        if (!isGuardingRemoval()) return;
        // onRemovedFromWorld はチャンクの可視性低下（トラッキング終了）でも呼ばれるが、
        // 再トラッキング時に onAddedToWorld は呼ばれないため、ここで登録を維持する
        EprotectManager.getInstance().register(this);
        lastTickServerTick = serverClock();
        repair();
        if (isProtectionActive()) {
            rememberHealthyState();
            recordToRegistryIfDue();
            updateForcedChunk();
        }
    }

    // ---- 消去・死亡の判定 ----------------------------------------------------

    /**
     * 消去を拒否すべきか。チャンクのアンロードや次元移動は妨げない。
     * 撃破演出中も拒否し、演出の最後（{@link #finishDeath()} の後）の消去だけを通す。
     * <p>判定はサーバー側だけ。撃破・退場の状態はサーバーにしか無いので、クライアントで拒否すると
     * サーバーから消えた後も画面に残り続ける（クライアントはサーバーの消去通知に必ず従う）。</p>
     */
    public boolean shouldBlockRemoval(Entity.RemovalReason reason) {
        if (owner.level().isClientSide || !isGuardingRemoval()) return false;
        if (isIllegalRemoval(owner, reason)) {
            AttackLog.refused(owner, AttackOp.REMOVE + ":" + reason.name().toLowerCase(Locale.ROOT));
            // 消去と一緒にクライアントへ「消した」と送る MOD がある（Fantasy Ending の DeleteEntityPacket）。サーバーでは残っても
            // 画面から消え、追跡の後片付け（stopSeenByPlayer）でボスバーも外れるので、次の Tick に見せ直す
            viewersResyncPending = true;
            return true;
        }
        // 次元移動を装った消去: 正規の次元移動（{@link #approveDimensionChange} 中）以外で、ブラックリストの MOD が直接呼んだもの
        return reason == Entity.RemovalReason.CHANGED_DIMENSION && !dimensionChangeApproved
                && blacklistedCaller(AttackOp.REMOVE + ":changed_dimension", true);
    }

    /**
     * ブラックリストの MOD が直接の呼び出し元なら記録して true（即死系の操作の拒否に使う。ブラックリストが空ならスタックを辿らない）。
     *
     * @param dimensionOp 次元移動・次元移動を装った消去か（設定 strictRemoval なら、呼び出し経路のどこかに載った MOD がいれば拒否）
     */
    public boolean blacklistedCaller(String op, boolean dimensionOp) {
        if (owner.level().isClientSide || !isProtectionActive() || !ModBlacklist.isActive()) return false;
        String mod = CallerAttribution.immediateCallerMod(owner);
        if (!ModBlacklist.contains(mod) && dimensionOp && EprotectConfig.get(EprotectConfig.BLACKLIST_STRICT_REMOVAL)) {
            CallerAttribution.Attribution a = CallerAttribution.attribute(owner);
            if (ModBlacklist.contains(a.foreignMod())) mod = a.foreignMod();
            else if (ModBlacklist.contains(a.otherForeign())) mod = a.otherForeign();
        }
        if (!ModBlacklist.contains(mod)) return false;
        AttackLog.blocked(owner, op, mod);
        return true;
    }

    /** 次元移動を許可した（入口の判定を通った）間は、途中の setRemoved(CHANGED_DIMENSION) を止めない（止めると複製が残る） */
    public void approveDimensionChange(boolean approved) {
        this.dimensionChangeApproved = approved;
    }

    /**
     * 保護対象にとって不正な消去理由か（サーバー側）。
     * <ul>
     *   <li>消去を伴う理由（KILLED / DISCARDED）</li>
     *   <li>UNLOADED_TO_CHUNK: 正規のアンロードは区画が HIDDEN になったチャンクでしか起きない
     *       （{@code PersistentEntitySectionManager.processChunkUnload}）。見えているチャンクでのアンロードは偽装。
     *       アンロード扱いのまま区画から外れると保存されず、UUID の登録だけが残って登録簿からの復元も止まる</li>
     *   <li>UNLOADED_WITH_PLAYER: プレイヤーの退出で一緒に外れるのは、乗り物か乗客になっている個体だけ</li>
     * </ul>
     * CHANGED_DIMENSION は次元移動として通す。
     */
    public static boolean isIllegalRemoval(Entity entity, @Nullable Entity.RemovalReason reason) {
        if (reason == null) return false;
        if (reason.shouldDestroy()) return true;
        if (!(entity.level() instanceof ServerLevel level)) return false;
        return switch (reason) {
            case UNLOADED_TO_CHUNK -> chunkVisibility(level, entity) != Visibility.HIDDEN;
            case UNLOADED_WITH_PLAYER -> !entity.isPassenger() && !entity.isVehicle();
            default -> false;
        };
    }

    private static Visibility chunkVisibility(ServerLevel level, Entity entity) {
        @SuppressWarnings("unchecked")
        EntityManagerAccessor<Entity> manager = (EntityManagerAccessor<Entity>) (Object)
                ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();
        return manager.automata_valkyries$getChunkVisibility().get(entity.chunkPosition().toLong());
    }

    /** 撃破演出の終わりに所有エンティティが呼ぶ。以後の消去を許可する */
    public void finishDeath() {
        this.deathFinished = true;
        if (owner.level() instanceof ServerLevel) registry().forget(owner.getUUID());
    }

    /** die() の直接呼び出し（即死攻撃）を拒否したことを記録する（所有エンティティの die() と Mixin から呼ぶ） */
    public void noteRefusedDeath() {
        AttackLog.refused(owner, AttackOp.DIE);
    }

    /** hurt() 経由で HP が尽きた場合だけ死亡を許可する（die() の直接呼び出しは拒否） */
    public boolean canDie() {
        if (!isProtectionActive()) return true;
        // 正規の hurt 処理中であり、被弾前の体力が1撃で倒せる残量（最大HPの10%）以下で、
        // かつ現在のHPが本当に 0 以下になった場合のみ許可する
        return isLegitimateFatalBlow() && !(DirectRead.health(owner) > 0.0F);
    }

    public void markDefeated() {
        this.defeated = true;
        if (owner.level() instanceof ServerLevel) registry().markDefeated(owner.getUUID());
    }

    public void beginHurt() {
        hurtDepth++;
    }

    public void endHurt() {
        if (hurtDepth > 0) hurtDepth--;
        // 正規の被弾で減った HP を「直前の正常値」として記録（同 Tick 内の即死ハックで被弾前に戻らないように）
        float hp = DirectRead.health(owner);
        if (Float.isFinite(hp) && hp > 0.0F) lastValidHealth = hp;
    }

    // ---- ダメージ ------------------------------------------------------------

    /** hurt() の入口: NaN / 無限大 / 桁外れの値を {@link #HUGE_DAMAGE_CLAMP} に置き換える */
    public float sanitizeIncomingDamage(float amount) {
        if (!isProtectionActive()) return amount;
        if (!Float.isFinite(amount) || amount >= HUGE_DAMAGE_THRESHOLD) {
            return HUGE_DAMAGE_CLAMP;
        }
        return amount;
    }

    /**
     * HP に反映される直前: {@link #DAMAGE_WINDOW_TICKS} の間に引く量を、合計で最大 HP の {@link #MAX_HIT_RATIO} までに制限する。
     * 窓は最初の被弾から数え始める（上限いっぱいの 1 撃を受けたら、次に削られるのは窓が明けてから）
     */
    public float capFinalDamage(float amount, @Nullable DamageSource source) {
        if (!isProtectionActive()) return amount;
        // 強化保護中でも通常攻撃は上限どおりに通す（即死系は入口で止める。EprotectForgeEvents.isLethalAttack）
        // ブラックリストの MOD からのダメージは上限を下げる（罰則）
        String penalized = source != null ? blacklistedDamageMod(source) : null;
        float ratio = penalized != null ? BLACKLIST_HIT_RATIO : MAX_HIT_RATIO;
        if (penalized != null) AttackLog.blocked(owner, AttackOp.DAMAGE_PENALTY, penalized);
        float cap = Math.max(1.0F, owner.getMaxHealth() * ratio);
        if (!Float.isFinite(amount)) amount = cap;
        long now = serverClock();
        if (now - damageWindowStart >= DAMAGE_WINDOW_TICKS) {
            damageWindowStart = now;
            damageInWindow = 0.0F;
        }
        float allowed = Math.max(0.0F, Math.min(amount, cap - damageInWindow));
        damageInWindow += allowed;
        return allowed;
    }

    /**
     * ダメージの出どころがブラックリストの MOD ならその ID（攻撃者の持つアイテム → 直接・間接の攻撃元の種類 → ダメージの種類 →
     * 呼び出し経路の順に見る）。ブラックリストが空なら何もしない
     */
    @Nullable
    private String blacklistedDamageMod(DamageSource source) {
        if (!ModBlacklist.isActive()) return null;
        List<String> candidates = new ArrayList<>();
        if (source.getEntity() instanceof LivingEntity attacker && !attacker.getMainHandItem().isEmpty()) {
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(attacker.getMainHandItem().getItem());
            if (item != null) candidates.add(item.getNamespace());
        }
        if (source.getDirectEntity() != null) candidates.add(BuiltInRegistries.ENTITY_TYPE.getKey(source.getDirectEntity().getType()).getNamespace());
        if (source.getEntity() != null) candidates.add(BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType()).getNamespace());
        source.typeHolder().unwrapKey().ifPresent(key -> candidates.add(key.location().getNamespace()));
        for (String mod : candidates) {
            if (ModBlacklist.contains(mod)) return mod;
        }
        String caller = CallerAttribution.foreignClassMod(owner);
        return ModBlacklist.contains(caller) ? caller : null;
    }

    /**
     * 凶悪技術の罰則: ブラックリストの MOD・強化保護中の即死系の攻撃を止めたとき、HP を最大まで戻す（1 回の攻撃につき 1 回。
     * 通常攻撃で削られた分は、凶悪技術を使われたときだけ帳消しになる。2026-10-03 にユーザー指定）
     */
    public void penalizeMaliciousAttack() {
        if (owner.level().isClientSide || !isProtectionActive()) return;
        float max = owner.getMaxHealth();
        if (DirectRead.health(owner) < max) {
            owner.setHealth(max);
            AttackLog.repaired(owner, "malicious_penalty:max_health");
        }
        lastValidHealth = max;
    }

    /** 被弾量の時間窓を数え直す（GameTest や、演出として正規に続けて大ダメージを与えたい場合に使う） */
    public void resetDamageWindow() {
        damageWindowStart = -DAMAGE_WINDOW_TICKS;
        damageInWindow = 0.0F;
    }

    /** setHealth() の入口: 正規の被弾以外での HP 0 化と、NaN / 無限大を拒否する */
    public float filterHealth(float health) {
        if (owner.level().isClientSide || !isProtectionActive()) return health; // HP の正はサーバー側
        float fallback = lastValidHealth > 0.0F ? lastValidHealth : owner.getMaxHealth();
        if (!Float.isFinite(health)) {
            AttackLog.refused(owner, AttackOp.HEALTH_LOSS + ":nan");
            return fallback;
        }
        if (hurtDepth == 0) {
            float current = DirectRead.health(owner);
            float safeCurrent = Float.isFinite(current) && current > 0.0F ? current : fallback;
            if (health <= 0.0F) {
                AttackLog.refused(owner, AttackOp.HEALTH_LOSS + ":zero");
                return safeCurrent;
            }
            // hurt() を通らない HP の削り（setHealth(現在値 - x) の連打など）も拒否。
            // 最大 HP の引き下げに合わせた切り詰めと回復は通す。設定で許していても、ブラックリストの MOD からは拒否する
            if (health < safeCurrent && health < owner.getMaxHealth()) {
                if (EprotectConfig.get(EprotectConfig.REJECT_HEALTH_LOSS_OUTSIDE_DAMAGE)) {
                    AttackLog.refused(owner, AttackOp.HEALTH_LOSS);
                    return safeCurrent;
                }
                if (blacklistedCaller(AttackOp.HEALTH_LOSS, false)) return safeCurrent;
            }
        } else {
            // hurt() 実行中であっても、1 撃の最大ダメージ（MAX_HIT_RATIO）を超える不当な HP 0 化を弾く
            float minAllowed = fallback - (owner.getMaxHealth() * MAX_HIT_RATIO + 1.0F);
            if (health <= 0.0F && minAllowed > 0.0F) {
                AttackLog.refused(owner, AttackOp.HEALTH_LOSS + ":fatal_in_hurt");
                return minAllowed;
            }
        }
        return health;
    }

    /** クライアント側: 生きているのに死亡演出のカウンタが残っていたら消す（倒れたまま・赤いままを防ぐ） */
    public void clientTick() {
        if (owner.deathTime > 0 && owner.getHealth() > 0.0F) {
            owner.deathTime = 0;
        }
    }

    // ---- 修復 ----------------------------------------------------------------

    /**
     * 毎 Tick の自己修復。「点検 → 丸ごと入り直し」の 2 段構え。
     * <ol>
     *   <li>他 MOD 固有のフラグを解除する（{@link KillModCounters}）。フラグが残ったままだと以後の読み出しが偽装されるので必ず最初</li>
     *   <li>HP と死亡フラグ（ワールドへの載り方とは別の状態）を戻す</li>
     *   <li>ワールドに正しく載っているかを点検し（{@link WorldPresence#inspect}）、1 つでも崩れていれば
     *       部分的に直さず、バニラの正規ルートで丸ごと入り直させる（{@link WorldPresence#rejoin}）</li>
     * </ol>
     * 修復した件数は {@link #noteAnomalies} に積み、追いつかなければ退避する。
     */
    void repair() {
        boolean neutralized = KillModCounters.counterAll(owner);

        // Eprotect+ Java エージェントによる外部干渉の検査・迂回
        com.automatavalkyries.protect.plus.EprotectPlusLink.inspectAndBypassInterference(owner);

        // AI が外部MOD（IMDS cleanAI 等）によって全消去されていれば復元
        if (owner instanceof IEprotected protectedOwner) {
            protectedOwner.restoreGoalsIfEmpty();
        }

        if (isDying()) {
            // 撃破演出中は HP・死亡フラグは正規の状態なので触らず、演出を最後まで走らせることだけ守る
            clearIllegalRemovalFlag();
            restoreTickingIfStopped();
            return;
        }
        if (!isProtectionActive()) return;

        // サーバー側でも生存中に死亡演出カウンタが残っていれば即座にリセット（Hyperlink/IMDS の Tick 進行キルを阻止）
        if (owner.deathTime > 0) {
            owner.deathTime = 0;
        }

        if (isReinforcedActive()) {
            // HP は満タンに戻さない（通常攻撃で削られた分まで消えるため）。抹殺予定の一覧と他 MOD の印の掃除だけ
            com.automatavalkyries.protect.flag.KillLists.purge(owner);
            ForeignStateCleaner.clean(owner);
        }

        int anomalies = 0;
        if (neutralized) {
            AutomataValkyries.LOGGER.warn("[eprotect] Neutralized kill-mod flags on {}", owner);
            anomalies++;
        }
        // 他 MOD の抹殺予定の一覧から先に外す（載ったままだと、その MOD の Mixin が入り直し・座標の戻しを拒否・隠蔽する。ECA の隔離など）
        if (com.automatavalkyries.protect.flag.KillLists.purge(owner) > 0) anomalies++;
        if (restoreMaxHealth()) anomalies++;
        if (restoreHealth()) anomalies++;
        if (clearDeadFlag()) anomalies++;
        if (restoreVanillaFlags()) anomalies++;
        anomalies += restorePresence();
        // vanilla の状態を戻した後に回す（vanilla のフィールドを見せるだけのアクセサ（nosugar の DeadFlag 等）を
        // 他 MOD 固有の状態と取り違えて、直書きの修復をその MOD の攻撃として記録しないため）
        if (ForeignStateCleaner.clean(owner)) anomalies++;
        noteSpoofedRead();
        if (restoreTickingIfStopped()) anomalies++;
        resyncViewersIfPending();

        if (anomalies > 0) noteAnomalies(anomalies);
    }

    /**
     * 読み出しの偽装を記録する（実際は生きているのに getter が「死亡・消去済み」と答える）。直せるのは原因の印だけなので、
     * 原因の印を戻した後（{@link ForeignStateCleaner} の後）にも残っているものだけを、修復の件数には数えずに記録する。
     */
    private void noteSpoofedRead() {
        String spoofed = DirectRead.spoofedRead(owner);
        if (spoofed != null) AttackLog.detected(owner, AttackOp.SPOOFED_READ + ":" + spoofed);
    }

    /**
     * 最大 HP（MAX_HEALTH 属性）の改ざんを戻す。基礎値は種類の既定値へ戻し、他 MOD の補正（名前がこの MOD の ID で始まらないもの）は外す。
     * 下げられると HP がそこまで切り詰められて 1 撃で倒せる残量になり、桁外れに上げられると 1 撃の上限（最大 HP の
     * {@link #MAX_HIT_RATIO}）が意味をなさず戦闘が成り立たない。どちらも他 MOD の属性上限の解除や /attribute で起こる。
     */
    private boolean restoreMaxHealth() {
        AttributeInstance attribute = owner.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return false;
        double expectedBase = defaultMaxHealth(attribute);
        double oldValue = attribute.getValue();
        int removed = 0;
        for (AttributeModifier modifier : List.copyOf(attribute.getModifiers())) {
            if (!modifier.getName().startsWith(AutomataValkyries.MODID)) {
                attribute.removeModifier(modifier.getId());
                removed++;
            }
        }
        boolean baseChanged = attribute.getBaseValue() != expectedBase;
        if (baseChanged) attribute.setBaseValue(expectedBase);
        if (removed == 0 && !baseChanged) return false;

        float max = owner.getMaxHealth();
        if (lastValidHealth > max) lastValidHealth = max;
        if (!(DirectRead.health(owner) <= max)) owner.setHealth(max); // NaN も含めて切り詰める
        AutomataValkyries.LOGGER.warn("[eprotect] Restored tampered max health of {} ({} -> {}, {} foreign modifiers removed)",
                owner, oldValue, max, removed);
        AttackLog.repaired(owner, "max_health");
        return true;
    }

    private double defaultMaxHealth(AttributeInstance attribute) {
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) owner.getType();
        return DefaultAttributes.hasSupplier(type)
                ? DefaultAttributes.getSupplier(type).getBaseValue(Attributes.MAX_HEALTH)
                : attribute.getAttribute().getDefaultValue();
    }

    /** hurt() を経ない即死（setHealth(0) など）や NaN 化は直前の正常値へ戻す */
    private boolean restoreHealth() {
        // getHealth() はこのMOD の Mixin が HP 0 を直前の正常値に読み替えるので、同期データの実際の値を見る
        float hp = DirectRead.health(owner);
        if (Float.isFinite(hp) && hp > 0.0F) {
            return cutUntrackedLoss(hp);
        }
        float restore = lastValidHealth > 0.0F ? lastValidHealth : owner.getMaxHealth();
        owner.setHealth(restore);
        owner.deathTime = 0;
        lastValidHealth = restore;
        AttackLog.repaired(owner, "health");
        return true;
    }

    /**
     * ダメージ処理（hurt）を通らずに減った HP（同期データの直書き・getter の書き換え越しの削り等。Fantasy Ending の catchSetTrueHealth）を、
     * 普通の被弾と同じ上限（{@link #capFinalDamage}: 時間窓の中で最大 HP の {@link #MAX_HIT_RATIO} まで）に掛け、超えた分を戻す。
     * 強化保護中（ブラックリストの MOD に攻撃された直後）は上限が 0 なので全部戻る。これまでは HP 0 にならない削りは素通りだった（2026-10-03）
     *
     * @return 戻したか
     */
    private boolean cutUntrackedLoss(float hp) {
        float previous = lastValidHealth;
        if (hurtDepth > 0 || !(previous > 0.0F) || hp >= previous - 0.001F || hp >= owner.getMaxHealth() || !isProtectionActive()) {
            lastValidHealth = hp;
            return false;
        }
        float loss = previous - hp;
        // ダメージ処理を通らない削りは通常攻撃ではないので、強化保護中は全部戻す
        float allowed = isReinforcedActive() ? 0.0F : capFinalDamage(loss, null);
        if (allowed >= loss - 0.001F) {
            lastValidHealth = hp;
            return false;
        }
        float target = Math.min(owner.getMaxHealth(), previous - allowed);
        owner.setHealth(target);
        lastValidHealth = target;
        AttackLog.repaired(owner, "health_cut");
        AutomataValkyries.LOGGER.debug("[eprotect] Cut untracked health loss on {}: {} -> {} (allowed {} of {})",
                owner, previous, target, allowed, loss);
        return true;
    }

    /**
     * 見張る vanilla の状態（名前・読み・書き）。見張るかはフラグ辞書で決める（保護対象に付いていたら戻すべき名前か:
     * invisible は CONCEAL、noAi・noGravity は CONTROL）。設定 {@code flagDictionary.ignore} に名前を足せば見張らない
     */
    private record VanillaFlag(String name, java.util.function.Predicate<LivingEntity> get,
                               java.util.function.BiConsumer<LivingEntity, Boolean> set) {}

    private static final List<VanillaFlag> VANILLA_FLAGS = List.of(
            new VanillaFlag("invisible", Entity::isInvisible, Entity::setInvisible),
            new VanillaFlag("noAi", e -> e instanceof net.minecraft.world.entity.Mob m && m.isNoAi(),
                    (e, v) -> { if (e instanceof net.minecraft.world.entity.Mob m) m.setNoAi(v); }),
            new VanillaFlag("noGravity", Entity::isNoGravity, Entity::setNoGravity));

    /** 保護を始めたときの値（他 MOD に書き換えられたら戻す基準）。null はまだ覚えていない */
    @Nullable
    private boolean[] vanillaBaseline;

    /**
     * vanilla の状態のうち、即死・消去の MOD が「消した」「止めた」見せ方に使うもの（透明・AI 停止・重力無し）を、保護を始めたときの状態に戻す
     * （Fantasy Ending の DeleteCommand は setInvisible(true)、クリエイティブの骨は AI を止める。2026-10-03）。どれを見張るかはフラグ辞書で決める。
     * 透明化の状態異常が付いている間の透明は正規なので戻さない
     *
     * @return 戻したか
     */
    private boolean restoreVanillaFlags() {
        if (!isProtectionActive()) return false;
        if (vanillaBaseline == null) {
            vanillaBaseline = new boolean[VANILLA_FLAGS.size()];
            for (int i = 0; i < VANILLA_FLAGS.size(); i++) vanillaBaseline[i] = VANILLA_FLAGS.get(i).get().test(owner);
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < VANILLA_FLAGS.size(); i++) {
            VanillaFlag flag = VANILLA_FLAGS.get(i);
            if (!com.automatavalkyries.protect.flag.FlagDictionary.harmfulOnProtected(flag.name())) continue;
            boolean expected = vanillaBaseline[i];
            if (flag.name().equals("invisible") && owner.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) expected = true;
            if (flag.get().test(owner) == expected) continue;
            flag.set().accept(owner, expected);
            AttackLog.repaired(owner, "vanilla:" + flag.name());
            changed = true;
        }
        return changed;
    }

    /** 消去を拒否した・入り直させた後に、見ているプレイヤーへ見せ直すか */
    private boolean viewersResyncPending;
    private long lastViewersResync = -1000L;

    /**
     * 見ているプレイヤーへ見せ直す: 追跡（ChunkMap の TrackedEntity）を作り直し、出現の情報を送り直す。追跡が始まるときの
     * {@code startSeenByPlayer} も呼ばれ直すので、外されたボスバーも戻る。同じ個体は 20 Tick に 1 回まで
     */
    private boolean resyncViewersIfPending() {
        if (!viewersResyncPending || !(owner.level() instanceof ServerLevel level) || owner.isRemoved()) return false;
        long now = serverClock();
        if (now - lastViewersResync < 20) return false;
        viewersResyncPending = false;
        lastViewersResync = now;
        try {
            level.getChunkSource().removeEntity(owner);
            level.getChunkSource().addEntity(owner);
            AttackLog.repaired(owner, "viewers_resync");
            return true;
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] Could not resync viewers of {}: {}", owner, t.toString());
            return false;
        }
    }

    /** 強制デスフラグ（dead = true）が残ると以後の死亡処理・ドロップが壊れるので戻す */
    private boolean clearDeadFlag() {
        LivingEntityAccessor living = (LivingEntityAccessor) owner;
        if (!living.automata_valkyries$isDead()) return false;
        living.automata_valkyries$setDead(false);
        AutomataValkyries.LOGGER.warn("[eprotect] Cleared forced death flag on {}", owner);
        AttackLog.repaired(owner, "dead_flag");
        return true;
    }

    /**
     * ワールドへの載り方を点検し、崩れていれば丸ごと入り直させる。
     * 保存・終了中は触らない（入り直しはチャンクや一覧を動かすので、終了処理と競合する）。
     * @return 異常の件数（0: 異常なし / 1: 入り直した / 2: 別個体に差し替えた / 5: 戻せない）
     */
    private int restorePresence() {
        if (!(owner.level() instanceof ServerLevel level) || !level.getServer().isRunning()) return 0;
        EnumSet<WorldPresence.Issue> issues = WorldPresence.inspect(level, owner);
        presenceHealthy = issues.isEmpty();
        if (presenceHealthy) return 0;

        AttackLog.repaired(owner, "presence");
        // 戻す先が無い（一度も正常な座標を見ていない）ときはワールドの出現地点へ
        Vec3 safe = lastSafePos != null ? lastSafePos : net.minecraft.world.phys.Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        WorldPresence.Outcome outcome = WorldPresence.rejoin(level, owner, safe);
        switch (outcome) {
            case REJOINED -> {
                viewersResyncPending = true;
                AutomataValkyries.LOGGER.warn("[eprotect] Rejoined {} via the vanilla route (broken: {})", owner, issues);
                return 1;
            }
            case REPLACED -> {
                AutomataValkyries.LOGGER.warn("[eprotect] Rejoin was refused; replaced {} with a fresh copy (broken: {})", owner, issues);
                return 2;
            }
            default -> {
                AutomataValkyries.LOGGER.error("[eprotect] Could not bring {} back into the world (broken: {})", owner, issues);
                return 5;
            }
        }
    }

    /**
     * 入り直しを拒否されて同じ UUID の新しい個体に差し替えたあと、古い個体（ワールドのどこにも載っていない）の保護を解く。
     * 以後の保護は新しい個体の Eprotect が担う。
     */
    public void retireReplaced() {
        despawning = true; // 保護を解く（セリフ・効果音は出さない）
        if (owner instanceof IEprotected protectedOwner) protectedOwner.onRetired();
        releaseForcedChunk();
        EprotectManager.getInstance().unregister(this);
        ((EntityAccessor) owner).automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);
    }

    // ---- 退避（修復が追いつかないとき） --------------------------------------

    private long anomalyWindowStart = Long.MIN_VALUE;
    private int anomalyCount = 0;

    /**
     * 短時間に修復が続く＝何かが毎 Tick 壊しに来ていて修復が追いつかない。
     * 壊れかけの状態で居座らせず、正規の退場処理（セリフ・効果音つき）で安全に退避させる。
     */
    private void noteAnomalies(int count) {
        long now = serverClock();
        int window = EprotectConfig.get(EprotectConfig.RETREAT_WINDOW_TICKS);
        if (now - anomalyWindowStart > window) {
            anomalyWindowStart = now;
            anomalyCount = 0;
        }
        anomalyCount += count;
        if (anomalyCount >= EprotectConfig.get(EprotectConfig.RETREAT_ANOMALY_THRESHOLD)
                && EprotectConfig.get(EprotectConfig.RETREAT_WHEN_UNREPAIRABLE)) {
            AutomataValkyries.LOGGER.warn("[eprotect] {} anomalies repaired within {} ticks; retreating {} safely",
                    anomalyCount, window, owner);
            AttackLog.retreated(owner);
            triggerDespawn();
        }
    }

    // ---- ワールドデータの登録簿 ------------------------------------------------

    /** 登録簿へ写しを取る間隔（Tick） */
    private static final int RECORD_INTERVAL = 20;
    /** 直近の点検でワールドに正しく載っていたか。壊れた状態を写しとして残さないため */
    private boolean presenceHealthy = false;
    private long lastRecordTick = Long.MIN_VALUE;

    private EprotectRegistry registry() {
        return EprotectRegistry.get((ServerLevel) owner.level());
    }

    /**
     * 正常な状態を一定間隔で登録簿へ写す。エンティティごと失われても、この写しから作り直せる。
     * HP が正常で、ワールドに正しく載っていた Tick だけ写す（壊されかけの状態を正本にしない）。
     */
    private void recordToRegistryIfDue() {
        if (!(owner.level() instanceof ServerLevel level) || !presenceHealthy) return;
        long now = serverClock();
        if (lastRecordTick != Long.MIN_VALUE && now - lastRecordTick < RECORD_INTERVAL) return;
        float hp = DirectRead.health(owner);
        if (!Float.isFinite(hp) || hp <= 0.0F) return;
        lastRecordTick = now;
        registry().record(owner, lastSafePos != null ? lastSafePos : owner.position());
    }

    /** 正常な座標を控えておく。入り直しで座標が壊れていたときに戻す先になる */
    private void rememberHealthyState() {
        if (isSafePosition(owner, owner.getX(), owner.getY(), owner.getZ())) {
            lastSafePos = owner.position();
        }
    }

    /** 撃破演出中に Mixin を経ずに直書きされた不正な消去フラグ（{@link #isIllegalRemoval}）を消す（演出中は入り直しをしない） */
    private boolean clearIllegalRemovalFlag() {
        EntityAccessor accessor = (EntityAccessor) owner;
        if (isIllegalRemoval(owner, accessor.automata_valkyries$getRemovalReason())) {
            accessor.automata_valkyries$setRemovalReason(null);
            owner.reviveCaps();
            AutomataValkyries.LOGGER.warn("[eprotect] Cleared illegal removal flag on {}", owner);
            AttackLog.repaired(owner, "removal_flag");
            return true;
        }
        return false;
    }

    /**
     * Tick 停止対策: ワールドには正しく載っているのに本体の Tick が止まっていれば、
     * <ul>
     *   <li>Tick 対象一覧から外されている → 一覧へ入れ直す（修復として数える）</li>
     *   <li>一覧には載っている → ワールドの Tick ごと止められている（時間停止など）か、他 MOD が反復から読み飛ばしている。
     *       入れ直しても効かないので、止まっている間は毎 Tick ここから本体の Tick を代わりに回す（修復には数えない。
     *       数えると時間停止が続くだけで退避してしまう）</li>
     * </ul>
     * {@link #tick()} が走らない状態なので、マネージャーの周期処理（サーバー Tick の最後）から呼ばれる。
     *
     * @return 一覧へ入れ直したとき true
     */
    private boolean restoreTickingIfStopped() {
        // isRemoved() は他 MOD の ASM に呼び出し箇所ごと偽装されうるので、フィールドを直接読む
        if (!(owner.level() instanceof ServerLevel level) || ((EntityAccessor) owner).automata_valkyries$getRemovalReason() != null) return false;
        if (insideDrivenTick) return false;
        if (!isSafePosition(owner, owner.getX(), owner.getY(), owner.getZ())) return false;
        if (lastTickServerTick == Long.MIN_VALUE) return false; // まだワールドに追加されていない
        long stalled = serverClock() - lastTickServerTick;
        if (stalled == 0) {
            // このサーバー Tick のうちに本体の Tick が自然に走った（止められていない）
            if (drivingFrozenTick) {
                drivingFrozenTick = false;
                tickBlockReported = false;
                AutomataValkyries.LOGGER.info("[eprotect] {} is ticking on its own again", owner);
            }
            return false;
        }
        if (!drivingFrozenTick && stalled <= TICK_STALL_THRESHOLD) return false;
        if (!level.isPositionEntityTicking(owner.blockPosition())) return false; // チャンク側の都合で止まっているだけ

        EntityTickList tickList = ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList();
        if (!((EntityTickListAccessor) tickList).automata_valkyries$getActive().containsKey(owner.getId())) {
            drivingFrozenTick = false;
            tickList.add(owner);
            lastTickServerTick = serverClock(); // 復帰の Tick が走るまで連続で戻さない
            AutomataValkyries.LOGGER.warn("[eprotect] Restored ticking of {}", owner);
            AttackLog.repaired(owner, "ticking");
            return true;
        }

        if (!drivingFrozenTick) {
            drivingFrozenTick = true;
            AutomataValkyries.LOGGER.warn("[eprotect] {} is in the tick list but is not being ticked (time stop?); ticking it directly", owner);
            AttackLog.repaired(owner, "frozen_tick");
        }
        driveTick(level);
        return false;
    }

    /** 本体の Tick をワールドの代わりに 1 回回す（騎乗中は乗り物が回すので触らない） */
    /** 包みの入口を飛ばして回したことを記録したか（記録は 1 個体 1 回） */
    private boolean entranceBypassNoted;

    private void driveTick(ServerLevel level) {
        Entity vehicle = owner.getVehicle();
        if (vehicle != null && !vehicle.isRemoved() && vehicle.hasPassenger(owner)) return;
        int ticksBefore = owner.tickCount;
        int runsBefore = ownTickRuns;
        insideDrivenTick = true;
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
            insideDrivenTick = false;
        }
        // 代わりに回しても進まない＝その入口で他 MOD に打ち切られている。打ち切れる処理を候補として 1 度だけ報告する
        // （tickCount は tickNonPassenger の先頭で進む。進んだのに tick() まで届かなければ、エンティティのクラスの tick で止められている）
        if (!tickBlockReported) {
            TickEntranceHooks.Stage stage = owner.tickCount == ticksBefore ? TickEntranceHooks.Stage.ENTRANCE
                    : owner instanceof IEprotected && ownTickRuns == runsBefore ? TickEntranceHooks.Stage.ENTITY_TICK : null;
            if (stage != null) {
                tickBlockReported = true;
                TickEntranceHooks.report(owner, stage);
            }
        }
        sendTrackedChanges(level);
        lastTickServerTick = serverClock() - 1; // 次のサーバー Tick で自然に走ったかを見分けられるよう、自分で回した分は数えない
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

    // ---- チャンクロック ------------------------------------------------------

    private void updateForcedChunk() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        if (!level.getServer().isRunning()) return; // 終了処理中にチャンクを同期ロードすると固まる
        if (!isSafePosition(owner, owner.getX(), owner.getY(), owner.getZ())) return; // 壊れた座標のチャンクを強制ロードしない
        ChunkPos current = owner.chunkPosition();
        if (level == forcedLevel && current.equals(forcedChunk)) return;

        releaseForcedChunk();
        ForgeChunkManager.forceChunk(level, AutomataValkyries.MODID, owner, current.x, current.z, true, true);
        forcedLevel = level;
        forcedChunk = current;
    }

    private void releaseForcedChunk() {
        if (forcedLevel != null && forcedChunk != null) {
            ForgeChunkManager.forceChunk(forcedLevel, AutomataValkyries.MODID, owner, forcedChunk.x, forcedChunk.z, false, true);
        }
        forcedLevel = null;
        forcedChunk = null;
    }

    // ---- 正規退場 ------------------------------------------------------------

    /** プレイヤー全滅時などの安全退場処理。セリフと効果音を出してから消去する */
    public void triggerDespawn() {
        if (despawning || owner.level().isClientSide) return;
        despawning = true;
        registry().forget(owner.getUUID());

        releaseForcedChunk();

        if (owner instanceof IProviderWithSpeech speechProvider) {
            Component speech = speechProvider.getDefeatSpeech();
            SoundEvent sound = speechProvider.getDefeatSound();
            if (speech != null && owner.getServer() != null) {
                owner.getServer().getPlayerList().broadcastSystemMessage(speech, false);
            }
            if (sound != null) {
                owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                        sound, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
        }

        owner.discard();
    }
}
