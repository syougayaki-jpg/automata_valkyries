package com.automatavalkyries.entity.seer;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.chrono.ChronoGazer;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * データの立方体（2026-10-26 ユーザー指定「アカシックちゃんを一般ボスとしておまけとして実装する」）: 事象観測姫アカシックの弾と、
 * 観測の立方体（{@link com.automatavalkyries.item.AkashicCubesItem}）を持ったプレイヤーの弾。光る桃・水色の立方体が回りながら飛ぶ（重力なし）。
 * 追尾（{@link #homing}）・落ちて砕けると周りへ（{@link #blast}）を選べる。ボスの弾はプレイヤーと兵姫に、プレイヤーの弾は敵のモブにだけ当たる。
 * ボスの弾は 2026-10-26 ユーザー指定「一般アカシックちゃんの弾とかを前の正２０面体に置き換えて」で、立方体でなく桃・薄紫・水色の正二十面体の結晶（{@link #crystal}）。
 * 結晶の雨の大きな結晶は、砕けると小さな欠片を横へ {@value #SHARDS} つ散らす（{@value #SHARD_DAMAGE}）
 */
public class AkashicCubeEntity extends ThrowableProjectile {

    public static final int PINK = 0xFF5AD8, CYAN = 0x4FE3FF, VIOLET = 0xB07CFF;
    public static final int SHARDS = 6;
    public static final float SHARD_DAMAGE = 3.0F;
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(AkashicCubeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(AkashicCubeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> CRYSTAL = SynchedEntityData.defineId(AkashicCubeEntity.class, EntityDataSerializers.BOOLEAN);

    private float damage = 6.0F, blast;
    private int life = 120, homingTicks;
    @Nullable
    private LivingEntity homing;

    public AkashicCubeEntity(EntityType<? extends AkashicCubeEntity> type, Level level) {
        super(type, level);
    }

    /** from から向き dir へ速さ speed で撃つ */
    public static AkashicCubeEntity shoot(LivingEntity owner, Vec3 from, Vec3 dir, float speed, float damage, int color, float size) {
        AkashicCubeEntity cube = new AkashicCubeEntity(ModEntities.AKASHIC_CUBE.get(), owner.level());
        cube.setOwner(owner);
        cube.damage = damage;
        cube.entityData.set(COLOR, color);
        cube.entityData.set(SIZE, size);
        cube.setPos(from.x, from.y, from.z);
        cube.shoot(dir.x, dir.y, dir.z, speed, 0.0F);
        owner.level().addFreshEntity(cube);
        return cube;
    }

    /** 追いかける（ticks の間、向きを少しずつ相手へ曲げる） */
    public AkashicCubeEntity homing(LivingEntity target, int ticks) {
        this.homing = target;
        this.homingTicks = ticks;
        return this;
    }

    /** 当たると砕けて半径 radius へ（落ちてくる大きな立方体） */
    public AkashicCubeEntity blast(float radius) {
        this.blast = radius;
        return this;
    }

    public AkashicCubeEntity life(int ticks) {
        this.life = ticks;
        return this;
    }

    /** 正二十面体の結晶にする（事象観測姫アカシックの弾） */
    public AkashicCubeEntity crystal() {
        entityData.set(CRYSTAL, true);
        return this;
    }

    public boolean isCrystal() {
        return entityData.get(CRYSTAL);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLOR, CYAN);
        entityData.define(SIZE, 0.18F);
        entityData.define(CRYSTAL, false);
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && homing != null && tickCount < homingTicks) {
            if (homing.isAlive()) {
                Vec3 v = getDeltaMovement();
                double speed = v.length();
                Vec3 want = homing.position().add(0, homing.getBbHeight() * 0.5, 0).subtract(position()).normalize().scale(speed);
                setDeltaMovement(v.add(want.subtract(v).scale(0.12)).normalize().scale(speed));
            } else {
                homing = null;
            }
        }
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > life) {
            shatter();
            discard();
        }
    }

    /** 当ててよい相手か: ボスの弾はプレイヤーと兵姫（クロノゲイザーの相手）、プレイヤーの弾は敵のモブだけ */
    boolean target(Entity e) {
        if (!(e instanceof LivingEntity l) || !l.isAlive() || e == getOwner()) return false;
        if (getOwner() instanceof com.automatavalkyries.entity.akashic.AkashicProbeEntity probe) {
            return probe.duelEngine().isValidTarget(l);
        }
        if (getOwner() instanceof Player) return e instanceof Enemy && !(e instanceof ValkyrieEntity);
        return ChronoGazer.canHurt(e) && !(e instanceof AkashicSeerEntity);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && target(e) && (damage > 0.0F || blast > 0.0F);   // 見た目だけの結晶（ダメージ 0）は体をすり抜ける
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide || blast > 0.0F) return;
        LivingEntity owner = getOwner() instanceof LivingEntity l ? l : null;
        hit.getEntity().hurt(source(owner), damage);
        if (owner instanceof AkashicSeerEntity seer) {
            seer.observed(3.0F);                                                           // 当てると論理突破率が上がり、観測の印が付く
            if (hit.getEntity() instanceof LivingEntity l) seer.mark(l);
        } else if (owner instanceof com.automatavalkyries.entity.akashic.AkashicProbeEntity probe && hit.getEntity() instanceof LivingEntity l) {
            probe.duelEngine().onCrystalHit(l, damage, color(), this);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level().isClientSide) return;
        if (blast > 0.0F && level() instanceof ServerLevel server) {
            LivingEntity owner = getOwner() instanceof LivingEntity l ? l : null;
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(blast), this::target)) {
                if (e.distanceTo(this) > blast + e.getBbWidth() / 2) continue;
                e.hurt(source(owner), damage);
                if (owner instanceof AkashicSeerEntity seer) seer.observed(2.0F);
                else if (owner instanceof com.automatavalkyries.entity.akashic.AkashicProbeEntity probe) {
                    probe.duelEngine().onCrystalHit(e, damage, color(), this);
                }
            }
            Fx.shockwave(server, position(), color(), blast + 0.5F, 12);
            if (isCrystal() && owner != null) {                                             // 大きな結晶は砕けて、小さな欠片が横へ散る
                int shardCount = owner instanceof com.automatavalkyries.entity.akashic.AkashicProbeEntity ? 8 : SHARDS;
                for (int i = 0; i < shardCount; i++) {
                    double a = Math.PI * 2 * i / shardCount + random.nextDouble() * 0.4;
                    shoot(owner, position().add(0, 0.4, 0), new Vec3(Math.cos(a), 0.05, Math.sin(a)), 0.45F, SHARD_DAMAGE, i % 2 == 0 ? VIOLET : color(), 0.09F)
                            .crystal().life(16);
                }
            }
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.6F, 0.7F);
        }
        shatter();
        discard();
    }

    /** ダメージの種類: アカシックの弾は色で物理・魔法・虚数（{@link AkashicSeerEntity#damageOf}）、観測の立方体の弾はふつうの飛び道具 */
    net.minecraft.world.damagesource.DamageSource source(@Nullable LivingEntity owner) {
        if (owner instanceof AkashicSeerEntity seer) return seer.damageOf(color(), this);
        if (owner instanceof com.automatavalkyries.entity.akashic.AkashicProbeEntity probe) return probe.damageOf(color(), this);
        return damageSources().mobProjectile(this, owner);
    }

    /** 砕けて光の欠片になる */
    void shatter() {
        if (!(level() instanceof ServerLevel server)) return;
        Fx.burst(server, MementoMoriEntity.glow(color(), 0.25F, 14), position(), 10 + (int) (size() * 30), 0.12);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 0.6F, 1.4F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putFloat("Blast", blast);
        tag.putInt("Color", color());
        tag.putFloat("Size", size());
        tag.putBoolean("Crystal", isCrystal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        blast = tag.getFloat("Blast");
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(SIZE, tag.getFloat("Size"));
        entityData.set(CRYSTAL, tag.getBoolean("Crystal"));
    }
}
