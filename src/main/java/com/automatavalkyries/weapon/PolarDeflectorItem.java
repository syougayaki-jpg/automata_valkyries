package com.automatavalkyries.weapon;

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

/**
 * 偏光ディフレクター（§7.2 module_polar_deflector、T1）: 左手シールド。右クリックを押している間だけ展開し、
 * 展開中は毎秒 15,000 FE（毎 Tick 750 FE）を使う。前方 120 度からの射撃・爆発のダメージを 100% 止め、
 * 飛んできた弾は向きを返して跳ね返す（{@link WeaponEvents}）。
 */
public class PolarDeflectorItem extends TacticalWeaponItem {

    public static final int CAPACITY = 300_000, PER_SECOND = 15_000, PER_TICK = PER_SECOND / 20;
    public static final float WEIGHT = 8.0F;
    /** 前方 120 度（向きとの内積が cos 60° 以上） */
    public static final double FRONT_DOT = 0.5;

    public PolarDeflectorItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    protected String[] extraAnimations() {
        return new String[]{"deploy", "retract"};
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_TICK)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (level instanceof ServerLevel) {
            playUse(player, stack, "deploy");
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide()) return;
        if (!WeaponPower.use(user, stack, PER_TICK)) {
            notEnoughPower(user);
            user.releaseUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (level instanceof ServerLevel) {
            playUse(user, stack, "retract");
            level.playSound(null, user.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
        }
    }

    /** user が今この盾を展開しているか */
    public static boolean deployed(LivingEntity user) {
        return user.isUsingItem() && user.getUseItem().getItem() instanceof PolarDeflectorItem;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SECOND)};
    }
}
