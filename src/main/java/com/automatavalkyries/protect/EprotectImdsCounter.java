package com.automatavalkyries.protect;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/**
 * インフィニティマタードミネーターソードリボーン（Infinity Matter Dominator Sword: Reborn / IMDS-Reborn）
 * の即死・抹消キル機構に対する特化カウンターハンドラー。
 */
public final class EprotectImdsCounter {

    private static boolean initialized = false;
    private static Set<UUID> forceDeadUuids = null;
    private static Set<UUID> removedUuids = null;
    private static Set<UUID> noaiUuids = null;
    private static Set<UUID> testUuids = null;
    private static Set<UUID> imdsInvUuids = null;

    private static Method unmarkMethod = null;
    private static Method unRemoveMethod = null;
    private static Method removeNoAiMethod = null;

    private static Class<?> iEntityImdsClass = null;
    private static Method setForceDeadMethod = null;
    private static Method setForceRemovedMethod = null;
    private static Method setInvulnerableMethod = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // 1. ImdsKillManager クラスの静的コレクションおよび制御メソッドを取得（存在する場合）
        try {
            Class<?> killManagerClass = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
            forceDeadUuids = getStaticSet(killManagerClass, "FORCE_DEAD_UUIDS");
            removedUuids = getStaticSet(killManagerClass, "REMOVED_UUIDS");
            noaiUuids = getStaticSet(killManagerClass, "NOAI_UUIDS");
            testUuids = getStaticSet(killManagerClass, "TEST_UUIDS");
            imdsInvUuids = getStaticSet(killManagerClass, "IMDS_INV_UUIDS");

            unmarkMethod = killManagerClass.getMethod("unmark", LivingEntity.class);
            unRemoveMethod = killManagerClass.getMethod("unRemove", net.minecraft.world.entity.Entity.class);
            removeNoAiMethod = killManagerClass.getMethod("removeNoAi", LivingEntity.class);
        } catch (Throwable ignored) {}

        // 2. IEntityImds インターフェースのメソッドを取得（存在する場合）
        try {
            iEntityImdsClass = Class.forName("net.mcreator.imds_reborn.IEntityImds");
            setForceDeadMethod = iEntityImdsClass.getMethod("imds$setForceDead", boolean.class);
            setForceRemovedMethod = iEntityImdsClass.getMethod("imds$setForceRemoved", boolean.class);
            setInvulnerableMethod = iEntityImdsClass.getMethod("imds$setInvulnerable", boolean.class);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> getStaticSet(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (Set<UUID>) f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * IMDS-Reborn の即死・抹消処理を常時解除する。
     * <p>
     * 以前は IMDS 自身の神無敵（IMDS_INV_UUIDS / imds$setInvulnerable）を奪って保護対象を完全無敵にしていたが、
     * IMDS が入っている環境だけ一切ダメージを受けず倒せなくなり、被弾量の上限（10 秒に最大 HP の 10%）と食い違うのでやめた。
     * 過去に付けた神無敵が保存データに残っていても外す。
     * </p>
     */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) {
            init();
        }

        UUID uuid = entity.getUUID();

        // 1. ImdsKillManager の抹消・死亡・AI停止・神無敵の登録リストから強制削除
        boolean found = false;
        if (forceDeadUuids != null) found |= forceDeadUuids.remove(uuid);
        if (removedUuids != null) found |= removedUuids.remove(uuid);
        if (noaiUuids != null) noaiUuids.remove(uuid);
        if (testUuids != null) testUuids.remove(uuid);
        if (imdsInvUuids != null) imdsInvUuids.remove(uuid);

        // 2. ImdsKillManager のユーティリティメソッドで登録を解除
        try {
            if (unmarkMethod != null) unmarkMethod.invoke(null, entity);
            if (unRemoveMethod != null) unRemoveMethod.invoke(null, entity);
            if (removeNoAiMethod != null) removeNoAiMethod.invoke(null, entity);
        } catch (Throwable ignored) {}

        // 3. IEntityImds のフラグを操作：強制死亡/強制削除/神無敵をすべて OFF にする
        if (iEntityImdsClass != null && iEntityImdsClass.isInstance(entity)) {
            try {
                if (setForceDeadMethod != null) setForceDeadMethod.invoke(entity, false);
                if (setForceRemovedMethod != null) setForceRemovedMethod.invoke(entity, false);
                if (setInvulnerableMethod != null) setInvulnerableMethod.invoke(entity, false);
            } catch (Throwable ignored) {}
        }
        return found;
    }
}
