package com.automatavalkyries.network;

import com.automatavalkyries.machine.ValkyrieStorageBlock;
import com.automatavalkyries.machine.ValkyrieStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 兵姫の収納テレポーターで選んだ兵姫をしまう・出す（クライアント → サーバー）。テレポーターのそばにいるときだけ受け付け、更新した一覧を返す */
public record StorageActionPacket(BlockPos pos, UUID id, boolean store) {

    public static void encode(StorageActionPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeUUID(packet.id);
        buf.writeBoolean(packet.store);
    }

    public static StorageActionPacket decode(FriendlyByteBuf buf) {
        return new StorageActionPacket(buf.readBlockPos(), buf.readUUID(), buf.readBoolean());
    }

    public static void handle(StorageActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !player.level().isLoaded(packet.pos)
                || !(player.level().getBlockEntity(packet.pos) instanceof ValkyrieStorageBlockEntity storage) || !storage.inReach(player)) return;
        String result = packet.store ? storage.store(player, packet.id) : storage.release(player, packet.id);
        ValkyrieStorageBlock.sendList(player, storage, "message.automata_valkyries.valkyrie_storage." + result);
    }
}
