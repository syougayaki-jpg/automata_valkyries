package com.automatavalkyries.protect.valkyrie;

import com.automatavalkyries.AutomataValkyries;

/**
 * オートマタヴァルキリー protect の Java エージェント（{@code -PwithValkyrieProtect}、{@code automata_valkyries-valkyrie-protect.jar}）との接続。
 * エージェントが無ければ何もしない（ゲーム内の層だけで守る）。
 * <p>呼び出し先（{@code ValkyrieHooks}）はブートストラップのクラスパスにある。このクラスはそれを直接参照しない
 * （参照すると、エージェントが無い環境でこのクラスの読み込みが失敗する）。触るのは有ると確かめた後の {@link ValkyrieAgentInstaller} だけ。</p>
 */
public final class ValkyrieAgentLink {

    private static final String HOOKS = "com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks";
    private static volatile boolean installed;

    private ValkyrieAgentLink() {}

    public static boolean isAgentPresent() {
        try {
            Class.forName(HOOKS, false, null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 判断（{@link ValkyrieAgentFilter}）を入れる。MOD の初期化のとき */
    public static synchronized void install() {
        if (installed || !isAgentPresent()) return;
        try {
            ValkyrieAgentInstaller.install();
            installed = true;
            AutomataValkyries.LOGGER.info("[valkyrie-protect] agent found; removal and lethal entry points of valkyries are guarded at the bytecode level");
            ensureLast();
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[valkyrie-protect] could not connect to the agent: {}", t.toString());
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    /** 自分の変換をいちばん外側へ並べ直させる（他のエージェントが後から変換器を足した後。ワールドの開始時など） */
    public static void ensureLast() {
        if (!installed) return;
        try {
            ValkyrieAgentInstaller.ensureLast();
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[valkyrie-protect] agent reorder failed: {}", t.toString());
        }
    }

    public static String status() {
        if (!installed) return "Valkyrie protect agent: not loaded";
        try {
            return ValkyrieAgentInstaller.status();
        } catch (Throwable t) {
            return "Valkyrie protect agent: " + t;
        }
    }
}
