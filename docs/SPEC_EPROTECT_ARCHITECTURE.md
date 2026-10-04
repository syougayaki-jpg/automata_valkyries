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
| RESPAWN_BLOCK | リスポーンの禁止・妨害（`noRespawn`・`antiRespawn`・`permaDeath` 等） | リスポーンの片付けで戻す | - |
| CONCEAL | 隠す（`invisible`・`hideBossBar`・`cancelRender`・`clientDelete` 等。消したように見せる） | 戻す | - |

2026-10-03 に足した語: 位置・移動・入力の固定（CONTROL: `*locationlock`・`*movelock`・`*inputlock` 等）、HP の錨（HEALTH_TAMPER: `*worldanchor`・`*healthlockenc`）、
短い守りの語（GUARD: `=invul`・`=defence`）、復活の見張り・自動復帰（REVIVE: `*allreturn`・`*autorevive` 等）。The Last Sword・ECA で実際に見た名前を一般化したもの。
時間停止の語（CONTROL: `*timefreeze`・`*worldstop`・`*frozenworld`・`*stoppeddimension` 等）と `*nogravity` も 2026-10-03 に足した。
止める・隠す一覧（名前かクラス名が CONTROL / CONCEAL の static な一覧。時間停止中の個体の一覧など）も、抹殺予定の一覧と同じく `KillLists` が見つけて保護対象を外す。
エージェント（Eprotect+）のマスター辞書には未反映（改ざんの修復が走ると新しい語・分類 RESPAWN_BLOCK / CONCEAL が消える。Antigravity 側で足す）。

設定 `flagDictionary.extra` / `ignore` と `/eprotect flags` で調整する。

**リスポーンの片付け（`protect/RespawnSanitizer`、2026-10-03）**: プレイヤーのリスポーンを壊す他 MOD の状態を辞書で見分けて戻す（準汎用。`-PgenericOnly` でも有効）。

| いつ | 誰に | 何を |
|---|---|---|
| 死亡時（`LivingDeathEvent` の最後）・リスポーンの直前（`PlayerList.respawn` の最初） | 死んだ古い個体 | 守り・復活の許可・停止・HP の固定・リスポーンの妨害の真偽を下ろす（`StateScanner.RELEASE_DEAD_PLAYER`。永続データはリスポーンの妨害・停止のキーだけ消す。PlayerPersisted が新しい個体へ引き継がれるため） |
| リスポーン直後から 40 Tick、毎 Tick | 新しい個体 | 死亡・停止・HP 改ざん・追放・迂回の印とリスポーンの妨害を戻す（`CLEAN_RESPAWNED_PLAYER`）。抹殺予定の一覧（`KillLists`）から外す。守りと記録（最後に死んだ場所・死亡回数等。`FlagDictionary.recordsData`）は触らない |
| リスポーンの要求（`RespawnCommandMixin`） | 死亡が確定した（`dead`）のに HP を戻された個体 | HP を 0 にして要求を通す（vanilla は HP > 0 だと無視する） |
| 新しい LocalPlayer ができたとき（クライアント） | 自分 | `CLEAN_RESPAWNED_PLAYER` |
| 毎 Tick（クライアント、`client/LocalPlayerRejoin`） | 自分 | クライアントのワールドに載っていなければ載せ直す（載っていないと Tick されず WASD が効かない） |
| 毎 Tick（サーバー、`StalePlayerSweeper`）・リスポーン時（`PlayerRespawnReleaseMixin`） | 古い個体 | ワールドのプレイヤー一覧・追跡・検索表に残っていたら外す（残ると同じ ID の別プレイヤーとして本人の画面に送られる） |

**状態の読み書き（`StateScanner`）**: 他 MOD が付けた状態を次の場所から見つけて辞書に従って戻す。
他 MOD のインターフェースの getter/setter、既存のクラスに後から足されたフィールド（`InjectedMembers`: jar の元のクラスファイルと比べて見つける。名前の付け方に左右されない）、
他 MOD のエンティティのクラス自身のフィールド、Capability、永続データ（NBT）、同期データ。

**抹殺予定の一覧（`KillLists` / `AttackerListCleanup`）**: 即死 MOD は消した個体を static な一覧に載せて後から攻撃し直す・再登録を拒否する。
読み込み済み・初期化済みの他 MOD のクラスだけを覗いて一覧を見つけ、保護対象を毎 Tick 外す。攻撃を止めたときは、攻撃したクラスの一覧からも外す（同じクラス・同じ対象は 1 Tick に 1 回まで。同じ Tick に同じクラスが別の保護対象を攻撃しても、そちらも外す）。

**実際の値（`DirectRead`）**: 生死の判定はすべて保存場所から直接読む。

**真ライフ（LifeGuard エージェント、`-PwithLifeGuard`、2026-10-03）**: 他 MOD のエージェントが getHealth / isDeadOrDying / isAlive の戻り値を包んで偽装すると
（Fantasy Ending は全 LivingEntity を再変換して包む）、vanilla がそれを信じて死亡演出を回し続ける。LifeGuard は vanilla の `LivingEntity` とこのMOD のクラスの
各 return の直前に `LifeHooks`（ブートストラップ）を呼ばせ、MOD 側の `TrueLife` が保護対象だけ実際の値に直す（直したら `repair:true_life:<getter>` を記録）。
変換器は足した順に回り後の方が外側になるので、他のエージェントの再変換を見たら自分の変換器を外して足し直し（最後へ）、対象を再変換する。
ワールドの開始時にも並べ直す。他 MOD のクラスは書き換えない。

**ダメージ処理を通らない HP の減少**（同期データの直書き等）は、毎 Tick の点検で普通の被弾と同じ上限（`capFinalDamage`、最大 HP の 10% / 200 Tick）に掛け、
超えた分を戻す（`repair:health_cut`）。強化保護中（ブラックリストの MOD に攻撃された直後）は全部戻す。2026-10-03 までは HP 0 にならない直書きの削りは素通りだった。

**強化保護・ブラックリストとダメージ（2026-10-03 にユーザー指定で変更）**: 強化保護中・ブラックリストの MOD からでも、止めるのは即死系の攻撃だけ
（`EprotectForgeEvents.isLethalAttack`: 即死・有害なダメージの種類、即死アイテム、NaN・無限大・1000 以上の量。イベント 2 つと `hurt` / `actuallyHurt` の入口の 4 か所）。
通常攻撃は通して被弾の上限に掛ける（ブラックリストの MOD は 1%、他は 10%）。以前は強化保護中は誰のダメージも 0 にし、毎 Tick HP を満タンに戻していた。
凶悪技術の罰則として、止めた即死系の攻撃 1 回ごとに HP を最大まで戻す（`Eprotect.penalizeMaliciousAttack`、`repair:malicious_penalty:max_health`）。
自動のブラックリスト入りは「凶悪技術を感知しました」と表示する（チャット・ログ）。

**見せ直し（2026-10-03）**: 消去を拒否した・入り直させた次の Tick に、追跡（ChunkMap の TrackedEntity）を作り直して出現の情報を送り直す
（`repair:viewers_resync`、同じ個体は 20 Tick に 1 回まで）。消去と一緒にクライアントへ「消した」と送る MOD（Fantasy Ending の DeleteEntityPacket）で
画面から消えたまま・追跡の後片付け（stopSeenByPlayer）でボスバーが外れたままになるのを防ぐ。

**時間停止の包みの入口（2026-10-03）**: 代わりに回す Tick（`driveTick`）は `guardEntityTick` を通すが、その入口で打ち切る MOD
（EndingLibrary の `LevelMixin.guardEntityTick`）があるので、進まなければ包みを飛ばして `tickNonPassenger` を直接呼ぶ（`repair:frozen_tick:entrance_bypassed`）。
クライアントでも同じく、保護対象の Tick が進まなければ `ClientLevel.tickNonPassenger` で代わりに回す（`client/ClientTickBypass`。止まると補間・演出が崩れる）。

**vanilla の見た目・AI の状態（2026-10-03）**: 保護を始めたときの透明・AI 停止・重力無しを覚え、他 MOD に書き換えられたら戻す
（`repair:vanilla:invisible` / `no_ai` / `no_gravity`。Fantasy Ending の DeleteCommand は消去と一緒に setInvisible(true)、クリエイティブの骨は AI を止める）。
透明化の状態異常が付いている間の透明は戻さない。見張るかはフラグ辞書で決める（名前 `invisible` は CONCEAL、`noAi`・`noGravity` は CONTROL。
設定 `flagDictionary.ignore` に名前を足せばその状態は見張らない）。

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

### 3.12a プレイヤーの保護（守護の護符、2026-10-04 ユーザー指定で作り直し）

守護の護符（`ward_charm`、`protect/PlayerWard`・`mixin/PlayerWardMixin`・`PlayerWardEntityMixin`・`PlayerWardLivingMixin`）を持ち物に持つプレイヤーは、他 MOD による強制テレポート・ワールド外への飛ばし・次元移動と、
即死の経路（/kill 型のダメージ・フラグ辞書で即死と分かる種類・die() の直接呼び出し・被弾以外での HP 0 化・生きているうちの消去）を拒否する。通常のダメージでは死ぬ（上限はかけない）。
vanilla の移動（エンダーパール・/tp・ポータル・リスポーン）は通す。死亡しても護符は手元に残る。
前の護符（2026-10-02 に、エレノア戦・ECA と The Last Sword などとの干渉で廃止）にあった、リスポーン後 10 秒の保護・即死 MOD ごとの後始末（魂ダメージの打ち消しを含む）・クライアント側の HP の扱いは入れず、
保護の中心の Mixin（`EprotectDamageMixin` 等）にも手を入れない（別の Mixin だけで見る）。

### 3.12 プレイヤーのクリーンアップ（PlayerRespawnHandler）

プレイヤー保護システム（護符・リスポーン保護）は、エレノア等の戦闘処理との干渉による不具合防止のため廃止された。
代わりに `PlayerRespawnHandler` により、リスポーン時や手放し時に The Last Sword や Epic Core API による位置固定・移動不能バグのクリーンアップのみを実行する。

## 4. 設定（`config/EprotectConfig`、`automata_valkyries-common.toml`）

| キー | 既定 | 内容 |
|---|---|---|
| `protection.rejectHealthLossOutsideDamage` | true | 被弾以外での HP 減少を拒否 |
| `protection.retreatWhenUnrepairable` | true | 修復が追いつかなければ退避 |
| `protection.retreatAnomalyThreshold` | 200 | 退避までの修復件数 |
| `protection.retreatWindowTicks` | 1200 | その時間窓 |
| `protection.expandedScope.enabled` | false | 保護範囲拡張のマスター設定（既定は範囲強化なしで単体保護のみ） |
| `protection.expandedScope.protectVehiclesAndPassengers` | false | 搭乗物・乗客の連動保護（ボートやトロッコ等） |
| `protection.expandedScope.reinforcedAura.enabled` | false | 強化オーラによる範囲内味方MOB/エンティティ保護 |
| `protection.expandedScope.reinforcedAura.radius` | 16.0 | 強化オーラの防護半径（1.0〜128.0ブロック） |
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
| 保護範囲設定 | `/eprotect scope`、`/eprotect scope on/off`、`/eprotect scope vehicles <on/off>`、`/eprotect scope aura <on/off>`、`/eprotect scope radius <distance>` |
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

### 8.1 注意事項: Java エージェント・深いフックを持つ MOD との干渉（2026-10-02 時点）

現状、Java エージェントのクラスを持つ MOD や、vanilla の深い所（同期データ・HP の読み書き・Tick）に処理を差し込む MOD とは、
保護同士・保護と即死がぶつかって**干渉することがある**。特に次の組み合わせで実際に起きている:

| MOD | 起きたこと |
|---|---|
| ECA（Epic Core API）＋ The Last Sword You Never Forgot | The Last Sword が持ち主の HP を ECA の `setHealth` で毎秒 20 回ほど下げようとし続けるなど、プレイヤー保護との激しい干渉が発生したため、プレイヤー保護システムは廃止された。ログイン直後・リスポーン直後の再死亡ループ（2026-10-02 解決）の**原因はこのMOD自身の後始末**だった: `TerminusPlayerExecutor` が毎 Tick、ラストソードを持っていないプレイヤーに `EcaLastSwordHelper.cleanupPlayerRestraints` を呼び、その手順 5 の The Last Sword の `EntityUtil.clearDefence` が「世界の錨（`tlsWorldAnchor`）の HP」を ECA の `setHealth` で書き戻す。錨の無いプレイヤーでは錨が 0 なので、HP が 0 に直接書かれ（同期データの入口を通らない）、ログイン直後の最初の Tick とリスポーン直後に必ず死んでいた。クライアントは HP 0 を受けて死亡画面を開くが、サーバーの死亡処理は走らないので死亡メッセージも出ない。錨が正のときだけ `clearDefence` を呼ぶように直した（調査は HP を 0 にした区間を Tick の区切りごとに記録して特定）。再発防止に、拘束の後始末は必要なときだけにした: 毎 Tick ではなく防御剣を手放した瞬間・死亡・リスポーン・ログインの時だけ。ECA の位置固定の問い合わせの中では位置固定だけを外す。飛行は The Last Sword の飛行の印があるときだけ外す（他 MOD の飛行を消さない）。あわせて、EcaMixins のプレイヤーの無条件除外をやめ、AllReturn の解除を対象プレイヤーだけにし、Hyperlink の DATA_NOVELIZED を Entity クラスから解除するようにした |
| Hyperlink | ノベル化の判定（同期データの読み出しに差し込まれる）から互換 Mixin を経て保護の判定に戻る再帰や、リスポーン後の小説化残存問題。兵姫保護に絞り込み、NPE対策とリスポーン時クリーンアップで対応 |
| Omni-Mobs ＋ Hyperlink | ボスの描画に Hyperlink の判定が差し込まれ、消えたボスの描画で null を渡してクライアントが落ちた |
| Omni-Mobs ＋ Witherzilla ＋ ECA | 処刑竜を遠方へ飛ばす処理と ECA のチャンク生成の拒否が重なり、サーバーが落ちた |

- 互換 Mixin と Java エージェント（Eprotect+）で個別に手当てしているが、組み合わせが増えるほど干渉の余地が残る。
- 検証は組み合わせを絞って行う（例: ECA を外す、Witherzilla と Omni-Mobs を別々に読み込む）。
- 干渉を調べるときは観察用 Java エージェント（`-PwithObserver`）の能力の地図・書き換えの記録と、`/eprotect attacks` を手がかりにする。
