package com.automatavalkyries.upgrade;

import com.automatavalkyries.weapon.WeaponUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * カスタム・マキシマイザー（クリエイティブ用。2026-10-04 ユーザー指定）: もう片方の手に持った兵装のカスタム（{@link WeaponUpgrades}）を
 * 4 つとも上限（+1000%）にする。スニークしながらだと 0 に戻す。減らない
 */
public class CustomMaximizerItem extends Item {

    public CustomMaximizerItem(Properties properties) {
        super(properties);
    }

    /** 兵装のカスタムを全部 level にする（GameTest からも呼ぶ） */
    public static void setAll(ItemStack weapon, int level) {
        for (WeaponUpgrades.Stat stat : WeaponUpgrades.Stat.values()) WeaponUpgrades.setLevel(weapon, stat, level);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        ItemStack weapon = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!WeaponUpgrades.upgradeable(weapon)) {
            if (!level.isClientSide()) player.displayClientMessage(Component.translatable("item.automata_valkyries.custom_maximizer.no_weapon").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(self);
        }
        if (!level.isClientSide()) {
            boolean reset = player.isShiftKeyDown();
            setAll(weapon, reset ? 0 : WeaponUpgrades.MAX_LEVEL);
            player.displayClientMessage(Component.translatable("item.automata_valkyries.custom_maximizer." + (reset ? "reset" : "maxed"), weapon.getHoverName())
                    .withStyle(reset ? ChatFormatting.GRAY : ChatFormatting.AQUA), true);
            level.playSound(null, player.blockPosition(), reset ? SoundEvents.GRINDSTONE_USE : SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, reset ? 1.0F : 1.4F);
        }
        return InteractionResultHolder.sidedSuccess(self, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.custom_maximizer.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.custom_maximizer.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
