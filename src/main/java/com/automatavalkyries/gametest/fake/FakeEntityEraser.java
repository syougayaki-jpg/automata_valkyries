package com.automatavalkyries.gametest.fake;

import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.EntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityInLevelCallback;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 試験用の「他 MOD」の消去。The King Sword 1.0.9 の {@code EntityChunkMapEraser} と同じ手順（2026-10-25 に jar を読んで写した）:
 * 消去の印・追跡からの切り離し → 座標を NaN・HP 0・deathTime 20 → UUID と ID を乱数へ書き換えて追跡の一覧から外し discard。
 * {@link #wipeAllReferences} は、さらに Tick の一覧から外し、ID を -1・UUID を 0・ワールドへの通知先を空にする
 */
public final class FakeEntityEraser {

    private FakeEntityEraser() {}

    /** {@code purgeMaximum} */
    public static void purgeMaximum(LivingEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        if (!entity.isRemoved()) entity.setRemoved(Entity.RemovalReason.KILLED);
        ((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap().remove(entity.getId());
        // processDeepServerPurge
        entity.noPhysics = true;
        entity.setInvisible(true);
        entity.invulnerableTime = Integer.MAX_VALUE;
        entity.setPos(Double.NaN, Double.NEGATIVE_INFINITY, Double.NaN);
        entity.setDeltaMovement(0, 0, 0);
        entity.setHealth(0.0F);
        entity.deathTime = 20;
        // applyNetworkAnnihilation
        EntityAccessor fields = (EntityAccessor) entity;
        fields.automata_valkyries$setIdField(ThreadLocalRandom.current().nextInt(5_000_000, 9_000_000));
        fields.automata_valkyries$setUuidField(new UUID(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong()));
        ((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap().remove(entity.getId());
        entity.discard();
    }

    /**
     * {@code EntityRemove.forceRemove}: 相手のクラス（親を含む）の引数なしで名前に set・start・remove を含むメソッドをリフレクションで全部呼び
     * （onRemovedFromWorld・removeAfterChangingDimensions など）、消去の理由を直に書いてワールドへの通知先に消去を伝え、
     * purgeMaximum の後、区画・Tick の一覧・検索表・UUID の一覧から（書き換えた後の UUID・ID で）外し、通知先を空にする
     */
    public static void forceRemove(LivingEntity entity) {
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                String name = m.getName().toLowerCase(java.util.Locale.ROOT);
                if (m.getParameterCount() != 0 || java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (!name.contains("removedfromworld") && !name.contains("removeafterchangingdimensions")) continue; // 害のある物だけ真似る
                try {
                    m.setAccessible(true);
                    m.invoke(entity);
                } catch (Throwable ignored) {}
            }
        }
        EntityAccessor fields = (EntityAccessor) entity;
        fields.automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);
        fields.automata_valkyries$getLevelCallback().onRemove(Entity.RemovalReason.DISCARDED);
        purgeMaximum(entity);
        ServerLevel level = (ServerLevel) entity.level();
        @SuppressWarnings("unchecked")
        var manager = (com.automatavalkyries.mixin.EntityManagerAccessor<Entity>) (Object)
                ((com.automatavalkyries.mixin.ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();
        ((com.automatavalkyries.mixin.ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().remove(entity);
        level.getChunkSource().removeEntity(entity);
        manager.automata_valkyries$getVisibleEntityStorage().remove(entity);
        manager.automata_valkyries$getKnownUuids().remove(entity.getUUID());
        fields.automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
    }

    /**
     * {@code ForceDropHelp.dropLoot}: 倒せなかった相手からも、死亡のドロップ（全部・ルート表・装備）を直に呼び、持っている物の写しを足元に出す
     */
    public static void forceDrop(LivingEntity entity) {
        var source = entity.level().damageSources().genericKill();
        invoke(entity, "dropAllDeathLoot", new Class<?>[] {net.minecraft.world.damagesource.DamageSource.class}, source);
        invoke(entity, "dropFromLootTable", new Class<?>[] {net.minecraft.world.damagesource.DamageSource.class, boolean.class}, source, true);
        invoke(entity, "dropEquipment", new Class<?>[0]);
        if (!entity.getMainHandItem().isEmpty()) entity.spawnAtLocation(entity.getMainHandItem().copy());
    }

    /** 別の次元へ送る（他 MOD のポータル・転送装置の形） */
    public static net.minecraft.world.entity.Entity sendTo(LivingEntity entity, ServerLevel destination) {
        return entity.changeDimension(destination);
    }

    /** 消去と die の直接の呼び出し（即死の基本の 2 つ） */
    public static void discardAndDie(LivingEntity entity) {
        entity.discard();
        entity.die(entity.level().damageSources().genericKill());
    }

    private static void invoke(LivingEntity entity, String name, Class<?>[] types, Object... args) {
        try {
            var m = LivingEntity.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            m.invoke(entity, args);
        } catch (Throwable ignored) {}
    }

    /** {@code wipeAllReferences}: Tick の一覧からも外して、ID・UUID・通知先を潰す */
    public static void wipeAllReferences(LivingEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        for (int i = 0; i < 10; i++) {
            level.getChunkSource().removeEntity(entity);
            ((com.automatavalkyries.mixin.ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().remove(entity);
            purgeMaximum(entity);
        }
        EntityAccessor fields = (EntityAccessor) entity;
        fields.automata_valkyries$setIdField(-1);
        fields.automata_valkyries$setUuidField(new UUID(0L, 0L));
        fields.automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
    }
}
