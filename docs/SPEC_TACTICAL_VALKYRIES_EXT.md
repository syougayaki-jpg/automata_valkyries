# 戦術兵姫 -Automata Valkyries- 追加拡張仕様書 (SPEC_TACTICAL_VALKYRIES_EXT.md)

※ 本ファイルは既存の仕様書 [`SPEC_TACTICAL_VALKYRIES.md`](SPEC_TACTICAL_VALKYRIES.md)（第1章〜第7章）に続く、第8章〜第13章の追加実装仕様書です。
受領日: 2026-10-02。

---

## E0. 取り込みメモ

### E0.1 1.20.1 Forge への読み替え（既存仕様書 §0.1 と同じ規則）

| 元資料 | 本プロジェクト（Forge 1.20.1） |
|---|---|
| `c:ingots/xxx` 等の `c:` タグ（§11.2） | `forge:ingots/xxx` 等の `forge:` タグ |
| `CustomPacketPayload` / `PlayPayloadContext`（§13.1 の `TacticalRequestPacket`） | `SimpleChannel`（`NetworkRegistry.newSimpleChannel`）のメッセージ。`encode` / `decode` / `handle(Supplier<NetworkEvent.Context>)` で書き、`context.enqueueWork` でサーバースレッドに渡す |
| `new ResourceLocation("tacticalvalkyries", ...)` | `ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, ...)`。名前空間は `automata_valkyries` |
| `EntityValkyrie` | 既存仕様書 §6.2 と同じく兵姫のエンティティ。クラス名はコーディング規約に合わせて決める（例: `ValkyrieEntity`） |
| 連携先 MOD（§11.1） | すべて 1.20.1 Forge 版。連携は任意依存（入っていなくても単体で完結する） |

### E0.2 暫定統一した項目（後で調整する前提の仮決定）

| # | 食い違い・不足 | 暫定統一 |
|---|---|---|
| F | §8.3 の兵装・設備の ID は `block_` / `weapon_` / `backpack_` の接頭辞付き、既存仕様書 §7.7 の素材 ID（`raw_astrite` 等）は接頭辞なし | **仕様書に書かれた ID をそのまま使う**（どちらの形も小文字スネークケースなので規約上は問題ない） |
| G | §9.2 の Tier 0 素材（`drop_chrono_scrap`・`crystal_luminal`・`drop_nanomachine`）は既存仕様書 §7.7 と同じもの | **同じアイテム**として扱う（新しく登録しない） |
| H | §12.1 の同調率の増減が、既存仕様書 §0.3 の「同調率の上昇・低下条件」（未定）に当たる | **§12.1 を正式な条件**とする（既存仕様書の未定項目から外す） |
| I | §8.2 墜落ポッドの兵姫が、既存仕様書 §0.3 の「兵姫の入手方法（初期 1 体の取得手段）」に当たる | **墜落ポッドで入手**を正式とする |
| J | 戦術級コード（§13）のダメージと、保護対象（eprotect）の被弾上限の関係（2026-10-02 にユーザーが決定） | **保護対象でない相手には仕様どおり通す**（ABYSS の最大 HP 30% の割合ダメージ・GUNGNIR の貫通もそのまま）。**eprotect の保護対象（裏ボス・ラスボス等）には上限を優先**し、通常の被弾と同じく 1 撃・時間あたり最大 HP の 10% までにライフカットする（戦術級コードだからといって上限を外さない） |

### E0.3 未定・未受領の項目（推測で埋めず、確認する）

- §8.3 の見出しの本文（データ定義の一覧だけ受領）。`block_crank_dynamo` の「1 回転」が右クリック何回分か
- §11.1 の見出しの本文（連携マトリクスの表だけ受領）
- 墜落ポッドの生成頻度・バイオーム、`damaged_interface` の役割
- 野良ドローン・斥候・前哨基地の防衛設備（§10.2）・クロノ・ガーディアンの HP / 攻撃力などの数値
- 基地ストラクチャ（§10.1）の大きさ・生成条件・頻度、Tier 3 浮遊要塞の生成方法
- GENESIS-ZERO の「半径 64m の地形を完全復元」の基準（いつの時点の地形に戻すか）
- `core_genesis_luminal` の毎秒 1,000,000 FE の出し先（そのマシン自身か、隣接するマシンへ配るか）
- §12.2 お掃除ルーチンの対象（すべてのドロップアイテムか、除外があるか）、§12.3 ボイスの再生方法（テキストのみか、音声ファイルか）

---

## 8. 序盤サバイバルと技術進行フェーズ (Early Game & Progression)

未来技術（時空テレポート、ルミナル変換、重粒子砲）を持たない最序盤において、バニラ資源と泥臭い物理兵站で兵姫を再起動・修復し、二人三脚で立ち上がるための仕様。

### 8.1 進行ステージ遷移 (Progression Stages)

* **Stage 0 [漂着と蘇生]**:
  * 墜落ポッド自然生成 ➔ 手動ダイナモ・石炭スタンドで微小給電 ➔ 記憶破損状態の兵姫が目覚める。
* **Stage 1 [ジャンク兵站と野良斥候戦]**:
  * バニラホッパー・チェストによる物理弾薬補給 ➔ スクラップ銃・振動刃で武装 ➔ 野良ドローン撃破。
* **Stage 2 [クロノ工学のブレイクスルー]**:
  * 敵ドロップ「歪んだクロノ破片」を携帯アナライザーで解析 ➔ 兵姫の記憶領域復旧 ➔ Tier 1 局所テレポートマシン解禁。
* **Stage 3 [前哨基地強襲・上位工学]**:
  * 敵前哨基地へ攻城レイド ➔ ルミナルエナジーアダプター稼働 ➔ 上位合金＆AEC級ボス戦へ。

### 8.2 墜落ポッド (Crashed Drop Pod)

* **ワールド生成**: 地表または浅い地下（Y=40〜80）に稀に生成される3×3×3の黒焦げ構造体。
* **構成ブロック**: `crashed_pod_hull` (硬度高、ツルハシで破壊可)、`damaged_interface`。
* **内包物**:
  * スリープ状態の `EntityValkyrie` (素体ランダム、HP 5/40、コア電力 0)。
  * ポッド内のチェストに `item_portable_analyzer`、破損した弾薬、少量の鉄・銅インゴット。

### 8.3 初期の物理給電・ジャンク兵装

（見出しの本文は未受領。以下は受領したデータ定義）

```
[初期インフラ・兵装データ定義]

block_crank_dynamo:
  プレイヤーの右クリック連打で回転。1回転につき 20 FE を生成し隣接スロット/エンティティへ給電。

block_coal_charge_stand:
  石炭/木炭を燃焼し、毎tick 20 FE を生成。上に座った兵姫の内部バッファへ直接チャージ。

weapon_scrap_shotgun:
  弾薬: 砂利 / 鉄塊 (Iron Nugget)。拡散実弾ペレット × 6発。単発 3.0 ダメージ (近距離特化)。

weapon_motor_blade:
  鉄の剣 + レッドストーンモーター。攻撃時 50 FE/振 を消費し、攻撃力 9.0 の高周波切削。

backpack_steam_thruster:
  内部に「水入り瓶」と「石炭」を装填。ジャンプキー2回押しで蒸気を噴射し、1.5ブロック緊急ステップ。
```

---

## 9. 資源圧縮階層システム (Resource Compression Hierarchy)

素材を `3×3` クラフト（またはアダプター相転移）で圧縮し、上位エネミーや上位ダンジョンほど高圧縮された素材を直接ドロップするハクスラ・ファクトリー連携仕様。

### 9.1 圧縮レート定義

* **Tier 0 [単位素材 (Unit)]**: 1個
* **Tier 1 [ブロック (Block)]**: 単位素材 × 9 (作業台 `3×3` またはアダプター)
* **Tier 2 [オーブ (Orb)]**: ブロック × 9 (単位素材 81個分)
* **Tier 3 [スター (Star)]**: オーブ × 9 (単位素材 729個分)
* **Tier 4 [コア (Core)]**: スター × 9 (単位素材 6,561個分)

### 9.2 3大基幹リソースの圧縮ツリー

| 階層 (Tier) | ① クロノ系 (時空・重装甲) | ② ルミナル系 (光子・高出力) | ③ ナノ系 (生体・修復) |
| :--- | :--- | :--- | :--- |
| **Tier 0 [素材]** | `drop_chrono_scrap`<br>(クロノスクラップ) | `crystal_luminal`<br>(ルミナルクリスタル) | `drop_nanomachine`<br>(リアナノマシン) |
| **Tier 1 [ブロック]**| `block_chrono_metal`<br>(耐爆・時空固定) | `block_luminal_gem`<br>(常時発光・高誘電) | `block_nano_mesh`<br>(自己修復多孔質) |
| **Tier 2 [オーブ]** | `orb_chronos`<br>(空間跳躍のオーブ) | `orb_luminal`<br>(光子励起のオーブ) | `orb_nano`<br>(再生変異のオーブ) |
| **Tier 3 [スター]** | `star_chronos`<br>(因果断絶のクロノスター) | `star_luminal`<br>(恒星崩壊のルミナルスター) | `star_nano`<br>(原初の死生ナノスター) |
| **Tier 4 [コア]** | `core_singularity_chronos`<br>(時空特異点クロノコア) | `core_genesis_luminal`<br>(創世ルミナルコア) | `core_catastrophe_nano`<br>(終焉再生ナノコア) |

### 9.3 敵・基地ドロップ＆直接利用機能

* **野良ドローン / 斥候**: Tier 0 (スクラップ / クリスタル) を 1〜3個 ドロップ。
* **Tier 1 基地 (前哨基地)**: Tier 1 (ブロック) をチェスト・タレット残骸から入手。
* **Tier 2 基地 / AEC級ボス**: Tier 2 (オーブ) を直接ドロップ。
* **Tier 3 基地 / タイムルーラー**: Tier 3 (スター) を直接ドロップ。
* **裏ボス (ロストカタストロフィー)**: Tier 4 (各コア) を確定ドロップ。
* **実用消費効果**:
  * `orb_luminal`: 右クリックまたは給電ドック投入で **5,000,000 FE** を瞬時充填。
  * `star_nano`: 兵姫スリープ（HP0）時に使用で、フルHP・フルシールドで即時戦線復帰。
  * `core_genesis_luminal`: マシンにセットすると、**毎秒 1,000,000 FE を永久無償発電**。

---

## 10. 侵食基地ダンジョンと防衛兵器 (Corrupted Base Dungeons & Siege Units)

プレイヤー拠点を守るタワーディフェンスではなく、敵の防衛網へ兵姫と共に乗り込む「攻城レイド型」システム。

### 10.1 基地ストラクチャ階層

* **Tier 1: クロノ・アウトリポスト (前哨監視基地)**
  * 地表施設。小規模な通信塔と外周タレットで構成。目的: 通信中枢の破壊と初期ブロック鹵獲。
* **Tier 2: ディメンション・ファウンドリ (時空兵站工場)**
  * 地下〜地表の複合ダンジョン。プレス機トラップ、組立ライン、無敵力場発生器が存在。
* **Tier 3: クロノ・バスティオン (浮遊移動要塞)**
  * 上空 Y=180 以上に滞空する超巨大要塞。AEC級ボスおよびタイムルーラーへのポータルを内包。

### 10.2 基地固有の防衛設備 (Defense Entities & Blocks)

1. **パルス・ファランクス (`entity_pulse_phalanx`)**:
   * 固定対空タレット。射程40m。プレイヤーのエリトラ飛行や長距離射撃を感知し、迎撃弾幕を掃射。
2. **ヘビー・シージキャノン (`entity_siege_cannon`)**:
   * 大口径榴弾固定砲。遮蔽物越しに山なり曲射（着弾地点に大爆発＋ノックバック）。
3. **位相アンカー・ジェネレーター (`block_phase_anchor`)**:
   * 基地深部を覆う紫色の無敵バリア供給源。周囲のサブ・コンデンサー（3〜4箇所）を全破壊するまで無敵。
4. **警報ドローン『サイレン・アイ』 (`entity_siren_eye`)**:
   * 侵入者発見時にアラーム鳴動。防衛ハッチから増援ドローンを毎10秒スポーンさせる。暗殺最優先対象。
5. **クロノ・ガーディアン (`entity_chrono_guardian`)**:
   * 基地内通路を巡回する大型多脚機。大型シールドで通路を塞ぎ、高圧スタンロッドで近接迎撃。

### 10.3 兵姫の攻城AIルーチン (`ValkyrieSiegeGoal`)

* **ポイントマン前進**: プレイヤーが遠距離武器所持時、シールド持ち兵姫が自動で前方半歩に立ち射線を遮蔽。
* **タレット・警報優先撃破**: 雑魚よりもタレット・サイレン・アイをAIが最優先ターゲットとしてロック。
* **防衛円陣 (Defensive Perimeter)**: プレイヤーがジェネレーター破壊・ハッキング中、背中合わせで360度迎撃。

---

## 11. 外部工業MOD連携アーキテクチャ (Cross-Mod Integration)

MOD単体で完全自己完結しつつ、外部工業MODの導入時に生産力・兵站が爆発的に向上するアダプティブ設計。

### 11.1 連携マトリクス仕様

（見出しの本文は未受領。以下は受領した連携マトリクス）

| 連携先工業MOD | 提供されるブースト・連携機能 |
|---|---|
| Mekanism | ・鉱石の3〜5倍化ライン完全対応 (スラリー/浸出/濃縮)<br>・核融合/超大容量FEによるルミナル相転移の即時完了<br>・液体窒素/冷却材を給電ドックへ注入 ➔ 熱蓄積完全無効化 |
| Applied Energistics 2 / Refined Storage | ・給電ドックにMEインターフェース直結<br>・兵姫の回収ドロップを直接MEストレージへ自動搬入<br>・弾薬/ナノリペア剤の自動クラフト発注・無限補給 |
| Create | ・回転動力(Stress)をオルタネーター経由で初期給電変換<br>・ベルトコンベア/メカニカルアームによる物理弾薬供給 |
| Thermal Expansion / Ender IO | ・アロイスメルターによる上位合金配合 (FE消費30%カット)<br>・ゲル化クリオセウムによる兵装強制冷却 |

### 11.2 タグ設計標準（元資料は `c:tags` 準拠。本プロジェクトでは `forge:` に読み替え。E0.1）

* `forge:ingots/astrite`, `forge:ingots/phononium`, `forge:ingots/ceruleas`, `forge:ingots/steel`
* `forge:ores/astrite`, `forge:raw_materials/astrite`
* 外部MODの金属インゴット・粉末・プレートと100%相互代替クラフト可能。

---

## 12. 自律兵姫の日常・お世話・感情AI (Valkyrie Emotion & Daily AI)

LittleMaidMobの「愛着・お世話」要素を最新AIゴールとして実装。

### 12.1 同調率 (Sync Rate: 0% 〜 100%) 変動ロジック

```java
// 同調率更新イベントフック
public void adjustSyncRate(EntityValkyrie valkyrie, int delta, SyncReason reason) {
    int current = valkyrie.getSyncRate();
    valkyrie.setSyncRate(Mth.clamp(current + delta, 0, 100));
    // 100%到達時: 裏ボスのハッキング完全無効化フラグ付与
}
```

* **加算 (+)**:
  * 満充電完了 (+2%) / 手動リペア (+3%)
  * 好物プレゼント（ルミナルキャンディ、冷却アイス） (+5%)
  * プレイヤー瀕死救助 / 兵姫スリープからの復旧 (+10%)
* **減算 (-)**:
  * 電力ゼロ放置 10分以上 (-5%) / 過熱状態の無理な連続稼働 (-3%)

### 12.2 拠点待機・日常AI (`ValkyrieDailyLifeGoal`)

* **椅子・ベッド腰掛け**: 待機モード中、半径12m以内のバニラ階段ブロックやベッドを検出し、自律的に腰掛けて足を揺らす。
* **お掃除ルーチン**: 周囲のドロップアイテムを検出し、拾い集めて最寄りのチェストへ搬入。
* **作業見守り**: プレイヤーが作業台・金床・かまどを開いている際、背後から覗き込むモーションを実行。

### 12.3 状況別ボイス・テキストテーブル

| 状況 | 低同期 | 高同期 |
|---|---|---|
| 朝の起動時 | 「システムオンライン。索敵プロトコル待機中。」 | 「おはよう、マスター！ 今日もコアの調子、バッチリだよ。」 |
| 低電力 (20%以下) | 「警告: ルミナル残量20%。充電ラインを要求。」 | 「うぅ……マスター、そろそろお腹空いた（充電したい）かも……」 |
| 弾切れ | 「残弾ゼロ！ 近接格闘へ強制移行します！」 | （同じ） |
| プレイヤー瀕死 (HP 20%以下) | 「マスターには指一本触れさせない……！ 絶対障壁、展開！」 | （同じ） |

---

## 13. 戦術級コード体系 (Tactical Protocol System)

ルミナルステラの軌道衛星群・戦略兵站庫とリンクし、戦局を覆す大戦略兵器を投下する決戦システム。

### 13.1 二重承認プロトコル仕様

* **発動フロー**:
  1. プレイヤーが `tool_tac_pointer` を構え、「V」キーでコードを選択 ➔ 右クリック長押しで座標指定。
  2. サーバーへ `TacticalRequestPacket` 送信。
  3. 兵姫のAIが「同調率」「必要FE」を判定 ➔ 承認ボイスと共に天から兵器投下。
* **コンフィグ**: `enableTacticalGriefing: false` で地形破壊を無効化（mobへのダメージのみ付与）。

```
[TACTICAL PROTOCOL REGISTRY]
1. protocol_carpet (コード: CARPET [絨毯飽和爆撃]):
   - 要求: 同調率 30% / 消費 500,000 FE / CT 120秒
   - 挙動: 半径32mへ64発の徹甲ナパーム弾を5秒間波状投下。タレット網・地雷原・雑魚群を一掃。

2. protocol_gungnir (コード: GUNGNIR [軌道衛星集束レーザー]):
   - 要求: 同調率 50% / 消費 1,500,000 FE / CT 180秒
   - 挙動: 指定座標へ直径6ブロックの天罰レーザー照射 (毎秒 150.0 ダメージ, 5秒持続)。
   - 特性: ボスの絶対無敵バリアや基地の主隔壁を強制貫通・蒸発。

3. protocol_abyss (コード: ABYSS [特異点重力崩壊球]):
   - 要求: 同調率 70% / 消費 3,000,000 FE / CT 300秒
   - 挙動: 前方にブラックホールを投擲。半径24m以内の敵・弾幕を8秒間強制吸引拘束 ➔ 爆縮時に最大HP30%の割合ダメージ。

4. protocol_iris (コード: IRIS [広域ナノ防壁・野戦病院]):
   - 要求: 同調率 40% / 消費 800,000 FE / CT 150秒
   - 挙動: 半径16mのドームシールド展開 (30秒持続)。敵弾を完全遮断し、内部の味方HPを毎秒10%回復＋充電速度3倍。

5. protocol_genesis_zero (コード: GENESIS-ZERO [因果律リブート]):
   - 要求: 同調率 100% (裏ボス零号機専用) / 消費 5,000,000 FE / CT 600秒
   - 挙動: ボスの即死詠唱・時間停止を強制キャンセル。半径64mの地形を完全復元し、プレイヤーと兵姫のHP/シールド全快。
```

元資料のパケット（NeoForge の Payload 方式。1.20.1 Forge では `SimpleChannel` のメッセージに読み替える。E0.1）:

```java
// C2S: 戦術級コード要請パケット
public record TacticalRequestPacket(
    UUID valkyrieUUID,
    BlockPos targetPos,
    ResourceLocation protocolId
) implements CustomPacketPayload {
    public static final ResourceLocation ID = new ResourceLocation("tacticalvalkyries", "tactical_request");
    @Override
    public ResourceLocation id() { return ID; }

    public void handle(PlayPayloadContext context) {
        context.workHandler().execute(() -> {
            ServerPlayer player = (ServerPlayer) context.player().orElseThrow();
            TacticalProtocolManager.executeProtocol(player, valkyrieUUID, targetPos, protocolId);
        });
    }
}
```

* **描画**: `GungnirBeamRenderer` はバニラのビーコンシェーダーを拡張し、中心部を白、外縁部をネオンブルーに多重描画。照射時、周囲32mのプレイヤー画面にカメラシェイクエフェクト（パケット通知）を付与。
