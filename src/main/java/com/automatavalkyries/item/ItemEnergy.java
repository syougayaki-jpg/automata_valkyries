package com.automatavalkyries.item;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;

/**
 * アイテムが持つ電力（FE）。量はスタックの NBT「Energy」に入れる。蓄電器の充電スロットや他 MOD の充電器で充電できる。
 * 使う側は {@link #provider} を initCapabilities から返し、{@link #get} / {@link #use} で読み書きする。
 */
public final class ItemEnergy {

    private static final String TAG = "Energy";

    private ItemEnergy() {}

    public static int get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG);
    }

    public static void set(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(TAG, Math.max(0, value));
    }

    /** amount を使えたら減らして true（足りなければ何もしない） */
    public static boolean use(ItemStack stack, int amount) {
        int stored = get(stack);
        if (stored < amount) return false;
        set(stack, stored - amount);
        return true;
    }

    public static ICapabilityProvider provider(ItemStack stack, int capacity, int maxTransfer) {
        return new Provider(stack, capacity, maxTransfer);
    }

    /** 耐久バーの代わりに出す電力のバー */
    public static int barWidth(ItemStack stack, int capacity) {
        return Math.round(13.0F * get(stack) / capacity);
    }

    public static int barColor(ItemStack stack, int capacity) {
        float ratio = (float) get(stack) / capacity;
        return Mth.hsvToRgb(0.12F + ratio * 0.38F, 0.85F, 1.0F);
    }

    public static Component tooltip(ItemStack stack, int capacity) {
        NumberFormat format = NumberFormat.getIntegerInstance();
        return Component.translatable("gui.automata_valkyries.energy", format.format(get(stack)), format.format(capacity))
                .withStyle(net.minecraft.ChatFormatting.GOLD);
    }

    private static final class Provider implements ICapabilityProvider, IEnergyStorage {
        private final ItemStack stack;
        private final int capacity, maxTransfer;
        private final LazyOptional<IEnergyStorage> self = LazyOptional.of(() -> this);

        Provider(ItemStack stack, int capacity, int maxTransfer) {
            this.stack = stack;
            this.capacity = capacity;
            this.maxTransfer = maxTransfer;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.ENERGY ? self.cast() : LazyOptional.empty();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int stored = get(stack);
            int received = Math.min(capacity - stored, Math.min(maxTransfer, maxReceive));
            if (!simulate && received > 0) set(stack, stored + received);
            return Math.max(0, received);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int stored = get(stack);
            int extracted = Math.min(stored, Math.min(maxTransfer, maxExtract));
            if (!simulate && extracted > 0) set(stack, stored - extracted);
            return extracted;
        }

        @Override public int getEnergyStored() { return get(stack); }
        @Override public int getMaxEnergyStored() { return capacity; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    }
}
