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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「蒼炎の竜息」に変わる。炎は 24 ブロック（約 2.7 倍）・左右 30 度の大きな扇になり、
 * 4 倍の量の青白い炎と紅と金の火の粉が奔流のように伸びる。炎の中を竜の胴のような光の螺旋がうねり、炎の先には燃える衝撃波が広がる。
 * 当たりも 24 ブロック・左右 30 度。浴びせ続ける間、2 秒ごとに紅と金の衝撃波が足元に広がる</p>
 */
public class PlasmaFlamerItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    /** 弾の威力（説明に出す） */
    @Override
    public float[] shotDamage() {
        return new float[]{PULSE_DAMAGE, 1};
    }


    public static final int CAPACITY = 600_000, PER_PULSE = 600, INTERVAL = 2;
    public static final float PULSE_DAMAGE = 12.0F, WEIGHT = 9.0F;
    public static final double RANGE = 9.0, HALF_ANGLE = 18.0, OVER_RANGE = 24.0, OVER_HALF_ANGLE = 30.0;
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

    /** 戦術兵姫が焼き払う（{@link ValkyrieArts}）。30 Tick 押し続けて離す。電力は onUseTick のまま */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!canWield(user, InteractionHand.MAIN_HAND)) return false;
        if (user.isUsingItem() && user.getUseItem() == stack) return true;
        if (!WeaponPower.has(user, stack, PER_PULSE) || !ValkyrieArts.hold(level, user, stack, target, 30)) return false;
        return true;
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
        boolean over = OverloadArts.active(user);
        double range = over ? OVER_RANGE : RANGE;
        double cos = Math.cos(Math.toRadians(over ? OVER_HALF_ANGLE : HALF_ANGLE));
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(range),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator());
        int hit = 0;
        for (LivingEntity e : targets) {
            Vec3 c = new Vec3(e.getX(), e.getY(0.5), e.getZ());
            Vec3 to = c.subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth() || dist < 1.0E-3 || to.normalize().dot(look) < cos) continue;
            if (!user.hasLineOfSight(e)) continue; // 壁の向こうには届かない
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), PULSE_DAMAGE);
            e.setSecondsOnFire(5);
            if (over && hit < 4 && (user.tickCount / 2) % 3 == 0) OverloadArts.impact(level, c);
            hit++;
        }
        if (over) {
            dragonBreath(level, user, nozzle, look);
            return hit;
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

    /** オーバーロードの蒼炎の竜息の見た目: 24 ブロック・左右 30 度に伸びる 4 倍の炎、うねる光の螺旋、炎の先の燃える衝撃波 */
    private static void dragonBreath(ServerLevel level, LivingEntity user, Vec3 nozzle, Vec3 look) {
        var r = level.random;
        if ((user.tickCount / 2) % 20 == 0) OverloadArts.flourish(level, user, 6.0F);
        // 炎: 寿命 12 Tick で 24 ブロック届くよう速く、広く（左右 30 度に合う広がり）
        for (int i = 0; i < 40; i++) {
            Vec3 dir = look.add(r.nextGaussian() * 0.3, r.nextGaussian() * 0.3, r.nextGaussian() * 0.3).normalize();
            double speed = 2.2 + r.nextDouble() * 1.0; // 粒の減速（0.9）込みで 17〜25 ブロック
            int color = i < 10 ? CORE : i < 30 ? COLOR : i < 36 ? EMBER : (i % 2 == 0 ? Overload.CRIMSON : Overload.GOLD);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.6F + r.nextFloat() * 0.6F, 14), nozzle, dir.scale(speed));
        }
        // 竜の胴: 炎の中をうねる 2 本の光の螺旋
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(look).normalize();
        int t = user.tickCount;
        for (double d = 1.0; d < OVER_RANGE; d += 1.2) {
            double width = 0.5 + d * Math.tan(Math.toRadians(OVER_HALF_ANGLE)) * 0.55;
            for (int k = 0; k < 2; k++) {
                double a = d * 0.5 - t * 0.6 + k * Math.PI;
                Vec3 p = nozzle.add(look.scale(d)).add(side.scale(Math.cos(a) * width)).add(up.scale(Math.sin(a) * width * 0.5));
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), k == 0 ? COLOR : Overload.GOLD, 0.45F, 4), p, 1, 0, 0);
            }
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, nozzle.x, nozzle.y, nozzle.z, 0, look.x, look.y, look.z, 1.2);
        if ((t / 2) % 3 == 0) { // 炎の先で燃える衝撃波（pulse は 2 Tick ごとなので t / 2 で数える）
            Vec3 tip = nozzle.add(look.scale(OVER_RANGE * 0.85));
            net.minecraft.world.phys.BlockHitResult wall = level.clip(new net.minecraft.world.level.ClipContext(nozzle, tip,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, user));
            if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) tip = wall.getLocation();
            Fx.beamRing(level, tip, look, COLOR, (float) (OVER_RANGE * 0.85 * Math.tan(Math.toRadians(OVER_HALF_ANGLE))), 8);
            Fx.beamRing(level, nozzle.add(look.scale(1.0)), look, Overload.CRIMSON, 1.5F, 6);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, tip.x, tip.y, tip.z, 16, 2.5, 1.0, 2.5, 0.05);
        }
        if (r.nextInt(2) == 0) level.playSound(null, user.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 0.4F + r.nextFloat() * 0.2F);
        if ((t / 2) % 10 == 0) level.playSound(null, user.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.8F, 0.8F);
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
