package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.RulerFxEntity;
import com.automatavalkyries.entity.zero.LostCatastropheEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * 零号機の赤黒い天災の技の専用エフェクト（{@link RulerFxEntity} の Z_*。2026-10-26 ユーザー指定「味気ないのでそれぞれ専用のエフェクト追加して演出を豪華にして」）。
 * 黒い面（普通の半透明）を先に、足し算の光を後に描く。バッファを行き来すると前の受け口が使えなくなるので、同じ形を 2 回（黒・光）なぞる。
 * 揺らぎは毎回同じ種から作るので、2 回とも同じ形になる
 */
final class ZeroFxRenderer {

    private ZeroFxRenderer() {
    }

    static final int RED = 0xFF2A44, PINK = 0xFF8090, DEEP = 0x780014, EMBER = 0xFF5A1E, GOLD = 0xFFC070, WHITE = 0xFFF0F0, INK = 0x080004, CRIMSON_INK = 0x2A0008;

    /** 光の面は裏から見えるよう両面、黒い面は片面（裏表の向きを問わない描き方なので） */
    private static boolean twoSided;

    static void render(RulerFxEntity fx, float age, int life, PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        Matrix4f m = pose.last().pose();
        float fade = Mth.clamp((life - age) / 10.0F, 0.0F, 1.0F);
        for (int pass = 0; pass < 2; pass++) {
            boolean glow = pass == 1;
            twoSided = glow;
            VertexConsumer vc = buffers.getBuffer(glow ? RenderType.lightning() : RenderType.debugQuads());
            switch (fx.kind()) {
                case RulerFxEntity.Z_BOLT -> bolt(vc, m, fx, cam, age, fade, glow);
                case RulerFxEntity.Z_TIDE -> tide(vc, m, fx, cam, age, fade, glow);
                case RulerFxEntity.Z_TWISTER -> {
                    float grow = Mth.clamp(age / (fx.mode() + 10.0F), 0.0F, 1.0F);
                    funnel(vc, m, Vec3.ZERO, cam, age, fx.size() * (0.15F + 0.85F * grow), fade * Mth.clamp(age / 6.0F, 0.0F, 1.0F), glow, fx.getId());
                }
                case RulerFxEntity.Z_FLAME -> flame(vc, m, fx, cam, age, fade, glow);
                case RulerFxEntity.Z_CROSS -> cross(vc, m, fx, cam, age, fade, glow);
                case RulerFxEntity.Z_RING -> flameRing(vc, m, fx, cam, age, glow);
                case RulerFxEntity.Z_TEMPEST -> tempest(vc, m, fx, cam, age, fade, glow);
                case RulerFxEntity.Z_HEAVEN -> heaven(vc, m, fx, cam, age, life, fade, glow);
                case RulerFxEntity.Z_DIGIT -> digit(vc, m, fx, cam, age, fade, glow);
                case RulerFxEntity.Z_SPIKE -> spike(vc, m, fx, cam, age, fade, glow);
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ 赫雷: 赤黒い落雷

    /** 空から地面への太い稲妻（黒いまとい・赤・桃・白い芯）と枝、2 Tick ごとに形が変わってちらつく。地面に焦げ跡・広がる輪・赤熱したひび */
    private static void bolt(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade, boolean glow) {
        int tick = (int) age;
        long seed = fx.getId() * 7919L + tick / 2;
        float a = fade * (age < 3 ? 1.0F : tick % 3 == 1 ? 0.35F : 0.8F);
        Random r = new Random(fx.getId() * 31L);
        Vec3 top = new Vec3(r.nextGaussian() * 2.0, fx.size(), r.nextGaussian() * 2.0);
        Vec3[] main = jag(seed, top, Vec3.ZERO, 14, 1.4, false);
        if (!glow) {
            poly(vc, m, main, cam, 1.8F, INK, (int) (120 * a));
            disc(vc, m, new Vec3(0, 0.03, 0), 0.0F, 3.0F, 24, INK, (int) (170 * fade), 0);
            return;
        }
        poly(vc, m, main, cam, 1.1F, RED, (int) (210 * a));
        poly(vc, m, main, cam, 0.45F, PINK, (int) (230 * a));
        poly(vc, m, main, cam, 0.16F, WHITE, (int) (255 * a));
        Random br = new Random(seed ^ 0x5DEECE66DL);
        for (int k = 0; k < 4; k++) {                                                           // 枝
            Vec3 from = main[2 + br.nextInt(main.length - 4)];
            Vec3 to = from.add(br.nextGaussian() * 3.0, -2.0 - br.nextDouble() * 4.0, br.nextGaussian() * 3.0);
            Vec3[] b = jag(seed * 13 + k, from, to, 5, 0.6, false);
            poly(vc, m, b, cam, 0.4F, RED, (int) (170 * a));
            poly(vc, m, b, cam, 0.1F, WHITE, (int) (200 * a));
        }
        rib(vc, m, Vec3.ZERO, new Vec3(0, 2.0, 0), cam, 2.4F, RED, (int) (110 * a));            // 着いた所の光
        float sw = Mth.clamp(age / 8.0F, 0.0F, 1.0F);
        disc(vc, m, new Vec3(0, 0.06, 0), 3.8F * sw, 3.8F * sw + 0.35F, 32, RED, (int) (230 * (1 - sw)), (int) (230 * (1 - sw)));
        for (int k = 0; k < 7; k++) {                                                           // 赤熱したひび
            double ang = Math.PI * 2 * k / 7 + r.nextDouble();
            Vec3[] crack = jag(fx.getId() * 101L + k, new Vec3(0, 0.05, 0), new Vec3(Math.cos(ang) * 3.4, 0.05, Math.sin(ang) * 3.4), 4, 0.35, true);
            poly(vc, m, crack, cam, 0.14F, EMBER, (int) (210 * fade));
            poly(vc, m, crack, cam, 0.05F, GOLD, (int) (230 * fade));
        }
    }

    // ------------------------------------------------------------------ 終の大波

    /** 波の断面（前からの距離, 高さの割合）: 後ろの裾 → 背 → 波頭 → 巻き込む唇 → 前の面 → 前の裾 */
    private static final double[][] WAVE = {{-4.0, 0.0}, {-2.6, 0.45}, {-1.3, 0.8}, {-0.3, 1.0}, {0.5, 0.95}, {0.9, 0.78}, {0.85, 0.6}, {0.55, 0.4}, {0.3, 0.0}};

    /** 黒から暗い紅への巻く大波の壁。波頭に赤い光の筋、唇に桃色のしぶき、前の面を流れ落ちる赤い筋、裾の赤い光。切れ目の両脇に白い光の縁 */
    private static void tide(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade, boolean glow) {
        float half = fx.size(), gap = fx.spin(), warn = Math.max(1, fx.mode());
        Vec3 dir = new Vec3(Math.cos(fx.yaw()), 0, Math.sin(fx.yaw())), side = new Vec3(-dir.z, 0, dir.x);
        float run = Math.max(0.0F, age - warn), len = LostCatastropheEntity.TIDE_LEN;
        float dist = Math.min(run, len);
        float h = age < warn ? 0.6F + 1.2F * age / warn : (float) Math.min(LostCatastropheEntity.TIDE_H, 1.8 + run * 0.4);
        if (run > len) h *= Math.max(0.0F, 1.0F - (run - len) / 12.0F);                       // 押し寄せ切ると崩れる
        if (h <= 0.05F) return;
        float a = Mth.clamp(age / 6.0F, 0.0F, 1.0F) * (run > len ? Math.max(0.0F, 1.0F - (run - len) / 12.0F) : 1.0F);
        double gapW = LostCatastropheEntity.TIDE_GAP;
        for (double l = -half; l < half - 1.0e-3; l += 1.0) {
            double l1 = Math.min(half, l + 1.0);
            if (Math.abs((l + l1) / 2 - gap) < gapW) continue;
            for (int k = 0; k < WAVE.length - 1; k++) {
                Vec3 p00 = wave(dir, side, dist, l, WAVE[k], h, age), p01 = wave(dir, side, dist, l1, WAVE[k], h, age);
                Vec3 p10 = wave(dir, side, dist, l, WAVE[k + 1], h, age), p11 = wave(dir, side, dist, l1, WAVE[k + 1], h, age);
                if (!glow) {
                    boolean high = WAVE[k][1] > 0.7 && WAVE[k + 1][1] > 0.7;
                    gquad(vc, m, p00, p01, p11, p10, high ? CRIMSON_INK : INK, (int) (230 * a), high ? DEEP : CRIMSON_INK, (int) (220 * a));
                }
            }
            if (!glow) continue;
            rib(vc, m, wave(dir, side, dist, l, WAVE[3], h, age), wave(dir, side, dist, l1, WAVE[3], h, age), cam, 0.4F, RED, (int) (200 * a));
            rib(vc, m, wave(dir, side, dist, l, WAVE[5], h, age), wave(dir, side, dist, l1, WAVE[5], h, age), cam, 0.28F, 0xFFB0B8, (int) (190 * a));
            rib(vc, m, wave(dir, side, dist, l, WAVE[8], h, age), wave(dir, side, dist, l1, WAVE[8], h, age), cam, 0.6F, RED, (int) (150 * a));
            double ph = (age * 0.09 + hash((int) Math.floor(l)) * 0.37) % 1.0;                   // 前の面を流れ落ちる筋
            double[] s0 = lerp(WAVE[5], WAVE[8], ph), s1 = lerp(WAVE[5], WAVE[8], Math.min(1.0, ph + 0.25));
            rib(vc, m, wave(dir, side, dist, l + 0.5, s0, h, age), wave(dir, side, dist, l + 0.5, s1, h, age), cam, 0.12F, PINK, (int) (170 * a));
        }
        if (glow) {
            for (int sd = -1; sd <= 1; sd += 2) {                                               // 切れ目の両脇の白い光の縁
                Vec3 at = side.scale(gap + sd * gapW).add(dir.scale(dist));
                rib(vc, m, at, at.add(0, h + 0.8, 0), cam, 0.35F, WHITE, (int) (220 * a));
            }
        }
    }

    private static Vec3 wave(Vec3 dir, Vec3 side, double dist, double l, double[] p, double h, float age) {
        double wob = Math.sin(l * 0.6 + age * 0.35) * 0.25 + Math.sin(l * 1.7 - age * 0.5) * 0.12;
        return dir.scale(dist + p[0] * (h / 4.0 + 0.4) + wob * p[1]).add(side.scale(l)).add(0, p[1] * h * (1 + wob * 0.3), 0);
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    // ------------------------------------------------------------------ 禍つ嵐・黙示の大嵐: 竜巻

    /** 黒い漏斗（螺旋の縞・曲がってうねる）・縞の縁の赤い筋・赤い芯・天辺の赤い輪・地面の削る輪と巻き上がる黒い土煙・ときどき中を走る赤い稲妻 */
    static void funnel(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 cam, float age, float H, float a, boolean glow, long seed) {
        if (H < 0.3F || a <= 0.0F) return;
        int levels = 12, segs = 14;
        Vec3[][] ring = new Vec3[levels + 1][segs];
        double lean = (seed * 0.7) % (Math.PI * 2);
        for (int i = 0; i <= levels; i++) {
            double y = H * i / levels, rad = 0.5 + y * 0.36, rot = age * 0.45 + y * 0.35;
            double bend = Math.sin(age * 0.07 + seed) * 0.08 * y * y / H;
            for (int j = 0; j < segs; j++) {
                double ang = rot + Math.PI * 2 * j / segs;
                ring[i][j] = base.add(bend * Math.cos(lean) + Math.cos(ang) * rad, y, bend * Math.sin(lean) + Math.sin(ang) * rad);
            }
        }
        for (int i = 0; i < levels; i++) {
            for (int j = 0; j < segs; j++) {
                int j1 = (j + 1) % segs;
                boolean band = (j + i) % 4 < 2;
                if (!glow) gquad(vc, m, ring[i][j], ring[i][j1], ring[i + 1][j1], ring[i + 1][j], INK, (int) ((band ? 150 : 60) * a), CRIMSON_INK, (int) ((band ? 130 : 45) * a));
                else if ((j + i) % 4 == 0) rib(vc, m, ring[i][j], ring[i + 1][j1], cam, 0.18F, RED, (int) (170 * a));
            }
        }
        double R = LostCatastropheEntity.STORM_R;
        if (!glow) {
            disc(vc, m, base.add(0, 0.04, 0), 0.0F, 5.5F, 24, INK, (int) (130 * a), 0);
            return;
        }
        rib(vc, m, base, base.add(0, H * 0.9, 0), cam, 0.4F, 0xFF6070, (int) (120 * a));
        for (int j = 0; j < segs; j++) rib(vc, m, ring[levels][j], ring[levels][(j + 1) % segs], cam, 0.25F, RED, (int) (130 * a));
        disc(vc, m, base.add(0, 0.06, 0), (float) R - 0.3F, (float) R, 32, RED, (int) (190 * a), (int) (190 * a));
        disc(vc, m, base.add(0, 0.05, 0), 0.0F, (float) R, 24, RED, (int) (40 * a), (int) (10 * a));
        if (Math.floorMod((int) age + (int) seed, 9) < 2) {
            Vec3[] b = jag(seed * 31 + (int) age, base.add(0, H, 0), base.add(0, 0.2, 0), 8, 1.0, false);
            poly(vc, m, b, cam, 0.5F, RED, (int) (220 * a));
            poly(vc, m, b, cam, 0.14F, WHITE, (int) (255 * a));
        }
    }

    /** 黙示の大嵐: 4 本の竜巻（サーバーと同じ式で渦を巻いて寄る）と、空を覆う黒い大渦の雲（中に赤い稲妻が走り、目に赤い輪） */
    private static void tempest(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade, boolean glow) {
        int delay = fx.mode();
        float from = fx.size(), t = age - delay;
        float k = Mth.clamp(t / LostCatastropheEntity.TEMPEST_LEN, 0.0F, 1.0F);
        double r = from - (from - LostCatastropheEntity.TEMPEST_TO) * k;
        float grow = Mth.clamp(age / (delay + 10.0F), 0.0F, 1.0F);
        float a = fade * Mth.clamp(age / 10.0F, 0.0F, 1.0F);
        for (int i = 0; i < 4; i++) {
            double an = fx.yaw() + i * Math.PI / 2 + Math.max(0.0F, t) * 0.025;
            funnel(vc, m, new Vec3(Math.cos(an) * r, 0, Math.sin(an) * r), cam, age, 11.0F * grow, a, glow, fx.getId() * 17L + i);
        }
        float cy = 22.0F, R = from + 8.0F;
        int rings = 6, segs = 40;
        for (int ri = 0; ri < rings; ri++) {
            float r0 = R * ri / rings + 2.5F, r1 = R * (ri + 1) / rings + 2.5F;
            float y0 = cy - (rings - ri) * 0.5F, y1 = cy - (rings - ri - 1) * 0.5F;
            double rot = age * 0.01 * (1.5 - ri * 0.15);
            for (int j = 0; j < segs; j++) {
                double b0 = rot + Math.PI * 2 * j / segs, b1 = rot + Math.PI * 2 * (j + 1) / segs;
                double noise = Math.sin(j * 0.9 + ri * 1.3 + age * 0.02) * 0.5 + 0.5;
                Vec3 p00 = new Vec3(Math.cos(b0) * r0, y0, Math.sin(b0) * r0), p01 = new Vec3(Math.cos(b1) * r0, y0, Math.sin(b1) * r0);
                Vec3 p10 = new Vec3(Math.cos(b0) * r1, y1, Math.sin(b0) * r1), p11 = new Vec3(Math.cos(b1) * r1, y1, Math.sin(b1) * r1);
                float outer = ri == rings - 1 ? 0.0F : 1.0F;
                if (!glow) gquad(vc, m, p00, p01, p11, p10, INK, (int) ((110 + 90 * noise) * a * grow), CRIMSON_INK, (int) ((90 + 80 * noise) * a * grow * outer));
                else if (noise > 0.85) gquad(vc, m, p00, p01, p11, p10, RED, (int) (45 * a * grow), DEEP, 0);
            }
        }
        if (!glow) return;
        disc(vc, m, new Vec3(0, cy - 3.0, 0), 2.0F, 2.6F, 24, RED, (int) (200 * a * grow), (int) (200 * a * grow));
        Random br = new Random(fx.getId() * 4099L + (int) (age / 3));
        if (br.nextInt(3) == 0) {                                                               // 雲の中を走る赤い稲妻
            for (int v = 0; v < 2; v++) {
                double an = br.nextDouble() * Math.PI * 2;
                Vec3 s = new Vec3(Math.cos(an) * 4.0, cy - 2.6, Math.sin(an) * 4.0);
                Vec3 e = new Vec3(Math.cos(an + br.nextGaussian() * 0.5) * R * 0.9, cy - 0.6, Math.sin(an + br.nextGaussian() * 0.5) * R * 0.9);
                Vec3[] vein = jag(br.nextLong(), s, e, 10, 1.6, true);
                poly(vc, m, vein, cam, 0.45F, RED, (int) (220 * a));
                poly(vc, m, vein, cam, 0.12F, WHITE, (int) (255 * a));
            }
        }
    }

    // ------------------------------------------------------------------ 煉獄焔・燃える床

    /** 扇（または丸）いっぱいに揺れる焔の舌（芯が金・外が赤・先が黒い煙）。噴き上がる時は高く、その後は低く燃え続ける。床に焦げ跡 */
    private static void flame(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade, boolean glow) {
        float R = fx.size(), w = fx.spin(), e = age - fx.mode();
        if (e < 0) return;                                                                      // 予告は扇（SECTOR）が描く
        double c = fx.yaw();
        float burst = e < 14 ? 1.0F - e / 14.0F : 0.0F, base = Math.min(1.0F, e / 3.0F);
        boolean full = w >= Math.PI * 2 - 0.01;
        if (!glow) {
            int segs = Math.max(6, (int) (w * 8));
            for (int i = 0; i < segs; i++) {
                double a0 = c - w / 2 + w * i / segs, a1 = c - w / 2 + w * (i + 1) / segs;
                float in = full ? 0.0F : 1.0F;
                gquad(vc, m, flat(a0, in), flat(a1, in), flat(a1, R), flat(a0, R), INK, (int) (160 * fade * base), INK, (int) (60 * fade * base));
            }
        }
        Random rnd = new Random(fx.getId() * 977L);
        for (double d = full ? 0.8 : 2.0; d < R; d += 1.5) {
            int n = Math.max(1, (int) (d * w / 1.4));
            for (int k = 0; k < n; k++) {
                double ang = c - w / 2 + w * (k + 0.1 + rnd.nextDouble() * 0.8) / n;
                double dd = d + rnd.nextDouble() * 1.2;
                float ph = rnd.nextFloat() * 10.0F;
                if (dd > R) continue;
                float hgt = (float) ((1.0 + 0.45 * Math.sin(age * 0.5 + ph)) * (1.0 + 4.5 * burst * (1 - dd / R * 0.5)));
                tongue(vc, m, new Vec3(Math.cos(ang) * dd, 0, Math.sin(ang) * dd), cam, hgt, 0.6F + 0.35F * burst, age, ph, fade * base, glow);
            }
        }
    }

    private static Vec3 flat(double ang, double r) {
        return new Vec3(Math.cos(ang) * r, 0.03, Math.sin(ang) * r);
    }

    /** カメラを向いた、3 節で揺れながら先細る焔の舌 */
    static void tongue(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 cam, float h, float w, float age, float ph, float a, boolean glow) {
        Vec3 toCam = cam.subtract(p).multiply(1, 0, 1);
        Vec3 side = toCam.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : new Vec3(-toCam.z, 0, toCam.x).normalize();
        Vec3 prevL = p.add(side.scale(w / 2)), prevR = p.subtract(side.scale(w / 2));
        for (int s = 1; s <= 3; s++) {
            double t = s / 3.0, sway = Math.sin(age * 0.7 + ph + s) * 0.18 * s;
            Vec3 c = p.add(side.scale(sway)).add(0, h * t, 0);
            double ww = w / 2 * (1 - t * 0.92);
            Vec3 L = c.add(side.scale(ww)), Rr = c.subtract(side.scale(ww));
            if (glow) {
                int lo = s == 1 ? GOLD : s == 2 ? EMBER : RED, hi = s == 1 ? EMBER : s == 2 ? RED : DEEP;
                gquad(vc, m, prevR, prevL, L, Rr, lo, (int) (210 * a * (1.1 - t * 0.3)), hi, (int) (200 * a * (1.0 - t * 0.85)));
            } else if (s == 3) {                                                                // 先から昇る黒い煙
                Vec3 top = c.add(side.scale(sway)).add(0, h * 0.6, 0);
                gquad(vc, m, Rr, L, top.add(side.scale(w * 0.4)), top.subtract(side.scale(w * 0.4)), INK, (int) (110 * a), INK, 0);
            }
            prevL = L;
            prevR = Rr;
        }
    }

    // ------------------------------------------------------------------ 黒雷の十字架

    /** 予告は細い赤い線が伸び、明けると 4 本の太い雷の腕が回る。腕の後ろに赤い残像と薙いだ扇、先に光の玉、床に焼けた筋。真ん中に黒い円と回る赤い紋 */
    private static void cross(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade, boolean glow) {
        float L = fx.size(), warn = fx.mode();
        double spin = fx.spin();
        boolean live = age >= warn;
        double ang0 = fx.yaw() + spin * Math.max(0.0F, age - warn);
        float grow = live ? 1.0F : Mth.clamp(age / Math.max(1.0F, warn), 0.0F, 1.0F);
        float a = fade;
        if (!glow) disc(vc, m, new Vec3(0, 0.04, 0), 0.0F, 3.4F, 32, INK, (int) (190 * a), (int) (60 * a));
        else {
            disc(vc, m, new Vec3(0, 0.07, 0), 2.5F, 2.7F, 40, RED, (int) (220 * a), (int) (220 * a));
            disc(vc, m, new Vec3(0, 0.07, 0), 3.05F, 3.15F, 40, WHITE, (int) (150 * a), (int) (150 * a));
            for (int k = 0; k < 8; k++) {
                double an = Math.PI * 2 * k / 8 - age * 0.05 * Math.signum(spin);
                rib(vc, m, new Vec3(Math.cos(an) * 0.6, 0.08, Math.sin(an) * 0.6), new Vec3(Math.cos(an) * 2.5, 0.08, Math.sin(an) * 2.5), cam, k % 2 == 0 ? 0.16F : 0.07F, RED, (int) (200 * a));
            }
        }
        for (int i = 0; i < 4; i++) {
            double an = ang0 + i * Math.PI / 2;
            Vec3 dir = new Vec3(Math.cos(an), 0, Math.sin(an));
            Vec3 from = new Vec3(0, 1.0, 0), to = dir.scale(L * grow).add(0, 1.0, 0);
            if (!live) {
                if (glow) rib(vc, m, from, to, cam, 0.12F, RED, (int) (150 * a * (0.6 + 0.4 * Math.sin(age * 0.8))));
                continue;
            }
            Vec3[] arm = jag(fx.getId() * 131L + i * 7L + (int) age, from, to, 16, 0.55, false);
            if (!glow) {
                poly(vc, m, arm, cam, 1.9F, INK, (int) (130 * a));
                continue;
            }
            poly(vc, m, arm, cam, 1.0F, RED, (int) (210 * a));
            poly(vc, m, arm, cam, 0.4F, PINK, (int) (220 * a));
            poly(vc, m, arm, cam, 0.13F, WHITE, (int) (255 * a));
            for (int k = 1; k <= 4; k++) {                                                      // 残像
                double pa = an - spin * k * 3.0;
                rib(vc, m, from, new Vec3(Math.cos(pa) * L, 1.0, Math.sin(pa) * L), cam, 0.6F, RED, (int) (90 * a * (1 - k / 5.0)));
            }
            double pa = an - spin * 12.0;                                                       // 薙いだ扇
            gquad(vc, m, from, from, to, new Vec3(Math.cos(pa) * L, 1.0, Math.sin(pa) * L), RED, 0, RED, (int) (55 * a));
            rib(vc, m, to.subtract(dir.scale(0.6)), to.add(dir.scale(0.6)), cam, 1.3F, PINK, (int) (200 * a));
            rib(vc, m, dir.add(0, 0.05, 0), dir.scale(L).add(0, 0.05, 0), cam, 0.25F, EMBER, (int) (140 * a));
        }
    }

    // ------------------------------------------------------------------ 焔の大海嘯・赫焉天墜の輪の波

    /** 広がる低い焔の壁（金の根元から赤へ消える二重の焔・白熱した裾・黒い根・後ろに焼けた床） */
    private static void flameRing(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, boolean glow) {
        float t = age - fx.mode();
        if (t < 0) return;
        float r = fx.yaw() + fx.spin() * t, max = fx.size();
        if (r > max + 0.5F) return;
        float a = Mth.clamp((max + 0.5F - r) / 3.0F, 0.0F, 1.0F) * Mth.clamp(t / 3.0F, 0.0F, 1.0F);
        int n = Mth.clamp((int) (r * 3), 24, 120);
        float in = Math.max(0.0F, r - 2.0F);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 p0 = new Vec3(Math.cos(a0) * r, 0.02, Math.sin(a0) * r), p1 = new Vec3(Math.cos(a1) * r, 0.02, Math.sin(a1) * r);
            float h0 = (float) (1.1 + 0.4 * Math.sin(i * 1.7 + age * 0.9)), h1 = (float) (1.1 + 0.4 * Math.sin((i + 1) * 1.7 + age * 0.9));
            if (!glow) {
                gquad(vc, m, p0, p1, p1.add(0, 0.6, 0), p0.add(0, 0.6, 0), INK, (int) (200 * a), CRIMSON_INK, 0);
                gquad(vc, m, flat(a0, in), flat(a1, in), p1, p0, INK, 0, INK, (int) (120 * a));
                continue;
            }
            gquad(vc, m, p0, p1, p1.add(0, h1, 0), p0.add(0, h0, 0), GOLD, (int) (230 * a), RED, 0);
            gquad(vc, m, p0, p1, p1.add(0, h1 * 1.6, 0), p0.add(0, h0 * 1.6, 0), RED, (int) (140 * a), DEEP, 0);
            rib(vc, m, p0.add(0, 0.05, 0), p1.add(0, 0.05, 0), cam, 0.22F, WHITE, (int) (200 * a));
        }
    }

    // ------------------------------------------------------------------ 赫焉天墜

    /**
     * 溜め: 空に黒い大渦（5 本の螺旋の腕・赤い筋・白い芯が育つ）、文字盤の縁へ降りる光の線、墜ちる直前に白い導きの線。
     * 墜ちる: 黒い殻に巻かれた赤い焔の柱（白い芯・巻きつく稲妻、細りながら消える）、地面に黒い跡と赤熱したひび、広がる白い衝撃の輪と光の半球
     */
    private static void heaven(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life, float fade, boolean glow) {
        float R = fx.size(), warn = fx.mode(), sky = 26.0F;
        Vec3 top = new Vec3(0, sky, 0);
        if (age < warn) {
            float c = age / warn, vr = 3.0F + 7.0F * c;
            for (int arm = 0; arm < 5; arm++) {
                Vec3 prev = null;
                for (int s = 0; s <= 16; s++) {
                    double tt = s / 16.0, rr = vr * tt, an = arm * Math.PI * 2 / 5 + tt * 3.5 - age * 0.15;
                    Vec3 p = top.add(Math.cos(an) * rr, -tt * 1.5, Math.sin(an) * rr);
                    if (prev != null) {
                        if (!glow) rib(vc, m, prev, p, cam, (float) (1.6 * tt + 0.4), INK, (int) (200 * fade));
                        else rib(vc, m, prev, p, cam, (float) (0.5 * tt + 0.15), RED, (int) (210 * fade * (1 - tt * 0.5)));
                    }
                    prev = p;
                }
            }
            if (!glow) {
                disc(vc, m, top, 0.0F, vr, 32, INK, (int) (210 * fade), (int) (50 * fade));
                return;
            }
            rib(vc, m, top.add(0, -0.6, 0), top.add(0, 0.6, 0), cam, 3.0F + 3.0F * c, RED, (int) (160 * fade));
            rib(vc, m, top.add(0, -0.6, 0), top.add(0, 0.6, 0), cam, 1.5F + 2.0F * c, WHITE, (int) (230 * fade));
            for (int k = 0; k < 6; k++) {
                double an = k * Math.PI / 3 + age * 0.05;
                rib(vc, m, top, new Vec3(Math.cos(an) * R, 0.1, Math.sin(an) * R), cam, 0.12F, RED, (int) (130 * fade * c));
            }
            if (age > warn - 8 && ((int) age & 1) == 0) rib(vc, m, top, Vec3.ZERO, cam, 0.3F, WHITE, (int) (210 * fade));
            return;
        }
        float f = age - warn;
        float shrink = 1.0F - Mth.clamp(f / 25.0F, 0.0F, 1.0F);
        float pr = R * 0.7F * (0.3F + 0.7F * shrink);
        int segs = 20;
        for (int j = 0; j < segs; j++) {
            double a0 = Math.PI * 2 * j / segs, a1 = Math.PI * 2 * (j + 1) / segs;
            if (!glow) {
                float sr = pr * 1.15F;
                boolean band = Math.floorMod(j + (int) (age * 1.5), 4) < 2;
                Vec3 b0 = new Vec3(Math.cos(a0) * sr, 0, Math.sin(a0) * sr), b1 = new Vec3(Math.cos(a1) * sr, 0, Math.sin(a1) * sr);
                gquad(vc, m, b0, b1, b1.add(0, sky, 0), b0.add(0, sky, 0), INK, (int) ((band ? 170 : 60) * shrink * fade), INK, (int) ((band ? 120 : 30) * shrink * fade));
                continue;
            }
            Vec3 o0 = new Vec3(Math.cos(a0) * pr, 0, Math.sin(a0) * pr), o1 = new Vec3(Math.cos(a1) * pr, 0, Math.sin(a1) * pr);
            gquad(vc, m, o0, o1, o1.add(0, sky, 0), o0.add(0, sky, 0), RED, (int) (200 * shrink * fade), DEEP, (int) (120 * shrink * fade));
            Vec3 i0 = o0.scale(0.35), i1 = o1.scale(0.35);
            gquad(vc, m, i0, i1, i1.add(0, sky, 0), i0.add(0, sky, 0), WHITE, (int) (230 * shrink * fade), PINK, (int) (150 * shrink * fade));
        }
        if (!glow) {
            disc(vc, m, new Vec3(0, 0.04, 0), 0.0F, R * 1.1F, 40, INK, (int) (210 * fade), (int) (40 * fade));
            return;
        }
        for (int h = 0; h < 3; h++) {                                                           // 柱に巻きつく稲妻
            Vec3[] helix = new Vec3[17];
            Random jr = new Random(fx.getId() * 53L + h * 7L + (int) age);
            for (int s = 0; s <= 16; s++) {
                double y = sky * s / 16.0, an = y * 0.4 + age * 0.6 + h * 2.1, rr = pr * 1.2 + jr.nextGaussian() * 0.3;
                helix[s] = new Vec3(Math.cos(an) * rr, y, Math.sin(an) * rr);
            }
            poly(vc, m, helix, cam, 0.35F, RED, (int) (220 * shrink * fade));
            poly(vc, m, helix, cam, 0.1F, WHITE, (int) (255 * shrink * fade));
        }
        for (int k = 0; k < 12; k++) {                                                          // 赤熱したひび
            double an = Math.PI * 2 * k / 12 + (fx.getId() % 7) * 0.3;
            Vec3[] crack = jag(fx.getId() * 211L + k, new Vec3(0, 0.05, 0), new Vec3(Math.cos(an) * R * 1.7, 0.05, Math.sin(an) * R * 1.7), 6, 0.6, true);
            poly(vc, m, crack, cam, 0.2F, EMBER, (int) (200 * fade));
            poly(vc, m, crack, cam, 0.07F, GOLD, (int) (230 * fade));
        }
        if (f < 14) {                                                                           // 広がる衝撃の輪
            float sr = R + f * 1.4F, k = 1.0F - f / 14.0F;
            disc(vc, m, new Vec3(0, 0.08, 0), sr - 0.7F, sr, 48, RED, 0, (int) (220 * k));
            disc(vc, m, new Vec3(0, 0.09, 0), sr - 0.25F, sr, 48, WHITE, (int) (200 * k), (int) (200 * k));
        }
        if (f < 12) {                                                                           // 光の半球
            float dr = R * (0.6F + f * 0.15F), k = 1.0F - f / 12.0F;
            for (int lat = 1; lat <= 4; lat++) {
                double phi = lat * Math.PI / 10;
                float rr = (float) (dr * Math.cos(phi));
                disc(vc, m, new Vec3(0, dr * Math.sin(phi), 0), rr - 0.12F, rr + 0.12F, 40, lat % 2 == 0 ? WHITE : RED, (int) (170 * k), (int) (170 * k));
            }
        }
    }

    // ------------------------------------------------------------------ 結晶槍の 7 セグの数字

    /** 地面から勢いよく突き出す光る 7 セグの 0 か 1（カメラへ向く。黒い縁取りに色の線と白い芯） */
    private static void digit(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade, boolean glow) {
        float rise = ease(Math.min(1.0F, age / 3.0F));
        float H = fx.size() * (0.2F + 0.8F * rise), W = H * 0.55F, t = H * 0.13F;
        Vec3 toCam = cam.multiply(1, 0, 1);
        Vec3 right = toCam.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : new Vec3(-toCam.z, 0, toCam.x).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        boolean one = fx.mode() == 1;
        int color = fx.color();
        float[][] bars = one ? new float[][]{{W - t, H / 2 + 0.02F, W, H}, {W - t, 0, W, H / 2 - 0.02F}}
                : new float[][]{{0, H - t, W, H}, {0, 0, W, t}, {0, H / 2, t, H}, {0, 0, t, H / 2}, {W - t, H / 2, W, H}, {W - t, 0, W, H / 2}};
        for (float[] b : bars) {
            Vec3 p00 = right.scale(b[0] - W / 2).add(up.scale(b[1])), p10 = right.scale(b[2] - W / 2).add(up.scale(b[1]));
            Vec3 p11 = right.scale(b[2] - W / 2).add(up.scale(b[3])), p01 = right.scale(b[0] - W / 2).add(up.scale(b[3]));
            if (!glow) {
                Vec3 gx = right.scale(t * 0.35), gy = up.scale(t * 0.35);
                gquad(vc, m, p00.subtract(gx).subtract(gy), p10.add(gx).subtract(gy), p11.add(gx).add(gy), p01.subtract(gx).add(gy), INK, (int) (200 * fade), INK, (int) (200 * fade));
                continue;
            }
            gquad(vc, m, p00, p10, p11, p01, color, (int) (230 * fade), color, (int) (230 * fade));
            Vec3 cx = right.scale((b[2] - b[0]) * 0.3), cy = up.scale((b[3] - b[1]) * 0.3);   // 白い芯
            Vec3 c = p00.add(p11).scale(0.5);
            gquad(vc, m, c.subtract(cx).subtract(cy), c.add(cx).subtract(cy), c.add(cx).add(cy), c.subtract(cx).add(cy), WHITE, (int) (200 * fade), WHITE, (int) (200 * fade));
        }
        if (glow && age < 4) disc(vc, m, new Vec3(0, 0.05, 0), 0.0F, W, 16, color, (int) (180 * (1 - age / 4)), 0);   // 突き出した所の光
    }

    // ------------------------------------------------------------------ 結晶槍の結晶の房

    /**
     * 地面から勢いよく突き出す結晶の房: 太い 1 本と、斜めに傾いた細い 2〜3 本。どれも先の尖った六角柱（面ごとに明暗を付けた半透明の光、白い稜線、
     * 先が白く光る）。根元に黒い影と光の輪。突き出して止まり、終わりに薄れる
     */
    private static void spike(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade, boolean glow) {
        float rise = ease(Math.min(1.0F, age / 2.5F));
        float H = fx.size();
        int color = fx.color();
        java.util.Random r = new java.util.Random(fx.getId() * 6271L);
        if (!glow) {
            disc(vc, m, new Vec3(0, 0.03, 0), 0.0F, H * 0.55F, 16, INK, (int) (160 * fade), 0);
        } else if (age < 5) {
            disc(vc, m, new Vec3(0, 0.05, 0), H * 0.2F, H * 0.2F + 0.15F + age * 0.2F, 20, color, (int) (200 * (1 - age / 5)), 0);
        }
        int count = 3 + r.nextInt(2);
        for (int i = 0; i < count; i++) {
            boolean main = i == 0;
            float h = (main ? H : H * (0.45F + r.nextFloat() * 0.3F)) * rise;
            float rad = main ? H * 0.16F : H * (0.07F + r.nextFloat() * 0.04F);
            double lean = main ? r.nextGaussian() * 0.08 : 0.35 + r.nextDouble() * 0.35, dir = r.nextDouble() * Math.PI * 2;
            Vec3 axis = new Vec3(Math.cos(dir) * Math.sin(lean), Math.cos(lean), Math.sin(dir) * Math.sin(lean));
            Vec3 base = main ? Vec3.ZERO : new Vec3(Math.cos(dir) * rad, 0, Math.sin(dir) * rad);
            prism(vc, m, base, axis, rad, h, (float) (r.nextDouble() * Math.PI), color, fade, glow);
        }
    }

    /** 先の尖った六角柱（base から axis の向きへ、太さ rad・長さ h。先の 3 割が尖る） */
    private static void prism(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 axis, float rad, float h, float twist, int color, float fade, boolean glow) {
        if (h < 0.02F) return;
        Vec3 u = axis.cross(Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), v = axis.cross(u).normalize();
        float body = h * 0.7F;
        Vec3 topC = base.add(axis.scale(body)), tip = base.add(axis.scale(h));
        Vec3[] lo = new Vec3[6], hi = new Vec3[6];
        for (int k = 0; k < 6; k++) {
            double a = twist + Math.PI * 2 * k / 6;
            Vec3 off = u.scale(Math.cos(a) * rad).add(v.scale(Math.sin(a) * rad));
            lo[k] = base.add(off);
            hi[k] = topC.add(off);
        }
        int light = 0xFFFFFF;
        for (int k = 0; k < 6; k++) {
            int k1 = (k + 1) % 6;
            float shade = k % 2 == 0 ? 1.0F : 0.75F;                                         // 面ごとの明暗で切子に見せる
            int c = shadeColor(color, shade);
            if (!glow) {
                gquad(vc, m, lo[k], lo[k1], hi[k1], hi[k], INK, (int) (70 * fade), INK, (int) (40 * fade));
                continue;
            }
            gquad(vc, m, lo[k], lo[k1], hi[k1], hi[k], c, (int) (150 * fade), c, (int) (190 * fade));                 // 柱の面
            gquad(vc, m, hi[k], hi[k1], tip, tip, c, (int) (190 * fade), light, (int) (230 * fade));                  // 尖った先（先ほど白く）
        }
        if (!glow) return;
        for (int k = 0; k < 6; k += 2) {                                                      // 稜線（細い白い帯を一つおきの縁に）
            Vec3 e0 = lo[k], e1 = hi[k];
            Vec3 w = u.scale(0.012).add(v.scale(0.012));
            gquad(vc, m, e0.subtract(w), e0.add(w), e1.add(w), e1.subtract(w), light, (int) (160 * fade), light, (int) (220 * fade));
            gquad(vc, m, e1.subtract(w), e1.add(w), tip.add(w), tip.subtract(w), light, (int) (220 * fade), light, (int) (255 * fade));
        }
    }

    private static int shadeColor(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // ------------------------------------------------------------------ 描く道具

    /** a から b へ、揺らぎをつけた折れ線（両端は揺らさない）。flat なら横にだけ揺らす */
    static Vec3[] jag(long seed, Vec3 a, Vec3 b, int segs, double amp, boolean flat) {
        Random r = new Random(seed);
        Vec3 axis = b.subtract(a);
        Vec3 n = axis.normalize();
        Vec3 s1 = n.cross(Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), s2 = n.cross(s1);
        Vec3[] pts = new Vec3[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double t = i / (double) segs, k = Math.sin(Math.PI * t) * amp;
            pts[i] = a.add(axis.scale(t)).add(s1.scale(r.nextGaussian() * k));
            if (!flat) pts[i] = pts[i].add(s2.scale(r.nextGaussian() * k));
        }
        return pts;
    }

    static void poly(VertexConsumer vc, Matrix4f m, Vec3[] pts, Vec3 cam, float w, int rgb, int alpha) {
        for (int i = 0; i + 1 < pts.length; i++) rib(vc, m, pts[i], pts[i + 1], cam, w, rgb, alpha);
    }

    static void rib(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float w, int rgb, int alpha) {
        if (alpha <= 0 || w <= 0) return;
        Vec3 axis = b.subtract(a);
        Vec3 side = axis.cross(cam.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1.0e-8) return;
        side = side.normalize().scale(w / 2.0);
        gquad(vc, m, a.add(side), a.subtract(side), b.subtract(side), b.add(side), rgb, alpha, rgb, alpha);
    }

    /** 床に平らな輪（c が真ん中、内の縁 r0 は a0・外の縁 r1 は a1 の濃さ） */
    static void disc(VertexConsumer vc, Matrix4f m, Vec3 c, float r0, float r1, int segs, int rgb, int a0, int a1) {
        if (r1 <= r0 || (a0 <= 0 && a1 <= 0)) return;
        for (int i = 0; i < segs; i++) {
            double b0 = Math.PI * 2 * i / segs, b1 = Math.PI * 2 * (i + 1) / segs;
            Vec3 i0 = c.add(Math.cos(b0) * r0, 0, Math.sin(b0) * r0), i1 = c.add(Math.cos(b1) * r0, 0, Math.sin(b1) * r0);
            Vec3 o0 = c.add(Math.cos(b0) * r1, 0, Math.sin(b0) * r1), o1 = c.add(Math.cos(b1) * r1, 0, Math.sin(b1) * r1);
            gquad(vc, m, i0, i1, o1, o0, rgb, a0, rgb, a1);
        }
    }

    /** a・b（色 lo）から d・c（色 hi）への四角（a→b→c→d の順に回る） */
    static void gquad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int lo, int loA, int hi, int hiA) {
        loA = Mth.clamp(loA, 0, 255);
        hiA = Mth.clamp(hiA, 0, 255);
        if (loA == 0 && hiA == 0) return;
        vert(vc, m, a, lo, loA);
        vert(vc, m, b, lo, loA);
        vert(vc, m, c, hi, hiA);
        vert(vc, m, d, hi, hiA);
        if (!twoSided) return;
        vert(vc, m, d, hi, hiA);
        vert(vc, m, c, hi, hiA);
        vert(vc, m, b, lo, loA);
        vert(vc, m, a, lo, loA);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, int rgb, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a).endVertex();
    }

    private static int hash(int x) {
        x = (x ^ 61) ^ (x >>> 16);
        x *= 9;
        x ^= x >>> 4;
        x *= 0x27d4eb2d;
        return (x ^ (x >>> 15)) & 0xFF;
    }
}
