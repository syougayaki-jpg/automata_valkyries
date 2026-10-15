package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 死蝶終葬『メメント・モリ』専用の弾幕（2026-10-15 ユーザー指定「死蝶終葬『メメント・モリ』専用一番豪華な弾幕を用意して」、同日「60 秒にして」。数値は【仮】）。
 * ほかの技では使わない模様だけで組み、約 20 秒ずつの 3 つの楽章に分ける。どの楽章も 3 巡りして、巡るごとに激しくなる（s は構えを終えてからの Tick）:
 * <ul>
 *   <li>第一楽章「蝶葬の円舞」（0〜{@value #STAGE2}）: 空に数式の蝶の曲線（Fay の蝶）を {@value #FORM_POINTS} 発の蝶弾で 1 秒かけて描き、1 秒止めてから放つ。
 *       2 秒ごとに真ん中・左・右に 9 回描き、放ち方は 相手へ収束・外へ破裂・その混ぜ合わせを組み合わせを変えて。巡るごとに速く、3 巡り目は左右の対も描く</li>
 *   <li>第二楽章「死を刻む時計」（{@value #STAGE2}〜{@value #STAGE3}）: 相手の足元を中心に半径 {@value #DIAL} の文字盤（大玉 12 個）。弾でできた長針と短針が逆へ回る。
 *       針は跳び越せない 2 段の高さで、1 か所だけ隙間がある。{@value #HOUR} Tick ごとに針を掛け替え（隙間の場所が変わる）、巡るごとに速く回る。
 *       文字盤からは 2 秒ごとに内向きの弾。1 秒ごとに鐘</li>
 *   <li>終楽章「千の死蝶」（{@value #STAGE3}〜{@value #END}）: 胸から 6 回対称の万華鏡の弾幕を流しながら、まわりに 4 重の球の殻状に蝶を並べ、
 *       全部が一斉に相手へ襲いかかる（外側の殻ほど速く、波になって届く）。{@value #CYCLE} Tick ごとに 3 回、最後は 5 重</li>
 * </ul>
 */
public final class MementoFinale {

    static final int STAGE2 = 390, STAGE3 = 790, END = 1180;
    static final int FORM_POINTS = 180, FORM_DRAW = 20, FORM_HOLD = 20, FORM_EVERY = 40;
    static final int HOUR = 130, CYCLE = 130;
    static final double FORM_SCALE = 2.0, DIAL = 11.0;
    private static final Vec3 UP = MementoMoriEntity.UP;

    private final MementoMoriEntity mori;
    private Vec3 formCenter = Vec3.ZERO, formRight = new Vec3(1, 0, 0), clock = Vec3.ZERO;
    private final Vec3[] mirrorCenter = new Vec3[1];
    private final double[] gap = new double[2];

    MementoFinale(MementoMoriEntity mori) {
        this.mori = mori;
    }

    void tick(LivingEntity t, int s) {
        if (s == 0) movement("spell_stage1");
        if (s == STAGE2) movement("spell_stage2");
        if (s == STAGE3) movement("spell_stage3");
        if (s < STAGE2) waltz(t, s);
        else if (s < STAGE3) clock(t, s - STAGE2);
        else if (s < END) thousand(t, s - STAGE3);
    }

    private ServerLevel server() {
        return (ServerLevel) mori.level();
    }

    /** 楽章の名（画面の下の行） */
    private void movement(String key) {
        for (ServerPlayer p : server().getPlayers(p -> p.distanceToSqr(mori) < 64 * 64)) {
            p.displayClientMessage(Component.translatable("message.automata_valkyries.memento_mori." + key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
        }
        mori.playSound(MementoMoriEntity.sfx("cast"), 4.0F, 0.9F);
        Fx.shockwave(server(), mori.chest(), MementoMoriEntity.C_PINK, 14.0F, 24);
    }

    /** 相手の方の水平の横向き（相手から見て右） */
    private static Vec3 sideways(Vec3 from, LivingEntity t) {
        Vec3 d = t.position().subtract(from).multiply(1, 0, 1);
        if (d.lengthSqr() < 1.0e-4) d = new Vec3(0, 0, 1);
        d = d.normalize();
        return new Vec3(-d.z, 0, d.x);
    }

    // ---- 第一楽章「蝶葬の円舞」 ----

    /** Fay の蝶の曲線の i 点目（x 横・y 縦。幅およそ ±3、高さ -2〜3） */
    static double[] butterflyCurve(int i) {
        double u = Math.PI * 2 * i / FORM_POINTS;
        double r = Math.exp(Math.cos(u)) - 2 * Math.cos(4 * u) + Math.pow(Math.sin(u / 12), 5);
        return new double[]{Math.sin(u) * r, Math.cos(u) * r};
    }

    private void waltz(LivingEntity t, int s) {
        int f = s / FORM_EVERY, local = s % FORM_EVERY;
        if (s % 30 == 15) mori.aimedFan(t, 7, 0.12, 0.55);
        if (f * FORM_EVERY + FORM_DRAW + FORM_HOLD >= STAGE2) return; // 楽章の終わりまでに放てない回は描かない
        int round = f / 3, place = f % 3, mode = (f + round) % 3;  // 置き場所: 真ん中・左・右。放ち方: 収束・破裂・混ぜ合わせ
        boolean mirror = round >= 2;                                 // 3 巡り目は左右の対も描く
        if (local == 0) {
            formRight = sideways(mori.chest(), t);
            double off = (place == 1 ? -1 : place == 2 ? 1 : 0) * 7.0;
            formCenter = mori.chest().add(0, 3.0, 0).add(formRight.scale(off));
            mirrorCenter[0] = mirror ? mori.chest().add(0, 3.0, 0).add(formRight.scale(place == 0 ? 12.0 : -off)) : null;
            int color = f % 2 == 1 ? MementoMoriEntity.C_CYAN : MementoMoriEntity.C_PINK;
            Fx.mandala(server(), formCenter, color, 7.0F, FORM_DRAW + FORM_HOLD, true, f % 2 == 1);
            if (mirrorCenter[0] != null) Fx.mandala(server(), mirrorCenter[0], color, 7.0F, FORM_DRAW + FORM_HOLD, true, f % 2 == 0);
            mori.playSound(MementoMoriEntity.sfx("gather"), 3.0F, 1.2F);
        }
        int per = FORM_POINTS / FORM_DRAW;
        if (local < FORM_DRAW) { // 1 秒かけて曲線をなぞって並べる
            for (int i = local * per; i < (local + 1) * per; i++) {
                drawPoint(t, formCenter, i, f, local, mode, round, 1.0);
                if (mirrorCenter[0] != null && i % 2 == 0) drawPoint(t, mirrorCenter[0], i, f + 1, local, (mode + 1) % 3, round, -1.0); // 対は半分の数で、左右を返す
            }
            if (local % 4 == 0) mori.shotSound(1.4F);
        }
        if (local == FORM_DRAW + FORM_HOLD - 1) { // 放つ（並べた弾は次の Tick に動き出す）
            mori.playSound(MementoMoriEntity.sfx("scatter"), 4.0F, 1.0F);
            Fx.shockwave(server(), formCenter, MementoMoriEntity.C_WHITE, 9.0F, 18);
            Fx.burst(server(), MementoMoriEntity.glow(MementoMoriEntity.C_PALE, 0.5F, 20), formCenter, 30, 0.4);
        }
    }

    /** 蝶の曲線の i 点目に 1 発並べる。mode 0 = 相手へ収束、1 = 外へ破裂、2 = 1 発おきに混ぜる。flip = -1 で左右を返す */
    private void drawPoint(LivingEntity t, Vec3 center, int i, int f, int local, int mode, int round, double flip) {
        double[] xy = butterflyCurve(i);
        Vec3 p = center.add(formRight.scale(xy[0] * FORM_SCALE * flip)).add(UP.scale((xy[1] - 0.5) * FORM_SCALE));
        int wait = FORM_DRAW + FORM_HOLD - local;
        double boost = 0.05 * round;
        byte kind = i % 4 == 0 ? MementoBulletEntity.GRAND : MementoBulletEntity.BUTTERFLY;
        boolean aimed = mode == 0 || (mode == 2 && i % 2 == 0);
        MementoBulletEntity.Shot shot = aimed
                ? MementoBulletEntity.shot(p, Vec3.ZERO).reaim(t, 0.32 + boost + (i % 5) * 0.02)
                : MementoBulletEntity.shot(p, p.subtract(center).normalize().scale(0.04)).accel(1.06F, (float) (0.42 + boost));
        mori.fire(shot.delay(wait).life(wait + 110).kind(kind).color(rainbow(i, f)));
    }

    /** 曲線に沿った色の移り変わり（桃色 → 紫 → 水色 → 白。回ごとにずらす） */
    private static int rainbow(int i, int f) {
        int[] c = {MementoMoriEntity.C_PINK, MementoMoriEntity.C_VIOLET, MementoMoriEntity.C_CYAN, MementoMoriEntity.C_WHITE};
        return c[(i * 4 / FORM_POINTS + f) % c.length];
    }

    // ---- 第二楽章「死を刻む時計」 ----

    private void clock(LivingEntity t, int c) {
        ServerLevel server = server();
        int span = STAGE3 - STAGE2;
        if (c == 0) { // 文字盤（楽章のあいだずっと）
            clock = new Vec3(t.getX(), t.getY(), t.getZ());
            Fx.mandala(server, clock.add(0, 0.1, 0), MementoMoriEntity.C_PALE, (float) DIAL + 0.5F, span, false, false);
            Fx.mandala(server, clock.add(0, 0.15, 0), MementoMoriEntity.C_VIOLET, (float) DIAL * 0.6F, span, false, true);
            for (int k = 0; k < 12; k++) {
                double a = Math.PI * 2 * k / 12;
                Vec3 p = clock.add(Math.cos(a) * DIAL, 1.0, Math.sin(a) * DIAL);
                mori.fire(MementoBulletEntity.shot(p, Vec3.ZERO).delay(span + 5).life(span + 5).kind(MementoBulletEntity.LARGE)
                        .color(k == 0 ? MementoMoriEntity.C_WHITE : k % 3 == 0 ? MementoMoriEntity.C_PINK : MementoMoriEntity.C_VIOLET).damage(MementoMoriEntity.BULLET_LARGE));
            }
            mori.fire(MementoBulletEntity.shot(clock.add(0, 1.0, 0), Vec3.ZERO).delay(span + 5).life(span + 5)
                    .kind(MementoBulletEntity.LARGE).color(MementoMoriEntity.C_WHITE).damage(MementoMoriEntity.BULLET_LARGE)); // 針の軸
            mori.playSound(MementoMoriEntity.sfx("moon_bell"), 5.0F, 0.6F);
        }
        int hour = c / HOUR, inHour = c % HOUR;
        if (c + 10 < span) {
            double base = Math.atan2(mori.getZ() - clock.z, mori.getX() - clock.x);
            if (inHour == 0) { // 掛け替える針の隙間を決める
                gap[0] = 3.0 + mori.getRandom().nextDouble() * 5.5;
                gap[1] = 2.5 + mori.getRandom().nextDouble() * 3.0;
            }
            if (inHour < 10 && inHour % 3 == 0) { // 針の予告の線
                for (int h = 0; h < 2; h++) {
                    double a = base + h * Math.PI, len = h == 0 ? 10.5 : 7.0;
                    Fx.line(server, MementoMoriEntity.glow(h == 0 ? MementoMoriEntity.C_PINK : MementoMoriEntity.C_CYAN, 0.25F, 5),
                            clock.add(0, 0.8, 0), clock.add(Math.cos(a) * len, 0.8, Math.sin(a) * len), 0.6);
                }
            }
            if (inHour == 10) { // 針: 長針は左回り、短針は右回り。弾を半径ごとに 2 段（跳び越せない高さ）。隙間を 1 か所あける。巡るごとに 1.25 倍ずつ速く
                double speed = 1.0 + 0.25 * hour;
                int life = Math.min(HOUR, span - c);
                hand(base, 10.5, 0.028 * speed, gap[0], MementoMoriEntity.C_PINK, life);
                hand(base + Math.PI, 7.0, -0.018 * speed, gap[1], MementoMoriEntity.C_CYAN, life);
                mori.playSound(MementoMoriEntity.sfx("cage_lock"), 4.0F, 0.8F + 0.1F * hour);
            }
        }
        if (c >= 10 && c % 20 == 10) mori.playSound(MementoMoriEntity.sfx("moon_bell"), 3.0F, (c / 20) % 2 == 0 ? 1.3F : 1.0F); // 秒を刻む鐘
        if (c >= 30 && c % 40 == 30) { // 文字盤から内向きの弾（巡るごとに 1 発ずつ増える）
            int spread = 1 + hour;
            for (int k = 0; k < 12; k++) {
                double a = Math.PI * 2 * k / 12;
                Vec3 p = clock.add(Math.cos(a) * DIAL, 1.0, Math.sin(a) * DIAL);
                for (int j = -spread; j <= spread; j++) {
                    Vec3 d = MementoBulletEntity.rotate(clock.add(0, 1.0, 0).subtract(p).normalize(), UP, j * 0.24 / spread);
                    mori.fire(MementoBulletEntity.shot(p, d.scale(0.16)).life(90).color(MementoMoriEntity.C_PALE));
                }
            }
            mori.shotSound(0.9F);
        }
        if (c % 30 == 20) mori.aimedFan(t, 5, 0.15, 0.5);
    }

    /** 時計の針 1 本: 軸から len まで 0.75 ごとに 2 段、角速度 omega（ラジアン/Tick）で回る。半径 gapAt から 1.8 は隙間 */
    private void hand(double angle, double len, double omega, double gapAt, int color, int life) {
        for (double r = 1.2; r <= len; r += 0.75) {
            if (r >= gapAt && r < gapAt + 1.8) continue;
            for (double y : new double[]{0.35, 1.25}) mori.fire(orbit(clock.add(0, y, 0), angle, r, omega).life(life).color(color));
        }
    }

    /**
     * center のまわりの半径 r・角 angle（x→z の向き）の所から、角速度 omega（ラジアン/Tick）で水平に回り続ける 1 発。
     * 速さ ωr で接線へ撃ち、向きを毎 Tick -ω 回す（ロドリゲスの回転で +ω は外向きに曲がるので、内向きに曲げるには -ω）
     */
    public static MementoBulletEntity.Shot orbit(Vec3 center, double angle, double r, double omega) {
        Vec3 p = center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
        Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
        return MementoBulletEntity.shot(p, tangent.scale(omega * r)).curl(UP, (float) -omega);
    }

    // ---- 終楽章「千の死蝶」 ----

    private static final int[] SHELL_COUNT = {30, 50, 64, 80, 96};
    private static final double[] SHELL_RADIUS = {3.0, 5.0, 7.0, 9.0, 11.0};
    private static final int SHELL_START = 30, SHELL_EVERY = 10, LAUNCH = 90;

    private void thousand(LivingEntity t, int s3) {
        ServerLevel server = server();
        int cycle = s3 / CYCLE, local = s3 % CYCLE;
        boolean last = cycle >= (END - STAGE3) / CYCLE - 1;
        if (local < LAUNCH - 10 && s3 % 3 == 0) { // 万華鏡: 6 回対称で逆へ回る 2 組、曲がりながら開く（巡るごとに回りが速い）
            Vec3 from = mori.chest();
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int arm = 0; arm < 6; arm++) {
                    double az = sign * s3 * (0.07 + 0.015 * cycle) + arm * Math.PI / 3;
                    Vec3 dir = new Vec3(Math.cos(-0.35) * Math.cos(az), Math.sin(-0.35), Math.cos(-0.35) * Math.sin(az));
                    int color = arm % 2 == 0 ? (sign > 0 ? MementoMoriEntity.C_PINK : MementoMoriEntity.C_CYAN) : MementoMoriEntity.C_WHITE;
                    mori.fire(MementoBulletEntity.shot(from, dir.scale(0.3)).curl(UP, sign * 0.02F).color(color));
                }
            }
            mori.shotSound(1.2F);
        }
        int shells = last ? SHELL_COUNT.length : 4;
        int shell = (local - SHELL_START) / SHELL_EVERY;
        if (local >= SHELL_START && (local - SHELL_START) % SHELL_EVERY == 0 && shell < shells && local < LAUNCH) { // 内側から 1 重ずつ殻を並べる（広がって見える）
            Vec3 c = mori.chest();
            int n = SHELL_COUNT[shell];
            double golden = Math.PI * (3 - Math.sqrt(5));
            byte kind = shell == 0 ? MementoBulletEntity.LARGE : shell % 2 == 0 ? MementoBulletEntity.GRAND : MementoBulletEntity.BUTTERFLY;
            int color = new int[]{MementoMoriEntity.C_WHITE, MementoMoriEntity.C_PINK, MementoMoriEntity.C_PALE, MementoMoriEntity.C_CYAN, MementoMoriEntity.C_VIOLET}[shell];
            double twist = cycle * 0.4;
            for (int k = 0; k < n; k++) {
                double y = 1 - 2 * (k + 0.5) / n, rr = Math.sqrt(1 - y * y), phi = k * golden + twist;
                Vec3 p = c.add(new Vec3(Math.cos(phi) * rr, y, Math.sin(phi) * rr).scale(SHELL_RADIUS[shell]));
                int wait = LAUNCH - local;
                mori.fire(MementoBulletEntity.shot(p, Vec3.ZERO).delay(wait).reaim(t, 0.26 + 0.05 * shell + 0.03 * cycle).life(wait + 120).kind(kind).color(color)
                        .damage(shell == 0 ? MementoMoriEntity.BULLET_LARGE : MementoMoriEntity.BULLET));
            }
            Fx.shockwave(server, c, color, (float) SHELL_RADIUS[shell], 14);
            mori.playSound(MementoMoriEntity.sfx("form"), 3.0F, 0.8F + shell * 0.15F);
        }
        if (local == LAUNCH) { // 一斉に
            Vec3 c = mori.chest();
            Fx.burst(server, MementoMoriEntity.glow(MementoMoriEntity.C_WHITE, 0.9F, 24), c, 60, 0.5);
            Fx.shockwave(server, c, MementoMoriEntity.C_PINK, 20.0F, 26);
            Fx.shockwave(server, c, MementoMoriEntity.C_CYAN, 26.0F, 32);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
            mori.playSound(MementoMoriEntity.sfx("scatter"), 5.0F, 0.8F);
            mori.playSound(MementoMoriEntity.sfx("ascend"), 4.0F, last ? 0.9F : 1.2F);
        }
    }
}
