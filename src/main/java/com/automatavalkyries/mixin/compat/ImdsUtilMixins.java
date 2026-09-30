package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** IMDS-Reborn の補助処理（奈落テレポート・ボスバー消去・出現拒否・移動停止） */
public final class ImdsUtilMixins {

    private ImdsUtilMixins() {}

    /** -Infinity テレポート（setPosRaw / 座標フィールド直書きを含む）とボスバーの消去 */
    @Pseudo
    @Mixin(targets = "net.mcreator.imds_reborn.procedures.ImdsUtilProcedure", remap = false)
    public abstract static class UtilProcedure {
        @Inject(method = "teleport(Lnet/minecraft/world/entity/Entity;D)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardTeleport(Entity entity, double pos, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "imds:teleport")) ci.cancel();
        }

        @Inject(method = "cleanBossBar(Lnet/minecraft/world/entity/Entity;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardCleanBossBar(Entity entity, boolean isDel, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "imds:cleanBossBar")) ci.cancel();
        }
    }

    /** 遅延テレポート（リフレクションで座標フィールドを直書きする） */
    @Pseudo
    @Mixin(targets = "net.mcreator.imds_reborn.ForceTeleportUtil", remap = false)
    public abstract static class ForceTeleport {
        @Inject(method = "forceTeleport(Lnet/minecraft/world/entity/Entity;DDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardForceTeleport(Entity target, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shieldAttack(target, "imds:forceTeleport")) cir.setReturnValue(false);
        }
    }

    /** EntityJoinLevelEvent で抹消済み扱いの個体の出現をキャンセルする（修復時の再登録が失敗する） */
    @Pseudo
    @Mixin(targets = "net.mcreator.imds_reborn.procedures.AutoSCProcedure", remap = false)
    public abstract static class AutoSC {
        @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardExecute(Event event, LevelAccessor world, Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }
    }

    /** LivingTickEvent でのキル・AI 停止・無重力化 */
    @Pseudo
    @Mixin(targets = "net.mcreator.imds_reborn.procedures.AutoMoveCancelProcedure", remap = false)
    public abstract static class AutoMoveCancel {
        @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardExecute(Event event, LevelAccessor world, Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }
    }
}
