package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.KillModCounters;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.PlayerWard;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackOp;
import com.automatavalkyries.protect.flag.FlagDictionary;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code LivingEntity.hurt()} の入口で即死系ダメージを拒否・サニタイズする */
@Mixin(value = LivingEntity.class, priority = 100000)
public abstract class EprotectDamageMixin {

    /** /kill や IMDS / Hyperlink 等の即死ダメージを HEAD で拒絶し、被弾の瞬間にカウンターを適用する */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$beginHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        LivingEntity self = (LivingEntity) (Object) this;
        if (protect == null) {
            // 守護の護符を持つプレイヤー: 即死ダメージだけ拒否し、通常の被弾は通す（被弾中の HP 減少を正規扱いにするため数える）
            if (PlayerWard.isActive(self)) {
                if (PlayerWard.isInstantKill(source)) {
                    cir.setReturnValue(false);
                    com.automatavalkyries.protect.EprotectForgeEvents.recordKillDamage(self, source);
                    return;
                }
                automata_valkyries$wardHurtDepth++;
            }
            return;
        }

        // 被弾の瞬間に IMDS / Hyperlink の抹消リスト除外・フラグ解除を実行
        KillModCounters.counterAll(self);

        if (protect.isProtectionActive()) {
            if (FlagDictionary.isHarmfulDamageSource(source) || com.automatavalkyries.protect.EprotectForgeEvents.isAttackerHoldingKillItem(source)) {
                cir.setReturnValue(false);
                cir.cancel();
                com.automatavalkyries.protect.EprotectForgeEvents.recordKillDamage(self, source);
                return;
            }
        }
        protect.beginHurt();
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void automata_valkyries$endHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null) protect.endHurt();
        if (automata_valkyries$wardHurtDepth > 0) automata_valkyries$wardHurtDepth--;
    }

    /** 守護の護符を持つプレイヤーの hurt() の入れ子の深さ（この間の HP 減少・0 化は正規の被弾） */
    @Unique
    private int automata_valkyries$wardHurtDepth = 0;

    /** まな板剣などの NaN / 無限大 / 桁外れな値を上限値へ置き換える */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float automata_valkyries$sanitizeDamage(float amount) {
        Eprotect protect = automata_valkyries$getProtect();
        return protect != null ? protect.sanitizeIncomingDamage(amount) : amount;
    }

    /**
     * hurt() を経ない HP 0 化・NaN 化をその場で弾く。
     * クライアントに HP 0 が同期されると死亡演出（deathTime）が始まって戻らなくなるため、修復より前に止める。
     */
    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float automata_valkyries$guardHealth(float health) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null) {
            LivingEntity self = (LivingEntity) (Object) this;
            if (health <= 0.0F || !Float.isFinite(health)) {
                KillModCounters.counterAll(self);
            }
            return protect.filterHealth(health);
        }
        // 守護の護符: 被弾以外での HP 0 化・NaN 化を拒否する（今の HP のまま）
        if (automata_valkyries$wardHurtDepth == 0 && (health <= 0.0F || !Float.isFinite(health))) {
            LivingEntity self = (LivingEntity) (Object) this;
            if (PlayerWard.isActive(self)) {
                float current = com.automatavalkyries.protect.DirectRead.health(self);
                if (Float.isFinite(current) && current > 0.0F) {
                    AttackLog.refused(self, AttackOp.HEALTH_LOSS + ":zero");
                    return current;
                }
            }
        }
        return health;
    }

    /**
     * IMDS 等が die() を直接呼んでも、正規の被弾（canDie == true）でない限り拒否する。
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDie(DamageSource source, CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        LivingEntity living = (LivingEntity) (Object) this;
        // 守護の護符: HP が残っているのに die() を直接呼ばれたら拒否（正規の死亡は HP 0 になってから呼ばれる）
        if (protect == null && PlayerWard.isActive(living)) {
            ci.cancel();
            AttackLog.refused(living, AttackOp.DIE);
            return;
        }
        if (protect != null) {
            LivingEntity self = (LivingEntity) (Object) this;
            KillModCounters.counterAll(self);

            if (!protect.canDie()) {
                ci.cancel();
                protect.noteRefusedDeath();
                float current = com.automatavalkyries.protect.DirectRead.health(self);
                float fallback = Float.isFinite(current) && current > 0.0F ? current : self.getMaxHealth();
                self.setHealth(fallback);
                self.deathTime = 0;
            }
        }
    }

    /**
     * Hyperlink や IMDS の Mixin/Transformer による getHealth() の -Infinity や 0 化をサニタイズする。
     * 正規に死亡していない（!protect.isDying() && !protect.isDefeated()）のに 0 以下の値に改変された場合、健全なHPを返す。
     * ただし、正規のダメージ処理中による最後の一撃（protect.isLegitimateFatalBlow()）の場合は 0.0F を通す。
     */
    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void automata_valkyries$guardGetHealth(CallbackInfoReturnable<Float> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.isProtectionActive()) {
            float val = cir.getReturnValue();
            if (!Float.isFinite(val)) {
                cir.setReturnValue(protect.getLastValidHealth());
            } else if (val <= 0.0F && !protect.isDying() && !protect.isDefeated()) {
                if (protect.isLegitimateFatalBlow()) {
                    return; // 正規の撃破進行中なので 0.0F をそのまま通す
                }
                float safe = protect.getLastValidHealth();
                if (safe > 0.0F) {
                    cir.setReturnValue(safe);
                }
            }
        }
    }

    /**
     * Hyperlink の isAliveNovel による強制 false を覆す。
     * 正規の撃破進行中の場合は介入せずバニラの判定を通す。
     */
    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void automata_valkyries$guardIsAlive(CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.isProtectionActive()) {
            if (!cir.getReturnValue() && !protect.isDying() && !protect.isDefeated() && !protect.isDespawning()) {
                if (protect.isLegitimateFatalBlow()) {
                    return;
                }
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Hyperlink の isDeadOrDyingNovel による強制 true を覆す。
     * 正規の撃破進行中の場合は介入せずバニラの判定を通す。
     */
    @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
    private void automata_valkyries$guardIsDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.isProtectionActive()) {
            if (cir.getReturnValue() && !protect.isDying() && !protect.isDefeated()) {
                if (protect.isLegitimateFatalBlow()) {
                    return;
                }
                cir.setReturnValue(false);
            }
        }
    }

    private Eprotect automata_valkyries$getProtect() {
        return (Object) this instanceof IEprotected provider ? provider.getEprotect() : null;
    }

    /**
     * Hyperlink の LivingEntityMixin.novelize を保護対象に対して完全にブロックする
     */
    @Inject(method = "novelize(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardLivingNovelize(LivingEntity living, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected) {
            ci.cancel();
            AttackLog.refused((LivingEntity) (Object) this, AttackOp.NOVELIZE);
        }
    }
}
