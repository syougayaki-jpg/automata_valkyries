# Eprotect+・フラグ辞書エージェントの設定

2026-10-09。Minecraft 1.20.1 / Forge 47.4.x / Java 17。

## 使い方

初回起動で `config/automata_valkyries-eprotect-plus.toml` が生成される。両方を設定から読み込む場合:

```toml
[agents.eprotectPlus]
enabled = true

[agents.flagDictionary]
enabled = true
```

保存してゲーム・サーバーのJVMを完全に再起動する。追加の `-javaagent` 引数は不要。既定値は両方 `false`。Eprotect+のみ・辞書のみも選べる。この設定では通常のゲーム内Eprotectを無効化しない。

起動引数で既に読み込んだエージェントは優先される。設定を `false` にしても、起動引数で読み込んだものや現在実行中のエージェントは解除しない。従来の `-javaagent:eprotect-plus.jar` は辞書も起動する。設定で読み込むEprotect+は、辞書設定がfalseなら `flagDictionary=false` を渡して辞書エージェントを起動しない。

設定による接続はMODの共通セットアップ時に行うため、起動前の干渉を防ぐものではない。既に読み込まれたクラスは再変換でその時点の入力を観測する。観測前の改ざんを取り消したり、元の未改ざん入力だったと保証したりはしない。再変換できないクラスは記録して観測対象から外す。

## Madness Entities との互換モード（2026-10-21）

`madness` が読み込まれ、設定から Eprotect+ またはフラグ辞書エージェントを装着する場合、既定の `agents.madnessCompatibility.passive = true` により受動モードで装着する。このモードでは CODEX 担当の二つのエージェントは変換器を登録せず、ロード済みクラスの再変換も要求しない。通常の MOD 側の終焉処理は使える。`-javaagent` で起動前に装着する場合、Forge の設定はまだ読めないため、各引数に `=passive` を指定する。開発環境では `-PpassiveAgents`、`-PwithMadness` 指定時は自動で受動モードにする。

通常モードでの Eprotect+ とフラグ辞書エージェントによるバイトコード返却は、合計16回を上限とする。超過すると両方が以後の返却と能動的な再監査を止め、ログと `getStats()` に受動状態を記録する。起動時のシステムプロパティ `-Dautomata_valkyries.agentCollisionLimit=数値` で 0～64 に変更でき、範囲外は丸める。開発環境の Gradle 実行では `-PagentCollisionLimit=数値` を指定できる。

Madness がエージェントの存在自体や他 MOD の存在を理由に `Runtime.halt` する場合、同じ JVM 内でその終了を捕まえることはできない。その場合は当該 Java エージェントを OFF にして MOD 側の処理を使う。Madness の変換器を取り外したり書き換えたりはしない。

## 実装と担当境界

- `protect/plus/BundledAgents` がConstructイベントで専用のCOMMON設定を登録し、Claudeの `AgentSelfAttach` の拡張入口に2種のエージェントを登録する。
- `build.gradle` のCODEX担当ブロックで2種のjarをMODに同梱。各jarに既存の `agentcommon.Attacher` のclassだけを同梱し、共通部品のソースは変更しない。
- 2種のjarは共通のエージェントクラスを含むため、クラスの存在だけでは個別の接続を区別できない。それぞれ専用のmarkerを同梱し、反対側のmarkerを除外する。接続後は `hasInstrumentation()` も確認する。
- Eprotect+が辞書を既に起動した場合は二重に付けない。後付け接続の後、MOD側の接続キャッシュを再探索する。辞書の「稼働中」判定はclassの存在ではなくInstrumentationの登録で確認する。
- 守る側のエージェント・AgentSelfAttach・EprotectConfig・MOD本体・守る側のビルドブロックは編集しない。

共通の読み込み処理は有効なjarを `automata_valkyries/agents/` に取り出し、同じJavaの別プロセスから接続する。Javaにattach機能が無い場合などは接続できず、ログに理由と従来の起動引数が案内される。専用markerが見えても登録が失敗している場合は、MOD側の接続ログに非稼働を明記する。

## 検証

`AgentConfigGameTests.configuredAgentsAreConnectedAndReportActualActivation` は設定で指定した接続、実際のInstrumentationとMOD側の状態の一致、起動引数なしの無効状態、読み込み済みの代表クラスの観測を確認する。`EprotectPlusGameTests` の辞書修復テストは辞書エージェントが稼働するときに実行するため、辞書単独起動も検証できる。

検証時の一時設定は終了後に戻す。未指定なら両方falseにする。

2026-10-09の結果:

| 構成 | 結果 | build配下のログ |
|---|---|---|
| 設定で両方有効・起動引数なし | 接続成功、339件中338件成功 | `codex-agent-config-both.log` |
| 設定でEprotect+のみ有効 | 辞書は非稼働、339件中338件成功 | `codex-agent-config-plus-only.log` |
| 設定で辞書のみ有効 | Eprotect+は非稼働、辞書修復成功、339件中338件成功 | `codex-agent-config-flag-only.log` |
| `-PwithFlagAgent` ＋設定で両方有効 | 既存の辞書と追加のEprotect+が接続、339件中338件成功 | `codex-agent-config-existing-flag.log` |
| 設定で両方無効・起動引数なし | 両方非稼働、339件中338件成功 | `codex-agent-config-off-final.log` |
| 最終build | BUILD SUCCESSFUL（16秒） | `codex-agent-config-build-final.log` |

各構成の唯一の失敗は既知の `overloadT4AnnihilationDropsLootAndExp`（羊の経験値ドロップ）。全GameTest成功とは扱わない。追加した接続確認・後付け入力観測のチェックは通った。独立回帰チェックもPASS。配布MODのjarに2種のエージェントが入り、各jarにAttacherと固有markerだけが入ることを確認した。

検証終了後、専用設定は既定の両方falseへ戻した。実行環境はWindowsのJDK17と開発用Forgeサーバー。通常ランチャーのJavaや実クライアントでの接続は未確認。
