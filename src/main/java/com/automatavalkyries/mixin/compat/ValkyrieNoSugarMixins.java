package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * オートマタヴァルキリー protect の No-Sugar 対策（2026-10-05 ユーザー「ついでに修正して」）。No-Sugar の消しゴム（Eraser）は
 * setRemoved を通らずに消し、消した UUID を「消去済み」に印して同じ UUID の追加も拒否させるので、オメガコアの兵姫が消える → 新しい UUID で戻る、になっていた。
 * オメガコアの兵姫だけ、消しゴムの入口と No-Sugar が LivingEntity に足すメソッドで止める（{@link NoSugarMixins} と同じ所、判定だけ兵姫用）
 */
public final class ValkyrieNoSugarMixins {

    private ValkyrieNoSugarMixins() {}

    @Pseudo
    @Mixin(targets = "com.test.nosugar.utils.item.Eraser_Utils", remap = false)
    public abstract static class EraserUtils {
        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;D)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrie1(Entity self, Entity attacker, double radius, CallbackInfoReturnable<Boolean> cir) {
            if (ValkyrieProtect.shieldForeign(self, "nosugar:eraser")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DZ)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrie2(Entity self, Entity attacker, double radius, boolean skip, CallbackInfoReturnable<Boolean> cir) {
            if (ValkyrieProtect.shieldForeign(self, "nosugar:eraser")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DLnet/minecraft/world/damagesource/DamageSource;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrie3(Entity self, Entity attacker, double radius, DamageSource src, CallbackInfoReturnable<Boolean> cir) {
            if (ValkyrieProtect.shieldForeign(self, "nosugar:eraser")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Z)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrie4(LivingEntity self, LivingEntity attacker, boolean skip, CallbackInfoReturnable<Boolean> cir) {
            if (ValkyrieProtect.shieldForeign(self, "nosugar:eraser")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrie5(LivingEntity self, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
            if (ValkyrieProtect.shieldForeign(self, "nosugar:eraser")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DZLnet/minecraft/world/damagesource/DamageSource;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$valkyrie6(Entity self, Entity attacker, double radius, boolean skip, DamageSource src, CallbackInfoReturnable<Boolean> cir) {
            if (ValkyrieProtect.shieldForeign(self, "nosugar:eraser")) cir.setReturnValue(false);
        }
    }

    /** No-Sugar が LivingEntity に合成するメソッド。No-Sugar の Mixin より後に当たるよう priority を上げる */
    @Mixin(value = LivingEntity.class, priority = 100000)
    public abstract static class Living {
        @Inject(method = "instantKill(Lnet/minecraft/world/entity/LivingEntity;ZLnet/minecraft/world/damagesource/DamageSource;)V",
                at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$valkyrieInstantKill(LivingEntity attacker, boolean skip, DamageSource src, CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign((Entity) (Object) this, "nosugar:instantKill")) ci.cancel();
        }

        @Inject(method = {"instantKill()V", "instantKill(Lnet/minecraft/world/damagesource/DamageSource;)V", "forceErase()V", "eraseClientEntity()V"},
                at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$valkyrieErase(CallbackInfo ci) {
            if (ValkyrieProtect.shieldForeign((Entity) (Object) this, "nosugar:erase")) ci.cancel();
        }

        @Inject(method = "setErased(Z)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$valkyrieSetErased(boolean flag, CallbackInfo ci) {
            if (flag && ValkyrieProtect.shieldForeign((Entity) (Object) this, "nosugar:setErased")) ci.cancel();
        }

        @Inject(method = "markErased(Ljava/util/UUID;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$valkyrieMarkErased(UUID uuid, CallbackInfo ci) {
            Entity self = (Entity) (Object) this;
            if (self.getUUID().equals(uuid) && ValkyrieProtect.shieldForeign(self, "nosugar:markErased")) ci.cancel();
        }
    }
}
