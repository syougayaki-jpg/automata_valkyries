package com.automatavalkyries.weapon;

import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.TwilightPacket;
import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

/**
 * 終の黄昏『ラグナロク』（T4、追加の兵装 第 2 弾。2026-10-03 こちらが考え、同日ユーザー指定で名前・射程・向き・ダメージ・演出を変えた）:
 * 両手の大剣。攻撃力 120 / 攻撃速度 1.0。右クリックで 3 秒溜める（刀身に橙の光が集まる）。離すと画面の中心へ思いきり振り下ろし（一人称は {@code WeaponClient.slamTransform}）、
 * 刃が落ちきった瞬間（0.2 秒後）に黄昏の斬撃が向いた方向（上下も）へ飛ぶ
 * （128 ブロック・幅 12・厚さ 10、0.8 秒で端まで）。触れた敵にカンストダメージ 2,147,483,647（種類 {@code ragnarok} は防具・エンチャント・状態異常・耐性を通らない、
 * 無敵時間を消す）。放った瞬間、まわり 96 ブロックのプレイヤーの画面と空が黄昏に染まる（{@link TwilightPacket}）。1 振り 3,000,000 FE。
 */
public class RagnarokEdgeItem extends ChargedWeaponItem {

    public static final int CAPACITY = 15_000_000, PER_CLEAVE = 3_000_000, CHARGE = 60, STEPS = 16, TWILIGHT_TICKS = 100;
    public static final double DAMAGE = 120.0, DULL_DAMAGE = 30.0, SPEED = 1.0, RANGE = 128.0, HALF_WIDTH = 6.0, HALF_THICK = 5.0, TWILIGHT_RANGE = 96.0;
    public static final long DAMAGE_VALUE = Integer.MAX_VALUE;
    public static final float CLEAVE_DAMAGE = (float) DAMAGE_VALUE, WEIGHT = 18.0F;
    /** 黄昏の色: 金・橙・茜・紫 */
    public static final int GOLD = 0xFFD070, ORANGE = 0xFF8A30, CRIMSON = 0xE0405A, VIOLET = 0x8A3AC0, CORE = 0xFFF0D0;
    static final int[] TWILIGHT = {GOLD, ORANGE, ORANGE, CRIMSON, VIOLET};

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public RagnarokEdgeItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, CHARGE, PER_CLEAVE);
    }

    private static Multimap<Attribute, AttributeModifier> modifiers(double damage) {
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage - 1.0, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= 10_000 ? charged : dull;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            WeaponPower.use(attacker, stack, 10_000);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), ORANGE, 0.35F, 8), new Vec3(target.getX(), target.getY(0.5), target.getZ()), 8, 0.35);
        }
        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide() && selected && entity instanceof LivingEntity user && level.getGameTime() % 20 == 0) blessHolder(user);
    }

    /** 手に持っている間の加護（2026-10-04 ユーザー指定。ルミナス・レクイエムと同じく 1 秒ごとに掛け直す）。黄昏の覇気: 力 IV・耐性 III・火炎耐性・採掘速度上昇 II。兵姫に持たせたときも同じ処理（暗視は切れかけの点滅が出ないよう長めに掛ける） */
    public static void blessHolder(LivingEntity user) {
        Overload.bless(user, net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 60, 3);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 60, 2);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 60, 0);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.DIG_SPEED, 60, 1);
        Overload.blessExtras(user); // オーバーロードなら耐性 IV・再生 VI
    }

    /** 通常攻撃の間隔（Tick） */
    @Override
    public int cooldownTicks() {
        return 12;
    }

    /** 通常攻撃（左クリック。空振りでも。0.6 秒に 1 回・80,000 FE）: 赤黒い横薙ぎの斬撃がまわりに起きる（{@link T4NormalAttacks}） */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level && T4NormalAttacks.ready(entity, stack, cooldownTicks(), 80_000)) {
            T4NormalAttacks.twilightSweep(level, entity);
        }
        return false;
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return Fx.opt(ModParticles.GLOW.get(), ORANGE, 0.3F, 10);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        super.onUseTick(level, user, stack, remaining);
        if (level instanceof ServerLevel server && user.isUsingItem()) {
            int t = elapsed(stack, remaining);
            int ct = chargeTicks(stack);
            if (t <= ct) gather(server, user, t / (float) ct, t);
        }
    }

    /** 刀身のおおよその根元と先（持っている手の側、斜め上へ） */
    static Vec3[] bladeLine(LivingEntity user) {
        Vec3 look = user.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 base = user.getEyePosition().add(look.scale(0.6)).add(right.scale(0.45)).add(up.scale(-0.5));
        Vec3 tip = base.add(up.scale(1.6)).add(look.scale(0.6)).add(right.scale(-0.2));
        return new Vec3[]{base, tip};
    }

    /** 溜め: 橙と金の光が刀身へ集まり、足元に黄昏の陣、火の粉が螺旋に昇る（f = 0〜1） */
    static void gather(ServerLevel level, LivingEntity user, float f, int t) {
        RandomSource r = level.random;
        Vec3[] blade = bladeLine(user);
        int count = 2 + (int) (f * 6);
        for (int i = 0; i < count; i++) {
            Vec3 onBlade = blade[0].add(blade[1].subtract(blade[0]).scale(r.nextDouble()));
            Vec3 from = onBlade.add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(2.0 + r.nextDouble() * 1.5));
            int color = r.nextInt(4) == 0 ? GOLD : ORANGE;
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.18F + 0.15F * f, 8), from, onBlade.subtract(from).scale(1 / 8.0));
        }
        // 刀身そのものが光りだす（集まった光が刃に沿って灯る）
        for (int i = 0; i < 1 + (int) (f * 3); i++) {
            Vec3 p = blade[0].add(blade[1].subtract(blade[0]).scale(r.nextDouble() * f));
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), i % 2 == 0 ? ORANGE : CORE, 0.12F + 0.1F * f, 4), p, Vec3.ZERO);
        }
        // 火の粉の螺旋（3 本、溜まるほど速く・高く）
        Vec3 feet = user.position();
        for (int arm = 0; arm < 3; arm++) {
            double a = t * (0.2 + 0.25 * f) + arm * Math.PI * 2 / 3;
            Vec3 p = feet.add(Math.cos(a) * 1.8, 0.1 + (t % 20) * 0.12 * (1 + f), Math.sin(a) * 1.8);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), TWILIGHT[arm + 1], 0.2F + 0.1F * f, 14), p, new Vec3(0, 0.05 + 0.08 * f, 0));
        }
        if (t % 10 == 0) {
            Fx.shockwave(level, feet.add(0, 0.05, 0), ORANGE, 3.0F + 3.0F * f, 14);
            Fx.shockwave(level, feet.add(0, 0.08, 0), VIOLET, 2.0F + 2.0F * f, 18);
        }
        if (f >= 1F) {
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), GOLD, 0.45F, 12), blade[1], 24, 0.4);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 1.4F, 8), blade[1], 1, 0, 0);
            level.playSound(null, user.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 0.6F);
        }
    }

    /** 振り下ろしで刃が画面の中心へ落ちきる時刻（Tick）。斬撃はこの瞬間に放つ */
    public static final int IMPACT_DELAY = 4;

    @Override
    protected void fire(ServerLevel level, LivingEntity user, ItemStack stack) {
        user.swing(user.getUsedItemHand(), true); // 三人称でも腕を振り下ろす
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.5F);
        WeaponUtil.later(level, IMPACT_DELAY, () -> {
            if (user.isAlive()) cleave(level, user);
        });
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        boolean full = elapsed(stack, remaining) >= chargeTicks(stack);
        super.releaseUsing(stack, level, user, remaining);
        if (full && level.isClientSide()) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.automatavalkyries.client.WeaponClient.slam(this));
        }
    }

    /** 黄昏の斬撃（兵姫に持たせたときも同じ処理）。向いた方向（上下も）へ 0.8 秒かけて飛ぶ */
    public static void cleave(ServerLevel level, LivingEntity user) {
        Vec3 n = user.getLookAngle().normalize();
        Vec3 origin = user.getEyePosition().subtract(0, 0.4, 0);
        Set<LivingEntity> hit = new HashSet<>();
        double k = Overload.range(user); // オーバーロードの T4 は範囲が広い
        twilight(level, user);
        level.playSound(null, user.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5F, 0.5F);
        for (int s = 0; s < STEPS; s++) {
            int step = s;
            WeaponUtil.later(level, s, () -> segment(level, user, origin, n, step, hit, k));
        }
    }

    /** 放つ瞬間: まわりのプレイヤーの画面を黄昏に染め、立ち昇る光の柱と黄昏の色の輪を重ねる */
    static void twilight(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(c) <= TWILIGHT_RANGE * TWILIGHT_RANGE) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TwilightPacket(TWILIGHT_TICKS));
            }
        }
        for (int i = 0; i < TWILIGHT.length; i++) {
            Fx.shockwave(level, c.add(0, 0.05 + i * 0.08, 0), TWILIGHT[i], 8.0F + i * 3.0F, 14 + i * 4);
        }
        // 黄昏の柱: 足元から空へ、芯は白金、外は橙から紫へ
        for (double h = 0; h < 24; h += 0.6) {
            int color = TWILIGHT[Math.min(TWILIGHT.length - 1, (int) (h / 5))];
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.9F - (float) h * 0.02F, 16), c.add(0, h, 0), new Vec3(0, 0.05, 0));
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.35F, 10), c.add(0, h, 0), Vec3.ZERO);
        }
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 3.0F, 8), c.add(0, 1, 0), 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), GOLD, 0.45F, 14), c.add(0, 1, 0), 40, 0.7);
    }

    /** 斬撃の 1 区間（RANGE / STEPS ブロック）。刃の面は「横」と「進む向き」に広がり、厚さは「上」の向き */
    static void segment(ServerLevel level, LivingEntity user, Vec3 origin, Vec3 n, int step, Set<LivingEntity> hit, double k) {
        double len = RANGE * k / STEPS, from = step * len, to = from + len;
        double halfWidth = HALF_WIDTH * k, halfThick = HALF_THICK * k;
        Vec3 side = n.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(n).normalize();
        Vec3 a = origin.add(n.scale(from)), b = origin.add(n.scale(to));
        AABB box = new AABB(a, b).inflate(halfWidth + 2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator() && !hit.contains(e))) {
            Vec3 rel = new Vec3(e.getX(), e.getY(0.5), e.getZ()).subtract(origin);
            double along = rel.dot(n);
            if (along < from - 1 || along > to + 1) continue;
            if (Math.abs(rel.dot(side)) > halfWidth + e.getBbWidth() * 0.5 || Math.abs(rel.dot(up)) > halfThick + e.getBbHeight() * 0.5) continue;
            hit.add(e);
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.RAGNAROK, user, user), CLEAVE_DAMAGE);
            Overload.overkill(e, user);
            Vec3 at = new Vec3(e.getX(), e.getY(0.6), e.getZ());
            Fx.number(level, at.add(0, e.getBbHeight() * 0.5 + 0.5, 0), DAMAGE_VALUE, ORANGE);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), GOLD, 0.45F, 10), at, 14, 0.5);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 1.2F, 5), at, 1, 0, 0);
        }
        // 黄昏の刃: 横に広がる光の帯（芯は白金、縁ほど橙→茜→紫）、飛んだ跡に火の粉、地面に当たる所は大地が割れる
        RandomSource r = level.random;
        for (double d = from; d < to; d += k) {
            Vec3 p = origin.add(n.scale(d));
            for (double w = -halfWidth; w <= halfWidth; w += 1.5 * k) {
                double edge = Math.abs(w) / halfWidth;
                int color = edge < 0.2 ? CORE : edge < 0.45 ? GOLD : edge < 0.7 ? ORANGE : edge < 0.9 ? CRIMSON : VIOLET;
                Vec3 q = p.add(side.scale(w)).add(up.scale((1 - edge * edge) * 0.8)); // 三日月のように中央が前へ反る
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.45F - (float) edge * 0.15F, 8 + (int) (edge * 8)), q, n.scale(0.4));
            }
            if (r.nextInt(2) == 0) {
                Vec3 ember = p.add(side.scale((r.nextDouble() - 0.5) * halfWidth * 2)).add(up.scale((r.nextDouble() - 0.5) * 2));
                Fx.one(level, Fx.opt(ModParticles.SPARK.get(), r.nextBoolean() ? ORANGE : GOLD, 0.25F, 20), ember, new Vec3(0, -0.04, 0));
            }
            BlockPos below = BlockPos.containing(p);
            if (!level.isLoaded(below)) continue; // 読み込まれていない区画は見ない（見ると区画を読み込み・生成してしまい、重くなる）
            for (int depth = 0; depth < 3; depth++, below = below.below()) {
                BlockState ground = level.getBlockState(below);
                if (!ground.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.x, below.getY() + 1.1, p.z, 8, 1.2, 0.1, 1.2, 0.45);
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), CRIMSON, 0.6F, 40), new Vec3(p.x, below.getY() + 1.05, p.z), Vec3.ZERO);
                    break;
                }
            }
        }
        Vec3 mid = origin.add(n.scale((from + to) / 2));
        float roll = (float) Math.atan2(side.y, Math.sqrt(side.x * side.x + side.z * side.z));
        Fx.slash(level, mid, ORANGE, 7.0F * (float) k, 7, roll);
        Fx.slash(level, mid.subtract(n.scale(1.5)), VIOLET, 6.0F * (float) k, 9, roll);
        if (step % 3 == 0) level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9F, 0.6F + step * 0.04F);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_CLEAVE), String.format("%.1f", CHARGE / 20.0)};
    }
}
