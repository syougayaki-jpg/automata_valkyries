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
import net.minecraft.world.entity.EntitySelector;
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
 * 細身の白金の機械の騎士。HP {@value #MAX_HP}（「少し硬め」。最大 HP の上限は mixin/HealthCapMixin で 1,024 から上げてある）。即死系の守り（Eprotect）は付けるが、被弾の上限は無し（普通の攻撃で倒せる）。
 * 技（数値は【仮】。HP 50% で第 2 形態: 間隔が短くなり、光速三連閃が 5 連に）:
 * <ul>
 *   <li>斬撃: 光の剣（攻撃力 {@value #MELEE}）</li>
 *   <li>光速瞬動: 相手の背後・横へ一瞬で移り、斬る（残像の光の筋が残る）</li>
 *   <li>光速三連閃: 相手のまわりを瞬動しながら 3 連（第 2 形態 5 連）で斬る（1 回 {@value #FLURRY_HIT}）</li>
 *   <li>光刃の波: 離れた相手へ、まっすぐ飛ぶ光の刃（{@value #WAVE} ・貫通・射程 {@value #WAVE_RANGE}）</li>
 *   <li>光速突進: 1 秒の予告線のあと、20 ブロックを一直線に駆け抜ける（線上に {@value #DASH}）</li>
 * </ul>
 * 狙いは兵姫を優先（クロノゲイザー共通）。挑む相手がいなくなると 10 秒で退く（セリフつきの正規の退場）
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.automatavalkyries.AutomataValkyries.MODID)
public class LuxionEntity extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech {

    public static final float MAX_HP = 5000.0F, MELEE = 30.0F, FLURRY_HIT = 25.0F, WAVE = 30.0F, DASH = 40.0F;
    public static final double WAVE_RANGE = 24.0, DASH_LENGTH = 20.0;
    /** 動きの合図（描画: 斬る腕の振り・突進の構え） */
    public static final byte EVENT_SLASH = 70, EVENT_DASH_WINDUP = 71;
    private static final int RETREAT_TICKS = 200;
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

    private final Eprotect protect = new Eprotect(this).withoutDamageCap();
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.automata_valkyries.luxion"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private boolean engaged;
    private int noChallengerTicks;
    /** クライアント: 最後に斬った・突進を構えた Tick（描画用） */
    public int slashTick = -100, windupTick = -100;
    /** クライアント: 背の戦闘機型ブースターの展開（0 = 背に収まる、1 = 後ろへ開いて翼を広げる）。戦闘中（相手を捉えている＝aggressive）に開く */
    public float deploy, deployO;
    /** クライアント: 第 2 形態の背の機構の豪華さ（0 → 1 へ 1.5 秒で）。光の翼・光輪・大きくなる翼とブースター（2026-10-08 ユーザー指定） */
    public float glory, gloryO;
    /** クライアント: 残光（瞬動・突進・速く動いた跡に残る半透明の自分の姿。2026-10-08 ユーザー指定「残光を自作して」） */
    public final java.util.ArrayList<Afterimage> afterimages = new java.util.ArrayList<>();
    public static final int AFTERIMAGE_LIFE = 14;
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
        setMaxUpStep(1.5F);
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
    }

    public boolean isPhase2() {
        return entityData.get(PHASE2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new LuxionAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this);
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
        if (!protect.canDie()) { // 被弾を通らない die() の直接呼び出しは即死攻撃として断る
            boolean malicious = protect.noteRefusedDeath();
            float current = DirectRead.health(this);
            setHealth(!malicious && Float.isFinite(current) && current > 0.0F ? current : getMaxHealth());
            deathTime = 0;
            com.automatavalkyries.protect.KillModCounters.counterAll(this);
            return;
        }
        protect.markDefeated();
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
        ++deathTime;
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++) {
                level().addParticle(ParticleTypes.END_ROD, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0.08, 0);
            }
        } else if (deathTime >= 40 && !DirectRead.isRemoved(this)) {
            if (level() instanceof ServerLevel server) {
                Vec3 c = position().add(0, 1.2, 0);
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

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            protect.clientTick();
            deployO = deploy;
            deploy = Mth.clamp(deploy + (isAggressive() ? 0.06F : -0.04F), 0.0F, 1.0F);
            trackAfterimages();
            gloryO = glory;
            glory = Mth.clamp(glory + (isPhase2() ? 1.0F / 30.0F : -0.05F), 0.0F, 1.0F);
            if (glory > 0.5F && tickCount % 2 == 0) { // 光の翼から舞う光の粒
                Vec3 side = Vec3.directionFromRotation(0, yBodyRot + 90.0F);
                double s = (random.nextBoolean() ? 1 : -1) * (0.8 + random.nextDouble() * 2.2);
                Vec3 p = position().add(side.scale(s)).add(Vec3.directionFromRotation(0, yBodyRot).scale(-0.6)).add(0, 2.2 + random.nextDouble() * 1.8, 0);
                level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, -0.02, 0);
            }
            if (tickCount % 2 == 0) { // 背の光の尾のきらめき
                Vec3 back = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(-0.4)).add(0, 2.0, 0);
                level().addParticle(ParticleTypes.END_ROD, back.x, back.y, back.z, 0, -0.01, 0);
            }
            return;
        }
        protect.tick();
        float ratio = Mth.clamp(DirectRead.health(this) / getMaxHealth(), 0.0F, 1.0F);
        bossEvent.setProgress(ratio);
        if (!isPhase2() && ratio <= 0.5F && isAlive()) {
            entityData.set(PHASE2, true);
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 1.6F);
            if (level() instanceof ServerLevel server) {
                Vec3 c = position().add(0, 1.4, 0);
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

    private void tickRetreat() {
        if (getTarget() instanceof Player) engaged = true;
        if (!engaged || tickCount % 20 != 0) return;
        boolean challenger = level().getNearestPlayer(getX(), getY(), getZ(), 64.0,
                EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE)) != null;
        if (challenger) {
            noChallengerTicks = 0;
            return;
        }
        noChallengerTicks += 20;
        if (noChallengerTicks >= RETREAT_TICKS) protect.triggerDespawn();
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
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SLASH) slashTick = tickCount;
        else if (id == EVENT_DASH_WINDUP) windupTick = tickCount;
        else super.handleEntityEvent(id);
    }

    // ---- 技 ----

    /** 光の剣で斬る（前方の扇・2.8 ブロック） */
    void slash(float damage) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.0, 1.0, 3.0), ChronoGazer::canHurt)) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (to.length() > 3.3 + e.getBbWidth() * 0.5 || (to.lengthSqr() > 0.25 && to.normalize().dot(look) < 0.3)) continue;
            e.hurt(damageSources().mobAttack(this), damage);
            e.knockback(0.4, -look.x, -look.z);
        }
        // 光の弧（刀の軌跡）と、白い芯・金の縁・赤い火花
        Vec3 arcAt = position().add(0, 1.3, 0).add(look.scale(1.6));
        com.automatavalkyries.weapon.Fx.slash(server, arcAt, C_WG, 2.6F, 8, (float) (random.nextGaussian() * 0.5));
        com.automatavalkyries.weapon.Fx.slash(server, arcAt, C_WHITE, 1.8F, 6, 0.0F);
        for (int i = -4; i <= 4; i++) {
            Vec3 dir = Vec3.directionFromRotation(0, getYRot() + i * 13);
            Vec3 p = position().add(0, 1.35, 0).add(dir.scale(2.3));
            com.automatavalkyries.weapon.Fx.one(server, glow(i == 0 ? C_WHITE : C_WG, 0.5F, 10), p, dir.scale(0.05));
            if (i % 2 == 0) com.automatavalkyries.weapon.Fx.one(server, star(i == 0 ? C_RED : C_GOLD, 0.35F, 14), p, dir.scale(0.12).add(0, 0.05, 0));
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
        level().broadcastEntityEvent(this, EVENT_SLASH);
    }

    /** 光速瞬動: to へ一瞬で移り、通った道に光の筋（残像）を残す */
    void blink(Vec3 to) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = position().add(0, 1.2, 0);
        Vec3 dest = to.add(0, 1.2, 0);
        double len = from.distanceTo(dest);
        // 光の筋（白い芯と金のにじみ）、通り道の星、出た所と着いた所の光の輪
        com.automatavalkyries.weapon.Fx.line(server, glow(C_WHITE, 0.45F, 12), from, dest, 0.35);
        com.automatavalkyries.weapon.Fx.line(server, glow(C_WG, 0.9F, 16), from, dest, 0.6);
        com.automatavalkyries.weapon.Fx.line(server, star(C_GOLD, 0.3F, 20), from, dest, 1.1);
        com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.45F, 18), from, 14, 0.18);
        com.automatavalkyries.weapon.Fx.shockwave(server, to.add(0, 0.1, 0), C_WG, 2.2F, 10);
        com.automatavalkyries.weapon.Fx.burst(server, glow(C_WHITE, 0.6F, 10), dest, 10, 0.12);
        teleportTo(to.x, to.y, to.z);
        resetFallDistance();
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
        Vec3 from = position().add(0, 1.3, 0);
        Vec3 dir = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        for (double d = 1.0; d <= WAVE_RANGE; d += 0.6) {
            Vec3 p = from.add(dir.scale(d));
            if (!level().getBlockState(net.minecraft.core.BlockPos.containing(p)).getCollisionShape(level(), net.minecraft.core.BlockPos.containing(p)).isEmpty()) break;
            for (int k = -2; k <= 2; k++) {
                Vec3 q = p.add(side.scale(k * 0.4)).add(0, -Math.abs(k) * 0.15, 0);
                com.automatavalkyries.weapon.Fx.one(server, glow(k == 0 ? C_WHITE : C_WG, k == 0 ? 0.6F : 0.4F, 10), q, dir.scale(0.08));
            }
            if (((int) (d / 0.6)) % 3 == 0) com.automatavalkyries.weapon.Fx.one(server, star(C_GOLD, 0.35F, 16), p, Vec3.ZERO);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.2), ChronoGazer::canHurt)) {
                if (hit.add(e)) e.hurt(damageSources().mobAttack(this), WAVE);
            }
        }
        playSound(SoundEvents.TRIDENT_RIPTIDE_1, 1.0F, 1.6F);
        level().broadcastEntityEvent(this, EVENT_SLASH);
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
                    e.hurt(damageSources().mobAttack(this), DASH);
                    e.knockback(1.0, -dir.x, -dir.z);
                }
            }
        }
        blink(end);
        if (level() instanceof ServerLevel server) { // 駆け抜けた後の光の衝撃波
            com.automatavalkyries.weapon.Fx.shockwave(server, end.add(0, 0.1, 0), C_WHITE, 4.0F, 14);
            com.automatavalkyries.weapon.Fx.shockwave(server, end.add(0, 0.2, 0), C_GOLD, 6.0F, 18);
            com.automatavalkyries.weapon.Fx.burst(server, star(C_WG, 0.5F, 22), end.add(0, 1.0, 0), 24, 0.25);
        }
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.8F);
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
            return canUse() || windup > 0 || flurryLeft > 0;
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
            lux.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private int cd(int ticks) {
            return lux.isPhase2() ? ticks * 3 / 5 : ticks;
        }

        @Override
        public void tick() {
            LivingEntity t = lux.getTarget();
            melee--; blink--; flurry--; wave--; dash--;
            // 突進の構え（動かずに予告線を出し、0 になったら駆け抜ける）
            if (windup > 0) {
                lux.getNavigation().stop();
                if (windup % 4 == 0) lux.dashTelegraph(dashDir);
                if (--windup == 0) lux.dash(dashDir);
                return;
            }
            // 三連閃の続き
            if (flurryLeft > 0) {
                if (t == null || !t.isAlive()) {
                    flurryLeft = 0;
                    return;
                }
                if (--flurryGap <= 0) {
                    flurryLeft--;
                    flurryGap = lux.isPhase2() ? 6 : 8;
                    lux.blink(lux.spotAround(t, lux.random.nextDouble() * Math.PI * 2, 1.8));
                    lux.getLookControl().setLookAt(t, 360.0F, 90.0F);
                    lux.setYRot((float) (Mth.atan2(t.getZ() - lux.getZ(), t.getX() - lux.getX()) * Mth.RAD_TO_DEG) - 90.0F);
                    lux.slash(FLURRY_HIT);
                }
                return;
            }
            if (t == null) return;
            lux.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double dist = lux.distanceTo(t);
            boolean sees = lux.getSensing().hasLineOfSight(t);
            if (flurry <= 0 && dist < 16 && sees) { // 光速三連閃（第 2 形態は 5 連）
                flurry = cd(200);
                flurryLeft = lux.isPhase2() ? 5 : 3;
                flurryGap = 0;
                return;
            }
            if (dash <= 0 && dist > 6 && dist < DASH_LENGTH && sees) { // 光速突進（構え 1 秒、第 2 形態 0.6 秒）
                dash = cd(240);
                windup = lux.isPhase2() ? 12 : 20;
                dashDir = t.position().subtract(lux.position()).multiply(1, 0, 1).normalize();
                lux.level().broadcastEntityEvent(lux, EVENT_DASH_WINDUP);
                lux.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.8F);
                return;
            }
            if (wave <= 0 && dist > 8 && sees) { // 光刃の波
                wave = cd(70);
                lux.wave(t);
                return;
            }
            if (blink <= 0 && dist > 4 && dist < 24) { // 光速瞬動で背後へ
                blink = cd(80);
                double behind = Mth.atan2(t.getZ() - lux.getZ(), t.getX() - lux.getX());
                lux.blink(lux.spotAround(t, behind, 1.8));
                melee = 4;
                return;
            }
            if (dist <= 3.2) {
                lux.getNavigation().stop();
                if (melee <= 0) {
                    melee = cd(22);
                    lux.slash((float) lux.getAttributeValue(Attributes.ATTACK_DAMAGE));
                }
            } else if (--repath <= 0) {
                repath = 8;
                lux.getNavigation().moveTo(t, 1.2);
            }
        }
    }
}
