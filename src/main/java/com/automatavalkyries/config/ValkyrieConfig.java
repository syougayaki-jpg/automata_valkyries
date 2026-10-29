package com.automatavalkyries.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 戦術兵姫の設定（automata_valkyries-valkyries.toml）。
 * 序盤の簡易化（2026-10-07 ユーザー指定「序盤重いのはコンフィグで簡易化設定を入れて。デフォルトはオフ」）
 */
public final class ValkyrieConfig {

    public static final ForgeConfigSpec SPEC;
    /** 墜落ポッドの兵姫を、プロトコア無し（または充電の足りないプロトコア）でも端末で目覚めさせられる */
    public static final ForgeConfigSpec.BooleanValue EASY_POD_WAKE;
    /** 簡易化で目覚めたときの電力（FE） */
    public static final ForgeConfigSpec.IntValue EASY_POD_ENERGY;
    /** 戦術級コードで地形を壊すか（追加拡張仕様書 §13.1） */
    public static final ForgeConfigSpec.BooleanValue TACTICAL_GRIEFING;
    /** 戦術級コードがボスに効くか（2026-10-29 ユーザー指定「ボスに通用するかを設定出来るように、デフォルトはオフ」） */
    public static final ForgeConfigSpec.BooleanValue TACTICAL_VS_BOSSES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("序盤の簡易化（既定はオフ）").push("earlyGame");
        EASY_POD_WAKE = builder.comment("true にすると、墜落ポッドの兵姫を端末の右クリックだけで目覚めさせられる（満充電のプロトコアは要らない）。",
                        "プロトコアを持っていれば、その電力（easyPodEnergy より少なければ easyPodEnergy）が入って使い切る")
                .define("easyPodWake", false);
        EASY_POD_ENERGY = builder.comment("簡易化で目覚めたときに入る電力（FE）")
                .defineInRange("easyPodEnergy", 30_000, 1_000, 100_000);
        builder.pop();
        builder.comment("戦術級コード（追加拡張仕様書 §13）").push("tactical");
        TACTICAL_GRIEFING = builder.comment("true にすると、戦術級コードで地形も壊れる（CARPET・GUNGNIR・ABYSS・COMET のクレーター、GUNGNIR の岩盤の層までの縦穴、ABYSS のブロックの吸い込み）。false ならモブへのダメージだけ")
                .define("enableTacticalGriefing", false);
        TACTICAL_VS_BOSSES = builder.comment("true にすると、戦術級コードがボスにも効く（ダメージ・吸い込み・吹き飛ばし）。false ならボスには何もしない。",
                        "ボス = forge:bosses のタグのあるモブ（ウィザー・エンダードラゴン・他 MOD のボス）と、このMOD のボス")
                .define("tacticalAffectsBosses", false);
        builder.pop();
        SPEC = builder.build();
    }

    private ValkyrieConfig() {}

    public static boolean easyPodWake() {
        try {
            return EASY_POD_WAKE.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    public static boolean tacticalGriefing() {
        try {
            return TACTICAL_GRIEFING.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    public static boolean tacticalAffectsBosses() {
        try {
            return TACTICAL_VS_BOSSES.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    public static int easyPodEnergy() {
        try {
            return EASY_POD_ENERGY.get();
        } catch (IllegalStateException notLoaded) {
            return 30_000;
        }
    }
}
