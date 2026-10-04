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
 */
public class SeraphStaffItem extends TacticalWeaponItem {

    public static final int CAPACITY = 2_000_000, PER_CAST = 150_000, COOLDOWN = 160, BUFF = 200;
    public static final double RADIUS = 10.0;
    public static final float HEAL = 12.0F, SMITE_DAMAGE = 14.0F, WEIGHT = 5.0F;
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
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(RADIUS),
                e -> e.isAlive() && !e.isSpectator() && e.distanceTo(user) <= RADIUS + e.getBbWidth())) {
            Vec3 at = new Vec3(e.getX(), e.getY(0.5), e.getZ());
            if (friendly(user, e)) {
                e.heal(HEAL);
                e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BUFF, 1));
                e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BUFF, 0));
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), LIFE, 0.3F, 20), at, 10, 0.4, 0.03);
                healed++;
            } else if (e instanceof Enemy) {
                float damage = e.getMobType() == MobType.UNDEAD ? SMITE_DAMAGE * 2 : SMITE_DAMAGE;
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), damage);
                Vec3 top = at.add(0, 8, 0);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), HOLY, 0.3F, 8), top, at, 0.4);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.16F, 6), top, at, 0.3);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), HOLY, 0.3F, 8), at, 8, 0.3);
                smitten++;
            }
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

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_CAST)};
    }
}
