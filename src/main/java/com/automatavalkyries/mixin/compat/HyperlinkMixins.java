package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.protect.DirectRead;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hyperlink (hyperdaimc) に対する防御及び NullPointerException 抑制 Mixin。
 * <p>
 * 1. NovelHandler.novelized / special / novelize における引数 entity の null ガード（レンダラーやプロキシ消滅時の NPE クラッシュ防止）
 * 2. 兵姫（IEprotected）に対する小説化（即死・消去）の確実な無効化
 * 3. 隔絶致死・終焉抹消された非プレイヤー個体（FumetsuEntity 等）の死亡演出（tickDeath）および消去フックの解錠
 * </p>
 */
public final class HyperlinkMixins {

    private HyperlinkMixins() {}

    @Pseudo
    @Mixin(targets = "com.sakurafuld.hyperdaimc.content.hyper.novel.NovelHandler", remap = false)
    public abstract static class NovelHandler {
        private static final ThreadLocal<Boolean> IN_CHECK = ThreadLocal.withInitial(() -> Boolean.FALSE);

        /**
         * 小説化状態判定のガード。
         * null 参照防止、兵姫の完全免除、および隔絶致死・抹消済み個体の死亡・消去ロック解除を行う。
         */
        @Inject(method = "novelized(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardNovelized(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity == null) {
                cir.setReturnValue(false);
                return;
            }
            if (IsolationDamageEngine.isProtectedTarget(entity)) {
                cir.setReturnValue(false);
                return;
            }
            // 判定の中の読み出し（HP・死亡フラグ）には他 MOD の差し込みがあり、その中でまた novelized が呼ばれる
            // （Hyperlink の getMuteki / getHealthNovel、Omni-Mobs の checkEntityDead 経由）。判定の最中の呼び直しでは何もせず抜ける
            // （2026-10-22、StackOverflowError で 2 回落ちた）
            if (!(entity instanceof Player) && !IN_CHECK.get()) {
                IN_CHECK.set(Boolean.TRUE);
                try {
                    if (IsolationDamageEngine.isIsolatedDead(entity) || AnnihilationExecutionAgent.isAnnihilated(entity)) {
                        cir.setReturnValue(true);
                        return;
                    }
                    if (entity instanceof LivingEntity living && !DirectRead.hasHealth(living)
                            && (living.getPose() == Pose.DYING || living.deathTime > 0
                            || ((LivingEntityAccessor) living).automata_valkyries$isDead())) {
                        cir.setReturnValue(true);
                    }
                } finally {
                    IN_CHECK.set(Boolean.FALSE);
                }
            }
        }

        /**
         * 特殊エンティティ判定の null セーフガード。
         */
        @Inject(method = "special(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSpecial(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity == null) {
                cir.setReturnValue(false);
            }
        }

        /**
         * 小説化攻撃実行のガード。
         * 兵姫（IEprotected）への攻撃を遮断する。
         * プレイヤー保護（PlayerWard）は廃止されたため、プレイヤーに対する遮断は行わない（リスポーン時クリーンアップで対応）。
         */
        @Inject(method = "novelize(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardNovelize(LivingEntity living, Entity entity, boolean flag, CallbackInfo ci) {
            if (living == null || entity == null) {
                ci.cancel();
                return;
            }
            if (com.automatavalkyries.protect.EprotectCompat.shieldAttack(entity, "hyperlink:novelize")) {
                ci.cancel();
            }
        }
    }
}
