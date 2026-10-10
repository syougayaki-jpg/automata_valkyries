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
 * 穿孔破城砲の光の束（2026-10-10 ユーザー指定「穿孔破城砲も自作して豪華にして」）。当たり判定はガリアフォートの側（{@link GalliaFortEntity#fireCannon}）で済ませ、
 * これは見た目だけ: 砲口から向き・長さ・太さを持って {@link #LIFE} Tick だけ残り、client/GalliaSiegeBeamRenderer が描く
 * （回る穿孔の弾頭が 3 Tick で伸び切り、白い芯・赤い層・紫のにじみの 3 層の柱に、三原色の 3 本のらせんが巻き付き、衝撃の輪が広がって、細りながら消える）。保存しない
 */
public class GalliaSiegeBeamEntity extends Entity {

    public static final int LIFE = 24, EXTEND = 3;
    /** 歪曲破界砲の光の束（MODE_RIFT。2026-10-10「演出を自作して豪華にして」）の残る Tick と伸び切る Tick */
    public static final int LIFE_RIFT = 40, EXTEND_RIFT = 4;
    public static final int MODE_SIEGE = 0, MODE_RIFT = 1;
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(GalliaSiegeBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(GalliaSiegeBeamEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(GalliaSiegeBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(GalliaSiegeBeamEntity.class, EntityDataSerializers.FLOAT);

    public GalliaSiegeBeamEntity(EntityType<? extends GalliaSiegeBeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** from から dir（正規化済み）へ長さ length・太さ width（半径の目安、ブロック）の光の束を出す */
    public static void spawn(ServerLevel level, Vec3 from, Vec3 dir, float length, float width) {
        spawn(level, from, dir, length, width, MODE_SIEGE);
    }

    /** mode = MODE_SIEGE（穿孔破城砲）・MODE_RIFT（歪曲破界砲: 太く長く残り、らせん 2 重・回る立方体の枠・流れる輪を足す） */
    public static void spawn(ServerLevel level, Vec3 from, Vec3 dir, float length, float width, int mode) {
        GalliaSiegeBeamEntity beam = new GalliaSiegeBeamEntity(ModEntities.GALLIA_SIEGE_BEAM.get(), level);
        beam.entityData.set(MODE, mode);
        beam.setPos(from.x, from.y, from.z);
        beam.entityData.set(DIR, dir.toVector3f());
        beam.entityData.set(LENGTH, length);
        beam.entityData.set(WIDTH, width);
        level.addFreshEntity(beam);
    }

    public Vec3 dir() {
        return new Vec3(entityData.get(DIR));
    }

    public float length() {
        return entityData.get(LENGTH);
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public boolean rift() {
        return entityData.get(MODE) == MODE_RIFT;
    }

    public int life() {
        return rift() ? LIFE_RIFT : LIFE;
    }

    public int extendTicks() {
        return rift() ? EXTEND_RIFT : EXTEND;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DIR, new Vector3f(0, 0, 1));
        entityData.define(LENGTH, 1.0F);
        entityData.define(WIDTH, 1.0F);
        entityData.define(MODE, MODE_SIEGE);
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
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
