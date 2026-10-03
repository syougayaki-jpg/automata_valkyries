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
 * クアンタムブレードの三日月の斬撃。重力が無く、毎 Tick 1.6 ブロックまっすぐ飛ぶ（射程 16 ブロック = 10 Tick）。
 * 刃は進む向きに直角な面にあり、roll（縦・横・斜め）の向きに幅 3 ブロック。刃が通り抜けた所の敵へ 1 回ずつ 18 ダメージ（貫通）。
 * 撃った者には当たらない。刃の中心が壁に当たると、そこで砕けて消える。
 */
public class QuantumSlashEntity extends ThrowableProjectile {

    public static final float SPEED = 1.6F, DAMAGE = 18.0F;
    public static final double RANGE = 16.0, HALF_WIDTH = 1.5;
    public static final int LIFETIME = (int) Math.ceil(RANGE / SPEED);
    public static final int COLOR_CORE = 0xE8F0FF, COLOR_EDGE = 0x8A7CFF, COLOR_GLOW = 0x5AE8FF;
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(QuantumSlashEntity.class, EntityDataSerializers.FLOAT);

    private final Set<Integer> hit = new HashSet<>();

    public QuantumSlashEntity(EntityType<? extends QuantumSlashEntity> type, Level level) {
        super(type, level);
    }

    public QuantumSlashEntity(Level level, LivingEntity shooter) {
        super(ModWeapons.QUANTUM_SLASH.get(), shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ROLL, 0F);
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
        // 弾自身の当たり判定は使わない（刃の幅で自分で判定する）。動きだけ進める
        baseTick();
        Vec3 motion = getDeltaMovement();
        Vec3 after = before.add(motion);
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

    /** 刃（中心から左右 1.5 ブロック）が before → after で通った所の敵を斬る */
    private void cut(Vec3 before, Vec3 after) {
        Entity owner = getOwner();
        Vec3 axis = bladeAxis();
        AABB swept = new AABB(before, after).inflate(HALF_WIDTH + 0.5);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, swept,
                e -> e != owner && e.isAlive() && !e.isSpectator() && !hit.contains(e.getId()))) {
            AABB box = target.getBoundingBox().inflate(0.25);
            boolean touched = false;
            for (int i = -3; i <= 3 && !touched; i++) {
                // 三日月なので、端ほど少し後ろへ反る
                double t = i / 3.0;
                Vec3 offset = axis.scale(t * HALF_WIDTH).subtract(getDeltaMovement().normalize().scale(t * t * 0.4));
                if (box.contains(before.add(offset)) || box.clip(before.add(offset), after.add(offset)).isPresent()) touched = true;
            }
            if (!touched) continue;
            hit.add(target.getId());
            target.hurt(WeaponDamage.source(this, WeaponDamage.QUANTUM_SLASH, this, owner), DAMAGE);
            if (level() instanceof ServerLevel server) {
                // 斬った所: 十字の斬撃の閃き、光の玉、量子の格子が弾け、きらめきが散る
                Vec3 at = new Vec3(target.getX(), target.getY(0.55), target.getZ());
                Fx.slash(server, at, COLOR_CORE, 1.6F, 6, roll());
                Fx.slash(server, at, COLOR_GLOW, 1.3F, 7, roll() + (float) (Math.PI / 2));
                Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), COLOR_GLOW, 1.1F, 8), at, 1, 0, 0);
                Fx.burst(server, Fx.opt(ModParticles.HEX.get(), COLOR_EDGE, 0.16F, 14), at, 10, 0.25);
                Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR_CORE, 0.32F, 10), at, 12, 0.4);
            }
        }
    }

    /**
     * 刃に沿って光を残す（クライアントで、見ている人だけ）: 刃の線の光、両端から長く伸びる光の尾、
     * 刃からこぼれて後ろへ流れるきらめき、ときどき剥がれる量子の格子（六角の欠片）
     */
    private void trail() {
        Vec3 axis = bladeAxis();
        Vec3 dir = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement().normalize() : Vec3.ZERO;
        Vec3 drift = dir.scale(-0.12);
        for (int i = -5; i <= 5; i++) {
            double t = i / 5.0;
            Vec3 p = position().add(axis.scale(t * HALF_WIDTH)).subtract(dir.scale(t * t * 0.4));
            boolean core = Math.abs(i) <= 3;
            level().addParticle(new FxOptions(ModParticles.GLOW.get(), core ? COLOR_GLOW : COLOR_EDGE, core ? 0.22F : 0.15F, 7, 0), p.x, p.y, p.z, drift.x * 0.3, drift.y * 0.3, drift.z * 0.3);
        }
        // 両端の光の尾（長く残る）
        for (int side = -1; side <= 1; side += 2) {
            Vec3 tip = position().add(axis.scale(side * HALF_WIDTH)).subtract(dir.scale(0.4));
            level().addParticle(new FxOptions(ModParticles.GLOW.get(), COLOR_CORE, 0.16F, 14, 0), tip.x, tip.y, tip.z, 0, 0, 0);
            level().addParticle(new FxOptions(ModParticles.GLOW.get(), COLOR_EDGE, 0.28F, 10, 0), tip.x, tip.y, tip.z, 0, 0, 0);
        }
        // こぼれるきらめき（後ろへ流れる）
        for (int k = 0; k < 2; k++) {
            Vec3 p = position().add(axis.scale((random.nextDouble() * 2 - 1) * HALF_WIDTH));
            level().addParticle(new FxOptions(ModParticles.SPARK.get(), k == 0 ? COLOR_CORE : COLOR_GLOW, 0.14F + random.nextFloat() * 0.1F, 10, 0),
                    p.x, p.y, p.z, drift.x + (random.nextDouble() - 0.5) * 0.04, drift.y + (random.nextDouble() - 0.5) * 0.04, drift.z + (random.nextDouble() - 0.5) * 0.04);
        }
        // 剥がれる量子の格子
        if (tickCount % 2 == 0) {
            Vec3 p = position().add(axis.scale((random.nextDouble() * 2 - 1) * HALF_WIDTH));
            level().addParticle(new FxOptions(ModParticles.HEX.get(), COLOR_EDGE, 0.1F, 14, 0), p.x, p.y, p.z, drift.x, drift.y, drift.z);
        }
    }

    /** 壁か射程の終わりで、刃が光の破片になって散る */
    private void shatter(Vec3 at) {
        if (level() instanceof ServerLevel server) {
            // 刃に沿って光の破片になって散る
            Vec3 axis = bladeAxis();
            for (int i = -3; i <= 3; i++) {
                Vec3 p = at.add(axis.scale(i / 3.0 * HALF_WIDTH));
                Fx.burst(server, Fx.opt(ModParticles.HEX.get(), COLOR_EDGE, 0.14F, 14), p, 2, 0.12);
                Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), COLOR_GLOW, 0.3F, 8), p, 1, 0, 0);
            }
            Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR_CORE, 0.25F, 9), at, 8, 0.25);
        }
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {}

    @Override
    protected void onHitBlock(BlockHitResult result) {}
}
