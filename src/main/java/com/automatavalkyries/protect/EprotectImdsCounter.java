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
public class EprotectImdsCounter {

    private static boolean initialized = false;
    private static Set<UUID> forceDeadUuids = null;
    private static Set<UUID> removedUuids = null;
    private static Set<UUID> noaiUuids = null;

    private static Class<?> iEntityImdsClass = null;
    private static Method setForceDeadMethod = null;
    private static Method setForceRemovedMethod = null;
    private static Method setInvulnerableMethod = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // 1. ImdsKillManager クラスの静的コレクションを取得（存在する場合）
        try {
            Class<?> killManagerClass = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
            forceDeadUuids = getStaticSet(killManagerClass, "FORCE_DEAD_UUIDS");
            removedUuids = getStaticSet(killManagerClass, "REMOVED_UUIDS");
            noaiUuids = getStaticSet(killManagerClass, "NOAI_UUIDS");
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
     * IMDS-Reborn の即死・抹消処理を常時解除し、IMDS 自身の無敵フラグを奪取して自身を保護する
     */
    public static void counter(LivingEntity entity) {
        if (!initialized) {
            init();
        }

        UUID uuid = entity.getUUID();

        // 1. ImdsKillManager の抹消登録リストから強制削除
        if (forceDeadUuids != null) forceDeadUuids.remove(uuid);
        if (removedUuids != null) removedUuids.remove(uuid);
        if (noaiUuids != null) noaiUuids.remove(uuid);

        // 2. IEntityImds のフラグを操作：IMDSの無敵フラグをONにし、強制死亡/強制削除フラグをOFFにする
        if (iEntityImdsClass != null && iEntityImdsClass.isInstance(entity)) {
            try {
                if (setForceDeadMethod != null) setForceDeadMethod.invoke(entity, false);
                if (setForceRemovedMethod != null) setForceRemovedMethod.invoke(entity, false);
                if (setInvulnerableMethod != null) setInvulnerableMethod.invoke(entity, true);
            } catch (Throwable ignored) {}
        }
    }
}
