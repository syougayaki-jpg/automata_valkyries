package com.automatavalkyries.protect;

import net.minecraft.network.syncher.EntityDataAccessor;
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

        // 1. Novel 関連のリフレクション取得
        try {
            Class<?> entityMixinClass = Class.forName("com.sakurafuld.hyperdaimc.mixin.novel.EntityMixin");
            Field dataNovelizedField = entityMixinClass.getDeclaredField("DATA_NOVELIZED");
            dataNovelizedField.setAccessible(true);
            //noinspection unchecked
            dataNovelized = (EntityDataAccessor<Boolean>) dataNovelizedField.get(null);
        } catch (Throwable ignored) {}

        try {
            Class<?> livingNovelMixinClass = Class.forName("com.sakurafuld.hyperdaimc.mixin.novel.LivingEntityMixin");
            novelDeadField = livingNovelMixinClass.getDeclaredField("dead");
            novelDeadField.setAccessible(true);
        } catch (Throwable ignored) {}

        // 2. Muteki 関連のリフレクション取得
        try {
            Class<?> iLivingEntityMutekiClass = Class.forName("com.sakurafuld.hyperdaimc.api.mixin.ILivingEntityMuteki");
            Class<?> livingMutekiMixinClass = Class.forName("com.sakurafuld.hyperdaimc.mixin.muteki.LivingEntityMixin");

            mutekiField = livingMutekiMixinClass.getDeclaredField("muteki");
            mutekiField.setAccessible(true);

            forcedField = livingMutekiMixinClass.getDeclaredField("forced");
            forcedField.setAccessible(true);

            mutekiForceMethod = iLivingEntityMutekiClass.getMethod("mutekiForce", boolean.class);
        } catch (Throwable ignored) {}
    }

    public static boolean isNovelizedAccessor(EntityDataAccessor<?> accessor) {
        if (!initialized) init();
        return dataNovelized != null && dataNovelized.equals(accessor);
    }

    /**
     * Hyperlink のノベル化・即死フラグを常時中和する
     */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) {
            init();
        }

        // 1. Novelize の解除：SynchedEntityData の DATA_NOVELIZED を false に上書き
        if (dataNovelized != null) {
            try {
                entity.getEntityData().set(dataNovelized, false);
            } catch (Throwable ignored) {}
        }

        // 2. 死亡Tick進行カウンタ（dead）を 0 にリセット
        if (novelDeadField != null) {
            try {
                novelDeadField.setInt(entity, 0);
            } catch (Throwable ignored) {}
        }

        // 3. Hyperlink の muteki フラグをクリア（muteki が立っていると SynchedEntityData でダメージの HP 減少が cancel されてしまうため）
        if (mutekiField != null) {
            try {
                mutekiField.setBoolean(entity, false);
                if (forcedField != null) forcedField.setBoolean(entity, false);
                if (mutekiForceMethod != null) mutekiForceMethod.invoke(entity, false);
            } catch (Throwable ignored) {}
        }
        // Hyperlink のフラグは読み出しを eprotect 側で偽装しているため、解除の要否は判定できない
        return false;
    }
}
