package com.automatavalkyries.client;

import com.automatavalkyries.item.ItemPocketItem;
import com.automatavalkyries.menu.ItemPocketMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * アイテムポケットの画面: 大きなチェストと同じ見た目の枠（容量に合わせた行数）と、右側にページ送り・自動使用の全体と分類ごとの切り替え。
 * ティアで増える切り替え（T3 の自動回収・T4 の補充）はそのティアから出す。T2 から整理のボタン
 */
public class ItemPocketScreen extends AbstractContainerScreen<ItemPocketMenu> {

    private static final ResourceLocation CHEST = ResourceLocation.parse("minecraft:textures/gui/container/generic_54.png");
    private final List<Button> categoryButtons = new ArrayList<>();
    private Button autoButton;

    public ItemPocketScreen(ItemPocketMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 114 + menu.rows * 18;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + imageWidth + 4, y = topPos + 4;
        if (menu.pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> press(ItemPocketMenu.BUTTON_PREV)).bounds(x, y, 20, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> press(ItemPocketMenu.BUTTON_NEXT)).bounds(x + 56, y, 20, 16).build());
            y += 22;
        }
        autoButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(ItemPocketMenu.BUTTON_AUTO)).bounds(x, y, 76, 16).build());
        y += 20;
        categoryButtons.clear();
        for (ItemPocketItem.Category c : ItemPocketItem.Category.values()) {
            Button b = Button.builder(Component.empty(), btn -> press(ItemPocketMenu.BUTTON_CATEGORY + c.ordinal())).bounds(x, y, 76, 16).build();
            categoryButtons.add(b);
            if (menu.tier < c.minTier) continue;                                                 // まだ使えない機能のボタンは出さない
            addRenderableWidget(b);
            y += 18;
        }
        if (menu.tier >= ItemPocketItem.SORT_TIER) {
            addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.item_pocket.sort"), b -> press(ItemPocketMenu.BUTTON_SORT))
                    .bounds(x, y + 4, 76, 16).build());
            y += 20;
        }
        if (menu.tier >= 4) {                                                                    // どこでもリポジトリ（リンクしていなければ押せない）
            Button repo = addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.item_pocket.repository"), b -> press(ItemPocketMenu.BUTTON_REPOSITORY))
                    .bounds(x, y + 4, 76, 16).build());
            repo.active = minecraft != null && minecraft.player != null && ItemPocketItem.repository(minecraft.player.getItemInHand(menu.hand())) != null;
        }
        refresh();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void refresh() {
        int f = menu.flags();
        boolean auto = (f & 1) == 0;
        autoButton.setMessage(Component.translatable("gui.automata_valkyries.item_pocket.auto").append(auto ? " ON" : " OFF"));
        for (ItemPocketItem.Category c : ItemPocketItem.Category.values()) {
            boolean on = auto && (f & (2 << c.ordinal())) == 0;
            categoryButtons.get(c.ordinal()).setMessage(Component.translatable("gui.automata_valkyries.item_pocket." + c.name().toLowerCase(Locale.ROOT))
                    .append(on ? " ON" : " OFF"));
            categoryButtons.get(c.ordinal()).active = auto;
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
        if (menu.pages > 1) {
            Component label = Component.translatable("gui.automata_valkyries.item_pocket.page", menu.page() + 1, menu.pages);
            g.drawCenteredString(font, label, leftPos + imageWidth + 4 + 38, topPos + 8, 0xFFFFFF);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int top = menu.rows * 18 + 17;
        g.blit(CHEST, leftPos, topPos, 0, 0, imageWidth, top);
        g.blit(CHEST, leftPos, topPos + top, 0, 126, imageWidth, 96);
        int start = menu.page() * ItemPocketItem.PAGE;                                     // 最後のページで容量を超える枠は暗く塗る
        for (int i = 0; i < menu.rows * 9; i++) {
            if (start + i < menu.capacity) continue;
            int sx = leftPos + 7 + (i % 9) * 18, sy = topPos + 17 + (i / 9) * 18;
            g.fill(sx, sy, sx + 18, sy + 18, 0xC0303030);
        }
    }
}
