package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Trial Monolith の魂ダメージ。加算（addSoulDamage / setSoulDamage）はすべて setSoulDamageForce を通るので、そこで止める。
 * 魂ダメージを 0 に戻す書き込み（魂の瓶など）は通す。
 */
public final class TrialMonolithMixins {

    private TrialMonolithMixins() {}

    @Pseudo
    @Mixin(targets = "io.github.kosianodangoo.trialmonolith.common.helper.EntityHelper", remap = false)
    public abstract static class Helper {
        @Inject(method = "setSoulDamageForce(Lnet/minecraft/world/entity/Entity;F)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSetSoulDamage(Entity entity, float damage, CallbackInfo ci) {
            if (damage > 0.0F && EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = {"onSoulDeath(Lnet/minecraft/world/entity/Entity;)V", "onSoulRemove(Lnet/minecraft/world/entity/Entity;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSoulKill(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }
    }
}
