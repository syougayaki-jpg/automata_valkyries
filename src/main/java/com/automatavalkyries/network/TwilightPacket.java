package com.automatavalkyries.network;

import com.automatavalkyries.client.ClientTwilight;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 近くのプレイヤーの画面を染める（ticks の間）。scheme 0 = 黄昏（終の黄昏『ラグナロク』）、1 = 星空（星葬冠『ルミナス・レクイエム』） */
public record TwilightPacket(int ticks, int scheme) {

    public static final int TWILIGHT = 0, STARRY = 1;

    public TwilightPacket(int ticks) {
        this(ticks, TWILIGHT);
    }

    public static void encode(TwilightPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.ticks);
        buf.writeVarInt(packet.scheme);
    }

    public static TwilightPacket decode(FriendlyByteBuf buf) {
        return new TwilightPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TwilightPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTwilight.start(packet.ticks, packet.scheme));
    }
}
