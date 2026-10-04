package com.automatavalkyries.upgrade;

import com.automatavalkyries.registry.ModUpgrades;
import com.automatavalkyries.weapon.WeaponUpgrades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 兵装強化台の画面の中身（バニラのエンチャントテーブルと同じ作り: 置いた物は閉じると返す）。枠 0 = 兵装、枠 1 = オーブ・スター・コア。
 * 能力のボタン（0〜3 = {@link WeaponUpgrades.Stat}）を押すと、サーバーで 1 回強化する（金属は持ち物から引く）。
 */
public class UpgradeStationMenu extends AbstractContainerMenu {

    public static final int WEAPON = 0, CATALYST = 1;
    /** 画面の大きさと、枠の位置（{@code client/UpgradeStationScreen} と合わせる） */
    public static final int WIDTH = 240, HEIGHT = 214, WEAPON_X = 20, WEAPON_Y = 30, CATALYST_X = 20, CATALYST_Y = 74, INV_X = 40, INV_Y = 132;

    private final Container inputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            UpgradeStationMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private final Player player;

    public UpgradeStationMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public UpgradeStationMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModUpgrades.MENU.get(), id);
        this.access = access;
        this.player = inventory.player;
        addSlot(new Slot(inputs, WEAPON, WEAPON_X, WEAPON_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return WeaponUpgrades.upgradeable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(inputs, CATALYST, CATALYST_X, CATALYST_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(WeaponUpgrades.ORBS) || stack.is(WeaponUpgrades.STARS) || stack.is(WeaponUpgrades.CORES);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INV_X + col * 18, INV_Y + 58));
    }

    public ItemStack weapon() {
        return inputs.getItem(WEAPON);
    }

    public ItemStack catalyst() {
        return inputs.getItem(CATALYST);
    }

    public Player player() {
        return player;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= WeaponUpgrades.Stat.values().length) return false;
        WeaponUpgrades.Stat stat = WeaponUpgrades.Stat.values()[id];
        if (!WeaponUpgrades.upgrade(player, weapon(), catalyst(), stat)) return false;
        inputs.setChanged();
        access.execute((level, pos) -> {
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
        });
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int invStart = 2, invEnd = this.slots.size();
        if (index < invStart) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else if (WeaponUpgrades.upgradeable(stack)) {
            if (!moveItemStackTo(stack, WEAPON, WEAPON + 1, false)) return ItemStack.EMPTY;
        } else if (this.slots.get(CATALYST).mayPlace(stack)) {
            if (!moveItemStackTo(stack, CATALYST, CATALYST + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, inputs));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModUpgrades.STATION.get());
    }
}
