package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * IMDS-Reborn の強制死亡・強制消去・AI 停止の登録。
 * 登録されると IMDS の ASM が getHealth / isAlive / isRemoved 等を全呼び出し箇所で書き換えるため、登録自体を拒否する。
 */
@Pseudo
@Mixin(targets = "net.mcreator.imds_reborn.ImdsKillManager", remap = false)
public abstract class ImdsKillManagerMixin {

    @Inject(method = "markForDead(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardMarkForDead(LivingEntity entity, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(entity, "imds:markForDead")) ci.cancel();
    }

    @Inject(method = "forceRemove(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardForceRemove(LivingEntity entity, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(entity, "imds:forceRemove")) ci.cancel();
    }

    @Inject(method = "forceNoAi(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardForceNoAi(LivingEntity entity, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(entity, "imds:forceNoAi")) ci.cancel();
    }
}
