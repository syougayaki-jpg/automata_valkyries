package com.automatavalkyries.protect;

/**
 * eprotect の保護対象（兵姫・ボス）が実装するインターフェース。
 * Mixin・イベント・互換処理はこのインターフェースの有無で保護対象かどうかを判定する。
 * 正規退場時のセリフ・効果音が必要なら {@link IProviderWithSpeech} を実装する。
 */
public interface IEprotected {
    /** 個体ごとの保護コントローラー */
    Eprotect getEprotect();

    /** AI（GoalSelector）が外部 MOD によって全消去された場合に復元するコールバック */
    default void restoreGoalsIfEmpty() {}
}
