"""AEC 級英雄機「弾幕特化【黒死蝶 / メメントモリ】」の形とテクスチャ。

2026-10-10 ユーザー指定「巨大な機械の黒い蝶」→「生物的な目はなく機械的な冷たい印象で」
→ 2026-10-13 ユーザー指定「女性型のアンドロイドを意識した蝶の羽を持つ生体に」→「機械的で無機質なアンドロイドとしてリモデル」
→ 「ガッツリポリゴン数あげていいんで流線型で丸みのあるアンドロイドデザインして」
→ 同日、ユーザーの参考画像を元に作り直し（細身の黒い光沢の体・長く細い脚の尖った先・虫のような滑らかな仮面の頭と長く曲がる触角・
   爪の長い手・紫の半透明の翅にデジタルのモザイクの紋・長い尾の後翅。光は紫と桃色）
→ 同日「触覚ではなく複雑半透明な幾何学的ヘイローにして」: 触角を外し、頭の後ろに半透明の幾何学の光輪（輪・六芒星・目盛り・四角が層ごとに回る）
→ 同日「ここから装甲を追加して」（質問で: 中くらい・紫がかった暗い金属・胸と肩と背・腕・脚と頭。腰は付けない）: armor()
→ 同日「八枚の装甲を三段にしてドレススカートのようにして腰から下に宙に浮かせる」: floating_skirt()
  →「スカート状は間隔を開けて回転するようにして」: 板の間を開け、段ごとの輪が逆向きに回る

  geo/entity/memento_mori.geo.json
  textures/entity/memento_mori.png（＋ _glowmask.png）… 512x512。塗り・詰め込みは gen_chrono_gazers.py の Atlas を使う

形: 背に蝶の翅を持つ巨大な女性型のアンドロイド。実寸の半分で作り、描画（client/MementoMoriRenderer）で 2 倍にする
（身長約 5.5 ブロック、翅を広げて約 10 ブロック）。丸みは薄い輪切りを重ねて作る（1 枚は十字に重ねた 2 つの箱。prism / ellipsoid）。
  root > body（腰 0,24,0。くびれた黒い光沢の胴、胸の形の装甲、節のある腹、紫の光の継ぎ目、肩の尖った装甲、襟の棘、翅の付け根）
         > head（首 0,36,0。前へ尖った卵形の黒い仮面の頭、細い紫の目の光、後ろへ伸びる後頭部）
             > halo（頭の後ろの光輪の中心 0,44.6,2.8）> halo_outer / halo_mid / halo_inner（層。Z の回転で別々の速さで回す）
         > arm_r（肩 -3.9,33.4,0）> forearm_r（肘 -3.9,27.6,0）/ arm_l・forearm_l（+X）… 細い腕、前腕の刃、長い爪の指
         > skirt（腰の下 0,23,0）> skirt_ring_0..2（段ごとの輪。Y の回転で段ごとに逆向きに回す）> skirt_dir_<段>_<枚>（向きだけ）
             > skirt_<段>_<枚>（板。前後に揺らし、上下に浮かせる）
         > legs（股 0,22.5,0）> leg_r / leg_l（股関節 ∓1.25,22.5,0）> shin_r / shin_l（膝 ∓1.25,12,0）… 長く細い脚、尖った先
         > wing_fl / wing_fr / wing_hl / wing_hr（前翅・後翅。l は +X 側。付け根 ±2.4,31,7）… 第 1 形態の紫
         > wing_fl_red など（同じ形で赤く光る翅。第 2 形態で入れ替える）
  翅は背の後ろの縦の薄い板（厚み 0.3、XY の面）で、前後の面に翅の形の絵を描き、形の外は透けて抜く。
  膜は半透明（描画は entityTranslucent）で、黒い翅脈と縁の骨組みの間に、紫の膜と、外ほど多いデジタルのモザイクの四角（紫・桃色・淡い紫・黒）
"""
import math

import gen_chrono_gazers as g

g.TEX = 512
VIO, VIO_L, VIO_D = 0xA45CFF, 0xE8D0FF, 0x4A2088                        # 紫の光（明るい所・暗い所）
MAG = 0xFF4FD8                                                          # 桃色の光
RED, RED_L, RED_D = 0xFF2A4A, 0xFFB0C0, 0xA0102A                        # 第 2 形態の赤
RED_ACC = 0xFF7A3A                                                      # 第 2 形態の差し色
g.MAT.update({
    'vio': (VIO, VIO_L, VIO_D, 'glow', True),                    # 紫に光る
    'mag': (MAG, 0xFFC0F0, 0xA0207A, 'glow', True),             # 桃色に光る
    'pale': (0xF0E4FF, 0xFFFFFF, 0xB8A0E0, 'glow', True),        # 淡く白く光る
})
HALO = {   # 光輪の半透明の光（色、不透明度）。発光マスクにも同じ不透明度で置く
    'halo': (VIO, 150),
    'halo_l': (VIO_L, 200),
    'halo_m': (MAG, 170),
}
SMOOTH = {   # 縁の線を描かない、なめらかな面（輪切りを重ねても縞にならない）。上・下の明るさの差だけ付ける
    'gloss': (0x342A48, 0x120E1A),       # 黒い光沢の外装（紫がかり、上ほど照る）
    'sheen': (0x6A5890, 0x2A2240),       # 外装の照り（縁・稜線）
    'core': (0x0A080E, 0x050408),        # 継ぎ目の奥の暗い所
    'metal': (0x4A4258, 0x24202E),       # 関節の暗い金属
    'armor': (0x5A4880, 0x2A2040),       # 装甲の板（紫がかった暗い金属。体の黒より少し明るい）
    'armor_e': (0x8A74B8, 0x4A3A6A),     # 装甲の板の稜線・重なりの縁
    'visor': (0x07060C, 0x030205),       # 仮面の目の黒いガラス
}
DENSITY = {}                                                          # 面の 1px あたりの texel（翅は 3）


def _put(atlas, u, v, c, a=255, glow=None):
    """glow を渡すと光らせる。GeckoLib の発光の層は発光マスクの点の色ではなく下の絵の色を使い、マスクの不透明度で描き、
    下の絵からその点を消す。なので半透明の膜はマスクにも同じ不透明度で置く（255 にすると膜が不透明になる）"""
    atlas.base.putpixel((u, v), g.rgb(c) + (a,))
    if glow is not None:
        atlas.glow.putpixel((u, v), g.rgb(glow) + (a,))


def _mix(a, b, t):
    t = max(0.0, min(1.0, t))
    ra, ga, ba = g.rgb(a)
    rb, gb, bb = g.rgb(b)
    return (int(ra + (rb - ra) * t) << 16) | (int(ga + (gb - ga) * t) << 8) | int(ba + (bb - ba) * t)


def _smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ 翅
def _wing_inside(x, t, kind):
    """翅の形（x: 付け根 0 → 先 1、t: 下 0 → 上 1）。中なら True"""
    if kind == 'fore':
        # 前翅: 付け根から斜め上の先へ伸びる前の縁、先から下へ丸くふくらむ外の縁、内の縁
        if x > 0.98:
            return False
        top = 0.30 + 0.70 * x ** 0.75
        outer = 0.60 + 0.38 * (t - 0.10) / 0.90 + 0.07 * math.sin(math.pi * max(0.0, min(1.0, (t - 0.10) / 0.90)))
        bottom = 0.10 * (x / 0.60) ** 1.6 if x < 0.60 else 0.10
        return bottom <= t <= top and x <= outer
    # 後翅: 付け根から垂れる丸い扇（上の 6 割）と、そこから下へ長く伸びて先が少し広がる尾
    if x > 0.92:
        return False
    top = 0.98 - 0.10 * x
    s = math.sin(math.pi * min(1.0, x / 0.92))
    bottom = 0.74 - 0.30 * s ** 0.8
    if bottom <= t <= top:
        return True
    if t < bottom:
        cx = 0.50 + 0.06 * (1.0 - t / 0.5)                                 # 尾は少し外へ流れる
        half = 0.045 + (0.035 if t < 0.10 else 0.0) * (1.0 - t / 0.10 if t < 0.10 else 0.0) + 0.04 * max(0.0, (t - 0.30) / 0.30)
        return abs(x - cx) < half and t >= 0.0
    return False


VEINS = {   # 翅脈（付け根から先への線。x, t）
    'fore': ((0.02, 0.20), [(0.97, 0.99), (0.90, 0.80), (0.82, 0.58), (0.72, 0.36), (0.58, 0.14), (0.36, 0.06)]),
    'hind': ((0.02, 0.90), [(0.88, 0.80), (0.80, 0.60), (0.66, 0.48), (0.53, 0.02), (0.36, 0.48), (0.18, 0.58)]),
}


def _seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    k = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - ax - k * dx, py - ay - k * dy)


def _hash(a, b, seed):
    h = (a * 73856093) ^ (b * 19349663) ^ (seed * 83492791)
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def _paint_wing(atlas, u, v, w, h, kind, flip, red):
    main, light, dark = (RED, RED_L, RED_D) if red else (VIO, VIO_L, VIO_D)
    acc = RED_ACC if red else MAG
    frame, frame_l = 0x0C0A12, 0x2A2238

    def coords(i, j):
        return ((w - 1 - i) if flip else i) / max(1, w - 1), 1.0 - j / max(1, h - 1)

    def inside(i, j):
        return 0 <= i < w and 0 <= j < h and _wing_inside(*coords(i, j), kind)

    root, ends = VEINS[kind]
    seed = 1 if kind == 'fore' else 2
    cs = 4                                                                  # モザイクの四角の大きさ（texel）
    for j in range(h):
        for i in range(w):
            if not inside(i, j):
                continue
            x, t = coords(i, j)
            # 縁からの近さ（texel）
            edge = 9
            for k in range(1, 9):
                if not (inside(i - k, j) and inside(i + k, j) and inside(i, j - k) and inside(i, j + k)):
                    edge = k
                    break
            if edge <= 2:                                                   # 縁の黒い骨組み（外の 1 texel は照り）
                _put(atlas, u + i, v + j, frame_l if edge == 1 else frame)
                continue
            # 翅脈: 黒い細い骨組み（付け根に近いほど太い）。太い脈の芯は淡く光る
            vein = False
            for n, (ex, et) in enumerate(ends):
                d = _seg_dist(x * w, t * h, root[0] * w, root[1] * h, ex * w, et * h)
                if d < 1.7 - 0.9 * x:
                    core = n % 2 == 0 and d < 0.45 and 0.15 < x < 0.7
                    _put(atlas, u + i, v + j, light if core else frame, 255, light if core else None)
                    vein = True
                    break
            if vein:
                continue
            r = math.hypot(x - root[0], (t - root[1]) * h / w)
            outer = _smoothstep(0.25, 0.95, r)
            if edge <= 4 and x > 0.2:                                       # 縁の内の紫の光の線（ところどころ途切れる）
                if _hash(i // 3, j // 3, seed + 7) > 0.25:
                    _put(atlas, u + i, v + j, main, 230, main)
                    continue
            # デジタルのモザイク: 外ほど多い。紫・桃色・淡い紫の光る四角と、黒い四角
            ci, cj = i // cs, j // cs
            hv = _hash(ci, cj, seed)
            rim = (i % cs == 0 or j % cs == 0)
            if hv < 0.04 + 0.22 * outer:
                c = _mix(main, light, _hash(ci, cj, seed + 3) * 0.4)
                _put(atlas, u + i, v + j, _mix(c, dark, 0.35) if rim else c, 225, c)
                continue
            if hv < 0.06 + 0.30 * outer:
                _put(atlas, u + i, v + j, _mix(acc, dark, 0.3) if rim else acc, 215, acc)
                continue
            if hv < 0.07 + 0.34 * outer:
                _put(atlas, u + i, v + j, light, 240, light)
                continue
            if hv < 0.12 + 0.50 * outer:
                _put(atlas, u + i, v + j, 0x120E1A if not rim else 0x1E1828, 215)
                continue
            # 膜: 付け根は暗く、外ほど紫に明るい半透明の光。細い横の走査線
            col = _mix(_mix(dark, 0x120E1A, 0.5), main, 0.15 + 0.7 * r)
            if j % 6 == 0:
                col = _mix(col, light, 0.25)
            a = int(80 + 80 * min(1.0, r))
            _put(atlas, u + i, v + j, col, a, col)


def _paint_smooth(atlas, u, v, w, h, mat):
    top, bottom = SMOOTH[mat]
    for j in range(h):
        for i in range(w):
            _put(atlas, u + i, v + j, _mix(top, bottom, (j + 0.5) / h * 0.6))


def _paint_halo(atlas, u, v, w, h, mat):
    c, a = HALO[mat]
    for j in range(h):
        for i in range(w):
            _put(atlas, u + i, v + j, c, a, c)


_orig_paint = g.Atlas.paint


def _paint(self, mat, w, h):
    if mat == 'none':
        return self.alloc(w, h)
    painter = None
    if isinstance(mat, str) and mat.startswith('wing:'):
        _, kind, flip, red = mat.split(':')
        painter = lambda u, v: _paint_wing(self, u, v, w, h, kind, flip == '1', red == '1')
    elif mat in SMOOTH:
        painter = lambda u, v: _paint_smooth(self, u, v, w, h, mat)
    elif mat in HALO:
        painter = lambda u, v: _paint_halo(self, u, v, w, h, mat)
    if painter is None:
        return _orig_paint(self, mat, w, h)
    key = (mat, w, h)
    if key in self.cache:
        return self.cache[key]
    u, v = self.alloc(w, h)
    painter(u, v)
    self.cache[key] = (u, v)
    return u, v


g.Atlas.paint = _paint


def density(mat):
    return 3 if mat.startswith('wing:') else DENSITY.get(mat, 1)


def write(m):
    """gen_chrono_gazers.write と同じ（面ごとの UV）。ただし材質ごとに面の 1px を density 枚の texel で描く。翅の絵から先に詰める"""
    import json
    import os
    atlas = g.Atlas(sum(map(ord, m.ident)))
    faces = []
    for b in m.bones:
        for cube in b['cubes']:
            for f in g.FACES:
                faces.append((b['name'], cube, f))
    order = sorted(range(len(faces)), key=lambda k: -density(faces[k][1][2][faces[k][2]]))
    uvs = {}
    for k in order:
        _, cube, f = faces[k]
        a, c, fm = cube[:3]
        mat = fm[f]
        dw, dh = g.face_dims(a, c, f)
        d = density(mat)
        w, h = max(1, int(math.ceil(dw * d - 1e-6))), max(1, int(math.ceil(dh * d - 1e-6)))
        uu, vv = atlas.paint(mat, w, h)
        uvs[k] = {'uv': [uu, vv], 'uv_size': [w, h]}
    out, k = [], 0
    for b in m.bones:
        o = {'name': b['name'], 'pivot': [round(p, 3) for p in b['pivot']]}
        if b['parent']:
            o['parent'] = b['parent']
        if b['rotation']:
            o['rotation'] = b['rotation']
        if b['cubes']:
            cubes = []
            for cube in b['cubes']:
                a, c = cube[:2]
                uv = {}
                for f in g.FACES:
                    uv[f] = uvs[k]
                    k += 1
                co = {'origin': [round(t, 3) for t in a], 'size': [round(c[i] - a[i], 3) for i in range(3)], 'uv': uv}
                if len(cube) > 3:
                    co['pivot'] = [round(t, 3) for t in cube[4]]
                    co['rotation'] = cube[3]
                cubes.append(co)
            o['cubes'] = cubes
        out.append(o)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{m.ident}', 'texture_width': g.TEX, 'texture_height': g.TEX,
                        'visible_bounds_width': 6, 'visible_bounds_height': 6, 'visible_bounds_offset': [0, 2.5, 0]},
        'bones': out}]}
    with open(os.path.join(g.ASSETS, 'geo', 'entity', m.ident + '.geo.json'), 'w', encoding='utf-8', newline='\n') as fp:
        json.dump(geo, fp, indent=1)
    atlas.base.save(os.path.join(g.ASSETS, 'textures', 'entity', m.ident + '.png'))
    atlas.glow.save(os.path.join(g.ASSETS, 'textures', 'entity', m.ident + '_glowmask.png'))
    n = sum(len(b['cubes']) for b in m.bones)
    print(f'{m.ident}: {len(m.bones)} bones, {n} cubes, texture used to y={atlas.y + atlas.row}')


# ------------------------------------------------------------------ 丸みの部品
K = 0.72   # 輪切り 1 枚の十字の箱の細い側の割合（円が十字の内に収まる）


def _slice(m, bone, axis, c, ru, rv, a0, a1, mat, clip=None):
    """軸 axis（'x' 'y' 'z'）に垂直な輪切り 1 枚（a0〜a1）。断面の半径 ru・rv（y 軸なら x・z、x 軸なら y・z、z 軸なら x・y）。
    十字に重ねた 2 つの箱。clip は {'x0','x1','y0','y1','z0','z1'} のどれか（その外を切る）"""
    if ru < 0.04 or rv < 0.04:
        return
    for fu, fv in ((1.0, K), (K, 1.0)):
        if axis == 'y':
            lo = [c[0] - ru * fu, a0, c[2] - rv * fv]
            hi = [c[0] + ru * fu, a1, c[2] + rv * fv]
        elif axis == 'x':
            lo = [a0, c[1] - ru * fu, c[2] - rv * fv]
            hi = [a1, c[1] + ru * fu, c[2] + rv * fv]
        else:
            lo = [c[0] - ru * fu, c[1] - rv * fv, a0]
            hi = [c[0] + ru * fu, c[1] + rv * fv, a1]
        if clip:
            for i, ax in enumerate('xyz'):
                if ax + '0' in clip:
                    lo[i] = max(lo[i], clip[ax + '0'])
                if ax + '1' in clip:
                    hi[i] = min(hi[i], clip[ax + '1'])
        if all(hi[i] - lo[i] > 0.03 for i in range(3)):
            m.box(bone, tuple(lo), tuple(hi), mat)


def prism(m, bone, axis, c, a0, a1, r0, r1, mat, step=0.6, clip=None, bend=None):
    """輪切りを重ねた細長い形（a0 → a1 で半径 r0 → r1 へ。r は (ru, rv)）。bend(t) があれば中心を (du, dv) ずらす（曲がった爪・刃）"""
    n = max(1, int(round(abs(a1 - a0) / step)))
    for i in range(n):
        t = (i + 0.5) / n
        ru, rv = r0[0] + (r1[0] - r0[0]) * t, r0[1] + (r1[1] - r0[1]) * t
        b0, b1 = a0 + (a1 - a0) * i / n, a0 + (a1 - a0) * (i + 1) / n
        cc = list(c)
        if bend:
            du, dv = bend(t)
            ia = 'xyz'.index(axis)
            others = [k for k in range(3) if k != ia]
            cc[others[0]] += du
            cc[others[1]] += dv
        _slice(m, bone, axis, cc, ru, rv, min(b0, b1), max(b0, b1), mat, clip)


def ellipsoid(m, bone, c, r, mat, step=0.45, axis='y', clip=None, squash=None, shift=None):
    """輪切りを重ねた楕円体（中心 c、半径 r = (rx, ry, rz)）。squash(t) で輪切りの半径に掛け、shift(t) で輪切りの中心を前後（z）へずらす
    （t: 軸に沿って -1〜1）"""
    ia = 'xyz'.index(axis)
    ra = r[ia]
    ru, rv = [r[i] for i in range(3) if i != ia]
    n = max(2, int(round(2 * ra / step)))
    for i in range(n):
        t0, t1 = -1 + 2 * i / n, -1 + 2 * (i + 1) / n
        t = (t0 + t1) / 2
        f = math.sqrt(max(0.0, 1 - t * t))
        sq = squash(t) if squash else 1.0
        cc = list(c)
        if shift:
            cc[2] += shift(t)
        _slice(m, bone, axis, cc, ru * f * sq, rv * f * sq, c[ia] + ra * t0, c[ia] + ra * t1, mat, clip)


def hline(m, bone, p0, p1, s, mat):
    """光輪の細い線: p0 → p1 を、隣と触れる程度の間の小さな箱でつなぐ（XY の面。厚みは薄い）"""
    n = max(1, int(math.ceil(math.dist(p0, p1) / (1.7 * s))))
    for k in range(n + 1):
        t = k / n
        x, y = p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t
        m.box(bone, (x - s, y - s, p0[2] - s * 0.5), (x + s, y + s, p0[2] + s * 0.5), mat)


def hring(m, bone, c, r, s, mat, dashes=0, a0=0.0, a1=360.0):
    """光輪の輪（中心 c、半径 r）。dashes があればその数の破線。a0〜a1 は度（弧）"""
    n = max(8, int(math.ceil(2 * math.pi * r * (a1 - a0) / 360.0 / (1.7 * s))))
    for k in range(n + 1 if a1 - a0 < 360 else n):
        t = k / n
        a = math.radians(a0 + (a1 - a0) * t)
        if dashes and int(t * dashes * 2) % 2 == 1:
            continue
        x, y = c[0] + r * math.cos(a), c[1] + r * math.sin(a)
        m.box(bone, (x - s, y - s, c[2] - s * 0.5), (x + s, y + s, c[2] + s * 0.5), mat)


def hpoly(m, bone, c, r, sides, rot, s, mat):
    """光輪の正多角形（頂点が半径 r、rot は度）"""
    pts = []
    for k in range(sides):
        a = math.radians(rot + 360.0 * k / sides)
        pts.append((c[0] + r * math.cos(a), c[1] + r * math.sin(a), c[2]))
    for k in range(sides):
        hline(m, bone, pts[k], pts[(k + 1) % sides], s, mat)


def halo(m):
    """頭の後ろの半透明の幾何学の光輪（XY の面、正面を向く）。3 つの層が別々に回る（Java の animateMemento）
      halo_outer: 外の輪・破線の輪・24 本の目盛り・4 つの菱形の光
      halo_mid:   六芒星（2 つの三角）・中の輪・上下の弧
      halo_inner: 45 度回した四角・小さな輪・8 本の短い放射"""
    c = HALO_C
    m.bone('halo', 'head', c)
    for name in ('halo_outer', 'halo_mid', 'halo_inner'):
        m.bone(name, 'halo', c)
    o, mi, inn = (c[0], c[1], c[2] + 0.2), (c[0], c[1], c[2]), (c[0], c[1], c[2] - 0.2)
    hring(m, 'halo_outer', o, 8.0, 0.13, 'halo_l')
    hring(m, 'halo_outer', o, 7.35, 0.1, 'halo', dashes=24)
    for k in range(24):
        a = math.radians(k * 15.0)
        r0 = 7.55 if k % 2 else 7.35
        hline(m, 'halo_outer', (o[0] + r0 * math.cos(a), o[1] + r0 * math.sin(a), o[2]),
              (o[0] + 7.85 * math.cos(a), o[1] + 7.85 * math.sin(a), o[2]), 0.08, 'halo')
    for k in range(4):
        a = math.radians(45.0 + k * 90.0)
        x, y = o[0] + 8.0 * math.cos(a), o[1] + 8.0 * math.sin(a)
        for d in range(4):                                                    # 小さな菱形（段々の箱）
            hw = 0.15 + d * 0.13
            hh = 0.6 - d * 0.14
            m.box('halo_outer', (x - hw, y - hh, o[2] - 0.08), (x + hw, y + hh, o[2] + 0.08), 'halo_m')
    hpoly(m, 'halo_mid', mi, 7.0, 3, 90.0, 0.1, 'halo')
    hpoly(m, 'halo_mid', mi, 7.0, 3, 270.0, 0.1, 'halo')
    hring(m, 'halo_mid', mi, 4.9, 0.12, 'halo_l')
    hring(m, 'halo_mid', mi, 6.1, 0.1, 'halo_m', a0=25.0, a1=155.0)
    hring(m, 'halo_mid', mi, 6.1, 0.1, 'halo_m', a0=205.0, a1=335.0)
    hpoly(m, 'halo_inner', inn, 3.6, 4, 45.0, 0.1, 'halo_m')
    hpoly(m, 'halo_inner', inn, 3.6, 4, 0.0, 0.08, 'halo')
    hring(m, 'halo_inner', inn, 2.5, 0.1, 'halo_l')
    for k in range(8):
        a = math.radians(k * 45.0 + 22.5)
        hline(m, 'halo_inner', (inn[0] + 2.7 * math.cos(a), inn[1] + 2.7 * math.sin(a), inn[2]),
              (inn[0] + 3.3 * math.cos(a), inn[1] + 3.3 * math.sin(a), inn[2]), 0.08, 'halo_l')


def lerp_profile(profile, y):
    """(y, rx, rz[, z]) の並びから y での半径と前後の中心"""
    for p0, p1 in zip(profile, profile[1:]):
        if p0[0] <= y <= p1[0]:
            t = (y - p0[0]) / (p1[0] - p0[0])
            return tuple(p0[i] + (p1[i] - p0[i]) * t for i in range(1, len(p0)))
    return tuple(profile[-1][1:])


# ------------------------------------------------------------------ 形
# 翅の大きさ（px。実寸の半分）: 前翅の長さ・高さ、後翅の長さ・高さ（下へ長く伸びる尾を含む）、付け根
FORE_L, FORE_H, HIND_L, HIND_H = 38.0, 33.0, 30.0, 40.0
WING_X, WING_Y, WING_Z = 2.4, 31.0, 7.0   # 付け根は背から離す（宙に浮く裾の後ろの板と当たらないように）
NORTH_FLIP = '0'    # 正面の面の絵の左右の向き（+X の翅。プレビューで確かめた）
HALO_C = (0.0, 44.6, 2.8)   # 光輪の中心（頭の後ろ、頭のてっぺんの少し上）


def wing(m, name, sx, kind, red):
    """翅 1 枚: 背の後ろ（WING_Z）の縦の薄い板。前翅は付け根から外・上へ、後翅は外・下へ。前後の面に翅の絵"""
    m.bone(name, 'body', (sx * WING_X, WING_Y, WING_Z))
    length, height = (FORE_L, FORE_H) if kind == 'fore' else (HIND_L, HIND_H)
    x0, x1 = sorted((sx * WING_X, sx * (WING_X + length)))
    y0 = WING_Y - 3.0 if kind == 'fore' else WING_Y + 1.0 - height
    flip_n = NORTH_FLIP if sx > 0 else ('1' if NORTH_FLIP == '0' else '0')
    flip_s = '1' if flip_n == '0' else '0'
    r = 1 if red else 0
    m.box(name, (x0, y0, WING_Z - 0.15), (x1, y0 + height, WING_Z + 0.15), 'none',
          north='wing:%s:%s:%d' % (kind, flip_n, r), south='wing:%s:%s:%d' % (kind, flip_s, r))


def side_clip(x, sx, inner=0.0):
    """腕・脚の外側の半分だけ（x から外へ。inner だけ内へ広げる）"""
    return {'x0': x - inner} if sx > 0 else {'x1': x + inner}


def armor(m):
    """装甲の板（紫がかった暗い金属。縁に紫の光）。体の形の外へ少し浮かせた殻（楕円体・輪切りを前・後ろ・外の半分で切る）
      胸当て・背の板・重ねた肩当て 3 枚・上腕の外の板・籠手・手の甲・腿の前と外の板・膝当て・脛当て・足首の覆い・額の板・頬当て・頭の稜"""
    # 胸当て: 胸の前の殻、下の縁の紫の光、真ん中の稜と菱形の核
    ellipsoid(m, 'body', (0, 31.4, -0.3), (3.05, 2.5, 2.1), 'armor', step=0.3, clip={'z1': -0.7, 'y0': 29.0})
    _slice(m, 'body', 'y', (0, 0, -0.3), 2.95, 2.05, 28.85, 29.05, 'vio', clip={'z1': -0.7})
    prism(m, 'body', 'y', (0, 0, -2.38), 29.4, 33.4, (0.22, 0.12), (0.12, 0.08), 'armor_e', step=0.4)
    ellipsoid(m, 'body', (0, 31.2, -2.5), (0.38, 0.55, 0.14), 'vio', step=0.12, axis='z')
    # 背の板: 背の後ろの殻（翅の軸はこの外へ出る）と、下の縁の紫の光
    ellipsoid(m, 'body', (0, 31.0, 0.4), (2.85, 2.7, 1.95), 'armor', step=0.3, clip={'z0': 1.0, 'y0': 28.6})
    _slice(m, 'body', 'y', (0, 0, 0.4), 2.7, 1.85, 28.45, 28.65, 'vio', clip={'z0': 1.0})
    prism(m, 'body', 'y', (0, 0, 2.38), 28.8, 33.2, (0.2, 0.12), (0.12, 0.08), 'armor_e', step=0.4)
    for side, sx in (('r', -1), ('l', 1)):
        arm, fore = 'arm_' + side, 'forearm_' + side
        leg, shin = 'leg_' + side, 'shin_' + side
        x = sx * 3.9
        # 重ねた肩当て 3 枚（上ほど大きい。縁に紫の光）
        for k in range(3):
            cy = 34.1 - k * 0.75
            rx, rz = 1.55 - k * 0.12, 1.6 - k * 0.12
            ellipsoid(m, arm, (x + sx * 0.35, cy, 0), (rx, 0.85, rz), 'armor' if k != 1 else 'armor_e', step=0.25,
                      clip={'y0': cy - 0.15})
            _slice(m, arm, 'y', (x + sx * 0.35, 0, 0), rx + 0.04, rz + 0.04, cy - 0.3, cy - 0.15, 'vio')
        # 上腕の外の板
        prism(m, arm, 'y', (x, 0, 0), 32.0, 28.6, (0.82, 0.8), (0.66, 0.64), 'armor', step=0.4, clip=side_clip(x, sx, 0.15))
        # 籠手: 前腕を包む殻（肘の下と手首で縁が光る）と、手の甲の板
        prism(m, fore, 'y', (x, 0, 0), 26.6, 21.7, (0.72, 0.7), (0.56, 0.54), 'armor', step=0.4)
        _slice(m, fore, 'y', (x, 0, 0), 0.76, 0.74, 26.45, 26.65, 'vio')
        _slice(m, fore, 'y', (x, 0, 0), 0.6, 0.58, 21.6, 21.8, 'vio')
        ellipsoid(m, fore, (x + sx * 0.2, 20.3, -0.05), (0.32, 0.6, 0.5), 'armor', step=0.2, clip=side_clip(x, sx, -0.1))
        xl = sx * 1.25                                                                     # 脚の中心
        # 腿の前と外の板（上の縁が光る）
        prism(m, leg, 'y', (xl, 0, 0), 22.2, 14.4, (1.2, 1.15), (0.8, 0.78), 'armor', step=0.5, clip={'z1': 0.2})
        _slice(m, leg, 'y', (xl, 0, 0), 1.22, 1.17, 22.2, 22.4, 'vio', clip={'z1': 0.2})
        # 膝当て（真ん中に紫の点）
        ellipsoid(m, shin, (xl, 12.2, -0.45), (0.78, 0.9, 0.55), 'armor', step=0.2, clip={'z1': 0.0})
        ellipsoid(m, shin, (xl, 12.2, -1.0), (0.18, 0.25, 0.08), 'vio', step=0.08, axis='z')
        # 脛当て（前の半分。上の縁が光る）と、足首から先の覆い
        prism(m, shin, 'y', (xl, 0, 0), 11.0, 2.6, (0.8, 0.8), (0.48, 0.48), 'armor', step=0.5, clip={'z1': 0.15})
        _slice(m, shin, 'y', (xl, 0, 0), 0.82, 0.82, 10.95, 11.15, 'vio', clip={'z1': 0.15})
        prism(m, shin, 'y', (xl, 0, 0), 1.5, -1.2, (0.55, 0.58), (0.3, 0.32), 'armor', step=0.4,
              bend=lambda t: (0.0, -0.12 * t))
    # 頭: 額の板（目もとのガラスの上に張り出し、下の縁が光る）・頬当て・頭の上の稜（後ろへ）
    ellipsoid(m, 'head', (0, 41.4, -0.5), (2.15, 1.25, 2.25), 'armor', step=0.25, clip={'z1': -1.0, 'y0': 40.9})
    _slice(m, 'head', 'y', (0, 0, -0.5), 2.1, 2.2, 40.75, 40.95, 'vio', clip={'z1': -1.2})
    for sx in (-1, 1):
        ellipsoid(m, 'head', (sx * 1.75, 38.9, -0.7), (0.4, 1.05, 1.2), 'armor', step=0.2, axis='x')
    for k in range(10):
        z = -1.6 + k * 0.45
        y0 = 42.6 + 0.25 * math.sin(k / 9 * math.pi) + (0.1 if k < 3 else 0.0)
        hgt = 0.55 + 0.35 * math.sin(k / 9 * math.pi)
        m.box('head', (-0.16, y0, z), (0.16, y0 + hgt, z + 0.45), 'armor_e')


SKIRT_N, SKIRT_FLARE = 8, 14.0
SKIRT_TIERS = ((22.4, 18.3), (18.0, 13.9), (13.6, 9.4))   # 段の上・下（y）。段の間はすき間


def floating_skirt(m):
    """腰から下に宙に浮く 8 枚 × 3 段の装甲のドレスの裾。体から離して浮かせ、段ごとに少し外へずらし下ほど広がる。
    板と板の間は開ける（輪の周りの 6 割ほどが板）。段ごとに半枚ずらし、段ごとの輪（skirt_ring_<段>）が逆向きに回る（Java の animateMemento）。
    1 枚は紫がかった暗い金属の板に、真ん中の稜・下の縁の紫の光・上の縁の照り"""
    m.bone('skirt', 'body', (0, 23, 0))
    tan = math.tan(math.radians(SKIRT_FLARE))
    r = 3.5
    flare = (-SKIRT_FLARE, 0, 0)
    piv = (0, 23, -r)
    for t, (y1, y0) in enumerate(SKIRT_TIERS):
        ring = m.bone('skirt_ring_%d' % t, 'skirt', (0, 23, 0))
        z = -r - t * 0.3
        rr = r + (23.0 - (y0 + y1) / 2) * tan + t * 0.3
        half = math.pi * rr / SKIRT_N * 0.62
        for k in range(SKIRT_N):
            d = m.bone('skirt_dir_%d_%d' % (t, k), ring, (0, 23, 0), rot=(0, k * 360.0 / SKIRT_N + t * 180.0 / SKIRT_N, 0))
            sk = m.bone('skirt_%d_%d' % (t, k), d, (0, 23, -r))
            m.box(sk, (-half, y0, z - 0.18), (half, y1, z + 0.18), 'armor', rot=flare, pivot=piv)
            m.box(sk, (-half + 0.1, y0 - 0.02, z - 0.26), (half - 0.1, y0 + 0.22, z - 0.14), 'vio', rot=flare, pivot=piv)     # 下の縁の光
            m.box(sk, (-half + 0.05, y1 - 0.2, z - 0.24), (half - 0.05, y1, z - 0.15), 'armor_e', rot=flare, pivot=piv)    # 上の縁の照り
            m.box(sk, (-0.14, y0 + 0.3, z - 0.3), (0.14, y1 - 0.3, z - 0.16), 'armor_e', rot=flare, pivot=piv)              # 真ん中の稜
            if t == 2:
                m.box(sk, (-0.22, y0 + 0.6, z - 0.36), (0.22, y0 + 1.2, z - 0.26), 'vio', rot=flare, pivot=piv)          # 裾の紫の点


TORSO = (   # 胴の輪切り（y, 横の半径, 前後の半径, 前後の中心）: 腰の張り・細いくびれ・肋・胸・肩の丸み
    (21.6, 2.2, 1.6, 0.0), (22.6, 2.75, 1.85, 0.0), (23.8, 2.6, 1.75, 0.0), (25.0, 1.95, 1.4, 0.0), (26.2, 1.6, 1.2, 0.05),
    (27.4, 1.75, 1.3, 0.05), (28.6, 2.25, 1.55, 0.0), (30.0, 2.65, 1.7, -0.05), (31.6, 2.8, 1.75, 0.0), (32.8, 2.7, 1.65, 0.05),
    (33.8, 2.1, 1.35, 0.1), (34.4, 1.2, 0.9, 0.1),
)


def mementomori():
    m = g.Model('memento_mori')
    m.bone('root')
    m.bone('body', 'root', (0, 24, 0))
    # 胴: 黒い光沢の輪切り。腹（25〜28.4）は節（間に暗い溝）
    y = 21.6
    while y < 34.4 - 1e-6:
        y1 = min(34.4, y + 0.4)
        rx, rz, zc = lerp_profile(TORSO, (y + y1) / 2)
        groove = 25.0 <= y < 28.4 and int((y - 25.0) / 0.4) % 3 == 2
        _slice(m, 'body', 'y', (0, 0, zc), rx * (0.94 if groove else 1.0), rz * (0.94 if groove else 1.0), y, y1, 'core' if groove else 'gloss')
        y = y1
    # 胸の形の装甲（左右のふくらみ）と、下の縁の照り、間の紫の光
    for sx in (-1, 1):
        ellipsoid(m, 'body', (sx * 1.2, 31.0, -1.25), (1.15, 1.15, 0.9), 'gloss', axis='z', step=0.25)
        ellipsoid(m, 'body', (sx * 1.25, 30.1, -1.55), (0.95, 0.3, 0.55), 'sheen', axis='z', step=0.2)
    m.box('body', (-0.12, 29.2, -1.9), (0.12, 31.8, -1.7), 'vio')                                      # 胸の間の紫の光
    for sx in (-1, 1):                                                                                 # 鎖骨の照り（首から肩へ）
        prism(m, 'body', 'x', (0, 33.5, -1.2), sx * 0.6, sx * 2.6, (0.18, 0.18), (0.12, 0.12), 'sheen', step=0.3,
              bend=lambda t: (-0.3 * t, 0.2 * t))
    # 紫の光の継ぎ目: 腹の真ん中の線、くびれから胸の下へ斜めに上がる線、腰の V の線
    m.box('body', (-0.1, 24.4, -1.42), (0.1, 28.6, -1.3), 'vio')
    for sx in (-1, 1):
        for k in range(6):
            yy = 26.4 + k * 0.5
            xx = sx * (1.2 + k * 0.22)
            a0, a1 = sorted((xx, xx + sx * 0.25))
            _, rz, zc = lerp_profile(TORSO, yy + 0.2)
            m.box('body', (a0, yy, zc - rz * 0.9 - 0.06), (a1, yy + 0.5, zc - rz * 0.9 + 0.1), 'vio')
        for k in range(5):
            yy = 22.4 + k * 0.45
            xx = sx * (2.4 - k * 0.42)
            a0, a1 = sorted((xx, xx + sx * 0.3))
            m.box('body', (a0, yy, -1.9), (a1, yy + 0.3, -1.78), 'vio')
    # 腰の張り出した装甲（股の上の三角の板）
    for sx in (-1, 1):
        ellipsoid(m, 'body', (sx * 2.15, 22.6, 0), (0.75, 1.3, 1.6), 'sheen', step=0.3)
    # 首: 細い首の輪切りと、首の両わきから上へ立つ襟の棘
    prism(m, 'body', 'y', (0, 0, 0.15), 34.0, 37.0, (0.75, 0.7), (0.6, 0.6), 'metal', step=0.4)
    for sx in (-1, 1):
        prism(m, 'body', 'y', (sx * 1.3, 0, 0.3), 33.6, 37.2, (0.45, 0.35), (0.05, 0.05), 'gloss', step=0.4,
              bend=lambda t, sx=sx: (sx * 0.6 * t, 0.4 * t))
    # 翅の付け根: 背の球と、後ろへ伸びる細い軸
    for sx in (-1, 1):
        ellipsoid(m, 'body', (sx * 1.6, 31.2, 1.5), (0.8, 0.8, 0.8), 'metal', step=0.3)
        prism(m, 'body', 'z', (sx * 2.1, 31.2, 0), 1.8, WING_Z, (0.35, 0.35), (0.22, 0.22), 'metal', step=0.5)
        ellipsoid(m, 'body', (sx * 2.3, 31.2, WING_Z - 0.2), (0.45, 0.45, 0.45), 'armor', step=0.2)
        ellipsoid(m, 'body', (sx * 1.6, 31.2, 2.25), (0.3, 0.3, 0.1), 'vio', step=0.1, axis='z')
    # 頭: 前へ少し尖った卵形の黒い仮面（あごへ細く尖る）と、後ろへ伸びる後頭部。目は細い紫の光（斜めに上がる）
    m.bone('head', 'body', (0, 36, 0))
    ellipsoid(m, 'head', (0, 40.0, -0.4), (2.05, 3.0, 2.3), 'gloss', step=0.3,
              squash=lambda t: 0.55 + 0.45 * min(1.0, (t + 1.0) / 1.1), shift=lambda t: -0.35 * max(0.0, -t))
    ellipsoid(m, 'head', (0, 41.0, 0.5), (1.7, 2.0, 1.9), 'gloss', step=0.3, axis='z')
    ellipsoid(m, 'head', (0, 40.2, -1.2), (1.75, 1.2, 1.4), 'visor', step=0.25, clip={'z1': -1.6})   # 目もとの黒いガラス
    for sx in (-1, 1):
        for k in range(4):
            xx = sx * (0.35 + k * 0.32)
            a0, a1 = sorted((xx, xx + sx * 0.34))
            yy = 40.15 + k * 0.1
            m.box('head', (a0, yy, -2.62 + k * 0.08), (a1, yy + 0.22, -2.5 + k * 0.08), 'vio')
    halo(m)
    # 腕: 球の肩と尖った肩の装甲、細い上腕、球の肘、前腕の外の刃、細い手首、手と長い爪の指 4 本
    for side, sx in (('r', -1), ('l', 1)):
        arm, fore = 'arm_' + side, 'forearm_' + side
        x = sx * 3.9
        m.bone(arm, 'body', (x, 33.4, 0))
        m.bone(fore, arm, (x, 27.6, 0))
        ellipsoid(m, arm, (x, 33.2, 0), (0.85, 0.85, 0.85), 'metal', step=0.3)
        ellipsoid(m, arm, (x + sx * 0.2, 33.6, 0), (1.25, 1.0, 1.25), 'gloss', step=0.25, clip={'y0': 33.3})
        prism(m, arm, 'y', (x + sx * 0.7, 0, 0), 34.0, 36.0, (0.35, 0.3), (0.04, 0.04), 'sheen', step=0.3,
              bend=lambda t, sx=sx: (sx * 0.8 * t, 0.0))                                             # 肩の棘
        prism(m, arm, 'y', (x, 0, 0), 32.6, 28.0, (0.68, 0.65), (0.5, 0.48), 'gloss', step=0.5)
        ellipsoid(m, fore, (x, 27.6, 0), (0.58, 0.58, 0.58), 'metal', step=0.25)
        prism(m, fore, 'y', (x, 0, 0), 27.1, 21.2, (0.58, 0.55), (0.42, 0.4), 'gloss', step=0.5)
        prism(m, fore, 'y', (x + sx * 0.45, 0, 0.1), 26.5, 21.8, (0.12, 0.35), (0.06, 0.12), 'sheen', step=0.5,
              bend=lambda t, sx=sx: (sx * 0.35 * math.sin(t * math.pi), 0.0))                         # 前腕の外の刃
        m.box(fore, (x - 0.08, 22.4, -0.5), (x + 0.08, 26.0, -0.36), 'vio')                             # 前腕の前の紫の線
        ellipsoid(m, fore, (x, 21.0, 0), (0.36, 0.3, 0.36), 'metal', step=0.2)
        ellipsoid(m, fore, (x, 20.2, -0.05), (0.38, 0.75, 0.5), 'gloss', step=0.25)
        for f in range(4):                                                                             # 長い爪の指（内へ曲がる）
            z = -0.45 + f * 0.3
            prism(m, fore, 'y', (x + sx * 0.05, 0, z), 19.6, 16.6, (0.12, 0.1), (0.03, 0.03), 'gloss', step=0.3,
                  bend=lambda t: (0.0, -0.5 * t * t))
        prism(m, fore, 'y', (x - sx * 0.35, 0, -0.3), 20.0, 18.4, (0.1, 0.09), (0.03, 0.03), 'gloss', step=0.3)   # 親指
    # 脚: 長く細い。腿は太く、膝の球、すねの前の刃、足首から下は下へ尖る先
    m.bone('legs', 'body', (0, 22.5, 0))
    for side, sx in (('r', -1), ('l', 1)):
        leg, shin = 'leg_' + side, 'shin_' + side
        x = sx * 1.25
        m.bone(leg, 'legs', (x, 22.5, 0))
        m.bone(shin, leg, (x, 12.0, 0))
        prism(m, leg, 'y', (x, 0, 0), 22.8, 12.6, (1.05, 1.0), (0.62, 0.6), 'gloss', step=0.5)
        m.box(leg, (x - 0.08, 14.5, -0.95), (x + 0.08, 20.5, -0.8), 'vio')                              # 腿の前の紫の線
        ellipsoid(m, shin, (x, 12.0, -0.05), (0.62, 0.7, 0.66), 'metal', step=0.25)
        prism(m, shin, 'y', (x, 0, 0), 11.6, 2.0, (0.62, 0.62), (0.35, 0.35), 'gloss', step=0.5)
        prism(m, shin, 'y', (x, 0, -0.8), 11.2, 4.0, (0.2, 0.3), (0.06, 0.1), 'sheen', step=0.5,
              bend=lambda t: (0.0, -0.25 * math.sin(t * math.pi)))                                     # すねの前の刃
        ellipsoid(m, shin, (x, 1.9, 0), (0.4, 0.4, 0.4), 'metal', step=0.2)
        prism(m, shin, 'y', (x, 0, 0), 1.6, -5.0, (0.42, 0.45), (0.03, 0.03), 'gloss', step=0.4,
              bend=lambda t: (0.0, -0.3 * t))                                                          # 下へ尖る足先
        _slice(m, shin, 'y', (x, 0, 0), 0.5, 0.5, 1.5, 1.7, 'mag')
    armor(m)
    floating_skirt(m)
    # 翅（紫の翅と、同じ形で赤く光る翅）
    for red in (False, True):
        suffix = '_red' if red else ''
        for sx, side in ((1, 'l'), (-1, 'r')):
            wing(m, 'wing_f' + side + suffix, sx, 'fore', red)
            wing(m, 'wing_h' + side + suffix, sx, 'hind', red)
    return m


if __name__ == '__main__':
    write(mementomori())
