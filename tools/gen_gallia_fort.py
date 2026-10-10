"""
AEC 級英雄機「特殊要塞型【重護屋 / ガリアフォート】」の GeckoLib モデルとテクスチャを作る（2026-10-10 ユーザー指定「大型の移動要塞メカ」）。

    python tools/gen_gallia_fort.py

出力（assets/automata_valkyries/ の下）:
  geo/entity/galliafort.geo.json                 … format 1.12.0、identifier geometry.galliafort
  textures/entity/galliafort.png（＋ _glowmask.png）… 1024x1024。塗り・詰め込みは gen_chrono_gazers.py の write を使う

モデルは実寸の半分で作り、描画（client/GalliaFortRenderer）で GalliaFortEntity.SIZE 倍（2 倍）にする。
骨（Java の ChronoGazerModel#animateGallia が動かす。名前と pivot を合わせること）:
  root > hull > cannon（主砲。上下に向ける・撃つと砲身が下がる）、hull > pod_left / pod_right > hatch_left / hatch_right（ミサイルの蓋）、
  hull > gun_left / gun_right（副砲。左右に振る）、hull > emitters（障壁の発生塔。光る先）、hull > pilot（操縦席の少女）
  root > leg_fl / leg_fr（人型ですね）、arm_* > leg_bl / leg_br（人型で前腕）、root > thigh_*（人型だけ）、hull > ... ; 以前は root > leg_fl / leg_fr / leg_bl / leg_br > *_tilt（逆台形の大きなキャタピラの 4 本脚。f = 前 -Z、b = 後ろ +Z、l = 左 +X、r = 右 -X）
コードで回す骨そのものには初期の向きを付けない。
色は敵の決まり（DESIGN_VALKYRIE_VISUAL_DB §2.4）: 暗いガンメタル・紫の差し色・冷たい赤の発光。障壁の発生塔は蒼い光（歪曲障壁の色）。
座標は GeckoLib（Bedrock）の geo の座標（px）。正面は -Z、右は -X。
"""
import os

import math

import gen_chrono_gazers as g

BLADE_X_FLIP = True    # 左右に薄い刃の絵の向き（プレビューで確かめた）
NORTH_ROOT_LEFT_WHEN_RIGHT = False  # 切り抜きの板の北の面で根元が u の左になるのは右（-X）の翼か（プレビューで確かめた）
TILT_SIGN = -1  # キャタピラを外へ傾ける X 回転の向き（プレビューで確かめた符号）


g.TEX = 1024
g.MAT.update({
    'gblue': (0x3A6BFF, 0xB8D0FF, 0x1E3A99, 'glow', True),        # 歪曲障壁の蒼い光
    'heavy': (0x30343E, 0x4C5260, 0x1A1C22, 'rivet', False),       # 重い外殻（鋲）
    'seat': (0x15171C, 0x23262E, 0x0B0C0F, 'plain', False),        # 操縦席の奥
    'skin': (0xF2D8C8, 0xFBE9DE, 0xD9B8A6, 'plain', False),
    'hair': (0xC9CEE6, 0xE6EAF8, 0x9AA0C0, 'plain', False),        # 銀の髪
    'suit': (0x2A2E40, 0x3E4460, 0x181A26, 'plain', False),
    'track': (0x1E2026, 0x34373F, 0x0E0F12, 'vent', False),       # 履帯
    'shoe': (0x2A2D35, 0x50555F, 0x111216, 'panel', False),       # 履板（真ん中に滑り止めの筋）
    'grouser': (0x3A3E48, 0x5E6470, 0x1C1E24, 'plain', False),   # 履板の滑り止めの突起
    'fitting': (0x6A707C, 0x9AA0AC, 0x3A3E46, 'plain', False),   # 履板の縁の金具
})


# 人型の逆関節の脚（px。Java の ChronoGazerModel と合わせる）: 腰の高さ、太ももの長さ、ひざ・足首の位置、すねの長さと傾き（geo の X 回転の度）
# 2026-10-10 ユーザー指定「胴体の部分を縮めて足を更に長く」: 腰を 50 → 64 へ上げ、太もも 15.6 → 21、すね 26 → 32
HIP_Y = 64.0
KNEE_Y, KNEE_Z = 46.0, -11.0
THIGH_LEN = math.hypot(HIP_Y - KNEE_Y, KNEE_Z)
ANKLE_Y, ANKLE_Z = 24.0, 12.0   # キャタピラを大きくした（2026-10-10「胴体をスリムにしてキャタピラを大型化」）ので足首を上げた
SHIN_LEN = math.hypot(KNEE_Y - ANKLE_Y, ANKLE_Z - KNEE_Z)
SHIN_ANG = math.degrees(math.atan2(ANKLE_Z - KNEE_Z, KNEE_Y - ANKLE_Y))   # geo の X 回転は標準の回転と符号が逆なので、下が後ろへ行くのは正
HULL_SCALE = 1.3   # 胴まわりの大きさ（Java の GalliaFortEntity の砲口などの位置はこれに合わせる）
HULL_DROP = 2.0    # 胴まわりを下げる分（px。左右のキャタピラの間に沈める。2026-10-10 ユーザー指定「胴の位置をさげて」→ キャタピラを大きくしたので 7 → 2。Java の HULL_DROP と合わせる）


T_PLATE = 3.0   # 台形の斜めの板の厚み
ZS = -1         # 横の板を内へ傾ける Z 回転の向き（プレビューで確かめた符号）


def _slope(side, b, t, y0, y1):
    """台形の 1 面の傾き（度）・斜めの長さ・面の中ほどの位置（前後なら z、左右なら x）・面の幅の半分・幅の中心"""
    H = y1 - y0
    if side in ('front', 'back'):
        i = 1 if side == 'front' else 2
        d = abs(t[i] - b[i])
        mid = (b[i] + t[i]) / 2
        return math.degrees(math.atan(d / H)), math.hypot(d, H), mid, (b[0] + t[0]) / 2, 0.0
    d = b[0] - t[0]
    mid = (b[0] + t[0]) / 2 * (1 if side == 'left' else -1)
    zc = ((b[1] + t[1]) / 2 + (b[2] + t[2]) / 2) / 2
    half = ((b[2] + t[2]) / 2 - (b[1] + t[1]) / 2) / 2
    return math.degrees(math.atan(d / H)), math.hypot(d, H), mid, half, zc


def _slope_box(m, bone, side, ang, ym, mid, y_lo, y_hi, out0, out1, half, c, mat):
    """斜めの面に沿った箱: 面から外への距離 out0〜out1、斜めに沿った y_lo〜y_hi（回す前の高さ）"""
    if side == 'front':
        m.box(bone, (c - half, y_lo, mid - out1), (c + half, y_hi, mid - out0), mat, rot=(-ang, 0, 0), pivot=(0, ym, mid))
    elif side == 'back':
        m.box(bone, (c - half, y_lo, mid + out0), (c + half, y_hi, mid + out1), mat, rot=(ang, 0, 0), pivot=(0, ym, mid))
    elif side == 'left':   # +X
        m.box(bone, (mid + out0, y_lo, c - half), (mid + out1, y_hi, c + half), mat, rot=(0, 0, ZS * ang), pivot=(mid, ym, 0))
    else:                  # right, -X
        m.box(bone, (mid - out1, y_lo, c - half), (mid - out0, y_hi, c + half), mat, rot=(0, 0, -ZS * ang), pivot=(mid, ym, 0))


def frustum(m, bone, b, t, y0, y1, mat, top):
    """台形の段（下の面 b・上の面 t は（半幅, 前の z, 後ろの z））: 上の面の大きさの芯の箱と、4 面の斜めの板"""
    m.box(bone, (-t[0], y0, t[1]), (t[0], y1, t[2]), mat, top=top)
    ym = (y0 + y1) / 2
    for side in ('front', 'back', 'left', 'right'):
        ang, L, mid, half, c = _slope(side, b, t, y0, y1)
        _slope_box(m, bone, side, ang, ym, mid, ym - L / 2, ym + L / 2, -T_PLATE, 0.0, half, c, mat)


def deco(m, bone, side, b, t, y0, y1, u, h, half, mat, dx=0.0):
    """台形の面に貼る飾り（帯・スリット）: 面の中ほどから斜めに沿って u（上が正）、長さ h、幅の半分 half"""
    ang, L, mid, _, c = _slope(side, b, t, y0, y1)
    ym = (y0 + y1) / 2
    _slope_box(m, bone, side, ang, ym, mid, ym + u - h / 2, ym + u + h / 2, -0.05, 0.4, half,
               c + dx if side in ('front', 'back') else c, mat)




# ---- 切り抜きの板（2026-10-10 ユーザー指定「② で翼を細かく」）: 薄い板の大きな面に、透けた部分を抜いた絵で輪郭を描く ----
# GeckoLib は箱しか描けない（poly_mesh は読むだけ）が、描画は entityCutoutNoCull なので透けた画素は抜ける。
# 材料の名前が 'cut:<種類>:<向き>' なら、下の絵を描く（向き 0 = 根元が u の左、1 = 右）。'none' は何も描かない（板の薄い縁を消す）
_orig_paint = g.Atlas.paint


def _blade_shape(x, y, kind):
    """刃・羽根の形（x: 根元 0 → 先 1、y: 上の縁 0 → 下 1）。中なら True"""
    if kind == 'main':
        bot = 1.0 - 0.78 * x ** 1.15
        if x > 0.22:
            bot -= 0.16 * ((x * 7.0) % 1.0)                 # 下の縁のギザギザ（羽根）
        top = 0.0
        for c in (0.30, 0.46, 0.62):                          # 上の縁の刻み
            if c <= x <= c + 0.035:
                top = 0.1
        return top <= y <= bot and x <= 0.995
    if kind == 'feather':
        bot = 1.0 - 0.55 * x
        bot -= 0.22 * ((x * 5.0) % 1.0) if x > 0.1 else 0.0
        top = 0.12 * x
        return top <= y <= bot
    # 'tip': 細く尖る小さなひれ
    return 0.5 * x <= y <= 1.0 - 0.45 * x and x <= 0.98


def _blade_hole(x, y, kind):
    """抜き穴（軽くする穴の列）"""
    if kind != 'main':
        return False
    for c in (0.12, 0.2, 0.28):
        if c <= x <= c + 0.045 and 0.42 <= y <= 0.6:
            return True
    return False


def _paint_cut(atlas, u, v, w, h, kind, flip):
    base, light, dark = 0x30343E, 0x4C5260, 0x15171C
    purple, glow = 0x7A44C0, 0x6C9BFF
    inside = lambda i, j: 0 <= i < w and 0 <= j < h and _blade_shape(((w - 1 - i) if flip else i) / max(1, w - 1), j / max(1, h - 1), kind) \
        and not _blade_hole(((w - 1 - i) if flip else i) / max(1, w - 1), j / max(1, h - 1), kind)
    for j in range(h):
        for i in range(w):
            if not inside(i, j):
                continue
            x = ((w - 1 - i) if flip else i) / max(1, w - 1)
            y = j / max(1, h - 1)
            edge = not (inside(i - 1, j) and inside(i + 1, j) and inside(i, j - 1) and inside(i, j + 1))
            c = base
            n = atlas.rnd.randint(-5, 5)
            r, gg, b = g.rgb(base)
            c = (max(0, min(255, r + n)) << 16) | (max(0, min(255, gg + n)) << 8) | max(0, min(255, b + n))
            if abs(((x * 4.0 + y * 0.35) % 1.0) - 0.5) < 0.5 / max(1, w / 4) * 1.2:
                c = dark                                       # 斜めの継ぎ目（装甲板の重なり）
            if 0.07 <= y <= 0.13 and x < 0.9 and kind == 'main':
                c = purple                                     # 上の縁の紫の帯
            g_on = False
            if edge:
                below = not inside(i, j + 1)
                c = glow if below else (light if not inside(i, j - 1) else dark)
                g_on = below                                   # 下の縁は蒼く光る
            if kind == 'tip' and x > 0.8:
                c, g_on = glow, True                           # 先が光る
            atlas.px(u + i, v + j, c, g_on)


def _paint(self, mat, w, h):
    if mat == 'none':
        return self.alloc(w, h)
    if isinstance(mat, str) and mat.startswith('cut:'):
        _, kind, flip = mat.split(':')
        u, v = self.alloc(w, h)
        _paint_cut(self, u, v, w, h, kind, flip == '1')
        return u, v
    return _orig_paint(self, mat, w, h)


g.Atlas.paint = _paint


def cut_plate(m, bone, x0, x1, y0, y1, z, kind, root_left_north, rot, pivot):
    """切り抜きの板（厚み 0.3）: 大きな面 2 枚（北・南）に kind の絵、縁は描かない。root_left_north = 北の面で根元が u の左"""
    fn = 'cut:%s:%d' % (kind, 0 if root_left_north else 1)
    fs = 'cut:%s:%d' % (kind, 1 if root_left_north else 0)
    m.box(bone, (x0, y0, z - 0.15), (x1, y1, z + 0.15), 'none', north=fn, south=fs, rot=rot, pivot=pivot)



def blade_z(m, bone, root, angle, length, width, kind='tip', z_off=0.0):
    """前後に薄い刃（切り抜きの板）: root（根元の真ん中）から、正面から見て +X を 0 度・反時計回りに angle 度の向きへ length、幅 width。
    人型の装甲をシャープにする（2026-10-10 ユーザー指定「人型の装甲をシャープに」）"""
    rx, ry, rz = root
    cut_plate(m, bone, rx, rx + length, ry - width / 2, ry + width / 2, rz + z_off, kind, True, (0, 0, -angle), (rx, ry, rz + z_off))


def blade_x(m, bone, root, angle, length, width, kind='tip', flip=BLADE_X_FLIP):
    """左右に薄い刃（切り抜きの板）: root から、横から見て +Z（後ろ）を 0 度・上へ回る向きを正に angle 度の向きへ length、幅 width（下へは負）"""
    rx, ry, rz = root
    fe = 'cut:%s:%d' % (kind, 1 if flip else 0)
    fw = 'cut:%s:%d' % (kind, 0 if flip else 1)
    m.box(bone, (rx - 0.15, ry - width / 2, rz), (rx + 0.15, ry + width / 2, rz + length), 'none', east=fe, west=fw,
          rot=(angle, 0, 0), pivot=(rx, ry, rz))


def binder(m, bone, sx, root, length, width, rise, sweep, thick=2.6):
    """X 字のバインダー 1 枚（2026-10-10「② で翼を細かく」で切り抜きの板の重なりに）: root から外（sx）へ length、幅 width、外へ上がる角度 rise（度。負で下がる）、
    後ろへ流す角度 sweep（度）。刃の形の主板（上の縁の刻み・下の縁の羽根のギザギザ・抜き穴・紫の帯・蒼く光る下の縁）、その下に少し開いた羽根の板 2 枚、先の光る細いひれ。
    根元には裏の骨組みのレールと関節の箱を残す"""
    rx, ry, rz = root
    piv = (rx, ry, rz)
    left = sx < 0 if NORTH_ROOT_LEFT_WHEN_RIGHT else sx > 0
    layers = (('main', 1.0, 1.0, 0.0, 0.0), ('feather', 0.82, 0.7, -9.0, 1.2), ('feather', 0.64, 0.55, -18.0, 2.4))
    for kind, lk, wk, drot, dz in layers:
        rot = (0, sx * sweep, -sx * (rise + drot))
        x0, x1 = sorted((rx, rx + sx * length * lk))
        top = ry + width * 0.5
        cut_plate(m, bone, x0, x1, top - width * wk, top, rz + dz, kind, left, rot, piv)
    rot = (0, sx * sweep, -sx * rise)
    t0, t1 = sorted((rx + sx * length * 0.92, rx + sx * length * 1.18))
    cut_plate(m, bone, t0, t1, ry + width * 0.5 - 4, ry + width * 0.5, rz - 0.6, 'tip', left, rot, piv)       # 先の光る細いひれ
    r0, r1 = sorted((rx + sx * 2, rx + sx * length * 0.55))
    m.box(bone, (r0, ry + width * 0.5 - 4.5, rz + 0.3), (r1, ry + width * 0.5 - 2.5, rz + 1.8), 'metal', rot=rot, pivot=piv)  # 裏の骨組みのレール
    j0, j1 = sorted((rx, rx + sx * 6))
    m.box(bone, (j0, ry + width * 0.5 - 6, rz - 1.6), (j1, ry + width * 0.5 + 0.5, rz + 2.2), 'heavy', top='plate', rot=rot, pivot=piv)  # 根元の箱


SHOE_T, SHOE_STEP, SHOE_TILT = 0.9, 2.2, 12.0   # 履板の厚み・並べる間隔・瓦のように傾ける角度（度）


TRACK_ROUND = 4.5   # 履帯の輪の角の丸み（px。2026-10-10 ユーザー指定「角をリアルにすこし丸めて」: 起動輪・転輪に巻き付くように）
TRACK_ROUND_TOP = 5.6   # 上の両端の角の丸み（起動輪・誘導輪をこの丸みの中心に置き、履帯が巻き付く。2026-10-10「履帯の上の歯車の位置があってない」）


def rounded_loop(pts, R, arc_step=0.6, centers=None):
    """凸な多角形 pts（(y, z)）の角を半径 R（数か、角ごとの列）で丸めた輪を、細かい点の列（閉じた折れ線）にする。centers があれば角ごとの丸みの中心と半径を足す"""
    out = []
    n = len(pts)
    for i in range(n):
        Rc = R[i] if isinstance(R, (list, tuple)) else R
        P, A, B = pts[i], pts[i - 1], pts[(i + 1) % n]
        d1 = (A[0] - P[0], A[1] - P[1]); l1 = math.hypot(*d1); d1 = (d1[0] / l1, d1[1] / l1)
        d2 = (B[0] - P[0], B[1] - P[1]); l2 = math.hypot(*d2); d2 = (d2[0] / l2, d2[1] / l2)
        half = math.acos(max(-1.0, min(1.0, d1[0] * d2[0] + d1[1] * d2[1]))) / 2
        r = min(Rc, 0.45 * min(l1, l2) * math.tan(half))                   # 短い辺では丸みを小さく
        t = r / math.tan(half)
        T1 = (P[0] + d1[0] * t, P[1] + d1[1] * t)
        T2 = (P[0] + d2[0] * t, P[1] + d2[1] * t)
        bis = (d1[0] + d2[0], d1[1] + d2[1]); lb = math.hypot(*bis); bis = (bis[0] / lb, bis[1] / lb)
        C = (P[0] + bis[0] * r / math.sin(half), P[1] + bis[1] * r / math.sin(half))
        if centers is not None:
            centers.append((C, r))
        a0 = math.atan2(T1[1] - C[1], T1[0] - C[0])
        a1 = math.atan2(T2[1] - C[1], T2[0] - C[0])
        da = (a1 - a0 + math.pi) % (2 * math.pi) - math.pi
        k = max(2, int(abs(da) * r / arc_step))
        for j in range(k + 1):
            a = a0 + da * j / k
            out.append((C[0] + r * math.cos(a), C[1] + r * math.sin(a)))
    return out


SHOE_SETS = 3   # 履板の組の数（描画で 1 組ずつ順に見せて、輪に沿って進んで見せる。ChronoGazerModel の G_SHOE_SETS と合わせる）


def track_shoes(m, bone, loop, x0, x1, z, Y):
    """輪 loop（(高さ, 前後の距離) の角を順に）の角を丸めた輪に沿って、薄い四角の履板を少し傾けて重ねながら並べ、外の面に滑り止めの突起（グローサー）を付ける。
    z・Y は前後・高さをモデルの座標に直す関数。履板は SHOE_SETS 組の骨（bone_s0, bone_s1, …）に分け、組 k は間隔の k/SHOE_SETS だけ輪に沿ってずらす。
    輪の向きは、下の帯（地面に着く所）で後ろ（+Z）へ進む向きにそろえる（組の番号が進むと、下の帯の履板が後ろへ・上の帯の履板が前へ進んで見える）"""
    pts = [(Y(a), z(b)) for a, b in loop]
    cy = sum(p[0] for p in pts) / len(pts)
    cz = sum(p[1] for p in pts) / len(pts)
    centers = []
    path = rounded_loop(pts, [TRACK_ROUND_TOP if a == max(q[0] for q in loop) else TRACK_ROUND for a, b in loop], centers=centers)
    low = min(range(len(path)), key=lambda i: path[i][0] + path[(i + 1) % len(path)][0])   # 一番低い辺（下の帯）
    if path[(low + 1) % len(path)][1] < path[low][1]:
        path.reverse()
    piv = m.by[bone]['pivot']
    sets = [m.bone(f'{bone}_s{k}', bone, piv) for k in range(SHOE_SETS)]
    segs = []
    total = 0.0
    for i in range(len(path)):
        A, B = path[i], path[(i + 1) % len(path)]
        L = math.hypot(B[0] - A[0], B[1] - A[1])
        if L > 1e-6:
            segs.append((total, A, B, L))
            total += L
    n = max(4, round(total / SHOE_STEP))
    step = total / n
    for i, kset in ((i, kset) for kset in range(SHOE_SETS) for i in range(n)):
        bone = sets[kset]
        s = (i + 0.5 + kset / SHOE_SETS) * step
        if s >= total:
            s -= total
        k = 0
        while k < len(segs) - 1 and segs[k][0] + segs[k][3] < s:
            k += 1
        s0, A, B, L = segs[k]
        t = (s - s0) / L
        py, pz = A[0] + (B[0] - A[0]) * t, A[1] + (B[1] - A[1]) * t
        uy, uz = (B[0] - A[0]) / L, (B[1] - A[1]) / L
        ny, nz = -uz, uy                                                # 外向きの法線（輪の中心から離れる向き）
        if (py - cy) * ny + (pz - cz) * nz < 0:
            ny, nz = -ny, -nz
        phi = math.degrees(math.atan2(-uy, uz))                        # 標準の回転で Z 軸を帯の向きへ
        c_y, c_z = py - ny * SHOE_T * 0.5, pz - nz * SHOE_T * 0.5
        half = step * 0.68                                             # 隣と少し重ねる
        m.box(bone, (x0 - 0.3, c_y - SHOE_T / 2, c_z - half), (x1 + 0.3, c_y + SHOE_T / 2, c_z + half), 'shoe',
              rot=(-(phi + SHOE_TILT), 0, 0), pivot=(0.0, c_y, c_z))
        # 豪華に（2026-10-10 ユーザー指定「履帯を豪華に」）: 履板の外の面の滑り止めの突起、1 枚おきに縁の金具
        g_y, g_z = py + ny * 0.25, pz + nz * 0.25
        m.box(bone, (x0 + 0.2, g_y - 0.35, g_z - 0.35), (x1 - 0.2, g_y + 0.35, g_z + 0.35), 'grouser', rot=(-phi, 0, 0), pivot=(0.0, g_y, g_z))
        if i % 2 == 0:
            for ex in (x0 - 0.5, x1 + 0.5):
                e0, e1 = sorted((ex, ex + (0.4 if ex > x1 else -0.4) * -1))
                m.box(bone, (e0, c_y - 0.6, c_z - 0.6), (e1, c_y + 0.6, c_z + 0.6), 'fitting', rot=(-phi, 0, 0), pivot=(0.0, c_y, c_z))
    return centers


def octagon(m, bone, x0, x1, cy, cz, r, mat):
    """X の厚み x0〜x1、中心 (cy, cz)・内接円の半径 r の正八角形の板（長さ 2r・幅 2r tan22.5° の板を 0・45・90・135 度に重ねる）"""
    w = r * math.tan(math.radians(22.5))
    for a in (0, 45, 90, 135):
        m.box(bone, (x0, cy - w, cz - r), (x1, cy + w, cz + r), mat, rot=(a, 0, 0) if a else None, pivot=(0.0, cy, cz))


def wheel(m, parent, name, ox, sx, cy, cz, r, mat='edge', hub='dark', glow=None, teeth=0):
    """外の面 ox（sx = 外の向き）の、中心 (cy, cz)・半径 r の丸い輪（長さ 2r・幅 2r tan22.5° の板を 45 度ずつ 4 枚重ねた正八角形）。真ん中の軸、光る軸の蓋、歯（起動輪）"""
    bone = m.bone(name, parent, (ox, cy, cz))                           # 車輪ごとの骨（描画で中心のまわりに回す）
    o0, o1 = sorted((ox, ox + sx * 0.8))
    octagon(m, bone, o0, o1, cy, cz, r, mat)
    outer = ox + sx * 0.8                                               # 外の面（右側は -X が外。sorted の大きい方ではない。2026-10-10「右側は赤くない」）
    h0, h1 = sorted((outer, outer + sx * 0.35))
    octagon(m, bone, h0, h1, cy, cz, r * 0.42, hub)                    # 軸の蓋
    for k in range(4):                                                  # 軸の蓋のまわりの 4 つの鋲
        a = math.radians(45 + 90 * k)
        by, bz = cy + r * 0.66 * math.cos(a), cz + r * 0.66 * math.sin(a)
        m.box(bone, (h0, by - 0.3, bz - 0.3), (h1, by + 0.3, bz + 0.3), 'metal')
    if glow:
        g0, g1 = sorted((outer + sx * 0.35, outer + sx * 0.6))
        m.box(bone, (g0, cy - r * 0.2, cz - r * 0.2), (g1, cy + r * 0.2, cz + r * 0.2), glow)
    for k in range(teeth):                                              # 起動輪の歯（まわりに等しく）
        t0, t1 = sorted((ox + sx * 0.1, ox + sx * 0.7))
        m.box(bone, (t0, cy + r * 0.95, cz - 0.55), (t1, cy + r * 1.12, cz + 0.55), 'metal', rot=(360.0 * k / teeth, 0, 0), pivot=(0.0, cy, cz))



def mirror_z(m, name):
    """骨 name の形と付け根を前後（z → -z）に鏡写しにする（向きを入れ替えて別のキャタピラに付け替えるため）。X・Y の回転は符号を変え、北と南の面を入れ替える"""
    b = m.by[name]
    b['pivot'] = [b['pivot'][0], b['pivot'][1], -b['pivot'][2]]
    cubes = []
    for c in b['cubes']:
        a, d, fm = c[0], c[1], dict(c[2])
        fm['north'], fm['south'] = fm['south'], fm['north']
        nc = ((a[0], a[1], -d[2]), (d[0], d[1], -a[2]), fm)
        if len(c) > 3:
            r, pv = c[3], c[4]
            nc += ([-r[0], -r[1], r[2]], [pv[0], pv[1], -pv[2]])
        cubes.append(nc)
    b['cubes'] = cubes


def scale_bones(m, names, k, base_y, drop=0.0):
    """names の骨の箱と pivot を、(0, base_y, 0) を中心に k 倍し、drop だけ下げる（回転はそのまま）"""
    f = lambda p: (p[0] * k, base_y + (p[1] - base_y) * k - drop, p[2] * k)
    for n in names:
        b = m.by[n]
        b['pivot'] = list(f(b['pivot']))
        cubes = []
        for c in b['cubes']:
            nc = (f(c[0]), f(c[1]), c[2])
            if len(c) > 3:
                nc += (c[3], list(f(c[4])))
            cubes.append(nc)
        b['cubes'] = cubes


def galliafort():
    m = g.Model('galliafort')
    m.reuse = {'shoe', 'grouser', 'fitting'}   # 履板は 3 組（動いて見せる）あるので面の絵を使い回す（2026-10-10「移動モーションで履帯が唸り動くように」）
    m.bone('root')
    # ---- 胴: 下の大きな台形の上に、ひとまわり小さい台形を重ねた 2 段（2026-10-10 ユーザー指定「四角だけじゃなく胴も台形の上にもう一段台形を重ねるように」）。
    # 上の段は後ろへ寄せ、前に空いた下の段の甲板に操縦席を置く（実寸はこの 2 倍、さらに HULL_SCALE 倍）
    m.bone('hull', 'root', (0, 26, 0))
    # 2026-10-10 ユーザー指定「胴体をスリムに」: 幅を 8 割ほどに
    LOW = dict(b=(13.5, -19, 19), t=(10.5, -15, 15), y0=17.5, y1=27.5)      # 下の段: 下の面と上の面の（半幅, 前, 後ろ）、高さ
    UP = dict(b=(9.5, -9, 13), t=(7, -6, 10), y0=27.5, y1=35)               # 上の段
    frustum(m, 'hull', **LOW, mat='heavy', top='plate')
    frustum(m, 'hull', **UP, mat='heavy', top='plate')
    deco(m, 'hull', 'front', **LOW, u=1.0, h=1.6, half=7.5, mat='slit')        # 赤い目のスリット（下の段の前）
    deco(m, 'hull', 'front', **UP, u=0.5, h=1.0, half=4, mat='purple')       # 上の段の前の紫の帯
    for side in ('left', 'right'):
        deco(m, 'hull', side, **LOW, u=0.5, h=1.4, half=13, mat='purple')    # 下の段の横の紫の帯
        deco(m, 'hull', side, **LOW, u=-3.0, h=0.8, half=14, mat='edge')     # 鋲の帯
        deco(m, 'hull', side, **UP, u=0.0, h=1.0, half=8, mat='stripe')
    deco(m, 'hull', 'back', **LOW, u=-1.5, h=4.0, half=7, mat='vent')        # 後ろの排気
    deco(m, 'hull', 'back', **LOW, u=-1.5, h=2.4, half=1.5, mat='glow', dx=-5)
    deco(m, 'hull', 'back', **LOW, u=-1.5, h=2.4, half=1.5, mat='glow', dx=5)
    m.box('hull', (-10, 17.5, -12), (10, 19.05, 12), 'metal', bottom='vent')                    # 腹
    # 前の押し出しの刃と牙
    m.box('hull', (-12.5, 17.6, -21), (12.5, 19.8, -18.6), 'edge', front='dark')
    for x in (-10, -5, 5, 10):
        m.box('hull', (x - 1.2, 17.6, -22.4), (x + 1.2, 20.2, -20.9), 'edge', rot=(0, 45, 0))
    # 両横に張り出す肩の重い装甲（キャタピラの上）。上に角、外に紫の帯。第 2 形態（人型）では腕になる（骨 arm_right / arm_left。
    # 付け根は後ろの端。2026-10-10 ユーザー指定「第二形態は変形して人型」「キャタピラが脚、肩が腕」）
    for name, s in (('arm_right', -1), ('arm_left', 1)):
        x0, x1 = sorted((s * 13, s * 19))
        m.bone(name, 'root', (s * 16, 27.5, 14))   # 胴から切り離す（人型で胴を縦に起こしても肩に付くように。要塞では胴の上下だけ写す）
        m.box(name, (x0, 24, -14), (x1, 31, 14), 'heavy', top='plate', out='armor')
        o0, o1 = sorted((s * 18.95, s * 19.6))
        m.box(name, (o0, 26.5, -12), (o1, 28.5, 12), 'stripe', out='stripe')
        for z in (-12, -7):
            k0, k1 = sorted((s * 16.0, s * 18.0))
            m.box(name, (k0, 31, z - 1.2), (k1, 33.5, z + 1.2), 'edge', top='dark')
        m.box(name, (min(s * 14, s * 18), 25, 13.95), (max(s * 14, s * 18), 30, 15.5), 'edge', back='glow')  # 肩の関節（人型で見える）
        # 大きな肩アーマー（人型だけ。要塞では大きさ 0。腕の向きの中で、+Z が人型の上・-Y が前）。2026-10-10 ユーザー指定「肩アーマーを大きく」
        pd = m.bone('pauldron_' + name.split('_')[1], name, (s * 16, 27.5, 14))
        x0, x1 = sorted((s * 10.0, s * 23.0))
        m.box(pd, (x0, 18.5, 14), (x1, 36.5, 23.5), 'heavy', top='plate')                         # 肩の上を覆う（大きく高く）
        c0, c1 = sorted((s * 11.0, s * 22.0))
        m.box(pd, (c0, 19.5, 23.45), (c1, 35.5, 25.0), 'armor', top='stripe')                     # 上の段（紫の帯）
        f0, f1 = sorted((s * 22.5, s * 25.0))
        m.box(pd, (f0, 19, -2), (f1, 36, 23.5), 'armor', out='stripe')                            # 外へ大きく垂れる縁
        g0, g1 = sorted((s * 24.95, s * 25.4))
        m.box(pd, (g0, 26.8, -1), (g1, 28.2, 22.5), 'gblue')                                      # 縁の蒼い光の筋
        e0, e1 = sorted((s * 10.0, s * 23.0))
        for yy in (18.3, 36.2):
            m.box(pd, (e0, yy - 0.3, 22.5), (e1, yy + 0.3, 23.6), 'gblue')                        # 上の前後の縁の光
        for (y0, y1, z0, z1) in ((36.4, 42, 21.5, 29.5), (42, 47, 23, 28.5), (47, 50, 24.5, 27.5)):     # 前へ張り出す尖ったひれ（腕の向きの中で +Y が人型の前）
            h0, h1 = sorted((s * 20.5, s * 22.5))
            m.box(pd, (h0, y0, z0), (h1, y1, z1), 'armor', sides='heavy')
        q0, q1 = sorted((s * 22.45, s * 22.85))
        m.box(pd, (q0, 37, 25), (q1, 48, 26.2), 'gblue')                                         # ひれの光の筋
        for y in (22.5, 27.5, 32.5):                                                              # 肩の角（大きく）
            k0, k1 = sorted((s * 14.5, s * 18.5))
            m.box(pd, (k0, y - 1.6, 24.9), (k1, y + 1.6, 30.5), 'edge', top='dark')
        # シャープに: 角の先から上へ突き出す刃 3 枚（腕の向きの中で +Z が人型の上）、外の縁から下へ垂れる刃
        for y, ang in ((22.5, -14), (27.5, 0), (32.5, 14)):
            blade_x(m, pd, (s * 16.5, y, 30.3), ang, 8, 3.0)
        blade_x(m, pd, (s * 25.2, 27.5, -1.5), 180, 9, 15, kind='main')
    # 後ろの煙突 2 本とアンテナ
    m.mbox('hull', (-12, 22, 15), (-8.5, 41, 18.5), 'metal', top='dark')
    m.mbox('hull', (-12.4, 39, 14.6), (-8.1, 40.2, 18.9), 'purple')
    m.mbox('hull', (-11.4, 40.95, 15.6), (-9.1, 41.3, 17.9), 'glow')
    m.box('hull', (-1, 35, 7), (1, 46, 9), 'metal')
    m.box('hull', (-0.6, 46, 7.4), (0.6, 47, 8.6), 'glow')
    # 操縦席（下の段の前の甲板。左右を装甲で囲み、上の段の前の面を背にする）
    m.box('hull', (-5.5, 27.5, -15), (5.5, 28.3, -9.5), 'seat')                                  # 床
    m.mbox('hull', (-10, 27.5, -15), (-5.5, 30.5, -9.5), 'armor', top='plate')                  # 左右の装甲
    m.mbox('hull', (-6.2, 28.3, -15), (-5.2, 33, -14), 'edge')                                   # 前の柱
    m.box('hull', (-6.2, 33, -15), (6.2, 34, -8.6), 'edge')                                      # 庇
    m.box('hull', (-4.5, 28.3, -15.3), (4.5, 29.1, -14.6), 'gblue')                              # 計器の光
    m.bone('pilot', 'hull', (0, 28.3, -11.5))
    m.box('pilot', (-1.2, 28.5, -12.5), (1.2, 30.9, -11.1), 'suit')                              # 胴
    m.box('pilot', (-1.0, 30.9, -12.7), (1.0, 32.7, -10.9), 'skin')                              # 顔
    m.box('pilot', (-1.2, 31.9, -12.8), (1.2, 32.9, -10.7), 'hair')                              # 前髪
    m.box('pilot', (-1.2, 29.7, -11.1), (1.2, 32.9, -10.55), 'hair')                             # 後ろ髪
    m.mbox('pilot', (-2.0, 29.3, -12.9), (-1.2, 30.5, -11.7), 'suit')                            # 腕（操縦桿へ）
    # 障壁の発生塔（上の段の四隅の柱、先が蒼く光る）
    m.bone('emitters', 'hull', (0, 35, 0))
    for x, z in ((-7, -4.5), (7, -4.5), (-7, 8.5), (7, 8.5)):
        m.box('emitters', (x - 1.2, 35, z - 1.2), (x + 1.2, 41, z + 1.2), 'metal', top='edge')
        m.box('emitters', (x - 1.6, 39, z - 1.6), (x + 1.6, 40, z + 1.6), 'purple')
        m.box('emitters', (x - 0.9, 41, z - 0.9), (x + 0.9, 43.5, z + 0.9), 'gblue')
    # ---- 頭（第 2 形態の人型だけ。要塞では大きさ 0 で隠す）。作り込み（2026-10-10 ユーザー指定「頭を作り込む」）:
    # 角張った兜に赤く光るバイザー、口元のフェイスガード（スリット）、額の V 字の角と蒼い宝石、後ろへ流れる頬の飾りと後頭部のとさか ----
    m.bone('head', 'root', (0, 35, 2))   # 胴から切り離す（人型で縦の胴の上へ）
    m.box('head', (-4, 35.5, -3), (4, 42.5, 5), 'heavy', top='plate')                           # 兜
    m.box('head', (-4.4, 41.5, -3.4), (4.4, 43.2, 5.4), 'armor', top='plate')                    # 兜の縁
    m.box('head', (-3.6, 39.0, -3.55), (3.6, 40.4, -2.95), 'slit', top='dark', bottom='dark')    # 赤く光るバイザー
    m.box('head', (-2.6, 35.5, -3.7), (2.6, 38.6, -2.95), 'edge')                                # フェイスガード
    for x in (-1.4, 0.0, 1.4):
        m.box('head', (x - 0.3, 36.0, -3.8), (x + 0.3, 38.0, -3.65), 'dark')                     # 口元のスリット
    m.box('head', (-0.7, 41.0, -3.75), (0.7, 42.4, -3.35), 'gblue')                              # 額の蒼い宝石
    for sx in (-1, 1):                                                                            # 額の V 字の角
        m.box('head', (-0.5, 42.0, -3.6), (0.5, 49.0, -2.6), 'edge', rot=(0, 0, sx * 38), pivot=(0, 42.2, -3.1))
        m.box('head', (-0.35, 47.6, -3.5), (0.35, 49.0, -2.7), 'gblue', rot=(0, 0, sx * 38), pivot=(0, 42.2, -3.1))
    m.mbox('head', (-5.4, 36.2, -2.0), (-4.0, 41.6, 4.0), 'armor', out='stripe', rot=(0, -12, 0)) # 頬の飾り
    m.mbox('head', (-5.5, 39.5, 3.8), (-4.3, 40.5, 9.5), 'edge', rot=(-10, -10, 0))               # 頬から後ろへ流れる飾り
    m.box('head', (-0.8, 42.5, 0.0), (0.8, 44.5, 10.5), 'armor', top='stripe', rot=(-12, 0, 0), pivot=(0, 43, 0))  # 後頭部のとさか
    m.box('head', (-2.8, 33.5, -1), (2.8, 35.6, 4), 'metal')                                      # 首
    # シャープに（2026-10-10 ユーザー指定「人型の装甲をシャープに」）: 頬から後ろ上へ伸びる刃、後頭部のとさかの刃、額の角の先の刃
    for sx in (-1, 1):
        blade_x(m, 'head', (sx * 5.7, 40.5, 1.5), 18, 10, 3.2)
        blade_z(m, 'head', (sx * 3.0, 47.0, -3.1), 90 + sx * 52, 4.5, 1.6, z_off=-0.3)
    blade_x(m, 'head', (0, 44.2, 6), 12, 11, 3.0)
    # ---- 主砲（穿孔破城砲。列車砲のような巨大な一門。2026-10-10 ユーザー指定「列車砲くらいいかつくしていい」）:
    # 上の段の上に斜めの装甲の大きな砲室、前に防盾、太く長い砲身（根元から先へ細くなる段々・紫の帯・両横に蒼い加速の光の筋）、
    # 上に駐退機の筒 2 本、先にスリットの付いた大きなマズルブレーキ、両横に俯仰の歯車の弧、後ろに釣り合いの重り
    m.bone('cannon', 'root', (0, 40, 4))   # 車体から切り離す（2026-10-10 人型の胴を細い胴に入れ替えたので、車体と一緒に隠さない）
    frustum(m, 'cannon', b=(8.5, -4, 15), t=(6.5, -2, 13), y0=35, y1=44, mat='heavy', top='plate')   # 砲室
    deco(m, 'cannon', 'front', b=(8.5, -4, 15), t=(6.5, -2, 13), y0=35, y1=44, u=2.5, h=1.0, half=6, mat='purple')
    for side in ('left', 'right'):
        deco(m, 'cannon', side, b=(8.5, -4, 15), t=(6.5, -2, 13), y0=35, y1=44, u=0.0, h=1.4, half=8, mat='stripe')
    m.box('cannon', (-5, 36, -6.5), (5, 44.5, -3.5), 'heavy', front='armor', top='plate')       # 防盾
    m.box('cannon', (-3.6, 36.4, -14), (3.6, 43.6, -6.45), 'metal', top='plate')                 # 砲身の根元（太い）
    m.box('cannon', (-2.9, 37.1, -34), (2.9, 42.9, -13.95), 'metal', top='plate')                 # 砲身の中
    m.box('cannon', (-2.3, 37.7, -48), (2.3, 42.3, -33.95), 'metal', top='plate')                 # 砲身の先
    for z0, r in ((-15, 3.4), (-26, 3.4), (-37, 2.8)):                                           # 砲身の紫の帯
        m.box('cannon', (-r, 40 - r, z0 - 1.2), (r, 40 + r, z0), 'purple')
    for x in (-1, 1):                                                                             # 両横の蒼い加速の光の筋
        x0, x1 = sorted((x * 2.85, x * 3.3))
        m.box('cannon', (x0, 39.3, -32), (x1, 40.7, -15), 'gblue')
    m.box('cannon', (-3.8, 36.2, -53), (3.8, 43.8, -47.95), 'heavy', top='plate')                 # マズルブレーキ
    for z in (-52, -50.2):                                                                        # 横のスリット
        for x in (-1, 1):
            x0, x1 = sorted((x * 3.75, x * 3.9))
            m.box('cannon', (x0, 37.5, z), (x1, 42.5, z + 1.0), 'dark')
    m.box('cannon', (-1.8, 38.2, -53.1), (1.8, 41.8, -52.95), 'eye')                              # 砲口（赤い光）
    m.mbox('cannon', (-3.2, 44, -20), (-1.0, 46, -6), 'metal', top='edge')                        # 駐退機の筒 2 本
    m.mbox('cannon', (-3.4, 43.8, -21), (-0.8, 46.2, -19.9), 'edge')
    m.mbox('cannon', (-9.2, 35.5, -2), (-8.45, 43, 8), 'edge', out='heavy')                       # 俯仰の歯車の弧（両横）
    for z in (-1, 1.5, 4, 6.5):
        m.mbox('cannon', (-9.6, 41.5, z), (-9.15, 42.5, z + 1), 'dark')
    m.box('cannon', (-6, 35, 14.95), (6, 41, 19), 'heavy', top='plate', back='vent')              # 後ろの釣り合いの重り
    m.box('cannon', (-6.4, 38, 18.95), (6.4, 39, 19.4), 'purple')
    # ---- 主砲の支えのアーム（人型だけ。2026-10-10 ユーザー指定「主砲を使うときはアームがせり上がり頭上で固定されるように」）:
    # 甲板（人型では背中）の真ん中から、胴の向きの -Z（人型では真上）へ伸びる柱。ふだんは長さ方向の大きさ 0 で隠れ、撃つときに伸びる。
    # 根元の台・太い柱・両横の油圧の筒・紫の帯・先の砲を挟む受け台
    m.bone('cannon_arm', 'root', (0, 36.5, 10))
    m.box('cannon_arm', (-4, 34, 7), (4, 39, 13), 'heavy', top='plate')                         # 根元の台
    m.box('cannon_arm', (-2.5, 35, -22), (2.5, 38, 7.05), 'metal', top='plate')                  # 柱
    m.mbox('cannon_arm', (-4.2, 35.5, -16), (-2.45, 37.5, 6), 'edge', out='metal')              # 油圧の筒
    for z in (-6, -14):
        m.box('cannon_arm', (-3, 34.6, z - 1), (3, 38.4, z), 'purple')                          # 紫の帯
    m.box('cannon_arm', (-6, 33, -27), (6, 40, -21.95), 'heavy', top='edge')                     # 受け台
    m.mbox('cannon_arm', (-6.6, 33, -27.5), (-5.95, 42, -21.5), 'edge', out='heavy')            # 受け台の挟む爪
    # ---- ミサイルの箱（肩の装甲の上、背の左右。蓋が上へ開く）。ごつく（2026-10-10 ユーザー指定「ミサイルと副装備もごつくして」）:
    # 厚い装甲の箱に、前の傾いた装甲・外の増加装甲と鋲・3×3 の赤く光る発射口・太い枠、上を覆う厚い蓋
    for name, s in (('pod_right', -1), ('pod_left', 1)):
        x0, x1 = sorted((s * 8.5, s * 18.5))
        xi, xo = s * 8.5, s * 18.5                                                                 # 内・外
        m.bone(name, 'root', (s * 13.5, 35, 8.5))
        m.box(name, (x0, 31, 1), (x1, 39, 17), 'heavy', top='dark', out='armor', back='vent')      # 箱
        m.box(name, (x0 + 0.3, 31.5, -2.2), (x1 - 0.3, 37.5, 1.05), 'armor', top='plate', rot=(-30, 0, 0), pivot=(s * 13.5, 34.5, 1))  # 前の傾いた装甲
        o0, o1 = sorted((xo, xo + s * 1.2))
        m.box(name, (o0, 31.5, 2), (o1, 38.5, 16), 'armor', out='stripe')                         # 外の増加装甲
        for z in (3.5, 9, 14.5):                                                                   # 増加装甲の鋲
            r0, r1 = sorted((xo + s * 1.15, xo + s * 1.6))
            m.box(name, (r0, 37, z - 0.6), (r1, 38, z + 0.6), 'edge')
        for i in range(3):                                                                         # 3×3 の発射口
            for j in range(3):
                cx = s * (10.5 + i * 3.0)
                cz = 3.5 + j * 4.6
                m.box(name, (cx - 1.0, 38.6, cz), (cx + 1.0, 39.05, cz + 3.0), 'glow')
        for zz in (1, 16.2):                                                                       # 太い枠（前後）
            m.box(name, (x0 - 0.3, 38.4, zz - 0.2), (x1 + 0.3, 39.4, zz + 1.0), 'edge')
        hatch = 'hatch_' + name.split('_')[1]
        m.bone(hatch, name, (s * 13.5, 39.4, 1))
        m.box(hatch, (x0 - 0.4, 39.4, 1), (x1 + 0.4, 40.8, 17.4), 'heavy', top='plate')            # 厚い蓋
        m.box(hatch, (x0 + 1.0, 40.75, 3), (x1 - 1.0, 41.3, 15.4), 'armor', top='stripe')         # 蓋の紫の帯の装甲
    # ---- 副砲（肩の装甲の前の砲座。左右へ振る）。ごつく: 傾いた防盾の厚い砲塔に、2×2 の 4 本の太い砲身と筒の覆い、
    # 先に赤い砲口の箱、外に弾倉のドラム、上に照準の箱
    for name, s in (('gun_right', -1), ('gun_left', 1)):
        x = s * 17
        m.bone(name, 'root', (x, 28, -15))   # 人型では肩の上へ（2026-10-10 ユーザー指定「副砲を肩に」）。胴から切り離す
        m.box(name, (x - 3.5, 24.5, -19), (x + 3.5, 31.5, -11), 'heavy', top='plate')              # 砲塔
        m.box(name, (x - 3.2, 25, -21.2), (x + 3.2, 31, -18.95), 'armor', top='plate', rot=(-20, 0, 0), pivot=(x, 28, -19))  # 傾いた防盾
        m.box(name, (x - 2.4, 25.6, -23), (x + 2.4, 30.4, -20.5), 'metal')                         # 砲身の覆いの根元
        for dx in (-1.1, 1.1):                                                                     # 2×2 の砲身
            for dy in (-1.1, 1.1):
                m.box(name, (x + dx - 0.7, 28 + dy - 0.7, -30), (x + dx + 0.7, 28 + dy + 0.7, -22.95), 'metal')
        m.box(name, (x - 2.6, 25.4, -27), (x + 2.6, 30.6, -25.8), 'purple')                        # 砲身を束ねる紫の輪
        m.box(name, (x - 2.5, 25.5, -31), (x + 2.5, 30.5, -29.95), 'edge', front='eye')            # 砲口（赤い光）
        d0, d1 = sorted((x + s * 3.45, x + s * 6.0))
        m.box(name, (d0, 25, -18), (d1, 30.5, -12), 'armor', out='stripe')                         # 弾倉のドラム
        m.box(name, (x - 1.5, 31.45, -17), (x + 1.5, 33, -13), 'metal', front='eye')               # 照準の箱
    # ---- 胴まわりを HULL_SCALE 倍に（キャタピラの上面 y 17.5 を基準。同日「胴の部分大きく」） ----
    scale_bones(m, ('hull', 'pilot', 'emitters', 'cannon', 'cannon_arm', 'pauldron_right', 'pauldron_left', 'pod_right', 'pod_left', 'hatch_right', 'hatch_left', 'gun_right', 'gun_left',
                    'arm_right', 'arm_left', 'head'),
                HULL_SCALE, 17.5, HULL_DROP)
    # ---- 4 本脚（2026-10-10 ユーザー指定「キャタピラを脚そのものにして地面にハの字に接触させる」→「前後に張り出す形で」→
    # 「キャタピラを逆台形の大型に」→「これをさっきのハの字にして」）: 横から見て逆台形（上が長く、下が短い）の大きなキャタピラを、
    # 胴の横の付け根（内の上の角）から前の 2 つは前へ・後ろの 2 つは後ろへ傾け、外の下の角が地面に着く（横から見てハの字）。
    # 骨: leg_X（コードで動かす。回転なし。付け根の関節）> leg_X_tilt（X で外へ傾ける）
    H, TOP0, TOP1, BOT0, BOT1, T = 24.0, 4.0, 42.0, 14.0, 32.0, 3.0   # 2026-10-10「キャタピラを大型化」: 高さ 17 → 24、長さ 29 → 38   # 高さ、上の帯・下の帯の範囲（付け根からの前後の距離の目安）、帯の厚み
    TILT = 0.0                                                         # 傾けない（同日ユーザー指定「やっぱ角度はつけないで」。25 → 40 → 0 度）
    SEP = 6.0                                                          # 前後のキャタピラを離す分（同日ユーザー指定「前後のキャタピラの距離を離して」）
    ang = math.degrees(math.atan((BOT0 - TOP0) / H))                   # 端の傾き（上の帯の方が外へ広い）
    slant = math.hypot(BOT0 - TOP0, H)
    # 傾けたとき外の下の角がちょうど地面に着く付け根の高さ
    t = math.radians(TILT)
    HY = max((H - dy) * math.cos(t) + dz * math.sin(t) for dz, dy in ((BOT1 - TOP0, 0.0), (TOP1 - TOP0, H)))
    OY = HY - H                                                         # 形を作るときの上げ幅
    for name, sx, sz in (('leg_fr', -1, -1), ('leg_fl', 1, -1), ('leg_br', -1, 1), ('leg_bl', 1, 1)):
        x0, x1 = sorted((sx * 14.5, sx * 27.5))   # 幅 9 → 13
        cx = (x0 + x1) / 2
        z = lambda d: sz * (d + SEP)
        Y = lambda y: y + OY
        # 前のキャタピラは root の子（人型ですね）、後ろのキャタピラは腕の子（人型で前腕。同日「前のキャタピラを脚の一部に後ろのキャタピラを腕の一部に」）
        # 2026-10-10 ユーザー指定「後ろのキャタピラを足に前のキャタピラを腕にくみかえて」: 前は腕の子（人型の前腕）、後ろは root の子（人型の足先）
        m.bone(name, ('arm_right' if sx < 0 else 'arm_left') if sz < 0 else 'root', (cx, HY, z(TOP0)))
        m.box(name, (min(sx * 13, cx), HY - 3, z(TOP0) - 3), (max(sx * 13, cx), HY + 3, z(TOP0) + 3), 'edge', top='purple')  # 付け根の関節
        m.box(name, (min(sx * 13, sx * 15.4), HY - 1, z(TOP0) - 1), (max(sx * 13, sx * 15.4), HY + 1, z(TOP0) + 1), 'glow')
        tl = m.bone(name + '_tilt', name, (cx, HY, z(TOP0)), (TILT_SIGN * sz * TILT, 0, 0))
        # 履帯（2026-10-10 ユーザー指定「履帯をリアルにするため四角い薄い板を斜めに重ねて連ねて」）: 逆台形の輪に沿って、薄い四角の履板を
        # 少し斜めにして瓦のように重ねながら並べる（上の帯 → 外の端 → 下の帯 → 内の端）
        loop = ((H, TOP0), (H, TOP1), (0.0, BOT1), (0.0, BOT0))
        corners = track_shoes(m, tl, loop, x0, x1, z, Y)          # 角ごとの丸みの中心と半径（loop の順。0・1 が上の両端）
        for d0, d1, o in ((TOP0, BOT0, -sz), (TOP1, BOT1, sz)):         # o = 端の上が傾く向き（内の端は胴の方、外の端は外へ）
            mz = z((d0 + d1) / 2)
            fz = mz + (-o) * 2.5                                         # 帯の内側を埋める板（端の三角）
            m.box(tl, (x0 + 0.5, Y(H / 2 - slant / 2 + 1.5), fz - 2.5), (x1 - 0.5, Y(H / 2 + slant / 2 - 1.5), fz + 2.5), 'heavy',
                  rot=(o * ang, 0, 0), pivot=(cx, Y(H / 2), fz))   # 斜めの端に沿う向き（2026-10-10 逆だったのを直した）
        # 帯の内側の箱（側面の装甲板）と、外の側面の転輪・起動輪・紫の帯
        m.box(tl, (x0 + 0.5, Y(SHOE_T), min(z(BOT0 - 1), z(BOT1 + 1))), (x1 - 0.5, Y(H - SHOE_T), max(z(BOT0 - 1), z(BOT1 + 1))), 'heavy')
        m.box(tl, (x0 + 0.5, Y(H / 2), min(z(TOP0 + 3), z(TOP1 - 3))), (x1 - 0.5, Y(H - SHOE_T), max(z(TOP0 + 3), z(TOP1 - 3))), 'heavy')
        ox = x1 if sx > 0 else x0                                         # 外の面
        o0, o1 = sorted((ox, ox + sx * 0.8))
        # 転輪（下。5 つ）・起動輪と誘導輪（上の両端。歯付き・軸の赤い光）・上の帯を支える小さな上部転輪 2 つ。どれも八角形で丸く（2026-10-10「角をリアルにすこし丸めて」「豪華に」）
        # 骨の名前: 転輪 <脚>_wr0〜4（半径 2.6）・起動輪と誘導輪 <脚>_ws0〜1（3.9）・上部転輪 <脚>_wt0〜1（1.5）。ChronoGazerModel が進んだ距離で回す
        for i in range(5):
            wheel(m, tl, f'{name}_wr{i}', ox, sx, Y(4.4), z(BOT0 + 2.0 + i * (BOT1 - BOT0 - 4) / 4), 2.6)
        for i in range(2):                                                # 起動輪・誘導輪は上の両端の丸みの中心に、履帯の内側にちょうど収まる大きさで（歯が履板にかみ合う）
            (wcy, wcz), rr = corners[i]
            wheel(m, tl, f'{name}_ws{i}', ox, sx, wcy, wcz, rr - SHOE_T - 0.35, glow='glow', teeth=10)
        for i, dz in enumerate((TOP0 + 13.5, TOP1 - 13.5)):
            wheel(m, tl, f'{name}_wt{i}', ox, sx, Y(H - 3.6), z(dz), 1.5, mat='metal')
        m.box(tl, (o0, Y(9.5), min(z(TOP0 + 9), z(TOP1 - 9))), (o1, Y(11.2), max(z(TOP0 + 9), z(TOP1 - 9))), 'purple')  # 紫の帯
        m.box(tl, (o0, Y(13.5), min(z(TOP0 + 9), z(TOP1 - 9))), (o1, Y(14.2), max(z(TOP0 + 9), z(TOP1 - 9))), 'gblue')   # 光の筋
        if sz > 0:   # 後ろのキャタピラ（人型の足先。回さずにそのまま使う）の内の端の踵の装備（人型だけ。要塞では大きさ 0）。2026-10-10 ユーザー指定「足の踵の部分が寂しいのでキャタピラの後ろに装備を追加」
            hz = -z(TOP0)                                                                       # 前のキャタピラ向きに作り、最後に前後に鏡写しにする
            heel = m.bone('heel_' + ('right' if sx < 0 else 'left'), tl, (cx, 6, hz))   # 2026-10-10「回転させず立ち上がる」で左右の入れ替えをやめた
            # 2026-10-10 ユーザー指定「かかとの装備を大きくふくざつに」: 段々に重なる踵の装甲、上の傾いた 2 段の装甲、3 本の爪の拍車、
            # 2 本の油圧の支柱、4 つの推進器（輪付き）の束、両横の刃のひれ、放熱の口と鋲、紫の帯と蒼い光の筋
            m.box(heel, (cx - 6.5, 0.5, hz), (cx + 6.5, 16, hz + 10), 'heavy', top='plate', back='armor')       # 踵の装甲の塊（下の段）
            m.box(heel, (cx - 5.8, 16, hz), (cx + 5.8, 22, hz + 7), 'heavy', top='plate')                       # 上の段（足首へつなぐ）
            for k, (y0, y1, z1, ha) in enumerate(((14, 18.5, hz + 12, -16), (19.5, 23.5, hz + 9.5, -24))):     # 上の傾いた 2 段の装甲
                m.box(heel, (cx - 7.0 + k * 0.6, y0, hz - 1), (cx + 7.0 - k * 0.6, y1, z1), 'armor', top='stripe', rot=(ha, 0, 0), pivot=(cx, y1, hz))
                m.box(heel, (cx - 6.0 + k * 0.6, y0 - 0.2, z1 - 0.6), (cx + 6.0 - k * 0.6, y0 + 0.6, z1 + 0.2), 'gblue', rot=(ha, 0, 0), pivot=(cx, y1, hz))
            for dx in (-4.0, 0.0, 4.0):                                                         # 3 本の爪の拍車（後ろ下へ）
                m.box(heel, (cx + dx - 1.3, 0, hz + 8), (cx + dx + 1.3, 3.2, hz + 18), 'edge', rot=(20, 0, 0), pivot=(cx + dx, 1.5, hz + 9))
                m.box(heel, (cx + dx - 0.5, 3.1, hz + 9), (cx + dx + 0.5, 3.5, hz + 17), 'gblue', rot=(20, 0, 0), pivot=(cx + dx, 1.5, hz + 9))
            for dx in (-4.5, 4.5):                                                              # 2 本の油圧の支柱（上の段から後ろの装甲へ）
                m.box(heel, (cx + dx - 1.0, 9, hz + 9.9), (cx + dx + 1.0, 21, hz + 11.8), 'metal', back='edge', rot=(-14, 0, 0), pivot=(cx + dx, 21, hz + 10))
                m.box(heel, (cx + dx - 1.4, 18, hz + 9.6), (cx + dx + 1.4, 19, hz + 12.2), 'purple', rot=(-14, 0, 0), pivot=(cx + dx, 21, hz + 10))
            for dx, dy in ((-2.6, 4.5), (2.6, 4.5), (-2.6, 10.0), (2.6, 10.0)):                    # 4 つの推進器の束（輪と赤い光）
                m.box(heel, (cx + dx - 2.0, dy - 2.0, hz + 9.95), (cx + dx + 2.0, dy + 2.0, hz + 12.5), 'dark')
                m.box(heel, (cx + dx - 2.3, dy - 2.3, hz + 11.6), (cx + dx + 2.3, dy + 2.3, hz + 12.4), 'edge')
                m.box(heel, (cx + dx - 1.3, dy - 1.3, hz + 12.45), (cx + dx + 1.3, dy + 1.3, hz + 12.7), 'glow')
            for sgn in (-1, 1):                                                                 # 両横: 刃のひれ（後ろへ尖る 3 段）・放熱の口・鋲・光の筋
                o0, o1 = sorted((cx + sgn * 6.45, cx + sgn * 7.6))
                for (z0, z1, y0, y1) in ((hz - 3, hz + 10, 1.5, 17), (hz + 10, hz + 15, 4, 14), (hz + 15, hz + 19, 7, 11)):
                    m.box(heel, (o0, y0, z0), (o1, y1, z1), 'armor', sides='stripe')
                v0, v1 = sorted((cx + sgn * 7.55, cx + sgn * 7.9))
                m.box(heel, (v0, 9, hz - 1), (v1, 14, hz + 6), 'vent')
                g0, g1 = sorted((cx + sgn * 7.55, cx + sgn * 7.85))
                m.box(heel, (g0, 6, hz - 2), (g1, 7, hz + 18), 'gblue')
                for (yy, zz) in ((3, hz + 1), (3, hz + 8), (15, hz + 1), (15, hz + 8)):
                    r0, r1 = sorted((cx + sgn * 7.55, cx + sgn * 8.0))
                    m.box(heel, (r0, yy - 0.5, zz - 0.5), (r1, yy + 0.5, zz + 0.5), 'edge')
            mirror_z(m, heel)
        if sz < 0:   # 前のキャタピラ（人型の前腕）の先の拳（人型だけ。要塞では大きさ 0）。2026-10-10 ユーザー指定「拳・手を付ける」
            e = -z(TOP1 + 1.2)                                                                  # 後ろのキャタピラ向きに作り、最後に前後に鏡写しにする
            fist = m.bone('fist_' + ('right' if sx < 0 else 'left'), tl, (cx, 8.5, e))
            m.box(fist, (cx - 5, 1.5, e - 0.5), (cx + 5, 15.5, e + 7), 'heavy', top='plate')        # 拳
            m.box(fist, (cx - 5.4, 1.2, e + 3.0), (cx + 5.4, 15.8, e + 4.2), 'gblue')               # 拳の蒼い光の輪
            for yy in (3.0, 6.5, 10.0, 13.5):                                                       # 指の節
                m.box(fist, (cx - 4.2, yy - 1.3, e + 6.95), (cx + 4.2, yy + 1.3, e + 8.6), 'edge')
            m.box(fist, (cx - 5.2, 13.0, e - 0.8), (cx + 5.2, 16.2, e + 2.5), 'armor', top='stripe')  # 手の甲の装甲
            # 前腕の外側の刃の装甲（拳の先へ尖って伸びる）
            bx = cx + sx * 5.0
            for (z0, z1, y0, y1) in ((e - 26, e + 3, 2.0, 15.0), (e + 3, e + 10, 4.0, 13.0), (e + 10, e + 15, 6.5, 10.5)):
                b0, b1 = sorted((bx, bx + sx * 1.8))
                m.box(fist, (b0, y0, z0), (b1, y1, z1), 'armor', sides='heavy')
            l0, l1 = sorted((bx + sx * 1.75, bx + sx * 2.2))
            m.box(fist, (l0, 8.0, e - 24), (l1, 9.4, e + 12), 'gblue')                            # 刃の光の筋
            mirror_z(m, fist)
    # ---- 人型だけの部品（要塞では大きさ 0 で隠す。人型の姿勢の位置にそのまま作る。root の子） ----
    # 人型専用の細い胴（2026-10-10 ユーザー指定「胴体がごっついと装甲が映えない…脚に多重装甲をつける大改革」で、車体は変形でたたんで隠し、これに入れ替える）:
    # 腰の骨盤 → くびれた腰（光の輪）→ 段々の腹 → 張った胸 → 襟。背中に主砲・ミサイルの箱を付ける板
    m.bone('torso', 'root', (0, 66, 0))
    m.box('torso', (-12, 60, -6), (12, 66, 6), 'heavy', top='plate')                                  # 骨盤
    m.box('torso', (-6.5, 66, -5), (6.5, 70.5, 5), 'metal')                                            # くびれた腰
    m.box('torso', (-6.9, 67.6, -5.4), (6.9, 68.6, 5.4), 'gblue')                                      # 腰の光の輪
    for i, (y0, w) in enumerate(((70.5, 9.0), (73.5, 10.5))):                                         # 段々の腹（2 段に縮めた）
        m.box('torso', (-w, y0, -6 - i * 0.5), (w, y0 + 3.2, 5), 'armor', top='plate')
    m.box('torso', (-13, 76.5, -7), (13, 91, 6), 'heavy', top='plate')                                 # 胸
    m.box('torso', (-16, 85, -6.5), (16, 94, 5.5), 'heavy', top='plate')                               # 張った胸の上
    m.box('torso', (-12, 75, 5.45), (12, 94, 7.5), 'armor', back='plate')                              # 背中の板
    m.box('torso', (-10, 94, -5), (10, 97, 4), 'heavy', top='plate')                                   # 襟
    m.mbox('torso', (-16.6, 87, -5.5), (-15.95, 92.5, 4.5), 'gblue')                                   # 胸の横の光の筋
    # 胸の装甲と光る核（2026-10-10 ユーザー指定「胸に装甲と光る核」）: V 字の胸板、真ん中に蒼く脈打つ歪曲力場の核（枠と輪）。細い胴に合わせて小さく前へ
    m.bone('chest', 'root', (0, 84, -7))
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 0.5, sx * 15.5))
        m.box('chest', (x0, 77.5, -10.5), (x1, 93.5, -6.5), 'heavy', top='plate', rot=(0, 0, sx * 10), pivot=(0, 93.5, -8))
        g0, g1 = sorted((sx * 6, sx * 15))
        m.box('chest', (g0, 76.7, -10.9), (g1, 77.7, -10.3), 'gblue', rot=(0, 0, sx * 10), pivot=(0, 93.5, -8))   # 胸板の下の縁の光
    for sx in (-1, 1):                                                                             # シャープに: 胸板の下の外の角から下外へ尖る刃
        blade_z(m, 'chest', (sx * 13.5, 79.5, -10.6), -90 + sx * 35, 9, 5, z_off=-0.2)
    m.box('chest', (-5.5, 79, -12), (5.5, 90, -10), 'edge', top='plate')                               # 核の枠
    m.box('chest', (-3.5, 81, -12.6), (3.5, 88, -11.95), 'gblue')                                      # 歪曲力場の核
    m.box('chest', (-4.5, 80, -12.4), (4.5, 80.8, -11.9), 'purple')
    m.box('chest', (-4.5, 88.2, -12.4), (4.5, 89, -11.9), 'purple')
    # 腰のスカート装甲（2026-10-10 ユーザー指定「腰のスカート装甲」）: 前垂れ 2 枚（重ねて 2 段）、腰の横の大きな装甲 3 枚の重ね、後ろの板。縁に蒼い光
    SK = 14.0   # 腰を上げた分（2026-10-10「足を更に長く」）
    m.bone('skirt', 'root', (0, 52 + SK, 0))
    for sx in (-1, 1):
        for k, (y0, y1, z) in enumerate(((52 + SK, 40 + SK, -7.0), (46 + SK, 35 + SK, -8.2))):                           # 前垂れ（2 段）
            x0, x1 = sorted((sx * 1.0, sx * (9.5 - k)))
            m.box('skirt', (x0, y1, z - 1.3), (x1, y0, z), 'armor', front='plate', rot=(-12 - k * 4, 0, 0), pivot=(0, y0, z))
            g0, g1 = sorted((sx * 1.5, sx * (9 - k)))
            m.box('skirt', (g0, y1 - 0.1, z - 1.6), (g1, y1 + 0.7, z - 1.2), 'gblue', rot=(-12 - k * 4, 0, 0), pivot=(0, y0, z))
        for k in range(3):                                                                            # 腰の横の大きな装甲（3 枚の重ね）
            y0 = 52 + SK - k * 5.5
            x0, x1 = sorted((sx * (12.5 + k * 0.9), sx * (14.0 + k * 0.9)))
            m.box('skirt', (x0, y0 - 11, -8 + k * 0.5), (x1, y0, 8 - k * 0.5), 'heavy', out='armor', rot=(0, 0, -sx * (8 + k * 4)), pivot=(sx * 12.5, y0, 0))
            g0, g1 = sorted((sx * (13.95 + k * 0.9), sx * (14.35 + k * 0.9)))
            m.box('skirt', (g0, y0 - 11, -1), (g1, y0 - 10, 1), 'gblue', rot=(0, 0, -sx * (8 + k * 4)), pivot=(sx * 12.5, y0, 0))
    m.box('skirt', (-9, 40 + SK, 6), (9, 52 + SK, 7.5), 'armor', back='plate', rot=(10, 0, 0), pivot=(0, 52 + SK, 6.5))  # 後ろの板
    # シャープに: 前垂れの裾から下へ尖る刃、腰の横の装甲の裾から下へ垂れる刃
    for sx in (-1, 1):
        blade_z(m, 'skirt', (sx * 5.0, 35.5 + SK, -10.6), -90, 8, 7, kind='main')
        blade_x(m, 'skirt', (sx * 17.2, 44 + SK, 0), -90, 9, 12, kind='main')
    # 背中の翼（2026-10-10 ユーザー指定「背中を翼のように」→ 参考画像の考え方「尖った装甲板の重なり・X 字に広がる大きな翼」。形と配色は写さず、敵の色のまま）:
    # 左右に上下 2 枚ずつ、X 字に広がる大きなバインダー。根元の関節、段々に細くなって尖る装甲板、裏の骨組みのレール、縁の紫・蒼い光の筋、上の縁の刻み
    for name, sx in (('wing_right', -1), ('wing_left', 1)):
        px = sx * 9.0
        m.bone(name, 'root', (px, 88, 17))
        m.box(name, (px - 3.5, 83, 14), (px + 3.5, 95, 21), 'edge', sides='glow')                            # 根元の関節
        binder(m, name, sx, (sx * 10, 93.5, 18), 66.0, 22.0, 30.0, 10.0)                                    # 上のバインダー（外へ上がる）
        binder(m, name, sx, (sx * 10, 82.5, 18), 56.0, 18.0, -36.0, 14.0)                                   # 下のバインダー（外へ下がる）
    # 逆関節の脚（2026-10-10 ユーザー指定「キャタピラを足先に逆関節で重厚な装甲をつける」→「脚に多重装甲をつける大改革」）:
    # 腰から前へ下る太もも → 前へ出たひざ → 後ろへ下るすね → 足首 → 足先のキャタピラ。太ももは骨の回転で前へ傾け、すねは形ごと後ろへ傾けて作る。
    # 多重装甲: 太もも・すねを上から順に重なる厚い装甲板の段で太くし、ひざの刃・すねの後ろの刃のひれ、太ももの横の排気口、ふくらはぎの赤く光る推進器
    for sx, side in ((-1, 'right'), (1, 'left')):
        cx = sx * 20.0
        th = m.bone('thigh_' + side, 'root', (cx, HIP_Y, 0))
        m.box(th, (cx - 6, HIP_Y - THIGH_LEN, -6), (cx + 6, HIP_Y - 1, 6), 'heavy', top='plate')                     # 太もも
        for i in range(3):                                                                                           # 前の重ねた装甲板
            top = HIP_Y - 1.5 - i * 4.4
            zf = -6.0 - (i + 1) * 0.9
            m.box(th, (cx - 6.4 + i * 0.3, top - 6.0, zf), (cx + 6.4 - i * 0.3, top, -5.9), 'armor', front='plate')
            m.box(th, (cx - 5.4 + i * 0.3, top - 6.1, zf - 0.35), (cx + 5.4 - i * 0.3, top - 5.3, zf + 0.1), 'gblue')
            o0, o1 = sorted((cx + sx * 5.9, cx + sx * (6.0 + (i + 1) * 0.9)))                                       # 外の横の重ねた装甲板
            m.box(th, (o0, top - 6.0, -5.6), (o1, top, 5.6), 'armor', out='stripe')
        v0, v1 = sorted((cx + sx * 8.6, cx + sx * 9.3))                                                             # 太ももの横の排気口
        m.box(th, (v0, HIP_Y - 12, -2.5), (v1, HIP_Y - 4, 3.5), 'vent')
        for yy in (HIP_Y - 10.5, HIP_Y - 8, HIP_Y - 5.5):
            r0, r1 = sorted((cx + sx * 9.25, cx + sx * 9.5))
            m.box(th, (r0, yy - 0.5, -2), (r1, yy + 0.5, 3), 'glow')
        m.box(th, (cx - 3.8, HIP_Y - 3, -3.8), (cx + 3.8, HIP_Y + 3.5, 3.8), 'edge', sides='glow')                   # 腰の関節
        # シャープに: 太ももの外の横から後ろ下へ伸びる刃、前の重ねた装甲の裾から下へ尖る刃
        blade_x(m, th, (cx + sx * 9.8, HIP_Y - 5, 3.0), -55, 14, 5)
        blade_z(m, th, (cx, HIP_Y - THIGH_LEN + 1.5, -9.6), -90, 7, 7, kind='main')
        sh = m.bone('shin_' + side, 'root', (cx, KNEE_Y, KNEE_Z))
        rot = (SHIN_ANG, 0, 0)
        pv = (cx, KNEE_Y, KNEE_Z)
        L = SHIN_LEN
        m.box(sh, (cx - 4.5, KNEE_Y - L, KNEE_Z - 4), (cx + 4.5, KNEE_Y, KNEE_Z + 4), 'metal', rot=rot, pivot=pv)      # すね
        for i in range(3):                                                                                           # ふくらはぎの重ねた重い装甲（下ほど張り出す）
            y1 = KNEE_Y - 2 - i * 7.5
            m.box(sh, (cx - 5.8 - i * 0.2, y1 - 9, KNEE_Z + 3.9), (cx + 5.8 + i * 0.2, y1, KNEE_Z + 7.0 + i * 1.1), 'heavy', back='armor', rot=rot, pivot=pv)
            m.box(sh, (cx - 4.8, y1 - 9.1, KNEE_Z + 6.6 + i * 1.1), (cx + 4.8, y1 - 8.3, KNEE_Z + 7.3 + i * 1.1), 'gblue', rot=rot, pivot=pv)
        for i in range(2):                                                                                           # 前のすね当て（2 段の重ね）
            y1 = KNEE_Y - 3 - i * 10
            m.box(sh, (cx - 5.0 + i * 0.3, y1 - 11, KNEE_Z - 5.0 - i * 0.9), (cx + 5.0 - i * 0.3, y1, KNEE_Z - 3.95), 'armor', front='stripe', rot=rot, pivot=pv)
        for dx in (-2.3, 2.3):                                                                                       # ふくらはぎの推進器（赤く光る）
            m.box(sh, (cx + dx - 1.6, KNEE_Y - L + 2, KNEE_Z + 9.9), (cx + dx + 1.6, KNEE_Y - L + 8, KNEE_Z + 12.2), 'dark', rot=rot, pivot=pv)
            m.box(sh, (cx + dx - 1.0, KNEE_Y - L + 1.95, KNEE_Z + 10.4), (cx + dx + 1.0, KNEE_Y - L + 2.3, KNEE_Z + 11.7), 'glow', rot=rot, pivot=pv)
        m.box(sh, (cx - 6, KNEE_Y - 3.5, KNEE_Z - 6), (cx + 6, KNEE_Y + 4.5, KNEE_Z + 4.5), 'heavy', top='plate')    # ひざの装甲
        m.box(sh, (cx - 1.6, KNEE_Y - 1, KNEE_Z - 12), (cx + 1.6, KNEE_Y + 2.4, KNEE_Z - 5.95), 'edge', rot=(-25, 0, 0), pivot=(cx, KNEE_Y, KNEE_Z - 6))  # ひざの刃
        m.box(sh, (cx - 0.5, KNEE_Y + 2.3, KNEE_Z - 11), (cx + 0.5, KNEE_Y + 2.7, KNEE_Z - 6.5), 'gblue', rot=(-25, 0, 0), pivot=(cx, KNEE_Y, KNEE_Z - 6))
        for (y0, y1, z0, z1) in ((KNEE_Y - L + 2, KNEE_Y - 4, KNEE_Z + 7.4, KNEE_Z + 11), (KNEE_Y - L - 5, KNEE_Y - L + 2, KNEE_Z + 7.4, KNEE_Z + 10),
                                 (KNEE_Y - L - 9, KNEE_Y - L - 5, KNEE_Z + 7.6, KNEE_Z + 9)):                       # すねの後ろの刃のひれ
            o = sx * 6.2
            m.box(sh, (cx + o - 0.6, y0, z0), (cx + o + 0.6, y1, z1), 'armor', sides='heavy', rot=rot, pivot=pv)
        m.box(sh, (cx - 4.5, ANKLE_Y - 1, ANKLE_Z - 3.5), (cx + 4.5, ANKLE_Y + 4.5, ANKLE_Z + 3.5), 'edge', sides='glow')  # 足首の関節
        # シャープに: ふくらはぎの重ねた装甲の裾から後ろ下へ尖る刃（すねの傾きの上で置く）
        dy, dz = ANKLE_Y - KNEE_Y, ANKLE_Z - KNEE_Z
        L2 = math.hypot(dy, dz)
        uy, uz = dy / L2, dz / L2
        ny, nz = uz, -uy                                                                                              # すねの後ろ向きの法線
        if ny * 0 + nz < 0:
            ny, nz = -ny, -nz
        shin_tilt = -math.degrees(math.atan2(uz, -uy))                                                              # すねの向き（下を 0 に、後ろへ倒れる分）
        for t, off, ln in ((0.35, 8.2, 11), (0.62, 9.3, 12), (0.88, 10.4, 10)):
            ry = KNEE_Y + uy * L * t + ny * off
            rz = KNEE_Z + uz * L * t + nz * off
            for dx in (-3.0, 3.0):
                blade_x(m, sh, (cx + dx, ry, rz), -25 + shin_tilt * 0.5, ln, 3.2)
        m.box(sh, (cx - 1.4, ANKLE_Y + 2, ANKLE_Z + 3), (cx + 1.4, KNEE_Y - 4, ANKLE_Z + 5), 'metal', back='edge', rot=(SHIN_ANG * 0.15, 0, 0), pivot=(cx, ANKLE_Y, ANKLE_Z))  # 足首の油圧の筒
    return m


if __name__ == '__main__':
    os.makedirs(os.path.join(g.ASSETS, 'geo', 'entity'), exist_ok=True)
    os.makedirs(os.path.join(g.ASSETS, 'textures', 'entity'), exist_ok=True)
    g.write(galliafort())
