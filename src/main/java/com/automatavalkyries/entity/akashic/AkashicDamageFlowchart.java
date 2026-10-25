package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ノーマルダメージ（{@code LivingEntity#hurt}）の緻密な有効ダメージ調査＆適応執行フローチャート
 * （2026-10-26 ユーザー指定: 「ノーマルダメージでも緻密なフローチャートを組んで有効ダメージ調査をしっかりしてティア１でも抜かりなく強く」）。
 *
 * <h3>7 段階・有効ダメージ調査フローチャート</h3>
 * <pre>
 * [Step 0: 事前検問・防御解除 (Pre-Scan &amp; Guard Disarm)]
 *   ├─ DirectRead.health と getHealth() の乖離測定、前回打撃からの自然回復・超速再生率 (healRate HP/s) 算出
 *   ├─ DAMAGE_RESISTANCE / ABSORPTION / REGENERATION 剥奪、盾ガード解除 (disableShield)
 *   ├─ 不死のトーテム保持時は予備致死打撃でトーテムを先消費、XP ラストスタンド検知済なら経験値消去
 *   └─ invulnerableTime=0, hurtTime=0, hurtDuration=0, lastHurt=-Float.MAX_VALUE で無敵時間・差分減衰を完全リセット
 *          ↓
 * [Step 1: 優先属性の試行 &amp; 貫通率測定 (Primary Strike &amp; Penetration Measurement)]
 *   ├─ ステージ指定の DamageSource に「既知の足切り (damageFloor)」「上限 (damageCeiling)」「軽減率逆補正 (1/mitigationRatio)」を適用して打撃
 *   └─ 実 HP 減少量 dealt = preHp - postHp を精密測定
 *          ↓
 * [Step 2: dealt == 0 時の 3 分岐診断ツリー (Zero-Damage Diagnostic Decision Tree)]
 *   ├─ 【分岐 2A: 過剰ダメージ拒否上限 (Over-Damage Ceiling Rejection) 調査】
 *   │    「一定以上の大ダメージを 0 にする / キャンセルする」ボス・MOD 防御を疑い、
 *   │    maxHp*0.20 → maxHp*0.09 → 15.0 → 5.0 → 1.0 の下降プローブで通る最大上限 (damageCeiling) を特定
 *   ├─ 【分岐 2B: 足切り閾値 (Under-Damage Floor) 調査】
 *   │    「一定以下の小ダメージを無視する」防御を疑い、
 *   │    maxFail と minPass の二分探索および段階増幅 (x2.5 → x8.0 → maxHp*0.55 → maxHp*1.10) で足切り境界 (damageFloor) を特定
 *   └─ 【分岐 2C: 属性耐性・攻撃者フィルタ (Source &amp; Attacker Topology) 調査】
 *        16 種のダメージチャネル × 4 種の攻撃者トポロジー（直接攻撃者 / 間接オーナー / 攻撃者なし環境偽装 / 対象自身のオウンゴール偽装）を
 *        UCB1（通りやすさ EMA + 未試行ボーナス）で探索し、最も貫通率の高い有効 DamageSource を特定
 *          ↓
 * [Step 3: 0 &lt; dealt &lt; attempted 時の軽減・1発上限解析 (Partial Mitigation &amp; Per-Hit Cap Analysis)]
 *   ├─ 異なる入力量に対して dealt がほぼ同一値（±10%以内）に頭打ちする場合 → 「1発あたり上限 (perHitLimit = hitLimit)」と確定
 *   └─ 一定の割合で減衰している場合 → 「線形軽減率 (mitigationRatio = dealt / attempted)」を学習し、次回以降の入力量を 1/ratio 倍へ逆補正
 *          ↓
 * [Step 4: ティア 1 適応型・多段刻み本執行 (Adaptive Multi-Slice Execution in Tier 1)]
 *   ├─ 1発上限 (perHitLimit) または過剰ダメージ上限 (damageCeiling) を持つ対象に対し、1 回のステージ内で上限値ぴったりの刻み打撃を
 *   │  無敵時間リセット＋属性ローテーション付きで連続執行（最大 16 刻み）し、ティア 1 単体でも上限持ちモブを削り切る
 *   └─ 線形軽減や足切りを持つ対象には、特定した有効 DamageSource と逆補正ダメージ量で追撃し、本来の要求ダメージ量を確実に通す
 *          ↓
 * [Step 5: 事後書き戻し監視 &amp; Tick 間マイクロ連撃 (Post-Strike Revert Watch &amp; Micro-Burst)]
 *   └─ 打撃から 1・3・10 Tick 後に実 HP が戻っていないかを検査（RevertCheck）。
 *      書き戻しが検知された対象には、書き戻し周期より細かい毎 Tick マイクロ連撃（MicroBurst）を予約して再生を上回る速度で削り切る
 * </pre>
 */
public final class AkashicDamageFlowchart {

    /** 調査対象のノーマルダメージチャネル数 */
    public static final int CHANNEL_COUNT = 16;

    /** UCB1 探索係数と EMA 平滑化係数 */
    private static final float UCB_EXPLORE = 0.40F;
    private static final float EMA_ALPHA = 0.40F;

    /** 1 ステージ内で多段刻み追撃を行う最大スライス数 */
    private static final int MAX_SLICES_PER_STAGE = 16;

    /** 時間窓上限の履歴保持期間（Tick）と初期窓長（Tick） */
    private static final int WINDOW_HISTORY_TICKS = 1200;
    private static final int DEFAULT_CAP_WINDOW_TICKS = 200;

    /** カスタム DamageType ID 一覧 */
    private static final String[] CUSTOM_DAMAGE_KEYS = {
            "elenor_hollow", "elenor_ribbon", "elenor_afterimage", "elenor_farthest"
    };

    /** Iron's Spells 学派一覧 */
    private static final String[] SPELL_SCHOOLS = {
            "fire", "ice", "lightning", "holy", "ender", "blood", "evocation", "nature", "eldritch"
    };

    /** チャネル名称（ログ・診断記録用） */
    private static final String[] CHANNEL_NAMES = {
            "C00_mob_attack",
            "C01_indirect_magic_pierce",
            "C02_pure_magic",
            "C03_wither",
            "C04_dragon_breath",
            "C05_sonic_boom",
            "C06_thorns_reflect",
            "C07_env_fire_lava_lightning",
            "C08_env_freeze_dryout_inwall",
            "C09_env_cramming_starve_drown",
            "C10_explosion_stalactite",
            "C11_outside_border",
            "C12_void_fell_out_of_world",
            "C13_generic_kill",
            "C14_custom_unknown_pierce",
            "C15_weakest_spell_school"
    };

    /**
     * 攻撃者トポロジー（攻撃者フィルタ・反射・自傷ガードの切り分け用）
     */
    public enum AttackerTopology {
        /** 通常の直接攻撃（direct=host, causing=host） */
        DIRECT_HOST("direct_host"),
        /** 間接攻撃（direct=null, causing=host）: 近接反射や直接エンティティ判定を回避 */
        INDIRECT_OWNER("indirect_owner"),
        /** 攻撃者なし環境ダメージ偽装（direct=null, causing=null）: 対モブ・対プレイヤーの攻撃者検問を完全回避 */
        ANONYMOUS_ENV("anonymous_env"),
        /** 対象自身の自傷偽装（direct=target, causing=target）: 「自分以外の攻撃を無効化する」ガードを内側から突破 */
        SELF_OWN_GOAL("self_own_goal");

        public final String label;

        AttackerTopology(String label) {
            this.label = label;
        }
    }

    /** 時間窓ダメージ履歴エントリ */
    private record DealtEntry(long tick, float amount) {}

    /** 1・3・10 Tick 後の HP 書き戻し検問タスク */
    private record RevertCheck(
            UUID targetId,
            long dueTick,
            float expectedHp,
            float dealtAmount,
            int delayTicks,
            int channel,
            AttackerTopology topology,
            @Nullable AkashicRecord record
    ) {}

    /** 書き戻し・上限対策の Tick 間マイクロ連撃タスク */
    private static final class MicroBurst {
        final UUID targetId;
        final UUID attackerId;
        int ticksRemaining;
        final float sliceDamage;
        final int preferredChannel;
        final AttackerTopology topology;

        MicroBurst(UUID targetId, UUID attackerId, int ticksRemaining, float sliceDamage, int preferredChannel, AttackerTopology topology) {
            this.targetId = targetId;
            this.attackerId = attackerId;
            this.ticksRemaining = ticksRemaining;
            this.sliceDamage = sliceDamage;
            this.preferredChannel = preferredChannel;
            this.topology = topology;
        }
    }

    /**
     * 対象ごとのノーマルダメージ診断プロファイル
     */
    public static final class TargetDamageProfile {
        public final UUID targetId;
        public final float[] channelRate = new float[CHANNEL_COUNT];
        public final int[] channelTries = new int[CHANNEL_COUNT];
        public final float[] topologyRate = new float[AttackerTopology.values().length];
        public final int[] topologyTries = new int[AttackerTopology.values().length];
        public int totalStrikes = 0;
        public int lastChannel = -1;
        public AttackerTopology bestTopology = AttackerTopology.DIRECT_HOST;

        // 足切り（Under-Damage Floor: これ以下のダメージは 0 にされる）
        public float maxFailedFloor = 0.0F;
        public float minPassedAboveFloor = Float.MAX_VALUE;
        public int floorVotes = 0;
        public boolean floorConfirmed = false;

        // 過剰ダメージ拒否上限（Over-Damage Ceiling: これ以上のダメージはキャンセル/0 にされる）
        public float minFailedCeiling = Float.MAX_VALUE;
        public float maxPassedBelowCeiling = 0.0F;
        public int ceilingVotes = 0;
        public boolean ceilingConfirmed = false;

        // 1 発あたりのダメージ上限（Per-Hit Cap: いくら大きく撃っても 1 発 dealt が hitLimit で頭打ち）
        public boolean perHitCapConfirmed = false;
        public float perHitLimit = Float.MAX_VALUE;
        public float lastDealtAmount = -1.0F;
        public float lastAttemptedAmount = -1.0F;

        // 時間窓累積ダメージ上限（Rolling Window Cap）
        public final ArrayDeque<DealtEntry> windowHistory = new ArrayDeque<>();
        public boolean windowCapConfirmed = false;
        public float windowCap = Float.MAX_VALUE;
        public int windowTicks = DEFAULT_CAP_WINDOW_TICKS;
        public int windowCapVotes = 0;
        public long blockedSinceTick = -1L;

        // 線形軽減率（Armor / Resistance / Custom Reduction Ratio）と非線形弾性指数 α
        public float effectivePenetrationRatio = 1.0F;
        public float elasticityExponent = 1.0F;

        // 回復速度（HP/秒）と書き戻し（Tick Revert）
        public float healRatePerSec = 0.0F;
        public float dealRatePerSec = 0.0F;
        public float lastPostStrikeHp = -1.0F;
        public long lastStrikeTick = -1L;
        public int revertCount = 0;
        public int minRevertDelayTicks = 3;

        // 特殊生存ギミック検知
        public boolean xpLastStandDetected = false;
        public long lastTotemPopTick = -1000L;

        // 多指標リアルタイム DPS テレメトリ ＆ 直近の最適打撃プラン
        public final UniversalDamageFormula.DpsTelemetry dpsTelemetry = new UniversalDamageFormula.DpsTelemetry();
        @Nullable
        public UniversalDamageFormula.OptimalStrikePlan lastOptimalPlan = null;

        public TargetDamageProfile(UUID targetId) {
            this.targetId = targetId;
            for (int i = 0; i < CHANNEL_COUNT; i++) {
                channelRate[i] = 0.5F;
            }
            for (int i = 0; i < topologyRate.length; i++) {
                topologyRate[i] = 0.5F;
            }
        }

        public boolean hasFastRegen() {
            return healRatePerSec > 0.5F;
        }

        public float dealtWithinCurrentWindow(long nowTick) {
            return dealtWithinWindow(this, nowTick, windowTicks);
        }

        /** 診断サマリ文字列を返す（記録・ツールチップ・テスト検証用） */
        public String diagnosticSummary() {
            return String.format(Locale.ROOT,
                    "strikes=%d bestCh=%s topo=%s ratio=%.2f alpha=%.2f floor=%s(fail<=%.1f,pass>=%s) ceiling=%s(pass<=%.1f,fail>=%s) perHitCap=%s windowCap=%s heal=%.1f/s reverts=%d",
                    totalStrikes,
                    lastChannel >= 0 ? CHANNEL_NAMES[lastChannel] : "none",
                    bestTopology.label,
                    effectivePenetrationRatio,
                    elasticityExponent,
                    floorConfirmed ? "YES" : (maxFailedFloor > 0 ? "PROBE" : "NO"),
                    maxFailedFloor,
                    minPassedAboveFloor == Float.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.1f", minPassedAboveFloor),
                    ceilingConfirmed ? "YES" : (minFailedCeiling < Float.MAX_VALUE ? "PROBE" : "NO"),
                    maxPassedBelowCeiling,
                    minFailedCeiling == Float.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.1f", minFailedCeiling),
                    perHitCapConfirmed ? String.format(Locale.ROOT, "%.1f", perHitLimit) : "NO",
                    windowCapConfirmed ? String.format(Locale.ROOT, "%.1f/%dt", windowCap, windowTicks) : "NO",
                    healRatePerSec,
                    revertCount
            );
        }
    }

    /** フローチャート 1 回の執行レポート */
    public record FlowchartStrikeResult(
            float preHp,
            float postHp,
            float totalDealt,
            int slicesExecuted,
            int primaryChannel,
            int effectiveChannel,
            AttackerTopology effectiveTopology,
            boolean penetrated,
            TargetDamageProfile profile
    ) {}

    /** 全エンティティ共用の診断プロファイルキャッシュ（プレイヤー辞書攻撃とアカシック検証で知見を共有） */
    private static final Map<UUID, TargetDamageProfile> PROFILES = new ConcurrentHashMap<>();
    private static final List<RevertCheck> REVERT_CHECKS = new ArrayList<>();
    private static final List<MicroBurst> MICRO_BURSTS = new ArrayList<>();

    private static Field lastHurtField;
    private static boolean lastHurtSearched;

    private AkashicDamageFlowchart() {}

    public static TargetDamageProfile profileOf(LivingEntity target) {
        return PROFILES.computeIfAbsent(target.getUUID(), TargetDamageProfile::new);
    }

    @Nullable
    public static TargetDamageProfile getProfileIfPresent(UUID targetId) {
        return PROFILES.get(targetId);
    }

    public static void clearProfile(UUID targetId) {
        PROFILES.remove(targetId);
    }

    /**
     * 毎サーバー Tick の事後書き戻し検査（1・3・10 Tick 後）および Tick 間マイクロ連撃を処理する。
     */
    public static synchronized void tick(ServerLevel level, @Nullable LivingEntity host, @Nullable AkashicRecord record) {
        if (REVERT_CHECKS.isEmpty() && MICRO_BURSTS.isEmpty()) return;
        long now = level.getGameTime();

        // 1. 1・3・10 Tick 後の HP 書き戻し（Revert）検問
        List<RevertCheck> dueChecks = new ArrayList<>();
        for (Iterator<RevertCheck> it = REVERT_CHECKS.iterator(); it.hasNext(); ) {
            RevertCheck check = it.next();
            if (now < check.dueTick) continue;
            it.remove();
            dueChecks.add(check);
        }
        for (RevertCheck c : dueChecks) {
            Entity e = level.getEntity(c.targetId);
            if (!(e instanceof LivingEntity living) || !AkashicResistanceAuditor.isTargetStillPresent(living)) continue;
            float currentHp = readEffectiveHealth(living);
            if (currentHp >= c.expectedHp + c.dealtAmount * 0.5F) {
                REVERT_CHECKS.removeIf(other -> other.targetId.equals(c.targetId) && other.expectedHp == c.expectedHp);
                TargetDamageProfile p = profileOf(living);
                p.revertCount++;
                p.minRevertDelayTicks = p.revertCount == 1 ? c.delayTicks : Math.min(p.minRevertDelayTicks, c.delayTicks);
                AkashicRecord targetRecord = c.record != null ? c.record : record;
                if (targetRecord != null) {
                    targetRecord.observe(resolveMod(living), "flowchart:revert_detected", "ADAPTED",
                            living.getType().toShortString() + " reverted +" + fmt(currentHp - c.expectedHp)
                                    + " HP after " + c.delayTicks + "t; scheduling micro-bursts", null);
                }
                // 書き戻し周期より速く毎 Tick 削るマイクロ連撃を追加
                if (host != null) {
                    int burstTicks = Math.max(4, Math.min(12, p.minRevertDelayTicks * 3));
                    float perTick = Math.max(2.0F, c.dealtAmount / Math.max(1, p.minRevertDelayTicks));
                    MICRO_BURSTS.add(new MicroBurst(c.targetId, host.getUUID(), burstTicks, perTick, c.channel, c.topology));
                }
            }
        }

        // 2. 予約された Tick 間マイクロ連撃の執行
        for (Iterator<MicroBurst> it = MICRO_BURSTS.iterator(); it.hasNext(); ) {
            MicroBurst b = it.next();
            if (b.ticksRemaining-- <= 0) {
                it.remove();
                continue;
            }
            Entity targetEnt = level.getEntity(b.targetId);
            if (!(targetEnt instanceof LivingEntity victim) || !AkashicResistanceAuditor.isTargetStillPresent(victim)) {
                it.remove();
                continue;
            }
            Entity attackerEnt = host != null && host.getUUID().equals(b.attackerId) ? host : level.getEntity(b.attackerId);
            LivingEntity attacker = attackerEnt instanceof LivingEntity lv ? lv : victim;
            resetInvulnerabilityAndGuard(victim, false);
            DamageSource src = buildDamageSource(level, attacker, victim, b.preferredChannel, b.topology);
            victim.hurt(src, b.sliceDamage);
        }
    }

    /**
     * ティア 1（{@code T001}〜{@code T012}）およびノーマルダメージ打撃において、
     * 7 段階フローチャートに基づく精密な有効ダメージ調査と適応型多段執行を行う。
     *
     * @param level            サーバーワールド
     * @param attacker         攻撃主体（アカシックちゃん又はプレイヤー）
     * @param target           攻撃対象
     * @param preferredChannel ステージが本来指定している優先チャネル（0〜15）
     * @param baseDamage       ステージの基本要求ダメージ量（倍率適用後）
     * @param allowMultiSlice  上限検知時や高 HP 対象へのティア 1 多段刻み追撃を許可するか
     * @param record           観測記録（null 可）
     * @return フローチャート執行結果
     */
    public static FlowchartStrikeResult executeSmartNormalDamage(
            ServerLevel level,
            LivingEntity attacker,
            LivingEntity target,
            int preferredChannel,
            float baseDamage,
            boolean allowMultiSlice,
            @Nullable AkashicRecord record
    ) {
        TargetDamageProfile p = profileOf(target);
        long now = level.getGameTime();
        String targetMod = resolveMod(target);
        float initialHp = readEffectiveHealth(target);
        float maxHp = Math.max(1.0F, target.getMaxHealth());

        // ==== Step 0: 事前検問・回復速度測定・バフ/ガード解除 ====
        if (p.lastPostStrikeHp >= 0.0F && p.lastStrikeTick >= 0L && now > p.lastStrikeTick) {
            float dtSec = (now - p.lastStrikeTick) / 20.0F;
            float healed = Math.max(0.0F, initialHp - p.lastPostStrikeHp) / dtSec;
            p.healRatePerSec = p.healRatePerSec * 0.6F + healed * 0.4F;
            if (healed > 0.5F && p.lastStrikeTick >= now - 10L) {
                p.revertCount++;
            }
        }

        Player targetPlayer = target instanceof Player pl && !pl.isCreative() && !pl.isSpectator() ? pl : null;
        resetInvulnerabilityAndGuard(target, true);

        // トーテム保持者には本打撃の前に予備致死打撃（防具貫通・不死トーテム発動可能な indirectMagic）でトーテムを先消費させる
        if (now - p.lastTotemPopTick > 40L && holdsTotem(target)) {
            p.lastTotemPopTick = now;
            DamageSource baitSrc = buildDamageSource(level, attacker, target, 1, AttackerTopology.DIRECT_HOST);
            target.hurt(baitSrc, initialHp + target.getAbsorptionAmount() + 50.0F);
            resetInvulnerabilityAndGuard(target, true);
            if (record != null) {
                record.observe(targetMod, "flowchart:totem_pop_bait", "ADAPTED",
                        "Popped Totem of Undying before main normal-damage strike", null);
            }
        }

        // XP ラストスタンド検知済みのプレイヤーからは止め前に経験値を抜く
        if (p.xpLastStandDetected && targetPlayer != null && (targetPlayer.experienceLevel > 0 || targetPlayer.totalExperience > 0)) {
            targetPlayer.giveExperienceLevels(-targetPlayer.experienceLevel);
            targetPlayer.experienceProgress = 0.0F;
            targetPlayer.totalExperience = 0;
        }

        // ==== Step 1: 汎用ダメージ逆算式（UniversalDamageFormula）による最適入力ダメージ量の算出 ====
        int activeChannel = clampChannel(preferredChannel);
        AttackerTopology activeTopo = p.bestTopology;
        // 既に優先チャネルが 2 回以上試して貫通率 0% と判明している場合は、最初から UCB 最良チャネルを選択
        if (p.channelTries[activeChannel] >= 2 && p.channelRate[activeChannel] <= 0.01F) {
            activeChannel = pickBestChannelUCB(p, activeChannel);
        }

        DamageSource primarySrc = buildDamageSource(level, attacker, target, activeChannel, activeTopo);
        UniversalDamageFormula.OptimalStrikePlan strikePlan = UniversalDamageFormula.computeOptimalStrikePlan(
                target, primarySrc, baseDamage, allowMultiSlice ? MAX_SLICES_PER_STAGE : 1, 10, p, now);
        p.lastOptimalPlan = strikePlan;
        float shapedDamage = shapeDamageByProfile(
                Math.max(baseDamage, strikePlan.optimalInputPerSlice()), maxHp, p, now);
        float totalRawAttempted = shapedDamage;
        int xpBefore = targetPlayer != null ? targetPlayer.totalExperience + targetPlayer.experienceLevel * 1000 : 0;

        // 初撃を実行
        float hpBeforeFirst = readEffectiveHealth(target);
        boolean accepted = target.hurt(primarySrc, shapedDamage);
        float hpAfterFirst = readEffectiveHealth(target);
        float dealt = Math.max(0.0F, hpBeforeFirst - hpAfterFirst);
        int slices = 1;

        // XP ラストスタンド検知（致死量だったのに生き残り、XP が減った場合）
        if (targetPlayer != null && !p.xpLastStandDetected && hpBeforeFirst - shapedDamage <= 0.0F && hpAfterFirst > 0.0F) {
            int xpAfter = targetPlayer.totalExperience + targetPlayer.experienceLevel * 1000;
            if (xpAfter < xpBefore) {
                p.xpLastStandDetected = true;
                targetPlayer.giveExperienceLevels(-targetPlayer.experienceLevel);
                targetPlayer.experienceProgress = 0.0F;
                targetPlayer.totalExperience = 0;
                if (record != null) {
                    record.observe(targetMod, "flowchart:xp_last_stand", "DETECTED",
                            "Target survived lethal normal damage by burning XP; drained XP", null);
                }
            }
        }

        // ==== Step 2: dealt == 0 時の 3 分岐精密調査ツリー（上限拒否 / 足切り閾値 / 属性・攻撃者フィルタ） ====
        if (dealt <= 1.0E-3F && AkashicResistanceAuditor.isTargetStillPresent(target)) {
            float alreadyDealtInWindow = dealtWithinWindow(p, now, p.windowTicks);

            // 既に同一時間窓内でダメージが通っており（alreadyDealtInWindow > 0）、直後に通らなくなった場合は「時間窓累積ダメージ上限」を最優先検問
            if (alreadyDealtInWindow > 0.0F && p.windowCapConfirmed && alreadyDealtInWindow >= p.windowCap * 0.90F) {
                updateProfileAfterStrike(p, activeChannel, activeTopo, shapedDamage, 0.0F, accepted, now, targetMod, record);
                return new FlowchartStrikeResult(
                        initialHp, readEffectiveHealth(target), 0.0F, slices,
                        preferredChannel, activeChannel, activeTopo, false, p);
            }

            // 【分岐 2A: 過剰ダメージ拒否上限（Over-Damage Ceiling Rejection）の下降プローブ】
            float[] ceilingCandidates = buildCeilingProbeCandidates(shapedDamage, maxHp);
            for (float candidate : ceilingCandidates) {
                if (!AkashicResistanceAuditor.isTargetStillPresent(target)) break;
                resetInvulnerabilityAndGuard(target, false);
                float pre = readEffectiveHealth(target);
                boolean ok = target.hurt(primarySrc, candidate);
                float post = readEffectiveHealth(target);
                float d = Math.max(0.0F, pre - post);
                slices++;
                if (d > 1.0E-3F) {
                    // 下げたら通った！＝過剰ダメージ拒否上限（または1発上限）が存在する
                    p.minFailedCeiling = Math.min(p.minFailedCeiling, shapedDamage);
                    p.maxPassedBelowCeiling = Math.max(p.maxPassedBelowCeiling, candidate);
                    p.ceilingVotes++;
                    p.ceilingConfirmed = true;
                    p.perHitCapConfirmed = true;
                    p.perHitLimit = Math.min(p.perHitLimit, d);
                    dealt += d;
                    shapedDamage = candidate;
                    accepted = ok;
                    if (record != null) {
                        record.observe(targetMod, "flowchart:ceiling_detected", "ADAPTED",
                                String.format(Locale.ROOT, "Rejected %.1f but passed %.1f (dealt=%.1f)",
                                        p.minFailedCeiling, candidate, d), null);
                    }
                    break;
                }
                // 既にこの時間窓でダメージが通っていた場合は、最小プローブ 1 回で通らなければ時間窓上限と判定して無駄な連打を省く
                if (alreadyDealtInWindow > 0.0F) {
                    break;
                }
            }

            // 時間窓内で既に削った直後に下降プローブも 0 だった場合、貫通属性(void)で 1 回だけ確認し、それも 0 なら「時間窓累積上限」と確定
            if (dealt <= 1.0E-3F && alreadyDealtInWindow > 0.0F && AkashicResistanceAuditor.isTargetStillPresent(target)) {
                resetInvulnerabilityAndGuard(target, false);
                float pre = readEffectiveHealth(target);
                DamageSource voidCheck = buildDamageSource(level, attacker, target, 12, p.bestTopology);
                target.hurt(voidCheck, Math.min(baseDamage, 10.0F));
                float d = Math.max(0.0F, pre - readEffectiveHealth(target));
                slices++;
                if (d <= 1.0E-3F) {
                    p.windowCap = Math.min(p.windowCap, alreadyDealtInWindow);
                    p.windowCapVotes++;
                    p.windowCapConfirmed = true;
                    p.blockedSinceTick = now;
                    if (record != null && p.windowCapVotes == 1) {
                        record.observe(targetMod, "flowchart:window_cap_detected", "ADAPTED",
                                String.format(Locale.ROOT, "Confirmed rolling window damage cap=%.1f", p.windowCap), null);
                    }
                    return new FlowchartStrikeResult(
                            initialHp, readEffectiveHealth(target), 0.0F, slices,
                            preferredChannel, activeChannel, activeTopo, false, p);
                } else {
                    activeChannel = 12;
                    dealt += d;
                }
            }

            // 【分岐 2B: 足切り閾値（Under-Damage Floor）の上昇二分・段階プローブ】
            if (dealt <= 1.0E-3F && AkashicResistanceAuditor.isTargetStillPresent(target)) {
                p.maxFailedFloor = Math.max(p.maxFailedFloor, shapedDamage);
                float[] floorCandidates = buildFloorProbeCandidates(shapedDamage, maxHp, p);
                for (float candidate : floorCandidates) {
                    if (!AkashicResistanceAuditor.isTargetStillPresent(target)) break;
                    resetInvulnerabilityAndGuard(target, false);
                    float pre = readEffectiveHealth(target);
                    boolean ok = target.hurt(primarySrc, candidate);
                    float post = readEffectiveHealth(target);
                    float d = Math.max(0.0F, pre - post);
                    slices++;
                    if (d > 1.0E-3F) {
                        // 上げたら通った！＝足切り（Minimum Damage Floor）が存在する
                        p.minPassedAboveFloor = Math.min(p.minPassedAboveFloor, candidate);
                        p.floorVotes++;
                        p.floorConfirmed = true;
                        dealt += d;
                        shapedDamage = candidate;
                        accepted = ok;
                        if (record != null) {
                            record.observe(targetMod, "flowchart:floor_detected", "ADAPTED",
                                    String.format(Locale.ROOT, "Ignored <=%.1f, penetrated at %.1f (dealt=%.1f)",
                                            p.maxFailedFloor, candidate, d), null);
                        }
                        break;
                    } else {
                        p.maxFailedFloor = Math.max(p.maxFailedFloor, candidate);
                    }
                }
            }

            // 【分岐 2C: 属性耐性 & 攻撃者トポロジー（Attacker Topology）の横断スキャン】
            if (dealt <= 1.0E-3F && AkashicResistanceAuditor.isTargetStillPresent(target)) {
                // 優先チャネルは 0 ダメージと記録
                updateChannelStats(p, activeChannel, activeTopo, 0.0F, 1.0F);

                // まず同一チャネルで攻撃者トポロジー 4 種（直接 / 間接 / 攻撃者なし環境 / 自己オウンゴール）を切り替えて検問突破を試みる
                for (AttackerTopology topo : AttackerTopology.values()) {
                    if (topo == activeTopo || !AkashicResistanceAuditor.isTargetStillPresent(target)) continue;
                    resetInvulnerabilityAndGuard(target, false);
                    float pre = readEffectiveHealth(target);
                    DamageSource altTopoSrc = buildDamageSource(level, attacker, target, activeChannel, topo);
                    target.hurt(altTopoSrc, baseDamage);
                    float d = Math.max(0.0F, pre - readEffectiveHealth(target));
                    slices++;
                    if (d > 1.0E-3F) {
                        activeTopo = topo;
                        p.bestTopology = topo;
                        dealt += d;
                        if (record != null) {
                            record.observe(targetMod, "flowchart:topology_switched", "ADAPTED",
                                    "Bypassed attacker filter via topology=" + topo.label + " (dealt=" + fmt(d) + ")", null);
                        }
                        break;
                    }
                }

                // それでも通らなければ、UCB 順で全 16 チャネルを順番にプローブして有効属性を特定する
                if (dealt <= 1.0E-3F && AkashicResistanceAuditor.isTargetStillPresent(target)) {
                    int[] probeOrder = buildChannelProbeOrder(p, activeChannel);
                    for (int candChannel : probeOrder) {
                        if (!AkashicResistanceAuditor.isTargetStillPresent(target)) break;
                        for (AttackerTopology topo : new AttackerTopology[]{
                                p.bestTopology, AttackerTopology.ANONYMOUS_ENV, AttackerTopology.SELF_OWN_GOAL, AttackerTopology.DIRECT_HOST}) {
                            resetInvulnerabilityAndGuard(target, false);
                            float pre = readEffectiveHealth(target);
                            DamageSource candSrc = buildDamageSource(level, attacker, target, candChannel, topo);
                            float probeAmt = p.ceilingConfirmed && p.maxPassedBelowCeiling > 0.0F
                                    ? p.maxPassedBelowCeiling
                                    : (p.floorConfirmed && p.minPassedAboveFloor < Float.MAX_VALUE ? p.minPassedAboveFloor * 1.05F : baseDamage);
                            target.hurt(candSrc, probeAmt);
                            float d = Math.max(0.0F, pre - readEffectiveHealth(target));
                            slices++;
                            if (d > 1.0E-3F) {
                                activeChannel = candChannel;
                                activeTopo = topo;
                                p.bestTopology = topo;
                                dealt += d;
                                shapedDamage = probeAmt;
                                if (record != null) {
                                    record.observe(targetMod, "flowchart:source_switched", "ADAPTED",
                                            "Switched normal damage channel to " + CHANNEL_NAMES[candChannel]
                                                    + " [" + topo.label + "] dealt=" + fmt(d), null);
                                }
                                break;
                            } else {
                                updateChannelStats(p, candChannel, topo, 0.0F, probeAmt);
                            }
                        }
                        if (dealt > 1.0E-3F) break;
                    }
                }
            }
        }

        // ==== Step 3: 貫通統計・1発上限・軽減率の学習 ====
        updateProfileAfterStrike(p, activeChannel, activeTopo, shapedDamage, dealt, accepted, now, targetMod, record);

        // ==== Step 4: ティア 1 適応型・多段刻み本執行（上限突破スライス連撃 ＆ 軽減逆補正追撃） ====
        if (allowMultiSlice && dealt > 0.0F && AkashicResistanceAuditor.isTargetStillPresent(target)) {
            if (p.perHitCapConfirmed || p.ceilingConfirmed) {
                // 1発上限または過剰ダメージ拒否上限がある場合：上限サイズに小分けして、無敵時間リセット＋属性ローテーションで一気に削り切る
                float sliceCap = p.ceilingConfirmed && p.maxPassedBelowCeiling > 0.0F
                        ? p.maxPassedBelowCeiling
                        : (p.perHitCapConfirmed ? p.perHitLimit * 1.05F : baseDamage);
                if (sliceCap > 0.0F && sliceCap < Float.MAX_VALUE) {
                    int altChannel = pickBestChannelUCB(p, activeChannel);
                    while (slices < MAX_SLICES_PER_STAGE
                            && AkashicResistanceAuditor.isTargetStillPresent(target)
                            && readEffectiveHealth(target) > 0.0F) {
                        resetInvulnerabilityAndGuard(target, false);
                        int ch = (slices % 2 == 0) ? activeChannel : (p.channelRate[altChannel] > 0.1F ? altChannel : activeChannel);
                        DamageSource sliceSrc = buildDamageSource(level, attacker, target, ch, activeTopo);
                        float pre = readEffectiveHealth(target);
                        target.hurt(sliceSrc, sliceCap);
                        float d = Math.max(0.0F, pre - readEffectiveHealth(target));
                        slices++;
                        if (d <= 1.0E-3F) {
                            // 同一属性連続無効化対策として別チャネルに切替
                            altChannel = pickBestChannelUCB(p, ch);
                            resetInvulnerabilityAndGuard(target, false);
                            DamageSource retrySrc = buildDamageSource(level, attacker, target, altChannel, activeTopo);
                            pre = readEffectiveHealth(target);
                            target.hurt(retrySrc, sliceCap);
                            d = Math.max(0.0F, pre - readEffectiveHealth(target));
                            slices++;
                            if (d <= 1.0E-3F) {
                                // 時間窓累積上限に到達したと記録してスライス終了
                                float usedInWin = dealtWithinWindow(p, now, p.windowTicks) + dealt;
                                p.windowCap = Math.min(p.windowCap, usedInWin);
                                p.windowCapVotes++;
                                p.windowCapConfirmed = true;
                                p.blockedSinceTick = now;
                                break;
                            }
                        }
                        totalRawAttempted += sliceCap;
                        dealt += d;
                    }
                }
            } else if (dealt < baseDamage * 0.9F && p.effectivePenetrationRatio > 0.01F) {
                // 防具・防具強度・耐性・EPF・MOD軽減率で減衰した場合：UniversalDamageFormula の厳密2次方程式逆関数で不足分を逆算して追撃
                float deficit = baseDamage - dealt;
                DamageSource compSrc = buildDamageSource(level, attacker, target, activeChannel, activeTopo);
                double armor = compSrc.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                        ? 0.0D
                        : target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                double toughness = compSrc.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                        ? 0.0D
                        : target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
                float magicTrans = UniversalDamageFormula.computeMagicAndProtectionTransmittance(target, compSrc);
                float exactInverse = UniversalDamageFormula.solveInverseInputForNet(
                        deficit * 1.05F, armor, toughness, magicTrans, p.effectivePenetrationRatio, p.elasticityExponent);
                float compensated = Math.min(baseDamage * 25.0F, Math.max(deficit / Math.max(0.05F, p.effectivePenetrationRatio), exactInverse));
                if (p.floorConfirmed && p.minPassedAboveFloor < Float.MAX_VALUE) {
                    compensated = Math.max(compensated, p.minPassedAboveFloor * 1.05F);
                }
                resetInvulnerabilityAndGuard(target, false);
                float pre = readEffectiveHealth(target);
                target.hurt(compSrc, compensated);
                float d = Math.max(0.0F, pre - readEffectiveHealth(target));
                totalRawAttempted += compensated;
                slices++;
                dealt += d;
            }
        }

        // ==== Step 5: 事後状態記録 & DPS テレメトリ更新 & 1・3・10 Tick 後の書き戻し監視予約 ====
        float finalHp = readEffectiveHealth(target);
        float totalNetDealt = Math.max(dealt, Math.max(0.0F, initialHp - finalHp));
        p.lastPostStrikeHp = finalHp;
        p.lastStrikeTick = now;
        p.dpsTelemetry.recordStrike(now, totalRawAttempted, totalNetDealt, slices);
        p.lastOptimalPlan = UniversalDamageFormula.computeOptimalStrikePlan(
                target,
                buildDamageSource(level, attacker, target, activeChannel, activeTopo),
                baseDamage,
                allowMultiSlice ? MAX_SLICES_PER_STAGE : 1,
                10,
                p,
                now
        );

        if (totalNetDealt > 0.0F && AkashicResistanceAuditor.isTargetStillPresent(target)) {
            synchronized (AkashicDamageFlowchart.class) {
                for (int delay : new int[]{1, 3, 10}) {
                    REVERT_CHECKS.add(new RevertCheck(
                            target.getUUID(), now + delay, finalHp, totalNetDealt, delay, activeChannel, activeTopo, record));
                }
                if (p.revertCount > 0 || p.hasFastRegen()) {
                    int burstTicks = Math.max(4, Math.min(10, p.minRevertDelayTicks * 2));
                    float sliceAmt = p.perHitCapConfirmed && p.perHitLimit < Float.MAX_VALUE
                            ? p.perHitLimit
                            : Math.max(5.0F, baseDamage / burstTicks);
                    MICRO_BURSTS.add(new MicroBurst(
                            target.getUUID(), attacker.getUUID(), burstTicks, sliceAmt, activeChannel, activeTopo));
                }
            }
        }

        boolean penetrated = !AkashicResistanceAuditor.isTargetStillPresent(target);
        return new FlowchartStrikeResult(
                initialHp,
                finalHp,
                totalNetDealt,
                slices,
                preferredChannel,
                activeChannel,
                activeTopo,
                penetrated,
                p
        );
    }

    // ---- 内部ヘルパー：プロファイルに基づくダメージ量整形・プローブ候補生成 ----

    private static float shapeDamageByProfile(float baseDamage, float maxHp, TargetDamageProfile p, long now) {
        float shaped = baseDamage;

        // 1. 線形軽減がある場合は逆補正（最大 10 倍）
        if (p.effectivePenetrationRatio > 0.02F && p.effectivePenetrationRatio < 0.85F && !p.perHitCapConfirmed && !p.ceilingConfirmed) {
            shaped = Math.min(baseDamage * 10.0F, baseDamage / p.effectivePenetrationRatio);
        }

        // 2. 回復速度が速い相手には回復分を上乗せ
        if (p.hasFastRegen()) {
            shaped *= Math.min(2.0F, 1.0F + p.healRatePerSec / Math.max(5.0F, p.dealRatePerSec));
        }

        // 3. 足切り（Floor）が判明している場合は必ず足切りより上に引き上げる
        if (p.maxFailedFloor > 0.0F && shaped <= p.maxFailedFloor * 1.05F) {
            if (p.minPassedAboveFloor < Float.MAX_VALUE) {
                shaped = p.floorConfirmed
                        ? p.minPassedAboveFloor * 1.05F
                        : (p.maxFailedFloor + p.minPassedAboveFloor) * 0.5F;
            } else {
                shaped = Math.max(shaped * 2.5F, p.maxFailedFloor * 2.0F);
            }
        }

        // 4. 過剰ダメージ拒否上限（Ceiling）が判明している場合は上限以下に抑える
        if (p.ceilingConfirmed && p.maxPassedBelowCeiling > 0.0F && shaped > p.maxPassedBelowCeiling) {
            shaped = p.maxPassedBelowCeiling;
        }

        // 5. 時間窓累積上限（Window Cap）が判明している場合は残り枠に合わせる
        if (p.windowCapConfirmed && p.windowCap < Float.MAX_VALUE) {
            float used = dealtWithinWindow(p, now, p.windowTicks);
            if (used < p.windowCap * 0.95F) {
                shaped = Math.min(shaped, Math.max(1.0F, p.windowCap - used));
            }
        }

        return Math.max(0.5F, shaped);
    }

    private static float[] buildCeilingProbeCandidates(float failedAmount, float maxHp) {
        List<Float> list = new ArrayList<>();
        float[] candidates = {
                maxHp * 0.45F,
                maxHp * 0.20F,
                maxHp * 0.095F,
                50.0F,
                19.0F,
                9.5F,
                4.5F,
                1.0F
        };
        for (float c : candidates) {
            if (c > 0.1F && c < failedAmount * 0.95F) {
                list.add(c);
            }
        }
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static float[] buildFloorProbeCandidates(float failedAmount, float maxHp, TargetDamageProfile p) {
        List<Float> list = new ArrayList<>();
        if (p.minPassedAboveFloor < Float.MAX_VALUE && p.minPassedAboveFloor > failedAmount) {
            list.add((failedAmount + p.minPassedAboveFloor) * 0.5F);
            list.add(p.minPassedAboveFloor * 1.02F);
        }
        float[] escalations = {
                failedAmount * 2.5F,
                failedAmount * 6.0F,
                Math.max(failedAmount * 10.0F, maxHp * 0.55F),
                Math.max(failedAmount * 25.0F, maxHp * 1.15F),
                Math.max(1000.0F, maxHp * 5.0F)
        };
        for (float c : escalations) {
            if (c > failedAmount * 1.05F && (p.minFailedCeiling == Float.MAX_VALUE || c < p.minFailedCeiling)) {
                list.add(c);
            }
        }
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static void updateChannelStats(TargetDamageProfile p, int channel, AttackerTopology topo, float dealt, float attempted) {
        int ch = clampChannel(channel);
        float ratio = attempted > 0.0F ? Math.max(0.0F, Math.min(1.0F, dealt / attempted)) : 0.0F;
        p.channelRate[ch] = p.channelTries[ch] == 0 ? ratio : p.channelRate[ch] * (1.0F - EMA_ALPHA) + ratio * EMA_ALPHA;
        p.channelTries[ch]++;

        int tIdx = topo.ordinal();
        p.topologyRate[tIdx] = p.topologyTries[tIdx] == 0 ? ratio : p.topologyRate[tIdx] * (1.0F - EMA_ALPHA) + ratio * EMA_ALPHA;
        p.topologyTries[tIdx]++;
    }

    private static void updateProfileAfterStrike(
            TargetDamageProfile p,
            int channel,
            AttackerTopology topo,
            float attempted,
            float dealt,
            boolean accepted,
            long now,
            String targetMod,
            @Nullable AkashicRecord record
    ) {
        p.totalStrikes++;
        p.lastChannel = channel;
        updateChannelStats(p, channel, topo, dealt, attempted);

        if (p.lastStrikeTick >= 0L && now > p.lastStrikeTick) {
            float dtSec = (now - p.lastStrikeTick) / 20.0F;
            p.dealRatePerSec = p.dealRatePerSec * 0.6F + (dealt / dtSec) * 0.4F;
        }

        // 時間窓履歴の更新
        while (!p.windowHistory.isEmpty() && now - p.windowHistory.peekFirst().tick > WINDOW_HISTORY_TICKS) {
            p.windowHistory.pollFirst();
        }
        if (dealt > 0.0F) {
            p.windowHistory.addLast(new DealtEntry(now, dealt));
        }

        // 1発あたり上限（Per-Hit Cap）の判定：
        // ① 前回と異なる入力量なのに dealt がほぼ同じ（±10%以内）で attempted の 75% 未満に抑えられている場合
        // ② または 1 発で attempted の半分以下に大きくクランプされ、かつ 2 回連続で同じ上限値になった場合
        if (dealt > 0.0F && dealt < attempted * 0.75F) {
            if (p.lastDealtAmount > 0.0F
                    && Math.abs(dealt - p.lastDealtAmount) <= Math.max(0.5F, p.lastDealtAmount * 0.10F)
                    && Math.abs(attempted - p.lastAttemptedAmount) >= 1.0F) {
                p.perHitCapConfirmed = true;
                p.perHitLimit = Math.max(dealt, p.lastDealtAmount);
                if (record != null) {
                    record.observe(targetMod, "flowchart:per_hit_cap", "ADAPTED",
                            String.format(Locale.ROOT, "Confirmed per-hit cap=%.1f (attempted %.1f -> dealt %.1f)",
                                    p.perHitLimit, attempted, dealt), null);
                }
            } else {
                // 非線形弾性指数 α = ln(d2 / d1) / ln(x2 / x1) および軽減率 η の推定更新
                if (p.lastDealtAmount > 0.5F && p.lastAttemptedAmount > 0.5F
                        && Math.abs(Math.log(attempted / p.lastAttemptedAmount)) > 0.15D) {
                    double logD = Math.log(dealt / p.lastDealtAmount);
                    double logX = Math.log(attempted / p.lastAttemptedAmount);
                    float measuredAlpha = (float) Math.max(0.25D, Math.min(2.0D, logD / logX));
                    p.elasticityExponent = p.elasticityExponent * 0.6F + measuredAlpha * 0.4F;
                }
                // 線形軽減率（Armor / Resistance 等）として記録
                float ratio = Math.max(0.02F, Math.min(1.0F, dealt / Math.max(1.0E-3F, attempted)));
                p.effectivePenetrationRatio = p.effectivePenetrationRatio * 0.5F + ratio * 0.5F;
            }
        } else if (dealt >= attempted * 0.85F) {
            p.effectivePenetrationRatio = p.effectivePenetrationRatio * 0.5F + 1.0F * 0.5F;
        }

        // 時間窓累積上限（Rolling Window Cap）の判定
        boolean passedBefore = p.minPassedAboveFloor < Float.MAX_VALUE && attempted >= p.minPassedAboveFloor * 0.95F;
        if (dealt <= 1.0E-3F && accepted && passedBefore && !p.windowHistory.isEmpty()) {
            if (p.blockedSinceTick < 0L) {
                p.blockedSinceTick = now;
                float sumInWindow = dealtWithinWindow(p, now, Math.max(p.windowTicks, DEFAULT_CAP_WINDOW_TICKS));
                if (sumInWindow > 0.0F) {
                    p.windowCap = p.windowCapVotes == 0 ? sumInWindow : Math.min(p.windowCap, sumInWindow);
                    if (++p.windowCapVotes >= 2) {
                        p.windowCapConfirmed = true;
                    }
                }
            }
        } else if (dealt > 0.0F) {
            if (attempted < p.minPassedAboveFloor) {
                p.minPassedAboveFloor = attempted;
            }
            if (p.blockedSinceTick >= 0L) {
                DealtEntry first = p.windowHistory.peekFirst();
                if (first != null && first.tick < p.blockedSinceTick) {
                    int measuredWindow = (int) Math.max(20L, Math.min(2400L, now - first.tick));
                    p.windowTicks = p.windowCapVotes <= 1 ? measuredWindow : (int) (p.windowTicks * 0.6F + measuredWindow * 0.4F);
                }
                p.blockedSinceTick = -1L;
            }
        }

        if (dealt > 0.0F) {
            p.lastDealtAmount = dealt;
            p.lastAttemptedAmount = attempted;
        }
    }

    private static float dealtWithinWindow(TargetDamageProfile p, long now, int windowTicks) {
        float sum = 0.0F;
        for (DealtEntry d : p.windowHistory) {
            if (now - d.tick < windowTicks) sum += d.amount;
        }
        return sum;
    }

    private static int pickBestChannelUCB(TargetDamageProfile p, int avoidChannel) {
        // まだ一度も試していないチャネルがあれば優先
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            if (i != avoidChannel && p.channelTries[i] == 0) {
                return i;
            }
        }
        int best = 0;
        float bestScore = -1.0E9F;
        double logTotal = Math.log(p.totalStrikes + 2.0);
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            if (i == avoidChannel) continue;
            float ucb = p.channelRate[i] + UCB_EXPLORE * (float) Math.sqrt(logTotal / (p.channelTries[i] + 1.0));
            if (ucb > bestScore) {
                bestScore = ucb;
                best = i;
            }
        }
        return best;
    }

    private static int[] buildChannelProbeOrder(TargetDamageProfile p, int skipChannel) {
        List<Integer> order = new ArrayList<>(CHANNEL_COUNT);
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            if (i != skipChannel) order.add(i);
        }
        double logTotal = Math.log(p.totalStrikes + 2.0);
        order.sort((a, b) -> {
            float scoreA = (p.channelTries[a] == 0 ? 1.5F : p.channelRate[a])
                    + UCB_EXPLORE * (float) Math.sqrt(logTotal / (p.channelTries[a] + 1.0));
            float scoreB = (p.channelTries[b] == 0 ? 1.5F : p.channelRate[b])
                    + UCB_EXPLORE * (float) Math.sqrt(logTotal / (p.channelTries[b] + 1.0));
            return Float.compare(scoreB, scoreA);
        });
        int[] result = new int[order.size()];
        for (int i = 0; i < order.size(); i++) result[i] = order.get(i);
        return result;
    }

    /**
     * 16 種のノーマルダメージチャネル × 4 種の攻撃者トポロジーから {@link DamageSource} を構築する。
     */
    public static DamageSource buildDamageSource(
            ServerLevel level,
            LivingEntity attacker,
            LivingEntity target,
            int channel,
            AttackerTopology topology
    ) {
        Entity direct = switch (topology) {
            case DIRECT_HOST -> attacker;
            case INDIRECT_OWNER, ANONYMOUS_ENV -> null;
            case SELF_OWN_GOAL -> target;
        };
        Entity causing = switch (topology) {
            case DIRECT_HOST, INDIRECT_OWNER -> attacker;
            case ANONYMOUS_ENV -> null;
            case SELF_OWN_GOAL -> target;
        };

        var sources = level.damageSources();
        var reg = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        return switch (clampChannel(channel)) {
            case 0 -> direct != null && causing instanceof LivingEntity lv
                    ? sources.mobAttack(lv)
                    : createTypedSource(reg, DamageTypes.MOB_ATTACK, direct, causing);
            case 1 -> createTypedSource(reg, DamageTypes.INDIRECT_MAGIC, direct, causing);
            case 2 -> createTypedSource(reg, DamageTypes.MAGIC, direct, causing);
            case 3 -> createTypedSource(reg, DamageTypes.WITHER, direct, causing);
            case 4 -> createTypedSource(reg, DamageTypes.DRAGON_BREATH, direct, causing);
            case 5 -> createTypedSource(reg, DamageTypes.SONIC_BOOM, direct, causing);
            case 6 -> createTypedSource(reg, DamageTypes.THORNS, direct, causing);
            case 7 -> createTypedSource(reg, DamageTypes.LIGHTNING_BOLT, direct, causing);
            case 8 -> createTypedSource(reg, DamageTypes.FREEZE, direct, causing);
            case 9 -> createTypedSource(reg, DamageTypes.DROWN, direct, causing);
            case 10 -> createTypedSource(reg, DamageTypes.EXPLOSION, direct, causing);
            case 11 -> createTypedSource(reg, DamageTypes.OUTSIDE_BORDER, direct, causing);
            case 12 -> createTypedSource(reg, DamageTypes.FELL_OUT_OF_WORLD, direct, causing);
            case 13 -> createTypedSource(reg, DamageTypes.GENERIC_KILL, direct, causing);
            case 14 -> createCustomModDamageSource(reg, CUSTOM_DAMAGE_KEYS[Math.abs(target.getId()) % CUSTOM_DAMAGE_KEYS.length], direct, causing);
            case 15 -> {
                DamageSource schoolSrc = buildWeakestSpellSchoolSource(level, target, direct, causing);
                yield schoolSrc != null ? schoolSrc : createTypedSource(reg, DamageTypes.INDIRECT_MAGIC, direct, causing);
            }
            default -> sources.genericKill();
        };
    }

    private static DamageSource createTypedSource(
            net.minecraft.core.Registry<DamageType> reg,
            ResourceKey<DamageType> key,
            @Nullable Entity direct,
            @Nullable Entity causing
    ) {
        Holder<DamageType> holder = reg.getHolderOrThrow(key);
        return new DamageSource(holder, direct, causing);
    }

    private static DamageSource createCustomModDamageSource(
            net.minecraft.core.Registry<DamageType> reg,
            String path,
            @Nullable Entity direct,
            @Nullable Entity causing
    ) {
        ResourceKey<DamageType> key = ResourceKey.create(
                Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, path));
        var holderOpt = reg.getHolder(key);
        if (holderOpt.isPresent()) {
            return new DamageSource(holderOpt.get(), direct, causing);
        }
        return createTypedSource(reg, DamageTypes.GENERIC_KILL, direct, causing);
    }

    @Nullable
    private static DamageSource buildWeakestSpellSchoolSource(
            ServerLevel level,
            LivingEntity victim,
            @Nullable Entity direct,
            @Nullable Entity causing
    ) {
        var reg = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> bestHolder = null;
        double lowestResist = Double.MAX_VALUE;
        for (String school : SPELL_SCHOOLS) {
            var key = ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("irons_spellbooks", school + "_magic"));
            var holderOpt = reg.getHolder(key);
            if (holderOpt.isEmpty()) continue;
            var resistAttr = ForgeRegistries.ATTRIBUTES.getValue(
                    ResourceLocation.fromNamespaceAndPath("irons_spellbooks", school + "_magic_resist"));
            double resist = resistAttr != null && victim.getAttributes().hasAttribute(resistAttr)
                    ? victim.getAttributeValue(resistAttr)
                    : 1.0D;
            if (resist < lowestResist) {
                lowestResist = resist;
                bestHolder = holderOpt.get();
            }
        }
        return bestHolder != null ? new DamageSource(bestHolder, direct, causing) : null;
    }

    /**
     * 被弾無敵時間・lastHurt 減衰・盾ガード・防御バフを即座にリセットする。
     */
    public static void resetInvulnerabilityAndGuard(LivingEntity target, boolean stripEffects) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        target.hurtDuration = 0;
        setLastHurtRaw(target, -Float.MAX_VALUE);
        if (target instanceof Player pl && pl.isBlocking()) {
            pl.disableShield(true);
        }
        if (stripEffects) {
            target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            target.removeEffect(MobEffects.ABSORPTION);
            target.setAbsorptionAmount(0.0F);
            if (target.hasEffect(MobEffects.REGENERATION)) {
                target.removeEffect(MobEffects.REGENERATION);
            }
        }
    }

    private static boolean holdsTotem(LivingEntity target) {
        return target.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || target.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private static void setLastHurtRaw(LivingEntity target, float value) {
        if (!lastHurtSearched) {
            lastHurtField = UnsafeHelper.findField(LivingEntity.class, "lastHurt", "f_20898_");
            lastHurtSearched = true;
        }
        if (lastHurtField != null) {
            UnsafeHelper.setFieldValue(target, lastHurtField.getName(), value);
        }
    }

    private static float readEffectiveHealth(LivingEntity entity) {
        if (entity == null) return 0.0F;
        AkashicResistanceAuditor.ensureHealthDataItemNeverNull(entity, 0.0F);
        float direct = DirectRead.health(entity);
        float vanilla = direct;
        try {
            vanilla = entity.getHealth();
        } catch (Throwable ignored) {
            AkashicResistanceAuditor.ensureHealthDataItemNeverNull(entity, Float.isFinite(direct) ? direct : 0.0F);
        }
        if (!Float.isFinite(direct)) return Float.isFinite(vanilla) ? vanilla : 0.0F;
        if (!Float.isFinite(vanilla)) return direct;
        return Math.min(direct, vanilla);
    }

    public static String channelName(int channel) {
        if (channel < 0 || channel >= CHANNEL_NAMES.length) return "none";
        return CHANNEL_NAMES[channel];
    }

    private static int clampChannel(int channel) {
        if (channel < 0) return 0;
        if (channel >= CHANNEL_COUNT) return CHANNEL_COUNT - 1;
        return channel;
    }

    private static String resolveMod(Entity target) {
        String mod = CallerAttribution.modOf(target.getClass());
        if (CallerAttribution.isForeign(mod)) return mod;
        var key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return key != null ? key.getNamespace() : mod;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
