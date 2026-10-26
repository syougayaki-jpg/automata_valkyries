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
 *   <li>{@link #BLADE}: 因果断ちの大剣。置いた所を根元に、空へ立った巨大な時空剪定の針が {@link #mode()} Tick かけて yaw の向きへゆっくり倒れ、床の赤い帯へ落ちる（2026-10-20）</li>
 *   <li>{@link #CAUSAL}: 因果停止と因果断絶（{@link CausalLock}。2026-10-20 ユーザー指定「因果断絶の演出を自作して美麗に」）。mode Tick の間は止まった者を縛る因果の糸・止まっていく 3 つの輪・時の結晶の殻・ひび割れる床の文字盤、明けると一閃で糸が断たれ殻が砕け散る（size = 止まった者の背の高さ）</li>
 *   <li>{@link #PENDULUM}・{@link #HOURGLASS}・{@link #SECTOR}・{@link #LANE}: 振り子の断頭台・砂時計の牢・崩れる文字盤の扇・時空疾走の道（2026-10-20）。{@link #SWEEP} は mode に刃の本数（0 なら 12）</li>
 *   <li>{@link #GEAR}: 時の歯車の挟撃の、床を転がる巨大な縦の歯車（yaw の向きへ 1 Tick に spin ずつ進み、進んだ分だけ回る。2026-10-20）</li>
 * </ul>
 * 文字盤は {@link #HANDS_FAST}・{@link #HANDS_SLOW} で針の速さを、{@link #FILL} で盤を薄く塗る（加速と減速の場）
 * 時間停止の間は（止められているので）エフェクトも止まる。保存しない。{@link #life()} Tick で消える
 */
public class RulerFxEntity extends Entity {

    public static final int CLOCK = 0, SWEEP = 1, PILLAR = 2, CUBE = 3, SLASH = 4, BLADE = 5, GEAR = 6, CAUSAL = 7, PENDULUM = 8, HOURGLASS = 9, SECTOR = 10, LANE = 11;
    /**
     * 零号機の赤黒い天災の技の専用エフェクト（2026-10-26。描くのは client/ZeroFxRenderer）:
     * 落雷（size = 高さ）・終の大波（size = 半分の幅、yaw = 向き、spin = 切れ目、mode = 盛り上がる Tick）・竜巻（size = 高さ、mode = 育つ Tick。サーバーが動かす）・
     * 焔の扇（size = 半径、yaw = 向き、spin = 開き、mode = 噴き上がる Tick）・黒雷の十字架（size = 腕の長さ、yaw = 初めの角度、spin = 1 Tick に回る角度、mode = 予告の Tick）・
     * 焔の輪の波（size = 届く半径、yaw = 初めの半径、spin = 1 Tick に広がる m、mode = 広がり始める Tick）・黙示の大嵐（size = 初めの半径、yaw = 初めの角度、mode = 始まる Tick）・
     * 赫焉天墜（size = 半径、mode = 墜ちる Tick）
     */
    public static final int Z_BOLT = 20, Z_TIDE = 21, Z_TWISTER = 22, Z_FLAME = 23, Z_CROSS = 24, Z_RING = 25, Z_TEMPEST = 26, Z_HEAVEN = 27;
    /** 地面から突き出す光る 7 セグの数字（事象観測姫アカシックの結晶槍。2026-10-26。mode = 0 か 1、size = 高さ。描くのは client/ZeroFxRenderer） */
    public static final int Z_DIGIT = 28;
    /** 地面から突き出す結晶の房（結晶槍の棘。2026-10-26 ユーザー指定「結晶槍自体も結晶らしくして」。size = 高さ、color = 結晶の色） */
    public static final int Z_SPIKE = 29;
    /** {@link #LANE} の mode に重ねる印: 駆け抜けた後に立つ数字を、ローマ数字でなく 7 セグの 0 と 1 にする（事象観測姫アカシックの結晶槍。2026-10-26 ユーザー指定「結晶槍のローマ数字をセグに変えてほしかった」） */
    public static final int LANE_SEGMENTS = 1 << 12;
    public static final int FLAT = 0, FACING = 1, HANDS_REVERSE = 2, HANDS_TO_TWELVE = 4, HANDS_FINALE = 8, HANDS_FAST = 16, HANDS_SLOW = 32, FILL = 64;
    /** 因果停止（{@link #CAUSAL}）が明けてから消えるまでの Tick（因果断絶の演出） */
    public static final int SEVER_TICKS = 34;
    public static final int GOLD = 0xFFD166, VIOLET = 0xB070FF, RED = 0xFF4060, WHITE = 0xFFF8E8, BLUE = 0x60A8FF;

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
        if (kind() == GEAR && tickCount <= mode()) {                                 // 転がる歯車は両方の側で同じだけ進める（mode = 転がる Tick）
            float y = yaw(), step = spin();
            setPos(getX() + Math.cos(y) * step, getY(), getZ() + Math.sin(y) * step);
        }
        if (!level().isClientSide && tickCount >= life()) discard();
    }

    /** 転がる歯車は自分で進むので、サーバーから届く位置（少し前の位置）で引き戻さない */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (kind() == GEAR) return;
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
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
