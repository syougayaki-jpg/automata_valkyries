package com.automatavalkyries.item;

import com.automatavalkyries.tactical.TacticalProtocol;
import com.automatavalkyries.tactical.TacticalProtocols;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 戦術級コード・オーバーライド（クリエイティブ用。2026-10-29 ユーザー指定「クリエイティブ用のクールタイムのない戦術コードを用意して」）:
 * 右クリックで、見ている所（{@value #RANGE} ブロックまで）へ選んだコードをすぐ発動する。兵姫の承認・同調率・電力・クールタイムは要らない。
 * コードの選び方は戦術ポインターと同じ（V キー・しゃがみ右クリック）。味方（自分・ほかのプレイヤー・自分の兵姫・飼っている動物）には当たらない
 */
public class TacOverrideItem extends TacPointerItem {

    public static final int RANGE = 256;

    public TacOverrideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) cycle(player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                player.displayClientMessage(Component.translatable("message.automata_valkyries.tactical.no_target").withStyle(ChatFormatting.GRAY), true);
            } else {
                TacticalProtocols.creativeLaunch(sp, selected(stack), Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TacticalProtocol p = selected(stack);
        tooltip.add(Component.translatable("item.automata_valkyries.tool_tac_pointer.selected",
                Component.translatable("tactical.automata_valkyries." + p.id)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tactical.automata_valkyries." + p.id + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.tac_override.desc").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
