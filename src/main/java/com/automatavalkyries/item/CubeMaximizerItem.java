package com.automatavalkyries.item;

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
 * 観測の立方体マキシマイザー（クリエイティブ用。2026-10-29 ユーザー指定「全部MAXにできるクリエイティブアイテムも追加して」）: 右クリックで、持ち物の観測の立方体を
 * 全部、8 つの能力とも Lv {@value AkashicCubeUpgrades#MAX_LEVEL} にする（{@link AkashicCubeUpgrades}）。スニークしながらだと 0 に戻す。減らない
 */
public class CubeMaximizerItem extends Item {

    public CubeMaximizerItem(Properties properties) {
        super(properties);
    }

    /** 持ち物の観測の立方体を全部 max（false なら 0）にする。変えた数を返す（GameTest からも呼ぶ） */
    public static int applyAll(Inventory inventory, boolean max) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof AkashicCubesItem)) continue;
            for (AkashicCubeUpgrades.Stat stat : AkashicCubeUpgrades.Stat.values()) AkashicCubeUpgrades.setLevel(stack, stat, max ? AkashicCubeUpgrades.MAX_LEVEL : 0);
            if (!max) stack.getTag().remove(AkashicCubeUpgrades.TAG);
            n++;
        }
        return n;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            boolean reset = player.isShiftKeyDown();
            int n = applyAll(player.getInventory(), !reset);
            if (n == 0) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.cube_maximizer.none").withStyle(ChatFormatting.GRAY), true);
            } else {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.cube_maximizer." + (reset ? "reset" : "maxed"), n)
                        .withStyle(reset ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE), true);
                level.playSound(null, player.blockPosition(), reset ? SoundEvents.GRINDSTONE_USE : SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, reset ? 1.0F : 1.2F);
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
        tooltip.add(Component.translatable("item.automata_valkyries.cube_maximizer.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.cube_maximizer.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
