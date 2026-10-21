package com.automatavalkyries.gametest.fake;

import com.automatavalkyries.mixin.LivingEntityAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

/**
 * GameTest 用: HP の読み書きを切り詰める、作り替えられた同期データの入れ物（SuperSteve の {@code HurtSynchedEntityData} の再現）。
 * HP の鍵の値を {@link #CEILING} 以下に切り詰めて読み、書く。架空の MOD のクラスとして扱われる（{@link FakeKillMod#install()} の後）
 */
public class FakeSwappedEntityData extends SynchedEntityData {

    public static final float CEILING = 5.0F;

    public FakeSwappedEntityData(Entity entity) {
        super(entity);
    }

    @SuppressWarnings("unchecked")
    private static <T> T clamp(EntityDataAccessor<T> accessor, T value) {
        if (accessor == LivingEntityAccessor.automata_valkyries$getDataHealthId() && value instanceof Float f) {
            return (T) Float.valueOf(Math.min(f, CEILING));
        }
        return value;
    }

    @Override
    public <T> T get(EntityDataAccessor<T> accessor) {
        return clamp(accessor, super.get(accessor));
    }

    @Override
    public <T> void set(EntityDataAccessor<T> accessor, T value, boolean force) {
        super.set(accessor, clamp(accessor, value), force);
    }
}
