package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.GenesisHaloItem;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 手に持つジェネシス・ヘイロー（知恵の輪）の中心のルミナルクリスタルを光らせる（2026-10-04 ユーザー指定）。
 * 自作の光（白い芯・水色から金の暈・6 本の光芒）を、直角に交わる 3 枚の板に加算で描き（どこから見ても光の玉に見える）、脈打たせてゆっくり回す。
 * 自分がチャージしている間は、溜まるほど大きく明るくなる。
 */
public class GenesisCoreLayer extends GeoRenderLayer<TacticalWeaponItem> {

    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/genesis_crystal_glow.png");

    public GenesisCoreLayer(GeoRenderer<TacticalWeaponItem> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, TacticalWeaponItem animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level == null ? 0 : mc.level.getGameTime() + partialTick;
        float charge = 0F;
        if (mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof GenesisHaloItem halo) charge = halo.chargeProgress(mc.player);
        float pulse = 1F + 0.12F * Mth.sin(time * 0.2F) + 0.9F * charge;
        float bright = Math.min(1F, 0.75F + 0.25F * charge);
        float half = 0.2F * pulse;
        // 直角に交わる 3 枚（xy・yz・xz）
        float[][] planes = {{0, 0}, {0, 90}, {90, 0}};
        for (float[] plane : planes) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(plane[0]));
            poseStack.mulPose(Axis.YP.rotationDegrees(plane[1]));
            RequiemBackdropClient.quad(poseStack, bufferSource, GLOW, half, time * 1.5F, 0F, bright);
            RequiemBackdropClient.quad(poseStack, bufferSource, GLOW, half * 2.2F, -time * 0.6F, 0F, 0.3F * bright);
            poseStack.popPose();
        }
    }
}
