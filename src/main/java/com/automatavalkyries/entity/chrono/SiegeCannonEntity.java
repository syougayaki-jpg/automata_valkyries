package com.automatavalkyries.entity.chrono;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * 侵食基地の防衛兵器「ヘビー・シージキャノン」（拡張仕様書 §10.2。2026-10-15 実装、数値は【仮】）: 動かない大口径の曲射砲。
 * {@value #MIN_RANGE}〜{@value #RANGE} m の相手へ、遮蔽物越しに山なりの砲弾を {@value #INTERVAL} Tick ごとに撃つ（{@value #DAMAGE}・半径 {@value #BLAST} の炸裂と吹き飛ばし）。
 * 落ちる所には撃った時から赤い印が出る（{@value #FLIGHT} Tick で落ちる）。相手の動きを少し読む。近すぎる相手は撃てない（懐に入るのが攻略）
 */
public class SiegeCannonEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final double RANGE = 40.0, MIN_RANGE = 8.0;
    public static final float DAMAGE = 18.0F, BLAST = 3.5F;
    public static final int INTERVAL = 100, FLIGHT = 40;
    private static final byte EVENT_FIRE = 60;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    public int fireTick = -100;

    public SiegeCannonEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MortarGoal(this));
        ChronoGazer.addTargets(this, RANGE);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void travel(Vec3 input) {
        super.travel(Vec3.ZERO);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_FIRE) fireTick = tickCount;
        else super.handleEntityEvent(id);
    }

    /** 撃つ: 砲口から、相手が FLIGHT Tick 後にいそうな所へ（読みは半分） */
    void fire(LivingEntity t) {
        Vec3 from = position().add(0, 1.8, 0);
        Vec3 to = t.position().add(t.getDeltaMovement().multiply(1, 0, 1).scale(FLIGHT * 0.5));
        MortarShellEntity.lob(this, from, to, FLIGHT, DAMAGE, BLAST);
        playSound(SoundEvents.GENERIC_EXPLODE, 1.2F, 0.7F);
        level().broadcastEntityEvent(this, EVENT_FIRE);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_EXPLODE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 相手の方へ砲を向け、届く距離なら（見えていなくても）曲射する */
    static class MortarGoal extends Goal {
        private final SiegeCannonEntity cannon;
        private int cooldown = 40;

        MortarGoal(SiegeCannonEntity cannon) {
            this.cannon = cannon;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return cannon.getTarget() != null && cannon.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = cannon.getTarget();
            if (t == null) return;
            cannon.getLookControl().setLookAt(t.getX(), t.getY() + 6.0, t.getZ(), 20.0F, 40.0F); // 砲は上を向く
            double d2 = cannon.distanceToSqr(t);
            if (--cooldown <= 0 && d2 <= RANGE * RANGE && d2 >= MIN_RANGE * MIN_RANGE) {
                cooldown = INTERVAL;
                cannon.fire(t);
            }
        }
    }
}
