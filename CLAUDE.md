# Automata Valkyries（戦術兵姫MOD）

Minecraft **1.20.1** / **Forge 47.4.x** / **Java 17** の MOD。

- MOD ID: `automata_valkyries`（リソースの名前空間もこれ）
- パッケージ: `com.automatavalkyries`
- メインクラス: `src/main/java/com/automatavalkyries/AutomataValkyries.java`
- マッピング: Mojang 公式名（`official`）。MCP/SRG 名（`func_xxx`, `field_xxx`）は使わない

## MOD 全体仕様

MOD 全体の仕様（世界観、兵姫、敵・ボス、アイテム、マシン）は **[`docs/SPEC_TACTICAL_VALKYRIES.md`](docs/SPEC_TACTICAL_VALKYRIES.md)** にある。第 8〜13 章（序盤の進行・墜落ポッド、資源圧縮、侵食基地ダンジョン、外部工業 MOD 連携、日常・感情 AI、戦術級コード）は追加拡張仕様書 **[`docs/SPEC_TACTICAL_VALKYRIES_EXT.md`](docs/SPEC_TACTICAL_VALKYRIES_EXT.md)** にあり、1.20.1 Forge への読み替え・暫定統一・未定の項目は同書 §E0。

- 本題の機械（クロノリンク・マシン）のティア設計（T1 ハイエンド / T2 フューチャー / T3 ビヨンド / T4 ネバーラスト）は **[`docs/SPEC_CHRONOLINK_MACHINES.md`](docs/SPEC_CHRONOLINK_MACHINES.md)**
- 戦術兵姫の見た目・動き・エフェクト・メカと少女のデザインの連携の設計の拠り所（『星の翼』の公開情報と一般原則から。写さないための決まり付き）は **[`docs/DESIGN_VALKYRIE_VISUAL_DB.md`](docs/DESIGN_VALKYRIE_VISUAL_DB.md)**
- 戦術兵姫の GUI・操作・戦闘 AI・日常/感情 AI・保護・入手の決定事項（2026-10-04 にユーザーへの質問で決定）は **[`docs/SPEC_VALKYRIE_GUI_AI.md`](docs/SPEC_VALKYRIE_GUI_AI.md)**
- 兵器ユニット（兵装・ルミナルコア・背部ユニット・外装フレーム・ロジックチップ）の決定事項（使い手・電力・製造マシン・ティア割り振り・操作・見た目など）は **[`docs/SPEC_WEAPON_UNITS.md`](docs/SPEC_WEAPON_UNITS.md)**
- 元資料は 1.20.4+ / NeoForge 前提。1.20.1 Forge への読み替えは同書 §0.1 を参照（`c:` タグは `forge:` に、時間停止は独自実装）
- 【暫定統一】の項目（§0.2）は後でユーザーが調整する前提の仮決定。変更されたら仕様書を更新する
- 未定・未受領の項目（§0.3）は推測で埋めず、ユーザーに確認する

**注意（2026-10-02 時点）**: Java エージェントのクラスや深いフックを持つ MOD（Hyperlink・ECA など）とは、保護同士・保護と即死がぶつかって干渉することがある。ECA ＋ The Last Sword の組み合わせでのプレイヤーのリスキル問題は解決済み。原因はこのMOD自身の後始末（`EcaLastSwordHelper.cleanupPlayerRestraints` が錨の無いプレイヤーに The Last Sword の `clearDefence` を呼び、HP を 0 に書いていた）（詳細は保護の仕様書 §8.1）。検証は組み合わせを絞って行う。

保護の仕組み（Eprotect）の全体像・各層・設定・テスト方針・他 MOD への移植手順は **[`docs/SPEC_EPROTECT_ARCHITECTURE.md`](docs/SPEC_EPROTECT_ARCHITECTURE.md)** にまとめてある（ゲーム内保護側）。

守る側の Java エージェント（観察者・LifeGuard・オートマタヴァルキリー protect、共通部品 `src/agentcommon`、起動の順番、このMOD 同士の決まり、乗っ取る MOD への備え）の地図は **[`docs/SPEC_JAVA_AGENTS_DEFENSIVE.md`](docs/SPEC_JAVA_AGENTS_DEFENSIVE.md)**（2026-10-08 整理）。

Java エージェント（Eprotect+ / FlagDictionaryProtector）、終焉執行エンジン（Terminus / プレイヤー絶対死 / 10秒耐性検知）、および高次元防護（ブラックリスト強化・10秒間無敵・バイトコード完全性復元）の包括的アーキテクチャ仕様書・データは **[`docs/SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md`](docs/SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md)** に完全永続化保存されている。

Eprotect および抹殺機構の汎用化・準汎用層への昇格引継ぎ仕様書は **[`docs/HANDOFF_CODEX_GENERALIZATION.md`](docs/HANDOFF_CODEX_GENERALIZATION.md)**（2026-10-08 CODEX 引継ぎ）。

Antigravity 担当業務（即死・抹殺機構 `annihilate/`、Eprotect+ Java エージェント `src/eprotectplus/`、終焉機構 `terminus`）の CODEX への全体引継書は **[`docs/HANDOFF_ANTIGRAVITY_TO_CODEX.md`](docs/HANDOFF_ANTIGRAVITY_TO_CODEX.md)**（2026-10-08 引継ぎ完了）。

## 戦術兵姫MOD 設計・開発コンテキスト（Antigravity引継ぎ）

**2026-10-08: Antigravity の担当（抹殺・終焉機構、Eprotect+、即死 MOD 個別の対抗など）は CODEX に引き継いだ。** CODEX の入口はリポジトリ直下の [`AGENTS.md`](AGENTS.md)、引継書は [`docs/HANDOFF_ANTIGRAVITY_TO_CODEX.md`](docs/HANDOFF_ANTIGRAVITY_TO_CODEX.md)。担当の分け方と境界の決まり（相手の担当ファイルを編集しない・自分のファイルだけをパス指定でコミットする）は AGENTS.md にある。

Antigravity AIとの対話で策定・実装された戦術兵姫のコア保護システム（`eprotect`）およびサンプルボス（エレノア）の全仕様・コード資産は **[`docs/TACTICAL_DOLL_CHAT_CONTEXT.md`](docs/TACTICAL_DOLL_CHAT_CONTEXT.md)** に詳細に記録されています。

- **主要要件**:
  1. `/kill` 拒否・即死攻撃無効化（Mixinによる `Entity.remove` / `discard` / `setRemoved` / `onRemovedFromWorld` のブロック）
  2. **IMDS-Reborn（インフィニティマタードミネーターソードリボーン）完全耐性**:
     - `die(DamageSource)` の直接呼び出しを HEAD で完全阻止（HP即時全快 & `deathTime = 0`）
     - ~~IMDSの内部Mixin無敵フラグ（`imds$setInvulnerable(true)`）を逆奪取して常時完全無敵化~~ → 2026-10-01 に廃止（ユーザー判断）。IMDS 環境だけ一切ダメージを受けず倒せなくなるため。今は神無敵を外す側
     - `ImdsKillManager` の `FORCE_DEAD_UUIDS` / `REMOVED_UUIDS` からの自動除外
     - `-Infinity` テレポート（奈落落とし）の阻止と安全座標バックアップへの即時ロールバック
  3. 被弾量の上限: NaN / 無限大 / 1000 以上の単発は 100 に置き換え、HP から引くのは **10 秒（200 Tick）の間に合計で最大 HP の 10% まで**（`Eprotect.capFinalDamage`、2026-10-01 にユーザー指定）。最大 HP 属性の改ざんも毎 Tick 戻す
  4. 毎Tick同期自己修復 & 物理消去からの自動復元（Forge `ServerTickEvent` 監視）
  - 時間停止対策（2026-10-01）: 時間窓（被弾上限・修復回数・復元回数・Tick 停止の検出）はゲーム時刻ではなくサーバーの Tick 数で測る。Tick 一覧に載っているのに本体の Tick が来なければ（ワールドの Tick ごと止める型）Eprotect が代わりに回す。代わりに回しても入口で打ち切られるときは、そこに差し込まれた打ち切れる他 MOD の Mixin を候補として攻撃の記録に残す（`protect/TickEntranceHooks`、報告のみ）。エンティティ単位で止める型は互換 Mixin が要る（No-Sugar は `NoSugarMixins$TimeStop`）
  - 攻撃の記録とブラックリスト（2026-10-01）: 拒否・修復した操作を「どの MOD か」と一緒に記録（`protect/attack/`、`/eprotect attacks`、`logs/automata_valkyries-attacks.log`）。MOD 単位のブラックリストは手動（`/eprotect blacklist`）と自動（既定 1200 Tick に 20 回）で、載った MOD の即死系の操作（強制移動・次元移動・ダメージ外の HP 減少）だけ追加で止める。通常ダメージは通す
  - 準汎用の状態リセットとフラグ辞書（2026-10-01）: 他 MOD が付けた状態（インターフェース・既存クラスに足されたフィールド・Capability・永続データ）を名前で分類する辞書（`protect/flag/FlagDictionary`）で、保護対象からは死亡・停止・迂回の印を毎 Tick 戻し、超絶抹殺の棒では守り・復活の許可を外す。辞書は設定 `flagDictionary.extra` / `ignore` と `/eprotect flags` で調整する。辞書補助の非干渉監視エージェント（`protect/flag/FlagWatchAgent`、2026-10-01）は他 MOD の状態を読むだけで見張り、死亡・消去・攻撃の直前にだけ変わる辞書に無い名前を候補に挙げる（`/eprotect flags watch`、`logs/automata_valkyries-flag-candidates.txt`。辞書には自分で足さない）生死の判定・修復は getter を通さず `DirectRead` で実際の値を読む
  - 保護範囲の拡張（2026-10-03）: 兵姫搭乗中の乗り物・同乗者の連動保護および強化モード時の結界オーラ（周囲16m）。安全のためコンフィグ既定は OFF（範囲強化なし、単体保護のみ）。設定 `protection.expandedScope.enabled` やコマンド `/eprotect scope` で切替可能
  5. ボス退場演出（`IProviderWithSpeech`、プレイヤー敗北時の紫色セリフ `「あら、もう終わりですか？ 期待外れですね……♪」` & サウンド）
- **移植元資産**: `C:\Users\sfdge\Downloads\masterE` にて最新ビルド成功（`mastere-1.0.0.jar` 生成確認済み）。
- **実装タスク**: `docs/TACTICAL_DOLL_CHAT_CONTEXT.md` を参照し、`automata_valkyries`（`com.automatavalkyries`）へ順次移植・拡張すること。

## コマンド

Windows では `.\gradlew.bat`、Git Bash では `./gradlew`。

| 目的 | コマンド |
|---|---|
| コンパイル＋jar 作成 | `./gradlew build`（成果物は `build/libs/`） |
| **ロード確認（GUI なし）** | `./gradlew runGameTestServer` — サーバーが起動して GameTest を実行し、自動終了する。登録エラーやクラッシュの検出に使う |
| ゲーム起動（手動確認） | `./gradlew runClient` — ウィンドウが開くので、ユーザーに確認を依頼する |
| 準汎用だけで測る | `-PgenericOnly` を足す（`runGameTestServer` / `runClient`）。MOD ごとの個別対策（互換 Mixin・KillModCounters・ボス固有の無力化）を切り、フラグ辞書などの準汎用の層だけでどこまで守れる・倒せるかを見る |
| このMODの Mixin を外して起動 | `-PdisableMixins=compat.HyperlinkMixins,compat.EcaMixins`（カンマ区切り、`com.automatavalkyries.mixin` からの相対名で前方一致）。干渉の切り分け用（`mixin/AutomataMixinPlugin`） |
| 観察用 Java エージェントを付ける | `-PwithObserver` を足す（`runGameTestServer` / `runClient`）。`build/libs/automata_valkyries-observer.jar`（`src/observer`、JDK だけに依存）を `-javaagent` で読ませる。他の Java エージェントによる実行中の書き換え（再変換・再定義）と呼んだ側を `logs/automata_valkyries-observer.log` と `/eprotect observer` に出す。能力の地図（他 MOD のクラスが Unsafe での書き込み・クラスの書き換え・自己 attach・JVM の終了・ワールドの一覧への手出し等をできる作りか。定数プールを読むだけ）を `/eprotect observer capabilities` に出す。他 MOD のクラスは書き換えないが、このMOD 自身のクラスを他のエージェントが書き換えたら読み込んだときの形に戻す（改ざん防止 `IntegrityGuard`。`-javaagent:...observer.jar=noguard` で切れる）。後から読み込まれたエージェントの再変換の中身は見えないので、見たいときはそのエージェントの後で `jcmd <pid> JVMTI.agent_load <jar>` で attach する |
| **LifeGuard（真ライフ）エージェントを付ける** | `-PwithLifeGuard` を足す。`build/libs/automata_valkyries-lifeguard.jar`（`src/lifeguard`、ASM 同梱）と呼び出し先 `automata_valkyries-lifeguard-hooks.jar`（`src/lifeguardhooks`、ブートストラップに置く。同じフォルダに置くこと）。vanilla の `LivingEntity` とこのMOD のクラスの getHealth / isDeadOrDying / isAlive の戻り際を、他のエージェントの包みより外側で包み、保護対象だけ実際の値に直す（`protect/TrueLife`）。他のエージェントの再変換を見たら自分の変換器を最後へ並べ直す。ログは `logs/automata_valkyries-lifeguard.log` |
| **オートマタヴァルキリー protect のエージェントを付ける** | `-PwithValkyrieProtect` を足す。`build/libs/automata_valkyries-valkyrie-protect.jar`（`src/valkyrieprotect`、ASM 同梱）と呼び出し先 `automata_valkyries-valkyrie-protect-hooks.jar`（`src/valkyrieprotecthooks`、ブートストラップ。同じフォルダに置く）。vanilla の `Entity` / `LivingEntity` の消去・即死の入口（remove / setRemoved / kill / die / setHealth）の先頭で問い合わせ、戦術兵姫だけ消去を断り即死を機能停止に置き換える（`protect/valkyrie/ValkyrieAgentFilter`）。他のエージェントの再変換を見たら最後へ並べ直す（このMOD 同士＝LifeGuard・Eprotect+・観察者の再変換は呼び出し元で見分けて数えない。2026-10-05、互いに並べ直し合っていた）。ログは `logs/automata_valkyries-valkyrie-protect.log`。エージェントが無くてもゲーム内の層（兵姫のクラス・`ValkyrieProtectMixin`・毎秒の見張りと二重バックアップからの復元、`protect/valkyrie/ValkyrieProtect`）は動く。管理は `/avprotect list` / `release <兵姫>` / `check` |
| **守る側のエージェントを起動の引数なしで（設定で）** | `run/config/automata_valkyries-common.toml` の `agents.observer.enabled` / `agents.lifeGuard.enabled` / `agents.valkyrieProtect.enabled` を true にして起動し直す（`protect/AgentSelfAttach`。MOD の jar に入れたエージェントを取り出し、別のプロセスから付ける。開発環境では先に `./gradlew build` した `build/libs` のものを使う。詳しくは `docs/SPEC_JAVA_AGENTS_DEFENSIVE.md` §2.1） |
| **Eprotect+ Java エージェントを付ける** | `-PwithEprotectPlus` を足す（`runGameTestServer` / `runClient`）。`build/libs/automata_valkyries-eprotect-plus.jar`（`src/eprotectplus`、JDK だけに依存）を `-javaagent` で読ませる。Witherzilla・ACE・Omni-Mobs・No-Sugar 等の干渉を調査・迂回・保護し、真抹殺の棒による特権抹殺（生命維持フラグ剥奪・StackChecker迂回）を実行する。`/eprotect plus` で診断・ログを確認可能 |
| **Java エージェント同梱 MOD をまとめて検証** | `-PwithAllAgents`（Shizuku Blade + ECA/The Last Sword）または `-PwithSlashBlade`, `-PwithLastSword`。エージェント同士の競合・干渉・防壁突破を検証 |
| **Omni-Mobs だけ外す** | `-PwithAllKillMods -PwithoutOmniMobs`。Omni-Mobs の爆発（`LevelUtil.explode`）が爆発の強さ×100 体の Husk を探り用に作り、どこかの集合に握られて残り続けてメモリが埋まり、終了時の保存が止まった（2026-10-05 の調査）。今は `mixin/compat/OmniMobsStabilityMixins` が explode を Husk を作らない同じ結果の処理に置き換えているので、通常は外さなくてよい。切り分け用 |
| **未知のチート MOD で準汎用の層を測る** | `-PwithCheatMods`（ManaitaPlus Legacy + ManaitaMTK。サーバー可。`CheatModGameTests`。`-PgenericOnly` と併せる）、`-PwithFantasyEnding`（Fantasy Ending と前提一式。**この開発環境では起動できない**: 作者の開発環境の判定で、開発環境扱いだと作者の PC のパスと自分の jar をエージェントに使い、そうでないと難読化名で反射して失敗する。通常のランチャーでも、このMOD抜きで FE 自身のシェーダーの登録で落ちる。2026-10-03 時点で実機確認できていない） |
| **他の MOD のボスと戦わせる** | `-PwithBossMods` を足す（`runClient`）。Titans World 1.3（＋ TitansAPI・AttributeFix）・Craziness Awakened 5.1.1（OreSpawn の後継。任意の前提の Pehkui 3.8.2 はこの開発環境では Mixin で落ちるので入れない）・L_Ender's Cataclysm 3.31・Terramity 0.9.8・Bosses of Mass Destruction 1.1.2・Cracker's Wither Storm Mod 4.2.1（とそれぞれの前提）を読み込む。Wither Storm は入れ子の CrackersLib がデオブフされないので、先に `python tools/prepare_boss_mods.py` で `libs/bossmods/` に取り出す。DOOM The Titans（自分で付く Java エージェント同梱）は `-PwithDoomTitans` に分けた。戦わせるのはクリエイティブ用の【宿命の結び糸】`fate_thread`（見ている 2 体を順に右クリックで結ぶと、どちらかが倒れるまで互いだけを狙う。`entity/FateDuels`。2026-10-20） |
| **他のエージェントに手を出す MOD を検証** | `-PwithSuperSteve` / `-PwithMadness`（`-PwithObserver` と併せる）。どちらも自分の jar を読み直すのでクラスパスに載せず、デオブフした物を `run/mods/dev-*.jar` に置く（`syncDevMods`、フラグを外せば消える）。SuperSteve は JDK の `InstrumentationImpl` を書き換えて他のエージェントの書き換えを捨てるので、このMOD が Mixin の読み込み時に SuperSteve 自身の安全モード（`supersteve.safemode=true`）を立てる（`protect/AgentHijackGuard`、2026-10-08。武器の登録名が `endoftwdr` に変わる。`-Dautomata_valkyries.superSteveSafemode=false` で立てない）。Madness Entities はこの開発環境では起動できない（隠したクラスを毎回ランダムな名前のパッケージに置き、それが見つからない） |
| **外部 Java エージェント / 外部 MOD を追加** | `./libs/` に MOD jar を置くと自動でデオブフ読込＆内包エージェントを自動登録。単体エージェントは `./agents/` に置くか `-PextraAgent="パス"` で `-javaagent` 追加 |
| データ生成 | `./gradlew runData` → `src/generated/resources/` |
| MC ソース再展開 | `./gradlew extractMcSources`（Forge バージョンを変えたら再実行） |

変更後は最低限 `build` を通し、登録・リソース周りを触ったら `runGameTestServer` まで実行してログに `ERROR` や例外が出ていないか確認すること。ログは `run/logs/latest.log` と `run/logs/debug.log`。

## バニラ / Forge のソースを参照する

`.mcsrc/` にデコンパイル済みソースとバニラ素材が展開されている（git 管理外）。
**API を推測で書かず、まずここを grep して実際のシグネチャと実装例を確認すること。**

- `.mcsrc/java/net/minecraft/` — Minecraft 本体
- `.mcsrc/java/net/minecraftforge/` — Forge API（イベント、レジストリ、ネットワーク等）
- `.mcsrc/assets/minecraft/` — バニラのモデル・blockstate・lang・テクスチャ
- `.mcsrc/data/minecraft/` — バニラのレシピ・ルートテーブル・タグ

例: エンティティの AI ゴールの実装を見るなら `.mcsrc/java/net/minecraft/world/entity/ai/goal/` を読む。

## 1.20.1 固有の注意点（他バージョンの書き方と混同しやすい）

AI が学習した情報は 1.21 / NeoForge / Fabric のものが混ざりやすい。以下は **1.20.1 Forge** の正しい形。

- `ResourceLocation.fromNamespaceAndPath(MODID, "path")` / `ResourceLocation.parse("ns:path")` を使う（Forge 47.4 でバックポート済み。`new ResourceLocation(...)` は非推奨）
- 登録は `DeferredRegister` + `RegistryObject`（`net.minecraftforge.registries`）。NeoForge の `DeferredHolder` / `DeferredItem` は存在しない
- メタデータは `src/main/resources/META-INF/mods.toml`（`neoforge.mods.toml` ではない）。値は `gradle.properties` から展開される
- アイテムの追加データは **NBT**（`stack.getOrCreateTag()`）。Data Components（1.20.5+）は存在しない
- `FoodProperties.Builder` は `.nutrition()` / `.saturationMod()`
- ネットワーク: `NetworkRegistry.newSimpleChannel` で `SimpleChannel` を作る（`net.minecraftforge.network`）。Payload/StreamCodec 方式は使えない
- クリエイティブタブ: `DeferredRegister<CreativeModeTab>`（`Registries.CREATIVE_MODE_TAB`）、既存タブへの追加は `BuildCreativeModeTabContentsEvent`
- エンティティ属性: `EntityAttributeCreationEvent`（MOD バス）
- イベントバス: 登録・セットアップ系は **MOD バス**（`context.getModEventBus()`）、ゲーム中のイベントは `MinecraftForge.EVENT_BUS`。`@Mod.EventBusSubscriber` の `bus` 指定を間違えると黙って呼ばれない
- クライアント専用コード（レンダラー、画面、`Minecraft.getInstance()`）は `Dist.CLIENT` 限定のクラスに分離する。共通コードから直接参照するとサーバーでクラッシュする
- リソースのフォルダ名（1.20.1）: `textures/item`, `textures/block`, `models/item`, `models/block`, `data/<modid>/loot_tables/blocks`, `data/<modid>/recipes`（1.21 の `loot_table` / `recipe` 単数形ではない）

## リソース

- 翻訳: `assets/automata_valkyries/lang/en_us.json` と `ja_jp.json` の両方に追加する
- テクスチャは PNG、アイテム/ブロックは基本 16x16

## コーディング規約

- 新しいレジストリ項目はメインクラスに詰め込まず、`registry/ModItems.java`, `ModBlocks.java`, `ModEntities.java` のように種類ごとに分ける
- レジストリ名は小文字スネークケース（`valkyrie_core` など）
- 現在の `example_block` / `example_item` / `Config` の中身は MDK のサンプル。本実装に置き換えてよい
