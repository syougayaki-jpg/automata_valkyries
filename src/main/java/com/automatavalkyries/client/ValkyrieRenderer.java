package com.automatavalkyries.client;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * 戦術兵姫のレンダラー（仮。2026-10-04）。光る所（{@code *_glowmask.png}）と、胸のコアのエンドポータルのように流れる星空を重ねる。
 */
public class ValkyrieRenderer extends GeoEntityRenderer<ValkyrieEntity> {

    public ValkyrieRenderer(EntityRendererProvider.Context context) {
        super(context, new ValkyrieModel());
        this.shadowRadius = 0.45F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        addRenderLayer(new CoreStarsLayer(this));
        addRenderLayer(new HeldItemLayer(this));
    }

    /** バニラの横倒しはしない（機能停止はモデル側で座り込む予定） */
    @Override
    protected float getDeathMaxRotation(ValkyrieEntity animatable) {
        return 0.0F;
    }

    @Override
    public int getPackedOverlay(ValkyrieEntity animatable, float u) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0));
    }

    /**
     * 手に持った兵装（右手 = 主、左手 = 副。段階 2、2026-10-05 ユーザー指定「手持ちの兵装だけ出す」）。腕の骨の先（手首）へ動かして描く。位置・向きは【仮】
     */
    static class HeldItemLayer extends software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer<ValkyrieEntity> {
        /** 腕の骨の付け根から手首までの長さ（px）。腕は付け根 y=20.1 から y=11 まで */
        private static final float ARM_LENGTH = 8.6F / 16.0F;

        HeldItemLayer(GeoRenderer<ValkyrieEntity> renderer) {
            super(renderer);
        }

        @Override
        protected net.minecraft.world.item.ItemStack getStackForBone(GeoBone bone, ValkyrieEntity animatable) {
            return switch (bone.getName()) {
                case "right_arm" -> animatable.getMainHandItem();
                case "left_arm" -> animatable.getOffhandItem();
                default -> null;
            };
        }

        @Override
        protected net.minecraft.world.item.ItemDisplayContext getTransformTypeForStack(GeoBone bone, net.minecraft.world.item.ItemStack stack, ValkyrieEntity animatable) {
            return bone.getName().equals("left_arm") ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, net.minecraft.world.item.ItemStack stack, ValkyrieEntity animatable,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            poseStack.pushPose();
            poseStack.translate(0.0F, -ARM_LENGTH, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
            poseStack.scale(0.8F, 0.8F, 0.8F);
            super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }

    /**
     * 胸のコア（core の骨）に、バニラのエンドポータルの描き方で星空を重ねる（画面に貼り付いたまま星が流れて、奥に別の空間が見える）。
     * 白い縁が残るよう、骨の中心で横と縦を少し縮め、手前へ少しだけ出して描く。
     * 別の描き方のバッファを使ったあとは、元の描き方のバッファを取り直す（GeckoLib は骨を描く途中でこの層を呼ぶため）。
     */
    static class CoreStarsLayer extends GeoRenderLayer<ValkyrieEntity> {
        CoreStarsLayer(GeoRenderer<ValkyrieEntity> renderer) {
            super(renderer);
        }

        @Override
        public void renderForBone(PoseStack poseStack, ValkyrieEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!bone.getName().equals("core") || bone.isHidden()) return;
            poseStack.pushPose();
            RenderUtils.translateToPivotPoint(poseStack, bone);
            poseStack.scale(0.72F, 0.72F, 1.12F);
            RenderUtils.translateAwayFromPivotPoint(poseStack, bone);
            getRenderer().renderCubesOfBone(poseStack, bone, bufferSource.getBuffer(RenderType.endGateway()), packedLight, packedOverlay, 1F, 1F, 1F, 1F);
            poseStack.popPose();
            bufferSource.getBuffer(renderType);
        }
    }
}
