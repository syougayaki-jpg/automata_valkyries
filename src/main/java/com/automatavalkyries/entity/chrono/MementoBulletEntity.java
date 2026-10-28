package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * メメントモリの弾幕の 1 発（2026-10-10 ユーザー指定。2026-10-15 ユーザー指定「より弾幕シューティングらしく画面を埋めるほど派手に」で作り直し）。
 * 種類は小さな光弾 {@link #SMALL}・光の蝶弾 {@link #BUTTERFLY}・大玉 {@link #LARGE}・終葬だけの大きな光の蝶 {@link #GRAND}。見た目は大きく光るが、当たるのは芯だけ（{@link #HIT}。弾幕シューティングと同じ）。
 * 動き: はじめ delay Tick その場で止まり（止まって並んでから一斉に動く型）、それから軸のまわりに 1 Tick に curl ラジアンずつ曲がり（渦巻き・花・格子）、
 * 速さを 1 Tick に accel 倍ずつ変える（maxSpeed まで。ゆっくり開いてから速くなる型）。reaim を付けると止まり終わりに相手へ向き直す。
 * 当たった相手に damage（防具・エンチャント・耐性・盾を通る {@link MementoMoriEntity#BULLET_DAMAGE}。2026-10-15 ユーザー指定）、入れば死蝶の呪い 1 つ（最大 HP 1%）。壊せない。地形に当たると消える。
 * 動きの決まりはクライアントにも送り、クライアントでも同じように動かす（数の多い弾の位置を毎 Tick 送らない）。見た目は client/MementoBulletRenderer
 */
public class MementoBulletEntity extends Entity {

    public static final byte SMALL = 0, BUTTERFLY = 1, LARGE = 2, GRAND = 3;
    /** 当たりの半径（芯だけ。見た目はずっと大きい） */
    static final float[] HIT = {0.12F, 0.2F, 0.38F, 0.28F};
    public static final int LIFE = 110;
    /** 1 つのサーバーに同時にいてよい数（これを超えると撃たない。重さの上限） */
    public static final int MAX_LIVE = 1000;
    private static final double MIN_SPEED = 0.03;
    /** いる数の数え方: 前の Tick に動いた弾の数と、この Tick に撃った数（消え方によらず数え漏れが残らない） */
    private static long countTime = Long.MIN_VALUE;
    private static int ticked, tickedLast, firedNow;

    private static final EntityDataAccessor<Vector3f> AXIS = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> CURL = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> VEL = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> ACCEL = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_SPEED = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MementoBulletEntity.class, EntityDataSerializers.INT);

    @Nullable
    private LivingEntity owner, target;
    private float damage = MementoMoriEntity.BULLET;
    private int life = LIFE;
    private double reaim;
    /** 終葬の弾（被弾後の無敵時間も無視する。サーバーだけ） */
    private boolean finale;
    private Vec3 vel = Vec3.ZERO;

    public MementoBulletEntity(EntityType<? extends MementoBulletEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** from から velocity で撃つ 1 発の決まり（続けて決まりを足し、{@link Shot#fire} で撃つ） */
    public static Shot shot(Vec3 from, Vec3 velocity) {
        return new Shot(from, velocity);
    }

    /** 今いるおよその数（サーバー） */
    public static int live() {
        return tickedLast + firedNow;
    }

    private static void count(long time) {
        if (time == countTime) return;
        countTime = time;
        tickedLast = ticked;
        ticked = 0;
        firedNow = 0;
    }

    public static final class Shot {
        private final Vec3 from, vel;
        private Vec3 axis = new Vec3(0, 1, 0);
        private float curl, accel = 1.0F, maxSpeed = 0.8F, damage = MementoMoriEntity.BULLET;
        private int delay, life = LIFE, color = MementoMoriEntity.C_VIOLET;
        private byte kind = SMALL;
        private double reaim;
        private boolean finale;
        @Nullable
        private LivingEntity target;

        private Shot(Vec3 from, Vec3 vel) {
            this.from = from;
            this.vel = vel;
        }

        /** axis のまわりに 1 Tick に curl ラジアン曲がる */
        public Shot curl(Vec3 axis, float curl) {
            this.axis = axis;
            this.curl = curl;
            return this;
        }

        /** 速さを 1 Tick に accel 倍（maxSpeed まで） */
        public Shot accel(float accel, float maxSpeed) {
            this.accel = accel;
            this.maxSpeed = maxSpeed;
            return this;
        }

        /** はじめ ticks の間その場で止まる */
        public Shot delay(int ticks) {
            this.delay = ticks;
            return this;
        }

        /** 止まり終わりに target へ向き直し、speed で飛ぶ */
        public Shot reaim(LivingEntity target, double speed) {
            this.target = target;
            this.reaim = speed;
            return this;
        }

        public Shot kind(byte kind) {
            this.kind = kind;
            return this;
        }

        public Shot color(int color) {
            this.color = color;
            return this;
        }

        public Shot damage(float damage) {
            this.damage = damage;
            return this;
        }

        public Shot life(int life) {
            this.life = life;
            return this;
        }

        /** 終葬の弾にする（{@link MementoMoriEntity#FINALE_DAMAGE}。被弾後の無敵時間も無視する） */
        public Shot finale() {
            this.finale = true;
            return this;
        }

        /** 撃つ（いる数が上限なら撃たずに null） */
        @Nullable
        public MementoBulletEntity fire(ServerLevel level, LivingEntity owner) {
            count(level.getGameTime());
            if (live() >= MAX_LIVE) return null;
            MementoBulletEntity b = new MementoBulletEntity(ModEntities.MEMENTO_BULLET.get(), level);
            b.owner = owner;
            b.target = target;
            b.reaim = reaim;
            b.finale = finale;
            b.damage = damage;
            b.life = life;
            b.vel = vel;
            b.entityData.set(VEL, vel.toVector3f());
            b.entityData.set(AXIS, axis.toVector3f());
            b.entityData.set(CURL, curl);
            b.entityData.set(ACCEL, accel);
            b.entityData.set(MAX_SPEED, maxSpeed);
            b.entityData.set(DELAY, delay);
            b.entityData.set(KIND, kind);
            b.entityData.set(COLOR, color);
            b.setPos(from.x, from.y, from.z);
            if (!level.addFreshEntity(b)) return null;
            firedNow++;
            return b;
        }
    }

    public byte kind() {
        return entityData.get(KIND);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    /** 止まっている間か */
    public boolean isWaiting() {
        return tickCount <= entityData.get(DELAY);
    }

    /** 今の飛ぶ向き（描画で蝶の向きに使う） */
    public Vec3 velocity() {
        return vel;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AXIS, new Vector3f(0, 1, 0));
        entityData.define(CURL, 0.0F);
        entityData.define(VEL, new Vector3f());
        entityData.define(ACCEL, 1.0F);
        entityData.define(MAX_SPEED, 0.8F);
        entityData.define(DELAY, 0);
        entityData.define(KIND, SMALL);
        entityData.define(COLOR, MementoMoriEntity.C_VIOLET);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (VEL.equals(key)) vel = new Vec3(entityData.get(VEL)); // 撃った時と、止まり終わりの向き直し
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount > life || !(level() instanceof ServerLevel)) {
                discard();
                return;
            }
            count(level().getGameTime());
            ticked++;
        }
        int delay = entityData.get(DELAY);
        if (tickCount <= delay) { // 止まって待つ
            if (!level().isClientSide && tickCount == delay && reaim > 0 && target != null && target.isAlive()) {
                Vec3 aim = owner instanceof MementoMoriEntity m ? m.lead(target, position(), reaim, 0.9) : target.position().add(0, target.getBbHeight() * 0.5, 0); // 動きを読んで向き直る
                Vec3 d = aim.subtract(position());
                if (d.lengthSqr() > 1.0e-4) entityData.set(VEL, d.normalize().scale(reaim).toVector3f());
            }
            return;
        }
        float curl = entityData.get(CURL);
        if (curl != 0.0F) vel = rotate(vel, new Vec3(entityData.get(AXIS)), curl);
        float accel = entityData.get(ACCEL);
        if (accel != 1.0F) {
            double speed = Math.max(MIN_SPEED, Math.min(entityData.get(MAX_SPEED), vel.length() * accel));
            if (vel.lengthSqr() > 1.0e-8) vel = vel.normalize().scale(speed);
        }
        Vec3 next = position().add(vel);
        if (level().isClientSide) {
            setPos(next.x, next.y, next.z);
            // 光の尾（大きい弾ほど濃く。小さな光弾は描画の尾の線だけで、粒は出さない）
            byte kind = kind();
            if (kind == LARGE || kind == GRAND ? tickCount % 2 == 0 : kind == BUTTERFLY && tickCount % 4 == 0) {
                level().addParticle(MementoMoriEntity.glow(color(), kind == BUTTERFLY ? 0.14F : 0.3F, kind == GRAND ? 14 : 8), getX(), getY(), getZ(), 0, 0, 0);
            }
            return;
        }
        ServerLevel server = (ServerLevel) level();
        HitResult hit = level().clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            if (kind() != SMALL || random.nextInt(3) == 0) {
                com.automatavalkyries.weapon.Fx.burst(server, MementoMoriEntity.glow(color(), 0.25F, 10), hit.getLocation(), kind() >= LARGE ? 6 : 2, 0.06);
            }
            discard();
            return;
        }
        setPos(next.x, next.y, next.z);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, core(), ChronoGazer::canHurt)) {
            if (e == owner) continue;
            strike(e);
            com.automatavalkyries.weapon.Fx.burst(server, MementoMoriEntity.glow(MementoMoriEntity.C_WHITE, 0.3F, 8), position(), 4, 0.08);
            discard();
            return;
        }
    }

    /** 当たりの箱（芯だけ。見た目よりずっと小さい） */
    public AABB core() {
        float r = HIT[Math.min(HIT.length - 1, Math.max(0, kind()))];
        return AABB.ofSize(position(), r * 2, r * 2, r * 2);
    }

    /** 当てる: 防御無視のダメージと、入れば死蝶の呪い 1 つ。入ったか */
    public boolean strike(LivingEntity e) {
        boolean landed = owner instanceof MementoMoriEntity m ? e.hurt(m.bulletDamage(this, finale), m.damageFor(e, damage)) : e.hurt(damageSources().indirectMagic(this, owner), damage);
        if (landed) MementoCurse.add(e, MementoCurse.BULLET, owner);
        return landed;
    }

    /** v を axis（正規化しなくてよい）のまわりに ang ラジアン回す（ロドリゲスの回転） */
    static Vec3 rotate(Vec3 v, Vec3 axis, double ang) {
        Vec3 k = axis.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : axis.normalize();
        double c = Math.cos(ang), s = Math.sin(ang);
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1 - c)));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
