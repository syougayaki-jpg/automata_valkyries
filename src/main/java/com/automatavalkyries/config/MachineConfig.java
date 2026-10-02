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

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("クロノリンク・マシンのアップグレードパーツ（アップデートキット）を、そのティアのルミナルエナジーアダプターで作れるか。",
                "false にするとレシピが読み込まれない（その場での昇格を止め、各ティアのマシンを直接作らせる等）。今はテストのため、すべて true").push("upgrades");
        for (String frame : new String[]{"upgrade_kit_future", "upgrade_kit_beyond", "upgrade_kit_neverlast"}) {
            FLAGS.put("upgrades." + frame, builder.define(frame, true));
        }
        builder.pop();
        SPEC = builder.build();
    }

    private MachineConfig() {}

    public static boolean enabled(String key) {
        ForgeConfigSpec.BooleanValue value = FLAGS.get(key);
        if (value == null) return true;
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return true;
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
