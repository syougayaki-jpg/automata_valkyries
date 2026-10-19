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
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 光の文字盤

    /** 光の文字盤: 外の 2 重の輪（切れ目つき）・12 の時の目盛り（3・6・9・12 は太い）・60 の分の刻み・回る文字の輪・針・真ん中の光 */
    private static void clock(VertexConsumer vc, Matrix4f m, RulerFxEntity fx, Vec3 cam, float age, float grow, float fade, int r, int g, int b) {
        int mode = fx.mode();
        float R = fx.size() * (0.6F + 0.4F * grow);
        Vec3 u, v;
        if ((mode & RulerFxEntity.FACING) != 0) {                                     // 向きの面（yaw の向きを向く縦の文字盤）
            float y = fx.yaw();
            u = new Vec3(Math.cos(y), 0, Math.sin(y));
            v = new Vec3(0, 1, 0);
        } else {                                                                      // 床の文字盤
            u = new Vec3(1, 0, 0);
            v = new Vec3(0, 0, 1);
        }
        float rot = age * 0.01F;
        int a = (int) (200 * fade * grow);
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
        for (int k = 0; k < 24; k++) {                                               // 回る文字の輪（光の点と線）
            double ang = Math.PI * 2 * k / 24 - age * 0.02F;
            float len = k % 2 == 0 ? 0.05F : 0.025F;
            radial(vc, m, u, v, ang, R * (0.66F - len), R * (0.66F + len), R * 0.012F, 255, 240, 200, (int) (a * 0.75F));
        }
        // 針
        double minute, hour;
        if ((mode & RulerFxEntity.HANDS_FINALE) != 0) {                              // 刻の終焉: 当たりと同じ角度（短針 0.02・長針 0.06 rad / Tick）
            hour = age * 0.02;
            minute = age * 0.06;
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
                minute = dir * age * 0.25 + Math.PI / 2;
                hour = dir * age * 0.03 + Math.PI / 2 + 1.0;
            }
            hand(vc, m, u, v, hour, R * 0.5F, R * 0.035F, cam, 255, 240, 200, a);
            hand(vc, m, u, v, minute, R * 0.74F, R * 0.025F, cam, r, g, b, a);
        }
        annulus(vc, m, u, v, 0.0F, R * 0.06F, 16, 0, 0, 255, 255, 255, a);           // 真ん中の光
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
        for (int k = 0; k < 12; k++) {
            double ang = base + k * Math.PI / 6;
            Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 end = d.scale(len);
            if (warn) {
                float blink = (int) (age / 3) % 2 == 0 ? 1.0F : 0.5F;
                ribbon(vc, m, Vec3.ZERO, end, cam, 0.08F, 255, 64, 96, (int) (200 * blink));
                continue;
            }
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
