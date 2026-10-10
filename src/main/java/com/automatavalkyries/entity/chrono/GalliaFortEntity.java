package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.weapon.WeaponDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * クロノゲイザーの AEC 級英雄機「特殊要塞型【重護屋 / ガリアフォート】」（全体仕様書 §4.1。2026-10-10 ユーザーへの質問で決定）。
 * 逆台形の大きなキャタピラ 4 つで進む大型の移動要塞（高さ約 6 ブロック。前の操縦席に少女が見える）。HP {@value #MAX_HP}（ルクシオンより硬め）、HP 50% で第 2 形態。
 * 即死系の守り（Eprotect）は付けるが、被弾の上限は無し。
 * <p>
 * <b>歪曲障壁</b>（ユーザー指定）: 障壁に体力がある。100 未満の一撃は本体に 1（§6.3 の手順 1。防具より前の値で見る）。100 以上の一撃は障壁の体力を削り、
 * 本体は無傷。障壁を削り切ると {@link #BARRIER_DOWN} Tick のあいだ素通し（全ダメージが本体に通る）で、その後に張り直す。
 * 貫通特性（重粒子収束砲の {@link WeaponDamage#PARTICLE_BEAM}）は障壁を無視してフルダメージ（障壁は削らない）。
 * </p>
 * 技（ユーザー指定。数値は【仮】。第 2 形態は間隔が 7 割で、それぞれ強くなる）:
 * <ul>
 *   <li>穿孔破城砲: 予告線（最後は狙いを固定）の後、主砲から一直線の貫通砲撃（{@value #CANNON}・射程 {@value #CANNON_RANGE}・ブロックも貫く・防具と盾を通る）。第 2 形態は狙い直してもう 1 発</li>
 *   <li>踏みつけと衝撃波: 足元に寄った相手を前脚で踏みつけ（{@value #STOMP}・半径 {@value #STOMP_RADIUS}）、地面を伝う衝撃波の輪（地上の相手に {@value #QUAKE}。跳べば避けられる）。第 2 形態は 2 回</li>
 *   <li>ミサイル一斉射撃: 背の箱の蓋を開け、上へ撃ち上げて相手のまわりへ降らせる（1 発 {@value #MISSILE}・半径 {@value #MISSILE_RADIUS}。着弾の予告円あり）</li>
 *   <li>副砲の弾幕: 両横の副砲から扇に小弾をばらまく（1 発 {@value #BULLET}。追尾なし）</li>
 * </ul>
 * 狙いは兵姫を優先（クロノゲイザー共通）。挑む相手がいなくなると 10 秒で退く（セリフつきの正規の退場）
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class GalliaFortEntity extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech, com.automatavalkyries.entity.RetreatableBoss {

    public static final float MAX_HP = 15000.0F;
    /** 歪曲障壁: 体力（第 2 形態で張り直すと {@link #BARRIER_MAX_P2}）、貫ける一撃の下限、素通しの Tick（第 2 形態 {@link #BARRIER_DOWN_P2}） */
    public static final float BARRIER_MAX = 3000.0F, BARRIER_MAX_P2 = 4000.0F, PIERCE_MIN = 100.0F;
    public static final int BARRIER_DOWN = 300, BARRIER_DOWN_P2 = 240;
    /** 障壁の見た目の半径（ブロック）と、中心の高さ */
    public static final float BARRIER_RADIUS = 4.0F, BARRIER_Y = 2.0F; // 要塞もモデルを MODEL_SCALE 倍にした（2026-10-10）ので 5.6 → 4.0
    public static final float CANNON = 80.0F, STOMP = 40.0F, QUAKE = 20.0F, MISSILE = 25.0F, BULLET = 6.0F;
    public static final double CANNON_RANGE = 48.0, CANNON_WIDTH = 1.6, STOMP_RADIUS = 5.0, STOMP_REACH = 7.0, QUAKE_RANGE = 14.0, MISSILE_RADIUS = 3.0;
    /** 体の大きさ（モデルの倍率。モデルは実寸の半分で作ってある）。描画（client/GalliaFortRenderer）と技の出る所はこれに合わせる */
    public static final float SIZE = 2.0F;
    /**
     * モデルの胴まわりの倍率（tools/gen_gallia_fort.py の HULL_SCALE。2026-10-10 ユーザー指定「胴の部分大きくいかつく」）と、その基準の高さ（px）。
     * 砲口などの位置はモデルの px を {@link #hullPx} で直して使う
     */
    static final double HULL_SCALE = 1.3, HULL_BASE_Y = 17.5;
    /** 胴まわりを下げた分（px。tools/gen_gallia_fort.py の HULL_DROP。2026-10-10 ユーザー指定「胴の位置をさげて」） */
    static final double HULL_DROP = 2.0; // キャタピラを大きくしたので 7 → 2（2026-10-10）

    /** モデルの胴まわりの高さ（px）を、大きくした後の高さ（ブロック）に */
    static double hullY(double px) {
        return (HULL_BASE_Y + (px - HULL_BASE_Y) * HULL_SCALE - HULL_DROP) / 16.0 * SIZE * MODEL_SCALE;
    }

    /** モデルの胴まわりの水平の距離（px）を、大きくした後の距離（ブロック）に */
    static double hullPx(double px) {
        return px * HULL_SCALE / 16.0 * SIZE * MODEL_SCALE;
    }
    /**
     * 穿孔破城砲のダメージの種類: 防具と盾を通る（「穿孔」。ルクシオンアクセラレータと同じく兵姫のフォートレスフレームの物理 50% カットも効かない）
     */
    public static final ResourceKey<DamageType> SIEGE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "galliafort_siege_cannon"));
    /** 動きの合図（描画）: 前脚を振り上げる・踏み下ろす、主砲の溜め・発射、ミサイルの箱の蓋を開ける・閉じる、右・左の副砲の発射、障壁が受けた・砕けた・張り直した */
    public static final byte EVENT_STOMP_RAISE = 70, EVENT_STOMP_HIT = 71, EVENT_CANNON_CHARGE = 72, EVENT_CANNON_FIRE = 73,
            EVENT_HATCH_OPEN = 74, EVENT_HATCH_CLOSE = 75, EVENT_GUN_RIGHT = 76, EVENT_GUN_LEFT = 77,
            EVENT_BARRIER_HIT = 78, EVENT_BARRIER_BREAK = 79, EVENT_BARRIER_RESTORE = 80;
    /** 人型の合図: 重拳を振りかぶる・叩きつける、なぎ払い、突進の構え・突進、大技の脚を打ち込む・撃つ */
    public static final byte EVENT_HAMMER_RAISE = 81, EVENT_HAMMER_HIT = 82, EVENT_SWEEP = 83, EVENT_CHARGE_WINDUP = 84, EVENT_CHARGE = 85,
            EVENT_ULT_BRACE = 89, EVENT_ULT_FIRE = 90;
    /** 右・左のミサイルの箱から 1 発撃ち上げた（描画: 箱が反動で沈む。2026-10-10 モーション改良） */
    public static final byte EVENT_MISSILE_RIGHT = 86, EVENT_MISSILE_LEFT = 87;
    /** 演出の段階（同期）: 降下（脚を踏ん張って着地し、発生塔から障壁を張る）・第 2 形態への移行（攻撃を受けない。障壁を張り直し、箱の蓋を開く）・退く（障壁で身を包んで歪みに消える） */
    public static final byte STAGE_NONE = 0, STAGE_INTRO = 1, STAGE_TRANSFORM = 2, STAGE_RETREAT = 3;
    public static final int INTRO_TICKS = 72, TRANSFORM_TICKS = 80, RETREAT_OUT_TICKS = 64, DEFEAT_TICKS = 78;
    /**
     * 撃破の演出（2026-10-10 ユーザー指定「撃破演出も作って」。描画は client/GalliaFortRenderer）: 0〜{@link #DEFEAT_CORE} Tick は壊れていく
     * （障壁の枠が砕けて飛び散り、体の裂け目から光が漏れ、電気が走り、小さな爆発が続いて体が沈む）→ {@link #DEFEAT_CORE} Tick から胸の核が暴走してふくらむ →
     * {@link #DEFEAT_TICKS} Tick で閃光とともに体が消える大爆発（景色の歪み・光の柱・地面の輪・踏みつけと同じ衝撃）
     */
    public static final int DEFEAT_CORE = 55;
    /**
     * 召喚と退場の演出（2026-10-10 ユーザー指定「豪華な召喚演出と退場演出を自作して」。描画は client/GalliaFortRenderer）:
     * 召喚は上空 {@link #PORTAL_INTRO_H} ブロックに歪みの門が開き（{@link #INTRO_OPEN} Tick）、そこから現れて落ち、{@link #INTRO_LAND} Tick で着地して衝撃、
     * 発生塔がせり上がって {@link #INTRO_BARRIER} Tick で障壁を張る。退場は頭上 {@link #PORTAL_RETREAT_H} ブロックに門が開き、障壁の立方体の枠が身を包んで縮み、
     * {@link #RETREAT_RISE} Tick から門へ吸い上げられ、{@link #RETREAT_GONE} Tick で閃光とともに消えて門が閉じる
     */
    public static final float PORTAL_INTRO_H = 26.0F, PORTAL_RETREAT_H = 14.0F;
    public static final int INTRO_OPEN = 12, INTRO_LAND = 30, INTRO_BARRIER = 56, RETREAT_RISE = 28, RETREAT_GONE = 52;
    /**
     * 第 2 形態の人型（2026-10-10 ユーザー指定「第二形態は変形して人型にしたい」。キャタピラが脚、肩の装甲が腕、列車砲は右肩に担ぐ。高さ約 10 ブロック）:
     * モデル全体の倍率（要塞と人型で同じ。2026-10-10 ユーザー指定「あまりパーツの大きさを変えずに」で、変形の間に大きさを変えないように要塞も同じ倍率にした）、人型の当たり判定の幅と高さ
     */
    public static final float MODEL_SCALE = 0.7F, HUMAN_WIDTH = 5.0F, HUMAN_HEIGHT = 11.0F; // 胴を縦に起こした（2026-10-10）ので背が伸びた分を縮める
    /** 人型の障壁の見た目の半径と中心の高さ（ブロック） */
    public static final float HUMAN_BARRIER_RADIUS = 8.0F, HUMAN_BARRIER_Y = 6.0F;
    /** 人型の近接技（重拳の振り下ろし・腕のなぎ払い・体当たりの突進。掴んで投げるは 2026-10-10 ユーザー指定で削除）と大技（列車砲の全開射撃）のダメージ。数値は【仮】 */
    public static final float HAMMER = 50.0F, SWEEP = 35.0F, CHARGE = 45.0F, ULT = 150.0F;
    public static final double HAMMER_RADIUS = 4.0, SWEEP_RANGE = 8.5, CHARGE_LENGTH = 18.0, ULT_RANGE = 72.0, ULT_WIDTH = 3.0;
    /** 突進（ロードキル）の次までの Tick（2026-10-10「もう少し頻度ふやして」で 200 → 110） */
    static final int CHARGE_COOLDOWN = 110;
    /** 列車砲の全開射撃: 脚を打ち込む・溜める（この間は障壁が消える）・撃った後の冷却の Tick */
    public static final int ULT_BRACE = 20, ULT_CHARGE = 80, ULT_COOL = 30;
    /** 色: 障壁の蒼・紫の差し色・冷たい赤・白 */
    static final int C_BLUE = 0x4A7BFF, C_PALE = 0xB8D0FF, C_PURPLE = 0x8A4AE0, C_RED = 0xFF2A4A, C_WHITE = 0xFFFFFF, C_ORANGE = 0xFF8A3A;

    static com.automatavalkyries.particle.FxOptions glow(int color, float size, int life) {
        return Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), color, size, life);
    }

    static com.automatavalkyries.particle.FxOptions star(int color, float size, int life) {
        return Fx.opt(com.automatavalkyries.registry.ModParticles.STAR.get(), color, size, life);
    }

    private static final EntityDataAccessor<Boolean> PHASE2 = SynchedEntityData.defineId(GalliaFortEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> STAGE = SynchedEntityData.defineId(GalliaFortEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(GalliaFortEntity.class, EntityDataSerializers.BOOLEAN);
    /** 障壁の残り（0〜1）と、素通しの残り Tick（0 = 張っている） */
    private static final EntityDataAccessor<Float> BARRIER = SynchedEntityData.defineId(GalliaFortEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BARRIER_DOWN_LEFT = SynchedEntityData.defineId(GalliaFortEntity.class, EntityDataSerializers.INT);
    /** ミサイルの箱の蓋が開いている */
    private static final EntityDataAccessor<Boolean> HATCH = SynchedEntityData.defineId(GalliaFortEntity.class, EntityDataSerializers.BOOLEAN);
    /** 列車砲の全開射撃で障壁の光を砲へ注いでいる（障壁が消え、全ダメージが通る） */
    private static final EntityDataAccessor<Boolean> BARRIER_OFF = SynchedEntityData.defineId(GalliaFortEntity.class, EntityDataSerializers.BOOLEAN);

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    private final com.automatavalkyries.entity.BossRetreatWatch retreatWatch = new com.automatavalkyries.entity.BossRetreatWatch(64.0, 200);
    private boolean trialBoss, retreatPending, introDone;
    private float trialAttack = 1.0F;
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.automata_valkyries.galliafort"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    /** 障壁のゲージ（2 本目のボスバー） */
    private final ServerBossEvent barrierEvent = new ServerBossEvent(Component.translatable("bossbar.automata_valkyries.galliafort.barrier"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** サーバー: 障壁の体力、障壁が受けた最後の一撃の大きさと残りの無敵 Tick（障壁にも無敵時間を付ける。毎 Tick の光線で一瞬で溶けない） */
    private float barrier = BARRIER_MAX;
    private float barrierLastHit;
    private int barrierHurtTime;
    /** サーバー: 「1」に変えた一撃を本体に通している間（防具で 1 より小さくしない）、プレイヤーへの手がかりを最後に出した Tick */
    private boolean fixedOne;
    private int hintTick = -1000;
    private int stageTicks;
    /** クライアント: 合図の Tick（描画用）。前脚の振り上げ・踏み下ろし、主砲の溜め・発射、右・左の副砲、障壁が受けた・砕けた・張り直した、今の段階の始まり */
    public int stompRaiseTick = -100, stompHitTick = -100, cannonChargeTick = -100, cannonFireTick = -100, gunRightTick = -100, gunLeftTick = -100,
            barrierHitTick = -100, barrierBreakTick = -100, barrierRestoreTick = -100, stageStart;
    /** クライアント: 人型の技の合図の Tick（描画用） */
    public int hammerRaiseTick = -100, hammerHitTick = -100, sweepTick = -100, chargeWindupTick = -100, chargeTick = -100,
            ultBraceTick = -100, ultFireTick = -100, missileRightTick = -100, missileLeftTick = -100;
    /** クライアント: 箱の蓋の開き（0〜1）、障壁の見た目の濃さ（0〜1。張り直すとふくらみ、砕けると消える）、撃破の演出の経過 Tick */
    public float hatch, hatchO, shield, shieldO;
    /** クライアント: 人型で主砲を頭上へ構える重み（0〜1。溜め・大技の間は 1 へ、終わると 0 へ、なめらかに） */
    public float cannonAim, cannonAimO;
    public int defeatTicks;

    public GalliaFortEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        setMaxUpStep(2.0F);
        // 砲の上下の向きを毎 Tick 0 へ戻さない（バニラの LookControl は戻すので、技で決めた向きが打ち消されて砲がぶれた。2026-10-10）
        this.lookControl = new net.minecraft.world.entity.ai.control.LookControl(this) {
            @Override
            protected boolean resetXRotOnTick() {
                return false;
            }

            // 狙う相手がいる間は頭（＝砲塔・副砲の向き）を体の向きへ引き戻さない（技の turnToward が決めた向きを毎 Tick 10 度ずつ戻して、
            // 砲が相手の方を向ききらなかった。2026-10-10「主砲と副砲をちゃんと相手のほうを向いて撃つ」）
            @Override
            public void tick() {
                if (getTarget() == null) super.tick();
            }
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, STOMP)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 56.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE2, false);
        // はじめから召喚の演出の段階にしておく（サーバーが最初の Tick で召喚を始める前に、クライアントが地面に立つ姿を一瞬描いていた。2026-10-10「最初一瞬だけ地面に見えてしまう」）。
        // あとから見える範囲に入ったボスは、今の段階（ふだんは STAGE_NONE）が既定と違うので最初の同期で届く
        entityData.define(STAGE, STAGE_INTRO);
        entityData.define(DEFEATED, false);
        entityData.define(BARRIER, 1.0F);
        entityData.define(BARRIER_DOWN_LEFT, 0);
        entityData.define(HATCH, false);
        entityData.define(BARRIER_OFF, false);
    }

    public boolean isPhase2() {
        return entityData.get(PHASE2);
    }

    public boolean isDefeatedSynced() {
        return entityData.get(DEFEATED);
    }

    /** 障壁の残り（0〜1） */
    public float barrierRatio() {
        return entityData.get(BARRIER);
    }

    /** 障壁が砕けて素通しになっている */
    public boolean isBarrierDown() {
        return entityData.get(BARRIER_DOWN_LEFT) > 0;
    }

    /** 列車砲の全開射撃の溜めで障壁が消えている */
    public boolean isBarrierOff() {
        return entityData.get(BARRIER_OFF);
    }

    void setBarrierOff(boolean off) {
        entityData.set(BARRIER_OFF, off);
    }

    /** 障壁が無い（砕けている・砲へ注いでいる）: 全ダメージが通る */
    public boolean barrierOpen() {
        return isBarrierDown() || isBarrierOff();
    }

    /** 人型への変形の進み（0 = 要塞、1 = 人型。変形の段階の間に 0 → 1） */
    public float form(float partialTick) {
        if (!isPhase2()) return 0.0F;
        if (stage() != STAGE_TRANSFORM) return 1.0F;
        return Mth.clamp((tickCount - stageStart + partialTick) / TRANSFORM_TICKS, 0.0F, 1.0F);
    }

    /**
     * ボスバーの横顔用（クライアントのワールドに置かない、描くだけの個体。client/GalliaFortBossBar）: 第 2 形態か p2 で決め、
     * 段階は無し（召喚の門・撃破の演出を描かない）にする
     */
    public void portraitState(boolean p2) {
        entityData.set(PHASE2, p2);
        entityData.set(STAGE, STAGE_NONE);
        entityData.set(DEFEATED, false);
    }

    /** 人型になりきっている（変形が終わった） */
    public boolean isHumanoid() {
        return isPhase2() && stage() != STAGE_TRANSFORM;
    }

    /** 障壁の見た目の中心の高さと半径（人型で大きく高く） */
    public float barrierCenterY(float partialTick) {
        return Mth.lerp(form(partialTick), BARRIER_Y * SIZE * 0.5F * MODEL_SCALE + 0.6F, HUMAN_BARRIER_Y);
    }

    public float barrierRadius(float partialTick) {
        return Mth.lerp(form(partialTick), BARRIER_RADIUS, HUMAN_BARRIER_RADIUS);
    }

    Vec3 barrierCenter() {
        return position().add(0, barrierCenterY(0.0F), 0);
    }

    /** 人型の当たり判定は細く高い */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return isPhase2() ? net.minecraft.world.entity.EntityDimensions.scalable(HUMAN_WIDTH, HUMAN_HEIGHT) : super.getDimensions(pose);
    }

    public boolean isHatchOpen() {
        return entityData.get(HATCH);
    }

    void setHatch(boolean open) {
        if (isHatchOpen() == open) return;
        entityData.set(HATCH, open);
        level().broadcastEntityEvent(this, open ? EVENT_HATCH_OPEN : EVENT_HATCH_CLOSE);
        playSound(open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, 1.5F, 0.6F);
    }

    public byte stage() {
        return entityData.get(STAGE);
    }

    private void startStage(byte stage) {
        entityData.set(STAGE, stage);
        stageTicks = 0;
    }

    /** 障壁の今の上限（第 2 形態で上がる） */
    public float barrierMax() {
        return isPhase2() ? BARRIER_MAX_P2 : BARRIER_MAX;
    }

    /** 障壁の体力（サーバー。テスト用） */
    public float barrierHealth() {
        return barrier;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STAGE.equals(key)) stageStart = tickCount;
        if (PHASE2.equals(key)) refreshDimensions();
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || stage() != STAGE_NONE || isDefeatedSynced();
    }

    private boolean defeatedNow() {
        return isDefeatedSynced() || deathTime > 0;
    }

    /** 要塞は押されない */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    // ---- 歪曲障壁 ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (defeatedNow()) return false;
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (stage() == STAGE_TRANSFORM && !bypass) return false; // 移行の間は受けない
        if (bypass || barrierOpen() || source.is(WeaponDamage.PARTICLE_BEAM)) return super.hurt(source, amount); // 素通し（砕けている・砲へ注いでいる）・貫通特性はそのまま
        if (!Float.isFinite(amount) || amount <= 0.0F) return super.hurt(source, amount);
        // 強化の倍率を入れた一撃の大きさで決める（2026-10-10「攻撃力 100 以下の武器をカスタムで強化したのにダメージが入らない」: 兵装強化台の攻撃力などは
        // LivingHurtEvent で掛かるので、ここでは掛かる前の値を見ていた）。ここで LivingHurtEvent を 1 度だけ流して、掛かった後の値を見る
        float boosted = net.minecraftforge.common.ForgeHooks.onLivingHurt(this, source, amount);
        if (!Float.isFinite(boosted)) boosted = amount;
        if (boosted <= 0.0F) return false;                                  // 取り消された
        amount = boosted;
        if (amount < PIERCE_MIN) { // 100 未満は本体に 1（防具より前で 1 にし、防具でそれ以上は減らさない。倍率も掛けない: BarrierEvents）
            barrierRipple(source, false);
            hint(source);
            fixedOne = true;
            try {
                return super.hurt(source, 1.0F);
            } finally {
                fixedOne = false;
            }
        }
        // 100 以上: 障壁を貫いて障壁の体力を削る（本体は無傷）。障壁にも無敵時間（10 Tick の間はそれより大きい分だけ）。
        // 本体の被弾を通らないので、攻撃のイベント（回想のトライアルの「近接のみ」など）と無敵の判定はここで通す
        if (isInvulnerableTo(source) || !net.minecraftforge.common.ForgeHooks.onLivingAttack(this, source, amount)) return false;
        float dealt = barrierHurtTime > 0 ? amount - barrierLastHit : amount;
        if (dealt <= 0.0F) return false;
        if (barrierHurtTime <= 0) barrierLastHit = 0.0F;
        barrierLastHit = Math.max(barrierLastHit, amount);
        barrierHurtTime = 10;
        barrier = Math.max(0.0F, barrier - dealt);
        entityData.set(BARRIER, barrier / barrierMax());
        if (source.getEntity() instanceof LivingEntity attacker) setLastHurtByMob(attacker);
        barrierRipple(source, true);
        if (barrier <= 0.0F) breakBarrier();
        return false;
    }

    @Override
    protected float getDamageAfterArmorAbsorb(DamageSource source, float amount) {
        return fixedOne ? amount : super.getDamageAfterArmorAbsorb(source, amount);
    }

    /**
     * 障壁で「1」にした一撃は、本体の被弾の中でもう一度流れる LivingHurtEvent・LivingDamageEvent の倍率で 1 より大きくならないよう、最後に 1 へ戻す
     * （倍率は {@link #hurt} で 1 度だけ見て、障壁を貫けるかを決めた）
     */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
    public static final class BarrierEvents {
        private BarrierEvents() {
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            if (event.getEntity() instanceof GalliaFortEntity fort && fort.fixedOne) event.setAmount(1.0F);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
            if (event.getEntity() instanceof GalliaFortEntity fort && fort.fixedOne) event.setAmount(1.0F);
        }
    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        return fixedOne ? amount : super.getDamageAfterMagicAbsorb(source, amount);
    }

    /** 障壁が受けた所（攻め手の方の障壁の面）に波紋。strong = 障壁の体力を削った */
    private void barrierRipple(DamageSource source, boolean strong) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = barrierCenter();
        Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : source.getEntity() != null ? source.getEntity().getEyePosition() : null;
        Vec3 dir = from == null || from.distanceToSqr(c) < 1.0e-4 ? Vec3.directionFromRotation(0, getYRot()) : from.subtract(c).normalize();
        Vec3 at = c.add(dir.scale(barrierRadius(0.0F)));
        Fx.beamRing(server, at, dir, strong ? C_PALE : C_BLUE, strong ? 1.6F : 0.9F, strong ? 14 : 8);
        Fx.burst(server, glow(C_BLUE, strong ? 0.6F : 0.35F, 12), at, strong ? 14 : 5, strong ? 0.18 : 0.08);
        if (strong) {
            Fx.burst(server, star(C_PALE, 0.4F, 16), at, 8, 0.2);
            playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 2.0F, 0.5F);
        } else {
            playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 0.7F);
        }
        level().broadcastEntityEvent(this, EVENT_BARRIER_HIT);
    }

    /** 「1」にされた攻め手（プレイヤー）へ、5 秒に 1 度だけ手がかりを出す */
    private void hint(DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || tickCount - hintTick < 100) return;
        hintTick = tickCount;
        player.displayClientMessage(Component.translatable("message.automata_valkyries.galliafort.barrier_hint", (int) PIERCE_MIN).withStyle(ChatFormatting.AQUA), true);
    }

    /** 障壁が砕ける: しばらく素通し */
    private void breakBarrier() {
        barrier = 0.0F;
        entityData.set(BARRIER, 0.0F);
        entityData.set(BARRIER_DOWN_LEFT, isPhase2() ? BARRIER_DOWN_P2 : BARRIER_DOWN);
        level().broadcastEntityEvent(this, EVENT_BARRIER_BREAK);
        playSound(SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
        playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.6F);
        if (level() instanceof ServerLevel server) {
            Vec3 c = barrierCenter();
            for (int i = 0; i < 60; i++) { // 障壁の欠片が散る
                Vec3 d = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.7, random.nextGaussian()).normalize();
                Fx.one(server, glow(i % 3 == 0 ? C_PALE : C_BLUE, 0.5F, 26), c.add(d.scale(barrierRadius(0.0F))), d.scale(0.25));
            }
            Fx.shockwave(server, position().add(0, 0.2, 0), C_BLUE, barrierRadius(0.0F) * 2.0F, 20);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 64 * 64)) {
                p.displayClientMessage(Component.translatable("message.automata_valkyries.galliafort.barrier_break").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            }
        }
    }

    /** 障壁を張り直す（満タンに） */
    private void restoreBarrier(boolean announce) {
        barrier = barrierMax();
        entityData.set(BARRIER, 1.0F);
        entityData.set(BARRIER_DOWN_LEFT, 0);
        level().broadcastEntityEvent(this, EVENT_BARRIER_RESTORE);
        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
        if (level() instanceof ServerLevel server) {
            Vec3 c = barrierCenter();
            Fx.gather(server, glow(C_BLUE, 0.6F, 20), c, 40, barrierRadius(0.0F), 16);
            Fx.shockwave(server, position().add(0, 0.2, 0), C_PALE, barrierRadius(0.0F), 16);
            if (announce) for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 64 * 64)) {
                p.displayClientMessage(Component.translatable("message.automata_valkyries.galliafort.barrier_restore").withStyle(ChatFormatting.BLUE), true);
            }
        }
    }

    /** 障壁の毎 Tick: 素通しの残りを減らし、0 で張り直す。ゲージのボスバー */
    private void tickBarrier() {
        if (barrierHurtTime > 0) barrierHurtTime--;
        int down = entityData.get(BARRIER_DOWN_LEFT);
        if (down > 0) {
            entityData.set(BARRIER_DOWN_LEFT, down - 1);
            if (down - 1 == 0) restoreBarrier(true);
            else if (down % 4 == 0 && level() instanceof ServerLevel server) { // 発生塔から漏れる火花
                Vec3 c = barrierCenter();
                Fx.one(server, glow(C_BLUE, 0.4F, 10), c.add(random.nextGaussian() * 2.0, random.nextGaussian(), random.nextGaussian() * 2.0), Vec3.ZERO);
            }
        }
        if (isBarrierOff()) {
            barrierEvent.setProgress(0.0F);
            barrierEvent.setColor(BossEvent.BossBarColor.RED);
            if (tickCount % 10 == 0) barrierEvent.setName(Component.translatable("bossbar.automata_valkyries.galliafort.barrier_off"));
            return;
        }
        boolean isDown = isBarrierDown();
        barrierEvent.setProgress(isDown ? 1.0F - (entityData.get(BARRIER_DOWN_LEFT) / (float) (isPhase2() ? BARRIER_DOWN_P2 : BARRIER_DOWN)) : barrier / barrierMax());
        barrierEvent.setColor(isDown ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.BLUE);
        if (tickCount % 10 == 0 || down == 1) {
            barrierEvent.setName(isDown
                    ? Component.translatable("bossbar.automata_valkyries.galliafort.barrier_down", (entityData.get(BARRIER_DOWN_LEFT) + 19) / 20)
                    : Component.translatable("bossbar.automata_valkyries.galliafort.barrier_value", (int) Math.ceil(barrier), (int) barrierMax()));
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Phase2", isPhase2());
        tag.putFloat("Barrier", barrier);
        tag.putInt("BarrierDown", entityData.get(BARRIER_DOWN_LEFT));
        // 列車砲の溜めの途中で保存されても、障壁は消えたままにしない（読み込むと技は始めからなので）
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        introDone = true;
        if (tag.getBoolean("Phase2")) entityData.set(PHASE2, true);
        if (tag.contains("Barrier")) barrier = Mth.clamp(tag.getFloat("Barrier"), 0.0F, barrierMax());
        entityData.set(BARRIER, barrier / barrierMax());
        entityData.set(BARRIER_DOWN_LEFT, Math.max(0, tag.getInt("BarrierDown")));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new GalliaAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this, 24.0);
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

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) { // ルクシオンと同じ: 手元でも撃破を記録して HP 0 のまま演出を走らせる
            protect.markDefeated();
            setHealth(0.0F);
            return;
        }
        if (protect.interceptDie()) return;
        entityData.set(DEFEATED, true);
        super.die(source);
    }

    private boolean lootDropped;

    /** 落とし物のイベントの最後: 取り消されていたら戻し、空にされていたら撃破の演出の終わりに出し直す（ルクシオンと同じ） */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof GalliaFortEntity fort)) return;
        if (event.isCanceled()) {
            AutomataValkyries.LOGGER.warn("[galliafort] another mod cancelled the boss drops; restoring them ({} items)", event.getDrops().size());
            event.setCanceled(false);
        }
        if (event.getDrops().isEmpty()) AutomataValkyries.LOGGER.warn("[galliafort] the boss drops were emptied by another mod; they will be rolled again");
        else fort.lootDropped = true;
    }

    private void ensureLoot() {
        if (lootDropped || !(level() instanceof ServerLevel)) return;
        lootDropped = true;
        DamageSource source = getLastDamageSource() != null ? getLastDamageSource() : damageSources().generic();
        dropFromLootTable(source, getLastHurtByMob() instanceof Player);
        AutomataValkyries.LOGGER.info("[galliafort] rolled the boss drops again after they were lost");
    }

    /** 撃破: 脚が崩れて胴が沈み、あちこちで小さな爆発、最後に障壁の発生炉が弾けて大きな爆発（3 秒） */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (!level().isClientSide && !entityData.get(DEFEATED)) {
            entityData.set(DEFEATED, true);
            AutomataValkyries.LOGGER.info("[galliafort] defeated without die(); playing the defeat motion anyway");
        }
        if (!level().isClientSide && stage() != STAGE_NONE) startStage(STAGE_NONE);
        if (level().isClientSide) {
            for (int i = 0; i < 2; i++) level().addParticle(ParticleTypes.LARGE_SMOKE, getRandomX(1.2), getY() + random.nextDouble() * 3.5, getRandomZ(1.2), 0, 0.06, 0);
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        if (deathTime == DEFEAT_CORE) { // 核の暴走
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.5F);
            playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.4F);
        }
        if (deathTime > DEFEAT_CORE && deathTime % 5 == 0) playSound(SoundEvents.BEACON_AMBIENT, 2.5F, 0.6F + (deathTime - DEFEAT_CORE) * 0.04F);
        if (deathTime % 6 == 0 && deathTime < DEFEAT_TICKS - 6) {
            Vec3 at = position().add(random.nextGaussian() * 1.8, 1.0 + random.nextDouble() * 3.0, random.nextGaussian() * 1.8);
            server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            Fx.burst(server, glow(random.nextBoolean() ? C_RED : C_ORANGE, 0.6F, 18), at, 10, 0.2);
            playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 0.8F + random.nextFloat() * 0.4F);
        }
        if (deathTime >= DEFEAT_TICKS && !DirectRead.isRemoved(this)) {
            Vec3 c = position().add(0, 2.2, 0);
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
            Fx.burst(server, glow(C_BLUE, 1.0F, 30), c, 60, 0.45);
            Fx.burst(server, star(C_PALE, 0.7F, 40), c, 40, 0.35);
            Fx.shockwave(server, position().add(0, 0.1, 0), C_BLUE, 10.0F, 26);
            Fx.shockwave(server, position().add(0, 0.2, 0), C_PURPLE, 14.0F, 32);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.5, 0.5, 0.5, 0);
            // 大爆発の見た目（2026-10-10「撃破演出も作って」）: 景色の歪み・光の柱・地面の輪・光の網・飛び散る立方体の枠と、地面を伝う衝撃
            GalliaRiftEntity.spawn(server, position(), 12.0F);
            GalliaQuakeEntity.spawn(server, position(), 16.0F, level().getBlockState(blockPosition().below()));
            playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.6F);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.5F);
            ensureLoot();
            protect.finishDeath();
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public Component getDefeatSpeech() {
        return Component.translatable("entity.automata_valkyries.galliafort.defeat_speech").withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
    }

    @Override
    public SoundEvent getDefeatSound() {
        return SoundEvents.BEACON_DEACTIVATE;
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
            hatchO = hatch;
            hatch = Mth.clamp(hatch + (isHatchOpen() ? 0.12F : -0.08F), 0.0F, 1.0F);
            shieldO = shield;
            cannonAimO = cannonAim;
            boolean aiming = tickCount - cannonChargeTick < 75 || tickCount - ultBraceTick < 135;
            cannonAim = Mth.clamp(cannonAim + (aiming ? 0.1F : -0.07F), 0.0F, 1.0F);
            boolean up = !barrierOpen() && !isDefeatedSynced() && (stage() != STAGE_INTRO || tickCount - stageStart > INTRO_BARRIER - 4)
                    && !(stage() == STAGE_RETREAT && tickCount - stageStart > RETREAT_OUT_TICKS - 6);
            shield = Mth.clamp(shield + (up ? 0.06F : -0.15F), 0.0F, 1.0F);
            // 要塞の走行（2026-10-10「移動モーションで履帯が唸り動くように」）: 履帯のきしむ音・重い足音と、後ろのキャタピラが巻き上げる土
            if (!isPhase2() && onGround() && Math.hypot(getX() - xo, getZ() - zo) > 0.02 && !isDefeatedSynced()) {
                if (tickCount % 4 == 0)
                    level().playLocalSound(getX(), getY(), getZ(), SoundEvents.CHAIN_STEP, getSoundSource(), 1.3F, 0.5F + random.nextFloat() * 0.1F, false);
                if (tickCount % 10 == 0)
                    level().playLocalSound(getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, getSoundSource(), 1.0F, 0.5F, false);
                net.minecraft.world.level.block.state.BlockState ground = level().getBlockState(blockPosition().below());
                if (!ground.isAir()) {
                    Vec3 f = forward();
                    Vec3 side = new Vec3(-f.z, 0, f.x);
                    double tx = 21.0 / 16.0 * SIZE * MODEL_SCALE, tz = 44.0 / 16.0 * SIZE * MODEL_SCALE; // 後ろのキャタピラの外の後ろの端（モデルの px から）
                    for (int sgn = -1; sgn <= 1; sgn += 2) {
                        Vec3 p = position().add(side.scale(sgn * tx)).add(f.scale(-tz));
                        level().addParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
                                p.x + (random.nextDouble() - 0.5) * 0.8, p.y + 0.1, p.z + (random.nextDouble() - 0.5) * 0.8, -f.x * 0.15, 0.2, -f.z * 0.15);
                    }
                }
            }
            if (isBarrierDown() && tickCount % 3 == 0) { // 素通しの間、発生塔が火花を散らす
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(1.6), getY() + hullY(41.0) + random.nextDouble(), getRandomZ(1.6), 0, 0.05, 0);
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
        if (!defeatedNow()) tickBarrier();
        else {
            getNavigation().stop();
            setTarget(null);
            setAggressive(false);
            Vec3 v = getDeltaMovement();
            setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
            xxa = zza = 0.0F;
            barrierEvent.setProgress(0.0F);
        }
        if (!isPhase2() && ratio <= 0.5F && isAlive()) {
            entityData.set(PHASE2, true);
            startStage(STAGE_TRANSFORM); // 人型へ変形し、障壁を張り直す（4 秒、攻撃を受けない）
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            playSound(SoundEvents.WARDEN_ROAR, 2.0F, 0.6F);
            if (level() instanceof ServerLevel server) {
                for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 64 * 64)) {
                    p.displayClientMessage(Component.translatable("message.automata_valkyries.galliafort.phase2").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD), true);
                }
            }
        }
        tickRetreat();
    }

    private void tickStage() {
        byte stage = stage();
        if (stage == STAGE_NONE || !(level() instanceof ServerLevel server)) return;
        int t = stageTicks++;
        getNavigation().stop();
        Vec3 c = barrierCenter();
        if (stage == STAGE_INTRO) {
            Vec3 portal = position().add(0, PORTAL_INTRO_H, 0);
            if (t == 0) { // 上空に歪みの門が開く
                playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
                playSound(SoundEvents.PORTAL_TRIGGER, 1.5F, 0.6F);
                server.sendParticles(ParticleTypes.FLASH, portal.x, portal.y, portal.z, 1, 0, 0, 0, 0);
            }
            if (t < INTRO_LAND && t % 2 == 0) server.sendParticles(ParticleTypes.REVERSE_PORTAL, portal.x, portal.y, portal.z, 12, 3.0, 0.4, 3.0, 0.05);
            if (t == INTRO_OPEN) playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.5F, 0.5F);   // 門から現れて落ちる
            if (t == INTRO_LAND) { // 地響きとともに降り立つ（踏みつけと同じ衝撃の見た目を大きく、景色を一瞬歪ませる）
                GalliaQuakeEntity.spawn(server, position(), 12.0F, level().getBlockState(blockPosition().below()));
                GalliaRiftEntity.spawn(server, position(), 6.0F);
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 2, 2.0, 0.3, 2.0, 0);
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(blockPosition().below())),
                        getX(), getY() + 0.3, getZ(), 120, 3.0, 0.3, 3.0, 0.4);
                playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.4F);
                playSound(SoundEvents.ANVIL_LAND, 2.5F, 0.3F);
                playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.5F);
            }
            if (t == INTRO_LAND + 10) playSound(SoundEvents.PISTON_EXTEND, 2.0F, 0.5F); // 発生塔がせり上がる
            if (t >= INTRO_BARRIER - 12 && t < INTRO_BARRIER && t % 2 == 0) Fx.line(server, glow(C_BLUE, 0.5F, 10), position().add(0, hullY(43.5), 0), c.add(0, BARRIER_RADIUS, 0), 0.5);
            if (t == INTRO_BARRIER) restoreBarrier(false); // 障壁を張る
            if (t >= INTRO_TICKS) startStage(STAGE_NONE);
        } else if (stage == STAGE_TRANSFORM) {
            // 人型への変形（部品が動いて組み替わる。描画は ChronoGazerModel#animateGallia が form で動かす）: キャタピラが起き上がって脚に、
            // 胴がせり上がり、肩の装甲が腕に開き、列車砲が右肩へ、最後に頭がせり上がる。あちこちから蒸気と火花、機械の音
            if (t == 0) {
                setHatch(true);
                setBarrierOff(false);
            }
            if (t % 3 == 0) { // 蒸気と火花
                Vec3 p = position().add(random.nextGaussian() * 2.0, 0.5 + random.nextDouble() * 6.0, random.nextGaussian() * 2.0);
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.02);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.2);
            }
            if (t == 0 || t == 14 || t == 28 || t == 42 || t == 56) playSound(SoundEvents.PISTON_EXTEND, 2.5F, 0.4F + t * 0.004F);
            if (t == 8 || t == 36) playSound(SoundEvents.ANVIL_PLACE, 1.5F, 0.5F);
            if (t == 20) { // キャタピラが起き上がる地響き
                Fx.shockwave(server, position().add(0, 0.1, 0), C_PURPLE, 8.0F, 18);
                playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 0.5F);
            }
            if (t == 66) playSound(SoundEvents.WARDEN_ROAR, 2.5F, 0.5F); // 頭がせり上がって吠える
            if (t % 4 == 0 && t >= 50) Fx.gather(server, glow(C_BLUE, 0.5F, 16), c, 16, barrierRadius(0.0F) * 1.4, 12);
            if (t == 60) { // 障壁を張り直す（上限も上がる）
                restoreBarrier(false);
                Fx.mandala(server, position().add(0, 0.1, 0), C_BLUE, 7.0F, 40, false, true);
                Fx.shockwave(server, position().add(0, 0.1, 0), C_WHITE, 10.0F, 20);
                server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                playSound(SoundEvents.TOTEM_USE, 1.0F, 0.6F);
            }
            if (t >= TRANSFORM_TICKS) startStage(STAGE_NONE);
        } else if (stage == STAGE_RETREAT) {
            Vec3 portal = position().add(0, PORTAL_RETREAT_H, 0);
            if (t == 0) { // 頭上に歪みの門が開く
                setHatch(false);
                playSound(SoundEvents.BEACON_ACTIVATE, 2.5F, 0.6F);
                playSound(SoundEvents.PORTAL_TRIGGER, 1.2F, 0.8F);
            }
            if (t >= 8 && t < RETREAT_RISE && t % 6 == 0) playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 0.6F + t * 0.01F);   // 障壁の枠が身を包む
            if (t >= 10) { // 障壁で身を包み、門へ吸い上げられる
                Fx.burst(server, glow(C_BLUE, 0.6F, 20), position().add(0, 0.5 + random.nextDouble() * 4.0, 0), 4 + t / 4, 0.15);
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, portal.x, portal.y, portal.z, 8, 2.5, 0.5, 2.5, 0.05);
            }
            if (t == RETREAT_RISE) playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.5F, 0.8F);
            if (t == RETREAT_GONE) { // 門へ消えて閃光、景色が歪む
                server.sendParticles(ParticleTypes.FLASH, portal.x, portal.y, portal.z, 2, 0.5, 0.5, 0.5, 0);
                GalliaRiftEntity.spawn(server, portal, 7.0F);
                playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 0.7F);
                playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
            }
            if (t >= RETREAT_OUT_TICKS) {
                startStage(STAGE_NONE);
                protect.triggerDespawn();
            }
        }
    }

    private void tickRetreat() {
        if (trialBoss) return;
        if (retreatWatch.tick(this)) retreatPending = true;
        if (retreatPending && stage() == STAGE_NONE) beginRetreat();
    }

    @Override
    public boolean beginRetreat() {
        if (!isAlive() || defeatedNow() || stage() == STAGE_RETREAT || protect.isDespawning()) return false;
        startStage(STAGE_RETREAT);
        playSound(SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.6F);
        return true;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
        barrierEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
        barrierEvent.removePlayer(player);
    }

    @Override
    public void onRetired() {
        bossEvent.removeAllPlayers();
        barrierEvent.removeAllPlayers();
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
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_STOMP_RAISE -> stompRaiseTick = tickCount;
            case EVENT_STOMP_HIT -> stompHitTick = tickCount;
            case EVENT_CANNON_CHARGE -> cannonChargeTick = tickCount;
            case EVENT_CANNON_FIRE -> cannonFireTick = tickCount;
            case EVENT_GUN_RIGHT -> gunRightTick = tickCount;
            case EVENT_GUN_LEFT -> gunLeftTick = tickCount;
            case EVENT_BARRIER_HIT -> barrierHitTick = tickCount;
            case EVENT_BARRIER_BREAK -> barrierBreakTick = tickCount;
            case EVENT_BARRIER_RESTORE -> barrierRestoreTick = tickCount;
            case EVENT_HATCH_OPEN, EVENT_HATCH_CLOSE -> { }
            case EVENT_HAMMER_RAISE -> hammerRaiseTick = tickCount;
            case EVENT_HAMMER_HIT -> hammerHitTick = tickCount;
            case EVENT_SWEEP -> sweepTick = tickCount;
            case EVENT_CHARGE_WINDUP -> chargeWindupTick = tickCount;
            case EVENT_CHARGE -> chargeTick = tickCount;
            case EVENT_ULT_BRACE -> ultBraceTick = tickCount;
            case EVENT_ULT_FIRE -> ultFireTick = tickCount;
            case EVENT_MISSILE_RIGHT -> missileRightTick = tickCount;
            case EVENT_MISSILE_LEFT -> missileLeftTick = tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    // ---- 技 ----

    /** 技のダメージ: プレイヤーには最大 HP に応じて上げ（ルクシオンと同じ）、回想のトライアルではさらに倍率を掛ける */
    float damageFor(LivingEntity target, float damage) {
        return LuxionEntity.scaledFor(target, damage * trialAttack);
    }

    /** 体の向き（水平）の前 */
    Vec3 forward() {
        return Vec3.directionFromRotation(0, yBodyRot);
    }

    /** 体の向きでの位置: 上へ up、前へ fwd、左へ left（ブロック） */
    Vec3 local(double up, double fwd, double left) {
        Vec3 f = forward();
        return position().add(0, up, 0).add(f.scale(fwd)).add(new Vec3(-f.z, 0, f.x).scale(left));
    }

    /** 主砲の砲口（砲塔は頭の向きへ回る）。人型では背中から伸びたアームで頭上に固定した列車砲の先 */
    public Vec3 muzzle() {
        // 砲身の付け根から、モデルと同じ向き（頭の向き・上下は描画と同じ範囲で止める）へ砲身の長さだけ先（2026-10-10「ちゃんと主砲から攻撃が出るように」）。
        // 要塞は車体の上の砲塔、人型は背中のアームで頭上に構えた列車砲（付け根の高さはモデルの構えの姿勢から出した値）
        double k = PX;
        Vec3 body = forward();
        Vec3 fortress = position().add(0, CANNON_PIVOT_FORT[0] * k, 0).add(body.scale(CANNON_PIVOT_FORT[1] * k))
                .add(Vec3.directionFromRotation(Mth.clamp(getXRot(), -63.0F, 28.6F), getYHeadRot()).scale(CANNON_LEN * k));   // 対空で上へ 63 度まで（2026-10-10）
        Vec3 human = position().add(0, CANNON_PIVOT_HUMAN[0] * k, 0).add(body.scale(CANNON_PIVOT_HUMAN[1] * k))
                .add(Vec3.directionFromRotation(Mth.clamp(getXRot(), -63.0F, 22.9F), getYHeadRot()).scale(CANNON_LEN * k));
        return fortress.lerp(human, form(0.0F));
    }

    /**
     * モデルの 1 px のブロック、主砲の砲身の付け根の高さ・前（px。要塞・人型の構え）と砲身の長さ、副砲の付け根の高さ・前・横（要塞・人型）と砲身の長さ。
     * tools/gen_gallia_fort.py の形と ChronoGazerModel の人型の構えの姿勢から出した値
     */
    static final double PX = SIZE * MODEL_SCALE / 16.0;
    static final double[] CANNON_PIVOT_FORT = {44.75, -5.2}, CANNON_PIVOT_HUMAN = {145.9, 4.2}, GUN_PIVOT_FORT = {29.15, 19.5, 22.1}, GUN_PIVOT_HUMAN = {109.55, 7.7, 21.0};
    static final double CANNON_LEN = 74.2, GUN_LEN = 20.8;


    /** 副砲の砲口（right = 右 -X 側） */
    Vec3 gunMuzzle(boolean right) {
        // 副砲の付け根から、モデルと同じ向き（頭の向き。体から左右 34 度・上下 23 度まで）へ砲身の長さだけ先（2026-10-10「ちゃんと相手のほうを向いて撃つ」）。
        // 人型では肩アーマーの上（2026-10-10 ユーザー指定「副砲を肩に」）
        Vec3 f = forward();
        Vec3 side = new Vec3(-f.z, 0, f.x); // 左
        double k = PX, s = right ? -1.0 : 1.0;
        float yaw = yBodyRot + Mth.clamp(Mth.wrapDegrees(getYHeadRot() - yBodyRot), -34.4F, 34.4F);
        Vec3 barrel = Vec3.directionFromRotation(Mth.clamp(getXRot(), -68.7F, 22.9F), yaw).scale(GUN_LEN * k);   // 対空で上へ 69 度まで（2026-10-10）
        Vec3 fortress = position().add(0, GUN_PIVOT_FORT[0] * k, 0).add(f.scale(GUN_PIVOT_FORT[1] * k)).add(side.scale(s * GUN_PIVOT_FORT[2] * k));
        Vec3 human = position().add(0, GUN_PIVOT_HUMAN[0] * k, 0).add(f.scale(GUN_PIVOT_HUMAN[1] * k)).add(side.scale(s * GUN_PIVOT_HUMAN[2] * k));
        return fortress.lerp(human, form(0.0F)).add(barrel);
    }

    /** 穿孔破城砲の予告線（locked = 狙いを固定した。白い芯を重ねる） */
    void cannonTelegraph(Vec3 dir, boolean locked) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = muzzle();
        Fx.line(server, glow(C_RED, locked ? 0.45F : 0.3F, 5), from, from.add(dir.scale(CANNON_RANGE)), 0.8);
        if (locked) Fx.line(server, glow(C_WHITE, 0.2F, 5), from, from.add(dir.scale(CANNON_RANGE)), 1.6);
        Fx.gather(server, glow(locked ? C_WHITE : C_BLUE, 0.4F, 8), from, 4, 2.0, 6); // 砲口へ集まる光
    }

    /** 穿孔破城砲: dir へ一直線（ブロックも貫く）。線の近くの相手に当てる */
    void fireCannon(Vec3 dir) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = muzzle();
        Vec3 to = from.add(dir.scale(CANNON_RANGE));
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(CANNON_WIDTH + 1.0), ChronoGazer::canHurt)) {
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double along = p.subtract(from).dot(dir);
            if (along < 0 || along > CANNON_RANGE) continue;
            double off = p.subtract(from.add(dir.scale(along))).length();
            if (off > CANNON_WIDTH + e.getBbWidth() * 0.5 + e.getBbHeight() * 0.3) continue;
            if (hit.add(e)) {
                e.hurt(siegeDamage(), damageFor(e, CANNON));
                e.knockback(1.6, -dir.x, -dir.z);
                server.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        // 光の束は専用の見た目（2026-10-10「穿孔破城砲も自作して豪華にして」。GalliaSiegeBeamEntity・client/GalliaSiegeBeamRenderer:
        // 回る穿孔の弾頭・3 層の柱・三原色のらせん・広がる衝撃の輪・砲口の星形の閃光）。ここでは砲口の火花と、光の束の終わりの炸裂を足す
        GalliaSiegeBeamEntity.spawn(server, from, dir, (float) CANNON_RANGE, 0.85F);
        Fx.burst(server, star(C_WHITE, 0.6F, 20), from, 20, 0.35);
        server.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        Fx.burst(server, glow(C_RED, 1.0F, 16), to, 24, 0.4);
        playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.5F);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_CANNON_FIRE);
    }

    private DamageSource siegeDamage() {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SIEGE_DAMAGE), this, this);
    }

    /** 前脚の踏みつけ: 前の足元のまわりへ。その後、地面を伝う衝撃波の輪を出す（{@link GalliaAttackGoal} が広げる） */
    Vec3 stomp() {
        Vec3 at = position().add(forward().scale(2.6)); // 要塞もモデルを MODEL_SCALE 倍にした（2026-10-10） // 前のキャタピラの下（人型では踏み下ろした右足の下）
        if (level() instanceof ServerLevel server) {
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(STOMP_RADIUS, 3.0, STOMP_RADIUS), ChronoGazer::canHurt)) {
                Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
                if (away.length() > STOMP_RADIUS + e.getBbWidth() * 0.5) continue;
                e.hurt(damageSources().mobAttack(this), damageFor(e, STOMP));
                if (away.lengthSqr() > 1.0e-4) e.knockback(1.4, -away.x, -away.z);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
            }
            server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 3, 1.0, 0.2, 1.0, 0);
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(blockPosition().below())),
                    at.x, at.y + 0.2, at.z, 60, 2.0, 0.2, 2.0, 0.3);
            // 衝撃の見た目は専用（2026-10-10「踏みつけの衝撃を自作して豪華にして」: 閃光・地割れ・広がる光の壁・飛び散る地面のかけら。壁は衝撃波の輪と同じ速さ・半径）
            GalliaQuakeEntity.spawn(server, at, (float) QUAKE_RANGE, level().getBlockState(net.minecraft.core.BlockPos.containing(at).below()));
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
        playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.3F);
        level().broadcastEntityEvent(this, EVENT_STOMP_HIT);
        return at;
    }

    /** 衝撃波の輪を半径 r まで広げる: 輪が通る（r の前後 0.8）地上の相手へ、1 体 1 回 */
    void quake(Vec3 center, double r, Set<LivingEntity> hit) {
        if (!(level() instanceof ServerLevel server)) return;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r + 1.0, 2.0, r + 1.0), ChronoGazer::canHurt)) {
            double d = e.position().subtract(center).multiply(1, 0, 1).length();
            if (Math.abs(d - r) > 0.8 + e.getBbWidth() * 0.5 || !e.onGround() || Math.abs(e.getY() - center.y) > 1.5 || !hit.add(e)) continue;
            e.hurt(damageSources().mobAttack(this), damageFor(e, QUAKE));
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
        }
        int n = (int) Math.max(8, r * 4);
        for (int i = 0; i < n; i++) { // 地面がめくれ上がる輪
            double a = Math.PI * 2 * i / n;
            Vec3 p = center.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(p).below();
            if (!level().getBlockState(below).isAir()) {
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(below)), p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.15);
            }
        }
    }

    /** ミサイルを 1 発、背の箱（right = 右）から撃ち上げる。target のまわりへ降らせる */
    void launchMissile(LivingEntity target, boolean right, int index) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 f = forward();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        Vec3 from = position().add(0, hullY(39.5), 0).add(f.scale(hullPx(-9.0))).add(side.scale(hullPx(right ? -13.5 : 13.5)))
                .lerp(local(5.85, -3.3, right ? -1.55 : 1.55), form(0.0F)); // 車体を縮めずに背負う（2026-10-10） // 背負った車体の甲板の箱 // 人型では細い胴の背の箱
        if (airborne(target)) { // 空の相手へは弱めに追尾する（2026-10-10「対空には…弱めに追尾するミサイルで」）
            GalliaMissileEntity.launchHoming(server, this, from, target, MISSILE * trialAttack, (float) MISSILE_RADIUS);
            Fx.burst(server, glow(C_ORANGE, 0.4F, 10), from, 6, 0.1);
            playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.5F, 0.6F);
            level().broadcastEntityEvent(this, right ? EVENT_MISSILE_RIGHT : EVENT_MISSILE_LEFT);
            return;
        }
        // 降らせる所: 1 発目は相手の足元、ほかは相手のまわり（半径 2〜6）に散らす
        Vec3 aim = target.position();
        if (index > 0) {
            double a = random.nextDouble() * Math.PI * 2, r = 2.0 + random.nextDouble() * 4.0;
            aim = aim.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        GalliaMissileEntity.launch(server, this, from, aim, MISSILE * trialAttack, (float) MISSILE_RADIUS);
        Fx.burst(server, glow(C_ORANGE, 0.4F, 10), from, 6, 0.1);
        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.5F, 0.6F);
        level().broadcastEntityEvent(this, right ? EVENT_MISSILE_RIGHT : EVENT_MISSILE_LEFT);
    }

    /**
     * 空の相手か（2026-10-10「対空には副砲と主砲と弱めに追尾するミサイルで対応して」）: エリトラで飛んでいる・クリエイティブで飛んでいる、
     * または足元から 3 ブロック下まで地面が無い
     */
    public static boolean airborne(LivingEntity t) {
        if (t instanceof net.minecraft.world.entity.player.Player p && (p.getAbilities().flying || p.isFallFlying())) return true;
        if (t.isFallFlying()) return true;
        if (t.onGround() || t.isInWater() || t.onClimbable()) return false;
        Vec3 feet = t.position();
        return t.level().clip(new net.minecraft.world.level.ClipContext(feet, feet.subtract(0, 3.0, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.ANY, t)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** 副砲の 1 斉射: 両横から target へ、扇に count 発ずつ（追尾なし） */
    void barrageVolley(LivingEntity target, int count, float spreadDeg) {
        for (boolean right : new boolean[]{true, false}) {
            Vec3 from = gunMuzzle(right);
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0);
            Vec3 d = to.subtract(from);
            float baseYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
            for (int i = 0; i < count; i++) {
                float off = count == 1 ? 0.0F : -spreadDeg + 2.0F * spreadDeg * i / (count - 1);
                Vec3 dir = Vec3.directionFromRotation(pitch, baseYaw + off);
                GalliaShellEntity.fire(this, from, dir, damageFor(target, BULLET), 1.3F, 1.5F);   // 専用の徹甲弾（2026-10-10「副砲とミサイルの弾を自作して搭載」）
            }
            if (level() instanceof ServerLevel server) Fx.burst(server, glow(C_RED, 0.4F, 6), from, 6, 0.12);
            level().broadcastEntityEvent(this, right ? EVENT_GUN_RIGHT : EVENT_GUN_LEFT);
        }
        playSound(SoundEvents.CROSSBOW_SHOOT, 1.5F, 0.6F);
    }


    // ---- 人型の近接技と大技（2026-10-10 ユーザー指定。数値は【仮】） ----

    /** 重拳の振り下ろし: 前の地面へ右腕を叩きつける（半径 {@link #HAMMER_RADIUS} に {@value #HAMMER}、打ち上げる）。叩いた所を返す（衝撃波の輪を広げる） */
    Vec3 hammerHit() {
        Vec3 at = local(0.1, 4.2, -1.0); // 前腕を縮めたので近く（2026-10-10）。縮めるのを 0.9 倍にとどめた分だけ遠く
        if (level() instanceof ServerLevel server) {
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(HAMMER_RADIUS, 3.0, HAMMER_RADIUS), ChronoGazer::canHurt)) {
                if (e.position().subtract(at).multiply(1, 0, 1).length() > HAMMER_RADIUS + e.getBbWidth() * 0.5) continue;
                e.hurt(damageSources().mobAttack(this), damageFor(e, HAMMER));
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                e.hurtMarked = true;
            }
            server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 4, 1.2, 0.2, 1.2, 0);
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(net.minecraft.core.BlockPos.containing(at).below())),
                    at.x, at.y + 0.2, at.z, 80, 2.0, 0.3, 2.0, 0.35);
            GalliaQuakeEntity.spawn(server, at, 8.0F, level().getBlockState(net.minecraft.core.BlockPos.containing(at).below()));   // 踏みつけと同じ衝撃の見た目（輪は 8 ブロックまで）
            Fx.burst(server, glow(C_ORANGE, 0.7F, 16), at.add(0, 0.5, 0), 20, 0.3);
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 2.5F, 0.5F);
        playSound(SoundEvents.ANVIL_LAND, 2.5F, 0.3F);
        level().broadcastEntityEvent(this, EVENT_HAMMER_HIT);
        return at;
    }

    /** 腕のなぎ払い: 前の 200 度・{@link #SWEEP_RANGE} ブロックの相手へ {@value #SWEEP}、大きく吹き飛ばす */
    void sweepHit() {
        if (!(level() instanceof ServerLevel server)) return;
        // 両腕を広げて 1 回転するので、まわり全部（2026-10-10「両手を広げてその場で超信地旋回で一回転」）
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SWEEP_RANGE, 3.0, SWEEP_RANGE), ChronoGazer::canHurt)) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (to.length() > SWEEP_RANGE + e.getBbWidth() * 0.5) continue;
            e.hurt(damageSources().mobAttack(this), damageFor(e, SWEEP));
            if (to.lengthSqr() > 1.0e-4) e.knockback(2.5, -to.x, -to.z);
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
            e.hurtMarked = true;
        }
        for (int i = 0; i < 24; i++) { // 腕の通り道の輪（まわり全部）
            Vec3 dir = Vec3.directionFromRotation(0, yBodyRot + i * 15);
            Fx.one(server, glow(i % 2 == 0 ? C_PURPLE : C_BLUE, 0.6F, 10), position().add(0, 5.5, 0).add(dir.scale(6.0)), dir.scale(0.25));
        }
        GalliaQuakeEntity.spawn(server, position(), (float) SWEEP_RANGE, level().getBlockState(blockPosition().below()));   // 足元から広がる衝撃
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.4F);
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 2.5F, 0.5F);
    }

    /** 体当たりの突進の予告線 */
    void chargeTelegraph(Vec3 dir) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = position().add(0, 0.3, 0);
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(getBbWidth() * 0.5);   // 人型・戦車の幅
        Fx.line(server, glow(C_RED, 0.4F, 6), from.add(side), from.add(side).add(dir.scale(CHARGE_LENGTH)), 0.8);
        Fx.line(server, glow(C_RED, 0.4F, 6), from.subtract(side), from.subtract(side).add(dir.scale(CHARGE_LENGTH)), 0.8);
    }

    /**
     * ロードキルで巻き込めそうな数: target の向きへ突進したときの道（長さ CHARGE_LENGTH + 2、幅は体の幅と相手の幅に 1 ブロックの余裕）にいる、
     * 攻撃してよい生き物の数（target を含む）
     */
    public int roadkillTargets(LivingEntity target) {
        Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0e-4) return 0;
        dir = dir.normalize();
        double len = CHARGE_LENGTH + 2.0;
        int n = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(dir.scale(len)).inflate(3.0, 2.0, 3.0), ChronoGazer::canHurt)) {
            if (e == this) continue;
            Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
            double along = d.dot(dir);
            double across = Math.abs(d.x * -dir.z + d.z * dir.x);
            if (along > 0.0 && along < len && across < getBbWidth() * 0.5 + e.getBbWidth() * 0.5 + 1.0 && Math.abs(e.getY() - getY()) < 4.0) n++;
        }
        return n;
    }

    /** 突進の 1 Tick: dir へ 1.3 ブロック進み、ぶつかった相手へ {@value #CHARGE}（1 体 1 回）。壁に当たったら false */
    boolean chargeStep(Vec3 dir, Set<LivingEntity> hit) {
        Vec3 before = position();
        move(net.minecraft.world.entity.MoverType.SELF, dir.scale(1.3));
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.0), ChronoGazer::canHurt)) {
            if (!hit.add(e)) continue;
            e.hurt(damageSources().mobAttack(this), damageFor(e, CHARGE));
            e.knockback(2.2, -dir.x, -dir.z);
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
            e.hurtMarked = true;
            playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
        }
        if (level() instanceof ServerLevel server && tickCount % 2 == 0) {
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(blockPosition().below())),
                    getX(), getY() + 0.2, getZ(), 20, 1.5, 0.1, 1.5, 0.2);
            Fx.burst(server, glow(C_BLUE, 0.5F, 10), position().add(0, 5.0, 0).add(dir.scale(2.5)), 6, 0.2);
        }
        trackSparks(dir, 1.0F);
        return position().distanceToSqr(before) > 0.25;
    }

    /**
     * 足のキャタピラ（人型の足先）の後ろから散る火花と、削れるような音（2026-10-10 ユーザー指定「ロードキル突進…履帯に火花のエフェクト追加して高速回転させる」）。
     * power（0〜1）が大きいほど多く速く。向き dir の後ろ上へ飛ばす
     */
    void trackSparks(Vec3 dir, float power) {
        if (!(level() instanceof ServerLevel server)) return;
        // 人型は両足のキャタピラの後ろの端、戦車は 4 つのキャタピラの地面に着いた所の後ろの端（モデルの px から）
        double tx = 21.0 / 16.0 * SIZE * MODEL_SCALE;
        Vec3[] spots = isHumanoid() ? new Vec3[]{local(0.2, -1.4, -1.65), local(0.2, -1.4, 1.65)}
                : new Vec3[]{local(0.2, -3.3, -tx), local(0.2, -3.3, tx), local(0.2, 1.75, -tx), local(0.2, 1.75, tx)};
        for (Vec3 at : spots) {
            int n = 2 + (int) (6 * power);
            for (int i = 0; i < n; i++) {
                double sx = -dir.x * (0.3 + 0.5 * power) + (random.nextDouble() - 0.5) * 0.3;
                double sz = -dir.z * (0.3 + 0.5 * power) + (random.nextDouble() - 0.5) * 0.3;
                double sy = 0.15 + random.nextDouble() * 0.35 * (0.5 + power);
                server.sendParticles(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, sx, sy, sz, 1.0);
            }
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.2, at.z, 3 + (int) (5 * power), 0.3, 0.1, 0.3, 0.4 * power);
            server.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 2, 0.2, 0.1, 0.2, 0.02);
        }
        if (tickCount % 3 == 0) playSound(SoundEvents.GRINDSTONE_USE, 1.5F + power, 0.5F + 0.4F * power);
    }

    /** 列車砲の全開射撃の予告: 砲の太さの赤い光の筒（4 本の縁の線と芯）、砲口へ集まる光 */
    void ultTelegraph(Vec3 dir, boolean locked, float charge) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = muzzle();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = dir.cross(up);
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up2 = side.cross(dir).normalize();
        Vec3 to = from.add(dir.scale(ULT_RANGE));
        for (Vec3 o : new Vec3[]{side.scale(ULT_WIDTH), side.scale(-ULT_WIDTH), up2.scale(ULT_WIDTH), up2.scale(-ULT_WIDTH)}) {
            Fx.line(server, glow(C_RED, locked ? 0.5F : 0.35F, 5), from.add(o), to.add(o), 1.2);
        }
        if (locked) Fx.line(server, glow(C_WHITE, 0.3F, 5), from, to, 1.0);
        Fx.gather(server, glow(charge > 0.6F ? C_WHITE : C_BLUE, 0.4F + charge * 0.6F, 10), from, 6 + (int) (charge * 10), 3.0 + charge * 3.0, 8);
    }

    /** 列車砲の全開射撃: 極太の一直線（ブロックも貫く・防具と盾を通る）に {@value #ULT}。道に爆発の光 */
    void fireUlt(Vec3 dir) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = muzzle();
        Vec3 to = from.add(dir.scale(ULT_RANGE));
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(ULT_WIDTH + 1.0), ChronoGazer::canHurt)) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double along = c.subtract(from).dot(dir);
            if (along < 0 || along > ULT_RANGE) continue;
            if (c.subtract(from.add(dir.scale(along))).length() > ULT_WIDTH + e.getBbWidth() * 0.5 + e.getBbHeight() * 0.3) continue;
            e.hurt(siegeDamage(), damageFor(e, ULT));
            e.knockback(3.0, -dir.x, -dir.z);
            e.hurtMarked = true;
        }
        // 光の束は専用の見た目（2026-10-10「演出を自作して豪華にして」: 穿孔破城砲の光の束の歪曲破界砲の形。太く 2 秒残り、らせん 2 重・回る立方体の枠・流れる輪）
        GalliaSiegeBeamEntity.spawn(server, from, dir, (float) ULT_RANGE, 1.7F, GalliaSiegeBeamEntity.MODE_RIFT);
        for (double d = 5.0; d < ULT_RANGE; d += 10.0) {
            Vec3 p = from.add(dir.scale(d));
            server.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0.5, 0.5, 0.5, 0);
        }
        Fx.burst(server, star(C_WHITE, 0.9F, 30), from, 50, 0.6);
        Fx.shockwave(server, position().add(0, 0.1, 0), C_RED, 14.0F, 24);
        // 着弾地点（光の束が最初に当たる地形。無ければ射程の先）の景色を一瞬歪ませる（2026-10-10 ユーザー指定「歪曲破界砲は着弾地点の景色が一瞬歪むように」）
        net.minecraft.world.phys.BlockHitResult land = level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        Vec3 impact = land.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : land.getLocation();
        GalliaRiftEntity.spawn(server, impact, 9.0F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y, impact.z, 2, 0.6, 0.4, 0.6, 0);
        Fx.shockwave(server, impact.add(0, 0.2, 0), C_PURPLE, 9.0F, 20);
        server.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 3, 0.5, 0.5, 0.5, 0);
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 96 * 96)) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(SoundEvents.GENERIC_EXPLODE),
                    net.minecraft.sounds.SoundSource.HOSTILE, p.getX(), p.getY(), p.getZ(), 4.0F, 0.4F, random.nextLong()));
        }
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 4.0F, 0.4F);
        level().broadcastEntityEvent(this, EVENT_ULT_FIRE);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.5F, 0.5F);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /**
     * 技の組み立て（1 度に 1 つ）: 足元に寄られたら踏みつけ、離れていればミサイル・穿孔破城砲、その間に副砲の弾幕。
     * 人型（第 2 形態）は近くでは重拳・なぎ払い、中くらいの間合いでは体当たりの突進、ときどき列車砲の全開射撃。
     * 間合いは要塞では 10〜14 ブロック、人型では近接の届く 6 ブロックまで寄る（重いので体はゆっくり相手へ向き直る。砲塔＝頭は先に向く）
     */
    static class GalliaAttackGoal extends Goal {
        private final GalliaFortEntity fort;
        private int cannon = 80, stomp = 20, missiles = 120, barrage = 50, repath;
        /** 人型の技の間隔（大技は人型になってから 10 秒後から） */
        private int hammer = 20, sweep = 40, charge = 60, ult = 200;
        /** 今の技: 0 = 無し、1 = 主砲、2 = 踏みつけ、3 = ミサイル、4 = 弾幕、5 = 重拳、6 = なぎ払い、7 = 突進、9 = 列車砲の全開射撃 */
        private int action, actionT, left;
        private Vec3 cannonDir, chargeDir;
        private Vec3 quakeAt;
        private final Set<LivingEntity> quakeHit = new HashSet<>();

        GalliaAttackGoal(GalliaFortEntity fort) {
            this.fort = fort;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = fort.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || action != 0;
        }

        @Override
        public void start() {
            fort.setAggressive(true);
        }

        @Override
        public void stop() {
            fort.setAggressive(false);
            action = 0;
            fort.setBarrierOff(false);
            if (!fort.isPhase2()) fort.setHatch(false);
            fort.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private int cd(int ticks) {
            return fort.isPhase2() ? ticks * 7 / 10 : ticks;
        }

        /** 体は 1 Tick に maxDeg 度までしか向き直れない（砲塔＝頭はすぐ向く） */
        private void turnToward(Vec3 at, float maxDeg) {
            float yaw = (float) (Mth.atan2(at.z - fort.getZ(), at.x - fort.getX()) * Mth.RAD_TO_DEG) - 90.0F;
            float body = Mth.approachDegrees(fort.yBodyRot, yaw, maxDeg);
            fort.setYRot(body);
            fort.setYBodyRot(body);
            fort.setYHeadRot(Mth.approachDegrees(fort.getYHeadRot(), yaw, 12.0F));
            double flat = Math.sqrt((at.x - fort.getX()) * (at.x - fort.getX()) + (at.z - fort.getZ()) * (at.z - fort.getZ()));
            fort.setXRot((float) -(Mth.atan2(at.y - fort.muzzle().y, flat) * Mth.RAD_TO_DEG));
        }

        private Vec3 aimAt(LivingEntity t) {
            return t.position().add(0, t.getBbHeight() * 0.5, 0);
        }

        private void announce(String key, ChatFormatting... style) {
            for (ServerPlayer p : ((ServerLevel) fort.level()).getPlayers(p -> p.distanceToSqr(fort) < 64 * 64)) {
                p.displayClientMessage(Component.translatable(key).withStyle(style), true);
            }
        }

        @Override
        public void tick() {
            LivingEntity t = fort.getTarget();
            cannon--; stomp--; missiles--; barrage--; charge--;
            boolean human = fort.isHumanoid();
            if (human) {
                hammer--; sweep--; ult--;
            }
            if (action != 0) {
                tickAction(t);
                return;
            }
            if (t == null || !t.isAlive()) return;
            double dist = Math.sqrt(fort.distanceToSqr(t.getX(), fort.getY(), t.getZ()));
            boolean sees = fort.getSensing().hasLineOfSight(t);
            turnToward(aimAt(t), human ? 5.0F : 3.0F);
            if (human && pickHumanMove(t, dist, sees)) return;
            // 足元に寄られたら踏みつけ
            if (stomp <= 0 && dist < STOMP_REACH && Math.abs(t.getY() - fort.getY()) < 3.0) {
                begin(2, fort.isPhase2() ? 2 : 1);
                fort.level().broadcastEntityEvent(fort, EVENT_STOMP_RAISE);
                fort.playSound(SoundEvents.PISTON_EXTEND, 2.0F, 0.4F);
                return;
            }
            // 戦車の姿でも、突進の道に 2 体以上を巻き込めそうならロードキルを狙う（2026-10-10 ユーザー指定「戦車状態でも複数体巻き込めそうならロードキルを狙って」）
            if (!human && charge <= 0 && dist > 5 && dist < CHARGE_LENGTH + 8 && sees && fort.roadkillTargets(t) >= 2) {
                begin(7, 1);
                chargeDir = t.position().subtract(fort.position()).multiply(1, 0, 1).normalize();
                fort.level().broadcastEntityEvent(fort, EVENT_CHARGE_WINDUP);
                fort.playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.4F);
                announce("message.automata_valkyries.galliafort.roadkill", ChatFormatting.RED, ChatFormatting.BOLD);
                return;
            }
            boolean air = airborne(t);                                    // 真上を飛ぶ相手にもミサイル・主砲を向ける（2026-10-10 対空）
            if (missiles <= 0 && (dist > 6 || air) && dist < 40 && sees) {
                begin(3, fort.isPhase2() ? 14 : 8);
                fort.setHatch(true);
                announce("message.automata_valkyries.galliafort.missiles", ChatFormatting.RED);
                return;
            }
            if (cannon <= 0 && (dist > 6 || air) && dist < CANNON_RANGE - 4 && sees) {
                begin(1, fort.isPhase2() ? 2 : 1);
                cannonDir = aimAt(t).subtract(fort.muzzle()).normalize();
                fort.level().broadcastEntityEvent(fort, EVENT_CANNON_CHARGE);
                fort.playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.5F);
                fort.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.6F);
                announce("message.automata_valkyries.galliafort.cannon", ChatFormatting.RED, ChatFormatting.BOLD);
                return;
            }
            if (barrage <= 0 && dist < 32 && sees) {
                begin(4, fort.isPhase2() ? 5 : 3);
                return;
            }
            // 間合いを保つ: 要塞は遠ければ寄り、近すぎれば止まる（踏みつけを待つ）。人型は近接の届くところまで寄る
            double far = human ? 6.0 : 14.0, near = human ? 4.5 : 10.0;
            if (dist > far || !sees) {
                if (--repath <= 0) {
                    repath = 10;
                    if (!fort.getNavigation().moveTo(t, 1.0)) fort.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
                }
            } else if (dist < near) {
                fort.getNavigation().stop();
            }
        }

        /** 人型の技を選ぶ（選んだら true） */
        private boolean pickHumanMove(LivingEntity t, double dist, boolean sees) {
            if (ult <= 0 && sees && dist > 8 && dist < ULT_RANGE - 10) { // 列車砲の全開射撃
                begin(9, 1);
                cannonDir = aimAt(t).subtract(fort.muzzle()).normalize();
                fort.level().broadcastEntityEvent(fort, EVENT_ULT_BRACE);
                fort.playSound(SoundEvents.ANVIL_LAND, 3.0F, 0.3F);
                announce("message.automata_valkyries.galliafort.ultimate", ChatFormatting.RED, ChatFormatting.BOLD);
                return true;
            }
            boolean air = airborne(t);                                  // 空の相手へは近接・突進が届かないので使わない（2026-10-10 対空）
            if (dist < 7.0 && !air) {
                if (hammer <= 0) { // 重拳の振り下ろし
                    begin(5, 1);
                    fort.level().broadcastEntityEvent(fort, EVENT_HAMMER_RAISE);
                    fort.playSound(SoundEvents.PISTON_EXTEND, 2.5F, 0.4F);
                    return true;
                }
                if (sweep <= 0) { // 腕のなぎ払い
                    begin(6, 1);
                    fort.level().broadcastEntityEvent(fort, EVENT_SWEEP);
                    fort.playSound(SoundEvents.PISTON_CONTRACT, 2.5F, 0.4F);
                    return true;
                }
            }
            if (charge <= 0 && !air && dist > 6 && dist < CHARGE_LENGTH + 8 && sees) { // 体当たりの突進（ロードキル。2026-10-10「もう少し頻度ふやして」: 間合い 9〜22 → 6〜26）
                begin(7, 1);
                chargeDir = t.position().subtract(fort.position()).multiply(1, 0, 1).normalize();
                fort.level().broadcastEntityEvent(fort, EVENT_CHARGE_WINDUP);
                fort.playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.5F);
                return true;
            }
            return false;
        }

        private void begin(int kind, int count) {
            action = kind;
            actionT = 0;
            left = count;
            quakeAt = null;
            quakeHit.clear();
            fort.getNavigation().stop();
        }

        private void end(int cooldown) {
            switch (action) {
                case 1 -> cannon = cd(cooldown);
                case 2 -> stomp = cd(cooldown);
                case 3 -> missiles = cd(cooldown);
                case 4 -> barrage = cd(cooldown);
                case 5 -> hammer = cooldown;
                case 6 -> sweep = cooldown;
                case 7 -> charge = cooldown;
                case 9 -> ult = cooldown;
                default -> { }
            }
            action = 0;
        }

        private void tickAction(LivingEntity t) {
            int at = actionT++;
            fort.getNavigation().stop();
            boolean alive = t != null && t.isAlive();
            switch (action) {
                case 1 -> { // 穿孔破城砲: 溜め（最後の 10 Tick は狙いを固定）→ 撃つ。第 2 形態は短い溜めでもう 1 発
                    int charge = left > 1 || !fort.isPhase2() ? (fort.isPhase2() ? 30 : 40) : 20;
                    boolean locked = at >= charge - 10;
                    if (!locked && alive) {
                        turnToward(aimAt(t), 2.0F);
                        cannonDir = aimAt(t).subtract(fort.muzzle()).normalize();
                    }
                    if (at % 3 == 0) fort.cannonTelegraph(cannonDir, locked);
                    if (at >= charge) {
                        fort.fireCannon(cannonDir);
                        if (--left > 0 && alive) {
                            actionT = 0;
                            fort.level().broadcastEntityEvent(fort, EVENT_CANNON_CHARGE);
                            fort.playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.7F);
                        } else {
                            end(170);
                        }
                    }
                }
                case 2 -> { // 踏みつけ: 前脚（人型は右足）を振り上げ（16 Tick）→ 踏み下ろし → 衝撃波の輪が広がる（毎 Tick 0.7 ブロック）
                    if (quakeAt == null) {
                        if (alive) turnToward(aimAt(t), 4.0F);
                        if (at >= 16) {
                            quakeAt = fort.stomp();
                            quakeHit.clear();
                            actionT = 0;
                        }
                    } else {
                        double r = 1.0 + at * 0.7;
                        fort.quake(quakeAt, r, quakeHit);
                        if (r >= QUAKE_RANGE) {
                            if (--left > 0) { // 第 2 形態: もう 1 度
                                quakeAt = null;
                                actionT = 4; // 振り上げは短く
                                fort.level().broadcastEntityEvent(fort, EVENT_STOMP_RAISE);
                            } else {
                                end(70);
                            }
                        }
                    }
                }
                case 3 -> { // ミサイル一斉射撃: 蓋を開いて（12 Tick）から 2 Tick ごとに左右交互に撃ち上げ、撃ち終えたら蓋を閉じる
                    if (alive) turnToward(aimAt(t), 2.0F);
                    if (at >= 12 && (at - 12) % 2 == 0 && left > 0 && alive) {
                        int n = (fort.isPhase2() ? 14 : 8) - left;
                        fort.launchMissile(t, n % 2 == 0, n);
                        left--;
                    }
                    if (left <= 0 || !alive) {
                        if (at >= 12 + 10 || !alive) {
                            if (!fort.isPhase2()) fort.setHatch(false); // 第 2 形態は開いたまま
                            end(260);
                        }
                    }
                }
                case 4 -> { // 副砲の弾幕: 8 Tick ごとに 1 斉射（左右から 5 発ずつ・第 2 形態 7 発。人型は肩の副砲から）
                    if (alive) turnToward(aimAt(t), 3.0F);
                    int first = 0;
                    if (at >= first && (at - first) % 8 == 0 && alive) {
                        fort.barrageVolley(t, fort.isPhase2() ? 7 : 5, fort.isPhase2() ? 28.0F : 20.0F);
                        left--;
                    }
                    if (left <= 0 || !alive) end(140);
                }
                case 5 -> { // 重拳の振り下ろし: 振りかぶり（16 Tick）→ 叩きつけ → 衝撃波の輪（8 ブロックまで）
                    if (quakeAt == null) {
                        if (alive) turnToward(aimAt(t), 4.0F);
                        if (at >= 16) {
                            quakeAt = fort.hammerHit();
                            quakeHit.clear();
                            actionT = 0;
                        }
                    } else {
                        double r = 1.0 + at * 0.7;
                        fort.quake(quakeAt, r, quakeHit);
                        if (r >= 8.0) end(110);
                    }
                }
                case 6 -> { // ふっとばし（腕のなぎ払い）: 両腕を広げる（7 Tick）→ その場で超信地旋回で 1 回転（10 Tick。半ばでまわり全部を払う）→ 戻し
                    if (at < 7 && alive) turnToward(aimAt(t), 4.0F);
                    if (at >= 7 && at < 17) fort.trackSparks(fort.forward(), 0.6F);   // 足のキャタピラが逆へ回って火花
                    if (at == 12) fort.sweepHit();
                    if (at >= 25) end(130);
                }
                case 7 -> { // 体当たりの突進: 構え（20 Tick。最後の 6 Tick は向きを固定）→ 最大 14 Tick 駆け抜ける（壁に当たると止まる）
                    if (at < 14 && alive) {
                        turnToward(aimAt(t), 6.0F);
                        chargeDir = t.position().subtract(fort.position()).multiply(1, 0, 1).normalize();
                    }
                    if (at < 20) {
                        if (at % 4 == 0) fort.chargeTelegraph(chargeDir);
                        if (at >= 8) fort.trackSparks(chargeDir, (at - 8) / 12.0F);     // 足のキャタピラを空転させて唸らせる
                        if (at == 19) {
                            fort.level().broadcastEntityEvent(fort, EVENT_CHARGE);
                            fort.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.4F);
                            quakeHit.clear();
                        }
                    } else if (at < 34) {
                        if (!fort.chargeStep(chargeDir, quakeHit)) {
                            fort.playSound(SoundEvents.ANVIL_LAND, 2.5F, 0.4F);
                            end(CHARGE_COOLDOWN);
                        }
                    } else {
                        end(CHARGE_COOLDOWN);
                    }
                }
                case 9 -> { // 列車砲の全開射撃: 脚を打ち込む（20 Tick）→ 溜め（80 Tick。障壁を砲へ注ぐので障壁が消える。最後の 20 Tick は狙いを固定）→ 撃つ → 冷却（30 Tick）で障壁が戻る
                    if (at == 0) {
                        fort.setBarrierOff(true);
                        if (fort.level() instanceof ServerLevel server) {
                            Fx.shockwave(server, fort.position().add(0, 0.1, 0), C_PURPLE, 10.0F, 20);
                            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, fort.level().getBlockState(fort.blockPosition().below())),
                                    fort.getX(), fort.getY() + 0.2, fort.getZ(), 80, 2.5, 0.2, 2.5, 0.3);
                        }
                    }
                    int fire = ULT_BRACE + ULT_CHARGE;
                    if (at < fire) {
                        boolean locked = at >= fire - 20;
                        if (!locked && alive) {
                            turnToward(aimAt(t), 1.5F);
                            cannonDir = aimAt(t).subtract(fort.muzzle()).normalize();
                        }
                        if (at >= ULT_BRACE && at % 3 == 0) fort.ultTelegraph(cannonDir, locked, (at - ULT_BRACE) / (float) ULT_CHARGE);
                        if (at >= ULT_BRACE && at % 20 == 0) fort.playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.4F + (at - ULT_BRACE) / (float) ULT_CHARGE);
                    } else if (at == fire) {
                        fort.fireUlt(cannonDir);
                    } else if (at >= fire + ULT_COOL) {
                        fort.setBarrierOff(false);
                        fort.playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
                        end(600);
                    } else if (fort.level() instanceof ServerLevel server && at % 3 == 0) { // 冷却の蒸気
                        Vec3 m = fort.muzzle();
                        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, m.x, m.y, m.z, 2, 0.4, 0.4, 0.4, 0.03);
                    }
                }
                default -> action = 0;
            }
        }
    }
}
