package com.automatavalkyries.weapon;

import com.automatavalkyries.item.ItemEnergy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.List;

/**
 * ルミナルコア（既存仕様書 §7.1）: 兵姫のコアスロットに入れる主電源。プレイヤーが持ち物に入れておくと、手持ちの兵装の
 * 内蔵バッテリーへ放電レートまで流し込む（{@link WeaponPower#topUp}）。タクティカル・アーモリーで組み立てる。
 * 固有パッシブ（時間停止の無効化・因果逆転の蘇生・全ステータス 3 倍）は兵姫に積んだときの物。全ステータス 3 倍は実装済み（{@code ValkyrieEntity#omegaMultiplier}、2026-10-05）、残りは後でつなぐ。
 * ステラ・ジェネシス・オメガは無限動力（いくら引いても減らない）。
 */
public class LuminalCoreItem extends Item {

    public enum Grade {
        BASIC("core_basic", 500_000, 5_000, 5.0F),
        HIGH_DENSITY("core_high_density", 5_000_000, 20_000, 7.5F),
        SINGULARITY("core_singularity", 50_000_000, 200_000, 10.0F),
        CHRONO_GENESIS("core_chrono_genesis", 100_000_000, 500_000, 12.0F),
        STELLA_OMEGA("core_stella_genesis_omega", Integer.MAX_VALUE, Integer.MAX_VALUE, 0.0F);

        public final String id;
        public final int capacity, rate;
        public final float weight;

        Grade(String id, int capacity, int rate, float weight) {
            this.id = id;
            this.capacity = capacity;
            this.rate = rate;
            this.weight = weight;
        }

        public boolean infinite() {
            return this == STELLA_OMEGA;
        }
    }

    private final Grade grade;

    public LuminalCoreItem(Properties properties, Grade grade) {
        super(properties.stacksTo(1));
        this.grade = grade;
    }

    public Grade grade() {
        return grade;
    }

    /** このコアから amount まで（放電レートで頭打ち）引き出し、引けた量を返す */
    public int extract(ItemStack stack, int amount) {
        int want = Math.min(amount, grade.rate);
        if (grade.infinite()) return want;
        int stored = ItemEnergy.get(stack);
        int taken = Math.min(stored, want);
        if (taken > 0) ItemEnergy.set(stack, stored - taken);
        return taken;
    }

    /** 満充電のコア（クリエイティブのタブ用） */
    public ItemStack charged() {
        ItemStack stack = new ItemStack(this);
        if (!grade.infinite()) ItemEnergy.set(stack, grade.capacity);
        return stack;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return grade.infinite() ? new Infinite() : ItemEnergy.provider(stack, grade.capacity, grade.rate);
    }

    @Override public boolean isBarVisible(ItemStack stack) { return !grade.infinite(); }
    @Override public int getBarWidth(ItemStack stack) { return ItemEnergy.barWidth(stack, grade.capacity); }
    @Override public int getBarColor(ItemStack stack) { return ItemEnergy.barColor(stack, grade.capacity); }

    @Override
    public boolean isFoil(ItemStack stack) {
        return grade.ordinal() >= Grade.SINGULARITY.ordinal();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        NumberFormat format = NumberFormat.getIntegerInstance();
        if (grade.infinite()) {
            tooltip.add(Component.translatable("gui.automata_valkyries.energy_infinite").withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(ItemEnergy.tooltip(stack, grade.capacity));
            tooltip.add(Component.translatable("gui.automata_valkyries.discharge_rate", format.format(grade.rate)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("gui.automata_valkyries.weight", String.valueOf(grade.weight)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries." + grade.id + ".desc").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** 無限動力: いくらでも出せて、受け取らない */
    private static final class Infinite implements ICapabilityProvider, IEnergyStorage {
        private final LazyOptional<IEnergyStorage> self = LazyOptional.of(() -> this);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.ENERGY ? self.cast() : LazyOptional.empty();
        }

        @Override public int receiveEnergy(int maxReceive, boolean simulate) { return 0; }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return maxExtract; }
        @Override public int getEnergyStored() { return Integer.MAX_VALUE; }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    }
}
