package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.EprotectHyperlinkCounter;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hyperlink の DATA_NOVELIZED 注入を完全に遮断する Mixin。
 * 保護対象エンティティへの DATA_NOVELIZED 書き込みを拒絶し、読み出し時は常に false を返却する。
 */
@Mixin(SynchedEntityData.class)
public abstract class EprotectSynchedDataMixin {

    @Shadow @Final private Entity entity;

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", at = @At("HEAD"), cancellable = true)
    private <T> void automata_valkyries$guardSet(EntityDataAccessor<T> key, T value, boolean force, CallbackInfo ci) {
        if (this.entity instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (Boolean.TRUE.equals(value) && EprotectHyperlinkCounter.isNovelizedAccessor(key)) {
                    ci.cancel();
                    com.automatavalkyries.protect.attack.AttackLog.refused(this.entity,
                            com.automatavalkyries.protect.attack.AttackOp.NOVELIZE + ":data");
                }
            }
        }
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private <T> void automata_valkyries$guardGet(EntityDataAccessor<T> key, CallbackInfoReturnable<T> cir) {
        if (this.entity instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (EprotectHyperlinkCounter.isNovelizedAccessor(key)) {
                    //noinspection unchecked
                    cir.setReturnValue((T) Boolean.FALSE);
                }
            }
        }
    }
}
