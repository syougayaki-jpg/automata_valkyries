package com.automatavalkyries.item;

import com.automatavalkyries.annihilate.TrueAnnihilationEngine;
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
 * 真抹殺の棒（True Annihilation Stick）。
 * <p>
 * Eprotect+ Java エージェント（JVM レベルの特権操作・バイトコード迂回機構）を完全搭載した究極の抹殺兵装。
 * Witherzilla（生命維持フラグ witherzillaLife、強制回復、強制消去拒否）や
 * Metapotent Flashfur（StackChecker スタック検閲防壁、healthDelta、Proxy 構造）を含む、
 * あらゆる最強・チートボスの防御機構を Java エージェント空間から迂回・解体し、不可逆的消滅へと至らしめる。
 * </p>
 */
public class TrueKillStickItem extends Item {

    public TrueKillStickItem(Properties properties) {
        super(properties);
    }

    public static Properties defaultProperties() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof Player) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.true_kill_stick.no_player")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.FAIL;
        }

        if (player.level() instanceof ServerLevel level) {
            boolean success = TrueAnnihilationEngine.annihilateEntity(level, player, target);
            if (success) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.true_kill_stick.used",
                        target.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            }
        }

        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // 空中右クリック（超広域真抹殺モード）: 半径 500 ブロック内の全エンティティ（プレイヤー・兵姫を除く）をエージェント真抹殺
            int eradicated = TrueAnnihilationEngine.annihilateAllInArea(serverLevel, player, 500.0);
            player.displayClientMessage(Component.translatable("item.automata_valkyries.true_kill_stick.aoe_used",
                    eradicated).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        }

        return InteractionResultHolder.sidedSuccess(itemInHand, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 5; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.true_kill_stick.tooltip." + i)
                    .withStyle(i <= 2 ? ChatFormatting.DARK_RED : ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
