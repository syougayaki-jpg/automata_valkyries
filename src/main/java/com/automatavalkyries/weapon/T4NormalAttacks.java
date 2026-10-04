package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * T4 兵装の通常攻撃（左クリック。空振りでも出る。2026-10-04 ユーザー指定。数値は【提案】）。どれも兵姫に持たせたときも同じ処理。
 * <ul>
 *   <li>星葬冠『ルミナス・レクイエム』: 虹の斬撃が飛ぶ（{@link RainbowSlashEntity}、1,500・貫通・40 ブロック）。0.3 秒に 1 回・50,000 FE</li>
 *   <li>終の黄昏『ラグナロク』: 赤黒い横薙ぎの斬撃がまわりに起きる（半径 {@link #SWEEP_RADIUS}・上下 3、{@link #SWEEP_DAMAGE}、外へ弾く）。0.6 秒に 1 回・80,000 FE</li>
 *   <li>極光轟雷『パニッシュメント・レールガン』: 狙った敵（64 ブロック先まで、照準の近くの敵も拾う）に青白い落雷（{@link #THUNDER_DAMAGE}）、
 *       そこから近くの敵 5 体まで放電が跳ぶ（{@link #ARC_DAMAGE}）。0.75 秒に 1 回・100,000 FE</li>
 *   <li>特異点砲『カタストロフ・バスター』: 敵を追う極小のカタストロフを撃つ（{@link MiniCatastropheEntity}）。0.75 秒に 1 回・100,000 FE</li>
 *   <li>天輪砲『ジェネシス・ヘイロー』: 手の中心のクリスタルから、きらきらの光線（64 ブロック、線上の敵を貫いて {@link #LASER_DAMAGE}）。0.4 秒に 1 回・60,000 FE</li>
 * </ul>
 */
public final class T4NormalAttacks {

    public static final double SWEEP_RADIUS = 7.0, AIM_RANGE = 64.0, ARC_RANGE = 10.0, LASER_RANGE = 64.0;
    public static final float SWEEP_DAMAGE = 2000.0F, THUNDER_DAMAGE = 3000.0F, ARC_DAMAGE = 1500.0F, LASER_DAMAGE = 2000.0F;
    public static final int ARC_TARGETS = 5;
    public static final int THUNDER_CORE = 0xE8F8FF, THUNDER_BLUE = 0x70C8FF, THUNDER_DEEP = 0x4060FF;
    public static final int SWEEP_CRIMSON = 0xC01028, SWEEP_BLOOD = 0x600010, SWEEP_BLACK = 0x180408;

    private T4NormalAttacks() {}

    /**
     * 通常攻撃を出せるか（サーバーで、利き手に持っていて、両手兵装はオフハンドが空、前の攻撃から interval Tick 経っていて、電力 cost を引き出せたとき）。
     * 出せるなら次に出せる時刻を記録する
     */
    public static boolean ready(LivingEntity user, ItemStack stack, int interval, int cost) {
        if (!(user.level() instanceof ServerLevel level) || user.getMainHandItem() != stack || !user.getOffhandItem().isEmpty() || user.isUsingItem()) return false;
        CompoundTag tag = stack.getOrCreateTag();
        long now = level.getGameTime();
        if (now < tag.getLong("NextNormal") || !WeaponPower.use(user, stack, cost)) return false;
        tag.putLong("NextNormal", now + WeaponUpgrades.ticks(stack, interval));
        return true;
    }

    /** オーバーロードの T4 兵装の通常攻撃の割合ダメージ（最大 HP に対する割合） */
    public static final float REND_RATIO = 0.01F;

    /**
     * 通常攻撃の 1 回の当たり（無敵時間を消して当てる）。撃った者がオーバーロードの T4 兵装を持っていれば、ウィザーの無敵も通る種類で当て、
     * さらに最大 HP の 1% の割合ダメージ（{@link WeaponDamage#OVERLOAD_REND}）を足す（2026-10-04 ユーザー指定）
     */
    public static boolean hit(LivingEntity victim, Entity context, net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type,
                              @Nullable Entity direct, @Nullable Entity causing, float amount) {
        boolean over = causing instanceof LivingEntity living && Overload.holdingT4(living);
        victim.invulnerableTime = 0;
        boolean hurt = victim.hurt(WeaponDamage.source(context, over ? WeaponDamage.OVERLOAD_PIERCE : type, direct, causing), amount);
        if (over && victim.isAlive()) {
            victim.invulnerableTime = 0;
            hurt |= victim.hurt(WeaponDamage.source(context, WeaponDamage.OVERLOAD_REND, direct, causing), victim.getMaxHealth() * REND_RATIO);
        }
        return hurt;
    }

    static boolean foe(@Nullable Entity owner, LivingEntity e) {
        return e != owner && e.isAlive() && !e.isSpectator() && !(owner != null && e.isAlliedTo(owner));
    }

    // ---- 星葬冠『ルミナス・レクイエム』 ----

    public static RainbowSlashEntity rainbowSlash(ServerLevel level, LivingEntity user) {
        RainbowSlashEntity slash = new RainbowSlashEntity(level, user);
        slash.setScale((float) Overload.range(user)); // オーバーロードの T4 は刃が広い
        Vec3 look = user.getLookAngle();
        slash.setPos(user.getX() + look.x * 0.8, user.getEyeY() - 0.3 + look.y * 0.8, user.getZ() + look.z * 0.8);
        float roll = (float) Math.toRadians(45 * level.random.nextInt(4) + (level.random.nextFloat() - 0.5F) * 24);
        slash.setRoll(roll);
        slash.shoot(look.x, look.y, look.z, RainbowSlashEntity.SPEED, 0);
        level.addFreshEntity(slash);
        Vec3 at = slash.position();
        for (int k = 0; k < 4; k++) Fx.slash(level, at, RainbowSlashEntity.rainbow(k / 4.0), 1.6F - k * 0.15F, 5, roll);
        Fx.star(level, at, 0xFFFFFF, 0.7F, 8, Vec3.ZERO);
        for (int k = 0; k < 10; k++) {
            Vec3 v = look.scale(0.2).add(new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()).scale(0.08));
            Fx.one(level, Fx.opt(ModParticles.STAR.get(), RainbowSlashEntity.rainbow(k / 10.0), 0.2F, 12), at, v);
        }
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.2F);
        return slash;
    }

    // ---- 終の黄昏『ラグナロク』 ----

    /** 赤黒い横薙ぎ。当てた数 */
    public static int twilightSweep(ServerLevel level, LivingEntity user) {
        Vec3 c = user.position();
        double radius = SWEEP_RADIUS * Overload.range(user); // オーバーロードの T4 は範囲が広い
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius + 1, 3 * Overload.range(user), radius + 1),
                e -> foe(user, e) && Math.hypot(e.getX() - c.x, e.getZ() - c.z) <= radius + e.getBbWidth() * 0.5);
        for (LivingEntity e : hit) {
            hit(e, user, WeaponDamage.RAGNAROK, user, user, SWEEP_DAMAGE);
            Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            if (out.lengthSqr() > 1.0E-4) {
                out = out.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(out.x * 0.7, 0.25, out.z * 0.7));
                e.hurtMarked = true;
            }
            Vec3 at = new Vec3(e.getX(), e.getY(0.5), e.getZ());
            Fx.slash(level, at, SWEEP_CRIMSON, 1.8F, 7, 0F);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), 0xFF4040, 0.3F, 10), at, 8, 0.35);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), SWEEP_BLACK, 0.8F, 14), at, 2, 0.3, 0.01);
        }
        // 見た目: 弧が一周する赤黒い帯（実体）、足元の紅の衝撃波、黒い煙と紅の火花が輪に沿って散る
        float yaw = user.getYRot();
        level.addFreshEntity(new T4StrikeEntity(level, T4StrikeEntity.SWEEP, c.add(0, user.getBbHeight() * 0.5, 0), c, (float) radius, yaw, 9));
        Fx.shockwave(level, c.add(0, 0.05, 0), SWEEP_CRIMSON, (float) radius + 1, 10);
        Fx.shockwave(level, c.add(0, 0.1, 0), SWEEP_BLOOD, (float) radius - 1.5F, 14);
        for (int k = 0; k < 24; k++) {
            double a = Math.toRadians(yaw + 90) + k * Math.PI * 2 / 24;
            double r = radius * (0.6 + 0.4 * level.random.nextDouble());
            Vec3 p = c.add(Math.cos(a) * r, user.getBbHeight() * 0.5 + (level.random.nextDouble() - 0.5), Math.sin(a) * r);
            Vec3 t = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.25);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), k % 3 == 0 ? 0xFF3030 : SWEEP_BLACK, k % 3 == 0 ? 0.35F : 0.7F, 14), p, t);
            if (k % 2 == 0) Fx.one(level, Fx.opt(ModParticles.SPARK.get(), 0xFF6040, 0.2F, 10), p, t.scale(1.5));
        }
        level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.6F, 0.6F);
        return hit.size();
    }

    // ---- 極光轟雷『パニッシュメント・レールガン』 ----

    /** 狙った敵（視線に当たる敵。いなければ照準の近く（約 6 度）の敵）。いなければ null */
    public static @Nullable LivingEntity aimed(ServerLevel level, LivingEntity user, double range) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        double reach = wall.getType() != HitResult.Type.MISS ? wall.getLocation().distanceTo(eye) + 1 : range;
        Vec3 end = eye.add(look.scale(reach));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(4), e -> foe(user, e))) {
            Vec3 center = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d = center.subtract(eye).dot(look);
            if (d < 0.5 || d > reach) continue;
            boolean direct = e.getBoundingBox().inflate(0.5).clip(eye, end).isPresent();
            double off = center.subtract(eye.add(look.scale(d))).length();
            if (!direct && off > Math.max(1.0, d * 0.1)) continue;
            double score = direct ? d : 1000 + off * 10 + d;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /** 青白い落雷と放電。落雷が落ちた的（敵がいなければ見ている地点に落ちて null） */
    public static @Nullable LivingEntity thunder(ServerLevel level, LivingEntity user) {
        double k = Overload.range(user); // オーバーロードの T4 は届く範囲も放電の数も多い
        LivingEntity target = aimed(level, user, AIM_RANGE * k);
        Vec3 ground;
        if (target != null) {
            ground = target.position();
        } else {
            Vec3 eye = user.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(user.getLookAngle().scale(AIM_RANGE * k)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            ground = hit.getLocation();
        }
        // 落雷（地面側に置き、空へ 28 ブロック）
        level.addFreshEntity(new T4StrikeEntity(level, T4StrikeEntity.THUNDER, ground, ground.add(0, 28, 0), (float) k, 0F, 10));
        Fx.shockwave(level, ground.add(0, 0.05, 0), THUNDER_BLUE, 3.5F, 10);
        Fx.shockwave(level, ground.add(0, 0.1, 0), THUNDER_CORE, 2.0F, 7);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), THUNDER_CORE, 2.2F, 6), ground.add(0, 0.8, 0), 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), THUNDER_BLUE, 0.35F, 12), ground.add(0, 0.5, 0), 18, 0.6);
        Fx.mandala(level, ground.add(0, 0.06, 0), THUNDER_BLUE, 2.2F, 12, false, false);
        level.playSound(null, net.minecraft.core.BlockPos.containing(ground), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.6F, 1.3F);
        level.playSound(null, net.minecraft.core.BlockPos.containing(ground), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 2.0F);
        // 銃口から細い放電が的へ
        Vec3 muzzle = user.getEyePosition().add(user.getLookAngle().scale(1.4)).add(0, -0.25, 0);
        level.addFreshEntity(new T4StrikeEntity(level, T4StrikeEntity.ARC, muzzle, target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : ground, 0.35F, 0F, 5));
        if (target == null) return null;
        hit(target, user, WeaponDamage.ARC, user, user, THUNDER_DAMAGE);
        // 放電: 的から近くの敵へ跳ぶ（近い順に 5 体まで）
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.5, 0);
        double arcRange = ARC_RANGE * k;
        List<LivingEntity> near = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(arcRange),
                e -> e != target && foe(user, e) && MiniCatastropheEntity.prey(user, e) && e.distanceTo(target) <= arcRange));
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(target)));
        int n = 0;
        for (LivingEntity e : near) {
            if (n++ >= ARC_TARGETS * k) break;
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0);
            level.addFreshEntity(new T4StrikeEntity(level, T4StrikeEntity.ARC, from, to, 0.5F, 0F, 7));
            hit(e, user, WeaponDamage.ARC, user, user, ARC_DAMAGE);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), THUNDER_CORE, 0.3F, 8), to, 8, 0.4);
        }
        return target;
    }

    // ---- 特異点砲『カタストロフ・バスター』 ----

    public static MiniCatastropheEntity miniCatastrophe(ServerLevel level, LivingEntity user) {
        MiniCatastropheEntity mini = new MiniCatastropheEntity(level, user);
        mini.setScale((float) Overload.range(user));
        Vec3 look = user.getLookAngle();
        mini.setPos(user.getX() + look.x * 1.4, user.getEyeY() - 0.35 + look.y * 1.4, user.getZ() + look.z * 1.4);
        mini.shoot(look.x, look.y, look.z, MiniCatastropheEntity.SPEED, 0);
        level.addFreshEntity(mini);
        Vec3 at = mini.position();
        Fx.beamRing(level, at, look, MiniCatastropheEntity.VIOLET, 0.9F, 8);
        Fx.burst(level, Fx.opt(ModParticles.VOID_SHARD.get(), MiniCatastropheEntity.EMBER, 0.1F, 10), at, 8, 0.2);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), MiniCatastropheEntity.VIOLET, 0.6F, 6), at, 1, 0, 0);
        level.playSound(null, user.blockPosition(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.4F, 2.0F);
        return mini;
    }

    // ---- 天輪砲『ジェネシス・ヘイロー』 ----

    /** 中心のクリスタルからのきらきらの光線。当てた数 */
    public static int genesisLaser(ServerLevel level, LivingEntity user) {
        double k = Overload.range(user); // オーバーロードの T4 は長く太い
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(LASER_RANGE * k)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 end = wall.getLocation();
        Vec3 start = GenesisHaloItem.crystal(user);
        int hits = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1.5 * k), e -> foe(user, e))) {
            if (e.getBoundingBox().inflate(0.6 * k).clip(start, end).isEmpty() && !e.getBoundingBox().inflate(0.6 * k).contains(start)) continue;
            hit(e, user, WeaponDamage.ENERGY, user, user, LASER_DAMAGE);
            Vec3 at = new Vec3(e.getX(), e.getY(0.55), e.getZ());
            Fx.star(level, at, 0xFFFFFF, 0.8F, 8, Vec3.ZERO);
            Fx.burst(level, Fx.opt(ModParticles.STAR.get(), GenesisHaloItem.GOLD, 0.25F, 12), at, 8, 0.3);
            hits++;
        }
        level.addFreshEntity(new T4StrikeEntity(level, T4StrikeEntity.LASER, start, end, 0.45F * (float) k, 0F, 8));
        Fx.mandala(level, start, GenesisHaloItem.GOLD, 0.6F, 8, true, false);
        Fx.star(level, start, 0xFFFFFF, 0.6F, 6, Vec3.ZERO);
        Fx.line(level, Fx.opt(ModParticles.STAR.get(), GenesisHaloItem.PALE, 0.18F, 12), start, end, 1.4);
        Fx.burst(level, Fx.opt(ModParticles.STAR.get(), 0xFFFFFF, 0.3F, 12), end, 10, 0.3);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), GenesisHaloItem.GOLD, 0.25F, 10), end, 8, 0.25);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 2.0F);
        level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F);
        return hits;
    }
}
