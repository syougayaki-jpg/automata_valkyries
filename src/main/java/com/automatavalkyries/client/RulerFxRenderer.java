package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.RulerFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * タイムルーラーの技の自作エフェクト（{@link RulerFxEntity}、2026-10-19 ユーザー指定「攻撃も自作の演出で超豪華に」）。
 * 足し算の光（白い芯・金・紫・赤）の面で描く。現れる時は広がり、終わりの 8 Tick で薄れる
 */
public class RulerFxRenderer extends EntityRenderer<RulerFxEntity> {

    public RulerFxRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(RulerFxEntity fx, Frustum frustum, double x, double y, double z) {
        return fx.distanceToSqr(x, y, z) < 160 * 160;
    }

    @Override
    public void render(RulerFxEntity fx, float yawIgnored, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = fx.tickCount + pt;
        int life = fx.life();
        if (age >= life) return;
        float grow = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        float fade = Mth.clamp((life - age) / 8.0F, 0.0F, 1.0F);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(fx.getPosition(pt));
        if (fx.kind() >= RulerFxEntity.Z_BOLT) {                                            // 零号機の天災の技（黒い面と光を別の描き方で）
            ZeroFxRenderer.render(fx, age, life, pose, buffers, cam);
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int col = fx.color();
        int r = (col >> 16) & 255, g = (col >> 8) & 255, b = col & 255;
        switch (fx.kind()) {
            case RulerFxEntity.CLOCK -> clock(vc, m, fx, cam, age, grow, fade, r, g, b);
            case RulerFxEntity.SWEEP -> sweep(vc, m, fx, cam, age, fade);
            case RulerFxEntity.PILLAR -> pillar(vc, m, fx, cam, age, life, fade, r, g, b);
            case RulerFxEntity.CUBE -> cube(vc, m, fx, cam, age, life, fade);
            case RulerFxEntity.SLASH -> slash(vc, m, fx, cam, age, life, r, g, b);
            case RulerFxEntity.BLADE -> blade(vc, m, fx, cam, age, life, r, g, b);
            case RulerFxEntity.GEAR -> gear(vc, m, fx, cam, age, life, fade, r, g, b);
            case RulerFxEntity.PENDULUM -> pendulum(vc, m, fx, cam, age, fade);
            case RulerFxEntity.HOURGLASS -> hourglass(vc, m, fx, cam, age, life, fade);
            case RulerFxEntity.SECTOR -> sector(vc, m, fx, cam, age, life);
            case RulerFxEntity.LANE -> lane(vc, m, fx, cam, age, life);
            case RulerFxEntity.CAUSAL -> {
                ClientRulerShow.causal(fx);                                                  // 止まったのが自分なら画面にも
                causal(vc, m, fx, cam, age, life);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 光の文字盤

    /**
     * 光の文字盤: 外の 2 重の輪（切れ目つき）・12 の時の目盛り（3・6・9・12 は太い）・60 の分の刻み・ローマ数字の文字盤（XII〜XI、4 は時計の慣わしの IIII）・
     * 外を逆へ回る I〜XXIV の小さな数字の帯・内を回る数字の輪・針・真ん中の光（ローマ数字は 2026-10-19 ユーザー指定「ローマ数字もふんだんに使って」）。
     * 面は見る側から右回りになるように (u, v, n) を右手系に取る（床は上から、縦は yaw の向きから見る）
     */
    private static void clock(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float grow, float fade, int r, int g, int b) {
        int mode = fx.mode();
        float R = fx.size() * (0.6F + 0.4F * grow);
        Vec3 u, v, n;
        boolean facing = (mode & RulerFxEntity.FACING) != 0;
        if (facing) {                                                                 // 縦の文字盤（yaw の向きへ面を向ける）
            float y = fx.yaw();
            n = new Vec3(Math.cos(y), 0, Math.sin(y));
            v = new Vec3(0, 1, 0);
            u = v.cross(n);
        } else {                                                                      // 床の文字盤（上から見る）
            n = new Vec3(0, 1, 0);
            u = new Vec3(1, 0, 0);
            v = new Vec3(0, 0, -1);
        }
        float rot = age * 0.01F;
        int a = (int) (200 * fade * grow);
        if ((mode & RulerFxEntity.FILL) != 0) annulus(vc, m, u, v, 0.0F, R * 0.94F, 48, 0, 0, r, g, b, (int) (a * 0.16F));   // 盤を薄く塗る（加速と減速の場）
        annulus(vc, m, u, v, R * 0.94F, R, 64, rot, 0, r, g, b, a);                    // 外の輪
        annulus(vc, m, u, v, R * 0.965F, R * 0.98F, 64, rot, 0, 255, 255, 255, a);
        annulus(vc, m, u, v, R * 0.76F, R * 0.79F, 48, -rot * 2, 6, r, g, b, (int) (a * 0.8F));   // 切れ目のある内の輪
        for (int k = 0; k < 12; k++) {                                               // 12 の時の目盛り
            double ang = Math.PI * 2 * k / 12 + rot;
            boolean big = k % 3 == 0;
            radial(vc, m, u, v, ang, R * (big ? 0.78F : 0.82F), R * 0.93F, R * (big ? 0.035F : 0.02F), r, g, b, a);
            if (big) radial(vc, m, u, v, ang, R * 0.84F, R * 0.9F, R * 0.012F, 255, 255, 255, a);
        }
        for (int k = 0; k < 60; k++) {                                               // 60 の分の刻み
            if (k % 5 == 0) continue;
            double ang = Math.PI * 2 * k / 60 + rot;
            radial(vc, m, u, v, ang, R * 0.89F, R * 0.93F, R * 0.006F, r, g, b, (int) (a * 0.7F));
        }
        for (int k = 0; k < 12; k++) {                                               // ローマ数字の文字盤（XII が上、右回り。床は外向き、縦は立てて）
            double ang = Math.PI / 2 - Math.PI * 2 * k / 12 + rot;
            Vec3 at = dir(u, v, ang).scale(R * 0.665F);
            Vec3 up = facing ? v : dir(u, v, ang);
            numeral(vc, m, HOURS[k], at, up, n, R * 0.105F, r, g, b, a);
        }
        if (R >= 6.0F) {
            annulus(vc, m, u, v, R * 1.035F, R * 1.045F, 96, -rot, 0, r, g, b, (int) (a * 0.6F));
            annulus(vc, m, u, v, R * 1.125F, R * 1.135F, 96, -rot, 0, r, g, b, (int) (a * 0.6F));
            for (int k = 0; k < 24; k++) {                                           // 外を逆へ回る I〜XXIV の帯
                double ang = Math.PI / 2 - Math.PI * 2 * k / 24 - age * 0.004F;
                numeral(vc, m, roman(k + 1), dir(u, v, ang).scale(R * 1.085F), dir(u, v, ang), n, R * 0.04F, 255, 240, 200, (int) (a * 0.8F));
            }
            for (int k = 0; k < 12; k++) {                                           // 内を回る数字の輪
                double ang = Math.PI / 2 - Math.PI * 2 * k / 12 + age * 0.012F;
                numeral(vc, m, HOURS[k], dir(u, v, ang).scale(R * 0.43F), dir(u, v, ang), n, R * 0.045F, 255, 255, 255, (int) (a * 0.5F));
            }
            annulus(vc, m, u, v, R * 0.47F, R * 0.478F, 64, rot, 3, 255, 255, 255, (int) (a * 0.5F));
        }
        // 針
        double minute, hour;
        if ((mode & RulerFxEntity.HANDS_FINALE) != 0) {                              // 刻の終焉: 当たりと同じ角度（短針 0.02・長針 0.06 rad / Tick）
            hour = -age * 0.02;                                                       // 当たりの (cos, 0, sin) は床の (u, v) では角度が逆
            minute = -age * 0.06;
            float len = fx.size();
            hand(vc, m, u, v, hour, len * 18.0F / 28.0F, len * 0.035F, cam, 255, 240, 200, (int) (230 * fade));
            hand(vc, m, u, v, minute, len, len * 0.03F, cam, r, g, b, (int) (230 * fade));
        } else {
            if ((mode & RulerFxEntity.HANDS_TO_TWELVE) != 0) {                       // 12 時へ寄る（時間停止の予告）
                float k = Mth.clamp(age / Math.max(1, fx.life() - 8), 0.0F, 1.0F);
                minute = Mth.lerp(k * k, 2.6, Math.PI / 2);
                hour = Mth.lerp(k, 4.1, Math.PI / 2);
            } else {
                double dir = (mode & RulerFxEntity.HANDS_REVERSE) != 0 ? 1 : -1;
                if ((mode & RulerFxEntity.HANDS_FAST) != 0) dir *= 4.0;                 // 加速の場
                if ((mode & RulerFxEntity.HANDS_SLOW) != 0) dir *= 0.15;                // 減速の場
                minute = dir * age * 0.25 + Math.PI / 2;
                hour = dir * age * 0.03 + Math.PI / 2 + 1.0;
            }
            hand(vc, m, u, v, hour, R * 0.5F, R * 0.035F, cam, 255, 240, 200, a);
            hand(vc, m, u, v, minute, R * 0.74F, R * 0.025F, cam, r, g, b, a);
        }
        annulus(vc, m, u, v, 0.0F, R * 0.06F, 16, 0, 0, 255, 255, 255, a);           // 真ん中の光
    }

    // ------------------------------------------------------------------ ローマ数字

    static final String[] HOURS = {"XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI"};

    static String roman(int k) {
        StringBuilder sb = new StringBuilder();
        for (; k >= 10; k -= 10) sb.append('X');
        if (k == 9) return sb.append("IX").toString();
        if (k >= 5) { sb.append('V'); k -= 5; }
        if (k == 4) return sb.append("IV").toString();
        for (; k > 0; k--) sb.append('I');
        return sb.toString();
    }

    /**
     * 光の線で書くローマ数字（I・V・X。上と下に字全体の幅の横線＝時計の字の飾り）。at が字の真ん中、up が字の上、n が見る側（右 = up × n）、h が字の高さ。
     * にじみの太い線と明るい芯の 2 重
     */
    static void numeral(VertexConsumer vc, Matrix4f m, String text, Vec3 at, Vec3 up, Vec3 n, float h, int r, int g, int b, int a) {
        if (a <= 0 || h <= 0.01F) return;
        Vec3 right = up.cross(n).normalize();
        Vec3 gu = up.normalize();
        float wI = 0.16F * h, wV = 0.58F * h, gap = 0.1F * h;
        float total = -gap;
        for (char c : text.toCharArray()) total += (c == 'I' ? wI : wV) + gap;
        float t = 0.075F * h;
        for (int pass = 0; pass < 2; pass++) {
            float th = pass == 0 ? t * 2.6F : t;
            int cr = pass == 0 ? r : Math.min(255, r / 2 + 128), cg = pass == 0 ? g : Math.min(255, g / 2 + 128), cb = pass == 0 ? b : Math.min(255, b / 2 + 128);
            int ca = pass == 0 ? a / 3 : a;
            float x = -total / 2;
            float top = h / 2, bot = -h / 2;
            for (char c : text.toCharArray()) {
                if (c == 'I') {
                    stroke(vc, m, at, right, gu, x + wI / 2, bot, x + wI / 2, top, th, cr, cg, cb, ca);
                    x += wI + gap;
                } else if (c == 'V') {
                    stroke(vc, m, at, right, gu, x, top, x + wV / 2, bot, th, cr, cg, cb, ca);
                    stroke(vc, m, at, right, gu, x + wV, top, x + wV / 2, bot, th * 0.6F, cr, cg, cb, ca);
                    x += wV + gap;
                } else {
                    stroke(vc, m, at, right, gu, x, top, x + wV, bot, th, cr, cg, cb, ca);
                    stroke(vc, m, at, right, gu, x + wV, top, x, bot, th * 0.6F, cr, cg, cb, ca);
                    x += wV + gap;
                }
            }
            float half = total / 2 + 0.06F * h;
            stroke(vc, m, at, right, gu, -half, top, half, top, th * 0.7F, cr, cg, cb, ca);   // 上下の飾りの横線
            stroke(vc, m, at, right, gu, -half, bot, half, bot, th * 0.7F, cr, cg, cb, ca);
        }
    }

    /** 字の面の (x0, y0) から (x1, y1) への太さ th の線 */
    private static void stroke(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 right, Vec3 up, float x0, float y0, float x1, float y1, float th, int r, int g, int b, int a) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-5F) return;
        float sx = -dy / len * th / 2, sy = dx / len * th / 2;
        Vec3 p0 = at.add(right.scale(x0 + sx)).add(up.scale(y0 + sy)), p1 = at.add(right.scale(x1 + sx)).add(up.scale(y1 + sy));
        Vec3 p2 = at.add(right.scale(x1 - sx)).add(up.scale(y1 - sy)), p3 = at.add(right.scale(x0 - sx)).add(up.scale(y0 - sy));
        quad(vc, m, p0, p1, p2, p3, r, g, b, a);
    }

    /** カメラの方を水平に向いた見る側（立てた字の n） */
    private static Vec3 toCam(Vec3 from, Vec3 cam) {
        Vec3 d = cam.subtract(from).multiply(1, 0, 1);
        return d.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** 輪（r0〜r1。gapEvery があれば切れ目を作る） */
    private static void annulus(VertexConsumer vc, Matrix4f m, Vec3 u, Vec3 v, float r0, float r1, int segs, float rot, int gapEvery, int r, int g, int b, int a) {
        annulus(vc, m, Vec3.ZERO, u, v, r0, r1, segs, rot, gapEvery, r, g, b, a);
    }

    /** 輪（中心 c） */
    private static void annulus(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float r0, float r1, int segs, float rot, int gapEvery, int r, int g, int b, int a) {
        if (a <= 0) return;
        for (int k = 0; k < segs; k++) {
            if (gapEvery > 0 && k % gapEvery == gapEvery - 1) continue;
            double a0 = rot + Math.PI * 2 * k / segs, a1 = rot + Math.PI * 2 * (k + 1) / segs;
            Vec3 i0 = c.add(dir(u, v, a0).scale(r0)), i1 = c.add(dir(u, v, a1).scale(r0)), o0 = c.add(dir(u, v, a0).scale(r1)), o1 = c.add(dir(u, v, a1).scale(r1));
            quad(vc, m, i0, o0, o1, i1, r, g, b, a);
        }
    }

    private static Vec3 dir(Vec3 u, Vec3 v, double ang) {
        return u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
    }

    /** 中心から ang の向きの細い棒（r0〜r1、幅 w） */
    private static void radial(VertexConsumer vc, Matrix4f m, Vec3 u, Vec3 v, double ang, float r0, float r1, float w, int r, int g, int b, int a) {
        Vec3 d = dir(u, v, ang), side = dir(u, v, ang + Math.PI / 2).scale(w);
        quad(vc, m, d.scale(r0).add(side), d.scale(r1).add(side), d.scale(r1).subtract(side), d.scale(r0).subtract(side), r, g, b, a);
    }

    /** 針（根元から先へ細くなる光の刃と白い芯） */
    private static void hand(VertexConsumer vc, Matrix4f m, Vec3 u, Vec3 v, double ang, float len, float w, Vec3 cam, int r, int g, int b, int a) {
        Vec3 d = dir(u, v, ang), side = dir(u, v, ang + Math.PI / 2);
        quad(vc, m, side.scale(w), d.scale(len), d.scale(len), side.scale(-w), r, g, b, a);
        quad(vc, m, side.scale(w * 0.35), d.scale(len * 0.97F), d.scale(len * 0.97F), side.scale(-w * 0.35), 255, 255, 255, a);
        quad(vc, m, side.scale(w * 1.6), d.scale(-len * 0.12F), d.scale(-len * 0.12F), side.scale(-w * 1.6), r, g, b, (int) (a * 0.8F));   // 釣り合いの重り
    }

    // ------------------------------------------------------------------ 掃射の光の刃

    /** 12 本の光の刃: はじめ 20 Tick は赤い予告の線、それから回る（当たりと同じ角度。yaw = はじめの向き、spin = 1 Tick に回る角度） */
    private static void sweep(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade) {
        float len = fx.size();
        boolean warn = age < 20;
        double base = fx.yaw() + fx.spin() * Math.max(0, age - 20);
        int blades = fx.mode() > 0 ? fx.mode() : 12;                                       // 刃の本数（逆巻く時の大渦は 3）
        for (int k = 0; k < blades; k++) {
            double ang = base + k * Math.PI * 2 / blades;
            Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 end = d.scale(len);
            Vec3 floorUp = new Vec3(0, 1, 0);
            if (warn) {
                float blink = (int) (age / 3) % 2 == 0 ? 1.0F : 0.5F;
                ribbon(vc, m, Vec3.ZERO, end, cam, 0.08F, 255, 64, 96, (int) (200 * blink));
                numeral(vc, m, HOURS[k * 12 / blades], d.scale(len + 1.4), d, floorUp, 1.5F, 255, 64, 96, (int) (220 * blink));   // 刃の先のローマ数字
                continue;
            }
            numeral(vc, m, HOURS[k * 12 / blades], d.scale(len + 1.4).add(0, 0.6, 0), new Vec3(0, 1, 0), toCam(d.scale(len + 1.4), cam), 1.6F, 255, 236, 170, (int) (255 * fade));
            int a = (int) (255 * fade);
            ribbon(vc, m, Vec3.ZERO, end, cam, 1.4F, 255, 200, 90, a / 3);                  // 金のにじみ
            ribbon(vc, m, Vec3.ZERO, end, cam, 0.55F, 255, 236, 170, (int) (a * 0.75F));
            ribbon(vc, m, Vec3.ZERO, end, cam, 0.16F, 255, 255, 255, a);                    // 白い芯
            Vec3 trail = new Vec3(Math.cos(ang - fx.spin() * 4), 0, Math.sin(ang - fx.spin() * 4)).scale(len);
            quad(vc, m, Vec3.ZERO, end, trail, Vec3.ZERO, 255, 210, 120, a / 4);            // 回った跡の扇
        }
        for (int k = 0; k < 3; k++) {                                                      // 真ん中の輪
            float rr = 1.0F + k * 0.8F;
            annulus(vc, m, new Vec3(1, 0, 0), new Vec3(0, 0, 1), rr, rr + 0.12F, 32, (float) base * (k % 2 == 0 ? 1 : -1), 4, 255, 230, 150, (int) (180 * fade));
        }
    }

    // ------------------------------------------------------------------ 光の柱

    /** 空から落ちる光の柱（2 Tick で落ちて、細く薄れる）と、巻き付いて昇る螺旋の輪 */
    private static void pillar(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life, float fade, int r, int g, int b) {
        float w = fx.size();
        float drop = Mth.clamp(age / 2.0F, 0.0F, 1.0F);
        float k = 1.0F - age / life;
        Vec3 top = new Vec3(0, 28, 0), bottom = new Vec3(0, 28 * (1 - drop), 0);
        ribbon(vc, m, top, bottom, cam, w * 2.4F * k, r, g, b, (int) (90 * k));
        ribbon(vc, m, top, bottom, cam, w * 1.0F * k, Math.min(255, r + 40), Math.min(255, g + 40), Math.min(255, b + 40), (int) (180 * k));
        ribbon(vc, m, top, bottom, cam, w * 0.3F, 255, 255, 255, (int) (255 * k));
        if (drop < 1.0F) return;
        Vec3 ux = new Vec3(1, 0, 0), uz = new Vec3(0, 0, 1);
        for (int i = 0; i < 4; i++) {                                                     // 昇る螺旋の輪
            float y = (age * 0.6F + i * 2.0F) % 8.0F;
            float rr = w * (1.6F + 0.4F * i) * k;
            annulus(vc, m, new Vec3(0, y, 0), ux, uz, rr, rr + 0.15F, 24, age * 0.2F + i, 4, r, g, b, (int) (200 * k * (1 - y / 8.0F)));
        }
        for (int i = 0; i < 3; i++) {                                                     // 柱のまわりを昇るローマ数字
            double ang = age * 0.18 + i * Math.PI * 2 / 3;
            float rr = w * 2.2F + 0.8F;
            Vec3 at = new Vec3(Math.cos(ang) * rr, 0.8 + (age * 0.25F + i * 1.5F) % 5.0F, Math.sin(ang) * rr);
            numeral(vc, m, HOURS[(fx.getId() + i * 4) % 12], at, new Vec3(0, 1, 0), toCam(at, cam), 0.5F + w * 0.35F, Math.min(255, r + 40), Math.min(255, g + 40), Math.min(255, b + 40), (int) (230 * k));
        }
        float ring = w * 3.0F * (1 - k) + 0.5F;                                           // 着いた所から広がる輪
        annulus(vc, m, new Vec3(0, 0.05, 0), ux, uz, ring, ring + 0.3F, 32, 0, 0, 255, 255, 255, (int) (160 * k));
    }

    // ------------------------------------------------------------------ 消去の立方体

    /** 赤い光の立方体（辺）・中で回る紫の八面体・上下する走査の面。終わりの 4 Tick で縮む */
    private static void cube(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life, float fade) {
        float h = fx.size() / 2.0F * Mth.clamp((life - age) / 4.0F, 0.0F, 1.0F) * Mth.clamp(age / 4.0F, 0.0F, 1.0F);
        if (h <= 0.01F) return;
        Vec3 c = new Vec3(0, h, 0);
        float pulse = 0.7F + 0.3F * Mth.sin(age * 0.9F);
        int a = (int) (230 * pulse);
        float wEdge = 0.06F;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            ribbon(vc, m, c.add(sx * h, -h, sz * h), c.add(sx * h, h, sz * h), cam, wEdge, 255, 64, 96, a);
        }
        for (int sy = -1; sy <= 1; sy += 2) {
            Vec3 y = c.add(0, sy * h, 0);
            ribbon(vc, m, y.add(-h, 0, -h), y.add(h, 0, -h), cam, wEdge, 255, 64, 96, a);
            ribbon(vc, m, y.add(-h, 0, h), y.add(h, 0, h), cam, wEdge, 255, 64, 96, a);
            ribbon(vc, m, y.add(-h, 0, -h), y.add(-h, 0, h), cam, wEdge, 255, 64, 96, a);
            ribbon(vc, m, y.add(h, 0, -h), y.add(h, 0, h), cam, wEdge, 255, 64, 96, a);
        }
        float scan = -h + 2 * h * (0.5F + 0.5F * Mth.sin(age * 0.35F));                     // 走査の面
        Vec3 s = c.add(0, scan, 0);
        quad(vc, m, s.add(-h, 0, -h), s.add(h, 0, -h), s.add(h, 0, h), s.add(-h, 0, h), 255, 80, 110, 60);
        double spin = age * 0.15;                                                          // 回る紫の八面体
        float o = h * 0.6F;
        Vec3[] pts = new Vec3[4];
        for (int k = 0; k < 4; k++) pts[k] = c.add(Math.cos(spin + k * Math.PI / 2) * o, 0, Math.sin(spin + k * Math.PI / 2) * o);
        Vec3 top = c.add(0, o, 0), bot = c.add(0, -o, 0);
        for (int k = 0; k < 4; k++) {
            ribbon(vc, m, pts[k], pts[(k + 1) % 4], cam, 0.05F, 190, 120, 255, a);
            ribbon(vc, m, pts[k], top, cam, 0.05F, 190, 120, 255, a);
            ribbon(vc, m, pts[k], bot, cam, 0.05F, 190, 120, 255, a);
        }
        annulus(vc, m, new Vec3(1, 0, 0), new Vec3(0, 0, 1), h * 1.3F, h * 1.38F, 32, -age * 0.1F, 4, 255, 64, 96, (int) (160 * pulse));   // 床の輪
        int count = Mth.clamp((int) Math.ceil((life - age) * 3.0F / life), 1, 3);         // 消えるまでの数え（III → II → I）
        Vec3 over = c.add(0, h + 0.9, 0);
        numeral(vc, m, roman(count), over, new Vec3(0, 1, 0), toCam(over, cam), 0.8F, 255, 80, 110, 255);
        for (int k = 0; k < 4; k++) {                                                     // 4 つの側の面に XII
            double ang = k * Math.PI / 2;
            Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            numeral(vc, m, "XII", c.add(out.scale(h * 1.01F)), new Vec3(0, 1, 0), out, h * 0.7F, 190, 120, 255, (int) (200 * pulse));
        }
    }

    // ------------------------------------------------------------------ 一閃

    /** 時針の一閃: yaw の向きの前の半円を、5 Tick で薙ぐ巨大な三日月の光の刃と跡の光の扇 */
    private static void slash(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life, int r, int g, int b) {
        float R = fx.size();
        float swing = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
        float k = 1.0F - age / life;
        double center = fx.yaw();
        double from = center - Math.PI * 0.55, to = from + Math.PI * 1.1 * swing;
        int segs = 28;
        Vec3 u = new Vec3(1, 0, 0), v = new Vec3(0, 0, 1);
        for (int i = 0; i < segs; i++) {
            double a0 = from + (to - from) * i / segs, a1 = from + (to - from) * (i + 1) / segs;
            float t = (i + 1) / (float) segs;                                               // 先ほど太く明るい
            Vec3 o0 = dir(u, v, a0).scale(R), o1 = dir(u, v, a1).scale(R);
            Vec3 i0 = dir(u, v, a0).scale(R * (1 - 0.35F * t)), i1 = dir(u, v, a1).scale(R * (1 - 0.35F * t));
            quad(vc, m, i0, o0, o1, i1, r, g, b, (int) (200 * k * t));
            Vec3 c0 = dir(u, v, a0).scale(R * 0.97F), c1 = dir(u, v, a1).scale(R * 0.97F);
            ribbon(vc, m, c0, c1, cam, 0.12F * t, 255, 255, 255, (int) (255 * k * t));
            quad(vc, m, Vec3.ZERO, o0, o1, Vec3.ZERO, r, g, b, (int) (40 * k));              // 跡の光の扇
        }
        for (int i = 0; i < 7; i++) {                                                       // 刃の通った所に立つローマ数字（外へ散る）
            double ang = center - Math.PI * 0.55 + Math.PI * 1.1 * (i + 0.5) / 7;
            if (ang > to) break;
            Vec3 out = dir(u, v, ang);
            numeral(vc, m, HOURS[(i * 2 + 1) % 12], out.scale(R * (1.08F + 0.25F * (1 - k))).add(0, 0.5 + 0.6 * (1 - k), 0), new Vec3(0, 1, 0), out, 0.9F, 255, 240, 200, (int) (255 * k));
        }
    }

    // ------------------------------------------------------------------ 因果断ちの大剣

    /** 大剣の倒れ具合（0 = 空へ立つ、1 = 床に倒れた）。はじめはゆっくり、終わりの 15% で一気に落ちる（当たりは mode Tick ちょうど） */
    public static float bladeFall(float age, int swing) {
        float p = Mth.clamp(age / Math.max(1, swing), 0.0F, 1.0F);
        return p < 0.85F ? 0.4F * p / 0.85F : 0.4F + 0.6F * Mth.square((p - 0.85F) / 0.15F);
    }

    /**
     * 因果断ちの大剣（2026-10-20 ユーザー指定「遅いが喰らってはいけない因果停止＋超貫通ダメージの即死級の技」、同日「大剣も自作して豪華な見た目に」）。
     * 置いた所を根元に空へ立つ巨大な時空剪定の針が、mode Tick かけて yaw の向きへ倒れる。
     * <ul>
     *   <li>現れる（20 Tick）: 根元の床に回る金の召喚の文字盤、光が昇って柄から切っ先へ剣が形になり、剣のまわりを光の輪が昇る</li>
     *   <li>剣: 柄頭は針が速く回る小さな文字盤と紫の宝石、巻いた柄、鍔は剣を囲む XII の輪と切っ先へ反る三日月の双翼（先に宝石）。
     *       身は根元で少し膨らんで切っ先へ細る金の刃（白い縁・2 面の十字）、真ん中の紫の樋を切っ先へ流れる光の脈、樋に刻まれたローマ数字、
     *       身を囲んで回る 3 つの刻みの輪、切っ先の星の光、紫と金のにじみ</li>
     *   <li>倒れる間: 倒れた跡に薄れていく 4 つの残像と光の扇。床には落ちる所の赤い帯（明滅、流れる線、縁の XII、真ん中に残りの秒）</li>
     *   <li>落ちた後: 帯が白く光り、帯に沿って光の柱が並んで立ち上がり、輪が広がり、ローマ数字が昇る。剣は切っ先から光の粒になってほどける</li>
     * </ul>
     */
    private static void blade(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life, int r, int g, int b) {
        int swing = Math.max(1, fx.mode());
        float full = fx.size();
        float W = com.automatavalkyries.entity.chrono.TimeRulerEntity.BLADE_HALF_WIDTH;
        double yaw = fx.yaw();
        Vec3 d = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)), up = new Vec3(0, 1, 0), side = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 ux = new Vec3(1, 0, 0), uz = new Vec3(0, 0, 1);
        boolean fallen = age >= swing;
        float after = fallen ? Mth.clamp((age - swing) / Math.max(1, life - swing), 0.0F, 1.0F) : 0.0F, k = 1.0F - after;
        float form = Mth.clamp(age / 20.0F, 0.0F, 1.0F);                                       // 現れる間（柄から切っ先へ形になる）
        float bandLen = full + 2.0F;
        Vec3 f = new Vec3(0, 0.04, 0);
        // ---- 根元の召喚の文字盤
        int ca = (int) (220 * Mth.clamp(age / 6.0F, 0.0F, 1.0F) * k);
        annulus(vc, m, f, ux, uz, 3.6F, 3.8F, 64, age * 0.02F, 0, 255, 209, 102, ca);
        annulus(vc, m, f, ux, uz, 2.7F, 2.78F, 48, -age * 0.03F, 6, 255, 240, 200, ca);
        for (int h = 0; h < 12; h++) {
            double ang = Math.PI / 2 - Math.PI * 2 * h / 12 + age * 0.02F;
            Vec3 o = new Vec3(Math.cos(ang), 0, -Math.sin(ang));
            numeral(vc, m, HOURS[h], f.add(o.scale(3.2)), o, up, 0.5F, 255, 220, 140, ca);
            radialAt(vc, m, f, ux, uz, -ang, 3.8F, 4.3F, 0.06F, 255, 209, 102, ca);
        }
        if (age < 24) {                                                                          // 昇る光
            float rise = age / 24.0F;
            ribbon(vc, m, f, f.add(0, full * rise, 0), cam, 3.0F * (1 - rise), 255, 220, 150, (int) (160 * (1 - rise)));
            ribbon(vc, m, f, f.add(0, full * rise, 0), cam, 0.4F, 255, 255, 255, (int) (230 * (1 - rise)));
        }
        // ---- 床の帯
        if (!fallen) {
            float blink = (int) (age / (age > swing - 30 ? 2 : 5)) % 2 == 0 ? 1.0F : 0.55F;   // 落ちる間際は速く明滅
            quad(vc, m, f.add(side.scale(W)), f.add(d.scale(bandLen)).add(side.scale(W)), f.add(d.scale(bandLen)).subtract(side.scale(W)), f.subtract(side.scale(W)), 255, 40, 70, (int) (60 * blink));
            ribbon(vc, m, f.add(side.scale(W)), f.add(d.scale(bandLen)).add(side.scale(W)), cam, 0.18F, 255, 64, 96, (int) (230 * blink));
            ribbon(vc, m, f.subtract(side.scale(W)), f.add(d.scale(bandLen)).subtract(side.scale(W)), cam, 0.18F, 255, 64, 96, (int) (230 * blink));
            for (float t = (age * 0.25F) % 3.0F; t < bandLen; t += 3.0F) {                     // 根元から先へ流れる山形の線
                Vec3 c = f.add(d.scale(t));
                ribbon(vc, m, c.add(side.scale(W)), c.add(d.scale(0.9)), cam, 0.1F, 255, 90, 120, (int) (150 * blink));
                ribbon(vc, m, c.subtract(side.scale(W)), c.add(d.scale(0.9)), cam, 0.1F, 255, 90, 120, (int) (150 * blink));
            }
            for (int i = 0; i < 6; i++) {                                                      // 帯の縁に XII
                Vec3 at = f.add(d.scale(bandLen * (i + 0.5F) / 6)).add(side.scale((i % 2 == 0 ? 1 : -1) * (W + 0.9F)));
                numeral(vc, m, "XII", at, d, up, 0.9F, 255, 64, 96, (int) (200 * blink));
            }
            int secs = Mth.clamp((int) Math.ceil((swing - age) / 20.0F), 1, 12);                // 落ちるまでの秒（V → I）
            Vec3 mid = d.scale(bandLen * 0.5F).add(0, 2.2, 0);
            numeral(vc, m, roman(secs), mid, up, toCam(mid, cam), 2.4F, 255, 64, 96, 255);
        } else {
            int a = (int) (220 * k);
            quad(vc, m, f.add(side.scale(W)), f.add(d.scale(bandLen)).add(side.scale(W)), f.add(d.scale(bandLen)).subtract(side.scale(W)), f.subtract(side.scale(W)), 255, 250, 235, a / 2);
            float out = W + after * 7.0F;                                                       // 帯の縁から外へ広がる衝撃
            ribbon(vc, m, f.add(side.scale(out)), f.add(d.scale(bandLen)).add(side.scale(out)), cam, 0.3F, 255, 220, 150, a);
            ribbon(vc, m, f.subtract(side.scale(out)), f.add(d.scale(bandLen)).subtract(side.scale(out)), cam, 0.3F, 255, 220, 150, a);
            for (int i = 0; i <= 8; i++) {                                                      // 帯に沿って立ち上がる光の柱・広がる輪・昇る数字
                float delay = i * 0.04F, s = Mth.clamp((after - delay) / 0.6F, 0.0F, 1.0F);
                if (s <= 0 || s >= 1) continue;
                Vec3 c = f.add(d.scale(bandLen * i / 8.0F));
                float hgt = 14.0F * Mth.sin(s * Mth.PI), pk = 1.0F - s;
                ribbon(vc, m, c, c.add(0, hgt, 0), cam, 2.2F * pk, 255, 200, 90, (int) (110 * pk));
                ribbon(vc, m, c, c.add(0, hgt, 0), cam, 0.35F, 255, 255, 255, (int) (240 * pk));
                annulus(vc, m, c.add(0, 0.05, 0), ux, uz, 0.5F + s * 4.5F, 0.7F + s * 4.5F, 32, 0, 0, 255, 230, 160, (int) (200 * pk));
                Vec3 nAt = c.add(0, 1.0 + s * 6.0, 0);
                numeral(vc, m, HOURS[(i * 5) % 12], nAt, up, toCam(nAt, cam), 0.9F, 255, 240, 200, (int) (255 * pk));
            }
        }
        // ---- 剣と倒れた跡の残像
        double th = Math.PI / 2 * (1.0 - bladeFall(age, swing));
        Vec3 base = new Vec3(0, 0.3, 0);
        if (!fallen && age > 20) {
            for (int gIdx = 4; gIdx >= 1; gIdx--) {                                             // 薄れていく 4 つの残像
                double pth = Math.PI / 2 * (1.0 - bladeFall(age - gIdx * 2.0F, swing));
                if (Math.abs(pth - th) < 0.004) continue;
                Vec3 pv = d.scale(Math.cos(pth)).add(up.scale(Math.sin(pth))).normalize();
                bladeBody(vc, m, cam, base, pv, side, full, 1.0F, (int) (70 / gIdx), false, age);
            }
            double prev = Math.PI / 2 * (1.0 - bladeFall(age - 8, swing));                      // 倒れた跡の光の扇
            Vec3 pv = d.scale(Math.cos(prev)).add(up.scale(Math.sin(prev))).normalize();
            Vec3 bvNow = d.scale(Math.cos(th)).add(up.scale(Math.sin(th))).normalize();
            quad(vc, m, base, base.add(bvNow.scale(full)), base.add(pv.scale(full)), base, 255, 200, 120, 60);
        }
        Vec3 bv = d.scale(Math.cos(th)).add(up.scale(Math.sin(th))).normalize();
        float reach = fallen ? Mth.clamp(1.0F - after * 1.4F, 0.0F, 1.0F) : form;               // 現れる間は柄から、落ちた後は切っ先からほどける
        if (reach > 0.02F) bladeBody(vc, m, cam, base, bv, side, full, reach, (int) (255 * (fallen ? Mth.clamp(k * 1.5F, 0.0F, 1.0F) : 1.0F)), true, age);
        if (fallen && after < 0.8F) {                                                           // ほどけた所から昇る光の粒
            java.util.Random rnd = new java.util.Random(fx.getId() * 131L);
            for (int i = 0; i < 24; i++) {
                float t0 = rnd.nextFloat();
                if (t0 < reach) continue;
                float lift = after * (2.0F + rnd.nextFloat() * 5.0F);
                Vec3 p = base.add(bv.scale(full * t0)).add(side.scale((rnd.nextFloat() - 0.5F) * 2.0F)).add(0, lift, 0);
                annulus(vc, m, p, ux, up, 0.0F, 0.18F, 6, 0, 0, 255, rnd.nextBoolean() ? 209 : 255, rnd.nextBoolean() ? 102 : 255, (int) (230 * (1.0F - after / 0.8F)));
            }
        }
    }

    /**
     * 因果断ちの大剣の剣の形。base が根元、bv が切っ先の向き、side が刃の幅の向き、L が長さ、reach が見える割合（0〜1、柄の側から）。
     * rich なら柄頭の文字盤・柄・鍔の輪と双翼・樋・刻んだ数字・刻みの輪・切っ先の星まで、そうでなければ刃の形とにじみだけ（残像）
     */
    private static void bladeBody(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 base, Vec3 bv, Vec3 side, float L, float reach, int a, boolean rich, float age) {
        if (a <= 0) return;
        Vec3 nv = bv.cross(side).normalize();
        float guardT = 0.08F;
        int segs = 18;
        float tEnd = guardT + (1.0F - guardT) * Mth.clamp((reach - guardT) / (1.0F - guardT), 0.0F, 1.0F);
        // ---- 刃: 根元で少し膨らみ切っ先へ細る。横の面と縦の面の十字、白い縁、紫の樋と流れる光の脈
        Vec3 prevC = null;
        float prevW = 0;
        for (int i = 0; i <= segs; i++) {
            float t = guardT + (tEnd - guardT) * i / segs;
            float u = (t - guardT) / (1.0F - guardT);
            float w = 1.9F * (float) Math.pow(Math.max(0.0F, 1.0F - u), 0.75) * (1.0F + 0.25F * Mth.sin(Mth.clamp(u * 3.0F, 0.0F, 1.0F) * Mth.PI));
            if (i == segs && tEnd >= 0.999F) w = 0.0F;
            Vec3 c = base.add(bv.scale(L * t));
            if (prevC != null) {
                quad(vc, m, prevC.add(side.scale(prevW)), c.add(side.scale(w)), c.subtract(side.scale(w)), prevC.subtract(side.scale(prevW)), 255, 196, 90, (int) (a * 0.8F));
                quad(vc, m, prevC.add(nv.scale(prevW * 0.35F)), c.add(nv.scale(w * 0.35F)), c.subtract(nv.scale(w * 0.35F)), prevC.subtract(nv.scale(prevW * 0.35F)), 255, 220, 140, (int) (a * 0.7F));
                if (rich) {
                    ribbon(vc, m, prevC.add(side.scale(prevW)), c.add(side.scale(w)), cam, 0.12F, 255, 255, 255, a);       // 白い縁
                    ribbon(vc, m, prevC.subtract(side.scale(prevW)), c.subtract(side.scale(w)), cam, 0.12F, 255, 255, 255, a);
                    float pulse = 0.5F + 0.5F * Mth.sin(u * 18.0F - age * 0.5F);                                         // 樋を切っ先へ流れる光
                    quad(vc, m, prevC.add(side.scale(prevW * 0.22F)).add(nv.scale(0.02)), c.add(side.scale(w * 0.22F)).add(nv.scale(0.02)),
                            c.subtract(side.scale(w * 0.22F)).add(nv.scale(0.02)), prevC.subtract(side.scale(prevW * 0.22F)).add(nv.scale(0.02)),
                            176 + (int) (79 * pulse), 112 + (int) (143 * pulse), 255, (int) (a * (0.6F + 0.4F * pulse)));
                }
            }
            prevC = c;
            prevW = w;
        }
        Vec3 tip = base.add(bv.scale(L * tEnd));
        Vec3 guard = base.add(bv.scale(L * guardT));
        ribbon(vc, m, guard, tip, cam, 5.0F, 176, 112, 255, (int) (a * 0.22F));                    // 紫と金のにじみ
        ribbon(vc, m, guard, tip, cam, 2.2F, 255, 210, 120, (int) (a * 0.25F));
        if (!rich) return;
        // ---- 樋に刻んだローマ数字（両の面）
        for (int i = 0; i < 9; i++) {
            float t = guardT + 0.06F + 0.8F * i / 9.0F;
            if (t > tEnd - 0.04F) break;
            Vec3 at = base.add(bv.scale(L * t));
            numeral(vc, m, HOURS[(12 - i) % 12], at.add(nv.scale(0.06)), bv, nv, 0.7F, 255, 255, 255, a);
            numeral(vc, m, HOURS[(12 - i) % 12], at.subtract(nv.scale(0.06)), bv, nv.scale(-1), 0.7F, 255, 255, 255, a);
        }
        // ---- 身を囲んで回る 3 つの刻みの輪
        for (int i = 0; i < 3; i++) {
            float t = 0.3F + 0.25F * i;
            if (t > tEnd) break;
            Vec3 c = base.add(bv.scale(L * t));
            float rr = 2.6F - 0.5F * i;
            float spin = age * (i % 2 == 0 ? 0.05F : -0.07F);
            annulus(vc, m, c, side, nv, rr, rr + 0.1F, 48, spin, 6, 255, 209, 102, (int) (a * 0.8F));
            for (int n = 0; n < 4; n++) {
                double ang = spin + Math.PI / 2 * n;
                Vec3 o = dir(side, nv, ang);
                numeral(vc, m, HOURS[n * 3], c.add(o.scale(rr + 0.35)), o, bv, 0.45F, 255, 240, 200, a);
            }
        }
        // ---- 切っ先の星
        if (tEnd > 0.95F) {
            float star = 1.4F + 0.4F * Mth.sin(age * 0.4F);
            ribbon(vc, m, tip.subtract(side.scale(star)), tip.add(side.scale(star)), cam, 0.15F, 255, 255, 255, a);
            ribbon(vc, m, tip.subtract(nv.scale(star)), tip.add(nv.scale(star)), cam, 0.15F, 255, 255, 255, a);
            ribbon(vc, m, tip.subtract(bv.scale(star * 0.8)), tip.add(bv.scale(star * 1.6)), cam, 0.15F, 255, 255, 255, a);
            annulus(vc, m, tip, side, nv, 0.0F, 0.7F, 12, 0, 0, 255, 255, 255, a);
        }
        // ---- 鍔: 剣を囲む XII の輪と、切っ先へ反る三日月の双翼（先に宝石）、真ん中の紫の宝石
        annulus(vc, m, guard, side, bv, 2.1F, 2.3F, 48, age * 0.01F, 0, 255, 209, 102, a);
        annulus(vc, m, guard, side, bv, 1.75F, 1.8F, 48, 0, 4, 255, 255, 255, a);
        for (int h = 0; h < 12; h++) {
            double ang = Math.PI / 2 - Math.PI * 2 * h / 12 + age * 0.01F;
            Vec3 o = dir(side, bv, ang);
            numeral(vc, m, HOURS[h], guard.add(o.scale(1.95)), o, nv, 0.28F, 255, 240, 200, a);
        }
        for (int s = -1; s <= 1; s += 2) {
            Vec3 prev = guard.add(side.scale(s * 2.2));
            for (int i = 1; i <= 8; i++) {
                float t = i / 8.0F;
                Vec3 p = guard.add(side.scale(s * (2.2 + 2.6 * t))).add(bv.scale(L * 0.06F * t * t * 3.0F));
                ribbon(vc, m, prev, p, cam, 0.7F * (1.0F - t * 0.7F), 255, 196, 90, a);
                ribbon(vc, m, prev, p, cam, 0.15F, 255, 255, 255, a);
                prev = p;
            }
            annulus(vc, m, prev, side, nv, 0.0F, 0.45F, 12, 0, 0, 200, 140, 255, a);                // 双翼の先の宝石
            annulus(vc, m, prev, side, nv, 0.0F, 0.18F, 8, 0, 0, 255, 255, 255, a);
        }
        annulus(vc, m, guard, side, nv, 0.0F, 0.75F, 16, 0, 0, 176, 112, 255, a);                    // 真ん中の紫の宝石
        annulus(vc, m, guard, side, nv, 0.0F, 0.3F, 12, 0, 0, 255, 255, 255, a);
        // ---- 巻いた柄と、針が速く回る柄頭の文字盤
        ribbon(vc, m, base, guard, cam, 0.55F, 120, 70, 200, a);
        for (int i = 1; i < 6; i++) {
            Vec3 c = base.add(bv.scale(L * guardT * i / 6.0F));
            annulus(vc, m, c, side, nv, 0.28F, 0.36F, 12, 0, 0, 255, 209, 102, a);
        }
        annulus(vc, m, base, side, nv, 1.2F, 1.32F, 32, 0, 0, 255, 209, 102, a);
        for (int h = 0; h < 12; h++) radialAt(vc, m, base, side, nv, Math.PI * 2 * h / 12, 0.95F, 1.15F, h % 3 == 0 ? 0.06F : 0.03F, 255, 240, 200, a);
        radialAt(vc, m, base, side, nv, -age * 0.4F, 0.0F, 0.95F, 0.05F, 255, 255, 255, a);
        radialAt(vc, m, base, side, nv, -age * 0.05F, 0.0F, 0.65F, 0.08F, 255, 220, 150, a);
        annulus(vc, m, base, side, nv, 0.0F, 0.22F, 10, 0, 0, 176, 112, 255, a);
        annulus(vc, m, base, side, bv, 0.0F, 0.9F, 16, 0, 0, 176, 112, 255, (int) (a * 0.6F));
    }

    // ------------------------------------------------------------------ 時の歯車

    /**
     * 時の歯車の挟撃（2026-10-20 ユーザー指定）: 床を yaw の向きへ転がる巨大な縦の歯車（厚みのある 2 枚の面、16 の歯・縁・6 本の輻・真ん中の光・縁のローマ数字）。
     * 進んだ分だけ回り、着いたら（mode Tick）かみ合って脈打つ
     */
    private static void gear(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life, float fade, int r, int g, int b) {
        float R = fx.size();
        int travel = Math.max(1, fx.mode());
        double yaw = fx.yaw();
        Vec3 u = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)), v = new Vec3(0, 1, 0), n = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        float roll = -Math.min(age, travel) * fx.spin() / R;
        boolean meshed = age >= travel;
        float pulse = meshed ? 0.7F + 0.3F * Mth.sin(age * 1.2F) : 1.0F;
        int a = (int) (220 * fade * pulse * Mth.clamp(age / 5.0F, 0.0F, 1.0F));
        Vec3 c = new Vec3(0, R, 0);
        for (int face = -1; face <= 1; face += 2) {
            Vec3 cf = c.add(n.scale(face * 0.45));
            annulus(vc, m, cf, u, v, R * 0.76F, R * 0.88F, 48, roll, 0, r, g, b, a);                 // 縁
            annulus(vc, m, cf, u, v, R * 0.8F, R * 0.83F, 48, roll, 0, 255, 255, 255, a);
            for (int k = 0; k < 16; k++) {                                                         // 歯
                double ang = roll + Math.PI * 2 * k / 16;
                radialAt(vc, m, cf, u, v, ang, R * 0.86F, R, R * 0.08F, r, g, b, a);
            }
            for (int k = 0; k < 6; k++) radialAt(vc, m, cf, u, v, roll + Math.PI * 2 * k / 6, R * 0.18F, R * 0.77F, R * 0.04F, r, g, b, (int) (a * 0.8F));   // 輻
            annulus(vc, m, cf, u, v, 0.0F, R * 0.18F, 16, 0, 0, 255, 255, 255, a);                     // 真ん中の光
            for (int k = 0; k < 12; k++) {                                                         // 縁の内のローマ数字（歯車と一緒に回る）
                double ang = Math.PI / 2 - Math.PI * 2 * k / 12 + roll;
                Vec3 dirk = dir(u, v, ang);
                numeral(vc, m, HOURS[k], cf.add(dirk.scale(R * 0.64F)), dirk, n.scale(face), R * 0.09F, 255, 240, 200, a);
            }
        }
        ribbon(vc, m, c.add(n.scale(-0.6)), c.add(n.scale(0.6)), cam, R * 0.3F, 255, 255, 255, a);   // 軸
        if (meshed) {                                                                                // かみ合って光る
            float k2 = Mth.clamp((age - travel) / Math.max(1, life - travel), 0.0F, 1.0F);
            annulus(vc, m, c, u, v, R * (1.0F + k2), R * (1.06F + k2), 32, 0, 0, 255, 255, 255, (int) (200 * (1 - k2)));
        }
    }

    /** 中心 c から ang の向きの細い棒 */
    private static void radialAt(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, double ang, float r0, float r1, float w, int r, int g, int b, int a) {
        Vec3 d = dir(u, v, ang), side = dir(u, v, ang + Math.PI / 2).scale(w);
        quad(vc, m, c.add(d.scale(r0)).add(side), c.add(d.scale(r1)).add(side), c.add(d.scale(r1)).subtract(side), c.add(d.scale(r0)).subtract(side), r, g, b, a);
    }

    // ------------------------------------------------------------------ 因果停止・因果断絶

    /**
     * 因果停止と因果断絶（2026-10-20 ユーザー指定「因果断絶の演出を自作して美麗に」）。mode = 止まる Tick、size = 止まった者の背の高さ、yaw = 断つ一閃の向き。
     * <ul>
     *   <li>止まっている間: 遠くから 12 本の赤い因果の糸が胸へ伸びて縛り（揺れが収まりながら締まる）、傾いた 3 つの輪が回りを遅くして止まり、
     *       時の結晶の六角の殻が少しずつ形になる。足元の文字盤は針が XII で止まり、ひびが広がる。体を巻く 3 つの帯、頭の上に残りの秒（II → I）が鼓動に合わせて脈打つ</li>
     *   <li>明けた瞬間（因果断絶）: 胸を斜めに断つ白い一閃、糸がその所で断たれて両側へ縮み、殻が破片になって舞い散り、横と縦の輪が広がり、ローマ数字が散る</li>
     * </ul>
     */
    private static void causal(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life) {
        int lock = Math.max(1, fx.mode());
        float H = Math.max(0.8F, fx.size());
        Vec3 chest = new Vec3(0, H * 0.55, 0), up = new Vec3(0, 1, 0), ux = new Vec3(1, 0, 0), uz = new Vec3(0, 0, 1);
        java.util.Random rnd = new java.util.Random(fx.getId() * 7919L);
        Vec3[] anchors = new Vec3[12];
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + rnd.nextDouble() * 0.4;
            anchors[i] = new Vec3(Math.cos(a) * (6.5 + rnd.nextDouble() * 2.5), H * 0.2 + rnd.nextDouble() * (H + 3.0), Math.sin(a) * (6.5 + rnd.nextDouble() * 2.5));
        }
        boolean severed = age >= lock;
        float k = Mth.clamp(age / lock, 0.0F, 1.0F);
        float beat = pulseBeat(age);                                                    // 鼓動（1 秒ごとに 2 度打つ）
        if (!severed) {
            // ---- 足元の文字盤（止まった針・広がるひび）
            float R = 2.6F;
            int a = (int) (220 * Mth.clamp(age / 6.0F, 0.0F, 1.0F));
            annulus(vc, m, new Vec3(0, 0.05, 0), ux, uz, R * 0.94F, R, 64, 0, 0, 255, 64, 96, a);
            annulus(vc, m, new Vec3(0, 0.05, 0), ux, uz, R * 0.62F, R * 0.66F, 48, 0, 6, 255, 209, 102, (int) (a * 0.8F));
            for (int h = 0; h < 12; h++) {
                double ang = Math.PI / 2 - Math.PI * 2 * h / 12;
                Vec3 at = new Vec3(Math.cos(ang) * R * 0.8, 0.06, -Math.sin(ang) * R * 0.8);
                numeral(vc, m, HOURS[h], at, new Vec3(Math.cos(ang), 0, -Math.sin(ang)), up, 0.32F, 255, 90, 120, a);
            }
            for (int pass = 0; pass < 2; pass++) {                                      // 針は 2 本とも XII で止まっている
                Vec3 tip = new Vec3(0, 0.07, -R * (pass == 0 ? 0.55 : 0.78));
                quad(vc, m, new Vec3(-0.08, 0.07, 0), tip, tip, new Vec3(0.08, 0.07, 0), 255, 240, 200, a);
            }
            for (int c = 0; c < 7; c++) {                                               // ひび（ぎざぎざに外へ伸びる）
                double ang = rnd.nextDouble() * Math.PI * 2;
                Vec3 p = new Vec3(0, 0.08, 0);
                int segs = 5;
                for (int sgm = 0; sgm < segs; sgm++) {
                    if ((sgm + 1) / (float) segs > k * 1.15F) break;
                    ang += (rnd.nextDouble() - 0.5) * 0.9;
                    Vec3 q = p.add(Math.cos(ang) * R / segs, 0, Math.sin(ang) * R / segs);
                    ribbon(vc, m, p, q, cam, 0.05F, 255, 245, 235, (int) (200 * a / 220.0F));
                    p = q;
                }
            }
            // ---- 因果の糸: 遠くから胸へ伸びて縛り、揺れが収まりながら締まる
            for (int i = 0; i < 12; i++) {
                float shoot = Mth.clamp((age - i * 0.8F) / 5.0F, 0.0F, 1.0F);
                if (shoot <= 0) continue;
                Vec3 from = anchors[i].scale(1.0 - 0.35 * k);
                Vec3 to = from.add(chest.subtract(from).scale(shoot));
                Vec3 axis = to.subtract(from), side = axis.cross(up).normalize();
                Vec3 prev = from;
                for (int sgm = 1; sgm <= 8; sgm++) {
                    float t = sgm / 8.0F;
                    double wave = Math.sin(age * 0.35 + i * 1.7 + t * 6.0) * 0.35 * (1.0 - k) * Math.sin(t * Math.PI);
                    Vec3 cur = from.add(axis.scale(t)).add(side.scale(wave));
                    ribbon(vc, m, prev, cur, cam, 0.16F + 0.06F * beat, 255, 40, 70, 110);
                    ribbon(vc, m, prev, cur, cam, 0.05F, 255, 210, 220, 230);
                    prev = cur;
                }
                annulus(vc, m, from, ux, uz, 0.0F, 0.18F, 8, 0, 0, 255, 120, 140, 200);        // 糸の根元の光
            }
            // ---- 傾いた 3 つの輪（回りが遅くなって止まる）
            for (int i = 0; i < 3; i++) {
                double tilt = 0.5 + i * 0.6, spin = (i % 2 == 0 ? 1 : -1) * 0.25 * (age - age * age / (2.0 * lock));
                Vec3 ax = new Vec3(Math.cos(i * 2.1), 0, Math.sin(i * 2.1));
                Vec3 u = ax, v = up.scale(Math.cos(tilt)).add(ax.cross(up).scale(Math.sin(tilt)));
                float rr = H * 0.75F + i * 0.3F;
                int ra = (int) (200 * Mth.clamp(age / 8.0F, 0.0F, 1.0F));
                annulus(vc, m, chest, u, v, rr, rr + 0.06F, 48, (float) spin, 8, i == 1 ? 255 : 255, i == 1 ? 209 : 64, i == 1 ? 102 : 96, ra);
                for (int n = 0; n < 4; n++) {
                    double ang = spin + Math.PI / 2 * n;
                    Vec3 d = dir(u, v, ang);
                    numeral(vc, m, HOURS[n * 3], chest.add(d.scale(rr + 0.25)), d, u.cross(v).normalize(), 0.28F, 255, 230, 200, ra);
                }
            }
            // ---- 体を巻く 3 つの帯（締まっていく）
            for (int i = 0; i < 3; i++) {
                float y = H * (0.25F + 0.28F * i), rr = 0.75F - 0.15F * k + 0.05F * beat;
                annulus(vc, m, new Vec3(0, y, 0), ux, uz, rr, rr + 0.08F, 6, (float) (i * 0.5 + age * 0.01 * (1 - k)), 0, 255, 64, 96, 200);
            }
            // ---- 時の結晶の六角の殻（少しずつ形になる）
            hexShell(vc, m, cam, H, k, 1.0F, 0, null, 0);
            // ---- 残りの秒（II → I）
            int secs = Mth.clamp((int) Math.ceil((lock - age) / 20.0F), 1, 9);
            Vec3 over = new Vec3(0, H + 1.1, 0);
            numeral(vc, m, roman(secs), over, up, toCam(over, cam), 0.9F + 0.25F * beat, 255, 64, 96, 255);
            if (lock - age < 10) {                                                     // 明ける間際: 胸の一点が白く膨らむ
                float g = 1.0F - (lock - age) / 10.0F;
                annulus(vc, m, chest, ux, up, 0.0F, 0.2F + g * 0.6F, 16, 0, 0, 255, 255, 255, (int) (255 * g));
                annulus(vc, m, chest, uz, up, 0.0F, 0.2F + g * 0.6F, 16, 0, 0, 255, 255, 255, (int) (255 * g));
            }
            return;
        }
        // ================= 因果断絶 =================
        float t = age - lock, s = Mth.clamp(t / RulerFxEntity.SEVER_TICKS, 0.0F, 1.0F), fade = 1.0F - s;
        double yaw = fx.yaw();
        Vec3 cut = new Vec3(Math.cos(yaw), 0.85, Math.sin(yaw)).normalize();
        // ---- 胸を斜めに断つ一閃（3 Tick で走り、赤くにじんで消える）
        float run = Mth.clamp(t / 3.0F, 0.0F, 1.0F), len = 3.2F + H;
        Vec3 c0 = chest.subtract(cut.scale(len)), c1 = chest.subtract(cut.scale(len)).add(cut.scale(len * 2 * run));
        ribbon(vc, m, c0, c1, cam, 2.6F * fade, 255, 40, 70, (int) (90 * fade));
        ribbon(vc, m, c0, c1, cam, 0.7F * fade, 255, 150, 170, (int) (200 * fade));
        ribbon(vc, m, c0, c1, cam, 0.18F, 255, 255, 255, (int) (255 * Mth.clamp(1.0F - t / 14.0F, 0.0F, 1.0F)));
        // ---- 断たれた糸: 断たれた所から、内側は胸へ、外側は根元へ縮みながら消える
        for (int i = 0; i < 12; i++) {
            Vec3 from = anchors[i].scale(0.65);
            Vec3 mid = from.add(chest.subtract(from).scale(0.5));
            float shrink = Mth.clamp(t / 12.0F, 0.0F, 1.0F);
            Vec3 outerEnd = mid.add(from.subtract(mid).scale(shrink));
            Vec3 innerEnd = mid.add(chest.subtract(mid).scale(shrink));
            int ta = (int) (220 * (1.0F - shrink));
            ribbon(vc, m, from, outerEnd, cam, 0.12F, 255, 60, 90, ta);
            ribbon(vc, m, innerEnd, chest, cam, 0.12F, 255, 60, 90, ta);
            annulus(vc, m, outerEnd, ux, uz, 0.0F, 0.12F, 6, 0, 0, 255, 230, 230, ta);       // 断ち口の火花
        }
        // ---- 殻が砕けて破片になって舞う
        hexShell(vc, m, cam, H, 1.0F, fade, 1, rnd, t);
        // ---- 広がる横と縦の輪
        float ring = 0.8F + s * 9.0F;
        annulus(vc, m, chest, ux, uz, ring, ring + 0.25F * fade + 0.05F, 64, 0, 0, 255, 255, 255, (int) (200 * fade));
        annulus(vc, m, chest, ux, uz, ring * 0.7F, ring * 0.7F + 0.15F, 64, 0, 4, 255, 64, 96, (int) (200 * fade));
        Vec3 n = cut.cross(up).normalize();
        annulus(vc, m, chest, cut, n.cross(cut).normalize(), ring * 0.6F, ring * 0.6F + 0.2F, 64, 0, 0, 255, 209, 102, (int) (180 * fade));
        // ---- 散るローマ数字
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI * 2 * i / 8 + 0.3;
            Vec3 d = new Vec3(Math.cos(ang), 0.5 + 0.3 * Math.sin(i * 1.3), Math.sin(ang)).normalize();
            Vec3 at = chest.add(d.scale(0.8 + t * 0.18));
            numeral(vc, m, HOURS[(i * 3 + 1) % 12], at, up, toCam(at, cam), 0.5F, 255, 230, 200, (int) (255 * fade));
        }
        annulus(vc, m, chest, ux, up, 0.0F, 1.2F * fade, 16, 0, 0, 255, 255, 255, (int) (255 * Mth.clamp(1.0F - t / 6.0F, 0.0F, 1.0F)));   // 断った瞬間の白い光
        annulus(vc, m, chest, uz, up, 0.0F, 1.2F * fade, 16, 0, 0, 255, 255, 255, (int) (255 * Mth.clamp(1.0F - t / 6.0F, 0.0F, 1.0F)));
    }

    /** 鼓動（1 秒ごとにどくん、どくんと 2 度。0〜1） */
    private static float pulseBeat(float age) {
        float p = age % 20.0F;
        float a = Math.max(0.0F, 1.0F - Math.abs(p - 1.5F) / 1.5F), b = Math.max(0.0F, 1.0F - Math.abs(p - 5.0F) / 1.5F) * 0.6F;
        return Math.max(a, b);
    }

    /**
     * 時の結晶の六角の殻（半径 1.1、背の高さ + 0.4）。mode 0: grow（0〜1）だけ下から形になる辺と薄い面。
     * mode 1: 砕けた破片（面ごとの三角が外へ飛び、回り、落ちる。t = 砕けてからの Tick）
     */
    private static void hexShell(VertexConsumer vc, Matrix4f m, Vec3 cam, float H, float grow, float alpha, int mode, @org.jetbrains.annotations.Nullable java.util.Random rnd, float t) {
        float r = 1.1F, top = H + 0.4F;
        Vec3[] lo = new Vec3[6], hi = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + Math.PI / 6;
            lo[i] = new Vec3(Math.cos(a) * r, -0.05, Math.sin(a) * r);
            hi[i] = new Vec3(Math.cos(a) * r, top, Math.sin(a) * r);
        }
        if (mode == 0) {
            float h = top * grow;
            int a = (int) (220 * alpha * Mth.clamp(grow * 2.0F, 0.0F, 1.0F));
            for (int i = 0; i < 6; i++) {
                Vec3 p0 = lo[i], p1 = lo[(i + 1) % 6];
                Vec3 q0 = new Vec3(p0.x, Math.min(h, top), p0.z), q1 = new Vec3(p1.x, Math.min(h, top), p1.z);
                ribbon(vc, m, p0, q0, cam, 0.05F, 255, 120, 150, a);                       // 縦の辺
                ribbon(vc, m, q0, q1, cam, 0.04F, 255, 200, 210, a);                       // 伸びていく上の縁
                quad(vc, m, p0, q0, q1, p1, 255, 80, 110, (int) (28 * alpha));            // 薄い面
                if (grow >= 1.0F) ribbon(vc, m, hi[i], new Vec3(0, top + 0.5, 0), cam, 0.04F, 255, 200, 210, a / 2);   // 上の尖り
            }
            return;
        }
        for (int i = 0; i < 6; i++) {                                                     // 面ごとに 4 つの三角の破片
            Vec3 p0 = lo[i], p1 = lo[(i + 1) % 6], q0 = hi[i], q1 = hi[(i + 1) % 6];
            Vec3 mid = p0.add(p1).add(q0).add(q1).scale(0.25);
            Vec3[][] tris = {{p0, p1, mid}, {p1, q1, mid}, {q1, q0, mid}, {q0, p0, mid}};
            for (Vec3[] tri : tris) {
                Vec3 c = tri[0].add(tri[1]).add(tri[2]).scale(1.0 / 3.0);
                Vec3 out = c.multiply(1, 0, 1).normalize();
                Vec3 vel = out.scale(0.12 + rnd.nextDouble() * 0.12).add(0, 0.05 + rnd.nextDouble() * 0.1, 0);
                Vec3 move = vel.scale(t).add(0, -0.004 * t * t, 0);
                double spin = t * (0.1 + rnd.nextDouble() * 0.2);
                Vec3 axis = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize();
                float shrinkT = 0.55F * alpha + 0.15F;
                Vec3[] pts = new Vec3[3];
                for (int k = 0; k < 3; k++) pts[k] = c.add(move).add(rotate(tri[k].subtract(c).scale(shrinkT), axis, spin));
                boolean gold = rnd.nextInt(4) == 0;
                quad(vc, m, pts[0], pts[1], pts[2], pts[2], 255, gold ? 209 : 90, gold ? 102 : 120, (int) (150 * alpha));
                ribbon(vc, m, pts[0], pts[1], cam, 0.03F, 255, 255, 255, (int) (230 * alpha));
                ribbon(vc, m, pts[1], pts[2], cam, 0.03F, 255, 255, 255, (int) (230 * alpha));
                ribbon(vc, m, pts[2], pts[0], cam, 0.03F, 255, 255, 255, (int) (230 * alpha));
            }
        }
    }

    /** v を axis（長さ 1）のまわりに ang 回す（ロドリゲスの回転） */
    private static Vec3 rotate(Vec3 v, Vec3 axis, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
    }

    // ------------------------------------------------------------------ 振り子の断頭台（2026-10-20）

    /**
     * 振り子の断頭台: 置いた所（相手の頭上）を支点に、yaw の向きの面で振れる巨大な三日月の刃。size = 腕の長さ、mode = 予告の Tick、spin = 振れ幅。
     * 支点の金の文字盤、刃の通る弧（予告の間は赤く）、鎖の輪を連ねた腕、白く縁取られた三日月の刃と、振った跡の 2 つの残像。角度は当たりと同じ式（{@code TimeRulerEntity.pendulumAngle}）
     */
    private static void pendulum(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float fade) {
        float L = fx.size(), amp = fx.spin();
        int warn = fx.mode();
        double yaw = fx.yaw();
        Vec3 d = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)), up = new Vec3(0, 1, 0), n = d.cross(up).normalize();
        int a = (int) (230 * fade * Mth.clamp(age / 6.0F, 0.0F, 1.0F));
        boolean warning = age < warn;
        // ---- 支点の文字盤
        annulus(vc, m, Vec3.ZERO, d, up, 1.3F, 1.45F, 32, age * 0.05F, 0, 255, 209, 102, a);
        annulus(vc, m, Vec3.ZERO, d, up, 0.0F, 0.35F, 12, 0, 0, 255, 255, 255, a);
        for (int h = 0; h < 12; h++) radialAt(vc, m, Vec3.ZERO, d, up, Math.PI * 2 * h / 12 + age * 0.05F, 0.95F, 1.25F, h % 3 == 0 ? 0.06F : 0.03F, 255, 240, 200, a);
        for (int h = 0; h < 4; h++) {
            double ang = Math.PI / 2 - Math.PI / 2 * h;
            Vec3 o = dir(d, up, ang);
            numeral(vc, m, HOURS[h * 3], o.scale(1.85), up, n, 0.4F, 255, 240, 200, a);
        }
        // ---- 刃の通る弧
        Vec3 prevArc = null;
        for (int i = 0; i <= 24; i++) {
            double ph = -amp + 2 * amp * i / 24;
            Vec3 p = d.scale(Math.sin(ph) * L).add(0, -Math.cos(ph) * L, 0);
            if (prevArc != null) ribbon(vc, m, prevArc, p, cam, warning ? 0.12F : 0.06F, 255, warning ? 64 : 220, warning ? 96 : 150, (int) (a * (warning ? 0.8F : 0.35F)));
            prevArc = p;
        }
        // ---- 振った跡の残像と、腕と刃
        for (int ghost = 2; ghost >= 0; ghost--) {
            float th = com.automatavalkyries.entity.chrono.TimeRulerEntity.pendulumAngle(age - ghost * 2.0F, warn, amp);
            int ga = ghost == 0 ? a : a / (ghost * 3);
            Vec3 bob = d.scale(Math.sin(th) * L).add(0, -Math.cos(th) * L, 0);
            Vec3 arm = bob.normalize();
            if (ghost == 0) {
                ribbon(vc, m, Vec3.ZERO, bob, cam, 0.4F, 255, 196, 90, ga);                       // 腕
                ribbon(vc, m, Vec3.ZERO, bob, cam, 0.1F, 255, 255, 255, ga);
                Vec3 side = arm.cross(n).normalize();
                for (float t = 1.6F; t < L - 1.0F; t += 1.4F) annulus(vc, m, arm.scale(t), n, side, 0.22F, 0.32F, 10, 0, 0, 255, 220, 150, ga);   // 鎖の輪
            }
            double beta = Math.atan2(bob.dot(up), bob.dot(d));                                   // 腕の向きの角度（d, up の面で）
            Vec3 cc = bob.subtract(arm.scale(1.0));
            int segs = 20;
            for (int i = 0; i < segs; i++) {                                                     // 三日月の刃（腕の先で横に広がり、真ん中ほど厚い）
                double p0 = -1.5 + 3.0 * i / segs, p1 = -1.5 + 3.0 * (i + 1) / segs;
                float w0 = 0.9F * Mth.cos((float) (p0 / 1.5 * Math.PI / 2)), w1 = 0.9F * Mth.cos((float) (p1 / 1.5 * Math.PI / 2));
                Vec3 o0 = cc.add(dir(d, up, beta + p0).scale(2.8)), o1 = cc.add(dir(d, up, beta + p1).scale(2.8));
                Vec3 i0 = cc.add(dir(d, up, beta + p0).scale(2.8 - w0)), i1 = cc.add(dir(d, up, beta + p1).scale(2.8 - w1));
                quad(vc, m, i0, o0, o1, i1, 255, 196, 90, (int) (ga * 0.85F));
                ribbon(vc, m, o0, o1, cam, 0.12F, 255, 255, 255, ga);                            // 白い刃先
            }
            if (ghost == 0) {
                annulus(vc, m, bob, d, up, 0.0F, 0.55F, 12, 0, 0, 176, 112, 255, ga);              // 刃の真ん中の宝石
                numeral(vc, m, "XII", bob.subtract(arm.scale(0.4)), arm.scale(-1), n, 0.5F, 255, 255, 255, ga);
            }
        }
    }

    // ------------------------------------------------------------------ 砂時計の牢（2026-10-20）

    /** 光の砂時計（高さ size × 2）: 金の枠と柱、硝子の線、上の砂が減って細い流れになり下に積もる（life の間に落ちきる）。上に残りの秒 */
    private static void hourglass(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life, float fade) {
        float h = fx.size();
        float k = Mth.clamp(age / Math.max(1, life - 8), 0.0F, 1.0F);
        int a = (int) (230 * fade * Mth.clamp(age / 6.0F, 0.0F, 1.0F));
        Vec3 ux = new Vec3(1, 0, 0), uz = new Vec3(0, 0, 1), up = new Vec3(0, 1, 0);
        float rim = 0.9F, neck = 0.12F;
        annulus(vc, m, new Vec3(0, 2 * h, 0), ux, uz, rim, rim + 0.2F, 24, 0, 0, 255, 209, 102, a);       // 上下の枠
        annulus(vc, m, new Vec3(0, 0.05, 0), ux, uz, rim, rim + 0.2F, 24, 0, 0, 255, 209, 102, a);
        for (int i = 0; i < 3; i++) {                                                                      // 柱
            double ang = Math.PI * 2 * i / 3 + fx.yaw();
            Vec3 o = new Vec3(Math.cos(ang) * (rim + 0.1), 0, Math.sin(ang) * (rim + 0.1));
            ribbon(vc, m, o.add(0, 0.05, 0), o.add(0, 2 * h, 0), cam, 0.12F, 255, 209, 102, a);
        }
        for (int i = 0; i < 6; i++) {                                                                      // 硝子
            double ang = Math.PI * 2 * i / 6;
            Vec3 o = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            ribbon(vc, m, o.scale(rim * 0.92).add(0, 2 * h, 0), o.scale(neck).add(0, h, 0), cam, 0.04F, 255, 255, 255, a / 2);
            ribbon(vc, m, o.scale(neck).add(0, h, 0), o.scale(rim * 0.92).add(0, 0.05, 0), cam, 0.04F, 255, 255, 255, a / 2);
        }
        float top = h + h * 0.85F * (1.0F - k);                                                            // 上の砂（減っていく）
        for (int i = 0; i < 5; i++) {
            float y = h + (top - h) * (i + 1) / 5.0F;
            float r = neck + (rim * 0.85F - neck) * (y - h) / h;
            annulus(vc, m, new Vec3(0, y, 0), ux, uz, 0.0F, r, 16, 0, 0, 255, 190, 90, (int) (a * 0.6F));
        }
        if (k < 1.0F) ribbon(vc, m, new Vec3(0, h, 0), new Vec3(0, 0.1 + h * 0.8 * k, 0), cam, 0.06F, 255, 220, 140, a);   // 落ちる砂の流れ
        float pile = h * 0.8F * k;                                                                         // 下に積もる砂
        for (int i = 0; i < 5; i++) {
            float y = 0.06F + pile * i / 5.0F;
            float r = rim * 0.85F * (1.0F - (float) i / 5.0F) * Mth.clamp(k * 3.0F, 0.0F, 1.0F);
            annulus(vc, m, new Vec3(0, y, 0), ux, uz, 0.0F, r, 16, 0, 0, 255, 190, 90, (int) (a * 0.6F));
        }
        int secs = Mth.clamp((int) Math.ceil((life - 8 - age) / 20.0F), 1, 9);
        Vec3 over = new Vec3(0, 2 * h + 0.8, 0);
        numeral(vc, m, roman(secs), over, up, toCam(over, cam), 0.6F, 255, 220, 140, a);
    }

    // ------------------------------------------------------------------ 崩れる文字盤（2026-10-20）

    /**
     * 崩れる文字盤の 1 つの時の扇（yaw = 扇の真ん中の向き、spin = 扇の幅、size = 半径、mode = 崩れるまでの Tick）。
     * はじめは薄い縁だけ、崩れる 0.8 秒前から赤く明滅してその時のローマ数字が立ち、崩れると白く光ってひびが走り薄れる
     */
    private static void sector(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life) {
        float R = fx.size(), w = fx.spin();
        int fall = fx.mode();
        double c = fx.yaw();
        int hour = Math.floorMod(Math.round((float) ((c + Math.PI / 2) / (Math.PI / 6))), 12);
        Vec3 up = new Vec3(0, 1, 0);
        boolean lit = age >= fall - 16, fallen = age >= fall;
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            double a0 = c - w / 2 + w * i / segs, a1 = c - w / 2 + w * (i + 1) / segs;
            Vec3 i0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)).scale(0.6), i1 = new Vec3(Math.cos(a1), 0, Math.sin(a1)).scale(0.6);
            Vec3 o0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)).scale(R), o1 = new Vec3(Math.cos(a1), 0, Math.sin(a1)).scale(R);
            if (fallen) {
                float k = 1.0F - Mth.clamp((age - fall) / Math.max(1, life - fall), 0.0F, 1.0F);
                quad(vc, m, i0, o0, o1, i1, 255, 245, 235, (int) (170 * k));
            } else if (lit) {
                float blink = (int) (age / 2) % 2 == 0 ? 1.0F : 0.6F;
                quad(vc, m, i0, o0, o1, i1, 255, 40, 70, (int) (120 * blink));
            }
            ribbon(vc, m, o0, o1, cam, 0.08F, 255, 64, 96, lit ? 230 : 60);                      // 外の縁
        }
        Vec3 e0 = new Vec3(Math.cos(c - w / 2), 0, Math.sin(c - w / 2)).scale(R), e1 = new Vec3(Math.cos(c + w / 2), 0, Math.sin(c + w / 2)).scale(R);
        ribbon(vc, m, Vec3.ZERO, e0, cam, 0.06F, 255, 64, 96, lit ? 220 : 60);                     // 扇の境目
        ribbon(vc, m, Vec3.ZERO, e1, cam, 0.06F, 255, 64, 96, lit ? 220 : 60);
        Vec3 mid = new Vec3(Math.cos(c), 0, Math.sin(c)).scale(R * 0.62);
        if (lit && !fallen) numeral(vc, m, HOURS[hour], mid.add(0, 1.2, 0), up, toCam(mid, cam), 1.3F, 255, 64, 96, 255);
        if (fallen) {
            float t = age - fall, k = 1.0F - Mth.clamp(t / Math.max(1, life - fall), 0.0F, 1.0F);
            numeral(vc, m, HOURS[hour], mid.add(0, 1.2 + t * 0.25, 0), up, toCam(mid, cam), 1.3F, 255, 255, 255, (int) (255 * k));
            java.util.Random rnd = new java.util.Random(fx.getId() * 17L);                         // 走るひび
            for (int i = 0; i < 4; i++) {
                double ang = c + (rnd.nextDouble() - 0.5) * w;
                Vec3 p = new Vec3(Math.cos(ang), 0, Math.sin(ang)).scale(0.8).add(0, 0.03, 0);
                for (int sgm = 0; sgm < 4; sgm++) {
                    ang += (rnd.nextDouble() - 0.5) * 0.6;
                    Vec3 q = p.add(Math.cos(ang) * R / 4.5, 0, Math.sin(ang) * R / 4.5);
                    ribbon(vc, m, p, q, cam, 0.07F, 255, 255, 255, (int) (230 * k));
                    p = q;
                }
            }
        }
    }

    // ------------------------------------------------------------------ 時空疾走の道（2026-10-20）

    /** 時空疾走の道（yaw の向きへ長さ size、mode = 予告の Tick）: 予告の間は赤く明滅する帯と先へ流れる山形、駆け抜けた後は金の光の筋と白い芯が薄れる */
    private static void lane(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, int life) {
        float L = fx.size(), W = 1.6F;
        int warn = fx.mode() & (RulerFxEntity.LANE_SEGMENTS - 1);
        boolean segments = (fx.mode() & RulerFxEntity.LANE_SEGMENTS) != 0;
        double yaw = fx.yaw();
        Vec3 d = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)), side = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), up = new Vec3(0, 1, 0);
        Vec3 f = new Vec3(0, 0.04, 0);
        if (age < warn) {
            float blink = (int) (age / 2) % 2 == 0 ? 1.0F : 0.55F;
            quad(vc, m, f.add(side.scale(W)), f.add(d.scale(L)).add(side.scale(W)), f.add(d.scale(L)).subtract(side.scale(W)), f.subtract(side.scale(W)), 255, 40, 70, (int) (70 * blink));
            ribbon(vc, m, f.add(side.scale(W)), f.add(d.scale(L)).add(side.scale(W)), cam, 0.14F, 255, 64, 96, (int) (230 * blink));
            ribbon(vc, m, f.subtract(side.scale(W)), f.add(d.scale(L)).subtract(side.scale(W)), cam, 0.14F, 255, 64, 96, (int) (230 * blink));
            for (float t = (age * 0.8F) % 2.5F; t < L; t += 2.5F) {
                Vec3 c0 = f.add(d.scale(t));
                ribbon(vc, m, c0.add(side.scale(W)), c0.add(d.scale(1.0)), cam, 0.1F, 255, 120, 140, (int) (180 * blink));
                ribbon(vc, m, c0.subtract(side.scale(W)), c0.add(d.scale(1.0)), cam, 0.1F, 255, 120, 140, (int) (180 * blink));
            }
            return;
        }
        float k = 1.0F - Mth.clamp((age - warn) / Math.max(1, life - warn), 0.0F, 1.0F);
        Vec3 h = new Vec3(0, 1.6, 0);
        ribbon(vc, m, h, h.add(d.scale(L)), cam, 3.0F * k, 255, 196, 90, (int) (120 * k));
        ribbon(vc, m, h, h.add(d.scale(L)), cam, 0.3F, 255, 255, 255, (int) (255 * k));
        for (int i = 1; i < 6; i++) {                                                           // 駆け抜けた所に立つローマ数字（結晶槍は 7 セグの 0 と 1）
            Vec3 at = h.add(d.scale(L * i / 6.0F)).add(0, 1.2 + (1 - k) * 1.5, 0);
            if (segments) {
                boolean one = ((fx.getId() * 31 + i * 7) & 4) != 0;
                segmentDigit(vc, m, at, up, toCam(at, cam), 0.9F, one, one ? 79 : 255, one ? 227 : 90, one ? 255 : 216, (int) (255 * k));
            } else {
                numeral(vc, m, HOURS[(i * 2) % 12], at, up, toCam(at, cam), 0.8F, 255, 240, 200, (int) (255 * k));
            }
        }
    }


    /** 7 セグの 0 か 1（at は真ん中、up は上、n は面の向き、h は高さ） */
    static void segmentDigit(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 up, Vec3 n, float h, boolean one, int r, int g, int b, int a) {
        Vec3 right = up.cross(n).normalize();
        float w = h * 0.55F, t = h * 0.13F;
        float[][] bars = one ? new float[][]{{w - t, h / 2 + 0.02F, w, h}, {w - t, 0, w, h / 2 - 0.02F}}
                : new float[][]{{0, h - t, w, h}, {0, 0, w, t}, {0, h / 2, t, h}, {0, 0, t, h / 2}, {w - t, h / 2, w, h}, {w - t, 0, w, h / 2}};
        Vec3 origin = at.subtract(right.scale(w / 2)).subtract(up.scale(h / 2));
        for (float[] bar : bars) {
            Vec3 p0 = origin.add(right.scale(bar[0])).add(up.scale(bar[1])), p1 = origin.add(right.scale(bar[2])).add(up.scale(bar[1]));
            Vec3 p2 = origin.add(right.scale(bar[2])).add(up.scale(bar[3])), p3 = origin.add(right.scale(bar[0])).add(up.scale(bar[3]));
            quad(vc, m, p0, p1, p2, p3, r, g, b, a);
        }
    }

    // ------------------------------------------------------------------ 描く道具

    /** a から b への、カメラを向いた帯 */
    static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float width, int r, int g, int bl, int alpha) {
        if (alpha <= 0 || width <= 0) return;
        Vec3 axis = b.subtract(a);
        Vec3 side = axis.cross(cam.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1.0e-8) return;
        side = side.normalize().scale(width / 2.0);
        quad(vc, m, a.add(side), a.subtract(side), b.subtract(side), b.add(side), r, g, bl, alpha);
    }

    static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int bl, int alpha) {
        int al = Math.max(0, Math.min(255, alpha));
        if (al == 0) return;
        for (Vec3[] q : new Vec3[][]{{a, b, c, d}, {d, c, b, a}}) {
            for (Vec3 p : q) vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, bl, al).endVertex();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(RulerFxEntity fx) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
