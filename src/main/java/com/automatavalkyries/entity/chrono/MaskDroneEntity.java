package com.automatavalkyries.entity.chrono;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * クロノゲイザーの上位機「仮面の観測機」（2026-10-07 ユーザー指定「人のお面のような見た目で高威力の光線を放つ上位ドローン」）。
 * 宙に浮く人の顔のお面。{@value #CHARGE} Tick 溜める間は目が光って細い照準線が相手を追い、撃つ瞬間に向きを固定して
 * {@value #FIRE} Tick まっすぐ照射する（毎 Tick {@value #BEAM_DAMAGE}、無敵時間を貫く、ブロックで止まる、射程 {@value #RANGE} m）。
 * 照準線を見て射線から外れれば避けられる（兵姫は遠距離の脅威として横へステップする）。近すぎると下がる。数値は【仮】
 */
public class MaskDroneEntity extends Monster implements GeoEntity, ChronoGazer, RangedAttackMob {

    public static final int CHARGE = 40, FIRE = 20, COOLDOWN = 100;
    public static final float BEAM_DAMAGE = 4.0F;
    public static final double RANGE = 32.0, KEEP = 12.0, TOO_CLOSE = 6.0;
    public static final int IDLE = 0, CHARGING = 1, FIRING = 2;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MaskDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AIM_TARGET = SynchedEntityData.defineId(MaskDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> BEAM_DIR = SynchedEntityData.defineId(MaskDroneEntity.class, EntityDataSerializers.VECTOR3);
    private static final Vector3f RED = new Vector3f(1.0F, 0.17F, 0.29F);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** 今の状態になった Tick（クライアントでも数える） */
    public int stateStart;

    public MaskDroneEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 15;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, BEAM_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, IDLE);
        entityData.define(AIM_TARGET, -1);
        entityData.define(BEAM_DIR, new Vector3f(0, 0, 1));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key)) stateStart = tickCount;
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
        goalSelector.addGoal(2, new BeamGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this);
    }

    public int getState() {
        return entityData.get(STATE);
    }

    private void setState(int state) {
        entityData.set(STATE, state);
        stateStart = tickCount;
        setAggressive(state != IDLE); // 兵姫の「遠距離の脅威」の判定に使われる
    }

    /** 溜めている間の照準の相手（クライアント。いなければ null） */
    public Entity aimTarget() {
        int id = entityData.get(AIM_TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    public Vec3 beamDir() {
        Vector3f d = entityData.get(BEAM_DIR);
        return new Vec3(d.x(), d.y(), d.z());
    }

    /** 光線の出どころ（顔の真ん中） */
    public Vec3 beamOrigin() {
        return position().add(0, getBbHeight() * 0.55, 0);
    }

    /** 光線の先（ブロックで止まる所。射程まで） */
    public Vec3 beamEnd() {
        Vec3 from = beamOrigin();
        Vec3 to = from.add(beamDir().scale(RANGE));
        var hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** 照射中の 1 Tick: 線に触れている相手に当てる */
    private void burn() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = beamOrigin(), to = beamEnd();
        Vec3 seg = to.subtract(from);
        double len = seg.length();
        if (len < 1.0e-3) return;
        Vec3 dir = seg.scale(1.0 / len);
        for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0), ChronoGazer::canHurt)) {
            Vec3 c = living.position().add(0, living.getBbHeight() * 0.5, 0);
            double t = Mth.clamp(c.subtract(from).dot(dir), 0.0, len);
            double off = c.subtract(from.add(dir.scale(t))).length();
            if (off > 0.5 + living.getBbWidth() * 0.5 + living.getBbHeight() * 0.25) continue;
            living.invulnerableTime = 0;
            living.hurt(damageSources().indirectMagic(this, this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        if (tickCount % 2 == 0) {
            server.sendParticles(new DustParticleOptions(RED, 2.0F), to.x, to.y, to.z, 4, 0.2, 0.2, 0.2, 0.0);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, to.x, to.y, to.z, 2, 0.1, 0.1, 0.1, 0.02);
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        // 攻撃はゴール（BeamGoal）で行う。RangedAttackMob は兵姫に「遠距離の脅威」と分からせるための目印
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
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GLASS_BREAK;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 距離を保って溜め → 向きを固定して照射 → 休む、の繰り返し */
    static class BeamGoal extends Goal {
        private final MaskDroneEntity drone;
        private int rest;
        private float orbit;

        BeamGoal(MaskDroneEntity drone) {
            this.drone = drone;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = drone.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || drone.getState() == FIRING;
        }

        @Override
        public void start() {
            rest = 30;
            orbit = drone.random.nextFloat() * 6.28F;
        }

        @Override
        public void stop() {
            drone.setState(IDLE);
            drone.entityData.set(AIM_TARGET, -1);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = drone.getTarget();
            int state = drone.getState();
            int elapsed = drone.tickCount - drone.stateStart;
            if (state == FIRING) { // 向きは固定（避けられるように）。動かない
                drone.getNavigation().stop();
                drone.setDeltaMovement(drone.getDeltaMovement().scale(0.5));
                drone.burn();
                if (elapsed >= FIRE) {
                    drone.setState(IDLE);
                    rest = COOLDOWN;
                }
                return;
            }
            if (t == null) return;
            drone.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double dist = drone.distanceTo(t);
            if (state == CHARGING) {
                drone.getNavigation().stop();
                drone.setDeltaMovement(drone.getDeltaMovement().scale(0.6));
                if (elapsed >= CHARGE) { // 撃つ瞬間の向きで固定
                    Vec3 aim = t.position().add(0, t.getBbHeight() * 0.55, 0).subtract(drone.beamOrigin()).normalize();
                    drone.entityData.set(BEAM_DIR, new Vector3f((float) aim.x, (float) aim.y, (float) aim.z));
                    drone.entityData.set(AIM_TARGET, -1);
                    drone.setState(FIRING);
                    drone.playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 1.4F);
                } else if (!drone.hasLineOfSight(t) || dist > RANGE) {
                    drone.setState(IDLE); // 見失ったら溜め直し
                    drone.entityData.set(AIM_TARGET, -1);
                }
                return;
            }
            // 休み: 距離を保って回り込む（近すぎれば下がる）
            orbit += 0.015F;
            double keep = dist < TOO_CLOSE ? KEEP + 4.0 : KEEP;
            Vec3 spot = t.position().add(Math.cos(orbit) * keep, 5.0, Math.sin(orbit) * keep);
            if (drone.tickCount % 10 == 0 || drone.getNavigation().isDone()) drone.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
            if (--rest <= 0 && drone.hasLineOfSight(t) && dist <= RANGE) {
                drone.entityData.set(AIM_TARGET, t.getId());
                drone.setState(CHARGING);
                drone.playSound(SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.6F);
            }
        }
    }
}
