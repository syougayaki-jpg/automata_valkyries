package com.automatavalkyries.protect;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ハイパーリンク（Hyperlink / hyperdaimc）のノベル化（Novelize）・即死・消去・Tick停止に対する特化カウンターハンドラー。
 */
public final class EprotectHyperlinkCounter {

    private static boolean initialized = false;

    // Hyperlink Novel 関連
    private static EntityDataAccessor<Boolean> dataNovelized = null;
    private static Field novelDeadField = null;

    // Hyperlink Muteki 関連
    private static Field mutekiField = null;
    private static Field forcedField = null;
    private static Method mutekiForceMethod = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // 1. Novel 関連のリフレクション取得: DATA_NOVELIZED は Entity.class に注入される
        try {
            Field dataNovelizedField = net.minecraft.world.entity.Entity.class.getDeclaredField("DATA_NOVELIZED");
            dataNovelizedField.setAccessible(true);
            //noinspection unchecked
            dataNovelized = (EntityDataAccessor<Boolean>) dataNovelizedField.get(null);
        } catch (Throwable ignored) {}

        // 2. 死亡Tickカウンタ（dead: int）および muteki/forced は LivingEntity.class に注入される
        try {
            for (Field f : net.minecraft.world.entity.LivingEntity.class.getDeclaredFields()) {
                if ("dead".equals(f.getName()) && f.getType() == int.class) {
                    f.setAccessible(true);
                    novelDeadField = f;
                } else if ("muteki".equals(f.getName()) && f.getType() == boolean.class) {
                    f.setAccessible(true);
                    mutekiField = f;
                } else if ("forced".equals(f.getName()) && f.getType() == boolean.class) {
                    f.setAccessible(true);
                    forcedField = f;
                }
            }
        } catch (Throwable ignored) {}

        // 3. MutekiForceMethod (ILivingEntityMuteki API インターフェース)
        try {
            Class<?> iLivingEntityMutekiClass = Class.forName("com.sakurafuld.hyperdaimc.api.mixin.ILivingEntityMuteki");
            mutekiForceMethod = iLivingEntityMutekiClass.getMethod("mutekiForce", boolean.class);
        } catch (Throwable ignored) {}
    }

    public static boolean isNovelizedAccessor(EntityDataAccessor<?> accessor) {
        if (!initialized) init();
        return dataNovelized != null && dataNovelized.equals(accessor);
    }

    /**
     * エンティティが現在実際に小説化フラグ（DATA_NOVELIZED）を持っているか安全に判定する
     */
    public static boolean isNovelized(Entity entity) {
        if (entity == null) return false;
        if (!initialized) init();
        if (dataNovelized != null) {
            try {
                return Boolean.TRUE.equals(entity.getEntityData().get(dataNovelized));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /**
     * Hyperlink のノベル化・即死フラグを常時中和する
     */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) {
            init();
        }
        boolean neutralized = false;

        // 1. Novelize の解除：SynchedEntityData の DATA_NOVELIZED を false に上書き
        if (dataNovelized != null) {
            try {
                if (Boolean.TRUE.equals(entity.getEntityData().get(dataNovelized))) {
                    entity.getEntityData().set(dataNovelized, false);
                    neutralized = true;
                }
            } catch (Throwable ignored) {}
        }

        // 2. 死亡Tick進行カウンタ（dead）を 0 にリセット
        if (novelDeadField != null) {
            try {
                if (novelDeadField.getInt(entity) != 0) {
                    novelDeadField.setInt(entity, 0);
                    neutralized = true;
                }
            } catch (Throwable ignored) {}
        }

        // 3. Hyperlink の muteki フラグをクリア
        if (mutekiField != null) {
            try {
                if (mutekiField.getBoolean(entity)) {
                    mutekiField.setBoolean(entity, false);
                    neutralized = true;
                }
                if (forcedField != null) forcedField.setBoolean(entity, false);
                if (mutekiForceMethod != null) mutekiForceMethod.invoke(entity, false);
            } catch (Throwable ignored) {}
        }

        // 4. NBT タグ（hyperdaimc:NovelDead / hyperdaimc:Novelized）の除去
        if (entity.getPersistentData().contains("hyperdaimc:NovelDead")) {
            entity.getPersistentData().remove("hyperdaimc:NovelDead");
            neutralized = true;
        }
        if (entity.getPersistentData().contains("hyperdaimc:Novelized")) {
            entity.getPersistentData().remove("hyperdaimc:Novelized");
            neutralized = true;
        }

        return neutralized;
    }
}
