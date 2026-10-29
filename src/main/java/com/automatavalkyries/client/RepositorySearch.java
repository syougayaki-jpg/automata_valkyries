package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ヴォイド・リポジトリの検索（2026-10-29 ユーザー指定「JEIと同じ方式にしてあとこのMODとバニラだけでも日本語対応して」。質問で: ひらがなで漢字も当たる・ローマ字で探せる）。
 * <p>書き方は JEI と同じ:</p>
 * <ul>
 *   <li>空白で区切った言葉はすべてに当たる物（かつ）。{@code |} で区切るとどれかに当たる物（または）。{@code "..."} で空白を含む言葉</li>
 *   <li>{@code -言葉} は当たらない物（除外）</li>
 *   <li>頭の記号: {@code @} MOD の名前、{@code #} 説明（ツールチップ）、{@code $} タグ、{@code %} クリエイティブのタブ、{@code &} 登録名（{@code minecraft:iron_ingot}）。記号なしは名前</li>
 * </ul>
 * <p>日本語: このMODとバニラの物は、名前の読み（{@code assets/automata_valkyries/search/readings_ja.json}、{@code tools/gen_search_readings.py} で作る）でも当たる。
 * ひらがな・カタカナは区別しない（「てつ」で鉄インゴット）。ローマ字でも当たる（「tetsu」「kairo」。si/shi・tu/tsu などの書き方の違いや、伸ばす音の有り無しは気にしない）。
 * 全角の英数字は半角として扱う</p>
 */
public final class RepositorySearch {

    /** 翻訳キー → [ひらがな, ローマ字]（読み込むのは最初に使う時の 1 回だけ） */
    private static Map<String, String[]> readings;

    private RepositorySearch() {}

    private static Map<String, String[]> readings() {
        if (readings != null) return readings;
        Map<String, String[]> map = new HashMap<>();
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "search/readings_ja.json"));
            if (resource.isPresent()) {
                try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                        JsonArray a = e.getValue().getAsJsonArray();
                        map.put(e.getKey(), new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
                    }
                }
            }
        } catch (Exception ex) {
            AutomataValkyries.LOGGER.warn("Could not read search readings", ex);
        }
        readings = map;
        return map;
    }

    /** 検索の言葉（頭の記号・除外） */
    private record Term(char kind, String text, boolean negate) {}

    /** 検索の文を「または」の組 → 「かつ」の言葉に分ける */
    static List<List<Term>> parse(String query) {
        List<List<Term>> groups = new ArrayList<>();
        for (String part : query.split("\\|")) {
            List<Term> terms = new ArrayList<>();
            StringBuilder word = new StringBuilder();
            boolean quoted = false;
            List<String> words = new ArrayList<>();
            for (char c : part.toCharArray()) {
                if (c == '"') quoted = !quoted;
                else if (Character.isWhitespace(c) && !quoted) {
                    if (word.length() > 0) words.add(word.toString());
                    word.setLength(0);
                } else word.append(c);
            }
            if (word.length() > 0) words.add(word.toString());
            for (String w : words) {
                boolean negate = w.startsWith("-") && w.length() > 1;
                if (negate) w = w.substring(1);
                char kind = "@#$%&".indexOf(w.charAt(0)) >= 0 && w.length() > 1 ? w.charAt(0) : ' ';
                String text = normalize(kind == ' ' ? w : w.substring(1));
                if (!text.isEmpty()) terms.add(new Term(kind, text, negate));
            }
            if (!terms.isEmpty()) groups.add(terms);
        }
        return groups;
    }

    /** 検索に当たるか（空の検索はすべて当たる） */
    public static boolean matches(ItemStack stack, String query) {
        List<List<Term>> groups = parse(query);
        if (groups.isEmpty()) return true;
        for (List<Term> group : groups) {
            boolean all = true;
            for (Term term : group) {
                if (hit(stack, term) == term.negate) {
                    all = false;
                    break;
                }
            }
            if (all) return true;
        }
        return false;
    }

    private static boolean hit(ItemStack stack, Term term) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String t = term.text;
        return switch (term.kind) {
            case '@' -> {
                String ns = id == null ? "" : id.getNamespace();
                String display = ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
                yield ns.contains(t) || normalize(display).contains(t);
            }
            case '#' -> {
                var mc = Minecraft.getInstance();
                for (Component line : stack.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL)) {
                    if (normalize(line.getString()).contains(t)) yield true;
                }
                yield false;
            }
            case '$' -> stack.getTags().anyMatch(tag -> tag.location().toString().contains(t));
            case '%' -> CreativeModeTabs.allTabs().stream().anyMatch(tab -> tab.contains(stack) && normalize(tab.getDisplayName().getString()).contains(t));
            case '&' -> id != null && id.toString().contains(t);
            default -> name(stack, id, t);
        };
    }

    /** 名前: 表示名・登録名・読み（ひらがな）・ローマ字 */
    private static boolean name(ItemStack stack, ResourceLocation id, String t) {
        if (normalize(stack.getHoverName().getString()).contains(t)) return true;
        if (id != null && id.getPath().contains(t)) return true;
        String[] reading = readings().get(stack.getDescriptionId());
        if (reading == null) return false;
        if (reading[0].contains(t)) return true;
        if (!isRomaji(t)) return false;
        return reading[1].contains(t) || canonRomaji(reading[1]).contains(canonRomaji(t));
    }

    private static boolean isRomaji(String t) {
        for (char c : t.toCharArray()) if (!(c >= 'a' && c <= 'z') && c != '-' && c != '\'') return false;
        return true;
    }

    /** 比べる形に直す: 小文字・カタカナ → ひらがな・全角英数 → 半角 */
    static String normalize(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'ァ' && c <= 'ヶ') c = (char) (c - 0x60);                                 // カタカナ → ひらがな
            else if (c >= '！' && c <= '～') c = Character.toLowerCase((char) (c - 0xFEE0));       // 全角 → 半角
            else if (c == '　') c = ' ';
            b.append(c);
        }
        return b.toString();
    }

    /** ローマ字の書き方の違いをそろえる（shi/si・tsu/tu・chi/ti・fu/hu・ji/zi・sha/sya・ん の nn・伸ばす音） */
    static String canonRomaji(String s) {
        String r = s.replace("-", "").replace("'", "")
                .replace("shi", "si").replace("chi", "ti").replace("tsu", "tu").replace("fu", "hu").replace("ji", "zi")
                .replace("sh", "sy").replace("ch", "ty").replace("j", "zy")
                .replace("nn", "n").replace("mb", "nb").replace("mp", "np");
        String prev;
        do {
            prev = r;
            r = r.replace("ou", "o").replace("oo", "o").replace("uu", "u").replace("aa", "a").replace("ii", "i").replace("ee", "e");
        } while (!r.equals(prev));
        return r;
    }
}
