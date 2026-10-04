package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.PlayerWard;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackOp;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 守護の護符（{@link PlayerWard}）: プレイヤーへの即死を拒否する。即死ダメージ（/kill 型）は当たらない、die() の直接呼び出しは止める、
 * 被弾以外での HP 0 化・NaN 化は今の HP のままにする。通常のダメージ（被弾の中での HP の減少・0 化）は通す。保護の中心の Mixin とは別に見る
 */
@Mixin(LivingEntity.class)
public abstract class PlayerWardLivingMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player) || !PlayerWard.isActive(self)) return;
        if (PlayerWard.isInstantKill(source)) {
            AttackLog.refused(self, AttackOp.KILL_DAMAGE + ":" + source.getMsgId());
            cir.setReturnValue(false);
            return;
        }
        PlayerWard.enterHurt((Player) self);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void automata_valkyries$wardHurtEnd(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player) PlayerWard.exitHurt(player);
    }

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float automata_valkyries$wardSetHealth(float health) {
        LivingEntity living = (LivingEntity) (Object) this;
        // 無敵の護符: HP は常に最大（2026-10-04 ユーザー指定）
        if (com.automatavalkyries.protect.InvincibleCharm.isActive(living) && !(health >= living.getMaxHealth())) return living.getMaxHealth();
        if (health > 0.0F && Float.isFinite(health)) return health;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player) || PlayerWard.inHurt(player) || !PlayerWard.isActive(self)) return health;
        float current = DirectRead.health(self);
        if (!Float.isFinite(current) || current <= 0.0F) return health;
        AttackLog.refused(self, AttackOp.HEALTH_LOSS + ":zero");
        return current;
    }

    /** 死亡処理（プレイヤーの本物の個体は ServerPlayer.die が上書きしているので、そちらは {@link PlayerWardMixin} で見る） */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardDie(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof Player player) || !PlayerWard.refusesDeath(player)) return;
        AttackLog.refused(player, AttackOp.DIE);
        ci.cancel();
    }
}
