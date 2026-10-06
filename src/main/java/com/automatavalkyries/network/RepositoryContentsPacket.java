package com.automatavalkyries.network;

import com.automatavalkyries.chrono.VoidRepositoryBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** サーバー → クライアント: 開いているヴォイド・リポジトリの中身（見本と数）。中身が変わるたびに送る */
public record RepositoryContentsPacket(int containerId, List<ItemStack> keys, long[] counts) {

    public static RepositoryContentsPacket of(int containerId, List<VoidRepositoryBlockEntity.Entry> entries) {
        List<ItemStack> keys = new ArrayList<>(entries.size());
        long[] counts = new long[entries.size()];
        for (int i = 0; i < entries.size(); i++) { // 空きは空の物と 0
            var e = entries.get(i);
            keys.add(e == null ? ItemStack.EMPTY : e.key);
            counts[i] = e == null ? 0 : e.count;
        }
        return new RepositoryContentsPacket(containerId, keys, counts);
    }

    public static void encode(RepositoryContentsPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeVarInt(packet.keys.size());
        for (int i = 0; i < packet.keys.size(); i++) {
            buf.writeItem(packet.keys.get(i));
            buf.writeVarLong(packet.counts[i]);
        }
    }

    public static RepositoryContentsPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int n = buf.readVarInt();
        List<ItemStack> keys = new ArrayList<>(n);
        long[] counts = new long[n];
        for (int i = 0; i < n; i++) {
            keys.add(buf.readItem());
            counts[i] = buf.readVarLong();
        }
        return new RepositoryContentsPacket(id, keys, counts);
    }

    public static void handle(RepositoryContentsPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.automatavalkyries.client.ClientChrono.applyRepository(packet));
        context.get().setPacketHandled(true);
    }
}
