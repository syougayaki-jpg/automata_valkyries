# Automata Valkyries（戦術兵姫MOD）

Minecraft **1.20.1** / **Forge 47.4.x** / **Java 17** の MOD。

- MOD ID: `automata_valkyries`（リソースの名前空間もこれ）
- パッケージ: `com.automatavalkyries`
- メインクラス: `src/main/java/com/automatavalkyries/AutomataValkyries.java`
- マッピング: Mojang 公式名（`official`）。MCP/SRG 名（`func_xxx`, `field_xxx`）は使わない

## MOD 全体仕様

MOD 全体の仕様（世界観、兵姫、敵・ボス、アイテム、マシン）は **[`docs/SPEC_TACTICAL_VALKYRIES.md`](docs/SPEC_TACTICAL_VALKYRIES.md)** にある。

- 元資料は 1.20.4+ / NeoForge 前提。1.20.1 Forge への読み替えは同書 §0.1 を参照（`c:` タグは `forge:` に、時間停止は独自実装）
- 【暫定統一】の項目（§0.2）は後でユーザーが調整する前提の仮決定。変更されたら仕様書を更新する
- 未定・未受領の項目（§0.3）は推測で埋めず、ユーザーに確認する

## 戦術兵姫MOD 設計・開発コンテキスト（Antigravity引継ぎ）

Antigravity AIとの対話で策定・実装された戦術兵姫のコア保護システム（`eprotect`）およびサンプルボス（エレノア）の全仕様・コード資産は **[`docs/TACTICAL_DOLL_CHAT_CONTEXT.md`](docs/TACTICAL_DOLL_CHAT_CONTEXT.md)** に詳細に記録されています。

- **主要要件**:
  1. `/kill` 拒否・即死攻撃無効化（Mixinによる `Entity.remove` / `discard` / `setRemoved` / `onRemovedFromWorld` のブロック）
  2. **IMDS-Reborn（インフィニティマタードミネーターソードリボーン）完全耐性**:
     - `die(DamageSource)` の直接呼び出しを HEAD で完全阻止（HP即時全快 & `deathTime = 0`）
     - IMDSの内部Mixin無敵フラグ（`imds$setInvulnerable(true)`）を逆奪取して常時完全無敵化
     - `ImdsKillManager` の `FORCE_DEAD_UUIDS` / `REMOVED_UUIDS` からの自動除外
     - `-Infinity` テレポート（奈落落とし）の阻止と安全座標バックアップへの即時ロールバック
  3. 桁外れ大ダメージカット（NaN / 無限大 / 100以上のダメージを上限10.0Fに制限）
  4. 毎Tick同期自己修復 & 物理消去からの自動復元（Forge `ServerTickEvent` 監視）
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
