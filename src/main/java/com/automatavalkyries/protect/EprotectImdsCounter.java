package com.automatavalkyries.protect;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.UUID;

/**
 * インフィニティマタードミネーターソードリボーン（Infinity Matter Dominator Sword: Reborn / IMDS-Reborn）
 * の即死・抹消キル機構および神無敵フックに対する特化カウンターハンドラー。
 */
public final class EprotectImdsCounter {

    private static boolean initialized = false;
    private static Set<UUID> forceDeadUuids = null;
    private static Set<UUID> removedUuids = null;
    private static Set<UUID> noaiUuids = null;
    private static Set<UUID> testUuids = null;
    private static Set<UUID> imdsInvUuids = null;
    private static Set<UUID> keepInvUuids = null;

    private static Method unmarkMethod = null;
    private static Method unRemoveMethod = null;
    private static Method removeNoAiMethod = null;
    private static Method unmarkInvMethod = null;
    private static Method unsetKiMethod = null;

    private static Class<?> iEntityImdsClass = null;
    private static Method setForceDeadMethod = null;
    private static Method setForceRemovedMethod = null;
    private static Method setInvulnerableMethod = null;
    private static Method isInvulnerableMethod = null;

    private static volatile EntityDataAccessor<Boolean> imdsInvulAccessor = null;

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
            keepInvUuids = getStaticSet(killManagerClass, "KEEPINV_UUIDS");

            unmarkMethod = killManagerClass.getMethod("unmark", LivingEntity.class);
            unRemoveMethod = killManagerClass.getMethod("unRemove", Entity.class);
            removeNoAiMethod = killManagerClass.getMethod("removeNoAi", LivingEntity.class);
            try {
                unmarkInvMethod = killManagerClass.getMethod("unmarkInv", LivingEntity.class);
            } catch (Throwable ignored) {}
            try {
                unsetKiMethod = killManagerClass.getMethod("unsetKI", Player.class);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        // 2. IEntityImds インターフェースのメソッドを取得（存在する場合）
        try {
            iEntityImdsClass = Class.forName("net.mcreator.imds_reborn.IEntityImds");
            setForceDeadMethod = iEntityImdsClass.getMethod("imds$setForceDead", boolean.class);
            setForceRemovedMethod = iEntityImdsClass.getMethod("imds$setForceRemoved", boolean.class);
            setInvulnerableMethod = iEntityImdsClass.getMethod("imds$setInvulnerable", boolean.class);
            isInvulnerableMethod = iEntityImdsClass.getMethod("imds$isInvulnerable");
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

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Boolean> getImdsInvulnerableAccessor() {
        if (imdsInvulAccessor != null) {
            return imdsInvulAccessor;
        }
        try {
            for (Field field : Entity.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        && EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                    String name = field.getName();
                    if (name.contains("IMDS_INVULNERABLE")) {
                        field.setAccessible(true);
                        Object val = field.get(null);
                        if (val instanceof EntityDataAccessor<?> acc) {
                            imdsInvulAccessor = (EntityDataAccessor<Boolean>) acc;
                            return imdsInvulAccessor;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 対象が IMDS の神無敵（IMDS_INV_UUIDS または imds$isInvulnerable）状態にあるか判定する。
     */
    public static boolean isImdsProtected(LivingEntity entity) {
        if (entity == null) return false;
        if (!initialized) {
            init();
        }
        try {
            if (imdsInvUuids != null && imdsInvUuids.contains(entity.getUUID())) {
                return true;
            }
            if (iEntityImdsClass != null && iEntityImdsClass.isInstance(entity) && isInvulnerableMethod != null) {
                Object res = isInvulnerableMethod.invoke(entity);
                if (Boolean.TRUE.equals(res)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * IMDS-Reborn の即死・抹消処理および神無敵フックを常時解除する。
     */
    public static boolean counter(LivingEntity entity) {
        if (entity == null) return false;
        if (!initialized) {
            init();
        }

        UUID uuid = entity.getUUID();

        // 1. ImdsKillManager の抹消・死亡・AI停止・神無敵・インベントリ保持の登録リストから強制削除
        boolean found = false;
        if (forceDeadUuids != null) found |= forceDeadUuids.remove(uuid);
        if (removedUuids != null) found |= removedUuids.remove(uuid);
        if (noaiUuids != null) noaiUuids.remove(uuid);
        if (testUuids != null) testUuids.remove(uuid);
        if (imdsInvUuids != null) imdsInvUuids.remove(uuid);
        if (keepInvUuids != null) keepInvUuids.remove(uuid);

        // 2. ImdsKillManager のユーティリティメソッドで登録を解除
        try {
            if (unmarkMethod != null) unmarkMethod.invoke(null, entity);
            if (unRemoveMethod != null) unRemoveMethod.invoke(null, entity);
            if (removeNoAiMethod != null) removeNoAiMethod.invoke(null, entity);
            if (unmarkInvMethod != null) unmarkInvMethod.invoke(null, entity);
            if (unsetKiMethod != null && entity instanceof Player p) unsetKiMethod.invoke(null, p);
        } catch (Throwable ignored) {}

        // 3. IEntityImds のフラグを操作：強制死亡/強制削除/神無敵をすべて OFF にする
        if (iEntityImdsClass != null && iEntityImdsClass.isInstance(entity)) {
            try {
                if (setForceDeadMethod != null) setForceDeadMethod.invoke(entity, false);
                if (setForceRemovedMethod != null) setForceRemovedMethod.invoke(entity, false);
                if (setInvulnerableMethod != null) setInvulnerableMethod.invoke(entity, false);
            } catch (Throwable ignored) {}
        }

        // 4. SynchedEntityData の imds$IMDS_INVULNERABLE と PersistentData も確実にクリア
        try {
            var acc = getImdsInvulnerableAccessor();
            if (acc != null) {
                entity.getEntityData().set(acc, false);
            }
        } catch (Throwable ignored) {}
        try {
            entity.getPersistentData().remove("imds_reborn:isImdsInvulnerable");
        } catch (Throwable ignored) {}

        return found;
    }
}
