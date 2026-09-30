package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.DebugKillStickItem;
import com.automatavalkyries.item.SuperKillStickItem;
import com.automatavalkyries.item.UltraKillStickItem;
import com.automatavalkyries.item.VerifyStickItem;
import com.automatavalkyries.item.WardCharmItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AutomataValkyries.MODID);

    // ---- 下位鉱石・下位金属（SPEC §7.7） ----
    public static final RegistryObject<Item> RAW_ASTRITE = simple("raw_astrite");
    public static final RegistryObject<Item> INGOT_ASTRITE = simple("ingot_astrite");
    public static final RegistryObject<Item> RAW_PHONONIUM = simple("raw_phononium");
    public static final RegistryObject<Item> INGOT_PHONONIUM = simple("ingot_phononium");
    public static final RegistryObject<Item> RAW_CERULEAS = simple("raw_ceruleas");
    public static final RegistryObject<Item> INGOT_CERULEAS = simple("ingot_ceruleas");

    // ---- スポーンエッグ ----
    public static final RegistryObject<Item> ELENOR_SPAWN_EGG = ITEMS.register("elenor_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ELENOR, 0x512da8, 0xe040fb, new Item.Properties()));

    // ---- テスト用 ----
    public static final RegistryObject<Item> DEBUG_KILL_STICK = ITEMS.register("debug_kill_stick", () ->
            new DebugKillStickItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SUPER_KILL_STICK = ITEMS.register("super_kill_stick", () ->
            new SuperKillStickItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ULTRA_KILL_STICK = ITEMS.register("ultra_kill_stick", () ->
            new UltraKillStickItem(UltraKillStickItem.defaultProperties()));
    public static final RegistryObject<Item> VERIFY_STICK = ITEMS.register("verify_stick", () ->
            new VerifyStickItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // ---- プレイヤー用 ----
    public static final RegistryObject<Item> WARD_CHARM = ITEMS.register("ward_charm", () ->
            new WardCharmItem(WardCharmItem.defaultProperties()));

    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }
}
