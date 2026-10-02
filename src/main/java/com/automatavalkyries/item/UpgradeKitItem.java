package com.automatavalkyries.item;

import com.automatavalkyries.chrono.ChronoTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * アップデートキット（docs/SPEC_CHRONOLINK_MACHINES.md §3.1）: 一つ下のティアのクロノリンク・マシンを右クリックすると、
 * 中身・空間リンク・電力を保ったままそのティアへ上げる（消費する）。そのティアのルミナルエナジーアダプターで作る。
 */
public class UpgradeKitItem extends Item {

    private final ChronoTier tier;

    public UpgradeKitItem(Properties properties, ChronoTier tier) {
        super(properties);
        this.tier = tier;
    }

    public ChronoTier tier() { return tier; }

    @Override
    public boolean isFoil(ItemStack stack) { return tier.level >= 3; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tier.automata_valkyries." + tier.id).withStyle(ChronoTiers.color(tier)));
        tooltip.add(Component.translatable("item.automata_valkyries.upgrade_kit.desc").withStyle(ChatFormatting.GRAY));
    }
}
