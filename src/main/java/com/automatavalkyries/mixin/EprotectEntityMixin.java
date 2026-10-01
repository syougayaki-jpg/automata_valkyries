package com.automatavalkyries.mixin;

import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.KillModCounters;
import com.automatavalkyries.protect.PlayerWard;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackOp;
import com.automatavalkyries.protect.attack.ModBlacklist;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraftforge.common.util.ITeleporter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 不正消去のブロック。
 * 他 MOD による {@code remove()} / {@code discard()} / {@code setRemoved()} / {@code onRemovedFromWorld()}
 * の直接呼び出しを HEAD で完全拒絶する。
 */
@Mixin(value = Entity.class, priority = 100000)
public abstract class EprotectEntityMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        if (automata_valkyries$wardRefusesRemoval(reason)) {
            ci.cancel();
            return;
        }
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
                if ((Object) this instanceof LivingEntity living) {
                    KillModCounters.counterAll(living);
                }
            }
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (automata_valkyries$wardRefusesRemoval(reason)) {
            ci.cancel();
            return;
        }
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
                if ((Object) this instanceof LivingEntity living) {
                    KillModCounters.counterAll(living);
                }
            }
        }
    }

    /**
     * 守護の護符を持つ生きたプレイヤーを、他 MOD が消去（KILLED / DISCARDED）しようとしたら拒否する。
     * vanilla の消去（リスポーン時の古い個体の破棄・ログアウト・次元移動）は他 MOD の経路を通らないので通る
     */
    @Unique
    private boolean automata_valkyries$wardRefusesRemoval(Entity.RemovalReason reason) {
        Entity self = (Entity) (Object) this;
        return reason.shouldDestroy() && PlayerWard.isActive(self)
                && PlayerWard.refuseFromForeignMod(self, AttackOp.REMOVE + ":" + reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardDiscard(CallbackInfo ci) {
        if (automata_valkyries$wardRefusesRemoval(Entity.RemovalReason.DISCARDED)) {
            ci.cancel();
            return;
        }
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isGuardingRemoval()) {
                ci.cancel();
                AttackLog.refused((Entity) (Object) this, AttackOp.DISCARD);
                if ((Object) this instanceof LivingEntity living) {
                    KillModCounters.counterAll(living);
                }
            }
        }
    }

    /**
     * IMDS や Hyperlink が直接 onRemovedFromWorld() を呼んでワールド・トラッキングから物理除外するのを防ぐ
     */
    @Inject(method = "onRemovedFromWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private void automata_valkyries$guardOnRemovedFromWorld(CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            Entity self = (Entity) (Object) this;
            Entity.RemovalReason reason = com.automatavalkyries.protect.DirectRead.removalReason(self);
            if (protect != null && protect.isGuardingRemoval() && (reason == null || Eprotect.isIllegalRemoval(self, reason))) {
                ci.cancel();
                // 消去理由が無いのはチャンクの可視性低下（正規の追跡終了）。拒否はするが攻撃としては記録しない
                if (reason != null) AttackLog.refused(self, AttackOp.REMOVED_FROM_WORLD);
                if (self instanceof LivingEntity living) {
                    KillModCounters.counterAll(living);
                }
            }
        }
    }

    /** IMDS 等による -Infinity / 奈落落としテレポートをブロック */
    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardTeleport(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (!Eprotect.isSafePosition((Entity) (Object) this, x, y, z)) {
                    ci.cancel();
                    AttackLog.refused((Entity) (Object) this, AttackOp.UNSAFE_POSITION);
                }
            }
        }
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardSetPos(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.isProtectionActive()) {
                if (!Eprotect.isSafePosition((Entity) (Object) this, x, y, z)) {
                    ci.cancel();
                    AttackLog.refused((Entity) (Object) this, AttackOp.UNSAFE_POSITION);
                }
            }
        }
    }

    /**
     * setPos を経ない座標の直書き（IMDS の -Infinity テレポートは setPosRaw も直接呼ぶ）。
     * ここを通さないと区画・チャンク座標まで -Infinity 由来の値になり、保存時に壊れた座標で書き出される。
     */
    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardSetPosRaw(double x, double y, double z, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (PlayerWard.isActive(entity)) {
            // 守護の護符: 他 MOD による遠くへの強制移動・ワールド外への飛ばし。通常の移動は短いので距離で先に弾き、スタックを辿らない
            boolean unsafe = !Eprotect.isSafePosition(entity, x, y, z);
            double limit = EprotectConfig.get(EprotectConfig.BLACKLIST_FORCED_RELOCATION_DISTANCE);
            if ((unsafe || entity.distanceToSqr(x, y, z) > limit * limit)
                    && PlayerWard.refuseFromForeignMod(entity, unsafe ? AttackOp.UNSAFE_POSITION : AttackOp.FORCED_RELOCATION)) {
                ci.cancel();
            }
            return;
        }
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect == null || !protect.isProtectionActive()) return;
            Entity self = (Entity) (Object) this;
            if (!Eprotect.isSafePosition(self, x, y, z)) {
                ci.cancel();
                AttackLog.refused(self, AttackOp.UNSAFE_POSITION);
                return;
            }
            // ブラックリストの MOD による遠くへの強制移動（setPos / teleportTo / 同じ次元内の移動はすべてここを通る）。
            // 通常の移動は 1 Tick に数ブロックなので、距離の条件で先に弾いてスタックを辿らない
            if (ModBlacklist.isActive() && !self.level().isClientSide) {
                double limit = EprotectConfig.get(EprotectConfig.BLACKLIST_FORCED_RELOCATION_DISTANCE);
                if (self.distanceToSqr(x, y, z) > limit * limit && protect.blacklistedCaller(AttackOp.FORCED_RELOCATION, false)) {
                    ci.cancel();
                }
            }
        }
    }

    /**
     * 次元移動（Forge の changeDimension）。移動先に複製を加えてから元の個体を消すので、途中では止められない。入口で判定し、
     * 通したものは最後まで通す（途中の setRemoved(CHANGED_DIMENSION) を止めない）。
     */
    @Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void automata_valkyries$guardChangeDimension(ServerLevel destination, ITeleporter teleporter, CallbackInfoReturnable<Entity> cir) {
        Eprotect protect = automata_valkyries$protect();
        if (protect == null) return;
        if (protect.blacklistedCaller(AttackOp.DIMENSION_CHANGE, true)) {
            cir.setReturnValue(null);
            return;
        }
        protect.approveDimensionChange(true);
    }

    @Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"), remap = false)
    private void automata_valkyries$endChangeDimension(ServerLevel destination, ITeleporter teleporter, CallbackInfoReturnable<Entity> cir) {
        Eprotect protect = automata_valkyries$protect();
        if (protect != null) protect.approveDimensionChange(false);
    }

    /** 別の次元への teleportTo（/tp 等）。同じ次元内は setPosRaw で見る */
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$guardTeleportToLevel(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relative,
                                                          float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$protect();
        if (protect == null || level == ((Entity) (Object) this).level()) return;
        if (protect.blacklistedCaller(AttackOp.DIMENSION_CHANGE, true)) {
            cir.setReturnValue(false);
            return;
        }
        protect.approveDimensionChange(true);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("RETURN"))
    private void automata_valkyries$endTeleportToLevel(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relative,
                                                        float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        Eprotect protect = automata_valkyries$protect();
        if (protect != null) protect.approveDimensionChange(false);
    }

    @Unique
    @Nullable
    private Eprotect automata_valkyries$protect() {
        return (Object) this instanceof IEprotected provider ? provider.getEprotect() : null;
    }

    /**
     * Hyperlink の isNovelized() を保護対象に対して常に false にする（演出中も含めノベル化によるTick停止を絶対阻止）
     */
    @Inject(method = "isNovelized", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardIsNovelized(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IEprotected) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Hyperlink の setNovelized() を保護対象に対して完全に無視する
     */
    @Inject(method = "setNovelized", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardSetNovelized(CallbackInfo ci) {
        if ((Object) this instanceof IEprotected) {
            ci.cancel();
            AttackLog.refused((Entity) (Object) this, AttackOp.NOVELIZE);
        }
    }

    /**
     * Hyperlink の novelRemove(Entity.RemovalReason) を保護対象に対して完全にブロックする
     */
    @Inject(method = "novelRemove", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardNovelRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null && protect.shouldBlockRemoval(reason)) {
                ci.cancel();
            }
        }
    }

    /**
     * Hyperlink の novelize(LivingEntity) を保護対象に対して完全にブロックする
     */
    @Inject(method = "novelize(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void automata_valkyries$guardNovelize(LivingEntity living, CallbackInfo ci) {
        if ((Object) this instanceof IEprotected) {
            ci.cancel();
            AttackLog.refused((Entity) (Object) this, AttackOp.NOVELIZE);
        }
    }
}
