package com.automatavalkyries.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * メメントモリの蝶の描き方（2026-10-10。蝶ビット・蝶弾が使う）: 体の向き fwd・上 up の、左右 2 枚ずつ（前翅・後翅）の翅の多角形を、
 * 体の軸のまわりに羽ばたかせて描く。翅の面は黒い半透明（debugQuads）、縁・直角に折れる回路の線・菱形の放射口は光（lightning。紫。第 2 形態は光体の水色と桃色で、翅の面も淡い光。2026-10-14）。
 * 生き物らしい目の紋は付けない（2026-10-10 ユーザー指定「生物的な目はなく機械的な冷たい印象で」）
 */
final class MementoFx {

    /** 前翅・後翅の形（翅の付け根から: x = 外へ、y = 前へ。大きさ 1 のとき） */
    private static final float[][] FORE = {{0.0F, 0.06F}, {0.32F, 0.30F}, {0.62F, 0.36F}, {0.70F, 0.16F}, {0.52F, -0.04F}, {0.0F, -0.04F}};
    private static final float[][] HIND = {{0.0F, -0.04F}, {0.44F, -0.10F}, {0.50F, -0.34F}, {0.30F, -0.52F}, {0.08F, -0.36F}};

    private MementoFx() {
    }

    /**
     * 蝶を 1 匹描く。c は中心（描く原点からのずれ）、size は翅を広げた片側の長さ（ブロック）、flap は羽ばたきの角度（ラジアン。0 で水平）、
     * dark = 翅の面を描く（蝶弾は光だけ）、radiant = 第 2 形態の光体（水色の縁・桃色の芯・淡い光の面）、alpha = 濃さ（0〜1）
     */
    static void butterfly(MultiBufferSource buffers, Matrix4f m, Vec3 c, Vec3 fwd, Vec3 up, float size, float flap, boolean dark, boolean radiant, float alpha) {
        Vec3 f = fwd.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : fwd.normalize();
        Vec3 side = f.cross(up.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : up);
        if (side.lengthSqr() < 1.0e-6) side = f.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        Vec3 u = side.cross(f).normalize();                                          // 体の背の向き
        int gr = radiant ? 150 : 164, gg = radiant ? 230 : 92, gb = 255;                    // 紫（第 2 形態は光体の水色）
        int al = (int) (255 * Mth.clamp(alpha, 0.0F, 1.0F));
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (int s = -1; s <= 1; s += 2) {
            // 翅の外向き: 横の向きを、体の軸のまわりに羽ばたきの角度だけ上へ傾ける
            Vec3 out = side.scale(s * Mth.cos(flap)).add(u.scale(Mth.sin(flap)));
            for (float[][] wing : new float[][][]{FORE, HIND}) {
                Vec3[] p = new Vec3[wing.length];
                for (int i = 0; i < wing.length; i++) p[i] = c.add(out.scale(wing[i][0] * size)).add(f.scale(wing[i][1] * size));
                if (dark) {
                    VertexConsumer fill = buffers.getBuffer(RenderType.debugQuads());
                    for (int i = 1; i + 1 < p.length; i++) {
                        if (radiant) quad(fill, m, p[0], p[i], p[i + 1], p[i + 1], 255, 190, 245, (int) (al * 0.45F));   // 淡い桃色の光の面
                        else quad(fill, m, p[0], p[i], p[i + 1], p[i + 1], 12, 14, 20, (int) (al * 0.92F));
                    }
                }
                for (int i = 0; i < p.length; i++) {                                // 光る縁
                    GalliaSiegeBeamRenderer.line(glow, m, p[i], p[(i + 1) % p.length], 0.012F * size + 0.006F, gr, gg, gb, (int) (al * 0.9F));
                }
                // 回路の線: 付け根から外へまっすぐ、途中で直角に折れて翅の先へ
                Vec3 bend = p[0].add(p[2].subtract(p[0]).multiply(0.5, 0.5, 0.5));
                Vec3 knee = c.add(out.scale(out.dot(bend.subtract(c)))).add(f.scale(f.dot(p[0].subtract(c))));
                GalliaSiegeBeamRenderer.line(glow, m, p[0], knee, 0.008F * size + 0.004F, gr, gg, gb, (int) (al * 0.6F));
                GalliaSiegeBeamRenderer.line(glow, m, knee, bend, 0.008F * size + 0.004F, gr, gg, gb, (int) (al * 0.6F));
            }
            // 前翅の菱形の放射口（光る縁と白い芯）
            Vec3 node = c.add(out.scale(0.45F * size)).add(f.scale(0.16F * size));
            Vec3 na = node.add(f.scale(0.07 * size)), nb = node.add(out.scale(0.055 * size)), nc = node.subtract(f.scale(0.07 * size)), nd = node.subtract(out.scale(0.055 * size));
            Vec3[] dia = {na, nb, nc, nd};
            for (int i = 0; i < 4; i++) GalliaSiegeBeamRenderer.line(glow, m, dia[i], dia[(i + 1) % 4], 0.012F * size + 0.004F, gr, gg, gb, al);
            GalliaSiegeBeamRenderer.line(glow, m, node.subtract(f.scale(0.015 * size)), node.add(f.scale(0.015 * size)), 0.02F * size, 255, radiant ? 150 : 238, radiant ? 235 : 255, al);
        }
        // 体（光る細い線）と頭
        GalliaSiegeBeamRenderer.line(glow, m, c.subtract(f.scale(0.35 * size)), c.add(f.scale(0.22 * size)), 0.03F * size, gr, gg, gb, al);
        GalliaSiegeBeamRenderer.line(glow, m, c.add(f.scale(0.2 * size)), c.add(f.scale(0.26 * size)), 0.05F * size, 255, 255, 255, al);
    }

    static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int bl, int al) {
        for (Vec3 v : new Vec3[]{a, b, c, d}) vc.vertex(m, (float) v.x, (float) v.y, (float) v.z).color(r, g, bl, al).endVertex();
    }
}
