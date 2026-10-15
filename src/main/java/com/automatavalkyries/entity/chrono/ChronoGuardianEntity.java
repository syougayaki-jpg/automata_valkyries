package com.automatavalkyries.entity.chrono;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * 侵食基地の防衛兵器「クロノ・ガーディアン」（拡張仕様書 §10.2。2026-10-15 実装、数値は【仮】）: 基地の通路を巡回する 4 本脚の大型機。
 * 左腕の大盾で正面（前 120 度）からの攻撃・射撃を {@value #SHIELD_CUT} 割減らし（盾に当たると火花と金属音）、右腕の高圧スタンロッドで近くの相手を打つ
 * （{@value #ROD_DAMAGE}・鈍化 IV 1.5 秒・弱体化 3 秒）。重く、ほとんど押し返されない。背後・横から攻めるのが攻略
 */
public class ChronoGuardianEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final float ROD_DAMAGE = 10.0F, SHIELD_CUT = 0.8F;
    public static final double ROD_RANGE = 3.2;
    public static final int ROD_INTERVAL = 30;
    private static final byte EVENT_SWING = 60, EVENT_BLOCK = 61;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** クライアント: 最後に振った・盾で受けた Tick */
    public int swingTick = -100, blockTick = -100;

    public ChronoGuardianEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 25;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ATTACK_DAMAGE, ROD_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new RodGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6)); // 巡回
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this);
    }

    /** 攻撃の来た向きが盾の正面（体の向きから ±60 度）か */
    boolean facesShield(DamageSource source) {
        Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        if (from == null) return false;
        Vec3 to = from.position().subtract(position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0e-4) return false;
        Vec3 face = Vec3.directionFromRotation(0, yBodyRot);
        return face.dot(to.normalize()) > 0.5;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) && facesShield(source)) {
            amount *= 1.0F - SHIELD_CUT; // 大盾で受ける
            playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.7F);
            if (level() instanceof ServerLevel server) {
                Vec3 p = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(1.2)).add(0, 1.4, 0);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 10, 0.3, 0.4, 0.3, 0.2);
            }
            level().broadcastEntityEvent(this, EVENT_BLOCK);
        }
        return super.hurt(source, amount);
    }

    /** スタンロッドで打つ */
    void strike(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3), this);
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
        }
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY(0.6), t.getZ(), 16, 0.3, 0.4, 0.3, 0.3);
        playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.8F);
        level().broadcastEntityEvent(this, EVENT_SWING);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SWING) swingTick = tickCount;
        else if (id == EVENT_BLOCK) blockTick = tickCount;
        else super.handleEntityEvent(id);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.9F, 0.8F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 相手へ盾を向けて詰め、届けばスタンロッドで打つ */
    static class RodGoal extends Goal {
        private final ChronoGuardianEntity guardian;
        private int cooldown, repath;

        RodGoal(ChronoGuardianEntity guardian) {
            this.guardian = guardian;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return guardian.getTarget() != null && guardian.getTarget().isAlive();
        }

        @Override
        public void stop() {
            guardian.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = guardian.getTarget();
            if (t == null) return;
            guardian.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = guardian.distanceTo(t);
            if (--repath <= 0) {
                repath = 10;
                if (d > ROD_RANGE - 0.6) guardian.getNavigation().moveTo(t, 1.0);
                else guardian.getNavigation().stop();
            }
            if (--cooldown <= 0 && d <= ROD_RANGE) {
                cooldown = ROD_INTERVAL;
                guardian.strike(t);
            }
        }
    }
}
