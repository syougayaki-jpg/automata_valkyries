package com.automatavalkyries.protect;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/** 正規退場（プレイヤー敗北・修復が追いつかない場合の退避）時にセリフと効果音を出す保護対象 */
public interface IProviderWithSpeech extends IEprotected {
    /** 正規退場時にチャットへ流すセリフ。null なら表示しない */
    @Nullable
    Component getDefeatSpeech();

    /** 正規退場時の効果音。null なら鳴らさない */
    @Nullable
    default SoundEvent getDefeatSound() {
        return null;
    }
}
