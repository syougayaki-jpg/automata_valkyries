package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.CargoContainerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/** 荷役機が投げる・落とすコンテナ（2026-10-15）: 回りながら飛ぶ樽 */
public class CargoContainerRenderer extends EntityRenderer<CargoContainerEntity> {

    public CargoContainerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(CargoContainerEntity cargo, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = cargo.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0.0, 0.5, 0.0);
        pose.mulPose(Axis.XP.rotationDegrees(age * 18.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(age * 11.0F));
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.BARREL.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(cargo, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CargoContainerEntity cargo) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
