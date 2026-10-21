package com.automatavalkyries.util;

import com.automatavalkyries.AutomataValkyries;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
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
 *   <li>remove() を潰した Set / Map（HashSet・HashMap・ConcurrentHashMap の派生）は、keySet().remove と
 *       イテレータの remove（どちらも上書きされた remove() を通らず内部の削除を直接呼ぶ）で外す。1 周だけ回して止まる</li>
 *   <li>エンティティの ID（int）を鍵にする管理表（FastUtil の Int2ObjectMap 等）は、ID を渡されたときだけ鍵の照合もする</li>
 *   <li>{@link #MAX_SIZE} より大きい一覧は触らない（取り違えたときの被害と負荷を抑える）</li>
 * </ul>
 */
public final class StaticReferencePurger {

    public static final int MAX_SIZE = 10_000;
    /** ID を照合しない */
    public static final int NO_ID = Integer.MIN_VALUE;

    private StaticReferencePurger() {}

    /** @return 取り除いた件数 */
    public static int purge(Collection<Class<?>> classes, Set<Object> victims, UUID uuid, String targetName) {
        return purge(classes, victims, uuid, NO_ID, targetName);
    }

    /** エンティティの ID も鍵として照合する版。@return 取り除いた件数 */
    public static int purge(Collection<Class<?>> classes, Set<Object> victims, UUID uuid, int entityId, String targetName) {
        int total = 0;
        for (Class<?> type : classes) {
            Field[] fields = UnsafeHelper.getRealDeclaredFields(type);
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    int removed = 0;
                    if (value instanceof Collection<?> collection) removed = purgeCollection(collection, victims, uuid);
                    else if (value instanceof Map<?, ?> map) removed = purgeMap(map, victims, uuid, entityId);
                    else if (value instanceof Object[] array && array.length <= MAX_SIZE) {
                        for (int i = 0; i < array.length; i++) {
                            if (matches(array[i], victims, uuid)) {
                                array[i] = null;
                                removed++;
                            }
                        }
                    } else if (value instanceof java.util.concurrent.atomic.AtomicReference<?> atomicRef) {
                        if (matches(atomicRef.get(), victims, uuid)) {
                            atomicRef.set(null);
                            removed = 1;
                        }
                    } else if (value instanceof Reference<?> reference) {
                        if (matches(reference.get(), victims, uuid)) {
                            reference.clear();
                            removed = 1;
                        }
                    } else if (matches(value, victims, uuid)) {
                        UnsafeHelper.setStaticFinalField(type, field.getName(),
                                value instanceof java.util.Optional<?> ? java.util.Optional.empty() : null);
                        removed = 1;
                    }
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
                else if (value instanceof Map<?, ?> map) removed = purgeMap(map, victims, uuid, NO_ID);
                total += removed;
            } catch (Throwable ignored) {}
        }
        return total;
    }

    private static int purgeCollection(Collection<?> collection, Set<Object> victims, UUID uuid) {
        if ((collection instanceof ArrayList<?> && collection.getClass() != ArrayList.class)
                || (collection instanceof java.util.concurrent.CopyOnWriteArrayList<?>
                        && collection.getClass() != java.util.concurrent.CopyOnWriteArrayList.class)
                || collection instanceof java.util.concurrent.CopyOnWriteArraySet<?>) {
            return UnsafeHelper.removeFromArrayList(collection, e -> matches(e, victims, uuid));
        }
        int size;
        Object[] snapshot;
        try {
            size = collection.size();
            if (size > MAX_SIZE) return 0;
            snapshot = collection.toArray();
        } catch (Throwable denied) {
            return UnsafeHelper.removeFromArrayList(collection, e -> matches(e, victims, uuid));
        }
        List<Object> matches = new ArrayList<>();
        for (Object element : snapshot) {
            if (matches(element, victims, uuid)) matches.add(element);
        }
        if (matches.isEmpty()) return 0;
        int removed = 0;
        for (Object element : matches) {
            try {
                for (int i = 0; i < 4 && collection.remove(element); i++) removed++;
            } catch (Throwable ignored) {}
        }
        if (removed < matches.size() && collection instanceof java.util.List<?>) {
            removed += UnsafeHelper.removeFromArrayList(collection, e -> matches(e, victims, uuid));
        } else if (removed < matches.size() && collection instanceof Set<?>) {
            removed += removeByIterator(collection.iterator(), e -> matches(e, victims, uuid), size);
        }
        return removed;
    }

    private static int purgeMap(Map<?, ?> map, Set<Object> victims, UUID uuid, int entityId) {
        if (map.size() > MAX_SIZE) return 0;
        boolean intKeyed = entityId != NO_ID && map.getClass().getName().startsWith("it.unimi.dsi.fastutil.ints.Int2");
        int removed = 0;
        boolean refused = false;
        for (Map.Entry<?, ?> entry : new ArrayList<>(map.entrySet())) {
            Object key = entry.getKey();
            if (!entryMatches(key, entry.getValue(), victims, uuid, entityId, intKeyed)) continue;
            map.remove(key);
            if (!map.containsKey(key)) {
                removed++;
                continue;
            }
            // remove() を潰した派生: keySet().remove は HashMap / ConcurrentHashMap の内部削除を直接呼ぶ
            try {
                map.keySet().remove(key);
            } catch (Throwable ignored) {}
            if (!map.containsKey(key)) removed++;
            else refused = true;
        }
        if (refused) {
            removed += removeByIterator(map.entrySet().iterator(),
                    e -> e instanceof Map.Entry<?, ?> en && entryMatches(en.getKey(), en.getValue(), victims, uuid, entityId, intKeyed),
                    map.size());
        }
        return removed;
    }

    /** イテレータで 1 周だけ外す（同じ要素を返し続ける一覧でも size 回で止まる） */
    private static int removeByIterator(Iterator<?> iterator, java.util.function.Predicate<Object> shouldRemove, int limit) {
        int removed = 0;
        try {
            for (int i = 0; i <= limit && iterator.hasNext(); i++) {
                if (shouldRemove.test(iterator.next())) {
                    iterator.remove();
                    removed++;
                }
            }
        } catch (Throwable ignored) {}
        return removed;
    }

    private static boolean entryMatches(Object key, Object value, Set<Object> victims, UUID uuid, int entityId, boolean intKeyed) {
        if (matches(key, victims, uuid) || matches(value, victims, uuid)) return true;
        // ID を鍵にする表: 値がエンティティか、Int2* の管理表のときだけ（無関係な数値の表を取り違えない）
        return entityId != NO_ID && key instanceof Integer id && id == entityId
                && (intKeyed || value instanceof net.minecraft.world.entity.Entity);
    }

    private static boolean matches(Object element, Set<Object> victims, UUID uuid) {
        if (element == null) return false;
        if (victims.contains(element) || (uuid != null && uuid.equals(element))) return true;
        if (element instanceof java.util.Optional<?> optional) {
            return optional.isPresent() && (victims.contains(optional.get()) || (uuid != null && uuid.equals(optional.get())));
        }
        if (element instanceof java.util.concurrent.atomic.AtomicReference<?> atomicRef) {
            Object inner = atomicRef.get();
            return inner != null && (victims.contains(inner) || (uuid != null && uuid.equals(inner)));
        }
        if (element instanceof Reference<?> reference) {
            Object inner = reference.get();
            return inner != null && (victims.contains(inner) || (uuid != null && uuid.equals(inner)));
        }
        return false;
    }
}
