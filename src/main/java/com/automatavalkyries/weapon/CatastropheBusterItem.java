package com.automatavalkyries.weapon;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 特異点砲『カタストロフ・バスター』（§7.2 weapon_catastrophe_buster、T4）: 両手超兵器。3 秒チャージして離すと、
 * 直径 5 ブロックのブラックホール弾を撃つ（1 射 1,000,000 FE）。着弾（か 3 秒）で空間が崩壊する（{@link BlackHoleEntity}）。
 * ブロックの破壊は設定 {@code blockDestruction} のときだけ（既定はオフ）。
 */
public class CatastropheBusterItem extends ChargedWeaponItem {

    public static final int CAPACITY = 5_000_000, PER_SHOT = 1_000_000, CHARGE = 60;
    public static final float WEIGHT = 35.0F;

    public CatastropheBusterItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, CHARGE, PER_SHOT);
    }

    @Override
    public float recoil() {
        return 1.6F;
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide() && selected && entity instanceof LivingEntity user && level.getGameTime() % 20 == 0) blessHolder(user);
    }

    /** 手に持っている間の加護（2026-10-04 ユーザー指定。ルミナス・レクイエムと同じく 1 秒ごとに掛け直す）。特異点の重力: 耐性 III・力 III・低速落下・火炎耐性。兵姫に持たせたときも同じ処理（暗視は切れかけの点滅が出ないよう長めに掛ける） */
    public static void blessHolder(LivingEntity user) {
        Overload.bless(user, net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 60, 2);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 60, 2);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.SLOW_FALLING, 60, 0);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 60, 0);
        Overload.blessExtras(user); // オーバーロードなら耐性 IV・再生 VI
    }

    /** 通常攻撃（左クリック。空振りでも。0.75 秒に 1 回・100,000 FE）: 敵を追う極小のカタストロフを撃つ（{@link T4NormalAttacks}） */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level && T4NormalAttacks.ready(entity, stack, 15, 100_000)) {
            T4NormalAttacks.miniCatastrophe(level, entity);
        }
        return false;
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return Fx.opt(com.automatavalkyries.registry.ModParticles.VOID_SHARD.get(), Fx.ORANGE, 0.14F, 12);
    }

    @Override
    protected void fire(ServerLevel level, LivingEntity user, ItemStack stack) {
        shoot(level, user);
    }

    /** 兵姫に持たせたときも同じ処理 */
    public static void shoot(ServerLevel level, LivingEntity user) {
        BlackHoleEntity hole = new BlackHoleEntity(level, user);
        hole.setScale((float) Overload.range(user)); // オーバーロードの T4 は大きい
        Vec3 look = user.getLookAngle();
        hole.setPos(user.getX() + look.x * 1.5, user.getEyeY() - 0.3 + look.y * 1.5, user.getZ() + look.z * 1.5);
        hole.shoot(look.x, look.y, look.z, BlackHoleEntity.SPEED, 0);
        level.addFreshEntity(hole);
        level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.5F);
        user.push(-look.x * 0.6, 0.1, -look.z * 0.6); // 大きな反動
        user.hurtMarked = true;
    }
}
