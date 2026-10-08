package com.automatavalkyries.network;

import com.automatavalkyries.client.ValkyrieStorageScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 兵姫の収納テレポーターの一覧（サーバー → クライアント）: 近くの自分の兵姫（しまえる）と、しまった兵姫（出せる）。
 * message は直前の操作の結果の翻訳キー（空なら無し）
 */
public record StorageListPacket(BlockPos pos, List<Row> rows, int stored, int capacity, String message) {

    /** 一覧の 1 行（stored = しまってある） */
    public record Row(UUID id, String name, boolean stored, float health, float maxHealth, boolean shutdown, boolean omega) {}

    public static void encode(StorageListPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.rows.size());
        for (Row r : packet.rows) {
            buf.writeUUID(r.id);
            buf.writeUtf(r.name, 128);
            buf.writeBoolean(r.stored);
            buf.writeFloat(r.health);
            buf.writeFloat(r.maxHealth);
            buf.writeBoolean(r.shutdown);
            buf.writeBoolean(r.omega);
        }
        buf.writeVarInt(packet.stored);
        buf.writeVarInt(packet.capacity);
        buf.writeUtf(packet.message, 256);
    }

    public static StorageListPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = Math.min(buf.readVarInt(), 256);
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUUID(), buf.readUtf(128), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean()));
        }
        return new StorageListPacket(pos, rows, buf.readVarInt(), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(StorageListPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ValkyrieStorageScreen.show(packet));
    }
}
