package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
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

/**
 * ルクシオンの技の自作エフェクト（見た目だけ。当たりは技の側で済ませてある。2026-10-08 ユーザー指定「光速突進や光刃の波も自作エフェクトで豪華に」）。
 * 位置が始まりで、{@link #END} が終わり。種類ごとに {@code client/LuxionFxRenderer} が描く:
 * 光刃の波 = 三日月の光の刃が始まりから終わりへ飛び、跡に光の帯、光速突進 = 道に光の帯と巻き付く螺旋の光が走って細く消える。
 * 保存しない。{@link #LIFE} Tick で消える
 */
public class LuxionFxEntity extends Entity {

    public static final int WAVE = 0, DASH = 1;
    public static final int LIFE = 18;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(LuxionFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> END = SynchedEntityData.defineId(LuxionFxEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> RED = SynchedEntityData.defineId(LuxionFxEntity.class, EntityDataSerializers.BOOLEAN);

    public LuxionFxEntity(EntityType<? extends LuxionFxEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** from から to への種類 kind のエフェクトを出す（red = 第 2 形態の赤み） */
    public static void spawn(ServerLevel level, int kind, Vec3 from, Vec3 to, boolean red) {
        LuxionFxEntity fx = new LuxionFxEntity(ModEntities.LUXION_FX.get(), level);
        fx.setPos(from.x, from.y, from.z);
        fx.entityData.set(KIND, kind);
        fx.entityData.set(END, new Vector3f((float) to.x, (float) to.y, (float) to.z));
        fx.entityData.set(RED, red);
        level.addFreshEntity(fx);
    }

    public int kind() {
        return entityData.get(KIND);
    }

    public Vec3 end() {
        Vector3f v = entityData.get(END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public boolean red() {
        return entityData.get(RED);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, WAVE);
        entityData.define(END, new Vector3f());
        entityData.define(RED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= LIFE) discard();
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
