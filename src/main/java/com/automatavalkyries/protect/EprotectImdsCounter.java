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

    private static Method markForInvMethod = null;
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

            markForInvMethod = killManagerClass.getMethod("markForInv", LivingEntity.class);
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
     * IMDS-Reborn の即死・抹消処理を常時解除し、IMDS 自身の神無敵フラグを奪取して自身を保護する
     */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) {
            init();
        }

        UUID uuid = entity.getUUID();

        // 1. ImdsKillManager の抹消・死亡・AI停止登録リストから強制削除
        boolean found = false;
        if (forceDeadUuids != null) found |= forceDeadUuids.remove(uuid);
        if (removedUuids != null) found |= removedUuids.remove(uuid);
        if (noaiUuids != null) noaiUuids.remove(uuid);
        if (testUuids != null) testUuids.remove(uuid);

        // 2. ImdsKillManager の神無敵リスト（IMDS_INV_UUIDS）に自身を登録！
        if (imdsInvUuids != null) imdsInvUuids.add(uuid);

        // 3. ImdsKillManager のユーティリティメソッドを呼び出し
        try {
            if (markForInvMethod != null) markForInvMethod.invoke(null, entity);
            if (unmarkMethod != null) unmarkMethod.invoke(null, entity);
            if (unRemoveMethod != null) unRemoveMethod.invoke(null, entity);
            if (removeNoAiMethod != null) removeNoAiMethod.invoke(null, entity);
        } catch (Throwable ignored) {}

        // 4. IEntityImds のフラグを操作：IMDSの神無敵フラグをONにし、強制死亡/強制削除フラグをOFFにする
        if (iEntityImdsClass != null && iEntityImdsClass.isInstance(entity)) {
            try {
                if (setForceDeadMethod != null) setForceDeadMethod.invoke(entity, false);
                if (setForceRemovedMethod != null) setForceRemovedMethod.invoke(entity, false);
                if (setInvulnerableMethod != null) setInvulnerableMethod.invoke(entity, true);
            } catch (Throwable ignored) {}
        }
        return found;
    }
}
