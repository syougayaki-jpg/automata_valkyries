package com.automatavalkyries.config;

import com.automatavalkyries.registry.IndustrialMetals;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉱石の生成の設定（automata_valkyries-worldgen.toml）。工業 MOD 連携の金属は、同じ金属を生成する MOD
 * （Mekanism のオスミウム・スズ・鉛・ウラン、Thermal のスズ・鉛・銀・ニッケル、Create の亜鉛 等）と重ならないよう、金属ごとに切れる。
 * 既定では、同じ金属の鉱石を他 MOD が同じバイオームに生成していれば、こちらは自動で生成しない（skipWhenOtherModGenerates）。
 * 生成するかどうかはワールドの読み込み時に決まる（変えたらワールドを読み込み直す）。
 */
public final class WorldgenConfig {

    public static final ForgeConfigSpec SPEC;
    /** 金属 → 鉱石を生成するか */
    public static final Map<String, ForgeConfigSpec.BooleanValue> ORES = new LinkedHashMap<>();
    /** 同じ金属の鉱石を他 MOD がそのバイオームに生成するなら、こちらは生成しない */
    public static final ForgeConfigSpec.BooleanValue SKIP_IF_OTHER_MOD;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("工業 MOD 連携の金属の鉱石を生成するか（同じ金属を生成する MOD があるなら false にして重ねない）").push("ores");
        for (IndustrialMetals.Metal metal : IndustrialMetals.BASE) {
            ORES.put(metal.name(), builder.define(metal.name(), true));
        }
        SKIP_IF_OTHER_MOD = builder.comment("他 MOD が同じ金属の鉱石（forge:ores/<金属> のブロック）を同じバイオームに生成するなら、こちらの鉱石は生成しない。",
                        "相手の MOD の設定で生成が切られていれば、こちらが生成する")
                .define("skipWhenOtherModGenerates", true);
        builder.pop();
        SPEC = builder.build();
    }

    private WorldgenConfig() {}

    /** 他 MOD が生成する金属を自動で外すか */
    public static boolean skipIfOtherMod() {
        try {
            return SKIP_IF_OTHER_MOD.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** その金属の鉱石を生成するか（設定にない金属は生成する） */
    public static boolean oreEnabled(String metal) {
        ForgeConfigSpec.BooleanValue value = ORES.get(metal);
        if (value == null) return true;
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return true; // 設定の読み込み前（GameTest のデータ生成など）
        }
    }
}
