package com.automatavalkyries.weapon;

import com.automatavalkyries.config.WeaponConfig;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * カタストロフ・バスターのブラックホール弾（直径 5 ブロック）。重力の無い弾として進み、何かに当たるか 3 秒で止まって崩壊を始める。
 * 崩壊: 1 秒のあいだ周り 8 ブロックの者を中心へ引き寄せ、最後に半径 5 の空間を潰す。範囲の敵へ「防御無視・無敵無視・カンストダメージ（2,147,483,647 固定）」
 * （ダメージの種類 {@link WeaponDamage#CATASTROPHE}、無敵時間を消してから当てる）。撃った者には当たらない。
 * ブロックは設定 {@code blockDestruction} のときだけ半径 4 を消す（壊れないブロックは残す。落とし物は出ない）。
 */
public class BlackHoleEntity extends ThrowableProjectile {

    /** カンストダメージ（int の上限）。保護対象（Eprotect のボス）には上限が掛かる */
    public static final long DAMAGE_VALUE = Integer.MAX_VALUE;
    public static final float SPEED = 1.2F, DAMAGE = (float) DAMAGE_VALUE;
    public static final int FLIGHT = 60, COLLAPSE = 20;
    public static final double PULL_RANGE = 8.0, BLAST_RADIUS = 5.0, BLOCK_RADIUS = 4.0;
    private static final EntityDataAccessor<Integer> COLLAPSING = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);

    public BlackHoleEntity(EntityType<? extends BlackHoleEntity> type, Level level) {
        super(type, level);
    }

    public BlackHoleEntity(Level level, LivingEntity shooter) {
        super(ModWeapons.BLACK_HOLE.get(), shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLLAPSING, -1);
    }

    /** 崩壊を始めてからの Tick（-1 = まだ飛んでいる） */
    public int collapseAge() {
        return entityData.get(COLLAPSING);
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        int age = collapseAge();
        if (age >= 0) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide()) {
                if (age >= COLLAPSE) {
                    detonate();
                    return;
                }
                pull();
                entityData.set(COLLAPSING, age + 1);
            }
            baseTick();
            return;
        }
        if (!level().isClientSide() && tickCount > FLIGHT) {
            startCollapse();
            return;
        }
        if (level() instanceof ServerLevel server) {
            // 飛ぶあいだ: 円盤のまわりを空間の破片と橙の光が巡り、後ろへ尾を引く
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            for (int k = 0; k < 3; k++) {
                double a = tickCount * 0.5 + k * Math.PI * 2 / 3;
                Vec3 p = c.add(Math.cos(a) * 2.6, Math.sin(a * 0.5) * 0.4, Math.sin(a) * 2.6);
                Fx.one(server, Fx.opt(com.automatavalkyries.registry.ModParticles.VOID_SHARD.get(), Fx.ORANGE, 0.14F, 14), p, new Vec3(-Math.sin(a) * 0.15, 0, Math.cos(a) * 0.15));
            }
            Fx.spray(server, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.EMBER, 0.9F, 12), c, 2, 0.8, 0.01);
            Fx.spray(server, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), 0x401020, 1.4F, 16), c.subtract(getDeltaMovement().scale(2)), 1, 0.3, 0);
        }
        super.tick();
    }

    private void startCollapse() {
        entityData.set(COLLAPSING, 0);
        setDeltaMovement(Vec3.ZERO);
        level().playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
        level().playSound(null, blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    /** 周りの者を中心へ引き寄せる */
    private void pull() {
        Entity owner = getOwner();
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(PULL_RANGE), e -> e != owner && !e.isSpectator() && (e instanceof LivingEntity || e instanceof net.minecraft.world.entity.item.ItemEntity))) {
            Vec3 to = position().subtract(e.position());
            double d = Math.max(0.5, to.length());
            e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(Math.min(0.6, 2.0 / d))));
            e.hurtMarked = true;
        }
        if (level() instanceof ServerLevel server) {
            // 引き寄せ: 空間の破片と光が渦を巻いて中心へ吸い込まれ、輪が縮む
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            int age = collapseAge();
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * 5;
                Vec3 p = c.add(Math.cos(a) * r, (random.nextDouble() - 0.5) * 4, Math.sin(a) * r);
                Vec3 v = c.subtract(p).scale(0.09).add(new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.25));
                Fx.one(server, Fx.opt(i % 2 == 0 ? com.automatavalkyries.registry.ModParticles.VOID_SHARD.get() : com.automatavalkyries.registry.ModParticles.GLOW.get(), i % 2 == 0 ? Fx.ORANGE : Fx.EMBER, i % 2 == 0 ? 0.16F : 0.5F, 12), p, v);
            }
            if (age % 5 == 0) Fx.shockwave(server, c, 0x801020, 7.0F - age * 0.25F, 6);
        }
    }

    /** 空間崩壊 */
    private void detonate() {
        if (!(level() instanceof ServerLevel server)) return;
        Entity owner = getOwner();
        LivingEntity causing = owner instanceof LivingEntity living ? living : null;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BLAST_RADIUS),
                e -> e != owner && e.isAlive() && e.distanceTo(this) <= BLAST_RADIUS + e.getBbWidth())) {
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(this, WeaponDamage.CATASTROPHE, this, causing), DAMAGE);
            Fx.number(server, new Vec3(target.getX(), target.getY() + target.getBbHeight() + 0.5, target.getZ()), DAMAGE_VALUE, 0xFFD166);
        }
        if (WeaponConfig.blockDestruction()) {
            BlockPos center = blockPosition();
            int r = (int) Math.ceil(BLOCK_RADIUS);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                if (pos.distSqr(center) > BLOCK_RADIUS * BLOCK_RADIUS) continue;
                BlockState state = server.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(server, pos) < 0) continue;
                server.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // 空間崩壊: 白い閃光、何重もの衝撃波、飛び散る空間の破片と光
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        Fx.spray(server, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 4.5F, 10), c, 1, 0, 0);
        Fx.spray(server, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.EMBER, 3.2F, 16), c, 4, 1.0, 0);
        Fx.shockwave(server, c, Fx.WHITE, 12.0F, 14);
        Fx.shockwave(server, c, Fx.ORANGE, 9.0F, 20);
        Fx.shockwave(server, c.add(0, 1.2, 0), 0xB050FF, 7.0F, 18);
        Fx.shockwave(server, c.add(0, -1.2, 0), 0xB050FF, 7.0F, 18);
        Fx.burst(server, Fx.opt(com.automatavalkyries.registry.ModParticles.VOID_SHARD.get(), Fx.ORANGE, 0.25F, 30), c, 60, 0.9);
        Fx.burst(server, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.WHITE, 0.5F, 14), c, 30, 0.8);
        Fx.burst(server, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.EMBER, 0.8F, 24), c, 30, 0.5);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F, 0.4F);
        server.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.4F);
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide() && collapseAge() < 0) startCollapse();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide() && collapseAge() < 0) {
            setPos(result.getLocation());
            startCollapse();
        }
    }
}
