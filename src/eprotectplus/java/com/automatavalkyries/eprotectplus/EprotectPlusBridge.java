package com.automatavalkyries.eprotectplus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Eprotect+ Java エージェントの記録・統計・MOD との橋渡し。
 * <p>
 * システムクラスローダーに常駐し、MOD 側からはリフレクションを介して呼び出される。
 * JDK 標準型のみで通信し、変換の監視・再監査・協調用の実行範囲・診断結果を提供する。
 * 記録は logs/automata_valkyries-eprotect-plus.log にも書き出される。
 * </p>
 */
public final class EprotectPlusBridge {

    private static final int MAX_EVENTS = 512;
    private static final Deque<Map<String, String>> EVENTS = new ArrayDeque<>();
    private static final AtomicLong INTERFERENCE_COUNT = new AtomicLong();
    private static final AtomicLong BYPASS_COUNT = new AtomicLong();
    private static final AtomicLong PROTECTION_COUNT = new AtomicLong();
    private static final AtomicLong ANNIHILATION_COUNT = new AtomicLong();

    private static volatile boolean active = false;
    private static volatile boolean lateAttached = false;
    private static final java.util.concurrent.ArrayBlockingQueue<String> LOG_QUEUE = new java.util.concurrent.ArrayBlockingQueue<>(1024);
    private static final AtomicLong DROPPED_LOGS = new AtomicLong();
    private static final AtomicLong LOG_ERRORS = new AtomicLong();
    private static volatile boolean loggingStarted;
    private static volatile boolean stopping;

    /** 協調用の実行範囲フラグ。JVM権限やスタックトレースを変更しない。 */
    private static final ThreadLocal<Boolean> PRIVILEGED_EXECUTION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 強化処理（Reinforced Protection）モード終了時刻（単調時計のナノ秒）と理由 */
    private static final AtomicLong REINFORCED_UNTIL_NS = new AtomicLong(System.nanoTime());
    private static volatile String REINFORCED_REASON = "";

    private EprotectPlusBridge() {}

    public static synchronized void init(boolean late) {
        lateAttached = late;
        if (loggingStarted) return;
        loggingStarted = true;
        Thread logger = new Thread(EprotectPlusBridge::writeLogs, "EprotectPlus-log");
        logger.setDaemon(true);
        logger.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping = true;
            logger.interrupt();
            try { logger.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }, "EprotectPlus-log-close"));
    }

    static void setActive(boolean value) { active = value; }

    private static void writeLogs() {
        Path path = Paths.get("logs", "automata_valkyries-eprotect-plus.log");
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter output = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                while (!stopping || !LOG_QUEUE.isEmpty()) {
                    String line;
                    try { line = LOG_QUEUE.poll(1, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException exception) { continue; }
                    if (line == null) continue;
                    output.write(line);
                    output.newLine();
                    for (int batch = 0; batch < 127 && (line = LOG_QUEUE.poll()) != null; batch++) {
                        output.write(line);
                        output.newLine();
                    }
                    output.flush();
                }
            }
        } catch (IOException exception) {
            LOG_ERRORS.incrementAndGet();
        }
    }

    public static synchronized void recordEvent(String kind, String target, String caller, String op, String action, String note) {
        Map<String, String> ev = new LinkedHashMap<>();
        ev.put("time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
        ev.put("kind", kind);
        ev.put("target", target != null ? target.replace('/', '.') : "-");
        ev.put("caller", caller != null ? caller : "-");
        ev.put("op", op != null ? op : "-");
        ev.put("action", action != null ? action : "-");
        ev.put("note", note != null ? note : "");

        EVENTS.addLast(ev);
        if (EVENTS.size() > MAX_EVENTS) EVENTS.removeFirst();

        if (!LOG_QUEUE.offer(String.join(" | ", ev.values()).replace('\n', ' ').replace('\r', ' ')))
            DROPPED_LOGS.incrementAndGet();
    }

    private static final Map<String, Long> LAST_INTERFERENCE_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long INTERFERENCE_THROTTLE_MS = 5000L;

    public static void recordInterference(String target, String caller, String op, String details) {
        INTERFERENCE_COUNT.incrementAndGet();
        String key = (target != null ? target : "") + "#" + (op != null ? op : "");
        long now = System.nanoTime();
        Long prev = LAST_INTERFERENCE_MS.get(key);
        if (prev != null && (now - prev) < java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(INTERFERENCE_THROTTLE_MS)) {
            return;
        }
        if (LAST_INTERFERENCE_MS.size() > 4096) LAST_INTERFERENCE_MS.clear();
        LAST_INTERFERENCE_MS.put(key, now);
        recordEvent("調査・干渉検知", target, caller, op, "検知・解析完了", details);
    }

    public static void recordBypass(String target, String caller, String op, String details) {
        BYPASS_COUNT.incrementAndGet();
        recordEvent("迂回執行", target, caller, op, "迂回成功", details);
    }

    public static void recordProtection(String target, String caller, String op, String details) {
        PROTECTION_COUNT.incrementAndGet();
        String key = "protection#" + target + "#" + caller + "#" + op;
        long now = System.nanoTime();
        synchronized (LAST_INTERFERENCE_MS) {
            Long previous = LAST_INTERFERENCE_MS.get(key);
            if (previous != null && now - previous < java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) return;
            if (LAST_INTERFERENCE_MS.size() > 4096) LAST_INTERFERENCE_MS.clear();
            LAST_INTERFERENCE_MS.put(key, now);
        }
        recordEvent("保護執行", target, caller, op, "改ざん拒否・復元", details);
    }

    public static void recordAnnihilation(String target, String caller, String op, String details) {
        ANNIHILATION_COUNT.incrementAndGet();
        recordEvent("真抹殺執行", target, caller, op, "完全消滅", details);
    }

    // ---- 特権実行（迂回フラグ制御） ---------------------------------------------

    public static boolean isPrivileged() {
        return Boolean.TRUE.equals(PRIVILEGED_EXECUTION.get());
    }

    public static void setPrivileged(boolean privileged) {
        if (privileged) PRIVILEGED_EXECUTION.set(true);
        else PRIVILEGED_EXECUTION.remove();
    }

    public static void runPrivileged(Runnable action) {
        java.util.Objects.requireNonNull(action, "action");
        boolean prev = PRIVILEGED_EXECUTION.get();
        PRIVILEGED_EXECUTION.set(true);
        try {
            action.run();
        } finally {
            setPrivileged(prev);
        }
    }

    /**
     * 互換API。特権フラグの範囲内で実行し、呼び出し履歴は隠さない。
     */
    public static void runCloaked(Runnable action) {
        // Compatibility name; the JVM stack is not hidden by this scope.
        runPrivileged(action);
    }

    /**
     * 保護対象および重要クラスの能動的バイトコード再監査・強制再変換（案2）。
     */
    public static int retransformCriticalClasses() {
        return EprotectPlusAgent.retransformCriticalClasses();
    }

    // ---- 強化処理（Reinforced Protection）制御 ---------------------------------

    public static void triggerReinforced(long durationMs, String reason) {
        long now = System.nanoTime();
        long durationNs = Math.min(Long.MAX_VALUE / 2,
                java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0, durationMs)));
        REINFORCED_UNTIL_NS.updateAndGet(previous -> previous - now > durationNs ? previous : now + durationNs);
        REINFORCED_REASON = reason != null ? reason : "UNKNOWN";
        recordEvent("強化", "EprotectPlusBridge", reason, "reinforced_mode", "発動",
                "強化処理発動（期間: " + durationMs + "ms, 理由: " + REINFORCED_REASON + "）");
    }

    public static boolean isReinforced() {
        return REINFORCED_UNTIL_NS.get() - System.nanoTime() > 0;
    }

    public static String getReinforcedReason() {
        return isReinforced() ? REINFORCED_REASON : "";
    }

    public static long getReinforcedRemainingMs() {
        long diff = REINFORCED_UNTIL_NS.get() - System.nanoTime();
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0, diff));
    }

    public static void resetReinforced() {
        REINFORCED_UNTIL_NS.set(System.nanoTime());
        REINFORCED_REASON = "";
    }

    // ---- 真抹殺エージェント実行への委譲 -----------------------------------------

    public static boolean annihilateTarget(Object entity) {
        return EprotectPlusAgent.annihilateEntityJvm(entity);
    }

    // ---- MOD 側からの状態読み出し（リフレクション） -----------------------------

    public static boolean isActive() {
        return active;
    }

    public static boolean isLateAttached() {
        return lateAttached;
    }

    public static synchronized List<Map<String, String>> getEvents() {
        List<Map<String, String>> list = new ArrayList<>(EVENTS.size());
        for (Map<String, String> ev : EVENTS) {
            list.add(Collections.unmodifiableMap(new LinkedHashMap<>(ev)));
        }
        return list;
    }

    public static synchronized void clearEvents() {
        EVENTS.clear();
    }

    public static synchronized Map<String, String> getStats() {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("active", String.valueOf(active));
        stats.put("lateAttached", String.valueOf(lateAttached));
        stats.put("instrumentation", String.valueOf(EprotectPlusAgent.hasInstrumentation()));
        stats.put("canRetransform", String.valueOf(EprotectPlusAgent.canRetransform()));
        stats.put("interferences", String.valueOf(INTERFERENCE_COUNT.get()));
        stats.put("bypasses", String.valueOf(BYPASS_COUNT.get()));
        stats.put("protections", String.valueOf(PROTECTION_COUNT.get()));
        stats.put("annihilations", String.valueOf(ANNIHILATION_COUNT.get()));
        stats.put("reinforced", String.valueOf(isReinforced()));
        stats.put("events", String.valueOf(EVENTS.size()));
        stats.put("flagDictionaryProtector", String.valueOf(com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent.hasInstrumentation()));
        stats.put("droppedLogs", Long.toString(DROPPED_LOGS.get()));
        stats.put("logErrors", Long.toString(LOG_ERRORS.get()));
        stats.putAll(EprotectPlusAgent.getAuditStats());
        stats.putAll(com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.getStats());
        return stats;
    }

    // ---- フラグ辞書（FlagDictionary）保護連携 ----------------------------------

    public static boolean verifyAndRepairFlagDictionary(Class<?> flagDictClass) {
        return com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.verifyAndRepair(flagDictClass);
    }

    public static Map<String, List<String>> getFlagMasterDictionary() {
        return com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.getMasterDictionary();
    }

    public static List<String> getFlagMasterEntries(String category) {
        return com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.getMasterEntries(category);
    }

    public static Map<String, String> getFlagProtectorStats() {
        return com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.getStats();
    }

    public static void clearPlayerFromKillModLists(UUID uuid) {
        EprotectPlusAgent.clearPlayerFromKillModLists(uuid);
    }
}
