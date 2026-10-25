package com.automatavalkyries.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * エレノアの技の選び方（2026-10-24 ユーザー指定「状況で選ぶ＋動きを読む」。メメントモリの頭 {@code MementoTactics} と同じ作り。数値は【仮】）。
 * <ul>
 *   <li>相手の動きを毎 Tick 見張る（なめらかにした速さ・平均の速さ・宙にいるか・屋根の下か）</li>
 *   <li>動きを読む: 弾・スポットライト・吊り物は、相手が今の速さで動き続けたら届く時にいる所（{@link #lead}）を狙う。向きを変えれば避けられる</li>
 *   <li>状況で技を選ぶ: 使える技に点を付けて一番高い物を使う（{@link #score}）。止まっている相手には吊り物・スポットライト・奈落、
 *       逃げ回る相手には綱・輪・扇、近い相手には輪舞と大見得、遠い相手には踏み込みと早替わり。屋根の下では吊り物・薔薇の雨・スポットライト
 *       （舞台の上から来る技）を使わない。前の回に 1 発も当たらなかった技・続けて同じ技は点を下げ、しばらく使っていない技は点を上げる</li>
 * </ul>
 */
public final class ElenorTactics {

    private final ElenorEntity elenor;
    private LivingEntity tracked;
    private Vec3 lastPos;
    private Vec3 vel = Vec3.ZERO;
    private double avgSpeed;
    private int airTicks;
    private int roofCheck;
    private boolean roofed;

    /** 技ごとの当たり具合 */
    private final Map<ElenorArts.Move, Double> hitEma = new EnumMap<>(ElenorArts.Move.class);
    private final Map<ElenorArts.Move, Integer> lastHits = new EnumMap<>(ElenorArts.Move.class);
    private final Map<ElenorArts.Move, Long> lastUsed = new EnumMap<>(ElenorArts.Move.class);
    private ElenorArts.Move credit;
    private int creditHits;
    private long creditUntil = -1L;
    private ElenorArts.Move last1, last2;

    ElenorTactics(ElenorEntity elenor) {
        this.elenor = elenor;
    }

    // ---- 見張り ----

    /** 毎 Tick: 相手の動きを見る */
    void observe(LivingEntity t) {
        Vec3 pos = t.position();
        if (t != tracked || lastPos == null) {
            tracked = t;
            vel = Vec3.ZERO;
            avgSpeed = 0;
        } else {
            Vec3 step = pos.subtract(lastPos);
            if (step.lengthSqr() > 4.0) step = Vec3.ZERO; // テレポートなどの飛びは数えない
            vel = vel.scale(0.6).add(step.scale(0.4));
            avgSpeed = avgSpeed * 0.95 + step.multiply(1, 0, 1).length() * 0.05;
        }
        lastPos = pos;
        airTicks = t.onGround() ? 0 : airTicks + 1;
        if (--roofCheck <= 0) {
            roofCheck = 20;
            Vec3 eye = t.getEyePosition();
            roofed = elenor.level().clip(new ClipContext(eye, eye.add(0, 10.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t))
                    .getType() != HitResult.Type.MISS;
        }
        long now = elenor.level().getGameTime();
        if (credit != null && creditUntil >= 0 && now > creditUntil) settle();
    }

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

    boolean roofed() {
        return roofed;
    }

    /** 相手が宙にいる（エレノアより 3 ブロック以上上・飛び続けている） */
    boolean aloft(LivingEntity t) {
        return t.getY() - elenor.getY() > 3.0 || flying();
    }

    /** 相手が超巨大（足もとの輪や近くの床で測る技が、体に届かない大きさ） */
    static boolean huge(LivingEntity t) {
        return t.getBbHeight() > 4.0F || t.getBbWidth() > 2.5F;
    }

    // ---- 動きを読む ----

    /** from から speed で届く技が、相手に届く時に相手がいる所（amount = 読みの強さ 0〜1。上下の動きは弱めに読む） */
    Vec3 lead(LivingEntity t, Vec3 from, double speed, double amount) {
        return leadPoint(t.position().add(0, t.getBbHeight() * 0.5, 0), velocity(t), from, speed, amount);
    }

    /** delay Tick 後に相手がいる所（足もと。上下は読まない） */
    Vec3 leadFeet(LivingEntity t, int delay, double amount) {
        Vec3 v = velocity(t);
        return t.position().add(v.x * delay * amount, 0, v.z * delay * amount);
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

    /** 候補から一番点の高い技 */
    ElenorArts.Move choose(List<ElenorArts.Move> candidates, LivingEntity t, boolean finalTier) {
        ElenorArts.Move best = candidates.get(0);
        double bestScore = -1.0e9;
        for (ElenorArts.Move m : candidates) {
            double s = score(m, t, finalTier) + elenor.getRandom().nextDouble() * 0.8;
            if (s > bestScore) {
                bestScore = s;
                best = m;
            }
        }
        return best;
    }

    double score(ElenorArts.Move move, LivingEntity t, boolean finalTier) {
        double dist = t.position().subtract(elenor.position()).multiply(1, 0, 1).length();
        boolean near = dist <= 5.0, far = dist > 12.0;
        double s = 1.0;
        switch (move) {
            case LUNGE -> s += (far ? 2.0 : 0) + (dist > 5 ? 0.5 : -3.0);
            case NOVA -> s += near ? 3.0 : -4.0;
            case RONDO -> s += (near ? 3.0 : dist < 8 ? 0.5 : -3.0) - (flying() ? 2.0 : 0);
            case SPOTLIGHT -> s += (slow() ? 2.5 : 0) + (far ? 1.0 : 0) + (flying() ? 1.0 : 0) - (roofed ? 6.0 : 0);
            case FLY_DROP -> s += (slow() ? 3.0 : 0) - (fast() ? 1.0 : 0) - (roofed ? 6.0 : 0);
            case VOLLEY -> s += (fast() ? 1.5 : 0) + (far ? 1.0 : 0) + (flying() ? 1.0 : 0);
            case BLINK -> s += (far ? 0.5 : 0) + (near ? 1.0 : 0);
            case BIND -> s += (dist <= 14 ? 1.0 : -6.0) + (fast() ? 1.5 : 0) + (flying() ? 1.5 : 0);
            case MARIONETTE -> s += (slow() ? 2.5 : 0) + (dist < 16 ? 0.5 : 0);
            case TRAPDOOR -> s += (slow() ? 2.0 : 0) + (near ? 0.5 : 0) - (flying() ? 4.0 : 0);
            case QUICK_CHANGE -> s += (far ? 2.0 : 0) + (fast() ? 1.0 : 0);
            case RING -> s += (fast() ? 1.5 : 0) + (dist < 12 ? 1.0 : 0);
            case SIGIL -> s += (slow() ? 2.0 : 0) - (fast() ? 1.0 : 0);
            case MIRAGE -> s += (fast() ? 1.0 : 0) + (dist > 6 ? 0.5 : 0);
            case MIE -> s += (dist < 12 ? 2.0 : -4.0) - (flying() ? 1.0 : 0);
            case ROSE_RAIN -> s += (fast() ? 1.5 : 0) + (slow() ? 1.0 : 0) - (roofed ? 6.0 : 0);
            case CHORDS -> s += (fast() ? 2.0 : 0) + (dist < 14 ? 0.5 : 0) - (flying() ? 2.0 : 0);
            case CURTAIN -> s += finalTier ? 1.5 : -10.0;
            default -> { }
        }
        // 宙にいる・超巨大な相手（2026-10-24）: 三次元の弾幕（螺旋・星蝶・天球）を主にし、床で測る技・近接は使わない
        boolean aloft = aloft(t), huge = huge(t);
        if (aloft || huge) {
            switch (move) {
                case HELIX, HOMING, CELESTIAL -> s += 3.5;
                case VOLLEY, BIND, MIRAGE -> s += 1.0;
                case SPOTLIGHT, FLY_DROP, MARIONETTE, TRAPDOOR, CHORDS, SIGIL, ROSE_RAIN -> s -= huge && !aloft ? 3.0 : 4.0;
                case RONDO, NOVA, MIE, LUNGE -> s -= aloft ? 4.0 : 0.0; // 宙の相手には届かない（巨大でも地上なら体に届く）
                default -> { }
            }
        } else if (move == ElenorArts.Move.HELIX || move == ElenorArts.Move.HOMING || move == ElenorArts.Move.CELESTIAL) {
            s += (fast() ? 1.0 : 0) + (dist > 10 ? 0.5 : 0);
        }
        if (finalTier && (move == ElenorArts.Move.BIND || move == ElenorArts.Move.SIGIL || ElenorArts.isAerial(move))) s += 2.0; // ティア 7: 終焉入りの技を主軸に
        s += Math.min(3.0, hitEma.getOrDefault(move, 0.0)) * 0.8;                               // よく当たる技
        Integer hits = lastHits.get(move);
        if (hits != null && hits == 0) s -= 1.5;                                                 // 前の回に 1 発も当たらなかった技
        if (move == last1) s -= 3.0;                                                             // 続けて同じ技は使わない
        else if (move == last2) s -= 1.0;
        Long used = lastUsed.get(move);
        long idle = used == null ? 1200L : elenor.level().getGameTime() - used;
        s += Math.min(1.5, idle / 400.0);                                                        // しばらく使っていない技
        return s;
    }

    /** 技を始めた: 当たりを数え始める（前の技の数えが残っていれば締める） */
    void started(ElenorArts.Move move) {
        if (credit != null) settle();
        credit = move;
        creditHits = 0;
        creditUntil = -1L;
        last2 = last1;
        last1 = move;
        lastUsed.put(move, elenor.level().getGameTime());
    }

    /** 技が終わった: そこから 2 秒の間に当たった分までその技の手柄にする（舞台の仕掛けは遅れて当たる） */
    void ended(ElenorArts.Move move) {
        if (credit == move) creditUntil = elenor.level().getGameTime() + 40L;
    }

    /** 技が当たった */
    void hit(ElenorArts.Move move) {
        if (move != null && move == credit) creditHits++;
    }

    private void settle() {
        if (credit != null) {
            hitEma.put(credit, hitEma.getOrDefault(credit, 0.0) * 0.6 + creditHits * 0.4);
            lastHits.put(credit, creditHits);
        }
        credit = null;
        creditHits = 0;
        creditUntil = -1L;
    }
}
