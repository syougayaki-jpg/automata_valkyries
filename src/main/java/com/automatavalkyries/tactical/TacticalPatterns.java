package com.automatavalkyries.tactical;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 戦術級コードの動きの決まり（サーバーのダメージとクライアントの演出で共通。同じ種（seed）から同じ動きになる）。
 * 2026-10-07 ユーザー指定「他のコードもさらに派手に」:
 * <ul>
 *   <li>CARPET: 4 機の無人爆撃機が異空間（裂け目）から現れて上空を横切り、大量の焼夷弾を落とす。落ちた所に独自の炎が残る</li>
 *   <li>IRIS: 範囲内にカラフルな花びらが舞い落ちる</li>
 *   <li>ABYSS: ひび割れた球体から光が漏れ出しながら吸い込み、吸い込む範囲が広がっていく。最後に中心へ 20,000</li>
 * </ul>
 * 2026-10-29 ユーザー指定「最近の MOD のインフレを見るに、もっと派手に」（質問で: 威力・範囲と演出の両方）で範囲をおよそ倍・威力をおよそ 10 倍【仮】
 */
public final class TacticalPatterns {

    private TacticalPatterns() {}

    // ------------------------------------------------------------------ CARPET

    /** 裂け目が開くまで・爆撃機が横切る長さ・焼夷弾が落ちる時間・炎が残る長さ（Tick） */
    public static final int CARPET_PORTAL = 20, CARPET_PASS = 140, CARPET_FALL = 15, CARPET_FIRE = 200, CARPET_BOMBERS = 8;
    /** 爆撃する範囲（半径）と、爆撃機の高さ */
    public static final double CARPET_R = 64.0, CARPET_ALT = 60.0;
    /** 着弾の爆風と、残る炎の半径（範囲内の敵に当たるよう大きく。2026-10-07 ユーザー指定「炎をもっと大きく」） */
    public static final double CARPET_BLAST_R = 8.0, CARPET_FIRE_R = 7.0;

    public static int carpetDuration() {
        return CARPET_PORTAL + CARPET_PASS + CARPET_FALL + CARPET_FIRE + 20;
    }

    /** 爆撃機の進む向き（水平の単位ベクトル） */
    public static Vec3 carpetDir(long seed) {
        double a = new Random(seed).nextDouble() * Math.PI * 2;
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    /** 爆撃機 i の、t のときの位置（中心からの相対） */
    public static Vec3 bomber(int i, float t, long seed) {
        Vec3 dir = carpetDir(seed), perp = new Vec3(-dir.z, 0, dir.x);
        double along = -CARPET_R - 30 + (t - CARPET_PORTAL) * ((2 * CARPET_R + 60) / CARPET_PASS);
        double lateral = (i - (CARPET_BOMBERS - 1) / 2.0) * 14.0;
        return dir.scale(along).add(perp.scale(lateral)).add(0, CARPET_ALT + (i % 2) * 4, 0);
    }

    /** 焼夷弾 1 発（落とした所の中心からの x, z・落とした時・落ちた時） */
    public record Bomb(double x, double z, int dropTick, int landTick, Vec3 dropPos) {}

    /** 落とす焼夷弾みな（爆撃機が範囲の上にいる間、2 Tick ごとに 1 発。少しばらける） */
    public static List<Bomb> carpetBombs(long seed) {
        List<Bomb> out = new ArrayList<>();
        for (int i = 0; i < CARPET_BOMBERS; i++) {
            for (int t = CARPET_PORTAL; t <= CARPET_PORTAL + CARPET_PASS; t += 3) { // 8 機になったので 3 Tick ごと（2026-10-29）
                Vec3 p = bomber(i, t, seed);
                if (p.x * p.x + p.z * p.z > CARPET_R * CARPET_R) continue;
                Random r = new Random(seed ^ (i * 7919L + t * 104729L));
                double jx = (r.nextDouble() - 0.5) * 18, jz = (r.nextDouble() - 0.5) * 18; // 範囲を隙間なく焼くよう広くばらける（2026-10-07）
                out.add(new Bomb(p.x + jx, p.z + jz, t, t + CARPET_FALL, p));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ ABYSS

    public static final int ABYSS_TIME = 200;
    public static final double ABYSS_START_R = 48.0, ABYSS_END_R = 96.0, ABYSS_CORE_R = 8.0;
    public static final float ABYSS_CORE_DAMAGE = 20000.0F, ABYSS_RATIO = 0.5F;

    /** 吸い込む範囲（時間とともに広がる） */
    public static double abyssRadius(float t) {
        return ABYSS_START_R + (ABYSS_END_R - ABYSS_START_R) * Math.min(1.0, Math.max(0.0, t / ABYSS_TIME));
    }

    // ------------------------------------------------------------------ COMET（仮想彗星）

    /** 落ちてくる間・落ちた後の残光（Tick）、吹き飛ばす半径、中心と縁のダメージ */
    public static final int COMET_FALL = 80, COMET_AFTER = 200;
    /** 2026-10-07 ユーザー指定で範囲を倍（30 → 60）、威力を 5 倍（中心 800 → 4,000、縁 200 → 1,000）に */
    public static final double COMET_R = 120.0;
    public static final float COMET_CORE = 40000.0F, COMET_EDGE = 10000.0F;

    /** 彗星の t（0〜COMET_FALL）のときの位置（狙った所からの相対）。斜め上の遠くから、だんだん速く */
    public static Vec3 cometPos(float t, long seed) {
        double a = new Random(seed).nextDouble() * Math.PI * 2;
        double k = Math.min(1.0, Math.max(0.0, t / COMET_FALL));
        double f = 1 - Math.pow(k, 1.7);
        return new Vec3(Math.cos(a) * 160 * f, 220 * f, Math.sin(a) * 160 * f);
    }

    // ------------------------------------------------------------------ IRIS

    public static final int IRIS_TIME = 900;
    public static final double IRIS_R = 32.0;
}
