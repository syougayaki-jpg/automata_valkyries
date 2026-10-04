package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * ネクロシス・ニードルガンの誘導針。重力が無く、前方 60 度・12 ブロック以内の敵（撃った者が最後に殴った相手を優先）へ曲がる。
 * 当たると 14.0 ダメージと黒死侵食。4 秒で消える。見た目は緑の粒子だけ。
 */
public class NecrosisNeedleEntity extends ThrowableProjectile {

    public static final float SPEED = 1.8F, DAMAGE = 35.0F;
    public static final double SEEK_RANGE = 12.0, TURN = 0.25;
    public static final int LIFETIME = 80;
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.44F, 0.82F, 0.63F), 0.7F);

    @Nullable
    private LivingEntity target;

    public NecrosisNeedleEntity(EntityType<? extends NecrosisNeedleEntity> type, Level level) {
        super(type, level);
    }

    public NecrosisNeedleEntity(Level level, LivingEntity shooter) {
        super(ModWeapons.NECROSIS_NEEDLE.get(), shooter, level);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            if (tickCount > LIFETIME) {
                discard();
                return;
            }
            seek();
        } else {
            level().addParticle(new com.automatavalkyries.particle.FxOptions(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.GREEN, 0.18F, 10, 0), getX(), getY(), getZ(), 0, 0, 0);
            if (tickCount % 2 == 0) level().addParticle(new com.automatavalkyries.particle.FxOptions(com.automatavalkyries.registry.ModParticles.VOID_SHARD.get(), Fx.GREEN, 0.06F, 12, 0), getX(), getY(), getZ(), 0, 0, 0);
        }
        super.tick();
    }

    private void seek() {
        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-4) return;
        if (target == null || !target.isAlive() || target.distanceToSqr(this) > SEEK_RANGE * SEEK_RANGE * 4) target = findTarget(motion);
        if (target == null) return;
        Vec3 toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position()).normalize();
        Vec3 dir = motion.normalize().lerp(toTarget, TURN).normalize();
        setDeltaMovement(dir.scale(SPEED));
    }

    @Nullable
    private LivingEntity findTarget(Vec3 motion) {
        Entity owner = getOwner();
        Vec3 forward = motion.normalize();
        LivingEntity preferred = owner instanceof LivingEntity living ? living.getLastHurtMob() : null;
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SEEK_RANGE),
                e -> e.isAlive() && e != owner && !e.isSpectator() && (owner == null || !e.isAlliedTo(owner)))) {
            if (!(candidate instanceof Enemy) && candidate != preferred) continue;
            Vec3 to = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(position());
            if (to.normalize().dot(forward) < 0.5) continue; // 前方 60 度
            double distance = to.lengthSqr() * (candidate == preferred ? 0.25 : 1.0);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel level) || !(result.getEntity() instanceof LivingEntity hit)) return;
        Entity owner = getOwner();
        hit.hurt(damageSources().thrown(this, owner), DAMAGE);
        Necrosis.apply(hit);
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(hit.getX(), hit.getY(0.6), hit.getZ());
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.VOID_SHARD.get(), Fx.GREEN, 0.1F, 20), at, 10, 0.2);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), 0x2E8A54, 0.6F, 12), at, 3, 0.2, 0.01);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(TRAIL, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0);
            discard();
        }
    }
}
