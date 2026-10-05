package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.automatavalkyries.weapon.Batch3T1.foe;
import static com.automatavalkyries.weapon.Batch3T1.mid;

/**
 * 追加の兵装 第 3 弾（2026-10-03、ユーザーの依頼「50 種類に。特殊効果多めに」でこちらが考えた）の T2 の 7 種。値はどれも【提案】。
 * どの兵装にもオーバーロードでの技の変化がある（{@link OverloadArts}、各クラスの説明）。
 */
public final class Batch3T2 {

    private Batch3T2() {}

    // ================= クリムゾンシックル =================

    /**
     * クリムゾンシックル（T2）: 片手の血の鎌。攻撃力 12 / 攻撃速度 1.4。当てるたびに 3.5 回復（吸血）。
     * 右クリックで血の波動: 半径 6 の敵に 6（無敵時間を無視）・衰弱 II（4 秒）、吸った分だけ回復（1 体 3、最大 15）。1 回 25,000 FE、間隔 4 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 血の波動が「血の満月」に変わる。頭上に紅い満月が昇り、
     * 半径 18（3 倍）の中の敵すべてから血の筋を吸い上げ、地面に半径 10・18 の 2 重の血の刃の輪（16＋24 枚）が回る。当たりも半径 18</p>
     */
    public static class CrimsonSickle extends SkillWeaponItem {
        public static final int COLOR = 0xD02040, DARK = 0x500818;
        public static final float LEECH = 3.5F;
        /** オーバーロードの血の満月の半径 */
        public static final double RADIUS = 6.0, OVER_RADIUS = 18.0;

        public CrimsonSickle(Properties p) {
            super(p, 500_000, 4.0F, false, 60.0, 1.4, 0, 25_000, 80);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            attacker.heal(LEECH);
            if (attacker.level() instanceof ServerLevel level) {
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.15F, 10), mid(target), mid(attacker), 0.4);
            }
            return true;
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            drain(level, user);
            return true;
        }

        /** 血の波動（兵姫に持たせたときも同じ処理）。吸った数 */
        public static int drain(ServerLevel level, LivingEntity user) {
            boolean over = OverloadArts.active(user);
            double radius = over ? OVER_RADIUS : RADIUS;
            Vec3 moon = mid(user).add(0, 4.5, 0);
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius), e -> foe(user, e) && e.distanceTo(user) <= radius + e.getBbWidth())) {
                e.invulnerableTime = 0;
                e.hurt(WeaponUtil.melee(user), 30.0F);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                if (!over) {
                    for (int i = 0; i < 6; i++) {
                        Vec3 from = mid(e).add((level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6);
                        Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.25F, 12), from, mid(user).subtract(from).scale(1 / 12.0));
                    }
                } else if (n < 16) { // 血の筋: 敵から頭上の満月へ吸い上げる（見せるのは 16 体まで）
                    Fx.line(level, Fx.opt(ModParticles.GLOW.get(), n % 2 == 0 ? COLOR : Overload.CRIMSON, 0.3F, 14), mid(e), moon, 0.7);
                    for (int i = 0; i < 4; i++) {
                        Vec3 from = mid(e).add((level.random.nextDouble() - 0.5) * 0.8, (level.random.nextDouble() - 0.5) * 0.8, (level.random.nextDouble() - 0.5) * 0.8);
                        Fx.one(level, Fx.opt(ModParticles.GLOW.get(), DARK, 0.35F, 16), from, moon.subtract(from).scale(1 / 16.0));
                    }
                    if (n < 10) OverloadArts.impact(level, mid(e));
                }
                n++;
            }
            user.heal(Math.min(15F, 3F * n));
            if (over) {
                bloodMoon(level, user, moon);
            } else {
                Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, 6.5F, 14);
                Fx.shockwave(level, user.position().add(0, 0.1, 0), DARK, 4.0F, 18);
            }
            level.playSound(null, user.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, over ? 1.0F : 0.6F, over ? 0.7F : 1.4F);
            return n;
        }

        /** オーバーロードの血の満月: 頭上の紅い満月、半径 10・18 の 2 重の血の刃の輪、半径 18 の衝撃波 */
        private static void bloodMoon(ServerLevel level, LivingEntity user, Vec3 moon) {
            Vec3 feet = user.position().add(0, 0.05, 0);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 4.0F, 30), moon, 1, 0, 0);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), Overload.HOT, 2.0F, 24), moon, 1, 0, 0);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.CRIMSON, 0.4F, 20), moon, 24, 0.5);
            Fx.mandala(level, moon, Overload.CRIMSON, 3.5F, 30, true, true);
            double spin = level.random.nextDouble() * Math.PI;
            for (int ring = 0; ring < 2; ring++) {
                double r = ring == 0 ? 10.0 : OVER_RADIUS;
                int blades = ring == 0 ? 16 : 24;
                for (int k = 0; k < blades; k++) {
                    double a = spin + k * Math.PI * 2 / blades;
                    Vec3 p = feet.add(Math.cos(a) * r, 0.6 + ring * 0.4, Math.sin(a) * r);
                    Fx.slash(level, p, k % 3 == 0 ? Overload.GOLD : COLOR, 1.4F + ring * 0.5F, 12, (float) (a + Math.PI / 2));
                }
            }
            Fx.shockwave(level, feet, COLOR, (float) OVER_RADIUS + 0.5F, 18);
            Fx.shockwave(level, feet.add(0, 0.05, 0), DARK, 10.0F, 22);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), DARK, 0.4F, 26), feet.add(0, 1.0, 0), 40, OVER_RADIUS * 0.55, 0.6, OVER_RADIUS * 0.55, 0.03);
            OverloadArts.flourish(level, user, (float) OVER_RADIUS);
            WeaponUtil.later(level, 6, () -> Fx.shockwave(level, feet, Overload.CRIMSON, (float) OVER_RADIUS, 14));
            level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.6F);
        }
    }

    // ================= レビテーションロッド =================

    /**
     * レビテーションロッド（T2）: 両手の重力反転の杖。右クリックで半径 8 の敵を浮かせ（浮遊 III 3 秒・6）、
     * 3 秒後にまとめて地面へ叩き落とす（8・下向きに強く）。1 回 30,000 FE、間隔 6 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「重力崩落」に変わる。半径 24（3 倍）の敵をまとめて浮かせ、
     * 足元に半径 10 の重力の陣と昇る光の柱が立ち、浮いた敵の下に光の輪が回る。3 秒後、空から紫と紅の光の柱が浮いた敵それぞれに落ち、
     * 落ちた所と持ち主のまわり（半径 24）に衝撃波が広がる。当たりも半径 24</p>
     */
    public static class LevitationRod extends SkillWeaponItem {
        public static final int COLOR = 0xB890FF, LIFT = 60;
        public static final double RADIUS = 8.0, OVER_RADIUS = 24.0;

        public LevitationRod(Properties p) {
            super(p, 600_000, 5.0F, true, 0, 0, 0, 30_000, 120);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            levitate(level, user);
            return true;
        }

        /** 浮かせる（兵姫に持たせたときも同じ処理）。浮かせた相手 */
        public static List<LivingEntity> levitate(ServerLevel level, LivingEntity user) {
            boolean over = OverloadArts.active(user);
            double radius = over ? OVER_RADIUS : RADIUS;
            List<LivingEntity> lifted = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius), e -> foe(user, e) && e.distanceTo(user) <= radius + e.getBbWidth());
            int shown = 0;
            for (LivingEntity e : lifted) {
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 50.0F);
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LIFT, 2));
                if (!over) {
                    Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.25F, 20), e.position(), 8, 0.4, 0.02);
                } else if (shown++ < 16) { // 浮いた敵の下で回る光の輪（見せるのは 16 体まで）
                    Fx.beamRing(level, e.position().add(0, 0.2, 0), new Vec3(0, 1, 0), shown % 2 == 0 ? COLOR : Overload.GOLD, 1.2F + e.getBbWidth(), 24);
                    Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.35F, 24), e.position(), 6, 0.5, 0.05);
                }
            }
            if (over) {
                Vec3 feet = user.position().add(0, 0.05, 0);
                Fx.shockwave(level, feet, COLOR, (float) OVER_RADIUS + 0.5F, 20);
                Fx.mandala(level, feet.add(0, 0.03, 0), COLOR, 10.0F, LIFT, false, false);
                // 重力の反転: 半径 24 の中で光が空へ昇る
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.35F, 30), feet.add(0, 1.0, 0), 60, OVER_RADIUS * 0.55, 0.5, OVER_RADIUS * 0.55, 0.0);
                Fx.spray(level, Fx.opt(ModParticles.HEX.get(), Overload.GOLD, 0.2F, 30), feet.add(0, 1.5, 0), 20, OVER_RADIUS * 0.5, 1.0, OVER_RADIUS * 0.5, 0.0);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.8F, 30), feet, feet.add(0, 16, 0), 0.6);
                OverloadArts.flourish(level, user, (float) OVER_RADIUS);
            } else {
                Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, 8.5F, 16);
            }
            level.playSound(null, user.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1.0F, over ? 0.4F : 0.6F);
            WeaponUtil.later(level, LIFT, () -> {
                int drawn = 0;
                for (LivingEntity e : lifted) {
                    if (!e.isAlive()) continue;
                    e.removeEffect(MobEffects.LEVITATION);
                    e.setDeltaMovement(0, -3.0, 0);
                    e.hurtMarked = true;
                    e.invulnerableTime = 0;
                    e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 65.0F);
                    if (!over) {
                        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.3F, 8), mid(e).add(0, 3, 0), mid(e), 0.3);
                    } else if (drawn++ < 16) { // 空から落ちる光の柱と、落ちた所の衝撃波
                        Vec3 at = mid(e);
                        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.9F, 10), at.add(0, 12, 0), at, 0.6);
                        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.CRIMSON, 0.4F, 12), at.add(0, 12, 0), at, 0.9);
                        Fx.shockwave(level, e.position().add(0, 0.05, 0), drawn % 2 == 0 ? COLOR : Overload.GOLD, 3.0F + e.getBbWidth(), 12);
                        OverloadArts.impact(level, at);
                    }
                }
                if (over) {
                    Vec3 feet = user.position().add(0, 0.05, 0);
                    Fx.shockwave(level, feet, COLOR, (float) OVER_RADIUS, 16);
                    Fx.shockwave(level, feet, Overload.CRIMSON, (float) OVER_RADIUS * 0.7F, 20);
                    level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.6F);
                }
                level.playSound(null, user.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);
            });
            return lifted;
        }
    }

    // ================= リコシェライフル =================

    /**
     * リコシェライフル（T2）: 両手の跳弾銃。撃った弾（射程 40）が最初の敵から 10 ブロック以内の敵へ次々に跳ね（6 体まで）、
     * 跳ねるたびに 2 割ずつ強くなる（10 → 12 → 14.4 …、無敵時間を無視）。1 射 20,000 FE、間隔 1 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「金色の跳弾嵐」に変わる。弾は太く（幅 1.5）、射程 60、
     * 跳ねる先を 20 ブロック以内から探して 24 体まで跳ね回り、紅と金の太い弾道が網のように残る。跳ねた所ごとに斬撃の閃き、最後の相手で星が弾ける。
     * 強まりは通常の 6 体目までで止める（ダメージの値は変えない）</p>
     */
    public static class RicochetRifle extends SkillWeaponItem {

        /** 弾の威力（説明に出す） */
        @Override
        public float[] shotDamage() {
            return new float[]{60.0F, 1};
        }

        public static final int COLOR = 0xFFB040, BOUNCES = 6, OVER_BOUNCES = 24;
        public static final double BOUNCE_RANGE = 10.0, OVER_BOUNCE_RANGE = 20.0;

        public RicochetRifle(Properties p) {
            super(p, 500_000, 7.0F, true, 0, 0, 0, 20_000, 20);
        }

        @Override
        public float recoil() {
            return 0.6F;
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            ricochet(level, user);
            return true;
        }

        /** 撃つ（兵姫に持たせたときも同じ処理）。当てた数 */
        public static int ricochet(ServerLevel level, LivingEntity user) {
            boolean over = OverloadArts.active(user);
            Vec3 muzzle = user.getEyePosition().add(user.getLookAngle().scale(1.0)).add(0, -0.2, 0);
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), over ? 60 : 40, over ? 1.5 : 0.6, 1);
            level.playSound(null, user.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, over ? 1.0F : 1.6F);
            if (over) {
                OverloadArts.flourish(level, user, 6.0F);
                Fx.beamRing(level, muzzle, user.getLookAngle(), Overload.GOLD, 1.5F, 10);
            }
            if (ray.hits().isEmpty()) {
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, over ? 0.4F : 0.15F, 6), muzzle, ray.end(), 0.5);
                return 0;
            }
            int bounces = over ? OVER_BOUNCES : BOUNCES;
            double bounceRange = over ? OVER_BOUNCE_RANGE : BOUNCE_RANGE;
            List<LivingEntity> hit = new ArrayList<>();
            LivingEntity cur = ray.hits().get(0);
            Vec3 from = muzzle;
            float damage = 60.0F;
            while (cur != null && hit.size() < bounces) {
                hit.add(cur);
                if (over) { // 紅と金の太い弾道
                    Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.HOT, 0.25F, 14), from, mid(cur), 0.6);
                    Fx.line(level, Fx.opt(ModParticles.GLOW.get(), hit.size() % 2 == 0 ? Overload.CRIMSON : Overload.GOLD, 0.5F, 18), from, mid(cur), 0.8);
                    Fx.slash(level, mid(cur), COLOR, 1.3F, 6, level.random.nextFloat() * 6.28F);
                    if (hit.size() <= 12) OverloadArts.impact(level, mid(cur));
                } else {
                    Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.1F, 6), from, mid(cur), 0.4);
                    Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.18F, 8), from, mid(cur), 0.6);
                }
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 8), mid(cur), 5, 0.25);
                cur.invulnerableTime = 0;
                cur.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), damage);
                if (hit.size() < BOUNCES) damage *= 1.2F; // 強まりは通常の跳ねる数までで止める
                from = mid(cur);
                LivingEntity last = cur;
                cur = level.getEntitiesOfClass(LivingEntity.class, last.getBoundingBox().inflate(bounceRange), e -> foe(user, e) && !hit.contains(e) && e instanceof net.minecraft.world.entity.monster.Enemy)
                        .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(last))).orElse(null);
            }
            if (over) { // 最後の相手で星が弾ける
                Fx.star(level, from, Overload.GOLD, 1.6F, 14, Vec3.ZERO);
                Fx.burst(level, Fx.opt(ModParticles.STAR.get(), COLOR, 0.4F, 14), from, 12, 0.5);
                Fx.shockwave(level, from.add(0, -0.8, 0), Overload.GOLD, 4.0F, 12);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
            return hit.size();
        }
    }

    // ================= スペクトラルチェイン =================

    /**
     * スペクトラルチェイン（T2）: 片手の霊鎖。攻撃力 7 / 攻撃速度 1.6。右クリックで見ている敵（20 ブロック）へ鎖を撃ち込み、
     * 相手へ一気に飛びつく（10・鈍化 V 1.5 秒・弱体化 3 秒）。相手がいなければ使わない。1 回 15,000 FE、間隔 2 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「霊鎖の檻」に変わる。鎖は 40 ブロック先まで届き（太さ 2.4）、
     * 狙った敵に加えて持ち主から 24 ブロック以内の敵 7 体へも紅と金の霊鎖が同時に撃ち込まれ、全員が狙った敵のもとへ引き寄せられる。
     * 狙った敵のまわりに半径 4 の光の輪の檻と陣が立つ。鎖が当たった敵はそれぞれ同じダメージ・鈍化・弱体化を受ける</p>
     */
    public static class SpectralChain extends SkillWeaponItem {
        public static final int COLOR = 0x80E0FF, OVER_EXTRA = 7;
        public static final double RANGE = 20.0, OVER_RANGE = 40.0, OVER_SPREAD = 24.0;

        public SpectralChain(Properties p) {
            super(p, 400_000, 3.0F, false, 50.0, 1.6, 0, 15_000, 40);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            return hook(level, user) != null;
        }

        /** 鎖を撃ち込んで飛びつく（兵姫に持たせたときも同じ処理）。捕らえた相手（いなければ null） */
        public static @Nullable LivingEntity hook(ServerLevel level, LivingEntity user) {
            boolean over = OverloadArts.active(user);
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), over ? OVER_RANGE : RANGE, over ? 2.4 : 1.2, 1);
            if (ray.hits().isEmpty() || !foe(user, ray.hits().get(0))) return null;
            LivingEntity target = ray.hits().get(0);
            if (over) cage(level, user, target);
            target.hurt(WeaponUtil.melee(user), 60.0F);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            Vec3 to = target.position().subtract(user.position());
            double d = to.length();
            Vec3 pull = to.normalize().scale(Math.min(3.0, d * 0.22));
            user.setDeltaMovement(pull.x, Math.max(0.35, pull.y + 0.3), pull.z);
            user.hurtMarked = true;
            user.resetFallDistance();
            // 鎖: 輪がつながった光の線
            Vec3 a = user.getEyePosition().add(0, -0.3, 0), b = mid(target);
            for (double t = 0; t < 1; t += 0.4 / Math.max(1, d)) {
                Vec3 p = a.add(b.subtract(a).scale(t));
                Fx.one(level, Fx.opt(ModParticles.HEX.get(), COLOR, 0.12F, 10), p, Vec3.ZERO);
            }
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), b, 8, 0.3);
            level.playSound(null, user.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
            return target;
        }

        /** オーバーロードの霊鎖の檻: 持ち主から 24 ブロック以内の敵 7 体へも鎖を撃ち込み、狙った敵のもとへ引き寄せる */
        private static void cage(ServerLevel level, LivingEntity user, LivingEntity target) {
            Vec3 a = user.getEyePosition().add(0, -0.3, 0), center = mid(target);
            List<LivingEntity> others = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_SPREAD),
                    e -> foe(user, e) && e != target && e.distanceTo(user) <= OVER_SPREAD + e.getBbWidth()));
            others.sort(Comparator.comparingDouble(e -> e.distanceToSqr(user)));
            int k = 0;
            for (LivingEntity e : others) {
                if (k >= OVER_EXTRA) break;
                k++;
                e.invulnerableTime = 0;
                e.hurt(WeaponUtil.melee(user), 60.0F);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                Vec3 to = center.subtract(mid(e));
                double d = to.length();
                if (d > 1.5) {
                    Vec3 pull = to.normalize().scale(Math.min(2.5, d * 0.25));
                    e.setDeltaMovement(pull.x, Math.max(0.3, pull.y + 0.25), pull.z);
                    e.hurtMarked = true;
                }
                // 紅と金の霊鎖（持ち主から敵へ、敵から狙った敵へ）
                Vec3 b = mid(e);
                double len = a.distanceTo(b);
                Fx.line(level, Fx.opt(ModParticles.HEX.get(), k % 2 == 0 ? Overload.GOLD : Overload.CRIMSON, 0.2F, 16), a, b, Math.max(0.6, len / 40));
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.25F, 16), b, center, Math.max(0.8, d / 20));
                OverloadArts.impact(level, b);
            }
            // 狙った敵の檻: 縦の光の輪と足元の陣
            Fx.mandala(level, target.position().add(0, 0.05, 0), COLOR, 4.0F, 30, false, true);
            Fx.shockwave(level, target.position().add(0, 0.05, 0), Overload.CRIMSON, 5.0F, 16);
            for (int i = 0; i < 4; i++) {
                double ang = i * Math.PI / 4;
                Fx.beamRing(level, center, new Vec3(Math.cos(ang), 0, Math.sin(ang)), i % 2 == 0 ? COLOR : Overload.GOLD, 4.0F, 20);
            }
            Fx.line(level, Fx.opt(ModParticles.HEX.get(), Overload.GOLD, 0.25F, 16), a, center, Math.max(0.5, a.distanceTo(center) / 60));
            OverloadArts.flourish(level, user, 8.0F);
            level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
        }
    }

    // ================= スタティックトラップ =================

    /**
     * スタティックトラップ（T2）: 片手の罠の投射器。狙った所（16 ブロック、最初の敵か壁）に電撃の罠を 20 秒置く。
     * 敵が 2 ブロック以内に入ると炸裂し、半径 3 の敵に 14 の電撃・鈍化 III（3 秒）。1 個 18,000 FE、間隔 1.5 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「雷陣」に変わる。置いた罠は半径 6 の金と青の雷の陣になり（目印も半径 6 の輪と陣）、
     * 敵が 6 ブロック以内に入ると落雷とともに炸裂して、半径 12（4 倍）の敵すべてを撃ち（稲妻を描くのは 8 体まで）、続けて 8 本の稲妻が放射状に地を這う</p>
     */
    public static class StaticTrap extends SkillWeaponItem {
        public static final int COLOR = 0x60C0FF, LIFE = 400;
        public static final double TRIGGER = 2.0, BLAST = 3.0, OVER_TRIGGER = 6.0, OVER_BLAST = 12.0;

        public StaticTrap(Properties p) {
            super(p, 400_000, 3.0F, false, 0, 0, 0, 18_000, 30);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), 16, 0.6, 1);
            Vec3 at = ray.hits().isEmpty() ? ray.end().subtract(user.getLookAngle().scale(0.3)) : ray.hits().get(0).position();
            place(level, user, at);
            return true;
        }

        /** 罠を置く（兵姫に持たせたときも同じ処理） */
        public static void place(ServerLevel level, LivingEntity user, Vec3 at) {
            boolean over = OverloadArts.active(user);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, over ? 1.2F : 2.0F);
            if (over) {
                OverloadArts.flourish(level, user, 6.0F);
                Fx.mandala(level, at.add(0, 0.05, 0), Overload.GOLD, (float) OVER_TRIGGER, 20, false, true);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.3F, 12), user.getEyePosition().add(0, -0.3, 0), at, 0.6);
            }
            watch(level, user, at, 0, over);
        }

        static void watch(ServerLevel level, LivingEntity user, Vec3 at, int age, boolean over) {
            if (age >= LIFE) return;
            double trigger = over ? OVER_TRIGGER : TRIGGER, blast = over ? OVER_BLAST : BLAST;
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(trigger), e -> foe(user, e) && e instanceof net.minecraft.world.entity.monster.Enemy);
            if (!near.isEmpty()) {
                int n = 0;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(blast), e -> foe(user, e))) {
                    e.invulnerableTime = 0;
                    e.hurt(WeaponDamage.source(user, WeaponDamage.ARC, user, user), 70.0F);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    if (!over) WeaponUtil.lightning(level, at.add(0, 0.3, 0), mid(e), COLOR);
                    else if (n < 8) { // 稲妻は 8 体まで描く
                        bolt(level, at.add(0, 1.5, 0), mid(e), n % 2 == 0 ? COLOR : Overload.GOLD);
                        OverloadArts.impact(level, mid(e));
                    }
                    n++;
                }
                if (over) {
                    thunderSeal(level, at);
                    return;
                }
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 2.0F, 5), at.add(0, 0.5, 0), 1, 0, 0);
                Fx.shockwave(level, at.add(0, 0.05, 0), COLOR, 3.5F, 10);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
                return;
            }
            if (age % 10 == 0) { // 罠の目印: 地面で回る光の輪（オーバーロードは半径 6 の輪と陣）
                if (over) {
                    Fx.shockwave(level, at.add(0, 0.05, 0), COLOR, (float) OVER_TRIGGER, 12);
                    Fx.shockwave(level, at.add(0, 0.08, 0), Overload.GOLD, (float) OVER_TRIGGER * 0.5F, 12);
                    if (age % 40 == 0) Fx.mandala(level, at.add(0, 0.05, 0), COLOR, (float) OVER_TRIGGER, 40, false, (age / 40) % 2 == 0);
                    Fx.spray(level, Fx.opt(ModParticles.SPARK.get(), Overload.GOLD, 0.2F, 8), at.add(0, 0.3, 0), 6, OVER_TRIGGER * 0.5, 0.2, OVER_TRIGGER * 0.5, 0.03);
                } else {
                    Fx.shockwave(level, at.add(0, 0.05, 0), COLOR, 1.2F, 10);
                    Fx.spray(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.15F, 6), at.add(0, 0.2, 0), 2, 0.3, 0.02);
                }
            }
            WeaponUtil.later(level, 5, () -> watch(level, user, at, age + 5, over));
        }

        /** オーバーロードの雷陣の炸裂: 落雷（見た目だけ）、半径 12 の衝撃波、次の Tick に放射状に地を這う 8 本の稲妻 */
        private static void thunderSeal(ServerLevel level, Vec3 at) {
            net.minecraft.world.entity.LightningBolt strike = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (strike != null) {
                strike.moveTo(at.x, at.y, at.z);
                strike.setVisualOnly(true);
                level.addFreshEntity(strike);
            }
            WeaponUtil.later(level, 1, () -> {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI * 2 / 8 + level.random.nextDouble() * 0.3;
                    bolt(level, at.add(0, 0.3, 0), at.add(Math.cos(a) * OVER_BLAST, 0.2, Math.sin(a) * OVER_BLAST), i % 2 == 0 ? Overload.GOLD : COLOR);
                }
            });
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 4.0F, 6), at.add(0, 1.0, 0), 1, 0, 0);
            Fx.shockwave(level, at.add(0, 0.05, 0), COLOR, (float) OVER_BLAST + 0.5F, 14);
            Fx.shockwave(level, at.add(0, 0.08, 0), Overload.GOLD, (float) OVER_BLAST * 0.7F, 18);
            Fx.shockwave(level, at.add(0, 0.1, 0), Overload.CRIMSON, (float) OVER_BLAST * 0.4F, 22);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.4F, 14), at.add(0, 0.6, 0), 30, 0.8);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 1.0F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 0.8F);
        }
    }

    // ================= バーサークアックス =================

    /**
     * バーサークアックス（T2）: 両手の狂戦士の斧。攻撃力 18 / 攻撃速度 0.9。持ち主の体力が減るほど近接ダメージが上がる
     * （体力 20% 以下で 2 倍。{@link Batch3Events}）。右クリックで激昂: 体力を 4 削って力 II・俊敏 II・耐性 I を 8 秒。1 回 30,000 FE、間隔 20 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 激昂が「狂嵐の咆哮」に変わる。咆哮が半径 12 の紅蓮の衝撃波になり、
     * 中の敵を吹き飛ばして鈍化 II・弱体化（4 秒）を与え、持ち主のまわりに火柱が立つ。さらに振るたびに当てた所から半径 5 の紅い旋風の斬撃が出て、
     * まわりの別の敵（12 体まで）にも同じ攻撃力で当たる</p>
     */
    public static class BerserkAxe extends SkillWeaponItem {
        public static final int COLOR = 0xFF4020;
        public static final double OVER_ROAR = 12.0, OVER_CLEAVE = 5.0;

        public BerserkAxe(Properties p) {
            super(p, 500_000, 12.0F, true, 90.0, 0.9, 0, 30_000, 400);
        }

        /** 近接ダメージの倍率（体力が満タンで 1、20% 以下で 2） */
        public static float multiplier(LivingEntity user) {
            float hp = user.getHealth() / Math.max(1F, user.getMaxHealth());
            return 1F + Math.min(1F, Math.max(0F, (1F - hp) / 0.8F));
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            rage(level, user);
            return true;
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            boolean result = super.hurtEnemy(stack, target, attacker);
            if (attacker.level() instanceof ServerLevel level && OverloadArts.active(attacker)) cleave(level, attacker, target);
            return result;
        }

        /** オーバーロードの紅い旋風: 当てた相手のまわり（半径 5）の別の敵に同じ攻撃力で当たる（12 体まで） */
        public static void cleave(ServerLevel level, LivingEntity user, LivingEntity target) {
            float damage = (float) user.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            Vec3 c = mid(target);
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(OVER_CLEAVE),
                    e -> foe(user, e) && e != target && e.distanceTo(target) <= OVER_CLEAVE + e.getBbWidth())) {
                if (n++ >= 12) break;
                e.invulnerableTime = 0;
                e.hurt(WeaponUtil.melee(user), damage);
                Fx.slash(level, mid(e), n % 2 == 0 ? COLOR : Overload.GOLD, 1.4F, 6, level.random.nextFloat() * 6.28F);
                if (n <= 6) OverloadArts.impact(level, mid(e));
            }
            Fx.saw(level, c, Overload.CRIMSON, (float) OVER_CLEAVE, 8);
            Fx.saw(level, c, COLOR, (float) OVER_CLEAVE * 0.6F, 10);
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                Fx.slash(level, c.add(Math.cos(a) * OVER_CLEAVE * 0.7, 0, Math.sin(a) * OVER_CLEAVE * 0.7), COLOR, 1.6F, 7, (float) (a + Math.PI / 2));
            }
            Fx.shockwave(level, target.position().add(0, 0.05, 0), COLOR, (float) OVER_CLEAVE + 0.5F, 10);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
        }

        /** 激昂（兵姫に持たせたときも同じ処理） */
        public static void rage(ServerLevel level, LivingEntity user) {
            user.setHealth(Math.max(1F, user.getHealth() - 4F));
            user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 1));
            user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 1));
            user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0));
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.3F, 20), mid(user), 20, 0.5, 0.08);
            if (OverloadArts.active(user)) {
                roar(level, user);
                return;
            }
            Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, 4.0F, 12);
            level.playSound(null, user.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 1.2F);
        }

        /** オーバーロードの狂嵐の咆哮: 半径 12 の紅蓮の衝撃波で敵を吹き飛ばし、鈍化 II・弱体化。持ち主のまわりに火柱 */
        private static void roar(ServerLevel level, LivingEntity user) {
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_ROAR),
                    e -> foe(user, e) && e.distanceTo(user) <= OVER_ROAR + e.getBbWidth())) {
                Vec3 away = new Vec3(e.getX() - user.getX(), 0, e.getZ() - user.getZ());
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.setDeltaMovement(away.x * 1.8, 0.6, away.z * 1.8);
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                if (n++ < 10) OverloadArts.impact(level, mid(e));
            }
            Vec3 feet = user.position().add(0, 0.05, 0);
            Fx.shockwave(level, feet, COLOR, (float) OVER_ROAR + 0.5F, 14);
            Fx.shockwave(level, feet.add(0, 0.05, 0), Overload.CRIMSON, (float) OVER_ROAR * 0.75F, 18);
            WeaponUtil.later(level, 4, () -> Fx.shockwave(level, feet, COLOR, (float) OVER_ROAR, 12));
            // 火柱: 持ち主を囲む 6 本の紅い光の柱と、立ちのぼる炎
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3;
                Vec3 base = feet.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), k % 2 == 0 ? COLOR : Overload.GOLD, 0.6F, 20), base, base.add(0, 7, 0), 0.5);
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, feet.x, feet.y + 0.5, feet.z, 60, 2.5, 0.5, 2.5, 0.12);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), Overload.CRIMSON, 0.4F, 24), feet.add(0, 1, 0), 50, OVER_ROAR * 0.5, 0.4, OVER_ROAR * 0.5, 0.05);
            OverloadArts.flourish(level, user, (float) OVER_ROAR);
            level.playSound(null, user.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6F, 0.7F);
            level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
    }

    // ================= ソニックキャノン =================

    /**
     * ソニックキャノン（T2）: 両手の音響砲。正面へ太い音の衝撃波（24 ブロック・幅 2.5・20 体まで貫通）: 12・吹き飛ばし・吐き気（5 秒）。
     * 1 射 35,000 FE、間隔 2 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「大音響砲」に変わる。衝撃波は 48 ブロック・幅 8（3 倍強）・40 体まで貫通し、
     * 太さ 8 の音の輪の筒（紅と青緑）が並んで進む。届いた先で音の爆発が起き、半径 6 の敵を外へ吹き飛ばす（同じダメージ）</p>
     */
    public static class SonicCannon extends SkillWeaponItem {

        /** 弾の威力（説明に出す） */
        @Override
        public float[] shotDamage() {
            return new float[]{75.0F, 1};
        }

        public static final int COLOR = 0x90FFE0;
        public static final double OVER_RANGE = 48.0, OVER_WIDTH = 8.0, OVER_BOOM = 6.0;

        public SonicCannon(Properties p) {
            super(p, 700_000, 9.0F, true, 0, 0, 0, 35_000, 40);
        }

        @Override
        public float recoil() {
            return 0.9F;
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            blast(level, user);
            return true;
        }

        /** 衝撃波（兵姫に持たせたときも同じ処理）。当たった数 */
        public static int blast(ServerLevel level, LivingEntity user) {
            if (OverloadArts.active(user)) return overBlast(level, user);
            Vec3 look = user.getLookAngle(), eye = user.getEyePosition();
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, 24, 2.5, 20);
            int n = 0;
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 75.0F);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                e.setDeltaMovement(e.getDeltaMovement().add(look.x * 1.5, 0.4, look.z * 1.5));
                e.hurtMarked = true;
                n++;
            }
            // 音の輪: 進む向きに並ぶ広がる輪
            Vec3 side = look.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(look).normalize();
            double len = ray.end().distanceTo(eye);
            for (double d = 1.5; d < len; d += 1.5) {
                double r = 0.6 + d * 0.06;
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI * 2 / 10;
                    Vec3 p = eye.add(look.scale(d)).add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.18F, 6), p, look.scale(0.2));
                }
            }
            level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.2F);
            return n;
        }

        /** オーバーロードの大音響砲: 48 ブロック・幅 8・40 体まで貫通、届いた先で半径 6 の音の爆発 */
        private static int overBlast(ServerLevel level, LivingEntity user) {
            Vec3 look = user.getLookAngle(), eye = user.getEyePosition();
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, OVER_RANGE, OVER_WIDTH, 40);
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                sonicHit(user, e, look.x * 2.2, 0.5, look.z * 2.2);
                if (hit.size() < 12) OverloadArts.impact(level, mid(e));
                hit.add(e);
            }
            // 届いた先の音の爆発（半径 6）: 外へ吹き飛ばす
            Vec3 end = ray.end();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(OVER_BOOM),
                    e -> foe(user, e) && !hit.contains(e) && e.distanceToSqr(end) <= (OVER_BOOM + e.getBbWidth()) * (OVER_BOOM + e.getBbWidth()))) {
                Vec3 away = mid(e).subtract(end);
                away = away.lengthSqr() < 1.0E-4 ? look : away.normalize();
                sonicHit(user, e, away.x * 1.8, 0.6, away.z * 1.8);
                hit.add(e);
            }
            // 太さ 8 の音の輪の筒: 3 ブロックごとの光線の輪と、粒の輪
            Vec3 side = look.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(look).normalize();
            double len = end.distanceTo(eye);
            int ring = 0;
            for (double d = 2.0; d < len; d += 3.0, ring++) {
                double r = OVER_WIDTH * 0.5 * Math.min(1.0, 0.45 + d * 0.06);
                Vec3 c = eye.add(look.scale(d));
                Fx.beamRing(level, c, look, ring % 2 == 0 ? COLOR : Overload.CRIMSON, (float) r, 10 + ring % 4);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI * 2 / 12 + ring * 0.26;
                    Vec3 p = c.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), i % 4 == 0 ? Overload.GOLD : COLOR, 0.3F, 8), p, look.scale(0.4));
                }
            }
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 3.0F, 6), end, 1, 0, 0);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.4F, 14), end, 30, 0.8);
            Fx.beamRing(level, end, look, Overload.GOLD, (float) OVER_BOOM + 1, 14);
            Fx.shockwave(level, end.add(0, -0.5, 0), COLOR, (float) OVER_BOOM + 1, 14);
            Fx.shockwave(level, end.add(0, -0.45, 0), Overload.CRIMSON, (float) OVER_BOOM * 0.6F, 18);
            OverloadArts.flourish(level, user, 8.0F);
            level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.7F);
            level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 1.2F);
            return hit.size();
        }

        private static void sonicHit(LivingEntity user, LivingEntity e, double vx, double vy, double vz) {
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 75.0F);
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            e.setDeltaMovement(e.getDeltaMovement().add(vx, vy, vz));
            e.hurtMarked = true;
        }
    }

    /** 粒の少ない長い稲妻（オーバーロードの雷陣・雷神の裁きで、長い稲妻を何本も描くとき用。{@link WeaponUtil#lightning} より軽い） */
    static void bolt(ServerLevel level, Vec3 a, Vec3 b, int color) {
        Vec3 d = b.subtract(a);
        int segments = Math.max(3, Math.min(10, (int) (d.length() * 0.7)));
        double jitter = Math.min(1.2, d.length() * 0.1);
        Vec3 prev = a;
        for (int i = 1; i <= segments; i++) {
            Vec3 p = a.add(d.scale((double) i / segments));
            if (i < segments) p = p.add((level.random.nextDouble() - 0.5) * jitter, (level.random.nextDouble() - 0.5) * jitter, (level.random.nextDouble() - 0.5) * jitter);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.2F, 6), prev, p, 1.0);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), color, 0.4F, 8), prev, p, 0.6);
            prev = p;
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), color, 0.25F, 8), b, 3, 0.25);
    }

    /** プレイヤーなら手に持った兵装を返す（テスト用の補助） */
    static boolean holding(LivingEntity user, Class<?> type) {
        return type.isInstance(user.getMainHandItem().getItem()) || (user instanceof Player p && type.isInstance(p.getOffhandItem().getItem()));
    }
}
