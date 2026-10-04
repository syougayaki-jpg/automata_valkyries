package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * オートマタヴァルキリー protect の IMDS-Reborn 対策（2026-10-05）。IMDS の {@code remov} はフィールド直書きと Tick 一覧・追跡からの除外で
 * 消すので、{@code setRemoved} の拒否をすり抜けて、オメガコアの兵姫が消える → 復元 → また消される、を繰り返していた。
 * オメガコアの兵姫（完全な保護）だけ IMDS の入口で止める。オメガコア無しの兵姫は今まで通り受ける（機能停止 → 消去 → 救済通信機）
 */
public final class ValkyrieImdsMixins {

    private ValkyrieImdsMixins() {}

    @Pseudo
    @Mixin(targets = "net.mcreator.imds_reborn.procedures.ImdsKillProcedure", remap = false)
    public abstract static class Procedure {

        @Inject(method = "execute_all(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;Z)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieExecuteAll(Entity victim, Player player, boolean flag, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(victim, "imds:execute_all")) ci.cancel();
        }

        @Inject(method = "execute_cmd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieExecuteCmd(Entity victim, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(victim, "imds:execute_cmd")) ci.cancel();
        }

        @Inject(method = "execute_ench(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieExecuteEnch(Entity victim, LivingEntity attacker, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(victim, "imds:execute_ench")) ci.cancel();
        }

        @Inject(method = "execute_imds(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieExecuteImds(Entity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "imds:execute_imds")) ci.cancel();
        }

        @Inject(method = "cleanAI(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieCleanAI(Entity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "imds:cleanAI")) ci.cancel();
        }

        @Inject(method = "remov(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieRemov(Entity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "imds:remov")) ci.cancel();
        }
    }

    @Pseudo
    @Mixin(targets = "net.mcreator.imds_reborn.ImdsKillManager", remap = false)
    public abstract static class Manager {

        @Inject(method = "markForDead(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieMarkForDead(LivingEntity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "imds:markForDead")) ci.cancel();
        }

        @Inject(method = "forceRemove(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieForceRemove(LivingEntity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "imds:forceRemove")) ci.cancel();
        }

        @Inject(method = "forceNoAi(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieForceNoAi(LivingEntity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "imds:forceNoAi")) ci.cancel();
        }
    }
}
