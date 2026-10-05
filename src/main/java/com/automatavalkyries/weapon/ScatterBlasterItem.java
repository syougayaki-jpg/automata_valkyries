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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 弾幕の斉射。1 射が 3 Tick おきの 3 連射になり、1 連射ごとに光の粒 16 発
 * （計 48 発）を 2.5 倍の広さの扇で射程 36 まで撃つ。粒は届いた先（敵か壁）で弾けて半径 2 の敵にも当たる（橙と紅と金の炸裂）。
 * 同じ相手に当たった粒は、連射ごとにまとめて 1 回のダメージにして吹き飛ばす</p>
 */
public class ScatterBlasterItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    /** 弾の威力（説明に出す） */
    @Override
    public float[] shotDamage() {
        return new float[]{PELLET_DAMAGE, PELLETS};
    }


    public static final int CAPACITY = 100_000, PER_SHOT = 5_000, PELLETS = 8, COOLDOWN = 16;
    public static final float PELLET_DAMAGE = 10.0F, WEIGHT = 5.0F;
    public static final double RANGE = 18.0, SPREAD = 0.12;
    public static final int COLOR = 0xFF8A50, CORE = 0xFFE0C0;
    /** オーバーロード: 連射の数・間隔（Tick）・1 連射の粒の数・扇の広さ・射程・粒が弾ける半径 */
    public static final int OVER_VOLLEYS = 3, OVER_VOLLEY_DELAY = 3, OVER_PELLETS = 16;
    public static final double OVER_SPREAD = 0.3, OVER_RANGE = 36.0, OVER_BLAST = 2.0;

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
    public static void fire(ServerLevel level, LivingEntity shooter) {
        if (OverloadArts.active(shooter)) {
            OverloadArts.flourish(level, shooter, 6.0F);
            for (int v = 0; v < OVER_VOLLEYS; v++) {
                if (v == 0) overVolley(level, shooter, 0);
                else {
                    int volley = v;
                    WeaponUtil.later(level, v * OVER_VOLLEY_DELAY, () -> {
                        if (shooter.isAlive() && shooter.level() == level) overVolley(level, shooter, volley);
                    });
                }
            }
            return;
        }
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

    /** オーバーロードの 1 連射: 16 発を広い扇で射程 36 まで。届いた先で半径 2 に弾ける */
    private static void overVolley(ServerLevel level, LivingEntity shooter, int volley) {
        Vec3 eye = shooter.getEyePosition(), look = shooter.getLookAngle();
        Vec3 muzzle = eye.add(look.scale(0.8)).add(0, -0.15, 0);
        Map<LivingEntity, Integer> hits = new HashMap<>();
        for (int i = 0; i < OVER_PELLETS; i++) {
            Vec3 dir = look.add(level.random.nextGaussian() * OVER_SPREAD, level.random.nextGaussian() * OVER_SPREAD * 0.6, level.random.nextGaussian() * OVER_SPREAD).normalize();
            WeaponUtil.Ray ray = WeaponUtil.ray(level, shooter, eye, dir, OVER_RANGE, 0.8, 1);
            Vec3 stop = ray.hits().isEmpty() ? ray.end() : new Vec3(ray.hits().get(0).getX(), ray.hits().get(0).getY(0.6), ray.hits().get(0).getZ());
            ray.hits().forEach(e -> hits.merge(e, 1, Integer::sum));
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(stop, stop).inflate(OVER_BLAST),
                    e -> e != shooter && e.isAlive() && !e.isAlliedTo(shooter) && !e.isSpectator() && !ray.hits().contains(e)
                            && e.getBoundingBox().inflate(OVER_BLAST).contains(stop))) {
                hits.merge(e, 1, Integer::sum);
            }
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), i % 3 == 0 ? Overload.GOLD : COLOR, 0.2F, 7), muzzle, stop, 2.5);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), i % 2 == 0 ? COLOR : Overload.CRIMSON, 0.3F, 9), stop, 3, 0.3);
            if (i % 4 == 0) Fx.shockwave(level, stop, i % 8 == 0 ? COLOR : Overload.CRIMSON, (float) OVER_BLAST, 8);
        }
        int shown = 0;
        for (Map.Entry<LivingEntity, Integer> hit : hits.entrySet()) {
            LivingEntity target = hit.getKey();
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(shooter, WeaponDamage.ENERGY, shooter, shooter), PELLET_DAMAGE * hit.getValue());
            Vec3 push = new Vec3(target.getX() - shooter.getX(), 0, target.getZ() - shooter.getZ()).normalize().scale(0.25 + 0.06 * Math.min(10, hit.getValue()));
            target.push(push.x, 0.15, push.z);
            target.hurtMarked = true;
            if (shown++ < 12) OverloadArts.impact(level, new Vec3(target.getX(), target.getY(0.6), target.getZ()));
        }
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 1.4F, 6), muzzle, 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.GOLD, 0.3F, 10), muzzle, 14, 0.45);
        Fx.beamRing(level, muzzle, look, COLOR, 2.5F, 8);
        level.playSound(null, shooter.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.4F + volley * 0.15F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 1.1F);
        shooter.push(-look.x * 0.12, 0.02, -look.z * 0.12);
        shooter.hurtMarked = true;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SHOT)};
    }
}
