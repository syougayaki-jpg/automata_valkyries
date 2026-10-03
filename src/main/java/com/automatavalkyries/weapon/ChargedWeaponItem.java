package com.automatavalkyries.weapon;

import net.minecraft.core.particles.ParticleOptions;
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

/**
 * チャージして撃つ兵装（兵器ユニット設計書 §1「弓と同じく右クリック長押しでチャージ、離して発射」）。
 * 1 射の電力はチャージの間に Tick ごとに分けて引き出す（既存仕様書 §7.2 の注・暫定統一 B）。チャージしきる前に離すと、引き出した分を戻す。
 */
public abstract class ChargedWeaponItem extends TacticalWeaponItem {

    protected final int chargeTicks, perShot;

    protected ChargedWeaponItem(Properties properties, int capacity, float weight, int chargeTicks, int perShot) {
        super(properties, capacity, weight, true);
        this.chargeTicks = chargeTicks;
        this.perShot = perShot;
    }

    public int chargeTicks() { return chargeTicks; }

    /** 撃つ（チャージしきって離したとき、サーバーで） */
    protected abstract void fire(ServerLevel level, LivingEntity user, ItemStack stack);

    /** チャージ中に銃口へ集まる粒子 */
    protected abstract ParticleOptions chargeParticle();

    @Override
    protected String[] extraAnimations() {
        return new String[]{"charge"};
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, perShot)) {
            notEnoughPower(player);
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 0.8F);
            return InteractionResultHolder.fail(stack);
        }
        stack.getOrCreateTag().putInt("Drawn", 0);
        player.startUsingItem(hand);
        if (level instanceof ServerLevel) playUse(player, stack, "charge");
        return InteractionResultHolder.consume(stack);
    }

    /** 経過 Tick（使い始めから） */
    protected int elapsed(ItemStack stack, int remaining) {
        return getUseDuration(stack) - remaining;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int t = elapsed(stack, remaining);
        if (t > chargeTicks) return;
        int share = perShot / chargeTicks + (t == chargeTicks ? perShot % chargeTicks : 0);
        if (!WeaponPower.use(user, stack, share)) {
            notEnoughPower(user);
            user.releaseUsingItem();
            return;
        }
        stack.getOrCreateTag().putInt("Drawn", stack.getOrCreateTag().getInt("Drawn") + share);
        // 銃口へ粒子が集まり、音が高くなっていく
        Vec3 muzzle = user.getEyePosition().add(user.getLookAngle().scale(1.4)).add(0, -0.25, 0);
        double f = (double) t / chargeTicks;
        for (int i = 0; i < 3; i++) {
            Vec3 from = muzzle.add((user.getRandom().nextDouble() - 0.5) * 2.4, (user.getRandom().nextDouble() - 0.5) * 2.4, (user.getRandom().nextDouble() - 0.5) * 2.4);
            Vec3 v = muzzle.subtract(from).scale(0.12);
            server.sendParticles(chargeParticle(), from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
        }
        if (t % 3 == 0) Fx.spray(server, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.WHITE, 0.15F + 0.25F * (float) f, 6), muzzle, 1, 0.05, 0);
        if (t == chargeTicks) {
            Fx.burst(server, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.WHITE, 0.35F, 10), muzzle, 12, 0.25);
            Fx.shockwave(server, user.position().add(0, 0.05, 0), Fx.WHITE, 2.0F, 10);
        }
        if (t % 5 == 0) server.playSound(null, user.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.6F + (float) f * 1.4F);
        if (t == chargeTicks) server.playSound(null, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        boolean charged = elapsed(stack, remaining) >= chargeTicks;
        if (level instanceof ServerLevel server) {
            if (charged) {
                fire(server, user, stack);
                playUse(user, stack);
            } else {
                // チャージしきる前に離した: 引き出した分を戻す
                int drawn = stack.getOrCreateTag().getInt("Drawn");
                if (!(user instanceof Player p && p.getAbilities().instabuild)) {
                    com.automatavalkyries.item.ItemEnergy.set(stack, Math.min(capacity, com.automatavalkyries.item.ItemEnergy.get(stack) + drawn));
                }
                playUse(user, stack, "use");
            }
            stack.getOrCreateTag().remove("Drawn");
        } else if (charged) {
            clientKick(user);
        }
    }

    /** チャージの進み（0〜1、一人称の揺れに使う） */
    public float chargeProgress(LivingEntity user) {
        return Math.min(1F, user.getTicksUsingItem() / (float) chargeTicks);
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
        return new Object[]{format(perShot), String.format("%.1f", chargeTicks / 20.0)};
    }
}
