package com.automatavalkyries.client;

import com.automatavalkyries.tactical.TacticalPatterns;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * COMET［仮想彗星］の幻想的な演出（2026-10-07 ユーザー指定「範囲を倍に、エフェクトを複雑で半透明にして幻想的に。時間をかけて高度なエフェクトを」）。
 * <ol>
 *   <li>前兆: 狙った所の地面に、回る魔法陣（同心の輪・刻印の破線・星形の線）が少しずつ広がる。空にオーロラの帳が揺れる</li>
 *   <li>落下: 彗星の頭は光の芯・重なる半透明の殻（縁ほど光る）・回る結晶の面。後ろへまっすぐ伸びる青いイオンの尾と、曲がって広がる金白の塵の尾。
 *       横に伸びる光の筋（レンズの光）</li>
 *   <li>着弾: 半透明のドーム（縁ほど光る殻）が範囲いっぱいまで二重に広がり、地面に波紋。結晶の欠片がゆっくり回りながら昇り、
 *       光の花びらが螺旋に舞い上がり、オーロラが残る</li>
 * </ol>
 * 足し算の光（{@link net.minecraft.client.renderer.RenderType#lightning}）・半透明の面（debugQuads）と、素材を貼った光（{@link TacticalSprites}）を重ねる。
 * 殻の明るさは見る向きで変わる（正面は透け、縁が光る）
 */
final class TacticalComet {

    private TacticalComet() {}

    /** camRel: カメラの位置（演出の中心からの相対）。殻の縁の光り方に使う */
    static void render(Matrix4f m, VertexConsumer glow, VertexConsumer dark, TacticalSprites.Ctx k, long seed, float radius, float t, float life, Vec3 camRel) {
        int fall = TacticalPatterns.COMET_FALL;
        float fade = Mth.clamp((life - t) / 30F, 0F, 1F);
        float omen = Mth.clamp(t / fall, 0F, 1F);
        // 空のオーロラ（全体を通して）
        aurora(m, glow, seed, radius, t, Math.min(omen * 1.5F, 1F) * fade);
        if (t < fall) {
            magicCircle(m, glow, dark, radius * Mth.clamp(omen * 1.2F, 0.05F, 1F), t, omen);
            cometHead(m, glow, dark, k, seed, t, camRel);
            return;
        }
        float st = t - fall;
        magicCircle(m, glow, dark, radius, t, Math.max(0F, 1F - st / 40F)); // 着弾で魔法陣が弾ける
        impact(m, glow, dark, k, seed, radius, st, fade, camRel);
    }

    // ------------------------------------------------------------------ 前兆: 魔法陣とオーロラ

    private static void magicCircle(Matrix4f m, VertexConsumer glow, VertexConsumer dark, float r, float t, float a) {
        if (a <= 0.01F || r < 1) return;
        int al = (int) (200 * a);
        float[] rings = {1.0F, 0.82F, 0.62F, 0.4F, 0.18F};
        for (int i = 0; i < rings.length; i++) {
            double rr = r * rings[i];
            TacticalFx.ring(glow, m, rr, 0.12 + i * 0.01, 0.35 + i * 0.05, 150, 220, 255, al);
            // 刻印（回る破線）
            double rot = t * (i % 2 == 0 ? 0.01 : -0.016) * (1 + i * 0.3);
            int marks = 24 + i * 12;
            for (int j = 0; j < marks; j++) {
                if ((j * 7 + i) % 5 == 0) continue;
                double a0 = rot + j * Math.PI * 2 / marks, a1 = a0 + Math.PI * 2 / marks * 0.55;
                double ri = rr - 0.9 - (j % 3) * 0.25, ro = rr - 0.25;
                TacticalFx.quad(glow, m, new float[]{(float) (Math.cos(a0) * ri), 0.14F, (float) (Math.sin(a0) * ri), (float) (Math.cos(a0) * ro), 0.14F, (float) (Math.sin(a0) * ro),
                                (float) (Math.cos(a1) * ro), 0.14F, (float) (Math.sin(a1) * ro), (float) (Math.cos(a1) * ri), 0.14F, (float) (Math.sin(a1) * ri)},
                        new int[]{200, 240, 255, al, 200, 240, 255, al, 200, 240, 255, al, 200, 240, 255, al});
            }
        }
        // 星形（七芒星と五芒星を逆に回す）と、半透明の淡い地
        star(m, glow, r * 0.82, 7, 3, t * 0.008, 170, 230, 255, al);
        star(m, glow, r * 0.4, 5, 2, -t * 0.014, 220, 200, 255, al);
        TacticalFx.ring(dark, m, r * 0.5, 0.1, r, 40, 90, 160, (int) (35 * a));
    }

    /** 星形（n 角を step 飛ばしで結ぶ） */
    private static void star(Matrix4f m, VertexConsumer c, double r, int n, int step, double rot, int cr, int cg, int cb, int a) {
        for (int i = 0; i < n; i++) {
            double a0 = rot + i * Math.PI * 2 / n, a1 = rot + ((i + step) % n) * Math.PI * 2 / n;
            flatLine(m, c, Math.cos(a0) * r, Math.sin(a0) * r, Math.cos(a1) * r, Math.sin(a1) * r, 0.16, 0.3, cr, cg, cb, a);
        }
    }

    /** 地面に寝かせた線（幅 w） */
    private static void flatLine(Matrix4f m, VertexConsumer c, double x0, double z0, double x1, double z1, double y, double w, int cr, int cg, int cb, int a) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) return;
        double nx = -dz / len * w, nz = dx / len * w;
        TacticalFx.quad(c, m, new float[]{(float) (x0 + nx), (float) y, (float) (z0 + nz), (float) (x1 + nx), (float) y, (float) (z1 + nz),
                        (float) (x1 - nx), (float) y, (float) (z1 - nz), (float) (x0 - nx), (float) y, (float) (z0 - nz)},
                new int[]{cr, cg, cb, a, cr, cg, cb, a, cr, cg, cb, a, cr, cg, cb, a});
    }

    /** オーロラの帳（高い空に、波打つ縦の帯。下は緑青、上は紫に透ける） */
    private static void aurora(Matrix4f m, VertexConsumer glow, long seed, float radius, float t, float a) {
        if (a <= 0.01F) return;
        Random r = new Random(seed ^ 0xA0A0);
        for (int band = 0; band < 4; band++) {
            double baseA = r.nextDouble() * Math.PI * 2, span = 1.2 + r.nextDouble();
            double dist = radius * (0.8 + r.nextDouble() * 0.9), y0 = 55 + r.nextDouble() * 20, h = 26 + r.nextDouble() * 14;
            int seg = 48;
            for (int i = 0; i < seg; i++) {
                double u0 = (double) i / seg, u1 = (double) (i + 1) / seg;
                double a0 = baseA + span * u0, a1 = baseA + span * u1;
                double w0 = Math.sin(u0 * 9 + t * 0.05 + band) * 4, w1 = Math.sin(u1 * 9 + t * 0.05 + band) * 4;
                float x0 = (float) (Math.cos(a0) * (dist + w0)), z0 = (float) (Math.sin(a0) * (dist + w0));
                float x1 = (float) (Math.cos(a1) * (dist + w1)), z1 = (float) (Math.sin(a1) * (dist + w1));
                double fl = 0.6 + 0.4 * Math.sin(u0 * 23 + t * 0.12 + band * 2);
                int al = (int) (110 * a * fl * Math.sin(u0 * Math.PI));
                float yb = (float) y0, ym = (float) (y0 + h * 0.45), yt = (float) (y0 + h);
                TacticalFx.quad(glow, m, new float[]{x0, yb, z0, x1, yb, z1, x1, ym, z1, x0, ym, z0},
                        new int[]{60, 255, 190, al, 60, 255, 190, al, 90, 200, 255, al, 90, 200, 255, al});
                TacticalFx.quad(glow, m, new float[]{x0, ym, z0, x1, ym, z1, x1, yt, z1, x0, yt, z0},
                        new int[]{90, 200, 255, al, 90, 200, 255, al, 190, 110, 255, 0, 190, 110, 255, 0});
            }
        }
    }

    // ------------------------------------------------------------------ 落下: 彗星

    private static void cometHead(Matrix4f m, VertexConsumer glow, VertexConsumer dark, TacticalSprites.Ctx k, long seed, float t, Vec3 camRel) {
        int fall = TacticalPatterns.COMET_FALL;
        Vec3 head = TacticalPatterns.cometPos(t, seed);
        Vec3 back = TacticalPatterns.cometPos(t - 2, seed).subtract(head);
        Vec3 motion = back.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : back.normalize(); // 尾の向き（来た方）
        float grow = 0.6F + 0.5F * (t / fall);
        // 重なる半透明の殻（縁ほど光る）
        shell(m, glow, head, 4.0 * grow, camRel, 200, 235, 255, 210, 18, 32);
        shell(m, glow, head, 7.5 * grow, camRel, 110, 190, 255, 150, 16, 28);
        shell(m, glow, head, 12.0 * grow, camRel, 140, 120, 255, 90, 14, 24);
        // 回る結晶の面（正二十面体の半透明の面）
        crystal(m, glow, dark, head, 3.2 * grow, t * 0.05, t * 0.031);
        // イオンの尾（まっすぐ後ろへ。青い光の帯を何本も）
        Random r = new Random(seed);
        for (int s = 0; s < 6; s++) {
            Vec3 off = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(0.8);
            Vec3 tail = head.add(motion.scale(70 + s * 8)).add(off.scale(6));
            TacticalFx.ribbon(glow, m, head.x + off.x, head.y + off.y, head.z + off.z, tail.x, tail.y, tail.z, 1.4 * grow, 90, 170, 255, 90);
        }
        // 塵の尾（曲がりながら広がる金白の光の粒）
        Vec3 side = motion.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i < 160; i++) {
            double u = i / 160.0;
            Vec3 p = head.add(motion.scale(u * 60)).add(side.scale(u * u * 22)).add(new Vec3(Math.sin(i * 1.7) * u * 4, Math.cos(i * 2.3) * u * 4, Math.sin(i * 0.9) * u * 4));
            TacticalSprites.glow(k, p.x, p.y, p.z, 1.2 + u * 2.5, 255, (int) (240 - u * 40), (int) (200 - u * 60), (int) (170 * (1 - u)));
        }
        // 光る芯・十字の光・横に伸びる光の筋（レンズの光）
        TacticalSprites.glow(k, head.x, head.y, head.z, 9 * grow, 235, 250, 255, 255);
        TacticalSprites.sprite(k, k.flare, head.x, head.y, head.z, 30 * grow, t * 0.015F, 220, 240, 255, 255, 0, 0, 1, 1);
        TacticalSprites.sprite(k, k.streak, head.x, head.y, head.z, 40 * grow, Mth.HALF_PI, 150, 210, 255, 160, 0, 0, 1, 1);
    }

    /** 縁ほど光る半透明の殻（見る向きと面の向きで明るさを変える） */
    static void shell(Matrix4f m, VertexConsumer c, Vec3 center, double r, Vec3 camRel, int cr, int cg, int cb, int maxA, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                Vec3[] n = {dir(t0, p0), dir(t0, p1), dir(t1, p1), dir(t1, p0)};
                float[] pts = new float[12];
                int[] col = new int[16];
                for (int q = 0; q < 4; q++) {
                    Vec3 p = center.add(n[q].scale(r));
                    pts[q * 3] = (float) p.x;
                    pts[q * 3 + 1] = (float) p.y;
                    pts[q * 3 + 2] = (float) p.z;
                    Vec3 view = camRel.subtract(p);
                    double d = view.lengthSqr() < 1.0e-6 ? 0 : Math.abs(n[q].dot(view.normalize()));
                    double rim = Math.pow(1 - d, 2.2);
                    col[q * 4] = cr;
                    col[q * 4 + 1] = cg;
                    col[q * 4 + 2] = cb;
                    col[q * 4 + 3] = (int) (maxA * (0.08 + 0.92 * rim));
                }
                TacticalFx.quad(c, m, pts, col);
            }
        }
    }

    private static Vec3 dir(double theta, double phi) {
        return new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
    }

    private static final double PHI = (1 + Math.sqrt(5)) / 2;
    private static final double[][] ICO_V = {{-1, PHI, 0}, {1, PHI, 0}, {-1, -PHI, 0}, {1, -PHI, 0}, {0, -1, PHI}, {0, 1, PHI}, {0, -1, -PHI}, {0, 1, -PHI},
            {PHI, 0, -1}, {PHI, 0, 1}, {-PHI, 0, -1}, {-PHI, 0, 1}};
    private static final int[][] ICO_F = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};

    /** 回る結晶（正二十面体）。面は半透明の水色、辺は光る */
    static void crystal(Matrix4f m, VertexConsumer glow, VertexConsumer dark, Vec3 c, double size, double rotY, double rotX) {
        Vec3[] v = new Vec3[12];
        double cy = Math.cos(rotY), sy = Math.sin(rotY), cx = Math.cos(rotX), sx = Math.sin(rotX);
        for (int i = 0; i < 12; i++) {
            double x = ICO_V[i][0], y = ICO_V[i][1], z = ICO_V[i][2];
            double x1 = x * cy + z * sy, z1 = -x * sy + z * cy;
            double y2 = y * cx - z1 * sx, z2 = y * sx + z1 * cx;
            v[i] = c.add(new Vec3(x1, y2, z2).scale(size / 1.9));
        }
        for (int[] f : ICO_F) {
            TacticalFx.quadPts(dark, m, v[f[0]], v[f[1]], v[f[2]], v[f[2]], new int[]{150, 220, 255, 70});
            TacticalFx.ribbon(glow, m, v[f[0]].x, v[f[0]].y, v[f[0]].z, v[f[1]].x, v[f[1]].y, v[f[1]].z, 0.12, 210, 245, 255, 220);
        }
    }

    // ------------------------------------------------------------------ 着弾

    private static void impact(Matrix4f m, VertexConsumer glow, VertexConsumer dark, TacticalSprites.Ctx k, long seed, float radius, float st, float fade, Vec3 camRel) {
        // 二重に広がる半透明のドーム
        for (int d = 0; d < 2; d++) {
            float ph = Mth.clamp((st - d * 10) / 45F, 0F, 1F);
            if (ph <= 0 || ph >= 1) continue;
            double rr = radius * (1 - Math.pow(1 - ph, 3));
            shell(m, glow, Vec3.ZERO, rr, camRel, d == 0 ? 170 : 200, d == 0 ? 230 : 180, 255, (int) (200 * (1 - ph)), 20, 40);
        }
        // 地面の波紋（何度も広がる）
        for (int w = 0; w < 6; w++) {
            float ph = ((st + w * 12) % 70) / 70F;
            TacticalFx.ring(glow, m, radius * ph, 0.2, 0.6 + 1.5 * (1 - ph), 160, 225, 255, (int) (150 * fade * (1 - ph)));
        }
        // 中心の光の泉（細い光の柱と、根元の大きな光）
        float spring = Math.max(0F, 1F - st / 120F) * fade;
        TacticalSprites.glow(k, 0, 1.5, 0, 18 * spring + 4, 170, 230, 255, (int) (200 * Math.max(spring, 0.2F)));
        for (int y = 0; y < 90; y += 4) TacticalSprites.glow(k, 0, y, 0, (3 + 2 * Mth.sin(st * 0.2F + y * 0.3F)) * spring, 190, 240, 255, (int) (160 * spring * (1 - y / 90F)));
        float flash = Math.max(0F, 1F - st / 18F);
        if (flash > 0) TacticalSprites.sprite(k, k.flare, 0, 3, 0, 110 * flash + 10, 0, 235, 248, 255, (int) (255 * flash), 0, 0, 1, 1);
        // 結晶の欠片がゆっくり回りながら昇る
        Random r = new Random(seed ^ 0xC0FFEE);
        for (int i = 0; i < 46; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 4 + Math.sqrt(r.nextDouble()) * (radius - 4);
            double delay = r.nextDouble() * 30, rise = Math.max(0, st - delay);
            double y = rise * (0.05 + r.nextDouble() * 0.08) * 6;
            double size = 0.8 + r.nextDouble() * 1.6;
            if (rise <= 0) continue;
            float al = (float) Math.min(1, rise / 15) * fade;
            Vec3 c = new Vec3(Math.cos(a) * d, y, Math.sin(a) * d);
            crystalFaded(m, glow, dark, c, size, st * 0.03 + i, st * 0.02 + i * 0.7, al);
            TacticalSprites.glow(k, c.x, c.y, c.z, size * 2.2, 150, 220, 255, (int) (110 * al));
        }
        // 光の花びらが螺旋に舞い上がる
        Random p = new Random(seed ^ 0xF10);
        for (int i = 0; i < 360; i++) {
            double base = p.nextDouble() * Math.PI * 2, rr = 3 + p.nextDouble() * radius * 0.9, sp = 0.6 + p.nextDouble();
            double ph = ((st * sp + p.nextDouble() * 120) % 120) / 120.0;
            double ang = base + ph * Math.PI * 2 * (p.nextBoolean() ? 1 : -1);
            double y = ph * 40, rad = rr * (1 - ph * 0.5);
            int[] col = i % 3 == 0 ? new int[]{220, 200, 255} : i % 3 == 1 ? new int[]{170, 235, 255} : new int[]{255, 250, 240};
            int al = (int) (220 * fade * Math.min(1F, st / 20F) * Math.sin(ph * Math.PI));
            TacticalSprites.sprite(k, k.petal, Math.cos(ang) * rad, y, Math.sin(ang) * rad, 0.35, (float) (st * 0.1 + i), col[0], col[1], col[2], al, 0, 0, 1, 1);
            if (i % 4 == 0) TacticalSprites.glow(k, Math.cos(ang) * rad, y, Math.sin(ang) * rad, 0.6, col[0], col[1], col[2], al / 2);
        }
    }

    /** 薄く消えていく結晶 */
    private static void crystalFaded(Matrix4f m, VertexConsumer glow, VertexConsumer dark, Vec3 c, double size, double rotY, double rotX, float a) {
        if (a <= 0.02F) return;
        Vec3[] v = new Vec3[12];
        double cy = Math.cos(rotY), sy = Math.sin(rotY), cx = Math.cos(rotX), sx = Math.sin(rotX);
        for (int i = 0; i < 12; i++) {
            double x = ICO_V[i][0], y = ICO_V[i][1] * 1.8, z = ICO_V[i][2]; // 縦長の結晶
            double x1 = x * cy + z * sy, z1 = -x * sy + z * cy;
            double y2 = y * cx * 0.3 + y * 0.7 - z1 * sx * 0.3, z2 = z1;
            v[i] = c.add(new Vec3(x1, y2, z2).scale(size / 1.9));
        }
        for (int[] f : ICO_F) {
            TacticalFx.quadPts(dark, m, v[f[0]], v[f[1]], v[f[2]], v[f[2]], new int[]{160, 225, 255, (int) (80 * a)});
            TacticalFx.ribbon(glow, m, v[f[0]].x, v[f[0]].y, v[f[0]].z, v[f[1]].x, v[f[1]].y, v[f[1]].z, 0.08, 200, 240, 255, (int) (200 * a));
        }
    }
}
