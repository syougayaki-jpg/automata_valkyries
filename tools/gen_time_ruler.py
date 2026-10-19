"""
総司令機【タイムルーラー】の細かい GeckoLib モデルとテクスチャ（2026-10-19 ユーザー指定「タイムルーラーのモデルをぐんと解像度あげて」、
同日「（参考の絵）これをもとにクロノルーラーを改良して」で騎士の姿に作り直した）。

    python tools/gen_time_ruler.py

出力: geo/entity/time_ruler.geo.json、textures/entity/time_ruler.png（＋ _glowmask.png、1024x1024・1px に 5 texel。paint で細かく塗る）。
箱・UV の詰め込みは tools/gen_chrono_gazers.py の write_hd を使う。
骨の名前と pivot は Java 側（client/ChronoBaseModel）と一致させること:
  root > body（0,40,0）> head（首 0,63,0）> visor（0,67,-4.8）、head > halo_top（0,80,0）/ halo_left（9,71,0）/ halo_right（-9,71,0）
                       > arm_right（右肩 -11,57,0）/ arm_left（左肩 11,57,0）
                       > cape（背のマント 0,58,5）
                       > dial（背の文字盤。体の左上の後ろ 10,62,11）> hand_hour / hand_minute（文字盤の真ん中）
       > leg_right（右の股関節 -4.5,36,0）/ leg_left（4.5,36,0）
動かす骨には初期の向きを付けない（傾きは箱ごとの回転で付ける）。
見た目: 白（0xF2EEE4）と金（0xE0B048）の鎧の騎士。脚があり、足先は下を向いた白い刃。暗い紫の面頬、宝石の付いた金の冠、頭の上と左右に金の光輪。
肩から紫のマント（金の縁と房）、紫に白い十字の前垂れ、肩当ての下の紫の垂れ布。背の文字盤は紫の帯に金の数字、金の縁と光の棘、
輪の外まで伸びる長針の先に三日月の飾り。一般的な騎士・時計の意匠だけを使い、特定の作品の形は写さない（DESIGN_VALKYRIE_VISUAL_DB §8）
"""
import importlib.util
import math
import os

_spec = importlib.util.spec_from_file_location('gen_chrono_gazers', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gen_chrono_gazers.py'))
g = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(g)

g.MAT.update({
    'white': (0xF2EEE4, 0xFFFFFF, 0xC4BCA8, 'panel', False),    # 白い装甲（パネルの線と金の彫り）
    'whites': (0xF2EEE4, 0xFFFFFF, 0xC4BCA8, 'plain', False),   # 白いなめらかな装甲（斜めの光沢）
    'goldp': (0xE0B048, 0xFFE08A, 0x8A6420, 'panel', False),    # 金の装甲
    'gold': (0xE0B048, 0xFFE08A, 0x8A6420, 'plain', False),     # 金の縁・部品
    'purple': (0x6A2CA0, 0x9A5AD0, 0x34105E, 'plain', False),   # 紫の布（織り目と金の裾）
    'tabard': (0x6A2CA0, 0x9A5AD0, 0x34105E, 'plain', False),   # 紫の前垂れ（白い十字）
    'band': (0x5A2290, 0x8048C0, 0x2C0C50, 'plain', False),     # 文字盤の紫の帯（金の数字）
    'visor': (0x2A1440, 0x7A50B0, 0x10081C, 'plain', False),    # 暗い紫の面頬（つやの映り込み）
    'bladew': (0xE8ECF4, 0xFFFFFF, 0xA8B0C0, 'plain', False),   # 足先の白銀の刃
    'frame': (0x2A2C38, 0x444858, 0x14151C, 'plain', False),    # 暗い骨組み（金の回路の線）
    'gglow': (0xFFD166, 0xFFF0B0, 0xC08A20, 'glow', True),      # 金の光（光輪・数字の芯・宝石の縁）
    'vglow': (0xB070FF, 0xE0C8FF, 0x6A30C0, 'glow', True),      # 紫の光（宝石）
})


def ring(B, bone, cx, cy, cz, r, n, size, mat, plane='xy', offset=0.0, every=None, alt=None, **f):
    """r の円に n 個の箱を並べ、円に沿う向きに回す。plane = 'xy'（前を向く輪）・'xz'（水平の輪）・'zy'（横を向く輪）"""
    sx, sy, sz = size
    for k in range(n):
        a = 2 * math.pi * k / n + offset
        mat_k = alt if (alt and every and k % every == 0) else mat
        if plane == 'xy':
            x, y = cx + r * math.cos(a), cy + r * math.sin(a)
            B(bone, (x - sx / 2, y - sy / 2, cz - sz / 2), (x + sx / 2, y + sy / 2, cz + sz / 2), mat_k, rot=(0, 0, math.degrees(a) + 90), pivot=(x, y, cz), **f)
        elif plane == 'xz':
            x, z = cx + r * math.cos(a), cz + r * math.sin(a)
            B(bone, (x - sx / 2, cy - sy / 2, z - sz / 2), (x + sx / 2, cy + sy / 2, z + sz / 2), mat_k, rot=(0, -math.degrees(a) + 90, 0), pivot=(x, cy, z), **f)
        else:
            z, y = cz + r * math.cos(a), cy + r * math.sin(a)
            B(bone, (cx - sz / 2, y - sy / 2, z - sx / 2), (cx + sz / 2, y + sy / 2, z + sx / 2), mat_k, rot=(-math.degrees(a) - 90, 0, 0), pivot=(cx, y, z), **f)


def time_ruler():
    m = g.Model('time_ruler')
    B, MB = m.box, m.mbox
    m.bone('root')

    # ---- 脚（太もも・金の膝の歯車・脛・足首・下を向いた白銀の刃）
    for name, s in (('leg_right', -1), ('leg_left', 1)):
        m.bone(name, 'root', (s * 4.5, 36, 0))

        def L(a, c, mat, rot=None, pivot=None, **f):
            r2 = (rot[0], rot[1] * -s, rot[2] * -s) if rot else None
            B(name, (s * a[0], a[1], a[2]), (s * c[0], c[1], c[2]), mat, rot=r2, pivot=(s * pivot[0], pivot[1], pivot[2]) if pivot else None, **f)
        L((1.6, 26, -3.2), (7.6, 36, 3.2), 'white', front='whites', out='whites')                      # 太もも
        L((1.3, 30, -3.5), (7.9, 31.2, 3.5), 'goldp')                                                     # 太ももの金の帯
        L((2.0, 21.5, -4.4), (7.2, 26.5, -2.6), 'goldp', rot=(-8, 0, 0), pivot=(4.6, 24, -3.5))           # 膝当て
        ring(B, name, s * 4.6, 24, -4.6, 2.5, 10, (1.4, 1.0, 0.8), 'gold')                                # 膝の金の歯車
        B(name, (s * 4.6 - 0.9, 23.1, -5.0), (s * 4.6 + 0.9, 24.9, -4.3), 'vglow')                       # 膝の紫の宝石
        L((1.9, 10, -3.1), (7.3, 22, 3.1), 'white', front='whites', out='whites')                      # 脛
        L((1.6, 18, -3.4), (7.6, 19.2, 3.4), 'goldp')                                                     # 脛の金の帯
        L((3.9, 10.5, -3.5), (5.3, 18, -3.0), 'purple')                                                   # 脛の前の紫の筋
        L((2.6, 7, -2.6), (6.6, 10, 2.6), 'frame')                                                        # 足首
        L((2.2, 7.6, -3.0), (7.0, 9.0, 3.0), 'goldp')                                                     # 足首の金の輪
        L((3.8, -9, -1.0), (5.4, 7.2, 1.0), 'bladew', rot=(14, 0, 0), pivot=(4.6, 7, 0))                 # 下を向いた刃（少し前へ）
        L((4.2, -11.5, -0.7), (5.0, -8.5, 0.7), 'bladew', rot=(14, 0, 0), pivot=(4.6, 7, 0))              # 刃の先
        L((3.4, 2.0, 0.6), (5.8, 7.2, 2.0), 'bladew', rot=(-24, 0, 0), pivot=(4.6, 7, 1))                 # 後ろの小さな刃

    # ---- 胴（腰・前垂れ・横の垂れ布・腹・胸・襟・首）
    m.bone('body', 'root', (0, 40, 0))
    B('body', (-6.5, 34, -4.6), (6.5, 40, 4.6), 'frame')                                                 # 腰
    B('body', (-7.2, 38, -5.2), (7.2, 40.6, 5.2), 'goldp')                                               # 帯
    B('body', (-1.6, 37.6, -5.9), (1.6, 40.9, -5.1), 'vglow', sides='gold')                              # 帯の宝石
    B('body', (-3.2, 13, -5.9), (3.2, 38, -5.0), 'tabard', back='purple', rot=(-5, 0, 0), pivot=(0, 38, -5.4))   # 紫の前垂れ（白い十字）
    MB('body', (-3.8, 13, -6.0), (-3.0, 38, -4.9), 'gold', rot=(-5, 0, 0), pivot=(0, 38, -5.4))          # 前垂れの金の縁
    B('body', (-3.8, 12.2, -6.1), (3.8, 13.2, -4.8), 'gold', rot=(-5, 0, 0), pivot=(0, 38, -5.4))
    MB('body', (-8.8, 24, -4.6), (-7.2, 38, 4.6), 'purple', out='purple', rot=(0, 0, 9), pivot=(-8, 38, 0))   # 横の垂れ布
    for k in range(5):                                                                                   # 横の垂れ布の金の房
        z = -3.6 + k * 1.8
        MB('body', (-8.6, 21.6, z - 0.4), (-7.6, 24.2, z + 0.4), 'gold', rot=(0, 0, 9), pivot=(-8, 38, 0))
    for i, y in enumerate((40.5, 42.6, 44.7)):                                                          # 重ねた腹の板
        MB('body', (-6.4, y, -5.8 - i * 0.15), (-0.3, y + 2.3, -4.6), 'white' if i % 2 == 0 else 'whites', rot=(-10, -8, 4), pivot=(-0.3, y + 2.3, -5.2))
    B('body', (-8.5, 46, -5.5), (8.5, 60, 5.5), 'frame', back='frame')                                   # 胸の骨組み
    MB('body', (-9.6, 50, -7.8), (-0.5, 59.4, -5.2), 'white', front='whites', top='goldp', rot=(10, -14, -6), pivot=(-0.5, 54.7, -6.5))   # 胸当て
    MB('body', (-9.0, 46.2, -7.0), (-0.8, 50.4, -5.2), 'whites', rot=(-8, -12, 5), pivot=(-0.8, 48.3, -6.1))
    MB('body', (-10.2, 46.5, -5.0), (-8.4, 59, 5.0), 'white', out='whites', rot=(0, 0, -8), pivot=(-8.4, 59, 0))
    B('body', (-1.1, 46, -8.6), (1.1, 59.4, -6.2), 'goldp', rot=(0, 45, 0), pivot=(0, 52.7, -7.4))       # 竜骨
    ring(B, 'body', 0, 54, -8.9, 2.4, 10, (1.4, 0.8, 0.8), 'gold')                                        # 胸の金の飾り
    B('body', (-1.4, 52.6, -9.2), (1.4, 55.4, -8.6), 'vglow', sides='gold')                              # 胸の紫の宝石
    MB('body', (-7.0, 59.2, -5.2), (-0.8, 61.8, 5.2), 'goldp', top='gold', rot=(-6, 0, 14), pivot=(-0.8, 59.2, 0))   # 襟
    B('body', (-2.4, 59.5, -2.4), (2.4, 63.5, 2.4), 'frame')                                             # 首
    B('body', (-4.2, 46, 5.4), (4.2, 60, 7.4), 'white')                                                  # 背の板
    B('body', (-1.6, 56, 7.2), (1.6, 63, 10.4), 'goldp')                                                 # 文字盤の支え

    # ---- 背の紫のマント（肩から、金の縁と房）
    m.bone('cape', 'body', (0, 58, 5))
    MB('cape', (-14.5, 16, 5.2), (-1.5, 58, 6.4), 'purple', rot=(10, 0, -7), pivot=(-8, 58, 5.8))
    MB('cape', (-14.8, 15.2, 5.0), (-1.2, 16.4, 6.6), 'goldp', rot=(10, 0, -7), pivot=(-8, 58, 5.8))      # 裾の金の縁
    MB('cape', (-15.0, 16, 5.0), (-14.2, 58, 6.6), 'gold', rot=(10, 0, -7), pivot=(-8, 58, 5.8))          # 外の縁
    for k in range(8):                                                                                   # 裾の金の房
        x = -14 + k * 1.7
        MB('cape', (x - 0.4, 12.6, 5.4), (x + 0.4, 15.4, 6.2), 'gold', rot=(10, 0, -7), pivot=(-8, 58, 5.8))
        MB('cape', (x - 0.6, 12.0, 5.2), (x + 0.6, 12.8, 6.4), 'gglow', rot=(10, 0, -7), pivot=(-8, 58, 5.8))

    # ---- 頭（兜・暗い紫の面頬・金の稜線と頬当て・宝石の冠・3 つの光輪）
    m.bone('head', 'body', (0, 63, 0))
    B('head', (-4.6, 63, -4.6), (4.6, 72.4, 4.6), 'white', front='whites')
    B('head', (-0.9, 67, -5.6), (0.9, 73.6, 2.0), 'goldp', top='gold', rot=(-18, 0, 0), pivot=(0, 70, -2))   # 額の稜線
    MB('head', (-5.6, 63.2, -4.4), (-4.4, 69.2, 3.0), 'goldp', rot=(0, -10, 0), pivot=(-4.4, 66, -1))       # 頬当て
    MB('head', (-4.0, 69.5, 3.6), (-1.0, 72.4, 8.4), 'whites', top='goldp', rot=(-28, -10, 0), pivot=(-2.5, 71, 4))   # 後ろへ流れる飾り
    m.bone('visor', 'head', (0, 67, -4.8))
    B('visor', (-3.7, 64.2, -5.3), (3.7, 70.2, -4.6), 'visor', sides='gold', top='gold', bottom='gold')   # 暗い紫の面頬
    B('visor', (-3.9, 69.9, -5.4), (3.9, 70.6, -4.5), 'gold')
    B('head', (-5.4, 72, -5.4), (5.4, 74.2, 5.4), 'goldp', top='gold')                                   # 冠の輪
    for k in range(10):                                                                                  # 冠の突起（前ほど高い）と紫の宝石
        a = 2 * math.pi * k / 10 - math.pi / 2
        x, z = 4.6 * math.cos(a), 4.6 * math.sin(a)
        h = 3.0 + (2.6 if k == 0 else 1.4 if k in (1, 9) else 0.0)
        B('head', (x - 0.7, 74.2, z - 0.7), (x + 0.7, 74.2 + h, z + 0.7), 'gold', rot=(0, 45, 0), pivot=(x, 75, z))
        B('head', (x - 0.6, 74.2 + h - 0.2, z - 0.6), (x + 0.6, 74.2 + h + 1.0, z + 0.6), 'vglow' if k % 2 == 0 else 'gglow', rot=(0, 45, 45), pivot=(x, 74.2 + h + 0.4, z))
    B('head', (-1.2, 74.6, -5.8), (1.2, 77.0, -5.0), 'vglow', sides='gold')                              # 冠の正面の大きな宝石
    m.bone('halo_top', 'head', (0, 81, 0))
    ring(B, 'halo_top', 0, 81, 0, 6.2, 24, (1.8, 0.7, 0.9), 'gglow', plane='xz')                         # 頭の上の光輪
    for name, s in (('halo_left', 1), ('halo_right', -1)):
        m.bone(name, 'head', (s * 9, 71, 0))
        ring(B, name, s * 9, 71, 0, 3.4, 16, (1.4, 0.6, 0.6), 'gglow', plane='zy')                       # 頭の左右の光輪

    # ---- 腕（肩当て・紫の垂れ布と房・二の腕・肘・籠手・手）
    for name, s in (('arm_right', -1), ('arm_left', 1)):
        m.bone(name, 'body', (s * 11, 57, 0))

        def X(a, c, mat, rot=None, pivot=None, **f):
            r2 = (rot[0], rot[1] * -s, rot[2] * -s) if rot else None
            B(name, (s * a[0], a[1], a[2]), (s * c[0], c[1], c[2]), mat, rot=r2, pivot=(s * pivot[0], pivot[1], pivot[2]) if pivot else None, **f)
        X((8.0, 54, -5.4), (17.0, 61.5, 5.4), 'white', top='goldp', out='whites', rot=(0, 0, 16), pivot=(9, 58, 0))   # 肩当て
        X((8.6, 52.4, -5.8), (17.6, 54.2, 5.8), 'goldp', rot=(0, 0, 16), pivot=(9, 58, 0))                        # 肩当ての金の縁
        X((14.5, 57, -2.2), (16.6, 60.6, 2.2), 'goldp', rot=(0, 0, 16), pivot=(9, 58, 0))                         # 肩の金の飾り
        X((15.4, 57.8, -1.0), (16.9, 59.8, 1.0), 'vglow', rot=(0, 0, 16), pivot=(9, 58, 0))
        X((9.0, 46.5, -5.2), (16.4, 52.6, 5.2), 'purple', out='purple', rot=(0, 0, 10), pivot=(10, 52.6, 0))     # 肩の下の紫の垂れ布
        for k in range(5):
            z = -4.0 + k * 2.0
            X((15.2, 44.2, z - 0.4), (16.2, 46.8, z + 0.4), 'gold', rot=(0, 0, 10), pivot=(10, 52.6, 0))         # 垂れ布の金の房
        X((9.0, 46, -2.8), (13.6, 56, 2.8), 'frame')                                                             # 二の腕
        X((8.6, 43.4, -3.3), (14.0, 46.4, 3.3), 'goldp')                                                         # 肘
        X((8.4, 34, -3.4), (14.4, 43.6, 3.4), 'white', front='whites', out='whites')                            # 籠手
        X((8.0, 41.6, -3.8), (14.8, 43.6, 3.8), 'goldp')                                                         # 籠手の金の縁
        X((7.8, 36, -1.2), (8.4, 41, 1.2), 'gold')
        X((9.4, 30, -2.4), (13.4, 34, 2.4), 'frame')                                                             # 手
        for k in range(3):
            X((9.6 + k * 1.3, 27.6, -2.4), (10.7 + k * 1.3, 30, -1.1), 'gold')                                  # 指

    # ---- 背の文字盤（体の左上の後ろ。紫の帯に金の数字、金の内輪・外輪、12 の光の棘）
    cx, cy, cz = 10, 62, 11
    m.bone('dial', 'body', (cx, cy, cz))
    ring(B, 'dial', cx, cy, cz, 30, 48, (4.1, 4.6, 1.6), 'band')                                         # 紫の帯
    ring(B, 'dial', cx, cy, cz - 0.3, 27.2, 48, (3.7, 1.2, 1.8), 'goldp')                                # 金の内輪
    ring(B, 'dial', cx, cy, cz - 0.3, 33.0, 48, (4.4, 1.6, 1.8), 'goldp', every=4, alt='gold')           # 金の外輪
    for k in range(12):                                                                                  # 外へ伸びる光の棘と金の飾り
        a = 2 * math.pi * k / 12
        x, y = cx + 35.6 * math.cos(a), cy + 35.6 * math.sin(a)
        long = k % 3 == 0
        B('dial', (x - 0.8, y - (2.6 if long else 1.6), cz - 0.6), (x + 0.8, y + (2.6 if long else 1.6), cz + 0.6), 'gold', rot=(0, 0, math.degrees(a) + 90), pivot=(x, y, cz))
        x2, y2 = cx + (38.6 if long else 37.4) * math.cos(a), cy + (38.6 if long else 37.4) * math.sin(a)
        B('dial', (x2 - 0.7, y2 - 0.7, cz - 0.5), (x2 + 0.7, y2 + 0.7, cz + 0.5), 'gglow', rot=(0, 0, 45), pivot=(x2, y2, cz))
    ring(B, 'dial', cx, cy, cz - 0.5, 24.0, 60, (0.4, 1.2, 0.6), 'gold')                                 # 分の刻み
    for k in range(12):                                                                                  # 中のロゼット
        a = 2 * math.pi * k / 12
        x, y = cx + 7.0 * math.cos(a), cy + 7.0 * math.sin(a)
        B('dial', (x - 1.3, y - 2.6, cz - 0.6), (x + 1.3, y + 2.6, cz + 0.4), 'goldp', rot=(0, 0, math.degrees(a) + 90), pivot=(x, y, cz))
    for k in range(4):                                                                                   # 4 本の支え
        B('dial', (cx - 0.6, cy + 3, cz - 0.3), (cx + 0.6, cy + 27, cz + 0.5), 'gold', rot=(0, 0, k * 90 + 45), pivot=(cx, cy, cz))
    B('dial', (cx - 2.8, cy - 2.8, cz - 1.2), (cx + 2.8, cy + 2.8, cz + 1.0), 'gglow', sides='gold')     # 真ん中の光
    m.bone('hand_hour', 'dial', (cx, cy, cz - 1.5))
    B('hand_hour', (cx - 1.1, cy, cz - 2.0), (cx + 1.1, cy + 18, cz - 1.2), 'gold', front='gglow')
    B('hand_hour', (cx - 2.4, cy + 15.6, cz - 2.0), (cx + 2.4, cy + 20.4, cz - 1.2), 'goldp', rot=(0, 0, 45), pivot=(cx, cy + 18, cz - 1.6))
    m.bone('hand_minute', 'dial', (cx, cy, cz - 2.0))
    B('hand_minute', (cx - 0.8, cy - 6, cz - 2.6), (cx + 0.8, cy + 40, cz - 1.8), 'gold', front='gglow')   # 輪の外まで伸びる長針
    B('hand_minute', (cx - 1.8, cy - 8, cz - 2.6), (cx + 1.8, cy - 4.5, cz - 1.8), 'goldp')                # 釣り合いの重り
    for k in range(5):                                                                                   # 先の三日月の飾り（光る）
        a = math.radians(200 + k * 35)
        x, y = cx + 2.6 * math.cos(a), cy + 42.4 + 2.6 * math.sin(a)
        B('hand_minute', (x - 0.8, y - 0.8, cz - 2.7), (x + 0.8, y + 0.8, cz - 1.7), 'gglow', rot=(0, 0, math.degrees(a)), pivot=(x, y, cz - 2.2))
    return m


# ------------------------------------------------------------------ 細かい塗り
TEX_HD, DENSITY = 1024, 5


def _c(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def _mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _put(img, x, y, col):
    img.putpixel((x, y), tuple(int(max(0, min(255, v))) for v in col[:3]) + (255,))


GOLD_LINE, GOLD_HI, GOLD_DEEP = _c(0xE0B048), _c(0xFFF0B0), _c(0x7A5418)
GLOW_CORE, GLOW_MID, GLOW_EDGE = (255, 252, 236), _c(0xFFE08A), _c(0xE0A030)


def paint(base, glow, u, v, w, h, mat, face, rnd, ctx=None):
    """面を細かく塗る: 光る素材は芯が白く縁へ褪せる。板は面取り・縦のグラデーション・斜めの光沢・パネルの溝・金の彫り線・リベット。
    紫の布は織り目と金の裾の唐草。前垂れは白い十字と金の縁取り。文字盤の帯は金のローマ数字の筆（数本の縦線と V の形）。
    面頬は暗い紫のつやと斜めの映り込み。白銀の刃は芯が明るく縁が冷たい。骨組みは金の回路の線（光る）"""
    def P(x, y, col, gl=False):
        _put(base, u + x, v + y, col)
        if gl:
            _put(glow, u + x, v + y, col)

    if mat in ('gglow', 'vglow'):
        hi, mid, edge = (GLOW_CORE, GLOW_MID, GLOW_EDGE) if mat == 'gglow' else ((250, 240, 255), _c(0xD0A8FF), _c(0x8A40E0))
        for y in range(h):
            for x in range(w):
                d = (((x + 0.5) / w - 0.5) ** 2 + ((y + 0.5) / h - 0.5) ** 2) ** 0.5 * 2
                col = _mix(hi, mid, d * 1.4)
                col = _mix(col, edge, (d - 0.65) * 2.5)
                P(x, y, col, True)
        return
    if mat == 'visor':
        for y in range(h):
            for x in range(w):
                t = (y + 0.5) / h
                col = _mix(_c(0x4A2470), _c(0x12081E), t)
                k = (x - y * 0.8) / max(1, w)
                if 0.15 < k < 0.28 or 0.36 < k < 0.4:
                    col = _mix(col, (220, 200, 255), 0.55)                               # 斜めの映り込み
                P(x, y, col, 0.15 < k < 0.28)
        return
    if mat == 'bladew':
        for y in range(h):
            for x in range(w):
                d = abs((x + 0.5) / w - 0.5) * 2
                col = _mix((255, 255, 255), _c(0xC8D0E0), d)
                col = _mix(col, _c(0x8A94A8), (d - 0.75) * 3)
                if d < 0.15:
                    col = _mix(col, _c(0xFFE8B0), 0.5)                                   # 芯の金の筋
                P(x, y, col, d < 0.15)
        return

    b0, l0, d0 = (_c(g.MAT[mat][0]), _c(g.MAT[mat][1]), _c(g.MAT[mat][2]))
    side = face not in ('up', 'down')
    grid = {}
    for y in range(h):
        for x in range(w):
            t = (y + 0.5) / h
            col = _mix(_mix(l0, b0, 0.5), b0, t * 1.3) if side else _mix(b0, l0, 0.15)
            if side and t > 0.72:
                col = _mix(col, d0, (t - 0.72) * 0.9)
            if mat in ('white', 'whites', 'goldp', 'gold') and w >= 8:
                k = (x + (h - y)) / (w + h)
                col = _mix(col, (255, 255, 255), max(0.0, 0.22 - abs(k - 0.6) * 0.9))  # 斜めの光沢
            n = rnd.randint(-2, 2)
            grid[(x, y)] = tuple(cc + n for cc in col)
    for (x, y), col in list(grid.items()):
        if w >= 4 and h >= 4:
            if x == 0 or y == 0:
                col = _mix(col, l0, 0.75)
            elif x == w - 1 or y == h - 1:
                col = _mix(col, d0, 0.75)
            elif x == 1 or y == 1:
                col = _mix(col, l0, 0.3)
            elif x == w - 2 or y == h - 2:
                col = _mix(col, d0, 0.3)
        grid[(x, y)] = col
    glows = set()

    def put(x, y, col, gl=False):
        if 0 <= x < w and 0 <= y < h:
            grid[(x, y)] = col
            if gl:
                glows.add((x, y))

    if mat in ('white', 'goldp') and w >= 10 and h >= 10:
        py = int(h * 0.45)
        for x in range(3, w - 3):                                                       # パネルの溝
            put(x, py, _mix(grid[(x, py)], d0, 0.7))
            put(x, py + 1, _mix(grid[(x, py + 1)], l0, 0.4))
        for (x, y) in ((w - 4, 3), (3, h - 5)):                                         # リベット
            put(x, y, _mix(d0, (0, 0, 0), 0.25))
        if mat == 'white' and w >= 12:
            for x in range(4, w - 4):                                                   # 金の彫り線
                put(x, h - 4, GOLD_LINE if x % 7 else GOLD_HI, x % 7 == 0)
            for y in range(4, h - 6):
                put(3, y, GOLD_LINE)
    if mat in ('purple', 'tabard'):
        for y in range(h):
            for x in range(w):
                if (x + y) % 3 == 0:
                    put(x, y, _mix(grid[(x, y)], l0, 0.16))                             # 織り目
                elif (x - y) % 5 == 0:
                    put(x, y, _mix(grid[(x, y)], d0, 0.18))
        for x in range(w):                                                              # 縦のひだの陰
            if w >= 12 and x % max(4, w // 4) == 0:
                for y in range(h):
                    put(x, y, _mix(grid[(x, y)], d0, 0.35))
        if side and h >= 10:
            for x in range(w):                                                          # 金の裾の唐草
                yy = h - 3 - int(1.5 + 1.5 * math.sin(x * 0.7))
                put(x, yy, GOLD_LINE)
                put(x, h - 2, GOLD_DEEP)
    if mat == 'tabard' and face == 'north' and w >= 8 and h >= 16:                     # 白い十字と金の縁取り
        cxp, cyp = w // 2, int(h * 0.3)
        arm = max(1, w // 10)
        for y in range(int(h * 0.12), int(h * 0.72)):
            for dx in range(-arm, arm + 1):
                put(cxp + dx, y, (250, 248, 240))
            put(cxp - arm - 1, y, GOLD_LINE)
            put(cxp + arm + 1, y, GOLD_LINE)
        for x in range(int(w * 0.22), int(w * 0.78)):
            for dy in range(-arm, arm + 1):
                put(x, cyp + dy, (250, 248, 240))
        for x in range(w):
            put(x, 1, GOLD_LINE)
    if mat == 'band' and face == 'north' and w >= 6:                                    # 金のローマ数字の筆
        seed = int(abs(ctx['origin'][0] * 7 + ctx['origin'][1] * 13)) if ctx else 0
        kind = seed % 4
        mid = h // 2
        top, bot = int(h * 0.22), int(h * 0.78)
        strokes = [w // 2] if kind == 0 else [w // 3, 2 * w // 3] if kind == 1 else [w // 4, w // 2, 3 * w // 4]
        if kind == 3:                                                                   # V の形
            for y in range(top, bot):
                f = (y - top) / max(1, bot - top)
                put(int(w * 0.25 + f * w * 0.25), y, GOLD_HI, True)
                put(int(w * 0.75 - f * w * 0.25), y, GOLD_HI, True)
        else:
            for sx in strokes:
                for y in range(top, bot):
                    put(sx, y, GOLD_HI, True)
        for x in range(w):
            put(x, 0, GOLD_LINE)
            put(x, h - 1, GOLD_LINE)
        _ = mid
    if mat == 'gold' and h >= 6 and w >= 3:
        my = h // 2
        for x in range(1, w - 1):
            put(x, my, GOLD_DEEP)
            put(x, my - 1, _mix(grid[(x, my - 1)], GOLD_HI, 0.5))
    if mat == 'frame' and w >= 8 and h >= 8:
        if rnd.random() < 0.8:                                                          # 金の回路の線（光る）
            y1, y2 = max(2, h // 4), min(h - 3, h * 3 // 4)
            x1, xm, x2 = 2, w // 2, w - 3
            path = [(x, y1) for x in range(x1, xm + 1)] + [(xm, y) for y in range(y1, y2 + 1)] + [(x, y2) for x in range(xm, x2 + 1)]
            for p in path:
                put(p[0], p[1], GLOW_EDGE, True)
    for (x, y), col in grid.items():
        P(x, y, col, (x, y) in glows)


if __name__ == '__main__':
    g.write_hd(time_ruler(), TEX_HD, DENSITY, paint,
               density_fn=lambda mat, face, bone: 2 if face == 'down' or mat in ('gglow', 'vglow') else DENSITY)
