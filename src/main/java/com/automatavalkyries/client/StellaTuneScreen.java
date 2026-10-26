package com.automatavalkyries.client;

import com.automatavalkyries.item.armor.StellaArmorItem;
import com.automatavalkyries.menu.StellaTuneMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** ステラ・クロノダイムの防具の機能を選ぶ画面: 機能ごとのボタン（押すと 切 → I → II → 切 と回る）と、その説明 */
public class StellaTuneScreen extends AbstractContainerScreen<StellaTuneMenu> {

    private final List<Button> buttons = new ArrayList<>();

    public StellaTuneScreen(StellaTuneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 220;
        this.imageHeight = 40 + menu.tunes.size() * 34;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        for (int i = 0; i < menu.tunes.size(); i++) {
            int id = i;
            buttons.add(addRenderableWidget(Button.builder(Component.empty(), b -> {
                if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            }).bounds(leftPos + 10, topPos + 24 + i * 34, imageWidth - 20, 18).build()));
        }
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < buttons.size(); i++) {
            StellaArmorItem.Tune t = menu.tunes.get(i);
            int lv = menu.level(i);
            Component value = lv == 0 ? Component.literal("OFF") : t.max == 1 ? Component.literal("ON") : Component.translatable("enchantment.level." + lv);
            buttons.get(i).setMessage(Component.translatable("gui.automata_valkyries.stella_tune." + t.key()).append(": ").append(value));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < menu.tunes.size(); i++) {
            g.drawString(font, Component.translatable("gui.automata_valkyries.stella_tune." + menu.tunes.get(i).key() + ".desc"), leftPos + 12, topPos + 44 + i * 34, 0xA0A8C0, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101028);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF7A6ED0);
        g.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF7A6ED0);
        g.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF7A6ED0);
        g.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF7A6ED0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 10, 8, 0xE8E0FF, false);
    }
}
