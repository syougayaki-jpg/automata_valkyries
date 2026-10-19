"""
総司令機【タイムルーラー】の細かい GeckoLib モデルとテクスチャ（2026-10-19 ユーザー指定「タイムルーラーのモデルをぐんと解像度あげて」、
同日「（参考の絵）これをもとにクロノルーラーを改良して」で騎士の姿に、「四角く見えないように全体的にシャープに」で八角の柱・段々に細くなる部品・尖りに作り直した）。

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
箱の回転の向き（GeckoLib 4.8 の BakedModelFactory・RenderUtils を読んで確かめた。2026-10-19）: geo の座標で Rz(-z)・Ry(+y)・Rx(-x) の順に効く。
つまり z が正なら正面から見て時計回り、x が正なら上の端が前（-Z）へ倒れる。
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
    'bladew': (0xE8ECF4, 0xFFFFFF, 0xA8B0C0, 'plain', False),   # 白銀の刃（いまは使っていない）
    'leather': (0x2A1C16, 0x5A4034, 0x120A08, 'plain', False),  # 革靴の靴底と踵（つやのある暗い革）
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
            B(bone, (x - sx / 2, y - sy / 2, cz - sz / 2), (x + sx / 2, y + sy / 2, cz + sz / 2), mat_k, rot=(0, 0, -(math.degrees(a) + 90)), pivot=(x, y, cz), **f)
        elif plane == 'xz':
            x, z = cx + r * math.cos(a), cz + r * math.sin(a)
            B(bone, (x - sx / 2, cy - sy / 2, z - sz / 2), (x + sx / 2, cy + sy / 2, z + sz / 2), mat_k, rot=(0, -math.degrees(a) + 90, 0), pivot=(x, cy, z), **f)
        else:
            z, y = cz + r * math.cos(a), cy + r * math.sin(a)
            B(bone, (cx - sz / 2, y - sy / 2, z - sx / 2), (cx + sz / 2, y + sy / 2, z + sx / 2), mat_k, rot=(math.degrees(a) + 90, 0, 0), pivot=(cx, y, z), **f)


def octo(B, bone, a, c, mat, rot=None, pivot=None, **f):
    """縦長の箱を、断面が八角の柱にする（少し細めた箱と、Y で 45 度回した箱を重ねる。角が落ちて四角く見えない）"""
    (x0, y0, z0), (x1, y1, z1) = a, c
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    w, d = abs(x1 - x0), abs(z1 - z0)
    B(bone, (cx - w * 0.41, y0, cz - d * 0.41), (cx + w * 0.41, y1, cz + d * 0.41), mat, rot=rot, pivot=pivot, **f)
    s = 0.37
    piv = pivot if pivot else (cx, (y0 + y1) / 2, cz)
    r2 = (rot[0], rot[1] + 45, rot[2]) if rot else (0, 45, 0)
    B(bone, (cx - w * s, y0 + 0.05, cz - d * s), (cx + w * s, y1 - 0.05, cz + d * s), mat, rot=r2, pivot=piv, **f)


def taper(B, bone, a, c, mat, steps=3, k=0.7, top_wide=True, **f):
    """y の向きに段々に細くなる八角の柱（top_wide なら上が太い）"""
    (x0, y0, z0), (x1, y1, z1) = a, c
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    w, d = abs(x1 - x0), abs(z1 - z0)
    hh = (y1 - y0) / steps
    for i in range(steps):
        t = (i + 0.5) / steps
        sc = (k + (1 - k) * t) if top_wide else (1 - (1 - k) * t)
        ya, yb = y0 + i * hh, y0 + (i + 1) * hh + 0.02
        octo(B, bone, (cx - w * sc / 2, ya, cz - d * sc / 2), (cx + w * sc / 2, yb, cz + d * sc / 2), mat, **f)


def spike(B, bone, at, size, length, mat, rot=(0, 0, 0)):
    """尖り: 細くなる 3 つの菱形（Z で 45 度）を rot の向きへ伸ばす。at は付け根、rot は付け根を軸にした向き（度）"""
    x, y, z = at
    for i, (sc, ln) in enumerate(((1.0, 0.45), (0.65, 0.35), (0.32, 0.2))):
        y0 = y + length * sum(l for _, l in ((1.0, 0.45), (0.65, 0.35), (0.32, 0.2))[:i])
        s = size * sc / 2
        B(bone, (x - s, y0, z - s), (x + s, y0 + length * ln, z + s), mat, rot=(rot[0], rot[1] + 45, rot[2]), pivot=at)


def rz(p, pivot, z_rot):
    """点 p を pivot のまわりに、geo の箱の回転 (0, 0, z_rot) と同じだけ回す（尖った先の菱形を板の先へ置くため）"""
    a = math.radians(-z_rot)
    dx, dy = p[0] - pivot[0], p[1] - pivot[1]
    return (pivot[0] + dx * math.cos(a) - dy * math.sin(a), pivot[1] + dx * math.sin(a) + dy * math.cos(a), p[2])


def rx(p, pivot, x_rot):
    """点 p を pivot のまわりに、geo の箱の回転 (x_rot, 0, 0) と同じだけ回す"""
    a = math.radians(-x_rot)
    dy, dz = p[1] - pivot[1], p[2] - pivot[2]
    return (p[0], pivot[1] + dy * math.cos(a) - dz * math.sin(a), pivot[2] + dy * math.sin(a) + dz * math.cos(a))


def time_ruler():
    m = g.Model('time_ruler')
    B, MB = m.box, m.mbox
    m.bone('root')

    # ---- 脚（八角の太もも・尖った膝・細くなる脛・尖った足先の刃）
    for name, s in (('leg_right', -1), ('leg_left', 1)):
        m.bone(name, 'root', (s * 4.5, 36, 0))
        X = s * 4.6
        taper(B, name, (X - 3.1, 26, -3.2), (X + 3.1, 36, 3.2), 'white', steps=2, k=0.82, top_wide=True)       # 太もも
        octo(B, name, (X - 3.4, 30, -3.5), (X + 3.4, 31.2, 3.5), 'goldp')                                         # 太ももの金の帯
        B(name, (X - 2.4, 21.6, -4.6), (X + 2.4, 26.2, -2.8), 'goldp', rot=(-12, 0, 0), pivot=(X, 24, -3.7))      # 膝当て
        spike(B, name, (X, 25.0, -4.4), 2.2, 4.0, 'gold', rot=(70, 0, 0))                                        # 膝の前へ尖る角
        ring(B, name, X, 23.4, -4.9, 2.2, 10, (1.2, 0.9, 0.7), 'gold')                                            # 膝の金の歯車
        B(name, (X - 0.8, 22.6, -5.3), (X + 0.8, 24.2, -4.6), 'vglow')
        # 脛から下は足先へ太くなる（2026-10-19 ユーザー指定「足先の方が太くなるように」）: 下へ広がる脛、広がった脛当ての縁、太い足首、大きな革靴
        taper(B, name, (X - 3.4, 10, -3.6), (X + 3.4, 22, 3.6), 'white', steps=4, k=0.68, top_wide=False)      # 脛（下へ太く）
        octo(B, name, (X - 2.6, 18.4, -2.8), (X + 2.6, 19.6, 2.8), 'goldp')                                      # 脛の上の金の帯
        shin = dict(rot=(-8, 0, 0), pivot=(X, 21, -2.6))
        B(name, (X - 1.5, 10.4, -4.1), (X + 1.5, 21, -2.4), 'whites', **shin)                                    # 脛の前の板（下が前へ出る）
        B(name, (X - 0.5, 10.8, -4.35), (X + 0.5, 20.2, -3.95), 'purple', **shin)                                # 紫の筋
        tx, ty, tz = rx((X, 10.0, -3.25), (X, 21, -2.6), -8)
        B(name, (tx - 1.2, ty - 1.2, tz - 0.8), (tx + 1.2, ty + 1.2, tz + 0.8), 'goldp', rot=(-8, 0, 45), pivot=(tx, ty, tz))   # 板の下の尖り
        octo(B, name, (X - 4.0, 9.2, -4.2), (X + 4.0, 10.8, 4.2), 'goldp')                                       # 広がった脛当ての縁
        for k in range(4):                                                                                     # 縁から外へ反る 4 枚の板
            a = k * 90 + 45
            px, pz = X + 3.6 * math.cos(math.radians(a)), 3.6 * math.sin(math.radians(a))
            B(name, (px - 1.1, 9.0, pz - 0.3), (px + 1.1, 12.0, pz + 0.3), 'gold', rot=(0, -a + 90, 0), pivot=(px, 10.5, pz))
        octo(B, name, (X - 2.9, 6.4, -2.9), (X + 2.9, 9.4, 2.9), 'frame')                                         # 太い足首
        octo(B, name, (X - 3.2, 7.0, -3.2), (X + 3.2, 8.4, 3.2), 'goldp')
        # 尖った革靴の形の足（2026-10-19 ユーザー指定「足先を革靴のようにシャープに」）: 踵・甲・先へ細くなる爪先・金の爪先、暗い革の靴底と踵。
        # 浮いているので、足首を軸に爪先を斜め下へ向ける（x が正で前が下がる）
        foot = dict(rot=(38, 0, 0), pivot=(X, 7.0, 0))
        B(name, (X - 2.8, 2.6, -1.6), (X + 2.8, 7.4, 3.4), 'white', top='goldp', **foot)                         # 踵と足首を包む甲
        B(name, (X - 2.6, 2.6, -5.2), (X + 2.6, 6.4, -1.6), 'whites', **foot)                                    # 甲
        B(name, (X - 2.1, 2.6, -8.0), (X + 2.1, 5.3, -5.2), 'whites', **foot)                                    # 前へ細く
        B(name, (X - 1.5, 2.6, -10.0), (X + 1.5, 4.4, -8.0), 'goldp', **foot)                                    # 金の爪先
        B(name, (X - 0.8, 2.6, -11.6), (X + 0.8, 3.6, -10.0), 'gold', **foot)                                    # 尖った先
        B(name, (X - 2.9, 2.0, -10.2), (X + 2.9, 2.65, 3.5), 'leather', **foot)                                 # 革の靴底
        B(name, (X - 2.0, 0.4, 0.9), (X + 2.0, 2.05, 3.3), 'leather', **foot)                                    # 踵
        B(name, (X - 2.85, 5.0, -1.65), (X + 2.85, 5.7, 3.45), 'goldp', **foot)                                 # 足首の金の帯
        B(name, (X - 0.7, 4.6, -5.0), (X + 0.7, 5.5, -1.2), 'gold', **foot)                                      # 甲の金の筋

    # ---- 胴（細い腰・尖った前垂れ・重ねた腰の板・八角の胸・V の胸当て・尖った襟）
    m.bone('body', 'root', (0, 40, 0))
    octo(B, 'body', (-6.0, 34, -4.4), (6.0, 40, 4.4), 'frame')                                              # 細い腰
    octo(B, 'body', (-7.0, 38, -5.0), (7.0, 40.6, 5.0), 'goldp')                                            # 帯
    B('body', (-1.2, 38.0, -5.9), (1.2, 40.4, -5.1), 'vglow', sides='gold', rot=(0, 0, 45), pivot=(0, 39.2, -5.5))   # 帯の菱形の宝石
    B('body', (-3.0, 15.5, -5.9), (3.0, 38, -5.0), 'tabard', back='purple', rot=(-5, 0, 0), pivot=(0, 38, -5.4))     # 紫の前垂れ
    B('body', (-2.12, 12.6, -5.85), (2.12, 16.8, -5.05), 'tabard', back='purple', rot=(-5, 0, 45), pivot=(0, 15.5, -5.4))   # 前垂れの尖った裾
    MB('body', (-3.6, 15.5, -6.0), (-2.8, 38, -4.9), 'gold', rot=(-5, 0, 0), pivot=(0, 38, -5.4))
    for side in (-1, 1):                                                                                  # 腰の左右に重ねた尖った板（外へ開く）
        for i in range(3):
            x = side * (5.0 + i * 1.6)
            zr = -side * (8 + i * 6)
            B('body', (x - 1.4, 25 - i * 1.5, -4.6 + i * 0.6), (x + 1.4, 38, 4.6 - i * 0.6), 'purple' if i < 2 else 'white',
              rot=(0, 0, zr), pivot=(x, 38, 0))
            tx, ty, _ = rz((x, 25 - i * 1.5, 0), (x, 38, 0), zr)
            B('body', (tx - 0.99, ty - 1.4, -4.0 + i * 0.6), (tx + 0.99, ty + 1.4, 4.0 - i * 0.6), 'gold',
              rot=(0, 0, zr + 45), pivot=(tx, ty, 0))                                                    # 板の尖った先
    for i, y in enumerate((40.5, 42.6, 44.7)):                                                           # 重ねた腹の板（V の向き）
        MB('body', (-6.2, y, -5.8 - i * 0.15), (-0.2, y + 2.3, -4.6), 'white' if i % 2 == 0 else 'whites', rot=(-10, -10, 12), pivot=(-0.2, y + 2.3, -5.2))
    octo(B, 'body', (-8.5, 46, -5.5), (8.5, 60, 5.5), 'frame')                                              # 胸の骨組み（八角）
    # 山なりに尖った胸（2026-10-19 ユーザー指定「胸の装甲を山なりにとがらせて」）: 左右の胸当ては外の縁が後ろへ下がり、真ん中で前へ合わさる舳先（-x 側の y が負。ゲームで確かめた向き。y を正にしたら胸がへこんだ）。
    # 上の縁は真ん中が高い山なり（z が負で外へ下がる）。真ん中に前へ張り出す菱形の稜線（核の上と下）
    MB('body', (-9.8, 49.5, -8.0), (-0.3, 59.6, -5.2), 'white', front='whites', top='goldp', rot=(10, -28, -12), pivot=(-0.3, 54.6, -7.0))   # 胸当て
    MB('body', (-9.4, 58.9, -8.3), (-0.4, 59.9, -7.4), 'goldp', rot=(10, -28, -12), pivot=(-0.3, 54.6, -7.0))       # 胸当ての上の金の縁
    MB('body', (-7.6, 51.8, -8.25), (-1.2, 52.4, -7.95), 'gold', rot=(10, -28, -12), pivot=(-0.3, 54.6, -7.0))      # 胸当ての金の筋
    MB('body', (-8.4, 45.8, -7.4), (-0.4, 50.4, -5.2), 'whites', rot=(-8, -22, 14), pivot=(-0.4, 48.1, -6.4))   # 胸の下の板（V に下がる）
    B('body', (-1.5, 55.6, -9.4), (1.5, 60.0, -6.4), 'goldp', rot=(0, 45, 0), pivot=(0, 57.8, -7.9))           # 上の稜線
    B('body', (-1.3, 45.6, -9.0), (1.3, 51.4, -6.4), 'goldp', rot=(0, 45, 0), pivot=(0, 48.5, -7.7))           # 下の稜線
    B('body', (-1.6, 44.2, -7.6), (1.6, 47.4, -5.6), 'goldp', rot=(0, 0, 45), pivot=(0, 45.8, -6.6))       # 胸の V の尖り
    MB('body', (-10.2, 46.5, -5.0), (-8.4, 59, 5.0), 'white', out='whites', rot=(0, 0, -10), pivot=(-8.4, 59, 0))
    B('body', (-1.0, 51.0, -8.6), (1.0, 56.0, -6.6), 'goldp', rot=(0, 45, 0), pivot=(0, 53.5, -7.6))           # 核の後ろの竜骨
    ring(B, 'body', 0, 53.5, -9.9, 2.4, 10, (1.4, 0.8, 0.8), 'gold')                                           # 胸の金の飾り
    B('body', (-1.1, 52.4, -10.2), (1.1, 54.6, -9.6), 'vglow', sides='gold', rot=(0, 0, 45), pivot=(0, 53.5, -9.9))   # 胸の紫の宝石
    MB('body', (-7.0, 59.2, -5.2), (-0.8, 61.8, 5.2), 'goldp', top='gold', rot=(-6, 0, 18), pivot=(-0.8, 59.2, 0))   # 襟
    spike(B, 'body', (-6.4, 61.0, -3.2), 1.6, 3.6, 'gold', rot=(0, 0, -50))                                 # 襟の外へ尖る角
    spike(B, 'body', (6.4, 61.0, -3.2), 1.6, 3.6, 'gold', rot=(0, 0, 50))
    octo(B, 'body', (-2.4, 59.5, -2.4), (2.4, 63.5, 2.4), 'frame')                                          # 首
    octo(B, 'body', (-4.2, 46, 5.2), (4.2, 60, 7.4), 'white')                                               # 背の板
    B('body', (-1.6, 56, 7.2), (1.6, 63, 10.4), 'goldp', rot=(0, 0, 0))                                    # 文字盤の支え

    # ---- 背のマント（体に沿って曲がる 3 枚ずつ、尖った裾と金の房）
    m.bone('cape', 'body', (0, 58, 5))
    for side in (-1, 1):
        for i in range(3):
            x0, x1 = 1.0 + i * 4.3, 6.0 + i * 4.3                                                        # 隣と少し重ねる
            bottom = 15.0 - i * 1.2
            zr = -side * (3 + i * 4)                                                                     # 外へ開く
            piv = (side * (x0 + x1) / 2, 58, 5.8)
            B('cape', (side * x0, bottom, 5.2 + i * 0.05), (side * x1, 58, 6.4 + i * 0.05), 'purple', rot=(0, 0, zr), pivot=piv)
            B('cape', (side * x0, bottom - 0.6, 5.0), (side * x1, bottom + 0.6, 6.6), 'goldp', rot=(0, 0, zr), pivot=piv)   # 裾の金の縁
            if i == 2:
                B('cape', (side * (x1 - 0.7), bottom, 5.0), (side * x1, 58, 6.6), 'gold', rot=(0, 0, zr), pivot=piv)         # 外の縁
            tx, ty, _ = rz((side * (x0 + x1) / 2, bottom - 0.2, 0), piv, zr)
            B('cape', (tx - 1.6, ty - 1.6, 5.3), (tx + 1.6, ty + 1.6, 6.3), 'purple', rot=(0, 0, zr + 45), pivot=(tx, ty, 5.8))   # 尖った裾
            px2, py2, _ = rz((side * (x0 + x1) / 2, bottom - 2.6, 0), piv, zr)
            B('cape', (px2 - 0.35, py2 - 2.4, 5.5), (px2 + 0.35, py2 + 0.4, 6.1), 'gold', rot=(0, 0, zr), pivot=(px2, py2, 5.8))  # 先の金の房
            B('cape', (px2 - 0.55, py2 - 3.0, 5.3), (px2 + 0.55, py2 - 2.2, 6.3), 'gglow', rot=(0, 0, zr), pivot=(px2, py2, 5.8))

    # ---- 頭（八角の兜・尖った顎と稜線・後ろへ流れる刃の飾り・面頬・冠・光輪）
    m.bone('head', 'body', (0, 63, 0))
    taper(B, 'head', (-4.6, 63, -4.6), (4.6, 72.4, 4.6), 'white', steps=2, k=0.86, top_wide=False)          # 兜（八角、上へ少し細く）
    B('head', (-2.1, 61.2, -5.4), (2.1, 64.8, -2.6), 'whites', rot=(10, 0, 45), pivot=(0, 63, -4.0))        # 尖った顎
    B('head', (-0.9, 67, -5.8), (0.9, 74.4, 2.4), 'goldp', top='gold', rot=(-20, 0, 0), pivot=(0, 70, -2))  # 額の稜線
    spike(B, 'head', (0, 73.0, -3.4), 1.8, 4.2, 'gold', rot=(-20, 0, 0))                                   # 稜線の先の角（少し後ろへ）
    MB('head', (-5.6, 63.4, -4.2), (-4.4, 69.4, 2.6), 'goldp', rot=(0, -14, 6), pivot=(-4.4, 66, -1))      # 頬当て
    spike(B, 'head', (-5.0, 64.0, 1.2), 1.4, 3.8, 'gold', rot=(-70, 0, 10))                                 # 頬当ての後ろ上へ尖る
    spike(B, 'head', (5.0, 64.0, 1.2), 1.4, 3.8, 'gold', rot=(-70, 0, -10))
    for side in (-1, 1):                                                                                 # 後ろへ流れる刃の飾り（細くなる）
        for i, (w2, ln) in enumerate(((3.0, 5.0), (2.0, 4.0), (1.0, 3.0))):
            z0 = 4.0 + sum(l for _, l in ((3.0, 5.0), (2.0, 4.0), (1.0, 3.0))[:i])
            B('head', (side * 2.5 - w2 / 2, 69.8, z0), (side * 2.5 + w2 / 2, 72.2 - i * 0.5, z0 + ln), 'whites' if i < 2 else 'gglow',
              top='goldp' if i == 0 else 'whites', rot=(22, side * -12, 0), pivot=(side * 2.5, 71, 4))
    m.bone('visor', 'head', (0, 67, -4.8))
    # T 字の面頬（2026-10-19 ユーザー指定「顔面のバイザーを T 字に」）: 白い面頬に、目の高さの横の切れ込みと真ん中を下る縦の切れ込み（暗い紫、奥がかすかに光る）
    B('visor', (-3.7, 63.8, -5.2), (3.7, 70.2, -4.6), 'whites', sides='gold', top='gold', bottom='gold')
    B('visor', (-3.3, 67.6, -5.42), (3.3, 68.9, -5.15), 'visor')                                            # 横の切れ込み（目）
    B('visor', (-0.6, 64.4, -5.42), (0.6, 67.7, -5.15), 'visor')                                            # 縦の切れ込み
    B('visor', (-3.5, 68.9, -5.38), (3.5, 69.3, -5.1), 'gold')                                              # 切れ込みの上の金の縁
    B('visor', (-1.8, 62.6, -5.15), (1.8, 65.0, -4.65), 'whites', rot=(0, 0, 45), pivot=(0, 63.8, -4.9))   # 面頬の下の尖り
    B('visor', (-3.9, 69.9, -5.4), (3.9, 70.6, -4.5), 'gold')
    octo(B, 'head', (-5.4, 72, -5.4), (5.4, 74.2, 5.4), 'goldp', top='gold')                               # 冠の輪（八角）
    for k in range(10):                                                                                  # 冠の尖った突起と宝石
        a = 2 * math.pi * k / 10 - math.pi / 2
        x, z = 4.6 * math.cos(a), 4.6 * math.sin(a)
        h = 3.6 + (3.0 if k == 0 else 1.6 if k in (1, 9) else 0.0)
        spike(B, 'head', (x, 74.2, z), 1.5, h, 'gold')
        B('head', (x - 0.5, 74.2 + h * 0.45, z - 0.5), (x + 0.5, 74.2 + h * 0.45 + 1.0, z + 0.5), 'vglow' if k % 2 == 0 else 'gglow', rot=(0, 45, 45), pivot=(x, 74.2 + h * 0.45 + 0.5, z))
    B('head', (-0.85, 74.8, -5.8), (0.85, 76.5, -5.0), 'vglow', sides='gold', rot=(0, 0, 45), pivot=(0, 75.65, -5.4))
    m.bone('halo_top', 'head', (0, 81, 0))
    ring(B, 'halo_top', 0, 81, 0, 6.2, 32, (1.4, 0.6, 0.8), 'gglow', plane='xz')
    for name, s in (('halo_left', 1), ('halo_right', -1)):
        m.bone(name, 'head', (s * 9, 71, 0))
        ring(B, name, s * 9, 71, 0, 3.4, 20, (1.1, 0.5, 0.5), 'gglow', plane='zy')

    # ---- 腕（重ねた尖った肩当て・紫の垂れ布・八角の腕・尖った肘・広がる籠手の縁・尖った指）
    for name, s in (('arm_right', -1), ('arm_left', 1)):
        m.bone(name, 'body', (s * 11, 57, 0))

        def X(a, c, mat, rot=None, pivot=None, **f):
            r2 = (rot[0], rot[1] * -s, rot[2] * -s) if rot else None
            B(name, (s * a[0], a[1], a[2]), (s * c[0], c[1], c[2]), mat, rot=r2, pivot=(s * pivot[0], pivot[1], pivot[2]) if pivot else None, **f)

        def SP(at, size, length, mat, rot=(0, 0, 0)):
            spike(B, name, (s * at[0], at[1], at[2]), size, length, mat, rot=(rot[0], rot[1] * -s, rot[2] * -s))
        # 複雑な肩当て（2026-10-19 ユーザー指定「肩の鎧を複雑に」）: 上の丸い板（2 段）と金の稜線、外へ垂れる 5 枚の重ね板（白と金の縁を交互）、
        # 上へ反る外の翼の板、前後の宝石付きの金の円盤、下の縁の金の菱形、稜線の上の大きさ違いの 3 本の角
        X((7.8, 56.0, -5.8), (15.6, 62.0, 5.8), 'white', top='goldp', out='whites', rot=(0, 0, -10), pivot=(9, 58, 0))   # 上の丸い板
        X((8.6, 61.6, -4.8), (14.6, 63.2, 4.8), 'whites', top='white', rot=(0, 0, -10), pivot=(9, 58, 0))              # その上の段
        X((10.4, 63.0, -5.2), (11.6, 64.2, 5.2), 'goldp', rot=(0, 0, -10), pivot=(9, 58, 0))                            # 金の稜線
        for i in range(5):                                                                                # 外へ垂れる 5 枚の重ね板
            zr = -(16 + i * 8)
            a0, a1 = 13.8 + i * 0.9, 17.4 + i * 0.7
            yb = 55.4 - i * 1.5
            dz = 5.9 - i * 0.35
            X((a0, yb, -dz), (a1, yb + 3.4, dz), 'white' if i % 2 == 0 else 'whites', top='goldp' if i % 2 == 0 else 'white', rot=(0, 0, zr), pivot=(9, 58, 0))
            X((a0 + 0.2, yb - 0.5, -dz - 0.2), (a1 + 0.2, yb + 0.2, dz + 0.2), 'goldp' if i % 2 == 0 else 'gold', rot=(0, 0, zr), pivot=(9, 58, 0))   # 下の縁
        X((15.6, 59.4, -4.6), (16.6, 65.6, 4.6), 'white', out='whites', top='goldp', rot=(0, 0, 18), pivot=(16, 59.4, 0))   # 上へ反る外の翼の板
        X((15.4, 65.2, -4.8), (16.8, 66.2, 4.8), 'gold', rot=(0, 0, 18), pivot=(16, 59.4, 0))
        for zz in (-1, 1):                                                                                # 前後の金の円盤と紫の宝石
            ring(B, name, s * 12.8, 58.8, zz * 6.3, 1.9, 10, (1.2, 0.7, 0.7), 'gold')
            B(name, (s * 12.8 - 0.75, 58.05, zz * 6.3 - 0.45), (s * 12.8 + 0.75, 59.55, zz * 6.3 + 0.45), 'vglow', rot=(0, 0, 45), pivot=(s * 12.8, 58.8, zz * 6.3))
            for k in range(3):                                                                            # 縁の金の菱形の飾り
                X((10.0 + k * 2.2, 55.4, zz * 6.0 - 0.4), (10.8 + k * 2.2, 56.2, zz * 6.0 + 0.4), 'gold', rot=(0, 0, 45), pivot=(10.4 + k * 2.2, 55.8, zz * 6.0))
        SP((11.0, 63.6, 0.0), 2.4, 6.4, 'gold', rot=(0, 0, -18))                                          # 稜線の上の 3 本の角（外ほど小さい）
        SP((11.0, 63.4, -3.4), 1.7, 4.4, 'gold', rot=(16, 0, -26))
        SP((11.0, 63.4, 3.4), 1.7, 4.4, 'gold', rot=(-16, 0, -26))
        X((15.2, 56.6, -1.0), (16.6, 58.6, 1.0), 'vglow', rot=(0, 0, 45), pivot=(15.9, 57.6, 0))
        X((9.0, 46.5, -5.2), (16.0, 52.6, 5.2), 'purple', out='purple', rot=(0, 0, 10), pivot=(10, 52.6, 0))   # 肩の下の紫の垂れ布
        for k in range(4):
            z = -3.6 + k * 2.4
            X((14.4, 44.0, z - 1.0), (16.0, 47.4, z + 1.0), 'purple', rot=(0, 0, 10 + 0), pivot=(10, 52.6, 0))
            X((14.6, 42.6, z - 0.35), (15.8, 44.4, z + 0.35), 'gold', rot=(0, 0, 10), pivot=(10, 52.6, 0))
        taper(B, name, (s * 11.3 - 2.3, 46, -2.6), (s * 11.3 + 2.3, 56, 2.6), 'frame', steps=2, k=0.85)    # 二の腕
        octo(B, name, (s * 11.3 - 2.7, 43.4, -3.2), (s * 11.3 + 2.7, 46.4, 3.2), 'goldp')                    # 肘
        SP((11.3, 44.6, 2.4), 1.6, 4.0, 'gold', rot=(-120, 0, 0))                                          # 肘の後ろへ尖る角
        taper(B, name, (s * 11.4 - 3.0, 34, -3.3), (s * 11.4 + 3.0, 43.6, 3.3), 'white', steps=3, k=0.72, top_wide=True)   # 籠手（手首へ細く）
        for zz in (-1, 1):                                                                                # 籠手の上の広がる縁（尖る）
            X((8.0, 41.0, zz * 3.6 - 0.5), (14.8, 44.4, zz * 3.6 + 0.5), 'goldp', rot=(-zz * 22, 0, 0), pivot=(11.4, 41.0, zz * 3.6))
        X((7.6, 41.0, -3.6), (8.6, 44.6, 3.6), 'goldp', rot=(0, 0, 20), pivot=(8.4, 41.0, 0))
        X((8.5, 36.0, -1.0), (9.0, 41.0, 1.0), 'gglow')
        octo(B, name, (s * 11.4 - 1.9, 30, -2.3), (s * 11.4 + 1.9, 34, 2.3), 'frame')                         # 手
        for k in range(3):
            X((9.8 + k * 1.2, 27.2, -2.2), (10.7 + k * 1.2, 30.2, -1.2), 'gold', rot=(-15, 0, 0), pivot=(10.2 + k * 1.2, 30.2, -1.7))
            X((9.95 + k * 1.2, 25.8, -2.0), (10.55 + k * 1.2, 27.4, -1.4), 'gold', rot=(-30, 0, 0), pivot=(10.2 + k * 1.2, 27.4, -1.7))   # 尖った指先

    # ---- 背の文字盤（体の左上の後ろ。紫の帯に金の数字、金の内輪・外輪、12 の尖った光の棘）
    cx, cy, cz = 10, 62, 11
    m.bone('dial', 'body', (cx, cy, cz))
    ring(B, 'dial', cx, cy, cz, 30, 60, (3.3, 4.6, 1.6), 'band')
    ring(B, 'dial', cx, cy, cz - 0.3, 27.2, 60, (3.0, 1.2, 1.8), 'goldp')
    ring(B, 'dial', cx, cy, cz - 0.3, 33.0, 60, (3.6, 1.6, 1.8), 'goldp', every=5, alt='gold')
    for k in range(12):                                                                                  # 外へ尖る光の棘
        a = 2 * math.pi * k / 12
        long = k % 3 == 0
        x, y = cx + 33.8 * math.cos(a), cy + 33.8 * math.sin(a)
        ln = 6.0 if long else 3.6
        deg = 90 - math.degrees(a)
        for i, (w2, l2) in enumerate(((1.6, 0.5), (1.0, 0.3), (0.5, 0.2))):
            off = ln * sum(l for _, l in ((1.6, 0.5), (1.0, 0.3), (0.5, 0.2))[:i])
            px, py = x + off * math.cos(a), y + off * math.sin(a)
            B('dial', (px - w2 / 2, py, cz - 0.6), (px + w2 / 2, py + ln * l2, cz + 0.6), 'gold' if i < 2 else 'gglow', rot=(0, 0, deg), pivot=(px, py, cz))
    ring(B, 'dial', cx, cy, cz - 0.5, 24.0, 60, (0.4, 1.2, 0.6), 'gold')
    for k in range(12):                                                                                  # 中の尖った花弁
        a = 2 * math.pi * k / 12
        x, y = cx + 7.0 * math.cos(a), cy + 7.0 * math.sin(a)
        B('dial', (x - 1.1, y - 2.4, cz - 0.6), (x + 1.1, y + 2.4, cz + 0.4), 'goldp', rot=(0, 0, 90 - math.degrees(a)), pivot=(x, y, cz))
        x2, y2 = cx + 9.6 * math.cos(a), cy + 9.6 * math.sin(a)
        B('dial', (x2 - 0.7, y2 - 0.7, cz - 0.6), (x2 + 0.7, y2 + 0.7, cz + 0.4), 'gold', rot=(0, 0, math.degrees(a) + 45), pivot=(x2, y2, cz))
    for k in range(4):
        B('dial', (cx - 0.5, cy + 3, cz - 0.3), (cx + 0.5, cy + 27, cz + 0.5), 'gold', rot=(0, 0, k * 90 + 45), pivot=(cx, cy, cz))
    B('dial', (cx - 2.4, cy - 2.4, cz - 1.2), (cx + 2.4, cy + 2.4, cz + 1.0), 'gglow', sides='gold', rot=(0, 0, 45), pivot=(cx, cy, cz))
    m.bone('hand_hour', 'dial', (cx, cy, cz - 1.5))
    for i, (w2, y0, y1) in enumerate(((2.0, 0, 12), (1.4, 12, 17), (0.8, 17, 20))):                     # 先へ細くなる短針
        B('hand_hour', (cx - w2 / 2, cy + y0, cz - 2.0), (cx + w2 / 2, cy + y1, cz - 1.2), 'gold', front='gglow')
    B('hand_hour', (cx - 1.7, cy + 14.3, cz - 2.0), (cx + 1.7, cy + 17.7, cz - 1.2), 'goldp', rot=(0, 0, 45), pivot=(cx, cy + 16, cz - 1.6))
    m.bone('hand_minute', 'dial', (cx, cy, cz - 2.0))
    for i, (w2, y0, y1) in enumerate(((1.5, -6, 24), (1.0, 24, 36), (0.5, 36, 40))):                    # 輪の外まで伸びる、先へ細くなる長針
        B('hand_minute', (cx - w2 / 2, cy + y0, cz - 2.6), (cx + w2 / 2, cy + y1, cz - 1.8), 'gold', front='gglow')
    B('hand_minute', (cx - 1.27, cy - 9.3, cz - 2.6), (cx + 1.27, cy - 4.7, cz - 1.8), 'goldp', rot=(0, 0, 45), pivot=(cx, cy - 7, cz - 2.2))   # 菱形の重り
    for k in range(7):                                                                                   # 先の三日月の飾り（光る、細い）
        a = math.radians(190 + k * 27)
        x, y = cx + 2.8 * math.cos(a), cy + 42.4 + 2.8 * math.sin(a)
        wk = 1.2 - abs(k - 3) * 0.22
        B('hand_minute', (x - wk / 2, y - wk / 2, cz - 2.7), (x + wk / 2, y + wk / 2, cz - 1.7), 'gglow', rot=(0, 0, math.degrees(a) + 45), pivot=(x, y, cz - 2.2))
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
    if mat == 'visor':                                                                  # T 字の切れ込み: 暗い紫、真ん中ほど奥がかすかに光る
        for y in range(h):
            for x in range(w):
                d = max(abs((x + 0.5) / w - 0.5), abs((y + 0.5) / h - 0.5)) * 2
                col = _mix(_c(0x8A50D0), _c(0x10081C), d * 1.3)
                P(x, y, col, d < 0.45)
        return
    if mat == 'leather':                                                                # つやのある暗い革（上の縁に光の筋）
        for y in range(h):
            for x in range(w):
                t = (y + 0.5) / h
                col = _mix(_c(0x5A4034), _c(0x1A100C), t)
                if y == 1 or (x + y) % 11 == 0:
                    col = _mix(col, (200, 170, 150), 0.35)
                P(x, y, col)
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
