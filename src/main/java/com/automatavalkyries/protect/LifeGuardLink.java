package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;

/**
 * LifeGuard エージェント（{@code -PwithLifeGuard}、{@code automata_valkyries-lifeguard.jar}）との接続。エージェントが無ければ何もしない。
 * <p>
 * エージェントの呼び出し先（{@code LifeHooks}）はブートストラップのクラスパスにある。このクラスはそれを直接参照しない
 * （参照すると、エージェントが無い環境でこのクラスの読み込みが失敗する）。触るのは有ると確かめた後の {@link LifeGuardInstaller} だけ。
 * </p>
 */
public final class LifeGuardLink {

    private static final String HOOKS = "com.automatavalkyries.lifeguard.hooks.LifeHooks";
    private static volatile boolean installed;

    private LifeGuardLink() {}

    public static boolean isAgentPresent() {
        try {
            Class.forName(HOOKS, false, null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 判断（{@link TrueLife}）を入れる。MOD の初期化のとき */
    public static synchronized void install() {
        if (installed || !isAgentPresent()) return;
        try {
            LifeGuardInstaller.install();
            installed = true;
            AutomataValkyries.LOGGER.info("[eprotect] LifeGuard agent found; protected entities' life getters now report their true values");
            ensureLast();
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[eprotect] Could not connect to the LifeGuard agent: {}", t.toString());
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    /** 自分の変換をいちばん外側へ並べ直させる（他のエージェントが後から変換器を足した後。ワールドの開始時など） */
    public static void ensureLast() {
        if (!installed) return;
        try {
            LifeGuardInstaller.ensureLast();
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] LifeGuard reorder failed: {}", t.toString());
        }
    }

    public static String status() {
        if (!installed) return "LifeGuard: not loaded";
        try {
            return LifeGuardInstaller.status();
        } catch (Throwable t) {
            return "LifeGuard: " + t;
        }
    }
}
