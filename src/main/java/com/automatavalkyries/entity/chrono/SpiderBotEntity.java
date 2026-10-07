package com.automatavalkyries.entity.chrono;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * クロノゲイザーの汎用機「自爆クモボット」（全体仕様書 §4.1）。6 本脚で速く走り、壁も登る。相手に近づくとコアが赤く脈打ち、
 * {@value #FUSE} Tick で自爆する（爆発の強さ {@value #POWER}、地形を壊すかは mobGriefing に従う。2026-10-07 ユーザー指定）。数値は【仮】
 */
public class SpiderBotEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final int FUSE = 30;
    public static final float POWER = 3.0F;
    private static final double TRIGGER = 3.0, ABORT = 7.0;

    private static final EntityDataAccessor<Integer> SWELL_DIR = SynchedEntityData.defineId(SpiderBotEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private int swell, swellO;

    public SpiderBotEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SWELL_DIR, -1);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new FuseGoal(this));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this);
    }

    /** 壁に当たっていれば登る（クモと同じ） */
    @Override
    public boolean onClimbable() {
        return horizontalCollision;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return true; // 噛みつかない（近づいたら自爆の導火線に任せる）
    }

    public void setSwellDir(int dir) {
        entityData.set(SWELL_DIR, dir);
    }

    /** 導火線の進み 0〜1（描画でコアを脈打たせる） */
    public float getSwelling(float partialTick) {
        return Mth.lerp(partialTick, (float) swellO, (float) swell) / (FUSE - 2);
    }

    @Override
    public void tick() {
        if (isAlive()) {
            swellO = swell;
            int dir = entityData.get(SWELL_DIR);
            if (dir > 0 && swell == 0) playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 1.6F);
            swell = Mth.clamp(swell + dir, 0, FUSE);
            if (swell >= FUSE && !level().isClientSide) explode();
        }
        super.tick();
    }

    private void explode() {
        dead = true;
        level().explode(this, getX(), getY(), getZ(), POWER, Level.ExplosionInteraction.MOB);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 30, 1.0, 0.5, 1.0, 0.3);
        }
        discard();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
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
    public float getVoicePitch() {
        return 1.6F + random.nextFloat() * 0.2F;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 相手が近ければ導火線を進め、離れたら戻す（クリーパーと同じ） */
    static class FuseGoal extends Goal {
        private final SpiderBotEntity bot;
        private LivingEntity target;

        FuseGoal(SpiderBotEntity bot) {
            this.bot = bot;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = bot.getTarget();
            return bot.swell > 0 || (t != null && bot.distanceToSqr(t) < TRIGGER * TRIGGER);
        }

        @Override
        public void start() {
            bot.getNavigation().stop();
            target = bot.getTarget();
        }

        @Override
        public void stop() {
            target = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (target == null || !target.isAlive() || bot.distanceToSqr(target) > ABORT * ABORT || !bot.getSensing().hasLineOfSight(target)) {
                bot.setSwellDir(-1);
            } else {
                bot.setSwellDir(1);
            }
        }
    }
}
