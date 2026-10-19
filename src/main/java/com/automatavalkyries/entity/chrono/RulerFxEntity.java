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
 * タイムルーラーの技の自作エフェクト（見た目だけ。当たりは技の側。2026-10-19 ユーザー指定「攻撃も自作の演出で超豪華に」）。
 * 種類ごとに {@code client/RulerFxRenderer} が光の面で描く:
 * <ul>
 *   <li>{@link #CLOCK}: 光の文字盤（外の輪・12 の時の目盛り・60 の分の刻み・文字の輪・針）。{@link #mode()} で向きと針（{@link #FLAT} 床・{@link #FACING} 向きの面・
 *       {@link #HANDS_REVERSE} 逆回り・{@link #HANDS_TO_TWELVE} 12 時へ寄る・{@link #HANDS_FINALE} 刻の終焉の長針と短針を大きく）</li>
 *   <li>{@link #SWEEP}: 文字盤の掃射の 12 本の光の刃（はじめ 20 Tick は赤い予告の線、それから {@link #spin()} で回る）</li>
 *   <li>{@link #PILLAR}: 空から落ちる光の柱と巻き付いて昇る螺旋の輪</li>
 *   <li>{@link #CUBE}: 確定座標消去の赤い光の立方体（中で回る紫の八面体、上下する走査の面）</li>
 *   <li>{@link #SLASH}: 時針の一閃の巨大な三日月の光の刃</li>
 * </ul>
 * 時間停止の間は（止められているので）エフェクトも止まる。保存しない。{@link #life()} Tick で消える
 */
public class RulerFxEntity extends Entity {

    public static final int CLOCK = 0, SWEEP = 1, PILLAR = 2, CUBE = 3, SLASH = 4;
    public static final int FLAT = 0, FACING = 1, HANDS_REVERSE = 2, HANDS_TO_TWELVE = 4, HANDS_FINALE = 8;
    public static final int GOLD = 0xFFD166, VIOLET = 0xB070FF, RED = 0xFF4060, WHITE = 0xFFF8E8;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(RulerFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(RulerFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(RulerFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(RulerFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(RulerFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(RulerFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPIN = SynchedEntityData.defineId(RulerFxEntity.class, EntityDataSerializers.FLOAT);

    public RulerFxEntity(EntityType<? extends RulerFxEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** at に kind のエフェクトを出す。size = 半径・長さ・幅、yaw = 向き（ラジアン）、spin = 1 Tick に回る角度、life = 消えるまでの Tick */
    public static RulerFxEntity spawn(ServerLevel level, int kind, int mode, Vec3 at, float size, float yaw, float spin, int life, int color) {
        RulerFxEntity fx = new RulerFxEntity(ModEntities.RULER_FX.get(), level);
        fx.setPos(at.x, at.y, at.z);
        fx.entityData.set(KIND, kind);
        fx.entityData.set(MODE, mode);
        fx.entityData.set(SIZE, size);
        fx.entityData.set(YAW, yaw);
        fx.entityData.set(SPIN, spin);
        fx.entityData.set(LIFE, Math.max(1, life));
        fx.entityData.set(COLOR, color);
        level.addFreshEntity(fx);
        return fx;
    }

    public int kind() {
        return entityData.get(KIND);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public float yaw() {
        return entityData.get(YAW);
    }

    public float spin() {
        return entityData.get(SPIN);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, CLOCK);
        entityData.define(MODE, FLAT);
        entityData.define(LIFE, 20);
        entityData.define(COLOR, GOLD);
        entityData.define(SIZE, 1.0F);
        entityData.define(YAW, 0.0F);
        entityData.define(SPIN, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= life()) discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
