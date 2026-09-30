package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.KillModCounters;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 不正消去のブロック。
 * 他 MOD による {@code remove()} / {@code discard()} / {@code setRemoved()} / {@code onRemovedFromWorld()}
 * の直接呼び出しを HEAD で完全拒絶する。
 */
@Mixin(value = Entity.class, priority = 100000)
public abstract class EprotectEntityMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
                if ((Object) this instanceof LivingEntity living) {
                    KillModCounters.counterAll(living);
                }
            }
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
                if ((Object) this instanceof LivingEntity living) {
                    KillModCounters.counterAll(living);
                }
            }
        }
    }

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDiscard(CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isGuardingRemoval()) {
                ci.cancel();
                if ((Object) this instanceof LivingEntity living) {
                    KillModCounters.counterAll(living);
                }
            }
        }
    }

    /**
     * IMDS や Hyperlink が直接 onRemovedFromWorld() を呼んでワールド・トラッキングから物理除外するのを防ぐ
     */
    @Inject(method = "onRemovedFromWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void automata_valkyries$guardOnRemovedFromWorld(CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            Entity self = (Entity) (Object) this;
            Entity.RemovalReason reason = self.getRemovalReason();
            if (protect != null && protect.isGuardingRemoval() && (reason == null || reason.shouldDestroy())) {
                ci.cancel();
                if (self instanceof LivingEntity living) {
                    KillModCounters.counterAll(living);
                }
            }
        }
    }

    /** IMDS 等による -Infinity / 奈落落としテレポートをブロック */
    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardTeleport(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (!Eprotect.isSafePosition((Entity) (Object) this, x, y, z)) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardSetPos(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (!Eprotect.isSafePosition((Entity) (Object) this, x, y, z)) {
                    ci.cancel();
                }
            }
        }
    }

    /**
     * setPos を経ない座標の直書き（IMDS の -Infinity テレポートは setPosRaw も直接呼ぶ）。
     * ここを通さないと区画・チャンク座標まで -Infinity 由来の値になり、保存時に壊れた座標で書き出される。
     */
    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardSetPosRaw(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive() && !Eprotect.isSafePosition((Entity) (Object) this, x, y, z)) {
                ci.cancel();
            }
        }
    }

    /**
     * Hyperlink の isNovelized() を保護対象に対して常に false にする（演出中も含めノベル化によるTick停止を絶対阻止）
     */
    @Inject(method = "isNovelized", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardIsNovelized(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IEprotected) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Hyperlink の setNovelized() を保護対象に対して完全に無視する
     */
    @Inject(method = "setNovelized", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardSetNovelized(CallbackInfo ci) {
        if ((Object) this instanceof IEprotected) {
            ci.cancel();
        }
    }

    /**
     * Hyperlink の novelRemove(Entity.RemovalReason) を保護対象に対して完全にブロックする
     */
    @Inject(method = "novelRemove", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardNovelRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
            }
        }
    }

    /**
     * Hyperlink の novelize(LivingEntity) を保護対象に対して完全にブロックする
     */
    @Inject(method = "novelize(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardNovelize(LivingEntity living, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected) {
            ci.cancel();
        }
    }
}
