package com.automatavalkyries.network;

import com.automatavalkyries.item.SteamThrusterItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** クライアント → サーバー: 蒸気スラスターを噴いた（サーバーは蒸気を減らし、落下距離を消し、煙と音を出す） */
public record SteamThrustPacket() {

    public static void encode(SteamThrustPacket packet, FriendlyByteBuf buf) {}

    public static SteamThrustPacket decode(FriendlyByteBuf buf) {
        return new SteamThrustPacket();
    }

    public static void handle(SteamThrustPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) SteamThrusterItem.consume(player);
        context.get().setPacketHandled(true);
    }
}
