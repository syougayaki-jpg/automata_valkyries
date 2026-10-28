package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ガリアフォート EX（2026-10-27 ユーザー指定「他二体の AEC 級ボスも EX を追加する」）。回想のトライアル「鋼鉄の残響」のボス。
 * HP {@value #MAX_HP_EX}（元の 2 倍）、技のダメージ ×1.5、3 形態（66% で第 2 形態＝人型、33% で第 3 形態）。見た目は黒鉄と赤熱の色違い。
 * <p>
 * 第 3 形態の技（2026-10-27 ユーザー指定「ルクシオン EX のように 6 個ほど」。数値は【仮】）は {@link ExGoal} が、いつもの技の手が空いた時に割り込んで出す:
 * <ul>
 *   <li>絨毯砲撃: 相手を横切る 4 本の帯（幅 {@value #CARPET_WIDTH}）に予告を出し、帯に沿って順に砲弾が降る。帯の間を抜けて避ける</li>
 *   <li>重力の錨: 錨を撃ち込んで相手を引き寄せ、叩きつけてから衝撃波の輪を 3 重に広げる</li>
 *   <li>反射障壁: 障壁を紅く反転させ、{@value #REFLECT_TICKS} Tick の間まわりの飛び道具を撃ち返す</li>
 *   <li>歪曲機雷: 相手のまわりに機雷 8 つ（予告の陣）。2.5 秒後に歪曲して爆ぜる</li>
 *   <li>砲台の展開: 対空砲 2 基と曲射砲 2 基を降ろす（同時に {@value #TURRET_MAX} 基まで）</li>
 *   <li>扇状斉射: 主砲で扇に 5 連射（1 発ずつ予告線）</li>
 * </ul>
 * 第 3 形態の大技は**リミットオーバー歪曲破界砲**（2026-10-27 ユーザー指定「照射時間をながめ最後に超大歪曲が起きる」。質問で: 追う照射＋前方の大歪曲・第 3 形態で繰り返し）:
 * 脚を打ち込み（1 秒）、主砲から極太の光を {@value #LIMIT_BEAM} Tick 照射し続けて相手をゆっくり追い（1 Tick に {@value #LIMIT_TURN} 度まで）、
 * 最後に砲の前方 {@value #DISTORT_ARC} 度・{@value #DISTORT_RANGE} m の扇が超大歪曲で爆ぜる（横・後ろへ回れば避けられる）。{@value #LIMIT_EVERY} Tick ごと
 * </p>
 * <p>
 * 戦い方の強化（2026-10-27 ユーザー指定「AI 強化して」。質問で: 状況で技を選ぶ・動きを読む砲撃・背後への対応・危険な相手を狙う。数値は【仮】）:
 * <ul>
 *   <li>状況で技を選ぶ: 第 3 形態の技に点を付けて一番高い物を使う（近い相手には錨・機雷、遠い相手には絨毯砲撃・扇状斉射、宙の相手には砲台（対空）、
 *       飛び道具で撃たれ続けていれば反射障壁、同じ技は続けない）</li>
 *   <li>動きを読む砲撃: 相手の速さをなめらかに見張り、主砲・全開射撃（いつもの技も）・扇状斉射・破界砲の照射・絨毯砲撃・機雷を、届く時に相手がいる所へ</li>
 *   <li>背後への対応: 背後・真横（{@value #BEHIND_DEG} 度より後ろ）に {@value #BEHIND_TICKS} Tick 回り込まれ続けたら、急旋回して振り払う（まわりへのなぎ払いと衝撃波）</li>
 *   <li>危険な相手を狙う: 受けたダメージを相手ごとに数え（時間で薄れる）、2 秒ごとに一番危険な相手（遠くから撃つ相手は 1.3 倍）へ狙いを替える</li>
 * </ul>
 * </p>
 */
public class GalliaFortExEntity extends GalliaFortEntity {

    public static final float MAX_HP_EX = 30000.0F, CARPET = 22.0F, ANCHOR_SLAM = 40.0F, MINE = 30.0F, LIMIT_TICK = 12.0F, DISTORT = 100.0F;
    public static final float PHASE_TWO_AT = 0.66F, PHASE_THREE_AT = 0.33F;
    static final int REFLECT_TICKS = 100, TURRET_MAX = 4, LIMIT_BRACE = 20, LIMIT_BEAM = 160, LIMIT_EVERY = 800, AWAKEN_TICKS = 40;
    static final double CARPET_WIDTH = 4.0, CARPET_LENGTH = 40.0, LIMIT_RANGE = 60.0, LIMIT_WIDTH = 2.6, DISTORT_RANGE = 40.0;
    static final float LIMIT_TURN = 1.0F, DISTORT_ARC = 80.0F;
    /** 背後への対応: この角度より後ろに、この Tick 数回り込まれ続けたら振り払う */
    static final float BEHIND_DEG = 110.0F;
    static final int BEHIND_TICKS = 40;
    private static final int C_EMBER = 0xFF5A1E, C_CRIMSON = 0xFF2A4A, C_WHITE = 0xFFFFFF, C_GOLD = 0xFFC060;

    private static final EntityDataAccessor<Boolean> PHASE3 = SynchedEntityData.defineId(GalliaFortExEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> REFLECTING = SynchedEntityData.defineId(GalliaFortExEntity.class, EntityDataSerializers.BOOLEAN);

    /** サーバー: 目覚めの残り Tick（受けない）、降ろした砲台、撃ち返した飛び道具 */
    private int awaken, reflectTicks;
    /** 狙う相手の動き（なめらかにした 1 Tick の移動）と前の位置、背後に回り込まれている Tick、飛び道具で撃たれた数（時間で薄れる） */
    private Vec3 targetVel = Vec3.ZERO, lastTargetPos;
    private LivingEntity tracked;
    private int behindTicks;
    private float projectileHeat;
    /** 相手ごとの危険度（受けたダメージ。時間で薄れる） */
    private final java.util.Map<LivingEntity, Float> threat = new java.util.HashMap<>();
    private final List<Mob> turrets = new ArrayList<>();
    private final Set<Projectile> reflected = new HashSet<>();
    private ExGoal exGoal;

    public GalliaFortExEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 900;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP_EX)
                .add(Attributes.ARMOR, 22.0)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, STOMP)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE3, false);
        entityData.define(REFLECTING, false);
    }

    public boolean isPhase3() {
        return entityData.get(PHASE3);
    }

    /** 反射障壁の最中（描く側: 障壁を紅く） */
    public boolean isReflecting() {
        return entityData.get(REFLECTING);
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
    protected void registerGoals() {
        super.registerGoals();
        exGoal = new ExGoal(this);
        goalSelector.addGoal(1, exGoal);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (awaken > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // 目覚めの間は受けない
        if (!level().isClientSide && Float.isFinite(amount) && amount > 0.0F) { // 危険度と、飛び道具で撃たれている具合を覚える
            if (source.getEntity() instanceof LivingEntity attacker && attacker != this) {
                float weight = attacker.distanceTo(this) > 12.0F ? 1.3F : 1.0F;
                threat.merge(attacker, Math.min(amount, 2000.0F) * weight, Float::sum);
            }
            if (source.getDirectEntity() instanceof Projectile) projectileHeat += 1.0F;
        }
        return super.hurt(source, amount);
    }

    /** 動きを読んだ狙い: 相手の今の位置 ＋ 速さ × 先読みの Tick（遠いほど長く、最大 {@code 12}） */
    @Override
    Vec3 aimFor(LivingEntity t) {
        return leadPoint(t, Math.min(12.0, 4.0 + distanceTo(t) * 0.15)).add(0, t.getBbHeight() * 0.5, 0);
    }

    /** ticks 先の相手の足元（水平の動きだけ読む） */
    Vec3 leadPoint(LivingEntity t, double ticks) {
        Vec3 v = t == tracked ? targetVel : Vec3.ZERO;
        return t.position().add(v.x * ticks, 0.0, v.z * ticks);
    }

    /** 相手の動きと、背後に回り込まれているかを見張る。2 秒ごとに危険な相手へ狙いを替える */
    private void watchTarget() {
        LivingEntity t = getTarget();
        if (t != tracked) {
            tracked = t;
            lastTargetPos = null;
            targetVel = Vec3.ZERO;
            behindTicks = 0;
        }
        if (t != null && t.isAlive()) {
            Vec3 pos = t.position();
            Vec3 step = lastTargetPos == null || pos.distanceToSqr(lastTargetPos) > 16.0 ? Vec3.ZERO : pos.subtract(lastTargetPos); // 瞬間移動は数えない
            targetVel = targetVel.scale(0.8).add(step.scale(0.2));
            lastTargetPos = pos;
            Vec3 to = pos.subtract(position()).multiply(1, 0, 1);
            boolean behind = to.lengthSqr() > 1.0e-4 && to.length() < 14.0
                    && to.normalize().dot(Vec3.directionFromRotation(0, yBodyRot)) < Math.cos(Math.toRadians(BEHIND_DEG));
            behindTicks = behind ? behindTicks + 1 : Math.max(0, behindTicks - 2);
        }
        projectileHeat *= 0.985F; // 3 秒ほどで半分に
        if (tickCount % 40 == 0) {
            threat.replaceAll((e, v) -> v * 0.7F);
            threat.keySet().removeIf(e -> !e.isAlive() || e.isRemoved() || e.distanceToSqr(this) > 64 * 64 || threat.get(e) < 1.0F);
            LivingEntity best = null;
            float bestV = 0.0F;
            for (var en : threat.entrySet()) {
                if (!ChronoGazer.canHurt(en.getKey()) || !getSensing().hasLineOfSight(en.getKey())) continue;
                if (en.getValue() > bestV) {
                    bestV = en.getValue();
                    best = en.getKey();
                }
            }
            float current = t != null ? threat.getOrDefault(t, 0.0F) : 0.0F;
            if (best != null && best != t && bestV > current * 1.5F + 50.0F) setTarget(best);
        }
    }

    // ---- 毎 Tick ----

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            if (isPhase3() && tickCount % 3 == 0) { // 赤熱の火の粉
                level().addParticle(ParticleTypes.LAVA, getRandomX(1.2), getY() + random.nextDouble() * getBbHeight(), getRandomZ(1.2), 0, 0, 0);
            }
            return;
        }
        if (!isAlive() || isDefeatedSynced()) {
            clearTurrets();
            return;
        }
        if (isPhase2() && !isPhase3() && getEprotect().healthRatio() <= PHASE_THREE_AT && stage() == STAGE_NONE) enterPhase3();
        if (awaken > 0) awaken--;
        watchTarget();
        if (exGoal != null) exGoal.countDown();
        if (isReflecting()) tickReflect();
        turrets.removeIf(m -> !m.isAlive() || m.isRemoved());
    }

    private void enterPhase3() {
        entityData.set(PHASE3, true);
        awaken = AWAKEN_TICKS;
        bossEvent().setColor(BossEvent.BossBarColor.RED);
        playSound(SoundEvents.WARDEN_ROAR, 3.0F, 0.4F);
        playSound(SoundEvents.WITHER_SPAWN, 1.5F, 0.6F);
        if (level() instanceof ServerLevel server) {
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            Fx.shockwave(server, position().add(0, 0.1, 0), C_EMBER, 14.0F, 30);
            Fx.shockwave(server, position().add(0, 0.2, 0), C_CRIMSON, 20.0F, 36);
            Fx.burst(server, glow(C_EMBER, 0.8F, 30), c, 80, 0.5);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
            server.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 60, 2.0, 2.0, 2.0, 0.2);
            message(server, "message.automata_valkyries.galliafort_ex.phase3", ChatFormatting.RED);
        }
    }

    void message(ServerLevel server, String key, ChatFormatting color) {
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 72 * 72)) {
            p.displayClientMessage(Component.translatable(key).withStyle(color, ChatFormatting.BOLD), true);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) clearTurrets();
        super.remove(reason);
    }

    void clearTurrets() {
        for (Mob m : turrets) if (m.isAlive()) m.discard();
        turrets.clear();
    }

    // ---- 技の部品 ----

    /** at のまわり radius に爆ぜる（防具・盾を通る攻城のダメージ） */
    void blast(ServerLevel server, Vec3 at, double radius, float damage, int color) {
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 3.0, radius), ChronoGazer::canHurt)) {
            if (e.position().subtract(at).multiply(1, 0, 1).length() > radius + e.getBbWidth() * 0.5) continue;
            e.hurt(siegeDamage(), damageFor(e, damage));
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
        }
        Fx.shockwave(server, at.add(0, 0.1, 0), color, (float) radius * 1.4F, 14);
        Fx.burst(server, glow(color, 0.6F, 16), at.add(0, 0.6, 0), 14, 0.25);
        server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
    }

    /** 反射障壁: まわりの飛び道具（自分の物以外）を、速さを上げて撃ち返す */
    private void tickReflect() {
        if (!(level() instanceof ServerLevel server)) return;
        if (--reflectTicks <= 0) {
            entityData.set(REFLECTING, false);
            reflected.clear();
            return;
        }
        double r = barrierRadius(0.0F) + 3.0;
        for (Projectile p : server.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(r), p -> p.getOwner() != this && !reflected.contains(p))) {
            reflected.add(p);
            p.setDeltaMovement(p.getDeltaMovement().scale(-1.3));
            p.setOwner(this);
            Fx.burst(server, glow(C_CRIMSON, 0.4F, 10), p.position(), 6, 0.1);
            server.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, getSoundSource(), 1.0F, 0.6F);
        }
        if (tickCount % 4 == 0) Fx.shockwave(server, barrierCenter(), C_CRIMSON, (float) r - 2.0F, 6);
    }

    /**
     * 第 3 形態の技（{@link GalliaAttackGoal} より先の優先度。いつもの技の途中には割り込まない）。技は最後まで出し切り、終わると {@link GalliaAttackGoal} に戻る。
     * 技どうしの間は少なくとも {@link #GAP} Tick 空ける
     */
    static final class ExGoal extends Goal {
        enum Move { AWAKEN, CARPET, ANCHOR, REFLECT, MINES, TURRETS, FAN, LIMIT, SHAKE_OFF }

        static final int GAP = 30;
        private final GalliaFortExEntity fort;
        private final int[] cooldown = new int[Move.values().length];
        private int gap = 20;
        private Move move;
        private int t;
        private LivingEntity target;
        private Vec3 dir = new Vec3(0, 0, 1), center = Vec3.ZERO;
        private final List<Vec3> spots = new ArrayList<>();
        private final List<Vec3> lanes = new ArrayList<>();
        private final Set<LivingEntity> hit = new HashSet<>();
        private GalliaSiegeBeamEntity beam;
        /** 前に使った技（続けない） */
        private Move last;

        ExGoal(GalliaFortExEntity fort) {
            this.fort = fort;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            cooldown[Move.LIMIT.ordinal()] = 200;
        }

        void countDown() {
            for (int i = 0; i < cooldown.length; i++) if (cooldown[i] > 0) cooldown[i]--;
            if (gap > 0 && move == null) gap--;
        }

        private boolean ready(Move m) {
            return cooldown[m.ordinal()] <= 0;
        }

        private Move pick() {
            if (fort.awaken > 0) return Move.AWAKEN;
            if (!fort.isPhase3() || fort.stage() != STAGE_NONE || gap > 0) return null;
            if (fort.attackGoal != null && fort.attackGoal.busy()) return null;
            LivingEntity tgt = fort.getTarget();
            if (tgt == null || !tgt.isAlive()) return null;
            double dist = fort.distanceTo(tgt);
            boolean sees = fort.getSensing().hasLineOfSight(tgt);
            if (fort.behindTicks >= BEHIND_TICKS && ready(Move.SHAKE_OFF)) return Move.SHAKE_OFF; // 背後に回り込まれ続けた
            if (ready(Move.LIMIT) && sees && dist < LIMIT_RANGE - 6) return Move.LIMIT;
            // 状況で技を選ぶ: 出せる技に点を付けて一番高い物（少しだけ揺らす）
            boolean air = airborne(tgt);
            Move best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Move m : new Move[]{Move.CARPET, Move.ANCHOR, Move.REFLECT, Move.MINES, Move.TURRETS, Move.FAN}) {
                if (!ready(m)) continue;
                double s = switch (m) {
                    case CARPET -> dist < 36 ? (dist > 16 ? 2.0 : 0.5) + (air ? -2.0 : 0.0) : Double.NaN;
                    case ANCHOR -> sees && dist > 6 && dist < 28 ? (dist < 16 ? 2.0 : 1.0) + (air ? 1.0 : 0.0) : Double.NaN; // 宙の相手も引きずり降ろす
                    case REFLECT -> !fort.isReflecting() ? fort.projectileHeat * 1.2 - 0.5 : Double.NaN;
                    case MINES -> dist < 30 ? (dist < 10 ? 2.0 : 1.0) + (air ? -2.0 : 0.0) : Double.NaN;
                    case TURRETS -> fort.turrets.size() < TURRET_MAX ? 0.5 + (air ? 3.0 : 0.0) + (TURRET_MAX - fort.turrets.size()) * 0.2 : Double.NaN;
                    case FAN -> sees && dist > 6 && dist < CANNON_RANGE ? (dist > 14 ? 2.0 : 1.0) : Double.NaN;
                    default -> Double.NaN;
                };
                if (Double.isNaN(s)) continue;
                if (m == last) s -= 1.5;
                s += fort.random.nextDouble() * 0.6;
                if (s > bestScore) {
                    bestScore = s;
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
            return move != null && fort.isAlive() && !fort.isDefeatedSynced() && fort.stage() == STAGE_NONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private int cd(int ticks) {
            return Math.max(1, Math.round(ticks * fort.cooldownScale()));
        }

        @Override
        public void start() {
            t = 0;
            target = fort.getTarget();
            fort.getNavigation().stop();
            fort.setAggressive(true);
            hit.clear();
            if (move != Move.AWAKEN) last = move;
            switch (move) {
                case SHAKE_OFF -> cooldown[Move.SHAKE_OFF.ordinal()] = cd(160);
                case CARPET -> cooldown[Move.CARPET.ordinal()] = cd(260);
                case ANCHOR -> cooldown[Move.ANCHOR.ordinal()] = cd(240);
                case REFLECT -> cooldown[Move.REFLECT.ordinal()] = cd(400);
                case MINES -> cooldown[Move.MINES.ordinal()] = cd(220);
                case TURRETS -> cooldown[Move.TURRETS.ordinal()] = cd(600);
                case FAN -> cooldown[Move.FAN.ordinal()] = cd(200);
                case LIMIT -> cooldown[Move.LIMIT.ordinal()] = LIMIT_EVERY;
                default -> {}
            }
        }

        @Override
        public void stop() {
            if (move == Move.LIMIT) fort.setBarrierOff(false);
            if (beam != null && beam.isAlive()) beam.discard();
            beam = null;
            move = null;
            gap = GAP;
        }

        private void finish() {
            move = null;
        }

        private boolean targetOk() {
            return target != null && target.isAlive();
        }

        /** 体を maxDeg 度ずつ at へ向ける（砲塔＝頭はすぐ向く）。砲の上下も合わせる */
        private void turnToward(Vec3 at, float maxDeg) {
            float yaw = (float) (Mth.atan2(at.z - fort.getZ(), at.x - fort.getX()) * Mth.RAD_TO_DEG) - 90.0F;
            float body = Mth.approachDegrees(fort.yBodyRot, yaw, maxDeg);
            fort.setYRot(body);
            fort.setYBodyRot(body);
            fort.setYHeadRot(Mth.approachDegrees(fort.getYHeadRot(), yaw, Math.max(maxDeg, 6.0F)));
            double flat = Math.sqrt((at.x - fort.getX()) * (at.x - fort.getX()) + (at.z - fort.getZ()) * (at.z - fort.getZ()));
            fort.setXRot((float) -(Mth.atan2(at.y - fort.muzzle().y, flat) * Mth.RAD_TO_DEG));
        }

        private Vec3 aimAt(LivingEntity e) {
            return e.position().add(0, e.getBbHeight() * 0.5, 0);
        }

        @Override
        public void tick() {
            if (move == null) return;
            ServerLevel server = (ServerLevel) fort.level();
            fort.getNavigation().stop();
            if (!targetOk() && move != Move.AWAKEN && move != Move.LIMIT && move != Move.REFLECT && move != Move.SHAKE_OFF) {
                finish();
                return;
            }
            switch (move) {
                case AWAKEN -> tickAwaken(server);
                case CARPET -> tickCarpet(server);
                case ANCHOR -> tickAnchor(server);
                case REFLECT -> tickReflectMove(server);
                case MINES -> tickMines(server);
                case TURRETS -> tickTurrets(server);
                case FAN -> tickFan(server);
                case LIMIT -> tickLimit(server);
                case SHAKE_OFF -> tickShakeOff(server);
            }
            t++;
        }

        /** 目覚め（第 3 形態に入った所。受けない）: 赤熱して吠える */
        private void tickAwaken(ServerLevel server) {
            if (t % 5 == 0) Fx.gather(server, glow(C_EMBER, 0.5F, 14), fort.position().add(0, fort.getBbHeight() * 0.5, 0), 12, 8.0, 12);
            if (fort.awaken <= 0) finish();
        }

        /** 絨毯砲撃: 相手を横切る 4 本の帯に予告（1.5 秒）、帯に沿って 2 Tick ごとに砲弾が降る */
        private void tickCarpet(ServerLevel server) {
            if (t == 0) {
                center = fort.leadPoint(target, 30); // 砲弾が降る頃に相手がいる所
                Vec3 toTarget = center.subtract(fort.position()).multiply(1, 0, 1);
                Vec3 along = toTarget.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(-toTarget.z, 0, toTarget.x).normalize(); // 帯は自分から見て横
                Vec3 across = new Vec3(-along.z, 0, along.x);
                double offset = fort.random.nextDouble() * CARPET_WIDTH * 2;
                lanes.clear();
                spots.clear();
                for (int i = 0; i < 4; i++) {
                    lanes.add(center.add(across.scale((i - 1.5) * CARPET_WIDTH * 2 + offset - CARPET_WIDTH)));
                    spots.add(along);
                }
                fort.level().broadcastEntityEvent(fort, EVENT_CANNON_CHARGE);
                fort.message(server, "message.automata_valkyries.galliafort_ex.carpet", ChatFormatting.GOLD);
                fort.playSound(SoundEvents.BELL_RESONATE, 3.0F, 0.5F);
            }
            for (int i = 0; i < lanes.size(); i++) {
                Vec3 mid = lanes.get(i), along = spots.get(i);
                if (t < 30 && t % 4 == 0) { // 帯の予告（2 本の赤い線）
                    Vec3 side = new Vec3(-along.z, 0, along.x).scale(CARPET_WIDTH / 2);
                    Vec3 a = mid.subtract(along.scale(CARPET_LENGTH / 2)).add(0, 0.2, 0), b = mid.add(along.scale(CARPET_LENGTH / 2)).add(0, 0.2, 0);
                    Fx.line(server, glow(C_CRIMSON, 0.35F, 6), a.add(side), b.add(side), 1.2);
                    Fx.line(server, glow(C_CRIMSON, 0.35F, 6), a.subtract(side), b.subtract(side), 1.2);
                }
                int k = t - 30 - i * 6; // 帯ごとに少しずつずらして
                if (k >= 0 && k % 2 == 0 && k / 2 <= 10) {
                    Vec3 p = mid.add(along.scale(-CARPET_LENGTH / 2 + (k / 2) * CARPET_LENGTH / 10.0));
                    Vec3 ground = new Vec3(p.x, center.y, p.z);
                    Fx.line(server, glow(C_EMBER, 0.6F, 6), ground.add(0, 16, 0), ground, 1.0);
                    fort.blast(server, ground, CARPET_WIDTH / 2 + 0.5, CARPET, C_EMBER);
                    if (k % 4 == 0) server.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE, fort.getSoundSource(), 1.5F, 0.8F);
                }
            }
            if (t >= 30 + 3 * 6 + 22) finish();
        }

        /** 重力の錨: 錨を撃ち込み（0.5 秒）、1.5 秒引き寄せ、叩きつけて衝撃波の輪を 3 重に */
        private void tickAnchor(ServerLevel server) {
            Vec3 chest = fort.position().add(0, fort.getBbHeight() * 0.5, 0);
            if (t == 0) {
                fort.level().broadcastEntityEvent(fort, EVENT_HAMMER_RAISE);
                fort.playSound(SoundEvents.CHAIN_PLACE, 3.0F, 0.5F);
                fort.message(server, "message.automata_valkyries.galliafort_ex.anchor", ChatFormatting.GOLD);
            }
            turnToward(aimAt(target), 6.0F);
            if (t >= 10 && t < 40) { // 鎖でつないで引き寄せる
                Fx.line(server, glow(C_GOLD, 0.35F, 4), chest, aimAt(target), 0.8);
                Vec3 pull = chest.subtract(target.position()).multiply(1, 0.3, 1);
                if (pull.length() > 4.0) {
                    target.setDeltaMovement(target.getDeltaMovement().scale(0.5).add(pull.normalize().scale(0.45)));
                    target.hurtMarked = true;
                }
                if (t % 5 == 0) server.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_HIT, fort.getSoundSource(), 1.5F, 0.6F);
            }
            if (t == 40) {
                center = fort.position().add(Vec3.directionFromRotation(0, fort.getYRot()).scale(4.0));
                fort.level().broadcastEntityEvent(fort, EVENT_HAMMER_HIT);
                fort.blast(server, center, 4.0, ANCHOR_SLAM, C_GOLD);
                fort.playSound(SoundEvents.ANVIL_LAND, 3.0F, 0.4F);
            }
            if (t > 40 && (t - 40) % 6 == 0 && t <= 58) fort.quake(center, (t - 40) / 6 * 4.0, hit); // 衝撃波の輪 4・8・12
            if (t >= 64) finish();
        }

        /** 反射障壁: 障壁を紅く反転させ、{@value #REFLECT_TICKS} Tick の間飛び道具を撃ち返す（その間もいつもの技は出せる。ここは掛けるだけ） */
        private void tickReflectMove(ServerLevel server) {
            if (t == 0) {
                fort.entityData.set(REFLECTING, true);
                fort.reflectTicks = REFLECT_TICKS;
                fort.reflected.clear();
                fort.playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
                Fx.shockwave(server, fort.barrierCenter(), C_CRIMSON, 10.0F, 16);
                fort.message(server, "message.automata_valkyries.galliafort_ex.reflect", ChatFormatting.RED);
            }
            if (targetOk()) turnToward(aimAt(target), 4.0F);
            if (t >= 12) finish();
        }

        /** 歪曲機雷: 相手のまわりに 8 つ（予告の陣 2.5 秒）、歪曲して爆ぜる */
        private void tickMines(ServerLevel server) {
            if (t == 0) {
                spots.clear();
                Vec3 ahead = fort.leadPoint(target, 40); // 爆ぜる頃に相手がいる所を真ん中に
                for (int i = 0; i < 8; i++) {
                    double a = fort.random.nextDouble() * Math.PI * 2, r = i == 0 ? 0.0 : i == 1 ? 1.5 : 3.0 + fort.random.nextDouble() * 9.0;
                    Vec3 base = i == 1 ? target.position() : ahead;
                    spots.add(base.add(Math.cos(a) * r, 0.1, Math.sin(a) * r));
                }
                fort.level().broadcastEntityEvent(fort, EVENT_MISSILE_RIGHT);
                fort.playSound(SoundEvents.DISPENSER_LAUNCH, 3.0F, 0.5F);
            }
            if (t < 50 && t % 5 == 0) for (Vec3 s : spots) Fx.mandala(server, s, t > 35 ? C_WHITE : C_CRIMSON, 3.0F, 6, false, t % 10 == 0);
            if (t == 50) {
                for (Vec3 s : spots) {
                    fort.blast(server, s, 3.0, MINE, C_CRIMSON);
                    Fx.line(server, glow(C_WHITE, 0.6F, 10), s, s.add(0, 6, 0), 0.5);
                }
                fort.playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.6F);
                fort.playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.6F);
            }
            if (t >= 56) finish();
        }

        /** 砲台の展開: 対空砲 2 基と曲射砲 2 基を、まわり 8 ブロックへ降ろす */
        private void tickTurrets(ServerLevel server) {
            if (t == 0) {
                fort.playSound(SoundEvents.PISTON_EXTEND, 3.0F, 0.5F);
                fort.message(server, "message.automata_valkyries.galliafort_ex.turrets", ChatFormatting.GOLD);
            }
            if (t == 10) {
                int room = TURRET_MAX - fort.turrets.size();
                for (int i = 0; i < Math.min(4, room); i++) {
                    Mob m = i % 2 == 0 ? ModEntities.PULSE_PHALANX.get().create(server) : ModEntities.SIEGE_CANNON.get().create(server);
                    if (m == null) continue;
                    double a = Math.PI * 2 * i / 4 + Math.PI / 4 + Math.toRadians(fort.getYRot());
                    Vec3 p = fort.position().add(Math.cos(a) * 8.0, 0.5, Math.sin(a) * 8.0);
                    m.moveTo(p.x, p.y, p.z, fort.getYRot(), 0.0F);
                    if (targetOk()) m.setTarget(target);
                    server.addFreshEntity(m);
                    fort.turrets.add(m);
                    Fx.line(server, glow(C_EMBER, 0.5F, 12), p.add(0, 10, 0), p, 0.6);
                    Fx.shockwave(server, p, C_EMBER, 3.0F, 12);
                }
                fort.playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.6F);
            }
            if (t >= 20) finish();
        }

        /** 扇状斉射: 主砲で扇に 5 連射（相手を中心に ±32 度。1 発ずつ予告線 0.4 秒） */
        private void tickFan(ServerLevel server) {
            if (t == 0) {
                fort.level().broadcastEntityEvent(fort, EVENT_CANNON_CHARGE);
                fort.playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.5F);
                dir = fort.aimFor(target).subtract(fort.muzzle()).normalize();
            }
            int shot = (t - 6) / 12, at = (t - 6) % 12;
            if (at == 0 && shot < 5) dir = fort.aimFor(target).subtract(fort.muzzle()).normalize(); // 1 発ごとに読み直す
            if (t >= 6 && shot < 5) {
                float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F + (shot - 2) * 16.0F * (shot % 2 == 0 ? 1 : -1);
                float pitch = (float) -(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG);
                Vec3 d = Vec3.directionFromRotation(pitch, yaw);
                turnToward(fort.muzzle().add(d.scale(10)), 8.0F);
                if (at < 8 && at % 2 == 0) fort.cannonTelegraph(d, at >= 4);
                if (at == 8) fort.fireCannon(d);
            }
            if (t >= 6 + 5 * 12) finish();
        }

        /** 背後への対応: 急旋回して（1 Tick に 15 度）、まわりをなぎ払い、衝撃波を広げる */
        private void tickShakeOff(ServerLevel server) {
            LivingEntity e = targetOk() ? target : fort.getTarget();
            if (t == 0) {
                fort.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.5F);
                fort.message(server, "message.automata_valkyries.galliafort_ex.shake_off", ChatFormatting.GOLD);
            }
            if (t < 8 && e != null) turnToward(aimAt(e), 15.0F);
            if (t == 8) {
                fort.level().broadcastEntityEvent(fort, EVENT_SWEEP);
                hit.clear();
            }
            if (t == 12) fort.sweepHit();
            if (t > 12 && (t - 12) % 5 == 0 && t <= 22) fort.quake(fort.position(), (t - 12) / 5 * 5.0 + 4.0, hit);
            if (t >= 26) {
                fort.behindTicks = 0;
                finish();
            }
        }

        /** リミットオーバー歪曲破界砲: 脚を打ち込む → 照射し続けてゆっくり追う → 前方の扇が超大歪曲で爆ぜる */
        private void tickLimit(ServerLevel server) {
            if (t == 0) {
                fort.setBarrierOff(true); // 障壁を砲へ注ぐ
                fort.level().broadcastEntityEvent(fort, EVENT_ULT_BRACE);
                fort.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.4F);
                fort.message(server, "message.automata_valkyries.galliafort_ex.limit", ChatFormatting.DARK_RED);
                Fx.shockwave(server, fort.position().add(0, 0.1, 0), C_EMBER, 12.0F, 20);
                dir = targetOk() ? aimAt(target).subtract(fort.muzzle()).normalize() : Vec3.directionFromRotation(0, fort.getYRot());
            }
            if (t < LIMIT_BRACE) {
                if (targetOk()) turnToward(aimAt(target), 3.0F);
                Fx.gather(server, glow(t % 2 == 0 ? C_CRIMSON : C_EMBER, 0.5F, 12), fort.muzzle(), 6, 6.0, 10);
                return;
            }
            int b = t - LIMIT_BRACE;
            if (b < LIMIT_BEAM) {
                if (targetOk()) { // ゆっくり追う（向きを 1 Tick に LIMIT_TURN 度まで。動きを読んだ所へ）
                    Vec3 want = fort.aimFor(target).subtract(fort.muzzle()).normalize();
                    double ang = Math.acos(Mth.clamp(dir.dot(want), -1.0, 1.0));
                    double max = Math.toRadians(LIMIT_TURN);
                    dir = ang <= max ? want : dir.scale(Math.sin(ang - max)).add(want.scale(Math.sin(max))).scale(1.0 / Math.sin(ang)).normalize();
                }
                turnToward(fort.muzzle().add(dir.scale(10)), 2.0F);
                Vec3 from = fort.muzzle();
                if (beam == null || !beam.isAlive()) {
                    beam = GalliaSiegeBeamEntity.spawnHeld(server, from, dir, (float) LIMIT_RANGE, 2.0F, GalliaSiegeBeamEntity.MODE_RIFT, LIMIT_BEAM - b);
                    fort.level().broadcastEntityEvent(fort, EVENT_ULT_FIRE);
                    fort.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.4F);
                } else {
                    beam.aim(from, dir);
                }
                if (b % 4 == 0) { // 光の束の中の相手へ
                    for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(from, from.add(dir.scale(LIMIT_RANGE))).inflate(LIMIT_WIDTH + 1.0), ChronoGazer::canHurt)) {
                        Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                        double along = c.subtract(from).dot(dir);
                        if (along < 0 || along > LIMIT_RANGE) continue;
                        if (c.subtract(from.add(dir.scale(along))).length() > LIMIT_WIDTH + e.getBbWidth() * 0.5) continue;
                        e.hurt(fort.siegeDamage(), fort.damageFor(e, LIMIT_TICK));
                    }
                }
                if (b % 3 == 0) {
                    Vec3 tip = from.add(dir.scale(LIMIT_RANGE));
                    var clip = fort.level().clip(new net.minecraft.world.level.ClipContext(from, tip, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, fort));
                    Vec3 hitAt = clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? tip : clip.getLocation();
                    Fx.burst(server, glow(C_CRIMSON, 0.8F, 14), hitAt, 10, 0.3);
                    server.sendParticles(ParticleTypes.EXPLOSION, hitAt.x, hitAt.y, hitAt.z, 1, 0, 0, 0, 0);
                }
                if (b % 20 == 0) fort.playSound(SoundEvents.BEACON_AMBIENT, 4.0F, 0.5F + b / (float) LIMIT_BEAM);
                if (b == LIMIT_BEAM - 30) fort.message(server, "message.automata_valkyries.galliafort_ex.distort", ChatFormatting.DARK_RED);
                if (b >= LIMIT_BEAM - 30 && b % 3 == 0) { // 超大歪曲の予告: 前方の扇
                    for (int k = -4; k <= 4; k++) {
                        Vec3 d = Vec3.directionFromRotation(0, fort.getYRot() + k * DISTORT_ARC / 8.0F);
                        Fx.line(server, glow(C_CRIMSON, 0.4F, 5), fort.position().add(0, 0.2, 0).add(d.scale(3)), fort.position().add(0, 0.2, 0).add(d.scale(DISTORT_RANGE)), 2.5);
                    }
                }
                return;
            }
            if (b == LIMIT_BEAM) { // 超大歪曲
                Vec3 at = fort.position();
                Vec3 fwd = Vec3.directionFromRotation(0, fort.getYRot());
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, fort.getBoundingBox().inflate(DISTORT_RANGE, 12.0, DISTORT_RANGE), ChronoGazer::canHurt)) {
                    Vec3 to = e.position().subtract(at).multiply(1, 0, 1);
                    if (to.length() > DISTORT_RANGE || to.length() < 0.5 || to.normalize().dot(fwd) < Math.cos(Math.toRadians(DISTORT_ARC / 2))) continue;
                    e.hurt(fort.siegeDamage(), fort.damageFor(e, DISTORT));
                    e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(1.5)).add(0, 0.8, 0));
                    e.hurtMarked = true;
                }
                for (double d = 6.0; d <= DISTORT_RANGE; d += 6.0) {
                    for (int k = -3; k <= 3; k++) {
                        Vec3 p = at.add(Vec3.directionFromRotation(0, fort.getYRot() + k * DISTORT_ARC / 6.0F).scale(d));
                        Fx.shockwave(server, p.add(0, 0.2, 0), k % 2 == 0 ? C_CRIMSON : C_WHITE, (float) (2.0 + d * 0.12), 20);
                        if (k % 2 == 0) server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
                    }
                }
                Fx.mandala(server, at.add(fwd.scale(DISTORT_RANGE / 2)).add(0, 0.2, 0), C_CRIMSON, (float) DISTORT_RANGE / 2, 40, false, true);
                server.sendParticles(ParticleTypes.FLASH, at.x, at.y + 3, at.z, 3, 4, 2, 4, 0);
                fort.playSound(SoundEvents.GENERIC_EXPLODE, 4.0F, 0.4F);
                fort.playSound(SoundEvents.WARDEN_SONIC_BOOM, 4.0F, 0.3F);
            }
            if (b >= LIMIT_BEAM + 30) {
                fort.setBarrierOff(false);
                finish();
            }
        }
    }
}
