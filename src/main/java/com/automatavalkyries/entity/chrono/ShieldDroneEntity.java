package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.weapon.Fx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 前哨司令機ウォッチャーの盾ドローン（2026-10-15。見た目は索敵ドローン）。持ち主のまわり（半径 {@value #RADIUS}・頭の上）を回り、持ち主へ光の線をつなぐ。
 * 1 機でも残っている間、ウォッチャーが受けるダメージは半分。HP {@value #HP} で、撃ち落とすのが攻略。持ち主がいなくなると落ちて壊れる
 */
public class ShieldDroneEntity extends ScoutDroneEntity {

    public static final double RADIUS = 3.5;
    public static final float HP = 20.0F;

    @Nullable
    private UUID ownerId;
    @Nullable
    private OutpostWatcherEntity owner;
    private float angle;

    public ShieldDroneEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.8)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        // 自分では狙わず、持ち主のまわりを回るだけ
    }

    void bind(OutpostWatcherEntity owner, float angle) {
        this.owner = owner;
        this.ownerId = owner.getUUID();
        this.angle = angle;
        setPersistenceRequired();
    }

    @Nullable
    OutpostWatcherEntity owner() {
        if (owner == null && ownerId != null && level() instanceof ServerLevel server && server.getEntity(ownerId) instanceof OutpostWatcherEntity w) owner = w;
        return owner;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        OutpostWatcherEntity w = owner();
        if (w == null || !w.isAlive() || w.isDefeated()) {
            if (tickCount > 40) hurt(damageSources().generic(), Float.MAX_VALUE); // 持ち主がいない: 落ちて壊れる
            return;
        }
        angle += 0.05F;
        Vec3 spot = w.position().add(Math.cos(angle) * RADIUS, 4.8 + Math.sin(angle * 2.0F) * 0.3, Math.sin(angle) * RADIUS);
        getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.4);
        if (tickCount % 8 == 0 && level() instanceof ServerLevel server) {
            Fx.line(server, MementoMoriEntity.glow(0x8FE6FF, 0.15F, 6), position(), w.position().add(0, 2.6, 0), 0.9);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putFloat("Angle", angle);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        angle = tag.getFloat("Angle");
    }
}
