package com.automatavalkyries.protect;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/**
 * Pig2Mod（kakiku / Pig2）の抹殺・追跡リストに対する特化カウンターハンドラー。
 * <p>
 * Pig2 は攻撃した敵エンティティを {@code Pig2.gKilledEnemyUUIDs} に登録し、
 * 各種 Mixin および Xform（{@code MyXformer2}）を通じて、該当 UUID を持つエンティティの存在を
 * サーバー・クライアント双方から強制的に抹消し続ける。
 * このカウンターでは、保護対象エンティティの UUID を {@code gKilledEnemyUUIDs} から解除し、
 * {@code gKilledAllServerEnemies} をリセットする。
 * </p>
 */
public final class EprotectPig2Counter {

    private static boolean initialized = false;
    private static Class<?> pig2Class = null;
    private static Field killedEnemyUuidsField = null;
    private static Field killedAllServerEnemiesField = null;
    private static Method permitKilledEnemyMethod = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            pig2Class = Class.forName("kakiku.pig2mod.entity.Pig2");
            killedEnemyUuidsField = pig2Class.getDeclaredField("gKilledEnemyUUIDs");
            killedEnemyUuidsField.setAccessible(true);
            killedAllServerEnemiesField = pig2Class.getDeclaredField("gKilledAllServerEnemies");
            killedAllServerEnemiesField.setAccessible(true);
            permitKilledEnemyMethod = pig2Class.getDeclaredMethod("permitKilledEnemy", net.minecraft.world.entity.Entity.class);
            permitKilledEnemyMethod.setAccessible(true);
        } catch (Throwable ignored) {
            pig2Class = null;
        }
    }

    /** Pig2Mod の抹殺リストから保護対象を除外する */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) init();
        if (pig2Class == null) return false;

        boolean neutralized = false;
        try {
            UUID uuid = entity.getUUID();
            if (permitKilledEnemyMethod != null) {
                try {
                    permitKilledEnemyMethod.invoke(null, entity);
                } catch (Throwable ignored) {}
            }

            if (killedEnemyUuidsField != null) {
                @SuppressWarnings("unchecked")
                Set<UUID> set = (Set<UUID>) killedEnemyUuidsField.get(null);
                if (set != null && set.remove(uuid)) {
                    neutralized = true;
                }
            }

            if (killedAllServerEnemiesField != null) {
                boolean allKilled = killedAllServerEnemiesField.getBoolean(null);
                if (allKilled) {
                    killedAllServerEnemiesField.setBoolean(null, false);
                    neutralized = true;
                }
            }
        } catch (Throwable ignored) {}

        return neutralized;
    }
}
