package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * サンダージャベリン（T2、追加の兵装。2026-10-03 こちらが考えた）: 両手の雷の投げ槍。右クリック長押しで 1 秒溜めて投げる
 * （{@link JavelinEntity}、弧を描いて飛ぶ）。当たった相手に 18 ダメージで刺さり、1 秒後に刺さった所へ雷が落ちて半径 4 の敵に 25 ダメージ。
 * 1 投 50,000 FE。
 */
public class ThunderJavelinItem extends ChargedWeaponItem {

    public static final int CAPACITY = 1_000_000, PER_THROW = 50_000, CHARGE = 20;
    public static final float WEIGHT = 6.0F;

    public ThunderJavelinItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, CHARGE, PER_THROW);
    }

    @Override
    public float recoil() {
        return 0.5F;
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return Fx.opt(ModParticles.SPARK.get(), JavelinEntity.COLOR, 0.2F, 8);
    }

    @Override
    protected void fire(ServerLevel level, LivingEntity user, ItemStack stack) {
        hurl(level, user);
    }

    /** 兵姫に持たせたときも同じ処理 */
    public static JavelinEntity hurl(ServerLevel level, LivingEntity user) {
        JavelinEntity javelin = new JavelinEntity(level, user);
        Vec3 look = user.getLookAngle();
        javelin.setPos(user.getX() + look.x * 0.6, user.getEyeY() - 0.1, user.getZ() + look.z * 0.6);
        javelin.shoot(look.x, look.y, look.z, JavelinEntity.SPEED, 0.5F);
        level.addFreshEntity(javelin);
        level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.4F, 1.6F);
        return javelin;
    }
}
