package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.PunishmentBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 光の帯（パニッシュメント・レールガンのレール・ジェネシス・ヘイローの光の柱など）: 始まりから終わりまで、外の光・中の光・白い芯を重ねた長い光の帯
 * （光を足す描き方。直角に交わる 3 枚の板で、どこから見ても太く見える）。帯に沿って光の節が流れ、寿命をかけて細く薄れる。色の組と太さは光の帯ごと。
 */
public class PunishmentBeamRenderer extends EntityRenderer<PunishmentBeamEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/punishment_beam.png");

    public PunishmentBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PunishmentBeamEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 span = entity.span();
        double length = span.length();
        if (length < 0.1) return;
        float age = entity.tickCount + partialTick;
        float t = Math.min(1F, age / entity.lifetime());
        float shrink = (float) Math.pow(1 - t, 0.6);
        float bright = t < 0.1F ? 1F : (1 - t) / 0.9F;
        float w = entity.width();
        Vec3 dir = span.scale(1 / length);
        Vec3 a = dir.cross(new Vec3(0, 1, 0));
        if (a.lengthSqr() < 1.0E-4) a = new Vec3(1, 0, 0);
        a = a.normalize();
        Vec3 b = dir.cross(a).normalize();
        Vec3 c = a.add(b).normalize();
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TEXTURE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float flow = age * 0.6F; // 帯に沿って流れる光の節（絵の縦を流す）
        Vec3[] axes = {a, b, c};
        int[][] colors = switch (entity.scheme()) {
            case PunishmentBeamEntity.SCHEME_GENESIS -> new int[][]{{0xFF, 0xB0, 0x30}, {0xFF, 0xE8, 0x90}, {0xFF, 0xFF, 0xF0}};
            case PunishmentBeamEntity.SCHEME_STELLA -> new int[][]{{0x50, 0x80, 0xFF}, {0x9A, 0xE8, 0xFF}, {0xFF, 0xFF, 0xFF}};
            case PunishmentBeamEntity.SCHEME_PRISM -> {
                // 虹: 時間で色が巡る
                float hue = (age * 0.03F) % 1F;
                int o = java.awt.Color.HSBtoRGB(hue, 0.8F, 1F), mid = java.awt.Color.HSBtoRGB((hue + 0.33F) % 1F, 0.6F, 1F);
                yield new int[][]{{(o >> 16) & 255, (o >> 8) & 255, o & 255}, {(mid >> 16) & 255, (mid >> 8) & 255, mid & 255}, {0xFF, 0xFF, 0xFF}};
            }
            default -> new int[][]{{0xE0, 0x20, 0x40}, {0xFF, 0xC0, 0x40}, {0xFF, 0xF6, 0xD8}};
        };
        // 外（太い）→ 中 → 芯（細い）
        layer(vc, m, n, span, length, axes, 2.6F * w * shrink, colors[0][0], colors[0][1], colors[0][2], bright * 0.75F, flow);
        layer(vc, m, n, span, length, axes, 1.4F * w * shrink, colors[1][0], colors[1][1], colors[1][2], bright * 0.9F, flow * 1.5F);
        layer(vc, m, n, span, length, axes, 0.5F * w * (0.4F + 0.6F * shrink), colors[2][0], colors[2][1], colors[2][2], bright, flow * 2F);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void layer(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 span, double length, Vec3[] axes, float width,
                              int r, int g, int b, float bright, float flow) {
        int cr = Math.round(r * bright), cg = Math.round(g * bright), cb = Math.round(b * bright);
        float v0 = flow, v1 = (float) (flow + length / 6.0); // 6 ブロックごとに光の節
        for (Vec3 axis : axes) {
            Vec3 w = axis.scale(width / 2);
            Vec3[] corners = {w.scale(-1), w, span.add(w), span.subtract(w)};
            float[][] uv = {{0, v0}, {1, v0}, {1, v1}, {0, v1}};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    int i = pass == 0 ? k : 3 - k;
                    Vec3 p = corners[i];
                    vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(cr, cg, cb, 255).uv(uv[i][0], uv[i][1])
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
                }
            }
        }
    }

    @Override
    public boolean shouldRender(PunishmentBeamEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    public ResourceLocation getTextureLocation(PunishmentBeamEntity entity) {
        return TEXTURE;
    }
}
