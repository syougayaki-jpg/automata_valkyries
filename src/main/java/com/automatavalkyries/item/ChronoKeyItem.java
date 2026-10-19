package com.automatavalkyries.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 時の鍵（2026-10-19 ユーザー指定「三種の AEC 級の素材で時の鍵」）。ルクシオンの核・ガリアフォートの装甲板・メメントモリの翼膜と時計で作る（recipes/chrono_key）。
 * 空中庭園要塞の眠れる時の門に使うと門が目覚める（要塞守護機を倒してから。{@link com.automatavalkyries.block.RulerPortalBlock}）。使うとなくなる
 */
public class ChronoKeyItem extends Item {

    public ChronoKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.chrono_key.desc").withStyle(ChatFormatting.GRAY));
    }
}
