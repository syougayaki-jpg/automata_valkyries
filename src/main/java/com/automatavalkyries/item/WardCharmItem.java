package com.automatavalkyries.item;

import com.automatavalkyries.protect.PlayerWard;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 守護の護符。持ち物にあるだけで、他 MOD の強制テレポートと即死から持ち主を守る（中身は {@link PlayerWard}） */
public class WardCharmItem extends Item {

    public WardCharmItem(Properties properties) {
        super(properties);
    }

    public static Properties defaultProperties() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.ward_charm.tooltip." + i)
                    .withStyle(i == 1 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
