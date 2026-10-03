package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 黒蓮扇『ブラックロータス』（T3、追加の兵装。2026-10-03 こちらが考えた）: 片手の扇。右クリックで 6 秒のあいだ、黒と薄紅の花弁の刃 8 枚が
 * 持ち主のまわり（半径 2.5）を舞う嵐を起こす。嵐に触れた敵（半径 3.5）は 0.5 秒ごとに 8 ダメージ。嵐の終わりに花弁の刃が 8 方向へ飛び散り、
 * 線上（12 ブロック）の敵に 16 ダメージ。1 回 100,000 FE、間隔 10 秒。
 */
public class BlackLotusItem extends TacticalWeaponItem {

    public static final int CAPACITY = 1_500_000, PER_STORM = 100_000, DURATION = 120, COOLDOWN = 200, PETALS = 8, TICK_RATE = 10;
    public static final float STORM_DAMAGE = 8.0F, BURST_DAMAGE = 16.0F, WEIGHT = 2.0F;
    public static final double ORBIT = 2.5, REACH = 3.5, BURST_RANGE = 12.0;
    public static final int PINK = 0xFF8AC8, BLACK = 0x3A1028, WHITE = 0xFFE8F4;

    public BlackLotusItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_STORM)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_STORM);
            stack.getOrCreateTag().putInt("Storm", DURATION);
            Fx.shockwave(server, player.position().add(0, 0.05, 0), PINK, 4.0F, 14);
            server.playSound(null, player.blockPosition(), SoundEvents.CHERRY_LEAVES_BREAK, SoundSource.PLAYERS, 1.2F, 0.8F);
            server.playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.4F, 1.6F);
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        CompoundTag tag = stack.getTag();
        if (!(level instanceof ServerLevel server) || tag == null || tag.getInt("Storm") <= 0 || !(entity instanceof LivingEntity user)) return;
        int left = tag.getInt("Storm") - 1;
        tag.putInt("Storm", left);
        storm(server, user, DURATION - left);
        if (left == 0) burst(server, user);
    }

    /** 嵐の 1 Tick: 花弁の刃が舞い、触れた敵を斬る（兵姫に持たせたときも同じ処理） */
    public static void storm(ServerLevel level, LivingEntity user, int t) {
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        for (int k = 0; k < PETALS; k++) {
            double a = t * 0.32 + k * Math.PI * 2 / PETALS;
            double bob = Math.sin(t * 0.2 + k) * 0.5;
            Vec3 p = c.add(Math.cos(a) * ORBIT, bob, Math.sin(a) * ORBIT);
            Fx.slash(level, p, k % 2 == 0 ? PINK : WHITE, 0.45F, 3, (float) (a + Math.PI / 2));
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), k % 2 == 0 ? PINK : BLACK, 0.22F, 8), p, 1, 0, 0);
        }
        if (t % 3 == 0) {
            Fx.spray(level, Fx.opt(ModParticles.HEX.get(), PINK, 0.1F, 20), c, 3, ORBIT, 0.02);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES, c.x, c.y + 1, c.z, 3, ORBIT, 1, ORBIT, 0.02);
        }
        if (t % TICK_RATE != 0) return;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(REACH),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && e.distanceTo(user) <= REACH + e.getBbWidth())) {
            target.invulnerableTime = 0;
            target.hurt(WeaponUtil.melee(user), STORM_DAMAGE);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), PINK, 0.25F, 8), new Vec3(target.getX(), target.getY(0.6), target.getZ()), 5, 0.25);
        }
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.7F);
    }

    /** 嵐の終わり: 花弁の刃が 8 方向へ飛び散る */
    public static void burst(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        for (int k = 0; k < PETALS; k++) {
            double a = k * Math.PI * 2 / PETALS;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, c, dir, BURST_RANGE, 1.0, 8);
            for (LivingEntity target : ray.hits()) {
                target.invulnerableTime = 0;
                target.hurt(WeaponUtil.melee(user), BURST_DAMAGE);
            }
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), WHITE, 0.2F, 8), c, ray.end(), 0.4);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), PINK, 0.35F, 12), c, ray.end(), 0.6);
            Fx.slash(level, ray.end(), PINK, 1.0F, 6, (float) a);
        }
        Fx.shockwave(level, user.position().add(0, 0.05, 0), PINK, (float) BURST_RANGE, 14);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), WHITE, 0.35F, 12), c, 20, 0.5);
        level.playSound(null, user.blockPosition(), SoundEvents.CHERRY_LEAVES_BREAK, SoundSource.PLAYERS, 1.5F, 1.2F);
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_STORM)};
    }
}
