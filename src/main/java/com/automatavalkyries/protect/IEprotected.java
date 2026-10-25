package com.automatavalkyries.protect;

/**
 * eprotect の保護対象（兵姫・ボス）が実装するインターフェース。
 * Mixin・イベント・互換処理はこのインターフェースの有無で保護対象かどうかを判定する。
 * 正規退場時のセリフ・効果音が必要なら {@link IProviderWithSpeech} を実装する。
 */
public interface IEprotected {
    /** テスト・検証用: 終焉機構のホワイトリストスキップを全体で無効化するスイッチ */
    java.util.concurrent.atomic.AtomicBoolean GLOBAL_TERMINUS_TEST_MODE = new java.util.concurrent.atomic.AtomicBoolean(false);

    /** 個体ごとの保護コントローラー */
    Eprotect getEprotect();

    /**
     * この個体のボスバー（無ければ空）。他 MOD に見ている人を外されたら、追跡で見えている人へ戻す（{@code Eprotect.restoreBossBarViewers}。
     * The King Sword はボスバーの全員をリフレクションで外す。2026-10-25）
     */
    default java.util.List<net.minecraft.server.level.ServerBossEvent> bossBars() {
        return java.util.List.of();
    }

    /** AI（GoalSelector）が外部 MOD によって全消去された場合に復元するコールバック */
    default void restoreGoalsIfEmpty() {}

    /** ワールドへの入り直しを拒否され、同じ UUID の新しい個体に差し替えられた古い個体で呼ばれる（ボスバー等の後片付け） */
    default void onRetired() {}

    /**
     * 終焉機構（超絶抹殺・Terminus）の味方保護ホワイトリストから除外するかどうか（検証・ダミー用）。
     * true の場合、終焉機構は誤爆防止スキップを行わず、全力で抹殺パイプラインをこのエンティティに執行する。
     * エンティティに "terminus_test_target" タグを付与するか、GLOBAL_TERMINUS_TEST_MODE を有効にすることでテスト対象にできる。
     */
    default boolean isTerminusTestTarget() {
        if (GLOBAL_TERMINUS_TEST_MODE.get()) return true;
        if (this instanceof net.minecraft.world.entity.Entity e) {
            return e.getTags().contains("terminus_test_target");
        }
        return false;
    }
}

