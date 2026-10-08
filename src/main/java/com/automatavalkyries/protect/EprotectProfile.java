package com.automatavalkyries.protect;

/**
 * 保護対象ごとの数値（被弾の上限の掛け方）。既定は {@link #DEFAULT}（全ボス共通だった値）。ボスごとに変えたいときは
 * {@code new Eprotect(this, EprotectProfile.DEFAULT.withMaxHitRatio(0.05F))} のように渡す
 *
 * @param capDamage           被弾の上限を掛けるか。false でも即死系（/kill・被弾以外の HP 減少・消去・強制移動など）は拒否し、
 *                            普通の被弾は桁外れの値もそのまま通す（NaN・無限大だけ置き換える）
 * @param maxHitRatio         時間窓の間に HP から引く合計の上限（最大 HP に対する割合）。1 撃で倒れてよい残量の判定にも使う
 * @param damageWindowTicks   被弾量を数える時間窓（サーバーの Tick 数）
 * @param hugeDamageThreshold これ以上（または NaN / 無限大）の単発ダメージは異常値とみなす
 * @param hugeDamageClamp     異常値を置き換えるダメージ
 * @param blacklistHitRatio   ブラックリストの MOD からのダメージの上限（罰則。maxHitRatio の代わりに使う）
 */
public record EprotectProfile(boolean capDamage, float maxHitRatio, int damageWindowTicks,
                              float hugeDamageThreshold, float hugeDamageClamp, float blacklistHitRatio) {

    /** 既定（2026-10-01 ユーザー指定: 10 秒で最大 HP の 10% まで、1000 以上の単発は 100 に） */
    public static final EprotectProfile DEFAULT = new EprotectProfile(true, Eprotect.MAX_HIT_RATIO, Eprotect.DAMAGE_WINDOW_TICKS,
            Eprotect.HUGE_DAMAGE_THRESHOLD, Eprotect.HUGE_DAMAGE_CLAMP, Eprotect.BLACKLIST_HIT_RATIO);

    /** 被弾の上限なし（普通に倒せるボス。2026-10-07 ユーザー指定、ルクシオン「即死系の守りを付ける・被弾上限は無し」） */
    public static final EprotectProfile UNCAPPED = DEFAULT.withoutDamageCap();

    public EprotectProfile withoutDamageCap() {
        return new EprotectProfile(false, maxHitRatio, damageWindowTicks, hugeDamageThreshold, hugeDamageClamp, blacklistHitRatio);
    }

    public EprotectProfile withMaxHitRatio(float ratio) {
        return new EprotectProfile(capDamage, ratio, damageWindowTicks, hugeDamageThreshold, hugeDamageClamp, blacklistHitRatio);
    }

    public EprotectProfile withDamageWindow(int ticks) {
        return new EprotectProfile(capDamage, maxHitRatio, ticks, hugeDamageThreshold, hugeDamageClamp, blacklistHitRatio);
    }

    /** 置き換えるべき異常な単発ダメージか（NaN・無限大はいつも、桁外れの値は上限を掛ける個体だけ） */
    public boolean isHugeDamage(float amount) {
        return !Float.isFinite(amount) || (capDamage && amount >= hugeDamageThreshold);
    }
}
