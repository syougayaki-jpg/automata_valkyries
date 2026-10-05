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
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 刈り取りは「虚無の大渦」になる。半径 6 から半径 18 の敵を強く引き寄せ、
 * 半径 6・12・18 の 3 重の斬撃の環（内から 12・18・24 の計 54 枚）で斬って吸収する。足元に黒い渦（渦巻いて吸い込まれる空間の破片）が開き、
 * 0.6 秒後に渦が潰れて「事象の地平」が弾け、半径 8 の敵をもう一度斬って吸収する（紫と紅の内向きの衝撃波）</p>
 */
public class VoidScytheItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 3_000_000, PER_HIT = 30_000, PER_REAP = 150_000, COOLDOWN = 80;
    public static final double DAMAGE = 350.0, DULL_DAMAGE = 70.0, SPEED = 0.9, REAP_RADIUS = 6.0;
    public static final float REAP_DAMAGE = 250.0F, LIFESTEAL = 0.2F, WEIGHT = 14.0F;
    public static final int VOID = 0x6A2AB0, DEEP = 0x1A0828, EDGE = 0xD8A8FF;
    /** オーバーロード: 大渦の半径・渦が潰れるまでの Tick・潰れたときの半径 */
    public static final double OVER_RADIUS = 18.0, OVER_COLLAPSE_RADIUS = 8.0;
    public static final int OVER_COLLAPSE_DELAY = 12;

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
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_REAP);
            reap(server, player);
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 戦術兵姫が刈り取る（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!canWield(user, InteractionHand.MAIN_HAND)) return false;
        if (!WeaponPower.use(user, stack, PER_REAP)) return false;
        reap(level, user);
        playUse(user, stack);
        user.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 刈り取り（兵姫に持たせたときも同じ処理） */
    public static void reap(ServerLevel level, LivingEntity user) {
        if (OverloadArts.active(user)) {
            maelstrom(level, user);
            return;
        }
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

    /** オーバーロードの大渦: 半径 18 の敵を引き寄せて 3 重の斬撃の環で斬り、0.6 秒後に渦が潰れて半径 8 をもう一度斬る */
    private static void maelstrom(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        float healed = 0;
        int shown = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_RADIUS, 6, OVER_RADIUS),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && e.distanceTo(user) <= OVER_RADIUS + e.getBbWidth())) {
            Vec3 to = c.subtract(target.position());
            double d = to.length();
            Vec3 pull = d < 1.0E-4 ? Vec3.ZERO : to.scale(Math.min(2.4, 0.5 + d * 0.12) / d);
            target.setDeltaMovement(pull.x, 0.3, pull.z);
            target.hurtMarked = true;
            target.invulnerableTime = 0;
            target.hurt(WeaponUtil.melee(user), REAP_DAMAGE);
            healed += REAP_DAMAGE * LIFESTEAL;
            if (shown++ < 10) {
                drainFx(level, user, target);
                OverloadArts.impact(level, new Vec3(target.getX(), target.getY(0.6), target.getZ()));
            }
        }
        if (healed > 0) user.heal(healed);
        // 3 重の斬撃の環（半径 6・12・18、逆向きに回る）
        double[] rings = {6.0, 12.0, 18.0};
        for (int ring = 0; ring < rings.length; ring++) {
            int blades = 12 + ring * 6;
            for (int i = 0; i < blades; i++) {
                double a = i * Math.PI * 2 / blades + ring * 0.3;
                Vec3 p = c.add(Math.cos(a) * rings[ring], ring * 0.4, Math.sin(a) * rings[ring]);
                float roll = (float) (a + (ring % 2 == 0 ? 1 : -1) * Math.PI / 2);
                Fx.slash(level, p, (i + ring) % 3 == 0 ? Overload.CRIMSON : i % 2 == 0 ? EDGE : VOID, 2.0F + ring * 0.5F, 9, roll);
            }
            Fx.shockwave(level, user.position().add(0, 0.05 + ring * 0.05, 0), ring == 1 ? Overload.CRIMSON : VOID, (float) rings[ring] + 0.5F, 14 + ring * 3);
        }
        // 黒い渦: 外から渦巻いて吸い込まれる空間の破片と、中心の黒い球
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48, r = OVER_RADIUS * (0.4 + (i % 4) * 0.2);
            Vec3 from = c.add(Math.cos(a) * r, (i % 3) * 0.6 - 0.4, Math.sin(a) * r);
            Vec3 swirl = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(r * 0.06);
            Fx.one(level, Fx.opt(ModParticles.VOID_SHARD.get(), i % 2 == 0 ? VOID : DEEP, 0.35F, 16), from, c.subtract(from).scale(1.0 / 16).add(swirl));
        }
        Fx.mandala(level, user.position().add(0, 0.04, 0), VOID, 7.0F, 30, false, true);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), DEEP, 5.0F, 14), c, 1, 0, 0);
        OverloadArts.flourish(level, user, (float) OVER_RADIUS);
        level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.8F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 0.7F);
        WeaponUtil.later(level, OVER_COLLAPSE_DELAY, () -> horizon(level, user));
    }

    /** 渦が潰れる「事象の地平」: 半径 8 の敵をもう一度斬って吸収する */
    private static void horizon(ServerLevel level, LivingEntity user) {
        if (!user.isAlive() || user.level() != level) return;
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        float healed = 0;
        int shown = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_COLLAPSE_RADIUS, 4, OVER_COLLAPSE_RADIUS),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && e.distanceTo(user) <= OVER_COLLAPSE_RADIUS + e.getBbWidth())) {
            target.invulnerableTime = 0;
            target.hurt(WeaponUtil.melee(user), REAP_DAMAGE);
            healed += REAP_DAMAGE * LIFESTEAL;
            if (shown++ < 10) OverloadArts.impact(level, new Vec3(target.getX(), target.getY(0.6), target.getZ()));
        }
        if (healed > 0) user.heal(healed);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), EDGE, 4.0F, 6), c, 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.VOID_SHARD.get(), VOID, 0.4F, 18), c, 40, 0.9);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.CRIMSON, 0.4F, 14), c, 24, 0.7);
        Fx.shockwave(level, user.position().add(0, 0.05, 0), EDGE, (float) OVER_COLLAPSE_RADIUS, 12);
        Fx.shockwave(level, c, Overload.CRIMSON, (float) OVER_COLLAPSE_RADIUS * 0.7F, 10);
        Fx.beamRing(level, c, new Vec3(0, 1, 0), VOID, (float) OVER_COLLAPSE_RADIUS, 12);
        for (int k = 0; k < 4; k++) Fx.slash(level, c, k % 2 == 0 ? EDGE : Overload.GOLD, 4.5F, 8, (float) (k * Math.PI / 4));
        level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.4F);
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
