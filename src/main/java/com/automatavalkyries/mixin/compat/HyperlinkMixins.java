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
 * Hyperlink の NovelHandler.novelized / special / novelize は引数 entity の null チェックを欠いており、
 * プロキシ解体後や消滅後の null 参照時（例: MetapotentFlashfurRendererMixin 等からの呼び出し）に
 * ゲームをクラッシュさせるため、HEAD で安全にガードする。
 * </p>
 */
public final class HyperlinkMixins {

    private HyperlinkMixins() {}

    @Pseudo
    @Mixin(targets = "com.sakurafuld.hyperdaimc.content.hyper.novel.NovelHandler", remap = false)
    public abstract static class NovelHandler {

        @Inject(method = "novelized(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardNovelized(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity == null) {
                cir.setReturnValue(false);
                return;
            }
            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                if (com.automatavalkyries.protect.PlayerWard.isActive(player) || com.automatavalkyries.protect.PlayerWard.isRespawnProtected(player)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
            if (entity instanceof com.automatavalkyries.protect.IEprotected) {
                cir.setReturnValue(false);
            }
        }

        @Inject(method = "special(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSpecial(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity == null) {
                cir.setReturnValue(false);
            }
        }

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

    @Pseudo
    @Mixin(targets = "com.sakurafuld.hyperdaimc.mixin.novel.LivingEntityMixin", remap = false)
    public abstract static class NovelLivingMixin {
        @Inject(method = "getHealthNovel(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardGetHealthNovel(CallbackInfoReturnable<Float> cir) {
            if ((Object) this instanceof net.minecraft.world.entity.player.Player player) {
                if (com.automatavalkyries.protect.PlayerWard.isActive(player) || com.automatavalkyries.protect.PlayerWard.isRespawnProtected(player)) {
                    cir.cancel();
                }
            } else if ((Object) this instanceof com.automatavalkyries.protect.IEprotected) {
                cir.cancel();
            }
        }

        @Inject(method = "isDeadOrDyingNovel(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardIsDeadOrDyingNovel(CallbackInfoReturnable<Boolean> cir) {
            if ((Object) this instanceof net.minecraft.world.entity.player.Player player) {
                if (com.automatavalkyries.protect.PlayerWard.isActive(player) || com.automatavalkyries.protect.PlayerWard.isRespawnProtected(player)) {
                    cir.cancel();
                }
            } else if ((Object) this instanceof com.automatavalkyries.protect.IEprotected) {
                cir.cancel();
            }
        }

        @Inject(method = "isAliveNovel(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardIsAliveNovel(CallbackInfoReturnable<Boolean> cir) {
            if ((Object) this instanceof net.minecraft.world.entity.player.Player player) {
                if (com.automatavalkyries.protect.PlayerWard.isActive(player) || com.automatavalkyries.protect.PlayerWard.isRespawnProtected(player)) {
                    cir.cancel();
                }
            } else if ((Object) this instanceof com.automatavalkyries.protect.IEprotected) {
                cir.cancel();
            }
        }
    }
}
