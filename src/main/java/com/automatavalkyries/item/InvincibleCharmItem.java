package com.automatavalkyries.item;

import com.automatavalkyries.protect.InvincibleCharm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 無敵の護符。持ち物にあるだけで、ダメージをすべて無効にし HP を最大に保つ（中身は {@link InvincibleCharm}） */
public class InvincibleCharmItem extends Item {

    public InvincibleCharmItem(Properties properties) {
        super(properties);
    }

    public static Properties defaultProperties() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 3; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.invincible_charm.tooltip." + i)
                    .withStyle(i == 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
