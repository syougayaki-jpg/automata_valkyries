package com.automatavalkyries.entity.chrono;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
 * クロノゲイザーの汎用機「重歩行戦車」（全体仕様書 §4.1）。逆関節の 2 本脚で重く歩き、遠くの相手には炸裂する砲弾、
 * 近くの相手には足元を踏み鳴らす衝撃波（まわりをまとめて吹き飛ばす）。硬く、ほとんど押し返されない。数値は【仮】（2026-10-07「中盤の敵（兵姫前提）」）
 */
public class HeavyWalkerEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final float SHELL_DAMAGE = 16.0F, SHELL_BLAST = 2.5F;
    public static final double STOMP_RANGE = 3.5, CANNON_RANGE = 24.0;
    public static final int CANNON_INTERVAL = 80, STOMP_INTERVAL = 30;
    /** 撃った・踏み鳴らした合図（handleEntityEvent。描画の反動・沈み込み） */
    private static final byte EVENT_FIRE = 60, EVENT_STOMP = 61;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** クライアント: 最後に撃った・踏み鳴らした Tick（描画用） */
    public int fireTick = -100, stompTick = -100;

    public HeavyWalkerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 20;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new WalkerAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_FIRE) fireTick = tickCount;
        else if (id == EVENT_STOMP) stompTick = tickCount;
        else super.handleEntityEvent(id);
    }

    /** 砲弾を撃つ */
    void fireCannon(LivingEntity target) {
        ChronoBolt.fire(this, target, SHELL_DAMAGE, SHELL_BLAST, 1.3F, 3.0F);
        playSound(SoundEvents.GENERIC_EXPLODE, 0.8F, 1.6F);
        level().broadcastEntityEvent(this, EVENT_FIRE);
    }

    /** 踏み鳴らす: 半径 {@link #STOMP_RANGE} の相手に攻撃力分のダメージと、外へ吹き飛ばす */
    void stomp() {
        if (!(level() instanceof ServerLevel server)) return;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(STOMP_RANGE, 1.0, STOMP_RANGE), ChronoGazer::canHurt)) {
            if (living.distanceToSqr(this) > (STOMP_RANGE + 1.0) * (STOMP_RANGE + 1.0)) continue;
            living.hurt(damageSources().mobAttack(this), damage);
            Vec3 push = living.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.0);
            living.push(push.x, 0.45, push.z);
        }
        BlockState ground = server.getBlockState(blockPosition().below());
        if (!ground.isAir()) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 50, STOMP_RANGE * 0.5, 0.1, STOMP_RANGE * 0.5, 0.2);
        }
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 3, 1.2, 0.1, 1.2, 0.0);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_STOMP);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.6F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
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
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 遠ければ近づきながら砲撃、近ければ踏み鳴らす */
    static class WalkerAttackGoal extends Goal {
        private final HeavyWalkerEntity walker;
        private int cannon, stomp, repath;

        WalkerAttackGoal(HeavyWalkerEntity walker) {
            this.walker = walker;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = walker.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            cannon = 40;
            stomp = 10;
        }

        @Override
        public void stop() {
            walker.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = walker.getTarget();
            if (t == null) return;
            walker.getLookControl().setLookAt(t, 20.0F, 20.0F);
            double dist = walker.distanceTo(t);
            boolean sees = walker.getSensing().hasLineOfSight(t);
            if (--repath <= 0) {
                repath = 10;
                if (dist > 3.0 || !sees) walker.getNavigation().moveTo(t, 1.0);
                else walker.getNavigation().stop();
            }
            cannon--;
            stomp--;
            if (dist <= STOMP_RANGE && stomp <= 0) {
                stomp = STOMP_INTERVAL;
                walker.stomp();
            } else if (dist > STOMP_RANGE + 1.5 && dist <= CANNON_RANGE && sees && cannon <= 0) {
                cannon = CANNON_INTERVAL;
                walker.fireCannon(t);
            }
        }
    }
}
