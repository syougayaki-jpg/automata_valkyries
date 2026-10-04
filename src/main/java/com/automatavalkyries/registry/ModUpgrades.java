package com.automatavalkyries.registry;

import com.automatavalkyries.upgrade.UpgradeStationBlock;
import com.automatavalkyries.upgrade.UpgradeStationMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

/** 兵装のカスタムアップグレード（2026-10-04 ユーザー指定）: 兵装強化台と、その画面。強化の中身は {@code weapon/WeaponUpgrades} */
public final class ModUpgrades {

    public static final RegistryObject<Block> STATION = ModBlocks.BLOCKS.register("upgrade_station", () -> new UpgradeStationBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(state -> 7)));
    public static final RegistryObject<Item> STATION_ITEM = ModItems.ITEMS.register("upgrade_station", () -> new BlockItem(STATION.get(), new Item.Properties()));
    /** カスタム・マキシマイザー（クリエイティブ用）: もう片方の手の兵装のカスタムを全部上限に */
    public static final RegistryObject<Item> CUSTOM_MAXIMIZER = ModItems.ITEMS.register("custom_maximizer",
            () -> new com.automatavalkyries.upgrade.CustomMaximizerItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<MenuType<UpgradeStationMenu>> MENU = ModMachines.MENUS.register("upgrade_station",
            () -> IForgeMenuType.create(UpgradeStationMenu::new));

    private ModUpgrades() {}

    public static void init() {}
}
