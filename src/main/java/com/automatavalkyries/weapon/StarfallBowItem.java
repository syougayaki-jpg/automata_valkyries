package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 星弓『スターフォール』（T3、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 両手の光の弓。
 * 右クリックで引き（1 秒で満ちる）、離すと星の矢（射程 64・5 体貫通）。満ちていれば 25、途中なら引いた分だけ（0.25 秒未満は撃たない）。
 * 満ちた矢は最初に射抜いた敵（いなければ矢の止まった所）のまわり（半径 5）へ 2 秒かけて星を 12 個降らせる（1 個 半径 2.5 に 10）。満ちた 1 射 120,000 FE（途中なら引いた分だけ）。
 */
public class StarfallBowItem extends TacticalWeaponItem {

    public static final int CAPACITY = 2_000_000, PER_SHOT = 120_000, DRAW = 20, MIN_DRAW = 5, STARS = 12;
    public static final float ARROW_DAMAGE = 25.0F, STAR_DAMAGE = 10.0F, WEIGHT = 4.0F;
    public static final double RANGE = 64.0, RAIN_RADIUS = 5.0, STAR_RADIUS = 2.5;
    public static final int COLOR = 0xFFE89A, CORE = 0xFFFFFF, SKY = 0x9AB8FF;

    public StarfallBowItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, true);
    }

    @Override
    public float recoil() {
        return 0.3F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, PER_SHOT * MIN_DRAW / DRAW)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int t = getUseDuration(stack) - remaining;
        if (!(level instanceof ServerLevel server) || t > DRAW) return;
        // 弦に光が集まる
        Vec3 at = user.getEyePosition().add(user.getLookAngle().scale(0.8)).add(0, -0.2, 0);
        Fx.gather(server, Fx.opt(ModParticles.GLOW.get(), t == DRAW ? CORE : COLOR, 0.15F, 6), at, 2, 1.2, 6);
        if (t == DRAW) {
            Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), at, 10, 0.2);
            server.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        int drawn = Math.min(DRAW, getUseDuration(stack) - remaining);
        if (drawn < MIN_DRAW) return;
        float power = drawn / (float) DRAW;
        if (level instanceof ServerLevel server) {
            if (!WeaponPower.use(user, stack, (int) (PER_SHOT * power))) {
                notEnoughPower(user);
                return;
            }
            loose(server, user, power);
            playUse(user, stack);
        } else {
            clientKick(user);
        }
    }

    /** 矢を放つ（power 0〜1、1 なら星が降る。兵姫に持たせたときも同じ処理）。矢が落ちた所 */
    public static Vec3 loose(ServerLevel level, LivingEntity user, float power) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, RANGE, 0.8, 5);
        for (LivingEntity e : ray.hits()) {
            if (e.isAlliedTo(user)) continue;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), ARROW_DAMAGE * power);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), new Vec3(e.getX(), e.getY(0.6), e.getZ()), 6, 0.3);
        }
        Vec3 end = ray.hits().size() >= 5 ? new Vec3(ray.hits().get(4).getX(), ray.hits().get(4).getY(), ray.hits().get(4).getZ()) : ray.end();
        Vec3 from = eye.add(look.scale(0.8)).add(0, -0.2, 0);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.12F, 6), from, end, 0.4);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.22F + 0.1F * power, 9), from, end, 0.6);
        Fx.line(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.2F, 10), from, end, 1.5);
        level.playSound(null, user.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        if (power >= 1.0F) {
            // 星は最初に射抜いた敵のまわりへ（誰にも当たらなければ矢の止まった所へ）
            Vec3 rain = ray.hits().isEmpty() ? end : ray.hits().get(0).position();
            Fx.shockwave(level, rain, COLOR, 3.0F, 10);
            for (int i = 0; i < STARS; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * RAIN_RADIUS;
                Vec3 spot = rain.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                WeaponUtil.later(level, 4 + i * 3, () -> star(level, user, spot));
            }
        }
        return end;
    }

    /** 星が 1 つ降る（地面の高さに合わせる） */
    public static void star(ServerLevel level, LivingEntity user, Vec3 spot) {
        if (!level.isLoaded(BlockPos.containing(spot))) return; // 読み込まれていない区画には降らせない（見ると区画を読み込み・生成してしまう）
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(spot).getX(), BlockPos.containing(spot).getZ());
        Vec3 ground = new Vec3(spot.x, Math.min(spot.y + 2, Math.max(spot.y - 6, top)), spot.z);
        Vec3 sky = ground.add(1.5, 14, 0.8);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), SKY, 0.25F, 6), sky, ground, 0.5);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.14F, 5), sky, ground, 0.35);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground, ground).inflate(STAR_RADIUS, 3, STAR_RADIUS),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator())) {
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), STAR_DAMAGE);
        }
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 1.6F, 6), ground.add(0, 0.3, 0), 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 10), ground.add(0, 0.3, 0), 10, 0.35);
        Fx.shockwave(level, ground.add(0, 0.05, 0), SKY, (float) STAR_RADIUS + 0.5F, 8);
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.8F, 1.2F);
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
        return new Object[]{format(PER_SHOT)};
    }
}
