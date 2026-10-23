package com.automatavalkyries.entity;

import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * エレノアの攻撃演出（2026-10-23 ユーザー指定「専用のパーティクルを作る」）。技は「前兆 → 発動 → 着弾」の 3 段で見せる。
 * 粒子は白い絵に色を掛ける（{@code tools/gen_elenor_particles.py} の絵）: リボン・花びら・花・レースの輪・光の筋・緞帳。
 * ティアが上がるほど、色は桃色から赤・黒へ寄り、飾り（星・虚空の欠片・緞帳）が増える。
 */
public final class ElenorFx {

    public static final int PINK = 0xFF6EC7, HOT = 0xFF2E9A, LIGHT = 0xFFD0EC, DARK = 0x3A1238, VIOLET = 0xB65CFF, GOLD = 0xFFD27A, CRIMSON = 0xC0102C;

    private ElenorFx() {}

    private static FxOptions o(net.minecraftforge.registries.RegistryObject<FxOptions.Type> type, int color, float scale, int life) {
        return Fx.opt(type.get(), color, scale, life);
    }

    /** ティア 0〜7 の主な色（桃 → 紅 → 赤黒） */
    public static int tierColor(int tier) {
        return switch (Math.max(0, Math.min(7, tier))) {
            case 0, 1 -> PINK;
            case 2, 3 -> HOT;
            case 4 -> 0xE0204A;
            case 5 -> CRIMSON;
            case 6 -> 0x8A0A2A;
            default -> 0x5A0020;
        };
    }

    // ---- 部品 ----

    /** 床の光の輪（前兆）: 花びらの粒を輪に並べる */
    public static void floorRing(ServerLevel level, Vec3 center, double radius, int points, int color) {
        FxOptions dot = o(ModParticles.GLOW, color, 0.18F, 8);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            Fx.one(level, dot, new Vec3(center.x + Math.cos(a) * radius, center.y + 0.08, center.z + Math.sin(a) * radius), Vec3.ZERO);
        }
    }

    /** レースの輪が地面と平行に広がる */
    public static void lace(ServerLevel level, Vec3 at, int color, float radius, int life) {
        Fx.one(level, o(ModParticles.LACE, color, radius, life), at.add(0, 0.06, 0), Vec3.ZERO);
    }

    /** リボンの渦（2 本: 桃と暗い紫） */
    public static void ribbonSwirl(ServerLevel level, Vec3 base, double radius, double height, double angle) {
        for (int i = 0; i < 6; i++) {
            double t = i / 6.0;
            for (int strand = 0; strand < 2; strand++) {
                double a = angle + t * Math.PI * 2 + strand * Math.PI;
                Vec3 at = new Vec3(base.x + Math.cos(a) * radius, base.y + t * height, base.z + Math.sin(a) * radius);
                Vec3 v = new Vec3(-Math.sin(a), 0.2, Math.cos(a)).scale(0.04);
                Fx.one(level, o(ModParticles.RIBBON, strand == 0 ? PINK : DARK, 0.28F, 24), at, v);
            }
        }
    }

    /** 花びらが舞う（spread の範囲から、少し上へ） */
    public static void petals(ServerLevel level, Vec3 at, int count, double spread, int color) {
        Fx.spray(level, o(ModParticles.PETAL, color, 0.16F, 40), at, count, spread, 0.04);
    }

    /** 花びらが中心から散る */
    public static void petalBurst(ServerLevel level, Vec3 at, int count, double speed, int color) {
        Fx.burst(level, o(ModParticles.PETAL, color, 0.18F, 36), at, count, speed);
    }

    /** from から to へ流れる光の筋（向きは速さで見せる） */
    public static void streakLine(ServerLevel level, Vec3 from, Vec3 to, int count, int color) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0e-4) return;
        Vec3 n = dir.scale(1.0 / len);
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 at = from.add(dir.scale(r.nextDouble())).add((r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4);
            Fx.one(level, o(ModParticles.STREAK, color, 0.5F, 6), at, n.scale(0.6));
        }
    }

    /** 着弾: 花が開き、花びらが散り、ティアが高いほど星・虚空の欠片・緞帳が加わる */
    public static void impact(ServerLevel level, Vec3 at, int tier) {
        int color = tierColor(tier);
        Fx.one(level, o(ModParticles.BLOOM, color, 0.6F + 0.05F * tier, 12), at, Vec3.ZERO);
        petalBurst(level, at, 6 + tier, 0.25, tier >= 5 ? DARK : LIGHT);
        if (tier >= 2) Fx.spray(level, o(ModParticles.SPARK, color, 0.35F, 8), at, 3, 0.3, 0.05);
        if (tier >= 4) Fx.star(level, at, color, 0.9F, 10, Vec3.ZERO);
        if (tier >= 5) Fx.burst(level, o(ModParticles.VOID_SHARD, DARK, 0.3F, 16), at, 5, 0.2);
        if (tier >= 7) Fx.one(level, o(ModParticles.CURTAIN, DARK, 1.2F, 24), at.add(0, 1.0, 0), new Vec3(0, -0.04, 0));
    }

    // ---- 結んでいないリボン（2026-10-23 ユーザー指定「結んでいないリボンを使って攻撃する」） ----

    /** 解けたリボンの帯: from から to へ、桃と暗い紫が交互の帯を並べる（飛ぶリボンの軌跡） */
    public static void ribbonStrand(ServerLevel level, Vec3 from, Vec3 to, int age) {
        int color = (age / 2) % 2 == 0 ? PINK : DARK;
        Fx.one(level, o(ModParticles.RIBBON, color, 0.34F, 8), from.add(to).scale(0.5), Vec3.ZERO);
        Fx.one(level, o(ModParticles.RIBBON, color == PINK ? LIGHT : DARK, 0.3F, 8), to, Vec3.ZERO);
    }

    /** リボンの鞭: center を中心に、向き dir の前の半円を、progress（0〜1）のところまで払う */
    public static void ribbonArc(ServerLevel level, Vec3 center, Vec3 dir, double radius, double progress) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0e-6) return;
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            double u = i / (double) steps;
            if (u > progress) break;
            double a = Math.PI * (u - 0.5);
            Vec3 at = center.add(flat.scale(Math.cos(a) * radius)).add(side.scale(Math.sin(a) * radius)).add(0, 1.0 + 0.3 * Math.sin(u * Math.PI), 0);
            Fx.one(level, o(ModParticles.RIBBON, i % 2 == 0 ? PINK : DARK, 0.36F, 8), at, Vec3.ZERO);
        }
    }

    /**
     * 近接の鞭: 解けたリボンが、片側の上から反対側の下へ、前の半円を斜めに払う（side = 1 で右から、-1 で左から）。払った跡に光の筋
     */
    public static void ribbonWhip(ServerLevel level, Vec3 center, Vec3 dir, double radius, int side) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0e-6) return;
        flat = flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x).scale(side);
        int steps = 12;
        Vec3 previous = null;
        for (int i = 0; i <= steps; i++) {
            double u = i / (double) steps;
            double a = Math.PI * (u - 0.5);
            Vec3 at = center.add(flat.scale(Math.cos(a) * radius)).add(right.scale(Math.sin(a) * radius)).add(0, 1.9 - 1.2 * u, 0);
            Fx.one(level, o(ModParticles.RIBBON, i % 2 == 0 ? PINK : DARK, 0.4F, 7), at, Vec3.ZERO);
            if (previous != null && i % 3 == 0) Fx.one(level, o(ModParticles.STREAK, LIGHT, 0.6F, 5), at, at.subtract(previous).scale(0.5));
            previous = at;
        }
    }

    /** 解けたリボンが中心から外へ放たれる（count 本、高さを変えて） */
    public static void ribbonBurst(ServerLevel level, Vec3 at, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            for (int k = 0; k < 3; k++) {
                Vec3 v = new Vec3(Math.cos(a + k * 0.12), 0.05 * (k - 1), Math.sin(a + k * 0.12)).scale(speed * (0.7 + 0.15 * k));
                Fx.one(level, o(ModParticles.RIBBON, (i + k) % 2 == 0 ? PINK : DARK, 0.4F, 16), at.add(0, 0.6 + 0.4 * k, 0), v);
            }
        }
    }

    /** 光弾の軌跡（1 Tick ぶん） */
    public static void orbTrail(ServerLevel level, Vec3 at, int age) {
        Fx.one(level, o(ModParticles.GLOW, PINK, 0.3F, 6), at, Vec3.ZERO);
        if (age % 4 == 0) Fx.one(level, o(ModParticles.PETAL, LIGHT, 0.12F, 20), at, Vec3.ZERO);
    }

    /** 残像（ドレスの形: 光の胴とリボン、裾の輪） */
    public static void mirage(ServerLevel level, Vec3 at, int tick) {
        Fx.spray(level, o(ModParticles.GLOW, VIOLET, 0.35F, 6), at.add(0, 1.1, 0), 2, 0.12, 0.35, 0.12, 0.0);
        if (tick % 4 == 0) Fx.one(level, o(ModParticles.RIBBON, PINK, 0.3F, 16), at.add(0, 1.4, 0), new Vec3(0, 0.02, 0));
        floorRing(level, at, 0.5, 8, PINK);
    }

    /** 魔法陣（床。半径 radius、life Tick 回り続ける） */
    public static void sigil(ServerLevel level, Vec3 at, float radius, int life) {
        Fx.mandala(level, at.add(0, 0.05, 0), HOT, radius, life, false, false);
        petals(level, at.add(0, 0.3, 0), 6, radius * 0.5, PINK);
    }

    // ---- 受けたときの反応 ----

    /** エレノアが殴られた: 花びらと火花が散る */
    public static void hitReaction(ServerLevel level, Vec3 at) {
        petalBurst(level, at, 5, 0.2, PINK);
        Fx.spray(level, o(ModParticles.SPARK, LIGHT, 0.3F, 6), at, 2, 0.25, 0.05);
    }

    /** 凶悪技術を受けた（凶悪度 III 以上）: 虚空の欠片と赤い紋章が周りに舞う。凶悪なほど多い */
    public static void viciousReaction(ServerLevel level, Vec3 at, int severity) {
        Fx.burst(level, o(ModParticles.VOID_SHARD, DARK, 0.35F, 20), at, 4 + severity * 2, 0.25);
        Fx.spray(level, o(ModParticles.GLYPH, CRIMSON, 0.4F, 30), at, 2 + severity, 1.0, 0.02);
        if (severity >= 4) Fx.mandala(level, at.add(0, -0.9, 0), CRIMSON, 2.0F, 20, false, true);
    }

    /** 相手のティアが上がった: 足もとにティアの色の星の陣、紋章が浮かぶ */
    public static void tierUp(ServerLevel level, Vec3 feet, int tier) {
        int color = tierColor(tier);
        Fx.mandala(level, feet.add(0, 0.05, 0), color, 1.4F, 24, false, tier % 2 == 1);
        Fx.spray(level, o(ModParticles.GLYPH, color, 0.45F, 30), feet.add(0, 1.2, 0), 3, 0.6, 0.02);
        Fx.star(level, feet.add(0, 2.2, 0), color, 1.0F, 12, Vec3.ZERO);
    }

    /** 緞帳が輪になって降りてくる（幕引きの前兆）。半径 radius、count 枚 */
    public static void curtainCircle(ServerLevel level, Vec3 center, double radius, int count, double angle) {
        for (int i = 0; i < count; i++) {
            double a = angle + Math.PI * 2 * i / count;
            Vec3 at = new Vec3(center.x + Math.cos(a) * radius, center.y + 4.5, center.z + Math.sin(a) * radius);
            Fx.one(level, o(ModParticles.CURTAIN, i % 2 == 0 ? DARK : CRIMSON, 2.2F, 30), at, new Vec3(0, -0.12, 0));
        }
    }
}
