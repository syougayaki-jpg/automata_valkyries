package com.automatavalkyries.protect;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Nekohashizuku Blade（猫雫刀 / AgentHealthTransformer）による HP 改ざんに対する特化カウンターハンドラー。
 * <p>
 * Nekohashizuku Blade の Java エージェントは、{@code LivingEntity.getHealth()} の呼び出しを横取りし、
 * {@code AgentHealthMethodHelper.HEALTH_MAP} に対象エンティティが登録されている場合、
 * そこに設定された任意の float 値（即死用には 0.0F、ボス用には全快値）を強制返却させる。
 * このカウンターでは、保護対象エンティティが {@code HEALTH_MAP} に存在する場合に即座に除去し、
 * 正当な HP 値の取得を復元する。
 * </p>
 */
public final class EprotectNekohashizukuCounter {

    private static boolean initialized = false;
    private static Class<?> helperClass = null;
    private static Field healthMapField = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            helperClass = Class.forName("com.buwan.shizuku.core.AgentHealthMethodHelper");
            healthMapField = helperClass.getDeclaredField("HEALTH_MAP");
            healthMapField.setAccessible(true);
        } catch (Throwable ignored) {
            helperClass = null;
        }
    }

    /** Nekohashizuku Blade の HEALTH_MAP から保護対象を除去する */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) init();
        if (helperClass == null || healthMapField == null) return false;

        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> healthMap = (Map<Object, Object>) healthMapField.get(null);
            if (healthMap != null && healthMap.containsKey(entity)) {
                healthMap.remove(entity);
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
