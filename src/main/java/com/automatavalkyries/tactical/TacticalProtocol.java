package com.automatavalkyries.tactical;

/**
 * 戦術級コード（追加拡張仕様書 §13）。要求する同調率（%）・消費 FE・クールタイム（Tick）。
 * GENESIS-ZERO（裏ボス専用のカウンターコード）は裏ボスと一緒に後で（2026-10-07 ユーザー指定）
 */
public enum TacticalProtocol {
    /** 絨毯飽和爆撃: 半径 32 m へ 64 発を 5 秒かけて波状投下 */
    CARPET("carpet", 30, 500_000, 120 * 20),
    /** 軌道衛星集束レーザー: 直径 6 ブロックへ毎秒 150 ダメージを 5 秒（無敵を貫通） */
    GUNGNIR("gungnir", 50, 1_500_000, 180 * 20),
    /** 特異点重力崩壊球: 半径 24 m の敵・弾を 8 秒吸い寄せて拘束し、爆縮で最大 HP の 30% */
    ABYSS("abyss", 70, 3_000_000, 300 * 20),
    /** 広域ナノ防壁・野戦病院: 半径 16 m のドームを 30 秒。敵の弾を消し、中の味方の HP を毎秒 10% 回復、兵姫の充電 3 倍 */
    IRIS("iris", 40, 800_000, 150 * 20);

    public final String id;
    public final int sync;
    public final int fe;
    public final int cooldown;

    TacticalProtocol(String id, int sync, int fe, int cooldown) {
        this.id = id;
        this.sync = sync;
        this.fe = fe;
        this.cooldown = cooldown;
    }

    public static TacticalProtocol of(int ordinal) {
        TacticalProtocol[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }
}
