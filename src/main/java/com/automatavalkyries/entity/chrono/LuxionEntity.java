package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * クロノゲイザーの AEC 級英雄機「最光速【ルクシオン】」（全体仕様書 §4.1、2026-10-07 ユーザー指定で 1 体目に作る）。
 * 細身の白金の機械の騎士。HP {@value #MAX_HP}（「少し硬め」→ 2026-10-09 に 5,000 から倍に。最大 HP の上限は mixin/HealthCapMixin で 1,024 から上げてある）。即死系の守り（Eprotect）は付けるが、被弾の上限は無し（普通の攻撃で倒せる）。
 * 技（数値は【仮】。HP 50% で第 2 形態: 間隔が短くなり、光速三連閃が 5 連に）:
 * <ul>
 *   <li>斬撃: 光の剣（攻撃力 {@value #MELEE}）</li>
 *   <li>光速瞬動: 相手の背後・横へ一瞬で移り、斬る（残像の光の筋が残る）</li>
 *   <li>光速三連閃: 相手のまわりを瞬動しながら 3 連（第 2 形態 5 連）で斬る（1 回 {@value #FLURRY_HIT}）</li>
 *   <li>光刃の波: 離れた相手へ、まっすぐ飛ぶ光の刃（{@value #WAVE} ・貫通・射程 {@value #WAVE_RANGE}）</li>
 *   <li>光速突進: 1 秒の予告線のあと、20 ブロックを一直線に駆け抜ける（線上に {@value #DASH}）。第 2 形態は駆け抜けた後に折り返してもう一度（2 回）</li>
 * </ul>
 * 狙いは兵姫を優先（クロノゲイザー共通）。挑む相手がいなくなると 10 秒で退く（セリフつきの正規の退場）
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.automatavalkyries.AutomataValkyries.MODID)
public class LuxionEntity extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech, com.automatavalkyries.entity.RetreatableBoss {

    public static final float MAX_HP = 10000.0F, MELEE = 30.0F, FLURRY_HIT = 25.0F, WAVE = 30.0F, DASH = 40.0F;
    /** ルクシオンアクセラレータ: 蹴りが当たった相手へ、着弾の爆風（半径 {@link #ACCEL_BLAST_RADIUS}）。数値は【仮】 */
    public static final float ACCEL_KICK = 120.0F, ACCEL_BLAST = 40.0F;
    static final double ACCEL_BLAST_RADIUS = 5.0, ACCEL_HEIGHT = 14.0, ACCEL_SPEED = 2.4;
    /**
     * ルクシオンアクセラレータのダメージの種類: 防御貫通（防具・防護のエンチャント・盾を通る。兵姫のフォートレスフレームの物理 50% カットも
     * 防具を通るダメージには掛からない。2026-10-09 ユーザー指定「フォートレスフレーム強いので大技を防御貫通に」）
     */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> ACCEL_DAMAGE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.automatavalkyries.AutomataValkyries.MODID, "luxion_accelerator"));
    /** 放つ所の相手からの水平の距離（これより近ければ跳び退いて離れる） */
    static final double ACCEL_RANGE = 18.0;
    /** 飛び上がる・狙う（最後の {@link #ACCEL_LOCK} Tick は狙いを固定して避けられるように）・蹴りの最長・着地の Tick */
    static final int ACCEL_LEAP_TICKS = 20, ACCEL_AIM_TICKS = 32, ACCEL_LOCK = 14, ACCEL_KICK_MAX = 24, ACCEL_LAND_TICKS = 14;
    public static final double WAVE_RANGE = 24.0, DASH_LENGTH = 20.0;
    /** 第 2 形態の 2 回目の光速突進の構え（Tick） */
    static final int DASH_AGAIN_WINDUP = 8;
    /** ダメージを上げ始めるプレイヤーの最大 HP（{@link #scaledFor}） */
    public static final float PLAYER_BASE_HP = 20.0F;
    /**
     * 体の大きさ（モデルの倍率。身長 5 ブロック、2026-10-08 ユーザー指定）。描画（client/LuxionRenderer）と、技の届く範囲・光の出る高さはこれに合わせる。
     * MELEE_REACH = 斬撃の届く距離、BESIDE = 相手のそばへ移るときの間合い
     */
    public static final float SIZE = 2.0F;
    static final double MELEE_REACH = 5.0, BESIDE = 3.0, STEP_HEIGHT = 3.5;
    /** 動きの合図（描画: 斬る腕の振り・突進の構え） */
    public static final byte EVENT_SLASH = 70, EVENT_DASH_WINDUP = 71;
    /**
     * モーションの合図（2026-10-08 ユーザー指定「ルクシオンのモーション全部作って」）: 斬撃の 3 種（袈裟・逆袈裟・横一文字）と三連閃の締めの振り下ろし、
     * 瞬動の前の反り・現れた後の居合の振り抜き、三連閃の締めの振りかぶり、光刃の波の溜めと振り抜き、突進の後の滑り止まり、大きな被弾ののけぞり
     */
    public static final byte EVENT_SLASH_RISING = 72, EVENT_SLASH_FLAT = 73, EVENT_SLASH_DOWN = 74,
            EVENT_BLINK_PREP = 75, EVENT_BLINK_ARRIVE = 76, EVENT_FLURRY_RAISE = 77,
            EVENT_WAVE_CHARGE = 78, EVENT_WAVE_RELEASE = 79, EVENT_DASH_END = 80, EVENT_BIG_HURT = 81;
    /** 三連閃を始めた（締めの振り下ろしまで刀を両手で持つ。2026-10-09 ユーザー指定「三連撃は両手で刀を振るって」） */
    public static final byte EVENT_TWO_HAND = 82;
    /**
     * 大技ルクシオンアクセラレータ（第 2 形態で解禁。2026-10-09 ユーザー指定）の合図: 大きく飛び上がる・頂点で狙いを定める（予測線と加速の光のレール）・
     * 蹴り穿つ（ライダーキック）・着弾
     */
    public static final byte EVENT_ACCEL_LEAP = 83, EVENT_ACCEL_AIM = 84, EVENT_ACCEL_KICK = 85, EVENT_ACCEL_IMPACT = 86;
    /** 演出の段階（同期）: 降臨（光の柱から膝をついて降り、立って剣を払い翼を開く）・第 2 形態への変身（剣を天へ掲げて浮かぶ。この間は攻撃を受けない）・退く（剣を納め、翼で身を包んで光に溶ける） */
    public static final byte STAGE_NONE = 0, STAGE_INTRO = 1, STAGE_TRANSFORM = 2, STAGE_RETREAT = 3;
    public static final int INTRO_TICKS = 40, TRANSFORM_TICKS = 30, RETREAT_OUT_TICKS = 30;
    private static final Vector3f WHITE_GOLD = new Vector3f(1.0F, 0.94F, 0.72F), RED = new Vector3f(1.0F, 0.17F, 0.29F);
    /** 光の粒の色（weapon/Fx の自作の光・星の粒。2026-10-08 ユーザー指定「光のパーティクルも豪華に」） */
    private static final int C_WHITE = 0xFFFFFF, C_WG = 0xFFF0B8, C_GOLD = 0xE8C060, C_RED = 0xFF2A4A;

    private static com.automatavalkyries.particle.FxOptions glow(int color, float size, int life) {
        return com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), color, size, life);
    }

    private static com.automatavalkyries.particle.FxOptions star(int color, float size, int life) {
        return com.automatavalkyries.weapon.Fx.opt(com.automatavalkyries.registry.ModParticles.STAR.get(), color, size, life);
    }

    private static final EntityDataAccessor<Boolean> PHASE2 = SynchedEntityData.defineId(LuxionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> STAGE = SynchedEntityData.defineId(LuxionEntity.class, EntityDataSerializers.BYTE);
    /**
     * 撃破された（同期）。クライアントは撃破の合図（イベント 3）や手元の HP（Eprotect の見張りが 0 を読み替える）に頼らず、これで撃破の演出を走らせる
     * （2026-10-08「死亡時に演出がうまくいかない」: 立ったまま消えた）
     */
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(LuxionEntity.class, EntityDataSerializers.BOOLEAN);
    /** 空中にとどまって戦っている（同期。描画: 脚を垂らし、翼とブースターを開く。2026-10-08「空中の敵に、空中にとどまって攻撃」） */
    private static final EntityDataAccessor<Boolean> HOVER = SynchedEntityData.defineId(LuxionEntity.class, EntityDataSerializers.BOOLEAN);
    /** ルクシオンアクセラレータの蹴りの向きの、水平から下への傾き（ラジアン。体ごと傾けて描く） */
    private static final EntityDataAccessor<Float> ACCEL_PITCH = SynchedEntityData.defineId(LuxionEntity.class, EntityDataSerializers.FLOAT);
    /** サーバー: 空中で向かう位置と、とどまる残り Tick（技の目標が毎 Tick 延ばす） */
    private Vec3 hoverTarget;
    private int hoverTicks;
    /** クライアント: 撃破の演出の経過 Tick（0 = 生きている） */
    public int defeatTicks;

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    /** 挑む相手（64 ブロック以内）がいなくなって 10 秒で退く */
    private final com.automatavalkyries.entity.BossRetreatWatch retreatWatch = new com.automatavalkyries.entity.BossRetreatWatch(64.0, 200);
    /** 回想のトライアルのボス（{@code trial/TrialManager}）: 自分では退かず、保存もしない。攻撃は {@link #trialAttack} 倍 */
    private boolean trialBoss;
    /** 退く時が来た（降臨・変身の演出が終わったら退く） */
    private boolean retreatPending;
    private float trialAttack = 1.0F;
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.automata_valkyries.luxion"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** クライアント: 最後に斬った・突進を構えた Tick（描画用） */
    public int slashTick = -100, windupTick = -100;
    /** クライアント: 最後の技のモーション（合図の番号）と始まった Tick、大きな被弾の Tick、今の演出の段階が始まった Tick */
    public byte action;
    public int actionTick = -100, bigHurtTick = -100, stageStart;
    /** クライアント: 三連閃を始めた Tick（両手持ちの始まり） */
    public int twoHandTick = -1000;
    /** サーバー: 今の演出の段階の経過 Tick、降臨を済ませたか（読み込んだものは済ませた扱い）、斬撃の型の順番 */
    private int stageTicks;
    private boolean introDone;
    private int combo;
    /** クライアント: 背の戦闘機型ブースターの展開（0 = 背に収まる、1 = 後ろへ開いて翼を広げる）。戦闘中（相手を捉えている＝aggressive）に開く */
    public float deploy, deployO;
    /** クライアント: 第 2 形態の背の機構の豪華さ（0 → 1 へ 1.5 秒で）。光の翼・光輪・大きくなる翼とブースター（2026-10-08 ユーザー指定） */
    public float glory, gloryO;
    /** クライアント: 残光（瞬動・突進・速く動いた跡に残る半透明の自分の姿。2026-10-08 ユーザー指定「残光を自作して」） */
    public final java.util.ArrayList<Afterimage> afterimages = new java.util.ArrayList<>();
    public static final int AFTERIMAGE_LIFE = 14;
    /**
     * クライアント: モデルが決めた今の姿勢（光のマント・ブースターの光・光の翼を体に合わせて描くため。
     * {@code ChronoGazerModel#animateLuxion} が毎フレーム書き、{@code LuxionRenderer} が読む。2026-10-09）。
     * 全体の上下（px）、胴の回転、背の推進器の傾き（ラジアン）、光の翼の開き・後ろへの流れ、畳み（0〜1）、突進の構え（0〜1）、ブースターの開き（0〜1）、
     * 技の勢いで強まるブースターの噴射とマントのはためき（0〜）
     */
    public float animRootY, animBodyX, animBodyY, animBodyZ, animThrustersX, animSpread, animSweep, animClose, animWindup, animBurst, animFlare, animOpen;
    public boolean animHover;
    private double lastX = Double.NaN, lastY, lastZ;

    /** 残光 1 つ（位置・体の向き・生まれた Tick） */
    public record Afterimage(double x, double y, double z, float yaw, int born) {}

    private void trackAfterimages() {
        if (!Double.isNaN(lastX)) {
            double dx = getX() - lastX, dy = getY() - lastY, dz = getZ() - lastZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 1.5) { // 瞬動: 通った道に並べて残す
                int n = (int) Math.min(8, Math.ceil(dist / 1.2));
                for (int i = 0; i < n; i++) {
                    double t = i / (double) n;
                    afterimages.add(new Afterimage(lastX + dx * t, lastY + dy * t, lastZ + dz * t, yBodyRot, tickCount - (n - i) / 3));
                }
            } else if (dist > 0.22 && tickCount % 2 == 0) { // 速く動いている間
                afterimages.add(new Afterimage(lastX, lastY, lastZ, yBodyRotO, tickCount));
            }
        }
        lastX = getX();
        lastY = getY();
        lastZ = getZ();
        afterimages.removeIf(a -> tickCount - a.born() > AFTERIMAGE_LIFE);
        while (afterimages.size() > 24) afterimages.remove(0);
    }

    public LuxionEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        setMaxUpStep(2.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 15.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, MELEE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE2, false);
        entityData.define(STAGE, STAGE_NONE);
        entityData.define(DEFEATED, false);
        entityData.define(HOVER, false);
        entityData.define(ACCEL_PITCH, 0.0F);
    }

    public boolean isPhase2() {
        return entityData.get(PHASE2);
    }

    /** 今の演出の段階（{@link #STAGE_NONE} なら普段どおり） */
    public boolean isHovering() {
        return entityData.get(HOVER);
    }

    /** 空中の at へ向かって、そこにとどまる（数 Tick。続けるなら毎 Tick 呼ぶ） */
    void hoverAt(Vec3 at) {
        hoverTarget = at;
        hoverTicks = 6;
    }

    /**
     * 空中にとどまる: 動く前に速さを決めるので重力に負けない（noGravity は Eprotect の状態の見張りに戻されるので使わない）。
     * 向かう位置へ、1 Tick に最大 0.6 ブロックで寄る
     */
    private void tickHover() {
        boolean hover = hoverTicks > 0 && hoverTarget != null && stage() == STAGE_NONE && isAlive() && !defeatedNow();
        if (hover) {
            hoverTicks--;
            Vec3 d = hoverTarget.subtract(position()).scale(0.3);
            if (d.length() > 0.6) d = d.normalize().scale(0.6);
            setDeltaMovement(d);
            resetFallDistance();
            getNavigation().stop();
        } else {
            hoverTicks = 0;
        }
        if (entityData.get(HOVER) != hover) entityData.set(HOVER, hover);
    }

    /** e の足元から下の地面までの高さ（8 ブロックまで） */
    static double heightAboveGround(Entity e) {
        net.minecraft.core.BlockPos.MutableBlockPos p = e.blockPosition().mutable();
        for (int i = 0; i < 8; i++) {
            if (!e.level().getBlockState(p).getCollisionShape(e.level(), p).isEmpty()) {
                return Math.max(0.0, e.getY() - (p.getY() + e.level().getBlockState(p).getCollisionShape(e.level(), p).max(net.minecraft.core.Direction.Axis.Y)));
            }
            p.move(0, -1, 0);
        }
        return 8.0;
    }

    public boolean isDefeatedSynced() {
        return entityData.get(DEFEATED);
    }

    /** 撃破の演出の経過（描画用。0 = 生きている） */
    public float defeatProgress(float partialTick) {
        return defeatTicks > 0 ? defeatTicks - 1 + partialTick : 0.0F;
    }

    public byte stage() {
        return entityData.get(STAGE);
    }

    private void startStage(byte stage) {
        entityData.set(STAGE, stage);
        stageTicks = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STAGE.equals(key)) stageStart = tickCount;
    }

    /** 演出の間は動かず、技も出さない（AI ごと止める） */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || stage() != STAGE_NONE || isDefeatedSynced();
    }

    /** 撃破されたか（HP が残ったまま即死 MOD で倒れた場合も含む） */
    private boolean defeatedNow() {
        return isDefeatedSynced() || deathTime > 0;
    }

    @Override
    public boolean isPushable() {
        return !defeatedNow() && super.isPushable();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!defeatedNow()) super.knockback(strength, x, z);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (stage() == STAGE_TRANSFORM && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // 変身の間は受けない
        if (defeatedNow()) return false; // 撃破の演出の間は受けない（のけぞり・押し戻しも無し）
        boolean hurt = super.hurt(source, amount);
        return hurt;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Phase2", isPhase2());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        introDone = true; // 読み込んだもの（もう降りてきている）は降臨し直さない
        if (tag.getBoolean("Phase2")) entityData.set(PHASE2, true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new LuxionAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this, 24.0); // 上・下 24 ブロックまで気づく
    }

    @Override
    public void restoreGoalsIfEmpty() {
        if (goalSelector.getAvailableGoals().isEmpty()) registerGoals();
    }

    // ---- Eprotect（即死系の守り。被弾の上限は無し） ----

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
        if (level().isClientSide) logClientRemoval();
    }

    /** 手元（クライアント）で個体が消えた記録: 画面から消えた原因（誰が消したか）を、サーバーで生きているのに倒れて見える件の調査に残す（2026-10-22） */
    private void logClientRemoval() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        for (int i = 2; i < Math.min(stack.length, 12); i++) frames.append("\n    at ").append(stack[i]);
        com.automatavalkyries.AutomataValkyries.LOGGER.info("[luxion] client: removed from the local world (id {}, reason {}, health {}, defeated {}, deathTime {}){}",
                getId(), getRemovalReason(), DirectRead.health(this), entityData.get(DEFEATED), deathTime, frames);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (protect.shouldBlockRemoval(reason)) return;
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) {
            // クライアントは撃破の合図（イベント 3）で呼ばれる。ここで断る（Eprotect の die の見張りも含む）と手元の HP が満タンに戻り、
            // さらに手元の Eprotect が撃破を知らないままだと getHealth の見張りが HP 0 を前の HP に読み替えて死にかけと見なさず、
            // 撃破の演出が走らずに立ったまま消えた（2026-10-08）。手元でも撃破を記録し、HP 0 のままにして演出（tickDeath）を走らせる
            protect.markDefeated();
            setHealth(0.0F);
            return;
        }
        if (protect.interceptDie()) return; // 被弾を通らない die() の直接呼び出しは即死攻撃として断る
        entityData.set(DEFEATED, true);
        super.die(source);
    }

    /** 撃破の落とし物が出たか（他 MOD が落とし物のイベントを取り消す・空にすると出ない。2026-10-08「アイテム落ちないです」） */
    private boolean lootDropped;

    /**
     * 落とし物のイベントの最後（取り消されたものも受ける）: 取り消されていたら戻し、空にされていたら撃破の演出の終わりに自分で出し直す。
     * どちらが起きたかはログに残す
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof LuxionEntity lux)) return;
        if (event.isCanceled()) {
            com.automatavalkyries.AutomataValkyries.LOGGER.warn("[luxion] another mod cancelled the boss drops; restoring them ({} items)", event.getDrops().size());
            event.setCanceled(false);
        }
        if (event.getDrops().isEmpty()) {
            com.automatavalkyries.AutomataValkyries.LOGGER.warn("[luxion] the boss drops were emptied by another mod; they will be rolled again");
        } else {
            lux.lootDropped = true;
        }
    }

    /** 落とし物が出ていなければ、ドロップの表をもう一度引いて出す */
    private void ensureLoot() {
        if (lootDropped || !(level() instanceof ServerLevel)) return;
        lootDropped = true;
        DamageSource source = getLastDamageSource() != null ? getLastDamageSource() : damageSources().generic();
        dropFromLootTable(source, getLastHurtByMob() instanceof Player);
        com.automatavalkyries.AutomataValkyries.LOGGER.info("[luxion] rolled the boss drops again after they were lost");
    }

    @Override
    protected void tickDeath() {
        if (protect.refuseDeathTick()) return; // 撃破を認めていないのに演出を回された（The King Sword 等）
        ++deathTime;
        if (!level().isClientSide && !entityData.get(DEFEATED)) { // die() を通らずに倒れた（HP を直接 0 にされた等）ときも演出を出す
            entityData.set(DEFEATED, true);
            com.automatavalkyries.AutomataValkyries.LOGGER.info("[luxion] defeated without die(); playing the defeat motion anyway");
        }
        if (!level().isClientSide && stage() != STAGE_NONE) startStage(STAGE_NONE);
        if (!level().isClientSide && deathTime >= 16 && deathTime % 2 == 0 && level() instanceof ServerLevel server) { // 剣を突き立てて膝をついたまま、光になって崩れていく
            com.automatavalkyries.weapon.Fx.burst(server, glow(C_WG, 0.5F, 24), position().add(0, (0.4 + random.nextDouble() * 1.6) * SIZE, 0), 6, 0.06);
            com.automatavalkyries.weapon.Fx.one(server, star(C_GOLD, 0.35F, 30), position().add(random.nextGaussian() * 0.4, 0.3 + random.nextDouble() * 1.8, random.nextGaussian() * 0.4), new Vec3(0, 0.08, 0));
        }
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++) {
                level().addParticle(ParticleTypes.END_ROD, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0.08, 0);
            }
        } else if (deathTime >= 40 && !DirectRead.isRemoved(this)) {
            if (level() instanceof ServerLevel server) {
                Vec3 c = position().add(0, 1.2 * SIZE, 0);
                com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.8F, 50), c, 80, 0.4);
                com.automatavalkyries.weapon.Fx.burst(server, glow(C_WHITE, 1.0F, 30), c, 30, 0.3);
                com.automatavalkyries.weapon.Fx.shockwave(server, position().add(0, 0.1, 0), C_GOLD, 7.0F, 24);
                server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.5), getZ(), 1, 0, 0, 0, 0);
            }
            ensureLoot();
            protect.finishDeath();
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public Component getDefeatSpeech() {
        return Component.translatable("entity.automata_valkyries.luxion.defeat_speech").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
    }

    @Override
    public SoundEvent getDefeatSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    // ---- 毎 Tick: 保護・ボスバー・第 2 形態・退く ----

    /** 撃破の印が立ったのに HP が戻って演出が始まらない状態の続き Tick 数 */
    private int staleDefeatTicks;

    /**
     * 撃破の印（DEFEATED）が立っているのに、実際の HP が残り、死亡演出（deathTime）も始まらないまま続くなら、撃破は取り消された。
     * 他 MOD が死亡を取り消して HP を戻すと、印だけが残ってクライアントでは倒れた演出になり（サーバーでは生きていて、リログで直る。2026-10-22）、
     * Eprotect も撃破済みとして保護を止めていた。印と保護を戻す
     */
    private void revokeStaleDefeat() {
        if (!entityData.get(DEFEATED) || deathTime > 0 || !(DirectRead.health(this) > 0.0F)) {
            staleDefeatTicks = 0;
            return;
        }
        if (++staleDefeatTicks < 10) return;
        staleDefeatTicks = 0;
        entityData.set(DEFEATED, false);
        protect.cancelDefeat();
        com.automatavalkyries.AutomataValkyries.LOGGER.warn("[luxion] the defeat was undone (health {} remains); cleared the defeated mark", DirectRead.health(this));
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            if (entityData.get(DEFEATED)) {
                if (defeatTicks == 0) { // 手元の Eprotect にも撃破を教える（HP 0 をそのまま読ませる）
                    protect.markDefeated();
                    com.automatavalkyries.AutomataValkyries.LOGGER.info("[luxion] client: defeat motion started (health {}, deathTime {})", DirectRead.health(this), deathTime);
                }
                defeatTicks++;
                for (int i = 0; i < 2 + defeatTicks / 8; i++) { // 光になって崩れていく
                    level().addParticle(ParticleTypes.END_ROD, getRandomX(0.8), getY() + random.nextDouble() * 1.8, getRandomZ(0.8), 0, 0.06, 0);
                }
            }
            protect.clientTick();
            deployO = deploy;
            deploy = Mth.clamp(deploy + (isAggressive() ? 0.06F : -0.04F), 0.0F, 1.0F);
            trackAfterimages();
            gloryO = glory;
            glory = Mth.clamp(glory + (isPhase2() ? 1.0F / 30.0F : -0.05F), 0.0F, 1.0F);
            if (glory > 0.5F && tickCount % 2 == 0) { // 光の翼から舞う光の粒
                Vec3 side = Vec3.directionFromRotation(0, yBodyRot + 90.0F);
                double s = (random.nextBoolean() ? 1 : -1) * (0.8 + random.nextDouble() * 2.2) * SIZE;
                Vec3 p = position().add(side.scale(s)).add(Vec3.directionFromRotation(0, yBodyRot).scale(-0.6 * SIZE)).add(0, (2.2 + random.nextDouble() * 1.8) * SIZE, 0);
                level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, -0.02, 0);
            }
            if (tickCount % 2 == 0) { // 背の光の尾のきらめき
                Vec3 back = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(-0.4 * SIZE)).add(0, 2.0 * SIZE, 0);
                level().addParticle(ParticleTypes.END_ROD, back.x, back.y, back.z, 0, -0.01, 0);
            }
            return;
        }
        revokeStaleDefeat();
        protect.tick();
        float ratio = protect.healthRatio();
        // 撃破されたら HP の値に関わらずボスバーを 0 に（即死 MOD で HP が残ったまま倒れると減らず、ボスバーの砕ける演出が始まらなかった。2026-10-08）
        bossEvent.setProgress(entityData.get(DEFEATED) || deathTime > 0 ? 0.0F : ratio);
        if (!introDone) { // 降臨（光の柱から降りてくる）
            introDone = true;
            if (isAlive()) startStage(STAGE_INTRO);
        }
        tickStage();
        tickHover();
        if (defeatedNow()) { // 撃破されたらその場で止まる（歩かない・向きを変えない・技を出さない。2026-10-08 ユーザー指定）
            getNavigation().stop();
            setTarget(null);
            setAggressive(false);
            Vec3 v = getDeltaMovement();
            setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0); // 落ちるだけ
            xxa = zza = 0.0F;
            setYHeadRot(yBodyRot);
        }
        if (!isPhase2() && ratio <= 0.5F && isAlive()) {
            entityData.set(PHASE2, true);
            startStage(STAGE_TRANSFORM); // 剣を天へ掲げて浮かび、翼と光輪を開く（1.5 秒、攻撃を受けない）
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 1.6F);
            if (level() instanceof ServerLevel server) {
                Vec3 c = position().add(0, 1.4 * SIZE, 0);
                com.automatavalkyries.weapon.Fx.mandala(server, position().add(0, 0.1, 0), C_GOLD, 5.0F, 40, false, true);
                com.automatavalkyries.weapon.Fx.shockwave(server, position().add(0, 0.1, 0), C_WHITE, 8.0F, 20);
                com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.7F, 40), c, 60, 0.35);
                com.automatavalkyries.weapon.Fx.burst(server, glow(C_RED, 0.8F, 30), c, 30, 0.25);
                for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 64 * 64)) {
                    p.displayClientMessage(Component.translatable("message.automata_valkyries.luxion.phase2").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                }
            }
        }
        tickRetreat();
    }

    /** 演出の段階を進める（光の柱・変身の浮遊・退くときの光に溶ける粒） */
    private void tickStage() {
        byte stage = stage();
        if (stage == STAGE_NONE || !(level() instanceof ServerLevel server)) return;
        int t = stageTicks++;
        getNavigation().stop();
        Vec3 c = position().add(0, 1.2 * SIZE, 0);
        if (stage == STAGE_INTRO) {
            if (t == 0) { // 天から降りる光の柱
                com.automatavalkyries.weapon.Fx.line(server, glow(C_WHITE, 1.2F, 20), position().add(0, 24, 0), position(), 0.5);
                com.automatavalkyries.weapon.Fx.line(server, glow(C_WG, 2.0F, 26), position().add(0, 24, 0), position(), 0.8);
                playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.4F);
            }
            if (t == 5) { // 膝をついて降り立つ
                com.automatavalkyries.weapon.Fx.shockwave(server, position().add(0, 0.1, 0), C_WHITE, 5.0F, 16);
                com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.5F, 30), position().add(0, 0.3, 0), 30, 0.3);
                playSound(SoundEvents.ANVIL_LAND, 0.8F, 1.6F);
            }
            if (t == 27) playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.2F); // 剣を払う
            if (t == 31) { // 翼を開く
                com.automatavalkyries.weapon.Fx.burst(server, star(C_GOLD, 0.45F, 26), c.add(0, 0.6, 0), 24, 0.25);
                playSound(SoundEvents.ELYTRA_FLYING, 0.6F, 1.8F);
            }
            if (t >= INTRO_TICKS) startStage(STAGE_NONE);
        } else if (stage == STAGE_TRANSFORM) {
            setDeltaMovement(0, t < 14 ? 0.08 : 0.0, 0); // ふわりと浮かぶ（毎 Tick 動く前に速さを決めるので重力に負けない。noGravity は Eprotect の状態の見張りに戻される）
            if (t % 3 == 0) com.automatavalkyries.weapon.Fx.line(server, glow(C_WG, 0.5F, 10), c.add(0, 1.2 * SIZE, 0), c.add(0, 10, 0), 0.6); // 掲げた剣から天への光
            if (t == 18) { // 翼と光輪が開く
                com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.8F, 40), c.add(0, 0.8, 0), 50, 0.4);
                com.automatavalkyries.weapon.Fx.mandala(server, c.add(0, 1.0, 0), C_RED, 3.0F, 30, true, false);
                server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.6), getZ(), 1, 0, 0, 0, 0);
                playSound(SoundEvents.TOTEM_USE, 1.0F, 1.6F);
            }
            if (t >= TRANSFORM_TICKS) startStage(STAGE_NONE);
        } else if (stage == STAGE_RETREAT) {
            setDeltaMovement(0, t >= 18 ? 0.03 : 0.0, 0);
            if (t >= 14) { // 光に溶ける
                com.automatavalkyries.weapon.Fx.burst(server, glow(C_WG, 0.6F, 22), position().add(0, (0.3 + random.nextDouble() * 2.0) * SIZE, 0), 4 + t / 4, 0.12);
                com.automatavalkyries.weapon.Fx.one(server, star(C_GOLD, 0.4F, 30), c.add(random.nextGaussian() * 0.6, random.nextGaussian() * 0.6, random.nextGaussian() * 0.6), new Vec3(0, 0.1, 0));
            }
            if (t == 8) playSound(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0F, 1.4F); // 剣を納める
            if (t >= RETREAT_OUT_TICKS) {
                startStage(STAGE_NONE);
                protect.triggerDespawn();
            }
        }
    }

    private void tickRetreat() {
        if (trialBoss) return; // 回想のトライアルのボスは、トライアルの側が退かせる（クリエイティブで試しても自分で消えない）
        if (retreatWatch.tick(this)) retreatPending = true; // 挑む相手が全員倒れた瞬間を覚えておく（生き返ってからも退く）
        if (retreatPending && stage() == STAGE_NONE) beginRetreat(); // 降臨・変身の途中では退かない
    }

    /** 撤退の号令: 挑む相手がいなくなったときと同じく、剣を納め、翼で身を包んでから退く */
    @Override
    public boolean beginRetreat() {
        if (!isAlive() || defeatedNow() || stage() == STAGE_RETREAT || protect.isDespawning()) return false;
        startStage(STAGE_RETREAT);
        playSound(SoundEvents.BEACON_DEACTIVATE, 1.5F, 1.4F);
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

    /** 名前を付けたらボスバーの名前も（回想のトライアルの「回想の ○○」） */
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
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_DASH_WINDUP) windupTick = tickCount;
        else if (id == EVENT_BIG_HURT) bigHurtTick = tickCount;
        else if (id == EVENT_TWO_HAND) twoHandTick = tickCount;
        else if (id >= EVENT_ACCEL_LEAP && id <= EVENT_ACCEL_IMPACT) {
            action = id;
            actionTick = tickCount;
        }
        else if (id >= EVENT_SLASH && id <= EVENT_DASH_END) {
            action = id;
            actionTick = tickCount;
            if (id == EVENT_SLASH || id == EVENT_SLASH_RISING || id == EVENT_SLASH_FLAT || id == EVENT_SLASH_DOWN) slashTick = tickCount;
        } else super.handleEntityEvent(id);
    }

    // ---- 技 ----

    /** 斬撃の型（袈裟 → 逆袈裟 → 横一文字の順。2026-10-08） */
    private static final byte[] COMBO = {EVENT_SLASH, EVENT_SLASH_RISING, EVENT_SLASH_FLAT};

    /**
     * プレイヤーへのダメージは最大 HP に応じて上げる: 最大 HP 20 を基準に、それを超えた分だけ比例して大きくする（最大 HP 200 なら 10 倍）。
     * HP を大きく盛ったプレイヤーにも同じ割合で効かせるため（2026-10-08 ユーザー指定。比例の決め方は【仮】）。兵姫・モブはそのまま
     */
    public static float scaledFor(LivingEntity target, float damage) {
        if (!(target instanceof Player player)) return damage;
        float maxHp = player.getMaxHealth();
        return Float.isFinite(maxHp) ? damage * Math.max(1.0F, maxHp / PLAYER_BASE_HP) : damage;
    }

    /** 技のダメージ: プレイヤーには最大 HP に応じて上げ（{@link #scaledFor}）、回想のトライアルではさらに倍率を掛ける */
    float damageFor(LivingEntity target, float damage) {
        return scaledFor(target, damage * trialAttack);
    }

    /** 光の剣で斬る（型は順番に回す） */
    void slash(float damage) {
        slash(damage, COMBO[combo++ % COMBO.length]);
    }

    /** 光の剣で斬る（前方の扇・2.8 ブロック）。motion は斬撃の型の合図 */
    void slash(float damage, byte motion) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(MELEE_REACH, STEP_HEIGHT, MELEE_REACH), ChronoGazer::canHurt)) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (to.length() > MELEE_REACH + 0.3 + e.getBbWidth() * 0.5 || (to.lengthSqr() > 0.25 && to.normalize().dot(look) < 0.3)) continue;
            e.hurt(damageSources().mobAttack(this), damageFor(e, damage));
            e.knockback(0.4, -look.x, -look.z);
        }
        // 光の弧（刀の軌跡）と、白い芯・金の縁・赤い火花
        Vec3 arcAt = position().add(0, 1.3 * SIZE, 0).add(look.scale(1.6 * SIZE));
        com.automatavalkyries.weapon.Fx.slash(server, arcAt, C_WG, 2.6F * SIZE, 8, (float) (random.nextGaussian() * 0.5));
        com.automatavalkyries.weapon.Fx.slash(server, arcAt, C_WHITE, 1.8F * SIZE, 6, 0.0F);
        for (int i = -4; i <= 4; i++) {
            Vec3 dir = Vec3.directionFromRotation(0, getYRot() + i * 13);
            Vec3 p = position().add(0, 1.35 * SIZE, 0).add(dir.scale(2.3 * SIZE));
            com.automatavalkyries.weapon.Fx.one(server, glow(i == 0 ? C_WHITE : C_WG, 0.5F, 10), p, dir.scale(0.05));
            if (i % 2 == 0) com.automatavalkyries.weapon.Fx.one(server, star(i == 0 ? C_RED : C_GOLD, 0.35F, 14), p, dir.scale(0.12).add(0, 0.05, 0));
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
        level().broadcastEntityEvent(this, motion);
    }

    /** 光速瞬動: to へ一瞬で移り、通った道に光の筋（残像）を残す */
    void blink(Vec3 to) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = position().add(0, 1.2 * SIZE, 0);
        Vec3 dest = to.add(0, 1.2 * SIZE, 0);
        double len = from.distanceTo(dest);
        // 光の筋（白い芯と金のにじみ）、通り道の星、出た所と着いた所の光の輪
        com.automatavalkyries.weapon.Fx.line(server, glow(C_WHITE, 0.45F, 12), from, dest, 0.35);
        com.automatavalkyries.weapon.Fx.line(server, glow(C_WG, 0.9F, 16), from, dest, 0.6);
        com.automatavalkyries.weapon.Fx.line(server, star(C_GOLD, 0.3F, 20), from, dest, 1.1);
        com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.45F, 18), from, 14, 0.18);
        com.automatavalkyries.weapon.Fx.shockwave(server, to.add(0, 0.1, 0), C_WG, 2.2F * SIZE, 10);
        com.automatavalkyries.weapon.Fx.burst(server, glow(C_WHITE, 0.6F, 10), dest, 10, 0.12);
        teleportTo(to.x, to.y, to.z);
        resetFallDistance();
        if (hoverTicks > 0) hoverTarget = to; // 空中で瞬動したら、着いた所にとどまる（元の位置へ戻らない）
        server.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.8F);
    }

    /** 相手のまわり（距離 dist、角度 angle）の、立てる場所。見つからなければ相手の足元 */
    Vec3 spotAround(LivingEntity t, double angle, double dist) {
        for (int i = 0; i < 6; i++) {
            double a = angle + i * 0.9;
            Vec3 p = t.position().add(Math.cos(a) * dist, 0.0, Math.sin(a) * dist);
            AABB box = getDimensions(getPose()).makeBoundingBox(p);
            if (level().noCollision(this, box)) return p;
        }
        return t.position();
    }

    /** 光刃の波: 相手へまっすぐ飛ぶ光の刃（射程 {@link #WAVE_RANGE}・貫通） */
    void wave(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = position().add(0, 1.3 * SIZE, 0);
        Vec3 dir = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        Vec3 reach = from.add(dir.scale(WAVE_RANGE));
        for (double d = 1.0; d <= WAVE_RANGE; d += 0.6) {
            Vec3 p = from.add(dir.scale(d));
            if (!level().getBlockState(net.minecraft.core.BlockPos.containing(p)).getCollisionShape(level(), net.minecraft.core.BlockPos.containing(p)).isEmpty()) {
                reach = p;
                break;
            }
            for (int k = -2; k <= 2; k++) {
                Vec3 q = p.add(side.scale(k * 0.4)).add(0, -Math.abs(k) * 0.15, 0);
                com.automatavalkyries.weapon.Fx.one(server, glow(k == 0 ? C_WHITE : C_WG, k == 0 ? 0.6F : 0.4F, 10), q, dir.scale(0.08));
            }
            if (((int) (d / 0.6)) % 3 == 0) com.automatavalkyries.weapon.Fx.one(server, star(C_GOLD, 0.35F, 16), p, Vec3.ZERO);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.2), ChronoGazer::canHurt)) {
                if (hit.add(e)) e.hurt(damageSources().mobAttack(this), damageFor(e, WAVE));
            }
        }
        LuxionFxEntity.spawn(server, LuxionFxEntity.WAVE, from.add(dir.scale(0.8)), reach, isPhase2()); // 三日月の光の刃（自作エフェクト）
        playSound(SoundEvents.TRIDENT_RIPTIDE_1, 1.0F, 1.6F);
        level().broadcastEntityEvent(this, EVENT_WAVE_RELEASE);
    }

    /** 光速突進の予告線 */
    void dashTelegraph(Vec3 dir) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = position().add(0, 0.3, 0);
        com.automatavalkyries.weapon.Fx.line(server, glow(C_RED, 0.35F, 6), from, from.add(dir.scale(DASH_LENGTH)), 0.7);
    }

    /** 光速突進: dir へ駆け抜けて、線上の相手に当てる。ブロックに当たる所で止まる */
    void dash(Vec3 dir) {
        if (!(level() instanceof ServerLevel)) return;
        Vec3 start = position();
        Vec3 end = start;
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        for (double d = 0.5; d <= DASH_LENGTH; d += 0.5) {
            Vec3 p = start.add(dir.scale(d));
            AABB box = getDimensions(getPose()).makeBoundingBox(p);
            if (!level().noCollision(this, box)) break;
            end = p;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box.inflate(0.6), ChronoGazer::canHurt)) {
                if (hit.add(e)) {
                    e.hurt(damageSources().mobAttack(this), damageFor(e, DASH));
                    e.knockback(1.0, -dir.x, -dir.z);
                }
            }
        }
        if (level() instanceof ServerLevel server) { // 駆け抜けた道の光の帯と螺旋（自作エフェクト）
            LuxionFxEntity.spawn(server, LuxionFxEntity.DASH, start.add(0, 1.2 * SIZE, 0), end.add(0, 1.2 * SIZE, 0), isPhase2());
        }
        blink(end);
        if (level() instanceof ServerLevel server) { // 駆け抜けた後の光の衝撃波
            com.automatavalkyries.weapon.Fx.shockwave(server, end.add(0, 0.1, 0), C_WHITE, 4.0F, 14);
            com.automatavalkyries.weapon.Fx.shockwave(server, end.add(0, 0.2, 0), C_GOLD, 6.0F, 18);
            com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.5F, 22), end.add(0, 1.0 * SIZE, 0), 24, 0.25);
        }
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.8F);
        level().broadcastEntityEvent(this, EVENT_DASH_END); // 滑って止まる
    }

    // ---- 大技ルクシオンアクセラレータ（2026-10-09 ユーザー指定） ----

    /** 蹴りの向きの水平から下への傾き（ラジアン。描画で体ごと傾ける） */
    public float accelPitch() {
        return entityData.get(ACCEL_PITCH);
    }

    /** 予測線: 今の位置から狙う所へ赤い光の線（狙いを固定したら金も重ねる） */
    void accelTelegraph(Vec3 to, boolean locked) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = position().add(0, 0.8 * SIZE, 0);
        com.automatavalkyries.weapon.Fx.line(server, glow(C_RED, locked ? 0.5F : 0.35F, 6), from, to, 0.6);
        if (locked) com.automatavalkyries.weapon.Fx.line(server, glow(C_GOLD, 0.3F, 6), from, to, 1.2);
        com.automatavalkyries.weapon.Fx.mandala(server, to.add(0, 0.1, 0), C_RED, 2.5F, 6, false, true); // 着弾の印
    }

    /** 加速の光のレールを展開する（見た目。{@link LuxionFxEntity#RAIL}） */
    void accelRail(Vec3 to) {
        if (!(level() instanceof ServerLevel server)) return;
        LuxionFxEntity.spawn(server, LuxionFxEntity.RAIL, position().add(0, 0.8 * SIZE, 0), to, true);
        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.8F);
    }

    /**
     * 蹴りの 1 Tick: from から to へ進み、道の相手を蹴り穿つ（1 体 1 回）。ブロックに当たる所で止まる。
     * @return 止まったら（ブロックに当たった・行き先に着いた）その場所、まだ進むなら null
     */
    @org.jetbrains.annotations.Nullable
    Vec3 accelStep(Vec3 from, Vec3 to, java.util.Set<LivingEntity> hit, boolean last) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 dir = len < 1.0e-4 ? Vec3.ZERO : d.scale(1.0 / len);
        Vec3 reached = from;
        boolean blocked = false;
        for (double s = 0.4; s <= len + 1.0e-4; s += 0.4) {
            Vec3 p = from.add(dir.scale(Math.min(s, len)));
            AABB box = getDimensions(getPose()).makeBoundingBox(p);
            if (!level().noCollision(this, box)) {
                blocked = true;
                break;
            }
            reached = p;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box.inflate(0.8), ChronoGazer::canHurt)) {
                if (hit.add(e)) {
                    e.hurt(accelDamage(), damageFor(e, ACCEL_KICK));
                    e.knockback(2.5, -dir.x, -dir.z);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                    if (level() instanceof ServerLevel server) {
                        com.automatavalkyries.weapon.Fx.burst(server, star(C_WHITE, 0.7F, 20), e.position().add(0, e.getBbHeight() * 0.5, 0), 30, 0.45);
                        server.sendParticles(ParticleTypes.FLASH, e.getX(), e.getY(0.5), e.getZ(), 1, 0, 0, 0, 0);
                    }
                    playSound(SoundEvents.ANVIL_LAND, 1.2F, 1.6F);
                }
            }
        }
        setPos(reached.x, reached.y, reached.z);
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
        if (level() instanceof ServerLevel server) { // 蹴り足の先から散る光
            com.automatavalkyries.weapon.Fx.burst(server, glow(C_WG, 0.6F, 14), reached.add(0, 0.6 * SIZE, 0), 8, 0.2);
            com.automatavalkyries.weapon.Fx.burst(server, glow(C_RED, 0.5F, 12), reached.add(0, 0.6 * SIZE, 0), 4, 0.15);
        }
        return blocked || last || len < ACCEL_SPEED - 1.0e-3 ? reached : null;
    }

    /** 着弾: 光の爆風（蹴りが当たっていない近くの相手へ）と、幾重もの衝撃波・光の陣 */
    void accelImpact(Vec3 at, Vec3 start, java.util.Set<LivingEntity> hit) {
        if (!(level() instanceof ServerLevel server)) return;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(ACCEL_BLAST_RADIUS), ChronoGazer::canHurt)) {
            if (hit.contains(e) || e.distanceToSqr(at) > ACCEL_BLAST_RADIUS * ACCEL_BLAST_RADIUS) continue;
            e.hurt(accelDamage(), damageFor(e, ACCEL_BLAST));
            Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0e-4) e.knockback(1.5, -away.x, -away.z);
        }
        Vec3 ground = at.add(0, 0.1, 0);
        com.automatavalkyries.weapon.Fx.shockwave(server, ground, C_WHITE, 6.0F, 14);
        com.automatavalkyries.weapon.Fx.shockwave(server, ground, C_GOLD, 9.0F, 20);
        com.automatavalkyries.weapon.Fx.shockwave(server, ground.add(0, 0.1, 0), C_RED, 12.0F, 26);
        com.automatavalkyries.weapon.Fx.mandala(server, ground, C_GOLD, 6.0F, 40, false, true);
        com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.9F, 40), at.add(0, 1.0, 0), 90, 0.6);
        com.automatavalkyries.weapon.Fx.burst(server, glow(C_RED, 0.8F, 30), at.add(0, 1.0, 0), 40, 0.4);
        com.automatavalkyries.weapon.Fx.line(server, glow(C_WHITE, 1.2F, 24), at, at.add(0, 18, 0), 0.6); // 天への光の柱
        server.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 2, 0.3, 0.3, 0.3, 0);
        LuxionFxEntity.spawn(server, LuxionFxEntity.DASH, start, at.add(0, 0.6 * SIZE, 0), true); // 蹴り抜けた道の残光
        playSound(SoundEvents.GENERIC_EXPLODE, 2.5F, 1.3F);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.4F);
        level().broadcastEntityEvent(this, EVENT_ACCEL_IMPACT);
    }

    /** 防御貫通のダメージ（ルクシオンが与えた扱い） */
    private DamageSource accelDamage() {
        return new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(ACCEL_DAMAGE), this, this);
    }

    void setAccelPitch(float pitch) {
        entityData.set(ACCEL_PITCH, pitch);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 技の組み立て: 近ければ斬る・瞬動、離れていれば光刃の波・突進、時々三連閃 */
    static class LuxionAttackGoal extends Goal {
        private final LuxionEntity lux;
        private int melee, blink, flurry, wave, dash, repath;
        /** 三連閃の残り回数と次までの Tick */
        private int flurryLeft, flurryGap;
        /** 突進の構えの残り Tick と向き */
        private int windup;
        private Vec3 dashDir;
        /** 第 2 形態の光速突進は 2 回（2026-10-09 ユーザー指定）: 駆け抜けた後に折り返してもう一度駆ける残り回数と、その相手 */
        private int dashAgain;
        private LivingEntity dashTarget;
        /**
         * ルクシオンアクセラレータ（第 2 形態）: 間隔、段階（0 = 無し、1 = 飛び上がる、2 = 狙う、3 = 蹴る、4 = 着地）、段階の経過 Tick、
         * 頂点、狙う所、蹴り始めの所、蹴った相手
         */
        private int accel = 100, accelStage, accelT;
        private Vec3 accelApex, accelTo, accelFrom;
        private final java.util.Set<LivingEntity> accelHit = new java.util.HashSet<>();
        /** 相手が空中にいる間（と、降りてからしばらく）は空中にとどまって戦う残り Tick */
        private int aerial;
        /**
         * 逃げる相手の見張り（2026-10-08「相手が自分より速く動いて距離を置くとき、光速突進で詰める」）: 相手の 1 Tick 前の位置と速さ、
         * 自分より速く離れていった Tick 数、追う突進の間隔、今の突進が追う突進か（放つ瞬間に相手の行く先へ向け直す）
         */
        private Vec3 lastTargetPos;
        private Vec3 targetVel = Vec3.ZERO;
        private int fleeing, chase;
        private boolean chaseDash;
        /** 自分の足の速さの目安（ブロック / Tick。移動速度 0.34 で約 0.2） */
        private double ownSpeed() {
            return lux.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.6;
        }

        /** 相手の動きを見て、自分より速く離れていく Tick を数える */
        private void watchFlight(LivingEntity t) {
            Vec3 pos = t.position();
            targetVel = lastTargetPos == null || pos.distanceToSqr(lastTargetPos) > 16.0 ? Vec3.ZERO : pos.subtract(lastTargetPos); // 瞬間移動は数えない
            lastTargetPos = pos;
            Vec3 away = pos.subtract(lux.position()).multiply(1, 0, 1);
            Vec3 flat = targetVel.multiply(1, 0, 1);
            boolean fleeingNow = away.lengthSqr() > 1.0e-4 && flat.length() > ownSpeed() && flat.dot(away.normalize()) > ownSpeed() * 0.7;
            fleeing = fleeingNow ? fleeing + 1 : Math.max(0, fleeing - 2);
        }

        /** 相手が空中にいれば、空中にとどまる位置を決める（技の溜め・三連閃の間はその場、それ以外は相手の横 {@link #BESIDE} ブロック・同じ高さ） */
        private void tickAerial(LivingEntity t) {
            boolean airborne = !t.onGround() && heightAboveGround(t) > 1.8;
            if (airborne) aerial = 20; // 跳ねた・一瞬降りただけでは落ちない
            else if (t.onGround()) aerial = Math.min(aerial - 1, 5); // 相手が地に降りたら、すぐ降りる
            else aerial--;
            if (aerial <= 0) return;
            boolean hold = windup > 0 || blinkPrep > 0 || waveCharge > 0 || finalRaise > 0 || flurryLeft > 0;
            if (hold) {
                lux.hoverAt(lux.position());
                return;
            }
            Vec3 flat = lux.position().subtract(t.position()).multiply(1, 0, 1);
            Vec3 side = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : flat.normalize();
            double hd = flat.length();
            Vec3 spot = hd > BESIDE + 0.5 ? t.position().add(side.scale(BESIDE)) : new Vec3(lux.getX(), t.getY(), lux.getZ());
            lux.hoverAt(spot);
        }

        /** 届かない相手（高い所・低い所・道が無い）へ瞬動で詰める間隔と、詰まり具合の見張り（2026-10-08「上下差があると反応しない」） */
        private int reach, stuckCheck;
        private double lastDist = Double.MAX_VALUE;
        /** 瞬動の前の反り・三連閃の締めの振りかぶり・光刃の波の溜めの残り Tick と、その先・相手 */
        private int blinkPrep, finalRaise, waveCharge;
        private Vec3 blinkTo;
        private LivingEntity waveTarget;
        /** 今の相手とは別に、自分を狙っている兵姫（光刃の波・突進を向ける）と、探し直しまでの Tick */
        private LivingEntity side;
        private int sideCheck;

        /**
         * 索敵の範囲で、今の相手以外に自分を狙っている（攻撃してくる）戦術兵姫と、戦術兵姫のダミーちゃん。見えているものから、遠いもの（後ろから撃ってくる兵姫）を選ぶ
         */
        private LivingEntity pickSideAttacker(LivingEntity current) {
            double range = lux.getAttributeValue(Attributes.FOLLOW_RANGE);
            LivingEntity best = null;
            double bestD = -1;
            // 戦術兵姫のダミーちゃん（練習台）は攻撃してこないが、兵姫の身代わりとして同じに数える（2026-10-08「ダミーちゃんだとうまく反応してくれない」）
            for (LivingEntity v : lux.level().getEntitiesOfClass(LivingEntity.class, lux.getBoundingBox().inflate(range), e -> e != current && e.isAlive()
                    && ((e instanceof com.automatavalkyries.entity.ValkyrieEntity va && !va.isShutdown() && (va.getTarget() == lux || va.getLastHurtMob() == lux))
                        || e instanceof com.automatavalkyries.entity.ValkyrieDummyEntity)
                    && lux.getSensing().hasLineOfSight(e))) {
                double d = v.distanceToSqr(lux);
                if (d > bestD) {
                    bestD = d;
                    best = v;
                }
            }
            return best;
        }

        LuxionAttackGoal(LuxionEntity lux) {
            this.lux = lux;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = lux.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || windup > 0 || flurryLeft > 0 || finalRaise > 0 || blinkPrep > 0 || waveCharge > 0 || accelStage > 0;
        }

        @Override
        public void start() {
            lux.setAggressive(true); // 背のブースターを展開（描画）
            melee = 10;
            blink = 40;
            flurry = 120;
            wave = 60;
            dash = 160;
        }

        @Override
        public void stop() {
            lux.setAggressive(false);
            flurryLeft = 0;
            windup = 0;
            accelStage = 0;
            lux.setAccelPitch(0.0F);
            dashAgain = 0;
            dashTarget = null;
            blinkPrep = finalRaise = waveCharge = 0;
            chaseDash = false;
            lastTargetPos = null;
            fleeing = 0;
            lux.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        /**
         * ルクシオンアクセラレータの 1 Tick: 大きく飛び上がり（身を沈めてから跳ぶ）→ 頂点でブースターを全開にして狙う（予測線。相手の行く先を読み、
         * 最後の {@link #ACCEL_LOCK} Tick は固定して加速の光のレールを展開）→ レールに沿って蹴り穿つ（ライダーキック）→ 着弾の爆風 → 着地
         */
        private void tickAccel(@org.jetbrains.annotations.Nullable LivingEntity t) {
            accelT++;
            lux.getNavigation().stop();
            lux.resetFallDistance();
            switch (accelStage) {
                case 1 -> { // 飛び上がる（最初の 4 Tick は身を沈める）
                    if (accelT > 4) {
                        Vec3 d = accelApex.subtract(lux.position()).scale(0.3);
                        if (d.length() > 2.2) d = d.normalize().scale(2.2);
                        lux.setDeltaMovement(d);
                        if (accelT == 5) {
                            lux.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0F, 0.8F);
                            if (lux.level() instanceof ServerLevel server) com.automatavalkyries.weapon.Fx.shockwave(server, lux.position().add(0, 0.1, 0), C_GOLD, 5.0F, 14);
                        }
                    } else {
                        lux.setDeltaMovement(Vec3.ZERO);
                    }
                    if (t != null && t.isAlive()) face(t);
                    if (accelT >= ACCEL_LEAP_TICKS) {
                        accelStage = 2;
                        accelT = 0;
                        accelTo = t != null ? t.position() : lux.position().add(0, -ACCEL_HEIGHT, 0);
                        lux.level().broadcastEntityEvent(lux, EVENT_ACCEL_AIM);
                        lux.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.6F);
                    }
                }
                case 2 -> { // 頂点で狙う
                    Vec3 d = accelApex.subtract(lux.position()).scale(0.3);
                    lux.setDeltaMovement(d.length() > 0.6 ? d.normalize().scale(0.6) : d);
                    boolean locked = accelT > ACCEL_AIM_TICKS - ACCEL_LOCK;
                    if (!locked && t != null && t.isAlive()) {
                        int lead = ACCEL_AIM_TICKS - accelT + 4;
                        accelTo = t.position().add(targetVel.multiply(1, 0, 1).scale(lead)); // 行く先を読む
                        face(t);
                    }
                    if (accelT == ACCEL_AIM_TICKS - ACCEL_LOCK + 1) lux.accelRail(accelTo); // 狙いを固定してレールを展開
                    if (accelT % 2 == 0) lux.accelTelegraph(accelTo, locked);
                    if (accelT >= ACCEL_AIM_TICKS) {
                        accelStage = 3;
                        accelT = 0;
                        accelFrom = lux.position().add(0, 0.8 * SIZE, 0);
                        Vec3 v = accelTo.subtract(lux.position());
                        double flat = Math.sqrt(v.x * v.x + v.z * v.z);
                        lux.setAccelPitch((float) Math.atan2(-v.y, Math.max(0.01, flat)));
                        float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
                        lux.setYRot(yaw);
                        lux.setYBodyRot(yaw);
                        lux.level().broadcastEntityEvent(lux, EVENT_ACCEL_KICK);
                        lux.playSound(SoundEvents.TRIDENT_RIPTIDE_3, 2.0F, 1.2F);
                    }
                }
                case 3 -> { // 蹴り穿つ
                    Vec3 here = lux.position();
                    Vec3 left = accelTo.subtract(here);
                    boolean last = accelT >= ACCEL_KICK_MAX || left.length() <= ACCEL_SPEED;
                    Vec3 next = left.length() <= ACCEL_SPEED ? accelTo : here.add(left.normalize().scale(ACCEL_SPEED));
                    Vec3 stop = lux.accelStep(here, next, accelHit, last);
                    if (stop != null) {
                        lux.accelImpact(stop, accelFrom, accelHit);
                        lux.setAccelPitch(0.0F);
                        accelStage = 4;
                        accelT = 0;
                    }
                }
                default -> { // 着地して身を起こす
                    lux.setDeltaMovement(new Vec3(0, lux.getDeltaMovement().y, 0));
                    if (accelT >= ACCEL_LAND_TICKS) {
                        accelStage = 0;
                        accelT = 0;
                    }
                }
            }
        }

        /** 突進の向き: 相手が高い所にいれば斜めに駆け上がる */
        private Vec3 dashDir(LivingEntity t) {
            return dashDir(t.position());
        }

        private Vec3 dashDir(Vec3 to) {
            Vec3 d = to.subtract(lux.position());
            if (d.y < 1.5 && aerial <= 0) d = d.multiply(1, 0, 1); // 地上では、下へは水平に駆け抜けて落ちる（地面に刺さらない）。空中では相手へまっすぐ
            return d.normalize();
        }

        /** 相手の方へ体ごと向く */
        private void face(LivingEntity t) {
            lux.getLookControl().setLookAt(t, 360.0F, 90.0F);
            float yaw = (float) (Mth.atan2(t.getZ() - lux.getZ(), t.getX() - lux.getX()) * Mth.RAD_TO_DEG) - 90.0F;
            lux.setYRot(yaw);
            lux.setYBodyRot(yaw);
        }

        /** 光刃の波を溜める（第 2 形態は短く） */
        private void chargeWave(LivingEntity t) {
            waveTarget = t;
            waveCharge = lux.isPhase2() ? 6 : 8;
            lux.getNavigation().stop();
            face(t);
            lux.level().broadcastEntityEvent(lux, EVENT_WAVE_CHARGE);
        }

        private int cd(int ticks) {
            return lux.isPhase2() ? ticks * 3 / 5 : ticks;
        }

        @Override
        public void tick() {
            LivingEntity t = lux.getTarget();
            melee--; blink--; flurry--; wave--; dash--; reach--; chase--; accel--;
            if (t != null && t.isAlive()) watchFlight(t);
            if (accelStage > 0) { // 大技の最中は他のことをしない（相手の動きの見張りだけ続ける）
                tickAccel(t);
                return;
            }
            if (t != null && t.isAlive()) tickAerial(t); // 空中の相手には空中にとどまって戦う
            // 突進の構え（動かずに予告線を出し、0 になったら駆け抜ける）
            if (windup > 0) {
                lux.getNavigation().stop();
                if (windup % 4 == 0) lux.dashTelegraph(dashDir);
                if (--windup == 0) {
                    if (chaseDash && t != null && t.isAlive()) dashDir = dashDir(t.position().add(targetVel.scale(4))); // 逃げる相手の行く先へ向け直す
                    chaseDash = false;
                    lux.dash(dashDir);
                    if (dashAgain > 0 && dashTarget != null && dashTarget.isAlive() && lux.isPhase2()) { // 折り返してもう一度（構えは短く）
                        dashAgain--;
                        windup = DASH_AGAIN_WINDUP;
                        chaseDash = dashTarget == t; // 今の相手なら、放つ瞬間に行く先へ向け直す
                        dashDir = dashDir(dashTarget);
                        face(dashTarget);
                        lux.level().broadcastEntityEvent(lux, EVENT_DASH_WINDUP);
                        lux.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 2.1F);
                    } else {
                        dashAgain = 0;
                    }
                }
                return;
            }
            // 瞬動の前の反り（消える前に身を反らせる）
            if (blinkPrep > 0) {
                lux.getNavigation().stop();
                if (--blinkPrep == 0) {
                    lux.blink(blinkTo);
                    if (t != null) face(t);
                    lux.level().broadcastEntityEvent(lux, EVENT_BLINK_ARRIVE); // 現れた直後は居合の振り抜きの姿
                    melee = 4;
                }
                return;
            }
            // 光刃の波の溜め（大きく引いてから全身で横に振り抜く）
            if (waveCharge > 0) {
                lux.getNavigation().stop();
                if (waveTarget != null && waveTarget.isAlive()) face(waveTarget);
                if (--waveCharge == 0 && waveTarget != null && waveTarget.isAlive()) lux.wave(waveTarget);
                return;
            }
            // 三連閃の締め: 振りかぶってから振り下ろす
            if (finalRaise > 0) {
                if (t != null && t.isAlive()) face(t);
                if (--finalRaise == 0) lux.slash(FLURRY_HIT, EVENT_SLASH_DOWN);
                return;
            }
            // 三連閃の続き（斬るたびに型を変える）
            if (flurryLeft > 0) {
                if (t == null || !t.isAlive()) {
                    flurryLeft = 0;
                    return;
                }
                if (--flurryGap <= 0) {
                    flurryLeft--;
                    flurryGap = lux.isPhase2() ? 6 : 8;
                    lux.blink(lux.spotAround(t, lux.random.nextDouble() * Math.PI * 2, BESIDE));
                    face(t);
                    if (flurryLeft == 0) { // 最後の一太刀
                        finalRaise = 6;
                        lux.level().broadcastEntityEvent(lux, EVENT_FLURRY_RAISE);
                        lux.playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 2.0F);
                    } else {
                        lux.slash(FLURRY_HIT, COMBO[(flurryLeft + 1) % COMBO.length]);
                    }
                }
                return;
            }
            if (t == null) return;
            // 自分より速く離れていく相手には、短い構えの光速突進で一気に詰める（行く先を読んで放つ）
            double away = lux.distanceTo(t);
            if (fleeing >= 6 && chase <= 0 && away > 7 && away < DASH_LENGTH * 2 && lux.getSensing().hasLineOfSight(t)) {
                chase = cd(80);
                fleeing = 0;
                windup = lux.isPhase2() ? 6 : 8;
                chaseDash = true;
                dashDir = dashDir(t.position().add(targetVel.scale(4 + windup)));
                dashTarget = t;
                dashAgain = lux.isPhase2() ? 1 : 0;
                face(t);
                lux.getNavigation().stop();
                lux.level().broadcastEntityEvent(lux, EVENT_DASH_WINDUP);
                lux.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 2.0F);
                return;
            }
            // 横から狙ってくる別の兵姫へ、光刃の波・光速突進を撃つ（今の相手との戦いは続ける。2026-10-08 ユーザー指定）
            if (--sideCheck <= 0) {
                sideCheck = 10;
                side = pickSideAttacker(t);
            }
            if (side != null && !side.isAlive()) side = null;
            if (side != null) {
                double sd = lux.distanceTo(side);
                boolean seesSide = lux.getSensing().hasLineOfSight(side);
                if (seesSide && dash <= 0 && sd > 6 && sd < DASH_LENGTH && lux.random.nextFloat() < 0.5F) { // その兵姫へ駆け抜ける
                    dash = cd(240);
                    windup = lux.isPhase2() ? 12 : 20;
                    dashDir = dashDir(side);
                    dashTarget = side;
                    dashAgain = lux.isPhase2() ? 1 : 0;
                    lux.getLookControl().setLookAt(side, 360.0F, 90.0F);
                    lux.level().broadcastEntityEvent(lux, EVENT_DASH_WINDUP);
                    lux.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.8F);
                    return;
                }
                if (seesSide && wave <= 0 && sd > 5 && sd <= WAVE_RANGE) { // その兵姫へ光刃の波
                    wave = cd(70);
                    chargeWave(side);
                    return;
                }
            }
            lux.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double dist = lux.distanceTo(t);
            boolean sees = lux.getSensing().hasLineOfSight(t);
            double dy = t.getY() - lux.getY();
            // 歩いて近づけない（高い・低い所にいる、道が無い、近づけていない）なら、瞬動でそばへ詰める
            if (--stuckCheck <= 0) {
                stuckCheck = 20;
                boolean noProgress = dist > MELEE_REACH && dist > lastDist - 0.5;
                boolean noPath = dist > MELEE_REACH && (lux.getNavigation().isDone() || lux.getNavigation().getPath() == null
                        || !lux.getNavigation().getPath().canReach());
                lastDist = dist;
                if (reach <= 0 && dist < 40 && (noPath || noProgress || Math.abs(dy) > STEP_HEIGHT) && (dist > MELEE_REACH || Math.abs(dy) > STEP_HEIGHT)) {
                    reach = cd(30);
                    blinkTo = lux.spotAround(t, Mth.atan2(lux.getZ() - t.getZ(), lux.getX() - t.getX()), BESIDE);
                    blinkPrep = lux.isPhase2() ? 3 : 4;
                    lux.getNavigation().stop();
                    lux.level().broadcastEntityEvent(lux, EVENT_BLINK_PREP);
                    return;
                }
            }
            // 大技ルクシオンアクセラレータ（第 2 形態）。近くにいても出し（2026-10-09 ユーザー指定）、HP が減るほど出やすい:
            // 間隔は HP 50% で 18 秒 → 0% で 6 秒、出せる間は毎 Tick 5% → 50% の見込みで出す
            float low = Mth.clamp(1.0F - DirectRead.health(lux) / lux.getMaxHealth() / 0.5F, 0.0F, 1.0F);
            if (lux.isPhase2() && accel <= 0 && dist < 28 && sees && lux.onGround() && lux.random.nextFloat() < 0.05F + 0.45F * low) {
                accel = (int) Mth.lerp(low, 360.0F, 120.0F);
                accelStage = 1;
                accelT = 0;
                accelHit.clear();
                lux.hoverTicks = 0; // 空中にとどまる動きをやめる
                // 遠くから放つ（2026-10-09 ユーザー指定「もうちょっと猶予あるように遠くから」）: 相手から水平に ACCEL_RANGE 以上離れた所の上空へ跳び退く
                Vec3 retreat = lux.position().subtract(t.position()).multiply(1, 0, 1);
                retreat = retreat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, lux.getYRot() + 180.0F) : retreat.normalize();
                double standOff = Math.max(ACCEL_RANGE, Math.sqrt(lux.distanceToSqr(t.getX(), lux.getY(), t.getZ())));
                accelApex = new Vec3(t.getX(), lux.getY(), t.getZ()).add(retreat.scale(standOff)).add(0, ACCEL_HEIGHT, 0);
                face(t);
                lux.getNavigation().stop();
                lux.level().broadcastEntityEvent(lux, EVENT_ACCEL_LEAP);
                lux.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 1.6F);
                for (net.minecraft.server.level.ServerPlayer p : ((ServerLevel) lux.level()).getPlayers(p -> p.distanceToSqr(lux) < 64 * 64)) {
                    p.displayClientMessage(Component.translatable("message.automata_valkyries.luxion.accelerator").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                }
                return;
            }
            if (flurry <= 0 && dist < 16 && sees) { // 光速三連閃（第 2 形態は 5 連）
                flurry = cd(200);
                flurryLeft = lux.isPhase2() ? 5 : 3;
                flurryGap = 0;
                lux.level().broadcastEntityEvent(lux, EVENT_TWO_HAND); // 締めまで両手で持つ
                return;
            }
            if (dash <= 0 && dist > 6 && dist < DASH_LENGTH && sees) { // 光速突進（構え 1 秒、第 2 形態 0.6 秒）
                dash = cd(240);
                windup = lux.isPhase2() ? 12 : 20;
                dashDir = dashDir(t);
                dashTarget = t;
                dashAgain = lux.isPhase2() ? 1 : 0;
                lux.level().broadcastEntityEvent(lux, EVENT_DASH_WINDUP);
                lux.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.8F);
                return;
            }
            if (wave <= 0 && sees && (dist > 9 || (dist > MELEE_REACH && Math.abs(dy) > STEP_HEIGHT))) { // 光刃の波（上下に離れた相手にも）
                wave = cd(70);
                chargeWave(t);
                return;
            }
            if (blink <= 0 && dist > 4 && dist < 24) { // 光速瞬動で背後へ（身を反らせてから消える）
                blink = cd(80);
                double behind = Mth.atan2(t.getZ() - lux.getZ(), t.getX() - lux.getX());
                blinkTo = lux.spotAround(t, behind, BESIDE);
                blinkPrep = lux.isPhase2() ? 3 : 4;
                lux.getNavigation().stop();
                lux.level().broadcastEntityEvent(lux, EVENT_BLINK_PREP);
                return;
            }
            if (dist <= MELEE_REACH && Math.abs(dy) <= STEP_HEIGHT) {
                lux.getNavigation().stop();
                if (melee <= 0) {
                    melee = cd(22);
                    lux.slash((float) lux.getAttributeValue(Attributes.ATTACK_DAMAGE));
                }
            } else if (aerial <= 0 && --repath <= 0) { // 空中では歩かずに飛んで寄る（tickAerial）
                repath = 8;
                lux.getNavigation().moveTo(t, 1.2);
            }
        }
    }
}
