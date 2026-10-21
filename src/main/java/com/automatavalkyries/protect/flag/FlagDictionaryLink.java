package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MOD クラスローダーから JVM エージェント常駐型フラグ辞書防護（FlagDictionaryProtector / FlagDictionaryAgent）
 * と密接に通信・整合性検証・自動修復を行うリンク層。
 * <p>
 * Java エージェントが有効な場合、システムクラスローダー上の不変マスター辞書との照合を行い、
 * 外部チート MOD によるリフレクションや Unsafe による辞書改ざんを即時検知・復元する。
 * </p>
 */
public final class FlagDictionaryLink {

    private static final String BRIDGE_CLASS = "com.automatavalkyries.eprotectplus.EprotectPlusBridge";
    private static final String PROTECTOR_CLASS = "com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector";

    @Nullable
    private static volatile Class<?> bridge;
    @Nullable
    private static volatile Class<?> protector;
    private static volatile boolean checked = false;

    private FlagDictionaryLink() {}

    public static synchronized void refreshAfterAttach() {
        checked = false;
        bridge = null;
        protector = null;
        init();
    }

    private static synchronized void init() {
        if (!checked) {
            checked = true;
            try {
                ClassLoader sysLoader = ClassLoader.getSystemClassLoader();
                Class<?> agent = Class.forName("com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent", false, sysLoader);
                if (!Boolean.TRUE.equals(agent.getMethod("hasInstrumentation").invoke(null))) return;
                try {
                    bridge = Class.forName(BRIDGE_CLASS, true, sysLoader);
                } catch (Throwable ignored) {}
                try {
                    protector = Class.forName(PROTECTOR_CLASS, true, sysLoader);
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        }
    }

    /** フラグ辞書保護エージェントが有効かつ稼働中か */
    public static boolean isAgentActive() {
        init();
        if (protector != null) return true;
        if (bridge != null) {
            try {
                Method m = bridge.getMethod("isActive");
                return Boolean.TRUE.equals(m.invoke(null));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /**
     * JVM エージェントと FlagDictionary の整合性を検証し、改ざんされていればマスターデータから即座に修復する。
     *
     * @return エージェントによる修復が行われたか
     */
    public static boolean verifyAndRepair() {
        init();
        if (protector != null) {
            try {
                Method m = protector.getMethod("verifyAndRepair", Class.class);
                Object res = m.invoke(null, FlagDictionary.class);
                if (Boolean.TRUE.equals(res)) {
                    AutomataValkyries.LOGGER.warn("[FlagDictionaryLink] Flag dictionary tampering detected and repaired by JVM agent!");
                    return true;
                }
            } catch (Throwable t) {
                AutomataValkyries.LOGGER.error("[FlagDictionaryLink] Failed to invoke verifyAndRepair on FlagDictionaryProtector", t);
            }
        } else if (bridge != null) {
            try {
                Method m = bridge.getMethod("verifyAndRepairFlagDictionary", Class.class);
                Object res = m.invoke(null, FlagDictionary.class);
                if (Boolean.TRUE.equals(res)) {
                    AutomataValkyries.LOGGER.warn("[FlagDictionaryLink] Flag dictionary tampering detected and repaired via bridge!");
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /**
     * エージェントのマスター辞書から指定カテゴリーの語句一覧を取得する。
     */
    @SuppressWarnings("unchecked")
    public static List<String> getMasterEntries(String category) {
        init();
        if (protector != null) {
            try {
                Method m = protector.getMethod("getMasterEntries", String.class);
                Object res = m.invoke(null, category);
                if (res instanceof List<?> l) return (List<String>) l;
            } catch (Throwable ignored) {}
        } else if (bridge != null) {
            try {
                Method m = bridge.getMethod("getFlagMasterEntries", String.class);
                Object res = m.invoke(null, category);
                if (res instanceof List<?> l) return (List<String>) l;
            } catch (Throwable ignored) {}
        }
        return Collections.emptyList();
    }

    /**
     * エージェント側のフラグ辞書保護統計情報を取得する。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getAgentStats() {
        init();
        if (protector != null) {
            try {
                Method m = protector.getMethod("getStats");
                Object res = m.invoke(null);
                if (res instanceof Map<?, ?> map) return (Map<String, String>) map;
            } catch (Throwable ignored) {}
        } else if (bridge != null) {
            try {
                Method m = bridge.getMethod("getFlagProtectorStats");
                Object res = m.invoke(null);
                if (res instanceof Map<?, ?> map) return (Map<String, String>) map;
            } catch (Throwable ignored) {}
        }
        return Collections.emptyMap();
    }
}
