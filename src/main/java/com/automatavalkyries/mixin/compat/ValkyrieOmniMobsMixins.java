package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * オートマタヴァルキリー protect の Omni-Mobs 対策（2026-10-05）。Omni-Mobs の消去アイテム（{@code items.EntityRemover}）と
 * {@code util.EntityUtil} の強制消去はエンティティの管理から直接消す（setRemoved を通らない）ので、オメガコアの兵姫が消える → 見張りが戻す、になっていた。
 * オメガコアの兵姫だけ入口で止める（{@link OmniMobsMixins} と同じ所、判定だけ兵姫用）。
 * 消去アイテムはクライアントでも消す（左クリックは両側で動く）ので、クライアント側では兵姫を全部守る（{@code ValkyrieProtect.shieldForeignOrClient}）
 */
public final class ValkyrieOmniMobsMixins {

    private ValkyrieOmniMobsMixins() {}

    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.items.EntityRemover", remap = false)
    public abstract static class Remover {
        @Inject(method = "removeEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/nbt/CompoundTag;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$valkyrieRemoveEntity(Entity entity, Level level, CompoundTag settings, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeignOrClient(entity, "omnimobs:entityRemover")) ci.cancel();
        }
    }

    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.util.EntityUtil", remap = false)
    public abstract static class Util {
        @Inject(method = {
                "forceRemove(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
                "forceRemoveNoLeaveLevelCalls(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
                "forceSetRemoved(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieRemove(Entity entity, Entity.RemovalReason reason, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeignOrClient(entity, "omnimobs:forceRemove")) ci.cancel();
        }

        @Inject(method = "forceRemoveNoPacket(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;Z)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieRemoveNoPacket(Entity entity, Entity.RemovalReason reason, boolean flag, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeignOrClient(entity, "omnimobs:forceRemoveNoPacket")) ci.cancel();
        }

        @Inject(method = "runRemoveMethods(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieRemoveMethods(Entity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeignOrClient(entity, "omnimobs:runRemoveMethods")) ci.cancel();
        }

        @Inject(method = "forceSetPos(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieSetPos(Entity entity, Vec3 pos, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeignOrClient(entity, "omnimobs:forceSetPos")) ci.cancel();
        }

        @Inject(method = {
                "forceSetHealth(Lnet/minecraft/world/entity/LivingEntity;F)V",
                "setAllSyncedHealthData(Lnet/minecraft/world/entity/LivingEntity;F)V",
                "setAllHealthFields(Lnet/minecraft/world/entity/LivingEntity;F)V",
                "runSetHealthMethods(Lnet/minecraft/world/entity/LivingEntity;F)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieSetHealth(LivingEntity entity, float health, CallbackInfo ci) {
            if (health <= 0.0F && ValkyrieProtect.shieldForeignOrClient(entity, "omnimobs:setHealth")) ci.cancel();
        }
    }

    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.util.EntityRemovalUtil", remap = false)
    public abstract static class RemovalUtil {
        @Inject(method = "deleteEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Z)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieDelete(Entity entity, Level level, boolean flag, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeignOrClient(entity, "omnimobs:deleteEntity")) ci.cancel();
        }
    }
}
