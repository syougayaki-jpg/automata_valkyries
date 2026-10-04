package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * アークランサー（T1、追加の兵装。2026-10-03 ユーザーの依頼でこちらが考えた）: 片手の電撃の槍。攻撃力 9 / 攻撃速度 1.4、間合い +1.5 ブロック。
 * 当てると稲妻が近くの敵（6 ブロック以内）へ最大 3 体まで次々に跳び、それぞれ半分（4.5）のダメージ。1 当たり 1,500 FE。
 */
public class ArcLancerItem extends TacticalWeaponItem {

    public static final int CAPACITY = 30_000, PER_HIT = 1_500, CHAINS = 3;
    public static final double DAMAGE = 30.0, DULL_DAMAGE = 10.0, SPEED = 1.4, REACH = 1.5, CHAIN_RANGE = 6.0;
    public static final float WEIGHT = 4.5F;
    public static final int COLOR = 0x7FD8FF;
    private static final UUID REACH_UUID = UUID.fromString("8a1d2c3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e");

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public ArcLancerItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    private static Multimap<Attribute, AttributeModifier> modifiers(double damage) {
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage - 1.0, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION),
                ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_UUID, "Lance reach", REACH, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= PER_HIT ? charged : dull;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!WeaponPower.use(attacker, stack, PER_HIT) || !(attacker.level() instanceof ServerLevel level)) return true;
        chain(level, attacker, target);
        playUse(attacker, stack);
        return true;
    }

    /** 稲妻を近くの敵へ次々に跳ばす（兵姫に持たせたときも同じ処理） */
    public static void chain(ServerLevel level, LivingEntity attacker, LivingEntity first) {
        Vec3 at = new Vec3(first.getX(), first.getY(0.6), first.getZ());
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), at, 10, 0.35);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.8F, 6), at, 1, 0, 0);
        level.playSound(null, first.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.8F);
        List<LivingEntity> hit = new ArrayList<>(List.of(first));
        LivingEntity from = first;
        for (int i = 0; i < CHAINS; i++) {
            LivingEntity source = from;
            LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(CHAIN_RANGE),
                            e -> e != attacker && !hit.contains(e) && e.isAlive() && !e.isAlliedTo(attacker) && e instanceof net.minecraft.world.entity.monster.Enemy)
                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(source))).orElse(null);
            if (next == null) break;
            hit.add(next);
            WeaponUtil.lightning(level, new Vec3(source.getX(), source.getY(0.6), source.getZ()), new Vec3(next.getX(), next.getY(0.6), next.getZ()), COLOR);
            next.invulnerableTime = 0;
            next.hurt(WeaponDamage.source(attacker, WeaponDamage.ARC, attacker, attacker), (float) (DAMAGE / 2));
            from = next;
        }
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_HIT)};
    }
}
