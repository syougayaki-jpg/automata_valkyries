package com.automatavalkyries.client;

import com.automatavalkyries.weapon.TacticalWeaponItem;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 兵装の GeckoLib レンダラー。モデル・アニメーション・テクスチャはアイテムの ID から決まる
 * （{@code geo/item/<id>.geo.json} など）。光る部分は {@code textures/item/<id>_glowmask.png}。
 */
public class WeaponRenderer extends GeoItemRenderer<TacticalWeaponItem> {

    private WeaponRenderer(TacticalWeaponItem item) {
        super(new DefaultedItemGeoModel<>(ForgeRegistries.ITEMS.getKey(item)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    public static WeaponRenderer create(TacticalWeaponItem item) {
        return new WeaponRenderer(item);
    }
}
