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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 踏みつけ・重拳の衝撃の見た目（2026-10-10 ユーザー指定「踏みつけの衝撃を自作して豪華にして」）。当たり判定はガリアフォートの側（quake）で済ませ、
 * これは見た目だけ: 着いた所から、衝撃の壁が速さ {@link #SPEED}（ブロック / Tick。当たり判定の輪と同じ）で最大の半径まで広がる。
 * 描き方は client/GalliaQuakeRenderer（閃光・地割れ・光の壁・飛び散る地面のかけら。かけらは着いた所の地面のブロック）。保存しない
 */
public class GalliaQuakeEntity extends Entity {

    public static final float SPEED = 0.7F;
    private static final EntityDataAccessor<Float> MAX_RADIUS = SynchedEntityData.defineId(GalliaQuakeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GROUND = SynchedEntityData.defineId(GalliaQuakeEntity.class, EntityDataSerializers.INT);

    public GalliaQuakeEntity(EntityType<? extends GalliaQuakeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** at（地面の上）から半径 maxRadius まで広がる衝撃。ground は飛び散るかけらのブロック */
    public static void spawn(ServerLevel level, Vec3 at, float maxRadius, BlockState ground) {
        GalliaQuakeEntity q = new GalliaQuakeEntity(ModEntities.GALLIA_QUAKE.get(), level);
        q.setPos(at.x, at.y, at.z);
        q.entityData.set(MAX_RADIUS, maxRadius);
        q.entityData.set(GROUND, Block.getId(ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground));
        level.addFreshEntity(q);
    }

    public float maxRadius() {
        return entityData.get(MAX_RADIUS);
    }

    public BlockState ground() {
        return Block.stateById(entityData.get(GROUND));
    }

    /** 壁が最大の半径に着いてから消えるまで 16 Tick */
    public int life() {
        return (int) Math.ceil(maxRadius() / SPEED) + 16;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MAX_RADIUS, 8.0F);
        entityData.define(GROUND, Block.getId(Blocks.DIRT.defaultBlockState()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > life()) discard();
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
