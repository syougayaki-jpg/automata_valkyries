package com.automatavalkyries.entity.zero;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.chrono.CausalLock;
import com.automatavalkyries.entity.chrono.ChronoGazer;
import com.automatavalkyries.entity.chrono.LuxionEntity;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.automatavalkyries.entity.chrono.RulerFxEntity;
import com.automatavalkyries.entity.chrono.RulerNeedleEntity;
import com.automatavalkyries.entity.chrono.TimeRulerEntity;
import com.automatavalkyries.entity.chrono.TimeStop;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.weapon.LuminalCoreItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 裏ボス 戦術兵姫零号機【マリスライズ・ロストカタストロフィー】（全体仕様書 §4.4。2026-10-20 ユーザー指定「裏ボスをつくりますか」。
 * 質問で: 時の狭間の奥の専用の次元「零の墓標」で会う・見た目は参考の絵（tools/gen_lost_catastrophe.py、8192 の絵）・詠唱を止められなければ闘技場の全員が消滅・
 * 倒した後に素体を回収して仲間にするまで。数値は【仮】）。ルミナルステラの絶望を背負って暴走した最初の兵姫。全ボスの兵装を融合している。
 * <ul>
 *   <li>HP {@value #MAX_HP}・3 形態（66%・33%）。即死系の守りは付けるが被弾の上限は無し（AEC 級と同じ）。大きさは人と同じくらい（約 2.5 ブロック、形態で変えない）</li>
 *   <li>FE 強制ドレイン: まわり {@value #DRAIN_RANGE} m のプレイヤーの持つルミナルコアと兵姫の電力を毎秒吸い、障壁（{@link #SHIELD_MAX} まで、先にダメージを受ける）に変える</li>
 *   <li>精神ハッキング（第 2 形態から）: 同調率 100% でない兵姫を 10 秒乗っ取り、持ち主へ赤い針を撃たせる（その間、零号機を撃たない）</li>
 *   <li>世界確定消滅詠唱: 第 2・第 3 形態に入るとき 60 秒。その間は一切のダメージ・コマンドを受けない。同調率 100% の兵姫がそばに 3 秒いると
 *       同期過電圧ショックで詠唱が崩れ、10 秒 動けず受けるダメージ 2 倍。止められなければ、この次元のプレイヤー・兵姫が消滅（プレイヤーは倒れ、兵姫は機能停止）し、HP が戻る</li>
 *   <li>融合兵装の技: 光速の斬撃（ルクシオン）・攻城の光線（ガリアフォート）・死蝶の螺旋（メメントモリ）・因果断ちの大剣と時間停止（タイムルーラー）、
 *       自前の黒い特異点（引き寄せて潰す）とデータの雨。いちばん長く使っていない技から順に（タイムルーラーと同じ選び方）</li>
 * </ul>
 * 倒れると零の墓標の帰りの門が開く（{@code world/ZeroGrave.onBossDefeated}）。落とし物は loot_tables/entities/lost_catastrophe（零号機の素体を含む）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class LostCatastropheEntity extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech, com.automatavalkyries.entity.RetreatableBoss {

    public static final float MAX_HP = 120000.0F, SHIELD_MAX = 8000.0F;
    public static final int CHANT_TICKS = 1200, SHOCK_TICKS = 60, STUN_TICKS = 200, INTRO_TICKS = 140, RETREAT_TICKS = 80, DEFEAT_TICKS = 160, HACK_TICKS = 200;
    public static final double DRAIN_RANGE = 24.0;
    /** 描く大きさの倍率と、胸の高さ（ブロック） */
    public static final float SIZE = 1.25F;
    /** 頭の大きさの倍率（描く側の LostCatastropheModel が頭の骨に掛ける）。2026-10-21 から頭も OBJ の面（ユーザー指定「顔を同じように変換して」）で
     *  体と同じ縮尺なので 1（箱の YSM 風の頭に戻すときは 0.8 で約 6 頭身） */
    public static final float HEAD_SCALE = 1.0F;
    /** 頭上の光輪の中心（geo の px）・半径（px）・傾き（geo で Rz(HALO_AZ)・Rx(HALO_T)）。tools/lc_objmesh.py の HALO_C・HALO_R・HALO_TILT */
    static final double HALO_X = 0.0, HALO_Y = 34.9, HALO_Z = 0.6, HALO_R = 3.6, HALO_T = -0.25, HALO_AZ = 0.0;
    static final double CHEST = 1.8;
    public static final float CHEST_H = (float) CHEST;
    public static final float SLASH = 30.0F, BEAM = 36.0F, NEEDLE = 10.0F, RAIN = 12.0F, HOLE_TICK = 4.0F, HOLE_CRUSH = 30.0F;
    public static final ResourceKey<DamageType> ZERO_ERASURE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "zero_erasure"));
    static final int RED = 0xFF2A44, MAGENTA = 0xFF2A9A, VIOLET = 0xB060FF, WHITE = 0xFFF0F0, DARK = 0x200010;
    /** 描く側の技の構え（エンティティの出来事 90〜） */
    public static final byte EV_SLASH = 90, EV_BEAM = 91, EV_SPIRAL = 92, EV_BLADE = 93, EV_RAIN = 94, EV_HOLE = 95, EV_HACK = 96, EV_STOP = 97, EV_SHOCK = 98, EV_INTRO = 99;
    /** 演出の画面（client/ClientZeroShow。2026-10-26）: 世界確定消滅・撤退・着弾の軽い揺れ・重い揺れ */
    public static final byte EV_ERASE = 100, EV_RETREAT = 102, EV_IMPACT = 103, EV_QUAKE = 104;
    /** 終末の技（2026-10-26 ユーザー指定「終末らしいオリジナルの技も追加して」。最終形態だけ）: 零落星・事象の地平・零号の葬送曲・終幕カタストロフィレクイエム */
    public static final byte EV_ZEROFALL = 105, EV_HORIZON = 106, EV_ECHOES = 107, EV_FINALE = 108;
    public static final float FALL = 26.0F, HORIZON_TICK = 6.0F, REQUIEM_HIT = 24.0F;
    /** 事象の地平: 縮む Tick・保つ Tick・初めと終わりの半径 */
    public static final int HORIZON_SHRINK = 160, HORIZON_HOLD = 40;
    public static final float HORIZON_FROM = 30.0F, HORIZON_TO = 6.0F;
    /** 葬送曲の残像が出ている Tick */
    public static final int ECHO_TICKS = 90;
    /**
     * 終幕（声なき葬送）: 前奏・足場の楽章（2 つ）の長さ・終楽章の長さ・終楽章で確定の一撃が落ちる Tick・終わって力尽きている Tick・足場の半径・
     * 終楽章で残す HP の割合（2026-10-26 ユーザー指定「最終楽章で確定９割ダメージで（後隙で削りきる想定）」）
     */
    public static final int FINALE_LEAD = 40, MOVEMENT_TICKS = 130, FINAL_MOVEMENT_TICKS = 140, FINAL_BLOW = 100,
            FINALE_TICKS = FINALE_LEAD + MOVEMENT_TICKS * 2 + FINAL_MOVEMENT_TICKS, FINALE_STUN = 300;
    public static final double PLATFORM_R = 2.6;
    public static final float FINAL_BLOW_LEFT = 0.1F;
    public static final byte EV_REQUIEM_BLOW = 109;
    /**
     * 赤黒い天災の技（2026-10-26 ユーザー指定「融合兵装以外にも終末モチーフに自作の赤黒い雷津波嵐炎で通常技を四つと第二形態に２種、第三に２種」）:
     * 落雷の閃き・大波の揺れ・赫焉天墜の着弾
     */
    public static final byte EV_THUNDER = 110, EV_TIDE = 111, EV_HEAVENFALL = 112;
    public static final float THUNDER = 22.0F, TIDE = 26.0F, STORM_TICK = 6.0F, FLAME = 24.0F, BURN_TICK = 4.0F, CROSS = 20.0F, DELUGE = 24.0F, HEAVENFALL = 40.0F;
    /** 赫雷の予告の Tick と半径 */
    public static final int BOLT_WARN = 16;
    public static final double BOLT_R = 2.2;
    /** 終の大波: 予告の Tick・押し寄せる Tick（1 Tick に 1 m）・半分の幅・切れ目の半分の幅・高さ */
    public static final int TIDE_WARN = 30, TIDE_LEN = 36;
    public static final double TIDE_HALF = 13.0, TIDE_GAP = 2.0, TIDE_H = 4.0;
    /** 禍つ嵐: 続く Tick・削る半径・引き寄せる半径・追う速さ */
    public static final int STORM_LEN = 120;
    public static final double STORM_R = 3.5, STORM_PULL = 8.0, STORM_SPEED = 0.16;
    /** 煉獄焔: 予告の Tick・扇の半径・扇の開き（ラジアン）・焼けた床が残る Tick */
    public static final int FLAME_WARN = 24, BURN_LEN = 60;
    public static final double FLAME_R = 18.0, FLAME_W = 1.25;
    /** 黒雷の十字架: 予告の Tick・回る Tick・腕の長さ・1 Tick に回る角度 */
    public static final int CROSS_WARN = 30, CROSS_SPIN = 90;
    public static final double CROSS_LEN = 24.0, CROSS_TURN = 0.03;
    /** 焔の大海嘯: 予告の Tick・波の数・波の間・1 Tick に広がる m・届く半径 */
    public static final int DELUGE_WARN = 24, DELUGE_WAVES = 3, DELUGE_GAP = 18;
    public static final double DELUGE_SPEED = 0.75, DELUGE_MAX = 30.0;
    /** 黙示の大嵐: 続く Tick・始めと終わりの半径 */
    public static final int TEMPEST_LEN = 160;
    public static final double TEMPEST_FROM = 22.0, TEMPEST_TO = 4.0;
    /** 赫焉天墜: 予告の Tick・半径 */
    public static final int HEAVEN_WARN = 40;
    public static final double HEAVEN_R = 7.0;
    static final int EMBER = 0xFF5A1E;

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CHANT = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STUN = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SHIELD = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SHOCK = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.INT);
    /** 第 2 形態から空に開く裂け目と浮かぶ残骸の中心（2026-10-20 ユーザー指定「裂け目と残骸を形態変化にとっておく」「第 2 形態からずっと」） */
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> RIFT = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** 終末の技（2026-10-26）の中心（闘技場の真ん中）・事象の地平の半径（0 なら無い）・終幕カタストロフィレクイエムの経過 Tick（0 なら無い） */
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> FIELD = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Float> HORIZON = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FINALE = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.INT);

    /** 乗っ取った兵姫 → 解ける時刻（サーバーの Tick） */
    static final Map<UUID, Long> HACKED = new HashMap<>();

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent;
    private final com.automatavalkyries.entity.BossRetreatWatch retreatWatch = new com.automatavalkyries.entity.BossRetreatWatch(64.0, 200);

    /** 描く側: 技ごとの構えの時刻（EV_* - 90） */
    public final int[] animTick = new int[24];
    /** 描く側: 葬送曲の残像が現れた所（現れた時の向きで決めて、そのまま置いておく） */
    public final Vec3[] echoAt = new Vec3[4];
    public float echoYaw;
    public int introTick = -10000;

    private boolean introDone, lootDropped, chant2Done, chant3Done, retreating, trialBoss, finaleDone;
    private int horizonLeft;
    private float trialAttack = 1.0F;
    private int introTicks, retreatTicks, castLock, gap;
    @Nullable
    private Vec3 home;

    private record Strike(int kind, Vec3 at, int fire, @Nullable Entity follow, double extra, double aux, double aux2) {
        Strike(int kind, Vec3 at, int fire, @Nullable Entity follow, double extra) {
            this(kind, at, fire, follow, extra, 0, 0);
        }

        Strike later(int fire) {
            return new Strike(kind, at, fire, follow, extra, aux, aux2);
        }
    }

    private static final int DASH_AIM = 0, DASH_GO = 1, BEAM_FIRE = 2, SPIRAL_WAVE = 3, BLADE_FALL = 4, RAIN_SPAWN = 5, RAIN_FALL = 6, STOP_BEGIN = 7, BEAM_CHARGE = 8,
            METEOR_MARK = 9, METEOR_HIT = 10, ECHO_SLASH = 11, ECHO_SPIRAL = 12,
            BOLT_MARK = 13, BOLT_HIT = 14, TIDE_STEP = 15, TWISTER = 16, FLAME_ERUPT = 17, BURN_CONE = 18, CROSS_SWEEP = 19, RING_WAVE = 20,
            SPIRAL_TWISTER = 21, HEAVEN_CHARGE = 22, HEAVEN_FALL = 23, BURN = 24;
    private final List<Strike> strikes = new ArrayList<>();
    /** 黒い特異点（真ん中・残り Tick） */
    private Vec3 holeAt = Vec3.ZERO;
    private int holeLeft;

    public LostCatastropheEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new HoverMoveControl(this);
        this.bossEvent = new ServerBossEvent(Component.translatable(type.getDescriptionId()), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
        this.xpReward = 3000;
        setNoGravity(true);
        setPersistenceRequired();
        for (int i = 0; i < animTick.length; i++) animTick[i] = -1000;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 18.0)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    /** 零の墓標の闘技場などに呼ぶ（出現の演出から始まる）。作れなければ null */
    @Nullable
    public static LostCatastropheEntity summonAt(ServerLevel level, Vec3 pos) {
        LostCatastropheEntity boss = ModEntities.LOST_CATASTROPHE.get().create(level);
        if (boss == null) return null;
        boss.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        boss.home = pos;
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(boss);
        return boss;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ZeroGoal(this));
        ChronoGazer.addTargets(this, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, (byte) 1);
        entityData.define(DEFEATED, false);
        entityData.define(CHANT, 0);
        entityData.define(STUN, 0);
        entityData.define(SHIELD, 0.0F);
        entityData.define(SHOCK, 0);
        entityData.define(RIFT, java.util.Optional.empty());
        entityData.define(FIELD, java.util.Optional.empty());
        entityData.define(HORIZON, 0.0F);
        entityData.define(FINALE, 0);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public boolean isDefeated() {
        return entityData.get(DEFEATED);
    }

    /** 詠唱の残り Tick（0 なら詠唱していない） */
    public int chantLeft() {
        return entityData.get(CHANT);
    }

    public int stunLeft() {
        return entityData.get(STUN);
    }

    public float shield() {
        return entityData.get(SHIELD);
    }

    /** 裂け目と残骸の中心（第 2 形態から。無ければ null） */
    @Nullable
    public BlockPos riftAnchor() {
        return entityData.get(RIFT).orElse(null);
    }

    /** 終末の技の中心（闘技場の真ん中。無ければ null） */
    @Nullable
    public BlockPos fieldCenter() {
        return entityData.get(FIELD).orElse(null);
    }

    /** 事象の地平の半径（0 なら無い） */
    public float horizonRadius() {
        return entityData.get(HORIZON);
    }

    /** 終幕カタストロフィレクイエムの経過 Tick（0 なら無い） */
    public int finaleTicks() {
        return entityData.get(FINALE);
    }

    /** 終幕の楽章（0・1 は足場の楽章、2 は終楽章。前奏・後は -1） */
    public static int movement(int finale) {
        int s = finale - FINALE_LEAD;
        if (s < 0) return -1;
        if (s < MOVEMENT_TICKS * 2) return s / MOVEMENT_TICKS;
        return s < MOVEMENT_TICKS * 2 + FINAL_MOVEMENT_TICKS ? 2 : -1;
    }

    /** 楽章が始まってからの Tick */
    public static int movementTick(int finale) {
        int m = movement(finale);
        return m < 0 ? 0 : finale - FINALE_LEAD - MOVEMENT_TICKS * m;
    }

    /** 終幕の楽章ごとの安全な足場の数（楽章が進むほど減る。終楽章は足場が無い） */
    public static int platformCount(int movement) {
        return movement < 0 || movement >= 2 ? 0 : 6 - movement;
    }

    /** 終幕の安全な足場の真ん中（描く側も同じ所を出す） */
    public static Vec3 platform(BlockPos center, int movement, int i) {
        int n = platformCount(movement);
        double r = 9.0 + movement * 2.5, a = movement * 0.9 + 0.35 + Math.PI * 2 * i / n;
        return Vec3.atBottomCenterOf(center).add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    /** 描く側: 近くのプレイヤーを目で追う頭の向き（体の向きとの差、度。LostCatastropheModel がなめらかに動かす） */
    public float lookYaw, lookPitch;
    /** 描く側だけで使う: 面頬の下り具合（0 = 額の上へ上げている、1 = 目の前へ下ろしている。2026-10-21） */
    public float visorDown;
    /** 描く側だけで使う: 虹彩の視線のバネ（位置・速さ）と、最後に進めた時刻（2026-10-21） */
    public float irisX, irisY, irisVX, irisVY, eyeAge;

    /** 描く側: 形態が変わった Tick（裂け目と残骸が現れる演出に使う） */
    public int phaseChangedTick = -10000;

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key) && level().isClientSide && tickCount > 0) {                    // 見え始めた時の同期（読み直し）では演出しない
            phaseChangedTick = tickCount;
            int to = phase();
            if (to >= 2) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                com.automatavalkyries.client.ClientZeroShow.phase(this, to);                     // 形態の帯・赤い閃光・グリッチ・揺れ
                com.automatavalkyries.client.ClientZeroShow.flash(this, 14, to >= 3 ? RED : VIOLET);
                com.automatavalkyries.client.ClientZeroShow.glitch(this, 30, 0.7F);
                com.automatavalkyries.client.ClientZeroShow.shake(this, 0.6F);
            });
        }
    }

    /** 同期過電圧ショックの溜まり（0〜{@value #SHOCK_TICKS}） */
    public int shockCharge() {
        return entityData.get(SHOCK);
    }

    int cd(int ticks) {
        return switch (phase()) {
            case 3 -> ticks * 6 / 10;
            case 2 -> ticks * 8 / 10;
            default -> ticks;
        };
    }

    float damageFor(LivingEntity target, float damage) {
        return LuxionEntity.scaledFor(target, damage * trialAttack);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= 90 && id < 90 + animTick.length) {
            animTick[id - 90] = tickCount;
            if (id == EV_INTRO) introTick = tickCount;
            if (id == EV_ECHOES) {
                echoYaw = yBodyRot;
                for (int i = 0; i < 4; i++) echoAt[i] = echoPos(position(), yBodyRot, i);
            }
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> showOnScreen(id));
            return;
        }
        super.handleEntityEvent(id);
    }

    /** 出来事に合わせた画面の演出（描く側だけ。client/ClientZeroShow） */
    private void showOnScreen(byte id) {
        switch (id) {
            case EV_INTRO -> {
                com.automatavalkyries.client.ClientZeroShow.intro(this, 100);                    // 題字は 100 Tick の閃光と一緒に
                com.automatavalkyries.client.ClientZeroShow.glitch(this, 24, 0.6F);
            }
            case EV_SHOCK -> {
                com.automatavalkyries.client.ClientZeroShow.crack(this);
                com.automatavalkyries.client.ClientZeroShow.shake(this, 0.7F);
            }
            case EV_ERASE -> com.automatavalkyries.client.ClientZeroShow.erase(this);
            case EV_RETREAT -> com.automatavalkyries.client.ClientZeroShow.glitch(this, 30, 0.45F);
            case EV_HACK -> com.automatavalkyries.client.ClientZeroShow.glitch(this, 14, 0.5F);
            case EV_STOP -> com.automatavalkyries.client.ClientZeroShow.flash(this, 6, RED);
            case EV_IMPACT -> com.automatavalkyries.client.ClientZeroShow.shake(this, 0.35F);
            case EV_QUAKE -> {
                com.automatavalkyries.client.ClientZeroShow.shake(this, 0.8F);
                com.automatavalkyries.client.ClientZeroShow.glitch(this, 8, 0.3F);
            }
            case EV_ZEROFALL -> com.automatavalkyries.client.ClientZeroShow.flash(this, 8, RED);
            case EV_HORIZON, EV_ECHOES -> com.automatavalkyries.client.ClientZeroShow.glitch(this, 16, 0.5F);
            case EV_FINALE -> {
                com.automatavalkyries.client.ClientZeroShow.finale(this);                    // 静まり返る（声なき葬送）
                com.automatavalkyries.client.ClientZeroShow.shake(this, 0.5F);
            }
            case EV_REQUIEM_BLOW -> com.automatavalkyries.client.ClientZeroShow.blow(this);
            case EV_THUNDER -> {                                                                 // 赤黒い落雷の閃き
                com.automatavalkyries.client.ClientZeroShow.flash(this, 3, RED);
                com.automatavalkyries.client.ClientZeroShow.shake(this, 0.25F);
            }
            case EV_TIDE -> {
                com.automatavalkyries.client.ClientZeroShow.shake(this, 0.5F);
                com.automatavalkyries.client.ClientZeroShow.glitch(this, 10, 0.3F);
            }
            case EV_HEAVENFALL -> {
                com.automatavalkyries.client.ClientZeroShow.flash(this, 12, EMBER);
                com.automatavalkyries.client.ClientZeroShow.shake(this, 1.0F);
                com.automatavalkyries.client.ClientZeroShow.glitch(this, 12, 0.5F);
            }
            default -> {
            }
        }
    }

    void say(String key, ChatFormatting... style) {
        if (!(level() instanceof ServerLevel server)) return;
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 128 * 128)) {
            p.displayClientMessage(Component.translatable("entity.automata_valkyries.lost_catastrophe." + key).withStyle(style), true);
        }
    }

    void speak(String key, ChatFormatting... style) {
        if (!(level() instanceof ServerLevel server)) return;
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 128 * 128)) {
            p.sendSystemMessage(Component.translatable("entity.automata_valkyries.lost_catastrophe." + key).withStyle(style));
        }
    }

    // ------------------------------------------------------------------ 毎 Tick

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            protect.clientTick();
            if (!isDefeated()) clientFx();
            return;
        }
        protect.tick();
        float ratio = protect.healthRatio();
        bossEvent.setProgress(isDefeated() || deathTime > 0 ? 0.0F : ratio);
        if (!isAlive() || isDefeated()) return;
        if (phase() == 1 && ratio <= 2.0F / 3.0F) enterPhase(2);
        else if (phase() == 2 && ratio <= 1.0F / 3.0F) enterPhase(3);
        else if (phase() == 3 && ratio <= 0.1F && !finaleDone && finaleTicks() == 0 && chantLeft() == 0 && introDone && !retreating) startFinale();
        if (!trialBoss && !retreating && retreatWatch.tick(this)) beginRetreat();
    }

    /** まとわりつく赤と紫の光の粒、頭上の光輪ににじむ桃色と紫の光 */
    void clientFx() {
        if (chantLeft() > 0 || finaleTicks() > 0) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.automatavalkyries.client.ClientZeroShow.chanting(this));      // 詠唱・終幕の画面（縁が赤く沈む・最後の秒読み・楽章の名）
        if (finaleTicks() > 0 && random.nextInt(2) == 0) {                                // 終幕: 闘技場じゅうに白と灰の灰が静かに降る（声なき葬送）
            double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * 26.0;
            level().addParticle(MementoMoriEntity.glow(random.nextBoolean() ? WHITE : 0x9A9AA8, 0.08F + random.nextFloat() * 0.06F, 80),
                    getX() + Math.cos(a) * d, getY() + 4.0 + random.nextDouble() * 8.0, getZ() + Math.sin(a) * d, random.nextGaussian() * 0.01, -0.04, random.nextGaussian() * 0.01);
        }
        int since = tickCount - introTick, gone = tickCount - animTick[EV_RETREAT - 90];
        if (since >= 0 && since < 55 || gone >= 0 && gone < RETREAT_TICKS + 20 && gone > 50) return;   // 体が見えない間はまとわりつく光も出さない
        float s = renderScale() / 16.0F;                                                  // モデルの 1 px のブロック数
        if (random.nextInt(3) == 0) {
            int[] colors = {RED, MAGENTA, VIOLET, WHITE};
            double a = random.nextDouble() * Math.PI * 2, r = (0.4 + random.nextDouble() * 0.9);
            level().addParticle(MementoMoriEntity.glow(colors[random.nextInt(colors.length)], 0.06F + random.nextFloat() * 0.05F, 30),
                    getX() + Math.cos(a) * r, getY() + random.nextDouble() * 2.6, getZ() + Math.sin(a) * r, 0, 0.01, 0);
        }
        double yaw = Math.toRadians(yBodyRot);
        if (random.nextInt(2) == 0) {                                                     // 光輪のにじみ（2026-10-20 ユーザー指定: 拡大図どおり、黒い輪に桃色と紫の光）
            double a = random.nextDouble() * Math.PI * 2;                               // 頭上の傾いた光輪の上の点（輪の面で円を描き、傾けて中心へ）
            double x0 = HALO_R * Math.cos(a), z0 = HALO_R * Math.sin(a), st = Math.sin(HALO_T), ct = Math.cos(HALO_T);
            double sa = Math.sin(HALO_AZ), ca = Math.cos(HALO_AZ);
            double gx = x0 * ca + z0 * st * sa, gy = x0 * sa - z0 * st * ca, gz = z0 * ct;
            Vec3 p = local((HALO_X + gx) * s, (HALO_Y + gy) * s, (HALO_Z + gz) * s, yaw);
            level().addParticle(MementoMoriEntity.glow(random.nextBoolean() ? 0xFF6AB0 : VIOLET, 0.05F + random.nextFloat() * 0.04F, 16), p.x, p.y, p.z, 0, 0.004, 0);
        }
        // 右手の黒い渦の光の粒は、渦を外したのでやめた（2026-10-21 ユーザー指定「剣も渦もなくして」）
    }

    /** モデルの中の位置（ブロック。体の向きで回す）を世界の位置へ */
    Vec3 local(double x, double y, double z, double yaw) {
        // GeckoLib は geo の X を返して描き、描く側は (180 - 向き) 度回す: 向き 0（南）で geo の (x, z) は世界の (x, -z)
        return position().add(x * Math.cos(yaw) + z * Math.sin(yaw), y, x * Math.sin(yaw) - z * Math.cos(yaw));
    }

    /** 描く大きさ（2026-10-20 ユーザー指定「人と同じくらい」・形態で大きさは変えない: 約 32 px の形を 1.25 倍で約 2.5 ブロック） */
    public float renderScale() {
        return SIZE;
    }

    void enterPhase(int to) {
        entityData.set(PHASE, (byte) to);
        if (riftAnchor() == null) entityData.set(RIFT, java.util.Optional.of(BlockPos.containing(home != null ? home : position())));
        bossEvent.setColor(to == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.PINK);
        // 形態の名は画面の帯で出す（client/ClientZeroShow.phase。2026-10-26 演出の改良でアクションバーから移した）
        playSound(SoundEvents.WITHER_SPAWN, 4.0F, to == 2 ? 0.7F : 0.5F);
        playSound(SoundEvents.GLASS_BREAK, 4.0F, 0.4F);
        if (level() instanceof ServerLevel server) {                                       // 怨嗟が噴き出す: 赤と紫のデータの欠片・衝撃波・光の柱・空の裂け目へ昇る稲妻
            Vec3 c = position().add(0, CHEST, 0);
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.62), getZ(), 1, 0, 0, 0, 0);
            Fx.shockwave(server, position().add(0, 1.0, 0), RED, 18.0F, 30);
            Fx.shockwave(server, c, to >= 3 ? MAGENTA : VIOLET, 26.0F, 36);
            Fx.burst(server, MementoMoriEntity.glow(RED, 0.4F, 26), c, 50, 0.4);
            Fx.burst(server, MementoMoriEntity.glow(to >= 3 ? WHITE : VIOLET, 0.3F, 22), c, 30, 0.25);
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, position(), 3.0F, 0, 0, 40, to >= 3 ? RED : VIOLET);
            for (int i = 0; i < 4; i++) {
                double a = Math.PI * 2 * i / 4 + random.nextDouble() * 0.5;
                Vec3 to2 = c.add(Math.cos(a) * 14.0, 18.0, Math.sin(a) * 14.0);
                Fx.line(server, MementoMoriEntity.glow(RED, 0.45F, 14), c, to2, 0.5);
            }
            playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 0.5F);
        }
        if ((to == 2 && !chant2Done) || (to == 3 && !chant3Done)) {
            if (to == 2) chant2Done = true;
            else chant3Done = true;
            startChant();
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        faceTarget();
        if (!(level() instanceof ServerLevel server)) return;
        if (!introDone) {
            tickIntro(server);
            return;
        }
        if (retreating) {
            tickRetreat(server);
            return;
        }
        if (isDefeated() || !isAlive()) {
            strikes.clear();
            holeLeft = 0;
            return;
        }
        if (castLock > 0) castLock--;
        if (gap > 0) gap--;
        int stun = stunLeft();
        if (stun > 0) {
            entityData.set(STUN, stun - 1);
            if (stun % 10 == 0) Fx.spray(server, MementoMoriEntity.glow(0x7FE4FF, 0.25F, 10), position().add(0, CHEST, 0), 8, 1.2, 0.05);
        }
        if (tickCount % 20 == 0) drainFe(server);
        tickFinale(server);
        tickHorizon(server);
        tickChant(server);
        tickHole(server);
        tickHacks(server);
        tickStrikes(server);
    }

    static final float TURN = 12.0F;

    void faceTarget() {
        if (level().isClientSide || isDefeated()) return;
        LivingEntity t = getTarget();
        if (t == null) return;
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0e-4) return;
        float want = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float yaw = net.minecraft.util.Mth.approachDegrees(getYRot(), want, TURN);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    static class HoverMoveControl extends MoveControl {
        HoverMoveControl(LostCatastropheEntity mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO) {
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.8));
                return;
            }
            Vec3 to = new Vec3(wantedX, wantedY, wantedZ).subtract(mob.position());
            double d = to.length();
            if (d < 0.6) {
                operation = Operation.WAIT;
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.6));
                return;
            }
            Vec3 want = to.scale(Math.min(0.34 * speedModifier, d * 0.08) / d);
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.add(want.subtract(v).scale(0.15)));
            mob.setSpeed(0.0F);
        }
    }

    // ---- 出現・退く ----

    /**
     * 出現（{@value #INTRO_TICKS} Tick、受けない。2026-10-26 ユーザー指定「演出を改良する」でタイムルーラー並みに作り直し）:
     * 0 真上の空に赤黒い裂け目が開き、床に逆回りの赤い文字盤と紫の魔法陣。0〜60 裂け目から赤と紫のデータの欠片が降り注いで集まり、赤い稲妻が 3 度落ちる。
     * 50〜72 体がちらつきながら組み上がる（描く側）。70 光輪が灯る。85 翼が開く。100 赤い閃光・衝撃波・光の柱・画面に題字（グリッチで組み上がる）と名乗り、
     * 抱えた腕を広げて構える（描く側）。100〜130 裂け目が閉じる
     */
    void tickIntro(ServerLevel server) {
        int t = introTicks++;
        Vec3 c = position().add(0, CHEST, 0), sky = position().add(0, 10.0, 0);
        if (t == 0) {
            level().broadcastEntityEvent(this, EV_INTRO);
            if (home == null) home = position();
            playSound(SoundEvents.WITHER_SPAWN, 5.0F, 0.4F);
            playSound(SoundEvents.PORTAL_TRIGGER, 4.0F, 0.5F);
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_REVERSE, position().add(0, 0.1, 0), 12.0F, 0, 0, INTRO_TICKS, RED);
            Fx.mandala(server, position().add(0, 0.15, 0), VIOLET, 7.0F, INTRO_TICKS - 20, false, true);
        }
        if (t < 60 && t % 2 == 0) {                                                      // 裂け目から降り注ぐデータの欠片
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 2.5;
                Vec3 from = sky.add(Math.cos(a) * r, random.nextGaussian() * 0.8, Math.sin(a) * r);
                int col = switch (random.nextInt(3)) {
                    case 0 -> RED;
                    case 1 -> VIOLET;
                    default -> WHITE;
                };
                Fx.one(server, MementoMoriEntity.glow(col, 0.18F + random.nextFloat() * 0.2F, 16), from, c.subtract(from).scale(1.0 / 16));
            }
        }
        if (t < 70 && t % 3 == 0) Fx.gather(server, MementoMoriEntity.glow(t % 6 == 0 ? RED : VIOLET, 0.35F, 14), c, 6, 6.0 - t * 0.05, 14);
        if (t == 20 || t == 35 || t == 50) {                                             // 赤い稲妻
            Vec3 from = sky.add(random.nextGaussian() * 1.5, 0, random.nextGaussian() * 1.5);
            Vec3 mid = from.add(c).scale(0.5).add(random.nextGaussian() * 1.2, 0, random.nextGaussian() * 1.2);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.5F, 10), from, mid, 0.3);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.5F, 10), mid, c, 0.3);
            Fx.line(server, MementoMoriEntity.glow(WHITE, 0.25F, 8), from, c, 0.5);
            playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.5F, 0.6F + t * 0.01F);
            level().broadcastEntityEvent(this, EV_IMPACT);
        }
        if (t % 12 == 0 && t < 96) playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.4F + t * 0.009F);
        if (t >= 50 && t < 72 && t % 3 == 0) {                                           // 体が組み上がる: 体のあちこちで白と赤の欠片が弾ける
            Fx.burst(server, MementoMoriEntity.glow(t % 2 == 0 ? WHITE : RED, 0.2F, 10), position().add(random.nextGaussian() * 0.4, 0.3 + random.nextDouble() * 2.3, random.nextGaussian() * 0.4), 6, 0.08);
            if (t % 6 == 0) playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.5F, 1.2F + (t - 50) * 0.03F);
        }
        if (t == 70) {                                                                   // 光輪が灯る
            Vec3 halo = haloPos();
            Fx.burst(server, MementoMoriEntity.glow(0xFF6AB0, 0.3F, 16), halo, 24, 0.12);
            Fx.shockwave(server, halo, MAGENTA, 2.5F, 14);
            playSound(SoundEvents.BELL_RESONATE, 3.0F, 1.2F);
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.6F);
        }
        if (t == 85) {                                                                   // 翼が開く
            double yaw = Math.toRadians(yBodyRot);
            for (int sd = -1; sd <= 1; sd += 2) {
                Vec3 side = new Vec3(Math.cos(yaw) * sd, 0, Math.sin(yaw) * sd);
                for (int i = 0; i < 10; i++) Fx.one(server, MementoMoriEntity.glow(sd > 0 ? RED : VIOLET, 0.25F, 18), c.add(side.scale(0.5)), side.scale(0.25 + random.nextDouble() * 0.2).add(0, random.nextGaussian() * 0.05, 0));
            }
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
        }
        if (t == 100) {
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            Fx.shockwave(server, position().add(0, 0.5, 0), RED, 22.0F, 30);
            Fx.shockwave(server, c, VIOLET, 14.0F, 24);
            Fx.shockwave(server, c, WHITE, 8.0F, 18);
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, position(), 2.2F, 0, 0, 30, RED);
            Fx.burst(server, MementoMoriEntity.glow(RED, 0.4F, 24), c, 40, 0.35);
            playSound(SoundEvents.END_PORTAL_SPAWN, 3.0F, 0.6F);
            playSound(SoundEvents.WITHER_SPAWN, 3.0F, 0.9F);
            level().broadcastEntityEvent(this, EV_QUAKE);
            speak("intro", ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
        }
        if (t == 104) for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 96 * 96)) p.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), getSoundSource(), 1.0F, 0.5F);
        if (t >= INTRO_TICKS) introDone = true;
    }

    /** 頭上の光輪の真ん中（世界の座標） */
    Vec3 haloPos() {
        float s = renderScale() / 16.0F;
        return local(HALO_X * s, HALO_Y * s, HALO_Z * s, Math.toRadians(yBodyRot));
    }

    @Override
    public boolean beginRetreat() {
        if (!isAlive() || isDefeated() || retreating || protect.isDespawning()) return false;
        retreating = true;
        retreatTicks = 0;
        playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.4F);
        level().broadcastEntityEvent(this, EV_RETREAT);
        return true;
    }

    /**
     * 撤退（{@value #RETREAT_TICKS} Tick。2026-10-26 演出の改良）: 0〜30 赤い光が胸へ吸い込まれ、光輪が暗くなる。20〜40 真上に裂け目が開く（描く側）。
     * 25〜55 体がちらつきながらデータの欠片になってほどけ、裂け目へ吸い上げられる。55 小さな閃光で消え、55〜80 裂け目が閉じる
     */
    void tickRetreat(ServerLevel server) {
        int t = retreatTicks++;
        Vec3 c = position().add(0, CHEST, 0), sky = position().add(0, 9.0, 0);
        setDeltaMovement(getDeltaMovement().scale(0.5));
        if (t < 30 && t % 2 == 0) Fx.gather(server, MementoMoriEntity.glow(RED, 0.35F, 12), c, 6, 6.0, 12);
        if (t == 20) playSound(SoundEvents.PORTAL_TRIGGER, 3.0F, 0.7F);
        if (t >= 25 && t < 55 && t % 2 == 0) {                                           // ほどけて裂け目へ吸い上げられる欠片
            for (int i = 0; i < 5; i++) {
                Vec3 from = position().add(random.nextGaussian() * 0.35, 0.2 + random.nextDouble() * 2.4, random.nextGaussian() * 0.35);
                Fx.one(server, MementoMoriEntity.glow(i % 2 == 0 ? RED : VIOLET, 0.2F + random.nextFloat() * 0.15F, 20), from, sky.subtract(from).scale(1.0 / 20));
            }
            if (t % 8 == 0) playSound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.5F, 0.6F + (t - 25) * 0.02F);
        }
        if (t == 55) {
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            Fx.shockwave(server, c, RED, 6.0F, 16);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 3.0F, 0.4F);
        }
        if (t >= RETREAT_TICKS) {
            cleanup(server);
            protect.triggerDespawn();
        }
    }

    // ---- FE 強制ドレイン ----

    /** 毎秒: まわりのプレイヤーの持つルミナルコアと兵姫の電力を吸い、障壁に変える（400 FE で 1。形態で強くなる） */
    public void drainFe(ServerLevel server) {
        int per = phase() == 3 ? 60000 : phase() == 2 ? 40000 : 20000;
        long total = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DRAIN_RANGE), ChronoGazer::canHurt)) {
            int took = 0;
            if (e instanceof Player p) {
                for (int i = 0; i < p.getInventory().getContainerSize() && took < per; i++) {
                    ItemStack st = p.getInventory().getItem(i);
                    if (st.getItem() instanceof LuminalCoreItem core) took += core.extract(st, per - took);
                }
            } else if (e instanceof ValkyrieEntity v && !v.isShutdown()) {
                took = Math.min(per, v.getEnergy());
                v.setEnergy(v.getEnergy() - took);
            }
            if (took > 0) {
                total += took;
                Fx.line(server, MementoMoriEntity.glow(RED, 0.2F, 14), e.position().add(0, e.getBbHeight() * 0.6, 0), position().add(0, CHEST, 0), 0.8);
            }
        }
        if (total > 0) entityData.set(SHIELD, Math.min(SHIELD_MAX, shield() + total / 400.0F));
    }

    // ---- 世界確定消滅詠唱 ----

    /** 世界確定消滅詠唱を始める（形態が変わるとき。GameTest からも呼ぶ） */
    public void startChant() {
        entityData.set(CHANT, CHANT_TICKS);
        entityData.set(SHOCK, 0);
        speak("chant", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.WITHER_AMBIENT, 5.0F, 0.4F);
        if (level() instanceof ServerLevel server) {
            Vec3 c = home != null ? home : position();
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_REVERSE, c.add(0, 0.1, 0), 30.0F, 0, 0, CHANT_TICKS, RED);
        }
    }

    /**
     * 詠唱の間: 一切受けない。同調率 100% の兵姫（乗っ取られていない・動ける）が 10 m 以内にいる間ショックが溜まり、{@value #SHOCK_TICKS} Tick で詠唱が崩れる。
     * 止められなければ消滅
     */
    void tickChant(ServerLevel server) {
        int left = chantLeft();
        if (left <= 0) return;
        if (TimeStop.active()) return;
        left--;
        entityData.set(CHANT, left);
        if (left % 20 == 0) {
            playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 4.0F, 0.5F);
            if (left <= 200) playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 3.0F, 0.5F + (200 - left) / 200.0F);   // 最後の 10 秒は上がっていく鐘
            int secs = left / 20;
            if (secs <= 10 || secs % 10 == 0) {
                for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 128 * 128)) {
                    p.displayClientMessage(Component.translatable("entity.automata_valkyries.lost_catastrophe.chant_count", secs).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                }
            }
        }
        if (tickCount % 4 == 0) Fx.mandala(server, position().add(0, CHEST, 0), RED, 3.5F, 6, true, true);
        int every = left > 400 ? 60 : left > 200 ? 30 : 12;                                 // 空から落ちる赤い稲妻（残りが少ないほど多く）
        if (left % every == 0) {
            Vec3 c = position().add(0, CHEST, 0);
            Vec3 from = c.add(random.nextGaussian() * 10.0, 16.0, random.nextGaussian() * 10.0);
            Vec3 mid = from.add(c).scale(0.5).add(random.nextGaussian() * 2.0, 0, random.nextGaussian() * 2.0);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.45F, 10), from, mid, 0.4);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.45F, 10), mid, c, 0.4);
            if (left <= 200) Fx.shockwave(server, c, RED, 4.0F + (200 - left) * 0.04F, 12);
        }
        if (left <= 200 && left % 4 == 0) {                                                // 最後の 10 秒: 赤いデータの欠片が胸へ吸い込まれ続ける
            Fx.gather(server, MementoMoriEntity.glow(left % 8 == 0 ? RED : WHITE, 0.3F, 12), position().add(0, CHEST, 0), 6, 12.0, 12);
        }
        ValkyrieEntity bonded = null;
        for (ValkyrieEntity v : level().getEntitiesOfClass(ValkyrieEntity.class, getBoundingBox().inflate(10.0),
                v -> !v.isShutdown() && v.getSyncRate() >= 100.0F && !isHacked(v))) {
            bonded = v;
            break;
        }
        int shock = shockCharge();
        if (bonded != null) {
            shock++;
            if (tickCount % 3 == 0) Fx.line(server, MementoMoriEntity.glow(0x7FE4FF, 0.3F, 10), bonded.position().add(0, 1.2, 0), position().add(0, CHEST, 0), 0.5);
            if (shock % 20 == 0) bonded.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.6F);
        } else shock = Math.max(0, shock - 2);
        entityData.set(SHOCK, shock);
        if (shock >= SHOCK_TICKS) {                                                      // 同期過電圧ショック: 詠唱が崩れる
            entityData.set(CHANT, 0);
            entityData.set(SHOCK, 0);
            entityData.set(STUN, STUN_TICKS);
            castLock = STUN_TICKS;
            level().broadcastEntityEvent(this, EV_SHOCK);
            speak("chant_broken", ChatFormatting.AQUA, ChatFormatting.BOLD);
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.62), getZ(), 1, 0, 0, 0, 0);
            Fx.shockwave(server, position().add(0, CHEST, 0), 0x7FE4FF, 14.0F, 24);
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, position(), 2.6F, 0, 0, 30, 0x7FE4FF);
            playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 1.2F);
            playSound(SoundEvents.GLASS_BREAK, 4.0F, 0.5F);
            return;
        }
        if (left == 0) eraseWorld(server);
    }

    /**
     * 詠唱を止められなかった（2026-10-26 ユーザー指定「安心安全にクリエイティブ以外のプレイヤーをキルと戦術兵姫を強制戦闘不能と全エンティティキル位のバニラ即死で」）:
     * 範囲（零の墓標なら次元じゅう、ほかの所は 96 m 以内）の、クリエイティブ・観戦でないプレイヤーはバニラの kill()（/kill）と同じ強さの即死（ダメージの種類は zero_erasure で、キルログは専用）、
     * 戦術兵姫は機能停止（戦闘不能）、ほかの生き物もバニラの kill()。深い仕掛けは使わない（守りを持つ他の MOD の個体は、その守りに任せる）。
     * 防具立て・落ちている物・額縁などの生き物でない物は消さない。零号機の HP と障壁は戻る
     */
    public void eraseWorld(ServerLevel server) {
        level().broadcastEntityEvent(this, EV_ERASE);                                     // 画面: 赤から白へ塗りつぶして「世界確定消滅」
        speak("erased", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.62), getZ(), 1, 0, 0, 0, 0);
        Fx.shockwave(server, position(), WHITE, 60.0F, 40);
        Fx.shockwave(server, position(), RED, 40.0F, 36);
        playSound(SoundEvents.GENERIC_EXPLODE, 6.0F, 0.3F);
        boolean grave = com.automatavalkyries.world.ZeroGrave.isGrave(server);           // 零の墓標なら次元の全員、ほかの所（卵・トライアル）は 96 m 以内
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity e : server.getAllEntities()) {                                       // 先に集める（殺している間に一覧が変わる）
            if (e == this || !(e instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!grave && e.distanceToSqr(this) > 96 * 96) continue;
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            targets.add(living);
        }
        // バニラの kill()（/kill）と同じ強さ（無限のダメージ・無敵／防具／耐性を通す・トーテムも効かない）で、ダメージの種類だけ零号機の世界確定消滅にする。
        // キルログは「○○ は 零号機… に世界ごと零に還された」（2026-10-26 ユーザー指定「維持したまま専用のキルログにして」）
        DamageSource erasure = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ZERO_ERASURE), this);
        for (LivingEntity e : targets) {
            try {
                if (e instanceof ValkyrieEntity v) {
                    if (!v.isShutdown()) v.shutdown();                                   // 兵姫は消さず戦闘不能に
                } else {
                    e.hurt(erasure, Float.MAX_VALUE);
                }
            } catch (RuntimeException ex) {
                AutomataValkyries.LOGGER.warn("[lost_catastrophe] World erasure could not kill {}: {}", e, ex.toString());
            }
        }
        setHealth(getMaxHealth());
        entityData.set(SHIELD, SHIELD_MAX * 0.5F);
    }

    // ---- 精神ハッキング ----

    public static boolean isHacked(Entity e) {
        Long until = HACKED.get(e.getUUID());
        return until != null && e.getServer() != null && e.getServer().getTickCount() < until;
    }

    void mindHack() {
        if (!(level() instanceof ServerLevel server)) return;
        List<ValkyrieEntity> list = level().getEntitiesOfClass(ValkyrieEntity.class, getBoundingBox().inflate(32.0),
                v -> !v.isShutdown() && v.getSyncRate() < 100.0F && !isHacked(v));
        if (list.isEmpty()) return;
        ValkyrieEntity v = list.get(random.nextInt(list.size()));
        HACKED.put(v.getUUID(), (long) server.getServer().getTickCount() + HACK_TICKS);
        v.setTarget(null);
        Fx.line(server, MementoMoriEntity.glow(MAGENTA, 0.3F, 16), position().add(0, CHEST, 0), v.position().add(0, 1.2, 0), 0.4);
        Fx.burst(server, MementoMoriEntity.glow(RED, 0.3F, 16), v.position().add(0, 1.0, 0), 24, 0.2);
        say("hack", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        playSound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 3.0F, 0.6F);
        level().broadcastEntityEvent(this, EV_HACK);
    }

    /** 乗っ取った兵姫: 赤い光をまとい、1.5 秒ごとに持ち主（いなければ近くのプレイヤー）へ赤い針を撃つ */
    void tickHacks(ServerLevel server) {
        if (HACKED.isEmpty()) return;
        HACKED.entrySet().removeIf(en -> server.getServer().getTickCount() >= en.getValue());
        if (tickCount % 30 != 0) return;
        for (UUID id : HACKED.keySet()) {
            if (!(server.getEntity(id) instanceof ValkyrieEntity v) || v.isShutdown()) continue;
            Player aim = v.getOwner();
            if (aim == null || aim.distanceToSqr(v) > 48 * 48 || !ChronoGazer.canHurt(aim)) aim = server.getNearestPlayer(v, 32.0);
            Fx.burst(server, MementoMoriEntity.glow(RED, 0.25F, 12), v.position().add(0, 1.2, 0), 12, 0.15);
            if (aim == null || !ChronoGazer.canHurt(aim)) continue;
            Vec3 from = v.position().add(0, 1.5, 0), to = aim.position().add(0, aim.getBbHeight() * 0.5, 0);
            for (int i = 0; i < 4; i++) RulerNeedleEntity.place(this, from.add(random.nextGaussian() * 0.4, random.nextGaussian() * 0.3, random.nextGaussian() * 0.4), to, 4 + i * 2, NEEDLE * 0.8F * trialAttack, 1.6F);
        }
    }

    /** 乗っ取られた兵姫は、狙いを変えようとしても持ち主（プレイヤー）へ向く */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof ValkyrieEntity v && !v.level().isClientSide && isHacked(v)) event.setNewTarget(v.getOwner());
    }

    // ---- 技 ----

    /** 主な相手のほかに、近くで戦っているプレイヤー・兵姫（最大 5）にも同じ技（タイムルーラーと同じ） */
    List<LivingEntity> otherFoes(LivingEntity main) {
        List<LivingEntity> out = new ArrayList<>(level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48.0),
                e -> e != main && ChronoGazer.canHurt(e) && (e instanceof Player || e instanceof ValkyrieEntity v && !v.isShutdown())));
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(this)));
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    void barrage(LivingEntity main, java.util.function.Consumer<LivingEntity> move) {
        move.accept(main);
        int lock = castLock;
        for (LivingEntity e : otherFoes(main)) move.accept(e);
        castLock = lock;
    }

    /** 光速の斬撃（ルクシオンの融合）: 相手を通る長さ 30 の道に 0.6 秒の赤い予告、一瞬で駆け抜けて斬る（{@value #SLASH}）。3 度（第 3 形態 5 度） */
    void lightSlash(LivingEntity t) {
        int n = phase() == 3 ? 5 : 3;
        for (int i = 0; i < n; i++) strikes.add(new Strike(DASH_AIM, Vec3.ZERO, tickCount + i * 24, t, 0));
        castLock = n * 24 + 6;
        say("slash", ChatFormatting.RED);
    }

    /** 攻城の光線（ガリアフォートの融合）: 1.5 秒溜めて、相手への長さ 44 の赤い光線（{@value #BEAM}、幅 2）。第 3 形態は左右 20 度にもう 2 本 */
    void siegeBeam(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = position().add(0, CHEST, 0);
        double base = Math.atan2(t.getZ() - from.z, t.getX() - from.x);
        double[] angles = phase() == 3 ? new double[]{base, base + 0.35, base - 0.35} : new double[]{base};
        for (double a : angles) {
            RulerFxEntity.spawn(server, RulerFxEntity.LANE, 30, new Vec3(getX(), t.getY() + 0.05, getZ()), 44.0F, (float) a, 0, 30 + 16, RED);
            strikes.add(new Strike(BEAM_FIRE, new Vec3(getX(), t.getY(), getZ()), tickCount + 30, null, a));
        }
        for (int i = 0; i < 5; i++) strikes.add(new Strike(BEAM_CHARGE, Vec3.ZERO, tickCount + i * 6, null, base));   // 溜め: 胸へ光が集まる
        castLock = 36;
        say("beam", ChatFormatting.RED);
        playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
        level().broadcastEntityEvent(this, EV_BEAM);
    }

    /** 死蝶の螺旋（メメントモリの融合）: まわりへ外向きの針を 3 波（1 波 20 本、波ごとに回る。第 3 形態は 5 波） */
    void butterflySpiral() {
        int waves = phase() == 3 ? 5 : 3;
        for (int i = 0; i < waves; i++) strikes.add(new Strike(SPIRAL_WAVE, Vec3.ZERO, tickCount + i * 10, null, i * 0.3));
        castLock = waves * 10;
        say("spiral", ChatFormatting.LIGHT_PURPLE);
        level().broadcastEntityEvent(this, EV_SPIRAL);
        playSound(SoundEvents.PHANTOM_FLAP, 3.0F, 0.6F);
    }

    /** 因果断ちの大剣（タイムルーラーの融合）: 赤い大剣が 4 秒かけて相手を通る線へ倒れ、帯の上の者は因果停止（{@link CausalLock}） */
    void causalBlade(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 pivot = new Vec3(getX(), t.getY(), getZ());
        double yaw = Math.atan2(t.getZ() - pivot.z, t.getX() - pivot.x);
        RulerFxEntity.spawn(server, RulerFxEntity.BLADE, 80, pivot, TimeRulerEntity.BLADE_LEN, (float) yaw, 0, 80 + 40, RED);
        strikes.add(new Strike(BLADE_FALL, pivot, tickCount + 80, null, yaw));
        castLock = 24;
        say("blade", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_RESONATE, 4.0F, 0.3F);
        level().broadcastEntityEvent(this, EV_BLADE);
    }

    /** データの雨: 3 秒の間、相手のまわり半径 11 に赤いデータの柱が降る（0.7 秒の予告、1 つ {@value #RAIN}） */
    void glitchRain(LivingEntity t) {
        for (int i = 0; i < 20; i++) strikes.add(new Strike(RAIN_SPAWN, Vec3.ZERO, tickCount + i * 3, t, 0));
        say("rain", ChatFormatting.RED);
        level().broadcastEntityEvent(this, EV_RAIN);
    }

    /** 黒い特異点（第 2 形態から）: 相手の居る所に 5 秒、まわり 14 m を引き寄せ、真ん中 2.5 m を削り（0.5 秒ごとに {@value #HOLE_TICK}）、最後に潰れる（半径 4・{@value #HOLE_CRUSH}、防具を通す） */
    void blackHole(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        holeAt = t.position().add(0, 1.0, 0);
        holeLeft = 100;
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_FAST | RulerFxEntity.HANDS_REVERSE, t.position().add(0, 0.08, 0), 14.0F, 0, 0, 100, VIOLET);
        castLock = 20;
        say("hole", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        playSound(SoundEvents.PORTAL_TRIGGER, 4.0F, 0.4F);
        Fx.burst(server, MementoMoriEntity.glow(DARK, 0.6F, 20), holeAt, 30, 0.3);                // 開く瞬間: 黒い欠片が弾け、赤い輪
        Fx.shockwave(server, holeAt, RED, 5.0F, 14);
        Fx.line(server, MementoMoriEntity.glow(VIOLET, 0.3F, 12), position().add(0, CHEST, 0), holeAt, 0.5);
        level().broadcastEntityEvent(this, EV_HOLE);
    }

    void tickHole(ServerLevel server) {
        if (holeLeft <= 0 || TimeStop.active()) return;
        holeLeft--;
        Vec3 c = holeAt;
        for (int i = 0; i < 3; i++) {
            double a = tickCount * 0.3 + i * Math.PI * 2 / 3, r = 3.0 + (tickCount % 20) * 0.4;
            Vec3 p = c.add(Math.cos(a) * r, Math.sin(a * 0.5) * 0.8, Math.sin(a) * r);
            Fx.one(server, MementoMoriEntity.glow(i == 0 ? RED : i == 1 ? VIOLET : DARK, 0.4F, 16), p, c.subtract(p).scale(0.08));
        }
        if (holeLeft % 3 == 0) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(14.0), ChronoGazer::canHurt)) {
                Vec3 rel = c.subtract(e.position()).multiply(1, 0, 1);
                double dist = rel.length();
                if (dist < 1.0 || dist > 14.0) continue;
                Vec3 step = rel.scale(0.14 / dist);
                // teleport は座標をそのまま（絶対）で受け取り、相対の印はクライアントへ送る差にだけ効く。引く量だけを渡すと原点のそばへ飛んでいた（2026-11-01「０座標までとばされる」）
                if (e instanceof ServerPlayer p) p.connection.teleport(p.getX() + step.x, p.getY(), p.getZ() + step.z, p.getYRot(), p.getXRot(), java.util.EnumSet.allOf(net.minecraft.world.entity.RelativeMovement.class));
                else e.push(step.x, 0, step.z);
            }
        }
        if (holeLeft % 10 == 0) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(2.5), ChronoGazer::canHurt)) {
                e.hurt(damageSources().mobAttack(this), damageFor(e, HOLE_TICK));
            }
        }
        if (holeLeft == 0) {                                                             // 潰れる
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            Fx.shockwave(server, c, VIOLET, 10.0F, 24);
            Fx.shockwave(server, c, RED, 6.0F, 18);
            Fx.burst(server, MementoMoriEntity.glow(WHITE, 0.35F, 16), c, 30, 0.45);
            hitAround(c, 4.0, HOLE_CRUSH, true);
            server.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 4.0F, 0.4F);
            level().broadcastEntityEvent(this, EV_QUAKE);
        }
    }

    /** 時間停止（タイムルーラーの融合。第 2 形態から）: 1.5 秒の予告の後、世界を 5 秒止め、止まった者のまわりに赤い針の輪 */
    void timeStop() {
        strikes.add(new Strike(STOP_BEGIN, Vec3.ZERO, tickCount + 30, null, 0));
        castLock = 40;
        say("stop", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (int i = 0; i < 3; i++) playSound(SoundEvents.BELL_BLOCK, 4.0F, 0.4F);
        level().broadcastEntityEvent(this, EV_STOP);
    }

    // ---- 終末の技（2026-10-26 ユーザー指定「終末らしいオリジナルの技も追加して」。質問で: 零落星・事象の地平・零号の葬送曲・カタストロフィレクイエム、最終形態だけ） ----

    /** 闘技場の真ん中（出現した所の真下の床）を終末の技の中心にする */
    BlockPos ensureField() {
        BlockPos c = fieldCenter();
        if (c != null) return c;
        Vec3 h = home != null ? home : position();
        BlockPos.MutableBlockPos p = BlockPos.containing(h).mutable();
        for (int i = 0; i < 16 && level().getBlockState(p.below()).isAir(); i++) p.move(0, -1, 0);
        c = p.immutable();
        entityData.set(FIELD, java.util.Optional.of(c));
        return c;
    }

    /**
     * 零落星（ゼロ・フォール）: 空の裂け目から浮かぶ残骸が燃える星になって降り注ぐ。4 秒の間、3 Tick ごとに 1 つ、赤い予告の円（1.1 秒）の所へ落ちる
     * （半径 2.8・{@value #FALL}）。半分は相手のまわり、残りは闘技場じゅう
     */
    void zeroFall(LivingEntity t) {
        if (!(level() instanceof ServerLevel)) return;
        BlockPos c = ensureField();
        for (int i = 0; i < 26; i++) strikes.add(new Strike(METEOR_MARK, Vec3.atBottomCenterOf(c), tickCount + i * 3, t, 0));
        castLock = 30;
        say("zerofall", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.WITHER_SHOOT, 4.0F, 0.4F);
        level().broadcastEntityEvent(this, EV_ZEROFALL);
    }

    /**
     * 事象の地平: 闘技場の縁から黒い虚無の輪が {@value #HORIZON_SHRINK} Tick かけて半径 {@value #HORIZON_FROM} から {@value #HORIZON_TO} へ縮み、
     * {@value #HORIZON_HOLD} Tick 保つ。輪の外は 0.5 秒ごとに {@value #HORIZON_TICK}（防具を通す）。零号機は真ん中の上に留まる
     */
    void horizon() {
        ensureField();
        horizonLeft = HORIZON_SHRINK + HORIZON_HOLD;
        entityData.set(HORIZON, HORIZON_FROM);
        castLock = 30;
        say("horizon", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        playSound(SoundEvents.END_PORTAL_SPAWN, 4.0F, 0.3F);
        playSound(SoundEvents.WARDEN_EMERGE, 3.0F, 0.6F);
        level().broadcastEntityEvent(this, EV_HORIZON);
    }

    void tickHorizon(ServerLevel server) {
        if (horizonLeft <= 0 || TimeStop.active()) return;
        horizonLeft--;
        int t = HORIZON_SHRINK + HORIZON_HOLD - horizonLeft;
        float r = HORIZON_FROM - (HORIZON_FROM - HORIZON_TO) * Math.min(1.0F, t / (float) HORIZON_SHRINK);
        entityData.set(HORIZON, horizonLeft > 0 ? r : 0.0F);
        Vec3 c = Vec3.atBottomCenterOf(ensureField());
        if (tickCount % 2 == 0) {                                                        // 輪の縁に立ち昇る黒と赤の粒（外側は暗い霧）
            int n = (int) (r * 1.6);
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 p = c.add(Math.cos(a) * r, random.nextDouble() * 4.0, Math.sin(a) * r);
                Fx.one(server, MementoMoriEntity.glow(random.nextInt(3) == 0 ? RED : DARK, 0.35F, 20), p, new Vec3(0, 0.04, 0));
            }
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = r + 1.0 + random.nextDouble() * 10.0;
                server.sendParticles(ParticleTypes.LARGE_SMOKE, c.x + Math.cos(a) * d, c.y + 0.3 + random.nextDouble() * 2.0, c.z + Math.sin(a) * d, 1, 0, 0.02, 0, 0.0);
            }
        }
        if (tickCount % 20 == 0) playSound(SoundEvents.BEACON_AMBIENT, 3.0F, 0.4F);
        if (horizonLeft % 10 == 0) {
            DamageSource ds = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TimeRulerEntity.ERASURE), this);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(64.0, 24.0, 64.0), ChronoGazer::canHurt)) {
                if (e.position().multiply(1, 0, 1).distanceToSqr(c.multiply(1, 0, 1)) <= r * r) continue;
                e.hurt(ds, damageFor(e, HORIZON_TICK));
                Fx.burst(server, MementoMoriEntity.glow(DARK, 0.3F, 12), e.position().add(0, 1.0, 0), 6, 0.1);
            }
        }
        if (horizonLeft == 0) {                                                          // ほどける
            Fx.shockwave(server, c.add(0, 0.5, 0), VIOLET, HORIZON_FROM, 30);
            playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
        }
    }

    /** 葬送曲の残像の所（零号機のまわり半径 6、向きから 45 度ずつ。描く側も同じ式） */
    public static Vec3 echoPos(Vec3 at, float bodyYaw, int i) {
        double a = Math.toRadians(bodyYaw + 90.0) + Math.PI / 4 + Math.PI / 2 * i;
        return at.add(Math.cos(a) * 6.0, 0.5, Math.sin(a) * 6.0);
    }

    /**
     * 零号の葬送曲: ルクシオン・ガリアフォート・メメントモリ・タイムルーラーの赤黒い残像がまわりに現れ（{@value #ECHO_TICKS} Tick）、
     * 1.5 秒の予告の後、4 体の大技を同時に放つ（光速の斬撃・攻城の光線・死蝶の螺旋・因果断ちの大剣）。融合兵装の総仕上げ
     */
    void requiemEchoes(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        float yaw = yBodyRot;
        for (int i = 0; i < 4; i++) {
            Vec3 at = echoPos(position(), yaw, i);
            Vec3 ground = new Vec3(at.x, t.getY(), at.z);
            double aim = Math.atan2(t.getZ() - at.z, t.getX() - at.x);
            Fx.burst(server, MementoMoriEntity.glow(ECHO_COLORS[i], 0.35F, 20), at.add(0, 1.5, 0), 24, 0.15);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.25F, 14), position().add(0, CHEST, 0), at.add(0, 1.5, 0), 0.5);
            switch (i) {
                case 0 -> {                                                              // ルクシオン: 相手を通る光速の斬撃
                    RulerFxEntity.spawn(server, RulerFxEntity.LANE, 30, ground.add(0, 0.05, 0), 32.0F, (float) aim, 0, 30 + 14, ECHO_COLORS[0]);
                    strikes.add(new Strike(ECHO_SLASH, ground, tickCount + 30, null, aim));
                }
                case 1 -> {                                                              // ガリアフォート: 攻城の光線
                    RulerFxEntity.spawn(server, RulerFxEntity.LANE, 30, ground.add(0, 0.05, 0), 44.0F, (float) aim, 0, 30 + 16, ECHO_COLORS[1]);
                    strikes.add(new Strike(BEAM_FIRE, ground, tickCount + 30, null, aim));
                }
                case 2 -> {                                                              // メメントモリ: 死蝶の螺旋 2 波
                    strikes.add(new Strike(ECHO_SPIRAL, at.add(0, 1.2, 0), tickCount + 30, null, 0));
                    strikes.add(new Strike(ECHO_SPIRAL, at.add(0, 1.2, 0), tickCount + 40, null, 0.3));
                }
                default -> {                                                             // タイムルーラー: 因果断ちの大剣
                    RulerFxEntity.spawn(server, RulerFxEntity.BLADE, 30, ground, TimeRulerEntity.BLADE_LEN, (float) aim, 0, 30 + 40, ECHO_COLORS[3]);
                    strikes.add(new Strike(BLADE_FALL, ground, tickCount + 30, null, aim));
                }
            }
        }
        castLock = 50;
        say("echoes", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_RESONATE, 4.0F, 0.5F);
        playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 3.0F, 0.5F);
        level().broadcastEntityEvent(this, EV_ECHOES);
    }

    /** 葬送曲の残像の色（ルクシオン・ガリアフォート・メメントモリ・タイムルーラー） */
    public static final int[] ECHO_COLORS = {0xFFE8A0, 0xFF7A3A, 0xC070FF, 0xFFD166};

    // ---- 赤黒い天災の技（2026-10-26 ユーザー指定「融合兵装以外にも終末モチーフに自作の赤黒い雷津波嵐炎で通常技を四つと第二形態に２種、第三に２種」）。
    //      第 1 形態から: 赫雷・終の大波・禍つ嵐・煉獄焔。第 2 形態から: 黒雷の十字架・焔の大海嘯。第 3 形態から: 黙示の大嵐・赫焉天墜 ----

    /** c のまわり range の者を真ん中へ引き寄せる（プレイヤーは相対移動で。lift で浮かせる） */
    public void pull(Vec3 c, double range, double strength, double lift) {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(range, 8.0, range), ChronoGazer::canHurt)) {
            Vec3 rel = c.subtract(e.position()).multiply(1, 0, 1);
            double dist = rel.length();
            if (dist < 0.8 || dist > range) continue;
            Vec3 step = rel.scale(strength / dist);
            // teleport は座標をそのまま（絶対）で受け取り、相対の印はクライアントへ送る差にだけ効く。引く量だけを渡すと原点のそばへ飛んでいた（2026-11-01「０座標までとばされる」）
            if (e instanceof ServerPlayer p) p.connection.teleport(p.getX() + step.x, p.getY() + lift, p.getZ() + step.z, p.getYRot(), p.getXRot(), java.util.EnumSet.allOf(net.minecraft.world.entity.RelativeMovement.class));
            else e.push(step.x, lift, step.z);
        }
    }

    /** 扇（pivot から角度 a・開き w・半径 r）の中か */
    static boolean inCone(Vec3 pivot, double a, double w, double r, LivingEntity e) {
        Vec3 rel = e.position().subtract(pivot);
        double dist = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
        if (dist > r + e.getBbWidth() / 2 || rel.y < -2.0 || rel.y > 5.0) return false;
        if (dist < 1.5) return true;
        double diff = net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(rel.z, rel.x) - a));
        return Math.abs(diff) <= Math.toDegrees(w / 2);
    }

    /**
     * 赫雷: 相手の行く先を読んで、赤黒い落雷が 8 Tick ごとに 5 度（第 3 形態 7 度）。赤い予告の円（{@value #BOLT_WARN} Tick、半径 {@value #BOLT_R}）の所へ落ちる（{@value #THUNDER}）
     */
    public void crimsonThunder(LivingEntity t) {
        int n = phase() == 3 ? 7 : 5;
        for (int i = 0; i < n; i++) strikes.add(new Strike(BOLT_MARK, Vec3.ZERO, tickCount + i * 8, t, 0));
        castLock = 20;
        say("thunder", ChatFormatting.RED);
        playSound(SoundEvents.TRIDENT_THUNDER, 3.0F, 0.6F);
        if (level() instanceof ServerLevel server) Fx.burst(server, MementoMoriEntity.glow(RED, 0.3F, 12), position().add(0, CHEST + 1.0, 0), 16, 0.15);
    }

    /**
     * 終の大波: 零号機の後ろから幅 {@value #TIDE_HALF}×2 の赤黒い大波が {@value #TIDE_WARN} Tick 盛り上がり、相手の方へ {@value #TIDE_LEN} m 押し寄せる（{@value #TIDE}・押し流す）。
     * 高さ {@value #TIDE_H} で跳んでも越えられず、道なりの白い光の柱で示す幅 4 の切れ目だけが抜けられる
     */
    public void doomTide(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        double a = Math.atan2(t.getZ() - getZ(), t.getX() - getX());
        Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a)), side = new Vec3(-dir.z, 0, dir.x);
        Vec3 origin = new Vec3(getX(), t.getY(), getZ()).subtract(dir.scale(6.0));
        double gap = (random.nextDouble() * 2 - 1) * (TIDE_HALF - 3.0);
        for (int sd = -1; sd <= 1; sd += 2) {                                               // 切れ目の両端に白い光の柱を道なりに
            for (int k = 0; k < 5; k++) {
                RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, origin.add(side.scale(gap + sd * TIDE_GAP)).add(dir.scale(4 + k * 8)), 0.3F, 0, 0, TIDE_WARN + TIDE_LEN, WHITE);
            }
        }
        RulerFxEntity.spawn(server, RulerFxEntity.Z_TIDE, TIDE_WARN, origin, (float) TIDE_HALF, (float) a, (float) gap, TIDE_WARN + TIDE_LEN + 12, RED);   // 巻く大波の壁（描く側）
        for (int i = 0; i < TIDE_WARN; i += 5) strikes.add(new Strike(TIDE_STEP, origin, tickCount + i, null, a, gap, -1));
        for (int i = 0; i <= TIDE_LEN; i++) strikes.add(new Strike(TIDE_STEP, origin.add(dir.scale(i)), tickCount + TIDE_WARN + i, null, a, gap, i));
        castLock = TIDE_WARN + 10;
        say("tide", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 3.0F, 0.5F);
        playSound(SoundEvents.AMBIENT_UNDERWATER_ENTER, 4.0F, 0.4F);
        level().broadcastEntityEvent(this, EV_TIDE);
    }

    /** 禍つ嵐: 相手の足元に赤黒い竜巻が生まれ、{@value #STORM_LEN} Tick 追いかける。まわり {@value #STORM_PULL} を引き寄せ、真ん中 {@value #STORM_R} を 0.5 秒ごとに削る（{@value #STORM_TICK}） */
    public void calamityStorm(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 at = t.position().add(random.nextGaussian() * 3.0, 0, random.nextGaussian() * 3.0);
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_FAST | RulerFxEntity.HANDS_REVERSE, at.add(0, 0.08, 0), (float) STORM_R, 0, 0, 20, RED);
        RulerFxEntity fx = RulerFxEntity.spawn(server, RulerFxEntity.Z_TWISTER, 20, at, 10.0F, 0, 0, 20 + STORM_LEN + 2, RED);   // 黒い漏斗（描く側。追う間はこちらで動かす）
        strikes.add(new Strike(TWISTER, at, tickCount + 20, t, STORM_LEN, 0, fx.getId()));
        castLock = 24;
        say("storm", ChatFormatting.DARK_RED);
        playSound(SoundEvents.ELYTRA_FLYING, 3.0F, 0.5F);
        Fx.line(server, MementoMoriEntity.glow(DARK, 0.4F, 14), position().add(0, CHEST, 0), at.add(0, 6.0, 0), 0.5);
    }

    /** 煉獄焔: 相手への扇（半径 {@value #FLAME_R}・開き 72 度）に {@value #FLAME_WARN} Tick の予告、赤黒い焔が噴き上がって焼き（{@value #FLAME}・燃える）、床は {@value #BURN_LEN} Tick 燃え続ける */
    public void purgatoryFlame(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 pivot = new Vec3(getX(), t.getY(), getZ());
        double a = Math.atan2(t.getZ() - pivot.z, t.getX() - pivot.x);
        RulerFxEntity.spawn(server, RulerFxEntity.SECTOR, FLAME_WARN, pivot.add(0, 0.06, 0), (float) FLAME_R, (float) a, (float) FLAME_W, FLAME_WARN + 12, RED);
        strikes.add(new Strike(FLAME_ERUPT, pivot, tickCount + FLAME_WARN, null, a));
        RulerFxEntity.spawn(server, RulerFxEntity.Z_FLAME, FLAME_WARN, pivot, (float) FLAME_R, (float) a, (float) FLAME_W, FLAME_WARN + BURN_LEN + 10, EMBER);   // 焔の舌（描く側）
        Fx.gather(server, MementoMoriEntity.glow(EMBER, 0.35F, 12), position().add(0, CHEST, 0), 14, 3.0, 12);
        castLock = FLAME_WARN + 6;
        say("flame", ChatFormatting.RED);
        playSound(SoundEvents.BLAZE_AMBIENT, 3.0F, 0.4F);
        playSound(SoundEvents.FIRECHARGE_USE, 3.0F, 0.5F);
    }

    /** 黒雷の十字架（第 2 形態から）: 零号機の下から 4 本の赤黒い雷の腕（長さ {@value #CROSS_LEN}）が {@value #CROSS_WARN} Tick 予告して伸び、{@value #CROSS_SPIN} Tick ゆっくり回って薙ぐ（{@value #CROSS}） */
    public void thunderCross(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 pivot = new Vec3(getX(), t.getY(), getZ());
        double a0 = Math.atan2(t.getZ() - pivot.z, t.getX() - pivot.x) + Math.PI / 4;           // 相手は腕と腕の間から（回ってくる）
        double spin = random.nextBoolean() ? 1 : -1;
        for (int i = 0; i < 4; i++) RulerFxEntity.spawn(server, RulerFxEntity.LANE, CROSS_WARN, pivot.add(0, 0.05, 0), (float) CROSS_LEN, (float) (a0 + i * Math.PI / 2), 0, CROSS_WARN + 4, RED);
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | (spin < 0 ? RulerFxEntity.HANDS_REVERSE : 0), pivot.add(0, 0.08, 0), 4.0F, 0, 0, CROSS_WARN + CROSS_SPIN, RED);
        strikes.add(new Strike(CROSS_SWEEP, pivot, tickCount + CROSS_WARN, null, CROSS_SPIN, a0, spin));
        RulerFxEntity.spawn(server, RulerFxEntity.Z_CROSS, CROSS_WARN, pivot, (float) CROSS_LEN, (float) a0, (float) (CROSS_TURN * spin), CROSS_WARN + CROSS_SPIN + 6, RED);   // 雷の腕（描く側。同じ式で回る）
        castLock = CROSS_WARN + 20;
        say("cross", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.4F);
        playSound(SoundEvents.TRIDENT_THUNDER, 3.0F, 0.5F);
    }

    /** 焔の大海嘯（第 2 形態から）: 零号機の下から低い焔の輪の波が 3 つ、半径 {@value #DELUGE_MAX} まで広がる（{@value #DELUGE}・燃える）。低いので跳べば越えられる */
    public void crimsonDeluge(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = new Vec3(getX(), t.getY(), getZ());
        int steps = (int) (DELUGE_MAX / DELUGE_SPEED);
        for (int w = 0; w < DELUGE_WAVES; w++) {
            for (int i = 0; i <= steps; i++) strikes.add(new Strike(RING_WAVE, c, tickCount + DELUGE_WARN + w * DELUGE_GAP + i, null, 1.0 + i * DELUGE_SPEED, DELUGE, 1));
            int delay = DELUGE_WARN + w * DELUGE_GAP;                                           // 焔の壁（描く側。同じ式で広がる）
            RulerFxEntity.spawn(server, RulerFxEntity.Z_RING, delay, c, (float) DELUGE_MAX, 1.0F, (float) DELUGE_SPEED, delay + steps + 6, EMBER);
        }
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_FAST, c.add(0, 0.08, 0), 5.0F, 0, 0, DELUGE_WARN, RED);
        Fx.shockwave(server, c.add(0, 0.3, 0), EMBER, 4.0F, DELUGE_WARN);
        castLock = DELUGE_WARN + 10;
        say("deluge", ChatFormatting.RED, ChatFormatting.BOLD);
        playSound(SoundEvents.LAVA_AMBIENT, 4.0F, 0.5F);
        playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.4F);
    }

    /** 黙示の大嵐（第 3 形態から）: 闘技場の縁（半径 {@value #TEMPEST_FROM}）に 4 本の竜巻が立ち、{@value #TEMPEST_LEN} Tick かけて渦を巻きながら真ん中へ寄る。その間、相手へ赫雷が降り続く */
    public void apocalypseTempest(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = Vec3.atBottomCenterOf(ensureField());
        double a0 = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 4; i++) {
            double a = a0 + i * Math.PI / 2;
            Vec3 at = c.add(Math.cos(a) * TEMPEST_FROM, 0, Math.sin(a) * TEMPEST_FROM);
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, at, 1.2F, 0, 0, 24, RED);
            strikes.add(new Strike(SPIRAL_TWISTER, c, tickCount + 20, null, TEMPEST_LEN, a, 0));
        }
        for (int k = 0; k < 12; k++) strikes.add(new Strike(BOLT_MARK, Vec3.ZERO, tickCount + 30 + k * 12, t, 0));
        RulerFxEntity.spawn(server, RulerFxEntity.Z_TEMPEST, 20, c, (float) TEMPEST_FROM, (float) a0, 0, 20 + TEMPEST_LEN + 20, RED);   // 4 本の竜巻と空の大渦（描く側）
        castLock = 40;
        say("tempest", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.WITHER_AMBIENT, 4.0F, 0.4F);
        playSound(SoundEvents.ELYTRA_FLYING, 4.0F, 0.3F);
        level().broadcastEntityEvent(this, EV_QUAKE);
    }

    /**
     * 赫焉天墜（第 3 形態から）: 相手の所に半径 {@value #HEAVEN_R} の赤い文字盤、空に赤黒い光が {@value #HEAVEN_WARN} Tick 集まって、焔と雷の柱が墜ちる
     * （{@value #HEAVENFALL}・防具を通す・燃える）。そこから焔の輪の波が 3 つ広がり（跳べば越えられる）、床は燃え続ける
     */
    public void heavenfall(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 at = t.position();
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_FINALE, at.add(0, 0.08, 0), (float) HEAVEN_R, 0, 0, HEAVEN_WARN + 10, RED);
        for (int i = 0; i < HEAVEN_WARN; i += 5) strikes.add(new Strike(HEAVEN_CHARGE, at, tickCount + i, null, i));
        strikes.add(new Strike(HEAVEN_FALL, at, tickCount + HEAVEN_WARN, null, 0));
        RulerFxEntity.spawn(server, RulerFxEntity.Z_HEAVEN, HEAVEN_WARN, at, (float) HEAVEN_R, 0, 0, HEAVEN_WARN + 40, RED);   // 空の大渦と焔の柱（描く側）
        castLock = HEAVEN_WARN + 20;
        say("heavenfall", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.WITHER_SPAWN, 3.0F, 0.5F);
        Fx.line(server, MementoMoriEntity.glow(RED, 0.4F, 16), position().add(0, CHEST, 0), at.add(0, 26.0, 0), 0.6);
    }

    /** 竜巻を 1 Tick 分（at は根元、age は生まれてからの Tick） */
    void twister(ServerLevel server, Vec3 at, int age) {
        if (age % 2 == 0) {                                                                 // 漏斗と稲妻は描く側（Z_TWISTER）。ここは巻き上がる欠片と煙だけ
            double a = age * 0.45, r = 1.5 + random.nextDouble() * 2.0;
            Vec3 p = at.add(Math.cos(a) * r, random.nextDouble() * 6.0, Math.sin(a) * r);
            Fx.one(server, MementoMoriEntity.glow(random.nextInt(3) == 0 ? RED : DARK, 0.4F, 10), p, new Vec3(-Math.sin(a) * 0.3, 0.12, Math.cos(a) * 0.3));
            server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 4, 1.5, 0.5, 1.5, 0.02);
            server.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 4.0, at.z, 2, 1.2, 2.5, 1.2, 0.02);
        }
        if (age % 25 == 0) server.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER, getSoundSource(), 1.5F, 1.2F);
        if (age % 20 == 0) server.playSound(null, BlockPos.containing(at), SoundEvents.ELYTRA_FLYING, getSoundSource(), 1.5F, 0.4F);
        if (age % 3 == 0) pull(at, STORM_PULL, 0.12, 0.0);
        if (age % 10 == 0) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(STORM_R, 10.0, STORM_R), ChronoGazer::canHurt)) {
                if (e.position().multiply(1, 0, 1).distanceToSqr(at.multiply(1, 0, 1)) > STORM_R * STORM_R) continue;
                e.hurt(damageSources().mobAttack(this), damageFor(e, STORM_TICK));
            }
        }
    }

    /** 燃える床を 1 回分（2 Tick ごと）。inside で中かを決める */
    void burnGround(ServerLevel server, int left, java.util.function.Predicate<LivingEntity> inside, java.util.function.Supplier<Vec3> spot) {
        for (int i = 0; i < 6; i++) {
            Vec3 p = spot.get();
            server.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.1, p.z, 1, 0.1, 0.05, 0.1, 0.01);
            Fx.one(server, MementoMoriEntity.glow(i % 2 == 0 ? EMBER : DARK, 0.3F, 10), p.add(0, 0.2, 0), new Vec3(0, 0.05, 0));
        }
        if (left % 10 == 0) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48.0, 16.0, 48.0), e -> ChronoGazer.canHurt(e) && inside.test(e))) {
                if (e.hurt(damageSources().mobAttack(this), damageFor(e, BURN_TICK))) e.setSecondsOnFire(2);
            }
        }
    }

    /** 焔の輪の波を 1 Tick 分（c から半径 r。足が床から 0.9 より上なら越えられる） */
    void ringWave(ServerLevel server, Vec3 c, double r, float damage) {
        int n = (int) Math.min(24, Math.max(6, r * 0.8));                                  // 焔の壁は描く側（Z_RING）。ここは火の粉だけ
        double off = random.nextDouble();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * (i + off) / n;
            Vec3 p = c.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            server.sendParticles(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LAVA, p.x, p.y + 0.4, p.z, 1, 0.1, 0.2, 0.1, 0.02);
        }
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 2.0, 3.0, r + 2.0), ChronoGazer::canHurt)) {
            double d = Math.sqrt(e.position().multiply(1, 0, 1).distanceToSqr(c.multiply(1, 0, 1)));
            if (Math.abs(d - r) > 0.9 + e.getBbWidth() / 2 || e.getY() - c.y > 0.9 || e.getY() - c.y < -2.0) continue;
            if (e.hurt(damageSources().mobAttack(this), damageFor(e, damage))) e.setSecondsOnFire(4);
        }
    }

    /** 天災の技の時間差の分（tickStrikes から）。扱った種類なら true */
    boolean tickCalamity(ServerLevel server, Strike s) {
        switch (s.kind()) {
            case BOLT_MARK -> {                                                                 // 赫雷の予告: 相手の行く先に赤い円
                if (!(s.follow() instanceof LivingEntity t) || !t.isAlive()) return true;
                Vec3 v = t.getDeltaMovement().multiply(1, 0, 1);
                Vec3 at = t.position().add(v.scale(BOLT_WARN * 0.7));
                RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_FAST, at.add(0, 0.1, 0), (float) BOLT_R, 0, 0, BOLT_WARN, RED);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 6, 0.8, 0.1, 0.8, 0.1);
                strikes.add(new Strike(BOLT_HIT, at, tickCount + BOLT_WARN, null, 0));
            }
            case BOLT_HIT -> {
                Vec3 at = s.at();
                RulerFxEntity.spawn(server, RulerFxEntity.Z_BOLT, 0, at, 22.0F, 0, 0, 14, RED);   // 稲妻・焦げ跡・ひびは描く側
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.2, at.z, 24, 0.8, 0.3, 0.8, 0.4);
                Fx.shockwave(server, at.add(0, 0.2, 0), RED, 3.5F, 12);
                Fx.burst(server, MementoMoriEntity.glow(DARK, 0.4F, 12), at.add(0, 0.4, 0), 10, 0.2);
                hitAround(at, BOLT_R, THUNDER, false);
                server.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_IMPACT, getSoundSource(), 2.0F, 0.6F);
                server.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER, getSoundSource(), 1.5F, 0.7F + random.nextFloat() * 0.3F);
                level().broadcastEntityEvent(this, EV_THUNDER);
            }
            case TIDE_STEP -> {                                                                 // 終の大波の前の面（aux = 切れ目、aux2 < 0 は盛り上がる間）
                Vec3 dir = new Vec3(Math.cos(s.extra()), 0, Math.sin(s.extra())), side = new Vec3(-dir.z, 0, dir.x);
                boolean forming = s.aux2() < 0;
                double h = forming ? 1.5 : TIDE_H;
                for (double l = -TIDE_HALF; l <= TIDE_HALF; l += 2.0) {                          // 波の壁は描く側（Z_TIDE）。ここは波頭のしぶきと黒い飛沫だけ
                    if (Math.abs(l - s.aux()) < TIDE_GAP) continue;
                    Vec3 base = s.at().add(side.scale(l));
                    Fx.one(server, MementoMoriEntity.glow(0xFF6A7A, 0.3F, 10), base.add(0, h, 0), dir.scale(0.2).add(random.nextGaussian() * 0.05, 0.1, random.nextGaussian() * 0.05));
                    server.sendParticles(ParticleTypes.SQUID_INK, base.x, base.y + h * 0.7, base.z, 1, 0.3, h * 0.2, 0.3, 0.02);
                }
                if (forming) return true;
                if ((int) s.aux2() % 6 == 0) {
                    server.playSound(null, BlockPos.containing(s.at()), SoundEvents.GENERIC_SPLASH, getSoundSource(), 3.0F, 0.4F);
                    server.playSound(null, BlockPos.containing(s.at()), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, getSoundSource(), 2.0F, 0.5F);
                }
                AABB box = new AABB(s.at().subtract(side.scale(TIDE_HALF)), s.at().add(side.scale(TIDE_HALF))).inflate(2.0, 0, 2.0).expandTowards(0, TIDE_H, 0).expandTowards(0, -1.0, 0);
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, ChronoGazer::canHurt)) {
                    Vec3 rel = e.position().subtract(s.at());
                    double along = rel.dot(dir), lat = rel.dot(side);
                    if (Math.abs(along) > 1.3 || Math.abs(lat) > TIDE_HALF || Math.abs(lat - s.aux()) < TIDE_GAP - e.getBbWidth() / 2) continue;
                    e.hurt(damageSources().mobAttack(this), damageFor(e, TIDE));
                    e.push(dir.x * 0.9, 0.3, dir.z * 0.9);                                       // 押し流す
                    e.hurtMarked = true;
                }
            }
            case TWISTER -> {                                                                   // 禍つ嵐（extra = 残り、aux = 生まれてから）
                Vec3 at = s.at();
                if (s.follow() instanceof LivingEntity t && t.isAlive()) {
                    Vec3 to = t.position().subtract(at).multiply(1, 0, 1);
                    double d = to.length();
                    if (d > 0.3) at = at.add(to.scale(Math.min(STORM_SPEED, d) / d));
                    at = new Vec3(at.x, at.y + (t.getY() - at.y) * 0.2, at.z);
                }
                twister(server, at, (int) s.aux());
                if (level().getEntity((int) s.aux2()) instanceof RulerFxEntity fx) {                // 描く漏斗も一緒に（毎 Tick 位置を送る）
                    fx.setPos(at.x, at.y, at.z);
                    fx.hasImpulse = true;
                }
                if (s.extra() > 1) strikes.add(new Strike(TWISTER, at, tickCount + 1, s.follow(), s.extra() - 1, s.aux() + 1, s.aux2()));
                else Fx.burst(server, MementoMoriEntity.glow(DARK, 0.5F, 16), at.add(0, 3.0, 0), 30, 0.3);
            }
            case SPIRAL_TWISTER -> {                                                            // 黙示の大嵐（at = 真ん中、aux = 初めの角度）
                int age = TEMPEST_LEN - (int) s.extra();
                double k = age / (double) TEMPEST_LEN, r = TEMPEST_FROM - (TEMPEST_FROM - TEMPEST_TO) * k, a = s.aux() + age * 0.025;
                Vec3 at = s.at().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                twister(server, at, age);
                if (s.extra() > 1) strikes.add(new Strike(SPIRAL_TWISTER, s.at(), tickCount + 1, null, s.extra() - 1, s.aux(), 0));
                else Fx.burst(server, MementoMoriEntity.glow(RED, 0.5F, 16), at.add(0, 3.0, 0), 30, 0.3);
            }
            case FLAME_ERUPT -> {                                                               // 煉獄焔が噴き上がる
                Vec3 pivot = s.at();
                for (int i = 0; i < 48; i++) {
                    double a = s.extra() + (random.nextDouble() - 0.5) * FLAME_W, d = 1.5 + Math.sqrt(random.nextDouble()) * (FLAME_R - 1.5);
                    Vec3 p = pivot.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
                    server.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.8, p.z, 5, 0.25, 0.8, 0.25, 0.06);
                    Fx.spray(server, MementoMoriEntity.glow(i % 3 == 0 ? DARK : i % 3 == 1 ? RED : EMBER, 0.6F, 14), p.add(0, 1.2, 0), 2, 0.3, 1.0, 0.3, 0.05);
                    if (i % 6 == 0) server.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.3, p.z, 2, 0.3, 0.1, 0.3, 0.1);
                }
                Fx.shockwave(server, pivot.add(0, 0.3, 0), EMBER, 6.0F, 14);
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(pivot, pivot).inflate(FLAME_R + 1, 6.0, FLAME_R + 1), ChronoGazer::canHurt)) {
                    if (!inCone(pivot, s.extra(), FLAME_W, FLAME_R, e)) continue;
                    if (e.hurt(damageSources().mobAttack(this), damageFor(e, FLAME))) e.setSecondsOnFire(5);
                }
                server.playSound(null, BlockPos.containing(pivot), SoundEvents.BLAZE_SHOOT, getSoundSource(), 4.0F, 0.4F);
                server.playSound(null, BlockPos.containing(pivot), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 2.5F, 0.6F);
                level().broadcastEntityEvent(this, EV_IMPACT);
                strikes.add(new Strike(BURN_CONE, pivot, tickCount + 2, null, BURN_LEN, s.extra(), 0));
            }
            case BURN_CONE -> {                                                                 // 煉獄焔の焼けた床
                Vec3 pivot = s.at();
                double a0 = s.aux();
                burnGround(server, (int) s.extra(), e -> inCone(pivot, a0, FLAME_W, FLAME_R, e), () -> {
                    double a = a0 + (random.nextDouble() - 0.5) * FLAME_W, d = 1.5 + Math.sqrt(random.nextDouble()) * (FLAME_R - 1.5);
                    return pivot.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                });
                if (s.extra() > 2) strikes.add(new Strike(BURN_CONE, pivot, tickCount + 2, null, s.extra() - 2, a0, 0));
            }
            case BURN -> {                                                                      // 丸く燃える床（aux = 半径）
                Vec3 c = s.at();
                double r = s.aux();
                burnGround(server, (int) s.extra(), e -> e.position().multiply(1, 0, 1).distanceToSqr(c.multiply(1, 0, 1)) <= r * r && Math.abs(e.getY() - c.y) < 3.0, () -> {
                    double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                    return c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                });
                if (s.extra() > 2) strikes.add(new Strike(BURN, c, tickCount + 2, null, s.extra() - 2, r, 0));
            }
            case CROSS_SWEEP -> {                                                               // 黒雷の十字架（extra = 残り、aux = 角度、aux2 = 回る向き）
                Vec3 pivot = s.at();
                for (int i = 0; i < 4; i++) {
                    double a = s.aux() + i * Math.PI / 2;
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));                           // 雷の腕は描く側（Z_CROSS）
                    if (tickCount % 3 == 0) {
                        Vec3 end = pivot.add(dir.scale(CROSS_LEN));
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 1.0, end.z, 4, 0.4, 0.4, 0.4, 0.2);
                    }
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(pivot, pivot.add(dir.scale(CROSS_LEN))).inflate(1.5, 0, 1.5).expandTowards(0, 3.5, 0).expandTowards(0, -1.0, 0), ChronoGazer::canHurt)) {
                        Vec3 rel = e.position().subtract(pivot).multiply(1, 0, 1);
                        double along = rel.dot(dir);
                        if (along < 0 || along > CROSS_LEN || rel.subtract(dir.scale(along)).length() > 1.0 + e.getBbWidth() / 2) continue;
                        e.hurt(damageSources().mobAttack(this), damageFor(e, CROSS));
                    }
                }
                if ((int) s.extra() % 10 == 0) server.playSound(null, BlockPos.containing(pivot), SoundEvents.LIGHTNING_BOLT_IMPACT, getSoundSource(), 2.0F, 1.4F);
                if (s.extra() > 1) strikes.add(new Strike(CROSS_SWEEP, pivot, tickCount + 1, null, s.extra() - 1, s.aux() + CROSS_TURN * s.aux2(), s.aux2()));
            }
            case RING_WAVE -> ringWave(server, s.at(), s.extra(), (float) s.aux());
            case HEAVEN_CHARGE -> {                                                             // 赫焉天墜の溜め: 空へ赤黒い光が集まり、文字盤の縁に小さな雷
                Vec3 sky = s.at().add(0, 26.0, 0);
                Fx.gather(server, MementoMoriEntity.glow(s.extra() % 10 == 0 ? RED : DARK, 0.5F, 8), sky, 12, 6.0, 8);
                double a = random.nextDouble() * Math.PI * 2;
                RulerFxEntity.spawn(server, RulerFxEntity.Z_BOLT, 0, s.at().add(Math.cos(a) * HEAVEN_R, 0, Math.sin(a) * HEAVEN_R), 26.0F, 0, 0, 8, RED);
                server.playSound(null, BlockPos.containing(s.at()), SoundEvents.BEACON_POWER_SELECT, getSoundSource(), 3.0F, 0.4F + (float) s.extra() * 0.02F);
            }
            case HEAVEN_FALL -> {                                                               // 焔と雷の柱が墜ちる
                Vec3 at = s.at();
                server.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
                for (int i = 0; i < 6; i++) {                                                   // 柱はすでに Z_HEAVEN が描いている。まわりに落雷
                    double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * HEAVEN_R;
                    RulerFxEntity.spawn(server, RulerFxEntity.Z_BOLT, 0, at.add(Math.cos(a) * d, 0, Math.sin(a) * d), 26.0F, 0, 0, 10 + i * 2, RED);
                }
                RulerFxEntity.spawn(server, RulerFxEntity.Z_FLAME, 0, at, (float) HEAVEN_R, 0, (float) (Math.PI * 2), 90, EMBER);   // 丸く燃え続ける焔
                server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.0, at.z, 160, 3.0, 1.5, 3.0, 0.15);
                server.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 40, 2.5, 0.5, 2.5, 0.3);
                server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 2.0, at.z, 60, 3.0, 2.0, 3.0, 0.05);
                Fx.burst(server, MementoMoriEntity.glow(EMBER, 0.6F, 24), at.add(0, 1.0, 0), 40, 0.5);
                Fx.burst(server, MementoMoriEntity.glow(DARK, 0.8F, 24), at.add(0, 1.5, 0), 30, 0.4);
                Fx.shockwave(server, at.add(0, 0.3, 0), RED, 16.0F, 24);
                Fx.shockwave(server, at.add(0, 0.5, 0), EMBER, 10.0F, 18);
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(HEAVEN_R, 4.0, HEAVEN_R), ChronoGazer::canHurt)) {
                    if (e.position().multiply(1, 0, 1).distanceToSqr(at.multiply(1, 0, 1)) <= HEAVEN_R * HEAVEN_R) e.setSecondsOnFire(6);
                }
                hitAround(at, HEAVEN_R, HEAVENFALL, true);
                int steps = (int) ((22.0 - HEAVEN_R) / DELUGE_SPEED);
                for (int w = 0; w < 3; w++) {
                    for (int i = 0; i <= steps; i++) strikes.add(new Strike(RING_WAVE, at, tickCount + 4 + w * 14 + i, null, HEAVEN_R + i * DELUGE_SPEED, DELUGE * 0.8, 1));
                    RulerFxEntity.spawn(server, RulerFxEntity.Z_RING, 4 + w * 14, at, 22.0F, (float) HEAVEN_R, (float) DELUGE_SPEED, 4 + w * 14 + steps + 6, EMBER);
                }
                strikes.add(new Strike(BURN, at, tickCount + 2, null, 80, HEAVEN_R, 0));
                server.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 5.0F, 0.4F);
                server.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER, getSoundSource(), 5.0F, 0.5F);
                level().broadcastEntityEvent(this, EV_HEAVENFALL);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 終幕カタストロフィレクイエム ― 声なき葬送（2026-10-26 ユーザー指定。HP 10% 以下で 1 度だけ。その間は受けず倒れない。
     * 同日「声なき葬送としてテキストは無しに様々な新しい演出で最終楽章で確定９割ダメージで（後隙で削りきる想定）」で、セリフ・題字・文字を全部やめて演出だけで見せる）:
     * 前奏で音が消え（描く側）、画面が暗く沈んで上下に黒い帯、零号機は闘技場の真ん中の高くへ昇り、空に五線の輪が回る。
     * 第 1 楽章（赤）・第 2 楽章（青い涙が降る）は、光る安全な足場（半径 {@value #PLATFORM_R}）が現れて 3 秒予告し、鎮魂の光が床じゅうに降る（足場の外の者に {@value #REQUIEM_HIT}、防具を通す）。
     * 終楽章は足場が無く、空の光が零号機へ集まって鼓動が速まり、{@value #FINAL_BLOW} Tick で避けられない白い一撃: 範囲のクリエイティブ・観戦でないプレイヤーと兵姫の HP を
     * 最大の {@value #FINAL_BLOW_LEFT} 倍まで確定で削る（9 割のダメージ。倒しはしない）。世界は壊さない（崩れた床は描くだけ）。
     * 終わると零号機は力尽き、{@value #FINALE_STUN} Tick 動けず受けるダメージ 2 倍（その間に削り切る）
     */
    public void startFinale() {
        if (!(level() instanceof ServerLevel server)) return;
        ensureField();
        strikes.clear();
        holeLeft = 0;
        horizonLeft = 0;
        entityData.set(HORIZON, 0.0F);
        entityData.set(STUN, 0);
        entityData.set(FINALE, 1);
        castLock = FINALE_TICKS;
        playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.3F);
        Vec3 c = position().add(0, CHEST, 0);
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        Fx.shockwave(server, c, RED, 40.0F, 40);
        Fx.shockwave(server, c, WHITE, 24.0F, 30);
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_FINALE, Vec3.atBottomCenterOf(ensureField()).add(0, 0.1, 0), 34.0F, 0, 0, FINALE_TICKS, RED);
        level().broadcastEntityEvent(this, EV_FINALE);
    }

    void tickFinale(ServerLevel server) {
        int f = finaleTicks();
        if (f <= 0) return;
        f++;
        entityData.set(FINALE, f);
        BlockPos center = ensureField();
        Vec3 c = Vec3.atBottomCenterOf(center);
        int m = movement(f), s = movementTick(f);
        int beatEvery = m == 2 ? Math.max(6, 24 - s / 6) : 24;                             // 鼓動（終楽章は一撃へ向けて速まる）
        if (f % beatEvery == 0 && (m != 2 || s < FINAL_BLOW)) playSound(SoundEvents.WARDEN_HEARTBEAT, 4.0F, m == 2 ? 0.8F + s * 0.004F : 0.6F);
        if (f < FINALE_LEAD && f % 3 == 0) Fx.gather(server, MementoMoriEntity.glow(f % 6 == 0 ? RED : DARK, 0.4F, 16), position().add(0, CHEST, 0), 8, 14.0, 16);
        if (m == 2) {
            tickFinalMovement(server, c, s);
        } else if (m >= 0) {
            int n = platformCount(m);
            int col = m == 0 ? RED : TEAR;
            if (s == 0) {                                                                // 楽章の始まり: 足場が光り、鐘
                for (int i = 0; i < 3; i++) playSound(SoundEvents.BELL_BLOCK, 4.0F, 0.5F + m * 0.15F);
                Fx.shockwave(server, position().add(0, CHEST, 0), col, 36.0F, 30);
                for (int i = 0; i < n; i++) {
                    Vec3 p = platform(center, m, i);
                    RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_SLOW, p.add(0, 0.12, 0), (float) PLATFORM_R, 0, 0, MOVEMENT_TICKS - 10, WHITE);
                    Fx.burst(server, MementoMoriEntity.glow(WHITE, 0.3F, 16), p.add(0, 0.5, 0), 12, 0.1);
                }
            }
            if (s < 60 && s % 4 == 0) {                                                  // 予告: 足場の外の床に赤い霧（第 2 楽章は青）と、足場へ立つ白い光
                for (int i = 0; i < 16; i++) {
                    double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * 30.0;
                    Vec3 p = c.add(Math.cos(a) * d, 0.2, Math.sin(a) * d);
                    if (onPlatform(center, m, p)) continue;
                    Fx.one(server, MementoMoriEntity.glow(col, 0.3F, 14), p, new Vec3(0, 0.03, 0));
                }
                for (int i = 0; i < n; i++) Fx.one(server, MementoMoriEntity.glow(WHITE, 0.2F, 16), platform(center, m, i).add(random.nextGaussian() * 0.8, 0.2, random.nextGaussian() * 0.8), new Vec3(0, 0.08, 0));
                if (s % 20 == 0) playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 3.0F, 0.6F + s / 60.0F);
            }
            if (m == 1 && s % 2 == 0) {                                                  // 第 2 楽章: 空から青白い涙が降る
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * 30.0;
                    Fx.one(server, MementoMoriEntity.glow(random.nextBoolean() ? TEAR : WHITE, 0.15F, 30), c.add(Math.cos(a) * d, 14.0 + random.nextDouble() * 4.0, Math.sin(a) * d), new Vec3(0, -0.5, 0));
                }
            }
            if (s >= 60 && (s - 60) % 15 == 0 && (s - 60) / 15 < 4) requiemPulse(server, center, m);
        }
        if (f >= FINALE_TICKS) endFinale(server);
    }

    /** 第 2 楽章の涙の色 */
    static final int TEAR = 0x7FB8FF;

    /**
     * 終楽章: 足場は無い。闘技場じゅうの白い光と空の光の筋が零号機へ集まり、鼓動が速まる。{@value #FINAL_BLOW} Tick で、範囲（零の墓標なら次元じゅう、
     * ほかの所は 96 m 以内）のクリエイティブ・観戦でないプレイヤーと兵姫の HP を最大の {@value #FINAL_BLOW_LEFT} 倍まで確定で削る（防具・耐性・無敵時間を問わない。倒しはしない）
     */
    void tickFinalMovement(ServerLevel server, Vec3 c, int s) {
        Vec3 chest = position().add(0, CHEST, 0);
        if (s == 0) {
            playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.4F);
            Fx.shockwave(server, chest, WHITE, 36.0F, 30);
        }
        if (s < FINAL_BLOW) {
            float k = s / (float) FINAL_BLOW;
            if (s % 2 == 0) Fx.gather(server, MementoMoriEntity.glow(s % 4 == 0 ? WHITE : 0xFFD0E0, 0.3F + 0.3F * k, 16), chest, 4 + (int) (8 * k), 28.0 - 18.0 * k, 16);
            if (s % 10 == 0) {                                                           // 空から零号機へ走る白い光の筋
                for (int i = 0; i < 3; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    Vec3 from = c.add(Math.cos(a) * 24.0, 18.0, Math.sin(a) * 24.0);
                    Fx.line(server, MementoMoriEntity.glow(WHITE, 0.3F, 10), from, chest, 0.6);
                }
            }
            if (s % 20 == 0) Fx.shockwave(server, chest, WHITE, 30.0F * (1.0F - k) + 3.0F, 20);   // 外から縮む輪
            if (s == FINAL_BLOW - 20) playSound(SoundEvents.BEACON_POWER_SELECT, 5.0F, 0.5F);
        }
        if (s == FINAL_BLOW) {                                                           // 確定の一撃
            server.sendParticles(ParticleTypes.FLASH, chest.x, chest.y, chest.z, 1, 0, 0, 0, 0);
            Fx.burst(server, MementoMoriEntity.glow(WHITE, 0.9F, 34), chest, 120, 0.6);
            Fx.shockwave(server, chest, WHITE, 60.0F, 40);
            Fx.shockwave(server, c.add(0, 0.3, 0), 0xFFD0E0, 44.0F, 34);
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, position(), 4.0F, 0, 0, 40, WHITE);
            playSound(SoundEvents.GENERIC_EXPLODE, 6.0F, 0.4F);
            playSound(SoundEvents.BELL_RESONATE, 6.0F, 1.6F);
            level().broadcastEntityEvent(this, EV_REQUIEM_BLOW);
            boolean grave = com.automatavalkyries.world.ZeroGrave.isGrave(server);
            List<LivingEntity> hit = new ArrayList<>();
            for (Entity e : server.getAllEntities()) {
                if (!(e instanceof Player || e instanceof ValkyrieEntity) || !((LivingEntity) e).isAlive()) continue;
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                if (!grave && e.distanceToSqr(this) > 96 * 96) continue;
                hit.add((LivingEntity) e);
            }
            for (LivingEntity e : hit) {
                float left = Math.max(1.0F, e.getMaxHealth() * FINAL_BLOW_LEFT);
                if (e.getHealth() > left) e.setHealth(left);
                e.setAbsorptionAmount(0.0F);
                Fx.burst(server, MementoMoriEntity.glow(WHITE, 0.3F, 16), e.position().add(0, 1.0, 0), 12, 0.15);
            }
        }
    }

    boolean onPlatform(BlockPos center, int m, Vec3 p) {
        for (int i = 0; i < platformCount(m); i++) {
            Vec3 q = platform(center, m, i);
            double dx = p.x - q.x, dz = p.z - q.z;
            if (dx * dx + dz * dz <= PLATFORM_R * PLATFORM_R) return true;
        }
        return false;
    }

    /** 鎮魂の光: 床じゅうに光の柱（第 1 楽章は赤、第 2 楽章は青）が降り、足場の外の者を打つ */
    void requiemPulse(ServerLevel server, BlockPos center, int m) {
        Vec3 c = Vec3.atBottomCenterOf(center);
        int col = m == 0 ? RED : TEAR;
        for (int i = 0; i < 8 + m * 4; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = 3.0 + random.nextDouble() * 28.0;
            Vec3 p = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            if (onPlatform(center, m, p)) continue;
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, p, 0.7F, 0, 0, 14, col);
        }
        Fx.shockwave(server, c.add(0, 0.3, 0), col, 34.0F, 18);
        server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.6), getZ(), 1, 0, 0, 0, 0);
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 0.6F + m * 0.15F);
        playSound(SoundEvents.BELL_RESONATE, 3.0F, 0.6F + m * 0.2F);
        level().broadcastEntityEvent(this, EV_IMPACT);
        DamageSource ds = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TimeRulerEntity.ERASURE), this);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(48.0, 24.0, 48.0), ChronoGazer::canHurt)) {
            if (onPlatform(center, m, e.position())) continue;
            e.invulnerableTime = 0;
            e.hurt(ds, damageFor(e, REQUIEM_HIT));
        }
    }

    /** 終幕を耐え切られた: 白い光の中で力尽き、動けず受けるダメージ 2 倍 */
    void endFinale(ServerLevel server) {
        entityData.set(FINALE, 0);
        finaleDone = true;
        entityData.set(STUN, FINALE_STUN);
        castLock = FINALE_STUN;
        Vec3 c = position().add(0, CHEST, 0);
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        Fx.burst(server, MementoMoriEntity.glow(WHITE, 0.6F, 30), c, 60, 0.4);
        Fx.shockwave(server, c, WHITE, 30.0F, 34);
        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, position(), 2.4F, 0, 0, 40, WHITE);
        playSound(SoundEvents.BEACON_DEACTIVATE, 4.0F, 0.5F);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0F, 1.2F);
        level().broadcastEntityEvent(this, EV_SHOCK);
    }

    void hitAround(Vec3 at, double r, float damage, boolean erasure) {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 3.0, r), ChronoGazer::canHurt)) {
            if (e.position().multiply(1, 0, 1).distanceToSqr(at.multiply(1, 0, 1)) > r * r) continue;
            DamageSource ds = erasure ? new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TimeRulerEntity.ERASURE), this)
                    : damageSources().mobAttack(this);
            e.hurt(ds, damageFor(e, damage));
        }
    }

    float facing(@Nullable LivingEntity t) {
        if (t != null) return (float) Math.atan2(t.getZ() - getZ(), t.getX() - getX());
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        return (float) Math.atan2(f.z, f.x);
    }

    // ---- 時間差の技 ----

    void tickStrikes(ServerLevel server) {
        for (Strike s0 : new ArrayList<>(strikes)) {
            Strike s = s0;
            int left = s.fire() - tickCount;
            if (s.kind() != STOP_BEGIN && TimeStop.active() && left < 30) {
                strikes.remove(s);
                strikes.add(s.later(s.fire() + 1));
                continue;
            }
            if (left > 0) continue;
            strikes.remove(s);
            switch (s.kind()) {
                case DASH_AIM -> {
                    if (s.follow() instanceof LivingEntity t && t.isAlive()) {
                        Vec3 from = new Vec3(getX(), t.getY(), getZ());
                        Vec3 dir = t.position().subtract(from).multiply(1, 0, 1);
                        if (dir.lengthSqr() < 1.0e-4) dir = new Vec3(1, 0, 0);
                        dir = dir.normalize();
                        RulerFxEntity.spawn(server, RulerFxEntity.LANE, 12, from.add(0, 0.05, 0), 30.0F, (float) Math.atan2(dir.z, dir.x), 0, 12 + 14, RED);
                        strikes.add(new Strike(DASH_GO, from.add(dir.scale(30.0)), tickCount + 12, null, 0));
                        level().broadcastEntityEvent(this, EV_SLASH);
                        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.9F);
                    }
                }
                case DASH_GO -> {
                    Vec3 from = new Vec3(getX(), s.at().y, getZ()), to = s.at();
                    Vec3 seg = to.subtract(from).multiply(1, 0, 1);
                    double len = seg.length();
                    Vec3 dir = len < 1.0e-4 ? new Vec3(1, 0, 0) : seg.scale(1.0 / len);
                    Fx.line(server, MementoMoriEntity.glow(RED, 0.6F, 20), from.add(0, 2.0, 0), to.add(0, 2.0, 0), 0.4);
                    Fx.line(server, MementoMoriEntity.glow(WHITE, 0.3F, 14), from.add(0, 1.0, 0), to.add(0, 1.0, 0), 0.3);
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(2.5, 5.0, 2.5), ChronoGazer::canHurt)) {
                        Vec3 rel = e.position().subtract(from).multiply(1, 0, 1);
                        double along = rel.dot(dir);
                        if (along < -1.0 || along > len + 1.0 || rel.subtract(dir.scale(along)).length() > 1.8 + e.getBbWidth() / 2) continue;
                        e.hurt(damageSources().mobAttack(this), damageFor(e, SLASH));
                    }
                    for (double k = 0; k <= len; k += 3.0) {                                    // 駆け抜けた道に残る赤い残像と、終わりの斬撃の弧
                        Vec3 p = from.add(dir.scale(k)).add(0, 1.3, 0);
                        Fx.burst(server, MementoMoriEntity.glow(k % 6 == 0 ? RED : MAGENTA, 0.3F, 14), p, 5, 0.05);
                    }
                    Fx.slash(server, to.add(0, 1.4, 0), RED, 3.0F, 10, (float) (random.nextDouble() * Math.PI));
                    moveTo(to.x, to.y + 1.0, to.z, getYRot(), getXRot());
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F);
                    playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.6F);
                }
                case BEAM_FIRE -> {
                    Vec3 from = s.at();
                    Vec3 dir = new Vec3(Math.cos(s.extra()), 0, Math.sin(s.extra()));
                    Vec3 to = from.add(dir.scale(44.0));
                    Fx.line(server, MementoMoriEntity.glow(RED, 0.9F, 18), from.add(0, 1.2, 0), to.add(0, 1.2, 0), 0.35);
                    Fx.line(server, MementoMoriEntity.glow(WHITE, 0.45F, 14), from.add(0, 1.2, 0), to.add(0, 1.2, 0), 0.3);
                    Fx.shockwave(server, to.add(0, 0.5, 0), RED, 5.0F, 16);
                    for (int k = 1; k <= 6; k++) Fx.beamRing(server, from.add(0, 1.2, 0).add(dir.scale(44.0 * k / 7)), dir, k % 2 == 0 ? WHITE : RED, 1.6F, 12);   // 光線を巻く輪
                    Fx.shockwave(server, position().add(0, CHEST, 0), RED, 3.0F, 10);
                    level().broadcastEntityEvent(this, EV_IMPACT);
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(3.0, 6.0, 3.0), ChronoGazer::canHurt)) {
                        Vec3 rel = e.position().subtract(from).multiply(1, 0, 1);
                        double along = rel.dot(dir);
                        if (along < 0 || along > 44.0 || rel.subtract(dir.scale(along)).length() > 2.0 + e.getBbWidth() / 2) continue;
                        e.hurt(damageSources().mobAttack(this), damageFor(e, BEAM));
                    }
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 4.0F, 0.6F);
                }
                case SPIRAL_WAVE -> {
                    Vec3 c = position().add(0, CHEST, 0);
                    for (int i = 0; i < 20; i++) {
                        double a = Math.PI * 2 * i / 20 + s.extra() + tickCount * 0.02;
                        Vec3 d = new Vec3(Math.cos(a), Math.sin(i * 0.9) * 0.15, Math.sin(a));
                        Vec3 from = c.add(d.scale(2.5));
                        RulerNeedleEntity.place(this, from, from.add(d), 6, NEEDLE * trialAttack, 0.9F);
                    }
                    Fx.mandala(server, c, MAGENTA, 2.5F, 10, true, s.extra() > 0.5);           // 波ごとに胸の前に蝶の陣
                    Fx.burst(server, MementoMoriEntity.glow(VIOLET, 0.25F, 14), c, 12, 0.18);
                    playSound(SoundEvents.TRIDENT_RIPTIDE_1, 2.0F, 1.2F);
                }
                case METEOR_MARK -> {                                                    // 零落星の予告: 落ちる所に赤い円
                    Vec3 at;
                    if (s.follow() instanceof LivingEntity t && t.isAlive() && random.nextBoolean()) {
                        at = t.position().add(random.nextGaussian() * 3.0, 0, random.nextGaussian() * 3.0);
                    } else {
                        double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * 24.0;
                        at = s.at().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    }
                    RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_FAST, at.add(0, 0.1, 0), 2.8F, 0, 0, 22, RED);
                    strikes.add(new Strike(METEOR_HIT, at, tickCount + 22, null, random.nextDouble() * Math.PI * 2));
                }
                case METEOR_HIT -> {                                                     // 燃える残骸が斜めに落ちて砕ける
                    Vec3 at = s.at();
                    Vec3 from = at.add(Math.cos(s.extra()) * 7.0, 24.0, Math.sin(s.extra()) * 7.0);
                    Fx.line(server, MementoMoriEntity.glow(RED, 0.7F, 10), from, at, 0.6);
                    Fx.line(server, MementoMoriEntity.glow(WHITE, 0.35F, 8), from, at, 0.9);
                    for (double k = 0; k < 1.0; k += 0.08) {
                        Vec3 p = from.add(at.subtract(from).scale(k));
                        server.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.01);
                        server.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.01);
                    }
                    Fx.shockwave(server, at.add(0, 0.3, 0), RED, 5.0F, 16);
                    Fx.burst(server, MementoMoriEntity.glow(0xFF8040, 0.35F, 18), at.add(0, 0.5, 0), 16, 0.25);
                    server.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 8, 0.6, 0.2, 0.6, 0.1);
                    server.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 2.5F, 0.6F + random.nextFloat() * 0.3F);
                    hitAround(at, 2.8, FALL, false);
                    if (random.nextInt(4) == 0) level().broadcastEntityEvent(this, EV_IMPACT);
                }
                case ECHO_SLASH -> {                                                     // ルクシオンの残像の斬撃（零号機は動かない）
                    Vec3 from = s.at(), dir = new Vec3(Math.cos(s.extra()), 0, Math.sin(s.extra()));
                    Vec3 to = from.add(dir.scale(32.0));
                    Fx.line(server, MementoMoriEntity.glow(ECHO_COLORS[0], 0.6F, 20), from.add(0, 1.6, 0), to.add(0, 1.6, 0), 0.4);
                    Fx.line(server, MementoMoriEntity.glow(WHITE, 0.3F, 14), from.add(0, 1.0, 0), to.add(0, 1.0, 0), 0.3);
                    Fx.slash(server, to.add(0, 1.4, 0), ECHO_COLORS[0], 3.0F, 10, (float) s.extra());
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(2.5, 5.0, 2.5), ChronoGazer::canHurt)) {
                        Vec3 rel = e.position().subtract(from).multiply(1, 0, 1);
                        double along = rel.dot(dir);
                        if (along < -1.0 || along > 33.0 || rel.subtract(dir.scale(along)).length() > 1.8 + e.getBbWidth() / 2) continue;
                        e.hurt(damageSources().mobAttack(this), damageFor(e, SLASH));
                    }
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.6F);
                }
                case ECHO_SPIRAL -> {                                                    // メメントモリの残像の死蝶の螺旋
                    for (int i = 0; i < 20; i++) {
                        double a = Math.PI * 2 * i / 20 + s.extra();
                        Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                        Vec3 from = s.at().add(d.scale(1.5));
                        RulerNeedleEntity.place(this, from, from.add(d), 6, NEEDLE * trialAttack, 0.9F);
                    }
                    Fx.mandala(server, s.at(), ECHO_COLORS[2], 2.5F, 10, true, true);
                    playSound(SoundEvents.TRIDENT_RIPTIDE_1, 2.0F, 1.2F);
                }
                case BEAM_CHARGE -> {
                    Vec3 c = position().add(0, CHEST, 0);
                    Fx.gather(server, MementoMoriEntity.glow(tickCount % 2 == 0 ? RED : WHITE, 0.3F, 6), c, 8, 3.5, 6);
                    Fx.beamRing(server, c.add(new Vec3(Math.cos(s.extra()), 0, Math.sin(s.extra())).scale(0.8)), new Vec3(Math.cos(s.extra()), 0, Math.sin(s.extra())), RED, 1.2F, 6);
                }
                case BLADE_FALL -> {
                    Vec3 d = new Vec3(Math.cos(s.extra()), 0, Math.sin(s.extra()));
                    Vec3 end = s.at().add(d.scale(TimeRulerEntity.BLADE_LEN));
                    for (int i = 0; i <= 8; i++) Fx.shockwave(server, s.at().add(d.scale(TimeRulerEntity.BLADE_LEN * i / 8.0)).add(0, 0.3, 0), i % 2 == 0 ? RED : WHITE, 3.5F, 16);
                    server.sendParticles(ParticleTypes.FLASH, end.x, end.y + 1, end.z, 1, 0, 0, 0, 0);
                    Fx.slash(server, s.at().add(d.scale(TimeRulerEntity.BLADE_LEN * 0.5)).add(0, 1.0, 0), RED, 6.0F, 14, (float) s.extra());
                    playSound(SoundEvents.ANVIL_LAND, 4.0F, 0.3F);
                    level().broadcastEntityEvent(this, EV_QUAKE);
                    AABB box = new AABB(s.at(), end).inflate(TimeRulerEntity.BLADE_HALF_WIDTH + 2.0, 0, TimeRulerEntity.BLADE_HALF_WIDTH + 2.0).expandTowards(0, 10, 0).expandTowards(0, -3, 0);
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, ChronoGazer::canHurt)) {
                        if (TimeRulerEntity.onBladeLine(s.at(), d, e)) CausalLock.lock(e, TimeRulerEntity.LOCK_TICKS, this);
                    }
                }
                case RAIN_SPAWN -> {
                    if (s.follow() instanceof LivingEntity t && t.isAlive()) {
                        for (int i = 0; i < 2; i++) {
                            double a = random.nextDouble() * Math.PI * 2, r = i == 0 && random.nextBoolean() ? 0 : Math.sqrt(random.nextDouble()) * 11.0;
                            Vec3 at = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_FAST, at.add(0, 0.1, 0), 1.7F, 0, 0, 14, RED);
                            strikes.add(new Strike(RAIN_FALL, at, tickCount + 14, null, 0));
                        }
                    }
                }
                case RAIN_FALL -> {
                    RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 0.45F, 0, 0, 12, RED);
                    Fx.line(server, MementoMoriEntity.glow(random.nextBoolean() ? RED : WHITE, 0.2F, 8), s.at().add(0, 14.0, 0), s.at(), 0.7);   // 空から落ちるデータの筋
                    Fx.burst(server, MementoMoriEntity.glow(RED, 0.2F, 10), s.at().add(0, 0.2, 0), 6, 0.12);
                    hitAround(s.at(), 1.7, RAIN, false);
                }
                case STOP_BEGIN -> {
                    TimeStop.start(server, this, 100);
                    speak("stopped", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                    Fx.shockwave(server, position().add(0, CHEST, 0), WHITE, 32.0F, 20);           // 時が止まる瞬間: 白い輪が広がり、足元に赤い文字盤
                    RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_TO_TWELVE, position().add(0, 0.1, 0), 9.0F, 0, 0, 100, RED);
                    level().broadcastEntityEvent(this, EV_IMPACT);
                    for (LivingEntity v : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48.0), e -> ChronoGazer.canHurt(e) && TimeStop.frozen(e))) {
                        Vec3 chest = v.position().add(0, v.getBbHeight() * 0.55, 0);
                        int ring = phase() == 3 ? 24 : 16;
                        for (int i = 0; i < ring; i++) {
                            double a = Math.PI * 2 * i / ring;
                            Vec3 from = chest.add(Math.cos(a) * 4.5, (i % 3 - 1), Math.sin(a) * 4.5);
                            RulerNeedleEntity.place(this, from, chest, 20, NEEDLE * 0.9F * trialAttack, 1.0F);
                        }
                    }
                }
                default -> tickCalamity(server, s);
            }
        }
    }

    // ------------------------------------------------------------------ 受ける・倒れる

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            if (!introDone || retreating || chantLeft() > 0 || finaleTicks() > 0) return false;   // 詠唱・終幕の間は一切受けない
            if (source.getEntity() instanceof ValkyrieEntity v && isHacked(v)) return false; // 乗っ取った兵姫の攻撃は当たらない
            if (stunLeft() > 0) amount *= 2.0F;
            float sh = shield();
            if (sh > 0 && amount > 0) {
                float absorbed = Math.min(sh, amount);
                entityData.set(SHIELD, sh - absorbed);
                amount -= absorbed;
                if (amount <= 0) {
                    playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.5F, 0.6F);
                    return false;
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) {
            protect.markDefeated();
            setHealth(0.0F);
            return;
        }
        if (chantLeft() > 0 || finaleTicks() > 0) {                                      // 詠唱・終幕の間は倒れない（コマンドも）
            setHealth(1.0F);
            return;
        }
        if (phase() == 3 && !finaleDone && introDone && !retreating) {                   // 終幕を見せずには倒れない: 一撃で HP が尽きても、先に終幕を始める
            setHealth(1.0F);
            startFinale();
            return;
        }
        if (protect.interceptDie()) return;
        entityData.set(DEFEATED, true);
        super.die(source);
    }

    private void ensureLoot() {
        if (lootDropped || !(level() instanceof ServerLevel)) return;
        lootDropped = true;
        DamageSource source = getLastDamageSource() != null ? getLastDamageSource() : damageSources().generic();
        dropFromLootTable(source, getLastHurtByMob() instanceof Player);
    }

    /** 落とし物は重さを無くして宙に浮かせ、光らせ、消えないようにする（零の墓標の奈落へ落ちない。タイムルーラーと同じ） */
    @Override
    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(ItemStack stack, float yOffset) {
        net.minecraft.world.entity.item.ItemEntity item = super.spawnAtLocation(stack, yOffset);
        if (item != null) {
            item.setNoGravity(true);
            item.setDeltaMovement((random.nextDouble() - 0.5) * 0.12, 0.02, (random.nextDouble() - 0.5) * 0.12);
            item.setUnlimitedLifetime();
            item.setGlowingTag(true);
        }
        return item;
    }

    /**
     * 撃破（{@value #DEFEAT_TICKS} Tick。2026-10-26 ユーザー指定「演出を改良する」でタイムルーラー並みに）:
     * 1 時が止まったように固まり、赤い閃光と画面のグリッチ・揺れ。1〜40 全身から赤いデータの欠片が剥がれ、体が時々ずれる（描く側）。
     * 36〜40 光輪が膨らみ、40 で砕けて桃色の破片が飛び散り落ちる（描く側で光輪が消える）。50〜110 ゆっくり沈んで膝をつく。
     * 70〜100 面頬の赤い光が消え、顔から白い光がこぼれる（怨嗟が抜ける）。100 最後の言葉。100〜140 空の裂け目が閉じて残骸が沈み（描く側）、白い光が胸へ集まる。
     * 140 白い閃光・衝撃波・光の柱、画面に大きく地の文。160 で消え、落とし物
     */
    @Override
    protected void tickDeath() {
        if (protect.refuseDeathTick()) return; // 撃破を認めていないのに演出を回された（The King Sword 等）
        ++deathTime;
        if (level().isClientSide) {
            int d = deathTime;
            if (d == 1) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                com.automatavalkyries.client.ClientZeroShow.flash(this, 10, RED);
                com.automatavalkyries.client.ClientZeroShow.glitch(this, 40, 0.8F);
                com.automatavalkyries.client.ClientZeroShow.shake(this, 0.6F);
            });
            if (d == 40) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.automatavalkyries.client.ClientZeroShow.glitch(this, 10, 0.4F));
            if (d == 140) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                com.automatavalkyries.client.ClientZeroShow.flash(this, 26, 0xFFFFFF);
                com.automatavalkyries.client.ClientZeroShow.defeat(this);
                com.automatavalkyries.client.ClientZeroShow.shake(this, 0.5F);
            });
            if (d > 1 && d < 45) {                                                           // 剥がれ落ちる赤いデータの欠片
                for (int i = 0; i < 2; i++) {
                    level().addParticle(MementoMoriEntity.glow(random.nextInt(3) == 0 ? MAGENTA : RED, 0.12F + random.nextFloat() * 0.1F, 24),
                            getRandomX(0.9), getY() + 0.2 + random.nextDouble() * 2.5, getRandomZ(0.9), random.nextGaussian() * 0.03, 0.05 + random.nextDouble() * 0.04, random.nextGaussian() * 0.03);
                }
            } else if (d >= 45 && d < 130 && d % 2 == 0) {
                level().addParticle(MementoMoriEntity.glow(d < 90 ? RED : WHITE, 0.2F, 20), getRandomX(1.0), getY() + random.nextDouble() * 2.6, getRandomZ(1.0), 0, 0.04, 0);
            }
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        int d = deathTime;
        Vec3 c = position().add(0, CHEST, 0);
        if (d == 1) {
            cleanup(server);
            playSound(SoundEvents.WITHER_DEATH, 5.0F, 0.5F);
            playSound(SoundEvents.BELL_RESONATE, 4.0F, 0.4F);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            Fx.shockwave(server, c, RED, 10.0F, 20);
        }
        if (d < 30) setDeltaMovement(Vec3.ZERO);
        if (d > 1 && d < 40 && d % 5 == 0) playSound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.5F, 0.5F + d * 0.02F);   // 欠片が剥がれる音
        if (d == 40) {                                                                     // 光輪が砕ける
            Vec3 halo = haloPos();
            Fx.burst(server, MementoMoriEntity.glow(0xFF6AB0, 0.3F, 26), halo, 40, 0.3);
            Fx.burst(server, MementoMoriEntity.glow(RED, 0.25F, 30), halo, 24, 0.15);
            Fx.shockwave(server, halo, MAGENTA, 4.0F, 14);
            playSound(SoundEvents.GLASS_BREAK, 4.0F, 0.5F);
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 4.0F, 0.6F);
        }
        if (d >= 50 && d < 110) {
            setDeltaMovement(0, -0.03, 0);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        }
        if (d == 70) playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.6F);               // 面頬の赤い光が消える
        if (d >= 70 && d < 100 && d % 3 == 0) {                                            // 顔からこぼれる白い光（怨嗟が抜ける）
            Vec3 face = position().add(0, 2.3 - Math.min(1.0, (d - 50) / 60.0) * 0.6, 0);
            Fx.spray(server, MementoMoriEntity.glow(WHITE, 0.15F, 22), face, 3, 0.2, 0.02);
        }
        if (d == 100) speak("defeat", ChatFormatting.WHITE, ChatFormatting.ITALIC);
        if (d >= 110 && d < 140 && d % 2 == 0) {                                           // 白い光が胸へ集まる
            Fx.gather(server, MementoMoriEntity.glow(WHITE, 0.3F, 14), c, 6, 5.0 + (140 - d) * 0.1, 14);
            if (d % 10 == 0) playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.8F + (d - 110) * 0.03F);
        }
        if (d == 140) {
            Fx.burst(server, MementoMoriEntity.glow(WHITE, 0.8F, 30), c, 80, 0.45);
            Fx.burst(server, MementoMoriEntity.glow(0xFFD0E0, 0.4F, 34), c, 40, 0.2);
            Fx.shockwave(server, position().add(0, 1.0, 0), WHITE, 24.0F, 34);
            Fx.shockwave(server, c, 0xFFD0E0, 14.0F, 28);
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, position(), 2.4F, 0, 0, 40, WHITE);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            playSound(SoundEvents.BELL_RESONATE, 5.0F, 1.4F);
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0F, 1.2F);
        }
        if (d >= DEFEAT_TICKS && !DirectRead.isRemoved(this)) {
            ensureLoot();
            com.automatavalkyries.world.ZeroGrave.onBossDefeated(server);
            protect.finishDeath();
            remove(RemovalReason.KILLED);
        }
    }

    void cleanup(ServerLevel server) {
        strikes.clear();
        holeLeft = 0;
        horizonLeft = 0;
        entityData.set(HORIZON, 0.0F);
        entityData.set(FINALE, 0);
        entityData.set(CHANT, 0);
        if (TimeStop.active()) TimeStop.stop();
        HACKED.clear();
        CausalLock.releaseBy(this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof LostCatastropheEntity zero)) return;
        if (event.isCanceled()) event.setCanceled(false);
        if (!event.getDrops().isEmpty()) zero.lootDropped = true;
    }

    /** 回想のトライアル「零の追憶」のボスにする（自分では退かず、保存もしない。技のダメージは attack 倍。タイムルーラーと同じ） */
    public void setTrialBoss(float attack) {
        this.trialBoss = true;
        this.trialAttack = attack;
    }

    @Override
    public boolean shouldBeSaved() {
        return !trialBoss && super.shouldBeSaved();
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    /** 出現の演出を飛ばす（GameTest 用） */
    public void skipIntro() {
        introDone = true;
        introTicks = INTRO_TICKS;
    }

    @Override
    public Component getDefeatSpeech() {
        return Component.translatable("entity.automata_valkyries.lost_catastrophe.retreat").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
    }

    // ------------------------------------------------------------------ Eprotect・ボスバー・保存

    @Override
    public Eprotect getEprotect() {
        return protect;
    }

    @Override
    public void restoreGoalsIfEmpty() {
        if (goalSelector.getAvailableGoals().isEmpty()) registerGoals();
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

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDefeated();
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

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", (byte) phase());
        tag.putBoolean("IntroDone", introDone);
        tag.putBoolean("Chant2", chant2Done);
        tag.putBoolean("Chant3", chant3Done);
        tag.putBoolean("FinaleDone", finaleDone || finaleTicks() > 0);                  // 終幕の途中で保存されたら、読み直した時は済んだことにする
        BlockPos rift = riftAnchor();
        if (rift != null) tag.putLong("Rift", rift.asLong());
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase")) entityData.set(PHASE, (byte) Math.max(1, Math.min(3, tag.getByte("Phase"))));
        introDone = tag.getBoolean("IntroDone");
        chant2Done = tag.getBoolean("Chant2");
        chant3Done = tag.getBoolean("Chant3");
        finaleDone = tag.getBoolean("FinaleDone");
        if (tag.contains("Rift")) entityData.set(RIFT, java.util.Optional.of(BlockPos.of(tag.getLong("Rift"))));
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ANVIL_LAND;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ------------------------------------------------------------------ 戦い方

    /**
     * 相手の斜め上に浮かび（詠唱の間は闘技場の真ん中の上）、間の明けた技のうち、いちばん長く使っていないものを選ぶ（タイムルーラーと同じ）。
     * 詠唱の間は技の間が倍、動けない間は何もしない
     */
    static class ZeroGoal extends Goal {
        private final LostCatastropheEntity boss;
        private int repath;
        @Nullable
        private LivingEntity cur;

        private static final class Move {
            final int minPhase, base;
            int cd, last = Integer.MIN_VALUE / 2;
            final Runnable act;

            Move(int minPhase, int base, int first, Runnable act) {
                this.minPhase = minPhase;
                this.base = base;
                this.cd = first;
                this.act = act;
            }
        }

        private final List<Move> moves = new ArrayList<>();

        ZeroGoal(LostCatastropheEntity boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            moves.add(new Move(1, 200, 40, () -> boss.barrage(cur, boss::lightSlash)));
            moves.add(new Move(1, 260, 120, () -> boss.barrage(cur, boss::siegeBeam)));
            moves.add(new Move(1, 220, 80, boss::butterflySpiral));
            moves.add(new Move(1, 520, 300, () -> boss.barrage(cur, boss::causalBlade)));
            moves.add(new Move(1, 300, 160, () -> boss.barrage(cur, boss::glitchRain)));
            moves.add(new Move(2, 420, 0, () -> boss.blackHole(cur)));
            moves.add(new Move(2, 600, 0, boss::mindHack));
            moves.add(new Move(2, 700, 0, boss::timeStop));
            moves.add(new Move(3, 380, 0, () -> boss.zeroFall(cur)));                  // 終末の技（最終形態だけ。2026-10-26）
            moves.add(new Move(3, 660, 0, boss::horizon));
            moves.add(new Move(3, 600, 0, () -> boss.requiemEchoes(cur)));
            moves.add(new Move(1, 240, 60, () -> boss.barrage(cur, boss::crimsonThunder)));   // 赤黒い天災の技（2026-10-26）
            moves.add(new Move(1, 340, 200, () -> boss.doomTide(cur)));
            moves.add(new Move(1, 380, 240, () -> boss.calamityStorm(cur)));
            moves.add(new Move(1, 280, 100, () -> boss.purgatoryFlame(cur)));
            moves.add(new Move(2, 480, 0, () -> boss.thunderCross(cur)));
            moves.add(new Move(2, 440, 0, () -> boss.crimsonDeluge(cur)));
            moves.add(new Move(3, 640, 0, () -> boss.apocalypseTempest(cur)));
            moves.add(new Move(3, 520, 0, () -> boss.heavenfall(cur)));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive() && boss.introDone && !boss.isDefeated() && !boss.retreating;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = boss.getTarget();
            if (t == null) return;
            boss.getLookControl().setLookAt(t, 30.0F, 30.0F);
            for (Move m : moves) m.cd--;
            if (--repath <= 0) {
                repath = 20;
                Vec3 spot;
                BlockPos field = boss.fieldCenter();
                if (boss.chantLeft() > 0 && boss.home != null) spot = boss.home.add(0, 6.0, 0);   // 詠唱は闘技場の真ん中の上で
                else if (field != null && boss.finaleTicks() > 0) spot = Vec3.atBottomCenterOf(field).add(0, 9.0, 0);   // 終幕は高く昇る
                else if (field != null && boss.horizonLeft > 0) spot = Vec3.atBottomCenterOf(field).add(0, 3.5, 0);    // 事象の地平は真ん中に留まる
                else {
                    Vec3 away = boss.position().subtract(t.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
                    boolean grounded = com.automatavalkyries.entity.FateDuels.dueling(boss) && !(t instanceof Player);
                    spot = t.position().add(away.normalize().scale(grounded ? 6.0 : 11.0)).add(0, grounded ? 0.0 : 3.0, 0);
                }
                boss.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
            }
            if (TimeStop.active() || boss.stunLeft() > 0) return;
            if (boss.castLock > 0 || boss.gap > 0) return;
            cur = t;
            Move pick = null;
            for (Move m : moves) {
                if (boss.phase() < m.minPhase || m.cd > 0) continue;
                if (pick == null || m.last < pick.last || (m.last == pick.last && boss.random.nextBoolean())) pick = m;
            }
            if (pick == null) return;
            pick.cd = boss.cd(pick.base);
            pick.last = boss.tickCount;
            boss.gap = boss.cd(boss.chantLeft() > 0 ? 32 : 16);
            pick.act.run();
        }
    }
}
