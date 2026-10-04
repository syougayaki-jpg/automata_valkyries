package com.automatavalkyries.protect.valkyrie;

import com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks;

/** エージェントの呼び出し先を直接触る部分（エージェントが有ると確かめた後にだけ読み込む。{@link ValkyrieAgentLink}） */
final class ValkyrieAgentInstaller {

    private ValkyrieAgentInstaller() {}

    static void install() {
        ValkyrieHooks.filter = new ValkyrieAgentFilter();
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
