package com.automatavalkyries.weapon;

import com.automatavalkyries.particle.DamageNumberOptions;
import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 自作エフェクトの粒子をサーバーから出す道具（見ている全員に届く）。色は 0xRRGGBB、大きさはブロック、寿命は Tick。
 * 「散らす」は中心から外へ、「集める」は外から中心へ、向きのある速さで 1 粒ずつ出す。
 */
public final class Fx {

    private Fx() {}

    /** 自作エフェクトの粒子が届く範囲（ブロック）。遠くの砲撃（ジェネシス・ヘイローは 256 ブロック先）も見えるよう、遠くまで送る */
    public static final double RANGE = 256.0;

    /**
     * 粒子を送る。バニラの既定では 32 ブロックより遠いプレイヤーには届かず、クライアントでも 32 ブロックより遠い粒子は出ないので、
     * RANGE 以内のプレイヤーへ「遠くでも出す」指定で 1 人ずつ送る（2026-10-04 修正: ジェネシス・ヘイローのエフェクトが見えなかった）
     */
    static <T extends net.minecraft.core.particles.ParticleOptions> void send(ServerLevel level, T options, double x, double y, double z,
                                                                             int count, double dx, double dy, double dz, double speed) {
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= RANGE * RANGE) level.sendParticles(player, options, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static FxOptions opt(ParticleType<FxOptions> type, int color, float scale, int life) {
        return new FxOptions(type, color, scale, life, 0F);
    }

    /** 範囲 spread にばらまく（速さ speed はランダムな向き） */
    public static void spray(ServerLevel level, FxOptions o, Vec3 at, int count, double spread, double speed) {
        send(level, o, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void spray(ServerLevel level, FxOptions o, Vec3 at, int count, double sx, double sy, double sz, double speed) {
        send(level, o, at.x, at.y, at.z, count, sx, sy, sz, speed);
    }

    /** 1 粒を、決まった速さで */
    public static void one(ServerLevel level, FxOptions o, Vec3 at, Vec3 velocity) {
        send(level, o, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
    }

    /** 中心から外へ、球の向きに散らす */
    public static void burst(ServerLevel level, FxOptions o, Vec3 at, int count, double speed) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            one(level, o, at, dir.scale(speed * (0.5 + r.nextDouble() * 0.7)));
        }
    }

    /** 外（半径 radius）から中心へ集める */
    public static void gather(ServerLevel level, FxOptions o, Vec3 at, int count, double radius, int ticks) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            Vec3 from = at.add(dir.scale(radius * (0.6 + r.nextDouble() * 0.4)));
            one(level, o, from, at.subtract(from).scale(1.0 / ticks));
        }
    }

    /** from から to へ、step ごとに並べる */
    public static void line(ServerLevel level, FxOptions o, Vec3 from, Vec3 to, double step) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1.0E-4) return;
        Vec3 n = d.scale(1 / length);
        for (double t = 0; t <= length; t += step) {
            Vec3 p = from.add(n.scale(t));
            send(level, o, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** 地面と平行に広がる衝撃波の輪（radius まで、life Tick） */
    public static void shockwave(ServerLevel level, Vec3 at, int color, float radius, int life) {
        send(level, opt(ModParticles.SHOCKWAVE.get(), color, radius, life), at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    /** 三日月の斬撃（roll = 向き、ラジアン） */
    public static void slash(ServerLevel level, Vec3 at, int color, float scale, int life, float roll) {
        send(level, new FxOptions(ModParticles.SLASH.get(), color, scale, life, roll), at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    /** チェーンソーの回る光の刃（大きさ scale、life Tick） */
    public static void saw(ServerLevel level, Vec3 at, int color, float scale, int life) {
        send(level, opt(ModParticles.SAW.get(), color, scale, life), at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    /** 瞬く星（大きさ scale、life Tick） */
    public static void star(ServerLevel level, Vec3 at, int color, float scale, int life, Vec3 velocity) {
        one(level, opt(ModParticles.STAR.get(), color, scale, life), at, velocity);
    }

    /** 星の陣（半径 radius、life Tick。facing = こちらを向く、ccw = 左回り） */
    public static void mandala(ServerLevel level, Vec3 at, int color, float radius, int life, boolean facing, boolean ccw) {
        send(level, new FxOptions(ModParticles.MANDALA.get(), color, radius, life, (facing ? 2 : 0) + (ccw ? 1 : 0)), at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    /** 光線の輪（axis に垂直な面で半径 radius まで広がる、life Tick） */
    public static void beamRing(ServerLevel level, Vec3 at, Vec3 axis, int color, float radius, int life) {
        send(level, opt(ModParticles.BEAM_RING.get(), color, radius, life), at.x, at.y, at.z, 0, axis.x, axis.y, axis.z, 1.0);
    }

    /** 浮かび上がるダメージの数字 */
    public static void number(ServerLevel level, Vec3 at, long value, int color) {
        send(level, new DamageNumberOptions(value, color), at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    // よく使う色
    public static final int WHITE = 0xFFFFFF, CYAN = 0x7FE4FF, ICE = 0xB8F4FF, GOLD = 0xFFD166, PALE_GOLD = 0xFFF0B0,
            VIOLET = 0xB070FF, LAVENDER = 0xC8B8FF, GREEN = 0x6FD1A0, ORANGE = 0xFF8C1A, EMBER = 0xFFB050, PINK = 0xFF8AD8;
}
