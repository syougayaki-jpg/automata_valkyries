"""
AEC 級ボス 最光速『ルクシオン』の GeckoLib モデルとテクスチャを作る（2026-10-08）。

    python tools/gen_luxion.py

出力: geo/entity/luxion.geo.json、textures/entity/luxion.png（＋ _glowmask.png、256x256・1px に 3 texel。paint_hd で細かく塗る）。
箱・色の板・面ごとの UV の自動の詰め込みは tools/gen_chrono_gazers.py のものを使う（色を足すだけ）。
アニメーションの json は無い（Java のモデルが骨をコードで動かす）ので、骨の名前と pivot は Java 側と一致させること:
  root > body（腰 0,20,0）> head（首 0,32,0）> tail（兜の後ろ）/ crest（動かさない）
                          > right_arm（肩 -5.5,30,0）> blade（拳）> blade_tilt（前・下へ傾ける。動かさない）
                          > left_arm（肩 5.5,30,0）
                          > thrusters（背中の上 0,28,2.5）> wing_right（-2.5,29,3）/ wing_left（2.5,29,3）（翼の開閉。傾きは *_tilt に）
                                                          > booster（戦闘機の形の主推進器 0,28,3.5。rotX で後ろへ展開）
                                                              > booster_core（真ん中の芯。動かさない）
                                                              > booster_pod_top / booster_pod_left / booster_pod_right（0,29.9,7.2 = 機首の先。噴射口の側を外へ開いて正四面体に）
                                                                  > booster_pod_left_frame / booster_pod_right_frame（±120°。動かさない）
                                                                  > booster_wing_left（左ポッド）/ booster_wing_right（右ポッド）（主翼。rotZ で広げる）
                                                                  > booster_fin_left / booster_fin_right（上ポッド）（尾翼。rotZ で起こす）
                          > cannon（右肩の上の光の槍 -4,33,2。rotX で上下）
       > right_leg（-2.2,20,0）/ left_leg（2.2,20,0）
動かす骨には初期の向きを付けない（傾きは子の骨 blade_tilt・fin_*・calf_fin_* に付ける）。
見た目: 細身の機械の騎士。クロノ・ゲイザーのガンメタルに白金の装甲（白 0xF4F0E4・金の縁 0xE8C060）、
冷たい赤の一つ目のバイザー・関節・刃の縁、兜の後ろから流れる白金の光の尾（発光）、背中とふくらはぎの後退したひれ。
2026-10-08 に強化: 重ねた広い胸・大きな肩当て・太い前腕と脛・足首の噴射口、背中の大きな光の翼（金の桁に白金の光の羽 4 枚ずつ）、右肩の上の長い光の槍。
一般的なメカの考え方だけを使い、特定の機体の形は写さない（DESIGN_VALKYRIE_VISUAL_DB §8）。
2026-10-08 に作り直し: 流線形の兜（前へ細く、頬板は後ろへ流す）、角度の付いた胸当て・竜骨・斜めの腹の板、噴射口から光の刃が出る翼。
板は箱ごとの回転（10〜20°、左右反転）で斜めにし、軸に沿った角ばった線を減らす。動かす骨には回転を付けない。
"""
import importlib.util
import os

_spec = importlib.util.spec_from_file_location('gen_chrono_gazers', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gen_chrono_gazers.py'))
g = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(g)

g.MAT.update({
    'wplate': (0xF4F0E4, 0xFFFFFF, 0xC8C0A8, 'panel', False),   # 白金の装甲
    'wsmooth': (0xF4F0E4, 0xFFFFFF, 0xC8C0A8, 'plain', False),
    'gold': (0xE8C060, 0xFADC78, 0x966E1E, 'plain', False),
    'champ': (0xD8C890, 0xEEE2B0, 0xA89860, 'rivet', False),    # 金がかった板
    'wglow': (0xFFF4CC, 0xFFFFFF, 0xE8C060, 'glow', True),      # 白金の光（尾・刃）
})


def luxion():
    m = g.Model('luxion')
    B, MB = m.box, m.mbox
    m.bone('root')
    m.bone('body', 'root', (0, 20, 0))
    # ---- 胴: 細い腰、角度の付いた胸当て・中心の竜骨・重ねた腹の板（赤いコアは金の枠に）
    B('body', (-2.9, 19.0, -1.7), (2.9, 21.5, 1.7), 'metal', front='vent')                                  # 腰
    MB('body', (-3.3, 16.6, -2.35), (-0.35, 21.0, -1.9), 'wplate', front='champ', rot=(-12, 0, 14), pivot=(-1.8, 21.0, -2.1))   # 前の草摺（外・前へ開く）
    MB('body', (-3.9, 17.0, -1.5), (-3.2, 21.0, 1.5), 'champ', out='gold', rot=(0, 0, 16), pivot=(-3.2, 21.0, 0))              # 横の草摺
    MB('body', (-2.6, 17.4, 1.85), (-0.4, 21.0, 2.3), 'wplate', rot=(12, 0, 12), pivot=(-1.5, 21.0, 2.0))                     # 後ろの草摺
    B('body', (-2.1, 21.4, -1.35), (2.1, 24.05, 1.35), 'dark')                                             # 細い胴
    for i, y in enumerate((21.6, 22.55, 23.5)):                                                            # 重ねた腹の板（斜め）
        MB('body', (-2.3, y, -2.0 - i * 0.05), (-0.15, y + 1.05, -1.4), 'wplate' if i % 2 == 0 else 'champ', rot=(-14, -10, 6), pivot=(-0.15, y + 1.05, -1.7))
    B('body', (-3.4, 24.0, -2.0), (3.4, 30.3, 2.2), 'metal', back='vent')                                  # 胸の骨組み
    MB('body', (-4.3, 26.8, -3.0), (-0.35, 30.0, -1.9), 'wplate', front='wsmooth', top='gold',
       rot=(12, -16, -8), pivot=(-0.35, 28.4, -2.4))                                                       # 胸当て（外・前へ傾ける）
    MB('body', (-4.0, 24.3, -2.7), (-0.6, 26.8, -1.8), 'champ', rot=(-10, -14, 6), pivot=(-0.6, 25.5, -2.2))  # 胸の下の板
    MB('body', (-4.5, 24.6, -1.6), (-3.3, 29.8, 1.8), 'wplate', out='champ', rot=(0, 0, -10), pivot=(-3.3, 29.8, 0))  # 脇の板
    B('body', (-0.45, 26.9, -3.35), (0.45, 30.0, -2.35), 'champ', top='gold', rot=(0, 45, 0))             # 中心の竜骨（角を前へ）
    B('body', (-0.95, 24.9, -3.25), (0.95, 26.75, -2.5), 'gold', rot=(10, 0, 0), pivot=(0, 25.8, -2.9))   # コアの金の枠
    B('body', (-0.6, 25.2, -3.4), (0.6, 26.45, -3.2), 'glow', rot=(10, 0, 0), pivot=(0, 25.8, -2.9))       # 胸の赤い光
    MB('body', (-2.9, 30.2, -1.8), (-0.3, 31.4, 1.6), 'champ', top='gold', rot=(-8, 0, 14), pivot=(-0.3, 30.2, 0))   # 斜めの襟
    B('body', (-0.8, 31.3, -0.8), (0.8, 32.3, 0.8), 'metal')                                               # 首

    # ---- 頭: 前へ細くなる流線形の兜、後ろへ流れる重ねた頬板、傾けた飾り（一つ目の赤いバイザー）
    m.bone('head', 'body', (0, 32, 0))
    # 菱形の頭（2026-10-08）: 正面から見て、バイザーの高さがいちばん広く、上は飾りへ、下は顎へ尖る。Z で 45° 回した箱を重ねる
    def D(cx, cy, side, z0, z1, mat, **f):
        h = side / 2
        B('head', (cx - h, cy - h, z0), (cx + h, cy + h, z1), mat, rot=(0, 0, 45), pivot=(cx, cy, (z0 + z1) / 2), **f)
    D(0, 34.2, 3.25, -1.9, 1.7, 'wplate', front='wsmooth', back='champ')                                   # 頭の菱形（幅 ±2.3、y 31.9〜36.5）
    D(0, 32.05, 1.3, -2.35, 0.6, 'champ', front='wplate')                                                 # 尖った顎
    D(0, 36.45, 1.1, -1.3, 1.4, 'gold')                                                                   # 上の尖り（飾りへつながる）
    D(0, 34.3, 1.8, 1.6, 2.35, 'champ')                                                                   # 後頭部（光の尾の付け根）
    # 後頭部の突起（2026-10-08 ユーザー指定「後頭部の突起を上斜め後ろにも生やして」）: 光の尾の付け根の左右から上斜め後ろへ伸びる 2 本と、真ん中の短い 1 本。先が白金に光る。左右は外へ開く向き（同日「扇状に広がらなかったので角度を逆に」で Z の傾きを反対に）
    MB('head', (-1.35, 35.0, 1.7), (-0.75, 39.2, 2.3), 'gold', rot=(-48, 0, -14), pivot=(-1.05, 35.0, 2.0))
    MB('head', (-1.25, 39.2, 1.8), (-0.85, 40.2, 2.2), 'wglow', rot=(-48, 0, -14), pivot=(-1.05, 35.0, 2.0))
    MB('head', (-1.6, 34.6, 1.55), (-0.5, 35.6, 2.45), 'wplate', rot=(-48, 0, -14), pivot=(-1.05, 35.0, 2.0))     # 付け根の白金の板
    B('head', (-0.25, 35.4, 2.0), (0.25, 38.0, 2.5), 'gold', rot=(-58, 0, 0), pivot=(0, 35.4, 2.25))
    B('head', (-0.18, 38.0, 2.07), (0.18, 38.7, 2.43), 'wglow', rot=(-58, 0, 0), pivot=(0, 35.4, 2.25))
    MB('head', (-2.93, 35.3, -1.95), (0.37, 35.65, 0.3), 'gold', rot=(0, 0, -45), pivot=(-1.28, 35.48, 0))     # 上の縁（金）
    MB('head', (-2.93, 32.68, -1.3), (0.37, 33.18, 2.1), 'champ', out='wplate', rot=(0, 0, 45), pivot=(-1.28, 32.93, 0))   # 下の縁の頬板（後ろへ流す）
    MB('head', (-2.7, 33.85, -1.4), (-2.0, 34.55, 1.0), 'gold', rot=(0, 0, 45), pivot=(-2.35, 34.2, 0))   # 横の尖り
    # 前へ尖る三角の舳先（2026-10-08）: 段々に小さくなる菱形を前へ重ね、上・下・左右の稜線を先端へ集めて四角錐にする。
    # 先端はバイザーの高さ（y 34.2）で z -4.2。横から見ても上から見ても三角に尖る
    for hd, z0, z1, mat in ((1.9, -2.5, -1.85, 'wplate'), (1.4, -3.1, -2.45, 'wsmooth'), (0.9, -3.7, -3.05, 'wplate'), (0.45, -4.2, -3.65, 'champ')):
        D(0, 34.2, hd * 2 / 1.4142, z0, z1, mat)
    B('head', (-0.35, 33.5, -3.05), (0.35, 37.0, -2.55), 'gold', rot=(-53, 0, 0), pivot=(0, 35.25, -2.8))      # 上の稜線（額から先端へ下がる）
    B('head', (-0.3, 31.2, -3.15), (0.3, 34.4, -2.65), 'champ', rot=(43, 0, 0), pivot=(0, 32.8, -2.9))        # 下の稜線（顎から先端へ上がる）
    MB('head', (-3.15, 33.92, -2.95), (0.71, 34.48, -2.45), 'slit', top='dark', bottom='dark',
       rot=(0, 51, 0), pivot=(-1.22, 34.2, -2.7))                                                          # 赤い一つ目のバイザー（左右の稜線に沿う V 字、先端で交わる）
    MB('head', (-3.1, 34.5, -2.85), (0.6, 34.75, -2.4), 'gold', rot=(0, 51, 0), pivot=(-1.25, 34.62, -2.62))   # バイザーの上の金の縁
    B('head', (-0.28, 33.95, -4.45), (0.28, 34.45, -4.1), 'glow')                                         # 先端の赤い光
    # バイザーの上の三角の庇（2026-10-08）: バイザーの V に沿って前へ張り出す矢じり形の板。下の縁が白金に光り、バイザーは庇の下に沈んで見える
    # 2026-10-08「キャップをもう少し前方に」: 庇の部品をまとめて 1px 前へ（同日「もう 1 ピクセル」でさらに 1px 出したが、戻して合計 1px）
    cap = dict(rot=(10, 51, 0), pivot=(-1.22, 34.95, -3.6))
    MB('head', (-3.3, 34.8, -4.35), (1.5, 35.12, -2.85), 'wplate', top='wsmooth', bottom='champ', **cap)      # 庇の板（先端より少し前へ）
    B('head', (-1.35, 34.82, -5.0), (1.35, 35.1, -2.3), 'wplate', top='wsmooth', bottom='champ', rot=(10, 45, 0), pivot=(0.0, 34.96, -3.65))   # 真ん中を埋める板（上から見て矢じり）
    MB('head', (-3.3, 35.12, -4.37), (0.95, 35.24, -4.0), 'gold', **cap)                                     # 上の金の縁
    MB('head', (-3.2, 34.72, -4.38), (0.9, 34.8, -4.2), 'wglow', **cap)                                      # 下の縁の白金の光（バイザーを向く）
    D(0, 35.2, 0.95, -5.25, -4.55, 'gold')                                                                   # 前から見て下を向く三角の先（バイザーの上で止める）
    cr = m.bone('crest', 'head', (0, 35.9, -0.6), (-28, 0, 0))                                             # 傾けた飾り
    B(cr, (-0.25, 35.9, -1.4), (0.25, 40.6, -0.6), 'gold')
    B(cr, (-0.18, 40.6, -1.3), (0.18, 41.4, -0.7), 'wglow')
    MB(cr, (-0.65, 35.8, -0.7), (-0.25, 39.0, 0.1), 'wplate', rot=(0, 0, 12), pivot=(-0.25, 35.8, -0.3))   # 飾りの横の刃
    m.bone('tail', 'head', (0, 35, 2.0))                                                                   # 光の尾
    for i, (w, y0, y1, z0, z1) in enumerate(((0.9, 33.0, 35.2, 2.0, 2.6), (0.8, 30.5, 33.2, 2.55, 3.1), (0.7, 27.5, 30.7, 3.0, 3.55),
                                             (0.55, 24.5, 27.7, 3.45, 3.95), (0.4, 21.5, 24.7, 3.85, 4.25), (0.25, 19.6, 21.7, 4.15, 4.45))):
        B('tail', (-w, y0, z0), (w, y1, z1), 'wglow')

    # ---- 腕: 斜めに重ねた肩当て、外・前へ開く前腕の板
    for name, s in (('right_arm', -1), ('left_arm', 1)):
        m.bone(name, 'body', (s * 5.5, 30, 0))
        def A(a, c, mat, rot=None, pivot=None, **f):
            r = (rot[0], s * -rot[1], s * -rot[2]) if rot else None                      # -x 側の値で書く
            p = (s * -pivot[0], pivot[1], pivot[2]) if pivot else None
            B(name, (s * -a[0], a[1], a[2]), (s * -c[0], c[1], c[2]), mat, rot=r, pivot=p, **f)
        A((-8.0, 28.6, -2.0), (-4.4, 31.4, 2.0), 'wplate', top='gold', out='champ', rot=(0, 0, -15), pivot=(-4.4, 31.0, 0))   # 肩当て（外へ下がる）
        A((-8.3, 30.4, -1.7), (-5.0, 32.0, 1.7), 'champ', top='gold', rot=(0, 0, -20), pivot=(-5.0, 31.8, 0))                 # 上に重ねた板
        A((-8.0, 28.0, -2.5), (-5.2, 30.6, -2.05), 'wplate', rot=(0, -14, -12), pivot=(-5.2, 30.6, -2.3))                       # 前の板
        A((-8.0, 28.0, 2.05), (-5.2, 30.6, 2.5), 'wplate', rot=(0, 14, -12), pivot=(-5.2, 30.6, 2.3))                          # 後ろの板
        A((-8.8, 26.8, -1.4), (-8.2, 30.4, 1.4), 'gold', rot=(0, 0, -18), pivot=(-8.2, 30.4, 0))                               # 外の金の刃
        A((-6.3, 24.5, -0.9), (-4.7, 28.45, 0.9), 'metal')                                                                     # 上腕
        A((-6.5, 23.8, -1.0), (-4.5, 24.8, 1.0), 'dark', front='glow', out='glow')                                              # 肘（赤い光）
        A((-6.4, 18.4, -1.1), (-4.6, 23.85, 1.1), 'metal')                                                                     # 前腕の芯
        A((-7.2, 18.6, -1.5), (-6.3, 23.4, 1.5), 'wplate', out='champ', rot=(0, 0, 12), pivot=(-6.3, 23.4, 0))                 # 前腕の外の板（下が外へ）
        A((-6.7, 18.8, -1.8), (-4.3, 23.0, -1.1), 'champ', rot=(-14, 0, 0), pivot=(-5.5, 23.0, -1.1))                          # 前腕の前の板（下が前へ）
        A((-7.0, 22.4, -1.6), (-4.0, 23.0, 1.6), 'gold', rot=(0, 0, 8))                                                        # 金の輪
        A((-7.32, 19.4, -0.5), (-7.22, 21.8, 0.5), 'glow', rot=(0, 0, 12), pivot=(-6.3, 23.4, 0))                              # 前腕の赤い線
        A((-6.2, 16.8, -1.0), (-4.8, 18.45, 1.0), 'dark')                                                                      # 拳
    m.bone('blade', 'right_arm', (-5.5, 17.8, 0))                                                          # 光の剣
    t = m.bone('blade_tilt', 'blade', (-5.5, 17.8, 0), (-60, 0, 0))
    B(t, (-5.85, 15.2, -0.35), (-5.15, 18.3, 0.35), 'dark')
    B(t, (-6.6, 14.8, -1.0), (-4.4, 15.25, 1.0), 'gold', bottom='glow')
    MB(t, (-7.0, 14.6, -0.4), (-6.4, 15.3, 0.4), 'gold', rot=(0, 0, -20), pivot=(-6.4, 15.0, 0))         # 鍔の反り
    B(t, (-5.85, -5.2, -0.2), (-5.15, 14.8, 0.2), 'wglow')
    B(t, (-5.62, -5.0, -0.28), (-5.38, 14.6, -0.19), 'glow')
    B(t, (-5.7, -6.0, -0.12), (-5.3, -5.15, 0.12), 'wglow')

    # ---- 右肩の上の長い光の槍
    m.bone('cannon', 'body', (-4, 33, 2))
    B('cannon', (-4.8, 32.2, 1.0), (-3.2, 34.0, 3.4), 'metal', top='gold')
    B('cannon', (-4.5, 32.6, -9.0), (-3.5, 33.6, 1.05), 'wplate', top='champ')
    B('cannon', (-4.6, 32.5, 3.35), (-3.4, 33.7, 7.6), 'metal', back='vent')
    B('cannon', (-4.8, 32.3, -10.2), (-3.2, 33.9, -8.95), 'gold', front='eye')
    B('cannon', (-4.65, 32.9, -8.5), (-4.55, 33.3, 0.5), 'glow')
    B('cannon', (-4.75, 32.45, -3.2), (-3.25, 33.75, -2.4), 'gold')
    B('cannon', (-4.4, 33.55, -6.5), (-3.6, 34.3, -1.0), 'champ', rot=(-6, 0, 0))                        # 上の斜めの板
    B('cannon', (-5.3, 32.5, 1.5), (-4.75, 33.7, 5.5), 'wplate', rot=(0, 12, 0))                         # 横のひれ

    # ---- 背中: 推進器・ひれ・噴射口から出る光の翼
    m.bone('thrusters', 'body', (0, 28, 2.5))
    B('thrusters', (-2.6, 24.2, 2.15), (2.6, 30.0, 3.1), 'metal', back='vent', top='gold')
    MB('thrusters', (-2.4, 25.0, 3.0), (-0.2, 29.6, 3.6), 'wplate', rot=(10, 0, 10), pivot=(-0.2, 29.6, 3.1))   # 背中の斜めの板
    MB('thrusters', (-1.7, 23.6, 2.3), (-0.6, 24.25, 2.9), 'glow')
    for name, s in (('fin_right', -1), ('fin_left', 1)):
        m.bone(name, 'thrusters', (s * 1.85, 28.5, 2.8), (20, s * 20, 0))   # 2026-10-08 から空（戦闘機の推進器に当たるので。骨の名前は残す）
    # ---- 三つに分かれる戦闘機の形の主推進器（2026-10-08）: たたむと背中に縦に沿う 1 機の戦闘機（機首が上、噴射口が下）。
    # 展開ではコードが booster を rotX で後ろへ振り出し、3 つのポッドを胴の軸から放射方向へ平行移動して、
    # 後ろから見て噴射口が正三角形の頂点に並ぶ（真ん中に芯が残る）。ポッドは 1/3 ずつの胴で、それぞれ大きな噴射口を 1 つ持つ。
    # 胴の軸は x 0・z ZC（光の尾より後ろ）。上ポッドの形を作り、左右は胴の軸の周りに ±120° 回した子の骨（動かさない）で同じ形を置く
    ZC, YM = 7.2, 23.0
    # 2026-10-08「噴出口の逆の根本は収束し正四面体のように」: ポッドの回転の中心を機首の先（根本）に置き、コードが噴射口の側を外へ開く（根本は 1 点に集まる）
    YA = 29.9
    bo = m.bone('booster', 'thrusters', (0, 28, 3.5))
    MB(bo, (-1.7, 26.4, 2.9), (-1.1, 27.6, 5.1), 'metal', top='gold')                                     # 取り付けの支柱（光の尾の横）
    B(bo, (-1.7, 26.6, 4.7), (1.7, 27.4, 5.3), 'metal', top='gold')                                        # 支柱をつなぐ横木（光の尾の後ろ）
    co = m.bone('booster_core', bo, (0, YM, ZC))                                                           # 真ん中の芯（動かさない）
    B(co, (-0.38, 15.6, ZC - 0.38), (0.38, 29.6, ZC + 0.38), 'metal', top='gold')
    for dx, dz in ((0.36, 0.36), (-0.36, 0.36), (0.36, -0.36), (-0.36, -0.36)):                            # 光る白金のレール
        B(co, (dx - 0.07, 16.0, ZC + dz - 0.07), (dx + 0.07, 29.0, ZC + dz + 0.07), 'wglow')
    for y in (18.0, 23.0, 27.6):                                                                           # ポッドへつなぐ小さな梁
        B(co, (-0.75, y - 0.25, ZC - 0.75), (0.75, y + 0.25, ZC + 0.75), 'gold', rot=(0, 45, 0), pivot=(0, y, ZC))
    B(co, (-0.85, 29.25, ZC - 0.6), (0.85, 30.95, ZC + 0.6), 'gold', rot=(0, 0, 45), pivot=(0, 30.1, ZC))   # 尖った機首

    def pod(frame, top):
        """上ポッドの形（胴の軸から +Z 側の 1/3）。frame は置く骨。top が真なら上だけの飾り"""
        z0, z1 = ZC + 0.15, ZC + 2.6
        B(frame, (-1.45, 17.2, z0), (1.45, 28.2, z1), 'wplate', back='wsmooth', top='gold')                  # 胴の殻
        B(frame, (-1.2, 20.5, z1 - 0.1), (1.2, 27.8, z1 + 0.35), 'wplate', back='champ', rot=(-6, 0, 0), pivot=(0, 27.8, z1))   # 外の斜めの板
        B(frame, (-1.0, 28.15, z0 + 0.1), (1.0, 29.9, z1 - 0.7), 'wplate', top='gold', rot=(20, 0, 0), pivot=(0, 28.2, z0 + 1.0))   # 機首へ細くなる
        B(frame, (-1.6, 16.4, z0 + 0.05), (1.6, 20.7, z1 + 0.3), 'champ', back='wplate', top='gold')         # 機関部
        B(frame, (-1.65, 20.6, z0), (1.65, 21.0, z1 + 0.4), 'gold')                                          # 金の帯
        for sx in (-1, 1):                                                                                   # 内側の面（ほかのポッドと合わさる所）の光る継ぎ目
            B(frame, (sx * 1.44, 17.4, z0 + 0.15), (sx * 1.5, 28.0, z0 + 0.45), 'wglow')
            B(frame, (sx * 1.59, 16.6, z0 + 0.2), (sx * 1.65, 20.5, z0 + 0.5), 'wglow')
        B(frame, (-0.2, 17.4, z0 - 0.06), (0.2, 28.0, z0), 'wglow')                                         # 芯に面した光る継ぎ目
        B(frame, (-0.3, YM - 0.5, ZC + 0.3), (0.3, YM + 0.5, z0 + 0.05), 'metal')                            # 芯へつなぐ小さな梁
        if top:
            B(frame, (-0.5, 23.4, z1 + 0.3), (0.5, 27.6, z1 + 0.6), 'wglow', rot=(-6, 0, 0), pivot=(0, 27.8, z1))   # 光の帯（センサー）
        else:
            B(frame, (-0.9, 21.8, z1 + 0.25), (0.9, 24.4, z1 + 0.65), 'dark', back='vent', top='gold')        # 吸気口
        nz = dict(rot=(10, 0, 0), pivot=(0, 16.4, ZC + 1.4))                                                 # 大きな噴射口（外へ 10°）
        B(frame, (-0.9, 14.4, ZC + 0.5), (0.9, 16.45, ZC + 2.3), 'metal', bottom='wglow', **nz)
        B(frame, (-1.0, 14.1, ZC + 0.4), (1.0, 14.5, ZC + 2.4), 'gold', bottom='wglow', **nz)
        B(frame, (-0.62, 13.6, ZC + 0.8), (0.62, 14.12, ZC + 2.0), 'wglow', **nz)

    pt = m.bone('booster_pod_top', bo, (0, YA, ZC))                                                          # 上ポッド（+Z）
    pod(pt, True)
    pl = m.bone('booster_pod_left', bo, (0, YA, ZC))                                                         # 左ポッド（+X・-Z）
    pod(m.bone('booster_pod_left_frame', pl, (0, YM, ZC), (0, 120, 0)), False)
    pr = m.bone('booster_pod_right', bo, (0, YA, ZC))                                                        # 右ポッド（-X・-Z）
    pod(m.bone('booster_pod_right_frame', pr, (0, YM, ZC), (0, -120, 0)), False)
    for name, s, parent in (('booster_wing_right', -1, pr), ('booster_wing_left', 1, pl)):                    # 主翼（左右のポッドの外に、たたんで沿う）
        wx = 3.6
        m.bone(name, parent, (s * wx, 23.4, 6.0))
        wing = [((wx + 0.15, 19.4, 4.6), (wx - 0.05, 23.4, 7.9)), ((wx + 0.13, 17.0, 5.2), (wx - 0.03, 19.45, 7.9)),
                ((wx + 0.11, 15.6, 6.1), (wx - 0.01, 17.05, 7.9))]
        for (ax, ay, az), (cx_, cy_, cz) in wing:
            B(name, (s * ax, ay, az), (s * cx_, cy_, cz), 'wplate', out='wsmooth')
            B(name, (s * (ax + 0.03), ay, az - 0.22), (s * (cx_ - 0.03), cy_, az + 0.02), 'gold')          # 金の前縁
        B(name, (s * (wx + 0.12), 15.6, 7.88), (s * (wx - 0.02), 23.3, 8.08), 'wglow')                     # 光る後縁
        B(name, (s * (wx + 0.2), 22.6, 5.4), (s * (wx - 0.1), 23.6, 6.8), 'metal', out='gold')             # 翼の付け根
    for name, s in (('booster_fin_right', -1), ('booster_fin_left', 1)):                                     # 尾翼（上ポッドの背、内へたたむ）
        fz = ZC + 2.9
        m.bone(name, pt, (s * 1.0, 17.6, fz))
        fin = dict(rot=(0, 0, s * -30), pivot=(s * 1.0, 17.6, fz))
        B(name, (s * 1.12, 17.6, fz - 0.05), (s * 0.88, 21.0, fz + 1.7), 'wplate', out='champ', top='gold', **fin)
        B(name, (s * 1.14, 17.6, fz + 1.65), (s * 0.86, 20.8, fz + 1.85), 'wglow', **fin)                 # 光る縁
    for name, s in (('wing_right', -1), ('wing_left', 1)):
        m.bone(name, 'thrusters', (s * 2.5, 29, 3))                                                       # 翼の根元（コードで開閉）
        B(name, (s * 3.3, 28.0, 2.9), (s * 1.9, 30.2, 4.1), 'metal', top='gold')
        wt = m.bone(name + '_tilt', name, (s * 2.5, 29, 3), (0, s * -10, s * -38))                        # 翼の骨組み（外・上・後ろへ）
        B(wt, (s * 2.6, 28.6, 3.0), (s * 12.5, 29.6, 3.8), 'metal', top='gold', bottom='dark')
        B(wt, (s * 3.0, 29.55, 3.1), (s * 12.2, 30.0, 3.7), 'gold')
        B(wt, (s * 12.4, 28.3, 2.95), (s * 13.4, 30.3, 3.85), 'champ', out='gold', rot=(0, 0, s * -15))  # 先の飾り
        # 骨組みの上の噴射口（暗い筒＋赤い縁）から、白金の光が刃の形に噴き出して翼になる
        for k, (x0, rel, ln) in enumerate(((4.6, -18, 12.0), (7.2, -36, 13.5), (9.8, -56, 13.0), (12.2, -76, 11.0))):
            z = 3.05 + k * 0.17
            fb = m.bone(f'{name}_feather_{k}', wt, (s * x0, 29.0, z + 0.2), (0, 0, s * -rel))
            B(fb, (s * (x0 - 0.4), 28.2, z - 0.25), (s * (x0 + 1.4), 29.8, z + 0.65), 'metal', out='glow', top='gold')   # 噴射口
            B(fb, (s * (x0 + 1.4), 28.35, z - 0.1), (s * (x0 + 1.6), 29.65, z + 0.5), 'gold')                             # 口の金の輪
            B(fb, (s * (x0 + 1.6), 28.55, z + 0.05), (s * (x0 + 2.6), 29.45, z + 0.35), 'wglow')                          # 噴き出す光（細い）
            B(fb, (s * (x0 + 2.6), 28.0, z + 0.07), (s * (x0 + 1.6 + ln * 0.62), 30.0, z + 0.33), 'wglow')               # 光の刃（広がる）
            B(fb, (s * (x0 + 1.6 + ln * 0.62), 28.3, z + 0.09), (s * (x0 + 1.6 + ln * 0.88), 29.7, z + 0.31), 'wglow')
            B(fb, (s * (x0 + 1.6 + ln * 0.88), 28.6, z + 0.11), (s * (x0 + 1.6 + ln), 29.4, z + 0.29), 'wglow')           # 光の先（細く）

    # ---- 脚: 斜めの太もも・膝・脛の板、足首の噴射口
    for name, s in (('right_leg', -1), ('left_leg', 1)):
        m.bone(name, 'root', (s * 2.2, 20, 0))
        def L(a, c, mat, rot=None, pivot=None, **f):
            r = (rot[0], s * -rot[1], s * -rot[2]) if rot else None
            p = (s * -pivot[0], pivot[1], pivot[2]) if pivot else None
            B(name, (s * -a[0], a[1], a[2]), (s * -c[0], c[1], c[2]), mat, rot=r, pivot=p, **f)
        L((-3.4, 12.5, -1.3), (-1.0, 20.2, 1.3), 'metal')                                                   # 太ももの芯
        L((-3.7, 13.6, -1.75), (-0.7, 19.0, -1.25), 'wplate', top='gold', rot=(-10, 0, 0), pivot=(-2.2, 19.0, -1.5))   # 太ももの前（下が前へ）
        L((-3.85, 14.0, -1.2), (-3.35, 18.6, 1.2), 'champ', rot=(0, 0, 10), pivot=(-3.35, 18.6, 0))           # 太ももの横
        L((-3.6, 11.3, -2.2), (-0.8, 13.7, -1.4), 'champ', front='wplate', rot=(15, 0, 0), pivot=(-2.2, 12.5, -1.4))   # 膝の装甲（上が前へ）
        L((-2.8, 11.8, -2.45), (-1.6, 12.6, -2.2), 'glow', rot=(15, 0, 0), pivot=(-2.2, 12.5, -1.4))          # 膝の赤い光
        L((-3.3, 2.4, -1.4), (-1.1, 11.35, 1.4), 'metal')                                                   # 脛の芯
        L((-3.6, 3.0, -1.85), (-0.8, 11.0, -1.3), 'wplate', front='champ', rot=(-8, 0, 0), pivot=(-2.2, 11.0, -1.5))   # 脛の前の板（下が前へ）
        L((-3.95, 3.4, -1.3), (-3.25, 10.6, 1.4), 'wplate', out='champ', rot=(0, 0, 10), pivot=(-3.25, 10.6, 0))  # 脛の外の板（下が外へ）
        L((-3.85, 9.3, -2.0), (-0.55, 9.9, 1.7), 'gold', rot=(-8, 0, 0), pivot=(-2.2, 9.6, 0))
        L((-3.2, 1.2, -1.0), (-1.2, 2.45, 1.0), 'metal')                                                    # 足首
        L((-2.8, 1.5, 1.4), (-1.6, 2.7, 2.3), 'dark', back='glow', rot=(-15, 0, 0), pivot=(-2.2, 2.1, 1.4))   # 足首の噴射口
        L((-3.6, 0.0, -3.6), (-0.8, 1.3, 1.6), 'wplate', top='gold')                                        # 尖った足
        L((-3.2, 0.4, -4.6), (-1.2, 1.2, -3.55), 'champ')                                                     # つま先
        c = m.bone('calf_fin_' + name.split('_')[0], name, (s * 2.2, 8.0, 1.6), (25, 0, 0))
        B(c, (s * 2.35, 6.0, 1.6), (s * 2.05, 9.5, 4.3), 'wplate', out='gold')
    return m


# ------------------------------------------------------------------ 細かい塗り（2026-10-08、256x256・1px に 3 texel）
TEX_HD, DENSITY = 256, 3


def _c(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def _mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _put(img, x, y, col):
    img.putpixel((x, y), tuple(int(max(0, min(255, v))) for v in col[:3]) + (255,))


def paint_hd(base, glow, u, v, w, h, mat, face, rnd, ctx=None):
    """面を細かく塗る: 縁の面取り（光と影）・グラデーション・パネルの線・リベット・金の彫り線・放熱の細い羽板・
    暗い骨組みの赤い回路の線・光の刃（芯が明るく縁へ金に褪せる）・バイザーの明るい芯の線"""
    def P(x, y, col, g=False):
        _put(base, u + x, v + y, col)
        if g:
            _put(glow, u + x, v + y, col)

    # ---- 光る素材
    if mat == 'wglow':                                                        # 白金の光（翼の刃・尾・剣）
        long_x = w >= h
        for y in range(h):
            for x in range(w):
                t = abs((y + 0.5) / h - 0.5) * 2 if long_x else abs((x + 0.5) / w - 0.5) * 2
                s = (x + 0.5) / w if long_x else (y + 0.5) / h
                col = _mix((255, 255, 255), _c(0xFFF0B8), min(1, t * 1.6))
                col = _mix(col, _c(0xE8C060), max(0, t - 0.55) * 2.2)
                if ((x if long_x else y) + (y if long_x else x)) % 5 == 0 and t < 0.6:
                    col = _mix(col, (255, 255, 255), 0.5)                     # 光の筋
                col = _mix(col, _c(0xFFE8A0), s * 0.15)
                P(x, y, col, True)
        return
    if mat == 'glow':                                                         # 赤い光（コア・関節など）
        for y in range(h):
            for x in range(w):
                d = ((x + 0.5 - w / 2) ** 2 / max(1, (w / 2) ** 2) + (y + 0.5 - h / 2) ** 2 / max(1, (h / 2) ** 2)) ** 0.5
                col = _mix((255, 236, 240), _c(0xFF8FA0), d * 2.2)
                col = _mix(col, _c(0xFF2A4A), (d - 0.45) * 2.5)
                if 0.62 < d < 0.75 and w >= 4 and h >= 4:
                    col = _c(0xFFB0BC)                                        # 細い光の輪
                if d > 0.9:
                    col = _c(0xB01830)
                P(x, y, col, True)
        return
    if mat == 'slit':                                                         # バイザー: 明るい芯の線
        for y in range(h):
            for x in range(w):
                dy = abs(y + 0.5 - h / 2) / (h / 2)
                if dy < 0.25:
                    col, gl = (255, 232, 238), True
                elif dy < 0.6:
                    col, gl = _c(0xFF2A4A), True
                elif dy < 0.85:
                    col, gl = _c(0x8A1428), True
                else:
                    col, gl = _c(0x1A0508), False
                if x in (0, w - 1):
                    col, gl = _c(0x1A0508), False
                P(x, y, col, gl)
        return
    if mat == 'eye':                                                          # 砲口のレンズ
        for y in range(h):
            for x in range(w):
                d = ((x + 0.5 - w / 2) ** 2 + (y + 0.5 - h / 2) ** 2) ** 0.5 / (min(w, h) / 2)
                if d < 0.3:
                    col, gl = (255, 230, 236), True
                elif d < 0.7:
                    col, gl = _c(0xFF2A4A), True
                elif d < 0.85:
                    col, gl = _c(0xFF8FA0), True
                else:
                    col, gl = _c(0x2C3038), False
                P(x, y, col, gl)
        return

    # ---- 板（面取り・グラデーション・むら）
    MAT = _g.MAT
    b0, l0, d0 = (_c(MAT[mat][0]), _c(MAT[mat][1]), _c(MAT[mat][2])) if mat in MAT else (_c(0x474C59), _c(0x6A7080), _c(0x23262E))
    side = face not in ('up', 'down')
    grid = {}
    for y in range(h):
        for x in range(w):
            t = (y + 0.5) / h
            col = _mix(_mix(l0, b0, 0.55), b0, t * 1.4) if side else b0
            if side and t > 0.7:
                col = _mix(col, d0, (t - 0.7) * 0.9)
            if mat in ('wsmooth', 'wplate', 'champ') and w >= 6:            # ゆるい斜めの光沢
                k = ((x + (h - y)) / (w + h))
                col = _mix(col, (255, 255, 255), max(0, 0.18 - abs(k - 0.62) * 0.9))
            n = rnd.randint(-3, 3)
            col = tuple(cc + n for cc in col)
            grid[(x, y)] = col
    for (x, y), col in grid.items():
        if w >= 3 and h >= 3:
            if x == 0 or y == 0:
                col = _mix(col, l0, 0.8)
            elif x == w - 1 or y == h - 1:
                col = _mix(col, d0, 0.8)
            elif x == 1 or y == 1:
                col = _mix(col, l0, 0.3)
            elif x == w - 2 or y == h - 2:
                col = _mix(col, d0, 0.3)
        grid[(x, y)] = col

    glows = set()
    if mat in ('wplate', 'champ') and w >= 6 and h >= 6:
        py = int(h * 0.42)
        for x in range(2, w - 2):                                             # パネルの線（暗い溝＋下の光）
            grid[(x, py)] = _mix(grid[(x, py)], d0, 0.75)
            grid[(x, py + 1)] = _mix(grid[(x, py + 1)], l0, 0.4)
        if w >= 10:
            sx = int(w * 0.33)
            for y in range(2, py):                                            # 縦の継ぎ目
                grid[(sx, y)] = _mix(grid[(sx, y)], d0, 0.6)
        for (x, y) in ((w - 3, 2), (2, h - 4)):                                # リベット（対角に 2 つ。並べると顔に見えるので）
            grid[(x, y)] = _mix(d0, (0, 0, 0), 0.2)
            if x + 1 < w and y + 1 < h:
                grid[(x + 1, y + 1)] = _mix(grid[(x + 1, y + 1)], l0, 0.6)
        if w >= 8:
            for x in range(3, w - 3):                                         # 金の彫り線
                grid[(x, h - 3)] = _c(0xE8C060) if (x % 6) else _c(0xFADC78)
    if mat == 'champ' and w >= 6 and h >= 4:
        for y in range(2, h - 2):
            for x in range(2, w - 2):
                if (x + y) % 6 == 0:
                    grid[(x, y)] = _mix(grid[(x, y)], d0, 0.25)               # 細い斜めの彫り
    if mat == 'gold':
        if h >= 4:
            my = h // 2
            for x in range(1, w - 1):
                grid[(x, my)] = _c(0x966E1E)
                grid[(x, my - 1)] = _mix(grid[(x, my - 1)], _c(0xFFF0B0), 0.5)
        if w >= 6 and h >= 5:
            cx, cy = w // 2, h // 2
            for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):               # 菱形の刻印
                grid[(cx + dx, cy + dy)] = _c(0x966E1E)
            grid[(cx, cy)] = _c(0xFFF0B0)
    if mat == 'vent':
        for y in range(1, h - 1):
            for x in range(1, w - 1):
                if y % 2 == 1:
                    grid[(x, y)] = _c(0x0C0D10)                               # 細い羽板の隙間
                elif x in (1, w - 2):
                    grid[(x, y)] = _mix(grid[(x, y)], l0, 0.3)
    if mat in ('metal', 'dark') and w >= 6 and h >= 6:
        py = int(h * 0.5)
        for x in range(1, w - 1):
            grid[(x, py)] = _mix(grid[(x, py)], d0, 0.6)                      # パネルの線
        if w >= 8 and h >= 8 and rnd.random() < 0.7:                          # 赤い回路の線（光る）
            y1, y2 = max(2, h // 4), min(h - 3, h * 3 // 4)
            x1, xm, x2 = 2, w // 2, w - 3
            path = [(x, y1) for x in range(x1, xm + 1)] + [(xm, y) for y in range(y1, y2 + 1)] + [(x, y2) for x in range(xm, x2 + 1)]
            for p in path:
                grid[p] = _c(0xC81E38)
                glows.add(p)
            for p in ((x1, y1), (x2, y2), (xm, y1)):
                grid[p] = _c(0xFF8FA0)
    if ctx:
        armor_glow(grid, glows, w, h, mat, face, ctx)
    for (x, y), col in grid.items():
        P(x, y, col, (x, y) in glows)


# ---- 装甲の発光（2026-10-08）: 脚を主に、肩・前腕・胸・草摺・兜にも白金の光の線と冷たい赤の差し色
WG_CORE, WG_MID, WG_EDGE = (255, 250, 235), (255, 232, 160), (232, 192, 96)
RED, RED_DIM = (255, 42, 74), (176, 24, 48)


def armor_glow(grid, glows, w, h, mat, face, ctx):
    bone, (sx, sy, sz), (ox, oy, oz) = ctx['bone'], ctx['size'], ctx['origin']
    cx = ox + sx / 2
    out = ('east' if cx < 0 else 'west')
    sides = ('north', 'south', 'east', 'west')

    def put(x, y, col):
        if 0 <= x < w and 0 <= y < h:
            grid[(x, y)] = col
            glows.add((x, y))

    def vline(x, y0=1, y1=None, segment=True, red=True):
        y1 = h - 2 if y1 is None else y1
        for y in range(y0, y1 + 1):
            gap = segment and y % 8 == 7
            put(x, y, WG_MID if gap else WG_CORE)
            put(x - 1, y, WG_EDGE if gap else WG_MID)
            put(x + 1, y, WG_EDGE if gap else WG_MID)
            if red and w >= 7:
                put(x - 2, y, RED_DIM if gap else RED)
                put(x + 2, y, RED_DIM if gap else RED)

    def hline(y, x0=1, x1=None, red=False):
        x1 = w - 2 if x1 is None else x1
        for x in range(x0, x1 + 1):
            put(x, y, WG_CORE if x % 6 else WG_MID)
            if red:
                put(x, y - 1, RED)
                put(x, y + 1, RED_DIM)

    def border():
        for x in range(w):
            put(x, 0, WG_MID); put(x, h - 1, WG_EDGE)
        for y in range(h):
            put(0, y, WG_MID); put(w - 1, y, WG_EDGE)
        if w >= 3 and h >= 3:
            put(w - 1, 0, RED); put(0, h - 1, RED)

    plate = mat in ('wplate', 'champ', 'wsmooth')
    if bone in ('right_leg', 'left_leg'):                                       # ---- 脚（いちばん光らせる）
        if mat in ('wplate', 'champ') and sy >= 5 and face in ('north', out) and h >= 12:
            vline(w // 2)                                                       # 前・外を走る光の線
            if face == 'north' and sy >= 7.5 and w >= 5:
                hline(1)                                                        # 膝と脛の間の光る継ぎ目
        elif mat == 'champ' and sx <= 0.6 and face == out:                      # 太ももの横の小さな光る放熱口
            for fy in (0.3, 0.5, 0.7):
                hline(int(h * fy), 2, w - 3)
        elif mat == 'champ' and 2.0 <= sy <= 3.0 and face in sides and h >= 4:  # 膝の装甲の下の継ぎ目
            hline(h - 2, red=True)
        elif mat == 'metal' and sy < 1.5 and face in sides:                     # 足首の光る輪
            mid = h // 2
            hline(mid, 0, w - 1, red=True)
        elif mat == 'champ' and sy < 1.0 and face == 'north':                   # つま先の光る線
            hline(h // 2, 1, w - 2)
        elif mat == 'wplate' and sy < 1.5 and sz > 4 and face == 'north':       # 足の前の縁
            hline(1)
        elif mat == 'metal' and sy > 7 and face == out and h >= 12:             # 脛の芯の外側にも赤い細線
            for y in range(2, h - 2):
                if y % 3:
                    put(w // 2, y, RED)
    elif bone.startswith('calf_fin'):                                           # ふくらはぎのひれの光る縁
        border()
    elif bone in ('right_arm', 'left_arm'):
        if oy >= 26.5 and mat in ('wplate', 'champ', 'gold') and face in sides and h >= 3:
            hline(1, red=False)                                                 # 肩当ての縁の光
        elif 18.0 <= oy <= 19.0 and sy > 4 and plate and face in ('north', out) and h >= 12:
            vline(w // 2, segment=True, red=False)                              # 前腕の光の線
    elif bone == 'body':
        if mat == 'gold' and abs(sx - 1.9) < 0.05 and face == 'north':          # コアの周りの光る継ぎ目
            border()
        elif mat == 'champ' and ctx['rot'] and ctx['rot'][1] == 45 and face in sides:
            vline(w // 2, segment=False, red=False)                             # 竜骨の光
        elif mat == 'wplate' and abs(sx - 3.95) < 0.05 and face == 'north' and h >= 6:
            hline(h - 3, red=True)                                              # 胸当ての下の光る継ぎ目
        elif oy <= 17.5 and plate and face in sides and h >= 6:                 # 草摺の先の光
            for x in range(1, w - 1):
                put(x, h - 2, WG_CORE if x % 2 else RED)
    elif bone == 'head' and mat == 'gold' and face in sides and w >= 3 and h >= 2:
        put(1, h // 2, WG_CORE); put(w - 2, h // 2, RED)                        # 兜の菱形の縁の光る点


_g = g

if __name__ == '__main__':
    g.write_hd(luxion(), TEX_HD, DENSITY, paint_hd)
