package com.automatavalkyries.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * フォトンエッジ（§7.2 weapon_photon_edge、T1）: 片手剣。攻撃力 12.0 / 攻撃速度 2.2、1 振り（当たり）2,000 FE。
 * 電力が無いとただの柄（攻撃力 4.0【提案】）。
 * 3 連コンボ: 1.5 秒以内に続けて当てると数え、3 撃目は周り（2.5 ブロック）の敵にも半分のダメージの光刃が走る【提案】。
 * 敵のフォトンシールドゲージを 2.5 倍で削る特性は、シールドを持つ敵を実装したらつなぐ。
 */
public class PhotonEdgeItem extends TacticalWeaponItem {

    public static final int CAPACITY = 40_000, PER_HIT = 2_000, COMBO_WINDOW = 30;
    public static final double DAMAGE = 12.0, DULL_DAMAGE = 4.0, SPEED = 2.2, SWEEP_RANGE = 2.5;
    public static final float WEIGHT = 4.0F;

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public PhotonEdgeItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    private static Multimap<Attribute, AttributeModifier> modifiers(double damage) {
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage - 1.0, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= PER_HIT ? charged : dull;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!WeaponPower.use(attacker, stack, PER_HIT)) return true;
        if (!(attacker.level() instanceof ServerLevel level)) return true;
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4F, 2.0F);
        playUse(attacker, stack);

        CompoundTag tag = stack.getOrCreateTag();
        long now = level.getGameTime();
        int combo = now - tag.getLong("ComboAt") <= COMBO_WINDOW ? tag.getInt("Combo") + 1 : 1;
        tag.putLong("ComboAt", now);
        if (combo >= 3) {
            combo = 0;
            sweep(level, stack, target, attacker);
        }
        tag.putInt("Combo", combo);
        return true;
    }

    /** 3 撃目: 周りの敵へ光刃 */
    private static void sweep(ServerLevel level, ItemStack stack, LivingEntity target, LivingEntity attacker) {
        DamageSource source = attacker instanceof Player player ? attacker.damageSources().playerAttack(player) : attacker.damageSources().mobAttack(attacker);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SWEEP_RANGE),
                e -> e != attacker && e != target && e.isAlive() && !e.isAlliedTo(attacker))) {
            other.hurt(source, (float) (DAMAGE / 2));
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 3, 0.8, 0.1, 0.8, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 24, 1.2, 0.3, 1.2, 0.2);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.5F);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative(); // 剣と同じく、クリエイティブで振ってもブロックを壊さない
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        return true;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_HIT)};
    }
}
