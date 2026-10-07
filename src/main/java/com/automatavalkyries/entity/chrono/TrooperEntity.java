package com.automatavalkyries.entity.chrono;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * クロノゲイザーの兵士「クロノ・トルーパー」（2026-10-07 ユーザー指定「腹部にガトリングを持ち、手に黄色のフォトン剣を持つ兵士（HP 100）」）。
 * 離れた相手には腹に露出した回転式ガトリングを {@value #GUN_TICKS} Tick 連射（足を止める。1 発 {@value #BULLET_DAMAGE}、無敵時間を貫く）、
 * 近い相手には黄色のフォトン剣で横薙ぎ（{@value #SLASH_DAMAGE}、前方の扇）と突き（{@value #THRUST_DAMAGE}、踏み込む）を交互に。数値は【仮】
 */
public class TrooperEntity extends Monster implements GeoEntity, ChronoGazer, RangedAttackMob {

    public static final float BULLET_DAMAGE = 2.0F, SLASH_DAMAGE = 12.0F, THRUST_DAMAGE = 16.0F;
    public static final int GUN_TICKS = 40, GUN_COOLDOWN = 80, SWING_INTERVAL = 20;
    public static final double GUN_MIN = 8.0, GUN_MAX = 20.0, REACH = 3.0;
    /** 剣を振った合図（handleEntityEvent。描画の腕の動き） */
    public static final byte EVENT_SLASH = 70, EVENT_THRUST = 71;

    private static final EntityDataAccessor<Boolean> FIRING = SynchedEntityData.defineId(TrooperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final Vector3f YELLOW = new Vector3f(1.0F, 0.88F, 0.29F);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** クライアント: 最後に振った Tick と種類（描画用） */
    public int swingTick = -100;
    public boolean swingThrust;
    /** クライアント: ガトリングの回転（撃つ間に上がり、止めると落ちる） */
    public float spin, spinO, spinSpeed;

    public TrooperEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, SLASH_DAMAGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FIRING, false);
    }

    public boolean isFiring() {
        return entityData.get(FIRING);
    }

    void setFiring(boolean firing) {
        entityData.set(FIRING, firing);
        setAggressive(firing); // 兵姫の「遠距離の脅威」の判定
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new TrooperAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spinO = spin;
            spinSpeed = isFiring() ? Math.min(1.2F, spinSpeed + 0.1F) : Math.max(0.0F, spinSpeed - 0.04F);
            spin += spinSpeed;
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SLASH || id == EVENT_THRUST) {
            swingTick = tickCount;
            swingThrust = id == EVENT_THRUST;
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** 腹のガトリングの銃口（体の向きの前へ 0.45 ブロック、高さ 1.06 ブロック。モデルの銃身の先 z -7.2px に合わせる） */
    Vec3 muzzle() {
        Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(0, 17.0 / 16.0, 0).add(forward.scale(7.2 / 16.0));
    }

    /** 横薙ぎ: 前方 120 度・{@link #REACH}+0.5 m の相手みなに */
    void slash() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(REACH + 0.5, 1.0, REACH + 0.5), ChronoGazer::canHurt)) {
            Vec3 to = living.position().subtract(position()).multiply(1, 0, 1);
            if (to.length() > REACH + 0.5 + living.getBbWidth() * 0.5 || (to.lengthSqr() > 0.25 && to.normalize().dot(look) < 0.5)) continue;
            living.hurt(damageSources().mobAttack(this), SLASH_DAMAGE);
            living.knockback(0.5, -look.x, -look.z);
        }
        swordFx(look, 1.4);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
        level().broadcastEntityEvent(this, EVENT_SLASH);
    }

    /** 突き: 踏み込んで正面の相手に */
    void thrust(LivingEntity target) {
        if (!(level() instanceof ServerLevel)) return;
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        setDeltaMovement(getDeltaMovement().add(look.scale(0.6)));
        hasImpulse = true;
        if (distanceTo(target) <= REACH + 1.0 && ChronoGazer.canHurt(target)) {
            target.hurt(damageSources().mobAttack(this), THRUST_DAMAGE);
            target.knockback(0.8, -look.x, -look.z);
        }
        swordFx(look, 2.4);
        playSound(SoundEvents.TRIDENT_THROW, 1.0F, 1.4F);
        level().broadcastEntityEvent(this, EVENT_THRUST);
    }

    /** 黄色の光の筋（剣の通り道） */
    private void swordFx(Vec3 look, double reach) {
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 1; i <= 6; i++) {
            Vec3 p = position().add(0, 1.2, 0).add(look.scale(reach * i / 6.0));
            server.sendParticles(new DustParticleOptions(YELLOW, 1.4F), p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.0);
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        // 攻撃はゴールで行う（RangedAttackMob は兵姫に遠距離の脅威と分からせるための目印）
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.IRON_GOLEM_STEP;
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

    /** 離れていればガトリング、近ければ剣（横薙ぎと突きを交互）、間は走って詰める */
    static class TrooperAttackGoal extends Goal {
        private final TrooperEntity trooper;
        private int gunCooldown, gunTicks, swing, repath;
        private boolean nextThrust;

        TrooperAttackGoal(TrooperEntity trooper) {
            this.trooper = trooper;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = trooper.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            gunCooldown = 20;
            swing = 10;
        }

        @Override
        public void stop() {
            trooper.setFiring(false);
            gunTicks = 0;
            trooper.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = trooper.getTarget();
            if (t == null) return;
            trooper.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double dist = trooper.distanceTo(t);
            boolean sees = trooper.getSensing().hasLineOfSight(t);
            gunCooldown--;
            swing--;
            if (gunTicks > 0) { // 連射中は足を止める
                gunTicks--;
                trooper.getNavigation().stop();
                if (sees) {
                    ChronoBolt.fire(trooper, trooper.muzzle(), t, BULLET_DAMAGE, 0.0F, 2.4F, 6.0F, true);
                    if (gunTicks % 3 == 0) trooper.playSound(SoundEvents.DISPENSER_LAUNCH, 0.6F, 1.8F);
                }
                if (gunTicks == 0 || !sees || dist < REACH) {
                    gunTicks = 0;
                    gunCooldown = GUN_COOLDOWN;
                    trooper.setFiring(false);
                }
                return;
            }
            if (dist <= REACH + 0.5) {
                trooper.getNavigation().stop();
                if (swing <= 0) {
                    swing = SWING_INTERVAL;
                    if (nextThrust) trooper.thrust(t);
                    else trooper.slash();
                    nextThrust = !nextThrust;
                }
                return;
            }
            if (dist >= GUN_MIN && dist <= GUN_MAX && sees && gunCooldown <= 0) {
                gunTicks = GUN_TICKS;
                trooper.setFiring(true);
                trooper.playSound(SoundEvents.PISTON_EXTEND, 1.0F, 0.8F); // 銃身が回り出す
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                trooper.getNavigation().moveTo(t, dist < GUN_MIN ? 1.25 : 1.0);
            }
        }
    }
}
