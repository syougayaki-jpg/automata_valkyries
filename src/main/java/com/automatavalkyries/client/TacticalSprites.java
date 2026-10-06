package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.network.TacticalFxPacket;
import com.automatavalkyries.tactical.TacticalPatterns;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * 戦術級コードの演出の「光・炎・煙・花びら」（素材を貼った面。2026-10-07 ユーザー指定「もっと解像度高く、リアルに」）。
 * 素材は tools/gen_tactical_fx.py（textures/fx）。光と炎は足し算（{@link RenderType#eyes}）、煙と花びらは半透明（{@link RenderType#entityTranslucent}）。
 * 光・煙はカメラを向く板、炎は縦に立ったままカメラを向く板（{@link TacticalFx} の形の上に重ねる）
 */
final class TacticalSprites {

    private TacticalSprites() {}

    private static ResourceLocation fx(String name) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/fx/" + name + ".png");
    }

    static final ResourceLocation GLOW = fx("glow"), FLARE = fx("flare"), FLAME = fx("flame"), SMOKE = fx("smoke"), PETAL = fx("petal"), STREAK = fx("streak");

    /** 描く場所（カメラの左・上の向きと、各素材の入れ物） */
    static final class Ctx {
        final Matrix4f m;
        final Vector3f left, up;
        final VertexConsumer glow, flare, flame, smoke, petal, streak;

        Ctx(Matrix4f m, Vector3f left, Vector3f up, MultiBufferSource buffers) {
            this.m = m;
            this.left = left;
            this.up = up;
            glow = buffers.getBuffer(RenderType.eyes(GLOW));
            flare = buffers.getBuffer(RenderType.eyes(FLARE));
            flame = buffers.getBuffer(RenderType.eyes(FLAME));
            streak = buffers.getBuffer(RenderType.eyes(STREAK));
            smoke = buffers.getBuffer(RenderType.entityTranslucent(SMOKE));
            petal = buffers.getBuffer(RenderType.entityTranslucent(PETAL));
        }
    }

    static RenderType[] types() {
        return new RenderType[]{RenderType.eyes(GLOW), RenderType.eyes(FLARE), RenderType.eyes(FLAME), RenderType.eyes(STREAK),
                RenderType.entityTranslucent(SMOKE), RenderType.entityTranslucent(PETAL)};
    }

    private static void v(VertexConsumer c, Matrix4f m, float x, float y, float z, int r, int g, int b, int a, float u, float vv) {
        c.vertex(m, x, y, z).color(r, g, b, a).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
    }

    /** カメラを向く板（中心 x,y,z、半分の大きさ s、回転 rot） */
    static void sprite(Ctx k, VertexConsumer c, double x, double y, double z, double s, float rot, int r, int g, int b, int a, float u0, float v0, float u1, float v1) {
        if (a <= 0) return;
        float cs = Mth.cos(rot) * (float) s, sn = Mth.sin(rot) * (float) s;
        // 左・上を rot だけ回した向き
        float lx = k.left.x() * cs + k.up.x() * sn, ly = k.left.y() * cs + k.up.y() * sn, lz = k.left.z() * cs + k.up.z() * sn;
        float ux = k.up.x() * cs - k.left.x() * sn, uy = k.up.y() * cs - k.left.y() * sn, uz = k.up.z() * cs - k.left.z() * sn;
        float px = (float) x, py = (float) y, pz = (float) z;
        v(c, k.m, px + lx - ux, py + ly - uy, pz + lz - uz, r, g, b, a, u0, v1);
        v(c, k.m, px - lx - ux, py - ly - uy, pz - lz - uz, r, g, b, a, u1, v1);
        v(c, k.m, px - lx + ux, py - ly + uy, pz - lz + uz, r, g, b, a, u1, v0);
        v(c, k.m, px + lx + ux, py + ly + uy, pz + lz + uz, r, g, b, a, u0, v0);
    }

    static void glow(Ctx k, double x, double y, double z, double s, int r, int g, int b, int a) {
        sprite(k, k.glow, x, y, z, s, 0, r, g, b, a, 0, 0, 1, 1);
    }

    /** 縦に立ったままカメラを向く板（炎）。下の端が y */
    static void upright(Ctx k, VertexConsumer c, double x, double y, double z, double w, double h, int r, int g, int b, int a, float u0, float v0, float u1, float v1) {
        if (a <= 0) return;
        float lx = k.left.x(), lz = k.left.z();
        float len = Mth.sqrt(lx * lx + lz * lz);
        if (len < 1.0e-4F) {
            lx = 1;
            lz = 0;
            len = 1;
        }
        lx = lx / len * (float) w;
        lz = lz / len * (float) w;
        float px = (float) x, py = (float) y, pz = (float) z, top = (float) (y + h);
        v(c, k.m, px + lx, py, pz + lz, r, g, b, a, u0, v1);
        v(c, k.m, px - lx, py, pz - lz, r, g, b, a, u1, v1);
        v(c, k.m, px - lx, top, pz - lz, r, g, b, a, u1, v0);
        v(c, k.m, px + lx, top, pz + lz, r, g, b, a, u0, v0);
    }

    // ------------------------------------------------------------------ GUNGNIR

    static void gungnir(Ctx k, float radius, float t, float fade) {
        float descent = TacticalFxPacket.GUNGNIR_DESCENT;
        if (t < descent) {
            float kk = t / descent;
            double head = 140 * (1 - kk * kk);
            for (int arm = 0; arm < 8; arm++) {
                double base = arm * Math.PI / 4;
                for (int s = 0; s <= 40; s += 2) {
                    double u = s / 40.0, y = head + u * 50.0;
                    double r = Math.max(3.0, radius * Math.min(1.0, y / 140.0));
                    double a = base + u * Math.PI * 3 + t * 0.25;
                    int al = (int) (200 * (1 - u));
                    glow(k, Math.cos(a) * r, y, Math.sin(a) * r, 2.6 - u * 1.5, 255, 40, 50, al);
                    if (s == 0) sprite(k, k.flare, Math.cos(a) * r, y, Math.sin(a) * r, 5.0, t * 0.1F, 255, 230, 230, 255, 0, 0, 1, 1);
                }
            }
            return;
        }
        float st = t - descent;
        int al = (int) (255 * fade);
        // 光の柱の芯（縦に重ねた光）と地面の大きな光
        for (int y = 0; y < 140; y += 5) glow(k, 0, y, 0, 7.5 + 1.5 * Mth.sin(st * 0.3F + y), 255, 60, 70, al / 2);
        for (int y = 0; y < 140; y += 9) glow(k, 0, y, 0, 3.0, 255, 235, 235, al);
        glow(k, 0, 0.5, 0, radius * 0.9, 255, 20, 30, al / 5);
        sprite(k, k.flare, 0, 2, 0, 16 + 4 * Mth.sin(st * 0.5F), st * 0.02F, 255, 220, 220, al, 0, 0, 1, 1);
        // えぐる腕の光と、先の大きな光・土煙
        for (int arm = 0; arm < TacticalFxPacket.GUNGNIR_ARMS; arm++) {
            double[] g = TacticalFxPacket.gungnirArm(arm, st, radius);
            double baseA = Math.atan2(g[1], g[0]), gr = Math.hypot(g[0], g[1]);
            for (int s = 0; s <= 40; s += 2) {
                double u = s / 40.0, y = 140 * (1 - u), r = radius + (gr - radius) * u, ang = baseA - (1 - u) * Math.PI * 1.5;
                glow(k, Math.cos(ang) * r, y, Math.sin(ang) * r, 3.2, 255, 35, 45, (int) (al * (0.3 + 0.5 * u)));
            }
            glow(k, g[0], 1.0, g[1], 9 + 2 * Mth.sin(st * 0.7F + arm), 255, 70, 40, al);
            sprite(k, k.flare, g[0], 1.5, g[1], 7, st * 0.05F + arm, 255, 200, 160, al, 0, 0, 1, 1);
            Random rr = new Random(arm * 977L + (long) (st / 3));
            for (int p = 0; p < 4; p++) {
                double age = ((st + p * 5) % 20) / 20.0;
                sprite(k, k.smoke, g[0] + (rr.nextDouble() - 0.5) * 4, 1 + age * 10, g[1] + (rr.nextDouble() - 0.5) * 4, 2.5 + age * 5, (float) (age * 3 + p),
                        40, 25, 25, (int) (200 * fade * (1 - age)), 0, 0, 1, 1);
            }
        }
        // 巻き上がる火の粉
        Random rnd = new Random(42);
        for (int i = 0; i < 260; i++) {
            double ph = rnd.nextDouble() * 80, sp = 0.5 + rnd.nextDouble();
            double lifeK = ((st * sp + ph) % 80) / 80.0;
            double rr = 4 + rnd.nextDouble() * (radius - 4) * (1 - lifeK * 0.7);
            double a = rnd.nextDouble() * Math.PI * 2 + st * 0.04 * sp;
            glow(k, Math.cos(a) * rr, lifeK * 70, Math.sin(a) * rr, 0.5 + rnd.nextDouble() * 0.6, 255, 80 + rnd.nextInt(80), 40, (int) (230 * fade * (1 - lifeK)));
        }
    }

    // ------------------------------------------------------------------ CARPET

    static void carpet(Ctx k, long seed, java.util.List<TacticalPatterns.Bomb> bombs, java.util.function.BiFunction<Double, Double, Double> ground, float t, float fade) {
        Vec3 dir = TacticalPatterns.carpetDir(seed), perp = new Vec3(-dir.z, 0, dir.x);
        int portal = TacticalPatterns.CARPET_PORTAL, pass = TacticalPatterns.CARPET_PASS;
        // 裂け目の光
        double r = TacticalPatterns.CARPET_R, alt = TacticalPatterns.CARPET_ALT + 2;
        float in = t < portal ? t / portal : t < portal + 30 ? 1F : Math.max(0F, 1F - (t - portal - 30) / 15F);
        float out = t < portal + pass - 30 ? 0F : t < portal + pass + 5 ? Math.min(1F, (t - (portal + pass - 30)) / 15F) : Math.max(0F, 1F - (t - portal - pass - 5) / 15F);
        riftGlow(k, dir.scale(-r - 30).add(0, alt, 0), perp, in, t);
        riftGlow(k, dir.scale(r + 30).add(0, alt, 0), perp, out, t);
        // 爆撃機の噴射の光
        if (t >= portal && t <= portal + pass) {
            for (int i = 0; i < TacticalPatterns.CARPET_BOMBERS; i++) {
                Vec3 p = TacticalPatterns.bomber(i, t, seed);
                Vec3 tail = p.add(dir.scale(-4.5));
                glow(k, tail.x, tail.y + 0.3, tail.z, 2.2 + 0.4 * Mth.sin(t * 2 + i), 255, 110, 50, 230);
                glow(k, p.x + perp.x * 6, p.y + 0.3, p.z + perp.z * 6, 0.8, 255, 40, 40, 255);
                glow(k, p.x - perp.x * 6, p.y + 0.3, p.z - perp.z * 6, 0.8, 255, 40, 40, 255);
            }
        }
        for (TacticalPatterns.Bomb b : bombs) {
            if (t >= b.dropTick() && t < b.landTick()) {
                float kk = (t - b.dropTick()) / (b.landTick() - b.dropTick());
                double gy = ground.apply(b.x(), b.z());
                double y = b.dropPos().y + (gy - b.dropPos().y) * kk * kk;
                double x = b.dropPos().x + (b.x() - b.dropPos().x) * kk, z = b.dropPos().z + (b.z() - b.dropPos().z) * kk;
                glow(k, x, y, z, 1.4, 255, 170, 70, 255);
                upright(k, k.streak, x, y, z, 0.35, 5.0, 255, 140, 50, 200, 0, 0, 1, 1);
            } else if (t >= b.landTick() && t < b.landTick() + TacticalPatterns.CARPET_FIRE) {
                float age = t - b.landTick();
                float life = Math.min(1F, (TacticalPatterns.CARPET_FIRE - age) / 30F) * fade;
                double gy = ground.apply(b.x(), b.z());
                if (age < 8) glow(k, b.x(), gy + 1, b.z(), 6 * (1 - age / 8), 255, 200, 120, (int) (255 * (1 - age / 8))); // 着弾の閃光
                // 炎（16 コマをずらして、炎の半径いっぱいに大小 9 枚。燃える範囲が見て分かるよう大きく）
                double fr = TacticalPatterns.CARPET_FIRE_R;
                for (int f = 0; f < 9; f++) {
                    int frame = (int) ((t * 0.6F + b.dropTick() * 3 + f * 5) % 16);
                    float v0 = frame / 16F, v1 = (frame + 1) / 16F;
                    double fa = f * 2.4 + b.dropTick(), fd = f == 0 ? 0 : fr * (0.35 + 0.6 * ((f * 37 % 10) / 10.0));
                    double ox = Math.cos(fa) * fd, oz = Math.sin(fa) * fd;
                    double h = (f == 0 ? 6.5 : 4.2 - fd * 0.35) * (age < 6 ? 1.5 : 1.0) * (0.9 + 0.1 * Mth.sin(t * 0.3F + f));
                    upright(k, k.flame, b.x() + ox, gy - 0.05, b.z() + oz, h * 0.5, h, 255, 255, 255, (int) (255 * life), 0, v0, 1, v1);
                }
                glow(k, b.x(), gy + 1.0, b.z(), fr * 1.1, 255, 90, 40, (int) (150 * life));
                glow(k, b.x(), gy + 4.5, b.z(), fr * 0.7, 200, 60, 255, (int) (80 * life)); // 炎の上の紫の揺らめき
                for (int p = 0; p < 2; p++) {
                    double sa = ((age + p * 11 + b.dropTick()) % 30) / 30.0;
                    sprite(k, k.smoke, b.x() + Mth.sin((float) (age * 0.05 + p)) * 1.2, gy + 5 + sa * 9, b.z(), 2.4 + sa * 4.0, (float) (sa * 2 + p),
                            30, 26, 30, (int) (170 * life * (1 - sa)), 0, 0, 1, 1);
                }
            }
        }
    }

    private static void riftGlow(Ctx k, Vec3 c, Vec3 side, float open, float t) {
        if (open <= 0.01F) return;
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48 + t * 0.06;
            Vec3 p = c.add(side.scale(Math.cos(a) * 15 * open)).add(0, Math.sin(a) * 8.5 * open, 0);
            glow(k, p.x, p.y, p.z, 1.6, 170, 70, 255, (int) (200 * open));
        }
        glow(k, c.x, c.y, c.z, 12 * open, 120, 40, 220, (int) (120 * open));
    }

    // ------------------------------------------------------------------ IRIS

    private static final int[][] PETALS = {{255, 140, 195}, {255, 225, 100}, {130, 225, 255}, {255, 250, 245}, {195, 150, 255}, {255, 175, 90}, {150, 250, 170}};

    static void iris(Ctx k, long seed, double radius, float t, float fade) {
        float rise = Math.min(1F, t / 20F);
        Random rnd = new Random(seed);
        for (int j = 0; j < 520; j++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * radius;
            double period = 110 + rnd.nextInt(90), phase = rnd.nextDouble() * period;
            int[] col = PETALS[rnd.nextInt(PETALS.length)];
            double spin = rnd.nextDouble() * Math.PI * 2;
            double fall = ((t + phase) % period) / period;
            double y = 20 * (1 - fall);
            double x = Math.cos(a) * d + Math.sin(t * 0.05 + j) * 0.9, z = Math.sin(a) * d + Math.cos(t * 0.04 + j * 1.3) * 0.9;
            int al = (int) (240 * fade * rise * Math.min(1.0, fall * 6) * Math.min(1.0, (1 - fall) * 8));
            sprite(k, k.petal, x, y, z, 0.28 + (j % 4) * 0.05, (float) (spin + t * 0.12), col[0], col[1], col[2], al, 0, 0, 1, 1);
            if (j % 6 == 0) glow(k, x, y, z, 0.35, col[0], col[1], col[2], al / 3); // ほのかな光
        }
        // 地面に積もって光る花びら
        Random g = new Random(seed ^ 77);
        for (int j = 0; j < 160; j++) {
            double a = g.nextDouble() * Math.PI * 2, d = Math.sqrt(g.nextDouble()) * radius;
            int[] col = PETALS[g.nextInt(PETALS.length)];
            glow(k, Math.cos(a) * d, 0.2, Math.sin(a) * d, 0.6, col[0], col[1], col[2], (int) (90 * fade * Math.min(1F, t / 60F)));
        }
    }

    // ------------------------------------------------------------------ ABYSS

    static void abyss(Ctx k, long seed, float t, float life) {
        float end = Math.max(0F, (t - (life - 12)) / 12F);
        double sr = 2.6 * (1 + t / life * 0.6) * (1 - end * 0.9);
        // 降着円盤（傾いた円盤の上を回る光。内ほど速く熱い色）
        Random rnd = new Random(seed);
        double tilt = 0.35;
        for (int i = 0; i < 420; i++) {
            double rr = sr * 1.4 + rnd.nextDouble() * sr * 2.6;
            double speed = 0.35 * Math.pow(sr * 1.4 / rr, 1.5);
            double a = rnd.nextDouble() * Math.PI * 2 + t * speed;
            double x = Math.cos(a) * rr, z = Math.sin(a) * rr, y = Math.sin(a) * rr * tilt * 0.3 + (rnd.nextDouble() - 0.5) * 0.3;
            double hot = (rr - sr * 1.4) / (sr * 2.6);
            int cr = 255, cg = (int) (200 - hot * 140), cb = (int) (120 + hot * 135);
            glow(k, x, y, z, 0.45 + rnd.nextDouble() * 0.5, cr, cg, cb, (int) (200 * (1 - end) * (1 - hot * 0.5)));
        }
        // 光の輪（重力でゆがむ光。カメラを向く輪）
        for (int i = 0; i < 64; i++) {
            float a = i * Mth.TWO_PI / 64;
            double rr = sr * 1.25;
            double x = (k.left.x() * Mth.cos(a) + k.up.x() * Mth.sin(a)) * rr, y = (k.left.y() * Mth.cos(a) + k.up.y() * Mth.sin(a)) * rr,
                    z = (k.left.z() * Mth.cos(a) + k.up.z() * Mth.sin(a)) * rr;
            glow(k, x, y, z, 0.5, 255, 230, 255, (int) (230 * (1 - end)));
        }
        // ひびから漏れる光（ひびと同じ種から）
        Random cr = new Random(seed);
        float pulse = 0.7F + 0.3F * Mth.sin(t * 0.5F);
        for (int c = 0; c < 12; c++) {
            double th = cr.nextDouble() * Math.PI, ph = cr.nextDouble() * Math.PI * 2;
            int steps = 5 + cr.nextInt(5);
            for (int s = 0; s < steps; s++) {
                th += (cr.nextDouble() - 0.5) * 0.5;
                ph += (cr.nextDouble() - 0.5) * 0.6;
                if ((float) s / steps >= t / (life * 0.5F)) continue;
                double x = Math.sin(th) * Math.cos(ph) * sr * 1.02, y = Math.cos(th) * sr * 1.02, z = Math.sin(th) * Math.sin(ph) * sr * 1.02;
                glow(k, x, y, z, 0.9, 220, 150, 255, (int) (230 * pulse));
                if (s % 2 == 0) sprite(k, k.flare, x, y, z, 2.2 * pulse, (float) (c + s), 230, 190, 255, (int) (200 * pulse), 0, 0, 1, 1);
            }
        }
        if (end > 0) {
            sprite(k, k.flare, 0, 0, 0, 30 * end, 0, 255, 230, 255, (int) (255 * (1 - end)), 0, 0, 1, 1);
            glow(k, 0, 0, 0, 20 * end, 200, 120, 255, (int) (200 * (1 - end)));
        }
    }
}
