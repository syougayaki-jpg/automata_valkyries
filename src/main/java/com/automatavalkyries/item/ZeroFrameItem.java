package com.automatavalkyries.item;

import com.automatavalkyries.chrono.ChargingDockBlockEntity;
import com.automatavalkyries.entity.ValkyrieBody;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 零号機の素体（2026-10-20。隠しボス「マリスライズ・ロストカタストロフィー」が落とす）。
 * 給電ドックに右クリックで使うと、ドックの上に眠った零号機（持ち主なし・深い休眠、{@link ValkyrieEntity#isDormantZero}）を安置する。
 * 起こすのは零号機再起動パッチ（{@link ZeroRebootPatchItem}）だけ。ドックの画面より先に受け取るので {@code onItemUseFirst} で
 */
public class ZeroFrameItem extends Item {

    public ZeroFrameItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos dock = ctx.getClickedPos();
        if (player == null || !(level.getBlockEntity(dock) instanceof ChargingDockBlockEntity)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        BlockPos at = dock.above();
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie_body.no_room").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        // 1 つのドックに安置できる零号機は 1 体
        if (!level.getEntitiesOfClass(ValkyrieEntity.class, new AABB(at).inflate(1.0), ValkyrieEntity::isDormantZero).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.zero.already_docked").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ValkyrieEntity v = ValkyrieBodyItem.spawn(server, at, player.getYRot() + 180.0F, ValkyrieBody.Z); // 深い休眠・HP 満タン・電力 0
        if (v == null) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        server.playSound(null, at, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 0.6F);
        Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), 0xFF2858, 0.4F, 20), v.position().add(0, 0.8, 0), 24, 0.4, 0.7, 0.4, 0.02);
        player.displayClientMessage(Component.translatable("message.automata_valkyries.zero.docked").withStyle(ChatFormatting.DARK_RED), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        // ドック以外に使ったとき（ドックは onItemUseFirst で受け取る）
        if (ctx.getPlayer() != null && !ctx.getLevel().isClientSide) {
            ctx.getPlayer().displayClientMessage(Component.translatable("message.automata_valkyries.zero.need_dock").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.zero_frame.desc").withStyle(ChatFormatting.GRAY));
    }
}
