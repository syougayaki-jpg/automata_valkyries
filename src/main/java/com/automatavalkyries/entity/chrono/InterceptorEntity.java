package com.automatavalkyries.entity.chrono;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * 浮遊要塞（T3）の雑魚「迎撃機 ストライカー」（2026-10-19 ユーザー指定「既存＋新しい 2 種」。数値は【仮】）。
 * 三角翼の無人戦闘機。相手の上を半径 {@value #ORBIT} m で回り、{@value #DIVE_INTERVAL} Tick ごとに相手の脇をかすめる急降下をして、その間に 3 連射（1 発 {@value #BOLT}）。
 * 空を飛ぶ相手（飛翔機・エリトラ）にも追いつく速さ
 */
public class InterceptorEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final double ORBIT = 11.0;
    public static final float BOLT = 4.0F;
    public static final int DIVE_INTERVAL = 70, DIVE_TICKS = 24;
    public static final byte EVENT_DIVE = 84;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    public int diveTick = -100;

    public InterceptorEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 30, true);
        this.xpReward = 15;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FLYING_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, BOLT)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new StrafeGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this, 24.0);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_DIVE) diveTick = tickCount;
        else super.handleEntityEvent(id);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ELYTRA_FLYING;
    }

    @Override
    protected float getSoundVolume() {
        return 0.25F;
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

    /** 相手の上を回り、ときどき脇をかすめる急降下と 3 連射 */
    static class StrafeGoal extends Goal {
        private final InterceptorEntity jet;
        private int diveCd, diving;
        private float orbit;
        private Vec3 pass = Vec3.ZERO;

        StrafeGoal(InterceptorEntity jet) {
            this.jet = jet;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = jet.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            diveCd = 30 + jet.random.nextInt(30);
            orbit = jet.random.nextFloat() * 6.28F;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = jet.getTarget();
            if (t == null) return;
            jet.getLookControl().setLookAt(t, 40.0F, 40.0F);
            if (diving > 0) {
                diving--;
                jet.getMoveControl().setWantedPosition(pass.x, pass.y, pass.z, 1.6);
                if (diving % 5 == 0 && diving >= 8 && jet.hasLineOfSight(t)) {
                    ChronoBolt.fire(jet, t, BOLT, 0.0F, 2.0F, 1.5F);
                    jet.playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 2.0F);
                }
                return;
            }
            orbit += 0.035F;
            Vec3 spot = t.position().add(Math.cos(orbit) * ORBIT, 6.0 + Math.sin(orbit * 3.0F), Math.sin(orbit) * ORBIT);
            if (jet.tickCount % 8 == 0 || jet.getNavigation().isDone()) jet.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.2);
            if (--diveCd <= 0 && jet.distanceToSqr(t) < 28.0 * 28.0) {
                diveCd = DIVE_INTERVAL + jet.random.nextInt(20);
                diving = DIVE_TICKS;
                Vec3 across = t.position().subtract(jet.position()).multiply(1, 0, 1);
                if (across.lengthSqr() < 1.0e-4) across = new Vec3(1, 0, 0);
                pass = t.position().add(across.normalize().scale(7.0)).add(0, 1.5, 0); // 相手の向こう側の低い所へ抜ける
                jet.playSound(SoundEvents.PHANTOM_SWOOP, 1.2F, 1.4F);
                jet.level().broadcastEntityEvent(jet, EVENT_DIVE);
            }
        }
    }
}
