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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 矢は太さ 2.4 の光の奔流になり 15 体まで貫く。満ちた矢は「流星雨」になり、
 * 半径 5 から半径 15 へ 2.4 秒かけて星を 48 個降らせる（1 個 半径 4、高さ 24 から斜めに落ちる大きな星）。降り終わりに中心へ大流星が落ち、
 * 半径 8 を打つ。降らせる所の空には半径 9 の星の陣が開く</p>
 */
public class StarfallBowItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    /** 弾の威力（説明に出す） */
    @Override
    public float[] shotDamage() {
        return new float[]{ARROW_DAMAGE, 1};
    }


    public static final int CAPACITY = 2_000_000, PER_SHOT = 120_000, DRAW = 20, MIN_DRAW = 5, STARS = 12;
    public static final float ARROW_DAMAGE = 250.0F, STAR_DAMAGE = 100.0F, WEIGHT = 4.0F;
    public static final double RANGE = 64.0, RAIN_RADIUS = 5.0, STAR_RADIUS = 2.5;
    public static final int COLOR = 0xFFE89A, CORE = 0xFFFFFF, SKY = 0x9AB8FF;
    /** オーバーロード: 矢の太さと貫く数・流星雨の半径と星の数・星 1 個の半径・大流星の半径 */
    public static final double OVER_WIDTH = 2.4, OVER_RAIN_RADIUS = 15.0, OVER_STAR_RADIUS = 4.0, OVER_FINALE_RADIUS = 8.0;
    public static final int OVER_PIERCE = 15, OVER_STARS = 48;

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

    /** 戦術兵姫が引き絞って放つ（いつも満月の引き）（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!canWield(user, InteractionHand.MAIN_HAND)) return false;
        if (!WeaponPower.use(user, stack, PER_SHOT)) return false;
        loose(level, user, 1.0F);
        playUse(user, stack);
        return true;
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
        boolean over = OverloadArts.active(user);
        int pierce = over ? OVER_PIERCE : 5;
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, RANGE, over ? OVER_WIDTH : 0.8, pierce);
        for (LivingEntity e : ray.hits()) {
            if (e.isAlliedTo(user)) continue;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), ARROW_DAMAGE * power);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), new Vec3(e.getX(), e.getY(0.6), e.getZ()), 6, 0.3);
        }
        Vec3 end = ray.hits().size() >= pierce ? ray.hits().get(pierce - 1).position() : ray.end();
        Vec3 from = eye.add(look.scale(0.8)).add(0, -0.2, 0);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.12F, 6), from, end, 0.4);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.22F + 0.1F * power, 9), from, end, 0.6);
        Fx.line(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.2F, 10), from, end, 1.5);
        level.playSound(null, user.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        if (over) {
            overArrow(level, user, from, end, look);
            if (power >= 1.0F) meteorShower(level, user, ray.hits().isEmpty() ? end : ray.hits().get(0).position());
            return end;
        }
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

    /** オーバーロード: 太さ 2.4 の光の奔流（まわりに 4 本の光の筋と光の輪） */
    private static void overArrow(ServerLevel level, LivingEntity user, Vec3 from, Vec3 end, Vec3 look) {
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2;
            Vec3 off = side.scale(Math.cos(a) * OVER_WIDTH * 0.5).add(up.scale(Math.sin(a) * OVER_WIDTH * 0.5));
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), k % 2 == 0 ? SKY : Overload.GOLD, 0.35F, 12), from.add(off), end.add(off), 1.2);
        }
        double len = end.distanceTo(from);
        for (double d = 4; d < len; d += 6) Fx.beamRing(level, from.add(look.scale(d)), look, COLOR, (float) OVER_WIDTH * 0.7F, 10);
        OverloadArts.flourish(level, user, 6.0F);
    }

    /** オーバーロードの流星雨: 半径 15 へ 2.4 秒かけて星を 48 個、最後に中心へ大流星 */
    private static void meteorShower(ServerLevel level, LivingEntity user, Vec3 rain) {
        Fx.shockwave(level, rain, COLOR, (float) OVER_RAIN_RADIUS, 20);
        Fx.shockwave(level, rain.add(0, 0.05, 0), Overload.CRIMSON, (float) OVER_RAIN_RADIUS * 0.6F, 18);
        Fx.mandala(level, rain.add(0, 22, 0), SKY, (float) OVER_RAIN_RADIUS * 0.6F, 60, false, true);
        Fx.mandala(level, rain.add(0, 21.5, 0), Overload.GOLD, 5.0F, 60, false, false);
        level.playSound(null, rain.x, rain.y, rain.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 1.4F);
        for (int i = 0; i < OVER_STARS; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * OVER_RAIN_RADIUS;
            Vec3 spot = rain.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            WeaponUtil.later(level, 4 + i, () -> star(level, user, spot, OVER_STAR_RADIUS));
        }
        WeaponUtil.later(level, 6 + OVER_STARS, () -> star(level, user, rain, OVER_FINALE_RADIUS));
    }

    /** 星が 1 つ降る（地面の高さに合わせる） */
    public static void star(ServerLevel level, LivingEntity user, Vec3 spot) {
        star(level, user, spot, STAR_RADIUS);
    }

    /** 半径 radius の星が 1 つ降る（半径が通常より大きければオーバーロードの大きな星） */
    public static void star(ServerLevel level, LivingEntity user, Vec3 spot, double radius) {
        boolean big = radius > STAR_RADIUS;
        if (!level.isLoaded(BlockPos.containing(spot))) return; // 読み込まれていない区画には降らせない（見ると区画を読み込み・生成してしまう）
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(spot).getX(), BlockPos.containing(spot).getZ());
        Vec3 ground = new Vec3(spot.x, Math.min(spot.y + 2, Math.max(spot.y - 6, top)), spot.z);
        if (big) {
            bigStar(level, user, ground, radius);
            return;
        }
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

    /** オーバーロードの大きな星: 高さ 24 から斜めに落ち、半径 radius（円柱、高さ ±4）の敵を打つ */
    private static void bigStar(ServerLevel level, LivingEntity user, Vec3 ground, double radius) {
        Vec3 sky = ground.add(4, 24, 2.5);
        boolean finale = radius >= OVER_FINALE_RADIUS;
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), SKY, finale ? 1.2F : 0.5F, 8), sky, ground, finale ? 0.6 : 1.0);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, finale ? 0.6F : 0.25F, 6), sky, ground, finale ? 0.5 : 0.8);
        Fx.star(level, ground.add(0, 0.6, 0), COLOR, (float) radius * 0.6F, finale ? 20 : 10, Vec3.ZERO);
        int shown = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground, ground).inflate(radius, 4, radius),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator()
                        && Math.hypot(e.getX() - ground.x, e.getZ() - ground.z) <= radius + e.getBbWidth() * 0.5)) {
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), STAR_DAMAGE);
            if (shown++ < (finale ? 12 : 3)) OverloadArts.impact(level, new Vec3(e.getX(), e.getY(0.6), e.getZ()));
        }
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, (float) radius, 6), ground.add(0, 0.3, 0), 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.4F, 12), ground.add(0, 0.3, 0), finale ? 50 : 14, finale ? 1.0 : 0.5);
        Fx.shockwave(level, ground.add(0, 0.05, 0), SKY, (float) radius + 0.5F, 10);
        Fx.shockwave(level, ground.add(0, 0.1, 0), level.random.nextBoolean() ? Overload.CRIMSON : Overload.GOLD, (float) radius * 0.6F, 12);
        if (finale) {
            Fx.beamRing(level, ground.add(0, 0.5, 0), new Vec3(0, 1, 0), Overload.GOLD, (float) radius * 1.5F, 16);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 6.0F, 10), ground.add(0, 1, 0), 1, 0, 0);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.0F, 0.6F);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.8F);
        } else {
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0F, 0.9F);
        }
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
