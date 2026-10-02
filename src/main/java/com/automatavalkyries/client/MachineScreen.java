package com.automatavalkyries.client;

import com.automatavalkyries.machine.MachineKind;
import com.automatavalkyries.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.text.NumberFormat;

/**
 * 基礎文明マシンの画面（すべての種類で共通）。テクスチャを使わず、バニラ風のパネルを描く:
 * 左に電力のゲージ、燃料発電機は炎、加工機は進み具合の矢印。
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, SLOT = 0xFF8B8B8B, BLACK = 0xFF000000;
    private static final int ENERGY_X = 10, ENERGY_Y = 17, ENERGY_W = 12, ENERGY_H = 52;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // パネル（立体の縁）
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth - 1, y + 1, LIGHT);
        g.fill(x, y, x + 1, y + imageHeight - 1, LIGHT);
        g.fill(x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, DARK);
        g.fill(x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, DARK);
        // スロットの枠
        for (Slot slot : menu.slots) slotFrame(g, x + slot.x - 1, y + slot.y - 1);
        // 電力のゲージ
        int ex = x + ENERGY_X, ey = y + ENERGY_Y;
        g.fill(ex - 1, ey - 1, ex + ENERGY_W + 1, ey + ENERGY_H + 1, DARK);
        g.fill(ex, ey, ex + ENERGY_W, ey + ENERGY_H, BLACK);
        int capacity = Math.max(1, menu.capacity());
        int filled = (int) ((long) ENERGY_H * menu.energy() / capacity);
        g.fillGradient(ex, ey + ENERGY_H - filled, ex + ENERGY_W, ey + ENERGY_H, 0xFFFFD166, 0xFFE63946);
        MachineKind kind = menu.kind();
        if (kind.hasFuel()) {
            // 炎（燃えている残り時間）
            int fx = x + MachineMenu.FUEL_X + 1, fy = y + MachineMenu.FUEL_Y - 16;
            int flame = menu.burnMax() > 0 ? 13 * menu.burn() / menu.burnMax() : 0;
            g.fill(fx + 2, fy, fx + 14, fy + 13, 0xFF6B6B6B);
            if (flame > 0) g.fillGradient(fx + 2, fy + 13 - flame, fx + 14, fy + 13, 0xFFFFE07A, 0xFFE63946);
        } else if (kind.processes()) {
            // 進み具合の矢印
            int ax = x + MachineMenu.arrowX(kind), ay = y + 35;
            g.fill(ax, ay + 5, ax + 22, ay + 11, DARK);
            int done = menu.maxProgress() > 0 ? 22 * menu.progress() / menu.maxProgress() : 0;
            if (done > 0) g.fill(ax, ay + 5, ax + done, ay + 11, 0xFF4CC9F0);
            for (int i = 0; i < 4; i++) g.fill(ax + 22 + i, ay + 1 + i, ax + 23 + i, ay + 15 - i, done >= 22 ? 0xFF4CC9F0 : DARK);
        } else {
            // 蓄電器: 残量の数字と、充電スロットの印（稲妻）
            g.drawCenteredString(font, NumberFormat.getIntegerInstance().format(menu.energy()) + " FE", x + 128, y + 39, 0xFFFFD166);
            int bx = x + MachineMenu.FUEL_X + 5, by = y + MachineMenu.FUEL_Y - 14;
            for (int i = 0; i < 6; i++) g.fill(bx + 5 - i, by + i, bx + 7 - i, by + i + 1, 0xFFFFD166);
            for (int i = 0; i < 6; i++) g.fill(bx + 1 + i, by + 6 + i, bx + 3 + i, by + 7 + i, 0xFFFFD166);
        }
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT);
        g.fill(x, y, x + 17, y + 1, DARK);
        g.fill(x, y, x + 1, y + 17, DARK);
        g.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int ex = leftPos + ENERGY_X, ey = topPos + ENERGY_Y;
        if (mouseX >= ex && mouseX < ex + ENERGY_W && mouseY >= ey && mouseY < ey + ENERGY_H) {
            NumberFormat format = NumberFormat.getIntegerInstance();
            g.renderTooltip(font, Component.translatable("gui.automata_valkyries.energy",
                    format.format(menu.energy()), format.format(menu.capacity())), mouseX, mouseY);
        }
    }
}
