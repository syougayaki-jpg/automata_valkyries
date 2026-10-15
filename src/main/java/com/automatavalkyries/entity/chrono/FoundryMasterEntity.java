package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 工場（ディメンション・ファウンドリ）の中ボス「工場長機 ファウンドリ・マスター」（2026-10-15 ユーザー指定。HP {@value #MAX_HP}、数値は【仮】）。
 * 回る台座の上の重い胴に、溶接機・プレスの槌・掴み爪・ドリルの 4 本の腕を持つ組立機。
 * <ul>
 *   <li>位相アンカーの無敵バリア: 半径 {@value PhaseCondenserEntity#LINK} m のサブ・コンデンサー（{@link PhaseCondenserEntity}）が 1 本でも立っている間は攻撃が届かない（紫の膜が光る）。
 *       部屋の四隅の 4 本を壊すと届く</li>
 *   <li>溶接アーク: 0.75 秒、相手へ赤い予告線を引き、その線の上を焼く（{@value #ARC}。予告の間に線から外れれば避けられる）</li>
 *   <li>プレス: 相手の足元に赤い輪を出し、1.25 秒後に槌を叩きつける（半径 3 に {@value #PRESS} と打ち上げ）</li>
 *   <li>回転アーム: 4 m より近い相手を 4 本の腕でなぎ払う（{@value #SPIN}・吹き飛ばし）</li>
 *   <li>ドローン組立: その場で索敵ドローンを 2 機組み立てる（同時に 4 機まで）</li>
 *   <li>第 2 形態（HP 50%）: 予備のコンデンサーを 2 本立ち上げて、もう一度バリアを張る。技の間隔が 3/4</li>
 * </ul>
 */
public class FoundryMasterEntity extends ChronoMidBoss {

    public static final float MAX_HP = 3000.0F, ARC = 16.0F, PRESS = 24.0F, SPIN = 14.0F;
    public static final byte EVENT_ARC = 70, EVENT_PRESS = 71, EVENT_SPIN = 72, EVENT_BUILD = 73, EVENT_BARRIER = 74;

    public int arcTick = -100, pressTick = -100, spinTick = -100, buildTick = -100, barrierTick = -100;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> SHIELDED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FoundryMasterEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public FoundryMasterEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 320;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.15)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 36.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SHIELDED, false);
    }

    /** 位相アンカーのバリアが張られているか（同期。描画の紫の膜） */
    public boolean isShielded() {
        return entityData.get(SHIELDED);
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected ResourceLocation rewardLoot() {
        return loot("chests/dimension_foundry_reward");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MasterGoal(this));
        ChronoGazer.addTargets(this, 12.0);
    }

    int condensers() {
        return level().getEntitiesOfClass(PhaseCondenserEntity.class, getBoundingBox().inflate(PhaseCondenserEntity.LINK), LivingEntity::isAlive).size();
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (!level().isClientSide && tickCount % 5 == 0) {
            boolean shielded = condensers() > 0;
            if (shielded != isShielded()) {
                entityData.set(SHIELDED, shielded);
                if (!shielded && level() instanceof ServerLevel server) { // バリアが落ちた
                    Fx.shockwave(server, position().add(0, 2.0, 0), PhaseCondenserEntity.C_PHASE, 8.0F, 20);
                    playSound(SoundEvents.GLASS_BREAK, 3.0F, 0.6F);
                    for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) {
                        p.displayClientMessage(Component.translatable("message.automata_valkyries.foundry_master.barrier_down").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
                    }
                }
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && isShielded() && tickCount % 2 == 0) { // 位相アンカーの紫の膜（体を包む球の上の光）
            double a = random.nextDouble() * Math.PI * 2, e = (random.nextDouble() - 0.3) * Math.PI * 0.5, r = 3.2;
            level().addParticle(MementoMoriEntity.glow(PhaseCondenserEntity.C_PHASE, 0.25F, 14), getX() + Math.cos(a) * Math.cos(e) * r,
                    getY() + 2.0 + Math.sin(e) * r, getZ() + Math.sin(a) * Math.cos(e) * r, 0, 0, 0);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isShielded() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.6F); // 位相アンカーのバリアで届かない
            level().broadcastEntityEvent(this, EVENT_BARRIER);
            if (source.getEntity() instanceof ServerPlayer p && tickCount % 20 == 0) {
                p.displayClientMessage(Component.translatable("message.automata_valkyries.foundry_master.barrier").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void onPhase2() {
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 0; i < 2; i++) { // 予備のコンデンサー
            PhaseCondenserEntity c = ModEntities.PHASE_CONDENSER.get().create(server);
            if (c == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 p = position().add(Math.cos(a) * 7.0, 0.5, Math.sin(a) * 7.0);
            c.moveTo(p.x, p.y, p.z, 0.0F, 0.0F);
            server.addFreshEntity(c);
            Fx.burst(server, MementoMoriEntity.glow(PhaseCondenserEntity.C_PHASE, 0.5F, 20), p.add(0, 1.5, 0), 20, 0.2);
        }
        playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.7F);
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) {
            p.displayClientMessage(Component.translatable("message.automata_valkyries.foundry_master.backup").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_ARC -> arcTick = tickCount;
            case EVENT_PRESS -> pressTick = tickCount;
            case EVENT_SPIN -> spinTick = tickCount;
            case EVENT_BUILD -> buildTick = tickCount;
            case EVENT_BARRIER -> barrierTick = tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    /** 溶接アーク: from から to の線の上（太さ 1）を焼く */
    void arc(Vec3 from, Vec3 to) {
        if (!(level() instanceof ServerLevel server)) return;
        Fx.line(server, MementoMoriEntity.glow(0xFFB040, 0.45F, 10), from, to, 0.4);
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 u = len > 1.0e-4 ? d.scale(1.0 / len) : Vec3.ZERO;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, to).inflate(1.0), ChronoGazer::canHurt)) {
            Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(from);
            double along = Math.max(0, Math.min(len, p.dot(u)));
            if (p.subtract(u.scale(along)).length() <= 1.0 + e.getBbWidth() * 0.5) {
                e.hurt(damageSources().mobAttack(this), ARC);
                e.setSecondsOnFire(3);
            }
        }
        playSound(SoundEvents.FIRECHARGE_USE, 1.5F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_ARC);
    }

    /** プレス: at の半径 3 を叩く */
    void press(Vec3 at) {
        if (!(level() instanceof ServerLevel server)) return;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(3.0, 2.0, 3.0), ChronoGazer::canHurt)) {
            if (e.position().subtract(at).horizontalDistance() > 3.0 + e.getBbWidth() * 0.5) continue;
            e.hurt(damageSources().mobAttack(this), PRESS);
            e.push(0, 0.8, 0);
        }
        BlockState ground = server.getBlockState(BlockPos.containing(at).below());
        if (!ground.isAir()) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 60, 1.5, 0.1, 1.5, 0.2);
        server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 3, 1.0, 0.1, 1.0, 0);
        playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_PRESS);
    }

    /** 回転アーム: 半径 4.5 をなぎ払う */
    void spin() {
        if (!(level() instanceof ServerLevel server)) return;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5, 1.0, 4.5), ChronoGazer::canHurt)) {
            if (e.distanceTo(this) > 5.5) continue;
            e.hurt(damageSources().mobAttack(this), SPIN);
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.4);
            e.push(push.x, 0.4, push.z);
        }
        server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY(0.5), getZ(), 8, 2.5, 0.3, 2.5, 0);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_SPIN);
    }

    /** ドローン組立: 索敵ドローンを 2 機（まわりに 4 機を超えない） */
    void build(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        int near = server.getEntitiesOfClass(ScoutDroneEntity.class, getBoundingBox().inflate(24.0), d -> d.isAlive() && !(d instanceof SirenEyeEntity) && !(d instanceof ShieldDroneEntity)).size();
        for (int i = 0; i < Math.min(2, 4 - near); i++) {
            ScoutDroneEntity drone = ModEntities.CHRONO_SCOUT_DRONE.get().create(server);
            if (drone == null) continue;
            Vec3 p = local(3.5, 1.5, i == 0 ? 2.0 : -2.0);
            drone.moveTo(p.x, p.y, p.z, getYRot(), 0.0F);
            drone.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            drone.setTarget(t);
            server.addFreshEntity(drone);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 20, 0.4, 0.4, 0.4, 0.2);
        }
        playSound(SoundEvents.SMITHING_TABLE_USE, 2.0F, 0.8F);
        level().broadcastEntityEvent(this, EVENT_BUILD);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.3F, 0.5F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PISTON_EXTEND;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    /** 戦い方: ゆっくり詰めながら、近ければ回転アーム、そうでなければ溶接アーク・プレス・組立を順に */
    static class MasterGoal extends Goal {
        private static final int NONE = 0, ARC_WIND = 1, PRESS_WIND = 2;
        private final FoundryMasterEntity boss;
        private int arcCd = 40, pressCd = 90, spinCd = 0, buildCd = 160, repath, state, stateT;
        private Vec3 arcFrom = Vec3.ZERO, arcTo = Vec3.ZERO, pressAt = Vec3.ZERO;

        MasterGoal(FoundryMasterEntity boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return boss.getTarget() != null && boss.getTarget().isAlive() && !boss.isDefeated();
        }

        @Override
        public void stop() {
            state = NONE;
            boss.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = boss.getTarget();
            if (t == null) return;
            arcCd--;
            pressCd--;
            spinCd--;
            buildCd--;
            ServerLevel server = (ServerLevel) boss.level();
            int st = stateT++;
            if (state == ARC_WIND) {
                if (st % 3 == 0) Fx.line(server, MementoMoriEntity.glow(0xFF3040, 0.2F, 5), arcFrom, arcTo, 0.8);
                if (st >= 15) {
                    boss.arc(arcFrom, arcTo);
                    state = NONE;
                }
                return;
            }
            if (state == PRESS_WIND) {
                if (st >= 25) {
                    boss.press(pressAt);
                    state = NONE;
                }
                return;
            }
            boss.getLookControl().setLookAt(t, 20.0F, 20.0F);
            double d = boss.distanceTo(t);
            if (--repath <= 0) {
                repath = 15;
                if (d > 6.0) boss.getNavigation().moveTo(t, 1.0);
                else boss.getNavigation().stop();
            }
            if (spinCd <= 0 && d < 4.0) {
                spinCd = boss.cd(40);
                boss.spin();
            } else if (arcCd <= 0 && boss.hasLineOfSight(t)) {
                arcCd = boss.cd(70);
                arcFrom = boss.local(2.8, 1.5, 1.5);
                Vec3 dir = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(arcFrom);
                arcTo = arcFrom.add(dir.normalize().scale(Math.min(20.0, dir.length() + 4.0)));
                state = ARC_WIND;
                stateT = 0;
                boss.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.8F);
            } else if (pressCd <= 0) {
                pressCd = boss.cd(110);
                pressAt = t.position();
                Fx.mandala(server, pressAt.add(0, 0.1, 0), 0xFF3040, 3.0F, 25, false, false);
                state = PRESS_WIND;
                stateT = 0;
                boss.playSound(SoundEvents.PISTON_EXTEND, 2.0F, 0.5F);
            } else if (buildCd <= 0) {
                buildCd = boss.cd(300);
                boss.build(t);
            }
        }
    }
}
