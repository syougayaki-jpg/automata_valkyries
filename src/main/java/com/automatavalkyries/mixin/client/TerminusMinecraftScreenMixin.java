package com.automatavalkyries.mixin.client;

import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.annihilate.TerminusPlayerExecutor;
import com.automatavalkyries.client.ClientAnnihilation;
import com.automatavalkyries.protect.AgentPlayerProtection;
import com.automatavalkyries.protect.DirectRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link Minecraft#setScreen(Screen)} の先頭（{@code priority = 1}）で、死亡確定した自プレイヤーの
 * 死亡画面表示を保護する（Java エージェント不要）。
 * <ol>
 *   <li>{@code setScreen(DeathScreen)} が呼ばれた際、他 MOD の Mixin（IMDS の {@code MinecraftMixin.imds_deathscreen} 等、
 *       既定 {@code priority = 1000}）がインベントリや無敵フラグを検査してキャンセルするより前に
 *       {@link ClientAnnihilation#sanitizeDeadLocalPlayer} を実行して防御アイテム・無敵フラグを一掃する</li>
 *   <li>自プレイヤーが死亡中（{@code !DirectRead.hasHealth(player)}）に他 MOD が {@code setScreen(null)} で
 *       {@link DeathScreen} を強制的に閉じようとした場合、それを阻止する</li>
 * </ol>
 */
@Mixin(value = Minecraft.class, priority = 1)
public abstract class TerminusMinecraftScreenMixin {

    @Shadow @Nullable public LocalPlayer player;
    @Shadow @Nullable public Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardTerminusDeathScreen(@Nullable Screen guiScreen, CallbackInfo ci) {
        LocalPlayer localPlayer = this.player;
        if (localPlayer == null || AgentPlayerProtection.isProtected(localPlayer)) return;

        boolean isDead = !DirectRead.hasHealth(localPlayer)
                || IsolationDamageEngine.isIsolatedDead(localPlayer)
                || TerminusPlayerExecutor.isTerminusDeadOrDying(localPlayer);
        if (guiScreen instanceof DeathScreen) {
            if (isDead || IsolationDamageEngine.shouldSuppressPlayerInventoryDefense(localPlayer)) {
                ClientAnnihilation.sanitizeDeadLocalPlayer(localPlayer);
            }
            return;
        }
        if (!isDead) return;

        if (guiScreen == null && this.screen instanceof DeathScreen) {
            ClientAnnihilation.sanitizeDeadLocalPlayer(localPlayer);
            ci.cancel();
        }
    }
}
