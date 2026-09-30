package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.jetbrains.annotations.Nullable;

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
    /** 最後に本体の Tick が走ったゲーム時刻（Tick 停止の検出用） */
    private long lastTickGameTime = Long.MIN_VALUE;

    /** 最後に確認した正常な座標（IMDS の -Infinity 直書きからの復帰用） */
    @Nullable
    private Vec3 lastSafePos;
    /** 最後に確認したワールドとの接続（NULL に差し替えられたときの復帰用） */
    @Nullable
    private EntityInLevelCallback lastLevelCallback;

    @Nullable
    private ServerLevel forcedLevel;
    @Nullable
    private ChunkPos forcedChunk;

    public Eprotect(LivingEntity owner) {
        this.owner = owner;
    }

    /** 座標として正常か（NaN / 無限大 / ワールド境界の遥か外 / 奈落の底を弾く） */
    public static boolean isSafePosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        return Math.abs(x) <= 3.0E7D && Math.abs(z) <= 3.0E7D && y >= -1000.0D && y <= 2.0E7D;
    }

    public LivingEntity getOwner() {
        return owner;
    }

    /** 保護が有効か（正規退場・正規撃破の後は無効） */
    public boolean isProtectionActive() {
        return !despawning && !defeated;
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

    // ---- ライフサイクル ----------------------------------------------------

    /**
     * 保護スタート（マネージャー登録）。
     * チャンクロックはここでは取らず、最初の {@link #tick()} に任せる。forceChunk はチャンクを同期ロードするため、
     * エンティティの読み込み処理の最中（特にサーバー終了時の saveAll）に呼ぶとサーバースレッドが自分を待ち続けて固まる。
     */
    public void onAddedToWorld() {
        if (!(owner.level() instanceof ServerLevel)) return;
        EprotectManager.getInstance().register(this);
        lastTickGameTime = owner.level().getGameTime(); // 追加直後に Tick 停止されても検出できるよう、ここを起点にする
    }

    public void onRemovedFromWorld() {
        if (!(owner.level() instanceof ServerLevel)) return;
        Entity.RemovalReason reason = owner.getRemovalReason();
        // Mixin をすり抜けた不正消去: マネージャーが復元できるよう登録とチャンクロックを維持する
        if (isGuardingRemoval() && reason != null && reason.shouldDestroy()) return;
        releaseForcedChunk();
        EprotectManager.getInstance().unregister(this);
    }

    /** 毎 Tick（サーバー側）に実行する自己修復 */
    public void tick() {
        hurtDepth = 0; // hurt() 中に例外が出てカウンタが残った場合の保険
        if (!isGuardingRemoval()) return;
        // onRemovedFromWorld はチャンクの可視性低下（トラッキング終了）でも呼ばれるが、
        // 再トラッキング時に onAddedToWorld は呼ばれないため、ここで登録を維持する
        EprotectManager.getInstance().register(this);
        lastTickGameTime = owner.level().getGameTime();
        repair();
        if (isProtectionActive()) {
            rememberHealthyState();
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
        if (owner.level().isClientSide) return false;
        return isGuardingRemoval() && reason.shouldDestroy();
    }

    /** 撃破演出の終わりに所有エンティティが呼ぶ。以後の消去を許可する */
    public void finishDeath() {
        this.deathFinished = true;
    }

    /** hurt() 経由で HP が尽きた場合だけ死亡を許可する（die() の直接呼び出しは拒否） */
    public boolean canDie() {
        if (!isProtectionActive()) return true;
        // 正規の hurt 処理中であり、被弾前の体力が1撃で倒せる残量（最大HPの10%）以下で、
        // かつ現在のHPが本当に 0 以下になった場合のみ許可する
        return isLegitimateFatalBlow() && owner.getHealth() <= 0.0F;
    }

    public void markDefeated() {
        this.defeated = true;
    }

    public void beginHurt() {
        hurtDepth++;
    }

    public void endHurt() {
        if (hurtDepth > 0) hurtDepth--;
        // 正規の被弾で減った HP を「直前の正常値」として記録（同 Tick 内の即死ハックで被弾前に戻らないように）
        float hp = owner.getHealth();
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

    /** HP に反映される直前: 1 撃を最大 HP の {@link #MAX_HIT_RATIO} までに制限する */
    public float capFinalDamage(float amount) {
        if (!isProtectionActive()) return amount;
        float cap = Math.max(1.0F, owner.getMaxHealth() * MAX_HIT_RATIO);
        if (!Float.isFinite(amount)) return cap;
        return Math.min(amount, cap);
    }

    /** setHealth() の入口: 正規の被弾以外での HP 0 化と、NaN / 無限大を拒否する */
    public float filterHealth(float health) {
        if (owner.level().isClientSide || !isProtectionActive()) return health; // HP の正はサーバー側
        float fallback = lastValidHealth > 0.0F ? lastValidHealth : owner.getMaxHealth();
        if (!Float.isFinite(health)) return fallback;
        if (hurtDepth == 0) {
            float current = owner.getHealth();
            float safeCurrent = Float.isFinite(current) && current > 0.0F ? current : fallback;
            if (health <= 0.0F) return safeCurrent;
            // hurt() を通らない HP の削り（setHealth(現在値 - x) の連打など）も拒否。
            // 最大 HP の引き下げに合わせた切り詰めと回復は通す
            if (EprotectConfig.get(EprotectConfig.REJECT_HEALTH_LOSS_OUTSIDE_DAMAGE)
                    && health < safeCurrent && health < owner.getMaxHealth()) {
                return safeCurrent;
            }
        } else {
            // hurt() 実行中であっても、1 撃の最大ダメージ（MAX_HIT_RATIO）を超える不当な HP 0 化を弾く
            float minAllowed = fallback - (owner.getMaxHealth() * MAX_HIT_RATIO + 1.0F);
            if (health <= 0.0F && minAllowed > 0.0F) {
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
     * HP 異常・死亡フラグ・消去フラグを修復する。ワールドや Tick 対象から外されていれば戻す。
     * <p>
     * 各修復ステップはどの即死 MOD が相手でも同じ形で効く（MOD 固有の処理は {@link KillModCounters} に分ける）。
     * 修復した件数は {@link #noteAnomalies} に積み、追いつかなければ退避する。
     * 他 MOD の ASM は getHealth / isRemoved 等の呼び出し箇所の戻り値を書き換えうるので、MOD 固有のフラグ解除を必ず最初に行う。
     * </p>
     */
    void repair() {
        boolean neutralized = KillModCounters.counterAll(owner);

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

        int anomalies = 0;
        if (neutralized) {
            AutomataValkyries.LOGGER.warn("[eprotect] Neutralized kill-mod flags on {}", owner);
            anomalies++;
        }
        if (restorePosition()) anomalies++;
        if (restoreHealth()) anomalies++;
        if (clearDeadFlag()) anomalies++;
        if (clearIllegalRemovalFlag()) anomalies++;
        if (restoreLevelCallback()) anomalies++;
        anomalies += restoreToWorld();
        if (restoreTickingIfStopped()) anomalies++;

        if (anomalies > 0) noteAnomalies(anomalies);
    }

    /** hurt() を経ない即死（setHealth(0) など）や NaN 化は直前の正常値へ戻す */
    private boolean restoreHealth() {
        float hp = owner.getHealth();
        if (Float.isFinite(hp) && hp > 0.0F) {
            lastValidHealth = hp;
            return false;
        }
        float restore = lastValidHealth > 0.0F ? lastValidHealth : owner.getMaxHealth();
        owner.setHealth(restore);
        owner.deathTime = 0;
        lastValidHealth = restore;
        return true;
    }

    /** 強制デスフラグ（dead = true）が残ると以後の死亡処理・ドロップが壊れるので戻す */
    private boolean clearDeadFlag() {
        LivingEntityAccessor living = (LivingEntityAccessor) owner;
        if (!living.automata_valkyries$isDead()) return false;
        living.automata_valkyries$setDead(false);
        AutomataValkyries.LOGGER.warn("[eprotect] Cleared forced death flag on {}", owner);
        return true;
    }

    /**
     * ワールドから外されていれば戻す。戻せなければ修復が効いていないので重く数える。
     * @return 異常の件数（0: 異常なし / 1: 戻した / 5: 戻せない）
     */
    private int restoreToWorld() {
        if (!(owner.level() instanceof ServerLevel level) || level.getEntity(owner.getUUID()) != null) return 0;
        // 保存・終了中は戻さない。UUID がまだ登録済み（見えない区画にいる等）なら戻すと複製になる
        if (!level.getServer().isRunning()
                || ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager().isLoaded(owner.getUUID())) {
            return 0;
        }
        if (level.addFreshEntity(owner)) {
            AutomataValkyries.LOGGER.warn("[eprotect] Restored {} after it was removed from the world", owner);
            return 1;
        }
        return 5;
    }

    // ---- 退避（修復が追いつかないとき） --------------------------------------

    private long anomalyWindowStart = Long.MIN_VALUE;
    private int anomalyCount = 0;

    /**
     * 短時間に修復が続く＝何かが毎 Tick 壊しに来ていて修復が追いつかない。
     * 壊れかけの状態で居座らせず、正規の退場処理（セリフ・効果音つき）で安全に退避させる。
     */
    private void noteAnomalies(int count) {
        long now = owner.level().getGameTime();
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
            triggerDespawn();
        }
    }

    /** 正常な状態（座標・ワールドとの接続）を控えておく。修復で戻す先になる */
    private void rememberHealthyState() {
        if (isSafePosition(owner.getX(), owner.getY(), owner.getZ())) {
            lastSafePos = owner.position();
        }
        EntityInLevelCallback callback = ((EntityAccessor) owner).automata_valkyries$getLevelCallback();
        if (callback != EntityInLevelCallback.NULL) {
            lastLevelCallback = callback;
        }
    }

    /** 座標フィールドの直書き（IMDS の -Infinity 奈落落とし等）で壊れた座標を、最後の正常な座標へ戻す */
    private boolean restorePosition() {
        if (isSafePosition(owner.getX(), owner.getY(), owner.getZ()) || lastSafePos == null) return false;
        owner.setPos(lastSafePos.x, lastSafePos.y, lastSafePos.z);
        owner.setDeltaMovement(Vec3.ZERO);
        AutomataValkyries.LOGGER.warn("[eprotect] Restored position of {}", owner);
        return true;
    }

    /**
     * ワールドとの接続（levelCallback）を NULL に直書きされていたら戻す。
     * NULL のままだと移動・保存・消去がワールドに伝わらず、区画に残った古いデータが壊れた座標で保存される。
     * 正規の消去（removalReason あり）の後は触らない。
     */
    private boolean restoreLevelCallback() {
        EntityAccessor accessor = (EntityAccessor) owner;
        if (accessor.automata_valkyries$getLevelCallback() != EntityInLevelCallback.NULL
                || lastLevelCallback == null || accessor.automata_valkyries$getRemovalReason() != null) {
            return false;
        }
        accessor.automata_valkyries$setLevelCallback(lastLevelCallback);
        accessor.automata_valkyries$setAddedToWorld(true);
        lastLevelCallback.onMove(); // 区画の登録を現在の座標に合わせ直す
        AutomataValkyries.LOGGER.warn("[eprotect] Restored level callback of {}", owner);
        return true;
    }

    /** Mixin を経ずに直書きされた消去フラグ（KILLED / DISCARDED）を消す */
    private boolean clearIllegalRemovalFlag() {
        EntityAccessor accessor = (EntityAccessor) owner;
        if (accessor.automata_valkyries$getRemovalReason() != null && accessor.automata_valkyries$getRemovalReason().shouldDestroy()) {
            accessor.automata_valkyries$setRemovalReason(null);
            owner.reviveCaps();
            AutomataValkyries.LOGGER.warn("[eprotect] Cleared illegal removal flag on {}", owner);
            return true;
        }
        return false;
    }

    /**
     * Tick 停止対策: 本来 Tick されるべき位置にいるのに本体の Tick が止まっていれば、Tick 対象一覧へ戻す。
     * （{@link #tick()} が走らない状態なので、マネージャーの周期処理から呼ばれる）
     */
    private boolean restoreTickingIfStopped() {
        // isRemoved() は他 MOD の ASM に呼び出し箇所ごと偽装されうるので、フィールドを直接読む
        if (!(owner.level() instanceof ServerLevel level) || ((EntityAccessor) owner).automata_valkyries$getRemovalReason() != null) return false;
        if (!isSafePosition(owner.getX(), owner.getY(), owner.getZ())) return false;
        if (lastTickGameTime == Long.MIN_VALUE) return false; // まだワールドに追加されていない
        if (level.getGameTime() - lastTickGameTime <= TICK_STALL_THRESHOLD) return false;
        if (!level.isPositionEntityTicking(owner.blockPosition())) return false; // チャンク側の都合で止まっているだけ

        ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().add(owner);
        lastTickGameTime = level.getGameTime(); // 復帰の Tick が走るまで連続で戻さない
        AutomataValkyries.LOGGER.warn("[eprotect] Restored ticking of {}", owner);
        return true;
    }

    // ---- チャンクロック ------------------------------------------------------

    private void updateForcedChunk() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        if (!level.getServer().isRunning()) return; // 終了処理中にチャンクを同期ロードすると固まる
        if (!isSafePosition(owner.getX(), owner.getY(), owner.getZ())) return; // 壊れた座標のチャンクを強制ロードしない
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
