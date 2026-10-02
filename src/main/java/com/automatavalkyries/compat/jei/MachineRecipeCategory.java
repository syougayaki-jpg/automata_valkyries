package com.automatavalkyries.compat.jei;

import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.item.ChronoTiers;
import com.automatavalkyries.machine.MachineRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * このMODのマシンのレシピ（粉砕・合金・プレス・組み立て・兵装・原子相転移）の JEI の表示。
 * 材料は 3 × 2 の格子（個数つき）→ 矢印 → 結果、下に電力と時間。相転移は必要なティアと段の数も出す。
 */
public class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {

    static final int WIDTH = 150, HEIGHT = 58;

    private final RecipeType<MachineRecipe> type;
    private final Component title;
    private final IDrawable icon, slot, outputSlot;
    private final IDrawableAnimated arrow;
    private final boolean showTier;

    public MachineRecipeCategory(IGuiHelper gui, RecipeType<MachineRecipe> type, String key, ItemLike iconItem, boolean showTier) {
        this.type = type;
        this.title = Component.translatable("jei.automata_valkyries." + key);
        this.icon = gui.createDrawableItemStack(new ItemStack(iconItem));
        this.slot = gui.getSlotDrawable();
        this.outputSlot = gui.getOutputSlot();
        this.arrow = gui.createAnimatedRecipeArrow(60);
        this.showTier = showTier;
    }

    @Override public RecipeType<MachineRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        List<MachineRecipe.CountedIngredient> inputs = recipe.inputs();
        for (int i = 0; i < inputs.size() && i < 6; i++) {
            MachineRecipe.CountedIngredient input = inputs.get(i);
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack option : input.ingredient().getItems()) stacks.add(option.copyWithCount(input.count()));
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 3) * 18, 1 + (i / 3) * 18).addItemStacks(stacks);
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 101, 10).addItemStack(recipe.getResultItem(Minecraft.getInstance().level.registryAccess()));
    }

    @Override
    public void draw(MachineRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        for (int i = 0; i < Math.min(6, recipe.inputs().size()); i++) slot.draw(g, (i % 3) * 18, (i / 3) * 18);
        outputSlot.draw(g, 96, 5);
        arrow.draw(g, 64, 9);
        var font = Minecraft.getInstance().font;
        NumberFormat f = NumberFormat.getIntegerInstance();
        g.drawString(font, Component.translatable("jei.automata_valkyries.energy_time", f.format(recipe.energy()), f.format(recipe.time())),
                0, 40, 0xFF404040, false);
        if (showTier && recipe.tier() > 0) {
            ChronoTier tier = ChronoTier.of(recipe.tier());
            Component line = Component.translatable("tier.automata_valkyries." + tier.id);
            if (recipe.stages() > 1) line = Component.translatable("jei.automata_valkyries.tier_stages", line, recipe.stages());
            g.drawString(font, line, 0, 50, 0xFF000000 | darker(ChronoTiers.rgb(tier)), false);
        }
    }

    /** 明るい背景でも読めるよう、ティアの色を暗くする */
    private static int darker(int rgb) {
        int r = (rgb >> 16 & 255) * 3 / 5, g = (rgb >> 8 & 255) * 3 / 5, b = (rgb & 255) * 3 / 5;
        return (r << 16) | (g << 8) | b;
    }
}
