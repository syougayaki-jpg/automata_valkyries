package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModRecipes;
import com.automatavalkyries.registry.ModTrials;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** 兵装のオーバーロード化（作業台、形は自由）: 兵装 1 つ＋オーバーロードメモリ 1 つ → オーバーロードの兵装（{@link Overload}） */
public class OverloadRecipe extends CustomRecipe {

    public OverloadRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    /** 並べた物から出来上がる物（合わなければ空） */
    public static ItemStack combine(List<ItemStack> stacks) {
        ItemStack weapon = ItemStack.EMPTY;
        int memories = 0;
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            if (s.is(ModTrials.OVERLOAD_MEMORY.get())) memories++;
            else if (weapon.isEmpty() && Overload.canApply(s)) weapon = s;
            else return ItemStack.EMPTY;
        }
        return memories == 1 && !weapon.isEmpty() ? Overload.apply(weapon) : ItemStack.EMPTY;
    }

    private static List<ItemStack> items(CraftingContainer container) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) list.add(container.getItem(i));
        return list;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return !combine(items(container)).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        return combine(items(container));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.OVERLOAD_SERIALIZER.get();
    }
}
