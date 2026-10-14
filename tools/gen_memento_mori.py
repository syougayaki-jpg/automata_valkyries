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
→ 2026-10-14「第二形態を赤くなるというより出力を上げほぼ幻想的な光体となる演出に」: 第 2 形態の翅（wing_*_p2）は骨組みも光る白に近い光の翅、
  全身に重ねる光体の絵 memento_mori_lumen.png（lumen()）
→ 2026-10-14「羽を紫ぴんく水色で幻想的に彩るようにして」: 第 1 形態の翅は付け根の紫から桃色、外の水色へ波打つ帯で混ざり、モザイクの四角も 3 色、きらめく光の粒（_tri）
→ 2026-10-14「鎧のような装甲を独立した浮遊存在として着飾っていきましょう」: floating_armor()（今の装甲はそのまま、外に約 2px 離して大きく張り出す
  浮く装甲を足す。肩鎧 3 枚・胸甲・襟・背の板・上腕・前腕の刃・脛・頭の冠。骨は fl_ で始まり、紫の光の粒で体とつなぐ）

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
         > wing_fl_p2 など（同じ形で光体になった翅。第 2 形態で入れ替える）
  翅は背の後ろの縦の薄い板（厚み 0.3、XY の面）で、前後の面に翅の形の絵を描き、形の外は透けて抜く。
  膜は半透明（描画は entityTranslucent）で、黒い翅脈と縁の骨組みの間に、紫の膜と、外ほど多いデジタルのモザイクの四角（紫・桃色・淡い紫・黒）
"""
import math

import gen_chrono_gazers as g

g.TEX = 512
VIO, VIO_L, VIO_D = 0xA45CFF, 0xE8D0FF, 0x4A2088                        # 紫の光（明るい所・暗い所）
MAG = 0xFF4FD8                                                          # 桃色の光
CYAN, CYAN_L = 0x6FDCFF, 0xDDF8FF                                       # 翅の水色の光（2026-10-14）
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


def _tri(s):
    """翅の幻想的な色の流れ（2026-10-14 ユーザー指定「羽を紫ぴんく水色で幻想的に彩る」）: 0 = 紫 → 0.5 = 桃色 → 1 = 水色"""
    s = max(0.0, min(1.0, s))
    if s < 0.5:
        return _mix(VIO, MAG, _smoothstep(0.0, 1.0, s / 0.5))
    return _mix(MAG, CYAN, _smoothstep(0.0, 1.0, (s - 0.5) / 0.5))


def _paint_wing(atlas, u, v, w, h, kind, flip, radiant):
    """翅の絵。第 1 形態は紫・桃色・水色の幻想的な膜と黒い骨組み（2026-10-14 ユーザー指定「羽を紫ぴんく水色で幻想的に彩るようにして」）。
    radiant = 第 2 形態（2026-10-14 ユーザー指定「赤くなるというより出力を上げほぼ幻想的な光体となる演出に」）: 骨組みも翅脈も光になり、
    膜は白に近い明るい光で濃く、モザイクの黒い四角は光の抜けに、きらめく粒が多い"""
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
            r = math.hypot(x - root[0], (t - root[1]) * h / w)
            outer = _smoothstep(0.25, 0.95, r)
            # 色の流れ: 付け根の紫から桃色、外の水色へ。ゆるく波打つ帯で混ざる
            flow = -0.1 + 1.45 * r + 0.16 * math.sin(7.0 * x + 5.0 * t + seed) + 0.07 * math.sin(17.0 * t - 3.0 * x)
            main = _tri(flow)
            light = _mix(main, 0xFFFFFF, 0.55)
            acc = _tri(flow + 0.45 if flow < 0.55 else flow - 0.45)
            dark = _mix(main, 0x120E1A, 0.65)
            # 縁からの近さ（texel）
            edge = 9
            for k in range(1, 9):
                if not (inside(i - k, j) and inside(i + k, j) and inside(i, j - k) and inside(i, j + k)):
                    edge = k
                    break
            if edge <= 2:                                                   # 縁の骨組み（第 2 形態は白く光る縁）
                if radiant:
                    c = 0xFFFFFF if edge == 1 else _mix(main, 0xFFFFFF, 0.7)
                    _put(atlas, u + i, v + j, c, 255, c)
                else:
                    _put(atlas, u + i, v + j, frame_l if edge == 1 else frame)
                continue
            # 翅脈: 細い骨組み（付け根に近いほど太い）。第 1 形態は黒く、太い脈の芯が淡く光る。第 2 形態はすべて光る
            vein = False
            for n, (ex, et) in enumerate(ends):
                d = _seg_dist(x * w, t * h, root[0] * w, root[1] * h, ex * w, et * h)
                if d < 1.7 - 0.9 * x:
                    if radiant:
                        c = _mix(light, 0xFFFFFF, 0.5 if d < 0.6 else 0.15)
                        _put(atlas, u + i, v + j, c, 250, c)
                    else:
                        core = n % 2 == 0 and d < 0.45 and 0.15 < x < 0.7
                        _put(atlas, u + i, v + j, light if core else frame, 255, light if core else None)
                    vein = True
                    break
            if vein:
                continue
            if edge <= 4 and x > 0.2:                                       # 縁の内の光の線（ところどころ途切れる。外ほど水色）
                if _hash(i // 3, j // 3, seed + 7) > (0.05 if radiant else 0.25):
                    c = _tri(flow + 0.25)
                    if radiant:
                        c = _mix(c, 0xFFFFFF, 0.4)
                    _put(atlas, u + i, v + j, c, 240, c)
                    continue
            if _hash(i, j, seed + 11) > (0.975 if radiant else 0.992) - 0.01 * outer:   # きらめく小さな光の粒
                c = _mix(_tri(_hash(i, j, seed + 13)), 0xFFFFFF, 0.8 if radiant else 0.6)
                _put(atlas, u + i, v + j, c, 255, c)
                continue
            # デジタルのモザイク: 外ほど多い。3 色の光る四角・淡い四角と、黒い四角（第 2 形態は光の抜け）
            ci, cj = i // cs, j // cs
            hv = _hash(ci, cj, seed)
            rim = (i % cs == 0 or j % cs == 0)
            boost = 0.45 if radiant else 0.0
            if hv < 0.04 + 0.22 * outer:
                c = _mix(_tri(flow + (_hash(ci, cj, seed + 3) - 0.5) * 0.7), 0xFFFFFF, boost)
                _put(atlas, u + i, v + j, _mix(c, dark, 0.35) if rim and not radiant else c, 240 if radiant else 225, c)
                continue
            if hv < 0.06 + 0.30 * outer:
                c = _mix(acc, 0xFFFFFF, boost)
                _put(atlas, u + i, v + j, _mix(c, dark, 0.3) if rim and not radiant else c, 235 if radiant else 215, c)
                continue
            if hv < 0.07 + 0.34 * outer:
                c = CYAN_L if _hash(ci, cj, seed + 5) > 0.5 else _mix(MAG, 0xFFFFFF, 0.6)
                if radiant:
                    c = _mix(c, 0xFFFFFF, 0.5)
                _put(atlas, u + i, v + j, c, 245, c)
                continue
            if hv < 0.12 + 0.50 * outer:
                if radiant:                                                 # 光の抜け: 膜より淡く透ける光の四角
                    c = _mix(main, 0xFFFFFF, 0.25)
                    _put(atlas, u + i, v + j, c, 120, c)
                else:
                    _put(atlas, u + i, v + j, 0x120E1A if not rim else 0x1E1828, 215)
                continue
            # 膜: 半透明の光（付け根は暗く外ほど明るい。第 2 形態は白に近く濃い）。細い横の走査線
            if radiant:
                col = _mix(main, 0xFFFFFF, 0.3 + 0.25 * min(1.0, r))
                if j % 6 == 0:
                    col = _mix(col, 0xFFFFFF, 0.4)
                a = int(160 + 70 * min(1.0, r))
            else:
                col = _mix(_mix(dark, 0x120E1A, 0.5), main, 0.3 + 0.65 * min(1.0, r))
                if j % 6 == 0:
                    col = _mix(col, light, 0.25)
                a = int(95 + 85 * min(1.0, r))
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
        _, kind, flip, radiant = mat.split(':')
        painter = lambda u, v: _paint_wing(self, u, v, w, h, kind, flip == '1', radiant == '1')
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
    lumen(atlas.base).save(os.path.join(g.ASSETS, 'textures', 'entity', m.ident + '_lumen.png'))
    n = sum(len(b['cubes']) for b in m.bones)
    print(f'{m.ident}: {len(m.bones)} bones, {n} cubes, texture used to y={atlas.y + atlas.row}')


def lumen(base):
    """第 2 形態の光体の絵（client/MementoMoriRenderer が足し算の光で全身に重ねる。2026-10-14 ユーザー指定
    「赤くなるというより出力を上げほぼ幻想的な光体となる演出に」）: 同じ並びで、点ごとに色合いはそのまま明るさを最大に上げて白へ寄せる
    （黒い外装は淡い紫の光に）。足し算で描くので、透ける所は不透明度を掛けた分だけ光る"""
    out = base.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, gg, b, a = px[x, y]
            if a == 0:
                px[x, y] = (0, 0, 0, 0)
                continue
            mx = max(r, gg, b, 1)
            c = [int(v * 255 / mx) for v in (r, gg, b)]
            c = [int(v + (255 - v) * 0.45) for v in c]
            k = a / 255.0
            px[x, y] = (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)
    return out


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


def wing(m, name, sx, kind, radiant):
    """翅 1 枚: 背の後ろ（WING_Z）の縦の薄い板。前翅は付け根から外・上へ、後翅は外・下へ。前後の面に翅の絵"""
    m.bone(name, 'body', (sx * WING_X, WING_Y, WING_Z))
    length, height = (FORE_L, FORE_H) if kind == 'fore' else (HIND_L, HIND_H)
    x0, x1 = sorted((sx * WING_X, sx * (WING_X + length)))
    y0 = WING_Y - 3.0 if kind == 'fore' else WING_Y + 1.0 - height
    flip_n = NORTH_FLIP if sx > 0 else ('1' if NORTH_FLIP == '0' else '0')
    flip_s = '1' if flip_n == '0' else '0'
    r = 1 if radiant else 0
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


# ------------------------------------------------------------------ 宙に浮く装甲（2026-10-14）
# ユーザー指定「鎧のような装甲を独立した浮遊存在として着飾っていきましょう」（質問で: 今の装甲はそのまま新しく足す・手足に付いていきながら小さく揺れる・
# すき間ははっきり見えるくらい（約 2px = 0.25 ブロック）・紫の光の粒でつなぐ・肩・胸と襟と背・腕・脚と頭の冠のすべて・大きく張り出す）。
# 板は 1 枚ずつ fl_ で始まる骨（Java の animateMemento が外へ広げ・上下に浮かせ・小さく傾ける）。つなぎの光の粒は動かない親の骨に置く


def arc_plate(m, bone, axis, c, R, a0, a1, span, thick=0.32, mat='armor', sx=1, rim='vio', ridge='armor_e'):
    """中心 c のまわりの半径 R の曲がった板（細い箱を向きを変えて並べる）。
    axis 'z': 角 a は +Y から sx の側の外へ（度）。span(t) は Z の (lo, hi)（c からの差）。肩鎧
    axis 'y': 角 a は前（-Z）から +X へ（度）。span(t) は Y の (lo, hi)（そのままの高さ）。胸甲・背・腕・脚
    t は a0 → a1 で 0 → 1。縁（span の両端）に rim の光、外の面の真ん中の線に ridge の照り（axis 'z' のみ）"""
    n = max(2, int(math.ceil(abs(a1 - a0) / 7.0)))
    da = (a1 - a0) / n
    L = R * math.radians(abs(da)) * 1.08 + 0.02
    for i in range(n):
        t = (i + 0.5) / n
        am = a0 + da * (i + 0.5)
        lo, hi = span(t)
        if hi - lo < 0.08:
            continue
        if axis == 'z':
            rot = (0, 0, sx * am)
            r0, r1 = c[1] + R - thick / 2, c[1] + R + thick / 2
            m.box(bone, (c[0] - L / 2, r0, c[2] + lo), (c[0] + L / 2, r1, c[2] + hi), mat, rot=rot, pivot=c)
            if rim:
                for z0, z1 in ((lo - 0.02, lo + 0.14), (hi - 0.14, hi + 0.02)):
                    m.box(bone, (c[0] - L / 2, r0 - 0.04, c[2] + z0), (c[0] + L / 2, r1 + 0.04, c[2] + z1), rim, rot=rot, pivot=c)
            if ridge:
                zm = (lo + hi) / 2
                m.box(bone, (c[0] - L / 2, r1, c[2] + zm - 0.1), (c[0] + L / 2, r1 + 0.12, c[2] + zm + 0.1), ridge, rot=rot, pivot=c)
        else:
            rot = (0, -am, 0)
            z0, z1 = c[2] - R - thick / 2, c[2] - R + thick / 2
            m.box(bone, (c[0] - L / 2, lo, z0), (c[0] + L / 2, hi, z1), mat, rot=rot, pivot=c)
            if rim:
                for y0, y1 in ((lo - 0.02, lo + 0.14), (hi - 0.14, hi + 0.02)):
                    m.box(bone, (c[0] - L / 2, y0, z0 - 0.04), (c[0] + L / 2, y1, z1 + 0.04), rim, rot=rot, pivot=c)


def tether(m, bone, p0, p1, mat='vio', s=0.09, gap=0.45):
    """装甲と体をつなぐ紫の光の粒の列（p0 → p1。両端は少し空ける）"""
    d = math.dist(p0, p1)
    n = max(1, int(d / gap))
    for k in range(1, n):
        t = k / n
        p = [p0[i] + (p1[i] - p0[i]) * t for i in range(3)]
        sz = s * (1.25 if k % 2 else 0.85)
        m.box(bone, (p[0] - sz, p[1] - sz, p[2] - sz), (p[0] + sz, p[1] + sz, p[2] + sz), mat)


def node(m, bone, p, s=0.16, mat='mag'):
    """板の内側の光の点（つなぎの粒の着く所）"""
    m.box(bone, (p[0] - s, p[1] - s, p[2] - s), (p[0] + s, p[1] + s, p[2] + s), mat)


def _lens(t, peak, wmax, wend0, wend1):
    """t（0〜1）で幅が peak で最大 wmax、両端で wend0・wend1 へ丸く細る半幅"""
    if t < peak:
        e = (peak - t) / peak
        return wend0 + (wmax - wend0) * math.sqrt(max(0.0, 1 - e * e))
    e = (t - peak) / (1 - peak)
    return wend1 + (wmax - wend1) * math.sqrt(max(0.0, 1 - e * e))


PAULDRON_C = 4.3, 32.6              # 浮く肩鎧の弧の中心（x の大きさ, y）
PAULDRONS = (   # 半径, 角の始め・終わり（度。+Y から外へ）, 最大の半幅, 最大の所
    (4.6, -18.0, 70.0, 1.9, 0.35),
    (5.0, 35.0, 98.0, 1.6, 0.3),
    (5.4, 80.0, 124.0, 1.25, 0.3),
)
CHEST_R, BACK_R = 5.2, 4.4
COLLAR_C, COLLAR_R, COLLAR_TILT = (0.0, 34.4, 0.2), 3.0, 18.0
COLLAR_ANG = (75.0, 120.0, 165.0, 195.0, 240.0, 285.0)
CROWN_C, CROWN_R, CROWN_TILT = (0.0, 41.8, -0.2), 3.8, 14.0
CROWN = ((0.0, 4.2), (35.0, 3.0), (-35.0, 3.0), (70.0, 2.2), (-70.0, 2.2), (105.0, 1.6), (-105.0, 1.6))   # 向き（度。前から +X へ）, 高さ


def floating_armor(m):
    """宙に浮く大きな装甲（体の装甲から約 2px 離す）。肩鎧 3 枚・胸甲・襟 6 枚・背の板・上腕の板・前腕の刃の板・脛の板・頭の冠 7 本。
    骨はすべて fl_ で始まる（Java が浮かせて動かす）。襟と冠は向きの骨 fl_*_dir_<k>（Y）> 傾きの骨 fl_*_tilt_<k>（X。外へ開く）> 板 fl_*_<k>"""
    for side, sx in (('r', -1), ('l', 1)):
        arm, fore, shin = 'arm_' + side, 'forearm_' + side, 'shin_' + side
        x = sx * 3.9
        # 肩鎧: 肩の上を外へ回り込む 3 枚の曲がった板（下の板ほど大きな半径で外に重なる）。外の端は尖り、上の板に外へ反る角
        pc = (sx * PAULDRON_C[0], PAULDRON_C[1], 0.0)
        for k, (R, a0, a1, w, peak) in enumerate(PAULDRONS):
            b = m.bone('fl_pauldron_%s_%d' % (side, k), arm, pc)
            arc_plate(m, b, 'z', pc, R, a0, a1, lambda t, w=w, peak=peak: (-_lens(t, peak, w, w * 0.4, 0.08), _lens(t, peak, w, w * 0.4, 0.08)), sx=sx)
            am = math.radians((a0 + a1) / 2 - 8)
            node(m, b, (pc[0] + sx * (R - 0.35) * math.sin(am), pc[1] + (R - 0.35) * math.cos(am), 0.0))
        b0 = 'fl_pauldron_%s_0' % side
        th = math.radians(18.0)
        hx, hy = pc[0] + sx * 4.75 * math.sin(th), pc[1] + 4.75 * math.cos(th)
        prism(m, b0, 'y', (hx, 0, 0), hy - 0.3, hy + 4.0, (0.55, 0.4), (0.03, 0.03), 'armor', step=0.3,
              bend=lambda t, sx=sx: (sx * 2.0 * t * t, 0.4 * t))                                        # 外へ反る角
        prism(m, b0, 'y', (hx, 0, -0.42), hy, hy + 2.6, (0.08, 0.06), (0.02, 0.02), 'vio', step=0.3,
              bend=lambda t, sx=sx: (sx * 1.0 * t * t, 0.2 * t))                                        # 角の前の紫の線
        for a in (14.0, 50.0, 88.0):
            ar = math.radians(a)
            tether(m, arm, (pc[0] + sx * 2.55 * math.sin(ar), pc[1] + 2.55 * math.cos(ar), 0.0),
                   (pc[0] + sx * 4.25 * math.sin(ar), pc[1] + 4.25 * math.cos(ar), 0.0))
        # 上腕の外の板
        b = m.bone('fl_upperarm_' + side, arm, (x, 30.2, 0))
        arc_plate(m, b, 'y', (x, 0, 0), 2.0, sx * 45.0, sx * 135.0,
                  lambda t: (28.6 + 0.6 * abs(2 * t - 1), 31.8 - 0.8 * abs(2 * t - 1) ** 2))
        node(m, b, (x + sx * 1.7, 30.2, 0.0))
        tether(m, arm, (x + sx * 0.8, 30.2, 0.0), (x + sx * 1.75, 30.2, 0.0))
        # 前腕の刃の板: 前腕の外を包む長い板。外の真ん中ほど上下に長く、肘の上・手首の下へ尖る
        b = m.bone('fl_forearm_' + side, fore, (x, 24.0, 0))
        arc_plate(m, b, 'y', (x, 0, 0), 2.3, sx * 40.0, sx * 140.0,
                  lambda t: (18.0 + 3.5 * abs(2 * t - 1), 29.0 - 3.0 * abs(2 * t - 1) ** 1.2))
        arc_plate(m, b, 'y', (x, 0, 0), 2.62, sx * 84.0, sx * 96.0,
                  lambda t: (19.0, 28.0), thick=0.12, mat='armor_e', rim=None)                         # 外の稜
        for yy in (25.0, 22.0):
            node(m, b, (x + sx * 2.0, yy, 0.0))
            tether(m, fore, (x + sx * 0.8, yy, 0.0), (x + sx * 2.05, yy, 0.0))
        # 脛の板: 脛の前と外を包み、前は膝の上まで尖る膝当て、下も尖る
        xl = sx * 1.25

        def greave(t, sx=sx):
            ph = math.radians(sx * (-10.0 + 120.0 * t))
            f = max(0.0, math.cos(ph))
            return 4.2 - 2.4 * f ** 2, 8.6 + 4.0 * f ** 3
        b = m.bone('fl_greave_' + side, shin, (xl, 7.0, 0))
        arc_plate(m, b, 'y', (xl, 0, 0), 2.1, sx * -10.0, sx * 95.0, greave)
        for a, yy in ((20.0, 7.0), (70.0, 6.0)):
            ar = math.radians(sx * a)
            node(m, b, (xl + 1.8 * math.sin(ar), yy, -1.8 * math.cos(ar)))
            tether(m, shin, (xl + 0.85 * math.sin(ar), yy, -0.85 * math.cos(ar)), (xl + 1.85 * math.sin(ar), yy, -1.85 * math.cos(ar)))
    # 胸甲: 胸の前に浮く曲がった板。下の縁は真ん中へ尖る V、真ん中に稜と菱形の核
    b = m.bone('fl_chest', 'body', (0, 31.0, -4.6))
    cc = (0.0, 0, 0.6)
    arc_plate(m, b, 'y', cc, CHEST_R, -36.0, 36.0, lambda t: (27.6 + 1.6 * abs(2 * t - 1), 33.4 - 0.9 * abs(2 * t - 1) ** 2))
    fz = cc[2] - CHEST_R - 0.16
    m.box(b, (-0.13, 28.0, fz - 0.14), (0.13, 33.0, fz), 'armor_e')
    for d in range(4):
        hw, hh = 0.18 + d * 0.13, 0.75 - d * 0.17
        m.box(b, (-hw, 31.0 - hh, fz - 0.26), (hw, 31.0 + hh, fz - 0.14), 'mag' if d == 0 else 'vio')
    for p in ((-1.6, 31.6), (1.6, 31.6), (0.0, 29.4)):
        node(m, b, (p[0], p[1], fz + 0.45))
        tether(m, 'body', (p[0], p[1], -2.55), (p[0], p[1], fz + 0.35))
    # 背の板: 翅の付け根の間に縦に長い菱形の板
    b = m.bone('fl_back', 'body', (0, 30.0, 4.4))
    arc_plate(m, b, 'y', (0.0, 0, 0.0), BACK_R, 162.0, 198.0, lambda t: (24.0 + 4.0 * abs(2 * t - 1), 35.0 - 2.5 * abs(2 * t - 1)))
    m.box(b, (-0.12, 25.0, BACK_R + 0.16), (0.12, 34.4, BACK_R + 0.3), 'vio')
    for yy in (31.0, 27.0):
        node(m, b, (0.0, yy, BACK_R - 0.4))
        tether(m, 'body', (0.0, yy, 2.45), (0.0, yy, BACK_R - 0.3))
    # 襟: 首のまわりに立つ 6 枚の板（前は開ける）。上ほど外へ開き、上の縁が光る
    for k, a in enumerate(COLLAR_ANG):
        d = m.bone('fl_collar_dir_%d' % k, 'body', COLLAR_C, rot=(0, -a, 0))
        tl = m.bone('fl_collar_tilt_%d' % k, d, (0, COLLAR_C[1], COLLAR_C[2] - COLLAR_R), rot=(COLLAR_TILT, 0, 0))
        b = m.bone('fl_collar_%d' % k, tl, (0, COLLAR_C[1], COLLAR_C[2] - COLLAR_R))
        arc_plate(m, b, 'y', COLLAR_C, COLLAR_R, -15.0, 15.0, lambda t: (34.2 + 0.4 * abs(2 * t - 1), 36.4 + 0.5 * (1 - abs(2 * t - 1))))
    # 冠: 頭のまわりに浮く 7 本の菱形の尖塔（前ほど高い）。外へ少し開き、いちばん太い所に桃色の点
    for k, (a, h) in enumerate(CROWN):
        base = (0, CROWN_C[1], CROWN_C[2] - CROWN_R)
        d = m.bone('fl_crown_dir_%d' % k, 'head', CROWN_C, rot=(0, -a, 0))
        tl = m.bone('fl_crown_tilt_%d' % k, d, base, rot=(CROWN_TILT, 0, 0))
        b = m.bone('fl_crown_%d' % k, tl, base)
        y0, y1, ym = base[1] - 0.9, base[1] + h, base[1]
        steps = 14
        for i in range(steps):
            ya, yb = y0 + (y1 - y0) * i / steps, y0 + (y1 - y0) * (i + 1) / steps
            yc = (ya + yb) / 2
            w = 0.55 * ((yc - y0) / (ym - y0) if yc < ym else (y1 - yc) / (y1 - ym)) + 0.03
            m.box(b, (-w, ya, base[2] - 0.13), (w, yb, base[2] + 0.13), 'armor')
            m.box(b, (-0.06, ya, base[2] - 0.2), (0.06, yb, base[2] - 0.13), 'vio' if yc < ym else 'armor_e')
        node(m, b, (0.0, ym, base[2] - 0.05), s=0.2)


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
    floating_armor(m)
    # 翅（第 1 形態の翅と、同じ形で光体になった第 2 形態の翅）
    for radiant in (False, True):
        suffix = '_p2' if radiant else ''
        for sx, side in ((1, 'l'), (-1, 'r')):
            wing(m, 'wing_f' + side + suffix, sx, 'fore', radiant)
            wing(m, 'wing_h' + side + suffix, sx, 'hind', radiant)
    return m


if __name__ == '__main__':
    write(mementomori())
