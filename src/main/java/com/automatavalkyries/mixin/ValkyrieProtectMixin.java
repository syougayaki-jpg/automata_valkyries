package com.automatavalkyries.mixin;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * オートマタヴァルキリー protect（{@link ValkyrieProtect}）: 兵姫のクラスでは上書きできない {@code final} の消去（setRemoved）を拒否する。
 * remove（discard もこれを通る）は兵姫のクラス自身が上書きして断る。チャンクの読み込み解除・次元移動は通す
 */
@Mixin(Entity.class)
public abstract class ValkyrieProtectMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$valkyrieSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof ValkyrieEntity v && !ValkyrieProtect.allowRemoval(v, reason)) ci.cancel();
    }
}
