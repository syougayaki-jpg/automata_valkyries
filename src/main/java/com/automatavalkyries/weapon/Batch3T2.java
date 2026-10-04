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
 */
public final class Batch3T2 {

    private Batch3T2() {}

    // ================= クリムゾンシックル =================

    /**
     * クリムゾンシックル（T2）: 片手の血の鎌。攻撃力 12 / 攻撃速度 1.4。当てるたびに 3.5 回復（吸血）。
     * 右クリックで血の波動: 半径 6 の敵に 6（無敵時間を無視）・衰弱 II（4 秒）、吸った分だけ回復（1 体 3、最大 15）。1 回 25,000 FE、間隔 4 秒。
     */
    public static class CrimsonSickle extends SkillWeaponItem {
        public static final int COLOR = 0xD02040;
        public static final float LEECH = 3.5F;

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
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(6), e -> foe(user, e) && e.distanceTo(user) <= 6 + e.getBbWidth())) {
                e.invulnerableTime = 0;
                e.hurt(WeaponUtil.melee(user), 30.0F);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                for (int i = 0; i < 6; i++) {
                    Vec3 from = mid(e).add((level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6, (level.random.nextDouble() - 0.5) * 0.6);
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.25F, 12), from, mid(user).subtract(from).scale(1 / 12.0));
                }
                n++;
            }
            user.heal(Math.min(15F, 3F * n));
            Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, 6.5F, 14);
            Fx.shockwave(level, user.position().add(0, 0.1, 0), 0x500818, 4.0F, 18);
            level.playSound(null, user.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.6F, 1.4F);
            return n;
        }
    }

    // ================= レビテーションロッド =================

    /**
     * レビテーションロッド（T2）: 両手の重力反転の杖。右クリックで半径 8 の敵を浮かせ（浮遊 III 3 秒・6）、
     * 3 秒後にまとめて地面へ叩き落とす（8・下向きに強く）。1 回 30,000 FE、間隔 6 秒。
     */
    public static class LevitationRod extends SkillWeaponItem {
        public static final int COLOR = 0xB890FF, LIFT = 60;

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
            List<LivingEntity> lifted = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(8), e -> foe(user, e) && e.distanceTo(user) <= 8 + e.getBbWidth());
            for (LivingEntity e : lifted) {
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 50.0F);
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LIFT, 2));
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.25F, 20), e.position(), 8, 0.4, 0.02);
            }
            Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, 8.5F, 16);
            level.playSound(null, user.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
            WeaponUtil.later(level, LIFT, () -> {
                for (LivingEntity e : lifted) {
                    if (!e.isAlive()) continue;
                    e.removeEffect(MobEffects.LEVITATION);
                    e.setDeltaMovement(0, -3.0, 0);
                    e.hurtMarked = true;
                    e.invulnerableTime = 0;
                    e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 65.0F);
                    Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.3F, 8), mid(e).add(0, 3, 0), mid(e), 0.3);
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
     */
    public static class RicochetRifle extends SkillWeaponItem {
        public static final int COLOR = 0xFFB040, BOUNCES = 6;

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
            Vec3 muzzle = user.getEyePosition().add(user.getLookAngle().scale(1.0)).add(0, -0.2, 0);
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), 40, 0.6, 1);
            level.playSound(null, user.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.6F);
            if (ray.hits().isEmpty()) {
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.15F, 6), muzzle, ray.end(), 0.5);
                return 0;
            }
            List<LivingEntity> hit = new ArrayList<>();
            LivingEntity cur = ray.hits().get(0);
            Vec3 from = muzzle;
            float damage = 60.0F;
            while (cur != null && hit.size() < BOUNCES) {
                hit.add(cur);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.1F, 6), from, mid(cur), 0.4);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.18F, 8), from, mid(cur), 0.6);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.25F, 8), mid(cur), 5, 0.25);
                cur.invulnerableTime = 0;
                cur.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), damage);
                damage *= 1.2F;
                from = mid(cur);
                LivingEntity last = cur;
                cur = level.getEntitiesOfClass(LivingEntity.class, last.getBoundingBox().inflate(10), e -> foe(user, e) && !hit.contains(e) && e instanceof net.minecraft.world.entity.monster.Enemy)
                        .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(last))).orElse(null);
            }
            return hit.size();
        }
    }

    // ================= スペクトラルチェイン =================

    /**
     * スペクトラルチェイン（T2）: 片手の霊鎖。攻撃力 7 / 攻撃速度 1.6。右クリックで見ている敵（20 ブロック）へ鎖を撃ち込み、
     * 相手へ一気に飛びつく（10・鈍化 V 1.5 秒・弱体化 3 秒）。相手がいなければ使わない。1 回 15,000 FE、間隔 2 秒。
     */
    public static class SpectralChain extends SkillWeaponItem {
        public static final int COLOR = 0x80E0FF;

        public SpectralChain(Properties p) {
            super(p, 400_000, 3.0F, false, 50.0, 1.6, 0, 15_000, 40);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            return hook(level, user) != null;
        }

        /** 鎖を撃ち込んで飛びつく（兵姫に持たせたときも同じ処理）。捕らえた相手（いなければ null） */
        public static @Nullable LivingEntity hook(ServerLevel level, LivingEntity user) {
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), 20, 1.2, 1);
            if (ray.hits().isEmpty() || !foe(user, ray.hits().get(0))) return null;
            LivingEntity target = ray.hits().get(0);
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
    }

    // ================= スタティックトラップ =================

    /**
     * スタティックトラップ（T2）: 片手の罠の投射器。狙った所（16 ブロック、最初の敵か壁）に電撃の罠を 20 秒置く。
     * 敵が 2 ブロック以内に入ると炸裂し、半径 3 の敵に 14 の電撃・鈍化 III（3 秒）。1 個 18,000 FE、間隔 1.5 秒。
     */
    public static class StaticTrap extends SkillWeaponItem {
        public static final int COLOR = 0x60C0FF, LIFE = 400;

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
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 2.0F);
            watch(level, user, at, 0);
        }

        static void watch(ServerLevel level, LivingEntity user, Vec3 at, int age) {
            if (age >= LIFE) return;
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.0), e -> foe(user, e) && e instanceof net.minecraft.world.entity.monster.Enemy);
            if (!near.isEmpty()) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0), e -> foe(user, e))) {
                    e.invulnerableTime = 0;
                    e.hurt(WeaponDamage.source(user, WeaponDamage.ARC, user, user), 70.0F);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    WeaponUtil.lightning(level, at.add(0, 0.3, 0), mid(e), COLOR);
                }
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 2.0F, 5), at.add(0, 0.5, 0), 1, 0, 0);
                Fx.shockwave(level, at.add(0, 0.05, 0), COLOR, 3.5F, 10);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
                return;
            }
            if (age % 10 == 0) { // 罠の目印: 地面で回る小さな光の輪
                Fx.shockwave(level, at.add(0, 0.05, 0), COLOR, 1.2F, 10);
                Fx.spray(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.15F, 6), at.add(0, 0.2, 0), 2, 0.3, 0.02);
            }
            WeaponUtil.later(level, 5, () -> watch(level, user, at, age + 5));
        }
    }

    // ================= バーサークアックス =================

    /**
     * バーサークアックス（T2）: 両手の狂戦士の斧。攻撃力 18 / 攻撃速度 0.9。持ち主の体力が減るほど近接ダメージが上がる
     * （体力 20% 以下で 2 倍。{@link Batch3Events}）。右クリックで激昂: 体力を 4 削って力 II・俊敏 II・耐性 I を 8 秒。1 回 30,000 FE、間隔 20 秒。
     */
    public static class BerserkAxe extends SkillWeaponItem {
        public static final int COLOR = 0xFF4020;

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

        /** 激昂（兵姫に持たせたときも同じ処理） */
        public static void rage(ServerLevel level, LivingEntity user) {
            user.setHealth(Math.max(1F, user.getHealth() - 4F));
            user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 1));
            user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 1));
            user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0));
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.3F, 20), mid(user), 20, 0.5, 0.08);
            Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, 4.0F, 12);
            level.playSound(null, user.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    // ================= ソニックキャノン =================

    /**
     * ソニックキャノン（T2）: 両手の音響砲。正面へ太い音の衝撃波（24 ブロック・幅 2.5・20 体まで貫通）: 12・吹き飛ばし・吐き気（5 秒）。
     * 1 射 35,000 FE、間隔 2 秒。
     */
    public static class SonicCannon extends SkillWeaponItem {
        public static final int COLOR = 0x90FFE0;

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
    }

    /** プレイヤーなら手に持った兵装を返す（テスト用の補助） */
    static boolean holding(LivingEntity user, Class<?> type) {
        return type.isInstance(user.getMainHandItem().getItem()) || (user instanceof Player p && type.isInstance(p.getOffhandItem().getItem()));
    }
}
