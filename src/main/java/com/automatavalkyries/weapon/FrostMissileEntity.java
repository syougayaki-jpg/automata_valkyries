package com.automatavalkyries.weapon;

import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * アバランチの誘導冷凍ミサイル。打ち上がってから目標へ曲がり、加速していく（目標が居なければ、前方の敵を探す）。
 * 当たると無敵時間を消してから 6 ダメージ（無敵貫通）、凍傷を 1 段重ねる（{@link Frostbite}）。8 秒で自壊する（128 ブロック先まで届く）。撃った者には当たらない。
 * 見た目: 白い機体に光る氷の弾頭（{@code client/FrostMissileRenderer}）、青白い噴射炎、白い飛行機雲。当たると氷の爆発（衝撃波・氷の欠片・雪）。
 */
public class FrostMissileEntity extends ThrowableProjectile {

    public static final float DAMAGE = 6.0F;
    public static final double MAX_SPEED = 1.6, SEEK_RANGE = 128.0;
    public static final int LIFETIME = 160, LAUNCH = 6;

    @Nullable
    private LivingEntity target;

    public FrostMissileEntity(EntityType<? extends FrostMissileEntity> type, Level level) {
        super(type, level);
    }

    public FrostMissileEntity(Level level, LivingEntity shooter, @Nullable LivingEntity target) {
        super(ModWeapons.FROST_MISSILE.get(), shooter, level);
        this.target = target;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected float getGravity() {
        return tickCount < LAUNCH ? 0.02F : 0.0F;
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            if (tickCount > LIFETIME) {
                burst(position());
                return;
            }
            if (tickCount >= LAUNCH) home();
        } else {
            trail();
        }
        super.tick();
    }

    private void home() {
        if (target == null || !target.isAlive()) target = seek();
        Vec3 motion = getDeltaMovement();
        double speed = Math.min(MAX_SPEED, motion.length() + 0.08);
        Vec3 dir = motion.lengthSqr() > 1.0E-6 ? motion.normalize() : getLookAngle();
        if (target != null) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position()).normalize();
            double turn = Math.min(0.45, 0.12 + (tickCount - LAUNCH) * 0.02); // 飛ぶほどよく曲がる
            dir = dir.lerp(to, turn).normalize();
        }
        setDeltaMovement(dir.scale(speed));
    }

    @Nullable
    private LivingEntity seek() {
        Entity owner = getOwner();
        if (owner instanceof LivingEntity living) {
            var list = AvalancheLauncherItem.targets(living);
            if (!list.isEmpty()) return list.get(random.nextInt(list.size()));
        }
        return null;
    }

    /** 噴射炎と飛行機雲（クライアントで）: 青白い炎の芯、外の青い炎、白い煙の筋、ときどき氷のきらめきと雪 */
    private void trail() {
        Vec3 motion = getDeltaMovement();
        Vec3 dir = motion.lengthSqr() > 1.0E-6 ? motion.normalize() : new Vec3(0, 1, 0);
        Vec3 tail = position().subtract(dir.scale(0.7));
        level().addParticle(new FxOptions(ModParticles.GLOW.get(), 0xFFFFFF, 0.16F, 4, 0), tail.x, tail.y, tail.z, 0, 0, 0);
        level().addParticle(new FxOptions(ModParticles.GLOW.get(), 0x8AD8FF, 0.32F, 7, 0), tail.x, tail.y, tail.z, -dir.x * 0.05, -dir.y * 0.05, -dir.z * 0.05);
        level().addParticle(new FxOptions(ModParticles.GLOW.get(), 0x3A78E0, 0.45F, 10, 0), tail.x, tail.y, tail.z, -dir.x * 0.08, -dir.y * 0.08, -dir.z * 0.08);
        // 飛行機雲（間を埋めて切れ目なく）
        for (int k = 0; k < 2; k++) {
            Vec3 p = tail.subtract(motion.scale(k * 0.5));
            level().addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.01, 0.005, (random.nextDouble() - 0.5) * 0.01);
        }
        if (tickCount % 3 == 0) level().addParticle(new FxOptions(ModParticles.SPARK.get(), 0xFFFFFF, 0.14F, 8, 0), tail.x, tail.y, tail.z, 0, 0, 0);
        if (tickCount % 4 == 0) level().addParticle(ParticleTypes.SNOWFLAKE, tail.x, tail.y, tail.z, -dir.x * 0.05, -dir.y * 0.05, -dir.z * 0.05);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel) || !(result.getEntity() instanceof LivingEntity hit)) return;
        hit.invulnerableTime = 0; // 無敵貫通
        hit.hurt(WeaponDamage.source(this, WeaponDamage.FROST_MISSILE, this, getOwner()), DAMAGE);
        Frostbite.apply(hit);
        burst(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide()) burst(result.getLocation());
    }

    /** 氷の爆発: 白い閃光、青白い衝撃波、氷の欠片と雪が散り、冷気の煙が残る */
    private void burst(Vec3 at) {
        if (level() instanceof ServerLevel server) {
            Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 1.0F, 5), at, 1, 0, 0);
            Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), 0x8AD8FF, 1.6F, 10), at, 1, 0, 0);
            Fx.shockwave(server, at, 0xB8F4FF, 2.4F, 10);
            Fx.burst(server, Fx.opt(ModParticles.HEX.get(), Fx.ICE, 0.18F, 16), at, 10, 0.3);
            Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), 0xFFFFFF, 0.25F, 10), at, 6, 0.35);
            server.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.08);
            server.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.25, 0.25, 0.25, 0.03);
            if (random.nextInt(2) == 0) server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5F, 1.5F + random.nextFloat() * 0.4F);
            if (random.nextInt(4) == 0) server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3F, 1.8F);
        }
        discard();
    }
}
