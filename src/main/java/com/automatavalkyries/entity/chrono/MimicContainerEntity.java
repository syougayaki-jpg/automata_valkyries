package com.automatavalkyries.entity.chrono;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 侵食基地の罠「ミミック・コンテナ」（2026-10-15 ユーザー指定。拡張仕様書 §10.2 の位相アンカーの代わりの 5 種目。数値は【仮】）。
 * 物資のコンテナに化けて倉庫に紛れ、ぴくりとも動かない（脚はしまい、蓋は閉じている。向きは 90 度ごと）。
 * プレイヤー・兵姫が {@value #WAKE_RANGE} m に近づくか、攻撃されると目を覚まし、蓋を開けて歯を見せ、脚を生やして飛びかかる（噛みつき {@value #BITE}）。
 * 倒すと中の物資を少し落とす（loot_tables/entities/mimic_container）
 */
public class MimicContainerEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final double WAKE_RANGE = 3.5;
    public static final float BITE = 8.0F;
    private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(MimicContainerEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** クライアント: 目を覚ました Tick（蓋が開いて脚が伸びる描画） */
    public int wakeTick = -100;

    public MimicContainerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, BITE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DORMANT, true);
    }

    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DORMANT.equals(key) && !isDormant()) wakeTick = tickCount;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true) {
            @Override
            public boolean canUse() {
                return !isDormant() && super.canUse();
            }
        });
        ChronoGazer.addTargets(this);
    }

    /** 化けている間は向きを 90 度ごとに揃えて止まる（置いた向き） */
    public void settle(float yaw) {
        float snapped = Math.round(yaw / 90.0F) * 90.0F;
        setYRot(snapped);
        yBodyRot = yBodyRotO = yHeadRot = yHeadRotO = snapped;
        setPersistenceRequired();
    }

    /** 目を覚ます: 蓋を開けて跳び上がる */
    public void wake(LivingEntity by) {
        if (!isDormant()) return;
        entityData.set(DORMANT, false);
        playSound(SoundEvents.CHEST_OPEN, 1.0F, 0.6F);
        playSound(SoundEvents.RAVAGER_ROAR, 0.6F, 1.8F);
        if (by != null && ChronoGazer.canHurt(by)) {
            setTarget(by);
            Vec3 to = by.position().subtract(position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1.0e-4) setDeltaMovement(to.normalize().scale(0.5).add(0, 0.45, 0));
        }
    }

    @Override
    public void aiStep() {
        if (isDormant()) {
            if (!level().isClientSide && tickCount % 5 == 0) {
                Player p = level().getNearestPlayer(this, WAKE_RANGE);
                LivingEntity near = p != null && ChronoGazer.canHurt(p) ? p : null;
                if (near == null) {
                    for (LivingEntity v : level().getEntitiesOfClass(com.automatavalkyries.entity.ValkyrieEntity.class, getBoundingBox().inflate(WAKE_RANGE))) {
                        near = v;
                        break;
                    }
                }
                if (near != null) wake(near);
            }
            setTarget(null); // 化けている間は狙わない（近づくのを待つ）
            yBodyRot = yBodyRotO = getYRot();
            yHeadRot = yHeadRotO = getYRot();
        }
        super.aiStep();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isDormant()) wake(source.getEntity() instanceof LivingEntity l ? l : null);
        return hit;
    }

    @Override
    public void travel(Vec3 input) {
        super.travel(isDormant() ? Vec3.ZERO : input);
    }

    @Override
    public boolean isPushable() {
        return !isDormant() && super.isPushable();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : SoundEvents.CHEST_LOCKED;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CHEST_CLOSE;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isDormant() && super.removeWhenFarAway(distance); // 化けている物は残る
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Dormant")) entityData.set(DORMANT, tag.getBoolean("Dormant"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
