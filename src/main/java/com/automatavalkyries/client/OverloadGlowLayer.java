package com.automatavalkyries.client;

import com.automatavalkyries.weapon.Overload;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * オーバーロードの兵装の光（2026-10-04 ユーザー指定）: 光る部分（{@code _glowmask}）をもう一度、紅から金へ脈打つ色で光を足して描く。
 */
public class OverloadGlowLayer extends GeoRenderLayer<TacticalWeaponItem> {

    public OverloadGlowLayer(GeoRenderer<TacticalWeaponItem> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, TacticalWeaponItem animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!(getRenderer() instanceof GeoItemRenderer<TacticalWeaponItem> item) || !Overload.is(item.getCurrentItemStack())) return;
        var level = Minecraft.getInstance().level;
        float time = level == null ? 0 : level.getGameTime() + partialTick;
        float k = 0.5F + 0.5F * Mth.sin(time * 0.25F); // 0 = 紅、1 = 金
        float r = 1F, g = 0.13F + 0.62F * k, b = 0.31F - 0.06F * k;
        float pulse = 0.65F + 0.35F * Mth.sin(time * 0.5F);
        RenderType glow = AutoGlowingTexture.getRenderType(getTextureResource(animatable));
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                0xF000F0, packedOverlay, r * pulse, g * pulse, b * pulse, 1F);
    }
}
