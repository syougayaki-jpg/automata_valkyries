package com.automatavalkyries.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * エレノアの戦い方（3 フェーズ。2026-10-22）。通常のエレノアだけが使う（最果てのエレノアは継承しても使わない: {@link ElenorEntity#usesBossArts()}）。
 * <ul>
 *   <li>第 1（HP 60% 超）: 近接と、前兆のある踏み込み、近づかれたときの衝撃波</li>
 *   <li>第 2（60% 以下）: 瞬間移動と、紫の光弾の扇撃ちが加わる</li>
 *   <li>第 3（30% 以下）: 光弾の輪と、足元の魔法陣が加わり、間隔が詰まる</li>
 * </ul>
 * 2026-10-22 追加: フェーズが進むときの演出（止まってスカートを広げ、黒とピンクのリボンの粒が渦を巻き、周りを押し返す。ダメージなし）、
 * リボンの縛め（第 2〜。前兆の線の先へリボンを伸ばし、当たると引き寄せて鈍足）、舞踏の残像（第 3。粒の残像 2 体が順に光弾を撃つ）、
 * カーテンコール（HP 10% で一度だけ。5 秒の弾幕。その間も攻撃は通る）
 * どの技にも前兆（動きを止めて光る）があり、避けられる。ダメージは攻撃力属性（既定 12）に比例する（回想のトライアルは攻撃力を絞って呼ぶ）
 */
public final class ElenorArts {

    public static final float PHASE2_RATIO = 0.6F;
    public static final float PHASE3_RATIO = 0.3F;

    /** カーテンコールに入る HP の割合（一度だけ） */
    public static final float CURTAIN_RATIO = 0.1F;

    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.7F, 0.25F, 1.0F), 1.2F);
    static final DustParticleOptions RIBBON_PINK = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.75F), 1.3F);
    static final DustParticleOptions RIBBON_BLACK = new DustParticleOptions(new Vector3f(0.08F, 0.05F, 0.1F), 1.3F);

    private enum Move { NONE, LUNGE, VOLLEY, NOVA, BLINK, RING, SIGIL, TRANSITION, BIND, MIRAGE, CURTAIN }

    /** 飛ぶ解けたリボン（もとは光弾）。進む向きに対して横にしなりながら飛ぶ */
    private static final class Orb {
        Vec3 pos;
        final Vec3 vel;
        final double phase;
        int life = 80;

        Orb(Vec3 pos, Vec3 vel) {
            this.pos = pos;
            this.vel = vel;
            this.phase = (pos.x * 7.3 + pos.z * 3.1 + vel.x * 11.0) % (Math.PI * 2);
        }

        /** しなり: 横へ揺れる量（進む向きは変えない） */
        Vec3 sway() {
            Vec3 side = new Vec3(-vel.z, 0, vel.x);
            if (side.lengthSqr() < 1.0e-6) return Vec3.ZERO;
            return side.normalize().scale(Math.sin(life * 0.45 + phase) * 0.12);
        }
    }

    /** 近接の鞭を払う向き（左右交互） */
    private int meleeSide = 1;

    /** 劇場（開幕・幕・結界・閉幕） */
    private final ElenorTheater theater;

    private static final class Sigil {
        final Vec3 at;
        int ticks = 40;

        Sigil(Vec3 at) {
            this.at = at;
        }
    }

    private final ElenorEntity elenor;
    private final List<Orb> orbs = new ArrayList<>();
    private final List<Sigil> sigils = new ArrayList<>();
    /** スケーリング・ペナルティ・ティア（最果てのエレノアは、ティア 7 固定・スケーリングなし） */
    private final ElenorPressure pressure;
    /** 最初から全開（最果てのエレノア）: 第 3 フェーズの全部の技を最初から使い、間隔も詰め、カーテンコールを繰り返す */
    private final boolean fullPower;
    private long nextCurtain = -1L;

    private Move move = Move.NONE;
    private int moveTick;
    private int cooldown = 40;
    private int meleeCooldown;
    private int phase = 1;
    private boolean introDone;
    private Vec3 lungeDir = Vec3.ZERO;
    private int ringsLeft;
    /** リボンの縛め: 伸ばす向き（前兆の終わりで決まる）と、当たったか */
    private Vec3 bindDir = Vec3.ZERO;
    private boolean bindHit;
    /** 舞踏の残像の位置 */
    private final List<Vec3> mirages = new ArrayList<>();
    private boolean curtainDone;
    /** GameTest 用: 0 以上なら実際の HP の代わりにこの割合で戦う（Eprotect が HP の直接の書き換えを止めるので） */
    private float testRatio = -1.0F;

    ElenorArts(ElenorEntity elenor) {
        this.elenor = elenor;
        this.fullPower = elenor.fullPower();
        this.pressure = new ElenorPressure(elenor, !fullPower, fullPower ? ElenorPressure.MAX_TIER : -1);
        this.theater = new ElenorTheater(elenor);
    }

    public ElenorPressure pressure() {
        return pressure;
    }

    /** 最初から全開か（最果てのエレノア） */
    public boolean fullPower() {
        return fullPower;
    }

    public int phase() {
        return phase;
    }

    /** GameTest 用: 実際の HP の代わりに使う割合（負で解除） */
    public void setTestHealthRatio(float ratio) {
        testRatio = ratio;
    }

    public static int phaseFor(float ratio) {
        return ratio <= PHASE3_RATIO ? 3 : ratio <= PHASE2_RATIO ? 2 : 1;
    }

    private double scale() {
        return elenor.getAttributeValue(Attributes.ATTACK_DAMAGE) / 12.0D;
    }

    private ServerLevel level() {
        return (ServerLevel) elenor.level();
    }

    // ---- 毎 Tick（エンティティから）：光弾と魔法陣は、ゴールが止まっていても進む ----

    public void tickProjectiles() {
        if (!(elenor.level() instanceof ServerLevel level)) return;
        theater.tick(level, elenor.getTarget() != null);
        for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
            Orb orb = it.next();
            Vec3 previous = orb.pos;
            orb.pos = orb.pos.add(orb.vel).add(orb.sway());
            ElenorFx.ribbonStrand(level, previous, orb.pos, orb.life); // 解けたリボンの帯
            boolean hit = false;
            for (LivingEntity player : foes(level, new net.minecraft.world.phys.AABB(orb.pos, orb.pos).inflate(0.7))) {
                if (!ElenorPressure.fightable(player)) continue;
                hurt(player, 6.0F);
                hit = true;
                break;
            }
            BlockPos block = BlockPos.containing(orb.pos);
            if (hit || --orb.life <= 0 || !level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
                if (!hit) ElenorFx.petalBurst(level, orb.pos, 4, 0.15, ElenorFx.LIGHT); // 当たったときは hurt の着弾の演出
                it.remove();
            }
        }
        for (Iterator<Sigil> it = sigils.iterator(); it.hasNext(); ) {
            Sigil sigil = it.next();
            if (sigil.ticks % 6 == 0) ring(level, sigil.at, 2.5, 14);
            if (--sigil.ticks > 0) continue;
            // 炸裂: レースの輪と衝撃波、花が開いて花びらが舞い上がる
            ElenorFx.lace(level, sigil.at, ElenorFx.HOT, 3.2F, 14);
            com.automatavalkyries.weapon.Fx.shockwave(level, sigil.at.add(0, 0.1, 0), ElenorFx.PINK, 3.0F, 10);
            ElenorFx.impact(level, sigil.at.add(0, 0.6, 0), 0);
            ElenorFx.petalBurst(level, sigil.at.add(0, 0.4, 0), 14, 0.35, ElenorFx.PINK);
            level.playSound(null, sigil.at.x, sigil.at.y, sigil.at.z, SoundEvents.GENERIC_EXPLODE, elenor.getSoundSource(), 0.8F, 1.4F);
            for (LivingEntity player : foes(level, new net.minecraft.world.phys.AABB(sigil.at, sigil.at).inflate(2.5, 2.0, 2.5))) {
                if (!ElenorPressure.fightable(player)) continue;
                hurt(player, 9.0F);
                terminusOn(level, player); // 終焉の魔法陣
            }
            it.remove();
        }
    }

    /** 床に光の輪を描く（前兆・魔法陣） */
    private static void ring(ServerLevel level, Vec3 center, double radius, int points) {
        ElenorFx.floorRing(level, center, radius, points, ElenorFx.PINK);
    }

    /**
     * 技が当たる相手: 戦える（クリエイティブ・スペクテイター以外の）プレイヤーと、プレイヤー以外の相手（エレノアが今狙っているもの・
     * 危険なフラグを持つモブ・最近エレノアを攻撃したもの）。モブバトルでも、技が通り、ティアが数えられる
     */
    private List<LivingEntity> foes(ServerLevel level, net.minecraft.world.phys.AABB box) {
        List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(Player.class, box, ElenorPressure::fightable));
        LivingEntity aim = elenor.getTarget();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                x -> !(x instanceof Player) && (x == aim || elenor.isHostileCandidate(x)))) {
            list.add(e);
        }
        return list;
    }

    /** 技のダメージを、相手のティアの攻め方で通す（攻撃力・係数を掛ける） */
    private void hurt(LivingEntity victim, float base) {
        if (elenor.level() instanceof ServerLevel level) {
            ElenorFx.impact(level, victim.position().add(0, victim.getBbHeight() * 0.55, 0), pressure.tier(victim));
        }
        pressure.strike(victim, (float) (base * scale() * pressure.attackScale()));
    }

    /** 終焉の技（ティア 7 の相手にだけ。最果てのエレノアは全員） */
    private void terminusOn(ServerLevel level, LivingEntity victim) {
        pressure.terminus(level, victim);
    }

    // ---- 戦いの流れ ----

    /** ゴールの 1 Tick。標的がいるときだけ呼ばれる */
    public void tick(LivingEntity target) {
        if (!(elenor.level() instanceof ServerLevel level)) return;
        float ratio = testRatio >= 0.0F ? testRatio : elenor.getEprotect().healthRatio();
        int now = phaseFor(ratio);
        if (fullPower) now = 3; // 最初から第 3
        theater.open(level, now); // 開幕（開いていれば、戦っていることだけ伝える）
        if (!introDone) {
            introDone = true;
            phase = now;
            elenor.setBarPhase(now);
            if (fullPower) nextCurtain = level.getGameTime() + 1200L;
            if (now == 1) say("message.automata_valkyries.elenor.intro");
        } else if (now > phase) {
            phase = now;
            enterPhase(level);
            theater.nextAct(level, now);
        } else if (!curtainDone && move != Move.TRANSITION && ratio <= CURTAIN_RATIO) {
            curtainDone = true;
            start(Move.CURTAIN);
            say("message.automata_valkyries.elenor.curtain");
        } else if (fullPower && move == Move.NONE && level.getGameTime() >= nextCurtain) { // 全開版は 60 秒ごとに
            nextCurtain = level.getGameTime() + 1200L;
            start(Move.CURTAIN);
            say("message.automata_valkyries.elenor.curtain");
        }
        elenor.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (meleeCooldown > 0) meleeCooldown--;
        if (move != Move.NONE) {
            runMove(level, target);
            return;
        }
        if (cooldown > 0) cooldown--;
        double distance = elenor.distanceTo(target);
        if (cooldown <= 0) {
            choose(distance, target);
            if (move != Move.NONE) return;
        }
        // 技の合間: 近接（解けたリボンの鞭。素手より遠く 3.5 ブロックまで届く）
        if (distance > 3.2D) {
            elenor.getNavigation().moveTo(target, 1.2D);
        } else if (meleeCooldown <= 0) {
            ribbonMelee(level, target);
            meleeCooldown = 24;
        }
    }

    /**
     * 近接: 解けたリボンの鞭で、前の半円を斜めに払う（左右交互）。当たり判定も前の半円（3.5 ブロック）で、届いた相手みんなに当たる。
     * 終焉の許可が入っている間は、今までどおり終焉で打つ（{@link ElenorEntity#doHurtTarget}）
     */
    private void ribbonMelee(ServerLevel level, LivingEntity target) {
        Vec3 dir = target.position().subtract(elenor.position());
        meleeSide = -meleeSide;
        ElenorFx.ribbonWhip(level, elenor.position(), dir, 3.5, meleeSide);
        level.playSound(null, elenor.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, elenor.getSoundSource(), 1.0F, 1.3F);
        elenor.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        elenor.triggerAnim("action", "point");
        if (elenor.isTerminusEnabled()) {
            elenor.doHurtTarget(target);
            return;
        }
        for (LivingEntity foe : foes(level, elenor.getBoundingBox().inflate(3.5, 1.0, 3.5))) {
            Vec3 rel = foe.position().subtract(elenor.position());
            if (rel.x * dir.x + rel.z * dir.z < 0.0) continue; // 後ろには届かない
            if (ElenorPressure.fightable(foe) && elenor.distanceTo(foe) <= 4.0F) hurt(foe, 12.0F);
        }
    }

    /** フェーズが進んだ: 途中の技をやめて、移行の演出（{@link Move#TRANSITION}）に入る */
    private void enterPhase(ServerLevel level) {
        say(phase == 2 ? "message.automata_valkyries.elenor.phase2" : "message.automata_valkyries.elenor.phase3");
        elenor.setBarPhase(phase);
        level.playSound(null, elenor.blockPosition(), SoundEvents.WITHER_SPAWN, elenor.getSoundSource(), 0.7F, phase == 2 ? 1.4F : 0.9F);
        start(Move.TRANSITION);
    }

    private void start(Move next) {
        move = next;
        moveTick = 0;
        ringsLeft = 0;
        bindHit = false;
        mirages.clear();
        elenor.getNavigation().stop();
    }

    /** 黒とピンクのリボンの粒の渦（angle から始まる 2 本の螺旋。高さ height、半径 radius） */
    static void ribbonSwirl(ServerLevel level, Vec3 base, double radius, double height, double angle) {
        ElenorFx.ribbonSwirl(level, base, radius, height, angle);
    }

    /** 粒の残像（ドレスの形: 細い胴と、裾の広がった輪） */
    private static void mirage(ServerLevel level, Vec3 at, int tick) {
        ElenorFx.mirage(level, at, tick);
    }

    /** from から標的へ、count 発を扇に撃つ（間隔 spread 度） */
    private void fan(ServerLevel level, Vec3 from, LivingEntity target, int count, double spread, double speed) {
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        double base = Math.atan2(aim.z, aim.x);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 1.0F, 1.5F);
        for (int i = 0; i < count; i++) {
            double angle = base + Math.toRadians((i - (count - 1) / 2.0) * spread);
            orbs.add(new Orb(from, new Vec3(Math.cos(angle) * speed, aim.y * speed, Math.sin(angle) * speed)));
        }
    }

    private void choose(double distance, LivingEntity target) {
        net.minecraft.util.RandomSource random = elenor.getRandom();
        List<Move> pool = new ArrayList<>();
        if (distance <= 4.0D) {
            pool.add(Move.NOVA);
            pool.add(Move.NOVA);
            if (phase >= 2) pool.add(Move.BLINK);
        } else {
            pool.add(Move.LUNGE);
            if (phase >= 2) {
                pool.add(Move.VOLLEY);
                pool.add(Move.VOLLEY);
                pool.add(Move.BLINK);
                if (distance <= 14.0D) pool.add(Move.BIND);
            }
            if (phase >= 3) {
                pool.add(Move.RING);
                pool.add(Move.SIGIL);
                pool.add(Move.MIRAGE);
            }
        }
        if (pressure.tier(target) >= ElenorPressure.MAX_TIER) { // ティア 7: 終焉入りの技（縛め・魔法陣・幕引き）を主軸にする
            for (int i = 0; i < 3; i++) pool.add(Move.SIGIL);
            if (distance <= 14.0D) for (int i = 0; i < 4; i++) pool.add(Move.BIND);
            if (fullPower || phase >= 3) for (int i = 0; i < 2; i++) pool.add(Move.CURTAIN);
        }
        start(pool.get(random.nextInt(pool.size())));
        ringsLeft = move == Move.RING ? 2 : 0;
    }

    private void finish() {
        move = Move.NONE;
        moveTick = 0;
        int gap = fullPower ? 20 : phase == 1 ? 60 : phase == 2 ? 45 : 32;
        cooldown = Math.max(8, (int) (gap / pressure.tempo(elenor.getTarget())));
    }

    private void runMove(ServerLevel level, LivingEntity target) {
        moveTick++;
        Vec3 eye = elenor.position().add(0, 1.0, 0);
        switch (move) {
            case LUNGE -> {
                if (moveTick == 1) {
                    Vec3 flat = target.position().subtract(elenor.position());
                    lungeDir = new Vec3(flat.x, 0, flat.z).normalize();
                    level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, elenor.getSoundSource(), 1.2F, 0.7F);
                }
                if (moveTick <= 18) { // 前兆: 進む先へ光の筋が流れ、行き先の床に輪
                    Vec3 from = elenor.position().add(0, 0.3, 0);
                    if (moveTick % 3 == 0) ElenorFx.streakLine(level, from, from.add(lungeDir.scale(7.0)), 4, ElenorFx.PINK);
                    if (moveTick % 6 == 0) ring(level, elenor.position().add(lungeDir.scale(7.0)), 1.0, 10);
                } else if (moveTick <= 24) { // 踏み込み: リボンと光の筋の残像
                    if (moveTick == 19) elenor.triggerAnim("action", "lunge");
                    elenor.setDeltaMovement(lungeDir.x * 1.2, elenor.getDeltaMovement().y, lungeDir.z * 1.2);
                    elenor.hurtMarked = true;
                    Vec3 back = elenor.position().add(0, 1.0, 0);
                    ElenorFx.streakLine(level, back, back.subtract(lungeDir.scale(2.5)), 3, ElenorFx.LIGHT);
                    ElenorFx.ribbonArc(level, elenor.position(), lungeDir, 2.6, (moveTick - 18) / 6.0); // 解けたリボンの鞭で前を払う
                    for (LivingEntity player : foes(level, elenor.getBoundingBox().inflate(2.6, 0.8, 2.6))) { // 鞭の届く前の半円
                        Vec3 rel = player.position().subtract(elenor.position());
                        if (rel.x * lungeDir.x + rel.z * lungeDir.z < -0.5) continue;
                        if (ElenorPressure.fightable(player) && player.invulnerableTime <= 10) hurt(player, 14.0F);
                    }
                } else {
                    finish();
                }
            }
            case NOVA -> {
                if (moveTick == 1) {
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BEACON_ACTIVATE, elenor.getSoundSource(), 1.0F, 1.5F);
                    ElenorFx.lace(level, elenor.position(), ElenorFx.HOT, 5.0F, 20); // 前兆: 届く範囲にレースの輪
                    elenor.triggerAnim("action", "cast");
                }
                if (moveTick < 20) {
                    if (moveTick % 4 == 0) ring(level, elenor.position(), 5.0, 24);
                    if (moveTick % 3 == 0) com.automatavalkyries.weapon.Fx.gather(level, com.automatavalkyries.weapon.Fx.opt(
                            com.automatavalkyries.registry.ModParticles.PETAL.get(), ElenorFx.PINK, 0.16F, 10), elenor.position().add(0, 1.0, 0), 6, 4.0, 10);
                } else if (moveTick == 20) {
                    elenor.triggerAnim("action", "skirt_spread");
                    // 炸裂: 衝撃波とレース、花びらの嵐
                    com.automatavalkyries.weapon.Fx.shockwave(level, elenor.position().add(0, 0.1, 0), ElenorFx.PINK, 6.0F, 12);
                    ElenorFx.lace(level, elenor.position(), ElenorFx.LIGHT, 6.0F, 14);
                    ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 24, 0.6, ElenorFx.PINK);
                    ElenorFx.ribbonBurst(level, elenor.position(), 12, 0.55); // 解けたリボンが四方へ放たれる
                    com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.BLOOM.get(),
                            ElenorFx.HOT, 2.4F, 14), elenor.position().add(0, 1.0, 0), Vec3.ZERO);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.GENERIC_EXPLODE, elenor.getSoundSource(), 0.9F, 1.2F);
                    for (LivingEntity player : foes(level, elenor.getBoundingBox().inflate(5.0, 2.0, 5.0))) {
                        if (!ElenorPressure.fightable(player)) continue;
                        hurt(player, 8.0F);
                        Vec3 away = player.position().subtract(elenor.position()).normalize();
                        player.push(away.x * 1.1, 0.5, away.z * 1.1);
                        player.hurtMarked = true;
                    }
                } else if (moveTick > 26) {
                    finish();
                }
            }
            case VOLLEY -> {
                if (moveTick < 16) { // 前兆: 光が胸元に集まり、小さな星の陣
                    if (moveTick % 2 == 0) com.automatavalkyries.weapon.Fx.gather(level, com.automatavalkyries.weapon.Fx.opt(
                            com.automatavalkyries.registry.ModParticles.GLOW.get(), ElenorFx.PINK, 0.25F, 8), eye.add(0, 0.4, 0), 3, 1.6, 8);
                    if (moveTick == 2) {
                        com.automatavalkyries.weapon.Fx.mandala(level, eye.add(0, 0.4, 0), ElenorFx.HOT, 0.8F, 16, true, false);
                        elenor.triggerAnim("action", "point");
                    }
                } else if (moveTick == 16) {
                    com.automatavalkyries.weapon.Fx.star(level, eye.add(0, 0.4, 0), ElenorFx.LIGHT, 1.2F, 8, Vec3.ZERO);
                    int count = phase >= 3 ? 7 : 5;
                    Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(eye).normalize();
                    double base = Math.atan2(aim.z, aim.x);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 1.0F, 1.5F);
                    for (int i = 0; i < count; i++) {
                        double angle = base + Math.toRadians((i - (count - 1) / 2.0) * 12.0);
                        orbs.add(new Orb(eye, new Vec3(Math.cos(angle) * 0.7, aim.y * 0.7, Math.sin(angle) * 0.7)));
                    }
                } else if (moveTick > 24) {
                    finish();
                }
            }
            case BLINK -> {
                if (moveTick == 1) { // 花びらにほどけて消え、相手の背後で花びらから現れる。間を光の筋が走る
                    elenor.triggerAnim("action", "spin");
                    Vec3 from = elenor.position().add(0, 1.0, 0);
                    ElenorFx.petalBurst(level, from, 18, 0.3, ElenorFx.PINK);
                    ElenorFx.ribbonSwirl(level, elenor.position(), 0.6, 2.0, 0.0);
                    blinkBehind(level, target);
                    Vec3 to = elenor.position().add(0, 1.0, 0);
                    ElenorFx.streakLine(level, from, to, 8, ElenorFx.LIGHT);
                    ElenorFx.petalBurst(level, to, 18, 0.3, ElenorFx.PINK);
                    ElenorFx.ribbonSwirl(level, elenor.position(), 0.6, 2.0, 1.0);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, elenor.getSoundSource(), 1.0F, 1.2F);
                } else if (moveTick >= 12) { // 背後から一撃
                    if (elenor.distanceTo(target) <= 4.0D) ribbonMelee(level, target); // 背後からリボンの鞭
                    meleeCooldown = 20;
                    finish();
                }
            }
            case RING -> {
                if (moveTick < 25 && ringsLeft == 2) {
                    if (moveTick == 1) {
                        ElenorFx.lace(level, elenor.position(), ElenorFx.PINK, 4.0F, 24);
                        elenor.triggerAnim("action", "cast");
                    }
                    if (moveTick % 3 == 0) ring(level, elenor.position(), 1.0 + moveTick * 0.12, 20);
                } else if (moveTick == 25 || (moveTick == 45 && ringsLeft == 1)) {
                    double offset = ringsLeft == 1 ? Math.PI / 16.0 : 0.0;
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 1.0F, 0.8F);
                    com.automatavalkyries.weapon.Fx.star(level, eye, ElenorFx.LIGHT, 1.4F, 8, Vec3.ZERO);
                    for (int i = 0; i < 16; i++) {
                        double angle = Math.PI * 2 * i / 16.0 + offset;
                        orbs.add(new Orb(eye, new Vec3(Math.cos(angle) * 0.45, 0.0, Math.sin(angle) * 0.45)));
                    }
                    ringsLeft--;
                } else if (moveTick > 50) {
                    finish();
                }
            }
            case SIGIL -> {
                if (moveTick == 1) elenor.triggerAnim("action", "cast");
                if (moveTick == 12) {
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, elenor.getSoundSource(), 1.2F, 0.8F);
                    sigils.add(new Sigil(target.position()));
                    for (int i = 0; i < 2; i++) {
                        double angle = elenor.getRandom().nextDouble() * Math.PI * 2;
                        sigils.add(new Sigil(target.position().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0)));
                    }
                    for (Sigil sigil : sigils) ElenorFx.sigil(level, sigil.at, 2.5F, sigil.ticks); // 床に星の陣が回る（炸裂まで）
                } else if (moveTick > 24) {
                    finish();
                }
            }
            case TRANSITION -> {
                // 止まってスカートを広げ、リボンの粒が渦を巻く → 周りを押し返す（ダメージなし）
                elenor.setDeltaMovement(0, elenor.getDeltaMovement().y, 0);
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "skirt_spread");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, elenor.getSoundSource(), 1.5F, 0.8F);
                }
                if (moveTick < 24 && moveTick % 2 == 0) ribbonSwirl(level, elenor.position(), 1.6 - moveTick * 0.04, 2.4, moveTick * 0.35);
                if (moveTick == 24) {
                    // リボンと花びらの嵐、足もとにレースの輪、大きな花が開く
                    ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 30, 0.7, ElenorFx.PINK);
                    ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 16, 0.5, ElenorFx.DARK);
                    ElenorFx.lace(level, elenor.position(), ElenorFx.HOT, 4.5F, 16);
                    com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.BLOOM.get(),
                            ElenorFx.PINK, 3.0F, 16), elenor.position().add(0, 1.2, 0), Vec3.ZERO);
                    ring(level, elenor.position(), 3.0, 24);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.GENERIC_EXPLODE, elenor.getSoundSource(), 0.6F, 1.6F);
                    for (LivingEntity player : foes(level, elenor.getBoundingBox().inflate(6.0, 3.0, 6.0))) {
                        if (!ElenorPressure.fightable(player)) continue;
                        Vec3 away = player.position().subtract(elenor.position()).multiply(1, 0, 1).normalize();
                        player.push(away.x * 1.4, 0.45, away.z * 1.4);
                        player.hurtMarked = true;
                    }
                }
                if (moveTick == 26) elenor.triggerAnim("action", "curtsy");
                if (moveTick > 36) {
                    finish();
                    cooldown = 20;
                }
            }
            case BIND -> {
                // 前兆: 標的へ向かう線（14 Tick まで標的を追い、そこで向きが決まる）→ リボンが先へ伸びる。当たると引き寄せて鈍足
                if (moveTick <= 14) {
                    bindDir = target.position().add(0, 1.0, 0).subtract(eye).normalize();
                    if (moveTick == 1) {
                        level.playSound(null, elenor.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, elenor.getSoundSource(), 1.5F, 0.7F);
                        elenor.triggerAnim("action", "point");
                    }
                }
                if (moveTick <= 22) {
                    if (moveTick % 3 == 0) ElenorFx.streakLine(level, eye, eye.add(bindDir.scale(14.0)), 5, moveTick > 14 ? ElenorFx.HOT : ElenorFx.PINK); // 前兆の線
                } else if (moveTick <= 30 && !bindHit) {
                    if (moveTick == 23) level.playSound(null, elenor.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, elenor.getSoundSource(), 1.5F, 0.6F);
                    double reach = (moveTick - 22) * 1.75; // 8 Tick で 14 ブロック
                    for (double d = 0.8; d <= reach; d += 0.8) { // リボンが先へ伸びる（桃と暗い紫が交互）
                        Vec3 p = eye.add(bindDir.scale(d));
                        com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.RIBBON.get(),
                                ((int) (d / 0.8)) % 2 == 0 ? ElenorFx.PINK : ElenorFx.DARK, 0.3F, 10), p, Vec3.ZERO);
                    }
                    Vec3 tip = eye.add(bindDir.scale(reach));
                    for (LivingEntity player : foes(level, new net.minecraft.world.phys.AABB(eye, tip).inflate(1.0))) {
                        if (!ElenorPressure.fightable(player)) continue;
                        Vec3 rel = player.position().add(0, 1.0, 0).subtract(eye);
                        double along = rel.dot(bindDir);
                        if (along < 0 || along > reach || rel.subtract(bindDir.scale(along)).length() > 1.0) continue;
                        bindHit = true;
                        hurt(player, 5.0F);
                        terminusOn(level, player); // 終焉の縛め
                        Vec3 pull = elenor.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                        player.setDeltaMovement(pull.x * 1.3, 0.35, pull.z * 1.3);
                        player.hurtMarked = true;
                        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                        level.playSound(null, player.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, elenor.getSoundSource(), 1.5F, 0.8F);
                        ElenorFx.ribbonSwirl(level, player.position(), 0.7, 1.8, 0.0); // 相手にリボンが巻きつく
                        break;
                    }
                } else if (moveTick > 34) {
                    finish();
                }
            }
            case MIRAGE -> {
                // 標的の左右 5 ブロックに粒の残像を 2 体置き、順に光弾を撃たせる
                if (moveTick == 1) {
                    Vec3 side = target.getLookAngle().multiply(1, 0, 1).yRot((float) (Math.PI / 2));
                    side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
                    mirages.add(target.position().add(side.scale(5.0)));
                    mirages.add(target.position().add(side.scale(-5.0)));
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, elenor.getSoundSource(), 1.5F, 1.0F);
                    elenor.triggerAnim("action", "spin");
                }
                if (moveTick % 2 == 0) {
                    for (Vec3 at : mirages) mirage(level, at, moveTick);
                }
                if ((moveTick == 20 || moveTick == 32) && mirages.size() == 2) {
                    com.automatavalkyries.weapon.Fx.star(level, mirages.get(moveTick == 20 ? 0 : 1).add(0, 1.2, 0), ElenorFx.VIOLET, 1.2F, 8, Vec3.ZERO);
                    fan(level, mirages.get(moveTick == 20 ? 0 : 1).add(0, 1.0, 0), target, 3, 14.0, 0.6);
                }
                if (moveTick > 44) {
                    for (Vec3 at : mirages) ElenorFx.petalBurst(level, at.add(0, 1.0, 0), 14, 0.3, ElenorFx.VIOLET); // 残像は花びらにほどけて消える
                    mirages.clear();
                    finish();
                }
            }
            case CURTAIN -> {
                // 5 秒の弾幕（10 Tick ごとに回る輪、25 Tick ごとに標的へ扇）。その場から動かない。攻撃は通常どおり通る
                elenor.setDeltaMovement(0, elenor.getDeltaMovement().y, 0);
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "skirt_spread");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_RESONATE, elenor.getSoundSource(), 2.0F, 1.2F);
                }
                if (moveTick % 3 == 0) ribbonSwirl(level, elenor.position(), 1.2, 2.2, moveTick * 0.3);
                if (moveTick % 5 == 0 && moveTick < 85) ElenorFx.petals(level, elenor.position().add(0, 5.0, 0), 8, 7.0, ElenorFx.PINK); // 花びらが降りしきる
                if (moveTick >= 10 && moveTick <= 100 && moveTick % 10 == 0) {
                    double offset = moveTick * 0.13;
                    for (int i = 0; i < 12; i++) {
                        double angle = Math.PI * 2 * i / 12.0 + offset;
                        orbs.add(new Orb(eye, new Vec3(Math.cos(angle) * 0.4, 0.0, Math.sin(angle) * 0.4)));
                    }
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 0.8F, 0.9F);
                }
                if (moveTick >= 25 && moveTick <= 100 && moveTick % 25 == 0) fan(level, eye, target, 5, 10.0, 0.75);
                if (moveTick >= 85 && moveTick < 105 && moveTick % 5 == 0) { // 前兆: 終わりに来る輪（半径 14）に、緞帳が降りてくる
                    ring(level, elenor.position(), 14.0, 48);
                    ElenorFx.curtainCircle(level, elenor.position(), 14.0, 16, moveTick * 0.05);
                }
                if (moveTick == 105) { // 終焉の幕引き: 暗いレースが広がり、真ん中で黒い花が開く
                    level.playSound(null, elenor.blockPosition(), SoundEvents.WITHER_DEATH, elenor.getSoundSource(), 1.2F, 1.5F);
                    ElenorFx.lace(level, elenor.position(), ElenorFx.DARK, 14.0F, 24);
                    com.automatavalkyries.weapon.Fx.shockwave(level, elenor.position().add(0, 0.1, 0), ElenorFx.CRIMSON, 14.0F, 18);
                    ElenorFx.impact(level, elenor.position().add(0, 1.2, 0), 7);
                    elenor.triggerAnim("action", "curtsy"); // 幕引きのお辞儀
                    for (LivingEntity player : foes(level, elenor.getBoundingBox().inflate(14.0, 6.0, 14.0))) {
                        if (ElenorPressure.fightable(player)) terminusOn(level, player);
                    }
                }
                if (moveTick > 110) {
                    finish();
                    cooldown = fullPower ? 30 : 50;
                }
            }
            default -> finish();
        }
    }

    /** 標的の背後（見ている向きの反対）3 ブロックへ。置ける場所が無ければその場で終わる */
    private void blinkBehind(ServerLevel level, LivingEntity target) {
        Vec3 back = target.getLookAngle().multiply(1, 0, 1).normalize().scale(-3.0);
        for (double scale : new double[]{1.0, 0.6, 0.3}) {
            Vec3 to = target.position().add(back.scale(scale));
            if (level.noCollision(elenor, elenor.getBoundingBox().move(to.subtract(elenor.position())))) {
                elenor.teleportTo(to.x, to.y, to.z);
                return;
            }
        }
    }

    private void say(String key) {
        Component message = Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
        for (ServerPlayer player : level().players()) {
            if (player.distanceToSqr(elenor) <= 48.0D * 48.0D) player.displayClientMessage(message, false);
        }
    }

    // ---- ゴール ----

    /** 標的がいる間、技と近接を回す。エンティティの構築中（registerGoals）には技の状態がまだ無いので、毎回エンティティから引く */
    public static final class ArtsGoal extends Goal {

        private final ElenorEntity elenor;

        public ArtsGoal(ElenorEntity elenor) {
            this.elenor = elenor;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = elenor.getTarget();
            return elenor.arts() != null && target != null && elenor.isReachable(target)
                    && (target.isAlive() || com.automatavalkyries.annihilate.MadnessAnnihilation.isMadness(target));
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = elenor.getTarget();
            ElenorArts arts = elenor.arts();
            if (target != null && arts != null) arts.tick(target);
        }

        @Override
        public void stop() {
            ElenorArts arts = elenor.arts();
            if (arts != null) {
                arts.move = Move.NONE;
                arts.mirages.clear();
            }
        }
    }
}
