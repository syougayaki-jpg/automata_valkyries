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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「量子の重ね斬り」。1 振りで三日月の斬撃が 7 発、前方 90 度（左右 45 度）の
 * 扇に重なって飛ぶ（向きは縦・横・斜めを順に入れ替え）。斬撃の射程は 16 から 32 ブロック。手元に量子の格子の陣と紅と金の閃きが開く。
 * 1 発の当たり・威力・電力は通常と同じ（電力は 1 振り分）</p>
 */
public class QuantumBladeItem extends TacticalWeaponItem {

    public static final int CAPACITY = 400_000, PER_SLASH = 20_000, SLASH_INTERVAL = 6;
    public static final double DAMAGE = 200.0, DULL_DAMAGE = 50.0, SPEED = 1.8;
    public static final float WEIGHT = 7.0F;
    /** オーバーロード: 1 振りの斬撃の数・扇の左右の角度（度） */
    public static final int OVER_SLASHES = 7;
    public static final double OVER_SPREAD = 45.0;

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
        if (OverloadArts.active(user)) return overSlash(level, user);
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

    /** オーバーロードの重ね斬り: 前方 90 度の扇に 7 発、射程 32 ブロック。真ん中の 1 発を返す */
    private static QuantumSlashEntity overSlash(ServerLevel level, LivingEntity user) {
        Vec3 look = user.getLookAngle();
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 muzzle = new Vec3(user.getX() + look.x * 0.8, user.getEyeY() - 0.25 + look.y * 0.8, user.getZ() + look.z * 0.8);
        QuantumSlashEntity middle = null;
        for (int k = 0; k < OVER_SLASHES; k++) {
            double yaw = Math.toRadians(-OVER_SPREAD + 2 * OVER_SPREAD * k / (OVER_SLASHES - 1));
            Vec3 dir = look.scale(Math.cos(yaw)).add(side.scale(Math.sin(yaw))).normalize();
            QuantumSlashEntity slash = new QuantumSlashEntity(level, user);
            slash.setPos(muzzle.x, muzzle.y, muzzle.z);
            float roll = (float) Math.toRadians(45 * ((k + level.random.nextInt(4)) % 4) + (level.random.nextFloat() - 0.5F) * 20);
            slash.setRoll(roll);
            slash.overload();
            slash.shoot(dir.x, dir.y, dir.z, QuantumSlashEntity.SPEED, 0);
            level.addFreshEntity(slash);
            Fx.slash(level, muzzle.add(dir.scale(1.2)), k % 2 == 0 ? QuantumSlashEntity.COLOR_CORE : Overload.CRIMSON, 1.6F, 5, roll);
            if (k == OVER_SLASHES / 2) middle = slash;
        }
        Fx.mandala(level, muzzle.add(look.scale(0.6)), QuantumSlashEntity.COLOR_EDGE, 2.5F, 14, true, true);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), QuantumSlashEntity.COLOR_GLOW, 1.4F, 6), muzzle, 1, 0, 0);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), QuantumSlashEntity.COLOR_EDGE, 0.2F, 12), muzzle, 14, 0.35);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Overload.GOLD, 0.25F, 10), muzzle, 10, 0.3);
        // 共通の紅と金の演出は振るたびだと重いので、1 秒に 1 回まで
        if (user.tickCount % 20 < 6) OverloadArts.flourish(level, user, 5.0F);
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 1.2F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.6F, 1.8F);
        return middle;
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
