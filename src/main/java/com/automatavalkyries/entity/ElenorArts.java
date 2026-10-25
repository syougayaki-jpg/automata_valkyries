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

    /**
     * 技。第一幕: 踏み込み・ノヴァ・輪舞の連撃・スポットライト・吊り物落とし（大技: 開幕の円舞曲）。第二幕で光弾の扇・瞬間移動・縛め・操り糸の檻・
     * 奈落の迫り・早替わり（大技: パ・ド・ドゥ）、終幕で光弾の輪・魔法陣・残像・大見得斬り・薔薇の雨・千秋楽の綱（大技: 緋色の独舞）が加わる（2026-10-24）
     */
    enum Move {
        NONE, INTRO, LUNGE, VOLLEY, NOVA, BLINK, RING, SIGIL, TRANSITION, BIND, MIRAGE, CURTAIN,
        RONDO, SPOTLIGHT, FLY_DROP, MARIONETTE, TRAPDOOR, QUICK_CHANGE, MIE, ROSE_RAIN, CHORDS,
        OVERTURE, PAS_DE_DEUX, SOLO,
        HELIX, HOMING, CELESTIAL
    }

    /** 三次元の弾幕（螺旋の昇天・星蝶の追尾・星環の天球）。ティア 7 の相手には終焉入り */
    static boolean isAerial(Move m) {
        return m == Move.HELIX || m == Move.HOMING || m == Move.CELESTIAL;
    }

    /** 幕の大技 */
    static boolean isUltimate(Move m) {
        return m == Move.OVERTURE || m == Move.PAS_DE_DEUX || m == Move.SOLO;
    }

    /** 飛ぶ解けたリボン（もとは光弾）。進む向きに対して横にしなりながら飛ぶ */
    private static final class Orb {
        Vec3 pos;
        Vec3 vel;
        double phase;
        // ---- 三次元の弾幕（2026-10-24 ユーザー指定「超巨大なモブや空を飛んでいる敵に当たりづらいので、幻想的な三次元の弾幕を」） ----
        /** 追いかける相手（体の真ん中へ、turn の割合ずつ向きを変える）。null なら真っすぐ */
        LivingEntity homing;
        double turn;
        /** 螺旋: 進む道（base）の周りを半径 helixR で回る。0 なら回らない */
        Vec3 base;
        double helixR;
        /** 撃つまでの待ち（Tick）。待つ間は、その場で光って前兆になる */
        int delay;
        /** 地形をすり抜ける（幻想の弾） */
        boolean ghost;
        int life = 80;
        /** 撃った技（当たり具合を技ごとに数える） */
        Move source = Move.NONE;
        /** 狙った相手のティア（帯の色と飾り） */
        int tier;
        /** 落ちる先の床の印（薔薇の雨）。無ければ null */
        Vec3 mark;

        Orb(Vec3 pos, Vec3 vel) {
            this.pos = pos;
            this.vel = vel;
            this.phase = (pos.x * 7.3 + pos.z * 3.1 + vel.x * 11.0) % (Math.PI * 2);
        }

        /** 1 Tick 進める: 追いかける・螺旋・しなり */
        void advance() {
            if (homing != null && homing.isAlive() && life > 50) { // 追うのは途中まで（その後は真っすぐ）
                Vec3 want = bodyCenter(homing).subtract(pos);
                double speed = vel.length();
                if (want.lengthSqr() > 1.0e-6 && speed > 1.0e-6) {
                    Vec3 dir = vel.scale(1.0 / speed).scale(1.0 - turn).add(want.normalize().scale(turn));
                    if (dir.lengthSqr() > 1.0e-6) vel = dir.normalize().scale(speed);
                }
            }
            if (helixR > 0.0) {
                base = (base == null ? pos : base).add(vel);
                Vec3 axis = vel.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : vel.normalize();
                Vec3 u = Math.abs(axis.y) < 0.9 ? axis.cross(new Vec3(0, 1, 0)).normalize() : axis.cross(new Vec3(1, 0, 0)).normalize();
                Vec3 w = axis.cross(u);
                double a = phase + life * 0.35;
                pos = base.add(u.scale(Math.cos(a) * helixR)).add(w.scale(Math.sin(a) * helixR));
                return;
            }
            pos = pos.add(vel).add(homing != null || ghost ? Vec3.ZERO : sway());
        }

        /** しなり: 横へ揺れる量（進む向きは変えない） */
        Vec3 sway() {
            Vec3 side = new Vec3(-vel.z, 0, vel.x);
            if (side.lengthSqr() < 1.0e-6) return Vec3.ZERO;
            return side.normalize().scale(Math.sin(life * 0.45 + phase) * 0.12);
        }
    }

    /** 体の真ん中（大きな相手・飛んでいる相手でも、足もとではなく体を狙う） */
    static Vec3 bodyCenter(LivingEntity e) {
        return e.getBoundingBox().getCenter();
    }

    /** from から相手の体（当たり判定の箱）までの近さ。大きな相手は、真ん中ではなく箱の面までで測る */
    static double gapTo(Vec3 from, LivingEntity e) {
        net.minecraft.world.phys.AABB box = e.getBoundingBox();
        double dx = Math.max(0.0, Math.max(box.minX - from.x, from.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - from.y, from.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - from.z, from.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** エレノアの胸から相手の体までの近さ */
    private double reach(LivingEntity e) {
        return gapTo(elenor.position().add(0, 1.0, 0), e);
    }

    /** 近接の鞭を払う向き（左右交互） */
    private int meleeSide = 1;

    /** 劇場（開幕・幕・結界・閉幕） */
    private final ElenorTheater theater;

    private static final class Sigil {
        final Vec3 at;
        int ticks = 40;
        /** 置いた相手のティア */
        int tier;

        Sigil(Vec3 at) {
            this.at = at;
        }
    }

    private final ElenorEntity elenor;
    private final List<Orb> orbs = new ArrayList<>();
    /** 舞台装置（技が置いていく仕掛け。{@link ElenorStage}） */
    private final List<ElenorStage.Prop> props = new ArrayList<>();
    /** 技の選び方（状況で選ぶ・動きを読む） */
    private final ElenorTactics tactics;
    /** 次の幕の大技を使える時（ゲーム時刻。負ならまだ決めていない） */
    private long nextUltimate = -1L;
    /** 早替わりの柱（[0] が本物）、パ・ド・ドゥの相手の位置と、今の踏み込みの始めと終わり */
    private final List<Vec3> columns = new ArrayList<>();
    private Vec3 partner, passFrom, passTo;
    /** 技を最後に進めた時（ゲーム時刻）。技が進まなくなったら（相手が倒れた・ゴールを外された）体の状態を戻す */
    private long lastMoveTick = Long.MIN_VALUE;
    /**
     * 今の技の見た目のティア（2026-10-24 ユーザー指定「ティアごとに攻撃演出も派手にして」）: 技を始めたときに、向ける相手のうち一番高いティア。
     * 色（{@link ElenorFx#pal}）・大きさ・放つ瞬間の飾り（{@link ElenorFx#tierFlourish}）・構えの気配（{@link ElenorFx#tierAura}）が変わる
     */
    private int fxTier;
    /** 舞台の見た目に使う、戦っている相手のうち一番高いティア（20 Tick ごとに見直す） */
    private int stageTier;
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
        this.tactics = new ElenorTactics(elenor);
    }

    ElenorTactics tactics() {
        return tactics;
    }

    /** 撃つ（今の技の弾として） */
    private Orb shoot(Vec3 from, Vec3 velocity) {
        Orb orb = new Orb(from, velocity);
        orb.source = move;
        orb.tier = fxTier;
        orbs.add(orb);
        return orb;
    }

    public ElenorPressure pressure() {
        return pressure;
    }

    /** 劇場（撃破の演出から幕を引く） */
    public ElenorTheater theater() {
        return theater;
    }

    /** 最初から全開か（最果てのエレノア） */
    public boolean fullPower() {
        return fullPower;
    }

    public int phase() {
        return phase;
    }

    /** GameTest 用: 名前の技をすぐに始める（登場の演出と大技の予定は飛ばす） */
    public void testPerform(String name) {
        introDone = true;
        nextUltimate = -1L;
        start(Move.valueOf(name));
        ringsLeft = move == Move.RING ? 2 : 0;
    }

    /** GameTest 用: 試す技の名前（登場・移り変わり・何もしない以外） */
    public static List<String> testMoveNames() {
        List<String> names = new ArrayList<>();
        for (Move m : Move.values()) {
            if (m != Move.NONE && m != Move.INTRO && m != Move.TRANSITION) names.add(m.name());
        }
        return names;
    }

    /** GameTest 用: 動いている舞台装置の数 */
    public int testPropCount() {
        return props.size();
    }

    /** GameTest 用: 今の技の名前 */
    public String testMoveName() {
        return move.name();
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
        if (level.getGameTime() % 20L == 0L) stageTier = elenor.getTarget() == null ? 0 : topTier(level, elenor.getTarget());
        theater.tick(level, elenor.getTarget() != null, stageTier);
        if (move != Move.NONE && level.getGameTime() - lastMoveTick > 5L) { // 技が進まなくなった: 宙に浮いたまま・柱に隠れたままにしない
            move = Move.NONE;
            mirages.clear();
            columns.clear();
            partner = null;
            resetBody();
        }
        if (elenor.deathTime > 0 && (!orbs.isEmpty() || !sigils.isEmpty())) { // 倒れたら、飛んでいるリボンと魔法陣は花びらにほどけて消える
            for (Orb orb : orbs) ElenorFx.petalBurst(level, orb.pos, 3, 0.1, ElenorFx.LIGHT);
            for (Sigil sigil : sigils) ElenorFx.petals(level, sigil.at.add(0, 0.3, 0), 5, 1.5, ElenorFx.PINK);
            orbs.clear();
            sigils.clear();
            props.clear();
            columns.clear();
            resetBody();
        }
        for (Iterator<ElenorStage.Prop> it = props.iterator(); it.hasNext(); ) {
            if (!it.next().tick(level, this)) it.remove();
        }
        for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
            Orb orb = it.next();
            if (orb.delay > 0) { // 撃つ前: その場で光る（前兆）
                if (orb.delay-- % 3 == 0) com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(
                        com.automatavalkyries.registry.ModParticles.GLOW.get(), ElenorFx.light(orb.tier), 0.35F, 4), orb.pos, Vec3.ZERO);
                continue;
            }
            Vec3 previous = orb.pos;
            orb.advance();
            ElenorFx.ribbonStrand(level, previous, orb.pos, orb.life, orb.tier); // 解けたリボンの帯（狙った相手のティアの色と飾り）
            if (orb.mark != null && orb.life % 4 == 0) ElenorFx.floorRing(level, orb.mark, 0.8, 8, ElenorFx.pal(orb.tier, 2)); // 落ちる先の印
            boolean hit = false;
            for (LivingEntity player : foes(level, new net.minecraft.world.phys.AABB(orb.pos, orb.pos).inflate(0.7))) {
                if (!ElenorPressure.fightable(player)) continue;
                hurtBy(player, 6.0F, orb.source);
                if (isAerial(orb.source)) terminusOn(level, player); // 三次元の弾幕は、ティア 7 の相手には終焉入り（2026-10-24 ユーザー指定）
                hit = true;
                break;
            }
            BlockPos block = BlockPos.containing(orb.pos);
            if (hit || --orb.life <= 0 || !orb.ghost && !level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
                if (!hit) ElenorFx.petalBurst(level, orb.pos, 4, 0.15, ElenorFx.light(orb.tier)); // 当たったときは hurt の着弾の演出
                it.remove();
            }
        }
        for (Iterator<Sigil> it = sigils.iterator(); it.hasNext(); ) {
            Sigil sigil = it.next();
            if (sigil.ticks % 6 == 0) ElenorFx.floorRing(level, sigil.at, 2.5, 14, ElenorFx.pal(sigil.tier, sigil.ticks < 12 ? 5 : 0));
            if (--sigil.ticks > 0) continue;
            // 炸裂: レースの輪と衝撃波、花が開いて花びらが舞い上がる（置いた相手のティアの色と飾り）
            ElenorFx.lace(level, sigil.at, ElenorFx.pal(sigil.tier, 2), 3.2F, 14);
            com.automatavalkyries.weapon.Fx.shockwave(level, sigil.at.add(0, 0.1, 0), ElenorFx.pal(sigil.tier, 0), 3.0F, 10);
            ElenorFx.impact(level, sigil.at.add(0, 0.6, 0), sigil.tier);
            ElenorFx.tierFlourish(level, sigil.at.add(0, 0.6, 0), sigil.tier, 2.5);
            ElenorFx.petalBurst(level, sigil.at.add(0, 0.4, 0), 14, 0.35, ElenorFx.pal(sigil.tier, 0));
            level.playSound(null, sigil.at.x, sigil.at.y, sigil.at.z, SoundEvents.GENERIC_EXPLODE, elenor.getSoundSource(), 0.8F, 1.4F);
            for (LivingEntity player : foes(level, new net.minecraft.world.phys.AABB(sigil.at, sigil.at).inflate(2.5, 2.0, 2.5))) {
                if (!ElenorPressure.fightable(player)) continue;
                hurtBy(player, 9.0F, Move.SIGIL);
                terminusOn(level, player); // 終焉の魔法陣
            }
            it.remove();
        }
    }

    /** 床に光の輪を描く（前兆・魔法陣） */
    private void ring(ServerLevel level, Vec3 center, double radius, int points) {
        ElenorFx.floorRing(level, center, radius, points, ElenorFx.pal(fxTier, 0));
    }

    /**
     * 技が当たる相手: 戦える（クリエイティブ・スペクテイター以外の）プレイヤーと、プレイヤー以外の相手（エレノアが今狙っているもの・
     * 危険なフラグを持つモブ・最近エレノアを攻撃したもの）。モブバトルでも、技が通り、ティアが数えられる
     */
    List<LivingEntity> foes(ServerLevel level, net.minecraft.world.phys.AABB box) {
        List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(Player.class, box, ElenorPressure::fightable));
        LivingEntity aim = elenor.getTarget();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                x -> !(x instanceof Player) && (x == aim || elenor.isHostileCandidate(x) || isEngagedValkyrie(x)))) {
            list.add(e);
        }
        return list;
    }

    /**
     * 戦いに加わっている兵姫（止まっていない）: エレノアを狙っている・最近エレノアを攻撃した、または持ち主が近く（{@link #AUDIENCE_RANGE} 以内）で
     * 戦えるプレイヤー。技に巻き込む（2026-10-24 ユーザー指定「近くの相手にも撃つ」）。近くにいるだけの無関係な兵姫は狙わない
     */
    private boolean isEngagedValkyrie(LivingEntity e) {
        if (!(e instanceof ValkyrieEntity v) || !v.isAlive() || v.isShutdown()) return false;
        if (v.getTarget() == elenor || elenor.getLastHurtByMob() == v) return true;
        java.util.UUID owner = v.getOwnerUUID();
        Player player = owner == null ? null : elenor.level().getPlayerByUUID(owner);
        return player != null && ElenorPressure.fightable(player) && player.distanceToSqr(elenor) <= AUDIENCE_RANGE * AUDIENCE_RANGE;
    }

    /** このMOD の守られた味方（兵姫など）。ティアの攻め方・終焉は向けず、ふつうのダメージだけにする */
    private static boolean isProtectedAlly(LivingEntity e) {
        return e instanceof com.automatavalkyries.protect.IEprotected p && !p.isTerminusTestTarget();
    }

    /**
     * 弾幕の相手: 主な相手と、近く（{@link #AUDIENCE_RANGE} 以内）で戦っている他のプレイヤー・兵姫を合わせて最大 {@link #AUDIENCE_MAX} 人
     * （2026-10-24 ユーザー指定「近くの相手にも撃つ」）
     */
    List<LivingEntity> audience(ServerLevel level, LivingEntity main) {
        List<LivingEntity> list = new ArrayList<>();
        list.add(main);
        net.minecraft.world.phys.AABB box = elenor.getBoundingBox().inflate(AUDIENCE_RANGE, 12.0, AUDIENCE_RANGE);
        List<LivingEntity> others = new ArrayList<>(level.getEntitiesOfClass(Player.class, box, p -> p != main && ElenorPressure.fightable(p)));
        others.addAll(level.getEntitiesOfClass(LivingEntity.class, box, e -> e != main && isEngagedValkyrie(e)));
        others.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(elenor)));
        for (LivingEntity e : others) {
            if (list.size() >= AUDIENCE_MAX) break;
            list.add(e);
        }
        return list;
    }

    static final double AUDIENCE_RANGE = 32.0D;
    static final int AUDIENCE_MAX = 5;

    /** 相手の見た目のティア（兵姫はティアの攻め方を受けないので 0） */
    int tierOf(LivingEntity e) {
        return e == null || isProtectedAlly(e) ? 0 : pressure.tier(e);
    }

    /** 相手たちのうち一番高いティア */
    private int topTier(ServerLevel level, LivingEntity main) {
        int top = 0;
        for (LivingEntity e : audience(level, main)) top = Math.max(top, tierOf(e));
        return top;
    }

    /** 魔法陣を置く（色と飾りは forWhom のティア） */
    private void addSigil(Vec3 at, LivingEntity forWhom) {
        Sigil sigil = new Sigil(at);
        sigil.tier = tierOf(forWhom);
        sigils.add(sigil);
    }

    /** 舞台装置を置く（色と飾りは forWhom のティア。null なら今の技のティア） */
    private void addProp(ElenorStage.Prop prop, LivingEntity forWhom) {
        prop.tier = forWhom == null ? fxTier : tierOf(forWhom);
        props.add(prop);
    }

    /** 技を放った瞬間の飾り（今の技のティア） */
    private void flourish(ServerLevel level, Vec3 at, double radius) {
        ElenorFx.tierFlourish(level, at, fxTier, radius);
    }

    /** 技のダメージを、相手のティアの攻め方で通す（攻撃力・係数を掛ける）。今の技の手柄にする */
    private void hurt(LivingEntity victim, float base) {
        hurtBy(victim, base, move);
    }

    /** 技 source のダメージを通す（舞台装置・飛んでいる弾は、撃った技の手柄にする） */
    void hurtBy(LivingEntity victim, float base, Move source) {
        if (!(elenor.level() instanceof ServerLevel level)) return;
        tactics.hit(source);
        float amount = (float) (base * scale() * pressure.attackScale());
        if (isProtectedAlly(victim)) { // 兵姫: ティア 5〜7 の機構（隔絶・抹殺・終焉）を向けない
            ElenorFx.impact(level, victim.position().add(0, victim.getBbHeight() * 0.55, 0), 0);
            victim.hurt(elenor.damageSources().mobAttack(elenor), amount);
            return;
        }
        ElenorFx.impact(level, victim.position().add(0, victim.getBbHeight() * 0.55, 0), pressure.tier(victim));
        pressure.strike(victim, amount);
    }

    /** 終焉の技（ティア 7 の相手にだけ。最果てのエレノアは全員。兵姫には向けない） */
    private void terminusOn(ServerLevel level, LivingEntity victim) {
        if (isProtectedAlly(victim)) return;
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
            nextUltimate = level.getGameTime() + (fullPower ? 300L : 500L); // 最初の大技は少し戦ってから
            start(Move.INTRO); // 登場の演出（名乗りとお辞儀）
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
        tactics.observe(target);
        elenor.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (meleeCooldown > 0) meleeCooldown--;
        if (move != Move.NONE) {
            runMove(level, target);
            return;
        }
        if (cooldown > 0) cooldown--;
        double distance = reach(target); // 体（当たり判定の箱）までの近さ。大きな相手にも近接が届く
        if (cooldown <= 0 && nextUltimate >= 0 && level.getGameTime() >= nextUltimate) { // 幕の大技
            nextUltimate = level.getGameTime() + (fullPower ? 600L : 900L);
            start(ultimate(target));
            return;
        }
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
        int tier = tierOf(target);
        ElenorFx.ribbonWhip(level, elenor.position(), dir, 3.5, meleeSide, tier);
        if (tier >= 4) ElenorFx.tierFlourish(level, elenor.position().add(dir.multiply(1, 0, 1).normalize().scale(2.0)).add(0, 1.0, 0), tier, 1.2);
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
            if (ElenorPressure.fightable(foe) && reach(foe) <= 3.5) hurt(foe, 12.0F);
        }
    }

    /** フェーズが進んだ: 途中の技をやめて、移行の演出（{@link Move#TRANSITION}）に入る */
    private void enterPhase(ServerLevel level) {
        say(phase == 2 ? "message.automata_valkyries.elenor.phase2" : "message.automata_valkyries.elenor.phase3");
        elenor.setBarPhase(phase);
        level.playSound(null, elenor.blockPosition(), SoundEvents.WITHER_SPAWN, elenor.getSoundSource(), 0.7F, phase == 2 ? 1.4F : 0.9F);
        start(Move.TRANSITION);
        nextUltimate = level.getGameTime() + 200L; // 新しい幕の大技は、移り変わりの 10 秒後に
    }

    private void start(Move next) {
        resetBody();
        columns.clear();
        move = next;
        moveTick = 0;
        lastMoveTick = elenor.level().getGameTime();
        if (elenor.level() instanceof ServerLevel level) {
            LivingEntity aim = elenor.getTarget();
            fxTier = aim == null ? 0 : topTier(level, aim);
            if (next != Move.NONE && next != Move.INTRO && next != Move.TRANSITION) ElenorFx.tierAura(level, elenor.position(), fxTier); // 構えの気配
        }
        if (next != Move.NONE && next != Move.INTRO && next != Move.TRANSITION) tactics.started(next);
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
        int saved = fxTier;
        fxTier = tierOf(target); // 扇はその相手のティアの色で
        try {
            fanAt(level, from, target, count, spread, speed);
        } finally {
            fxTier = saved;
        }
    }

    private void fanAt(ServerLevel level, Vec3 from, LivingEntity target, int count, double spread, double speed) {
        Vec3 aim = tactics.lead(target, from, speed, 0.7).subtract(from).normalize(); // 動きを読んで撃つ
        double base = Math.atan2(aim.z, aim.x);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 1.0F, 1.5F);
        for (int i = 0; i < count; i++) {
            double angle = base + Math.toRadians((i - (count - 1) / 2.0) * spread);
            shoot(from, new Vec3(Math.cos(angle) * speed, aim.y * speed, Math.sin(angle) * speed));
        }
    }

    /** 今の幕で使える技から、状況で点を付けて選ぶ（{@link ElenorTactics}） */
    private void choose(double distance, LivingEntity target) {
        List<Move> pool = new ArrayList<>(List.of(Move.LUNGE, Move.NOVA, Move.RONDO, Move.SPOTLIGHT, Move.FLY_DROP, Move.HELIX));
        if (fullPower || phase >= 2) pool.addAll(List.of(Move.VOLLEY, Move.BLINK, Move.MARIONETTE, Move.TRAPDOOR, Move.QUICK_CHANGE, Move.HOMING));
        if ((fullPower || phase >= 2) && distance <= 14.0D) pool.add(Move.BIND);
        if (fullPower || phase >= 3) pool.addAll(List.of(Move.RING, Move.SIGIL, Move.MIRAGE, Move.MIE, Move.ROSE_RAIN, Move.CHORDS, Move.CELESTIAL));
        boolean finalTier = pressure.tier(target) >= ElenorPressure.MAX_TIER; // ティア 7: 終焉入りの技（縛め・魔法陣・幕引き）を主軸にする
        if (finalTier && (fullPower || phase >= 3)) pool.add(Move.CURTAIN);
        if (distance > 5.0D) pool.remove(Move.NOVA);
        start(tactics.choose(pool, target, finalTier));
        ringsLeft = move == Move.RING ? 2 : 0;
    }

    /**
     * 技が体に残した状態を戻す（落ちた高さを数え直す）。宙に浮く技は重力無しのフラグを使わない: Eprotect が他 MOD の「重力無し」を
     * 保護を始めたときの値へ戻すので、フラグでは浮けない（2026-10-24）。速さを毎 Tick 決めて浮く
     */
    private void resetBody() {
        elenor.resetFallDistance();
    }

    /** 幕の大技: 第一幕は開幕の円舞曲、第二幕はパ・ド・ドゥ、終幕は緋色の独舞。最果ては 3 つを順に（しばらく使っていない物から） */
    private Move ultimate(LivingEntity target) {
        if (!fullPower) return phase == 1 ? Move.OVERTURE : phase == 2 ? Move.PAS_DE_DEUX : Move.SOLO;
        return tactics.choose(List.of(Move.OVERTURE, Move.PAS_DE_DEUX, Move.SOLO), target, false);
    }

    private void finish() {
        tactics.ended(move);
        resetBody();
        move = Move.NONE;
        moveTick = 0;
        int gap = fullPower ? 20 : phase == 1 ? 60 : phase == 2 ? 45 : 32;
        cooldown = Math.max(8, (int) (gap / pressure.tempo(elenor.getTarget())));
    }

    private void runMove(ServerLevel level, LivingEntity target) {
        moveTick++;
        lastMoveTick = level.getGameTime();
        Vec3 eye = elenor.position().add(0, 1.0, 0);
        switch (move) {
            case INTRO -> runIntro(level, target);
            case LUNGE -> {
                if (moveTick == 1) {
                    Vec3 flat = tactics.leadFeet(target, 20, 0.5).subtract(elenor.position()); // 踏み込む頃にいる所へ
                    lungeDir = new Vec3(flat.x, 0, flat.z).normalize();
                    level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, elenor.getSoundSource(), 1.2F, 0.7F);
                }
                if (moveTick <= 18) { // 前兆: 進む先へ光の筋が流れ、行き先の床に輪
                    Vec3 from = elenor.position().add(0, 0.3, 0);
                    if (moveTick % 3 == 0) ElenorFx.streakLine(level, from, from.add(lungeDir.scale(7.0)), 4, ElenorFx.pal(fxTier, 0));
                    if (moveTick % 6 == 0) ring(level, elenor.position().add(lungeDir.scale(7.0)), 1.0, 10);
                } else if (moveTick <= 24) { // 踏み込み: リボンと光の筋の残像
                    if (moveTick == 19) elenor.triggerAnim("action", "lunge");
                    if (moveTick == 19) flourish(level, elenor.position().add(0, 1.0, 0), 1.5); // 踏み出す瞬間
                    elenor.setDeltaMovement(lungeDir.x * 1.2, elenor.getDeltaMovement().y, lungeDir.z * 1.2);
                    elenor.hurtMarked = true;
                    Vec3 back = elenor.position().add(0, 1.0, 0);
                    ElenorFx.streakLine(level, back, back.subtract(lungeDir.scale(2.5)), 3, ElenorFx.light(fxTier));
                    ElenorFx.ribbonArc(level, elenor.position(), lungeDir, 2.6, (moveTick - 18) / 6.0, fxTier); // 解けたリボンの鞭で前を払う
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
                    ElenorFx.lace(level, elenor.position(), ElenorFx.pal(fxTier, 2), 5.0F, 20); // 前兆: 届く範囲にレースの輪
                    elenor.triggerAnim("action", "cast");
                }
                if (moveTick < 20) {
                    if (moveTick % 4 == 0) ring(level, elenor.position(), 5.0, 24);
                    if (moveTick % 3 == 0) com.automatavalkyries.weapon.Fx.gather(level, com.automatavalkyries.weapon.Fx.opt(
                            com.automatavalkyries.registry.ModParticles.PETAL.get(), ElenorFx.pal(fxTier, 0), 0.16F, 10), elenor.position().add(0, 1.0, 0), 6, 4.0, 10);
                } else if (moveTick == 20) {
                    elenor.triggerAnim("action", "skirt_spread");
                    // 炸裂: 衝撃波とレース、花びらの嵐
                    flourish(level, elenor.position().add(0, 1.0, 0), 5.0);
                    com.automatavalkyries.weapon.Fx.shockwave(level, elenor.position().add(0, 0.1, 0), ElenorFx.pal(fxTier, 0), 6.0F, 12);
                    ElenorFx.lace(level, elenor.position(), ElenorFx.light(fxTier), 6.0F, 14);
                    ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 24, 0.6, ElenorFx.pal(fxTier, 0));
                    ElenorFx.ribbonBurst(level, elenor.position(), 12, 0.55, fxTier); // 解けたリボンが四方へ放たれる
                    com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.BLOOM.get(),
                            ElenorFx.pal(fxTier, 2), 2.4F, 14), elenor.position().add(0, 1.0, 0), Vec3.ZERO);
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
                            com.automatavalkyries.registry.ModParticles.GLOW.get(), ElenorFx.pal(fxTier, 0), 0.25F, 8), eye.add(0, 0.4, 0), 3, 1.6, 8);
                    if (moveTick == 2) {
                        com.automatavalkyries.weapon.Fx.mandala(level, eye.add(0, 0.4, 0), ElenorFx.pal(fxTier, 2), 0.8F, 16, true, false);
                        elenor.triggerAnim("action", "point");
                    }
                } else if (moveTick == 16) {
                    com.automatavalkyries.weapon.Fx.star(level, eye.add(0, 0.4, 0), ElenorFx.light(fxTier), 1.2F, 8, Vec3.ZERO);
                    flourish(level, eye.add(0, 0.4, 0), 1.0);
                    int count = phase >= 3 || fullPower ? 7 : 5;
                    List<LivingEntity> crowd = audience(level, target);
                    for (int k = 0; k < crowd.size(); k++) { // 近くで戦っている相手みんなへ（主な相手以外は 1 回り少なく）
                        fan(level, eye.add(0, 0.4, 0), crowd.get(k), k == 0 ? count : count - 2, 12.0, 0.7);
                    }
                } else if (moveTick > 24) {
                    finish();
                }
            }
            case BLINK -> {
                if (moveTick == 1) { // 花びらにほどけて消え、相手の背後で花びらから現れる。間を光の筋が走る
                    elenor.triggerAnim("action", "spin");
                    Vec3 from = elenor.position().add(0, 1.0, 0);
                    ElenorFx.petalBurst(level, from, 18, 0.3, ElenorFx.pal(fxTier, 0));
                    ElenorFx.ribbonSwirl(level, elenor.position(), 0.6, 2.0, 0.0);
                    blinkBehind(level, target);
                    Vec3 to = elenor.position().add(0, 1.0, 0);
                    ElenorFx.streakLine(level, from, to, 8, ElenorFx.light(fxTier));
                    ElenorFx.petalBurst(level, to, 18, 0.3, ElenorFx.pal(fxTier, 0));
                    flourish(level, to, 1.2); // 背後に現れる
                    ElenorFx.ribbonSwirl(level, elenor.position(), 0.6, 2.0, 1.0);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, elenor.getSoundSource(), 1.0F, 1.2F);
                } else if (moveTick >= 12) { // 背後から一撃
                    if (reach(target) <= 3.5) ribbonMelee(level, target); // 背後からリボンの鞭
                    meleeCooldown = 20;
                    finish();
                }
            }
            case RING -> {
                if (moveTick < 25 && ringsLeft == 2) {
                    if (moveTick == 1) {
                        ElenorFx.lace(level, elenor.position(), ElenorFx.pal(fxTier, 0), 4.0F, 24);
                        elenor.triggerAnim("action", "cast");
                    }
                    if (moveTick % 3 == 0) ring(level, elenor.position(), 1.0 + moveTick * 0.12, 20);
                } else if (moveTick == 25 || (moveTick == 45 && ringsLeft == 1)) {
                    double offset = ringsLeft == 1 ? Math.PI / 16.0 : 0.0;
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 1.0F, 0.8F);
                    com.automatavalkyries.weapon.Fx.star(level, eye, ElenorFx.light(fxTier), 1.4F, 8, Vec3.ZERO);
                    flourish(level, eye, 2.0);
                    for (int i = 0; i < 16; i++) {
                        double angle = Math.PI * 2 * i / 16.0 + offset;
                        shoot(eye, new Vec3(Math.cos(angle) * 0.45, 0.0, Math.sin(angle) * 0.45));
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
                    addSigil(tactics.leadFeet(target, 40, 0.5), target);
                    List<LivingEntity> crowd = audience(level, target);
                    for (int k = 1; k < crowd.size(); k++) addSigil(crowd.get(k).position(), crowd.get(k));
                    for (int i = 0; i < 2; i++) {
                        double angle = elenor.getRandom().nextDouble() * Math.PI * 2;
                        addSigil(target.position().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0), target);
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
                    ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 30, 0.7, ElenorFx.pal(fxTier, 0));
                    ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 16, 0.5, ElenorFx.shade(fxTier));
                    ElenorFx.lace(level, elenor.position(), ElenorFx.pal(fxTier, 2), 4.5F, 16);
                    com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.BLOOM.get(),
                            ElenorFx.pal(fxTier, 0), 3.0F, 16), elenor.position().add(0, 1.2, 0), Vec3.ZERO);
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
                    if (moveTick % 3 == 0) ElenorFx.streakLine(level, eye, eye.add(bindDir.scale(14.0)), 5, moveTick > 14 ? ElenorFx.pal(fxTier, 2) : ElenorFx.pal(fxTier, 0)); // 前兆の線
                } else if (moveTick <= 30 && !bindHit) {
                    if (moveTick == 23) level.playSound(null, elenor.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, elenor.getSoundSource(), 1.5F, 0.6F);
                    double reach = (moveTick - 22) * 1.75; // 8 Tick で 14 ブロック
                    for (double d = 0.8; d <= reach; d += 0.8) { // リボンが先へ伸びる（桃と暗い紫が交互）
                        Vec3 p = eye.add(bindDir.scale(d));
                        com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.RIBBON.get(),
                                ((int) (d / 0.8)) % 2 == 0 ? ElenorFx.pal(fxTier, 0) : ElenorFx.shade(fxTier), 0.3F, 10), p, Vec3.ZERO);
                    }
                    Vec3 tip = eye.add(bindDir.scale(reach));
                    for (LivingEntity player : foes(level, new net.minecraft.world.phys.AABB(eye, tip).inflate(1.0))) {
                        if (!ElenorPressure.fightable(player)) continue;
                        Vec3 rel = player.position().add(0, 1.0, 0).subtract(eye);
                        double along = rel.dot(bindDir);
                        if (along < 0 || along > reach || rel.subtract(bindDir.scale(along)).length() > 1.0) continue;
                        bindHit = true;
                        ElenorFx.tierFlourish(level, player.position().add(0, 1.0, 0), tierOf(player), 1.0); // 巻きついた瞬間
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
                    flourish(level, mirages.get(moveTick == 20 ? 0 : 1).add(0, 1.2, 0), 1.0);
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
                if (moveTick % 5 == 0 && moveTick < 85) ElenorFx.petals(level, elenor.position().add(0, 5.0, 0), 8, 7.0, ElenorFx.pal(fxTier, 0)); // 花びらが降りしきる
                if (moveTick >= 10 && moveTick <= 100 && moveTick % 10 == 0) {
                    double offset = moveTick * 0.13;
                    for (int i = 0; i < 12; i++) {
                        double angle = Math.PI * 2 * i / 12.0 + offset;
                        shoot(eye, new Vec3(Math.cos(angle) * 0.4, 0.0, Math.sin(angle) * 0.4));
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
                    ElenorFx.lace(level, elenor.position(), ElenorFx.shade(fxTier), 14.0F, 24);
                    com.automatavalkyries.weapon.Fx.shockwave(level, elenor.position().add(0, 0.1, 0), ElenorFx.pal(fxTier, 5), 14.0F, 18);
                    ElenorFx.impact(level, elenor.position().add(0, 1.2, 0), 7);
                    elenor.triggerAnim("action", "curtsy"); // 幕引きのお辞儀
                    flourish(level, elenor.position().add(0, 1.2, 0), 6.0);
                    for (LivingEntity player : foes(level, elenor.getBoundingBox().inflate(14.0, 6.0, 14.0))) {
                        if (ElenorPressure.fightable(player)) terminusOn(level, player);
                    }
                }
                if (moveTick > 110) {
                    finish();
                    cooldown = fullPower ? 30 : 50;
                }
            }
            // ---- 第一幕の技（2026-10-24） ----
            case RONDO -> runRondo(level, target);
            case SPOTLIGHT -> {
                // 舞台の照明が相手を追う。止まってから光の柱が落ちる（近くで戦っている相手みんなに 1 つずつ。第二幕からは主な相手に 2 つ目）
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "cast");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BEACON_ACTIVATE, elenor.getSoundSource(), 1.2F, 1.8F);
                    for (LivingEntity foe : audience(level, target)) addProp(new ElenorStage.Spotlight(Move.SPOTLIGHT, foe, 30, 10, 2.0, 10.0F), foe);
                }
                if (moveTick == 22 && (fullPower || phase >= 2)) addProp(new ElenorStage.Spotlight(Move.SPOTLIGHT, target, 24, 8, 1.6, 10.0F), target);
                if (moveTick > 24) finish();
            }
            case FLY_DROP -> {
                // 吊り物落とし: 相手が落ちる頃にいる所と、主な相手の周りに 2 つ。床の影が濃くなってから落ちる
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "point");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.CHAIN_BREAK, elenor.getSoundSource(), 1.5F, 0.7F);
                    for (LivingEntity foe : audience(level, target)) addProp(new ElenorStage.FlyDrop(Move.FLY_DROP, tactics.leadFeet(foe, 30, 0.8), 30, 2.2, 12.0F), foe);
                    for (int i = 0; i < 2; i++) {
                        double angle = elenor.getRandom().nextDouble() * Math.PI * 2, r = 3.0 + elenor.getRandom().nextDouble() * 2.0;
                        addProp(new ElenorStage.FlyDrop(Move.FLY_DROP, target.position().add(Math.cos(angle) * r, 0, Math.sin(angle) * r), 38 + i * 8, 2.2, 12.0F), target);
                    }
                }
                if (moveTick > 18) finish();
            }
            case OVERTURE -> {
                // 開幕の円舞曲（第一幕の大技）: エレノアから伸びるリボンの輻が舞台を回り、途中で逆に回る。その間、輻の隙間へ小さな扇を撃つ
                elenor.setDeltaMovement(0, elenor.getDeltaMovement().y, 0);
                if (moveTick == 1) {
                    sayUltimate("overture");
                    flourish(level, elenor.position().add(0, 1.0, 0), 4.0);
                    elenor.triggerAnim("action", "curtsy");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), elenor.getSoundSource(), 2.0F, 0.8F);
                    ElenorFx.lace(level, elenor.position(), ElenorFx.pal(fxTier, 0), 15.0F, 30);
                    addProp(new ElenorStage.Spokes(Move.OVERTURE, elenor, fullPower ? 8 : 6, 15.0, elenor.getRandom().nextDouble() * Math.PI, 0.035, 20, 110), null);
                }
                if (moveTick % 6 == 0) ribbonSwirl(level, elenor.position(), 1.0, 2.2, moveTick * 0.3);
                if (moveTick % 12 == 0) level.playSound(null, elenor.blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(), elenor.getSoundSource(), 1.2F,
                        0.8F + 0.2F * ((moveTick / 12) % 3)); // 三拍子
                if (moveTick == 40 || moveTick == 70 || moveTick == 100) {
                    elenor.triggerAnim("action", "spin");
                    for (LivingEntity foe : audience(level, target)) fan(level, eye, foe, 3, 20.0, 0.55);
                }
                if (moveTick > 112) {
                    finish();
                    cooldown = 40;
                }
            }
            // ---- 第二幕の技 ----
            case MARIONETTE -> {
                // 操り糸の檻: 近くの相手 3 人まで、周りに 6 本の糸が降りて縮む。エレノアは指先から糸を操る
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "cast");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, elenor.getSoundSource(), 1.5F, 1.2F);
                    List<LivingEntity> crowd = audience(level, target);
                    for (int k = 0; k < Math.min(3, crowd.size()); k++) {
                        addProp(new ElenorStage.StringCage(Move.MARIONETTE, crowd.get(k).position(), elenor.getRandom().nextDouble() * Math.PI * 2, 5.0, 14, 40), crowd.get(k));
                    }
                }
                if (moveTick % 4 == 0) { // 指先から上へ伸びる糸
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 hand = elenor.position().add(new Vec3(0.45 * side, 1.5, 0).yRot((float) Math.toRadians(-elenor.yBodyRot)));
                        com.automatavalkyries.weapon.Fx.line(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(),
                                ElenorFx.light(fxTier), 0.1F, 4), hand, hand.add(0, 5.0, 0), 0.8);
                    }
                }
                if (moveTick > 30) finish();
            }
            case TRAPDOOR -> {
                // 奈落の迫り: 相手の足もとの床が市松に光り、光った枡が開く。続けて残りの枡も開く（互い違い）
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "skirt_spread");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_BLOCK, elenor.getSoundSource(), 1.5F, 0.5F);
                    for (LivingEntity foe : audience(level, target)) {
                        if (foe != target && foe.distanceToSqr(target) < 36.0) continue; // 主な相手の床と重なる相手には足さない
                        addProp(new ElenorStage.Trapdoor(Move.TRAPDOOR, foe.position(), 0, 24), foe);
                        addProp(new ElenorStage.Trapdoor(Move.TRAPDOOR, foe.position(), 1, 46), foe);
                    }
                }
                if (moveTick > 20) finish();
            }
            case QUICK_CHANGE -> runQuickChange(level, target);
            case PAS_DE_DEUX -> runPasDeDeux(level, target);
            // ---- 終幕の技 ----
            case MIE -> {
                // 大見得斬り: 見得を切り、3 方向の扇（120° ずつ）を順に巨大なリボンで薙ぐ。最初は相手の方から
                elenor.setDeltaMovement(0, elenor.getDeltaMovement().y, 0);
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "mie");
                    flourish(level, elenor.position().add(0, 1.0, 0), 3.0);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.RAVAGER_ROAR, elenor.getSoundSource(), 0.8F, 1.8F);
                    com.automatavalkyries.weapon.Fx.mandala(level, elenor.position().add(0, 0.05, 0), ElenorFx.pal(fxTier, 5), 2.0F, 50, false, true);
                    Vec3 to = target.position().subtract(elenor.position());
                    double a0 = Math.atan2(to.z, to.x);
                    int sweeps = fullPower ? 4 : 3;
                    for (int i = 0; i < sweeps; i++) {
                        addProp(new ElenorStage.Sector(Move.MIE, elenor.position(), a0 + Math.toRadians(120.0 * i), 12.0, 18 + 12 * i, 12.0F), null);
                    }
                }
                if (moveTick == 18 || moveTick == 30 || moveTick == 42) elenor.triggerAnim("action", "point");
                if (moveTick > 46) finish();
            }
            case ROSE_RAIN -> {
                // 薔薇の雨: 舞台の天井から、相手が着く頃にいる所へリボンの花が降る。落ちる先の床に印
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "cast");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, elenor.getSoundSource(), 1.5F, 1.4F);
                }
                if (moveTick >= 4 && moveTick <= 52 && moveTick % 3 == 0) {
                    List<LivingEntity> crowd = audience(level, target);
                    for (int k = 0; k < crowd.size(); k++) {
                        for (int n = 0; n < (k == 0 ? 2 : 1); n++) {
                            Vec3 land = tactics.leadFeet(crowd.get(k), 24, 0.8);
                            if (n > 0 || moveTick % 9 != 0) { // 3 回に 1 回は読んだ所へまっすぐ、他はまわりに散らす
                                double angle = elenor.getRandom().nextDouble() * Math.PI * 2, r = elenor.getRandom().nextDouble() * 2.5;
                                land = land.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                            }
                            Orb drop = shoot(land.add(0, 12.0, 0), new Vec3(0, -0.5, 0));
                            drop.life = 30;
                            drop.mark = land;
                        }
                    }
                    if (moveTick % 6 == 0) ElenorFx.petals(level, target.position().add(0, 10.0, 0), 4, 3.0, ElenorFx.pal(fxTier, 2));
                }
                if (moveTick > 56) finish();
            }
            case CHORDS -> {
                // 千秋楽の綱: 舞台を横切る低い綱を張り、ゆっくり回す（跳べば越えられる）。その間に扇を撃つ
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "point");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, elenor.getSoundSource(), 2.0F, 0.6F);
                    Vec3 c = theater.center() != null && theater.center().distanceTo(target.position()) < ElenorTheater.RADIUS
                            ? theater.center() : elenor.position();
                    int count = fullPower ? 4 : 3;
                    double base = elenor.getRandom().nextDouble() * Math.PI;
                    for (int i = 0; i < count; i++) {
                        double offset = (i - (count - 1) / 2.0) * 4.5;
                        addProp(new ElenorStage.Chord(Move.CHORDS, new Vec3(c.x, target.getY(), c.z), 15.0, base + i * 0.4, offset, (i % 2 == 0 ? 1 : -1) * 0.006, 20, 70), null);
                    }
                }
                if (moveTick == 30 || moveTick == 50) fan(level, eye, target, 5, 12.0, 0.6);
                if (moveTick > 56) finish();
            }
            case SOLO -> runSolo(level, target);
            // ---- 三次元の弾幕（2026-10-24 ユーザー指定「超巨大なモブや空を飛んでいる敵に当たりづらいので、幻想的な三次元の弾幕を」）。どれも地形をすり抜け、体の真ん中を狙う ----
            case HELIX -> {
                // 螺旋の昇天（第一幕〜）: 足もとから二重の螺旋が昇り、二重螺旋のリボンが、相手が着く頃にいる所（体の真ん中）へ昇っていく
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "cast");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, elenor.getSoundSource(), 1.5F, 1.2F);
                }
                if (moveTick < 12 && moveTick % 2 == 0) ribbonSwirl(level, elenor.position(), 1.0, 0.5 + moveTick * 0.25, moveTick * 0.5);
                if (moveTick == 12) flourish(level, eye, 1.5);
                if (moveTick >= 12 && moveTick <= 36 && moveTick % 3 == 0) {
                    List<LivingEntity> crowd = audience(level, target);
                    for (int k = 0; k < crowd.size(); k++) {
                        LivingEntity foe = crowd.get(k);
                        Vec3 aim = k == 0 ? tactics.lead(foe, eye, 0.55, 0.6) : bodyCenter(foe);
                        Vec3 dir = aim.subtract(eye);
                        if (dir.lengthSqr() < 1.0e-4) continue;
                        dir = dir.normalize().scale(0.55);
                        int saved = fxTier;
                        fxTier = tierOf(foe);
                        for (int strand = 0; strand < 2; strand++) {
                            Orb orb = shoot(eye, dir);
                            orb.helixR = 0.9;
                            orb.phase += strand * Math.PI;
                            orb.ghost = true;
                            orb.life = 70;
                        }
                        fxTier = saved;
                    }
                    if (moveTick % 6 == 0) level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_CLUSTER_STEP, elenor.getSoundSource(), 1.2F, 1.6F);
                }
                if (moveTick > 40) finish();
            }
            case HOMING -> {
                // 星蝶の追尾（第二幕〜）: 噴水のように上へ放たれたリボンの蝶が、順に向きを変えて相手の体を追う（追うのは途中まで。向きの変え方に限りがあり、横へ切れば避けられる）
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "spin");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, elenor.getSoundSource(), 1.5F, 1.4F);
                    flourish(level, eye.add(0, 0.6, 0), 1.5);
                }
                if (moveTick == 6) {
                    List<LivingEntity> crowd = audience(level, target);
                    for (int k = 0; k < crowd.size(); k++) {
                        LivingEntity foe = crowd.get(k);
                        int count = k == 0 ? 10 : 5;
                        int saved = fxTier;
                        fxTier = tierOf(foe);
                        for (int i = 0; i < count; i++) {
                            double angle = Math.PI * 2 * i / count + k;
                            Orb orb = shoot(eye.add(0, 0.6, 0), new Vec3(Math.cos(angle) * 0.22, 0.42, Math.sin(angle) * 0.22));
                            orb.homing = foe;
                            orb.turn = 0.1;
                            orb.delay = i * 2;
                            orb.ghost = true;
                            orb.life = 100;
                        }
                        fxTier = saved;
                    }
                }
                if (moveTick > 30) finish();
            }
            case CELESTIAL -> {
                // 星環の天球（終幕〜）: 相手の体を包む光の球（大きな相手ほど大きい）に星が並び、18 Tick 光ってから一斉に真ん中へ降り注ぐ。球には 5 か所の隙間がある
                if (moveTick == 1) {
                    elenor.triggerAnim("action", "cast");
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BEACON_ACTIVATE, elenor.getSoundSource(), 1.5F, 1.6F);
                    for (LivingEntity foe : audience(level, target)) {
                        Vec3 center = bodyCenter(foe);
                        double radius = Math.max(7.0, Math.max(foe.getBbWidth(), foe.getBbHeight()) * 0.8 + 5.0);
                        int n = 30;
                        java.util.Set<Integer> gaps = new java.util.HashSet<>();
                        while (gaps.size() < 5) gaps.add(elenor.getRandom().nextInt(n));
                        int saved = fxTier;
                        fxTier = tierOf(foe);
                        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
                        for (int i = 0; i < n; i++) {
                            if (gaps.contains(i)) continue;
                            double y = 1.0 - 2.0 * (i + 0.5) / n, r = Math.sqrt(1.0 - y * y), th = i * golden;
                            Vec3 at = center.add(Math.cos(th) * r * radius, y * radius, Math.sin(th) * r * radius);
                            Orb orb = shoot(at, center.subtract(at).normalize().scale(radius / 22.0));
                            orb.delay = 18;
                            orb.ghost = true;
                            orb.life = 30;
                        }
                        ElenorFx.lace(level, center, ElenorFx.pal(fxTier, 2), (float) radius, 20); // 球の赤道に光の輪
                        com.automatavalkyries.weapon.Fx.mandala(level, center, ElenorFx.pal(fxTier, 0), 1.2F, 24, true, false);
                        fxTier = saved;
                    }
                }
                if (moveTick == 18) {
                    level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, elenor.getSoundSource(), 1.8F, 0.7F);
                    flourish(level, bodyCenter(target), 2.0);
                }
                if (moveTick > 30) finish();
            }
            default -> finish();
        }
    }

    // ---- 新しい技の中身（2026-10-24 ユーザー指定「技もほかボスと同じようにこだわって作る」） ----

    /** 輪舞の連撃: 3 拍。右から払う → 左から払う → くるりと回って周りを払う。拍の前に、払う範囲が床に光る。拍ごとに半歩踏み込む */
    private void runRondo(ServerLevel level, LivingEntity target) {
        int[] beats = {12, 22, 36};
        Vec3 flat = target.position().subtract(elenor.position()).multiply(1, 0, 1);
        Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        for (int b = 0; b < beats.length; b++) {
            if (moveTick == beats[b] - 8) {
                ElenorFx.floorArc(level, elenor.position(), dir, 4.0, b == 2, b == 2 ? ElenorFx.pal(fxTier, 2) : ElenorFx.pal(fxTier, 0));
                level.playSound(null, elenor.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), elenor.getSoundSource(), 1.0F, 1.2F + 0.2F * b);
            }
            if (moveTick != beats[b]) continue;
            elenor.setDeltaMovement(dir.x * 0.5, elenor.getDeltaMovement().y, dir.z * 0.5);
            elenor.hurtMarked = true;
            flourish(level, elenor.position().add(dir.scale(2.0)).add(0, 1.0, 0), b == 2 ? 2.5 : 1.2); // 拍ごと
            level.playSound(null, elenor.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, elenor.getSoundSource(), 1.2F, 1.1F + 0.15F * b);
            if (b < 2) {
                elenor.triggerAnim("action", "point");
                ElenorFx.ribbonWhip(level, elenor.position(), dir, 4.0, b == 0 ? 1 : -1, fxTier);
                sweep(level, dir, 4.0, false, 8.0F);
            } else {
                elenor.triggerAnim("action", "spin");
                ElenorFx.ribbonWhip(level, elenor.position(), dir, 4.2, 1, fxTier);
                ElenorFx.ribbonWhip(level, elenor.position(), dir.scale(-1), 4.2, 1, fxTier);
                ElenorFx.ribbonBurst(level, elenor.position(), 8, 0.35, fxTier);
                ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 14, 0.4, ElenorFx.pal(fxTier, 0));
                sweep(level, dir, 4.2, true, 10.0F);
            }
        }
        if (moveTick > 44) finish();
    }

    /** エレノアの前の半円（full なら周り全部）の radius 以内を払う。外へ押し出す */
    private void sweep(ServerLevel level, Vec3 dir, double radius, boolean full, float base) {
        for (LivingEntity foe : foes(level, elenor.getBoundingBox().inflate(radius, 1.2, radius))) {
            Vec3 rel = foe.position().subtract(elenor.position());
            if (!full && rel.x * dir.x + rel.z * dir.z < 0.0) continue;
            if (!ElenorPressure.fightable(foe) || reach(foe) > radius) continue;
            hurt(foe, base);
            Vec3 away = rel.multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0e-4) away = away.normalize();
            foe.push(away.x * 0.5, 0.2, away.z * 0.5);
            foe.hurtMarked = true;
        }
    }

    /**
     * 早替わり: 相手の周りに緞帳の柱が 3 本立ち、エレノアはそのどれかに隠れる。本物の柱の足もとには桃色の花びら、偽物には暗い花びら（見分ける手がかり）。
     * 28 Tick で柱が落ち、本物から相手へ踏み込み（リボンの鞭）、偽物からはリボンの扇が飛ぶ
     */
    private void runQuickChange(ServerLevel level, LivingEntity target) {
        if (moveTick == 1) {
            double base = elenor.getRandom().nextDouble() * Math.PI * 2;
            for (int i = 0; i < 3; i++) {
                double a = base + Math.PI * 2 * i / 3;
                columns.add(target.position().add(Math.cos(a) * 6.0, 0, Math.sin(a) * 6.0));
            }
            Vec3 from = elenor.position();
            int pick = elenor.getRandom().nextInt(3);
            Vec3 real = columns.get(pick);
            if (level.noCollision(elenor, elenor.getBoundingBox().move(real.subtract(from)))) {
                elenor.teleportTo(real.x, real.y, real.z);
            } else {
                columns.set(pick, from); // 置けなければ、その場の柱が本物
            }
            elenor.triggerAnim("action", "spin");
            ElenorFx.petalBurst(level, from.add(0, 1.0, 0), 18, 0.3, ElenorFx.pal(fxTier, 0));
            level.playSound(null, elenor.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, elenor.getSoundSource(), 1.5F, 1.2F);
        }
        if (columns.isEmpty()) {
            finish();
            return;
        }
        elenor.setDeltaMovement(0, elenor.getDeltaMovement().y, 0);
        int real = realColumn();
        if (moveTick < 28 && moveTick % 2 == 0) {
            for (int i = 0; i < columns.size(); i++) {
                Vec3 c = columns.get(i);
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI * 2 * k / 6 + moveTick * 0.05;
                    Vec3 p = c.add(Math.cos(a) * 0.9, 1.2 + (k % 2) * 0.8, Math.sin(a) * 0.9);
                    com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.CURTAIN.get(),
                            k % 2 == 0 ? ElenorFx.shade(fxTier) : ElenorFx.pal(fxTier, 2), 1.5F, 4), p, Vec3.ZERO);
                }
                if (moveTick % 6 == 0) ElenorFx.petals(level, c.add(0, 0.2, 0), 2, 0.4, i == real ? ElenorFx.pal(fxTier, 0) : ElenorFx.shade(fxTier));
            }
        }
        if (moveTick == 28) {
            level.playSound(null, elenor.blockPosition(), SoundEvents.WOOL_BREAK, elenor.getSoundSource(), 2.0F, 0.6F);
            for (int i = 0; i < columns.size(); i++) {
                Vec3 c = columns.get(i);
                ElenorFx.curtainCircle(level, c.add(0, -2.5, 0), 0.9, 6, 0.0);
                if (i != real) fan(level, c.add(0, 1.2, 0), target, 5, 14.0, 0.6);
            }
            Vec3 to = tactics.leadFeet(target, 6, 0.5).subtract(elenor.position()).multiply(1, 0, 1);
            lungeDir = to.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : to.normalize();
            elenor.triggerAnim("action", "lunge");
        }
        if (moveTick > 28 && moveTick <= 34) {
            if (moveTick == 29) flourish(level, elenor.position().add(0, 1.0, 0), 1.5); // 柱から飛び出す
            elenor.setDeltaMovement(lungeDir.x * 1.3, elenor.getDeltaMovement().y, lungeDir.z * 1.3);
            elenor.hurtMarked = true;
            ElenorFx.ribbonArc(level, elenor.position(), lungeDir, 2.6, (moveTick - 28) / 6.0, fxTier);
            for (LivingEntity foe : foes(level, elenor.getBoundingBox().inflate(2.6, 0.8, 2.6))) {
                Vec3 rel = foe.position().subtract(elenor.position());
                if (rel.x * lungeDir.x + rel.z * lungeDir.z < -0.5) continue;
                if (ElenorPressure.fightable(foe) && foe.invulnerableTime <= 10) hurt(foe, 12.0F);
            }
        }
        if (moveTick > 38) {
            columns.clear();
            finish();
        }
    }

    /** 早替わりの本物の柱（エレノアに一番近い柱） */
    private int realColumn() {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < columns.size(); i++) {
            double d = columns.get(i).distanceToSqr(elenor.position());
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * パ・ド・ドゥ（第二幕の大技）: 相手を挟んだ反対側に、もう一人のエレノア（残像）が立つ。二人が交互に、相手を貫く線を踏み込んで交差する（4 回）。
     * 踏み込む線は 12 Tick 前から床と宙に光る
     */
    private void runPasDeDeux(ServerLevel level, LivingEntity target) {
        if (moveTick == 1) {
            sayUltimate("pas_de_deux");
            flourish(level, elenor.position().add(0, 1.0, 0), 2.0);
            Vec3 across = target.position().subtract(elenor.position()).multiply(1, 0, 1);
            across = across.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : across.normalize();
            partner = target.position().add(across.scale(7.0));
            level.playSound(null, elenor.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, elenor.getSoundSource(), 1.5F, 1.0F);
            ElenorFx.petalBurst(level, partner.add(0, 1.0, 0), 20, 0.3, ElenorFx.VIOLET);
            elenor.triggerAnim("action", "curtsy");
        }
        if (partner == null) {
            finish();
            return;
        }
        if (moveTick % 2 == 0) mirage(level, partner, moveTick);
        int pass = (moveTick - 10) / 22, local = (moveTick - 10) % 22;
        if (moveTick >= 10 && pass < 4) {
            boolean self = pass % 2 == 0;
            Vec3 dancer = self ? elenor.position() : partner;
            if (local == 0) { // 踏み込む線を決める: 相手が着く頃にいる所を貫いて、その先 6 ブロックまで
                passFrom = dancer;
                Vec3 aim = tactics.leadFeet(target, 14, 0.6);
                Vec3 line = aim.subtract(dancer).multiply(1, 0, 1);
                double len = line.length();
                passTo = len < 1.0e-3 ? dancer : dancer.add(line.scale((len + 6.0) / len));
                level.playSound(null, BlockPos.containing(dancer), SoundEvents.AMETHYST_BLOCK_CHIME, elenor.getSoundSource(), 1.5F, 0.8F + 0.15F * pass);
            }
            if (local < 12 && local % 2 == 0 && passFrom != null) { // 前兆の線
                ElenorFx.streakLine(level, passFrom.add(0, 1.0, 0), passTo.add(0, 1.0, 0), 6, local > 6 ? ElenorFx.pal(fxTier, 5) : ElenorFx.pal(fxTier, 2));
                com.automatavalkyries.weapon.Fx.line(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(),
                        ElenorFx.pal(fxTier, 0), 0.14F, 4), passFrom.add(0, 0.08, 0), passTo.add(0, 0.08, 0), 1.0);
            }
            if (local >= 12 && local < 18 && passFrom != null) { // 踏み込み（6 Tick で線を渡る）
                Vec3 prev = passFrom.add(passTo.subtract(passFrom).scale((local - 12) / 6.0));
                Vec3 next = passFrom.add(passTo.subtract(passFrom).scale((local - 11) / 6.0));
                if (local == 12) {
                    level.playSound(null, BlockPos.containing(prev), SoundEvents.PLAYER_ATTACK_SWEEP, elenor.getSoundSource(), 1.5F, 0.9F);
                    flourish(level, prev.add(0, 1.0, 0), 1.5);
                    if (self) elenor.triggerAnim("action", "lunge");
                }
                if (self) {
                    if (level.noCollision(elenor, elenor.getBoundingBox().move(next.subtract(elenor.position())))) elenor.teleportTo(next.x, elenor.getY(), next.z);
                } else {
                    partner = next;
                }
                ElenorFx.ribbonStrand(level, prev.add(0, 1.0, 0), next.add(0, 1.0, 0), local, fxTier);
                ElenorFx.streakLine(level, prev.add(0, 1.0, 0), next.add(0, 1.0, 0), 3, ElenorFx.light(fxTier));
                for (LivingEntity foe : foes(level, new net.minecraft.world.phys.AABB(prev, next).inflate(1.4, 2.0, 1.4))) {
                    if (!ElenorPressure.fightable(foe) || foe.invulnerableTime > 10) continue;
                    if (ElenorStage.flatDistance(foe.position(), prev, next) <= 1.3 && Math.abs(foe.getY() - prev.y) < 2.5) hurt(foe, 10.0F);
                }
            }
        }
        if (moveTick == 10 + 4 * 22) {
            elenor.triggerAnim("action", "curtsy");
            ElenorFx.petalBurst(level, partner.add(0, 1.0, 0), 20, 0.3, ElenorFx.VIOLET); // もう一人は花びらにほどけて消える
            level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_RESONATE, elenor.getSoundSource(), 1.5F, 1.3F);
        }
        if (moveTick > 10 + 4 * 22 + 8) {
            partner = null;
            finish();
            cooldown = 40;
        }
    }

    /**
     * 緋色の独舞（終幕の大技）: 宙へ浮き上がり、上から照らされて舞う。逆回りの 2 本の螺旋のリボンを撒き、30 Tick ごとに相手みんなへスポットライト。
     * 最後に降りてお辞儀
     */
    private void runSolo(ServerLevel level, LivingEntity target) {
        Vec3 eye = elenor.position().add(0, 1.0, 0);
        if (moveTick == 1) {
            sayUltimate("solo");
            flourish(level, elenor.position().add(0, 1.0, 0), 3.0);
            elenor.triggerAnim("action", "solo");
            level.playSound(null, elenor.blockPosition(), SoundEvents.BEACON_ACTIVATE, elenor.getSoundSource(), 2.0F, 0.6F);
            ElenorFx.lace(level, elenor.position(), ElenorFx.pal(fxTier, 5), 8.0F, 30);
        }
        // 浮く: ゴールの Tick は移動の前に来るので、上下の速さを毎 Tick 決めれば高さを保てる（12 Tick で 3 ブロック昇り、そこで止まる）
        if (moveTick <= 12) {
            elenor.setDeltaMovement(0, 0.25, 0);
        } else if (moveTick <= 112) {
            elenor.setDeltaMovement(0, 0, 0);
        }
        elenor.resetFallDistance();
        elenor.hurtMarked = true;
        if (moveTick % 4 == 0 && moveTick <= 112) { // 照らす光
            com.automatavalkyries.weapon.Fx.spray(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(),
                    ElenorFx.light(fxTier), 0.5F, 10), elenor.position().add(0, 4.0, 0), 3, 0.3, 1.5, 0.3, 0.0);
        }
        if (moveTick > 12 && moveTick <= 108 && moveTick % 5 == 0) {
            boolean clockwise = (moveTick / 5) % 2 == 0;
            double spin = moveTick * 0.09 * (clockwise ? 1 : -1);
            for (int i = 0; i < 5; i++) {
                double a = spin + Math.PI * 2 * i / 5;
                shoot(eye, new Vec3(Math.cos(a) * 0.38, -0.04, Math.sin(a) * 0.38)).life = 50;
            }
            if (moveTick % 10 == 0) level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 0.6F, 1.6F);
            if (moveTick % 15 == 0) ribbonSwirl(level, elenor.position(), 1.4, 2.4, moveTick * 0.3);
        }
        if (moveTick == 20 || moveTick == 50 || moveTick == 80) {
            elenor.triggerAnim("action", "spin");
            flourish(level, eye, 2.0);
            for (LivingEntity foe : audience(level, target)) addProp(new ElenorStage.Spotlight(Move.SOLO, foe, 18, 8, 1.8, 10.0F), foe);
        }
        if (moveTick == 113) { // 降りてお辞儀
            elenor.triggerAnim("action", "curtsy");
            ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 24, 0.45, ElenorFx.pal(fxTier, 5));
        }
        if (moveTick > 124) {
            finish();
            cooldown = 40;
        }
    }

    /** 幕の大技の名乗り（最果ては自分のセリフ） */
    private void sayUltimate(String name) {
        say((fullPower ? "message.automata_valkyries.farthest_elenor.ult." : "message.automata_valkyries.elenor.ult.") + name);
    }

    /** 登場の演出の長さ（Tick） */
    static final int INTRO_TICKS = 50;

    /**
     * 登場の演出（2026-10-24 ユーザー指定「開幕の登場演出」）: 緞帳が上がる中、上からの光に照らされて立ち、名乗ってお辞儀をする。
     * その間は技を撃たない（攻撃は通る）。リボンが足もとから渦を巻いて昇り、花びらが集まり、最後にスカートを広げて始まる。
     * 最果ては、白い光と虚空の欠片、黒と白のリボンで同じ流れ
     */
    private void runIntro(ServerLevel level, LivingEntity target) {
        elenor.setDeltaMovement(0, elenor.getDeltaMovement().y, 0);
        Vec3 at = elenor.position();
        int main = fullPower ? ElenorTheater.VOID_WHITE : ElenorFx.pal(fxTier, 0);
        if (moveTick < 30) {
            if (moveTick % 2 == 0) { // 上から細い光の柱
                com.automatavalkyries.weapon.Fx.spray(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(),
                        fullPower ? ElenorTheater.VOID_WHITE : ElenorFx.light(fxTier), 0.45F, 12), at.add(0, 4.0, 0), 3, 0.3, 2.5, 0.3, 0.0);
            }
            if (moveTick % 3 == 0) ribbonSwirl(level, at, 1.3, 0.6 + moveTick * 0.08, moveTick * 0.3); // 足もとからリボンが昇る
            if (moveTick % 4 == 0) com.automatavalkyries.weapon.Fx.gather(level, com.automatavalkyries.weapon.Fx.opt(
                    com.automatavalkyries.registry.ModParticles.PETAL.get(), main, 0.16F, 12), at.add(0, 1.0, 0), 6, 3.5, 12);
            if (fullPower && moveTick % 5 == 0) com.automatavalkyries.weapon.Fx.burst(level, com.automatavalkyries.weapon.Fx.opt(
                    com.automatavalkyries.registry.ModParticles.VOID_SHARD.get(), ElenorTheater.VOID_BLACK, 0.3F, 20), at.add(0, 1.2, 0), 4, 0.15);
        }
        if (moveTick == 10) {
            String key = fullPower ? "message.automata_valkyries.farthest_elenor.intro"
                    : phase == 1 ? "message.automata_valkyries.elenor.intro" : "message.automata_valkyries.elenor.intro_again";
            say(key);
            level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, elenor.getSoundSource(), 1.5F, fullPower ? 0.6F : 1.1F);
        }
        if (moveTick == 26) {
            elenor.triggerAnim("action", "curtsy");
            ElenorFx.lace(level, at, main, 3.0F, 20);
            com.automatavalkyries.weapon.Fx.one(level, com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.BLOOM.get(),
                    fullPower ? ElenorTheater.VOID_WHITE : ElenorFx.pal(fxTier, 2), 1.8F, 16), at.add(0, 1.0, 0), Vec3.ZERO);
        }
        if (moveTick == 44) {
            elenor.triggerAnim("action", "skirt_spread");
            ElenorFx.petalBurst(level, at.add(0, 1.0, 0), 20, 0.5, main);
            if (fullPower) com.automatavalkyries.weapon.Fx.mandala(level, at.add(0, 0.05, 0), ElenorTheater.VOID_BLACK, 3.0F, 30, false, true);
            level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, elenor.getSoundSource(), 1.5F, fullPower ? 0.5F : 1.0F);
        }
        if (moveTick >= INTRO_TICKS) {
            finish();
            cooldown = 20;
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
            return elenor.arts() != null && target != null && elenor.isReachable(target) && !elenor.flight().isFlying() // 足場へ飛んで戻る間は技を止める
                    && ElenorPressure.living(target);
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
                arts.columns.clear();
                arts.resetBody();
            }
        }
    }
}
