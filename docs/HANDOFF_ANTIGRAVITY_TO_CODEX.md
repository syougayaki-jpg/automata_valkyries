# Antigravity → CODEX 業務全般引継書（戦術兵姫MOD）

作成日: 2026-10-08  
前任: Antigravity  
後任: CODEX  
対象プロジェクト: `automata_valkyries` (Minecraft 1.20.1 / Forge 47.4.x / Java 17)

---

## 1. はじめに（引継ぎの背景と目的）

これまで Antigravity が担当してきた**「即死・抹殺機構（超絶抹殺・真抹殺・終焉機構）」「Eprotect+ Java エージェント」「攻撃貫通・チートMOD解体」「準汎用化アーキテクチャ」**に関するすべての業務・コード資産・未着手タスクを、後任の **CODEX** へ全面的に引き継ぎます。

本MODでは、Claude（兵姫本体・兵装・機械・世界観・ゲーム内通常保護担当）と役割を分担して開発を行ってきました。CODEX は Antigravity の役割を引き継ぎ、強力な即死MODや干渉MODに対抗する技術基盤を維持・発展させてください。

---

## 2. 担当領域（CODEX 所管のコード資産マップ）

CODEX が担当するコードおよびリソースは以下のとおりです。

```
src/
├── main/java/com/automatavalkyries/
│   ├── annihilate/                       ★【抹殺・終焉エンジン】
│   │   ├── AnnihilationExecutionAgent.java   # 多段階即死・完全消去パイプライン
│   │   ├── ModProtectionBreaker.java         # 他MODの生命維持剥奪・チートボス解体
│   │   └── TickListEviction.java             # サーバーTickループを壊さない安全なリスト除外
│   ├── protect/plus/                     ★【Eprotect+ & 終焉リンク層】
│   │   ├── EprotectPlusLink.java             # JVMエージェント通信 ＆ 特権フォールバック
│   │   └── TerminusKillLink.java             # 終焉執行リンク
│   ├── protect/flag/                     ★【準汎用フラグ・抹殺リスト】
│   │   ├── KillLists.java                    # 他MODの抹殺予定リスト収集と毎Tickパージ
│   │   ├── InjectedMembers.java              # 他MODが注入したフィールドの解析
│   │   └── StateScanner.java                 # 状態の走査・消去
│   ├── item/                             ★【抹殺・検証用アイテム】
│   │   ├── DebugKillStickItem.java           # 抹殺の棒（バニラ基礎消去）
│   │   ├── SuperKillStickItem.java           # 超抹殺の棒（深い追い出し・再参加拒否）
│   │   ├── UltraKillStickItem.java           # 超絶抹殺の棒（汎用・準汎用・特化解体）
│   │   ├── TrueKillStickItem.java            # 真抹殺の棒（エージェント特権執行）
│   │   └── TerminusWandItem.java             # 終焉の魔杖（10秒耐性追跡・絶対死）
│   ├── entity/
│   │   └── ProtectDummyZombieEntity.java     # Eprotect+ 検証用ダミーゾンビ（終焉保護対象外）
│   └── weapon/
│       ├── DeathLootFallback.java            # 即死・抹殺時のドロップ品・経験値補完
│       └── Overload.java (kill メソッド)     # T4兵装チャージ攻撃の超絶抹殺連携
│
└── eprotectplus/java/com/automatavalkyries/eprotectplus/  ★【JVM 特権エージェント層】
    ├── EprotectPlusAgent.java                # システムクラスローダー常駐 Java エージェント
    ├── EprotectPlusBridge.java               # クラスローダー間通信架け橋・ログ
    ├── TerminusKillEngine.java               # 終焉絶対死・特権空間キル
    └── flag/
        ├── FlagDictionaryAgent.java          # フラグ辞書のバイトコード保護
        └── FlagDictionaryProtector.java      # 不変マスター辞書
```

---

## 3. 重要原則と境界ルール（Claude との共同作業における約束）

Claude が担当している以下の **6 ファイルは共有ファイルのため、原則として直接編集しないでください**。

1. `src/main/java/com/automatavalkyries/protect/Eprotect.java`
2. `src/main/java/com/automatavalkyries/mixin/EprotectDamageMixin.java`
3. `src/main/java/com/automatavalkyries/protect/EprotectForgeEvents.java`
4. `src/main/java/com/automatavalkyries/entity/ElenorEntity.java`
5. `src/main/java/com/automatavalkyries/protect/flag/FlagDictionary.java`
6. `src/main/java/com/automatavalkyries/mixin/LivingEntityAccessor.java`

- **防御の住み分け**:
  - ゲーム内通常の被弾上限（10% / 10秒）、死亡拒否、自己修復は Claude 側の `Eprotect.java` が担当。
  - バイトコード改ざん復元、JVM 特権空間からの偽装迂回、スタック検閲の欺瞞（`runWithCloakedStack`）は CODEX（Eprotect+）が担当。
- **Java エージェントの住み分け**:
  - `ObserverAgent`（観察者）、`LifeGuardAgent`（真ライフ）、`ValkyrieProtectAgent`（兵姫保護）は Claude 側が管理（詳細は [`docs/SPEC_JAVA_AGENTS_DEFENSIVE.md`](SPEC_JAVA_AGENTS_DEFENSIVE.md)）。
  - `EprotectPlusAgent`（Eprotect+ / 終焉執行）および `FlagDictionaryAgent` は CODEX が管理。

---

## 4. CODEX が直ちに着手・継続すべき課題一覧

### 課題 1: 特化対策から準汎用層への昇格・汎用化（最重要）
詳細仕様書: **[`docs/HANDOFF_CODEX_GENERALIZATION.md`](HANDOFF_CODEX_GENERALIZATION.md)**

直近で SuperSteve, Pig2Mod, Nekohashizuku Blade に施した対策を準汎用層へ引き上げてください：
1. **AABB（当たり判定）崩壊の検知と自動復元**: `ForeignStateCleaner.java` で `< 0.001D` の極小 AABB を検知し `entity.refreshDimensions()` で復元。
2. **物理無効化（`noPhysics`）の不正改ざん復元**: `ForeignStateCleaner.java` で生身の `noPhysics = true` を `false` に戻す。
3. **`HEALTH_TAMPER` 静的リストの自動収集・パージ**: `KillLists.java` に `isTamperName()` を追加し、猫雫の `HEALTH_MAP` 等の静的 HP 改ざん Map から保護対象を自動除外。
4. **チートボスの切断・ボスバー解放の汎用化**: `ModProtectionBreaker.java` で `disconnect()` / `kick()` や `ServerBossEvent.removeAllPlayers()` をリフレクションで汎用呼び出し。
5. **ボスの戦闘追尾・ターゲットループの汎用解除**: `ModProtectionBreaker.java` で `stop*Fighting*` 等のメソッドや `target` フィールドを汎用リセット。

### 課題 2: クライアント側の消し残り後始末による保護対象誤消去の抑止
- **現象**: ダミーゾンビがサーバー側で生存しているのに、クライアントの後始末処理（`ClientAnnihilation` 付近）で誤って消去され、プレイヤーから見えなくなる現象が発生していた。
- **対策**: 後始末の対象から `IEprotected` を持つ個体、およびサーバーがまだ追跡中（消去パケット未受信）の個体を必ず除外すること。

### 課題 3: バイトコード再監査（`retransformCriticalClasses`）の負荷軽減
- **現象**: `EprotectPlusLink.enforceBytecodeIntegrity()` が頻繁に呼ばれると、サーバーTick遅延（Can't keep up!）が発生する。
- **対策**:
  - 定期点検の間隔を長めに保つ（1200 Tick 程度、または他エージェントの干渉検知時のみ）。
  - 強化保護中の多重呼び出しをスキップする。
  - `eprotect-plus.log` への同一クラス再変換ログの重複を間引く。

---

## 5. ビルド・検証コマンド集

CODEX は以下のコマンドで動作確認および GameTest 検証を行ってください。

```powershell
# 1. コンパイル確認
./gradlew compileJava test

# 2. GameTest 全件実行（GUI なし、最重要検証）
./gradlew runGameTestServer --no-daemon

# 3. 準汎用層のみの検証（MOD個別互換層を切った状態での耐性試験）
./gradlew runGameTestServer -PgenericOnly --no-daemon

# 4. 全 Java エージェント統合テスト（並べ直しループや競合の確認）
./gradlew runGameTestServer -PwithObserver -PwithLifeGuard -PwithValkyrieProtect -PwithEprotectPlus --no-daemon
```

---

## 6. 関連仕様書リンク一覧

- **汎用化引継ぎ仕様書**: [`docs/HANDOFF_CODEX_GENERALIZATION.md`](HANDOFF_CODEX_GENERALIZATION.md)
- **オーバーロード T4 超絶抹殺仕様書**: [`docs/HANDOFF_OVERLOAD_ULTIMATE_ANNIHILATION.md`](HANDOFF_OVERLOAD_ULTIMATE_ANNIHILATION.md)
- **Eprotect 保護アーキテクチャ全般仕様書**: [`docs/SPEC_EPROTECT_ARCHITECTURE.md`](SPEC_EPROTECT_ARCHITECTURE.md)
- **Eprotect+ & Terminus 包括仕様書**: [`docs/SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md`](SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md)
- **守る側の Java エージェント地図**: [`docs/SPEC_JAVA_AGENTS_DEFENSIVE.md`](SPEC_JAVA_AGENTS_DEFENSIVE.md)
- **戦術兵姫MOD 全体仕様書**: [`docs/SPEC_TACTICAL_VALKYRIES.md`](SPEC_TACTICAL_VALKYRIES.md)
