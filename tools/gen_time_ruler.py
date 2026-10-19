"""
総司令機【タイムルーラー】の細かい GeckoLib モデルとテクスチャ（2026-10-19 ユーザー指定「タイムルーラーのモデルをぐんと解像度あげて」）。

    python tools/gen_time_ruler.py

出力: geo/entity/time_ruler.geo.json、textures/entity/time_ruler.png（＋ _glowmask.png、1024x1024・1px に 6 texel。paint で細かく塗る）。
はじめの粗い版（tools/gen_chrono_base.py の time_ruler）の置き換え。箱・UV の詰め込みは tools/gen_chrono_gazers.py の write_hd を使う。
骨の名前と pivot は Java 側（client/ChronoBaseModel）と一致させること（粗い版と同じ）:
  root > body（0,40,0）> head（首 0,56,0）> visor（0,61,-6.8）
                       > arm_right（右肩 -15,52,0）/ arm_left（左肩 15,52,0）
                       > dial（背の文字盤 0,52,12）> hand_hour（0,52,10.5）/ hand_minute（0,52,10）
                       > blades（周りの 8 本の針の剣 0,44,0）
       > gear_ring（下半身の代わりの歯車の輪 0,12,0）、gear_inner（内側の輪 0,17,0）
動かす骨には初期の向きを付けない（傾きは箱ごとの回転で付ける）。
見た目: 白（0xF2EEE4）と金（0xE0B048）の鎧に深い藍の外套と腰布。金に光る面頬と時計の針の冠、胸に金の時計の核。
背に 48 の金の欠片で丸く組んだ文字盤（12 の光る目盛り・60 の分の刻み・中のロゼット・4 本の支え）、長針と短針。
右手に時計の長針の形の剣。周りに 8 本の針の剣（鍔・柄・光る柄頭）。下は外 32 歯・内輪・6 本のスポークの回る歯車。
一般的な時計・機械の意匠だけを使い、特定の作品の形は写さない（DESIGN_VALKYRIE_VISUAL_DB §8）
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
    'cloth': (0x2A2468, 0x4A42A0, 0x120E34, 'plain', False),    # 深い藍の外套（織り目と金の裾）
    'frame': (0x2A2C38, 0x444858, 0x14151C, 'plain', False),    # 暗い骨組み（金の回路の線）
    'vent': (0x2A2C38, 0x444858, 0x14151C, 'vent', False),
    'gglow': (0xFFD166, 0xFFF0B0, 0xC08A20, 'glow', True),      # 金の光（文字盤・針・面頬・核）
    'vglow': (0xB070FF, 0xE0C8FF, 0x6A30C0, 'glow', True),      # 紫の光（腰の芯・宝石）
})


def ring(B, bone, cx, cy, cz, r, n, size, mat, plane='xy', offset=0.0, every=None, alt=None, **f):
    """r の円に n 個の箱を並べ、円に沿う向きに回す。plane = 'xy'（前を向く輪）か 'xz'（水平の輪）"""
    sx, sy, sz = size
    for k in range(n):
        a = 2 * math.pi * k / n + offset
        mat_k = alt if (alt and every and k % every == 0) else mat
        if plane == 'xy':
            x, y = cx + r * math.cos(a), cy + r * math.sin(a)
            B(bone, (x - sx / 2, y - sy / 2, cz - sz / 2), (x + sx / 2, y + sy / 2, cz + sz / 2), mat_k, rot=(0, 0, math.degrees(a) + 90), pivot=(x, y, cz), **f)
        else:
            x, z = cx + r * math.cos(a), cz + r * math.sin(a)
            B(bone, (x - sx / 2, cy - sy / 2, z - sz / 2), (x + sx / 2, cy + sy / 2, z + sz / 2), mat_k, rot=(0, -math.degrees(a) + 90, 0), pivot=(x, cy, z), **f)


def time_ruler():
    m = g.Model('time_ruler')
    B, MB = m.box, m.mbox
    m.bone('root')

    # ---- 下半身の代わりの歯車（外 32 歯の輪・内輪・光の象嵌）
    m.bone('gear_ring', 'root', (0, 12, 0))
    ring(B, 'gear_ring', 0, 12, 0, 15.5, 32, (3.2, 3.0, 2.2), 'goldp', plane='xz', every=4, alt='gold', top='gold')
    ring(B, 'gear_ring', 0, 12, 0, 17.6, 16, (1.8, 2.4, 1.6), 'gold', plane='xz', offset=math.pi / 32)            # 外の歯
    ring(B, 'gear_ring', 0, 13.7, 0, 15.5, 24, (2.6, 0.4, 0.6), 'gglow', plane='xz')                             # 上面の光る象嵌
    ring(B, 'gear_ring', 0, 11.5, 0, 13.2, 24, (3.2, 2.0, 1.2), 'cloth', plane='xz')                             # 内側の藍の縁
    m.bone('gear_inner', 'root', (0, 17, 0))
    B('gear_inner', (-5, 15, -5), (5, 19, 5), 'cloth', top='goldp', bottom='frame')
    B('gear_inner', (-5, 15.2, -5), (5, 18.8, 5), 'cloth', top='goldp', bottom='frame', rot=(0, 45, 0), pivot=(0, 17, 0))  # 八角の芯
    B('gear_inner', (-2.6, 13.4, -2.6), (2.6, 15.2, 2.6), 'vglow', sides='gold')                                  # 下の紫の芯
    for k in range(6):                                                                                            # 6 本のスポーク
        a = math.degrees(2 * math.pi * k / 6)
        B('gear_inner', (5, 16.4, -0.8), (12.6, 17.6, 0.8), 'gold', rot=(0, a, 0), pivot=(0, 17, 0))
        B('gear_inner', (8.2, 15.9, -1.1), (10.4, 18.1, 1.1), 'goldp', rot=(0, a + 30, 0), pivot=(0, 17, 0))     # スポークの間の小さな歯車
    ring(B, 'gear_inner', 0, 17, 0, 12.6, 18, (4.2, 1.6, 1.4), 'goldp', plane='xz')                              # 内輪

    # ---- 胴（腰・腰布・腹・胸・時計の核・襟・背）
    m.bone('body', 'root', (0, 40, 0))
    B('body', (-6, 26, -5), (6, 32.5, 5), 'frame', back='vent')                                                    # 腰
    B('body', (-7, 30.5, -6), (7, 32.5, 6), 'goldp')                                                               # 帯
    B('body', (-2.2, 29.6, -6.8), (2.2, 33.4, -6.0), 'gglow', sides='gold')                                        # 帯の時計の留め金
    MB('body', (-6.2, 15.5, -6.4), (-0.4, 30.5, -5.4), 'cloth', rot=(-12, 0, 6), pivot=(-3, 30.5, -5.9))           # 前の腰布（左右）
    MB('body', (-6.2, 14.6, -6.6), (-0.4, 15.6, -5.2), 'goldp', rot=(-12, 0, 6), pivot=(-3, 30.5, -5.9))           # その金の裾
    MB('body', (-8.6, 14.5, -4.6), (-7.6, 30.5, 4.6), 'cloth', out='cloth', rot=(0, 0, 12), pivot=(-8, 30.5, 0))   # 横の腰布
    MB('body', (-8.8, 13.6, -4.8), (-7.4, 14.6, 4.8), 'goldp', rot=(0, 0, 12), pivot=(-8, 30.5, 0))
    B('body', (-7.5, 9.5, 5.4), (7.5, 30.5, 6.4), 'cloth', rot=(14, 0, 0), pivot=(0, 30.5, 5.9))                  # 後ろの長い外套
    B('body', (-7.5, 8.6, 5.2), (7.5, 9.6, 6.6), 'goldp', rot=(14, 0, 0), pivot=(0, 30.5, 5.9))
    for i, y in enumerate((32.5, 34.6, 36.7)):                                                                    # 重ねた腹の板
        MB('body', (-6.6, y, -6.2 - i * 0.2), (-0.3, y + 2.3, -5.0), 'white' if i % 2 == 0 else 'whites', rot=(-10, -8, 4), pivot=(-0.3, y + 2.3, -5.6))
    B('body', (-9, 36, -6), (9, 52, 6), 'frame', back='vent')                                                      # 胸の骨組み
    MB('body', (-11, 42, -8.6), (-0.6, 52, -5.5), 'white', front='whites', top='goldp', rot=(10, -14, -6), pivot=(-0.6, 47, -7))  # 胸当て
    MB('body', (-10, 37, -7.8), (-1, 42, -5.6), 'whites', rot=(-8, -12, 5), pivot=(-1, 39.5, -6.7))               # 胸の下の板
    MB('body', (-11.4, 37, -5), (-9.4, 51, 5), 'white', out='whites', rot=(0, 0, -8), pivot=(-9.4, 51, 0))         # 脇の板
    B('body', (-1.3, 39.5, -9.2), (1.3, 52, -6.6), 'goldp', rot=(0, 45, 0), pivot=(0, 45.75, -7.9))               # 竜骨
    ring(B, 'body', 0, 46, -9.9, 3.4, 12, (1.9, 1.0, 1.0), 'gold')                                                 # 胸の時計の核の金の輪
    B('body', (-2.4, 43.6, -10.1), (2.4, 48.4, -9.4), 'gglow', sides='gold')                                       # 核の光
    for k in range(4):                                                                                            # 核から伸びる金の筋
        a = k * 90 + 45
        B('body', (-0.4, 49.4, -9.6), (0.4, 53.0, -9.0), 'gold', rot=(0, 0, a), pivot=(0, 46, -9.3))
    MB('body', (-7.5, 52, -5.5), (-1, 54.8, 5.5), 'goldp', top='gold', rot=(-6, 0, 14), pivot=(-1, 52, 0))         # 斜めの襟
    B('body', (-2.6, 52, -2.6), (2.6, 57, 2.6), 'frame')                                                           # 首
    B('body', (-4.2, 36, 6), (4.2, 52, 9), 'white', back='vent')                                                   # 背の板
    B('body', (-2.2, 47, 8.6), (2.2, 57, 12), 'goldp')                                                             # 文字盤の支え
    MB('body', (-8, 40, 6), (-4.2, 50, 8), 'whites', rot=(0, 18, 0), pivot=(-4.2, 45, 7))                          # 背の斜めの板

    # ---- 頭（兜・面頬・頬当て・稜線・後ろへ流れる飾り・針の冠）
    m.bone('head', 'body', (0, 56, 0))
    B('head', (-5.5, 56, -5.5), (5.5, 66, 5), 'white', front='whites', top='white')
    B('head', (-4.6, 56.4, -6.8), (4.6, 63, -5.4), 'whites', rot=(8, 0, 0), pivot=(0, 60, -6))                    # 顔の板
    MB('head', (-6.6, 56.4, -5), (-5.3, 63.5, 3), 'goldp', rot=(0, -10, 0), pivot=(-5.3, 60, -1))                  # 頬当て
    B('head', (-1.1, 61, -7.2), (1.1, 72, 1.2), 'goldp', top='gold', rot=(-22, 0, 0), pivot=(0, 66, -3))          # 額から後ろへの稜線
    MB('head', (-5.4, 60, 3.8), (-1.2, 64, 11), 'whites', top='goldp', rot=(-28, -12, 0), pivot=(-3, 62, 4))       # 後ろへ流れる飾り
    MB('head', (-4.8, 63.6, 9.5), (-2, 64.6, 13), 'gglow', rot=(-28, -12, 0), pivot=(-3, 62, 4))                   # その光る先
    B('head', (-6.2, 65, -6.2), (6.2, 67, 6.2), 'goldp')                                                           # 冠の輪
    for k in range(-3, 4):                                                                                        # 時計の針の形の 7 本の冠
        x = k * 1.75
        h = 4.0 + (3 - abs(k)) * 1.6
        B('head', (x - 0.55, 67, -5.6), (x + 0.55, 67 + h, -4.5), 'gold', rot=(0, 0, -k * 7), pivot=(x, 67, -5.0))
        B('head', (x - 0.85, 67 + h - 1.6, -5.8), (x + 0.85, 67 + h, -4.3), 'gglow', rot=(0, 0, -k * 7 + 45), pivot=(x, 67 + h - 0.8, -5.0))
    m.bone('visor', 'head', (0, 61, -6.8))
    B('visor', (-4.2, 60.1, -7.4), (4.2, 62.1, -6.7), 'gglow', sides='gold', top='gold', bottom='gold')             # 金に光る面頬
    B('visor', (-0.4, 57.2, -7.3), (0.4, 60.2, -6.8), 'gglow')                                                     # 縦の細い光

    # ---- 腕（肩当て 3 重・二の腕・金の肘・籠手・手）。右手に長針の剣、左手の上に小さな光の輪
    for name, s in (('arm_right', -1), ('arm_left', 1)):
        m.bone(name, 'body', (s * 15, 52, 0))
        def X(a, c, mat, rot=None, pivot=None, **f):
            B(name, (s * a[0], a[1], a[2]), (s * c[0], c[1], c[2]), mat,
              rot=(rot[0], s * -rot[1] if s > 0 else rot[1], s * -rot[2] if s > 0 else rot[2]) if rot else None,
              pivot=(s * pivot[0], pivot[1], pivot[2]) if pivot else None, **f)
        X((12, 50, -6.4), (21.5, 56.5, 6.4), 'white', rot=(0, 0, 18), pivot=(13, 53, 0), top='goldp', out='whites')  # 肩当て 1
        X((13.5, 47.2, -5.8), (22.5, 51, 5.8), 'whites', rot=(0, 0, 24), pivot=(14, 49, 0))                       # 肩当て 2
        X((14, 46.4, -6.2), (23, 47.4, 6.2), 'goldp', rot=(0, 0, 24), pivot=(14, 49, 0))                          # その金の縁
        X((18.5, 52.5, -2.0), (20.5, 54.5, 2.0), 'gglow', rot=(0, 0, 18), pivot=(13, 53, 0))                      # 肩の光る宝石
        X((12.5, 40, -3), (17.5, 49, 3), 'frame')                                                                 # 二の腕
        X((12, 38, -3.6), (18, 41, 3.6), 'goldp')                                                                 # 肘
        X((11, 28, -4.2), (19, 38, 4.2), 'white', front='whites', out='whites')                                  # 籠手
        X((10.6, 35.8, -4.6), (19.4, 38, 4.6), 'goldp')                                                           # 籠手の金の縁
        X((18.8, 29, -1.0), (19.3, 36.5, 1.0), 'gglow')                                                           # 籠手の外の光の線
        X((12.5, 24, -2.6), (17.5, 28, 2.6), 'frame')                                                             # 手
        for k in range(3):
            X((12.7 + k * 1.6, 21.5, -2.6), (14.0 + k * 1.6, 24, -1.2), 'gold')                                   # 指
    # 右手: 時計の長針の形の剣（金の身、光る縁、矢じりの先、鍔）
    B('arm_right', (-17.6, 23.2, -2.4), (-12.4, 24.6, 2.4), 'goldp')                                               # 鍔
    B('arm_right', (-15.6, 4, -0.6), (-14.4, 23.2, 0.6), 'gold', front='gglow', back='gglow')                     # 剣の身
    B('arm_right', (-16.4, 2.2, -0.5), (-13.6, 5.0, 0.5), 'gglow', rot=(0, 0, 45), pivot=(-15, 3.6, 0))           # 矢じりの先
    B('arm_right', (-16.8, 14.0, -0.5), (-13.2, 16.0, 0.5), 'gold', rot=(0, 0, 45), pivot=(-15, 15, 0))          # 身の菱形の飾り
    # 左手の上: 小さな光の時計の輪
    ring(B, 'arm_left', 15, 19.5, 0, 3.0, 10, (1.6, 0.6, 0.6), 'gglow', plane='xz')

    # ---- 背の巨大な文字盤（48 の金の欠片の外輪・藍の内輪・12 の光る目盛り・60 の分の刻み・中のロゼット・4 本の支え）
    m.bone('dial', 'body', (0, 52, 12))
    ring(B, 'dial', 0, 52, 12, 28, 48, (3.9, 3.0, 2.0), 'goldp', every=4, alt='gold')
    ring(B, 'dial', 0, 52, 12.6, 30.4, 24, (1.6, 1.4, 1.4), 'gold', offset=math.pi / 24)                          # 外の小さな飾り
    ring(B, 'dial', 0, 52, 12, 24.8, 48, (3.4, 1.1, 1.2), 'cloth')
    ring(B, 'dial', 0, 52, 11.6, 26.4, 60, (0.45, 1.2, 0.5), 'gglow')                                             # 分の刻み
    for k in range(12):                                                                                           # 12 の光る目盛り（3・6・9・12 は大きい）
        a = 2 * math.pi * k / 12
        big = k % 3 == 0
        x, y = 21.5 * math.cos(a), 52 + 21.5 * math.sin(a)
        w, hh = (1.6, 4.4) if big else (1.1, 2.8)
        B('dial', (x - w / 2, y - hh / 2, 11.2), (x + w / 2, y + hh / 2, 12.6), 'gglow', sides='gold', rot=(0, 0, math.degrees(a) + 90), pivot=(x, y, 11.9))
    for k in range(12):                                                                                           # 中のロゼット
        a = 2 * math.pi * k / 12
        x, y = 7.5 * math.cos(a), 52 + 7.5 * math.sin(a)
        B('dial', (x - 1.4, y - 2.8, 11.4), (x + 1.4, y + 2.8, 12.4), 'goldp', rot=(0, 0, math.degrees(a) + 90), pivot=(x, y, 11.9))
    for k in range(4):                                                                                            # 4 本の支え
        a = k * 90 + 45
        B('dial', (-0.7, 55, 11.5), (0.7, 76, 12.5), 'gold', rot=(0, 0, a), pivot=(0, 52, 12))
    B('dial', (-3.2, 48.8, 10.8), (3.2, 55.2, 13.2), 'gglow', sides='gold')                                        # 真ん中の光
    m.bone('hand_hour', 'dial', (0, 52, 10.5))
    B('hand_hour', (-1.1, 52, 10.0), (1.1, 64, 10.8), 'gold', front='gglow')
    B('hand_hour', (-2.4, 61, 10.0), (2.4, 65.8, 10.8), 'goldp', rot=(0, 0, 45), pivot=(0, 63.4, 10.4))           # 先の菱形の飾り
    B('hand_hour', (-1.0, 65.6, 9.9), (1.0, 68.4, 10.9), 'gglow', rot=(0, 0, 45), pivot=(0, 67, 10.4))
    m.bone('hand_minute', 'dial', (0, 52, 10))
    B('hand_minute', (-0.75, 52, 9.5), (0.75, 75, 10.2), 'gold', front='gglow')
    B('hand_minute', (-1.7, 45.5, 9.5), (1.7, 50.5, 10.2), 'goldp')                                               # 釣り合いの重り
    B('hand_minute', (-1.4, 74, 9.4), (1.4, 77.4, 10.3), 'gglow', rot=(0, 0, 45), pivot=(0, 75.7, 9.85))          # 光る矢じり

    # ---- 周りの 8 本の針の剣（下を向いて浮く。身・光る縁・鍔・柄・光る柄頭）
    m.bone('blades', 'body', (0, 44, 0))
    for k in range(8):
        a = 2 * math.pi * k / 8
        x, z = 26 * math.cos(a), 26 * math.sin(a)
        ry = -math.degrees(a) + 90
        piv = (x, 44, z)
        B('blades', (x - 0.9, 28, z - 0.35), (x + 0.9, 50, z + 0.35), 'gold', front='gglow', back='gglow', rot=(0, ry, 0), pivot=piv)
        B('blades', (x - 1.1, 25.6, z - 0.3), (x + 1.1, 28.6, z + 0.3), 'gglow', rot=(0, ry, 45), pivot=(x, 27.1, z))     # 切っ先
        B('blades', (x - 2.6, 50, z - 0.9), (x + 2.6, 51.4, z + 0.9), 'goldp', rot=(0, ry, 0), pivot=piv)                 # 鍔
        B('blades', (x - 0.6, 51.4, z - 0.6), (x + 0.6, 55.5, z + 0.6), 'cloth', rot=(0, ry, 0), pivot=piv)               # 柄
        B('blades', (x - 0.9, 55.5, z - 0.9), (x + 0.9, 57.2, z + 0.9), 'vglow', rot=(0, ry + 45, 0), pivot=(x, 56.3, z))  # 柄頭
    return m


# ------------------------------------------------------------------ 細かい塗り
TEX_HD, DENSITY = 1024, 6


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
    """面を細かく塗る: 光る素材は芯が白く縁が金に褪せる。板は面取り・縦のグラデーション・斜めの光沢・パネルの溝・金の彫り線・
    リベット。外套は細かな織り目と金の裾の唐草。骨組みは金の回路の線（光る）。放熱口は細い羽板"""
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
                if (x + y) % 7 == 0 and d < 0.5:
                    col = _mix(col, (255, 255, 255), 0.5)
                P(x, y, col, True)
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
            if mat in ('white', 'whites', 'goldp', 'gold') and w >= 8:          # 斜めの光沢
                k = (x + (h - y)) / (w + h)
                col = _mix(col, (255, 255, 255), max(0.0, 0.22 - abs(k - 0.6) * 0.9))
            n = rnd.randint(-2, 2)
            grid[(x, y)] = tuple(cc + n for cc in col)
    for (x, y), col in list(grid.items()):                                      # 縁の面取り
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
        for x in range(3, w - 3):                                               # パネルの溝（暗い線と下の光）
            put(x, py, _mix(grid[(x, py)], d0, 0.7))
            put(x, py + 1, _mix(grid[(x, py + 1)], l0, 0.4))
        if w >= 16:
            sx = int(w * 0.3)
            for y in range(3, py):
                put(sx, y, _mix(grid[(sx, y)], d0, 0.55))
        for (x, y) in ((w - 4, 3), (3, h - 5)):                                 # リベット
            put(x, y, _mix(d0, (0, 0, 0), 0.25))
            put(x + 1, y + 1, _mix(grid.get((x + 1, y + 1), l0), l0, 0.6))
        if mat == 'white' and w >= 12:
            for x in range(4, w - 4):                                           # 金の彫り線（ところどころ光る）
                put(x, h - 4, GOLD_LINE if x % 7 else GOLD_HI, x % 7 == 0)
            if h >= 20 and w >= 20:                                             # 時計の目盛りの刻印
                cx, cy, r = w // 2, int(h * 0.25), min(w, h) // 8
                for k in range(12):
                    a = k * math.pi / 6
                    put(int(cx + math.cos(a) * r), int(cy + math.sin(a) * r), GOLD_LINE)
    if mat == 'cloth':
        for y in range(h):
            for x in range(w):
                if (x + y) % 3 == 0:
                    put(x, y, _mix(grid[(x, y)], l0, 0.18))                     # 織り目
                elif (x - y) % 5 == 0:
                    put(x, y, _mix(grid[(x, y)], d0, 0.2))
        if side and h >= 10:
            for x in range(w):                                                  # 金の裾の唐草
                yy = h - 3 - int(1.5 + 1.5 * math.sin(x * 0.7))
                put(x, yy, GOLD_LINE)
                put(x, h - 2, GOLD_DEEP)
                if x % 6 == 0:
                    put(x, yy - 1, GOLD_HI, True)
    if mat == 'gold' and h >= 6 and w >= 3:
        my = h // 2
        for x in range(1, w - 1):
            put(x, my, GOLD_DEEP)
            put(x, my - 1, _mix(grid[(x, my - 1)], GOLD_HI, 0.5))
    if mat == 'frame' and w >= 8 and h >= 8:
        py = int(h * 0.5)
        for x in range(1, w - 1):
            put(x, py, _mix(grid[(x, py)], d0, 0.6))
        if rnd.random() < 0.8:                                                  # 金の回路の線（光る）
            y1, y2 = max(2, h // 4), min(h - 3, h * 3 // 4)
            x1, xm, x2 = 2, w // 2, w - 3
            path = [(x, y1) for x in range(x1, xm + 1)] + [(xm, y) for y in range(y1, y2 + 1)] + [(x, y2) for x in range(xm, x2 + 1)]
            for p in path:
                put(p[0], p[1], GLOW_EDGE, True)
            for p in ((x1, y1), (x2, y2), (xm, y1)):
                put(p[0], p[1], GLOW_CORE, True)
    if mat == 'vent':
        for y in range(1, h - 1):
            for x in range(1, w - 1):
                if y % 3 == 1:
                    put(x, y, _c(0x0A0B10))
                elif y % 3 == 2 and x % 9 == 4:
                    put(x, y, GLOW_EDGE, True)                                  # 羽板の奥の金の光
    for (x, y), col in grid.items():
        P(x, y, col, (x, y) in glows)


if __name__ == '__main__':
    g.write_hd(time_ruler(), TEX_HD, DENSITY, paint,
               density_fn=lambda mat, face, bone: 2 if face == 'down' or mat in ('gglow', 'vglow') else DENSITY)
