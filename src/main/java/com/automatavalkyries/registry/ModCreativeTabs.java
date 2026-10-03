package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AutomataValkyries.MODID);

    /** MOD の全アイテム一覧（ModItems に登録した順に並ぶ） */
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.automata_valkyries.main"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.INGOT_ASTRITE.get().getDefaultInstance())
                    .displayItems((parameters, output) ->
                            ModItems.ITEMS.getEntries().forEach(entry -> {
                                net.minecraft.world.item.Item item = entry.get();
                                // 兵装・ルミナルコアは満充電（弾・溶液も満タン）で並べる
                                if (item instanceof com.automatavalkyries.weapon.TacticalWeaponItem weapon) output.accept(weapon.charged());
                                else if (item instanceof com.automatavalkyries.weapon.LuminalCoreItem core) output.accept(core.charged());
                                else output.accept(item);
                                // クロノリンク・マシンは T2〜T4 も並べる（ティアはアイテムの BlockStateTag。並べないと T2〜T4 を見つけられない）
                                if (item instanceof com.automatavalkyries.chrono.ChronoMachineItem) {
                                    for (com.automatavalkyries.chrono.ChronoTier tier : com.automatavalkyries.chrono.ChronoTier.values()) {
                                        if (tier.level < 2) continue;
                                        output.accept(com.automatavalkyries.chrono.ChronoMachineItem.withTier(new net.minecraft.world.item.ItemStack(item), tier));
                                    }
                                }
                            }))
                    .build());
}
