# 終焉機構の改善点（2026-10-08）

対象: Minecraft 1.20.1 / Forge 47.4.23 / Java 17。現在の作業ツリーを解析した。今回は製品コードを変更せず、優先順位と再現結果を整理した。

## 1. 現在の経路

`TerminusStickItem` / `TerminusAttackGoal` / 最果てのエレノア → `TerminusEngine.executeTerminus`

- 保護対象を判定し、ボスバーを解除して耐性追跡に登録する。
- ServerPlayer: `TerminusPlayerExecutor` で防御解除・死亡・リスポーン待ちの修復。
- その他: `GenericCheatMobNeutralizer.neutralize` → `TrueAnnihilationEngine.annihilateEntity`。
- 範囲終焉: 上記を対象ごとに繰り返し、さらに全体清掃と天候変更を行う。

前回の Eprotect+ 改良で、TrueAnnihilationEngine 内の単体抹殺に付随する全保存削除を除去し、最後の退去確認を追加した。しかし **その前に呼ばれる GenericCheatMobNeutralizer には全保存削除が残る**。終焉の経路全体では問題が解決していない。

## 2. 優先順位

| 優先度 | 改善点 | 現在の問題 | 推奨する方向 |
|---|---|---|---|
| P0 | 保存清掃を対象限定にする | バニラ対象でも既知3フォルダを丸ごと削除。外部対象では MOD 名のフォルダ全体も削除 | 対象の UUID/所有情報を確認できる保存だけ、対応形式別に処理。所有不明は削除しない |
| P0 | 本体・共有サービスを区別する | 対象への逆参照が無い他 MOD の参照も本体とみなし close/dispose を呼ぶ | 相互参照・所有情報を確認。共有物は対象参照だけを除去 |
| P0 | 型に合った低レベル書き込み | byte/short/char まで putInt で書く | 型別の書き込み。全メモリ消去を段階的な最後の手段にする |
| P1 | 耐性追跡の開始時刻を維持する | 攻撃ごとに record を上書きし、継続攻撃時の10秒耐性を検知できない | 同じ実体の再攻撃では開始を保持。別実体・新しい試行は区別 |
| P1 | プレイヤー死亡の成功契約 | 最終検証が失敗しても true。HP0と死亡処理完了を混同 | 検証結果を返し、死亡処理と画面通知を一回だけ実行 |
| P1 | 入口と genericOnly の境界 | 前処理の前にワールド・スレッドを検証しない。既知 MOD 処理に切替漏れ | 入口で対象方針を統一し、既知 MOD の清掃を互換層へ集約 |
| P1 | 解体の順序と参照の保持 | Generic 側のゼロクリア後に True 側が後始末用の参照を集める | 全経路で一つの実行 context を使い、収集→対象限定解体→消去→検証 |
| P2 | 範囲終焉の負荷を分散する | 半径500の対象を同期処理。アイテムにクールダウンが無い | 対象キュー・Tick ごとの処理予算・再要求の集約 |
| P2 | クライアントの死亡通知を実体に結び付ける | packet の playerId を使わず UUID だけで適用 | ID/世代を、実際に処理する時点の LocalPlayer と照合 |
| P2 | 失敗の観測・テスト・仕様説明 | 多数の例外握り潰し。10秒テストは手動発動。旧仕様書の説明が現行能力とずれる | 段階ごとの結果・失敗理由と実時間/継続攻撃テストを追加 |

## 3. P0 の根拠

### 3.1 保存データの所有を確認せず削除する

`GenericCheatMobNeutralizer.purgeModSaveFiles`（427行付近）は、対象の種類によらず `metapotent_flashfurs` / `omnimobs` / `witherzilla` を再帰削除する。さらに外部 MOD の対象では、ワールド直下の MOD 名ディレクトリを丸ごと削除する。対象 UUID、同じ MOD の別個体、ボス以外のデータを区別しない。

この関数には `SpecificCountermeasures.enabled()` と `ANNIHILATION_DELETE_OMNIMOB_SAVES` の確認がない。別の `wipeFlashfurDiskSaves` にある切替・設定を通らない。したがって genericOnly でも、通常の単体終焉からこの削除へ入る。

範囲処理の `TerminusEngine.executeAreaTerminus`（135行付近）も、選択した対象とは別に全体の Flashfur メモリ・保存・OmniMobs リストを清掃する。対象0件でも呼ぶ。ここは対応形式の把握と対象限定清掃へ置き換える必要がある。全体の EventBus 復元は、選択した個体の清掃と分けて、必要性と結果を報告する。

### 3.2 共有物を本体とみなす

`GenericCheatMobNeutralizer.dismantleControllers`（124行付近）の条件は「非static・非primitive・非Entity・他 MOD のオブジェクト」で、対象への逆参照を要求しない。コメントでは再帰探索としているが、ここで探索するのは対象の直接フィールドである。

その参照に `dismantleCoreObject` を呼び、close/dispose 等の終了メソッドや防御名の Collection/Map の clear を実行する。共有サービスや共有保護リストでも対象以外に影響し得る。

既存の `ModProtectionBreaker.removeControllers`（255行付近）は `pointsBackTo(controller, target)` を要求しており、こちらへ整理する足場がある。ただし逆参照があっても共有物である可能性は残るため、clear より対象参照の除去を優先する。

### 3.3 Unsafe の書き込み幅

`UnsafeHelper.obliterateObjectMemory`（189行付近）は byte/short/int/char を同じ putInt に渡す。ラッパー（105行付近）は `Unsafe.putInt` をそのまま呼ぶため、型に合った書き込みではない。配置次第で隣接するフィールドへ影響する可能性がある。これはコード点検による指摘で、JVMを壊す再現実験はしていない。

型別処理に加えて、辞書で選んだフィールド・対象所有のコントローラーの解除を先に行い、消去が完了しているならゼロクリアまで進まない構成が望ましい。共通 utility の担当境界は実装時に確認し、Claude の共有ファイルは変更しない。

## 4. 耐性追跡

`trackTarget`（154行付近）の put は毎回 TargetRecord を新しくする。最果てのエレノアは30Tick間隔の終焉を持つため、失敗し続ける同じ相手に対して、開始時刻・開始Tick・発動済み状態が上書きされる。

開始は同じ実体への最初の試行から保持し、最終攻撃時刻・試行回数を別に持つ。単純な putIfAbsent だけでは同じ UUID の別実体やリスポーンを取り違えるので、弱参照の実体 identity、サーバー、必要なら次元・試行世代を確認する。

関連する問題:

- 生存・除去判定は `entity.isRemoved()` / `entity.isAlive()`（177行付近）で、getter 偽装により追跡を捨て得る。既存の DirectRead とワールドの UUID/ID 検索を組み合わせる。HP0だけで「退去済み」とはしない。
- `reinforcementTriggered=true` を先に立て、他の強化が有効なら発動しない（189行付近）。その強化が切れた後も対象の追跡では再発動しない。既存の強化が十分な期間を覆っているかを判定するか、要求を保留する。
- 時計は currentTimeMillis。時計の前進でTickが進んでいなくても発動する。**200Tickまたは10,000ms**という現行仕様は維持したまま、実時間は nanoTime の差分にする。
- static な追跡 map はサーバー停止時の明示的な清掃が無い。弱参照は値側のみで、UUIDキーの存続・別セッションへの持越しを直接管理しない。停止・ログアウト・新実体への置換を明示する。

## 5. プレイヤー死亡

### 成功判定

`TerminusPlayerExecutor.executeOnPlayer`（178～212行付近）は検証失敗時に修復を呼ぶが、修復の boolean を使わず、最後に成功ログと true を返す。MOB 側の退去確認とは別に、プレイヤーの「有限HP0以下・死亡処理・通常リスポーンを受け入れる状態」を確認して結果を返す。

`isPlayerProperlyKilled` は DirectRead.hasHealth の後で getter のHPを読み、NaNと正数しか拒否しない。負のInfinityは除外されず、保存されたHPとgetterが食い違う場合の扱いも一貫しない。保存された値の有限性を明示的に確認する。

### HP0と死亡処理を区別する

`verifyAndRemediatePlayerDeath` は HP0・deadフラグを書いた**後**に wasDead を取得する（357～367行付近）。1.20.1 の実物 `.mcsrc/java/net/minecraft/world/entity/LivingEntity.java:1054` では isDeadOrDying は getHealth<=0 なので、正常にHPを書けた場合にも後続の die 呼び出しが飛ばされる。

HP0は ServerPlayer.die を一度実行した証明ではない。`.mcsrc/java/net/minecraft/server/level/ServerPlayer.java:571` の die は Forge死亡フック、死亡通知、ドロップ、死亡統計、最終死亡場所などを処理する。修復前に状態を取得し、「死亡処理を試行/確認したか」を別に管理する。処理済みなら二重に die や死亡画面通知を送らない。

### 待機状態を一つにする

現在は pending_respawn のbooleanを立て、異常時は毎Tick再修復する。処理中・死亡確認・リスポーン待ち・完了・遮断を分け、同じ対象への再要求を集約する。装備剥奪・パケット送信・死亡イベントの重複を避け、解除できない保護は失敗理由として扱う。

既知の No-Sugar の resetAbilities / packet送信や TLS/ECA の後始末は PlayerExecutor に直接残る。Link のUUID清掃だけをgenericOnlyで止めても、PlayerExecutor の既知処理全体は止まらない。担当の互換層へ寄せて切替を統一する。

## 6. 全経路の順序・対象方針・負荷

- TrueAnnihilationEngine は前回の変更でワールド一致・サーバースレッドを確認するが、TerminusEngine はその前に Generic の副作用を実行する。検証は入口に移し、PlayerExecutor/Generic の直接入口でも同じ条件を確認する。
- Generic の直接入口にはプレイヤー/通常の保護対象の拒否が無く、メモリ消去段階だけでプレイヤーを除外する。StateScanner.BREAK_TARGET をプレイヤーに使わない方針を入口から徹底する。
- Generic が先にコントローラー・本体参照をゼロクリアした後に、True 側が ModProtectionBreaker.Context を取得する。前回 True 内だけで順序を改善したが、終焉の全経路では遅い。UUID/ID、ボスバー、所有コントローラー、静的参照、対応保存形式を最初にcontextへ収集する。
- Generic と ModProtectionBreaker は走査・辞書操作・本体解体が重複する。前処理を集約し、各段階の attempted/confirmed/unsupported/failed を残す。
- 終焉の魔杖は空中右クリックで半径500のAABBに対して同期処理し、アイテムのクールダウンが無い。対象は既にロードされた範囲から取得するが、対象数と解体の重さ次第でTickを長く占有する。処理件数/時間の予算、途中進捗、再要求の集約、停止条件を追加する。範囲形状や威力の仕様を推測で変更しない。
- コントローラーの Integer/Long/Float/Double を認めながら primitive用 Field.setInt/setFloat 等で書き、例外を握り潰す箇所がある。ラッパー型はField.setで型に合う値を書く。解体できたようなログと実際の値を分ける。

## 7. クライアントと検証

TerminusPlayerDeathPacket には playerId があるが、handler は ClientAnnihilation.handlePlayerDeath にUUIDだけを渡す。後者はUUID一致後にmc.executeで処理し、その中では現在のLocalPlayerへHP0と死亡画面を適用する。

リスポーン前後の同じUUIDの別実体を区別する材料が使われていない。これは現時点では遅延通知に対する設計上のリスクであり、実クライアントでの再死亡は今回再現していない。ID/世代と死亡状態を実行時に確認し、死亡・リスポーン通知を冪等にする。ModNetwork の登録は consumerMainThread なので「network threadから直接実行している」という指摘ではない。

追加すべき回帰ケース:

1. 同じ対象へ30Tickごとに攻撃しても、開始から200Tick/10秒で発動する。単発・閾値直前・実体置換・強化中到達も区別する。
2. 生存getter偽装、HP0だがワールドに残る対象、次元移動・ログアウト・サーバー停止。
3. 単体終焉・対象0件の範囲終焉でも、他個体・他MOD・非ボスの保存を残す。削除OFFとgenericOnlyも検証する。
4. 対象を参照しない共有サービス、共有リスト、別個体を参照する本体を解体しない。
5. noop HP setter、死亡拒否、NaN/Infinity、die未処理、死亡統計/ドロップの一回性、実接続クライアントの死亡→リスポーン→操作復帰。
6. wrapper型の防御値、閉鎖拒否、処理中の対象退去、複数の範囲要求。

既存の `terminusResistedFor10SecondsTriggersReinforcedProtection` は timeoutTicks=100 で、trackTarget の直後に triggerResistedManually を呼ぶ。自動の200Tick/10秒判定や継続攻撃は測っていない。既存の正常系テストを残したまま、本来の境界ケースを追加する。

旧 `SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md` の「完全スタック偽装」「未装着でも同等」「不可逆的」「通常ダメージも完全遮断」は、現行実装の説明として使わない。暫定統一や未定の仕様は推測で変更せず、現在の能力・確認した結果をこの解析資料と Eprotect+ 実装記録に分けて残す。

## 8. 今回の確認結果と限界

Java17の小さな確認プログラムを `build/terminus-review/` に作った。製品ソースから追跡・選別・解体・保存削除の該当メソッドを抽出し、時計またはゲーム型・辞書・外部サービスをテスト用の型に置き換えた。Minecraftや外部MOD自体を起動した再現ではない。

追跡は currentTimeMillis の読みだけを注入した時計に替えて確認:

```text
singleAttemptAfter200Ticks: triggers=1
repeatedEvery30TicksFor300Ticks: triggers=0
wallClockJumpWithoutTickProgress: triggers=1
resistanceReachedWhileOtherReinforcementActive: laterTriggers=0
aliveGetterFalseDespiteActualHealth20: tracked=false
```

選別・解体・削除の確認:

```text
sharedServiceWithNoTargetBackReference: selected=1, closedForOtherMob=true
boxedDefensiveValuesAfterDismantling: lives=3, healthDelta=4.0
vanillaTarget: unrelatedKnownSaveDirectoriesRemaining=0
foreignTarget: unrelatedModDatabaseStillExists=false
```

削除したものは、ワークスペースの build/terminus-review の新しい一時フォルダ内に作った使い捨てデータだけ。ゲームのワールドやユーザーの保存には実行していない。

ログ: `build/terminus-review/tracking-probe.log` / `side-effect-probe.log`。検証用生成スクリプト: `build/probe_terminus_review.py` / `build/probe_terminus_side_effects.py`（build配下でgit管理外）。

製品コードを変えていないため、今回はbuild/GameTestを再実行していない。直前の最終通常構成のGameTest 329件中326件成功という結果は、上記の未検証ケースの正しさを保証しない。

推奨する実装順は、保存と共有物の対象限定 → 型別書き込み → 耐性追跡 → プレイヤー死亡の結果と一回性 → 前処理の集約 → 範囲負荷とクライアント通知。CODEX担当内で先に完結できるものから進め、兵姫本体・守る側エージェント・共有ファイルの変更は担当境界を守る。
