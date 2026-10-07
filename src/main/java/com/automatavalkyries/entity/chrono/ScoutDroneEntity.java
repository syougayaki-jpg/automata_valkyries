package com.automatavalkyries.entity.chrono;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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
 * クロノゲイザーの汎用機「索敵ドローン」（全体仕様書 §4.1）。空を飛び、相手の斜め上 {@value #KEEP} m ほどに陣取って赤いレーザー弾を撃つ。
 * 相手を見つけると警報を鳴らし、半径 {@value #ALARM_RADIUS} m の仲間に相手を教える。数値は【仮】（2026-10-07 ユーザー指定「中盤の敵（兵姫前提）」）
 */
public class ScoutDroneEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final double KEEP = 9.0, ALARM_RADIUS = 24.0;
    public static final float BOLT_DAMAGE = 6.0F;
    public static final int FIRE_INTERVAL = 40;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private LivingEntity alarmedFor;

    public ScoutDroneEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 8;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.ATTACK_DAMAGE, BOLT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 32.0);
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
        goalSelector.addGoal(2, new HoverShootGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        LivingEntity target = getTarget();
        if (!level().isClientSide && target != null && target != alarmedFor && target.isAlive()) {
            alarmedFor = target;
            alarm(target);
        }
    }

    /** 警報: 甲高い音と、近くの仲間に相手を教える。近くのプレイヤーには知らせる */
    private void alarm(LivingEntity target) {
        playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 1.5F, 1.8F);
        playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 1.5F, 1.4F);
        ChronoGazer.alert(this, target, ALARM_RADIUS);
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(ALARM_RADIUS))) {
            if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.automata_valkyries.chrono_alarm").withStyle(ChatFormatting.RED), true);
        }
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
        return SoundEvents.BEE_LOOP;
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
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 相手の斜め上に陣取り（回り込みながら）、見えていれば一定の間隔で撃つ */
    static class HoverShootGoal extends Goal {
        private final ScoutDroneEntity drone;
        private int cooldown;
        private float orbit;

        HoverShootGoal(ScoutDroneEntity drone) {
            this.drone = drone;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = drone.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            cooldown = 20;
            orbit = drone.random.nextFloat() * 6.28F;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = drone.getTarget();
            if (t == null) return;
            drone.getLookControl().setLookAt(t, 30.0F, 30.0F);
            orbit += 0.02F;
            Vec3 spot = t.position().add(Math.cos(orbit) * KEEP * 0.8, 4.0 + Math.sin(orbit * 2.0F) * 0.8, Math.sin(orbit) * KEEP * 0.8);
            if (drone.tickCount % 10 == 0 || drone.getNavigation().isDone()) {
                drone.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
            }
            if (--cooldown <= 0 && drone.hasLineOfSight(t) && drone.distanceToSqr(t) < 24.0 * 24.0) {
                cooldown = FIRE_INTERVAL;
                ChronoBolt.fire(drone, t, (float) drone.getAttributeValue(Attributes.ATTACK_DAMAGE), 0.0F, 1.6F, 2.0F);
                drone.playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 1.8F);
            }
        }
    }
}
