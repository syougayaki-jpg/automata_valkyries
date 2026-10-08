package com.automatavalkyries.observer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配布環境の Minecraft の名前（SRG 名）と開発環境の名前（公式名）の表（MOD 側の {@code protect/McNames} と同じ表。JDK だけで読む）。
 * 表は観察者の jar に入っている（build.gradle の generateMcNames）
 */
final class McNames {

    private static final String RESOURCE = "/automata_valkyries_mcnames.txt";
    private static final Map<String, String> OFFICIAL = new HashMap<>();
    private static final Map<String, List<String>> SRG = new HashMap<>();

    static {
        try (InputStream in = McNames.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = reader.readLine()) != null; ) {
                    int space = line.indexOf(' ');
                    if (space <= 0) continue;
                    String srg = line.substring(0, space), official = line.substring(space + 1).trim();
                    OFFICIAL.put(srg, official);
                    SRG.computeIfAbsent(official, k -> new ArrayList<>()).add(srg);
                }
            }
        } catch (Exception ignored) {
            // 表が無ければ公式名だけで見る
        }
    }

    private McNames() {}

    /** SRG 名なら公式名に直す（表に無い名前・公式名はそのまま） */
    static String official(String name) {
        if (name.length() < 4 || name.charAt(1) != '_' || (name.charAt(0) != 'f' && name.charAt(0) != 'm')) return name;
        return OFFICIAL.getOrDefault(name, name);
    }

    /** 公式名に当たる SRG 名（無ければ空） */
    static List<String> srgOf(String official) {
        return SRG.getOrDefault(official, Collections.emptyList());
    }

    static boolean loaded() {
        return !OFFICIAL.isEmpty();
    }
}
