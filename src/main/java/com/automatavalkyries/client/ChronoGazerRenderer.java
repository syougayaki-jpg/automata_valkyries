package com.automatavalkyries.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** クロノゲイザーの汎用機のレンダラー（赤い目・発光部は *_glowmask.png で光らせる） */
public class ChronoGazerRenderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T> {

    public ChronoGazerRenderer(EntityRendererProvider.Context context, String name, float shadow) {
        super(context, new ChronoGazerModel<>(name));
        this.shadowRadius = shadow;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
