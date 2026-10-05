package com.automatavalkyries.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 光速剣『ルクシオンセイバー』（§7.2 weapon_luxion_saber、T2）: 片手剣。攻撃力 28.0 / 攻撃速度 3.5、1 振り（当たり）15,000 FE。
 * 無敵時間を無視する（当てた相手の無敵時間を消すので、続けて振ると毎回入る）。
 * 右クリックで、向いた方の 15 ブロック以内（壁の向こうは除く）の敵へ瞬時に空間跳躍して斬り抜け、相手の向こう側に出る（15,000 FE、間隔 0.5 秒【提案】）。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「光速乱舞」に変わる。跳べる距離は 30 ブロック（狙える向きも広く）、
 * 斬り抜けた後も 0.15 秒ごとに 30 ブロック以内の見えている別の敵へ次々と跳び（最初の 1 体を含めて 8 体まで）、金と紅の太い光の軌跡が空間に残る。
 * 軌跡の幅 1.5 の中にいた敵も斬る。最後に持ち主のまわりで巨大な十字の斬撃が弾け、半径 8 の敵を斬る</p>
 */
public class LuxionSaberItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 300_000, PER_HIT = 15_000, BLINK_COOLDOWN = 10;
    public static final double DAMAGE = 90.0, DULL_DAMAGE = 20.0, SPEED = 3.5, BLINK_RANGE = 15.0;
    public static final float WEIGHT = 2.5F;
    /** オーバーロード: 跳べる距離・跳び続ける数・跳ぶ間隔・軌跡の当たりの幅・最後の十字の斬撃の半径 */
    public static final double OVER_RANGE = 30.0, OVER_PATH = 1.5, OVER_FINALE = 8.0;
    public static final int OVER_CHAIN = 8, OVER_STEP = 3;
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.55F), 1.2F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.5F);

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public LuxionSaberItem(Properties properties) {
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
        target.invulnerableTime = 0; // 無敵時間を無視
        if (attacker.level() instanceof ServerLevel level) {
            Vec3 hit = new Vec3(target.getX(), target.getY(0.6), target.getZ());
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.PALE_GOLD, 0.3F, 8), hit, 8, 0.3);
            Fx.slash(level, hit, Fx.GOLD, 1.4F, 5, (float) (level.random.nextFloat() * Math.PI * 2));
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 2.0F);
            playUse(attacker, stack);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_HIT)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        LivingEntity target = findTarget(player);
        if (target == null) return InteractionResultHolder.pass(stack);
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, BLINK_COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_HIT);
            blink(server, player, target, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 戦術兵姫が跳ぶ（{@link ValkyrieArts}）。向いた先に跳べる相手がいなければ false */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @Nullable LivingEntity target) {
        if (!WeaponPower.has(user, stack, PER_HIT)) return false;
        LivingEntity found = findTarget(user);
        if (found == null) return false;
        WeaponPower.use(user, stack, PER_HIT);
        blink(level, user, found, stack);
        user.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 向いた方（前方 30 度）の 15 ブロック以内で、見えている一番近い敵（オーバーロードは前方 45 度・30 ブロック） */
    @Nullable
    private static LivingEntity findTarget(LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        double range = OverloadArts.pick(user, BLINK_RANGE, OVER_RANGE), cone = OverloadArts.pick(user, 0.86, 0.7);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(range),
                e -> e != user && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double d = to.length();
            if (d > range + e.getBbWidth() || to.normalize().dot(look) < cone) continue;
            if (!user.hasLineOfSight(e)) continue; // 壁の向こうへは跳ばない
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    /** 相手の向こう側へ跳び、すれ違いざまに斬る。兵姫に持たせたときも同じ処理（オーバーロードは光速乱舞） */
    public static void blink(ServerLevel level, LivingEntity user, LivingEntity target, ItemStack stack) {
        boolean over = OverloadArts.active(user);
        strike(level, user, target, stack, over);
        if (!over) return;
        OverloadArts.flourish(level, user, 6.0F);
        List<LivingEntity> done = new ArrayList<>();
        done.add(target);
        chain(level, user, stack, done);
    }

    /** 光速乱舞の続き: 0.15 秒ごとに次の敵へ跳ぶ。跳ぶ先が無いか 8 体斬ったら十字の斬撃で締める */
    private static void chain(ServerLevel level, LivingEntity user, ItemStack stack, List<LivingEntity> done) {
        WeaponUtil.later(level, OVER_STEP, () -> {
            if (!user.isAlive() || user.level() != level) return;
            LivingEntity next = null;
            if (done.size() < OVER_CHAIN) {
                double best = Double.MAX_VALUE;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_RANGE),
                        e -> e != user && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user) && !done.contains(e)
                                && e.distanceTo(user) <= OVER_RANGE + e.getBbWidth())) {
                    double d = e.distanceToSqr(user);
                    if (d < best && user.hasLineOfSight(e)) { best = d; next = e; }
                }
            }
            if (next == null) {
                finale(level, user);
                return;
            }
            done.add(next);
            strike(level, user, next, stack, true);
            chain(level, user, stack, done);
        });
    }

    /** 光速乱舞の締め: 持ち主のまわりで巨大な十字の斬撃が弾け、半径 8 の敵を斬る */
    private static void finale(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.55, 0);
        DamageSource source = WeaponUtil.melee(user);
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_FINALE),
                e -> e != user && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user) && e.distanceTo(user) <= OVER_FINALE + e.getBbWidth())) {
            e.invulnerableTime = 0;
            e.hurt(source, (float) DAMAGE);
            if (n++ < 12) OverloadArts.impact(level, new Vec3(e.getX(), e.getY(0.55), e.getZ()));
        }
        float yaw = (float) Math.toRadians(-user.getYRot());
        Fx.slash(level, c, Fx.WHITE, 7.0F, 12, yaw + 0.785F);
        Fx.slash(level, c, Fx.GOLD, 8.0F, 14, yaw - 0.785F);
        Fx.slash(level, c, com.automatavalkyries.weapon.Overload.CRIMSON, 6.0F, 12, yaw + 2.356F);
        Fx.slash(level, c, com.automatavalkyries.weapon.Overload.GOLD, 6.0F, 12, yaw - 2.356F);
        Fx.shockwave(level, user.position().add(0, 0.05, 0), Fx.GOLD, (float) OVER_FINALE + 0.5F, 14);
        Fx.shockwave(level, user.position().add(0, 0.08, 0), com.automatavalkyries.weapon.Overload.CRIMSON, (float) OVER_FINALE * 0.6F, 18);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.STAR.get(), Fx.PALE_GOLD, 0.45F, 16), c, 20, 0.7);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 3.0F, 6), c, 1, 0, 0);
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4F, 0.8F);
    }

    /** 1 回の跳躍と斬り抜け（over なら太い光の軌跡と、軌跡の中の敵も斬る） */
    private static void strike(ServerLevel level, LivingEntity user, LivingEntity target, ItemStack stack, boolean over) {
        Vec3 from = user.position();
        Vec3 dir = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z).normalize();
        double past = target.getBbWidth() * 0.5 + user.getBbWidth() * 0.5 + 1.0;
        Vec3 to = new Vec3(target.getX(), target.getY(), target.getZ()).add(dir.scale(past));
        // 抜けた先が埋まるなら、相手の手前に出る
        if (!level.noCollision(user, user.getBoundingBox().move(to.subtract(from)))) to = target.position().subtract(dir.scale(past));
        if (!level.noCollision(user, user.getBoundingBox().move(to.subtract(from)))) to = from;
        // 光の軌跡
        Vec3 mid = new Vec3(0, user.getBbHeight() * 0.55, 0);
        double length = to.distanceTo(from);
        // 光の軌跡: 白い芯と金の帯、こぼれるきらめき、出発と到着の衝撃波
        if (length > 0.1) {
            Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 0.35F, 14), from.add(mid), to.add(mid), 0.3);
            Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.GOLD, 0.6F, 18), from.add(mid), to.add(mid), 0.45);
            for (double d = 0; d <= length; d += 0.7) {
                Vec3 p = from.add(to.subtract(from).normalize().scale(d)).add(mid);
                Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.PALE_GOLD, 0.2F, 12), p, 1, 0.08);
            }
            if (over) { // 太い金と紅の軌跡と、軌跡の中（幅 1.5）の敵も斬る
                Vec3 a = from.add(mid), b = to.add(mid);
                Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), com.automatavalkyries.weapon.Overload.CRIMSON, 1.3F, 24), a, b, 0.8);
                Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), com.automatavalkyries.weapon.Overload.GOLD, 0.9F, 28), a, b, 0.6);
                DamageSource source = WeaponUtil.melee(user);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(a, b).inflate(OVER_PATH + 1),
                        e -> e != user && e != target && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user)
                                && e.getBoundingBox().inflate(OVER_PATH).clip(a, b).isPresent())) {
                    e.invulnerableTime = 0;
                    e.hurt(source, (float) DAMAGE);
                    Fx.slash(level, new Vec3(e.getX(), e.getY(0.55), e.getZ()), Fx.GOLD, 1.8F, 6, level.random.nextFloat() * 6.28F);
                }
            }
        }
        Fx.shockwave(level, from.add(0, 0.05, 0), Fx.GOLD, 2.2F, 10);
        Fx.shockwave(level, to.add(0, 0.05, 0), Fx.PALE_GOLD, 3.0F, 12);
        user.teleportTo(to.x, to.y, to.z);
        target.invulnerableTime = 0;
        DamageSource source = user instanceof Player player ? user.damageSources().playerAttack(player) : user.damageSources().mobAttack(user);
        target.hurt(source, (float) DAMAGE);
        Vec3 hit = new Vec3(target.getX(), target.getY(0.55), target.getZ());
        Fx.slash(level, hit, Fx.WHITE, over ? 3.5F : 2.2F, 7, (float) Math.atan2(dir.z, dir.x));
        Fx.slash(level, hit, Fx.GOLD, over ? 4.5F : 2.8F, 8, (float) (Math.atan2(dir.z, dir.x) + 0.5));
        if (over) {
            Fx.slash(level, hit, com.automatavalkyries.weapon.Overload.CRIMSON, 4.0F, 9, (float) (Math.atan2(dir.z, dir.x) - 0.6));
            OverloadArts.impact(level, hit);
        }
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.WHITE, 0.35F, 10), hit, 14, 0.4);
        level.playSound(null, BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.8F);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.6F);
        if (stack.getItem() instanceof LuxionSaberItem saber) saber.playUse(user, stack);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_HIT)};
    }
}
