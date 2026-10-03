package com.automatavalkyries.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 兵器ユニットの設定（automata_valkyries-weapons.toml。兵器ユニット設計書 docs/SPEC_WEAPON_UNITS.md §1）。
 * 範囲兵装のブロック破壊と、強い状態異常をプレイヤーにも掛けるかは、どちらも既定はオフ。
 */
public final class WeaponConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BLOCK_DESTRUCTION;
    public static final ForgeConfigSpec.BooleanValue STRONG_EFFECTS_ON_PLAYERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        BLOCK_DESTRUCTION = builder.comment("範囲兵装（カタストロフ・バスターの空間崩壊など）がブロックを壊すか。false のときはエンティティにだけ効く")
                .define("blockDestruction", false);
        STRONG_EFFECTS_ON_PLAYERS = builder.comment("強い状態異常（黒死侵食の最大 HP 永続減少・因果停止・全バフ消去）をプレイヤーにも掛けるか。",
                        "false のときは mob・ボスだけ（ダメージそのものはプレイヤーにも入る）")
                .define("strongEffectsOnPlayers", false);
        SPEC = builder.build();
    }

    private WeaponConfig() {}

    public static boolean blockDestruction() {
        return get(BLOCK_DESTRUCTION, false);
    }

    public static boolean strongEffectsOnPlayers() {
        return get(STRONG_EFFECTS_ON_PLAYERS, false);
    }

    private static boolean get(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }
}
