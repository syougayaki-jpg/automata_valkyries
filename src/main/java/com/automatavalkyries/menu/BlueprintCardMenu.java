package com.automatavalkyries.menu;

import com.automatavalkyries.item.BlueprintCardItem;
import com.automatavalkyries.registry.ModChrono;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 設計図カードの画面（2026-10-29 ユーザー指定「設計図カードもJEIの窓からもレシピを持ってこれるようにして」）: 書き込んであるレシピを見せ、消すボタンを置く。
 * この画面を開いたまま JEI のレシピの「+」を押すと、そのレシピをカードに書き込む（{@code compat/jei/BlueprintTransferHandler}）。
 * 持っているカードの枠は動かせない
 */
public class BlueprintCardMenu extends AbstractContainerMenu {

    public static final int BUTTON_CLEAR = 0;
    public static final int WIDTH = 176, HEIGHT = 166, INV_Y = 84;

    final InteractionHand hand;
    private final int lockedHotbar;

    public BlueprintCardMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(InteractionHand.class));
    }

    public BlueprintCardMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModChrono.BLUEPRINT_MENU.get(), id);
        this.hand = hand;
        lockedHotbar = hand == InteractionHand.MAIN_HAND ? inv.selected : -1;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) {
            if (c == lockedHotbar) {
                addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            } else {
                addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
            }
        }
    }

    /** 開いているカード */
    public ItemStack card(Player player) {
        return player.getItemInHand(hand);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CLEAR) return false;
        card(player).removeTagKey("Recipe");
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (type == ClickType.SWAP && (button == 40 && hand == InteractionHand.OFF_HAND || button == lockedHotbar)) return;   // 持っているカードを入れ替えで動かさない
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return card(player).getItem() instanceof BlueprintCardItem;
    }
}
