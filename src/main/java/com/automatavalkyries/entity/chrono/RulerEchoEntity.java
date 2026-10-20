package com.automatavalkyries.entity.chrono;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * タイムルーラーの「残像の分身」（2026-10-20 ユーザー指定「残像の分身」）: 過去の自分の残像。見た目だけの人形で、動きと技はルーラーが動かす
 * （{@code TimeRulerEntity.echoes}。ルーラーの剪定の針・時針の一閃・巻き戻しの弾を少し遅れてなぞる）。受けない・ぶつからない・保存しない。
 * {@link #life()} Tick で薄れて消え、ルーラーが居なくなっても消える。描く側は {@code client/RulerEchoRenderer}（ルーラーの姿を半透明の紫で）
 */
public class RulerEchoEntity extends Entity {

    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(RulerEchoEntity.class, EntityDataSerializers.INT);

    /** 描く側の技の時刻（エンティティの出来事で届く。ルーラーと同じ構え） */
    public int needleTick = -100, slashTick = -100;
    @Nullable
    private UUID ruler;

    public RulerEchoEntity(EntityType<? extends RulerEchoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setRuler(Entity by, int life) {
        this.ruler = by.getUUID();
        entityData.set(LIFE, life);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(LIFE, 200);
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case TimeRulerEntity.EVENT_NEEDLE -> needleTick = tickCount;
            case TimeRulerEntity.EVENT_SLASH -> slashTick = tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        Entity owner = ruler == null ? null : server.getEntity(ruler);
        if (tickCount >= life() || owner == null || !owner.isAlive()) discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
