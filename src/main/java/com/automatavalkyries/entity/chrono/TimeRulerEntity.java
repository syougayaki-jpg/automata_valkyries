package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.world.TimeRift;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
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
import java.util.Iterator;
import java.util.List;

/**
 * 総司令機【タイムルーラー】（全体仕様書 §4.3。2026-10-19 ユーザー指定「クロノルーラーを制作」。質問で: 時計の機械神・専用の次元「時の狭間」・本当に世界ごと止める（8 秒、
 * 特異点コアの兵姫と持ち主は動ける）・HP 80,000 の 3 形態・落とし物は心臓・針・スター。数値は【仮】）。
 * 白と金の鎧に紫のマントの騎士の姿の、浮かぶ時計の機械神（背に巨大な文字盤、宝石の冠と 3 つの光輪。見た目は tools/gen_time_ruler.py）。即死系の守りは付けるが被弾の上限は無し（AEC 級と同じ）。
 * <ul>
 *   <li>剪定の針: 周りの針を相手へ（第 1 形態 6 本・第 2 形態 8・第 3 形態 12）。少し止まってから飛ぶ</li>
 *   <li>文字盤の掃射: 12 本の光線を腰の高さで回す（跳べば越えられる）</li>
 *   <li>時の楔: 相手のまわり 6 か所に輪、1.2 秒後に空から金の楔</li>
 *   <li>確定座標消去（第 2 形態から）: 相手のいる座標に光の立方体、2 秒後にその座標を消す（防具を通す {@value #ERASE}）。第 3 形態は 3 回続けて追う</li>
 *   <li>時針の一閃: 近い相手を前の半円で薙ぐ</li>
 *   <li>全世界時間停止（{@link TimeStop}）: 2 秒の鐘の予告の後、全ての次元を 8 秒止める。止まった時の中で、相手の背後へ跳び、相手のまわりに針の輪を並べる
 *       （時が動き出してから 1 秒後に飛ぶ）。第 2 形態からは座標消去の印も置く（動き出してから 1.5 秒後）。第 1 形態は 45 秒ごと、第 2 は 40、第 3 は 30</li>
 *   <li>因果逆転（第 2・第 3 形態に入るときに 1 度ずつ）: 8 秒の詠唱の間に因果の歯車 4 つ（{@link RulerAnchorEntity}）が浮かぶ。1 つでも残っていれば体力が 20 秒前へ巻き戻る。
 *       全部壊せば詠唱は崩れ、6 秒の間 受けるダメージ 1.5 倍</li>
 *   <li>刻の終焉（第 3 形態で HP 10% を切ったら 1 度だけ、19 秒）: 床に時計の長針と短針の光が回り（低いので跳べば越えられる）、時の楔が降る。その間は受けるダメージ半分。
 *       2026-10-20 ユーザー指定「時の終焉で全ての技を使うようにして」で、その間に全ての技を間を無視して 0.6 秒ごとに 1 つずつ（順は乱数）使い、最後に全世界時間停止（{@code RulerGoal}）</li>
 * </ul>
 * 2026-10-20 ユーザー指定「更にラスボスらしく多彩な技と、遅いが喰らってはいけない因果停止＋超貫通ダメージの即死級の技を追加して」で足した技（質問で決めた）:
 * <ul>
 *   <li>因果断ちの大剣: 空に立つ巨大な時空剪定の針が {@value #BLADE_SWING} Tick かけて相手の居た線へ倒れる。床の赤い帯（幅 {@value #BLADE_HALF_WIDTH} の倍）の上に居た者は
 *       {@link CausalLock 因果停止}（{@value #LOCK_TICKS} Tick 動けず回復もせず、明けて最大 HP の 2 倍の超貫通ダメージ。不死のトーテムだけが効く）。第 3 形態は 2 本目が別の向きから</li>
 *   <li>残像の分身（第 2 形態から）: 過去の自分の残像 {@link RulerEchoEntity} を 2 体（第 3 形態 3 体）呼び、剪定の針・時針の一閃・巻き戻しの弾を少し遅れてなぞらせる</li>
 *   <li>時の歯車の挟撃: 巨大な歯車が 2 つ、床を転がって左右から相手の所で挟み、かみ合うと砕けて針が輪に散る。第 3 形態は直角にもう 1 組</li>
 *   <li>加速と減速の場（第 2 形態から）: 相手の足元に青い減速の文字盤、まわりに赤い加速の文字盤。減速の中は鈍り、加速の中は速く動けるが、時間差の技が倍の速さで落ち、楔が追う</li>
 *   <li>巻き戻しの弾: 扇に放った針が通り過ぎてから止まり、逆再生のように同じ道を戻ってくる</li>
 * </ul>
 * 2026-10-20 ユーザー指定「形態変化ごとに大技一つと技を四つ追加して」（質問で: 第 2・第 3 形態に入るごとに加わる・大技は何度でも）:
 * 第 2 形態から 大技「逆巻く時の大渦」・振り子の断頭台・砂時計の牢・時針の三連・時限の刻印、
 * 第 3 形態から 大技「終末時計」・双子の文字盤・時空疾走・崩れる文字盤・時の雨
 * 倒れると文字盤が割れて歯車が落ち、砕けて光になる。落とし物は loot_tables/entities/time_ruler、時の狭間なら帰りの門が開く（{@link TimeRift}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class TimeRulerEntity extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech, com.automatavalkyries.entity.RetreatableBoss {

    public static final float MAX_HP = 80000.0F, NEEDLE = 10.0F, STOP_NEEDLE = 9.0F, SWEEP = 8.0F, SPIKE = 14.0F, ERASE = 40.0F, SLASH = 20.0F, HANDS = 12.0F;
    public static final int STOP_TICKS = 160, STOP_WARN = 40, REWIND_TICKS = 160, FINALE_TICKS = 380, INTRO_TICKS = 120, DEFEAT_TICKS = 160, RETREAT_TICKS = 60;
    public static final ResourceKey<DamageType> ERASURE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "time_erasure"));
    public static final byte EVENT_NEEDLE = 90, EVENT_SWEEP = 91, EVENT_STOP = 92, EVENT_ERASE = 93, EVENT_REWIND = 94, EVENT_SLASH = 95, EVENT_FINALE = 96, EVENT_INTRO = 97, EVENT_RETREAT = 98,
            EVENT_BLADE = 99, EVENT_GEAR = 100, EVENT_FIELD = 101, EVENT_ECHO = 102, EVENT_MAEL = 103, EVENT_DOOM = 104;
    /** 第 2・第 3 形態で加わる技（2026-10-20。数値は【仮】） */
    public static final int MAEL_TICKS = 160, PEND_WARN = 20, PEND_PERIOD = 50, PRISON_TICKS = 60, DOOM_TICKS = 20 + 11 * 14 + 30;
    public static final float MAEL_CRUSH = 45.0F, PENDULUM_HIT = 18.0F, PRISON_HIT = 30.0F, MARK_HIT = 16.0F, DOOM_HIT = 60.0F, DASH_HIT = 26.0F, SECTOR_HIT = 18.0F, RAIN_HIT = 12.0F;
    public static final float PEND_LEN = 12.0F, PEND_AMP = 1.22F;
    static final double MAEL_SPIN = 0.035, PRISON_R = 5.0, SECTOR_R = 10.0;
    /** 因果断ちの大剣（2026-10-20）: 倒れるまでの Tick（第 3 形態の 2 本目は短い）・長さ・床の帯の半分の幅・因果停止の Tick */
    public static final int BLADE_SWING = 100, BLADE_SWING_2 = 80, LOCK_TICKS = 40;
    public static final float BLADE_LEN = 40.0F, BLADE_HALF_WIDTH = 2.2F, GEAR = 16.0F, GEAR_MESH = 20.0F, GEAR_RADIUS = 3.2F;
    static final int GOLD = 0xFFD166, PALE = 0xFFF0B0, INDIGO = 0x6A5CFF, VIOLET = 0xB070FF, WHITE = 0xFFFFFF, RED = 0xFF4060;

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(TimeRulerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(TimeRulerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> REWINDING = SynchedEntityData.defineId(TimeRulerEntity.class, EntityDataSerializers.BOOLEAN);

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent;
    private final com.automatavalkyries.entity.BossRetreatWatch retreatWatch = new com.automatavalkyries.entity.BossRetreatWatch(64.0, 200);

    /** 描く側の技の時刻（エンティティの出来事で届く） */
    public int needleTick = -100, sweepTick = -100, stopTick = -100, eraseTick = -100, rewindTick = -100, slashTick = -100, finaleTick = -100;
    public int bladeTick = -200, gearTick = -100, fieldTick = -100, echoTick = -100, maelTick = -400, doomTick = -400;
    /** 出現・撤退の演出が始まった時刻（描く側。始まっていなければずっと前） */
    public int introTick = -10000, retreatTick = -10000;

    private boolean trialBoss, introDone, lootDropped, rewind2Done, rewind3Done, finaleDone, retreating;
    private float trialAttack = 1.0F;
    private int introTicks, retreatTicks;
    /** 1 秒ごとの体力の記録（因果逆転で 20 秒前へ戻すため） */
    private final float[] history = new float[30];
    private int historyAt;
    private int rewindLeft, staggerLeft, finaleLeft, stopCd = 500, castLock;
    /** 時を止めた時刻（止まった時の中の段取りの時計。止まっていなければ -1） */
    private int stopAt = -1;
    private double floorY = Double.NaN;

    /** 時間差で起きる技 */
    private record Strike(int kind, Vec3 at, int fire, @Nullable Entity follow) {}

    private static final int SPIKE_FALL = 0, ERASE_FALL = 1, STOP_BEGIN = 2, SLASH_HIT = 3, SLASH_SPIN = 4, JUDGE_FALL = 5,
            BLADE_CAST = 6, GEAR_CAST = 7, ECHO_NEEDLE = 8, ECHO_SLASH = 9, ECHO_SLASH_HIT = 10, ECHO_RSHOT = 11,
            MARK_BLAST = 12, DASH_BLINK = 13, PRISON_FALL = 14, DOOM_CHIME = 15, DOOM_FINAL = 16, TWIN_SHOT = 17, DASH_AIM = 18, DASH_GO = 19, RAIN_SPAWN = 20, RAIN_FALL = 21;
    private final List<Strike> strikes = new ArrayList<>();
    /** 倒れていく因果断ちの大剣（根元・向き・倒れる時刻） */
    private record Blade(Vec3 pivot, double yaw, int fire) {}
    private final List<Blade> blades = new ArrayList<>();
    /** 転がる時の歯車の組（2 つのエフェクト・挟む所・転がる Tick）。当たりはエフェクトの実際の位置で（時間停止の間は歯車も止まる） */
    private static final class GearPair {
        final RulerFxEntity a, b;
        final Vec3 meet;
        final int travel;

        GearPair(RulerFxEntity a, RulerFxEntity b, Vec3 meet, int travel) {
            this.a = a;
            this.b = b;
            this.meet = meet;
            this.travel = travel;
        }
    }
    private final List<GearPair> gears = new ArrayList<>();
    /** 加速（赤）と減速（青）の場 */
    private record Field(Vec3 c, double r, boolean haste, int until) {}
    private final List<Field> fields = new ArrayList<>();
    /** 呼んだ残像の分身 */
    private final List<RulerEchoEntity> echoes = new ArrayList<>();
    /** 逆巻く時の大渦（残り Tick・真ん中・時針の角度）、振り子のエフェクト、崩れる文字盤の扇（真ん中・向き・崩れる時刻）、終末時計の打った数 */
    private int maelLeft, doomChime;
    private Vec3 maelCenter = Vec3.ZERO;
    private double maelAngle;
    private final List<RulerFxEntity> pendulums = new ArrayList<>();
    private record Sector(Vec3 c, double a, int fire) {}
    private final List<Sector> sectors = new ArrayList<>();
    /** 掃射の状態（残り Tick と回る向き） */
    private int sweepLeft, sweepWarn;
    /** 掃射の刃の長さ（第 2 形態から 30）と、第 3 形態の逆へ回る 2 組目の刃 */
    private double sweepLen = 24, sweepAngle2, sweepSpin2;
    private boolean dualSweep;
    /** 時間停止が明けたら十二時の審判を落とす所（第 3 形態） */
    private final List<Vec3> afterStop = new ArrayList<>();
    private double sweepAngle, sweepSpin;
    /** 掃射・刻の終焉の中心（始めた時に決めて動かさない。光の刃のエフェクトと当たりを合わせる） */
    private Vec3 sweepCenter = Vec3.ZERO, finaleCenter = Vec3.ZERO;
    /** 消去の印の立方体のエフェクトを出した座標（落ちたら外す） */
    private final java.util.Set<Vec3> eraseMarked = new java.util.HashSet<>();
    private RulerFxEntity rewindFx;

    public TimeRulerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new HoverMoveControl(this);
        this.bossEvent = new ServerBossEvent(Component.translatable(type.getDescriptionId()), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);
        this.xpReward = 2000;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new RulerGoal(this));
        ChronoGazer.addTargets(this, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, (byte) 1);
        entityData.define(DEFEATED, false);
        entityData.define(REWINDING, false);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public boolean isDefeated() {
        return entityData.get(DEFEATED);
    }

    public boolean isRewinding() {
        return entityData.get(REWINDING);
    }

    int cd(int ticks) {
        return switch (phase()) {
            case 3 -> ticks * 6 / 10;
            case 2 -> ticks * 8 / 10;
            default -> ticks;
        };
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_NEEDLE -> needleTick = tickCount;
            case EVENT_SWEEP -> sweepTick = tickCount;
            case EVENT_STOP -> stopTick = tickCount;
            case EVENT_ERASE -> eraseTick = tickCount;
            case EVENT_REWIND -> rewindTick = tickCount;
            case EVENT_SLASH -> slashTick = tickCount;
            case EVENT_FINALE -> finaleTick = tickCount;
            case EVENT_INTRO -> {
                introTick = tickCount;
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.automatavalkyries.client.ClientRulerShow.intro(this, 90));                  // 題字は光の柱と一緒に
            }
            case EVENT_RETREAT -> retreatTick = tickCount;
            case EVENT_BLADE -> bladeTick = tickCount;
            case EVENT_GEAR -> gearTick = tickCount;
            case EVENT_FIELD -> fieldTick = tickCount;
            case EVENT_ECHO -> echoTick = tickCount;
            case EVENT_MAEL -> maelTick = tickCount;
            case EVENT_DOOM -> doomTick = tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    // ---- 多人数（2026-10-20 ユーザー指定「多人数用にプレイヤーや戦術兵姫の人数にあわせて弾幕を張るようにして」） ----

    /** 主な相手のほかに弾幕を向ける数の上限 */
    public static final int MULTI_MAX = 5;
    /** 多人数の弾幕の 2 人目から（名乗り・残像のなぞりを重ねない） */
    boolean quiet;

    /** 主な相手のほかに、近く（48 m）で戦っているプレイヤー（クリエイティブ・観戦は除く）と戦術兵姫（機能停止は除く）。近い順に {@value #MULTI_MAX} まで */
    public List<LivingEntity> otherFoes(LivingEntity main) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48.0),
                e -> e != main && ChronoGazer.canHurt(e) && (e instanceof Player || e instanceof com.automatavalkyries.entity.ValkyrieEntity v && !v.isShutdown()))) {
            out.add(e);
        }
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(this)));
        return out.size() > MULTI_MAX ? out.subList(0, MULTI_MAX) : out;
    }

    /** 主な相手へ技を使い、近くで戦っている他の者にも同じ弾幕を（名乗りは 1 度だけ） */
    void barrage(LivingEntity main, java.util.function.Consumer<LivingEntity> move) {
        move.accept(main);
        List<LivingEntity> others = otherFoes(main);
        if (others.isEmpty()) return;
        int lock = castLock;
        quiet = true;
        try {
            for (LivingEntity e : others) move.accept(e);
        } finally {
            quiet = false;
            castLock = lock;
        }
    }

    // ---- 落とし物は浮く（2026-10-20 ユーザー指定「素材が落ちないようにドロップアイテムが空中に浮くようにして」） ----

    /** 落とし物（倒れた時のも、演出の後で出すのも）は重さを無くして宙に浮かせ、光らせ、消えないようにする（時の狭間の奈落へ落ちない） */
    @Override
    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(net.minecraft.world.item.ItemStack stack, float yOffset) {
        net.minecraft.world.entity.item.ItemEntity item = super.spawnAtLocation(stack, yOffset);
        if (item != null) {
            item.setNoGravity(true);
            item.setDeltaMovement((random.nextDouble() - 0.5) * 0.12, 0.02, (random.nextDouble() - 0.5) * 0.12);   // 少し散って止まる
            item.setUnlimitedLifetime();
            item.setGlowingTag(true);
        }
        return item;
    }

    /** 技のダメージ（プレイヤーは最大 HP に応じて上げ、回想のトライアルでは倍率） */
    float damageFor(LivingEntity target, float damage) {
        return LuxionEntity.scaledFor(target, damage * trialAttack);
    }

    /**
     * セリフ（2026-10-19 ユーザー指定「撤退と撃破のセリフを設定して」）: 名もかぎかっこも付けない地の文を金の斜体でチャットに。
     * 撤退（相手がいなくなって退く）は {@link #getDefeatSpeech}（正規退場がサーバー全体へ）、撃破は {@link #speak}（近くの者へ）
     */
    Component line(String key) {                                                   // 地の文（2026-10-19 ユーザー指定「セリフではなく地の文で」）: 名前もかぎかっこも付けない
        return Component.translatable("entity.automata_valkyries.time_ruler." + key).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
    }

    void speak(ServerLevel server, String key) {
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 128 * 128)) p.sendSystemMessage(line(key));
    }

    void say(String key, ChatFormatting... style) {
        if (quiet || !(level() instanceof ServerLevel server)) return;            // 多人数の弾幕の 2 人目からは名乗らない
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 96 * 96)) {
            p.displayClientMessage(Component.translatable("entity.automata_valkyries.time_ruler." + key).withStyle(style), true);
        }
    }

    // ------------------------------------------------------------------ 毎 Tick

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            protect.clientTick();
            if (!isDefeated() && random.nextInt(2) == 0) { // まとわりつく紫・水色・金の光の粒（2026-10-19 参考の絵）
                int[] colors = {VIOLET, 0x7FE4FF, GOLD, 0xD8B0FF};
                double a = random.nextDouble() * Math.PI * 2, r = 1.6 + random.nextDouble() * 2.4;
                level().addParticle(MementoMoriEntity.glow(colors[random.nextInt(colors.length)], 0.12F + random.nextFloat() * 0.1F, 30),
                        getX() + Math.cos(a) * r, getY() + 1.0 + random.nextDouble() * 4.5, getZ() + Math.sin(a) * r, 0, 0.01, 0);
            }
            return;
        }
        protect.tick();
        float ratio = protect.healthRatio();
        bossEvent.setProgress(isDefeated() || deathTime > 0 ? 0.0F : ratio);
        if (!isAlive() || isDefeated()) return;
        if (tickCount % 20 == 0) history[historyAt++ % history.length] = getHealth();
        if (phase() == 1 && ratio <= 2.0F / 3.0F) enterPhase(2);
        else if (phase() == 2 && ratio <= 1.0F / 3.0F) enterPhase(3);
        if (phase() == 3 && !finaleDone && ratio <= 0.1F && rewindLeft <= 0) startFinale();
        if (!trialBoss && !retreating && retreatWatch.tick(this)) beginRetreat();
    }

    void enterPhase(int to) {
        entityData.set(PHASE, (byte) to);
        bossEvent.setColor(to == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
        say("phase" + to, ChatFormatting.GOLD, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_RESONATE, 4.0F, to == 2 ? 0.8F : 0.6F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(3.0), getZ(), 1, 0, 0, 0, 0);
            Fx.shockwave(server, position().add(0, 1.0, 0), GOLD, 14.0F, 24);
        }
        if ((to == 2 && !rewind2Done) || (to == 3 && !rewind3Done)) {
            if (to == 2) rewind2Done = true;
            else rewind3Done = true;
            startRewind();
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
            eraseMarked.clear();
            afterStop.clear();
            blades.clear();
            gears.clear();
            fields.clear();
            sectors.clear();
            pendulums.clear();
            maelLeft = 0;
            return;
        }
        if (castLock > 0) castLock--;
        if (staggerLeft > 0 && --staggerLeft % 10 == 0) Fx.spray(server, MementoMoriEntity.glow(VIOLET, 0.25F, 10), position().add(0, 3.0, 0), 6, 1.0, 0.05);
        tickStopRoutine(server);
        tickSweep(server);
        tickRewind(server);
        tickFinale(server);
        tickStrikes(server);
        tickBlades(server);
        tickGears(server);
        tickFields(server);
        tickEchoes(server);
        tickMaelstrom(server);
        tickPendulums(server);
        tickSectors(server);
        if (!afterStop.isEmpty() && !TimeStop.active()) {
            for (Vec3 at : afterStop) judgment(at, 10);
            afterStop.clear();
        }
    }

    /**
     * 相手がいる間は、体・頭・向きをいつも相手へ（1 Tick に {@value #TURN} 度まで、なめらかに）。
     * 移動（{@link HoverMoveControl}）は向きを変えないので、回り込んでも背を向けず、頭と体が逆を向いてがくがくしない
     * （2026-10-19 ユーザー指定「攻撃している時背を向けてがくがくする」）
     */
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
        double dy = t.getEyeY() - getEyeY(), flat = Math.sqrt(dx * dx + dz * dz);
        setXRot(net.minecraft.util.Mth.approachDegrees(getXRot(), (float) -(net.minecraft.util.Mth.atan2(dy, flat) * (180.0 / Math.PI)), TURN));
    }

    /** 浮かんで動く（向きは変えない。目標へなめらかに加速し、近づくと減速して止まる。行き過ぎて戻る揺れを抑える） */
    static class HoverMoveControl extends MoveControl {
        HoverMoveControl(TimeRulerEntity mob) {
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
            double top = 0.32 * speedModifier;                                   // 目標へ向かう速さ（近いほど遅く）
            Vec3 want = to.scale(Math.min(top, d * 0.08) / d);
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.add(want.subtract(v).scale(0.15)));           // なめらかに近づける
            mob.setSpeed(0.0F);
        }
    }

    // ---- 現れる・退く ----

    /**
     * 出現の演出（{@value #INTRO_TICKS} Tick、受けない。2026-10-19 ユーザー指定「出現演出まで自作して作りこんで」）:
     * 0〜24 鐘が 3 つ鳴り、床に巨大な文字盤の魔法陣（金）と紫の魔法陣が広がる。0〜50 金と藍の光が渦を巻いて集まり、昇る光の輪。
     * 20〜55 体が足元から組み上がる（描く側で骨を大きくする）、組み立ての音。50〜75 背の文字盤が針を速く回しながら展開する（鐘の上がる音）。
     * 60〜80 マントがほどける。70 面頬が灯る。75 光輪がはまる。90 光の柱・大きな衝撃波・閃光と題字（描く側）。90〜105 交差した腕を広げて構える
     */
    void tickIntro(ServerLevel server) {
        int t = introTicks++;
        Vec3 feet = position(), c = feet.add(0, 3.0, 0);
        if (t == 0) level().broadcastEntityEvent(this, EVENT_INTRO);
        if (t == 0 || t == 12 || t == 24) playSound(SoundEvents.BELL_BLOCK, 4.0F, 0.45F + t * 0.008F);
        if (t == 2) Fx.mandala(server, feet.add(0, 0.15, 0), GOLD, 11.0F, 115, false, true);                // 床の巨大な文字盤
        if (t == 14) Fx.mandala(server, feet.add(0, 0.2, 0), VIOLET, 7.0F, 100, false, false);
        if (t < 50 && t % 2 == 0) {                                                                        // 渦を巻いて集まる光
            for (int i = 0; i < 6; i++) {
                double a = t * 0.3 + i * Math.PI / 3, r = 10.0 - t * 0.12;
                Vec3 p = c.add(Math.cos(a) * r, (i - 2.5) * 1.2, Math.sin(a) * r);
                Fx.one(server, MementoMoriEntity.glow(i % 2 == 0 ? GOLD : INDIGO, 0.4F, 16), p, c.subtract(p).scale(1.0 / 16));
            }
        }
        if (t < 50 && t % 8 == 0) Fx.beamRing(server, feet.add(0, 0.2 + t * 0.08, 0), new Vec3(0, 1, 0), GOLD, 5.5F, 14);   // 昇る光の輪
        if (t >= 20 && t < 60 && t % 4 == 0) {                                                             // 組み上がる体の火花と音
            Fx.burst(server, MementoMoriEntity.glow(PALE, 0.25F, 10), feet.add(0, (t - 20) * 0.15, 0), 8, 0.15);
            playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 1.2F + (t - 20) * 0.01F);
        }
        if (t >= 50 && t <= 74 && t % 4 == 2) playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2.5F, 0.6F + (t - 50) * 0.05F);   // 文字盤の展開
        if (t == 70) {                                                                                     // 面頬が灯る
            playSound(SoundEvents.BEACON_ACTIVATE, 2.5F, 1.4F);
            Fx.burst(server, MementoMoriEntity.glow(0x7FE4FF, 0.3F, 12), position().add(0, 4.2, 0), 16, 0.12);
        }
        if (t == 75) {                                                                                     // 光輪がはまる
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 1.6F);
            Fx.beamRing(server, position().add(0, 5.1, 0), new Vec3(0, 1, 0), GOLD, 2.0F, 12);
        }
        if (t == 90) {                                                                                     // 光の柱・衝撃波・閃光（題字は描く側）
            Fx.line(server, MementoMoriEntity.glow(GOLD, 0.7F, 30), feet, feet.add(0, 40, 0), 0.5);
            Fx.shockwave(server, feet.add(0, 0.3, 0), GOLD, 18.0F, 30);
            Fx.shockwave(server, c, VIOLET, 10.0F, 24);
            Fx.burst(server, MementoMoriEntity.glow(PALE, 0.6F, 26), c, 60, 0.35);
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(3.0), getZ(), 1, 0, 0, 0, 0);
            playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.9F);
            playSound(SoundEvents.END_PORTAL_SPAWN, 2.0F, 1.4F);
            say("intro", ChatFormatting.GOLD, ChatFormatting.ITALIC);
        }
        if (t >= INTRO_TICKS) introDone = true;
    }

    /**
     * 撤退の演出（{@value #RETREAT_TICKS} Tick。2026-10-19 ユーザー指定「退場演出まで自作して作りこんで」）: 片手を掲げ、背の文字盤を逆へ速く回す。
     * 時が巻き戻るように光が逆に吸い込まれ（逆回りの魔法陣・下がっていく時計の音）、30〜52 で体が縮んで回りながら点になり、55 で閃光とともに消える。
     * 消えるとき、地の文がサーバー全体に出る（{@link #getDefeatSpeech}）
     */
    @Override
    public boolean beginRetreat() {
        if (!isAlive() || isDefeated() || retreating || protect.isDespawning()) return false;
        retreating = true;
        retreatTicks = 0;
        level().broadcastEntityEvent(this, EVENT_RETREAT);
        playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
        return true;
    }

    void tickRetreat(ServerLevel server) {
        int t = retreatTicks++;
        Vec3 c = position().add(0, 3.0, 0);
        if (t == 4) Fx.mandala(server, position().add(0, 0.2, 0), INDIGO, 8.0F, 50, false, false);       // 逆回りの魔法陣
        if (t < 50 && t % 2 == 0) Fx.gather(server, MementoMoriEntity.glow(t % 4 == 0 ? GOLD : INDIGO, 0.35F, 12), c, 6, 6.0, 12);   // 逆に吸い込まれる光
        if (t < 50 && t % 6 == 0) playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.5F, 1.8F - t * 0.025F);  // 下がっていく時計の音
        if (t >= 30 && t < 52 && t % 3 == 0) Fx.burst(server, MementoMoriEntity.glow(PALE, 0.3F, 10), c, 6, 0.08);
        if (t == 55) {
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            Fx.shockwave(server, c, GOLD, 8.0F, 16);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 3.0F, 0.5F);
            playSound(SoundEvents.BELL_BLOCK, 3.0F, 1.6F);
        }
        if (t >= RETREAT_TICKS) {
            cleanup(server);
            protect.triggerDespawn();
        }
    }

    // ---- 技 ----

    /** 剪定の針: 周りの針を、相手の動きを少し読んだ所へ（2026-10-19「ラスボスらしく攻撃性能をもっとはでに」で 8 / 12 / 16 本に） */
    void needles(LivingEntity t) {
        needleRing(position(), t, phase() == 3 ? 16 : phase() == 2 ? 12 : 8);
        playSound(SoundEvents.TRIDENT_RIPTIDE_1, 2.0F, 1.6F);
        level().broadcastEntityEvent(this, EVENT_NEEDLE);
        if (!quiet) for (int i = 0; i < echoes.size(); i++) strikes.add(new Strike(ECHO_NEEDLE, Vec3.ZERO, tickCount + 12 + i * 8, echoes.get(i)));   // 残像が少し遅れてなぞる
    }

    /** center のまわりに n 本の針を並べ、t の動きを少し読んだ所へ */
    void needleRing(Vec3 center, LivingEntity t, int n) {
        Vec3 lead = t.position().add(t.getDeltaMovement().scale(8)).add(0, t.getBbHeight() * 0.5, 0);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + tickCount * 0.05;
            Vec3 from = center.add(Math.cos(a) * 3.2, 3.5 + Math.sin(a * 2) * 0.8, Math.sin(a) * 3.2);
            RulerNeedleEntity.place(this, from, lead, 8 + i * 2, NEEDLE * trialAttack, 1.6F);
        }
    }

    /**
     * 文字盤の針雨（第 2 形態から。2026-10-19「ラスボスらしく攻撃性能をもっとはでに」）: 背に相手へ面を向けた光の文字盤を広げ、XII から右回りに 1 時ずつ針を放つ。
     * 第 3 形態は外にもう 1 周（24 本、2 周目は逆回り）
     */
    void dialBarrage(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        float face = facing(t);
        Vec3 n = new Vec3(Math.cos(face), 0, Math.sin(face)), up = new Vec3(0, 1, 0), right = up.cross(n);
        Vec3 c = position().add(0, 5.0, 0).subtract(n.scale(1.5));
        int rings = phase() == 3 ? 2 : 1;
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FACING, c, rings == 2 ? 8.5F : 6.5F, face, 0, 70, RulerFxEntity.GOLD);
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0);
        for (int ring = 0; ring < rings; ring++) {
            double r = ring == 0 ? 5.5 : 7.5;
            for (int k = 0; k < 12; k++) {
                int h = ring == 0 ? k : (12 - k) % 12;
                double ang = Math.PI / 2 - Math.PI * 2 * h / 12;
                Vec3 from = c.add(right.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
                RulerNeedleEntity.place(this, from, aim, 12 + k * 3 + ring * 6, NEEDLE * trialAttack, 1.9F);
            }
        }
        castLock = 20;
        say("barrage", ChatFormatting.GOLD);
        playSound(SoundEvents.BELL_RESONATE, 3.0F, 1.2F);
        level().broadcastEntityEvent(this, EVENT_NEEDLE);
    }

    /**
     * 十二時の審判（第 2 形態から。2026-10-19「ラスボスらしく攻撃性能をもっとはでに」）: 相手のまわり半径 8 の 12 の時の所に XII から右回りに光の柱が落ち、
     * 最後に真ん中へ巨大な柱（半径 4）。逃げ道は時計の外か、柱の落ちた後
     */
    void judgment(Vec3 center, int delay) {
        if (!(level() instanceof ServerLevel server)) return;
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_TO_TWELVE, center.add(0, 0.1, 0), 9.5F, 0, 0, delay + 12 * 4 + 20, RulerFxEntity.VIOLET);
        for (int k = 0; k < 12; k++) {
            double ang = Math.PI / 2 - Math.PI * 2 * k / 12;
            Vec3 at = center.add(Math.cos(ang) * 8.0, 0, -Math.sin(ang) * 8.0); // 上から見て右回り（床の文字盤と同じ）
            strikes.add(new Strike(SPIKE_FALL, at, tickCount + delay + k * 4, null));
        }
        strikes.add(new Strike(JUDGE_FALL, center, tickCount + delay + 12 * 4 + 16, null));
        say("judge", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_BLOCK, 4.0F, 0.7F);
        level().broadcastEntityEvent(this, EVENT_ERASE);
    }

    /** 文字盤の掃射: 1 秒の予告の後、12 本の光線を回す */
    void sweep(LivingEntity t) {
        floorY = t.getY();
        sweepWarn = 20;
        sweepLeft = 60;
        sweepAngle = random.nextDouble() * Math.PI * 2;
        sweepSpin = (random.nextBoolean() ? 1 : -1) * (phase() == 3 ? 0.045 : 0.03);
        sweepLen = phase() >= 2 ? 30 : 24;
        dualSweep = phase() == 3;                                                    // 第 3 形態は逆へ回る 2 組目（間を半分ずらす）
        sweepAngle2 = sweepAngle + Math.PI / 12;
        sweepSpin2 = -sweepSpin * 0.8;
        sweepCenter = new Vec3(getX(), floorY + 1.0, getZ());
        if (level() instanceof ServerLevel server) {
            RulerFxEntity.spawn(server, RulerFxEntity.SWEEP, 0, sweepCenter, (float) sweepLen, (float) sweepAngle, (float) sweepSpin, 80, RulerFxEntity.GOLD);
            if (dualSweep) RulerFxEntity.spawn(server, RulerFxEntity.SWEEP, 0, sweepCenter, (float) sweepLen, (float) sweepAngle2, (float) sweepSpin2, 80, RulerFxEntity.GOLD);
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT, sweepCenter.add(0, -0.9, 0), (float) sweepLen + 0.5F, 0, 0, 80, RulerFxEntity.GOLD);
        }
        playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 1.6F);
        level().broadcastEntityEvent(this, EVENT_SWEEP);
    }

    void tickSweep(ServerLevel server) {
        if (sweepLeft <= 0 || TimeStop.active()) return;
        Vec3 o = sweepCenter;
        if (sweepWarn > 0) { // 予告の赤い線は RulerFxEntity が描く
            sweepWarn--;
            return;
        }
        sweepLeft--;
        sweepAngle += sweepSpin;
        sweepAngle2 += sweepSpin2;
        for (int i = 0; i < (dualSweep ? 24 : 12); i++) {
            double a = (i < 12 ? sweepAngle : sweepAngle2) + i * Math.PI / 6;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            if (tickCount % 6 == 0) Fx.one(server, MementoMoriEntity.glow(i % 3 == 0 ? WHITE : GOLD, 0.3F, 8), o.add(dir.scale(4 + random.nextDouble() * (sweepLen - 4))), Vec3.ZERO); // 刃に散る火の粉
            if (tickCount % 10 != 0) continue;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(sweepLen, 2, sweepLen), ChronoGazer::canHurt)) {
                Vec3 rel = e.position().subtract(o).multiply(1, 0, 1);
                double along = rel.dot(dir);
                if (along < 0 || along > sweepLen) continue;
                double side = rel.subtract(dir.scale(along)).length();
                boolean low = e.getBoundingBox().minY < o.y + 0.3 && e.getBoundingBox().maxY > o.y - 0.3; // 腰の高さ（跳べば越える）
                if (side < 0.8 && low) e.hurt(damageSources().mobAttack(this), damageFor(e, SWEEP));
            }
        }
    }

    /** 時の楔: 相手のまわり 6 / 9 / 12 か所（形態ごと） */
    void spikes(LivingEntity t) {
        int n = phase() == 3 ? 12 : phase() == 2 ? 9 : 6;
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : 2.0 + random.nextDouble() * (phase() == 3 ? 6.0 : 3.5);
            Vec3 at = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            strikes.add(new Strike(SPIKE_FALL, at, tickCount + 24 + i * 2, null));
            if (level() instanceof ServerLevel server) RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT, at.add(0, 0.12, 0), 2.2F, 0, 0, 24 + i * 2, RulerFxEntity.GOLD);
        }
        playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0F, 0.6F);
    }

    /** 確定座標消去: 相手のいる座標に印（第 2 形態は 2 回、第 3 形態は 4 回続けて追う） */
    void erase(LivingEntity t, int delay) {
        int n = phase() == 3 ? 4 : 2;
        for (int i = 0; i < n; i++) strikes.add(new Strike(ERASE_FALL, i == 0 ? t.position() : Vec3.ZERO, tickCount + delay + i * 16, i == 0 ? null : t));
        playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.8F);
        say("erase", ChatFormatting.RED);
        level().broadcastEntityEvent(this, EVENT_ERASE);
    }

    /** 時針の一閃（0.75 秒構えて、前の半円 5 m）。第 2 形態から返す刀の 2 撃目、第 3 形態は最後に長針と短針で半径 9 を一回りに薙ぐ */
    void slash() {
        strikes.add(new Strike(SLASH_HIT, Vec3.ZERO, tickCount + 15, null));
        if (phase() >= 2) strikes.add(new Strike(SLASH_HIT, Vec3.ZERO, tickCount + 27, null));
        if (phase() == 3) strikes.add(new Strike(SLASH_SPIN, Vec3.ZERO, tickCount + 45, null));
        castLock = phase() == 3 ? 50 : phase() == 2 ? 30 : 15;
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_SLASH);
        for (int i = 0; i < echoes.size(); i++) strikes.add(new Strike(ECHO_SLASH, Vec3.ZERO, tickCount + 10 + i * 12, echoes.get(i)));   // 残像が背後へ跳んで斬る
    }

    /** 全世界時間停止の予告（2 秒の鐘）。止まるのは予告の後 */
    void announceStop() {
        strikes.add(new Strike(STOP_BEGIN, Vec3.ZERO, tickCount + STOP_WARN, null));
        castLock = STOP_WARN + 10;
        say("stop_warn", ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (int i = 0; i < 3; i++) playSound(SoundEvents.BELL_BLOCK, 4.0F, 0.5F);
        if (level() instanceof ServerLevel server) {
            LivingEntity t = getTarget();
            float face = facing(t); // 文字盤の面を相手へ向ける
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FACING | RulerFxEntity.HANDS_TO_TWELVE, position().add(0, 13.0, 0), 9.0F, face, 0, STOP_WARN + 10, RulerFxEntity.GOLD);
        }
        level().broadcastEntityEvent(this, EVENT_STOP);
    }

    /**
     * 時が止まった（2026-10-20 ユーザー指定「時止めの技の殺意をもっと高く」で、止まった時の中で段取りを踏んで逃げ場を潰すように）。
     * 止まった 8 秒の間に、止まっている者のまわりを跳び回りながら（{@link #tickStopRoutine}）:
     * <ol>
     *   <li>0 秒: 背後へ跳び、胸の高さに針の輪（12 / 16 / 24 本）。動き出して 1 秒で飛ぶ</li>
     *   <li>1.5 秒: 横へ跳び、頭の上に針の冠（輪の 2/3、上から胸へ）。動き出して 1.3 秒</li>
     *   <li>3 秒: 真上へ跳び、頭上 6 m から足元へ針の雨（8 / 10 / 14 本）。動き出して 1.6 秒</li>
     *   <li>4.5 秒（第 2 形態から）: 居る所と前後左右 2.5 m に十字の座標消去の印。動き出して 1.5 秒・2 秒で落ちる</li>
     *   <li>5.5 秒（第 3 形態）: 因果断ちの大剣を相手を通る線に。動き出してから現れて 2.5 秒で倒れる</li>
     *   <li>5 / 6 / 7 秒: 「三」「二」「一」と数え、7.5 秒で間合いの外へ退いて「――そして、時は動き出す」</li>
     * </ol>
     * 第 3 形態は動き出した瞬間、止まっていた者の居た所に十二時の審判（{@link #afterStop}）。1 本 {@value #STOP_NEEDLE}
     */
    void stopTime(ServerLevel server) {
        TimeStop.start(server, this, STOP_TICKS);
        stopAt = tickCount;
        say("stop", ChatFormatting.GOLD, ChatFormatting.BOLD);
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_TO_TWELVE, position().add(0, 18.0, 0), 34.0F, 0, 0, 30, RulerFxEntity.VIOLET); // 止まった世界の空の文字盤（時と一緒に止まる）
        tickStopRoutine(server);                                                       // 0 秒の段取りはこの Tick に
    }

    /** 止まっている（ルーラーに当ててよい）者 */
    List<LivingEntity> frozenVictims(ServerLevel server) {
        return server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48.0), e -> ChronoGazer.canHurt(e) && TimeStop.frozen(e));
    }

    /** 止まった時の中の段取り（止めてからの Tick で） */
    void tickStopRoutine(ServerLevel server) {
        if (stopAt < 0) return;
        if (!TimeStop.active()) {
            stopAt = -1;
            return;
        }
        int t = tickCount - stopAt, resume = TimeStop.remaining();                  // resume = 動き出すまでの Tick
        LivingEntity target = getTarget();
        int ring = phase() == 3 ? 24 : phase() == 2 ? 16 : 12;
        switch (t) {
            case 0 -> {                                                                // 背後へ跳び、胸の高さに針の輪
                if (target != null) {
                    Vec3 look = target.getLookAngle().multiply(1, 0, 1);
                    if (look.lengthSqr() < 1.0e-4) look = new Vec3(1, 0, 0);
                    blink(server, target.position().subtract(look.normalize().scale(5.0)).add(0, 1.5, 0));
                }
                for (LivingEntity v : frozenVictims(server)) {
                    Vec3 chest = v.position().add(0, v.getBbHeight() * 0.55, 0);
                    for (int i = 0; i < ring; i++) {
                        double a = Math.PI * 2 * i / ring;
                        Vec3 from = chest.add(Math.cos(a) * 4.5, (i % 3 - 1) * 1.0, Math.sin(a) * 4.5);
                        RulerNeedleEntity.place(this, from, chest, 20, STOP_NEEDLE * trialAttack, 1.0F);   // 針は止まっているので、動き出して 1 秒後に飛ぶ
                    }
                    if (phase() == 3) afterStop.add(v.position());                        // 動き出したら十二時の審判
                }
                playSound(SoundEvents.TRIDENT_RIPTIDE_1, 2.0F, 0.6F);
            }
            case 30 -> {                                                               // 横へ跳び、頭の上に針の冠
                if (target != null) blink(server, target.position().add(Vec3.directionFromRotation(0, target.getYRot() + 90.0F).scale(6.0)).add(0, 2.0, 0));
                for (LivingEntity v : frozenVictims(server)) {
                    Vec3 chest = v.position().add(0, v.getBbHeight() * 0.55, 0);
                    int n = ring * 2 / 3;
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * (i + 0.5) / n;
                        Vec3 from = chest.add(Math.cos(a) * 3.2, 2.8, Math.sin(a) * 3.2);
                        RulerNeedleEntity.place(this, from, chest, 26, STOP_NEEDLE * trialAttack, 1.0F);
                    }
                }
                level().broadcastEntityEvent(this, EVENT_NEEDLE);
                playSound(SoundEvents.TRIDENT_RIPTIDE_2, 2.0F, 0.7F);
            }
            case 60 -> {                                                               // 真上へ跳び、足元へ針の雨
                if (target != null) blink(server, target.position().add(0, 9.0, 0));
                int n = phase() == 3 ? 14 : phase() == 2 ? 10 : 8;
                for (LivingEntity v : frozenVictims(server)) {
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n, r = i == 0 ? 0.0 : 0.6 + (i % 3) * 0.5;
                        Vec3 from = v.position().add(Math.cos(a) * r, 6.0 + (i % 2) * 0.8, Math.sin(a) * r);
                        RulerNeedleEntity.place(this, from, v.position().add(Math.cos(a) * r * 0.5, 0, Math.sin(a) * r * 0.5), 32 + i % 4, STOP_NEEDLE * trialAttack, 1.2F);
                    }
                }
                level().broadcastEntityEvent(this, EVENT_NEEDLE);
                playSound(SoundEvents.TRIDENT_RIPTIDE_3, 2.0F, 0.8F);
            }
            case 90 -> {                                                               // 第 2 形態から: 十字の座標消去の印
                if (phase() < 2) break;
                for (LivingEntity v : frozenVictims(server)) {
                    strikes.add(new Strike(ERASE_FALL, v.position(), tickCount + resume + 30, null));
                    for (int i = 0; i < 4; i++) {
                        double a = Math.PI / 2 * i;
                        strikes.add(new Strike(ERASE_FALL, v.position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5), tickCount + resume + 40, null));
                    }
                }
                say("erase", ChatFormatting.RED);
                level().broadcastEntityEvent(this, EVENT_ERASE);
                playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.8F);
            }
            case 110 -> {                                                              // 第 3 形態: 因果断ちの大剣を相手を通る線に（動き出してから現れて倒れる）
                if (phase() < 3 || target == null || !TimeStop.frozen(target)) break;
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 pivot = target.position().add(Math.cos(a) * 14.0, 0, Math.sin(a) * 14.0);
                spawnBlade(server, pivot, Math.atan2(target.getZ() - pivot.z, target.getX() - pivot.x), 50);
                say("blade", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                level().broadcastEntityEvent(this, EVENT_BLADE);
            }
            case 150 -> {                                                              // 間合いの外へ退き、動き出す
                if (target != null) {
                    Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
                    blink(server, target.position().add(away.normalize().scale(12.0)).add(0, 3.0, 0));
                }
                say("stop_resume", ChatFormatting.GOLD, ChatFormatting.BOLD);
            }
            default -> {
            }
        }
        if (t == STOP_TICKS - 60 || t == STOP_TICKS - 40 || t == STOP_TICKS - 20) {   // 「三」「二」「一」
            say("stop_count_" + (STOP_TICKS - t) / 20, ChatFormatting.GOLD, ChatFormatting.BOLD);
            playSound(SoundEvents.BELL_BLOCK, 4.0F, 0.5F + (STOP_TICKS - t) * 0.005F);
        }
        if (t > 0 && t < 150 && t % 30 == 15) playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 2.0F, 0.5F);   // 止まった時の中で響く足音
    }

    /** 縦の文字盤のエフェクトの面を t へ向ける角度（RulerFxEntity の yaw。t が無ければ向いている方） */
    float facing(LivingEntity t) {
        if (t != null) return (float) Math.atan2(t.getZ() - getZ(), t.getX() - getX());
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        return (float) Math.atan2(f.z, f.x);
    }

    /** 時間跳躍: その場から消えて at に現れる */
    void blink(ServerLevel server, Vec3 at) {
        Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.4F, 14), position().add(0, 3.0, 0), 24, 0.25);
        float face = facing(getTarget());
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FACING, position().add(0, 0.5, 0), 3.2F, face, 0, 16, RulerFxEntity.GOLD);
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FACING | RulerFxEntity.HANDS_REVERSE, at.add(0, 0.5, 0), 3.2F, face, 0, 16, RulerFxEntity.VIOLET);
        moveTo(at.x, at.y, at.z, getYRot(), getXRot());
        Fx.burst(server, MementoMoriEntity.glow(INDIGO, 0.4F, 14), at.add(0, 3.0, 0), 24, 0.25);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.5F);
    }

    /** from から to を向く yaw（度。Minecraft の向き） */
    static float yawTo(Vec3 from, Vec3 to) {
        return (float) (net.minecraft.util.Mth.atan2(to.z - from.z, to.x - from.x) * (180.0 / Math.PI)) - 90.0F;
    }

    /** 時針の一閃の当たり: pos から yawDeg の向きの前の半円 5.5 m（ルーラーと残像で同じ） */
    void slashFrom(ServerLevel server, Vec3 pos, float yawDeg) {
        Vec3 fwd = Vec3.directionFromRotation(0, yawDeg);
        Fx.slash(server, pos.add(0, 2.0, 0).add(fwd.scale(2.5)), GOLD, 5.0F, 10, 0.0F);
        float face = (float) Math.atan2(fwd.z, fwd.x);
        RulerFxEntity.spawn(server, RulerFxEntity.SLASH, 0, pos.add(0, 1.6, 0), 6.0F, face, 0, 14, RulerFxEntity.GOLD);
        RulerFxEntity.spawn(server, RulerFxEntity.SLASH, 0, pos.add(0, 0.8, 0), 5.0F, face, 0, 12, RulerFxEntity.VIOLET);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(5.5, 4.0, 5.5), ChronoGazer::canHurt)) {
            Vec3 rel = e.position().subtract(pos).multiply(1, 0, 1);
            if (rel.length() < 5.5 && rel.normalize().dot(fwd) > 0.0 && Math.abs(e.getY() - pos.y) < 4.0) e.hurt(damageSources().mobAttack(this), damageFor(e, SLASH));
        }
        server.playSound(null, BlockPos.containing(pos), SoundEvents.PLAYER_ATTACK_SWEEP, getSoundSource(), 2.0F, 0.6F);
    }

    // ---- 因果断ちの大剣（2026-10-20） ----

    /**
     * 因果断ちの大剣: ルーラーの足元の床を根元に、空へ立った巨大な時空剪定の針が {@value #BLADE_SWING} Tick かけて相手の居た線へ倒れる（はじめはゆっくり、最後に一気に）。
     * 床の赤い帯の上に居た者は因果停止（{@link CausalLock}）。跳んでも高く飛んでも避けられず、帯の外へ出るしかない。第 3 形態は 2.5 秒後に別の向きから 2 本目
     */
    void causalBlade(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 pivot = new Vec3(getX(), t.getY(), getZ());
        spawnBlade(server, pivot, Math.atan2(t.getZ() - pivot.z, t.getX() - pivot.x), BLADE_SWING);
        if (phase() == 3) strikes.add(new Strike(BLADE_CAST, new Vec3(random.nextDouble() * Math.PI * 2, 0, 0), tickCount + 50, t));   // at.x = 2 本目の根元の向き
        castLock = 30;
        say("blade", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.35F);
        playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_BLADE);
    }

    void spawnBlade(ServerLevel server, Vec3 pivot, double yaw, int swing) {
        RulerFxEntity.spawn(server, RulerFxEntity.BLADE, swing, pivot, BLADE_LEN, (float) yaw, 0, swing + 40, RulerFxEntity.GOLD);   // 落ちた後 2 秒は光の柱と、ほどける剣
        Fx.line(server, MementoMoriEntity.glow(GOLD, 0.6F, 24), pivot, pivot.add(0, BLADE_LEN, 0), 0.6);
        blades.add(new Blade(pivot, yaw, tickCount + swing));
    }

    void tickBlades(ServerLevel server) {
        for (Blade b : new ArrayList<>(blades)) {
            if (TimeStop.active()) {                                                    // 止まった時の中では大剣も止まる（エフェクトと合わせる）
                blades.remove(b);
                blades.add(new Blade(b.pivot(), b.yaw(), b.fire() + 1));
                continue;
            }
            int left = b.fire() - tickCount;
            if (left == 30 || left == 10) server.playSound(null, BlockPos.containing(b.pivot()), SoundEvents.BELL_BLOCK, getSoundSource(), 4.0F, left == 30 ? 0.6F : 0.9F);
            if (left > 0) continue;
            blades.remove(b);
            Vec3 d = new Vec3(Math.cos(b.yaw()), 0, Math.sin(b.yaw()));
            Vec3 end = b.pivot().add(d.scale(BLADE_LEN));
            for (int i = 0; i <= 8; i++) Fx.shockwave(server, b.pivot().add(d.scale(BLADE_LEN * i / 8.0)).add(0, 0.3, 0), i % 2 == 0 ? GOLD : WHITE, 3.5F, 16);
            Fx.line(server, MementoMoriEntity.glow(PALE, 0.7F, 20), b.pivot().add(0, 0.5, 0), end.add(0, 0.5, 0), 0.4);
            server.sendParticles(ParticleTypes.FLASH, end.x, end.y + 1, end.z, 1, 0, 0, 0, 0);
            server.playSound(null, BlockPos.containing(b.pivot().add(d.scale(BLADE_LEN / 2))), SoundEvents.ANVIL_LAND, getSoundSource(), 4.0F, 0.4F);
            server.playSound(null, BlockPos.containing(b.pivot().add(d.scale(BLADE_LEN / 2))), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 3.0F, 0.5F);
            AABB box = new AABB(b.pivot(), end).inflate(BLADE_HALF_WIDTH + 2.0, 0, BLADE_HALF_WIDTH + 2.0).expandTowards(0, 10, 0).expandTowards(0, -3, 0);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, ChronoGazer::canHurt)) {
                if (onBladeLine(b.pivot(), d, e)) CausalLock.lock(e, LOCK_TICKS, this);
            }
        }
    }

    /** 大剣の倒れる帯（根元から長さ {@value #BLADE_LEN}、幅 {@value #BLADE_HALF_WIDTH} の倍。上は 10 m まで届く）に e が居るか */
    public static boolean onBladeLine(Vec3 pivot, Vec3 d, LivingEntity e) {
        Vec3 rel = e.position().subtract(pivot).multiply(1, 0, 1);
        double along = rel.dot(d);
        double side = rel.subtract(d.scale(along)).length();
        return along > -1.0 && along < BLADE_LEN + 2.0 && side < BLADE_HALF_WIDTH + e.getBbWidth() / 2 && e.getY() > pivot.y - 3.0 && e.getY() < pivot.y + 10.0;
    }

    // ---- 時の歯車の挟撃（2026-10-20） ----

    /** 時の歯車の挟撃: 相手を通る線の両端から巨大な歯車が転がってきて、相手の所で挟む。第 3 形態は直角にもう 1 組（少し遅れて） */
    void gearPincer(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        double axis = random.nextDouble() * Math.PI;
        spawnGears(server, t, axis);
        if (phase() == 3) strikes.add(new Strike(GEAR_CAST, new Vec3(axis + Math.PI / 2, 0, 0), tickCount + 25, t));   // at.x = 2 組目の線の向き
        castLock = 20;
        say("gear", ChatFormatting.GOLD);
        level().broadcastEntityEvent(this, EVENT_GEAR);
    }

    void spawnGears(ServerLevel server, LivingEntity t, double axis) {
        int travel = phase() == 3 ? 50 : 60;
        float dist = 24.0F, speed = dist / travel;
        Vec3 meet = t.position(), d = new Vec3(Math.cos(axis), 0, Math.sin(axis));
        RulerFxEntity a = RulerFxEntity.spawn(server, RulerFxEntity.GEAR, travel, meet.subtract(d.scale(dist)), GEAR_RADIUS, (float) axis, speed, travel + 18, RulerFxEntity.GOLD);
        RulerFxEntity b = RulerFxEntity.spawn(server, RulerFxEntity.GEAR, travel, meet.add(d.scale(dist)), GEAR_RADIUS, (float) (axis + Math.PI), speed, travel + 18, 0xC8D2E0);
        Fx.line(server, MementoMoriEntity.glow(RED, 0.25F, 30), meet.subtract(d.scale(dist)).add(0, 0.2, 0), meet.add(d.scale(dist)).add(0, 0.2, 0), 0.8);   // 転がる線の予告
        gears.add(new GearPair(a, b, meet, travel));
        server.playSound(null, BlockPos.containing(meet), SoundEvents.IRON_GOLEM_REPAIR, getSoundSource(), 3.0F, 0.5F);
    }

    void tickGears(ServerLevel server) {
        if (TimeStop.active()) return;
        for (GearPair g : new ArrayList<>(gears)) {
            for (RulerFxEntity fx : new RulerFxEntity[]{g.a, g.b}) {
                if (!fx.isAlive() || fx.tickCount > g.travel) continue;
                Vec3 at = fx.position(), d = new Vec3(Math.cos(fx.yaw()), 0, Math.sin(fx.yaw())), n = new Vec3(-d.z, 0, d.x);
                if (fx.tickCount % 3 == 0) {                                            // 床を削る火花と音
                    server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.2, at.z, 4, 0.4, 0.05, 0.4, 0.1);
                    if (fx.tickCount % 9 == 0) server.playSound(null, BlockPos.containing(at), SoundEvents.CHAIN_STEP, getSoundSource(), 2.0F, 0.5F);
                }
                if (fx.tickCount % 4 != 0) continue;
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(GEAR_RADIUS + 1.0, 0, GEAR_RADIUS + 1.0).expandTowards(0, GEAR_RADIUS * 2, 0).expandTowards(0, -1, 0), ChronoGazer::canHurt)) {
                    Vec3 rel = e.position().subtract(at).multiply(1, 0, 1);
                    double along = rel.dot(d), side = rel.dot(n);
                    if (Math.abs(along) > GEAR_RADIUS * 0.85 || Math.abs(side) > 0.6 + e.getBbWidth() / 2 || e.getY() > at.y + GEAR_RADIUS * 2) continue;
                    e.hurt(damageSources().mobAttack(this), damageFor(e, GEAR));               // はじかない（2026-10-20 ユーザー指定「ふっとばしも無くして」）
                }
            }
            if (g.a.tickCount >= g.travel || !g.a.isAlive() || !g.b.isAlive()) {        // かみ合う: 砕けて針が輪に散る
                gears.remove(g);
                Vec3 c = g.meet;
                Fx.shockwave(server, c.add(0, 0.3, 0), GOLD, 7.0F, 22);
                Fx.burst(server, MementoMoriEntity.glow(PALE, 0.5F, 20), c.add(0, GEAR_RADIUS, 0), 40, 0.3);
                server.sendParticles(ParticleTypes.FLASH, c.x, c.y + GEAR_RADIUS, c.z, 1, 0, 0, 0, 0);
                hitAround(c, 3.5, GEAR_MESH, false);
                int n = phase() == 3 ? 16 : 12;
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    Vec3 from = c.add(Math.cos(a) * 1.5, 1.2, Math.sin(a) * 1.5);
                    RulerNeedleEntity.place(this, from, from.add(Math.cos(a), 0, Math.sin(a)), 10, NEEDLE * trialAttack, 1.2F);
                }
                server.playSound(null, BlockPos.containing(c), SoundEvents.ANVIL_DESTROY, getSoundSource(), 3.0F, 0.6F);
                server.playSound(null, BlockPos.containing(c), SoundEvents.BELL_BLOCK, getSoundSource(), 3.0F, 1.2F);
            }
        }
    }

    // ---- 加速と減速の場（2026-10-20） ----

    static final int FIELD_TICKS = 240;

    /** 加速と減速の場: 相手の足元に青い減速の文字盤（半径 6）、まわりに赤い加速の文字盤（半径 5）を 2 つ（第 3 形態 3 つ）。12 秒 */
    void timeFields(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = t.position();
        addField(server, c, 6.0, false);
        int reds = phase() == 3 ? 3 : 2;
        double base = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < reds; i++) {
            double a = base + Math.PI * 2 * i / reds, r = 9.0 + random.nextDouble() * 3.0;
            addField(server, c.add(Math.cos(a) * r, 0, Math.sin(a) * r), 5.0, true);
        }
        castLock = 15;
        say("field", ChatFormatting.AQUA);
        playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 1.2F);
        level().broadcastEntityEvent(this, EVENT_FIELD);
    }

    void addField(ServerLevel server, Vec3 c, double r, boolean haste) {
        fields.add(new Field(c, r, haste, tickCount + FIELD_TICKS));
        int mode = RulerFxEntity.FLAT | RulerFxEntity.FILL | (haste ? RulerFxEntity.HANDS_FAST : RulerFxEntity.HANDS_SLOW | RulerFxEntity.HANDS_REVERSE);
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, mode, c.add(0, 0.08, 0), (float) r, 0, 0, FIELD_TICKS, haste ? RulerFxEntity.RED : RulerFxEntity.BLUE);
    }

    boolean inHaste(Vec3 at) {
        for (Field f : fields) if (f.haste() && inField(f, at)) return true;
        return false;
    }

    static boolean inField(Field f, Vec3 at) {
        double dx = at.x - f.c().x, dz = at.z - f.c().z;
        return dx * dx + dz * dz < f.r() * f.r() && Math.abs(at.y - f.c().y) < 3.0;
    }

    void tickFields(ServerLevel server) {
        fields.removeIf(f -> tickCount >= f.until());
        if (fields.isEmpty() || TimeStop.active()) return;
        for (Field f : fields) {
            if (tickCount % 5 == 0) KeeperFx.ring(server, f.c().add(0, 0.2, 0), f.r(), f.haste() ? RED : 0x60A8FF, 20);
            if (tickCount % 10 != 0) continue;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(f.c(), f.c()).inflate(f.r(), 3.0, f.r()), ChronoGazer::canHurt)) {
                if (!inField(f, e.position())) continue;
                if (f.haste()) {
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 20, 1, false, false, true));
                    if ((tickCount + e.getId()) % 30 < 10) {                            // 加速の中では楔がすぐ追ってくる
                        strikes.add(new Strike(SPIKE_FALL, e.position(), tickCount + 12, null));
                        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_FAST, e.position().add(0, 0.12, 0), 2.2F, 0, 0, 12, RulerFxEntity.RED);
                    }
                } else {
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false, true));
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 20, 1, false, false, true));
                }
            }
        }
    }

    // ---- 残像の分身（2026-10-20） ----

    static final int ECHO_TICKS = 300;

    /** 残像の分身: 相手のまわり半径 10 に過去の自分の残像を 2 体（第 3 形態 3 体）。15 秒、ルーラーの針・一閃・巻き戻しの弾を少し遅れてなぞる */
    void summonEchoes(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        int n = phase() == 3 ? 3 : 2;
        double base = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            RulerEchoEntity echo = ModEntities.RULER_ECHO.get().create(server);
            if (echo == null) continue;
            double a = base + Math.PI * 2 * i / n;
            Vec3 at = t.position().add(Math.cos(a) * 10.0, 3.0, Math.sin(a) * 10.0);
            echo.moveTo(at.x, at.y, at.z, yawTo(at, t.position()), 0.0F);
            echo.setRuler(this, ECHO_TICKS);
            server.addFreshEntity(echo);
            echoes.add(echo);
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FACING | RulerFxEntity.HANDS_REVERSE, at.add(0, 3.0, 0), 3.5F, facing(t), 0, 20, RulerFxEntity.VIOLET);
            Fx.burst(server, MementoMoriEntity.glow(VIOLET, 0.4F, 16), at.add(0, 3.0, 0), 24, 0.25);
        }
        castLock = 20;
        say("echo", ChatFormatting.LIGHT_PURPLE);
        playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 3.0F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_ECHO);
    }

    void tickEchoes(ServerLevel server) {
        echoes.removeIf(e -> !e.isAlive());
        LivingEntity t = getTarget();
        if (t == null) return;
        for (RulerEchoEntity echo : echoes) {                                          // 残像も相手を向き、ゆっくり漂う
            echo.setYRot(net.minecraft.util.Mth.approachDegrees(echo.getYRot(), yawTo(echo.position(), t.position()), TURN));
            if (tickCount % 4 == 0) Fx.one(server, MementoMoriEntity.glow(VIOLET, 0.2F, 16), echo.position().add(random.nextGaussian() * 0.8, 1.0 + random.nextDouble() * 4.0, random.nextGaussian() * 0.8), new Vec3(0, -0.02, 0));
        }
    }

    // ---- 巻き戻しの弾（2026-10-20） ----

    /** 巻き戻しの弾: 相手へ扇に針を放つ（10 / 14 / 18 本）。通り過ぎてから止まり、同じ道を戻ってくる。第 3 形態は間をずらした 2 つ目の扇 */
    void rewindShot(LivingEntity t) {
        int n = phase() == 3 ? 18 : phase() == 2 ? 14 : 10;
        rewindFan(position(), t, n, 0.0);
        if (phase() == 3) rewindFan(position(), t, n - 1, 0.5);
        playSound(SoundEvents.TRIDENT_RIPTIDE_2, 2.0F, 0.8F);
        level().broadcastEntityEvent(this, EVENT_NEEDLE);
        if (!quiet) for (int i = 0; i < echoes.size(); i++) strikes.add(new Strike(ECHO_RSHOT, Vec3.ZERO, tickCount + 14 + i * 10, echoes.get(i)));
    }

    /** center から t へ、左右 50 度の扇に n 本の巻き戻しの弾（offset は本の間の何割ずらすか） */
    void rewindFan(Vec3 center, LivingEntity t, int n, double offset) {
        Vec3 c = center.add(0, 3.5, 0);
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0);
        double base = Math.atan2(aim.z - c.z, aim.x - c.x), flat = Math.max(1.0, Math.hypot(aim.x - c.x, aim.z - c.z));
        double dy = (aim.y - c.y) / flat, spread = Math.toRadians(100);
        for (int i = 0; i < n; i++) {
            double a = base + ((i + offset) / Math.max(1, n - 1) - 0.5) * spread;
            Vec3 dir = new Vec3(Math.cos(a), dy, Math.sin(a)).normalize();
            Vec3 from = c.add(dir.scale(1.5));
            RulerNeedleEntity.place(this, from, from.add(dir.scale(10)), 10 + i + (offset > 0 ? 12 : 0), NEEDLE * trialAttack, 1.3F).rewinding(26);
        }
    }

    // ================================================================== 第 2・第 3 形態で加わる技（2026-10-20）
    // ユーザー指定「タイムルーラーの形態変化ごとに大技一つと技を四つ追加して」。質問で: 第 2・第 3 形態に入るごとに加わる（後の形態も前の技を使い続ける）・大技はその形態の間に何度でも

    // ---- 第 2 形態の大技: 逆巻く時の大渦 ----

    /**
     * 逆巻く時の大渦（第 2 形態から）: 足元に半径 28 の逆回りの紫の文字盤を広げ、{@value #MAEL_TICKS} Tick の間、まわり 32 m の者を真ん中へ引き寄せる（歩けば抗える速さ）。
     * その間、真ん中から 3 本の時針の光（長さ 26、腰の高さ。跳べば越える）が回り、終わりに真ん中の半径 7 が潰れる（{@value #MAEL_CRUSH}、防具を通す）
     */
    void maelstrom(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        maelLeft = MAEL_TICKS;
        maelCenter = new Vec3(getX(), t.getY(), getZ());
        maelAngle = random.nextDouble() * Math.PI * 2;
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_REVERSE, maelCenter.add(0, 0.06, 0), 28.0F, 0, 0, MAEL_TICKS, RulerFxEntity.VIOLET);
        RulerFxEntity.spawn(server, RulerFxEntity.SWEEP, 3, maelCenter.add(0, 1.0, 0), 26.0F, (float) maelAngle, (float) MAEL_SPIN, MAEL_TICKS + 20, RulerFxEntity.VIOLET);
        castLock = 60;
        say("maelstrom", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.3F);
        playSound(SoundEvents.PORTAL_TRIGGER, 3.0F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_MAEL);
    }

    void tickMaelstrom(ServerLevel server) {
        if (maelLeft <= 0 || TimeStop.active()) return;
        int t = MAEL_TICKS - maelLeft--;
        Vec3 c = maelCenter;
        if (t >= 20) maelAngle += MAEL_SPIN;
        if (t % 2 == 0) {                                                            // 渦を巻いて真ん中へ吸い込まれる光
            for (int i = 0; i < 4; i++) {
                double a = t * 0.15 + i * Math.PI / 2, r = 26.0 - (t % 40) * 0.6;
                Vec3 p = c.add(Math.cos(a) * r, 0.5 + i * 0.6, Math.sin(a) * r);
                Fx.one(server, MementoMoriEntity.glow(i % 2 == 0 ? VIOLET : INDIGO, 0.35F, 20), p, c.subtract(p).scale(0.05).add(new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.3)));
            }
        }
        if (t % 20 == 0) playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 3.0F, 0.4F + t * 0.004F);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(32.0, 6.0, 32.0), ChronoGazer::canHurt)) {
            Vec3 rel = c.subtract(e.position()).multiply(1, 0, 1);
            double dist = rel.length();
            if (dist > 1.2 && dist < 32.0 && t % 3 == 0) {                            // 引き寄せ（1 秒に 2.4 m。歩けば抗える）
                Vec3 step = rel.scale(0.12 / dist);
                if (e instanceof ServerPlayer p) p.connection.teleport(step.x, 0, step.z, 0, 0, java.util.EnumSet.allOf(net.minecraft.world.entity.RelativeMovement.class));
                else e.push(step.x, 0, step.z);
            }
            if (t >= 20 && t % 10 == 0) {                                              // 回る 3 本の時針の光（腰の高さ）
                for (int k = 0; k < 3; k++) {
                    double a = maelAngle + k * Math.PI * 2 / 3;
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 r2 = e.position().subtract(c).multiply(1, 0, 1);
                    double along = r2.dot(dir);
                    boolean low = e.getBoundingBox().minY < c.y + 1.3 && e.getBoundingBox().maxY > c.y + 0.7;
                    if (along > 0 && along < 26 && r2.subtract(dir.scale(along)).length() < 0.9 && low) e.hurt(damageSources().mobAttack(this), damageFor(e, SWEEP * 1.25F));
                }
            }
        }
        if (maelLeft == 0) {                                                           // 真ん中が潰れる
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, c, 4.0F, 0, 0, 30, RulerFxEntity.VIOLET);
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, c, 1.6F, 0, 0, 22, RulerFxEntity.WHITE);
            Fx.shockwave(server, c.add(0, 0.3, 0), VIOLET, 14.0F, 30);
            Fx.shockwave(server, c.add(0, 1.5, 0), WHITE, 8.0F, 20);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
            hitAround(c, 7.0, MAEL_CRUSH, true);
            server.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 4.0F, 0.4F);
            server.playSound(null, BlockPos.containing(c), SoundEvents.BELL_RESONATE, getSoundSource(), 5.0F, 0.5F);
        }
    }

    // ---- 第 2 形態: 振り子の断頭台 ----

    /** 振り子の断頭台: 相手の頭上 13 m を支点に、巨大な三日月の刃の振り子（腕 12、振れ幅 70 度）を、相手を通る向きに 2 往復振る（1 秒の予告の後） */
    void pendulum(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        double yaw = random.nextDouble() * Math.PI;
        Vec3 pivot = t.position().add(0, PEND_LEN + 1.0, 0);
        RulerFxEntity fx = RulerFxEntity.spawn(server, RulerFxEntity.PENDULUM, PEND_WARN, pivot, PEND_LEN, (float) yaw, PEND_AMP, PEND_WARN + PEND_PERIOD * 2 + 10, RulerFxEntity.GOLD);
        pendulums.add(fx);
        castLock = 20;
        say("pendulum", ChatFormatting.GOLD);
        playSound(SoundEvents.CHAIN_PLACE, 3.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_SWEEP);
    }

    /** 振り子の角度（ラジアン。予告の間は端に止まり、それから cos で振れる。描く側と同じ式） */
    public static float pendulumAngle(float age, int warn, float amp) {
        if (age < warn) return amp;
        return amp * net.minecraft.util.Mth.cos((float) (Math.PI * 2 * (age - warn) / PEND_PERIOD));
    }

    void tickPendulums(ServerLevel server) {
        pendulums.removeIf(fx -> !fx.isAlive());
        for (RulerFxEntity fx : pendulums) {
            int age = fx.tickCount;
            if (age < PEND_WARN || age > PEND_WARN + PEND_PERIOD * 2) continue;
            float th = pendulumAngle(age, PEND_WARN, PEND_AMP);
            Vec3 d = new Vec3(Math.cos(fx.yaw()), 0, Math.sin(fx.yaw()));
            Vec3 bob = fx.position().add(d.scale(Math.sin(th) * PEND_LEN)).add(0, -Math.cos(th) * PEND_LEN, 0);
            if (age % 3 == 0) server.sendParticles(ParticleTypes.ELECTRIC_SPARK, bob.x, bob.y, bob.z, 3, 0.5, 0.5, 0.5, 0.05);
            if (Math.abs(th) < 0.08F) server.playSound(null, BlockPos.containing(bob), SoundEvents.PLAYER_ATTACK_SWEEP, getSoundSource(), 2.5F, 0.4F);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(bob, bob).inflate(3.0), ChronoGazer::canHurt)) {
                if (e.getBoundingBox().getCenter().distanceTo(bob) > 2.6 + e.getBbWidth() / 2) continue;
                e.hurt(damageSources().mobAttack(this), damageFor(e, PENDULUM_HIT));
            }
        }
    }

    // ---- 第 2 形態: 砂時計の牢 ----

    /** 砂時計の牢: 相手のまわり半径 5 に 6 つの光の砂時計。中は砂に足を取られ（鈍化 II）、3 秒で砂が落ちきると中に {@value #PRISON_HIT} と盲目。外へ出れば助かる */
    void hourglassPrison(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = t.position();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            RulerFxEntity.spawn(server, RulerFxEntity.HOURGLASS, 0, c.add(Math.cos(a) * PRISON_R, 0, Math.sin(a) * PRISON_R), 1.6F, (float) a, 0, PRISON_TICKS + 8, RulerFxEntity.GOLD);
        }
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_TO_TWELVE, c.add(0, 0.07, 0), (float) PRISON_R, 0, 0, PRISON_TICKS, RulerFxEntity.GOLD);
        strikes.add(new Strike(PRISON_FALL, c, tickCount + PRISON_TICKS, null));
        castLock = 15;
        say("prison", ChatFormatting.GOLD);
        playSound(SoundEvents.SAND_PLACE, 3.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_FIELD);
    }

    static boolean inRing(Vec3 c, double r, LivingEntity e) {
        double dx = e.getX() - c.x, dz = e.getZ() - c.z;
        return dx * dx + dz * dz < r * r && Math.abs(e.getY() - c.y) < 4.0;
    }

    // ---- 第 2 形態: 時針の三連 ----

    /** 時針の三連（18 m 以内）: 相手のそばへ跳んでは一閃、を 0.8 秒ごとに 3 度 */
    void handBarrage(LivingEntity t) {
        for (int i = 0; i < 3; i++) strikes.add(new Strike(DASH_BLINK, Vec3.ZERO, tickCount + i * 16, t));
        castLock = 3 * 16 + 14;
        say("barrage3", ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    // ---- 第 2 形態: 時限の刻印 ----

    /** 時限の刻印: 相手のまわりに 6 つの小さな紫の文字盤。針が XII に着いたものから順に（1〜3.5 秒）弾ける（半径 3、{@value #MARK_HIT}） */
    void delayMarks(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0.0 : 2.0 + random.nextDouble() * 4.5;
            Vec3 at = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            int timer = 20 + i * 10;
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_TO_TWELVE, at.add(0, 0.1, 0), 3.0F, 0, 0, timer, RulerFxEntity.VIOLET);
            strikes.add(new Strike(MARK_BLAST, at, tickCount + timer, null));
        }
        say("marks", ChatFormatting.LIGHT_PURPLE);
        playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2.5F, 1.2F);
        level().broadcastEntityEvent(this, EVENT_ERASE);
    }

    // ---- 第 3 形態の大技: 終末時計 ----

    /**
     * 終末時計（第 3 形態）: 背に巨大な縦の文字盤を掲げ、0.7 秒ごとに I から XII まで 12 度鐘を打つ。I 時に 1 つ、II 時に 2 つ……と、打った数だけ相手のまわりへ楔が落ち
     * （1 つ目は相手の居る所、0.8 秒の予告）、XII 時には相手の居る所に半径 5 の終末の光（1.5 秒の予告、{@value #DOOM_HIT}、防具を通す）
     */
    void doomsday(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        doomChime = 0;
        for (int k = 0; k < 12; k++) strikes.add(new Strike(DOOM_CHIME, Vec3.ZERO, tickCount + 20 + k * 14, t));
        int total = 20 + 11 * 14 + 30 + 10;
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FACING | RulerFxEntity.HANDS_TO_TWELVE, position().add(0, 9.0, 0).subtract(Vec3.directionFromRotation(0, getYRot()).scale(2.0)),
                13.0F, facing(t), 0, total, RulerFxEntity.GOLD);
        castLock = total;
        say("doomsday", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.3F);
        playSound(SoundEvents.END_PORTAL_SPAWN, 2.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_DOOM);
    }

    // ---- 第 3 形態: 双子の文字盤 ----

    /** 双子の文字盤: 相手の左右 10 m の宙に相手へ面を向けた 2 つの文字盤。3 秒の間、交互に速い針を相手の動きを読んだ所へ（15 本） */
    void twinDials(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 to = t.position().subtract(position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0e-4) to = new Vec3(1, 0, 0);
        Vec3 side = new Vec3(-to.z, 0, to.x).normalize();
        Vec3[] dials = {t.position().add(side.scale(10)).add(0, 4, 0), t.position().subtract(side.scale(10)).add(0, 4, 0)};
        for (Vec3 dv : dials) {
            float face = (float) Math.atan2(t.getZ() - dv.z, t.getX() - dv.x);
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FACING, dv, 3.0F, face, 0, 16 + 15 * 4 + 6, RulerFxEntity.GOLD);
            Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.4F, 14), dv, 16, 0.2);
        }
        for (int i = 0; i < 15; i++) strikes.add(new Strike(TWIN_SHOT, dials[i % 2], tickCount + 16 + i * 4, t));
        say("twin", ChatFormatting.GOLD);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 1.4F);
        level().broadcastEntityEvent(this, EVENT_NEEDLE);
    }

    // ---- 第 3 形態: 時空疾走 ----

    /** 時空疾走: 相手を通る長さ 30 の道に 0.8 秒の赤い予告、それから一瞬で駆け抜けて道の上を斬る（{@value #DASH_HIT}）。向きを変えて 3 度 */
    void phaseDash(LivingEntity t) {
        for (int i = 0; i < 3; i++) strikes.add(new Strike(DASH_AIM, Vec3.ZERO, tickCount + i * 30, t));
        castLock = 3 * 30 + 10;
        say("dash", ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    // ---- 第 3 形態: 崩れる文字盤 ----

    /** 崩れる文字盤: 相手を中心に半径 10 の床を 12 の時の扇に分け、XII から右回りに 0.3 秒ずつ赤く光り、光って 0.8 秒で崩れる（{@value #SECTOR_HIT}、打ち上げ）。崩れた後を追って回れば避けられる */
    void crumblingDial(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = t.position();
        for (int k = 0; k < 12; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 6;                                // 上から見て XII（北）から右回り
            int fall = 16 + k * 6;
            RulerFxEntity.spawn(server, RulerFxEntity.SECTOR, fall, c.add(0, 0.06, 0), (float) SECTOR_R, (float) a, (float) (Math.PI / 6), fall + 12, RulerFxEntity.RED);
            sectors.add(new Sector(c, a, tickCount + fall));
        }
        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT, c.add(0, 0.05, 0), (float) SECTOR_R + 0.4F, 0, 0, 16 + 12 * 6 + 12, RulerFxEntity.RED);
        castLock = 20;
        say("crumble", ChatFormatting.RED);
        playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_FIELD);
    }

    void tickSectors(ServerLevel server) {
        for (Sector s : new ArrayList<>(sectors)) {
            if (TimeStop.active()) {
                sectors.remove(s);
                sectors.add(new Sector(s.c(), s.a(), s.fire() + 1));
                continue;
            }
            if (s.fire() > tickCount) continue;
            sectors.remove(s);
            Vec3 mid = s.c().add(Math.cos(s.a()) * SECTOR_R * 0.6, 0, Math.sin(s.a()) * SECTOR_R * 0.6);
            Fx.shockwave(server, mid.add(0, 0.3, 0), WHITE, 3.0F, 12);
            server.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y + 0.5, mid.z, 2, 2.0, 0.2, 2.0, 0);
            server.playSound(null, BlockPos.containing(mid), SoundEvents.DEEPSLATE_BREAK, getSoundSource(), 3.0F, 0.5F);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(s.c(), s.c()).inflate(SECTOR_R, 3.0, SECTOR_R), ChronoGazer::canHurt)) {
                Vec3 rel = e.position().subtract(s.c()).multiply(1, 0, 1);
                if (rel.length() > SECTOR_R || Math.abs(e.getY() - s.c().y) > 3.0) continue;
                double da = net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(rel.z, rel.x) - s.a()));
                if (Math.abs(da) > 15.5 && rel.length() > 0.8) continue;
                e.hurt(damageSources().mobAttack(this), damageFor(e, SECTOR_HIT));
            }
        }
    }

    // ---- 第 3 形態: 時の雨 ----

    /** 時の雨: 3 秒の間、相手のまわり半径 11 に紫の時の雫が降り続ける（0.7 秒の予告の輪、1 つ {@value #RAIN_HIT}・半径 1.7。2 つに 1 つは相手の行く先を読む） */
    void hourRain(LivingEntity t) {
        for (int i = 0; i < 20; i++) strikes.add(new Strike(RAIN_SPAWN, Vec3.ZERO, tickCount + i * 3, t));
        say("rain", ChatFormatting.LIGHT_PURPLE);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 3.0F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_NEEDLE);
    }

    /** ローマ数字（終末時計の打った数） */
    static String romanNumeral(int k) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
        return k >= 0 && k < r.length ? r[k] : String.valueOf(k);
    }

    // ---- 因果逆転 ----

    void startRewind() {
        if (!(level() instanceof ServerLevel server)) return;
        rewindLeft = REWIND_TICKS;
        entityData.set(REWINDING, true);
        castLock = REWIND_TICKS;
        say("rewind", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        double base = random.nextDouble() * Math.PI * 2;
        double floor = getTarget() != null ? getTarget().getY() : getY() - 2.0;
        for (int i = 0; i < 4; i++) {
            RulerAnchorEntity anchor = ModEntities.RULER_ANCHOR.get().create(server);
            if (anchor == null) continue;
            double a = base + i * Math.PI / 2;
            anchor.moveTo(getX() + Math.cos(a) * 12, floor + 1.5 + (i % 2) * 1.5, getZ() + Math.sin(a) * 12, 0, 0);
            server.addFreshEntity(anchor);
            Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.4F, 16), anchor.position().add(0, 1.0, 0), 20, 0.2);
        }
        rewindFx = RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_REVERSE, new Vec3(getX(), floor + 0.1, getZ()), 13.0F, 0, 0, REWIND_TICKS, RulerFxEntity.VIOLET);
        playSound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 0.4F);
        level().broadcastEntityEvent(this, EVENT_REWIND);
    }

    List<RulerAnchorEntity> anchors(ServerLevel server) {
        return server.getEntitiesOfClass(RulerAnchorEntity.class, getBoundingBox().inflate(48.0), RulerAnchorEntity::isAlive);
    }

    void tickRewind(ServerLevel server) {
        if (rewindLeft <= 0) return;
        rewindLeft--;
        if (rewindLeft % 20 == 0) playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 2.0F, 0.5F + rewindLeft / 320.0F);
        if (tickCount % 3 == 0) Fx.mandala(server, position().add(0, 3.0, 0), VIOLET, 3.0F, 6, true, false);
        List<RulerAnchorEntity> left = anchors(server);
        if (left.isEmpty()) { // 全部壊された: 詠唱が崩れる
            rewindLeft = 0;
            entityData.set(REWINDING, false);
            castLock = 0;
            staggerLeft = 120;
            if (rewindFx != null) rewindFx.discard();
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, position().add(0, -1.0, 0), 2.0F, 0, 0, 24, RulerFxEntity.WHITE);
            say("rewind_broken", ChatFormatting.AQUA, ChatFormatting.BOLD);
            playSound(SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
            Fx.shockwave(server, position().add(0, 3.0, 0), VIOLET, 8.0F, 16);
            return;
        }
        if (rewindLeft == 0) { // 残った: 20 秒前の体力へ巻き戻る
            entityData.set(REWINDING, false);
            float past = history[Math.floorMod(historyAt - 20, history.length)];
            if (past > getHealth()) setHealth(Math.min(getMaxHealth(), past));
            for (RulerAnchorEntity a : left) a.discard();
            RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, position().add(0, -1.0, 0), 1.6F, 0, 0, 24, RulerFxEntity.VIOLET);
            say("rewound", ChatFormatting.LIGHT_PURPLE);
            playSound(SoundEvents.BELL_RESONATE, 4.0F, 1.4F);
            Fx.burst(server, MementoMoriEntity.glow(VIOLET, 0.5F, 20), position().add(0, 3.0, 0), 40, 0.4);
        }
    }

    // ---- 刻の終焉 ----

    void startFinale() {
        finaleDone = true;
        finaleLeft = FINALE_TICKS;
        castLock = FINALE_TICKS;
        LivingEntity t = getTarget();
        floorY = t != null ? t.getY() : getY() - 2.0;
        finaleCenter = new Vec3(getX(), floorY + 0.4, getZ());
        if (level() instanceof ServerLevel server) {
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_FINALE, finaleCenter.add(0, 0.05, 0), 28.0F, 0, 0, FINALE_TICKS, RulerFxEntity.GOLD);
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FACING, position().add(0, 16.0, 0), 12.0F, facing(t), 0, FINALE_TICKS, RulerFxEntity.VIOLET);
        }
        say("finale", ChatFormatting.GOLD, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.4F);
        level().broadcastEntityEvent(this, EVENT_FINALE);
    }

    void tickFinale(ServerLevel server) {
        if (finaleLeft <= 0 || TimeStop.active()) return;                              // 止まった時の中では針も止まる（止まっている者を刻まない）
        int t = FINALE_TICKS - finaleLeft--;
        Vec3 o = finaleCenter;
        double hour = t * 0.02, minute = t * 0.06;
        for (double[] hand : new double[][]{{hour, 18}, {minute, 28}}) {
            Vec3 dir = new Vec3(Math.cos(hand[0]), 0, Math.sin(hand[0]));
            if (t % 4 == 0) Fx.one(server, MementoMoriEntity.glow(hand[1] > 20 ? GOLD : PALE, 0.35F, 8), o.add(dir.scale(random.nextDouble() * hand[1])).add(0, 0.3, 0), Vec3.ZERO); // 針は RulerFxEntity が描く
            if (t % 10 != 0) continue;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(hand[1], 2, hand[1]), ChronoGazer::canHurt)) {
                Vec3 rel = e.position().subtract(o).multiply(1, 0, 1);
                double along = rel.dot(dir);
                if (along < 0 || along > hand[1]) continue;
                boolean low = e.getBoundingBox().minY < o.y + 0.5; // 床すれすれ（跳べば越える）
                if (rel.subtract(dir.scale(along)).length() < 1.0 && low) e.hurt(damageSources().mobAttack(this), damageFor(e, HANDS));
            }
        }
        if (t % 40 == 20 && getTarget() != null) spikes(getTarget());
        if (t % 20 == 0) playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 3.0F, 0.5F);
    }

    // ---- 時間差の技 ----

    void tickStrikes(ServerLevel server) {
        for (Iterator<Strike> it = new ArrayList<>(strikes).iterator(); it.hasNext(); ) {
            Strike s = it.next();
            int left = s.fire() - tickCount;
            if (s.kind() != STOP_BEGIN && TimeStop.active() && left < 30) { // 止まった時の中では当てない（動き出してから）
                strikes.remove(s);
                strikes.add(new Strike(s.kind(), s.at(), s.fire() + 1, s.follow()));
                continue;
            }
            if (s.kind() == ERASE_FALL && s.follow() != null) { // 追いかける印: 落ちる 2 秒前に、その時いる座標へ印を置く
                if (left <= 40) {
                    strikes.remove(s);
                    strikes.add(new Strike(ERASE_FALL, s.follow().position(), s.fire(), null));
                }
                continue;
            }
            if ((s.kind() == SPIKE_FALL || s.kind() == ERASE_FALL || s.kind() == JUDGE_FALL) && left > 1 && tickCount % 2 == 0 && inHaste(s.at())) {   // 加速の場の中では倍の速さで落ちる
                strikes.remove(s);
                s = new Strike(s.kind(), s.at(), s.fire() - 1, s.follow());
                strikes.add(s);
                left--;
            }
            switch (s.kind()) {
                case SPIKE_FALL -> {
                    if (left > 0 && tickCount % 3 == 0) KeeperFx.ring(server, s.at().add(0, 0.15, 0), 2.0, GOLD, 16);
                    if (left <= 0) {
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 0.7F, 0, 0, 16, RulerFxEntity.GOLD);
                        Fx.shockwave(server, s.at().add(0, 0.2, 0), GOLD, 3.0F, 12);
                        hitAround(s.at(), 2.0, SPIKE, false);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.LIGHTNING_BOLT_IMPACT, getSoundSource(), 1.2F, 1.6F);
                    }
                }
                case ERASE_FALL -> {
                    Vec3 at = s.at();
                    if (left > 0 && left <= 40 && !TimeStop.active() && eraseMarked.add(at)) RulerFxEntity.spawn(server, RulerFxEntity.CUBE, 0, at, 1.6F, 0, 0, left, RulerFxEntity.RED);
                    if (left <= 0) {
                        eraseMarked.remove(at);
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, at, 1.1F, 0, 0, 22, RulerFxEntity.VIOLET);
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, at, 0.5F, 0, 0, 14, RulerFxEntity.WHITE);
                        Fx.shockwave(server, at.add(0, 0.2, 0), WHITE, 3.0F, 12);
                        hitAround(at, 1.6, ERASE, true);
                        server.playSound(null, BlockPos.containing(at), SoundEvents.WARDEN_SONIC_BOOM, getSoundSource(), 1.5F, 1.6F);
                    }
                }
                case STOP_BEGIN -> {
                    if (left <= 0) stopTime(server);
                }
                case JUDGE_FALL -> {
                    if (left <= 0) {
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 3.0F, 0, 0, 30, RulerFxEntity.VIOLET);
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 1.4F, 0, 0, 22, RulerFxEntity.WHITE);
                        RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT, s.at().add(0, 0.15, 0), 6.0F, 0, 0, 20, RulerFxEntity.WHITE);
                        Fx.shockwave(server, s.at().add(0, 0.3, 0), VIOLET, 8.0F, 24);
                        server.sendParticles(ParticleTypes.FLASH, s.at().x, s.at().y + 1.0, s.at().z, 1, 0, 0, 0, 0);
                        hitAround(s.at(), 4.0, ERASE * 0.75F, true);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 2.0F, 0.6F);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.BELL_RESONATE, getSoundSource(), 3.0F, 0.5F);
                    }
                }
                case SLASH_SPIN -> {
                    if (left <= 0) {
                        RulerFxEntity.spawn(server, RulerFxEntity.SLASH, 0, position().add(0, 1.4, 0), 9.0F, 0, 0, 16, RulerFxEntity.GOLD);
                        RulerFxEntity.spawn(server, RulerFxEntity.SLASH, 0, position().add(0, 1.4, 0), 9.0F, (float) Math.PI, 0, 16, RulerFxEntity.GOLD);
                        RulerFxEntity.spawn(server, RulerFxEntity.SLASH, 0, position().add(0, 0.6, 0), 7.5F, (float) Math.PI / 2, 0, 14, RulerFxEntity.VIOLET);
                        RulerFxEntity.spawn(server, RulerFxEntity.SLASH, 0, position().add(0, 0.6, 0), 7.5F, (float) -Math.PI / 2, 0, 14, RulerFxEntity.VIOLET);
                        Fx.shockwave(server, position().add(0, 1.0, 0), GOLD, 9.0F, 24);
                        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(9.5), ChronoGazer::canHurt)) {
                            if (e.position().subtract(position()).multiply(1, 0, 1).length() < 9.0) e.hurt(damageSources().mobAttack(this), damageFor(e, SLASH * 1.2F));
                        }
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.4F);
                        playSound(SoundEvents.BELL_RESONATE, 3.0F, 1.6F);
                    }
                }
                case SLASH_HIT -> {
                    if (left <= 0) {
                        level().broadcastEntityEvent(this, EVENT_SLASH); // 続く一撃も構える
                        slashFrom(server, position(), getYRot());
                    }
                }
                case BLADE_CAST -> {                                                    // 第 3 形態の 2 本目: 相手の今いる所へ、別の向きから
                    if (left <= 0 && s.follow() instanceof LivingEntity t && t.isAlive()) {
                        double a = s.at().x;
                        Vec3 pivot = t.position().add(Math.cos(a) * 16.0, 0, Math.sin(a) * 16.0);
                        spawnBlade(server, pivot, Math.atan2(t.getZ() - pivot.z, t.getX() - pivot.x), BLADE_SWING_2);
                    }
                }
                case GEAR_CAST -> {                                                     // 第 3 形態の 2 組目（直角）
                    if (left <= 0 && s.follow() instanceof LivingEntity t && t.isAlive()) spawnGears(server, t, s.at().x);
                }
                case ECHO_NEEDLE -> {
                    if (left <= 0 && s.follow() instanceof RulerEchoEntity echo && echo.isAlive() && getTarget() != null) {
                        needleRing(echo.position(), getTarget(), 6);
                        level().broadcastEntityEvent(echo, EVENT_NEEDLE);
                    }
                }
                case ECHO_SLASH -> {                                                    // 残像が相手の背後へ跳んで構える
                    LivingEntity t = getTarget();
                    if (left <= 0 && s.follow() instanceof RulerEchoEntity echo && echo.isAlive() && t != null) {
                        Vec3 back = t.getLookAngle().multiply(1, 0, 1);
                        if (back.lengthSqr() < 1.0e-4) back = new Vec3(1, 0, 0);
                        Vec3 at = t.position().subtract(back.normalize().scale(3.5));
                        Fx.burst(server, MementoMoriEntity.glow(VIOLET, 0.35F, 12), echo.position().add(0, 3.0, 0), 16, 0.2);
                        echo.moveTo(at.x, at.y, at.z, yawTo(at, t.position()), 0.0F);
                        Fx.burst(server, MementoMoriEntity.glow(INDIGO, 0.35F, 12), at.add(0, 3.0, 0), 16, 0.2);
                        level().broadcastEntityEvent(echo, EVENT_SLASH);
                        server.playSound(null, echo.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 1.5F, 0.7F);
                        strikes.add(new Strike(ECHO_SLASH_HIT, Vec3.ZERO, tickCount + 15, echo));
                    }
                }
                case ECHO_SLASH_HIT -> {
                    if (left <= 0 && s.follow() instanceof RulerEchoEntity echo && echo.isAlive()) slashFrom(server, echo.position(), echo.getYRot());
                }
                case MARK_BLAST -> {                                                    // 時限の刻印が弾ける
                    if (left <= 0) {
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 1.0F, 0, 0, 16, RulerFxEntity.VIOLET);
                        Fx.shockwave(server, s.at().add(0, 0.3, 0), VIOLET, 3.5F, 14);
                        hitAround(s.at(), 3.0, MARK_HIT, false);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 1.5F, 1.4F);
                    }
                }
                case DASH_BLINK -> {                                                    // 時針の三連: 相手のそばへ跳んで構える
                    if (left <= 0 && s.follow() instanceof LivingEntity t && t.isAlive()) {
                        double a = random.nextDouble() * Math.PI * 2;
                        Vec3 at = t.position().add(Math.cos(a) * 3.0, 0.3, Math.sin(a) * 3.0);
                        blink(server, at);
                        float yaw = yawTo(at, t.position());
                        setYRot(yaw);
                        yBodyRot = yaw;
                        yHeadRot = yaw;
                        level().broadcastEntityEvent(this, EVENT_SLASH);
                        strikes.add(new Strike(SLASH_HIT, Vec3.ZERO, tickCount + 10, null));
                    }
                }
                case PRISON_FALL -> {                                                   // 砂時計の牢: 中は砂に足を取られ、落ちきると潰れる
                    if (left > 0 && tickCount % 10 == 0) {
                        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(s.at(), s.at()).inflate(PRISON_R, 4.0, PRISON_R), ChronoGazer::canHurt)) {
                            if (inRing(s.at(), PRISON_R, e)) e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, true));
                        }
                        Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.2F, 20), s.at().add(0, 4.0, 0), 10, 0.15);
                    }
                    if (left <= 0) {
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 4.0F, 0, 0, 24, RulerFxEntity.GOLD);
                        Fx.shockwave(server, s.at().add(0, 0.3, 0), GOLD, (float) PRISON_R + 1.0F, 20);
                        Fx.burst(server, MementoMoriEntity.glow(PALE, 0.4F, 24), s.at().add(0, 1.0, 0), 50, 0.35);
                        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(s.at(), s.at()).inflate(PRISON_R, 4.0, PRISON_R), ChronoGazer::canHurt)) {
                            if (!inRing(s.at(), PRISON_R, e)) continue;
                            e.hurt(damageSources().mobAttack(this), damageFor(e, PRISON_HIT));
                            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0));
                        }
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.SAND_BREAK, getSoundSource(), 4.0F, 0.4F);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.BELL_BLOCK, getSoundSource(), 3.0F, 0.6F);
                    }
                }
                case DOOM_CHIME -> {                                                    // 終末時計: k 時を打ち、k の楔（XII 時は終末の光）
                    if (left <= 0 && s.follow() instanceof LivingEntity t && t.isAlive()) {
                        int k = ++doomChime;
                        playSound(SoundEvents.BELL_BLOCK, 5.0F, 0.5F + k * 0.04F);
                        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 96 * 96)) {
                            p.displayClientMessage(Component.literal(romanNumeral(k)).withStyle(k == 12 ? ChatFormatting.DARK_RED : ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                        }
                        if (k < 12) {
                            for (int i = 0; i < k; i++) {
                                double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0.0 : 2.0 + random.nextDouble() * 8.0;
                                Vec3 at = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                                RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT, at.add(0, 0.12, 0), 2.2F, 0, 0, 16, RulerFxEntity.GOLD);
                                strikes.add(new Strike(SPIKE_FALL, at, tickCount + 16, null));
                            }
                        } else {
                            Vec3 at = t.position();
                            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_TO_TWELVE, at.add(0, 0.1, 0), 5.5F, 0, 0, 30, RulerFxEntity.RED);
                            strikes.add(new Strike(DOOM_FINAL, at, tickCount + 30, null));
                            say("doomsday_final", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                        }
                    }
                }
                case DOOM_FINAL -> {
                    if (left <= 0) {
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 5.0F, 0, 0, 34, RulerFxEntity.RED);
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 2.4F, 0, 0, 28, RulerFxEntity.GOLD);
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 0.9F, 0, 0, 22, RulerFxEntity.WHITE);
                        Fx.shockwave(server, s.at().add(0, 0.3, 0), RED, 16.0F, 30);
                        Fx.shockwave(server, s.at().add(0, 2.0, 0), GOLD, 10.0F, 24);
                        server.sendParticles(ParticleTypes.FLASH, s.at().x, s.at().y + 1.0, s.at().z, 1, 0, 0, 0, 0);
                        hitAround(s.at(), 5.0, DOOM_HIT, true);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 5.0F, 0.4F);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.BELL_RESONATE, getSoundSource(), 5.0F, 0.3F);
                    }
                }
                case TWIN_SHOT -> {                                                     // 双子の文字盤から速い針
                    if (left <= 0 && s.follow() instanceof LivingEntity t && t.isAlive()) {
                        Vec3 lead = t.position().add(t.getDeltaMovement().scale(5)).add(0, t.getBbHeight() * 0.5, 0);
                        RulerNeedleEntity.place(this, s.at(), lead, 2, NEEDLE * trialAttack, 2.2F);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.TRIDENT_THROW, getSoundSource(), 1.2F, 1.6F);
                    }
                }
                case DASH_AIM -> {                                                      // 時空疾走: 道を決めて赤く予告
                    if (left <= 0 && s.follow() instanceof LivingEntity t && t.isAlive()) {
                        Vec3 from = new Vec3(getX(), t.getY(), getZ());
                        Vec3 dir = t.position().subtract(from).multiply(1, 0, 1);
                        if (dir.lengthSqr() < 1.0e-4) dir = new Vec3(1, 0, 0);
                        dir = dir.normalize();
                        Vec3 to = from.add(dir.scale(30.0));
                        RulerFxEntity.spawn(server, RulerFxEntity.LANE, 16, from.add(0, 0.05, 0), 30.0F, (float) Math.atan2(dir.z, dir.x), 0, 16 + 14, RulerFxEntity.RED);
                        float yaw = yawTo(from, to);
                        setYRot(yaw);
                        yBodyRot = yaw;
                        yHeadRot = yaw;
                        strikes.add(new Strike(DASH_GO, to, tickCount + 16, null));
                        level().broadcastEntityEvent(this, EVENT_SLASH);
                        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.8F);
                    }
                }
                case DASH_GO -> {                                                       // 一瞬で駆け抜け、道の上を斬る
                    if (left <= 0) {
                        Vec3 from = new Vec3(getX(), s.at().y, getZ()), to = s.at();
                        Vec3 seg = to.subtract(from).multiply(1, 0, 1);
                        double len = seg.length();
                        Vec3 dir = len < 1.0e-4 ? new Vec3(1, 0, 0) : seg.scale(1.0 / len);
                        Fx.line(server, MementoMoriEntity.glow(GOLD, 0.6F, 20), from.add(0, 2.0, 0), to.add(0, 2.0, 0), 0.4);
                        Fx.line(server, MementoMoriEntity.glow(WHITE, 0.3F, 14), from.add(0, 1.0, 0), to.add(0, 1.0, 0), 0.3);
                        for (int i = 0; i < 6; i++) Fx.burst(server, MementoMoriEntity.glow(INDIGO, 0.35F, 12), from.add(dir.scale(len * i / 6)).add(0, 3.0, 0), 6, 0.1);   // 駆け抜けた跡の残像の光
                        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(2.5, 4.0, 2.5), ChronoGazer::canHurt)) {
                            Vec3 rel = e.position().subtract(from).multiply(1, 0, 1);
                            double along = rel.dot(dir);
                            if (along < -1.0 || along > len + 1.0 || rel.subtract(dir.scale(along)).length() > 1.6 + e.getBbWidth() / 2 || Math.abs(e.getY() - from.y) > 4.0) continue;
                            e.hurt(damageSources().mobAttack(this), damageFor(e, DASH_HIT));
                        }
                        moveTo(to.x, to.y + 1.0, to.z, getYRot(), getXRot());
                        setDeltaMovement(dir.scale(0.4));
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F);
                        playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.4F);
                    }
                }
                case RAIN_SPAWN -> {                                                    // 時の雨の雫を置く
                    if (left <= 0 && s.follow() instanceof LivingEntity t && t.isAlive()) {
                        for (int i = 0; i < 2; i++) {
                            Vec3 at;
                            if (i == 0 && random.nextBoolean()) at = t.position().add(t.getDeltaMovement().multiply(1, 0, 1).scale(12));
                            else {
                                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * 11.0;
                                at = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                            }
                            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.HANDS_FAST, at.add(0, 0.1, 0), 1.7F, 0, 0, 14, RulerFxEntity.VIOLET);
                            strikes.add(new Strike(RAIN_FALL, at, tickCount + 14, null));
                        }
                    }
                }
                case RAIN_FALL -> {
                    if (left <= 0) {
                        RulerFxEntity.spawn(server, RulerFxEntity.PILLAR, 0, s.at(), 0.45F, 0, 0, 12, RulerFxEntity.VIOLET);
                        hitAround(s.at(), 1.7, RAIN_HIT, false);
                        if (random.nextInt(3) == 0) server.playSound(null, BlockPos.containing(s.at()), SoundEvents.AMETHYST_BLOCK_BREAK, getSoundSource(), 1.2F, 1.4F);
                    }
                }
                case ECHO_RSHOT -> {
                    if (left <= 0 && s.follow() instanceof RulerEchoEntity echo && echo.isAlive() && getTarget() != null) {
                        rewindFan(echo.position(), getTarget(), 6, 0.0);
                        level().broadcastEntityEvent(echo, EVENT_NEEDLE);
                    }
                }
                default -> {
                }
            }
            if (left <= 0) strikes.remove(s);
        }
    }

    void hitAround(Vec3 at, double r, float damage, boolean erasure) {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 3.0, r), ChronoGazer::canHurt)) {
            if (e.position().multiply(1, 0, 1).distanceToSqr(at.multiply(1, 0, 1)) > r * r) continue;
            DamageSource ds = erasure ? new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ERASURE), this) : damageSources().mobAttack(this);
            e.hurt(ds, damageFor(e, damage));
        }
    }

    // ------------------------------------------------------------------ 受ける・倒れる

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && (!introDone || retreating)) return false;
        if (staggerLeft > 0) amount *= 1.5F;
        if (finaleLeft > 0) amount *= 0.5F;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) {
            protect.markDefeated();
            setHealth(0.0F);
            return;
        }
        if (protect.interceptDie()) return;
        entityData.set(DEFEATED, true);
        super.die(source);
    }

    /**
     * ふっとばし無し（2026-10-20 ユーザー指定「ふっとばしも無くして」）: タイムルーラー（とその針・残像の技）の攻撃を受けた者は、その一撃でノックバックしない。
     * 1.20.1 には no_knockback のダメージの印が無いので、受けた Tick を覚えておき、同じ Tick のノックバックを取り消す
     */
    private static final java.util.Map<java.util.UUID, Integer> NO_KNOCKBACK = new java.util.HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (event.getSource().getEntity() instanceof TimeRulerEntity || event.getSource().getDirectEntity() instanceof RulerNeedleEntity) {
            if (NO_KNOCKBACK.size() > 256) NO_KNOCKBACK.clear();
            NO_KNOCKBACK.put(victim.getUUID(), victim.tickCount);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockBack(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Integer at = NO_KNOCKBACK.remove(victim.getUUID());
        if (at != null && at == victim.tickCount) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof TimeRulerEntity ruler)) return;
        if (event.isCanceled()) event.setCanceled(false);
        if (!event.getDrops().isEmpty()) ruler.lootDropped = true;
    }

    private void ensureLoot() {
        if (lootDropped || !(level() instanceof ServerLevel)) return;
        lootDropped = true;
        DamageSource source = getLastDamageSource() != null ? getLastDamageSource() : damageSources().generic();
        dropFromLootTable(source, getLastHurtByMob() instanceof Player);
    }

    /** 背の文字盤の真ん中（世界の座標） */
    Vec3 dialPos() {
        Vec3 back = Vec3.directionFromRotation(0, yBodyRot).scale(-11.0 / 16.0);
        return position().add(0, 60.0 / 16.0, 0).add(back);
    }

    /**
     * 撃破の演出（{@value #DEFEAT_TICKS} Tick。2026-10-19 ユーザー指定「撃破演出まで自作して作りこんで」）:
     * 0〜20 時が凍ったように固まり、低い鐘。20〜100 文字盤の針が狂ったように逆回り（描く側）、ひびから金の光が漏れ、鐘が下がりながら鳴る。40 光輪が明滅して落ちる。
     * 60〜100 ゆっくり沈んで膝をつく。100〜120 文字盤へ光が集まって膨らみ、120 で砕けて輪の形に破片が飛び散り、光の柱。
     * 140 最後の閃光と大きな衝撃波、画面に大きく「終末の時は止まった」（描く側）と地の文。160 で消え、落とし物。時の狭間なら帰りの門が開く
     */
    @Override
    protected void tickDeath() {
        if (protect.refuseDeathTick()) return; // 撃破を認めていないのに演出を回された（The King Sword 等）
        ++deathTime;
        if (level().isClientSide) {
            if (deathTime == 1) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.automatavalkyries.client.ClientRulerShow.flash(this, 8, 0xFFF0B0));
            if (deathTime == 140) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                com.automatavalkyries.client.ClientRulerShow.flash(this, 24, 0xFFFFFF);
                com.automatavalkyries.client.ClientRulerShow.defeat(this);
            });
            if (deathTime > 20 && deathTime < 120 && deathTime % 2 == 0) {                                  // ひびから漏れる光
                level().addParticle(MementoMoriEntity.glow(GOLD, 0.18F, 18), getRandomX(1.2), getY() + 1.0 + random.nextDouble() * 4.5, getRandomZ(1.2), 0, 0.03, 0);
            }
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        int d = deathTime;
        Vec3 c = position().add(0, 3.0, 0);
        if (d == 1) {
            cleanup(server);
            playSound(SoundEvents.BELL_RESONATE, 4.0F, 0.4F);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        if (d < 20) setDeltaMovement(Vec3.ZERO);                                                         // 時が凍ったように固まる
        if (d == 20) {
            playSound(SoundEvents.GLASS_BREAK, 3.0F, 0.4F);
            Fx.shockwave(server, c, GOLD, 5.0F, 14);
        }
        if (d >= 20 && d < 100) {
            if (d % 4 == 0) Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.3F, 14), c.add(random.nextGaussian() * 0.8, random.nextGaussian() * 1.4, random.nextGaussian() * 0.8), 6, 0.12);
            if (d % 15 == 5) playSound(SoundEvents.BELL_BLOCK, 3.0F, 1.5F - (d - 20) * 0.012F);
        }
        if (d == 40) playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 3.0F, 0.7F);                           // 光輪が落ちる
        if (d >= 60 && d < 100) {                                                                          // ゆっくり沈んで膝をつく
            setDeltaMovement(0, -0.03, 0);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            if (d % 6 == 0) server.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.8, 1.2, 0.8, 0.1);
        }
        Vec3 dial = dialPos();
        if (d == 100) playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.4F);
        if (d >= 100 && d < 120 && d % 2 == 0) Fx.gather(server, MementoMoriEntity.glow(d % 4 == 0 ? GOLD : VIOLET, 0.35F, 10), dial, 8, 4.0, 10);
        if (d == 120) {                                                                                    // 文字盤が砕ける
            Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot), right = new Vec3(-fwd.z, 0, fwd.x);
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48;
                Vec3 dir = right.scale(Math.cos(a)).add(0, Math.sin(a), 0);
                Fx.one(server, MementoMoriEntity.glow(i % 3 == 0 ? VIOLET : GOLD, 0.45F, 26), dial.add(dir.scale(1.9)), dir.scale(0.35));
            }
            Fx.beamRing(server, dial, fwd, GOLD, 4.0F, 20);
            Fx.line(server, MementoMoriEntity.glow(PALE, 0.6F, 30), position(), position().add(0, 36, 0), 0.5);
            playSound(SoundEvents.GLASS_BREAK, 5.0F, 0.6F);
            playSound(SoundEvents.GLASS_BREAK, 5.0F, 1.1F);
            playSound(SoundEvents.ANVIL_DESTROY, 3.0F, 0.8F);
        }
        if (d == 140) {                                                                                    // 最後の閃光・大きな衝撃波・地の文
            Fx.burst(server, MementoMoriEntity.glow(PALE, 0.8F, 30), c, 70, 0.45);
            Fx.shockwave(server, position().add(0, 1.0, 0), GOLD, 22.0F, 34);
            Fx.shockwave(server, c, VIOLET, 12.0F, 26);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            playSound(SoundEvents.BELL_RESONATE, 5.0F, 1.2F);
            playSound(SoundEvents.END_PORTAL_SPAWN, 2.0F, 0.6F);
            speak(server, "defeat");                                                                   // 撃破の地の文（チャット）
        }
        if (d >= DEFEAT_TICKS && !DirectRead.isRemoved(this)) {
            Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.5F, 20), c, 30, 0.2);
            ensureLoot();
            if (TimeRift.isRift(server)) TimeRift.onRulerDefeated(server);
            protect.finishDeath();
            remove(RemovalReason.KILLED);
        }
    }

    /** 自分の技の後始末（時間停止を解き、歯車・針を消す） */
    void cleanup(ServerLevel server) {
        strikes.clear();
        eraseMarked.clear();
        sweepLeft = 0;
        finaleLeft = 0;
        rewindLeft = 0;
        stopAt = -1;
        if (TimeStop.active()) TimeStop.stop();
        for (RulerAnchorEntity a : anchors(server)) a.discard();
        for (RulerNeedleEntity n : server.getEntitiesOfClass(RulerNeedleEntity.class, getBoundingBox().inflate(64.0))) n.discard();
        blades.clear();
        for (GearPair g : gears) {
            g.a.discard();
            g.b.discard();
        }
        gears.clear();
        fields.clear();
        for (RulerEchoEntity e : echoes) e.discard();
        echoes.clear();
        for (RulerFxEntity fx : pendulums) fx.discard();
        pendulums.clear();
        sectors.clear();
        maelLeft = 0;
        CausalLock.releaseBy(this);                                                   // 倒れた・退いたら因果停止も解く
    }

    @Override
    public Component getDefeatSpeech() {
        return line("retreat");                                                    // 退くとき（Eprotect の正規退場がチャットに出す）
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
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    /** 回想のトライアルのボスにする（自分では退かず、保存もしない。技のダメージは attack 倍） */
    public void setTrialBoss(float attack) {
        this.trialBoss = true;
        this.trialAttack = attack;
    }

    @Override
    public boolean shouldBeSaved() {
        return !trialBoss && super.shouldBeSaved();
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
        tag.putBoolean("Rewind2", rewind2Done);
        tag.putBoolean("Rewind3", rewind3Done);
        tag.putBoolean("Finale", finaleDone);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase")) entityData.set(PHASE, (byte) Math.max(1, Math.min(3, tag.getByte("Phase"))));
        introDone = tag.getBoolean("IntroDone");
        rewind2Done = tag.getBoolean("Rewind2");
        rewind3Done = tag.getBoolean("Rewind3");
        finaleDone = tag.getBoolean("Finale");
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
    protected SoundEvent getAmbientSound() {
        return SoundEvents.NOTE_BLOCK_BELL.value();
    }

    @Override
    protected float getSoundVolume() {
        return 1.5F;
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
     * 相手の斜め上 10〜16 m に浮かび、ときどき時間跳躍で回り込む。時間停止の時が来ればそれを、ほかは使える技の中から選ぶ。
     * 選び方（2026-10-20 ユーザー指定「形態変形してからも万遍なく技を使うようにして」）: 間の明けた技のうち、いちばん長く使っていないものを選ぶ（同じなら乱数）。
     * 形態に入ったばかりの技はまだ一度も使っていないので先に一巡し、それから古い技も新しい技も順に回る。技と技の間は少なくとも {@value #GAP} Tick（形態で短く）
     */
    static class RulerGoal extends Goal {
        static final int GAP = 16;
        private final TimeRulerEntity boss;
        private int blinkCd = 120, repath, gap, seenPhase = 1;
        @Nullable
        private LivingEntity cur;
        private double dist;

        /** 1 つの技（使える形態・間・はじめの間・使える条件・使う。strict なら刻の終焉の中でも条件を守る＝分身が出ている・渦がある時は重ねない） */
        private static final class Move {
            final int minPhase, base;
            int cd, last = Integer.MIN_VALUE / 2;
            final java.util.function.BooleanSupplier ok;
            final Runnable act;
            boolean strict;

            Move(int minPhase, int base, int first, java.util.function.BooleanSupplier ok, Runnable act) {
                this.minPhase = minPhase;
                this.base = base;
                this.cd = first;
                this.ok = ok;
                this.act = act;
            }
        }

        private final List<Move> moves = new ArrayList<>();
        /** 刻の終焉で使う残りの技（始まった時に全ての技を乱数で並べ、最後に全世界時間停止）。null なら終焉の外 */
        @Nullable
        private java.util.ArrayDeque<Runnable> finaleQueue;
        private int finaleStep;
        static final int FINALE_STEP = 12;

        RulerGoal(TimeRulerEntity boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            java.util.function.BooleanSupplier any = () -> true, sight = () -> boss.hasLineOfSight(cur);
            // 第 1 形態から（間は第 2 形態 0.8 倍・第 3 形態 0.6 倍）
            add(1, 60, 0, () -> dist < 5.0, boss::slash);                                       // 時針の一閃（近い時）
            add(1, 560, 300, any, () -> boss.barrage(cur, boss::causalBlade));                                 // 因果断ちの大剣
            add(1, 320, 200, any, () -> boss.barrage(cur, boss::gearPincer));                                  // 時の歯車の挟撃
            add(1, 240, 160, any, () -> boss.sweep(cur));                                       // 文字盤の掃射
            add(1, 120, 90, any, () -> boss.barrage(cur, boss::spikes));                                       // 時の楔
            add(1, 220, 120, sight, () -> boss.barrage(cur, boss::rewindShot));                                // 巻き戻しの弾
            add(1, 70, 40, sight, () -> boss.barrage(cur, boss::needles));                                     // 剪定の針
            // 第 2 形態から
            add(2, 700, 0, () -> boss.echoes.isEmpty(), () -> boss.summonEchoes(cur)).strict = true;   // 残像の分身
            add(2, 380, 0, any, () -> boss.barrage(cur, e -> boss.judgment(e.position(), 20)));                       // 十二時の審判
            add(2, 220, 0, any, () -> boss.barrage(cur, e -> boss.erase(e, 40)));                                     // 確定座標消去
            add(2, 560, 0, any, () -> boss.timeFields(cur));                                    // 加速と減速の場
            add(2, 260, 0, sight, () -> boss.barrage(cur, boss::dialBarrage));                                 // 文字盤の針雨
            add(2, 700, 0, () -> boss.maelLeft <= 0, () -> boss.maelstrom(cur)).strict = true;   // 大技 逆巻く時の大渦
            add(2, 360, 0, any, () -> boss.barrage(cur, boss::pendulum));                                      // 振り子の断頭台
            add(2, 420, 0, any, () -> boss.barrage(cur, boss::hourglassPrison));                               // 砂時計の牢
            add(2, 300, 0, () -> dist < 18.0, () -> boss.handBarrage(cur));                     // 時針の三連
            add(2, 260, 0, any, () -> boss.barrage(cur, boss::delayMarks));                                    // 時限の刻印
            // 第 3 形態から
            add(3, 800, 0, any, () -> boss.doomsday(cur));                                      // 大技 終末時計
            add(3, 360, 0, any, () -> boss.barrage(cur, boss::twinDials));                                     // 双子の文字盤
            add(3, 380, 0, any, () -> boss.phaseDash(cur));                                     // 時空疾走
            add(3, 420, 0, any, () -> boss.crumblingDial(cur));                                 // 崩れる文字盤
            add(3, 400, 0, any, () -> boss.barrage(cur, boss::hourRain));                                      // 時の雨
        }

        private Move add(int minPhase, int base, int first, java.util.function.BooleanSupplier ok, Runnable act) {
            Move m = new Move(minPhase, base, first, ok, act);
            moves.add(m);
            return m;
        }

        /**
         * 刻の終焉（2026-10-20 ユーザー指定「時の終焉で全ての技を使うようにして」）: 全ての技（第 1〜第 3 形態の 22）を、間を無視して {@value #FINALE_STEP} Tick ごとに 1 つずつ
         * （順は乱数）使い、最後に全世界時間停止。技の詠唱の間も止まらない。分身・大渦は出ている間は重ねない
         */
        private void tickFinaleMoves(ServerLevel server) {
            if (finaleQueue == null) {
                List<Move> order = new ArrayList<>(moves);
                java.util.Collections.shuffle(order, new java.util.Random(boss.random.nextLong()));
                finaleQueue = new java.util.ArrayDeque<>();
                for (Move m : order) finaleQueue.add(() -> {
                    if (!m.strict || m.ok.getAsBoolean()) {
                        m.last = boss.tickCount;
                        m.act.run();
                    }
                });
                finaleQueue.add(boss::announceStop);                                    // 締めくくりに全世界時間停止
                finaleStep = 20;
            }
            if (--finaleStep > 0 || finaleQueue.isEmpty()) return;
            finaleStep = FINALE_STEP;
            finaleQueue.poll().run();
            boss.castLock = boss.finaleLeft;                                            // 技の詠唱の間で終焉の後まで止まらないように
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
            if (t == null || !(boss.level() instanceof ServerLevel server)) return;
            boss.getLookControl().setLookAt(t, 30.0F, 30.0F);
            for (Move m : moves) m.cd--;
            blinkCd--;
            gap--;
            boss.stopCd--;
            if (boss.phase() != seenPhase) {                                            // 形態に入った: 新しい技を、因果逆転の詠唱の後へ少しずつずらして明ける
                seenPhase = boss.phase();
                int k = 0;
                for (Move m : moves) if (m.minPhase == seenPhase) m.cd = REWIND_TICKS + 20 + 30 * k++;
            }
            double d = boss.distanceTo(t);
            if (--repath <= 0) { // 相手の斜め上に
                repath = 20;
                Vec3 away = boss.position().subtract(t.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
                // 宿命の結び糸で他の生き物と戦う間は、床の高さで 6 m まで寄る（2026-10-20 ユーザー指定「相手が攻めてこない」: 宙の 12 m 先に浮かぶと、
                // 歩いて近づいて殴る他の MOD のボスは道が見つからず立ち尽くしていた）
                boolean grounded = com.automatavalkyries.entity.FateDuels.dueling(boss) && !(t instanceof Player);
                Vec3 spot = t.position().add(away.normalize().scale(grounded ? 6.0 : 12.0)).add(0, grounded ? 0.0 : 3.0, 0);
                boss.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
            }
            if (TimeStop.active()) return; // 止まった時の中では、並べた針と印のほかは手を出さない
            cur = t;
            dist = d;
            if (boss.finaleLeft > 0) {                                                  // 刻の終焉: 全ての技を順に
                tickFinaleMoves(server);
                return;
            }
            finaleQueue = null;
            if (boss.castLock > 0 || boss.sweepLeft > 0 || boss.staggerLeft > 0) return;
            if (boss.stopCd <= 0) {
                boss.stopCd = boss.phase() == 1 ? 900 : boss.phase() == 2 ? 800 : 600; // 45 / 40 / 30 秒ごと
                boss.announceStop();
                return;
            }
            if (blinkCd <= 0 && d > 20.0) { // 離れすぎたら回り込む
                blinkCd = 100;
                double a = boss.random.nextDouble() * Math.PI * 2;
                boss.blink(server, t.position().add(Math.cos(a) * 10, 3.0, Math.sin(a) * 10));
                return;
            }
            if (gap > 0) return;
            Move pick = null;
            for (Move m : moves) {                                                     // 間の明けた技のうち、いちばん長く使っていないもの
                if (boss.phase() < m.minPhase || m.cd > 0 || !m.ok.getAsBoolean()) continue;
                if (pick == null || m.last < pick.last || (m.last == pick.last && boss.random.nextBoolean())) pick = m;
            }
            if (pick == null) return;
            pick.cd = boss.cd(pick.base);
            pick.last = boss.tickCount;
            gap = boss.cd(GAP);
            pick.act.run();
        }
    }

    /** 地面の輪（要塞守護機と同じ描き方） */
    static final class KeeperFx {
        static void ring(ServerLevel server, Vec3 at, double r, int color, int count) {
            BastionKeeperEntity.ring(server, at, r, color, count);
        }
    }
}
