package com.automatavalkyries.menu;

import com.automatavalkyries.item.ItemPocketItem;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * アイテムポケットの画面（{@link ItemPocketItem}）。1 ページ 54 枠（容量が少なければその行数）、ページはボタンで送る。
 * <p>サーバーはポケットの中身すべてを持ち、枠は「今のページ × 54 + 何番目」を見る。描く側は今のページの分だけを持つ
 * （ページを送るとサーバーが新しいページの中身を枠ごとに送り直す）。持っているポケットの枠は動かせない</p>
 */
public class ItemPocketMenu extends AbstractContainerMenu {

    public static final int BUTTON_PREV = 0, BUTTON_NEXT = 1, BUTTON_AUTO = 2, BUTTON_CATEGORY = 3;

    final ItemStack pocket;
    final InteractionHand hand;
    public final int capacity, rows, pages;
    final boolean client;
    final SimpleContainer store;
    final DataSlot page = DataSlot.standalone(), flags = DataSlot.standalone();
    private final int lockedHotbar;

    public ItemPocketMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(InteractionHand.class));
    }

    public ItemPocketMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModItems.POCKET_MENU.get(), id);
        Player player = inv.player;
        this.hand = hand;
        this.pocket = player.getItemInHand(hand);
        this.capacity = Math.max(1, ItemPocketItem.capacityOf(pocket));
        this.rows = Math.min(6, (capacity + 8) / 9);
        this.pages = (capacity + ItemPocketItem.PAGE - 1) / ItemPocketItem.PAGE;
        this.client = player.level().isClientSide;
        this.store = new SimpleContainer(client ? rows * 9 : capacity);
        if (!client) {
            NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
            for (int i = 0; i < items.size() && i < store.getContainerSize(); i++) store.setItem(i, items.get(i));
            store.addListener(c -> save());
            flags.set(ItemPocketItem.flags(pocket));
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) addSlot(new PageSlot(this, r * 9 + c, 8 + c * 18, 18 + r * 18));
        }
        lockedHotbar = hand == InteractionHand.MAIN_HAND ? inv.selected : -1;
        int invY = 31 + rows * 18;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, invY + r * 18));
        }
        for (int c = 0; c < 9; c++) {
            if (c == lockedHotbar) {
                addSlot(new Slot(inv, c, 8 + c * 18, invY + 58) {
                    @Override
                    public boolean mayPickup(Player p) {
                        return false;
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            } else {
                addSlot(new Slot(inv, c, 8 + c * 18, invY + 58));
            }
        }
        addDataSlot(page);
        addDataSlot(flags);
    }

    /** このポケットの画面か */
    public boolean holds(ItemStack stack) {
        return pocket == stack;
    }

    public int page() {
        return page.get();
    }

    public int flags() {
        return flags.get();
    }

    private void save() {
        NonNullList<ItemStack> items = NonNullList.withSize(capacity, ItemStack.EMPTY);
        for (int i = 0; i < capacity && i < store.getContainerSize(); i++) items.set(i, store.getItem(i));
        ItemPocketItem.save(pocket, items);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_PREV) page.set(Math.floorMod(page.get() - 1, pages));
        else if (id == BUTTON_NEXT) page.set(Math.floorMod(page.get() + 1, pages));
        else if (id == BUTTON_AUTO) ItemPocketItem.toggleAuto(pocket);
        else if (id >= BUTTON_CATEGORY && id < BUTTON_CATEGORY + ItemPocketItem.Category.values().length) ItemPocketItem.toggle(pocket, ItemPocketItem.Category.values()[id - BUTTON_CATEGORY]);
        else return false;
        flags.set(ItemPocketItem.flags(pocket));
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (type == ClickType.SWAP && (button == 40 && hand == InteractionHand.OFF_HAND || button == lockedHotbar)) return;   // 持っているポケットを入れ替えで動かさない
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        int own = rows * 9;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!ItemPocketItem.canStore(stack) || !moveItemStackTo(stack, 0, own, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand) == pocket;
    }

    /** 今のページの枠（サーバーはページ分ずらして中身全体を、描く側はページ分だけを見る） */
    static class PageSlot extends Slot {
        private final ItemPocketMenu menu;

        PageSlot(ItemPocketMenu menu, int index, int x, int y) {
            super(menu.store, index, x, y);
            this.menu = menu;
        }

        /** ポケットの中で何番目の枠か */
        int inPocket() {
            return menu.page.get() * ItemPocketItem.PAGE + getContainerSlot();
        }

        int stored() {
            return menu.client ? getContainerSlot() : inPocket();
        }

        boolean exists() {
            return inPocket() < menu.capacity;
        }

        @Override
        public ItemStack getItem() {
            return exists() ? container.getItem(stored()) : ItemStack.EMPTY;
        }

        @Override
        public void set(ItemStack stack) {
            if (menu.client || exists()) container.setItem(stored(), stack);   // 描く側はページの切り替わりより先に中身が届くことがある
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            return exists() ? container.removeItem(stored(), amount) : ItemStack.EMPTY;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return exists() && ItemPocketItem.canStore(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return exists();
        }

        @Override
        public boolean isActive() {
            return exists();
        }
    }
}
