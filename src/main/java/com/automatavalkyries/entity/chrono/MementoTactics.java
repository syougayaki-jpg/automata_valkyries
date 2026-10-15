package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * メメントモリの戦い方の頭（2026-10-15 ユーザー指定「攻撃性能はこんなものなので AI 強化して」。質問で: 隠れても攻める・動きを読む狙い撃ち・状況で技を選ぶ。数値は【仮】）。
 * <ul>
 *   <li>相手の動きを毎 Tick 見張る（なめらかにした速さ・平均の速さ・地面にいるか・屋根の下か）</li>
 *   <li>動きを読む: 狙い撃ちは、相手が今の速さで動き続けたら弾が届く時にいる所（{@link #lead}）へ撃つ。向きを変えれば避けられる</li>
 *   <li>状況で技を選ぶ: 使える技に点を付けて一番高い物を使う（{@link #score}）。止まっている相手には檻と大輪、逃げ回る相手には雨と綾織、
 *       遠い相手には幾何弾幕、飛ぶ相手には乱舞、屋根の下には雨を使わない。前の回に 1 発も当たらなかった技・続けて同じ技は点を下げる</li>
 *   <li>隠れても攻める: 相手が見えなくなったら、見える所（低い所も探す）へ回り込み、しばらくその高さで戦う。
 *       1.5 秒たっても見えなければいぶり出す: 壁を抜ける蝶ビットを放ち、相手のまわりの開いた所から止まって向き直る光弾を撃つ（地形は壊さない）</li>
 * </ul>
 */
public final class MementoTactics {

    static final int SWARM = 1, BARRAGE = 2, CAGE = 3, MOON = 4, BLOOM = 5, RAIN = 6, LATTICE = 7, SPELL = 8;
    /** 見えないまま、いぶり出しを始めるまでの Tick */
    static final int FLUSH_AFTER = 30;

    private final MementoMoriEntity mori;
    // 相手の動き
    private LivingEntity tracked;
    private Vec3 lastPos;
    private Vec3 vel = Vec3.ZERO;
    private double avgSpeed;
    private int airTicks;
    // 見えない間
    private int unseen, sightCheck, lastFlushNotice = -1000;
    private Vec3 sightSpot;
    // 当たり具合（技ごと）
    private final double[] hitEma = new double[SPELL + 1];
    private final int[] lastHits = new int[SPELL + 1], uses = new int[SPELL + 1];
    private int credit, creditHits, creditUntil = -1, hurtStamp = Integer.MIN_VALUE, last1, last2;

    MementoTactics(MementoMoriEntity mori) {
        this.mori = mori;
    }

    // ---- 見張り ----

    /** 毎 Tick: 相手の動きと、こちらの攻撃が当たったかを見る */
    void observe(LivingEntity t) {
        Vec3 pos = t.position();
        if (t != tracked || lastPos == null) {
            tracked = t;
            vel = Vec3.ZERO;
            avgSpeed = 0;
            hurtStamp = t.getLastHurtByMobTimestamp();
        } else {
            Vec3 step = pos.subtract(lastPos);
            if (step.lengthSqr() > 4.0) step = Vec3.ZERO;        // テレポートなどの飛びは数えない
            vel = vel.scale(0.6).add(step.scale(0.4));
            avgSpeed = avgSpeed * 0.95 + step.multiply(1, 0, 1).length() * 0.05;
        }
        lastPos = pos;
        airTicks = t.onGround() ? 0 : airTicks + 1;
        if (t.getLastHurtByMob() == mori && t.getLastHurtByMobTimestamp() != hurtStamp) {
            hurtStamp = t.getLastHurtByMobTimestamp();
            if (credit != 0 && mori.tickCount <= creditUntil) creditHits++;
        }
        if (credit != 0 && creditUntil >= 0 && mori.tickCount > creditUntil) settle();
    }

    /** なめらかにした相手の速さ（ブロック/Tick） */
    Vec3 velocity(LivingEntity t) {
        return t == tracked ? vel : Vec3.ZERO;
    }

    boolean slow() {
        return avgSpeed < 0.06;
    }

    boolean fast() {
        return avgSpeed > 0.16;
    }

    boolean flying() {
        return airTicks > 20;
    }

    /** 相手の頭の上 {@value MementoMoriEntity#RAIN_HEIGHT} ブロックの間に屋根があるか */
    boolean roofed(LivingEntity t) {
        Vec3 eye = t.getEyePosition();
        return mori.level().clip(new ClipContext(eye, eye.add(0, MementoMoriEntity.RAIN_HEIGHT, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t))
                .getType() != HitResult.Type.MISS;
    }

    // ---- 動きを読む ----

    /** from から speed の弾で、相手の今の速さが続くとして届く時の所を狙う（amount = 読みの強さ 0〜1。上下の動きは弱めに読む） */
    Vec3 lead(LivingEntity t, Vec3 from, double speed, double amount) {
        return leadPoint(MementoMoriEntity.aimAt(t), velocity(t), from, speed, amount);
    }

    /** 読みの計算（試験からも呼ぶ）: aim の所を v で動く相手へ、from から speed の弾が届く所（届くまで最大 40 Tick 分） */
    public static Vec3 leadPoint(Vec3 aim, Vec3 v, Vec3 from, double speed, double amount) {
        Vec3 move = new Vec3(v.x, v.y * 0.3, v.z).scale(amount);
        Vec3 p = aim;
        for (int i = 0; i < 3; i++) {
            double time = Math.min(40.0, p.distanceTo(from) / Math.max(0.05, speed));
            p = aim.add(move.scale(time));
        }
        return p;
    }

    // ---- 状況で技を選ぶ ----

    /** 使える技（cooldown が 0 以下）から一番点の高い物。無ければ 0 */
    int choose(LivingEntity t, int swarm, int barrage, int cage, int bloom, int rain, int lattice) {
        int best = 0;
        double bestScore = -1.0e9;
        boolean roof = roofed(t);
        int[][] ready = {{SWARM, swarm}, {BARRAGE, barrage}, {CAGE, cage}, {BLOOM, bloom}, {RAIN, rain}, {LATTICE, lattice}};
        for (int[] m : ready) {
            if (m[1] > 0) continue;
            double s = score(m[0], t, roof) + mori.getRandom().nextDouble() * 0.8;
            if (s > bestScore) {
                bestScore = s;
                best = m[0];
            }
        }
        return best;
    }

    double score(int move, LivingEntity t, boolean roof) {
        double dist = t.position().subtract(mori.position()).multiply(1, 0, 1).length();
        double s = 1.0;
        switch (move) {
            case CAGE -> s += (slow() ? 3.0 : 0) - (fast() ? 1.5 : 0);
            case BLOOM -> s += (slow() ? 2.0 : 0) + (dist < 14 ? 1.0 : 0);
            case RAIN -> s += (fast() ? 2.0 : 0) + (flying() ? 1.0 : 0) - (roof ? 6.0 : 0);
            case LATTICE -> s += (fast() ? 2.0 : 0) + (dist > 10 ? 0.5 : 0);
            case BARRAGE -> s += (dist > 18 ? 2.0 : 0) + (slow() ? 0.5 : 0);
            case SWARM -> s += (fast() ? 1.0 : 0) + (flying() ? 2.0 : 0) + (roof ? 1.0 : 0);
            default -> { }
        }
        s += Math.min(3.0, hitEma[move]) * 0.8;                     // よく当たる技
        if (uses[move] > 0 && lastHits[move] == 0) s -= 1.5;        // 前の回に 1 発も当たらなかった技
        if (move == last1) s -= 3.0;                                // 続けて同じ技は使わない
        else if (move == last2) s -= 1.0;
        return s;
    }

    /** 技を始めた: 当たりを数え始める（前の技の数えが残っていれば締める） */
    void started(int move) {
        if (credit != 0) settle();
        credit = move;
        creditHits = 0;
        creditUntil = -1; // 終わるまで数え続ける
        last2 = last1;
        last1 = move;
    }

    /** 技が終わった: そこから 2 秒の間に当たった分までその技の手柄にする */
    void ended(int move) {
        if (credit == move) creditUntil = mori.tickCount + 40;
    }

    private void settle() {
        int m = credit;
        if (m > 0 && m < hitEma.length) {
            hitEma[m] = hitEma[m] * 0.6 + creditHits * 0.4;
            lastHits[m] = creditHits;
            uses[m]++;
        }
        credit = 0;
        creditHits = 0;
        creditUntil = -1;
    }

    // ---- 隠れても攻める ----

    /** 技を使っていない間の毎 Tick: 見えていれば true（ふつうに戦う）。見えなければ回り込み、長引けばいぶり出して false */
    boolean handleSight(LivingEntity t, boolean sees) {
        if (sees) {
            unseen = 0;
            sightSpot = null;
            return true;
        }
        unseen++;
        if (sightSpot == null || --sightCheck <= 0) {
            sightSpot = findSight(t);
            sightCheck = 10;
        }
        if (sightSpot != null) {
            mori.steer(sightSpot, 0.55);
            mori.faceToward(t.position());
        } else {
            mori.flyAround(t, 0.0F, 0.4);
        }
        if (unseen > FLUSH_AFTER) flush(t, unseen - FLUSH_AFTER);
        return false;
    }

    /** 相手が見える所を探す（高い所ほど好み、近い所ほど好む）。見つかればしばらくその高さで戦う */
    Vec3 findSight(LivingEntity t) {
        Vec3 eye = t.getEyePosition();
        double eyeUp = mori.getEyeHeight();
        Vec3 best = null;
        double bestCost = Double.MAX_VALUE, bestH = MementoMoriEntity.HOVER_HEIGHT;
        double base = Math.max(t.getY(), mori.groundY(t.position()));
        for (double h : new double[]{MementoMoriEntity.HOVER_HEIGHT, 6.0, 3.0, 1.0}) {
            for (double r : new double[]{MementoMoriEntity.ORBIT, 9.0}) {
                for (int k = 0; k < 16; k++) {
                    double a = Math.PI * 2 * k / 16;
                    Vec3 feet = new Vec3(t.getX() + Math.cos(a) * r, base + h, t.getZ() + Math.sin(a) * r);
                    double cost = feet.distanceToSqr(mori.position()) + (MementoMoriEntity.HOVER_HEIGHT - h) * 12.0;
                    if (cost >= bestCost) continue;
                    if (!mori.level().noCollision(mori, mori.getBoundingBox().move(feet.subtract(mori.position())))) continue;
                    Vec3 from = feet.add(0, eyeUp, 0);
                    if (mori.level().clip(new ClipContext(from, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mori)).getType() != HitResult.Type.MISS) continue;
                    best = feet;
                    bestCost = cost;
                    bestH = h;
                }
            }
        }
        if (best != null) mori.keepHeight(bestH, 200);
        return best;
    }

    /** いぶり出す（見えなくなってから n Tick 目）: 壁を抜ける蝶ビットと、相手のまわりの開いた所からの光弾 */
    private void flush(LivingEntity t, int n) {
        ServerLevel server = (ServerLevel) mori.level();
        boolean p2 = mori.isPhase2();
        if (n == 1 && mori.tickCount - lastFlushNotice > 200) {
            lastFlushNotice = mori.tickCount;
            for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(mori) < 64 * 64)) {
                p.displayClientMessage(Component.translatable("message.automata_valkyries.memento_mori.hidden").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
            }
            mori.playSound(MementoMoriEntity.sfx("cast"), 3.0F, 0.7F);
        }
        if (n % (p2 ? 35 : 50) == 1) { // 壁を抜ける蝶ビット
            int count = p2 ? 8 : 6;
            for (int i = 0; i < count; i++) {
                boolean left = i % 2 == 0;
                Vec3 from = mori.wingTip(left);
                Vec3 v = new Vec3(mori.getRandom().nextGaussian() * 0.15, 0.3, mori.getRandom().nextGaussian() * 0.15);
                MementoBitEntity.swarm(server, mori, t, from, v, MementoBitEntity.DAMAGE, p2).phase();
            }
            mori.playSound(MementoMoriEntity.sfx("bit"), 2.0F, 0.7F);
        }
        if (n % 12 == 6) { // 相手のまわりの開いた所に光弾を置き、止まってから向き直って撃つ
            Vec3 chest = MementoMoriEntity.aimAt(t);
            int placed = 0;
            for (int k = 0; k < 16 && placed < 3; k++) {
                double a = mori.getRandom().nextDouble() * Math.PI * 2, r = 3.0 + mori.getRandom().nextDouble() * 4.0;
                Vec3 p = chest.add(Math.cos(a) * r, mori.getRandom().nextGaussian() * 0.5, Math.sin(a) * r);
                if (!mori.level().getBlockState(net.minecraft.core.BlockPos.containing(p)).getCollisionShape(mori.level(), net.minecraft.core.BlockPos.containing(p)).isEmpty()) continue;
                if (mori.level().clip(new ClipContext(p, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mori)).getType() != HitResult.Type.MISS) continue;
                mori.fire(MementoBulletEntity.shot(p, Vec3.ZERO).delay(12).reaim(t, 0.32).life(80).color(p2 ? MementoMoriEntity.C_PINK : MementoMoriEntity.C_VIOLET));
                Fx.burst(server, MementoMoriEntity.glow(MementoMoriEntity.C_PALE, 0.3F, 12), p, 4, 0.05);
                placed++;
            }
        }
    }
}
