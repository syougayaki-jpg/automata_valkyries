package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.BlackHoleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * ブラックホール弾の見た目: 常にこちらを向く板に、黒い穴と渦巻く降着円盤の絵（textures/entity/black_hole.png）を重ねて回す。
 * 直径 5 ブロック。崩壊中は脈打ちながら縮み、最後に一瞬膨らむ。
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHoleEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/black_hole.png");
    private static final float DIAMETER = 5.0F;

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BlackHoleEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = entity.tickCount + partialTick;
        float size = DIAMETER * entity.scale();
        int collapse = entity.collapseAge();
        if (collapse >= 0) {
            float t = (collapse + partialTick) / BlackHoleEntity.COLLAPSE;
            size *= t < 0.85F ? 1.0F - 0.35F * t + 0.06F * (float) Math.sin(time * 1.6) : 0.7F + (t - 0.85F) * 6F;
        } else {
            size *= Math.min(1F, time / 6F); // 撃った直後は小さく出て広がる
        }
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() * 0.5, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(time * 9F));
        pose.scale(size, size, size);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc, m, n, -0.5F, -0.5F, 0, 1);
        vertex(vc, m, n, 0.5F, -0.5F, 1, 1);
        vertex(vc, m, n, 0.5F, 0.5F, 1, 0);
        vertex(vc, m, n, -0.5F, 0.5F, 0, 0);
        // 逆向きに回る 2 枚目（渦の重なり）
        pose.mulPose(Axis.ZP.rotationDegrees(-time * 15F));
        pose.scale(0.8F, 0.8F, 0.8F);
        m = pose.last().pose();
        vertex(vc, m, n, -0.5F, -0.5F, 1, 1);
        vertex(vc, m, n, 0.5F, -0.5F, 0, 1);
        vertex(vc, m, n, 0.5F, 0.5F, 0, 0);
        vertex(vc, m, n, -0.5F, 0.5F, 1, 0);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHoleEntity entity) {
        return TEXTURE;
    }
}
