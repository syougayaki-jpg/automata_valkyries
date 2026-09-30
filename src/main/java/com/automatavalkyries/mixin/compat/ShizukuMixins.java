package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Shizuku Blade（猫羽雫）。{@code UltimateEntityRemover} はエンティティ管理・区画・ボスバーからの直接削除と
 * (-999, -999, -999) への座標直書きを行い、{@code IsRemovedHelper} で「消去済み」の印を付け、HP をロックする。
 * 同梱の Java エージェント・ILaunchPluginService による書き換えは JVM 乗っ取り級なので対象外（Java の入口だけ止める）。
 */
public final class ShizukuMixins {

    private ShizukuMixins() {}

    @Pseudo
    @Mixin(targets = "com.buwan.shizuku.core.UltimateEntityRemover", remap = false)
    public abstract static class Remover {
        @Inject(method = "remove(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardRemove(Entity entity, Level level, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }
    }

    @Pseudo
    @Mixin(targets = "com.buwan.shizuku.util.EntityKillerHelper", remap = false)
    public abstract static class Killer {
        @Inject(method = {"killEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;)V",
                "AsynchronousAttack(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;)V",
                "AsynchronousRemove(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;)V",
                "tickDeath(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardAttack(Entity entity, Player player, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "kill(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardKill(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "remove(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardRemove(Entity entity, Player player, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }
    }

    /**
     * 必殺技「Ymsa（審判）」。範囲内の全モブを対象に、HP の消去・フィールドの直書き・物理削除・再出現の抑止を行い、
     * その後も毎 Tick 同じ UUID を消し続ける（ANNIHILATED / PENDING_WIPE）。対象選定（shouldKill）で外すのが根本対策。
     */
    @Pseudo
    @Mixin(targets = "com.buwan.shizuku.sa.Ymsa", remap = false)
    public abstract static class Ymsa {
        @Inject(method = "shouldKill(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$excludeTarget(LivingEntity entity, net.minecraft.server.level.ServerPlayer caster,
                                                            CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }

        @Inject(method = "annihilate(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardAnnihilate(LivingEntity entity, net.minecraft.server.level.ServerPlayer caster,
                                                              net.minecraft.server.level.ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }

        @Inject(method = "physicallyRemove(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerLevel;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardPhysicalRemove(LivingEntity entity, net.minecraft.server.level.ServerLevel level, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = {"wipeBossHealth(Lnet/minecraft/world/entity/LivingEntity;)V", "stripProtections(Lnet/minecraft/world/entity/LivingEntity;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardWipe(LivingEntity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "wipeEntityFields(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardWipeFields(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "suppressRespawn(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSuppress(LivingEntity entity, net.minecraft.server.level.ServerPlayer caster, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "isTickSuppressed(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$allowTick(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }

        @Inject(method = "shouldCancelJoin(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$allowJoin(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }
    }

    /** 「消去済み」の印。印が付くと Shizuku の Mixin が Tick と再登録を拒否する */
    @Pseudo
    @Mixin(targets = "com.buwan.shizuku.core.IsRemovedHelper", remap = false)
    public abstract static class RemovedMark {
        @Inject(method = "setIsRemoved(Lnet/minecraft/world/entity/Entity;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardMark(Entity entity, boolean removed, CallbackInfo ci) {
            if (removed && EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = {"isRemoved(Ljava/lang/Object;)Z", "containsEntity(Ljava/lang/Object;)Z"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardCheck(Object entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof Entity e && EprotectCompat.shouldShield(e)) cir.setReturnValue(false);
        }
    }

    /** HP のロック（同名クラスが core と util の 2 か所にある） */
    @Pseudo
    @Mixin(targets = {"com.buwan.shizuku.core.HealthLockManager", "com.buwan.shizuku.util.HealthLockManager"}, remap = false)
    public abstract static class HealthLock {
        @Inject(method = "lockEntityHealth", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardLock(LivingEntity entity, float health, float max, List<?> fields, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }
    }
}
