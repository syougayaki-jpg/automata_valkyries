package com.automatavalkyries.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * プロトコア（追加拡張仕様書 §11.1.4）: 基礎ルミナルコア（core_basic）より前の最初期コア。組み立て機で作る。
 * 小さな蓄電を持ち、墜落ポッドで眠る兵姫を目覚めさせる鍵になる（目覚めさせる処理は兵姫を実装してからつなぐ）。
 */
public class ProtoCoreItem extends Item {

    public static final int CAPACITY = 100_000, TRANSFER = 1_000;

    public ProtoCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return ItemEnergy.provider(stack, CAPACITY, TRANSFER);
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return ItemEnergy.barWidth(stack, CAPACITY); }
    @Override public int getBarColor(ItemStack stack) { return ItemEnergy.barColor(stack, CAPACITY); }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ItemEnergy.get(stack) >= CAPACITY;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ItemEnergy.tooltip(stack, CAPACITY));
        tooltip.add(Component.translatable("item.automata_valkyries.core_proto.desc").withStyle(ChatFormatting.GRAY));
    }
}
