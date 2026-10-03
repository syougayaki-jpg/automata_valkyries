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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * プラズマフレイマー（T2、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 両手の火炎放射器。
 * 右クリックを押している間、正面の扇（9 ブロック・左右 18 度、壁の向こうには届かない）へ青白いプラズマの炎を浴びせる:
 * 0.1 秒ごと 2.5（無敵時間を無視）・5 秒燃やす。0.1 秒ごと 600 FE。
 */
public class PlasmaFlamerItem extends TacticalWeaponItem {

    public static final int CAPACITY = 600_000, PER_PULSE = 600, INTERVAL = 2;
    public static final float PULSE_DAMAGE = 2.5F, WEIGHT = 9.0F;
    public static final double RANGE = 9.0, HALF_ANGLE = 18.0;
    public static final int COLOR = 0x60A0FF, CORE = 0xE0F0FF, EMBER = 0xFF8040;

    public PlasmaFlamerItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, true);
    }

    @Override
    public float recoil() {
        return 0.05F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, PER_PULSE)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int t = getUseDuration(stack) - remaining;
        if (t % INTERVAL != 0 || !(level instanceof ServerLevel server)) return;
        if (!WeaponPower.use(user, stack, PER_PULSE)) {
            notEnoughPower(user);
            user.releaseUsingItem();
            return;
        }
        pulse(server, user);
        if (t % 10 == 0) playUse(user, stack);
    }

    /** 炎を 1 回浴びせる（兵姫に持たせたときも同じ処理）。当たった数 */
    public static int pulse(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        Vec3 nozzle = eye.add(look.scale(1.1)).add(0, -0.3, 0);
        double cos = Math.cos(Math.toRadians(HALF_ANGLE));
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(RANGE),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator());
        int hit = 0;
        for (LivingEntity e : targets) {
            Vec3 c = new Vec3(e.getX(), e.getY(0.5), e.getZ());
            Vec3 to = c.subtract(eye);
            double dist = to.length();
            if (dist > RANGE + e.getBbWidth() || dist < 1.0E-3 || to.normalize().dot(look) < cos) continue;
            if (!user.hasLineOfSight(e)) continue; // 壁の向こうには届かない
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), PULSE_DAMAGE);
            e.setSecondsOnFire(5);
            hit++;
        }
        // 炎: 芯は白く、外は青く、先は橙の火の粉。扇に広がりながら伸びる
        var r = level.random;
        for (int i = 0; i < 10; i++) {
            Vec3 dir = look.add(r.nextGaussian() * 0.12, r.nextGaussian() * 0.12, r.nextGaussian() * 0.12).normalize();
            double speed = 0.6 + r.nextDouble() * 0.5;
            int color = i < 3 ? CORE : i < 8 ? COLOR : EMBER;
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.35F + r.nextFloat() * 0.3F, 12), nozzle, dir.scale(speed));
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, nozzle.x, nozzle.y, nozzle.z, 0, look.x, look.y, look.z, 0.5);
        if (r.nextInt(3) == 0) level.playSound(null, user.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, 0.6F + r.nextFloat() * 0.2F);
        return hit;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_PULSE)};
    }
}
