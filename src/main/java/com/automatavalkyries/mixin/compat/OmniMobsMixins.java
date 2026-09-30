package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Omni-Mobs の防壁解除用 Mixin。
 * 抹消対象として指定されたエンティティについて、Omni-Mobs の checkDespawnImmunity を無効化し、
 * 区画・一覧・追跡からの正規抹消を許可する。
 */
public final class OmniMobsMixins {

    private OmniMobsMixins() {}

    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.coremod.CoreModHooks", remap = false)
    public abstract static class Hooks {
        @Inject(method = "checkDespawnImmunity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardDespawnImmunity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (AnnihilationExecutionAgent.isAnnihilated(entity)) {
                cir.setReturnValue(false);
            }
        }
    }
}
