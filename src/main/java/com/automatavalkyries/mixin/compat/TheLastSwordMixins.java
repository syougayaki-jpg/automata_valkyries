package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import com.automatavalkyries.protect.IEprotected;
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
 * The Last Sword You Never Forgot（ラストソード）の保護とクラッシュ・フリーズ抑止 Mixin。
 * <p>
 * ラストソードの射出弾や即死攻撃が {@link IEprotected}（兵姫など）にヒットした際、
 * 毎 Tick の {@code theLastEndSetDead} 呼び出しによるチャットメッセージ（completely destroyed by）のスパム送信
 * およびサーバー／クライアントのパケット飽和によるフリーズを完全に遮断する。
 * </p>
 */
public final class TheLastSwordMixins {

    private TheLastSwordMixins() {}

    @Pseudo
    @Mixin(targets = "net.the_last_sword.util.EntityUtil", remap = false)
    public abstract static class Util {

        /**
         * ラストソードの即死処理・死亡メッセージ送信（completely destroyed by ...）。
         * 保護対象への毎 Tick 連続呼び出しによるチャットスパムとフリーズを防止。
         */
        @Inject(method = "theLastEndSetDead(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardTheLastEndSetDead(LivingEntity living, DamageSource source, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(living, "the_last_sword:theLastEndSetDead")) {
                ci.cancel();
            }
        }

        /**
         * ラストソードによる HP 直書き処理。
         */
        @Inject(method = "theLastEndSetHealth(Lnet/minecraft/world/entity/LivingEntity;F)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardTheLastEndSetHealth(LivingEntity living, float health, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shieldAttack(living, "the_last_sword:theLastEndSetHealth")) {
                cir.setReturnValue(false);
            }
        }
    }

    @Pseudo
    @Mixin(targets = "net.the_last_sword.item.TheLastSwordYouNeverForgot", remap = false)
    public abstract static class TheLastSwordYouNeverForgotCompat {
        @Inject(method = "attack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;F)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardAttack(LivingEntity living, Entity attacker, float damage, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(living, "the_last_sword:attack")) {
                ci.cancel();
            }
        }
    }

    @Pseudo
    @Mixin(targets = "net.the_last_sword.entity.TheLastEndSwordProjectile", remap = false)
    public abstract static class Projectile {

        /**
         * 射出弾による範囲ダメージ・即死ループの防止。
         */
        @Inject(method = "applyBaseDamage(Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardBaseDamage(Entity target, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(target, "the_last_sword:projectileBase")) {
                ci.cancel();
            }
        }

        @Inject(method = "applyExtraDamage(Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardExtraDamage(Entity target, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(target, "the_last_sword:projectileExtra")) {
                ci.cancel();
            }
        }
    }

    /** UltraTestSwordItem のサバイバルモード手放し時の解除不全バグを自動補完 */
    @Pseudo
    @Mixin(targets = "net.the_last_sword.test.UltraTestSwordItem", remap = false)
    public abstract static class UltraTestSwordCompat {
        @Inject(method = "onPlayerTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V", at = @At("HEAD"), require = 0)
        private static void automata_valkyries$guardUltraTestSwordTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event, CallbackInfo ci) {
            if (event != null && event.player != null) {
                if (!com.automatavalkyries.protect.EcaLastSwordHelper.hasDefenseSword(event.player)) {
                    com.automatavalkyries.protect.EcaLastSwordHelper.clearLocationLock(event.player.getUUID());
                }
            }
        }
    }
}
