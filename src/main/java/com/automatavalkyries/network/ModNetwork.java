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

        CHANNEL.messageBuilder(TwilightPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TwilightPacket::encode)
                .decoder(TwilightPacket::decode)
                .consumerMainThread(TwilightPacket::handle)
                .add();

        // 救済通信機（一覧と呼び出し）
        CHANNEL.messageBuilder(RescueListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RescueListPacket::encode)
                .decoder(RescueListPacket::decode)
                .consumerMainThread(RescueListPacket::handle)
                .add();
        CHANNEL.messageBuilder(RescueRecallPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RescueRecallPacket::encode)
                .decoder(RescueRecallPacket::decode)
                .consumerMainThread(RescueRecallPacket::handle)
                .add();
        CHANNEL.messageBuilder(RescueDiscardPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RescueDiscardPacket::encode)
                .decoder(RescueDiscardPacket::decode)
                .consumerMainThread(RescueDiscardPacket::handle)
                .add();

        // 戦術兵姫のメニューの操作（段階 2）
        CHANNEL.messageBuilder(ValkyrieCommandPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ValkyrieCommandPacket::encode)
                .decoder(ValkyrieCommandPacket::decode)
                .consumerMainThread(ValkyrieCommandPacket::handle)
                .add();
    }
}
