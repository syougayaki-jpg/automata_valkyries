package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * ヒートパイルバンカー（T1、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 片手の杭打ち籠手。攻撃力 7 / 攻撃速度 1.2。
 * 右クリックで、正面 4 ブロック以内の敵に赤熱した杭を打ち込む: 24 ダメージ（防具の半分を無視）・強く吹き飛ばし・3 秒燃やす。1 回 15,000 FE、間隔 2 秒。
 */
public class HeatPileBunkerItem extends TacticalWeaponItem {

    public static final int CAPACITY = 150_000, PER_STRIKE = 15_000, COOLDOWN = 40;
    public static final double DAMAGE = 20.0, SPEED = 1.2, REACH = 4.0;
    public static final float STRIKE_DAMAGE = 50.0F, WEIGHT = 6.0F;
    public static final int COLOR = 0xFF6A20, CORE = 0xFFE0A0;

    private final Multimap<Attribute, AttributeModifier> modifiers = ImmutableMultimap.of(
            Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", DAMAGE - 1.0, AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION));

    public HeatPileBunkerItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot, stack);
    }

    @Override
    public float recoil() {
        return 0.9F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_STRIKE)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_STRIKE);
            strike(server, player);
            playUse(player, stack);
        }
        clientKick(player);
        return InteractionResultHolder.consume(stack);
    }

    /** 杭を打ち込む（兵姫に持たせたときも同じ処理）。当たった相手（いなければ null） */
    public static @Nullable LivingEntity strike(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, REACH, 1.0, 1);
        Vec3 tip = eye.add(look.scale(1.0)).add(0, -0.2, 0);
        level.playSound(null, user.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.6F);
        if (ray.hits().isEmpty()) {
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.2F, 5), tip, ray.end(), 0.3);
            return null;
        }
        LivingEntity target = ray.hits().get(0);
        Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        target.hurt(WeaponDamage.source(user, WeaponDamage.ARMOR_PIERCING, user, user), WeaponDamage.armorPiercing(target, STRIKE_DAMAGE, 0.5F));
        target.setSecondsOnFire(3);
        Vec3 push = look.multiply(1, 0, 1).normalize().scale(1.4);
        target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.35, push.z));
        target.hurtMarked = true;
        // 赤熱の杭: 芯の光、飛び散る火花、貫いた先の衝撃の輪
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.25F, 6), tip, at, 0.2);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.4F, 8), tip, at, 0.3);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 10), at, 18, 0.45);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 1.4F, 5), at, 1, 0, 0);
        Fx.shockwave(level, at.subtract(0, 0.6, 0), COLOR, 2.5F, 8);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 1.4F);
        return target;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_STRIKE)};
    }
}
