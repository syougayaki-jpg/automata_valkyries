package com.automatavalkyries.gametest;

/**
 * 観察用 Java エージェントの改ざん防止（IntegrityGuard）の自己点検に使うクラス。点検では value() を「0 を返すだけ」に潰す
 * 書き換えを他のエージェントのふりをして入れ、改ざん防止が元の 42 に戻すかを見る。
 * value() の中身（bipush 42; ireturn）はエージェント側がバイト列で探すので変えないこと。
 */
public final class GuardProbe {

    private GuardProbe() {}

    public static int value() {
        return 42;
    }
}
