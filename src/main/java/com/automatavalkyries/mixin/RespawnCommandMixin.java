package com.automatavalkyries.mixin;

import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.RespawnSanitizer;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * リスポーンの要求の受け口。
 * <ol>
 *   <li>死亡が確定しているのに他 MOD が HP を戻していると vanilla は要求を無視するので、
 *       {@link RespawnSanitizer#allowRespawnOfDeadPlayer} で HP を 0 に戻してから通す（準汎用。2026-10-03）</li>
 *   <li>万が一リスポーン処理（{@code PERFORM_RESPAWN}）の完了後も古い死亡個体のまま・またはリスポーン不能状態になっていた場合は、
 *       安全弁（{@link IsolationDamageEngine#triggerSafetyValveAndAdvanceTier}）を発動してこちら側の干渉を辞め、次のティアへ移行する。</li>
 * </ol>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RespawnCommandMixin {
    @Shadow public ServerPlayer player;
    @Shadow @Final private MinecraftServer server;

    @Unique
    private ServerPlayer automata_valkyries$respawnRequestPlayer;

    @Inject(method = "handleClientCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V", shift = At.Shift.AFTER))
    private void automata_valkyries$allowDeadPlayerRespawn(ServerboundClientCommandPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
            automata_valkyries$respawnRequestPlayer = player;
            if (!IsolationDamageEngine.isSafetyValveBypassed(player, 6)) {
                RespawnSanitizer.allowRespawnOfDeadPlayer(player);
            }
        } else {
            automata_valkyries$respawnRequestPlayer = null;
        }
    }

    @Inject(method = "handleClientCommand", at = @At("RETURN"))
    private void automata_valkyries$checkRespawnSafetyValve(ServerboundClientCommandPacket packet, CallbackInfo ci) {
        ServerPlayer requested = automata_valkyries$respawnRequestPlayer;
        automata_valkyries$respawnRequestPlayer = null;
        if (packet.getAction() != ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
                || requested == null || requested.wonGame) {
            return;
        }
        // PERFORM_RESPAWN 実行後に新しい生存 ServerPlayer へ切り替わっていない（リスポーンが拒否・失敗した）場合、
        // 安全弁を発動してこちら側の干渉をすべて停止し、死亡画面を閉じて次のティアへ移行する
        boolean respawnSucceeded = this.player != null
                && this.player != requested
                && !this.player.isDeadOrDying()
                && DirectRead.hasHealth(this.player);
        if (!respawnSucceeded) {
            ServerPlayer target = this.player != null ? this.player : requested;
            IsolationDamageEngine.triggerSafetyValveAndAdvanceTier(target, "PERFORM_RESPAWN did not complete respawn");
        }
    }
}
