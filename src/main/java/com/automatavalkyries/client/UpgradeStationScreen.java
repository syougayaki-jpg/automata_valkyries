package com.automatavalkyries.client;

import com.automatavalkyries.upgrade.UpgradeStationMenu;
import com.automatavalkyries.weapon.WeaponTiers;
import com.automatavalkyries.weapon.WeaponUpgrades;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 兵装強化台の画面（エンチャントテーブルのように、右に 4 つの能力の行が並ぶ。自作の描画で絵は使わない）。
 * 行ごとに: 能力の名前・今の回数と上げた分・次の 1 回の材料（オーブ・スター・コア 1 つ＋金属の数と持っている数）。押せない行は暗く、理由を出す。
 */
public class UpgradeStationScreen extends AbstractContainerScreen<UpgradeStationMenu> {

    private static final int ROW_X = 46, ROW_Y = 8, ROW_W = 186, ROW_H = 26, ROW_GAP = 2;
    private static final int BG = 0xF0141A26, PANEL = 0xFF1C2433, EDGE = 0xFF3A6A8A, GLOW = 0xFF60D8FF, SLOT = 0xFF0B0F17, TEXT = 0xFFE0ECF8, DIM = 0xFF7A8698;

    public UpgradeStationScreen(UpgradeStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = UpgradeStationMenu.WIDTH;
        imageHeight = UpgradeStationMenu.HEIGHT;
        inventoryLabelX = UpgradeStationMenu.INV_X;
        inventoryLabelY = UpgradeStationMenu.INV_Y - 11;
        titleLabelY = -10;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        frame(g, x, y, imageWidth, imageHeight, EDGE);
        // 左の枠（兵装・オーブ等）
        g.fill(x + 6, y + 6, x + 42, y + 120, PANEL);
        frame(g, x + 6, y + 6, 36, 114, EDGE);
        slot(g, x + UpgradeStationMenu.WEAPON_X, y + UpgradeStationMenu.WEAPON_Y);
        slot(g, x + UpgradeStationMenu.CATALYST_X, y + UpgradeStationMenu.CATALYST_Y);
        g.drawCenteredString(font, Component.translatable("upgrade.automata_valkyries.weapon_slot"), x + 24, y + 18, DIM);
        g.drawCenteredString(font, Component.translatable("upgrade.automata_valkyries.catalyst_slot"), x + 24, y + 62, DIM);
        // 持ち物の枠
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + UpgradeStationMenu.INV_X + col * 18, y + UpgradeStationMenu.INV_Y + row * 18 + (row == 3 ? 4 : 0));
            }
        }
        // 能力の行
        ItemStack weapon = menu.weapon();
        boolean has = WeaponUpgrades.upgradeable(weapon);
        int tier = has ? WeaponTiers.of(weapon.getItem()) : 1;
        // T1・T2 は使える金属が複数なので、1 秒ごとに替えて出す
        java.util.List<Item> metals = WeaponUpgrades.metalChoices(tier);
        Item metal = metals.get((int) (net.minecraft.Util.getMillis() / 1000 % metals.size()));
        int owned = WeaponUpgrades.metalOwned(menu.player().getInventory(), tier);
        for (WeaponUpgrades.Stat stat : WeaponUpgrades.Stat.values()) {
            int rx = x + ROW_X, ry = y + ROW_Y + stat.ordinal() * (ROW_H + ROW_GAP);
            boolean ok = has && WeaponUpgrades.problem(menu.player(), weapon, menu.catalyst(), stat) == null;
            boolean hover = mouseX >= rx && mouseX < rx + ROW_W && mouseY >= ry && mouseY < ry + ROW_H;
            g.fill(rx, ry, rx + ROW_W, ry + ROW_H, ok ? (hover ? 0xFF24405A : PANEL) : 0xFF151A24);
            frame(g, rx, ry, ROW_W, ROW_H, ok ? (hover ? GLOW : EDGE) : 0xFF2A3240);
            g.fill(rx + 2, ry + 2, rx + 5, ry + ROW_H - 2, ok ? (0xFF000000 | stat.color) : 0xFF3A4250);
            int level = has ? WeaponUpgrades.level(weapon, stat) : 0;
            g.drawString(font, stat.title(), rx + 9, ry + 4, ok || !has ? (0xFF000000 | stat.color) : DIM, false);
            g.drawString(font, Component.translatable("upgrade.automata_valkyries.level", level, WeaponUpgrades.MAX_LEVEL, level * 10), rx + 92, ry + 4, has ? TEXT : DIM, false);
            if (!has) continue;
            if (level >= WeaponUpgrades.MAX_LEVEL) {
                g.drawString(font, Component.translatable("upgrade.automata_valkyries.max"), rx + 9, ry + 15, GLOW, false);
                continue;
            }
            int cost = WeaponUpgrades.metalCost(level);
            g.renderItem(new ItemStack(metal), rx + 8, ry + 9);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, Component.translatable("upgrade.automata_valkyries.cost", cost, owned,
                    Component.translatable("upgrade.automata_valkyries.catalyst." + (tier >= 4 ? "core" : tier == 3 ? "star" : "orb"))),
                    rx + 26, ry + 15, owned >= cost || menu.player().getAbilities().instabuild ? TEXT : 0xFFFF6A6A, false);
            g.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // 押せない行の理由
        ItemStack weapon = menu.weapon();
        for (WeaponUpgrades.Stat stat : WeaponUpgrades.Stat.values()) {
            int rx = leftPos + ROW_X, ry = topPos + ROW_Y + stat.ordinal() * (ROW_H + ROW_GAP);
            if (mouseX >= rx && mouseX < rx + ROW_W && mouseY >= ry && mouseY < ry + ROW_H) {
                Component problem = WeaponUpgrades.problem(menu.player(), weapon, menu.catalyst(), stat);
                Component desc = Component.translatable("upgrade.automata_valkyries." + stat.id + ".desc");
                java.util.List<Component> lines = new java.util.ArrayList<>();
                lines.add(desc);
                if (WeaponUpgrades.upgradeable(weapon)) {   // 使える金属（どれでも・混ぜてよい）
                    java.util.List<Item> metals = WeaponUpgrades.metalChoices(WeaponTiers.of(weapon.getItem()));
                    net.minecraft.network.chat.MutableComponent names = Component.empty();
                    for (int i = 0; i < metals.size(); i++) {
                        if (i > 0) names.append(" / ");
                        names.append(new ItemStack(metals.get(i)).getHoverName());
                    }
                    lines.add(Component.translatable("upgrade.automata_valkyries.metals", names).withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                if (problem != null) lines.add(problem.copy().withStyle(s -> s.withColor(0xFF6A6A)));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (WeaponUpgrades.Stat stat : WeaponUpgrades.Stat.values()) {
            int rx = leftPos + ROW_X, ry = topPos + ROW_Y + stat.ordinal() * (ROW_H + ROW_GAP);
            if (mouseX >= rx && mouseX < rx + ROW_W && mouseY >= ry && mouseY < ry + ROW_H
                    && WeaponUpgrades.problem(menu.player(), menu.weapon(), menu.catalyst(), stat) == null && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, stat.ordinal());
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, titleLabelY, GLOW, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF3A4A60);
        g.fill(x, y, x + 16, y + 16, SLOT);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
