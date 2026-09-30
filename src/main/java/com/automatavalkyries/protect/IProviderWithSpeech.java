package com.automatavalkyries.protect;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 保護対象モブ（兵姫・ボス）が実装するインターフェース。
 * Mixin はこのインターフェースの有無で保護対象かどうかを判定する。
 */
public interface IProviderWithSpeech {
    /** 個体ごとの保護コントローラー */
    Eprotect getEprotect();

    /** 正規退場（プレイヤー敗北）時にチャットへ流すセリフ。null なら表示しない */
    @Nullable
    Component getDefeatSpeech();

    /** 正規退場時の効果音。null なら鳴らさない */
    @Nullable
    default SoundEvent getDefeatSound() {
        return null;
    }
}
