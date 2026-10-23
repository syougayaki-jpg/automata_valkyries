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

    private static final class Orb {
        Vec3 pos;
        final Vec3 vel;
        int life = 80;

        Orb(Vec3 pos, Vec3 vel) {
            this.pos = pos;
            this.vel = vel;
        }
    }

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
        for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
            Orb orb = it.next();
            orb.pos = orb.pos.add(orb.vel);
            if (orb.life % 2 == 0) { // 粒子の送信を半分に（光弾は多いと 30 発を超える）
                level.sendParticles(PURPLE, orb.pos.x, orb.pos.y, orb.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, orb.pos.x, orb.pos.y, orb.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            boolean hit = false;
            for (LivingEntity player : foes(level, new net.minecraft.world.phys.AABB(orb.pos, orb.pos).inflate(0.7))) {
                if (!ElenorPressure.fightable(player)) continue;
                hurt(player, 6.0F);
                hit = true;
                break;
            }
            BlockPos block = BlockPos.containing(orb.pos);
            if (hit || --orb.life <= 0 || !level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
                level.sendParticles(ParticleTypes.WITCH, orb.pos.x, orb.pos.y, orb.pos.z, 8, 0.2, 0.2, 0.2, 0.05);
                it.remove();
            }
        }
        for (Iterator<Sigil> it = sigils.iterator(); it.hasNext(); ) {
            Sigil sigil = it.next();
            if (sigil.ticks % 3 == 0) ring(level, sigil.at, 2.5, 14);
            if (--sigil.ticks > 0) continue;
            level.sendParticles(ParticleTypes.EXPLOSION, sigil.at.x, sigil.at.y + 0.3, sigil.at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.WITCH, sigil.at.x, sigil.at.y + 0.5, sigil.at.z, 30, 1.2, 0.6, 1.2, 0.1);
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
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(PURPLE, center.x + Math.cos(angle) * radius, center.y + 0.1, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
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
        if (!introDone) {
            introDone = true;
            phase = now;
            elenor.setBarPhase(now);
            if (fullPower) nextCurtain = level.getGameTime() + 1200L;
            if (now == 1) say("message.automata_valkyries.elenor.intro");
        } else if (now > phase) {
            phase = now;
            enterPhase(level);
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
        // 技の合間: 近接
        if (distance > 2.2D) {
            elenor.getNavigation().moveTo(target, 1.2D);
        } else if (meleeCooldown <= 0) {
            elenor.doHurtTarget(target);
            meleeCooldown = 24;
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
        for (int i = 0; i < 8; i++) {
            double t = i / 8.0;
            for (int strand = 0; strand < 2; strand++) {
                double a = angle + t * Math.PI * 2 + strand * Math.PI;
                level.sendParticles(strand == 0 ? RIBBON_PINK : RIBBON_BLACK,
                        base.x + Math.cos(a) * radius, base.y + t * height, base.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 粒の残像（ドレスの形: 細い胴と、裾の広がった輪） */
    private static void mirage(ServerLevel level, Vec3 at, int tick) {
        for (int i = 0; i < 6; i++) {
            level.sendParticles(i % 2 == 0 ? RIBBON_PINK : RIBBON_BLACK, at.x, at.y + 0.7 + i * 0.18, at.z, 1, 0.08, 0.02, 0.08, 0);
        }
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10.0 + tick * 0.15;
            level.sendParticles(PURPLE, at.x + Math.cos(a) * 0.45, at.y + 0.35, at.z + Math.sin(a) * 0.45, 1, 0, 0, 0, 0);
        }
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
                if (moveTick <= 18) { // 前兆: 進む先に光の筋
                    for (int i = 1; i <= 6; i++) {
                        Vec3 p = elenor.position().add(lungeDir.scale(i));
                        level.sendParticles(PURPLE, p.x, p.y + 0.2, p.z, 1, 0.1, 0, 0.1, 0);
                    }
                } else if (moveTick <= 24) { // 踏み込み
                    elenor.setDeltaMovement(lungeDir.x * 1.2, elenor.getDeltaMovement().y, lungeDir.z * 1.2);
                    elenor.hurtMarked = true;
                    for (LivingEntity player : foes(level, elenor.getBoundingBox().inflate(0.8))) {
                        if (ElenorPressure.fightable(player) && player.invulnerableTime <= 10) hurt(player, 14.0F);
                    }
                } else {
                    finish();
                }
            }
            case NOVA -> {
                if (moveTick == 1) level.playSound(null, elenor.blockPosition(), SoundEvents.BEACON_ACTIVATE, elenor.getSoundSource(), 1.0F, 1.5F);
                if (moveTick < 20) {
                    ring(level, elenor.position(), 5.0 * moveTick / 20.0, 20);
                    ring(level, elenor.position(), 5.0, 24);
                } else if (moveTick == 20) {
                    elenor.triggerAnim("action", "skirt_spread");
                    level.sendParticles(ParticleTypes.EXPLOSION, elenor.getX(), elenor.getY() + 0.5, elenor.getZ(), 3, 1.5, 0.2, 1.5, 0);
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
                if (moveTick < 16) {
                    level.sendParticles(ParticleTypes.WITCH, eye.x, eye.y + 0.6, eye.z, 4, 0.4, 0.3, 0.4, 0.02);
                } else if (moveTick == 16) {
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
                if (moveTick == 1) {
                    level.sendParticles(ParticleTypes.PORTAL, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
                    blinkBehind(level, target);
                    level.sendParticles(ParticleTypes.PORTAL, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, elenor.getSoundSource(), 1.0F, 1.2F);
                } else if (moveTick >= 12) { // 背後から一撃
                    if (elenor.distanceTo(target) <= 3.0D) elenor.doHurtTarget(target);
                    meleeCooldown = 20;
                    finish();
                }
            }
            case RING -> {
                if (moveTick < 25 && ringsLeft == 2) {
                    ring(level, elenor.position(), 1.0 + moveTick * 0.12, 20);
                } else if (moveTick == 25 || (moveTick == 45 && ringsLeft == 1)) {
                    double offset = ringsLeft == 1 ? Math.PI / 16.0 : 0.0;
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 1.0F, 0.8F);
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
                if (moveTick == 12) {
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, elenor.getSoundSource(), 1.2F, 0.8F);
                    sigils.add(new Sigil(target.position()));
                    for (int i = 0; i < 2; i++) {
                        double angle = elenor.getRandom().nextDouble() * Math.PI * 2;
                        sigils.add(new Sigil(target.position().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0)));
                    }
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
                    level.sendParticles(RIBBON_PINK, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 40, 1.5, 0.8, 1.5, 0);
                    level.sendParticles(RIBBON_BLACK, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 40, 1.5, 0.8, 1.5, 0);
                    level.sendParticles(ParticleTypes.END_ROD, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 30, 0.5, 1.0, 0.5, 0.15);
                    ring(level, elenor.position(), 3.0, 24);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.GENERIC_EXPLODE, elenor.getSoundSource(), 0.6F, 1.6F);
                    for (LivingEntity player : foes(level, elenor.getBoundingBox().inflate(6.0, 3.0, 6.0))) {
                        if (!ElenorPressure.fightable(player)) continue;
                        Vec3 away = player.position().subtract(elenor.position()).multiply(1, 0, 1).normalize();
                        player.push(away.x * 1.4, 0.45, away.z * 1.4);
                        player.hurtMarked = true;
                    }
                }
                if (moveTick > 36) {
                    finish();
                    cooldown = 20;
                }
            }
            case BIND -> {
                // 前兆: 標的へ向かう線（14 Tick まで標的を追い、そこで向きが決まる）→ リボンが先へ伸びる。当たると引き寄せて鈍足
                if (moveTick <= 14) {
                    bindDir = target.position().add(0, 1.0, 0).subtract(eye).normalize();
                    if (moveTick == 1) level.playSound(null, elenor.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, elenor.getSoundSource(), 1.5F, 0.7F);
                }
                if (moveTick <= 22) {
                    if (moveTick % 2 == 0) {
                        for (int i = 1; i <= 14; i++) {
                            Vec3 p = eye.add(bindDir.scale(i));
                            level.sendParticles(moveTick > 14 ? RIBBON_PINK : PURPLE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                        }
                    }
                } else if (moveTick <= 30 && !bindHit) {
                    if (moveTick == 23) level.playSound(null, elenor.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, elenor.getSoundSource(), 1.5F, 0.6F);
                    double reach = (moveTick - 22) * 1.75; // 8 Tick で 14 ブロック
                    for (double d = 0.5; d <= reach; d += 0.5) {
                        Vec3 p = eye.add(bindDir.scale(d));
                        level.sendParticles(((int) (d * 2)) % 2 == 0 ? RIBBON_PINK : RIBBON_BLACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
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
                    elenor.triggerAnim("action", "skirt_spread");
                }
                if (moveTick % 2 == 0) {
                    for (Vec3 at : mirages) mirage(level, at, moveTick);
                }
                if ((moveTick == 20 || moveTick == 32) && mirages.size() == 2) {
                    fan(level, mirages.get(moveTick == 20 ? 0 : 1).add(0, 1.0, 0), target, 3, 14.0, 0.6);
                }
                if (moveTick > 44) {
                    for (Vec3 at : mirages) level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 1.0, at.z, 16, 0.3, 0.5, 0.3, 0.05);
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
                if (moveTick >= 10 && moveTick <= 100 && moveTick % 10 == 0) {
                    double offset = moveTick * 0.13;
                    for (int i = 0; i < 12; i++) {
                        double angle = Math.PI * 2 * i / 12.0 + offset;
                        orbs.add(new Orb(eye, new Vec3(Math.cos(angle) * 0.4, 0.0, Math.sin(angle) * 0.4)));
                    }
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 0.8F, 0.9F);
                }
                if (moveTick >= 25 && moveTick <= 100 && moveTick % 25 == 0) fan(level, eye, target, 5, 10.0, 0.75);
                if (moveTick >= 85 && moveTick < 105 && moveTick % 5 == 0) { // 前兆: 終わりに来る輪（半径 14）
                    ring(level, elenor.position(), 14.0, 48);
                    ribbonSwirl(level, elenor.position(), 14.0, 1.0, moveTick * 0.2);
                }
                if (moveTick == 105) { // 終焉の幕引き
                    level.playSound(null, elenor.blockPosition(), SoundEvents.WITHER_DEATH, elenor.getSoundSource(), 1.2F, 1.5F);
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
            return elenor.arts() != null && target != null
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
