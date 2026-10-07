package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 終焉の魔杖・特権守護の核の説明の最後に「Java エージェント無しでは不安定に動くことがある」と注意を足す（2026-10-07 ユーザー指定）。
 * アイテムのクラスは Antigravity 側なので触らず、説明のイベントで足す
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class AgentNoticeTooltips {

    private AgentNoticeTooltips() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.is(ModItems.TERMINUS_STICK.get()) || stack.is(ModItems.PRIVILEGE_AEGIS.get())) {
            event.getToolTip().add(Component.translatable("tooltip.automata_valkyries.agent_unstable").withStyle(ChatFormatting.YELLOW));
        }
    }
}
