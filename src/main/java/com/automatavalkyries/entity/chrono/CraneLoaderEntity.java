package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 物資倉庫の中ボス「荷役機 クレーン・ローダー」（2026-10-15 ユーザー指定。HP {@value #MAX_HP}、数値は【仮】）。
 * 2 本の履帯で走る重い荷役機。上にクレーンの腕（先に吊り鉤）、前に 2 本のフォーク。
 * <ul>
 *   <li>コンテナ投げ: クレーンで掴んだコンテナを相手へ山なりに投げる（{@value #THROW}・半径 3 の炸裂。落ちる所に赤い印）</li>
 *   <li>フォーク突進: 1 秒、前へ赤い予告線を出して構え、{@value #CHARGE_LEN} ブロック突っ込む（{@value #CHARGE}・強く吹き飛ばす。壁で止まる）</li>
 *   <li>積み荷落とし: 相手のまわり 4 か所に印を出し、1.5 秒後にクレーンから吊ったコンテナを落とす（1 つ {@value #DROP}）</li>
 *   <li>第 2 形態（HP 50%）: 「積み荷が暴れ出す」——ミミック・コンテナを 2 つ呼び、技の間隔が 3/4</li>
 * </ul>
 */
public class CraneLoaderEntity extends ChronoMidBoss {

    public static final float MAX_HP = 800.0F, THROW = 14.0F, CHARGE = 18.0F, DROP = 14.0F;
    public static final int CHARGE_LEN = 14;
    public static final byte EVENT_THROW = 70, EVENT_CHARGE = 71, EVENT_DROP = 72;
    private static final DustParticleOptions WARN = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.2F), 1.2F);

    /** クライアント: 技の合図の Tick（描画） */
    public int throwTick = -100, chargeTick = -100, dropTick = -100;

    public CraneLoaderEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.YELLOW;
    }

    @Override
    protected ResourceLocation rewardLoot() {
        return loot("chests/supply_depot_reward");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new LoaderGoal(this));
        ChronoGazer.addTargets(this, 8.0);
    }

    @Override
    protected void onPhase2() {
        if (!(level() instanceof ServerLevel server)) return;
        playSound(SoundEvents.CHEST_OPEN, 2.0F, 0.5F);
        for (int i = 0; i < 2; i++) { // 積み荷が暴れ出す
            MimicContainerEntity mimic = ModEntities.MIMIC_CONTAINER.get().create(server);
            if (mimic == null) continue;
            Vec3 p = local(0.5, -1.5, i == 0 ? 2.5 : -2.5);
            mimic.moveTo(p.x, p.y, p.z, getYRot(), 0.0F);
            mimic.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            server.addFreshEntity(mimic);
            mimic.wake(getTarget());
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_THROW -> throwTick = tickCount;
            case EVENT_CHARGE -> chargeTick = tickCount;
            case EVENT_DROP -> dropTick = tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    /** コンテナ投げ: クレーンの先から、相手の少し先へ */
    void throwCargo(LivingEntity t) {
        Vec3 from = local(4.2, 0.5, 0);
        Vec3 to = t.position().add(t.getDeltaMovement().multiply(1, 0, 1).scale(15));
        CargoContainerEntity.hurl(this, from, to, 30, THROW, 3.0F);
        playSound(SoundEvents.PISTON_EXTEND, 1.5F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_THROW);
    }

    /** 積み荷落とし: 印を出した所（4 か所）の真上からコンテナを落とす */
    void dropCargo(List<Vec3> spots) {
        for (Vec3 s : spots) {
            Vec3 from = s.add(0, 10, 0);
            CargoContainerEntity.hurl(this, from, s, 14, DROP, 2.5F);
        }
        playSound(SoundEvents.CHAIN_BREAK, 2.0F, 0.6F);
        level().broadcastEntityEvent(this, EVENT_DROP);
    }

    /** 突進の 1 Tick: 前へ進み、触れた相手を吹き飛ばす。進めなくなったら false */
    boolean chargeStep(Vec3 dir, List<LivingEntity> hit) {
        Vec3 before = position();
        setDeltaMovement(dir.scale(0.9).add(0, getDeltaMovement().y, 0));
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        if (level() instanceof ServerLevel server) {
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), ChronoGazer::canHurt)) {
                if (hit.contains(e)) continue;
                hit.add(e);
                e.hurt(damageSources().mobAttack(this), CHARGE);
                e.push(dir.x * 1.6, 0.6, dir.z * 1.6);
                playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.7F);
            }
            BlockState ground = server.getBlockState(blockPosition().below());
            if (!ground.isAir() && tickCount % 2 == 0) {
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 6, 0.8, 0.1, 0.8, 0.1);
            }
        }
        return position().subtract(before).horizontalDistanceSqr() > 0.2 * 0.2;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.MINECART_RIDING, 0.4F, 0.6F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PISTON_CONTRACT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected float getSoundVolume() {
        return 1.5F;
    }

    /** 戦い方: 相手へ詰めながら、投げ・突進・落としを順に。投げは遠い相手、突進は 5〜16 m の相手へ */
    static class LoaderGoal extends Goal {
        private static final int NONE = 0, CHARGE_WIND = 1, CHARGING = 2, DROP_WIND = 3;
        private final CraneLoaderEntity boss;
        private int throwCd = 40, chargeCd = 120, dropCd = 200, repath, state, stateT;
        private Vec3 chargeDir = Vec3.ZERO;
        private final List<Vec3> dropSpots = new ArrayList<>();
        private final List<LivingEntity> chargeHit = new ArrayList<>();

        LoaderGoal(CraneLoaderEntity boss) {
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
            throwCd--;
            chargeCd--;
            dropCd--;
            int st = stateT++;
            ServerLevel server = (ServerLevel) boss.level();
            switch (state) {
                case CHARGE_WIND -> { // 構え: 向きを決めて前へ赤い予告線
                    boss.getNavigation().stop();
                    if (st % 3 == 0) Fx.line(server, MementoMoriEntity.glow(0xFF3040, 0.3F, 6), boss.position().add(0, 0.6, 0), boss.position().add(chargeDir.scale(CHARGE_LEN)).add(0, 0.6, 0), 0.8);
                    boss.setYRot((float) (Math.atan2(chargeDir.z, chargeDir.x) * 180 / Math.PI) - 90.0F);
                    boss.yBodyRot = boss.getYRot();
                    if (st >= 20) {
                        state = CHARGING;
                        stateT = 0;
                        chargeHit.clear();
                        boss.playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.6F);
                    }
                    return;
                }
                case CHARGING -> {
                    if (!boss.chargeStep(chargeDir, chargeHit) || st >= CHARGE_LEN / 0.9) {
                        state = NONE;
                        boss.playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
                    }
                    return;
                }
                case DROP_WIND -> { // 落とす所の印を出して待つ
                    if (st % 4 == 0) for (Vec3 s : dropSpots) server.sendParticles(WARN, s.x, s.y + 0.2, s.z, 6, 0.8, 0.0, 0.8, 0.0);
                    if (st >= 16) { // 落ちるまで 14 Tick（印から 1.5 秒）
                        boss.dropCargo(dropSpots);
                        state = NONE;
                    }
                    return;
                }
                default -> { }
            }
            boss.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = boss.distanceTo(t);
            if (--repath <= 0) {
                repath = 10;
                if (d > 5.0) boss.getNavigation().moveTo(t, 1.0);
                else boss.getNavigation().stop();
            }
            if (chargeCd <= 0 && d >= 5.0 && d <= 16.0 && boss.hasLineOfSight(t)) {
                chargeCd = boss.cd(160);
                Vec3 to = t.position().subtract(boss.position()).multiply(1, 0, 1);
                chargeDir = to.lengthSqr() > 1.0e-4 ? to.normalize() : Vec3.directionFromRotation(0, boss.yBodyRot);
                state = CHARGE_WIND;
                stateT = 0;
                boss.level().broadcastEntityEvent(boss, EVENT_CHARGE);
                boss.playSound(SoundEvents.PISTON_EXTEND, 1.5F, 0.5F);
            } else if (dropCd <= 0) {
                dropCd = boss.cd(220);
                dropSpots.clear();
                dropSpots.add(t.position());
                for (int i = 0; i < 3; i++) {
                    double a = boss.random.nextDouble() * Math.PI * 2;
                    dropSpots.add(t.position().add(Math.cos(a) * 3.0, 0, Math.sin(a) * 3.0));
                }
                for (Vec3 s : dropSpots) Fx.mandala(server, s.add(0, 0.1, 0), 0xFF3040, 1.6F, 30, false, false);
                state = DROP_WIND;
                stateT = 0;
                boss.playSound(SoundEvents.CHAIN_PLACE, 2.0F, 0.6F);
            } else if (throwCd <= 0 && d > 4.0) {
                throwCd = boss.cd(60);
                boss.throwCargo(t);
            }
        }
    }
}
