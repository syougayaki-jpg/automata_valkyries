package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
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
 * 虚無の大鎌『ヴォイドサイズ』（T3、追加の兵装。2026-10-03 こちらが考えた）: 両手の大鎌。攻撃力 40 / 攻撃速度 0.9、当てるとダメージの 20% を吸って回復。
 * 右クリックで「刈り取り」: まわり（半径 6）の敵を引き寄せ、360 度の斬撃で 30 ダメージと吸収。1 当たり 30,000 FE、刈り取り 150,000 FE（間隔 4 秒）。
 */
public class VoidScytheItem extends TacticalWeaponItem {

    public static final int CAPACITY = 3_000_000, PER_HIT = 30_000, PER_REAP = 150_000, COOLDOWN = 80;
    public static final double DAMAGE = 40.0, DULL_DAMAGE = 8.0, SPEED = 0.9, REAP_RADIUS = 6.0;
    public static final float REAP_DAMAGE = 30.0F, LIFESTEAL = 0.2F, WEIGHT = 14.0F;
    public static final int VOID = 0x6A2AB0, DEEP = 0x1A0828, EDGE = 0xD8A8FF;

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public VoidScytheItem(Properties properties) {
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
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= PER_HIT ? charged : dull;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!WeaponPower.use(attacker, stack, PER_HIT) || !(attacker.level() instanceof ServerLevel level)) return true;
        attacker.heal((float) DAMAGE * LIFESTEAL);
        drainFx(level, attacker, target);
        playUse(attacker, stack);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, PER_REAP)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_REAP);
            reap(server, player);
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 刈り取り（兵姫に持たせたときも同じ処理） */
    public static void reap(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        float healed = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(REAP_RADIUS, 3, REAP_RADIUS),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && e.distanceTo(user) <= REAP_RADIUS + e.getBbWidth())) {
            // 引き寄せてから斬る
            Vec3 pull = c.subtract(target.position()).normalize().scale(0.9);
            target.setDeltaMovement(pull.x, 0.25, pull.z);
            target.hurtMarked = true;
            target.invulnerableTime = 0;
            target.hurt(WeaponUtil.melee(user), REAP_DAMAGE);
            healed += REAP_DAMAGE * LIFESTEAL;
            drainFx(level, user, target);
        }
        if (healed > 0) user.heal(healed);
        // 360 度の虚無の斬撃: 斬撃の環・暗い衝撃波・吸い込まれる空間の破片
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            Vec3 p = c.add(Math.cos(a) * REAP_RADIUS * 0.6, 0, Math.sin(a) * REAP_RADIUS * 0.6);
            Fx.slash(level, p, i % 2 == 0 ? EDGE : VOID, 1.6F, 7, (float) (a + Math.PI / 2));
        }
        Fx.shockwave(level, user.position().add(0, 0.05, 0), VOID, (float) REAP_RADIUS + 1, 14);
        Fx.shockwave(level, c, EDGE, (float) REAP_RADIUS, 10);
        Fx.gather(level, Fx.opt(ModParticles.VOID_SHARD.get(), VOID, 0.22F, 12), c, 30, REAP_RADIUS, 10);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), DEEP, 2.4F, 10), c, 1, 0, 0);
        level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /** 相手から持ち主へ流れ込む紫の光（吸収） */
    private static void drainFx(ServerLevel level, LivingEntity user, LivingEntity target) {
        Vec3 from = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        Vec3 to = new Vec3(user.getX(), user.getY(0.6), user.getZ());
        for (int i = 0; i < 6; i++) {
            Vec3 p = from.add((level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), EDGE, 0.2F, 10), p, to.subtract(p).scale(0.1));
        }
        Fx.burst(level, Fx.opt(ModParticles.VOID_SHARD.get(), VOID, 0.16F, 12), from, 6, 0.25);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_HIT), format(PER_REAP)};
    }
}
