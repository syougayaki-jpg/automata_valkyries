package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.annihilate.DefenseStructureAnalyzer;
import com.automatavalkyries.annihilate.GenericCheatMobNeutralizer;
import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.annihilate.ModProtectionBreaker;
import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.annihilate.TerminusPlayerExecutor;
import com.automatavalkyries.annihilate.TrueAnnihilationEngine;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.chrono.ChronoGazer;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.automatavalkyries.entity.chrono.RulerFxEntity;
import com.automatavalkyries.entity.seer.AkashicCubeEntity;
import com.automatavalkyries.entity.seer.AkashicSeerEntity;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.KillLists;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事象観測体アカシック・プローブの直接対決エンジン（2026-10-26 ユーザー指定:
 * 「アカシックプローブの直接対決版をボスアカシックを元に実装して元の仕様も凶悪化してペナルティの上がり方は緩めで凶悪感知で上がりやすくして」）。
 *
 * <h3>1. ボス・アカシック（{@link AkashicSeerEntity}）ベースの凶悪化戦闘仕様</h3>
 * <ul>
 *   <li>直接対決モード（⑤ {@link AkashicProbeEntity.Mode#DIRECT_DUEL_UNCAPPED} / ⑥ {@link AkashicProbeEntity.Mode#DIRECT_DUEL_NO_PENALTY}）では
 *       移動速度を開放し、相手から 7〜11m の間合いを保って高速ストレイフ移動しながらボス・アカシックの全 9 種の結晶技（凶悪強化版）を繰り出す。</li>
 *   <li><b>観測結晶・射（強化版）</b>: 予測射撃＋水色（物理）・桃（魔法）・薄紫（虚数）の三色結晶を第1形態 5連射 / 第2形態 8連射。印付きの相手を強追尾。</li>
 *   <li><b>結晶の雨（強化版）</b>: 予測地点周囲に第1形態 7個 / 第2形態 12個の予告円を展開し、巨大結晶落下＋8方向の三色結晶破片拡散。</li>
 *   <li><b>結晶の結界（強化版）</b>: 周囲に正二十面体結晶を展開し、被ダメージを {@value #BARRIER_CUT} 倍に半減しつつ接触者の有益バフ剥奪＋虚数ダメージ。</li>
 *   <li><b>追跡結晶（強化版）</b>: 第1形態 4発 / 第2形態 7発の薄紫・桃色ホーミング結晶を扇状に斉射。</li>
 *   <li><b>結晶槍（強化版）</b>: 正面レーン（第2形態では左右斜めを含む3レーン）に7セグメントの0/1予告線を敷き、15連の結晶棘で打ち上げ＋7セグダメージ表示。</li>
 *   <li><b>屈折光線（強化版）</b>: 対象の周囲4箇所にプリズム結晶を配置し、外周＋十字交差の屈折レーザー網で物理・虚数の複合切断。</li>
 *   <li><b>結晶の檻（強化版）</b>: 対象の周囲10箇所に結晶の二重包囲陣を展開し、重力牽引で中央へ引き寄せた後に全方向から一斉収束。</li>
 *   <li><b>観測の瞬き（強化版）</b>: 対象の背後へ瞬間移動して爆砕残像結晶を残し、同時に移動先から至近距離3連結晶ショットを放つ。</li>
 *   <li><b>アカシック・レコード展開（強化版）</b>: 60方向フィボナッチ結晶球＋二重螺旋＋3面傾斜リング＋16結晶光輪照準斉射＋ペナルティ階層収束波。</li>
 *   <li><b>論理突破率による超適応</b>: 相手の防具・防具強度・攻撃力・最大HP・回復量を毎秒観測し、攻撃倍率（最大 ×{@value #ADAPT_ATTACK_MAX}）、
 *       防御（最大 +{@value #ADAPT_ARMOR_MAX}）、防具強度（最大 +{@value #ADAPT_TOUGHNESS_MAX}）、毎秒自己再生を強化。</li>
 * </ul>
 *
 * <h3>2. ペナルティ階層（Tier 0〜7）の緩やかな基本進行と「凶悪感知（Vicious Sensing）」による急加速</h3>
 * <ul>
 *   <li><b>基本の上がり方は緩やか（{@value #BASE_FAILS} 回耐性で +1 Tier）</b>:
 *       相手が凶悪技術（即死・消去・メモリ改ざん等）を使わず正統に戦っている限り、攻撃が防がれても {@value #BASE_FAILS} 回連続で
 *       無効化されるまでペナルティ階層は上がらない（じっくりボス戦を楽しめる）。</li>
 *   <li><b>凶悪感知（Vicious Sensing）で上がりやすく</b>:
 *       相手が過剰ダメージ、防御貫通・特殊IEEE-754ビット（NaN/±∞）、直接 {@code setHealth(0)} / {@code kill()} / {@code die()}、
 *       {@code remove()} / {@code discard()} / 空間追放、シャドウステート改ざん、非エンティティプロキシ破壊、Unsafe/JVM改ざん、
 *       あるいは被弾後の不正なHP書き戻し（チート防御）を行った瞬間、凶悪度レベル（Lv1〜Lv5）に応じて<b>凶悪感知値（{@code sensedWeight}）</b>が蓄積される。</li>
 *   <li>凶悪感知値が上がると、ペナルティ昇格に必要な失敗閾値（{@code failThreshold}）が {@code 8 → 5 → 3 → 1} へと劇的に短縮され、
 *       さらに Lv3（直接即死）以上で即座にペナルティ階層が {@code +1〜+3 Tier} 跳ね上がり、論理突破率も急上昇する。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class AkashicDuelEngine {

    /** 凶悪強化版スキル基礎威力（一般ボス AkashicSeerEntity より高威力・多段化） */
    public static final float SHOT = 9.0F;
    public static final float RAIN = 15.0F;
    public static final float ORBIT = 6.0F;
    public static final float HOMING = 10.0F;
    public static final float LANCE = 12.0F;
    public static final float REFRACT = 14.0F;
    public static final float CAGE = 9.0F;
    public static final float BLINK = 12.0F;
    public static final float BREACH = 14.0F;
    public static final float BURST_SMALL = 8.0F;
    public static final float BARRIER_CUT = 0.50F;

    public static final float PHASE1_GAUGE_CAP = 75.0F;
    public static final float GAUGE_AFTER_RECORD = 45.0F;
    public static final int BARRIER_TICKS = 180;
    public static final int CHARGE_TICKS = 32;
    public static final int BURST_TICKS = 90;
    public static final double ORBIT_R = 2.1;

    /** 観測の印（凶悪強化版: 持続 12 秒・ダメージ倍率 1.35 倍・半径 24m） */
    public static final int MARK_TICKS = 240;
    public static final int OBSERVE_INTERVAL = 120;
    public static final float MARK_BONUS = 1.35F;
    public static final double OBSERVE_RANGE = 24.0;

    /** 論理突破率による適応の上限（凶悪強化版: 最大攻撃 ×5.0、防具 +30、強度 +16、再生 0.8%/s） */
    public static final float ADAPT_ATTACK_MAX = 5.0F;
    public static final float ADAPT_ARMOR_MAX = 30.0F;
    public static final float ADAPT_TOUGHNESS_MAX = 16.0F;
    public static final float ADAPT_REGEN_RATIO = 0.008F;

    private static final UUID ADAPT_ARMOR_ID = UUID.fromString("8a4f1c2e-9b3d-4e7a-bc1f-5d2e6a9c8b41");
    private static final UUID ADAPT_TOUGHNESS_ID = UUID.fromString("9b5e2d3f-ac4e-4f8b-cd2a-6e3f7b0d9c52");
    private static final UUID DUEL_SPEED_ID = UUID.fromString("7c3d0b1a-6a2c-4d5e-ab0e-4c1d5f8b7a30");

    // ---- ペナルティ階層 ＆ 凶悪感知定数 ----------------------------------------

    /** 最大ペナルティ階層（Tier 0: 通常結晶戦闘 〜 Tier 7: 終焉・極 [Omega Terminus]） */
    public static final int MAX_PENALTY_TIER = 7;
    /** モード⑥（ペナルティなし最強版）の初期ペナルティ階層 */
    public static final int NO_PENALTY_INITIAL_TIER = 5;
    /** 凶悪感知がない通常の緩やかな昇格に必要な連続無効化回数（8回） */
    public static final int BASE_FAILS = 8;
    /** 凶悪感知ポイントが直接 +1 Tier に変換される単位 */
    public static final int SENSE_WEIGHT_PER_AUTO_TIER = 6;

    /**
     * 凶悪感知（Vicious Sensing）の5段階分類。
     * 相手が使った攻撃・干渉の悪質度に応じて重み（{@link #weight}）、即時昇格階層（{@link #instantTierJump}）、
     * 論理突破率ボーナス（{@link #gaugeGain}）を与える。
     */
    public enum ViciousLevel {
        NONE(0, 0, 0, 0.0F, "Lv0:正統"),
        EXCESS_DAMAGE(1, 2, 0, 6.0F, "Lv1:過剰打撃"),
        DEFENSE_BYPASS(2, 4, 0, 14.0F, "Lv2:貫通・特殊ビット"),
        INSTANT_DEATH(3, 7, 1, 24.0F, "Lv3:直接即死・生死改ざん"),
        WORLD_ERASURE(4, 11, 2, 38.0F, "Lv4:存在抹消・空間追放"),
        JVM_MEMORY_TAMPER(5, 16, 3, 55.0F, "Lv5:深層メモリ・JVM改ざん");

        public final int level;
        public final int weight;
        public final int instantTierJump;
        public final float gaugeGain;
        public final String label;

        ViciousLevel(int level, int weight, int instantTierJump, float gaugeGain, String label) {
            this.level = level;
            this.weight = weight;
            this.instantTierJump = instantTierJump;
            this.gaugeGain = gaugeGain;
            this.label = label;
        }
    }

    /** ペナルティ階層（Tier 0〜7）の名称 */
    public static String penaltyTierLabel(int tier) {
        return switch (Mth.clamp(tier, 0, MAX_PENALTY_TIER)) {
            case 0 -> "Tier 0 [観測結晶・標準]";
            case 1 -> "Tier 1 [障壁剥奪・属性適応]";
            case 2 -> "Tier 2 [無敵貫通・盾/不死破壊]";
            case 3 -> "Tier 3 [多段刻み連撃・再生阻害]";
            case 4 -> "Tier 4 [割合削り・直HP減算]";
            case 5 -> "Tier 5 [隔絶ダメージ・状態走査剥奪]";
            case 6 -> "Tier 6 [絶対防御解体・プロキシ無力化]";
            default -> "Tier 7 [終焉・極 Omega Terminus]";
        };
    }

    /** 対戦相手ごとのペナルティ＆凶悪感知状態 */
    public static final class TargetPenaltyState {
        public int penaltyTier;
        public int fails;
        public int sensedWeight;
        public int sensedCarry;
        public int sensedEvents;
        public ViciousLevel maxVicious = ViciousLevel.NONE;
        public String lastViciousOp = "";
        public int lastInstantJumpTick = -100;

        /** 現在の凶悪感知量を反映した、次の階層昇格までに必要な無効化回数（通常 8 → 凶悪感知で最小 1） */
        public int effectiveFailThreshold(int globalSenseWeight) {
            int reduction = (this.sensedWeight / 2) + (globalSenseWeight / 3) + (this.penaltyTier / 3);
            return Math.max(1, BASE_FAILS - reduction);
        }
    }

    /** アクティブなプローブ UUID → DuelEngine インスタンス（AkashicRecord からの非同期通知受け口） */
    private static final Map<UUID, AkashicDuelEngine> ACTIVE_ENGINES = new ConcurrentHashMap<>();
    /** 相手が回復した量（LivingHealEvent 計測用） */
    private static final Map<LivingEntity, Float> HEALED = new WeakHashMap<>();

    private final AkashicProbeEntity host;
    private final ServerBossEvent bossEvent;
    private final ServerBossEvent gaugeEvent;

    /** 相手 UUID ごとのペナルティ状態 */
    private final Map<UUID, TargetPenaltyState> targetStates = new HashMap<>();
    /** 攻撃元エンティティが特定できない直接呼び出し・環境干渉で蓄積された全体凶悪感知値 */
    private int globalSensedWeight;
    private int globalSensedCarry;
    private int globalSensedEvents;
    private int basePenaltyTier;
    private ViciousLevel maxGlobalVicious = ViciousLevel.NONE;
    private String lastGlobalViciousOp = "";
    private int lastGlobalJumpTick = -100;

    /** ボス・アカシックの戦闘・スキル制御状態 */
    @Nullable
    private UUID currentTargetId;
    private final Map<Integer, Integer> marks = new HashMap<>();
    private boolean marksDirty;
    private float oppArmor, oppToughness, oppAttack, oppMaxHp, oppHeal;
    private int castLock;
    private int burstLeft;
    private int repath;
    private int gap;
    private int strafe = 1;
    /** 0:射撃, 1:結晶の雨, 2:結界, 3:追跡結晶, 4:結晶槍, 5:屈折光線, 6:結晶の檻, 7:観測の瞬き */
    private final int[] cd = {14, 55, 160, 45, 70, 95, 75, 90};
    private final int[] last = new int[8];
    private static final boolean[] PHASE2_ONLY = {false, false, false, false, false, false, true, true};

    private record Delayed(int at, Runnable act) {}
    private final List<Delayed> delayed = new ArrayList<>();

    public AkashicDuelEngine(AkashicProbeEntity host) {
        this.host = host;
        this.bossEvent = new ServerBossEvent(
                Component.literal("事象観測体アカシック・プローブ [直接対決]").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                BossEvent.BossBarColor.PINK,
                BossEvent.BossBarOverlay.NOTCHED_10);
        this.gaugeEvent = new ServerBossEvent(
                Component.literal("論理突破率 0% | Tier 0"),
                BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        this.bossEvent.setVisible(false);
        this.gaugeEvent.setVisible(false);
        this.gaugeEvent.setProgress(0.0F);
        ACTIVE_ENGINES.put(host.getUUID(), this);
    }

    public void register() {
        ACTIVE_ENGINES.put(host.getUUID(), this);
    }

    public void unregister() {
        ACTIVE_ENGINES.remove(host.getUUID(), this);
        bossEvent.removeAllPlayers();
        gaugeEvent.removeAllPlayers();
    }

    /** クローン差し替え・転生時の状態引き継ぎ */
    public void inheritStateFrom(AkashicDuelEngine other) {
        if (other == null || other == this) return;
        this.targetStates.clear();
        this.targetStates.putAll(other.targetStates);
        this.globalSensedWeight = other.globalSensedWeight;
        this.globalSensedCarry = other.globalSensedCarry;
        this.globalSensedEvents = other.globalSensedEvents;
        this.basePenaltyTier = other.basePenaltyTier;
        this.maxGlobalVicious = other.maxGlobalVicious;
        this.lastGlobalViciousOp = other.lastGlobalViciousOp;
        this.currentTargetId = other.currentTargetId;
        this.oppArmor = other.oppArmor;
        this.oppToughness = other.oppToughness;
        this.oppAttack = other.oppAttack;
        this.oppMaxHp = other.oppMaxHp;
        this.oppHeal = other.oppHeal;
        host.setPhase2(other.host.isPhase2());
        host.setGauge(other.host.gauge());
        host.setBarrierLeft(other.host.barrierLeft());
        host.setChargeLeft(other.host.chargeLeft());
        ACTIVE_ENGINES.put(host.getUUID(), this);
    }

    /** モード切替時の初期化 */
    public void onModeChanged(AkashicProbeEntity.Mode newMode) {
        delayed.clear();
        burstLeft = 0;
        castLock = 0;
        host.setNoGravity(false);
        host.setChargeLeft(0);
        host.setBarrierLeft(0);
        boolean duel = isDuelMode(newMode);
        bossEvent.setVisible(duel);
        gaugeEvent.setVisible(duel);
        updateMovementSpeedModifier(duel);
        if (!duel) {
            currentTargetId = null;
            host.getNavigation().stop();
            host.setPhase2(false);
            modifier(Attributes.ARMOR, ADAPT_ARMOR_ID, 0.0F);
            modifier(Attributes.ARMOR_TOUGHNESS, ADAPT_TOUGHNESS_ID, 0.0F);
        } else {
            if (newMode == AkashicProbeEntity.Mode.DIRECT_DUEL_NO_PENALTY) {
                host.setPhase2(true);
                this.basePenaltyTier = Math.max(this.basePenaltyTier, NO_PENALTY_INITIAL_TIER);
                host.setGauge(Math.max(host.gauge(), 60.0F));
                bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
            } else {
                // モード⑤（上限解除）へ切り替えた際は緩やかな初期ペナルティから開始できるようにリセット
                this.targetStates.clear();
                this.globalSensedWeight = 0;
                this.globalSensedCarry = 0;
                this.globalSensedEvents = 0;
                this.basePenaltyTier = 0;
                this.maxGlobalVicious = ViciousLevel.NONE;
                this.lastGlobalViciousOp = "";
                host.setPhase2(false);
                bossEvent.setColor(BossEvent.BossBarColor.PINK);
            }
        }
    }

    public static boolean isDuelMode(AkashicProbeEntity.Mode mode) {
        return mode == AkashicProbeEntity.Mode.DIRECT_DUEL_UNCAPPED
                || mode == AkashicProbeEntity.Mode.DIRECT_DUEL_NO_PENALTY;
    }

    private void updateMovementSpeedModifier(boolean duel) {
        AttributeInstance inst = host.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        inst.removeModifier(DUEL_SPEED_ID);
        if (duel) {
            inst.addTransientModifier(new AttributeModifier(
                    DUEL_SPEED_ID, "akashic_duel_speed", 0.34D, AttributeModifier.Operation.ADDITION));
        }
    }

    public void startSeenByPlayer(ServerPlayer player) {
        bossEvent.addPlayer(player);
        gaugeEvent.addPlayer(player);
    }

    public void stopSeenByPlayer(ServerPlayer player) {
        bossEvent.removePlayer(player);
        gaugeEvent.removePlayer(player);
    }

    // ====================================================================
    // 凶悪感知（Vicious Sensing）＆ ペナルティ階層管理
    // ====================================================================

    /**
     * {@link AkashicRecord#observe} に記録されたあらゆる操作・干渉を検査し、
     * 相手からの凶悪技術であれば即座に凶悪感知値とペナルティ階層を引き上げる。
     */
    public static void onRecordObserved(UUID probeUuid, String mod, String op, String outcome, @Nullable String frame) {
        if (probeUuid == null || op == null) return;
        AkashicDuelEngine engine = ACTIVE_ENGINES.get(probeUuid);
        if (engine != null) {
            engine.inspectObservedOperation(mod, op, outcome, frame);
        }
    }

    public void inspectObservedOperation(String mod, String op, String outcome, @Nullable String frame) {
        if (op == null || op.isEmpty()) return;
        // 自分自身のモード切替・検証ログ・対決出力ログは除外（ただし flowchart:revert_detected は相手の凶悪回復なので感知対象）
        if (op.startsWith("mode:") || op.startsWith("lexicon:") || op.startsWith("audit:")
                || op.startsWith("duel:") || op.startsWith("duel_nopenalty:")) {
            return;
        }
        if (op.startsWith("flowchart:") && !op.equals("flowchart:revert_detected")) {
            return;
        }
        // 通常の軽微な被弾記録（damage:...）は onIncomingHurt 側で実ダメージ量と共に判定するためここでは特殊属性のみ見る
        if (op.startsWith("damage:")) {
            if (op.contains("generic_kill") || op.contains("fell_out_of_world") || op.contains("outside_border")) {
                recordViciousSense(resolveCurrentTarget(), ViciousLevel.DEFENSE_BYPASS, op);
            }
            return;
        }

        ViciousLevel level = classifyOperationViciousness(op, outcome);
        if (level != ViciousLevel.NONE) {
            recordViciousSense(resolveCurrentTarget(), level, op);
        }
    }

    /**
     * 観測された操作名（{@code op}）から凶悪度レベル（Lv1〜Lv5）を判定する。
     */
    public static ViciousLevel classifyOperationViciousness(String op, @Nullable String outcome) {
        if (op == null) return ViciousLevel.NONE;
        String lower = op.toLowerCase(Locale.ROOT);

        // Lv5: 深層メモリ・JVMヘッダ・Unsafe・エージェント・レジストリ改ざん・終焉棒試行
        if (lower.startsWith("unsafe_obliteration") || lower.startsWith("jvm_header")
                || lower.startsWith("enum_tamper") || lower.startsWith("jvm_cache")
                || lower.startsWith("agent_canary") || lower.startsWith("registry_tamper")
                || lower.startsWith("thread_local") || lower.startsWith("gc_tamper")
                || lower.startsWith("ai_tamper") || lower.startsWith("trial:ultra_kill_stick")
                || lower.startsWith("trial:terminus_stick")) {
            return ViciousLevel.JVM_MEMORY_TAMPER;
        }

        // Lv4: ワールド存在抹消・remove/discard・次元追放・非エンティティプロキシ破壊・時間停止・座標NaN破壊
        if (lower.startsWith("world_eviction") || lower.startsWith("dimension_banish")
                || lower.startsWith("reflective_call") || lower.startsWith("non_entity_proxy")
                || lower.startsWith("spatial_tamper") || lower.startsWith("identity_tamper")
                || lower.startsWith("network_tamper") || lower.startsWith("time_stop")
                || lower.equals("remove") || lower.equals("discard") || lower.equals("setremoved")
                || lower.contains("ticklist") || lower.contains("section") || lower.contains("chunk")) {
            return ViciousLevel.WORLD_ERASURE;
        }

        // Lv3: 直接 setHealth(0/NaN)・kill()・die()・SynchedEntityData抜去/改ざん・最大HP改ざん・シャドウ/真ライフ破壊
        if (lower.startsWith("direct_call:") || lower.startsWith("synched_data:")
                || lower.startsWith("attr_tamper:") || lower.startsWith("shadow_")
                || lower.startsWith("truelife:") || lower.startsWith("direct_field:")
                || lower.equals("sethealth") || lower.equals("kill") || lower.equals("die")
                || lower.equals("dead") || lower.equals("deathtime") || lower.equals("pose")
                || lower.equals("maxhealth") || lower.equals("entitydata")) {
            return ViciousLevel.INSTANT_DEATH;
        }

        // Lv2: IEEE-754 特殊ビット（NaN/±∞/-0.0/lastHurt負値）・HP偽装・遅延HP書き戻し
        if (lower.startsWith("damage_bit:") || lower.startsWith("spoofed_read")
                || lower.startsWith("combat_tamper") || lower.startsWith("random_tamper")
                || lower.equals("flowchart:revert_detected")) {
            return ViciousLevel.DEFENSE_BYPASS;
        }

        if ("REFUSED".equals(outcome) || "REPAIRED".equals(outcome) || "NEUTRALIZED".equals(outcome)) {
            return ViciousLevel.INSTANT_DEATH;
        }
        return ViciousLevel.NONE;
    }

    /**
     * アカシック自身が {@link AkashicProbeEntity#hurt(DamageSource, float)} で被弾した際の凶悪感知＆ターゲット捕捉。
     */
    public void onIncomingHurt(DamageSource source, float rawAmount) {
        Entity srcEntity = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        LivingEntity attacker = srcEntity instanceof LivingEntity l ? l : null;
        if (attacker != null && attacker != host && isValidTarget(attacker)) {
            setTarget(attacker);
            mark(attacker);
        }

        // 被弾量・ダメージ種別から凶悪度を判定
        ViciousLevel v = ViciousLevel.NONE;
        String reason = "hurt:" + source.getMsgId();
        if (!Float.isFinite(rawAmount) || Float.floatToRawIntBits(rawAmount) == 0x80000000) {
            v = ViciousLevel.DEFENSE_BYPASS;
            reason = "hurt:non_finite(" + rawAmount + ")";
        } else if (rawAmount >= 10000.0F) {
            v = ViciousLevel.INSTANT_DEATH;
            reason = "hurt:extreme(" + String.format(Locale.ROOT, "%.0f", rawAmount) + ")";
        } else if (rawAmount >= 500.0F
                || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.OUTSIDE_BORDER)) {
            v = ViciousLevel.DEFENSE_BYPASS;
            reason = "hurt:bypass(" + String.format(Locale.ROOT, "%.1f", rawAmount) + ")";
        } else if (rawAmount > 30.0F || rawAmount > host.getMaxHealth() * 0.25F) {
            v = ViciousLevel.EXCESS_DAMAGE;
            reason = "hurt:excess(" + String.format(Locale.ROOT, "%.1f", rawAmount) + ")";
        }

        if (v != ViciousLevel.NONE) {
            recordViciousSense(attacker, v, reason);
        } else {
            observed(2.0F);
        }
    }

    /**
     * 凶悪技術を感知した際に、対象および全体の凶悪感知値を加算し、ペナルティ階層と論理突破率を引き上げる。
     */
    public synchronized void recordViciousSense(@Nullable LivingEntity suspect, ViciousLevel level, String opDescription) {
        if (level == null || level == ViciousLevel.NONE) return;
        int tick = host.tickCount;

        // 全体凶悪感知値の更新
        this.globalSensedEvents++;
        this.globalSensedWeight += level.weight;
        this.globalSensedCarry += level.weight;
        if (level.level >= this.maxGlobalVicious.level) {
            this.maxGlobalVicious = level;
        }
        this.lastGlobalViciousOp = opDescription;

        int autoTiersFromGlobal = this.globalSensedCarry / SENSE_WEIGHT_PER_AUTO_TIER;
        if (autoTiersFromGlobal > 0) {
            this.globalSensedCarry %= SENSE_WEIGHT_PER_AUTO_TIER;
            this.basePenaltyTier = Math.min(MAX_PENALTY_TIER, this.basePenaltyTier + autoTiersFromGlobal);
        }
        if (level.instantTierJump > 0 && tick - this.lastGlobalJumpTick >= 2) {
            this.lastGlobalJumpTick = tick;
            this.basePenaltyTier = Math.min(MAX_PENALTY_TIER, this.basePenaltyTier + level.instantTierJump);
        }

        // 個別ターゲットの凶悪感知値の更新
        if (suspect != null) {
            TargetPenaltyState st = stateFor(suspect);
            st.sensedEvents++;
            st.sensedWeight += level.weight;
            st.sensedCarry += level.weight;
            if (level.level >= st.maxVicious.level) {
                st.maxVicious = level;
            }
            st.lastViciousOp = opDescription;

            int prevTier = st.penaltyTier;
            int autoTiers = st.sensedCarry / SENSE_WEIGHT_PER_AUTO_TIER;
            if (autoTiers > 0) {
                st.sensedCarry %= SENSE_WEIGHT_PER_AUTO_TIER;
                st.penaltyTier = Math.min(MAX_PENALTY_TIER, st.penaltyTier + autoTiers);
            }
            if (level.instantTierJump > 0 && tick - st.lastInstantJumpTick >= 2) {
                st.lastInstantJumpTick = tick;
                st.penaltyTier = Math.min(MAX_PENALTY_TIER, st.penaltyTier + level.instantTierJump);
            }
            st.penaltyTier = Math.max(st.penaltyTier, this.basePenaltyTier);
            if (st.penaltyTier > prevTier) {
                onPenaltyTierEscalated(suspect, prevTier, st.penaltyTier, "vicious_sense:" + level.name().toLowerCase(Locale.ROOT));
            }
        }

        // 論理突破率も凶悪度に応じて大きく上昇
        observed(level.gaugeGain);
        if (this.basePenaltyTier >= 2 && !host.isPhase2() && isDuelMode(host.mode())) {
            enterPhase2();
        }
    }

    public TargetPenaltyState stateFor(@Nullable LivingEntity target) {
        if (target == null) {
            TargetPenaltyState dummy = new TargetPenaltyState();
            dummy.penaltyTier = currentBasePenaltyTier();
            dummy.sensedWeight = globalSensedWeight;
            dummy.sensedEvents = globalSensedEvents;
            dummy.maxVicious = maxGlobalVicious;
            dummy.lastViciousOp = lastGlobalViciousOp;
            return dummy;
        }
        TargetPenaltyState st = targetStates.computeIfAbsent(target.getUUID(), id -> {
            TargetPenaltyState s = new TargetPenaltyState();
            s.penaltyTier = currentBasePenaltyTier();
            return s;
        });
        st.penaltyTier = Math.max(st.penaltyTier, currentBasePenaltyTier());
        return st;
    }

    public int currentBasePenaltyTier() {
        if (host.mode() == AkashicProbeEntity.Mode.DIRECT_DUEL_NO_PENALTY) {
            return Math.max(NO_PENALTY_INITIAL_TIER, basePenaltyTier);
        }
        return basePenaltyTier;
    }

    public int penaltyTier(@Nullable LivingEntity target) {
        return stateFor(target).penaltyTier;
    }

    public int effectiveFailThreshold(@Nullable LivingEntity target) {
        return stateFor(target).effectiveFailThreshold(globalSensedWeight);
    }

    public void adaptToTarget(@Nullable LivingEntity target) {
        if (target == null) return;
        setTarget(target);
        oppArmor = Math.max(oppArmor, target.getArmorValue());
        oppToughness = Math.max(oppToughness, (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var atk = target.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) oppAttack = Math.max(oppAttack, (float) atk.getValue());
        oppMaxHp = Math.max(oppMaxHp, target.getMaxHealth());
        observed(25.0F);
    }

    public float attackMultiplier(@Nullable LivingEntity target) {
        float base = attackMultiplier();
        return (target != null && isMarked(target)) ? base * MARK_BONUS : base;
    }

    public int totalSensedWeight(@Nullable LivingEntity target) {
        if (target == null) return globalSensedWeight;
        TargetPenaltyState st = targetStates.get(target.getUUID());
        return (st != null ? st.sensedWeight : 0) + globalSensedWeight;
    }

    public int totalSensedEvents(@Nullable LivingEntity target) {
        if (target == null) return globalSensedEvents;
        TargetPenaltyState st = targetStates.get(target.getUUID());
        return Math.max(globalSensedEvents, st != null ? st.sensedEvents : 0);
    }

    public ViciousLevel highestViciousLevel(@Nullable LivingEntity target) {
        if (target == null) return maxGlobalVicious;
        TargetPenaltyState st = targetStates.get(target.getUUID());
        if (st == null) return maxGlobalVicious;
        return st.maxVicious.level >= maxGlobalVicious.level ? st.maxVicious : maxGlobalVicious;
    }

    private void onPenaltyTierEscalated(@Nullable LivingEntity target, int fromTier, int toTier, String reason) {
        if (toTier >= 2 && !host.isPhase2() && isDuelMode(host.mode())) {
            enterPhase2();
        }
        String mod = target != null ? CallerAttribution.modOf(target.getClass()) : "unknown";
        String targetDesc = target != null ? (target.getType().toShortString() + "#" + target.getId()) : "global";
        host.record().observe(mod, "duel:penalty_tier_" + toTier, "ADAPTED",
                targetDesc + " Tier " + fromTier + "->" + toTier + " (" + reason + ")", null);
        if (host.level() instanceof ServerLevel server) {
            Fx.shockwave(server, host.position().add(0, 1.0, 0),
                    toTier >= 6 ? AkashicCubeEntity.VIOLET : AkashicCubeEntity.PINK, 3.5F + toTier * 0.5F, 14);
            host.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.9F + toTier * 0.1F);
        }
    }

    /**
     * 攻撃後のダメージ結果を評価し、無効化されていた場合はペナルティ失敗カウントを進める。
     * <ul>
     *   <li>通常時（凶悪感知なし）は {@value #BASE_FAILS} 回（8回）連続で無効化されて初めて +1 Tier 昇格する（緩やか）。</li>
     *   <li>相手がチート防御（外来の hurt/setHealth フック、IEprotected、遅延HP書き戻し）で 100% 無効化している場合は
     *       防御系凶悪感知を加算し、昇格を早める。</li>
     *   <li>モード⑥（ペナルティなし最強版）では 1 回でも耐えられた時点で即座に Tier 7（終焉・極）へ昇格する。</li>
     * </ul>
     */
    public void evaluateStrikeResult(ServerLevel level, LivingEntity target, float preHp, float expectedDamage, String strikeTag) {
        if (target == null) return;
        TargetPenaltyState st = stateFor(target);
        boolean defeated = !AkashicResistanceAuditor.isTargetStillPresent(target);
        float postHp = defeated ? 0.0F : readEffectiveHealth(target);
        float dealt = Math.max(0.0F, preHp - postHp);
        boolean effective = defeated || dealt >= Math.max(0.5F, Math.min(expectedDamage * 0.15F, preHp * 0.05F));

        if (defeated) {
            st.fails = 0;
            return;
        }
        if (effective) {
            // ダメージが通っている間は失敗カウントを減衰させ、ペナルティを無理に上げない
            if (st.fails > 0) st.fails--;
            return;
        }

        // ダメージが防がれた・無効化された場合
        st.fails++;

        // モード⑥（ペナルティなし最強版）では 1 回耐えられたら即 Tier 7（終焉・極）へ
        if (host.mode() == AkashicProbeEntity.Mode.DIRECT_DUEL_NO_PENALTY) {
            int prev = st.penaltyTier;
            st.penaltyTier = MAX_PENALTY_TIER;
            this.basePenaltyTier = MAX_PENALTY_TIER;
            if (st.penaltyTier > prev) {
                onPenaltyTierEscalated(target, prev, st.penaltyTier, "no_penalty_instant_max");
            }
            return;
        }

        // 相手が外来 MOD の不死防御構造（IEprotected または DefenseStructureAnalyzer で検出された制御機構）を持っているか検査
        if (st.penaltyTier >= 1 && dealt < 0.01F) {
            boolean hasCheatDefense = target instanceof IEprotected
                    || !DefenseStructureAnalyzer.analyze(target).mechanisms().isEmpty();
            if (hasCheatDefense) {
                // チート防御を感知: 凶悪感知ポイントを +3 加算して昇格を加速
                st.sensedWeight += 3;
                st.sensedCarry += 3;
                if (st.maxVicious.level < ViciousLevel.DEFENSE_BYPASS.level) {
                    st.maxVicious = ViciousLevel.DEFENSE_BYPASS;
                    st.lastViciousOp = "cheat_defense_nullify";
                }
            }
        }

        int threshold = st.effectiveFailThreshold(globalSensedWeight);
        if (st.fails >= threshold) {
            st.fails = 0;
            int prev = st.penaltyTier;
            int jump = (st.sensedWeight + globalSensedWeight >= 12) ? 2 : 1;
            st.penaltyTier = Math.min(MAX_PENALTY_TIER, st.penaltyTier + jump);
            if (st.penaltyTier > prev) {
                onPenaltyTierEscalated(target, prev, st.penaltyTier,
                        "resisted_" + threshold + "_strikes(" + strikeTag + ")");
            }
        }
    }

    /**
     * 現在のペナルティ階層（Tier 0〜7）に応じた追加突破打撃を対象に適用する。
     */
    public void applyPenaltyTierAugmentation(ServerLevel level, LivingEntity target, float baseDamage, int color) {
        if (target == null || !AkashicResistanceAuditor.isTargetStillPresent(target)) return;
        int tier = penaltyTier(target);
        float scaledDamage = baseDamage * attackMultiplier() * ( isMarked(target) ? MARK_BONUS : 1.0F);

        if (tier <= 0) {
            // Tier 0: 純粋なボス・アカシックの三色結晶ダメージのみ
            return;
        }

        // Tier 1+: 事前バフ・吸収剥奪 ＆ 最適チャネル適応打撃
        if (tier >= 1) {
            target.setAbsorptionAmount(0.0F);
            int prefChannel = color == AkashicCubeEntity.PINK ? 1 : color == AkashicCubeEntity.VIOLET ? 5 : 0;
            if (tier == 1) {
                AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, false);
                target.hurt(host.damageOf(color, null), scaledDamage);
                return;
            }
            if (tier == 2) {
                // Tier 2: 無敵時間・盾・不死のトーテム突破
                AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, true);
                AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, prefChannel, Math.max(scaledDamage, 25.0F), false, host.record());
                return;
            }
            if (tier == 3) {
                // Tier 3: 多段刻み連撃（1発上限突破）＋1/3/10 Tick 後 HP 書き戻し検問
                AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, prefChannel, Math.max(scaledDamage * 1.5F, 45.0F), true, host.record());
                return;
            }
            if (tier == 4) {
                // Tier 4: 最大HP割合ボーナス（8% MaxHP）＋バニラ不死フラグ解除＋直HP減算フォールバック
                float pctDamage = Math.max(scaledDamage * 2.0F, target.getMaxHealth() * 0.08F);
                AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, prefChannel, pctDamage, true, host.record());
                return;
            }
        }

        if (tier == 5) {
            // Tier 5: 状態走査剥奪（StateScanner + KillLists）＋隔絶ダメージ（IsolationDamageEngine）
            StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
            KillLists.purge(target);
            float isoDmg = Math.max(scaledDamage * 4.0F, Math.max(120.0F, target.getMaxHealth() * 0.25F));
            IsolationDamageEngine.ensureIsolatedAttack(host, target, host.damageOf(color, null), isoDmg);
            return;
        }

        if (tier == 6) {
            // Tier 6: 他MOD絶対防御解体（ModProtectionBreaker + GenericCheatMobNeutralizer）＋高出力隔絶打撃
            ModProtectionBreaker.breakProtection(level, target);
            StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
            KillLists.purge(target);
            GenericCheatMobNeutralizer.neutralize(level, target);
            if (target instanceof Player player) {
                TerminusPlayerExecutor.breakPlayerDefensesOnly(level, host, player);
            }
            float isoDmg = Math.max(scaledDamage * 10.0F, Math.max(1000.0F, target.getMaxHealth() * 0.50F));
            IsolationDamageEngine.ensureIsolatedAttack(host, target, host.damageSources().genericKill(), isoDmg);
            return;
        }

        // Tier 7 (MAX): 終焉・極（Omega Terminus Convergence）
        executeOmegaTerminus(level, target);
    }

    /** Tier 7 最終奥義：全防御解体＋真・抹殺＋終焉機構収束 */
    public void executeOmegaTerminus(ServerLevel level, LivingEntity target) {
        if (target == null) return;
        ModProtectionBreaker.breakProtection(level, target);
        ModProtectionBreaker.finishWithModOwnDeath(target);
        StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
        KillLists.purge(target);
        GenericCheatMobNeutralizer.neutralize(level, target);
        AnnihilationExecutionAgent.neutralizeWitherzilla(level, target);
        AnnihilationExecutionAgent.neutralizeFlashfur(level, target);
        TrueAnnihilationEngine.annihilateEntity(level, null, target);
        TerminusEngine.executeTerminus(level, host, target);
    }

    /**
     * 結晶弾（{@link AkashicCubeEntity}）が対象に命中した際のコールバック。
     */
    public void onCrystalHit(LivingEntity victim, float baseDamage, int color, @Nullable Entity directProjectile) {
        if (!(host.level() instanceof ServerLevel level) || victim == null || victim == host) return;
        mark(victim);
        observed(3.5F);
        float preHp = readEffectiveHealth(victim);
        int tier = penaltyTier(victim);
        if (tier >= 1) {
            applyPenaltyTierAugmentation(level, victim, baseDamage, color);
        }
        evaluateStrikeResult(level, victim, preHp, baseDamage * attackMultiplier(), "crystal_hit_T" + tier);
    }

    // ====================================================================
    // 観測の印（Observation Marks）＆ 論理突破率によるステータス適応
    // ====================================================================

    public void mark(LivingEntity e) {
        if (host.level().isClientSide || e == null || e == host || !e.isAlive()) return;
        if (marks.put(e.getId(), MARK_TICKS) == null) {
            marksDirty = true;
            if (host.level() instanceof ServerLevel server) {
                Fx.shockwave(server, e.position().add(0, 0.1, 0), AkashicCubeEntity.PINK, 1.6F, 12);
                server.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, host.getSoundSource(), 1.2F, 1.8F);
            }
        }
    }

    public boolean isMarked(Entity e) {
        if (e == null) return false;
        if (!host.level().isClientSide) return marks.containsKey(e.getId());
        for (int id : host.markedIds()) {
            if (id == e.getId()) return true;
        }
        return false;
    }

    private void tickMarks(ServerLevel server) {
        for (var it = marks.entrySet().iterator(); it.hasNext(); ) {
            var m = it.next();
            Entity e = server.getEntity(m.getKey());
            int left = m.getValue() - 1;
            if (left <= 0 || !(e instanceof LivingEntity l) || !l.isAlive() || e.distanceToSqr(host) > 64 * 64) {
                it.remove();
                marksDirty = true;
            } else {
                m.setValue(left);
            }
        }
        if (host.tickCount % OBSERVE_INTERVAL == 0 && resolveCurrentTarget() != null) {
            observeAround(server);
        }
        if (marksDirty && host.tickCount % 4 == 0) {
            host.setMarkedIds(marks.keySet().stream().mapToInt(Integer::intValue).toArray());
            marksDirty = false;
        }
    }

    private void observeAround(ServerLevel server) {
        Vec3 eye = host.getEyePosition();
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(OBSERVE_RANGE), this::isValidTarget)) {
            if (e.distanceTo(host) > OBSERVE_RANGE || !host.hasLineOfSight(e)) continue;
            Fx.line(server, MementoMoriEntity.glow(AkashicCubeEntity.PINK, 0.12F, 10),
                    eye, e.position().add(0, e.getBbHeight() * 0.6, 0), 0.6);
            mark(e);
        }
        host.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.8F);
    }

    public float attackCap() {
        float k = (oppArmor + oppToughness * 0.6F) / 30.0F + Math.max(0.0F, oppMaxHp - 20.0F) / 30.0F;
        return 1.0F + Math.min(ADAPT_ATTACK_MAX - 1.0F, k);
    }

    public float attackMultiplier() {
        int tier = penaltyTier(resolveCurrentTarget());
        float tierBonus = tier * 0.20F;
        return (1.0F + (attackCap() - 1.0F) * host.gauge() / 100.0F) + tierBonus;
    }

    public float armorBonus() {
        return Math.min(ADAPT_ARMOR_MAX, oppAttack * 0.9F) * host.gauge() / 100.0F;
    }

    public float toughnessBonus() {
        return Math.min(ADAPT_TOUGHNESS_MAX, oppAttack * 0.4F) * host.gauge() / 100.0F;
    }

    public float regenPerSecond() {
        float baseRegen = host.isPhase2() ? 0.5F : 0.0F;
        return baseRegen + Math.min(oppHeal, host.getMaxHealth() * ADAPT_REGEN_RATIO) * host.gauge() / 100.0F;
    }

    private List<LivingEntity> opponents(ServerLevel server) {
        List<LivingEntity> out = new ArrayList<>();
        LivingEntity cur = resolveCurrentTarget();
        if (cur != null) out.add(cur);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(32.0), this::isValidTarget)) {
            if (!out.contains(e) && (e == cur || e instanceof Player || e instanceof ValkyrieEntity
                    || (e instanceof Mob m && m.getTarget() == host))) {
                out.add(e);
            }
        }
        return out;
    }

    private void adapt(ServerLevel server) {
        float armor = 0, tough = 0, attack = 0, hp = 0, heal = 0;
        for (LivingEntity e : opponents(server)) {
            armor = Math.max(armor, e.getArmorValue());
            tough = Math.max(tough, (float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            var atk = e.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) attack = Math.max(attack, (float) atk.getValue());
            hp = Math.max(hp, e.getMaxHealth());
            Float h = HEALED.remove(e);
            if (h != null) heal = Math.max(heal, h);
        }
        oppArmor = armor;
        oppToughness = tough;
        oppAttack = attack;
        oppMaxHp = hp;
        oppHeal = oppHeal * 0.6F + heal * 0.4F;
        modifier(Attributes.ARMOR, ADAPT_ARMOR_ID, armorBonus());
        modifier(Attributes.ARMOR_TOUGHNESS, ADAPT_TOUGHNESS_ID, toughnessBonus());
        float regen = regenPerSecond();
        if (regen > 0.01F && host.getHealth() > 0.0F && host.getHealth() < host.getMaxHealth()) {
            host.heal(regen);
        }
    }

    private void modifier(Attribute attr, UUID id, float amount) {
        var inst = host.getAttribute(attr);
        if (inst == null) return;
        var old = inst.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - amount) < 0.05) return;
        if (old != null) inst.removeModifier(id);
        if (amount > 0.05F) {
            inst.addTransientModifier(new AttributeModifier(id, "akashic_duel_adapt", amount, AttributeModifier.Operation.ADDITION));
        }
    }

    public void observed(float amount) {
        float cap = host.isPhase2() ? 100.0F : PHASE1_GAUGE_CAP;
        host.setGauge(Math.min(cap, host.gauge() + amount));
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof AkashicProbeEntity probe && event.getEntity() != probe) {
            if (isDuelMode(probe.mode())) {
                float k = probe.duelEngine().attackMultiplier();
                if (probe.duelEngine().isMarked(event.getEntity())) k *= MARK_BONUS;
                event.setAmount(event.getAmount() * k);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (ACTIVE_ENGINES.isEmpty() || event.getEntity().level().isClientSide || event.getEntity() instanceof AkashicProbeEntity) {
            return;
        }
        HEALED.merge(event.getEntity(), event.getAmount(), Float::sum);
    }

    // ====================================================================
    // ターゲット選定 ＆ 毎 Tick ループ
    // ====================================================================

    public boolean isValidTarget(@Nullable LivingEntity e) {
        if (e == null || e == host || !AkashicResistanceAuditor.isTargetStillPresent(e)) return false;
        if (e instanceof AkashicProbeEntity) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return true;
    }

    public void setTarget(@Nullable LivingEntity target) {
        this.currentTargetId = target != null ? target.getUUID() : null;
    }

    @Nullable
    public LivingEntity resolveCurrentTarget() {
        if (!(host.level() instanceof ServerLevel server)) return null;
        if (currentTargetId != null) {
            Entity e = server.getEntity(currentTargetId);
            if (e instanceof LivingEntity l && isValidTarget(l) && l.distanceToSqr(host) <= 48.0 * 48.0) {
                return l;
            }
            currentTargetId = null;
        }
        LivingEntity lastHurtBy = host.getLastHurtByMob();
        if (lastHurtBy != null && isValidTarget(lastHurtBy) && lastHurtBy.distanceToSqr(host) <= 40.0 * 40.0) {
            currentTargetId = lastHurtBy.getUUID();
            return lastHurtBy;
        }
        LivingEntity candidate = host.auditor().findEligibleTarget(server, false);
        if (candidate != null && isValidTarget(candidate)) {
            currentTargetId = candidate.getUUID();
            return candidate;
        }
        // 近くに敵対しているモブまたはサバイバルプレイヤーがいれば捕捉
        List<LivingEntity> nearby = server.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(24.0),
                e -> isValidTarget(e) && ((e instanceof Mob m && m.getTarget() == host)
                        || e.getTags().contains(AkashicResistanceAuditor.AUDIT_TARGET_TAG)));
        if (!nearby.isEmpty()) {
            nearby.sort(Comparator.comparingDouble(host::distanceToSqr));
            currentTargetId = nearby.get(0).getUUID();
            return nearby.get(0);
        }
        return null;
    }

    public void enterPhase2() {
        if (host.isPhase2()) return;
        host.setPhase2(true);
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        host.playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 1.2F);
        say("phase2", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_PHASE2);
        if (host.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, host.getX(), host.getY(1.0), host.getZ(), 1, 0, 0, 0, 0);
            Fx.shockwave(server, host.position().add(0, 1.0, 0), AkashicCubeEntity.PINK, 8.0F, 20);
            Fx.shockwave(server, host.position().add(0, 0.2, 0), AkashicCubeEntity.CYAN, 12.0F, 24);
        }
    }

    /**
     * 毎サーバー Tick の直接対決ループ（{@link AkashicProbeEntity#baseTick()} / {@link AkashicProbeEntity#aiStep()} から呼ばれる）。
     */
    public void tickServer(ServerLevel server) {
        boolean duel = isDuelMode(host.mode());
        bossEvent.setVisible(duel);
        gaugeEvent.setVisible(duel);
        if (!duel) return;

        updateMovementSpeedModifier(true);
        float maxHp = Math.max(1.0F, host.getMaxHealth());
        float curHp = Math.max(0.0F, DirectRead.health(host));
        float ratio = Mth.clamp(curHp / maxHp, 0.0F, 1.0F);
        bossEvent.setProgress(ratio);
        bossEvent.setName(Component.literal(host.mode().displayLabel)
                .withStyle(host.mode().color, ChatFormatting.BOLD));

        LivingEntity target = resolveCurrentTarget();
        int tier = penaltyTier(target);
        ViciousLevel vLevel = highestViciousLevel(target);
        gaugeEvent.setProgress(Mth.clamp(host.gauge() / 100.0F, 0.0F, 1.0F));
        gaugeEvent.setName(Component.literal(String.format(Locale.ROOT,
                "論理突破率 %d%% (攻×%.2f 防+%.1f) | %s | 凶悪感知:%s(w=%d)",
                Math.round(host.gauge()), attackMultiplier(), armorBonus(),
                penaltyTierLabel(tier), vLevel.label, totalSensedWeight(target)))
                .withStyle(tier >= 5 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));

        if (!host.isPhase2() && (ratio <= 0.60F || tier >= 2 || host.mode() == AkashicProbeEntity.Mode.DIRECT_DUEL_NO_PENALTY)) {
            enterPhase2();
        }

        tickMarks(server);
        if (host.tickCount % 20 == 0) {
            adapt(server);
        }
        if (castLock > 0) castLock--;

        for (Delayed d : new ArrayList<>(delayed)) {
            if (host.tickCount >= d.at()) {
                delayed.remove(d);
                d.act().run();
            }
        }

        // 結晶の結界の持続処理（虚数ダメージ＋有益バフ剥奪）
        int barrier = host.barrierLeft();
        if (barrier > 0) {
            host.setBarrierLeft(barrier - 1);
            if (barrier % 8 == 0) {
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class,
                        host.getBoundingBox().inflate(ORBIT_R + 0.8, 0.8, ORBIT_R + 0.8), this::isValidTarget)) {
                    if (e.distanceTo(host) <= ORBIT_R + 1.0) {
                        purgeOneBeneficialEffect(e);
                        float pre = readEffectiveHealth(e);
                        e.hurt(host.damageOf(AkashicCubeEntity.VIOLET, null), ORBIT);
                        applyPenaltyTierAugmentation(server, e, ORBIT, AkashicCubeEntity.VIOLET);
                        evaluateStrikeResult(server, e, pre, ORBIT, "barrier_orbit");
                        observed(1.5F);
                    }
                }
            }
        }

        // アカシック・レコード展開の溜め処理
        int charge = host.chargeLeft();
        if (charge > 0) {
            host.setChargeLeft(charge - 1);
            host.setDeltaMovement(0, 0.04, 0);
            if (charge % 4 == 0) {
                Fx.gather(server, MementoMoriEntity.glow(
                        charge % 8 == 0 ? AkashicCubeEntity.PINK : AkashicCubeEntity.CYAN, 0.35F, 12),
                        host.position().add(0, 1.2, 0), 12, 5.2, 12);
            }
            if (charge == 1) {
                recordBurst(server);
            }
        }

        if (burstLeft > 0) {
            burstLeft--;
            host.setNoGravity(burstLeft > 0);
            host.setDeltaMovement(host.getDeltaMovement().multiply(0.5, 0.0, 0.5));
            host.getNavigation().stop();
        }

        if (target != null && target.isAlive()) {
            tickCombatAi(server, target);
        }
    }

    /**
     * ボス・アカシックの移動（7〜11m ストレイフ）と9種スキルローテーション。
     */
    private void tickCombatAi(ServerLevel server, LivingEntity t) {
        host.getLookControl().setLookAt(t, 30.0F, 30.0F);
        for (int i = 0; i < cd.length; i++) cd[i]--;

        // 7〜11m を保って横へ回り込む
        if (--repath <= 0 && host.chargeLeft() == 0 && burstLeft <= 0) {
            repath = 12;
            if (host.getRandom().nextInt(4) == 0) strafe = -strafe;
            Vec3 away = host.position().subtract(t.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
            away = away.normalize();
            double d = host.distanceTo(t);
            double want = d < 7.0 ? 10.0 : d > 11.0 ? 8.5 : d;
            Vec3 side = new Vec3(-away.z, 0, away.x).scale(strafe * 3.2);
            Vec3 spot = t.position().add(away.scale(want)).add(side);
            host.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.15);
        }

        if (castLock > 0 || --gap > 0 || host.chargeLeft() > 0) return;

        // 第2形態で論理突破率 100% に達したら大技「アカシック・レコード展開」
        if (host.isPhase2() && host.gauge() >= 100.0F) {
            record();
            gap = 16;
            return;
        }

        int pick = -1;
        for (int i = 0; i < cd.length; i++) {
            if (cd[i] > 0 || (PHASE2_ONLY[i] && !host.isPhase2())) continue;
            if (pick < 0 || last[i] < last[pick]) pick = i;
        }
        if (pick < 0) return;

        last[pick] = host.tickCount;
        gap = cdScale(14);
        mark(t);
        switch (pick) {
            case 0 -> {
                observeShot(t);
                cd[0] = cdScale(40);
            }
            case 1 -> {
                dataRain(t);
                cd[1] = cdScale(120);
            }
            case 2 -> {
                barrier();
                cd[2] = cdScale(240);
            }
            case 3 -> {
                homing(t);
                cd[3] = cdScale(90);
            }
            case 4 -> {
                lance(t);
                cd[4] = cdScale(130);
            }
            case 5 -> {
                refract(t);
                cd[5] = cdScale(160);
            }
            case 6 -> {
                cage(t);
                cd[6] = cdScale(150);
            }
            default -> {
                blink(t);
                cd[7] = cdScale(190);
            }
        }
    }

    private int cdScale(int ticks) {
        int tier = penaltyTier(resolveCurrentTarget());
        float factor = host.isPhase2() ? 0.72F : 1.0F;
        if (tier >= 4) factor *= 0.75F;
        return Math.max(6, Math.round(ticks * factor));
    }

    // ====================================================================
    // ボス・アカシックの 9 種結晶技（凶悪強化版）
    // ====================================================================

    private Vec3 predict(LivingEntity t, double ticks) {
        Vec3 v = t.getDeltaMovement().multiply(1, 0, 1);
        return t.position().add(v.scale(ticks));
    }

    private Vec3 hand() {
        Vec3 f = Vec3.directionFromRotation(0, host.yBodyRot);
        return host.position().add(0, 1.3, 0).add(f.scale(0.6));
    }

    private void say(String key, ChatFormatting... style) {
        if (!(host.level() instanceof ServerLevel server)) return;
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(host) < 64 * 64)) {
            p.displayClientMessage(Component.translatable("entity.automata_valkyries.akashic_seer." + key).withStyle(style), true);
        }
    }

    private static void purgeOneBeneficialEffect(LivingEntity target) {
        for (MobEffectInstance inst : new ArrayList<>(target.getActiveEffects())) {
            if (inst.getEffect().isBeneficial()) {
                target.removeEffect(inst.getEffect());
                break;
            }
        }
    }

    /** 1. 観測結晶・射（凶悪強化版: 第1形態 5連射 / 第2形態 8連射、三色ローテーション＋強追尾） */
    public void observeShot(LivingEntity t) {
        int n = host.isPhase2() ? 8 : 5;
        int[] colors = {AkashicCubeEntity.CYAN, AkashicCubeEntity.PINK, AkashicCubeEntity.VIOLET};
        for (int i = 0; i < n; i++) {
            int k = i;
            int color = colors[i % colors.length];
            delayed.add(new Delayed(host.tickCount + i * 3, () -> {
                if (!t.isAlive()) return;
                Vec3 from = hand();
                double dist = from.distanceTo(t.position());
                Vec3 to = predict(t, dist / 1.05).add(0, t.getBbHeight() * 0.5, 0);
                AkashicCubeEntity shot = AkashicCubeEntity.shoot(host, from, to.subtract(from), 1.05F, SHOT, color, 0.17F).crystal();
                if (isMarked(t)) shot.homing(t, 24);
                host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_SHOT);
                host.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1.4F + k * 0.08F);
            }));
        }
        castLock = n * 3 + 8;
        say("shot", ChatFormatting.AQUA);
    }

    /** 2. 結晶の雨（凶悪強化版: 第1形態 7個 / 第2形態 12個、大爆砕＋破片拡散） */
    public void dataRain(LivingEntity t) {
        if (!(host.level() instanceof ServerLevel server)) return;
        int n = host.isPhase2() ? 12 : 7;
        Vec3 c = predict(t, 16);
        int[] colors = {AkashicCubeEntity.PINK, AkashicCubeEntity.VIOLET, AkashicCubeEntity.CYAN};
        for (int i = 0; i < n; i++) {
            int color = colors[i % colors.length];
            double a = host.getRandom().nextDouble() * Math.PI * 2;
            double r = i == 0 ? 0 : 1.2 + host.getRandom().nextDouble() * 4.5;
            Vec3 at = new Vec3(c.x + Math.cos(a) * r, t.getY(), c.z + Math.sin(a) * r);
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK,
                    RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_FAST,
                    at.add(0, 0.08, 0), 2.5F, 0, 0, 20, color);
            delayed.add(new Delayed(host.tickCount + 5 + i * 2, () ->
                    AkashicCubeEntity.shoot(host, at.add(0, 12.0, 0), new Vec3(0, -1, 0), 1.05F, RAIN, color, 0.48F)
                            .crystal().blast(2.6F).life(40)));
        }
        castLock = 24;
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_RAIN);
        host.playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
        say("rain", ChatFormatting.LIGHT_PURPLE);
    }

    /** 3. 結晶の結界（凶悪強化版: 被ダメージ半減＋周囲に虚数ダメージ＆バフ剥奪） */
    public void barrier() {
        host.setBarrierLeft(BARRIER_TICKS);
        castLock = 16;
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_BARRIER);
        host.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 1.2F);
        say("barrier", ChatFormatting.AQUA);
    }

    /** 4. 追跡結晶（凶悪強化版: 第1形態 4発 / 第2形態 7発の薄紫・桃ホーミング結晶扇状斉射） */
    public void homing(LivingEntity t) {
        Vec3 from = hand();
        Vec3 dir = t.position().add(0, 1.0, 0).subtract(from).normalize();
        int count = host.isPhase2() ? 7 : 4;
        for (int i = 0; i < count; i++) {
            double spread = (i - (count - 1) * 0.5) * 0.38;
            Vec3 d = new Vec3(
                    dir.x * Math.cos(spread) - dir.z * Math.sin(spread),
                    0.32,
                    dir.x * Math.sin(spread) + dir.z * Math.cos(spread));
            int color = i % 2 == 0 ? AkashicCubeEntity.VIOLET : AkashicCubeEntity.PINK;
            AkashicCubeEntity.shoot(host, from, d, 0.62F, HOMING, color, 0.21F).crystal().homing(t, 50);
        }
        castLock = 18;
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_HOMING);
        host.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 2.0F, 1.4F);
        say("homing", ChatFormatting.LIGHT_PURPLE);
    }

    /** 5. 結晶槍（凶悪強化版: 15連結晶棘＋第2形態は3方向フォークレーン＋7セグダメージ表示） */
    public void lance(LivingEntity t) {
        if (!(host.level() instanceof ServerLevel server)) return;
        Vec3 from = new Vec3(host.getX(), t.getY(), host.getZ());
        Vec3 baseDir = t.position().subtract(from).multiply(1, 0, 1);
        if (baseDir.lengthSqr() < 1.0e-4) baseDir = new Vec3(1, 0, 0);
        baseDir = baseDir.normalize();

        double[] angles = host.isPhase2() ? new double[]{0.0, -0.26, 0.26} : new double[]{0.0};
        for (double ang : angles) {
            Vec3 dir = new Vec3(
                    baseDir.x * Math.cos(ang) - baseDir.z * Math.sin(ang),
                    0,
                    baseDir.x * Math.sin(ang) + baseDir.z * Math.cos(ang)).normalize();
            double yaw = Math.atan2(dir.z, dir.x);
            RulerFxEntity.spawn(server, RulerFxEntity.LANE, 16 | RulerFxEntity.LANE_SEGMENTS,
                    from.add(0, 0.05, 0), 22.0F, (float) yaw, 0, 16 + 30, AkashicCubeEntity.PINK);
            for (int k = 0; k < 15; k++) {
                Vec3 p = from.add(dir.scale(1.4 + k * 1.4));
                delayed.add(new Delayed(host.tickCount + 14 + k * 2, () -> spike(server, p, LANCE)));
            }
        }
        castLock = 26;
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_LANCE);
        host.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.7F);
        say("lance", ChatFormatting.LIGHT_PURPLE);
    }

    private void spike(ServerLevel server, Vec3 point, float damage) {
        int bit = host.getRandom().nextInt(3);
        int color = bit == 0 ? AkashicCubeEntity.PINK : bit == 1 ? AkashicCubeEntity.CYAN : AkashicCubeEntity.VIOLET;
        RulerFxEntity.spawn(server, RulerFxEntity.Z_SPIKE, 0, point,
                1.9F + host.getRandom().nextFloat() * 0.6F, 0, 0, 16, color);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
                point.x, point.y + 0.3, point.z, 10, 0.3, 0.2, 0.3, 0.1);
        Fx.burst(server, MementoMoriEntity.glow(color, 0.3F, 12), point.add(0, 0.6, 0), 8, 0.12);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(1.4, 2.2, 1.4), this::isValidTarget)) {
            float pre = readEffectiveHealth(e);
            long shown = Math.round(damage * attackMultiplier() * (isMarked(e) ? MARK_BONUS : 1.0F));
            boolean hit = e.hurt(host.damageOf(color, null), damage);
            applyPenaltyTierAugmentation(server, e, damage, color);
            evaluateStrikeResult(server, e, pre, damage, "crystal_lance");
            if (hit || readEffectiveHealth(e) < pre) {
                Fx.segmentNumber(server, e.position().add(0, e.getBbHeight() + 0.4, 0), shown, AkashicCubeEntity.CYAN);
                e.push(0, 0.48, 0);
                e.hurtMarked = true;
                observed(2.5F);
            }
        }
        server.playSound(null, BlockPos.containing(point), SoundEvents.AMETHYST_CLUSTER_PLACE,
                host.getSoundSource(), 1.2F, 0.8F + host.getRandom().nextFloat() * 0.4F);
    }

    /** 6. 屈折光線（凶悪強化版: 4プリズム外周＋十字交差レーザー網） */
    public void refract(LivingEntity t) {
        if (!(host.level() instanceof ServerLevel server)) return;
        double base = host.getRandom().nextDouble() * Math.PI * 2;
        Vec3[] pts = new Vec3[5];
        pts[0] = hand();
        for (int i = 0; i < 4; i++) {
            double a = base + i * Math.PI * 2 / 4;
            pts[i + 1] = t.position().add(Math.cos(a) * 3.8, 1.0 + (i % 2) * 0.5, Math.sin(a) * 3.8);
            AkashicCubeEntity.shoot(host, pts[i + 1], new Vec3(0, 1, 0), 0.001F, 0.0F, AkashicCubeEntity.VIOLET, 0.32F)
                    .crystal().life(28);
        }
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 1}, {1, 3}, {2, 4}};
        for (int[] edge : edges) {
            Fx.line(server, MementoMoriEntity.glow(AkashicCubeEntity.VIOLET, 0.08F, 20), pts[edge[0]], pts[edge[1]], 0.5);
        }
        delayed.add(new Delayed(host.tickCount + 18, () -> {
            for (int[] edge : edges) {
                Vec3 a = pts[edge[0]], b = pts[edge[1]];
                Fx.line(server, MementoMoriEntity.glow(AkashicCubeEntity.CYAN, 0.5F, 12), a, b, 0.3);
                Fx.line(server, MementoMoriEntity.glow(0xFFFFFF, 0.2F, 10), a, b, 0.6);
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.2), this::isValidTarget)) {
                    if (segmentDistance(e.position().add(0, e.getBbHeight() * 0.5, 0), a, b) <= 1.0 + e.getBbWidth() / 2) {
                        float pre = readEffectiveHealth(e);
                        e.hurt(host.damageOf(AkashicCubeEntity.VIOLET, null), REFRACT);
                        applyPenaltyTierAugmentation(server, e, REFRACT, AkashicCubeEntity.VIOLET);
                        evaluateStrikeResult(server, e, pre, REFRACT, "refract_beam");
                        observed(3.0F);
                    }
                }
            }
            host.playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.8F);
        }));
        castLock = 24;
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_REFRACT);
        host.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.6F);
        say("refract", ChatFormatting.AQUA);
    }

    private static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double k = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / Math.max(1.0e-6, ab.lengthSqr())));
        return p.distanceTo(a.add(ab.scale(k)));
    }

    /** 7. 結晶の檻（凶悪強化版: 10個の結晶による二重包囲陣＋中心牽引＋一斉収束） */
    public void cage(LivingEntity t) {
        Vec3 c = t.position().add(0, 1.0, 0);
        int count = 10;
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            double yOff = (i % 2 == 0) ? 0.0 : 0.9;
            Vec3 p = c.add(Math.cos(a) * 4.2, yOff, Math.sin(a) * 4.2);
            int color = i % 3 == 0 ? AkashicCubeEntity.PINK : i % 3 == 1 ? AkashicCubeEntity.CYAN : AkashicCubeEntity.VIOLET;
            AkashicCubeEntity.shoot(host, p, new Vec3(0, 1, 0), 0.001F, 0.0F, color, 0.24F).crystal().life(22);
            delayed.add(new Delayed(host.tickCount + 20, () -> {
                Vec3 targetCenter = t.isAlive() ? t.position().add(0, t.getBbHeight() * 0.5, 0) : c;
                AkashicCubeEntity.shoot(host, p, targetCenter.subtract(p), 0.68F, CAGE, color, 0.24F).crystal().life(18);
            }));
        }
        // 中心への重力牽引
        Vec3 pull = c.subtract(t.position()).scale(0.25);
        t.setDeltaMovement(t.getDeltaMovement().add(pull.x, 0.1, pull.z));
        t.hurtMarked = true;
        castLock = 26;
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_CAGE);
        host.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 1.0F);
        say("cage", ChatFormatting.LIGHT_PURPLE);
    }

    /** 8. 観測の瞬き（凶悪強化版: 背後転移＋残像結晶爆破＋至近距離3連三色ショット） */
    public void blink(LivingEntity t) {
        if (!(host.level() instanceof ServerLevel server)) return;
        Vec3 back = Vec3.directionFromRotation(0, t.getYRot()).multiply(1, 0, 1).normalize().scale(-3.5);
        Vec3 to = t.position().add(back);
        Vec3 old = host.position();
        if (host.level().noCollision(host, host.getBoundingBox().move(to.subtract(old)))) {
            host.teleportTo(to.x, to.y, to.z);
        }
        Fx.burst(server, MementoMoriEntity.glow(AkashicCubeEntity.VIOLET, 0.35F, 16), old.add(0, 1.0, 0), 22, 0.2);
        AkashicCubeEntity.shoot(host, old.add(0, 1.0, 0), new Vec3(0, 1, 0), 0.001F, 0.0F, AkashicCubeEntity.VIOLET, 0.48F)
                .crystal().life(14);
        delayed.add(new Delayed(host.tickCount + 14, () -> {
            Fx.shockwave(server, old.add(0, 0.5, 0), AkashicCubeEntity.VIOLET, 3.2F, 14);
            Fx.burst(server, MementoMoriEntity.glow(AkashicCubeEntity.PINK, 0.3F, 14), old.add(0, 1.0, 0), 26, 0.3);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(old, old).inflate(3.0, 2.2, 3.0), this::isValidTarget)) {
                if (e.distanceToSqr(old) <= 3.0 * 3.0 + 1) {
                    float pre = readEffectiveHealth(e);
                    e.hurt(host.damageOf(AkashicCubeEntity.VIOLET, null), BLINK);
                    applyPenaltyTierAugmentation(server, e, BLINK, AkashicCubeEntity.VIOLET);
                    evaluateStrikeResult(server, e, pre, BLINK, "blink_clone_blast");
                    observed(3.5F);
                }
            }
            server.playSound(null, BlockPos.containing(old), SoundEvents.AMETHYST_CLUSTER_BREAK, host.getSoundSource(), 2.0F, 0.6F);
        }));
        host.setYRot((float) Math.toDegrees(Math.atan2(t.getZ() - host.getZ(), t.getX() - host.getX())) - 90.0F);
        host.yBodyRot = host.getYRot();
        Fx.burst(server, MementoMoriEntity.glow(AkashicCubeEntity.CYAN, 0.3F, 14), host.position().add(0, 1.0, 0), 16, 0.2);

        // 転移直後の至近距離3連結晶ショット
        Vec3 from = hand();
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
        int[] colors = {AkashicCubeEntity.CYAN, AkashicCubeEntity.PINK, AkashicCubeEntity.VIOLET};
        for (int i = 0; i < 3; i++) {
            double sp = (i - 1) * 0.22;
            Vec3 d = new Vec3(aim.x * Math.cos(sp) - aim.z * Math.sin(sp), aim.y, aim.x * Math.sin(sp) + aim.z * Math.cos(sp));
            AkashicCubeEntity.shoot(host, from, d, 1.1F, SHOT, colors[i], 0.18F).crystal().homing(t, 20);
        }

        castLock = 14;
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_BLINK);
        host.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.5F, 1.4F);
        say("blink", ChatFormatting.LIGHT_PURPLE);
    }

    /** 9. アカシック・レコード展開（溜め開始） */
    public void record() {
        host.setChargeLeft(CHARGE_TICKS);
        host.setGauge(GAUGE_AFTER_RECORD);
        castLock = CHARGE_TICKS + BURST_TICKS + 10;
        host.playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.6F);
        say("record", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
    }

    /**
     * アカシック・レコード展開の放出（凶悪強化版）:
     * ① 60方向フィボナッチ結晶球 → ② 二重螺旋 → ③ 3面傾斜リング → ④ 16結晶光輪照準斉射 → ⑤ ペナルティ階層収束波。
     */
    public void recordBurst(ServerLevel server) {
        server.sendParticles(ParticleTypes.FLASH, host.getX(), host.getY(1.0), host.getZ(), 1, 0, 0, 0, 0);
        host.playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 1.4F);
        host.playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 1.6F);
        host.level().broadcastEntityEvent(host, AkashicSeerEntity.EV_BURST);
        burstLeft = BURST_TICKS;
        Vec3 c0 = host.position().add(0, 1.2, 0);
        Fx.mandala(server, host.position().add(0, 0.1, 0), AkashicCubeEntity.PINK, 5.5F, BURST_TICKS, false, true);
        Fx.mandala(server, c0, AkashicCubeEntity.CYAN, 3.4F, BURST_TICKS, true, false);
        Fx.shockwave(server, c0, AkashicCubeEntity.VIOLET, 11.0F, 20);
        int[] colors = {AkashicCubeEntity.PINK, AkashicCubeEntity.VIOLET, AkashicCubeEntity.CYAN};

        // ① 60方向フィボナッチ結晶球
        int n = 60;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < n; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / n, r = Math.sqrt(1.0 - y * y), a = golden * i;
            Vec3 d = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
            AkashicCubeEntity.shoot(host, c0.add(d.scale(0.8)), d, 0.36F, BURST_SMALL,
                    colors[y > 0.33 ? 0 : y > -0.33 ? 1 : 2], 0.18F).crystal().life(110);
        }

        // ② 二重螺旋（18ステップ×2腕）
        for (int k = 0; k < 18; k++) {
            int step = k;
            delayed.add(new Delayed(host.tickCount + 8 + k * 2, () -> {
                Vec3 c = host.position().add(0, 1.2, 0);
                for (int arm = 0; arm < 2; arm++) {
                    double a = step * 0.4 + arm * Math.PI, p = Math.sin(step * 0.35) * 0.65;
                    Vec3 d = new Vec3(Math.cos(a) * Math.cos(p), Math.sin(p), Math.sin(a) * Math.cos(p));
                    AkashicCubeEntity.shoot(host, c.add(d.scale(0.6)), d, 0.55F, BURST_SMALL,
                            arm == 0 ? AkashicCubeEntity.PINK : AkashicCubeEntity.CYAN, 0.17F).crystal().life(80);
                }
                if (step % 4 == 0) host.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F, 1.0F + step * 0.05F);
            }));
        }

        // ③ 傾いた3つの輪（各24結晶）
        delayed.add(new Delayed(host.tickCount + 44, () -> {
            Vec3 c = host.position().add(0, 1.2, 0);
            double tilt = Math.toRadians(35);
            Vec3[] normals = {
                    new Vec3(0, Math.cos(tilt), Math.sin(tilt)),
                    new Vec3(0, Math.cos(tilt), -Math.sin(tilt)),
                    new Vec3(1, 0, 0)
            };
            for (int ring = 0; ring < 3; ring++) {
                Vec3 nrm = normals[ring].normalize();
                Vec3 u = nrm.cross(Math.abs(nrm.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
                Vec3 v = nrm.cross(u);
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    Vec3 d = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                    AkashicCubeEntity.shoot(host, c.add(d.scale(0.8)), d, 0.46F, BURST_SMALL, colors[ring], 0.18F)
                            .crystal().life(90);
                }
                Fx.shockwave(server, c, colors[ring], 5.5F, 12);
            }
            host.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 1.4F);
        }));

        // ④ 観測の照準（16結晶光輪 → 予測照準へ一斉高速射撃＋ペナルティ階層収束打撃）
        List<AkashicCubeEntity> halo = new ArrayList<>();
        delayed.add(new Delayed(host.tickCount + 58, () -> {
            Vec3 c = host.position().add(0, 2.4, 0);
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                halo.add(AkashicCubeEntity.shoot(host,
                        c.add(Math.cos(a) * 1.9, Math.sin(a * 2) * 0.3, Math.sin(a) * 1.9),
                        new Vec3(0, 1, 0), 0.001F, 0.0F, colors[i % 3], 0.23F).crystal().life(40));
            }
            host.playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 1.8F);
        }));
        delayed.add(new Delayed(host.tickCount + 74, () -> {
            LivingEntity t = resolveCurrentTarget();
            for (AkashicCubeEntity h : halo) {
                Vec3 from = h.position();
                int col = h.color();
                h.discard();
                if (t == null || !t.isAlive()) continue;
                Vec3 to = predict(t, from.distanceTo(t.position()) / 1.25).add(0, t.getBbHeight() * 0.5, 0);
                AkashicCubeEntity.shoot(host, from, to.subtract(from), 1.25F, BREACH, col, 0.23F)
                        .crystal().homing(t, 20).life(45);
            }
            if (t != null && AkashicResistanceAuditor.isTargetStillPresent(t)) {
                float pre = readEffectiveHealth(t);
                applyPenaltyTierAugmentation(server, t, BREACH * 2.5F, AkashicCubeEntity.VIOLET);
                evaluateStrikeResult(server, t, pre, BREACH * 2.5F, "akashic_record_burst");
            }
            server.sendParticles(ParticleTypes.FLASH, host.getX(), host.getY(1.4), host.getZ(), 1, 0, 0, 0, 0);
            host.playSound(SoundEvents.TRIDENT_THUNDER, 1.5F, 1.8F);
        }));
    }

    // ====================================================================
    // 直接対決ストライク実行（スキル発動＋ペナルティ階層執行）
    // ====================================================================

    /**
     * 直接対決での1アクション執行（{@link AkashicResistanceAuditor#executeDuelCounterStrike} および
     * {@link AkashicResistanceAuditor#executeNoPenaltyFullSpecStrike} と連動）。
     * ボス・アカシックの結晶スキルを放ちつつ、現在のペナルティ階層（Tier 0〜7）に応じた打撃を与え、
     * 耐えられた場合は緩やかな基本カウントまたは凶悪感知ブーストに従ってペナルティ階層を引き上げる。
     */
    public String executeDuelAction(ServerLevel level, LivingEntity target, boolean noPenaltyFullSpec) {
        if (target == null || !AkashicResistanceAuditor.isTargetStillPresent(target)) {
            return "duel:none";
        }
        setTarget(target);
        mark(target);
        host.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (noPenaltyFullSpec) {
            if (!host.isPhase2()) enterPhase2();
            TargetPenaltyState st = stateFor(target);
            st.penaltyTier = Math.max(st.penaltyTier, NO_PENALTY_INITIAL_TIER);
        }

        int tier = penaltyTier(target);
        float preHp = readEffectiveHealth(target);

        // ボス・アカシックの結晶スキルを同時に展開（視覚演出＋結晶弾幕）
        triggerBossSkillForStrike(level, target, tier);

        String strikeType;
        if (noPenaltyFullSpec) {
            if (tier < MAX_PENALTY_TIER) {
                strikeType = "duel_nopenalty:overdrive_barrage";
                // 初撃: ボス結晶攻撃＋ノーマルダメージ精密フローチャート＋隔絶ダメージの複合斉射
                target.hurt(host.damageOf(AkashicCubeEntity.PINK, null), BREACH * attackMultiplier() * MARK_BONUS);
                AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 5, Math.max(500.0F, target.getMaxHealth() * 0.5F), true, host.record());
                if (AkashicResistanceAuditor.isTargetStillPresent(target)) {
                    float iso = Math.max(500.0F, target.getMaxHealth() * 0.5F);
                    IsolationDamageEngine.ensureIsolatedAttack(host, target, host.damageSources().genericKill(), iso);
                }
            } else {
                strikeType = "duel_nopenalty:omega_terminus";
                executeOmegaTerminus(level, target);
            }
        } else {
            // モード⑤（直接対決・制限解除）: ペナルティ階層 Tier 0〜7 に応じた段階執行
            if (tier == 0) {
                strikeType = "duel:seer_crystal_strike";
                // Tier 0 でも通常のモブやプレイヤーにしっかり通るボス・アカシックの三色結晶連撃
                int[] cols = {AkashicCubeEntity.CYAN, AkashicCubeEntity.PINK, AkashicCubeEntity.VIOLET};
                for (int c : cols) {
                    if (!AkashicResistanceAuditor.isTargetStillPresent(target)) break;
                    target.invulnerableTime = 0;
                    target.hurt(host.damageOf(c, null), SHOT * attackMultiplier() * MARK_BONUS);
                }
            } else if (tier <= 4) {
                strikeType = "duel:tactical_strike_T" + tier;
                target.invulnerableTime = 0;
                target.hurt(host.damageOf(AkashicCubeEntity.VIOLET, null), LANCE * attackMultiplier() * MARK_BONUS);
                applyPenaltyTierAugmentation(level, target, Math.max(35.0F, target.getMaxHealth() * 0.25F), AkashicCubeEntity.VIOLET);
            } else if (tier <= 6) {
                strikeType = "duel:isolated_counter_T" + tier;
                applyPenaltyTierAugmentation(level, target, Math.max(60.0F, target.getMaxHealth() * 0.35F), AkashicCubeEntity.VIOLET);
            } else {
                strikeType = "duel:terminus_counter";
                executeOmegaTerminus(level, target);
            }
        }

        observed(6.0F);
        evaluateStrikeResult(level, target, preHp, Math.max(25.0F, target.getMaxHealth() * 0.2F), strikeType);
        return strikeType;
    }

    private void triggerBossSkillForStrike(ServerLevel level, LivingEntity target, int tier) {
        if (castLock > 0) return;
        int skillPick = (host.tickCount / 10 + tier) % 6;
        switch (skillPick) {
            case 0 -> observeShot(target);
            case 1 -> lance(target);
            case 2 -> dataRain(target);
            case 3 -> homing(target);
            case 4 -> refract(target);
            default -> {
                if (host.isPhase2()) cage(target);
                else observeShot(target);
            }
        }
    }

    public boolean isBursting() {
        return burstLeft > 0;
    }

    /** 頭上観測 UI（{@code OBSERVATION_UI_DATA}）へ直接対決のペナルティ階層・凶悪感知・論理突破率を書き出す */
    public void writeDuelTelemetry(CompoundTag tag) {
        LivingEntity target = resolveCurrentTarget();
        TargetPenaltyState st = stateFor(target);
        int tier = st.penaltyTier;
        int threshold = st.effectiveFailThreshold(globalSensedWeight);
        ViciousLevel vLevel = highestViciousLevel(target);
        int sensedW = totalSensedWeight(target);
        int sensedCnt = totalSensedEvents(target);
        String lastVicious = !st.lastViciousOp.isEmpty() ? st.lastViciousOp : lastGlobalViciousOp;

        tag.putInt("PenaltyTier", tier);
        tag.putInt("MaxPenaltyTier", MAX_PENALTY_TIER);
        tag.putString("PenaltyTierLabel", penaltyTierLabel(tier));
        tag.putInt("PenaltyFails", st.fails);
        tag.putInt("PenaltyFailThreshold", threshold);
        tag.putInt("ViciousLevel", vLevel.level);
        tag.putString("ViciousLabel", vLevel.label);
        tag.putInt("ViciousWeight", sensedW);
        tag.putInt("ViciousEvents", sensedCnt);
        tag.putString("LastViciousOp", lastVicious != null ? lastVicious : "");
        tag.putBoolean("Phase2", host.isPhase2());
        tag.putFloat("BreakthroughGauge", host.gauge());
        tag.putFloat("AdaptAttackMult", attackMultiplier());
        tag.putFloat("AdaptArmorBonus", armorBonus());
        tag.putFloat("AdaptRegen", regenPerSecond());
    }

    private static float readEffectiveHealth(LivingEntity entity) {
        if (entity == null) return 0.0F;
        AkashicResistanceAuditor.ensureHealthDataItemNeverNull(entity, 0.0F);
        float direct = DirectRead.health(entity);
        float vanilla = direct;
        try {
            vanilla = entity.getHealth();
        } catch (Throwable ignored) {}
        if (!Float.isFinite(direct)) return Float.isFinite(vanilla) ? vanilla : 0.0F;
        if (!Float.isFinite(vanilla)) return direct;
        return Math.min(direct, vanilla);
    }
}
