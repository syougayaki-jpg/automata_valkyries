package com.automatavalkyries.entity.chrono;

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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * メメントモリの黒死蝶ビット（2026-10-10 ユーザー指定）。当たった相手に {@link #DAMAGE} と死蝶の呪い（最大 HP を 5% 削る。{@link MementoCurse}）を与えて消える。
 * 攻撃・射撃で一撃で壊せる（壊すと黒い鱗粉と紫の光が散る）。
 * <ul>
 *   <li>乱舞（{@link #SWARM}）: 羽ばたきながら舞い上がってから、相手をゆるく追う（1 Tick に {@link #SWARM_TURN} 度まで曲がる）</li>
 *   <li>檻（{@link #CAGE}）: 相手のまわりの決まった所へ飛んで並び、合図（{@link #release}）まで止まって羽ばたき、合図で今の相手の所へまっすぐ突っ込む</li>
 * </ul>
 * 見た目は client/MementoBitRenderer（黒い翅に冷たい青紫の光る回路、羽ばたく）。保存しない
 */
public class MementoBitEntity extends Entity {

    public static final int SWARM = 0, CAGE = 1;
    public static final float DAMAGE = 8.0F;
    public static final double SWARM_SPEED = 0.55, SWARM_TURN = 6.0, CHARGE_SPEED = 1.1;
    public static final int LIFE = 160, SWARM_RISE = 14;
    private static final EntityDataAccessor<Boolean> RED = SynchedEntityData.defineId(MementoBitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(MementoBitEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private LivingEntity owner, target;
    private int mode = SWARM;
    private Vec3 slot = Vec3.ZERO;
    private float damage = DAMAGE;
    private boolean released;

    public MementoBitEntity(EntityType<? extends MementoBitEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** 乱舞の 1 匹: from から velocity で飛び出し、舞い上がってから target をゆるく追う */
    public static MementoBitEntity swarm(ServerLevel level, LivingEntity owner, LivingEntity target, Vec3 from, Vec3 velocity, float damage, boolean red) {
        MementoBitEntity b = create(level, owner, target, from, damage, red);
        b.mode = SWARM;
        b.setDeltaMovement(velocity);
        level.addFreshEntity(b);
        return b;
    }

    /** 檻の 1 匹: from から相手のまわりの slot（世界の位置）へ飛んで並ぶ */
    public static MementoBitEntity cage(ServerLevel level, LivingEntity owner, LivingEntity target, Vec3 from, Vec3 slot, float damage, boolean red) {
        MementoBitEntity b = create(level, owner, target, from, damage, red);
        b.mode = CAGE;
        b.slot = slot;
        level.addFreshEntity(b);
        return b;
    }

    private static MementoBitEntity create(ServerLevel level, LivingEntity owner, LivingEntity target, Vec3 from, float damage, boolean red) {
        MementoBitEntity b = new MementoBitEntity(ModEntities.MEMENTO_BIT.get(), level);
        b.owner = owner;
        b.target = target;
        b.damage = damage;
        b.entityData.set(RED, red);
        b.setPos(from.x, from.y, from.z);
        return b;
    }

    /** 檻: 今の相手の所へ一斉に突っ込む */
    public void release() {
        if (mode != CAGE || released) return;
        released = true;
        entityData.set(CHARGING, true);
        LivingEntity t = target;
        Vec3 to = t != null && t.isAlive() ? t.position().add(0, t.getBbHeight() * 0.5, 0) : position().add(0, -1, 0);
        Vec3 d = to.subtract(position());
        setDeltaMovement(d.lengthSqr() < 1.0e-4 ? new Vec3(0, -CHARGE_SPEED, 0) : d.normalize().scale(CHARGE_SPEED));
        tickCount = Math.min(tickCount, LIFE - 40); // 突っ込んでから少しは飛ぶ
    }

    public boolean isRed() {
        return entityData.get(RED);
    }

    public boolean isCharging() {
        return entityData.get(CHARGING);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RED, false);
        entityData.define(CHARGING, false);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (level().isClientSide) {
            if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SQUID_INK, getX(), getY(), getZ(), 0, -0.01, 0); // 黒い鱗粉
            return;
        }
        if (!(level() instanceof ServerLevel server) || tickCount > LIFE || owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        if (mode == SWARM) {
            if (tickCount < SWARM_RISE) {
                v = v.scale(0.9).add(0, 0.02, 0);                                  // 舞い上がる
            } else if (target != null && target.isAlive()) {
                Vec3 want = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position()).normalize();
                Vec3 cur = v.lengthSqr() > 1.0e-4 ? v.normalize() : want;
                double ang = Math.acos(Math.max(-1.0, Math.min(1.0, cur.dot(want))));
                double max = Math.toRadians(SWARM_TURN);
                Vec3 dir = ang <= max ? want : cur.add(want.subtract(cur).scale(max / ang)).normalize();
                double flutter = Math.sin((tickCount + getId()) * 0.7) * 0.12;        // ひらひら
                v = dir.scale(SWARM_SPEED).add(0, flutter, 0);
            }
        } else if (!released) {                                                     // 檻: 決まった所へ
            Vec3 to = slot.subtract(position());
            v = to.length() < 0.3 ? new Vec3(0, Math.sin((tickCount + getId()) * 0.5) * 0.04, 0) : to.scale(0.18);
        }
        setDeltaMovement(v);
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        // 当たり: 攻撃してよい生き物（持ち主の仲間は除く）
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.3), ChronoGazer::canHurt)) {
            if (e == owner) continue;
            DamageSource src = damageSources().indirectMagic(this, owner);
            e.hurt(src, owner instanceof MementoMoriEntity m ? m.damageFor(e, damage) : damage);
            MementoCurse.add(e, 1);
            Fx.burst(server, MementoMoriEntity.glow(MementoMoriEntity.C_VIOLET, 0.5F, 12), position(), 10, 0.15);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 0.8F, 1.4F);
            discard();
            return;
        }
        if (!level().noCollision(this, getBoundingBox()) && (mode == SWARM ? tickCount > SWARM_RISE : released)) burst(server);
    }

    /** 壊れる（地形に当たった・撃ち落とされた）: 黒い鱗粉と紫の光 */
    private void burst(ServerLevel server) {
        server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        Fx.burst(server, MementoMoriEntity.glow(isRed() ? MementoMoriEntity.C_CRIMSON : MementoMoriEntity.C_VIOLET, 0.4F, 10), position(), 6, 0.12);
        discard();
    }

    /** 攻撃・射撃で一撃で壊れる */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || source.getEntity() == owner) return false;
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.6F, 1.6F);
            burst(server);
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
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
