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
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
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
 *   <li>刻の終焉（第 3 形態で HP 10% を切ったら 1 度だけ、15 秒）: 床に時計の長針と短針の光が回り（低いので跳べば越えられる）、時の楔が降る。その間は受けるダメージ半分</li>
 * </ul>
 * 倒れると文字盤が割れて歯車が落ち、砕けて光になる。落とし物は loot_tables/entities/time_ruler、時の狭間なら帰りの門が開く（{@link TimeRift}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class TimeRulerEntity extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech, com.automatavalkyries.entity.RetreatableBoss {

    public static final float MAX_HP = 80000.0F, NEEDLE = 10.0F, STOP_NEEDLE = 6.0F, SWEEP = 8.0F, SPIKE = 14.0F, ERASE = 40.0F, SLASH = 20.0F, HANDS = 12.0F;
    public static final int STOP_TICKS = 160, STOP_WARN = 40, REWIND_TICKS = 160, FINALE_TICKS = 300, INTRO_TICKS = 60, DEFEAT_TICKS = 80;
    public static final ResourceKey<DamageType> ERASURE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "time_erasure"));
    public static final byte EVENT_NEEDLE = 90, EVENT_SWEEP = 91, EVENT_STOP = 92, EVENT_ERASE = 93, EVENT_REWIND = 94, EVENT_SLASH = 95, EVENT_FINALE = 96;
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

    private boolean trialBoss, introDone, lootDropped, rewind2Done, rewind3Done, finaleDone, retreating;
    private float trialAttack = 1.0F;
    private int introTicks, retreatTicks;
    /** 1 秒ごとの体力の記録（因果逆転で 20 秒前へ戻すため） */
    private final float[] history = new float[30];
    private int historyAt;
    private int rewindLeft, staggerLeft, finaleLeft, stopCd = 500, castLock;
    private double floorY = Double.NaN;

    /** 時間差で起きる技 */
    private record Strike(int kind, Vec3 at, int fire, @Nullable Entity follow) {}

    private static final int SPIKE_FALL = 0, ERASE_FALL = 1, STOP_BEGIN = 2, SLASH_HIT = 3;
    private final List<Strike> strikes = new ArrayList<>();
    /** 掃射の状態（残り Tick と回る向き） */
    private int sweepLeft, sweepWarn;
    private double sweepAngle, sweepSpin;

    public TimeRulerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
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
            default -> super.handleEntityEvent(id);
        }
    }

    /** 技のダメージ（プレイヤーは最大 HP に応じて上げ、回想のトライアルでは倍率） */
    float damageFor(LivingEntity target, float damage) {
        return LuxionEntity.scaledFor(target, damage * trialAttack);
    }

    void say(String key, ChatFormatting... style) {
        if (!(level() instanceof ServerLevel server)) return;
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
            return;
        }
        if (castLock > 0) castLock--;
        if (staggerLeft > 0 && --staggerLeft % 10 == 0) Fx.spray(server, MementoMoriEntity.glow(VIOLET, 0.25F, 10), position().add(0, 3.0, 0), 6, 1.0, 0.05);
        tickSweep(server);
        tickRewind(server);
        tickFinale(server);
        tickStrikes(server);
    }

    // ---- 現れる・退く ----

    void tickIntro(ServerLevel server) {
        int t = introTicks++;
        Vec3 c = position().add(0, 3.0, 0);
        if (t == 0) {
            playSound(SoundEvents.BELL_BLOCK, 4.0F, 0.5F);
            say("intro", ChatFormatting.GOLD, ChatFormatting.ITALIC);
        }
        if (t < 50) {
            for (int i = 0; i < 4; i++) {
                double a = t * 0.25 + i * Math.PI / 2;
                double r = 8.0 * (1.0 - t / 55.0);
                Vec3 p = c.add(Math.cos(a) * r, Math.sin(t * 0.2 + i) * 2.0, Math.sin(a) * r);
                Fx.one(server, MementoMoriEntity.glow(i % 2 == 0 ? GOLD : INDIGO, 0.4F, 14), p, c.subtract(p).scale(0.06));
            }
        }
        if (t % 10 == 0) playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 2.0F, 0.5F + t / 120.0F);
        if (t == 30) Fx.mandala(server, position().add(0, 0.2, 0), GOLD, 8.0F, 60, false, true);
        if (t >= INTRO_TICKS) {
            introDone = true;
            Fx.burst(server, MementoMoriEntity.glow(PALE, 0.6F, 24), c, 50, 0.3);
            Fx.shockwave(server, c, GOLD, 10.0F, 20);
            playSound(SoundEvents.BELL_RESONATE, 4.0F, 1.0F);
        }
    }

    @Override
    public boolean beginRetreat() {
        if (!isAlive() || isDefeated() || retreating || protect.isDespawning()) return false;
        retreating = true;
        retreatTicks = 0;
        say("retreat", ChatFormatting.GOLD, ChatFormatting.ITALIC);
        return true;
    }

    void tickRetreat(ServerLevel server) {
        if (retreatTicks++ % 4 == 0) Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.4F, 16), position().add(0, 3.0, 0), 10, 0.2);
        if (retreatTicks >= 40) {
            cleanup(server);
            protect.triggerDespawn();
        }
    }

    // ---- 技 ----

    /** 剪定の針: 周りの針を、相手の動きを少し読んだ所へ */
    void needles(LivingEntity t) {
        int n = phase() == 3 ? 12 : phase() == 2 ? 8 : 6;
        Vec3 lead = t.position().add(t.getDeltaMovement().scale(8)).add(0, t.getBbHeight() * 0.5, 0);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + tickCount * 0.05;
            Vec3 from = position().add(Math.cos(a) * 3.2, 3.5 + Math.sin(a * 2) * 0.8, Math.sin(a) * 3.2);
            RulerNeedleEntity.place(this, from, lead, 8 + i * 2, NEEDLE * trialAttack, 1.6F);
        }
        playSound(SoundEvents.TRIDENT_RIPTIDE_1, 2.0F, 1.6F);
        level().broadcastEntityEvent(this, EVENT_NEEDLE);
    }

    /** 文字盤の掃射: 1 秒の予告の後、12 本の光線を回す */
    void sweep(LivingEntity t) {
        floorY = t.getY();
        sweepWarn = 20;
        sweepLeft = 60;
        sweepAngle = random.nextDouble() * Math.PI * 2;
        sweepSpin = (random.nextBoolean() ? 1 : -1) * (phase() == 3 ? 0.045 : 0.03);
        playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 1.6F);
        level().broadcastEntityEvent(this, EVENT_SWEEP);
    }

    void tickSweep(ServerLevel server) {
        if (sweepLeft <= 0) return;
        Vec3 o = new Vec3(getX(), floorY + 1.0, getZ());
        if (sweepWarn > 0) {
            sweepWarn--;
            if (sweepWarn % 4 == 0) for (int i = 0; i < 12; i++) {
                double a = sweepAngle + i * Math.PI / 6;
                Fx.line(server, MementoMoriEntity.glow(RED, 0.1F, 5), o, o.add(Math.cos(a) * 24, 0, Math.sin(a) * 24), 1.5);
            }
            return;
        }
        sweepLeft--;
        sweepAngle += sweepSpin;
        for (int i = 0; i < 12; i++) {
            double a = sweepAngle + i * Math.PI / 6;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            if (tickCount % 2 == 0) Fx.line(server, MementoMoriEntity.glow(i % 3 == 0 ? WHITE : GOLD, 0.2F, 3), o, o.add(dir.scale(24)), 0.8);
            if (tickCount % 10 != 0) continue;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(24, 2, 24), ChronoGazer::canHurt)) {
                Vec3 rel = e.position().subtract(o).multiply(1, 0, 1);
                double along = rel.dot(dir);
                if (along < 0 || along > 24) continue;
                double side = rel.subtract(dir.scale(along)).length();
                boolean low = e.getBoundingBox().minY < o.y + 0.3 && e.getBoundingBox().maxY > o.y - 0.3; // 腰の高さ（跳べば越える）
                if (side < 0.8 && low) e.hurt(damageSources().mobAttack(this), damageFor(e, SWEEP));
            }
        }
    }

    /** 時の楔: 相手のまわり 6 か所 */
    void spikes(LivingEntity t) {
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : 2.0 + random.nextDouble() * 3.5;
            strikes.add(new Strike(SPIKE_FALL, t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r), tickCount + 24 + i * 2, null));
        }
        playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0F, 0.6F);
    }

    /** 確定座標消去: 相手のいる座標に印（第 3 形態は 3 回続けて追う） */
    void erase(LivingEntity t, int delay) {
        int n = phase() == 3 ? 3 : 1;
        for (int i = 0; i < n; i++) strikes.add(new Strike(ERASE_FALL, i == 0 ? t.position() : Vec3.ZERO, tickCount + delay + i * 16, i == 0 ? null : t));
        playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.8F);
        say("erase", ChatFormatting.RED);
        level().broadcastEntityEvent(this, EVENT_ERASE);
    }

    /** 時針の一閃（0.75 秒構えて、前の半円 5 m） */
    void slash() {
        strikes.add(new Strike(SLASH_HIT, Vec3.ZERO, tickCount + 15, null));
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_SLASH);
    }

    /** 全世界時間停止の予告（2 秒の鐘）。止まるのは予告の後 */
    void announceStop() {
        strikes.add(new Strike(STOP_BEGIN, Vec3.ZERO, tickCount + STOP_WARN, null));
        castLock = STOP_WARN + 10;
        say("stop_warn", ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (int i = 0; i < 3; i++) playSound(SoundEvents.BELL_BLOCK, 4.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_STOP);
    }

    /** 時が止まった: 相手の背後へ跳び、止まっている相手のまわりに針の輪（と第 2 形態からは消去の印） */
    void stopTime(ServerLevel server) {
        TimeStop.start(server, this, STOP_TICKS);
        say("stop", ChatFormatting.GOLD, ChatFormatting.BOLD);
        LivingEntity t = getTarget();
        if (t != null) {
            Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(5.0)).add(0, 1.5, 0);
            blink(server, behind);
        }
        int ring = phase() == 3 ? 16 : 12;
        for (LivingEntity v : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(48.0), e -> ChronoGazer.canHurt(e) && TimeStop.frozen(e))) {
            Vec3 chest = v.position().add(0, v.getBbHeight() * 0.55, 0);
            for (int i = 0; i < ring; i++) {
                double a = Math.PI * 2 * i / ring;
                Vec3 from = chest.add(Math.cos(a) * 4.5, (i % 3 - 1) * 1.0, Math.sin(a) * 4.5);
                RulerNeedleEntity.place(this, from, chest, 20, STOP_NEEDLE * trialAttack, 1.0F); // 止まった時の中に並べる（動き出して 1 秒後に飛ぶ）
            }
            if (phase() >= 2) strikes.add(new Strike(ERASE_FALL, v.position(), tickCount + STOP_TICKS + 30, null));
        }
    }

    /** 時間跳躍: その場から消えて at に現れる */
    void blink(ServerLevel server, Vec3 at) {
        Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.4F, 14), position().add(0, 3.0, 0), 24, 0.25);
        moveTo(at.x, at.y, at.z, getYRot(), getXRot());
        Fx.burst(server, MementoMoriEntity.glow(INDIGO, 0.4F, 14), at.add(0, 3.0, 0), 24, 0.25);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.5F);
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
        say("finale", ChatFormatting.GOLD, ChatFormatting.BOLD);
        playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.4F);
        level().broadcastEntityEvent(this, EVENT_FINALE);
    }

    void tickFinale(ServerLevel server) {
        if (finaleLeft <= 0) return;
        int t = FINALE_TICKS - finaleLeft--;
        Vec3 o = new Vec3(getX(), floorY + 0.4, getZ());
        double hour = t * 0.02, minute = t * 0.06;
        for (double[] hand : new double[][]{{hour, 18}, {minute, 28}}) {
            Vec3 dir = new Vec3(Math.cos(hand[0]), 0, Math.sin(hand[0]));
            if (t % 2 == 0) Fx.line(server, MementoMoriEntity.glow(hand[1] > 20 ? GOLD : PALE, 0.3F, 3), o, o.add(dir.scale(hand[1])), 0.7);
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
            switch (s.kind()) {
                case SPIKE_FALL -> {
                    if (left > 0 && tickCount % 3 == 0) KeeperFx.ring(server, s.at().add(0, 0.15, 0), 2.0, GOLD, 16);
                    if (left <= 0) {
                        Fx.line(server, MementoMoriEntity.glow(GOLD, 0.45F, 12), s.at().add(0, 20, 0), s.at(), 0.6);
                        Fx.shockwave(server, s.at().add(0, 0.2, 0), GOLD, 3.0F, 12);
                        hitAround(s.at(), 2.0, SPIKE, false);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.LIGHTNING_BOLT_IMPACT, getSoundSource(), 1.2F, 1.6F);
                    }
                }
                case ERASE_FALL -> {
                    Vec3 at = s.at();
                    if (left > 0 && left <= 40 && tickCount % 2 == 0) cube(server, at);
                    if (left <= 0) {
                        Fx.line(server, MementoMoriEntity.glow(WHITE, 0.6F, 14), at.add(0, 24, 0), at.add(0, -2, 0), 0.4);
                        Fx.shockwave(server, at.add(0, 0.2, 0), WHITE, 3.0F, 12);
                        hitAround(at, 1.6, ERASE, true);
                        server.playSound(null, BlockPos.containing(at), SoundEvents.WARDEN_SONIC_BOOM, getSoundSource(), 1.5F, 1.6F);
                    }
                }
                case STOP_BEGIN -> {
                    if (left <= 0) stopTime(server);
                }
                case SLASH_HIT -> {
                    if (left <= 0) {
                        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
                        Fx.slash(server, position().add(0, 2.0, 0).add(fwd.scale(2.5)), GOLD, 5.0F, 10, 0.0F);
                        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.5), ChronoGazer::canHurt)) {
                            Vec3 rel = e.position().subtract(position()).multiply(1, 0, 1);
                            if (rel.length() < 5.5 && rel.normalize().dot(fwd) > 0.0) e.hurt(damageSources().mobAttack(this), damageFor(e, SLASH));
                        }
                    }
                }
                default -> {
                }
            }
            if (left <= 0) strikes.remove(s);
        }
    }

    /** 消去の印の光の立方体（一辺 1.6） */
    static void cube(ServerLevel server, Vec3 at) {
        double h = 0.8;
        Vec3 b = at.add(0, h, 0);
        for (int ax = -1; ax <= 1; ax += 2) for (int az = -1; az <= 1; az += 2) {
            Fx.line(server, MementoMoriEntity.glow(RED, 0.12F, 3), b.add(ax * h, -h, az * h), b.add(ax * h, h, az * h), 0.4);
        }
        for (int ay = -1; ay <= 1; ay += 2) {
            Vec3 y = b.add(0, ay * h, 0);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.12F, 3), y.add(-h, 0, -h), y.add(h, 0, -h), 0.4);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.12F, 3), y.add(-h, 0, h), y.add(h, 0, h), 0.4);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.12F, 3), y.add(-h, 0, -h), y.add(-h, 0, h), 0.4);
            Fx.line(server, MementoMoriEntity.glow(RED, 0.12F, 3), y.add(h, 0, -h), y.add(h, 0, h), 0.4);
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

    /** 撃破: 文字盤が割れ、歯車が落ち、砕けて金の光になる。時の狭間なら帰りの門が開く */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level().isClientSide) {
            if (deathTime % 2 == 0) level().addParticle(ParticleTypes.END_ROD, getRandomX(1.5), getY() + random.nextDouble() * 6.0, getRandomZ(1.5), 0, 0.05, 0);
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        if (deathTime == 1) {
            cleanup(server);
            say("defeat", ChatFormatting.GOLD, ChatFormatting.ITALIC);
        }
        setDeltaMovement(0, -0.02, 0);
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        if (deathTime % 10 == 0) playSound(SoundEvents.BELL_BLOCK, 3.0F, 1.6F - deathTime / 100.0F);
        if (deathTime % 4 == 0) Fx.burst(server, MementoMoriEntity.glow(GOLD, 0.35F, 16), position().add(random.nextGaussian(), 3.0 + random.nextGaussian(), random.nextGaussian()), 6, 0.15);
        if (deathTime == 40) {
            Fx.shockwave(server, position().add(0, 4.5, 0), GOLD, 8.0F, 20);
            playSound(SoundEvents.GLASS_BREAK, 4.0F, 0.5F);
        }
        if (deathTime >= DEFEAT_TICKS && !DirectRead.isRemoved(this)) {
            Fx.burst(server, MementoMoriEntity.glow(PALE, 0.8F, 30), position().add(0, 3.0, 0), 60, 0.4);
            Fx.shockwave(server, position().add(0, 1.0, 0), GOLD, 16.0F, 30);
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(3.0), getZ(), 1, 0, 0, 0, 0);
            playSound(SoundEvents.BELL_RESONATE, 5.0F, 1.2F);
            ensureLoot();
            if (TimeRift.isRift(server)) TimeRift.onRulerDefeated(server);
            protect.finishDeath();
            remove(RemovalReason.KILLED);
        }
    }

    /** 自分の技の後始末（時間停止を解き、歯車・針を消す） */
    void cleanup(ServerLevel server) {
        strikes.clear();
        sweepLeft = 0;
        finaleLeft = 0;
        rewindLeft = 0;
        if (TimeStop.active()) TimeStop.stop();
        for (RulerAnchorEntity a : anchors(server)) a.discard();
        for (RulerNeedleEntity n : server.getEntitiesOfClass(RulerNeedleEntity.class, getBoundingBox().inflate(64.0))) n.discard();
    }

    @Override
    public Component getDefeatSpeech() {
        return Component.translatable("entity.automata_valkyries.time_ruler.defeat").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
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

    /** 相手の斜め上 10〜16 m に浮かび、ときどき時間跳躍で回り込む。時間停止の時が来ればそれを、ほかは点を付けて技を選ぶ */
    static class RulerGoal extends Goal {
        private final TimeRulerEntity boss;
        private int needleCd = 40, sweepCd = 160, spikeCd = 90, eraseCd = 200, slashCd = 0, blinkCd = 120, repath;

        RulerGoal(TimeRulerEntity boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
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
            needleCd--;
            sweepCd--;
            spikeCd--;
            eraseCd--;
            slashCd--;
            blinkCd--;
            boss.stopCd--;
            double d = boss.distanceTo(t);
            if (--repath <= 0) { // 相手の斜め上に
                repath = 20;
                Vec3 away = boss.position().subtract(t.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
                Vec3 spot = t.position().add(away.normalize().scale(12.0)).add(0, 3.0, 0);
                boss.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
            }
            if (TimeStop.active()) return; // 止まった時の中では、並べた針と印のほかは手を出さない
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
            if (slashCd <= 0 && d < 5.0) {
                slashCd = boss.cd(60);
                boss.slash();
            } else if (boss.phase() >= 2 && eraseCd <= 0) {
                eraseCd = boss.cd(220);
                boss.erase(t, 40);
            } else if (sweepCd <= 0) {
                sweepCd = boss.cd(240);
                boss.sweep(t);
            } else if (spikeCd <= 0) {
                spikeCd = boss.cd(120);
                boss.spikes(t);
            } else if (needleCd <= 0 && boss.hasLineOfSight(t)) {
                needleCd = boss.cd(70);
                boss.needles(t);
            }
        }
    }

    /** 地面の輪（要塞守護機と同じ描き方） */
    static final class KeeperFx {
        static void ring(ServerLevel server, Vec3 at, double r, int color, int count) {
            BastionKeeperEntity.ring(server, at, r, color, count);
        }
    }
}
