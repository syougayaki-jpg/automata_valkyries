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

import java.util.List;

/**
 * クロノリピーター（T3、追加の兵装。2026-10-03 こちらが考えた）: 片手の時間射撃銃。右クリックで時間の矢を撃つ（射程 48、最大 3 体を貫く、20 ダメージ）。
 * 当たった相手には「時の残響」が残り、0.5 秒後と 1 秒後に同じ一撃が繰り返される（1 射で 3 回、無敵時間を無視）。1 射 60,000 FE、間隔 1 秒。
 */
public class ChronoRepeaterItem extends TacticalWeaponItem {

    /** 弾の威力（説明に出す） */
    @Override
    public float[] shotDamage() {
        return new float[]{DAMAGE, 1};
    }


    public static final int CAPACITY = 1_200_000, PER_SHOT = 60_000, COOLDOWN = 20, ECHOES = 2, ECHO_GAP = 10;
    public static final float DAMAGE = 120.0F, WEIGHT = 5.0F;
    public static final double RANGE = 48.0;
    public static final int COLOR = 0xB8A8FF, CORE = 0xF0ECFF;

    public ChronoRepeaterItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public float recoil() {
        return 0.5F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!WeaponPower.has(player, gun, PER_SHOT)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(gun);
        }
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, gun, PER_SHOT);
            fire(server, player);
            playUse(player, gun);
        }
        clientKick(player);
        return InteractionResultHolder.consume(gun);
    }

    /** 兵姫に持たせたときも同じ処理 */
    public static List<LivingEntity> fire(ServerLevel level, LivingEntity shooter) {
        Vec3 eye = shooter.getEyePosition(), look = shooter.getLookAngle();
        Vec3 muzzle = eye.add(look.scale(0.8)).add(0, -0.15, 0);
        WeaponUtil.Ray ray = WeaponUtil.ray(level, shooter, eye, look, RANGE, 0.6, 3);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.12F, 8), muzzle, ray.end(), 0.4);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.26F, 12), muzzle, ray.end(), 0.6);
        for (double d = 2; d < ray.end().distanceTo(muzzle); d += 4) {
            Fx.spray(level, Fx.opt(ModParticles.GLYPH.get(), COLOR, 0.2F, 20), muzzle.add(look.scale(d)), 1, 0.2, 0.01);
        }
        for (LivingEntity target : ray.hits()) {
            hit(level, shooter, target, 0);
            for (int e = 1; e <= ECHOES; e++) {
                int echo = e;
                WeaponUtil.later(level, ECHO_GAP * e, () -> hit(level, shooter, target, echo));
            }
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 8), muzzle, 6, 0.2);
        level.playSound(null, shooter.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.5F, 1.8F);
        return ray.hits();
    }

    /** 一撃（echo = 0 が本物、1・2 が時の残響） */
    private static void hit(ServerLevel level, LivingEntity shooter, LivingEntity target, int echo) {
        if (!target.isAlive()) return;
        target.invulnerableTime = 0;
        target.hurt(WeaponDamage.source(shooter, WeaponDamage.ENERGY, shooter, shooter), DAMAGE);
        Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        Fx.shockwave(level, at, echo == 0 ? CORE : COLOR, 1.6F + echo * 0.6F, 10);
        Fx.burst(level, Fx.opt(ModParticles.GLYPH.get(), COLOR, 0.24F, 22), at, 4 + echo * 2, 0.1);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), CORE, 0.28F, 8), at, 6, 0.3);
        if (echo > 0) level.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 1.2F + echo * 0.3F);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SHOT)};
    }
}
