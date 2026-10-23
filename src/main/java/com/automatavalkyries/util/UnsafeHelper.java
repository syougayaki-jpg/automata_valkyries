package com.automatavalkyries.util;

import com.automatavalkyries.AutomataValkyries;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * 低レベルメモリ操作及びリフレクション制限バイパス用ユーティリティ。
 * 他 MOD（Omni-Mobs, Witherzilla 等）の StackChecker や保護リストをバイパスして
 * メモリレベルでの確実な消去・修復を行う。
 */
public final class UnsafeHelper {

    private static final Unsafe THE_UNSAFE;
    private static final java.lang.invoke.MethodHandle INTERNAL_OBJECT_FIELD_OFFSET;
    private static final java.lang.invoke.MethodHandle GET_DECLARED_FIELDS_0;
    private static final java.lang.invoke.MethodHandle GET_DECLARED_METHODS_0;
    private static final long REFLECTION_DATA_OFFSET;

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
        java.lang.invoke.MethodHandle internalOffset = null;
        java.lang.invoke.MethodHandle getFields0 = null;
        java.lang.invoke.MethodHandle getMethods0 = null;
        long reflectionDataOffset = -1L;
        if (unsafe != null) {
            try {
                Field lookupField = java.lang.invoke.MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                var trusted = (java.lang.invoke.MethodHandles.Lookup) unsafe.getObject(
                        unsafe.staticFieldBase(lookupField), unsafe.staticFieldOffset(lookupField));
                Field internalField = Unsafe.class.getDeclaredField("theInternalUnsafe");
                Object internalUnsafe = unsafe.getObject(
                        unsafe.staticFieldBase(internalField), unsafe.staticFieldOffset(internalField));
                internalOffset = trusted.findVirtual(internalUnsafe.getClass(), "objectFieldOffset",
                        java.lang.invoke.MethodType.methodType(long.class, Field.class)).bindTo(internalUnsafe);
                getFields0 = trusted.findVirtual(Class.class, "getDeclaredFields0",
                        java.lang.invoke.MethodType.methodType(Field[].class, boolean.class));
                getMethods0 = trusted.findVirtual(Class.class, "getDeclaredMethods0",
                        java.lang.invoke.MethodType.methodType(Method[].class, boolean.class));
                Field[] classFields = (Field[]) getFields0.invokeExact(Class.class, false);
                for (Field cf : classFields) {
                    if ("reflectionData".equals(cf.getName())) {
                        reflectionDataOffset = (long) internalOffset.invokeExact(cf);
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }
        INTERNAL_OBJECT_FIELD_OFFSET = internalOffset;
        GET_DECLARED_FIELDS_0 = getFields0;
        GET_DECLARED_METHODS_0 = getMethods0;
        REFLECTION_DATA_OFFSET = reflectionDataOffset;
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
            if (INTERNAL_OBJECT_FIELD_OFFSET != null) {
                try {
                    return (long) INTERNAL_OBJECT_FIELD_OFFSET.invokeExact(field);
                } catch (Throwable ignored) {}
            }
            return -1L;
        }
    }

    /**
     * 他 MOD（SuperSteve の PLZBase.antiRefGetMF 等）が {@code Class.reflectionData} のキャッシュ配列を
     * 長さ 0 の空配列に書き換えてリフレクションを無効化している場合、キャッシュ自体を破棄する。
     */
    public static void clearReflectionCache(Class<?> clazz) {
        if (THE_UNSAFE != null && clazz != null && REFLECTION_DATA_OFFSET >= 0) {
            try {
                THE_UNSAFE.putObjectVolatile(clazz, REFLECTION_DATA_OFFSET, null);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * リフレクションキャッシュ汚染（{@code PLZBase.antiRefGetMF} 等）を迂回してクラスの全宣言フィールドを取得する。
     */
    public static Field[] getRealDeclaredFields(Class<?> clazz) {
        if (clazz == null) return new Field[0];
        try {
            Field[] fields = clazz.getDeclaredFields();
            if (fields.length > 0) return fields;
        } catch (Throwable ignored) {}
        clearReflectionCache(clazz);
        if (GET_DECLARED_FIELDS_0 != null) {
            try {
                Field[] raw = (Field[]) GET_DECLARED_FIELDS_0.invokeExact(clazz, false);
                if (raw != null) return raw;
            } catch (Throwable ignored) {}
        }
        try {
            return clazz.getDeclaredFields();
        } catch (Throwable t) {
            return new Field[0];
        }
    }

    /**
     * リフレクションキャッシュ汚染（{@code PLZBase.antiRefGetMF} 等）を迂回してクラスの全宣言メソッドを取得する。
     */
    public static Method[] getRealDeclaredMethods(Class<?> clazz) {
        if (clazz == null) return new Method[0];
        try {
            Method[] methods = clazz.getDeclaredMethods();
            if (methods.length > 0) return methods;
        } catch (Throwable ignored) {}
        clearReflectionCache(clazz);
        if (GET_DECLARED_METHODS_0 != null) {
            try {
                Method[] raw = (Method[]) GET_DECLARED_METHODS_0.invokeExact(clazz, false);
                if (raw != null) return raw;
            } catch (Throwable ignored) {}
        }
        try {
            return clazz.getDeclaredMethods();
        } catch (Throwable t) {
            return new Method[0];
        }
    }

    /**
     * リフレクションキャッシュ汚染を迂回してクラス階層・インターフェースからメソッドを検索する。
     */
    public static Method findRealMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        if (clazz == null || name == null) return null;
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : getRealDeclaredMethods(c)) {
                if (name.equals(m.getName()) && Arrays.equals(m.getParameterTypes(), parameterTypes)) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m;
                }
            }
            for (Class<?> iface : c.getInterfaces()) {
                Method found = findRealMethodOnInterface(iface, name, parameterTypes);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Method findRealMethodOnInterface(Class<?> iface, String name, Class<?>[] parameterTypes) {
        for (Method m : getRealDeclaredMethods(iface)) {
            if (name.equals(m.getName()) && Arrays.equals(m.getParameterTypes(), parameterTypes)) {
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {}
                return m;
            }
        }
        for (Class<?> superIface : iface.getInterfaces()) {
            Method found = findRealMethodOnInterface(superIface, name, parameterTypes);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 任意のインターフェース（SuperSteve の {@code Operator} 等の関数型 HP ラッパー）に対し、
     * 常に {@code 0.0F} / {@code false} / {@code 0} を返し例外を起こさない無害な動的プロキシを生成する。
     */
    public static Object createZeroProxy(Class<?> iface) {
        if (iface == null || !iface.isInterface()) return null;
        try {
            return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (proxy, method, args) -> {
                String mName = method.getName();
                if ("toString".equals(mName) && (args == null || args.length == 0)) return "ZeroProxy";
                if ("hashCode".equals(mName) && (args == null || args.length == 0)) return 0;
                if ("equals".equals(mName) && args != null && args.length == 1) return proxy == args[0];
                Class<?> rt = method.getReturnType();
                if (rt == void.class || rt == Void.class) return null;
                if (rt == boolean.class || rt == Boolean.class) return Boolean.FALSE;
                if (rt == float.class || rt == Float.class || rt == Object.class || rt == Number.class) return 0.0F;
                if (rt == double.class || rt == Double.class) return 0.0D;
                if (rt == int.class || rt == Integer.class) return 0;
                if (rt == long.class || rt == Long.class) return 0L;
                if (rt == short.class || rt == Short.class) return (short) 0;
                if (rt == byte.class || rt == Byte.class) return (byte) 0;
                if (rt == char.class || rt == Character.class) return '\0';
                if (rt == String.class) return "";
                return null;
            });
        } catch (Throwable t) {
            return null;
        }
    }

    public static Field findField(Class<?> clazz, String... candidateNames) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Field[] declared = getRealDeclaredFields(current);
            for (String name : candidateNames) {
                for (Field f : declared) {
                    if (name.equals(f.getName())) {
                        try {
                            f.setAccessible(true);
                        } catch (Throwable ignored) {}
                        return f;
                    }
                }
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

    public static long getLong(Object target, long offset) {
        if (THE_UNSAFE == null || target == null || offset < 0) return 0L;
        return THE_UNSAFE.getLong(target, offset);
    }

    public static void putLong(Object target, long offset, long value) {
        if (THE_UNSAFE != null && target != null && offset >= 0) {
            THE_UNSAFE.putLong(target, offset, value);
        }
    }

    public static float getFloat(Object target, long offset) {
        if (THE_UNSAFE == null || target == null || offset < 0) return 0.0F;
        return THE_UNSAFE.getFloat(target, offset);
    }

    public static void putFloat(Object target, long offset, float value) {
        if (THE_UNSAFE != null && target != null && offset >= 0) {
            THE_UNSAFE.putFloat(target, offset, value);
        }
    }

    public static double getDouble(Object target, long offset) {
        if (THE_UNSAFE == null || target == null || offset < 0) return 0.0D;
        return THE_UNSAFE.getDouble(target, offset);
    }

    public static void putDouble(Object target, long offset, double value) {
        if (THE_UNSAFE != null && target != null && offset >= 0) {
            THE_UNSAFE.putDouble(target, offset, value);
        }
    }

    /**
     * 未知のチート MOD オブジェクトやボスの内部メモリを走査し、
     * 全フィールド（参照・プリミティブ・タイマー等）をゼロクリア・null化して自壊させる。
     * <p>
     * <b>※重要: プレイヤーに対しては絶対に実行してはならない（リスポーン不全等の原因となるため）。</b>
     * また、Entity / LivingEntity のバニラ基底クラスのフィールドは破壊せず、
     * MOD 側のサブクラスが保持する独自フィールドのみを消去する。
     * </p>
     */
    public static void obliterateObjectMemory(Object target) {
        if (target == null || THE_UNSAFE == null) return;
        // プレイヤーおよび保護対象（Eprotect+）に対する執行を厳格に除外
        if (target.getClass().getName().contains("Player")) return;
        if (target instanceof com.automatavalkyries.protect.IEprotected) return;

        Class<?> clazz = target.getClass();
        // Entity, LivingEntity, Mob 等のバニラ基底クラスに達するまで（MOD 側のサブクラス階層のみ走査）
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            String cName = c.getName();
            if (cName.equals("net.minecraft.world.entity.Entity")
                    || cName.equals("net.minecraft.world.entity.LivingEntity")
                    || cName.equals("net.minecraft.world.entity.Mob")) {
                break;
            }

            for (Field f : getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                long offset = objectFieldOffset(f);
                if (offset < 0) continue;

                Class<?> type = f.getType();
                try {
                    if (type == boolean.class) {
                        putBoolean(target, offset, false);
                    } else if (type == byte.class) {
                        THE_UNSAFE.putByte(target, offset, (byte) 0);
                    } else if (type == short.class) {
                        THE_UNSAFE.putShort(target, offset, (short) 0);
                    } else if (type == char.class) {
                        THE_UNSAFE.putChar(target, offset, (char) 0);
                    } else if (type == int.class) {
                        putInt(target, offset, 0);
                    } else if (type == long.class) {
                        putLong(target, offset, 0L);
                    } else if (type == float.class) {
                        putFloat(target, offset, 0.0F);
                    } else if (type == double.class) {
                        putDouble(target, offset, 0.0D);
                    } else {
                        Object obj = null;
                        try {
                            obj = getObject(target, offset);
                        } catch (Throwable ignored) {}

                        if (obj instanceof net.minecraft.server.level.ServerBossEvent sbe) {
                            // ServerBossEvent は全プレイヤーから切り離して非表示化し、参照自体は残す
                            // （残여 Tick の customServerAiStep 等で bossEvent.setProgress が呼ばれても NPE にしないため）
                            try {
                                sbe.removeAllPlayers();
                                sbe.setVisible(false);
                            } catch (Throwable ignored) {}
                        } else if (obj instanceof net.minecraft.world.BossEvent) {
                            // クライアント側等の BossEvent も参照自体は残す
                        } else if (type == boolean[].class && obj != null) {
                            java.util.Arrays.fill((boolean[]) obj, false);
                        } else if (type == byte[].class && obj != null) {
                            java.util.Arrays.fill((byte[]) obj, (byte) 0);
                        } else if (type == short[].class && obj != null) {
                            java.util.Arrays.fill((short[]) obj, (short) 0);
                        } else if (type == char[].class && obj != null) {
                            java.util.Arrays.fill((char[]) obj, (char) 0);
                        } else if (type == int[].class && obj != null) {
                            java.util.Arrays.fill((int[]) obj, 0);
                        } else if (type == long[].class && obj != null) {
                            java.util.Arrays.fill((long[]) obj, 0L);
                        } else if (type == float[].class && obj != null) {
                            java.util.Arrays.fill((float[]) obj, 0.0F);
                        } else if (type == double[].class && obj != null) {
                            java.util.Arrays.fill((double[]) obj, 0.0D);
                        } else if (net.minecraft.world.item.ItemStack.class.isAssignableFrom(type)) {
                            putObject(target, offset, net.minecraft.world.item.ItemStack.EMPTY);
                        } else if (net.minecraftforge.common.util.LazyOptional.class.isAssignableFrom(type)) {
                            putObject(target, offset, net.minecraftforge.common.util.LazyOptional.empty());
                        } else if (obj instanceof java.util.Collection<?> col) {
                            try {
                                removeFromArrayList(col, x -> true);
                                col.clear();
                            } catch (Throwable ignored) {}
                        } else if (obj instanceof java.util.Map<?, ?> map) {
                            try {
                                map.clear();
                            } catch (Throwable ignored) {}
                        } else if (type.isArray() && obj != null) {
                            Class<?> compType = type.getComponentType();
                            if (net.minecraftforge.common.util.LazyOptional.class.isAssignableFrom(compType)) {
                                java.util.Arrays.fill((Object[]) obj, net.minecraftforge.common.util.LazyOptional.empty());
                            } else if (net.minecraft.world.item.ItemStack.class.isAssignableFrom(compType)) {
                                java.util.Arrays.fill((Object[]) obj, net.minecraft.world.item.ItemStack.EMPTY);
                            } else if (net.minecraft.world.entity.Entity.class.isAssignableFrom(compType)) {
                                putObject(target, offset, java.lang.reflect.Array.newInstance(compType, 0));
                            }
                        } else if (obj instanceof java.util.concurrent.atomic.AtomicBoolean ab) {
                            ab.set(false);
                        } else if (obj instanceof java.util.concurrent.atomic.AtomicInteger ai) {
                            ai.set(0);
                        } else if (obj instanceof java.util.concurrent.atomic.AtomicLong al) {
                            al.set(0L);
                        } else if (obj instanceof java.util.concurrent.atomic.AtomicReference<?> ar) {
                            ar.set(null);
                        } else if (type.isEnum()
                                || obj instanceof Enum<?>
                                || obj instanceof String
                                || obj instanceof java.util.UUID
                                || obj instanceof net.minecraft.world.phys.Vec3
                                || obj instanceof net.minecraft.world.phys.AABB
                                || obj instanceof net.minecraft.network.chat.Component
                                || obj instanceof net.minecraft.resources.ResourceLocation
                                || obj instanceof net.minecraft.network.syncher.EntityDataAccessor<?>
                                || obj instanceof net.minecraft.network.syncher.SynchedEntityData) {
                            // 値オブジェクト・Enum・DataAccessor は残余 Tick での NPE 防止のため維持
                        } else if (type.isInterface()
                                && !type.getName().startsWith("java.")
                                && !type.getName().startsWith("net.minecraft.")
                                && !type.getName().startsWith("net.minecraftforge.")) {
                            // 関数型インターフェース（SuperSteve の Operator health 等）は null にすると
                            // isAlive() / getHealth() / remove() 呼び出し時に NPE が発生して除去が中断されるため、
                            // 常に 0.0F / false を返す動的プロキシへ置換する
                            Object zeroProxy = createZeroProxy(type);
                            putObject(target, offset, zeroProxy);
                        } else {
                            // 外部コントローラー・プロキシ等の参照型は null クリアして強制切断
                            putObject(target, offset, null);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * 削除を拒否する ArrayList（Omni-Mobs の ProtectedArrayList など）や CopyOnWriteArrayList（Madness の C848734 など）から、
     * 条件に合う要素を内部配列ごと詰めて取り除く。
     * <p>
     * 拒否する実装に {@code Iterator.remove()} を使ってはいけない: 内部の {@code remove(int)} が何もせずに戻ると、
     * イテレータは位置を巻き戻して同じ要素を返し続け、無限ループになる。
     * </p>
     * @return 取り除いた数（対応コレクションでない・Unsafe が使えないときは 0）
     */
    public static int removeFromArrayList(Object list, java.util.function.Predicate<Object> shouldRemove) {
        if (THE_UNSAFE == null || list == null) return 0;
        if (list instanceof java.util.concurrent.CopyOnWriteArraySet<?> cowSet) {
            try {
                long alOffset = objectFieldOffset(java.util.concurrent.CopyOnWriteArraySet.class.getDeclaredField("al"));
                if (alOffset >= 0) return removeFromArrayList(getObject(cowSet, alOffset), shouldRemove);
            } catch (Throwable ignored) {}
            return 0;
        }
        if (list instanceof java.util.concurrent.CopyOnWriteArrayList<?> cowList) {
            try {
                long arrayOffset = objectFieldOffset(java.util.concurrent.CopyOnWriteArrayList.class.getDeclaredField("array"));
                if (arrayOffset < 0) return 0;
                if (!(THE_UNSAFE.getObjectVolatile(cowList, arrayOffset) instanceof Object[] data)
                        || data.length > StaticReferencePurger.MAX_SIZE) return 0;
                Object[] temp = new Object[data.length];
                int kept = 0;
                for (Object item : data) {
                    if (!shouldRemove.test(item)) temp[kept++] = item;
                }
                int removed = data.length - kept;
                if (removed > 0) {
                    THE_UNSAFE.putObjectVolatile(cowList, arrayOffset, java.util.Arrays.copyOf(temp, kept, Object[].class));
                }
                return removed;
            } catch (Throwable t) {
                return 0;
            }
        }
        if (!(list instanceof java.util.ArrayList<?>)) return 0;
        try {
            long sizeOffset = objectFieldOffset(java.util.ArrayList.class.getDeclaredField("size"));
            long dataOffset = objectFieldOffset(java.util.ArrayList.class.getDeclaredField("elementData"));
            if (sizeOffset < 0 || dataOffset < 0) return 0;
            if (!(getObject(list, dataOffset) instanceof Object[] data)) return 0;
            int size = Math.min(getInt(list, sizeOffset), data.length);
            if (size <= 0 || size > StaticReferencePurger.MAX_SIZE) return 0;
            int kept = 0;
            for (int i = 0; i < size; i++) {
                if (!shouldRemove.test(data[i])) data[kept++] = data[i];
            }
            if (kept != size) {
                java.util.Arrays.fill(data, kept, size, null); // 参照を残すと GC されない
                putInt(list, sizeOffset, kept);
                incrementArrayListModCount(list);
            }
            return size - kept;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * ArrayList の要素数を Unsafe で直接縮小した際、進行中の {@code ArrayList$Itr.next()} が
     * {@code NoSuchElementException} でクラッシュするのを防ぐため {@code AbstractList.modCount} を加算する。
     */
    public static void incrementArrayListModCount(Object list) {
        if (THE_UNSAFE == null || !(list instanceof java.util.AbstractList<?>)) return;
        try {
            Field modField = findField(java.util.AbstractList.class, "modCount");
            if (modField != null) {
                long modOffset = objectFieldOffset(modField);
                if (modOffset >= 0) {
                    putInt(list, modOffset, getInt(list, modOffset) + 1);
                }
            }
        } catch (Throwable ignored) {}
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
                    Class<?> type = f.getType();
                    if (type == boolean.class && value instanceof Boolean b) {
                        THE_UNSAFE.putBoolean(base, offset, b);
                    } else if (type == int.class && value instanceof Number n) {
                        THE_UNSAFE.putInt(base, offset, n.intValue());
                    } else if (type == long.class && value instanceof Number n) {
                        THE_UNSAFE.putLong(base, offset, n.longValue());
                    } else if (type == float.class && value instanceof Number n) {
                        THE_UNSAFE.putFloat(base, offset, n.floatValue());
                    } else if (type == double.class && value instanceof Number n) {
                        THE_UNSAFE.putDouble(base, offset, n.doubleValue());
                    } else if (type == byte.class && value instanceof Number n) {
                        THE_UNSAFE.putByte(base, offset, n.byteValue());
                    } else if (type == short.class && value instanceof Number n) {
                        THE_UNSAFE.putShort(base, offset, n.shortValue());
                    } else if (type == char.class && value instanceof Character c) {
                        THE_UNSAFE.putChar(base, offset, c);
                    } else if (!type.isPrimitive()) {
                        THE_UNSAFE.putObject(base, offset, value);
                    }
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
