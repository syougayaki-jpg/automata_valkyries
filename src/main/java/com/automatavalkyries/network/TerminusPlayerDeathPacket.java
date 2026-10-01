package com.automatavalkyries.network;

import com.automatavalkyries.client.ClientAnnihilation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 終焉執行を受けたプレイヤーに対し、クライアント側で死亡・消滅フラグを安全に確定するパケット。
 * <p>
 * プレイヤーエンティティをワールドから強制消去（removeEntity / forceRemove）することなく、
 * クライアント側で No-Sugar 等の防御フック（DeathScreen キャンセル等）を解除し、
 * 正規の死亡画面（DeathScreen）を確実に表示して安全なリスポーンを可能にする。
 * </p>
 */
public record TerminusPlayerDeathPacket(UUID playerUuid, int playerId) {

    public static void encode(TerminusPlayerDeathPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerUuid);
        buf.writeVarInt(packet.playerId);
    }

    public static TerminusPlayerDeathPacket decode(FriendlyByteBuf buf) {
        return new TerminusPlayerDeathPacket(buf.readUUID(), buf.readVarInt());
    }

    public static void handle(TerminusPlayerDeathPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAnnihilation.handlePlayerDeath(packet.playerUuid));
    }
}
