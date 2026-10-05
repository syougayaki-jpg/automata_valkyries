package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * リニアレールライフル（§7.2 weapon_linear_rail_rifle、T1）: 両手ライフル。徹甲弾（内部 12 発）と 1 射 25,000 FE を使う。
 * 射程 64 ブロック、弾速 12 ブロック/Tick で弾道の落下が無いので、撃った瞬間に線上を判定する。
 * 単発 35.0 ダメージ、線上の最大 4 体を貫通し、防具（防御力・防具強度）の 50% を無視する。
 * 弾が尽きたら、右クリックで持ち物の徹甲弾から装填する（2 秒）。連射の間隔は 1 秒【提案】。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 超電磁の極光砲。弾道が太さ 3・長さ 96 ブロックの光の奔流になり、
 * 線上の敵 32 体までを貫く（同じダメージ・防具 50% 無視）。弾道には 6 ブロックおきに水色と金の電磁加速の輪が並び、銃口に紅と金の陣、
 * 着弾点（壁か 96 ブロック先）で半径 6 の炸裂（線から外れた敵にも同じダメージ）</p>
 */
public class RailRifleItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    /** 弾の威力（説明に出す） */
    @Override
    public float[] shotDamage() {
        return new float[]{DAMAGE, 1};
    }


    public static final int CAPACITY = 500_000, PER_SHOT = 25_000, MAGAZINE = 12, COOLDOWN = 20, RELOAD = 40, PIERCE = 4;
    public static final float DAMAGE = 50.0F, ARMOR_IGNORE = 0.5F, WEIGHT = 12.0F;
    public static final double RANGE = 64.0;
    /** オーバーロード: 射程・弾道の太さ・貫く数・着弾の炸裂の半径・加速の輪の間隔 */
    public static final double OVER_RANGE = 96.0, OVER_WIDTH = 3.0, OVER_BLAST = 6.0, OVER_RING_STEP = 6.0;
    public static final int OVER_PIERCE = 32;
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.3F, 0.8F, 1.0F), 0.8F);

    public RailRifleItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, true);
    }

    public static int rounds(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("Rounds") : 0;
    }

    public static void setRounds(ItemStack stack, int rounds) {
        stack.getOrCreateTag().putInt("Rounds", Math.max(0, Math.min(MAGAZINE, rounds)));
    }

    @Override
    public ItemStack charged() {
        ItemStack stack = super.charged();
        setRounds(stack, MAGAZINE);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack rifle = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(rifle);
        if (rounds(rifle) <= 0) {
            if (!reload(player, rifle)) {
                if (!level.isClientSide()) player.displayClientMessage(Component.translatable("gui.automata_valkyries.no_ammo").withStyle(ChatFormatting.RED), true);
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 1.2F);
                return InteractionResultHolder.fail(rifle);
            }
            player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, RELOAD));
            level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F, 0.7F);
            return InteractionResultHolder.sidedSuccess(rifle, level.isClientSide());
        }
        if (!WeaponPower.has(player, rifle, PER_SHOT)) {
            notEnoughPower(player);
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 0.8F);
            return InteractionResultHolder.fail(rifle);
        }
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, rifle, PER_SHOT);
            if (!player.getAbilities().instabuild) setRounds(rifle, rounds(rifle) - 1);
            fire(server, player);
            playUse(player, rifle);
        }
        clientKick(player);
        return InteractionResultHolder.consume(rifle); // 殴る振りはしない（反動の動きを付ける）
    }

    /**
     * 戦術兵姫が撃つ（{@link ValkyrieArts}）。弾倉が空なら内蔵弾薬庫の徹甲弾で込め直し（装填の間は撃たない）、
     * 込められなければ false。電力は 1 発ごと
     */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack rifle, @Nullable LivingEntity target) {
        if (!canWield(user, InteractionHand.MAIN_HAND)) return false;
        CompoundTag tag = rifle.getOrCreateTag();
        if (level.getGameTime() < tag.getLong("ValkyrieReload")) return true; // 装填中
        if (rounds(rifle) <= 0) {
            int loaded = ValkyrieArts.takeAmmo(user, ModWeapons.AMMO_AP_ROUND.get(), MAGAZINE);
            if (loaded <= 0) return false;
            setRounds(rifle, loaded);
            tag.putLong("ValkyrieReload", level.getGameTime() + WeaponUpgrades.ticks(rifle, RELOAD));
            level.playSound(null, user.blockPosition(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F, 0.7F);
            return true;
        }
        if (!WeaponPower.use(user, rifle, PER_SHOT)) return false;
        setRounds(rifle, rounds(rifle) - 1);
        fire(level, user);
        playUse(user, rifle);
        return true;
    }

    /** 持ち物の徹甲弾で満たす（クリエイティブは弾を使わない）。1 発も入らなければ false */
    private static boolean reload(Player player, ItemStack rifle) {
        if (player.getAbilities().instabuild) {
            setRounds(rifle, MAGAZINE);
            return true;
        }
        int need = MAGAZINE - rounds(rifle);
        for (int i = 0; i < player.getInventory().getContainerSize() && need > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModWeapons.AMMO_AP_ROUND.get())) {
                int taken = Math.min(need, stack.getCount());
                stack.shrink(taken);
                need -= taken;
            }
        }
        int loaded = MAGAZINE - need;
        if (loaded == rounds(rifle)) return false;
        setRounds(rifle, loaded);
        return true;
    }

    /** 線上の最大 PIERCE 体に当てる。誰が撃ってもよい（兵姫に持たせたときも同じ処理） */
    public static void fire(ServerLevel level, LivingEntity shooter) {
        if (OverloadArts.active(shooter)) {
            overFire(level, shooter);
            return;
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        Vec3 stop = end;
        List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(1.0),
                        e -> e != shooter && e.isAlive() && e.isPickable() && !e.isSpectator()
                                && e.getBoundingBox().inflate(0.3).clip(eye, stop).isPresent())
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(eye))).limit(PIERCE).toList();
        for (LivingEntity target : hits) {
            target.hurt(WeaponDamage.source(shooter, WeaponDamage.ARMOR_PIERCING, shooter, shooter),
                    WeaponDamage.armorPiercing(target, DAMAGE, ARMOR_IGNORE));
            Vec3 hit = new Vec3(target.getX(), target.getY(0.6), target.getZ());
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.WHITE, 0.3F, 8), hit, 10, 0.35);
            Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.CYAN, 0.7F, 8), hit, 2, 0.1, 0);
        }
        // 弾道: 白い芯と水色の光の帯、まわりを巡るきらめき
        Vec3 from = eye.add(look.scale(1.0)).add(0, -0.1, 0), to = stop.add(0, -0.1, 0);
        Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 0.14F, 10), from, to, 0.35);
        Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.CYAN, 0.28F, 14), from, to, 0.5);
        double length = to.distanceTo(from);
        Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(look).normalize();
        for (double d = 0; d < length; d += 0.8) {
            double a = d * 1.6;
            Vec3 p = from.add(look.scale(d)).add(side.scale(Math.cos(a) * 0.25)).add(up.scale(Math.sin(a) * 0.25));
            Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.ICE, 0.12F, 12), p, 1, 0, 0);
        }
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.ICE, 0.3F, 10), stop, 10, 0.3);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.CYAN, 0.3F, 8), from, 6, 0.08);
        level.playSound(null, shooter.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 2.0F);
        shooter.push(-look.x * 0.2, 0.0, -look.z * 0.2); // 反動
        shooter.hurtMarked = true;
    }

    /** オーバーロード: 太さ 3・長さ 96 の光の奔流。32 体まで貫き、着弾点で半径 6 に炸裂する */
    private static void overFire(ServerLevel level, LivingEntity shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        WeaponUtil.Ray ray = WeaponUtil.ray(level, shooter, eye, look, OVER_RANGE, OVER_WIDTH, OVER_PIERCE);
        List<LivingEntity> hits = ray.hits().stream().filter(e -> !e.isAlliedTo(shooter)).toList();
        int shown = 0;
        for (LivingEntity target : hits) {
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(shooter, WeaponDamage.ARMOR_PIERCING, shooter, shooter), WeaponDamage.armorPiercing(target, DAMAGE, ARMOR_IGNORE));
            if (shown++ < 12) OverloadArts.impact(level, new Vec3(target.getX(), target.getY(0.6), target.getZ()));
        }
        Vec3 stop = ray.end();
        // 着弾の炸裂: 半径 6 の敵（線から外れた敵）にも同じダメージ
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(stop, stop).inflate(OVER_BLAST),
                e -> e != shooter && e.isAlive() && !e.isAlliedTo(shooter) && !e.isSpectator() && !hits.contains(e)
                        && e.distanceToSqr(stop) <= (OVER_BLAST + e.getBbWidth()) * (OVER_BLAST + e.getBbWidth()))) {
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(shooter, WeaponDamage.ARMOR_PIERCING, shooter, shooter), WeaponDamage.armorPiercing(e, DAMAGE, ARMOR_IGNORE));
        }
        // 光の奔流: 白い芯、太い水色の帯、紅の外縁、電磁加速の輪
        Vec3 from = eye.add(look.scale(1.0)).add(0, -0.1, 0), to = stop.add(0, -0.1, 0);
        Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 0.5F, 12), from, to, 1.0);
        Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.CYAN, 1.3F, 16), from, to, 1.3);
        Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Overload.CRIMSON, 1.8F, 10), from, to, 2.4);
        double length = to.distanceTo(from);
        int ring = 0;
        for (double d = OVER_RING_STEP; d < length; d += OVER_RING_STEP) {
            Fx.beamRing(level, from.add(look.scale(d)), look, ring++ % 2 == 0 ? Fx.CYAN : Overload.GOLD, (float) OVER_WIDTH * 0.8F, 14);
        }
        Fx.mandala(level, from.add(look.scale(0.6)), Overload.GOLD, 2.2F, 16, true, true);
        Fx.beamRing(level, from, look, Overload.CRIMSON, 3.5F, 12);
        Fx.shockwave(level, stop, Fx.CYAN, (float) OVER_BLAST, 12);
        Fx.shockwave(level, stop, Overload.CRIMSON, (float) OVER_BLAST * 1.3F, 16);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 5.0F, 6), stop, 1, 0, 0);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.ICE, 0.4F, 14), stop, 36, 0.8);
        OverloadArts.flourish(level, shooter, 6.0F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.PLAYERS, 2.0F, 0.4F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, 1.5F);
        level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.8F);
        shooter.push(-look.x * 0.35, 0.0, -look.z * 0.35); // 大きな反動
        shooter.hurtMarked = true;
    }

    @Override
    public float recoil() {
        return 1.0F;
    }

    @Override
    protected void appendStatus(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable("gui.automata_valkyries.rounds", rounds(stack), MAGAZINE).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SHOT)};
    }
}
