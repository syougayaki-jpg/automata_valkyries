package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.QuantumSlashEntity;
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
 * クアンタムブレードの三日月の斬撃の見た目（自作の重ね描き）。刃の向き（縦・横・斜め）に幅 3 ブロックで、
 * 刃と進む向きを含む面に、進む向きへふくらむ三日月を描く（2026-10-03 ユーザー指定。刃の縁が先頭）。
 * 後ろから見ても細い線にならないよう、刃に直角な向きへ少しずつずらした薄い層を重ねて厚みを出す。
 * <ol>
 *   <li>残影: 後ろへ 5 枚、だんだん薄く小さく、水色から紫へ（光を足す描き方）</li>
 *   <li>にじむ光: 本体より大きな柔らかい光の三日月。脈打つ（光を足す）</li>
 *   <li>本体: 白い縁から水色・紫へ移る三日月と量子の格子（半透明）</li>
 *   <li>芯: 外の縁の細く鋭い白い線。ちらつく（光を足す）</li>
 * </ol>
 * 出た直後は浅く鋭く広がり、射程の終わりで薄れて消える。
 */
public class QuantumSlashRenderer extends EntityRenderer<QuantumSlashEntity> {

    private static final ResourceLocation BODY = tex("quantum_slash"), GLOW = tex("quantum_slash_glow"), CORE = tex("quantum_slash_core");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/" + name + ".png");
    }

    public QuantumSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(QuantumSlashEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 dir = entity.getDeltaMovement().lengthSqr() > 1.0E-6 ? entity.getDeltaMovement().normalize() : entity.getLookAngle();
        Vec3 axis = entity.bladeAxis();
        Vec3 normal = axis.cross(dir).normalize(); // 三日月の面に直角（厚みを出す向き）
        float age = entity.tickCount + partialTick;
        float grow = Math.min(1F, age / 2.5F);
        float fade = age > QuantumSlashEntity.LIFETIME - 2 ? Math.max(0F, (QuantumSlashEntity.LIFETIME + 1 - age) / 3F) : 1F;
        float half = (float) QuantumSlashEntity.HALF_WIDTH * (0.6F + 0.4F * grow);
        float depth = 0.45F + 0.4F * grow; // 進む向きへのふくらみ（出た直後は浅い）
        float pulse = 1F + 0.08F * (float) Math.sin(age * 2.2F);
        float flicker = 0.75F + 0.25F * (float) Math.sin(age * 7.3F + entity.getId());
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        // 絵の上（ふくらみ）を進む向き dir へ向ける
        VertexConsumer additiveGlow = buffers.getBuffer(RenderType.eyes(GLOW));
        // 1. 残影（後ろへ。だんだん薄く小さく、水色 → 紫）
        double speed = entity.getDeltaMovement().length();
        for (int k = 5; k >= 1; k--) {
            float f = 1F - k / 6F;
            Vec3 back = dir.scale(-k * speed * 0.45);
            float s = half * (1F - k * 0.06F);
            int r = (int) (90 + (138 - 90) * (k / 5F)), g = (int) (232 - (232 - 124) * (k / 5F));
            float b = f * 0.55F * fade;
            quad(additiveGlow, m, n, back, axis.scale(s), dir.scale(s * depth), scale(r, b), scale(g, b), scale(255, b), 255);
        }
        // 2. にじむ光（厚みの層を 3 枚、脈打つ）
        float gs = half * 1.3F * pulse;
        for (int k = -1; k <= 1; k++) {
            float b = (k == 0 ? 0.9F : 0.5F) * fade;
            quad(additiveGlow, m, n, normal.scale(k * 0.14), axis.scale(gs), dir.scale(gs * depth), scale(110, b), scale(200, b), scale(255, b), 255);
        }
        // 3. 本体（厚みの層を 3 枚）
        VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucentEmissive(BODY));
        for (int k = -1; k <= 1; k++) {
            int alpha = (int) (255 * fade * (k == 0 ? 1F : 0.55F));
            quad(body, m, n, normal.scale(k * 0.07), axis.scale(half), dir.scale(half * depth), 255, 255, 255, alpha);
        }
        // 4. 芯（細く鋭い白い線、ちらつく）
        VertexConsumer core = buffers.getBuffer(RenderType.eyes(CORE));
        float cs = half * 1.02F;
        int c = scale(255, flicker * fade);
        quad(core, m, n, Vec3.ZERO, axis.scale(cs), dir.scale(cs * depth), c, c, c, 255);
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
    public ResourceLocation getTextureLocation(QuantumSlashEntity entity) {
        return BODY;
    }
}
