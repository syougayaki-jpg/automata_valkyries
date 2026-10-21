package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SuperSteve（PLZLiZi）の消去の入口（2026-10-22。バイトコードを読んだ結果。実行はしていない）。
 * <p>
 * {@code SSUtil.killEntity} は最後に {@code simpleKillEntity} を呼ぶ。{@code simpleKillEntity} は、サーバーで次を行う:
 * 全クライアントへ {@code RmCEntityL}（ID と UUID）を送る → 個体の除去の印を DISCARDED にし、当たり判定を体積 0 の点にし、
 * レベルのコールバックを NULL にし、死亡の印を書く → ChunkMap の追跡表・検索表（byId / byUuid）・区画・Tick 一覧・部位表を、
 * 「その個体を除いた写し」に差し替える。受け取ったクライアントも、同じ処理を手元の個体にする。
 * 保護中の個体（クライアントでは常に）だけ、この入口で止める。保護の層は個体が消えた後に戻す作りで、消されるたびに入り直しの
 * 異常が積まれ、手元の画面では当たり判定ごと消えたままだった
 * </p>
 */
public final class SuperSteveMixins {

    private SuperSteveMixins() {}

    @Pseudo
    @Mixin(targets = "plz.lizi.supersteve.api.SSUtil", remap = false)
    public abstract static class Util {

        @Inject(method = "simpleKillEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSimpleKill(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "supersteve:simpleKillEntity")) ci.cancel();
        }

        @Inject(method = {"killEntity(Lnet/minecraft/world/entity/Entity;)V", "killEntity(Lnet/minecraft/world/entity/Entity;Z)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardKill(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "supersteve:killEntity")) ci.cancel();
        }
    }
}
