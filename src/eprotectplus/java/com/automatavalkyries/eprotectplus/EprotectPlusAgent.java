package com.automatavalkyries.eprotectplus;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final Map<Class<?>, Boolean> AUDIT_UNSUPPORTED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static final BaselineStore BASELINE_BYTECODE = new BaselineStore();
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
        start(inst, false);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(inst, true);
    }

    private static synchronized void start(Instrumentation inst, boolean lateAttach) {
        if (instrumentation != null) return;
        EprotectPlusBridge.init(lateAttach);
        try {
            boolean capable = inst.isRetransformClassesSupported();
            inst.addTransformer(TRANSFORMER, capable);
            instrumentation = inst;
            state = capable ? "ACTIVE" : "DEGRADED";
            EprotectPlusBridge.setActive(true);
            com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent.start(inst, lateAttach);
            if (capable) {
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
        } catch (Throwable t) {
            state = "DEGRADED";
            EprotectPlusBridge.setActive(instrumentation != null);
            ERRORS.incrementAndGet();
            EprotectPlusBridge.recordEvent("エラー", "EprotectPlusAgent", "JVM-Agent", "start", "失敗", t.toString());
        }
    }

    public static Map<String, String> getAuditStats() {
        return Map.of("agentState", state, "auditRequests", Long.toString(REQUESTS.get()),
                "auditTransformed", Long.toString(TRANSFORMED.get()), "baselineReturns", Long.toString(RESTORED.get()),
                "auditErrors", Long.toString(ERRORS.get()), "lastAuditNs", Long.toString(lastAuditDurationNs),
                "auditUnsupported", Integer.toString(AUDIT_UNSUPPORTED.size()));
    }

    public static boolean hasInstrumentation() {
        return instrumentation != null;
    }

    public static boolean canRetransform() {
        return instrumentation != null && instrumentation.isRetransformClassesSupported();
    }

    // =========================================================================
    // バイトコード監視・調査・保護
    // =========================================================================

    private static byte[] handleTransform(ClassLoader loader, String className, Class<?> redefined, byte[] bytes) {
        if (!isCriticalClass(className)) return null;
        if (redefined == null) {
            BASELINE_BYTECODE.capture(loader, className, bytes);
            return null;
        }
        byte[] baseline = BASELINE_BYTECODE.get(loader, className);
        if (baseline == null || java.util.Arrays.equals(baseline, bytes)) return null;
        String caller = extractCaller(Thread.currentThread().getStackTrace());
        boolean foreign = isForeignCaller(caller);
        List<String> neuteredMethods = new ArrayList<>();
        try {
            ClassSummary before = ClassSummary.parse(baseline);
            ClassSummary now = ClassSummary.parse(bytes);
            for (var entry : now.methods.entrySet()) {
                var old = before.methods.get(entry.getKey());
                if (old != null && entry.getValue().trivialReturn() && !old.trivialReturn())
                    neuteredMethods.add(entry.getKey());
            }
        } catch (java.io.IOException | RuntimeException exception) {
            ERRORS.incrementAndGet();
        }
        if (!foreign && neuteredMethods.isEmpty()) return null;
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
        REQUESTS.incrementAndGet();
        if (!canRetransform()) return 0;
        DIRTY.set(true);
        if (!AUDITING.compareAndSet(false, true)) return 0;
        long started = System.nanoTime();
        boolean performed = false;
        try {
            if (lastAuditNs != 0 && started - lastAuditNs < AUDIT_INTERVAL_NS) return 0;
            performed = true;
            lastAuditNs = started;
            DIRTY.set(false);
            Map<String, List<Class<?>>> groups = new java.util.LinkedHashMap<>();
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                String name = type.getName().replace('.', '/');
                if (isCriticalClass(name) && instrumentation.isModifiableClass(type)
                        && !name.startsWith("com/automatavalkyries/mixin/")
                        && !AUDIT_UNSUPPORTED.containsKey(type)
                        && BASELINE_BYTECODE.contains(type.getClassLoader(), name)) {
                    String group = name.startsWith("net/minecraft/") ? "minecraft-entity"
                            : name.substring(0, name.lastIndexOf('/'));
                    groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(type);
                }
            }
            int transformed = 0;
            for (var group : groups.entrySet()) {
                try {
                    instrumentation.retransformClasses(group.getValue().toArray(Class<?>[]::new));
                    transformed += group.getValue().size();
                } catch (ClassFormatError invalidGroup) {
                    // The JVM rejected this unchanged-schema group atomically. Diagnose once,
                    // then skip only classes whose individual retransformation is unsupported.
                    for (Class<?> type : group.getValue()) {
                        try {
                            instrumentation.retransformClasses(type);
                            transformed++;
                        } catch (Throwable exception) {
                            AUDIT_UNSUPPORTED.put(type, Boolean.TRUE);
                            ERRORS.incrementAndGet();
                            EprotectPlusBridge.recordEvent("警告", type.getName(), "EprotectPlusAgent", "active_retransform",
                                    "能動的監査非対応", exception.toString());
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
