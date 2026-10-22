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
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
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
            if (arts != null) arts.tickProjectiles();
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
        if (protect.interceptDie()) return;
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
        if (player.isShiftKeyDown() && !this.level().isClientSide) {
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
                .triggerableAnim("skirt_spread", SKIRT_SPREAD));
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
