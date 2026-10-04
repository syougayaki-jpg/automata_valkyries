package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.List;
import java.util.UUID;

/**
 * ショックウィップ（T1、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 片手の電撃の鞭。攻撃力 6 / 攻撃速度 1.6、間合い +2。
 * 右クリックで正面の扇（8 ブロック・左右 25 度）を薙ぎ払い、触れた敵に 8 の電撃・手元へ引き寄せ・鈍化 II（3 秒）。1 回 8,000 FE、間隔 1 秒。
 */
public class ShockWhipItem extends TacticalWeaponItem {

    public static final int CAPACITY = 80_000, PER_LASH = 8_000, COOLDOWN = 20;
    public static final double DAMAGE = 18.0, SPEED = 1.6, REACH = 2.0, LASH_RANGE = 8.0, HALF_ANGLE = 25.0;
    public static final float LASH_DAMAGE = 24.0F, WEIGHT = 2.5F;
    public static final int COLOR = 0xFFE060;
    private static final UUID REACH_UUID = UUID.fromString("5b6c7d8e-9fa0-4b1c-8d2e-3f4a5b6c7d8e");

    private final Multimap<Attribute, AttributeModifier> modifiers = ImmutableMultimap.of(
            Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", DAMAGE - 1.0, AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION),
            ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_UUID, "Whip reach", REACH, AttributeModifier.Operation.ADDITION));

    public ShockWhipItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot, stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_LASH)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_LASH);
            lash(server, player);
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 扇を薙ぎ払う（兵姫に持たせたときも同じ処理）。当たった数 */
    public static int lash(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        double cos = Math.cos(Math.toRadians(HALF_ANGLE));
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(LASH_RANGE),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator());
        int hit = 0;
        for (LivingEntity e : targets) {
            Vec3 to = new Vec3(e.getX(), e.getY(0.5), e.getZ()).subtract(eye);
            double dist = to.length();
            if (dist > LASH_RANGE + e.getBbWidth() || dist < 1.0E-3 || to.normalize().dot(look) < cos) continue;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ARC, user, user), LASH_DAMAGE);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            Vec3 pull = to.normalize().scale(-Math.min(1.2, dist * 0.18));
            e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0.25, pull.z));
            e.hurtMarked = true;
            WeaponUtil.lightning(level, eye.add(look.scale(0.8)).add(0, -0.3, 0), new Vec3(e.getX(), e.getY(0.6), e.getZ()), COLOR);
            hit++;
        }
        // 鞭の軌跡: 扇に沿ってしなる光の弧
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        for (int i = 0; i <= 16; i++) {
            double a = Math.toRadians(-HALF_ANGLE + 2 * HALF_ANGLE * i / 16.0);
            Vec3 dir = look.scale(Math.cos(a)).add(right.scale(Math.sin(a))).normalize();
            double r = LASH_RANGE * (0.7 + 0.3 * Math.sin(Math.PI * i / 16.0));
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.3F, 6), eye.add(dir.scale(r)).add(0, -0.3, 0), Vec3.ZERO);
            Fx.one(level, Fx.opt(ModParticles.SPARK.get(), 0xFFFFFF, 0.2F, 5), eye.add(dir.scale(r * 0.6)).add(0, -0.3, 0), Vec3.ZERO);
        }
        level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.7F, 1.8F);
        level.playSound(null, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 2.0F);
        return hit;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_LASH)};
    }
}
