package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;

import java.lang.reflect.Method;

/**
 * Epic Core API（ECA）の書き換え除外リストに、このMODのパッケージを登録する。
 * <p>
 * ECA は Java エージェントで他 MOD のクラスを再変換できる（AllReturn: 全メソッドを即 return させる、Transform: フックの差し込み）。
 * 除外リストは ECA 自身が他 MOD 向けに用意している正規の仕組み（The Last Sword など ECA を使う MOD も「友好」扱いで登録済み）。
 * ここに載っていれば、AllReturn の対象解決・全体 AllReturn の候補・クラス変換のいずれからも外れる。
 * ECA が入っていなければ何もしない。
 * </p>
 */
public final class EprotectEcaWhitelist {

    private static final String PACKAGE_PREFIX = "com.automatavalkyries.";

    private EprotectEcaWhitelist() {}

    public static void register() {
        Class<?> whitelist;
        try {
            whitelist = Class.forName("net.eca.coremod.TransformerWhitelist");
        } catch (ClassNotFoundException e) {
            return; // ECA なし
        }
        for (String method : new String[]{"addAllReturn", "addTransform"}) {
            try {
                Method m = whitelist.getMethod(method, String.class);
                m.invoke(null, PACKAGE_PREFIX);
            } catch (ReflectiveOperationException | RuntimeException e) {
                AutomataValkyries.LOGGER.warn("[eprotect] Could not register {} with ECA {}", PACKAGE_PREFIX, method, e);
                return;
            }
        }
        AutomataValkyries.LOGGER.info("[eprotect] Registered {} in the ECA transformer whitelist", PACKAGE_PREFIX);
    }
}
