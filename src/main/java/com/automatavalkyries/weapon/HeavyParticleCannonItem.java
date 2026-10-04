package com.automatavalkyries.weapon;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 重粒子収束砲（§7.2 weapon_heavy_particle_cannon、T2）: 両手重火器。1.5 秒チャージして離すと、極太の光線を 2 秒照射する
 * （0.2 秒ごとに 20.0 ダメージ。1 射 200,000 FE）。光線は向いた方へ 96 ブロック（元仕様に射程の値は無く、ユーザー指定で 32 の 3 倍）、太さ 1.2 で、線上の全員を貫く。
 * 照射の 0.2 秒ごとの当たりは無敵時間を待たない（毎回入る）。ガリアフォートの「ダメージ 1 固定の場」を無効にする特性は、
 * ガリアフォートを実装したら、ダメージの種類 {@link WeaponDamage#PARTICLE_BEAM} で見分けてつなぐ。
 */
public class HeavyParticleCannonItem extends ChargedWeaponItem {

    public static final int CAPACITY = 4_000_000, PER_SHOT = 200_000, CHARGE = 30, BEAM_TICKS = 40, PULSE = 4;
    public static final float DAMAGE = 100.0F, WEIGHT = 28.0F;
    public static final double RANGE = 96.0, WIDTH = 1.2;
    private static final DustParticleOptions CORE = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 1.0F), 2.0F);
    private static final DustParticleOptions EDGE = new DustParticleOptions(new Vector3f(0.7F, 0.35F, 1.0F), 1.6F);

    public HeavyParticleCannonItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, CHARGE, PER_SHOT);
    }

    @Override
    public float recoil() {
        return 0.8F;
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.VIOLET, 0.25F, 10);
    }

    @Override
    protected void fire(ServerLevel level, LivingEntity user, ItemStack stack) {
        stack.getOrCreateTag().putInt("Beam", BEAM_TICKS);
        level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        CompoundTag tag = stack.getTag();
        if (!(level instanceof ServerLevel server) || tag == null || tag.getInt("Beam") <= 0) return;
        int beam = tag.getInt("Beam");
        tag.putInt("Beam", beam - 1);
        if (!(entity instanceof LivingEntity user) || user.getMainHandItem() != stack) {
            tag.remove("Beam"); // 持ち替えたら照射をやめる
            return;
        }
        beamTick(server, user, BEAM_TICKS - beam);
    }

    /** 照射の 1 Tick（step = 照射を始めてからの Tick）。兵姫に持たせたときも同じ処理 */
    public static void beamTick(ServerLevel level, LivingEntity user, int step) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        Vec3 start = eye.add(look.scale(0.8)).add(0, -0.2, 0);
        Vec3 end = eye.add(look.scale(RANGE));
        HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        Vec3 stop = end;
        // 極太の光線: 白い芯・紫の外層・まわりを螺旋に巡るきらめき。当たった所に衝撃波と光の飛沫
        double length = stop.distanceTo(start);
        Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 0.5F, 3), start, stop, 0.6);
        Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.VIOLET, 1.05F, 3), start, stop, 0.8);
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(look).normalize();
        for (double d = 0; d < length; d += 1.0) {
            for (int k = 0; k < 2; k++) {
                double a = d * 1.3 + step * 0.6 + k * Math.PI;
                Vec3 p = start.add(look.scale(d)).add(side.scale(Math.cos(a) * WIDTH * 0.6)).add(up.scale(Math.sin(a) * WIDTH * 0.6));
                Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.LAVENDER, 0.18F, 6), p, 1, 0, 0);
            }
        }
        if (step % 4 == 0) Fx.shockwave(level, stop, Fx.VIOLET, 2.5F, 8);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.LAVENDER, 0.4F, 10), stop, 5, 0.3);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.WHITE, 0.3F, 8), stop, 4, 0.4);
        if (step % 10 == 0) level.playSound(null, user.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2F, 2.0F);
        if (step % PULSE != 0) return;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, stop).inflate(WIDTH),
                e -> e != user && e.isAlive() && !e.isSpectator() && e.getBoundingBox().inflate(WIDTH * 0.5).clip(start, stop).isPresent())) {
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(user, WeaponDamage.PARTICLE_BEAM, user, user), DAMAGE);
        }
    }

    public static boolean firing(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getInt("Beam") > 0;
    }
}
