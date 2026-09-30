package com.automatavalkyries.client;

import com.automatavalkyries.entity.ElenorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** エレノアのレンダラー（GeckoLib: 長い髪と広がるスカート） */
public class ElenorRenderer extends GeoEntityRenderer<ElenorEntity> {

    public ElenorRenderer(EntityRendererProvider.Context context) {
        super(context, new ElenorModel());
        this.shadowRadius = 0.5F;
    }

    /** バニラの横倒しはしない（撃破演出はモデル側の座り込み） */
    @Override
    protected float getDeathMaxRotation(ElenorEntity animatable) {
        return 0.0F;
    }

    /** 被弾時だけ赤く光らせ、撃破演出中は赤くしない */
    @Override
    public int getPackedOverlay(ElenorEntity animatable, float u) {
        return OverlayTexture.pack(OverlayTexture.u(u),
                OverlayTexture.v(animatable.hurtTime > 0 && animatable.deathTime == 0));
    }
}
