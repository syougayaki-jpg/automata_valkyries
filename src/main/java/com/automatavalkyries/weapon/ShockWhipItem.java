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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 雷蛇の大薙ぎ。鞭が長さ 28 ブロックの雷の大蛇になり、正面の扇（左右 75 度）を
 * 3 重の光の弧（28・19・10 ブロック）でしなりながら薙ぎ払う。扇の中の敵すべてに同じ電撃・鈍化 II・強い引き寄せ。稲妻は近い 6 体へ手元から伸び、
 * 残りの敵には紅と金の閃き</p>
 */
public class ShockWhipItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 80_000, PER_LASH = 8_000, COOLDOWN = 20;
    public static final double DAMAGE = 18.0, SPEED = 1.6, REACH = 2.0, LASH_RANGE = 8.0, HALF_ANGLE = 25.0;
    public static final float LASH_DAMAGE = 24.0F, WEIGHT = 2.5F;
    public static final int COLOR = 0xFFE060;
    /** オーバーロード: 鞭の長さ・扇の左右の角度・手元から稲妻を描く数 */
    public static final double OVER_RANGE = 28.0, OVER_HALF_ANGLE = 75.0;
    public static final int OVER_BOLTS = 6;
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

    /** 戦術兵姫が鞭を振るう（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!WeaponPower.use(user, stack, PER_LASH)) return false;
        lash(level, user);
        playUse(user, stack);
        user.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 扇を薙ぎ払う（兵姫に持たせたときも同じ処理）。当たった数 */
    public static int lash(ServerLevel level, LivingEntity user) {
        if (OverloadArts.active(user)) return overLash(level, user);
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

    /** 長い稲妻（2 ブロックごとに折れる、粒を間引いた軽い稲妻。遠くの敵へ何本も伸ばすため） */
    private static void longBolt(ServerLevel level, Vec3 a, Vec3 b, int color) {
        Vec3 d = b.subtract(a);
        int segments = Math.max(2, (int) (d.length() / 2));
        Vec3 prev = a;
        for (int i = 1; i <= segments; i++) {
            Vec3 p = a.add(d.scale((double) i / segments));
            if (i < segments) p = p.add(level.random.nextGaussian() * 0.5, level.random.nextGaussian() * 0.5, level.random.nextGaussian() * 0.5);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), color, 0.3F, 7), prev, p, 0.5);
            prev = p;
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), color, 0.3F, 8), b, 4, 0.3);
    }

    /** オーバーロード: 28 ブロック・左右 75 度の雷の大蛇で薙ぎ払う。当たった数 */
    private static int overLash(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        Vec3 hand = eye.add(look.scale(0.8)).add(0, -0.3, 0);
        double cos = Math.cos(Math.toRadians(OVER_HALF_ANGLE));
        List<LivingEntity> targets = new java.util.ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_RANGE),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator()));
        targets.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(user)));
        int hit = 0;
        for (LivingEntity e : targets) {
            Vec3 to = new Vec3(e.getX(), e.getY(0.5), e.getZ()).subtract(eye);
            double dist = to.length();
            if (dist > OVER_RANGE + e.getBbWidth() || dist < 1.0E-3 || to.normalize().dot(look) < cos) continue;
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ARC, user, user), LASH_DAMAGE);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            Vec3 pull = to.normalize().scale(-Math.min(2.4, dist * 0.12));
            e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0.35, pull.z));
            e.hurtMarked = true;
            Vec3 at = new Vec3(e.getX(), e.getY(0.6), e.getZ());
            if (hit < OVER_BOLTS) longBolt(level, hand, at, hit % 2 == 0 ? COLOR : Overload.GOLD);
            else if (hit < 18) OverloadArts.impact(level, at);
            hit++;
        }
        // 雷の大蛇: 扇に沿ってしなる 3 重の光の弧（外から 28・19・10 ブロック）
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        double[] radii = {OVER_RANGE, OVER_RANGE * 0.68, OVER_RANGE * 0.36};
        for (int ring = 0; ring < radii.length; ring++) {
            int steps = 40 - ring * 10;
            for (int i = 0; i <= steps; i++) {
                double a = Math.toRadians(-OVER_HALF_ANGLE + 2 * OVER_HALF_ANGLE * i / (double) steps);
                Vec3 dir = look.scale(Math.cos(a)).add(right.scale(Math.sin(a))).normalize();
                double r = radii[ring] * (0.8 + 0.2 * Math.sin(Math.PI * i / steps));
                double wave = Math.sin(i * 0.9 + ring) * 0.6;
                Vec3 p = eye.add(dir.scale(r)).add(0, -0.3 + wave, 0);
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), ring == 0 ? COLOR : ring == 1 ? Overload.GOLD : Overload.CRIMSON, 0.55F - ring * 0.1F, 8), p, Vec3.ZERO);
                if (i % 2 == 0) Fx.one(level, Fx.opt(ModParticles.SPARK.get(), 0xFFFFFF, 0.3F, 6), p, new Vec3(0, 0.05, 0));
            }
        }
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Fx.slash(level, eye.add(flat.scale(OVER_RANGE * 0.5)).add(0, -0.3, 0), COLOR, 6.0F, 8, 0F);
        Fx.slash(level, eye.add(flat.scale(OVER_RANGE * 0.5)).add(0, -0.2, 0), Overload.GOLD, 4.5F, 7, 0.2F);
        OverloadArts.flourish(level, user, 10.0F);
        level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.6F);
        return hit;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_LASH)};
    }
}
