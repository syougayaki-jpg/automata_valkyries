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
    public static final ForgeConfigSpec.BooleanValue BASE_DISTANCE_SCALING;
    public static final ForgeConfigSpec.IntValue BASE_FULL_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue BASE_MIN_CHANCE;

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
        builder.comment("侵食基地の出やすさ（2026-10-30 ユーザー指定「浸食基地は初期リスポーンから遠くにいけばいくほど湧きやすくして」）").push("corruptedBases");
        BASE_DISTANCE_SCALING = builder.comment("true にすると、侵食基地は初めのリスポーン地点から遠いほど出やすくなる（近くでは minChance、fullChanceDistance で今までどおりの数）。false なら距離に関係なく出る")
                .define("distanceScaling", true);
        BASE_FULL_DISTANCE = builder.comment("今までどおりの数で出るようになる距離（ブロック）。T2 の工場の値で、倉庫は 0.5 倍・前哨基地は 0.75 倍・空中庭園要塞は 1.5 倍（structure_set の distance_factor）")
                .defineInRange("fullChanceDistance", 3_000, 0, 1_000_000);
        BASE_MIN_CHANCE = builder.comment("初めのリスポーン地点のすぐそばで出る割合（0〜1。距離に比例して 1 まで上がる）")
                .defineInRange("minChance", 0.1, 0.0, 1.0);
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

    /** 侵食基地の出る割合（初めのリスポーン地点からの距離 distance、ティアの倍率 factor）。設定を読めない間は今までどおり（1） */
    public static double baseChance(double distance, double factor) {
        try {
            if (!BASE_DISTANCE_SCALING.get()) return 1.0;
            double full = BASE_FULL_DISTANCE.get() * factor, min = BASE_MIN_CHANCE.get();
            if (full <= 0) return 1.0;
            return min + (1.0 - min) * Math.min(1.0, distance / full);
        } catch (IllegalStateException notLoaded) {
            return 1.0;
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
