package com.automatavalkyries.compat.jei;

import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.chrono.LuminalReactorBlockEntity;
import com.automatavalkyries.item.ChronoTiers;
import com.automatavalkyries.registry.ModChrono;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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

import java.text.NumberFormat;

/** ルミナル反応炉の燃料（1 個の電力と、燃やせるティア）。創世ルミナルコアは T4 のコア枠で毎秒 1,000,000 FE */
public class ReactorFuelCategory implements IRecipeCategory<ReactorFuelCategory.Fuel> {

    /** 燃料（1 個の電力・燃やせる最低のティア。永久の物は perpetual） */
    public record Fuel(ItemStack stack, long energy, ChronoTier minTier, boolean perpetual) {}

    public static final RecipeType<Fuel> TYPE = RecipeType.create(com.automatavalkyries.AutomataValkyries.MODID, "reactor_fuel", Fuel.class);

    private final IDrawable icon, slot;

    public ReactorFuelCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(ModChrono.LUMINAL_REACTOR.get()));
        this.slot = gui.getSlotDrawable();
    }

    @Override public RecipeType<Fuel> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.automata_valkyries.reactor_fuel"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 150; }
    @Override public int getHeight() { return 30; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Fuel fuel, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 7).addItemStack(fuel.stack());
    }

    @Override
    public void draw(Fuel fuel, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        slot.draw(g, 0, 6);
        var font = Minecraft.getInstance().font;
        NumberFormat f = NumberFormat.getIntegerInstance();
        Component amount = fuel.perpetual()
                ? Component.translatable("jei.automata_valkyries.reactor_perpetual", f.format(LuminalReactorBlockEntity.GENESIS_PER_TICK * 20L))
                : Component.translatable("jei.automata_valkyries.reactor_energy", f.format(fuel.energy()));
        g.drawString(font, amount, 24, 6, 0xFF404040, false);
        g.drawString(font, Component.translatable("jei.automata_valkyries.reactor_tier", Component.translatable("tier.automata_valkyries." + fuel.minTier().id)),
                24, 17, 0xFF000000 | (ChronoTiers.rgb(fuel.minTier()) >> 1 & 0x7F7F7F), false);
    }
}
