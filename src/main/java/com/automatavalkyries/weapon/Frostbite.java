package com.automatavalkyries.weapon;

import com.automatavalkyries.config.WeaponConfig;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 凍傷（アバランチの冷凍ミサイル）: 当たるたびに 1 段重なり（最大 V）、5 秒続く。1 段ごとに移動速度 15% と跳躍力 15% を下げる
 * （跳躍は {@link WeaponEvents} で）。体が霜で覆われ（バニラの凍える見た目、凍えのダメージが出る手前まで）、冷気が漂う。
 * プレイヤーへは設定 {@code strongEffectsOnPlayers} のときだけ（ダメージそのものは入る）。
 */
public final class Frostbite {

    public static final int MAX_LEVEL = 4, DURATION = 100;
    public static final double PER_LEVEL = 0.15;

    private Frostbite() {}

    public static void apply(LivingEntity target) {
        if (target instanceof Player && !WeaponConfig.strongEffectsOnPlayers()) return;
        MobEffectInstance current = target.getEffect(ModWeapons.FROSTBITE.get());
        int level = current == null ? 0 : Math.min(MAX_LEVEL, current.getAmplifier() + 1);
        target.addEffect(new MobEffectInstance(ModWeapons.FROSTBITE.get(), DURATION, level, false, true, true));
        // バニラの凍える見た目（凍えのダメージが出る 140 の手前まで）
        target.setTicksFrozen(Math.min(130, target.getTicksFrozen() + 20));
    }

    /** 跳躍力の倍率（凍傷が無ければ 1） */
    public static double jumpFactor(LivingEntity entity) {
        MobEffectInstance effect = entity.getEffect(ModWeapons.FROSTBITE.get());
        return effect == null ? 1 : Math.max(0, 1 - PER_LEVEL * (effect.getAmplifier() + 1));
    }

    public static class Effect extends MobEffect {
        public Effect() {
            super(MobEffectCategory.HARMFUL, 0x8AD8FF);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, "6d9e3f4a-8c5b-4a0d-9e3f-4b5c6d7e8f90", -PER_LEVEL, AttributeModifier.Operation.MULTIPLY_TOTAL);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.getTicksFrozen() < 100) entity.setTicksFrozen(Math.min(130, entity.getTicksFrozen() + 3)); // 霜を保つ
            if (entity.level() instanceof ServerLevel level && entity.tickCount % 6 == 0) {
                Vec3 at = new Vec3(entity.getX(), entity.getY(0.6), entity.getZ());
                Fx.spray(level, Fx.opt(ModParticles.SPARK.get(), Fx.ICE, 0.14F + amplifier * 0.03F, 12), at, 1 + amplifier, entity.getBbWidth() * 0.6, 0.01);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 1 + amplifier, 0.3, 0.4, 0.3, 0.01);
            }
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }
    }
}
