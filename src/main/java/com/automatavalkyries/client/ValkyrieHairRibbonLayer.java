package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * エレノアの髪留め（黒とピンクのリボン。{@link ValkyrieEntity#hasHairRibbon()}）を兵姫の後ろ頭に描く（2026-10-22）。
 * 形はエレノアの頭のリボンと同じ（{@code geo/entity/unit/hair_ribbon.geo.json}、原点が結び目の中心）を縮めて置く。
 * 左右の輪はゆっくり羽ばたき、垂れ 2 本は揺れながら後ろ髪の外へ逃げる
 */
public class ValkyrieHairRibbonLayer extends GeoRenderLayer<ValkyrieEntity> {
    private static final ResourceLocation MODEL = rl("geo/entity/unit/hair_ribbon.geo.json");
    private static final ResourceLocation TEXTURE = rl("textures/entity/unit/valkyrie_hair_ribbon.png");
    /** 結び目を置く所（兵姫の geo の座標、px）: 後ろ頭の上寄り、後ろ髪の少し外 */
    private static final float X = 0.0F, Y = 25.4F, Z = 4.0F;
    /** エレノアの頭に付けている大きさとの比（兵姫の頭は 5px、エレノアは 8px） */
    private static final float SCALE = 0.4F;

    public ValkyrieHairRibbonLayer(GeoRenderer<ValkyrieEntity> renderer) {
        super(renderer);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, path);
    }

    @Override
    public void renderForBone(PoseStack poseStack, ValkyrieEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!"head".equals(bone.getName()) || bone.isHidden() || animatable.isInvisible() || !animatable.hasHairRibbon()) return;
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(MODEL); // 読み直し（F3+T）後も引き直す
        if (model == null) return;
        GeoBone bow = model.getBone("bow").orElse(null);
        if (bow == null) return;
        float age = animatable.tickCount + partialTick;
        float walk = Math.min(animatable.walkAnimation.speed(partialTick), 1.0F);
        float flap = Mth.sin(age * 0.12F) * 0.08F;
        float sway = Mth.sin(age * 0.1F) * 0.1F + Mth.sin(age * 0.5F) * 0.12F * walk;
        float back = -(0.25F + walk * 0.3F);
        for (GeoBone child : bow.getChildBones()) {
            switch (child.getName()) {
                case "lobe_right" -> child.setRotY(flap);
                case "lobe_left" -> child.setRotY(-flap);
                case "tail_right" -> { child.setRotZ(0.18F + sway); child.setRotX(back); }
                case "tail_left" -> { child.setRotZ(-0.18F + sway * 0.8F); child.setRotX(back + Mth.sin(age * 0.13F + 1.0F) * 0.05F); }
                default -> { }
            }
        }
        VertexConsumer ribbon = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.pushPose();
        poseStack.translate(X / 16.0F, Y / 16.0F, Z / 16.0F);
        poseStack.scale(SCALE, SCALE, SCALE);
        renderBone(poseStack, bow, ribbon, packedLight, packedOverlay);
        poseStack.popPose();
        bufferSource.getBuffer(renderType); // 兵姫の描き方のバッファへ戻す（GeckoLib は骨を描く途中でこの層を呼ぶ）
    }

    private void renderBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int light, int overlay) {
        getRenderer().renderCubesOfBone(poseStack, bone, buffer, light, overlay, 1F, 1F, 1F, 1F);
        for (GeoBone child : bone.getChildBones()) {
            poseStack.pushPose();
            RenderUtils.prepMatrixForBone(poseStack, child);
            renderBone(poseStack, child, buffer, light, overlay);
            poseStack.popPose();
        }
    }
}
