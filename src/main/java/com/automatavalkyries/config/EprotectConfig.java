package com.automatavalkyries.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** eprotect の設定（config/automata_valkyries-common.toml） */
public final class EprotectConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue REJECT_HEALTH_LOSS_OUTSIDE_DAMAGE = BUILDER
            .comment("ダメージ処理（hurt）を通らない HP の減少を拒否する")
            .define("protection.rejectHealthLossOutsideDamage", true);

    public static final ForgeConfigSpec.BooleanValue RESTORE_UNKNOWN_FOREIGN_STATE = BUILDER
            .comment("死亡の兆候が出た Tick に、辞書に無い名前の外部状態（他 MOD が付けた値）が死亡の兆候に合う変化をしていたら、正常時に控えた値へ戻す（準汎用。名前に頼らないキルへの対）")
            .define("protection.restoreUnknownForeignState", true);

    public static final ForgeConfigSpec.BooleanValue MIRROR_TIME_STOP_EXEMPTION = BUILDER
            .comment("保護対象が時間停止で止められたとき、動いている個体が持つ時間停止の免除の状態（EndingLibrary の timeStopCount 等）を探して真似る（準汎用。見つからなければ従来どおり Tick を直接回す）")
            .define("protection.mirrorTimeStopExemption", true);

    public static final ForgeConfigSpec.BooleanValue RESTORE_ENTITY_DATA_CONTAINER = BUILDER
            .comment("保護対象の同期データの入れ物（SynchedEntityData）が他 MOD の継承クラスに作り替えられていたら、元の型の新しい入れ物へ付け替え、その MOD を攻撃として記録する"
                    + "（SuperSteve は HP の読み書きを入れ物の中で切り詰める）")
            .define("protection.restoreEntityDataContainer", true);

    public static final ForgeConfigSpec.BooleanValue COMBAT_STALEMATE = BUILDER
            .comment("戦闘中（敵がいる間）は印を付け、どちらかの HP が減っている間は修復が積もっても退場しない。"
                    + "1 分間どちらの HP も減らない千日手で修復が積もったときだけ退場する")
            .define("protection.combatStalemate", true);

    public static final ForgeConfigSpec.BooleanValue RETREAT_WHEN_UNREPAIRABLE = BUILDER
            .comment("異常の修復が追いつかないとき、保護対象を正規の退場処理で安全に退避させる")
            .define("protection.retreatWhenUnrepairable", true);

    public static final ForgeConfigSpec.IntValue RETREAT_ANOMALY_THRESHOLD = BUILDER
            .comment("retreatWindowTicks の間にこの回数だけ異常を修復したら退避する（修復は 2 Tick ごとの軽い確認なので、大きめでも負荷はほぼ増えない）")
            .defineInRange("protection.retreatAnomalyThreshold", 200, 5, 10000);

    public static final ForgeConfigSpec.IntValue RETREAT_WINDOW_TICKS = BUILDER
            .comment("異常の修復回数を数える時間窓（Tick）")
            .defineInRange("protection.retreatWindowTicks", 1200, 20, 72000);

    public static final ForgeConfigSpec.BooleanValue EXPANDED_SCOPE_ENABLED = BUILDER
            .comment("保護範囲の拡張（乗り物・同乗者、および強化モード時の結界オーラによる周囲保護）を一括で有効にするか。デフォルトは false（範囲強化なし、保護対象単体のみ防護）")
            .define("protection.expandedScope.enabled", false);

    public static final ForgeConfigSpec.BooleanValue PROTECT_VEHICLES_AND_PASSENGERS = BUILDER
            .comment("保護対象が乗っている乗り物（馬・ボート等）および同乗しているエンティティを即死・異常テレポートから保護する")
            .define("protection.expandedScope.protectVehiclesAndPassengers", false);

    public static final ForgeConfigSpec.BooleanValue REINFORCED_AURA_ENABLED = BUILDER
            .comment("Eprotect+ 強化モード時、保護対象の周囲（結界オーラ）にいる味方・周囲エンティティへの即死攻撃を遮断する")
            .define("protection.expandedScope.reinforcedAura.enabled", false);

    public static final ForgeConfigSpec.DoubleValue REINFORCED_AURA_RADIUS = BUILDER
            .comment("強化モード時の結界オーラの半径（ブロック）")
            .defineInRange("protection.expandedScope.reinforcedAura.radius", 16.0D, 1.0D, 128.0D);

    public static final ForgeConfigSpec.BooleanValue REPORT_FOREIGN_ENTITY_HOOKS = BUILDER
            .comment("起動時に、他 MOD がエンティティ・ワールド管理クラスへ追加した処理をログに報告する（報告のみ）")
            .define("diagnostics.reportForeignEntityHooks", true);

    public static final ForgeConfigSpec.BooleanValue WATCHDOG_ENABLED = BUILDER
            .comment("メインスレッドの停止（無限ループ攻撃・ハング）を別スレッドで見張り、スタックトレースをログに出す（見張るだけでエンティティには触れない）")
            .define("diagnostics.watchdog.enabled", true);

    public static final ForgeConfigSpec.IntValue WATCHDOG_INTERVAL_MS = BUILDER
            .comment("ウォッチドッグの確認周期（ミリ秒）")
            .defineInRange("diagnostics.watchdog.intervalMs", 100, 20, 5000);

    public static final ForgeConfigSpec.IntValue WATCHDOG_HANG_THRESHOLD_MS = BUILDER
            .comment("メインスレッドがこの時間進まなければ停止とみなして報告する（ミリ秒）")
            .defineInRange("diagnostics.watchdog.hangThresholdMs", 2000, 500, 60000);

    public static final ForgeConfigSpec.BooleanValue ANNIHILATION_DELETE_OMNIMOB_SAVES = BUILDER
            .comment("超絶抹殺の棒で、ワールドのセーブにある Omni-Mobs の Metapotent Flashfur の保存ファイル（metapotent_flashfurs/*.omnimob）を削除する（元に戻せない）")
            .define("annihilation.deleteOmniMobSaves", true);

    public static final ForgeConfigSpec.BooleanValue ATTACK_LOG_ENABLED = BUILDER
            .comment("保護対象への攻撃（拒否・修復した操作）を、どの MOD から来たかと一緒に記録する（/eprotect attacks で表示）")
            .define("attackLog.enabled", true);

    public static final ForgeConfigSpec.BooleanValue ATTACK_LOG_WRITE_FILE = BUILDER
            .comment("攻撃の記録を logs/automata_valkyries-attacks.log にも書き出す")
            .define("attackLog.writeFile", true);

    public static final ForgeConfigSpec.IntValue ATTACK_LOG_MAX_ENTRIES = BUILDER
            .comment("記録を集計して保持する件数（MOD・操作・結果の組み合わせごと）。超えた分は other にまとめる")
            .defineInRange("attackLog.maxEntries", 256, 16, 4096);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_MODS = BUILDER
            .comment("ブラックリストの MOD ID。ここに載った MOD からの即死系の操作（強制移動・次元移動・ダメージ外の HP 減少）も拒否する。通常のダメージは通す")
            .defineListAllowEmpty("blacklist.mods", List.of(), o -> o instanceof String s && ModIdCheck.isValid(s));

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_NEVER = BUILDER
            .comment("自動でも手動でもブラックリストに載せない MOD ID（minecraft / forge / このMOD は常に除外）")
            .defineListAllowEmpty("blacklist.neverBlacklist", List.of(), o -> o instanceof String s && ModIdCheck.isValid(s));

    public static final ForgeConfigSpec.BooleanValue BLACKLIST_AUTO_ENABLED = BUILDER
            .comment("保護対象への即死攻撃を繰り返す MOD を自動でブラックリストに載せる")
            .define("blacklist.auto.enabled", true);

    public static final ForgeConfigSpec.IntValue BLACKLIST_AUTO_THRESHOLD = BUILDER
            .comment("blacklist.auto.windowTicks の間にこの回数（1 Tick に 1 回まで数える）即死攻撃を拒否したら自動で載せる")
            .defineInRange("blacklist.auto.threshold", 20, 1, 10000);

    public static final ForgeConfigSpec.IntValue BLACKLIST_AUTO_WINDOW_TICKS = BUILDER
            .comment("自動登録の回数を数える時間窓（Tick）")
            .defineInRange("blacklist.auto.windowTicks", 1200, 20, 72000);

    public static final ForgeConfigSpec.DoubleValue BLACKLIST_FORCED_RELOCATION_DISTANCE = BUILDER
            .comment("ブラックリストの MOD による、これより遠くへの移動を強制移動として拒否する（ブロック）")
            .defineInRange("blacklist.forcedRelocationDistance", 8.0D, 1.0D, 1.0E6D);

    public static final ForgeConfigSpec.BooleanValue BLACKLIST_STRICT_REMOVAL = BUILDER
            .comment("次元移動・次元移動を装った消去は、直接の呼び出し元でなくても呼び出し経路にブラックリストの MOD があれば拒否する")
            .define("blacklist.strictRemoval", false);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLAG_EXTRA = BUILDER
            .comment("フラグ辞書に足す項目（\"分類:項目\"）。分類は kill / control / health_tamper / guard / bypass / revive / count / timer。",
                    "項目は 単語の頭（例 kill:smite）、=単語そのもの（例 kill:=doom）、*つなげた名前に含まれる（例 guard:*holyshield）")
            .defineListAllowEmpty("flagDictionary.extra", List.of(), o -> o instanceof String s && s.indexOf(':') > 0);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLAG_IGNORE = BUILDER
            .comment("フラグ辞書で扱わない名前（取り違えの除外。例 \"soulBound\"）。大文字小文字・区切りは無視して比べる")
            .defineListAllowEmpty("flagDictionary.ignore", List.of(), o -> o instanceof String);

    public static final ForgeConfigSpec.BooleanValue FLAG_WATCH_ENABLED = BUILDER
            .comment("辞書補助の監視: 他 MOD の状態を読むだけで見張り、死亡・消去・保護対象への攻撃の直前に変わったのに辞書に無い名前を",
                    "候補として logs/automata_valkyries-flag-candidates.txt と /eprotect flags watch に出す（何も書き換えない）")
            .define("flagDictionary.watch.enabled", true);

    public static final ForgeConfigSpec.IntValue FLAG_WATCH_SAMPLE_INTERVAL_TICKS = BUILDER
            .comment("普段の値を読み取る間隔（Tick）。死亡・攻撃の時点の値はこの前回の読み取りと比べる")
            .defineInRange("flagDictionary.watch.sampleIntervalTicks", 20, 1, 1200);

    public static final ForgeConfigSpec.IntValue FLAG_WATCH_MAX_ENTITIES = BUILDER
            .comment("見張るエンティティの上限（他 MOD の状態を持つ生き物だけ数える。保護対象は常に見張る）")
            .defineInRange("flagDictionary.watch.maxEntities", 256, 1, 4096);

    public static final ForgeConfigSpec.IntValue FLAG_WATCH_MIN_EVENTS = BUILDER
            .comment("死亡・攻撃の直前にこの回数以上変わった名前だけを候補にする")
            .defineInRange("flagDictionary.watch.minEvents", 2, 1, 1000);

    // ---- 守る側の Java エージェントを、起動の引数（-javaagent）を付けずに設定で読み込む（2026-10-09 ユーザー指定） ----
    // MOD の jar に入っているエージェントを <ゲームのフォルダ>/automata_valkyries/agents/ へ取り出し、起動の途中で別のプロセスから
    // このゲームへ付ける（同じプロセスから自分へは付けられない）。起動の引数で付けてあれば何もしない。変えたらゲームを起動し直す

    public static final ForgeConfigSpec.BooleanValue AGENT_OBSERVER = BUILDER
            .comment("観察用エージェント（他のエージェントの書き換えの記録・能力の地図・このMOD のクラスの改ざん防止）を設定で読み込む")
            .define("agents.observer.enabled", false);

    public static final ForgeConfigSpec.BooleanValue AGENT_OBSERVER_GUARD = BUILDER
            .comment("観察用エージェントの改ざん防止（このMOD のクラスを他のエージェントが書き換えたら戻す）。false は noguard と同じ")
            .define("agents.observer.integrityGuard", true);

    public static final ForgeConfigSpec.BooleanValue AGENT_LIFEGUARD = BUILDER
            .comment("LifeGuard（保護対象の生死の getter を、他のエージェントの偽装より外側で実際の値に直す）を設定で読み込む")
            .define("agents.lifeGuard.enabled", false);

    public static final ForgeConfigSpec.BooleanValue AGENT_VALKYRIE_PROTECT = BUILDER
            .comment("オートマタヴァルキリー protect（戦術兵姫の消去・即死の入口をバイトコードの段階で守る）を設定で読み込む")
            .define("agents.valkyrieProtect.enabled", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private EprotectConfig() {}

    /** MOD ID として妥当な文字列か */
    public static final class ModIdCheck {
        private ModIdCheck() {}

        public static boolean isValid(String id) {
            return id.matches("[a-z0-9_.-]{1,64}");
        }
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static List<? extends String> getList(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

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

    @org.jetbrains.annotations.Nullable
    private static volatile Boolean testScopeOverride = null;

    /** テスト・GameTest 用の保護範囲切り替えオーバーライド（null で解除） */
    public static void setExpandedScopeForTesting(@org.jetbrains.annotations.Nullable Boolean override) {
        testScopeOverride = override;
    }

    /** 保護範囲の拡張（乗り物・同乗者・結界オーラ）全体が有効化されているか */
    public static boolean isExpandedScopeEnabled() {
        if (testScopeOverride != null) return testScopeOverride;
        return get(EXPANDED_SCOPE_ENABLED);
    }

    /** 乗り物・同乗者への保護波及が有効化されているか */
    public static boolean isProtectVehiclesAndPassengersEnabled() {
        if (testScopeOverride != null) return testScopeOverride;
        return get(EXPANDED_SCOPE_ENABLED) || get(PROTECT_VEHICLES_AND_PASSENGERS);
    }

    /** 強化モード時の結界オーラ範囲保護が有効化されているか */
    public static boolean isReinforcedAuraEnabled() {
        if (testScopeOverride != null) return testScopeOverride;
        return get(EXPANDED_SCOPE_ENABLED) || get(REINFORCED_AURA_ENABLED);
    }
}
