package com.automatavalkyries.network;

import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 救済通信機の一覧（サーバー → クライアント）。届いたら画面を開く・更新する。message は直前の操作の結果（翻訳キー、空なら無し） */
public record RescueListPacket(List<ValkyrieProtect.Summary> entries, int registered, int max, String message) {

    public static void encode(RescueListPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entries.size());
        for (ValkyrieProtect.Summary s : packet.entries) {
            buf.writeUUID(s.id());
            buf.writeUtf(s.name(), 64);
            buf.writeVarInt(s.state());
            buf.writeUtf(s.dimension(), 128);
            buf.writeBlockPos(s.pos());
            buf.writeBoolean(s.shutdown());
            buf.writeBoolean(s.omega());
            buf.writeFloat(s.health());
            buf.writeBoolean(s.owned());
        }
        buf.writeVarInt(packet.registered);
        buf.writeVarInt(packet.max);
        buf.writeUtf(packet.message, 128);
    }

    public static RescueListPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        List<ValkyrieProtect.Summary> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new ValkyrieProtect.Summary(buf.readUUID(), buf.readUtf(64), buf.readVarInt(), buf.readUtf(128), buf.readBlockPos(),
                    buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readBoolean()));
        }
        return new RescueListPacket(list, buf.readVarInt(), buf.readVarInt(), buf.readUtf(128));
    }

    public static void handle(RescueListPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.automatavalkyries.client.RescueCommunicatorScreen.show(packet));
    }
}
