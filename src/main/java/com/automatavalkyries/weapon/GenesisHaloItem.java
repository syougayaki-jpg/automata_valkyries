package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 天輪砲『ジェネシス・ヘイロー』（T4、追加の兵装。2026-10-03 こちらが考えた）: 両手の軌道砲撃の照準器。右クリック長押しで 5 秒チャージし、
 * 離すと狙った地点（256 ブロック先まで）へ天から光が降る。まず 12 本の光の柱が地点を囲む輪（半径 8）に降り、1 秒後に巨大な光の柱が 3 秒注ぐ。
 * 柱の中（半径 6、上下 12）の敵に 0.25 秒ごとにカンストダメージ 2,147,483,647（2026-10-03 ユーザー指定。無敵時間を消す。ダメージの種類 {@code orbital} は防具・エンチャント・状態異常・耐性を通らない）。
 * 撃った者には当たらない。1 射 8,000,000 FE。ブロックは壊さない。
 */
public class GenesisHaloItem extends ChargedWeaponItem {

    public static final int CAPACITY = 40_000_000, PER_SHOT = 8_000_000, CHARGE = 100;
    public static final int RING_PILLARS = 12, DELAY = 20, POUR = 60, PULSE = 5;
    /** カンストダメージ（int の上限）。保護対象（Eprotect のボス）には上限が掛かる */
    public static final long DAMAGE_VALUE = Integer.MAX_VALUE;
    public static final float DAMAGE = (float) DAMAGE_VALUE, WEIGHT = 20.0F;
    public static final double RANGE = 256.0, RING = 8.0, RADIUS = 6.0, HEIGHT = 60.0;
    public static final int GOLD = 0xFFC040, PALE = 0xFFF0B0, WHITE = 0xFFFFF0;

    public GenesisHaloItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, CHARGE, PER_SHOT);
    }

    @Override
    public float recoil() {
        return 0.8F;
    }

    /** 手に持つ知恵の輪の中心（ルミナルクリスタル）のおおよその位置: 持っている手の側、目の前で少し下 */
    public static Vec3 crystal(LivingEntity user) {
        Vec3 look = user.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        boolean rightHand = (user.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND) == (user.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        return user.getEyePosition().add(look.scale(0.8)).add(right.scale(rightHand ? 0.32 : -0.32)).add(up.scale(-0.3));
    }

    /** チャージの光は、手の中のクリスタルへ集まる（2026-10-04 ユーザー指定） */
    @Override
    protected Vec3 chargePoint(LivingEntity user) {
        return crystal(user);
    }

    @Override
    protected double chargeSpread() {
        return 1.6;
    }

    @Override
    public void onUseTick(net.minecraft.world.level.Level level, LivingEntity user, ItemStack stack, int remaining) {
        super.onUseTick(level, user, stack, remaining);
        if (!(level instanceof ServerLevel server) || !user.isUsingItem()) return;
        int t = elapsed(stack, remaining);
        if (t > chargeTicks) return;
        float f = t / (float) chargeTicks;
        Vec3 c = crystal(user);
        // クリスタルへ巻き込む 2 本の光の渦（溜まるほど速く、明るく）
        for (int arm = 0; arm < 2; arm++) {
            double a = t * (0.25 + 0.6 * f) + arm * Math.PI;
            double r = 0.9 - 0.3 * f;
            Vec3 p = c.add(Math.cos(a) * r, Math.sin(a * 0.7) * 0.25, Math.sin(a) * r);
            Fx.one(server, Fx.opt(ModParticles.GLOW.get(), arm == 0 ? GOLD : 0xC8F4FF, 0.1F + 0.08F * f, 6), p, c.subtract(p).scale(1 / 6.0));
        }
        if (t % 4 == 0) Fx.star(server, c, 0xFFFFFF, 0.2F + 0.35F * f, 6, Vec3.ZERO);
        if (t % 10 == 0) Fx.mandala(server, c, GOLD, 0.35F + 0.4F * f, 12, true, t % 20 == 0);
        if (t == chargeTicks) {
            Fx.burst(server, Fx.opt(ModParticles.STAR.get(), 0xFFFFFF, 0.4F, 14), c, 16, 0.25);
            Fx.mandala(server, c, 0xFFF4C0, 1.2F, 16, true, false);
        }
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return Fx.opt(ModParticles.GLOW.get(), GOLD, 0.3F, 10);
    }

    @Override
    protected void fire(ServerLevel level, LivingEntity user, ItemStack stack) {
        strike(level, user, aim(level, user));
    }

    /** 狙った地点（見ている先のブロック。届かなければ 256 ブロック先の真下の地面） */
    public static Vec3 aim(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        Vec3 end = eye;
        for (double d = 0; d < RANGE; d += 16) {
            Vec3 a = eye.add(look.scale(d)), b = eye.add(look.scale(Math.min(RANGE, d + 16)));
            if (!level.isLoaded(BlockPos.containing(b))) { end = a; break; }
            BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            if (hit.getType() != HitResult.Type.MISS) return hit.getLocation();
            end = b;
        }
        // 空を狙ったとき: その真下の地面
        BlockHitResult ground = level.clip(new ClipContext(end, end.add(0, -128, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return ground.getType() != HitResult.Type.MISS ? ground.getLocation() : end;
    }

    /** 軌道砲撃（兵姫に持たせたときも同じ処理） */
    public static void strike(ServerLevel level, LivingEntity user, Vec3 target) {
        // 1. 12 本の光の柱が輪に降る
        for (int i = 0; i < RING_PILLARS; i++) {
            double a = i * Math.PI * 2 / RING_PILLARS;
            Vec3 foot = target.add(Math.cos(a) * RING, 0, Math.sin(a) * RING);
            int delay = i * 1;
            WeaponUtil.later(level, delay, () -> {
                level.addFreshEntity(new PunishmentBeamEntity(level, foot.add(0, HEIGHT, 0), foot, PunishmentBeamEntity.SCHEME_GENESIS, 0.45F, DELAY + POUR - delay));
                Fx.shockwave(level, foot.add(0, 0.05, 0), PALE, 2.0F, 10);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), GOLD, 0.35F, 10), foot.add(0, 0.3, 0), 6, 0.3);
            });
        }
        Fx.shockwave(level, target.add(0, 0.05, 0), GOLD, (float) RING + 1, 30);
        level.playSound(null, BlockPos.containing(target), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0F, 0.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.5F);
        // 2. 1 秒後に巨大な光の柱が注ぐ
        WeaponUtil.later(level, DELAY, () -> {
            level.addFreshEntity(new PunishmentBeamEntity(level, target.add(0, HEIGHT + 20, 0), target, PunishmentBeamEntity.SCHEME_GENESIS, 4.5F, POUR + 10));
            level.playSound(null, BlockPos.containing(target), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0F, 0.5F);
            level.playSound(null, BlockPos.containing(target), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.6F);
            for (int p = 0; p < POUR; p += PULSE) {
                int step = p;
                WeaponUtil.later(level, p, () -> pour(level, user, target, step));
            }
        });
    }

    /** 光の柱の 1 回の当たり（0.25 秒ごと） */
    private static void pour(ServerLevel level, Entity owner, Vec3 target, int step) {
        LivingEntity causing = owner instanceof LivingEntity living ? living : null;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(target, target).inflate(RADIUS, 12, RADIUS),
                e -> e != owner && e.isAlive() && Math.hypot(e.getX() - target.x, e.getZ() - target.z) <= RADIUS + e.getBbWidth())) {
            victim.invulnerableTime = 0;
            victim.hurt(WeaponDamage.source(victim, WeaponDamage.ORBITAL, null, causing), DAMAGE);
            Fx.number(level, new Vec3(victim.getX(), victim.getY() + victim.getBbHeight() + 0.5, victim.getZ()), DAMAGE_VALUE, 0xFFD166);
        }
        Vec3 g = target.add(0, 0.05, 0);
        Fx.shockwave(level, g, step % 10 == 0 ? WHITE : GOLD, (float) RADIUS + 2, 10);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), PALE, 2.5F, 8), target.add(0, 1, 0), 2, RADIUS * 0.4, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), WHITE, 0.5F, 14), target.add(0, 0.5, 0), 16, 0.7);
        Fx.burst(level, Fx.opt(ModParticles.VOID_SHARD.get(), GOLD, 0.25F, 20), target.add(0, 0.3, 0), 10, 0.6);
    }
}
