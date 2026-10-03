package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.MiniCatastropheEntity;
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
 * 極小のカタストロフの見た目（2026-10-04 ユーザー指定）: こちらを向く板に、ブラックホール弾と同じ黒い穴と降着円盤の絵（直径 0.9）を逆向きに 2 枚回し、
 * まわりに紫と紅の光の輪と渦（{@code mini_catastrophe_glow}、光を足す）を脈打たせる。
 */
public class MiniCatastropheRenderer extends EntityRenderer<MiniCatastropheEntity> {

    private static final ResourceLocation HOLE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/black_hole.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/mini_catastrophe_glow.png");

    public MiniCatastropheRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MiniCatastropheEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = entity.tickCount + partialTick;
        float grow = Math.min(1F, time / 4F) * entity.scale();
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() * 0.5, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        // 外の光の輪と渦（脈打つ）
        pose.pushPose();
        float g = 1.9F * grow * (1F + 0.12F * (float) Math.sin(time * 0.9F));
        pose.mulPose(Axis.ZP.rotationDegrees(-time * 14F));
        pose.scale(g, g, g);
        quad(buffers.getBuffer(RenderType.eyes(GLOW)), pose, 255, 255);
        pose.popPose();
        // 黒い穴と降着円盤（逆向きに 2 枚）
        VertexConsumer hole = buffers.getBuffer(RenderType.entityTranslucentEmissive(HOLE));
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(time * 20F));
        float s = 0.9F * grow;
        pose.scale(s, s, s);
        quad(hole, pose, 255, 255);
        pose.mulPose(Axis.ZP.rotationDegrees(-time * 34F));
        pose.scale(0.8F, 0.8F, 0.8F);
        quad(hole, pose, 255, 255);
        pose.popPose();
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void quad(VertexConsumer vc, PoseStack pose, int c, int alpha) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float[][] p = {{-0.5F, -0.5F, 0, 1}, {0.5F, -0.5F, 1, 1}, {0.5F, 0.5F, 1, 0}, {-0.5F, 0.5F, 0, 0}};
        for (float[] q : p) {
            vc.vertex(m, q[0], q[1], 0).color(c, c, c, alpha).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(MiniCatastropheEntity entity) {
        return HOLE;
    }
}
