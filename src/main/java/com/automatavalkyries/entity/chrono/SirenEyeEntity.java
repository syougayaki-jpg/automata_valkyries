package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 侵食基地の防衛兵器、警報ドローン「サイレン・アイ」（拡張仕様書 §10.2。2026-10-15 実装、数値は【仮】）。飛ぶ大きな一つ目と、頭の上で回る警告灯。
 * 侵入者を見つけるとサイレンを鳴らし（半径 {@value #ALARM} m の仲間に相手を教え、近くのプレイヤーに知らせる）、{@value #REINFORCE} Tick ごとに増援の索敵ドローンを呼ぶ（同時に {@value #MAX_CALLED} 機まで）。
 * 自分は撃たず、相手から {@value #KEEP} m ほど離れて逃げ回る（暗殺の最優先対象。兵姫も優先して狙う）。HP は低い
 */
public class SirenEyeEntity extends ScoutDroneEntity {

    public static final double ALARM = 32.0, KEEP = 12.0;
    public static final int REINFORCE = 200, MAX_CALLED = 4;
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.2F), 1.4F);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> SOUNDING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SirenEyeEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private final List<UUID> called = new ArrayList<>();
    private int reinforce = 40;
    private boolean sounding;

    public SirenEyeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.7)
                .add(Attributes.FOLLOW_RANGE, ALARM);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new KeepAwayGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this, 16.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SOUNDING, false);
    }

    /** 警報が鳴っているか（同期。描画の警告灯の回り） */
    public boolean isSounding() {
        return entityData.get(SOUNDING);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (isSounding() || tickCount % 10 == 0) level().addParticle(RED, getX(), getY() + 1.1, getZ(), 0, 0.02, 0);
            return;
        }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) {
            sounding = false;
            entityData.set(SOUNDING, false);
            return;
        }
        entityData.set(SOUNDING, true);
        if (!sounding) { // 見つけた: サイレン
            sounding = true;
            reinforce = 40;
            playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0F, 0.5F);
            ChronoGazer.alert(this, t, ALARM);
            for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(ALARM))) {
                if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.automata_valkyries.siren_eye.alarm").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }
        if (tickCount % 20 == 0) playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 1.6F, tickCount % 40 == 0 ? 1.8F : 1.2F); // 鳴り続けるサイレン
        if (--reinforce <= 0 && level() instanceof ServerLevel server) {
            reinforce = REINFORCE;
            called.removeIf(id -> !(server.getEntity(id) instanceof LivingEntity e) || !e.isAlive());
            if (called.size() < MAX_CALLED) {
                ScoutDroneEntity drone = ModEntities.CHRONO_SCOUT_DRONE.get().create(server);
                if (drone != null) {
                    Vec3 p = position().add(random.nextGaussian() * 2.0, 1.5, random.nextGaussian() * 2.0);
                    drone.moveTo(p.x, p.y, p.z, random.nextFloat() * 360.0F, 0.0F);
                    drone.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.REINFORCEMENT, null, null);
                    drone.setTarget(t);
                    server.addFreshEntity(drone);
                    called.add(drone.getUUID());
                    server.sendParticles(RED, p.x, p.y, p.z, 20, 0.5, 0.5, 0.5, 0.0);
                    playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.6F);
                }
            }
        }
    }

    /** 相手から KEEP m 離れた高い所を保ち、近づかれたら反対へ逃げる */
    static class KeepAwayGoal extends Goal {
        private final SirenEyeEntity eye;
        private float orbit;

        KeepAwayGoal(SirenEyeEntity eye) {
            this.eye = eye;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return eye.getTarget() != null && eye.getTarget().isAlive();
        }

        @Override
        public void start() {
            orbit = eye.random.nextFloat() * 6.28F;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = eye.getTarget();
            if (t == null) return;
            eye.getLookControl().setLookAt(t, 30.0F, 30.0F);
            Vec3 away = eye.position().subtract(t.position()).multiply(1, 0, 1);
            double dist = away.length();
            if (dist < KEEP * 0.6 && dist > 1.0e-3) { // 近い: 反対へ逃げる
                Vec3 p = t.position().add(away.normalize().scale(KEEP)).add(0, 6.0, 0);
                eye.getMoveControl().setWantedPosition(p.x, p.y, p.z, 1.3);
                return;
            }
            orbit += 0.015F;
            if (eye.tickCount % 15 == 0 || eye.getNavigation().isDone()) {
                Vec3 p = t.position().add(Math.cos(orbit) * KEEP, 6.0 + Math.sin(orbit * 2.0F), Math.sin(orbit) * KEEP);
                eye.getMoveControl().setWantedPosition(p.x, p.y, p.z, 1.0);
            }
        }
    }
}
