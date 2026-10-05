package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 熾天杖『セラフ』（T3、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 両手の祝福の杖。
 * 右クリックで半径 10 に光の祝福: 持ち主と味方（プレイヤー・持ち主の飼い慣らした動物・持ち主の仲間）を 12 回復し、再生 II と耐性 I を 10 秒。
 * 同じ範囲の敵（モンスター）には聖なる光で 14（アンデッドには 2 倍）。1 回 150,000 FE、間隔 8 秒。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「熾天の審判」。祝福の範囲が半径 10 から半径 30 に広がる
 * （癒やしも聖なる光も半径 30 の全員）。持ち主の背に 6 枚の光の翼（熾天使の 3 対の翼）と頭上に金の光輪が開き、足元から半径 30 へ
 * 金と紅の 3 重の衝撃波と 72 本の光の羽が立つ。敵には空（高さ 24）から太い光の柱が落ちる（柱の見た目は 16 体まで、当たりは全員）</p>
 */
public class SeraphStaffItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 2_000_000, PER_CAST = 150_000, COOLDOWN = 160, BUFF = 200;
    public static final double RADIUS = 10.0, OVER_RADIUS = 30.0;
    public static final float HEAL = 12.0F, SMITE_DAMAGE = 120.0F, WEIGHT = 5.0F;
    public static final int COLOR = 0xFFF4C0, HOLY = 0xFFD870, LIFE = 0x9CFFB0;

    public SeraphStaffItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, PER_CAST)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_CAST);
            bless(server, player);
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 戦術兵姫が祝福する（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!canWield(user, InteractionHand.MAIN_HAND)) return false;
        if (!WeaponPower.use(user, stack, PER_CAST)) return false;
        bless(level, user);
        playUse(user, stack);
        user.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 味方か（持ち主・プレイヤー・持ち主の飼い慣らした動物・持ち主の仲間。モンスターは味方にしない） */
    static boolean friendly(LivingEntity user, LivingEntity e) {
        if (e == user) return true;
        if (e instanceof Enemy) return e.isAlliedTo(user);
        return e instanceof Player || e.isAlliedTo(user) || (e instanceof TamableAnimal pet && pet.isOwnedBy(user));
    }

    /** 祝福する（兵姫に持たせたときも同じ処理）。{癒やした数, 打った敵の数} */
    public static int[] bless(ServerLevel level, LivingEntity user) {
        int healed = 0, smitten = 0;
        Vec3 c = user.position();
        boolean over = OverloadArts.active(user);
        double radius = over ? OVER_RADIUS : RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && e.distanceTo(user) <= radius + e.getBbWidth())) {
            Vec3 at = new Vec3(e.getX(), e.getY(0.5), e.getZ());
            if (friendly(user, e)) {
                e.heal(HEAL);
                e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BUFF, 1));
                e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BUFF, 0));
                if (!over || healed < 8) Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), LIFE, 0.3F, 20), at, 10, 0.4, 0.03);
                healed++;
            } else if (e instanceof Enemy) {
                float damage = e.getMobType() == MobType.UNDEAD ? SMITE_DAMAGE * 2 : SMITE_DAMAGE;
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), damage);
                if (over) {
                    int index = smitten;
                    if (index < 16) WeaponUtil.later(level, index / 4, () -> judgment(level, e, at, index)); // 1 Tick に 4 本ずつ
                    smitten++;
                    continue;
                }
                Vec3 top = at.add(0, 8, 0);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), HOLY, 0.3F, 8), top, at, 0.4);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.16F, 6), top, at, 0.3);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), HOLY, 0.3F, 8), at, 8, 0.3);
                smitten++;
            }
        }
        if (over) {
            seraphWings(level, user);
            return new int[]{healed, smitten};
        }
        // 祝福の陣: 地面に広がる金の輪と、舞い上がる羽のような光
        Vec3 ground = c.add(0, 0.05, 0);
        Fx.shockwave(level, ground, COLOR, (float) RADIUS, 20);
        Fx.shockwave(level, ground, HOLY, (float) RADIUS * 0.6F, 16);
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36;
            Vec3 p = c.add(Math.cos(a) * RADIUS * 0.8, 0.2, Math.sin(a) * RADIUS * 0.8);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), i % 2 == 0 ? COLOR : HOLY, 0.35F, 30), p, new Vec3(0, 0.12, 0));
        }
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 2.0F, 8), c.add(0, user.getBbHeight() + 1.5, 0), 1, 0, 0);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        return new int[]{healed, smitten};
    }

    /** オーバーロード: 空（高さ 24）から落ちる太い光の柱 */
    private static void judgment(ServerLevel level, LivingEntity e, Vec3 at, int index) {
        Vec3 top = at.add(0, 24, 0);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.3F, 10), top, at, 0.8);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), index % 3 == 0 ? Overload.GOLD : HOLY, 0.7F, 12), top, at, 1.2);
        Fx.shockwave(level, e.position().add(0, 0.05, 0), HOLY, 2.0F + e.getBbWidth(), 12);
        Fx.beamRing(level, at, new Vec3(0, 1, 0), COLOR, 1.5F + e.getBbWidth(), 10);
        OverloadArts.impact(level, at);
    }

    /** オーバーロード: 背の 6 枚の光の翼・頭上の光輪・半径 30 へ広がる衝撃波と立ちのぼる光の羽 */
    private static void seraphWings(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position();
        Vec3 look = user.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = flat.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 back = c.add(0, user.getBbHeight() * 0.75, 0).subtract(flat.scale(0.5));
        // 3 対の翼（上・中・下へ開く）。翼ごとに羽が外へ並ぶ
        double[] lifts = {0.9, 0.2, -0.5};
        for (int pair = 0; pair < 3; pair++) {
            for (int s : new int[]{1, -1}) {
                for (int f = 1; f <= 8; f++) {
                    double len = f * 0.55;
                    Vec3 p = back.add(side.scale(s * len)).add(0, lifts[pair] * len * 0.6 + Math.sin(f * 0.4) * 0.3, 0);
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), f > 6 ? Overload.GOLD : f % 2 == 0 ? COLOR : HOLY, 0.45F, 30), p, new Vec3(0, 0.01, 0));
                    if (f % 2 == 0) Fx.slash(level, p, COLOR, 0.9F, 14, (float) Math.atan2(lifts[pair], s));
                }
            }
        }
        Fx.mandala(level, c.add(0, user.getBbHeight() + 0.9, 0), Overload.GOLD, 1.2F, 40, false, true);
        Fx.mandala(level, c.add(0, 0.05, 0), COLOR, 8.0F, 40, false, false);
        Vec3 ground = c.add(0, 0.05, 0);
        Fx.shockwave(level, ground, COLOR, (float) OVER_RADIUS, 26);
        Fx.shockwave(level, ground.add(0, 0.05, 0), Overload.GOLD, (float) OVER_RADIUS * 0.7F, 22);
        Fx.shockwave(level, ground.add(0, 0.1, 0), Overload.CRIMSON, (float) OVER_RADIUS * 0.4F, 18);
        for (int i = 0; i < 72; i++) {
            double a = i * Math.PI * 2 / 72, r = OVER_RADIUS * (i % 2 == 0 ? 0.95 : 0.6);
            Vec3 p = c.add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), i % 3 == 0 ? Overload.GOLD : i % 2 == 0 ? COLOR : HOLY, 0.5F, 40), p, new Vec3(0, 0.15, 0));
        }
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.6F, 20), c, c.add(0, 30, 0), 1.0);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 4.0F, 10), c.add(0, user.getBbHeight() + 1.5, 0), 1, 0, 0);
        OverloadArts.flourish(level, user, (float) OVER_RADIUS);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6F, 1.0F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.6F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_CAST)};
    }
}
