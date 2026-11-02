# Automata Valkyries（戦術兵姫MOD）

**[日本語](#日本語) ／ [English](#english)**

Minecraft **1.20.1** ／ Forge **47.4** 以降 ／ 前提 MOD **GeckoLib 4.8** 以降

---

## 日本語

未来から墜ちてきた機械仕掛けの少女「戦術兵姫（オートマタ・ヴァルキリー）」と出会い、育て、共に戦う大型 SF・工業・戦闘 MOD です。

### 主な内容

- **戦術兵姫**
  - 世界各地の墜落ポッドで眠る兵姫を、プロトコアで目覚めさせて仲間にする
  - 兵装（T1〜T4）・外装フレーム・背部ユニット・ルミナルコア・ロジックチップで換装し、同調率を上げて育てる
  - 前衛・後衛・拠点防衛・医療・回収と運搬などの戦闘と日常の AI、時間帯や同調率で変わる会話
  - 戦術支援コード（COMET / CARPET / GUNGNIR / ABYSS / IRIS）で大技を要請
- **クロノリンク・マシン**（T1 ハイエンド → T2 フューチャー → T3 ビヨンド → T4 ネバーラスト）
  - ルミナル反応炉・ルミナルエナジーアダプター（原子相転移。レシピを選べる）・クロノ・スメルター・クロノ・ファブリケータ（設計図カードで自動の組み立て）・テザー・パイロン（無線給電）・ヴォイド・リポジトリ（圧縮ストレージ。日本語のかな・ローマ字検索）・給電ドック・時間加速器・時間巻き戻し機
  - 空間リンクで離れた所とつなぐ。T4 のマシンはタイム・ルーラーを倒すと作れる
- **兵装と装備**: 兵装強化台・オーバーロード・各金属の防具・アイテムポケット・観測の立方体・光翼の飛翔機
- **敵とボス**: 侵食基地（倉庫・前哨基地・工場・空中庭園要塞。初めのリスポーンから遠いほど出やすい）、英雄機、クロノゲイザー、総司令機タイム・ルーラー、アカシック・シーア、エレノア、隠しボス
- **追憶メモリ（回想のトライアル）**、**進捗 60 個**（パロディ多め）

遊び方は、初めてワールドに入るともらえるゲーム内の説明書「**戦術兵姫の手引き**」（全 18 章）をご覧ください。レシピの確認には **JEI** をおすすめします。

### 必要な物

| | バージョン |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4 以降 |
| GeckoLib | 4.8 以降（必須） |
| Java | 17 |

### 連携（入れると使える。無くても動く）

| MOD | 連携の内容 |
|---|---|
| JEI（おすすめ）／ EMI | このMOD のマシンのレシピ、設計図カードへのレシピの書き込み（[+]） |
| Jade | マシン（ティア・リンク）と兵姫（状態・電力・同調率）の情報 |
| Curios | アイテムポケット（ベルト）・観測の立方体（お守り）・光翼の飛翔機（背中） |
| Mekanism ／ Create ／ Immersive Engineering ／ Thermal | 固有の鉱石の粉の 2 倍化、連携金属の板、電力のやり取り、基本回路・高等回路の互換 |
| AE2 ／ Refined Storage | ヴォイド・リポジトリをストレージバス・外部ストレージでつなげる |
| Tinkers' Construct | このMOD の 8 金属の溶鉱炉・鋳造、道具の素材（独自の特性）、ボス素材のアビリティ（隔絶など）。TiCEX の再鍛抜刀剣にも使える |
| SlashBlade: Resharped | 8 金属の名のある刀（専用のモデル・独自の抜刀術・特殊効果） |

ほかの MOD が同じ金属の鉱石を生成する場合は、このMOD の鉱石の生成は自動で譲ります。

### 設定

設定ファイルは `config/` にできます（`automata_valkyries-*.toml`）。全項目とよく使う設定は [`docs/CONFIG_REFERENCE.md`](docs/CONFIG_REFERENCE.md) にまとめてあります。例:

- 序盤の簡易化（墜落ポッドの兵姫を端末だけで起こす）: `earlyGame.easyPodWake`
- 戦術コードで地形を壊す・ボスに効かせる: `tactical.*`（既定はどちらもオフ）
- T4 のマシンをタイム・ルーラーより前に作る: `machines.t4RequiresTimeRuler = false`

### 注意

- このMOD のボスと兵姫には強い保護（即死・強制消去への耐性）が付いています。保護の仕組みは設定とコマンド `/eprotect` で調整できます。
- 同梱している守る側の Java エージェントは、**既定ではすべてオフ**です（設定 `agents.*.enabled` で入れる）。Java エージェントを持つ他の MOD とは干渉することがあります。
- クリエイティブのタブには、検証用の道具（抹殺の棒など）も入っています。

### ライセンス

- **コード**（Java のソース、ビルドの設定、`data/` 以下のレシピ・タグなどのデータ）: [Mozilla Public License 2.0](LICENSE.txt)。改変したファイルを配布するときは、そのファイルのソースも MPL-2.0 で公開してください。
- **アセット**（`src/main/resources/assets/automata_valkyries/` 以下のモデル・テクスチャ・アニメーション・音・シェーダー・翻訳の文章・ガイド、およびキャラクター・ボスの名前・設定・台詞）: All Rights Reserved（作者: sfdge）。詳しくは [`LICENSE-ASSETS.txt`](LICENSE-ASSETS.txt)。
- **MOD パック**: 改変していない jar を MOD パックに入れて配布するのは自由です（連絡不要）。

---

## English

Meet the **Tactical Valkyries** — mechanical weapon girls who fell from the future. Wake them, gear them up, build time-bending machines and fight together.

### Features

- **Tactical Valkyries**
  - Wake valkyries sleeping in crashed pods with a Proto Core and recruit them
  - Customize T1–T4 weapons, frames, back units, Luminal Cores and logic chips; raise their sync rate
  - Combat and daily-life AI (vanguard, rearguard, base defense, medic, hauling…) and dialogue that changes with time and sync
  - Call in tactical support codes (COMET / CARPET / GUNGNIR / ABYSS / IRIS)
- **Chronolink machines** (T1 High-End → T2 Future → T3 Beyond → T4 Neverlast)
  - Luminal Reactor, Luminal Energy Adapter (phase transitions, selectable recipes), Chrono Smelter, Chrono Fabricator (blueprint auto-crafting), Tether Pylon (wireless power), Void Repository (compressed storage with search), Charging Dock, Time Accelerator, Time Rewinder
  - Spatial links connect distant blocks. T4 machines unlock after defeating the Time Ruler
- **Gear**: Weapon Upgrade Station, Overload, metal armor sets, Item Pocket, Observation Cubes, Light Wing Flyer
- **Enemies and bosses**: corrupted bases (more common farther from world spawn), hero units, Chrono Gazers, the Time Ruler, the Akashic Seer, Elenor and a secret boss
- **Recollection trials** and **60 advancements**

Read the in-game guide **"Tactical Valkyries Guide"** (18 chapters, given on first join). **JEI** is recommended for recipes.

### Requirements

Minecraft 1.20.1 · Forge 47.4+ · **GeckoLib 4.8+ (required)** · Java 17

### Optional integrations

JEI / EMI (recipes, blueprint [+] transfer) · Jade (machine and valkyrie info) · Curios (pocket, cubes, flyer slots) · Mekanism / Create / Immersive Engineering / Thermal (2× ore dust, shared-metal plates, power, shared basic/advanced circuits) · AE2 / Refined Storage (Void Repository via storage bus / external storage) · Tinkers' Construct (melting, casting, tool materials with custom traits, boss-material abilities; works with TiCEX's Reforged SlashBlade) · SlashBlade: Resharped (eight named blades with original models, Slash Arts and special effects)

### Configuration

Config files are `config/automata_valkyries-*.toml`. Every option is listed in [`docs/CONFIG_REFERENCE.md`](docs/CONFIG_REFERENCE.md).

### Notes

- Bosses and valkyries from this mod are strongly protected against instant-kill and forced removal; adjust with the config and `/eprotect`.
- The bundled defensive Java agents are **off by default** (`agents.*.enabled`). They may interact with other mods that use Java agents.
- The creative tabs also contain testing tools (such as kill sticks).

### License

- **Code** (Java sources, build scripts, and data under `data/` such as recipes and tags): [Mozilla Public License 2.0](LICENSE.txt). If you distribute a modified file, publish its source under MPL-2.0 too.
- **Assets** (models, textures, animations, sounds, shaders, translated text and the guide under `src/main/resources/assets/automata_valkyries/`, plus the names, settings and lines of the characters and bosses): All Rights Reserved (author: sfdge). See [`LICENSE-ASSETS.txt`](LICENSE-ASSETS.txt).
- **Modpacks**: You may include the unmodified jar in any modpack. No need to ask.
