package com.automatavalkyries.entity;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** サンプルボス「エレノア」。eprotect による保護対象、終焉の権能（ITerminusBearer）を搭載可能 */
public class ElenorEntity extends PathfinderMob implements IProviderWithSpeech, GeoEntity, ITerminusBearer, RetreatableBoss {

    private boolean terminusEnabled = false;
    private int lastFlinch = -100;

    /** 正規撃破から消滅までの Tick 数（モデル側の座り込み演出もこれに合わせる） */
    public static final int DEATH_TICKS = 30;

    private final Eprotect protect = new Eprotect(this);
    /** 画面上部のボスバー（HP の見える化）。追跡中のプレイヤーだけに表示する */
    private final ServerBossEvent bossEvent =
            new ServerBossEvent(Component.translatable("entity.automata_valkyries.elenor"), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_10);
    /** 挑む相手（48 ブロック以内）がいなくなって 10 秒で撤収する */
    private final BossRetreatWatch retreatWatch = new BossRetreatWatch(48.0D, 200);

    /** 3 フェーズの戦い方（{@link ElenorArts}）。使わない型（最果てのエレノア）では null */
    private final ElenorArts arts = usesBossArts() ? new ElenorArts(this) : null;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.elenor.idle");
    /** スカートをふわっと広げる（サーバーから triggerAnim で再生） */
    private static final RawAnimation SKIRT_SPREAD = RawAnimation.begin().thenPlay("animation.elenor.skirt_spread");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public ElenorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    /**
     * 敵対探知（2026-10-23）。他 MOD（砂糖の防具・保護アイテムなど）がターゲットを外したり、探索の判定をすり抜けたりしても、
     * 近くに戦える相手がいる間は、0.5 秒ごとに攻撃対象へ戻す。バニラの探索は上下 ±4m しか見ず、透明な相手は視認距離が縮むので、
     * ここでは透明化を無視して、3 次元の {@link #HOSTILE_RANGE} m を直接探す。Madness Entities のような非 Entity 型のボス
     * （月・古代ネザー遺跡の残骸など。生きていない扱いのプロキシ）も、{@link com.automatavalkyries.annihilate.MadnessAnnihilation#isMadness} で拾う
     */
    private void reacquireTarget() {
        if (tickCount % 10 != 0 || !isAlive() || this.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        if (!hasFreeWill()) return; // 自分で相手を探す仕組み（ゴール）を外された個体（GameTest の置き物など）は、自分では狙わない
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        LivingEntity current = getTarget();
        if (current != null && isHostileCandidate(current)) return;
        LivingEntity nearest = null;
        double best = HOSTILE_RANGE * HOSTILE_RANGE;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HOSTILE_RANGE), this::isHostileCandidate)) {
            double d = this.distanceToSqr(e);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest != null) setTarget(nearest);
    }

    /**
     * 追いかけてよい相手か: 座標が数（NaN・無限大でない）で、同じワールドの {@link #HOSTILE_RANGE} の 1.5 倍以内。
     * 遠くへ瞬間移動する相手（フラッシュファーなど）を技で追いかけて、エレノアが安全でない位置へ向かい続け、どこかへ行ってしまった（2026-10-23）
     */
    public boolean isReachable(net.minecraft.world.entity.Entity e) {
        if (e.level() != this.level()) return false;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        double limit = HOSTILE_RANGE * 1.5D;
        return this.distanceToSqr(e) <= limit * limit;
    }

    /** ボスバーを見ているプレイヤー（ティアの表示を送る相手） */
    public java.util.Collection<net.minecraft.server.level.ServerPlayer> bossBarViewers() {
        return bossEvent.getPlayers();
    }

    /** 自分で相手を探す仕組み（狙いのゴール）があるか。GameTest の removeFreeWill で外された個体は false */
    public boolean hasFreeWill() {
        return !this.targetSelector.getAvailableGoals().isEmpty();
    }

    /** 敵対探知の範囲（m） */
    static final double HOSTILE_RANGE = 96.0D;

    /**
     * 敵として狙える相手か: 戦えるプレイヤー、または、危険なフラグを持つ・最近エレノアを攻撃したエンティティ
     * （生きていない扱いの Madness のプロキシも含む。このMOD の守られたエンティティ（兵姫など）は除く）
     */
    boolean isHostileCandidate(LivingEntity e) {
        if (e == null || e == this || e instanceof ElenorEntity) return false;
        if (!isReachable(e)) return false;
        if (e instanceof Player player) return isFightable(player);
        if (e instanceof com.automatavalkyries.protect.IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget()) return false;
        if (!e.isAlive() && !com.automatavalkyries.annihilate.MadnessAnnihilation.isMadness(e)) return false;
        if (arts != null && arts.pressure().isViciousAttacker(e)) return true; // 凶悪技術を使ってきた相手（即死系の操作は、被弾として残らない）
        return e == getLastHurtByMob() || com.automatavalkyries.annihilate.FlagKillPredicates.hasDangerousFlag(e);
    }

    /** サバイバル・アドベンチャーの生きたプレイヤーか（クリエイティブ・スペクテイターは対象にしない） */
    private static boolean isFightable(LivingEntity e) {
        return e instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    /** 他 MOD が付けた無敵フラグで canBeSeenAsEnemy が偽でも、戦える相手は攻撃対象にできる */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (isFightable(target) && this.level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) return true;
        // 最近エレノアを攻撃したモブは、無敵でも、敵対できる（試練のモノリスなど）
        if (target != null && target == getLastHurtByMob() && tickCount - getLastHurtByMobTimestamp() < 600 && target.isAlive()) return true;
        return super.canAttack(target);
    }

    /** 最初から全開か（最果てのエレノア。コンストラクタの前から呼ばれるので、定数で答える） */
    protected boolean fullPower() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 受けたダメージを記録し、過剰な攻撃・即死を感知して、スケーリングで割り引く（ElenorPressure）
        if (arts != null && !this.level().isClientSide) amount = arts.pressure().onHurt(source, amount);
        // 受けたときの反応: 花びらと火花、ときどき（1.5 秒に 1 回）のけぞる
        if (this.level() instanceof net.minecraft.server.level.ServerLevel server && amount > 0.0F && Float.isFinite(amount) && isAlive()) {
            ElenorFx.hitReaction(server, position().add(0, 1.2, 0));
            if (tickCount - lastFlinch > 30) {
                lastFlinch = tickCount;
                triggerAnim("action", "flinch");
            }
        }
        if (!this.level().isClientSide && source.getEntity() instanceof Player attacker && isFightable(attacker)) this.setTarget(attacker); // 殴られたら無敵持ちでも敵対
        return super.hurt(source, amount);
    }

    /** 技（{@link ElenorArts}）で戦うか。最果てのエレノアは終焉の権能で戦うので使わない（コンストラクタの前から呼ばれるので、定数で答える） */
    protected boolean usesBossArts() {
        return true;
    }

    @org.jetbrains.annotations.Nullable
    public ElenorArts arts() {
        return arts;
    }

    /** フェーズが進んだら、ボスバーの色を変える（第 2: ピンク、第 3: 赤） */
    void setBarPhase(int phase) {
        bossEvent.setColor(phase >= 3 ? BossEvent.BossBarColor.RED : phase == 2 ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.PURPLE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // usesBossArts は定数（ここはサブクラスのフィールドが出来上がる前に呼ばれる）
        this.goalSelector.addGoal(2, usesBossArts() ? new ElenorArts.ArtsGoal(this) : new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // 敵対は危険なフラグ（即死・消滅・無敵・生命維持・防御貫通等）を所有しているMOBに限定！
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                com.automatavalkyries.protect.flag.FlagDictionary::hasDangerousFlag));
        // 無敵フラグ（砂糖の防具・トライアルモノリスの保護など）で canBeSeenAsEnemy が偽になるプレイヤーも敵として探す
        NearestAttackableTargetGoal<Player> players = new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, ElenorEntity::isFightable) {
            {
                this.targetConditions = net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat()
                        .range(this.getFollowDistance()).selector(ElenorEntity::isFightable);
            }
        };
        this.targetSelector.addGoal(3, players);
    }

    /**
     * 兵姫が索敵・交戦すべき敵対エンティティ（危険なフラグを所有しているMOB）の判定。
     */
    public static boolean isHostileOrBossMob(LivingEntity entity) {
        return com.automatavalkyries.protect.flag.FlagDictionary.hasDangerousFlag(entity);
    }

    @Override
    public void restoreGoalsIfEmpty() {
        if (this.goalSelector.getAvailableGoals().isEmpty()) {
            registerGoals();
        }
    }

    // ---- eprotect への委譲 ---------------------------------------------------

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        protect.onAddedToWorld();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        protect.onRemovedFromWorld();
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (!this.level().isClientSide) {
            protect.tick(); // 撃破後の演出が進まないときの代行もここ（Eprotect#tick）
            tickRetreat();
            // 同じ UUID で作り直す相手（フラッシュファーなど）は、ワールドに登録されている本体を狙い直す（古い複製を狙うと技が当たらない）
            if (getTarget() != null && this.level() instanceof net.minecraft.server.level.ServerLevel server
                    && server.getEntity(getTarget().getUUID()) instanceof LivingEntity live && live != getTarget()) {
                setTarget(live);
            }
            // 追いかけられない相手（座標がおかしい・遠すぎる）は狙いから外す。速度が数でなくなっていたら止める
            if (getTarget() != null && !isReachable(getTarget())) setTarget(null);
            net.minecraft.world.phys.Vec3 motion = getDeltaMovement();
            if (!Double.isFinite(motion.x) || !Double.isFinite(motion.y) || !Double.isFinite(motion.z)) setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            reacquireTarget();
            if (arts != null) {
                arts.tickProjectiles();
                arts.pressure().tick();
            }
            // 実際の HP で出す（他 MOD のエージェントが getHealth を書き換えて最大 HP を返すと、削れていても満タンに見えていた。2026-10-05）
            bossEvent.setProgress(protect.healthRatio());
        } else {
            protect.clientTick();
        }
    }

    /** 回想のトライアルのボス（{@code trial/TrialManager}）: 自分では退かず、保存もしない */
    private boolean trialBoss = false;

    public void setTrialBoss(boolean trialBoss) {
        this.trialBoss = trialBoss;
    }

    @Override
    public boolean shouldBeSaved() {
        return !trialBoss && super.shouldBeSaved();
    }

    /** 正規の退場（セリフと効果音を出して消える）。回想のトライアルの失敗で使う（{@code trial/TrialManager}） */
    public void retreat() {
        protect.triggerDespawn();
    }

    /** 撤退の号令: 正規の退場（セリフと効果音） */
    @Override
    public boolean beginRetreat() {
        if (!isAlive() || protect.isDespawning()) return false;
        retreat();
        return true;
    }

    // ---- ボスバー --------------------------------------------------------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    /** 別個体に差し替えられた古い個体のボスバーを片付ける（新しい個体が自分のボスバーを出す） */
    @Override
    public void onRetired() {
        bossEvent.removeAllPlayers();
    }

    @Override
    public void remove(RemovalReason reason) {
        // setRemoved は Mixin でも塞いでいるが、ここで止めれば invalidateCaps 等の後処理も走らない
        if (protect.shouldBlockRemoval(reason)) return;
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        // hurt() を経ない die() の直接呼び出し（即死攻撃）は断り、健全な HP へ戻す（凶悪技術なら罰則で HP を最大へ）
        if (protect.interceptDie()) {
            if (arts != null) arts.pressure().onInstantKillRefused(source); // 即死を感知（ペナルティのティアが上がりやすくなる）
            return;
        }
        super.die(source);
    }

    /**
     * 正規撃破時の演出。バニラの「横倒し 20 Tick」の代わりに、座り込み（モデル側）→ 光の粒 → 消滅を {@link #DEATH_TICKS} かけて行う。
     */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.level().isClientSide) {
            spawnDeathParticles();
        } else if (this.deathTime >= DEATH_TICKS && !com.automatavalkyries.protect.DirectRead.isRemoved(this)) {
            this.level().broadcastEntityEvent(this, (byte) 60); // 消滅時の煙
            protect.finishDeath(); // ここで初めて消去を許可（撃破演出中の強制消去は eprotect が拒否）
            this.remove(RemovalReason.KILLED);
        }
    }

    private void spawnDeathParticles() {
        // 座り込んだ後（約 0.6 秒後）から、体の周りに紫の光の粒が舞い上がる
        if (this.deathTime < 12) return;
        int count = this.deathTime >= DEATH_TICKS - 2 ? 12 : 2;
        for (int i = 0; i < count; i++) {
            double x = this.getX() + (this.random.nextDouble() - 0.5D) * 1.2D;
            double y = this.getY() + this.random.nextDouble() * 1.0D;
            double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 1.2D;
            this.level().addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WITCH,
                    x, y, z, 0.0D, 0.03D + this.random.nextDouble() * 0.04D, 0.0D);
        }
        // 頭のリボンがほどけて、黒とピンクの 2 本の帯が螺旋を描いて舞い上がる（2026-10-22）
        double rise = (this.deathTime - 12) / (double) (DEATH_TICKS - 12);
        for (int strand = 0; strand < 2; strand++) {
            double angle = this.deathTime * 0.35 + strand * Math.PI;
            double radius = 0.35 + rise * 0.6;
            this.level().addParticle(strand == 0 ? ElenorArts.RIBBON_PINK : ElenorArts.RIBBON_BLACK,
                    this.getX() + Math.cos(angle) * radius, this.getY() + 1.4D + rise * 1.6D, this.getZ() + Math.sin(angle) * radius,
                    0.0D, 0.0D, 0.0D);
        }
        if (this.deathTime == DEATH_TICKS - 2) {
            for (int i = 0; i < 24; i++) {
                this.level().addParticle(i % 2 == 0 ? ElenorArts.RIBBON_PINK : ElenorArts.RIBBON_BLACK,
                        this.getX() + (this.random.nextDouble() - 0.5D) * 1.6D, this.getY() + 1.0D + this.random.nextDouble() * 1.5D,
                        this.getZ() + (this.random.nextDouble() - 0.5D) * 1.6D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ---- 撤収（プレイヤー全滅時） --------------------------------------------

    private void tickRetreat() {
        if (trialBoss) return; // 回想のトライアルのボスは、トライアルの側が退かせる（クリエイティブで試しても自分で消えない）
        if (retreatWatch.tick(this)) protect.triggerDespawn();
    }

    // ---- 終焉権能 (ITerminusBearer) ----------------------------------------

    @Override
    public boolean isTerminusEnabled() {
        return this.terminusEnabled;
    }

    @Override
    public void setTerminusEnabled(boolean enabled) {
        this.terminusEnabled = enabled;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 試練の砂時計を持っているときは、しゃがみ右クリックを砂時計に譲る（終焉の許可の切り替えはしない）
        if (player.getItemInHand(hand).getItem() instanceof com.automatavalkyries.item.TrialHourglassItem) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && hand != InteractionHand.MAIN_HAND) return InteractionResult.sidedSuccess(this.level().isClientSide); // オフハンドの 2 回目では切り替えない
        if (player.isShiftKeyDown()) {
            if (this.level().isClientSide) return InteractionResult.SUCCESS; // クライアントも「処理した」と返す（返さないと、続けてオフハンドの右クリックが送られ、2 回切り替わっていた）
            this.terminusEnabled = !this.terminusEnabled;
            Component msg = this.terminusEnabled
                    ? Component.translatable("message.automata_valkyries.elenor.terminus_enabled").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    : Component.translatable("message.automata_valkyries.elenor.terminus_disabled").withStyle(ChatFormatting.GRAY);
            player.displayClientMessage(msg, true);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    this.terminusEnabled ? SoundEvents.WITHER_SPAWN : SoundEvents.EXPERIENCE_ORB_PICKUP,
                    this.getSoundSource(), 1.0F, this.terminusEnabled ? 0.6F : 1.2F);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    // ---- GeckoLib -----------------------------------------------------------

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.terminusEnabled && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            triggerAnim("action", "skirt_spread");
            return com.automatavalkyries.annihilate.TerminusEngine.executeTerminus(serverLevel, this, target);
        }
        boolean hit = super.doHurtTarget(target);
        if (hit) triggerAnim("action", "skirt_spread");
        return hit;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 5, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("skirt_spread", SKIRT_SPREAD)
                .triggerableAnim("lunge", RawAnimation.begin().thenPlay("animation.elenor.lunge"))
                .triggerableAnim("cast", RawAnimation.begin().thenPlay("animation.elenor.cast"))
                .triggerableAnim("point", RawAnimation.begin().thenPlay("animation.elenor.point"))
                .triggerableAnim("spin", RawAnimation.begin().thenPlay("animation.elenor.spin"))
                .triggerableAnim("curtsy", RawAnimation.begin().thenPlay("animation.elenor.curtsy"))
                .triggerableAnim("flinch", RawAnimation.begin().thenPlay("animation.elenor.flinch")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ---- IProviderWithSpeech ------------------------------------------------

    @Override
    public Eprotect getEprotect() {
        return protect;
    }

    @Override
    public Component getDefeatSpeech() {
        return Component.translatable("entity.automata_valkyries.elenor.defeat_speech")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
    }

    @Override
    public SoundEvent getDefeatSound() {
        return SoundEvents.PLAYER_LEVELUP;
    }
}
