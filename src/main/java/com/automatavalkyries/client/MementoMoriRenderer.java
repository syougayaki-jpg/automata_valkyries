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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.core.object.Color;
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
        return mori.isAlive() || mori.isDefeatedSynced() ? frustum.isVisible(mori.getBoundingBox().inflate(mori.stage() != MementoMoriEntity.STAGE_NONE || mori.isDefeatedSynced() ? 18.0 : 6.0))
                : super.shouldRender(mori, frustum, x, y, z);                              // 登場・撃破の蝶の群れは遠くまで広がる
    }

    /** 体の不透明度（登場で現れ、撃破・退場で透けて消える。{@link MementoShow#bodyAlpha}） */
    @Override
    public Color getRenderColor(MementoMoriEntity mori, float partialTick, int packedLight) {
        return Color.ofRGBA(1.0F, 1.0F, 1.0F, MementoShow.bodyAlpha(mori, partialTick));
    }

    @Override
    protected float glowAlpha(MementoMoriEntity mori, float partialTick) {
        return MementoShow.bodyAlpha(mori, partialTick);
    }

    @Override
    public void render(MementoMoriEntity mori, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.scale(MementoMoriEntity.SIZE, MementoMoriEntity.SIZE, MementoMoriEntity.SIZE);
        super.render(mori, entityYaw, partialTick, pose, buffers, light);
        pose.popPose();
        MementoSwarm.render(mori, partialTick, pose, buffers);
        renderAscension(mori, partialTick, pose, buffers);
    }

    /**
     * 第 2 形態への昇華（{@link MementoShow}）: 0〜60 は胸の核へ縮んでいく光の輪、30〜75 は足元から空への光の柱（外は水色、芯は白）、
     * 60 の閃光で横へ広がる桃色と水色の光の輪
     */
    private static void renderAscension(MementoMoriEntity mori, float pt, PoseStack pose, MultiBufferSource buffers) {
        float aw = MementoShow.stageT(mori, MementoMoriEntity.STAGE_AWAKEN, pt);
        if (aw < 0.0F) return;
        Matrix4f m = pose.last().pose();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] ab = GalliaSiegeBeamRenderer.basis(up);
        com.mojang.blaze3d.vertex.VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Vec3 chest = up.scale(3.6);
        if (aw < 60.0F) { // 核へ縮んでいく光の輪（3 本ずつずらして）
            for (int k = 0; k < 3; k++) {
                float u = ((aw + k * 5.0F) % 15.0F) / 15.0F;
                float r = 5.0F * (1.0F - u);
                int al = (int) (200 * u * MementoShow.smooth(aw / 10.0F));
                boolean pink = k % 2 == 0;
                GalliaSiegeBeamRenderer.ring(glow, m, chest.add(0, (k - 1) * 0.6, 0), ab[0], ab[1], Math.max(0.0F, r - 0.15F), r, pink ? 255 : 143, pink ? 140 : 230, 255, al);
            }
        }
        if (aw >= 30.0F && aw < 80.0F) { // 空への光の柱
            float grow = MementoShow.smooth((aw - 30.0F) / 8.0F), fade = 1.0F - MementoShow.smooth((aw - 62.0F) / 18.0F);
            float pulse = 0.85F + 0.15F * Mth.sin(aw * 1.3F);
            GalliaSiegeBeamRenderer.tube(glow, m, up, ab[0], ab[1], -8.0F, 120.0F * grow, 1.8F * pulse, 143, 230, 255, (int) (110 * fade));
            GalliaSiegeBeamRenderer.tube(glow, m, up, ab[0], ab[1], -8.0F, 120.0F * grow, 0.7F, 255, 255, 255, (int) (220 * fade));
            GalliaSiegeBeamRenderer.tube(glow, m, up, ab[0], ab[1], -8.0F, 120.0F * grow, 2.8F * pulse, 255, 140, 235, (int) (50 * fade));
        }
        if (aw >= 60.0F && aw < 78.0F) { // 閃光の光の輪
            float u = (aw - 60.0F) / 18.0F;
            float r = 3.0F + 18.0F * MementoShow.smooth(u);
            int al = (int) (230 * (1.0F - u));
            GalliaSiegeBeamRenderer.ring(glow, m, chest, ab[0], ab[1], r - 0.6F, r, 255, 140, 235, al);
            GalliaSiegeBeamRenderer.ring(glow, m, chest, ab[0], ab[1], r * 0.8F - 0.4F, r * 0.8F, 143, 230, 255, al);
        }
    }

    /** 光体の重ね描き（第 2 形態のふだんの光と、登場・昇華・撃破・退場の光） */
    private static final class LumenLayer extends GeoRenderLayer<MementoMoriEntity> {
        private static final ResourceLocation LUMEN = ResourceLocation.fromNamespaceAndPath("automata_valkyries", "textures/entity/memento_mori_lumen.png");

        LumenLayer(MementoMoriRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, MementoMoriEntity mori, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                           float partialTick, int light, int overlay) {
            float age = mori.tickCount + partialTick;
            // 第 2 形態のふだんの脈打つ出力と、演出（登場・昇華・撃破・退場。どちらの形態でも）の光（MementoShow）
            float k = MementoShow.radiantPower(mori, partialTick) * (0.62F + 0.14F * Mth.sin(age * 0.15F) + 0.08F * Mth.sin(age * 0.37F + 1.0F))
                    + MementoShow.showLight(mori, partialTick);
            if (k <= 0.01F) return;
            k = Math.min(1.0F, k);
            float hue = age * 0.03F;                                                                              // 紫 → 桃色 → 水色へゆっくり移る
            float r = 0.8F + 0.2F * Mth.sin(hue), g = 0.72F + 0.28F * Mth.sin(hue + 2.1F), b = 0.9F + 0.1F * Mth.sin(hue + 4.2F);
            RenderType lumen = RenderType.eyes(LUMEN);
            getRenderer().reRender(model, pose, buffers, mori, lumen, buffers.getBuffer(lumen), partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    r * k, g * k, b * k, 1.0F);
        }
    }
}
