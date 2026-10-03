package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * T4 兵装の通常攻撃の見た目（見た目だけ。当たり判定は撃った側が済ませる。2026-10-04 ユーザー指定）。描画は {@code client/T4StrikeRenderer}。
 * <ul>
 *   <li>{@link #SWEEP}: 終の黄昏『ラグナロク』の赤黒い横薙ぎ。置いた位置を中心に、半径 {@link #width} の弧が向き {@link #yaw} から一周する</li>
 *   <li>{@link #THUNDER}: 極光轟雷『パニッシュメント・レールガン』の青白い落雷。地面側（的）に置き、空への差 {@link #span}。形は {@link #seed} から決め、ちらつきで組み替える</li>
 *   <li>{@link #ARC}: 落雷から近くの敵へ跳ぶ放電（始まりから終わりまでのぎざぎざの筋）</li>
 *   <li>{@link #LASER}: 天輪砲『ジェネシス・ヘイロー』のきらきらの光線（手のクリスタルから的まで）</li>
 * </ul>
 */
public class T4StrikeEntity extends net.minecraft.world.entity.Entity {

    public static final int SWEEP = 0, THUNDER = 1, ARC = 2, LASER = 3;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(T4StrikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> END_X = SynchedEntityData.defineId(T4StrikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Y = SynchedEntityData.defineId(T4StrikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Z = SynchedEntityData.defineId(T4StrikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(T4StrikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(T4StrikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(T4StrikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(T4StrikeEntity.class, EntityDataSerializers.INT);

    public T4StrikeEntity(EntityType<? extends T4StrikeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** at に置き、もう一方の端は end（横薙ぎでは使わない）。width は横薙ぎの半径・光の太さ、yaw は横薙ぎの始まりの向き（度） */
    public T4StrikeEntity(Level level, int kind, Vec3 at, Vec3 end, float width, float yaw, int lifetime) {
        this(ModWeapons.T4_STRIKE.get(), level);
        setPos(at);
        entityData.set(KIND, kind);
        entityData.set(END_X, (float) (end.x - at.x));
        entityData.set(END_Y, (float) (end.y - at.y));
        entityData.set(END_Z, (float) (end.z - at.z));
        entityData.set(WIDTH, width);
        entityData.set(YAW, yaw);
        entityData.set(LIFE, lifetime);
        entityData.set(SEED, level.random.nextInt());
    }

    public int kind() { return entityData.get(KIND); }
    public Vec3 span() { return new Vec3(entityData.get(END_X), entityData.get(END_Y), entityData.get(END_Z)); }
    public float width() { return entityData.get(WIDTH); }
    public float yaw() { return entityData.get(YAW); }
    public int lifetime() { return Math.max(1, entityData.get(LIFE)); }
    public int seed() { return entityData.get(SEED); }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, SWEEP);
        entityData.define(END_X, 0F);
        entityData.define(END_Y, 0F);
        entityData.define(END_Z, 0F);
        entityData.define(WIDTH, 1F);
        entityData.define(YAW, 0F);
        entityData.define(LIFE, 10);
        entityData.define(SEED, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > lifetime()) discard();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (kind() == SWEEP) return new AABB(position(), position()).inflate(width() + 2, 3, width() + 2);
        return new AABB(position(), position().add(span())).inflate(2 + width() * 2);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
