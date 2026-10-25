package com.automatavalkyries.entity;

import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 劇場の舞台装置（2026-10-24 ユーザー指定「舞台装置×リボン舞踏」）。技が置いていく仕掛けで、エレノアが次の技へ移っても動き続ける。
 * どれも前兆（床の印・降りてくる糸）があり、避けられる。見た目は粒子だけ（ブロックは置かない）。数値は【仮】。
 * <ul>
 *   <li>{@link Spotlight} スポットライト: 床の光の輪が相手を追い、止まってから光の柱が落ちる</li>
 *   <li>{@link FlyDrop} 吊り物落とし: 床の影が濃くなり、上から吊り物（重し）が落ちる</li>
 *   <li>{@link StringCage} 操り糸の檻: 6 本の糸の輪が相手の周りで縮む。糸に触れると痛い。1 か所だけ糸の無い隙間がある</li>
 *   <li>{@link Trapdoor} 奈落の迫り: 床が市松に光り、光った枡が開いて下へ引き込む（2 回、互い違い）</li>
 *   <li>{@link Spokes} 円舞曲の輻: エレノアから伸びるリボンの輻が、舞台をゆっくり回る</li>
 *   <li>{@link Chord} 千秋楽の綱: 舞台を横切る低い綱。跳べば越えられる</li>
 *   <li>{@link Sector} 大見得の扇: 扇形の床が光り、巨大なリボンが薙ぐ</li>
 * </ul>
 */
final class ElenorStage {

    private ElenorStage() {}

    /** 舞台装置 1 つ。tick が false を返したら片付ける */
    abstract static class Prop {
        final ElenorArts.Move source;
        int age;
        /** 向けた相手のティア（色と飾りの派手さ。{@link ElenorFx#pal}・{@link ElenorFx#tierFlourish}） */
        int tier;

        Prop(ElenorArts.Move source) {
            this.source = source;
        }

        /** 1 Tick 進める。終わったら false */
        abstract boolean tick(ServerLevel level, ElenorArts arts);
    }

    /** 同じ相手に続けて当てない間（Tick）を数える札 */
    private static final class Cooldowns {
        private final Map<UUID, Integer> next = new HashMap<>();

        boolean ready(LivingEntity e, int now, int gap) {
            Integer at = next.get(e.getUUID());
            if (at != null && now < at) return false;
            next.put(e.getUUID(), now + gap);
            return true;
        }
    }

    private static boolean between(double y, double low, double high) {
        return y >= low && y <= high;
    }

    /** 点 p と線分 ab の水平の距離 */
    static double flatDistance(Vec3 p, Vec3 a, Vec3 b) {
        double abx = b.x - a.x, abz = b.z - a.z;
        double len = abx * abx + abz * abz;
        double t = len < 1.0e-6 ? 0 : Mth.clamp(((p.x - a.x) * abx + (p.z - a.z) * abz) / len, 0.0, 1.0);
        double dx = p.x - (a.x + abx * t), dz = p.z - (a.z + abz * t);
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- スポットライト ----

    /** 床の光の輪が相手を追い（chase Tick）、止まって強く光り（lock Tick）、上から光の柱が落ちる */
    static final class Spotlight extends Prop {
        private final LivingEntity follow;
        private Vec3 at;
        private final int chase, lock;
        private final double radius;
        private final float damage;

        Spotlight(ElenorArts.Move source, LivingEntity follow, int chase, int lock, double radius, float damage) {
            super(source);
            this.follow = follow;
            this.at = follow.position();
            this.chase = chase;
            this.lock = lock;
            this.radius = radius;
            this.damage = damage;
        }

        @Override
        boolean tick(ServerLevel level, ElenorArts arts) {
            age++;
            if (age <= chase && follow.isAlive()) { // 追う: 相手が光の輪に着く頃にいる所へ、限られた速さで寄る
                Vec3 want = arts.tactics().leadFeet(follow, Math.max(4, chase - age), 0.6);
                Vec3 step = new Vec3(want.x - at.x, 0, want.z - at.z);
                double len = step.length();
                if (len > 0.3) step = step.scale(0.3 / len);
                at = new Vec3(at.x + step.x, follow.getY() * 0.2 + at.y * 0.8, at.z + step.z);
            }
            boolean locked = age > chase;
            int color = locked ? ElenorFx.pal(tier, 2) : ElenorFx.light(tier);
            if (age % 2 == 0) ElenorFx.floorRing(level, at, radius, 16, color);
            if (age % 4 == 0) { // 上から降りる光の円錐
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6 + age * 0.1;
                    double h = 2.0 + (i % 3) * 2.5;
                    double r = radius * (1.0 - h / 9.0);
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.3F, 8), at.add(Math.cos(a) * r, h, Math.sin(a) * r), Vec3.ZERO);
                }
            }
            if (age == chase + 1) level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.6F);
            if (age < chase + lock) return true;
            // 光の柱が落ちる
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), ElenorFx.light(tier), 0.7F, 10), at.add(0, 10.0, 0), at, 0.5);
            ElenorFx.streakLine(level, at.add(0, 9.0, 0), at, 10, ElenorFx.light(tier));
            Fx.shockwave(level, at.add(0, 0.1, 0), ElenorFx.pal(tier, 2), (float) radius + 0.5F, 10);
            ElenorFx.tierFlourish(level, at.add(0, 0.6, 0), tier, radius);
            Fx.one(level, Fx.opt(ModParticles.BLOOM.get(), ElenorFx.light(tier), (float) radius, 10), at.add(0, 0.4, 0), Vec3.ZERO);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.7F);
            for (LivingEntity foe : arts.foes(level, new AABB(at, at).inflate(radius, 4.0, radius))) {
                if (Math.hypot(foe.getX() - at.x, foe.getZ() - at.z) <= radius && between(foe.getY(), at.y - 1.5, at.y + 4.0)) {
                    arts.hurtBy(foe, damage, source);
                }
            }
            return false;
        }
    }

    // ---- 吊り物落とし ----

    /** 床の影が濃くなり、delay Tick で上から吊り物が落ちる（最後の 10 Tick は落ちてくる重しが見える） */
    static final class FlyDrop extends Prop {
        private final Vec3 at;
        private final int delay;
        private final double radius;
        private final float damage;

        FlyDrop(ElenorArts.Move source, Vec3 at, int delay, double radius, float damage) {
            super(source);
            this.at = at;
            this.delay = delay;
            this.radius = radius;
            this.damage = damage;
        }

        @Override
        boolean tick(ServerLevel level, ElenorArts arts) {
            age++;
            double progress = age / (double) delay;
            if (age % 3 == 0) {
                ElenorFx.floorRing(level, at, radius * (1.1 - 0.3 * progress), 14, ElenorFx.shade(tier)); // 影
                ElenorFx.floorRing(level, at, radius * 0.3, 6, progress > 0.6 ? ElenorFx.pal(tier, 2) : ElenorFx.pal(tier, 0));
            }
            if (age == 1) { // 吊り糸が見える
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), ElenorFx.light(tier), 0.15F, delay), at.add(0, 3.0, 0), at.add(0, 12.0, 0), 0.8);
            }
            int fall = delay - age;
            if (fall >= 0 && fall < 10) { // 落ちてくる重し（緞帳の布で包んだ吊り物）
                Vec3 p = at.add(0, 1.0 + fall * 1.0, 0);
                Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), fall % 2 == 0 ? ElenorFx.shade(tier) : ElenorFx.pal(tier, 5), 1.4F, 3), p, Vec3.ZERO);
                Fx.one(level, Fx.opt(ModParticles.STREAK.get(), ElenorFx.light(tier), 0.6F, 4), p.add(0, 1.0, 0), new Vec3(0, -0.8, 0));
            }
            if (age < delay) return true;
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.6F);
            ElenorFx.lace(level, at, ElenorFx.pal(tier, 2), (float) radius + 0.8F, 14);
            ElenorFx.tierFlourish(level, at.add(0, 0.6, 0), tier, radius);
            Fx.shockwave(level, at.add(0, 0.1, 0), ElenorFx.pal(tier, 0), (float) radius + 1.0F, 10);
            ElenorFx.petalBurst(level, at.add(0, 0.5, 0), 14, 0.35, ElenorFx.pal(tier, 0));
            Fx.spray(level, Fx.opt(ModParticles.SPARK.get(), ElenorFx.GOLD, 0.35F, 8), at.add(0, 0.3, 0), 6, 0.6, 0.15);
            for (LivingEntity foe : arts.foes(level, new AABB(at, at).inflate(radius, 3.0, radius))) {
                if (Math.hypot(foe.getX() - at.x, foe.getZ() - at.z) > radius || !between(foe.getY(), at.y - 1.5, at.y + 3.0)) continue;
                arts.hurtBy(foe, damage, source);
                Vec3 away = new Vec3(foe.getX() - at.x, 0, foe.getZ() - at.z);
                if (away.lengthSqr() > 1.0e-4) away = away.normalize();
                foe.push(away.x * 0.6, 0.3, away.z * 0.6);
                foe.hurtMarked = true;
            }
            return false;
        }
    }

    // ---- 操り糸の檻 ----

    /**
     * 相手の周りに上から 6 本の糸が降り（telegraph Tick）、糸の輪が半径 r0 から 1.2 まで縮む（shrink Tick）。糸と糸の間に張った糸に触れると
     * 痛く、少し鈍る。1 か所だけ糸の無い隙間があり、そこからなら無傷で抜けられる。縮み切ると糸が引かれ、中にいた相手は宙へ吊り上げられる
     */
    static final class StringCage extends Prop {
        private static final int STRINGS = 6;
        private final Vec3 center;
        private final double gapAngle;
        private final double r0;
        private final int telegraph, shrink;
        private final Cooldowns touched = new Cooldowns();

        StringCage(ElenorArts.Move source, Vec3 center, double gapAngle, double r0, int telegraph, int shrink) {
            super(source);
            this.center = center;
            this.gapAngle = gapAngle;
            this.r0 = r0;
            this.telegraph = telegraph;
            this.shrink = shrink;
        }

        private double radius() {
            if (age <= telegraph) return r0;
            return r0 - (r0 - 1.2) * Math.min(1.0, (age - telegraph) / (double) shrink);
        }

        private Vec3 post(int i, double r) {
            double a = gapAngle + Math.PI * 2 * (i + 0.5) / STRINGS + age * 0.01; // ゆっくり回る
            return new Vec3(center.x + Math.cos(a) * r, center.y, center.z + Math.sin(a) * r);
        }

        @Override
        boolean tick(ServerLevel level, ElenorArts arts) {
            age++;
            double r = radius();
            if (age <= telegraph) { // 前兆: 上から糸が降りてくる
                double drop = 9.0 - 9.0 * age / telegraph;
                if (age % 2 == 0) {
                    for (int i = 0; i < STRINGS; i++) {
                        Vec3 p = post(i, r);
                        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), ElenorFx.light(tier), 0.12F, 4), p.add(0, 10.0, 0), p.add(0, drop + 0.2, 0), 1.0);
                    }
                }
                if (age == 1) level.playSound(null, center.x, center.y, center.z, SoundEvents.ARMOR_EQUIP_LEATHER, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 1.4F);
                return true;
            }
            int now = age;
            if (age % 2 == 0) { // 糸（縦の糸と、間に張った横の糸。隙間の 1 辺だけ張らない）
                for (int i = 0; i < STRINGS; i++) {
                    Vec3 a = post(i, r), b = post((i + 1) % STRINGS, r);
                    Fx.line(level, Fx.opt(ModParticles.GLOW.get(), ElenorFx.light(tier), 0.12F, 4), a.add(0, 0.2, 0), a.add(0, 6.0, 0), 1.2);
                    if (i == STRINGS - 1) continue; // 隙間
                    for (double h : new double[]{0.4, 1.2, 2.0}) {
                        Fx.line(level, Fx.opt(ModParticles.RIBBON.get(), i % 2 == 0 ? ElenorFx.pal(tier, 0) : ElenorFx.VIOLET, 0.14F, 4), a.add(0, h, 0), b.add(0, h, 0), 0.7);
                    }
                }
            }
            for (LivingEntity foe : arts.foes(level, new AABB(center, center).inflate(r0 + 1.0, 3.0, r0 + 1.0))) {
                if (!between(foe.getY(), center.y - 1.0, center.y + 2.4)) continue;
                for (int i = 0; i < STRINGS - 1; i++) {
                    if (flatDistance(foe.position(), post(i, r), post(i + 1, r)) > 0.55) continue;
                    if (touched.ready(foe, now, 10)) {
                        arts.hurtBy(foe, 6.0F, source);
                        foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                        level.playSound(null, foe.blockPosition(), SoundEvents.TRIPWIRE_CLICK_ON, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 1.6F);
                    }
                    break;
                }
            }
            if (age < telegraph + shrink) return true;
            // 糸が引かれる: 中にいた相手は宙へ吊り上げられる
            level.playSound(null, center.x, center.y, center.z, SoundEvents.CROSSBOW_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.6F);
            ElenorFx.petalBurst(level, center.add(0, 1.0, 0), 16, 0.4, ElenorFx.light(tier));
            ElenorFx.tierFlourish(level, center.add(0, 1.0, 0), tier, r);
            for (int i = 0; i < STRINGS; i++) ElenorFx.streakLine(level, post(i, r), post(i, r).add(0, 8.0, 0), 4, ElenorFx.light(tier));
            for (LivingEntity foe : arts.foes(level, new AABB(center, center).inflate(r + 0.5, 3.0, r + 0.5))) {
                if (Math.hypot(foe.getX() - center.x, foe.getZ() - center.z) > r + 0.4 || !between(foe.getY(), center.y - 1.0, center.y + 2.4)) continue;
                arts.hurtBy(foe, 10.0F, source);
                foe.setDeltaMovement(foe.getDeltaMovement().x * 0.2, 0.9, foe.getDeltaMovement().z * 0.2);
                foe.hurtMarked = true;
                ElenorFx.ribbonSwirl(level, foe.position(), 0.6, 2.0, 0.0);
            }
            return false;
        }
    }

    // ---- 奈落の迫り ----

    /** 2 ブロック四方の枡を 5×5 に並べ、parity の枡（市松）が delay Tick 光ってから開く。開いた枡の上の相手を下へ引き込む */
    static final class Trapdoor extends Prop {
        private static final int TILES = 5;
        private final int baseX, baseZ;
        private final double y;
        private final int parity, delay;

        Trapdoor(ElenorArts.Move source, Vec3 center, int parity, int delay) {
            super(source);
            this.baseX = Mth.floor(center.x / 2.0) * 2 - TILES / 2 * 2;
            this.baseZ = Mth.floor(center.z / 2.0) * 2 - TILES / 2 * 2;
            this.y = center.y;
            this.parity = parity;
            this.delay = delay;
        }

        private boolean lit(int i, int k) {
            return ((i + k) & 1) == parity;
        }

        @Override
        boolean tick(ServerLevel level, ElenorArts arts) {
            age++;
            boolean open = age >= delay;
            if (age % 4 == 0 || age == delay) {
                int color = open ? ElenorFx.shade(tier) : age > delay - 8 ? ElenorFx.pal(tier, 5) : ElenorFx.pal(tier, 2);
                for (int i = 0; i < TILES; i++) {
                    for (int k = 0; k < TILES; k++) {
                        if (!lit(i, k)) continue;
                        double x0 = baseX + i * 2, z0 = baseZ + k * 2;
                        for (int d = 0; d < 4; d++) { // 枡の縁
                            double u = 0.25 + d * 0.5;
                            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.16F, 6), new Vec3(x0 + u, y + 0.08, z0 + 0.1), Vec3.ZERO);
                            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.16F, 6), new Vec3(x0 + u, y + 0.08, z0 + 1.9), Vec3.ZERO);
                            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.16F, 6), new Vec3(x0 + 0.1, y + 0.08, z0 + u), Vec3.ZERO);
                            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.16F, 6), new Vec3(x0 + 1.9, y + 0.08, z0 + u), Vec3.ZERO);
                        }
                        if (open && age == delay) {
                            Fx.burst(level, Fx.opt(ModParticles.VOID_SHARD.get(), ElenorFx.shade(tier), 0.3F, 16), new Vec3(x0 + 1, y + 0.2, z0 + 1), 3, 0.15);
                        }
                    }
                }
            }
            if (age == delay) {
                Vec3 mid = new Vec3(baseX + TILES, y, baseZ + TILES);
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.IRON_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
                ElenorFx.tierFlourish(level, mid.add(0, 0.5, 0), tier, TILES);
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.WITHER_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 0.5F);
                for (LivingEntity foe : arts.foes(level, new AABB(baseX, y - 1.5, baseZ, baseX + TILES * 2, y + 1.6, baseZ + TILES * 2))) {
                    int i = Mth.floor((foe.getX() - baseX) / 2.0), k = Mth.floor((foe.getZ() - baseZ) / 2.0);
                    if (i < 0 || k < 0 || i >= TILES || k >= TILES || !lit(i, k)) continue;
                    arts.hurtBy(foe, 9.0F, source);
                    foe.setDeltaMovement(foe.getDeltaMovement().x * 0.3, -0.8, foe.getDeltaMovement().z * 0.3); // 奈落へ引き込む
                    foe.hurtMarked = true;
                    foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                }
            }
            return age < delay + 8;
        }
    }

    // ---- 円舞曲の輻 ----

    /** center（エレノア）から count 本のリボンの輻（長さ length）が伸び、回る。telegraph Tick は点線だけ、life Tick で消える。途中で回る向きが逆になる */
    static final class Spokes extends Prop {
        private final ElenorEntity elenor;
        private final int count, telegraph, life;
        private final double length;
        private double angle, spin;
        private final Cooldowns touched = new Cooldowns();

        Spokes(ElenorArts.Move source, ElenorEntity elenor, int count, double length, double startAngle, double spin, int telegraph, int life) {
            super(source);
            this.elenor = elenor;
            this.count = count;
            this.length = length;
            this.angle = startAngle;
            this.spin = spin;
            this.telegraph = telegraph;
            this.life = life;
        }

        @Override
        boolean tick(ServerLevel level, ElenorArts arts) {
            age++;
            boolean live = age > telegraph;
            if (live) angle += spin;
            if (age == telegraph + 1) ElenorFx.tierFlourish(level, elenor.position().add(0, 1.0, 0), tier, 3.0);
            if (age == telegraph + (life - telegraph) / 2) { // 折り返し: 向きが逆になる
                spin = -spin;
                level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_RESONATE, elenor.getSoundSource(), 1.2F, 1.4F);
            }
            Vec3 c = elenor.position();
            for (int i = 0; i < count; i++) {
                double a = angle + Math.PI * 2 * i / count;
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                if (age % 2 == 0) {
                    double step = live ? 1.1 : 2.0;
                    for (double d = 1.2; d <= length; d += step) {
                        Vec3 p = c.add(dir.scale(d)).add(0, 0.9, 0);
                        if (live) {
                            Fx.one(level, Fx.opt(ModParticles.RIBBON.get(), ((int) (d / step) + i) % 2 == 0 ? ElenorFx.pal(tier, 0) : ElenorFx.shade(tier), 0.32F, 3), p, Vec3.ZERO);
                        } else {
                            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), ElenorFx.light(tier), 0.14F, 3), p.add(0, -0.8, 0), Vec3.ZERO);
                        }
                    }
                }
                if (!live) continue;
                Vec3 end = c.add(dir.scale(length));
                for (LivingEntity foe : arts.foes(level, new AABB(c, c).inflate(length, 3.0, length))) {
                    if (foe.distanceToSqr(c) < 1.0 || !between(foe.getY(), c.y - 1.0, c.y + 2.2)) continue;
                    if (flatDistance(foe.position(), c, end) <= 0.8 && touched.ready(foe, age, 12)) arts.hurtBy(foe, 7.0F, source);
                }
            }
            return age < life;
        }
    }

    // ---- 千秋楽の綱 ----

    /** 舞台を横切る低い綱（高さ 0.5）。telegraph Tick は点線、その後 life Tick 張られて回る。跳べば越えられる */
    static final class Chord extends Prop {
        private final Vec3 center;
        private final double radius, offset;
        private double angle;
        private final double spin;
        private final int telegraph, life;
        private final Cooldowns touched = new Cooldowns();

        Chord(ElenorArts.Move source, Vec3 center, double radius, double angle, double offset, double spin, int telegraph, int life) {
            super(source);
            this.center = center;
            this.radius = radius;
            this.angle = angle;
            this.offset = offset;
            this.spin = spin;
            this.telegraph = telegraph;
            this.life = life;
        }

        @Override
        boolean tick(ServerLevel level, ElenorArts arts) {
            age++;
            boolean live = age > telegraph;
            if (live) angle += spin;
            // 弦: 中心から offset 離れた、向き angle に直交する線分
            Vec3 normal = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 along = new Vec3(-normal.z, 0, normal.x);
            double half = Math.sqrt(Math.max(0, radius * radius - offset * offset));
            Vec3 mid = center.add(normal.scale(offset)).add(0, 0.5, 0);
            Vec3 a = mid.add(along.scale(half)), b = mid.subtract(along.scale(half));
            if (age == telegraph + 1) { // 綱が張られた: 両端の結び目で炸裂
                ElenorFx.tierFlourish(level, a.add(0, 0.5, 0), tier, 1.2);
                ElenorFx.tierFlourish(level, b.add(0, 0.5, 0), tier, 1.2);
            }
            if (age % 2 == 0) {
                Fx.line(level, Fx.opt(live ? ModParticles.RIBBON.get() : ModParticles.GLOW.get(), live ? ElenorFx.pal(tier, 5) : ElenorFx.pal(tier, 2), live ? 0.2F : 0.12F, 3), a, b, live ? 0.6 : 1.4);
                if (live) {
                    Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), ElenorFx.shade(tier), 1.0F, 3), a.add(0, 0.6, 0), Vec3.ZERO); // 綱の結び目
                    Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), ElenorFx.shade(tier), 1.0F, 3), b.add(0, 0.6, 0), Vec3.ZERO);
                }
            }
            if (live) {
                for (LivingEntity foe : arts.foes(level, new AABB(center, center).inflate(radius, 3.0, radius))) {
                    double feet = foe.getY();
                    if (feet > mid.y + 0.15 || feet < mid.y - 2.0) continue; // 跳んで越えた
                    if (flatDistance(foe.position(), a, b) > 0.6 || !touched.ready(foe, age, 10)) continue;
                    arts.hurtBy(foe, 7.0F, source);
                    foe.push(normal.x * 0.4 * Math.signum(foe.position().subtract(mid).dot(normal) + 1.0e-3), 0.25, normal.z * 0.4 * Math.signum(foe.position().subtract(mid).dot(normal) + 1.0e-3));
                    foe.hurtMarked = true;
                }
            }
            return age < telegraph + life;
        }
    }

    // ---- 大見得の扇 ----

    /** center から向き angle、開き 120° の扇（半径 radius）の床が telegraph Tick 光り、巨大なリボンが薙ぐ */
    static final class Sector extends Prop {
        private final Vec3 center;
        private final double angle, radius;
        private final int telegraph;
        private final float damage;

        Sector(ElenorArts.Move source, Vec3 center, double angle, double radius, int telegraph, float damage) {
            super(source);
            this.center = center;
            this.angle = angle;
            this.radius = radius;
            this.telegraph = telegraph;
            this.damage = damage;
        }

        private static final double HALF = Math.toRadians(60.0);

        @Override
        boolean tick(ServerLevel level, ElenorArts arts) {
            age++;
            if (age < telegraph) {
                if (age % 3 == 0) {
                    int color = age > telegraph - 6 ? ElenorFx.pal(tier, 5) : ElenorFx.pal(tier, 2);
                    for (double r = 2.0; r <= radius; r += 2.5) { // 扇の弧
                        int points = (int) (r * 1.2) + 3;
                        for (int i = 0; i <= points; i++) {
                            double a = angle - HALF + 2 * HALF * i / points;
                            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), color, 0.16F, 6), center.add(Math.cos(a) * r, 0.08, Math.sin(a) * r), Vec3.ZERO);
                        }
                    }
                    for (double edge : new double[]{angle - HALF, angle + HALF}) { // 扇の縁
                        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), color, 0.18F, 6), center.add(0, 0.08, 0),
                                center.add(Math.cos(edge) * radius, 0.08, Math.sin(edge) * radius), 0.8);
                    }
                }
                return true;
            }
            // 巨大なリボンが扇を薙ぐ
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            for (double r = 3.0; r <= radius; r += 3.0) ElenorFx.ribbonArc(level, center.add(0, -0.6, 0), dir, r, 1.0, tier);
            ElenorFx.ribbonWhip(level, center, dir, radius * 0.6, 1, tier);
            for (int i = 0; i < 6; i++) {
                double a = angle - HALF + 2 * HALF * i / 5;
                ElenorFx.streakLine(level, center.add(0, 1.0, 0), center.add(Math.cos(a) * radius, 1.0, Math.sin(a) * radius), 4, ElenorFx.pal(tier, 5));
            }
            level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.5F);
            ElenorFx.tierFlourish(level, center.add(dir.scale(radius * 0.5)).add(0, 0.8, 0), tier, radius * 0.4);
            for (LivingEntity foe : arts.foes(level, new AABB(center, center).inflate(radius, 3.0, radius))) {
                double dx = foe.getX() - center.x, dz = foe.getZ() - center.z;
                double dist = Math.hypot(dx, dz);
                if (dist > radius || !between(foe.getY(), center.y - 1.0, center.y + 3.0)) continue;
                double diff = Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx) - angle));
                if (Math.abs(diff) > 60.0 && dist > 1.2) continue;
                arts.hurtBy(foe, damage, source);
                if (dist > 1.0e-3) foe.push(dx / dist * 0.8, 0.35, dz / dist * 0.8);
                foe.hurtMarked = true;
            }
            return false;
        }
    }
}
