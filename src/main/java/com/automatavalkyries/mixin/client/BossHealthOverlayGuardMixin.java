package com.automatavalkyries.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 手元のボスバーを、サーバーが送ってきた状態に保つ（2026-10-25 ユーザー指定「ボスバーが消える」）。
 * サーバーからのボスバーの知らせ（{@link ClientboundBossEventPacket}）を処理した後の一覧を覚え、サーバーの「消した」知らせ無しに
 * 一覧から消されていたら、描く直前に戻す。The King Sword の {@code BossBarRemover.clearClientBossBars} は手元の一覧をリフレクションで空にする。
 * 誰が消したかは問わない（サーバーの知らせ・ワールドの切り替え（reset）以外で消えるのは正規ではない）
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayGuardMixin implements com.automatavalkyries.client.BossBarRestorer {

    @Shadow
    @Final
    Map<UUID, LerpingBossEvent> events;

    @Unique
    private final Map<UUID, LerpingBossEvent> automata_valkyries$confirmed = new LinkedHashMap<>();

    @Inject(method = "update", at = @At("TAIL"))
    private void automata_valkyries$rememberServerState(ClientboundBossEventPacket packet, CallbackInfo ci) {
        automata_valkyries$confirmed.clear();
        automata_valkyries$confirmed.putAll(events);
    }

    @Inject(method = "reset", at = @At("TAIL"))
    private void automata_valkyries$forget(CallbackInfo ci) {
        automata_valkyries$confirmed.clear();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void automata_valkyries$restoreBeforeRender(net.minecraft.client.gui.GuiGraphics graphics, CallbackInfo ci) {
        automata_valkyries$restoreRemoved();
    }

    @Override
    public void automata_valkyries$restoreRemoved() {
        if (automata_valkyries$confirmed.isEmpty()) return;
        for (Map.Entry<UUID, LerpingBossEvent> entry : automata_valkyries$confirmed.entrySet()) {
            if (!events.containsKey(entry.getKey())) events.put(entry.getKey(), entry.getValue());
        }
    }
}
