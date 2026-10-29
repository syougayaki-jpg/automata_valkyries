package com.automatavalkyries.tactical;

/**
 * 戦術級コード（追加拡張仕様書 §13）。要求する同調率（%）・消費 FE・クールタイム（Tick）。
 * GENESIS-ZERO（裏ボス専用のカウンターコード）は裏ボスと一緒に後で（2026-10-07 ユーザー指定）
 */
public enum TacticalProtocol {
    /** 絨毯飽和爆撃: 異空間から現れた 8 機の無人爆撃機が半径 64 m へ焼夷弾を落とし（1 発 300）、炎が残る（TacticalPatterns）。2026-10-29 ユーザー指定「最近の MOD のインフレを見るに、もっと派手に」（質問で: 威力・範囲と演出の両方）で範囲をおよそ倍・威力をおよそ 10 倍【仮】 */
    CARPET("carpet", 30, 500_000, 120 * 20),
    /** 軌道衛星集束レーザー: 赤黒い螺旋の光が落ち、半径 100 m を焼く、毎秒 3,000（無敵を貫通） */
    GUNGNIR("gungnir", 50, 1_500_000, 180 * 20),
    /** 特異点重力崩壊球: 10 秒吸い寄せ（範囲は 48 → 96 m）、爆縮で最大 HP の 50%、中心に 20,000 */
    ABYSS("abyss", 70, 3_000_000, 300 * 20),
    /** 広域ナノ防壁・野戦病院: 半径 32 m のドームを 45 秒。敵の弾を消し、中の味方の HP を毎秒 25% 回復・耐性 II・攻撃力上昇、兵姫の充電の上乗せ */
    IRIS("iris", 40, 800_000, 150 * 20),
    /**
     * 仮想彗星: 青白い光の彗星が残光を引いて空から落ち、半径 120 m を吹き飛ばす（中心 40,000 → 縁 10,000。2026-10-29 にさらに範囲倍・威力 10 倍）
     * （2026-10-07 ユーザー指定「同調率 90% で使える、青白い光の仮想彗星が残光を引いて落ちてくるコード」。電力・クールタイム・威力は【仮】）
     */
    COMET("comet", 90, 4_000_000, 360 * 20);

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
