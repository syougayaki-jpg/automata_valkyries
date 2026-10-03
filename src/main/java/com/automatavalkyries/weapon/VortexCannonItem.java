package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * ヴォルテックスキャノン（T2、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 両手の渦動砲。
 * 撃った先（32 ブロック、最初の敵か壁）に真空の渦を 2 秒作り、半径 7 の敵を中心へ吸い寄せ続ける。
 * 終わりに渦が潰れて半径 4 の敵に 16 ダメージ。1 射 40,000 FE、間隔 4 秒。
 */
public class VortexCannonItem extends TacticalWeaponItem {

    public static final int CAPACITY = 800_000, PER_SHOT = 40_000, COOLDOWN = 80, DURATION = 40;
    public static final double RANGE = 32.0, PULL_RADIUS = 7.0, BURST_RADIUS = 4.0;
    public static final float BURST_DAMAGE = 16.0F, WEIGHT = 10.0F;
    public static final int COLOR = 0x7AFFD0, CORE = 0xE8FFF6;

    public VortexCannonItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, true);
    }

    @Override
    public float recoil() {
        return 0.8F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, PER_SHOT)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_SHOT);
            fire(server, player);
            playUse(player, stack);
        }
        clientKick(player);
        return InteractionResultHolder.consume(stack);
    }

    /** 撃って渦を作る（兵姫に持たせたときも同じ処理）。渦の中心 */
    public static Vec3 fire(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, RANGE, 0.8, 1);
        Vec3 center = ray.hits().isEmpty() ? ray.end().subtract(look.scale(0.5)) : new Vec3(ray.hits().get(0).getX(), ray.hits().get(0).getY(0.5), ray.hits().get(0).getZ());
        Vec3 muzzle = eye.add(look.scale(1.2)).add(0, -0.25, 0);
        // 弾道: 螺旋に巻く風の筋
        Vec3 d = center.subtract(muzzle);
        double len = d.length();
        Vec3 n = d.normalize(), side = n.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(n).normalize();
        for (double t = 0; t < len; t += 0.4) {
            double a = t * 2.2;
            Vec3 p = muzzle.add(n.scale(t)).add(side.scale(Math.cos(a) * 0.35)).add(up.scale(Math.sin(a) * 0.35));
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.2F, 8), p, Vec3.ZERO);
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 8), muzzle, 8, 0.25);
        level.playSound(null, user.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.7F);
        vortex(level, user, center, 0);
        return center;
    }

    /** 渦を 2 Tick ごとに回す（age は経過 Tick）。終わりに潰れる */
    public static void vortex(ServerLevel level, LivingEntity user, Vec3 center, int age) {
        if (age >= DURATION) {
            collapse(level, user, center);
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(PULL_RADIUS),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator())) {
            Vec3 to = center.subtract(e.getX(), e.getY(0.5), e.getZ());
            double dist = to.length();
            if (dist > PULL_RADIUS || dist < 0.3) continue;
            Vec3 tangent = new Vec3(-to.z, 0, to.x).normalize().scale(0.15);
            Vec3 pull = to.normalize().scale(0.25 + 0.05 * (PULL_RADIUS - dist)).add(tangent);
            e.setDeltaMovement(pull.x, Math.max(e.getDeltaMovement().y, pull.y * 0.6 + 0.04), pull.z);
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
        // 渦: 回りながら中心へ落ちていく粒の腕 3 本
        double spin = age * 0.35;
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < 6; k++) {
                double r = PULL_RADIUS * (1 - k / 6.0) * 0.9;
                double a = spin + arm * Math.PI * 2 / 3 + k * 0.45;
                Vec3 p = center.add(Math.cos(a) * r, (k - 3) * 0.12, Math.sin(a) * r);
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), k < 2 ? CORE : COLOR, 0.25F + 0.05F * k, 6), p, center.subtract(p).scale(0.08));
            }
        }
        if (age % 10 == 0) {
            Fx.shockwave(level, center, COLOR, 2.0F, 8);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        WeaponUtil.later(level, 2, () -> vortex(level, user, center, age + 2));
    }

    /** 渦が潰れる */
    public static void collapse(ServerLevel level, LivingEntity user, Vec3 center) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(BURST_RADIUS),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator()
                        && e.distanceToSqr(center) <= (BURST_RADIUS + e.getBbWidth()) * (BURST_RADIUS + e.getBbWidth()))) {
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), BURST_DAMAGE);
        }
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 2.5F, 6), center, 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.35F, 10), center, 24, 0.5);
        Fx.shockwave(level, center, 0xFFFFFF, (float) BURST_RADIUS + 2, 10);
        Fx.shockwave(level, center, COLOR, (float) BURST_RADIUS, 14);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SHOT)};
    }
}
