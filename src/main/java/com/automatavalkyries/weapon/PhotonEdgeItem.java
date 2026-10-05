package com.automatavalkyries.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * フォトンエッジ（§7.2 weapon_photon_edge、T1）: 片手剣。攻撃力 12.0 / 攻撃速度 2.2、1 振り（当たり）2,000 FE。
 * 電力が無いとただの柄（攻撃力 4.0【提案】）。
 * 3 連コンボ: 1.5 秒以内に続けて当てると数え、3 撃目は抜刀のような横薙ぎの光刃が目の前（左右 75 度・4 ブロック）の敵に半分のダメージ【提案】。
 * 敵のフォトンシールドゲージを 2.5 倍で削る特性は、シールドを持つ敵を実装したらつなぐ。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 光輪の大旋斬。3 撃目の横薙ぎが持ち主を中心にした一回転（360 度）の光の旋斬になり、
 * 半径 16 の敵すべてに半分のダメージ。白と水色の光の輪が半径 6・11・16 の 3 重に走り、外周に 12 枚の大きな三日月の斬撃（紅と金を交えて）が並ぶ</p>
 */
public class PhotonEdgeItem extends TacticalWeaponItem {

    public static final int CAPACITY = 40_000, PER_HIT = 2_000, COMBO_WINDOW = 30;
    public static final double DAMAGE = 36.0, DULL_DAMAGE = 12.0, SPEED = 2.2, SWEEP_RANGE = 4.0;
    public static final float WEIGHT = 4.0F;
    /** オーバーロード: 旋斬の半径・外周の三日月の数 */
    public static final double OVER_SWEEP_RANGE = 16.0;
    public static final int OVER_CRESCENTS = 12;

    private static final DustParticleOptions SLASH_CORE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.5F);
    private static final DustParticleOptions SLASH_EDGE = new DustParticleOptions(new Vector3f(0.45F, 0.88F, 1.0F), 1.1F);

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
        Vec3 hit = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.CYAN, 0.25F, 8), hit, 8, 0.25);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.ICE, 0.5F, 10), hit, 3, 0.2, 0.01);
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

    /**
     * 3 撃目: 抜刀のような横薙ぎの光刃。目の前（左右 75 度・4 ブロック）の敵に半分のダメージ。
     * 胸の高さに、白い芯と水色の縁の弧の斬撃を描き、刃の通り道に火花と薙ぎ払いの跡を残す。
     */
    private static void sweep(ServerLevel level, ItemStack stack, LivingEntity target, LivingEntity attacker) {
        DamageSource source = attacker instanceof Player player ? attacker.damageSources().playerAttack(player) : attacker.damageSources().mobAttack(attacker);
        if (OverloadArts.active(attacker)) {
            overSweep(level, target, attacker, source);
            return;
        }
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = new Vec3(attacker.getLookAngle().x, 0, attacker.getLookAngle().z).normalize();
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(SWEEP_RANGE),
                e -> e != attacker && e != target && e.isAlive() && !e.isAlliedTo(attacker))) {
            Vec3 to = new Vec3(other.getX() - attacker.getX(), 0, other.getZ() - attacker.getZ());
            if (to.length() > SWEEP_RANGE + other.getBbWidth() || to.normalize().dot(look) < 0.26) continue;
            other.hurt(source, (float) (DAMAGE / 2));
        }
        // 弧の斬撃（右から左へ、わずかに傾いた横一文字）: 白い芯と水色の縁の光の帯、大きな三日月、刃の通り道のきらめき
        double yaw = Math.atan2(look.z, look.x);
        for (int deg = -80; deg <= 80; deg += 3) {
            double ang = yaw + Math.toRadians(deg);
            double tilt = deg / 80.0 * 0.25;
            double y = eye.y - 0.45 + tilt;
            Vec3 core = new Vec3(attacker.getX() + Math.cos(ang) * 2.6, y, attacker.getZ() + Math.sin(ang) * 2.6);
            Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 0.32F, 9), core, 1, 0, 0);
            for (double rad : new double[]{2.0, 3.2}) {
                Vec3 edge = new Vec3(attacker.getX() + Math.cos(ang) * rad, y, attacker.getZ() + Math.sin(ang) * rad);
                Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.CYAN, 0.22F, 7), edge, 1, 0, 0);
            }
            if (deg % 12 == 0) Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.ICE, 0.22F, 10), core, 2, 0.12);
        }
        Vec3 front = new Vec3(attacker.getX() + look.x * 2.4, eye.y - 0.4, attacker.getZ() + look.z * 2.4);
        Fx.slash(level, front, Fx.ICE, 3.0F, 8, 0F);
        Fx.slash(level, front.add(0, 0.05, 0), Fx.WHITE, 2.4F, 6, 0F);
        for (int deg : new int[]{-50, 0, 50}) {
            double ang = yaw + Math.toRadians(deg);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, attacker.getX() + Math.cos(ang) * 2.4, eye.y - 0.5,
                    attacker.getZ() + Math.sin(ang) * 2.4, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, attacker.getX() + look.x * 2.6, eye.y - 0.45, attacker.getZ() + look.z * 2.6, 14, 1.2, 0.1, 1.2, 0.05);
        level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, attacker.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.8F);
    }

    /** オーバーロードの 3 撃目: 一回転の光の旋斬。半径 16 の敵すべてに半分のダメージ */
    private static void overSweep(ServerLevel level, LivingEntity target, LivingEntity attacker, DamageSource source) {
        Vec3 eye = attacker.getEyePosition();
        int shown = 0;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(OVER_SWEEP_RANGE, 4, OVER_SWEEP_RANGE),
                e -> e != attacker && e != target && e.isAlive() && !e.isAlliedTo(attacker) && !e.isSpectator())) {
            Vec3 to = new Vec3(other.getX() - attacker.getX(), 0, other.getZ() - attacker.getZ());
            if (to.length() > OVER_SWEEP_RANGE + other.getBbWidth()) continue;
            other.invulnerableTime = 0;
            other.hurt(source, (float) (DAMAGE / 2));
            Vec3 at = new Vec3(other.getX(), other.getY(0.6), other.getZ());
            if (shown++ < 12) {
                OverloadArts.impact(level, at);
                Fx.slash(level, at, Fx.ICE, 1.2F, 5, level.random.nextFloat() * 6.28F);
            }
        }
        double y = eye.y - 0.45;
        Vec3 c = new Vec3(attacker.getX(), y, attacker.getZ());
        double spin = level.getGameTime() * 0.1;
        // 3 重の光の輪（半径 6・11・16）
        double[] radii = {OVER_SWEEP_RANGE * 0.375, OVER_SWEEP_RANGE * 0.69, OVER_SWEEP_RANGE};
        for (int ring = 0; ring < radii.length; ring++) {
            int steps = 36 + ring * 12;
            for (int i = 0; i < steps; i++) {
                double ang = spin + i * Math.PI * 2 / steps;
                Vec3 p = c.add(Math.cos(ang) * radii[ring], Math.sin(ang * 2 + ring) * 0.15, Math.sin(ang) * radii[ring]);
                Fx.one(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), ring == 1 ? Fx.WHITE : Fx.CYAN, 0.35F + ring * 0.05F, 10),
                        p, new Vec3(-Math.sin(ang), 0, Math.cos(ang)).scale(0.15));
            }
        }
        // 外周の三日月の斬撃（紅と金を交えて）
        for (int k = 0; k < OVER_CRESCENTS; k++) {
            double ang = spin + k * Math.PI * 2 / OVER_CRESCENTS;
            Vec3 p = c.add(Math.cos(ang) * OVER_SWEEP_RANGE * 0.85, 0, Math.sin(ang) * OVER_SWEEP_RANGE * 0.85);
            int color = k % 3 == 0 ? Overload.CRIMSON : k % 3 == 1 ? Fx.ICE : Overload.GOLD;
            Fx.slash(level, p, color, 3.0F, 9, (float) (ang + Math.PI / 2));
        }
        Fx.shockwave(level, attacker.position().add(0, 0.05, 0), Fx.CYAN, (float) OVER_SWEEP_RANGE, 12);
        Fx.shockwave(level, attacker.position().add(0, 0.1, 0), Fx.WHITE, (float) OVER_SWEEP_RANGE * 0.6F, 10);
        Fx.beamRing(level, c, new Vec3(0, 1, 0), Fx.ICE, (float) OVER_SWEEP_RANGE, 10);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, OVER_SWEEP_RANGE * 0.5, 0.3, OVER_SWEEP_RANGE * 0.5, 0.05);
        OverloadArts.flourish(level, attacker, (float) OVER_SWEEP_RANGE);
        level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, attacker.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.2F);
        level.playSound(null, attacker.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 0.8F, 1.6F);
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
