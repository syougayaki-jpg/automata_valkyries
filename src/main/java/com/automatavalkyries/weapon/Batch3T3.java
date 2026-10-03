package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.automatavalkyries.weapon.Batch3T1.foe;
import static com.automatavalkyries.weapon.Batch3T1.mid;

/**
 * 追加の兵装 第 3 弾（2026-10-03、ユーザーの依頼「50 種類に。特殊効果多めに」でこちらが考えた）の T3 の 5 種。値はどれも【提案】。
 */
public final class Batch3T3 {

    private Batch3T3() {}

    // ================= 呪詛の魔導書 =================

    /**
     * 呪詛の魔導書『ヘクス』（T3）: 片手の魔導書。右クリックで半径 12 の敵を呪う（6 秒）: 衰弱 II・弱体化 II・鈍化 II・発光、
     * さらに呪われた敵はあらゆるダメージを 1.5 倍受ける（{@link Batch3Events}）。1 回 200,000 FE、間隔 10 秒。
     */
    public static class HexGrimoire extends SkillWeaponItem {
        public static final int COLOR = 0x9A40D0, DURATION = 120;
        public static final float AMPLIFY = 1.5F;
        static final String TAG = "AutomataHexUntil";

        public HexGrimoire(Properties p) {
            super(p, 3_000_000, 2.0F, false, 0, 0, 0, 200_000, 200);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            hex(level, user);
            return true;
        }

        /** 呪われているか */
        public static boolean cursed(LivingEntity e) {
            return e.getPersistentData().getLong(TAG) > e.level().getGameTime();
        }

        /** 呪う（兵姫に持たせたときも同じ処理）。呪った数 */
        public static int hex(ServerLevel level, LivingEntity user) {
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(12), e -> foe(user, e) && e.distanceTo(user) <= 12 + e.getBbWidth())) {
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, DURATION, 1));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DURATION, 1));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION, 1));
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, DURATION, 0));
                e.getPersistentData().putLong(TAG, level.getGameTime() + DURATION);
                // 呪いの印: 頭上で回る紫の六角と、足元から立ち昇る黒い煙
                Vec3 head = new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ());
                Fx.burst(level, Fx.opt(ModParticles.HEX.get(), COLOR, 0.25F, 30), head, 6, 0.05);
                Fx.spray(level, Fx.opt(ModParticles.VOID_SHARD.get(), 0x200830, 0.25F, 20), e.position(), 8, 0.4, 0.03);
                n++;
            }
            Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, 12.5F, 20);
            Fx.shockwave(level, user.position().add(0, 0.1, 0), 0x400860, 8.0F, 24);
            for (int i = 0; i < 12; i++) { // 魔法陣の外周に浮かぶ時計の数字
                double a = i * Math.PI / 6;
                Fx.one(level, Fx.opt(ModParticles.GLYPH.get(), COLOR, 0.35F, 30), user.position().add(Math.cos(a) * 3, 0.3, Math.sin(a) * 3), new Vec3(0, 0.02, 0));
            }
            level.playSound(null, user.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.6F);
            return n;
        }
    }

    // ================= 不死鳥の翼 =================

    /**
     * 不死鳥の翼『フェニックス』（T3）: 両手の炎の翼。右クリックで向いた方向（上下も）へ 20 ブロック飛翔し、通り道に炎の帯を残す:
     * 触れた敵に 20・8 秒燃やす。持ち主は火炎耐性（10 秒）と落下低速（5 秒）。1 回 150,000 FE、間隔 6 秒。
     */
    public static class PhoenixWing extends SkillWeaponItem {
        public static final int COLOR = 0xFF7020, CORE = 0xFFE080;

        public PhoenixWing(Properties p) {
            super(p, 3_000_000, 6.0F, true, 0, 0, 0, 150_000, 120);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            soar(level, user);
            return true;
        }

        /** 飛翔（兵姫に持たせたときも同じ処理）。焼いた数 */
        public static int soar(ServerLevel level, LivingEntity user) {
            Vec3 look = user.getLookAngle(), start = mid(user);
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, start, look, 20, 2.0, 20);
            int n = 0;
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 20.0F);
                e.setSecondsOnFire(8);
                n++;
            }
            user.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
            user.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
            user.setDeltaMovement(look.scale(3.0));
            user.hurtMarked = true;
            user.resetFallDistance();
            // 炎の帯と、左右に広がる炎の翼
            Vec3 side = look.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            double len = ray.end().distanceTo(start);
            for (double d = 0; d < len; d += 0.7) {
                Vec3 p = start.add(look.scale(d));
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), d % 1.4 < 0.7 ? CORE : COLOR, 0.5F, 20), p, new Vec3(0, 0.03, 0));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
            for (int w = 1; w <= 8; w++) {
                for (int s : new int[]{1, -1}) {
                    Vec3 p = start.add(side.scale(s * w * 0.45)).add(0, -Math.abs(w - 4) * 0.1 + 0.4, 0);
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), w < 4 ? CORE : COLOR, 0.4F, 14), p, look.scale(0.5));
                }
            }
            level.playSound(null, user.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
            level.playSound(null, user.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.4F);
            return n;
        }
    }

    // ================= ストームコーラー =================

    /**
     * 嵐呼びの杖『テンペスト』（T3）: 両手の杖。右クリックで半径 16 の敵へ 2 秒かけて雷を 8 回落とす（1 回 20 の電撃・鈍化 II 2 秒、
     * 見た目だけの雷で火は付かない）。雨や雷雨の中では 16 回。1 回 250,000 FE、間隔 8 秒。
     */
    public static class StormCaller extends SkillWeaponItem {
        public static final int COLOR = 0xA0C8FF;

        public StormCaller(Properties p) {
            super(p, 3_000_000, 6.0F, true, 0, 0, 0, 250_000, 160);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            return call(level, user) > 0;
        }

        /** 雷を呼ぶ（兵姫に持たせたときも同じ処理）。落とす回数（範囲に敵がいなければ 0） */
        public static int call(ServerLevel level, LivingEntity user) {
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(16, 8, 16), e -> foe(user, e) && e instanceof Enemy);
            if (targets.isEmpty()) return 0;
            int strikes = level.isRaining() || level.isThundering() ? 16 : 8;
            for (int i = 0; i < strikes; i++) {
                WeaponUtil.later(level, i * (40 / strikes), () -> {
                    List<LivingEntity> alive = targets.stream().filter(LivingEntity::isAlive).toList();
                    if (alive.isEmpty()) return;
                    LivingEntity e = alive.get(level.random.nextInt(alive.size()));
                    e.invulnerableTime = 0;
                    e.hurt(WeaponDamage.source(user, WeaponDamage.ARC, user, user), 20.0F);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    Vec3 at = mid(e);
                    WeaponUtil.lightning(level, at.add(level.random.nextDouble() * 2 - 1, 18, level.random.nextDouble() * 2 - 1), at, COLOR);
                    Fx.shockwave(level, e.position().add(0, 0.05, 0), COLOR, 2.5F, 8);
                    level.playSound(null, e.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.6F, 1.4F);
                });
            }
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.4F, 30), user.position().add(0, user.getBbHeight() + 2, 0), 20, 1.5, 0.02);
            level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.0F);
            return strikes;
        }
    }

    // ================= リフレクターブレード =================

    /**
     * 鏡刃『リフレクター』（T3）: 片手の剣。攻撃力 20 / 攻撃速度 1.4。右クリックを押している間（最大 5 秒）鏡の守り:
     * 飛び道具は跳ね返し、近接攻撃は 75% 減らして相手に 50% を返す（{@link Batch3Events}）。守りの間 0.05 秒ごと 500 FE。
     */
    public static class ReflectorBlade extends SkillWeaponItem {
        public static final int COLOR = 0xE0F0FF, PER_TICK = 500, MAX_GUARD = 100;

        public ReflectorBlade(Properties p) {
            super(p, 3_000_000, 4.0F, false, 20.0, 1.4, 0, PER_TICK, 0);
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            return true;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!WeaponPower.has(player, stack, PER_TICK)) {
                notEnoughPower(player);
                return InteractionResultHolder.fail(stack);
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            if (!(level instanceof ServerLevel server)) return;
            if (!WeaponPower.use(user, stack, PER_TICK)) {
                user.releaseUsingItem();
                return;
            }
            int t = getUseDuration(stack) - remaining;
            if (t % 4 == 0) { // 鏡の面: 正面に六角の欠片が並ぶ
                Vec3 look = user.getLookAngle();
                Vec3 c = user.getEyePosition().add(look.scale(0.9));
                Fx.burst(server, Fx.opt(ModParticles.HEX.get(), COLOR, 0.15F, 6), c, 4, 0.05);
            }
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.BLOCK;
        }

        @Override
        public int getUseDuration(ItemStack stack) {
            return MAX_GUARD;
        }

        /** 鏡の守りの最中か */
        public static boolean guarding(LivingEntity e) {
            return e.isUsingItem() && e.getUseItem().getItem() instanceof ReflectorBlade;
        }
    }

    // ================= ノヴァランス =================

    /**
     * 新星槍『ノヴァ』（T3）: 両手の槍。攻撃力 26 / 攻撃速度 1.0・間合い +3。4 発当てるごとに新星が弾け、半径 5 の敵に 30（無敵時間を無視）・
     * 5 秒燃やす・打ち上げ。右クリックで 8 ブロック突き抜ける突進（10 体まで 25）。1 当たり 30,000 FE、突進 60,000 FE・間隔 3 秒。
     */
    public static class NovaLance extends SkillWeaponItem {
        public static final int COLOR = 0xFFE060, PER_HIT = 30_000, NOVA_EVERY = 4;
        static final String TAG = "AutomataNovaHits";

        public NovaLance(Properties p) {
            super(p, 4_000_000, 10.0F, true, 26.0, 1.0, 3.0, 60_000, 60);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (!(attacker.level() instanceof ServerLevel level) || !WeaponPower.use(attacker, stack, PER_HIT)) return true;
            CompoundTag tag = stack.getOrCreateTag();
            int hits = tag.getInt(TAG) + 1;
            if (hits >= NOVA_EVERY) {
                hits = 0;
                nova(level, attacker, mid(target));
            }
            tag.putInt(TAG, hits);
            playUse(attacker, stack);
            return true;
        }

        /** 新星（兵姫に持たせたときも同じ処理）。当たった数 */
        public static int nova(ServerLevel level, LivingEntity user, Vec3 at) {
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(5), e -> foe(user, e) && e.distanceToSqr(at) <= 30)) {
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 30.0F);
                e.setSecondsOnFire(5);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.8, 0));
                e.hurtMarked = true;
                n++;
            }
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 3.0F, 6), at, 1, 0, 0);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.4F, 12), at, 30, 0.6);
            Fx.shockwave(level, at, COLOR, 5.5F, 12);
            Fx.shockwave(level, at, 0xFF8030, 3.5F, 16);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.5F);
            return n;
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            thrust(level, user);
            return true;
        }

        /** 突進（兵姫に持たせたときも同じ処理）。貫いた数 */
        public static int thrust(ServerLevel level, LivingEntity user) {
            Vec3 look = user.getLookAngle();
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, mid(user), look, 8, 1.5, 10);
            int n = 0;
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                e.hurt(WeaponUtil.melee(user), 25.0F);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), mid(e), 8, 0.3);
                n++;
            }
            user.setDeltaMovement(look.scale(2.2).add(0, 0.1, 0));
            user.hurtMarked = true;
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.35F, 10), mid(user), mid(user).add(look.scale(8)), 0.35);
            level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.2F);
            return n;
        }
    }
}
