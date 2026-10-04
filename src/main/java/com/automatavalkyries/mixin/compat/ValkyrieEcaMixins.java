package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * オートマタヴァルキリー protect の ECA 対策（2026-10-05 ユーザー指定「全部やって」）。ECA の即死は die を無視されても、
 * サーバーの入れ物からの直接の削除（{@code removeFromServerContainers}）と、種類ごとの出現禁止（{@code SpawnBanHook}。
 * 検索表・区画・Tick 一覧・追跡から隠し、追加も拒否する）・消去中の隔離（{@code EntityRemovalQuarantine}）で消すので、
 * オメガコアの兵姫が消える → 戻そうとしても数秒拒否される → 戻る、を繰り返していた。
 * <ul>
 *   <li>オメガコアの兵姫: 消す・動かす・HP を書く入口で止める</li>
 *   <li>兵姫は全部: 出現禁止と隔離の追加拒否の対象外（オメガコア無しでも、救済通信機の呼び出しと見張りの復元が拒否されないように）</li>
 * </ul>
 */
public final class ValkyrieEcaMixins {

    private ValkyrieEcaMixins() {}

    @Pseudo
    @Mixin(targets = "net.eca.util.EntityUtil", remap = false)
    public abstract static class Util {
        /** 即死の後に出現を送り直す（クライアントから消されていても見えるように） */
        @Inject(method = "kill(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)V",
                at = @At("RETURN"), require = 0)
        private static void automata_valkyries$valkyrieResync(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
            if (entity instanceof ValkyrieEntity v && v.hasOmegaCore()) ValkyrieProtect.resync(v);
        }

        @Inject(method = "remove(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieRemove(Entity entity, Entity.RemovalReason reason, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "eca:remove")) ci.cancel();
        }

        @Inject(method = "removeFromServerContainers(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieContainers(ServerLevel level, Entity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "eca:removeFromServerContainers")) ci.cancel();
        }

        @Inject(method = {"cleanupAI(Lnet/minecraft/world/entity/Entity;)V", "cleanupBossBar(Lnet/minecraft/world/entity/Entity;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieCleanup(Entity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "eca:cleanup")) ci.cancel();
        }

        @Inject(method = "setHealth(Lnet/minecraft/world/entity/LivingEntity;F)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieSetHealth(LivingEntity entity, float health, CallbackInfoReturnable<Boolean> cir) {
            if (health <= 0.0F && ValkyrieProtect.shieldForeign(entity, "eca:setHealth")) cir.setReturnValue(false);
        }

        @Inject(method = "teleport(Lnet/minecraft/world/entity/Entity;DDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieTeleport(Entity entity, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
            if (ValkyrieProtect.shieldForeign(entity, "eca:teleport")) cir.setReturnValue(false);
        }
    }

    @Pseudo
    @Mixin(targets = "net.eca.util.spawn_ban.SpawnBanHook", remap = false)
    public abstract static class SpawnBan {
        @Inject(method = "shouldBlockSpawn(Ljava/lang/Object;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieExempt(Object entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof ValkyrieEntity) cir.setReturnValue(false);
        }

        @Inject(method = "shouldBlockSpawn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieExemptAdd(Level level, Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof ValkyrieEntity) cir.setReturnValue(false);
        }
    }

    @Pseudo
    @Mixin(targets = "net.eca.util.EntityRemovalQuarantine", remap = false)
    public abstract static class Quarantine {
        @Inject(method = "begin(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieBegin(ServerLevel level, Entity entity, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(entity, "eca:quarantine")) ci.cancel();
        }

        @Inject(method = "shouldBlockAdd(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieAllowAdd(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof ValkyrieEntity) cir.setReturnValue(false);
        }
    }
}
