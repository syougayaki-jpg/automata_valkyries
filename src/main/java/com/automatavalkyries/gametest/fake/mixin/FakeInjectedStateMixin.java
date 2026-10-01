package com.automatavalkyries.gametest.fake.mixin;

import com.automatavalkyries.gametest.fake.FakeStatefulMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * GameTest 用: 架空の MOD が Mixin で既存のクラスに足すフィールド（実際の MOD は vanilla の Entity / LivingEntity に足す）。
 * 元のクラスファイルに無いフィールドとして見つかり、架空の MOD のものと分かること・辞書どおりに戻される／外されることを確かめる。
 */
@Mixin(FakeStatefulMob.class)
public abstract class FakeInjectedStateMixin {

    /** 死亡の印（保護対象なら戻す） */
    @Unique
    public boolean fakemod$condemned;

    /** 守り（保護対象では触らない。抹殺では外す） */
    @Unique
    public boolean fakemod$invulnerable;
}
