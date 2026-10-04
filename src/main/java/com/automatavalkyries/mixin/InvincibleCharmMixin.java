package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.InvincibleCharm;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 無敵の護符（{@link InvincibleCharm}）: 被ダメージの動きを切る（2026-10-04 ユーザー指定「ノックバックがうざい」）。
 * サーバー: 外から押されない（Entity.push）。クライアント: 自分が無敵の護符を持っていれば、被弾の赤い点滅・画面の揺れ・被弾の音を出さない
 */
public final class InvincibleCharmMixin {

    private InvincibleCharmMixin() {}

    @Mixin(Entity.class)
    public abstract static class Push {
        @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$noPush(double x, double y, double z, CallbackInfo ci) {
            if (InvincibleCharm.isActive((Entity) (Object) this) || InvincibleCharm.holdsOnClient((Entity) (Object) this)) ci.cancel();
        }
    }

    @Mixin(LivingEntity.class)
    public abstract static class HurtMotion {
        @Inject(method = "handleDamageEvent", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$noDamageEvent(DamageSource source, CallbackInfo ci) {
            if (InvincibleCharm.holdsOnClient((Entity) (Object) this)) ci.cancel();
        }

        @Inject(method = "animateHurt", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$noHurtAnimation(float yaw, CallbackInfo ci) {
            if (InvincibleCharm.holdsOnClient((Entity) (Object) this)) ci.cancel();
        }
    }

    /**
     * 護符を持っている間は、HP・生死の問い合わせに実際に保存されている値を返す（2026-10-04 修正: 実際の HP は 20 のまま、見かけの HP だけマイナスにされて、
     * サーバーがその値をクライアントへ送り、死亡画面が出ていた）。他の処理が返り値を書き換えた後で直すよう、優先度を高くして最後に動かす
     */
    @Mixin(value = LivingEntity.class, priority = 2000)
    public abstract static class TrueHealth {
        private static boolean automata_valkyries$guarded(Object self) {
            if (!(self instanceof net.minecraft.world.entity.player.Player player)) return false;
            return InvincibleCharm.isActive(player) || InvincibleCharm.holdsOnClient(player) || com.automatavalkyries.protect.PlayerWard.isActive(player);
        }

        @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
        private void automata_valkyries$trueHealth(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
            if (!automata_valkyries$guarded(this)) return;
            float actual = com.automatavalkyries.protect.DirectRead.health((LivingEntity) (Object) this);
            if (cir.getReturnValueF() != actual) cir.setReturnValue(actual);
        }

        @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
        private void automata_valkyries$trueAlive(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
            if (!automata_valkyries$guarded(this)) return;
            LivingEntity self = (LivingEntity) (Object) this;
            boolean actual = !com.automatavalkyries.protect.DirectRead.isRemoved(self) && com.automatavalkyries.protect.DirectRead.health(self) > 0.0F;
            if (cir.getReturnValueZ() != actual) cir.setReturnValue(actual);
        }

        @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
        private void automata_valkyries$trueDying(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
            if (!automata_valkyries$guarded(this)) return;
            boolean actual = com.automatavalkyries.protect.DirectRead.health((LivingEntity) (Object) this) <= 0.0F;
            if (cir.getReturnValueZ() != actual) cir.setReturnValue(actual);
        }
    }
}
