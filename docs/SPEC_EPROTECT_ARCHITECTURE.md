# Eprotect 保護アーキテクチャ仕様書

最終更新: 2026-10-02 / 対象: Minecraft 1.20.1・Forge 47.4.x・Java 17

この文書は、Automata Valkyries の保護の仕組み（Eprotect）を、他の MOD 制作でも使い回せるようにまとめた仕様書である。
コードの場所は `src/main/java/com/automatavalkyries/` からの相対パスで書く。

## 0. 範囲

- **書くもの**: 保護対象（ボス・兵姫）を、他 MOD の即死・消去・凍結から守る仕組みと、その観察・記録・テスト。
- **書かないもの**: 即死側（抹殺の棒・真抹殺・終焉の魔杖、Eprotect+ Java エージェントの抹殺・迂回の処理）。これは Antigravity 側の担当で、
  `src/eprotectplus/`・`protect/plus/`・`annihilate/`・`TrueKillStickItem` 等にある。本書では「保護の外側に別の層がある」ことだけ触れる。

## 1. 設計の原則

1. **禁止の列挙ではなく、正しい状態へ戻す。** 手口を 1 つずつ塞ぐのではなく、「正しい状態」を持っておき、毎 Tick そこへ戻す。
   未知の即死 MOD にも効く。
2. **getter を信用しない。** `getHealth` / `isAlive` / `isRemoved` は他 MOD に偽装されうる。判定・修復は保存場所（フィールド・同期データ）を直接読む（`protect/DirectRead`）。
3. **部分的に直さず、正規の経路で丸ごとやり直す。** ワールドへの載り方が 1 か所でも崩れていたら、古い参照を外してバニラの `addFreshEntity` で入り直させる（`protect/WorldPresence`）。
4. **状態の正本をエンティティの外にも置く。** 最後に正常だった写しをワールドデータに保存し、エンティティごと失われても作り直す（`protect/EprotectRegistry`）。
5. **無敵にはしない。** 正規の戦闘での撃破は通す。1 撃・時間あたりの被ダメージに上限をかけるだけ。
6. **追いつかなければ壊れる前に退く。** 修復が続くときは、正規の退場処理（セリフ・効果音）で安全に退避させる。
7. **層を分ける。** 準汎用の層（どの MOD でも効く）と、MOD ごとの互換層を分け、互換層を切っても（`-PgenericOnly`）準汎用だけで守れることをテストで測る。
8. **ワールドはメインスレッドでだけ書き換える。** 別スレッドは見張りと記録だけ。

## 2. 全体構成

```
                    ┌────────────────────────────────────────────┐
  他 MOD の攻撃 ──▶ │ ① 入口の拒否（Mixin / Forge イベント）          │
                    │   消去・die・/kill・被弾外の HP 減少・強制移動 等 │
                    ├────────────────────────────────────────────┤
                    │ ② 被弾の上限（1 撃・時間窓ごと）                 │
                    ├────────────────────────────────────────────┤
                    │ ③ 毎 Tick の自己修復（Eprotect.repair）          │
                    │   HP / 死亡フラグ / 最大 HP / 消去理由            │
                    │   ワールドへの載り方の点検 → 入り直し            │
                    │   他 MOD の状態のリセット（フラグ辞書）          │
                    │   他 MOD の抹殺予定の一覧から外す               │
                    │   Tick 停止・時間停止の検出と代行               │
                    ├────────────────────────────────────────────┤
                    │ ④ 登録簿からの作り直し（エンティティごと消えた）  │
                    ├────────────────────────────────────────────┤
                    │ ⑤ 退避（修復が追いつかない）                     │
                    └────────────────────────────────────────────┘
   横断: 攻撃の記録・MOD の割り出し・ブラックリスト / 観察（ウォッチドッグ・観察エージェント）
   互換層: MOD ごとの互換 Mixin・KillModCounters（-PgenericOnly で切れる）
```

主なクラス:

| 役割 | クラス |
|---|---|
| 個体ごとの保護コントローラー | `protect/Eprotect` |
| 全保護対象の周期処理（サーバー Tick の最後） | `protect/EprotectManager` |
| 保護対象の目印 | `protect/IEprotected`（退場のセリフは `IProviderWithSpeech`） |
| 実際の値を読む | `protect/DirectRead` |
| ワールドへの載り方の点検と入り直し | `protect/WorldPresence` |
| 状態の写し（ワールドデータ） | `protect/EprotectRegistry` |
| 入口の拒否 | `mixin/EprotectEntityMixin`・`EprotectDamageMixin`・`EprotectSectionCallbackMixin`・`EprotectSynchedDataMixin`・`protect/EprotectForgeEvents` |
| 準汎用の状態リセット | `protect/flag/FlagDictionary`・`StateScanner`・`InjectedMembers`・`KillLists`・`protect/ForeignStateCleaner` |
| 互換層 | `mixin/compat/*`・`protect/KillModCounters`・`protect/EprotectCompat`・`protect/SpecificCountermeasures` |
| 攻撃の記録 | `protect/attack/*` |
| Tick の入口の犯人探し（報告のみ） | `protect/TickEntranceHooks` |
| 観察 | `protect/EprotectWatchdogAgent`・`protect/ForeignHookReport`・`protect/ObserverLink`（＋ `src/observer` の Java エージェント） |
| プレイヤーの保護（護符） | `protect/PlayerWard`・`mixin/PlayerWardMixin` |

## 3. 各構成要素

### 3.1 入口の拒否

保護が有効な間、次の操作を入口（HEAD）で止める。止めた操作は攻撃として記録する（§3.9）。

- 消去: `remove` / `discard` / `setRemoved` / `onRemovedFromWorld`、エンティティ区画からの消去（`EprotectSectionCallbackMixin`）。
  チャンクの読み込み解除・次元移動など正規の理由は通す（入口で判定した次元移動だけ許可する）。
- 死亡: 被弾処理（`hurt`）の外からの `die()` の直接呼び出し。HP を戻して `deathTime = 0`。
- 即死ダメージ: `/kill` 型のダメージ。
- 被弾以外での HP 減少（設定 `protection.rejectHealthLossOutsideDamage`）。
- 他 MOD が保護対象に付ける同期データの印（例: Hyperlink の novelize）。
- 遠くへの強制移動・奈落・ワールド外への飛ばし（安全座標へ戻す）。

**正規の撃破は通す**: `hurt()` の実行中で、被弾前の HP が 1 撃で倒せる残量以下のときの死亡だけは正規として扱い、撃破演出を最後まで走らせてから消去を許可する（`Eprotect.isLegitimateFatalBlow` / `isDying`）。

### 3.2 被弾の上限

`Eprotect.capFinalDamage`（`LivingDamageEvent` の LOWEST で最後に適用）。

- NaN・無限大・1000 以上の単発は 100 に置き換える。
- HP から引くのは **200 Tick（10 秒）の窓の中で合計で最大 HP の 10% まで**。無敵時間を消して `hurt()` を連打されても、1 Tick で倒されない。
- ブラックリストの MOD からのダメージは 1% まで（罰則）。
- 窓の時計はサーバーの Tick 数（§3.7）。

### 3.3 毎 Tick の自己修復（`Eprotect.repair`）

本体の Tick（`Eprotect.tick`）と、マネージャーの周期処理（本体の Tick が止められていても走る）の両方から呼ぶ。順序が意味を持つ:

1. MOD ごとの対策（`KillModCounters`）で、その MOD のフラグ・登録を解除する（残っていると以後の読み出しが偽装されるので最初）。
2. 他 MOD の抹殺予定の一覧から外す（`KillLists`。§3.6）。
3. 最大 HP 属性の改ざんを戻す・HP を戻す・死亡フラグを戻す。
4. ワールドへの載り方を点検し、崩れていれば入り直す（§3.4）。
5. 他 MOD が付けた状態をフラグ辞書で戻す（`ForeignStateCleaner`。vanilla の状態を戻した後に回す）。
6. 読み出しの偽装を記録する（直せるのは原因の印だけ）。
7. Tick 停止の検出と代行（§3.7）。
8. 修復した件数を積み、追いつかなければ退避（§3.8）。

正常な間は、最後に正常だった状態（HP・安全座標）と、一定間隔で登録簿への写しを残す。

### 3.4 入り直し（`WorldPresence`）

即死 MOD は消去理由・ワールドへの接続（levelCallback）・座標や、ワールドの各一覧（UUID 登録・検索表・区画・Tick 一覧・追跡）を直接書き換えて消す。
壊れた箇所を 1 つずつ直すと、順番や漏れで中途半端な状態が残る（実際に「already tracked」のクラッシュや再登録の失敗が起きた）。

- `inspect`: 各一覧に正しく載っているか、フィールドが正しいかを点検する。
- `rejoin`: 1 つでも崩れていれば、古い参照をすべて外し、フィールドを「ワールドに未追加」の状態へ戻して `addFreshEntity` で入り直させる。
  接続・一覧への登録・追跡はバニラが作り直す。
- 入り直しを拒否されたら（他 MOD がイベントを取り消す等）、同じ UUID の新しい個体に差し替え、古い個体の保護を解く（`retireReplaced`）。

### 3.5 登録簿（`EprotectRegistry`）

- ディメンションごとのワールドデータ（`SavedData`）に「最後に正常だった写し」を持つ。HP が正常で、ワールドに正しく載っていた Tick だけ写す。
- マネージャーが 20 Tick ごとに見回り、どこにも居ない保護対象を写しから作り直す。作り直しの連発は時間窓で抑える。
- 改ざんされた写しは拒否する。次元移動したときは移動元の記録を消す（複製を防ぐ）。

### 3.6 準汎用の層（MOD を知らなくても効く）

**フラグ辞書（`protect/flag/FlagDictionary`）**: 名前を単語に分けて分類する。

| 分類 | 意味 | 保護対象では | 抹殺（Antigravity 側）では |
|---|---|---|---|
| KILL / CONTROL / HEALTH_TAMPER / BANISH | 死亡・停止・HP 改ざん・追放 | 戻す | - |
| BYPASS | 守りの迂回 | 戻す | - |
| GUARD / REVIVE | 守り・復活 | 触らない | 外す |
| TIMER / COUNT | 単独では手がかりにならない | 組み合わせで判断 | |

設定 `flagDictionary.extra` / `ignore` と `/eprotect flags` で調整する。

**状態の読み書き（`StateScanner`）**: 他 MOD が付けた状態を次の場所から見つけて辞書に従って戻す。
他 MOD のインターフェースの getter/setter、既存のクラスに後から足されたフィールド（`InjectedMembers`: jar の元のクラスファイルと比べて見つける。名前の付け方に左右されない）、
他 MOD のエンティティのクラス自身のフィールド、Capability、永続データ（NBT）、同期データ。

**抹殺予定の一覧（`KillLists` / `AttackerListCleanup`）**: 即死 MOD は消した個体を static な一覧に載せて後から攻撃し直す・再登録を拒否する。
読み込み済み・初期化済みの他 MOD のクラスだけを覗いて一覧を見つけ、保護対象を毎 Tick 外す。攻撃を止めたときは、攻撃したクラスの一覧からも外す。

**実際の値（`DirectRead`）**: 生死の判定はすべて保存場所から直接読む。

**監視（`FlagWatchAgent`）**: 他 MOD の状態を読むだけで見張り、死亡・消去・攻撃の直前にだけ変わる辞書に無い名前を候補に挙げる（辞書には自分で足さない）。

### 3.7 Tick 停止・時間停止への対策

- **時計**: 被弾・修復・復元の時間窓と Tick 停止の検出は、ワールドのゲーム時刻ではなくサーバーの Tick 数で測る。
  ワールドの Tick ごと止める時間停止ではゲーム時刻も止まり、窓が明けなくなるため。
- **Tick 一覧から外された**: 一覧へ入れ直す（修復として数える）。
- **一覧に載っているのに Tick が来ない**（ワールドの Tick ごと止める型、反復で読み飛ばす型）: 止まっている間は毎 Tick、マネージャーから本体の Tick を代わりに回し、
  その移動・状態の変化をクライアントへ送る（`ServerEntity.sendChanges`。送るのも本来ワールドの Tick の役目なので）。修復には数えない（数えると時間停止が続くだけで退避する）。
- **代わりに回しても入口で打ち切られる**: その入口に処理を差し込んだ、打ち切れる作りの他 MOD の Mixin を候補として記録する（`TickEntranceHooks`。報告のみ）。
  エンティティ単位で止める型は互換 Mixin が要る（例: No-Sugar は `NoSugarMixins$TimeStop`）。

### 3.8 退避

`protection.retreatAnomalyThreshold`（既定 200）件の修復が `protection.retreatWindowTicks`（既定 1200）の間に積もったら、正規の退場処理（セリフ・効果音つき）で退避させる。
壊れかけの状態で居座らせないための安全弁。テスト用の棒で連打するとこれが働くので、即死に見えることがある（HP は減っていない）。

### 3.9 攻撃の記録・MOD の割り出し・ブラックリスト（`protect/attack/`）

- 拒否・修復した操作を、操作名・結果（REFUSED / SHIELDED / NEUTRALIZED / REPAIRED / DETECTED / BLOCKED_BLACKLIST）・呼び出し元と一緒に記録する。
  `/eprotect attacks`、`logs/automata_valkyries-attacks.log`。
- **MOD の割り出し**（`CallerAttribution`）: スタックから、自分・Mixin の仕組み・プラットフォームのフレームを飛ばした最初の呼び出し元と、最寄りの他 MOD を求める。
- **ブラックリスト**（`ModBlacklist`）: 手動（`/eprotect blacklist`）と自動（既定 1200 Tick に 20 回）。載った MOD の即死系の操作（強制移動・次元移動・ダメージ外の HP 減少）だけ追加で止める。
  通常ダメージは通す（上限を下げるだけ）。`blacklist.neverBlacklist` の MOD と自分自身は決して載せない。
- 候補止まりの記録（DETECTED の一部）はブラックリストの回数に数えない。

### 3.10 互換層（MOD ごと）

- `mixin/compat/*`: 各即死 MOD のキル処理の入口に差し込み、対象が保護中なら実行させない（`EprotectCompat.shouldShield` / `shieldAttack`）。
  `@Pseudo`・`require = 0` で、その MOD が無ければ何もしない。
- `KillModCounters`: 各 MOD のフラグ・登録の解除。
- 他 MOD の不具合を直す互換も置く（例: No-Sugar のストップウォッチに空のタグが付いて使えなくなる不具合）。
- `SpecificCountermeasures`: `-PgenericOnly`（システムプロパティ `automata_valkyries.genericOnly=true`）で互換層をまとめて切れる。

### 3.11 観察（報告のみ）

- **ウォッチドッグ**（`EprotectWatchdogAgent`）: メインスレッドの停止を別スレッドから見張り、スタックを出す。
- **他 MOD のフック一覧**（`ForeignHookReport`）: 起動時、生死・ワールド管理に関わる vanilla のクラスに他 MOD が Mixin で足した処理と、足したフィールドを一覧にする。
- **観察用 Java エージェント**（`src/observer`、`-PwithObserver`、JDK だけに依存）:
  - 他の Java エージェントによる実行中のクラスの書き換え（再変換・再定義）と呼んだ側を記録（`/eprotect observer`）。
  - 能力の地図: 他 MOD のクラスが何をできる作りか（Unsafe での書き込み・クラスの書き換え・自己 attach・JVM の終了・ワールドの一覧への手出し等）を定数プールから割り出し、
    そのクラスが使う名前をフラグ辞書で分類して出す（`/eprotect observer capabilities`）。できることであって、したことではない。
  - 改ざん防止: このMOD 自身のクラスを他のエージェントが書き換えたら、読み込んだときの形に戻す（`=noguard` で切れる）。他 MOD のクラスは書き換えない。

### 3.12 プレイヤーの保護（護符）

守護の護符（`WARD_CHARM`）を持つプレイヤーは、他 MOD による強制テレポート・次元移動と即死の経路を拒否する。通常のダメージでは死ぬ（上限はかけない）。
vanilla の移動（エンダーパール・/tp・ポータル・リスポーン）は通す。死亡しても護符は手元に残る。

## 4. 設定（`config/EprotectConfig`、`automata_valkyries-common.toml`）

| キー | 既定 | 内容 |
|---|---|---|
| `protection.rejectHealthLossOutsideDamage` | true | 被弾以外での HP 減少を拒否 |
| `protection.retreatWhenUnrepairable` | true | 修復が追いつかなければ退避 |
| `protection.retreatAnomalyThreshold` | 200 | 退避までの修復件数 |
| `protection.retreatWindowTicks` | 1200 | その時間窓 |
| `attackLog.enabled` / `writeFile` / `maxEntries` | true / true / 256 | 攻撃の記録 |
| `blacklist.mods` / `neverBlacklist` | 空 | 手動のブラックリスト・除外 |
| `blacklist.auto.enabled` / `threshold` / `windowTicks` | true / 20 / 1200 | 自動ブラックリスト |
| `blacklist.forcedRelocationDistance` | 8.0 | 強制移動とみなす距離 |
| `flagDictionary.extra` / `ignore` | 空 | 辞書の追加（`分類:名前`）・除外 |
| `flagDictionary.watch.*` | | 監視の間隔・対象数・候補にする回数 |
| `diagnostics.reportForeignEntityHooks` | true | 起動時のフック一覧 |
| `diagnostics.watchdog.*` | true / 100ms / 2000ms | ウォッチドッグ |

## 5. コマンド・ログ

| 用途 | コマンド / ファイル |
|---|---|
| 攻撃の記録 | `/eprotect attacks`、`logs/automata_valkyries-attacks.log` |
| ブラックリスト | `/eprotect blacklist list / clear / auto ...` |
| フラグ辞書 | `/eprotect flags`、`/eprotect flags watch`、`logs/automata_valkyries-flag-candidates.txt` |
| 観察エージェント | `/eprotect observer`、`/eprotect observer capabilities`、`logs/automata_valkyries-observer.log` |
| ゲーム全体のログ | `run/logs/latest.log`・`debug.log`（`[eprotect]`・`[eprotect-attack]`・`[observer]` の印） |

## 6. テストの方針

- `./gradlew runGameTestServer`（GUI なし）で全 GameTest を回す。主な組み合わせ: 通常 / `-PgenericOnly` / `-PwithObserver` / 即死系 MOD（`-PwithKillMods -PwithKillMods2 -PwithEca -PwithSlashBlade`）。
- 即死 MOD が入っていなければ、その MOD のテストは何もせず成功する。他 MOD の処理は実物を直接呼び、判定は getter ではなくフィールド・一覧を直接見る。
- 準汎用の層は、偽の即死 MOD（`gametest/fake/`）で手口を再現してテストする。
- 時間停止のように同じワールドの他のテストを止めるものは別の batch で走らせ、GameTest の遅延（ゲーム時刻）ではなくサーバー Tick で解除する。
- クライアント専用の MOD（IMDS・Witherzilla・Omni-Mobs・The Last Sword）は `runClient` で手動確認する。

## 7. 他の MOD への移植手順

1. **保護対象**: エンティティに `IEprotected` を実装し、`Eprotect` を 1 つ持たせる。`tick()` から `getEprotect().tick()` を呼ぶ。
   ワールドへの追加・削除で `onAddedToWorld` / `onRemovedFromWorld` を呼ぶ。退場のセリフが要るなら `IProviderWithSpeech`。
2. **Mixin**: `mixin/` の入口の拒否（`EprotectEntityMixin`・`EprotectDamageMixin`・`EprotectSectionCallbackMixin`・`EprotectSynchedDataMixin`）と Accessor 群
   （`EntityAccessor`・`LivingEntityAccessor`・`ServerLevelTickListAccessor`・`EntityManagerAccessor`・`EntityLookupAccessor`・`EntityTickListAccessor`・`ChunkMapAccessor`・`TrackedEntityAccessor`）を持っていく。
   `OffThreadPositionMixin` は別スレッドでの座標設定で固まるのを防ぐので一緒に持っていく。
3. **登録**: `EprotectManager` を `MinecraftForge.EVENT_BUS` に登録し（サーバー Tick の END で周期処理、`LivingDamageEvent` の LOWEST で被弾の上限）、
   `EprotectRegistry` の `SavedData` と `ForgeChunkManager` のコールバック（保護対象のチャンクを読み込んだままにする）を登録する。
4. **準汎用の層**: `protect/flag/` 一式と `ForeignStateCleaner`・`DirectRead`・`WorldPresence` を持っていく。辞書は MOD ごとの名前で `extra` / `ignore` を調整する。
5. **記録**: `protect/attack/` を持っていく。`CallerAttribution` の「自分」の判定（パッケージ名）を移植先の名前に変える。
6. **互換層**: 移植先で想定する即死 MOD の分だけ `mixin/compat/` を選ぶ。全部 `@Pseudo`・`require = 0` なので、入っていない MOD の分は害が無い。
7. **1.20.1 の注意**: Mixin の対象メソッドは開発環境の公式名で書き、refmap で配布環境の SRG 名に変わる。互換 Mixin の `remap = false` は他 MOD のメソッド名にだけ使い、
   Minecraft のメソッドを指す `@At` には `remap = true` を付ける。Mixin のパッケージにふつうのクラス（ヘルパー）を置かない（読み込めずに落ちる）。

## 8. 既知の限界

- JVM・スレッドそのものを止める、ネイティブコードで書き換える、このMOD より先に読み込まれて乗っ取る、といった Minecraft のロジックより下の攻撃は守れない
  （Java エージェントによる攻撃への対策は Antigravity 側の Eprotect+ が担う）。
- エンティティの Tick の入口で個別に止める型は、準汎用の層では外せない（検出・報告のみ）。互換 Mixin が要る。
- 毎 Tick 状態を前の値へ巻き戻す型は、Tick が走っているので停止としては検出できない（修復の対象にはなる）。
- 能力の地図・フラグ辞書の Minecraft 側の名前は開発環境（公式名）で見る。配布環境では SRG のフィールド名以外は当たらない。
- 修復が続くと退避する（安全弁）。テストで連打するときは閾値を上げる。
