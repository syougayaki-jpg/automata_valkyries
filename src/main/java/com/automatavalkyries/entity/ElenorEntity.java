package com.automatavalkyries.entity;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
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

/** サンプルボス「エレノア」。eprotect による保護対象 */
public class ElenorEntity extends PathfinderMob implements IProviderWithSpeech, GeoEntity {

    /** この範囲に戦えるプレイヤーがいなくなったら「全滅」とみなす */
    private static final double ENGAGE_RANGE = 48.0D;
    /** 全滅判定から撤収までの猶予（Tick） */
    private static final int DESPAWN_DELAY = 200;
    private static final int CHECK_INTERVAL = 20;
    /** 正規撃破から消滅までの Tick 数（モデル側の座り込み演出もこれに合わせる） */
    public static final int DEATH_TICKS = 30;

    private final Eprotect protect = new Eprotect(this);
    /** プレイヤーと交戦したか */
    private boolean engaged = false;
    private int noPlayerTicks = 0;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.elenor.idle");
    /** スカートをふわっと広げる（サーバーから triggerAnim で再生） */
    private static final RawAnimation SKIRT_SPREAD = RawAnimation.begin().thenPlay("animation.elenor.skirt_spread");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public ElenorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
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
            protect.tick();
            tickRetreat();
        } else {
            protect.clientTick();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // setRemoved は Mixin でも塞いでいるが、ここで止めれば invalidateCaps 等の後処理も走らない
        if (protect.shouldBlockRemoval(reason)) return;
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        // hurt() を経ない die() の直接呼び出し（即死攻撃）は無視する
        if (!protect.canDie()) return;
        protect.markDefeated();
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
        } else if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
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
        if (this.getTarget() instanceof Player) engaged = true;
        if (!engaged || this.tickCount % CHECK_INTERVAL != 0) return;

        boolean anyChallenger = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), ENGAGE_RANGE,
                EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE)) != null;
        if (anyChallenger) {
            noPlayerTicks = 0;
            return;
        }
        noPlayerTicks += CHECK_INTERVAL;
        if (noPlayerTicks >= DESPAWN_DELAY) {
            protect.triggerDespawn();
        }
    }

    // ---- GeckoLib -----------------------------------------------------------

    @Override
    public boolean doHurtTarget(Entity target) {
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
