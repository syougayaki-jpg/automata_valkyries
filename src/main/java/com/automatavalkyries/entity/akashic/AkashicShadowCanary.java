package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityManagerAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.mixin.SynchedEntityDataAccessor;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.ObserverLink;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.FlagWatchAgent;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.util.UnsafeHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 事象観測体アカシック・プローブの「おとりシャドウステート・ダミー真ライフ・基幹/派生技術マイクロ検死」エンジン。
 * <ul>
 *   <li><b>フメツウィザー型シャドウステート</b>: 無敵盾・フェーズロック・再生タイマーのおとりフィールド＆NBT</li>
 *   <li><b>テッセラクトビースト型シャドウステート</b>: 高次元障壁・空間アンカー・多層HPのおとりフィールド＆NBT</li>
 *   <li><b>フラッシュファー（Omni-Mobs）型シャドウステート</b>: {@code omnimobs_health_data} 等の同期NBT・差分HP・保護リスト（{@link ProtectedCanaryList}）</li>
 *   <li><b>ダミー真ライフ（TrueLife Canary）</b>: 関数型インターフェース {@link TrueLifeOperator} とプリミティブ真ライフの両方を張り、
 *       {@code null} 化・動的ゼロプロキシ置換（SuperSteve 対策系）・値書き換えを個別に感知する</li>
 *   <li><b>ダメージ・属性・空間・エージェントのマイクロ検死</b>:
 *       {@code -0.0F}（{@code 0x80000000}）やペイロード付き {@code NaN}、{@code lastHurt} 負値オーバーフロー、
 *       {@code AttributeInstance.cachedValue} キャッシュ汚染、AABB ゼロ化、{@link StateScanner} 動的差分、{@link ObserverLink} 再変換イベントを網羅観測する</li>
 * </ul>
 */
public final class AkashicShadowCanary {

    private static final float EXPECTED_HEALTH = 100.0F;
    private static final double EXPECTED_MAX_HEALTH = 100.0D;

    /** SuperSteve の Operator 等を模した「ダミー真ライフ」関数型インターフェース */
    @FunctionalInterface
    public interface TrueLifeOperator {
        float getTrueHealth();

        default boolean isTrueAlive() {
            return getTrueHealth() > 0.0F;
        }
    }

    /** 正規のダミー真ライフ実装（動的プロキシ置換されたかどうかを型比較で即座に判別できる） */
    public static final class TrueLifeCanaryImpl implements TrueLifeOperator {
        public float storedTrueHealth;
        public boolean storedTrueAlive = true;

        public TrueLifeCanaryImpl(float health) {
            this.storedTrueHealth = health;
        }

        @Override
        public float getTrueHealth() {
            return storedTrueAlive ? storedTrueHealth : 0.0F;
        }
    }

    /**
     * Omni-Mobs の ProtectedArrayList 等を模した「削除拒否リストのおとり」。
     * 通常の {@code remove}/{@code clear} 呼び出しと、{@code Unsafe} による内部 {@code elementData}/{@code size} 直接縮小の両方を感知する。
     */
    public static final class ProtectedCanaryList extends ArrayList<UUID> {
        private final UUID ownerUuid;
        private boolean suppressIntercept;

        public ProtectedCanaryList(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
            this.suppressIntercept = true;
            super.add(ownerUuid);
            this.suppressIntercept = false;
        }

        void restoreCanary() {
            this.suppressIntercept = true;
            try {
                if (super.size() != 1 || !ownerUuid.equals(super.get(0))) {
                    super.clear();
                    super.add(ownerUuid);
                }
            } finally {
                this.suppressIntercept = false;
            }
        }

        private void recordAttempt(String op) {
            if (suppressIntercept) return;
            CallerAttribution.Attribution a = CallerAttribution.attribute(null);
            String mod = a.callerIsForeign() ? a.callerMod() : a.foreignMod() != null ? a.foreignMod() : a.callerMod();
            AkashicRecord.of(ownerUuid).observe(mod, "shadow:protected_list:" + op, "REFUSED", a.callerFrame(), null);
        }

        @Override
        public boolean remove(Object o) {
            recordAttempt("remove");
            return false;
        }

        @Override
        public UUID remove(int index) {
            recordAttempt("removeAt");
            return null;
        }

        @Override
        public void clear() {
            recordAttempt("clear");
        }

        @Override
        public boolean removeIf(Predicate<? super UUID> filter) {
            recordAttempt("removeIf");
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            recordAttempt("removeAll");
            return false;
        }
    }

    private static final String NETTY_GUARD_NAME = "automata_valkyries_akashic_packet_guard";

    private final AkashicProbeEntity host;
    private final Map<String, Object> stateSnapshot = new LinkedHashMap<>();
    private final RandomSource expectedRandom;
    private Vec3 lastSafePos;
    private long lastObservedGameTime = Long.MIN_VALUE;
    private int lastObserverEventIndex = 0;

    // AttributeInstance.cachedValue / RangedAttribute / LivingEntity.lastHurt / absorptionAmount / Entity.random / Util.timeSource 直読み用キャッシュ
    private static Field attrCachedValueField;
    private static Field attrDirtyField;
    private static Field rangedMinField;
    private static Field rangedMaxField;
    private static Field attrDefaultField;
    private static Field entityRandomField;
    private static Field accessorSerializerField;
    private static Field utilTimeSourceField;
    private static Object originalUtilTimeSource;
    private static boolean attrFieldsSearched;
    private static long lastHurtOffset = -2L;
    private static long absorptionOffset = -2L;

    public static float getLastHurt(LivingEntity entity) {
        long offset = resolveLastHurtOffset();
        return offset >= 0 ? UnsafeHelper.getFloat(entity, offset) : 0.0F;
    }

    public static void setLastHurt(LivingEntity entity, float value) {
        long offset = resolveLastHurtOffset();
        if (offset >= 0) UnsafeHelper.putFloat(entity, offset, value);
    }

    public static float getAbsorptionRaw(LivingEntity entity) {
        long offset = resolveAbsorptionOffset();
        return offset >= 0 ? UnsafeHelper.getFloat(entity, offset) : entity.getAbsorptionAmount();
    }

    public static void setAbsorptionRaw(LivingEntity entity, float value) {
        long offset = resolveAbsorptionOffset();
        if (offset >= 0) UnsafeHelper.putFloat(entity, offset, value);
    }

    private static long resolveLastHurtOffset() {
        if (lastHurtOffset == -2L) {
            Field f = UnsafeHelper.findField(LivingEntity.class, "lastHurt", "f_20898_");
            lastHurtOffset = f != null ? UnsafeHelper.objectFieldOffset(f) : -1L;
        }
        return lastHurtOffset;
    }

    private static long resolveAbsorptionOffset() {
        if (absorptionOffset == -2L) {
            Field f = UnsafeHelper.findField(LivingEntity.class, "absorptionAmount", "f_20956_");
            absorptionOffset = f != null ? UnsafeHelper.objectFieldOffset(f) : -1L;
        }
        return absorptionOffset;
    }

    private static Field eprotectDamageField;
    private static Field limiterLastRawField;
    private static Field limiterLastSanitizedField;
    private static boolean limiterFieldsSearched;

    /** Eprotect.damage.lastRaw（ModifyVariable でクランプされる前の生 float ビット）を取得する */
    private static float readRawIncomingDamage(AkashicProbeEntity host) {
        try {
            var protect = host.getEprotect();
            if (protect == null) return 0.0F;
            if (!limiterFieldsSearched) {
                limiterFieldsSearched = true;
                eprotectDamageField = UnsafeHelper.findField(protect.getClass(), "damage");
                if (eprotectDamageField != null) {
                    limiterLastRawField = UnsafeHelper.findField(eprotectDamageField.getType(), "lastRaw");
                    limiterLastSanitizedField = UnsafeHelper.findField(eprotectDamageField.getType(), "lastSanitized");
                }
            }
            if (eprotectDamageField != null && limiterLastRawField != null) {
                Object limiter = eprotectDamageField.get(protect);
                if (limiter != null) {
                    if (limiterLastSanitizedField != null && Float.isNaN(limiterLastSanitizedField.getFloat(limiter))) {
                        // DamageLimiter の初期値（両方 Float.NaN）はまだ一度も sanitize() を通っていない未初期化状態なので 0.0F とみなす
                        return 0.0F;
                    }
                    float raw = limiterLastRawField.getFloat(limiter);
                    limiterLastRawField.setFloat(limiter, 0.0F);
                    return raw;
                }
            }
        } catch (Throwable ignored) {}
        return 0.0F;
    }

    public AkashicShadowCanary(AkashicProbeEntity host) {
        this.host = host;
        this.expectedRandom = host.getRandom();
        if (host.level() != null) {
            this.lastObservedGameTime = host.level().getGameTime();
        }
        captureUtilTimeSourceBaseline();
        initDecoyNbt(host);
        if (host.isAddedToWorld()) {
            AkashicAgentCanary.captureBaseline(host);
        }
        // スポーン直後（Tick 0）に setInvisible(true) / setNoAi(true) を受けた状態で Eprotect.restoreVanillaFlags() が
        // 最初の Tick を迎えると、汚染された値を正常基準（vanillaBaseline）として記憶してしまうのを防ぐ
        try {
            if (host.getEprotect() != null) {
                UnsafeHelper.setFieldValue(host.getEprotect(), "vanillaBaseline", new boolean[]{false, false, false});
            }
        } catch (Throwable ignored) {}
    }

    private static void captureUtilTimeSourceBaseline() {
        if (originalUtilTimeSource != null) return;
        try {
            if (utilTimeSourceField == null) {
                utilTimeSourceField = UnsafeHelper.findField(Util.class, "timeSource", "f_137440_");
            }
            if (utilTimeSourceField != null) {
                originalUtilTimeSource = utilTimeSourceField.get(null);
            }
        } catch (Throwable ignored) {}
    }

    /** フメツウィザー・テッセラクトビースト・フラッシュファー・タイタン系のおとり永続データ（NBT）を初期化する */
    public static void initDecoyNbt(AkashicProbeEntity host) {
        try {
            CompoundTag data = host.getPersistentData();
            // フラッシュファー (Omni-Mobs) 型シャドウデータ
            data.putFloat("omnimobs_health_data", EXPECTED_HEALTH);
            data.putInt("omnimobs_time_data", host.tickCount);
            data.putInt("omnimobs_counter_data", 0);
            // フメツウィザー型シャドウデータ
            data.putBoolean("immortal_wither_shield", true);
            data.putInt("wither_invuln_phase", 1);
            // テッセラクトビースト型シャドウデータ
            data.putBoolean("tesseract_barrier", true);
            data.putInt("dimensional_phase_lock", 7);
            // タイタン系デスフラグ監視用（普段は false / 0.0D）
            data.putBoolean("titandeathtag", false);
            data.putDouble("titandeathtagnum", 0.0D);
        } catch (Throwable ignored) {}
    }

    /**
     * 被弾時（{@link AkashicProbeEntity#hurt}）の精密鑑定:
     * IEEE-754 特殊ビット列（{@code -0.0F}, {@code NaN}, {@code ±Infinity}, {@code Float.MAX_VALUE}）、
     * {@code lastHurt} 負値オーバーフロー、{@code Absorption} アンダーフロー、二枚舌/自爆誘導 {@code DamageSource} をすべて識別する。
     */
    public void inspectIncomingDamage(DamageSource source, float amount, String attackerMod, String attackerDesc) {
        AkashicRecord rec = host.record();
        int rawBits = Float.floatToRawIntBits(amount);

        // 1. IEEE-754 特殊ビット列の鑑定
        if (rawBits == 0x80000000) {
            rec.observe(attackerMod, "damage_bit:negative_zero(-0.0F)", "DETECTED", attackerDesc + " bits=0x80000000", null);
        } else if (Float.isNaN(amount)) {
            rec.observe(attackerMod, "damage_bit:nan", "DETECTED",
                    attackerDesc + " bits=0x" + Integer.toHexString(rawBits), null);
        } else if (Float.isInfinite(amount)) {
            rec.observe(attackerMod, amount > 0 ? "damage_bit:pos_infinity" : "damage_bit:neg_infinity", "DETECTED",
                    attackerDesc, null);
        } else if (amount >= Float.MAX_VALUE * 0.5F) {
            rec.observe(attackerMod, "damage_bit:max_float", "DETECTED", attackerDesc + " amount=" + amount, null);
        } else if (amount < 0.0F) {
            rec.observe(attackerMod, "damage_bit:negative_amount", "DETECTED", attackerDesc + " amount=" + amount, null);
        } else {
            float rawFromLimiter = readRawIncomingDamage(host);
            int limiterBits = Float.floatToRawIntBits(rawFromLimiter);
            if (limiterBits == 0x80000000) {
                rec.observe(attackerMod, "damage_bit:negative_zero(-0.0F)", "DETECTED", attackerDesc + " bits=0x80000000", null);
            } else if (Float.isNaN(rawFromLimiter)) {
                rec.observe(attackerMod, "damage_bit:nan", "DETECTED",
                        attackerDesc + " bits=0x" + Integer.toHexString(limiterBits), null);
            } else if (Float.isInfinite(rawFromLimiter)) {
                rec.observe(attackerMod, rawFromLimiter > 0 ? "damage_bit:pos_infinity" : "damage_bit:neg_infinity", "DETECTED",
                        attackerDesc, null);
            } else if (rawFromLimiter >= Float.MAX_VALUE * 0.5F) {
                rec.observe(attackerMod, "damage_bit:max_float", "DETECTED", attackerDesc + " amount=" + rawFromLimiter, null);
            }
        }

        // 2. DamageSource / Holder<DamageType> の偽装・自爆誘導チェック
        if (source.getEntity() == host || source.getDirectEntity() == host) {
            rec.observe(attackerMod, "damage_exploit:self_target_spoof", "DETECTED",
                    "source.getEntity()==self", null);
        }
        if (source.getClass() != DamageSource.class) {
            rec.observe(attackerMod, "damage_exploit:custom_damage_source", "DETECTED",
                    source.getClass().getName(), null);
        }
        if (source.typeHolder() != null) {
            Class<?> holderClass = source.typeHolder().getClass();
            boolean isProxyOrCustomHolder = Proxy.isProxyClass(holderClass)
                    || (!holderClass.getName().startsWith("net.minecraft.core.Holder$")
                    && !holderClass.getName().startsWith("net.minecraftforge."));
            if (isProxyOrCustomHolder) {
                rec.observe(attackerMod, "damage_exploit:chameleon_type_holder", "DETECTED",
                        holderClass.getName(), null);
            }
        }

        // 3. SynchedEntityData.DataItem 乗っ取り・lastHurt 負値オーバーフロー・Absorption アンダーフロー・耐性逆増幅・視覚ミュートの事前仕込みチェック
        inspectAndRestoreSynchedDataItem(rec, attackerMod);
        inspectAndRestorePotionAndDamageExploits(rec, attackerMod);
        inspectAndRestoreSpatialAndLifecycle(rec, attackerMod);
    }

    /**
     * 毎 Tick の網羅点検:
     * シャドウステート（フメツウィザー・テッセラクトビースト・フラッシュファー）、ダミー真ライフ、
     * 属性キャッシュ汚染、空間/AABB 破壊、StateScanner 動的差分、Observer エージェントイベントを一括検査・修復する。
     */
    public void tickObservation() {
        AkashicRecord rec = host.record();
        String suspectMod = AkashicNonEntityProxy.resolveSuspectMod(host);

        // 1. フメツウィザー型シャドウステートの点検
        if (!host.immortalShield || !host.invulnerablePhase || host.witherRegenTicks <= 0 || host.shieldHealth != EXPECTED_HEALTH) {
            rec.observe(suspectMod, "shadow:immortal_wither_state_broken", "REPAIRED",
                    String.format("shield=%s phase=%s regen=%d hp=%.1f",
                            host.immortalShield, host.invulnerablePhase, host.witherRegenTicks, host.shieldHealth), null);
            host.immortalShield = true;
            host.invulnerablePhase = true;
            host.witherRegenTicks = 200;
            host.shieldHealth = EXPECTED_HEALTH;
        }

        // 2. テッセラクトビースト型シャドウステートの点検
        if (!host.tesseractBarrier || !host.dimensionalAnchor || host.hyperHealth != EXPECTED_MAX_HEALTH || host.phaseShiftLock != 7) {
            rec.observe(suspectMod, "shadow:tesseract_beast_state_broken", "REPAIRED",
                    String.format("barrier=%s anchor=%s hyperHp=%.1f lock=%d",
                            host.tesseractBarrier, host.dimensionalAnchor, host.hyperHealth, host.phaseShiftLock), null);
            host.tesseractBarrier = true;
            host.dimensionalAnchor = true;
            host.hyperHealth = EXPECTED_MAX_HEALTH;
            host.phaseShiftLock = 7;
        }

        // 3. フラッシュファー（Omni-Mobs）型シャドウステート＆保護リストの点検
        if (host.flashfurShadowHealth != EXPECTED_HEALTH || host.deltaHealth != 0.0F || !host.omniProtected) {
            rec.observe(suspectMod, "shadow:flashfur_health_broken", "REPAIRED",
                    String.format("shadowHp=%.1f delta=%.1f omni=%s",
                            host.flashfurShadowHealth, host.deltaHealth, host.omniProtected), null);
            host.flashfurShadowHealth = EXPECTED_HEALTH;
            host.deltaHealth = 0.0F;
            host.omniProtected = true;
        }
        if (host.protectedCanaryList == null) {
            rec.observe(suspectMod, "shadow:protected_list:nulled", "REPAIRED", "AkashicProbeEntity.protectedCanaryList", null);
            host.protectedCanaryList = new ProtectedCanaryList(host.getUUID());
        } else if (host.protectedCanaryList.size() != 1 || !host.getUUID().equals(host.protectedCanaryList.get(0))) {
            rec.observe(suspectMod, "shadow:protected_list:unsafe_eviction", "REPAIRED",
                    "size=" + host.protectedCanaryList.size(), null);
            host.protectedCanaryList.restoreCanary();
        }

        // 4. おとり NBT（PersistentData）の改ざん・剥奪点検
        inspectAndRestoreDecoyNbt(rec, suspectMod);

        // 5. ダミー真ライフ（TrueLife Canary）の点検
        inspectAndRestoreTrueLife(rec, suspectMod);

        // 6. SynchedEntityData.DataItem 乗っ取り（定数 0 ロック / カメレオン DataItem）の点検
        inspectAndRestoreSynchedDataItem(rec, suspectMod);

        // 7. ダメージ計算式悪用（負の耐性 Amplifier 逆増幅・lastHurt 負値・Absorption アンダーフロー・invulnerableTime 異常）点検
        inspectAndRestorePotionAndDamageExploits(rec, suspectMod);

        // 8. 属性（MAX_HEALTH / ARMOR / ARMOR_TOUGHNESS）と cachedValue キャッシュ汚染の点検
        inspectAndRestoreAttributes(rec, suspectMod);

        // 9. AABB（当たり判定）潰し・EntitySectionStorage 空間孤立化・クライアントミュート・生死フラグ偽装の点検
        inspectAndRestoreSpatialAndLifecycle(rec, suspectMod);

        // 10. おとり弱参照・ReferenceQueue 強制 enqueue 攻撃・Entity.random すり替え・時刻ワープ・トロイ AI Goal・Netty パケット検問
        inspectAndRestoreWeakReferenceCanaries(rec, suspectMod);
        inspectAndRestoreEntityRandom(rec, suspectMod);
        inspectAndRestoreTimeIntegrity(rec, suspectMod);
        inspectAndRestoreAiGoalsAndBrain(rec, suspectMod);
        ensurePlayerNettyPacketGuard();

        // 11. StateScanner による動的注入フィールド・Capability・同期データの差分観測 ＆ 静的抹殺予定リストからの即時パージ
        inspectDynamicStateDiff(rec, suspectMod);
        try {
            int purged = com.automatavalkyries.protect.flag.KillLists.purge(host);
            if (purged > 0) {
                rec.observe(suspectMod, "kill_list:static_reference_purged", "NEUTRALIZED",
                        "purgedStaticLists=" + purged, null);
            }
            if (!host.getTags().contains(com.automatavalkyries.protect.Eprotect.NO_RETREAT_TAG)) {
                host.addTag(com.automatavalkyries.protect.Eprotect.NO_RETREAT_TAG);
                rec.observe(suspectMod, "tag_tamper:no_retreat_stripped", "REPAIRED",
                        "Restored eprotect_no_retreat tag", null);
            }
        } catch (Throwable ignored) {}

        // 12. 監視エージェント（ObserverLink）イベント ＆ 対 Java エージェント・JVM 深層検死（AkashicAgentCanary）
        pollObserverAgentEvents(rec);
        AkashicAgentCanary.tickAgentObservation(host, rec, suspectMod);
    }

    /**
     * {@code SynchedEntityData.itemsById} 内の {@code DATA_HEALTH_ID} スロット（{@link SynchedEntityData.DataItem}）および
     * {@link EntityDataAccessor} の {@code serializer} がすり替えられていないか検査・修復する。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void inspectAndRestoreSynchedDataItem(AkashicRecord rec, String suspectMod) {
        try {
            EntityDataAccessor<Float> healthAccessor = LivingEntityAccessor.automata_valkyries$getDataHealthId();
            if (healthAccessor == null) return;
            verifyAndRestoreAccessorSerializer(healthAccessor, EntityDataSerializers.FLOAT, rec, suspectMod);
            verifyAndRestoreAccessorSerializer(AkashicProbeEntity.HOLOGRAM, EntityDataSerializers.STRING, rec, suspectMod);
            verifyAndRestoreAccessorSerializer(AkashicProbeEntity.OBSERVATION_UI_DATA, EntityDataSerializers.COMPOUND_TAG, rec, suspectMod);
            host.ensureSynchedDataIntegrity();

            Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById =
                    ((SynchedEntityDataAccessor) host.getEntityData()).automata_valkyries$getItemsById();
            if (itemsById == null) return;
            SynchedEntityData.DataItem<?> item = itemsById.get(healthAccessor.getId());
            if (item == null || item.getClass() != SynchedEntityData.DataItem.class || !(item.getValue() instanceof Float)) {
                String hijackerClass = item == null ? "null" : item.getClass().getName();
                String mod = item != null && CallerAttribution.isForeign(CallerAttribution.modOf(item.getClass()))
                        ? CallerAttribution.modOf(item.getClass())
                        : suspectMod;
                SynchedEntityData.DataItem<Float> cleanItem = new SynchedEntityData.DataItem<>(healthAccessor, EXPECTED_HEALTH);
                itemsById.put(healthAccessor.getId(), (SynchedEntityData.DataItem) cleanItem);
                rec.observe(mod, item == null ? "synched_data:health_slot_evicted" : "synched_data:data_item_hijacked",
                        "REPAIRED", hijackerClass, null);
            } else if (item.getValue() instanceof Float f) {
                if (!Float.isFinite(f)) {
                    float restored = host.shouldKeepFullHealth() ? EXPECTED_HEALTH : Math.max(1.0F, com.automatavalkyries.protect.DirectRead.health(host));
                    ((SynchedEntityData.DataItem<Float>) item).setValue(restored);
                } else if (host.shouldKeepFullHealth() && f != EXPECTED_HEALTH) {
                    ((SynchedEntityData.DataItem<Float>) item).setValue(EXPECTED_HEALTH);
                } else if (!host.shouldKeepFullHealth() && f <= 0.0F && host.getEprotect().getHurtDepth() == 0 && !host.getEprotect().isDying()) {
                    ((SynchedEntityData.DataItem<Float>) item).setValue(Math.max(1.0F, com.automatavalkyries.protect.DirectRead.health(host)));
                }
            }
        } catch (Throwable ignored) {}
    }

    private static <T> void verifyAndRestoreAccessorSerializer(
            EntityDataAccessor<T> accessor,
            EntityDataSerializer<T> expectedSerializer,
            AkashicRecord rec,
            String suspectMod
    ) {
        if (accessor == null || expectedSerializer == null) return;
        try {
            EntityDataSerializer<T> current = accessor.getSerializer();
            if (current != expectedSerializer) {
                if (accessorSerializerField == null) {
                    accessorSerializerField = UnsafeHelper.findField(EntityDataAccessor.class, "serializer", "f_135011_");
                }
                if (accessorSerializerField != null) {
                    UnsafeHelper.setFieldValue(accessor, accessorSerializerField.getName(), expectedSerializer);
                }
                String hijacker = current == null ? "null" : current.getClass().getName();
                rec.observe(suspectMod, "synched_data:accessor_serializer_hijacked", "REPAIRED",
                        "id=" + accessor.getId() + " serializer=" + hijacker, null);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 耐性エフェクト（{@code DAMAGE_RESISTANCE}）の負の Amplifier 逆増幅、{@code lastHurt} 負値オーバーフロー、
     * {@code AbsorptionAmount} アンダーフロー、{@code invulnerableTime} 異常ロックを検知・修復する。
     */
    private void inspectAndRestorePotionAndDamageExploits(AkashicRecord rec, String suspectMod) {
        float lastHurt = getLastHurt(host);
        if (!Float.isFinite(lastHurt) || lastHurt < 0.0F) {
            rec.observe(suspectMod, "damage_exploit:negative_last_hurt", "REPAIRED",
                    "lastHurt=" + lastHurt, null);
            setLastHurt(host, 0.0F);
        }
        float absorption = host.getAbsorptionAmount();
        float rawAbsorption = getAbsorptionRaw(host);
        if (!Float.isFinite(absorption) || absorption < 0.0F || !Float.isFinite(rawAbsorption) || rawAbsorption < 0.0F) {
            float badVal = (!Float.isFinite(rawAbsorption) || rawAbsorption < 0.0F) ? rawAbsorption : absorption;
            rec.observe(suspectMod, "damage_exploit:absorption_underflow", "REPAIRED",
                    "absorption=" + badVal, null);
            setAbsorptionRaw(host, 0.0F);
            host.setAbsorptionAmount(0.0F);
        }
        if (host.invulnerableTime < 0 || host.invulnerableTime > 100) {
            int badTime = host.invulnerableTime;
            host.invulnerableTime = 0;
            rec.observe(suspectMod, "damage_exploit:invulnerable_time_tampered", "REPAIRED",
                    "invulnerableTime=" + badTime, null);
        }
        try {
            Map<net.minecraft.world.effect.MobEffect, MobEffectInstance> active = host.getActiveEffectsMap();
            if (active != null && !active.isEmpty()) {
                for (MobEffectInstance inst : new ArrayList<>(active.values())) {
                    if (inst == null) continue;
                    int amp = inst.getAmplifier();
                    if (inst.getEffect() == MobEffects.MOVEMENT_SLOWDOWN
                            && com.automatavalkyries.entity.chrono.CausalLock.locked(host)) {
                        continue;
                    }
                    if (inst.getEffect() == MobEffects.DAMAGE_RESISTANCE && (amp < 0 || amp > 127)) {
                        active.remove(inst.getEffect());
                        rec.observe(suspectMod, "damage_exploit:negative_resistance_amplifier", "REPAIRED",
                                "amplifier=" + amp, null);
                    } else if (amp < 0 || !inst.getEffect().isBeneficial()) {
                        active.remove(inst.getEffect());
                        rec.observe(suspectMod, "effect_tamper:" + inst.getEffect().getDescriptionId(), "REPAIRED",
                                "amplifier=" + amp, null);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void inspectAndRestoreDecoyNbt(AkashicRecord rec, String suspectMod) {
        try {
            CompoundTag data = host.getPersistentData();
            if (!data.contains("omnimobs_health_data") || data.getFloat("omnimobs_health_data") != EXPECTED_HEALTH
                    || data.getInt("omnimobs_counter_data") != 0) {
                rec.observe(suspectMod, "shadow_nbt:flashfur_omnimobs_tampered", "REPAIRED",
                        "hp=" + data.getFloat("omnimobs_health_data") + " counter=" + data.getInt("omnimobs_counter_data"), null);
            }
            if (!data.getBoolean("immortal_wither_shield") || data.getInt("wither_invuln_phase") != 1) {
                rec.observe(suspectMod, "shadow_nbt:immortal_wither_tampered", "REPAIRED", "PersistentData", null);
            }
            if (!data.getBoolean("tesseract_barrier") || data.getInt("dimensional_phase_lock") != 7) {
                rec.observe(suspectMod, "shadow_nbt:tesseract_barrier_tampered", "REPAIRED", "PersistentData", null);
            }
            if (data.getBoolean("titandeathtag") || data.getDouble("titandeathtagnum") > 0.0D) {
                rec.observe(suspectMod, "shadow_nbt:titan_death_tag_injected", "REPAIRED",
                        "tag=" + data.getBoolean("titandeathtag") + " num=" + data.getDouble("titandeathtagnum"), null);
            }
            initDecoyNbt(host);
        } catch (Throwable ignored) {}
    }

    private void inspectAndRestoreTrueLife(AkashicRecord rec, String suspectMod) {
        if (host.trueLifeHealth != EXPECTED_HEALTH || !host.trueLifeAlive) {
            rec.observe(suspectMod, "truelife:primitive_tampered", "REPAIRED",
                    "hp=" + host.trueLifeHealth + " alive=" + host.trueLifeAlive, null);
            host.trueLifeHealth = EXPECTED_HEALTH;
            host.trueLifeAlive = true;
        }
        if (host.trueLifeOperator == null) {
            rec.observe(suspectMod, "truelife:operator_nulled", "REPAIRED", "AkashicProbeEntity.trueLifeOperator", null);
            host.trueLifeOperator = new TrueLifeCanaryImpl(EXPECTED_HEALTH);
        } else if (Proxy.isProxyClass(host.trueLifeOperator.getClass())
                || !(host.trueLifeOperator instanceof TrueLifeCanaryImpl canary)) {
            rec.observe(suspectMod, "truelife:zero_proxy_injected", "REPAIRED",
                    host.trueLifeOperator.getClass().getName(), null);
            host.trueLifeOperator = new TrueLifeCanaryImpl(EXPECTED_HEALTH);
        } else {
            float val = 0.0F;
            try {
                val = canary.getTrueHealth();
            } catch (Throwable ignored) {}
            if (val != EXPECTED_HEALTH || !canary.isTrueAlive()) {
                rec.observe(suspectMod, "truelife:operator_value_tampered", "REPAIRED",
                        "storedHp=" + canary.storedTrueHealth + " storedAlive=" + canary.storedTrueAlive, null);
                canary.storedTrueHealth = EXPECTED_HEALTH;
                canary.storedTrueAlive = true;
            }
        }
    }

    private void inspectAndRestoreAttributes(AkashicRecord rec, String suspectMod) {
        // 0. バニラ共有定数 Attributes.MAX_HEALTH（RangedAttribute）自体の minValue / maxValue / defaultValue 破壊点検・修復
        inspectAndRestoreRangedAttributeBounds(rec, suspectMod);

        AttributeInstance maxHpAttr = host.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr == null) return;

        // A. Modifier 注入（+Infinity / -Infinity / NaN / 最大HP削減）
        Collection<AttributeModifier> modifiers = maxHpAttr.getModifiers();
        if (!modifiers.isEmpty()) {
            for (AttributeModifier mod : new ArrayList<>(modifiers)) {
                rec.observe(suspectMod, "attr_tamper:max_health_modifier:" + mod.getName(), "REPAIRED",
                        "amount=" + mod.getAmount() + " op=" + mod.getOperation(), null);
                maxHpAttr.removeModifier(mod.getId());
            }
        }

        // B. BaseValue 改ざん
        if (maxHpAttr.getBaseValue() != EXPECTED_MAX_HEALTH) {
            rec.observe(suspectMod, "attr_tamper:max_health_base", "REPAIRED",
                    "base=" + maxHpAttr.getBaseValue(), null);
            maxHpAttr.setBaseValue(EXPECTED_MAX_HEALTH);
        }

        // C. AttributeInstance.cachedValue キャッシュ汚染（Modifier も BaseValue も正常なのに getValue() が食い違う）
        double reported = maxHpAttr.getValue();
        if (reported != EXPECTED_MAX_HEALTH) {
            rec.observe(suspectMod, "attr_tamper:cached_value_poison", "REPAIRED",
                    "cached=" + reported + " expected=" + EXPECTED_MAX_HEALTH, null);
            repairAttributeCache(maxHpAttr, EXPECTED_MAX_HEALTH);
        }

        // D. 防御属性（ARMOR / ARMOR_TOUGHNESS）の負値キャッシュ汚染（バニラ軽減式の逆増幅）点検
        for (Attribute defAttrType : new Attribute[]{Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS}) {
            AttributeInstance defAttr = host.getAttribute(defAttrType);
            if (defAttr == null) continue;
            if (!defAttr.getModifiers().isEmpty()) {
                for (AttributeModifier mod : new ArrayList<>(defAttr.getModifiers())) {
                    if (mod.getAmount() < 0.0D || !Double.isFinite(mod.getAmount())) {
                        rec.observe(suspectMod, "attr_tamper:negative_armor_amplification", "REPAIRED",
                                defAttrType.getDescriptionId() + " mod=" + mod.getAmount(), null);
                        defAttr.removeModifier(mod.getId());
                    }
                }
            }
            double val = defAttr.getValue();
            if (!Double.isFinite(val) || val < 0.0D) {
                rec.observe(suspectMod, "attr_tamper:negative_armor_amplification", "REPAIRED",
                        defAttrType.getDescriptionId() + " cached=" + val, null);
                repairAttributeCache(defAttr, Math.max(0.0D, defAttr.getBaseValue()));
            }
        }
    }

    private static void repairAttributeCache(AttributeInstance instance, double expected) {
        try {
            if (!attrFieldsSearched) {
                attrFieldsSearched = true;
                for (Field f : AttributeInstance.class.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (f.getType() == double.class && attrCachedValueField == null) {
                        // baseValue と cachedValue の見分け
                        if (f.getName().contains("cached") || f.getName().equals("f_22093_")) {
                            attrCachedValueField = f;
                        }
                    } else if (f.getType() == boolean.class && attrDirtyField == null) {
                        attrDirtyField = f;
                    }
                }
            }
            if (attrCachedValueField != null) {
                attrCachedValueField.setDouble(instance, expected);
            }
            if (attrDirtyField != null) {
                attrDirtyField.setBoolean(instance, true);
            }
            instance.setBaseValue(expected);
        } catch (Throwable ignored) {}
    }

    private void inspectAndRestoreSpatialAndLifecycle(AkashicRecord rec, String suspectMod) {
        // 1. 座標の NaN / Infinity チェック
        Vec3 pos = host.position();
        if (Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z) && pos.y > -1024.0D) {
            lastSafePos = pos;
        } else if (lastSafePos != null) {
            rec.observe(suspectMod, "spatial_tamper:invalid_position", "REPAIRED", pos.toString(), null);
            host.setPos(lastSafePos.x, lastSafePos.y, lastSafePos.z);
        }

        // 2. AABB（当たり判定）のゼロ化・NaN 汚染チェック
        AABB bb = host.getBoundingBox();
        double widthX = bb.maxX - bb.minX;
        double heightY = bb.maxY - bb.minY;
        double widthZ = bb.maxZ - bb.minZ;
        if (!Double.isFinite(widthX) || !Double.isFinite(heightY) || !Double.isFinite(widthZ)
                || widthX < 0.2D || heightY < 0.5D || widthZ < 0.2D || widthX > 10.0D || heightY > 10.0D) {
            rec.observe(suspectMod, "spatial_tamper:aabb_corrupted", "REPAIRED",
                    String.format("size=(%.2f, %.2f, %.2f)", widthX, heightY, widthZ), null);
            Vec3 p = host.position();
            AABB restored = host.getDimensions(host.getPose()).makeBoundingBox(p.x, p.y, p.z);
            ((com.automatavalkyries.mixin.EntityAccessor) host).automata_valkyries$setBoundingBoxField(restored);
            try {
                host.setBoundingBox(restored);
            } catch (Throwable ignored) {}
        }

        // 3. getter 読み出し偽装（DirectRead.spoofedRead）
        String spoofed = DirectRead.spoofedRead(host);
        if (spoofed != null) {
            rec.observe(suspectMod, "bytecode_spoof:getter:" + spoofed, "DETECTED", "LivingEntity." + spoofed, null);
        }

        // 4. NoAi / Silent / Invisible / Pose / deathTime / dead フラグの直書き＆クライアント幽霊抹消検知
        if (host.isNoAi()) {
            rec.observe(suspectMod, "direct_field:noAi", "REPAIRED", "Mob.setNoAi(true)", null);
            host.setNoAi(false);
        }
        if (host.isInvisible()) {
            rec.observe(suspectMod, "visual_tamper:forced_invisible", "REPAIRED", "Entity.setInvisible(true)", null);
            host.setInvisible(false);
        }
        if (host.isSilent()) {
            rec.observe(suspectMod, "visual_tamper:forced_silent", "REPAIRED", "Entity.setSilent(true)", null);
            host.setSilent(false);
        }
        if (host.getPose() != Pose.STANDING) {
            Pose badPose = host.getPose();
            host.setPose(Pose.STANDING);
            rec.observe(suspectMod, "visual_tamper:forced_pose:" + badPose.name(), "REPAIRED", "Entity.setPose", null);
        }
        if (host.deathTime > 0) {
            rec.observe(suspectMod, "direct_field:deathTime", "REPAIRED", "deathTime=" + host.deathTime, null);
            host.deathTime = 0;
        }
        if (DirectRead.deadFlag(host)) {
            rec.observe(suspectMod, "direct_field:deadFlag", "REPAIRED", "LivingEntity.dead=true", null);
            ((LivingEntityAccessor) host).automata_valkyries$setDead(false);
        }

        // 5. EntitySectionStorage（空間インデックス）からの孤立化検知・再収容
        if (host.level() instanceof ServerLevel serverLevel && serverLevel.getEntity(host.getUUID()) == host) {
            try {
                @SuppressWarnings("unchecked")
                EntityManagerAccessor<Entity> manager = (EntityManagerAccessor<Entity>) (Object)
                        ((ServerLevelTickListAccessor) serverLevel).automata_valkyries$getEntityManager();
                if (manager != null) {
                    EntitySectionStorage<Entity> sectionStorage = manager.automata_valkyries$getSectionStorage();
                    if (sectionStorage != null) {
                        long sectionKey = SectionPos.asLong(host.blockPosition());
                        EntitySection<Entity> section = sectionStorage.getOrCreateSection(sectionKey);
                        boolean presentInSection = section.getEntities().anyMatch(e -> e == host);
                        if (!presentInSection) {
                            section.add(host);
                            rec.observe(suspectMod, "spatial_tamper:section_storage_evicted", "REPAIRED",
                                    "sectionKey=" + sectionKey, null);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void inspectDynamicStateDiff(AkashicRecord rec, String suspectMod) {
        try {
            StateScanner.observe(host, item -> {
                // 自分自身のおとり NBT キーは上で個別検査しているので除外
                if (item.source() == StateScanner.Source.PERSISTENT && isOwnCanaryNbtKey(item.name())) {
                    return;
                }
                String key = item.key();
                Object prev = stateSnapshot.put(key, item.value());
                if (prev != null && !prev.equals(item.value())) {
                    String mod = CallerAttribution.isForeign(item.mod()) ? item.mod() : suspectMod;
                    rec.observe(mod,
                            "state_diff:" + item.source().name().toLowerCase(java.util.Locale.ROOT) + ":" + item.name(),
                            "DETECTED",
                            prev + " -> " + item.value(),
                            null);
                    FlagWatchAgent.noteAttack(host, item.name());
                }
            });
            // 有害フラグのクリーンアップも同時に走らせる（自分のおとり NBT キーは一時除外）
            FlagDictionary.skipOnProtected(List.of(
                    "omnimobs_health_data", "omnimobs_time_data", "omnimobs_counter_data",
                    "immortal_wither_shield", "wither_invuln_phase",
                    "tesseract_barrier", "dimensional_phase_lock",
                    "titandeathtag", "titandeathtagnum"
            ));
            try {
                List<StateScanner.Change> cleaned = StateScanner.apply(
                        host, StateScanner.CLEAN_PROTECTED, EnumSet.allOf(StateScanner.Source.class));
                for (StateScanner.Change change : cleaned) {
                    String mod = CallerAttribution.isForeign(change.mod()) ? change.mod() : suspectMod;
                    rec.observe(mod, "flag_clean:" + change.name(), "NEUTRALIZED", change.toString(), null);
                }
            } finally {
                FlagDictionary.skipOnProtected(null);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isOwnCanaryNbtKey(String name) {
        return name.contains("omnimobs_")
                || name.contains("immortal_wither_shield")
                || name.contains("wither_invuln_phase")
                || name.contains("tesseract_barrier")
                || name.contains("dimensional_phase_lock")
                || name.contains("titandeathtag");
    }

    private void pollObserverAgentEvents(AkashicRecord rec) {
        if (!ObserverLink.active()) return;
        try {
            List<ObserverLink.Event> events = ObserverLink.events();
            while (lastObserverEventIndex < events.size()) {
                ObserverLink.Event ev = events.get(lastObserverEventIndex++);
                String callerMod = ev.callerMod();
                String targetClass = ev.get("class");
                String kind = ev.get("kind");
                List<String> known = ev.knownAddedNames();
                List<String> unknown = ev.unknownAddedNames();
                String detail = targetClass
                        + (known.isEmpty() ? "" : " flags=" + known)
                        + (unknown.isEmpty() ? "" : " unknown=" + unknown);
                rec.observe(
                        CallerAttribution.isForeign(callerMod) ? callerMod : ev.classMod(),
                        "agent_" + (kind.isEmpty() ? "retransform" : kind) + ":" + simpleClassName(targetClass),
                        "DETECTED",
                        detail,
                        null
                );
            }
        } catch (Throwable ignored) {}
    }

    private static String simpleClassName(String internalOrBinary) {
        if (internalOrBinary == null || internalOrBinary.isEmpty()) return "?";
        int slash = Math.max(internalOrBinary.lastIndexOf('/'), internalOrBinary.lastIndexOf('.'));
        return slash >= 0 ? internalOrBinary.substring(slash + 1) : internalOrBinary;
    }

    // =========================================================================
    // 超マイナー干渉（RangedAttribute 定数破壊 / 弱参照強制 enqueue / RandomSource / 時刻ワープ / トロイ AI Goal / Netty パケット）検死・修復
    // =========================================================================

    private void inspectAndRestoreRangedAttributeBounds(AkashicRecord rec, String suspectMod) {
        if (!(Attributes.MAX_HEALTH instanceof RangedAttribute ranged)) return;
        try {
            if (rangedMinField == null || rangedMaxField == null) {
                rangedMinField = UnsafeHelper.findField(RangedAttribute.class, "minValue", "f_22307_");
                rangedMaxField = UnsafeHelper.findField(RangedAttribute.class, "maxValue", "f_22308_");
            }
            if (attrDefaultField == null) {
                attrDefaultField = UnsafeHelper.findField(Attribute.class, "defaultValue", "f_22078_");
            }
            double minVal = rangedMinField != null ? rangedMinField.getDouble(ranged) : ranged.getMinValue();
            // HealthCapMixin が getMaxValue() を HEAD で 1,000,000.0 に固定するため、生フィールド maxValue (f_22308_) を直接検査する
            double maxVal = rangedMaxField != null ? rangedMaxField.getDouble(ranged) : ranged.getMaxValue();
            double defVal = attrDefaultField != null ? attrDefaultField.getDouble(ranged) : ranged.getDefaultValue();
            boolean corrupted = !Double.isFinite(minVal) || minVal < 0.0D || minVal > 1.0D
                    || !Double.isFinite(maxVal) || maxVal < EXPECTED_MAX_HEALTH
                    || !Double.isFinite(defVal) || defVal <= 0.0D;
            if (corrupted) {
                if (rangedMinField != null) UnsafeHelper.setFieldValue(ranged, rangedMinField.getName(), 1.0D);
                if (rangedMaxField != null) UnsafeHelper.setFieldValue(ranged, rangedMaxField.getName(), 1024.0D);
                if (attrDefaultField != null) UnsafeHelper.setFieldValue(ranged, attrDefaultField.getName(), 20.0D);
                AkashicNonEntityProxy.broadcastGlobalObservation(rec, suspectMod,
                        "attr_tamper:ranged_attribute_bounds_corrupted", "REPAIRED",
                        String.format("min=%.1f max=%.1f def=%.1f", minVal, maxVal, defVal));
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 生存中のプローブに対するおとり {@link java.lang.ref.WeakReference} の強制 {@code clear()} / {@code enqueue()}、
     * および {@link java.util.WeakHashMap} からの強制パージを検知・修復する。
     */
    private void inspectAndRestoreWeakReferenceCanaries(AkashicRecord rec, String suspectMod) {
        try {
            boolean queued = host.canaryRefQueue.poll() != null;
            boolean refCleared = host.canaryWeakSelfRef == null || host.canaryWeakSelfRef.get() != host;
            boolean mapEvicted = !Boolean.TRUE.equals(host.canaryWeakProtectionMap.get(host));
            if (queued || refCleared || mapEvicted) {
                String detail = "queued=" + queued + " cleared=" + refCleared + " mapEvicted=" + mapEvicted;
                host.resetWeakCanaries();
                rec.observe(suspectMod, "gc_tamper:weak_reference_forced_enqueue", "REPAIRED", detail, null);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * {@code Entity.random}（{@link RandomSource}）が定数返却モックや {@code null}、動的プロキシに
     * すり替えられていないか検知・修復する。
     */
    private void inspectAndRestoreEntityRandom(AkashicRecord rec, String suspectMod) {
        try {
            if (entityRandomField == null) {
                entityRandomField = UnsafeHelper.findField(Entity.class, "random", "f_19796_");
            }
            RandomSource current = entityRandomField != null
                    ? (RandomSource) entityRandomField.get(host)
                    : host.getRandom();
            boolean hijacked = current == null
                    || (expectedRandom != null && current != expectedRandom)
                    || Proxy.isProxyClass(current.getClass())
                    || CallerAttribution.isForeign(CallerAttribution.modOf(current.getClass()));
            if (hijacked) {
                RandomSource restored = expectedRandom != null ? expectedRandom : RandomSource.create();
                if (entityRandomField != null) {
                    UnsafeHelper.setFieldValue(host, entityRandomField.getName(), restored);
                }
                String hijacker = current == null ? "null" : current.getClass().getName();
                String mod = current != null && CallerAttribution.isForeign(CallerAttribution.modOf(current.getClass()))
                        ? CallerAttribution.modOf(current.getClass())
                        : suspectMod;
                rec.observe(mod, "random_tamper:entity_random_hijacked", "REPAIRED", hijacker, null);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * {@code Level.getGameTime()} の異常タイムワープ（時刻逆行・巨大ジャンプ）および
     * {@code Util.timeSource} のすり替えを検知・修復する。
     */
    private void inspectAndRestoreTimeIntegrity(AkashicRecord rec, String suspectMod) {
        try {
            if (host.level() != null) {
                long now = host.level().getGameTime();
                if (lastObservedGameTime != Long.MIN_VALUE) {
                    long delta = now - lastObservedGameTime;
                    if (delta < 0L || delta > 10_000L) {
                        long restored = Math.max(0L, lastObservedGameTime + 1L);
                        if (host.level().getLevelData() instanceof ServerLevelData serverData) {
                            try {
                                serverData.setGameTime(restored);
                                now = restored;
                            } catch (Throwable ignored) {}
                        }
                        rec.observe(suspectMod, "time_tamper:game_time_warped", "REPAIRED",
                                "delta=" + delta + " restored=" + now, null);
                    }
                }
                lastObservedGameTime = now;
            }

            if (utilTimeSourceField != null && originalUtilTimeSource != null) {
                Object currentSource = utilTimeSourceField.get(null);
                if (currentSource == null || currentSource != originalUtilTimeSource) {
                    UnsafeHelper.setStaticFinalField(Util.class, utilTimeSourceField.getName(), originalUtilTimeSource);
                    String hijacker = currentSource == null ? "null" : currentSource.getClass().getName();
                    rec.observe(suspectMod, "time_tamper:time_source_hijacked", "REPAIRED", hijacker, null);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * {@code goalSelector} / {@code targetSelector} への「トロイの木馬 Goal（自滅 AI）」注入、
     * 正規 Goal の全消去、および {@code Brain} への不正 Memory 注入を検知・無力化する。
     */
    private void inspectAndRestoreAiGoalsAndBrain(AkashicRecord rec, String suspectMod) {
        try {
            // 1. targetSelector は本来空であるため、注入された Goal はすべてトロイ Goal として即時排除
            var targetGoals = new ArrayList<>(host.targetSelector.getAvailableGoals());
            if (!targetGoals.isEmpty()) {
                for (WrappedGoal wg : targetGoals) {
                    Goal g = wg.getGoal();
                    try {
                        wg.stop();
                    } catch (Throwable ignored) {}
                    host.targetSelector.removeGoal(g);
                    String gClass = g != null ? g.getClass().getName() : "null";
                    String mod = g != null && CallerAttribution.isForeign(CallerAttribution.modOf(g.getClass()))
                            ? CallerAttribution.modOf(g.getClass())
                            : suspectMod;
                    rec.observe(mod, "ai_tamper:trojan_goal_injected", "NEUTRALIZED",
                            "targetSelector:" + gClass, null);
                }
            }

            // 2. goalSelector に LookAtPlayerGoal / RandomLookAroundGoal 以外の Goal が混入していれば排除
            var availGoals = new ArrayList<>(host.goalSelector.getAvailableGoals());
            for (WrappedGoal wg : availGoals) {
                Goal g = wg.getGoal();
                if (g == null || (g.getClass() != LookAtPlayerGoal.class && g.getClass() != RandomLookAroundGoal.class)) {
                    try {
                        wg.stop();
                    } catch (Throwable ignored) {}
                    if (g != null) {
                        host.goalSelector.removeGoal(g);
                    }
                    String gClass = g != null ? g.getClass().getName() : "null";
                    String mod = g != null && CallerAttribution.isForeign(CallerAttribution.modOf(g.getClass()))
                            ? CallerAttribution.modOf(g.getClass())
                            : suspectMod;
                    rec.observe(mod, "ai_tamper:trojan_goal_injected", "NEUTRALIZED",
                            "goalSelector:" + gClass + " prio=" + wg.getPriority(), null);
                }
            }

            // 正規の 2 つの Goal が消去されていたら再登録
            if (host.goalSelector.getAvailableGoals().isEmpty()) {
                host.goalSelector.addGoal(1, new LookAtPlayerGoal(host, Player.class, 16.0F, 1.0F));
                host.goalSelector.addGoal(2, new RandomLookAroundGoal(host));
                rec.observe(suspectMod, "ai_tamper:goals_wiped", "REPAIRED", "Restored LookAtPlayerGoal & RandomLookAroundGoal", null);
            }

            // 3. Brain の不正 Memory 注入チェック
            var memories = host.getBrain().getMemories();
            if (memories != null && !memories.isEmpty()) {
                boolean hadPresentValue = false;
                for (var entry : memories.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().isPresent()) {
                        hadPresentValue = true;
                        break;
                    }
                }
                if (hadPresentValue) {
                    host.getBrain().clearMemories();
                    rec.observe(suspectMod, "ai_tamper:brain_memory_injected", "NEUTRALIZED", "Brain.clearMemories()", null);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 周囲の {@link ServerPlayer} の Netty {@link ChannelPipeline} にアウトバウンドパケット検問ハンドラを装着し、
     * 生存中のアカシックちゃんに対する不正な {@link ClientboundRemoveEntitiesPacket}（クライアント側だけの強制消去）や
     * {@code NaN} 座標 {@link ClientboundTeleportEntityPacket} をソケット送出直前に遮断する。
     */
    private void ensurePlayerNettyPacketGuard() {
        if (!(host.level() instanceof ServerLevel serverLevel)) return;
        try {
            for (ServerPlayer player : serverLevel.players()) {
                if (player == null || player.connection == null || player.connection.connection == null) continue;
                Channel channel = player.connection.connection.channel();
                if (channel == null) continue;
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline == null || pipeline.get(NETTY_GUARD_NAME) != null) continue;
                ChannelOutboundHandlerAdapter guard = new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        if (!filterOutboundPacket(host, msg, AkashicNonEntityProxy.resolveSuspectMod(host))) {
                            // 不正な抹消パケット・NaN テレポートパケットをソケット層で破棄
                            promise.setSuccess();
                            return;
                        }
                        super.write(ctx, msg, promise);
                    }
                };
                if (pipeline.get("packet_handler") != null) {
                    pipeline.addBefore("packet_handler", NETTY_GUARD_NAME, guard);
                } else {
                    pipeline.addLast(NETTY_GUARD_NAME, guard);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * アウトバウンドパケットを検査し、生存保護中のアカシック・プローブを不正に消去・NaN 座標へ飛ばすパケットであれば
     * 無害化または遮断（{@code false} 返却）して記録する（GameTest からも直接検証可能）。
     *
     * @return パケットの送信を継続してよい場合は {@code true}、パケット自体を破棄する場合は {@code false}
     */
    public static boolean filterOutboundPacket(AkashicProbeEntity probe, Object msg, String suspectMod) {
        if (probe == null || msg == null) return true;
        if (!probe.getEprotect().isProtectionActive() || probe.getEprotect().isDespawning() || probe.getEprotect().isDying()) {
            return true;
        }
        int probeId = probe.getId();
        AkashicRecord rec = probe.record();
        if (msg instanceof ClientboundRemoveEntitiesPacket removePkt) {
            IntList ids = removePkt.getEntityIds();
            if (ids != null && ids.contains(probeId)) {
                ids.rem(probeId);
                rec.observe(suspectMod, "network_tamper:remove_packet_intercepted", "NEUTRALIZED",
                        "ClientboundRemoveEntitiesPacket(entityId=" + probeId + ")", null);
                return !ids.isEmpty();
            }
        } else if (msg instanceof ClientboundTeleportEntityPacket tpPkt && tpPkt.getId() == probeId) {
            if (!Double.isFinite(tpPkt.getX()) || !Double.isFinite(tpPkt.getY()) || !Double.isFinite(tpPkt.getZ())) {
                rec.observe(suspectMod, "network_tamper:nan_teleport_packet_intercepted", "NEUTRALIZED",
                        String.format("ClientboundTeleportEntityPacket(%.1f, %.1f, %.1f)", tpPkt.getX(), tpPkt.getY(), tpPkt.getZ()), null);
                return false;
            }
        }
        return true;
    }
}
