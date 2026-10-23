package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.annihilate.SpawnBanZone;
import com.automatavalkyries.protect.EprotectCompat;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Omni-Mobs への対策。
 * <ul>
 *   <li>抹消対象: Omni-Mobs の checkDespawnImmunity を無効化し、区画・一覧・追跡からの正規抹消を許可する</li>
 *   <li>保護対象（兵姫）: Metapotent Flashfur の即死（forceHurt → eraseEntity）と、Omni-Mobs 共通の強制消去・強制座標・
 *       強制 HP の各ユーティリティを入口で止める。強制ダメージは通常の被弾に置き換え、eprotect のダメージ上限に任せる</li>
 * </ul>
 * eraseEntity は removalReason の直書き・各一覧からの直接削除・levelCallback の差し替え・座標 1.0E9 への飛ばしを行い、
 * Flashfur の攻撃のたびに繰り返されるので、事後の修復（入り直し）では追いつかず退避に追い込まれていた。
 */
public final class OmniMobsMixins {

    private OmniMobsMixins() {}

    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.coremod.CoreModHooks", remap = false)
    public abstract static class Hooks {
        @Inject(method = "checkDespawnImmunity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardDespawnImmunity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (com.automatavalkyries.protect.SpecificCountermeasures.annihilatedForCompat(entity)) {
                cir.setReturnValue(false);
            }
        }
    }

    /** Metapotent Flashfur 本体（エンティティではなく、Omni-Mobs が独自に Tick する管理オブジェクト） */
    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfur", remap = false)
    public abstract static class Flashfur {
        @Shadow private boolean removed;

        /** 抹消済み（removed=true）の本体の Tick 処理および再召喚を完全遮断 */
        @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardTick(CallbackInfo ci) {
            if (this.removed) {
                ci.cancel();
            }
        }

        /** 抹消済みの本体のカスタム Tick（全消去・攻撃ルーチン）を完全遮断 */
        @Inject(method = "customTick()V", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardCustomTick(CallbackInfo ci) {
            if (this.removed) {
                ci.cancel();
            }
        }

        /** Omni-Mobs 自身の「免除」判定（自 MOD のボスだけが対象）に兵姫も含める */
        @Inject(method = "isImmune(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$immune(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(true);
        }

        @Inject(method = "instantKill(Lnet/minecraft/world/entity/LivingEntity;F)V", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardInstantKill(LivingEntity target, float amount, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(target, "omnimobs:instantKill")) {
                ci.cancel();
                // 通常の被弾に置き換えるのは被弾量の上限がある保護対象だけ（守護の護符のプレイヤーは上限が無いので止めるだけ）
                if (target instanceof IEprotected) target.hurt(target.damageSources().generic(), amount);
            }
        }

        @Inject(method = "eraseEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardErase(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "omnimobs:erase")) ci.cancel();
        }
    }

    /** Omni-Mobs 共通のユーティリティ（Flashfur 以外の Omni-Mobs のボスも使う） */
    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.util.EntityUtil", remap = false)
    public abstract static class Util {
        /** 強制ダメージ（防具・無敵時間・イベントを無視して HP を直接削る）を通常の被弾に置き換える。量は eprotect の上限で抑える */
        @Inject(method = "forceHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Lflashfur/omnimobs/util/ForceDamageSource;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardForceHurt(LivingEntity target, LivingEntity attacker,
                                                            @Coerce Object source, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(target, "omnimobs:forceHurt")) {
                ci.cancel();
                if (target instanceof IEprotected) {
                    target.hurt(attacker != null ? target.damageSources().mobAttack(attacker) : target.damageSources().generic(), Float.MAX_VALUE);
                }
            }
        }

        @Inject(method = {
                "forceRemove(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
                "forceRemoveNoLeaveLevelCalls(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
                "forceSetRemoved(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardRemove(Entity entity, Entity.RemovalReason reason, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "omnimobs:remove")) ci.cancel();
        }

        @Inject(method = "forceRemoveNoPacket(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;Z)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardRemoveNoPacket(Entity entity, Entity.RemovalReason reason, boolean flag, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "omnimobs:removeNoPacket")) ci.cancel();
        }

        @Inject(method = "runRemoveMethods(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardRemoveMethods(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "omnimobs:removeMethods")) ci.cancel();
        }

        /** 座標の直書き（1.0E9 などワールド外への飛ばし） */
        @Inject(method = "forceSetPos(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSetPos(Entity entity, Vec3 pos, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "omnimobs:setPos")) ci.cancel();
        }

        /** HP のフィールド・同期データ・setHealth 系の直書き */
        @Inject(method = {
                "forceSetHealth(Lnet/minecraft/world/entity/LivingEntity;F)V",
                "setAllSyncedHealthData(Lnet/minecraft/world/entity/LivingEntity;F)V",
                "setAllHealthFields(Lnet/minecraft/world/entity/LivingEntity;F)V",
                "runSetHealthMethods(Lnet/minecraft/world/entity/LivingEntity;F)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSetHealth(LivingEntity entity, float health, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "omnimobs:setHealth")) ci.cancel();
        }

        /** 抹消済み個体の forceAddEntity による復活・再召喚を完全遮断 */
        @Inject(method = "forceAddEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardForceAddEntity(Entity entity, net.minecraft.world.level.Level level, CallbackInfo ci) {
            if (entity == null) return;
            if (AnnihilationExecutionAgent.isAnnihilated(entity)) {
                ci.cancel();
                entity.discard();
                return;
            }
            if (entity.getClass().getName().contains("MetapotentFlashfurEntity")) {
                try {
                    java.lang.reflect.Field f = entity.getClass().getDeclaredField("metapotentFlashfur");
                    f.setAccessible(true);
                    Object core = f.get(entity);
                    if (core != null) {
                        java.lang.reflect.Field rf = core.getClass().getDeclaredField("removed");
                        rf.setAccessible(true);
                        boolean removed = rf.getBoolean(core);
                        java.lang.reflect.Field idf = core.getClass().getDeclaredField("id");
                        idf.setAccessible(true);
                        UUID id = (UUID) idf.get(core);
                        if (removed || (id != null && AnnihilationExecutionAgent.isAnnihilated(id))) {
                            ci.cancel();
                            entity.discard();
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    /** Metapotent Flashfur Proxy エンティティ（ワールド上に現れる影）の完全制圧 */
    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurEntity", remap = false)
    public abstract static class FlashfurProxy {
        @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardProxyTick(CallbackInfo ci) {
            Entity self = (Entity) (Object) this;
            if (AnnihilationExecutionAgent.isAnnihilated(self)) {
                ci.cancel();
                self.discard();
            }
        }

        @Inject(method = "isAlive()Z", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardProxyIsAlive(CallbackInfoReturnable<Boolean> cir) {
            Entity self = (Entity) (Object) this;
            if (AnnihilationExecutionAgent.isAnnihilated(self) || self.isRemoved()) {
                cir.setReturnValue(false);
            }
        }
    }

    /** Metapotent Flashfur Level 管理（サーバースレッド Tick 前の残骸パージと EventBus 乗っ取り遮断） */
    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel", remap = false)
    public abstract static class Level {
        @Inject(method = "tickMetapotentFlashfurs()V", at = @At("HEAD"), require = 0)
        private static void automata_valkyries$cleanBeforeServerTick(CallbackInfo ci) {
            AnnihilationExecutionAgent.purgeDeadFlashfurs();
        }

        @Inject(method = "updateEventBus()V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardUpdateEventBus(CallbackInfo ci) {
            if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
            net.minecraftforge.eventbus.api.IEventBus bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
            if (bus != null && !bus.getClass().getName().endsWith("MetapotentFlashfurEventBus")) {
                // 正規の EventBus が稼働中なら MetapotentFlashfurEventBus への置換（ServerTickEvent/LevelTickEvent 握り潰し）を阻止
                ci.cancel();
            }
        }
    }

    /** Metapotent Flashfur レンダラー居残り完全阻止 */
    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurRenderer", remap = false)
    public abstract static class Renderer {
        @Inject(method = "render(Lflashfur/omnimobs/entities/metapotent_flashfur/MetapotentFlashfurEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$guardRender(
                @Coerce Object entityObj, float entityYaw, float partialTick,
                com.mojang.blaze3d.vertex.PoseStack poseStack,
                net.minecraft.client.renderer.MultiBufferSource bufferSource,
                int packedLight, CallbackInfo ci) {
            if (entityObj == null) {
                ci.cancel();
                return;
            }
            if (entityObj instanceof Entity entity) {
                if (entity.isRemoved() || AnnihilationExecutionAgent.isAnnihilated(entity.getUUID())) {
                    ci.cancel();
                    return;
                }
            }
            try {
                Field f = entityObj.getClass().getDeclaredField("metapotentFlashfur");
                f.setAccessible(true);
                Object mf = f.get(entityObj);
                if (mf == null) {
                    ci.cancel();
                    return;
                }
                Field rf = mf.getClass().getDeclaredField("removed");
                rf.setAccessible(true);
                if (rf.getBoolean(mf)) {
                    ci.cancel();
                    return;
                }
                Field idf = mf.getClass().getDeclaredField("id");
                idf.setAccessible(true);
                UUID id = (UUID) idf.get(mf);
                if (id != null && AnnihilationExecutionAgent.isAnnihilated(id)) {
                    ci.cancel();
                    return;
                }
            } catch (Throwable ignored) {}
        }
    }

    /** Omni-Mobs Anticheat AccessChecker の特権許可 */
    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.entities.anticheat.AccessChecker", remap = false)
    public abstract static class Access {
        @Inject(method = "checkAccess()Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$allowAccess(CallbackInfoReturnable<Boolean> cir) {
            if (AnnihilationExecutionAgent.isCleaningFlashfur()) {
                cir.setReturnValue(true);
            }
        }
    }
}
