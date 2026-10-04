package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
 * グラビティハンマー（T1、追加の兵装。2026-10-03 こちらが考えた）: 両手の重力槌。攻撃力 14 / 攻撃速度 0.8。
 * 右クリックで地面を叩き、半径 5 の敵に 10 ダメージを与えて空へ打ち上げる（重力反転の衝撃波）。1 回 20,000 FE、間隔 3 秒。
 */
public class GravityHammerItem extends TacticalWeaponItem {

    public static final int CAPACITY = 200_000, PER_SLAM = 20_000, COOLDOWN = 60;
    public static final double DAMAGE = 45.0, DULL_DAMAGE = 15.0, SPEED = 0.8, RADIUS = 5.0;
    public static final float SLAM_DAMAGE = 32.0F, WEIGHT = 12.0F;
    public static final int COLOR = 0xA070FF;

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public GravityHammerItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, true);
    }

    private static Multimap<Attribute, AttributeModifier> modifiers(double damage) {
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage - 1.0, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= PER_SLAM / 10 ? charged : dull;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, PER_SLAM)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_SLAM);
            slam(server, player);
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 地面を叩いて打ち上げる（兵姫に持たせたときも同じ処理） */
    public static void slam(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(RADIUS, 2, RADIUS),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && e.distanceTo(user) <= RADIUS + e.getBbWidth())) {
            target.hurt(WeaponUtil.melee(user), SLAM_DAMAGE);
            Vec3 out = new Vec3(target.getX() - c.x, 0, target.getZ() - c.z);
            out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(0.35);
            target.setDeltaMovement(target.getDeltaMovement().add(out.x, 0.95, out.z));
            target.hurtMarked = true;
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 8), new Vec3(target.getX(), target.getY(0.3), target.getZ()), 4, 0.2);
        }
        // 重力反転の衝撃波: 三重の輪、舞い上がる地面の破片、紫の光
        Vec3 ground = c.add(0, 0.05, 0);
        Fx.shockwave(level, ground, 0xFFFFFF, (float) RADIUS + 1, 10);
        Fx.shockwave(level, ground, COLOR, (float) RADIUS, 14);
        Fx.shockwave(level, ground.add(0, 0.6, 0), 0x6040C0, (float) RADIUS * 0.7F, 16);
        BlockState below = level.getBlockState(BlockPos.containing(c).below());
        if (!below.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), c.x, c.y + 0.2, c.z, 60, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.3);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            Vec3 p = c.add(Math.cos(a) * RADIUS * 0.6, 0.2, Math.sin(a) * RADIUS * 0.6);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.4F, 16), p, new Vec3(0, 0.25, 0));
        }
        level.playSound(null, user.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), new Vec3(target.getX(), target.getY(0.5), target.getZ()), 6, 0.3);
            playUse(attacker, stack);
        }
        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SLAM)};
    }
}
