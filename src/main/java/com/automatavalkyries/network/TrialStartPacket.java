package com.automatavalkyries.network;

import com.automatavalkyries.trial.TrialManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 回想を選ぶ画面で選んだトライアルに挑む（クライアント → サーバー）。追憶メモリを手に持っているときだけ受け付ける */
public record TrialStartPacket(int index) {

    public static void encode(TrialStartPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.index);
    }

    public static TrialStartPacket decode(FriendlyByteBuf buf) {
        return new TrialStartPacket(buf.readVarInt());
    }

    public static void handle(TrialStartPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) TrialManager.startFromScreen(player, packet.index);
    }
}
