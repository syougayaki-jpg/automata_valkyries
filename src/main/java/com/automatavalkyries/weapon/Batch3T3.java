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
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「大呪界」。呪いの範囲が半径 12 から半径 30 に広がり、足元に半径 8 の
     * 紫と金の魔法陣・半径 30 へ広がる 3 重の衝撃波・外周 30 に並ぶ 24 の時計の数字が浮かぶ。呪った敵それぞれの足元から紫の呪いの柱が立ち、
     * 持ち主から呪いの鎖が伸びる（柱と鎖の見た目は 12 体まで、呪い自体は範囲の全員）</p>
     */
    public static class HexGrimoire extends SkillWeaponItem {
        public static final int COLOR = 0x9A40D0, DURATION = 120;
        /** 呪う範囲（オーバーロードは半径 30） */
        public static final double RADIUS = 12.0, OVER_RADIUS = 30.0;
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
            boolean over = OverloadArts.active(user);
            double radius = over ? OVER_RADIUS : RADIUS;
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius), e -> foe(user, e) && e.distanceTo(user) <= radius + e.getBbWidth())) {
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, DURATION, 1));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DURATION, 1));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION, 1));
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, DURATION, 0));
                e.getPersistentData().putLong(TAG, level.getGameTime() + DURATION);
                // 呪いの印: 頭上で回る紫の六角と、足元から立ち昇る黒い煙
                Vec3 head = new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ());
                Fx.burst(level, Fx.opt(ModParticles.HEX.get(), COLOR, 0.25F, 30), head, 6, 0.05);
                Fx.spray(level, Fx.opt(ModParticles.VOID_SHARD.get(), 0x200830, 0.25F, 20), e.position(), 8, 0.4, 0.03);
                if (over && n < 12) curseMark(level, user, e);
                n++;
            }
            if (over) {
                overCircle(level, user);
                return n;
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

        /** オーバーロード: 呪った敵の足元から立つ紫の呪いの柱と、持ち主から伸びる呪いの鎖 */
        private static void curseMark(ServerLevel level, LivingEntity user, LivingEntity e) {
            Vec3 base = e.position().add(0, 0.1, 0);
            Fx.line(level, Fx.opt(ModParticles.HEX.get(), COLOR, 0.3F, 24), base, base.add(0, 6, 0), 0.6);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.CRIMSON, 0.25F, 18), base, base.add(0, 4, 0), 0.8);
            Fx.shockwave(level, base, COLOR, 2.0F + e.getBbWidth(), 14);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0x5A2090, 0.2F, 16), mid(user), mid(e), 1.5);
            OverloadArts.impact(level, mid(e));
        }

        /** オーバーロード: 足元の大魔法陣と、半径 30 へ広がる呪いの波・外周の時計の数字 */
        private static void overCircle(ServerLevel level, LivingEntity user) {
            Vec3 feet = user.position().add(0, 0.05, 0);
            Fx.mandala(level, feet, COLOR, 8.0F, 60, false, true);
            Fx.mandala(level, feet.add(0, 0.03, 0), Overload.GOLD, 5.0F, 60, false, false);
            Fx.shockwave(level, feet, COLOR, (float) OVER_RADIUS, 26);
            Fx.shockwave(level, feet.add(0, 0.1, 0), 0x400860, (float) OVER_RADIUS * 0.7F, 30);
            Fx.shockwave(level, feet.add(0, 0.15, 0), Overload.CRIMSON, (float) OVER_RADIUS * 0.45F, 22);
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12;
                Fx.one(level, Fx.opt(ModParticles.GLYPH.get(), i % 2 == 0 ? COLOR : Overload.GOLD, 0.6F, 50),
                        user.position().add(Math.cos(a) * OVER_RADIUS, 0.6, Math.sin(a) * OVER_RADIUS), new Vec3(0, 0.03, 0));
            }
            Fx.spray(level, Fx.opt(ModParticles.VOID_SHARD.get(), 0x200830, 0.3F, 30), user.position().add(0, 1, 0), 40, OVER_RADIUS * 0.5, 1.0, OVER_RADIUS * 0.5, 0.03);
            Fx.spray(level, Fx.opt(ModParticles.HEX.get(), COLOR, 0.2F, 30), user.position().add(0, 1, 0), 30, OVER_RADIUS * 0.5, 1.5, OVER_RADIUS * 0.5, 0.02);
            OverloadArts.flourish(level, user, (float) OVER_RADIUS);
            level.playSound(null, user.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.5F, 0.4F);
            level.playSound(null, user.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    // ================= 不死鳥の翼 =================

    /**
     * 不死鳥の翼『フェニックス』（T3）: 両手の炎の翼。右クリックで向いた方向（上下も）へ 20 ブロック飛翔し、通り道に炎の帯を残す:
     * 触れた敵に 20・8 秒燃やす。持ち主は火炎耐性（10 秒）と落下低速（5 秒）。1 回 150,000 FE、間隔 6 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「炎羽の大扇」。飛び立つ瞬間、背に半径 3.5 の炎の羽根の扇が開き、
     * 炎の羽根 9 枚を前方 120 度（左右 60 度）へ扇状に放つ（中央から外へ 0.2 秒で開く）。羽根 1 枚は 24 ブロック・太さ 2.4 で、触れた敵に
     * 飛翔と同じ一撃と 8 秒の炎（1 体には 1 回だけ）。羽根に沿って炎の羽枝（斬撃）が並ぶ</p>
     */
    public static class PhoenixWing extends SkillWeaponItem {
        public static final int COLOR = 0xFF7020, CORE = 0xFFE080;
        /** オーバーロード: 炎の羽根の数・扇の左右の角度（度）・羽根の届く距離・太さ */
        public static final int OVER_FEATHERS = 9;
        public static final double OVER_SPREAD = 60.0, OVER_RANGE = 24.0, OVER_WIDTH = 2.4;

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
            boolean over = OverloadArts.active(user);
            java.util.Set<LivingEntity> burned = new java.util.HashSet<>();
            int n = 0;
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                burned.add(e);
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 200.0F);
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
            if (over) featherFan(level, user, start, look, burned);
            return n;
        }

        /** オーバーロード: 背に炎の羽根の扇が開き、前方 120 度へ炎の羽根 9 枚が扇状に飛ぶ */
        private static void featherFan(ServerLevel level, LivingEntity user, Vec3 start, Vec3 look, java.util.Set<LivingEntity> burned) {
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
            Vec3 side = flat.cross(new Vec3(0, 1, 0)).normalize();
            // 背に開く扇（半円に並ぶ炎の羽根）
            for (int k = 0; k <= 12; k++) {
                double a = Math.PI * k / 12;
                Vec3 p = start.subtract(flat.scale(0.6)).add(side.scale(Math.cos(a) * 3.5)).add(0, Math.sin(a) * 3.5, 0);
                Fx.slash(level, p, k % 2 == 0 ? COLOR : CORE, 1.4F, 10, (float) (a - Math.PI / 2));
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), k % 3 == 0 ? Overload.GOLD : COLOR, 0.5F, 18), p, new Vec3(0, 0.04, 0));
            }
            Fx.mandala(level, start.subtract(flat.scale(0.8)), Overload.GOLD, 3.0F, 20, true, false);
            OverloadArts.flourish(level, user, 8.0F);
            level.playSound(null, user.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
            int middle = OVER_FEATHERS / 2;
            for (int k = 0; k < OVER_FEATHERS; k++) {
                double yaw = Math.toRadians(-OVER_SPREAD + 2 * OVER_SPREAD * k / (OVER_FEATHERS - 1));
                Vec3 dir = flat.scale(Math.cos(yaw)).add(side.scale(Math.sin(yaw))).add(0, look.y * 0.6, 0).normalize();
                int index = k;
                WeaponUtil.later(level, Math.abs(k - middle), () -> feather(level, user, start, dir, index, burned));
            }
        }

        /** 炎の羽根 1 枚: 24 ブロック・太さ 2.4 の線上の敵を焼く（1 体には 1 回だけ） */
        private static void feather(ServerLevel level, LivingEntity user, Vec3 start, Vec3 dir, int k, java.util.Set<LivingEntity> burned) {
            if (!user.isAlive()) return;
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, start, dir, OVER_RANGE, OVER_WIDTH, 8);
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e) || !burned.add(e)) continue;
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 200.0F);
                e.setSecondsOnFire(8);
                OverloadArts.impact(level, mid(e));
            }
            Vec3 end = ray.end();
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.35F, 14), start, end, 1.0);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), k % 2 == 0 ? COLOR : Overload.CRIMSON, 0.6F, 18), start, end, 1.4);
            double len = end.distanceTo(start);
            float roll = (float) Math.atan2(dir.z, dir.x);
            for (double d = 2; d < len; d += 3) { // 羽根の羽枝: 線に沿って並ぶ炎の斬撃
                Vec3 p = start.add(dir.scale(d));
                Fx.slash(level, p, ((int) d / 3) % 2 == 0 ? COLOR : CORE, 1.0F + (float) (d / len) * 0.8F, 10, roll);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.4, 0.3, 0.4, 0.02);
            }
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.GOLD, 0.35F, 12), end, 8, 0.35);
            level.playSound(null, end.x, end.y, end.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6F, 0.8F + k * 0.05F);
        }
    }

    // ================= ストームコーラー =================

    /**
     * 嵐呼びの杖『テンペスト』（T3）: 両手の杖。右クリックで半径 16 の敵へ 2 秒かけて雷を 8 回落とす（1 回 20 の電撃・鈍化 II 2 秒、
     * 見た目だけの雷で火は付かない）。雨や雷雨の中では 16 回。1 回 250,000 FE、間隔 8 秒。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「天雷の大嵐」。範囲が半径 16 から半径 32 に広がり、頭上 18 に
     * 半径 20 の雷雲の渦と紅と金の輪が巻く。3 秒かけて雷を 32 回（雨や雷雨の中では 48 回）落とし、落ちた所は半径 4 の雷の衝撃波で、
     * 落ちた敵とまわり半径 4 の敵（6 体まで）を同じ一撃で打つ</p>
     */
    public static class StormCaller extends SkillWeaponItem {
        public static final int COLOR = 0xA0C8FF;
        /** オーバーロード: 範囲・落ちた所の衝撃波の半径・雷の回数（雨のとき）・雷を落とす時間 */
        public static final double OVER_RADIUS = 32.0, OVER_SPLASH = 4.0;
        public static final int OVER_STRIKES = 32, OVER_RAIN_STRIKES = 48, OVER_TICKS = 60;

        public StormCaller(Properties p) {
            super(p, 4_500_000, 6.0F, true, 0, 0, 0, 750_000, 160); // 1 回 250,000 → 750,000・蓄電 3,000,000 → 4,500,000（雨で最大 3,200 と T3 を超える）（2026-10-07 ユーザー指定「火力が範囲を超える兵装は消費電力で調整」）
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            return call(level, user) > 0;
        }

        /** 雷を呼ぶ（兵姫に持たせたときも同じ処理）。落とす回数（範囲に敵がいなければ 0） */
        public static int call(ServerLevel level, LivingEntity user) {
            if (OverloadArts.active(user)) return overCall(level, user);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(16, 8, 16), e -> foe(user, e) && e instanceof Enemy);
            if (targets.isEmpty()) return 0;
            int strikes = level.isRaining() || level.isThundering() ? 16 : 8;
            for (int i = 0; i < strikes; i++) {
                WeaponUtil.later(level, i * (40 / strikes), () -> {
                    List<LivingEntity> alive = targets.stream().filter(LivingEntity::isAlive).toList();
                    if (alive.isEmpty()) return;
                    LivingEntity e = alive.get(level.random.nextInt(alive.size()));
                    e.invulnerableTime = 0;
                    e.hurt(WeaponDamage.source(user, WeaponDamage.ARC, user, user), 200.0F);
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

        /** オーバーロードの大嵐: 半径 32 の敵へ 3 秒で 32 回（雨なら 48 回）、落ちた所は半径 4 の雷の衝撃波 */
        private static int overCall(ServerLevel level, LivingEntity user) {
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_RADIUS, 12, OVER_RADIUS),
                    e -> foe(user, e) && e instanceof Enemy && e.distanceTo(user) <= OVER_RADIUS + e.getBbWidth());
            if (targets.isEmpty()) return 0;
            int strikes = level.isRaining() || level.isThundering() ? OVER_RAIN_STRIKES : OVER_STRIKES;
            for (int i = 0; i < strikes; i++) {
                int index = i;
                WeaponUtil.later(level, i * OVER_TICKS / strikes, () -> {
                    List<LivingEntity> alive = targets.stream().filter(LivingEntity::isAlive).toList();
                    if (alive.isEmpty()) return;
                    overStrike(level, user, alive.get(level.random.nextInt(alive.size())), index);
                });
            }
            // 頭上の雷雲の渦（半径 20）と、紅と金の輪
            Vec3 sky = user.position().add(0, user.getBbHeight() + 18, 0);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x404860, 1.2F, 70), sky, 60, 14, 1.2, 14, 0.01);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.6F, 60), sky, 40, 16, 0.8, 16, 0.02);
            Fx.beamRing(level, sky, new Vec3(0, 1, 0), COLOR, 20.0F, 40);
            Fx.beamRing(level, sky.add(0, -0.5, 0), new Vec3(0, 1, 0), Overload.CRIMSON, 12.0F, 40);
            Fx.mandala(level, sky.add(0, -1, 0), Overload.GOLD, 8.0F, 60, false, true);
            Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, (float) OVER_RADIUS, 24);
            OverloadArts.flourish(level, user, (float) OVER_RADIUS * 0.5F);
            level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.5F, 0.6F);
            level.playSound(null, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 0.5F);
            return strikes;
        }

        /** オーバーロードの雷 1 本: 太い雷と、落ちた所の半径 4 の雷の衝撃波（落ちた敵とまわり 6 体まで） */
        private static void overStrike(ServerLevel level, LivingEntity user, LivingEntity e, int index) {
            Vec3 at = mid(e);
            List<LivingEntity> hit = new java.util.ArrayList<>();
            hit.add(e);
            for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(OVER_SPLASH),
                    o -> o != e && foe(user, o) && o instanceof Enemy && o.distanceTo(e) <= OVER_SPLASH + o.getBbWidth())) {
                if (hit.size() > 6) break;
                hit.add(o);
            }
            for (LivingEntity o : hit) {
                o.invulnerableTime = 0;
                o.hurt(WeaponDamage.source(user, WeaponDamage.ARC, user, user), 200.0F);
                o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            Vec3 top = at.add(level.random.nextDouble() * 2 - 1, 18, level.random.nextDouble() * 2 - 1);
            WeaponUtil.lightning(level, top, at, index % 3 == 0 ? Overload.CRIMSON : COLOR);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.HOT, 0.5F, 6), top, at, 1.0);
            Vec3 ground = e.position().add(0, 0.05, 0);
            Fx.shockwave(level, ground, COLOR, (float) OVER_SPLASH + 0.5F, 10);
            Fx.shockwave(level, ground, index % 2 == 0 ? Overload.CRIMSON : Overload.GOLD, (float) OVER_SPLASH * 0.6F, 12);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.35F, 10), at, 10, 0.5);
            if (index % 2 == 0) OverloadArts.impact(level, at);
            level.playSound(null, e.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.8F, 1.0F + level.random.nextFloat() * 0.5F);
        }
    }

    // ================= リフレクターブレード =================

    /**
     * 鏡刃『リフレクター』（T3）: 片手の剣。攻撃力 20 / 攻撃速度 1.4。右クリックを押している間（最大 5 秒）鏡の守り:
     * 飛び道具は跳ね返し、近接攻撃は 75% 減らして相手に 50% を返す（{@link Batch3Events}）。守りの間 0.05 秒ごと 500 FE。
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「万華鏡の鏡城」。守りの間、持ち主のまわり半径 6 を 12 枚の鏡の板が
     * 回り、半径 6 の中へ外から飛び込む飛び道具をすべて鏡の面で跳ね返す（向きを返し、持ち主のものにする）。1 秒ごとに鏡が閃き、
     * 半径 6 の敵を外へ弾き飛ばす（ダメージは無く、押し返すだけ）。近接の 75% 減・50% 返しはそのまま</p>
     */
    public static class ReflectorBlade extends SkillWeaponItem implements ValkyrieArts.Usable {
        public static final int COLOR = 0xE0F0FF, PER_TICK = 500, MAX_GUARD = 100;
        /** オーバーロード: 鏡の城の半径・鏡の板の数 */
        public static final double OVER_RADIUS = 6.0;
        public static final int OVER_MIRRORS = 12;

        public ReflectorBlade(Properties p) {
            super(p, 3_000_000, 4.0F, false, 200.0, 1.4, 0, PER_TICK, 0);
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

        /** 兵姫が鏡の守りを構える長さ（Tick、最大 {@link #MAX_GUARD}） */
        public static final int VALKYRIE_GUARD = 60;

        /** 戦術兵姫が鏡の守りを構える（{@link ValkyrieArts}）。{@link #VALKYRIE_GUARD} Tick 構えて解く。電力は onUseTick のまま */
        @Override
        public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
            if (guarding(user)) return user.getUseItem() == stack;
            return WeaponPower.has(user, stack, PER_TICK) && ValkyrieArts.hold(level, user, stack, target, VALKYRIE_GUARD);
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
            if (OverloadArts.active(user)) mirrorCastle(server, user, t);
        }

        /** オーバーロードの鏡の城の 1 Tick: 半径 6 を回る鏡の板、飛び込む飛び道具の跳ね返し、1 秒ごとの押し返しの閃き */
        private static void mirrorCastle(ServerLevel level, LivingEntity user, int t) {
            Vec3 c = mid(user);
            if (t == 0) OverloadArts.flourish(level, user, (float) OVER_RADIUS);
            for (int k = 0; k < OVER_MIRRORS; k++) {
                double a = t * 0.12 + k * Math.PI * 2 / OVER_MIRRORS;
                Vec3 p = c.add(Math.cos(a) * OVER_RADIUS, Math.sin(t * 0.1 + k) * 0.8, Math.sin(a) * OVER_RADIUS);
                Fx.one(level, Fx.opt(ModParticles.HEX.get(), k % 3 == 0 ? Overload.GOLD : COLOR, 0.45F, 3), p, Vec3.ZERO);
                if ((t + k) % 3 == 0) Fx.one(level, Fx.opt(ModParticles.GLOW.get(), k % 2 == 0 ? COLOR : Overload.CRIMSON, 0.3F, 6), p, new Vec3(0, 0.02, 0));
            }
            // 外から飛び込む飛び道具を、鏡の面で跳ね返す
            for (net.minecraft.world.entity.projectile.Projectile projectile : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                    user.getBoundingBox().inflate(OVER_RADIUS + 2))) {
                net.minecraft.world.entity.Entity owner = projectile.getOwner();
                if (owner == user || (owner != null && owner.isAlliedTo(user))) continue;
                Vec3 offset = projectile.position().subtract(c);
                double d = offset.length();
                Vec3 v = projectile.getDeltaMovement();
                if (d > OVER_RADIUS + 0.6 || d < 0.01 || v.dot(offset) >= 0) continue;
                projectile.setOwner(user);
                projectile.setDeltaMovement(v.scale(-1.2));
                projectile.hurtMarked = true;
                projectile.hasImpulse = true;
                Fx.burst(level, Fx.opt(ModParticles.HEX.get(), Overload.GOLD, 0.3F, 12), projectile.position(), 8, 0.2);
                Fx.slash(level, projectile.position(), COLOR, 1.0F, 5, level.random.nextFloat() * 6.28F);
                level.playSound(null, projectile.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.8F);
            }
            if (t % 20 != 0) return;
            // 鏡の閃き: 半径 6 の敵を外へ弾く
            Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR, (float) OVER_RADIUS, 12);
            Fx.shockwave(level, c, Overload.GOLD, (float) OVER_RADIUS * 0.8F, 10);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 1.6F, 5), c, 1, 0, 0);
            int shown = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_RADIUS),
                    e -> foe(user, e) && e.distanceTo(user) <= OVER_RADIUS + e.getBbWidth())) {
                Vec3 push = e.position().subtract(user.position()).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : push.normalize();
                e.setDeltaMovement(push.x * 1.4, 0.4, push.z * 1.4);
                e.hurtMarked = true;
                if (shown++ < 8) OverloadArts.impact(level, mid(e));
            }
            level.playSound(null, user.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
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
     * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 新星は「超新星」になり、半径 5 から半径 15 に広がる（白い大閃光・
     * 金と紅と橙の 3 重の衝撃波・光の輪・高さ 20 の光の柱・あとから 2 回広がる余波の輪）。突進は「流星突き」になり、24 ブロック・太さ 4・
     * 30 体まで貫き、通り道に金と紅の 6 本の光の帯と光の輪が並ぶ</p>
     */
    public static class NovaLance extends SkillWeaponItem {
        public static final int COLOR = 0xFFE060, PER_HIT = 30_000, NOVA_EVERY = 4;
        /** オーバーロード: 超新星の半径・流星突きの距離と太さと貫く数 */
        public static final double OVER_NOVA_RADIUS = 15.0, OVER_THRUST = 24.0, OVER_THRUST_WIDTH = 4.0;
        public static final int OVER_THRUST_MAX = 30;
        static final String TAG = "AutomataNovaHits";

        public NovaLance(Properties p) {
            super(p, 4_000_000, 10.0F, true, 260.0, 1.0, 3.0, 60_000, 60);
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
            if (OverloadArts.active(user)) return supernova(level, user, at);
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(5), e -> foe(user, e) && e.distanceToSqr(at) <= 30)) {
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 300.0F);
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

        /** オーバーロードの超新星: 半径 15 の敵に新星と同じ一撃 */
        private static int supernova(ServerLevel level, LivingEntity user, Vec3 at) {
            int n = 0;
            double r = OVER_NOVA_RADIUS;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(r),
                    e -> foe(user, e) && mid(e).distanceTo(at) <= r + e.getBbWidth())) {
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), 300.0F);
                e.setSecondsOnFire(5);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.8, 0));
                e.hurtMarked = true;
                if (n++ < 12) OverloadArts.impact(level, mid(e));
            }
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 7.0F, 8), at, 1, 0, 0);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 10.0F, 12), at, 1, 0, 0);
            Fx.star(level, at, Overload.GOLD, 6.0F, 16, Vec3.ZERO);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.6F, 18), at, 70, 1.4);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.CRIMSON, 0.5F, 16), at, 40, 1.0);
            Fx.shockwave(level, at, COLOR, (float) r, 18);
            Fx.shockwave(level, at, Overload.CRIMSON, (float) r * 0.7F, 20);
            Fx.shockwave(level, at, 0xFF8030, (float) r * 0.45F, 22);
            Fx.beamRing(level, at, new Vec3(0, 1, 0), Overload.GOLD, (float) r, 16);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.HOT, 0.8F, 20), at, at.add(0, 20, 0), 0.8);
            for (int w = 1; w <= 2; w++) { // あとから広がる余波の輪
                WeaponUtil.later(level, w * 5, () -> {
                    Fx.shockwave(level, at, Overload.GOLD, (float) r + 1, 14);
                    Fx.beamRing(level, at, new Vec3(0, 1, 0), COLOR, (float) r * 0.8F, 12);
                });
            }
            OverloadArts.flourish(level, user, (float) r);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.6F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.0F, 0.7F);
            return n;
        }

        @Override
        protected boolean skill(ServerLevel level, LivingEntity user, ItemStack stack) {
            thrust(level, user);
            return true;
        }

        /** 突進（兵姫に持たせたときも同じ処理）。貫いた数 */
        public static int thrust(ServerLevel level, LivingEntity user) {
            if (OverloadArts.active(user)) return meteorThrust(level, user);
            Vec3 look = user.getLookAngle();
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, mid(user), look, 8, 1.5, 10);
            int n = 0;
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                e.hurt(WeaponUtil.melee(user), 250.0F);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 8), mid(e), 8, 0.3);
                n++;
            }
            user.setDeltaMovement(look.scale(2.2).add(0, 0.1, 0));
            user.hurtMarked = true;
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.35F, 10), mid(user), mid(user).add(look.scale(8)), 0.35);
            level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.2F);
            return n;
        }

        /** オーバーロードの流星突き: 24 ブロック・太さ 4・30 体まで貫く */
        private static int meteorThrust(ServerLevel level, LivingEntity user) {
            Vec3 look = user.getLookAngle(), from = mid(user);
            WeaponUtil.Ray ray = WeaponUtil.ray(level, user, from, look, OVER_THRUST, OVER_THRUST_WIDTH, OVER_THRUST_MAX);
            int n = 0;
            for (LivingEntity e : ray.hits()) {
                if (!foe(user, e)) continue;
                e.hurt(WeaponUtil.melee(user), 250.0F);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.35F, 10), mid(e), 8, 0.35);
                if (n++ < 12) OverloadArts.impact(level, mid(e));
            }
            user.setDeltaMovement(look.scale(3.2).add(0, 0.1, 0));
            user.hurtMarked = true;
            Vec3 end = ray.end();
            Vec3 side = look.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(look).normalize();
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), Overload.HOT, 0.6F, 14), from, end, 0.5);
            for (int k = 0; k < 6; k++) { // 太さ 4 の光の帯（まわりに 6 本）
                double a = k * Math.PI / 3;
                Vec3 off = side.scale(Math.cos(a) * OVER_THRUST_WIDTH * 0.5).add(up.scale(Math.sin(a) * OVER_THRUST_WIDTH * 0.5));
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), k % 2 == 0 ? COLOR : Overload.CRIMSON, 0.4F, 14), from.add(off), end.add(off), 1.0);
            }
            double len = end.distanceTo(from);
            for (double d = 3; d < len; d += 4) {
                Fx.beamRing(level, from.add(look.scale(d)), look, ((int) d / 4) % 2 == 0 ? COLOR : Overload.GOLD, (float) OVER_THRUST_WIDTH * 0.6F, 10);
            }
            Fx.slash(level, end, COLOR, 3.0F, 8, 0F);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.GOLD, 0.45F, 14), end, 24, 0.7);
            OverloadArts.flourish(level, user, 6.0F);
            level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.5F, 0.8F);
            level.playSound(null, user.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5F, 0.6F);
            return n;
        }
    }
}
