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

    /** サーバー起動時にログへ一言（付いているかどうか） */
    public static void logStatus() {
        if (active()) {
            AutomataValkyries.LOGGER.info("[observer] The observation Java agent is attached: {}", status());
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
