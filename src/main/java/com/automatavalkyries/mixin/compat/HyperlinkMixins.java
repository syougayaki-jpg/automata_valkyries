package com.automatavalkyries.mixin.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * </p>
 */
public final class HyperlinkMixins {

    private HyperlinkMixins() {}

    @Pseudo
    @Mixin(targets = "com.sakurafuld.hyperdaimc.content.hyper.novel.NovelHandler", remap = false)
    public abstract static class NovelHandler {

        /**
         * 小説化状態判定のガード。
         * null 参照防止、兵姫の完全免除を行う。
         */
        @Inject(method = "novelized(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardNovelized(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity == null) {
                cir.setReturnValue(false);
                return;
            }
            if (entity instanceof com.automatavalkyries.protect.IEprotected) {
                cir.setReturnValue(false);
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
         * 兵姫、護符所持プレイヤー、リスポーン保護中プレイヤーへの攻撃を遮断する。
         * 通常プレイヤー（護符なし・リスポーン保護終了後）の戦闘による死亡は許容する。
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
