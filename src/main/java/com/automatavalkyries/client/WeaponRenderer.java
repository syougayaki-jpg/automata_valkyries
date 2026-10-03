package com.automatavalkyries.client;

import com.automatavalkyries.weapon.TacticalWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 兵装の GeckoLib レンダラー。モデル・アニメーション・テクスチャはアイテムの ID から決まる
 * （{@code geo/item/<id>.geo.json} など）。光る部分は {@code textures/item/<id>_glowmask.png}。
 */
public class WeaponRenderer extends GeoItemRenderer<TacticalWeaponItem> {

    private WeaponRenderer(TacticalWeaponItem item) {
        super(item instanceof com.automatavalkyries.weapon.RailgunBackdropItem ? new RailgunGearModel(ForgeRegistries.ITEMS.getKey(item)) // 歯車は溜めると加速
                : new DefaultedItemGeoModel<>(ForgeRegistries.ITEMS.getKey(item)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        String id = ForgeRegistries.ITEMS.getKey(item).getPath();
        BladeAuraLayer.Spec blade = BladeAuraLayer.specFor(id);
        if (blade != null) addRenderLayer(new BladeAuraLayer(this, blade, id)); // 刃物の刃先の光のレイヤー
        if (item instanceof com.automatavalkyries.weapon.GenesisHaloItem) addRenderLayer(new GenesisCoreLayer(this)); // 中心のルミナルクリスタルの光
    }

    @Override
    public void renderRecursively(PoseStack poseStack, TacticalWeaponItem animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (getGeoModel() instanceof RailgunGearModel) RailgunGearModel.apply(bone);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    public static WeaponRenderer create(TacticalWeaponItem item) {
        return new WeaponRenderer(item);
    }
}
