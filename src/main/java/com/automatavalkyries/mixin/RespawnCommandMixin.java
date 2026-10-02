package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.RespawnSanitizer;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * リスポーンの要求の受け口。死亡が確定しているのに他 MOD が HP を戻していると vanilla は要求を無視するので、
 * {@link RespawnSanitizer#allowRespawnOfDeadPlayer} で HP を 0 に戻してから通す（準汎用。2026-10-03）
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RespawnCommandMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleClientCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V", shift = At.Shift.AFTER))
    private void automata_valkyries$allowDeadPlayerRespawn(ServerboundClientCommandPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
            RespawnSanitizer.allowRespawnOfDeadPlayer(player);
        }
    }
}
