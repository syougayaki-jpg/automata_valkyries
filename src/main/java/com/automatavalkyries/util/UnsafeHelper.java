package com.automatavalkyries.util;

import com.automatavalkyries.AutomataValkyries;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 低レベルメモリ操作及びリフレクション制限バイパス用ユーティリティ。
 * 他 MOD（Omni-Mobs, Witherzilla 等）の StackChecker や保護リストをバイパスして
 * メモリレベルでの確実な消去・修復を行う。
 */
public final class UnsafeHelper {

    private static final Unsafe THE_UNSAFE;

    static {
        Unsafe unsafe = null;
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (Throwable t) {
            try {
                Field[] fields = Unsafe.class.getDeclaredFields();
                for (Field f : fields) {
                    if (f.getType() == Unsafe.class) {
                        f.setAccessible(true);
                        unsafe = (Unsafe) f.get(null);
                        break;
                    }
                }
            } catch (Throwable t2) {
                AutomataValkyries.LOGGER.error("[UnsafeHelper] Failed to acquire sun.misc.Unsafe instance", t2);
            }
        }
        THE_UNSAFE = unsafe;
    }

    private UnsafeHelper() {}

    /**
     * そのクラスの初期化（static 初期化子）が済んでいるか。済んでいないクラスの static を読むと初期化が走ってしまうので、
     * 他 MOD のクラスを覗く前に確かめる。判定できなければ false（覗かない側に倒す）。
     */
    public static boolean isInitialized(Class<?> type) {
        if (THE_UNSAFE == null) return false;
        try {
            java.lang.reflect.Method method = Unsafe.class.getMethod("shouldBeInitialized", Class.class);
            return !(Boolean) method.invoke(THE_UNSAFE, type);
        } catch (Throwable t) {
            return false;
        }
    }

    public static Unsafe getUnsafe() {
        return THE_UNSAFE;
    }

    public static boolean isAvailable() {
        return THE_UNSAFE != null;
    }

    public static long objectFieldOffset(Field field) {
        if (THE_UNSAFE == null || field == null) return -1L;
        try {
            return THE_UNSAFE.objectFieldOffset(field);
        } catch (Throwable t) {
            return -1L;
        }
    }

    public static Field findField(Class<?> clazz, String... candidateNames) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (String name : candidateNames) {
                try {
                    Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static Object getObject(Object target, long offset) {
        if (THE_UNSAFE == null || target == null || offset < 0) return null;
        return THE_UNSAFE.getObject(target, offset);
    }

    public static void putObject(Object target, long offset, Object value) {
        if (THE_UNSAFE != null && target != null && offset >= 0) {
            THE_UNSAFE.putObject(target, offset, value);
        }
    }

    public static int getInt(Object target, long offset) {
        if (THE_UNSAFE == null || target == null || offset < 0) return 0;
        return THE_UNSAFE.getInt(target, offset);
    }

    public static void putInt(Object target, long offset, int value) {
        if (THE_UNSAFE != null && target != null && offset >= 0) {
            THE_UNSAFE.putInt(target, offset, value);
        }
    }

    public static boolean getBoolean(Object target, long offset) {
        if (THE_UNSAFE == null || target == null || offset < 0) return false;
        return THE_UNSAFE.getBoolean(target, offset);
    }

    public static void putBoolean(Object target, long offset, boolean value) {
        if (THE_UNSAFE != null && target != null && offset >= 0) {
            THE_UNSAFE.putBoolean(target, offset, value);
        }
    }

    /**
     * 削除を拒否する ArrayList（Omni-Mobs の ProtectedArrayList など）から、条件に合う要素を内部配列ごと詰めて取り除く。
     * <p>
     * 拒否する実装に {@code Iterator.remove()} を使ってはいけない: 内部の {@code remove(int)} が何もせずに戻ると、
     * イテレータは位置を巻き戻して同じ要素を返し続け、無限ループになる。
     * ArrayList のオフセットで書くので、ArrayList 以外には書かない（メモリ破壊になる）。
     * </p>
     * @return 取り除いた数（ArrayList でない・Unsafe が使えないときは 0）
     */
    public static int removeFromArrayList(Object list, java.util.function.Predicate<Object> shouldRemove) {
        if (!(list instanceof java.util.ArrayList<?>) || THE_UNSAFE == null) return 0;
        try {
            long sizeOffset = objectFieldOffset(java.util.ArrayList.class.getDeclaredField("size"));
            long dataOffset = objectFieldOffset(java.util.ArrayList.class.getDeclaredField("elementData"));
            if (sizeOffset < 0 || dataOffset < 0) return 0;
            if (!(getObject(list, dataOffset) instanceof Object[] data)) return 0;
            int size = Math.min(getInt(list, sizeOffset), data.length);
            int kept = 0;
            for (int i = 0; i < size; i++) {
                if (!shouldRemove.test(data[i])) data[kept++] = data[i];
            }
            java.util.Arrays.fill(data, kept, size, null); // 参照を残すと GC されない
            putInt(list, sizeOffset, kept);
            return size - kept;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setFieldValue(Object target, String fieldName, Object value) {
        if (target == null) return;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f != null) {
                if (THE_UNSAFE != null && !Modifier.isStatic(f.getModifiers())) {
                    long offset = objectFieldOffset(f);
                    if (offset >= 0) {
                        if (f.getType() == boolean.class) {
                            putBoolean(target, offset, (Boolean) value);
                        } else if (f.getType() == int.class) {
                            putInt(target, offset, (Integer) value);
                        } else {
                            putObject(target, offset, value);
                        }
                        return;
                    }
                }
                f.setAccessible(true);
                f.set(target, value);
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[UnsafeHelper] Failed to set field {} on {}", fieldName, target, t);
        }
    }

    public static void setStaticFinalField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field f = findField(clazz, fieldName);
            if (f != null) {
                f.setAccessible(true);
                if (THE_UNSAFE != null) {
                    Object base = THE_UNSAFE.staticFieldBase(f);
                    long offset = THE_UNSAFE.staticFieldOffset(f);
                    THE_UNSAFE.putObject(base, offset, value);
                    return;
                }
                f.set(null, value);
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[UnsafeHelper] Failed to set static field {} on {}", fieldName, clazz, t);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStaticField(Class<?> clazz, String fieldName) {
        try {
            Field f = findField(clazz, fieldName);
            if (f != null) {
                f.setAccessible(true);
                return (T) f.get(null);
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[UnsafeHelper] Failed to get static field {} on {}", fieldName, clazz, t);
        }
        return null;
    }
}
