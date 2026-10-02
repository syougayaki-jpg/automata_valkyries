package com.automatavalkyries.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 死亡画面の初期化が飛ばされたまま描かれて落ちるのを防ぐ（ECA の DeathScreenMixin との食い違い）。
 * <p>
 * ECA は「無敵・復活中」と見なしたとき死亡画面の {@code init} を丸ごと打ち切る（得点の文字・リスポーンのボタンが作られない）。
 * その後、描く時点では条件が外れていると描画は打ち切られず、vanilla が未初期化の {@code deathScore}（null）を描いて落ちた（2026-10-02）。
 * 描く前に未初期化なら作り直し、それでも作れない（ECA がまだ打ち切る）ならその回の描画だけ飛ばす。
 * </p>
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenGuardMixin extends Screen {

    @Shadow private Component deathScore;

    protected DeathScreenGuardMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardUninitialized(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (deathScore != null) return;
        rebuildWidgets();
        if (deathScore == null) ci.cancel();
    }
}
