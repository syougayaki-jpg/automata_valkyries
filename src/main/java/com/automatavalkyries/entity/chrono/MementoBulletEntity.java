package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * メメントモリの死蝶の幾何弾幕の 1 発（2026-10-10 ユーザー指定）。まっすぐ、または軸（狙いの向き）のまわりに 1 Tick に curl ラジアンずつ向きを曲げながら飛び
 * （渦巻き・花の形の模様）、当たった相手に damage（最大 HP は削らない）。壊せない。地形に当たると消える。
 * 曲げ方はクライアントにも送り、クライアントでも同じように動かす（数の多い弾の位置を毎 Tick 送らない）。見た目は client/MementoBulletRenderer（光る小さな蝶）
 */
public class MementoBulletEntity extends Entity {

    public static final int LIFE = 100;
    private static final EntityDataAccessor<Vector3f> AXIS = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> CURL = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> VEL = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> RADIANT = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private LivingEntity owner;
    private float damage = 6.0F;
    private Vec3 vel = Vec3.ZERO;
    private boolean started;

    public MementoBulletEntity(EntityType<? extends MementoBulletEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** from から velocity で撃つ。axis のまわりに 1 Tick に curl ラジアン曲がる（0 ならまっすぐ） */
    public static void fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 velocity, Vec3 axis, float curl, float damage, boolean radiant) {
        MementoBulletEntity b = new MementoBulletEntity(ModEntities.MEMENTO_BULLET.get(), level);
        b.owner = owner;
        b.damage = damage;
        b.vel = velocity;
        b.entityData.set(VEL, velocity.toVector3f());
        b.entityData.set(AXIS, axis.toVector3f());
        b.entityData.set(CURL, curl);
        b.entityData.set(RADIANT, radiant);
        b.setPos(from.x, from.y, from.z);
        level.addFreshEntity(b);
    }

    public boolean isRadiant() {
        return entityData.get(RADIANT);
    }

    /** 今の飛ぶ向き（描画で蝶の向きに使う） */
    public Vec3 velocity() {
        return vel;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AXIS, new Vector3f(0, 1, 0));
        entityData.define(CURL, 0.0F);
        entityData.define(VEL, new Vector3f());
        entityData.define(RADIANT, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!started) {
            vel = new Vec3(entityData.get(VEL));
            started = true;
        }
        float curl = entityData.get(CURL);
        if (curl != 0.0F) vel = rotate(vel, new Vec3(entityData.get(AXIS)), curl);
        Vec3 next = position().add(vel);
        if (level().isClientSide) {
            setPos(next.x, next.y, next.z);
            // 光の尾（2026-10-14 ユーザー指定の演出「光の軌跡・粒を濃く」）
            if (tickCount % 2 == 0) level().addParticle(MementoMoriEntity.glow(isRadiant() ? MementoMoriEntity.C_CYAN : MementoMoriEntity.C_PALE, 0.14F, 8), getX(), getY() + 0.1, getZ(), 0, 0, 0);
            return;
        }
        if (!(level() instanceof ServerLevel server) || tickCount > LIFE) {
            discard();
            return;
        }
        HitResult hit = level().clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            com.automatavalkyries.weapon.Fx.burst(server, MementoMoriEntity.glow(isRadiant() ? MementoMoriEntity.C_CYAN : MementoMoriEntity.C_PALE, 0.25F, 10), hit.getLocation(), 3, 0.06); // 光の粒（2026-10-14 ユーザー指定「黒いもやを全部幻想的な光にして」）
            discard();
            return;
        }
        setPos(next.x, next.y, next.z);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.15), ChronoGazer::canHurt)) {
            if (e == owner) continue;
            e.hurt(damageSources().indirectMagic(this, owner), owner instanceof MementoMoriEntity m ? m.damageFor(e, damage) : damage);
            discard();
            return;
        }
    }

    /** v を axis（正規化しなくてよい）のまわりに ang ラジアン回す（ロドリゲスの回転） */
    static Vec3 rotate(Vec3 v, Vec3 axis, double ang) {
        Vec3 k = axis.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : axis.normalize();
        double c = Math.cos(ang), s = Math.sin(ang);
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1 - c)));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
