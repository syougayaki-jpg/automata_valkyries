package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.entity.akashic.AkashicDamageFlowchart.TargetDamageProfile;
import com.automatavalkyries.protect.DirectRead;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * 最適 DPS 算出 ＆ 汎用ダメージ逆算エンジン（2026-10-26 ユーザー指定:
 * 「より最適なDPS算出汎用ダメージ計算式を作って」）。
 *
 * <h3>1. 汎用ダメージ順方向・逆方向（厳密逆算）計算式</h3>
 * Minecraft 1.20.1 のバニラ防御パイプラインおよび他 MOD の非線形軽減・足切り・上限・超速再生を統合した順方向モデル：
 * <pre>
 *   D_post_armor(x, A, T) = x * (1 - min(20, max(A/5, A - 4x / (T + 8))) / 25)
 *   D_post_magic(x)       = D_post_armor(x, A, T) * M_res * M_epf
 *   D_mod_net(x)          = clamp_ceiling( step_floor( D_post_magic(x) * η_mod * x^(α - 1) ) )
 * </pre>
 * これに対し、目標実効ダメージ {@code D_req} を通すための厳密な逆算入力値 {@code x*} を以下の閉形式（2次方程式の正根）で求める：
 * <pre>
 *   1) 再生・書き戻し・割合スケーリング補償を加えた必要実効量:
 *      D_req = (D_base + β_pct * HP_max + γ_regen * (R_heal + R_revert) * Δt)
 *   2) バニラ耐性・EPF・MOD軽減率 η_mod および非線形弾性指数 α の逆変換:
 *      y_armor = (D_req / max(0.02, M_res * M_epf * η_mod))^(1 / α)
 *   3) 防具・防具強度方程式 (4 / (25*(T+8))) * x^2 + (1 - A/25) * x - y_armor = 0 の厳密解:
 *      x_raw = solveExactInverseArmorDamage(y_armor, A, T)
 *   4) 境界制約（足切り F_min / 拒否上限 C_max / 時間窓残量 B_rem）への射影:
 *      x* = clamp(x_raw, F_min * 1.05, min(C_max, B_rem))
 * </pre>
 *
 * <h3>2. 多指標・最適 DPS 算出式（{@link DpsTelemetry}）</h3>
 * 単なる「累計ダメージ ÷ 経過秒数」では初撃スパイクや途中回復・上限停止で大きく歪むため、以下の 5 指標を同時算出する：
 * <ul>
 *   <li><b>① 理論無制限 DPS ({@code nominalRawDps})</b>: 上限クランプや防具減衰前の投入火力ベースの DPS</li>
 *   <li><b>② 実効削り DPS ({@code effectiveDealtDps})</b>: 実際に削った HP 総量 ÷ 実交戦時間</li>
 *   <li><b>③ 瞬間 EWMA DPS ({@code ewmaInstantDps})</b>: 半減期 τ = 2.0 秒（λ = ln(2)/2.0）の指数減衰カーネルによるリアルタイム瞬間 DPS</li>
 *   <li><b>④ 直近窓ピーク DPS ({@code slidingWindowDps})</b>: 直近 100 Tick（5.0 秒）のスライディングウィンドウ実効 DPS</li>
 *   <li><b>⑤ 純実効 DPS ({@code netBleedDps}) &amp; 推定撃破時間 ({@code estimatedTtkSeconds})</b>:
 *       {@code DPS_net = max(0, max(ewmaInstantDps, slidingWindowDps) - R_heal - R_revert)}、
 *       {@code ETTK = HP_current / DPS_net}（{@code DPS_net <= 0} の場合は通常ダメージでは撃破不能と判定し、上位ティアへの即時昇格フラグを立てる）</li>
 * </ul>
 */
public final class UniversalDamageFormula {

    /** EWMA 瞬間 DPS の半減期（秒）と減衰定数 λ = ln(2) / τ */
    private static final double EWMA_HALF_LIFE_SEC = 2.0;
    private static final double EWMA_LAMBDA = Math.log(2.0) / EWMA_HALF_LIFE_SEC;

    /** スライディングウィンドウ DPS の窓幅（100 Tick = 5.0 秒） */
    private static final int DPS_WINDOW_TICKS = 100;

    /** 高 HP ボスに対する割合ダメージボーナス係数（最大 HP の 5%、上限 100 万） */
    public static final float MAX_HP_PERCENT_RATIO = 0.05F;
    public static final float MAX_HP_PERCENT_CAP = 1_000_000.0F;

    /** 再生・書き戻し速度に対する安全上乗せマージン係数 γ */
    private static final float REGEN_COMPENSATION_GAMMA = 1.35F;

    private UniversalDamageFormula() {}

    /**
     * 最適打撃プラン（1 回の攻撃アクションにおける最適入力ダメージ量・最適連撃刻み数・理論 DPS・推定撃破時間）
     */
    public record OptimalStrikePlan(
            float optimalInputPerSlice,
            float expectedNetPerSlice,
            int optimalSliceCount,
            int optimalIntervalTicks,
            double theoreticalMaxDps,
            double estimatedTtkSeconds,
            boolean requiresTierEscalation,
            String formulaSummary
    ) {}

    /**
     * 多指標・リアルタイム DPS テレメトリ
     */
    public static final class DpsTelemetry {
        private record Sample(long tick, float rawAttempted, float actualDealt) {}

        private final ArrayDeque<Sample> windowSamples = new ArrayDeque<>();
        private long firstHitTick = -1L;
        private long lastHitTick = -1L;
        private double cumulativeRawDamage = 0.0;
        private double cumulativeDealtDamage = 0.0;
        private double ewmaDealtRate = 0.0;
        private double ewmaRawRate = 0.0;
        private double peakWindowDps = 0.0;
        private int totalHits = 0;

        /**
         * 1 回の打撃（または多段スライス打撃）の結果を記録し、全 DPS 指標を更新する。
         */
        public synchronized void recordStrike(long nowTick, float rawAttempted, float actualDealt, int slices) {
            float safeRaw = Float.isFinite(rawAttempted) && rawAttempted > 0.0F ? rawAttempted : 0.0F;
            float safeDealt = Float.isFinite(actualDealt) && actualDealt > 0.0F ? actualDealt : 0.0F;

            if (firstHitTick < 0L || (lastHitTick >= 0L && nowTick - lastHitTick > 200L)) {
                // 10秒以上間隔が空いた場合は新しい交戦セッションとして初期化
                firstHitTick = nowTick;
                lastHitTick = nowTick;
                cumulativeRawDamage = 0.0;
                cumulativeDealtDamage = 0.0;
                ewmaDealtRate = safeDealt;
                ewmaRawRate = safeRaw;
                windowSamples.clear();
                totalHits = 0;
            } else {
                double dtSec = Math.max(0.05, (nowTick - lastHitTick) / 20.0);
                double decay = Math.exp(-EWMA_LAMBDA * dtSec);
                double instDealtDps = safeDealt / Math.max(0.25, dtSec);
                double instRawDps = safeRaw / Math.max(0.25, dtSec);
                ewmaDealtRate = ewmaDealtRate * decay + instDealtDps * (1.0 - decay);
                ewmaRawRate = ewmaRawRate * decay + instRawDps * (1.0 - decay);
                lastHitTick = nowTick;
            }

            cumulativeRawDamage += safeRaw;
            cumulativeDealtDamage += safeDealt;
            totalHits += Math.max(1, slices);

            windowSamples.addLast(new Sample(nowTick, safeRaw, safeDealt));
            pruneWindow(nowTick);
            double winDps = slidingWindowDps(nowTick);
            if (winDps > peakWindowDps) {
                peakWindowDps = winDps;
            }
        }

        private void pruneWindow(long nowTick) {
            while (!windowSamples.isEmpty() && nowTick - windowSamples.peekFirst().tick > DPS_WINDOW_TICKS) {
                windowSamples.pollFirst();
            }
        }

        /** 交戦開始からの経過秒数（最低 0.5 秒でゼロ除算・初撃スパイクを防止） */
        public synchronized double elapsedCombatSeconds(long nowTick) {
            if (firstHitTick < 0L) return 0.5;
            long end = Math.max(lastHitTick, nowTick);
            return Math.max(0.5, (end - firstHitTick) / 20.0);
        }

        /** ① 理論無制限（投入）DPS */
        public synchronized double nominalRawDps(long nowTick) {
            return cumulativeRawDamage / elapsedCombatSeconds(nowTick);
        }

        /** ② 実効削り（累計平均）DPS */
        public synchronized double effectiveDealtDps(long nowTick) {
            return cumulativeDealtDamage / elapsedCombatSeconds(nowTick);
        }

        /** ③ 瞬間 EWMA DPS（半減期 2.0 秒の指数減衰リアルタイム DPS） */
        public synchronized double ewmaInstantDps(long nowTick) {
            if (lastHitTick < 0L) return 0.0;
            double idleSec = Math.max(0.0, (nowTick - lastHitTick) / 20.0);
            return ewmaDealtRate * Math.exp(-EWMA_LAMBDA * idleSec);
        }

        /** ④ 直近 100 Tick（5.0 秒）スライディングウィンドウ実効 DPS */
        public synchronized double slidingWindowDps(long nowTick) {
            pruneWindow(nowTick);
            if (windowSamples.isEmpty()) return 0.0;
            double sum = 0.0;
            long earliest = windowSamples.peekFirst().tick;
            for (Sample s : windowSamples) {
                sum += s.actualDealt;
            }
            double spanSec = Math.max(0.5, Math.min(DPS_WINDOW_TICKS, Math.max(lastHitTick, nowTick) - earliest) / 20.0);
            return sum / spanSec;
        }

        /** ⑤ 回復・書き戻し速度を差し引いた純実効 DPS（真の削り速度） */
        public synchronized double netBleedDps(long nowTick, float healRatePerSec, float revertRatePerSec) {
            double activeDps = Math.max(effectiveDealtDps(nowTick), Math.max(ewmaInstantDps(nowTick), slidingWindowDps(nowTick)));
            double regenLoss = Math.max(0.0, healRatePerSec) + Math.max(0.0, revertRatePerSec);
            return Math.max(0.0, activeDps - regenLoss);
        }

        /** 推定撃破時間 ETTK（秒）。削り速度が 0 以下の場合は {@link Double#POSITIVE_INFINITY} */
        public synchronized double estimatedTtkSeconds(long nowTick, float currentHp, float healRatePerSec, float revertRatePerSec) {
            if (currentHp <= 0.0F) return 0.0;
            double net = netBleedDps(nowTick, healRatePerSec, revertRatePerSec);
            if (net <= 1.0E-3) return Double.POSITIVE_INFINITY;
            return currentHp / net;
        }

        public synchronized double peakWindowDps() {
            return peakWindowDps;
        }

        public synchronized double cumulativeDealtDamage() {
            return cumulativeDealtDamage;
        }

        public synchronized int totalHits() {
            return totalHits;
        }

        public synchronized String formatSummary(long nowTick, float currentHp, float healRatePerSec, float revertRatePerSec) {
            double effDps = effectiveDealtDps(nowTick);
            double ewma = ewmaInstantDps(nowTick);
            double win = slidingWindowDps(nowTick);
            double net = netBleedDps(nowTick, healRatePerSec, revertRatePerSec);
            double ettk = estimatedTtkSeconds(nowTick, currentHp, healRatePerSec, revertRatePerSec);
            String ettkStr = Double.isInfinite(ettk) ? "∞(要昇格)" : String.format(Locale.ROOT, "%.1fs", ettk);
            return String.format(Locale.ROOT,
                    "DPS[実効=%.1f | 瞬間EWMA=%.1f | 5s窓=%.1f | 純削り=%.1f/s] 累計=%.0f(%d撃) ETTK=%s",
                    effDps, ewma, win, net, cumulativeDealtDamage, totalHits, ettkStr);
        }
    }

    // ========================================================================
    // 1. バニラ防具・防具強度・耐性・エンチャントの順方向＆閉形式厳密逆算
    // ========================================================================

    /**
     * バニラ 1.20.1 {@code CombatRules.getDamageAfterAbsorb} の順方向計算式：
     * 防具 {@code armor} と防具強度 {@code toughness} による減衰後のダメージを返す。
     */
    public static float predictPostArmorDamage(float rawDamage, double armor, double toughness) {
        if (rawDamage <= 0.0F) return 0.0F;
        if (armor <= 0.0D) return rawDamage;
        double f = 2.0D + toughness / 4.0D;
        double effectiveArmor = Math.min(20.0D, Math.max(armor / 5.0D, armor - rawDamage / f));
        return (float) (rawDamage * (1.0D - effectiveArmor / 25.0D));
    }

    /**
     * バニラ防具減衰式の閉形式・厳密逆関数 {@code f_armor^-1(desiredPostArmor, armor, toughness)}。
     * <p>
     * 防具減衰後にちょうど {@code desiredPostArmor} のダメージを通すために必要な防具前入力ダメージ {@code x} を
     * 3 区間（上限クランプ領域・2次方程式中間領域・下限貫通領域）から厳密に解く。
     * </p>
     */
    public static float solveExactInverseArmorDamage(float desiredPostArmor, double armor, double toughness) {
        if (desiredPostArmor <= 0.0F) return 0.0F;
        if (armor <= 0.0D) return desiredPostArmor;

        double y = desiredPostArmor;
        double f = 2.0D + Math.max(0.0D, toughness) / 4.0D; // f = (T + 8) / 4

        // 区間1: 高貫通領域 (armor - x / f <= armor / 5  <=>  x >= 0.8 * armor * f)
        // この領域では減衰率が一定 (1 - armor / 125)
        double minReduceFactor = Math.max(0.20D, 1.0D - Math.min(20.0D, armor / 5.0D) / 25.0D);
        double xHigh = y / minReduceFactor;
        if (armor - xHigh / f <= armor / 5.0D) {
            return (float) xHigh;
        }

        // 区間2: 上限クランプ領域 (armor - x / f >= 20  <=>  armor > 20 かつ x <= (armor - 20) * f)
        // この領域では減衰率が最大 80% (1 - 20/25 = 0.20)
        if (armor >= 20.0D) {
            double xCap = y / 0.20D;
            if (armor - xCap / f >= 20.0D) {
                return (float) xCap;
            }
        }

        // 区間3: 中間非線形領域 (armor / 5 < armor - x / f < 20)
        // y = x * (1 - (armor - x / f) / 25) = (1 / (25 * f)) * x^2 + (1 - armor / 25) * x
        // 2次方程式: a * x^2 + b * x - y = 0  (ただし a = 1 / (25 * f) > 0)
        double a = 1.0D / (25.0D * f);
        double b = 1.0D - armor / 25.0D;
        double discriminant = Math.max(0.0D, b * b + 4.0D * a * y);
        double xQuad = (-b + Math.sqrt(discriminant)) / (2.0D * a);
        return (float) Math.max(y, xQuad);
    }

    /**
     * 対象の耐性エフェクトおよび防具エンチャント（EPF）による透過率 {@code M_res * M_epf}（0.0〜1.0）を計算する。
     */
    public static float computeMagicAndProtectionTransmittance(LivingEntity target, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return 1.0F;
        }
        float transmittance = 1.0F;

        // 1. ポーション耐性 (DAMAGE_RESISTANCE)
        if (!source.is(DamageTypeTags.BYPASSES_RESISTANCE) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            MobEffectInstance res = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (res != null) {
                int amp = res.getAmplifier() + 1;
                if (amp > 0) {
                    transmittance *= Math.max(0.0F, 1.0F - amp * 0.20F);
                }
            }
        }

        // 2. エンチャント EPF (CombatRules.getDamageAfterMagicAbsorb)
        if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            try {
                int epf = EnchantmentHelper.getDamageProtection(target.getArmorSlots(), source);
                if (epf > 0) {
                    float clampedEpf = Math.min(20.0F, epf);
                    transmittance *= (1.0F - clampedEpf / 25.0F);
                }
            } catch (Throwable ignored) {}
        }

        return Math.max(0.0F, Math.min(1.0F, transmittance));
    }

    // ========================================================================
    // 2. 汎用ダメージ逆算 ＆ 最適 DPS スケジューリング計算式
    // ========================================================================

    /**
     * 対象の防御プロファイル（防具・強度・耐性・EPF・MOD軽減率 η・非線形弾性指数 α・足切り・上限・時間窓・回復速度・書き戻し）から、
     * 実効 DPS を最大化する {@link OptimalStrikePlan}（1 発あたりの最適入力ダメージ・最適スライス数・理論最大 DPS・推定撃破時間）を算出する。
     *
     * @param target         攻撃対象
     * @param source         使用予定の DamageSource
     * @param baseStageDmg   ステージや武器の基本ダメージ量
     * @param maxSlices      1 回の攻撃で許容される最大スライス数
     * @param intervalTicks  攻撃サイクル間隔（Tick）
     * @param profile        学習済みの対象ダメージプロファイル
     * @param nowTick        現在のサーバー Tick
     * @return 算出された最適打撃プラン
     */
    public static OptimalStrikePlan computeOptimalStrikePlan(
            LivingEntity target,
            DamageSource source,
            float baseStageDmg,
            int maxSlices,
            int intervalTicks,
            TargetDamageProfile profile,
            long nowTick
    ) {
        float currentHp = readEffectiveHealth(target);
        float maxHp = Math.max(1.0F, target.getMaxHealth());
        double cycleSec = Math.max(0.05D, intervalTicks / 20.0D);

        // 1. 再生速度 + 書き戻し速度の推定 (HP/s)
        float healRate = Math.max(0.0F, profile.healRatePerSec);
        float revertRate = profile.revertCount > 0
                ? Math.max(5.0F, (profile.lastDealtAmount > 0 ? profile.lastDealtAmount : baseStageDmg * 0.5F)
                        * (20.0F / Math.max(1, profile.minRevertDelayTicks)))
                : 0.0F;
        float regenCompensation = (healRate + revertRate) * (float) cycleSec * REGEN_COMPENSATION_GAMMA;

        // 2. 高 HP ボスに対する割合スケーリング上乗せ（基本ダメージが最大 HP の 2% 未満のとき、最大 HP の 5% を上乗せ）
        float hpPercentBonus = 0.0F;
        if (maxHp > 200.0F && baseStageDmg < maxHp * 0.02F && !profile.ceilingConfirmed && !profile.perHitCapConfirmed) {
            hpPercentBonus = Math.min(MAX_HP_PERCENT_CAP, maxHp * MAX_HP_PERCENT_RATIO);
        }

        // 3. このサイクルで削りたい目標実効ダメージ量 D_goal
        float desiredTotalNet = Math.max(baseStageDmg + hpPercentBonus + regenCompensation, currentHp + regenCompensation);

        // 4. 1発あたりの実効上限 d_slice_max の特定（1発上限 perHitLimit と 拒否上限 maxPassedBelowCeiling）
        double armor = source.is(DamageTypeTags.BYPASSES_ARMOR) ? 0.0D : target.getAttributeValue(Attributes.ARMOR);
        double toughness = source.is(DamageTypeTags.BYPASSES_ARMOR) ? 0.0D : target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float magicTrans = computeMagicAndProtectionTransmittance(target, source);
        float modRatio = Math.max(0.02F, profile.effectivePenetrationRatio);
        float alpha = Math.max(0.25F, Math.min(2.0F, profile.elasticityExponent));

        float maxInputAllowed = Float.MAX_VALUE;
        if (profile.ceilingConfirmed && profile.maxPassedBelowCeiling > 0.0F) {
            maxInputAllowed = Math.min(maxInputAllowed, profile.maxPassedBelowCeiling);
        }
        if (profile.windowCapConfirmed && profile.windowCap < Float.MAX_VALUE) {
            float usedInWindow = profile.dealtWithinCurrentWindow(nowTick);
            float remainingBudget = Math.max(0.0F, profile.windowCap - usedInWindow);
            if (remainingBudget > 0.0F) {
                maxInputAllowed = Math.min(maxInputAllowed, solveInverseInputForNet(
                        remainingBudget, armor, toughness, magicTrans, modRatio, alpha));
            }
        }

        // 5. スライス分割数 N* と 1 スライスあたりの目標実効ダメージ d_slice* の最適化
        int allowedSlices = Math.max(1, maxSlices);
        float singleSliceNetCap = Float.MAX_VALUE;
        if (profile.perHitCapConfirmed && profile.perHitLimit > 0.0F && profile.perHitLimit < Float.MAX_VALUE) {
            singleSliceNetCap = Math.min(singleSliceNetCap, profile.perHitLimit);
        }
        if (maxInputAllowed < Float.MAX_VALUE) {
            float netAtCeiling = predictExpectedNetDamage(maxInputAllowed, armor, toughness, magicTrans, modRatio, alpha);
            if (netAtCeiling > 0.0F) {
                singleSliceNetCap = Math.min(singleSliceNetCap, netAtCeiling);
            }
        }

        int optimalSlices;
        float targetNetPerSlice;
        if (singleSliceNetCap < Float.MAX_VALUE && singleSliceNetCap > 0.0F) {
            // 1発上限・拒否上限がある場合：対象 HP（または目標ダメージ）を 1 発上限で割った最小必要スライス数を算出
            int neededForKill = (int) Math.ceil((currentHp + regenCompensation + 1.0F) / singleSliceNetCap);
            optimalSlices = Math.max(1, Math.min(allowedSlices, neededForKill));
            targetNetPerSlice = singleSliceNetCap;
        } else if (alpha < 0.85F && allowedSlices > 1) {
            // 非線形減衰（ソフトキャップ α < 0.85：大ダメージほど減衰率が上がるボス）の場合：複数スライスに分散した方が総ダメージが最大化
            optimalSlices = Math.min(allowedSlices, 4);
            targetNetPerSlice = desiredTotalNet / optimalSlices;
        } else {
            // 通常または防具貫通型（α >= 0.85）：単発に集中して防具強度（Toughness）を貫通
            optimalSlices = 1;
            targetNetPerSlice = Math.max(baseStageDmg + hpPercentBonus + regenCompensation, baseStageDmg);
        }

        // 6. 目標実効ダメージ targetNetPerSlice を通すための厳密逆算入力値 x*
        float optimalInput = solveInverseInputForNet(
                targetNetPerSlice, armor, toughness, magicTrans, modRatio, alpha);

        // 7. 足切り（Floor）および拒否上限（Ceiling）への射影
        if (profile.maxFailedFloor > 0.0F && optimalInput <= profile.maxFailedFloor * 1.05F) {
            if (profile.minPassedAboveFloor < Float.MAX_VALUE) {
                optimalInput = profile.floorConfirmed
                        ? profile.minPassedAboveFloor * 1.05F
                        : (profile.maxFailedFloor + profile.minPassedAboveFloor) * 0.5F;
            } else {
                optimalInput = Math.max(optimalInput * 2.5F, profile.maxFailedFloor * 2.0F);
            }
        }
        if (maxInputAllowed < Float.MAX_VALUE && optimalInput > maxInputAllowed) {
            optimalInput = maxInputAllowed;
        }
        optimalInput = Math.max(0.5F, Math.min(1.0E12F, optimalInput));

        // 8. 期待実効ダメージ・理論最大 DPS・推定撃破時間 ETTK の算出
        float expectedNetPerSlice = predictExpectedNetDamage(optimalInput, armor, toughness, magicTrans, modRatio, alpha);
        if (profile.perHitCapConfirmed && profile.perHitLimit < Float.MAX_VALUE) {
            expectedNetPerSlice = Math.min(expectedNetPerSlice, profile.perHitLimit);
        }
        float expectedCycleNet = expectedNetPerSlice * optimalSlices;
        double cycleGrossDps = expectedCycleNet / cycleSec;
        if (profile.windowCapConfirmed && profile.windowCap < Float.MAX_VALUE) {
            double windowMaxDps = profile.windowCap / Math.max(0.05D, profile.windowTicks / 20.0D);
            cycleGrossDps = Math.min(cycleGrossDps, windowMaxDps);
        }
        double netTheoreticalDps = Math.max(0.0D, cycleGrossDps - healRate - revertRate);
        double ettk = netTheoreticalDps > 1.0E-3D ? currentHp / netTheoreticalDps : Double.POSITIVE_INFINITY;

        // 純実効 DPS が 0 以下、または通常ダメージでの推定撃破時間が 120 秒を超える場合は上位ティア（隔絶/即死）への昇格を推奨
        boolean escalate = netTheoreticalDps <= 1.0E-3D || ettk > 120.0D;

        String summary = String.format(Locale.ROOT,
                "x*=%.1f/slice × %d (E[d]=%.1f, A=%.0f/T=%.0f, M_magic=%.2f, η=%.2f, α=%.2f) -> 理論純DPS=%.1f/s, ETTK=%s%s",
                optimalInput, optimalSlices, expectedCycleNet, armor, toughness, magicTrans, modRatio, alpha,
                netTheoreticalDps,
                Double.isInfinite(ettk) ? "∞" : String.format(Locale.ROOT, "%.1fs", ettk),
                escalate ? " [ESCALATE]" : "");

        return new OptimalStrikePlan(
                optimalInput,
                expectedNetPerSlice,
                optimalSlices,
                intervalTicks,
                netTheoreticalDps,
                ettk,
                escalate,
                summary
        );
    }

    /**
     * 目標実効ダメージ {@code targetNet} を通すための厳密逆算入力ダメージ {@code x*} を求める。
     */
    public static float solveInverseInputForNet(
            float targetNet,
            double armor,
            double toughness,
            float magicTransmittance,
            float modPenetrationRatio,
            float elasticityAlpha
    ) {
        if (targetNet <= 0.0F) return 0.0F;
        float combinedTrans = Math.max(0.01F, magicTransmittance * Math.max(0.01F, modPenetrationRatio));
        float preMagicAndMod = targetNet / combinedTrans;

        // 非線形弾性指数 α の逆変換 (d ≈ η * x^α  =>  x ≈ (d / η)^(1/α))
        if (Math.abs(elasticityAlpha - 1.0F) > 0.05F && preMagicAndMod > 1.0F) {
            float invAlpha = Math.max(0.5F, Math.min(2.5F, 1.0F / elasticityAlpha));
            preMagicAndMod = (float) Math.pow(preMagicAndMod, invAlpha);
        }

        // 防具・防具強度 2 次方程式の厳密逆関数
        return solveExactInverseArmorDamage(preMagicAndMod, armor, toughness);
    }

    /**
     * 入力ダメージ {@code rawInput} に対して、防具・耐性・EPF・MOD 軽減率・非線形弾性指数を適用した期待実効ダメージを予測する。
     */
    public static float predictExpectedNetDamage(
            float rawInput,
            double armor,
            double toughness,
            float magicTransmittance,
            float modPenetrationRatio,
            float elasticityAlpha
    ) {
        if (rawInput <= 0.0F) return 0.0F;
        float postArmor = predictPostArmorDamage(rawInput, armor, toughness);
        float scaled = Math.abs(elasticityAlpha - 1.0F) > 0.05F && postArmor > 1.0F
                ? (float) Math.pow(postArmor, elasticityAlpha)
                : postArmor;
        return scaled * Math.max(0.0F, magicTransmittance) * Math.max(0.01F, modPenetrationRatio);
    }

    private static float readEffectiveHealth(LivingEntity entity) {
        float direct = DirectRead.health(entity);
        float vanilla = entity.getHealth();
        if (!Float.isFinite(direct)) return Float.isFinite(vanilla) ? vanilla : 0.0F;
        if (!Float.isFinite(vanilla)) return direct;
        return Math.min(direct, vanilla);
    }
}
