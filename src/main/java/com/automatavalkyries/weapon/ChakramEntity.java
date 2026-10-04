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
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * プラズマチャクラムの円盤。行き: 毎 Tick 1.4 ブロックまっすぐ（12 Tick か壁まで）。帰り: 壁をすり抜けて持ち主へ曲がりながら戻る。
 * 行きと帰りで、通った所（半径 0.9）の敵をそれぞれ 1 回ずつ斬る（12 ダメージ）。持ち主に戻るか 3 秒で消える。
 */
public class ChakramEntity extends ThrowableProjectile {

    public static final float SPEED = 1.4F, DAMAGE = 60.0F;
    public static final int OUT_TICKS = 12, LIFETIME = 60;
    public static final double RADIUS = 0.9;
    public static final int COLOR = 0xFF5AD8, CORE = 0xFFE0F8;

    private boolean returning;
    private final Set<Integer> hitOut = new HashSet<>(), hitBack = new HashSet<>();

    public ChakramEntity(EntityType<? extends ChakramEntity> type, Level level) {
        super(type, level);
    }

    public ChakramEntity(Level level, LivingEntity owner) {
        super(ModWeapons.CHAKRAM.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected float getGravity() {
        return 0F;
    }

    @Override
    public void tick() {
        Vec3 before = position();
        baseTick();
        if (level().isClientSide()) {
            trail();
            setPos(before.add(getDeltaMovement()));
            return;
        }
        Entity owner = getOwner();
        if (owner == null || tickCount > LIFETIME) {
            discard();
            return;
        }
        if (!returning && tickCount >= OUT_TICKS) returning = true;
        if (returning) {
            Vec3 to = owner.position().add(0, owner.getBbHeight() * 0.6, 0).subtract(position());
            if (to.length() < 1.5) {
                level().playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.6F);
                discard();
                return;
            }
            // 持ち主へ向きを変える（真後ろへ戻るときも折り返せるよう、今の向きより持ち主の向きを強く混ぜる）
            Vec3 v = getDeltaMovement().normalize().scale(0.4).add(to.normalize().scale(0.6));
            v = (v.lengthSqr() < 1.0E-4 ? to : v).normalize().scale(SPEED * 1.15);
            setDeltaMovement(v);
        }
        Vec3 after = before.add(getDeltaMovement());
        if (!returning) {
            BlockHitResult wall = level().clip(new ClipContext(before, after, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (wall.getType() != HitResult.Type.MISS) {
                after = wall.getLocation();
                returning = true;
                if (level() instanceof ServerLevel server) Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), after, 8, 0.3);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.4F, 1.8F);
            }
        }
        cut(before, after, returning ? hitBack : hitOut);
        setPos(after);
    }

    private void cut(Vec3 before, Vec3 after, Set<Integer> hit) {
        Entity owner = getOwner();
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, new AABB(before, after).inflate(RADIUS + 0.5),
                e -> e != owner && e.isAlive() && !e.isSpectator() && !hit.contains(e.getId())
                        && (e.getBoundingBox().inflate(RADIUS).clip(before, after).isPresent() || e.getBoundingBox().inflate(RADIUS).contains(before)))) {
            hit.add(target.getId());
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(this, WeaponDamage.ENERGY, this, owner), DAMAGE);
            if (level() instanceof ServerLevel server) {
                Vec3 at = new Vec3(target.getX(), target.getY(0.55), target.getZ());
                Fx.slash(server, at, CORE, 1.2F, 5, (float) (random.nextFloat() * Math.PI * 2));
                Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.28F, 9), at, 8, 0.3);
            }
            level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.8F);
        }
    }

    /** 回る円盤の縁から光が散り、輪の尾を引く（クライアントで） */
    private void trail() {
        double spin = tickCount * 0.9;
        for (int k = 0; k < 3; k++) {
            double a = spin + k * Math.PI * 2 / 3;
            Vec3 p = position().add(Math.cos(a) * 0.55, 0, Math.sin(a) * 0.55);
            level().addParticle(new FxOptions(ModParticles.GLOW.get(), COLOR, 0.2F, 8, 0), p.x, p.y, p.z, 0, 0, 0);
        }
        level().addParticle(new FxOptions(ModParticles.GLOW.get(), CORE, 0.35F, 5, 0), getX(), getY(), getZ(), 0, 0, 0);
        if (tickCount % 2 == 0) level().addParticle(new FxOptions(ModParticles.SPARK.get(), CORE, 0.15F, 8, 0), getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {}

    @Override
    protected void onHitBlock(BlockHitResult result) {}
}
