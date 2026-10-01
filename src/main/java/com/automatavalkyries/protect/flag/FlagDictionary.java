package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * フラグ辞書: 他 MOD がエンティティに付ける「状態」の名前（メソッド・フィールド・永続データのキー・状態異常の ID）を、
 * 何を表すかで分類する。保護側（{@link com.automatavalkyries.protect.ForeignStateCleaner}）と抹殺側
 * （{@link com.automatavalkyries.annihilate.ModProtectionBreaker}）の準汎用処理が共通で使う。
 * <p>
 * 名前は単語に分けて照らし合わせる（camelCase・snake_case・Mixin の接頭辞 {@code modid$}・名前空間 {@code modid:} に対応）。
 * 部分文字列で照らすと soldier の die、studied の die のような取り違えが起きるため。
 * </p>
 * 項目の書き方:
 * <ul>
 *   <li>{@code kill} … いずれかの単語が kill で始まる（killed, killer）</li>
 *   <li>{@code =die} … いずれかの単語がちょうど die</li>
 *   <li>{@code *dimensionalcore} … 単語をつなげた名前に含まれる（複数語の言い回し用）</li>
 * </ul>
 * 設定 {@code flagDictionary.extra}（"分類:項目"）で足し、{@code flagDictionary.ignore}（名前）で除外できる。
 */
public final class FlagDictionary {

    public enum Category {
        /** 死亡・消去・抹消・呪いの印 */
        KILL,
        /** 停止・凍結・封印（Tick・AI・移動を止める印） */
        CONTROL,
        /** HP の固定・差し引き（HP をロックする・getHealth に足す値） */
        HEALTH_TAMPER,
        /** 守り（無敵・保護・免除）。保護対象では触らず、抹殺では外す */
        GUARD,
        /** 守りの迂回。保護対象では外し、抹殺では有効にする */
        BYPASS,
        /** 復活の許可 */
        REVIVE,
        /** 残り回数・数を表す単語（REVIVE と組み合わせて「残り復活回数」を見分ける） */
        COUNT,
        /** 時間・待ち時間を表す単語（0 にすると早まるので触らない） */
        TIMER
    }

    private static final Map<Category, List<String>> BUILT_IN = new EnumMap<>(Category.class);

    static {
        BUILT_IN.put(Category.KILL, List.of(
                "dead", "death", "=die", "=dies", "=died", "=dying", "eras", "remov", "novel", "kill", "soul", "doom",
                "condemn", "curse", "annihil", "obliter", "wipe", "purg", "banish", "delet", "vanish", "exile", "smite",
                "=executed", "=execution", "judg", "destroy", "eliminat", "terminat", "sentenc", "perish", "slain", "slay",
                "=reaped", "disintegrat", "nonexist", "unexist", "exterminat", "eradicat", "despawn", "fatal", "doomed",
                "*forcedead", "*forcekill", "*instakill", "*instantkill", "*markedfordeath"));
        BUILT_IN.put(Category.CONTROL, List.of(
                "freez", "frozen", "stun", "paraly", "petrif", "=sealed", "*timestop", "*stoptime", "*timestopped",
                "*aidisabled", "*disableai", "*noai", "*cleanai", "*notick", "*tickstopped", "*stoptick", "*skiptick"));
        BUILT_IN.put(Category.HEALTH_TAMPER, List.of(
                "*healthdelta", "*hpdelta", "*healthoffset", "*healthlock", "*lockhealth", "*lockedhealth", "*fixedhealth",
                "*healthoverride", "*forcedhealth", "*forcehealth"));
        BUILT_IN.put(Category.GUARD, List.of(
                "protect", "immun", "invuln", "=guard", "guarded", "guarding", "shield", "resist", "immortal", "invinc", "=god", "godmode", "muteki",
                "undying", "barrier", "exempt", "whitelist", "=ward", "warded", "fumetsu", "deathless", "unkillable",
                "indestruct", "*dimensionalcore", "phasing", "intangib"));
        BUILT_IN.put(Category.BYPASS, List.of("bypass", "*ignoreprotect", "*ignoreinvul", "*ignoreimmun"));
        BUILT_IN.put(Category.REVIVE, List.of("reviv", "respawn", "resurrect", "rebirth", "reincarnat", "=lives", "extralives"));
        BUILT_IN.put(Category.COUNT, List.of("count", "=left", "remaining", "=times", "charges", "=lives", "=num", "=amount"));
        BUILT_IN.put(Category.TIMER, List.of("delay", "timer", "cooldown", "tick", "=time", "wait", "interval", "duration", "until", "expir"));
    }

    private static volatile Map<Category, List<String>> active = BUILT_IN;
    private static volatile Set<String> ignored = Set.of();
    /** 辞書が変わるたびに増える（分類の結果を覚えている側が作り直すため） */
    private static volatile int generation = 0;

    private FlagDictionary() {}

    /** 設定の読み込み・再読み込み時に作り直す */
    public static synchronized void reloadFromConfig() {
        Map<Category, List<String>> merged = new EnumMap<>(Category.class);
        BUILT_IN.forEach((category, entries) -> merged.put(category, new ArrayList<>(entries)));
        for (String line : EprotectConfig.getList(EprotectConfig.FLAG_EXTRA)) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            try {
                Category category = Category.valueOf(line.substring(0, colon).trim().toUpperCase(Locale.ROOT));
                String entry = line.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
                if (!entry.isEmpty()) merged.get(category).add(entry);
            } catch (IllegalArgumentException e) {
                AutomataValkyries.LOGGER.warn("[eprotect] Unknown flag dictionary category in '{}'", line);
            }
        }
        Set<String> ignore = new LinkedHashSet<>();
        for (String name : EprotectConfig.getList(EprotectConfig.FLAG_IGNORE)) ignore.add(compact(name));
        merged.replaceAll((category, entries) -> List.copyOf(entries));
        active = merged;
        ignored = Set.copyOf(ignore);
        CACHE.clear();
        generation++;
    }

    public static int generation() {
        return generation;
    }

    /** 名前 → 分類（毎 Tick 同じ名前を引くので覚えておく。辞書を作り直したら捨てる） */
    private static final Map<String, Set<Category>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 名前が表す分類（設定で除外した名前は空） */
    public static Set<Category> classify(String name) {
        if (CACHE.size() > 8192) CACHE.clear();
        return CACHE.computeIfAbsent(name, FlagDictionary::computeClassify);
    }

    private static Set<Category> computeClassify(String name) {
        Set<Category> result = EnumSet.noneOf(Category.class);
        List<String> words = words(name);
        String compact = String.join("", words);
        if (compact.isEmpty() || ignored.contains(compact)) return Set.of();
        active.forEach((category, entries) -> {
            for (String entry : entries) {
                if (matches(entry, words, compact)) {
                    result.add(category);
                    break;
                }
            }
        });
        return result.isEmpty() ? Set.of() : java.util.Collections.unmodifiableSet(result);
    }

    public static boolean is(String name, Category category) {
        return classify(name).contains(category);
    }

    /**
     * 保護対象に付いていたら戻すべき状態か: 守りの迂回、または死亡・停止・HP 改ざんの印（守りを表す名前は除く）。
     * 「保護を迂回する」（bypassProtection）は守りの単語も含むが、迂回が優先。
     */
    public static boolean harmfulOnProtected(String name) {
        Set<Category> c = classify(name);
        if (c.contains(Category.BYPASS)) return true;
        if (c.contains(Category.GUARD)) return false;
        return c.contains(Category.KILL) || c.contains(Category.CONTROL) || c.contains(Category.HEALTH_TAMPER);
    }

    /** 抹殺の対象から外すべき守りか（守り・復活の許可。迂回は含めない） */
    public static boolean protectiveOnTarget(String name) {
        Set<Category> c = classify(name);
        if (c.contains(Category.BYPASS) || c.contains(Category.TIMER)) return false;
        return c.contains(Category.GUARD) || c.contains(Category.REVIVE);
    }

    /** 残り復活回数（0 にする数）か。待ち時間・間隔は含めない */
    public static boolean livesCounter(String name) {
        Set<Category> c = classify(name);
        if (c.contains(Category.TIMER)) return false;
        List<String> words = words(name);
        if (words.size() == 1 && (words.get(0).equals("lives") || words.get(0).equals("extralives"))) return true;
        return c.contains(Category.REVIVE) && c.contains(Category.COUNT);
    }

    // ---- 単語分け --------------------------------------------------------------

    /** 名前を小文字の単語に分ける（接頭辞 modid$ / modid: は落とす） */
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
            // 小文字→大文字（markedFor）と、大文字の連なりの最後（HTMLParser の P）で区切る。前の文字は元の文字で見る
            boolean boundary = upper && current.length() > 0
                    && (Character.isLowerCase(base.charAt(i - 1)) || Character.isDigit(base.charAt(i - 1))
                    || (i + 1 < base.length() && Character.isLowerCase(base.charAt(i + 1))));
            if (boundary) flush(current, words);
            current.append(Character.toLowerCase(ch));
        }
        flush(current, words);
        // 先頭の is / get / set / has は名前の意味ではないので落とす
        if (words.size() > 1 && Set.of("is", "get", "set", "has").contains(words.get(0))) words.remove(0);
        return words;
    }

    private static void flush(StringBuilder current, List<String> words) {
        if (current.length() > 0) {
            words.add(current.toString());
            current.setLength(0);
        }
    }

    private static String compact(String name) {
        return String.join("", words(name));
    }

    private static boolean matches(String entry, List<String> words, String compact) {
        if (entry.startsWith("*")) return compact.contains(entry.substring(1));
        if (entry.startsWith("=")) {
            String exact = entry.substring(1);
            for (String word : words) if (word.equals(exact)) return true;
            return false;
        }
        for (String word : words) if (word.startsWith(entry)) return true;
        return false;
    }

    /** 辞書の中身（コマンドの表示用） */
    public static Map<Category, List<String>> entries() {
        return active;
    }
}
