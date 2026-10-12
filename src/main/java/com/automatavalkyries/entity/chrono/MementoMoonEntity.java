package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 黒死の月の見た目（2026-10-10 ユーザー指定。当たり判定はメメントモリの側 {@link MementoMoriEntity#moonFall}）。見た目だけ:
 * 地面の中心から半径 radius の範囲と、光の当たる安全地帯 {@value MementoMoriEntity#MOON_SAFE} つ（まわりより明るい光の柱）を、life Tick の間描く
 * （client/MementoMoonRenderer。範囲の縁の赤い輪・閉じていく数え輪・空の赤黒い月・降る鱗粉）。保存しない
 */
public class MementoMoonEntity extends Entity {

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(MementoMoonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(MementoMoonEntity.class, EntityDataSerializers.INT);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Vector3f>[] SAFE = new EntityDataAccessor[MementoMoriEntity.MOON_SAFE];

    static {
        for (int i = 0; i < SAFE.length; i++) SAFE[i] = SynchedEntityData.defineId(MementoMoonEntity.class, EntityDataSerializers.VECTOR3);
    }

    public MementoMoonEntity(EntityType<? extends MementoMoonEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, Vec3 center, float radius, List<Vec3> safe, int life) {
        MementoMoonEntity m = new MementoMoonEntity(ModEntities.MEMENTO_MOON.get(), level);
        m.setPos(center.x, center.y, center.z);
        m.entityData.set(RADIUS, radius);
        m.entityData.set(LIFE, life);
        for (int i = 0; i < SAFE.length && i < safe.size(); i++) m.entityData.set(SAFE[i], safe.get(i).subtract(center).toVector3f());
        level.addFreshEntity(m);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    /** 安全地帯の中心（この目印からのずれ） */
    public List<Vec3> safeZones() {
        List<Vec3> out = new ArrayList<>();
        for (EntityDataAccessor<Vector3f> a : SAFE) out.add(new Vec3(entityData.get(a)));
        return out;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, MementoMoriEntity.MOON_RADIUS);
        entityData.define(LIFE, 120);
        for (EntityDataAccessor<Vector3f> a : SAFE) entityData.define(a, new Vector3f());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) { // 降る黒い鱗粉（範囲の中に）
            float r = radius();
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                level().addParticle(ParticleTypes.SQUID_INK, getX() + Math.cos(a) * d, getY() + 6 + random.nextDouble() * 6, getZ() + Math.sin(a) * d, 0, -0.15, 0);
            }
            return;
        }
        if (tickCount > life() + 10) discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
