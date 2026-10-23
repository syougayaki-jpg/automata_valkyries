package com.automatavalkyries.mixin;

import com.automatavalkyries.annihilate.FlagKillPredicates;
import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.weapon.Overload;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * オーバーロード兵装の「隔絶ダメージ（{@link IsolationDamageEngine}）」を支える最優先（{@code priority = 1}）Mixin 群。
 * <ul>
 *   <li>{@link Living}: {@code hurt()} の先頭と末尾で隔絶侵食を捕捉・確定し、{@code getHealth}/{@code setHealth}/{@code isAlive}/{@code isDeadOrDying} を侵食上限にクランプする。</li>
 *   <li>{@link SynchedData}: SuperSteve の {@code HurtSynchedEntityData} 相当の {@code SynchedEntityData.get/set} クランプをエージェント無しで実現する。</li>
 *   <li>{@link PlayerAttack}: {@code hurt()} を独自オーバーライドして {@code super.hurt()} を呼ばない特殊ボスに対しても隔絶ダメージを確実に到達させる。</li>
 * </ul>
 */
public final class IsolationDamageMixin {

    private IsolationDamageMixin() {}

    @Mixin(value = LivingEntity.class, priority = 1)
    public abstract static class Living {

        @Inject(method = "hurt", at = @At("HEAD"))
        private void automata_valkyries$isolationHurtHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
            IsolationDamageEngine.onHurtHead((LivingEntity) (Object) this, source, amount);
        }

        @Inject(method = "hurt", at = @At("RETURN"), cancellable = true)
        private void automata_valkyries$isolationHurtReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
            boolean original = cir.getReturnValueZ();
            boolean updated = IsolationDamageEngine.onHurtReturn((LivingEntity) (Object) this, original);
            if (updated != original) {
                cir.setReturnValue(updated);
            }
        }

        @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$isolationGetHealthHead(CallbackInfoReturnable<Float> cir) {
            if (IsolationDamageEngine.isIsolatedDead((LivingEntity) (Object) this)) {
                cir.setReturnValue(0.0F);
            }
        }

        @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
        private void automata_valkyries$isolationGetHealthReturn(CallbackInfoReturnable<Float> cir) {
            float cap = IsolationDamageEngine.getClampedMaxHealth((LivingEntity) (Object) this);
            if (!Float.isNaN(cap)) {
                float current = cir.getReturnValueF();
                if (!Float.isFinite(current) || current > cap) {
                    cir.setReturnValue(cap);
                }
            }
        }

        @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
        private float automata_valkyries$isolationSetHealth(float health) {
            float cap = IsolationDamageEngine.getClampedMaxHealth((LivingEntity) (Object) this);
            if (!Float.isNaN(cap)) {
                if (!Float.isFinite(health) || health > cap) {
                    return cap;
                }
            }
            return health;
        }

        @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$isolationIsDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
            if (IsolationDamageEngine.isIsolatedDead((LivingEntity) (Object) this)) {
                cir.setReturnValue(true);
            }
        }

        @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$isolationIsAlive(CallbackInfoReturnable<Boolean> cir) {
            if (IsolationDamageEngine.isIsolatedDead((LivingEntity) (Object) this)) {
                cir.setReturnValue(false);
            }
        }

        @Inject(method = "getEffect", at = @At("RETURN"), cancellable = true)
        private void automata_valkyries$preventNightVisionNpe(MobEffect effect, CallbackInfoReturnable<MobEffectInstance> cir) {
            if (effect == MobEffects.NIGHT_VISION && cir.getReturnValue() == null) {
                LivingEntity self = (LivingEntity) (Object) this;
                if (self.level().isClientSide() && self.hasEffect(MobEffects.NIGHT_VISION)) {
                    cir.setReturnValue(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0, false, false, false));
                }
            }
        }

        @Unique
        private int automata_valkyries$preTickDeathTime;

        @Inject(method = "tick", at = @At("HEAD"))
        private void automata_valkyries$isolationTickHead(CallbackInfo ci) {
            if ((Object) this instanceof Player player) {
                IsolationDamageEngine.suppressPlayerActiveDefensesOnTick(player);
            }
        }

        @Inject(method = "baseTick", at = @At("HEAD"))
        private void automata_valkyries$isolationBaseTickHead(CallbackInfo ci) {
            if ((Object) this instanceof Player player) {
                IsolationDamageEngine.suppressPlayerActiveDefensesOnTick(player);
            }
            automata_valkyries$preTickDeathTime = ((LivingEntity) (Object) this).deathTime;
        }

        @Inject(method = "baseTick", at = @At("TAIL"))
        private void automata_valkyries$isolationBaseTickTail(CallbackInfo ci) {
            IsolationDamageEngine.onLivingBaseTickTail((LivingEntity) (Object) this, automata_valkyries$preTickDeathTime);
        }
    }

    @Mixin(value = SynchedEntityData.class, priority = 1)
    public abstract static class SynchedData {

        @Shadow @Final private Entity entity;

        @ModifyVariable(
                method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
                at = @At("HEAD"),
                argsOnly = true
        )
        @SuppressWarnings("unchecked")
        private <T> T automata_valkyries$clampSynchedSet(T value, EntityDataAccessor<T> key, T originalValue, boolean force) {
            if (this.entity instanceof LivingEntity living) {
                float cap = IsolationDamageEngine.getClampedMaxHealth(living);
                if (!Float.isNaN(cap) && IsolationDamageEngine.isHealthAccessor(living, key)) {
                    if (value instanceof Float fv && (!Float.isFinite(fv) || fv > cap)) {
                        return (T) Float.valueOf(cap);
                    }
                    if (value instanceof Double dv && (!Double.isFinite(dv) || dv > cap)) {
                        return (T) Double.valueOf(cap);
                    }
                }
            }
            return value;
        }

        @Inject(method = "get", at = @At("RETURN"), cancellable = true)
        @SuppressWarnings("unchecked")
        private <T> void automata_valkyries$clampSynchedGet(EntityDataAccessor<T> key, CallbackInfoReturnable<T> cir) {
            if (this.entity instanceof LivingEntity living) {
                float cap = IsolationDamageEngine.getClampedMaxHealth(living);
                if (!Float.isNaN(cap) && IsolationDamageEngine.isHealthAccessor(living, key)) {
                    Object val = cir.getReturnValue();
                    if (val instanceof Float fv && (!Float.isFinite(fv) || fv > cap)) {
                        cir.setReturnValue((T) Float.valueOf(cap));
                    } else if (val instanceof Double dv && (!Double.isFinite(dv) || dv > cap)) {
                        cir.setReturnValue((T) Double.valueOf(cap));
                    }
                }
            }
        }
    }

    @Mixin(value = Player.class, priority = 1)
    public abstract static class PlayerAttack {

        @Unique
        private Float automata_valkyries$preAttackIsolationTotal;

        @Inject(method = "attack", at = @At("HEAD"))
        private void automata_valkyries$onAttackHead(Entity target, CallbackInfo ci) {
            Player self = (Player) (Object) this;
            if (!self.level().isClientSide() && target instanceof LivingEntity victim && Overload.holding(self)) {
                IsolationDamageEngine.IsolationState state = IsolationDamageEngine.getState(victim);
                automata_valkyries$preAttackIsolationTotal = state != null ? state.flatIsolation : 0.0F;
            } else {
                automata_valkyries$preAttackIsolationTotal = null;
            }
        }

        @Inject(method = "attack", at = @At("RETURN"))
        private void automata_valkyries$onAttackReturn(Entity target, CallbackInfo ci) {
            Float preTotal = automata_valkyries$preAttackIsolationTotal;
            automata_valkyries$preAttackIsolationTotal = null;
            if (preTotal == null) return;
            Player self = (Player) (Object) this;
            if (!self.level().isClientSide() && target instanceof LivingEntity victim && Overload.holding(self)) {
                IsolationDamageEngine.IsolationState afterState = IsolationDamageEngine.getState(victim);
                float afterTotal = afterState != null ? afterState.flatIsolation : 0.0F;
                // もし対象が hurt() をサブクラスで握り潰して LivingEntity.hurt に到達しなかった場合でも、隔絶ダメージを強制直撃させる
                if (afterTotal <= preTotal) {
                    float baseAttack = (float) self.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    if (baseAttack <= 0.0F || !Float.isFinite(baseAttack)) {
                        baseAttack = 2.0F;
                    }
                    IsolationDamageEngine.ensureIsolatedAttack(self, victim, self.damageSources().playerAttack(self), baseAttack);
                }
            }
        }

        @Inject(method = "getInventory", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$maskInventoryWhenDefenseBroken(CallbackInfoReturnable<Inventory> cir) {
            Player self = (Player) (Object) this;
            if (IsolationDamageEngine.shouldMaskInventoryForForeignCaller(self)) {
                cir.setReturnValue(IsolationDamageEngine.getEmptyMaskInventory(self));
            }
        }

        @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$maskItemBySlotWhenDefenseBroken(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
            Player self = (Player) (Object) this;
            if (IsolationDamageEngine.shouldMaskInventoryForForeignCaller(self)) {
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }

    @Mixin(value = ItemStack.class, priority = 1)
    public abstract static class Stack {

        @Inject(method = "inventoryTick", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$suppressDefensiveItemTick(Level level, Entity entity, int slotId, boolean isCurrentItem, CallbackInfo ci) {
            if (entity instanceof Player player
                    && IsolationDamageEngine.shouldSuppressPlayerInventoryDefense(player)
                    && FlagKillPredicates.isDefensiveItem((ItemStack) (Object) this)) {
                ci.cancel();
            }
        }
    }
}
