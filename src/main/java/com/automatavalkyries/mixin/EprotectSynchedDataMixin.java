package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.EprotectHyperlinkCounter;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hyperlink の DATA_NOVELIZED 注入を完全に遮断する Mixin。
 * 保護対象エンティティへの DATA_NOVELIZED 書き込みを拒絶し、読み出し時は常に false を返却する。
 */
@Mixin(SynchedEntityData.class)
public abstract class EprotectSynchedDataMixin {

    @Shadow @Final private Entity entity;

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", at = @At("HEAD"), cancellable = true)
    private <T> void automata_valkyries$guardSet(EntityDataAccessor<T> key, T value, boolean force, CallbackInfo ci) {
        // 守護の護符を持つプレイヤー: 即死の印（魂ダメージ等）を立てる・増やす書き込みだけ拒否する
        if (!(this.entity instanceof IEprotected) && com.automatavalkyries.protect.PlayerWard.isActive(this.entity)) {
            StateScanner.DataKey wardKey = StateScanner.injectedDataKey(key);
            if (wardKey != null && FlagDictionary.harmfulOnProtected(wardKey.name()) && automata_valkyries$worsens(key, value)) {
                ci.cancel();
                com.automatavalkyries.protect.attack.AttackLog.refused(this.entity,
                        com.automatavalkyries.protect.attack.AttackOp.SYNCHED_DATA + ":" + wardKey.name(), wardKey.mod());
            }
            return;
        }
        if (this.entity instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (Boolean.TRUE.equals(value) && EprotectHyperlinkCounter.isNovelizedAccessor(key)) {
                    ci.cancel();
                    com.automatavalkyries.protect.attack.AttackLog.refused(this.entity,
                            com.automatavalkyries.protect.attack.AttackOp.NOVELIZE + ":data");
                    return;
                }
                // 他 MOD が vanilla に足した同期データのうち、フラグ辞書で死亡・停止・迂回の印と分かるものを悪化させる書き込み
                StateScanner.DataKey dataKey = StateScanner.injectedDataKey(key);
                if (dataKey != null && FlagDictionary.harmfulOnProtected(dataKey.name()) && automata_valkyries$worsens(key, value)) {
                    ci.cancel();
                    com.automatavalkyries.protect.attack.AttackLog.refused(this.entity,
                            com.automatavalkyries.protect.attack.AttackOp.SYNCHED_DATA + ":" + dataKey.name(), dataKey.mod());
                }
            }
        }
    }

    /** 印を立てる・増やす書き込みか（下げる・外す書き込みは通す） */
    @org.spongepowered.asm.mixin.Unique
    private <T> boolean automata_valkyries$worsens(EntityDataAccessor<T> key, T value) {
        if (value instanceof Boolean flag) return flag;
        if (!(value instanceof Number number)) return false;
        double next = number.doubleValue();
        if (!Double.isFinite(next)) return true;
        try {
            Object current = ((SynchedEntityData) (Object) this).get(key);
            return current instanceof Number now ? next > now.doubleValue() : next != 0.0D;
        } catch (Throwable t) {
            return next != 0.0D;
        }
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private <T> void automata_valkyries$guardGet(EntityDataAccessor<T> key, CallbackInfoReturnable<T> cir) {
        if (this.entity instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (EprotectHyperlinkCounter.isNovelizedAccessor(key)) {
                    //noinspection unchecked
                    cir.setReturnValue((T) Boolean.FALSE);
                }
            }
        }
    }
}
