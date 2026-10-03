package com.automatavalkyries.trial;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** オーバーロードメモリ（回想のトライアルの全クリアの報酬）。兵装と作業台で合わせるとオーバーロード化 */
public class OverloadMemoryItem extends Item {

    public OverloadMemoryItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.overload_memory.desc").withStyle(ChatFormatting.GRAY));
    }
}
