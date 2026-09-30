package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
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
 * No-Sugar（シュガーソード等）の抹消。hurt / die / remove を一切通らずにフィールドとワールドの一覧を直接書き換えるうえ、
 * 同梱の Java エージェントが抹消済み個体の getHealth / isAlive / isRemoved を全呼び出し箇所で上書きするため、入口で止める。
 */
public final class NoSugarMixins {

    private NoSugarMixins() {}

    /** 剣の左クリック・振り・範囲攻撃・レイキャストのパケットが共通で通る入口 */
    @Pseudo
    @Mixin(targets = "com.test.nosugar.utils.item.Eraser_Utils", remap = false)
    public abstract static class EraserUtils {
        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;D)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard1(Entity self, Entity attacker, double radius, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(self)) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DZ)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard2(Entity self, Entity attacker, double radius, boolean skip, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(self)) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DLnet/minecraft/world/damagesource/DamageSource;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard3(Entity self, Entity attacker, double radius, DamageSource src, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(self)) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Z)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard4(LivingEntity self, LivingEntity attacker, boolean skip, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(self)) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard5(LivingEntity self, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(self)) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DZLnet/minecraft/world/damagesource/DamageSource;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard6(Entity self, Entity attacker, double radius, boolean skip, DamageSource src, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(self)) cir.setReturnValue(false);
        }
    }

    /**
     * No-Sugar が LivingEntity に合成するメソッド（instantKill / forceErase / setErased / markErased / eraseClientEntity）。
     * No-Sugar の Mixin（priority 0）より後に適用される必要があるので priority を上げる。
     * クリエイティブソードや凝視の呪文など Eraser_Utils を通らない経路もここで止まる。
     */
    @Mixin(value = LivingEntity.class, priority = 100000)
    public abstract static class Living {
        @Inject(method = "instantKill(Lnet/minecraft/world/entity/LivingEntity;ZLnet/minecraft/world/damagesource/DamageSource;)V",
                at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$guardInstantKill(LivingEntity attacker, boolean skip, DamageSource src, CallbackInfo ci) {
            if (EprotectCompat.shouldShield((Entity) (Object) this)) ci.cancel();
        }

        @Inject(method = {"instantKill()V", "instantKill(Lnet/minecraft/world/damagesource/DamageSource;)V", "forceErase()V", "eraseClientEntity()V"},
                at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$guardErase(CallbackInfo ci) {
            if (EprotectCompat.shouldShield((Entity) (Object) this)) ci.cancel();
        }

        @Inject(method = "setErased(Z)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$guardSetErased(boolean flag, CallbackInfo ci) {
            if (flag && EprotectCompat.shouldShield((Entity) (Object) this)) ci.cancel();
        }

        @Inject(method = "markErased(Ljava/util/UUID;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$guardMarkErased(UUID uuid, CallbackInfo ci) {
            Entity self = (Entity) (Object) this;
            if (self.getUUID().equals(uuid) && EprotectCompat.shouldShield(self)) ci.cancel();
        }
    }
}
