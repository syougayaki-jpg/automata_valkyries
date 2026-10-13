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
| `gen_valkyrie_units.py` | `geo/entity/unit/*.geo.json`・`textures/entity/unit/valkyrie_units*.png`・`memento_butterfly.png` | 兵姫の外装フレーム（型×部位）・背部ユニットの形と共通の素材の見本帳（Python＋PIL）。座標は兵姫のモデルと同じ。`preview` を付けると兵姫に重ねた確認用 geo を `build/unit_preview/` に作る（GeoPreview で描く）。兵姫の面と同じ平面の面を警告する |
| `gen_tactical_fx.py` | `textures/fx/*.png` | 戦術級コードの演出の素材（柔らかい光・十字の光・炎 16 コマ・煙・花びら・光の筋。Python＋numpy＋PIL）。`client/TacticalSprites` が貼る |
| `gen_gallia_fort.py` | `geo/entity/galliafort.geo.json`・`textures/entity/galliafort.png`（＋ `_glowmask.png`） | AEC 級英雄機ガリアフォート（4 本脚の移動要塞）の形とテクスチャ。`gen_chrono_gazers.py` の塗り・詰め込みを 1024×1024 で使う。実寸の半分で作り、描画で 2 倍にする |
| `gen_gallia_ammo.py` | `geo/entity/gallia_shell.geo.json`・`gallia_missile.geo.json`・`textures/entity/gallia_shell.png`・`gallia_missile.png`（＋ `_glowmask.png`） | ガリアフォートの副砲の徹甲弾とミサイル（2026-10-10）。機首を +Y に向け、真ん中を付け根に作る（`client/GalliaAmmoRenderer` が飛ぶ向きへ回す）。材質は `gen_gallia_fort.py` と同じ、テクスチャは 128×128 |
| `gen_memento_mori.py` | `geo/entity/memento_mori.geo.json`・`textures/entity/memento_mori.png`（＋ `_glowmask.png`） | AEC 級英雄機メメントモリ（背に蝶の翅を持つ細身の巨大な女性型のアンドロイド。2026-10-13 にユーザーの参考画像を元に作り直し）。丸みは `prism` / `ellipsoid`（十字に重ねた 2 つの箱の薄い輪切りを重ねる）、触角は `chain`（小さな箱の列）で作り、面は縁の線の無いなめらかな材質（`SMOOTH`）で塗る。翅は背の後ろの縦の薄い板の前後の面に翅の形の絵（黒い翅脈・紫の半透明の膜・デジタルのモザイクの四角）を描き、形の外を透けて抜く。膜は半透明で、発光マスクにも同じ不透明度で置く（GeckoLib の発光の層は下の絵の色をマスクの不透明度で描くため）。翅は面の 1px を 3 texel で描く。第 2 形態の赤い翅（`wing_*_red`）も作る。512×512。`GeoPreview` の 7 番目の引数で 1 枚の大きさを広げて確かめる |
| `gen_chrono_gazers.py` | `geo/entity/chrono_*.geo.json`・`textures/entity/chrono_*.png`（＋ `_glowmask.png`） | クロノ・ゲイザーの雑魚 3 種（索敵ドローン・自爆クモボット・重歩行戦車）の形とテクスチャ（Python＋PIL）。面ごとの UV を自動で詰めて色の板で塗る。骨の名前と pivot は Java のモデル（コードで動かす）と一致させる |
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
