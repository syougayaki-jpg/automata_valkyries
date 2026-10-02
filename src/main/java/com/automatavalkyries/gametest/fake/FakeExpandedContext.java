package com.automatavalkyries.gametest.fake;

/** GameTest 用: 架空の MOD が既存のクラスに足すフィールドに入れる状態の入れ物（Fantasy Ending の LivingEntityExpandedContext の再現） */
public class FakeExpandedContext {
    /** 死亡の印（保護対象なら戻す） */
    public boolean isDead;
    /** 待ち時間（触らない） */
    public int invulnerableTime = 7;
}
