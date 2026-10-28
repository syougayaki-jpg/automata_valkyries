package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ルクシオン EX（2026-10-08 ユーザー指定「裏ボスの後のルミナルステラの追憶メモリで戦う強化版」。2026-10-27 に作った）。
 * 回想のトライアル「極光の残響」のボス。HP {@value #MAX_HP_EX}、技のダメージ ×1.5、装甲・速さを少し上げた。3 形態（66% で第 2、33% で第 3）。
 * 見た目は色違い（黒と紅金。{@code client/LuxionExRenderer}）。
 * <p>
 * 第 3 形態の技（2026-10-09 ユーザー指定の 8 つ。数値は【仮】）は {@link ExGoal} が、いつもの技の組み立ての手が空いた時に割り込んで出す:
 * <ul>
 *   <li>天穿つ居合: 鞘に納めて 0.8 秒溜め、前方の広い扇（{@value #IAI_ARC} 度・{@value #IAI_RANGE} m）へ居合。溜めの間に {@value #IAI_INTERRUPT} 以上の一撃を受けると崩れてのけぞる</li>
 *   <li>地を這う光刃: 刀を地に突き立て、3 方向へ走る光の亀裂から、追って光の刃が噴き出す</li>
 *   <li>逆噴射の後退斬り: 近くで押され続けると、ブースターで後ろへ跳び、着地と同時に光刃の波</li>
 *   <li>光の檻: 相手を光の柱（壊せる金の色ガラス）の輪で閉じ込め、2 秒後に内側へ斬撃が集まる。柱を壊して抜けられる</li>
 *   <li>残像の分け身: 見た目だけの分け身 2 体と、3 方向から光刃の波</li>
 *   <li>連鎖瞬動: 近くの相手を順に瞬動で斬り継ぐ（相手が少なければ同じ相手のまわりを）</li>
 *   <li>刻の加速: HP {@value #HASTE_AT_PERCENT}% 以下で 1 度だけ、{@value #HASTE_TICKS} Tick の間、技の間隔が半分・足が速く・残光を常に引く</li>
 *   <li>落光の連撃: アクセラレータの着弾の後に、もう一度跳んで踏み抜く</li>
 * </ul>
 * 第 3 形態の大技は分身の 3 連撃のルクシオンアクセラレータ（2026-10-27 ユーザー指定「分身は当たり無し」）: 3 方向の上空に本体と影 2 体が並び、
 * それぞれ予測線を出して、{@value #CLONE_KICK_GAP} Tick ずつずらして影・影・本体の順に蹴り穿つ。影は攻撃を受けない（見た目だけ。当たりは本体が出す）。
 * </p>
 */
public class LuxionExEntity extends LuxionEntity {

    public static final float MAX_HP_EX = 30000.0F, IAI = 50.0F, CRACK = 28.0F, CAGE = 45.0F, STOMP = 45.0F;
    public static final float PHASE_TWO_AT = 0.66F, PHASE_THREE_AT = 0.33F, HASTE_AT = 0.30F;
    static final int HASTE_AT_PERCENT = 30, HASTE_TICKS = 160, CLONE_KICK_GAP = 12, IAI_ARC = 140;
    static final double IAI_RANGE = 18.0, CRACK_LENGTH = 16.0, CAGE_RADIUS = 4.5, STOMP_RADIUS = 4.5;
    /** 天穿つ居合の溜めを崩す一撃 */
    static final float IAI_INTERRUPT = 300.0F;
    private static final java.util.UUID HASTE_SPEED_ID = java.util.UUID.fromString("9c3e7b21-4d5a-4f86-b1c9-7e2d0a5f8b64");
    private static final int C_CRIMSON = 0xFF2A4A, C_GOLD = 0xE8C060, C_WHITE = 0xFFFFFF, C_VIOLET = 0xB060FF;

    private static final EntityDataAccessor<Boolean> PHASE3 = SynchedEntityData.defineId(LuxionExEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HASTE = SynchedEntityData.defineId(LuxionExEntity.class, EntityDataSerializers.BOOLEAN);
    /** 分身（見た目だけ）の数と位置・向き */
    private static final EntityDataAccessor<Byte> SHADOW_COUNT = SynchedEntityData.defineId(LuxionExEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> SHADOW_A = SynchedEntityData.defineId(LuxionExEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> SHADOW_B = SynchedEntityData.defineId(LuxionExEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> SHADOW_A_YAW = SynchedEntityData.defineId(LuxionExEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SHADOW_B_YAW = SynchedEntityData.defineId(LuxionExEntity.class, EntityDataSerializers.FLOAT);

    /** サーバー: 刻の加速を使ったか・残り Tick、天穿つ居合の溜めの最中か、崩れてのけぞる残り Tick、近くで押され続けている Tick、落光の連撃の予約 */
    private boolean hasteUsed, iaiCharging, stompPending;
    private int hasteTicks, stunTicks, pressed;
    /** 光の檻で置いた柱のブロック（技の終わり・倒れた時に消す） */
    private final List<BlockPos> cageBlocks = new ArrayList<>();
    /** 第 3 形態の技（間隔は使っていない間も毎 Tick 減らす） */
    private ExGoal exGoal;

    public LuxionExEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 800;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP_EX)
                .add(Attributes.ARMOR, 18.0)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.38)
                .add(Attributes.ATTACK_DAMAGE, MELEE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE3, false);
        entityData.define(HASTE, false);
        entityData.define(SHADOW_COUNT, (byte) 0);
        entityData.define(SHADOW_A, new Vector3f());
        entityData.define(SHADOW_B, new Vector3f());
        entityData.define(SHADOW_A_YAW, 0.0F);
        entityData.define(SHADOW_B_YAW, 0.0F);
    }

    public boolean isPhase3() {
        return entityData.get(PHASE3);
    }

    public boolean isHasted() {
        return entityData.get(HASTE);
    }

    @Override
    protected float exScale() {
        return 1.5F;
    }

    @Override
    protected float phaseTwoAt() {
        return PHASE_TWO_AT;
    }

    @Override
    protected float cooldownScale() {
        float scale = super.cooldownScale() * (isPhase3() ? 0.8F : 1.0F);
        return isHasted() ? scale * 0.5F : scale;
    }

    @Override
    protected boolean usesBaseAccelerator() {
        return !isPhase3();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        exGoal = new ExGoal(this);
        goalSelector.addGoal(1, exGoal);
    }

    // ---- 分身（見た目だけ） ----

    @Override
    public List<Afterimage> shadows() {
        int n = entityData.get(SHADOW_COUNT);
        if (n <= 0) return List.of();
        List<Afterimage> list = new ArrayList<>(n);
        Vector3f a = entityData.get(SHADOW_A);
        list.add(new Afterimage(a.x, a.y, a.z, entityData.get(SHADOW_A_YAW), tickCount));
        if (n > 1) {
            Vector3f b = entityData.get(SHADOW_B);
            list.add(new Afterimage(b.x, b.y, b.z, entityData.get(SHADOW_B_YAW), tickCount));
        }
        return list;
    }

    void setShadow(int i, Vec3 at, float yaw) {
        entityData.set(i == 0 ? SHADOW_A : SHADOW_B, new Vector3f((float) at.x, (float) at.y, (float) at.z));
        entityData.set(i == 0 ? SHADOW_A_YAW : SHADOW_B_YAW, yaw);
        if (entityData.get(SHADOW_COUNT) < i + 1) entityData.set(SHADOW_COUNT, (byte) (i + 1));
    }

    Vec3 shadowPos(int i) {
        Vector3f v = entityData.get(i == 0 ? SHADOW_A : SHADOW_B);
        return new Vec3(v.x, v.y, v.z);
    }

    void clearShadows() {
        if (entityData.get(SHADOW_COUNT) != 0) entityData.set(SHADOW_COUNT, (byte) 0);
    }

    /** 分身が現れる・消える光 */
    void shadowFlash(Vec3 at) {
        if (!(level() instanceof ServerLevel server)) return;
        Fx.burst(server, glowFx(C_CRIMSON, 0.7F, 18), at.add(0, 1.2 * SIZE, 0), 20, 0.25);
        Fx.burst(server, starFx(C_GOLD, 0.4F, 22), at.add(0, 1.2 * SIZE, 0), 12, 0.2);
    }

    static com.automatavalkyries.particle.FxOptions glowFx(int color, float size, int life) {
        return Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), color, size, life);
    }

    static com.automatavalkyries.particle.FxOptions starFx(int color, float size, int life) {
        return Fx.opt(com.automatavalkyries.registry.ModParticles.STAR.get(), color, size, life);
    }

    // ---- 毎 Tick: 第 3 形態・刻の加速・押され具合 ----

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            if (isHasted() && tickCount % 2 == 0 && !isDefeatedSynced()) afterimages.add(new Afterimage(getX(), getY(), getZ(), yBodyRot, tickCount)); // 残光を常に引く
            return;
        }
        if (!isAlive() || isDefeatedSynced()) {
            clearCage();
            clearShadows();
            return;
        }
        float ratio = getEprotect().healthRatio();
        if (isPhase2() && !isPhase3() && ratio <= PHASE_THREE_AT && stage() == STAGE_NONE) enterPhase3();
        if (isPhase3() && !hasteUsed && ratio <= HASTE_AT && stage() == STAGE_NONE) startHaste();
        if (hasteTicks > 0 && --hasteTicks == 0) endHaste();
        if (stunTicks > 0) stunTicks--;
        if (exGoal != null) exGoal.countDown();
        LivingEntity t = getTarget();
        pressed = t != null && t.isAlive() && distanceTo(t) < 4.0 ? pressed + 1 : Math.max(0, pressed - 2);
    }

    private void enterPhase3() {
        entityData.set(PHASE3, true);
        startStage(STAGE_TRANSFORM); // 剣を天へ掲げて浮かぶ（受けない）
        bossEvent().setColor(BossEvent.BossBarColor.PURPLE);
        playSound(SoundEvents.WITHER_SPAWN, 1.5F, 1.6F);
        if (level() instanceof ServerLevel server) {
            Vec3 c = position().add(0, 1.4 * SIZE, 0);
            Fx.mandala(server, position().add(0, 0.1, 0), C_CRIMSON, 7.0F, 50, false, false);
            Fx.mandala(server, position().add(0, 0.2, 0), C_GOLD, 4.5F, 50, false, true);
            Fx.shockwave(server, position().add(0, 0.1, 0), C_VIOLET, 12.0F, 26);
            Fx.burst(server, starFx(C_GOLD, 0.8F, 40), c, 80, 0.45);
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.6), getZ(), 2, 0.3, 0.3, 0.3, 0);
            message(server, "message.automata_valkyries.luxion_ex.phase3", ChatFormatting.DARK_PURPLE);
        }
    }

    private void startHaste() {
        hasteUsed = true;
        hasteTicks = HASTE_TICKS;
        entityData.set(HASTE, true);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(HASTE_SPEED_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(HASTE_SPEED_ID, com.automatavalkyries.AutomataValkyries.MODID + ":luxion_ex_haste", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        playSound(SoundEvents.BELL_RESONATE, 2.0F, 1.6F);
        if (level() instanceof ServerLevel server) {
            Fx.mandala(server, position().add(0, 0.1, 0), C_GOLD, 6.0F, 40, false, false);
            Fx.shockwave(server, position().add(0, 0.2, 0), C_WHITE, 9.0F, 20);
            message(server, "message.automata_valkyries.luxion_ex.haste", ChatFormatting.GOLD);
        }
    }

    private void endHaste() {
        entityData.set(HASTE, false);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(HASTE_SPEED_ID);
    }

    void message(ServerLevel server, String key, ChatFormatting color) {
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 64 * 64)) {
            p.displayClientMessage(Component.translatable(key).withStyle(color, ChatFormatting.BOLD), true);
        }
    }

    /** 天穿つ居合の溜めの間に大きな一撃を受けると、溜めが崩れてのけぞる */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && iaiCharging && amount >= IAI_INTERRUPT) {
            iaiCharging = false;
            stunTicks = 30;
            level().broadcastEntityEvent(this, EVENT_BIG_HURT);
            playSound(SoundEvents.SHIELD_BREAK, 2.0F, 0.8F);
            if (level() instanceof ServerLevel server) {
                Fx.burst(server, starFx(C_GOLD, 0.6F, 20), position().add(0, 1.2 * SIZE, 0), 30, 0.35);
                message(server, "message.automata_valkyries.luxion_ex.iai_broken", ChatFormatting.YELLOW);
            }
        }
        return hurt;
    }

    /** 着弾: 第 3 形態では、続けて落光の連撃（もう一度跳んで踏み抜く）を予約する */
    @Override
    void accelImpact(Vec3 at, Vec3 start, Set<LivingEntity> hit) {
        super.accelImpact(at, start, hit);
        if (isPhase3()) stompPending = true;
    }

    @Override
    public void remove(RemovalReason reason) {
        clearCage();
        super.remove(reason);
    }

    // ---- 光の檻の柱 ----

    void placeCage(Vec3 center) {
        clearCage();
        BlockState glass = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        int base = Mth.floor(center.y);
        for (int i = 0; i < 18; i++) {
            double a = Math.PI * 2 * i / 18;
            int x = Mth.floor(center.x + Math.cos(a) * CAGE_RADIUS), z = Mth.floor(center.z + Math.sin(a) * CAGE_RADIUS);
            for (int y = 0; y < 5; y++) {
                BlockPos p = new BlockPos(x, base + y, z);
                if (!level().getBlockState(p).isAir() || cageBlocks.contains(p)) continue;
                level().setBlock(p, glass, 3);
                cageBlocks.add(p);
            }
        }
    }

    void clearCage() {
        if (cageBlocks.isEmpty() || level().isClientSide) return;
        for (BlockPos p : cageBlocks) if (level().getBlockState(p).is(Blocks.YELLOW_STAINED_GLASS)) level().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        cageBlocks.clear();
    }

    // ---- 技の部品 ----

    /** 光刃の波を、自分以外の所（分身）から撃つ */
    void waveFrom(Vec3 from, LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 dir = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Set<LivingEntity> hit = new HashSet<>();
        Vec3 reach = from.add(dir.scale(WAVE_RANGE));
        for (double d = 1.0; d <= WAVE_RANGE; d += 0.6) {
            Vec3 p = from.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(p);
            if (!level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty()) {
                reach = p;
                break;
            }
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.2), ChronoGazer::canHurt)) {
                if (hit.add(e)) e.hurt(damageSources().mobAttack(this), damageFor(e, WAVE));
            }
        }
        LuxionFxEntity.spawn(server, LuxionFxEntity.WAVE, from.add(dir.scale(0.8)), reach, true);
    }

    /** 分身の蹴りの 1 Tick: from から to へ進み、道の相手を蹴り穿つ（1 体 1 回）。着いたら true */
    boolean cloneKickStep(int i, Vec3 from, Vec3 to, Set<LivingEntity> hit) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 next = len <= ACCEL_SPEED ? to : from.add(d.scale(ACCEL_SPEED / len));
        for (double s = 0.0; s <= Math.min(len, ACCEL_SPEED); s += 0.6) {
            Vec3 p = len < 1.0e-4 ? to : from.add(d.scale(s / len));
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.4, 2.0, 1.4), ChronoGazer::canHurt)) {
                if (hit.add(e)) {
                    e.hurt(accelDamage(), damageFor(e, ACCEL_KICK));
                    Vec3 dir = len < 1.0e-4 ? Vec3.ZERO : d.scale(1.0 / len);
                    e.knockback(2.5, -dir.x, -dir.z);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
                }
            }
        }
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        setShadow(i, next, yaw);
        if (level() instanceof ServerLevel server) Fx.burst(server, glowFx(C_CRIMSON, 0.6F, 14), next.add(0, 0.6 * SIZE, 0), 6, 0.2);
        return len <= ACCEL_SPEED;
    }

    /** 分身の着弾: 光の爆風（蹴りが当たっていない近くの相手へ）と衝撃波 */
    void cloneImpact(Vec3 at, Set<LivingEntity> hit) {
        if (!(level() instanceof ServerLevel server)) return;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(ACCEL_BLAST_RADIUS), ChronoGazer::canHurt)) {
            if (hit.contains(e) || e.distanceToSqr(at) > ACCEL_BLAST_RADIUS * ACCEL_BLAST_RADIUS) continue;
            e.hurt(accelDamage(), damageFor(e, ACCEL_BLAST));
        }
        Vec3 ground = at.add(0, 0.1, 0);
        Fx.shockwave(server, ground, C_CRIMSON, 7.0F, 16);
        Fx.shockwave(server, ground, C_GOLD, 10.0F, 22);
        Fx.mandala(server, ground, C_CRIMSON, 5.0F, 30, false, true);
        Fx.burst(server, starFx(C_GOLD, 0.8F, 30), at.add(0, 1.0, 0), 50, 0.5);
        Fx.line(server, glowFx(C_CRIMSON, 1.0F, 20), at, at.add(0, 14, 0), 0.6);
        server.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
        playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 1.4F);
    }

    /** まわり radius の相手へ（防御貫通） */
    void blastAround(Vec3 at, double radius, float damage) {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 3.0, radius), ChronoGazer::canHurt)) {
            if (e.position().subtract(at).multiply(1, 0, 1).length() > radius + e.getBbWidth() * 0.5) continue;
            e.hurt(accelDamage(), damageFor(e, damage));
            Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0e-4) e.knockback(1.2, -away.x, -away.z);
        }
    }

    /**
     * 第 3 形態の技（{@link LuxionAttackGoal} より先の優先度。いつもの技の途中には割り込まない）。技は最後まで出し切り、終わると {@link LuxionAttackGoal} に戻る。
     * 技どうしの間は少なくとも {@link #GAP} Tick 空ける（いつもの技も混ざるように）
     */
    static final class ExGoal extends Goal {
        enum Move { IAI, CRACKS, BACKSTEP, CAGE, SPLIT, CHAIN, CLONE_ACCEL, STOMP, STUN }

        static final int GAP = 30;
        private final LuxionExEntity ex;
        private final int[] cooldown = new int[Move.values().length];
        private int gap = 20;
        private Move move;
        private int t;
        private LivingEntity target;
        // 技ごとの状態
        private Vec3 dir, center, groundSpot;
        private final List<Vec3> lanes = new ArrayList<>();
        private final List<Set<LivingEntity>> laneHits = new ArrayList<>();
        private final List<LivingEntity> chain = new ArrayList<>();
        /** 3 連撃のアクセラレータ: 3 つの頂点（影 A・影 B・本体）、狙う所、蹴った相手、蹴りの始まり、終わったか */
        private final Vec3[] apex = new Vec3[3], aim = new Vec3[3];
        private final List<Set<LivingEntity>> kickHits = new ArrayList<>();
        private final boolean[] kicked = new boolean[3], landed = new boolean[3];
        private Vec3 bodyFrom;

        ExGoal(LuxionExEntity ex) {
            this.ex = ex;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            cooldown[Move.CLONE_ACCEL.ordinal()] = 100;
        }

        private int cd(int ticks) {
            return Math.max(1, Math.round(ticks * (ex.isHasted() ? 0.5F : 1.0F)));
        }

        /** 毎 Tick（使っていない間も）間隔を減らす */
        void countDown() {
            for (int i = 0; i < cooldown.length; i++) if (cooldown[i] > 0) cooldown[i]--;
            if (gap > 0 && move == null) gap--;
        }

        /** 出せる技を選ぶ */
        private Move pick() {
            if (ex.stunTicks > 0) return Move.STUN;
            if (ex.stompPending) return Move.STOMP;
            if (!ex.isPhase3() || ex.stage() != STAGE_NONE || gap > 0) return null;
            if (ex.attackGoal != null && ex.attackGoal.busy()) return null;
            LivingEntity tgt = ex.getTarget();
            if (tgt == null || !tgt.isAlive()) return null;
            double dist = ex.distanceTo(tgt);
            boolean sees = ex.getSensing().hasLineOfSight(tgt);
            if (ready(Move.CLONE_ACCEL) && sees && dist < 30 && ex.onGround()) return Move.CLONE_ACCEL;
            if (ready(Move.BACKSTEP) && ex.pressed >= 25) return Move.BACKSTEP;
            List<Move> options = new ArrayList<>();
            if (ready(Move.IAI) && sees && dist < IAI_RANGE) options.add(Move.IAI);
            if (ready(Move.CRACKS) && dist < CRACK_LENGTH && ex.onGround()) options.add(Move.CRACKS);
            if (ready(Move.CAGE) && dist < 20 && tgt.onGround()) options.add(Move.CAGE);
            if (ready(Move.SPLIT) && sees && dist > 4 && dist < WAVE_RANGE) options.add(Move.SPLIT);
            if (ready(Move.CHAIN) && dist < 16) options.add(Move.CHAIN);
            return options.isEmpty() ? null : options.get(ex.random.nextInt(options.size()));
        }

        private boolean ready(Move m) {
            return cooldown[m.ordinal()] <= 0;
        }

        @Override
        public boolean canUse() {
            Move m = pick();
            if (m == null) return false;
            move = m;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return move != null && ex.isAlive() && !ex.isDefeatedSynced() && ex.stage() == STAGE_NONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            target = ex.getTarget();
            ex.getNavigation().stop();
            ex.setAggressive(true);
            switch (move) {
                case IAI -> cooldown[Move.IAI.ordinal()] = cd(160);
                case CRACKS -> cooldown[Move.CRACKS.ordinal()] = cd(140);
                case BACKSTEP -> cooldown[Move.BACKSTEP.ordinal()] = cd(100);
                case CAGE -> cooldown[Move.CAGE.ordinal()] = cd(300);
                case SPLIT -> cooldown[Move.SPLIT.ordinal()] = cd(220);
                case CHAIN -> cooldown[Move.CHAIN.ordinal()] = cd(200);
                case CLONE_ACCEL -> cooldown[Move.CLONE_ACCEL.ordinal()] = cd(420);
                case STOMP -> ex.stompPending = false;
                default -> {}
            }
        }

        @Override
        public void stop() {
            if (move == Move.IAI) ex.iaiCharging = false;
            ex.clearCage();
            ex.clearShadows();
            ex.setAccelPitch(0.0F);
            move = null;
            gap = GAP;
        }

        private void finish() {
            move = null;
        }

        private void face(LivingEntity e) {
            ex.getLookControl().setLookAt(e, 360.0F, 90.0F);
            float yaw = (float) (Mth.atan2(e.getZ() - ex.getZ(), e.getX() - ex.getX()) * Mth.RAD_TO_DEG) - 90.0F;
            ex.setYRot(yaw);
            ex.setYBodyRot(yaw);
        }

        private static float yawTo(Vec3 from, Vec3 to) {
            return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
        }

        @Override
        public void tick() {
            if (move == null) return;
            ServerLevel server = (ServerLevel) ex.level();
            boolean targetOk = target != null && target.isAlive();
            if (move != Move.STUN && ex.stunTicks > 0) { // 溜めを崩された
                ex.clearShadows();
                ex.clearCage();
                move = Move.STUN;
                t = 0;
            }
            if (!targetOk && move != Move.STUN && move != Move.CLONE_ACCEL && move != Move.STOMP) {
                finish();
                return;
            }
            switch (move) {
                case STUN -> {
                    ex.getNavigation().stop();
                    ex.setDeltaMovement(new Vec3(0, ex.getDeltaMovement().y, 0));
                    if (ex.stunTicks <= 0) finish();
                }
                case IAI -> tickIai(server);
                case CRACKS -> tickCracks(server);
                case BACKSTEP -> tickBackstep(server);
                case CAGE -> tickCage(server);
                case SPLIT -> tickSplit(server);
                case CHAIN -> tickChain(server);
                case CLONE_ACCEL -> tickCloneAccel(server);
                case STOMP -> tickStomp(server);
            }
            t++;
        }

        /** 天穿つ居合: 鞘に納めて溜め（0.8 秒。狙いは 0.3 秒前に固定）、前方の広い扇へ */
        private void tickIai(ServerLevel server) {
            ex.getNavigation().stop();
            if (t == 0) {
                ex.iaiCharging = true;
                ex.level().broadcastEntityEvent(ex, EVENT_WAVE_CHARGE);
                ex.playSound(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.5F, 0.7F);
                ex.message(server, "message.automata_valkyries.luxion_ex.iai", ChatFormatting.GOLD);
            }
            if (t <= 10) {
                face(target);
                dir = Vec3.directionFromRotation(0, ex.getYRot());
            }
            if (t % 3 == 0 && t < 16) { // 扇の予告（地面の赤い光）
                for (int k = -6; k <= 6; k++) {
                    Vec3 d = Vec3.directionFromRotation(0, ex.getYRot() + k * IAI_ARC / 12.0F);
                    Fx.line(server, glowFx(C_CRIMSON, 0.3F, 6), ex.position().add(0, 0.15, 0).add(d.scale(2.0)), ex.position().add(0, 0.15, 0).add(d.scale(IAI_RANGE)), 2.5);
                }
            }
            if (t == 16) {
                ex.iaiCharging = false;
                Vec3 at = ex.position();
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, ex.getBoundingBox().inflate(IAI_RANGE, 4.0, IAI_RANGE), ChronoGazer::canHurt)) {
                    Vec3 to = e.position().subtract(at).multiply(1, 0, 1);
                    if (to.length() > IAI_RANGE + e.getBbWidth() * 0.5) continue;
                    if (to.lengthSqr() > 0.25 && to.normalize().dot(dir) < Math.cos(Math.toRadians(IAI_ARC / 2.0))) continue;
                    e.hurt(ex.damageSources().mobAttack(ex), ex.damageFor(e, IAI));
                    e.knockback(1.2, -dir.x, -dir.z);
                }
                for (int k = -3; k <= 3; k++) { // 扇に広がる光の刃
                    Vec3 d = Vec3.directionFromRotation(0, ex.getYRot() + k * IAI_ARC / 6.0F);
                    Vec3 from = at.add(0, 1.3 * SIZE, 0);
                    LuxionFxEntity.spawn(server, LuxionFxEntity.WAVE, from.add(d.scale(0.8)), from.add(d.scale(IAI_RANGE)), true);
                }
                Fx.slash(server, at.add(0, 1.3 * SIZE, 0).add(dir.scale(3.0)), C_WHITE, 5.0F * SIZE, 10, 0.0F);
                Fx.slash(server, at.add(0, 1.3 * SIZE, 0).add(dir.scale(3.0)), C_CRIMSON, 6.0F * SIZE, 12, 0.1F);
                server.sendParticles(ParticleTypes.FLASH, at.x, at.y + 2, at.z, 1, 0, 0, 0, 0);
                ex.level().broadcastEntityEvent(ex, EVENT_SLASH_FLAT);
                ex.playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.5F, 1.9F);
                ex.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F);
            }
            if (t >= 26) finish();
        }

        /** 地を這う光刃: 刀を地に突き立て、3 方向へ光の亀裂が走り、8 Tick 遅れて刃が噴き出す */
        private void tickCracks(ServerLevel server) {
            ex.getNavigation().stop();
            if (t == 0) {
                face(target);
                lanes.clear();
                laneHits.clear();
                float yaw = ex.getYRot();
                for (int k = -1; k <= 1; k++) {
                    lanes.add(Vec3.directionFromRotation(0, yaw + k * 35.0F));
                    laneHits.add(new HashSet<>());
                }
                center = ex.position();
                ex.level().broadcastEntityEvent(ex, EVENT_SLASH_DOWN);
                ex.playSound(SoundEvents.ANVIL_LAND, 1.5F, 0.6F);
                Fx.shockwave(server, center.add(0, 0.1, 0), C_GOLD, 3.0F, 12);
            }
            double front = (t - 4) * 1.0, erupt = (t - 12) * 1.0;
            for (int k = 0; k < lanes.size(); k++) {
                Vec3 d = lanes.get(k);
                if (front > 0 && front <= CRACK_LENGTH) { // 走る亀裂
                    Vec3 p = center.add(d.scale(front)).add(0, 0.1, 0);
                    Fx.burst(server, glowFx(C_CRIMSON, 0.45F, 16), p, 4, 0.05);
                    Fx.one(server, starFx(C_GOLD, 0.3F, 16), p, Vec3.ZERO);
                }
                if (erupt > 0 && erupt <= CRACK_LENGTH) { // 噴き出す刃
                    Vec3 p = center.add(d.scale(erupt));
                    Fx.line(server, glowFx(C_WHITE, 0.5F, 10), p, p.add(0, 3.0, 0), 0.4);
                    Fx.line(server, glowFx(C_CRIMSON, 0.8F, 12), p, p.add(0, 2.4, 0), 0.6);
                    for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.3, 3.0, 1.3), ChronoGazer::canHurt)) {
                        if (laneHits.get(k).add(e)) {
                            e.hurt(ex.damageSources().mobAttack(ex), ex.damageFor(e, CRACK));
                            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                        }
                    }
                    if (t % 3 == 0) server.playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_BREAK, ex.getSoundSource(), 1.0F, 1.4F);
                }
            }
            if (erupt > CRACK_LENGTH) finish();
        }

        /** 逆噴射の後退斬り: ブースターで後ろへ跳び、着地と同時に光刃の波 */
        private void tickBackstep(ServerLevel server) {
            if (t == 0) {
                Vec3 away = ex.position().subtract(target.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, ex.getYRot() + 180.0F) : away.normalize();
                ex.setDeltaMovement(away.scale(1.6).add(0, 0.55, 0));
                ex.hasImpulse = true;
                face(target);
                ex.level().broadcastEntityEvent(ex, EVENT_BLINK_PREP);
                ex.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0F, 1.2F);
                Fx.burst(server, glowFx(C_CRIMSON, 0.7F, 16), ex.position().add(0, 1.6 * SIZE, 0).subtract(away.scale(1.0)), 24, 0.3);
                ex.pressed = 0;
            } else {
                face(target);
                ex.resetFallDistance();
            }
            if (t >= 6 && (ex.onGround() || t >= 16)) {
                ex.wave(target);
                Fx.shockwave(server, ex.position().add(0, 0.1, 0), C_GOLD, 4.0F, 12);
                finish();
            }
        }

        /** 光の檻: 相手を光の柱の輪で囲み、2 秒後に内側へ斬撃を集める */
        private void tickCage(ServerLevel server) {
            ex.getNavigation().stop();
            if (t == 0) {
                center = target.position();
                face(target);
                ex.placeCage(center);
                ex.level().broadcastEntityEvent(ex, EVENT_FLURRY_RAISE);
                ex.playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
                ex.message(server, "message.automata_valkyries.luxion_ex.cage", ChatFormatting.GOLD);
            }
            if (t < 40 && t % 4 == 0) {
                Fx.mandala(server, center.add(0, 0.1, 0), C_CRIMSON, (float) CAGE_RADIUS, 6, false, t % 8 == 0);
                face(target);
            }
            if (t == 40) {
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(CAGE_RADIUS, 5.0, CAGE_RADIUS), ChronoGazer::canHurt)) {
                    if (e.position().subtract(center).multiply(1, 0, 1).length() > CAGE_RADIUS - 0.4) continue;
                    e.hurt(ex.damageSources().mobAttack(ex), ex.damageFor(e, CAGE));
                }
                for (int i = 0; i < 12; i++) { // 柱から内へ集まる斬撃
                    double a = Math.PI * 2 * i / 12;
                    Vec3 from = center.add(Math.cos(a) * CAGE_RADIUS, 1.5, Math.sin(a) * CAGE_RADIUS);
                    Fx.line(server, glowFx(i % 2 == 0 ? C_WHITE : C_CRIMSON, 0.5F, 10), from, center.add(0, 1.0, 0), 0.4);
                }
                Fx.slash(server, center.add(0, 1.2, 0), C_GOLD, 4.0F, 10, 0.4F);
                Fx.slash(server, center.add(0, 1.2, 0), C_CRIMSON, 4.0F, 10, -0.4F);
                server.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
                ex.level().broadcastEntityEvent(ex, EVENT_SLASH_DOWN);
                ex.playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.8F);
                ex.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.7F);
            }
            if (t >= 48) {
                ex.clearCage();
                finish();
            }
        }

        /** 残像の分け身: 相手を挟む 2 か所に分身が現れ、3 方向から光刃の波 */
        private void tickSplit(ServerLevel server) {
            ex.getNavigation().stop();
            face(target);
            if (t == 0) {
                double base = Mth.atan2(ex.getZ() - target.getZ(), ex.getX() - target.getX());
                double dist = Math.max(8.0, ex.distanceTo(target));
                for (int i = 0; i < 2; i++) {
                    double a = base + (i == 0 ? 1 : -1) * Math.toRadians(110);
                    Vec3 at = new Vec3(target.getX() + Math.cos(a) * dist, ex.getY(), target.getZ() + Math.sin(a) * dist);
                    ex.setShadow(i, at, yawTo(at, target.position()));
                    ex.shadowFlash(at);
                }
                ex.level().broadcastEntityEvent(ex, EVENT_WAVE_CHARGE);
                ex.playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 2.0F, 1.0F);
            }
            if (t > 0 && t < 18) for (int i = 0; i < 2; i++) ex.setShadow(i, ex.shadowPos(i), yawTo(ex.shadowPos(i), target.position()));
            if (t == 18) {
                for (int i = 0; i < 2; i++) ex.waveFrom(ex.shadowPos(i).add(0, 1.3 * SIZE, 0), target);
                ex.wave(target);
            }
            if (t == 26) {
                for (int i = 0; i < 2; i++) ex.shadowFlash(ex.shadowPos(i));
                ex.clearShadows();
                finish();
            }
        }

        /** 連鎖瞬動: 近くの相手を順に瞬動で斬り継ぐ（6 回。相手が少なければ同じ相手のまわりを） */
        private void tickChain(ServerLevel server) {
            if (t == 0) {
                chain.clear();
                chain.addAll(server.getEntitiesOfClass(LivingEntity.class, ex.getBoundingBox().inflate(16.0, 6.0, 16.0), ChronoGazer::canHurt));
                chain.sort((a, b) -> Double.compare(a.distanceToSqr(ex), b.distanceToSqr(ex)));
                if (chain.isEmpty()) chain.add(target);
                ex.level().broadcastEntityEvent(ex, EVENT_TWO_HAND);
            }
            if (t % 5 == 0) {
                int n = t / 5;
                if (n >= 6) {
                    finish();
                    return;
                }
                LivingEntity e = chain.get(n % chain.size());
                if (!e.isAlive()) e = target;
                ex.blink(ex.spotAround(e, ex.random.nextDouble() * Math.PI * 2, BESIDE));
                face(e);
                ex.slash(FLURRY_HIT);
            }
        }

        /** 分身の 3 連撃のルクシオンアクセラレータ: 跳ぶ（20）→ 狙う（影 A の蹴りまで 32）→ 影 A・影 B・本体の順に {@link #CLONE_KICK_GAP} Tick ずつずらして蹴る */
        private void tickCloneAccel(ServerLevel server) {
            ex.getNavigation().stop();
            ex.resetFallDistance();
            int leap = ACCEL_LEAP_TICKS, kick0 = leap + ACCEL_AIM_TICKS;
            if (t == 0) {
                Vec3 tp = targetOk() ? target.position() : ex.position();
                Vec3 back = ex.position().subtract(tp).multiply(1, 0, 1);
                back = back.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, ex.getYRot() + 180.0F) : back.normalize();
                double base = Mth.atan2(back.z, back.x);
                for (int i = 0; i < 3; i++) {
                    double a = base + (i == 2 ? 0.0 : (i == 0 ? 1 : -1) * Math.toRadians(115));
                    apex[i] = new Vec3(tp.x + Math.cos(a) * ACCEL_RANGE, ex.getY() + ACCEL_HEIGHT, tp.z + Math.sin(a) * ACCEL_RANGE);
                    aim[i] = tp;
                    kicked[i] = landed[i] = false;
                }
                kickHits.clear();
                for (int i = 0; i < 3; i++) kickHits.add(new HashSet<>());
                for (int i = 0; i < 2; i++) {
                    ex.setShadow(i, apex[i], yawTo(apex[i], tp));
                    ex.shadowFlash(apex[i]);
                }
                ex.level().broadcastEntityEvent(ex, EVENT_ACCEL_LEAP);
                ex.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 1.3F);
                ex.message(server, "message.automata_valkyries.luxion_ex.clone_accel", ChatFormatting.DARK_PURPLE);
            }
            // 本体: 跳んで頂点にとどまる（蹴るまで）
            if (!kicked[2]) {
                Vec3 d = apex[2].subtract(ex.position()).scale(0.3);
                double max = t < leap ? 2.2 : 0.6;
                ex.setDeltaMovement(d.length() > max ? d.normalize().scale(max) : d);
                if (t == 5) ex.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0F, 0.8F);
                if (t == leap) {
                    ex.level().broadcastEntityEvent(ex, EVENT_ACCEL_AIM);
                    ex.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F);
                }
                if (targetOk()) face(target);
            }
            for (int i = 0; i < 3; i++) {
                int kickAt = kick0 + i * CLONE_KICK_GAP;
                boolean locked = t >= kickAt - ACCEL_LOCK;
                if (t >= leap && t < kickAt) { // 狙う（行く先を読む。最後は固定）
                    if (!locked && targetOk()) {
                        Vec3 vel = ex.attackGoal != null ? ex.attackGoal.targetVelocity() : Vec3.ZERO;
                        aim[i] = target.position().add(vel.multiply(1, 0, 1).scale(kickAt - t + 4));
                    }
                    if (t == kickAt - ACCEL_LOCK) LuxionFxEntity.spawn(server, LuxionFxEntity.RAIL, from(i).add(0, 0.8 * SIZE, 0), aim[i], true);
                    if (t % 2 == 0) {
                        Vec3 from = from(i).add(0, 0.8 * SIZE, 0);
                        Fx.line(server, glowFx(i == 2 ? C_CRIMSON : C_VIOLET, locked ? 0.5F : 0.35F, 6), from, aim[i], 0.6);
                        Fx.mandala(server, aim[i].add(0, 0.1, 0), C_CRIMSON, 2.5F, 6, false, true);
                    }
                    if (i < 2) ex.setShadow(i, apex[i], yawTo(apex[i], aim[i]));
                }
                if (t == kickAt) {
                    kicked[i] = true;
                    if (i == 2) {
                        bodyFrom = ex.position().add(0, 0.8 * SIZE, 0);
                        Vec3 v = aim[2].subtract(ex.position());
                        ex.setAccelPitch((float) Math.atan2(-v.y, Math.max(0.01, Math.sqrt(v.x * v.x + v.z * v.z))));
                        float yaw = yawTo(ex.position(), aim[2]);
                        ex.setYRot(yaw);
                        ex.setYBodyRot(yaw);
                        ex.level().broadcastEntityEvent(ex, EVENT_ACCEL_KICK);
                    }
                    ex.playSound(SoundEvents.TRIDENT_RIPTIDE_3, 2.0F, 1.0F + i * 0.15F);
                }
                if (kicked[i] && !landed[i]) {
                    if (i < 2) {
                        if (ex.cloneKickStep(i, ex.shadowPos(i), aim[i], kickHits.get(i))) {
                            landed[i] = true;
                            ex.cloneImpact(aim[i], kickHits.get(i));
                            ex.shadowFlash(aim[i]);
                            if (landed[0] && landed[1]) ex.clearShadows();
                            else ex.setShadow(i, apex[i].add(0, 200, 0), 0.0F); // 着いた影は消える（もう 1 体の影を残す）
                        }
                    } else {
                        Vec3 here = ex.position();
                        Vec3 left = aim[2].subtract(here);
                        boolean last = t - kickAt >= ACCEL_KICK_MAX || left.length() <= ACCEL_SPEED;
                        Vec3 next = left.length() <= ACCEL_SPEED ? aim[2] : here.add(left.normalize().scale(ACCEL_SPEED));
                        Vec3 stop = ex.accelStep(here, next, kickHits.get(2), last);
                        if (stop != null) {
                            landed[2] = true;
                            ex.accelImpact(stop, bodyFrom, kickHits.get(2)); // 第 3 形態は続けて落光の連撃
                            ex.setAccelPitch(0.0F);
                            t = 1000; // 着地へ
                        }
                    }
                }
            }
            if (t >= 1000) {
                ex.setDeltaMovement(new Vec3(0, ex.getDeltaMovement().y, 0));
                if (t >= 1000 + ACCEL_LAND_TICKS) {
                    ex.clearShadows();
                    finish();
                }
            }
        }

        private boolean targetOk() {
            return target != null && target.isAlive();
        }

        /** 3 連撃の i 番目の今の位置（影は影の位置、本体は自分） */
        private Vec3 from(int i) {
            return i == 2 ? ex.position() : apex[i];
        }

        /** 落光の連撃: 相手の頭上へ跳び（0.7 秒、足元に印）、踏み抜く */
        private void tickStomp(ServerLevel server) {
            ex.getNavigation().stop();
            ex.resetFallDistance();
            LivingEntity tgt = targetOk() ? target : ex.getTarget();
            if (t == 0) {
                groundSpot = tgt != null && tgt.isAlive() ? tgt.position() : ex.position();
                Vec3 up = groundSpot.add(0, 9.0, 0);
                ex.blink(up);
                ex.hoverAt(up);
                ex.level().broadcastEntityEvent(ex, EVENT_ACCEL_LEAP);
                ex.playSound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.0F, 0.9F);
            }
            if (t > 0 && t < 14) {
                if (t <= 8 && tgt != null && tgt.isAlive()) groundSpot = tgt.position();
                ex.hoverAt(groundSpot.add(0, 9.0, 0));
                if (t % 2 == 0) Fx.mandala(server, groundSpot.add(0, 0.1, 0), t > 8 ? C_GOLD : C_CRIMSON, (float) STOMP_RADIUS, 6, false, true);
            }
            if (t == 14) {
                ex.blink(groundSpot);
                ex.blastAround(groundSpot, STOMP_RADIUS, STOMP);
                Fx.shockwave(server, groundSpot.add(0, 0.1, 0), C_WHITE, 6.0F, 14);
                Fx.shockwave(server, groundSpot.add(0, 0.2, 0), C_CRIMSON, 9.0F, 20);
                Fx.burst(server, starFx(C_GOLD, 0.8F, 30), groundSpot.add(0, 1.0, 0), 60, 0.5);
                server.sendParticles(ParticleTypes.FLASH, groundSpot.x, groundSpot.y + 1.0, groundSpot.z, 1, 0, 0, 0, 0);
                ex.level().broadcastEntityEvent(ex, EVENT_ACCEL_IMPACT);
                ex.playSound(SoundEvents.GENERIC_EXPLODE, 2.5F, 1.1F);
            }
            if (t >= 24) finish();
        }
    }

    /** 体力の割合（テスト用） */
    public float healthRatio() {
        return DirectRead.health(this) / getMaxHealth();
    }
}
