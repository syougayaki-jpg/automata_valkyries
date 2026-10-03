package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.trial.OverloadMemoryItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

/**
 * 回想のトライアル（裏ボス撃破後のおまけ。2026-10-04 ユーザー指定）: 回想の空間（ディメンション）とオーバーロードメモリ。
 * 仕組みは {@code trial/TrialManager}、トライアルの一覧は {@code trial/Trial}。
 */
public final class ModTrials {

    /** 回想の空間（{@code data/automata_valkyries/dimension/recollection.json}） */
    public static final ResourceKey<Level> RECOLLECTION = ResourceKey.create(Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "recollection"));

    /** オーバーロードメモリ: 回想のトライアルを全部クリアすると手に入る。兵装と作業台で合わせるとオーバーロード化（{@code weapon/OverloadRecipe}） */
    public static final RegistryObject<Item> OVERLOAD_MEMORY = ModItems.ITEMS.register("overload_memory",
            () -> new OverloadMemoryItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    private ModTrials() {}

    public static void init() {}
}
