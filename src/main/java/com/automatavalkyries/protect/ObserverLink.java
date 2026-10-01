package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 観察用の Java エージェント（別 jar、{@code -javaagent:automata_valkyries-observer.jar}）の記録を読む。
 * エージェントはシステムクラスローダーに居て、このMOD のクラスからは直接見えないのでリフレクションで呼ぶ。
 * エージェントが付いていなければ何もしない（無くても MOD は普通に動く）。
 */
public final class ObserverLink {

    private static final String BRIDGE = "com.automatavalkyries.observer.ObserverBridge";

    /** 1 件の記録（呼び出し元・変わったクラスがどの MOD のものかを付けたもの） */
    public record Event(Map<String, String> raw, String callerMod, String classMod) {
        public String get(String key) {
            return raw.getOrDefault(key, "");
        }

        /** 新しく呼ぶようになったメソッド・フィールドのうち、フラグ辞書に当たらない名前（辞書に足す手がかり） */
        public List<String> unknownAddedNames() {
            Set<String> names = new LinkedHashSet<>();
            for (String ref : get("addedRefs").split(", ")) {
                int dot = ref.lastIndexOf('.');
                if (dot < 0 || dot == ref.length() - 1) continue;
                String name = ref.substring(dot + 1);
                if (!name.startsWith("<") && FlagDictionary.classify(name).isEmpty()) names.add(name);
            }
            return List.copyOf(names);
        }
    }

    @Nullable
    private static volatile Class<?> bridge;
    private static volatile boolean looked = false;

    private ObserverLink() {}

    @Nullable
    private static Class<?> bridge() {
        if (!looked) {
            looked = true;
            try {
                bridge = Class.forName(BRIDGE, true, ClassLoader.getSystemClassLoader());
            } catch (Throwable t) {
                bridge = null;
            }
        }
        return bridge;
    }

    public static boolean active() {
        Object value = call("active");
        return Boolean.TRUE.equals(value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> status() {
        Object value = call("status");
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    /** 古い順 */
    @SuppressWarnings("unchecked")
    public static List<Event> events() {
        Object value = call("events");
        if (!(value instanceof List<?> list)) return List.of();
        List<Event> events = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, String> raw = (Map<String, String>) map;
            events.add(new Event(raw, modOfFrame(raw.getOrDefault("caller", "")), modOfClass(raw.getOrDefault("class", ""))));
        }
        return events;
    }

    /** 能力の地図の 1 行（その MOD のクラスが何をできる作りか。実際にしたかは別） */
    public record Capability(String mod, String capability, int classes, String samples, String location) {}

    /** 能力の地図（MOD・能力の順）。MOD は例のクラスのパッケージから引く */
    @SuppressWarnings("unchecked")
    public static List<Capability> capabilities() {
        Object value = call("capabilities");
        if (!(value instanceof List<?> list)) return List.of();
        // 同じ MOD が jar を分けている（エージェントの jar と MOD の jar 等）ことがあるので、MOD・能力ごとにまとめる
        Map<String, Capability> merged = new java.util.TreeMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, String> raw = (Map<String, String>) map;
            String samples = raw.getOrDefault("samples", "");
            String first = samples.split(", ")[0];
            int count;
            try {
                count = Integer.parseInt(raw.getOrDefault("classes", "0"));
            } catch (NumberFormatException e) {
                count = 0;
            }
            String mod = first.isEmpty() ? CallerAttribution.UNKNOWN : CallerAttribution.modOfClassName(first);
            String capability = raw.getOrDefault("capability", "?");
            String location = raw.getOrDefault("location", "?");
            Capability row = new Capability(mod, capability, count, samples, location);
            merged.merge(mod + "|" + capability, row, (a, b) -> new Capability(mod, capability, a.classes() + b.classes(),
                    joinSamples(a.samples(), b.samples()), a.location() + " " + b.location()));
        }
        return new ArrayList<>(merged.values());
    }

    private static String joinSamples(String a, String b) {
        Set<String> samples = new LinkedHashSet<>();
        for (String s : (a + ", " + b).split(", ")) {
            if (!s.isEmpty() && samples.size() < 3) samples.add(s);
        }
        return String.join(", ", samples);
    }

    /** サーバー起動時にログへ一言（付いているかどうか） */
    public static void logStatus() {
        if (active()) {
            AutomataValkyries.LOGGER.info("[observer] The observation Java agent is attached: {}", status());
            for (Capability row : capabilities()) {
                if (!CallerAttribution.isForeign(row.mod())) continue;
                AutomataValkyries.LOGGER.info("[observer]   capability {}: {} ({} classes, e.g. {})", row.mod(), row.capability(),
                        row.classes(), row.samples());
            }
        } else {
            AutomataValkyries.LOGGER.debug("[observer] The observation Java agent is not attached");
        }
    }

    /** "クラス.メソッド < クラス.メソッド" の最初の他 MOD（無ければ最初の枠の MOD） */
    private static String modOfFrame(String caller) {
        String first = null;
        for (String frame : caller.split(" < ")) {
            int dot = frame.lastIndexOf('.');
            if (dot <= 0) continue;
            String mod = CallerAttribution.modOfClassName(frame.substring(0, dot));
            if (first == null) first = mod;
            if (CallerAttribution.isForeign(mod)) return mod;
        }
        return first != null ? first : CallerAttribution.UNKNOWN;
    }

    private static String modOfClass(String className) {
        return className.isEmpty() || className.equals("-") ? "-" : CallerAttribution.modOfClassName(className);
    }

    /** 改ざん防止の自己点検（GameTest 用。点検用のクラスだけを潰す変換器を他のエージェントのふりをして入れる） */
    public static String startGuardSelfTest(Class<?> probe) {
        Class<?> type = bridge();
        if (type == null) return "not attached";
        try {
            return String.valueOf(type.getMethod("startGuardSelfTest", Class.class).invoke(null, probe));
        } catch (Throwable t) {
            return "failed: " + t;
        }
    }

    public static void endGuardSelfTest() {
        call("endGuardSelfTest");
    }

    @Nullable
    private static Object call(String method) {
        Class<?> type = bridge();
        if (type == null) return null;
        try {
            Method m = type.getMethod(method);
            return m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
