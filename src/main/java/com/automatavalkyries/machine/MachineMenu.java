package com.automatavalkyries.machine;

import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * マシンの画面（すべての種類で共通）。スロットの位置は種類ごと:
 * 発電機 = 燃料 1、蓄電器 = 充電 1、加工機 = 材料（1・2・6）→ 結果。
 */
public class MachineMenu extends AbstractContainerMenu {

    /** 画面上のスロットの位置（MachineScreen と合わせる） */
    public static final int OUTPUT_Y = 35, FUEL_X = 80, FUEL_Y = 35;

    /** 材料のスロットの位置 {x, y}: 1 枠は中央、2 枠は縦に、6 枠は 3 × 2 の格子 */
    public static int[] inputPos(MachineKind kind, int index) {
        if (kind.inputs >= 3) return new int[]{30 + (index % 3) * 18, 26 + (index / 3) * 18};
        if (kind.inputs == 2) return new int[]{56, index == 0 ? 17 : 53};
        return new int[]{56, 35};
    }

    /** 結果のスロットと進み具合の矢印の x */
    public static int outputX(MachineKind kind) { return kind.inputs >= 3 ? 124 : 116; }

    public static int arrowX(MachineKind kind) { return kind.inputs >= 3 ? 94 : 79; }

    private final MachineKind kind;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos pos;

    /** クライアント（サーバーから位置と種類を受け取る） */
    public MachineMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readEnum(MachineKind.class));
    }

    private MachineMenu(int id, Inventory inventory, BlockPos pos, MachineKind kind) {
        this(id, inventory, kind, new ItemStackHandler(kind.slots()), new SimpleContainerData(MachineBlockEntity.DATA_COUNT), pos);
    }

    /** サーバー */
    public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) {
        this(id, inventory, machine.kind(), machine.items(), machine.data(), machine.getBlockPos());
    }

    private MachineMenu(int id, Inventory inventory, MachineKind kind, IItemHandler items, ContainerData data, BlockPos pos) {
        super(ModMachines.MENU.get(), id);
        this.kind = kind;
        this.data = data;
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        if (!kind.processes()) {
            addSlot(new SlotItemHandler(items, 0, FUEL_X, FUEL_Y));
        } else {
            for (int i = 0; i < kind.inputs; i++) {
                int[] p = inputPos(kind, i);
                addSlot(new SlotItemHandler(items, i, p[0], p[1]));
            }
            addSlot(new SlotItemHandler(items, kind.outputSlot(), outputX(kind), OUTPUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    public MachineKind kind() { return kind; }

    public int energy() { return (data.get(0) & 0xFFFF) | (data.get(1) << 16); }
    public int capacity() { return (data.get(2) & 0xFFFF) | (data.get(3) << 16); }
    public int progress() { return data.get(4); }
    public int maxProgress() { return data.get(5); }
    public int burn() { return data.get(6); }
    public int burnMax() { return data.get(7); }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, player.level().getBlockState(pos).getBlock());
    }

    /** Shift クリック: マシン ↔ 持ち物 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = kind.slots();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int inputEnd = kind.processes() ? kind.inputs : 1;
            if (!moveItemStackTo(stack, 0, inputEnd, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
