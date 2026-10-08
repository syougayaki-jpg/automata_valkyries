package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 配布環境の Minecraft の名前（SRG 名: {@code f_146795_} / {@code m_8647_}）を開発環境の名前（公式名）に直す。
 * 保護の見分けは公式名で書いてあるので、配布環境で他 MOD が書いた名前はここを通してから比べる。表はビルドのときに作る
 * （build.gradle の generateMcNames。見分けに使うクラスのぶんだけ）。SRG 名はクラスをまたいで一意なので、名前だけで引ける
 */
public final class McNames {

    private static final String RESOURCE = "/automata_valkyries_mcnames.txt";
    private static final Map<String, String> OFFICIAL = load();

    private McNames() {}

    /** SRG 名なら公式名に直す（表に無い名前・公式名はそのまま） */
    public static String official(String name) {
        if (name == null || name.length() < 4 || name.charAt(1) != '_' || (name.charAt(0) != 'f' && name.charAt(0) != 'm')) return name;
        return OFFICIAL.getOrDefault(name, name);
    }

    /** 表を読めたか（読めなければ配布環境の名前は直せない） */
    public static boolean loaded() {
        return !OFFICIAL.isEmpty();
    }

    private static Map<String, String> load() {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = McNames.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                AutomataValkyries.LOGGER.warn("[eprotect] {} is missing; names in a release environment will not be translated", RESOURCE);
                return map;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                int space = line.indexOf(' ');
                if (space > 0) map.put(line.substring(0, space), line.substring(space + 1).trim());
            }
        } catch (Exception e) {
            AutomataValkyries.LOGGER.warn("[eprotect] Could not read {}: {}", RESOURCE, e.toString());
        }
        return map;
    }
}
