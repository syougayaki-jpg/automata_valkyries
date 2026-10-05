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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 黒死の群れ撃ち。1 射が 12 本の針になり、持ち主の前に開く緑と紅の陣から
 * 扇（左右 60 度・上下にも散らして）に放たれる。針は 24 ブロック以内の敵へ向きを問わず鋭く曲がり、刺さると半径 4 の黒死の瘴気が弾けて
 * 中の敵すべてに黒死侵食を重ねる（{@link NecrosisNeedleEntity#setOverloaded}）</p>
 */
public class NecrosisNeedleItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    /** 弾の威力（説明に出す） */
    @Override
    public float[] shotDamage() {
        return new float[]{NecrosisNeedleEntity.DAMAGE, 1};
    }


    public static final int CAPACITY = 200_000, PER_SHOT = 10_000, COOLDOWN = 8;
    public static final float WEIGHT = 6.0F;
    /** オーバーロード: 1 射の針の数・扇の左右の角度 */
    public static final int OVER_NEEDLES = 12;
    public static final double OVER_FAN = 60.0;

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
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, gun, PER_SHOT);
            fire(server, player);
            playUse(player, gun);
        }
        clientKick(player);
        return InteractionResultHolder.consume(gun); // 殴る振りはしない（反動の動きを付ける）
    }

    /** 戦術兵姫が撃つ（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack gun, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!WeaponPower.use(user, gun, PER_SHOT)) return false;
        fire(level, user);
        playUse(user, gun);
        return true;
    }

    public static void fire(ServerLevel level, LivingEntity shooter) {
        if (OverloadArts.active(shooter)) {
            swarm(level, shooter);
            return;
        }
        NecrosisNeedleEntity needle = new NecrosisNeedleEntity(level, shooter);
        Vec3 look = shooter.getLookAngle();
        needle.setPos(shooter.getX() + look.x * 0.6, shooter.getEyeY() - 0.15, shooter.getZ() + look.z * 0.6);
        needle.shoot(look.x, look.y, look.z, NecrosisNeedleEntity.SPEED, 0.5F);
        level.addFreshEntity(needle);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    /** オーバーロード: 12 本の誘導針を扇に放つ */
    private static void swarm(ServerLevel level, LivingEntity shooter) {
        Vec3 look = shooter.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(look).normalize();
        Vec3 origin = new Vec3(shooter.getX() + look.x * 0.6, shooter.getEyeY() - 0.15, shooter.getZ() + look.z * 0.6);
        for (int i = 0; i < OVER_NEEDLES; i++) {
            double a = Math.toRadians(-OVER_FAN + 2 * OVER_FAN * i / (OVER_NEEDLES - 1));
            double lift = Math.toRadians((i % 3 - 1) * 15);
            Vec3 dir = look.scale(Math.cos(a) * Math.cos(lift)).add(right.scale(Math.sin(a))).add(up.scale(Math.sin(lift))).normalize();
            NecrosisNeedleEntity needle = new NecrosisNeedleEntity(level, shooter);
            needle.setOverloaded(true);
            Vec3 p = origin.add(right.scale(Math.sin(a) * 0.8)).add(up.scale(Math.sin(lift) * 0.8));
            needle.setPos(p.x, p.y, p.z);
            needle.shoot(dir.x, dir.y, dir.z, NecrosisNeedleEntity.SPEED, 0.0F);
            level.addFreshEntity(needle);
        }
        Vec3 front = shooter.getEyePosition().add(look.scale(1.2));
        Fx.mandala(level, front, Fx.GREEN, 1.8F, 14, true, true);
        Fx.mandala(level, front.add(look.scale(0.1)), Overload.CRIMSON, 1.2F, 14, true, false);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.VOID_SHARD.get(), Fx.GREEN, 0.12F, 16), front, 16, 0.4);
        OverloadArts.flourish(level, shooter, 5.0F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.2F, 1.2F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5F, 1.8F);
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
