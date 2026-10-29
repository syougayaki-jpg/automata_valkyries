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
 *   <li>落下（2026-10-29 に電脳の彗星へ作り替え。{@link #cometHead}）: 多面体の結晶の核・回る光の弧・水色と桃紫の長い光の筋の束の尾と光の粒（空は星空に暗くなる）。
 *       地面に沈み込む電脳の格子と照準。画面に警告（{@link #hud}）</li>
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
            magicCircle(m, glow, dark, radius * Mth.clamp(omen * 1.2F, 0.05F, 1F), t, omen * 0.5F);
            cyberGrid(m, glow, radius, t, omen); // 電脳の格子が沈み込む（2026-10-29）
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

    // ------------------------------------------------------------------ 落下: 彗星（電脳の彗星）

    /** 尾の筋の色（参考画像: 水色・桃紫・白・紫・青。水色と桃紫を多めに） */
    private static final int[][] TAIL = {{70, 225, 255}, {255, 60, 215}, {245, 250, 255}, {60, 225, 255}, {170, 80, 255}, {255, 90, 230}, {60, 130, 255}};

    /** 電脳の色: 水色・青・白・桃紫・紫 */
    private static final int[][] CYBER = {{60, 230, 255}, {40, 120, 255}, {235, 250, 255}, {255, 70, 220}, {150, 80, 255}};

    /**
     * 彗星の本体（2026-10-29 ユーザー指定「仮想彗星の本体の演出をこんな感じに」。参考画像: 多面体の結晶の核・まわりを回る光の弧・
     * 水色と桃紫の光の筋が長く伸びる尾・四角いデータの粒と数字の列）。
     * <ul>
     *   <li>核: 細かく割った多面体の面を、水色・青・白・桃紫で塗り分けて瞬かせ、辺を白く光らせる。中に光の芯</li>
     *   <li>光の弧: 傾いた 3 本の輪（水色・桃紫）が核のまわりを回る（途切れた弧）</li>
     *   <li>尾: 来た方へ束になって伸びる長い光の筋（素材を線に沿って張る）、頭のすぐ後ろの太い光、瞬く光の粒</li>
     * </ul>
     */
    private static void cometHead(Matrix4f m, VertexConsumer glow, VertexConsumer dark, TacticalSprites.Ctx k, long seed, float t, Vec3 camRel) {
        int fall = TacticalPatterns.COMET_FALL;
        Vec3 head = TacticalPatterns.cometPos(t, seed);
        Vec3 back = TacticalPatterns.cometPos(t - 2, seed).subtract(head);
        Vec3 motion = back.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : back.normalize(); // 尾の向き（来た方）
        Vec3 side = motion.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 lift = side.cross(motion).normalize();
        float grow = 0.7F + 0.6F * (t / fall);
        int tick = (int) t;

        // ---- 尾（2026-10-29 ユーザー指定「本体まわりの四角い光を消して、尾を引く演出をそっくりに」）:
        //      頭から来た方へ、水色・桃紫・白・紫・青の長い光の筋が束になって伸びる（素材の筋を線に沿って張る。明るい端が頭側）。
        //      頭のすぐ後ろは太い白と水色の光（コマ）、尾の中に小さな光の粒が瞬きながら流れる
        Random r = new Random(seed ^ 0x5EED);
        for (int s = 0; s < 90; s++) {
            double ang = r.nextDouble() * Math.PI * 2, spread = Math.pow(r.nextDouble(), 0.7);
            double len = (35 + r.nextDouble() * 110) * (0.9 + 0.1 * Math.sin(t * 0.25 + s));
            Vec3 o0 = side.scale(Math.cos(ang) * spread * 2.4 * grow).add(lift.scale(Math.sin(ang) * spread * 2.4 * grow));
            Vec3 o1 = side.scale(Math.cos(ang) * spread * 16 * grow).add(lift.scale(Math.sin(ang) * spread * 16 * grow));
            Vec3 p0 = head.add(o0).add(motion.scale(1.0 + r.nextDouble() * 4)), p1 = head.add(motion.scale(len)).add(o1);
            int[] c = TAIL[s % TAIL.length];
            double flick = 0.7 + 0.3 * Math.sin(t * 0.8 + s * 1.3);
            double width = (0.5 + Math.pow(r.nextDouble(), 2) * 2.6) * grow;
            TacticalSprites.axisSprite(k, k.streak, p0, p1, width, camRel, c[0], c[1], c[2], (int) (235 * flick));
        }
        for (int s = 0; s < 4; s++) { // コマ（頭のすぐ後ろの太い光）
            Vec3 p1 = head.add(motion.scale((28 + s * 9) * grow));
            int[] c = s % 2 == 0 ? TAIL[2] : TAIL[0];
            TacticalSprites.axisSprite(k, k.streak, head.add(motion.scale(0.5)), p1, (9 - s * 1.6) * grow, camRel, c[0], c[1], c[2], 255);
        }
        Random d = new Random(seed ^ 0xDA7A);
        for (int i = 0; i < 180; i++) { // 尾の中の光の粒
            double u = Math.pow(d.nextDouble(), 0.8);
            double ang = d.nextDouble() * Math.PI * 2, rad = (1 + u * 17) * grow * Math.sqrt(d.nextDouble());
            double drift = (t * (0.6 + d.nextDouble())) % 15;
            Vec3 p = head.add(motion.scale(u * 115 + drift)).add(side.scale(Math.cos(ang) * rad)).add(lift.scale(Math.sin(ang) * rad));
            int[] c = TAIL[i % TAIL.length];
            double tw = 0.5 + 0.5 * Math.sin(t * 1.3 + i * 2.1);
            TacticalSprites.glow(k, p.x, p.y, p.z, (0.25 + d.nextDouble() * 0.6) * (1 - u * 0.4), c[0], c[1], c[2], (int) (230 * tw * (1 - u * 0.6)));
        }

        // ---- 核: 多面体の結晶（面の塗り分けと瞬き、白い辺）
        cyberCore(m, glow, dark, head, 4.2 * grow, t, seed);
        shell(m, glow, head, 6.5 * grow, camRel, 120, 210, 255, 120, 14, 24);
        shell(m, glow, head, 9.5 * grow, camRel, 230, 90, 255, 70, 12, 20);

        // ---- 光の弧: 傾いた 3 本の輪が回る（途切れた弧）
        for (int ringI = 0; ringI < 3; ringI++) {
            double tilt = 0.6 + ringI * 0.9, spin = t * (0.12 + ringI * 0.05) * (ringI % 2 == 0 ? 1 : -1);
            double rr = (7 + ringI * 2.2) * grow;
            int[] c = ringI == 1 ? CYBER[3] : CYBER[0];
            Vec3 ax = side.scale(Math.cos(tilt)).add(lift.scale(Math.sin(tilt)));
            Vec3 ay = motion.cross(ax).normalize();
            int seg = 48;
            for (int sgi = 0; sgi < seg; sgi++) {
                if ((sgi + ringI * 5) % 12 >= 8) continue; // 途切れ
                double a0 = spin + Math.PI * 2 * sgi / seg, a1 = spin + Math.PI * 2 * (sgi + 1) / seg;
                Vec3 p0 = head.add(ax.scale(Math.cos(a0) * rr)).add(ay.scale(Math.sin(a0) * rr * 0.55)).add(motion.scale(Math.sin(a0) * 1.5));
                Vec3 p1 = head.add(ax.scale(Math.cos(a1) * rr)).add(ay.scale(Math.sin(a1) * rr * 0.55)).add(motion.scale(Math.sin(a1) * 1.5));
                TacticalFx.ribbon(glow, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.28 * grow, c[0], c[1], c[2], 220);
            }
        }

        // ---- 光る芯・閃光・横に伸びる光の筋
        TacticalSprites.glow(k, head.x, head.y, head.z, 10 * grow, 240, 252, 255, 255);
        TacticalSprites.glow(k, head.x, head.y, head.z, 18 * grow, 120, 200, 255, 140);
        TacticalSprites.sprite(k, k.flare, head.x, head.y, head.z, 34 * grow, t * 0.02F, 210, 235, 255, 255, 0, 0, 1, 1);
    }

    /** 多面体の核: 正二十面体を 1 段細かく割った 80 面を、色を塗り分けて瞬かせる。辺は白く光る */
    private static void cyberCore(Matrix4f m, VertexConsumer glow, VertexConsumer dark, Vec3 c, double size, float t, long seed) {
        double rotY = t * 0.05, rotX = t * 0.031;
        double cy = Math.cos(rotY), sy = Math.sin(rotY), cx = Math.cos(rotX), sx = Math.sin(rotX);
        Vec3[] base = new Vec3[12];
        for (int i = 0; i < 12; i++) base[i] = new Vec3(ICO_V[i][0], ICO_V[i][1], ICO_V[i][2]).normalize();
        int fi = 0;
        for (int[] f : ICO_F) {
            Vec3 a = base[f[0]], b = base[f[1]], cc = base[f[2]];
            Vec3 ab = a.add(b).normalize(), bc = b.add(cc).normalize(), ca = cc.add(a).normalize();
            Vec3[][] tris = {{a, ab, ca}, {ab, b, bc}, {ca, bc, cc}, {ab, bc, ca}};
            for (Vec3[] tri : tris) {
                Vec3[] w = new Vec3[3];
                for (int q = 0; q < 3; q++) {
                    double jag = 1.0 + 0.12 * Math.sin(fi * 2.1 + q + seed % 13); // 少し凸凹の面
                    Vec3 v = tri[q].scale(jag);
                    double x1 = v.x * cy + v.z * sy, z1 = -v.x * sy + v.z * cy;
                    double y2 = v.y * cx - z1 * sx, z2 = v.y * sx + z1 * cx;
                    w[q] = c.add(new Vec3(x1, y2, z2).scale(size));
                }
                int[] col = CYBER[(fi * 7 + (int) (seed & 3)) % CYBER.length];
                double flick = 0.45 + 0.55 * Math.max(0, Math.sin(t * 0.7 + fi * 1.3));
                TacticalFx.quadPts(dark, m, w[0], w[1], w[2], w[2], new int[]{col[0] / 3, col[1] / 3, col[2] / 2, 150});
                TacticalFx.quadPts(glow, m, w[0], w[1], w[2], w[2], new int[]{col[0], col[1], col[2], (int) (170 * flick)});
                TacticalFx.ribbon(glow, m, w[0].x, w[0].y, w[0].z, w[1].x, w[1].y, w[1].z, 0.07, 235, 250, 255, 230);
                fi++;
            }
        }
    }

    /**
     * 前兆の電脳の格子（参考画像の歪む格子）: 狙った所のまわりに水色の格子を敷き、彗星が近づくほど中心へ向かって沈み込む。
     * 外ほど薄く、中心に照準の十字
     */
    private static void cyberGrid(Matrix4f m, VertexConsumer glow, float radius, float t, float k) {
        if (k <= 0.01F) return;
        int n = 24;
        double step = radius * 2 / n, depth = 14 * k;
        for (int axis = 0; axis < 2; axis++) {
            for (int i = 0; i <= n; i++) {
                double fixed = -radius + i * step;
                for (int j = 0; j < n; j++) {
                    double v0 = -radius + j * step, v1 = v0 + step;
                    double x0 = axis == 0 ? fixed : v0, z0 = axis == 0 ? v0 : fixed, x1 = axis == 0 ? fixed : v1, z1 = axis == 0 ? v1 : fixed;
                    double d0 = Math.sqrt(x0 * x0 + z0 * z0) / radius, d1 = Math.sqrt(x1 * x1 + z1 * z1) / radius;
                    if (d0 > 1 && d1 > 1) continue;
                    double y0 = 0.3 - depth * Math.pow(Math.max(0, 1 - d0), 2), y1 = 0.3 - depth * Math.pow(Math.max(0, 1 - d1), 2);
                    int a = (int) (170 * k * Math.max(0, 1 - Math.min(d0, d1)) * (0.7 + 0.3 * Math.sin(t * 0.2 + i)));
                    TacticalFx.ribbon(glow, m, x0, y0, z0, x1, y1, z1, 0.12, 70, 220, 255, a);
                }
            }
        }
        int ca = (int) (230 * k);
        double cr = radius * 0.12;
        TacticalFx.ring(glow, m, cr, 0.4 - depth, 0.35, 255, 70, 200, ca);
        TacticalFx.ribbon(glow, m, -cr * 1.6, 0.4 - depth, 0, cr * 1.6, 0.4 - depth, 0, 0.25, 255, 70, 200, ca);
        TacticalFx.ribbon(glow, m, 0, 0.4 - depth, -cr * 1.6, 0, 0.4 - depth, cr * 1.6, 0.25, 255, 70, 200, ca);
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
    // ------------------------------------------------------------------ 画面の警告（落下の間）

    /**
     * 落下の間の画面の警告（参考画像の HUD）: 左上に照準と「[WARNING] VIRTUAL COMET IMPACT」、右に「仮想彗星（バーチャル・コメット）」、
     * 下に着弾までの残り時間。点滅し、着弾が近いほど速く
     */
    static void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, int w, int h, float t) {
        int fall = TacticalPatterns.COMET_FALL;
        if (t >= fall) return;
        float left = (fall - t) / 20F;
        boolean blink = Mth.sin(t * (0.4F + (1 - left / (fall / 20F)) * 1.2F)) > -0.3F;
        int pink = 0xFF4A9A, cyan = 0x3BE8FF;
        // 照準（左上）
        int cx = 46, cy = 46;
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            if (i % 16 < 12) g.fill(cx + (int) (Math.cos(a) * 18), cy + (int) (Math.sin(a) * 18), cx + (int) (Math.cos(a) * 18) + 1, cy + (int) (Math.sin(a) * 18) + 1, 0xC0000000 | cyan);
        }
        g.fill(cx - 26, cy, cx - 8, cy + 1, 0xC0000000 | cyan);
        g.fill(cx + 8, cy, cx + 26, cy + 1, 0xC0000000 | cyan);
        g.fill(cx, cy - 26, cx + 1, cy - 8, 0xC0000000 | cyan);
        g.fill(cx, cy + 8, cx + 1, cy + 26, 0xC0000000 | cyan);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xE0000000 | pink);
        if (blink) {
            g.pose().pushPose();
            g.pose().translate(24, 82, 0);
            g.pose().scale(1.6F, 1.6F, 1);
            g.drawString(font, "[WARNING]", 0, 0, 0xFF000000 | pink, true);
            g.pose().popPose();
        }
        g.drawString(font, "VIRTUAL COMET IMPACT", 26, 100, 0xD0000000 | pink, true);
        // 題（右）
        net.minecraft.network.chat.Component title = net.minecraft.network.chat.Component.translatable("tactical.automata_valkyries.comet.hud_title");
        net.minecraft.network.chat.Component sub = net.minecraft.network.chat.Component.translatable("tactical.automata_valkyries.comet.hud_sub");
        g.pose().pushPose();
        g.pose().translate(w - 20 - font.width(title) * 2.2F, h * 0.38F, 0);
        g.pose().scale(2.2F, 2.2F, 1);
        g.drawString(font, title, 0, 0, 0xFF000000 | pink, true);
        g.pose().popPose();
        g.drawString(font, sub, w - 20 - font.width(sub), (int) (h * 0.38F) + 24, 0xE0000000 | pink, true);
        g.fill(w - 22 - font.width(sub) - 4, (int) (h * 0.38F) + 36, w - 16, (int) (h * 0.38F) + 37, 0xA0000000 | cyan);
        // 残り時間（下）
        String eta = String.format(java.util.Locale.ROOT, "IMPACT  T-%.1fs", Math.max(0, left));
        int ew = font.width(eta);
        g.fill(w / 2 - ew / 2 - 8, h - 74, w / 2 + ew / 2 + 8, h - 60, 0x90000000);
        g.fill(w / 2 - ew / 2 - 8, h - 61, w / 2 - ew / 2 - 8 + (int) ((ew + 16) * (1 - left / (fall / 20F))), h - 60, 0xFF000000 | pink);
        g.drawString(font, eta, w / 2 - ew / 2, h - 71, 0xFF000000 | cyan, true);
    }
}
