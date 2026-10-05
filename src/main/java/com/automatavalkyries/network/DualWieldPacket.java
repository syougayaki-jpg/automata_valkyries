package com.automatavalkyries.network;

import com.automatavalkyries.weapon.DualWield;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 二刀流の操作（{@link DualWield}、2026-10-06）: クライアントが左右のクリックを置き換えて送る。
 * MAIN_SKILL = 右手の技（シフト＋左クリック）、OFF_SKILL = 左手の技、OFF_ATTACK = 左手の攻撃（狙っている相手、居なければ空振り）
 */
public record DualWieldPacket(int action, int target) {

    public static final int MAIN_SKILL = 0, OFF_SKILL = 1, OFF_ATTACK = 2;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(target);
    }

    public static DualWieldPacket decode(FriendlyByteBuf buf) {
        return new DualWieldPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(DualWieldPacket p, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !player.isAlive() || player.isSpectator() || !DualWield.isDual(player)) return;
        switch (p.action) {
            case MAIN_SKILL -> skill(player, player.getMainHandItem());
            case OFF_SKILL -> {
                ItemStack off = player.getOffhandItem();
                DualWield.asMainHand(player, () -> skill(player, off));
                player.swing(InteractionHand.OFF_HAND, true);
            }
            case OFF_ATTACK -> {
                ItemStack off = player.getOffhandItem();
                Entity target = p.target >= 0 ? player.level().getEntity(p.target) : null;
                DualWield.asMainHand(player, () -> {
                    off.onEntitySwing(player);
                    if (target != null && target.isAlive() && target != player && player.distanceToSqr(target) <= 6.0 * 6.0) {
                        player.attack(target);
                    }
                });
                player.swing(InteractionHand.OFF_HAND, true);
            }
            default -> { }
        }
    }

    /** 技（use）を利き手で（クールタイム中なら何もしない） */
    private static void skill(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !DualWield.hasSkill(stack) || player.getCooldowns().isOnCooldown(stack.getItem())) return;
        stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
    }
}
