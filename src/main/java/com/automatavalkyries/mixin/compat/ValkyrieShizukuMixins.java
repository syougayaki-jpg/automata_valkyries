package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * オートマタヴァルキリー protect の Shizuku Blade（猫羽雫）対策（2026-10-05）。{@code Ymsa} の即死は setRemoved を拒否されても
 * {@code annihilate}（エンティティの一覧・区画・Tick 一覧・追跡からの直接の削除）と {@code wipeEntityFields}（フィールドの総書き換え）で消すので、
 * オメガコアの兵姫が消える → 見張りが戻す、になり、戻った位置が持ち主から離れていた（ユーザーの報告「死んでないけど遠くにいる」）。
 * オメガコアの兵姫だけ、消す・書き換える段の入口で止める（ダメージ・HP 0・die の段は兵姫の側で無視している）
 */
public final class ValkyrieShizukuMixins {

    private ValkyrieShizukuMixins() {}

    @Pseudo
    @Mixin(targets = "com.buwan.shizuku.sa.Ymsa", remap = false)
    public abstract static class Ymsa {

        @Inject(method = "annihilate(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieAnnihilate(LivingEntity target, ServerPlayer player, ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
            if (ValkyrieProtect.shieldForeign(target, "shizuku:annihilate")) cir.setReturnValue(false);
        }

        @Inject(method = "physicallyRemove(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerLevel;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyriePhysicallyRemove(LivingEntity target, ServerLevel level, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(target, "shizuku:physicallyRemove")) ci.cancel();
        }

        @Inject(method = "wipeEntityFields(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieWipeFields(Entity target, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(target, "shizuku:wipeEntityFields")) ci.cancel();
        }

        @Inject(method = {"stripProtections(Lnet/minecraft/world/entity/LivingEntity;)V", "wipeBossHealth(Lnet/minecraft/world/entity/LivingEntity;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieStrip(LivingEntity target, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(target, "shizuku:strip")) ci.cancel();
        }

        /** クライアントへの「消えた」の直送（消去の段を止めても、これでプレイヤーから見えなくなっていた） */
        @Inject(method = "broadcastClientErase(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerLevel;Z)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieClientErase(LivingEntity target, ServerLevel level, boolean boss, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(target, "shizuku:broadcastClientErase")) ci.cancel();
        }

        /** 即死の後に出現を送り直す（ほかの経路でクライアントから消えていても戻す） */
        @Inject(method = "killTarget(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;)Lcom/buwan/shizuku/sa/Ymsa$KillOutcome;",
                at = @At("RETURN"), require = 0)
        private static void automata_valkyries$valkyrieResync(LivingEntity target, ServerPlayer player, ServerLevel level, CallbackInfoReturnable<Object> cir) {
            if (target instanceof com.automatavalkyries.entity.ValkyrieEntity v && v.hasOmegaCore()) ValkyrieProtect.resync(v);
        }

        @Inject(method = "suppressRespawn(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrieSuppressRespawn(LivingEntity target, ServerPlayer player, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign(target, "shizuku:suppressRespawn")) ci.cancel();
        }
    }
}
