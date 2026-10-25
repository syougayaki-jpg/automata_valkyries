package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.SynchedEntityDataAccessor;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import com.automatavalkyries.protect.attack.CallerAttribution;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 事象観測体アカシック・プローブ（2026-10-25 ユーザー指定「アカシックちゃんのモデルを使って即死凶悪技術の観測に特化した特殊モブを作る」。
 * 仕様は docs/SPEC_AKASHIC_PROBE.md）。
 * <p>
 * その場に浮いて、近くのプレイヤーと攻撃してきた相手の方を向くだけで、攻撃も移動もしない。保護（{@link Eprotect}）で倒れず、HP も減らさない
 * （受けた被弾は記録してから満タンへ戻す）。受けた即死・凶悪技術（保護の攻撃の記録のうち自分が受けたもの）と被弾を {@link AkashicRecord} に
 * 記録し、初めて見た他 MOD は下調べにかけ、頭上のホログラムとログファイルへ出す。保護を突破されたら、その呼び出しの経路を最後の記録として残す。
 * シフトを押しながら素手で右クリックすると、正規に退場する
 * </p>
 */
public class AkashicProbeEntity extends PathfinderMob implements IProviderWithSpeech {

    /** 頭上のホログラムの行（JSON の部品を改行でつないだもの） */
    static final EntityDataAccessor<String> HOLOGRAM = SynchedEntityData.defineId(AkashicProbeEntity.class, EntityDataSerializers.STRING);
    /** 頭上の専用観測 UI パネル（Overhead Observation Console）のリアルタイムテレメトリ同期データ */
    static final EntityDataAccessor<CompoundTag> OBSERVATION_UI_DATA =
            SynchedEntityData.defineId(AkashicProbeEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private record SynchedSlotSnapshot(EntityDataAccessor<Object> accessor, Object initialValue) {}

    /**
     * アカシック・プローブの稼働モード（2026-10-25 ユーザー指定:
     * ①観測待機 / ②ダメージ系統オンリー検証 / ③即死系統オンリー検証 / ④全108種・総合段階検証 /
     * ⑤直接対決・ダメージ制限解除 / ⑥直接対決・ペナルティなし最強版）。
     */
    public enum Mode {
        /** ① 観測待機: 一切攻撃せず、MOD界最強格の耐性で相手の攻撃・凶悪技術を観測・記録し続ける（初期状態） */
        OBSERVE_STANDBY("① 観測待機 (Observe Standby)", ChatFormatting.AQUA),
        /** ② ダメージ系統オンリー詳細検証: ダメージ系技術のみ（全36種: 通常打撃〜特殊属性・極大Float/NaN・隔絶ダメージ）を段階執行し、ダメージ耐性を測定 */
        DAMAGE_ONLY_AUDIT("② ダメージ系統オンリー検証 (Damage-Only Audit 36)", ChatFormatting.YELLOW),
        /** ③ 即死系統オンリー詳細検証: hurt() を一切使わず即死・消去・状態破壊・ワールド抹消・終焉系技術のみ（全72種）を段階執行し、即死耐性を測定 */
        INSTANT_KILL_ONLY_AUDIT("③ 即死系統オンリー検証 (Instant-Kill-Only Audit 72)", ChatFormatting.LIGHT_PURPLE),
        /** ④ 全108種・総合段階検証: 最強格の耐性を維持したまま、対象に全10階層・108種の凶悪技術を段階的に執行して耐性を完全測定する */
        RESISTANCE_AUDIT("④ 全108種・総合段階検証 (Full 108-Tech Audit)", ChatFormatting.GOLD),
        /** ⑤ 直接対決・ダメージ制限解除: 即死・消去耐性は維持しつつ通常ダメージ上限と毎 Tick 全回復を解除し、ガチタイマンで自律反撃する */
        DIRECT_DUEL_UNCAPPED("⑤ 直接対決・ダメージ制限解除 (Direct Duel Uncapped)", ChatFormatting.RED),
        /** ⑥ 直接対決・ペナルティなし最強版: MOD界最強格の完全防御・全回復を一切弱体化させずに維持したまま、108種の凶悪技術庫を解放して全力迎撃する */
        DIRECT_DUEL_NO_PENALTY("⑥ 直接対決・ペナルティなし最強版 (Direct Duel No-Penalty)", ChatFormatting.DARK_PURPLE);

        public final String displayLabel;
        public final ChatFormatting color;

        Mode(String displayLabel, ChatFormatting color) {
            this.displayLabel = displayLabel;
            this.color = color;
        }

        public Mode next() {
            Mode[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    private Eprotect protect;
    private AkashicShadowCanary shadowCanary;
    private AkashicResistanceAuditor auditor;
    private Mode mode = Mode.OBSERVE_STANDBY;
    /** 手元: ホログラムの行（同期の文字列が変わったときだけ作り直す） */
    private String hologramSource = "";
    private List<Component> hologramLines = List.of();
    /** SynchedEntityData.itemsById からスロットごと抜かれたり null 化された際に即時復元するための初期スロットスナップショット */
    private final Map<Integer, SynchedSlotSnapshot> baselineSynchedSlots = new java.util.LinkedHashMap<>();
    private boolean repairingSynchedData;

    // ---- おとりシャドウステート・ダミー真ライフ・非エンティティプロキシ参照（他 MOD のメモリ破壊・フィールド走査を誘引・検知する） ----

    // 1. フメツウィザー（Immortal Wither）型シャドウステート
    public boolean immortalShield = true;
    public boolean invulnerablePhase = true;
    public int witherRegenTicks = 200;
    public float shieldHealth = 100.0F;

    // 2. テッセラクトビースト（Tesseract Beast）型シャドウステート
    public boolean tesseractBarrier = true;
    public boolean dimensionalAnchor = true;
    public double hyperHealth = 100.0D;
    public int phaseShiftLock = 7;

    // 3. フラッシュファー（Metapotent Flashfur / Omni-Mobs）型シャドウステート
    public float flashfurShadowHealth = 100.0F;
    public float deltaHealth = 0.0F;
    public boolean omniProtected = true;
    public AkashicShadowCanary.ProtectedCanaryList protectedCanaryList;

    // 4. ダミー真ライフ（TrueLife Canary: プリミティブ＆関数型インターフェース）
    public float trueLifeHealth = 100.0F;
    public boolean trueLifeAlive = true;
    public AkashicShadowCanary.TrueLifeOperator trueLifeOperator;

    // 5. マッドネスエンティティ（Madness Entities）型・非エンティティプロキシ参照
    public AkashicNonEntityProxy nonEntityProxy;
    public Object madnessControllerProxy;

    // 6. GC・ReferenceQueue 横取り（強制 clear / enqueue による弱参照キャッシュ破壊）検知用おとり参照
    public final ReferenceQueue<AkashicProbeEntity> canaryRefQueue = new ReferenceQueue<>();
    public WeakReference<AkashicProbeEntity> canaryWeakSelfRef;
    public final Map<AkashicProbeEntity, Boolean> canaryWeakProtectionMap = new WeakHashMap<>();

    public AkashicProbeEntity(EntityType<? extends AkashicProbeEntity> type, Level level) {
        super(type, level);
        this.protect = new Eprotect(this);
        this.addTag(Eprotect.NO_RETREAT_TAG); // 異常修復回数（200回）や千日手（60秒）による自動退避を永久に無効化
        this.protectedCanaryList = new AkashicShadowCanary.ProtectedCanaryList(this.getUUID());
        this.trueLifeOperator = new AkashicShadowCanary.TrueLifeCanaryImpl(100.0F);
        captureBaselineSynchedSlots();
        resetWeakCanaries();
        if (!level.isClientSide) {
            this.nonEntityProxy = new AkashicNonEntityProxy(this);
            this.madnessControllerProxy = this.nonEntityProxy;
            this.shadowCanary = new AkashicShadowCanary(this);
            this.auditor = new AkashicResistanceAuditor(this);
            syncHologramNow();
        }
        this.setPersistenceRequired();
    }

    /**
     * 初期生成された {@code SynchedEntityData.itemsById} 内の全スロット（{@code DATA_HEALTH_ID}, {@code HOLOGRAM},
     * {@code OBSERVATION_UI_DATA}, {@code DATA_SHARED_FLAGS_ID}, {@code DATA_POSE} 等）を記録し、
     * 外部から {@code items.remove(id)} や {@code items.clear()} でそのまま抜かれた際に即座に再生成できるようにする。
     */
    @SuppressWarnings("unchecked")
    private void captureBaselineSynchedSlots() {
        try {
            SynchedEntityData data = ((EntityAccessor) this).automata_valkyries$getEntityDataField();
            if (data == null) return;
            Int2ObjectMap<SynchedEntityData.DataItem<?>> items =
                    ((SynchedEntityDataAccessor) data).automata_valkyries$getItemsById();
            if (items == null) return;
            for (Int2ObjectMap.Entry<SynchedEntityData.DataItem<?>> entry : items.int2ObjectEntrySet()) {
                SynchedEntityData.DataItem<?> item = entry.getValue();
                if (item != null && item.getAccessor() != null) {
                    baselineSynchedSlots.put(entry.getIntKey(), new SynchedSlotSnapshot(
                            (EntityDataAccessor<Object>) item.getAccessor(),
                            item.getValue()
                    ));
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * {@code SynchedEntityData.itemsById} から {@code DATA_HEALTH_ID}（HP データ）や他の必須スロットが直接抜かれたり（{@code null}）、
     * {@code DataItem.value} が {@code null} 化されたり、定数 0 サブクラスに置換されていた場合に、
     * バニラの {@code getHealth()} / {@code setHealth()} / {@code baseTick()} が {@link NullPointerException} で落ちる前に
     * その場で即座に検知・記録・完全修復する。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void ensureSynchedDataIntegrity() {
        if (this.repairingSynchedData) return;
        this.repairingSynchedData = true;
        try {
            if (this.nonEntityProxy != null) {
                this.nonEntityProxy.restoreObliteratedCoreFields(this);
            }
            SynchedEntityData data = ((EntityAccessor) this).automata_valkyries$getEntityDataField();
            if (data == null) return;
            Int2ObjectMap<SynchedEntityData.DataItem<?>> items =
                    ((SynchedEntityDataAccessor) data).automata_valkyries$getItemsById();
            if (items == null) return;

            EntityDataAccessor<Float> healthAccessor = LivingEntityAccessor.automata_valkyries$getDataHealthId();
            if (healthAccessor != null) {
                int healthId = healthAccessor.getId();
                SynchedEntityData.DataItem<?> item = items.get(healthId);
                float safeHp = 100.0F;
                if (!shouldKeepFullHealth() && this.protect != null && this.protect.getLastValidHealth() > 0.0F) {
                    safeHp = this.protect.getLastValidHealth();
                }
                if (item == null || !(item.getValue() instanceof Float)) {
                    SynchedEntityData.DataItem<Float> clean = new SynchedEntityData.DataItem<>(healthAccessor, safeHp);
                    clean.setDirty(true);
                    items.put(healthId, (SynchedEntityData.DataItem) clean);
                    ((SynchedEntityDataAccessor) data).automata_valkyries$setDirty(true);
                    if (level() != null && !level().isClientSide) {
                        String caller = CallerAttribution.immediateCallerMod(this);
                        String mod = CallerAttribution.isForeign(caller) ? caller : AkashicNonEntityProxy.resolveSuspectMod(this);
                        record().observe(mod, "synched_data:health_slot_evicted", "REPAIRED",
                                item == null ? "itemsById.get(DATA_HEALTH_ID)==null" : "DataItem.value==null", null);
                    }
                } else if (item.getClass() != SynchedEntityData.DataItem.class) {
                    String hijacker = item.getClass().getName();
                    String mod = CallerAttribution.isForeign(CallerAttribution.modOf(item.getClass()))
                            ? CallerAttribution.modOf(item.getClass())
                            : AkashicNonEntityProxy.resolveSuspectMod(this);
                    SynchedEntityData.DataItem<Float> clean = new SynchedEntityData.DataItem<>(healthAccessor, safeHp);
                    clean.setDirty(true);
                    items.put(healthId, (SynchedEntityData.DataItem) clean);
                    ((SynchedEntityDataAccessor) data).automata_valkyries$setDirty(true);
                    if (level() != null && !level().isClientSide) {
                        record().observe(mod, "synched_data:data_item_hijacked", "REPAIRED", hijacker, null);
                    }
                }
            }

            if (!baselineSynchedSlots.isEmpty()) {
                for (Map.Entry<Integer, SynchedSlotSnapshot> entry : baselineSynchedSlots.entrySet()) {
                    int slotId = entry.getKey();
                    if (healthAccessor != null && slotId == healthAccessor.getId()) continue;
                    SynchedEntityData.DataItem<?> existing = items.get(slotId);
                    if (existing == null || existing.getValue() == null) {
                        SynchedSlotSnapshot snap = entry.getValue();
                        Object fallbackVal = snap.initialValue() instanceof CompoundTag tag ? tag.copy() : snap.initialValue();
                        SynchedEntityData.DataItem<Object> restored = new SynchedEntityData.DataItem<>(snap.accessor(), fallbackVal);
                        restored.setDirty(true);
                        items.put(slotId, (SynchedEntityData.DataItem) restored);
                        ((SynchedEntityDataAccessor) data).automata_valkyries$setDirty(true);
                        if (level() != null && !level().isClientSide) {
                            String mod = AkashicNonEntityProxy.resolveSuspectMod(this);
                            record().observe(mod, "synched_data:slot_evicted", "REPAIRED", "slotId=" + slotId, null);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            this.repairingSynchedData = false;
        }
    }

    /** 現在の稼働モードを返す */
    public Mode mode() {
        return this.mode != null ? this.mode : Mode.OBSERVE_STANDBY;
    }

    /**
     * 毎 Tick の HP 満タン維持（不死ロック）が有効かどうか。
     * モード①（観測待機）とモード②（耐性・即死詳細検証）では {@code true}、
     * モード③（直接対決・ダメージ制限解除）では {@code false}。
     */
    public boolean shouldKeepFullHealth() {
        return mode() != Mode.DIRECT_DUEL_UNCAPPED;
    }

    public AkashicResistanceAuditor auditor() {
        if (this.auditor == null) {
            this.auditor = new AkashicResistanceAuditor(this);
        }
        return this.auditor;
    }

    /** 素手右クリック等で次の稼働モードへ切り替える（①観測待機 → ②耐性・即死詳細検証 → ③直接対決・上限解除 → ①） */
    public Mode cycleMode(@Nullable Player player) {
        Mode next = mode().next();
        setMode(next, player);
        return next;
    }

    /** 稼働モードを指定のモードへ切り替え、Eprotect のダメージ上限設定とホログラムを更新する */
    public void setMode(Mode newMode, @Nullable Player player) {
        if (newMode == null) newMode = Mode.OBSERVE_STANDBY;
        this.mode = newMode;
        if (!level().isClientSide) {
            auditor().resetState();
            if (newMode == Mode.DIRECT_DUEL_UNCAPPED) {
                getEprotect().withoutDamageCap();
            } else {
                com.automatavalkyries.util.UnsafeHelper.setFieldValue(
                        getEprotect(), "profile", com.automatavalkyries.protect.EprotectProfile.DEFAULT);
                float max = getMaxHealth() > 0.0F ? getMaxHealth() : 100.0F;
                super.setHealth(max);
            }
            record().observe("automata_valkyries", "mode:" + newMode.name().toLowerCase( java.util.Locale.ROOT),
                    "ACTIVE", newMode.displayLabel, null);
            syncHologramNow();
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("[Akashic Probe] ")
                                .withStyle(ChatFormatting.LIGHT_PURPLE)
                                .append(Component.literal(newMode.displayLabel).withStyle(newMode.color)),
                        true);
            }
            playSound(SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.2F);
        }
    }

    private void syncHologramNow() {
        ensureSynchedDataIntegrity();
        record().tick(level().getGameTime());
        List<String> json = new ArrayList<>();
        for (Component line : record().hologram()) json.add(Component.Serializer.toJson(line));
        this.entityData.set(HOLOGRAM, String.join("\n", json));
        this.entityData.set(OBSERVATION_UI_DATA, buildObservationUiTag());
    }

    private CompoundTag buildObservationUiTag() {
        CompoundTag tag = new CompoundTag();
        Mode m = mode();
        tag.putInt("ModeOrdinal", m.ordinal());
        tag.putString("ModeLabel", m.displayLabel);
        float maxHp = getMaxHealth() > 0.0F ? getMaxHealth() : 100.0F;
        float curHp = DirectRead.health(this);
        if (!Float.isFinite(curHp)) curHp = maxHp;
        tag.putFloat("Hp", curHp);
        tag.putFloat("MaxHp", maxHp);
        tag.putBoolean("KeepFullHp", shouldKeepFullHealth());
        record().writeObservationUiCounters(tag);
        auditor().writeObservationUiTelemetry(tag);
        return tag;
    }

    /** おとり弱参照・ReferenceQueue・WeakHashMap エントリを初期状態に再設定する */
    public void resetWeakCanaries() {
        while (this.canaryRefQueue.poll() != null) {
            // 強制 enqueue された残骸を排出
        }
        this.canaryWeakSelfRef = new WeakReference<>(this, this.canaryRefQueue);
        this.canaryWeakProtectionMap.clear();
        this.canaryWeakProtectionMap.put(this, Boolean.TRUE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        try {
            super.defineSynchedData();
            this.entityData.define(HOLOGRAM, "");
            this.entityData.define(OBSERVATION_UI_DATA, new CompoundTag());
        } catch (IllegalArgumentException ignored) {
            // 外部 MOD の総当たりリフレクション呼び出し（defineSynchedData 再実行）による例外クラッシュを防ぐ
        }
    }

    @Override
    protected void registerGoals() {
        if (this.goalSelector != null) {
            this.goalSelector.removeAllGoals(g -> true);
            this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 16.0F, 1.0F));
            this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        }
    }

    @Override
    public void restoreGoalsIfEmpty() {
        if (this.goalSelector != null && this.goalSelector.getAvailableGoals().isEmpty()) {
            registerGoals();
        }
    }

    public AkashicRecord record() {
        return AkashicRecord.of(getUUID());
    }

    public AkashicShadowCanary shadowCanary() {
        if (this.shadowCanary == null) {
            this.shadowCanary = new AkashicShadowCanary(this);
        }
        return this.shadowCanary;
    }

    public AkashicNonEntityProxy nonEntityProxy() {
        if (this.nonEntityProxy == null) {
            this.nonEntityProxy = new AkashicNonEntityProxy(this);
            this.madnessControllerProxy = this.nonEntityProxy;
        }
        return this.nonEntityProxy;
    }

    // ---- 観測 --------------------------------------------------------------

    /** 保護の攻撃の記録から呼ばれる（{@code AttackLog.record}）。サーバーのスレッドの外からも来る */
    public void observe(String mod, String op, String outcome, String frame, @Nullable String via) {
        if (level().isClientSide) return;
        record().observe(mod, op, outcome, frame, via);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        ensureSynchedDataIntegrity();
        if (this.nonEntityProxy != null) {
            this.nonEntityProxy.restoreObliteratedCoreFields(this);
        }
        if (!level().isClientSide) {
            Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
            String mod = attacker != null ? CallerAttribution.modOf(attacker.getClass())
                    : source.typeHolder().unwrapKey().map(key -> key.location().getNamespace()).orElse(CallerAttribution.UNKNOWN);
            String type = source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse(source.getMsgId());
            String attackerDesc = attacker != null ? attacker.getType().toShortString() : "none";
            record().observeDamage(type, amount, mod, attackerDesc);
            shadowCanary().inspectIncomingDamage(source, amount, mod, attackerDesc);
        }
        return super.hurt(source, amount);
    }

    @Override
    public float getHealth() {
        ensureSynchedDataIntegrity();
        try {
            float hp = super.getHealth();
            if (!Float.isFinite(hp)) {
                return shouldKeepFullHealth() ? 100.0F : Math.max(1.0F, DirectRead.health(this));
            }
            return hp;
        } catch (Throwable t) {
            ensureSynchedDataIntegrity();
            return shouldKeepFullHealth() ? 100.0F : Math.max(1.0F, DirectRead.health(this));
        }
    }

    @Override
    public boolean isAlive() {
        ensureSynchedDataIntegrity();
        try {
            return super.isAlive();
        } catch (Throwable t) {
            ensureSynchedDataIntegrity();
            return true;
        }
    }

    @Override
    public boolean isDeadOrDying() {
        ensureSynchedDataIntegrity();
        try {
            return super.isDeadOrDying();
        } catch (Throwable t) {
            ensureSynchedDataIntegrity();
            return false;
        }
    }

    @Override
    public void setHealth(float health) {
        ensureSynchedDataIntegrity();
        if (this.nonEntityProxy != null) {
            this.nonEntityProxy.restoreObliteratedCoreFields(this);
        }
        if (!level().isClientSide && getEprotect().isProtectionActive()) {
            float max = getMaxHealth() > 0.0F ? getMaxHealth() : 100.0F;
            if (!Float.isFinite(health)) {
                String caller = CallerAttribution.immediateCallerMod(this);
                String mod = CallerAttribution.isForeign(caller) ? caller : AkashicNonEntityProxy.resolveSuspectMod(this);
                record().observe(mod, "direct_call:setHealth_nan", "REFUSED", "setHealth(" + health + ")", null);
                super.setHealth(shouldKeepFullHealth() ? max : Math.max(1.0F, DirectRead.health(this)));
                return;
            }
            if (health <= 0.0F) {
                // モード①・②では常時拒否。モード③（直接対決・上限解除）では hurt() 外（hurtDepth == 0）の直接即死のみ拒否し、正統な被弾による 0 化は許可
                if (shouldKeepFullHealth() || getEprotect().getHurtDepth() == 0) {
                    String caller = CallerAttribution.immediateCallerMod(this);
                    String mod = CallerAttribution.isForeign(caller) ? caller : AkashicNonEntityProxy.resolveSuspectMod(this);
                    record().observe(mod, "direct_call:setHealth_zero", "REFUSED", "setHealth(" + health + ")", null);
                    super.setHealth(shouldKeepFullHealth() ? max : Math.max(1.0F, DirectRead.health(this)));
                    return;
                }
            }
        }
        super.setHealth(health);
    }

    @Override
    public void kill() {
        if (!level().isClientSide && getEprotect().isProtectionActive()) {
            String caller = CallerAttribution.immediateCallerMod(this);
            String mod = CallerAttribution.isForeign(caller) ? caller : AkashicNonEntityProxy.resolveSuspectMod(this);
            record().observe(mod, "direct_call:kill", "REFUSED", "Entity.kill()", null);
            return;
        }
        super.kill();
    }

    @Override
    protected void removeAfterChangingDimensions() {
        if (!level().isClientSide && getEprotect().isProtectionActive()) {
            String caller = CallerAttribution.immediateCallerMod(this);
            String mod = CallerAttribution.isForeign(caller) ? caller : AkashicNonEntityProxy.resolveSuspectMod(this);
            record().observe(mod, "reflective_call:removeAfterChangingDimensions", "REFUSED",
                    "Entity.removeAfterChangingDimensions()", null);
            return;
        }
        super.removeAfterChangingDimensions();
    }

    @Nullable
    @Override
    public Entity changeDimension(net.minecraft.server.level.ServerLevel destination) {
        if (!level().isClientSide && getEprotect().isProtectionActive()) {
            String caller = CallerAttribution.immediateCallerMod(this);
            String mod = CallerAttribution.isForeign(caller) ? caller : AkashicNonEntityProxy.resolveSuspectMod(this);
            record().observe(mod, "dimension_banish:changeDimension", "REFUSED",
                    destination != null ? destination.dimension().location().toString() : "null", null);
            return null;
        }
        return super.changeDimension(destination);
    }

    @Override
    public void die(DamageSource source) {
        if (getEprotect().interceptDie()) return;
        AkashicNonEntityProxy.unregister(getUUID());
        if (!shouldKeepFullHealth() && getEprotect().isDying()) {
            record().observe("automata_valkyries", "duel:defeated_legitimately", "DEFEATED",
                    "die (" + source.getMsgId() + ")", null);
            syncHologramNow();
        } else {
            record().observeBreach("die (" + source.getMsgId() + ")");
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (getEprotect().shouldBlockRemoval(reason)) return;
        if (!level().isClientSide && reason.shouldDestroy() && !getEprotect().isDespawning() && !getEprotect().isDying()) {
            record().observeBreach("remove (" + reason + ")");
        }
        super.remove(reason);
    }

    // ---- 毎 Tick --------------------------------------------------------------

    @Override
    public void baseTick() {
        ensureSynchedDataIntegrity();
        if (this.nonEntityProxy != null) {
            this.nonEntityProxy.restoreObliteratedCoreFields(this);
        }
        if (!this.getTags().contains(Eprotect.NO_RETREAT_TAG)) {
            this.addTag(Eprotect.NO_RETREAT_TAG);
        }
        super.baseTick();
        if (level().isClientSide) return;
        if (!getEprotect().isProtectionActive()) {
            AkashicNonEntityProxy.unregister(getUUID());
            return;
        }
        nonEntityProxy().verifyAndRepair(this);
        shadowCanary().tickObservation();
        getEprotect().tick();
        if (!getEprotect().isProtectionActive()) {
            AkashicNonEntityProxy.unregister(getUUID());
            return;
        }
        // モード①・②では観測のために受け続ける（被弾は記録したら満タンへ戻す）。モード③（直接対決・上限解除）では実ダメージを維持する
        if (shouldKeepFullHealth() && DirectRead.health(this) < getMaxHealth()) {
            setHealth(getMaxHealth());
        }
        auditor().tickMode();
        if (getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < 100) {
            getLookControl().setLookAt(getLastHurtByMob(), 30.0F, 30.0F);
        }
        boolean recordChanged = record().tick(level().getGameTime());
        if (recordChanged) {
            List<String> json = new ArrayList<>();
            for (Component line : record().hologram()) json.add(Component.Serializer.toJson(line));
            this.entityData.set(HOLOGRAM, String.join("\n", json));
        }
        if (recordChanged || tickCount % 4 == 0) {
            this.entityData.set(OBSERVATION_UI_DATA, buildObservationUiTag());
        }
    }

    /** 手元: 頭上の専用観測 UI パネル（Overhead Observation Console）の同期データ */
    public CompoundTag observationUiData() {
        ensureSynchedDataIntegrity();
        try {
            CompoundTag tag = this.entityData.get(OBSERVATION_UI_DATA);
            return tag != null ? tag : new CompoundTag();
        } catch (Throwable ignored) {
            return new CompoundTag();
        }
    }

    /** 手元: 頭上に出す行 */
    public List<Component> hologramLines() {
        ensureSynchedDataIntegrity();
        String source = "";
        try {
            source = this.entityData.get(HOLOGRAM);
        } catch (Throwable ignored) {}
        if (source == null) source = "";
        if (!source.equals(hologramSource)) {
            hologramSource = source;
            List<Component> lines = new ArrayList<>();
            if (!source.isEmpty()) {
                for (String json : source.split("\n")) {
                    try {
                        Component line = Component.Serializer.fromJson(json);
                        if (line != null) lines.add(line);
                    } catch (RuntimeException ignored) {}
                }
            }
            hologramLines = List.copyOf(lines);
        }
        return hologramLines;
    }

    // ---- 操作・退場 ------------------------------------------------------------

    /**
     * プレイヤーが持つ「カスタムダメージ辞書」または「カスタム即死辞書」の ItemStack をアカシックちゃんに読み込ませる。
     */
    public boolean loadCustomLexiconFromStack(net.minecraft.world.item.ItemStack stack, @Nullable Player player) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof com.automatavalkyries.item.AkashicLexiconItem lexiconItem)) {
            return false;
        }
        if (!level().isClientSide) {
            AkashicCustomLexicon parsed = AkashicCustomLexicon.fromStack(stack, lexiconItem.kind());
            auditor().loadCustomLexicon(parsed);
            if (mode() == Mode.OBSERVE_STANDBY) {
                Mode targetMode = lexiconItem.kind() == AkashicResistanceAuditor.TechniqueKind.DAMAGE
                        ? Mode.DAMAGE_ONLY_AUDIT
                        : Mode.INSTANT_KILL_ONLY_AUDIT;
                setMode(targetMode, null);
            } else {
                syncHologramNow();
            }
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("[Akashic Probe] カスタム辞書を登録: ")
                                .withStyle(ChatFormatting.LIGHT_PURPLE)
                                .append(Component.literal(parsed.summaryText()).withStyle(ChatFormatting.AQUA)),
                        true);
            }
            playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.2F);
        }
        return true;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof com.automatavalkyries.item.AkashicLexiconItem) {
            loadCustomLexiconFromStack(held, player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
            if (player.isShiftKeyDown()) {
                if (!level().isClientSide) {
                    AkashicNonEntityProxy.unregister(getUUID());
                    AkashicRecord.close(getUUID());
                    protect.triggerDespawn();
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            } else {
                if (!level().isClientSide) {
                    cycleMode(player);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public Component getDefeatSpeech() {
        return Component.translatable("akashic.automata_valkyries.dismissed").withStyle(ChatFormatting.AQUA);
    }

    @Override
    public SoundEvent getDefeatSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    // ---- 保護 --------------------------------------------------------------

    @Override
    public Eprotect getEprotect() {
        if (this.protect == null) this.protect = new Eprotect(this); // Unsafe で null にされても作り直す
        return this.protect;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        getEprotect().onAddedToWorld();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        getEprotect().onRemovedFromWorld();
    }

    @Override
    protected void tickDeath() {
        if (protect.refuseDeathTick()) return; // 撃破を認めていないのに演出を回された
        super.tickDeath();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return false; // 何でも受けて記録する
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return null; // 誰も狙わない
    }
}
