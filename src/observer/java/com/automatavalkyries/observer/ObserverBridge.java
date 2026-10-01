package com.automatavalkyries.observer;

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
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 観察の記録と、MOD 側への橋渡し。MOD はシステムクラスローダーからこのクラスをリフレクションで読む
 * （{@code com.automatavalkyries.protect.ObserverLink}）。やり取りは JDK の型だけにする。
 * 記録は logs/automata_valkyries-observer.log にも追記する。
 */
public final class ObserverBridge {

    private static final int MAX_EVENTS = 512;
    private static final Deque<Map<String, String>> EVENTS = new ArrayDeque<>();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static volatile boolean active = false;
    private static volatile boolean lateAttach = false;
    private static BufferedWriter writer;

    private ObserverBridge() {}

    static void init(boolean late) {
        lateAttach = late;
        active = true;
        try {
            Path path = Paths.get("logs", "automata_valkyries-observer.log");
            Files.createDirectories(path.getParent());
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            writer.write("# 観察用 Java エージェントの記録（他 MOD のクラスは書き換えない。このMOD 自身のクラスへの書き換えだけ元に戻す（guard））");
            writer.newLine();
            writer.write("# 書式: 時刻 | 種類 | クラス | 呼び出し元 | 変わったメソッド | 潰されたメソッド | 新しい参照 | 備考");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            writer = null;
        }
    }

    static synchronized void event(String kind, String className, String caller, String changed, String neutered,
                                   String addedRefs, String note) {
        Map<String, String> event = new LinkedHashMap<>();
        event.put("time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
        event.put("kind", kind);
        event.put("class", className.replace('/', '.'));
        event.put("caller", caller);
        event.put("changed", changed);
        event.put("neutered", neutered);
        event.put("addedRefs", addedRefs.replace('/', '.'));
        event.put("note", note);
        EVENTS.addLast(event);
        if (EVENTS.size() > MAX_EVENTS) EVENTS.removeFirst();
        if (writer != null) {
            try {
                writer.write(String.join(" | ", event.values()));
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {}
        }
    }

    static void dropped() {
        DROPPED.incrementAndGet();
    }

    // ---- MOD 側から（リフレクション） ------------------------------------------------

    public static boolean active() {
        return active;
    }

    public static boolean lateAttach() {
        return lateAttach;
    }

    /** 古い順 */
    public static synchronized List<Map<String, String>> events() {
        List<Map<String, String>> copy = new ArrayList<>(EVENTS.size());
        for (Map<String, String> event : EVENTS) copy.add(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(event)));
        return copy;
    }

    /** 改ざん防止の自己点検を始める（GameTest 用。点検用のクラス GuardProbe 以外は受け付けない） */
    public static String startGuardSelfTest(Class<?> probe) {
        try {
            return IntegrityGuard.startSelfTest(probe);
        } catch (Throwable t) {
            return "failed: " + t;
        }
    }

    public static void endGuardSelfTest() {
        IntegrityGuard.endSelfTest();
    }

    /** 能力の地図（置き場所, 能力, クラスの数, 例） */
    public static List<Map<String, String>> capabilities() {
        return ObserverAgent.capabilities();
    }

    public static Map<String, String> status() {
        Map<String, String> status = new LinkedHashMap<>(ObserverAgent.status());
        status.put("events", String.valueOf(EVENTS.size()));
        status.put("dropped", String.valueOf(DROPPED.get()));
        status.put("lateAttach", String.valueOf(lateAttach));
        return status;
    }
}
