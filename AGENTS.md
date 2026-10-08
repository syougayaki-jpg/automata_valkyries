# AGENTS.md（CODEX 向けの入口）

Minecraft **1.20.1** / **Forge 47.4.x** / **Java 17** の MOD「Automata Valkyries（戦術兵姫MOD）」。MOD ID `automata_valkyries`、パッケージ `com.automatavalkyries`。

このリポジトリは複数の AI で分担して開発している。CODEX は **2026-10-08 に Antigravity の担当を引き継いだ**。最初に次の順で読むこと。

1. **[`CLAUDE.md`](CLAUDE.md)** — プロジェクト全体の決まり（1.20.1 Forge の書き方の注意、ビルド・テストのコマンド、`.mcsrc/` で実物の API を確かめる決まり、リソースの置き方、仕様書の一覧）。Claude 向けに書いてあるが、内容はそのまま CODEX にも当てはまる
2. **[`docs/HANDOFF_ANTIGRAVITY_TO_CODEX.md`](docs/HANDOFF_ANTIGRAVITY_TO_CODEX.md)** — 前任の Antigravity からの引継書（担当のコードの地図・課題・検証コマンド）
3. **[`docs/HANDOFF_CODEX_GENERALIZATION.md`](docs/HANDOFF_CODEX_GENERALIZATION.md)** — 最優先の課題（特化対策の準汎用化）の詳細
4. **[`docs/SPEC_JAVA_AGENTS_DEFENSIVE.md`](docs/SPEC_JAVA_AGENTS_DEFENSIVE.md)** — Claude が持つ守る側の Java エージェントの地図（境界を知るため）

## 担当の分け方

| 担当 | 範囲 |
|---|---|
| **CODEX**（旧 Antigravity） | 抹殺・終焉機構（`annihilate/`、各種「抹殺の棒」、終焉の魔杖、`TerminusEngine` 等）、Eprotect+ とフラグ辞書の Java エージェント（`src/eprotectplus/`、`protect/plus/`）、他 MOD の守りの解体（`ModProtectionBreaker`）、即死 MOD 個別の対抗（`protect/Eprotect*Counter.java`）、準汎用層の抹殺リスト（`protect/flag/KillLists` 等）、検証用ダミーゾンビ、最果てのエレノア、退去願い、`weapon/Overload.java` の抹殺連携、`weapon/DeathLootFallback`。詳しくは引継書 §2 |
| **Claude** | 兵姫本体・AI・GUI、兵装・機械・アイテム・ブロック、敵・ボス（ルクシオン等のクロノゲイザー）、ゲーム内の通常保護（`Eprotect` 本体・被弾上限・死亡拒否・自己修復）、守る側の Java エージェント（観察者 `src/observer`、LifeGuard `src/lifeguard*`、オートマタヴァルキリー protect `src/valkyrieprotect*`、共通部品 `src/agentcommon`）、`protect/AgentHijackGuard` |

### 境界の決まり

- **Claude の共有ファイルは直接編集しない**（必要なら変更内容をユーザーに伝える）: `protect/Eprotect.java`、`mixin/EprotectDamageMixin.java`、`protect/EprotectForgeEvents.java`、`entity/ElenorEntity.java`、`protect/flag/FlagDictionary.java`、`mixin/LivingEntityAccessor.java`。守る側のエージェント（`src/observer`、`src/lifeguard*`、`src/valkyrieprotect*`、`src/agentcommon`）と `build.gradle` の守る側のエージェントの部分も同じ
- Claude も CODEX の担当ファイルは編集しない
- **コミットは自分のファイルだけをパス指定でステージする**（`git add -A` / `git add .` は使わない）。2026-10-08 に、もう一方の未コミットの作業が別のコミットに混ざって入った
- コミットはユーザーに頼まれたときだけ
- 検証で他方のファイルが原因の失敗を見つけたら、直さずにユーザーに伝える

## 変えてはいけないもの

- `build.gradle` の `-PwithXxx` の即死系 MOD の検証用の依存（ユーザーが外すと言うまで残す）
- `.ref/starward/`（学習用の参考資料。git 管理外。コミットしない・写さない）
- `.mcsrc/`（デコンパイルしたソース。git 管理外）
- 仕様書の【暫定統一】項目は推測で変えない。未定の項目（仕様書 §0.3）はユーザーに確かめる

## 環境（Windows）

- `./gradlew build` → `./gradlew runGameTestServer`（GUI なし。ログは `run/logs/latest.log`）。登録・リソースを触ったら GameTest まで通す
- 開発中のクライアント（`runClient`）が動いていると、コンパイルで jar がロックされることがある。止めるとき: `Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'forgeclientuserdev|clientuserdev' } | Stop-Process`
- 動いているクライアントにリソースの変更を読ませるときは、先に `./gradlew processResources` してからユーザーに F3+T を頼む
- 開発環境で起動できない MOD: PIG2、Madness Entities、Fantasy Ending（理由は `CLAUDE.md` のコマンド表と `docs/SPEC_JAVA_AGENTS_DEFENSIVE.md` §5）。SuperSteve は `-PwithSuperSteve` で `run/mods/dev-*.jar` に置いて読む

## 2026-10-08 時点の状態

- 前から落ちている GameTest: `overloadT4AnnihilationDropsLootAndExp`（羊が経験値・羊毛を落とさない）。ときどき落ちる: `crashedPodWakesWithProtoCore`、`t4NormalAttackCooldown`。ユーザーの設定次第で落ちる: `upgrade_kit_future` の 2 件
- **CODEX の担当で落ちているもの**: `retransformEnforcerAndStackCloaking`（「Privileged flag leaked after cloaked execution」）。`-PwithEprotectPlus` だけで起動しても落ちる
- `protect/AgentHijackGuard.ensureAgtCallbackNeutralized()`（Antigravity が追加し、`EprotectSuperSteveCounter` から呼ぶ）は、説明では「監視・解除」だが、実際は SuperSteve の `AgtCallback.INST` が入っているかを見て `true` を返すだけで解除はしていない。SuperSteve の乗っ取りそのものは `AgentHijackGuard.beforeModsLoad()`（安全モードを先に立てる）で止まっている
- Madness Entities は、自分の変換器を外されたり既知のチート MOD を見つけたりすると `Runtime.halt` でゲームを終わらせる作り。Madness の書き換えを外す対策は入れないこと
- 守る側のエージェントを 2026-10-08 に整理した（共通部品 `src/agentcommon`、改ざん防止のテストの修正）。Eprotect+ と同時に付けて並べ直し合いが起きないことを確認済み
