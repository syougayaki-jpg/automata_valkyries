package com.automatavalkyries.eprotectplus;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Instrumentation and transformer-input protection. Game cleanup stays in the MOD.
 * Saved input is not guaranteed to be pristine or the final installed bytecode.
 */
public final class EprotectPlusAgent {

    private static volatile Instrumentation instrumentation;
    private static volatile String state = "INACTIVE";
    private static final java.util.concurrent.atomic.AtomicBoolean AUDITING = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean DIRTY = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicLong REQUESTS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong TRANSFORMED = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong RESTORED = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong ERRORS = new java.util.concurrent.atomic.AtomicLong();
    private static volatile long lastAuditNs;
    private static volatile long lastAuditDurationNs;
    private static final long AUDIT_INTERVAL_NS = java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
    private static final long URGENT_AUDIT_INTERVAL_NS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    private static final Map<Class<?>, Boolean> AUDIT_UNSUPPORTED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 案 A / D: 外部クラスの定数プール参照先キャッシュと Runtime.halt / System.exit 自爆トラップ保持クラス集合 */
    private static final Map<String, List<String>> FOREIGN_CLASS_REFS = new ConcurrentHashMap<>();
    private static final Set<String> FOREIGN_HALT_TRAPS = ConcurrentHashMap.newKeySet();
    /** 対 Java エージェント汎用対策 ①: バニラ重要クラスのバイトコードに注入された外部エージェントフッククラス名（ドット区切り） */
    private static final Set<String> VANILLA_INJECTED_HOOK_CLASSES = ConcurrentHashMap.newKeySet();

    private static final BaselineStore BASELINE_BYTECODE = new BaselineStore();
    private static final ThreadLocal<Boolean> OBSERVING_LATE_INPUT = ThreadLocal.withInitial(() -> false);

    static {
        HiddenAgentVault.storeBaseline(BASELINE_BYTECODE);
    }

    private static Instrumentation inst() {
        Instrumentation current = instrumentation;
        if (current != null) return current;
        Instrumentation vaulted = HiddenAgentVault.loadInstrumentation();
        if (vaulted != null) instrumentation = vaulted;
        return vaulted;
    }

    private static BaselineStore baseline() {
        BaselineStore current = BASELINE_BYTECODE;
        if (current != null) return current;
        return HiddenAgentVault.loadBaseline();
    }

    private static final String[] CRITICAL_AREAS = {
            "com/automatavalkyries/protect/", "com/automatavalkyries/entity/",
            "com/automatavalkyries/annihilate/", "com/automatavalkyries/mixin/",
            "com/automatavalkyries/weapon/", "com/automatavalkyries/util/"
    };

    private static final ClassFileTransformer TRANSFORMER = new ClassFileTransformer() {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (className == null) return null;
            try {
                return handleTransform(loader, className, classBeingRedefined, classfileBuffer);
            } catch (Throwable t) {
                ERRORS.incrementAndGet();
                return null;
            }
        }
    };

    private EprotectPlusAgent() {}

    public static void premain(String args, Instrumentation inst) {
        start(inst, false, args);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(inst, true, args);
    }

    private static synchronized void start(Instrumentation inst, boolean lateAttach, String args) {
        if (instrumentation != null) return;
        EprotectPlusBridge.init(lateAttach);
        try {
            AgentCollisionPolicy.configure(args);
            boolean capable = inst.isRetransformClassesSupported();
            if (!AgentCollisionPolicy.isPassive()) inst.addTransformer(TRANSFORMER, capable);
            instrumentation = inst;
            HiddenAgentVault.storeInstrumentation(inst);
            state = AgentCollisionPolicy.isPassive() ? "PASSIVE" : capable ? "ACTIVE" : "DEGRADED";
            EprotectPlusBridge.setActive(true);
            if (args == null || !java.util.Arrays.asList(args.split(",")).contains("flagDictionary=false")) {
                com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent.start(inst, lateAttach, args);
            }
            if (capable && !AgentCollisionPolicy.isPassive()) {
                var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "EprotectPlus-audit");
                    thread.setDaemon(true);
                    return thread;
                });
                scheduler.scheduleWithFixedDelay(() -> {
                    if (DIRTY.get()) retransformCriticalClasses();
                }, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
            }
            EprotectPlusBridge.recordEvent("初期化", "EprotectPlusAgent", "JVM-Agent", "start", state,
                    "CanRetransform=" + capable + ", lateAttach=" + lateAttach);
            if (lateAttach && capable && !AgentCollisionPolicy.isPassive()) {
                OBSERVING_LATE_INPUT.set(true);
                try { retransformCriticalClasses(); }
                finally { OBSERVING_LATE_INPUT.remove(); }
                EprotectPlusBridge.recordEvent("監査", "JVM", "EprotectPlusAgent", "late_attach_input",
                        "現在の入力を観測", "Already-loaded classes are observed after attachment; pristine input is not guaranteed");
            }
        } catch (Throwable t) {
            state = "DEGRADED";
            EprotectPlusBridge.setActive(instrumentation != null);
            ERRORS.incrementAndGet();
            EprotectPlusBridge.recordEvent("エラー", "EprotectPlusAgent", "JVM-Agent", "start", "失敗", t.toString());
        }
    }

    public static Map<String, String> getAuditStats() {
        String reportedState = AgentCollisionPolicy.isPassive() && "ACTIVE".equals(state)
                ? "PASSIVE_COLLISION_LIMIT" : state;
        return Map.of("agentState", reportedState, "auditRequests", Long.toString(REQUESTS.get()),
                "auditTransformed", Long.toString(TRANSFORMED.get()), "baselineReturns", Long.toString(RESTORED.get()),
                "auditErrors", Long.toString(ERRORS.get()), "lastAuditNs", Long.toString(lastAuditDurationNs),
                "auditUnsupported", Integer.toString(AUDIT_UNSUPPORTED.size()),
                "collisionLimit", Integer.toString(AgentCollisionPolicy.limit()),
                "collisionReplacements", Integer.toString(AgentCollisionPolicy.replacementCount()),
                "collisionPassive", Boolean.toString(AgentCollisionPolicy.isPassive()));
    }

    public static boolean hasInstrumentation() {
        return inst() != null;
    }

    /**
     * 攻撃時のエージェント隔離用: 他のエージェントや変換器には一切干渉せず、
     * {@link Instrumentation#getAllLoadedClasses()} から指定パッケージ接頭辞のロード済みクラス
     * （{@code Unsafe.defineClass} で隠蔽定義された未参照クラスを含む）を逆引きする。
     */
    public static List<Class<?>> findLoadedClassesByPrefix(String packagePrefix, int limit) {
        Instrumentation current = inst();
        if (current == null || packagePrefix == null || packagePrefix.isEmpty() || limit <= 0) {
            return List.of();
        }
        if (packagePrefix.startsWith("java.") || packagePrefix.startsWith("jdk.")
                || packagePrefix.startsWith("sun.")
                || (packagePrefix.startsWith("com.automatavalkyries.")
                && !packagePrefix.startsWith("com.automatavalkyries.gametest.fake."))) {
            return List.of();
        }
        List<Class<?>> found = new ArrayList<>();
        try {
            for (Class<?> loaded : current.getAllLoadedClasses()) {
                if (loaded == null) continue;
                String name = loaded.getName();
                if (name.startsWith(packagePrefix)) {
                    found.add(loaded);
                    if (found.size() >= limit) break;
                }
            }
        } catch (Throwable ignored) {}
        return found;
    }

    /**
     * 案 B: 同一 MOD JAR（{@link CodeSource}）またはパッケージ接頭辞を共有する全ロード済みクラスを抽出する。
     * パッケージ名を偽装して別パッケージに隠された管理クラスも同一 CodeSource から捕捉する。
     */
    public static List<Class<?>> findLoadedClassesByOwner(Class<?> referenceClass, String packagePrefix, int limit) {
        Instrumentation current = inst();
        if (current == null || referenceClass == null || limit <= 0) {
            return List.of();
        }
        URL referenceLocation = codeSourceLocation(referenceClass);
        boolean validPrefix = packagePrefix != null && !packagePrefix.isEmpty()
                && !packagePrefix.startsWith("java.") && !packagePrefix.startsWith("jdk.")
                && !packagePrefix.startsWith("sun.")
                && (!packagePrefix.startsWith("com.automatavalkyries.")
                || packagePrefix.startsWith("com.automatavalkyries.gametest.fake."));
        List<Class<?>> found = new ArrayList<>();
        try {
            for (Class<?> loaded : current.getAllLoadedClasses()) {
                if (loaded == null) continue;
                String name = loaded.getName();
                if (!isInspectableForeignClassName(name.replace('.', '/'))) continue;
                boolean match = validPrefix && name.startsWith(packagePrefix);
                if (!match && referenceLocation != null && !name.startsWith("com.automatavalkyries.")) {
                    URL loc = codeSourceLocation(loaded);
                    if (loc != null && referenceLocation.equals(loc)) {
                        match = true;
                    }
                }
                if (match) {
                    found.add(loaded);
                    if (found.size() >= limit) break;
                }
            }
        } catch (Throwable ignored) {}
        return found;
    }

    private static URL codeSourceLocation(Class<?> type) {
        try {
            ProtectionDomain pd = type.getProtectionDomain();
            if (pd == null) return null;
            CodeSource cs = pd.getCodeSource();
            return cs != null ? cs.getLocation() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 案 A: エージェントが観測・キャッシュした定数プール参照先クラス名一覧（ドット区切り）を返す。
     */
    public static List<String> getReferencedClasses(Class<?> targetClass) {
        if (targetClass == null) return List.of();
        String internalName = targetClass.getName().replace('.', '/');
        List<String> cached = FOREIGN_CLASS_REFS.get(internalName);
        if (cached != null) return cached;
        ClassSummary summary = parseFromClassResource(targetClass);
        if (summary != null) {
            cacheForeignSummary(internalName, summary);
            List<String> after = FOREIGN_CLASS_REFS.get(internalName);
            if (after != null) return after;
        }
        return List.of();
    }

    /**
     * 案 D: 対象クラスのバイトコード定数プールに {@code Runtime.halt} / {@code System.exit} 等の
     * 自爆トラップ呼び出しが含まれているか判定する。
     */
    public static boolean hasHaltOrExitTrap(Class<?> targetClass) {
        if (targetClass == null) return false;
        String internalName = targetClass.getName().replace('.', '/');
        if (FOREIGN_HALT_TRAPS.contains(internalName)) return true;
        if (!FOREIGN_CLASS_REFS.containsKey(internalName)) {
            ClassSummary summary = parseFromClassResource(targetClass);
            if (summary != null) {
                cacheForeignSummary(internalName, summary);
            }
        }
        return FOREIGN_HALT_TRAPS.contains(internalName);
    }

    /**
     * 対 Java エージェント汎用対策 ①: バニラ重要クラスの変換後バイトコードから逆引きされた外部エージェントフッククラス名一覧を返す。
     */
    public static List<String> getVanillaInjectedHookClasses() {
        return List.copyOf(VANILLA_INJECTED_HOOK_CLASSES);
    }

    /**
     * 対 Java エージェント汎用対策 ②: {@link Instrumentation#getAllLoadedClasses()} から、
     * <ul>
     *   <li>Bootstrap ClassLoader（{@code getClassLoader() == null}）に注入された外部エージェントクラス</li>
     *   <li>{@code defineHiddenClass} / {@code defineAnonymousClass} で定義された外部 Hidden クラス（{@code isHidden()}）</li>
     *   <li>外部の {@link ClassFileTransformer} 実装クラスおよびそれと同一 JAR / 同一パッケージの補助クラス</li>
     *   <li>バニラ重要クラスのバイトコードから直接参照されている外部エージェントフッククラス</li>
     *   <li>クラス名に Agent / Transformer / AgtCallback / MethodHelper 等を含む外部エージェント関連クラス</li>
     * </ul>
     * を一括抽出する。
     */
    public static List<Class<?>> findForeignAgentAndHiddenClasses(int limit) {
        Instrumentation current = inst();
        if (current == null || limit <= 0) return List.of();
        List<Class<?>> result = new ArrayList<>();
        Set<URL> agentCodeSources = new java.util.HashSet<>();
        Set<String> agentPrefixes = new java.util.HashSet<>();
        try {
            Class<?>[] allLoaded = current.getAllLoadedClasses();
            for (Class<?> loaded : allLoaded) {
                if (loaded == null) continue;
                String name = loaded.getName();
                String internalName = name.replace('.', '/');
                if (!isInspectableForeignClassName(internalName)) continue;
                if (ClassFileTransformer.class.isAssignableFrom(loaded)) {
                    URL loc = codeSourceLocation(loaded);
                    if (loc != null) agentCodeSources.add(loc);
                    String prefix = packagePrefixOf(name);
                    if (!prefix.isEmpty()) agentPrefixes.add(prefix);
                }
            }
            for (Class<?> loaded : allLoaded) {
                if (loaded == null) continue;
                String name = loaded.getName();
                String internalName = name.replace('.', '/');
                if (!isInspectableForeignClassName(internalName)) continue;
                boolean match = loaded.getClassLoader() == null
                        || loaded.isHidden()
                        || ClassFileTransformer.class.isAssignableFrom(loaded)
                        || VANILLA_INJECTED_HOOK_CLASSES.contains(name)
                        || isAgentOrHookClassName(loaded.getSimpleName());
                if (!match && !agentPrefixes.isEmpty()) {
                    for (String prefix : agentPrefixes) {
                        if (name.startsWith(prefix)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (!match && !agentCodeSources.isEmpty() && !name.startsWith("com.automatavalkyries.")) {
                    URL loc = codeSourceLocation(loaded);
                    if (loc != null && agentCodeSources.contains(loc)) {
                        match = true;
                    }
                }
                if (match) {
                    result.add(loaded);
                    if (result.size() >= limit) break;
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static boolean isAgentOrHookClassName(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) return false;
        String lower = simpleName.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("agent") || lower.contains("transformer") || lower.contains("agtcallback")
                || lower.contains("methodhelper") || lower.contains("instrument") || lower.contains("bytecodehook");
    }

    private static String packagePrefixOf(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot <= 0) return "";
        String pkg = className.substring(0, lastDot);
        String[] parts = pkg.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".";
        }
        return pkg + ".";
    }

    private static void inspectVanillaBytecodeHooks(String className, byte[] bytes) {
        if (bytes == null || !isVanillaHookTargetClass(className)) return;
        try {
            ClassSummary summary = ClassSummary.parse(bytes);
            for (String refClass : summary.referencedClasses) {
                if (isInspectableForeignClassName(refClass) && VANILLA_INJECTED_HOOK_CLASSES.size() < 512) {
                    VANILLA_INJECTED_HOOK_CLASSES.add(refClass.replace('/', '.'));
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isVanillaHookTargetClass(String className) {
        return "net/minecraft/world/entity/Entity".equals(className)
                || "net/minecraft/world/entity/LivingEntity".equals(className)
                || "net/minecraft/world/entity/Mob".equals(className)
                || "net/minecraft/world/entity/player/Player".equals(className)
                || "net/minecraft/server/level/ServerPlayer".equals(className)
                || "net/minecraft/server/level/ServerLevel".equals(className)
                || "net/minecraft/network/syncher/SynchedEntityData".equals(className);
    }

    private static ClassSummary parseFromClassResource(Class<?> targetClass) {
        String resource = targetClass.getName().replace('.', '/') + ".class";
        ClassLoader loader = targetClass.getClassLoader();
        try (InputStream in = loader != null
                ? loader.getResourceAsStream(resource)
                : ClassLoader.getSystemResourceAsStream(resource)) {
            if (in == null) return null;
            return ClassSummary.parse(in.readAllBytes());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void cacheForeignSummary(String internalName, ClassSummary summary) {
        if (summary == null || internalName == null) return;
        if (summary.hasHaltOrExitTrap()) {
            FOREIGN_HALT_TRAPS.add(internalName);
        }
        if (FOREIGN_CLASS_REFS.size() < 4096) {
            List<String> dotNames = new ArrayList<>();
            for (String refClass : summary.referencedClasses) {
                if (isInspectableForeignClassName(refClass)) {
                    dotNames.add(refClass.replace('/', '.'));
                }
            }
            FOREIGN_CLASS_REFS.put(internalName, List.copyOf(dotNames));
        }
    }

    private static boolean isInspectableForeignClassName(String internalName) {
        if (internalName == null || internalName.isEmpty()) return false;
        if (internalName.startsWith("java/") || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/") || internalName.startsWith("sun/")
                || internalName.startsWith("com/mojang/") || internalName.startsWith("net/minecraftforge/")
                || internalName.startsWith("cpw/mods/") || internalName.startsWith("org/spongepowered/")
                || internalName.startsWith("org/objectweb/") || internalName.startsWith("org/slf4j/")
                || internalName.startsWith("org/apache/") || internalName.startsWith("com/google/")
                || internalName.startsWith("io/netty/") || internalName.startsWith("it/unimi/")) {
            return false;
        }
        if (internalName.startsWith("net/minecraft/") && !internalName.startsWith("net/minecraft/madcrazy/")) {
            return false;
        }
        if (internalName.startsWith("com/automatavalkyries/")
                && !internalName.startsWith("com/automatavalkyries/gametest/fake/")) {
            return false;
        }
        return true;
    }

    public static boolean canRetransform() {
        return inst() != null && !AgentCollisionPolicy.isPassive()
                && inst().isRetransformClassesSupported();
    }

    // =========================================================================
    // バイトコード監視・調査・保護
    // =========================================================================

    private static byte[] handleTransform(ClassLoader loader, String className, Class<?> redefined, byte[] bytes) {
        // 案 A / D: 外部クラスのバイトコードは一切書き換えず（不干渉）、定数プール参照先と自爆トラップの有無だけをパッシブ記録する
        if (bytes != null && isInspectableForeignClassName(className) && !FOREIGN_CLASS_REFS.containsKey(className)) {
            try {
                cacheForeignSummary(className, ClassSummary.parse(bytes));
            } catch (Throwable ignored) {}
        }
        // 対 Java エージェント汎用対策 ①: バニラ重要クラスのバイトコードに注入された外部フッククラスへの参照をパッシブ記録する
        inspectVanillaBytecodeHooks(className, bytes);
        if (AgentCollisionPolicy.isConfiguredPassive()) return null;
        if (!isCriticalClass(className)) return null;
        if (redefined == null) {
            baseline().capture(loader, className, bytes);
            return null;
        }
        byte[] baseline = baseline().get(loader, className);
        if (baseline == null) {
            if (OBSERVING_LATE_INPUT.get()) baseline().capture(loader, className, bytes);
            return null;
        }
        if (java.util.Arrays.equals(baseline, bytes)) return null;
        String caller = extractCaller(Thread.currentThread().getStackTrace());
        boolean foreign = isForeignCaller(caller);
        boolean ownCriticalClass = className.startsWith("com/automatavalkyries/");
        List<String> neuteredMethods = new ArrayList<>();
        boolean foreignHookInjected = false;
        try {
            ClassSummary before = ClassSummary.parse(baseline);
            ClassSummary now = ClassSummary.parse(bytes);
            for (var entry : now.methods.entrySet()) {
                var old = before.methods.get(entry.getKey());
                if (old != null && entry.getValue().trivialReturn() && !old.trivialReturn())
                    neuteredMethods.add(entry.getKey());
            }
            for (String ref : now.referencedClasses) {
                if (!before.referencedClasses.contains(ref) && isInspectableForeignClassName(ref)) {
                    foreignHookInjected = true;
                    break;
                }
            }
        } catch (java.io.IOException | RuntimeException exception) {
            ERRORS.incrementAndGet();
        }
        if (!foreign && !ownCriticalClass && !foreignHookInjected && neuteredMethods.isEmpty()) return null;
        if (!AgentCollisionPolicy.allowReplacement(className)) {
            state = "PASSIVE_COLLISION_LIMIT";
            return null;
        }
        state = "ACTIVE";
        DIRTY.set(true);
        EprotectPlusBridge.recordInterference(className, caller, "retransform/redefine", "ShortReturns=" + neuteredMethods);
        RESTORED.incrementAndGet();
        EprotectPlusBridge.recordProtection(className, caller, "baseline_returned",
                "Saved transformer input returned; later transformers and JVM acceptance are not observable here");
        return baseline;
    }

    /**
     * 保護対象および重要クラスのバイトコード整合性を能動的に検査し、
     * 要求をまとめて再変換する。成功件数は改ざんの復元件数ではない。
     */
    public static int retransformCriticalClasses() {
        return retransformCriticalClassesInternal(AUDIT_INTERVAL_NS);
    }

    /**
     * 強化保護突入時など緊急時の再変換（最小間隔 5 秒）。
     */
    public static int retransformCriticalClassesUrgent() {
        return retransformCriticalClassesInternal(URGENT_AUDIT_INTERVAL_NS);
    }

    private static int retransformCriticalClassesInternal(long minIntervalNs) {
        REQUESTS.incrementAndGet();
        if (AgentCollisionPolicy.isPassive()) return 0;
        if (!canRetransform()) return 0;
        DIRTY.set(true);
        if (!AUDITING.compareAndSet(false, true)) return 0;
        long started = System.nanoTime();
        boolean performed = false;
        try {
            if (lastAuditNs != 0 && started - lastAuditNs < minIntervalNs) return 0;
            performed = true;
            lastAuditNs = started;
            DIRTY.set(false);
            Map<String, List<Class<?>>> groups = new java.util.LinkedHashMap<>();
            Instrumentation currentInst = inst();
            BaselineStore store = baseline();
            if (currentInst == null || store == null) return 0;
            for (Class<?> type : currentInst.getAllLoadedClasses()) {
                String name = type.getName().replace('.', '/');
                if (isCriticalClass(name) && currentInst.isModifiableClass(type)
                        && !name.startsWith("com/automatavalkyries/mixin/")
                        && !AUDIT_UNSUPPORTED.containsKey(type)
                        && (OBSERVING_LATE_INPUT.get() || store.contains(type.getClassLoader(), name))) {
                    String group = name.startsWith("net/minecraft/") ? "minecraft-entity"
                            : name.substring(0, name.lastIndexOf('/'));
                    groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(type);
                }
            }
            int transformed = 0;
            for (var group : groups.entrySet()) {
                try {
                    currentInst.retransformClasses(group.getValue().toArray(Class<?>[]::new));
                    transformed += group.getValue().size();
                } catch (ClassFormatError invalidGroup) {
                    // The JVM rejected this unchanged-schema group atomically. Diagnose once,
                    // then skip only classes whose individual retransformation is unsupported.
                    for (Class<?> type : group.getValue()) {
                        try {
                            currentInst.retransformClasses(type);
                            transformed++;
                        } catch (Throwable exception) {
                            // 対エージェント仕掛け ③: retransformClasses（canRetransform=true パイプライン）が妨害・拒否された場合、
                            // redefineClasses（canRetransform=false 先行パイプライン＋保存済み baseline 直接供給）へ非対称フォールバックする
                            boolean redefinedOk = false;
                            if (currentInst.isRedefineClassesSupported()) {
                                byte[] savedBytes = store.get(type.getClassLoader(), type.getName().replace('.', '/'));
                                if (savedBytes != null) {
                                    try {
                                        currentInst.redefineClasses(new java.lang.instrument.ClassDefinition(type, savedBytes));
                                        transformed++;
                                        redefinedOk = true;
                                    } catch (Throwable ignored) {}
                                }
                            }
                            if (!redefinedOk) {
                                AUDIT_UNSUPPORTED.put(type, Boolean.TRUE);
                                ERRORS.incrementAndGet();
                                EprotectPlusBridge.recordEvent("警告", type.getName(), "EprotectPlusAgent", "active_retransform",
                                        "能動的監査非対応", exception.toString());
                            }
                        }
                    }
                } catch (Throwable exception) {
                    ERRORS.incrementAndGet();
                    EprotectPlusBridge.recordEvent("警告", group.getKey(), "EprotectPlusAgent", "active_retransform",
                            "グループ失敗", exception.toString() + "; classes=" + group.getValue().size());
                }
            }
            TRANSFORMED.addAndGet(transformed);
            EprotectPlusBridge.recordEvent("監査", "JVM", "EprotectPlusAgent", "active_retransform", "再変換完了",
                    "classes=" + transformed + ", elapsedNs=" + (System.nanoTime() - started)
                            + "; this is not a restoration count");
            return transformed;
        } catch (Throwable exception) {
            ERRORS.incrementAndGet();
            EprotectPlusBridge.recordEvent("警告", "JVM", "EprotectPlusAgent", "active_retransform", "失敗", exception.toString());
            return 0;
        } finally {
            if (performed) lastAuditDurationNs = System.nanoTime() - started;
            AUDITING.set(false);
        }
    }

    private static boolean isCriticalClass(String name) {
        if (name.startsWith("com/automatavalkyries/eprotectplus/")
                || name.startsWith("com/automatavalkyries/protect/flag/")) return false;
        for (String area : CRITICAL_AREAS)
            if (name.startsWith(area)) return true;
        return name.equals("net/minecraft/world/entity/Entity")
                || name.equals("net/minecraft/world/entity/LivingEntity")
                || name.equals("net/minecraft/world/entity/player/Player")
                || name.equals("net/minecraft/server/level/ServerPlayer");
    }

    private static String extractCaller(StackTraceElement[] stack) {
        for (StackTraceElement elem : stack) {
            String cn = elem.getClassName();
            if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")
                    || cn.startsWith("com.automatavalkyries.eprotectplus.")) {
                continue;
            }
            return cn + "." + elem.getMethodName() + ":" + elem.getLineNumber();
        }
        return "UnknownCaller";
    }

    private static boolean isForeignCaller(String caller) {
        // Unknown external agents receive the same policy as known external MODs.
        return !caller.startsWith("com.automatavalkyries.");
    }

    public static boolean isInternalTransformCall() {
        return !isForeignCaller(extractCaller(Thread.currentThread().getStackTrace()));
    }

    /** Compatibility API: game operations belong to the MOD/server thread. */
    @Deprecated
    public static boolean annihilateEntityJvm(Object entity) { return false; }

    @Deprecated
    public static void clearPlayerFromKillModLists(UUID uuid) {
        EprotectPlusBridge.recordEvent("unsupported", "EprotectPlusAgent", "legacy", "clearPlayer", "not_executed", "Use the MOD-side targeted cleanup");
    }
}
