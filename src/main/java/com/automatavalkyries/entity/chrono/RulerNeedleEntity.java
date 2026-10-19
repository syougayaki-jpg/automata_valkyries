package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * タイムルーラーの「時空剪定の針」（2026-10-19）。置かれた所で {@link #hold} Tick の間、狙う向きへ切っ先を向けて光りながら止まり、それからまっすぐ飛ぶ。
 * 時間停止の間は Tick が止まるので、止まった時の中に並べた針は、時が動き出してから少し待って一斉に飛ぶ（避ける間がある）。
 * 当たった相手にダメージ（無敵時間は普通に効く）。壁に当たるか、飛んで 60 Tick で消える。保存しない
 */
public class RulerNeedleEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Float> AIM_X = SynchedEntityData.defineId(RulerNeedleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_Y = SynchedEntityData.defineId(RulerNeedleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_Z = SynchedEntityData.defineId(RulerNeedleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> HOLD = SynchedEntityData.defineId(RulerNeedleEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID owner;
    private float damage = 8.0F, speed = 1.4F;
    private int flown;

    public RulerNeedleEntity(EntityType<? extends RulerNeedleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** 針を置く: from に、to へ向けて。hold Tick 止まってから speed で飛ぶ */
    public static RulerNeedleEntity place(LivingEntity shooter, Vec3 from, Vec3 to, int hold, float damage, float speed) {
        RulerNeedleEntity n = new RulerNeedleEntity(ModEntities.RULER_NEEDLE.get(), shooter.level());
        n.owner = shooter.getUUID();
        n.damage = damage;
        n.speed = speed;
        n.setPos(from.x, from.y, from.z);
        Vec3 d = to.subtract(from);
        n.setAim(d.lengthSqr() < 1.0e-6 ? new Vec3(0, -1, 0) : d.normalize());
        n.entityData.set(HOLD, hold);
        shooter.level().addFreshEntity(n);
        return n;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AIM_X, 0.0F);
        entityData.define(AIM_Y, -1.0F);
        entityData.define(AIM_Z, 0.0F);
        entityData.define(HOLD, 0);
    }

    void setAim(Vec3 d) {
        entityData.set(AIM_X, (float) d.x);
        entityData.set(AIM_Y, (float) d.y);
        entityData.set(AIM_Z, (float) d.z);
    }

    public Vec3 aim() {
        return new Vec3(entityData.get(AIM_X), entityData.get(AIM_Y), entityData.get(AIM_Z));
    }

    /** まだ止まっているか（描く側も、切っ先の光を強める） */
    public boolean holding() {
        return entityData.get(HOLD) > 0;
    }

    @Override
    public void tick() {
        super.tick();
        int hold = entityData.get(HOLD);
        if (hold > 0) {
            if (!level().isClientSide) entityData.set(HOLD, hold - 1);
            if (level() instanceof ServerLevel server && tickCount % 3 == 0) Fx.one(server, MementoMoriEntity.glow(0xFFD166, 0.18F, 6), position(), Vec3.ZERO);
            if (hold == 1 && !level().isClientSide) playSound(SoundEvents.TRIDENT_THROW, 0.6F, 1.8F);
            return;
        }
        Vec3 v = aim().scale(speed);
        Vec3 from = position(), to = from.add(v);
        if (!level().isClientSide) {
            HitResult wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
            for (Entity e : level().getEntities(this, getBoundingBox().expandTowards(to.subtract(from)).inflate(0.4), ChronoGazer::canHurt)) {
                if (e.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) {
                    Entity src = owner != null && level() instanceof ServerLevel server ? server.getEntity(owner) : null;
                    DamageSource ds = src instanceof LivingEntity l ? damageSources().mobProjectile(this, l) : damageSources().magic();
                    e.hurt(ds, damage);
                    shatter();
                    return;
                }
            }
            if (wall.getType() != HitResult.Type.MISS || ++flown > 60) {
                shatter();
                return;
            }
        }
        setPos(to.x, to.y, to.z);
    }

    private void shatter() {
        if (level() instanceof ServerLevel server) Fx.burst(server, MementoMoriEntity.glow(0xFFD166, 0.2F, 8), position(), 6, 0.1);
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
