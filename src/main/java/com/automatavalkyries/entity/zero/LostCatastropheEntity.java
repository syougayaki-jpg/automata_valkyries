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
    public static final int CHANT_TICKS = 1200, SHOCK_TICKS = 60, STUN_TICKS = 200, INTRO_TICKS = 100, DEFEAT_TICKS = 160, HACK_TICKS = 200;
    public static final double DRAIN_RANGE = 24.0;
    /** 描く大きさの倍率と、胸の高さ（ブロック） */
    public static final float SIZE = 1.25F;
    /** 頭の大きさの倍率（描く側の LostCatastropheModel が頭の骨に掛ける）。2026-10-21 から頭も OBJ の面（ユーザー指定「顔を同じように変換して」）で
     *  体と同じ縮尺なので 1（箱の YSM 風の頭に戻すときは 0.8 で約 6 頭身） */
    public static final float HEAD_SCALE = 1.0F;
    /** 頭上の光輪の中心（geo の px）・半径（px）・傾き（geo で Rz(HALO_AZ)・Rx(HALO_T)）。tools/lc_objmesh.py の HALO_C・HALO_R・HALO_TILT */
    static final double HALO_X = 0.0, HALO_Y = 34.9, HALO_Z = 0.6, HALO_R = 3.6, HALO_T = -0.25, HALO_AZ = 0.0;
    static final double CHEST = 1.8;
    public static final float SLASH = 30.0F, BEAM = 36.0F, NEEDLE = 10.0F, RAIN = 12.0F, HOLE_TICK = 4.0F, HOLE_CRUSH = 30.0F;
    public static final ResourceKey<DamageType> ZERO_ERASURE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "zero_erasure"));
    static final int RED = 0xFF2A44, MAGENTA = 0xFF2A9A, VIOLET = 0xB060FF, WHITE = 0xFFF0F0, DARK = 0x200010;
    /** 描く側の技の構え（エンティティの出来事 90〜） */
    public static final byte EV_SLASH = 90, EV_BEAM = 91, EV_SPIRAL = 92, EV_BLADE = 93, EV_RAIN = 94, EV_HOLE = 95, EV_HACK = 96, EV_STOP = 97, EV_SHOCK = 98, EV_INTRO = 99;

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CHANT = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STUN = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SHIELD = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SHOCK = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.INT);
    /** 第 2 形態から空に開く裂け目と浮かぶ残骸の中心（2026-10-20 ユーザー指定「裂け目と残骸を形態変化にとっておく」「第 2 形態からずっと」） */
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> RIFT = SynchedEntityData.defineId(LostCatastropheEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    /** 乗っ取った兵姫 → 解ける時刻（サーバーの Tick） */
    static final Map<UUID, Long> HACKED = new HashMap<>();

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent;
    private final com.automatavalkyries.entity.BossRetreatWatch retreatWatch = new com.automatavalkyries.entity.BossRetreatWatch(64.0, 200);

    /** 描く側: 技ごとの構えの時刻（EV_* - 90） */
    public final int[] animTick = new int[16];
    public int introTick = -10000;

    private boolean introDone, lootDropped, chant2Done, chant3Done, retreating, trialBoss;
    private float trialAttack = 1.0F;
    private int introTicks, retreatTicks, castLock, gap;
    @Nullable
    private Vec3 home;

    private record Strike(int kind, Vec3 at, int fire, @Nullable Entity follow, double extra) {}

    private static final int DASH_AIM = 0, DASH_GO = 1, BEAM_FIRE = 2, SPIRAL_WAVE = 3, BLADE_FALL = 4, RAIN_SPAWN = 5, RAIN_FALL = 6, STOP_BEGIN = 7;
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
        if (PHASE.equals(key) && level().isClientSide) phaseChangedTick = tickCount;
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
            return;
        }
        super.handleEntityEvent(id);
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
        if (!trialBoss && !retreating && retreatWatch.tick(this)) beginRetreat();
    }

    /** まとわりつく赤と紫の光の粒、頭上の光輪ににじむ桃色と紫の光 */
    void clientFx() {
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
        say("phase" + to, ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        playSound(SoundEvents.WITHER_SPAWN, 4.0F, to == 2 ? 0.7F : 0.5F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.62), getZ(), 1, 0, 0, 0, 0);
            Fx.shockwave(server, position().add(0, 1.0, 0), RED, 18.0F, 30);
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

    /** 出現（{@value #INTRO_TICKS} Tick、受けない）: 空に赤い亀裂が開き、黒い渦から光の欠片が集まって形になる。光輪が灯り、翼が開く */
    void tickIntro(ServerLevel server) {
        int t = introTicks++;
        Vec3 c = position().add(0, CHEST, 0);
        if (t == 0) {
            level().broadcastEntityEvent(this, EV_INTRO);
            if (home == null) home = position();
            playSound(SoundEvents.WITHER_SPAWN, 5.0F, 0.4F);
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_REVERSE, position().add(0, 0.1, 0), 12.0F, 0, 0, INTRO_TICKS, RED);
        }
        if (t < 70 && t % 2 == 0) Fx.gather(server, MementoMoriEntity.glow(t % 4 == 0 ? RED : VIOLET, 0.4F, 14), c, 8, 9.0 - t * 0.1, 14);
        if (t % 12 == 0 && t < 80) playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.4F + t * 0.01F);
        if (t == 80) {
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            Fx.shockwave(server, position().add(0, 0.5, 0), RED, 20.0F, 30);
            Fx.shockwave(server, c, VIOLET, 12.0F, 24);
            playSound(SoundEvents.END_PORTAL_SPAWN, 3.0F, 0.6F);
            speak("intro", ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
        }
        if (t >= INTRO_TICKS) introDone = true;
    }

    @Override
    public boolean beginRetreat() {
        if (!isAlive() || isDefeated() || retreating || protect.isDespawning()) return false;
        retreating = true;
        retreatTicks = 0;
        playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.4F);
        return true;
    }

    void tickRetreat(ServerLevel server) {
        int t = retreatTicks++;
        if (t < 40 && t % 2 == 0) Fx.gather(server, MementoMoriEntity.glow(RED, 0.35F, 12), position().add(0, CHEST, 0), 6, 6.0, 12);
        if (t >= 45) {
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
            int secs = left / 20;
            if (secs <= 10 || secs % 10 == 0) {
                for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 128 * 128)) {
                    p.displayClientMessage(Component.translatable("entity.automata_valkyries.lost_catastrophe.chant_count", secs).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                }
            }
        }
        if (tickCount % 4 == 0) Fx.mandala(server, position().add(0, CHEST, 0), RED, 3.5F, 6, true, true);
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

    /** 詠唱を止められなかった: この次元のプレイヤーは倒れ（不死のトーテムも効かない）、兵姫は機能停止。HP と障壁が戻る */
    public void eraseWorld(ServerLevel server) {
        speak("erased", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.62), getZ(), 1, 0, 0, 0, 0);
        Fx.shockwave(server, position(), WHITE, 60.0F, 40);
        Fx.shockwave(server, position(), RED, 40.0F, 36);
        playSound(SoundEvents.GENERIC_EXPLODE, 6.0F, 0.3F);
        DamageSource ds = new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ZERO_ERASURE), this);
        boolean grave = com.automatavalkyries.world.ZeroGrave.isGrave(server);           // 零の墓標なら次元の全員、ほかの所（卵・トライアル）は 96 m 以内
        for (ServerPlayer p : server.players()) {
            if (p.isCreative() || p.isSpectator() || !grave && p.distanceToSqr(this) > 96 * 96) continue;
            p.setAbsorptionAmount(0.0F);
            p.invulnerableTime = 0;
            p.hurt(ds, Math.max(1000.0F, p.getMaxHealth() * 10.0F));
        }
        for (Entity e : server.getAllEntities()) {
            if (e instanceof ValkyrieEntity v && !v.isShutdown() && (grave || v.distanceToSqr(this) <= 96 * 96)) v.shutdown();
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
                if (e instanceof ServerPlayer p) p.connection.teleport(step.x, 0, step.z, 0, 0, java.util.EnumSet.allOf(net.minecraft.world.entity.RelativeMovement.class));
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
            hitAround(c, 4.0, HOLE_CRUSH, true);
            server.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 4.0F, 0.4F);
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
                strikes.add(new Strike(s.kind(), s.at(), s.fire() + 1, s.follow(), s.extra()));
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
                    playSound(SoundEvents.TRIDENT_RIPTIDE_1, 2.0F, 1.2F);
                }
                case BLADE_FALL -> {
                    Vec3 d = new Vec3(Math.cos(s.extra()), 0, Math.sin(s.extra()));
                    Vec3 end = s.at().add(d.scale(TimeRulerEntity.BLADE_LEN));
                    for (int i = 0; i <= 8; i++) Fx.shockwave(server, s.at().add(d.scale(TimeRulerEntity.BLADE_LEN * i / 8.0)).add(0, 0.3, 0), i % 2 == 0 ? RED : WHITE, 3.5F, 16);
                    server.sendParticles(ParticleTypes.FLASH, end.x, end.y + 1, end.z, 1, 0, 0, 0, 0);
                    playSound(SoundEvents.ANVIL_LAND, 4.0F, 0.3F);
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
                    hitAround(s.at(), 1.7, RAIN, false);
                }
                case STOP_BEGIN -> {
                    TimeStop.start(server, this, 100);
                    speak("stopped", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
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
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ 受ける・倒れる

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            if (!introDone || retreating || chantLeft() > 0) return false;                  // 詠唱の間は一切受けない
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
        if (chantLeft() > 0) {                                                           // 詠唱の間は倒れない（コマンドも）
            setHealth(1.0F);
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
     * 撃破（{@value #DEFEAT_TICKS} Tick）: 時が止まったように固まり、全身から赤いデータの欠片が剥がれ、光輪が砕けて落ちる。ゆっくり沈んで膝をつき、
     * 面頬の赤い光が消えて白い光に変わる（怨嗟が抜ける）。最後に白い閃光で弾けて落とし物
     */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level().isClientSide) {
            if (deathTime > 10 && deathTime < 130 && deathTime % 2 == 0) {
                level().addParticle(MementoMoriEntity.glow(deathTime < 90 ? RED : WHITE, 0.2F, 20), getRandomX(1.0), getY() + random.nextDouble() * 2.6, getRandomZ(1.0), 0, 0.04, 0);
            }
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        int d = deathTime;
        Vec3 c = position().add(0, CHEST, 0);
        if (d == 1) {
            cleanup(server);
            playSound(SoundEvents.WITHER_DEATH, 5.0F, 0.5F);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        if (d < 30) setDeltaMovement(Vec3.ZERO);
        if (d == 40) playSound(SoundEvents.GLASS_BREAK, 4.0F, 0.5F);                       // 光輪が砕ける
        if (d >= 50 && d < 110) {
            setDeltaMovement(0, -0.03, 0);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        }
        if (d == 100) speak("defeat", ChatFormatting.WHITE, ChatFormatting.ITALIC);
        if (d == 140) {
            Fx.burst(server, MementoMoriEntity.glow(WHITE, 0.8F, 30), c, 80, 0.45);
            Fx.shockwave(server, position().add(0, 1.0, 0), WHITE, 24.0F, 34);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            playSound(SoundEvents.BELL_RESONATE, 5.0F, 1.4F);
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
                if (boss.chantLeft() > 0 && boss.home != null) spot = boss.home.add(0, 6.0, 0);   // 詠唱は闘技場の真ん中の上で
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
