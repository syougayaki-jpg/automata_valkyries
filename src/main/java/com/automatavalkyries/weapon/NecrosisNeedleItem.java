package com.automatavalkyries.weapon;

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
 * ネクロシス・ニードルガン（§7.2 weapon_necrosis_needle、T1）: 片手射撃。1 射 10,000 FE で誘導する針を撃つ
 * （{@link NecrosisNeedleEntity}、単発 14.0 ダメージ）。当たると黒死侵食（{@link Necrosis}）。連射の間隔は 0.4 秒【提案】。
 */
public class NecrosisNeedleItem extends TacticalWeaponItem {

    public static final int CAPACITY = 200_000, PER_SHOT = 10_000, COOLDOWN = 8;
    public static final float WEIGHT = 6.0F;

    public NecrosisNeedleItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!WeaponPower.has(player, gun, PER_SHOT)) {
            notEnoughPower(player);
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 1.4F);
            return InteractionResultHolder.fail(gun);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, gun, PER_SHOT);
            fire(server, player);
            playUse(player, gun);
        }
        clientKick(player);
        return InteractionResultHolder.consume(gun); // 殴る振りはしない（反動の動きを付ける）
    }

    public static void fire(ServerLevel level, LivingEntity shooter) {
        NecrosisNeedleEntity needle = new NecrosisNeedleEntity(level, shooter);
        Vec3 look = shooter.getLookAngle();
        needle.setPos(shooter.getX() + look.x * 0.6, shooter.getEyeY() - 0.15, shooter.getZ() + look.z * 0.6);
        needle.shoot(look.x, look.y, look.z, NecrosisNeedleEntity.SPEED, 0.5F);
        level.addFreshEntity(needle);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    @Override
    public float recoil() {
        return 0.45F;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SHOT)};
    }
}
