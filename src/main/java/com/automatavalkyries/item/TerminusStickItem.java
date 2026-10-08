package com.automatavalkyries.item;

import com.automatavalkyries.annihilate.TerminusEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
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
 * 終焉の魔杖（Terminus Stick / Scepter of Absolute Finality）。
 * <p>
 * Eprotect+ Java エージェント（JVM レベルの特権操作・バイトコード迂回機構）を完全搭載した究極の終焉兵装。
 * Witherzilla や Metapotent Flashfur を含むあらゆるチートMODボスの汎用解体・完全抹殺に加え、
 * クリエイティブモード・無敵フラグ・チート防具を着用した<b>プレイヤーに対しても絶対即死・終焉</b>を執行できる。
 * </p>
 */
public class TerminusStickItem extends Item {

    private static final String TAG_INCLUDE_PLAYERS = "IncludePlayers";

    public TerminusStickItem(Properties properties) {
        super(properties);
    }

    public static Properties defaultProperties() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    public static boolean isIncludePlayers(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG_INCLUDE_PLAYERS)) return false; // デフォルトは安全のためプレイヤー除外（MOBのみ対象）
        return stack.getTag().getBoolean(TAG_INCLUDE_PLAYERS);
    }

    public static void setIncludePlayers(ItemStack stack, boolean include) {
        stack.getOrCreateTag().putBoolean(TAG_INCLUDE_PLAYERS, include);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (player.level() instanceof ServerLevel serverLevel) {
            boolean includePlayers = isIncludePlayers(stack);
            if (entity instanceof Player && !includePlayers) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.terminus_stick.player_safe")
                        .withStyle(ChatFormatting.YELLOW), true);
                return true;
            }

            boolean success = TerminusEngine.executeTerminus(serverLevel, player, entity);
            if (success) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.terminus_stick.used",
                        entity.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            }
        }
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level() instanceof ServerLevel level) {
            boolean includePlayers = isIncludePlayers(stack);
            if (target instanceof Player && !includePlayers) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.terminus_stick.player_safe")
                        .withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.FAIL;
            }

            boolean success = TerminusEngine.executeTerminus(level, player, target);
            if (success) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.terminus_stick.used",
                        target.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            // スニーク右クリック: プレイヤー対象モードの切り替え
            if (!level.isClientSide) {
                boolean current = isIncludePlayers(itemInHand);
                setIncludePlayers(itemInHand, !current);
                Component msg = !current
                        ? Component.translatable("item.automata_valkyries.terminus_stick.mode_all")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        : Component.translatable("item.automata_valkyries.terminus_stick.mode_mobs_only")
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
                player.displayClientMessage(msg, true);
            }
            return InteractionResultHolder.sidedSuccess(itemInHand, level.isClientSide);
        }

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // 通常空中右クリック: 超広域終焉執行（半径 500 ブロック）
            boolean includePlayers = isIncludePlayers(itemInHand);
            if (TerminusEngine.queueAreaTerminus(serverLevel, player, 500.0, includePlayers,
                    eradicated -> player.displayClientMessage(Component.translatable(
                            "item.automata_valkyries.terminus_stick.aoe_used", eradicated)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false))) {
                player.getCooldowns().addCooldown(this, 20);
            }
        }

        return InteractionResultHolder.sidedSuccess(itemInHand, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean includePlayers = isIncludePlayers(stack);
        tooltip.add(Component.translatable("item.automata_valkyries.terminus_stick.mode_indicator",
                includePlayers
                        ? Component.translatable("item.automata_valkyries.terminus_stick.mode_all_short").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        : Component.translatable("item.automata_valkyries.terminus_stick.mode_mobs_short").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        ).withStyle(ChatFormatting.GRAY));

        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.terminus_stick.tooltip." + i)
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
