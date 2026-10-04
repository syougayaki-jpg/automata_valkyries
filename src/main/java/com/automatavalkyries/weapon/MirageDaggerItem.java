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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * ミラージュダガー（T2、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 片手の短剣。攻撃力 8 / 攻撃速度 2.4。
 * 右クリックで、見ている敵（16 ブロック以内）の背後へ瞬間移動して背中を刺す（20 ダメージ）。元の場所に残像が残る。
 * 背後が塞がっていたら移動しない（電力も使わない）。1 回 20,000 FE、間隔 3 秒。
 */
public class MirageDaggerItem extends TacticalWeaponItem {

    public static final int CAPACITY = 400_000, PER_BLINK = 20_000, COOLDOWN = 60;
    public static final double DAMAGE = 50.0, SPEED = 2.4, RANGE = 16.0;
    public static final float BACKSTAB_DAMAGE = 100.0F, WEIGHT = 1.5F;
    public static final int COLOR = 0xC890FF, CORE = 0xF4E8FF;

    private final Multimap<Attribute, AttributeModifier> modifiers = ImmutableMultimap.of(
            Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", DAMAGE - 1.0, AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION));

    public MirageDaggerItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot, stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_BLINK)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server) {
            if (blink(server, player) == null) return InteractionResultHolder.fail(stack);
            WeaponPower.use(player, stack, PER_BLINK);
            player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 見ている敵の背後へ跳んで刺す（兵姫に持たせたときも同じ処理）。刺した相手（跳べなければ null） */
    public static @Nullable LivingEntity blink(ServerLevel level, LivingEntity user) {
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), RANGE, 1.5, 1);
        if (ray.hits().isEmpty()) return null;
        LivingEntity target = ray.hits().get(0);
        Vec3 away = new Vec3(target.getX() - user.getX(), 0, target.getZ() - user.getZ());
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        Vec3 behind = target.position().add(away.scale(target.getBbWidth() * 0.5 + user.getBbWidth() * 0.5 + 0.6));
        AABB box = user.getDimensions(user.getPose()).makeBoundingBox(behind);
        if (!level.noCollision(user, box)) return null;
        Vec3 from = user.position();
        // 残像（元の場所に紫の人影の粒）と、跳んだ先の閃光
        for (int i = 0; i < 12; i++) {
            Vec3 p = from.add((level.random.nextDouble() - 0.5) * 0.6, level.random.nextDouble() * user.getBbHeight(), (level.random.nextDouble() - 0.5) * 0.6);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.35F, 20), p, new Vec3(0, 0.01, 0));
        }
        Fx.line(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.15F, 8), from.add(0, 1, 0), behind.add(0, 1, 0), 0.6);
        float yaw = (float) (Math.toDegrees(Math.atan2(-(-away.x), -away.z))); // 相手の方を向く
        if (user instanceof net.minecraft.server.level.ServerPlayer player) {
            player.teleportTo(level, behind.x, behind.y, behind.z, yaw, player.getXRot()); // 向きも画面に届ける
        } else {
            user.teleportTo(behind.x, behind.y, behind.z);
            user.setYRot(yaw);
        }
        user.setYHeadRot(yaw);
        user.resetFallDistance();
        Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        target.hurt(WeaponUtil.melee(user), BACKSTAB_DAMAGE);
        Fx.slash(level, at, COLOR, 1.4F, 6, (float) (level.random.nextDouble() * Math.PI * 2));
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), CORE, 0.3F, 8), at, 10, 0.3);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 1.0F, 5), behind.add(0, 1, 0), 1, 0, 0);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.6F);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
        return target;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_BLINK)};
    }
}
