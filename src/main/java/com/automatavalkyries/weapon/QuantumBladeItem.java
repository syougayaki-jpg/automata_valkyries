package com.automatavalkyries.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

/**
 * クアンタムブレード（T3、ユーザー指定 2026-10-03）: 片手剣。振るたびに（空振りでも）三日月形の斬撃を前へまっすぐ飛ばす。
 * 斬撃の向きは縦・横・斜めからランダム（{@link QuantumSlashEntity}、1 発 18 ダメージ・射程 16 ブロック・線上の全員を貫く・1 発 20,000 FE）。
 * 剣としての攻撃力 20 / 攻撃速度 1.8【提案】。斬撃は 0.3 秒に 1 発まで【提案】（掘るときの連続の振りで撃ちすぎないように）。
 */
public class QuantumBladeItem extends TacticalWeaponItem {

    public static final int CAPACITY = 400_000, PER_SLASH = 20_000, SLASH_INTERVAL = 6;
    public static final double DAMAGE = 200.0, DULL_DAMAGE = 50.0, SPEED = 1.8;
    public static final float WEIGHT = 7.0F;

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public QuantumBladeItem(Properties properties) {
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
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= PER_SLASH ? charged : dull;
    }

    /** 振ったとき（サーバーでも呼ばれる。空振りも含む） */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level && entity.getMainHandItem() == stack) {
            CompoundTag tag = stack.getOrCreateTag();
            long now = level.getGameTime();
            if (now >= tag.getLong("NextSlash") && WeaponPower.use(entity, stack, PER_SLASH)) {
                tag.putLong("NextSlash", now + WeaponUpgrades.ticks(stack, SLASH_INTERVAL));
                slash(level, entity);
                playUse(entity, stack);
            }
        }
        return false;
    }

    /** 三日月の斬撃を 1 発。向きは縦・横・斜め（0・45・90・135 度）にゆらぎを足す。兵姫に持たせたときも同じ処理 */
    public static QuantumSlashEntity slash(ServerLevel level, LivingEntity user) {
        QuantumSlashEntity slash = new QuantumSlashEntity(level, user);
        Vec3 look = user.getLookAngle();
        slash.setPos(user.getX() + look.x * 0.8, user.getEyeY() - 0.25 + look.y * 0.8, user.getZ() + look.z * 0.8);
        float roll = (float) Math.toRadians(45 * level.random.nextInt(4) + (level.random.nextFloat() - 0.5F) * 20);
        slash.setRoll(roll);
        slash.shoot(look.x, look.y, look.z, QuantumSlashEntity.SPEED, 0);
        level.addFreshEntity(slash);
        // 放つ瞬間: 刃の向きの斬撃の閃き、光の玉、きらめきが手元から散る
        Vec3 muzzle = slash.position();
        Fx.slash(level, muzzle, QuantumSlashEntity.COLOR_CORE, 1.2F, 4, roll);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), QuantumSlashEntity.COLOR_GLOW, 0.7F, 6), muzzle, 1, 0, 0);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), QuantumSlashEntity.COLOR_EDGE, 0.2F, 8), muzzle, 6, 0.2);
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        return slash;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), QuantumSlashEntity.COLOR_EDGE, 0.25F, 8),
                    new Vec3(target.getX(), target.getY(0.6), target.getZ()), 6, 0.25);
        }
        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        return true;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SLASH)};
    }
}
