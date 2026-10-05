package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 黒蓮扇『ブラックロータス』（T3、追加の兵装。2026-10-03 こちらが考えた）: 片手の扇。右クリックで 6 秒のあいだ、黒と薄紅の花弁の刃 8 枚が
 * 持ち主のまわり（半径 2.5）を舞う嵐を起こす。嵐に触れた敵（半径 3.5）は 0.5 秒ごとに 8 ダメージ。嵐の終わりに花弁の刃が 8 方向へ飛び散り、
 * 線上（12 ブロック）の敵に 16 ダメージ。1 回 100,000 FE、間隔 10 秒。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定「半径 15 ブロックの花吹雪に拡大」）: 花弁の刃は半径 5・10・15 の 3 重の輪（36 枚）で舞い、
 * 半径 15 の中が桜吹雪で埋まる。嵐は半径 15 の敵を斬る。終わりの飛び散りは 24 方向・30 ブロック</p>
 */
public class BlackLotusItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 1_500_000, PER_STORM = 100_000, DURATION = 120, COOLDOWN = 200, PETALS = 8, TICK_RATE = 10;
    public static final float STORM_DAMAGE = 100.0F, BURST_DAMAGE = 200.0F, WEIGHT = 2.0F;
    public static final double ORBIT = 2.5, REACH = 3.5, BURST_RANGE = 12.0;
    /** 扇で打つ近接の攻撃力（電力があるとき / 切れたとき）・攻撃速度・1 打の電力（2026-10-06 ユーザー指定「殴って攻撃するので武器攻撃力を」。値は【仮】） */
    public static final double DAMAGE = 180.0, DULL_DAMAGE = 45.0, SPEED = 2.0;
    public static final int PER_HIT = 10_000;
    public static final int PINK = 0xFF8AC8, BLACK = 0x3A1028, WHITE = 0xFFE8F4;
    /** オーバーロード: 3 重の輪の半径・輪ごとの刃の数・嵐の届く距離・飛び散りの方向の数と距離 */
    public static final double[] OVER_RINGS = {5.0, 10.0, 15.0};
    public static final int OVER_PETALS = 12, OVER_BURST_DIRS = 24;
    public static final double OVER_REACH = 15.0, OVER_BURST_RANGE = 30.0;

    private final com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>
            charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public BlackLotusItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    private static com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> modifiers(double damage) {
        return com.google.common.collect.ImmutableMultimap.of(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
                        "Weapon modifier", damage - 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION),
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_SPEED_UUID,
                        "Weapon modifier", SPEED - 4.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }

    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(
            net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= PER_HIT ? charged : dull;
    }

    /** 扇で打つたびに電力を使う（切れていれば弱い打撃のまま） */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        WeaponPower.use(attacker, stack, PER_HIT);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_STORM)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) begin(server, player, stack);
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 嵐を始める（電力を使い、刃の嵐を {@link #DURATION} Tick） */
    private void begin(ServerLevel server, LivingEntity user, ItemStack stack) {
        WeaponPower.use(user, stack, PER_STORM);
        stack.getOrCreateTag().putInt("Storm", DURATION);
        Fx.shockwave(server, user.position().add(0, 0.05, 0), PINK, OverloadArts.active(user) ? 16.0F : 4.0F, 14);
        if (OverloadArts.active(user)) OverloadArts.flourish(server, user, 15.0F);
        server.playSound(null, user.blockPosition(), SoundEvents.CHERRY_LEAVES_BREAK, SoundSource.PLAYERS, 1.2F, 0.8F);
        server.playSound(null, user.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.4F, 1.6F);
        playUse(user, stack);
    }

    /** 戦術兵姫が嵐を起こす（{@link ValkyrieArts}）。嵐の最中・電力が足りなければ false */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (stack.getOrCreateTag().getInt("Storm") > 0 || !WeaponPower.has(user, stack, PER_STORM)) return false;
        begin(level, user, stack);
        user.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        CompoundTag tag = stack.getTag();
        if (!(level instanceof ServerLevel server) || tag == null || tag.getInt("Storm") <= 0 || !(entity instanceof LivingEntity user)) return;
        int left = tag.getInt("Storm") - 1;
        tag.putInt("Storm", left);
        storm(server, user, DURATION - left);
        if (left == 0) burst(server, user);
    }

    /** 嵐の 1 Tick: 花弁の刃が舞い、触れた敵を斬る（兵姫に持たせたときも同じ処理） */
    public static void storm(ServerLevel level, LivingEntity user, int t) {
        if (OverloadArts.active(user)) {
            overStorm(level, user, t);
            return;
        }
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        for (int k = 0; k < PETALS; k++) {
            double a = t * 0.32 + k * Math.PI * 2 / PETALS;
            double bob = Math.sin(t * 0.2 + k) * 0.5;
            Vec3 p = c.add(Math.cos(a) * ORBIT, bob, Math.sin(a) * ORBIT);
            Fx.slash(level, p, k % 2 == 0 ? PINK : WHITE, 0.45F, 3, (float) (a + Math.PI / 2));
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), k % 2 == 0 ? PINK : BLACK, 0.22F, 8), p, 1, 0, 0);
        }
        if (t % 3 == 0) {
            Fx.spray(level, Fx.opt(ModParticles.HEX.get(), PINK, 0.1F, 20), c, 3, ORBIT, 0.02);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES, c.x, c.y + 1, c.z, 3, ORBIT, 1, ORBIT, 0.02);
        }
        if (t % TICK_RATE != 0) return;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(REACH),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && e.distanceTo(user) <= REACH + e.getBbWidth())) {
            target.invulnerableTime = 0;
            target.hurt(WeaponUtil.melee(user), STORM_DAMAGE);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), PINK, 0.25F, 8), new Vec3(target.getX(), target.getY(0.6), target.getZ()), 5, 0.25);
        }
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.7F);
    }

    /** オーバーロードの嵐の 1 Tick: 半径 5・10・15 の 3 重の輪で花弁の刃が逆向きに舞い、半径 15 が桜吹雪で埋まる。半径 15 の敵を斬る */
    private static void overStorm(ServerLevel level, LivingEntity user, int t) {
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        for (int ring = 0; ring < OVER_RINGS.length; ring++) {
            double radius = OVER_RINGS[ring];
            double spin = (ring % 2 == 0 ? 1 : -1) * t * (0.24 / (1 + ring * 0.5));
            for (int k = 0; k < OVER_PETALS; k++) {
                double a = spin + k * Math.PI * 2 / OVER_PETALS + ring * 0.4;
                double bob = Math.sin(t * 0.15 + k + ring) * (0.6 + ring * 0.5) + ring * 0.8;
                Vec3 p = c.add(Math.cos(a) * radius, bob, Math.sin(a) * radius);
                Fx.slash(level, p, (k + ring) % 2 == 0 ? PINK : WHITE, 0.8F + ring * 0.25F, 3, (float) (a + Math.PI / 2));
                if (k % 2 == 0) Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), (k / 2 + ring) % 2 == 0 ? PINK : Overload.GOLD, 0.35F, 10), p, 1, 0, 0);
            }
        }
        // 半径 15 を埋める桜吹雪（空から舞い落ちる花弁と、渦を巻いて昇る光）
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES, c.x, c.y + 4, c.z, 24, OVER_REACH * 0.6, 2.5, OVER_REACH * 0.6, 0.02);
        if (t % 2 == 0) {
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), PINK, 0.3F, 24), c.add(0, 2, 0), 10, OVER_REACH * 0.55, 2.0, OVER_REACH * 0.55, 0.03);
            Fx.spray(level, Fx.opt(ModParticles.HEX.get(), WHITE, 0.14F, 26), c, 6, OVER_REACH * 0.5, 1.5, OVER_REACH * 0.5, 0.02);
        }
        if (t % 10 == 0) {
            Fx.shockwave(level, user.position().add(0, 0.05, 0), PINK, (float) OVER_REACH, 18);
            Fx.mandala(level, user.position().add(0, 0.04, 0), Overload.GOLD, 6.0F, 20, false, (t / 10) % 2 == 0);
        }
        if (t % TICK_RATE != 0) return;
        int shown = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_REACH),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user) && e.distanceTo(user) <= OVER_REACH + e.getBbWidth())) {
            target.invulnerableTime = 0;
            target.hurt(WeaponUtil.melee(user), STORM_DAMAGE);
            Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
            Fx.slash(level, at, PINK, 1.0F, 4, level.random.nextFloat() * 6.28F);
            if (shown++ < 12) OverloadArts.impact(level, at);
        }
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.CHERRY_LEAVES_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /** 嵐の終わり: 花弁の刃が 8 方向へ飛び散る（オーバーロードは 24 方向・30 ブロック） */
    public static void burst(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        boolean over = OverloadArts.active(user);
        int dirs = over ? OVER_BURST_DIRS : PETALS;
        double range = over ? OVER_BURST_RANGE : BURST_RANGE;
        for (int k = 0; k < dirs; k++) {
            double a = k * Math.PI * 2 / dirs;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, c, dir, range, over ? 1.6 : 1.0, 8);
            for (LivingEntity target : ray.hits()) {
                target.invulnerableTime = 0;
                target.hurt(WeaponUtil.melee(user), BURST_DAMAGE);
            }
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), WHITE, 0.2F, 8), c, ray.end(), 0.4);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), PINK, 0.35F, 12), c, ray.end(), 0.6);
            Fx.slash(level, ray.end(), PINK, 1.0F, 6, (float) a);
        }
        Fx.shockwave(level, user.position().add(0, 0.05, 0), PINK, (float) range, 14);
        if (over) {
            Fx.shockwave(level, user.position().add(0, 0.05, 0), Overload.GOLD, (float) range * 0.7F, 18);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES, c.x, c.y + 2, c.z, 200, range * 0.4, 3, range * 0.4, 0.3);
            OverloadArts.flourish(level, user, (float) range);
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), WHITE, 0.35F, 12), c, 20, 0.5);
        level.playSound(null, user.blockPosition(), SoundEvents.CHERRY_LEAVES_BREAK, SoundSource.PLAYERS, 1.5F, 1.2F);
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_STORM)};
    }
}
