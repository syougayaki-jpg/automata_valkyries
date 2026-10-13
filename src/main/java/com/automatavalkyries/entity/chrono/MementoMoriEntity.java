package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * AEC 級英雄機「弾幕特化【黒死蝶 / メメントモリ】」（全体仕様書 §4.1。2026-10-10 ユーザーへの質問で決定。数値は【仮】）。
 * 背に蝶の翅を持つ細身の巨大な女性型のアンドロイド（身長約 5.5 ブロック、紫の半透明の翅を広げて約 10 ブロック。2026-10-13 ユーザー指定で蝶の姿から作り直し、同日ユーザーの参考画像を元に）。いつも相手の上空 {@link #HOVER_HEIGHT} ブロックをゆらゆらと円を描いて舞う（飛び道具・射撃で戦う相手）。
 * HP {@value #MAX_HP}、HP 50% で第 2 形態（翼が大きく開き、紋様が赤く光る。弾幕が激しくなり、大技が増える）。
 * 技:
 * <ul>
 *   <li>黒死蝶の乱舞: 翼から蝶ビットを次々に放ち、群れでゆるく追わせる（{@link MementoBitEntity}。当たると {@value MementoBitEntity#DAMAGE} と最大 HP を 5% 削る呪い）</li>
 *   <li>死蝶の幾何弾幕: 相手の方へ、輪・渦巻き・花の模様を描く蝶弾（{@link MementoBulletEntity}。1 発 {@value #BULLET}。最大 HP は削らない）</li>
 *   <li>蝶の檻と一斉突撃: 蝶ビットが相手のまわりを 2 段の輪で囲み、2 秒後の合図で一斉に突っ込む（ビットを壊して逃げ道を作る）</li>
 *   <li>黒死の月（第 2 形態）: 舞い上がって翼で相手のまわり半径 {@value #MOON_RADIUS} を覆い、5 秒後に光の当たる安全地帯 {@value #MOON_SAFE} つの外を死の鱗粉で納める
 *       （{@value #MOON} と呪い 2 つ分）。見た目は {@link MementoMoonEntity}</li>
 * </ul>
 * 召喚はルクシオン・ガリアフォートと同じ（回想のトライアル「死蝶の舞踏」と英雄機の呼び声）。挑む相手がいなくなると退く
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class MementoMoriEntity extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech, com.automatavalkyries.entity.RetreatableBoss {

    public static final float MAX_HP = 15000.0F, BULLET = 6.0F, MOON = 40.0F;
    public static final float HOVER_HEIGHT = 10.0F, ORBIT = 13.0F, MOON_RADIUS = 22.0F, MOON_SAFE_RADIUS = 2.6F;
    public static final int MOON_SAFE = 4, MOON_COUNTDOWN = 100;
    /** 体の大きさ（モデルの倍率。描画 client/MementoMoriRenderer）。当たり判定は ModEntities で幅 1.6・高さ 5.4 */
    public static final float SIZE = 2.0F;
    /** 胸の高さ（足元から。演出の中心） */
    static final double CHEST = 3.6;
    public static final byte STAGE_NONE = 0, STAGE_INTRO = 1, STAGE_AWAKEN = 2, STAGE_RETREAT = 3;
    public static final int INTRO_TICKS = 50, AWAKEN_TICKS = 40, RETREAT_TICKS = 50, DEFEAT_TICKS = 70;
    /** 動きの合図（描画）: 乱舞・弾幕・檻・檻の突撃・月の構え・月の納め */
    public static final byte EVENT_SWARM = 70, EVENT_BARRAGE = 71, EVENT_CAGE = 72, EVENT_CAGE_RELEASE = 73, EVENT_MOON = 74, EVENT_MOON_FALL = 75;
    static final int C_VIOLET = 0xA45CFF, C_CRIMSON = 0xFF2A4A, C_PALE = 0xE8D0FF, C_WHITE = 0xFFFFFF;   // 紫と淡い紫（2026-10-13 に参考画像に合わせて青紫から）

    static com.automatavalkyries.particle.FxOptions glow(int color, float size, int life) {
        return Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), color, size, life);
    }

    private static final EntityDataAccessor<Boolean> PHASE2 = SynchedEntityData.defineId(MementoMoriEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> STAGE = SynchedEntityData.defineId(MementoMoriEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(MementoMoriEntity.class, EntityDataSerializers.BOOLEAN);

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    private final com.automatavalkyries.entity.BossRetreatWatch retreatWatch = new com.automatavalkyries.entity.BossRetreatWatch(64.0, 200);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.automata_valkyries.memento_mori"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private boolean trialBoss, retreatPending, introDone, lootDropped;
    private float trialAttack = 1.0F;
    private int stageTicks;
    private float orbitAngle;
    /** クライアント: 合図の Tick（描画用）と、今の段階の始まり、撃破の演出の経過 Tick */
    public int swarmTick = -100, barrageTick = -100, cageTick = -100, cageReleaseTick = -100, moonTick = -100, moonFallTick = -100, stageStart, defeatTicks;
    /** 頭の後ろの光輪の回った量（クライアントだけ。技の間は速く回る。描画 client/ChronoGazerModel#animateMemento） */
    public float haloSpin, haloSpinO;

    public MementoMoriEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        setNoGravity(true); // 動きはゴールの steer で直に決める（travel は何もしない）
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE2, false);
        entityData.define(STAGE, STAGE_INTRO); // はじめから現れる演出の段階（ガリアフォートと同じ）
        entityData.define(DEFEATED, false);
    }

    public boolean isPhase2() {
        return entityData.get(PHASE2);
    }

    public boolean isDefeatedSynced() {
        return entityData.get(DEFEATED);
    }

    public byte stage() {
        return entityData.get(STAGE);
    }

    private void startStage(byte stage) {
        entityData.set(STAGE, stage);
        stageTicks = 0;
    }

    /** ボスバーの横顔用（描くだけの個体）: 第 2 形態か p2 で決め、段階は無し */
    public void portraitState(boolean p2) {
        entityData.set(PHASE2, p2);
        entityData.set(STAGE, STAGE_NONE);
        entityData.set(DEFEATED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STAGE.equals(key)) stageStart = tickCount;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDefeatedSynced();
    }

    private boolean defeatedNow() {
        return isDefeatedSynced() || deathTime > 0 || isDeadOrDying();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (defeatedNow()) return false;
        boolean bypass = source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        byte s = stage();
        if ((s == STAGE_INTRO || s == STAGE_AWAKEN) && !bypass) return false; // 現れる・目覚める間は受けない
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    // ---- 保存・ゴール ----

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Phase2", isPhase2());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        introDone = true;
        entityData.set(STAGE, STAGE_NONE);
        if (tag.getBoolean("Phase2")) entityData.set(PHASE2, true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MementoAttackGoal(this));
        ChronoGazer.addTargets(this, 40.0);
    }

    @Override
    public void restoreGoalsIfEmpty() {
        if (goalSelector.getAvailableGoals().isEmpty()) registerGoals();
    }

    @Override
    public Eprotect getEprotect() {
        return protect;
    }

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
    public void remove(RemovalReason reason) {
        if (protect.shouldBlockRemoval(reason)) return;
        super.remove(reason);
    }

    // ---- 撃破 ----

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) {
            protect.markDefeated();
            setHealth(0.0F);
            return;
        }
        if (protect.interceptDie()) return;
        entityData.set(DEFEATED, true);
        if (level() instanceof ServerLevel server) MementoCurse.releaseAround(server, position()); // 倒すと呪いが解ける
        super.die(source);
    }

    /** 落とし物のイベントの最後: 取り消されていたら戻し、空にされていたら撃破の演出の終わりに出し直す（ルクシオン・ガリアフォートと同じ） */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof MementoMoriEntity mori)) return;
        if (event.isCanceled()) event.setCanceled(false);
        if (!event.getDrops().isEmpty()) mori.lootDropped = true;
    }

    private void ensureLoot() {
        if (lootDropped || !(level() instanceof ServerLevel)) return;
        lootDropped = true;
        DamageSource source = getLastDamageSource() != null ? getLastDamageSource() : damageSources().generic();
        dropFromLootTable(source, getLastHurtByMob() instanceof Player);
    }

    /** 撃破: 後ろへ反って翅を垂らし、らせんを描いて落ち、最後に無数の蝶になって弾ける（3.5 秒） */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (!level().isClientSide && !entityData.get(DEFEATED)) entityData.set(DEFEATED, true);
        if (!level().isClientSide && stage() != STAGE_NONE) startStage(STAGE_NONE);
        if (level().isClientSide) {
            if (deathTime % 2 == 0) level().addParticle(ParticleTypes.SQUID_INK, getRandomX(2.0), getY() + 1.0 + random.nextDouble() * 5.0, getRandomZ(2.0), 0, -0.05, 0);
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        // らせんを描いて落ちる（地面の上 1 ブロックまで）
        double ground = groundY(position());
        double a = deathTime * 0.25;
        setDeltaMovement(Math.cos(a) * 0.15, getY() > ground + 1.0 ? -0.08 : 0.0, Math.sin(a) * 0.15);
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        if (deathTime % 5 == 0) Fx.burst(server, glow(random.nextBoolean() ? C_VIOLET : C_CRIMSON, 0.6F, 18), position().add(0, CHEST, 0), 8, 0.2);
        if (deathTime >= DEFEAT_TICKS && !DirectRead.isRemoved(this)) {
            Vec3 c = position().add(0, CHEST, 0);
            server.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 120, 2.0, 2.5, 2.0, 0.25);
            Fx.burst(server, glow(C_VIOLET, 1.0F, 30), c, 60, 0.45);
            Fx.burst(server, glow(C_CRIMSON, 0.8F, 30), c, 40, 0.35);
            Fx.shockwave(server, c, C_VIOLET, 12.0F, 26);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
            GalliaRiftEntity.spawn(server, c, 9.0F);
            playSound(SoundEvents.PHANTOM_DEATH, 3.0F, 0.5F);
            playSound(SoundEvents.GENERIC_EXPLODE, 2.5F, 0.8F);
            MementoCurse.releaseAround(server, position());
            ensureLoot();
            protect.finishDeath();
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public Component getDefeatSpeech() {
        return Component.translatable("entity.automata_valkyries.memento_mori.defeat_speech").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
    }

    @Override
    public SoundEvent getDefeatSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    // ---- 毎 Tick ----

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            if (entityData.get(DEFEATED)) {
                if (defeatTicks == 0) protect.markDefeated();
                defeatTicks++;
            }
            protect.clientTick();
            haloSpinO = haloSpin;
            boolean busy = tickCount - swarmTick < 40 || tickCount - barrageTick < 50 || tickCount - cageTick < 75;
            boolean big = tickCount - moonTick < 140 || stage() == STAGE_AWAKEN;
            haloSpin += isDefeatedSynced() ? 0.3F : big ? 4.0F : busy ? 2.5F : 1.0F;
            if (!isDefeatedSynced() && tickCount % 3 == 0) { // 翼から舞う鱗粉
                level().addParticle(ParticleTypes.SQUID_INK, getRandomX(2.0), getY() + 1.0 + random.nextDouble() * 5.5, getRandomZ(2.0), 0, -0.03, 0);
            }
            return;
        }
        protect.tick();
        float ratio = protect.healthRatio();
        bossEvent.setProgress(entityData.get(DEFEATED) || deathTime > 0 ? 0.0F : ratio);
        if (!introDone) {
            introDone = true;
            if (isAlive()) startStage(STAGE_INTRO);
        }
        tickStage();
        if (!isPhase2() && ratio <= 0.5F && isAlive() && !defeatedNow()) {
            entityData.set(PHASE2, true);
            startStage(STAGE_AWAKEN); // 翼が大きく開き、紋様が赤く光る（2 秒、攻撃を受けない）
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            playSound(SoundEvents.PHANTOM_AMBIENT, 3.0F, 0.4F);
            if (level() instanceof ServerLevel server) {
                for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 64 * 64)) {
                    p.displayClientMessage(Component.translatable("message.automata_valkyries.memento_mori.phase2").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
            }
        }
        if (!trialBoss) {
            if (retreatWatch.tick(this)) retreatPending = true;
            if (retreatPending && stage() == STAGE_NONE) beginRetreat();
        }
    }

    private void tickStage() {
        byte stage = stage();
        if (stage == STAGE_NONE || !(level() instanceof ServerLevel server)) return;
        int t = stageTicks++;
        if (stage == STAGE_INTRO) { // 黒い鱗粉の渦から舞い上がる
            if (t == 0) {
                playSound(SoundEvents.PHANTOM_FLAP, 3.0F, 0.5F);
                playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
            }
            for (int i = 0; i < 3; i++) {
                double a = (t * 0.4 + i * 2.1);
                Vec3 p = position().add(Math.cos(a) * (4.0 - t * 0.05), 0.3 + t * 0.12, Math.sin(a) * (4.0 - t * 0.05));
                server.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.01);
                Fx.one(server, glow(C_VIOLET, 0.5F, 14), p, Vec3.ZERO);
            }
            setDeltaMovement(0, 0.18, 0);                                           // 舞い上がる
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            if (t >= INTRO_TICKS) startStage(STAGE_NONE);
        } else if (stage == STAGE_AWAKEN) { // 翼が大きく開き、紋様が赤く光る
            setDeltaMovement(Vec3.ZERO);
            if (t == 10) {
                Fx.burst(server, glow(C_CRIMSON, 1.0F, 30), position().add(0, CHEST, 0), 60, 0.4);
                Fx.shockwave(server, position().add(0, CHEST, 0), C_CRIMSON, 12.0F, 24);
                server.sendParticles(ParticleTypes.FLASH, getX(), getY() + CHEST, getZ(), 1, 0, 0, 0, 0);
                playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.2F);
            }
            if (t >= AWAKEN_TICKS) startStage(STAGE_NONE);
        } else if (stage == STAGE_RETREAT) { // 高く舞い上がり、無数の蝶になって散る
            setDeltaMovement(0, 0.35, 0);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            server.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + CHEST, getZ(), 6, 2.0, 2.5, 2.0, 0.05);
            if (t % 4 == 0) Fx.burst(server, glow(C_VIOLET, 0.6F, 20), position().add(0, CHEST, 0), 6, 0.2);
            if (t >= RETREAT_TICKS) {
                startStage(STAGE_NONE);
                protect.triggerDespawn();
            }
        }
    }

    @Override
    public boolean beginRetreat() {
        if (!isAlive() || defeatedNow() || stage() == STAGE_RETREAT || protect.isDespawning()) return false;
        startStage(STAGE_RETREAT);
        playSound(SoundEvents.PHANTOM_FLAP, 2.0F, 0.6F);
        return true;
    }

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

    @Override
    public void onRetired() {
        bossEvent.removeAllPlayers();
    }

    /** 回想のトライアルのボスにする（自分では退かず、保存もしない。攻撃は attack 倍） */
    public void setTrialBoss(float attack) {
        this.trialBoss = true;
        this.trialAttack = attack;
    }

    @Override
    public boolean shouldBeSaved() {
        return !trialBoss && super.shouldBeSaved();
    }

    @Override
    public void setCustomName(@org.jetbrains.annotations.Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_SWARM -> swarmTick = tickCount;
            case EVENT_BARRAGE -> barrageTick = tickCount;
            case EVENT_CAGE -> cageTick = tickCount;
            case EVENT_CAGE_RELEASE -> cageReleaseTick = tickCount;
            case EVENT_MOON -> moonTick = tickCount;
            case EVENT_MOON_FALL -> moonFallTick = tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    // ---- 飛び方 ----

    /** at の真下の地面の高さ（最大 40 ブロック下まで） */
    double groundY(Vec3 at) {
        net.minecraft.world.phys.BlockHitResult hit = level().clip(new net.minecraft.world.level.ClipContext(at, at.subtract(0, 40, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, this));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? at.y - 40 : hit.getLocation().y;
    }

    /** 相手の上空 HOVER_HEIGHT（extraUp を足す）を、半径 ORBIT の円を描いてゆらゆら舞う（速さは speed まで） */
    void flyAround(LivingEntity t, float extraUp, double speed) {
        orbitAngle += 0.012F;
        Vec3 c = t.position();
        double r = ORBIT + Math.sin(tickCount * 0.03) * 2.0;
        double h = Math.max(c.y, groundY(c)) + HOVER_HEIGHT + extraUp + Math.sin(tickCount * 0.07) * 1.2;
        Vec3 want = new Vec3(c.x + Math.cos(orbitAngle) * r, h, c.z + Math.sin(orbitAngle) * r);
        steer(want, speed);
        faceToward(t.position());
    }

    void steer(Vec3 want, double speed) {
        Vec3 d = want.subtract(position());
        Vec3 v = getDeltaMovement().scale(0.85).add(d.lengthSqr() > 1.0e-4 ? d.normalize().scale(Math.min(speed, d.length()) * 0.15) : Vec3.ZERO);
        if (v.length() > speed) v = v.normalize().scale(speed);
        setDeltaMovement(v);
        move(net.minecraft.world.entity.MoverType.SELF, v);
    }

    void faceToward(Vec3 at) {
        float yaw = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        float body = Mth.approachDegrees(yBodyRot, yaw, 4.0F);
        setYRot(body);
        setYBodyRot(body);
        setYHeadRot(body);
    }

    @Override
    public void travel(Vec3 input) {
        // 動きはゴールの steer で決める（重力・地面の摩擦なし）
    }

    // ---- 技 ----

    /** 技のダメージ: プレイヤーには最大 HP に応じて上げ（ルクシオンと同じ）、回想のトライアルではさらに倍率を掛ける */
    float damageFor(LivingEntity target, float damage) {
        return LuxionEntity.scaledFor(target, damage * trialAttack);
    }

    /** 体の向きでの位置: 上へ up、前へ fwd、左へ left（ブロック） */
    Vec3 local(double up, double fwd, double left) {
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(0, up, 0).add(f.scale(fwd)).add(new Vec3(-f.z, 0, f.x).scale(left));
    }

    /** 黒死蝶の乱舞の 1 匹: 左右の前翅の中ほど（背の後ろ）から外・上へ放つ */
    void swarmBit(LivingEntity t, boolean left) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = local(5.5, -0.8, left ? 3.5 : -3.5);
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 side = new Vec3(-f.z, 0, f.x).scale(left ? 1 : -1);
        Vec3 vel = side.scale(0.35).add(0, 0.25, 0).add(f.scale(0.15)).add(random.nextGaussian() * 0.08, random.nextGaussian() * 0.08, random.nextGaussian() * 0.08);
        MementoBitEntity.swarm(server, this, t, from, vel, MementoBitEntity.DAMAGE, isPhase2());
        Fx.burst(server, glow(isPhase2() ? C_CRIMSON : C_VIOLET, 0.35F, 10), from, 4, 0.08);
    }

    /** 死蝶の幾何弾幕の 1 波: 相手を指す右手の先から、相手の方への円錐に count 発。pattern 0 = 輪（まっすぐ）、1 = 花（交互に逆へ曲がる）、2 = 渦巻きの 1 本（ang の向きの 1 発） */
    void barrageWave(LivingEntity t, int pattern, int count, double rot, double cone, double speed) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = local(4.0, 2.0, -0.5);
        Vec3 d = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = d.cross(up).normalize(), v = d.cross(u).normalize();
        boolean red = isPhase2();
        for (int i = 0; i < count; i++) {
            double th = rot + Math.PI * 2 * i / count;
            Vec3 dir = d.scale(Math.cos(cone)).add(u.scale(Math.cos(th) * Math.sin(cone))).add(v.scale(Math.sin(th) * Math.sin(cone))).normalize();
            float curl = pattern == 1 ? (i % 2 == 0 ? 0.045F : -0.045F) : 0.0F;
            MementoBulletEntity.fire(server, this, from, dir.scale(speed), d, curl, BULLET, red);
        }
    }

    /** 蝶の檻の並ぶ所: 相手のまわりの 2 段の輪（半径 5.5、足元から 0.6・2.2 ブロック） */
    static List<Vec3> cageSlots(LivingEntity t, int count) {
        List<Vec3> out = new ArrayList<>();
        int per = count / 2;
        for (int layer = 0; layer < 2; layer++) {
            for (int i = 0; i < per; i++) {
                double a = Math.PI * 2 * (i + layer * 0.5) / per;
                out.add(t.position().add(Math.cos(a) * 5.5, layer == 0 ? 0.6 : 2.2, Math.sin(a) * 5.5));
            }
        }
        return out;
    }

    /** 黒死の月の納め: center のまわり半径 MOON_RADIUS の、安全地帯 safe の外にいる相手に {@value #MOON} と呪い 2 つ分 */
    void moonFall(Vec3 center, List<Vec3> safe) {
        if (!(level() instanceof ServerLevel server)) return;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(MOON_RADIUS, 16.0, MOON_RADIUS), ChronoGazer::canHurt)) {
            if (e == this) continue;
            Vec3 p = e.position();
            if (p.subtract(center).multiply(1, 0, 1).length() > MOON_RADIUS) continue;
            boolean sheltered = false;
            for (Vec3 s : safe) if (p.subtract(s).multiply(1, 0, 1).length() <= MOON_SAFE_RADIUS + e.getBbWidth() * 0.5) sheltered = true;
            if (sheltered) continue;
            e.hurt(damageSources().indirectMagic(this, this), damageFor(e, MOON));
            MementoCurse.add(e, 2);
        }
        server.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y + 1.0, center.z, 300, MOON_RADIUS * 0.6, 1.5, MOON_RADIUS * 0.6, 0.05);
        Fx.shockwave(server, center.add(0, 0.2, 0), C_CRIMSON, MOON_RADIUS, 30);
        Fx.shockwave(server, center.add(0, 0.3, 0), C_VIOLET, MOON_RADIUS * 0.7F, 24);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.6F);
        playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_MOON_FALL);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ---- ゴール ----

    /**
     * 戦い方: 相手の上空を舞いながら、黒死蝶の乱舞・死蝶の幾何弾幕・蝶の檻を順に使う（第 2 形態は間隔が 7 割で、数が増え、黒死の月を足す）
     */
    static class MementoAttackGoal extends Goal {
        private final MementoMoriEntity mori;
        private int action, actionT, left;
        private int swarm = 60, barrage = 20, cage = 160, moon = 200;
        private int pattern;
        private final List<MementoBitEntity> cageBits = new ArrayList<>();
        private Vec3 moonCenter = Vec3.ZERO;
        private final List<Vec3> moonSafe = new ArrayList<>();

        MementoAttackGoal(MementoMoriEntity mori) {
            this.mori = mori;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mori.stage() == STAGE_NONE && !mori.isDefeatedSynced();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            action = 0;
            for (MementoBitEntity b : cageBits) b.discard();
            cageBits.clear();
        }

        private int cd(int ticks) {
            return mori.isPhase2() ? ticks * 7 / 10 : ticks;
        }

        private void announce(String key, ChatFormatting... style) {
            for (ServerPlayer p : ((ServerLevel) mori.level()).getPlayers(p -> p.distanceToSqr(mori) < 64 * 64)) {
                p.displayClientMessage(Component.translatable(key).withStyle(style), true);
            }
        }

        @Override
        public void tick() {
            LivingEntity t = mori.getTarget();
            swarm--; barrage--; cage--;
            if (mori.isPhase2()) moon--;
            if (t == null || !t.isAlive()) {
                mori.steer(mori.position().add(0, Math.sin(mori.tickCount * 0.05) * 0.5, 0), 0.1);
                return;
            }
            if (action != 0) {
                tickAction(t);
                return;
            }
            mori.flyAround(t, 0.0F, 0.4);
            if (!mori.getSensing().hasLineOfSight(t)) return;
            if (mori.isPhase2() && moon <= 0) { // 黒死の月
                begin(4);
                mori.level().broadcastEntityEvent(mori, EVENT_MOON);
                announce("message.automata_valkyries.memento_mori.moon", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                mori.playSound(SoundEvents.WITHER_SPAWN, 2.0F, 1.4F);
                return;
            }
            if (cage <= 0) { // 蝶の檻
                begin(3);
                mori.level().broadcastEntityEvent(mori, EVENT_CAGE);
                announce("message.automata_valkyries.memento_mori.cage", ChatFormatting.DARK_PURPLE);
                mori.playSound(SoundEvents.PHANTOM_SWOOP, 2.5F, 0.6F);
                return;
            }
            if (swarm <= 0) { // 黒死蝶の乱舞
                begin(1);
                left = mori.isPhase2() ? 18 : 12;
                mori.level().broadcastEntityEvent(mori, EVENT_SWARM);
                mori.playSound(SoundEvents.PHANTOM_FLAP, 3.0F, 0.6F);
                return;
            }
            if (barrage <= 0) { // 死蝶の幾何弾幕
                begin(2);
                pattern = mori.random.nextInt(3);
                mori.level().broadcastEntityEvent(mori, EVENT_BARRAGE);
                mori.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.6F);
            }
        }

        private void begin(int kind) {
            action = kind;
            actionT = 0;
        }

        private void end(int cooldown) {
            switch (action) {
                case 1 -> swarm = cd(cooldown);
                case 2 -> barrage = cd(cooldown);
                case 3 -> cage = cd(cooldown);
                case 4 -> moon = cooldown;
                default -> { }
            }
            action = 0;
        }

        private void tickAction(LivingEntity t) {
            int at = actionT++;
            boolean p2 = mori.isPhase2();
            ServerLevel server = (ServerLevel) mori.level();
            switch (action) {
                case 1 -> { // 乱舞: 2 Tick ごとに左右の翼から 1 匹ずつ
                    mori.flyAround(t, 0.0F, 0.2);
                    if (at >= 6 && at % 2 == 0 && left > 0) {
                        mori.swarmBit(t, left % 2 == 0);
                        left--;
                    }
                    if (left <= 0 && at > 10) end(200);
                }
                case 2 -> { // 幾何弾幕: 輪（3 波）・花（2 波）・渦巻き（40 Tick 流す）
                    mori.flyAround(t, 0.0F, 0.15);
                    double cone = p2 ? 0.62 : 0.5, speed = p2 ? 0.55 : 0.45;
                    if (pattern == 0) {
                        if (at % 12 == 0 && at <= 24) mori.barrageWave(t, 0, p2 ? 28 : 20, at * 0.13, cone, speed);
                        if (at > 30) end(150);
                    } else if (pattern == 1) {
                        if (at % 16 == 0 && at <= 16) mori.barrageWave(t, 1, p2 ? 32 : 24, at * 0.1, cone * 0.8, speed * 0.9);
                        if (at > 24) end(160);
                    } else {
                        if (at < 40 && at % 2 == 0) {
                            for (int arm = 0; arm < (p2 ? 4 : 3); arm++) {
                                mori.barrageWave(t, 2, 1, at * 0.22 + arm * Math.PI * 2 / (p2 ? 4 : 3), cone, speed);
                            }
                        }
                        if (at > 44) end(160);
                    }
                }
                case 3 -> { // 檻: 並べて（20 Tick）、2 秒止め、合図で一斉に突っ込む
                    mori.flyAround(t, 2.0F, 0.15);
                    if (at == 0) {
                        int n = p2 ? 16 : 12;
                        for (Vec3 slot : cageSlots(t, n)) cageBits.add(MementoBitEntity.cage(server, mori, t, mori.position().add(0, CHEST, 0), slot, MementoBitEntity.DAMAGE, p2));
                    }
                    if (at > 20 && at < 60 && at % 4 == 0) { // 予告: 檻から相手へ紫の線
                        for (MementoBitEntity b : cageBits) {
                            if (b.isAlive()) Fx.line(server, glow(p2 ? C_CRIMSON : C_VIOLET, 0.2F, 5), b.position(), t.position().add(0, t.getBbHeight() * 0.5, 0), 1.2);
                        }
                    }
                    if (at == 60) {
                        for (MementoBitEntity b : cageBits) if (b.isAlive()) b.release();
                        cageBits.clear();
                        mori.level().broadcastEntityEvent(mori, EVENT_CAGE_RELEASE);
                        mori.playSound(SoundEvents.PHANTOM_BITE, 3.0F, 0.6F);
                    }
                    if (at > 70) end(260);
                }
                case 4 -> { // 黒死の月: 20 Tick で舞い上がって構え、100 Tick 数えて納める
                    if (at == 0) {
                        moonCenter = new Vec3(t.getX(), mori.groundY(t.position().add(0, 1, 0)), t.getZ());
                        moonSafe.clear();
                        for (int i = 0; i < MOON_SAFE; i++) {
                            double a = Math.PI * 2 * i / MOON_SAFE + mori.random.nextDouble() * 0.9;
                            double r = 6.0 + mori.random.nextDouble() * 10.0;
                            Vec3 s = moonCenter.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                            moonSafe.add(new Vec3(s.x, mori.groundY(s.add(0, 4, 0)), s.z));
                        }
                        MementoMoonEntity.spawn(server, moonCenter, MOON_RADIUS, moonSafe, 20 + MOON_COUNTDOWN);
                    }
                    mori.steer(moonCenter.add(0, HOVER_HEIGHT + 8.0, 0), 0.4);
                    mori.faceToward(moonCenter);
                    if (at >= 20 && (at - 20) % 20 == 0 && at < 20 + MOON_COUNTDOWN) mori.playSound(SoundEvents.BELL_BLOCK, 3.0F, 0.5F + (at - 20) * 0.004F);
                    if (at == 20 + MOON_COUNTDOWN) mori.moonFall(moonCenter, moonSafe);
                    if (at > 30 + MOON_COUNTDOWN) end(600);
                }
                default -> action = 0;
            }
        }
    }
}
