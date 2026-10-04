package com.automatavalkyries.upgrade;

import com.automatavalkyries.weapon.Overload;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * オーバーロード・イグナイター（クリエイティブ用。2026-10-05 ユーザー指定）: 持ち物の兵装を全部まとめてオーバーロード化する
 * （{@link Overload}、オーバーロードメモリのクラフトと同じ印）。スニークしながらだと全部元に戻す。減らない
 */
public class OverloadIgniterItem extends Item {

    public OverloadIgniterItem(Properties properties) {
        super(properties);
    }

    /** 持ち物の兵装を全部オーバーロード化する（on = false で元に戻す）。変えた数を返す（GameTest からも呼ぶ） */
    public static int applyAll(Inventory inventory, boolean on) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (on && Overload.canApply(stack)) {
                stack.getOrCreateTag().putBoolean(Overload.TAG, true);
                n++;
            } else if (!on && stack.getItem() instanceof TacticalWeaponItem && Overload.is(stack)) {
                stack.getTag().remove(Overload.TAG);
                n++;
            }
        }
        return n;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            boolean revert = player.isShiftKeyDown();
            int n = applyAll(player.getInventory(), !revert);
            if (n == 0) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.overload_igniter.none").withStyle(ChatFormatting.GRAY), true);
            } else {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.overload_igniter." + (revert ? "reverted" : "applied"), n)
                        .withStyle(revert ? ChatFormatting.GRAY : ChatFormatting.RED), true);
                level.playSound(null, player.blockPosition(), revert ? SoundEvents.GRINDSTONE_USE : SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, revert ? 1.0F : 0.7F);
            }
        }
        return InteractionResultHolder.sidedSuccess(self, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.overload_igniter.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.overload_igniter.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
