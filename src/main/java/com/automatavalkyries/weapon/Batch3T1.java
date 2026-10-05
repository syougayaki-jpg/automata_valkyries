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
 * <p>4 種とも、オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）で技が専用の形に大きく変わる（各クラスの説明）</p>
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

    /** 点 p と線分 a-b の距離 */
    static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** 水平の向きで、正面（look）から左右 halfAngle 度の中・距離 [near, far] の帯の中か（ゲイルファンの風の壁） */
    static boolean inBand(LivingEntity user, LivingEntity e, Vec3 look, double near, double far, double halfAngle) {
        Vec3 to = mid(e).subtract(user.getEyePosition());
        double d = to.length();
        if (d < 1.0E-3 || d > far + e.getBbWidth() || d < near) return false;
        return to.normalize().dot(look) >= Math.cos(Math.toRadians(halfAngle));
    }

    // ================= ヴェノムスティンガー =================

    /**
     * ヴェノムスティンガー（T1）: 片手の毒の細剣。攻撃力 6 / 攻撃速度 2.0。当てると毒 II（4 秒）。
     * 右クリックで正面へ 6 ブロック突進し、通り道の敵（5 体まで）に 6・毒 II・弱体化（5 秒）。1 回 5,000 FE、間隔 1.5 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 大蛇の毒牙。突進の先へ、長さ 24・太さ 4 の毒の大蛇の牙が駆け抜け、
     * 通り道の敵 20 体までに同じダメージ・毒 II・弱体化。牙の通り道には毒の霧が 3 秒残り、0.5 秒ごとに霧の中（通り道から 2.5 ブロック）の敵に
     * 毒 II と鈍化を重ねる（ダメージは牙の 1 回だけ）。緑の大蛇がうねる 2 本の紅と金の筋を連れ、先端で大きな牙の斬撃が交差する</p>
     */
    public static class VenomStinger extends SkillWeaponItem {
        public static final int COLOR = 0x8AE060;
        public static final float LUNGE_DAMAGE = 20.0F;
        /** オーバーロード: 牙の長さ・太さ・当たる数・霧の残る間（Tick）・霧の太さ */
        public static final double OVER_LENGTH = 24.0, OVER_WIDTH = 4.0, OVER_MIST = 2.5;
        public static final int OVER_MAX = 20, OVER_MIST_TICKS = 60;

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
            if (OverloadArts.active(user)) return overLunge(level, user, flat);
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

        /** オーバーロードの突進: 長さ 24・太さ 4 の毒の大蛇の牙と、3 秒残る毒の霧。当たった数 */
        private static int overLunge(ServerLevel level, LivingEntity user, Vec3 flat) {
            Vec3 from = user.position().add(0, user.getBbHeight() * 0.5, 0);
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, from, flat, OVER_LENGTH, OVER_WIDTH, OVER_MAX);
            int hit = 0;
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                e.invulnerableTime = 0;
                e.hurt(WeaponUtil.melee(user), LUNGE_DAMAGE);
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                if (hit < 12) OverloadArts.impact(level, mid(e));
                hit++;
            }
            Vec3 to = ray.end();
            user.setDeltaMovement(flat.scale(2.4).add(0, 0.2, 0));
            user.hurtMarked = true;
            // 毒の大蛇: 太い緑の胴、うねる 2 本の紅と金の筋、先端で交差する大きな牙
            Vec3 side = new Vec3(-flat.z, 0, flat.x);
            double length = to.distanceTo(from);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.9F, 14), from, to, 0.5);
            for (double d = 0; d < length; d += 0.6) {
                double w = Math.sin(d * 0.6) * OVER_WIDTH * 0.45;
                Vec3 c = from.add(flat.scale(d));
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), Overload.CRIMSON, 0.4F, 12), c.add(side.scale(w)).add(0, 0.3, 0), Vec3.ZERO);
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), Overload.GOLD, 0.35F, 12), c.add(side.scale(-w)).add(0, -0.3, 0), Vec3.ZERO);
            }
            float yaw = (float) Math.atan2(flat.z, flat.x);
            Fx.slash(level, to, COLOR, 4.0F, 10, yaw + 0.8F);
            Fx.slash(level, to, Overload.CRIMSON, 4.0F, 10, yaw - 0.8F);
            Fx.burst(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.4F, 16), to, 24, 0.5);
            OverloadArts.flourish(level, user, 6.0F);
            level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.2F);
            level.playSound(null, user.blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 1.2F, 0.8F);
            // 3 秒残る毒の霧（0.5 秒ごと）
            for (int t = 10; t <= OVER_MIST_TICKS; t += 10) WeaponUtil.later(level, t, () -> mist(level, user, from, to));
            return hit;
        }

        /** 毒の霧の 1 回: 通り道から 2.5 ブロックの敵に毒 II と鈍化 */
        private static void mist(ServerLevel level, LivingEntity user, Vec3 from, Vec3 to) {
            Vec3 c = from.add(to).scale(0.5);
            double half = from.distanceTo(to) * 0.5 + OVER_MIST;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(half, OVER_MIST + 1, half),
                    e -> foe(user, e) && segmentDistance(mid(e), from, to) <= OVER_MIST + e.getBbWidth() * 0.5)) {
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            }
            Vec3 d = to.subtract(from);
            for (int i = 0; i < 24; i++) {
                Vec3 p = from.add(d.scale(level.random.nextDouble()));
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), i % 4 == 0 ? Overload.CRIMSON : 0x4E9A30, 0.6F, 20), p, 1, OVER_MIST * 0.5, 0.4, OVER_MIST * 0.5, 0.01);
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE, c.x, c.y, c.z, 6, half * 0.4, 0.3, half * 0.4, 0.01);
        }
    }

    // ================= フラッシュランチャー =================

    /**
     * フラッシュランチャー（T1）: 片手の閃光弾の発射器。撃った先（24 ブロック、最初の敵か壁）で弾けて、半径 5 の敵に
     * 4・盲目（5 秒）・鈍化 II（4 秒）・発光（10 秒、壁越しに見える）。1 発 6,000 FE、間隔 2 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 超新星の閃光。射程が 48 に伸び、弾けた所で半径 20 の超新星になる:
     * 半径 20 の敵すべてに同じダメージと盲目・鈍化 II・発光。白い大きな閃光の芯、白・金・紅の 4 重の衝撃波、半径 12 の輪に咲く 8 つの星の閃き、
     * 天へ昇る光の柱</p>
     */
    public static class FlashLauncher extends SkillWeaponItem {

        /** 弾の威力（説明に出す） */
        @Override
        public float[] shotDamage() {
            return new float[]{12.0F, 1};
        }

        public static final int COLOR = 0xFFFFE0;
        public static final double RADIUS = 5.0;
        /** オーバーロード: 射程・超新星の半径・輪に咲く星の数 */
        public static final double OVER_RANGE = 48.0, OVER_RADIUS = 20.0;
        public static final int OVER_STARS = 8;

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
            boolean over = OverloadArts.active(user);
            double radius = over ? OVER_RADIUS : RADIUS;
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), over ? OVER_RANGE : 24.0, 0.6, 1);
            Vec3 at = ray.hits().isEmpty() ? ray.end() : mid(ray.hits().get(0));
            Fx.line(level, Fx.opt(ModParticles.SPARK.get(), 0xFFD070, 0.15F, 6), user.getEyePosition().add(user.getLookAngle()), at, 0.8);
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> foe(user, e) && e.distanceToSqr(at) <= radius * radius * 1.4)) {
                if (over) {
                    e.invulnerableTime = 0;
                    if (n < 12) OverloadArts.impact(level, mid(e));
                }
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 12.0F);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
                n++;
            }
            if (over) {
                supernova(level, user, at);
                return n;
            }
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 4.0F, 6), at, 1, 0, 0);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.35F, 10), at, 24, 0.5);
            Fx.shockwave(level, at, COLOR, (float) RADIUS, 10);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2F, 1.4F);
            return n;
        }

        /** オーバーロードの超新星の見た目（当たりは flash で半径 20） */
        private static void supernova(ServerLevel level, LivingEntity user, Vec3 at) {
            float r = (float) OVER_RADIUS;
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 14.0F, 8), at, 1, 0, 0);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), Overload.GOLD, 8.0F, 12), at, 1, 0, 0);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.5F, 16), at, 60, 1.4);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.CRIMSON, 0.4F, 14), at, 30, 1.0);
            Fx.shockwave(level, at, 0xFFFFFF, r, 12);
            Fx.shockwave(level, at, Overload.GOLD, r * 0.8F, 16);
            Fx.shockwave(level, at, Overload.CRIMSON, r * 1.1F, 20);
            Fx.shockwave(level, at.add(0, 1.0, 0), COLOR, r * 0.5F, 14);
            Fx.beamRing(level, at, new Vec3(0, 1, 0), COLOR, r, 12);
            for (int k = 0; k < OVER_STARS; k++) {
                double a = k * Math.PI * 2 / OVER_STARS;
                Vec3 p = at.add(Math.cos(a) * 12, 1.0, Math.sin(a) * 12);
                Fx.star(level, p, k % 2 == 0 ? COLOR : Overload.GOLD, 2.5F, 16, Vec3.ZERO);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), k % 2 == 0 ? COLOR : Overload.CRIMSON, 0.3F, 10), p, 6, 0.4);
            }
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.8F, 16), at, at.add(0, 24, 0), 0.6);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.GOLD, 1.4F, 12), at, at.add(0, 24, 0), 1.5);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH, at.x, at.y, at.z, 3, 1, 1, 1, 0);
            OverloadArts.flourish(level, user, 6.0F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 3.0F, 0.8F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, SoundSource.PLAYERS, 2.0F, 1.0F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 1.2F);
        }
    }

    // ================= ゲイルファン =================

    /**
     * ゲイルファン（T1）: 片手の風の扇。攻撃力 4 / 攻撃速度 1.8。右クリックで正面の扇（10 ブロック・左右 35 度）に突風:
     * 敵を大きく吹き飛ばして 3、飛んでくる飛び道具は向きを反転して持ち主のものにする（撃ち返す）。1 回 4,000 FE、間隔 1 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 三重の暴風壁。突風が正面の扇（30 ブロック・左右 60 度）を、
     * 0.15 秒おきに押し寄せる 3 枚の風の壁（0〜10・10〜20・20〜30 ブロック）で薙ぎ払う。壁が通った帯の敵に同じダメージを与えて 2 倍強く吹き飛ばし、
     * 扇の中の飛び道具はすべて撃ち返す。風の壁は白緑の三日月の列と雲の帯、紅と金の筋で見える</p>
     */
    public static class GaleFan extends SkillWeaponItem {
        public static final int COLOR = 0xC8FFF0;
        public static final double RANGE = 10.0, HALF_ANGLE = 35.0;
        /** オーバーロード: 扇の距離・左右の角度・風の壁の数・壁の間隔（Tick） */
        public static final double OVER_RANGE = 30.0, OVER_HALF_ANGLE = 60.0;
        public static final int OVER_WAVES = 3, OVER_WAVE_DELAY = 3;

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
            if (OverloadArts.active(user)) return overGust(level, user);
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

        /** オーバーロード: 扇の飛び道具をすべて撃ち返し、3 枚の風の壁を順に押し寄せさせる。{最初の壁で吹き飛ばした数, 撃ち返した数} */
        private static int[] overGust(ServerLevel level, LivingEntity user) {
            Vec3 look = user.getLookAngle();
            int shots = 0;
            double cos = Math.cos(Math.toRadians(OVER_HALF_ANGLE));
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, user.getBoundingBox().inflate(OVER_RANGE), p -> p.getOwner() != user)) {
                Vec3 to = p.position().subtract(user.getEyePosition());
                if (to.length() > OVER_RANGE || to.length() < 1.0E-3 || to.normalize().dot(look) < cos) continue;
                double speed = Math.max(0.8, p.getDeltaMovement().length());
                p.setOwner(user);
                p.shoot(look.x, look.y, look.z, (float) speed * 1.5F, 0);
                shots++;
            }
            OverloadArts.flourish(level, user, 8.0F);
            level.playSound(null, user.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.5F, 1.2F);
            int first = wall(level, user, look, 0);
            for (int w = 1; w < OVER_WAVES; w++) {
                int wave = w;
                WeaponUtil.later(level, w * OVER_WAVE_DELAY, () -> {
                    if (user.isAlive() && user.level() == level) wall(level, user, look, wave);
                });
            }
            return new int[]{first, shots};
        }

        /** 風の壁の 1 枚: 帯（wave×10〜(wave+1)×10 ブロック）の敵を斬って 2 倍強く吹き飛ばす。当たった数 */
        private static int wall(ServerLevel level, LivingEntity user, Vec3 look, int wave) {
            double band = OVER_RANGE / OVER_WAVES;
            double near = wave * band, far = (wave + 1) * band;
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(far),
                    e -> foe(user, e) && inBand(user, e, look, near, far, OVER_HALF_ANGLE))) {
                e.invulnerableTime = 0;
                e.hurt(WeaponUtil.melee(user), 10.0F);
                e.setDeltaMovement(e.getDeltaMovement().add(look.x * 4.0, 0.9, look.z * 4.0));
                e.hurtMarked = true;
                if (n++ < 8) OverloadArts.impact(level, mid(e));
            }
            // 風の壁: 帯の外の縁に沿って並ぶ三日月と雲、紅と金の筋
            Vec3 eye = user.getEyePosition();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            Vec3 right = new Vec3(-flat.z, 0, flat.x);
            int steps = 10 + wave * 6;
            for (int i = 0; i <= steps; i++) {
                double a = Math.toRadians(-OVER_HALF_ANGLE + 2 * OVER_HALF_ANGLE * i / steps);
                Vec3 dir = flat.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
                Vec3 p = eye.add(dir.scale(far - band * 0.3)).add(0, -0.4, 0);
                Fx.slash(level, p, i % 4 == 0 ? Overload.GOLD : COLOR, 1.4F + wave * 0.4F, 7, (float) (Math.atan2(dir.z, dir.x) + Math.PI / 2));
                for (int k = 0; k < 3; k++) {
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), k == 1 ? Overload.CRIMSON : COLOR, 0.35F, 12), p.add(0, k * 1.2 - 0.6, 0), dir.scale(0.6));
                }
                if (i % 3 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, p.x, p.y, p.z, 3, 0.6, 0.8, 0.6, 0.15);
            }
            level.playSound(null, user.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.9F, 1.6F - wave * 0.2F);
            level.playSound(null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.7F);
            return n;
        }
    }

    // ================= フロストナックル =================

    /**
     * フロストナックル（T1）: 片手の凍気の籠手。攻撃力 7 / 攻撃速度 1.8。当てるたびに凍傷を 1 段重ね、5 秒以内に 3 発当てると
     * 氷が砕けて 6 の追撃・鈍化 IV（2 秒）。右クリックで足元から凍気の波（半径 4 の敵に 4・凍傷 2 段）。1 回 3,000 FE、間隔 2 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 氷河の拳。凍気の波が 0.2 秒おきに 3 重（半径 6・11・16）に広がり、
     * 波が通った帯の敵に同じダメージと凍傷 2 段（輪に沿って氷の柱が突き立つ）。3 発目で砕ける氷は半径 6 の氷の炸裂になり、まわりの敵にも
     * 同じ追撃と鈍化 IV</p>
     */
    public static class FrostKnuckle extends SkillWeaponItem {
        public static final int COLOR = 0xA8E8FF;
        /** オーバーロード: 3 重の波の半径・波の間隔（Tick）・氷の柱の数・砕けた氷の炸裂の半径 */
        public static final double[] OVER_WAVES = {6.0, 11.0, 16.0};
        public static final int OVER_WAVE_DELAY = 4, OVER_SPIKES = 12;
        public static final double OVER_SHATTER = 6.0;
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
                if (OverloadArts.active(attacker)) shatter(level, attacker, target, at);
                return 0;
            }
            data.putInt("Hits", hits);
            data.putLong("Time", now);
            target.getPersistentData().put(TAG, data);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.2F, 8), at, 4, 0.2);
            return hits;
        }

        /** オーバーロードの砕けた氷: 半径 6 の敵（砕いた相手は除く）にも同じ追撃と鈍化 IV */
        private static void shatter(ServerLevel level, LivingEntity attacker, LivingEntity target, Vec3 at) {
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(OVER_SHATTER),
                    e -> e != target && foe(attacker, e) && e.distanceTo(target) <= OVER_SHATTER + e.getBbWidth())) {
                e.invulnerableTime = 0;
                e.hurt(WeaponUtil.melee(attacker), 18.0F);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
                if (n++ < 8) OverloadArts.impact(level, mid(e));
            }
            Fx.shockwave(level, target.position().add(0, 0.05, 0), COLOR, (float) OVER_SHATTER, 12);
            Fx.shockwave(level, target.position().add(0, 0.1, 0), Overload.CRIMSON, (float) OVER_SHATTER * 0.7F, 14);
            Fx.burst(level, Fx.opt(ModParticles.HEX.get(), COLOR, 0.4F, 20), at, 40, 0.8);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.GOLD, 0.3F, 12), at, 16, 0.6);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 2.5F, 6), at, 1, 0, 0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 50, OVER_SHATTER * 0.4, 1, OVER_SHATTER * 0.4, 0.1);
            OverloadArts.flourish(level, attacker, (float) OVER_SHATTER);
            level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F);
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5F, 0.8F);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            nova(level, user);
            return true;
        }

        /** 凍気の波（兵姫に持たせたときも同じ処理）。当たった数 */
        public static int nova(ServerLevel level, LivingEntity user) {
            if (OverloadArts.active(user)) return overNova(level, user);
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

        /** オーバーロード: 0.2 秒おきに 3 重（半径 6・11・16）に広がる凍気の波。最初の波で当たった数 */
        private static int overNova(ServerLevel level, LivingEntity user) {
            OverloadArts.flourish(level, user, (float) OVER_WAVES[OVER_WAVES.length - 1]);
            Vec3 c = user.position();
            int first = wave(level, user, c, 0);
            for (int w = 1; w < OVER_WAVES.length; w++) {
                int index = w;
                WeaponUtil.later(level, w * OVER_WAVE_DELAY, () -> {
                    if (user.isAlive() && user.level() == level) wave(level, user, c, index);
                });
            }
            return first;
        }

        /** 凍気の波の 1 重: 前の輪から今の輪までの帯の敵に同じダメージと凍傷 2 段、輪に沿って氷の柱。当たった数 */
        private static int wave(ServerLevel level, LivingEntity user, Vec3 c, int index) {
            double outer = OVER_WAVES[index], inner = index == 0 ? 0 : OVER_WAVES[index - 1];
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(outer, 3, outer), e -> {
                if (!foe(user, e)) return false;
                double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                return d <= outer + e.getBbWidth() && d >= inner;
            })) {
                e.invulnerableTime = 0;
                e.hurt(WeaponUtil.melee(user), 12.0F);
                Frostbite.apply(e);
                Frostbite.apply(e);
                if (n++ < 10) OverloadArts.impact(level, mid(e));
            }
            Vec3 ground = c.add(0, 0.05, 0);
            Fx.shockwave(level, ground, COLOR, (float) outer, 14);
            Fx.shockwave(level, ground.add(0, 0.1, 0), index % 2 == 0 ? Overload.CRIMSON : Overload.GOLD, (float) outer * 0.9F, 16);
            int spikes = OVER_SPIKES + index * 4;
            for (int k = 0; k < spikes; k++) {
                double a = k * Math.PI * 2 / spikes + index * 0.3;
                Vec3 base = c.add(Math.cos(a) * outer, 0, Math.sin(a) * outer);
                double h = 2.0 + index;
                Fx.line(level, Fx.opt(ModParticles.HEX.get(), k % 3 == 0 ? 0xFFFFFF : COLOR, 0.35F, 20), base, base.add(0, h, 0), 0.5);
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, c.x, c.y + 0.8, c.z, 60, outer * 0.6, 0.5, outer * 0.6, 0.05);
            level.playSound(null, user.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F + index * 0.15F);
            level.playSound(null, user.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 0.5F + index * 0.2F);
            return n;
        }
    }
}
