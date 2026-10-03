package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.chrono.ChronoMachineItem;
import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.item.ChronoTiers;
import com.automatavalkyries.item.TierFrameItem;
import com.automatavalkyries.machine.MachineRecipe;
import com.automatavalkyries.registry.ModRecipes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 素材の名前にティアを付ける（クロノリンク・マシンの名前の [ティア] と同じ見せ方。2026-10-03）。
 * <p>
 * ティアは相転移（兵装・ルミナルコアはタクティカル・アーモリー）のレシピから決める: その素材を作るレシピの「必要ティア」のうち一番低いもの（量子 TAC 集積回路は T2 のアダプターで作るので
 * [フューチャー]）。ティアフレームはフレーム自身のティア。マシン（{@link ChronoMachineItem}）は自分で名前にティアを付けるので除く。
 * レシピが変われば（データパック）付くティアも変わる。
 * </p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class MaterialTierLabels {

    private static volatile Map<Item, ChronoTier> tiers = Map.of();

    private MaterialTierLabels() {}

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        Map<Item, ChronoTier> map = new HashMap<>();
        List<MachineRecipe> recipes = new java.util.ArrayList<>(event.getRecipeManager().getAllRecipesFor(ModRecipes.PHASE_TRANSITION.get()));
        recipes.addAll(event.getRecipeManager().getAllRecipesFor(ModRecipes.ARMORY_ASSEMBLING.get())); // 兵装・ルミナルコアはアーモリーのティア
        for (MachineRecipe recipe : recipes) {
            Item item = recipe.result().getItem();
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null || !AutomataValkyries.MODID.equals(key.getNamespace()) || item instanceof ChronoMachineItem) continue;
            ChronoTier tier = ChronoTier.of(recipe.tier());
            map.merge(item, tier, (a, b) -> a.level <= b.level ? a : b);
        }
        tiers = Map.copyOf(map);
    }

    /** その素材のティア（無ければ null） */
    public static ChronoTier tierOf(Item item) {
        if (item instanceof TierFrameItem frame) return frame.tier();
        return tiers.get(item);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        if (tooltip.isEmpty()) return;
        ChronoTier tier = tierOf(event.getItemStack().getItem());
        if (tier == null) return;
        tooltip.set(0, tooltip.get(0).copy().append(" [")
                .append(Component.translatable("tier.automata_valkyries." + tier.id).withStyle(ChronoTiers.color(tier)))
                .append("]"));
    }
}
