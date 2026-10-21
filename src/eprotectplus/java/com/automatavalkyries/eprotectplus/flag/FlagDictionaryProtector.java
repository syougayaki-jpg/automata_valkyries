package com.automatavalkyries.eprotectplus.flag;

import com.automatavalkyries.eprotectplus.EprotectPlusBridge;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JVM エージェント常駐型フラグ辞書保護コントローラー（FlagDictionaryProtector）。
 * <p>
 * システムクラスローダーに常駐し、チート MOD によるリフレクション・Unsafe・バイトコード改ざんから
 * フラグ辞書（{@code FlagDictionary}）の定義・判定を完全に防護・復元する。
 * また、エージェント側自身がチート MOB の解体・真抹殺を実行する際の高精度判定エンジンとしても機能する。
 * </p>
 * <p>
 * 2026-10-21（段 1b）: 手書きの写しを廃止し、MOD と同じ {@value #RESOURCE}（JSON）を直接読み込んで
 * 内容の要約（SHA-256 ダイジェスト）と語彙一覧で点検・復元する（docs/SPEC_FLAG_DICTIONARY_V2.md §2・§3）。
 * </p>
 */
public final class FlagDictionaryProtector {

    public static final String RESOURCE = "/automata_valkyries/flag_dictionary.json";

    private static final AtomicLong TAMPER_DETECTED_COUNT = new AtomicLong();
    private static final AtomicLong REPAIRED_COUNT = new AtomicLong();

    /** JSON から読み込んだ不変のフラグ辞書マスターデータ（システムクラスローダー保護） */
    private static volatile Map<String, List<String>> MASTER_DICTIONARY;
    /** マスター辞書の SHA-256 要約（ハッシュ） */
    private static volatile String MASTER_DIGEST;

    private static final Pattern CATEGORY_BLOCK_PATTERN = Pattern.compile(
            "\"([A-Z_]+)\"\\s*:\\s*\\{[^{}]*?\"entries\"\\s*:\\s*\\[(.*?)\\]\\s*(?:,[^{}]*)?\\}",
            Pattern.DOTALL);
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    static {
        Map<String, List<String>> master = loadFromJsonResource();
        MASTER_DICTIONARY = Collections.unmodifiableMap(master);
        MASTER_DIGEST = digestOf(MASTER_DICTIONARY);
        com.automatavalkyries.eprotectplus.HiddenAgentVault.storeDictionary(MASTER_DICTIONARY, MASTER_DIGEST);
    }

    private static Map<String, List<String>> loadFromJsonResource() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        InputStream in = FlagDictionaryProtector.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            in = ClassLoader.getSystemResourceAsStream(RESOURCE.substring(1));
        }
        if (in == null) {
            return out;
        }
        try (InputStream stream = in) {
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int catIdx = json.indexOf("\"categories\"");
            if (catIdx < 0) return out;
            Matcher blockMatcher = CATEGORY_BLOCK_PATTERN.matcher(json.substring(catIdx));
            while (blockMatcher.find()) {
                String category = blockMatcher.group(1);
                String entriesBody = blockMatcher.group(2);
                List<String> entries = new ArrayList<>();
                Matcher strMatcher = STRING_LITERAL_PATTERN.matcher(entriesBody);
                while (strMatcher.find()) {
                    String value = unescapeJson(strMatcher.group(1)).trim().toLowerCase(Locale.ROOT);
                    if (!value.isEmpty()) entries.add(value);
                }
                out.put(category, List.copyOf(entries));
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private static String unescapeJson(String raw) {
        if (raw.indexOf('\\') < 0) return raw;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(++i);
                switch (next) {
                    case '"', '\\', '/' -> sb.append(next);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String digestOf(Map<String, List<String>> map) {
        if (map == null || map.isEmpty()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<String> keys = new ArrayList<>(map.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                md.update(key.getBytes(StandardCharsets.UTF_8));
                md.update((byte) ':');
                List<String> values = map.get(key);
                if (values != null) {
                    for (String val : values) {
                        md.update(val.getBytes(StandardCharsets.UTF_8));
                        md.update((byte) ',');
                    }
                }
                md.update((byte) ';');
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String getMasterDigest() {
        return masterDigest();
    }

    private static final Map<String, Set<String>> LOCAL_CACHE = new ConcurrentHashMap<>();

    private FlagDictionaryProtector() {}

    public static Map<String, List<String>> getMasterDictionary() {
        return masterDictionary();
    }

    private static Map<String, List<String>> masterDictionary() {
        Map<String, List<String>> current = MASTER_DICTIONARY;
        if (current != null && !current.isEmpty()) return current;
        Map<String, List<String>> vaulted = com.automatavalkyries.eprotectplus.HiddenAgentVault.loadDictionary();
        if (vaulted != null && !vaulted.isEmpty()) {
            MASTER_DICTIONARY = vaulted;
            return vaulted;
        }
        return Map.of();
    }

    private static String masterDigest() {
        String current = MASTER_DIGEST;
        if (current != null && !current.isEmpty()) return current;
        String vaulted = com.automatavalkyries.eprotectplus.HiddenAgentVault.loadDigest();
        if (vaulted != null && !vaulted.isEmpty()) {
            MASTER_DIGEST = vaulted;
            return vaulted;
        }
        return "";
    }

    public static List<String> getMasterEntries(String category) {
        return masterDictionary().getOrDefault(category, Collections.emptyList());
    }

    // ---- MOD 側 FlagDictionary の整合性検証・強制修復 -------------------------

    /**
     * MOD 側の FlagDictionary クラスの完全性を検証し、改ざんや空化があれば即座にマスター辞書で復元する。
     *
     * @param flagDictionaryClass MOD 側の FlagDictionary.class
     * @return 修復が行われたか
     */
    public static boolean verifyAndRepair(Class<?> flagDictionaryClass) {
        if (flagDictionaryClass == null) return false;
        synchronized (flagDictionaryClass) {
            try {
                Field active = flagDictionaryClass.getDeclaredField("active");
                Field builtIn = flagDictionaryClass.getDeclaredField("BUILT_IN");
                active.setAccessible(true);
                builtIn.setAccessible(true);
                Map<String, List<String>> expected = expectedActive(flagDictionaryClass);
                String expectedActiveDigest = digestOf(expected);
                Map<String, List<String>> currentBuiltIn = snapshot(builtIn.get(null));
                Map<String, List<String>> currentActive = snapshot(active.get(null));
                if (masterDigest().equals(digestOf(currentBuiltIn))
                        && expectedActiveDigest.equals(digestOf(currentActive))
                        && masterDictionary().equals(currentBuiltIn)
                        && expected.equals(currentActive)) return false;
                TAMPER_DETECTED_COUNT.incrementAndGet();
                repairModFlagDictionary(flagDictionaryClass, active, builtIn);
                // Do not report a repair merely because reflective writes returned normally.
                Map<String, List<String>> repairedBuiltIn = snapshot(builtIn.get(null));
                Map<String, List<String>> repairedActive = snapshot(active.get(null));
                if (!masterDigest().equals(digestOf(repairedBuiltIn))
                        || !expectedActiveDigest.equals(digestOf(repairedActive))
                        || !masterDictionary().equals(repairedBuiltIn)
                        || !expected.equals(repairedActive)) {
                    EprotectPlusBridge.recordEvent("警告", "FlagDictionary", "integrity", "repair", "未確認", "Content still differs after reload");
                    return false;
                }
                REPAIRED_COUNT.incrementAndGet();
                EprotectPlusBridge.recordProtection("FlagDictionary", "integrity", "integrity_repair", "Built-in content restored from flag_dictionary.json; user extra/ignore configuration reapplied");
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                EprotectPlusBridge.recordEvent("警告", "FlagDictionaryProtector", "integrity", "inspect", "失敗", exception.toString());
                return false;
            }
        }
    }

    private static Map<String, List<String>> snapshot(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            if (!(entry.getValue() instanceof List<?> list)) return Map.of();
            List<String> words = new ArrayList<>();
            for (Object word : list) {
                if (!(word instanceof String text)) return Map.of();
                words.add(text);
            }
            result.put(entry.getKey().toString(), words);
        }
        return result;
    }

    private static Map<String, List<String>> expectedActive(Class<?> dictionary) throws ReflectiveOperationException {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        masterDictionary().forEach((key, value) -> expected.put(key, new ArrayList<>(value)));
        Class<?> config = Class.forName("com.automatavalkyries.config.EprotectConfig", true, dictionary.getClassLoader());
        Object extra = config.getField("FLAG_EXTRA").get(null);
        java.lang.reflect.Method getter = java.util.Arrays.stream(config.getMethods())
                .filter(method -> method.getName().equals("getList") && method.getParameterCount() == 1)
                .findFirst().orElseThrow(() -> new NoSuchMethodException("EprotectConfig.getList"));
        for (Object item : (List<?>) getter.invoke(null, extra)) {
            String line = item.toString();
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            List<String> entries = expected.get(line.substring(0, colon).trim().toUpperCase(Locale.ROOT));
            String word = line.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
            if (entries != null && !word.isEmpty()) entries.add(word);
        }
        return expected;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void repairModFlagDictionary(Class<?> dictionary, Field active, Field builtIn) throws ReflectiveOperationException {
        Class<?> category = Class.forName("com.automatavalkyries.protect.flag.FlagDictionary$Category", true, dictionary.getClassLoader());
        Map map = (Map) builtIn.get(null);
        map.clear();
        for (Object key : category.getEnumConstants())
            map.put(key, masterDictionary().getOrDefault(key.toString(), List.of()));
        dictionary.getMethod("reloadFromConfig").invoke(null);
    }

    public static Set<String> classify(String name) {
        if (name == null || name.isEmpty()) return Collections.emptySet();
        if (LOCAL_CACHE.size() > 4096) LOCAL_CACHE.clear();
        return LOCAL_CACHE.computeIfAbsent(name, FlagDictionaryProtector::computeClassify);
    }

    private static Set<String> computeClassify(String name) {
        Set<String> result = new HashSet<>();
        List<String> words = words(name);
        String compact = String.join("", words);
        if (compact.isEmpty()) return Collections.emptySet();

        for (Map.Entry<String, List<String>> entry : masterDictionary().entrySet()) {
            for (String pattern : entry.getValue()) {
                if (matches(pattern, words, compact)) {
                    result.add(entry.getKey());
                    break;
                }
            }
        }
        return result.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(result);
    }

    public static boolean isHarmful(String name) {
        Set<String> c = classify(name);
        if (c.contains("BYPASS")) return true;
        if (c.contains("GUARD")) return false;
        return c.contains("KILL") || c.contains("CONTROL") || c.contains("HEALTH_TAMPER") || c.contains("CONCEAL") || c.contains("BANISH");
    }

    public static boolean isDefensive(String name) {
        Set<String> c = classify(name);
        if (c.contains("BYPASS") || c.contains("TIMER")) return false;
        return c.contains("GUARD") || c.contains("REVIVE") || c.contains("HEALTH_TAMPER");
    }

    /**
     * 死んだプレイヤー（リスポーンで消える古い個体）から外すべき状態か: 守り・復活の許可・停止・HP の固定・リスポーンの妨害。
     */
    public static boolean heldOnDeadPlayer(String name) {
        Set<String> c = classify(name);
        if (c.contains("BYPASS") || c.contains("TIMER")) return false;
        return c.contains("GUARD") || c.contains("REVIVE") || c.contains("CONTROL")
                || c.contains("HEALTH_TAMPER") || c.contains("RESPAWN_BLOCK");
    }

    /**
     * リスポーンした新しいプレイヤーに付いていたら戻すべき状態か: 保護対象と同じ有害な印とリスポーン妨害。
     */
    public static boolean hazardOnRespawned(String name) {
        Set<String> c = classify(name);
        if (c.contains("RESPAWN_BLOCK")) return true;
        if (c.contains("TIMER") || c.contains("COUNT") || recordsData(name)) return false;
        return isHarmful(name);
    }

    /**
     * リスポーン禁止・妨害の印か。
     */
    public static boolean isRespawnBlock(String name) {
        return classify(name).contains("RESPAWN_BLOCK");
    }

    /**
     * 印ではなく記録を表す名前か（最後に死んだ場所・死亡回数・統計等）。
     */
    public static boolean recordsData(String name) {
        for (String w : words(name)) {
            switch (w) {
                case "pos", "position", "location", "loc", "point", "coord", "coords", "dimension", "dim", "stat", "stats",
                     "history", "record", "records", "last", "total", "log", "message", "cause", "source", "killer", "attacker" -> {
                    return true;
                }
                default -> {}
            }
        }
        return false;
    }

    public static boolean isDeathState(String name) {
        if (name == null || name.isEmpty()) return false;
        Set<String> c = classify(name);
        if (!c.contains("KILL")) return false;
        List<String> words = words(name);
        for (String w : words) {
            if (w.equals("dead") || w.equals("death") || w.equals("die") || w.equals("removed")
                    || w.equals("erased") || w.equals("killed") || w.equals("discarded") || w.equals("destroyed")) {
                return true;
            }
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("dead") || lower.contains("death") || lower.contains("removed") || lower.contains("erased");
    }

    public static boolean isTerminationMethod(String methodName) {
        if (methodName == null || methodName.isEmpty()) return false;
        String lower = methodName.toLowerCase(Locale.ROOT);
        if (lower.equals("remove") || lower.equals("kill") || lower.equals("discard")
                || lower.equals("close") || lower.equals("stop") || lower.equals("cleanup")
                || lower.equals("destroy") || lower.equals("dispose") || lower.equals("terminate")
                || lower.equals("shutdown") || lower.equals("finish") || lower.equals("clear")) {
            return true;
        }
        return classify(methodName).contains("KILL");
    }

    // ---- 単語分割ヘルパー ------------------------------------------------------

    public static List<String> words(String name) {
        String base = name;
        int dollar = base.lastIndexOf('$');
        if (dollar >= 0) base = base.substring(dollar + 1);
        int colon = base.lastIndexOf(':');
        if (colon >= 0) base = base.substring(colon + 1);

        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            if (!Character.isLetterOrDigit(ch)) {
                flush(current, words);
                continue;
            }
            boolean upper = Character.isUpperCase(ch);
            boolean boundary = upper && current.length() > 0
                    && (Character.isLowerCase(base.charAt(i - 1)) || Character.isDigit(base.charAt(i - 1))
                    || (i + 1 < base.length() && Character.isLowerCase(base.charAt(i + 1))));
            if (boundary) flush(current, words);
            current.append(Character.toLowerCase(ch));
        }
        flush(current, words);
        if (words.size() > 1 && (words.get(0).equals("is") || words.get(0).equals("get")
                || words.get(0).equals("set") || words.get(0).equals("has"))) {
            words.remove(0);
        }
        return words;
    }

    private static void flush(StringBuilder current, List<String> words) {
        if (current.length() > 0) {
            words.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean matches(String pattern, List<String> words, String compact) {
        if (pattern.startsWith("*")) return compact.contains(pattern.substring(1));
        if (pattern.startsWith("=")) {
            String exact = pattern.substring(1);
            for (String word : words) if (word.equals(exact)) return true;
            return false;
        }
        for (String word : words) if (word.startsWith(pattern)) return true;
        return false;
    }

    public static Map<String, String> getStats() {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("masterCategories", String.valueOf(masterDictionary().size()));
        stats.put("masterDigest", masterDigest());
        stats.put("tamperDetections", String.valueOf(TAMPER_DETECTED_COUNT.get()));
        stats.put("repairs", String.valueOf(REPAIRED_COUNT.get()));
        return stats;
    }
}
