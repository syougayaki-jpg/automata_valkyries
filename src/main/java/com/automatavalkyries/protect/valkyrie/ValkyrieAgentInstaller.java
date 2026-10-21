package com.automatavalkyries.protect.valkyrie;

import com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks;

/** エージェントの呼び出し先を直接触る部分（エージェントが有ると確かめた後にだけ読み込む。{@link ValkyrieAgentLink}） */
final class ValkyrieAgentInstaller {

    private ValkyrieAgentInstaller() {}

    /** 入れた判断（毎秒、これが入っているか確かめる） */
    private static final ValkyrieAgentFilter FILTER = new ValkyrieAgentFilter();

    static void install() {
        ValkyrieHooks.install(FILTER);
    }

    /** 入っている判断が自分のものか */
    static boolean intact() {
        return ValkyrieHooks.current() == FILTER;
    }

    /** 判断が出した例外の回数 */
    static long errors() {
        return ValkyrieHooks.errors();
    }

    static void ensureLast() {
        Runnable r = ValkyrieHooks.ensureLast;
        if (r != null) r.run();
    }

    static String status() {
        var s = ValkyrieHooks.status;
        return s != null ? s.get() : "Valkyrie protect agent: connected";
    }
}
