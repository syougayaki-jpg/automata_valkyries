package com.automatavalkyries.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** サーバー → クライアント: 戦術級コードの演出を始める（kind 0 = GUNGNIR の赤黒い螺旋の光。client/TacticalFx） */
public record TacticalFxPacket(int kind, double x, double y, double z, float radius, int duration, long seed) {

    public static final int GUNGNIR = 0, CARPET = 1, IRIS = 2, ABYSS = 3;

    public TacticalFxPacket(int kind, double x, double y, double z, float radius, int duration) {
        this(kind, x, y, z, radius, duration, 0L);
    }
    /** GUNGNIR: 光が落ちてくる間と、照射の長さ（Tick。2026-10-07 ユーザー指定「演出が見やすいように 10 秒」） */
    public static final int GUNGNIR_DESCENT = 40, GUNGNIR_STRIKE = 200, GUNGNIR_ARMS = 8;

    /**
     * GUNGNIR の螺旋の腕 arm が、照射を始めてから strikeTicks のときに地面をえぐっている所（中心からの x, z）。
     * 外（半径いっぱい）から回りながら中心へ寄っていくので、えぐった跡は螺旋になる（サーバーの地形・クライアントの描画で共通）
     */
    public static double[] gungnirArm(int arm, float strikeTicks, double radius) {
        double k = Math.min(1.0, strikeTicks / GUNGNIR_STRIKE);
        double r = radius * Math.pow(1.0 - k, 0.8);
        double a = arm * Math.PI * 2 / GUNGNIR_ARMS + strikeTicks * 0.07;
        return new double[]{Math.cos(a) * r, Math.sin(a) * r};
    }

    public static void encode(TacticalFxPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.kind);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.radius);
        buf.writeVarInt(p.duration);
        buf.writeLong(p.seed);
    }

    public static TacticalFxPacket decode(FriendlyByteBuf buf) {
        return new TacticalFxPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt(), buf.readLong());
    }

    public static void handle(TacticalFxPacket p, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.automatavalkyries.client.TacticalFx.start(p));
        context.get().setPacketHandled(true);
    }
}
