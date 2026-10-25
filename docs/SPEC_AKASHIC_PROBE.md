# 事象観測体 アカシック・プローブ（2026-10-25）

ユーザー指定「アカシックちゃんのモデルを使って即死凶悪技術の観測に特化した特殊モブを作る」。即死・凶悪技術を自分で受けて記録し、相手の MOD を下調べする、倒れない観測用のモブ。

- 登録名: `automata_valkyries:akashic_probe`（スポーンエッグ `akashic_probe_spawn_egg`）
- コード: `entity/akashic/`（`AkashicProbeEntity`・`AkashicRecord`・`AkashicSurvey`）、描画は `client/AkashicProbeRenderer`・`client/ObjMesh`
- GameTest: `AkashicProbeGameTests`（batch `akashic`）

## 0. 担当（2026-10-25 ユーザー指定「アンチグラビティーに依頼するのでモデルと演出担当で」）

| 担当 | 範囲 |
|---|---|
| **Claude** | モデルと演出: メッシュの描画（`client/AkashicProbeRenderer`・`client/ObjMesh`）、テクスチャ・素材（`meshes/`・`textures/entity/akashic_*`）、結晶・浮遊・揺れ・ホログラムの見せ方、パーティクル・音、翻訳の文言 |
| **Antigravity** | 観測の仕組みの拡張（監視エージェントを使った観測の網羅など。ユーザーから Antigravity へ直接依頼） |

既にある観測の部分（`entity/akashic/AkashicRecord`・`AkashicSurvey`、`AkashicProbeEntity` の観測・保護の部分、`AttackLog` の呼び出し）は 2026-10-25 に Claude が作った。Antigravity が広げるときは、そのまま使っても作り替えてもよい。演出が観測の結果を使うとき（ホログラムの行・突破の知らせなど）は、`AkashicRecord.hologram()` と同期データ `HOLOGRAM` を境目にする

## 1. 決定事項（2026-10-25 ユーザーへの質問で決定）

| 項目 | 決定 |
|---|---|
| モデル | **メッシュをそのまま描く**。GeckoLib（箱）には作り直さない |
| 観測のしかた | **自分が受けて記録する** ＋ **相手の MOD を下調べする**（近くの戦いの見張りはしない） |
| 耐久 | **保護して倒れない**（Eprotect）。保護を突破されたら、その経路を最後の記録として残す |
| 出力 | **頭上のホログラム** ＋ **ログファイル**（チャット・本は使わない） |

## 2. 見た目

- 元の素材: `Downloads/アカシックちゃん3d model/`（Tripo で作った三角形のメッシュ 1 枚。11002 面・骨なし・PBR のテクスチャ一式）と、立ち絵 `OneDrive/画像/アカシックちゃん正面.png`・`背面.png`（名前は「事象観測体 アカシック・プローブ」、周りに桃・薄紫・水色の多面体の結晶）
- MOD に入れた物: `assets/automata_valkyries/meshes/akashic_probe.obj`（元のまま）、`textures/entity/akashic_probe.png`（基本色 `basecolor_hairfix` を 4096 → 2048 に縮めた物。法線・金属・粗さの絵は使わない）、`textures/entity/akashic_crystal.png`（結晶用の白）
- メッシュは +Z が正面（マイクラのエンティティの向きと同じ）、高さ 0〜0.9775 を当たり判定の高さ 1.75 ブロックへ合わせる。UV の縦は反転する
- 骨が無いので、体全体を少し浮かせてゆっくり上下させ、向きと僅かな揺れだけ付ける。周りに正二十面体の結晶を 3 つ（桃 `#FF4FD8`・薄紫 `#B07CFF`・水色 `#4FE3FF`）、半透明・自発光で回す
- `client/ObjMesh` はリソースの読み直しのとき（裏のスレッド）に OBJ を読み、描画では作っておいた配列を流すだけ。三角形は最後の頂点を 2 度出して四角形にする（エンティティの描画は四角形ごと）。F3+T で読み直せる
- 手足を動かしたくなったら、Blender で分割・骨入れ（`blender` MCP。2026-10-25 に登録済み、Blender 5.2 にアドオン）してから考える

## 3. 動き

- その場に立って（浮いて見える）、近くのプレイヤーと、直近 5 秒に攻撃してきた相手の方を向くだけ。攻撃も移動もしない。押されない・ノックバックしない・遠くで消えない・ピースフルで消えない
- 誰も狙わない（`getTarget` は常に null）
- **シフトを押しながら素手で右クリック**すると、記録を閉じて（最後のまとめをログへ）正規に退場する（「観測を終了します。記録は保存しました」）

## 4. 観測（`AkashicRecord`）

- 記録は観測体の UUID ごとに 1 つ。入り直し・登録簿からの作り直しで個体が替わっても続く
- **受けた操作**: 保護の攻撃の記録（`AttackLog.record`）のうち、この観測体が受けた物を全部（MOD・操作・結果ごとに数える）。結果は拒否・修復・無力化・検知・ブラックリストで阻止など、保護の記録のまま
- **被弾**: ダメージの種類ごと（`damage:<種類>`）。相手の MOD（相手のエンティティのクラス、無ければダメージの種類の名前空間）と最後の量。HP は記録したら毎 Tick 満タンへ戻す（受け続けるため）
- **突破**: die が通った（保護が撃破を認めた）・消去が通った（正規の退場・撃破の演出以外で、消す理由の消去）ときは、そのときの呼び出しの経路（スタック 48 段まで）を `BREACH` として書く
- 何でも受ける（`isInvulnerableTo` は常に false）。終焉機構の味方の保護（`isTerminusTestTarget`）は既定のまま（このMOD の終焉では狙わない）

## 5. 下調べ（`AkashicSurvey`）

- 初めて見た他 MOD（`CallerAttribution.isForeign`）は、その MOD の jar（`ModList` の MOD のファイル。入れ子の jar も 1 段）のクラスを定数プールだけ読み、「できる作り」を割り出す。クラスは読み込まない。MOD ごとに 1 回、別のスレッド（`AutomataValkyries-AkashicSurvey`）で回し、結果は使い回す。1 つの MOD で 4 万クラスまで
- 見る能力（翻訳キー `akashic.automata_valkyries.capability.<名前>`）:
  - `unsafe`（Unsafe）・`instrumentation`（クラスの書き換え）・`attach`（自分でエージェントを付ける）・`coremod`（起動時のクラス書き換え）・`overwrite`（Mixin の丸ごと置き換え）・`jvm_exit`（JVM の終了）
  - `field_write`（リフレクション等で生死の状態のフィールド名を指す。公式名と SRG 名）・`reflective_calls`（メソッドの総当たり呼び出し）・`world_lists`（ワールドのエンティティの一覧の内部）・`client_remove`（手元への消去の送信）・`boss_bar`（ボスバーの消去）・`threads`（他のスレッドの停止）
- 観察用エージェント（`-PwithObserver`）が付いていれば、その能力の地図（`ObserverLink.capabilities`）の行も足す（`observer:<能力>`）
- 実際にしたことではなく、できること（The King Sword の手口を調べたときの物を一般化した。`memory: king-sword-mechanics`）

## 6. 出力

- **頭上のホログラム**: 見出し「― 事象観測記録 ―」と、最近の 6 行（新しい物が上）。下調べの結果・突破の知らせ（2 行まで）と、最近の組み合わせ（`MOD 操作 ×数`。拒否は赤・修復と無力化は金・検知は黄）。24 ブロック以内で見える。サーバーで作った部品（JSON）を同期データで送り、手元で翻訳して描く
- **ログファイル**: `logs/automata_valkyries-akashic.log`（ゲームのフォルダ）。初めての組み合わせは呼び出し元・経由した MOD まで 1 行、続けて起きた数は 10 秒ごとにまとめ、下調べの結果は能力ごとに例のクラス付き、突破はスタック付き。同じ行を `latest.log` にも `[akashic]` で出す

## 7. 未定・今後

- 近くの戦い（エレノア等）の見張り、チャット・本での表示は、今回は選ばれなかった
- 見た目の確認（向き・大きさ・明るさ・結晶）はゲーム内で未確認（2026-10-25 時点）
