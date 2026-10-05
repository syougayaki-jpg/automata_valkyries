package com.automatavalkyries.item;

import com.automatavalkyries.protect.AgentPlayerProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 特権守護の核（Privilege Aegis）。
 * <p>
 * 右クリックでプレイヤーの魂（UUID）にJVMエージェント（LifeGuard / Eprotect+）の不可侵特権を刻印する。
 * 刻印されたプレイヤーは、たとえこのアイテムをインベントリから剥奪・消去されても、
 * JVM最外郭（Outermost Layer）のバイトコードおよびUnsafe保護により永続的に生存する。
 * スニーク右クリックで刻印を解除可能。
 * </p>
 */
public class PrivilegeAegisItem extends Item {

    public PrivilegeAegisItem(Properties properties) {
        super(properties);
    }

    public static Properties defaultProperties() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                AgentPlayerProtection.unbind(sp);
            } else {
                AgentPlayerProtection.bind(sp);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.privilege_aegis.tooltip." + i)
                    .withStyle(i == 1 ? ChatFormatting.LIGHT_PURPLE : i == 4 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
