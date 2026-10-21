package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;

import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.lang.reflect.Field;
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
 * 組み込みの項目は {@value #RESOURCE}（2026-10-21 から。改修の設計は docs/SPEC_FLAG_DICTIONARY_V2.md）。
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
        TIMER,
        /** 追放・奈落落とし・強制テレポート */
        BANISH,
        /** リスポーンの禁止・妨害（死亡画面から戻れなくする・リスポーンを取り消す印） */
        RESPAWN_BLOCK,
        /** 隠す（透明化・描画の取り消し・ボスバーの消去・クライアントだけの消去）。消したように見せる印 */
        CONCEAL
    }

    /** 組み込みの辞書の置き場所（MOD の jar の中。資源パック・データパックでは上書きできない。辞書のエージェントも同じファイルを読む） */
    public static final String RESOURCE = "/automata_valkyries/flag_dictionary.json";

    /**
     * 組み込みの辞書。2026-10-21 から {@value #RESOURCE} の JSON 1 つから読む（それまではここと辞書のエージェントに同じ並びを写していた。
     * ユーザー指定「アンチグラビティーと合わせてフラグ辞書を改修していく」、docs/SPEC_FLAG_DICTIONARY_V2.md §3）
     */
    private static final Map<Category, List<String>> BUILT_IN = loadBuiltIn();

    private static Map<Category, List<String>> loadBuiltIn() {
        Map<Category, List<String>> out = new EnumMap<>(Category.class);
        for (Category category : Category.values()) out.put(category, List.of());
        try (InputStream in = FlagDictionary.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                AutomataValkyries.LOGGER.error("[eprotect] The built-in flag dictionary {} is missing", RESOURCE);
                return out;
            }
            JsonObject categories = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("categories");
            for (Map.Entry<String, JsonElement> e : categories.entrySet()) {
                Category category;
                try {
                    category = Category.valueOf(e.getKey());
                } catch (IllegalArgumentException unknown) {                     // 新しい分類を先に足した側が古い側を壊さないよう、読み飛ばす
                    AutomataValkyries.LOGGER.warn("[eprotect] Unknown flag dictionary category '{}' in {}", e.getKey(), RESOURCE);
                    continue;
                }
                List<String> entries = new ArrayList<>();
                for (JsonElement entry : e.getValue().getAsJsonObject().getAsJsonArray("entries")) {
                    String text = entry.getAsString().trim().toLowerCase(Locale.ROOT);
                    if (!text.isEmpty()) entries.add(text);
                }
                out.put(category, List.copyOf(entries));
            }
        } catch (Exception ex) {
            AutomataValkyries.LOGGER.error("[eprotect] Could not read the built-in flag dictionary {}", RESOURCE, ex);
        }
        return out;
    }

    /** 組み込みの辞書（JSON から読んだまま。設定の追加・除外は含まない） */
    public static Map<Category, List<String>> builtIn() {
        return java.util.Collections.unmodifiableMap(BUILT_IN);
    }

    private static volatile Map<Category, List<String>> active = BUILT_IN;
    private static volatile Set<String> ignored = Set.of();
    /** 辞書が変わるたびに増える（分類の結果を覚えている側が作り直すため） */
    private static volatile int generation = 0;

    private FlagDictionary() {}

    /**
     * JVM エージェント（FlagDictionaryProtector / FlagDictionaryAgent）と連携し、辞書の整合性を点検・修復する。
     */
    public static synchronized boolean ensureAgentProtection() {
        boolean repaired = FlagDictionaryLink.verifyAndRepair();
        // エージェント未接続時でもローカルフェイルセーフで保護
        if (active == null || active.isEmpty()) {
            Map<Category, List<String>> fallback = new EnumMap<>(Category.class);
            BUILT_IN.forEach((cat, list) -> fallback.put(cat, List.copyOf(list)));
            active = fallback;
            CACHE.clear();
            generation++;
            repaired = true;
        }
        return repaired;
    }

    /** JVM エージェントによるフラグ辞書防護が有効か */
    public static boolean isProtectedByAgent() {
        return FlagDictionaryLink.isAgentActive();
    }

    /** JVM エージェント側の統計情報を取得 */
    public static Map<String, String> getAgentStats() {
        return FlagDictionaryLink.getAgentStats();
    }

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

    static {
        // 初期化完了時に JVM エージェントとの連携・整合性チェックを実施。
        // 使うフィールド（active・CACHE）の宣言より後に置く（前に置くと、まだ null のまま呼ばれて読み込みに失敗する）
        ensureAgentProtection();
    }

    /** 名前が表す分類（設定で除外した名前は空） */
    public static Set<Category> classify(String name) {
        if (active == null || active.isEmpty()) {
            ensureAgentProtection();
        }
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

    /** 設定 {@code flagDictionary.ignore} で除外した名前か */
    public static boolean ignored(String name) {
        return ignored.contains(compact(name));
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
        return c.contains(Category.KILL) || c.contains(Category.CONTROL) || c.contains(Category.HEALTH_TAMPER)
                || c.contains(Category.BANISH) || c.contains(Category.CONCEAL);
    }

    /**
     * ダメージソースが即死・有害・防御貫通属性を持つか、フラグ辞書で準汎用的に判定する。
     * バニラの /kill や奈落落とし、未知 MOD の即死・処刑・消滅ダメージタイプを網羅する。
     */
    public static boolean isHarmfulDamageSource(DamageSource source) {
        if (source == null) return false;
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return true;
        // このMOD 自身の兵装のダメージ（overload_pierce・armor_piercing など）は即死扱いにしない。名前の「pierc」が辞書の迂回に当たり、
        // エレノアに兵装のダメージが一切入らなかった（2026-10-05 ユーザーの報告）。通常の被弾の上限（Eprotect.capFinalDamage）で受ける。
        // このMOD のアイテムを即死扱いにしない（EprotectForgeEvents.isForeignKillItem）のと揃える
        var ownType = source.typeHolder().unwrapKey().map(key -> key.location()).orElse(null);
        if (ownType != null && com.automatavalkyries.AutomataValkyries.MODID.equals(ownType.getNamespace())) return false;

        String msgId = source.getMsgId();
        if (msgId != null) {
            if (harmfulOnProtected(msgId)) return true;
            String lower = msgId.toLowerCase(Locale.ROOT);
            if (lower.contains("imds") || lower.contains("novel") || lower.contains("hyper") || lower.contains("kill")
                    || lower.contains("outofworld") || lower.contains("felloutofworld")) {
                return true;
            }
        }

        var typeLocation = source.typeHolder().unwrapKey().map(key -> key.location()).orElse(null);
        if (typeLocation != null) {
            String path = typeLocation.getPath();
            if (harmfulOnProtected(path)) return true;
            String ns = typeLocation.getNamespace().toLowerCase(Locale.ROOT);
            if (ns.contains("imds") || ns.contains("hyperdaimc") || ns.contains("nosugar")) return true;
        }

        return false;
    }

    /**
     * 攻撃者が所持しているアイテムが即死・処刑アイテムか、フラグ辞書で準汎用的に判定する。
     * 未知 MOD の即死剣・デスサイズ・抹消ツールを網羅する。
     */
    public static boolean isKillItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null) {
            String path = key.getPath();
            if (harmfulOnProtected(path) || is(path, Category.KILL) || is(path, Category.BYPASS)) return true;
            String fullKey = key.toString().toLowerCase(Locale.ROOT);
            if (fullKey.contains("imds") || fullKey.contains("dominator") || fullKey.contains("hyperdaimc")
                    || fullKey.contains("novel")) {
                return true;
            }
        }

        String className = stack.getItem().getClass().getSimpleName();
        if (harmfulOnProtected(className) || is(className, Category.KILL) || is(className, Category.BYPASS)) return true;
        String fullClassName = stack.getItem().getClass().getName().toLowerCase(Locale.ROOT);
        if (fullClassName.contains("imds") || fullClassName.contains("dominator")
                || fullClassName.contains("hyperdaimc") || fullClassName.contains("novel")) {
            return true;
        }

        if (stack.hasCustomHoverName()) {
            String hover = stack.getHoverName().getString();
            if (harmfulOnProtected(hover) || is(hover, Category.KILL) || is(hover, Category.BYPASS)) return true;
        }

        CompoundTag tag = stack.getTag();
        if (tag != null) {
            for (String tagKey : tag.getAllKeys()) {
                if (harmfulOnProtected(tagKey) || is(tagKey, Category.KILL) || is(tagKey, Category.BYPASS)) return true;
                if (tag.contains(tagKey, Tag.TAG_STRING)) {
                    String strVal = tag.getString(tagKey);
                    if (harmfulOnProtected(strVal) || is(strVal, Category.KILL) || is(strVal, Category.BYPASS)) return true;
                }
            }
        }

        return false;
    }

    /**
     * 死んだプレイヤー（リスポーンで消える古い個体）から外すべき状態か: 守り・復活の許可・停止・HP の固定・リスポーンの妨害。
     * 古い個体が守られ続けたり他 MOD に復活させられたりすると、消えずに残って新しい個体と二重になる・リスポーンの要求が無視される。
     * 死亡・消去の印は残す（死んでいるので正しい）。待ち時間・迂回は触らない。
     */
    public static boolean heldOnDeadPlayer(String name) {
        Set<Category> c = classify(name);
        if (c.contains(Category.BYPASS) || c.contains(Category.TIMER)) return false;
        return c.contains(Category.GUARD) || c.contains(Category.REVIVE) || c.contains(Category.CONTROL)
                || c.contains(Category.HEALTH_TAMPER) || c.contains(Category.RESPAWN_BLOCK);
    }

    /**
     * リスポーンした新しいプレイヤーに付いていたら戻すべき状態か: 保護対象と同じ有害な印（死亡・停止・HP 改ざん・追放・迂回）と
     * リスポーンの妨害。UUID で持つ印・引き継がれた永続データで、新しい個体まで死亡扱い・停止のままになるのを防ぐ。守りは触らない。
     */
    public static boolean hazardOnRespawned(String name) {
        Set<Category> c = classify(name);
        if (c.contains(Category.RESPAWN_BLOCK)) return true;
        if (c.contains(Category.TIMER) || c.contains(Category.COUNT) || recordsData(name)) return false;
        return harmfulOnProtected(name);
    }

    /**
     * 印ではなく記録を表す名前か（最後に死んだ場所・死亡回数・統計等）。リスポーンのたびに消すと他 MOD の記録が失われるので、
     * リスポーンの片付けでは触らない
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

    /**
     * チート MOB やプレイヤーのフィールド・NBT・Capability で「無敵・生命維持・防御・HP固定」を表すか。
     * 終焉執行・解体時に false または 0 にリセットすべき対象。
     */
    public static boolean isDefensiveField(String name) {
        if (protectiveOnTarget(name)) return true;
        Set<Category> c = classify(name);
        if (c.contains(Category.BYPASS) || c.contains(Category.TIMER)) return false;
        return c.contains(Category.HEALTH_TAMPER) || c.contains(Category.GUARD) || c.contains(Category.REVIVE);
    }

    /**
     * 死亡・削除済みを表すフラグ名か（例: isDead, dead, removed, erased, killed）。
     * 終焉執行・解体時に true に設定すべきフラグ。
     */
    public static boolean isDeathStateField(String name) {
        if (name == null || name.isEmpty()) return false;
        Set<Category> c = classify(name);
        if (!c.contains(Category.KILL)) return false;
        List<String> words = words(name);
        for (String w : words) {
            if (w.equals("dead") || w.equals("death") || w.equals("die") || w.equals("removed")
                    || w.equals("erased") || w.equals("killed") || w.equals("discarded") || w.equals("destroyed")) {
                return true;
            }
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("dead") || lower.contains("death") || lower.contains("removed")
                || lower.contains("eras");
    }

    /**
     * オブジェクトやコントローラーを破棄・終了・解体するためのメソッド名か。
     */
    public static boolean isTerminationMethod(String methodName) {
        if (methodName == null || methodName.isEmpty()) return false;
        String lower = methodName.toLowerCase(Locale.ROOT);
        if (lower.equals("remove") || lower.equals("kill") || lower.equals("discard")
                || lower.equals("close") || lower.equals("stop") || lower.equals("cleanup")
                || lower.equals("destroy") || lower.equals("dispose") || lower.equals("terminate")
                || lower.equals("shutdown") || lower.equals("finish") || lower.equals("clear")) {
            return true;
        }
        Set<Category> c = classify(methodName);
        return c.contains(Category.KILL);
    }

    /**
     * アイテムが不死・身代わり・防御・復活効果を持つか判定する（Totem 互換品、チート防具、無敵チャーム等）。
     * プレイヤー終焉執行時にインベントリ・装備から剥奪すべき対象。
     */
    public static boolean isDefensiveItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.TOTEM_OF_UNDYING)) return true;

        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null) {
            String path = key.getPath();
            if (protectiveOnTarget(path) || is(path, Category.GUARD) || is(path, Category.REVIVE)) return true;
            String fullKey = key.toString().toLowerCase(Locale.ROOT);
            if (fullKey.contains("infinity") || fullKey.contains("immortal") || fullKey.contains("totem")
                    || fullKey.contains("invulnerab") || fullKey.contains("god") || fullKey.contains("ward")
                    || fullKey.contains("halo") || fullKey.contains("sugar") || fullKey.contains("snack")
                    || fullKey.contains("bless")) {
                return true;
            }
        }

        String className = stack.getItem().getClass().getSimpleName();
        if (protectiveOnTarget(className) || is(className, Category.GUARD) || is(className, Category.REVIVE)) return true;

        if (stack.hasCustomHoverName()) {
            String hover = stack.getHoverName().getString();
            if (protectiveOnTarget(hover) || is(hover, Category.GUARD) || is(hover, Category.REVIVE)) return true;
        }

        CompoundTag tag = stack.getTag();
        if (tag != null) {
            for (String tagKey : tag.getAllKeys()) {
                if (protectiveOnTarget(tagKey) || is(tagKey, Category.GUARD) || is(tagKey, Category.REVIVE)) return true;
                if (tag.contains(tagKey, Tag.TAG_STRING)) {
                    String strVal = tag.getString(tagKey);
                    if (protectiveOnTarget(strVal) || is(strVal, Category.GUARD) || is(strVal, Category.REVIVE)) return true;
                }
            }
        }

        return false;
    }

    /**
     * コントローラー・本体・コア・親管理体を表すフィールド名か。
     */
    public static boolean isCoreOrControllerName(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("controller") || lower.contains("core") || lower.contains("parent")
                || lower.contains("master") || lower.contains("manager") || lower.contains("boss")
                || lower.contains("source") || lower.contains("origin") || lower.contains("root")
                || lower.contains("holder") || lower.contains("owner")) {
            return true;
        }
        List<String> words = words(name);
        for (String w : words) {
            if (w.equals("controller") || w.equals("core") || w.equals("parent")
                    || w.equals("master") || w.equals("manager") || w.equals("boss")
                    || w.equals("source") || w.equals("origin") || w.equals("root")) {
                return true;
            }
        }
        return false;
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
        // 連結形の前方一致: 単語をまたぐ長めの項目も拾う（例 chronolock → chronoLocked = chrono+locked。
        // 単語ごとの前方一致では当たらない）。短い項目での偶然のまたぎ誤爆を避けるため 6 文字以上に限る（2026-10-21）
        if (entry.length() >= 6 && compact.startsWith(entry)) return true;
        return false;
    }

    /** 辞書の中身（コマンドの表示用） */
    public static Map<Category, List<String>> entries() {
        return active;
    }

    /**
     * エンティティが危険なフラグ（チートMODの即死・消滅・無敵・生命維持・防御貫通フラグ等）を
     * 所有しているか判定する。
     * バニラの一般モンスター（ゾンビ、スケルトン等）や家畜・村人等は false になる。
     */
    public static boolean hasDangerousFlag(@org.jetbrains.annotations.Nullable Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof IEprotected || entity instanceof net.minecraft.world.entity.player.Player) return false;

        // 1. 直近に eprotect を攻撃してきた攻撃者
        if (AttackLog.isRecentAttacker(entity)) {
            return true;
        }

        // 2. 危険な即死・処刑アイテムを所持している場合
        if (entity instanceof LivingEntity living) {
            if (isKillItem(living.getMainHandItem()) || isKillItem(living.getOffhandItem())) {
                return true;
            }
        }

        // 3. PersistentData (Forge NBT) の危険フラグキー走査
        try {
            CompoundTag tag = entity.getPersistentData();
            for (String key : tag.getAllKeys()) {
                if (harmfulOnProtected(key) || is(key, Category.KILL) || is(key, Category.BYPASS)
                        || is(key, Category.HEALTH_TAMPER) || is(key, Category.GUARD) || is(key, Category.BANISH)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // 4. クラス名・パッケージ名の照合（既知のチートMOD）
        Class<?> clazz = entity.getClass();
        String className = clazz.getName().toLowerCase(Locale.ROOT);
        if (className.contains("witherzilla") || className.contains("omnimobs") || className.contains("flashfur")
                || className.contains("nosugar") || className.contains("imds") || className.contains("the_last_sword")
                || className.contains("hyperdaimc") || className.contains("shizuku") || className.contains("moreavaritia")) {
            return true;
        }

        // 5. 独自フィールド・インターフェースの動的走査（他 MOD のクラスである場合）
        if (CallerAttribution.isForeign(CallerAttribution.modOf(clazz))) {
            for (Class<?> c = clazz; c != null && c != Object.class && CallerAttribution.isForeign(CallerAttribution.modOf(c)); c = c.getSuperclass()) {
                for (Class<?> iface : c.getInterfaces()) {
                    String ifaceName = iface.getSimpleName();
                    if (harmfulOnProtected(ifaceName) || is(ifaceName, Category.KILL) || is(ifaceName, Category.BYPASS)
                            || is(ifaceName, Category.GUARD) || is(ifaceName, Category.HEALTH_TAMPER)) {
                        return true;
                    }
                }
                for (Field f : c.getDeclaredFields()) {
                    String fname = f.getName();
                    if (harmfulOnProtected(fname) || is(fname, Category.KILL) || is(fname, Category.BYPASS)
                            || is(fname, Category.GUARD) || is(fname, Category.HEALTH_TAMPER) || isDefensiveField(fname)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
