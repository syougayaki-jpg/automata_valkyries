package com.automatavalkyries.entity;

import com.automatavalkyries.entity.ai.ValkyrieCombat;
import com.automatavalkyries.entity.ai.ValkyrieGoals;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.RepairInjectorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * 戦術兵姫（2026-10-04、段階 1: 持ち主・作戦指示・追従・機能停止・保護。docs/SPEC_VALKYRIE_GUI_AI.md）。
 * 見た目は {@code geo/entity/valkyrie_type_v.geo.json}（docs/DESIGN_VALKYRIE_VISUAL_DB.md §11）。
 *
 * <ul>
 *   <li>持ち主: 持ち主のいない兵姫を右クリックすると起動して持ち主になる（墜落ポッドはまだ無い）</li>
 *   <li>行動モード: 追従・待機・拠点防衛・巡回（{@link Mode}）。しゃがみ右クリックで、頭を狙うとなでる、体を狙うと追従 ↔ 待機。追従は 4 体まで</li>
 *   <li>機能停止: HP が 0 になると、死なずに崩れ落ちて（死亡モーション）その場に座り込んで止まる。
 *       ナノマシン溶液かリペアインジェクターを持って右クリックで直す（20 回復して起きる）</li>
 *   <li>即死（kill / die / HP 0 への書き換え / 桁外れのダメージ）: 胸のコアがステラ・ジェネシス・オメガなら受け付けない。
 *       それ以外は通常の HP 0 と同じく死亡モーションから機能停止（2026-10-04 ユーザー指定）</li>
 *   <li>保護: オートマタヴァルキリー protect（{@link ValkyrieProtect}）。データを二重に残す。オメガコアなら消去も断り自動で戻す、
 *       無ければ消去は通り救済通信機で呼び出す</li>
 *   <li>メニュー（段階 2、2026-10-05）: 持ち主が素手・普通のアイテムで右クリックで開く（{@code menu/ValkyrieMenu}）。換装・状態・作戦指示のタブ。
 *       胸のコアの入れ替え・表情の切り替えもここから</li>
 *   <li>シールド: ダメージを先に受け、最後に被弾してから 3 秒後から毎秒 5 ずつ電力で戻る【仮: 1 点 200 FE】</li>
 *   <li>電力: 内蔵 10 万 FE。胸のコアから放電レート（最大 5 万 FE/t）で補充し、維持に毎 Tick 使う【仮: 2 FE/t】。0 で休眠し、補充されたら起きる（暫定統一 C）</li>
 *   <li>同調率: 0〜100%。なでる（1 日 5 回まで【仮】）+1・手で修理 +3（拡張仕様書 §12.1）。100% で攻撃・速度 +20% まで 20% ごとに段階的に【仮】</li>
 * </ul>
 */
public class ValkyrieEntity extends PathfinderMob implements GeoEntity {

    /** 表情（テクスチャの名前の後ろ。通常は空） */
    public static final String[] EXPRESSIONS = {"normal", "smile", "shy", "angry", "sad", "surprise", "sleepy", "shutdown"};
    /** 同時に追従させられる数 */
    public static final int MAX_FOLLOWERS = 4;
    /** 修理 1 回の回復量 */
    public static final float REPAIR = 20.0F;
    /** これ以上の 1 撃は「即死」とみなす（オメガコアなら受け付けない） */
    public static final float INSTANT_KILL_DAMAGE = 1.0e6F;

    public enum Mode { FOLLOW, WAIT, DEFEND, PATROL }

    /** 頭上の感情アイコン（NONE は出さない）。絵は client 側（{@code ValkyrieOverhead}） */
    public enum Emote { NONE, HEART, ALERT, ANGRY, SWEAT, NOTE, QUESTION, SLEEP }

    /** 吹き出しを出す長さ（Tick）・感情アイコンを出す長さ（Tick） */
    public static final int SPEECH_DURATION = 80, EMOTE_DURATION = 40;

    /** 内蔵の電力の上限（全体仕様書 §3.1） */
    public static final int MAX_ENERGY = 100_000;
    /** コアから 1 Tick に補充できる上限（全体仕様書 §3.1 Max I/O、暫定統一 B） */
    public static final int MAX_IO = 50_000;
    /** 体の維持に毎 Tick 使う電力【仮】 */
    public static final int UPKEEP = 2;
    /** 休眠から起きる電力【仮】 */
    public static final int WAKE_ENERGY = 1_000;
    /** シールドの回復: 毎秒の量・1 点の電力【仮】・最後の被弾から待つ Tick【仮】 */
    public static final float SHIELD_REGEN = 5.0F;
    public static final int SHIELD_COST = 200;
    public static final int SHIELD_DELAY = 60;
    /** なでて同調率が上がる 1 日の回数【仮】 */
    public static final int PATS_PER_DAY = 5;
    /** 積載超過で遅くなる割合【仮】・同調率の能力上昇の最大【仮】 */
    public static final double OVERWEIGHT_SLOW = -0.3, SYNC_BONUS = 0.2;
    private static final UUID OVERWEIGHT_ID = UUID.fromString("5b6f6d3e-2c1a-4f39-9b0a-7a1e0c4d2f10");
    private static final UUID SYNC_SPEED_ID = UUID.fromString("8e2a1c55-7d3b-4f0e-a6c9-1b2d3e4f5a60");
    private static final UUID SYNC_ATTACK_ID = UUID.fromString("c41d9e70-3a5b-4c8d-9e1f-2a3b4c5d6e70");
    private static final UUID SYNC_HEALTH_ID = UUID.fromString("5b1e7c2a-9d34-4f8a-b6c1-0e2f3a4b5c61");
    private static final UUID SYNC_ARMOR_ID = UUID.fromString("a7c3e9f1-2b4d-4e6a-8c0b-1d3f5a7b9c62");
    private static final UUID SYNC_TOUGHNESS_ID = UUID.fromString("3f8d2b6e-7a1c-4b9e-9d5f-6c2a8e4b1d63");
    /** ステラ・ジェネシス・オメガの全ステータス 3 倍（全体仕様書 §3.4 のコアの表。2026-10-05 実装） */
    public static final float OMEGA_MULTIPLIER = 3.0F;
    private static final UUID OMEGA_HEALTH_ID = UUID.fromString("0d7a3c91-5e2b-4f68-a1c4-9b8e7d6f5a41");
    private static final UUID OMEGA_ARMOR_ID = UUID.fromString("1e8b4da2-6f3c-4079-b2d5-ac9f8e7a6b52");
    private static final UUID OMEGA_TOUGHNESS_ID = UUID.fromString("2f9c5eb3-7a4d-418a-c3e6-bdaf9f8b7c63");
    private static final UUID OMEGA_SPEED_ID = UUID.fromString("3aad6fc4-8b5e-429b-d4f7-cebaa09c8d74");

    private static final EntityDataAccessor<Integer> EXPRESSION = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHUTDOWN = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** なでられて喜んでいる残り Tick（表情を一時的に笑顔に） */
    private static final EntityDataAccessor<Integer> HAPPY = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BODY = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SYNC = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SHIELD = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ASLEEP = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BOOLEAN);
    /** 頭上の吹き出し（翻訳キー。空なら無し）と残り Tick（段階 3。SPEC_VALKYRIE_GUI_AI §4「頭上に短い吹き出し＋同じ文をチャットにも」） */
    private static final EntityDataAccessor<String> SPEECH = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SPEECH_TICKS = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    /** 頭上の感情アイコン（{@link Emote}）と残り Tick（§2.5「感情アイコン（♥ や ！）を短く出す」） */
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMOTE_TICKS = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    /** 戦いの構え（0 = なし、1 = 近接、2 = 射撃）と、ステップの合図（下位 3 ビットが向き＋1、上は回数。変わるたびにクライアントでステップの動きを始める） */
    private static final EntityDataAccessor<Byte> COMBAT_POSE = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> STEP_ANIM = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    /** 近接の攻撃の動きの合図（下位 3 ビットが種類 {@link #ATTACK_SLASH} など、4 ビット目が左手、上は回数） */
    private static final EntityDataAccessor<Integer> ATTACK_ANIM = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    /** 日常（段階 4）: 腰掛けている・充電台で整備している・仕草の合図（下位 3 ビットが種類）・気分の表情（EXPRESSIONS の番号）と残り Tick */
    private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GESTURE_ANIM = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOOD_TICKS = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    /** 仕草の種類: 喜ぶ（跳ねて両手を上げる）・しょんぼり・手を振る・うなずく・覗き込む */
    public static final int GESTURE_JOY = 1, GESTURE_DEJECTED = 2, GESTURE_WAVE = 3, GESTURE_NOD = 4, GESTURE_PEEK = 5;
    /** 仕草の長さ（Tick、種類の順。添字 0 は使わない） */
    public static final int[] GESTURE_TICKS = {0, 24, 50, 30, 16, 40};
    /** 近接の攻撃の動きの種類: 斬る・突く・叩く・薙ぎ払う（2026-10-06 ユーザー指定） */
    public static final int ATTACK_SLASH = 1, ATTACK_THRUST = 2, ATTACK_SMASH = 3, ATTACK_SWEEP = 4;
    /** 素手・拳の殴り（右・左。2026-10-06 ユーザー指定「素のときは殴りモーション」） */
    public static final int ATTACK_PUNCH_RIGHT = 5, ATTACK_PUNCH_LEFT = 6;
    /** 攻撃の動きの長さ（Tick、種類の順。添字 0 は使わない） */
    public static final int[] ATTACK_TICKS = {0, 8, 7, 12, 10, 6, 6};
    /** 外装（頭・胴・脚）と背部ユニット（クライアントへ送る写し。見た目に出す。2026-10-06） */
    private static final EntityDataAccessor<ItemStack> UNIT_HEAD = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> UNIT_CHEST = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> UNIT_LEGS = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> UNIT_BACK = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.ITEM_STACK);
    /** 胸のコア（クライアントへ送る写し。HUD・頭上の発光線の色に使う） */
    private static final EntityDataAccessor<ItemStack> CORE_ITEM = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.ITEM_STACK);
    /** エレノアの髪留め（黒とピンクのリボン）を頭に付けているか（見た目だけ。2026-10-22） */
    private static final EntityDataAccessor<Boolean> HAIR_RIBBON = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private BlockPos home;
    /** エレノアの髪留めを一度でも結んでもらったか（同調率は初回だけ） */
    private boolean ribbonGifted;
    public int homeRadius = 12;
    /** 保護を外して消してよい（{@link ValkyrieProtect#release} と組で使う） */
    private boolean released;
    /** 胸のコア（ルミナルコア） */
    private ItemStack core = ItemStack.EMPTY;
    /** 通常のダメージの処理の最中か（HP 0 への書き換えが、ダメージによるものか外からの即死かを見分ける） */
    private boolean inHurt;
    /** 直前の正常な HP（オメガコアで、HP を直に 0 以下に書き換えられたときに戻す値） */
    private float lastGoodHealth = 40.0F;
    /** 装備（換装画面のハードポイント） */
    private ValkyrieInventory inventory;
    /** 戦闘の状態（registerGoals で作る。親のコンストラクタから呼ばれるので初期値は書かない） */
    private ValkyrieCombat.Brain combat;
    /** 作戦指示（拠点の範囲・交戦規定） */
    public final ValkyrieOrders orders = new ValkyrieOrders();
    /** 同調率の履歴（新しい順、最大 {@link #SYNC_HISTORY}）: 増減・理由（翻訳キーの後ろ）・ゲーム時刻 */
    public final java.util.ArrayDeque<SyncEntry> syncHistory = new java.util.ArrayDeque<>();
    public static final int SYNC_HISTORY = 8;
    /** 戦績: 撃破数・稼働時間（Tick）・一緒に倒したボス（名前、最大 {@link #MAX_BOSSES}） */
    public int kills;
    public long uptime;
    public final java.util.List<String> bosses = new java.util.ArrayList<>();
    public static final int MAX_BOSSES = 20;
    /** 最後に被弾した Tick（シールドの回復を待つ） */
    private int lastHurtTick = -SHIELD_DELAY;
    /** なでた日（ゲーム内の日）とその日の回数 */
    private long patDay = -1;
    private int patsToday;

    public record SyncEntry(float delta, String reason, long time) {}

    // ---- クライアントだけ: 髪のなびき（ばね）と瞬き
    public float hairSwing, hairSwingO, hairVel;
    public float sideSway, sideSwayO, sideVel;
    public int blinkTicks, nextBlink = 60;
    /** 機能停止の座り込みの進み（0〜1、なめらかに） */
    public float downAnim, downAnimO;
    /** 構えの重み（0〜1、なめらかに）: 近接・射撃 */
    public float meleeReady, meleeReadyO, rangedReady, rangedReadyO;
    /** ステップの動き: 向き（{@code ValkyrieCombat.StepDir} の順、-1 = なし）と始めた Tick */
    public int stepDir = -1;
    /** Type-H の着地の揺れ: 空中にいる間の一番大きな落下距離（ValkyrieTypeTraits） */
    public float typeFall;
    public int stepStart;
    private int stepSeq, attackSeq, gestureSeq;
    /** 仕草: 種類（0 = なし）と始めた Tick（クライアント） */
    public int gestureKind, gestureStart;
    /** 腰掛け・整備の重み（0〜1、なめらかに。クライアント） */
    public float sitAnim, sitAnimO, chargeAnim, chargeAnimO;
    /** 日常の状態（セリフの間隔・朝の挨拶・好物の回数など。{@link com.automatavalkyries.entity.ai.ValkyrieDaily.Life}） */
    private com.automatavalkyries.entity.ai.ValkyrieDaily.Life life;
    /** 近接の攻撃の動き: 種類（0 = なし）と始めた Tick */
    public int attackKind;
    public int attackStart;
    /** 左手（二刀流の左手の武器）の攻撃か */
    public boolean attackLeft;
    /** 手に持つ物の傾きの戻し（ラジアン。腕を上げた分だけ持つ物を下へ戻す。モデルが毎フレーム決め、手の物の描画が使う） */
    public float itemTiltRight, itemTiltLeft;
    private float lastBodyRot;

    public ValkyrieEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 全体仕様書 §3.1 Type-V: HP 40、外装 4 / 2、速度 0.28
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(EXPRESSION, 0);
        entityData.define(MODE, Mode.WAIT.ordinal());
        entityData.define(SHUTDOWN, false);
        entityData.define(OWNER, Optional.empty());
        entityData.define(HAPPY, 0);
        entityData.define(BODY, ValkyrieBody.V.ordinal());
        entityData.define(SYNC, 0.0F);
        entityData.define(SHIELD, ValkyrieBody.V.maxShield);
        entityData.define(ENERGY, MAX_ENERGY);
        entityData.define(ASLEEP, false);
        entityData.define(SPEECH, "");
        entityData.define(SPEECH_TICKS, 0);
        entityData.define(EMOTE, 0);
        entityData.define(EMOTE_TICKS, 0);
        entityData.define(CORE_ITEM, ItemStack.EMPTY);
        entityData.define(HAIR_RIBBON, false);
        entityData.define(UNIT_HEAD, ItemStack.EMPTY);
        entityData.define(UNIT_CHEST, ItemStack.EMPTY);
        entityData.define(UNIT_LEGS, ItemStack.EMPTY);
        entityData.define(UNIT_BACK, ItemStack.EMPTY);
        entityData.define(COMBAT_POSE, (byte) 0);
        entityData.define(STEP_ANIM, 0);
        entityData.define(ATTACK_ANIM, 0);
        entityData.define(SITTING, false);
        entityData.define(CHARGING, false);
        entityData.define(GESTURE_ANIM, 0);
        entityData.define(MOOD, 0);
        entityData.define(MOOD_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        combat = new ValkyrieCombat.Brain(this);
        goalSelector.addGoal(0, new FloatGoal(this));
        // 戦闘（段階 3）: かばう・後退・戦うが追従より先
        goalSelector.addGoal(1, new ValkyrieCombat.Guard(this));
        goalSelector.addGoal(1, new ValkyrieCombat.Retreat(this));
        goalSelector.addGoal(1, new ValkyrieCombat.Medic(this)); // ロジック: メディック（戦うより先に手当て）
        // 日常（段階 4）: 自分で充電・作業の見守り・お掃除・腰掛け
        goalSelector.addGoal(1, new com.automatavalkyries.entity.ai.ValkyrieDaily.SelfCharge(this));
        goalSelector.addGoal(2, new com.automatavalkyries.entity.ai.ValkyrieDaily.WatchWork(this));
        goalSelector.addGoal(2, new com.automatavalkyries.entity.ai.ValkyriePorter(this)); // ロジック: ポーター（運搬）
        goalSelector.addGoal(4, new com.automatavalkyries.entity.ai.ValkyrieDaily.Clean(this));
        goalSelector.addGoal(5, new com.automatavalkyries.entity.ai.ValkyrieDaily.Sit(this));
        goalSelector.addGoal(2, new ValkyrieCombat.Fight(this));
        goalSelector.addGoal(3, new ValkyrieGoals.FollowOwner(this));
        goalSelector.addGoal(4, new ValkyrieGoals.KeepHome(this));
        goalSelector.addGoal(5, new ValkyrieGoals.Wander(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new ValkyrieCombat.Targeting(this));
    }

    /** 戦闘の状態（ダッシュ・横ステップ・滞空・セリフの間隔。{@link ValkyrieCombat}） */
    public ValkyrieCombat.Brain combat() {
        if (combat == null) combat = new ValkyrieCombat.Brain(this);
        return combat;
    }

    // ------------------------------------------------------------------ 状態

    public int getExpression() {
        return Mth.clamp(entityData.get(EXPRESSION), 0, EXPRESSIONS.length - 1);
    }

    public void setExpression(int index) {
        entityData.set(EXPRESSION, Math.floorMod(index, EXPRESSIONS.length));
    }

    /** 今の表情（機能停止中は目を閉じた顔、なでられた直後は笑顔） */
    public String expressionName() {
        if (isShutdown()) return "shutdown";
        if (isAsleep()) return "sleepy";
        if (entityData.get(HAPPY) > 0) return "smile";
        if (entityData.get(MOOD_TICKS) > 0) return EXPRESSIONS[Mth.clamp(entityData.get(MOOD), 0, EXPRESSIONS.length - 1)];
        return EXPRESSIONS[getExpression()];
    }

    public Mode getMode() {
        return Mode.values()[Mth.clamp(entityData.get(MODE), 0, Mode.values().length - 1)];
    }

    /** 行動モードを変える。追従は持ち主ごとに {@link #MAX_FOLLOWERS} 体まで（超えるなら false）。拠点防衛・巡回は今いる場所を拠点にする */
    public boolean setMode(Mode mode) {
        if (mode == Mode.FOLLOW && getMode() != Mode.FOLLOW && level() instanceof ServerLevel server && followers(server, getOwnerUUID()) >= MAX_FOLLOWERS) return false;
        if (mode == Mode.DEFEND || mode == Mode.PATROL) home = blockPosition();
        if (mode == Mode.WAIT) life().waitSpot = onGround() ? blockPosition() : null; // 待機の日常（腰掛け・お掃除）の中心。空中なら着地してから決める
        entityData.set(MODE, mode.ordinal());
        applyOrders();
        getNavigation().stop();
        return true;
    }

    /** その持ち主に追従している兵姫の数（読み込まれている全部の次元） */
    public static int followers(ServerLevel any, @Nullable UUID owner) {
        if (owner == null) return 0;
        int n = 0;
        for (ServerLevel level : any.getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ValkyrieEntity v && owner.equals(v.getOwnerUUID()) && v.getMode() == Mode.FOLLOW) n++;
            }
        }
        return n;
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return entityData.get(OWNER).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID owner) {
        entityData.set(OWNER, Optional.ofNullable(owner));
    }

    @Nullable
    public Player getOwner() {
        UUID id = getOwnerUUID();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    public boolean isShutdown() {
        return entityData.get(SHUTDOWN);
    }

    public boolean isReleased() {
        return released;
    }

    public ItemStack getCore() {
        if (core != null && level().isClientSide) {
            // クライアントは送られた写し（種類だけ）。メニューで中身ごと届いた同じコアがあればそちら（電力の表示用）。core が null の間は構築の途中
            ItemStack synced = entityData.get(CORE_ITEM);
            return !core.isEmpty() && ItemStack.isSameItem(core, synced) ? core : synced;
        }
        return core == null ? ItemStack.EMPTY : core; // 構築の途中（親のコンストラクタから呼ばれたとき）はまだ null
    }

    public void setCore(ItemStack stack) {
        core = stack == null ? ItemStack.EMPTY : stack.copy();
        syncCore();
    }

    private static EntityDataAccessor<ItemStack> unitKey(int slot) {
        return switch (slot) {
            case ValkyrieInventory.HEAD -> UNIT_HEAD;
            case ValkyrieInventory.CHEST -> UNIT_CHEST;
            case ValkyrieInventory.LEGS -> UNIT_LEGS;
            default -> UNIT_BACK;
        };
    }

    /** 外装・背部の物をクライアントへ（種類だけ） */
    public void syncUnit(int slot, ItemStack stack) {
        if (level() == null || level().isClientSide || entityData == null) return;
        ItemStack copy = stack == null || stack.isEmpty() ? ItemStack.EMPTY : new ItemStack(stack.getItem());
        EntityDataAccessor<ItemStack> key = unitKey(slot);
        if (!ItemStack.isSameItem(entityData.get(key), copy)) entityData.set(key, copy);
    }

    /** 外装・背部の物（{@link ValkyrieInventory#HEAD} など）。クライアントでも使える（見た目） */
    public ItemStack unitItem(int slot) {
        if (level() != null && level().isClientSide) return entityData.get(unitKey(slot));
        return getInventory().getItem(slot);
    }

    /** コアの写しをクライアントへ（種類だけ見るので、電力の NBT は付けない） */
    private void syncCore() {
        ItemStack copy = core == null || core.isEmpty() ? ItemStack.EMPTY : new ItemStack(core.getItem());
        if (!ItemStack.isSameItem(entityData.get(CORE_ITEM), copy)) entityData.set(CORE_ITEM, copy);
    }

    /** 胸のコアがステラ・ジェネシス・オメガか（即死・消去を完全に無効にする） */
    public boolean hasOmegaCore() {
        return getCore().is(ModWeapons.CORE_STELLA_GENESIS_OMEGA.get());
    }

    /**
     * オメガコアが断るデバフか。バニラ・他 MOD・このMOD を問わず悪い効果（HARMFUL）と、
     * 分類が悪い効果になっていなくても名前がフラグ辞書で保護対象に有害とされる物（停止・拘束・即死の印など）
     */
    public static boolean isOmegaBlockedDebuff(MobEffect effect) {
        if (effect.getCategory() == MobEffectCategory.HARMFUL) return true;
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return id != null && com.automatavalkyries.protect.flag.FlagDictionary.harmfulOnProtected(id.getPath());
    }

    /** オメガコアならデバフを受けない */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (hasOmegaCore() && isOmegaBlockedDebuff(effect.getEffect())) return false;
        return super.canBeAffected(effect);
    }

    /** canBeAffected を通らずに入ったデバフ（積む前から掛かっていた物を含む）を消す */
    private void clearDebuffs() {
        for (MobEffect effect : java.util.List.copyOf(getActiveEffectsMap().keySet())) {
            if (isOmegaBlockedDebuff(effect)) removeEffect(effect);
        }
    }

    // ------------------------------------------------------------------ 段階 3: 構え・ステップの動き（モデルの動きの合図）

    /** 戦いの構え（0 = なし、1 = 近接、2 = 射撃）。戦闘 AI が戦っている間に付ける */
    public void setCombatPose(int pose) {
        if (entityData.get(COMBAT_POSE) != pose) entityData.set(COMBAT_POSE, (byte) pose);
    }

    public int getCombatPose() {
        return entityData.get(COMBAT_POSE);
    }

    /** ステップの動きを始める（dir は {@code ValkyrieCombat.StepDir} の順: 前・後ろ・左・右） */
    public void playStep(int dir) {
        entityData.set(STEP_ANIM, ((++stepSeq & 0xFFFFF) << 3) | (dir + 1));
    }

    // ------------------------------------------------------------------ 段階 4: 日常（腰掛け・整備・仕草・気分・セリフ）

    /** 調べ用: 今動いている AI の目標の名前 */
    public String goalSelectorRunning() {
        return goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList().toString();
    }

        public com.automatavalkyries.entity.ai.ValkyrieDaily.Life life() {
        if (life == null) life = new com.automatavalkyries.entity.ai.ValkyrieDaily.Life(this);
        return life;
    }

    public boolean isSitting() {
        return entityData.get(SITTING);
    }

    public void setSitting(boolean sitting) {
        if (entityData.get(SITTING) != sitting) entityData.set(SITTING, sitting);
    }

    /** 充電台で整備している（座って胸のコアに手を当てる） */
    public boolean isCharging() {
        return entityData.get(CHARGING);
    }

    public void setCharging(boolean charging) {
        if (entityData.get(CHARGING) != charging) entityData.set(CHARGING, charging);
    }

    /** 仕草を始める（{@link #GESTURE_JOY} など） */
    public void gesture(int kind) {
        entityData.set(GESTURE_ANIM, ((++gestureSeq & 0xFFFFF) << 3) | (kind & 7));
    }

    /** 気分の表情を少しの間出す（index は {@link #EXPRESSIONS} の番号） */
    public void mood(int index, int ticks) {
        entityData.set(MOOD, index);
        entityData.set(MOOD_TICKS, ticks);
    }

    /**
     * 場面のセリフ（Type-V の性格、同調率 50% 以上なら親しい口調。3 つのうち 1 つ）。lang の
     * {@code valkyrie.automata_valkyries.speech.<場面>.<low|high>.<1〜3>}。同じ場面は {@code Life} の間隔より短く繰り返さない。言えたら true
     */
    public boolean talk(String situation) {
        if (level().isClientSide || isShutdown() || !life().mayTalk(situation)) return false;
        String tier = getSyncRate() >= 50.0F ? "high" : "low";
        say(situation + "." + tier + "." + (1 + random.nextInt(3)));
        return true;
    }

    /** 場面のセリフを間隔を見ずに言う（指示リモコンから） */
    public void talkNow(String situation) {
        if (level().isClientSide || isShutdown()) return;
        say(situation + "." + (getSyncRate() >= 50.0F ? "high" : "low") + "." + (1 + random.nextInt(3)));
    }

    /** 近接の攻撃の動きを始める（kind は {@link #ATTACK_SLASH} など） */
    public void playAttack(int kind) {
        playAttack(kind, false);
    }

    /** 近接の攻撃の動きを始める（left なら左手で。二刀流の左手の武器） */
    public void playAttack(int kind, boolean left) {
        entityData.set(ATTACK_ANIM, ((++attackSeq & 0xFFFFF) << 4) | (left ? 8 : 0) | (kind & 7));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (GESTURE_ANIM.equals(key) && level().isClientSide) {
            gestureKind = entityData.get(GESTURE_ANIM) & 7;
            gestureStart = tickCount;
        }
        if (ATTACK_ANIM.equals(key) && level().isClientSide) {
            attackKind = entityData.get(ATTACK_ANIM) & 7;
            attackLeft = (entityData.get(ATTACK_ANIM) & 8) != 0;
            attackStart = tickCount;
        }
        if (STEP_ANIM.equals(key) && level().isClientSide) {
            int v = entityData.get(STEP_ANIM);
            stepDir = (v & 7) - 1;
            stepStart = tickCount;
        }
    }

    // ------------------------------------------------------------------ 段階 3: 吹き出し・感情アイコン

    /**
     * セリフ: 頭上の吹き出しだけに出す（key は翻訳キー。lang の {@code valkyrie.automata_valkyries.speech.<key>}）。
     * チャット・HUD には出さない（2026-10-05〜06 ユーザー指定「テキストは頭上だけでいい」。SPEC_VALKYRIE_GUI_AI §4 の「チャットにも」を置き換え）
     */
    public void say(String key) {
        if (level().isClientSide) return;
        entityData.set(SPEECH, key);
        entityData.set(SPEECH_TICKS, SPEECH_DURATION);
    }

    public static String speechKey(String key) {
        return "valkyrie.automata_valkyries.speech." + key;
    }

    /** 今の吹き出し（翻訳キーの後ろ。無ければ空）と残り Tick */
    public String getSpeech() {
        return entityData.get(SPEECH_TICKS) > 0 ? entityData.get(SPEECH) : "";
    }

    public int getSpeechTicks() {
        return entityData.get(SPEECH_TICKS);
    }

    /** 頭上に感情アイコンを短く出す */
    public void emote(Emote emote) {
        if (level().isClientSide) return;
        entityData.set(EMOTE, emote.ordinal());
        entityData.set(EMOTE_TICKS, emote == Emote.NONE ? 0 : EMOTE_DURATION);
    }

    public Emote getEmote() {
        int i = entityData.get(EMOTE);
        return entityData.get(EMOTE_TICKS) > 0 && i >= 0 && i < Emote.values().length ? Emote.values()[i] : Emote.NONE;
    }

    public int getEmoteTicks() {
        return entityData.get(EMOTE_TICKS);
    }

    // ------------------------------------------------------------------ 段階 2: 装備・素体・同調率・シールド・電力

    public ValkyrieInventory getInventory() {
        if (inventory == null) inventory = new ValkyrieInventory(this); // 親のコンストラクタから呼ばれることがある
        return inventory;
    }

    public ValkyrieBody getBody() {
        return ValkyrieBody.of(entityData.get(BODY));
    }

    /** 素体タイプを変える（HP・速度の基本値も合わせる。弾薬庫の枠を超えた物は落とす） */
    public void setBody(ValkyrieBody body) {
        entityData.set(BODY, body.ordinal());
        com.automatavalkyries.entity.ai.ValkyrieTypeTraits.applyAttributes(this, body);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(body.maxHealth);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(body.speed);
        if (DirectRead.health(this) > body.maxHealth) super.setHealth(body.maxHealth);
        if (getShield() > body.maxShield) entityData.set(SHIELD, body.maxShield);
        if (!level().isClientSide) for (ItemStack stack : getInventory().overflowAmmo()) spawnAtLocation(stack);
    }

    public float getSyncRate() {
        return entityData.get(SYNC);
    }

    /** 同調率を増減する（0〜100）。履歴に理由を残す */
    public void addSync(float delta, String reason) {
        float before = getSyncRate();
        float after = Mth.clamp(before + delta, 0.0F, 100.0F);
        if (after == before) return;
        entityData.set(SYNC, after);
        syncHistory.addFirst(new SyncEntry(after - before, reason, level().getGameTime()));
        while (syncHistory.size() > SYNC_HISTORY) syncHistory.removeLast();
        // 20% の節目を越えたら喜ぶ（段階 4）
        if (!level().isClientSide && (int) (after / 20.0F) > (int) (before / 20.0F)) {
            talk("sync_up");
            gesture(GESTURE_JOY);
            mood(1, 60);
        }
    }

    public float getShield() {
        return entityData.get(SHIELD);
    }

    public float getMaxShield() {
        return (getBody().maxShield + com.automatavalkyries.entity.ai.ValkyrieUnitEffects.frameShield(this)) * omegaMultiplier() * syncMultiplier();
    }

    /** HP・シールドを今の最大まで下げる（外装・コアを外したとき） */
    public void clampVitals() {
        if (DirectRead.health(this) > getMaxHealth()) super.setHealth(getMaxHealth());
        if (getShield() > getMaxShield()) entityData.set(SHIELD, getMaxShield());
    }

    /** コマンドリンクにつながっている期限（ゲーム時刻。サーバー） */
    public long commandLinkUntil;

    /** 電力の最大（ルミナスフレーム一式で 1.5 倍。クライアントでも見た目の外装から） */
    public int maxEnergy() {
        return com.automatavalkyries.entity.ai.ValkyrieUnitEffects.maxEnergy(this);
    }

    /** 最後に被弾してからの Tick */
    public int ticksSinceHurt() {
        return tickCount - lastHurtTick;
    }

    /** シールドの回復を ticks 分だけ足す（セラフフレーム。回復できる間だけ、電力は使わない） */
    public void bonusShieldRegen(float ticks) {
        float shield = getShield();
        if (isShutdown() || shield >= getMaxShield() || ticksSinceHurt() < SHIELD_DELAY) return;
        entityData.set(SHIELD, Math.min(getMaxShield(), shield + SHIELD_REGEN * omegaMultiplier() * syncMultiplier() / 20.0F * ticks));
    }

    public int getEnergy() {
        return entityData.get(ENERGY);
    }

    public void setEnergy(int energy) {
        entityData.set(ENERGY, Mth.clamp(energy, 0, maxEnergy()));
    }

    /** 電力切れで休眠しているか（動かない。補充されたら起きる） */
    public boolean isAsleep() {
        return entityData.get(ASLEEP);
    }

    /** 積んでいる重さ（t）: 手の兵装とコア（外装・背部・チップは後で） */
    public float carriedWeight() {
        float w = 0.0F;
        for (int slot : new int[]{ValkyrieInventory.MAIN_HAND, ValkyrieInventory.OFF_HAND}) {
            if (getInventory().getItem(slot).getItem() instanceof com.automatavalkyries.weapon.TacticalWeaponItem weapon) w += weapon.weight();
        }
        if (getCore().getItem() instanceof com.automatavalkyries.weapon.LuminalCoreItem c) w += c.grade().weight;
        w += com.automatavalkyries.entity.ai.ValkyrieUnitEffects.unitWeight(this); // 外装・背部ユニット・チップ（2026-10-06）
        return w;
    }

    public float weightLimit() {
        return getBody().payload;
    }

    public boolean overweight() {
        return carriedWeight() > weightLimit();
    }

    /** 攻撃力（装備・同調率込み）。銃は 1 発 × 弾数 */
    public float attackValue() {
        ItemStack main = getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        float melee = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (main.getItem() instanceof com.automatavalkyries.weapon.TacticalWeaponItem weapon && weapon.shotDamage() != null) {
            float[] shot = weapon.shotDamage();
            return Math.max(melee * omegaMultiplier(), shot[0] * shot[1] * syncMultiplier() * omegaMultiplier());
        }
        return melee * omegaMultiplier(); // 与えるダメージはオメガコアで 3 倍（ValkyrieCombat#onHurt）
    }

    public float baseAttack() {
        return (float) getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
    }

    public float defenseValue() {
        return (float) getAttributeValue(Attributes.ARMOR);
    }

    public float baseDefense() {
        return (float) getAttributeBaseValue(Attributes.ARMOR);
    }

    public float speedValue() {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    public float baseSpeed() {
        return (float) getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
    }

    /** オメガコア（ステラ・ジェネシス・オメガ）なら 3、無ければ 1 */
    public float omegaMultiplier() {
        return hasOmegaCore() ? OMEGA_MULTIPLIER : 1.0F;
    }

    /**
     * オメガコアの全ステータス 3 倍を付け外しする（最大 HP・防御・防具強度・速度。シールドの最大と回復・与えるダメージは各所で掛ける）。
     * 外して最大 HP が下がったら HP をそこまで下げる
     */
    private void applyOmega() {
        double bonus = omegaMultiplier() - 1.0;
        applyModifier(Attributes.MAX_HEALTH, OMEGA_HEALTH_ID, "valkyrie omega health", bonus);
        applyModifier(Attributes.ARMOR, OMEGA_ARMOR_ID, "valkyrie omega armor", bonus);
        applyModifier(Attributes.ARMOR_TOUGHNESS, OMEGA_TOUGHNESS_ID, "valkyrie omega toughness", bonus);
        applyModifier(Attributes.MOVEMENT_SPEED, OMEGA_SPEED_ID, "valkyrie omega speed", bonus);
        clampVitals();
    }

    /**
     * 同調率の能力上昇の倍率（20% ごとに段階的、100% で 1.2 倍【仮】）。2026-10-20 ユーザー指定「同調率でのステータスアップを実装して」（質問で: 兵装の威力・
     * 最大 HP と装甲・兵装の速さ・シールドと電力の全部、上がり幅は今の +20% のまま、20% ごとの段階）で、次の全部に掛ける:
     * 与えるダメージ（{@code ValkyrieCombat.onHurt}。兵装の技・飛び道具・近接すべて）、最大 HP・装甲・装甲強度・移動速度（属性）、
     * 攻撃の間隔と技の再使用（{@code ValkyrieUnitEffects.attackIntervalScale}）、シールドの最大と回復、電力の消費（{@code ValkyrieUnitEffects.powerScale}。割る）
     */
    public float syncMultiplier() {
        int steps = (int) (getSyncRate() / 20.0F);
        if (level().getGameTime() < commandLinkUntil) steps = Math.min(steps + 1, 5); // コマンドリンクで 1 段上げ
        return 1.0F + (float) SYNC_BONUS * steps / 5.0F;
    }

    /** 装備一式の毎 Tick の消費 FE（今は体の維持だけ。兵装は撃つたび・背部ユニットは使うたびなので入れない） */
    public int fePerTick() {
        return com.automatavalkyries.entity.ai.ValkyrieUnitEffects.upkeep(this);
    }

    /** 装備が変わったとき（換装画面）: 積載超過の遅さを付け直す */
    public void onEquipmentChanged() {
        if (level() == null || level().isClientSide) return;
        applyModifier(Attributes.MOVEMENT_SPEED, OVERWEIGHT_ID, "valkyrie overweight", overweight() ? OVERWEIGHT_SLOW : 0.0);
    }

    /** 作戦指示を拠点の範囲へ反映する（巡回なら巡回範囲、それ以外は防衛半径） */
    public void applyOrders() {
        homeRadius = getMode() == Mode.PATROL ? orders.patrolRadius : orders.defendRadius;
    }

    /** 今いる場所を拠点にする */
    public void setHomeHere() {
        home = blockPosition();
    }

    private void applyModifier(net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id, String name, double amount) {
        var instance = getAttribute(attribute);
        if (instance == null) return;
        var current = instance.getModifier(id);
        if (current != null && current.getAmount() == amount) return;
        if (current != null) instance.removeModifier(id);
        if (amount != 0.0) instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, name, amount,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 電力・シールド・休眠・同調率の能力上昇・稼働時間（毎 Tick、サーバー） */
    private void powerTick() {
        int energy = getEnergy();
        // 使う分（維持・兵装・シールド）を先に引き、最後にコアから補充する（補充を先にすると、コアに電力があっても満タンに見えなかった。2026-10-05）
        if (!isShutdown()) energy -= com.automatavalkyries.entity.ai.ValkyrieUnitEffects.upkeep(this); // リソースセーバーなら半分
        setEnergy(energy);
        // 手の兵装の毎 Tick の処理（プレイヤーの持ち物と同じく inventoryTick: 兵姫の電力から内蔵バッテリーへ補充・T4 の加護など。段階 3）
        if (!isShutdown()) {
            ItemStack main = getMainHandItem(), off = getOffhandItem();
            if (!main.isEmpty()) main.inventoryTick(level(), this, 0, true);
            if (!off.isEmpty()) off.inventoryTick(level(), this, 1, false);
        }
        energy = getEnergy();
        // シールドの回復（最後の被弾から SHIELD_DELAY 後、毎秒 SHIELD_REGEN）
        float shield = getShield();
        if (!isShutdown() && shield < getMaxShield() && tickCount - lastHurtTick >= SHIELD_DELAY) {
            float add = Math.min(SHIELD_REGEN * omegaMultiplier() * syncMultiplier() / 20.0F, getMaxShield() - shield);
            int cost = (int) Math.ceil(add * SHIELD_COST);
            if (energy >= cost) {
                energy -= cost;
                entityData.set(SHIELD, shield + add);
            }
        }
        // コアから補充（放電レートと Max I/O で頭打ち）
        if (energy < maxEnergy() && getCore().getItem() instanceof com.automatavalkyries.weapon.LuminalCoreItem c) {
            energy += c.extract(getCore(), Math.min(maxEnergy() - Math.max(0, energy), MAX_IO));
        }
        setEnergy(energy);
        // 電力 0 で休眠、補充されたら起きる（暫定統一 C）
        if (!isAsleep() && getEnergy() <= 0) {
            entityData.set(ASLEEP, true);
            getNavigation().stop();
            setTarget(null);
            setSitting(false);
            talk("sleepy");
        } else if (isAsleep() && getEnergy() >= WAKE_ENERGY && !podSleeper) { // 墜落ポッドの深い休眠は端末でだけ起きる
            entityData.set(ASLEEP, false);
            life().onWake();
        }
        if (!isShutdown() && !isAsleep()) uptime++;
        applySyncBonus();
        applyOmega();
    }

    /** 同調率の能力上昇を属性に掛ける（最大 HP・装甲・装甲強度・移動速度。毎 Tick と GameTest から） */
    public void applySyncBonus() {
        double bonus = syncMultiplier() - 1.0;
        applyModifier(Attributes.MOVEMENT_SPEED, SYNC_SPEED_ID, "valkyrie sync speed", bonus);
        applyModifier(Attributes.ATTACK_DAMAGE, SYNC_ATTACK_ID, "valkyrie sync attack", 0.0);       // 与えるダメージは ValkyrieCombat.onHurt で全部に掛ける（近接だけ二重にならないよう外す）
        applyModifier(Attributes.MAX_HEALTH, SYNC_HEALTH_ID, "valkyrie sync health", bonus);
        applyModifier(Attributes.ARMOR, SYNC_ARMOR_ID, "valkyrie sync armor", bonus);
        applyModifier(Attributes.ARMOR_TOUGHNESS, SYNC_TOUGHNESS_ID, "valkyrie sync toughness", bonus);
    }

    /** GameTest 用: 保護を通らない消去（他 MOD が無理やり消した）を真似る。バックアップは残すので見張りが戻す */
    public void eraseBypassingProtectForTest() {
        released = true;
        discard();
    }

    /** 保護を外して消す（持ち主が手放す・管理者のコマンド） */
    public void releaseAndDiscard() {
        released = true;
        if (level() instanceof ServerLevel server) ValkyrieProtect.release(server.getServer(), getUUID());
        discard();
    }

    // ------------------------------------------------------------------ 機能停止と修理

    /** 機能停止: 死なずに崩れ落ちて（死亡モーションはモデル側）その場に座り込んで止まる（HP は 1 で持つ。直すまで動かず、狙われず、ダメージも受けない） */
    public void shutdown() {
        if (level().isClientSide) return;
        super.setHealth(1.0F);
        deathTime = 0;
        if (!isShutdown()) {
            entityData.set(SHUTDOWN, true);
            getNavigation().stop();
            setTarget(null);
            stopRiding();
            playSound(sound("shutdown"), 1.0F, 1.0F);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(1.0), getZ(), 14, 0.3, 0.4, 0.3, 0.15);
                server.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.6), getZ(), 6, 0.2, 0.3, 0.2, 0.01);
            }
        }
    }

    /** 外からの即死（kill / die / HP 0 への書き換え / 桁外れのダメージ）: オメガコアなら受け付けない（false）、無ければ機能停止（true） */
    private boolean takeInstantKill(String what) {
        ValkyrieProtect.noteLethal(this, what);
        if (hasOmegaCore()) return false;
        shutdown();
        return true;
    }

    /** 修理（REPAIR 回復）。機能停止中なら起きる */
    public void repair(float amount) {
        super.setHealth(Math.min(getMaxHealth(), DirectRead.health(this) + amount));
        if (isShutdown()) {
            entityData.set(SHUTDOWN, false);
            playSound(sound("wake"), 1.0F, 1.0F);
            if (!level().isClientSide) { // 機能停止からの復帰（拡張仕様書 §12.1 +10%）
                addSync(10.0F, "revived");
                talk("revived");
                gesture(GESTURE_NOD);
            }
        } else if (!level().isClientSide && getOwner() != null && random.nextFloat() < 0.5F) {
            talk("repaired");
        }
        if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(1.0), getZ(), 8, 0.3, 0.3, 0.3, 0);
    }

    @Override
    public void setHealth(float health) {
        if (health <= 0.0F && !level().isClientSide && isAddedToWorld()) {
            if (inHurt) shutdown(); // 通常のダメージで HP 0 → 死亡モーションから機能停止
            else takeInstantKill("setHealth(" + health + ")");
            return;
        }
        super.setHealth(health);
    }

    @Override
    public void die(DamageSource source) {
        takeInstantKill("die(" + source.getMsgId() + ")");
    }

    @Override
    public void kill() {
        takeInstantKill("kill()");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isShutdown()) return false;
        if (!Float.isFinite(amount) || amount >= INSTANT_KILL_DAMAGE) {
            // 桁外れの 1 撃は即死の扱い
            if (level().isClientSide) return false;
            takeInstantKill("hurt(" + source.getMsgId() + ", " + amount + ")");
            return !hasOmegaCore();
        }
        inHurt = true;
        try {
            return super.hurt(source, amount);
        } finally {
            inHurt = false;
        }
    }

    /** シールドが先に受ける（防具の後、HP の前） */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isInvulnerableTo(source)) {
            lastHurtTick = tickCount;
            amount = com.automatavalkyries.entity.ai.ValkyrieUnitEffects.galliaBarrier(this, source, amount); // シールドより先に（シールドが受けると変換されなかった）
            float shield = getShield();
            if (shield > 0.0F && amount > 0.0F) {
                float absorbed = Math.min(shield, amount);
                entityData.set(SHIELD, shield - absorbed);
                amount -= absorbed;
                if (amount <= 0.0F) return;
            }
            if (com.automatavalkyries.entity.ai.ValkyrieUnitEffects.tachyonPhase(this, source, amount)) return; // タキオンドライブ
        }
        super.actuallyHurt(source, amount);
    }

    @Override
    public boolean isDeadOrDying() {
        return false; // HP 0 でも死なない（機能停止）
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isShutdown() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean isPushable() {
        return !isShutdown() && super.isPushable();
    }

    /** 機能停止中は動かない（LivingEntity#aiStep が AI の処理ごと飛ばす） */
    @Override
    protected boolean isImmobile() {
        return isShutdown() || isAsleep() || super.isImmobile();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!ValkyrieProtect.allowRemoval(this, reason)) return;
        super.remove(reason);
    }

    // ------------------------------------------------------------------ 操作

    // ------------------------------------------------------------------ 墜落ポッド（追加拡張仕様書 §8.2）

    /** 墜落ポッドで深く眠っている（端末にプロトコアを差すまで起きない・持ち主にできない） */
    private boolean podSleeper;

    public boolean isPodSleeper() {
        return podSleeper;
    }

    /** 墜落ポッドの中で眠らせる（HP 5、電力 0、深い休眠。生成のとき） */
    public void makePodSleeper() {
        podSleeper = true;
        super.setHealth(5.0F);
        setEnergy(0);
        entityData.set(ASLEEP, true);
        setSitting(true);
    }

    /**
     * 端末（{@link com.automatavalkyries.block.DamagedInterfaceBlock}）で目覚める: 使った人が持ち主になり、プロトコアの電力が入る。
     * 持ち主にできなければ（登録の上限）false
     */
    public boolean wakeFromPod(Player player, int energy) {
        if (!(level() instanceof ServerLevel sl)) return false;
        if (ValkyrieProtect.ownedCount(sl.getServer(), player.getUUID()) >= ValkyrieProtect.MAX_REGISTERED) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.registry_full", ValkyrieProtect.MAX_REGISTERED).withStyle(ChatFormatting.RED), true);
            return false;
        }
        podSleeper = false;
        com.automatavalkyries.world.CrashedPods.forget(sl, blockPosition()); // 兵姫探知レーダーの記録から外す
        setOwnerUUID(player.getUUID());
        setMode(Mode.FOLLOW);
        setEnergy(getEnergy() + energy);
        entityData.set(ASLEEP, false);
        setSitting(false);
        playSound(sound("core_startup"), 1.0F, 1.0F);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(1.0), getZ(), 20, 0.4, 0.5, 0.4, 0.15);
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        talkNow("pod_wake");
        gesture(GESTURE_NOD);
        return true;
    }

    /** 名前を付けていないときの名前は素体タイプごと（「戦術兵姫 Type-S」など。2026-10-07 ユーザー指定「上にある名前を対応したタイプに」） */
    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.automata_valkyries.valkyrie." + getBody().name().toLowerCase(java.util.Locale.ROOT));
    }

    /** 担架で持ち主の頭の上に乗っているときは、頭の上に座る高さへ */
    @Override
    public double getMyRidingOffset() {
        return getVehicle() instanceof Player ? 0.5 : super.getMyRidingOffset();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        boolean client = level().isClientSide;
        UUID owner = getOwnerUUID();
        ItemStack held = player.getItemInHand(hand);
        if (owner == null && podSleeper) { // 墜落ポッドの深い休眠: 端末にプロトコアを
            if (!client) player.displayClientMessage(Component.translatable("message.automata_valkyries.pod.sleeping").withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.sidedSuccess(client);
        }
        if (owner == null) {
            if (!client && level() instanceof ServerLevel sl && ValkyrieProtect.ownedCount(sl.getServer(), player.getUUID()) >= ValkyrieProtect.MAX_REGISTERED) {
                player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.registry_full", ValkyrieProtect.MAX_REGISTERED).withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            if (!client) {
                setOwnerUUID(player.getUUID());
                setMode(Mode.FOLLOW);
                playSound(sound("core_startup"), 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.claimed", getDisplayName()).withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (!owner.equals(player.getUUID())) return InteractionResult.PASS;
        if (held.getItem() instanceof com.automatavalkyries.item.TacticalRemoteItem) return InteractionResult.PASS; // 指示リモコン（アイテムの側で指示する）
        if (held.getItem() instanceof com.automatavalkyries.item.ValkyrieStretcherItem) return InteractionResult.PASS; // 担架（アイテムの側で乗せる）
        if (held.getItem() instanceof com.automatavalkyries.item.SyncMaximizerItem) return InteractionResult.PASS; // 同調率マキシマイザー（アイテムの側で）
        // 好物を渡す（ルミナルキャンディ・冷却アイス。拡張仕様書 §12.1 +5%）
        boolean candy = held.is(com.automatavalkyries.registry.ModItems.LUMINAL_CANDY.get()), ice = held.is(com.automatavalkyries.registry.ModItems.COOLING_ICE.get());
        if ((candy || ice) && !isShutdown()) {
            if (!client) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                life().gift(candy);
                playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 0.6F, 1.3F);
            }
            return InteractionResult.sidedSuccess(client);
        }
        // エレノアの髪留めを結んであげる（見た目 + 同調率）。ハサミで外して返してもらう
        if (held.is(com.automatavalkyries.registry.ModBossDrops.ELENOR_RIBBON.get()) && !hasHairRibbon()) {
            if (!client) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                entityData.set(HAIR_RIBBON, true);
                if (!ribbonGifted) addSync(2.0F, "hair_ribbon"); // 同調率は初めて結んだときだけ（付け外しで稼げないように）
                ribbonGifted = true;
                entityData.set(HAPPY, 40);
                playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, 0.8F, 1.3F);
                ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY(1.1), getZ(), 3, 0.3, 0.2, 0.3, 0);
                player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.hair_ribbon.on", getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (held.is(net.minecraft.world.item.Items.SHEARS) && hasHairRibbon()) {
            if (!client) {
                entityData.set(HAIR_RIBBON, false);
                ItemStack ribbon = new ItemStack(com.automatavalkyries.registry.ModBossDrops.ELENOR_RIBBON.get());
                if (!player.addItem(ribbon)) spawnAtLocation(ribbon);
                playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, 0.8F, 0.9F);
                player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.hair_ribbon.off", getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        // 修理（ナノマシン溶液・リペアインジェクター）
        if (held.is(ModWeapons.NANO_SOLUTION.get()) || held.is(ModWeapons.REPAIR_INJECTOR.get())) {
            if (DirectRead.health(this) >= getMaxHealth() && !isShutdown()) return InteractionResult.PASS;
            if (held.is(ModWeapons.REPAIR_INJECTOR.get())) {
                if (RepairInjectorItem.solution(held) <= 0) return InteractionResult.FAIL;
                if (!client) RepairInjectorItem.setSolution(held, RepairInjectorItem.solution(held) - 1);
            } else if (!client && !player.getAbilities().instabuild) {
                held.shrink(1);
            }
            if (!client) {
                repair(REPAIR);
                addSync(3.0F, "repair"); // 手での修理（拡張仕様書 §12.1）
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (isShutdown()) {
            // 機能停止中でもメニュー（換装）は開ける
            if (!player.isShiftKeyDown()) {
                if (!client) openMenu((ServerPlayer) player);
                return InteractionResult.sidedSuccess(client);
            }
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (aimsAtHead(player)) {
                // なでる: 同調率 +1（1 日 PATS_PER_DAY 回まで【仮】）
                if (!client) {
                    long day = level().getDayTime() / 24000L;
                    if (day != patDay) {
                        patDay = day;
                        patsToday = 0;
                    }
                    if (patsToday < PATS_PER_DAY) {
                        patsToday++;
                        addSync(1.0F, "pat");
                    }
                    entityData.set(HAPPY, 40);
                    talk("pat");
                    playSound(sound("emote_happy"), 0.8F, 1.0F);
                    ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY(1.1), getZ(), 1, 0.1, 0.1, 0.1, 0);
                }
            } else if (!client) {
                Mode next = getMode() == Mode.FOLLOW ? Mode.WAIT : Mode.FOLLOW;
                if (setMode(next)) {
                    playSound(sound("ui_confirm"), 0.8F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.mode." + next.name().toLowerCase(java.util.Locale.ROOT), getDisplayName()), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.too_many", MAX_FOLLOWERS).withStyle(ChatFormatting.RED), true);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        // 素手・普通のアイテム: メニュー（好物を渡すのは段階 4 で）
        if (!client) openMenu((ServerPlayer) player);
        return InteractionResult.sidedSuccess(client);
    }

    /** メニュー（換装・状態・作戦指示）を開く。状態タブの一覧（同調率の履歴・ボス）は開くときに一緒に送る */
    public void openMenu(ServerPlayer player) {
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.automatavalkyries.menu.ValkyrieMenu(id, inv, this), getDisplayName()),
                buf -> com.automatavalkyries.menu.ValkyrieMenu.writeOpenData(buf, this));
        playSound(sound("ui_beep"), 0.6F, 1.0F);
    }

    /** 視線が頭（体の高さの上 22%）に当たっているか */
    public boolean aimsAtHead(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(6.0));
        AABB box = getBoundingBox().inflate(0.1);
        Optional<Vec3> hit = box.clip(eye, end);
        double y = hit.map(h -> h.y).orElse(eye.y);
        return y >= getY() + getBbHeight() * 0.78;
    }

    // ------------------------------------------------------------------ Tick

    @Override
    public void tick() {
        if (level().isClientSide) {
            super.tick();
            clientTick();
            return;
        }
        // 担架で運ばれている: 動けるようになったら（充電・修理）自分で降りる。運ばれている間は座った姿勢
        if (getVehicle() instanceof Player carrier) {
            if (!com.automatavalkyries.item.ValkyrieStretcherItem.carriable(this)) {
                setSitting(false);
                com.automatavalkyries.item.ValkyrieStretcherItem.setDown(carrier, this);
            } else if (!isSitting()) {
                setSitting(true);
            }
        }
        // AI（戦闘・兵装の技）・手の兵装の毎 Tick の処理・ステップの間は「いま動いている兵姫」（味方へのデバフを断る。ValkyrieCombat#shieldedFromActing）
        ValkyrieEntity prev = ValkyrieCombat.setActing(this);
        try {
            serverTick();
        } finally {
            ValkyrieCombat.setActing(prev);
        }
    }

    private void serverTick() {
        super.tick();
        if (hasOmegaCore()) clearDebuffs();
        // HP が直接 0 以下に書き換えられていたら（setHealth を通らない書き換え）即死の扱い。オメガコアなら直前の HP へ戻す
        float hp = DirectRead.health(this);
        if (hp <= 0.0F || !Float.isFinite(hp)) {
            if (!takeInstantKill("health written to " + hp)) super.setHealth(lastGoodHealth > 0.0F ? lastGoodHealth : getMaxHealth());
        } else if (!isShutdown()) {
            lastGoodHealth = hp;
        }
        if (entityData.get(HAPPY) > 0) entityData.set(HAPPY, entityData.get(HAPPY) - 1);
        if (entityData.get(MOOD_TICKS) > 0) entityData.set(MOOD_TICKS, entityData.get(MOOD_TICKS) - 1);
        if (!isShutdown()) life().tick();
        if (entityData.get(SPEECH_TICKS) > 0) entityData.set(SPEECH_TICKS, entityData.get(SPEECH_TICKS) - 1);
        if (entityData.get(EMOTE_TICKS) > 0) entityData.set(EMOTE_TICKS, entityData.get(EMOTE_TICKS) - 1);
        // 持ち主が降りたら一緒に降りる（担架で持ち主の頭の上にいるときは別。ここで降りてしまい、担架で降ろせなかった。2026-10-07）
        if (isPassenger() && getMode() == Mode.FOLLOW && !(getVehicle() instanceof Player)) {
            Player owner = getOwner();
            if (owner == null || owner.getVehicle() != getVehicle()) stopRiding();
        }
        powerTick();
        if (!isShutdown() && !isAsleep()) combat().tick();
        else if (isUsingItem()) stopUsingItem();
        com.automatavalkyries.entity.ai.ValkyrieTypeTraits.tick(this, !isShutdown() && !isAsleep()); // 素体タイプの能力と立ち回り
        if (tickCount % ValkyrieProtect.INTERVAL == 0) ValkyrieProtect.snapshot(this);
    }

    /** 髪のなびき: 前へ進むほど後ろへ流れ、止まると揺れ戻る（減衰するばね）。体の向きが変わると横髪が振れる。瞬きの間合いもここで数える */
    private void clientTick() {
        Vec3 v = getDeltaMovement();
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        float forward = (float) (-v.x * Mth.sin(yaw) + v.z * Mth.cos(yaw));
        float target = Mth.clamp(0.06F + Math.max(0F, forward) * 3.2F + Math.max(0F, (float) v.y) * 1.5F, 0F, 1.0F);
        hairSwingO = hairSwing;
        hairVel = (hairVel + (target - hairSwing) * 0.22F) * 0.72F;
        hairSwing += hairVel;

        float turn = Mth.wrapDegrees(yBodyRot - lastBodyRot) * Mth.DEG_TO_RAD;
        lastBodyRot = yBodyRot;
        sideSwayO = sideSway;
        sideVel = (sideVel + (-turn * 0.8F - sideSway) * 0.25F) * 0.75F;
        sideSway = Mth.clamp(sideSway + sideVel, -0.5F, 0.5F);

        sitAnimO = sitAnim;
        chargeAnimO = chargeAnim;
        sitAnim = Mth.approach(sitAnim, isSitting() || isCharging() ? 1.0F : 0.0F, 0.12F);
        chargeAnim = Mth.approach(chargeAnim, isCharging() ? 1.0F : 0.0F, 0.1F);
        meleeReadyO = meleeReady;
        rangedReadyO = rangedReady;
        int pose = entityData.get(COMBAT_POSE);
        meleeReady = Mth.approach(meleeReady, pose == 1 ? 1.0F : 0.0F, 0.15F);
        rangedReady = Mth.approach(rangedReady, pose == 2 ? 1.0F : 0.0F, 0.15F);
        downAnimO = downAnim;
        downAnim = Mth.approach(downAnim, isShutdown() ? 1.0F : 0.0F, isShutdown() ? 0.055F : 0.1F); // 崩れ落ちるのに約 0.9 秒

        if (blinkTicks > 0) {
            blinkTicks--;
        } else if (--nextBlink <= 0) {
            blinkTicks = 3;
            nextBlink = 50 + random.nextInt(90);
        }
    }

    /** 合成した効果音（sounds.json のイベント。tools/gen_valkyrie_sounds.py） */
    public static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(com.automatavalkyries.AutomataValkyries.MODID, "valkyrie." + name));
    }

    // ------------------------------------------------------------------ 保存

    /** エレノアの髪留めを頭に付けているか（クライアントでも読める） */
    public boolean hasHairRibbon() {
        return entityData.get(HAIR_RIBBON);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (podSleeper) tag.putBoolean("PodSleeper", true);
        if (hasHairRibbon()) tag.putBoolean("HairRibbon", true);
        if (ribbonGifted) tag.putBoolean("RibbonGifted", true);
        tag.putInt("Expression", getExpression());
        tag.putString("Mode", getMode().name());
        tag.putBoolean("Shutdown", isShutdown());
        if (getOwnerUUID() != null) tag.putUUID("Owner", getOwnerUUID());
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putInt("HomeRadius", homeRadius);
        if (!getCore().isEmpty()) tag.put("Core", getCore().save(new CompoundTag()));
        tag.putString("Body", getBody().name());
        tag.put("Equipment", getInventory().save());
        tag.put("Orders", orders.save());
        tag.putFloat("Sync", getSyncRate());
        net.minecraft.nbt.ListTag history = new net.minecraft.nbt.ListTag();
        for (SyncEntry e : syncHistory) {
            CompoundTag h = new CompoundTag();
            h.putFloat("Delta", e.delta());
            h.putString("Reason", e.reason());
            h.putLong("Time", e.time());
            history.add(h);
        }
        tag.put("SyncHistory", history);
        tag.putFloat("Shield", getShield());
        tag.putInt("Energy", getEnergy());
        tag.putBoolean("Asleep", isAsleep());
        tag.putInt("Kills", kills);
        tag.putLong("Uptime", uptime);
        net.minecraft.nbt.ListTag bossList = new net.minecraft.nbt.ListTag();
        for (String b : bosses) bossList.add(net.minecraft.nbt.StringTag.valueOf(b));
        tag.put("Bosses", bossList);
        tag.putLong("PatDay", patDay);
        tag.put("Life", life().save());
        tag.putInt("PatsToday", patsToday);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        podSleeper = tag.getBoolean("PodSleeper");
        entityData.set(HAIR_RIBBON, tag.getBoolean("HairRibbon"));
        ribbonGifted = tag.getBoolean("RibbonGifted");
        setExpression(tag.getInt("Expression"));
        try {
            entityData.set(MODE, Mode.valueOf(tag.getString("Mode")).ordinal());
        } catch (IllegalArgumentException ignored) {
            entityData.set(MODE, Mode.WAIT.ordinal());
        }
        entityData.set(SHUTDOWN, tag.getBoolean("Shutdown"));
        setOwnerUUID(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        home = tag.contains("Home") ? NbtUtils.readBlockPos(tag.getCompound("Home")) : null;
        if (tag.contains("HomeRadius")) homeRadius = tag.getInt("HomeRadius");
        core = tag.contains("Core") ? ItemStack.of(tag.getCompound("Core")) : ItemStack.EMPTY;
        syncCore();
        // オメガコアの最大 HP を先に付けてから HP を戻す（付ける前の最大 HP で HP が削られないように）
        if (hasOmegaCore()) {
            applyOmega();
            if (tag.contains("Health")) super.setHealth(Math.min(tag.getFloat("Health"), getMaxHealth()));
        }
        try {
            if (tag.contains("Body")) entityData.set(BODY, ValkyrieBody.valueOf(tag.getString("Body")).ordinal());
        } catch (IllegalArgumentException ignored) {
            entityData.set(BODY, ValkyrieBody.V.ordinal());
        }
        com.automatavalkyries.entity.ai.ValkyrieTypeTraits.applyAttributes(this, getBody());
        getInventory().load(tag.getList("Equipment", net.minecraft.nbt.Tag.TAG_COMPOUND));
        orders.load(tag.getCompound("Orders"));
        entityData.set(SYNC, Mth.clamp(tag.getFloat("Sync"), 0.0F, 100.0F));
        syncHistory.clear();
        net.minecraft.nbt.ListTag history = tag.getList("SyncHistory", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < history.size() && i < SYNC_HISTORY; i++) {
            CompoundTag h = history.getCompound(i);
            syncHistory.addLast(new SyncEntry(h.getFloat("Delta"), h.getString("Reason"), h.getLong("Time")));
        }
        // 段階 2 より前の兵姫（記録が無い）は満タンで始める
        entityData.set(SHIELD, tag.contains("Shield") ? Mth.clamp(tag.getFloat("Shield"), 0.0F, getMaxShield()) : getMaxShield());
        setEnergy(tag.contains("Energy") ? tag.getInt("Energy") : maxEnergy());
        entityData.set(ASLEEP, tag.getBoolean("Asleep"));
        kills = tag.getInt("Kills");
        uptime = tag.getLong("Uptime");
        bosses.clear();
        net.minecraft.nbt.ListTag bossList = tag.getList("Bosses", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < bossList.size() && i < MAX_BOSSES; i++) bosses.add(bossList.getString(i));
        patDay = tag.contains("PatDay") ? tag.getLong("PatDay") : -1;
        patsToday = tag.getInt("PatsToday");
        // 前の版で運んでいた物（Carried）は持ち物の拾った物の枠へ
        net.minecraft.nbt.ListTag carry = tag.getList("Carried", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < carry.size(); i++) {
            ItemStack rest = getInventory().addPickup(ItemStack.of(carry.getCompound(i)));
            if (!rest.isEmpty() && !level().isClientSide) spawnAtLocation(rest);
        }
        life().load(tag.getCompound("Life"));
        applyOrders();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 動きは今はすべて ValkyrieModel#setCustomAnimations で計算する（キーフレームのアニメーションは後で足す）
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 持ち主の近くへテレポートする（足場があって体が埋まらない所）。できたら true */
    public boolean teleportNear(LivingEntity target) {
        return teleportNear(target.blockPosition());
    }

    /** その位置の近くへテレポートする（足場があって体が埋まらない所）。できたら true */
    public boolean teleportNear(BlockPos base) {
        for (int i = 0; i < 16; i++) {
            int dx = random.nextInt(5) - 2, dz = random.nextInt(5) - 2;
            if (Math.abs(dx) < 1 && Math.abs(dz) < 1) continue;
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos p = base.offset(dx, dy, dz);
                if (level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP)
                        && level().noCollision(this, getBoundingBox().move(p.getX() + 0.5 - getX(), p.getY() - getY(), p.getZ() + 0.5 - getZ()))) {
                    moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), getXRot());
                    getNavigation().stop();
                    if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.5), getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                    playSound(sound("boost"), 0.7F, 1.2F);
                    return true;
                }
            }
        }
        return false;
    }

    /** 持ち主が次元を移ったとき、追従中なら一緒に移る（{@link ValkyrieGoals#onOwnerChangedDimension}） */
    @Nullable
    public ValkyrieEntity followTo(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel to)) return null;
        Entity moved = changeDimension(to, new net.minecraftforge.common.util.ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                Entity e = repositionEntity.apply(false);
                e.moveTo(owner.getX(), owner.getY(), owner.getZ(), yaw, e.getXRot());
                return e;
            }

            @Override
            public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, java.util.function.Function<ServerLevel, net.minecraft.world.level.portal.PortalInfo> defaultPortalInfo) {
                return new net.minecraft.world.level.portal.PortalInfo(owner.position(), Vec3.ZERO, entity.getYRot(), entity.getXRot());
            }
        });
        return moved instanceof ValkyrieEntity v ? v : null;
    }
}
