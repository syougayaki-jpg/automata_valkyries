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

    /** ワールドへの入り直しを拒否され、同じ UUID の新しい個体に差し替えられた古い個体で呼ばれる（ボスバー等の後片付け） */
    default void onRetired() {}

    /** 異常修復が短時間に規定回数（既定200回）を超えた際に自動退避するか（ボスなどで退避させたくない場合は false） */
    default boolean canRetreatOnExcessiveAnomalies() {
        return true;
    }
}
