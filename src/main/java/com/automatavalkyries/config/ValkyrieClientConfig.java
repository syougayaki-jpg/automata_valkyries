package com.automatavalkyries.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 戦術兵姫の表示の設定（クライアント。automata_valkyries-client.toml。SPEC_VALKYRIE_GUI_AI §2.5）。
 * HUD（追従中の兵姫を最大 4 体）の表示・位置と、頭上の名前・バーを出す距離。
 * 2026-10-29 ユーザー指定「ボスの追跡マーカーをオンオフ」「ダメージ表記をどんなダメージでも出すかをオンオフ」で表示の項目を足した（ゲーム中にファイルを書き換えても効く）。
 */
public final class ValkyrieClientConfig {

    /** HUD を寄せる画面の角 */
    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    public static final ForgeConfigSpec.IntValue HUD_X;
    public static final ForgeConfigSpec.IntValue HUD_Y;
    public static final ForgeConfigSpec.EnumValue<Anchor> HUD_ANCHOR;
    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;
    public static final ForgeConfigSpec.BooleanValue OVERHEAD_ENABLED;
    public static final ForgeConfigSpec.DoubleValue OVERHEAD_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue BOSS_LOCATOR;
    public static final ForgeConfigSpec.BooleanValue ALL_DAMAGE_NUMBERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("追従中の兵姫の HUD（顔・HP・シールド・電力）").push("hud");
        HUD_ENABLED = builder.comment("HUD を出すか").define("enabled", true);
        HUD_X = builder.comment("寄せた角からの横のずれ（GUI の px）").defineInRange("x", 4, 0, 4096);
        HUD_Y = builder.comment("寄せた角からの縦のずれ（GUI の px）").defineInRange("y", 4, 0, 4096);
        HUD_ANCHOR = builder.comment("寄せる画面の角").defineEnum("anchor", Anchor.TOP_LEFT);
        HUD_SCALE = builder.comment("HUD の大きさ（倍）。ゲーム中は HUD の配置の画面（既定の J キー）でホイールを回して変えられる").defineInRange("scale", 1.0, 0.4, 3.0);
        builder.pop();
        builder.comment("頭上の名前・HP/シールドのバー・感情アイコン・吹き出し").push("overhead");
        OVERHEAD_ENABLED = builder.comment("頭上の表示を出すか（false でも吹き出しと感情アイコンは出す）").define("enabled", true);
        OVERHEAD_DISTANCE = builder.comment("名前とバーを出す距離（ブロック）").defineInRange("distance", 8.0, 1.0, 64.0);
        builder.pop();
        builder.comment("戦いの表示").push("combat");
        BOSS_LOCATOR = builder.comment("ボスの追跡マーカー（このMOD のボスが 128 m 以内にいる間、画面の縁の矢印・体のまわりの括弧と距離）を出すか").define("bossLocator", true);
        ALL_DAMAGE_NUMBERS = builder.comment("どんなダメージでも頭上に数字を出すか（近く 32 m の生き物の HP が減ったら、減った量を出す）。",
                "false でも兵装の大技・訓練用ダミーゾンビの数字はいつもどおり出す").define("allDamageNumbers", false);
        builder.pop();
        SPEC = builder.build();
    }

    private ValkyrieClientConfig() {}

    public static boolean hudEnabled() {
        return get(HUD_ENABLED, true);
    }

    public static int hudX() {
        return get(HUD_X, 4);
    }

    public static int hudY() {
        return get(HUD_Y, 4);
    }

    public static Anchor hudAnchor() {
        return get(HUD_ANCHOR, Anchor.TOP_LEFT);
    }

    public static double hudScale() {
        return get(HUD_SCALE, 1.0);
    }

    /** HUD の表示・位置・大きさを書き換えて保存する（HUD の配置の画面・表示切り替えのキーから。2026-10-07） */
    public static void saveHud(boolean enabled, Anchor anchor, int x, int y, double scale) {
        try {
            HUD_ENABLED.set(enabled);
            HUD_ANCHOR.set(anchor);
            HUD_X.set(Math.max(0, Math.min(4096, x)));
            HUD_Y.set(Math.max(0, Math.min(4096, y)));
            HUD_SCALE.set(Math.max(0.4, Math.min(3.0, scale)));
            SPEC.save();
        } catch (IllegalStateException notLoaded) {
            // 設定が読み込まれる前（起きないはず）
        }
    }

    public static boolean overheadEnabled() {
        return get(OVERHEAD_ENABLED, true);
    }

    public static double overheadDistance() {
        return get(OVERHEAD_DISTANCE, 8.0);
    }

    public static boolean bossLocator() {
        return get(BOSS_LOCATOR, true);
    }

    public static boolean allDamageNumbers() {
        return get(ALL_DAMAGE_NUMBERS, false);
    }

    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }
}
