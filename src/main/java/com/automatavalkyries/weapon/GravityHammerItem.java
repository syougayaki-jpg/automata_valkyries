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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 重力崩壊。叩いた瞬間に半径 20 の敵が紫の特異点へ吸い寄せられ（ダメージなし）、
 * 0.4 秒後に半径 20 の地面が爆ぜて敵にダメージを与え高く打ち上げる（紫・白・紅・金の 5 重の衝撃波、輪に立つ 16 本の光の柱）。
 * さらに 1 秒後、打ち上げた敵を重力反転で地面へ叩き落とす（落下の衝撃波）</p>
 */
public class GravityHammerItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 200_000, PER_SLAM = 20_000, COOLDOWN = 60;
    public static final double DAMAGE = 45.0, DULL_DAMAGE = 15.0, SPEED = 0.8, RADIUS = 5.0;
    public static final float SLAM_DAMAGE = 32.0F, WEIGHT = 12.0F;
    public static final int COLOR = 0xA070FF;
    /** オーバーロード: 半径・吸い寄せてから叩くまで・叩いてから叩き落とすまで（Tick）・光の柱の数 */
    public static final double OVER_RADIUS = 20.0;
    public static final int OVER_SLAM_DELAY = 8, OVER_CRASH_DELAY = 20, OVER_PILLARS = 16;

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

    /** 戦術兵姫が叩きつける（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!canWield(user, InteractionHand.MAIN_HAND)) return false;
        if (!WeaponPower.use(user, stack, PER_SLAM)) return false;
        slam(level, user);
        playUse(user, stack);
        user.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 地面を叩いて打ち上げる（兵姫に持たせたときも同じ処理） */
    public static void slam(ServerLevel level, LivingEntity user) {
        if (OverloadArts.active(user)) {
            overCollapse(level, user);
            return;
        }
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

    private static java.util.List<LivingEntity> overTargets(ServerLevel level, LivingEntity user, Vec3 c) {
        return level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(OVER_RADIUS, 6, OVER_RADIUS),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator()
                        && Math.hypot(e.getX() - c.x, e.getZ() - c.z) <= OVER_RADIUS + e.getBbWidth());
    }

    /** オーバーロード: 吸い寄せ → 半径 20 を叩いて打ち上げ → 叩き落とし */
    private static void overCollapse(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position();
        // 1. 特異点: 半径 20 の敵を中心へ吸い寄せる（ダメージなし）
        for (LivingEntity e : overTargets(level, user, c)) {
            Vec3 in = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
            double d = in.length();
            if (d < 1.0E-3) continue;
            in = in.scale(Math.min(1.6, d * 0.12) / d);
            e.setDeltaMovement(e.getDeltaMovement().add(in.x, 0.2, in.z));
            e.hurtMarked = true;
        }
        Vec3 core = c.add(0, 2.5, 0);
        Fx.gather(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.5F, OVER_SLAM_DELAY + 2), core, 70, OVER_RADIUS, OVER_SLAM_DELAY);
        Fx.gather(level, Fx.opt(ModParticles.SPARK.get(), Overload.CRIMSON, 0.4F, OVER_SLAM_DELAY + 2), core, 40, OVER_RADIUS * 0.6, OVER_SLAM_DELAY);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x2A1050, 3.0F, OVER_SLAM_DELAY + 4), core, 1, 0, 0);
        Fx.mandala(level, c.add(0, 0.05, 0), COLOR, (float) OVER_RADIUS * 0.5F, OVER_SLAM_DELAY + 6, false, true);
        OverloadArts.flourish(level, user, (float) OVER_RADIUS);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 2.0F);
        // 2. 半径 20 を叩いて打ち上げる
        WeaponUtil.later(level, OVER_SLAM_DELAY, () -> {
            if (!user.isAlive() || user.level() != level) return;
            Vec3 at = user.position();
            java.util.List<LivingEntity> launched = new java.util.ArrayList<>();
            int shown = 0;
            for (LivingEntity target : overTargets(level, user, at)) {
                target.invulnerableTime = 0;
                target.hurt(WeaponUtil.melee(user), SLAM_DAMAGE);
                Vec3 out = new Vec3(target.getX() - at.x, 0, target.getZ() - at.z);
                out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(0.3);
                target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0, 0.2).add(out.x, 1.6, out.z));
                target.hurtMarked = true;
                launched.add(target);
                if (shown++ < 12) OverloadArts.impact(level, new Vec3(target.getX(), target.getY(0.3), target.getZ()));
            }
            Vec3 ground = at.add(0, 0.05, 0);
            Fx.shockwave(level, ground, 0xFFFFFF, (float) OVER_RADIUS + 2, 12);
            Fx.shockwave(level, ground, COLOR, (float) OVER_RADIUS, 16);
            Fx.shockwave(level, ground.add(0, 0.6, 0), 0x6040C0, (float) OVER_RADIUS * 0.7F, 18);
            Fx.shockwave(level, ground.add(0, 0.3, 0), Overload.CRIMSON, (float) OVER_RADIUS * 0.85F, 20);
            Fx.shockwave(level, ground.add(0, 1.0, 0), Overload.GOLD, (float) OVER_RADIUS * 0.45F, 22);
            BlockState below = level.getBlockState(BlockPos.containing(at).below());
            if (!below.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), at.x, at.y + 0.2, at.z, 120, OVER_RADIUS * 0.5, 0.2, OVER_RADIUS * 0.5, 0.5);
            for (int i = 0; i < OVER_PILLARS; i++) {
                double a = i * Math.PI * 2 / OVER_PILLARS;
                Vec3 p = at.add(Math.cos(a) * OVER_RADIUS, 0.2, Math.sin(a) * OVER_RADIUS);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), i % 2 == 0 ? COLOR : Overload.GOLD, 0.6F, 18), p, p.add(0, 10, 0), 0.8);
            }
            level.playSound(null, user.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5F, 0.4F);
            level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.5F);
            // 3. 打ち上げた敵を重力反転で地面へ叩き落とす
            WeaponUtil.later(level, OVER_CRASH_DELAY, () -> {
                int n = 0;
                for (LivingEntity target : launched) {
                    if (!target.isAlive() || target.level() != level) continue;
                    target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, -2.5, 0));
                    target.hurtMarked = true;
                    if (n++ < 12) {
                        Vec3 p = new Vec3(target.getX(), target.getY(0.5), target.getZ());
                        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.4F, 10), p, p.add(0, 4, 0), 0.5);
                        Fx.shockwave(level, p, Overload.CRIMSON, 2.0F, 8);
                    }
                }
                Fx.shockwave(level, at.add(0, 0.05, 0), COLOR, (float) OVER_RADIUS, 12);
                level.playSound(null, BlockPos.containing(at), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 0.6F);
            });
        });
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
