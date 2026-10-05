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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 熔鉄の巨杭。杭が太さ 3・長さ 16 ブロックの赤熱した巨大な杭になって
 * 線上の敵 8 体までを貫き（同じダメージ・吹き飛ばし・燃やす）、先端（最後の敵か届いた先）で噴火する: 半径 5 の敵にも同じダメージと 5 秒の炎、
 * 立ちのぼる溶岩と火柱。杭の通り道には 2 ブロックおきに熱の輪</p>
 */
public class HeatPileBunkerItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 150_000, PER_STRIKE = 15_000, COOLDOWN = 40;
    public static final double DAMAGE = 20.0, SPEED = 1.2, REACH = 4.0;
    public static final float STRIKE_DAMAGE = 50.0F, WEIGHT = 6.0F;
    public static final int COLOR = 0xFF6A20, CORE = 0xFFE0A0;
    /** オーバーロード: 杭の長さ・太さ・貫く数・先端の噴火の半径 */
    public static final double OVER_REACH = 16.0, OVER_WIDTH = 3.0, OVER_ERUPTION = 5.0;
    public static final int OVER_PIERCE = 8;

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

    /** 戦術兵姫が杭を打つ（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!WeaponPower.use(user, stack, PER_STRIKE)) return false;
        strike(level, user);
        playUse(user, stack);
        return true;
    }

    /** 杭を打ち込む（兵姫に持たせたときも同じ処理）。当たった相手（いなければ null） */
    public static @Nullable LivingEntity strike(ServerLevel level, LivingEntity user) {
        if (OverloadArts.active(user)) return overStrike(level, user);
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

    /** オーバーロード: 長さ 16・太さ 3 の巨杭で 8 体まで貫き、先端で半径 5 に噴火する。最初に当たった相手（いなければ null） */
    private static @Nullable LivingEntity overStrike(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, OVER_REACH, OVER_WIDTH, OVER_PIERCE);
        Vec3 tip = eye.add(look.scale(1.0)).add(0, -0.2, 0);
        java.util.List<LivingEntity> hits = ray.hits().stream().filter(e -> !e.isAlliedTo(user)).toList();
        Vec3 push = look.multiply(1, 0, 1).normalize().scale(1.8);
        int shown = 0;
        for (LivingEntity target : hits) {
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(user, WeaponDamage.ARMOR_PIERCING, user, user), WeaponDamage.armorPiercing(target, STRIKE_DAMAGE, 0.5F));
            target.setSecondsOnFire(3);
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.45, push.z));
            target.hurtMarked = true;
            if (shown++ < 12) OverloadArts.impact(level, new Vec3(target.getX(), target.getY(0.6), target.getZ()));
        }
        Vec3 end = ray.end(); // 杭の先端（壁か、16 ブロック先）
        // 赤熱の巨杭: 太い芯、橙と紅の外殻、通り道の熱の輪
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.6F, 10), tip, end, 0.4);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 1.2F, 12), tip, end, 0.6);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.CRIMSON, 1.6F, 8), tip, end, 1.2);
        double length = end.distanceTo(tip);
        for (double d = 2; d < length; d += 2) Fx.beamRing(level, tip.add(look.scale(d)), look, d % 4 == 0 ? COLOR : Overload.GOLD, (float) OVER_WIDTH * 0.6F, 10);
        // 先端の噴火: 半径 5 の敵にも同じダメージと炎
        Vec3 c = end;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(OVER_ERUPTION),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator() && !hits.contains(e)
                        && e.distanceToSqr(c) <= (OVER_ERUPTION + e.getBbWidth()) * (OVER_ERUPTION + e.getBbWidth()))) {
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ARMOR_PIERCING, user, user), WeaponDamage.armorPiercing(e, STRIKE_DAMAGE, 0.5F));
            e.setSecondsOnFire(5);
            Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(0.6);
            e.setDeltaMovement(e.getDeltaMovement().add(out.x, 0.7, out.z));
            e.hurtMarked = true;
        }
        Vec3 ground = c.subtract(0, 0.6, 0);
        Fx.shockwave(level, ground, COLOR, (float) OVER_ERUPTION, 12);
        Fx.shockwave(level, ground, Overload.CRIMSON, (float) OVER_ERUPTION * 1.3F, 16);
        Fx.shockwave(level, ground.add(0, 0.4, 0), Overload.GOLD, (float) OVER_ERUPTION * 0.7F, 18);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 4.0F, 6), c, 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.4F, 14), c, 40, 0.8);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Vec3 p = ground.add(Math.cos(a) * OVER_ERUPTION * 0.6, 0, Math.sin(a) * OVER_ERUPTION * 0.6);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), i % 2 == 0 ? COLOR : Overload.CRIMSON, 0.7F, 14), p, p.add(0, 6, 0), 0.6);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, c.x, c.y, c.z, 30, OVER_ERUPTION * 0.4, 0.5, OVER_ERUPTION * 0.4, 0.2);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, c.x, c.y, c.z, 60, OVER_ERUPTION * 0.5, 1.5, OVER_ERUPTION * 0.5, 0.1);
        OverloadArts.flourish(level, user, 8.0F);
        level.playSound(null, user.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.5F, 0.4F);
        level.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.7F);
        level.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.LAVA_POP, SoundSource.PLAYERS, 1.5F, 0.6F);
        return hits.isEmpty() ? null : hits.get(0);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_STRIKE)};
    }
}
