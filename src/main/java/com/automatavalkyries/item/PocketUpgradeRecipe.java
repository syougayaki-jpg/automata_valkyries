package com.automatavalkyries.item;

import com.automatavalkyries.registry.ModRecipes;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * アイテムポケットの強化（作業台の形のあるレシピ。書き方は minecraft:crafting_shaped と同じ）: 材料のポケットの中身と自動使用の切り替えを、出来たポケットへそのまま引き継ぐ
 */
public class PocketUpgradeRecipe extends ShapedRecipe {

    public PocketUpgradeRecipe(ShapedRecipe base) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(), base.getResultItem(null), base.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        ItemStack out = super.assemble(inv, access);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ItemPocketItem && s.hasTag()) {
                out.setTag(s.getTag().copy());
                break;
            }
        }
        return out;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.POCKET_UPGRADE.get();
    }

    public static class Serializer implements RecipeSerializer<PocketUpgradeRecipe> {
        @Override
        public PocketUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new PocketUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public PocketUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new PocketUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, PocketUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}
