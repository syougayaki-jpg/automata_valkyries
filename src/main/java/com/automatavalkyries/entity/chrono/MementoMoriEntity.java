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
 * HP {@value #MAX_HP}、HP 50% で第 2 形態（出力を上げ、翼が大きく開いて全身が紫・桃色・水色の光体になる。弾幕が激しくなり、大技が増える）。
 * 技:
 * <ul>
 *   <li>黒死蝶の乱舞: 翼から蝶ビットを次々に放ち、群れでゆるく追わせる（{@link MementoBitEntity}。当たると {@value MementoBitEntity#DAMAGE} と最大 HP を 5% 削る呪い）</li>
 *   <li>死蝶の幾何弾幕: 相手の方へ、輪・渦巻き・花の模様を描く弾（{@link MementoBulletEntity}。1 発 {@value #BULLET}。防御無視で、当たると最大 HP を 1% 削る）</li>
 *   <li>死蝶の大輪・光蝶の雨・死蝶の綾織と、技の合間の連射（2026-10-15 ユーザー指定「より弾幕シューティングらしく画面を埋めるほど派手に」）:
 *       胸から全方位へ花の形に開く何段もの輪、空の魔法陣から降る雨、左右の翅から逆へ曲がってぶつかり合う扇。弾は多いが 1 発は弱く、当たるのは芯だけ</li>
 *   <li>蝶の檻と一斉突撃: 蝶ビットが相手のまわりを 2 段の輪で囲み、2 秒後の合図で一斉に突っ込む（ビットを壊して逃げ道を作る）</li>
 *   <li>黒死の月（第 2 形態）: 舞い上がって翼で相手のまわり半径 {@value #MOON_RADIUS} を覆い、5 秒後に光の当たる安全地帯 {@value #MOON_SAFE} つの外を死の鱗粉で納める
 *       （{@value #MOON} と呪い 2 つ分）。見た目は {@link MementoMoonEntity}。数える間も安全地帯の外へ光の雨が降る</li>
 *   <li>死蝶終葬『メメント・モリ』（第 2 形態で HP {@value #SPELL_AT} 割を切ったら 1 度だけ）: 名を告げて {@value #SPELL_TICKS} Tick、全部の模様を重ねた耐久の弾幕。
 *       その間に受けるダメージは {@value #SPELL_GUARD} 倍</li>
 * </ul>
 * 召喚はルクシオン・ガリアフォートと同じ（回想のトライアル「死蝶の舞踏」と英雄機の呼び声）。挑む相手がいなくなると退く
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class MementoMoriEntity extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech, com.automatavalkyries.entity.RetreatableBoss {

    /** 弾の威力（2026-10-15 に弾を何倍にも増やしたので 6 から下げた） */
    /** HP はティア 3 の兵装の火力を前提に 15,000 から 3 倍（2026-10-15 ユーザー指定「ティア３前提の火力なので体力三倍で」） */
    public static final float MAX_HP = 45000.0F, BULLET = 3.0F, BULLET_LARGE = 5.0F, MOON = 40.0F;
    /** 終葬: HP がこの割合を切ったら・長さ・その間に受けるダメージの倍率 */
    public static final float SPELL_AT = 0.15F, SPELL_GUARD = 0.35F;
    /** 終葬の長さ: 構え 20 Tick ＋ 弾幕 MementoFinale#END（2026-10-15 ユーザー指定「60 秒にして」） */
    public static final int SPELL_TICKS = 1200;
    /** 光蝶の雨の魔法陣の高さ（相手の足元から） */
    static final double RAIN_HEIGHT = 16.0;
    static final Vec3 UP = new Vec3(0, 1, 0);
    /**
     * 弾のダメージの種類（2026-10-15 ユーザー指定「攻撃力が低い変わりに防御無視と最大体力削り」）: 防具・ダメージ軽減のエンチャント・耐性の効果・盾を通る。
     * 被弾後の無敵時間は効く。当たると死蝶の呪いを {@link MementoCurse#BULLET} つ（最大 HP 1%）
     */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> BULLET_DAMAGE = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "memento_bullet"));

    /** 終葬の弾のダメージの種類（2026-10-15 ユーザー指定「死蝶終葬『メメント・モリ』だけは無敵無視でいい」）: 弾と同じく防御無視で、被弾後の無敵時間も無視する（クリエイティブは傷つけない） */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> FINALE_DAMAGE = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "memento_finale"));

    /** 弾 direct の当たりのダメージ（持ち主はメメントモリ。finale = 終葬の間に撃った弾） */
    DamageSource bulletDamage(net.minecraft.world.entity.Entity direct, boolean finale) {
        return new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(finale ? FINALE_DAMAGE : BULLET_DAMAGE), direct, this);
    }
    public static final float HOVER_HEIGHT = 10.0F, ORBIT = 13.0F, MOON_RADIUS = 22.0F, MOON_SAFE_RADIUS = 2.6F;
    public static final int MOON_SAFE = 4, MOON_COUNTDOWN = 100;
    /** 体の大きさ（モデルの倍率。描画 client/MementoMoriRenderer）。当たり判定は ModEntities で幅 1.6・高さ 5.4 */
    public static final float SIZE = 2.0F;
    /** 胸の高さ（足元から。演出の中心） */
    static final double CHEST = 3.6;
    public static final byte STAGE_NONE = 0, STAGE_INTRO = 1, STAGE_AWAKEN = 2, STAGE_RETREAT = 3;
    /** 演出の長さ（2026-10-14 に延ばした。時間割は client/MementoShow） */
    public static final int INTRO_TICKS = 80, AWAKEN_TICKS = 100, RETREAT_TICKS = 50, DEFEAT_TICKS = 90;
    /** 動きの合図（描画）: 乱舞・弾幕・檻・檻の突撃・月の構え・月の納め */
    public static final byte EVENT_SWARM = 70, EVENT_BARRAGE = 71, EVENT_CAGE = 72, EVENT_CAGE_RELEASE = 73, EVENT_MOON = 74, EVENT_MOON_FALL = 75, EVENT_SPELL = 76;
    public static final int C_VIOLET = 0xA45CFF, C_PALE = 0xE8D0FF, C_WHITE = 0xFFFFFF;   // 紫と淡い紫（2026-10-13 に参考画像に合わせて青紫から）
    /** 第 2 形態の光体の光（2026-10-14 ユーザー指定「赤くなるというより出力を上げほぼ幻想的な光体となる演出に」。赤の代わりに明るい桃色と水色） */
    public static final int C_PINK = 0xFF8CEB, C_CYAN = 0x8FE6FF;

    /** 演出の効果音（tools/gen_memento_sounds.py で合成した memento.<名前>。2026-10-14） */
    static SoundEvent sfx(String name) {
        return SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "memento." + name));
    }

    /** 紫・桃色・水色から 1 つ */
    int dreamColor() {
        int k = random.nextInt(3);
        return k == 0 ? C_VIOLET : k == 1 ? C_PINK : C_CYAN;
    }

    public static com.automatavalkyries.particle.FxOptions glow(int color, float size, int life) {
        return Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), color, size, life);
    }

    private static final EntityDataAccessor<Boolean> PHASE2 = SynchedEntityData.defineId(MementoMoriEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> STAGE = SynchedEntityData.defineId(MementoMoriEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(MementoMoriEntity.class, EntityDataSerializers.BOOLEAN);

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    private final com.automatavalkyries.entity.BossRetreatWatch retreatWatch = new com.automatavalkyries.entity.BossRetreatWatch(64.0, 200);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable(getType().getDescriptionId()), // EX は自分の名前（2026-10-27）
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private boolean trialBoss, retreatPending, introDone, lootDropped;
    /** 終葬を使ったか（1 度だけ）・今その最中か */
    private boolean spellDone, spellActive;
    /** 技の組み立て（メメントモリ EX の第 3 形態の技は、これが技の途中でない時に割り込む） */
    MementoAttackGoal attackGoal;
    private int lastShotSound = -100;
    private float trialAttack = 1.0F;
    private int stageTicks;
    private float orbitAngle;
    /** 戦い方の頭（2026-10-15「AI 強化」: 隠れても攻める・動きを読む・状況で技を選ぶ） */
    final MementoTactics tactics = new MementoTactics(this);
    /** 見える所を探して低く飛ぶ間の高さ（keepUntil の Tick まで。HOVER_HEIGHT の代わり） */
    private double keepH = HOVER_HEIGHT;
    private int keepUntil;
    /** クライアント: 合図の Tick（描画用）と、今の段階の始まり、撃破の演出の経過 Tick */
    public int swarmTick = -100, barrageTick = -100, cageTick = -100, cageReleaseTick = -100, moonTick = -100, moonFallTick = -100, spellTick = -1000, stageStart, defeatTicks;
    /** クライアント: 最後に終葬を始めたメメントモリ（専用ロゴの描画 client/MementoSpellLogo が見る） */
    private static java.lang.ref.WeakReference<MementoMoriEntity> lastSpellCaster = new java.lang.ref.WeakReference<>(null);

    @org.jetbrains.annotations.Nullable
    public static MementoMoriEntity lastSpellCaster() {
        return lastSpellCaster.get();
    }

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

    void startStage(byte stage) {
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
        if (spellActive && !bypass) amount *= SPELL_GUARD;                     // 終葬の間は受けにくい
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
        tag.putBoolean("SpellDone", spellDone);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        introDone = true;
        entityData.set(STAGE, STAGE_NONE);
        if (tag.getBoolean("Phase2")) entityData.set(PHASE2, true);
        spellDone = tag.getBoolean("SpellDone");
    }

    @Override
    protected void registerGoals() {
        attackGoal = new MementoAttackGoal(this);
        goalSelector.addGoal(2, attackGoal);
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
    public java.util.List<net.minecraft.server.level.ServerBossEvent> bossBars() {
        return java.util.List.of(bossEvent);
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

    /**
     * 撃破（2026-10-14 ユーザー指定「光の蝶になって散る」。時間割は client/MementoShow）: 光が乱れてまたたきながらゆっくり沈み、浮く装甲が 1 枚ずつ光を失って落ち（0〜45）、
     * 光輪が広がって割れ（30〜45）、体が光になってほどけて無数の光の蝶になって空へ舞い上がる（45〜90）
     */
    @Override
    protected void tickDeath() {
        if (protect.refuseDeathTick()) return; // 撃破を認めていないのに演出を回された（The King Sword 等）
        ++deathTime;
        if (!level().isClientSide && !entityData.get(DEFEATED)) entityData.set(DEFEATED, true);
        if (!level().isClientSide && stage() != STAGE_NONE) startStage(STAGE_NONE);
        if (level().isClientSide) {
            if (deathTime % 2 == 0) level().addParticle(glow(dreamColor(), 0.3F, 20), getRandomX(2.0), getY() + 1.0 + random.nextDouble() * 5.0, getRandomZ(2.0), 0, 0.03, 0);
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        // ゆっくり沈む（地面の上 2 ブロックまで。はじめの 45 Tick は小さならせん）
        double ground = groundY(position());
        double a = deathTime * 0.2;
        double sway = deathTime < 45 ? 0.06 : 0.0;
        setDeltaMovement(Math.cos(a) * sway, getY() > ground + 2.0 && deathTime < 60 ? -0.05 : 0.0, Math.sin(a) * sway);
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        Vec3 c = position().add(0, CHEST, 0);
        if (deathTime < 45 && deathTime % 3 == 0) { // 光を失って落ちる装甲の火花
            Fx.burst(server, glow(C_PALE, 0.35F, 14), c.add(random.nextGaussian() * 1.5, random.nextGaussian() * 1.5, random.nextGaussian() * 1.5), 4, 0.08);
        }
        if (deathTime == 2) playSound(SoundEvents.PHANTOM_DEATH, 2.5F, 0.6F);
        if (deathTime == 40) { // 光輪が割れる
            Vec3 head = position().add(0, 5.6, 0);
            Fx.burst(server, glow(C_PALE, 0.6F, 24), head, 40, 0.35);
            Fx.burst(server, glow(C_PINK, 0.5F, 24), head, 20, 0.3);
            Fx.shockwave(server, head, C_VIOLET, 5.0F, 16);
            playSound(sfx("shatter"), 3.0F, 1.0F);
        }
        if (deathTime == 45) playSound(sfx("scatter"), 3.5F, 1.0F);
        if (deathTime >= 45 && deathTime < 85) { // ほどけていく光（舞い上がる粒）
            for (int i = 0; i < 3; i++) {
                Fx.one(server, glow(dreamColor(), 0.4F, 30), c.add(random.nextGaussian() * 1.6, random.nextGaussian() * 2.0, random.nextGaussian() * 1.6),
                        new Vec3(random.nextGaussian() * 0.05, 0.12 + random.nextDouble() * 0.1, random.nextGaussian() * 0.05));
            }
        }
        if (deathTime >= DEFEAT_TICKS && !DirectRead.isRemoved(this)) {
            Fx.burst(server, glow(C_WHITE, 0.8F, 30), c, 40, 0.3);
            Fx.burst(server, glow(C_CYAN, 0.6F, 30), c, 30, 0.4);
            Fx.shockwave(server, c, C_PINK, 10.0F, 26);
            server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 1.5, 2.0, 1.5, 0.08);
            playSound(sfx("form"), 3.0F, 0.6F);
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
            boolean big = tickCount - moonTick < 140 || tickCount - spellTick < spellTicks() || stage() == STAGE_AWAKEN;
            haloSpin += isDefeatedSynced() ? 0.3F : big ? 4.0F : busy ? 2.5F : 1.0F;
            if (!isDefeatedSynced() && tickCount % 3 == 0) { // 翼から舞う光の鱗粉（2026-10-14 ユーザー指定の演出「光の軌跡・粒を濃く」。黒い鱗粉は「黒いもやを全部幻想的な光にして」で無くした）
                level().addParticle(glow(C_PALE, 0.2F, 24), getRandomX(2.0), getY() + 1.0 + random.nextDouble() * 5.5, getRandomZ(2.0), 0, -0.03, 0);
                float yaw = yBodyRot * Mth.DEG_TO_RAD;
                double side = (random.nextBoolean() ? 1 : -1) * (1.0 + random.nextDouble() * 7.0), back = 1.8;
                level().addParticle(glow(isPhase2() ? dreamColor() : C_VIOLET, isPhase2() ? 0.35F : 0.25F, 30),
                        getX() + Math.cos(yaw) * side + Math.sin(yaw) * back, getY() + 3.0 + random.nextDouble() * 6.0, getZ() + Math.sin(yaw) * side - Math.cos(yaw) * back,
                        0, -0.02, 0);
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
        if (!isPhase2() && ratio <= phaseTwoAt() && isAlive() && !defeatedNow()) {
            entityData.set(PHASE2, true);
            startStage(STAGE_AWAKEN); // 出力を上げ、翅が大きく開いて全身が光体になる（2 秒、攻撃を受けない）
            bossEvent.setColor(BossEvent.BossBarColor.PINK);
            playSound(sfx("charge"), 4.0F, 1.0F);
            if (level() instanceof ServerLevel server) {
                for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 64 * 64)) {
                    p.displayClientMessage(Component.translatable("message.automata_valkyries.memento_mori.phase2").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
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
        if (stage == STAGE_INTRO) { // 光の蝶の群れが渦を巻いて集まって形になり（0〜55）、浮く装甲を纏い（45〜68）、舞い上がる（45〜80）。時間割は client/MementoShow
            Vec3 c = position().add(0, CHEST, 0);
            if (t == 0) playSound(sfx("gather"), 4.0F, 1.0F);
            if (t < 45) {
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.3 + i * 2.1;
                    double r = 9.0 * (1.0 - t / 50.0);
                    Vec3 p = c.add(Math.cos(a) * r, 4.0 * (1.0 - t / 45.0) + random.nextGaussian() * 0.5, Math.sin(a) * r);
                    Fx.one(server, glow(dreamColor(), 0.45F, 16), p, c.subtract(p).scale(0.05));
                }
            }
            if (t == 20) Fx.mandala(server, position().add(0, 0.2, 0), C_VIOLET, 6.0F, 60, false, false);
            if (t == 40) {
                playSound(sfx("form"), 3.5F, 1.0F);
                Fx.burst(server, glow(C_PALE, 0.6F, 24), c, 40, 0.25);
                Fx.shockwave(server, c, C_PINK, 6.0F, 20);
            }
            if (t >= 45 && t % 3 == 0) playSound(SoundEvents.PHANTOM_FLAP, 1.5F, 0.7F + random.nextFloat() * 0.2F);
            setDeltaMovement(0, t >= 45 ? 0.26 : 0.0, 0);                          // 集まる間は止まり、纏ってから舞い上がる
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            if (t >= INTRO_TICKS) startStage(STAGE_NONE);
        } else if (stage == STAGE_AWAKEN) { // 昇華（2026-10-14 ユーザー指定「時間をかけた昇華」。時間割は client/MementoShow）
            setDeltaMovement(Vec3.ZERO);
            Vec3 c = position().add(0, CHEST, 0);
            if (t < 60 && t % 2 == 0) { // 胸の核へ光が集まる
                Fx.gather(server, glow(t % 4 == 0 ? C_PINK : C_CYAN, 0.45F, 14), c, 3, 5.0, 12);
            }
            if (t == 20) Fx.burst(server, glow(C_PALE, 0.5F, 20), c, 30, 0.35); // 浮く装甲が弾かれる
            if (t == 30) { // 足元の大きな魔法陣と空への光の柱
                Vec3 feet = new Vec3(getX(), groundY(position()) + 0.15, getZ());
                Fx.mandala(server, feet, C_PINK, 9.0F, 70, false, false);
                Fx.mandala(server, feet.add(0, 0.05, 0), C_CYAN, 6.5F, 70, false, true);
                Fx.beamRing(server, c, new Vec3(0, 1, 0), C_CYAN, 10.0F, 30);
                playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 1.4F);
            }
            if (t == 60) { // 白い閃光で光体になる
                Fx.burst(server, glow(C_WHITE, 1.0F, 30), c, 50, 0.45);
                Fx.burst(server, glow(C_PINK, 1.0F, 30), c, 40, 0.55);
                Fx.burst(server, glow(C_CYAN, 1.0F, 30), c, 40, 0.65);
                Fx.shockwave(server, c, C_PINK, 14.0F, 26);
                Fx.shockwave(server, c, C_CYAN, 18.0F, 32);
                server.sendParticles(ParticleTypes.FLASH, getX(), getY() + CHEST, getZ(), 2, 0, 0, 0, 0);
                server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 2.0, 2.5, 2.0, 0.2);
                playSound(sfx("ascend"), 5.0F, 1.0F);
            }
            if (t >= AWAKEN_TICKS) startStage(STAGE_NONE);
        } else if (stage == STAGE_RETREAT) { // 高く舞い上がり、透けながら無数の光の蝶になって散る（撃破の短い版）
            setDeltaMovement(0, 0.3, 0);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            if (t == 0) playSound(sfx("scatter"), 3.0F, 1.1F);
            if (t % 2 == 0) Fx.one(server, glow(dreamColor(), 0.45F, 26), position().add(random.nextGaussian() * 1.5, CHEST + random.nextGaussian() * 2.0, random.nextGaussian() * 1.5),
                    new Vec3(0, 0.15, 0));
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
            case EVENT_SPELL -> {
                spellTick = tickCount;
                lastSpellCaster = new java.lang.ref.WeakReference<>(this);
            }
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
        double hover = tickCount < keepUntil ? keepH : HOVER_HEIGHT;   // 隠れる相手には、見える低い所でしばらく戦う
        double h = Math.max(c.y, groundY(c)) + hover + extraUp + Math.sin(tickCount * 0.07) * 1.2;
        Vec3 want = new Vec3(c.x + Math.cos(orbitAngle) * r, h, c.z + Math.sin(orbitAngle) * r);
        steer(want, speed);
        faceToward(t.position());
    }

    /** しばらく（ticks の間）相手の上 h ブロックの高さで舞う */
    void keepHeight(double h, int ticks) {
        keepH = h;
        keepUntil = tickCount + ticks;
    }

    /** 相手が今の速さで動き続けたら、from から speed の弾が届く時にいる所（{@link MementoTactics#lead}） */
    Vec3 lead(LivingEntity t, Vec3 from, double speed, double amount) {
        return tactics.lead(t, from, speed, amount);
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
        return LuxionEntity.scaledFor(target, damage * trialAttack * exScale());
    }

    // ---- メメントモリ EX（{@link MementoMoriExEntity}。2026-10-27）が変える所 ----

    /** 技のダメージの倍率（EX は 1.5） */
    protected float exScale() {
        return 1.0F;
    }

    /** 第 2 形態に入る HP の割合（EX は 66%） */
    protected float phaseTwoAt() {
        return 0.5F;
    }

    /** 技の間隔の倍率（第 2 形態は 7 割） */
    protected float cooldownScale() {
        return isPhase2() ? 0.7F : 1.0F;
    }

    /** 終葬の長さ（EX は第四楽章のぶん長い） */
    public int spellTicks() {
        return SPELL_TICKS;
    }

    /** 死蝶の呪いが深まっているか（EX の第 3 形態: 移動速度も削り、最大 HP は 25% まで） */
    public boolean deepCurse() {
        return false;
    }

    /** 終葬に第四楽章「黄泉の月蝕」を足すか（EX） */
    boolean fourthMovement() {
        return false;
    }

    /** 自分の分身（見た目だけ。EX の鏡像の死蝶。描画は体と同じ姿を置く） */
    public java.util.List<LuxionEntity.Afterimage> shadows() {
        return java.util.List.of();
    }

    boolean spellActive() {
        return spellActive;
    }

    ServerBossEvent bossEvent() {
        return bossEvent;
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
        playSound(sfx("bit"), 1.5F, 0.9F + random.nextFloat() * 0.3F);
        Fx.burst(server, glow(isPhase2() ? C_CYAN : C_VIOLET, 0.35F, 10), from, 4, 0.08);
    }

    // ---- 弾幕（2026-10-15 ユーザー指定「より弾幕シューティングらしく画面を埋めるほど派手に」。数値は【仮】） ----

    /** 弾の色: 第 1 形態は紫・淡い紫・桃色、第 2 形態は光体の桃色・水色・淡い紫 */
    int bulletColor(int i) {
        int k = Math.floorMod(i, 3);
        if (isPhase2()) return k == 0 ? C_PINK : k == 1 ? C_CYAN : C_PALE;
        return k == 0 ? C_VIOLET : k == 1 ? C_PALE : C_PINK;
    }

    /** 撃つ音（数が多いので 3 Tick に 1 度まで） */
    void shotSound(float pitch) {
        if (tickCount - lastShotSound < 3) return;
        lastShotSound = tickCount;
        playSound(sfx("shot"), 1.5F, pitch + random.nextFloat() * 0.2F);
    }

    Vec3 hand() {
        return local(4.0, 2.0, -0.5);
    }

    Vec3 chest() {
        return position().add(0, CHEST, 0);
    }

    /** 前翅の先（left = 左） */
    Vec3 wingTip(boolean left) {
        return local(5.5, -0.8, left ? 4.5 : -4.5);
    }

    static Vec3 aimAt(LivingEntity t) {
        return t.position().add(0, t.getBbHeight() * 0.5, 0);
    }

    boolean fire(MementoBulletEntity.Shot shot) {
        if (spellActive) shot.finale(); // 終葬の間に撃った弾は無敵時間も無視する
        return level() instanceof ServerLevel server && shot.fire(server, this) != null;
    }

    /** 死蝶の幾何弾幕の 1 波: 右手の先から、相手の方への円錐に count 発。pattern 0 = 輪（まっすぐ）、1 = 花（交互に逆へ曲がる）、2 = 渦巻きの 1 本（rot の向きの 1 発） */
    void barrageWave(LivingEntity t, int pattern, int count, double rot, double cone, double speed, byte kind, int color) {
        Vec3 from = hand();
        Vec3 d = lead(t, from, speed, 0.6).subtract(from).normalize(); // 模様の真ん中は少し先を読む
        Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : UP;
        Vec3 u = d.cross(up).normalize(), v = d.cross(u).normalize();
        for (int i = 0; i < count; i++) {
            double th = rot + Math.PI * 2 * i / count;
            Vec3 dir = d.scale(Math.cos(cone)).add(u.scale(Math.cos(th) * Math.sin(cone))).add(v.scale(Math.sin(th) * Math.sin(cone))).normalize();
            float curl = pattern == 1 ? (i % 2 == 0 ? 0.045F : -0.045F) : 0.0F;
            fire(MementoBulletEntity.shot(from, dir.scale(speed)).curl(d, curl).kind(kind).color(color));
        }
        shotSound(0.8F);
    }

    /** 合間の連射: 左右の翅の先から交互に、相手のまわり（半径 9）へ小さな光弾を絶え間なく。ときどき右手から相手を狙う蝶弾の扇 */
    void streamTick(LivingEntity t) {
        boolean p2 = isPhase2();
        int every = p2 ? 2 : 3;
        if (tickCount % every == 0) {
            boolean left = (tickCount / every) % 2 == 0;
            Vec3 from = wingTip(left);
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 9.0;
                Vec3 to = lead(t, from, 0.3, 0.7).add(Math.cos(a) * r, -0.5, Math.sin(a) * r);
                fire(MementoBulletEntity.shot(from, to.subtract(from).normalize().scale(0.3)).curl(UP, left ? 0.01F : -0.01F).color(bulletColor(i + (left ? 0 : 1))));
            }
        }
        if (tickCount % (p2 ? 24 : 36) == 0) aimedFan(t, p2 ? 7 : 5, 0.13, 0.55);
    }

    /** 右手から相手を狙う蝶弾の扇（count 発、隣どうし spread ラジアン） */
    void aimedFan(LivingEntity t, int count, double spread, double speed) {
        Vec3 from = hand();
        Vec3 d = lead(t, from, speed, 1.0).subtract(from).normalize(); // 動きを読んで先回り
        for (int i = 0; i < count; i++) {
            Vec3 dir = MementoBulletEntity.rotate(d, UP, (i - (count - 1) / 2.0) * spread);
            fire(MementoBulletEntity.shot(from, dir.scale(speed)).kind(MementoBulletEntity.BUTTERFLY).color(isPhase2() ? C_WHITE : C_PINK));
        }
        shotSound(1.2F);
    }

    /**
     * 死蝶の大輪の 1 波: 胸から全方位へ、仰角の違う layers 段（下 62 度〜上 12 度）の輪を、花びら petals 枚の花の形に（向きで速さを変える）。
     * ゆっくり開いてから速くなる。波ごとに回して、曲がる向きを入れ替える
     */
    void bloomWave(int wave, int layers, int perRing, int petals) {
        Vec3 from = chest();
        double rot = wave * Math.PI / petals * 0.5;
        float curl = wave % 2 == 0 ? 0.008F : -0.008F;
        for (int l = 0; l < layers; l++) {
            double el = Math.toRadians(-62.0 + 74.0 * l / Math.max(1, layers - 1));
            byte kind = l % 3 == 1 ? MementoBulletEntity.BUTTERFLY : MementoBulletEntity.SMALL;
            for (int i = 0; i < perRing; i++) {
                double az = rot + l * 0.21 + Math.PI * 2 * i / perRing;
                double petal = 1.0 + 0.35 * Math.cos(petals * (az - rot));
                Vec3 dir = new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
                fire(MementoBulletEntity.shot(from, dir.scale(0.1 * petal)).accel(1.05F, (float) (0.42 * petal)).curl(UP, curl)
                        .kind(kind).color(bulletColor(l + wave)).life(el > 0 ? 60 : MementoBulletEntity.LIFE));
            }
        }
        shotSound(0.7F);
    }

    /**
     * 光蝶の雨の 1 Tick: center のまわり半径 radius の空（{@value #RAIN_HEIGHT} ブロック上）の思い思いの所から count 発、ゆっくり降り始めて速くなる。
     * aimed なら、ときどき相手の真上から蝶弾。avoid の所（半径 avoidR）には降らせない（黒死の月の安全地帯）
     */
    void rain(Vec3 center, double radius, int count, @org.jetbrains.annotations.Nullable LivingEntity aimed, @org.jetbrains.annotations.Nullable List<Vec3> avoid, double avoidR) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * radius;
            Vec3 ground = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            if (avoid != null && avoid.stream().anyMatch(s -> ground.subtract(s).multiply(1, 0, 1).length() < avoidR)) continue;
            Vec3 v = new Vec3(random.nextGaussian() * 0.015, -0.1, random.nextGaussian() * 0.015);
            fire(MementoBulletEntity.shot(ground.add(0, RAIN_HEIGHT, 0), v).accel(1.04F, 0.42F).color(bulletColor(i)));
        }
        if (aimed != null && random.nextInt(6) == 0) {
            Vec3 ahead = tactics.velocity(aimed).multiply(1, 0, 1).scale(30.0);  // 降りるまで（約 1.5 秒）に動く分を読む
            Vec3 from = aimed.position().add(ahead).add(random.nextGaussian() * 1.5, RAIN_HEIGHT, random.nextGaussian() * 1.5);
            fire(MementoBulletEntity.shot(from, new Vec3(0, -0.12, 0)).accel(1.05F, 0.5F).kind(MementoBulletEntity.BUTTERFLY).color(C_WHITE));
        }
    }

    /** 死蝶の綾織の 1 回: 左右の翅の先から相手の方へ count 発の扇。左右で逆へ曲がり、逆へ首を振るので、ぶつかり合って網目と波の模様になる */
    void latticeFan(LivingEntity t, int at, int count) {
        for (boolean left : new boolean[]{true, false}) {
            Vec3 from = wingTip(left);
            Vec3 d = lead(t, from, 0.36, 0.5).subtract(from).normalize();
            double sweep = Math.sin(at * 0.12) * 0.35 * (left ? 1 : -1);
            int color = left ? (isPhase2() ? C_CYAN : C_VIOLET) : C_PINK;
            for (int i = 0; i < count; i++) {
                double off = (i - (count - 1) / 2.0) * (1.2 / Math.max(1, count - 1)) + sweep;
                fire(MementoBulletEntity.shot(from, MementoBulletEntity.rotate(d, UP, off).scale(0.36)).curl(UP, left ? 0.014F : -0.014F).color(color));
            }
        }
        shotSound(1.0F);
    }

    /** 終葬の 2 重の渦: 胸から arms 本ずつ、右回りと左回りの渦巻きを 1 発ずつ */
    void doubleSpiral(int at, int arms) {
        Vec3 from = chest();
        double el = -0.5 + 0.25 * Math.sin(at * 0.05);
        for (int sign = -1; sign <= 1; sign += 2) {
            for (int arm = 0; arm < arms; arm++) {
                double az = sign * at * 0.19 + arm * Math.PI * 2 / arms;
                Vec3 dir = new Vec3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
                fire(MementoBulletEntity.shot(from, dir.scale(0.34)).color(sign > 0 ? C_PINK : C_CYAN));
            }
        }
        shotSound(1.4F);
    }

    /** 大玉: 胸の前に count 個並べて止め、少しずつずらして相手へ向き直して飛ぶ */
    void largeShot(LivingEntity t, int count) {
        Vec3 from = chest();
        Vec3 d = aimAt(t).subtract(from).normalize();
        for (int i = 0; i < count; i++) {
            Vec3 dir = MementoBulletEntity.rotate(d, UP, (i - (count - 1) / 2.0) * 0.5);
            fire(MementoBulletEntity.shot(from.add(dir.scale(2.0)), Vec3.ZERO).delay(20 + i * 3).reaim(t, 0.5).kind(MementoBulletEntity.LARGE)
                    .color(bulletColor(i)).damage(BULLET_LARGE));
        }
        playSound(sfx("cast"), 2.0F, 1.5F);
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
            MementoCurse.add(e, MementoCurse.BIT * 2, this);
        }
        for (int c : new int[]{C_VIOLET, C_PINK, C_CYAN}) {                          // 範囲いっぱいに舞う光の鱗粉（2026-10-14 ユーザー指定「黒いもやを全部幻想的な光にして」）
            Fx.spray(server, glow(c, 0.5F, 30), center.add(0, 1.0, 0), 100, MOON_RADIUS * 0.6, 1.5, MOON_RADIUS * 0.6, 0.05);
        }
        Fx.shockwave(server, center.add(0, 0.2, 0), C_PINK, MOON_RADIUS, 30);
        Fx.shockwave(server, center.add(0, 0.3, 0), C_VIOLET, MOON_RADIUS * 0.7F, 24);
        Fx.shockwave(server, center.add(0, 0.4, 0), C_CYAN, MOON_RADIUS * 1.15F, 36);
        server.sendParticles(ParticleTypes.FLASH, center.x, center.y + 2.0, center.z, 3, MOON_RADIUS * 0.3, 0.5, MOON_RADIUS * 0.3, 0);
        server.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 8.0, center.z, 200, MOON_RADIUS * 0.5, 6.0, MOON_RADIUS * 0.5, 0.1);  // 降る光
        playSound(sfx("moon_fall"), 6.0F, 1.0F);
        playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.5F);
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

    /** 終葬の名を告げる: 画面には専用ロゴ（EVENT_SPELL を受けたクライアントが client/MementoSpellLogo で描く。2026-10-15）、ボスバーの名も技の名にする */
    void announceSpell() {
        bossEvent.setName(Component.translatable("message.automata_valkyries.memento_mori.spell").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }

    void endSpell() {
        if (!spellActive) return;
        spellActive = false;
        bossEvent.setName(getDisplayName());
    }

    // ---- ゴール ----

    /**
     * 戦い方: 相手の上空を舞いながら、技の合間も翅から弾を流し続け、黒死蝶の乱舞・死蝶の幾何弾幕・死蝶の大輪・死蝶の綾織・光蝶の雨・蝶の檻を
     * 状況で選んで使う（{@link MementoTactics}。相手が見えなければ回り込み、長引けばいぶり出す）
     * （第 2 形態は間隔が 7 割で、数が増え、黒死の月を足す。HP {@value #SPELL_AT} 割を切ると 1 度だけ終葬）
     */
    static class MementoAttackGoal extends Goal {
        private static final int SWARM = 1, BARRAGE = 2, CAGE = 3, MOON = 4, BLOOM = 5, RAIN = 6, LATTICE = 7, SPELL = 8;
        private final MementoMoriEntity mori;
        private int action, actionT, left;
        private int swarm = 60, barrage = 20, cage = 160, moon = 200, bloom = 100, rain = 240, lattice = 180;
        private int pattern, gap;
        private final List<MementoBitEntity> cageBits = new ArrayList<>();
        private Vec3 moonCenter = Vec3.ZERO, rainCenter = Vec3.ZERO;
        private final List<Vec3> moonSafe = new ArrayList<>();
        private MementoFinale finale;

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
            mori.endSpell();
        }

        private int cd(int ticks) {
            return Math.max(1, Math.round(ticks * mori.cooldownScale()));
        }

        /** 技の途中でない（ふだんの舞いと連射だけ）か */
        boolean idle() {
            return action == 0 && !mori.spellActive;
        }

        private void announce(String key, ChatFormatting... style) {
            for (ServerPlayer p : ((ServerLevel) mori.level()).getPlayers(p -> p.distanceToSqr(mori) < 64 * 64)) {
                p.displayClientMessage(Component.translatable(key).withStyle(style), true);
            }
        }

        @Override
        public void tick() {
            LivingEntity t = mori.getTarget();
            swarm--; barrage--; cage--; bloom--; rain--; lattice--; gap--;
            if (mori.isPhase2()) moon--;
            if (t == null || !t.isAlive()) {
                mori.steer(mori.position().add(0, Math.sin(mori.tickCount * 0.05) * 0.5, 0), 0.1);
                return;
            }
            mori.tactics.observe(t); // 相手の動きと当たり具合を見張る
            if (action != 0) {
                tickAction(t);
                return;
            }
            if (!mori.tactics.handleSight(t, mori.getSensing().hasLineOfSight(t))) return; // 見えなければ回り込み、長引けばいぶり出す
            mori.flyAround(t, 0.0F, 0.4);
            ServerLevel server = (ServerLevel) mori.level();
            if (mori.isPhase2() && !mori.spellDone && mori.protect.healthRatio() <= SPELL_AT) { // 終葬
                begin(SPELL);
                finale = new MementoFinale(mori);
                mori.spellDone = true;
                mori.spellActive = true;
                mori.level().broadcastEntityEvent(mori, EVENT_SPELL);
                mori.announceSpell();
                mori.playSound(sfx("ascend"), 5.0F, 0.8F);
                mori.playSound(sfx("cast"), 5.0F, 0.6F);
                mori.playSound(SoundEvents.WITHER_SPAWN, 1.5F, 1.6F);
                Vec3 c = mori.chest();
                Fx.mandala(server, c, C_PINK, 6.0F, mori.spellTicks(), true, false);
                Fx.mandala(server, c, C_CYAN, 4.0F, mori.spellTicks(), true, true);
                Fx.mandala(server, new Vec3(t.getX(), t.getY() + RAIN_HEIGHT, t.getZ()), C_PALE, 16.0F, mori.spellTicks(), false, false);
                Fx.shockwave(server, c, C_PINK, 16.0F, 30);
                server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
                return;
            }
            if (mori.isPhase2() && moon <= 0) { // 黒死の月
                begin(MOON);
                mori.level().broadcastEntityEvent(mori, EVENT_MOON);
                announce("message.automata_valkyries.memento_mori.moon", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
                mori.playSound(SoundEvents.WITHER_SPAWN, 1.5F, 1.4F);
                mori.playSound(sfx("cast"), 4.0F, 0.7F);
                return;
            }
            int pick = gap > 0 ? 0 : mori.tactics.choose(t, swarm, barrage, cage, bloom, rain, lattice); // 状況で技を選ぶ
            if (pick == CAGE) { // 蝶の檻
                begin(CAGE);
                mori.level().broadcastEntityEvent(mori, EVENT_CAGE);
                announce("message.automata_valkyries.memento_mori.cage", ChatFormatting.DARK_PURPLE);
                mori.playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 0.6F);
                mori.playSound(sfx("cast"), 3.0F, 0.9F);
                Fx.mandala(server, new Vec3(t.getX(), t.getY() + 0.1, t.getZ()), mori.isPhase2() ? C_PINK : C_VIOLET, 6.0F, 75, false, true); // 相手の足元の魔法陣
                return;
            }
            if (pick == SWARM) { // 黒死蝶の乱舞
                begin(SWARM);
                left = mori.isPhase2() ? 18 : 12;
                mori.level().broadcastEntityEvent(mori, EVENT_SWARM);
                mori.playSound(SoundEvents.PHANTOM_FLAP, 3.0F, 0.6F);
                mori.playSound(sfx("cast"), 3.0F, 1.1F);
                for (boolean l : new boolean[]{true, false}) { // 左右の翅の前の魔法陣
                    Fx.mandala(server, mori.local(5.5, -0.8, l ? 3.5 : -3.5), mori.isPhase2() ? C_CYAN : C_VIOLET, 2.4F, 36, true, l);
                }
                return;
            }
            if (pick == BLOOM) { // 死蝶の大輪
                begin(BLOOM);
                mori.level().broadcastEntityEvent(mori, EVENT_SWARM);
                announce("message.automata_valkyries.memento_mori.bloom", ChatFormatting.LIGHT_PURPLE);
                mori.playSound(sfx("cast"), 3.5F, 0.8F);
                Fx.mandala(server, mori.chest(), mori.isPhase2() ? C_PINK : C_VIOLET, 4.5F, 70, true, false); // 胸の前の魔法陣
                return;
            }
            if (pick == LATTICE) { // 死蝶の綾織
                begin(LATTICE);
                mori.level().broadcastEntityEvent(mori, EVENT_SWARM);
                announce("message.automata_valkyries.memento_mori.lattice", ChatFormatting.LIGHT_PURPLE);
                mori.playSound(sfx("cast"), 3.5F, 1.2F);
                for (boolean l : new boolean[]{true, false}) {
                    Fx.mandala(server, mori.wingTip(l), l ? (mori.isPhase2() ? C_CYAN : C_VIOLET) : C_PINK, 2.8F, 100, true, l);
                }
                return;
            }
            if (pick == RAIN) { // 光蝶の雨
                begin(RAIN);
                rainCenter = t.position();
                mori.level().broadcastEntityEvent(mori, EVENT_BARRAGE);
                announce("message.automata_valkyries.memento_mori.rain", ChatFormatting.LIGHT_PURPLE);
                mori.playSound(sfx("cast"), 3.5F, 1.0F);
                mori.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.8F);
                int life = mori.isPhase2() ? 140 : 110;
                Fx.mandala(server, rainCenter.add(0, RAIN_HEIGHT, 0), mori.isPhase2() ? C_CYAN : C_VIOLET, 16.0F, life, false, false); // 空の魔法陣
                Fx.mandala(server, rainCenter.add(0, RAIN_HEIGHT - 0.2, 0), C_PINK, 11.0F, life, false, true);
                return;
            }
            if (pick == BARRAGE) { // 死蝶の幾何弾幕
                begin(BARRAGE);
                pattern = mori.random.nextInt(3);
                mori.level().broadcastEntityEvent(mori, EVENT_BARRAGE);
                mori.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.6F);
                mori.playSound(sfx("cast"), 3.0F, 1.3F);
                Fx.mandala(server, mori.hand(), mori.isPhase2() ? C_PINK : C_VIOLET, 1.8F, 60, true, false); // 右手の先の魔法陣
                return;
            }
            mori.streamTick(t); // 技の合間もずっと撃つ
        }

        private void begin(int kind) {
            action = kind;
            actionT = 0;
            mori.tactics.started(kind);
        }

        private void end(int cooldown) {
            mori.tactics.ended(action);
            gap = mori.isPhase2() ? 6 : 10; // 技と技の間に少しだけ連射の間をあける
            switch (action) {
                case SWARM -> swarm = cd(cooldown);
                case BARRAGE -> barrage = cd(cooldown);
                case CAGE -> cage = cd(cooldown);
                case MOON -> moon = cooldown;
                case BLOOM -> bloom = cd(cooldown);
                case RAIN -> rain = cd(cooldown);
                case LATTICE -> lattice = cd(cooldown);
                default -> { }
            }
            action = 0;
        }

        private void tickAction(LivingEntity t) {
            int at = actionT++;
            boolean p2 = mori.isPhase2();
            ServerLevel server = (ServerLevel) mori.level();
            switch (action) {
                case SWARM -> { // 乱舞: 2 Tick ごとに左右の翼から 1 匹ずつ（その間も連射は続く）
                    mori.flyAround(t, 0.0F, 0.2);
                    mori.streamTick(t);
                    if (at >= 6 && at % 2 == 0 && left > 0) {
                        mori.swarmBit(t, left % 2 == 0);
                        left--;
                    }
                    if (left <= 0 && at > 10) end(200);
                }
                case BARRAGE -> { // 幾何弾幕: 10 Tick 構えて（右手の先へ光が集まる）から、2 重の輪（4 波）・花（3 波）・渦巻き（40 Tick 流す）。終わりに大玉
                    mori.flyAround(t, 0.0F, 0.15);
                    if (at < 10) {
                        Fx.gather(server, glow(p2 ? C_CYAN : C_PALE, 0.3F, 10), mori.hand(), 2, 2.0, 8);
                        break;
                    }
                    at -= 10;
                    double cone = p2 ? 0.75 : 0.62, speed = p2 ? 0.5 : 0.42;
                    if (pattern == 0) {
                        if (at % 10 == 0 && at <= 30) {
                            int w = at / 10;
                            mori.barrageWave(t, 0, p2 ? 64 : 48, w * 0.13, cone, speed, MementoBulletEntity.SMALL, mori.bulletColor(w));
                            mori.barrageWave(t, 0, p2 ? 32 : 24, w * 0.13 + 0.1, cone * 0.5, speed * 1.15, MementoBulletEntity.BUTTERFLY, mori.bulletColor(w + 1));
                        }
                        if (at == 34) mori.largeShot(t, p2 ? 5 : 3);
                        if (at > 40) end(150);
                    } else if (pattern == 1) {
                        if (at % 12 == 0 && at <= 24) mori.barrageWave(t, 1, p2 ? 72 : 56, at * 0.1, cone * 0.8, speed * 0.9, MementoBulletEntity.BUTTERFLY, mori.bulletColor(at / 12));
                        if (at == 30) mori.largeShot(t, p2 ? 5 : 3);
                        if (at > 36) end(160);
                    } else {
                        int arms = p2 ? 8 : 6;
                        if (at < 40 && at % 2 == 0) {
                            for (int arm = 0; arm < arms; arm++) {
                                double rot = at * 0.22 + arm * Math.PI * 2 / arms;
                                mori.barrageWave(t, 2, 1, rot, cone, speed, MementoBulletEntity.SMALL, mori.bulletColor(arm));
                                mori.barrageWave(t, 2, 1, -rot, cone * 0.6, speed * 0.85, MementoBulletEntity.SMALL, mori.bulletColor(arm + 1));
                            }
                        }
                        if (at > 44) end(160);
                    }
                }
                case BLOOM -> { // 死蝶の大輪: 12 Tick 胸へ光を集めてから、全方位の花を 4 波（第 2 形態 5 波）
                    mori.flyAround(t, 1.0F, 0.1);
                    if (at < 12) {
                        Fx.gather(server, glow(p2 ? C_PINK : C_PALE, 0.4F, 12), mori.chest(), 3, 4.0, 10);
                        break;
                    }
                    at -= 12;
                    int waves = p2 ? 5 : 4, every = p2 ? 8 : 10;
                    if (at % every == 0 && at / every < waves) {
                        int w = at / every;
                        mori.bloomWave(w, p2 ? 5 : 4, p2 ? 34 : 28, p2 ? 6 : 5);
                        Fx.shockwave(server, mori.chest(), mori.bulletColor(w), 5.0F, 12);
                    }
                    if (at > every * waves + 10) end(230);
                }
                case LATTICE -> { // 死蝶の綾織: 15 Tick 構えてから、左右の翅から逆へ曲がる扇を 60 Tick（第 2 形態 80）
                    mori.flyAround(t, 0.0F, 0.12);
                    if (at < 15) {
                        for (boolean l : new boolean[]{true, false}) Fx.gather(server, glow(l ? C_CYAN : C_PINK, 0.3F, 10), mori.wingTip(l), 2, 2.5, 8);
                        break;
                    }
                    at -= 15;
                    int len = p2 ? 80 : 60;
                    if (at < len && at % (p2 ? 2 : 3) == 0) mori.latticeFan(t, at, p2 ? 9 : 7);
                    if (at > len + 5) end(260);
                }
                case RAIN -> { // 光蝶の雨: 空の魔法陣から 90 Tick（第 2 形態 120）降らせる
                    mori.flyAround(t, 2.0F, 0.2);
                    int len = p2 ? 120 : 90;
                    if (at >= 15 && at < 15 + len) mori.rain(rainCenter, 16.0, p2 ? 9 : 6, t, null, 0);
                    if (at > 20 + len) end(300);
                }
                case CAGE -> { // 檻: 並べて（20 Tick）、2 秒止め、合図で一斉に突っ込む（その間も連射は続く）
                    mori.flyAround(t, 2.0F, 0.15);
                    mori.streamTick(t);
                    if (at == 0) {
                        int n = p2 ? 16 : 12;
                        for (Vec3 slot : cageSlots(t, n)) cageBits.add(MementoBitEntity.cage(server, mori, t, mori.chest(), slot, MementoBitEntity.DAMAGE, p2));
                    }
                    if (at > 20 && at < 60 && at % 4 == 0) { // 予告: 檻から相手へ紫の線
                        for (MementoBitEntity b : cageBits) {
                            if (b.isAlive()) Fx.line(server, glow(p2 ? C_CYAN : C_VIOLET, 0.2F, 5), b.position(), aimAt(t), 1.2);
                        }
                    }
                    if (at == 20) mori.playSound(sfx("cage_lock"), 3.0F, 1.0F);
                    if (at == 60) {
                        for (MementoBitEntity b : cageBits) if (b.isAlive()) b.release();
                        cageBits.clear();
                        mori.level().broadcastEntityEvent(mori, EVENT_CAGE_RELEASE);
                        mori.playSound(SoundEvents.PHANTOM_BITE, 2.0F, 0.6F);
                        mori.playSound(sfx("cage_release"), 3.0F, 1.0F);
                    }
                    if (at > 70) end(260);
                }
                case MOON -> { // 黒死の月: 20 Tick で舞い上がって構え、100 Tick 数えて納める。数える間は安全地帯の外へ光の雨
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
                        Fx.mandala(server, moonCenter.add(0, 20.0, 0), C_PINK, 12.0F, 20 + MOON_COUNTDOWN, false, false);   // 空の月の前の大きな魔法陣
                        Fx.mandala(server, moonCenter.add(0, 19.8, 0), C_CYAN, 8.0F, 20 + MOON_COUNTDOWN, false, true);
                    }
                    mori.steer(moonCenter.add(0, HOVER_HEIGHT + 8.0, 0), 0.4);
                    mori.faceToward(moonCenter);
                    if (at >= 20 && (at - 20) % 20 == 0 && at < 20 + MOON_COUNTDOWN) mori.playSound(sfx("moon_bell"), 5.0F, 0.8F + (at - 20) * 0.004F);
                    if (at >= 20 && at < 10 + MOON_COUNTDOWN) mori.rain(moonCenter, MOON_RADIUS, 4, null, moonSafe, MOON_SAFE_RADIUS + 1.2);
                    if (at == 20 + MOON_COUNTDOWN) mori.moonFall(moonCenter, moonSafe);
                    if (at > 30 + MOON_COUNTDOWN) end(600);
                }
                case SPELL -> { // 終葬: 20 Tick 構えてから 1180 Tick、専用の弾幕（MementoFinale。2026-10-15 ユーザー指定「専用一番豪華な弾幕」）
                    mori.flyAround(t, 3.0F, 0.12);
                    if (at < 20) {
                        Fx.gather(server, glow(at % 2 == 0 ? C_PINK : C_CYAN, 0.5F, 14), mori.chest(), 4, 6.0, 12);
                        break;
                    }
                    int s = at - 20;
                    if (finale != null) finale.tick(t, s);
                    if (s >= mori.spellTicks() - 20) { // 終わり: 白い閃光
                        Vec3 c = mori.chest();
                        Fx.burst(server, glow(C_WHITE, 0.8F, 26), c, 50, 0.4);
                        Fx.shockwave(server, c, C_CYAN, 18.0F, 30);
                        server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
                        mori.playSound(sfx("form"), 4.0F, 1.2F);
                        mori.endSpell();
                        announce("message.automata_valkyries.memento_mori.spell_end", ChatFormatting.WHITE, ChatFormatting.BOLD);
                        action = 0;
                        swarm = Math.max(swarm, 60); barrage = Math.max(barrage, 40); bloom = Math.max(bloom, 120);
                        lattice = Math.max(lattice, 160); rain = Math.max(rain, 200); cage = Math.max(cage, 100);
                    }
                }
                default -> action = 0;
            }
        }
    }
}
