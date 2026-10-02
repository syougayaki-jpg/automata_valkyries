package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.KillModCounters;
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
        boolean inScope = protect != null || com.automatavalkyries.protect.EprotectForgeEvents.isUnderProtectionScope(self);
        if (!inScope) {
            return;
        }

        // 被弾の瞬間に IMDS / Hyperlink の抹消リスト除外・フラグ解除を実行
        KillModCounters.counterAll(self);

        // EventBus が破壊された場合でも動作するよう EventBusGuardian を点検
        com.automatavalkyries.protect.EventBusGuardian.ensureEventBusIntegrity();

        if (protect != null && protect.isProtectionActive()) {
            boolean isReinforced = com.automatavalkyries.protect.plus.EprotectPlusLink.isReinforcedActive() || protect.isReinforcedActive();
            String sourceMod = com.automatavalkyries.protect.EprotectForgeEvents.getSourceMod(source);
            boolean isBlacklisted = com.automatavalkyries.protect.attack.ModBlacklist.contains(sourceMod);
            if (isBlacklisted) {
                com.automatavalkyries.protect.plus.EprotectPlusLink.triggerBlacklistReinforcement(sourceMod);
            }

            // 強化モード中・ブラックリストMOD相手でも、HEAD で遮断するのは即死系の攻撃だけ（通常攻撃は通して上限に掛ける）
            if ((isReinforced || isBlacklisted) && com.automatavalkyries.protect.EprotectForgeEvents.isLethalAttack(source, amount)) {
                cir.setReturnValue(false);
                cir.cancel();
                protect.penalizeMaliciousAttack();
                AttackLog.refused(self, AttackOp.DAMAGE + ":mixin_head_reinforced_guard", sourceMod);
                return;
            }

            if (FlagDictionary.isHarmfulDamageSource(source) || com.automatavalkyries.protect.EprotectForgeEvents.isAttackerHoldingKillItem(source)) {
                cir.setReturnValue(false);
                cir.cancel();
                com.automatavalkyries.protect.EprotectForgeEvents.recordKillDamage(self, source);
                return;
            }
            protect.beginHurt();
        } else if (protect == null) {
            // 保護範囲内（同乗者・乗り物・オーラ防護）
            String sourceMod = com.automatavalkyries.protect.EprotectForgeEvents.getSourceMod(source);
            boolean isBlacklisted = com.automatavalkyries.protect.attack.ModBlacklist.contains(sourceMod);
            if (isBlacklisted) {
                com.automatavalkyries.protect.plus.EprotectPlusLink.triggerBlacklistReinforcement(sourceMod);
            }
            if (isBlacklisted || com.automatavalkyries.protect.EprotectForgeEvents.isLethalAttack(source, amount)
                    || FlagDictionary.isHarmfulDamageSource(source) || com.automatavalkyries.protect.EprotectForgeEvents.isAttackerHoldingKillItem(source)) {
                cir.setReturnValue(false);
                cir.cancel();
                AttackLog.refused(self, AttackOp.DAMAGE + ":scope_guard_mixin", sourceMod);
            }
        }
    }

    /**
     * hurt() を迂回して直接 actuallyHurt() を呼び出すチートに対抗し、
     * 強化モード時や有害ダメージ時に HEAD で完全拒絶する（案4）。
     */
    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardActuallyHurt(DamageSource damageSource, float damageAmount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Eprotect protect = automata_valkyries$getProtect();
        boolean inScope = protect != null ? protect.isProtectionActive() : com.automatavalkyries.protect.EprotectForgeEvents.isUnderProtectionScope(self);
        if (inScope) {
            boolean isReinforced = com.automatavalkyries.protect.plus.EprotectPlusLink.isReinforcedActive() || (protect != null && protect.isReinforcedActive());
            String sourceMod = com.automatavalkyries.protect.EprotectForgeEvents.getSourceMod(damageSource);
            boolean guarded = isReinforced || com.automatavalkyries.protect.attack.ModBlacklist.contains(sourceMod);
            boolean malicious = guarded && com.automatavalkyries.protect.EprotectForgeEvents.isLethalAttack(damageSource, damageAmount);
            if (malicious
                    || FlagDictionary.isHarmfulDamageSource(damageSource)
                    || com.automatavalkyries.protect.EprotectForgeEvents.isAttackerHoldingKillItem(damageSource)) {
                ci.cancel();
                if (malicious && protect != null) protect.penalizeMaliciousAttack();
                KillModCounters.counterAll(self);
            }
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void automata_valkyries$endHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null) protect.endHurt();
    }

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
        LivingEntity self = (LivingEntity) (Object) this;
        if (protect != null) {
            if (health <= 0.0F || !Float.isFinite(health)) {
                KillModCounters.counterAll(self);
            }
            return protect.filterHealth(health);
        } else if (!(self instanceof net.minecraft.world.entity.player.Player) && com.automatavalkyries.protect.EprotectForgeEvents.isUnderProtectionScope(self)) {
            // 同乗者・乗り物等の非プレイヤーの HP 異常改変（setHealth(0) や NaN）を防止
            if (health <= 0.0F || !Float.isFinite(health)) {
                KillModCounters.counterAll(self);
                if (self.isAlive() && self.getHealth() > 0.0F) {
                    return self.getHealth();
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
}
