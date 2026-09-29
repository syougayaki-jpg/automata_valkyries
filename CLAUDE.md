# Automata Valkyries（戦術兵姫MOD）

Minecraft **1.20.1** / **Forge 47.4.x** / **Java 17** の MOD。

- MOD ID: `automata_valkyries`（リソースの名前空間もこれ）
- パッケージ: `com.automatavalkyries`
- メインクラス: `src/main/java/com/automatavalkyries/AutomataValkyries.java`
- マッピング: Mojang 公式名（`official`）。MCP/SRG 名（`func_xxx`, `field_xxx`）は使わない

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
