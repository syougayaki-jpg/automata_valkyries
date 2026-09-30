package com.automatavalkyries.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** eprotect の設定（config/automata_valkyries-common.toml） */
public final class EprotectConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue REJECT_HEALTH_LOSS_OUTSIDE_DAMAGE = BUILDER
            .comment("ダメージ処理（hurt）を通らない HP の減少を拒否する")
            .define("protection.rejectHealthLossOutsideDamage", true);

    public static final ForgeConfigSpec.BooleanValue RETREAT_WHEN_UNREPAIRABLE = BUILDER
            .comment("異常の修復が追いつかないとき、保護対象を正規の退場処理で安全に退避させる")
            .define("protection.retreatWhenUnrepairable", true);

    public static final ForgeConfigSpec.IntValue RETREAT_ANOMALY_THRESHOLD = BUILDER
            .comment("retreatWindowTicks の間にこの回数だけ異常を修復したら退避する（修復は 2 Tick ごとの軽い確認なので、大きめでも負荷はほぼ増えない）")
            .defineInRange("protection.retreatAnomalyThreshold", 200, 5, 10000);

    public static final ForgeConfigSpec.IntValue RETREAT_WINDOW_TICKS = BUILDER
            .comment("異常の修復回数を数える時間窓（Tick）")
            .defineInRange("protection.retreatWindowTicks", 1200, 20, 72000);

    public static final ForgeConfigSpec.BooleanValue REPORT_FOREIGN_ENTITY_HOOKS = BUILDER
            .comment("起動時に、他 MOD がエンティティ・ワールド管理クラスへ追加した処理をログに報告する（報告のみ）")
            .define("diagnostics.reportForeignEntityHooks", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private EprotectConfig() {}

    /** 設定の読み込み前（GameTest の一部経路など）でも既定値で動くようにする */
    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }
}
