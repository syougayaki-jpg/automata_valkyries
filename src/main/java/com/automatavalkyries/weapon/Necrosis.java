package com.automatavalkyries.weapon;

import com.automatavalkyries.config.WeaponConfig;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 黒死侵食（§7.2 ネクロシス・ニードルガンの特性）: 当たるたびに最大 HP を 1% ずつ永続的に削り（最大 30%）、
 * 10 秒の間、毎秒 最大 HP の 1%【提案】の割合ダメージ。
 * 削った量は最大 HP の属性の修飾（{@link #MODIFIER}）そのもので数えるので、対象が死ぬと（エンティティごと）消える。
 * プレイヤーへは設定 {@code strongEffectsOnPlayers} が true のときだけ。保護対象（Eprotect のボス）は最大 HP の改ざんを毎 Tick 戻すので、効かない。
 */
public final class Necrosis {

    public static final UUID MODIFIER = UUID.fromString("5b0c0f7e-8d0a-4c1e-9f5a-6e2b7d3c4a91");
    public static final int MAX_STACKS = 30, DURATION = 200;

    private Necrosis() {}

    public static boolean affects(LivingEntity target) {
        return !(target instanceof Player) || WeaponConfig.strongEffectsOnPlayers();
    }

    public static int stacks(LivingEntity target) {
        AttributeInstance health = target.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier modifier = health == null ? null : health.getModifier(MODIFIER);
        return modifier == null ? 0 : (int) Math.round(-modifier.getAmount() * 100);
    }

    public static void apply(LivingEntity target) {
        if (!affects(target) || !target.isAlive()) return;
        AttributeInstance health = target.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        int stacks = Math.min(MAX_STACKS, stacks(target) + 1);
        health.removeModifier(MODIFIER);
        health.addPermanentModifier(new AttributeModifier(MODIFIER, "Necrosis", -stacks / 100.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (target.getHealth() > target.getMaxHealth()) target.setHealth(target.getMaxHealth());
        target.addEffect(new MobEffectInstance(ModWeapons.NECROSIS.get(), DURATION, 0, false, true, true));
    }

    /** 毎秒の割合ダメージ */
    public static class Effect extends MobEffect {
        public Effect() {
            super(MobEffectCategory.HARMFUL, 0x3A6A44);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.level().isClientSide()) return;
            entity.hurt(entity.damageSources().magic(), Math.max(1.0F, entity.getMaxHealth() * 0.01F * (amplifier + 1)));
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }
    }
}
