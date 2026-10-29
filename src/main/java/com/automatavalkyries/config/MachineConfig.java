package com.automatavalkyries.config;

import com.automatavalkyries.AutomataValkyries;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * マシンの設定（automata_valkyries-machines.toml）。クロノリンク・マシンのアップグレードパーツ（アップデートキット）を
 * アダプターで作れるかをティアごとに切れる（今はテストのため、すべて作れる）。
 * レシピの JSON は {@code "conditions": [ { "type": "automata_valkyries:config", "key": "upgrades.upgrade_kit_future" } ]} で読む。
 * 設定はワールドの読み込み（データパックの読み込み）のときに効く。
 */
public final class MachineConfig {

    public static final ForgeConfigSpec SPEC;
    /** 設定の名前（"upgrades.upgrade_kit_future" など）→ 値 */
    public static final Map<String, ForgeConfigSpec.BooleanValue> FLAGS = new LinkedHashMap<>();
    /** 時間巻き戻し機で、焼いた物を鉱石・原石へ戻してよいか（2026-10-29 ユーザー指定「コンフィグで設定出来るように、もとはオフで」） */
    public static final ForgeConfigSpec.BooleanValue REWIND_ORES;
    public static final String REWIND_ORES_KEY = "rewinder.ores";
    /** 時間加速器の設定（2026-10-29 ユーザー指定「取り扱いミスるとえぐい重いので、多重加速を防ぎ、加速させるブロックまで詳しく設定出来るように」） */
    public static final ForgeConfigSpec.BooleanValue ACCEL_ENABLED, ACCEL_BLOCK_ENTITIES, ACCEL_RANDOM_TICKS, ACCEL_WHITELIST_MODE;
    public static final ForgeConfigSpec.IntValue ACCEL_MAX_EXTRA, ACCEL_MAX_TARGETS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ACCEL_BLACKLIST, ACCEL_WHITELIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("クロノリンク・マシンのアップグレードパーツ（アップデートキット）を、そのティアのルミナルエナジーアダプターで作れるか。",
                "false にするとレシピが読み込まれない（その場での昇格を止め、各ティアのマシンを直接作らせる等）。今はテストのため、すべて true").push("upgrades");
        for (String frame : new String[]{"upgrade_kit_future", "upgrade_kit_beyond", "upgrade_kit_neverlast"}) {
            FLAGS.put("upgrades." + frame, builder.define(frame, true));
        }
        builder.pop();
        builder.comment("時間巻き戻し機（chrono_rewinder）").push("rewinder");
        REWIND_ORES = builder.comment("焼いた物を鉱石・原石へ巻き戻してよいか（インゴット → 原石など）。",
                "true にすると、クロノ・スメルターの倍加と合わせて素材を増やし続けられる。既定は false（ゲーム中に変えても効く）").define("ores", false);
        builder.pop();
        builder.comment("時間加速器（chrono_accelerator）。どれもゲーム中に変えても効く。",
                "同じブロックは、範囲が重なった何台の加速器からも 1 Tick に 1 台分しか速めない。加速器そのものも、他から速められても 1 Tick に 1 回しか働かない",
                "ブロックの書き方: \"minecraft:furnace\"（ブロック）、\"#minecraft:crops\"（ブロックのタグ）、\"create:*\"（その MOD のすべて）").push("accelerator");
        ACCEL_ENABLED = builder.comment("時間加速器を働かせるか（false なら置いても何もしない）").define("enabled", true);
        ACCEL_BLOCK_ENTITIES = builder.comment("ブロックエンティティ（かまど・機械など）を速めるか").define("blockEntities", true);
        ACCEL_RANDOM_TICKS = builder.comment("ランダムに育つブロック（作物・苗など）を速めるか").define("randomTicks", true);
        ACCEL_MAX_EXTRA = builder.comment("1 Tick に余分に動かす回数の上限（ティア・兵姫のオペレーターで増えても、ここで止める。T4 は 15）").defineInRange("maxExtraTicks", 15, 1, 255);
        ACCEL_MAX_TARGETS = builder.comment("1 台が速めるブロックエンティティの数の上限（近い順）。重いときはここを下げる").defineInRange("maxTargets", 64, 1, 4096);
        ACCEL_WHITELIST_MODE = builder.comment("true にすると whitelist に書いたブロックだけを速める（blacklist も見る）").define("whitelistMode", false);
        ACCEL_BLACKLIST = builder.comment("速めないブロック（加速の重ねがけになる他 MOD の加速器・スポナーなど）").defineListAllowEmpty("blacklist",
                java.util.List.of("minecraft:spawner", "automata_valkyries:chrono_gate", "torcherino:*"), o -> o instanceof String);
        ACCEL_WHITELIST = builder.comment("whitelistMode が true のとき、速めるブロック").defineListAllowEmpty("whitelist",
                java.util.List.of("#minecraft:crops", "#minecraft:saplings", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:brewing_stand"),
                o -> o instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    /**
     * GameTest 用の上書き（保存された設定には書かない）。2026-10-09: テストが設定そのものを false → true と書き換えていたが、
     * Forge の設定の自動保存とファイルの見張りが競り合い、false のまま保存されて次の実行のテストが落ち、遊ぶときも作れなくなっていた
     */
    public static final Map<String, Boolean> TEST_OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();

    private MachineConfig() {}

    public static boolean enabled(String key) {
        Boolean forced = TEST_OVERRIDES.get(key);
        if (forced != null) return forced;
        ForgeConfigSpec.BooleanValue value = FLAGS.get(key);
        if (value == null) return true;
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** 時間巻き戻し機で鉱石・原石へ戻してよいか（読み込み前・設定が無いときは false） */
    public static boolean rewindOres() {
        Boolean forced = TEST_OVERRIDES.get(REWIND_ORES_KEY);
        if (forced != null) return forced;
        try {
            return REWIND_ORES.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /** 設定の値（読み込み前は fallback） */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }

    /** レシピの条件: {@code { "type": "automata_valkyries:config", "key": "upgrades.upgrade_kit_future" }} */
    public record Condition(String key) implements ICondition {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "config");

        @Override public ResourceLocation getID() { return ID; }
        @Override public boolean test(IContext context) { return enabled(key); }

        public static final IConditionSerializer<Condition> SERIALIZER = new IConditionSerializer<>() {
            @Override public void write(JsonObject json, Condition value) { json.addProperty("key", value.key); }
            @Override public Condition read(JsonObject json) { return new Condition(GsonHelper.getAsString(json, "key")); }
            @Override public ResourceLocation getID() { return ID; }
        };
    }
}
