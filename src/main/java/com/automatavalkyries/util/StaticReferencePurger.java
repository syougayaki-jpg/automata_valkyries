package com.automatavalkyries.util;

import com.automatavalkyries.AutomataValkyries;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 指定したクラスの static な Collection / Map から、特定のオブジェクト（と UUID）だけを取り除く。
 * 超絶抹殺の棒（対象の復活待ちの一覧の掃除）と eprotect（攻撃した MOD のクラスが持つ抹殺予定の一覧の掃除）で共用する。
 * <ul>
 *   <li>1 回ずつ remove する（Iterator.remove は、削除を拒否する一覧で同じ要素を返し続けて止まらなくなる）</li>
 *   <li>削除を拒否する ArrayList（Omni-Mobs の ProtectedArrayList 等）だけは内部配列を詰める</li>
 *   <li>{@link #MAX_SIZE} より大きい一覧は触らない（取り違えたときの被害と負荷を抑える）</li>
 * </ul>
 */
public final class StaticReferencePurger {

    public static final int MAX_SIZE = 10_000;

    private StaticReferencePurger() {}

    /** @return 取り除いた件数 */
    public static int purge(Collection<Class<?>> classes, Set<Object> victims, UUID uuid, String targetName) {
        int total = 0;
        for (Class<?> type : classes) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    int removed = 0;
                    if (value instanceof Collection<?> collection) removed = purgeCollection(collection, victims, uuid);
                    else if (value instanceof Map<?, ?> map) removed = purgeMap(map, victims, uuid);
                    if (removed > 0) {
                        total += removed;
                        AutomataValkyries.LOGGER.info("[eprotect] Removed {} reference(s) to {} from {}.{}", removed, targetName,
                                type.getSimpleName(), field.getName());
                    }
                } catch (Throwable ignored) {}
            }
        }
        return total;
    }

    /** 指定した static フィールドだけから取り除く（覚えておいた抹殺予定の一覧を毎 Tick 掃除する用。ログは呼び出し側が出す）。@return 取り除いた件数 */
    public static int purgeFields(Collection<Field> fields, Set<Object> victims, UUID uuid, String targetName) {
        int total = 0;
        for (Field field : fields) {
            try {
                Object value = field.get(null);
                int removed = 0;
                if (value instanceof Collection<?> collection) removed = purgeCollection(collection, victims, uuid);
                else if (value instanceof Map<?, ?> map) removed = purgeMap(map, victims, uuid);
                total += removed;
            } catch (Throwable ignored) {}
        }
        return total;
    }

    private static int purgeCollection(Collection<?> collection, Set<Object> victims, UUID uuid) {
        if (collection.size() > MAX_SIZE) return 0;
        List<Object> matches = new ArrayList<>();
        for (Object element : collection.toArray()) {
            if (matches(element, victims, uuid)) matches.add(element);
        }
        if (matches.isEmpty()) return 0;
        int removed = 0;
        for (Object element : matches) {
            for (int i = 0; i < 4 && collection.remove(element); i++) removed++;
        }
        if (removed < matches.size()) removed += UnsafeHelper.removeFromArrayList(collection, e -> matches(e, victims, uuid));
        return removed;
    }

    private static int purgeMap(Map<?, ?> map, Set<Object> victims, UUID uuid) {
        if (map.size() > MAX_SIZE) return 0;
        int removed = 0;
        for (Map.Entry<?, ?> entry : new ArrayList<>(map.entrySet())) {
            if (matches(entry.getKey(), victims, uuid) || matches(entry.getValue(), victims, uuid)) {
                map.remove(entry.getKey());
                if (!map.containsKey(entry.getKey())) removed++;
            }
        }
        return removed;
    }

    private static boolean matches(Object element, Set<Object> victims, UUID uuid) {
        if (element == null) return false;
        if (victims.contains(element) || uuid.equals(element)) return true;
        return element instanceof Reference<?> reference && victims.contains(reference.get());
    }
}
