package com.automatavalkyries.network;

import com.automatavalkyries.client.ElenorBossBar;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** エレノアが、そのプレイヤーに使っているペナルティアタックのティア（0〜7）を知らせる。ボスバーの中に出す（docs/SPEC_ELENOR.md §2.3.2） */
public record ElenorTierPacket(int tier, int entityTier) {

    public static void encode(ElenorTierPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.tier);
        buf.writeVarInt(packet.entityTier);
    }

    public static ElenorTierPacket decode(FriendlyByteBuf buf) {
        int tier = buf.readVarInt();
        return new ElenorTierPacket(tier, buf.readVarInt());
    }

    public static void handle(ElenorTierPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ElenorBossBar.setTier(packet.tier, packet.entityTier));
    }
}
