package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 追加の兵装 第 3 弾（2026-10-03、ユーザーの依頼「50 種類に。特殊効果多めに」でこちらが考えた）の T1 の 4 種。値はどれも【提案】。
 */
public final class Batch3T1 {

    private Batch3T1() {}

    /** 敵（持ち主と仲間以外の生き物） */
    static boolean foe(LivingEntity user, LivingEntity e) {
        return e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator();
    }

    /** 正面の扇（range・左右 halfAngle 度）の中か */
    static boolean inCone(LivingEntity user, LivingEntity e, double range, double halfAngle) {
        Vec3 to = new Vec3(e.getX(), e.getY(0.5), e.getZ()).subtract(user.getEyePosition());
        double d = to.length();
        return d <= range + e.getBbWidth() && d > 1.0E-3 && to.normalize().dot(user.getLookAngle()) >= Math.cos(Math.toRadians(halfAngle));
    }

    static Vec3 mid(LivingEntity e) {
        return new Vec3(e.getX(), e.getY(0.5), e.getZ());
    }

    // ================= ヴェノムスティンガー =================

    /**
     * ヴェノムスティンガー（T1）: 片手の毒の細剣。攻撃力 6 / 攻撃速度 2.0。当てると毒 II（4 秒）。
     * 右クリックで正面へ 6 ブロック突進し、通り道の敵（5 体まで）に 6・毒 II・弱体化（5 秒）。1 回 5,000 FE、間隔 1.5 秒。
     */
    public static class VenomStinger extends SkillWeaponItem {
        public static final int COLOR = 0x8AE060;
        public static final float LUNGE_DAMAGE = 20.0F;

        public VenomStinger(Properties p) {
            super(p, 80_000, 2.0F, false, 18.0, 2.0, 0, 5_000, 30);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
            if (attacker.level() instanceof ServerLevel level) Fx.burst(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.2F, 12), mid(target), 5, 0.15);
            return true;
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            lunge(level, user);
            return true;
        }

        /** 突進（兵姫に持たせたときも同じ処理）。当たった数 */
        public static int lunge(ServerLevel level, LivingEntity user) {
            Vec3 look = user.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.position().add(0, user.getBbHeight() * 0.5, 0), flat, 6.0, 1.2, 5);
            int hit = 0;
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                e.hurt(WeaponUtil.melee(user), LUNGE_DAMAGE);
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                Fx.burst(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.25F, 14), mid(e), 8, 0.2);
                hit++;
            }
            user.setDeltaMovement(flat.scale(1.6).add(0, 0.15, 0));
            user.hurtMarked = true;
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.2F, 10), user.position().add(0, 1, 0), user.position().add(0, 1, 0).add(flat.scale(6)), 0.4);
            level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.7F, 1.6F);
            return hit;
        }
    }

    // ================= フラッシュランチャー =================

    /**
     * フラッシュランチャー（T1）: 片手の閃光弾の発射器。撃った先（24 ブロック、最初の敵か壁）で弾けて、半径 5 の敵に
     * 4・盲目（5 秒）・鈍化 II（4 秒）・発光（10 秒、壁越しに見える）。1 発 6,000 FE、間隔 2 秒。
     */
    public static class FlashLauncher extends SkillWeaponItem {

        /** 弾の威力（説明に出す） */
        @Override
        public float[] shotDamage() {
            return new float[]{12.0F, 1};
        }

        public static final int COLOR = 0xFFFFE0;
        public static final double RADIUS = 5.0;

        public FlashLauncher(Properties p) {
            super(p, 90_000, 3.5F, false, 0, 0, 0, 6_000, 40);
        }

        @Override
        public float recoil() {
            return 0.5F;
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            flash(level, user);
            return true;
        }

        /** 閃光弾（兵姫に持たせたときも同じ処理）。浴びせた数 */
        public static int flash(ServerLevel level, LivingEntity user) {
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), 24.0, 0.6, 1);
            Vec3 at = ray.hits().isEmpty() ? ray.end() : mid(ray.hits().get(0));
            Fx.line(level, Fx.opt(ModParticles.SPARK.get(), 0xFFD070, 0.15F, 6), user.getEyePosition().add(user.getLookAngle()), at, 0.8);
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS), e -> foe(user, e) && e.distanceToSqr(at) <= RADIUS * RADIUS * 1.4)) {
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 12.0F);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
                n++;
            }
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 4.0F, 6), at, 1, 0, 0);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.35F, 10), at, 24, 0.5);
            Fx.shockwave(level, at, COLOR, (float) RADIUS, 10);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2F, 1.4F);
            return n;
        }
    }

    // ================= ゲイルファン =================

    /**
     * ゲイルファン（T1）: 片手の風の扇。攻撃力 4 / 攻撃速度 1.8。右クリックで正面の扇（10 ブロック・左右 35 度）に突風:
     * 敵を大きく吹き飛ばして 3、飛んでくる飛び道具は向きを反転して持ち主のものにする（撃ち返す）。1 回 4,000 FE、間隔 1 秒。
     */
    public static class GaleFan extends SkillWeaponItem {
        public static final int COLOR = 0xC8FFF0;
        public static final double RANGE = 10.0, HALF_ANGLE = 35.0;

        public GaleFan(Properties p) {
            super(p, 60_000, 1.5F, false, 12.0, 1.8, 0, 4_000, 20);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            gust(level, user);
            return true;
        }

        /** 突風（兵姫に持たせたときも同じ処理）。{吹き飛ばした数, 撃ち返した飛び道具の数} */
        public static int[] gust(ServerLevel level, LivingEntity user) {
            Vec3 look = user.getLookAngle();
            int mobs = 0, shots = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(RANGE), e -> foe(user, e) && inCone(user, e, RANGE, HALF_ANGLE))) {
                e.hurt(WeaponUtil.melee(user), 10.0F);
                e.setDeltaMovement(e.getDeltaMovement().add(look.x * 2.0, 0.5, look.z * 2.0));
                e.hurtMarked = true;
                mobs++;
            }
            double cos = Math.cos(Math.toRadians(HALF_ANGLE));
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, user.getBoundingBox().inflate(RANGE), p -> p.getOwner() != user)) {
                Vec3 to = p.position().subtract(user.getEyePosition());
                if (to.length() > RANGE || to.normalize().dot(look) < cos) continue;
                Vec3 v = p.getDeltaMovement();
                double speed = Math.max(0.8, v.length());
                p.setOwner(user);
                p.shoot(look.x, look.y, look.z, (float) speed * 1.2F, 0);
                shots++;
            }
            Vec3 eye = user.getEyePosition();
            for (int i = 0; i < 30; i++) {
                Vec3 dir = look.add(level.random.nextGaussian() * 0.3, level.random.nextGaussian() * 0.15, level.random.nextGaussian() * 0.3).normalize();
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.25F, 12), eye.add(look.scale(0.8)), dir.scale(0.9));
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, eye.x + look.x * 2, eye.y + look.y * 2, eye.z + look.z * 2, 16, 1, 0.5, 1, 0.3);
            level.playSound(null, user.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8F, 1.8F);
            return new int[]{mobs, shots};
        }
    }

    // ================= フロストナックル =================

    /**
     * フロストナックル（T1）: 片手の凍気の籠手。攻撃力 7 / 攻撃速度 1.8。当てるたびに凍傷を 1 段重ね、5 秒以内に 3 発当てると
     * 氷が砕けて 6 の追撃・鈍化 IV（2 秒）。右クリックで足元から凍気の波（半径 4 の敵に 4・凍傷 2 段）。1 回 3,000 FE、間隔 2 秒。
     */
    public static class FrostKnuckle extends SkillWeaponItem {
        public static final int COLOR = 0xA8E8FF;
        static final String TAG = "AutomataFrostHits";

        public FrostKnuckle(Properties p) {
            super(p, 60_000, 3.0F, false, 21.0, 1.8, 0, 3_000, 40);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (attacker.level() instanceof ServerLevel level) punch(level, attacker, target);
            return true;
        }

        /** 1 発当てた（兵姫に持たせたときも同じ処理）。重ねた後の数（砕けたら 0） */
        public static int punch(ServerLevel level, LivingEntity attacker, LivingEntity target) {
            Frostbite.apply(target);
            CompoundTag data = target.getPersistentData().getCompound(TAG);
            long now = level.getGameTime();
            int hits = now - data.getLong("Time") > 100 ? 1 : data.getInt("Hits") + 1;
            Vec3 at = mid(target);
            if (hits >= 3) {
                target.getPersistentData().remove(TAG);
                target.invulnerableTime = 0;
                target.hurt(WeaponUtil.melee(attacker), 18.0F);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
                Fx.burst(level, Fx.opt(ModParticles.HEX.get(), COLOR, 0.25F, 16), at, 14, 0.35);
                level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
                return 0;
            }
            data.putInt("Hits", hits);
            data.putLong("Time", now);
            target.getPersistentData().put(TAG, data);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.2F, 8), at, 4, 0.2);
            return hits;
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            nova(level, user);
            return true;
        }

        /** 凍気の波（兵姫に持たせたときも同じ処理）。当たった数 */
        public static int nova(ServerLevel level, LivingEntity user) {
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(4, 1.5, 4), e -> foe(user, e) && e.distanceTo(user) <= 4 + e.getBbWidth())) {
                e.hurt(WeaponUtil.melee(user), 12.0F);
                Frostbite.apply(e);
                Frostbite.apply(e);
                n++;
            }
            Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, 4.5F, 12);
            Fx.spray(level, Fx.opt(ModParticles.HEX.get(), COLOR, 0.2F, 20), user.position().add(0, 0.3, 0), 24, 2.0, 0.05);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, user.getX(), user.getY() + 0.5, user.getZ(), 40, 2, 0.3, 2, 0.05);
            level.playSound(null, user.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.2F, 0.8F);
            return n;
        }
    }
}
