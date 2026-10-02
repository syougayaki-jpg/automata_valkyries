package com.automatavalkyries.item;

import com.automatavalkyries.chrono.ChronoTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** ティアの表示色（ツールチップ・画面の縁） */
public final class ChronoTiers {

    private ChronoTiers() {}

    /** 画面・文字の色（RGB）: 白銀のシアン・金・星の金・虹色の基調の紫 */
    public static int rgb(ChronoTier tier) {
        return switch (tier) {
            case HIGH_END -> 0x7DF9FF;
            case FUTURE -> 0xFFD166;
            case BEYOND -> 0xFFE6A0;
            case NEVERLAST -> 0xE0A0FF;
        };
    }

    public static Style color(ChronoTier tier) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb(tier))).withBold(tier.level >= 3);
    }

    public static ChatFormatting legacy(ChronoTier tier) {
        return switch (tier) {
            case HIGH_END -> ChatFormatting.AQUA;
            case FUTURE -> ChatFormatting.GOLD;
            case BEYOND -> ChatFormatting.YELLOW;
            case NEVERLAST -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}
