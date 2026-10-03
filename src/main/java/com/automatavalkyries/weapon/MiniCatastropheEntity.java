package com.automatavalkyries.weapon;

import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 特異点砲『カタストロフ・バスター』の通常攻撃の極小のカタストロフ（2026-10-04 ユーザー指定）。直径 0.8 ブロックの小さな特異点が、
 * 前方の敵（モンスターと、撃った者を狙う mob。{@link #SEEK_RANGE} 以内）を追って飛ぶ（毎 Tick 進む向きを的へ曲げる）。
 * 的・壁・何かに当たるか 5 秒で潰れ、半径 {@link #BLAST_RADIUS} の敵に {@link #DAMAGE}（種類 {@code catastrophe}、無敵時間を消す）、
 * まわり {@link #PULL_RADIUS} の者を中心へ引き寄せる。撃った者には当たらない。ブロックは壊さない。描画は {@code client/MiniCatastropheRenderer}。
 */
public class MiniCatastropheEntity extends ThrowableProjectile {

    public static final float SPEED = 1.0F, DAMAGE = 2500.0F, TURN = 0.3F;
    public static final double SEEK_RANGE = 32.0, BLAST_RADIUS = 3.0, PULL_RADIUS = 6.0;
    public static final int LIFETIME = 100;
    public static final int VIOLET = 0xC050FF, CRIMSON = 0xFF3060, EMBER = 0xFF7A30;

    private @Nullable LivingEntity target;
    /** 大きさの倍率（オーバーロードの T4 では {@link Overload#RANGE_MULT}。潰れる範囲・吸い寄せ・見た目に掛ける） */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SCALE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MiniCatastropheEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

    public MiniCatastropheEntity(EntityType<? extends MiniCatastropheEntity> type, Level level) {
        super(type, level);
    }

    public MiniCatastropheEntity(Level level, LivingEntity shooter) {
        super(ModWeapons.MINI_CATASTROPHE.get(), shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SCALE, 1F);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public void setScale(float scale) {
        entityData.set(SCALE, scale);
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    /** 追う的（モンスターと、撃った者を狙う mob） */
    static boolean prey(@Nullable Entity owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e.isSpectator()) return false;
        if (owner != null && e.isAlliedTo(owner)) return false;
        return e instanceof Enemy || (e instanceof Mob mob && owner != null && mob.getTarget() == owner);
    }

    /** 進む向きの前にいる、いちばん近い的 */
    private @Nullable LivingEntity seek() {
        Entity owner = getOwner();
        Vec3 dir = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement().normalize() : getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SEEK_RANGE), e -> prey(owner, e))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(position());
            double d = to.length();
            if (d > SEEK_RANGE || d < 1.0E-3) continue;
            double facing = to.scale(1 / d).dot(dir);
            if (facing < 0.2) continue;
            double score = d * (2 - facing);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            if (tickCount > LIFETIME) {
                implode();
                return;
            }
            if (target == null || !target.isAlive() || tickCount % 10 == 0) target = seek();
            if (target != null) {
                Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
                Vec3 to = aim.subtract(position());
                if (to.length() < 0.6 + target.getBbWidth() * 0.5) {
                    implode();
                    return;
                }
                Vec3 v = getDeltaMovement().normalize().scale(1 - TURN).add(to.normalize().scale(TURN)).normalize().scale(SPEED);
                setDeltaMovement(v);
            }
        } else {
            trail();
        }
        super.tick();
    }

    /** 飛ぶあいだ: まわりを紫と紅の光が巡り、後ろへ暗い尾を引き、空間の破片が吸い込まれる（クライアント） */
    private void trail() {
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        for (int k = 0; k < 2; k++) {
            double a = tickCount * 0.7 + k * Math.PI;
            Vec3 p = c.add(Math.cos(a) * 0.6, Math.sin(a * 0.8) * 0.3, Math.sin(a) * 0.6);
            level().addParticle(new FxOptions(ModParticles.GLOW.get(), k == 0 ? VIOLET : CRIMSON, 0.12F, 6, 0), p.x, p.y, p.z, 0, 0, 0);
        }
        Vec3 back = c.subtract(getDeltaMovement().scale(0.6));
        level().addParticle(new FxOptions(ModParticles.GLOW.get(), 0x300818, 0.45F, 12, 0), back.x, back.y, back.z, 0, 0, 0);
        if (tickCount % 2 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble();
            Vec3 p = c.add(Math.cos(a) * r, (random.nextDouble() - 0.5) * 1.2, Math.sin(a) * r);
            Vec3 v = c.subtract(p).scale(0.12).add(getDeltaMovement());
            level().addParticle(new FxOptions(ModParticles.VOID_SHARD.get(), EMBER, 0.08F, 10, 0), p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /** 潰れる: 引き寄せ、半径 3 の敵へダメージ、閃光と紫の衝撃波 */
    private void implode() {
        if (!(level() instanceof ServerLevel server)) return;
        Entity owner = getOwner();
        LivingEntity causing = owner instanceof LivingEntity living ? living : null;
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        double pullRadius = PULL_RADIUS * scale(), blastRadius = BLAST_RADIUS * scale();
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(pullRadius),
                e -> e != owner && e.isAlive() && !e.isSpectator() && !(owner != null && e.isAlliedTo(owner)))) {
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d > pullRadius + e.getBbWidth()) continue;
            if (d <= blastRadius + e.getBbWidth()) {
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(this, WeaponDamage.CATASTROPHE, this, causing), DAMAGE);
            }
            if (d > 0.3) {
                e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(Math.min(0.8, 2.0 / d))));
                e.hurtMarked = true;
            }
        }
        Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 1.6F, 6), c, 1, 0, 0);
        Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), VIOLET, 1.2F, 10), c, 2, 0.3, 0);
        Fx.shockwave(server, c, VIOLET, (float) blastRadius + 1.5F, 10);
        Fx.shockwave(server, c, CRIMSON, (float) blastRadius, 14);
        Fx.beamRing(server, c, getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement().normalize() : new Vec3(0, 1, 0), VIOLET, 1.8F, 10);
        Fx.burst(server, Fx.opt(ModParticles.VOID_SHARD.get(), EMBER, 0.16F, 16), c, 18, 0.45);
        Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), 0xFFD0F0, 0.3F, 10), c, 12, 0.5);
        server.playSound(null, blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.2F, 0.5F);
        server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F);
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide()) implode();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide()) {
            setPos(result.getLocation());
            implode();
        }
    }
}
