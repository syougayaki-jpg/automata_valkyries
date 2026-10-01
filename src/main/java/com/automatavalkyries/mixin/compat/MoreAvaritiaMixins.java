package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MoreAvaritia（Infinity God Sword など）。{@code InfinityUtils.killEntity} はワールドの各一覧からの直接削除・removalReason の直書き・
 * 座標 (999999, …) への飛ばしに加え、levelCallback を「何もしない独自の実装」に差し替える（NULL ではないので素朴な復元をすり抜ける）。
 * {@code superAttack} は死亡リスト（ListUtils）に登録し、以後 LivingTickEvent で毎 Tick killEntity を掛け続ける。入口で止める。
 */
public final class MoreAvaritiaMixins {

    private MoreAvaritiaMixins() {}

    @Pseudo
    @Mixin(targets = "net.wzz.more_avaritia.util.InfinityUtils", remap = false)
    public abstract static class Utils {
        @Inject(method = "killEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardKill(Entity entity, LivingEntity source, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "more_avaritia:kill")) ci.cancel();
        }

        @Inject(method = "superAttack(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSuper(LivingEntity living, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(living, "more_avaritia:super")) ci.cancel();
        }

        @Inject(method = "easyAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/player/Player;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardEasy(LivingEntity living, Player player, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(living, "more_avaritia:easy")) ci.cancel();
        }

        /** 同期データへの HP 直書き（3 引数版もここを通る）。減らす方向だけ拒否する */
        @Inject(method = "forceSetHealth(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/entity/LivingEntity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSetHealth(LivingEntity living, float health, DamageSource source, LivingEntity attacker,
                                                             CallbackInfo ci) {
            if (!(health >= com.automatavalkyries.protect.DirectRead.health(living)) && EprotectCompat.shieldAttack(living, "more_avaritia:setHealth")) ci.cancel();
        }

        /** 倒していないのに戦利品だけ落とさせない */
        @Inject(method = "forceDropLoot(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/entity/LivingEntity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardLoot(LivingEntity living, DamageSource source, LivingEntity attacker, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(living, "more_avaritia:loot")) ci.cancel();
        }
    }

    /** 死亡リスト。登録されると毎 Tick 抹消が掛かり続ける */
    @Pseudo
    @Mixin(targets = "net.wzz.more_avaritia.util.ListUtils", remap = false)
    public abstract static class DieList {
        @Inject(method = "addDieEntity(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardAdd(LivingEntity living, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(living, "more_avaritia:add")) ci.cancel();
        }

        @Inject(method = "isDieEntity(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardCheck(LivingEntity living, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(living)) cir.setReturnValue(false);
        }
    }
}
