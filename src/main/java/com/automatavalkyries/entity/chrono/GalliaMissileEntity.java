package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * ガリアフォートのミサイル（ミサイル一斉射撃。2026-10-10）。背の箱から真上へ撃ち上がり（{@link #ASCEND} Tick）、頂点で止まって
 * 降らせる所に赤い予告の陣を出し（{@link #HOLD} Tick）、そこへまっすぐ降りて炸裂する（地形は壊さない）。
 * 当たった相手へのダメージはプレイヤーの最大 HP に応じて上げる（ルクシオンと同じ）。保存しない
 */
public class GalliaMissileEntity extends Entity implements GeoEntity {

    public static final int ASCEND = 14, HOLD = 26, LIFE = 120;
    /** 降らせる所（描画で頂点から機首をそこへ向ける。2026-10-10「副砲とミサイルの弾を自作して搭載」で 3D のミサイルにした） */
    private static final EntityDataAccessor<Vector3f> AIM = SynchedEntityData.defineId(GalliaMissileEntity.class, EntityDataSerializers.VECTOR3);
    /**
     * 対空（2026-10-10 ユーザー指定「対空には副砲と主砲と弱めに追尾するミサイルで対応して」）: 空の相手へは、撃ち上がった後に止まらず相手を追う。
     * 速さ {@link #HOMING_SPEED}（ブロック / Tick）、1 Tick に {@link #HOMING_TURN} 度までしか曲がれない（鋭く曲がれば避けられる）。相手から {@link #FUSE} 以内か地形で炸裂
     */
    private static final EntityDataAccessor<Boolean> HOMING = SynchedEntityData.defineId(GalliaMissileEntity.class, EntityDataSerializers.BOOLEAN);
    public static final double HOMING_SPEED = 0.85, HOMING_TURN = 3.5, FUSE = 1.6;
    public static final int HOMING_ASCEND = 10;
    @Nullable
    private LivingEntity homingTarget;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private static final double DIVE_SPEED = 1.6;

    @Nullable
    private LivingEntity owner;
    private Vec3 aim = Vec3.ZERO;
    private float damage, radius;

    public GalliaMissileEntity(EntityType<? extends GalliaMissileEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** from から撃ち上げ、空の相手 target を弱めに追う */
    public static void launchHoming(ServerLevel level, LivingEntity owner, Vec3 from, LivingEntity target, float damage, float radius) {
        GalliaMissileEntity m = new GalliaMissileEntity(ModEntities.GALLIA_MISSILE.get(), level);
        m.owner = owner;
        m.homingTarget = target;
        m.aim = target.position();
        m.entityData.set(AIM, m.aim.toVector3f());
        m.entityData.set(HOMING, true);
        m.damage = damage;
        m.radius = radius;
        m.setPos(from.x, from.y, from.z);
        m.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.3, 0.9, (level.random.nextDouble() - 0.5) * 0.3);
        level.addFreshEntity(m);
    }

    public boolean homing() {
        return entityData.get(HOMING);
    }

    /** from から撃ち上げ、aim（地面の上）へ降らせる */
    public static void launch(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 aim, float damage, float radius) {
        GalliaMissileEntity m = new GalliaMissileEntity(ModEntities.GALLIA_MISSILE.get(), level);
        m.owner = owner;
        m.aim = aim;
        m.entityData.set(AIM, aim.toVector3f());
        m.damage = damage;
        m.radius = radius;
        m.setPos(from.x, from.y, from.z);
        m.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.2, 0.9, (level.random.nextDouble() - 0.5) * 0.2);
        level.addFreshEntity(m);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AIM, new Vector3f());
        entityData.define(HOMING, false);
    }

    /** 降らせる所（クライアントでも分かる） */
    public Vec3 aim() {
        return new Vec3(entityData.get(AIM));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (level().isClientSide) { // 煙と火の尾
            level().addParticle(ParticleTypes.SMOKE, getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0, 0.02, 0);
            level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 0, 0, 0);
            return; // 位置はサーバーから毎 Tick 届く

        }
        if (!(level() instanceof ServerLevel server) || tickCount > LIFE) {
            discard();
            return;
        }
        if (homing()) {
            if (!homingTick(server, v)) return;
        } else if (tickCount < ASCEND) {
            setDeltaMovement(v.x * 0.9, v.y * 0.92, v.z * 0.9); // 上で勢いが落ちる
        } else if (tickCount < ASCEND + HOLD) {
            setDeltaMovement(Vec3.ZERO);
            if (tickCount % 4 == 0) { // 予告の陣（だんだん赤く速く）
                Fx.mandala(server, aim.add(0, 0.1, 0), GalliaFortEntity.C_RED, radius, 6, false, true);
            }
            if (tickCount == ASCEND + HOLD - 1) server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 1.0F, 0.6F);
        } else {
            Vec3 to = aim.subtract(position());
            double len = to.length();
            if (len <= DIVE_SPEED) {
                setPos(aim.x, aim.y, aim.z);
                explode(server);
                return;
            }
            Vec3 step = to.scale(DIVE_SPEED / len);
            HitResult hit = level().clip(new ClipContext(position(), position().add(step), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                explode(server);
                return;
            }
            setDeltaMovement(step);
        }
        Vec3 nv = getDeltaMovement();
        setPos(getX() + nv.x, getY() + nv.y, getZ() + nv.z);
    }

    /** 対空の 1 Tick: 撃ち上がった後、相手へ向きを少しずつ変えて追う。炸裂したら false */
    private boolean homingTick(ServerLevel server, Vec3 v) {
        if (tickCount < HOMING_ASCEND) {
            setDeltaMovement(v.x * 0.92, v.y * 0.9, v.z * 0.92);
            return true;
        }
        LivingEntity t = homingTarget != null && homingTarget.isAlive() ? homingTarget : null;
        Vec3 cur = v.lengthSqr() > 1.0e-4 ? v.normalize() : new Vec3(0, 1, 0);
        Vec3 dir = cur;
        if (t != null) {
            Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(position());
            if (to.length() < FUSE) {
                explode(server);
                return false;
            }
            Vec3 want = to.normalize();
            double ang = Math.acos(Math.max(-1.0, Math.min(1.0, cur.dot(want))));
            double max = Math.toRadians(HOMING_TURN);
            dir = ang <= max ? want : cur.add(want.subtract(cur).scale(max / ang)).normalize();
            if (tickCount % 6 == 0) server.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 0.8F, 1.8F); // 追っている音
        }
        Vec3 step = dir.scale(HOMING_SPEED);
        HitResult hit = level().clip(new ClipContext(position(), position().add(step), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
            explode(server);
            return false;
        }
        setDeltaMovement(step);
        return true;
    }

    /** 炸裂: 半径 radius の相手へ、近いほど強く（地形は壊さない） */
    private void explode(ServerLevel server) {
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.4, 0.3, 0.4, 0);
        Fx.burst(server, GalliaFortEntity.glow(GalliaFortEntity.C_ORANGE, 0.7F, 16), position().add(0, 0.4, 0), 14, 0.25);
        Fx.shockwave(server, position().add(0, 0.1, 0), GalliaFortEntity.C_RED, radius + 0.5F, 12);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 1.1F);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(radius + 1.0), ChronoGazer::canHurt)) {
            double d = e.position().add(0, e.getBbHeight() * 0.4, 0).distanceTo(position());
            if (d > radius + e.getBbWidth() * 0.5) continue;
            float scale = (float) Math.max(0.4, 1.0 - d / (radius + 0.5));
            e.hurt(damageSources().explosion(this, owner), LuxionEntity.scaledFor(e, damage * scale));
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
            if (push.lengthSqr() > 1.0e-4) push = push.normalize().scale(0.5 * scale);
            e.push(push.x, 0.3 * scale, push.z);
        }
        discard();
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
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
