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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Eprotect+ Java エージェントの記録・統計・MOD との橋渡し。
 * <p>
 * システムクラスローダーに常駐し、MOD 側からはリフレクションを介して呼び出される。
 * JDK 標準型のみで通信を行い、クラスローダーの壁を越えて一貫した保護・調査・迂回・真抹殺を実行する。
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
    private static BufferedWriter writer;

    /** 特権実行（StackChecker などの発信元チェックを迂回するコンテキスト）フラグ */
    private static final ThreadLocal<Boolean> PRIVILEGED_EXECUTION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 強化処理（Reinforced Protection）モード終了時刻（ミリ秒）と理由 */
    private static final AtomicLong REINFORCED_UNTIL_MS = new AtomicLong(0);
    private static volatile String REINFORCED_REASON = "";

    private EprotectPlusBridge() {}

    static synchronized void init(boolean late) {
        lateAttached = late;
        active = true;
        try {
            Path path = Paths.get("logs", "automata_valkyries-eprotect-plus.log");
            Files.createDirectories(path.getParent());
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            writer.write("# Eprotect+ Java エージェント診断・調査・迂回・保護ログ");
            writer.newLine();
            writer.write("# 形式: 時刻 | 種別 | クラス/対象 | 発信元/呼出元 | 操作/フック | 対処/結果 | 備考");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            writer = null;
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

        if (writer != null) {
            try {
                writer.write(String.join(" | ", ev.values()));
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {}
        }
    }

    public static void recordInterference(String target, String caller, String op, String details) {
        INTERFERENCE_COUNT.incrementAndGet();
        recordEvent("調査・干渉検知", target, caller, op, "検知・解析完了", details);
    }

    public static void recordBypass(String target, String caller, String op, String details) {
        BYPASS_COUNT.incrementAndGet();
        recordEvent("迂回執行", target, caller, op, "迂回成功", details);
    }

    public static void recordProtection(String target, String caller, String op, String details) {
        PROTECTION_COUNT.incrementAndGet();
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
        PRIVILEGED_EXECUTION.set(privileged);
    }

    public static void runPrivileged(Runnable action) {
        boolean prev = PRIVILEGED_EXECUTION.get();
        PRIVILEGED_EXECUTION.set(true);
        try {
            action.run();
        } finally {
            PRIVILEGED_EXECUTION.set(prev);
        }
    }

    // ---- 強化処理（Reinforced Protection）制御 ---------------------------------

    public static void triggerReinforced(long durationMs, String reason) {
        long targetUntil = System.currentTimeMillis() + durationMs;
        REINFORCED_UNTIL_MS.updateAndGet(prev -> Math.max(prev, targetUntil));
        REINFORCED_REASON = reason != null ? reason : "UNKNOWN";
        recordProtection("EprotectPlusBridge", reason, "reinforced_mode",
                "強化処理発動（期間: " + durationMs + "ms, 理由: " + REINFORCED_REASON + "）");
    }

    public static boolean isReinforced() {
        return System.currentTimeMillis() < REINFORCED_UNTIL_MS.get();
    }

    public static String getReinforcedReason() {
        return isReinforced() ? REINFORCED_REASON : "";
    }

    public static long getReinforcedRemainingMs() {
        long diff = REINFORCED_UNTIL_MS.get() - System.currentTimeMillis();
        return Math.max(0, diff);
    }

    public static void resetReinforced() {
        REINFORCED_UNTIL_MS.set(0);
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

    public static Map<String, String> getStats() {
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
        stats.put("flagDictionaryProtector", "active");
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
}
