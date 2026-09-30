package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * IMDS-Reborn のキル処理本体。die() の直接呼び出し・強制死亡フラグ・AI 全消去に加え、
 * {@code remov} はフィールド直書き（levelCallback = NULL、座標 -Infinity、isAddedToWorld = false）と
 * Tick 一覧・追跡からの除外を行うため、バニラ側の Mixin では防げない。入口で止める。
 */
@Pseudo
@Mixin(targets = "net.mcreator.imds_reborn.procedures.ImdsKillProcedure", remap = false)
public abstract class ImdsKillProcedureMixin {

    @Inject(method = "execute_all(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;Z)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardExecuteAll(Entity victim, Player source, boolean isDel, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(victim, "imds:executeAll")) ci.cancel();
    }

    @Inject(method = "execute_cmd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardExecuteCmd(Entity victim, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(victim, "imds:executeCmd")) ci.cancel();
    }

    @Inject(method = "execute_ench(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardExecuteEnch(Entity victim, LivingEntity source, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(victim, "imds:executeEnch")) ci.cancel();
    }

    @Inject(method = "execute_imds(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardExecuteImds(Entity entity, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(entity, "imds:executeImds")) ci.cancel();
    }

    @Inject(method = "cleanAI(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardCleanAI(Entity entity, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(entity, "imds:cleanAI")) ci.cancel();
    }

    @Inject(method = "remov(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void automata_valkyries$guardRemov(Entity entity, CallbackInfo ci) {
        if (EprotectCompat.shieldAttack(entity, "imds:remov")) ci.cancel();
    }
}
