package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 追加の兵装 第 3 弾の常に効く効果: 呪詛の魔導書の呪い（受けるダメージ 1.5 倍）、バーサークアックスの体力が減るほど上がる近接ダメージ、
 * 鏡刃の守り（飛び道具を跳ね返す、近接は 75% 減らして 50% を返す）。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class Batch3Events {

    private Batch3Events() {}

    /** 鏡刃の守り: 飛び道具は当たる前に止めて跳ね返す */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity defender = event.getEntity();
        if (!(defender.level() instanceof ServerLevel level) || !Batch3T3.ReflectorBlade.guarding(defender)) return;
        DamageSource source = event.getSource();
        if (!source.is(DamageTypeTags.IS_PROJECTILE) || !(source.getDirectEntity() instanceof Projectile projectile)) return;
        event.setCanceled(true);
        Vec3 back = projectile.getDeltaMovement().scale(-1.2);
        WeaponUtil.later(level, 1, () -> { // 当たった Tick のうちは弾自身が向きを上書きするので次の Tick に
            if (!projectile.isRemoved()) {
                projectile.setOwner(defender);
                projectile.setDeltaMovement(back);
                projectile.hurtMarked = true;
            }
        });
        Fx.burst(level, Fx.opt(ModParticles.HEX.get(), Batch3T3.ReflectorBlade.COLOR, 0.2F, 12), projectile.position(), 8, 0.2);
        level.playSound(null, defender.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        float amount = event.getAmount();
        // 呪い: あらゆるダメージを 1.5 倍
        if (Batch3T3.HexGrimoire.cursed(target)) amount *= Batch3T3.HexGrimoire.AMPLIFY;
        // 狂戦士: 体力が減るほど近接が強い
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && attacker.getMainHandItem().getItem() instanceof Batch3T2.BerserkAxe) {
            amount *= Batch3T2.BerserkAxe.multiplier(attacker);
        }
        // 鏡の守り: 近接は 75% 減らし、相手に 50% を返す
        if (Batch3T3.ReflectorBlade.guarding(target) && source.getEntity() instanceof LivingEntity attacker
                && source.getDirectEntity() == attacker && attacker != target
                && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) { // 返した分は返さない（鏡どうしで往復しない）
            float original = amount;
            amount *= 0.25F;
            attacker.hurt(target.damageSources().thorns(target), original * 0.5F);
            if (target.level() instanceof ServerLevel level) {
                Fx.burst(level, Fx.opt(ModParticles.HEX.get(), Batch3T3.ReflectorBlade.COLOR, 0.2F, 12), new Vec3(attacker.getX(), attacker.getY(0.5), attacker.getZ()), 8, 0.2);
            }
        }
        event.setAmount(amount);
    }
}
