# フラグ辞書 第 2 版（改修の設計。2026-10-21）

2026-10-21 ユーザー指定「アンチグラビティーと合わせてフラグ辞書を改修していく」。質問で決めたこと:

- 狙い（4 つとも）: **辞書を 1 か所にまとめる**・**取り違え・見落としを減らす**・**見張りの候補を辞書に育てる流れ**・**分類を増やす・見直す**
- 分担: **辞書本体（中身・判定のしかた・守る側の使い方）は Claude、使う側は各自**。抹殺側（`KillLists`・`FlagDictionaryLink`・
  辞書のエージェント `FlagDictionaryAgent` / `FlagDictionaryProtector`・抹殺での使い方）は Antigravity。辞書の形式と受け渡しの約束（この書類 §3・§4）を先に決め、両方が合わせる

今の作りは [`SPEC_EPROTECT_ARCHITECTURE.md`](SPEC_EPROTECT_ARCHITECTURE.md) の「フラグ辞書」、見張りは同書と `protect/flag/FlagWatchAgent`。

## 1. 今の問題（2026-10-21 に読んで分かったこと）

1. **辞書が 2 か所にある**: `protect/flag/FlagDictionary` の `BUILT_IN` と、エージェントの `FlagDictionaryProtector.MASTER_DICTIONARY` に同じ単語の並びが写してある。
   片方だけ直すとずれ、エージェントの「点検・復元」がずれた方へ戻してしまう
2. **辞書のファイルに抹殺側の判定が混ざっている**: `isKillItem`・`isDefensiveItem`・`isDefensiveField`・`isDeathStateField`・`isTerminationMethod`・
   `isCoreOrControllerName`・`hasDangerousFlag`・`ensureAgentProtection` 等。担当の境界がぼやけ、片方の直しがもう片方を壊しうる
3. **取り違え（拾いすぎ）**: 前方一致の単語が別の意味の名前を拾う。例: `delta`（HP 改ざん）が vanilla の `deltaMovement` を、`resist`（守り）が
   `resistance` を、`sleep`（停止）が眠りを、`tick`（待ち時間）が `ticket` を、`count` が `counter`・`country` を、`tomb`・`grave`（死亡）が墓石の MOD を拾う
4. **見落とし**: 名前だけで決めるので、新しい言い回しは辞書に足すまで分からない。見張りの候補（`logs/automata_valkyries-flag-candidates.txt`）を
   辞書に足す道筋が手作業（設定 `flagDictionary.extra` に書く）しかない
5. **特定の MOD 名の決め打ちが判定の中にある**: `imds`・`hyperdaimc`・`nosugar`・`witherzilla` 等の文字列を、辞書の外のコードで直接見ている
6. **値の型を見ていない**: 真偽・数・時刻・UUID のどれかで意味が変わる（`dead`=true は死亡の印、`deadCount` は記録）のに、名前だけで分類している

## 2. 段取り

| 段 | 内容 | 担当 |
|---|---|---|
| 1 | 辞書を 1 つの JSON にまとめ、MOD は JSON から読む（判定は今のまま） | Claude（済: §3） |
| 1b | エージェントも同じ JSON を読み、`MASTER_DICTIONARY` の写しを消す。点検・復元は JSON の中身の要約（ハッシュ）で比べる | Antigravity |
| 2 | 抹殺側の判定を辞書のファイルから Antigravity のファイルへ移す（§4） | Antigravity が新しいファイルを作って呼び出しを移し、Claude が辞書から消す |
| 3 | 取り違え・見落としを減らす: 除外の項目、項目ごとの重み、値の型の手がかり、実際の名前を集めた確かめ用の一覧（§5） | Claude |
| 4 | 見張りの候補を育てる流れ: 候補の一覧・採用・却下のコマンドと、利用者の追加辞書のファイル（§6） | Claude（抹殺側で使う候補は Antigravity も足せる） |
| 5 | 分類の見直し（§7。足す分類はユーザーに確かめてから） | Claude が案、ユーザーが決める |

**段 1b が済むまでの注意**: エージェント（`FlagDictionaryProtector`）は MOD の辞書を自分の写し（`MASTER_DICTIONARY`）と比べ、違えば写しの方へ戻す。
エージェントを付けている間に JSON の中身だけを変えると、エージェントが元へ戻してしまう。段 1b の前に JSON の中身を変えるときは写しも同じに直す
（写しは Antigravity のファイルなので、Antigravity に頼む）。段 1 の時点では JSON の中身は写しと同じ（399 項目）。

## 3. 辞書の形式（段 1。両方が守る約束）

- 置き場所: **`src/main/resources/automata_valkyries/flag_dictionary.json`**（MOD の jar の `/automata_valkyries/flag_dictionary.json`）。
  資源パック・データパックでは上書きできない場所（守りの辞書を外から書き換えられないように）。エージェントの jar にはビルドで同じファイルを写す
- 形式:

```json
{
  "format": 2,
  "categories": {
    "KILL": { "description": "死亡・消去・抹消・呪いの印", "entries": ["dead", "=die", "*forcedead", "..."] },
    "CONTROL": { "...": "..." }
  }
}
```

- `categories` の名前は `FlagDictionary.Category` の名前（`KILL`・`CONTROL`・`HEALTH_TAMPER`・`GUARD`・`BYPASS`・`REVIVE`・`COUNT`・`TIMER`・`BANISH`・
  `RESPAWN_BLOCK`・`CONCEAL`）。知らない分類は読み飛ばして警告を出す（新しい分類を先に足した側が、古い側を壊さないように）
- 項目の書き方は今と同じ: `kill`（どれかの単語が kill で始まる）・`=die`（どれかの単語がちょうど die）・`*forcedead`（単語をつなげた名前に含まれる）。
  名前は単語に分けて照らす（camelCase・snake_case・Mixin の接頭辞 `modid$`・名前空間 `modid:`。先頭の is/get/set/has は落とす）
- 段 3 で足す欄（`exclude`・`weight`・`types`）は、知らない側が読み飛ばせる形で足す。`format` を上げるのは読み方を変えるときだけ
- 利用者の追加・除外は今の設定（`flagDictionary.extra`・`flagDictionary.ignore`）のまま。段 4 で追加辞書のファイルを足す

## 4. 抹殺側の判定を移す（段 2）

辞書のファイル（`FlagDictionary`）に残すのは、辞書そのもの（読み込み・単語分け・`classify`・`is`・`entries`・`generation`・`reloadFromConfig`）と
守る側の判定（`harmfulOnProtected`・`heldOnDeadPlayer`・`hazardOnRespawned`・`recordsData`・`isHarmfulDamageSource`・`isKillItem` の守る側の使い方）。

Antigravity のファイル（例: `annihilate/FlagKillPredicates.java`）へ移すもの: `protectiveOnTarget`・`livesCounter`・`isDefensiveField`・
`isDeathStateField`・`isTerminationMethod`・`isDefensiveItem`・`isCoreOrControllerName`・`hasDangerousFlag`・エージェントとの連携
（`ensureAgentProtection`・`isProtectedByAgent`・`getAgentStats`）。移す間は、辞書のファイルの古い名前から新しい方を呼ぶ形で残し、呼び出しが全部移ったら Claude が消す。
守る側でも使っているもの（`StateScanner` の `isDeathStateField`、`ElenorEntity` の `hasDangerousFlag`、`EprotectForgeEvents` の `isKillItem`）は、
移すときに Claude に知らせる（守る側の呼び出しは Claude が直す）。

## 5. 取り違え・見落としを減らす（段 3。案）

- **除外の項目** `exclude`: 分類ごとに「この名前は当てない」（`deltamovement`・`resistance`・`sleeping`・`ticket`・`counter` 等）。
  拾いすぎは除外で消し、単語の前方一致そのものは残す（見落としを増やさない）
- **重み**: 項目ごとに確かさ（強い言い回し `*forcedead` は 1.0、短い前方一致 `soul`・`tomb` は 0.4 など）。分類は重みの合計が閾値を超えたときだけ当てる。
  守る側（戻す）は低めの閾値、抹殺側（外す・付ける）は高めの閾値を選べるようにする（取り違えたときの害が違うので）
- **値の型の手がかり**: 項目に「真偽のとき」「数のとき」を付ける（`dead` は真偽なら死亡の印、数なら記録）。判定の呼び出しに型を渡せる形を足す
- **確かめ用の一覧**: 実際の MOD（`libs/` と `-PwithXxx` の即死系 MOD、ボス MOD）から集めた名前に、正しい分類を付けた一覧
  （`src/test` か GameTest で読む）。辞書を直すたびに、拾いすぎ・見落としが増えていないか数で見る
- **決め打ちの MOD 名**: 判定のコードの中の文字列を、辞書の JSON の別の欄（`knownMods`）に移す（段 2 の後。抹殺側の分は Antigravity）

## 6. 見張りの候補を辞書に育てる（段 4。案）

- 見張りのエージェントの候補（`logs/automata_valkyries-flag-candidates.txt`）を読み、`/eprotect flags candidates` で一覧（名前・出た回数・どの MOD・
  変わった直前の出来事）、`/eprotect flags accept <名前> <分類>`・`reject <名前>` で決める
- 採った項目は利用者の追加辞書 `config/automata_valkyries-flags.json`（形式は §3 と同じ）に書き、組み込みの辞書と合わせて読む。
  却下した名前は同じファイルの `rejected` に残し、次から候補に出さない
- 組み込みの辞書に入れるべきもの（他の環境でも当たるもの）は、Claude が `flag_dictionary.json` に移す

## 7. 分類の見直し（段 5。案。ユーザーに確かめてから）

- 足す候補: **DAMAGE_SCALE**（受けるダメージの倍率・無効化の係数）、**HURT_TIME**（無敵時間の改ざん）、**IDENTITY**（所有者・UUID のすり替え）
- 分ける候補: KILL を「死亡の印（状態）」と「消す操作（メソッド）」に分ける（今は `isDeathStateField`・`isTerminationMethod` が単語を直接見て補っている）

## 8. 確かめ方

- `./gradlew build`・`./gradlew runGameTestServer`（`FlagDictionaryGameTests`・`GenericProtectGameTests` ほか）
- 段 1 は判定を変えないので、JSON にする前と後で同じ名前が同じ分類になること（`FlagDictionaryGameTests` に、組み込みの全項目が読めたことと代表の名前の分類を足す）

## 9. 進み具合（2026-10-21）

- **段 1 済み（未コミット）**: 組み込みの辞書を `flag_dictionary.json` へ。`FlagDictionary.loadBuiltIn()`・`builtIn()`・`RESOURCE`。テスト `builtInDictionaryLoadsFromJson`
- **段 5 の測る土台 済み（未コミット。中身を変えないので 1b を待たない）**: 守る側の確かめ用の名前 `src/main/resources/automata_valkyries/gametest/flag_cases.json`
  （reset=戻すべき・keep=戻してはいけない）と、テスト `defensiveCasesDoNotRegress`（取りこぼし・拾いすぎが baseline を超えたら失敗）。
  今の基準値: 取りこぼし 1・拾いすぎ 12。辞書を良くしたら baseline を下げる
  - 今の取りこぼし（1）: `chronoLocked`（単語 chrono+locked。項目 `chronolock` は前方一致で、つなげた名前には当たらない）
  - 今の拾いすぎ（12）: `deltaMovement`（delta）・`sleepingPos`（sleep）・`graveStoneId`/`soulboundItems`（grave・soul）・`deathCount`/`lastDeathPos`（death、記録）・
    `removalReason`（remov、vanilla）・`frozenTicks`（frozen）・`vanishingCurse`（vanish・curse、エンチャント）・`rootDirectory`（=root）・`bindingSlot`（bind）・`removeTimer`（remov、タイマー）
  - これらは段 1/段 3 で減らす（除外の項目・重み・値の型）。語彙を変えるので **段 1b の後**に入れる。それまでは測るだけ
  - 補足: `StateScanner` は他 MOD が足したフィールドだけを見る（vanilla の `deltaMovement`・`removalReason`・`frozenTicks` は走査しない）ので、
    これらの拾いすぎが今すぐ害になるわけではない。名前が他 MOD のフィールドと重なったときの取り違えを減らすのが狙い
- **守る側の「覚えた値へ戻す」 済み（未コミット。1b を待たない）**: `protect/ForeignStateBaseline`。辞書に無い名前の外部スカラー状態を正常時に
  読み取り専用の `StateScanner.observe` で控え、死亡の兆候が出た Tick にだけ、死亡の兆候に合う変化（真偽 false→true、数値 正→0 以下）を控えた値へ戻す。
  `Eprotect.repair` から呼ぶ（`isUnderDeathAttempt`・`refreshForeignBaselineIfDue`）。設定 `protection.restoreUnknownForeignState`。
  テスト `genericProtectRestoresUnknownForeignStateAtDeath`。2026-10-21 ユーザー指定「終末機構を汎用処理の限界付近まで強化したので Eprotect の強化も」の対。
  既知名は従来どおり `ForeignStateCleaner`。詳しくは `SPEC_EPROTECT_ARCHITECTURE.md` §3.6
- **照合の連結前方一致 済み（未コミット。1b 非依存＝照合ロジックのみ、JSON 内容を変えない）**: `matches()` に、6 文字以上の plain 項目は `compact.startsWith(entry)` でも当てる処理を足した。
  単語をまたぐ語（`chronolock`→`chronoLocked` = chrono+locked）を拾う。短い項目での偶然のまたぎ誤爆を避けるため 6 文字以上に限る。
  計測の baseline を **missedReset 1→0** に締めた（`chronoLocked` の取りこぼしが解消。拾いすぎは 12 のまま＝精度の回帰なし）。GameTest `defensiveCasesDoNotRegress` で確認
- **語彙の調査（30 MOD＋見本 1）済み**: `libs/`＋`-PwithXxx` のチート/即死/ボス系 30 MOD（IMDS・The Last Sword・Shizuku・Hyperlink・No-Sugar・Omni-Mobs・Witherzilla・
  more_avaritia・Fantasy Ending・ManaitaPlus/MTK・Trial Monolith 等。中華の神仙/秒殺系も含む）と、見本の god 級即死 MOD（modrinth `omnisicientssword`）を
  定数プール採掘して現行辞書で分類（ツール: scratchpad の `mine_vocab.py`）。**結論: 語彙はほぼ出揃っている**（IMDS は取りこぼし 0、他も良性語のみ）。
  即死は vanilla メソッド呼び（die/kill/remove/setHealth、入口 Mixin＋既存語で受け）か既知の名前で、新語はごく僅か（例 epic-core-api `lockMaxHealth`→HEALTH_TAMPER）。
  **効くのは語彙拡充より精度**（過度に広い項目: `execute`・`collapse`・`extinguish`・`displac`・`root`・`hidden`(geckolib ボーン)・`annihil`(ae2 ブロック)・`remov`・`delta`）
- **次（1b を待たずに進める）**: 段 4（候補の流れ）、`words()` の短い接頭辞落とし、corpus を実名でハードニング（調査で得た良性名を keep[] へ）
- **次（1b 調整が要る＝辞書内容）**: 精度の段 3（上の過度に広い項目を `exclude`・重みで絞る）、`lockMaxHealth` 等の僅かな追加

## 10. 見張りの点数付けの改善（2026-10-21。守る側・未コミット。StateScanner の探す範囲は触らない）

`FlagWatchAgent`（死亡・消去・保護対象への攻撃の直前に変わった、辞書に無い名前を候補に出す読むだけの見張り）の点数付けを賢くした。探す場所
（`StateScanner`）は Antigravity の範囲なので触らず、集めた観測の点数と絞りだけを変えた。

- **向きのそろい**（新）: いつも同じ向き（死亡の印 false→true、HP が削られて下がる等）なら 1、両方向に揺れる雑音（移動・向き）なら 0.5。
  下限 `MIN_CONSISTENCY = 0.6` 未満は候補から外す。これで、事件の直前にたまたま動いただけの値が落ちる
- **少ない観測を過大評価しない**: 事件での変化率を Laplace で均す（`(changes+1)/(seen+2)`）。数回しか見ていない名前が満点にならない
- **事件の種類の広がり**: 死亡だけでなく消去・攻撃にも出る名前を少し重く（×1.0〜1.6）。1 種類の事件に偏った名前より確からしい
- **値の型**: 真偽の印（on/off）は死亡・停止・守りの印として確かなので重く（×1.25）。足すときの推測（`suggestion`）も型で分ける
  （真偽が立つ→kill、下りる→guard、数が下がる→health_tamper、上がる→kill）
- 候補の記録（`Candidate`）に向きのそろいと真偽かを足し、ログに出す。テスト `flagDictionaryWatchSuggests` に向きのそろい・型の確認を足した
- 効果: 探す範囲を Antigravity が広げても、増えた観測の中から本当に効く名前が上位に出る。設計書 §6 の候補→採用の流れにそのまま乗る
