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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「時環の奔流」。時間の矢が射程 64・太さ 2.4 の時の奔流になり 12 体まで貫く
 * （まわりを 4 本の光の筋が巻き、8 ブロックごとにこちらを向いた時計の陣が並ぶ）。時の残響は 2 回から 5 回（0.3 秒ごと）に増え、
 * 最後の残響で時が砕け、相手のまわり半径 5 の敵（6 体まで）にも同じ一撃が届く（半径 5 の時計の陣と衝撃波）</p>
 */
public class ChronoRepeaterItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    /** 弾の威力（説明に出す） */
    @Override
    public float[] shotDamage() {
        return new float[]{DAMAGE, 1};
    }


    public static final int CAPACITY = 1_200_000, PER_SHOT = 60_000, COOLDOWN = 20, ECHOES = 2, ECHO_GAP = 10;
    public static final float DAMAGE = 120.0F, WEIGHT = 5.0F;
    public static final double RANGE = 48.0;
    public static final int COLOR = 0xB8A8FF, CORE = 0xF0ECFF;
    /** オーバーロード: 射程・太さ・貫く数・時の残響の回数と間隔・最後に砕ける半径 */
    public static final double OVER_RANGE = 64.0, OVER_WIDTH = 2.4, OVER_SHATTER = 5.0;
    public static final int OVER_PIERCE = 12, OVER_ECHOES = 5, OVER_ECHO_GAP = 6;

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

    /** 戦術兵姫が撃つ（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack gun, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!WeaponPower.use(user, gun, PER_SHOT)) return false;
        fire(level, user);
        playUse(user, gun);
        return true;
    }

    /** 兵姫に持たせたときも同じ処理 */
    public static List<LivingEntity> fire(ServerLevel level, LivingEntity shooter) {
        if (OverloadArts.active(shooter)) return overFire(level, shooter);
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

    /** オーバーロードの時環の奔流: 射程 64・太さ 2.4・12 体まで、時の残響 5 回、最後の残響で半径 5 へ砕ける */
    private static List<LivingEntity> overFire(ServerLevel level, LivingEntity shooter) {
        Vec3 eye = shooter.getEyePosition(), look = shooter.getLookAngle();
        Vec3 muzzle = eye.add(look.scale(0.8)).add(0, -0.15, 0);
        WeaponUtil.Ray ray = WeaponUtil.ray(level, shooter, eye, look, OVER_RANGE, OVER_WIDTH, OVER_PIERCE);
        Vec3 end = ray.end();
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.3F, 10), muzzle, end, 0.8);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.6F, 14), muzzle, end, 1.0);
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        double len = end.distanceTo(muzzle);
        for (double d = 0; d < len; d += 1.0) { // まわりを巻く 4 本の光の筋（ねじれながら進む）
            for (int k = 0; k < 4; k++) {
                if ((k + (int) d) % 2 != 0) continue;
                double a = d * 0.35 + k * Math.PI / 2;
                Vec3 p = muzzle.add(look.scale(d)).add(side.scale(Math.cos(a) * OVER_WIDTH * 0.5)).add(up.scale(Math.sin(a) * OVER_WIDTH * 0.5));
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), k % 2 == 0 ? Overload.GOLD : Overload.CRIMSON, 0.3F, 14), p, Vec3.ZERO);
            }
        }
        for (double d = 4; d < len; d += 8) {
            Fx.mandala(level, muzzle.add(look.scale(d)), COLOR, (float) OVER_WIDTH * 0.8F, 20, true, ((int) d / 8) % 2 == 0);
        }
        for (LivingEntity target : ray.hits()) {
            hit(level, shooter, target, 0);
            for (int e = 1; e <= OVER_ECHOES; e++) {
                int echo = e;
                WeaponUtil.later(level, OVER_ECHO_GAP * e, () -> {
                    hit(level, shooter, target, Math.min(echo, 3));
                    if (echo == OVER_ECHOES) shatter(level, shooter, target);
                });
            }
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.35F, 10), muzzle, 14, 0.35);
        Fx.beamRing(level, muzzle, look, Overload.GOLD, 2.0F, 10);
        OverloadArts.flourish(level, shooter, 6.0F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2F, 0.9F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
        return ray.hits();
    }

    /** オーバーロード: 最後の残響で時が砕け、相手のまわり半径 5 の敵（6 体まで）にも同じ一撃 */
    private static void shatter(ServerLevel level, LivingEntity shooter, LivingEntity target) {
        Vec3 c = target.position();
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(OVER_SHATTER),
                e -> e != shooter && e != target && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(shooter)
                        && e.position().distanceTo(c) <= OVER_SHATTER + e.getBbWidth())) {
            if (n++ >= 6) break;
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(shooter, WeaponDamage.ENERGY, shooter, shooter), DAMAGE);
            OverloadArts.impact(level, new Vec3(e.getX(), e.getY(0.6), e.getZ()));
        }
        Fx.mandala(level, c.add(0, 0.05, 0), COLOR, (float) OVER_SHATTER, 24, false, true);
        Fx.shockwave(level, c.add(0, 0.05, 0), Overload.GOLD, (float) OVER_SHATTER, 12);
        Fx.shockwave(level, c.add(0, 0.1, 0), Overload.CRIMSON, (float) OVER_SHATTER * 0.6F, 14);
        Fx.burst(level, Fx.opt(ModParticles.GLYPH.get(), COLOR, 0.35F, 24), new Vec3(target.getX(), target.getY(0.6), target.getZ()), 16, 0.3);
        level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.6F);
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
