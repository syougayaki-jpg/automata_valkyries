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

/**
 * 無敵の護符。持ち物にあるだけで、ダメージをすべて無効にし HP を最大に保つ（中身は {@link InvincibleCharm}）。
 * 右クリックで入・切（2026-10-20 ユーザー指定「無敵の護符のオンオフがない」。切った護符は持っていても効かない。切った瞬間に無敵の名残も消える）
 */
public class InvincibleCharmItem extends Item {

    public InvincibleCharmItem(Properties properties) {
        super(properties);
    }

    public static Properties defaultProperties() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    /** 入っているか（既定は入） */
    public static boolean enabled(ItemStack stack) {
        return !(stack.hasTag() && stack.getTag().getBoolean("Off"));
    }

    /** 右クリックで入・切 */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean on = !enabled(stack);
            if (on) stack.removeTagKey("Off");
            else stack.getOrCreateTag().putBoolean("Off", true);
            if (!on) InvincibleCharm.disableNow(player);
            else InvincibleCharm.refresh(player);
            player.displayClientMessage(Component.translatable(on ? "item.automata_valkyries.invincible_charm.on" : "item.automata_valkyries.invincible_charm.off")
                    .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
            level.playSound(null, player.blockPosition(), on ? net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE : net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(enabled(stack) ? "item.automata_valkyries.invincible_charm.state_on" : "item.automata_valkyries.invincible_charm.state_off")
                .withStyle(enabled(stack) ? ChatFormatting.GREEN : ChatFormatting.RED));
        for (int i = 1; i <= 3; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.invincible_charm.tooltip." + i)
                    .withStyle(i == 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return enabled(stack);                                          // 切ると光らない
    }
}
