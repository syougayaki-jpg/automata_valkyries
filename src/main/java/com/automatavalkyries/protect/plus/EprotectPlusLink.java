package com.automatavalkyries.protect.plus;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.MadnessAnnihilation;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.protect.EventBusGuardian;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.InjectedMembers;
import com.automatavalkyries.protect.flag.KillLists;
import com.automatavalkyries.util.StaticReferencePurger;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MOD 側から Eprotect+ Java エージェント（システムクラスローダー常駐）を操作・参照するためのリンク。
 * <p>
 * {@code -javaagent:automata_valkyries-eprotect-plus.jar} が付いていれば JVM エージェント経由で
 * バイトコード監視・再監査・診断・動的クラス捕捉を利用できる。
 * エージェントが付いていない環境（通常の GameTest や非エージェント起動時）でも、
 * ゲーム内の対象限定の前処理・汎用防御・定数プール解析フォールバックが機能する。
 * </p>
 */
public final class EprotectPlusLink {

    private static final String BRIDGE_CLASS = "com.automatavalkyries.eprotectplus.EprotectPlusBridge";

    @Nullable
    private static volatile Class<?> bridge;
    private static volatile long nextBridgeLookupNs = System.nanoTime();
    private static final Map<String, Method> BRIDGE_METHODS = new ConcurrentHashMap<>();

    // ---- フォールバック用ローカル記憶域（エージェント未接続時） -----------------
    private static final int MAX_LOCAL_EVENTS = 512;
    private static final Deque<Map<String, String>> LOCAL_EVENTS = new ArrayDeque<>();
    private static final AtomicLong LOCAL_INTERFERENCES = new AtomicLong();
    private static final AtomicLong LOCAL_BYPASSES = new AtomicLong();
    private static final AtomicLong LOCAL_PROTECTIONS = new AtomicLong();
    private static final AtomicLong LOCAL_ANNIHILATIONS = new AtomicLong();
    private static final ThreadLocal<Boolean> LOCAL_PRIVILEGED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final AtomicLong LOCAL_REINFORCED_UNTIL_NS = new AtomicLong(System.nanoTime());
    private static volatile String LOCAL_REINFORCED_REASON = "";

    /** 保護対象の正規 UUID（Unsafe による UUID すり替え・Identity Spoofing 攻撃からの自己修復用） */
    private static final Map<LivingEntity, UUID> PROTECTED_TRUE_UUID = Collections.synchronizedMap(new WeakHashMap<>());
    /** 保護対象の正常時の EntityInLevelCallback（Unsafe 等による NULL 化・追跡切断からの自己修復用） */
    private static final Map<LivingEntity, net.minecraft.world.level.entity.EntityInLevelCallback> PROTECTED_LEVEL_CALLBACK =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** クラスごとの自爆トラップ（Runtime.halt / System.exit）ローカル検査キャッシュ */
    private static final Map<Class<?>, Boolean> LOCAL_HALT_TRAP_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile long lastAgentHookDiscoveryNs = 0L;

    private EprotectPlusLink() {}

    public static synchronized void refreshAfterAttach() {
        bridge = null;
        BRIDGE_METHODS.clear();
        nextBridgeLookupNs = System.nanoTime();
        bridge();
    }

    @Nullable
    private static synchronized Class<?> bridge() {
        if (bridge != null) return bridge;
        long now = System.nanoTime();
        if (now - nextBridgeLookupNs < 0) return null;
        nextBridgeLookupNs = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        try {
            bridge = Class.forName(BRIDGE_CLASS, true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) { }
        return bridge;
    }

    private static Method bridgeMethod(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        String key = name + java.util.Arrays.toString(parameters);
        Method method = BRIDGE_METHODS.get(key);
        if (method == null) {
            method = type.getMethod(name, parameters);
            BRIDGE_METHODS.putIfAbsent(key, method);
        }
        return method;
    }

    /** Java エージェントが有効かつ稼働中か */
    public static boolean isAgentActive() {
        Class<?> type = bridge();
        if (type == null) return false;
        try {
            Method m = bridgeMethod(type, "isActive");
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 協調用の特権フラグの実行範囲内か（JVM権限・スタックを変更するものではない） */
    public static boolean isPrivilegedOrBypassing() {
        if (Boolean.TRUE.equals(LOCAL_PRIVILEGED.get())) return true;
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "isPrivileged");
                return Boolean.TRUE.equals(m.invoke(null));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /** 同じスレッド上の協調用フラグ。action は正常時・例外時とも一度だけ実行する。 */
    public static void runPrivileged(Runnable action) {
        runScoped("runPrivileged", action);
    }

    /** 互換API。呼び出し履歴を隠す保証はなく、特権フラグの範囲だけを管理する。 */
    public static void runWithCloakedStack(Runnable action) {
        java.util.Objects.requireNonNull(action, "action");
        runScoped("runCloaked", () -> java.util.stream.Stream.of(action).forEach(Runnable::run));
    }

    private static void runScoped(String methodName, Runnable action) {
        java.util.Objects.requireNonNull(action, "action");
        boolean previous = LOCAL_PRIVILEGED.get();
        LOCAL_PRIVILEGED.set(true);
        try {
            Class<?> type = bridge();
            Method method = null;
            if (type != null && isAgentActive()) {
                try {
                    method = bridgeMethod(type, methodName, Runnable.class);
                } catch (NoSuchMethodException ignored) { }
            }
            if (method == null) {
                action.run();
                return;
            }
            try {
                method.invoke(null, action);
            } catch (java.lang.reflect.InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Eprotect+ action failed", cause);
            } catch (IllegalAccessException exception) {
                // Invocation has not started; only this case may use the local action.
                action.run();
            }
        } finally {
            if (previous) LOCAL_PRIVILEGED.set(true);
            else LOCAL_PRIVILEGED.remove();
        }
    }

    /**
     * JVM エージェントによる能動的バイトコード再監査・強制復元（案2）。
     * @return 正常に再変換したクラス数（復元を確認した件数ではない）
     */
    public static int enforceBytecodeIntegrity() {
        return enforceBytecodeIntegrityInternal("retransformCriticalClasses");
    }

    /**
     * 強化保護突入時など緊急時の即時バイトコード再監査（最小 5 秒間隔）。
     */
    public static int enforceBytecodeIntegrityUrgent() {
        int res = enforceBytecodeIntegrityInternal("retransformCriticalClassesUrgent");
        return res > 0 ? res : enforceBytecodeIntegrityInternal("retransformCriticalClasses");
    }

    private static int enforceBytecodeIntegrityInternal(String bridgeMethodName) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, bridgeMethodName);
                Object res = isolateForeignCallbacksAndCall(() -> m.invoke(null));
                if (res instanceof Integer count) {
                    return count;
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private record SavedStaticField(Class<?> owner, String fieldName, Object value) {}
    private record SavedAtomicFlag(java.util.concurrent.atomic.AtomicBoolean target, boolean previous) {}

    /**
     * スコープ実行中だけ、他 MOD のエージェント破棄コールバック（SuperSteve の {@code AgtCallback.INST} や
     * {@code sun.instrument.InstrumentationImpl} から参照される外部変換フックの static シングルトン、
     * および外部エージェント／トランスフォーマークラスの有効化フラグ）を
     * 汎用的に一時隔離・休眠し、スコープ終了後に元へ戻す（不干渉・非競合の原則）。
     */
    private static <T> T isolateForeignCallbacksAndCall(java.util.concurrent.Callable<T> task) throws Exception {
        List<SavedStaticField> saved = new ArrayList<>();
        List<SavedAtomicFlag> savedAtomics = new ArrayList<>();
        try {
            // 1. 既知の AgtCallback（ロード済みかつ初期化済みの場合のみ）
            isolateSingletonFieldByName("plz.lizi.supersteve.power.AgtCallback", "INST", saved);

            // 2. 汎用: sun.instrument.InstrumentationImpl が参照している外部クラスの static シングルトン隔離
            try {
                Class<?> instImpl = Class.forName("sun.instrument.InstrumentationImpl", false, null);
                for (String refName : getAgentReferencedClasses(instImpl)) {
                    try {
                        Class<?> hookClass = Class.forName(refName, false, EprotectPlusLink.class.getClassLoader());
                        if (CallerAttribution.isForeign(CallerAttribution.modOf(hookClass))
                                && UnsafeHelper.isInitialized(hookClass)) {
                            isolateTransformHookSingletons(hookClass, saved, savedAtomics);
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            // 3. 汎用: 外部 ClassFileTransformer / エージェントフッククラス群の一時休眠
            for (Class<?> agentClass : findForeignAgentAndHiddenClasses()) {
                if (agentClass != null && UnsafeHelper.isInitialized(agentClass)) {
                    isolateTransformHookSingletons(agentClass, saved, savedAtomics);
                }
            }

            return task.call();
        } finally {
            restoreSavedAtomicFlags(savedAtomics);
            restoreSavedStaticFields(saved);
        }
    }

    /**
     * 案 C: 抹殺・解体スコープの実行中（数ミリ秒）だけ、対象 MOD の関連クラス群が持つ
     * バイトコード変換フックや割り込みコールバック（{@code afterTransform} / {@code beforeTransform} 等を持つクラスの
     * static シングルトンや static 関数型フック・有効化フラグ）を一時退避・隔離し、{@code finally} で即座に復元する。
     */
    public static void runWithIsolatedTargetHooks(Set<Class<?>> targetClasses, Runnable action) {
        if (targetClasses == null || targetClasses.isEmpty()) {
            action.run();
            return;
        }
        List<SavedStaticField> saved = new ArrayList<>();
        List<SavedAtomicFlag> savedAtomics = new ArrayList<>();
        try {
            isolateSingletonFieldByName("plz.lizi.supersteve.power.AgtCallback", "INST", saved);
            for (Class<?> c : targetClasses) {
                if (c == null || !CallerAttribution.isForeign(CallerAttribution.modOf(c))
                        || !UnsafeHelper.isInitialized(c)) {
                    continue;
                }
                isolateTransformHookSingletons(c, saved, savedAtomics);
                if (saved.size() + savedAtomics.size() >= 24) break;
            }
            action.run();
        } finally {
            restoreSavedAtomicFlags(savedAtomics);
            restoreSavedStaticFields(saved);
        }
    }

    private static void isolateSingletonFieldByName(String className, String fieldName, List<SavedStaticField> saved) {
        try {
            Class<?> clz = Class.forName(className, false, EprotectPlusLink.class.getClassLoader());
            if (UnsafeHelper.isInitialized(clz)) {
                Object val = UnsafeHelper.getStaticField(clz, fieldName);
                if (val != null) {
                    UnsafeHelper.setStaticFinalField(clz, fieldName, null);
                    if (UnsafeHelper.getStaticField(clz, fieldName) == null) {
                        saved.add(new SavedStaticField(clz, fieldName, val));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void isolateTransformHookSingletons(
            Class<?> hookClass,
            List<SavedStaticField> saved,
            List<SavedAtomicFlag> savedAtomics
    ) {
        try {
            if (hasHaltOrExitTrap(hookClass)) return;
            boolean hasTransformHook = java.lang.instrument.ClassFileTransformer.class.isAssignableFrom(hookClass);
            if (!hasTransformHook) {
                for (Method m : hookClass.getDeclaredMethods()) {
                    String lower = m.getName().toLowerCase(Locale.ROOT);
                    if (lower.contains("transform") || lower.contains("redefine") || lower.contains("agtcallback")) {
                        hasTransformHook = true;
                        break;
                    }
                }
            }
            String simpleLower = hookClass.getSimpleName().toLowerCase(Locale.ROOT);
            if (!hasTransformHook && !simpleLower.contains("callback") && !simpleLower.contains("agent")
                    && !simpleLower.contains("hook") && !simpleLower.contains("interceptor")
                    && !simpleLower.contains("transformer")) {
                return;
            }
            for (Field f : hookClass.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                String fLower = f.getName().toLowerCase(Locale.ROOT);
                if (f.getType() == boolean.class && isAgentEnableFlagName(fLower)) {
                    Object cur = UnsafeHelper.getStaticField(hookClass, f.getName());
                    if (Boolean.TRUE.equals(cur)) {
                        UnsafeHelper.setStaticFinalField(hookClass, f.getName(), Boolean.FALSE);
                        if (Boolean.FALSE.equals(UnsafeHelper.getStaticField(hookClass, f.getName()))) {
                            saved.add(new SavedStaticField(hookClass, f.getName(), Boolean.TRUE));
                        }
                    }
                    continue;
                }
                if (f.getType() == java.util.concurrent.atomic.AtomicBoolean.class && isAgentEnableFlagName(fLower)) {
                    Object cur = UnsafeHelper.getStaticField(hookClass, f.getName());
                    if (cur instanceof java.util.concurrent.atomic.AtomicBoolean ab && ab.get()) {
                        ab.set(false);
                        savedAtomics.add(new SavedAtomicFlag(ab, true));
                    }
                    continue;
                }
                if (f.getType().isPrimitive()) continue;
                boolean singletonOrHook = f.getType() == hookClass
                        || fLower.equals("inst") || fLower.equals("instance")
                        || fLower.contains("callback") || fLower.contains("hook")
                        || f.getType().getName().startsWith("java.util.function.");
                if (!singletonOrHook) continue;
                Object val = UnsafeHelper.getStaticField(hookClass, f.getName());
                if (val != null) {
                    UnsafeHelper.setStaticFinalField(hookClass, f.getName(), null);
                    if (UnsafeHelper.getStaticField(hookClass, f.getName()) == null) {
                        saved.add(new SavedStaticField(hookClass, f.getName(), val));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isAgentEnableFlagName(String lower) {
        return lower.equals("enabled") || lower.equals("active") || lower.equals("hooked")
                || lower.equals("installed") || lower.equals("transforming") || lower.equals("running");
    }

    private static void restoreSavedAtomicFlags(List<SavedAtomicFlag> savedAtomics) {
        for (int i = savedAtomics.size() - 1; i >= 0; i--) {
            SavedAtomicFlag entry = savedAtomics.get(i);
            try {
                entry.target().set(entry.previous());
            } catch (Throwable ignored) {}
        }
    }

    private static void restoreSavedStaticFields(List<SavedStaticField> saved) {
        for (int i = saved.size() - 1; i >= 0; i--) {
            SavedStaticField entry = saved.get(i);
            try {
                UnsafeHelper.setStaticFinalField(entry.owner(), entry.fieldName(), entry.value());
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 対 Java エージェント汎用対策 ① & ②:
     * <ul>
     *   <li>バニラ重要クラスのバイトコードから逆引きされた外部エージェントフッククラス</li>
     *   <li>Bootstrap ClassLoader（{@code getClassLoader() == null}）に注入された外部クラス</li>
     *   <li>{@code defineHiddenClass} / {@code defineAnonymousClass} で定義された外部 Hidden クラス</li>
     *   <li>外部の {@code ClassFileTransformer} 実装クラスと同一 JAR / パッケージの補助クラス</li>
     *   <li>エージェント未接続時のフォールバックとして、バニラ {@code LivingEntity} / {@code Entity} の参照先や
     *       常駐エージェントスレッドのクラス</li>
     * </ul>
     * を一括で取得する。
     */
    private static volatile List<Class<?>> cachedForeignAgentClasses = List.of();
    private static final AtomicLong LAST_FOREIGN_AGENT_SCAN_NS = new AtomicLong(0L);
    private static final long FOREIGN_AGENT_SCAN_CACHE_NS = 10_000_000_000L;

    @SuppressWarnings("unchecked")
    public static List<Class<?>> findForeignAgentAndHiddenClasses() {
        long now = System.nanoTime();
        long prev = LAST_FOREIGN_AGENT_SCAN_NS.get();
        if (prev != 0L && now - prev < FOREIGN_AGENT_SCAN_CACHE_NS) {
            return cachedForeignAgentClasses;
        }
        if (!LAST_FOREIGN_AGENT_SCAN_NS.compareAndSet(prev, now)) {
            return cachedForeignAgentClasses;
        }
        Set<Class<?>> found = new LinkedHashSet<>();
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "findForeignAgentAndHiddenClasses", int.class);
                Object res = m.invoke(null, 64);
                if (res instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Class<?> c && isForeignOrBootstrapAgentClass(c)) {
                            found.add(c);
                        }
                    }
                }
            } catch (Throwable ignored) {}
            try {
                Method m = bridgeMethod(type, "getVanillaInjectedHookClasses");
                Object res = m.invoke(null);
                if (res instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof String className && !className.isEmpty()) {
                            try {
                                Class<?> c = Class.forName(className, false, EprotectPlusLink.class.getClassLoader());
                                if (isForeignOrBootstrapAgentClass(c)) found.add(c);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        // エージェント未接続時（-PgenericOnly 等）でも機能するフォールバック探索:
        // 1. LivingEntity / Entity のバイトコードから参照されている外部クラス
        for (Class<?> vanilla : new Class<?>[]{net.minecraft.world.entity.LivingEntity.class, Entity.class}) {
            for (String refName : getAgentReferencedClasses(vanilla)) {
                if (found.size() >= 64) break;
                try {
                    Class<?> c = Class.forName(refName, false, EprotectPlusLink.class.getClassLoader());
                    if (isForeignOrBootstrapAgentClass(c)) found.add(c);
                } catch (Throwable ignored) {}
            }
        }
        // 2. 常駐スレッドのうちエージェント・監視スレッドに属する外部クラス（Safepoint を伴わない ThreadGroup 列挙）
        try {
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            while (group != null && group.getParent() != null) {
                group = group.getParent();
            }
            if (group != null) {
                Thread[] threads = new Thread[Math.min(group.activeCount() + 32, 256)];
                int count = group.enumerate(threads, true);
                for (int i = 0; i < count && found.size() < 64; i++) {
                    Thread thread = threads[i];
                    if (thread == null) continue;
                    Class<?> tc = thread.getClass();
                    if (tc != Thread.class && isForeignOrBootstrapAgentClass(tc)) {
                        found.add(tc);
                    }
                }
            }
        } catch (Throwable ignored) {}
        List<Class<?>> result = List.copyOf(found);
        cachedForeignAgentClasses = result;
        return result;
    }

    /**
     * 対 Java エージェント汎用対策 ①:
     * バニラ重要クラス（{@code Entity} / {@code LivingEntity} 等）の変換後バイトコード内に
     * 外部エージェントや変換器から埋め込まれた外部フッククラス名一覧（ドット区切り）を取得する。
     */
    @SuppressWarnings("unchecked")
    public static List<String> getVanillaInjectedHookClasses() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "getVanillaInjectedHookClasses");
                Object res = m.invoke(null);
                if (res instanceof List<?> list) {
                    return (List<String>) list;
                }
            } catch (Throwable ignored) {}
        }
        return List.of();
    }

    private static boolean isForeignOrBootstrapAgentClass(Class<?> c) {
        if (c == null) return false;
        String name = c.getName();
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun.")
                || name.startsWith("com.mojang.") || name.startsWith("net.minecraftforge.")
                || name.startsWith("cpw.mods.") || name.startsWith("org.spongepowered.")
                || name.startsWith("org.objectweb.") || name.startsWith("org.slf4j.")) {
            return false;
        }
        if (name.startsWith("net.minecraft.") && !name.startsWith("net.minecraft.madcrazy.")) {
            return false;
        }
        if (name.startsWith("com.automatavalkyries.") && !name.startsWith("com.automatavalkyries.gametest.fake.")) {
            return false;
        }
        return c.getClassLoader() == null || c.isHidden() || CallerAttribution.isForeign(CallerAttribution.modOf(c));
    }

    /**
     * 攻撃時のエージェント隔離・案 B 用: 他のエージェントや変換器には一切干渉せず、
     * {@code Instrumentation.getAllLoadedClasses()} 経由で抹殺対象と同じ MOD JAR（{@code CodeSource}）
     * または同じパッケージ接頭辞のロード済みクラス（{@code Unsafe.defineClass} で隠蔽定義された未参照クラスを含む）を抽出する。
     */
    @SuppressWarnings("unchecked")
    public static List<Class<?>> findAgentLoadedClassesFor(Class<?> referenceType) {
        if (referenceType == null) return List.of();
        String mod = CallerAttribution.modOf(referenceType);
        if (!CallerAttribution.isForeign(mod)) return List.of();
        String prefix = foreignPackagePrefix(referenceType.getName());
        Class<?> type = bridge();
        if (type == null) return List.of();
        try {
            Object res = null;
            try {
                Method byOwner = bridgeMethod(type, "findLoadedClassesByOwner", Class.class, String.class, int.class);
                res = byOwner.invoke(null, referenceType, prefix, 48);
            } catch (NoSuchMethodException ignored) {
                if (!prefix.isEmpty()) {
                    Method byPrefix = bridgeMethod(type, "findLoadedClassesByPrefix", String.class, int.class);
                    res = byPrefix.invoke(null, prefix, 48);
                }
            }
            if (res instanceof List<?> list) {
                List<Class<?>> out = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Class<?> c
                            && CallerAttribution.isForeign(CallerAttribution.modOf(c))
                            && UnsafeHelper.isInitialized(c)) {
                        out.add(c);
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {}
        return List.of();
    }

    /**
     * 案 A: エージェントがパッシブ捕獲した対象クラスの定数プール参照先クラス名一覧（ドット区切り）を取得する。
     */
    @SuppressWarnings("unchecked")
    public static List<String> getAgentReferencedClasses(Class<?> targetClass) {
        if (targetClass == null) return List.of();
        Class<?> type = bridge();
        if (type == null) return List.of();
        try {
            Method m = bridgeMethod(type, "getReferencedClasses", Class.class);
            Object res = m.invoke(null, targetClass);
            if (res instanceof List<?> list) {
                return (List<String>) list;
            }
        } catch (Throwable ignored) {}
        return List.of();
    }

    /**
     * 案 D: 対象クラスのバイトコード内に {@code Runtime.halt} / {@code System.exit} 等の
     * 道連れ自爆トラップが含まれているか事前検査する（エージェントキャッシュ＋ローカル ASM フォールバック）。
     */
    public static boolean hasHaltOrExitTrap(Class<?> targetClass) {
        if (targetClass == null) return false;
        Boolean cached = LOCAL_HALT_TRAP_CACHE.get(targetClass);
        if (cached != null) return cached;

        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "hasHaltOrExitTrap", Class.class);
                Object res = m.invoke(null, targetClass);
                if (res instanceof Boolean b && b) {
                    LOCAL_HALT_TRAP_CACHE.put(targetClass, Boolean.TRUE);
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        boolean localDetected = detectHaltOrExitTrapFromResource(targetClass);
        LOCAL_HALT_TRAP_CACHE.put(targetClass, localDetected);
        return localDetected;
    }

    private static boolean detectHaltOrExitTrapFromResource(Class<?> targetClass) {
        String resource = targetClass.getName().replace('.', '/') + ".class";
        ClassLoader loader = targetClass.getClassLoader();
        try (InputStream in = loader != null
                ? loader.getResourceAsStream(resource)
                : ClassLoader.getSystemResourceAsStream(resource)) {
            if (in == null) return false;
            boolean[] found = {false};
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String desc, boolean isInterface) {
                            if (("java/lang/Runtime".equals(owner) && ("halt".equals(methodName) || "exit".equals(methodName)))
                                    || ("java/lang/System".equals(owner) && "exit".equals(methodName))
                                    || ("java/lang/Shutdown".equals(owner) && "halt".equals(methodName))
                                    || ("jdk/internal/misc/VM".equals(owner) && "shutdown".equals(methodName))) {
                                found[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return found[0];
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String foreignPackagePrefix(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot <= 0) return "";
        String pkg = className.substring(0, lastDot + 1);
        if (pkg.startsWith("net.minecraft.") && !pkg.startsWith("net.minecraft.madcrazy.")) return "";
        if (pkg.startsWith("com.mojang.") || pkg.startsWith("net.minecraftforge.")) return "";
        if (pkg.startsWith("com.automatavalkyries.")) {
            return pkg.startsWith("com.automatavalkyries.gametest.fake.") ? "com.automatavalkyries.gametest.fake." : "";
        }
        int first = className.indexOf('.');
        int second = first > 0 ? className.indexOf('.', first + 1) : -1;
        int third = second > 0 ? className.indexOf('.', second + 1) : -1;
        if (third > 0) return className.substring(0, third + 1);
        if (second > 0) return className.substring(0, second + 1);
        return pkg;
    }

    // ---- 強化処理（Reinforced Protection）制御 ---------------------------------

    /**
     * 強化処理（指定ミリ秒）を発動する。
     */
    public static void triggerReinforcedProtection(long durationMs, String reason) {
        boolean alreadyActive = isReinforcedActive();
        long now = System.nanoTime();
        long durationNs = Math.min(Long.MAX_VALUE / 2,
                java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0, durationMs)));
        LOCAL_REINFORCED_UNTIL_NS.updateAndGet(previous -> previous - now > durationNs ? previous : now + durationNs);
        LOCAL_REINFORCED_REASON = reason != null ? reason : "UNKNOWN";

        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "triggerReinforced", long.class, String.class);
                m.invoke(null, durationMs, reason);
            } catch (Throwable ignored) {}
        }

        if (!alreadyActive) {
            // 強化処理開始時に緊急バイトコード点検を執行（60秒クールダウンを緩和し即座に検査）
            enforceBytecodeIntegrityUrgent();

            recordLocal("強化", "EprotectPlusLink", reason, "reinforced_mode", "発動",
                    "期間: " + durationMs + "ms, 理由: " + LOCAL_REINFORCED_REASON);
            AutomataValkyries.LOGGER.info("[eprotect-plus] >>> REINFORCED PROTECTION ACTIVATED <<< Duration: {}ms, Reason: {}",
                    durationMs, LOCAL_REINFORCED_REASON);
        }
    }

    /**
     * 強化処理（Tick 数を50msに換算、例: 200 ticks = 実時間10秒）を発動する。
     */
    public static void triggerReinforcedProtectionTicks(long durationTicks, String reason) {
        long ticks = Math.max(0, durationTicks);
        triggerReinforcedProtection(ticks > Long.MAX_VALUE / 50L ? Long.MAX_VALUE : ticks * 50L, reason);
    }

    /**
     * ブラックリストに入った MOD 相手の強化処理を発動する（10秒間 = 200 ticks）。
     */
    public static void triggerBlacklistReinforcement(String modId) {
        triggerReinforcedProtection(10000L, "BLACKLIST_MOD:" + modId);
        if (modId != null && CallerAttribution.isForeign(modId)) {
            for (Class<?> sub : EventBusGuardian.subscriberClassesForMod(modId)) {
                KillLists.discoverFrom(sub);
            }
        }
    }

    /**
     * 終焉機能に10秒以上耐えられた場合の強化処理を10秒間実行する。
     */
    public static void triggerTerminusResistedReinforcement(Entity target) {
        String name = target != null ? target.getName().getString() : "UnknownTarget";
        triggerReinforcedProtection(10000L, "TERMINUS_RESISTED_10S:" + name);
    }

    /**
     * 現在 Eprotect+ 強化処理が稼働中か。
     */
    public static boolean isReinforcedActive() {
        if (LOCAL_REINFORCED_UNTIL_NS.get() - System.nanoTime() > 0) return true;
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "isReinforced");
                return Boolean.TRUE.equals(m.invoke(null));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static String getReinforcedReason() {
        if (isReinforcedActive()) {
            return LOCAL_REINFORCED_REASON;
        }
        return "";
    }

    public static long getReinforcedRemainingMs() {
        long localRemaining = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0, LOCAL_REINFORCED_UNTIL_NS.get() - System.nanoTime()));
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "getReinforcedRemainingMs");
                Object val = m.invoke(null);
                if (val instanceof Long l) return Math.max(localRemaining, l);
            } catch (Throwable ignored) {}
        }
        return localRemaining;
    }

    /**
     * 強化処理状態を即時リセットする（テスト環境・戦闘終了用）。
     */
    public static void resetReinforced() {
        LOCAL_REINFORCED_UNTIL_NS.set(System.nanoTime());
        LOCAL_REINFORCED_REASON = "";
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "resetReinforced");
                m.invoke(null);
            } catch (Throwable ignored) {}
        }
    }

    // ---- 調査 (Investigation) 記録 -------------------------------------------

    private static final Map<String, Long> LAST_LOCAL_INTERFERENCE_MS = new ConcurrentHashMap<>();
    private static final long LOCAL_INTERFERENCE_THROTTLE_MS = 5000L;

    public static void recordInterference(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "recordInterference", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_INTERFERENCES.incrementAndGet();
        String key = (target != null ? target : "") + "#" + (op != null ? op : "");
        long now = System.nanoTime();
        Long prev = LAST_LOCAL_INTERFERENCE_MS.get(key);
        if (prev != null && (now - prev) < java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(LOCAL_INTERFERENCE_THROTTLE_MS)) {
            return;
        }
        if (LAST_LOCAL_INTERFERENCE_MS.size() > 4096) LAST_LOCAL_INTERFERENCE_MS.clear();
        LAST_LOCAL_INTERFERENCE_MS.put(key, now);
        recordLocal("調査・干渉検知", target, caller, op, "検知・解析完了", details);
    }

    public static void recordBypass(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "recordBypass", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_BYPASSES.incrementAndGet();
        recordLocal("迂回執行", target, caller, op, "迂回成功", details);
    }

    public static void recordProtection(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "recordProtection", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_PROTECTIONS.incrementAndGet();
        recordLocal("保護執行", target, caller, op, "改ざん拒否・復元", details);
    }

    public static void recordAnnihilation(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "recordAnnihilation", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_ANNIHILATIONS.incrementAndGet();
        recordLocal("真抹殺執行", target, caller, op, "完全消滅", details);
    }

    private static void recordLocal(String kind, String target, String caller, String op, String action, String note) {
        Map<String, String> ev = new LinkedHashMap<>();
        ev.put("time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME));
        ev.put("kind", kind);
        ev.put("target", target != null ? target : "-");
        ev.put("caller", caller != null ? caller : "-");
        ev.put("op", op != null ? op : "-");
        ev.put("action", action != null ? action : "-");
        ev.put("note", note != null ? note : "");

        synchronized (LOCAL_EVENTS) {
            LOCAL_EVENTS.addLast(Collections.unmodifiableMap(ev));
            if (LOCAL_EVENTS.size() > MAX_LOCAL_EVENTS) LOCAL_EVENTS.removeFirst();
        }
    }

    // ---- 迂回 & 保護 (Bypass & Protection) -----------------------------------

    /**
     * 保護対象エンティティへの外部干渉を迂回し、正規の生命状態を維持する。
     * 個別対応が有効な場合は既知 5 MOD の処理も走らせ、常時（-PgenericOnly 含む）汎用保護を実行する。
     */
    public static void inspectAndBypassInterference(LivingEntity entity) {
        if (entity == null) return;

        // ---- A. 準汎用 Eprotect+ 防御（MOD 名不問・常時稼働） ------------------
        inspectAndBypassGenericInterference(entity);

        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        UUID uuid = entity.getUUID();

        // 1. No-Sugar / ACE 型の erased 偽装の検出と迂回
        try {
            Class<?> iliving = Class.forName("com.test.nosugar.utils.interfaces.ILivingEntity");
            if (iliving.isInstance(entity)) {
                Method isErased = iliving.getMethod("isErased");
                if (Boolean.TRUE.equals(isErased.invoke(entity))) {
                    recordInterference(entity.getClass().getName(), "No-Sugar", "isErased", "抹消フラグの検知");
                    Method setErased = iliving.getMethod("setErased", boolean.class);
                    setErased.invoke(entity, false);
                    Method unmark = iliving.getMethod("unmarkErased", UUID.class);
                    unmark.invoke(entity, uuid);
                    recordBypass(entity.getClass().getName(), "Eprotect+", "unmarkErased", "抹消フラグを解除し生存状態を維持");
                }
            }
        } catch (Throwable ignored) {}

        // 2. Omni-Mobs 型の healthDelta 負数強制注入の検出と迂回
        try {
            Field f = entity.getClass().getDeclaredField("healthDelta");
            f.setAccessible(true);
            float delta = f.getFloat(entity);
            if (delta < 0.0F) {
                recordInterference(entity.getClass().getName(), "Omni-Mobs", "healthDelta", "HP強制減少デルタ: " + delta);
                f.setFloat(entity, 0.0F);
                recordBypass(entity.getClass().getName(), "Eprotect+", "healthDelta", "デルタを0に修正し不正HP削りを無力化");
            }
        } catch (Throwable ignored) {}

        // 3. SuperSteve 改ざん・当たり判定崩壊・コールバック偽装の検出と迂回
        try {
            if (com.automatavalkyries.protect.EprotectSuperSteveCounter.counter(entity)) {
                recordBypass(entity.getClass().getName(), "Eprotect+", "superSteveCounter", "SuperSteveの改ざん・判定崩壊を修復");
            }
        } catch (Throwable ignored) {}

        // 4. Pig2Mod 抹殺追跡リストの検出と迂回
        try {
            if (com.automatavalkyries.protect.EprotectPig2Counter.counter(entity)) {
                recordBypass(entity.getClass().getName(), "Eprotect+", "pig2Counter", "Pig2Modの抹殺追跡リストから除外");
            }
        } catch (Throwable ignored) {}

        // 5. Nekohashizuku Blade HEALTH_MAP 改ざんの検出と迂回
        try {
            if (com.automatavalkyries.protect.EprotectNekohashizukuCounter.counter(entity)) {
                recordBypass(entity.getClass().getName(), "Eprotect+", "nekohashizukuCounter", "Nekohashizuku HEALTH_MAPから除外");
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 準汎用 Eprotect+ 防御:
     * <ol>
     *   <li>保護対象の生の UUID（{@code Entity.uuid} / {@code stringUUID}）が外部から {@code Unsafe} で
     *       別人の UUID にすり替えられた場合、記憶しておいた正規 UUID に復元する。</li>
     *   <li>Mixin 注入フィールドで負数の HP 減少デルタ（{@code healthDelta} 等）が書き込まれていれば 0 に戻す。</li>
     *   <li>強化保護中または死亡試行を受けている最中、保護対象への参照を握っている外部 MOD の別スレッド／ラムダ式を切断する。</li>
     * </ol>
     */
    private static void inspectAndBypassGenericInterference(LivingEntity entity) {
        // 0. 外部 Java エージェントのフッククラス群から KillLists（HEALTH_MAP 等）を定期自動発見（5秒間隔）
        long nowNs = System.nanoTime();
        if (nowNs - lastAgentHookDiscoveryNs > java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) {
            lastAgentHookDiscoveryNs = nowNs;
            try {
                for (Class<?> agentClass : findForeignAgentAndHiddenClasses()) {
                    if (agentClass != null && UnsafeHelper.isInitialized(agentClass)) {
                        com.automatavalkyries.protect.flag.KillLists.discoverFrom(agentClass);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 1. 保護対象の生 UUID すり替え（Identity Spoofing）の自己修復
        try {
            UUID rawUuid = MadnessAnnihilation.uuid(entity);
            UUID expectedUuid = PROTECTED_TRUE_UUID.computeIfAbsent(entity, e -> rawUuid);
            if (expectedUuid != null && rawUuid != null && !expectedUuid.equals(rawUuid)) {
                entity.setUUID(expectedUuid);
                recordProtection(entity.getClass().getName(), "Eprotect+", "uuid_restored",
                        "Restored tampered UUID " + rawUuid + " -> " + expectedUuid);
            }
        } catch (Throwable ignored) {}

        // 2. バニラ物理・コールバック構造（levelCallback / bb / noPhysics）のスナップショット自己修復
        try {
            EntityAccessor accessor = (EntityAccessor) entity;
            net.minecraft.world.level.entity.EntityInLevelCallback currentCb = accessor.automata_valkyries$getLevelCallback();
            if (currentCb != null && currentCb != net.minecraft.world.level.entity.EntityInLevelCallback.NULL) {
                PROTECTED_LEVEL_CALLBACK.put(entity, currentCb);
            } else if (entity.level() instanceof net.minecraft.server.level.ServerLevel) {
                net.minecraft.world.level.entity.EntityInLevelCallback savedCb = PROTECTED_LEVEL_CALLBACK.get(entity);
                if (savedCb != null && savedCb != net.minecraft.world.level.entity.EntityInLevelCallback.NULL) {
                    accessor.automata_valkyries$setLevelCallback(savedCb);
                    recordProtection(entity.getClass().getName(), "Eprotect+", "level_callback_restored",
                            "Restored severed EntityInLevelCallback from NULL");
                }
            }
        } catch (Throwable ignored) {}

        try {
            net.minecraft.world.phys.AABB currentBb = entity.getBoundingBox();
            if (currentBb == null
                    || currentBb.getXsize() < 0.001D
                    || currentBb.getYsize() < 0.001D
                    || currentBb.getZsize() < 0.001D) {
                entity.refreshDimensions();
                net.minecraft.world.phys.AABB refreshed = entity.getBoundingBox();
                if (refreshed == null
                        || refreshed.getXsize() < 0.001D
                        || refreshed.getYsize() < 0.001D
                        || refreshed.getZsize() < 0.001D) {
                    entity.setBoundingBox(entity.getType().getDimensions().makeBoundingBox(entity.position()));
                }
                recordProtection(entity.getClass().getName(), "Eprotect+", "bounding_box_restored",
                        "Restored collapsed 0-volume BoundingBox");
            }
            if (entity.noPhysics && !entity.isSpectator()) {
                entity.noPhysics = false;
                recordProtection(entity.getClass().getName(), "Eprotect+", "nophysics_restored",
                        "Restored tampered noPhysics=true -> false");
            }
        } catch (Throwable ignored) {}

        // 3. Mixin 等で注入された負数デルタフィールドの汎用中和
        try {
            for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (InjectedMembers.InjectedField injected : InjectedMembers.fields(c)) {
                    if (CallerAttribution.SELF.equals(injected.mod())) continue;
                    Field f = injected.field();
                    if (f.getType() != float.class && f.getType() != double.class) continue;
                    String lower = f.getName().toLowerCase(Locale.ROOT);
                    if (lower.contains("delta") || FlagDictionary.is(f.getName(), FlagDictionary.Category.HEALTH_TAMPER)) {
                        f.setAccessible(true);
                        double val = f.getType() == float.class ? f.getFloat(entity) : f.getDouble(entity);
                        if (val < 0.0D) {
                            if (f.getType() == float.class) f.setFloat(entity, 0.0F);
                            else f.setDouble(entity, 0.0D);
                            recordBypass(entity.getClass().getName(), injected.mod(), f.getName(),
                                    "Neutralized negative health delta: " + val);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 強化保護中または死亡演出強制中に、保護対象を直接狙う外部スレッド／ラムダがあれば参照を切断
        if (isReinforcedActive() || entity.deathTime > 0) {
            try {
                UUID id = entity.getUUID();
                com.automatavalkyries.annihilate.ModProtectionBreaker.Context ctx =
                        new com.automatavalkyries.annihilate.ModProtectionBreaker.Context(
                                List.of(), Set.of(), id != null ? Set.of(id) : Set.of());
                int severed = com.automatavalkyries.annihilate.ModProtectionBreaker.severForeignThreadReferences(entity, ctx);
                if (severed > 0) {
                    recordProtection(entity.getClass().getName(), "Eprotect+", "sever_attack_threads",
                            "Severed " + severed + " foreign background task(s) targeting protected entity");
                }
            } catch (Throwable ignored) {}
        }
    }

    // ---- 真抹殺 (True Annihilation) ------------------------------------------

    /** 後続の抹殺パイプラインに渡す対象限定の前処理。エージェント有無で同じ処理を使う。 */
    public static Map<String, String> prepareAnnihilation(Entity target) {
        boolean isForeignPlayerSubclass = target instanceof Player
                && CallerAttribution.isForeign(CallerAttribution.modOf(target.getClass()));
        if (target == null || (target instanceof Player && !isForeignPlayerSubclass)
                || (target instanceof com.automatavalkyries.protect.IEprotected protectedEntity
                && !protectedEntity.isTerminusTestTarget())) {
            return Map.of("status", "unsupported", "reason", "excluded_target");
        }
        if (!(target.level() instanceof net.minecraft.server.level.ServerLevel level) || !level.getServer().isSameThread()) {
            return Map.of("status", "unsupported", "reason", "server_thread_required");
        }
        try {
            runPrivileged(() -> {
                com.automatavalkyries.annihilate.ModProtectionBreaker.breakProtection(level, target);
                if (target instanceof LivingEntity living) living.setHealth(0.0F);
            });
            // Preparation is not proof of death/removal. The caller verifies the final pipeline.
            return Map.of("status", "attempted", "reason", "target_prepared");
        } catch (RuntimeException exception) {
            recordLocal("実行失敗", target.getClass().getName(), "Eprotect+", "prepare",
                    "failed", exception.toString());
            return Map.of("status", "failed", "reason", exception.toString());
        }
    }

    /** 互換API。true はサーバーで個体の消去が確認できた場合だけ返す。 */
    public static boolean annihilateTargetWithAgent(Entity target) {
        if (!"attempted".equals(prepareAnnihilation(target).get("status"))) return false;
        return isAnnihilationConfirmed(target);
    }

    public static boolean isAnnihilationConfirmed(Entity target) {
        if (target == null || !(target.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        UUID rawUuid = MadnessAnnihilation.uuid(target);
        int rawId = MadnessAnnihilation.id(target);
        return com.automatavalkyries.protect.DirectRead.isRemoved(target)
                && level.getEntity(rawUuid) != target && level.getEntity(rawId) != target;
    }

    // ---- 状態・イベント取得 ---------------------------------------------------

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> getEvents() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "getEvents");
                Object res = isolateForeignCallbacksAndCall(() -> m.invoke(null));
                if (res instanceof List<?> list) return (List<Map<String, String>>) list;
            } catch (Throwable ignored) {}
        }

        synchronized (LOCAL_EVENTS) {
            return new ArrayList<>(LOCAL_EVENTS);
        }
    }

    public static void clearEvents() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "clearEvents");
                m.invoke(null);
            } catch (Throwable ignored) {}
        }
        synchronized (LOCAL_EVENTS) {
            LOCAL_EVENTS.clear();
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getStats() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "getStats");
                Object res = isolateForeignCallbacksAndCall(() -> m.invoke(null));
                if (res instanceof Map<?, ?> map) return (Map<String, String>) map;
            } catch (Throwable ignored) {}
        }

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("active", "false");
        stats.put("agentState", "INACTIVE");
        stats.put("gamePreparation", "available");
        stats.put("lateAttached", "false");
        stats.put("instrumentation", "false");
        stats.put("canRetransform", "false");
        stats.put("interferences", String.valueOf(LOCAL_INTERFERENCES.get()));
        stats.put("bypasses", String.valueOf(LOCAL_BYPASSES.get()));
        stats.put("protections", String.valueOf(LOCAL_PROTECTIONS.get()));
        stats.put("annihilations", String.valueOf(LOCAL_ANNIHILATIONS.get()));
        synchronized (LOCAL_EVENTS) { stats.put("events", String.valueOf(LOCAL_EVENTS.size())); }
        return stats;
    }

    /**
     * 外部 MOD のキル管理リスト・消滅リスト（No-Sugar の erasedUuids 等、および {@link KillLists} で検出された汎用一覧）から
     * プレイヤーの UUID を安全に削除し、リスポーンや再参加を正常化する。
     */
    public static void clearPlayerFromKillModLists(UUID uuid) {
        if (uuid == null) return;

        // 1. 準汎用: KillLists に登録されている全ての外部抹殺リストから UUID をパージ（常時有効）
        try {
            List<Field> fields = new ArrayList<>(KillLists.lists());
            if (!fields.isEmpty()) {
                StaticReferencePurger.purgeFields(fields, Set.of(), uuid, uuid.toString());
            }
        } catch (Throwable ignored) {}

        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;

        // 2. 個別対応（エージェント未接続時でもリフレクションで直接除去）
        for (String className : new String[]{
                "com.test.nosugar.mixin.sugar_sword.LivingEntityMixin",
                "com.test.nosugar.utils.EraseUtils",
                "com.test.nosugar.utils.entity.LivingEntityUtils",
                "net.eca.util.health.DelayedHealthVerifier",
                "net.eca.util.EntityRemovalQuarantine"
        }) {
            try {
                Class<?> cls = Class.forName(className);
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        if (Set.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            Set<Object> set = (Set<Object>) f.get(null);
                            if (set != null) set.remove(uuid);
                        } else if (Map.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            Map<Object, Object> map = (Map<Object, Object>) f.get(null);
                            if (map != null) {
                                map.remove(uuid);
                                map.keySet().removeIf(k -> k instanceof Entity e && e.getUUID().equals(uuid));
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // IMDS クリーンアップ
        try {
            Class<?> km = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
            for (String fieldName : new String[]{"FORCE_DEAD_UUIDS", "REMOVED_UUIDS"}) {
                try {
                    Field f = km.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Set<Object> set = (Set<Object>) f.get(null);
                    if (set != null) set.remove(uuid);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // Pig2Mod gKilledEnemyUUIDs クリーンアップ
        try {
            Class<?> pig2 = Class.forName("kakiku.pig2mod.entity.Pig2");
            Field f = pig2.getDeclaredField("gKilledEnemyUUIDs");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<Object> set = (Set<Object>) f.get(null);
            if (set != null) set.remove(uuid);
        } catch (Throwable ignored) {}

        // Nekohashizuku HEALTH_MAP クリーンアップ
        try {
            Class<?> shizuku = Class.forName("com.buwan.shizuku.core.AgentHealthMethodHelper");
            Field f = shizuku.getDeclaredField("HEALTH_MAP");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) f.get(null);
            if (map != null) {
                map.keySet().removeIf(k -> {
                    try {
                        Method getUuid = k.getClass().getMethod("getUUID");
                        return uuid.equals(getUuid.invoke(k));
                    } catch (Throwable ignored) {
                        return false;
                    }
                });
            }
        } catch (Throwable ignored) {}
    }
}
