package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.ChakramEntity;
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

/** プラズマチャクラムの見た目: 寝かせた円盤（刃のある輪の絵）が高速で回る。光を足す描き方で、外の光の輪も重ねる */
public class ChakramRenderer extends EntityRenderer<ChakramEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/plasma_chakram.png");

    public ChakramRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChakramEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, 0.15, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 48F));
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TEXTURE));
        disc(vc, pose, 0.75F, 255, 255, 255);
        disc(vc, pose, 1.05F, 120, 40, 110); // 外の光の輪（淡い）
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void disc(VertexConsumer vc, PoseStack pose, float r, int red, int green, int blue) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float[][] c = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                vc.vertex(m, c[i][0], 0, c[i][1]).color(red, green, blue, 255).uv(uv[i][0], uv[i][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ChakramEntity entity) {
        return TEXTURE;
    }
}
