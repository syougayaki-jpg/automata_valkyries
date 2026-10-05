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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「大渦潮」に変わる。弾道は紅と金の太い螺旋になり、渦は半径 21（3 倍）で
 * 6 本の腕が回り、中心に光の柱が立って半径 21 の敵を吸い寄せ続ける。終わりに渦が潰れて半径 12（3 倍）の大爆発になり、光の柱が空へ噴き上がる。
 * 吸い寄せも爆発も当たりは見た目と同じ半径</p>
 */
public class VortexCannonItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    /** 弾の威力（説明に出す） */
    @Override
    public float[] shotDamage() {
        return new float[]{BURST_DAMAGE, 1};
    }


    public static final int CAPACITY = 800_000, PER_SHOT = 40_000, COOLDOWN = 80, DURATION = 40;
    public static final double RANGE = 32.0, PULL_RADIUS = 7.0, BURST_RADIUS = 4.0, OVER_PULL_RADIUS = 21.0, OVER_BURST_RADIUS = 12.0;
    public static final float BURST_DAMAGE = 80.0F, WEIGHT = 10.0F;
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
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_SHOT);
            fire(server, player);
            playUse(player, stack);
        }
        clientKick(player);
        return InteractionResultHolder.consume(stack);
    }

    /** 戦術兵姫が撃つ（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!canWield(user, InteractionHand.MAIN_HAND)) return false;
        if (!WeaponPower.use(user, stack, PER_SHOT)) return false;
        fire(level, user);
        playUse(user, stack);
        return true;
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
        boolean over = OverloadArts.active(user);
        for (double t = 0; t < len; t += 0.4) {
            double a = t * 2.2;
            Vec3 p = muzzle.add(n.scale(t)).add(side.scale(Math.cos(a) * 0.35)).add(up.scale(Math.sin(a) * 0.35));
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.2F, 8), p, Vec3.ZERO);
            if (over) { // 紅と金の太い螺旋
                Vec3 q = muzzle.add(n.scale(t)).add(side.scale(Math.cos(a + Math.PI) * 1.0)).add(up.scale(Math.sin(a + Math.PI) * 1.0));
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), (int) (t / 2) % 2 == 0 ? Overload.CRIMSON : Overload.GOLD, 0.4F, 12), q, Vec3.ZERO);
            }
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 8), muzzle, 8, 0.25);
        if (over) {
            OverloadArts.flourish(level, user, 6.0F);
            Fx.beamRing(level, muzzle, n, Overload.GOLD, 2.0F, 12);
        }
        level.playSound(null, user.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, over ? 0.5F : 0.7F);
        vortex(level, user, center, 0, over);
        return center;
    }

    /** 渦を 2 Tick ごとに回す（age は経過 Tick）。終わりに潰れる */
    public static void vortex(ServerLevel level, LivingEntity user, Vec3 center, int age) {
        vortex(level, user, center, age, OverloadArts.active(user));
    }

    /** over = オーバーロードの大渦潮（撃ったときに決まる） */
    public static void vortex(ServerLevel level, LivingEntity user, Vec3 center, int age, boolean over) {
        if (age >= DURATION) {
            collapse(level, user, center, over);
            return;
        }
        double pullRadius = over ? OVER_PULL_RADIUS : PULL_RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(pullRadius),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator())) {
            Vec3 to = center.subtract(e.getX(), e.getY(0.5), e.getZ());
            double dist = to.length();
            if (dist > pullRadius || dist < 0.3) continue;
            Vec3 tangent = new Vec3(-to.z, 0, to.x).normalize().scale(0.15);
            // オーバーロードは半径 21 の縁からでも 2 秒で中心へ届く強さ（通常の式を半径に合わせて伸ばす）
            double strength = over ? Math.min(1.2, 0.3 + 0.04 * (pullRadius - dist)) : 0.25 + 0.05 * (PULL_RADIUS - dist);
            Vec3 pull = to.normalize().scale(strength).add(tangent);
            e.setDeltaMovement(pull.x, Math.max(e.getDeltaMovement().y, pull.y * 0.6 + 0.04), pull.z);
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
        if (over) {
            overSwirl(level, center, age);
            WeaponUtil.later(level, 2, () -> vortex(level, user, center, age + 2, true));
            return;
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
        WeaponUtil.later(level, 2, () -> vortex(level, user, center, age + 2, false));
    }

    /** オーバーロードの大渦潮の見た目: 半径 21 で回る 6 本の腕、中心の光の柱、縁の輪 */
    private static void overSwirl(ServerLevel level, Vec3 center, int age) {
        double spin = age * 0.3;
        for (int arm = 0; arm < 6; arm++) {
            for (int k = 0; k < 10; k++) {
                double r = OVER_PULL_RADIUS * (1 - k / 10.0) * 0.95;
                double a = spin + arm * Math.PI / 3 + k * 0.35;
                Vec3 p = center.add(Math.cos(a) * r, (k - 5) * 0.15, Math.sin(a) * r);
                int color = k >= 8 ? CORE : arm % 3 == 0 ? Overload.CRIMSON : arm % 3 == 1 ? COLOR : Overload.GOLD;
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.4F + 0.05F * k, 8), p, center.subtract(p).scale(0.06));
            }
        }
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.6F, 3), center.add(0, -2, 0), center.add(0, 8, 0), 0.5);
        if (age % 10 == 0) {
            Fx.shockwave(level, center, COLOR, (float) OVER_PULL_RADIUS, 12);
            Fx.mandala(level, center, Overload.GOLD, 6.0F, 12, false, true);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        if (age % 4 == 0) Fx.beamRing(level, center, new Vec3(0, 1, 0), Overload.CRIMSON, (float) OVER_PULL_RADIUS * 0.5F, 6);
    }

    /** 渦が潰れる */
    public static void collapse(ServerLevel level, LivingEntity user, Vec3 center) {
        collapse(level, user, center, OverloadArts.active(user));
    }

    public static void collapse(ServerLevel level, LivingEntity user, Vec3 center, boolean over) {
        double radius = over ? OVER_BURST_RADIUS : BURST_RADIUS;
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator()
                        && e.distanceToSqr(center) <= (radius + e.getBbWidth()) * (radius + e.getBbWidth()))) {
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), BURST_DAMAGE);
            if (over && n++ < 10) OverloadArts.impact(level, new Vec3(e.getX(), e.getY(0.5), e.getZ()));
        }
        if (over) {
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 6.0F, 8), center, 1, 0, 0);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.45F, 14), center, 40, 1.0);
            Fx.burst(level, Fx.opt(ModParticles.GLOW.get(), Overload.CRIMSON, 0.5F, 16), center, 30, 0.8);
            Fx.shockwave(level, center, 0xFFFFFF, (float) OVER_BURST_RADIUS + 2, 12);
            Fx.shockwave(level, center, COLOR, (float) OVER_BURST_RADIUS, 16);
            Fx.shockwave(level, center, Overload.GOLD, (float) OVER_BURST_RADIUS * 0.6F, 20);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 1.4F, 16), center, center.add(0, 24, 0), 0.6);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.CRIMSON, 0.8F, 20), center, center.add(0, 24, 0), 0.9);
            Fx.beamRing(level, center.add(0, 2, 0), new Vec3(0, 1, 0), Overload.GOLD, 6.0F, 14);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.7F);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 0.6F);
            return;
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
