# 設定（コンフィグ）一覧

このMOD の設定ファイルと項目の一覧（2026-10-31 ユーザー指定「コンフィグの設定を纏めて」）。各 `config/*.java` の ForgeConfigSpec から機械的に抜き出した（既定値はソースの式のまま）。
設定ファイルはワールドを開いた後 `config/`（開発環境は `run/config/`）にできる。共通の設定はサーバー側、クライアントの設定は各プレイヤー側。

## よく使う設定

| やりたいこと | ファイル | 項目 |
|---|---|---|
| 墜落ポッドの兵姫を端末だけで起こす（序盤の簡易化） | valkyries | `earlyGame.easyPodWake` = true |
| 戦術級コードで地形を壊す・ボスにも効かせる | valkyries | `tactical.enableTacticalGriefing` / `tactical.tacticalAffectsBosses` = true |
| 侵食基地を初めのリスポーンの近くでも普通に出す | valkyries | `corruptedBases.distanceScaling` = false |
| T4 のマシンをタイム・ルーラーを倒す前から作る | valkyries | `machines.t4RequiresTimeRuler` = false |
| 範囲兵装でブロックを壊す | weapons | `blockDestruction` = true |
| 強い状態異常をプレイヤーにも効かせる | weapons | `strongEffectsOnPlayers` = true |
| 時間加速器を止める・速める物を絞る | machines | `accelerator.enabled` / `accelerator.whitelistMode` |
| 巻き戻し機で鉱石・原石まで戻す | machines | `rewinder.ores` = true |
| 連携金属の鉱石を生成しない（他 MOD に任せる） | worldgen | `ores.<金属>` = false（既定でも他 MOD が生成していれば譲る） |
| HUD・頭上の表示・ダメージの数字 | client | `hud.*` / `overhead.*` / `combat.allDamageNumbers` |
| 守る側の Java エージェントを起動の引数なしで付ける | common | `agents.observer.enabled` / `agents.lifeGuard.enabled` / `agents.valkyrieProtect.enabled` |
| 保護の攻撃の記録・ブラックリストの調整 | common | `attackLog.*` / `blacklist.*`（コマンド `/eprotect blacklist` でも） |

ファイル名の略: common = `automata_valkyries-common.toml`、valkyries = `-valkyries.toml`、machines = `-machines.toml`、weapons = `-weapons.toml`、worldgen = `-worldgen.toml`、client = `-client.toml`。

## automata_valkyries-common.toml（共通・保護）

ソース: `config/EprotectConfig.java`（37 項目）

| 区分 | 項目 | 既定 | 範囲 | 説明 |
|---|---|---|---|---|
| - | `protection.rejectHealthLossOutsideDamage` | true |  | ダメージ処理（hurt）を通らない HP の減少を拒否する |
| - | `protection.restoreUnknownForeignState` | true |  | 死亡の兆候が出た Tick に、辞書に無い名前の外部状態（他 MOD が付けた値）が死亡の兆候に合う変化をしていたら、正常時に控えた値へ戻す（準汎用。名前に頼らないキルへの対） |
| - | `protection.mirrorTimeStopExemption` | true |  | 保護対象が時間停止で止められたとき、動いている個体が持つ時間停止の免除の状態（EndingLibrary の timeStopCount 等）を探して真似る（準汎用。見つからなければ従来どおり Tick を直接回す） |
| - | `protection.restoreEntityDataContainer` | true |  | 保護対象の同期データの入れ物（SynchedEntityData）が他 MOD の継承クラスに作り替えられていたら、元の型の新しい入れ物へ付け替え、その MOD を攻撃として記録する （SuperSteve は HP の読み書きを入れ物の中で切り詰める） |
| - | `protection.combatStalemate` | true |  | 戦闘中（敵がいる間）は印を付け、どちらかの HP が減っている間は修復が積もっても退場しない。 1 分間どちらの HP も減らない千日手で修復が積もったときだけ退場する |
| - | `protection.retreatWhenUnrepairable` | true |  | 異常の修復が追いつかないとき、保護対象を正規の退場処理で安全に退避させる |
| - | `protection.retreatAnomalyThreshold` | 200 | 5 〜 10000 | retreatWindowTicks の間にこの回数だけ異常を修復したら退避する（修復は 2 Tick ごとの軽い確認なので、大きめでも負荷はほぼ増えない） |
| - | `protection.retreatWindowTicks` | 1200 | 20 〜 72000 | 異常の修復回数を数える時間窓（Tick） |
| - | `protection.expandedScope.enabled` | false |  | 保護範囲の拡張（乗り物・同乗者、および強化モード時の結界オーラによる周囲保護）を一括で有効にするか。デフォルトは false（範囲強化なし、保護対象単体のみ防護） |
| - | `protection.expandedScope.protectVehiclesAndPassengers` | false |  | 保護対象が乗っている乗り物（馬・ボート等）および同乗しているエンティティを即死・異常テレポートから保護する |
| - | `protection.expandedScope.reinforcedAura.enabled` | false |  | Eprotect+ 強化モード時、保護対象の周囲（結界オーラ）にいる味方・周囲エンティティへの即死攻撃を遮断する |
| - | `protection.expandedScope.reinforcedAura.radius` | 16.0D | 1.0D 〜 128.0D | 強化モード時の結界オーラの半径（ブロック） |
| - | `diagnostics.reportForeignEntityHooks` | true |  | 起動時に、他 MOD がエンティティ・ワールド管理クラスへ追加した処理をログに報告する（報告のみ） |
| - | `diagnostics.watchdog.enabled` | true |  | メインスレッドの停止（無限ループ攻撃・ハング）を別スレッドで見張り、スタックトレースをログに出す（見張るだけでエンティティには触れない） |
| - | `diagnostics.watchdog.intervalMs` | 100 | 20 〜 5000 | ウォッチドッグの確認周期（ミリ秒） |
| - | `diagnostics.watchdog.hangThresholdMs` | 2000 | 500 〜 60000 | メインスレッドがこの時間進まなければ停止とみなして報告する（ミリ秒） |
| - | `annihilation.deleteOmniMobSaves` | true |  | 超絶抹殺の棒で、ワールドのセーブにある Omni-Mobs の Metapotent Flashfur の保存ファイル（metapotent_flashfurs/*.omnimob）を削除する（元に戻せない） |
| - | `attackLog.enabled` | true |  | 保護対象への攻撃（拒否・修復した操作）を、どの MOD から来たかと一緒に記録する（/eprotect attacks で表示） |
| - | `attackLog.writeFile` | true |  | 攻撃の記録を logs/automata_valkyries-attacks.log にも書き出す |
| - | `attackLog.maxEntries` | 256 | 16 〜 4096 | 記録を集計して保持する件数（MOD・操作・結果の組み合わせごと）。超えた分は other にまとめる |
| - | `blacklist.mods` | List.of() |  | ブラックリストの MOD ID。ここに載った MOD からの即死系の操作（強制移動・次元移動・ダメージ外の HP 減少）も拒否する。通常のダメージは通す |
| - | `blacklist.neverBlacklist` | List.of() |  | 自動でも手動でもブラックリストに載せない MOD ID（minecraft / forge / このMOD は常に除外） |
| - | `blacklist.auto.enabled` | true |  | 保護対象への即死攻撃を繰り返す MOD を自動でブラックリストに載せる |
| - | `blacklist.auto.threshold` | 20 | 1 〜 10000 | blacklist.auto.windowTicks の間にこの回数（1 Tick に 1 回まで数える）即死攻撃を拒否したら自動で載せる |
| - | `blacklist.auto.windowTicks` | 1200 | 20 〜 72000 | 自動登録の回数を数える時間窓（Tick） |
| - | `blacklist.forcedRelocationDistance` | 8.0D | 1.0D 〜 1.0E6D | ブラックリストの MOD による、これより遠くへの移動を強制移動として拒否する（ブロック） |
| - | `blacklist.strictRemoval` | false |  | 次元移動・次元移動を装った消去は、直接の呼び出し元でなくても呼び出し経路にブラックリストの MOD があれば拒否する |
| - | `flagDictionary.extra` | List.of() |  | フラグ辞書に足す項目（"分類:項目"）。分類は kill / control / health_tamper / guard / bypass / revive / count / timer。 項目は 単語の頭（例 kill:smite）、=単語そのもの（例 kill:=doom）、*つなげた名前に含まれる（例 guard:*holyshield） |
| - | `flagDictionary.ignore` | List.of() |  | フラグ辞書で扱わない名前（取り違えの除外。例 "soulBound"）。大文字小文字・区切りは無視して比べる |
| - | `flagDictionary.watch.enabled` | true |  | 辞書補助の監視: 他 MOD の状態を読むだけで見張り、死亡・消去・保護対象への攻撃の直前に変わったのに辞書に無い名前を 候補として logs/automata_valkyries-flag-candidates.txt と /eprotect flags watch に出す（何も書き換えない） |
| - | `flagDictionary.watch.sampleIntervalTicks` | 20 | 1 〜 1200 | 普段の値を読み取る間隔（Tick）。死亡・攻撃の時点の値はこの前回の読み取りと比べる |
| - | `flagDictionary.watch.maxEntities` | 256 | 1 〜 4096 | 見張るエンティティの上限（他 MOD の状態を持つ生き物だけ数える。保護対象は常に見張る） |
| - | `flagDictionary.watch.minEvents` | 2 | 1 〜 1000 | 死亡・攻撃の直前にこの回数以上変わった名前だけを候補にする |
| - | `agents.observer.enabled` | false |  | 観察用エージェント（他のエージェントの書き換えの記録・能力の地図・このMOD のクラスの改ざん防止）を設定で読み込む |
| - | `agents.observer.integrityGuard` | true |  | 観察用エージェントの改ざん防止（このMOD のクラスを他のエージェントが書き換えたら戻す）。false は noguard と同じ |
| - | `agents.lifeGuard.enabled` | false |  | LifeGuard（保護対象の生死の getter を、他のエージェントの偽装より外側で実際の値に直す）を設定で読み込む |
| - | `agents.valkyrieProtect.enabled` | false |  | オートマタヴァルキリー protect（戦術兵姫の消去・即死の入口をバイトコードの段階で守る）を設定で読み込む |

## automata_valkyries-valkyries.toml（兵姫・ゲームの進め方）

ソース: `config/ValkyrieConfig.java`（8 項目）

| 区分 | 項目 | 既定 | 範囲 | 説明 |
|---|---|---|---|---|
| earlyGame | `easyPodWake` | false |  | true にすると、墜落ポッドの兵姫を端末の右クリックだけで目覚めさせられる（満充電のプロトコアは要らない）。 プロトコアを持っていれば、その電力（easyPodEnergy より少なければ easyPodEnergy）が入って使い切る |
| earlyGame | `easyPodEnergy` | 30000 | 1000 〜 100000 | 簡易化で目覚めたときに入る電力（FE） |
| tactical | `enableTacticalGriefing` | false |  | true にすると、戦術級コードで地形も壊れる（CARPET・GUNGNIR・ABYSS・COMET のクレーター、GUNGNIR の岩盤の層までの縦穴、ABYSS のブロックの吸い込み）。false ならモブへのダメージだけ |
| tactical | `tacticalAffectsBosses` | false |  | true にすると、戦術級コードがボスにも効く（ダメージ・吸い込み・吹き飛ばし）。false ならボスには何もしない。 ボス = forge:bosses のタグのあるモブ（ウィザー・エンダードラゴン・他 MOD のボス）と、このMOD のボス |
| corruptedBases | `distanceScaling` | true |  | true にすると、侵食基地は初めのリスポーン地点から遠いほど出やすくなる（近くでは minChance、fullChanceDistance で今までどおりの数）。false なら距離に関係なく出る |
| corruptedBases | `fullChanceDistance` | 3000 | 0 〜 1000000 | 今までどおりの数で出るようになる距離（ブロック）。T2 の工場の値で、倉庫は 0.5 倍・前哨基地は 0.75 倍・空中庭園要塞は 1.5 倍（structure_set の distance_factor） |
| corruptedBases | `minChance` | 0.1 | 0.0 〜 1.0 | 初めのリスポーン地点のすぐそばで出る割合（0〜1。距離に比例して 1 まで上がる） |
| machines | `t4RequiresTimeRuler` | true |  | true にすると、T4（ネバーラスト）のマシン（タクティカルアーモリーも）は、タイム・ルーラーを倒した進捗を持つプレイヤーだけが作れる （作業台・T4 の強化キット・設計図カード）。2026-10-31 ユーザー指定「タクティカルアーモリー以外のティア４マシンレシピはタイムルーラー撃破後に解禁で」 |

## automata_valkyries-machines.toml（機械）

ソース: `config/MachineConfig.java`（10 項目）

| 区分 | 項目 | 既定 | 範囲 | 説明 |
|---|---|---|---|---|
| upgrades | `frame` | true |  | クロノリンク・マシンのアップグレードパーツ（アップデートキット）を、そのティアのルミナルエナジーアダプターで作れるか。 false にするとレシピが読み込まれない（その場での昇格を止め、各ティアのマシンを直接作らせる等）。今はテストのため、すべて true |
| rewinder | `ores` | false |  | 焼いた物を鉱石・原石へ巻き戻してよいか（インゴット → 原石など）。 true にすると、クロノ・スメルターの倍加と合わせて素材を増やし続けられる。既定は false（ゲーム中に変えても効く） |
| accelerator | `enabled` | true |  | 時間加速器を働かせるか（false なら置いても何もしない） |
| accelerator | `blockEntities` | true |  | ブロックエンティティ（かまど・機械など）を速めるか |
| accelerator | `randomTicks` | true |  | ランダムに育つブロック（作物・苗など）を速めるか |
| accelerator | `maxExtraTicks` | 15 | 1 〜 255 | 1 Tick に余分に動かす回数の上限（ティア・兵姫のオペレーターで増えても、ここで止める。T4 は 15） |
| accelerator | `maxTargets` | 64 | 1 〜 4096 | 1 台が速めるブロックエンティティの数の上限（近い順）。重いときはここを下げる |
| accelerator | `whitelistMode` | false |  | true にすると whitelist に書いたブロックだけを速める（blacklist も見る） |
| accelerator | `blacklist` | java.util.List.of("minecraft:spawner", "automata_valkyries:chrono_gate", "torcherino:*") |  | 速めないブロック（加速の重ねがけになる他 MOD の加速器・スポナーなど） |
| accelerator | `whitelist` | java.util.List.of("#minecraft:crops", "#minecraft:saplings", "minecraft:furnace", "minecraft:blast_furnace", "minecra… |  | whitelistMode が true のとき、速めるブロック |

## automata_valkyries-weapons.toml（兵装）

ソース: `config/WeaponConfig.java`（2 項目）

| 区分 | 項目 | 既定 | 範囲 | 説明 |
|---|---|---|---|---|
| - | `blockDestruction` | false |  | 範囲兵装（カタストロフ・バスターの空間崩壊など）がブロックを壊すか。false のときはエンティティにだけ効く |
| - | `strongEffectsOnPlayers` | false |  | 強い状態異常（黒死侵食の最大 HP 永続減少・因果停止・全バフ消去）をプレイヤーにも掛けるか。 false のときは mob・ボスだけ（ダメージそのものはプレイヤーにも入る） |

## automata_valkyries-worldgen.toml（ワールド生成）

ソース: `config/WorldgenConfig.java`（2 項目）

| 区分 | 項目 | 既定 | 範囲 | 説明 |
|---|---|---|---|---|
| ores | `<金属>（tin / lead / silver / nickel / zinc / aluminum / osmium / uranium / steel / bronze / brass / electrum のそれぞれ）` | true |  | 工業 MOD 連携の金属の鉱石を生成するか（同じ金属を生成する MOD があるなら false にして重ねない） |
| ores | `skipWhenOtherModGenerates` | true |  | 他 MOD が同じ金属の鉱石（forge:ores/<金属> のブロック）を同じバイオームに生成するなら、こちらの鉱石は生成しない。 相手の MOD の設定で生成が切られていれば、こちらが生成する |

## automata_valkyries-client.toml（クライアント）

ソース: `config/ValkyrieClientConfig.java`（9 項目）

| 区分 | 項目 | 既定 | 範囲 | 説明 |
|---|---|---|---|---|
| hud | `enabled` | true |  | HUD を出すか |
| hud | `x` | 4 | 0 〜 4096 | 寄せた角からの横のずれ（GUI の px） |
| hud | `y` | 4 | 0 〜 4096 | 寄せた角からの縦のずれ（GUI の px） |
| hud | `anchor` | Anchor.TOP_LEFT |  | 寄せる画面の角 |
| hud | `scale` | 1.0 | 0.4 〜 3.0 | HUD の大きさ（倍）。ゲーム中は HUD の配置の画面（既定の J キー）でホイールを回して変えられる |
| overhead | `enabled` | true |  | 頭上の表示を出すか（false でも吹き出しと感情アイコンは出す） |
| overhead | `distance` | 8.0 | 1.0 〜 64.0 | 名前とバーを出す距離（ブロック） |
| combat | `bossLocator` | true |  | ボスの追跡マーカー（このMOD のボスが 128 m 以内にいる間、画面の縁の矢印・体のまわりの括弧と距離）を出すか |
| combat | `allDamageNumbers` | false |  | どんなダメージでも頭上に数字を出すか（近く 32 m の生き物の HP が減ったら、減った量を出す）。 false でも兵装の大技・訓練用ダミーゾンビの数字はいつもどおり出す |

## Eprotect+ のエージェント（Antigravity の担当・読むだけ）

ソース: `protect/plus/BundledAgents.java`（3 項目）

| 区分 | 項目 | 既定 | 範囲 | 説明 |
|---|---|---|---|---|
| - | `agents.eprotectPlus.enabled` | false |  | Load the bundled Eprotect+ Java agent without -javaagent. Requires a full game/server restart. Late attachment observes current transformer input; it cannot undo earlier interference. |
| - | `agents.flagDictionary.enabled` | false |  | Load the bundled flag dictionary Java agent without -javaagent. Requires a full restart. Startup -javaagent options take precedence; false does not unload an already running agent. |
| - | `agents.madnessCompatibility.passive` | true |  | When Madness Entities is loaded, attach the bundled agents without transformers or retransformation. This avoids interference with Madness; it cannot prevent a halt caused solely by agent presence. |

