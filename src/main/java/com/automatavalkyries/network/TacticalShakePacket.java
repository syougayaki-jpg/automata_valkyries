package com.automatavalkyries.network;

import com.automatavalkyries.client.TacticalShake;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 戦術級コードの着弾で画面を揺らす（power 0〜1。近いほど強い。2026-10-29 ユーザー指定「戦術コードをもっと派手に」） */
public record TacticalShakePacket(float power) {

    public static void encode(TacticalShakePacket packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.power);
    }

    public static TacticalShakePacket decode(FriendlyByteBuf buf) {
        return new TacticalShakePacket(buf.readFloat());
    }

    public static void handle(TacticalShakePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TacticalShake.add(packet.power));
    }
}
