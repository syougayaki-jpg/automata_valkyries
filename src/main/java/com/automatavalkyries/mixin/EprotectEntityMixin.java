package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 不正消去のブロック。
 * {@code remove()} / {@code discard()} / {@code kill()} はすべて {@code setRemoved()} に行き着くため、
 * ここを塞げば他 MOD が {@code setRemoved()} を直接呼んだ場合も含めて拒否できる。
 */
@Mixin(Entity.class)
public abstract class EprotectEntityMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
            }
        }
    }

    /** IMDS 等による -Infinity / 奈落落としテレポートをブロック */
    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardTeleport(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y) || Double.isNaN(z) || Double.isInfinite(z) || y < -1000.0D) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardSetPos(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof IProviderWithSpeech provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y) || Double.isNaN(z) || Double.isInfinite(z) || y < -1000.0D) {
                    ci.cancel();
                }
            }
        }
    }
}
