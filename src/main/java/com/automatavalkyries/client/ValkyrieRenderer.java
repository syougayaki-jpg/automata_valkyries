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

    /** T4 兵装・ジェネシス・ヘイロー・ルミナス・レクイエムの背の飾り（GeckoLib には RenderLivingEvent が来ないので、ここから呼ぶ） */
    @Override
    public void render(ValkyrieEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (entity.isInvisible() || portraitPass) return;
        T4BackdropClient.render(entity, poseStack, bufferSource, partialTick);
        GenesisBackdropClient.render(entity, poseStack, bufferSource, partialTick);
        RequiemBackdropClient.render(entity, poseStack, bufferSource, partialTick);
        ValkyrieOverhead.render(entity, poseStack, bufferSource, partialTick, entityRenderDispatcher, getFont()); // 名前・バー・感情アイコン・吹き出し
    }

    /** HUD の顔（{@link ValkyrieHud#drawFace}）を描いている最中か: 背の飾り・頭上の表示・手の物・名札を出さない */
    static boolean portraitPass;

    /** 頭上の表示が出る距離ではバニラの名札を出さない（名前が二重にならないよう） */
    @Override
    public boolean shouldShowName(ValkyrieEntity animatable) {
        return !portraitPass && !ValkyrieOverhead.suppressNameTag(animatable, entityRenderDispatcher) && super.shouldShowName(animatable);
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
     * 手に持った兵装（右手 = 主、左手 = 副。段階 2、2026-10-05 ユーザー指定「手持ちの兵装だけ出す」）。
     * バニラのプレイヤーの持ち方（{@code ItemInHandLayer}: 腕の付け根から X -90°・Y 180°・(±1/16, 2/16, 手首まで) 動かして THIRD_PERSON で描く）を、
     * GeckoLib の座標（バニラのモデルと違い X・Y を反転していない）へ直してそのまま行う。兵装の表示の向き（各兵装の thirdperson_*）がプレイヤーと同じに効く。
     * 体が小さい（27px、プレイヤーは 32px）ので持つ物も同じ割合で小さくする
     */
    static class HeldItemLayer extends software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer<ValkyrieEntity> {
        /** 腕の付け根（y 20.1）から握る所（手首の少し上）までの長さ。腕は y 11.0 まで。プレイヤーは 12px の腕の 10px の所で持つ */
        private static final float GRIP = 7.6F / 16.0F;
        /** 実機で合わせた握る位置の補正（px、持つ物の座標。X は外向きが正、Y は前が正、Z は肩の向きが正） */
        private static final float GRIP_FIX_X = -1.0F, GRIP_FIX_Y = -0.8F, GRIP_FIX_Z = -2.9F;
        /** 体の大きさの比（兵姫 27px / プレイヤー 32px） */
        private static final float BODY_SCALE = 27.0F / 32.0F;

        HeldItemLayer(GeoRenderer<ValkyrieEntity> renderer) {
            super(renderer);
        }

        @Override
        protected net.minecraft.world.item.ItemStack getStackForBone(GeoBone bone, ValkyrieEntity animatable) {
            if (portraitPass) return null; // HUD の顔には手の物を描かない
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
            boolean left = bone.getName().equals("left_arm");
            poseStack.pushPose();
            // 腕の付け根の位置・向き（GeckoLib が済ませている）で、バニラのモデルの座標（X・Y 反転）へ切り替える
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((left ? -1.0F : 1.0F) / 16.0F * BODY_SCALE, 0.125F * BODY_SCALE, -GRIP);
            poseStack.scale(BODY_SCALE, BODY_SCALE, BODY_SCALE);
            // 実機で合わせた握る位置の補正（2026-10-05、ユーザーと /avhand で確認: 内へ 1・後ろへ 0.8・下へ 2.9 px。持つ物の座標）
            poseStack.translate(GRIP_FIX_X / 16.0F, GRIP_FIX_Y / 16.0F, GRIP_FIX_Z / 16.0F);
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
