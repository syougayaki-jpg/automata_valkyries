package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.RainbowSlashEntity;
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
 * ルミナス・レクイエムの虹の斬撃の見た目（自作の重ね描き。2026-10-04 ユーザー指定）。刃の向きに幅 5 ブロックの虹の三日月
 * （外の縁が白く、外から赤・橙・黄・緑・青・紫）を、進む向きへふくらませて描く。
 * <ol>
 *   <li>残影: 後ろへ 6 枚、だんだん薄く小さく（光を足す）</li>
 *   <li>にじむ光: 本体より大きな柔らかい虹の三日月。脈打つ（光を足す）</li>
 *   <li>本体: 虹の三日月（半透明）。厚みの層を 3 枚</li>
 *   <li>きらめき: 本体と同じ絵を薄く光で足して、ちらつかせる</li>
 * </ol>
 */
public class RainbowSlashRenderer extends EntityRenderer<RainbowSlashEntity> {

    private static final ResourceLocation BODY = tex("rainbow_slash"), GLOW = tex("rainbow_slash_glow");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/" + name + ".png");
    }

    public RainbowSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RainbowSlashEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 dir = entity.getDeltaMovement().lengthSqr() > 1.0E-6 ? entity.getDeltaMovement().normalize() : entity.getLookAngle();
        Vec3 axis = entity.bladeAxis();
        Vec3 normal = axis.cross(dir).normalize();
        float age = entity.tickCount + partialTick;
        float grow = Math.min(1F, age / 3F);
        float fade = age > RainbowSlashEntity.LIFETIME - 3 ? Math.max(0F, (RainbowSlashEntity.LIFETIME + 1 - age) / 4F) : 1F;
        float half = (float) entity.halfWidth() * (0.55F + 0.45F * grow);
        float depth = 0.5F + 0.35F * grow;
        float pulse = 1F + 0.07F * (float) Math.sin(age * 2.0F);
        float flicker = 0.6F + 0.4F * (float) Math.sin(age * 6.1F + entity.getId());
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        double speed = entity.getDeltaMovement().length();
        for (int k = 6; k >= 1; k--) {
            float b = (1F - k / 7F) * 0.5F * fade;
            float s = half * (1F - k * 0.05F);
            int c = scale(255, b);
            quad(glow, m, n, dir.scale(-k * speed * 0.4), axis.scale(s), dir.scale(s * depth), c, c, c, 255);
        }
        float gs = half * 1.25F * pulse;
        for (int k = -1; k <= 1; k++) {
            int c = scale(255, (k == 0 ? 0.95F : 0.5F) * fade);
            quad(glow, m, n, normal.scale(k * 0.16), axis.scale(gs), dir.scale(gs * depth), c, c, c, 255);
        }
        VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucentEmissive(BODY));
        for (int k = -1; k <= 1; k++) {
            quad(body, m, n, normal.scale(k * 0.08), axis.scale(half), dir.scale(half * depth), 255, 255, 255, (int) (255 * fade * (k == 0 ? 1F : 0.55F)));
        }
        VertexConsumer shine = buffers.getBuffer(RenderType.eyes(BODY));
        int c = scale(255, 0.45F * flicker * fade);
        quad(shine, m, n, Vec3.ZERO, axis.scale(half * 1.01F), dir.scale(half * depth * 1.01F), c, c, c, 255);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static int scale(int v, float f) {
        return Math.max(0, Math.min(255, Math.round(v * f)));
    }

    /** 中心 center、横 = u、縦 = v の板を両面で。絵の上（三日月のふくらみ）が +v */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 center, Vec3 u, Vec3 v, int r, int g, int b, int alpha) {
        Vec3[] corners = {center.subtract(u).subtract(v), center.add(u).subtract(v), center.add(u).add(v), center.subtract(u).add(v)};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                Vec3 c = corners[i];
                vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, b, alpha).uv(uv[i][0], uv[i][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(RainbowSlashEntity entity) {
        return BODY;
    }
}
