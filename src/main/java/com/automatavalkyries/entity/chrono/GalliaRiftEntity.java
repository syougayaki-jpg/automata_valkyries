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

/**
 * 歪曲破界砲（列車砲の全開射撃を改名。2026-10-10）の着弾地点の目印（2026-10-10 ユーザー指定「着弾地点の景色が一瞬歪むようにして」）。
 * 見た目だけ: {@link #LIFE} Tick だけ残り、client/GalliaRiftRenderer が画面のその場所のまわりの景色を歪ませる（client/RiftWarp）。保存しない
 */
public class GalliaRiftEntity extends Entity {

    public static final int LIFE = 26;
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(GalliaRiftEntity.class, EntityDataSerializers.FLOAT);

    public GalliaRiftEntity(EntityType<? extends GalliaRiftEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** at に、半径 radius（ブロック）の歪みを出す */
    public static void spawn(ServerLevel level, Vec3 at, float radius) {
        GalliaRiftEntity rift = new GalliaRiftEntity(ModEntities.GALLIA_RIFT.get(), level);
        rift.setPos(at.x, at.y, at.z);
        rift.entityData.set(RADIUS, radius);
        level.addFreshEntity(rift);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 8.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFE) discard();
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
