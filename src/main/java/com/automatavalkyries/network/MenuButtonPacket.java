package com.automatavalkyries.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 画面のボタン（クライアント → サーバー）。バニラのボタンの通信（{@code ServerboundContainerButtonClickPacket}）は番号を 1 バイト（-128〜127）で送るので、
 * 大きな番号（ヴォイド・リポジトリの取り出し 1000 + 番号など）は専用サーバーで別の番号に化ける。こちらは番号をそのまま送る（2026-10-29。
 * 1 人で遊ぶ時は通信を書き出さないので、これまで気づかなかった）
 */
public record MenuButtonPacket(int containerId, int buttonId) {

    public static void encode(MenuButtonPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeVarInt(packet.buttonId);
    }

    public static MenuButtonPacket decode(FriendlyByteBuf buf) {
        return new MenuButtonPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MenuButtonPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || player.isSpectator() || player.containerMenu.containerId != packet.containerId) return;
        if (!player.containerMenu.stillValid(player)) return;
        if (player.containerMenu.clickMenuButton(player, packet.buttonId)) player.containerMenu.broadcastChanges();
    }
}
