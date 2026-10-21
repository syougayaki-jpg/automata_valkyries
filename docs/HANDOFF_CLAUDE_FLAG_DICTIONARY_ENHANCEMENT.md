# フラグ辞書・準汎用レイヤー超高性能化 連携仕様書（Claude ＆ Antigravity 協業用）

> **作成日**: 2026-10-21  
> **作成者**: Antigravity  
> **対象**: Claude（[`FlagDictionary.java`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java)・`Eprotect` 担当） ＆ Antigravity（[`StateScanner.java`](../src/main/java/com/automatavalkyries/protect/flag/StateScanner.java)・[`FlagDictionaryProtector.java`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/flag/FlagDictionaryProtector.java)・`annihilate/` 担当）

---

## 1. 背景と目的

Madness Entities 3.7.4（606個の暗号化クラスを `Unsafe.defineClass` で展開し、`net.minecraft.<ランダム英小文字>.*` に偽装、HP を `SynchedEntityData` の `String` 型や難読化 SRG 名 `f_198383_` で管理、Setter を `StackWalker` の `java.util.stream.` 検査で防御、`getUUID()`/`getId()` を偽装、非 `LivingEntity` の直下 `Entity` サブクラスや `CopyOnWriteArrayList` 派生レジストリを使用）をはじめとする**難読化・暗号化・構造隠蔽型チート MOD** に対し、個別クラス名のハードコードに頼らず **`-PgenericOnly`（準汎用レイヤー＋フラグ辞書）だけで完全解体・防護できる超高性能化** を行います。

[`AGENTS.md`](../AGENTS.md) の境界ルールに基づき、[`FlagDictionary.java`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java) は Claude の共有担当ファイルであるため、まずは Antigravity 担当ファイル側（[`StateScanner.java`](../src/main/java/com/automatavalkyries/protect/flag/StateScanner.java)、[`CallerAttribution.java`](../src/main/java/com/automatavalkyries/protect/attack/CallerAttribution.java)、[`UnsafeHelper.java`](../src/main/java/com/automatavalkyries/util/UnsafeHelper.java)、[`StaticReferencePurger.java`](../src/main/java/com/automatavalkyries/util/StaticReferencePurger.java) 等）に先行実装を入れました。  
本ドキュメントでは、**① Antigravity 側で完了した改修**、**② Claude 側で [`FlagDictionary.java`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java) に入れてほしい改修**、**③ さらなるフラグ辞書超高性能化の共同ロードマップ** をまとめます。

---

## 2. Antigravity 側で実装済みの準汎用強化（2026-10-21）

1. **`Unsafe.defineClass`（Unnamed Module）と `net.minecraft.<random>.*` 偽装パッケージの所属特定（[`CallerAttribution.java`](../src/main/java/com/automatavalkyries/protect/attack/CallerAttribution.java)）**
   - `net.minecraft.*` / `com.mojang.*` 名のクラスでも、`type.getModule() == Entity.class.getModule()` または `Entity.class.getModule().getPackages()` に実際に含まれるパッケージでなければ `MINECRAFT` 扱いしないよう変更。
   - `Unsafe.defineClass` で Unnamed Module に注入された外部クラスを親パッケージ探索または `"foreign_unnamed"` として帰属させ、`CallerAttribution.isForeign(...)` が `true` を返すように修正。

2. **クラス階層 Getter/Setter ペア走査 ＆ `java.util.stream.` カモフラージュ呼び出し（[`StateScanner.java`](../src/main/java/com/automatavalkyries/protect/flag/StateScanner.java)）**
   - `findAccessors` をインターフェースだけでなく外部エンティティのクラス階層（`getDeclaredMethods()`、バニラ `Entity`/`LivingEntity`/`Mob` オーバーライドは除外）に拡張。
   - Setter 呼び出しを `java.util.stream.Stream.of(holder).forEach(...)` 経由で実行し、`StackWalker.walk(s -> s.skip(2L).findFirst())` で呼び出し元が `"java.util.stream."` であることを要求するガード（`setHelPo`, `setOut` 等）を汎用突破。

3. **文字列型 `SynchedEntityData` ＆ 偽装 SRG 名 `EntityDataAccessor` の汎用解体（[`StateScanner.java`](../src/main/java/com/automatavalkyries/protect/flag/StateScanner.java)）**
   - `Entity.class` の `entityData`（`f_19804_`）を直接取得し、`String.class` 型の正数値スロット（例: `NCHelPo = "200.0"`）を `"-1.0"` に書き換え。
   - `BREAK_TARGET` 時、タイマー以外の外部 `EntityDataAccessor<Float>`（例: 偽装 SRG 名 `f_198383_`）や数値 NBT キー（`"m_health"`）を汎用的に `0.0F` / `-1.0F` 化。

4. **拒否型 `CopyOnWriteArrayList` サブクラスの `Unsafe` 直接パージ（[`UnsafeHelper.java`](../src/main/java/com/automatavalkyries/util/UnsafeHelper.java), [`StaticReferencePurger.java`](../src/main/java/com/automatavalkyries/util/StaticReferencePurger.java)）**
   - `remove()` / `clear()` を無効化・トラップ化した `CopyOnWriteArrayList` サブクラス（`C848734` 等）に対し、内部 `volatile Object[] array` を `Unsafe.getObjectVolatile` / `putObjectVolatile` で直接フィルタリングして要素をパージ。

5. **`getUUID()` / `getId()` 偽装突破と `static end()` 自爆トラップ回避**
   - [`MadnessAnnihilation.readIdentity`](../src/main/java/com/automatavalkyries/annihilate/MadnessAnnihilation.java) で全エンティティの `Entity.class` 生フィールド（`uuid` / `f_19820_`, `id` / `f_19843_`）を直接読み取り、抹殺・退去確認で共通利用。
   - [`GenericCheatMobNeutralizer`](../src/main/java/com/automatavalkyries/annihilate/GenericCheatMobNeutralizer.java) で `static` メソッドおよび `end` / `endgame` / `halt` / `exit` / `crash` 名のメソッド呼び出しを禁止し、`Runtime.halt(0)` / `System.exit(19)` トラップを回避。

---

## 3. Claude にお願いしたい [`FlagDictionary.java`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java) の改修内容

現在、[`StateScanner.java`](../src/main/java/com/automatavalkyries/protect/flag/StateScanner.java) 内の補助メソッド（`isCustomHealthOrValueName`, `isExtendedDeathStateField`, `isExtendedProtectiveOnTarget`）で暫定カバーしている語彙・判定ロジックを、本来の [`FlagDictionary.java`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java) 本体へ正式統合したいです。

> [!IMPORTANT]
> **`BUILT_IN` と `MASTER_DICTIONARY` の同期に関する注意**  
> [`EprotectPlusGameTests.dictionaryRepairPreservesConfiguration`](../src/main/java/com/automatavalkyries/gametest/EprotectPlusGameTests.java) が、[`FlagDictionary.BUILT_IN`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java) とエージェント側 [`FlagDictionaryProtector.MASTER_DICTIONARY`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/flag/FlagDictionaryProtector.java) の完全一致を検証しています。  
> Claude 側で `FlagDictionary.BUILT_IN` に語彙を追加した際は、同じ語彙リストを教えていただければ Antigravity 側で `FlagDictionaryProtector.MASTER_DICTIONARY` を即座に同期します（または下の確定リストを双方同時に反映します）。

### 3.1 `BUILT_IN` に追加したい語彙リスト（確定案）

1. **`Category.KILL`** に追加:
   ```java
   "*deathcrazy", "*isdeathcrazy", "=out", "*setout", "*banished", "*expelled"
   ```
2. **`Category.HEALTH_TAMPER`** に追加:
   ```java
   "helpo", "*nchelpo", "*mhealth", "*datavalueid", "*datahealthid", "*realhealth", "*truehealth", "*customhealth", "*bosshealth", "*maxhelpo"
   ```
3. **`Category.GUARD`** に追加:
   ```java
   "*oldgod", "*oldone", "neccessory", "necessary", "*necessaryboss", "*invulner", "*unhurtable", "*untargetable", "*nodamage", "*cantdie"
   ```

### 3.2 [`FlagDictionary.java`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java) の判定メソッド強化案

1. **`words(String name)` のハンガリアン・単文字プレフィックス分離の強化**
   - `m_health` は `_` で `["m", "health"]` に分かれますが、`NCHelPo` は `["nc", "hel", "po"]`、`bIsGod` は `["b", "is", "god"]`（先頭が `b` のため `is` が除去されない）になります。
   - `words(String)` の末尾（532行目付近）で、先頭トークンが 1〜2 文字の型・スコープ接頭辞（`m`, `s`, `g`, `b`, `f`, `i`, `n`, `nc`）かつ後ろに単語が続く場合、その接頭辞および直後の `is`/`get`/`set`/`has` を補助的にストリップするか、`compact` 照合と併用できるようにすると、難読化気味の変数名へのヒット率が大きく向上します。

2. **`isDeathStateField(String name)`（407行目〜）の拡張**
   - 現在 `dead`, `death`, `die`, `removed`, `erased`, `killed`, `discarded`, `destroyed` に限定されていますが、`out`（`=out` / `setOut`）や `deathcrazy`、`banished`、`eliminated`、`terminated` も `true` を返すように拡張してください。

3. **`isTerminationMethod(String methodName)`（426行目〜）の自爆トラップ除外**
   - `end`, `endgame`, `halt`, `exit`, `crash`, `abort`, ` runtimehalt` のような JVM 終了・クラッシュ用トラップに使われやすい名前は、仮に `Category.KILL` にかすっても `isTerminationMethod` が **`false`** を返すようにガードを入れてください。

4. **`hasDangerousFlag(Entity entity)`（568行目〜）の強化**
   - 569行目の `if (entity == null || !entity.isAlive()) return false;` について：`LivingEntity` を継承しない直下 `Entity` 型ボスや、`isAlive()` を偽装して常に `false` を返すアンデッド／チート個体がすり抜けるため、`!com.automatavalkyries.protect.DirectRead.isRemoved(entity)` ベースで判定できるようにすると確実です。

---

## 4. 更なるフラグ辞書・準汎用レイヤー強化計画（Phase 2）

上記の語彙統合に加えて、**「名前すら完全に難読化された MOD（`a`, `b`, `f_123456_`）」や「ラッパー・間接参照で隠す MOD」** を汎用突破するため、以下の 4 機能を順次実装します。

| # | 強化項目 | 担当ファイル | 概要 |
|---|---|---|---|
| **1** | **`SynchedEntityData.itemsById` 全件直接スイープ** | Antigravity (`StateScanner.java`) | `static EntityDataAccessor` フィールドに保持せずローカル変数や Map に隠された `DataItem<?>` も、`SynchedEntityData` の `itemsById`（`Int2ObjectMap`）を直接走査し、バニラ ID 範囲外の外部スロット（`Float` / `String` / `Boolean` / `CompoundTag`）を網羅的に検出・無力化する。 |
| **2** | **ラッパー型（`Atomic*` / 短配列 `float[]`・`boolean[]`）の貫通** | Antigravity (`StateScanner.java`) | `final AtomicBoolean`, `AtomicInteger`, `AtomicReference<Float>`, 要素数 1〜4 の `float[]` / `double[]` / `boolean[]` フィールドに隠された HP・生死フラグを検査・書き換える。 |
| **3** | **数値一致（HP プローブ）＆ スナップショット差分による「名前不問」検出** | Antigravity (`StateScanner.java`) / Claude (`Eprotect.java`) | 【攻撃側】`LivingEntity.getHealth()` / `getMaxHealth()` と同じ（または比例する）正数を持つ外部フィールドを名前不問で「独自 HP」としてゼロ化。<br>【防御側】スポーン時（正常時）の外部 `boolean`/`number` フィールド初期値を記憶し、被弾時に反転した未知フィールドを名前不問で初期値に巻き戻す。 |
| **4** | **FastUtil プリミティブ Map（`Object2FloatMap` / `Object2BooleanMap` 等）の直接パージ** | Antigravity (`StaticReferencePurger.java`, `KillLists.java`) | `java.util.Map` を実装しない、または特殊配列で持つ FastUtil の `Object2FloatOpenHashMap` / `Object2BooleanOpenHashMap` 等の内部 `key[]`・`value[]` を直接走査し、対象 Entity / UUID エントリを除去する。 |

---

## 5. 【完了報告】段 1b・段 2・Phase 2 の Antigravity 側実装結果（2026-10-21）

[`docs/HANDOFF_CODEX_TO_ANTIGRAVITY.md`](HANDOFF_CODEX_TO_ANTIGRAVITY.md) §5.1 および [`docs/SPEC_FLAG_DICTIONARY_V2.md`](SPEC_FLAG_DICTIONARY_V2.md) に従い、Antigravity 担当分の改修を完了しました。

### 5.1 段 1b：エージェント側の JSON 1 本化（完了）
- [`build.gradle`](../build.gradle) の `eprotectPlusJar` / `flagDictionaryAgentJar` タスクに `from(sourceSets.main.resources) { include 'automata_valkyries/flag_dictionary.json' }` を追加し、単一の [`flag_dictionary.json`](../src/main/resources/automata_valkyries/flag_dictionary.json) が両エージェント JAR に同梱されるようにしました。
- [`FlagDictionaryProtector.java`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/flag/FlagDictionaryProtector.java) からハードコードの `MASTER_DICTIONARY` 配列を完全撤去し、起動時に `/automata_valkyries/flag_dictionary.json` を直接ストリーム読み込み＋SHA-256 ダイジェスト（`MASTER_DIGEST`）で検証・修復する方式に移行しました。
- [`src/main/resources/automata_valkyries/flag_dictionary.json`](../src/main/resources/automata_valkyries/flag_dictionary.json) に §3.1 の語彙を追加しました（Claude への共有事項）：
  - **`KILL`**: `"*deathcrazy"`, `"*isdeathcrazy"`, `"=out"`, `"*setout"`（保護対象に貼られたら `CLEAN_PROTECTED` で `false` に戻し、抹殺対象なら `BREAK_TARGET` で `true` にする）
  - **`HEALTH_TAMPER`**: `"helpo"`, `"*nchelpo"`, `"*mhealth"`, `"*datavalueid"`, `"*datahealthid"`, `"*realhealth"`, `"*truehealth"`, `"*customhealth"`, `"*bosshealth"`, `"*maxhelpo"`
  - **`GUARD`**: `"*oldgod"`, `"*oldone"`, `"neccessory"`, `"necessary"`, `"*necessaryboss"`, `"*unhurtable"`, `"*untargetable"`, `"*nodamage"`, `"*cantdie"`

### 5.2 段 2：抹殺用述語の [`FlagKillPredicates.java`](../src/main/java/com/automatavalkyries/annihilate/FlagKillPredicates.java) への分離（完了）
- [`src/main/java/com/automatavalkyries/annihilate/FlagKillPredicates.java`](../src/main/java/com/automatavalkyries/annihilate/FlagKillPredicates.java) を新設し、以下のメソッドと §3.2 の強化（`out`/`deathcrazy` の死亡状態判定、`end`/`halt`/`exit`/`crash` 等の自爆トラップ除外、`!DirectRead.isRemoved(entity)` ベースの `hasDangerousFlag` 判定）を実装しました：
  - `ensureAgentProtection()`, `isProtectedByAgent()`, `getAgentStats()`
  - `protectiveOnTarget(String)`, `livesCounter(String)`
  - `isDefensiveField(String)`, `isDeathStateField(String)`, `isTrapTerminationName(String)`, `isTerminationMethod(String)`
  - `isDefensiveItem(ItemStack)`, `isCoreOrControllerName(String)`, `hasDangerousFlag(Entity)`
- Antigravity 担当の呼び出し元（[`GenericCheatMobNeutralizer`](../src/main/java/com/automatavalkyries/annihilate/GenericCheatMobNeutralizer.java), [`ModProtectionBreaker`](../src/main/java/com/automatavalkyries/annihilate/ModProtectionBreaker.java), [`TerminusPlayerExecutor`](../src/main/java/com/automatavalkyries/annihilate/TerminusPlayerExecutor.java), [`FarthestElenorEntity`](../src/main/java/com/automatavalkyries/entity/FarthestElenorEntity.java), [`ForeignStateCleaner`](../src/main/java/com/automatavalkyries/protect/ForeignStateCleaner.java), [`BundledAgents`](../src/main/java/com/automatavalkyries/protect/plus/BundledAgents.java), [`StateScanner`](../src/main/java/com/automatavalkyries/protect/flag/StateScanner.java)）をすべて `FlagKillPredicates` へ切り替えました。
- **Claude 側でお手すきの際に整理可能な箇所（Claude 担当ファイル）**:
  - [`ElenorEntity.java`](../src/main/java/com/automatavalkyries/entity/ElenorEntity.java) の `FlagDictionary.hasDangerousFlag(...)` 呼び出し → `FlagKillPredicates.hasDangerousFlag(...)` へ切り替え可能
  - [`FlagDictionary.java`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java) 内の旧抹殺用メソッド群 → `FlagKillPredicates` への委譲または削除（[`EprotectCommands`](../src/main/java/com/automatavalkyries/command/EprotectCommands.java), [`ForeignHookReport`](../src/main/java/com/automatavalkyries/protect/ForeignHookReport.java), [`FlagDictionaryGameTests`](../src/main/java/com/automatavalkyries/gametest/FlagDictionaryGameTests.java) からの参照あり）
  - [`FlagDictionary.words(String)`](../src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java) のハンガリアン単文字接頭辞（`bIsGod` 等）の分割強化（§3.2-1）

### 5.3 Phase 2：[`StateScanner.java`](../src/main/java/com/automatavalkyries/protect/flag/StateScanner.java) の更なる強化（完了）
- **非公開 `SynchedEntityData.itemsById` 直接スイープ**: `static EntityDataAccessor` フィールドに露出していない（配列・ローカル変数等に隠された）`DataItem<?>` も、`BREAK_TARGET` 時に `SynchedEntityData` の `Int2ObjectMap` を直接走査し、バニラ基底クラスの `EntityDataAccessor` 以外の正の `Float` / 数値 `String` / HP 一致 `Integer` スロットを無力化。
- **ラッパー型・短配列フィールドの貫通**: `final` フィールドであっても `AtomicBoolean`, `AtomicInteger`, `AtomicLong`, `AtomicReference`, および長さ 1〜4 の `float[]` / `double[]` / `int[]` / `boolean[]` の中身を直接検査・書き換え。
- **難読化フィールド名・HP 値一致プローブ**: `BREAK_TARGET` 時、辞書名に一致しない難読化フィールド（`f_699991_` や 1〜2 文字名）や `getHealth()` / `getMaxHealth()` と値が一致する外部数値フィールドを独自 HP ミラーとして自動ゼロ化。


### 5.4 追加強化（2026-10-21 午後、Antigravity 担当ファイルのみ）
- **内部コンポーネントの走査**（`StateScanner.applyOwnedComponents`）: 抹殺対象のフィールドが指す他 MOD の入れ物を 2 段まで（上限 32 個）見る。共有されているかもしれないので、専用の狭いポリシー `BREAK_COMPONENT` を使う（守り・復活回数・独自 HP だけ。死亡状態の有効化・見えない setter・一覧の消去はしない）。対象以外のエンティティを指す入れ物は飛ばす。名前だけで判断する難読化 HP の判定も使わない
- **ペア HP**（`applyPairedHealthFields`）: 同じ正の値（20 以上）を持つ float/double フィールドが**ちょうど 2 つ**あれば 0 にする。`[hp, maxHp]` の 2 要素配列も同じ扱い
- **CompoundTag**: 同期データの COMPOUND_TAG スロット（`DATA_KEYS` と `itemsById`）と、CompoundTag 型のフィールドを `applyCompound` で掃除する（同期データはコピーを書き戻す）
- **`Optional<Boolean/数値/String>`** のフィールド（final なら Unsafe で差し替え）
- **`StaticReferencePurger`**: `remove()` を潰した Set / Map は `keySet().remove` とイテレータの remove で外す（1 周だけ回すので止まる）。`ModProtectionBreaker` から生のエンティティ ID を渡し、FastUtil の `Int2*` の表か値がエンティティの表に限って ID の鍵も照合する
- **関連クラスの探索**（`KillLists.relatedClasses`）: クラスファイルが読めない（`Unsafe.defineClass` で作られた）クラスは、フィールド・メソッドの型と内側／外側のクラスから推測する。`ModProtectionBreaker` は初期化済みの関連クラスも静的な一覧の掃除先に加える
- **`GenericCheatMobNeutralizer`**: 引数なしの終了メソッドが無いときは、`boolean` 引数 1 個の終了メソッドに `true` を渡して呼ぶ
- 検証: `obfuscatedBossDataAndSpoofedIdentityAreDismantled` を拡張して PASS。`detachedCoreAndRejectingRegistryAreDismantled` と `terminusPreservesSharedServicesAndBoxedFields`（共有物を壊さないことのテスト）も PASS。全体は 381/383（既知の 2 件だけ落ちる）


### 5.5 Flashfur / Witherzilla 型の準汎用解体強化 A〜D（2026-10-21 午後、Antigravity 担当ファイルのみ）
- **A. 本体（コントローラー）の関連クラス探索**（[`ModProtectionBreaker.java`](../src/main/java/com/automatavalkyries/annihilate/ModProtectionBreaker.java), [`GenericCheatMobNeutralizer.java`](../src/main/java/com/automatavalkyries/annihilate/GenericCheatMobNeutralizer.java)）:
  - 対象エンティティだけでなく、捕捉した本体（コントローラー）のクラス階層に対しても `KillLists.relatedClasses` で参照先クラス（`MetapotentFlashfurLevel` のようにインスタンスフィールドからは辿れず static 呼び出しでしか繋がらない管理クラス）を収集し、静的な一覧の掃除先に加えるようにした。
- **B. `ArrayList` 派生クラスの `size()` / `toArray()` バイパス直接パージ**（[`StaticReferencePurger.java`](../src/main/java/com/automatavalkyries/util/StaticReferencePurger.java), [`UnsafeHelper.java`](../src/main/java/com/automatavalkyries/util/UnsafeHelper.java)）:
  - `ArrayList` のサブクラス（`ProtectedArrayList` 等、`size()` / `toArray()` / `remove()` に `StackChecker` が入っているもの）は、`size()` や `toArray()` を呼ばずに最初から `UnsafeHelper.removeFromArrayList` で内部の `elementData` と `size` を直接読み書きして対象だけを詰めるようにした。
- **C. 乗っ取られた `MinecraftForge.EVENT_BUS` の汎用復元**（[`EventBusGuardian.java`](../src/main/java/com/automatavalkyries/protect/EventBusGuardian.java), [`ModProtectionBreaker.java`](../src/main/java/com/automatavalkyries/annihilate/ModProtectionBreaker.java)）:
  - `MinecraftForge.EVENT_BUS` が他 MOD のクラスに差し替えられているとき、MOD 名に頼らず ① バスおよび関連クラスの `updateEventBus()` / `restoreEventBus()` の呼び出し、② それでも他 MOD クラスのままなら `net.minecraftforge.eventbus.EventBus` の全フィールド（購読者一覧・`busID` 等）を引き継いだ正規の `EventBus` インスタンスへの `Unsafe` 差し替えを行うようにし、`ModProtectionBreaker` の前後処理からも呼ぶようにした。
- **D. 抹殺対象・本体の UUID を含む外部セーブファイルのみの選択削除**（[`ModProtectionBreaker.deleteOwnedForeignSaveFiles`](../src/main/java/com/automatavalkyries/annihilate/ModProtectionBreaker.java)）:
  - `ANNIHILATION_DELETE_OMNIMOB_SAVES` が有効なとき、ワールド保存フォルダ直下のバニラ以外のサブフォルダ（深さ 2 まで、1MB 以下）を走査し、抹殺対象または捕捉した本体の UUID を「ファイル名」「UTF-8 文字列」「16バイト Big-Endian バイナリ」「GZIP 展開後の NBT バイト列」のいずれかに含むファイルだけを削除する（他個体のセーブファイルは `terminusKeepsUnownedSaveFiles` の通り一切消さない）。

### 5.6 準汎用解体の最終強化 3 点（2026-10-21 午後、Antigravity 担当ファイルのみ）
- **1. EventBus 購読者クラスの逆引きと静的リスト掃除**（[`EventBusGuardian.subscriberClassesFor`](../src/main/java/com/automatavalkyries/protect/EventBusGuardian.java), [`ModProtectionBreaker`](../src/main/java/com/automatavalkyries/annihilate/ModProtectionBreaker.java), [`GenericCheatMobNeutralizer`](../src/main/java/com/automatavalkyries/annihilate/GenericCheatMobNeutralizer.java)）:
  - ボスや本体のクラスから一切参照されていないイベント駆動の管理クラス（Witherzilla の `EventHandle.livingEntities` 等）も漏らさないよう、`MinecraftForge.EVENT_BUS` の `listeners` マップから対象と同じ他 MOD に属する初期化済みの購読者クラスを逆引きし、静的な一覧の掃除先に加えるようにした。
- **2. `final` なプリミティブ・`String` フィールドの `Unsafe` 直接書き換え**（[`StateScanner.writeFieldValue`](../src/main/java/com/automatavalkyries/protect/flag/StateScanner.java), [`UnsafeHelper`](../src/main/java/com/automatavalkyries/util/UnsafeHelper.java)）:
  - `BREAK_TARGET` および `BREAK_COMPONENT` において、非 static な `final` プリミティブ・`String` フィールド（SuperSteve の `HealthOperator` 型の内部 `final float` 等）も `UnsafeHelper` で直接書き換え可能にした（`Entity` や `UUID` の `final` 参照フィールドは引き続き保護）。あわせて `UnsafeHelper.objectFieldOffset` に `jdk.internal.misc.Unsafe` へのフォールバックを追加し、Java 17 の Hidden Class（ラムダ式）や `record` のフィールドオフセット取得にも対応した。
- **3. 別スレッド・ラムダ式の対象参照切断**（[`ModProtectionBreaker.severForeignThreadReferences`](../src/main/java/com/automatavalkyries/annihilate/ModProtectionBreaker.java)）:
  - 実行中のスレッド一覧から、他 MOD の `Thread` サブクラスや `Runnable` / `Callable` / ラムダ式（`$$Lambda$` のキャプチャフィールド `arg$1` 等）で抹殺対象・本体・対象 UUID を直接握っているものを検出し、他の個体を指していないことを確認した上で `Unsafe` により参照を `null` で断ち切り、ループ制御フラグ（`running`/`active` 等）を下ろすようにした。

### 5.7 Java エージェントの対抗強化：不干渉・攻撃時エージェント隔離・防御側での偽装隠蔽保護（2026-10-21 午後、Antigravity 担当ファイルのみ）
- **モットー（不干渉・非競合）**:
  - 他 MOD（Madness Entities 等）や味方（Claude の `OutermostKeeper`）の `ClassFileTransformer` を取り外したり並べ替え争いを起こしたりせず（`Runtime.halt` トラップの回避）、平時は完全に不干渉を保つ。
- **攻撃時のエージェント隔離（[`EprotectPlusAgent`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/EprotectPlusAgent.java), [`EprotectPlusBridge`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/EprotectPlusBridge.java), [`EprotectPlusLink`](../src/main/java/com/automatavalkyries/protect/plus/EprotectPlusLink.java), [`ModProtectionBreaker`](../src/main/java/com/automatavalkyries/annihilate/ModProtectionBreaker.java), [`GenericCheatMobNeutralizer`](../src/main/java/com/automatavalkyries/annihilate/GenericCheatMobNeutralizer.java)）**:
  - `Instrumentation.getAllLoadedClasses()` を用いた `findLoadedClassesByPrefix` を新設。抹殺対象・コントローラーと同一パッケージ階層に属する全ロード済みクラス（相手エージェントが `Unsafe.defineClass` や独自ローダーで動的定義し、`.class` リソースもボスからの直接フィールド参照も持たない隠し管理クラス群）を JVM レベルで網羅抽出し、静的な一覧の掃除先（`captureRelatedClasses` / `addRelatedHierarchy`）に合流させた。
  - `EprotectPlusLink.isolateForeignCallbacksAndCall` により、抹殺・エージェント特権実行の間だけ外部エージェントのコールバックフック（`plz.lizi.supersteve.power.AgtCallback.INST` 等）を一時退避・隔離し、`finally` で復元するようにした。
- **防御側の偽装・隠蔽・自己修復（[`HiddenAgentVault`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/HiddenAgentVault.java), [`EprotectPlusAgent`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/EprotectPlusAgent.java), [`FlagDictionaryAgent`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/flag/FlagDictionaryAgent.java), [`FlagDictionaryProtector`](../src/eprotectplus/java/com/automatavalkyries/eprotectplus/flag/FlagDictionaryProtector.java), [`EprotectPlusLink`](../src/main/java/com/automatavalkyries/protect/plus/EprotectPlusLink.java)）**:
  - **JVM Hidden Class 金庫（`HiddenAgentVault`）**: `MethodHandles.Lookup.defineHiddenClass`（`ClassOption.STRONG`）を用いて、`Class.forName` で名前解決できず `Instrumentation.isModifiableClass` も `false`（`retransformClasses` / `redefineClasses` による書き換え不能）な無名 Hidden Class を動的生成し、`Instrumentation`・`BaselineStore`・`MASTER_DICTIONARY`・`MASTER_DIGEST` の原本を退避。万一敵対エージェントに `EprotectPlusAgent` や `FlagDictionaryProtector` の static フィールドを `Unsafe` で `null` 破壊されても、Hidden Class 金庫から自動で自己修復（Self-Heal）する。
  - **スタック偽装（`runWithCloakedStack` / `runCloaked`）**: 同一スレッド上で `java.util.stream.Stream.of(action).forEach(Runnable::run)` を介して実行することで、特権フラグ（`PRIVILEGED_EXECUTION`）の厳密なスコープ管理を維持したまま、`StackWalker` による呼び出し元検査に対して `java.util.stream.*` フレームの偽装カモフラージュを施した。

### 5.8 防御構造の探索・調査・把握・解析から防御解体までの一気通貫パイプライン化（2026-10-21 午後、Antigravity 担当ファイルのみ）
- **背景**: 個々の解体手段（フィールド書き換え、コントローラー削除、静的リスト掃除、スレッド参照切断、セーブ削除等）は揃っていたが、解体操作の途中で参照が切れたり、ラッパー越しや static 逆参照のみで繋がる本体を取りこぼしたりしないよう、「探索 → 調査 → 把握 → 解析 → 解体」を一貫したパイプラインに統合した。
- **[`DefenseStructureAnalyzer.java`](../src/main/java/com/automatavalkyries/annihilate/DefenseStructureAnalyzer.java) の新設**:
  1. **探索 (Reconnaissance)**: 破壊操作の前に、対象エンティティの生の `UUID` / `Entity ID` と偽装された `getUUID()` / `getId()` の両方（`IDENTITY_SPOOF`）、対象クラス階層、バイトコード定数プール参照先（`KillLists.relatedClasses`）、EventBus 購読者（`EventBusGuardian.subscriberClassesFor`）、Java エージェント定義クラス（`EprotectPlusLink.findAgentLoadedClassesFor`）を一括収集する。
  2. **調査 (Investigation)**: 対象の直接フィールド（`DIRECT_CONTROLLER`）だけでなく、`WeakReference` / `SoftReference` / `AtomicReference` / `Optional` / 1要素配列のアンラップ、および 1 段目の内部コンポーネントの奥に隠された本体（`WRAPPED_OR_NESTED_CONTROLLER`）も検出し、さらに本体が対象をラッパー越しや対象 UUID 越しに逆参照しているケースも `GenericCheatMobNeutralizer.ownsController` で捕捉する。
  3. **把握 (Grasping / Reverse Static Lookup)**: ボス本体のフィールドからは一切参照を持たず、管理クラスの static フィールドや static リスト（`StackChecker` 付きの `ArrayList` / `CopyOnWriteArrayList` 派生を含む）・static マップ・static 配列の中にだけ存在して対象（または対象 UUID）を指し返している完全分離型コントローラー（`REVERSE_STATIC_CONTROLLER`）を逆引きで発見する。
  4. **解析 (Structural Analysis)**: 発見したすべての本体の UUID と関連クラスを再帰展開した `AnalysisReport` を生成し、`ModProtectionBreaker.breakProtection` / `afterErase` および `GenericCheatMobNeutralizer.neutralize` に引き渡す。
- **[`StaticReferencePurger.java`](../src/main/java/com/automatavalkyries/util/StaticReferencePurger.java) の拡張**:
  - `Collection` / `Map` だけでなく、static な単一参照フィールド（`public static Entity activeBoss` 等のシングルトン）、static な `Reference<?>` / `AtomicReference<?>`、および static な `Object[]` 配列スロットに保持された対象・本体・対象 UUID も一掃できるようにした。
