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

import java.util.HashMap;
import java.util.Map;

/**
 * スキャッターブラスター（T1、追加の兵装。2026-10-03 こちらが考えた）: 片手のエネルギー散弾銃。右クリックで光の粒 8 発を扇状に撃つ
 * （1 発 4 ダメージ、射程 18、同じ相手に当たった粒はまとめて 1 回のダメージにして吹き飛ばす）。1 射 5,000 FE、間隔 0.8 秒。
 */
public class ScatterBlasterItem extends TacticalWeaponItem {

    public static final int CAPACITY = 100_000, PER_SHOT = 5_000, PELLETS = 8, COOLDOWN = 16;
    public static final float PELLET_DAMAGE = 4.0F, WEIGHT = 5.0F;
    public static final double RANGE = 18.0, SPREAD = 0.12;
    public static final int COLOR = 0xFF8A50, CORE = 0xFFE0C0;

    public ScatterBlasterItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public float recoil() {
        return 0.7F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!WeaponPower.has(player, gun, PER_SHOT)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(gun);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, gun, PER_SHOT);
            fire(server, player);
            playUse(player, gun);
        }
        clientKick(player);
        return InteractionResultHolder.consume(gun);
    }

    /** 兵姫に持たせたときも同じ処理 */
    public static void fire(ServerLevel level, LivingEntity shooter) {
        Vec3 eye = shooter.getEyePosition(), look = shooter.getLookAngle();
        Vec3 muzzle = eye.add(look.scale(0.8)).add(0, -0.15, 0);
        Map<LivingEntity, Integer> hits = new HashMap<>();
        for (int i = 0; i < PELLETS; i++) {
            Vec3 dir = look.add(level.random.nextGaussian() * SPREAD, level.random.nextGaussian() * SPREAD, level.random.nextGaussian() * SPREAD).normalize();
            WeaponUtil.Ray ray = WeaponUtil.ray(level, shooter, eye, dir, RANGE, 0.6, 1);
            Vec3 stop = ray.hits().isEmpty() ? ray.end() : new Vec3(ray.hits().get(0).getX(), ray.hits().get(0).getY(0.6), ray.hits().get(0).getZ());
            ray.hits().forEach(e -> hits.merge(e, 1, Integer::sum));
            // 光の粒の筋
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.1F, 5), muzzle, stop, 0.5);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.18F, 7), muzzle, stop, 0.7);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.2F, 7), stop, 2, 0.2);
        }
        for (Map.Entry<LivingEntity, Integer> hit : hits.entrySet()) {
            LivingEntity target = hit.getKey();
            target.hurt(WeaponDamage.source(shooter, WeaponDamage.ENERGY, shooter, shooter), PELLET_DAMAGE * hit.getValue());
            Vec3 push = new Vec3(target.getX() - shooter.getX(), 0, target.getZ() - shooter.getZ()).normalize().scale(0.25 + 0.08 * hit.getValue());
            target.push(push.x, 0.15, push.z);
            target.hurtMarked = true;
        }
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.7F, 5), muzzle, 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 8), muzzle, 8, 0.3);
        level.playSound(null, shooter.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.9F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.4F);
        shooter.push(-look.x * 0.18, 0.02, -look.z * 0.18);
        shooter.hurtMarked = true;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SHOT)};
    }
}
