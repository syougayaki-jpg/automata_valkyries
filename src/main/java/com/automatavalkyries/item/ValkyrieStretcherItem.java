package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 戦術兵姫用担架（2026-10-07 ユーザー指定「充電切れと機能停止状態の戦術兵姫を運ぶ担架（頭の上に乗せる）」）。
 * 電力切れ（休眠）か機能停止の自分の兵姫を右クリックすると、持ち主の頭の上に乗せて運ぶ（座った姿勢）。
 * 空中で右クリックすると目の前に降ろす。乗せている間に動けるようになったら（充電・修理）自分で降りる（{@link ValkyrieEntity}）
 */
public class ValkyrieStretcherItem extends Item {

    public ValkyrieStretcherItem(Properties properties) {
        super(properties);
    }

    /** 担架で運べる状態か（電力切れか機能停止） */
    public static boolean carriable(ValkyrieEntity v) {
        return v.isShutdown() || v.isAsleep();
    }

    @Nullable
    private static ValkyrieEntity carried(Player player) {
        for (var e : player.getPassengers()) if (e instanceof ValkyrieEntity v) return v;
        return null;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ValkyrieEntity v) || !player.getUUID().equals(v.getOwnerUUID())) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        // 頭の上の兵姫に視線が当たると、空中の右クリックではなくこの兵姫への右クリックになる（降ろせなかった。2026-10-07）
        if (v.getVehicle() == player) {
            setDown(player, v);
            return InteractionResult.CONSUME;
        }
        if (!carriable(v)) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.stretcher.only_down").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (carried(player) != null) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.stretcher.busy").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        v.stopRiding();
        v.getNavigation().stop();
        if (v.startRiding(player, true)) {
            v.setSitting(true);
            sync(player);
            player.displayClientMessage(Component.translatable("message.automata_valkyries.stretcher.picked", v.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ValkyrieEntity v = carried(player);
        if (v == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) setDown(player, v);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 目の前に降ろす */
    public static void setDown(Player player, ValkyrieEntity v) {
        v.stopRiding();
        Vec3 look = player.getLookAngle();
        Vec3 front = new Vec3(look.x, 0, look.z);
        front = front.lengthSqr() < 1.0e-4 ? Vec3.ZERO : front.normalize().scale(1.2);
        v.teleportTo(player.getX() + front.x, player.getY(), player.getZ() + front.z);
        v.setYRot(player.getYRot() + 180.0F);
        sync(player);
        player.displayClientMessage(Component.translatable("message.automata_valkyries.stretcher.placed", v.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** プレイヤーの上に乗る・降りるのは本人の画面へは自動で送られないので送る */
    public static void sync(Player player) {
        if (player instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetPassengersPacket(player));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.valkyrie_stretcher.desc").withStyle(ChatFormatting.GRAY));
    }
}
