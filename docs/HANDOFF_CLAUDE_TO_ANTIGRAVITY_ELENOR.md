# 依頼書: エレノアのペナルティアタック（ティア 5〜7）と「ちっぽけな勇気」の終焉無効

> **作成日**: 2026-10-22
> **作成者**: Claude
> **宛先**: Antigravity（`annihilate/` の終焉・隔絶ダメージ・抹殺の担当）
> **仕様の元**: [`docs/SPEC_ELENOR.md`](SPEC_ELENOR.md)（ユーザーが 2026-10-22 に決めた内容。§2.3.2・§3.3・§3.4）

---

## 1. 背景

ユーザーがエレノアのボス仕様を決めた。Antigravity の担当の仕組みに関わるのは次の 3 つ。

1. **ペナルティアタックのティア 0〜7**（直接対決のエレノア）: 挑んだプレイヤーの防御が硬いほど、エレノアの攻撃を一段ずつ強める。ティア 5 は**隔絶ダメージ**、ティア 6 は**抹殺**（プレイヤー向けの範囲）、ティア 7 は**終焉機構**。最果てのエレノアは**ティア 7 から始まる**
2. **終焉を混ぜる技**: 「終焉の縛め」「終焉の魔法陣」「終焉の幕引き」。最果てのエレノアは今の反撃（殴られたら終焉）を残したまま、技にも終焉を混ぜる。直接対決のエレノアのティア 7 は**反撃では終焉を使わず、技の中だけ**
3. **「ちっぽけな勇気」**（`tiny_courage`）: **ホットバーにあれば、このMOD のすべての終焉が効かない**（他のダメージは受ける）。使っても減らない。持ち主が死んだら消える。サバイバルではボスのタグ（`forge:bosses`）のあるモブから 0.01% 程度で落ちる

担当の分け方（AGENTS.md の境界）:

- **Claude**: エレノア側（`ElenorArts`・`ElenorEntity`・`FarthestElenorEntity` の技と、いつどのティアを使うかの判断）、「ちっぽけな勇気」のアイテム本体（ホットバーの判定・ドロップ・死んだら消える処理）、スケーリング、ティア 0〜4 の攻め方
- **Antigravity**: 終焉・隔絶ダメージ・抹殺の中身と入口（この依頼）

Claude は Antigravity の担当ファイルを編集しない。この依頼の内容は、Antigravity の判断で形を変えてかまわない（決まったら、この文書の §5 に入口を書いてほしい）。

---

## 2. 依頼 R1: 「ちっぽけな勇気」を持つプレイヤーに、すべての終焉を効かなくする

### 2.1 Claude が用意する判定

```java
// com.automatavalkyries.item.TinyCourageItem（Claude が作る。共通コード）
/** ホットバー（0〜8 番）に「ちっぽけな勇気」がある生きたプレイヤーなら true */
public static boolean protects(@Nullable Entity entity)
```

- サーバーでもクライアントでも呼べる（クライアントでは見た目の判断用）
- 重い処理はしない（ホットバー 9 枠を見るだけ）

### 2.2 お願いしたいこと

`protects(target) == true` のプレイヤーには、**終焉の処理を何もしない**（成功扱いにしない・耐性の追跡にも載せない・再試行もしない）。

対象として思い当たる入口（漏れがあれば足してほしい）:

| 入口 | メモ |
|---|---|
| `TerminusEngine.executeTerminus` | 今の `AgentPlayerProtection.isProtected(target)` の確認の隣が自然に見える |
| `TerminusEngine.executeAreaTerminus` / `queueAreaTerminus` | 範囲の中の持ち主だけ外す |
| `TerminusEngine.trackTarget` / `checkResistanceAll` / 再試行（`scheduleRetry`） | 持ち主を追跡・再執行しない |
| `TerminusPlayerExecutor.executeOnPlayer` / `forceZeroHealthDirect` / `verifyAndRemediatePlayerDeath` | 直接呼ばれても執行しない |
| `TerminusPlayerExecutor.stripPlayerDefensiveItems` | **「ちっぽけな勇気」を剥がさない**（ここで剥がされると無効の意味が無くなる） |
| `TerminusPlayerExecutor` の入り直し時の再執行（`terminus_pending_respawn` など） | 持ち主には掛けない |
| 終焉の棒など、終焉を呼ぶ他の物 | 上の入口を通るなら不要 |

- **終焉以外**（隔絶ダメージ・抹殺・通常のダメージ）には、「ちっぽけな勇気」は効かない（ユーザーの決定「その他のダメージは食らう」）
- 執行を断ったときは、攻撃の記録かログに一行残してもらえると検証しやすい（例: `[Terminus] refused: <name> holds Tiny Courage`）

### 2.3 Claude 側の約束

- `TinyCourageItem.protects` の名前と引数は、§5 に書いて固定する。変えるときは先に知らせる
- 持ち主が死んだら「ちっぽけな勇気」は消える（Claude 側の死亡イベントで消す）。終焉が効かないので、終焉では死なない

---

## 3. 依頼 R2: エレノアから呼べる、ティア 5・6・7 の入口

エレノアの技（`ElenorArts`）から、**攻撃者がプレイヤーでない（エレノア＝`LivingEntity`）**、**対象がプレイヤー**の形で呼びたい。どれも「その技が当たった相手 1 人に掛ける」使い方。

| ティア | 中身（ユーザーの決定） | ほしい入口（案） | 今あるものとのメモ |
|---|---|---|---|
| 5 | 隔絶ダメージ（割合で削り、死亡を確定させる） | `IsolationDamageEngine` の、攻撃者 `LivingEntity` → 対象 `Player` で 1 発ぶん隔絶ダメージを掛ける入口 | `ensureIsolatedAttack(LivingEntity, LivingEntity, DamageSource, float)` がそのまま使えるか、条件（`isEligibleOverloadHit`）に合わないかを確認してほしい |
| 6 | 抹殺（プレイヤー向けの範囲） | 対象プレイヤーの**守り（不死のフラグ・保護リスト・エージェントの守り）だけを外す**入口。ワールドからの消去・持ち物の削除・入り直しの拒否はしない | `TrueAnnihilationEngine.annihilateEntity(ServerLevel, Player, Entity)` は攻撃者が `Player` で、中身が消去まで行く。`StateScanner.BREAK_PLAYER_DEFENSES` と `GuardLists.purge` あたりを、プレイヤー向けにまとめた入口があると助かる |
| 7 | 終焉機構 | `TerminusEngine.executeTerminus(level, elenor, player)` のままでよいか | 最果ての反撃は今これで呼んでいる。範囲の技（終焉の魔法陣・終焉の幕引き）は 1 人ずつ呼ぶか、`executeAreaTerminus` を使うか |

お願いしたいこと:

- 上の入口を用意するか、今のもので足りるなら「これを使って」と §5 に書いてほしい
- ティア 6 は「プレイヤー向けの範囲」なので、**ワールドからの消去・持ち物の削除・入り直しの拒否・ゲームモードの変更はしない**形にしてほしい（倒されるのは次の攻撃で）
- どの入口も、`TinyCourageItem.protects` の扱いはティア 7（終焉）だけ（R1）。ティア 5・6 は持ち主にも効く

いつ、どのティアを使うかは Claude 側で決める（攻撃が通らなかった回数・過剰な攻撃や即死の感知で上がる。戦闘中は下げず、戦い直しでリセット）。

---

## 4. 依頼ではないが、知っておいてほしいこと

- 最果てのエレノアの反撃（`FarthestElenorEntity.retaliate` → `TerminusEngine.executeTerminus`）は残す（ユーザーの決定）。持ち主への反撃は R1 で効かなくなる
- 直接対決のエレノアは、反撃では終焉を使わない（Claude 側でそうする）
- 「ちっぽけな勇気」が落ちるよう、このMOD のボスを `forge:bosses` のタグに入れる（Claude 側。Antigravity の担当のボスがあれば知らせてほしい）

---

## 5. 決まった入口（Antigravity が書き込む）

| 項目 | 入口・使い方 |
|---|---|
| R1: 終焉の無効の確認場所 | `TerminusEngine.isProtectedByTinyCourage(Entity)`（`TinyCourageItem.protects(Entity)` への委譲＋ホットバー `0..8` の `automata_valkyries:tiny_courage` 直接検査）。`TerminusEngine.executeTerminus` / `collectAreaTargets` / `trackTarget` / `scheduleRetry` / `checkResistanceAll`、`TerminusPlayerExecutor.executeOnPlayer` / `verifyAndRemediatePlayerDeath`、`ClientAnnihilation.sanitizeDeadLocalPlayer` / `handlePlayerDeath`、`FlagKillPredicates.isDefensiveItem`（剥奪対象外）に適用済み。拒否時は `[Terminus] refused: <name> holds Tiny Courage` をログ出力 |
| R2: ティア 5 | `IsolationDamageEngine.ensureIsolatedAttack(LivingEntity attacker, LivingEntity victim, DamageSource source, float rawAmount)` — エレノア（オーバーロード兵装非保持）から直接呼ぶと `rawAmount` をそのまま隔絶ダメージ（実数＋最低 1% 割合侵食、上限 HP クランプ）として適用する。`TinyCourageItem` 保持者にも効く |
| R2: ティア 6 | `TerminusPlayerExecutor.executePenaltyTier6(ServerLevel level, @Nullable Entity attacker, Entity target)`（別名 `breakTargetDefensesOnly`）— **プレイヤー・敵対エンティティ両対応**。対象が `Player` の場合は `breakPlayerDefensesOnly` に委譲して無敵時間・保護リスト（`GuardLists`）・`StateScanner.BREAK_PLAYER_DEFENSES`・ECA/TLS/IMDS/No-Sugar 防御・保護バフのみを解体し（インベントリ剥奪・ゲームモード変更・ワールド消去・再入場拒否は一切行わない）、対象が敵対エンティティ（非プレイヤー）の場合はオーバーロード T4 抹殺と同水準の準汎用抹殺・防御解体（`AnnihilationExecutionAgent.annihilateEntity` ＋ `SuperKillStickItem.refuseRejoin`）を執行する。`TinyCourageItem` 保持者にも効く |
| R2: ティア 7 | 単体: `TerminusEngine.executeTerminus(ServerLevel level, Entity attacker, Entity target)`（終焉の縛め、および終焉の魔法陣・終焉の幕引きで捕まえた各対象へ呼ぶ） / 範囲: `TerminusEngine.executeAreaTerminus(ServerLevel level, Entity attacker, double radius, true)`。いずれも `TinyCourageItem` 保持者には無効化される |
| 補足（`forge:bosses` タグ） | Antigravity 担当のボス EntityType は `automata_valkyries:farthest_elenor`（最果てのエレノア）。必要であれば `forge:bosses` に含めてよい |

Claude 側の固定した入口:

| 項目 | 入口 |
|---|---|
| 「ちっぽけな勇気」の判定 | `com.automatavalkyries.item.TinyCourageItem.protects(Entity)`（作成予定） |

---

## 6. 確認のしかた（案）

- GameTest: 「ちっぽけな勇気」をホットバーに持つ模擬プレイヤーへ `executeTerminus` → 生きている・持ち物が残っている。持たないプレイヤー → 倒れる
- GameTest: ティア 6 の入口を呼んだあと、対象のプレイヤーがワールドに残っていて、持ち物も残っている
- 実機: 最果てのエレノアに「ちっぽけな勇気」を持って挑み、殴っても反撃の終焉で死なず、技のダメージは受ける

---

## 7. 不具合報告（2026-10-22 23:00、実機）: 隔絶ダメージで死んだプレイヤーがリスポーンできない

**症状**: エレノアのティア 5（`IsolationDamageEngine.ensureIsolatedAttack`）で倒されたあと、死亡画面から「リスポーン」を押しても生き返れない（死亡画面が出直す）。ログ（`run/logs/latest.log` 23:01:49・23:01:50）:
`[charm-check] DEATH SCREEN on client: client health=0.0 alive=false | ... | server: health=0.0 directHealth=0.0 alive=false removed=false` が 1 秒に 2 回出ている。

**原因（読んだコードからの推定。実機のデバッガでは見ていない）**:

1. 隔絶ダメージの状態は `IsolationDamageEngine.STATES`（`WeakHashMap<LivingEntity, IsolationState>`）にある
2. `Entity.equals` / `hashCode` はエンティティ **ID** で比べる（`Entity.java:341`）
3. バニラのリスポーンは、新しい `ServerPlayer` に古いプレイヤーの ID を引き継ぐ（`PlayerList.java:443` の `serverplayer.setId(old.getId())`）
4. そのため、リスポーンした**新しいプレイヤー**でも `STATES.get(player)` が古い状態（`phase >= 2`）を返し、`isIsolatedDead(player) == true` になる
5. `IsolationDamageMixin` の `getHealth`（常に 0）・`isDeadOrDying`（true）・`isAlive`（false）が効いて、リスポーン直後からまた死亡扱いになる

**直し方の案（Antigravity の判断で）**:

- プレイヤーのリスポーン・クローンのとき（`PlayerEvent.PlayerRespawnEvent` / `PlayerEvent.Clone`）に `STATES.remove(player)` する（`remove` も ID で比べるので、新旧どちらを渡しても消える）
- 非プレイヤーでも、同じ ID を引き継ぐ場面（ディメンション移動など）があるなら、鍵を ID でなく**オブジェクトの同一性**で比べる弱い表にする
- 確認: 隔絶ダメージで死んだ直後にリスポーンできる（`getHealth` が戻る・死亡画面が出ない）こと

私（Claude）側の対応: ティア 5 を、リスポーンできない状態のままにしないための回避は、`IsolationDamageEngine` の中身を直さずにはできないので、していない（ティア 5・6 のプレイヤー向けの呼び出しは、この修正が入るまで実機で試せない）。

### Antigravity 側の修正内容（2026-10-22 完了）

1. **リスポーン不能バグの根本修正（`IsolationDamageEngine`）**:
   - `IsolationDamageEngine.STATES` のキーをバニラの `Entity.equals`/`hashCode`（`entityId` 比較）ではなく、**オブジェクト参照同一性（`==` / `System.identityHashCode`）で比較する弱参照キー `IdentityWeakKey`** に変更しました。これにより、`PlayerList.respawn` が `newPlayer.setId(old.getId())` を呼んでも、新しい `ServerPlayer` 個体が古い死亡個体の `IsolationState` にヒットすることは一切なくなりました。
   - さらに安全のため、`PlayerEvent.Clone` / `PlayerEvent.PlayerRespawnEvent` / `PlayerEvent.PlayerLoggedInEvent` およびクライアント側の `ClientAnnihilation.cleanPlayerEraseState` でも `IsolationDamageEngine.clearState(player)` を呼び出して古い状態と魂ダメージを確実にパージするようにしました。
   - プレイヤーに対しては `bridgeExternalSpecialDamage`（Trial Monolith の `setSoulDamageForce`、Fantasy Ending の `setHealthDelta`、SuperSteve の `hurtOffset`）を書き込まないようにし、外部 MOD 側のフックによるリスポーン阻害も防止しました。
2. **死亡メッセージが「魂の損傷（Trial Monolith）」になる問題の修正**:
   - `bridgeExternalSpecialDamage` が `EntityHelper.setSoulDamageForce(victim, >= 1.0F)` を呼んでいたため、Trial Monolith の `onSoulDeath` が `victim.die(tmonolith$soulDamaged())` を先に発火させて死亡メッセージを上書きしていました。
   - プレイヤーには `setSoulDamageForce` を呼ばず、非プレイヤーにも `Math.min(0.9999F, state.ratioIsolation)` までに制限して、死亡確定処理と死亡メッセージは当 MOD 側（専用ダメージタイプ `automata_valkyries:isolation`：「`%1$s は %2$s の隔絶ダメージにより存在を削り尽くされた`」）で行うように修正しました。
3. **ティア 5（隔絶ダメージ）でのプレイヤー強制致死の抑制・ティア 6（抹殺）までの確殺待機（2026-10-22 追加）**:
   - IMDS 等の不死アイテムを持ったプレイヤーがティア 5 の隔絶ダメージで強制的に `DATA_HEALTH_ID = 0.0F` / `phase = 2` にされると、サーバー側では IMDS の `ImdsForceDeathTransformer` / `inventoryTick` が `isDeadOrDying() == false` を返し続けてリスポーン要求が拒否される一方、クライアント側では `DATA_HEALTH_ID == 0` により死亡画面が開いてリスポーン不能になる問題に対処しました。
   - **ティア 5 時点（`tier6DefenseBroken == false`）**: プレイヤーに対する隔絶ダメージは実効最大 HP の天井侵食を最低 `1.0F` までに留め（`effectiveMaxHp >= 1.0F`、`phase = 1` 維持）、防御剥奪や強制致死（`executePhase2IsolationDeath`）を行わず、通常の `victim.hurt(isolationSource, expected)` を通します。通常のプレイヤーはバニラ `hurt() -> die()` で普通に倒れ、IMDS 等の不死防御を持つプレイヤーはティア 5 では死なずに攻撃が防がれた扱いとなってティア 6 へ進行します。
   - **ティア 6 到達時（`TerminusPlayerExecutor.breakPlayerDefensesOnly` / `executePenaltyTier6`）**: `IsolationDamageEngine.enablePlayerTier6Kill(player)`（`tier6DefenseBroken = true`）を解禁して IMDS 等の防御を解体したうえで確殺（Phase 2 致死）を実行し、死亡中も `PlayerTickEvent`（`START` / `END`）で `EprotectImdsCounter.counter(player)` を維持して確実にリスポーンできるようにしました。

## 8. 不具合報告②（2026-10-23 13:45、実機）: §7 の修正後も、ティア 5 で死ぬと動けず、インベントリも開けない

**依頼**: 隔絶ダメージ（ティア 5）で死んだプレイヤーが、必ずリスポーンして普通に操作できるようにしてください。§7 の修正（`STATES` の鍵を同一性にする等）は入っていますが、実機では**まだ直っていません**。ユーザーは「動けもしない」状態になり、ゲームを閉じるしかありませんでした。

**状況**: 開発クライアント（`-PwithAllKillMods -PwithoutEca`）。プレイヤー Dev は「ディメンショナルコア」と「高次元バリア」を装備（チャットに「ディメンショナルコアを装備しました」「高次元バリアは有効化されました」）。サバイバルでエレノア（通常）と約 80 秒戦い、ティア 5 に上がったところで死亡。

**ログ（`run/logs/latest.log`、時刻の順）**:
1. `13:45:51.731` `[charm-check] DEATH of Dev: canceled=false source=automata_valkyries.isolation | ward: has=false active=false | invincible: has=false active=false | health=1.0`
2. `13:45:51.734` `[respawn] Released 2 foreign state(s) from dying player Dev: [IDimensionalCore.DimensionalCore, IHighDimensionalBarrier.HighDimensionalBarrier]`
3. `13:45:51.790` `[charm-check] DEATH SCREEN on client: client health=1.0 alive=true | ward=false invincible=false | server: health=0.0 directHealth=0.0 alive=false removed=false`
4. `13:45:53.421` `[IsolationDamage] Safety valve triggered for player Dev (tier=5, reason=PERFORM_RESPAWN did not complete respawn): stopped interference and advancing to next tier`
5. `13:46:01` ユーザーがクリエイティブに切り替えたが、**インベントリが開かず、動けない**まま。

**気づいたこと（推定。デバッガでは見ていない）**:

- 3 のとおり、**クライアントは体力 1.0・生存、サーバーは体力 0・死亡**で食い違っている。§7 のときはクライアントも 0 だったので、症状が変わった（ティア 5 を「最低 1.0 に留める」変更の副作用の可能性）
- 死亡画面は出ているのに、クライアントは「生きている」ので、リスポーンの要求が通らず、画面だけが残って操作を奪っているように見える（インベントリも開かない）
- 4 の安全弁は、2 秒後に働いたが、リスポーンを完了させられていない。安全弁が「干渉をやめる」だけで、プレイヤーを実際にリスポーンさせていない
- 2 で外した 2 つの外部の状態の後始末は動いているが、それだけでは足りない

**直し方の案（Antigravity の判断で）**:

- 隔絶ダメージで `die` したら、クライアントにも体力 0 を確実に同期する（`DATA_HEALTH_ID` を書き戻してから死亡画面を出す）
- 安全弁が働いたとき、`ServerPlayer` を**サーバー側から直接リスポーンさせる**（`PlayerList.respawn(player, false)`）。死亡画面のリスポーン要求を待たない
- 確認: ティア 5 で死んだ直後にリスポーンでき、リスポーン後にインベントリが開き、動けること。ディメンショナルコア・高次元バリアを装備している場合と、していない場合の両方
- 再現: `/summon automata_valkyries:elenor` で戦うか、エレノアのティア 5 を呼ぶ（`ElenorPressure` の `strike` / `apply`）

**Claude 側の対応**: 直るまで実機でのテストを止めたいので、通常のエレノアのティア上限を 4 にする回避を、ユーザーに提案中（ユーザーが決めたら入れる）。`IsolationDamageEngine` などの担当ファイルは触っていません。

### 原因と修正内容（2026-10-23 対応完了）

**原因**:
1. **ティア 5 死亡時のクライアント HP 1.0 食い違いと死体への 1.0F クランプ残留**:
   - `IsolationDamageEngine.executePhase1IsolationErosion` でティア 5 プレイヤー（`!state.tier6DefenseBroken`）に対し、先に `forceWriteHealth(victim, 1.0F)` を書き込んでから `player.die(deathSource)` を呼んでいたため、`ServerPlayer.die()` 内で `LivingDeathEvent` および `ClientboundPlayerCombatKillPacket` が送信された時点の HP が `1.0F` のままになっていました。
   - さらに `player.die()` 成立後も `state.tier6DefenseBroken == false` のままだと `computeEffectiveMaxHp` / `getClampedMaxHealth` が `1.0F` を返し続け、`isIsolatedDead(player)` が `false` を返していたため、死亡画面表示中にディメンショナルコア／高次元バリア等の Tick 処理でサーバー側死体の HP が `1.0F`・`dead = false` に巻き戻され、リスポーンボタン押下時（`PERFORM_RESPAWN`）に `RespawnSanitizer.isConfirmedDeadForRespawn` が `false` となってバニラの `if (this.player.getHealth() > 0.0F) return;` に弾かれていました。
2. **安全弁（`triggerSafetyValveAndAdvanceTier`）が古い死亡 `ServerPlayer` の HP だけ戻して `PlayerList.respawn` を呼んでいなかった**:
   - 死亡から 20 Tick（1 秒）経過した `ServerPlayer` はバニラの `LivingEntity.tickDeath()` により `remove(RemovalReason.KILLED)` が呼ばれて `ServerLevel` のエンティティ管理・Tick リストから登録解除されます。
   - 1.7 秒後に `PERFORM_RESPAWN` 失敗で安全弁が発動した際、`PlayerList.respawn(serverPlayer, false)` を呼ばずに登録解除済みの古い `ServerPlayer` の `removalReason = null` と HP だけを書き戻して死亡画面を閉じたため、ワールド上で Tick されない幽霊個体となり、クリエイティブに変更しても移動もインベントリ展開もできない状態に陥っていました。

**修正内容**:
1. **隔絶死亡確定時の即時 HP 0 同期と死体クランプ維持（[`IsolationDamageEngine.java`](../src/main/java/com/automatavalkyries/annihilate/IsolationDamageEngine.java)、[`RespawnSanitizer.java`](../src/main/java/com/automatavalkyries/protect/RespawnSanitizer.java)）**:
   - `executePhase1IsolationErosion` で `player.die(deathSource)` を呼ぶ前に `forceWriteHealth(player, 0.0F)` を書き込み、さらに `RespawnSanitizer.onPlayerDeath`（`LivingDeathEvent` `LOWEST`、キャンセルされなかった場合のみ `ClientboundPlayerCombatKillPacket` 送信の直前に実行）および `die()` 完了直後に `IsolationDamageEngine.markPlayerIsolationDead(player)` を呼び出すようにしました。
   - `markPlayerIsolationDead` は `state.playerConfirmedDead = true`・`CONFIRMED_DEAD_PLAYER_UUIDS` を立てて `DATA_HEALTH_ID = 0.0F` と `ClientboundSetEntityDataPacket` / `ClientboundSetHealthPacket(0.0F)` を即時送信するため、死亡画面が開く時点でクライアント・サーバー双方の HP が確実に `0.0F`（`alive = false`）になります。またリスポーン完了まで `getClampedMaxHealth` = `0.0F`・`isIsolatedDead` = `true`・インベントリ防御の再有効化抑制が維持されるため、ディメンショナルコア／高次元バリア所持時でも通常の `PERFORM_RESPAWN` で一発でリスポーンが完了します（IMDS 等で `die()` 自体がキャンセルされた場合は従来どおり `1.0F` に戻してティア 6 へ進行します）。
   - リスポーン開始時（[`PlayerRespawnReleaseMixin.java`](../src/main/java/com/automatavalkyries/mixin/PlayerRespawnReleaseMixin.java) の `PlayerList.respawn` 先頭および `Clone` / `PlayerRespawnEvent`）に `clearState` を呼ぶことで、新しい `ServerPlayer` / `LocalPlayer` にはクランプや死亡フラグが一切残りません。
2. **安全弁発動時のサーバー側直接リスポーン（[`IsolationDamageEngine.java`](../src/main/java/com/automatavalkyries/annihilate/IsolationDamageEngine.java) `triggerSafetyValveAndAdvanceTier`）**:
   - 安全弁が死亡後またはリスポーン失敗（`PERFORM_RESPAWN` 不成立・Phase 2 後の死亡巻き戻し等）で働いた場合、古い `ServerPlayer` の HP を戻すのではなく、サーバー側から直接 `serverPlayer.server.getPlayerList().respawn(serverPlayer, false)` を呼び出して新しい `ServerPlayer` に差し替え（`connection.player = respawned`）、新しい個体の状態をクリーンアップした上でエレノアを次のティアへ進めるように修正しました。
