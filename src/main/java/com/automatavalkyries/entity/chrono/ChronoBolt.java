package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * クロノゲイザーの弾（索敵ドローンの赤いレーザー弾・重歩行戦車の砲弾）。まっすぐ飛び（重力なし）、仲間には当たらない。
 * 砲弾（shell）は当たった所で小さく炸裂し、まわりにもダメージ（地形は壊さない）
 */
public class ChronoBolt extends ThrowableProjectile {

    private static final EntityDataAccessor<Boolean> SHELL = SynchedEntityData.defineId(ChronoBolt.class, EntityDataSerializers.BOOLEAN);
    private static final Vector3f RED = new Vector3f(1.0F, 0.17F, 0.29F);
    private static final int LIFE = 100;

    private float damage = 6.0F;
    private float blastRadius = 0.0F;
    /** 無敵時間を貫く（クロノ・トルーパーのガトリング: 小さな弾を連射しても当たるたびに通す） */
    private boolean pierce;

    public ChronoBolt(EntityType<? extends ChronoBolt> type, Level level) {
        super(type, level);
    }

    /** 撃つ（shooter の目の高さから、target の胸へ speed で） */
    public static ChronoBolt fire(LivingEntity shooter, LivingEntity target, float damage, float blastRadius, float speed, float inaccuracy) {
        return fire(shooter, target, damage, blastRadius, speed, inaccuracy, false);
    }

    /** pierce = 当たるたびに無敵時間を消して通す（連射の弾） */
    public static ChronoBolt fire(LivingEntity shooter, LivingEntity target, float damage, float blastRadius, float speed, float inaccuracy, boolean pierce) {
        Vec3 eye = shooter.getEyePosition().add(shooter.getLookAngle().scale(0.6));
        return fire(shooter, eye.subtract(0, 0.1, 0), target, damage, blastRadius, speed, inaccuracy, pierce);
    }

    /** from = 弾の出る所（クロノ・トルーパーは腹の銃口から） */
    public static ChronoBolt fire(LivingEntity shooter, Vec3 from, LivingEntity target, float damage, float blastRadius, float speed, float inaccuracy, boolean pierce) {
        ChronoBolt bolt = new ChronoBolt(ModEntities.CHRONO_BOLT.get(), shooter.level());
        bolt.setOwner(shooter);
        bolt.damage = damage;
        bolt.blastRadius = blastRadius;
        bolt.pierce = pierce;
        bolt.entityData.set(SHELL, blastRadius > 0.0F);
        bolt.setPos(from.x, from.y, from.z);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vec3 d = to.subtract(bolt.position());
        bolt.shoot(d.x, d.y, d.z, speed, inaccuracy);
        shooter.level().addFreshEntity(bolt);
        return bolt;
    }

    public boolean isShell() {
        return entityData.get(SHELL);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SHELL, false);
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(new DustParticleOptions(RED, isShell() ? 1.6F : 0.9F), getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > LIFE) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !ChronoGazer.isFriend(e);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;
        Entity e = hit.getEntity();
        if (blastRadius <= 0.0F && ChronoGazer.canHurt(e)) {
            if (pierce) e.invulnerableTime = 0;
            e.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), damage);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level().isClientSide) return;
        if (blastRadius > 0.0F) blast();
        else if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.1);
        discard();
    }

    /** 砲弾の炸裂: 半径 blastRadius の相手に、近いほど強く（地形は壊さない） */
    private void blast() {
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 2, 0.3, 0.3, 0.3, 0.0);
        server.sendParticles(new DustParticleOptions(RED, 2.0F), getX(), getY(), getZ(), 24, blastRadius * 0.4, blastRadius * 0.3, blastRadius * 0.4, 0.0);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.3F);
        LivingEntity owner = getOwner() instanceof LivingEntity l ? l : null;
        for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(blastRadius), ChronoGazer::canHurt)) {
            double dist = living.distanceTo(this);
            if (dist > blastRadius + 0.5) continue;
            float scale = (float) Math.max(0.35, 1.0 - dist / (blastRadius + 0.5));
            living.hurt(damageSources().explosion(this, owner), damage * scale);
            Vec3 push = living.position().subtract(position()).normalize().scale(0.6 * scale);
            living.push(push.x, 0.25 * scale, push.z);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putFloat("Blast", blastRadius);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        blastRadius = tag.getFloat("Blast");
        entityData.set(SHELL, blastRadius > 0.0F);
    }
}
