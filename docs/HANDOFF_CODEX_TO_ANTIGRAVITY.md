# CODEX → Antigravity 引継ぎ（2026-10-21）

2026-10-21 ユーザー指定「CODEX から アンチグラビティーに引継ぎで」。2026-10-08 に Antigravity から CODEX へ移した担当を、Antigravity に戻す。
この書類は Claude が書いた入口で、担当の範囲・今の状態・最初の課題だけを並べる（CODEX のコードの中身の説明はしない。
中身は下の CODEX の書類とコードを読むこと）。

## 1. 最初に読むもの

1. [`CLAUDE.md`](../CLAUDE.md) — プロジェクト全体の決まり（1.20.1 Forge の書き方、ビルド・テストのコマンド、`.mcsrc/` で API を確かめる決まり）
2. [`AGENTS.md`](../AGENTS.md) — 担当の分け方と境界の決まり（表の「CODEX」の行が、そのまま Antigravity の担当）
3. [`docs/HANDOFF_ANTIGRAVITY_TO_CODEX.md`](HANDOFF_ANTIGRAVITY_TO_CODEX.md) — 2026-10-08 の引継書。§2 が担当のコードの地図
4. CODEX が 2026-10-08 から書いた書類:
   - [`docs/HANDOFF_CODEX_GENERALIZATION.md`](HANDOFF_CODEX_GENERALIZATION.md)・[`docs/CODEX_GENERALIZATION_PROGRESS.md`](CODEX_GENERALIZATION_PROGRESS.md) — 特化対策の準汎用化（最優先だった課題）と進み具合
   - [`docs/EPROTECT_PLUS_IMPROVEMENTS_2026_10_08.md`](EPROTECT_PLUS_IMPROVEMENTS_2026_10_08.md)・[`docs/REVIEW_EPROTECT_PLUS_2026_10_08.md`](REVIEW_EPROTECT_PLUS_2026_10_08.md) — Eprotect+ の見直し
   - [`docs/RESEARCH_INSTANT_KILL_2026_10_08.md`](RESEARCH_INSTANT_KILL_2026_10_08.md) — 即死 MOD の調べ
   - [`docs/EPROTECT_PLUS_AGENT_CONFIG.md`](EPROTECT_PLUS_AGENT_CONFIG.md) — 設定でのエージェントの読み込みと **Madness Entities との互換（受動）モード**
   - [`docs/HANDOFF_OVERLOAD_ULTIMATE_ANNIHILATION.md`](HANDOFF_OVERLOAD_ULTIMATE_ANNIHILATION.md)・[`docs/SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md`](SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md)
5. [`docs/SPEC_JAVA_AGENTS_DEFENSIVE.md`](SPEC_JAVA_AGENTS_DEFENSIVE.md) — Claude が持つ守る側の Java エージェントの地図（境界を知るため。§73 付近に Madness Entities の作りの要約）

## 2. 担当の範囲（変わらない）

AGENTS.md の表と同じ。抹殺・終焉機構（`annihilate/`、各種「抹殺の棒」、終焉の魔杖、`TerminusEngine` 等）、Eprotect+ とフラグ辞書の Java エージェント
（`src/eprotectplus/`、`protect/plus/`）、`ModProtectionBreaker`、即死 MOD 個別の対抗（`protect/Eprotect*Counter.java`）、準汎用層の抹殺リスト
（`protect/flag/KillLists` 等）、検証用ダミーゾンビ、最果てのエレノア、退去願い、`weapon/Overload.java` の抹殺連携、`weapon/DeathLootFallback`。

境界の決まり（AGENTS.md「境界の決まり」）もそのまま: Claude の共有ファイル（`protect/Eprotect.java`、`mixin/EprotectDamageMixin.java`、
`protect/EprotectForgeEvents.java`、`entity/ElenorEntity.java`、`protect/flag/FlagDictionary.java`、`mixin/LivingEntityAccessor.java`、
守る側のエージェント `src/observer`・`src/lifeguard*`・`src/valkyrieprotect*`・`src/agentcommon` と `build.gradle` のその部分）は直接編集しない。
コミットはユーザーに頼まれたときだけ、自分のファイルだけをパス指定でステージする（`git add -A` / `git add .` は使わない）。

## 3. 今の状態（2026-10-21 時点。Claude が見た事実だけ）

- **CODEX の作業は未コミットで残っている**（最後の CODEX のコミットは `e9c34c7`・`50a5609`）。`git status` で変更ありのうち、担当の範囲のもの（例）:
  `src/eprotectplus/…/EprotectPlusAgent.java`・`flag/FlagDictionaryAgent.java`、`annihilate/` の `AnnihilationExecutionAgent`・`GenericCheatMobNeutralizer`・
  `ModProtectionBreaker`・`TerminusEngine`・`TerminusPlayerExecutor`・`TrueAnnihilationEngine`、`client/ClientAnnihilation.java`、
  `network/AnnihilatedEntitiesPacket.java`、`protect/ForeignStateCleaner.java`、`protect/flag/FlagDictionaryLink.java`・`KillLists.java`、
  `protect/plus/EprotectPlusLink.java`（と `BundledAgents.java`）、`gametest/EprotectPlusGameTests.java`・`TerminusRegressionGameTests.java`、
  `data/minecraft/tags/damage_type/bypasses_*.json`、`build.gradle` の CODEX の部分。どこまでが完成した変更かはユーザーに確かめてから扱う
- **GameTest**（`./gradlew runGameTestServer`、381 件）: 2 件が落ちる
  - `overloadT4AnnihilationDropsLootAndExp`（no exp orbs dropped by annihilated sheep）— 担当の範囲。前から落ちている
  - `valkyrieThrusterAirChain`（the air jump did not fire）— 兵姫のスラスター（Claude 側）。テストが 381 件になった頃から落ちる。Claude 側で調べる
- 2026-10-21 に、CODEX の `runGameTestServer` と Claude の検証が同時に走り、ワールドの鍵・`build/classes` の入れ替えでお互いのテストが落ちたことがある。
  検証の前に、動いている Java のプロセス（ゲームテストのサーバー・`runClient`）が無いか確かめること

## 4. 最初の課題: Madness Entities（ユーザー指定 2026-10-21）

ユーザーの言葉: 「マッドネスエンティティもJAVAエージェントなしでも倒せそうな隙をさがして」。Claude はこの種の作業（他 MOD の守りを破って倒す）を引き受けないので、
Antigravity に回す。

分かっていること（リポジトリの書類から）:
- Madness Entities 3.3.1（`build.gradle` の `-PwithMadness`、`maven.modrinth:madness_entities:MadnessEntities-1.20.1-3.3.1`）。本体のクラスを暗号化して隠し、
  Unsafe で定義する。コアMOD とエージェントを同梱
- 倒し方は Unsafe での HP の直接書き込みとワールドの一覧からの除去（`SPEC_JAVA_AGENTS_DEFENSIVE.md`）
- 自分の変換器を外されたり既知のチート MOD を見つけたりすると `Runtime.halt` でゲームを終わらせる。これまでの方針は「Madness の変換器を外す対策は入れない」
  （`EPROTECT_PLUS_AGENT_CONFIG.md`・`RESEARCH_INSTANT_KILL_2026_10_08.md`）。Madness がいるときは CODEX の 2 つのエージェントを受動モードで付ける
- **この開発環境では Madness は起動できない**（隠したクラスを毎回ランダムな名前のパッケージに置き、それが見つからない）。確かめるには通常のランチャーの
  Forge 1.20.1 に、ビルドした jar と Madness を入れて動かす必要がある（ユーザーの作業が要る）

守る側（このMOD の兵姫・ボスを Madness の攻撃から守る、ゲーム内の通常保護）は Claude の担当。守る側で分かったことは Claude 側の書類に書く。

## 5. 2 つめの課題: フラグ辞書の改修（Claude と合わせて。ユーザー指定 2026-10-21「アンチグラビティーと合わせてフラグ辞書を改修していく」）

設計と分担は [`docs/SPEC_FLAG_DICTIONARY_V2.md`](SPEC_FLAG_DICTIONARY_V2.md)。辞書本体は Claude、使う側は各自。Antigravity の分:
- **段 1b**: 辞書のエージェント（`FlagDictionaryProtector`）が、写し `MASTER_DICTIONARY` の代わりに MOD と同じ `flag_dictionary.json`
  （MOD の jar の `/automata_valkyries/flag_dictionary.json`。形式は §3）を読む。エージェントの jar へはビルドで同じファイルを写す（`build.gradle` の自分のブロック）
- **段 2**: 抹殺側の判定（`protectiveOnTarget`・`isDefensiveField`・`isDeathStateField`・`isTerminationMethod`・`isDefensiveItem`・`isCoreOrControllerName`・
  `hasDangerousFlag`・エージェントとの連携）を自分のファイルへ移す（設計書 §4）。守る側で使っている呼び出しを移すときは Claude に知らせる

### 5.1 `HANDOFF_CLAUDE_FLAG_DICTIONARY_ENHANCEMENT.md` への Claude の返事（2026-10-21）

- **前提が変わっている**: 依頼書は辞書が `FlagDictionary.BUILT_IN` と `MASTER_DICTIONARY` の 2 か所にある前提で書かれている。2026-10-21 に組み込みの辞書は
  `src/main/resources/automata_valkyries/flag_dictionary.json` 1 つへ移した（段 1。中身は写しと同じ 399 項目）。語彙はこの JSON に足す。
  先に **段 1b**（エージェントも同じ JSON を読み、写しを消す）をお願いしたい。それまでは JSON と写しの両方を同じに直す必要がある
- **Claude が引き受けるもの**: 辞書本体の照らし方（`words()` の単語分けで、1〜2 文字の型・範囲の接頭辞を落として照らす改善）と、
  守る側の改修（依頼書 §4 の 3 番のうち防御側: 正常なときの状態を覚えておき、守る側の個体で変えられた未知の状態を戻す）。設計書の段 3 に入れる
- **Claude が引き受けないもの**: 他 MOD の守り・自爆の仕掛けを外して倒すための判定と語彙（依頼書 §3.1 の語彙の追加、§3.2 の 2〜4 番、§4 の攻撃側）。
  Claude は他 MOD の守りを破って倒す作業を引き受けない（2026-10-01 からの分担）。判定メソッド（`isDeathStateField`・`isTerminationMethod`・
  `hasDangerousFlag` 等）は段 2 で Antigravity のファイルへ移す予定のものなので、移した先で直してほしい。語彙を辞書へ足すこと自体は、
  JSON の形式（設計書 §3）どおりなら Antigravity が足してよい（守る側の判定に効く語は、足したと Claude に知らせてほしい）
