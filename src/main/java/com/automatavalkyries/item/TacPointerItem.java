package com.automatavalkyries.item;

import com.automatavalkyries.tactical.TacticalProtocol;
import com.automatavalkyries.tactical.TacticalProtocols;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 戦術ポインター（追加拡張仕様書 §13.1。2026-10-07 実装）: 戦術級コードを要請する。
 * 「V」キー（またはしゃがんで右クリック）でコードを選び、右クリックを 1 秒長押しして離すと、見ている所（128 ブロックまで）へ要請する。
 * 承認は近くの自分の兵姫が自動で行う（{@link TacticalProtocols#request}）
 */
public class TacPointerItem extends Item {

    public static final int AIM_TICKS = 20, RANGE = 128;

    public TacPointerItem(Properties properties) {
        super(properties);
    }

    public static TacticalProtocol selected(ItemStack stack) {
        return TacticalProtocol.of(stack.hasTag() ? stack.getTag().getInt("Protocol") : 0);
    }

    /** 次のコードへ（V キー・しゃがみ右クリック） */
    public static void cycle(Player player, ItemStack stack) {
        TacticalProtocol next = TacticalProtocol.of(selected(stack).ordinal() + 1);
        stack.getOrCreateTag().putInt("Protocol", next.ordinal());
        player.displayClientMessage(Component.translatable("message.automata_valkyries.tactical.selected",
                Component.translatable("tactical.automata_valkyries." + next.id), next.sync, String.format("%,d", next.fe)).withStyle(ChatFormatting.AQUA), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.5F);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) cycle(player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand); // 長押しで狙う
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide || !(user instanceof ServerPlayer player)) return;
        if (getUseDuration(stack) - timeLeft < AIM_TICKS) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tactical.hold").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tactical.no_target").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        TacticalProtocols.request(player, selected(stack), target);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TacticalProtocol p = selected(stack);
        tooltip.add(Component.translatable("item.automata_valkyries.tool_tac_pointer.selected",
                Component.translatable("tactical.automata_valkyries." + p.id)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tactical.automata_valkyries." + p.id + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.tool_tac_pointer.cost", p.sync, String.format("%,d", p.fe), p.cooldown / 20).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.tool_tac_pointer.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
