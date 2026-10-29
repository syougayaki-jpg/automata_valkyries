package com.automatavalkyries.client;

import com.automatavalkyries.item.BlueprintCardItem;
import com.automatavalkyries.menu.BlueprintCardMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 設計図カードの画面: 書き込んであるレシピ（作る物・1 回分の材料）と、消すボタン。JEI のレシピの「+」で書き込める（その案内は JEI がある時だけ出す） */
public class BlueprintCardScreen extends AbstractContainerScreen<BlueprintCardMenu> {

    private static final ResourceLocation CHEST = ResourceLocation.parse("minecraft:textures/gui/container/generic_54.png");

    public BlueprintCardScreen(BlueprintCardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BlueprintCardMenu.WIDTH;
        this.imageHeight = BlueprintCardMenu.HEIGHT;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.blueprint.clear"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BlueprintCardMenu.BUTTON_CLEAR);
        }).bounds(leftPos + imageWidth + 4, topPos + 4, 50, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(CHEST, leftPos, topPos, 0, 0, imageWidth, 71);                                    // 上の 3 行ぶん（枠は塗りつぶす）
        g.blit(CHEST, leftPos, topPos + 71, 0, 126, imageWidth, 96);
        g.fill(leftPos + 7, topPos + 17, leftPos + 169, topPos + 71, 0xFFC6C6C6);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;
        ItemStack card = menu.card(minecraft.player);
        ResourceLocation id = BlueprintCardItem.recipeId(card);
        var recipe = id == null ? null : minecraft.level.getRecipeManager().byKey(id).orElse(null);
        if (recipe == null) {
            g.drawString(font, Component.translatable("item.automata_valkyries.blueprint_card.empty"), 8, 22, 0x404040, false);
        } else {
            g.renderItem(recipe.getResultItem(minecraft.level.registryAccess()), 8, 18);
            g.drawString(font, recipe.getResultItem(minecraft.level.registryAccess()).getHoverName(), 28, 22, 0x203060, false);
            List<FormattedCharSequence> lines = font.split(Component.translatable("item.automata_valkyries.blueprint_card.materials",
                    BlueprintCardItem.materials(recipe, minecraft.level)), 160);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), 8, 36 + i * 10, 0x404040, false);
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {                            // JEI が無ければ「+」で書き込む案内は出さない
            g.drawString(font, Component.translatable("gui.automata_valkyries.blueprint.jei_hint").withStyle(ChatFormatting.DARK_GRAY), 8, 60, 0x606060, false);
        }
    }
}
