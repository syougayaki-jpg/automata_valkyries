package com.automatavalkyries.protect;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

/**
 * Omni-Mobs（Metapotent Flashfur / EntityRemover 等）の即死・負数 HP 加算（healthDelta）
 * および遅延物理消去（RemoveDeadEntities）に対する特化カウンターハンドラー。
 * <p>
 * Omni-Mobs は Java エージェントで全 LivingEntity の {@code getHealth()} に
 * {@code + healthDelta} を注入し、負数を書き込むことで強制マイナス HP（死亡判定）を維持する。
 * また {@code RemoveDeadEntities} で 50 Tick 後に強制消去をスケジュールする。
 * これらを検知・中和して通常状態を維持する。
 * </p>
 */
public final class EprotectOmniMobsCounter {

    private static boolean initialized = false;
    private static EntityDataAccessor<Float> healthDeltaAccessor = null;
    private static Method setHealthDeltaMethod = null;
    private static List<?> entitiesToRemoveList = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> utilClass = Class.forName("flashfur.omnimobs.util.GetHealthModifyUtil");
            Field accessorField = utilClass.getDeclaredField("DATA_MODIFY_GET_HEALTH_DELTA");
            accessorField.setAccessible(true);
            //noinspection unchecked
            healthDeltaAccessor = (EntityDataAccessor<Float>) accessorField.get(null);

            setHealthDeltaMethod = utilClass.getDeclaredMethod("setHealthDelta", LivingEntity.class, float.class);
            setHealthDeltaMethod.setAccessible(true);
        } catch (Throwable ignored) {
            healthDeltaAccessor = null;
            setHealthDeltaMethod = null;
        }

        try {
            Class<?> removeClass = Class.forName("flashfur.omnimobs.entities.base.RemoveDeadEntities");
            Field listField = removeClass.getDeclaredField("ENTITIES_TO_REMOVE");
            listField.setAccessible(true);
            entitiesToRemoveList = (List<?>) listField.get(null);
        } catch (Throwable ignored) {
            entitiesToRemoveList = null;
        }
    }

    /**
     * Omni-Mobs による負数 HP 改変や消去スケジュールを解除する。
     * 異常が検知され解除された場合は true を返す。
     */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) init();

        boolean neutralized = false;

        // 1. healthDelta の解除
        if (setHealthDeltaMethod != null) {
            try {
                if (healthDeltaAccessor != null) {
                    Float currentDelta = entity.getEntityData().get(healthDeltaAccessor);
                    if (currentDelta != null && (currentDelta < 0.0F || !Float.isFinite(currentDelta))) {
                        setHealthDeltaMethod.invoke(null, entity, 0.0F);
                        neutralized = true;
                    }
                } else {
                    setHealthDeltaMethod.invoke(null, entity, 0.0F);
                }
            } catch (Throwable ignored) {}
        }

        // 2. SynchedEntityData 内の delta アクセサ直接リセット（フォールバック）
        SynchedEntityData data = entity.getEntityData();
        if (healthDeltaAccessor != null) {
            try {
                Float delta = data.get(healthDeltaAccessor);
                if (delta != null && (delta < 0.0F || !Float.isFinite(delta))) {
                    data.set(healthDeltaAccessor, 0.0F);
                    neutralized = true;
                }
            } catch (Throwable ignored) {}
        }

        // 3. RemoveDeadEntities からの登録解除 & NBT タイマー消去
        if (entity.getPersistentData().contains("omnimobs_flashfur_remove_dead_entities_tick")) {
            entity.getPersistentData().remove("omnimobs_flashfur_remove_dead_entities_tick");
            neutralized = true;
        }

        if (entitiesToRemoveList != null && !entitiesToRemoveList.isEmpty()) {
            synchronized (entitiesToRemoveList) {
                try {
                    Iterator<?> it = entitiesToRemoveList.iterator();
                    while (it.hasNext()) {
                        Object obj = it.next();
                        if (obj instanceof WeakReference<?> ref) {
                            if (ref.get() == entity || ref.get() == null) {
                                it.remove();
                                neutralized = true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        return neutralized;
    }
}
