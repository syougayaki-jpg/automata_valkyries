package com.automatavalkyries.eprotectplus.flag;

import com.automatavalkyries.eprotectplus.EprotectPlusBridge;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JVM エージェント常駐型フラグ辞書保護コントローラー（FlagDictionaryProtector）。
 * <p>
 * システムクラスローダーに常駐し、チート MOD によるリフレクション・Unsafe・バイトコード改ざんから
 * フラグ辞書（{@code FlagDictionary}）の定義・判定を完全に防護・復元する。
 * また、エージェント側自身がチート MOB の解体・真抹殺を実行する際の高精度判定エンジンとしても機能する。
 * </p>
 */
public final class FlagDictionaryProtector {

    private static final AtomicLong TAMPER_DETECTED_COUNT = new AtomicLong();
    private static final AtomicLong REPAIRED_COUNT = new AtomicLong();

    /** 不変のフラグ辞書マスターデータ（システムクラスローダー保護） */
    private static final Map<String, List<String>> MASTER_DICTIONARY;

    static {
        Map<String, List<String>> master = new LinkedHashMap<>();

        master.put("KILL", List.of(
                "dead", "death", "=die", "=dies", "=died", "=dying", "eras", "remov", "novel", "kill", "soul", "doom",
                "condemn", "curse", "annihil", "obliter", "wipe", "purg", "banish", "delet", "vanish", "exile", "smite",
                "execut", "assassin", "murder", "butcher", "slaughter", "extinguish", "annul", "nullif", "discard", "suicide",
                "=reap", "=reaper", "=reaped", "sever", "oblivion", "expire", "mortal", "endlife", "erasure", "abyss", "tomb", "grave",
                "destruct", "terminus", "=void", "collapse", "shred", "decimat", "dissolv", "vaporiz",
                "marked", "condemned", "cursed", "branded", "stigmat", "fatality", "instadeath",
                "judg", "destroy", "eliminat", "terminat", "sentenc", "perish", "slain", "slay",
                "disintegrat", "nonexist", "unexist", "exterminat", "eradicat", "despawn", "fatal", "doomed",
                "*forcedead", "*forcekill", "*instakill", "*instantkill", "*markedfordeath", "*forcedeath", "*truekill",
                "*absolutekill", "*superkill", "*directkill", "*unmake", "*erasetarget", "*cleartarget", "*removeentity",
                "*setdead", "*forcediscard", "*fatalkill", "*cleardata", "*seterased", "*markerased", "*eraseentity"));

        master.put("CONTROL", List.of(
                "freez", "frozen", "stun", "paraly", "petrif", "=sealed", "timelock", "chronolock", "chronostop",
                "ticklock", "frozenevent", "immob", "stasis", "suppress", "lockdown", "=bind", "=bound", "binding",
                "=root", "=rooted", "rooting", "entangle", "=trap", "=trapped", "sleep", "silence", "sedat", "subdue",
                "incapacitat", "*timestop", "*stoptime", "*timestopped", "*aidisabled", "*disableai", "*noai", "*cleanai",
                "*notick", "*tickstopped", "*stoptick", "*skiptick", "*stopticking", "*disablegoal", "*cleargoal",
                "*stopai", "*pauseai", "*suppressai", "*freezeai",
                "tether", "*locationlock", "*lockedlocation", "*locklocation", "*positionlock", "*lockedposition", "*lockposition",
                "*movelock", "*lockmove", "*movementlock", "*lockmovement", "*nomove", "*cantmove", "*disablemove", "*inputlock",
                "*lockinput", "*lockedinput", "*disableinput", "*pinned", "*pinposition",
                // 時間停止・Tick 停止（EndingLibrary の TimeStopUtils / timeStopDimensions 等）
                "*timefreeze", "*freezetime", "*timepause", "*pausetime", "*worldstop", "*stopworld", "*tickfreeze",
                "*freezetick", "*frozenworld", "*frozentime", "*timehalt", "*halttime", "*stoppedtime", "*timeisstop",
                "*stoppeddimension", "*frozendimension", "*chronostasis", "*timeskip", "*nogravity", "*disablegravity"));

        master.put("HEALTH_TAMPER", List.of(
                "zerohp", "hpzero", "drainhp", "hpdrain", "damagecap", "healthlimit", "hpoverride", "sethp", "sethealth", "delta",
                "*healthdelta", "*hpdelta", "*shielddelta", "*healthoffset", "*healthlock", "*lockhealth", "*lockedhealth", "*fixedhealth",
                "*healthoverride", "*forcedhealth", "*forcehealth", "*forcezero", "*zerodamage", "*drainhealth",
                "*nullhealth", "*hpcap", "*currenthp", "*worldanchor", "*healthanchor", "*anchorhealth", "*anchoredhealth",
                "*healthlockenc", "*lockedhp", "*hplock", "*gethealth", "*healthmodif", "*hpmodif", "*hpoffset"));

        master.put("GUARD", List.of(
                "protect", "immun", "invuln", "=guard", "guarded", "guarding", "shield", "resist", "immortal", "invinc",
                "=god", "godmode", "muteki", "undying", "barrier", "exempt", "whitelist", "=ward", "warded", "fumetsu",
                "deathless", "unkillable", "indestruct", "*dimensionalcore", "phasing", "intangib", "aegis", "sanctuary",
                "impervious", "defiance", "witherzilla", "perpetual", "eternal", "unbreakable", "unyielding", "adamant",
                "fullset", "=halo", "bless", "blessing", "snack",
                "*forcefullset", "*wasfullset", "*forcehalo", "*hasfullset", "*snackprotector",
                "*damagenegation", "*negatedamage", "*infinitehp", "*infinitehealth", "*infinitedefense", "*infinitelife",
                "*immortalstate", "*invulnerablestate", "*godstate",
                "=invul", "=invuls", "=defence", "=defense", "*defencemode", "*defensemode", "*godprotect", "*absoluteprotect"));

        master.put("BYPASS", List.of(
                "bypass", "penetrat", "pierc", "*ignoreprotect", "*ignoreinvul", "*ignoreimmun", "*truedamage",
                "*unblockable", "*ignoredefense", "*overrideguard", "*antiinvul", "*antiprotect", "*directdamage",
                "*absolutedamage", "*bypasstotem", "*ignorebarrier"));

        master.put("REVIVE", List.of("reviv", "respawn", "resurrect", "rebirth", "reincarnat", "=lives", "extralives", "*undyingcore", "*respawntick",
                "*allreturn", "*returnall", "*autoreturn", "*autorevive", "*selfrevive", "*comeback", "*restorelife", "*lifeback"));

        master.put("CONCEAL", List.of(
                "=invisible", "*forceinvisible", "*setinvisible", "*keepinvisible", "*invisiblemode", "=hidden", "*hideentity",
                "*hiderender", "*norender", "*cancelrender", "*disablerender", "*skiprender", "*stoprender", "*hidebossbar",
                "*nobossbar", "*removebossbar", "*clearbossbar", "*hidebar", "*cloaked", "*clientdelete", "*deleteclient",
                "*clientremove", "*removeclient", "*hidefromclient", "*untracked", "*stoptracking"));

        master.put("RESPAWN_BLOCK", List.of(
                "*norespawn", "*antirespawn", "*respawnlock", "*lockrespawn", "*blockrespawn", "*respawnblock", "*denyrespawn",
                "*cancelrespawn", "*preventrespawn", "*respawnban", "*banrespawn", "*disablerespawn", "*respawndisabled",
                "*disallowrespawn", "*respawnforbid", "*forbidrespawn", "*permadeath", "*permanentdeath", "*keepdead",
                "*staydead", "*deathlock", "*lockdeath", "*lockeddeath", "*deathscreenlock", "*nodeathscreenclose"));

        master.put("COUNT", List.of("count", "=left", "remaining", "=times", "charges", "=lives", "=num", "=amount"));

        master.put("TIMER", List.of("delay", "timer", "cooldown", "tick", "=time", "wait", "interval", "duration", "until", "expir", "respawntick", "erasetime"));

        master.put("BANISH", List.of(
                "banish", "voidfall", "relocat", "displace", "=rift", "*voiddrop", "*forcevoid", "*forcedimension",
                "*banishment", "*dimensionfall"));

        MASTER_DICTIONARY = Collections.unmodifiableMap(master);
    }

    private static final Map<String, Set<String>> LOCAL_CACHE = new ConcurrentHashMap<>();

    private FlagDictionaryProtector() {}

    public static Map<String, List<String>> getMasterDictionary() {
        return MASTER_DICTIONARY;
    }

    public static List<String> getMasterEntries(String category) {
        return MASTER_DICTIONARY.getOrDefault(category, Collections.emptyList());
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
        try {
            boolean tampered = false;

            // 1. active フィールドの点検
            Field activeField = flagDictionaryClass.getDeclaredField("active");
            activeField.setAccessible(true);
            Object activeObj = activeField.get(null);

            if (activeObj instanceof Map<?, ?> map) {
                if (map.isEmpty() || map.size() < MASTER_DICTIONARY.size()) {
                    tampered = true;
                } else {
                    // 各カテゴリーのエントリ数を点検
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        String catName = entry.getKey().toString();
                        if (entry.getValue() instanceof List<?> list) {
                            List<String> expected = MASTER_DICTIONARY.get(catName);
                            if (expected != null && list.size() < expected.size() / 2) {
                                tampered = true;
                                break;
                            }
                        }
                    }
                }
            } else {
                tampered = true;
            }

            // 2. BUILT_IN フィールドの点検
            Field builtInField = flagDictionaryClass.getDeclaredField("BUILT_IN");
            builtInField.setAccessible(true);
            Object builtInObj = builtInField.get(null);
            if (builtInObj instanceof Map<?, ?> builtInMap && builtInMap.isEmpty()) {
                tampered = true;
            }

            if (tampered) {
                TAMPER_DETECTED_COUNT.incrementAndGet();
                repairModFlagDictionary(flagDictionaryClass, activeField, builtInField);
                REPAIRED_COUNT.incrementAndGet();
                EprotectPlusBridge.recordProtection("FlagDictionary", "ForeignMod/Reflection", "integrity_repair",
                        "MOD側のフラグ辞書改ざん・初期化を検知し、JVMエージェントマスターデータから完全復元");
                return true;
            }

            return false;
        } catch (Throwable t) {
            EprotectPlusBridge.recordEvent("警告", "FlagDictionaryProtector", "verifyAndRepair", "inspect", "検証中例外", t.getMessage());
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void repairModFlagDictionary(Class<?> flagDictClass, Field activeField, Field builtInField) throws Exception {
        Class<?> categoryEnumClass = Class.forName("com.automatavalkyries.protect.flag.FlagDictionary$Category", true, flagDictClass.getClassLoader());
        Map newMerged = (Map) EnumMap.class.getConstructor(Class.class).newInstance(categoryEnumClass);

        Object[] enumConstants = categoryEnumClass.getEnumConstants();
        for (Object enumConst : enumConstants) {
            String name = enumConst.toString();
            List<String> masterEntries = MASTER_DICTIONARY.get(name);
            if (masterEntries != null) {
                newMerged.put(enumConst, Collections.unmodifiableList(new ArrayList<>(masterEntries)));
            } else {
                newMerged.put(enumConst, Collections.emptyList());
            }
        }

        // active フィールドを上書き復元
        activeField.set(null, newMerged);

        // BUILT_IN フィールドも復元
        if (builtInField != null) {
            Object builtInMap = builtInField.get(null);
            if (builtInMap instanceof Map m) {
                for (Object enumConst : enumConstants) {
                    String name = enumConst.toString();
                    List<String> masterEntries = MASTER_DICTIONARY.get(name);
                    if (masterEntries != null) {
                        m.put(enumConst, new ArrayList<>(masterEntries));
                    }
                }
            }
        }

        // CACHE をクリア
        try {
            Field cacheField = flagDictClass.getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            Object cacheObj = cacheField.get(null);
            if (cacheObj instanceof Map m) {
                m.clear();
            }
        } catch (Throwable ignored) {}

        // generation をインクリメント
        try {
            Field genField = flagDictClass.getDeclaredField("generation");
            genField.setAccessible(true);
            int currentGen = genField.getInt(null);
            genField.setInt(null, currentGen + 1);
        } catch (Throwable ignored) {}
    }

    // ---- エージェント内蔵判定エンジン（JDK 標準のみで完結） ----------------------

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

        for (Map.Entry<String, List<String>> entry : MASTER_DICTIONARY.entrySet()) {
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
        stats.put("masterCategories", String.valueOf(MASTER_DICTIONARY.size()));
        stats.put("tamperDetections", String.valueOf(TAMPER_DETECTED_COUNT.get()));
        stats.put("repairs", String.valueOf(REPAIRED_COUNT.get()));
        return stats;
    }
}
