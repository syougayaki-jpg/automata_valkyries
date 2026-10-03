package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 剣の刃先の薄い光のレイヤー（2026-10-04 ユーザー指定）: 刃の形（根元から先へ細る台形と尖った先）に沿った光の縁と、
 * 根元から先へ数秒ごとに走る光の帯。加算で描く（黒は見えない）。描く面は今の姿勢の xy 平面（表からも裏からも見える）。
 */
public final class BladeAura {

    private static final ResourceLocation AURA = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/blade_aura.png");
    private static final ResourceLocation SHEEN = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/blade_sheen.png");
    /** テクスチャの光の縁は、横幅の 72% の所にある（刃の幅をこの割合で割った幅で描くと、縁が刃の縁に重なる） */
    private static final float EDGE_AT = 0.72F;

    /** 色を上書きする（0xRRGGBB、-1 = しない）と明るさの倍率。オーバーロードの兵装の刃で使う（{@link BladeAuraLayer}） */
    static int tint = -1;
    static float tintBrightness = 1F;

    private BladeAura() {}

    /**
     * 刃の光（今の姿勢の xy 平面で、(bx, by) の根元から角度 ang 度の向きへ長さ len。根元の幅 w0・先の幅 w1 は刃の幅）。
     * phase は光の帯の走り出しをずらす値、brightness は明るさ（0〜1）。color は 0xRRGGBB
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, float bx, float by, float ang, float len, float w0, float w1,
                            float time, float phase, float brightness, int color) {
        if (tint >= 0) {
            color = tint;
            brightness *= tintBrightness;
        }
        float r = ((color >> 16) & 255) / 255F * brightness, g = ((color >> 8) & 255) / 255F * brightness, b = (color & 255) / 255F * brightness;
        float dx = Mth.cos(ang * Mth.DEG_TO_RAD), dy = Mth.sin(ang * Mth.DEG_TO_RAD);
        float px = -dy, py = dx; // 刃の横
        float h0 = w0 / EDGE_AT / 2, h1 = w1 / EDGE_AT / 2;
        float shoulder = 0.78F; // 先の 22% は尖りをテクスチャに任せる
        // 縁の光: 根元（v=1）→ 肩（v=0.22）→ 先（v=0）の 2 段の台形
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(AURA));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float sx = bx + dx * len * shoulder, sy = by + dy * len * shoulder, tx = bx + dx * len, ty = by + dy * len;
        float shimmer = 0.9F + 0.25F * Mth.sin(time * 0.35F + phase * 3F);
        quad(vc, m, n, bx - px * h0, by - py * h0, bx + px * h0, by + py * h0, sx + px * h1, sy + py * h1, sx - px * h1, sy - py * h1,
                1F, 0.22F, r * shimmer, g * shimmer, b * shimmer);
        quad(vc, m, n, sx - px * h1, sy - py * h1, sx + px * h1, sy + py * h1, tx + px * h1, ty + py * h1, tx - px * h1, ty - py * h1,
                0.22F, 0F, r * shimmer, g * shimmer, b * shimmer);
        // 走る光の帯: 3 秒ごとに根元から先へ（その間は休む）
        float cycle = ((time * 0.02F + phase) % 1F) * 1.6F;
        if (cycle < 1F) {
            float c0 = Math.max(0F, cycle - 0.09F), c1 = Math.min(1F, cycle + 0.09F);
            float w = h0 + (h1 - h0) * Math.min(1F, cycle / shoulder);
            float ax = bx + dx * len * c0, ay = by + dy * len * c0, ex = bx + dx * len * c1, ey = by + dy * len * c1;
            float fade = Mth.sin(cycle * Mth.PI) * 1.4F;
            VertexConsumer sv = buffers.getBuffer(RenderType.eyes(SHEEN));
            quad(sv, m, n, ax - px * w, ay - py * w, ax + px * w, ay + py * w, ex + px * w, ey + py * w, ex - px * w, ey - py * w,
                    1F, 0F, r * fade, g * fade, b * fade);
        }
    }

    /**
     * 虹の刃の光（{@link #draw} と同じ形で、根元から先へ赤・橙・黄・緑・青・紫と移り、色が時間で先へ流れる。2026-10-04 ユーザー指定、ルミナス・レクイエム）。
     * 走る光の帯は白く光る
     */
    public static void drawRainbow(PoseStack pose, MultiBufferSource buffers, float bx, float by, float ang, float len, float w0, float w1,
                                   float time, float phase, float brightness) {
        if (tint >= 0) { // オーバーロードでは虹ではなくオーバーロードの色
            draw(pose, buffers, bx, by, ang, len, w0, w1, time, phase, brightness, tint);
            return;
        }
        float dx = Mth.cos(ang * Mth.DEG_TO_RAD), dy = Mth.sin(ang * Mth.DEG_TO_RAD);
        float px = -dy, py = dx;
        float h0 = w0 / EDGE_AT / 2, h1 = w1 / EDGE_AT / 2;
        float shoulder = 0.78F;
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(AURA));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float shimmer = (0.9F + 0.25F * Mth.sin(time * 0.35F + phase * 3F)) * brightness;
        int slices = 12;
        for (int i = 0; i < slices; i++) {
            float t0 = i / (float) slices, t1 = (i + 1) / (float) slices;
            float hw0 = t0 < shoulder ? h0 + (h1 - h0) * t0 / shoulder : h1, hw1 = t1 < shoulder ? h0 + (h1 - h0) * t1 / shoulder : h1;
            float v0 = t0 < shoulder ? 1F - 0.78F * t0 / shoulder : 0.22F * (1F - (t0 - shoulder) / (1F - shoulder));
            float v1 = t1 < shoulder ? 1F - 0.78F * t1 / shoulder : 0.22F * (1F - (t1 - shoulder) / (1F - shoulder));
            float ax = bx + dx * len * t0, ay = by + dy * len * t0, ex = bx + dx * len * t1, ey = by + dy * len * t1;
            int c0 = hue(t0 * 0.85F - time * 0.015F), c1 = hue(t1 * 0.85F - time * 0.015F);
            float[][] p = {{ax - px * hw0, ay - py * hw0, 0, v0}, {ax + px * hw0, ay + py * hw0, 1, v0}, {ex + px * hw1, ey + py * hw1, 1, v1}, {ex - px * hw1, ey - py * hw1, 0, v1}};
            int[] col = {c0, c0, c1, c1};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    int j = pass == 0 ? k : 3 - k;
                    float[] q = p[j];
                    int c = col[j];
                    vc.vertex(m, q[0], q[1], 0).color(Mth.clamp(((c >> 16) & 255) / 255F * shimmer, 0F, 1F), Mth.clamp(((c >> 8) & 255) / 255F * shimmer, 0F, 1F),
                                    Mth.clamp((c & 255) / 255F * shimmer, 0F, 1F), 1F).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(0xF000F0).normal(n, 0, 0, pass == 0 ? 1 : -1).endVertex();
                }
            }
        }
        float cycle = ((time * 0.02F + phase) % 1F) * 1.6F;
        if (cycle < 1F) {
            float c0 = Math.max(0F, cycle - 0.09F), c1 = Math.min(1F, cycle + 0.09F);
            float w = h0 + (h1 - h0) * Math.min(1F, cycle / shoulder);
            float ax = bx + dx * len * c0, ay = by + dy * len * c0, ex = bx + dx * len * c1, ey = by + dy * len * c1;
            float fade = Mth.sin(cycle * Mth.PI) * 1.2F * brightness;
            VertexConsumer sv = buffers.getBuffer(RenderType.eyes(SHEEN));
            quad(sv, m, n, ax - px * w, ay - py * w, ax + px * w, ay + py * w, ex + px * w, ey + py * w, ex - px * w, ey - py * w, 1F, 0F, fade, fade, fade);
        }
    }

    /** 虹の色（0〜1 を巡る。赤 → 紫 → 赤へなめらかに戻る） */
    private static int hue(float t) {
        float h = ((t % 1F) + 1F) % 1F;
        return java.awt.Color.HSBtoRGB(h, 0.7F, 1F);
    }

    /**
     * 曲がった刃・輪の刃の光（今の姿勢の xy 平面で、点 (xs[i], ys[i]) を順につなぐ線に沿う。ws[i] はその点での刃の幅）。
     * tipped = 最後の点で尖る（刃の先）、false なら同じ幅の光の帯。closed = 最後の点から最初の点へ戻る輪（帯で描く）
     */
    public static void drawPath(PoseStack pose, MultiBufferSource buffers, float[] xs, float[] ys, float[] ws, boolean tipped, boolean closed,
                                float time, float phase, float brightness, int color) {
        int n = xs.length;
        if (n < 2) return;
        if (tint >= 0) {
            color = tint;
            brightness *= tintBrightness;
        }
        float r = ((color >> 16) & 255) / 255F * brightness, g = ((color >> 8) & 255) / 255F * brightness, b = (color & 255) / 255F * brightness;
        float shimmer = 0.9F + 0.25F * Mth.sin(time * 0.35F + phase * 3F);
        int segs = closed ? n : n - 1;
        float[] cum = new float[n + 1];
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % n;
            cum[i + 1] = cum[i] + Mth.sqrt((xs[j] - xs[i]) * (xs[j] - xs[i]) + (ys[j] - ys[i]) * (ys[j] - ys[i]));
        }
        float total = cum[segs];
        if (total < 1.0E-5F) return;
        // 点ごとの横の向き（前後の辺の横の向きの平均。つなぎ目に隙間を作らない）
        float[] nx = new float[n], ny = new float[n];
        for (int i = 0; i < n; i++) {
            int prev = closed ? (i - 1 + n) % n : Math.max(0, i - 1), next = closed ? (i + 1) % n : Math.min(n - 1, i + 1);
            float dx = xs[next] - xs[prev], dy = ys[next] - ys[prev];
            float len = Mth.sqrt(dx * dx + dy * dy);
            if (len < 1.0E-6F) { nx[i] = 0; ny[i] = 1; continue; }
            nx[i] = -dy / len;
            ny[i] = dx / len;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(AURA));
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % n;
            float hi = ws[i] / EDGE_AT / 2, hj = ws[j] / EDGE_AT / 2;
            float vi = tipped && !closed ? 1F - cum[i] / total : 0.6F, vj = tipped && !closed ? 1F - cum[i + 1] / total : 0.6F;
            quad(vc, m, nm, xs[i] - nx[i] * hi, ys[i] - ny[i] * hi, xs[i] + nx[i] * hi, ys[i] + ny[i] * hi,
                    xs[j] + nx[j] * hj, ys[j] + ny[j] * hj, xs[j] - nx[j] * hj, ys[j] - ny[j] * hj, vi, vj, r * shimmer, g * shimmer, b * shimmer);
        }
        // 走る光の帯（線に沿って進む）
        float cycle = ((time * 0.02F + phase) % 1F) * 1.6F;
        if (cycle >= 1F) return;
        float d = cycle * total, band = Math.max(total * 0.08F, 0.06F);
        int i = 0;
        while (i < segs - 1 && cum[i + 1] < d) i++;
        int j = (i + 1) % n;
        float seg = Math.max(1.0E-5F, cum[i + 1] - cum[i]), t = (d - cum[i]) / seg;
        float cx = xs[i] + (xs[j] - xs[i]) * t, cy = ys[i] + (ys[j] - ys[i]) * t;
        float dx = (xs[j] - xs[i]) / seg, dy = (ys[j] - ys[i]) / seg, px = -dy, py = dx;
        float w = (ws[i] + (ws[j] - ws[i]) * t) / EDGE_AT / 2;
        float fade = Mth.sin(cycle * Mth.PI) * 1.4F;
        VertexConsumer sv = buffers.getBuffer(RenderType.eyes(SHEEN));
        float ax = cx - dx * band, ay = cy - dy * band, ex = cx + dx * band, ey = cy + dy * band;
        quad(sv, m, nm, ax - px * w, ay - py * w, ax + px * w, ay + py * w, ex + px * w, ey + py * w, ex - px * w, ey - py * w,
                1F, 0F, r * fade, g * fade, b * fade);
    }

    /** 4 点（根元左・根元右・先右・先左）の板を、テクスチャの縦 v0（根元）〜 v1（先）で描く。表と裏 */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                             float v0, float v1, float r, float g, float b) {
        r = Mth.clamp(r, 0F, 1F); // 1 を超えると色の値があふれて暗く見える
        g = Mth.clamp(g, 0F, 1F);
        b = Mth.clamp(b, 0F, 1F);
        float[][] p = {{x0, y0, 0, v0}, {x1, y1, 1, v0}, {x2, y2, 1, v1}, {x3, y3, 0, v1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] q = p[pass == 0 ? i : 3 - i];
                vc.vertex(m, q[0], q[1], 0).color(r, g, b, 1F).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(0xF000F0).normal(n, 0, 0, pass == 0 ? 1 : -1).endVertex();
            }
        }
    }
}
