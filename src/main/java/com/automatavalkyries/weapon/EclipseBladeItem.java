package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
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
 * 蝕刃『エクリプス』（T3、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 片手の黒い刃と金の光輪の剣。攻撃力 22 / 攻撃速度 1.6。
 * 当てるたびに相手へ「蝕の印」を刻む（10 秒で消える）。印が 5 つ揃うと日蝕が起き、相手に 60（無敵時間を無視）・持ち主は 10 回復。1 当たり 20,000 FE。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 日蝕は「皆既日蝕」になる。相手の頭上 6 に黒い太陽（半径 3 の黒い円盤）と
 * 半径 8 の金と紅の光冠が開き、光冠から 12 本の光の筋が地面へ走る。相手だけでなく、相手のまわり半径 8 の敵（12 体まで）にも日蝕と同じ一撃。
 * 持ち主の回復は打った数だけ（1 体ごとに 10）</p>
 */
public class EclipseBladeItem extends TacticalWeaponItem {

    public static final int CAPACITY = 2_000_000, PER_HIT = 20_000, MARKS = 5, MARK_LIFE = 200;
    public static final double DAMAGE = 220.0, DULL_DAMAGE = 60.0, SPEED = 1.6;
    public static final float BURST_DAMAGE = 500.0F, HEAL = 10.0F, WEIGHT = 4.0F;
    public static final int COLOR = 0xFFC840, DARK = 0x200830;
    /** オーバーロード: 皆既日蝕の半径・巻き込む数 */
    public static final double OVER_RADIUS = 8.0;
    public static final int OVER_MAX = 12;
    static final String TAG = "AutomataEclipse", COUNT = "Marks", TIME = "Time";

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public EclipseBladeItem(Properties properties) {
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
        if (!WeaponPower.use(attacker, stack, PER_HIT) || !(attacker.level() instanceof ServerLevel level)) return true;
        mark(level, attacker, target);
        playUse(attacker, stack);
        return true;
    }

    /** 印を 1 つ刻む。揃ったら日蝕（兵姫に持たせたときも同じ処理）。刻んだ後の印の数（日蝕が起きたら 0） */
    public static int mark(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        CompoundTag data = target.getPersistentData().getCompound(TAG);
        long now = level.getGameTime();
        int marks = now - data.getLong(TIME) > MARK_LIFE ? 0 : data.getInt(COUNT);
        marks++;
        Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        if (marks >= MARKS) {
            target.getPersistentData().remove(TAG);
            eclipse(level, attacker, target);
            return 0;
        }
        data.putInt(COUNT, marks);
        data.putLong(TIME, now);
        target.getPersistentData().put(TAG, data);
        // 印: 相手の頭上に、刻んだ数だけ金の光の点が輪に並ぶ
        Vec3 head = new Vec3(target.getX(), target.getY() + target.getBbHeight() + 0.4, target.getZ());
        for (int i = 0; i < marks; i++) {
            double a = i * Math.PI * 2 / MARKS;
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.25F, 20), head.add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5), Vec3.ZERO);
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 7), at, 4 + marks, 0.25);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 0.6F + marks * 0.2F);
        return marks;
    }

    /** 日蝕: 黒い円と金の光輪、相手に大きな一撃・持ち主の回復 */
    static void eclipse(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        if (OverloadArts.active(attacker)) {
            totalEclipse(level, attacker, target);
            return;
        }
        Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        target.invulnerableTime = 0;
        target.hurt(WeaponDamage.source(attacker, WeaponDamage.ENERGY, attacker, attacker), BURST_DAMAGE);
        attacker.heal(HEAL);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            Vec3 p = at.add(Math.cos(a) * 1.4, Math.sin(a) * 1.4, 0);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.35F, 14), p, p.subtract(at).scale(0.05));
        }
        Fx.spray(level, Fx.opt(ModParticles.VOID_SHARD.get(), DARK, 0.4F, 16), at, 16, 0.5, 0.1);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 1.8F, 5), at, 1, 0, 0);
        Fx.shockwave(level, target.position().add(0, 0.05, 0), COLOR, 4.0F, 12);
        Fx.number(level, at.add(0, 1, 0), (long) BURST_DAMAGE, COLOR);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x80FFA0, 0.3F, 16), new Vec3(attacker.getX(), attacker.getY(0.5), attacker.getZ()), 8, 0.4, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.6F, 1.4F);
        level.playSound(null, target.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** オーバーロードの皆既日蝕: 頭上の黒い太陽と半径 8 の光冠、相手とまわり半径 8 の敵（12 体まで）に大きな一撃 */
    private static void totalEclipse(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        Vec3 ground = target.position();
        java.util.List<LivingEntity> hit = new java.util.ArrayList<>();
        hit.add(target);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(OVER_RADIUS),
                e -> e != attacker && e != target && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(attacker)
                        && e.position().distanceTo(ground) <= OVER_RADIUS + e.getBbWidth())) {
            if (hit.size() > OVER_MAX) break;
            hit.add(e);
        }
        for (LivingEntity e : hit) {
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(attacker, WeaponDamage.ENERGY, attacker, attacker), BURST_DAMAGE);
            Vec3 at = new Vec3(e.getX(), e.getY(0.6), e.getZ());
            Fx.spray(level, Fx.opt(ModParticles.VOID_SHARD.get(), DARK, 0.4F, 16), at, 6, 0.4, 0.1);
            OverloadArts.impact(level, at);
        }
        attacker.heal(HEAL * hit.size());
        // 黒い太陽（頭上 6）: 黒い円盤と、金と紅の光冠（半径 8 まで）
        Vec3 sun = ground.add(0, target.getBbHeight() + 6, 0);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), DARK, 6.0F, 30), sun, 1, 0, 0);
        Fx.mandala(level, sun, COLOR, 3.5F, 30, true, true);
        Fx.beamRing(level, sun, new Vec3(0, 1, 0), COLOR, (float) OVER_RADIUS, 20);
        Fx.beamRing(level, sun, new Vec3(0, 1, 0), Overload.CRIMSON, (float) OVER_RADIUS * 0.6F, 16);
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36;
            Vec3 p = sun.add(Math.cos(a) * 3.2, 0, Math.sin(a) * 3.2);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), i % 3 == 0 ? Overload.CRIMSON : COLOR, 0.6F, 24), p, new Vec3(Math.cos(a), 0, Math.sin(a)).scale(0.25));
        }
        for (int i = 0; i < 12; i++) { // 光冠から地面へ走る光の筋（半径 8 の縁へ）
            double a = i * Math.PI / 6;
            Vec3 foot = ground.add(Math.cos(a) * OVER_RADIUS, 0.1, Math.sin(a) * OVER_RADIUS);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), i % 2 == 0 ? COLOR : Overload.GOLD, 0.3F, 14), sun, foot, 1.0);
        }
        Fx.shockwave(level, ground.add(0, 0.05, 0), COLOR, (float) OVER_RADIUS, 18);
        Fx.shockwave(level, ground.add(0, 0.1, 0), DARK, (float) OVER_RADIUS * 0.75F, 20);
        Fx.shockwave(level, ground.add(0, 0.15, 0), Overload.CRIMSON, (float) OVER_RADIUS * 0.5F, 16);
        Fx.number(level, new Vec3(target.getX(), target.getY(0.6), target.getZ()).add(0, 1, 0), (long) BURST_DAMAGE, COLOR);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x80FFA0, 0.3F, 16), new Vec3(attacker.getX(), attacker.getY(0.5), attacker.getZ()), 12, 0.5, 0.02);
        OverloadArts.flourish(level, attacker, (float) OVER_RADIUS);
        level.playSound(null, target.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, target.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.4F, 1.8F);
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
