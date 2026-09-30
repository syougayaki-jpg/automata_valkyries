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
                            ModItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());
}
