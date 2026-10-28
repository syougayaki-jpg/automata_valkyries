package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * メメントモリ EX（2026-10-27 ユーザー指定「他二体の AEC 級ボスも EX を追加する」）。回想のトライアル「死蝶の残響」のボス。
 * HP {@value #MAX_HP_EX}（元の 2 倍）、技のダメージ ×1.5、3 形態（66% で第 2 形態、33% で第 3 形態＝もう一度昇華する）。見た目は白と金の光体の色違い。
 * <p>
 * 第 3 形態（数値は【仮】）:
 * <ul>
 *   <li>呪いの深化（ユーザー指定）: 死蝶の呪いが移動速度も削り、最大 HP は 25% まで（{@link MementoCurse#add(LivingEntity, int, net.minecraft.world.entity.Entity)}）</li>
 *   <li>『死蝶終葬・真』（ユーザー指定）: 終葬に第四楽章「黄泉の月蝕」（安全地帯が動く黒死の月を 3 つ続けて。{@link MementoFinale}）を足す</li>
 * </ul>
 * 第 3 形態の技（2026-10-27 ユーザー指定「ルクシオン EX のように 6 個ほど」）は {@link ExGoal} が、いつもの技の手が空いた時に割り込んで出す:
 * <ul>
 *   <li>鏡像の死蝶: 相手を挟んだ反対側に、見た目だけの鏡像が現れ、同じ蝶弾の扇と輪を向かい合わせに撃つ</li>
 *   <li>逆さの弾幕: 胸から広がった輪が途中で消え、少しして逆再生のように戻ってくる（3 波）</li>
 *   <li>蝶の鎖: 弾でできた 2 段の鎖の壁が 4 方向から順に迫る。どの壁にも隙間が 1 か所</li>
 *   <li>螺旋階段: 相手のまわりから 4 本の腕の渦巻きが内へ巻き込む</li>
 *   <li>月光の槍: 空から予告線の後に大玉の槍を 5 本（動きを読む）</li>
 *   <li>鱗粉の霧: 相手のまわりの床に 5 つの霧（予告の陣 1 秒）。6 秒残り、中にいると削られ・呪われ・遅くなる</li>
 * </ul>
 * </p>
 * <p>
 * 戦い方の強化（2026-10-27 ユーザー指定「AI 強化して」。質問で: EX の技も状況で選ぶ・技のつなぎ（追い込み）・呪いを狙う。避ける動きは入れない（2026-10-15 の決まり）。数値は【仮】）:
 * <ul>
 *   <li>EX の技も状況で選ぶ: 止まっている相手には霧・螺旋、逃げ回る相手には鎖・槍、遠い相手には鏡像・槍、宙の相手には槍・逆さ（霧は使わない）。
 *       当たった技ほど上げ、前の回に当たらなかった技は下げ、同じ技は続けない（当たったかは、技の間に相手の呪いが増えたか・HP が減ったかで見る）</li>
 *   <li>技のつなぎ: 鎖・霧で逃げ道を狭めたら、{@value #COMBO_WINDOW} Tick の間は間を空けずに槍・螺旋で仕留めに行く</li>
 *   <li>呪いを狙う: 2 秒ごとに、見えている相手のうち呪いの一番重い者へ狙いを替える（今の相手より {@value #CURSE_SWITCH} 重いとき）。
 *       相手の呪いが重いほど、EX の技の弾を濃くする（最大 {@value #DENSITY_MAX} 倍）</li>
 * </ul>
 * </p>
 */
public class MementoMoriExEntity extends MementoMoriEntity {

    public static final float MAX_HP_EX = 90000.0F, LANCE = 15.0F, FOG = 3.0F;
    public static final float PHASE_TWO_AT = 0.66F, PHASE_THREE_AT = 0.33F;
    static final double FOG_RADIUS = 3.0;
    static final int FOG_TICKS = 120;
    static final int COMBO_WINDOW = 60, CURSE_SWITCH = 10;
    static final float DENSITY_MAX = 1.6F;

    private static final EntityDataAccessor<Boolean> PHASE3 = SynchedEntityData.defineId(MementoMoriExEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MIRROR = SynchedEntityData.defineId(MementoMoriExEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> MIRROR_AT = SynchedEntityData.defineId(MementoMoriExEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> MIRROR_YAW = SynchedEntityData.defineId(MementoMoriExEntity.class, EntityDataSerializers.FLOAT);

    private ExGoal exGoal;

    public MementoMoriExEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 1000;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP_EX)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FLYING_SPEED, 0.55)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 72.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE3, false);
        entityData.define(MIRROR, false);
        entityData.define(MIRROR_AT, new Vector3f());
        entityData.define(MIRROR_YAW, 0.0F);
    }

    public boolean isPhase3() {
        return entityData.get(PHASE3);
    }

    @Override
    protected float exScale() {
        return 1.5F;
    }

    @Override
    protected float phaseTwoAt() {
        return PHASE_TWO_AT;
    }

    @Override
    protected float cooldownScale() {
        return super.cooldownScale() * (isPhase3() ? 0.8F : 1.0F);
    }

    @Override
    public int spellTicks() {
        return SPELL_TICKS + MementoFinale.ECLIPSE_CYCLE * MementoFinale.ECLIPSES;
    }

    @Override
    public boolean deepCurse() {
        return isPhase3();
    }

    @Override
    boolean fourthMovement() {
        return true;
    }

    @Override
    public List<LuxionEntity.Afterimage> shadows() {
        if (!entityData.get(MIRROR)) return List.of();
        Vector3f v = entityData.get(MIRROR_AT);
        return List.of(new LuxionEntity.Afterimage(v.x, v.y, v.z, entityData.get(MIRROR_YAW), tickCount));
    }

    void setMirror(Vec3 at, float yaw) {
        entityData.set(MIRROR_AT, at.toVector3f());
        entityData.set(MIRROR_YAW, yaw);
        if (!entityData.get(MIRROR)) entityData.set(MIRROR, true);
    }

    Vec3 mirrorPos() {
        return new Vec3(entityData.get(MIRROR_AT));
    }

    void clearMirror() {
        if (entityData.get(MIRROR)) entityData.set(MIRROR, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        exGoal = new ExGoal(this);
        goalSelector.addGoal(1, exGoal);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) return;
        if (!isAlive() || isDefeatedSynced()) {
            clearMirror();
            return;
        }
        if (isPhase2() && !isPhase3() && getEprotect().healthRatio() <= PHASE_THREE_AT && stage() == STAGE_NONE && !spellActive()) enterPhase3();
        if (exGoal != null) exGoal.countDown();
        if (tickCount % 40 == 0) huntCursed();
    }

    /** 呪いを狙う: 見えている相手のうち呪いの一番重い者へ狙いを替える（今の相手より CURSE_SWITCH 以上重いとき） */
    private void huntCursed() {
        LivingEntity t = getTarget();
        int current = t != null && t.isAlive() ? MementoCurse.stacks(t) : -1;
        LivingEntity best = null;
        int bestStacks = current;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48.0), ChronoGazer::canHurt)) {
            if (e == this || !(e instanceof net.minecraft.world.entity.player.Player || e instanceof com.automatavalkyries.entity.ValkyrieEntity)) continue;
            int st = MementoCurse.stacks(e);
            if (st >= bestStacks + CURSE_SWITCH && getSensing().hasLineOfSight(e)) {
                bestStacks = st;
                best = e;
            }
        }
        if (best != null && best != t) setTarget(best);
    }

    /** 弾の濃さ: 相手の呪いが重いほど濃く（1〜{@link #DENSITY_MAX} 倍） */
    float density(LivingEntity t) {
        return 1.0F + (DENSITY_MAX - 1.0F) * Math.min(1.0F, MementoCurse.stacks(t) / (float) MementoCurse.DEEP_MAX_STACKS);
    }

    int dense(LivingEntity t, int count) {
        return Math.round(count * density(t));
    }

    /** 第 3 形態: もう一度昇華して（受けない）、呪いが深まる */
    private void enterPhase3() {
        entityData.set(PHASE3, true);
        startStage(STAGE_AWAKEN);
        bossEvent().setColor(BossEvent.BossBarColor.WHITE);
        playSound(sfx("charge"), 5.0F, 0.8F);
        playSound(SoundEvents.WITHER_SPAWN, 1.5F, 1.8F);
        if (level() instanceof ServerLevel server) message(server, "message.automata_valkyries.memento_mori_ex.phase3", ChatFormatting.LIGHT_PURPLE);
    }

    void message(ServerLevel server, String key, ChatFormatting color) {
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 72 * 72)) {
            p.displayClientMessage(Component.translatable(key).withStyle(color, ChatFormatting.BOLD), true);
        }
    }

    /** from から相手を狙う蝶弾の扇（鏡像からも撃つ） */
    void fanFrom(Vec3 from, LivingEntity t, int count, double spread, double speed, int color) {
        Vec3 d = lead(t, from, speed, 1.0).subtract(from).normalize();
        for (int i = 0; i < count; i++) {
            Vec3 dir = MementoBulletEntity.rotate(d, UP, (i - (count - 1) / 2.0) * spread);
            fire(MementoBulletEntity.shot(from, dir.scale(speed)).kind(MementoBulletEntity.BUTTERFLY).color(color));
        }
    }

    /** from から水平に近い輪（count 発。仰角 el） */
    void ringFrom(Vec3 from, int count, double rot, double el, double speed, int color, byte kind) {
        for (int i = 0; i < count; i++) {
            double az = rot + Math.PI * 2 * i / count;
            Vec3 dir = new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
            fire(MementoBulletEntity.shot(from, dir.scale(speed)).kind(kind).color(color));
        }
    }

    /**
     * 第 3 形態の技（いつもの技の組み立てより先の優先度。いつもの技・終葬の途中には割り込まない）。技は最後まで出し切り、終わるといつもの組み立てに戻る。
     * 技の間も相手のまわりを舞う
     */
    static final class ExGoal extends Goal {
        enum Move { MIRROR, REVERSE, CHAINS, SPIRAL, LANCES, FOG }

        static final int GAP = 40;
        private final MementoMoriExEntity mori;
        private final int[] cooldown = new int[Move.values().length];
        private int gap = 40;
        private Move move;
        private int t;
        private LivingEntity target;
        private final List<Vec3> spots = new ArrayList<>();
        private final List<Vec3> dirs = new ArrayList<>();
        private Vec3 center = Vec3.ZERO;
        /** 技ごとの当たり具合（なめらかにした 0〜1）、前の回に当たったか、前に使った技、技のつなぎの窓の終わり（tickCount）、技の始めの相手の呪いと HP */
        private final double[] hitEma = new double[Move.values().length];
        private final boolean[] lastHit = new boolean[Move.values().length];
        private final boolean[] used = new boolean[Move.values().length];
        private Move last;
        private int comboUntil = -1, startStacks;
        private float startHealth;

        ExGoal(MementoMoriExEntity mori) {
            this.mori = mori;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        void countDown() {
            for (int i = 0; i < cooldown.length; i++) if (cooldown[i] > 0) cooldown[i]--;
            if (gap > 0 && move == null) gap--;
        }

        private Move pick() {
            boolean combo = mori.tickCount < comboUntil;
            if (!mori.isPhase3() || mori.stage() != STAGE_NONE || (gap > 0 && !combo) || mori.spellActive()) return null;
            if (mori.attackGoal != null && !mori.attackGoal.idle()) return null;
            LivingEntity tgt = mori.getTarget();
            if (tgt == null || !tgt.isAlive() || !mori.getSensing().hasLineOfSight(tgt)) return null;
            // 状況で選ぶ: 出せる技に点を付けて一番高い物（少しだけ揺らす）
            MementoTactics tac = mori.tactics;
            double dist = mori.distanceTo(tgt);
            boolean slow = tac.slow(), fast = tac.fast(), air = tac.flying() || !tgt.onGround();
            Move best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Move m : Move.values()) {
                if (cooldown[m.ordinal()] > 0) continue;
                if (m == Move.FOG && air) continue;
                double sc = switch (m) {
                    case MIRROR -> (dist > 18 ? 2.0 : 0.5);
                    case REVERSE -> (slow ? 1.0 : 0.5) + (air ? 1.0 : 0.0);
                    case CHAINS -> (fast ? 2.0 : 0.5);
                    case SPIRAL -> (slow ? 2.0 : 0.5) + (combo ? 3.0 : 0.0);
                    case LANCES -> (fast ? 2.0 : 0.5) + (dist > 18 ? 1.0 : 0.0) + (air ? 2.0 : 0.0) + (combo ? 3.0 : 0.0);
                    case FOG -> (slow ? 3.0 : 0.8);
                };
                sc += hitEma[m.ordinal()] * 2.0;                 // よく当たる技ほど
                if (used[m.ordinal()] && !lastHit[m.ordinal()]) sc -= 1.5; // 前の回に当たらなかった技は下げる
                if (m == last) sc -= 2.0;
                sc += mori.random.nextDouble() * 0.6;
                if (sc > bestScore) {
                    bestScore = sc;
                    best = m;
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            Move m = pick();
            if (m == null) return false;
            move = m;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return move != null && mori.isAlive() && !mori.isDefeatedSynced() && mori.stage() == STAGE_NONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private int cd(int ticks) {
            return Math.max(1, Math.round(ticks * mori.cooldownScale()));
        }

        @Override
        public void start() {
            t = 0;
            target = mori.getTarget();
            last = move;
            startStacks = target != null ? MementoCurse.stacks(target) : 0;
            startHealth = target != null ? target.getHealth() : 0.0F;
            switch (move) {
                case MIRROR -> cooldown[Move.MIRROR.ordinal()] = cd(400);
                case REVERSE -> cooldown[Move.REVERSE.ordinal()] = cd(300);
                case CHAINS -> cooldown[Move.CHAINS.ordinal()] = cd(360);
                case SPIRAL -> cooldown[Move.SPIRAL.ordinal()] = cd(320);
                case LANCES -> cooldown[Move.LANCES.ordinal()] = cd(260);
                case FOG -> cooldown[Move.FOG.ordinal()] = cd(340);
            }
            mori.playSound(sfx("cast"), 4.0F, 0.8F + move.ordinal() * 0.08F);
            mori.message((ServerLevel) mori.level(), "message.automata_valkyries.memento_mori_ex." + move.name().toLowerCase(java.util.Locale.ROOT), ChatFormatting.LIGHT_PURPLE);
        }

        @Override
        public void stop() {
            mori.clearMirror();
            if (move != null && target != null) { // 当たったか（技の間に呪いが増えた・HP が減った）
                boolean landed = MementoCurse.stacks(target) > startStacks || target.getHealth() < startHealth - 0.5F || !target.isAlive();
                int i = move.ordinal();
                hitEma[i] = hitEma[i] * 0.6 + (landed ? 0.4 : 0.0);
                lastHit[i] = landed;
                used[i] = true;
                if (move == Move.CHAINS || move == Move.FOG) comboUntil = mori.tickCount + COMBO_WINDOW; // 逃げ道を狭めた: 間を空けずに仕留めに行く
            }
            move = null;
            gap = mori.tickCount < comboUntil ? 8 : GAP;
        }

        private void finish() {
            move = null;
        }

        @Override
        public void tick() {
            if (move == null) return;
            if (target == null || !target.isAlive()) {
                finish();
                return;
            }
            ServerLevel server = (ServerLevel) mori.level();
            mori.tactics.observe(target); // 相手の動きを見張り続ける（いつもの組み立てが止まっている間も）
            mori.flyAround(target, 1.0F, 0.25);
            mori.faceToward(target.position());
            switch (move) {
                case MIRROR -> tickMirror(server);
                case REVERSE -> tickReverse(server);
                case CHAINS -> tickChains(server);
                case SPIRAL -> tickSpiral(server);
                case LANCES -> tickLances(server);
                case FOG -> tickFog(server);
            }
            t++;
        }

        /** 鏡像の死蝶（6 秒）: 相手を挟んだ反対側に鏡像。12 Tick ごとに扇、40 Tick ごとに輪を、本体と鏡像から向かい合わせに */
        private void tickMirror(ServerLevel server) {
            Vec3 mirror = target.position().multiply(2, 0, 2).subtract(mori.position().multiply(1, 0, 1)).add(0, mori.getY(), 0);
            float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - mirror.x), target.getZ() - mirror.z));
            if (t == 0) {
                Fx.burst(server, glow(C_WHITE, 0.6F, 20), mirror.add(0, CHEST, 0), 40, 0.4);
                Fx.mandala(server, mirror.add(0, CHEST, 0), C_PINK, 4.0F, 120, true, true);
            }
            mori.setMirror(mirror, yaw);
            Vec3 mirrorHand = mirror.add(mori.hand().subtract(mori.position()).multiply(-1, 1, -1));
            if (t % 12 == 6) {
                mori.fanFrom(mori.hand(), target, mori.dense(target, 7), 0.12, 0.55, C_PINK);
                mori.fanFrom(mirrorHand, target, mori.dense(target, 7), 0.12, 0.55, C_CYAN);
                mori.shotSound(1.1F);
            }
            if (t % 40 == 20) {
                double rot = mori.random.nextDouble() * Math.PI;
                int ring = mori.dense(target, 32);
                mori.ringFrom(mori.chest(), ring, rot, -0.15, 0.35, C_PALE, MementoBulletEntity.SMALL);
                mori.ringFrom(mirror.add(0, CHEST, 0), ring, rot + Math.PI / ring, -0.15, 0.35, C_WHITE, MementoBulletEntity.SMALL);
            }
            if (t >= 120) {
                Fx.burst(server, glow(C_CYAN, 0.6F, 20), mirror.add(0, CHEST, 0), 30, 0.4);
                mori.clearMirror();
                finish();
            }
        }

        /** 逆さの弾幕: 胸から輪が広がって消え、10 Tick 後に消えた所から逆向きに戻ってくる（30 Tick ごとに 3 波） */
        private void tickReverse(ServerLevel server) {
            if (t % 30 == 0 && t < 90) {
                Vec3 from = mori.chest();
                Vec3 toT = target.position().add(0, 1.0, 0).subtract(from);
                double el = Math.atan2(toT.y, Math.sqrt(toT.x * toT.x + toT.z * toT.z));
                double rot = mori.random.nextDouble() * Math.PI;
                int n = mori.dense(target, 36), out = 24;
                double speed = 0.5;
                for (int i = 0; i < n; i++) {
                    double az = rot + Math.PI * 2 * i / n;
                    Vec3 dir = new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
                    mori.fire(MementoBulletEntity.shot(from, dir.scale(speed)).kind(MementoBulletEntity.BUTTERFLY).color(C_PINK).life(out));
                    Vec3 end = from.add(dir.scale(speed * out));
                    mori.fire(MementoBulletEntity.shot(end, dir.scale(-speed * 0.8)).delay(out + 10).kind(MementoBulletEntity.BUTTERFLY).color(C_WHITE).life(out + 10 + 60));
                }
                mori.shotSound(0.7F);
            }
            if (t == 30 * 2 + 24 + 10) mori.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0F, 0.6F);
            if (t >= 120) finish();
        }

        /** 蝶の鎖: 4 方向から順に（25 Tick ごと）、2 段の鎖の壁が迫る。隙間は幅 3 で 1 か所 */
        private void tickChains(ServerLevel server) {
            if (t == 0) center = target.position();
            if (t % 25 == 0 && t < 100) {
                double a = mori.random.nextDouble() * Math.PI * 2 + t / 25 * Math.PI / 2;
                Vec3 in = new Vec3(-Math.cos(a), 0, -Math.sin(a));
                Vec3 side = new Vec3(-in.z, 0, in.x);
                Vec3 mid = target.position().subtract(in.scale(16.0));
                double gapAt = (mori.random.nextDouble() - 0.5) * 16.0;
                for (double s = -14.0; s <= 14.0; s += 1.0) {
                    if (Math.abs(s - gapAt) < 1.5) continue;
                    for (double h : new double[]{0.35, 1.25}) {
                        Vec3 p = mid.add(side.scale(s)).add(0, h, 0);
                        mori.fire(MementoBulletEntity.shot(p, in.scale(0.28)).kind(MementoBulletEntity.SMALL).color(h < 1.0 ? C_VIOLET : C_PINK).delay(10).life(140));
                    }
                }
                Fx.line(server, glow(C_PINK, 0.3F, 10), mid.add(side.scale(-14)).add(0, 0.8, 0), mid.add(side.scale(14)).add(0, 0.8, 0), 1.0);
                mori.playSound(SoundEvents.CHAIN_PLACE, 3.0F, 1.2F);
            }
            if (t >= 130) finish();
        }

        /** 螺旋階段: 相手のまわり半径 8 から 4 本の腕の渦巻きが内へ巻き込む（3 秒） */
        private void tickSpiral(ServerLevel server) {
            if (t == 0) center = target.position();
            if (t < 60 && t % 2 == 0) {
                int arms = mori.density(target) > 1.3F ? 6 : 4;
                for (int arm = 0; arm < arms; arm++) {
                    double a = arm * Math.PI * 2 / arms + t * 0.09;
                    Vec3 p = center.add(Math.cos(a) * 8.0, 0.4 + (t % 12) * 0.12, Math.sin(a) * 8.0);
                    Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    Vec3 inward = new Vec3(-Math.cos(a), 0, -Math.sin(a));
                    mori.fire(MementoBulletEntity.shot(p, tangent.scale(0.25).add(inward.scale(0.08))).curl(UP, 0.03F).kind(arm % 2 == 0 ? MementoBulletEntity.BUTTERFLY : MementoBulletEntity.SMALL)
                            .color(arm % 2 == 0 ? C_CYAN : C_PALE).life(90));
                }
            }
            if (t % 10 == 0 && t < 60) Fx.mandala(server, center.add(0, 0.1, 0), C_CYAN, 8.0F, 10, false, t % 20 == 0);
            if (t >= 70) finish();
        }

        /** 月光の槍: 空から予告線（1 秒）の後に大玉の槍。12 Tick ごとに 5 本（動きを読む） */
        private void tickLances(ServerLevel server) {
            int n = t / 12, at = t % 12, lances = mori.dense(target, 5);
            if (n < lances && at == 0) {
                double a = mori.random.nextDouble() * Math.PI * 2;
                Vec3 sky = target.position().add(Math.cos(a) * 8.0, 18.0, Math.sin(a) * 8.0);
                Vec3 aim = target.position().add(target.getDeltaMovement().multiply(1, 0, 1).scale(22)).add(0, 0.6, 0);
                spots.add(sky);
                dirs.add(aim);
            }
            for (int i = 0; i < spots.size(); i++) {
                int born = i * 12, age = t - born;
                if (age >= 0 && age < 20 && age % 3 == 0) Fx.line(server, glow(C_WHITE, 0.3F, 5), spots.get(i), dirs.get(i), 0.8);
                if (age == 20) {
                    Vec3 d = dirs.get(i).subtract(spots.get(i)).normalize();
                    mori.fire(MementoBulletEntity.shot(spots.get(i), d.scale(0.9)).accel(1.08F, 1.6F).kind(MementoBulletEntity.GRAND).color(C_WHITE).damage(LANCE));
                    server.playSound(null, spots.get(i).x, spots.get(i).y, spots.get(i).z, SoundEvents.TRIDENT_THROW, mori.getSoundSource(), 3.0F, 0.6F);
                }
            }
            if (t >= lances * 12 + 30) {
                spots.clear();
                dirs.clear();
                finish();
            }
        }

        /** 鱗粉の霧: 相手のまわりの床に 5 つ（予告 1 秒）。{@value #FOG_TICKS} Tick 残り、中にいると 10 Tick ごとに削られ・呪われ・遅くなる */
        private void tickFog(ServerLevel server) {
            if (t == 0) {
                spots.clear();
                for (int i = 0; i < mori.dense(target, 5); i++) {
                    double a = mori.random.nextDouble() * Math.PI * 2, r = i == 0 ? 0.0 : 4.0 + mori.random.nextDouble() * 6.0;
                    Vec3 p = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    spots.add(new Vec3(p.x, mori.groundY(p.add(0, 3, 0)) + 0.1, p.z));
                }
            }
            if (t < 20 && t % 4 == 0) for (Vec3 s : spots) Fx.mandala(server, s, C_VIOLET, (float) FOG_RADIUS, 5, false, true);
            if (t >= 20 && t < 20 + FOG_TICKS) {
                for (Vec3 s : spots) {
                    if (t % 3 == 0) Fx.spray(server, glow(t % 6 == 0 ? C_VIOLET : C_PINK, 0.4F, 20), s.add(0, 0.6, 0), 6, FOG_RADIUS * 0.6, 0.5, FOG_RADIUS * 0.6, 0.01);
                    if (t % 10 == 0) {
                        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(s, s).inflate(FOG_RADIUS, 2.5, FOG_RADIUS), ChronoGazer::canHurt)) {
                            if (e.position().subtract(s).multiply(1, 0, 1).length() > FOG_RADIUS) continue;
                            if (e.hurt(mori.damageSources().indirectMagic(mori, mori), mori.damageFor(e, FOG))) MementoCurse.add(e, MementoCurse.BULLET, mori);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                        }
                    }
                }
            }
            if (t >= 20 + FOG_TICKS) finish();
        }
    }
}
