package com.automatavalkyries.network;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** このMODのパケット通信路 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(AnnihilatedEntitiesPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnnihilatedEntitiesPacket::encode)
                .decoder(AnnihilatedEntitiesPacket::decode)
                .consumerMainThread(AnnihilatedEntitiesPacket::handle)
                .add();

        CHANNEL.messageBuilder(TerminusPlayerDeathPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TerminusPlayerDeathPacket::encode)
                .decoder(TerminusPlayerDeathPacket::decode)
                .consumerMainThread(TerminusPlayerDeathPacket::handle)
                .add();

        CHANNEL.messageBuilder(TerminusPlayerRespawnPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TerminusPlayerRespawnPacket::encode)
                .decoder(TerminusPlayerRespawnPacket::decode)
                .consumerMainThread(TerminusPlayerRespawnPacket::handle)
                .add();

        CHANNEL.messageBuilder(SteamThrustPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SteamThrustPacket::encode)
                .decoder(SteamThrustPacket::decode)
                .consumerMainThread(SteamThrustPacket::handle)
                .add();

        CHANNEL.messageBuilder(RepositoryContentsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RepositoryContentsPacket::encode)
                .decoder(RepositoryContentsPacket::decode)
                .consumerMainThread(RepositoryContentsPacket::handle)
                .add();
    }
}
