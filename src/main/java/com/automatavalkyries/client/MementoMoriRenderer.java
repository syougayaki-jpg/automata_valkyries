package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * メメントモリのレンダラー（2026-10-10）: モデルを {@link MementoMoriEntity#SIZE} 倍にして描く（身長約 5.5 ブロック、翅を広げて約 10 ブロック）。
 * 翅は半透明の光の膜なので半透明で描く（2026-10-13）。翅は広いので、体の当たり判定が画面の外でも描く。
 * 撃破では倒れて回らない（らせんを描いて落ちる動きはエンティティの側）
 */
public class MementoMoriRenderer extends ChronoGazerRenderer<MementoMoriEntity> {

    public MementoMoriRenderer(EntityRendererProvider.Context context) {
        super(context, "memento_mori", 0.0F);
    }

    @Override
    public RenderType getRenderType(MementoMoriEntity mori, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    protected float getDeathMaxRotation(MementoMoriEntity mori) {
        return 0.0F;
    }

    @Override
    public boolean shouldRender(MementoMoriEntity mori, Frustum frustum, double x, double y, double z) {
        return mori.isAlive() || mori.isDefeatedSynced() ? frustum.isVisible(mori.getBoundingBox().inflate(6.0)) : super.shouldRender(mori, frustum, x, y, z);
    }

    @Override
    public void render(MementoMoriEntity mori, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.scale(MementoMoriEntity.SIZE, MementoMoriEntity.SIZE, MementoMoriEntity.SIZE);
        super.render(mori, entityYaw, partialTick, pose, buffers, light);
        pose.popPose();
    }
}
