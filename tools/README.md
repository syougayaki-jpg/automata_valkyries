# tools/

テクスチャ・テンプレートの生成とモデルのプレビュー用スクリプト。MOD 本体のビルドには含まれない。
すべて Java 17 の単一ファイル実行（`java Xxx.java ...`）で動く。プロジェクトのルートで実行すること。

| スクリプト | 出力 | 用途 |
|---|---|---|
| `GenElenorSkin.java` | `textures/entity/elenor.png` | エレノアのテクスチャ（128x128）。色は先頭の `C` マップ、パーツは各 `map` / `box` 呼び出しで調整 |
| `GenOreTextures.java` | `textures/item/*.png` | 鉱石・インゴット・テスト用の抹殺の棒の仮テクスチャ |
| `GenEmpty.java` | `data/automata_valkyries/structures/empty.nbt` | GameTest 用の空ストラクチャ |
| `gen_valkyrie_v.py` | `geo/entity/valkyrie_type_v.geo.json`・`textures/entity/valkyrie_type_v*.png`（表情 9 枚＋発光マスク） | 戦術兵姫 Type-V のモデルとテクスチャ（Python、標準ライブラリだけ）。形は `build()`、色は `PAL`、目は `eye_grid()`（表情ごと）。1px に 2 ドット（目は 12×12）の面ごとの UV で、置き場所は自動で詰める |
| `gen_valkyrie_sounds.py` | `sounds/valkyrie/*.ogg`・`sounds.json`・字幕（lang） | 戦術兵姫の効果音を波形の計算で合成（Python。`pip install numpy soundfile` が要る）。音ごとに a/b/c の 3 つ。数値は各関数で調整し、`python tools/gen_valkyrie_sounds.py [名前の一部]` で作り直す |
| `GeoPreview.java` | 任意の PNG | GeckoLib モデルを 3 ポーズ × 4 方向で描画して確認（ゲーム起動不要） |

```bash
A=src/main/resources/assets/automata_valkyries

java tools/GenElenorSkin.java $A/textures/entity/elenor.png
java tools/GenOreTextures.java $A/textures/item
java tools/GenEmpty.java src/main/resources/data/automata_valkyries/structures/empty.nbt

# GeoPreview は gson が必要（Gradle キャッシュにあるものを使う）
GSON=$(find ~/.gradle/caches/modules-2 -name 'gson-2.10.1.jar' | head -1)
java -cp "$GSON" tools/GeoPreview.java $A/geo/entity/elenor.geo.json $A/textures/entity/elenor.png build/elenor_preview.png
# 兵姫（識別子に valkyrie）はスカートの無い姿勢。引数の後ろに [拡大率] [ポーズ] [向き] を足せる
java -cp "$GSON" tools/GeoPreview.java $A/geo/entity/valkyrie_type_v.geo.json $A/textures/entity/valkyrie_type_v.png build/valkyrie_v_preview.png 12 idle,walk 0,35,90,180
```

`GeoPreview` のポーズ計算は `client/ElenorModel#setCustomAnimations` を手で写したもの。モデル側の計算を変えたらこちらも合わせること。
