package com.automatavalkyries.item;

import com.automatavalkyries.entity.RetreatableBoss;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 撤退の号令（2026-10-08 ユーザー指定）: 退く演出を見るためのクリエイティブ専用の道具（レシピ無し）。ボス（{@link RetreatableBoss}: ルクシオン・エレノア）に
 * 右クリックでその 1 体、空中で右クリックで半径 {@link #RADIUS} のボス全部に、いつもの退く演出をさせる。減らない
 */
public class RetreatOrderItem extends Item {

    public static final double RADIUS = 64.0;

    public RetreatOrderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof RetreatableBoss boss)) return InteractionResult.PASS;
        if (!player.level().isClientSide) report(player, boss.beginRetreat() ? 1 : 0);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int n = 0;
            for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(RADIUS), e -> e instanceof RetreatableBoss)) {
                if (((RetreatableBoss) e).beginRetreat()) n++;
            }
            report(player, n);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void report(Player player, int n) {
        player.displayClientMessage(n > 0
                ? Component.translatable("item.automata_valkyries.retreat_order.done", n).withStyle(ChatFormatting.GOLD)
                : Component.translatable("item.automata_valkyries.retreat_order.none").withStyle(ChatFormatting.GRAY), true);
        if (n > 0) player.level().playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.retreat_order.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.retreat_order.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
