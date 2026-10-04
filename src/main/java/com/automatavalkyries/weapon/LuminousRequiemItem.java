package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 星葬冠『ルミナス・レクイエム』（T4、追加の兵装 第 3 弾。2026-10-03 こちらが考えた。T4 は裏ボス後の最強クラス）:
 * 両手の星の聖杖。攻撃力 200 / 攻撃速度 1.2。手に持っている間、持ち主に再生 III・耐性 III・力 III・火炎耐性。
 * 右クリックで 2 秒溜めて離すと星の鎮魂歌: 半径 64 の敵（モンスターと、持ち主を狙う mob）の時を 5 秒止め（因果停止）、
 * 1 秒後に天から星の光が一斉に降ってカンストダメージ 2,147,483,647（種類 {@code orbital}、無敵時間を消す）。1 回 8,000,000 FE。
 * 演出（2026-10-04 ユーザーの絵を元に）: 溜めると背に 6 振りの光の剣が巡り足元に星の陣、放つと背と足元に大きな星の陣・星の天蓋・
 * 光の剣から的への光の筋・星の柱（{@link PunishmentBeamEntity#SCHEME_STELLA}）、まわりのプレイヤーの画面が星空に染まる。
 */
public class LuminousRequiemItem extends ChargedWeaponItem {

    public static final int CAPACITY = 40_000_000, PER_CAST = 8_000_000, CHARGE = 40, STOP = 100, DELAY = 20;
    public static final double DAMAGE = 200.0, DULL_DAMAGE = 40.0, SPEED = 1.2, RADIUS = 64.0;
    public static final long DAMAGE_VALUE = Integer.MAX_VALUE;
    public static final float WEIGHT = 15.0F;
    public static final int STAR = 0xC8E0FF, GOLD = 0xFFE8A0, ROSE = 0xFFB0E0, CORE = 0xFFFFFF, CRYSTAL = 0xBDF4FF, CYAN = 0x60E0FF;

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public LuminousRequiemItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, CHARGE, PER_CAST);
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
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide() && selected && entity instanceof LivingEntity user && level.getGameTime() % 20 == 0) blessHolder(user);
    }

    /** 持ち主への加護（兵姫に持たせたときも同じ処理） */
    public static void blessHolder(LivingEntity user) {
        Overload.bless(user, MobEffects.REGENERATION, 60, 2);
        Overload.bless(user, MobEffects.DAMAGE_RESISTANCE, 60, 2);
        Overload.bless(user, MobEffects.DAMAGE_BOOST, 60, 2);
        Overload.bless(user, MobEffects.FIRE_RESISTANCE, 60, 0);
        Overload.blessExtras(user); // オーバーロードなら耐性 IV・再生 VI
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            WeaponPower.use(attacker, stack, 10_000);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), STAR, 0.35F, 10), new Vec3(target.getX(), target.getY(0.5), target.getZ()), 10, 0.35);
        }
        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    /** 通常攻撃の間隔（Tick） */
    @Override
    public int cooldownTicks() {
        return 6;
    }

    /** 通常攻撃（左クリック。空振りでも。0.3 秒に 1 回・50,000 FE）: 虹の斬撃が飛ぶ（{@link T4NormalAttacks}） */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level && T4NormalAttacks.ready(entity, stack, cooldownTicks(), 50_000)) {
            T4NormalAttacks.rainbowSlash(level, entity);
        }
        return false;
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return Fx.opt(ModParticles.GLOW.get(), STAR, 0.3F, 10);
    }

    @Override
    protected void fire(ServerLevel level, LivingEntity user, ItemStack stack) {
        requiem(level, user);
    }

    /** 鎮魂歌の的（モンスターと、持ち主を狙っている mob） */
    static boolean target(LivingEntity user, LivingEntity e) {
        if (e == user || !e.isAlive() || e.isSpectator() || e.isAlliedTo(user)) return false;
        return e instanceof Enemy || (e instanceof Mob mob && mob.getTarget() == user);
    }

    /** 星の鎮魂歌が届く画面の範囲（ブロック）と、星空に染まる長さ（Tick） */
    public static final double SKY_RANGE = 96.0;
    public static final int SKY_TICKS = 140;

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        super.onUseTick(level, user, stack, remaining);
        if (level instanceof ServerLevel server && user.isUsingItem()) {
            int t = elapsed(stack, remaining);
            int ct = chargeTicks(stack);
            if (t <= ct) gather(server, user, t / (float) ct, t);
        }
    }

    /** 持ち主の背に立つ縦の輪の、中心と向き（横・上・前） */
    static Vec3[] haloFrame(LivingEntity user) {
        Vec3 look = user.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        Vec3 center = user.position().add(0, user.getBbHeight() * 0.65, 0).subtract(flat.scale(0.9));
        return new Vec3[]{center, side, new Vec3(0, 1, 0), flat};
    }

    /** 背の輪の上の、k 番目の剣のビット（6 振り）の根元・先・向き */
    static Vec3[] bit(Vec3[] frame, int k, double spin, double radius) {
        double a = spin + k * Math.PI / 3;
        Vec3 dir = frame[1].scale(Math.cos(a)).add(frame[2].scale(Math.sin(a)));
        return new Vec3[]{frame[0].add(dir.scale(radius)), frame[0].add(dir.scale(radius + 1.6)), dir};
    }

    /** 溜め: 背の剣のまわりに星が瞬き、足元に星の陣が広がり、星が集まる（f = 0〜1） */
    static void gather(ServerLevel level, LivingEntity user, float f, int t) {
        var r = level.random;
        Vec3[] frame = haloFrame(user);
        double spin = t * (0.05 + 0.12 * f);
        // 背の 6 振りの剣はモデル（requiem_backdrop）で描くので、ここでは剣先の位置に星を瞬かせるだけ
        for (int k = 0; k < 6; k++) {
            if (t % 4 == k % 4) Fx.star(level, bit(frame, k, spin, 1.5 + 0.5 * f)[1], CORE, 0.35F + 0.3F * f, 8, Vec3.ZERO);
        }
        for (int i = 0; i < 2 + (int) (f * 4); i++) { // 集まる星
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
            Vec3 from = frame[0].add(d.scale(3.5 + r.nextDouble() * 2));
            Fx.star(level, from, i % 3 == 0 ? GOLD : i % 3 == 1 ? ROSE : STAR, 0.25F + 0.2F * f, 12, frame[0].subtract(from).scale(1 / 12.0));
        }
        if (t % 10 == 0) {
            Fx.mandala(level, user.position().add(0, 0.06, 0), GOLD, 2.5F + 4F * f, 16, false, false);
            Fx.mandala(level, user.position().add(0, 0.09, 0), CYAN, 1.5F + 2.5F * f, 18, false, true);
        }
        if (t % 8 == 0) Fx.mandala(level, frame[0].subtract(frame[3].scale(0.2)), GOLD, 1.2F + 1.6F * f, 12, true, false);
        if (f >= 1F) {
            Fx.burst(level, Fx.opt(ModParticles.STAR.get(), CORE, 0.5F, 14), frame[0], 16, 0.35);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 2.0F, 8), frame[0], 1, 0, 0);
            level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.6F);
            level.playSound(null, user.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 1.4F);
        }
    }

    /** 星の鎮魂歌（兵姫に持たせたときも同じ処理）。的にした数 */
    public static int requiem(ServerLevel level, LivingEntity user) {
        double radius = RADIUS * Overload.range(user); // オーバーロードの T4 は範囲が広い
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius), e -> target(user, e) && e.distanceTo(user) <= radius);
        Vec3 c = user.position();
        Vec3[] frame = haloFrame(user);
        for (net.minecraft.server.level.ServerPlayer player : level.players()) { // まわりのプレイヤーの画面を星空に
            if (player.distanceToSqr(c) <= SKY_RANGE * SKY_RANGE) {
                com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new com.automatavalkyries.network.TwilightPacket(SKY_TICKS, com.automatavalkyries.network.TwilightPacket.STARRY));
            }
        }
        // 背に大きな星の陣（縦）、足元に三重の星の陣、空に星の天蓋
        Fx.mandala(level, frame[0], GOLD, 7.0F, 60, true, false);
        Fx.mandala(level, frame[0], CYAN, 4.5F, 60, true, true);
        Fx.mandala(level, c.add(0, 0.06, 0), GOLD, 18.0F, 70, false, false);
        Fx.mandala(level, c.add(0, 0.1, 0), CYAN, 11.0F, 70, false, true);
        Fx.mandala(level, c.add(0, 0.14, 0), ROSE, 6.0F, 70, false, false);
        for (int i = 0; i < 120; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * 28;
            Vec3 p = c.add(Math.cos(a) * r, 16 + level.random.nextDouble() * 8, Math.sin(a) * r);
            Fx.star(level, p, i % 3 == 0 ? GOLD : i % 3 == 1 ? ROSE : STAR, 0.4F + level.random.nextFloat() * 0.8F, 50 + level.random.nextInt(30), Vec3.ZERO);
        }
        for (int i = 0; i < 4; i++) Fx.shockwave(level, c.add(0, 0.05 + i * 0.1, 0), i % 2 == 0 ? STAR : GOLD, 10F + i * 8F, 20 + i * 4);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 3.5F, 10), frame[0], 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.STAR.get(), CORE, 0.6F, 20), frame[0], 30, 0.6);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6F, 1.6F);
        // 的ごとに: 時を止め、背の光の剣から光の筋が飛んで頭上に印、1 秒後に星の柱が降る
        int shown = 0;
        for (LivingEntity e : targets) {
            e.addEffect(new MobEffectInstance(ModWeapons.CAUSAL_STOP.get(), STOP, 0, false, true, true));
            Vec3 head = new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.6, e.getZ());
            if (shown++ < 48) { // 光の筋は 48 体まで（多すぎると粒が溢れる）
                Vec3 from = bit(frame, shown % 6, 0, 1.6)[1];
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CRYSTAL, 0.18F, 14), from, head, 0.7);
                Fx.line(level, Fx.opt(ModParticles.STAR.get(), CORE, 0.25F, 16), from, head, 2.5);
                Fx.mandala(level, e.position().add(0, 0.06, 0), GOLD, 1.6F + e.getBbWidth(), DELAY + 10, false, shown % 2 == 0);
            }
            Fx.burst(level, Fx.opt(ModParticles.GLYPH.get(), STAR, 0.4F, 30), head, 4, 0.03);
            Fx.star(level, head, CORE, 0.8F, DELAY + 5, Vec3.ZERO);
        }
        WeaponUtil.later(level, DELAY, () -> {
            int beams = 0;
            for (LivingEntity e : targets) {
                if (!e.isAlive()) continue;
                Vec3 at = new Vec3(e.getX(), e.getY(0.5), e.getZ());
                if (beams++ < 48) {
                    level.addFreshEntity(new PunishmentBeamEntity(level, at.add(0, 30, 0), e.position(), PunishmentBeamEntity.SCHEME_STELLA, 1.2F, 24));
                    Fx.mandala(level, e.position().add(0, 0.08, 0), CYAN, 3.0F, 24, false, true);
                    Fx.burst(level, Fx.opt(ModParticles.STAR.get(), CORE, 0.5F, 18), at, 10, 0.45);
                    Fx.shockwave(level, e.position().add(0, 0.05, 0), GOLD, 3.5F, 12);
                }
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ORBITAL, user, user), (float) DAMAGE_VALUE);
                Overload.overkill(e, user);
                Fx.number(level, at.add(0, e.getBbHeight() * 0.5 + 0.6, 0), DAMAGE_VALUE, STAR);
            }
            Fx.mandala(level, c.add(0, 0.2, 0), CORE, 24.0F, 30, false, true);
            level.playSound(null, user.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5F, 1.2F);
            level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.6F);
            level.playSound(null, user.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.5F, 0.8F);
        });
        return targets.size();
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_CAST), String.format("%.1f", CHARGE / 20.0)};
    }
}
