package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * メメントモリのレンダラー（2026-10-10）: モデルを {@link MementoMoriEntity#SIZE} 倍にして描く（身長約 5.5 ブロック、翅を広げて約 10 ブロック）。
 * 翅は半透明の光の膜なので半透明で描く（2026-10-13）。翅は広いので、体の当たり判定が画面の外でも描く。
 * 撃破では倒れて回らない（らせんを描いて落ちる動きはエンティティの側）。
 * 第 2 形態は全身が光体になる（2026-10-14 ユーザー指定「赤くなるというより出力を上げほぼ幻想的な光体となる演出に」）: 明るさを最大に上げた同じ並びの絵
 * （memento_mori_lumen.png、tools/gen_memento_mori.py の lumen()）を足し算の光で全身に重ね、紫・桃色・水色へゆっくり色を移しながら脈打たせる。
 * 目覚めでは光が満ちていき、撃破では消えていく
 */
public class MementoMoriRenderer extends ChronoGazerRenderer<MementoMoriEntity> {

    public MementoMoriRenderer(EntityRendererProvider.Context context) {
        super(context, "memento_mori", 0.0F);
        addRenderLayer(new LumenLayer(this));
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

    /** 第 2 形態の光体の重ね描き */
    private static final class LumenLayer extends GeoRenderLayer<MementoMoriEntity> {
        private static final ResourceLocation LUMEN = ResourceLocation.fromNamespaceAndPath("automata_valkyries", "textures/entity/memento_mori_lumen.png");

        LumenLayer(MementoMoriRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, MementoMoriEntity mori, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                           float partialTick, int light, int overlay) {
            if (!mori.isPhase2()) return;
            float age = mori.tickCount + partialTick;
            float power = 1.0F;
            if (mori.stage() == MementoMoriEntity.STAGE_AWAKEN) power = Mth.clamp((age - mori.stageStart - 4.0F) / 16.0F, 0.0F, 1.0F);   // 光が満ちていく
            if (mori.isDefeatedSynced()) power *= 1.0F - Mth.clamp((mori.defeatTicks + partialTick) / MementoMoriEntity.DEFEAT_TICKS, 0.0F, 1.0F);
            if (power <= 0.0F) return;
            float k = power * (0.62F + 0.14F * Mth.sin(age * 0.15F) + 0.08F * Mth.sin(age * 0.37F + 1.0F));      // 脈打つ出力
            float hue = age * 0.03F;                                                                              // 紫 → 桃色 → 水色へゆっくり移る
            float r = 0.8F + 0.2F * Mth.sin(hue), g = 0.72F + 0.28F * Mth.sin(hue + 2.1F), b = 0.9F + 0.1F * Mth.sin(hue + 4.2F);
            RenderType lumen = RenderType.eyes(LUMEN);
            getRenderer().reRender(model, pose, buffers, mori, lumen, buffers.getBuffer(lumen), partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    r * k, g * k, b * k, 1.0F);
        }
    }
}
