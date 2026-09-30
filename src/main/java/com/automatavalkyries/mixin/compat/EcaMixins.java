package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Epic Core API（ECA）。他の即死 MOD（The Last Sword You Never Forgot 等）が使う基盤ライブラリ。
 * {@code EntityUtil.remove} は removalReason の直書き・遠方テレポート・AI とボスバーの消去・ワールドの一覧からの直接削除を行い、
 * さらに {@code EntityRemovalQuarantine} で同じ個体の再登録を拒否するため、eprotect の「消されたら戻す」が効かない。入口で止める。
 * 既定設定の経路だけが対象。「Radical Logic」（メモリ直書き・クラス再変換）は JVM 乗っ取り級なので対象外。
 */
public final class EcaMixins {

    private EcaMixins() {}

    @Pseudo
    @Mixin(targets = "net.eca.util.EntityUtil", remap = false)
    public abstract static class Util {
        @Inject(method = "kill(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardKill(LivingEntity entity, DamageSource source, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "remove(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardRemove(Entity entity, Entity.RemovalReason reason, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "removeFromServerContainers(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardContainers(ServerLevel level, Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = {"cleanupAI(Lnet/minecraft/world/entity/Entity;)V", "cleanupBossBar(Lnet/minecraft/world/entity/Entity;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardCleanup(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        /** HP の多経路書き込み（同期データ直書き・データフロー逆算・外部スキャン）。減らす方向だけ拒否する */
        @Inject(method = "setHealth(Lnet/minecraft/world/entity/LivingEntity;F)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSetHealth(LivingEntity entity, float health, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity) && !(health >= entity.getHealth())) cir.setReturnValue(false);
        }

        /** 遠方（102400, -102400, 102400）への強制テレポート */
        @Inject(method = "teleport(Lnet/minecraft/world/entity/Entity;DDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardTeleport(Entity entity, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }
    }

    /** 消去中の個体の再登録を拒否する隔離。兵姫を隔離対象にさせない */
    @Pseudo
    @Mixin(targets = "net.eca.util.EntityRemovalQuarantine", remap = false)
    public abstract static class Quarantine {
        @Inject(method = "begin(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardBegin(ServerLevel level, Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "shouldBlockAdd(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$allowAdd(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }
    }
}
