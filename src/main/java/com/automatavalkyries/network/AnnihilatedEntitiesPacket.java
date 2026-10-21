package com.automatavalkyries.network;

import com.automatavalkyries.client.ClientAnnihilation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 超絶抹殺の棒で抹消したエンティティをクライアントへ知らせる。
 * <p>
 * バニラの消去通知（ClientboundRemoveEntitiesPacket）だけでは、Witherzilla の処刑竜のように
 * 自分の「生命維持フラグ」で消去を拒否する実装がクライアント側に残り、再読み込みまで幽霊として見え続ける。
 * このパケットでクライアントにも「抹消済み」を記録させ、残ったものは強制的に取り除かせる。
 * </p>
 */
public record AnnihilatedEntitiesPacket(List<UUID> uuids, List<Integer> ids, boolean madnessSweep) {

    public AnnihilatedEntitiesPacket(List<UUID> uuids, List<Integer> ids) {
        this(uuids, ids, false);
    }

    public static void encode(AnnihilatedEntitiesPacket packet, FriendlyByteBuf buf) {
        buf.writeCollection(packet.uuids, FriendlyByteBuf::writeUUID);
        buf.writeCollection(packet.ids, FriendlyByteBuf::writeVarInt);
        buf.writeBoolean(packet.madnessSweep);
    }

    public static AnnihilatedEntitiesPacket decode(FriendlyByteBuf buf) {
        List<UUID> uuids = buf.readList(FriendlyByteBuf::readUUID);
        List<Integer> ids = buf.readList(FriendlyByteBuf::readVarInt);
        return new AnnihilatedEntitiesPacket(uuids, ids, buf.isReadable() && buf.readBoolean());
    }

    public static void handle(AnnihilatedEntitiesPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (packet.madnessSweep) com.automatavalkyries.annihilate.MadnessAnnihilation.sweepClient();
            if (!packet.uuids.isEmpty() || !packet.ids.isEmpty()) ClientAnnihilation.purge(packet.uuids, packet.ids);
        });
    }
}
