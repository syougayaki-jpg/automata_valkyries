package com.automatavalkyries.weapon;

import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * 星葬冠『ルミナス・レクイエム』の通常攻撃の虹の斬撃（2026-10-04 ユーザー指定）。重力が無く、毎 Tick 2 ブロックまっすぐ飛ぶ（射程 40 ブロック）。
 * 刃は進む向きに直角な面にあり、roll の向きに幅 5 ブロック。刃が通り抜けた所の敵へ 1 回ずつ {@link #DAMAGE}（貫通、無敵時間を消す）。
 * 撃った者には当たらない。刃の中心が壁に当たると、そこで虹の光になって散る。描画は {@code client/RainbowSlashRenderer}。
 */
public class RainbowSlashEntity extends ThrowableProjectile {

    public static final float SPEED = 2.0F, DAMAGE = 1500.0F;
    public static final double RANGE = 40.0, HALF_WIDTH = 2.5;
    public static final int LIFETIME = (int) Math.ceil(RANGE / SPEED);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(RainbowSlashEntity.class, EntityDataSerializers.FLOAT);
    /** 刃の幅の倍率（オーバーロードの T4 では {@link Overload#RANGE_MULT}） */
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(RainbowSlashEntity.class, EntityDataSerializers.FLOAT);

    private final Set<Integer> hit = new HashSet<>();

    public RainbowSlashEntity(EntityType<? extends RainbowSlashEntity> type, Level level) {
        super(type, level);
    }

    public RainbowSlashEntity(Level level, LivingEntity shooter) {
        super(ModWeapons.RAINBOW_SLASH.get(), shooter, level);
    }

    /** 虹の色（0〜1 の位置から。赤 → 紫） */
    public static int rainbow(double t) {
        return java.awt.Color.HSBtoRGB((float) (((t % 1) + 1) % 1 * 0.82), 0.75F, 1F) & 0xFFFFFF;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ROLL, 0F);
        entityData.define(SCALE, 1F);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public void setScale(float scale) {
        entityData.set(SCALE, scale);
    }

    /** 刃の半分の幅（ブロック） */
    public double halfWidth() {
        return HALF_WIDTH * scale();
    }

    public float roll() {
        return entityData.get(ROLL);
    }

    public void setRoll(float roll) {
        entityData.set(ROLL, roll);
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    /** 刃の向き（進む向きに直角な面で、roll だけ回した単位ベクトル） */
    public Vec3 bladeAxis() {
        Vec3 dir = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement().normalize() : getLookAngle();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float r = roll();
        return side.scale(Math.cos(r)).add(up.scale(Math.sin(r)));
    }

    @Override
    public void tick() {
        Vec3 before = position();
        baseTick();
        Vec3 after = before.add(getDeltaMovement());
        if (!level().isClientSide()) {
            BlockHitResult wall = level().clip(new ClipContext(before, after, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (wall.getType() != HitResult.Type.MISS) after = wall.getLocation();
            cut(before, after);
            if (wall.getType() != HitResult.Type.MISS || tickCount > LIFETIME) {
                shatter(after);
                return;
            }
        } else {
            trail();
        }
        setPos(after);
    }

    private void cut(Vec3 before, Vec3 after) {
        Entity owner = getOwner();
        Vec3 axis = bladeAxis();
        Vec3 dir = getDeltaMovement().normalize();
        AABB swept = new AABB(before, after).inflate(halfWidth() + 0.5);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, swept,
                e -> e != owner && e.isAlive() && !e.isSpectator() && !hit.contains(e.getId()) && !(owner != null && e.isAlliedTo(owner)))) {
            AABB box = target.getBoundingBox().inflate(0.3);
            boolean touched = false;
            for (int i = -4; i <= 4 && !touched; i++) {
                double t = i / 4.0;
                Vec3 offset = axis.scale(t * halfWidth()).subtract(dir.scale(t * t * 0.8));
                if (box.contains(before.add(offset)) || box.clip(before.add(offset), after.add(offset)).isPresent()) touched = true;
            }
            if (!touched) continue;
            hit.add(target.getId());
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(this, WeaponDamage.ENERGY, this, owner), DAMAGE);
            if (level() instanceof ServerLevel server) {
                Vec3 at = new Vec3(target.getX(), target.getY(0.55), target.getZ());
                for (int k = 0; k < 3; k++) Fx.slash(server, at, rainbow(k / 3.0 + tickCount * 0.05), 1.8F - k * 0.2F, 7, roll() + k * 1.05F);
                Fx.star(server, at, 0xFFFFFF, 0.9F, 10, Vec3.ZERO);
                for (int k = 0; k < 12; k++) {
                    Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(0.35);
                    Fx.one(server, Fx.opt(ModParticles.STAR.get(), rainbow(k / 12.0), 0.25F, 12), at, v);
                }
            }
        }
    }

    /** 刃に沿って虹の光を残す（クライアントで、見ている人だけ）: 刃の線に沿った 7 色の光、両端の白い光の尾、こぼれる虹の星 */
    private void trail() {
        Vec3 axis = bladeAxis();
        Vec3 dir = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement().normalize() : Vec3.ZERO;
        Vec3 drift = dir.scale(-0.05);
        for (int i = -6; i <= 6; i++) {
            double t = i / 6.0;
            Vec3 p = position().add(axis.scale(t * halfWidth())).subtract(dir.scale(t * t * 0.8));
            level().addParticle(new FxOptions(ModParticles.GLOW.get(), rainbow((i + 6) / 13.0 + tickCount * 0.04), 0.3F, 8, 0), p.x, p.y, p.z, drift.x, drift.y, drift.z);
        }
        for (int side = -1; side <= 1; side += 2) {
            Vec3 tip = position().add(axis.scale(side * halfWidth())).subtract(dir.scale(0.8));
            level().addParticle(new FxOptions(ModParticles.GLOW.get(), 0xFFFFFF, 0.2F, 16, 0), tip.x, tip.y, tip.z, 0, 0, 0);
        }
        for (int k = 0; k < 3; k++) {
            Vec3 p = position().add(axis.scale((random.nextDouble() * 2 - 1) * halfWidth()));
            level().addParticle(new FxOptions(ModParticles.STAR.get(), rainbow(random.nextDouble()), 0.12F + random.nextFloat() * 0.12F, 14, 0),
                    p.x, p.y, p.z, drift.x + (random.nextDouble() - 0.5) * 0.05, drift.y + (random.nextDouble() - 0.5) * 0.05, drift.z + (random.nextDouble() - 0.5) * 0.05);
        }
    }

    private void shatter(Vec3 at) {
        if (level() instanceof ServerLevel server) {
            Vec3 axis = bladeAxis();
            for (int i = -4; i <= 4; i++) {
                Vec3 p = at.add(axis.scale(i / 4.0 * halfWidth()));
                Fx.burst(server, Fx.opt(ModParticles.STAR.get(), rainbow((i + 4) / 9.0), 0.2F, 14), p, 2, 0.15);
                Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), rainbow((i + 4) / 9.0), 0.4F, 8), p, 1, 0, 0);
            }
        }
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {}

    @Override
    protected void onHitBlock(BlockHitResult result) {}
}
