package com.automatavalkyries.mixin.client;

import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.annihilate.TerminusPlayerExecutor;
import com.automatavalkyries.client.ClientAnnihilation;
import com.automatavalkyries.protect.DirectRead;
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
 * 死亡画面（{@link DeathScreen}）の初期化・Tick・描画を Java エージェント無しでも保護する（{@code priority = 1}）。
 * <p>
 * ECA 等の Mixin（既定 {@code priority = 1000}）は {@code DeathScreen.init} や {@code DeathScreen.tick} の先頭で
 * クライアント側の無敵状態・復活予約を見て初期化を打ち切ったり {@code setScreen(null)} で閉じたりする。
 * {@code priority = 1} でそれより先に {@link ClientAnnihilation#sanitizeDeadLocalPlayer} を走らせることで、
 * 後続の他 MOD Mixin が見た時点で無敵・保護状態がすべて解除済みになるようにする。
 * </p>
 */
@Mixin(value = DeathScreen.class, priority = 1)
public abstract class DeathScreenGuardMixin extends Screen {

    @Shadow private Component deathScore;

    protected DeathScreenGuardMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void automata_valkyries$preSanitizeOnInit(CallbackInfo ci) {
        if (this.minecraft != null && this.minecraft.player != null
                && (!DirectRead.hasHealth(this.minecraft.player)
                || IsolationDamageEngine.isIsolatedDead(this.minecraft.player)
                || IsolationDamageEngine.shouldSuppressPlayerInventoryDefense(this.minecraft.player)
                || TerminusPlayerExecutor.isTerminusDeadOrDying(this.minecraft.player))) {
            ClientAnnihilation.sanitizeDeadLocalPlayer(this.minecraft.player);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void automata_valkyries$preSanitizeOnTick(CallbackInfo ci) {
        if (this.minecraft != null && this.minecraft.player != null
                && (!DirectRead.hasHealth(this.minecraft.player)
                || IsolationDamageEngine.isIsolatedDead(this.minecraft.player)
                || IsolationDamageEngine.shouldSuppressPlayerInventoryDefense(this.minecraft.player)
                || TerminusPlayerExecutor.isTerminusDeadOrDying(this.minecraft.player))) {
            ClientAnnihilation.sanitizeDeadLocalPlayer(this.minecraft.player);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardUninitialized(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (deathScore != null) return;
        rebuildWidgets();
        if (deathScore == null) ci.cancel();
    }
}
