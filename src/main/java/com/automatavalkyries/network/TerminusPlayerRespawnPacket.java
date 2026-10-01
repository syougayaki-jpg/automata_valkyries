package com.automatavalkyries.network;

import com.automatavalkyries.client.ClientAnnihilation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * プレイヤーがリスポーンした際、クライアント側でも消滅リストや弱体化フラグを確実に解除するパケット。
 */
public record TerminusPlayerRespawnPacket(UUID playerUuid) {

    public static void encode(TerminusPlayerRespawnPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerUuid);
    }

    public static TerminusPlayerRespawnPacket decode(FriendlyByteBuf buf) {
        return new TerminusPlayerRespawnPacket(buf.readUUID());
    }

    public static void handle(TerminusPlayerRespawnPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAnnihilation.handlePlayerRespawn(packet.playerUuid));
    }
}
