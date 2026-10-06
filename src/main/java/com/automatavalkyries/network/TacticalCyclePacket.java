package com.automatavalkyries.network;

import com.automatavalkyries.item.TacPointerItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** クライアント → サーバー: 手の戦術ポインターのコードを次へ（「V」キー。追加拡張仕様書 §13.1） */
public record TacticalCyclePacket() {

    public static void encode(TacticalCyclePacket packet, FriendlyByteBuf buf) {}

    public static TacticalCyclePacket decode(FriendlyByteBuf buf) {
        return new TacticalCyclePacket();
    }

    public static void handle(TacticalCyclePacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            for (InteractionHand hand : InteractionHand.values()) {
                if (player.getItemInHand(hand).getItem() instanceof TacPointerItem) {
                    TacPointerItem.cycle(player, player.getItemInHand(hand));
                    break;
                }
            }
        }
        context.get().setPacketHandled(true);
    }
}
