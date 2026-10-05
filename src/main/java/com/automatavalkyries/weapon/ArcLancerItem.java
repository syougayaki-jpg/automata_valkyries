package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * アークランサー（T1、追加の兵装。2026-10-03 ユーザーの依頼でこちらが考えた）: 片手の電撃の槍。攻撃力 9 / 攻撃速度 1.4、間合い +1.5 ブロック。
 * 当てると稲妻が近くの敵（6 ブロック以内）へ最大 3 体まで次々に跳び、それぞれ半分（4.5）のダメージ。1 当たり 1,500 FE。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 雷霆の連鎖。稲妻は 16 ブロック以内の敵へ最大 12 体まで、
 * 2 Tick ごとに 1 体ずつ駆け抜けるように跳ぶ。跳んだ先には空（高さ 14）から紅と金の落雷が突き立ち、その敵のまわり半径 3 の敵（6 体まで）にも
 * 半分のダメージが弾ける（青白い放電の輪）。最初の一撃で足元に紅と金の衝撃波</p>
 */
public class ArcLancerItem extends TacticalWeaponItem {

    public static final int CAPACITY = 30_000, PER_HIT = 1_500, CHAINS = 3;
    public static final double DAMAGE = 30.0, DULL_DAMAGE = 10.0, SPEED = 1.4, REACH = 1.5, CHAIN_RANGE = 6.0;
    public static final float WEIGHT = 4.5F;
    public static final int COLOR = 0x7FD8FF;
    /** オーバーロード: 跳ぶ数・跳ぶ距離・跳ぶ間隔（Tick）・落雷の高さ・落雷のまわりの弾ける半径と数 */
    public static final int OVER_CHAINS = 12, OVER_HOP_DELAY = 2, OVER_SPLASH_MAX = 6;
    public static final double OVER_CHAIN_RANGE = 16.0, OVER_BOLT_HEIGHT = 14.0, OVER_SPLASH = 3.0;
    private static final UUID REACH_UUID = UUID.fromString("8a1d2c3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e");

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public ArcLancerItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    private static Multimap<Attribute, AttributeModifier> modifiers(double damage) {
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage - 1.0, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION),
                ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_UUID, "Lance reach", REACH, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= PER_HIT ? charged : dull;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!WeaponPower.use(attacker, stack, PER_HIT) || !(attacker.level() instanceof ServerLevel level)) return true;
        chain(level, attacker, target);
        playUse(attacker, stack);
        return true;
    }

    /** 稲妻を近くの敵へ次々に跳ばす（兵姫に持たせたときも同じ処理） */
    public static void chain(ServerLevel level, LivingEntity attacker, LivingEntity first) {
        Vec3 at = new Vec3(first.getX(), first.getY(0.6), first.getZ());
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), at, 10, 0.35);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.8F, 6), at, 1, 0, 0);
        level.playSound(null, first.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.8F);
        if (OverloadArts.active(attacker)) {
            OverloadArts.flourish(level, attacker, 8.0F);
            List<LivingEntity> struck = new ArrayList<>(List.of(first));
            skyBolt(level, attacker, first, struck);
            WeaponUtil.later(level, OVER_HOP_DELAY, () -> overHop(level, attacker, first, struck, 0));
            return;
        }
        List<LivingEntity> hit = new ArrayList<>(List.of(first));
        LivingEntity from = first;
        for (int i = 0; i < CHAINS; i++) {
            LivingEntity source = from;
            LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(CHAIN_RANGE),
                            e -> e != attacker && !hit.contains(e) && e.isAlive() && !e.isAlliedTo(attacker) && e instanceof net.minecraft.world.entity.monster.Enemy)
                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(source))).orElse(null);
            if (next == null) break;
            hit.add(next);
            WeaponUtil.lightning(level, new Vec3(source.getX(), source.getY(0.6), source.getZ()), new Vec3(next.getX(), next.getY(0.6), next.getZ()), COLOR);
            next.invulnerableTime = 0;
            next.hurt(WeaponDamage.source(attacker, WeaponDamage.ARC, attacker, attacker), (float) (DAMAGE / 2));
            from = next;
        }
    }

    private static boolean chainable(LivingEntity attacker, List<LivingEntity> struck, LivingEntity e) {
        return e != attacker && !struck.contains(e) && e.isAlive() && !e.isAlliedTo(attacker) && e instanceof net.minecraft.world.entity.monster.Enemy;
    }

    /** オーバーロードの 1 跳び: 16 ブロック以内の次の敵へ稲妻を跳ばし、空から落雷を落とす（12 体まで、2 Tick ごと） */
    private static void overHop(ServerLevel level, LivingEntity attacker, LivingEntity source, List<LivingEntity> struck, int i) {
        if (i >= OVER_CHAINS || !attacker.isAlive() || attacker.level() != level) return;
        Vec3 from = new Vec3(source.getX(), source.getY(0.6), source.getZ());
        LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, from).inflate(OVER_CHAIN_RANGE),
                        e -> chainable(attacker, struck, e) && e.distanceToSqr(from) <= OVER_CHAIN_RANGE * OVER_CHAIN_RANGE)
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
        if (next == null) {
            // 跳ぶ先が無くなったら、最後の敵のまわりに大きな放電の輪
            Fx.shockwave(level, from, COLOR, (float) OVER_SPLASH * 2, 12);
            Fx.shockwave(level, from, Overload.GOLD, (float) OVER_SPLASH * 1.4F, 14);
            return;
        }
        struck.add(next);
        Vec3 to = new Vec3(next.getX(), next.getY(0.6), next.getZ());
        WeaponUtil.lightning(level, from, to, COLOR);
        next.invulnerableTime = 0;
        next.hurt(WeaponDamage.source(attacker, WeaponDamage.ARC, attacker, attacker), (float) (DAMAGE / 2));
        skyBolt(level, attacker, next, struck);
        level.playSound(null, next.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7F, 1.2F + i * 0.05F);
        WeaponUtil.later(level, OVER_HOP_DELAY, () -> overHop(level, attacker, next, struck, i + 1));
    }

    /** 空から紅と金の落雷が突き立ち、まわり半径 3 の敵（6 体まで、鎖の当たりとは別）に半分のダメージが弾ける */
    private static void skyBolt(ServerLevel level, LivingEntity attacker, LivingEntity target, List<LivingEntity> struck) {
        Vec3 ground = target.position();
        Vec3 top = ground.add((level.random.nextDouble() - 0.5) * 2, OVER_BOLT_HEIGHT, (level.random.nextDouble() - 0.5) * 2);
        WeaponUtil.lightning(level, top, ground.add(0, target.getBbHeight() * 0.6, 0), Overload.CRIMSON);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.GOLD, 0.45F, 8), top, ground, 0.8);
        Fx.shockwave(level, ground.add(0, 0.05, 0), COLOR, (float) OVER_SPLASH, 10);
        Fx.shockwave(level, ground.add(0, 0.1, 0), Overload.CRIMSON, (float) OVER_SPLASH * 0.7F, 12);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 1.6F, 5), ground.add(0, 1, 0), 1, 0, 0);
        OverloadArts.impact(level, ground.add(0, target.getBbHeight() * 0.6, 0));
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(OVER_SPLASH),
                e -> e != attacker && e != target && !struck.contains(e) && e.isAlive() && !e.isAlliedTo(attacker) && !e.isSpectator()
                        && e.distanceTo(target) <= OVER_SPLASH + e.getBbWidth())) {
            if (n++ >= OVER_SPLASH_MAX) break;
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(attacker, WeaponDamage.ARC, attacker, attacker), (float) (DAMAGE / 2));
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 8), new Vec3(e.getX(), e.getY(0.6), e.getZ()), 4, 0.25);
        }
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
