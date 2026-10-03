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
 * 光の帯（見た目だけ。当たり判定は撃った側が済ませる）。始まりから終わりまでの長い光を、寿命をかけて細く薄れさせる。
 * 色の組（{@link #SCHEME_PUNISHMENT} = 真紅・金・白、{@link #SCHEME_GENESIS} = 金・淡金・白、{@link #SCHEME_PRISM} = 虹、{@link #SCHEME_STELLA} = 星の青・水色・白）・太さの倍率・寿命を持つ。
 * パニッシュメント・レールガンのレールと、ジェネシス・ヘイローの光の柱で使う。描画は {@code client/PunishmentBeamRenderer}。
 */
public class PunishmentBeamEntity extends net.minecraft.world.entity.Entity {

    public static final int LIFETIME = 30;
    public static final int SCHEME_PUNISHMENT = 0, SCHEME_GENESIS = 1, SCHEME_PRISM = 2, SCHEME_STELLA = 3;
    private static final EntityDataAccessor<Float> END_X = SynchedEntityData.defineId(PunishmentBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Y = SynchedEntityData.defineId(PunishmentBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Z = SynchedEntityData.defineId(PunishmentBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SCHEME = SynchedEntityData.defineId(PunishmentBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(PunishmentBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(PunishmentBeamEntity.class, EntityDataSerializers.INT);

    public PunishmentBeamEntity(EntityType<? extends PunishmentBeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public PunishmentBeamEntity(Level level, Vec3 start, Vec3 end) {
        this(level, start, end, SCHEME_PUNISHMENT, 1F, LIFETIME);
    }

    public PunishmentBeamEntity(Level level, Vec3 start, Vec3 end, int scheme, float width, int lifetime) {
        this(ModWeapons.PUNISHMENT_BEAM.get(), level);
        // 置く位置: 空から降る柱は地面側の端に置く。クライアントは、居場所の区画（16 ブロックの立方体）の描画の準備ができていないと
        // 実体を描かない。空の区画は画面に入ったときにしか準備されないので、空の端に置くと視点によって柱が消えたり出たりした（2026-10-04 修正）
        boolean fromSky = start.y - end.y > 4;
        Vec3 anchor = fromSky ? end : start, other = fromSky ? start : end;
        setPos(anchor);
        // もう一方の端は置いた位置からの差で持つ（float でも 1,024 ブロック先まで十分な精度）
        entityData.set(END_X, (float) (other.x - anchor.x));
        entityData.set(END_Y, (float) (other.y - anchor.y));
        entityData.set(END_Z, (float) (other.z - anchor.z));
        entityData.set(SCHEME, scheme);
        entityData.set(WIDTH, width);
        entityData.set(LIFE, lifetime);
    }

    /** 置いた位置からもう一方の端までの差（空から降る柱は、地面側から空への向き） */
    public Vec3 span() {
        return new Vec3(entityData.get(END_X), entityData.get(END_Y), entityData.get(END_Z));
    }

    public int scheme() { return entityData.get(SCHEME); }
    public float width() { return entityData.get(WIDTH); }
    public int lifetime() { return Math.max(1, entityData.get(LIFE)); }

    @Override
    protected void defineSynchedData() {
        entityData.define(END_X, 0F);
        entityData.define(END_Y, 0F);
        entityData.define(END_Z, 0F);
        entityData.define(SCHEME, SCHEME_PUNISHMENT);
        entityData.define(WIDTH, 1F);
        entityData.define(LIFE, LIFETIME);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > lifetime()) discard();
    }

    /** 長い光なので、始まりが画面の外でも描く */
    @Override
    public AABB getBoundingBoxForCulling() {
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
