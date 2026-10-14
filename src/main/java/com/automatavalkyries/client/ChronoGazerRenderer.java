package com.automatavalkyries.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** クロノゲイザーの汎用機のレンダラー（赤い目・発光部は *_glowmask.png で光らせる） */
public class ChronoGazerRenderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T> {

    public ChronoGazerRenderer(EntityRendererProvider.Context context, String name, float shadow) {
        super(context, new ChronoGazerModel<>(name));
        this.shadowRadius = shadow;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack pose, T animatable, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                               float partialTick, int light, int overlay) {
                float alpha = glowAlpha(animatable, partialTick);
                if (alpha <= 0.0F) return;
                RenderType glow = getRenderType(animatable);
                getRenderer().reRender(model, pose, buffers, animatable, glow, buffers.getBuffer(glow), partialTick, LightTexture.FULL_SKY,
                        OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
            }
        });
    }

    /** 発光部の不透明度（体を透けさせる演出のある機体が上書きする。2026-10-14 メメントモリの登場・撃破） */
    protected float glowAlpha(T animatable, float partialTick) {
        return 1.0F;
    }
}
