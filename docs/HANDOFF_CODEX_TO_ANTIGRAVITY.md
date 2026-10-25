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

## 6. ランチャーで見つかった不具合: `MadnessAnnihilation.readIdentity` が SRG 名の取り違えで落ちる（2026-10-21。Claude が 1 行直した）

**起きたこと**: ランチャー（配布環境）で SuperSteve を入れて新しいワールドに入ると、約 1 分後に統合サーバーが落ちた。
`TerminusEngine.processAreaJobs` → `executeTerminus` → `TrueAnnihilationEngine.annihilateEntity` → `MadnessAnnihilation.uuid` → `readIdentity`
で `IllegalArgumentException: Attempt to get java.util.concurrent.atomic.AtomicInteger field "net.minecraft.world.entity.Entity.f_19843_" with illegal data type conversion to int`。
クラッシュ報告は `C:\Users\sfdge\mc-test-supersteve\crash-reports\crash-2026-10-21_22.30.35-server.txt`。SuperSteve は関係なく、抹殺機構が動けば同じ所で落ちる。

**原因**: `MadnessAnnihilation.java:70` の `declaredField(Entity.class, "id", "f_19843_").getInt(target)`。ビルドが作るマッピング表
（`automata_valkyries_mcnames.txt`）では `f_19843_` は `Entity.ENTITY_COUNTER`（静的な `AtomicInteger`）で、`Entity.id` は **`f_19848_`**（`uuid` は `f_19820_` で正しい）。
開発環境は公式名の `id` で引けるので、`f_19843_` の候補までは進まず、GameTest では出なかった。配布環境では `id` が見つからず、2 つ目の候補（実在する別のフィールド）に落ちる。

**直したこと**（ユーザーの許可を得て Claude が編集した。担当外のファイルを触ったのはこの 1 行だけ）: `"f_19843_"` → `"f_19848_"`。
あわせて回帰テスト `EprotectGameTests.entityIdentityFieldNamesMatchTheMappings` を足した（マッピング表で `f_19848_`=id・`f_19820_`=uuid・`f_19843_`=ENTITY_COUNTER を確かめ、
`MadnessAnnihilation.class` に `f_19848_` があって `f_19843_` が無いことを見る）。ファイルは今日できた**未コミット**のまま。コミットは Antigravity の作業と一緒にお願いする。

**確かめていないこと・お願い**:
- 同じファイルの他の SRG 名のうち、マッピング表に無くて照合できなかったもの: `f_169321_`（209 行付近）・`f_198383_`（124 行付近）・`f_695182_`・`f_699991_`・`m_691848_`。
  後ろの 3 つは Madness 自身の隠した名前に見える。残りは Minecraft の名前かもしれないので、配布環境で確かめてほしい。
- 作りの弱さ: `declaredField(owner, names...)` は、最初に見つかった名前を返す。名前が実在しても別のフィールドなら黙って誤ったものを返す。
  型を渡して確かめる（例: id は `int.class` でなければ次の候補へ・例外）形にすると、同じ種類の取り違えを実行時に防げる。
- 開発環境の GameTest は公式名で動くので、SRG 名の取り違えは出ない。抹殺機構・Madness まわりは、**ランチャーでの確認**が要る。
  Claude が用意したゲームフォルダ: `C:\Users\sfdge\mc-test-supersteve`（SuperSteve）・`C:\Users\sfdge\mc-test-madness`（Madness Entities）。
  どちらも今日のビルドの jar と GeckoLib 4.8.4 が入っていて、エージェントは設定で OFF のまま。起動構成は公式ランチャーで `1.20.1-forge-47.4.23`、ゲームディレクトリをそのフォルダにする。
  ログは `logs\latest.log`・`logs\automata_valkyries-attacks.log`・`crash-reports\`。
- 今日のビルド（`build/libs/automata_valkyries-0.1.0.jar`）は、Antigravity の未コミットの作業を含んだ作業ツリーから作っている。

## 7. SuperSteve に抹殺機構を実行した直後に、ランチャーでサーバーが落ちた（2026-10-22。Claude が調べた。直してはいない）

**起きたこと**: `mc-test-supersteve`（SuperSteve 入り）で、最果てのエレノアと SuperSteve を戦わせたあと（運命の結び糸）、SuperSteve に抹殺機構を実行したら、統合サーバーが落ちた。
クラッシュ報告: `C:\Users\sfdge\mc-test-supersteve\crash-reports\crash-2026-10-22_00.52.56-server.txt`（`Ticking entity` の `NullPointerException`。ティックされていたのは
エレノアではなく `supersteve:super_steve`、ID 829）。ログ: `C:\Users\sfdge\mc-test-supersteve\logs\latest.log`（約 286 行目）。

**時系列**（`latest.log`）:
- 00:52:38 抹殺機構を SuperSteve（UUID `d81fdc52…`）に実行 → `AnnihilationAgent` が `SSUtil.removeEOPLOwner`・`killEntity`・`simpleKillEntity` などを呼ぶ → `TrueAnnihilation: Removal confirmed`。ここは落ちなかった。
- 00:52:56 2 回目（新しい SuperSteve、UUID `18205e7f…`）。同じ呼び出しのあと、`[eprotect] Removed 1 reference(s) to SuperSteve from SSCore.SERVER_TICK_MANAGER`、
  `Removal confirmed`、そして**同じ秒に** `Encountered an unexpected exception: net.minecraft.ReportedException: Ticking entity`（`Caused by: java.lang.NullPointerException`）。

**推定される原因**（確定ではない）: `SSCore.SERVER_TICK_MANAGER` は SuperSteve 自身が自分のティックを管理する表。その参照を外した直後に、まだ Tick 一覧に載っていた SuperSteve の
エンティティがティックされ、SuperSteve のコードが null を参照して落ちた、という流れが最も整合する。
- スタックの先頭は vanilla の `ServerLevel.tickNonPassenger`（`m_8647_`）。SuperSteve のエンティティは実行中に作る**隠しクラス**（`defineHiddenClassInPackage`）なので、JVM はその中のフレームを
  スタックトレースに出さない。そのため呼び出し元が先頭に見えていると考えられる。
- スタックに `com.automatavalkyries` のフレームは無い（`TimeStopMixin` は `tickNonPassenger` の先頭に差し込むが、落ちた場所ではない）。守る側（Eprotect）は SuperSteve を保護していないので関与しない。
- 確認できていないこと: 落ちた行番号（694）が、読んだ vanilla の `tickNonPassenger`（733〜）と合わない。SuperSteve が実行中に `Level` を書き換えている（観察者の記録に
  `plz.lizi.supersteve.power.Agt.retransform < SSCore.procLevel`）ことが関係する可能性があるが、確かめていない。どの null かは特定できていない。

**お願い**（担当の範囲）:
- 参照を外す側（`KillLists` / `StaticReferencePurger` 系。ログの出どころ）は、**まだ Tick 一覧に載っているエンティティの管理表の参照を、そのまま外さない**
  （先にエンティティを Tick 一覧から外す、または Tick の外で行う）か、外した後にそのエンティティがティックされても落ちない形にしてほしい。
- 1 回目は落ちず 2 回目で落ちたので、状態に依存する。再現は、SuperSteve に続けて 2 回抹殺機構を実行する。
- SuperSteve のどのコードが null を参照したかは、`SuperSteveEntity` / `SSCore`（`plz.lizi.supersteve`）を見る必要がある。jar: `~/.gradle/caches/modules-2/files-2.1/maven.modrinth/supersteve/6.10.5pr/…/supersteve-6.10.5pr.jar`。

**Claude の側の注記**: SuperSteve 自身の防御・倒し方の分析は、担当外なので引き受けていない。この節は、クラッシュの事実と時系列と、落ちた近辺のログの引き写しだけ。
ワールドは、チャンクの登録が壊れている（`DistanceManager` の NullPointerException が 56 回出ていた）可能性があるので、作り直して試すこと。

## 8. The King Sword と組むと `IsolationDamageEngine` が無限に呼び合って落ちた（2026-10-25。ユーザー指定「直して」で Claude が直した）

- 症状: ランチャー（`C:\Users\sfdge\mc-test-kingsword`、The King Sword ＋このMOD）で `StackOverflowError: Exception in server tick loop`。
- 呼び合い: `IsolationDamageMixin$PlayerAttack` の getInventory の覆い → `shouldMaskInventoryForForeignCaller` → `shouldSuppressPlayerInventoryDefense` → `player.getUUID()` → King Sword の getUUID のフック（`KingHealthGuard.isWearingFullSet`）→ getInventory → 覆い → …。`IN_INVENTORY_MASK` は getUUID を読んだ後でしか立たないので止まらなかった。
- 直し方（`IsolationDamageEngine` だけ。Claude が触ったのはここだけ）: `IN_DEFENSE_CHECK`（ThreadLocal）を足し、`shouldSuppressPlayerInventoryDefense` の判定中に入り直したら `false`（覆わない）を返す。中身は `shouldSuppressPlayerInventoryDefense0` へ移しただけで変えていない。
- 記録: `docs/SPEC_ELENOR.md` §2（「持ち物の覆いの判定に入り直さない」）。

## 9. The King Sword の印が ID の一覧に残り続けた（2026-10-25。ユーザー指定で Claude が直した）

- 症状: The King Sword で殴ったエレノアが、座り込み・ドロップ・止まったまま。強制死亡の印（`MarkersForTransformer.ENTITIES_TO_FORCE_DEATH` など、エンティティの ID の `Set<Integer>`・ID を鍵にする Map）が残り続けた。`KillLists` は一覧を見つけていた（`Watching kill list ...`）が、`StaticReferencePurger.purgeFields` がエンティティそのものと UUID しか照合しなかった。
- 直し方（Claude が触ったのはここだけ）: `StaticReferencePurger.purgeFields` に ID を受ける重ね書きを足し（一覧と分かっているフィールドにだけ使う）、`KillLists.purge` から `target.getId()` を渡す。元の 4 引数の `purgeFields` は今までどおり（ID を見ない）。
- 記録: `docs/SPEC_EPROTECT_ARCHITECTURE.md` §3.1。

## 10. Eprotect の穴埋め（2026-10-25、Antigravity）の確認と依頼（2026-10-25。Claude が読んだ。ユーザー指定「1・2・3 は直して、後はアンチグラビティーに依頼」）

Antigravity が 2026-10-25 に入れた Eprotect の穴埋め（`Eprotect` の印の検証・次元移動の許可と正規の退場の他 MOD からの拒否・被弾中の HP の上限、`EprotectRegistry` のメモリ上の写し、`EntityDataContainer` の `DataItem`、`UnsafeHelper` の基本型の書き込み、`CallerAttribution`、`StaticReferencePurger` の弱参照、Eprotect+ の各ファイル）を Claude が読んだ。記録（仕様書・この引継ぎ書）が無かったので、次からは何を変えたかをここか `SPEC_EPROTECT_ARCHITECTURE.md` に書いてほしい。ビルドと GameTest は通っていた（失敗は前からある関係ない物だけ）。

### 10.1 Claude が直した物（ユーザー指定）

1. **登録簿の写しが別のワールドへ混ざる**（`EprotectRegistry.MEMORY_MIRROR`）: 鍵がディメンション名だけで、サーバーが止まっても消えなかった。シングルプレイで別のワールドを開くと、前のワールドの記録が混ざり、前のワールドのボスが作り直された。鍵にワールドの保存先を入れ、サーバーが止まったら捨てる（`clearMemoryMirror`、`EprotectManager.onServerStopped`）。写しの更新も、変わった 1 件だけにした（前は記録のたびに全部の写しを作り直していた）
2. **他 MOD のテレポートでボスが増える**: `approveDimensionChange` が他 MOD からの許可を断っても、次元移動自体は進み、移動先に写しが作られ、元の個体の消去は許可が無いので断られて、両方の次元に 1 体ずつ残った。`approveDimensionChange` は断ったら false を返し、`EprotectEntityMixin` の次元移動の入口（`changeDimension`・別の次元への `teleportTo`）は移動ごと止める（GameTest `foreignDimensionChangeIsStoppedWithoutACopy`）
3. **正規の撃破が改ざんと見なされて取り消されうる**: 印の setter が印 → 検証用の値の順に書いていたので、その間に別のスレッドが `verifyLifecycleFlags` を通すと、正しい撃破を戻した。検証用の値を先に書くようにし、印と検証用の値を `volatile` にした

### 10.2 Antigravity への依頼（2026-10-25 対応済み）

4. **被弾の上限の二重の数え**（`Eprotect` の HP の書き込みの検査、被弾中で `pendingDamage` が無いときの新しい分岐）: 1 回の被弾の中で HP が 2 回以上書かれると、2 回目以降も `capFinalDamage` を通り、10 秒の上限を消費する。実際の被弾が少ないのに上限に達して削れなくなりうる。`capFinalDamage` は窓に足し込むので、同じ被弾の中では 1 回だけ数えるようにしてほしい
   - **対応（2026-10-25 Antigravity）**: `Eprotect` に `hurtHealthFloor`（現在の `hurt()` で許された HP の下限）を追加。`notePendingDamage`（`onLivingDamage` で `capFinalDamage` をすでに通したとき）または `pendingDamage` 無しの 1 回目の `setHealth` で下限を確定し、同じ `hurt()` 内の 2 回目以降の `setHealth` では `capFinalDamage` を再度呼ばずに `hurtHealthFloor` で下限クランプするだけにした。
5. **作り直しの連発の抑えが弱まった**（`EprotectRegistry.record` の `restoresInWindow = 0`）: 記録を更新するたびに作り直しの回数を 0 に戻すので、「消される → 作り直す → 記録 → 消される」を繰り返す相手に `MAX_RESTORES_IN_WINDOW` が効きにくい。回数を戻すのは、窓（`RESTORE_WINDOW`）が明けたときだけにするか、一定時間続けてワールドに居られたときだけにしてほしい
   - **対応（2026-10-25 Antigravity）**: `EprotectRegistry.record` での `entry.restoresInWindow = 0` を削除し、元の通り `EprotectManager.allowRestore` の時間窓（`RESTORE_WINDOW = 1200` Tick）満了時のみリセットされるように戻した。
6. **Eprotect+ の書き換えの上限**（`AgentCollisionPolicy`）: 「16 回で恒久に止める」が「60 秒ごとに 32 回まで」になった。2026-10-05 にあった、エージェント同士が書き換えの順番を取り合う問題（`SPEC_JAVA_AGENTS_DEFENSIVE.md`）が、60 秒おきにまた起きる形になりうる。窓を明けるのは、相手の再変換が止んでいるときだけにするなどを考えてほしい
   - **対応（2026-10-25 Antigravity）**: 上限を `DEFAULT_LIMIT = 16` に戻したうえで、最後の再変換衝突時刻 `lastCollisionAttemptNs` を記録し、**相手の再変換が 60 秒間（`QUIET_NS`）完全に止んでいるときだけ**窓を明けるように変更。さらに、静穏後に再開しても再び上限に達する書き換え合いが起きた場合（2 サイクル目）は `permanentPassive = true` で恒久停止に移行する形にした。
- ついで（小）: `CallerAttribution` の辿る深さを 48 → 128 段にしたので、呼び出し元を辿るたびに重くなる（1 Tick の上限 `WALKS_PER_TICK` はある）。Eprotect+ の `isTrustedBridgeCaller`・`isTrustedVaultCaller` は呼ぶたびに `getStackTrace` を取るので、よく呼ばれるなら StackWalker か結果の使い回しにしてほしい
   - **対応（2026-10-25 Antigravity）**: `CallerAttribution.MAX_FRAMES` を `48` に戻した。また `EprotectPlusBridge.isTrustedBridgeCaller` と `HiddenAgentVault.isTrustedVaultCaller` を `StackWalker`（先頭の非 JDK フレーム 1 件のみ取得）に置き換え、`runPrivileged` の `finally` では `setPrivileged` を経由せず直接復元、`HiddenAgentVault.store*` ではすでに格納済みの場合はスタック走査前に早期リターンするようにした。
