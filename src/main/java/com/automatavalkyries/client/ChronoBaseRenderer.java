package com.automatavalkyries.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** 侵食基地の雑魚・中ボス・コンデンサーのレンダラー（2026-10-15。赤い目・紫の核などの発光部は *_glowmask.png で光らせる。動きは {@link ChronoBaseModel}） */
public class ChronoBaseRenderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T> {

    public ChronoBaseRenderer(EntityRendererProvider.Context context, String name, float shadow) {
        super(context, new ChronoBaseModel<>(name));
        this.shadowRadius = shadow;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack pose, T animatable, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                               float partialTick, int light, int overlay) {
                RenderType glow = getRenderType(animatable);
                getRenderer().reRender(model, pose, buffers, animatable, glow, buffers.getBuffer(glow), partialTick, LightTexture.FULL_SKY,
                        OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        });
    }
}
