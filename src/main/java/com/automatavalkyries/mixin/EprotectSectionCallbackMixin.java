package com.automatavalkyries.mixin;

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
 * ワールドのエンティティセクション（PersistentEntitySectionManager）からの不正物理消去を HEAD で阻止する。
 * Hyperlink の novelRemove や他 MOD の直接呼び出しでも、保護がアクティブなら一切消去させない。
 */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public abstract class EprotectSectionCallbackMixin {

    @Shadow @Final private EntityAccess entity;

    @Inject(method = "onRemove", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardOnRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (this.entity instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
            }
        }
    }
}
