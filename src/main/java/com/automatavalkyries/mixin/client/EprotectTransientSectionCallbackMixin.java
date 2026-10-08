package com.automatavalkyries.mixin.client;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * クライアント側のエンティティセクション（TransientEntitySectionManager）からの不正消去を HEAD で阻止する。
 * 他 MOD のパケット（SuperSteve の RmCEntityL 等）やクライアント側での強制消去でも、保護対象なら消去させない。
 */
@Mixin(targets = "net.minecraft.world.level.entity.TransientEntitySectionManager$Callback")
public abstract class EprotectTransientSectionCallbackMixin {

    @Shadow @Final private EntityAccess entity;

    @Inject(method = "onRemove", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardTransientOnRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (this.entity instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
            }
        }
    }
}
