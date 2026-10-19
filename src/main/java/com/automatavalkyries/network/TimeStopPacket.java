package com.automatavalkyries.network;

import com.automatavalkyries.client.ClientTimeStop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** タイムルーラーの時間停止を画面に伝える（2026-10-19）。ticks = 残り（0 で動き出す）、exempt = このプレイヤーは動ける */
public record TimeStopPacket(int ticks, boolean exempt) {

    public static void encode(TimeStopPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.ticks);
        buf.writeBoolean(packet.exempt);
    }

    public static TimeStopPacket decode(FriendlyByteBuf buf) {
        return new TimeStopPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(TimeStopPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTimeStop.set(packet.ticks, packet.exempt));
    }
}
