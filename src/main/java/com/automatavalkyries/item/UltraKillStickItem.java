package com.automatavalkyries.item;

import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 超絶抹殺の棒（Ultra Annihilation Stick）。
 * イベント操作と追跡偽装に特化した専用エージェント（{@link AnnihilationExecutionAgent}）を搭載し、
 * Witherzilla（生命維持フラグ、EventHandle、ChunkMap 追跡）や
 * Metapotent Flashfur（プロキシ構造、StackChecker、ProtectedArrayList、ディスク永続化）
 * を含むあらゆるチートボスを完全かつ不可逆的に抹消する。
 */
public class UltraKillStickItem extends Item {

    public UltraKillStickItem(Properties properties) {
        super(properties);
    }

    public static Properties defaultProperties() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof Player) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.ultra_kill_stick.no_player")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.FAIL;
        }

        if (player.level() instanceof ServerLevel level) {
            boolean success = AnnihilationExecutionAgent.annihilateEntity(level, player, target);
            if (success) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.ultra_kill_stick.used",
                        target.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            }
        }

        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // 空中右クリック（範囲抹消モード）: 半径 500 ブロック内の全エンティティ（プレイヤー・兵姫を除く）を一括抹消
            int eradicated = AnnihilationExecutionAgent.annihilateAllInArea(serverLevel, player, 500.0);
            player.displayClientMessage(Component.translatable("item.automata_valkyries.ultra_kill_stick.aoe_used",
                    eradicated).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        }

        return InteractionResultHolder.sidedSuccess(itemInHand, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.ultra_kill_stick.tooltip." + i)
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
