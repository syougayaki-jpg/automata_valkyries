# 引継書: 即死保護（Eprotect）および抹殺機構の汎用化・準汎用化（CODEX 向け）

作成: 2026-10-08（Antigravity）。
ユーザーの依頼「これぜんぶCODEXに引継ぎで」に基づき、直近で実装された Java エージェント付き即死MOD対策（SuperSteve, Pig2Mod, Nekohashizuku Blade 等）および抹殺機構から抽出された**準汎用層・汎用層への昇格・汎用化タスク**をまとめた仕様書・引継ぎ資料です。

---

## 1. 背景と設計方針

当MODの保護アーキテクチャ（[`docs/SPEC_EPROTECT_ARCHITECTURE.md`](SPEC_EPROTECT_ARCHITECTURE.md)）では、以下の原則が定められています。

> **原則 7. 層を分ける。**
> 準汎用の層（どの MOD でも効く）と、MOD ごとの互換層を分け、互換層を切っても（`-PgenericOnly`）準汎用だけで守れることをテストで測る。

直近の作業で、SuperSteve、Pig2Mod、猫雫（Nekohashizuku Blade）に対する特化対策（`EprotectSuperSteveCounter`、`EprotectPig2Counter`、`EprotectNekohashizukuCounter`、および `ModProtectionBreaker.breakSuperSteve` 等）を導入し、GameTest 320件すべて合格（コミット `4573485`）を達成しました。

これら特化対策の中で使われている技術には、**「他の未知の即死MODにも共通して使われる手口」** が多数含まれています。これらを特化層（個別MOD対応）から**準汎用層（フラグ辞書・ForeignStateCleaner・KillLists・ModProtectionBreaker）**へ引き上げることで、`-PgenericOnly`（互換層無効化環境）でも未知のMODから自己防衛・完全抹殺できる能力を大幅に底上げします。

---

## 2. 制約事項（最重要）

Claude 側で直近に編集・調整された以下の **6 ファイルは共有ファイルのため、絶対に上書き・直接編集しないでください**。

1. `src/main/java/com/automatavalkyries/protect/Eprotect.java`
2. `src/main/java/com/automatavalkyries/mixin/EprotectDamageMixin.java`
3. `src/main/java/com/automatavalkyries/protect/EprotectForgeEvents.java`
4. `src/main/java/com/automatavalkyries/entity/ElenorEntity.java`
5. `src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java`
6. `src/main/java/com/automatavalkyries/mixin/LivingEntityAccessor.java`

本引継書の汎用化タスクは、すべて上記の共有ファイルに触れることなく、**準汎用層の各クラス（`ForeignStateCleaner`、`KillLists`、`ModProtectionBreaker` 等）の内部で完結**して実装可能です。

---

## 3. 汎用化・昇格タスク一覧（全 5 項目）

### タスク 1: 当たり判定（AABB / Hitbox）崩壊の検知と自動再計算（準汎用化）
- **背景**:
  SuperSteve をはじめとする凶悪MODは、エンティティの BoundingBox を `new AABB(x, y, z, x, y, z)` のように体積 0 の点や極小値（幅・高さ < 0.001D）に書き換えます。これにより、当たり判定が消失し、ブロックの当たり判定を抜けて奈落へ落下したり、プレイヤーの攻撃や照準からターゲット不能になります。
- **現状の実装**:
  `com.automatavalkyries.protect.EprotectSuperSteveCounter` 内にハードコード:
  ```java
  AABB currentBb = entity.getBoundingBox();
  if (currentBb.getXsize() < 0.001D && currentBb.getYsize() < 0.001D && currentBb.getZsize() < 0.001D) {
      entity.refreshDimensions();
      neutralized = true;
  }
  ```
- **汎用化の仕様**:
  - **対象クラス**: `com.automatavalkyries.protect.ForeignStateCleaner`
  - **実行タイミング**: 毎 Tick の `ForeignStateCleaner.clean(LivingEntity entity)` 内
  - **ロジック**:
    保護対象エンティティの AABB の幅（X/Z）または高さ（Y）が極小（`< 0.001D`）になっている場合、`entity.refreshDimensions()` を呼び出して正規の寸法に再計算・復元する。
    復元した場合は `AttackLog.neutralized(entity, CallerAttribution.UNKNOWN, "hitbox:aabb_collapsed")` を記録し、戻り値を `true` とする。
  - **安全性**: バニラの健全なエンティティは生身で幅や高さが 0.001D 未満になることはないため、健全なエンティティに対する副作用はありません（`AttackAttributionGameTests.foreignKillStateIsReset` の `clean(mob) == false` 条件も満たします）。

---

### タスク 2: 物理無効化フラグ（`noPhysics`）の不正改ざん復元（準汎用化）
- **背景**:
  敵対MODが保護対象のフィールド `entity.noPhysics` を `true` に書き換えることで、重力やブロック衝突を無視させ、奈落へ強制落下させて即死させる攻撃手法です。
- **現状の実装**:
  `EprotectSuperSteveCounter` 内で `if (entity.noPhysics) entity.noPhysics = false` を個別実行。
- **汎用化の仕様**:
  - **対象クラス**: `com.automatavalkyries.protect.ForeignStateCleaner`
  - **実行タイミング**: 毎 Tick の `ForeignStateCleaner.clean(LivingEntity entity)` 内
  - **ロジック**:
    `entity.noPhysics && !entity.isPassenger()` の場合（乗客として搭乗している正当なケースを除く）、`entity.noPhysics = false` に戻す。
    復元時は `AttackLog.neutralized(entity, CallerAttribution.UNKNOWN, "physics:no_physics")` を記録。

---

### タスク 3: 静的リスト・Map による即死・HP改ざん（`HEALTH_TAMPER`）の自動収集・除外（準汎用化）
- **背景**:
  猫雫（Nekohashizuku Blade）の `AgentHealthMethodHelper.HEALTH_MAP` のように、「敵対エンティティを static Map<LivingEntity, Float> や static Map<UUID, ...> に保持し、毎 Tick HP を強制固定・上書きする」手法は多くのチートMODが採用しています。
  しかし現行の `KillLists.java` は、名前分類が `Category.KILL`（死亡・消去）および `CONTROL` / `CONCEAL`（停止・隠蔽）の static コレクションのみを収集しており、`HEALTH_TAMPER`（HP改ざん・ロック）が探索網から漏れていました。そのため猫雫対策として `EprotectNekohashizukuCounter` を個別に実装する必要が生じていました。
- **汎用化の仕様**:
  - **対象クラス**: `com.automatavalkyries.protect.flag.KillLists`
  - **ロジック**:
    1. `isTamperName(String name)` を追加:
       ```java
       private static boolean isTamperName(String name) {
           Set<FlagDictionary.Category> c = FlagDictionary.classify(name);
           if (c.contains(FlagDictionary.Category.HEALTH_TAMPER) && !c.contains(FlagDictionary.Category.GUARD)) {
               return true;
           }
           String lower = name.toLowerCase(java.util.Locale.ROOT);
           return (lower.contains("health") || lower.contains("hp"))
                   && (lower.contains("map") || lower.contains("list") || lower.contains("set") || lower.contains("lock") || lower.contains("record"));
       }
       ```
    2. `collectLists(Class<?> type)` 内の判定条件を拡張:
       ```java
       boolean killClass = isKillName(type.getSimpleName()) || isStopName(type.getSimpleName()) || isTamperName(type.getSimpleName());
       ...
       if (killClass || isKillName(name) || isStopName(name) || isTamperName(name)) remember(field, CallerAttribution.modOf(type));
       ```
    3. `scanInjected()` 内の判定条件にも `isTamperName(field.getName())` を追加。
  - **効果**:
    `StaticReferencePurger.purgeMap` はすでにキーと値の両方を走査して除去する実装になっているため、これだけで未知のMODが持つ `HEALTH_MAP`, `HP_LOCK_LIST`, `DAMAGE_TRACKER` 等の静的コレクションから保護対象が毎 Tick 自動的にパージされます。

---

### タスク 4: 抹殺側：擬似プレイヤー切断・ボスバー解放の汎用化（準汎用化）
- **背景**:
  他MODのチートボス（FakePlayer や独自 `ServerPlayer` を継承したボス、または画面に巨大な常駐ボスバーを持つボス）を超絶抹殺の棒や真抹殺で消去した際、クライアントにゴーストプレイヤー接続が残ったり、画面上部に消えないボスバーが取り残される問題があります。
- **現状の実装**:
  `ModProtectionBreaker.breakSuperSteve` 内に `disconnect()` や `bossEvent.removeAllPlayers()` がハードコードされています。
- **汎用化の仕様**:
  - **対象クラス**: `com.automatavalkyries.annihilate.ModProtectionBreaker`
  - **配置場所**: `removeControllers(Entity target)` 付近、または新設の汎用ボス後始末メソッド `cleanupForeignBoss(Entity target)`
  - **ロジック**:
    1. **接続切断**: 抹殺対象またはそのコントローラーが `disconnect()` または `kick()` メソッド（引数なし、または Component/String 引数）を持っていれば、リフレクションで安全に呼び出す。
    2. **ボスバー消去**: 対象またはそのコントローラーが `ServerBossEvent` や `CustomBossEvent`（あるいはフィールド名が `bossEvent`, `bossBar`）を保持している場合、リフレクションで `removeAllPlayers()` および `setVisible(false)` を呼び出す。

---

### タスク 5: 抹殺側：ボスの戦闘追尾・ターゲットループの汎用解除（準汎用化）
- **背景**:
  Pig2Mod のように、対象が死亡・消去された後も別スレッドやタイマーで攻撃ループを回し続け、ターゲットを追い続けるボスが存在します。
- **現状の実装**:
  `ModProtectionBreaker.breakPig2` 内で `stopFightingRemovedEnemy()` や `mAttackingEnemy = null` などを個別呼出し。
- **汎用化の仕様**:
  - **対象クラス**: `com.automatavalkyries.annihilate.ModProtectionBreaker`
  - **ロジック**:
    対象クラスおよびコントローラーのクラスを走査し:
    1. 引数なしメソッドで `stop*Fighting*`, `stop*Combat*`, `clearTarget*`, `resetTarget*` 等の名前に一致するものがあればリフレクションで実行。
    2. フィールド名が `mAttacking*`, `target`, `attackTarget`, `currentTarget` 等で非プリミティブのものを `null` にクリア。

---

## 4. 検証と品質基準

作業完了後は、以下の手順でビルド・テストを実行し、リグレッションがないことを確認してください。

1. **基本 GameTest の実行**:
   ```powershell
   ./gradlew runGameTestServer --no-daemon
   ```
   **基準**: 320 / 320 の全 GameTest が成功すること。

2. **準汎用のみモードでの検証**:
   ```powershell
   ./gradlew runGameTestServer -PgenericOnly --no-daemon
   ```
   **基準**: 特化カウンターを切った状態でも、汎用保護テスト（`GenericProtectGameTests` 等）がパスすること。

3. **保護エージェント併用モードでの検証**:
   ```powershell
   ./gradlew runGameTestServer -PwithObserver -PwithLifeGuard -PwithValkyrieProtect -PwithEprotectPlus --no-daemon
   ```
   **基準**: エージェント間で並べ直しループやクラッシュが発生しないこと。

---

## 5. 参考資料

- 保護アーキテクチャ全般仕様: [`docs/SPEC_EPROTECT_ARCHITECTURE.md`](SPEC_EPROTECT_ARCHITECTURE.md)
- 守る側の Java エージェント仕様: [`docs/SPEC_JAVA_AGENTS_DEFENSIVE.md`](SPEC_JAVA_AGENTS_DEFENSIVE.md)
- 倒す側の Java エージェント（Eprotect+ / Terminus）仕様: [`docs/SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md`](SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md)
- 最新コミット: `4573485`（Harden Java Agent instant-kill offensive and defensive mechanics）
