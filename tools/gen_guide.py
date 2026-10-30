"""ゲーム内の説明書「戦術兵姫の手引き」の中身（2026-10-30 ユーザー指定「最後にこのMODの説明を詳細にかいて作る」→「ゲーム内での説明書をつくる」）。

章ごとに行を並べ、src/main/resources/assets/automata_valkyries/guide/<言語>.json に書く。行の書き方（client/GuideScreen が読む）:
  "## 見出し"   金色の見出し
  "- 文"        点付きの項目
  "> 文"        水色の補足・ヒント
  "@<アイテムID> 文"  左にアイテムの絵を付けた行
  ""            空行
  それ以外       ふつうの文
使い方: python tools/gen_guide.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "src/main/resources/assets/automata_valkyries/guide")
M = "automata_valkyries:"

JA = [
    ("intro", "はじめに", M + "guide_book", [
        "未来から墜ちてきた「戦術兵姫」――人の姿をした兵器の少女たちと出会い、工業と時間を操る機械を育て、侵食された世界の機械兵やボスと戦う MOD です。",
        "",
        "## この手引きの読み方",
        "- 左の章を選ぶと右に中身が出ます。マウスのホイールで読み進めます",
        "- 手引きは初めて入った時に 1 冊もらえます。なくしたら作業台で 本 ＋ クロノスクラップ から作れます",
        "",
        "## あると便利な物",
        "- JEI: レシピの確認に。設計図カードには JEI のレシピの［+］で書き込めます",
        "- 進捗（L キー）に「戦術兵姫」「クロノリンク」「戦場」の 3 つのタブがあり、次にやることの目安になります",
        "",
        "## おおまかな流れ",
        "- 墜落ポッドを探して、眠っている兵姫を目覚めさせる",
        "- 素材を集めて機械を作り、電力と回路の段を上げていく",
        "- 兵装を作って兵姫と自分に持たせ、侵食基地の機械兵とボスに挑む",
        "- クロノリンク・マシンを T4（ネバーラスト）まで育て、最強の兵装と最後の敵へ",
    ]),
    ("start", "序盤: 墜落ポッドと目覚め", M + "crashed_pod_hull", [
        "## 墜落ポッドを探す",
        "世界のあちこちに、黒焦げの墜落ポッドが半分埋まっています。中には眠っている兵姫・壁の「損傷したインターフェース」・チェストがあります。",
        "@" + M + "valkyrie_radar 兵姫探知レーダー: 右クリックで、いちばん近い眠っている兵姫の方角と距離を示します（2,000 m まで）",
        "",
        "## 兵姫を目覚めさせる",
        "@" + M + "core_proto プロトコア: 組み立て機で作ります（レシピは JEI で）",
        "- 蓄電器の充電スロットに入れて、満タン（100,000 FE）まで充電します",
        "- 満充電のプロトコアを持って、ポッドの「損傷したインターフェース」を右クリックすると、6 ブロック以内の眠っている兵姫が目覚め、あなたが持ち主になります",
        "- プロトコアの電力は兵姫に入り、プロトコアは使い切ります",
        "> 設定 earlyGame.easyPodWake を true にすると、プロトコアなしで目覚めさせられます",
        "",
        "## 目覚めた後",
        "- 兵姫を素手で右クリックするとメニューが開きます（「戦術兵姫」の章）",
        "- まずは鉱石を掘り、発電機と基本的な機械を作りましょう（「素材と鉱石」「機械と電力」の章）",
    ]),
    ("materials", "素材と鉱石", M + "ingot_astrite", [
        "## このMOD独自の鉱石",
        "@" + M + "ingot_astrite アストライト: 最初の金属。道具・機械・兵装の土台",
        "@" + M + "ingot_phononium フォノニウム",
        "@" + M + "ingot_ceruleas セルリアス",
        "@" + M + "crystal_luminal ルミナルクリスタル: ルミナル鉱石から。電力と回路の要",
        "@" + M + "drop_chrono_scrap クロノスクラップ・ナノマシン: 機械兵を倒すと落とす",
        "",
        "## 連携金属",
        "- スズ・鉛・銀・ニッケル・亜鉛・アルミニウム・オスミウム・ウランの 8 種の鉱石と、鋼鉄・青銅・真鍮・エレクトラム・インバー・コンスタンタンの 6 種の合金",
        "- forge のタグで他の工業 MOD の物と共通に使えます",
        "- 板はインゴット 2、線はインゴット 3 で作業台でも作れます",
        "> エレクトラムは合金炉がなくても、金の粉と銀の粉を作業台で混ぜて焼けば作れます",
        "",
        "## 圧縮",
        "- 9 個で 1 段上がります: ブロック → オーブ → スター → コア（1 個から 9 個に戻せます）",
        "- クロノ・ルミナル・ナノの 3 系統は機械の燃料・材料に、連携金属のオーブ・スター・コアは観測の立方体の強化に使います",
        "",
        "## 上位合金",
        "@" + M + "alloy_luminal_sterling ルミナル・スターリング合金・クロノ・フォートレス鋼・タキオン・アクセル合金・エクリプス・バイオメタル",
        "@" + M + "ingot_stellachronodyme ステラクロノダイム: 最上位の金属",
        "- どれもルミナルエナジーアダプターの「原子相転移」で作ります",
    ]),
    ("machines", "機械と電力", M + "generator", [
        "## 電力（FE）",
        "@" + M + "generator 発電機: 燃料を燃やして FE を作ります",
        "@" + M + "battery 蓄電器: 電力をためて隣へ送り、充電スロットのアイテム（プロトコアなど）を充電します",
        "@" + M + "power_cable 電力ケーブル: 離れた機械へ電力を運びます",
        "",
        "## 基本の機械",
        "@" + M + "crusher 粉砕機: 鉱石・インゴットを粉に",
        "@" + M + "electric_furnace 電気炉: 電力で精錬",
        "@" + M + "alloy_smelter 合金炉: 合金を作る",
        "@" + M + "press プレス機: 板を作る",
        "@" + M + "assembler 組み立て機: 回路・部品・プロトコアなど",
        "@" + M + "weapon_workbench 兵装ワークベンチ: 初期の兵装",
        "- それぞれに速い「改良型」があります",
        "",
        "## 兵姫を機械のそばに",
        "- 兵姫を機械のそばに置くとオペレーターになり、同調率に応じて機械が強くなります（上限は機械のティア）",
        "- 素体タイプで、速さと電力の節約のどちらに寄るかが変わります",
    ]),
    ("circuits", "回路と部品", M + "circuit_advanced", [
        "## 回路の段",
        "@" + M + "circuit_basic 基本回路: 作業台（組み立て機なら 4 個）",
        "@" + M + "circuit_advanced 高等回路: 作業台か組み立て機（2 個）。T1 の兵装・機械の多くで使う",
        "@" + M + "circuit_quantum_tac 量子TAC集積回路（T1）: 組み立て機、またはアダプター T1 の相転移（2 個）",
        "@" + M + "circuit_photonic フォトニック回路（T2）: 量子TAC 2 からアダプター T2 の相転移",
        "@" + M + "circuit_chrono_lattice 時空格子回路（T3）",
        "@" + M + "circuit_causal 因果演算回路（T4）",
        "",
        "> 各ティアの機械・兵装は、ひとつ下のティアの回路を使います（T2 の機械は量子TAC、T3 の機械はフォトニック回路）",
        "",
        "## 部品",
        "- モーター・加熱コイル・複合合金板・封入ウランセル・マシンフレーム",
        "- ティアごとの中間素材（ルミナルレンズ・クロノコイル・タキオン駆動核など）はアダプターで作ります",
    ]),
    ("chrono", "クロノリンク・マシン", M + "luminal_reactor", [
        "時間と空間を扱う機械群です。ティアを上げるほど強く、遠くへつながります。",
        "",
        "## ティア",
        "- T1 ハイエンド → T2 フューチャー → T3 ビヨンド → T4 ネバーラスト",
        "- ティアフレームを使った作業台のレシピか、アップグレードキットで上げます",
        "",
        "## 機械",
        "@" + M + "luminal_reactor ルミナル反応炉: 発電。燃料はクリスタル → ブロック → オーブ（T2）→ スター（T3）→ 創世ルミナルコア（T4）。T4 はコアの枠に創世コアを置くと燃料なしで永久に発電",
        "@" + M + "luminal_adapter ルミナルエナジーアダプター: 原子相転移で上位合金・回路・中間素材を作る",
        "@" + M + "chrono_smelter クロノ・スメルター: 鉱石を倍にして精錬",
        "@" + M + "chrono_fabricator クロノ・ファブリケータ: 差した設計図カードのレシピを、搬入リンク先の材料で自動で作る",
        "@" + M + "tether_pylon テザー・パイロン: 範囲内のクロノリンク・マシンへ送電",
        "@" + M + "void_repository ヴォイド・リポジトリ: 大容量の倉庫（下で詳しく）",
        "@" + M + "tactical_armory タクティカル・アーモリー: 兵装・兵姫の装備を組み立てる",
        "@" + M + "charging_dock 給電ドック: 兵姫の給電と修理",
        "@" + M + "chrono_accelerator 時間加速器: 周りの作物・機械の時間を進める",
        "@" + M + "chrono_rewinder 時間巻き戻し機: 物を前の姿へ戻す",
        "",
        "## リンク",
        "@" + M + "tool_chrono_tuner クロノ・チューナー: スニーク＋右クリックで先（チェストや機械）を覚え、機械を右クリックで登録。向き（IN / OUT / BOTH）は画面のリンクの印で切り替え",
        "",
        "## 設計図カード",
        "@" + M + "blueprint_card 作りたい物をオフハンドに持ってカードを右クリックで書き込み。オフハンドが空ならカードの画面が開き、JEI のレシピの［+］でも書き込めます",
        "",
        "## ヴォイド・リポジトリ",
        "- 1 種類に T1 で 4,096 個（64 スタック）、T4 は上限なし",
        "- 右上の欄で検索。JEI と同じ書き方（@MOD・#説明・$タグ・%タブ・&ID・-除外・| または）。このMODとバニラの物はひらがなやローマ字でも探せます（てつ / tetsu）",
        "- 「圧縮」を入にすると、64 個を超えた分を 9 個ずつまとめます（電力を使う）",
        "- 中の物を右クリックで「在庫の決まり」。搬出リンクの先にその数を保って配ります（電力を使う）",
        "- 電力の目盛りをクリックで、テザー・パイロンから受け取るかを切り替え（既定は受け取らない）",
    ]),
    ("valkyrie", "戦術兵姫", M + "valkyrie_body_v", [
        "## 素体タイプ",
        "- Type-V（汎用）・Type-S（強襲）・Type-H（重装）・Type-C（指揮）。タイプで性格・得意なことが違います",
        "",
        "## さわり方",
        "- 素手で右クリック: メニュー（換装・状態・作戦指示・持ち物）",
        "- 好物やリペア剤を持って右クリック: 渡す",
        "- しゃがんで頭を右クリック: なでる　／　体を右クリック: 追従 ↔ 待機",
        "",
        "## 行動モード",
        "- 追従・待機・拠点防衛・巡回。作戦指示で守る範囲や引く HP・電力も決められます",
        "",
        "## 電力と機能停止",
        "- 内蔵 100,000 FE を胸のルミナルコアから補充します。0 になると休眠し、補充されると起きます",
        "@" + M + "valkyrie_charger 兵姫用充電台・給電ドックで充電できます",
        "- HP が 0 になっても死なず、座り込んで機能停止します。ナノマシン溶液や給電ドックで修理します",
        "@" + M + "valkyrie_stretcher 戦術兵姫用担架: 動けない兵姫を頭の上に乗せて運べます",
        "",
        "## 同調率",
        "- なでる・好物・修理・満充電などのお世話で上がり、兵姫が強くなります。戦術コードの承認にも要ります",
        "",
        "## 兵姫の道具",
        "@" + M + "tactical_remote 指示リモコン: 近くの兵姫みなに指示",
        "@" + M + "motion_remote モーションリモコン: 手を振る・喜ぶなどの仕草をさせる",
        "@" + M + "emergency_recall 緊急招集装置: 追従中の兵姫をそばへ呼び寄せる",
        "@" + M + "rescue_communicator 救済通信機: 保存された兵姫を呼び出す",
        "@" + M + "valkyrie_storage 兵姫の収納テレポーター: 兵姫をしまっておく（9 体まで）",
    ]),
    ("units", "兵姫の装備", M + "backpack_thruster", [
        "メニューの「換装」で装備を付け替えます。",
        "",
        "## 付けられる物",
        "- 外装フレーム（頭・胸・脚）: 防御・HP・速さ",
        "- 背部ユニット: スラスター（空中ジャンプ）・タキオンドライブ（3 倍速）・修理ドローン など",
        "- ロジックチップ: 戦い方（前衛・後衛・精密射撃・資源節約 など）",
        "- 両手の兵装・胸のルミナルコア・弾薬庫",
        "",
        "## ルミナルコア",
        "@" + M + "core_basic 基礎 → 高密度 → 特異点励起 → クロノ・ジェネシス → ステラ・ジェネシス・オメガ。上ほど電力が多く、強い効果が付きます",
        "",
        "## 戦わない兵姫",
        "@" + M + "backpack_harvester 農業・畜産・採掘・伐採・釣り・料理・持ち主の補助のユニットを付けると、兵姫は戦わずに仕事を優先し、危なくなると持ち主のもとへ逃げます",
    ]),
    ("weapons", "兵装と強化", M + "weapon_photon_edge", [
        "## 兵装",
        "- T1〜T4 の兵装をタクティカル・アーモリーで組み立てます。電力で動き、近接・技（右クリック）・チャージがあります",
        "- 兵姫に持たせると、兵姫もその技を使います",
        "- 片手の武器を両手に持つと二刀流（左クリック = 右手、右クリック = 左手）",
        "",
        "## 兵装強化台",
        "@" + M + "upgrade_station 攻撃力・攻撃速度・チャージとクールタイム・エネルギー容量を 1 回 +10% ずつ（最大 +1000%）。材料はティアのオーブ・スター・コアとティアの金属",
        "",
        "## オーバーロード",
        "@" + M + "overload_memory オーバーロードメモリ: 回想のトライアルをすべて越えるともらえます。兵装と一緒に作業台へ置くとオーバーロード化（ダメージが大きく上がり、防御を無視）",
        "",
        "## 道具",
        "@" + M + "tool_flight_wing 光翼の飛翔機（T3）: 持っている間、電力で空を飛べます",
        "@" + M + "tool_drill_t1 採掘ドリル・チェーンソー: T4 のドリルは岩盤も掘れます",
        "",
        "## 防具",
        "- 固有金属と上位合金の防具があり、一式そろえると便利な効果が付きます",
    ]),
    ("tactical", "戦術コード", M + "tool_tac_pointer", [
        "兵姫に要請する大技です。",
        "@" + M + "tool_tac_pointer 戦術ポインター: V キー（またはしゃがみ右クリック）でコードを選び、右クリックを 1 秒長押しして離すと、見ている所へ要請します",
        "- 近くの自分の兵姫のうち、同調率と電力が足りる子が承認します",
        "",
        "## コード",
        "- CARPET［絨毯飽和爆撃］",
        "- GUNGNIR［軌道衛星集束レーザー］",
        "- ABYSS［特異点重力崩壊球］",
        "- COMET［仮想彗星］",
        "- IRIS［広域ナノ防壁・野戦病院］",
        "",
        "> 地形を壊すかは設定 enableTacticalGriefing、ボスに効くかは tacticalAffectsBosses（どちらも既定はオフ）",
    ]),
    ("tools", "便利な道具", M + "item_pocket_t1", [
        "## アイテムポケット",
        "@" + M + "item_pocket_t1 持ち物にあるだけで、中のトーテム・回復のポーション・状態のポーション・食べ物を自動で使います。収納は T1 27・T2 54・T3 108・T4 216 枠",
        "- T2: ポーションやトーテムなど重ならない物が 16 まで重なる・整理",
        "- T3: 何でも 64 まで重なる・入っている種類の物を拾うとポケットへ",
        "- T4: 手の物を使い切ったら補充・周り 16 ブロックの物と経験値を吸い寄せる・死んでも持ち物と経験値が残る・リンクしたヴォイド・リポジトリをどこからでも開ける（リポジトリをスニーク右クリックでリンク）",
        "",
        "## 観測の立方体",
        "@" + M + "akashic_cubes ホットバーか左手にあると、まわりを回る立方体が近くの敵を自動で撃ちます",
        "- 金床で連携金属のオーブ（Lv 5 まで）・スター（Lv 10 まで）・コア（Lv 15 まで）と合わせて強化。スズ = 射程、銀 = ダメージ、亜鉛 = 間隔、ニッケル = 追尾、アルミニウム = 弾の速さ、オスミウム = 貫通、鉛 = 着弾の範囲、ウラン = 同時に狙う数",
    ]),
    ("bases", "侵食基地とボス", M + "chrono_key", [
        "侵食された世界には、機械兵の軍勢「クロノゲイザー」の基地があります。奥ほど強く、報酬も良くなります。",
        "",
        "## 基地のティア",
        "- T0 倉庫 → T1 前哨基地 → T2 工場 → T3 空中庭園要塞（クロノ・バスティオン）",
        "- それぞれの奥に中ボス（前哨司令機 ウォッチャー・工場長機 ファウンドリ・マスター・要塞守護機 バスティオン・キーパー など）",
        "- 初めのリスポーン地点から遠いほど見つかりやすくなります（すぐそばでは少なく、上のティアほど遠くで増えます）",
        "@automata_valkyries:base_radar 侵食基地レーダー: 右クリックでいちばん近い侵食基地の方角と距離。スニーク＋右クリックで探す物（全部・倉庫・前哨基地・工場・要塞）を切り替え",
        "",
        "## 時の狭間",
        "@" + M + "chrono_key 時の鍵: 要塞守護機を倒した後、空中庭園要塞の眠れる時の門に使うと門が目覚め、時の狭間へ通じます",
        "- 時の狭間の奥で、総司令機 タイムルーラーが待っています",
        "> その先にも、まだ何かがあるかもしれません……",
    ]),
    ("heroes", "英雄機・エレノア・回想", M + "boss_drop_memory_stella", [
        "## 呼び出すボス",
        "@" + M + "signal_luxion 英雄機の呼び声（ルクシオン・ガリアフォート・メメントモリ）: 地面に使うと 5 秒後にボスが降りてきます",
        "- 呼び声の材料には、侵食基地の雑魚敵がまれに落とす物が要ります（プレイヤーが倒した時だけ。ドロップ増加で少し出やすくなります）",
        "@" + M + "photon_blade_shard フォトン剣の欠片（ルクシオン）: クロノ・トルーパーから 3%",
        "@" + M + "distortion_core_shard 歪曲障壁の核片（ガリアフォート）: 重歩行戦車から 5%",
        "@" + M + "black_death_cocoon 黒死蝶の繭（メメントモリ）: 仮面の観測機から 6%",
        "@" + M + "signal_akashic_seer 事象観測端末: 事象観測姫アカシックを呼び出します",
        "@" + M + "signal_elenor 紫紺の招待状: エレノアが現れます。相手の強さに合わせて強くなる強敵です",
        "- 倒したボスの素材は、強い兵装や装備の材料になります",
        "",
        "## 回想のトライアル",
        "@" + M + "boss_drop_memory_stella ルミナルステラの追憶メモリ: 右クリックで回想を選ぶ画面が開き、倒した相手と再戦できます",
        "- 回想の空間で死んでも持ち物は残ります。かかった時間と被弾でランクが付きます",
        "- EX（強化版）や連戦の回想もあります。すべて越えるとオーバーロードメモリがもらえます",
        "- 回想中にスニークしながら追憶メモリを右クリックすると、あきらめて戻ります",
    ]),
    ("settings", "設定と操作", "minecraft:comparator", [
        "## キー",
        "- V: 戦術コードの選択",
        "- H: 兵姫の HUD の表示・非表示　／　J: HUD の配置を変える画面",
        "- （操作設定で変えられます）",
        "",
        "## 主な設定（config フォルダ）",
        "- automata_valkyries-valkyries.toml: earlyGame.easyPodWake（プロトコアなしで目覚める）・enableTacticalGriefing（戦術コードで地形を壊す）・tacticalAffectsBosses（戦術コードがボスに効く）",
        "- 機械の設定: 時間加速器の重ねがけの制限や対象・巻き戻しで鉱石に戻すか（既定はしない）",
        "- クライアントの設定: ダメージの数字・ボスの位置の印・HUD",
        "",
        "## 他の MOD と一緒に",
        "- Jade: マシンを見るとティアとリンクの数、兵姫を見ると持ち主・状態・電力・シールド・同調率が出ます",
        "- Curios: アイテムポケットをベルトの枠、観測の立方体をお守りの枠、光翼の飛翔機を背中の枠に付けられます（付けたままでも動き、飛べます）",
        "- Mekanism: アストライト・フォノニウム・セルリアスの鉱石を濃縮機で粉 2 個にできます（このMODの粉砕機でも 2 個）",
        "- AE2 / Refined Storage: ストレージバスや外部ストレージでヴォイド・リポジトリや機械につなげます。ポーターのチップの兵姫は、拾った物を ME インターフェースなどへも運びます",
        "- 共通の金属（スズ・鉛など）は forge のタグで他の工業 MOD の物と同じに使えます",
        "- 基本回路・高等回路は他の MOD の同じ段の回路（Mekanism の制御回路など）と互いに代わりに使えます",
        "- Create: 破砕ホイールで独自鉱石を粉に、プレス機で連携金属の板を作れます",
        "- Immersive Engineering・Thermal: その粉砕機でも独自鉱石を粉 2 個にできます",
        "- Tinkers' Construct: 溶鉱炉でこのMODの金属（上位合金・ステラクロノダイムも）を溶かし、延べ棒・ブロックに鋳造できます（合金を混ぜて作ることはできません）",
        "- EMI: JEI と一緒に入れると、このMODのレシピの画面も EMI で見られます",
        "",
        "> 困ったら JEI でアイテムを調べ、この手引きと進捗のタブを見返してください",
    ]),
]

EN = [
    ("intro", "Introduction", M + "guide_book", [
        "Meet the Tactical Valkyries - weapon girls who fell from the future - build machines that bend industry and time, and fight the machines and bosses of a corrupted world.",
        "",
        "## Reading this guide",
        "- Pick a chapter on the left; scroll with the mouse wheel",
        "- You get one guide the first time you join. Craft another from a book + chrono scrap",
        "",
        "## Helpful extras",
        "- JEI for recipes. Blueprint cards accept JEI's [+] button",
        "- Advancements (L) have three tabs: Tactical Valkyries, Chronolink and Battlefield",
        "",
        "## The overall flow",
        "- Find a crashed pod and wake the sleeping valkyrie",
        "- Gather materials, build machines, climb the power and circuit tiers",
        "- Build weapons for yourself and your valkyries, then raid corrupted bases",
        "- Raise your Chronolink machines to T4 (Neverlast) for the strongest gear and the final foe",
    ]),
    ("start", "Early Game: Pods and Waking", M + "crashed_pod_hull", [
        "## Find a crashed pod",
        "Charred crashed pods lie half-buried around the world, each holding a sleeping valkyrie, a Damaged Interface and a chest.",
        "@" + M + "valkyrie_radar Valkyrie Radar: right-click for the direction and distance of the nearest sleeping valkyrie (up to 2,000 m)",
        "",
        "## Wake a valkyrie",
        "@" + M + "core_proto Proto Core: made in the Assembler (see JEI)",
        "- Charge it to full (100,000 FE) in a Battery's charging slot",
        "- Right-click the pod's Damaged Interface with a full Proto Core: the sleeping valkyrie within 6 blocks wakes and you become her owner",
        "> Set earlyGame.easyPodWake to true to wake them without a Proto Core",
        "",
        "## Afterwards",
        "- Right-click her empty-handed to open her menu",
        "- Mine ores and build a generator and basic machines next",
    ]),
    ("materials", "Materials and Ores", M + "ingot_astrite", [
        "## Ores unique to this mod",
        "@" + M + "ingot_astrite Astrite: the first metal, used for tools, machines and weapons",
        "@" + M + "ingot_phononium Phononium",
        "@" + M + "ingot_ceruleas Ceruleas",
        "@" + M + "crystal_luminal Luminal Crystal: from luminal ore; the heart of power and circuits",
        "@" + M + "drop_chrono_scrap Chrono scrap and nanomachines: dropped by machine enemies",
        "",
        "## Shared metals",
        "- 8 ores (tin, lead, silver, nickel, zinc, aluminum, osmium, uranium) and 6 alloys (steel, bronze, brass, electrum, invar, constantan), shared with other tech mods through forge tags",
        "- Plates (2 ingots) and wires (3 ingots) can also be crafted by hand",
        "> Electrum needs no alloy smelter: mix gold and silver dust by hand and smelt it",
        "",
        "## Compression",
        "- 9 of a tier make 1 of the next: block > orb > star > core",
        "- Shared-metal orbs, stars and cores upgrade the Observation Cubes",
        "",
        "## Higher alloys",
        "@" + M + "ingot_stellachronodyme Luminal Sterling, Chrono Fortress, Tachyon Accel, Eclipse Biometal and Stellachronodyme are made by the Luminal Adapter's phase transition",
    ]),
    ("machines", "Machines and Power", M + "generator", [
        "@" + M + "generator Generator: burns fuel for FE",
        "@" + M + "battery Battery: stores power and charges items such as the Proto Core",
        "@" + M + "power_cable Power Cable: carries power",
        "@" + M + "crusher Crusher, Electric Furnace, Alloy Smelter, Press, Assembler and Weapon Workbench - each with a faster improved version",
        "",
        "## Valkyrie operators",
        "- A valkyrie standing next to a machine boosts it based on her sync rate (capped by the machine's tier)",
    ]),
    ("circuits", "Circuits and Parts", M + "circuit_advanced", [
        "@" + M + "circuit_basic Basic Circuit > Advanced Circuit",
        "@" + M + "circuit_quantum_tac Quantum TAC Circuit (T1): Assembler or Adapter T1",
        "@" + M + "circuit_photonic Photonic Circuit (T2): from 2 Quantum TAC at Adapter T2",
        "@" + M + "circuit_chrono_lattice Chrono Lattice Circuit (T3) > Causal Circuit (T4)",
        "> Each tier's machines and weapons use the circuit one tier below",
    ]),
    ("chrono", "Chronolink Machines", M + "luminal_reactor", [
        "## Tiers",
        "- T1 High-End > T2 Future > T3 Beyond > T4 Neverlast, raised with tier frames or upgrade kits",
        "",
        "@" + M + "luminal_reactor Luminal Reactor: power. Fuel: crystal > block > orb (T2) > star (T3) > Genesis Luminal Core (T4). At T4 a Genesis Core in the core slot generates forever",
        "@" + M + "luminal_adapter Luminal Energy Adapter: phase transitions",
        "@" + M + "chrono_smelter Chrono Smelter: doubles ores",
        "@" + M + "chrono_fabricator Chrono Fabricator: crafts blueprint card recipes from linked inputs",
        "@" + M + "tether_pylon Tether Pylon: powers machines in range",
        "@" + M + "void_repository Void Repository: huge storage",
        "@" + M + "tactical_armory Tactical Armory: assembles weapons and gear",
        "@" + M + "charging_dock Charging Dock: charges and repairs valkyries",
        "@" + M + "chrono_accelerator Time Accelerator and Time Rewinder",
        "",
        "@" + M + "tool_chrono_tuner Chrono Tuner: sneak + right-click a target, then right-click a machine to link it (IN / OUT / BOTH)",
        "@" + M + "blueprint_card Blueprint Card: hold the wanted item in your offhand and right-click; with an empty offhand it opens a screen that accepts JEI's [+]",
        "",
        "## Void Repository",
        "- Search box (JEI syntax). Japanese names also match kana and romaji",
        "- Pack: compresses 9-to-1 above 64 (uses power). Right-click an item for a stock rule kept in output links (uses power)",
        "- Click the power gauge to accept power from Tether Pylons (off by default)",
    ]),
    ("valkyrie", "Tactical Valkyries", M + "valkyrie_body_v", [
        "- Types: V (all-round), S (assault), H (heavy), C (command)",
        "- Right-click empty-handed: menu. Right-click with a treat or repair item: give it",
        "- Sneak + right-click her head: pat. Her body: follow / wait",
        "- Modes: follow, wait, defend, patrol",
        "- 100,000 FE inside, refilled from her chest core. At 0 she sleeps until recharged",
        "- At 0 HP she shuts down instead of dying; repair her with nanomachine solution or a Charging Dock",
        "- Sync rate rises with care and makes her stronger; tactical codes need it",
        "@" + M + "tactical_remote Command Remote, Motion Remote, Emergency Recall, Rescue Communicator, Valkyrie Storage and Stretcher help you manage them",
    ]),
    ("units", "Valkyrie Gear", M + "backpack_thruster", [
        "- Armor frames (head, chest, legs), a back unit, logic chips, weapons in both hands, a chest core and ammo",
        "@" + M + "core_basic Cores: Basic > High-Density > Singularity > Chrono Genesis > Stella Genesis Omega",
        "@" + M + "backpack_harvester Non-combat units (farming, ranching, mining, logging, fishing, cooking, support) make her work instead of fight and flee to you when in danger",
    ]),
    ("weapons", "Weapons and Upgrades", M + "weapon_photon_edge", [
        "- T1-T4 weapons from the Tactical Armory run on power with melee, a skill (right-click) and charge attacks; valkyries use them too",
        "- One-handed weapons in both hands: dual wield",
        "@" + M + "upgrade_station Weapon Upgrade Station: +10% per upgrade to attack, speed, charge/cooldown and capacity (up to +1000%)",
        "@" + M + "overload_memory Overload Memory: from clearing every recollection trial; overloads a weapon at a crafting table",
        "@" + M + "tool_flight_wing Light Wing Flyer (T3): fly while holding it",
        "@" + M + "tool_drill_t1 Drills and chainsaws; the T4 drill breaks bedrock",
    ]),
    ("tactical", "Tactical Codes", M + "tool_tac_pointer", [
        "@" + M + "tool_tac_pointer Tactical Pointer: V (or sneak + right-click) picks a code; hold right-click 1 s and release to call it where you look",
        "- A nearby valkyrie of yours with enough sync and power approves it",
        "- CARPET, GUNGNIR, ABYSS, COMET, IRIS",
        "> enableTacticalGriefing and tacticalAffectsBosses are off by default",
    ]),
    ("tools", "Handy Items", M + "item_pocket_t1", [
        "@" + M + "item_pocket_t1 Item Pocket: auto-uses totems, potions and food from inside. 27/54/108/216 slots",
        "- T2 stacks unstackables to 16; T3 stacks to 64 and auto-collects; T4 refills your hand, has a magnet, keeps items on death and opens a linked Void Repository from anywhere",
        "@" + M + "akashic_cubes Observation Cubes: orbiting cubes shoot nearby enemies; upgrade them at an anvil with shared-metal orbs, stars and cores",
    ]),
    ("bases", "Corrupted Bases and Bosses", M + "chrono_key", [
        "- T0 warehouse > T1 outpost > T2 factory > T3 sky garden fortress, each with a boss deep inside",
        "- Bases get more common the farther you go from the world spawn (higher tiers ramp up farther out)",
        "@automata_valkyries:base_radar Corrupted Base Radar: right-click for the nearest base; sneak + right-click to choose which kind",
        "@" + M + "chrono_key Chrono Key: after the Bastion Keeper falls, wakes the fortress gate to the Time Rift, where the Time Ruler waits",
        "> Something more may lie beyond...",
    ]),
    ("heroes", "Heroes, Elenor and Recollections", M + "boss_drop_memory_stella", [
        "@" + M + "signal_luxion Hero signals (Luxion, Gallia Fort, Memento Mori), the Akashic terminal and Elenor's invitation summon bosses",
        "- Each hero signal needs a rare drop from regular base enemies (player kills only; Looting helps):",
        "@" + M + "photon_blade_shard Photon Blade Shard (Luxion): 3% from Chrono Troopers",
        "@" + M + "distortion_core_shard Distortion Core Shard (Gallia Fort): 5% from Heavy Walkers",
        "@" + M + "black_death_cocoon Black Death Cocoon (Memento Mori): 6% from Mask Drones",
        "@" + M + "boss_drop_memory_stella Memory of Luminal Stella: right-click to rematch defeated foes in recollection trials (you keep your items if you die). Clear them all for the Overload Memory",
    ]),
    ("settings", "Settings and Controls", "minecraft:comparator", [
        "- V: tactical code   H: valkyrie HUD   J: HUD layout",
        "- automata_valkyries-valkyries.toml: earlyGame.easyPodWake, enableTacticalGriefing, tacticalAffectsBosses",
        "- Machine config: Time Accelerator limits and Time Rewinder ores (off by default)",
        "## With other mods",
        "- Jade shows machine tiers and links, and valkyrie owner, state, power, shield and sync",
        "- Curios: Item Pocket in a belt slot, Observation Cubes in a charm slot, Light Wing Flyer in a back slot (fly while worn)",
        "- Mekanism: astrite, phononium and ceruleas ores double in the Enrichment Chamber",
        "- AE2 / Refined Storage connect to the Void Repository and machines through storage buses; porter-chip valkyries deliver pickups to ME interfaces too",
        "- Basic and advanced circuits are interchangeable with other mods' circuits of the same tier (e.g. Mekanism control circuits)",
        "- Create: crushing wheels turn this mod's ores into dust; the press makes shared-metal plates",
        "- Immersive Engineering / Thermal: their crushers double this mod's ores into dust too",
        "- Tinkers' Construct: melt this mod's metals (higher alloys and Stellachronodyme too) in the smeltery and cast ingots and blocks (no alloying)",
        "- EMI shows this mod's recipe screens when JEI is installed too",
    ]),
]


# ---- レシピの章（2026-10-30 ユーザー指定「推奨はJEIですが手引きの最後にも一応アイテムレシピを書いておいて」） ----
# レシピの JSON から作る（圧縮の戻し＝ID に _from_ を含む物は省く）。名前はこのMOD・バニラの言語ファイルから
ROOT = os.path.dirname(HERE)
RECIPES = os.path.join(ROOT, "src/main/resources/data/automata_valkyries/recipes")
MOD_LANG = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/lang")
ASSETS = os.path.expanduser("~/.gradle/caches/forge_gradle/assets")
VANILLA_EN = os.path.join(ROOT, ".mcsrc/assets/minecraft/lang/en_us.json")


def vanilla_lang(code):
    if code == "en_us":
        return json.load(open(VANILLA_EN, encoding="utf-8")) if os.path.exists(VANILLA_EN) else {}
    try:
        for name in os.listdir(os.path.join(ASSETS, "indexes")):
            entry = json.load(open(os.path.join(ASSETS, "indexes", name), encoding="utf-8")).get("objects", {}).get("minecraft/lang/%s.json" % code)
            if entry:
                h = entry["hash"]
                return json.load(open(os.path.join(ASSETS, "objects", h[:2], h), encoding="utf-8"))
    except OSError:
        pass
    return {}


# タグの形 → （このMODのアイテムの頭、バニラのアイテムの形）
TAG_FORMS = {"ingots": ("ingot_", "{}_ingot"), "nuggets": ("nugget_", "{}_nugget"), "dusts": ("dust_", None), "plates": ("plate_", None),
             "gears": ("gear_", None), "rods": ("rod_", None), "storage_blocks": ("block_", "{}_block"), "raw_materials": ("raw_", "raw_{}"),
             "gems": (None, "{}"), "ores": ("ore_", "{}_ore"), "dyes": (None, "{}_dye"), "circuits": ("circuit_", None)}
# 形で引けないタグ → 代わりに名前を出すバニラのアイテム
TAG_ITEMS = {"forge:chests/wooden": "chest", "forge:chests/ender": "ender_chest", "forge:chests": "chest", "forge:slimeballs": "slime_ball", "minecraft:wool": "white_wool",
             "minecraft:planks": "oak_planks", "minecraft:logs": "oak_log", "forge:glass": "glass", "forge:string": "string", "forge:feathers": "feather"}
WORDS = {
    "ja_jp": {"or": " か ", "sep": "、", "tag": "（タグ）",
              "chapter": {"craft": "レシピ: 作業台", "machine": "レシピ: 機械", "phase": "レシピ: 原子相転移", "armory": "レシピ: タクティカル・アーモリー"},
              "machine": {"crushing": "粉砕機", "alloying": "合金炉", "pressing": "プレス機", "assembling": "組み立て機", "weapon_assembling": "兵装ワークベンチ"},
              "intro": "JEI を入れると、レシピの形や使い道まで見られます。ここでは材料と数だけを並べます（作業台は形のあるレシピも数だけ）",
              "pocket": "（中身を引き継ぐ）", "open": "（", "close": "）", "join": "・"},
    "en_us": {"or": " or ", "sep": ", ", "tag": " (tag)",
              "chapter": {"craft": "Recipes: Crafting Table", "machine": "Recipes: Machines", "phase": "Recipes: Phase Transition", "armory": "Recipes: Tactical Armory"},
              "machine": {"crushing": "Crusher", "alloying": "Alloy Smelter", "pressing": "Press", "assembling": "Assembler", "weapon_assembling": "Weapon Workbench"},
              "intro": "JEI shows shapes and uses. This lists ingredients and counts only (shaped crafting recipes too)",
              "pocket": " (keeps contents)", "open": " (", "close": ")", "join": ", "},
}


def recipe_chapters(code):
    names = {}
    names.update(vanilla_lang(code))
    names.update(json.load(open(os.path.join(MOD_LANG, code + ".json"), encoding="utf-8")))
    w = WORDS[code]

    def lookup(ns, path):
        for pre in ("item", "block"):
            key = "%s.%s.%s" % (pre, ns, path)
            if key in names:
                return names[key]
        return None

    def item_name(rid):
        ns, path = rid.split(":") if ":" in rid else ("minecraft", rid)
        return lookup(ns, path) or path

    def tag_name(tag):
        ns, path = tag.split(":")
        if tag in TAG_ITEMS and lookup("minecraft", TAG_ITEMS[tag]):
            return lookup("minecraft", TAG_ITEMS[tag]) + w["tag"]
        if "/" in path:
            form, material = path.split("/", 1)
            mod_pre, van = TAG_FORMS.get(form, (None, None))
            if mod_pre and lookup("automata_valkyries", mod_pre + material):
                return lookup("automata_valkyries", mod_pre + material)
            if van and lookup("minecraft", van.format(material)):
                return lookup("minecraft", van.format(material))
        last = path.split("/")[-1]
        for guess in (last, last.rstrip("s"), last[:-2] if last.endswith("es") else last):
            if lookup("minecraft", guess):
                return lookup("minecraft", guess) + w["tag"]
        return "#" + tag

    def ing_name(ing):
        if isinstance(ing, list):
            return w["or"].join(ing_name(i) for i in ing)
        if "item" in ing:
            return item_name(ing["item"])
        if "tag" in ing:
            return tag_name(ing["tag"])
        return "?"

    def fmt(parts):
        return w["sep"].join("%s ×%d" % (n, c) for n, c in parts)

    def result(r):
        res = r.get("result") or r.get("output")
        if isinstance(res, str):
            return res, 1, ""
        rid, count = res.get("item", "?"), res.get("count", 1)
        tier = ""
        nbt = res.get("nbt", "")
        if "tier:" in nbt:
            digits = "".join(ch for ch in nbt.split("tier:")[1][:6] if ch.isdigit())
            if digits:
                tier = " T" + digits[0]
        return rid, count, tier

    groups = {"craft": [], "machine": [], "phase": [], "armory": []}
    tiers = {}
    for dp, _, fs in os.walk(RECIPES):
        for f in sorted(fs):
            if "_from_" in f or not f.endswith(".json"):
                continue
            r = json.load(open(os.path.join(dp, f), encoding="utf-8"))
            t = r.get("type", "").split(":")[-1]
            try:
                rid, count, tier = result(r)
            except Exception:
                continue
            if not rid.startswith("automata_valkyries:"):
                continue
            head = "@%s %s%s%s ← " % (rid, item_name(rid), tier, " ×%d" % count if count > 1 else "")
            if t in ("crafting_shaped", "crafting_shapeless", "pocket_upgrade"):
                parts = []
                if "key" in r:
                    pattern = "".join(r.get("pattern", []))
                    for ch, ing in r["key"].items():
                        parts.append((ing_name(ing), pattern.count(ch)))
                else:
                    merged = {}
                    for ing in r.get("ingredients", []):
                        n = ing_name(ing)
                        merged[n] = merged.get(n, 0) + 1
                    parts = list(merged.items())
                groups["craft"].append(head + fmt(parts) + (w["pocket"] if t == "pocket_upgrade" else ""))
            elif t in w["machine"] or t in ("phase_transition", "armory_assembling"):
                parts = []
                for ing in r.get("ingredients", []):
                    inner = ing.get("ingredient", ing)
                    parts.append((ing_name(inner), ing.get("count", 1)))
                info = []
                if t in w["machine"]:
                    info.append(w["machine"][t])
                if "tier" in r:
                    info.append("T%d" % r["tier"])
                if r.get("energy"):
                    info.append("{:,} FE".format(r["energy"]))
                line = head + fmt(parts) + (w["open"] + w["join"].join(info) + w["close"] if info else "")
                key = "machine" if t in w["machine"] else "phase" if t == "phase_transition" else "armory"
                groups[key].append(line)
                tiers[line] = r.get("tier", 0)
    icons = {"craft": "minecraft:crafting_table", "machine": "automata_valkyries:assembler", "phase": "automata_valkyries:luminal_adapter",
             "armory": "automata_valkyries:tactical_armory"}
    out = []
    for key in ("craft", "machine", "phase", "armory"):
        lines = sorted(groups[key], key=lambda l: (tiers.get(l, 0), l))
        out.append(("recipes_" + key, w["chapter"][key], icons[key], ["> " + w["intro"], ""] + lines))
    return out


JA += recipe_chapters("ja_jp")
EN += recipe_chapters("en_us")


def write(lang, chapters):
    os.makedirs(OUT, exist_ok=True)
    data = {"chapters": [{"id": i, "title": t, "icon": icon, "lines": lines} for i, t, icon, lines in chapters]}
    with open(os.path.join(OUT, lang + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")


write("ja_jp", JA)
write("en_us", EN)
print(len(JA), "chapters (ja),", len(EN), "chapters (en)")
