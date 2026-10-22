# Eprotect 防御の計測（他の即死 MOD を読み込んだ GameTest、2026-10-22）

Eprotect の防御が、他の即死・干渉系 MOD を読み込んだ状態でどれだけ保たれるかを、このMOD の GameTest で測った記録。
**自己採点であり、他の MOD の保護との独立の比較ではない**（テストはこのMOD が書いたもので、MOD 側は実物の jar を読み込んでいる）。

## 1. 方法

- `./gradlew runGameTestServer -P<フラグ>`（全 397 件）を、MOD の組ごとに、通常と `-PgenericOnly`（MOD ごとの個別対策＝互換 Mixin・KillModCounters・ボス固有の無力化を切り、フラグ辞書などの準汎用の層だけにする）で 1 回ずつ走らせた。
- **防御のテスト集は 155 件**: `EprotectGameTests`（33）・`GenericProtectGameTests`（13）・`AttackAttributionGameTests`（23）・`FlagDictionaryGameTests`（17）・`KillModGameTests`（33）・`GeneralizationGameTests`（6）・`CheatModGameTests`（6）・`ObserverGameTests`（3）・`EprotectPlusGameTests`（5）・`PrivilegeAegisGameTests`（10）・`ProtectDummyZombieGameTests`（6）。
- **防御スコア** = (155 − 防御側の失敗数) ÷ 155。失敗した各テストの内容（どのクラスの何を確かめるか）で、防御側・即死側・無関係に分けた。
- 計測日: 2026-10-22。コミットは `c5a081b` の時点（この文書より前）。生の結果は `bench.txt`（作業用）。

## 2. 結果

基準（MOD なし）は、397 件中 2 件の失敗（下の「無関係」）で、防御側の失敗は 0（100%）。

| 読み込んだ MOD の組（フラグ） | 個別対策あり | 準汎用のみ（`-PgenericOnly`） |
|---|---|---|
| `-PwithKillMods` | 99.4%（防御側 1 件失敗） | 99.4%（1 件） |
| `-PwithKillMods2` | 100% | 100% |
| `-PwithEca` | 100% | 100% |
| `-PwithTrialMonolith` | 98.1%（3 件） | 98.1%（3 件） |
| `-PwithCheatMods` | 100% | 100% |
| `-PwithSlashBlade` | 100% | 100% |
| `-PwithTechMods` | 99.4%（1 件） | 100% |
| **合計（7 組 × 155 件 = 1085 件）** | **99.5%**（失敗 5 件） | **99.6%**（失敗 4 件） |

個別対策あり・なしでほぼ同じ点なので、**準汎用の層だけで守れている**。

### 防御側の失敗（5 件の内訳）

| テスト | クラス | 失敗した組 |
|---|---|---|
| `protectedCleaningCoversEveryPlace` | `FlagDictionaryGameTests` | KillMods、Trial Monolith |
| `unrepairableStateTriggersSafeRetreat` | `EprotectGameTests` | Trial Monolith |
| `combatStalemateRetreats` | `EprotectGameTests` | Trial Monolith、TechMods（個別対策あり） |

退避のテスト 2 件は、以前から不安定（GameTest の Tick が速く、実時間と Tick 数が合わない。`gametest-flakiness`）。Trial Monolith の組での失敗が再現する弱点かは、同じ条件で複数回走らせて確かめること。

### 即死側のテストの失敗（Antigravity の担当・参考）

即死・抹殺の側が相手の守りに阻まれたことを示すテスト。防御スコアには入れていない。

- `ultraKillStickErasesTesseractBeast` / `ultraKillStickErasesInvaderMonolith`（`KillModGameTests`）: KillMods、Trial Monolith。
- `annihilationBreaksGuardsEverywhere`（`FlagDictionaryGameTests`）: KillMods、Trial Monolith。
- `quantumSlashPierces`（`WeaponGameTests`）: KillMods・Trial Monolith（準汎用のみ）。

### 無関係の失敗

- `valkyriethrusterairchain`（`ValkyrieCombatGameTests`）、`overloadt4annihilationdropslootandexp` / `overloadt4playertakesdamagenotannihilated`（`TrialGameTests`）。基準でも出るか、防御・即死のどちらとも関係が薄い。

## 3. 計測できなかった MOD

次は専用サーバーで起動できず、テストが走らなかった（クライアント専用のクラスを使う、または FML が先に落ちる）。ランチャーでの実機確認になる。

| MOD | 理由 | 実機での結果 |
|---|---|---|
| IMDS-Reborn、Last Sword、Witherzilla、Omni-Mobs | 起動のログに `invalid dist DEDICATED_SERVER` や FML のクラッシュ。テストが 1 件も走らない | 未確認（Omni-Mobs は `mixin/compat/OmniMobsStabilityMixins` で別に対処済み） |
| SuperSteve | 自分の jar を読み直す・隠しクラス（`-PwithSuperSteve`） | 耐えた（ルクシオン。`OpponentHoldings`＋手元の入れ直し。2026-10-22 に実機で確認） |
| Madness Entities | 開発環境では起動できない（`-PwithMadness`） | 耐えた（エレノア・ルクシオン。HP 0 書き込み・奈落ダメージ・`mob_slay` を拒否、ブラックリスト入り） |
| Fantasy Ending | 開発環境では起動できない | 未確認 |

`-PwithAllKillMods` は、専用サーバーでは起動できない MOD を含むため、一括では測れない（`ClassMetadataNotFoundException: net.minecraft.client.Minecraft`）。組に分けて測ること。

## 4. 読み取り方と限界

- テストは、このMOD の作者が「守れるべき状況」を書いたもの。実際の MOD の新しい攻撃は含まれない（含まれない攻撃の守りは、準汎用の層に頼る）。
- 実機で確認できた MOD（SuperSteve・Madness）は、テストでは測れない型（クライアントの表示・イベントの購読の外れ等）に当たったため、実機のログで見つけて直した（`SPEC_EPROTECT_ARCHITECTURE.md` §3.13・§3.14）。
- 点数は、テストの数え方（防御側とみなすテストの選び方）で動く。比べるときは、同じ分け方で測ること。

## 5. 再現手順

```
./gradlew runGameTestServer -PwithKillMods            # 個別対策あり
./gradlew runGameTestServer -PwithKillMods -PgenericOnly   # 準汎用のみ
```

フラグは `-PwithKillMods` / `-PwithKillMods2` / `-PwithEca` / `-PwithTrialMonolith` / `-PwithCheatMods` / `-PwithSlashBlade` / `-PwithTechMods`。
ログの末尾の `required tests failed` と、その下の失敗したテスト名を、上の分類表と照らす。ゲーム（`runClient`・ランチャー）が動いている間は走らせないこと。
