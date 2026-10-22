package com.automatavalkyries.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 粒子パケットの個数に上限を付ける。バニラのプレイヤー攻撃は与ダメージ÷2 個のダメージ表示粒子を 1 パケットで送るので、
 * 即死系の武器（桁外れのダメージ）で殴ると数十億個になり、クライアントがその場で固まる（2026-10-22）。
 */
@Mixin(ClientPacketListener.class)
public abstract class ParticleFloodGuardMixin {
    private static final int MAX_PARTICLES_PER_PACKET = 256;

    @Redirect(method = "handleParticleEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;getCount()I"))
    private int automata_valkyries$capCount(ClientboundLevelParticlesPacket packet) {
        return Math.min(packet.getCount(), MAX_PARTICLES_PER_PACKET);
    }
}
