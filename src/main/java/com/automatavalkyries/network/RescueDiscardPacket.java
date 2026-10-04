package com.automatavalkyries.network;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.item.RescueCommunicatorItem;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 救済通信機で選んだ兵姫のバックアップを破棄する（クライアント → サーバー。画面で警告して確かめた後）。通信機を手に持っているときだけ受け付け、更新した一覧を返す */
public record RescueDiscardPacket(UUID id) {

    public static void encode(RescueDiscardPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.id);
    }

    public static RescueDiscardPacket decode(FriendlyByteBuf buf) {
        return new RescueDiscardPacket(buf.readUUID());
    }

    public static void handle(RescueDiscardPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !RescueCommunicatorItem.holds(player)) return;
        String result = ValkyrieProtect.discardBackup(player, packet.id);
        player.level().playSound(null, player.blockPosition(), ValkyrieEntity.sound(result.equals("discarded") ? "shutdown" : "ui_beep"), SoundSource.PLAYERS, 0.8F, 1.0F);
        RescueCommunicatorItem.sendList(player, "message.automata_valkyries.rescue." + result);
    }
}
