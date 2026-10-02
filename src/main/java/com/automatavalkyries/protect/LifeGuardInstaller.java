package com.automatavalkyries.protect;

import com.automatavalkyries.lifeguard.hooks.LifeHooks;

/** LifeGuard の呼び出し先を直接触る部分（エージェントが有ると確かめた後にだけ読み込む。{@link LifeGuardLink}） */
final class LifeGuardInstaller {

    private LifeGuardInstaller() {}

    static void install() {
        LifeHooks.filter = new TrueLife();
    }

    static void ensureLast() {
        Runnable r = LifeHooks.ensureLast;
        if (r != null) r.run();
    }

    static String status() {
        var s = LifeHooks.status;
        return s != null ? s.get() : "LifeGuard: connected";
    }
}
