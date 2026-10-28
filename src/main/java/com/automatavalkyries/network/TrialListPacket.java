package com.automatavalkyries.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 回想のトライアルの一覧（サーバー → クライアント。2026-10-27 ユーザー指定「選択画面を作る」）。届いたら回想を選ぶ画面を開く。
 * トライアルの中身（名前・ボス・条件）はクライアントも {@code trial/Trial#ALL} を持つので、プレイヤーごとの記録（クリア・ランク・最速）だけ送る
 */
public record TrialListPacket(int selected, boolean[] cleared, int[] ranks, int[] times) {

    public static void encode(TrialListPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.selected);
        buf.writeVarInt(packet.cleared.length);
        for (int i = 0; i < packet.cleared.length; i++) {
            buf.writeBoolean(packet.cleared[i]);
            buf.writeVarInt(packet.ranks[i]);
            buf.writeVarInt(packet.times[i]);
        }
    }

    public static TrialListPacket decode(FriendlyByteBuf buf) {
        int selected = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 64);
        boolean[] cleared = new boolean[n];
        int[] ranks = new int[n], times = new int[n];
        for (int i = 0; i < n; i++) {
            cleared[i] = buf.readBoolean();
            ranks[i] = buf.readVarInt();
            times[i] = buf.readVarInt();
        }
        return new TrialListPacket(selected, cleared, ranks, times);
    }

    public static void handle(TrialListPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.automatavalkyries.client.TrialSelectScreen.show(packet));
    }
}
