package com.automatavalkyries.entity;

/**
 * 素体タイプ（全体仕様書 §3.2）。HP・シールド・速度・積載の上限・内蔵弾薬庫の枠数・性格。
 * <ul>
 *   <li>弾薬庫の枠数は素体タイプで決める（2026-10-05 ユーザー指定。数は【仮】: V 6 / S 4 / H 8 / C 6）</li>
 *   <li>性格は素体タイプごとに固定（SPEC_VALKYRIE_GUI_AI §4【仮】: V 素直 / S 元気 / H 物静か / C 理知的）</li>
 * </ul>
 * モデルは 4 型すべて（2026-10-07、tools/gen_valkyrie_v.py）。型ごとの能力と立ち回りは ai/ValkyrieTypeTraits。
 * <p>Z は零号機（2026-10-20。隠しボス「マリスライズ・ロストカタストロフィー」を倒して素体を回収し、零号機再起動パッチで味方にする最初の兵姫。
 * 全体仕様書 §4 の 4・「零号機再起動パッチ」）。どの型より強い万能型【仮】。同調率は 100% に固定（{@link #fixedSync}）。
 * 弾薬庫の枠は持ち物の上限（ValkyrieInventory.MAX_AMMO = 8）まで。保存は名前なので、並びの最後に足す（数字の番号で同期している所もずれない）</p>
 */
public enum ValkyrieBody {
    V(40.0F, 20.0F, 0.28F, 50.0F, 6, "honest"),
    S(30.0F, 15.0F, 0.35F, 35.0F, 4, "cheerful"),
    H(60.0F, 40.0F, 0.20F, 85.0F, 8, "quiet"),
    C(35.0F, 30.0F, 0.26F, 45.0F, 6, "intellectual"),
    Z(80.0F, 50.0F, 0.32F, 90.0F, 8, "zero");

    public final float maxHealth, maxShield, speed, payload;
    public final int ammoSlots;
    public final String personality;

    ValkyrieBody(float maxHealth, float maxShield, float speed, float payload, int ammoSlots, String personality) {
        this.maxHealth = maxHealth;
        this.maxShield = maxShield;
        this.speed = speed;
        this.payload = payload;
        this.ammoSlots = ammoSlots;
        this.personality = personality;
    }

    /** 同調率が 100% に固定の型か（零号機） */
    public boolean fixedSync() {
        return this == Z;
    }

    public static ValkyrieBody of(int ordinal) {
        ValkyrieBody[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : V;
    }
}
