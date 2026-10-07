package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.ChronoBolt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** クロノゲイザーの弾: カメラを向いた赤い光の玉（外側の赤いにじみと白い芯。砲弾は大きく） */
public class ChronoBoltRenderer extends EntityRenderer<ChronoBolt> {

    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/fx/glow.png");

    public ChronoBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChronoBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float size = bolt.isShell() ? 0.9F : 0.5F;
        pose.pushPose();
        pose.translate(0, bolt.getBbHeight() / 2.0F, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
        quad(pose, vc, size, 255, 42, 74);
        quad(pose, vc, size * 0.45F, 255, 220, 230);
        pose.popPose();
        super.render(bolt, yaw, partialTick, pose, buffers, light);
    }

    private static void quad(PoseStack pose, VertexConsumer vc, float s, int r, int g, int b) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float h = s / 2.0F;
        vertex(vc, m, n, -h, -h, 0, 1, r, g, b);
        vertex(vc, m, n, h, -h, 1, 1, r, g, b);
        vertex(vc, m, n, h, h, 1, 0, r, g, b);
        vertex(vc, m, n, -h, h, 0, 0, r, g, b);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int r, int g, int b) {
        vc.vertex(m, x, y, 0).color(r, g, b, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ChronoBolt bolt) {
        return GLOW;
    }
}
