package com.automatavalkyries.registry;

import com.automatavalkyries.item.armor.MetalArmorItem;
import com.automatavalkyries.item.armor.MetalArmorMaterial;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** プレイヤー用の固有金属の防具（アストライト・フォノニウム・セルリアス、各 4 部位。2026-10-09） */
public final class ModArmor {

    public static final List<RegistryObject<Item>> ALL = new ArrayList<>();

    static {
        for (MetalArmorMaterial metal : MetalArmorMaterial.values()) {
            for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS}) {
                ALL.add(ModItems.ITEMS.register(metal.id + "_" + type.getName(), () -> new MetalArmorItem(metal, type, new Item.Properties())));
            }
        }
    }

    private ModArmor() {}

    /** クラスを読み込んで登録させる（メインクラスから呼ぶ） */
    public static void init() {
    }
}
