# Eprotect+ の整理・改良（2026-10-08）

ユーザーの承認に基づき、[解析資料](REVIEW_EPROTECT_PLUS_2026_10_08.md)の優先課題を実装した。Claude 担当の共有ソース・守る側エージェント・検証依存・build.gradle は編集していない。

2026-10-09 にEprotect+本体・接続層・辞書エージェント・回帰テスト・本記録をコミット対象とした。TrueAnnihilationEngine の実行順序・保存保持・退去確認は終焉修正のコミット `50a5609` に含まれる。その他の準汎用化作業は別の未コミット変更として残す。

## 変更

### 実行範囲と結果

- Link の特権実行を一つの実装に集約。正常・例外・入れ子とも呼び出し前の状態を finally で復元する。InvocationTargetException の原因をそのまま伝え、実行済み処理をフォールバックで再実行しない。
- runCloaked は同じ協調用フラグの範囲管理。AccessController.doPrivileged を除去し、呼び出し履歴を隠す能力とは説明しない。
- エージェントのゲーム固有解体を削除し、MOD の ModProtectionBreaker/Counter/抹殺エンジンに集約。旧 annihilateEntityJvm は false を返す互換入口、旧 UUID 清掃入口は unsupported を記録する。JVM 特権を理由にゲーム型やマッピングの違いを無視しない。
- Link.prepareAnnihilation はサーバースレッド上の対象限定前処理。プレイヤーと通常の保護対象・null を除外し、attempted / unsupported / failed を返す。準備完了と消滅を同一視しない。
- TrueAnnihilationEngine は対象の解体前に後始末用の参照を取得し、最後に除去理由と UUID/ID 検索からの退去を確認する。未確認なら false。単体抹殺に付随した保存ディレクトリ全体の削除を除去した。
- Link の既知 MOD 対策とプレイヤー UUID 清掃を SpecificCountermeasures.enabled で制御。genericOnly で特化処理を動かさない。

### 接続・変換・再監査

- Link は未接続時の探索を5秒間隔で再試行し、見つけた反射 API をキャッシュする。後付け接続を永久に見落とさない。
- 基準バイトコードは弱いクラスローダー**identity**＋内部クラス名で保存。equals を独自実装したローダーでも混ざらず、渡す配列はコピーする。bootstrap loader も区別する。
- 登録が成功してから Instrumentation と ACTIVE 状態を公開。登録失敗なら DEGRADED/非稼働として再試行可能。再変換非対応の JVM ではロード時だけの登録ができる。
- EntityType 等が Entity prefix に巻き込まれないよう vanilla 対象を正確な4クラス名に変更。MOD 側の監視範囲を protect/entity/annihilate/mixin/weapon/util に絞り、エージェント自身と辞書専用変換器の範囲を除外。
- 未知の外部呼び出し元も検知対象。協調するこの MOD の変換と外部変換を分け、同じ入力では復元記録を増やさない。短い定数返却の検知を boolean true、整数、float、double、long に拡張した。
- 再監査は60秒の最低間隔と同時実行防止で要求をまとめる。保留があれば daemon が点検する。Mixin テンプレートは能動的再監査から除外し、vanilla の関連4クラス・MOD の各パッケージを単位に処理する。ClassFormatError で一括変換が拒否された場合のみ個別に調べ、失敗クラスを次回の能動的監査から除外する。成功件数だけを加算する。
- 統計は要求数・正常に再変換した件数・基準入力を返した件数・エラー・処理時間を分ける。保存した入力は「最終的に JVM に適用された未改ざんコード」とは保証しない。

### 辞書・記録・時間

- BUILT_IN と設定を合成した active の語句を照合。同じ件数での差し替えも検出する。
- 修復は組み込み辞書を戻し、既存の reloadFromConfig API で extra と ignore を再適用。修復後の内容を再確認してから成功件数を増やす。
- ログのファイル書き込みを daemon に移し、最大1024件のキューと128件単位の書き込みにする。イベント履歴は512件、キュー超過による破棄数と I/O エラーを統計に表示。終了時に最大2秒の排出待ち。辞書エージェントの単独起動でも記録を開始する。
- 同じ原因の保護記録を間引き、Link の履歴読み書きと stats のロックを統一する。
- 間隔・強化期限は System.nanoTime。Tick 指定の互換 API は従来どおり ticks×50ms の**実時間**換算で、サーバー Tick による期限へは仕様を推測して変更しない。

## 再現チェック

`tools/eprotectplus/run-regression.ps1` は Java 17 のみでエージェントと回帰チェックをコンパイル・実行する。ローダー identity の分離、配列の防御コピー、bootstrap、例外・入れ子のフラグ復元、一回実行、noop 対象を成功としない契約、同じ入力の辞書変換、未知呼び出し元、監視範囲、登録失敗後の再試行、再変換非対応、強化期限の延長・リセットを確認する。

`EprotectPlusGameTests` はゲーム側の例外・入れ子・一回実行、準備と消滅の区別、エージェント接続時の同件数改ざん修復と extra/ignore 保持を確認する。既存の retransformEnforcerAndStackCloaking も継続して実行する。

## 制限と継続調査

- 後付け接続後も、初回ロード時の入力を取得していないクラスの基準は捏造しない。基準がなければ復元しない。
- 後続の変換器の出力や JVM の最終適用は、この変換器からは観測できない。メソッドの短い返却の検知は全ての意味改変を見抜くものではない。
- 消滅確認は実行直後の対象・ワールド検索についての確認で、将来の別 UUID 復活や外部保存ファイル全体の完全消滅は保証しない。
- 外部 MOD の一覧清掃・ディスク形式・EventBus 全体への既存の特化処理は別の解析対象として残る。今回はエージェント側の重複と単体抹殺の無条件な全保存削除を整理した。
- 実際の動的 attach、意図的な JVM 改ざんの全ケース、各外部 MOD での討伐は今回の独立チェックだけでは確認できない。

## 検証結果

| 構成 | 結果 | ログ（build/） |
|---|---|---|
| Java 17 の独立回帰チェック | PASS | `codex-eprotect-probe.log` および `eprotectplus-regression-work/logs/` |
| 最終 build | BUILD SUCCESSFUL（16秒） | `codex-eprotect-build-final.log` |
| 最初の通常 GameTest | 328件中325件成功 | `codex-eprotect-default.log` |
| 最終ソースの通常 GameTest | 329件中326件成功 | `codex-eprotect-default-final.log` |
| genericOnly + Eprotect+ | 329件中326件成功 | `codex-eprotect-generic.log` |
| Observer + LifeGuard + ValkyrieProtect + Eprotect+ | 329件中325件成功 | `codex-eprotect-agents-final.log` |
| 上記 + SuperSteve | 329件中325件成功 | `codex-eprotect-supersteve.log` |

途中に別担当のテスト追加と辞書回帰テスト追加があり、最初の通常実行と後続では総数が異なる。全件成功とは報告しない。

Eprotect+ の新規3件と、以前に落ちていた `retransformEnforcerAndStackCloaking` は後続の実行で失敗一覧に出ていない。既知の特権フラグ漏れは今回の併用構成でも再発しなかった。

残った失敗:

- 全構成: `overloadT4AnnihilationDropsLootAndExp`（既知の羊ドロップ）。`everyTierHasMachineRecipesAndKits` と `upgradeRecipesFollowConfig`（現在の設定で upgrade_kit_future が作れない、AGENTS.md の設定依存2件）。
- 守る側エージェント併用・SuperSteve 併用: `directDeathIsRepaired`（Elenor の期待HP500に対し494）。最初の通常・genericOnly では失敗していない。共有担当の Eprotect/Elenor/守る側エージェントは編集せず、再現情報を残す。

能動的監査を一括実行すると ClassFormatError が出ていた。拒否されたグループを個別に調べたところ、`ValkyrieArts$Art`、`WeaponUtil$Task`、`ValkyrieBackups$Entry`、`CallerAttribution$Attribution` の4クラスが拒否された。いずれも record 型だが、原因は断定していない。併用実行では残り341クラスを再変換できた。4クラスは次回の能動的監査から除外し、統計とログに非対応として表示する。共有担当の record 定義は変更していない。

SuperSteve の実読み込みでは既存 AgentHijackGuard が `supersteve.safemode=true` を設定していることを確認。ゲームテスト自体は最後まで実行できるが、起動ログに Forge の itemstack coremod のエラーと SuperSteve のレシピ `make_cutter1` の Unknown item `supersteve:endofplz_lite` がある。安全モードでの登録名とレシピの整合について別途確認が必要で、今回その外部 MOD を変更していない。

最終ソースの通常構成でも、Eprotect+ 関連の回帰は通過した。残った3件は同じ羊・キットの失敗で、`directDeathIsRepaired` は通常構成では失敗していない。
