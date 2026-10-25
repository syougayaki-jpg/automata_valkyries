package com.automatavalkyries.mixin;

import com.automatavalkyries.AutomataValkyries;
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
                com.automatavalkyries.protect.Eprotect.reinforceAgainst(sourceMod);
            }

            // 強化モード中・ブラックリストMOD相手でも、HEAD で遮断するのは即死系の攻撃だけ（通常攻撃は通して上限に掛ける）
            // このMOD自身の兵装や vanilla のダメージは即死技術として扱わず、被弾上限・防御ロジックに任せる
            boolean isGuardedMod = com.automatavalkyries.protect.EprotectForgeEvents.guardedSource(sourceMod, isReinforced);
            if (isGuardedMod && com.automatavalkyries.protect.EprotectForgeEvents.isLethalAttack(source, amount, protect)) {
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
                com.automatavalkyries.protect.Eprotect.reinforceAgainst(sourceMod);
            }
            boolean isSelf = AutomataValkyries.MODID.equals(sourceMod);
            if (isBlacklisted || (!isSelf && com.automatavalkyries.protect.EprotectForgeEvents.isLethalAttack(source, amount))
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
            boolean isBlacklisted = com.automatavalkyries.protect.attack.ModBlacklist.contains(sourceMod);
            boolean guarded = com.automatavalkyries.protect.EprotectForgeEvents.guardedSource(sourceMod, isReinforced);
            boolean malicious = guarded && com.automatavalkyries.protect.EprotectForgeEvents.isLethalAttack(damageSource, damageAmount, protect);
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
        if (protect == null) return;
        // 正規の最後の一撃で実際の HP が尽きたのに死亡処理が呼ばれなかったら（他 MOD が getHealth / isDeadOrDying を書き換えて
        // 「生きている」と答えさせると vanilla の hurt が die を呼ばない。2026-10-05、HP 0 → 修復で 50 に戻り撃破できなかった）、ここで呼ぶ
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && protect.isProtectionActive() && !protect.isDefeated() && !protect.isDying()
                && !(com.automatavalkyries.protect.DirectRead.health(self) > 0.0F) && protect.canDie()) {
            self.die(source);
        }
        protect.endHurt();
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
                protect.refuseDeath(com.automatavalkyries.protect.attack.AttackOp.DIE); // 凶悪技術なら罰則で HP を最大へ
            }
        }
    }

    /** 撃破を認めていないのに死亡のドロップを出させられたら断る（{@link Eprotect#refuseDeathDrop}） */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDropAllDeathLoot(DamageSource source, CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.refuseDeathDrop("all")) ci.cancel();
    }

    @Inject(method = "dropFromLootTable", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDropFromLootTable(DamageSource source, boolean byPlayer, CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.refuseDeathDrop("loot_table")) ci.cancel();
    }

    @Inject(method = "dropCustomDeathLoot", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDropCustomDeathLoot(DamageSource source, int looting, boolean byPlayer, CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.refuseDeathDrop("custom")) ci.cancel();
    }

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDropEquipment(CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.refuseDeathDrop("equipment")) ci.cancel();
    }

    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDropExperience(CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && protect.refuseDeathDrop("experience")) ci.cancel();
    }

    @Inject(method = "getExperienceReward", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardExperienceReward(CallbackInfoReturnable<Integer> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && !((LivingEntity) (Object) this).level().isClientSide && protect.isProtectionActive()) cir.setReturnValue(0);
    }

    /**
     * 動けないかを、倒れかけかどうか（vanilla と同じ決まり。倒れかけの判定はこの Mixin が実際の値で直す）で答える。
     * The King Sword は isImmobile を丸ごと置き換え、強制死亡の印（ID）が付いた相手を動けなくする（2026-10-25「止まります」）
     */
    @Inject(method = "isImmobile", at = @At("RETURN"), cancellable = true)
    private void automata_valkyries$guardIsImmobile(CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect != null && cir.getReturnValue() && protect.isProtectionActive() && !protect.isDying()) {
            cir.setReturnValue(((LivingEntity) (Object) this).isDeadOrDying());
        }
    }

    /**
     * 撃破を認めていないのに撃破の演出（tickDeath）を回されたら止める（{@link Eprotect#refuseDeathTick}）。
     * tickDeath を上書きする個体は、上書きの先頭で同じ物を呼ぶ（上書きされるとここは通らない）
     */
    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardTickDeath(CallbackInfo ci) {
        Eprotect protect = automata_valkyries$getProtect();
        if (protect == null) return;
        if (protect.refuseDeathTick()) {
            ci.cancel();
            return;
        }
        // vanilla の撃破の演出（20 Tick で消滅の煙を出して remove(KILLED)）を使う個体は、最後の Tick の前に演出の終わりを保護へ伝える。
        // 伝えないと消去が撃破の演出中として断られ続け、毎 Tick 煙を出して残った（2026-10-25、アカシック・プローブの直接対決で倒したとき）
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && protect.isDying() && self.deathTime + 1 >= 20) protect.finishDeath();
    }

    /**
     * Hyperlink や IMDS の Mixin/Transformer による getHealth() の -Infinity や 0 化をサニタイズする。
     * 正規に死亡していない（!protect.isDying() && !protect.isDefeated()）のに 0 以下の値に改変された場合、健全なHPを返す。
     * ただし、正規のダメージ処理中による最後の一撃（protect.isLegitimateFatalBlow()）の場合は 0.0F を通す。
     */
    /** HP の項目を同期データから抜かれていたら、vanilla が読む前に作り直す（{@link com.automatavalkyries.protect.DirectRead#ensureHealthItem}） */
    @Inject(method = "getHealth", at = @At("HEAD"))
    private void automata_valkyries$ensureHealthEntry(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof IEprotected) com.automatavalkyries.protect.DirectRead.ensureHealthItem((LivingEntity) (Object) this);
    }

    @Inject(method = "setHealth", at = @At("HEAD"))
    private void automata_valkyries$ensureHealthEntryBeforeWrite(float health, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected) com.automatavalkyries.protect.DirectRead.ensureHealthItem((LivingEntity) (Object) this);
    }

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
            } else if (!cir.getReturnValue() && (protect.canDie()
                    || (protect.isDefeated() && !(com.automatavalkyries.protect.DirectRead.health((LivingEntity) (Object) this) > 0.0F)))) {
                // 正規の最後の一撃で実際の HP が尽きたのに、他 MOD が getHealth を書き換えて最大 HP を返すと
                // 「死んでいない」と判定され、撃破できずに修復で HP が戻っていた（2026-10-05）。実際の HP で死を認める
                cir.setReturnValue(true);
            }
        }
    }

    private Eprotect automata_valkyries$getProtect() {
        return (Object) this instanceof IEprotected provider ? provider.getEprotect() : null;
    }
}
