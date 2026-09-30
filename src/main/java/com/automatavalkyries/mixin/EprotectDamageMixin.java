package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.EprotectImdsCounter;
import com.automatavalkyries.protect.IProviderWithSpeech;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code LivingEntity.hurt()} の入口で即死系ダメージを拒否・サニタイズする */
@Mixin(LivingEntity.class)
public abstract class EprotectDamageMixin {

    /** /kill（LivingEntity.kill() → genericKill）を拒否し、それ以外は hurt 実行中として記録する */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$beginHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect == null) return;
        if (protect.isProtectionActive() && source.is(DamageTypes.GENERIC_KILL)) {
            cir.setReturnValue(false);
            return;
        }
        protect.beginHurt();
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void automata_valkyries$endHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null) protect.endHurt();
    }

    /** まな板剣などの NaN / 無限大 / 桁外れな値を上限値へ置き換える */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float automata_valkyries$sanitizeDamage(float amount) {
        Eprotect protect = automata_valkyries$getProtect();
        return protect != null ? protect.sanitizeIncomingDamage(amount) : amount;
    }

    /**
     * hurt() を経ない HP 0 化・NaN 化をその場で弾く。
     * クライアントに HP 0 が同期されると死亡演出（deathTime）が始まって戻らなくなるため、修復より前に止める。
     */
    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float automata_valkyries$guardHealth(float health) {
        Eprotect protect = automata_valkyries$getProtect();
        return protect != null ? protect.filterHealth(health) : health;
    }

    /**
     * IMDS 等が die() を直接呼んでも、正規の被弾（canDie == true）でない限り拒否する。
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDie(DamageSource source, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && !protect.canDie()) {
            ci.cancel();
            LivingEntity self = (LivingEntity) (Object) this;
            self.setHealth(self.getMaxHealth());
            self.deathTime = 0;
            EprotectImdsCounter.counter(self);
        }
    }

    /**
     * 死亡Tick進行（tickDeath）で死亡アニメーション後に消去されるのを防止する。
     */
    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardTickDeath(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.isProtectionActive()) {
            ci.cancel();
            LivingEntity self = (LivingEntity) (Object) this;
            self.deathTime = 0;
            self.setHealth(self.getMaxHealth());
            EprotectImdsCounter.counter(self);
        }
    }

    private Eprotect automata_valkyries$getProtect() {
        return (Object) this instanceof IProviderWithSpeech provider ? provider.getEprotect() : null;
    }
}
