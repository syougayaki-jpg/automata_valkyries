"""
戦術兵姫の外装フレーム・背部ユニットの見た目（GeckoLib geo.json）と共通のテクスチャを生成する（2026-10-06）。

    python tools/gen_valkyrie_units.py            # 生成
    python tools/gen_valkyrie_units.py preview    # 兵姫に重ねた確認用の geo とテクスチャを build/unit_preview/ に作る（GeoPreview 用）

出力（assets/automata_valkyries/ の下）:
  geo/entity/unit/frame_<型>_<部位>.geo.json    … valkyrie（＋ valkyrie_mk2〜mk4）/ fortress / mirage /
                                                 scout / maintenance / seraph / striker / aegis / tempest / helios / nemesis / luminous の head・chest・legs
  geo/entity/unit/backpack_<種類>.geo.json      …  thruster（＋ _mk2 / _mk3 / _mk4）/ folding_cannon / ecm_pod / tachyon_drive / memento_familiar / gallia_fortress /
                                                 sub_arm / recon_drone / spare_battery / repair_drone / micro_missile / grapple_anchor / prism_reflector / astral_halo / resurrection_core / accelerator / command_link
  textures/entity/unit/valkyrie_units.png（＋ _glowmask.png）  … 全ユニット共通の素材の見本帳（8x8 の板の絵を面ごとに貼る）
  textures/entity/unit/memento_butterfly.png    … メメント・ファミリアの蝶（描画側で羽ばたかせる）

座標は兵姫のモデル（geo/entity/valkyrie_type_v.geo.json）と同じ。一番上の骨の名前（head / body / right_arm / left_arm /
right_leg / left_leg）が兵姫の骨に重なる（client/ValkyrieUnitLayer）。子の骨は回した部品で、名前の頭で描き方が変わる:
  fx_*      … 光る所だけ（発光の描き方でしか描かない。噴射炎など）
  spin_z_* / spin_y_* … 時間で Z / Y 軸に回る。名前に ghost があれば残像を重ねる
顔（目の板・前髪の前）は覆わない。髪（後ろ髪 z 1.4〜3.6・横髪 x ±2.15〜4.0 の前）と面が重ならないようにし、兵姫の面と同じ平面に面を置かない（実行時に確かめて警告する）。
"""
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries')
TEX = 128

# ------------------------------------------------------------------ 色
def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)

# 板: (地, 明, 暗)
PLATE = {
    # 特殊フレーム（2026-10-06）
    's_olive': (0x4a5a48, 0x6a7c66, 0x2e3a2c),
    'n_black': (0x1c1a22, 0x34303c, 0x0e0c12),
    'n_crimson': (0xa01828, 0xd04050, 0x600c18),
    'r_red': (0xc82830, 0xf05058, 0x801820),
    'a_blue': (0x3a64b8, 0x6a90e0, 0x22407c),
    't_teal': (0x2aa8a0, 0x5ad8cc, 0x187068),
    'p_pink': (0xf0c8dc, 0xffe8f4, 0xc098b0),
    'c_crystal': (0x9ef0ff, 0xe0ffff, 0x5ab8d0),
    'v_white': (0xe8eef6, 0xffffff, 0xa0acbe),
    'v_silver': (0xb0bac8, 0xdce2ea, 0x78828f),
    'v_dark': (0x262a3a, 0x464c60, 0x161822),
    'f_steel': (0x687282, 0x8c96a4, 0x424854),
    'f_dark': (0x3a3e48, 0x545a64, 0x24262e),
    'f_orange': (0xe28424, 0xfab45a, 0xa05414),
    'm_black': (0x22202c, 0x3c3a4a, 0x14121a),
    'm_white': (0xd6dce8, 0xf6f8fc, 0x9aa2b4),
    'g_dark': (0x221e2c, 0x3a3448, 0x121018),
    'g_gold': (0xd6ac40, 0xfadc78, 0x966e1e),
    'b_white': (0xe2e8f0, 0xfafcff, 0x9ca6b6),
    'b_grey': (0x929aa8, 0xbac2ce, 0x606876),
    'b_dark': (0x3a3e4a, 0x565c6a, 0x22242c),
}
GLOWC = {'yellow': (0xffe040, 0xfff6b0), 'green': (0x6cff7a, 0xe0ffe4), 'pink': (0xff9ad8, 0xffe4f4), 'red': (0xff3048, 0xffc0c8),
         'teal': (0x40ffd8, 0xd8fff6), 'orange': (0xffa040, 0xfff0c8), 'blue': (0x5aa0ff, 0xdce8ff),
         'cyan': (0x64ecff, 0xdcffff), 'violet': (0xb86eff, 0xf0d2ff), 'gold': (0xffd66e, 0xfff4cc),
         'white': (0xb4faff, 0xffffff)}

# ------------------------------------------------------------------ 見本帳（8x8 の板。galaxy だけ 16x16）
TILES = {}   # 名前 -> (u, v, 大きさ)
_base = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 0))
_glow = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 0))
_slot = [0]


def _alloc(name, size=8):
    i = _slot[0]
    if size == 16:  # 16x16 は下の段から
        u, v = 0, 96
    else:
        u, v = (i % 16) * 8, (i // 16) * 8
        _slot[0] += 1
    TILES[name] = (u, v, size)
    return u, v


def _px(u, v, c, glow=False):
    _base.putpixel((u, v), rgb(c) + (255,) if isinstance(c, int) else tuple(c) + (255,))
    if glow:
        _glow.putpixel((u, v), rgb(c) + (255,) if isinstance(c, int) else tuple(c) + (255,))


def _bevel(u, v, key, n=8):
    base, light, dark = PLATE[key]
    rnd = random.Random(sum(map(ord, key)))  # 実行ごとに変わらないように
    for y in range(n):
        for x in range(n):
            c = rgb(base)
            if y == 0 or x == 0:
                c = rgb(light)
            elif y == n - 1 or x == n - 1:
                c = rgb(dark)
            else:  # 少しだけむら（のっぺり防止）
                d = rnd.randint(-5, 5) - (y * 2)
                c = tuple(max(0, min(255, ch + d)) for ch in c)
            _px(u + x, v + y, c)


def plate(name, key, line=None, vline=None, rivets=None, seam=False):
    u, v = _alloc(name)
    _bevel(u, v, key)
    if seam:  # 継ぎ目
        for x in range(1, 7):
            _px(u + x, v + 5, PLATE[key][2])
    if line:
        c = GLOWC[line][0]
        for x in range(1, 7):
            _px(u + x, v + 4, c, True)
    if vline:
        c = GLOWC[vline][0]
        for y in range(1, 7):
            _px(u + 4, v + y, c, True)
    if rivets:
        g = PLATE['g_gold'][1]
        for (x, y) in ((1, 1), (6, 1), (1, 6), (6, 6)):
            _px(u + x, v + y, g)


def glow_tile(name, col):
    u, v = _alloc(name)
    c, core = GLOWC[col]
    for y in range(8):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 7)
            _px(u + x, v + y, c if edge else core if 2 <= x <= 5 and 2 <= y <= 5 else c, True)


def vent(name, key):
    u, v = _alloc(name)
    _bevel(u, v, key)
    for y in (2, 4, 6):
        for x in range(1, 7):
            _px(u + x, v + y, 0x101014)


def hazard(name):
    u, v = _alloc(name)
    for y in range(8):
        for x in range(8):
            _px(u + x, v + y, PLATE['f_orange'][0] if ((x + y) // 2) % 2 == 0 else 0x24242a)


def nozzle(name, col):
    u, v = _alloc(name)
    c, core = GLOWC[col]
    for y in range(8):
        for x in range(8):
            r = math.hypot(x - 3.5, y - 3.5)
            if r < 1.6:
                _px(u + x, v + y, core, True)
            elif r < 2.7:
                _px(u + x, v + y, c, True)
            elif r < 3.4:
                _px(u + x, v + y, 0x1a1c22)
            else:
                _px(u + x, v + y, PLATE['b_dark'][2])


def lens(name):
    u, v = _alloc(name)
    for y in range(8):
        for x in range(8):
            r = math.hypot(x - 3.5, y - 3.5)
            c = PLATE['b_white'][1] if r < 1.0 else PLATE['b_grey'][2] if 2.0 < r < 2.6 else PLATE['b_grey'][1] if r < 3.6 else PLATE['b_grey'][2]
            _px(u + x, v + y, c, r < 1.0)
    _glow.putpixel((u + 3, v + 3), rgb(GLOWC['cyan'][1]) + (255,))


def galaxy(name):
    u, v = _alloc(name, 16)
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                _px(u + x, v + y, PLATE['g_gold'][0] if (x + y) % 3 else PLATE['g_gold'][1])
                continue
            # 渦の星雲（藍〜紫〜桃）
            dx, dy = x - 7.5, y - 7.5
            a = math.atan2(dy, dx)
            r = math.hypot(dx, dy)
            arm = 0.5 + 0.5 * math.cos(2 * a - r * 0.9)
            k = max(0.0, 1.0 - r / 8.0) * (0.35 + 0.65 * arm)
            c = (int(14 + 120 * k), int(10 + 40 * k), int(40 + 150 * k))
            glow = False
            if r < 1.3:
                c, glow = (250, 220, 255), True
            elif rnd.random() < 0.08:
                c, glow = (230, 240, 255), True
            _px(u + x, v + y, c, glow)
    # 星雲の芯は少しだけ光る
    for y in range(5, 11):
        for x in range(5, 11):
            p = _base.getpixel((u + x, v + y))
            if _glow.getpixel((u + x, v + y))[3] == 0:
                _glow.putpixel((u + x, v + y), (p[0] // 2, p[1] // 2, p[2] // 2, 255))


plate('v_white', 'v_white')
plate('v_white_line', 'v_white', line='cyan')
plate('v_white_vline', 'v_white', vline='cyan')
plate('v_silver', 'v_silver', seam=True)
plate('v_dark', 'v_dark')
plate('f_steel', 'f_steel', seam=True)
plate('f_steel_line', 'f_steel', line='cyan')
plate('f_dark', 'f_dark')
vent('f_vent', 'f_dark')
plate('f_orange', 'f_orange')
hazard('f_hazard')
plate('m_black', 'm_black')
plate('m_black_line', 'm_black', line='cyan')
plate('m_black_vline', 'm_black', vline='cyan')
plate('m_white', 'm_white')
plate('m_white_line', 'm_white', line='cyan')
plate('g_dark', 'g_dark', rivets=True)
plate('g_dark_line', 'g_dark', line='violet', rivets=True)
plate('g_dark_vline', 'g_dark', vline='violet', rivets=True)
plate('g_gold', 'g_gold')
plate('b_white', 'b_white')
plate('b_white_line', 'b_white', line='cyan')
plate('b_white_vline', 'b_white', vline='cyan')
plate('b_white_violet', 'b_white', vline='violet')
plate('b_grey', 'b_grey', seam=True)
plate('b_dark', 'b_dark')
plate('b_dark_line', 'b_dark', line='cyan')
plate('b_dark_vline', 'b_dark', vline='cyan')
vent('b_vent', 'b_dark')
nozzle('nozzle', 'cyan')
nozzle('nozzle_violet', 'violet')
lens('lens')
glow_tile('glow_cyan', 'cyan')
glow_tile('glow_violet', 'violet')
glow_tile('glow_gold', 'gold')
# ハイマニューバスラスター Ⅳ（ヴァルキュリア）用（2026-10-06。既存の板の位置を変えないよう後ろへ足す）
plate('b_white_gold', 'b_white', line='gold')
plate('b_white_vgold', 'b_white', vline='gold')
nozzle('nozzle_white', 'white')
glow_tile('glow_white', 'white')
# ヴァルキリーフレーム・アストライア（Mk-IV）用（2026-10-06。後ろへ足す）
plate('v_white_wline', 'v_white', line='white')
plate('v_white_wvline', 'v_white', vline='white')
plate('v_white_gvline', 'v_white', vline='gold')
# 特殊フレーム 9 種（2026-10-06。後ろへ足す）
plate('s_olive', 's_olive', seam=True)
plate('s_olive_line', 's_olive', line='green')
glow_tile('glow_green', 'green')
nozzle('nozzle_orange', 'orange')
glow_tile('glow_orange', 'orange')
plate('v_white_pink', 'v_white', vline='pink')
plate('v_white_pline', 'v_white', line='pink')
plate('p_pink', 'p_pink')
glow_tile('glow_pink', 'pink')
plate('r_red', 'r_red', seam=True)
plate('n_black', 'n_black')
plate('n_black_rline', 'n_black', line='red')
plate('n_black_rvline', 'n_black', vline='red')
plate('n_crimson', 'n_crimson')
glow_tile('glow_red', 'red')
plate('a_blue', 'a_blue', seam=True)
plate('a_blue_line', 'a_blue', line='white')
glow_tile('glow_blue', 'blue')
plate('t_teal', 't_teal')
plate('t_teal_line', 't_teal', line='white')
plate('v_white_tline', 'v_white', line='teal')
glow_tile('glow_teal', 'teal')
plate('h_line', 'g_dark', line='orange')
plate('h_vline', 'g_dark', vline='orange')


def crystal(name):
    """透けて見える結晶の板（地の色を発光にも半分の明るさで入れる）"""
    u, v = _alloc(name)
    _bevel(u, v, 'c_crystal')
    for y in range(8):
        for x in range(8):
            p = _base.getpixel((u + x, v + y))
            k = 0.75 if (x + y) % 5 == 0 or x == y else 0.45
            _glow.putpixel((u + x, v + y), (int(p[0] * k), int(p[1] * k), int(p[2] * k), 255))


crystal('crystal')
# 背部ユニット 11 種（2026-10-06。後ろへ足す）
glow_tile('glow_yellow', 'yellow')
nozzle('nozzle_red', 'red')
galaxy('galaxy')

# ------------------------------------------------------------------ 形
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
SEM = {'front': 'north', 'back': 'south', 'top': 'up', 'bottom': 'down'}
MIRROR_BONE = {'right_arm': 'left_arm', 'left_arm': 'right_arm', 'right_leg': 'left_leg', 'left_leg': 'right_leg'}
ATTACH = ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg')


_VALK_BOXES = []


def avoid_valkyrie_planes(a, c):
    """兵姫の面と同じ平面（0.015 以内）で重なる面を、箱の外側へ 0.04 ずらす（回していない骨同士だけ。coplanar_check と同じ判定）"""
    if not _VALK_BOXES:
        path = os.path.join(ASSETS, 'geo', 'entity', 'valkyrie_type_v.geo.json')
        for vb in json.load(open(path, encoding='utf-8'))['minecraft:geometry'][0]['bones']:
            if vb.get('rotation'):
                continue
            for cu in vb.get('cubes', []):
                inf = cu.get('inflate', 0)
                o, sz = cu['origin'], cu['size']
                _VALK_BOXES.append(([o[i] - inf for i in range(3)], [o[i] + sz[i] + inf for i in range(3)]))
    a, c = list(a), list(c)
    for _ in range(6):
        moved = False
        for lo, hi in _VALK_BOXES:
            for ax in range(3):
                o1, o2 = [i for i in range(3) if i != ax]
                if not (min(c[o1], hi[o1]) - max(a[o1], lo[o1]) > 0.02 and min(c[o2], hi[o2]) - max(a[o2], lo[o2]) > 0.02):
                    continue
                for ov in (lo[ax], hi[ax]):
                    if abs(a[ax] - ov) < 0.015:
                        a[ax] = round(a[ax] - 0.04, 3); moved = True
                    if abs(c[ax] - ov) < 0.015:
                        c[ax] = round(c[ax] + 0.04, 3); moved = True
        if not moved:
            break
    return a[0], a[1], a[2], c[0], c[1], c[2]


class Model:
    def __init__(self, name):
        self.name = name
        self.avoid = False  # True: 兵姫の面と同じ平面に来る面を少し外へずらす（特殊フレーム用。既存の形は変えない）
        self.bones = {}   # name -> dict
        self.order = []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rot=None):
        if parent in ATTACH and parent not in self.bones:
            self.bone(parent)
        if name not in self.bones:
            self.bones[name] = {'name': name, 'parent': parent, 'pivot': list(pivot), 'rotation': list(rot) if rot else None, 'cubes': []}
            self.order.append(name)
        return name

    def mbone(self, name, parent, pivot, rot=None):
        """左右一対の子の骨（name に {s} を入れる。r = -x 側、l = +x 側。pivot・rot は -x 側の値）"""
        r = self.bone(name.format(s='r'), parent, pivot, rot)
        mp = MIRROR_BONE.get(parent, parent)
        if mp == parent and parent in self.bones and '_r' in parent:
            mp = parent.replace('_r', '_l')
        l = self.bone(name.format(s='l'), mp, (-pivot[0], pivot[1], pivot[2]), (rot[0], -rot[1], -rot[2]) if rot else None)
        return r, l

    def box(self, bone, a, b, mat, **faces):
        """a・b は対角の 2 点（px、兵姫のモデルの座標）。faces で面ごとの素材（front/back/top/bottom/out/in か north…）"""
        if bone in ATTACH:
            self.bone(bone)
        x0, x1 = sorted((a[0], b[0]))
        y0, y1 = sorted((a[1], b[1]))
        z0, z1 = sorted((a[2], b[2]))
        b_ = self.bones[bone]
        if self.avoid and not b_['rotation'] and not (b_['parent'] and self.bones.get(b_['parent'], {}).get('rotation')):
            x0, y0, z0, x1, y1, z1 = avoid_valkyrie_planes((x0, y0, z0), (x1, y1, z1))
        cx = (x0 + x1) / 2
        fm = {f: mat for f in FACES}
        for k, m in faces.items():
            if k == 'out':
                k = 'east' if cx < 0 else 'west'   # GeckoLib は X を反転するので、-x 側の外の面が east
            elif k == 'in':
                k = 'west' if cx < 0 else 'east'
            elif k == 'sides':
                fm['east'] = fm['west'] = m
                continue
            fm[SEM.get(k, k)] = m
        self.bones[bone]['cubes'].append(((x0, y0, z0), (x1, y1, z1), fm))

    def mbox(self, bone, a, b, mat, **faces):
        """左右対称の 2 つ（bone の左右の骨か、同じ骨の反対側）"""
        self.box(bone, a, b, mat, **faces)
        mb = MIRROR_BONE.get(bone, bone)
        if mb == bone and bone.endswith('_r'):
            mb = bone[:-2] + '_l'
        self.box(mb, (-a[0], a[1], a[2]), (-b[0], b[1], b[2]), mat, **faces)


MODELS = []


def model(name):
    m = Model(name)
    MODELS.append(m)
    return m


# ---------------- 外装フレーム: ヴァルキリー（白銀の鋭い装甲＋ヘッドセット）
def frame_valkyrie():
    h = model('frame_valkyrie_head')
    h.box('head', (-4.15, 27.08, -0.45), (4.15, 27.46, 0.55), 'v_white')               # 頭の上を渡る輪
    h.box('head', (-1.1, 27.46, -0.25), (1.1, 27.66, 0.35), 'v_white_line', top='glow_cyan')
    h.mbox('head', (-4.17, 24.85, -0.45), (-3.82, 27.46, 0.55), 'v_silver')            # 横の柱
    h.mbox('head', (-4.6, 22.42, -1.18), (-3.97, 24.88, 1.37), 'v_white', out='v_white_line')  # 耳当て（耳のセンサーの外）
    h.mbox('head', (-4.72, 23.08, -0.42), (-4.6, 24.22, 0.62), 'glow_cyan')
    # 後ろへ伸びる刃の飾り（斜め）
    fr, fl = h.mbone('fin_{s}', 'head', (-4.3, 24.3, 1.0), (28, 0, 0))
    h.box(fr, (-4.45, 24.05, 0.9), (-4.08, 24.62, 4.3), 'v_white', out='v_white_line')
    h.box(fl, (4.08, 24.05, 0.9), (4.45, 24.62, 4.3), 'v_white', out='v_white_line')
    h.box(fr, (-4.38, 24.62, 1.6), (-4.15, 25.0, 3.6), 'v_silver')
    h.box(fl, (4.15, 24.62, 1.6), (4.38, 25.0, 3.6), 'v_silver')
    # 片側（+x）のマイク
    h.box('head', (3.86, 21.92, -2.85), (4.08, 22.2, -1.1), 'v_silver')
    h.box('head', (3.8, 21.82, -3.08), (4.14, 22.3, -2.85), 'glow_cyan')

    c = model('frame_valkyrie_chest')
    c.mbox('body', (1.31, 17.95, -2.27), (2.47, 20.43, -1.29), 'v_white', front='v_white_vline')  # コアの左右の胸当て
    c.box('body', (-2.47, 20.48, -1.62), (2.47, 20.92, 1.37), 'v_silver')                # 首元の板
    c.box('body', (-1.5, 14.62, -1.42), (1.5, 16.18, -1.17), 'v_silver', front='v_white_line')  # 腹の板
    c.mbox('body', (-3.27, 11.28, -1.36), (-2.62, 14.02, 1.22), 'v_white', out='v_white_vline', front='v_white_line')  # 腰の装甲
    c.mbox('right_arm', (-4.76, 19.92, -1.28), (-2.38, 21.06, 1.33), 'v_white', out='v_white_line')  # 肩の装甲
    c.mbox('right_arm', (-4.97, 18.58, -1.22), (-3.42, 20.12, 1.24), 'v_silver')
    sr, sl = c.mbone('spike_{s}', 'right_arm', (-4.9, 20.4, 0.0), (0, 0, 22))           # 肩の鋭い張り出し
    c.box(sr, (-5.75, 20.15, -0.3), (-4.6, 20.68, 0.42), 'v_white', top='v_white_line')
    c.box(sl, (4.6, 20.15, -0.3), (5.75, 20.68, 0.42), 'v_white', top='v_white_line')
    c.mbox('right_arm', (-4.37, 13.62, -1.12), (-2.13, 15.62, 1.12), 'v_white', out='v_white_line')  # 前腕の装甲

    l = model('frame_valkyrie_legs')
    l.mbox('right_leg', (-2.97, 7.62, -1.18), (-2.57, 11.78, 1.08), 'v_white', out='v_white_vline')  # 太ももの横
    l.mbox('right_leg', (-2.38, 8.02, -1.66), (-0.22, 11.38, -1.43), 'v_silver')        # 太ももの前
    l.mbox('right_leg', (-2.47, 5.28, -2.02), (-0.13, 7.32, -1.18), 'v_white', front='v_white_vline')  # 膝当て
    kr, kl = l.mbone('knee_{s}', 'right_leg', (-1.3, 7.3, -1.9), (-35, 0, 0))            # 膝の鋭い張り出し
    l.box(kr, (-1.85, 7.0, -2.25), (-0.75, 8.6, -1.7), 'v_white')
    l.box(kl, (0.75, 7.0, -2.25), (1.85, 8.6, -1.7), 'v_white')
    l.mbox('right_leg', (-2.72, 1.18, -1.67), (0.12, 5.26, -1.37), 'v_white', front='v_white_vline')  # 脛当て
    l.mbox('right_leg', (-2.83, 1.38, -1.36), (-2.62, 4.98, 1.22), 'v_silver')
    l.mbox('right_leg', (-2.97, 0.88, 0.02), (-2.68, 2.42, 1.62), 'v_white', out='v_white_line')  # 足首のひれ


# ---------------- 外装フレーム: ヴァルキリー Mk-II〜Ⅳ（アストライア）。Mk-I と同じ形に、ティアごとに装甲・光の線・飾りを足す
VALK_MATS = {
    2: {'w': 'v_white', 's': 'v_silver', 'l': 'v_white_line', 'vl': 'v_white_vline', 'g': 'glow_cyan', 'acc': 'v_silver'},
    3: {'w': 'v_white', 's': 'v_silver', 'l': 'v_white_line', 'vl': 'v_white_vline', 'g': 'glow_cyan', 'acc': 'v_silver'},
    4: {'w': 'v_white', 's': 'v_silver', 'l': 'v_white_wline', 'vl': 'v_white_wvline', 'g': 'glow_white', 'acc': 'g_gold'},
}


def _valk_base(h, c, l, M):
    """Mk-I（frame_valkyrie）と同じ形。素材だけ差し替える"""
    w, sv, ln, vl, g = M['w'], M['s'], M['l'], M['vl'], M['g']
    h.box('head', (-4.15, 27.08, -0.45), (4.15, 27.46, 0.55), w)
    h.box('head', (-1.1, 27.46, -0.25), (1.1, 27.66, 0.35), ln, top=g)
    h.mbox('head', (-4.17, 24.85, -0.45), (-3.82, 27.46, 0.55), sv)
    h.mbox('head', (-4.6, 22.42, -1.18), (-3.97, 24.88, 1.37), w, out=ln)
    h.mbox('head', (-4.72, 23.08, -0.42), (-4.6, 24.22, 0.62), g)
    fr, fl = h.mbone('fin_{s}', 'head', (-4.3, 24.3, 1.0), (28, 0, 0))
    h.box(fr, (-4.45, 24.05, 0.9), (-4.08, 24.62, 4.3), w, out=ln)
    h.box(fl, (4.08, 24.05, 0.9), (4.45, 24.62, 4.3), w, out=ln)
    h.box(fr, (-4.38, 24.62, 1.6), (-4.15, 25.0, 3.6), sv)
    h.box(fl, (4.15, 24.62, 1.6), (4.38, 25.0, 3.6), sv)
    h.box('head', (3.86, 21.92, -2.85), (4.08, 22.2, -1.1), sv)
    h.box('head', (3.8, 21.82, -3.08), (4.14, 22.3, -2.85), g)

    c.mbox('body', (1.31, 17.95, -2.27), (2.47, 20.43, -1.29), w, front=vl)
    c.box('body', (-2.47, 20.48, -1.62), (2.47, 20.92, 1.37), sv)
    c.box('body', (-1.5, 14.62, -1.42), (1.5, 16.18, -1.17), sv, front=ln)
    c.mbox('body', (-3.27, 11.28, -1.36), (-2.62, 14.02, 1.22), w, out=vl, front=ln)
    c.mbox('right_arm', (-4.76, 19.92, -1.28), (-2.38, 21.06, 1.33), w, out=ln)
    c.mbox('right_arm', (-4.97, 18.58, -1.22), (-3.42, 20.12, 1.24), sv)
    sr, sl = c.mbone('spike_{s}', 'right_arm', (-4.9, 20.4, 0.0), (0, 0, 22))
    c.box(sr, (-5.75, 20.15, -0.3), (-4.6, 20.68, 0.42), w, top=ln)
    c.box(sl, (4.6, 20.15, -0.3), (5.75, 20.68, 0.42), w, top=ln)
    c.mbox('right_arm', (-4.37, 13.62, -1.12), (-2.13, 15.62, 1.12), w, out=ln)

    l.mbox('right_leg', (-2.97, 7.62, -1.18), (-2.57, 11.78, 1.08), w, out=vl)
    l.mbox('right_leg', (-2.38, 8.02, -1.66), (-0.22, 11.38, -1.43), sv)
    l.mbox('right_leg', (-2.47, 5.28, -2.02), (-0.13, 7.32, -1.18), w, front=vl)
    kr, kl = l.mbone('knee_{s}', 'right_leg', (-1.3, 7.3, -1.9), (-35, 0, 0))
    l.box(kr, (-1.85, 7.0, -2.25), (-0.75, 8.6, -1.7), w)
    l.box(kl, (0.75, 7.0, -2.25), (1.85, 8.6, -1.7), w)
    l.mbox('right_leg', (-2.72, 1.18, -1.67), (0.12, 5.26, -1.37), w, front=vl)
    l.mbox('right_leg', (-2.83, 1.38, -1.36), (-2.62, 4.98, 1.22), sv)
    l.mbox('right_leg', (-2.97, 0.88, 0.02), (-2.68, 2.42, 1.62), w, out=ln)


def frame_valkyrie_tier(tier):
    key = 'valkyrie_mk%d' % tier
    M = VALK_MATS[tier]
    w, ln, vl, g, acc = M['w'], M['l'], M['vl'], M['g'], M['acc']
    h = model(f'frame_{key}_head')
    c = model(f'frame_{key}_chest')
    l = model(f'frame_{key}_legs')
    _valk_base(h, c, l, M)

    # ---- Mk-II 以上: 装甲を一枚ずつ足し、光の線を増やす
    h.mbox('head', (-4.66, 21.95, -0.95), (-4.0, 22.5, 1.15), M['s'])                  # 耳当ての下の板
    h.mbox('head', (-4.7, 22.1, -0.6), (-4.66, 22.3, 0.8), g)
    h.box('head', (-2.6, 27.15, -0.53), (2.6, 27.35, -0.47), g)                         # 輪の前の光の線
    c.box('body', (-2.0, 17.6, -2.33), (2.0, 17.98, -1.62), w, front=ln)               # 胸当ての下の帯
    c.mbox('right_arm', (-4.8, 20.3, -1.33), (-2.66, 20.45, -1.28), g)                   # 肩の前の光の線
    c.mbox('right_arm', (-4.62, 16.4, -0.9), (-4.42, 18.4, 0.9), w, out=vl)            # 上腕の板
    c.mbox('right_arm', (-4.42, 14.5, -0.5), (-4.37, 14.75, 0.5), g)                    # 前腕の光の輪
    l.mbox('right_leg', (-1.35, 1.5, -1.72), (-1.05, 4.9, -1.67), g)                    # 脛の光の線
    l.mbox('right_leg', (-3.02, 8.5, -0.2), (-2.97, 10.9, 0.1), g)                      # 太ももの横の光の線
    l.mbox('right_leg', (-2.72, 0.3, -2.4), (0.12, 0.95, -1.9), acc)                    # つま先の覆い

    if tier >= 3:
        # ---- Mk-III 以上: ヘッドセットの飾り・アンテナ、肩の小さなひれ、膝当て
        h.box('head', (-0.3, 27.62, -0.2), (0.3, 28.75, 0.3), acc)                         # 頭の上の飾り
        h.box('head', (-0.22, 28.75, -0.12), (0.22, 29.1, 0.22), g)
        ar, al = h.mbone('antenna_{s}', 'head', (-4.55, 24.95, 0.5), (-25, 0, 0))          # 耳当ての上のアンテナ（後ろへ傾ける）
        h.box(ar, (-4.62, 24.95, 0.42), (-4.48, 27.9, 0.58), acc)
        h.box(al, (4.48, 24.95, 0.42), (4.62, 27.9, 0.58), acc)
        h.box(ar, (-4.65, 27.9, 0.39), (-4.45, 28.25, 0.61), g)
        h.box(al, (4.45, 27.9, 0.39), (4.65, 28.25, 0.61), g)
        fr, fl = c.mbone('sfin_{s}', 'right_arm', (-4.7, 20.6, 0.9), (0, 30, 12))         # 肩の後ろの小さなひれ（外・後ろへ）
        c.box(fr, (-4.7, 20.38, 0.78), (-6.3, 20.82, 1.02), w, top=ln)
        c.box(fl, (4.7, 20.38, 0.78), (6.3, 20.82, 1.02), w, top=ln)
        c.box(fr, (-5.4, 20.44, 1.02), (-6.2, 20.76, 1.08), g)
        c.box(fl, (5.4, 20.44, 1.02), (6.2, 20.76, 1.08), g)
        l.mbox('right_leg', (-2.68, 5.0, -2.2), (0.08, 5.62, -1.25), acc)                    # 膝当ての下の縁
        l.mbox('right_leg', (-1.45, 5.7, -2.07), (-1.15, 6.9, -2.02), g)                   # 膝の光の線

    if tier >= 4:
        # ---- Ⅳ（アストライア）: 金の冠、重ねた肩当て、腰の前垂れ、白シアンの光と金の飾り
        h.box('head', (-4.22, 27.1, -0.51), (4.22, 27.4, -0.43), 'g_gold')               # 輪の前の金の縁（光の線の下）
        h.mbox('head', (-1.95, 27.4, -0.18), (-1.45, 28.3, 0.32), 'g_gold')               # 冠の左右の尖り
        h.mbox('head', (-1.85, 28.3, -0.1), (-1.55, 28.55, 0.24), 'glow_gold')
        h.mbox('head', (-4.68, 24.8, -1.0), (-3.99, 25.05, 1.2), 'g_gold')               # 耳当ての上の金の縁
        c.mbox('right_arm', (-4.95, 21.0, -1.38), (-2.55, 21.35, 1.43), 'g_gold')          # 重ねた肩当て（上）
        c.mbox('right_arm', (-5.12, 18.3, -1.3), (-3.6, 18.75, 1.32), 'g_gold')            # 重ねた肩当て（下の縁）
        c.mbox('right_arm', (-5.25, 18.7, -1.05), (-4.95, 20.0, 1.08), w, out='v_white_gvline')  # 肩当ての外の板
        c.box('body', (-0.2, 14.35, -1.47), (0.2, 16.45, -1.42), 'glow_white')            # 腹の光の線
        c.mbox('body', (-2.45, 10.2, -1.78), (-0.3, 12.4, -1.55), w, front='v_white_gvline', bottom='g_gold')  # 腰の前垂れ
        c.mbox('body', (-2.5, 12.35, -1.8), (-0.25, 12.7, -1.5), 'g_gold')
        l.mbox('right_leg', (-2.5, 7.28, -1.95), (-0.1, 7.5, -1.2), 'g_gold')             # 膝当ての上の金の縁
        l.mbox('right_leg', (-3.0, 0.95, 0.4), (-2.97, 2.1, 1.3), 'glow_white')           # 足首のひれの光


# ---------------- 外装フレーム: フォートレス（巨大な肩部装甲＋重厚な外骨格）
def frame_fortress():
    h = model('frame_fortress_head')
    h.box('head', (-3.36, 26.92, -2.62), (3.36, 27.82, 2.92), 'f_steel', top='f_vent')   # 頭の上の装甲
    h.box('head', (-2.92, 26.48, -3.22), (2.92, 27.12, -2.72), 'f_steel', front='f_steel_line')  # 額の庇（目より上）
    h.mbox('head', (-4.18, 25.38, -1.02), (-3.79, 27.62, 1.62), 'f_dark')                # 横の柱
    h.mbox('head', (-4.77, 21.82, -1.82), (-3.86, 25.62, 2.18), 'f_steel', out='f_steel_line', front='f_hazard')  # 耳の厚い装甲
    h.mbox('head', (-4.97, 22.6, -0.6), (-4.77, 24.6, 1.0), 'f_vent')
    h.box('head', (1.02, 27.82, 0.48), (1.62, 29.02, 1.18), 'f_dark')                    # アンテナ
    h.box('head', (1.07, 29.02, 0.53), (1.57, 29.42, 1.13), 'glow_cyan')

    c = model('frame_fortress_chest')
    c.mbox('body', (1.31, 18.32, -2.47), (2.77, 20.62, -1.29), 'f_steel', front='f_steel_line')  # コアの左右
    c.box('body', (-2.77, 15.92, -2.47), (2.77, 18.32, -1.13), 'f_steel', front='f_steel_line', top='f_orange')  # 胸の下の厚い板
    c.box('body', (-2.88, 20.42, -1.82), (2.88, 21.22, 1.38), 'f_dark')  # 首の輪
    c.mbox('body', (-2.82, 13.98, -1.02), (-2.38, 17.02, 1.02), 'f_dark')                # 脇の骨組み
    c.mbox('body', (-3.47, 10.78, -1.62), (-2.57, 14.22, 1.38), 'f_steel', out='f_vent')  # 腰の厚い装甲
    c.mbox('body', (-2.32, 10.58, -1.77), (-0.28, 13.62, -1.43), 'f_steel', front='f_steel_line')  # 前の草摺
    # 巨大な肩
    c.mbox('right_arm', (-6.18, 18.42, -1.28), (-2.58, 21.62, 1.34), 'f_steel', out='f_hazard', top='f_steel_line')
    c.mbox('right_arm', (-5.42, 21.62, -1.12), (-2.82, 22.12, 1.02), 'f_orange')
    c.mbox('right_arm', (-6.42, 17.62, -1.22), (-4.62, 18.62, 1.22), 'f_dark')
    c.mbox('right_arm', (-6.32, 19.2, -0.5), (-6.18, 20.8, 0.7), 'glow_cyan')
    c.mbox('right_arm', (-4.42, 15.68, -1.02), (-2.12, 16.62, 1.32), 'f_dark')           # 肘
    c.mbox('right_arm', (-3.52, 16.62, 0.97), (-3.02, 18.42, 1.32), 'f_vent')            # 上腕のシリンダー
    c.mbox('right_arm', (-4.62, 11.42, -1.32), (-1.88, 15.68, 1.32), 'f_steel', out='f_steel_line', top='f_orange')  # 籠手

    l = model('frame_fortress_legs')
    l.mbox('right_leg', (-2.97, 0.78, -1.92), (0.37, 5.62, 1.62), 'f_steel', front='f_steel_line', out='f_vent')  # 脛の厚い装甲
    l.mbox('right_leg', (-2.77, 5.38, -2.22), (0.17, 7.42, -1.02), 'f_orange', front='f_hazard')  # 膝
    l.mbox('right_leg', (-2.62, 7.62, -1.77), (0.02, 11.82, -1.42), 'f_steel')           # 太ももの前
    l.mbox('right_leg', (-3.12, 7.38, -1.32), (-2.62, 12.02, 1.32), 'f_dark', out='f_steel_line')  # 太ももの横
    l.mbox('right_leg', (-1.62, 7.42, 1.32), (-0.92, 11.42, 1.72), 'f_vent')             # 太ももの後ろのシリンダー
    l.mbox('right_leg', (-2.97, -0.05, -2.62), (0.37, 0.78, 1.62), 'f_dark', top='f_steel')  # 足の覆い


# ---------------- 外装フレーム: ミラージュ（軽いサイバー猫耳＋発光ラインスーツ）
def frame_mirage():
    h = model('frame_mirage_head')
    h.box('head', (-3.22, 26.42, -0.37), (3.22, 27.17, 0.33), 'm_black', top='m_black_line')  # カチューシャ
    h.mbox('head', (-4.02, 24.82, -0.32), (-3.8, 26.92, 0.32), 'm_black', out='m_black_vline')
    er, el = h.mbone('ear_{s}', 'head', (-2.0, 27.0, 0.0), (0, 0, 14))                  # 猫耳（少し外へ開く）
    for b, s in ((er, -1), (el, 1)):
        def X(a0, a1):
            return (s * a0, s * a1)
        x = X(1.18, 2.82); h.box(b, (x[0], 26.95, -0.62), (x[1], 27.72, 0.62), 'm_black')
        x = X(1.42, 2.62); h.box(b, (x[0], 27.72, -0.47), (x[1], 28.42, 0.47), 'm_black')
        x = X(1.82, 2.42); h.box(b, (x[0], 28.42, -0.32), (x[1], 29.12, 0.32), 'm_white')
        x = X(1.52, 2.42); h.box(b, (x[0], 27.22, -0.69), (x[1], 28.32, -0.62), 'glow_cyan')

    c = model('frame_mirage_chest')
    c.box('body', (-2.42, 16.02, -1.57), (2.42, 16.42, 1.37), 'm_black', front='m_black_line')  # 胸の下の帯
    c.mbox('body', (-2.17, 14.42, -0.16), (-1.97, 16.02, 0.16), 'glow_cyan')             # 脇の光の線
    c.box('body', (-0.12, 14.42, -1.32), (0.12, 16.02, -1.17), 'glow_cyan')              # 腹の光の線
    c.mbox('body', (-2.77, 10.48, -0.92), (-2.58, 13.62, 1.02), 'm_white', out='m_white_line')  # 腰の軽いひれ
    c.mbox('right_arm', (-4.57, 20.02, -1.27), (-2.62, 20.82, 1.27), 'm_white', out='m_white_line')  # 肩の軽い板
    c.mbox('right_arm', (-4.34, 16.42, -0.16), (-4.12, 19.42, 0.16), 'glow_cyan')        # 腕の光の線
    c.mbox('right_arm', (-4.29, 13.62, -0.16), (-4.1, 14.58, 0.16), 'glow_cyan')
    c.mbox('right_arm', (-4.32, 14.62, -1.02), (-2.18, 15.42, 1.02), 'm_black', out='m_black_line', front='m_black_line')  # 腕輪

    l = model('frame_mirage_legs')
    l.mbox('right_leg', (-1.45, 7.02, -1.46), (-1.15, 11.82, -1.32), 'glow_cyan')        # 太ももの前の光の線
    l.mbox('right_leg', (-2.69, 6.62, -0.16), (-2.57, 9.42, 0.16), 'glow_cyan')          # 太ももの横
    l.mbox('right_leg', (-1.45, 1.12, -1.56), (-1.15, 5.32, -1.42), 'glow_cyan')         # 脛の前
    l.mbox('right_leg', (-2.86, 7.98, -0.92), (-2.62, 11.52, 0.92), 'm_white', out='m_white_line')  # 太ももの軽いひれ
    l.mbox('right_leg', (-2.74, 2.42, -1.56), (0.14, 2.72, 1.56), 'm_black', front='m_black_line')  # 足首の輪


# ---------------- 外装フレーム: 特殊 9 種（2026-10-06。役割で見た目を決める。特定のキャラクターは写さない）
# 型ごとに head / chest / legs の 3 つ。Fortress より細身（Aegis だけ少し重め）。腰の後ろは後ろ髪（z 1.4 より後ろ）に入らないよう z 1.38 まで
def _parts(key):
    ms = model(f'frame_{key}_head'), model(f'frame_{key}_chest'), model(f'frame_{key}_legs')
    for m in ms:
        m.avoid = True
    return ms


def frame_scout():
    """スカウト（T1）: 軽い偵察。額の跳ね上げバイザー・片側のアンテナ・最小限の板・緑のセンサー灯"""
    h, c, l = _parts('scout')
    h.box('head', (-3.22, 26.42, -0.37), (3.22, 27.17, 0.33), 's_olive', top='s_olive_line')       # 頭の上の帯
    h.mbox('head', (-4.02, 24.82, -0.32), (-3.8, 26.92, 0.32), 'v_dark')
    h.mbox('head', (-4.5, 22.6, -0.9), (-3.97, 24.6, 1.0), 's_olive', out='s_olive_line')          # 耳のセンサー
    h.mbox('head', (-4.6, 23.2, -0.3), (-4.5, 24.0, 0.4), 'glow_green')
    h.box('head', (-2.9, 26.5, -3.4), (2.9, 26.95, -2.9), 's_olive', front='glow_green')            # 額の跳ね上げバイザー
    h.box('head', (4.1, 24.6, 0.6), (4.3, 29.2, 0.8), 'v_dark')                                    # +x 側のアンテナ
    h.box('head', (4.07, 29.2, 0.57), (4.33, 29.5, 0.83), 'glow_green')
    c.mbox('body', (1.0, 17.9, -2.2), (2.4, 19.9, -1.4), 'v_dark', front='s_olive_line')           # 軽い胸当て
    c.box('body', (-3.05, 12.95, -1.62), (3.05, 13.45, 1.36), 'v_dark', front='s_olive_line')      # ベルト
    c.mbox('body', (-2.0, 12.2, -1.85), (-1.2, 13.3, -1.6), 's_olive')                            # 小物入れ
    c.mbox('right_arm', (-4.55, 20.0, -1.0), (-2.6, 20.85, 1.05), 's_olive', out='s_olive_line')   # 小さな肩当て
    c.mbox('right_arm', (-4.6, 20.2, -0.3), (-4.55, 20.6, 0.3), 'glow_green')
    c.mbox('right_arm', (-4.35, 13.8, -1.05), (-2.2, 15.0, 1.05), 's_olive', out='s_olive_line')   # 前腕
    l.mbox('right_leg', (-2.45, 5.3, -2.0), (-0.15, 6.9, -1.2), 's_olive', front='s_olive_line')   # 膝当て
    l.mbox('right_leg', (-3.05, 8.2, -0.9), (-2.6, 10.8, 0.7), 's_olive', out='s_olive_line')      # 太もものホルスター
    l.mbox('right_leg', (-2.8, 2.0, -0.6), (-2.62, 3.6, 0.6), 'v_dark')                           # 足首の灯
    l.mbox('right_leg', (-2.86, 2.6, -0.2), (-2.8, 3.0, 0.2), 'glow_green')


def frame_maintenance():
    """メンテナンス（T1）: 作業・技術者。ゴーグルのバイザー、耳当て、右肩の工具アーム（はさみ）、工具ベルトと腰の袋、橙と灰"""
    h, c, l = _parts('maintenance')
    h.box('head', (-3.0, 26.95, -2.2), (3.0, 27.55, 2.5), 'f_orange', top='f_vent')                # 作業帽
    h.box('head', (-2.6, 26.3, -3.45), (2.6, 26.9, -2.95), 'f_dark', front='glow_orange')         # 額のゴーグル
    h.mbox('head', (-4.5, 22.3, -1.2), (-3.9, 24.6, 1.2), 'f_steel', out='f_hazard')              # 耳当て
    h.box('head', (3.9, 25.0, -1.5), (4.6, 25.8, -0.4), 'f_dark', front='glow_orange')            # +x 側のライト
    c.box('body', (0.4, 16.6, -2.3), (1.9, 18.0, -1.9), 'f_orange')                               # 胸の物入れ
    c.mbox('body', (-1.6, 15.2, -1.55), (-1.2, 20.6, -1.4), 'f_dark')                             # 吊り帯
    c.box('body', (-3.0, 12.6, -1.65), (3.0, 13.35, 1.36), 'f_dark', front='f_hazard')            # 工具ベルト
    c.mbox('body', (-3.4, 11.4, -1.3), (-3.0, 13.0, 0.6), 'f_orange', out='f_vent')               # 腰の袋
    c.mbox('body', (-2.3, 11.6, -1.95), (-1.3, 12.9, -1.6), 'f_orange', top='f_steel')
    c.mbox('right_arm', (-4.6, 19.8, -1.1), (-2.5, 20.95, 1.2), 'f_steel', out='f_hazard')         # 肩当て
    c.mbox('right_arm', (-4.4, 13.4, -1.1), (-2.1, 15.2, 1.1), 'f_steel', out='f_orange')          # 前腕
    arm = c.bone('tool_arm', 'right_arm', (-4.0, 21.0, 0.8), (-25, 0, -25))                        # 右肩の工具アーム（外・後ろへ）
    c.box(arm, (-4.25, 20.9, 0.6), (-3.85, 24.0, 1.0), 'f_dark')
    c.box(arm, (-4.6, 24.0, 0.3), (-3.5, 24.5, 1.3), 'f_orange')
    c.box(arm, (-4.55, 24.5, 0.35), (-4.3, 25.3, 0.6), 'f_steel')                                 # はさみ
    c.box(arm, (-3.75, 24.5, 1.0), (-3.55, 25.3, 1.25), 'f_steel')
    l.mbox('right_leg', (-2.5, 5.2, -2.05), (-0.1, 7.0, -1.2), 'f_orange', front='f_hazard')       # 膝当て
    l.mbox('right_leg', (-2.85, -0.03, -2.45), (0.25, 1.6, 1.45), 'f_dark', top='f_steel')         # 作業靴
    l.mbox('right_leg', (-3.05, 8.0, -0.8), (-2.6, 10.4, 0.8), 'f_orange', out='f_vent')           # 太ももの物入れ


def frame_seraph():
    """セラフ（T2）: 救護。白に淡い桃と白の光、頭の上の小さな輪、肩の後ろの小さな羽の板"""
    h, c, l = _parts('seraph')
    halo = h.bone('halo', 'head', (0, 28.4, 0.6), (-15, 0, 0))                                     # 頭の上の輪（8 つに分けて回す）
    for k in range(8):
        seg = h.bone(f'halo_{k}', halo, (0, 28.4, 0.6), (0, k * 45, 0))
        h.box(seg, (-0.95, 28.3, 2.85), (0.95, 28.55, 3.1), 'glow_pink')
    h.mbox('head', (-4.35, 23.0, -0.6), (-3.85, 25.2, 0.9), 'v_white', out='v_white_pink')        # 耳の飾り
    fr, fl = h.mbone('efin_{s}', 'head', (-4.2, 24.6, 0.6), (35, 0, 0))                           # 耳の小さな羽
    h.box(fr, (-4.3, 24.4, 0.6), (-4.1, 24.8, 3.0), 'v_white', out='v_white_pline')
    h.box(fl, (4.1, 24.4, 0.6), (4.3, 24.8, 3.0), 'v_white', out='v_white_pline')
    c.mbox('body', (1.2, 17.9, -2.25), (2.4, 20.3, -1.3), 'v_white', front='v_white_pink')         # 胸当て
    c.box('body', (-0.5, 18.8, -2.0), (0.5, 19.6, -1.92), 'glow_pink')                            # 胸の桃の光
    c.mbox('body', (-3.2, 11.3, -1.3), (-2.65, 13.9, 1.2), 'v_white', out='v_white_pink')         # 腰の帯
    c.box('body', (-1.4, 11.4, -1.62), (1.4, 13.6, -1.45), 'p_pink', front='v_white_pline')       # 前の柔らかい垂れ
    c.mbox('right_arm', (-4.6, 19.9, -1.2), (-2.45, 20.9, 1.25), 'v_white', out='v_white_pline')   # 丸い肩当て
    c.mbox('right_arm', (-4.3, 13.7, -1.05), (-2.2, 15.3, 1.05), 'v_white', out='v_white_pline')   # 前腕
    for key, piv, rot, ln in (('a', (-4.3, 20.4, 1.0), (15, -25, 0), 2.4), ('b', (-4.3, 19.3, 1.0), (0, -38, 0), 1.7)):
        fr, fl = c.mbone('feather_' + key + '_{s}', 'right_arm', piv, rot)                         # 肩の後ろの羽の板（外・後ろへ）
        y = piv[1]
        c.box(fr, (-4.5, y - 0.7, 1.0), (-4.25, y + 0.5, 1.0 + ln), 'v_white', out='v_white_pink')
        c.box(fl, (4.25, y - 0.7, 1.0), (4.5, y + 0.5, 1.0 + ln), 'v_white', out='v_white_pink')
    l.mbox('right_leg', (-2.95, 7.8, -1.1), (-2.6, 11.6, 1.0), 'v_white', out='v_white_pline')     # 太ももの横
    l.mbox('right_leg', (-2.42, 5.4, -1.95), (-0.18, 7.1, -1.2), 'v_white', front='v_white_pink')  # 膝
    l.mbox('right_leg', (-2.7, 1.2, -1.62), (0.1, 5.1, -1.45), 'v_white', front='v_white_pline')   # 脛
    l.mbox('right_leg', (-2.95, 0.85, 0.1), (-2.68, 2.6, 1.55), 'v_white', out='v_white_pink')     # 足首の羽


def frame_striker():
    """ストライカー（T2）: 突撃。赤と黒、角張った前腕の守り、膝の棘、前へ反った鶏冠"""
    h, c, l = _parts('striker')
    h.box('head', (-3.5, 26.95, -1.2), (3.5, 27.5, 1.6), 'n_black', top='n_black_rline')           # 兜の帯
    cr = h.bone('crest', 'head', (0, 27.2, 0.5), (35, 0, 0))                                         # 前へ反った鶏冠
    h.box(cr, (-0.25, 27.2, 0.2), (0.25, 30.0, 0.8), 'r_red')
    h.box(cr, (-0.15, 30.0, 0.3), (0.15, 30.6, 0.7), 'glow_red')
    h.mbox('head', (-4.55, 22.0, -1.25), (-3.92, 25.4, 1.4), 'n_black', out='r_red')                # 頬の角張った守り
    hr, hl = h.mbone('hfin_{s}', 'head', (-4.3, 25.2, 0.8), (25, 0, 0))                             # 後ろへ伸びる刃
    h.box(hr, (-4.45, 25.0, 0.6), (-4.15, 25.5, 3.6), 'r_red')
    h.box(hl, (4.15, 25.0, 0.6), (4.45, 25.5, 3.6), 'r_red')
    c.box('body', (-2.1, 17.0, -2.35), (2.1, 18.3, -1.6), 'n_black', front='n_black_rline')        # 胸の板
    c.mbox('body', (1.3, 18.5, -2.2), (2.35, 20.2, -1.3), 'r_red')
    c.mbox('body', (-3.3, 11.0, -1.45), (-2.65, 14.2, 1.25), 'n_black', out='r_red')                # 腰
    c.mbox('right_arm', (-4.9, 19.8, -1.3), (-2.4, 21.2, 1.35), 'n_black', out='r_red', top='n_black_rline')  # 角張った肩
    sr, sl = c.mbone('spike_{s}', 'right_arm', (-4.8, 20.8, 0.0), (0, 0, 30))
    c.box(sr, (-6.2, 20.6, -0.35), (-4.7, 21.1, 0.35), 'r_red')
    c.box(sl, (4.7, 20.6, -0.35), (6.2, 21.1, 0.35), 'r_red')
    c.mbox('right_arm', (-4.6, 12.8, -1.25), (-2.0, 15.8, 1.25), 'n_black', out='n_black_rline', top='r_red')  # 角張った前腕の守り
    br, bl = c.mbone('blade_{s}', 'right_arm', (-4.6, 13.0, 0.0), (0, 0, -15))
    c.box(br, (-4.9, 12.6, -0.2), (-4.6, 15.6, 0.2), 'r_red')
    c.box(bl, (4.6, 12.6, -0.2), (4.9, 15.6, 0.2), 'r_red')
    l.mbox('right_leg', (-2.5, 5.2, -2.1), (-0.1, 7.2, -1.2), 'n_black', front='n_black_rline')    # 膝
    kr, kl = l.mbone('kspike_{s}', 'right_leg', (-1.3, 6.6, -2.0), (-40, 0, 0))                    # 膝の棘（前・上へ）
    l.box(kr, (-1.6, 6.35, -3.2), (-1.0, 6.85, -1.95), 'r_red')
    l.box(kl, (1.0, 6.35, -3.2), (1.6, 6.85, -1.95), 'r_red')
    l.mbox('right_leg', (-2.75, 1.1, -1.75), (0.15, 5.1, -1.45), 'r_red', front='n_black_rline')   # 脛
    l.mbox('right_leg', (-2.95, 7.7, -1.2), (-2.6, 11.7, 1.1), 'n_black', out='r_red')             # 太もも
    l.mbox('right_leg', (-2.75, 0.2, -2.5), (0.15, 0.98, -1.95), 'r_red')                          # つま先


def frame_aegis():
    """イージス（T3）: 守護。盾のような大きな肩当て（左が大きい）、青と銀、頑丈で上品"""
    h, c, l = _parts('aegis')
    h.box('head', (-3.0, 26.45, -3.4), (3.0, 27.05, -2.8), 'a_blue', front='a_blue_line')          # 額の守り
    h.box('head', (-3.3, 26.98, -2.5), (3.3, 27.7, 2.7), 'v_silver', top='a_blue_line')            # 頭の上
    h.mbox('head', (-4.65, 22.0, -1.25), (-3.92, 25.6, 1.5), 'a_blue', out='v_silver')            # 耳の守り
    h.mbox('head', (-4.72, 23.0, -0.4), (-4.65, 24.4, 0.6), 'glow_blue')
    c.box('body', (-2.5, 16.2, -2.4), (2.5, 18.45, -1.25), 'v_silver', front='a_blue_line', top='a_blue')  # 胸当て
    c.mbox('body', (1.28, 18.45, -2.2), (2.45, 20.3, -1.28), 'a_blue', front='a_blue_line')
    c.box('right_arm', (-5.6, 18.6, -1.5), (-2.55, 21.4, 1.36), 'a_blue', out='v_silver', top='a_blue_line')   # 右の肩当て
    c.box('left_arm', (2.55, 17.8, -1.8), (6.4, 21.7, 1.36), 'a_blue', out='a_blue_line', top='v_silver')     # 左の大きな盾の肩当て
    c.box('left_arm', (3.95, 17.85, 1.3), (6.38, 21.65, 1.85), 'a_blue', back='v_silver')
    c.box('left_arm', (6.4, 18.2, -1.4), (6.65, 21.5, 1.45), 'v_silver')
    c.box('left_arm', (6.65, 19.4, -0.3), (6.72, 20.6, 0.3), 'glow_blue')
    c.mbox('right_arm', (-4.5, 12.4, -1.2), (-2.05, 15.4, 1.2), 'v_silver', out='a_blue_line')      # 籠手
    c.mbox('body', (-3.3, 10.6, -1.5), (-2.62, 14.1, 1.25), 'v_silver', out='a_blue_line')          # 腰の草摺
    c.mbox('body', (-2.3, 10.5, -1.75), (-0.3, 12.6, -1.48), 'a_blue', front='a_blue_line')
    l.mbox('right_leg', (-2.45, 8.0, -1.7), (-0.15, 11.4, -1.45), 'a_blue')                         # 太ももの前
    l.mbox('right_leg', (-2.98, 7.6, -1.2), (-2.6, 11.8, 1.1), 'v_silver', out='a_blue_line')        # 太ももの横
    l.mbox('right_leg', (-2.55, 5.2, -2.1), (-0.05, 7.3, -1.2), 'v_silver', front='a_blue_line')     # 膝
    l.mbox('right_leg', (-2.85, 0.95, -1.8), (0.25, 5.25, 1.55), 'a_blue', front='v_silver')        # 脛当て


def frame_tempest():
    """テンペスト（T3）: 空戦。肩と腰に翼の形の板、青緑と白、ふくらはぎの後退したひれ"""
    h, c, l = _parts('tempest')
    h.box('head', (-3.3, 26.5, -0.6), (3.3, 27.2, 0.5), 'v_white', top='t_teal_line')
    h.mbox('head', (-4.5, 22.6, -0.9), (-3.95, 24.5, 1.1), 'v_white', out='v_white_tline')
    fr, fl = h.mbone('efin_{s}', 'head', (-4.2, 24.0, 0.4), (25, 0, 0))                             # 後ろへ流れる耳のひれ
    h.box(fr, (-4.4, 23.6, 0.3), (-4.1, 24.4, 4.2), 't_teal', out='v_white_tline')
    h.box(fl, (4.1, 23.6, 0.3), (4.4, 24.4, 4.2), 't_teal', out='v_white_tline')
    c.mbox('body', (1.27, 18.0, -2.2), (2.42, 20.25, -1.3), 'v_white', front='v_white_tline')      # 胸当て
    c.box('body', (-1.2, 16.6, -2.2), (1.2, 17.6, -1.9), 't_teal')
    c.mbox('right_arm', (-4.7, 19.9, -1.2), (-2.45, 20.95, 1.25), 'v_white', out='v_white_tline')   # 肩
    wr, wl = c.mbone('swing_{s}', 'right_arm', (-4.5, 20.5, 0.4), (0, 20, 25))                     # 肩の翼（外・上・後ろへ）
    for w, s in ((wr, -1), (wl, 1)):
        c.box(w, (s * 4.5, 19.8, 0.25), (s * 7.3, 21.0, 0.55), 'v_white', front='v_white_tline', back='v_white_tline')
        c.box(w, (s * 7.3, 20.25, 0.28), (s * 8.4, 21.0, 0.52), 't_teal')
    c.mbox('right_arm', (-4.3, 13.8, -1.05), (-2.2, 15.2, 1.05), 't_teal', out='v_white_tline')    # 前腕
    c.mbox('body', (-3.25, 10.2, -1.2), (-2.62, 13.9, 1.2), 'v_white', out='v_white_tline')        # 腰の横
    hr, hl = c.mbone('hwing_{s}', 'body', (-3.25, 10.4, 0.3), (0, 15, -20))                       # 腰の翼（外・下へ。手より下）
    for w, s in ((hr, -1), (hl, 1)):
        c.box(w, (s * 3.25, 9.9, 0.15), (s * 6.0, 10.8, 0.45), 't_teal', front='v_white_tline', back='v_white_tline')
        c.box(w, (s * 6.0, 10.2, 0.18), (s * 6.9, 10.8, 0.42), 'v_white')
    l.mbox('right_leg', (-2.7, 1.2, -1.65), (0.1, 5.0, -1.45), 'v_white', front='v_white_tline')   # 脛
    l.mbox('right_leg', (-2.45, 5.3, -1.98), (-0.15, 7.0, -1.22), 't_teal')                        # 膝
    l.mbox('right_leg', (-2.95, 7.8, -1.1), (-2.6, 11.4, 1.0), 'v_white', out='t_teal_line')       # 太ももの横
    cr, cl = l.mbone('cfin_{s}', 'right_leg', (-2.75, 3.0, 0.5), (30, 0, 0))                       # ふくらはぎの後退したひれ
    l.box(cr, (-2.95, 2.6, 0.4), (-2.7, 3.5, 3.2), 't_teal', out='v_white_tline')
    l.box(cl, (2.7, 2.6, 0.4), (2.95, 3.5, 3.2), 't_teal', out='v_white_tline')


def frame_helios():
    """ヘリオス（T3）: 砲撃。片目の照準鏡（目の横）、肩の後ろの測距器、橙と金の日輪の光の線"""
    h, c, l = _parts('helios')
    h.box('head', (-3.4, 26.95, -0.45), (3.4, 27.35, 0.5), 'g_dark', top='h_line')                # 頭の帯
    h.box('head', (-0.8, 27.3, -0.15), (0.8, 28.9, 0.15), 'g_gold', front='glow_orange', back='glow_orange')  # 日輪
    for k, a in enumerate((-60, -30, 0, 30, 60)):
        ray = h.bone(f'fx_ray_{k}', 'head', (0, 28.1, 0), (0, 0, a))
        h.box(ray, (-0.12, 28.95, -0.1), (0.12, 29.7, 0.1), 'glow_orange')
    h.mbox('head', (-4.6, 22.4, -1.2), (-3.95, 25.0, 1.3), 'g_dark', out='h_line')                # 耳当て
    h.box('head', (4.05, 23.5, -3.45), (4.3, 23.9, -0.8), 'g_dark')                               # 照準鏡の腕（+x、横髪の外）
    h.box('head', (2.0, 23.0, -3.6), (4.35, 24.4, -3.4), 'g_dark', front='nozzle_orange')         # 照準鏡（目の横・横髪の前）
    c.box('body', (-2.2, 16.7, -2.3), (2.2, 18.35, -1.6), 'g_dark', front='h_line')               # 胸の板
    sun = c.bone('fx_sun', 'body', (0, 16.75, -2.33))                                              # 胸の日輪の光（発光だけ）
    for k, a in enumerate((-50, -25, 0, 25, 50)):
        ray = c.bone(f'sun_{k}', sun, (0, 16.75, -2.33), (0, 0, a))
        c.box(ray, (-0.08, 16.8, -2.37), (0.08, 18.2, -2.32), 'glow_orange')
    c.mbox('right_arm', (-4.8, 19.9, -1.25), (-2.45, 21.0, 1.3), 'g_dark', out='h_line')           # 肩
    c.mbox('right_arm', (-5.2, 20.4, 1.2), (-4.0, 21.6, 3.0), 'g_dark', back='nozzle_orange', top='g_gold')  # 肩の後ろの測距器
    c.box('body', (-3.0, 12.9, -1.6), (3.0, 13.5, 1.36), 'g_dark', front='g_gold')                # 弾帯
    c.mbox('body', (-3.3, 11.0, -1.2), (-2.62, 13.0, 1.1), 'g_dark', out='h_line')
    c.mbox('right_arm', (-4.45, 13.2, -1.15), (-2.1, 15.5, 1.15), 'g_dark', out='h_line', top='g_gold')  # 前腕
    l.mbox('right_leg', (-2.9, -0.03, -2.5), (0.3, 1.8, 1.5), 'g_dark', top='g_gold', front='h_line')    # 踏ん張る靴
    l.mbox('right_leg', (-2.48, 5.25, -2.05), (-0.12, 7.2, -1.2), 'g_gold', front='h_vline')       # 膝
    l.mbox('right_leg', (-2.96, 7.7, -1.15), (-2.6, 11.6, 1.05), 'g_dark', out='h_vline')          # 太もも


def frame_nemesis():
    """ネメシス（T4）: 決戦。黒と深紅、赤く光る線、鋭い重ねた板、角の飾り"""
    h, c, l = _parts('nemesis')
    h.box('head', (-3.45, 26.95, -1.3), (3.45, 27.55, 1.8), 'n_black', top='n_black_rline')        # 兜の帯
    hr, hl = h.mbone('horn_{s}', 'head', (-2.6, 27.3, 0.0), (-20, 0, -25))                         # 角（外・後ろへ）
    for b, s in ((hr, -1), (hl, 1)):
        h.box(b, (s * 2.9, 27.3, -0.3), (s * 2.3, 29.5, 0.3), 'n_black', out='n_black_rvline')
        h.box(b, (s * 2.8, 29.5, -0.2), (s * 2.4, 30.3, 0.2), 'n_crimson')
    h.mbox('head', (-4.6, 21.9, -1.25), (-3.92, 25.4, 1.5), 'n_black', out='n_black_rvline')        # 耳の重ねた板
    h.mbox('head', (-4.8, 22.4, -0.8), (-4.6, 24.9, 1.1), 'n_crimson')
    h.mbox('head', (-4.86, 23.2, -0.2), (-4.8, 24.2, 0.6), 'glow_red')
    c.box('body', (-2.35, 16.5, -2.4), (2.35, 18.3, -1.3), 'n_black', front='n_black_rline')       # 胸の板
    c.mbox('body', (1.27, 18.3, -2.25), (2.4, 20.35, -1.3), 'n_crimson', front='n_black_rvline')
    c.box('body', (-2.0, 17.2, -2.45), (2.0, 17.35, -2.4), 'glow_red')
    c.mbox('right_arm', (-5.0, 19.8, -1.35), (-2.45, 21.15, 1.36), 'n_black', out='n_black_rline')  # 重ねた肩
    c.mbox('right_arm', (-5.3, 18.9, -1.2), (-3.5, 19.85, 1.2), 'n_crimson')
    for key, piv, a, box in (('a', (-4.9, 20.9, 0.0), 35, ((-6.5, 20.7, -0.3), (-4.9, 21.15, 0.35), 'n_black')),
                             ('b', (-5.2, 19.6, 0.0), 25, ((-6.0, 19.4, -0.25), (-5.2, 19.8, 0.3), 'n_crimson'))):
        sr, sl = c.mbone('spike_' + key + '_{s}', 'right_arm', piv, (0, 0, a))
        (x0, y0, z0), (x1, y1, z1), mat = box
        c.box(sr, (x0, y0, z0), (x1, y1, z1), mat)
        c.box(sl, (-x0, y0, z0), (-x1, y1, z1), mat)
    c.mbox('right_arm', (-4.55, 12.5, -1.2), (-2.05, 15.6, 1.2), 'n_black', out='n_black_rline', top='n_crimson')  # 籠手
    c.mbox('body', (-3.35, 10.4, -1.45), (-2.62, 14.1, 1.25), 'n_black', out='n_black_rvline')      # 腰の重ねた草摺
    c.mbox('body', (-2.4, 10.2, -1.75), (-0.3, 12.6, -1.5), 'n_crimson', front='n_black_rvline')
    l.mbox('right_leg', (-2.98, 7.5, -1.25), (-2.6, 11.8, 1.12), 'n_black', out='n_black_rvline')    # 太もも
    l.mbox('right_leg', (-2.5, 5.2, -2.1), (-0.1, 7.3, -1.2), 'n_black', front='n_black_rline')      # 膝
    kr, kl = l.mbone('kspike_{s}', 'right_leg', (-1.3, 6.6, -2.0), (-40, 0, 0))
    l.box(kr, (-1.6, 6.35, -3.2), (-1.0, 6.85, -1.95), 'n_crimson')
    l.box(kl, (1.0, 6.35, -3.2), (1.6, 6.85, -1.95), 'n_crimson')
    l.mbox('right_leg', (-2.78, 1.1, -1.8), (0.18, 5.15, -1.45), 'n_black', front='n_black_rvline')  # 脛
    l.mbox('right_leg', (-2.85, 1.3, -1.38), (-2.62, 5.0, 1.25), 'n_crimson')
    l.mbox('right_leg', (-2.78, 0.2, -2.55), (0.18, 0.98, -1.9), 'n_crimson')                       # つま先


def frame_luminous():
    """ルミナス（T4）: 力。透けて見える結晶の板（発光）、白にシアンの光の導管が手足を走る、胸のコア"""
    h, c, l = _parts('luminous')
    h.box('head', (-3.3, 26.95, -0.5), (3.3, 27.4, 0.45), 'v_white', top='v_white_line')          # 結晶の冠の台
    h.box('head', (-0.35, 27.4, -0.3), (0.35, 29.0, 0.3), 'crystal')
    h.mbox('head', (-1.6, 27.4, -0.2), (-1.1, 28.3, 0.25), 'crystal')
    h.mbox('head', (-4.55, 22.4, -1.1), (-3.95, 24.9, 1.3), 'v_white', out='v_white_line')        # 耳
    fr, fl = h.mbone('efin_{s}', 'head', (-4.25, 24.3, 1.0), (28, 0, 0))                          # 結晶のひれ
    h.box(fr, (-4.4, 24.0, 0.8), (-4.1, 24.6, 3.8), 'crystal')
    h.box(fl, (4.1, 24.0, 0.8), (4.4, 24.6, 3.8), 'crystal')
    c.box('body', (-1.1, 18.75, -2.25), (1.1, 20.35, -1.95), 'crystal', front='glow_cyan')        # 胸のコア
    c.mbox('body', (1.3, 18.0, -2.2), (2.45, 20.4, -1.3), 'v_white', front='v_white_vline')
    c.box('body', (-0.12, 14.55, -1.48), (0.12, 16.4, -1.43), 'glow_cyan')                        # 腹の導管
    c.mbox('body', (-3.25, 11.2, -1.3), (-2.62, 13.95, 1.2), 'v_white', out='v_white_vline')       # 腰
    c.mbox('right_arm', (-4.85, 19.95, -1.25), (-2.45, 21.0, 1.3), 'v_white', out='v_white_line')  # 肩
    sr, sl = c.mbone('shard_{s}', 'right_arm', (-4.8, 20.7, 0.0), (0, 0, 25))                      # 肩の結晶
    c.box(sr, (-6.0, 20.4, -0.4), (-4.7, 21.0, 0.4), 'crystal')
    c.box(sl, (4.7, 20.4, -0.4), (6.0, 21.0, 0.4), 'crystal')
    c.mbox('right_arm', (-4.47, 12.9, -0.15), (-4.42, 20.5, 0.15), 'glow_cyan')                    # 腕の導管
    c.mbox('right_arm', (-4.38, 13.5, -1.1), (-2.15, 15.3, 1.1), 'v_white', out='crystal')         # 前腕
    l.mbox('right_leg', (-2.97, 7.7, -1.1), (-2.6, 11.7, 1.05), 'v_white', out='v_white_vline')    # 太もも
    l.mbox('right_leg', (-2.72, 1.0, -0.15), (-2.67, 7.5, 0.15), 'glow_cyan')                      # 脚の導管
    l.mbox('right_leg', (-2.45, 5.3, -2.0), (-0.15, 7.1, -1.2), 'crystal')                         # 結晶の膝
    l.mbox('right_leg', (-2.75, 1.15, -1.7), (0.15, 5.2, -1.42), 'v_white', front='v_white_vline')  # 脛
    l.mbox('right_leg', (-1.35, 1.3, -1.75), (-1.05, 5.1, -1.7), 'glow_cyan')


# ---------------- 背部ユニット（胴の骨。背中の横から張り出す腕で、後ろ髪の外へ出す）
def strut(m, y=17.62, top=18.38):
    """背中の左右から外へ出る支え（腕の後ろ・後ろ髪の前の薄い隙間 z 1.17〜1.38）"""
    m.mbox('body', (-4.12, y, 1.17), (-1.62, top, 1.38), 'b_dark')


def backpack_thruster():
    m = model('backpack_thruster')
    strut(m)
    pr, pl = m.mbone('pod_{s}', 'body', (-4.85, 18.0, 1.3), (-14, 0, 0))                 # 下が後ろへ傾いた推進器
    for b, s in ((pr, -1), (pl, 1)):
        def B(x0, y0, z0, x1, y1, z1, mat, **f):
            m.box(b, (s * x0, y0, z0), (s * x1, y1, z1), mat, **f)
        B(4.05, 14.5, 1.25, 5.65, 20.0, 3.25, 'b_white', out='b_white_vline', back='b_grey', top='b_grey')
        B(4.25, 13.4, 1.45, 5.45, 14.5, 3.05, 'b_dark', bottom='nozzle')
        B(5.65, 16.0, 2.0, 6.1, 20.6, 3.2, 'b_white', out='b_white_line')
        B(4.35, 19.0, 3.25, 5.35, 19.8, 3.55, 'b_vent')
    fr, fl = m.mbone('fx_flame_{s}', pr, (-4.85, 13.4, 2.25), None)
    m.box(fr, (-5.25, 11.9, 1.85), (-4.45, 13.38, 2.65), 'glow_cyan')
    m.box(fl, (4.45, 11.9, 1.85), (5.25, 13.38, 2.65), 'glow_cyan')


def thruster_pod(m, tilt=-14):
    """ハイマニューバスラスター共通の推進器の骨（左右。Mk-I と同じ傾き）。返り値は 右, 左, (骨, 符号) の組"""
    pr, pl = m.mbone('pod_{s}', 'body', (-4.85, 18.0, 1.3), (tilt, 0, 0))
    return pr, pl, ((pr, -1), (pl, 1))


def backpack_thruster_mk2():
    """Mk-II: 大きくなった双発の推進器＋左右 1 枚ずつの機械の翼（斜めの骨組み＋板、下の縁がシアンに光る。片側 0.7 ブロックほど）"""
    m = model('backpack_thruster_mk2')
    strut(m, 17.3, 18.62)
    pr, pl, sides = thruster_pod(m)
    for b, s in sides:
        def B(x0, y0, z0, x1, y1, z1, mat, **f):
            m.box(b, (s * x0, y0, z0), (s * x1, y1, z1), mat, **f)
        B(4.05, 14.0, 1.25, 6.05, 20.4, 3.45, 'b_white', out='b_white_vline', back='b_grey', top='b_grey')
        B(4.2, 12.8, 1.4, 5.9, 14.0, 3.3, 'b_dark', bottom='nozzle')                       # 太いノズル
        B(6.05, 15.4, 1.9, 6.5, 20.9, 3.2, 'b_white', out='b_white_line')                  # 外のレール（翼の付け根）
        B(6.5, 16.6, 2.35, 6.6, 18.4, 2.75, 'glow_cyan')
        B(4.35, 18.9, 3.45, 5.75, 19.9, 3.75, 'b_vent')
        B(4.85, 15.0, 3.45, 5.25, 18.6, 4.15, 'b_white', back='b_white_line')              # 背びれ
    # 機械の翼: 外へ斜めに上がる骨組み（暗い棒）と、その下に下がる白い板
    wr, wl = m.mbone('wing_{s}', pr, (-6.5, 20.0, 2.75), (0, 20, 24))
    for w, s in ((wr, -1), (wl, 1)):
        m.box(w, (s * 6.55, 19.7, 2.5), (s * 13.0, 20.4, 3.0), 'b_dark', top='b_dark_line', front='b_dark_line', back='b_dark_line')   # 骨組み
        m.box(w, (s * 12.6, 19.5, 2.45), (s * 13.4, 20.6, 3.05), 'b_grey')                                                          # 先の継ぎ手
        m.box(w, (s * 6.6, 17.6, 2.62), (s * 12.4, 19.7, 2.88), 'b_white', front='b_white_line', back='b_white_line')               # 板
        m.box(w, (s * 6.6, 16.4, 2.64), (s * 9.6, 17.6, 2.86), 'b_white', front='b_white_line', back='b_white_line')
    er, el = m.mbone('fx_wing_edge_{s}', wr, (-6.5, 20.0, 2.75), None)
    for e, s in ((er, -1), (el, 1)):
        m.box(e, (s * 9.6, 17.38, 2.66), (s * 12.4, 17.6, 2.84), 'glow_cyan')
        m.box(e, (s * 6.8, 16.18, 2.66), (s * 9.6, 16.4, 2.84), 'glow_cyan')
        m.box(e, (s * 9.6, 16.4, 2.66), (s * 9.82, 17.6, 2.84), 'glow_cyan')
    dr, dl = m.mbone('fin_low_{s}', pr, (-6.5, 15.8, 2.55), (0, 12, -26))
    m.box(dr, (-6.4, 15.45, 2.42), (-7.4, 16.15, 2.68), 'b_grey')
    m.box(dl, (6.4, 15.45, 2.42), (7.4, 16.15, 2.68), 'b_grey')
    fr, fl = m.mbone('fx_flame_{s}', pr, (-5.05, 12.8, 2.35), None)
    m.box(fr, (-5.6, 10.9, 1.8), (-4.5, 12.78, 2.9), 'glow_cyan')
    m.box(fl, (4.5, 10.9, 1.8), (5.6, 12.78, 2.9), 'glow_cyan')


def backpack_thruster_mk3():
    """Mk-III: 後ろへ流れる大きな機械の翼（上・中・下の 3 枚の推進翼。前の縁は暗い骨組み、下の縁が光る。片側 1.1 ブロックほど）＋白銀の推進器"""
    m = model('backpack_thruster_mk3')
    strut(m, 17.3, 18.62)
    pr, pl, sides = thruster_pod(m)
    for b, s in sides:
        def B(x0, y0, z0, x1, y1, z1, mat, **f):
            m.box(b, (s * x0, y0, z0), (s * x1, y1, z1), mat, **f)
        B(4.05, 14.0, 1.25, 6.05, 20.6, 3.45, 'b_white', out='b_white_vline', back='b_white_line', top='v_silver')
        B(4.2, 12.8, 1.4, 5.9, 14.0, 3.3, 'b_dark', bottom='nozzle')
        B(6.05, 14.6, 1.8, 6.45, 21.0, 3.3, 'v_silver')                                    # 翼の付け根の柱
        B(4.35, 19.0, 3.45, 5.75, 20.0, 3.75, 'b_vent')
        B(4.6, 15.2, 3.45, 5.5, 16.0, 3.62, 'glow_cyan')
    # 推進翼: (名前, 付け根の高さ, 跳ね上げ, 後退, 長さ, 翼弦, 奥行き)。-x 側は Z が正で外の端が上がり、Y が正で後ろへ流れる。
    # 付け根の 6 割は太く、先は細い。翼ごとに奥行きをずらす（同じ平面に面を置かない）
    for key, y, up, back, ln, ch, z in (('a', 20.2, 30, 18, 13.0, 2.2, 2.3), ('b', 17.4, 8, 22, 11.0, 1.8, 2.75),
                                        ('c', 15.0, -14, 26, 8.0, 1.4, 3.2)):
        vr, vl = m.mbone('vane_' + key + '_{s}', pr, (-6.3, y, z + 0.15), (0, back, up))
        for v, s in ((vr, -1), (vl, 1)):
            m.box(v, (s * 6.3, y + ch / 2 - 0.5, z - 0.1), (s * (6.3 + ln + 0.4), y + ch / 2, z + 0.4), 'b_dark', top='b_dark_line', front='b_dark_line', back='b_dark_line')  # 前の縁の骨組み
            m.box(v, (s * 6.3, y - ch / 2, z), (s * (6.3 + ln * 0.6), y + ch / 2 - 0.5, z + 0.3), 'b_white', front='b_white_line', back='b_white_line')
            m.box(v, (s * (6.3 + ln * 0.6), y - ch * 0.15, z + 0.03), (s * (6.3 + ln), y + ch / 2 - 0.5, z + 0.27), 'b_white', front='b_white_line', back='b_white_line')
            m.box(v, (s * (6.3 + ln + 0.4), y + ch / 2 - 0.6, z - 0.15), (s * (6.3 + ln + 0.9), y + ch / 2 + 0.1, z + 0.45), 'v_silver')  # 先の継ぎ手
        er, el = m.mbone('fx_edge_' + key + '_{s}', vr, (-6.3, y, z + 0.15), None)
        for e, s in ((er, -1), (el, 1)):
            m.box(e, (s * 6.5, y - ch / 2 - 0.22, z + 0.05), (s * (6.3 + ln * 0.6), y - ch / 2, z + 0.25), 'glow_cyan')
            m.box(e, (s * (6.3 + ln * 0.6), y - ch * 0.15 - 0.22, z + 0.05), (s * (6.3 + ln), y - ch * 0.15, z + 0.25), 'glow_cyan')
    fr, fl = m.mbone('fx_flame_{s}', pr, (-5.05, 12.8, 2.35), None)
    m.box(fr, (-5.6, 10.6, 1.8), (-4.5, 12.78, 2.9), 'glow_cyan')
    m.box(fl, (4.5, 10.6, 1.8), (5.6, 12.78, 2.9), 'glow_cyan')


def backpack_thruster_mk4():
    """Ⅳ（ヴァルキュリア）: 白と金の推進器＋大きな機械の翼（外へ上がる金の腕の中ほどに豪華な副推進器を下げ、腕の先から 6 枚の羽が扇に広がり、羽の後ろに白シアンの光の羽を重ねる。片側 1.5 ブロックほど）"""
    m = model('backpack_thruster_mk4')
    strut(m, 17.3, 18.62)
    pr, pl, sides = thruster_pod(m)
    for b, s in sides:
        def B(x0, y0, z0, x1, y1, z1, mat, **f):
            m.box(b, (s * x0, y0, z0), (s * x1, y1, z1), mat, **f)
        B(4.05, 14.05, 1.25, 6.15, 20.6, 3.5, 'b_white', out='b_white_vgold', back='b_white_gold', top='g_gold')
        B(4.2, 12.8, 1.35, 5.95, 13.7, 2.35, 'b_dark', bottom='nozzle_white')              # 前後 2 つのノズル
        B(4.2, 12.8, 2.45, 5.95, 13.7, 3.4, 'b_dark', bottom='nozzle_white')
        B(4.1, 13.7, 1.3, 6.1, 14.05, 3.45, 'g_gold')                                      # ノズルの上の金の帯
        B(6.15, 14.6, 1.75, 6.6, 21.2, 3.35, 'g_gold')                                     # 羽の付け根の柱
        B(4.6, 20.6, 2.0, 5.6, 21.9, 2.9, 'g_gold')                                        # 上の冠
        B(4.85, 21.9, 2.2, 5.35, 22.5, 2.7, 'glow_gold')
        B(4.6, 15.4, 3.5, 5.6, 19.4, 3.62, 'glow_white')
    wr, wl = m.mbone('wing_{s}', pr, (-6.4, 19.0, 2.6), (0, 16, 0))
    # 翼の腕（外へ上がる金の骨組み。羽より手前の奥行き）
    ar, al = m.mbone('arm_{s}', wr, (-6.4, 19.0, 2.0), (0, 0, 22))
    for w, s in ((ar, -1), (al, 1)):
        m.box(w, (s * 6.5, 18.5, 1.75), (s * 13.4, 19.7, 2.25), 'g_gold', front='b_white_gold', top='g_gold')
        m.box(w, (s * 8.0, 19.7, 1.85), (s * 12.0, 20.1, 2.15), 'b_white', top='b_white_gold')
        m.box(w, (s * 13.0, 18.2, 1.65), (s * 14.0, 20.0, 2.35), 'b_white', front='b_white_vgold')   # 先の継ぎ手
        m.box(w, (s * 9.0, 18.3, 1.7), (s * 12.0, 18.5, 1.8), 'glow_white')
        m.box(w, (s * 10.0, 18.2, 2.05), (s * 11.0, 18.9, 2.65), 'g_gold')                  # 副推進器の取り付け金具
    # 腕の中ほどに下げる豪華な副推進器（腕の傾きを打ち消して下・後ろへ噴く。羽より内側、腕の後ろの奥行き）
    sr, sl = m.mbone('subpod_{s}', ar, (-10.5, 18.4, 3.1), (20, 0, -22))
    for b, s in ((sr, -1), (sl, 1)):
        def B(x0, y0, z0, x1, y1, z1, mat, **f):
            m.box(b, (s * x0, y0, z0), (s * x1, y1, z1), mat, **f)
        B(9.75, 15.4, 2.4, 11.25, 18.3, 3.8, 'b_white', out='b_white_vgold', back='b_white_gold', top='g_gold')
        B(9.65, 17.6, 2.3, 11.35, 17.95, 3.9, 'g_gold')                                    # 上の金の輪
        B(9.6, 15.0, 2.25, 11.4, 15.4, 3.95, 'g_gold')                                     # 下の金の帯
        B(9.9, 14.2, 2.55, 11.1, 15.0, 3.65, 'b_dark', bottom='nozzle_white')              # ノズル
        B(10.3, 17.6, 3.5, 10.7, 19.6, 4.3, 'g_gold')                                      # 金の冠のひれ
        B(10.35, 19.6, 3.7, 10.65, 19.9, 4.1, 'glow_gold')
        B(10.2, 15.8, 3.8, 10.8, 17.3, 3.86, 'glow_white')
    # ノズルを囲む金の輪（ゆっくり回る）
    rr, rl = m.mbone('spin_y_ring_{s}', sr, (-10.5, 14.55, 3.1), None)
    for r, s in ((rr, -1), (rl, 1)):
        m.box(r, (s * 9.55, 14.4, 2.05), (s * 11.45, 14.7, 2.25), 'g_gold')
        m.box(r, (s * 9.55, 14.4, 3.95), (s * 11.45, 14.7, 4.15), 'g_gold')
        m.box(r, (s * 9.55, 14.42, 2.25), (s * 9.75, 14.68, 3.95), 'g_gold')
        m.box(r, (s * 11.25, 14.42, 2.25), (s * 11.45, 14.68, 3.95), 'g_gold')
    qr, ql = m.mbone('fx_subflame_{s}', sr, (-10.5, 14.2, 3.1), None)
    m.box(qr, (-10.9, 12.4, 2.7), (-10.1, 14.18, 3.5), 'glow_white')
    m.box(ql, (10.1, 12.4, 2.7), (10.9, 14.18, 3.5), 'glow_white')
    # 羽: 腕の先（回す前の座標で -13.5, 19.1）から、腕に対する跳ね上げ・長さ。1 枚ごとに奥行きをずらす
    for k, (up, ln) in enumerate(((26, 11.0), (10, 12.6), (-6, 12.8), (-22, 11.4), (-38, 9.4), (-54, 7.2))):
        z = 2.3 + k * 0.45
        fr, fl = m.mbone(f'feather_{k}_{{s}}', ar, (-13.5, 19.1, z + 0.15), (0, k * 2, up))
        x0 = 13.5
        for f, s in ((fr, -1), (fl, 1)):
            m.box(f, (s * x0, 18.35, z), (s * (x0 + ln * 0.6), 19.85, z + 0.3), 'b_white', front='b_white_gold', back='b_white_gold', top='g_gold')
            m.box(f, (s * (x0 + ln * 0.6), 18.5, z + 0.03), (s * (x0 + ln), 19.55, z + 0.27), 'b_white', front='b_white_gold', back='b_white_gold', top='g_gold')
            m.box(f, (s * (x0 + ln), 18.75, z + 0.05), (s * (x0 + ln + 0.6), 19.3, z + 0.25), 'glow_gold')  # 羽の先の光
        qr, ql = m.mbone(f'fx_plume_{k}_{{s}}', fr, (-13.5, 19.1, z + 0.15), None)         # 羽の後ろの光の羽
        for f, s in ((qr, -1), (ql, 1)):
            m.box(f, (s * (x0 + 0.5), 18.45, z + 0.33), (s * (x0 + ln + 0.4), 19.75, z + 0.40), 'glow_white')
    fr, fl = m.mbone('fx_flame_{s}', pr, (-5.07, 12.8, 2.4), None)
    for f, s in ((fr, -1), (fl, 1)):
        m.box(f, (s * 4.6, 10.3, 1.6), (s * 5.55, 12.78, 2.15), 'glow_white')
        m.box(f, (s * 4.6, 10.3, 2.65), (s * 5.55, 12.78, 3.2), 'glow_white')

def backpack_gallia_fortress():
    """ガリア・フォートレス（T3、2026-10-06 に全身外装から背部ユニットへ）: 背中の重い発電機（灰と金）＋兵姫のまわりを回る 5 枚の盾。
    盾の輪は兵姫の縦の軸（x=0, z=0）で回る（spin_y_）。半径 16px で腕・髪・武器の外を回る"""
    m = model('backpack_gallia_fortress')
    strut(m)
    m.mbox('body', (-5.7, 14.8, 1.25), (-4.02, 19.8, 3.5), 'f_steel', out='f_steel_line', back='f_vent', top='g_gold', bottom='g_gold')  # 左右の発電機
    m.mbox('body', (-5.85, 18.6, 1.15), (-3.95, 19.05, 3.6), 'g_gold')                     # 金の輪
    m.mbox('body', (-5.85, 15.5, 1.15), (-3.95, 15.95, 3.6), 'g_gold')
    m.mbox('body', (-5.8, 16.4, 1.9), (-5.7, 18.1, 2.9), 'glow_gold')                      # 外の光る窓
    m.mbox('body', (-5.3, 19.8, 1.85), (-4.4, 20.6, 2.9), 'f_dark', top='glow_gold')        # 上の放熱の筒
    m.mbox('body', (-5.3, 14.1, 1.85), (-4.4, 14.8, 2.9), 'f_dark', bottom='glow_gold')     # 下の筒
    R_, cy, n = 16.0, 16.0, 5
    ring = m.bone('spin_y_ring', 'body', (0, cy, 0))
    for k in range(n):
        sh = m.bone(f'shield_{k}', ring, (0, cy, 0), (0, k * 360 / n, 0))
        t = m.bone(f'tilt_{k}', sh, (0, 12.5, R_), (-10, 0, 0))                          # 上を外へ少し傾ける
        z0, z1 = R_, R_ + 0.4
        m.box(t, (-1.8, 13.9, z0), (1.8, 18.1, z1), 'f_steel', back='g_dark', front='f_steel')   # 盾の胴（外＝south）
        m.box(t, (-1.2, 18.0, z0 + 0.03), (1.2, 19.2, z1 - 0.03), 'f_steel', back='g_gold')           # 上の肩
        m.box(t, (-0.55, 19.1, z0 + 0.06), (0.55, 19.8, z1 - 0.06), 'g_gold')                         # 上の尖り
        m.box(t, (-1.2, 12.8, z0 + 0.03), (1.2, 14.0, z1 - 0.03), 'f_steel', back='g_gold')           # 下の肩
        m.box(t, (-0.55, 12.2, z0 + 0.06), (0.55, 12.9, z1 - 0.06), 'g_gold')                         # 下の尖り
        m.box(t, (-1.95, 13.9, z0 - 0.05), (-1.8, 18.1, z1 + 0.05), 'g_gold')                         # 左右の金の縁
        m.box(t, (1.8, 13.9, z0 - 0.05), (1.95, 18.1, z1 + 0.05), 'g_gold')
        m.box(t, (-0.35, 15.4, z1), (0.35, 16.6, z1 + 0.08), 'glow_gold')                             # 中央の紋の光
        fx = m.bone(f'fx_edge_{k}', t, (0, 12.5, R_), None)                                # 光る縁（発光だけ）
        m.box(fx, (-2.05, 14.0, z1 + 0.1), (-1.9, 18.0, z1 + 0.16), 'glow_gold')
        m.box(fx, (1.9, 14.0, z1 + 0.1), (2.05, 18.0, z1 + 0.16), 'glow_gold')
        m.box(fx, (-1.15, 19.25, z1 + 0.1), (1.15, 19.4, z1 + 0.16), 'glow_white')
        m.box(fx, (-1.15, 12.6, z1 + 0.1), (1.15, 12.75, z1 + 0.16), 'glow_white')


# ---------------- 背部ユニット 11 種（2026-10-06）。背中の真ん中に置く物は後ろ髪（z 3.6 まで、頭の向きで回る）より後ろ z 5.3 以上に置き、
# 左右の柱（|x| 4.02 以上）で支える。浮く・回る物は兵姫の縦の軸の spin_y_。兵姫の面と同じ平面はずらす（avoid）
def _bp(name):
    m = model(name)
    m.avoid = True
    return m


def backpack_sub_arm():
    """サブアーム（T1）: 右の肩甲骨から出る関節の腕が、肩の高さで小さな丸い盾を前・外へ構える"""
    m = _bp('backpack_sub_arm')
    strut(m)
    m.box('body', (-4.7, 16.8, 1.25), (-4.0, 19.8, 2.7), 'b_dark', out='b_dark_line')                  # 付け根
    a1 = m.bone('arm_upper', 'body', (-4.4, 19.2, 1.95), (0, 0, 10))                                     # 外へ
    m.box(a1, (-4.4, 18.9, 1.65), (-7.0, 19.5, 2.25), 'b_grey')
    m.box(a1, (-7.0, 18.75, 1.5), (-7.65, 19.65, 2.4), 'b_dark', out='glow_cyan', top='glow_cyan')    # 関節の光
    a2 = m.bone('arm_fore', a1, (-7.3, 19.2, 1.95), None)                                               # 前へ
    m.box(a2, (-7.55, 18.95, 1.5), (-7.05, 19.45, -2.4), 'b_grey')
    m.box(a2, (-7.65, 18.85, -2.4), (-6.95, 19.55, -2.9), 'b_dark')
    sh = m.bone('buckler', a2, (-7.3, 19.2, -2.95), (0, -20, 0))                                        # 小さな丸い盾（外へ向ける）
    m.box(sh, (-8.8, 17.9, -3.2), (-5.8, 20.5, -2.95), 'f_steel', front='b_dark_line')
    m.box(sh, (-8.4, 17.5, -3.15), (-6.2, 20.9, -3.0), 'f_steel')
    m.box(sh, (-9.2, 18.3, -3.15), (-5.4, 20.1, -3.0), 'f_steel')
    m.box(sh, (-7.6, 18.8, -3.35), (-7.0, 19.6, -3.2), 'glow_cyan')


def _orbit_drone(m, ring, k, ang, cy, r, body, eye):
    """spin_y_ の輪に付く小さなドローン（四隅の回転翼）。ang は輪の上の角度"""
    d = m.bone(f'drone_{k}', ring, (0, cy, 0), (0, ang, 0))
    m.box(d, (-1.1, cy - 0.3, r - 1.1), (1.1, cy + 0.3, r + 1.1), body)                    # 円盤
    m.box(d, (-0.65, cy - 0.65, r - 0.65), (0.65, cy - 0.3, r + 0.65), 'b_dark', bottom=eye)  # 目
    for i, (x, z) in enumerate(((-1.4, -1.4), (1.4, -1.4), (-1.4, 1.4), (1.4, 1.4))):
        m.box(d, (x - 0.15, cy + 0.0 + i * 0.01, r + z - 0.15), (x + 0.15, cy + 0.35, r + z + 0.15), 'b_dark')   # 支柱
        m.box(d, (x - 0.55, cy + 0.35, r + z - 0.55), (x + 0.55, cy + 0.45, r + z + 0.55), 'b_grey', top='glow_cyan')  # 回転翼
    return d


def backpack_recon_drone():
    """偵察ドローン（T1）: 背中の小さな格納台＋頭の高さを半径 14px で回るドローン 1 基（緑の目）"""
    m = _bp('backpack_recon_drone')
    strut(m)
    m.mbox('body', (-5.3, 15.5, 1.25), (-4.02, 18.6, 2.9), 'b_grey', out='b_dark_line', top='b_dark')
    m.mbox('body', (-5.36, 16.4, 1.7), (-5.3, 17.4, 2.4), 'glow_green')
    ring = m.bone('spin_y_orbit', 'body', (0, 25.5, 0))
    _orbit_drone(m, ring, 0, 0, 25.5, 14.0, 'b_dark', 'glow_green')


def backpack_spare_battery():
    """増設バッテリー（T1）: 背中の左右に円筒の電池 2 本（黄とシアンの残量の光）"""
    m = _bp('backpack_spare_battery')
    strut(m)
    m.mbox('body', (-5.55, 14.5, 1.45), (-4.25, 19.5, 2.75), 'b_grey', back='b_dark_line')            # 円筒（2 つの箱を重ねて八角に）
    m.mbox('body', (-5.75, 14.6, 1.65), (-4.05, 19.4, 2.55), 'b_grey')
    m.mbox('body', (-5.3, 19.5, 1.75), (-4.5, 20.0, 2.45), 'b_dark', top='glow_cyan')                 # 端子
    m.mbox('body', (-5.3, 14.0, 1.75), (-4.5, 14.5, 2.45), 'b_dark')
    m.mbox('body', (-5.81, 15.2, 1.95), (-5.75, 18.8, 2.25), 'glow_yellow')                            # 残量の光
    m.mbox('body', (-5.1, 15.2, 2.75), (-4.7, 18.8, 2.81), 'glow_cyan')


def backpack_repair_drone():
    """リペアドローン（T2）: 背中の格納台＋向かい合って半径 12px で回る白いドローン 2 基（緑の十字）"""
    m = _bp('backpack_repair_drone')
    strut(m)
    m.mbox('body', (-5.3, 15.5, 1.25), (-4.02, 18.6, 2.9), 'b_white', out='b_white_vline', top='b_grey')
    ring = m.bone('spin_y_orbit', 'body', (0, 24.0, 0))
    for k, ang in enumerate((0, 180)):
        d = _orbit_drone(m, ring, k, ang, 24.0, 12.0, 'b_white', 'glow_green')
        m.box(d, (-0.7, 24.3, 11.85), (0.7, 24.4, 12.15), 'glow_green')                                 # 上の緑の十字
        m.box(d, (-0.15, 24.3, 11.3), (0.15, 24.42, 12.7), 'glow_green')
        m.box(d, (-0.6, 23.85, 13.1), (0.6, 24.15, 13.18), 'glow_green')                               # 外の面の十字
        m.box(d, (-0.15, 23.72, 13.1), (0.15, 24.28, 13.16), 'glow_green')


def backpack_micro_missile():
    """マイクロミサイルポッド（T2）: 肩の上に箱の発射機 2 つ（2×2 の筒、赤い弾頭、少し上向き）"""
    m = _bp('backpack_micro_missile')
    strut(m)
    m.mbox('body', (-5.5, 17.6, 1.6), (-4.9, 22.4, 2.4), 'b_grey')                                    # 支柱
    pr, pl = m.mbone('pod_{s}', 'body', (-6.2, 22.2, 2.0), (-15, 0, 0))                               # 前を上へ
    for b, s_ in ((pr, -1), (pl, 1)):
        def B(x0, y0, z0, x1, y1, z1, mat, **f):
            m.box(b, (s_ * x0, y0, z0), (s_ * x1, y1, z1), mat, **f)
        B(5.0, 22.2, 0.3, 7.4, 24.6, 3.3, 'b_dark', out='b_dark_line', top='b_dark_line', back='b_vent')
        for i, (x0, y0) in enumerate(((5.25, 22.45), (6.3, 22.45), (5.25, 23.5), (6.3, 23.5))):
            B(x0, y0, 0.12 + i * 0.01, x0 + 0.85, y0 + 0.85, 0.3, 'b_grey', front='nozzle_red')         # 筒の口（赤い弾頭）


def backpack_grapple_anchor():
    """グラップルアンカー（T2）: 右の腰の後ろに巻き上げの胴、左の腰の後ろに爪のアンカーをしまう（暗い鋼と橙）"""
    m = _bp('backpack_grapple_anchor')
    m.mbox('body', (-4.1, 11.8, 1.17), (-2.6, 12.6, 1.38), 'b_dark')                                  # 腰の支え
    m.box('body', (-5.6, 11.2, 1.5), (-4.0, 13.4, 3.1), 'f_dark', out='f_orange', back='f_vent')      # 巻き上げの胴
    m.box('body', (-5.5, 11.0, 1.7), (-4.1, 13.6, 2.9), 'f_dark')
    m.box('body', (-5.75, 11.5, 1.8), (-5.6, 13.1, 2.8), 'f_orange')
    m.box('body', (-4.0, 11.9, 2.1), (-3.95, 12.7, 2.5), 'glow_orange')
    m.box('body', (4.0, 10.6, 1.45), (4.8, 13.2, 2.35), 'f_dark', out='f_hazard')                      # アンカーの軸
    for x0, z0 in ((4.05, 1.4), (4.55, 2.05)):                                                         # 爪
        m.box('body', (x0, 9.5, z0), (x0 + 0.22, 10.5, z0 + 0.3), 'f_orange')
    m.box('body', (4.1, 10.35, 1.5), (4.7, 10.6, 2.3), 'f_steel')


def backpack_prism_reflector():
    """プリズムリフレクター（T3）: 肩の後ろに扇に広がる結晶の板（透けて光る）。縁はシアンと桃"""
    m = _bp('backpack_prism_reflector')
    strut(m)
    m.mbox('body', (-5.2, 18.6, 1.25), (-4.02, 20.4, 2.8), 'b_white', out='b_white_vline')
    for k, ang in enumerate((12, 38, 64)):
        z = 2.0 + k * 0.38
        pr, pl = m.mbone(f'prism_{k}_{{s}}', 'body', (-4.6, 19.6, z + 0.12), (0, 12, ang))
        for b, s_ in ((pr, -1), (pl, 1)):
            m.box(b, (s_ * 4.6, 19.0, z), (s_ * 8.6, 20.2, z + 0.25), 'crystal')
            m.box(b, (s_ * 8.6, 19.3, z + 0.03), (s_ * 9.7, 19.9, z + 0.22), 'crystal')
        er, el = m.mbone(f'fx_prism_{k}_{{s}}', pr, (-4.6, 19.6, z + 0.12), None)
        for b, s_ in ((er, -1), (el, 1)):
            m.box(b, (s_ * 4.8, 20.22, z + 0.05), (s_ * 8.6, 20.36, z + 0.2), 'glow_pink' if k % 2 == 0 else 'glow_cyan')
            m.box(b, (s_ * 4.8, 18.84, z + 0.05), (s_ * 8.6, 18.98, z + 0.2), 'glow_cyan' if k % 2 == 0 else 'glow_pink')


def backpack_astral_halo():
    """アストラルハロー（T4）: 頭と背中の後ろに浮かぶ大きな金の輪（ゆっくり回り、残像が重なる）＋背中の小さな放射器"""
    m = _bp('backpack_astral_halo')
    strut(m)
    m.mbox('body', (-5.0, 15.5, 1.25), (-4.02, 19.5, 5.9), 'b_white', out='b_white_gold', top='g_gold')   # 左右の柱
    m.mbox('body', (-4.5, 18.6, 5.5), (-0.2, 19.2, 5.84), 'b_white', top='g_gold')                          # 横木
    m.box('body', (-0.25, 19.2, 5.62), (0.25, 21.2, 5.88), 'g_gold')                                      # 放射器の柱
    m.box('body', (-0.8, 21.2, 5.4), (0.8, 22.8, 6.2), 'b_white', front='glow_white', back='glow_white', top='g_gold')  # 放射器
    cy, cz = 22.0, 6.6
    ring = m.bone('spin_z_ghost_halo', 'body', (0, cy, cz))
    for k in range(16):
        seg = m.bone(f'halo_{k}', ring, (0, cy, cz), (0, 0, k * 22.5))
        dz = 0.05 if k % 2 else 0.0
        m.box(seg, (-1.5, cy + 7.0, cz - 0.22 - dz), (1.5, cy + 7.6, cz + 0.22 + dz), 'g_gold', bottom='glow_gold')
        if k % 2 == 0:
            m.box(seg, (-0.2, cy + 7.6, cz - 0.12), (0.2, cy + 8.5, cz + 0.12), 'glow_white')            # 外への光の棘


def backpack_resurrection_core():
    """リザレクションコア（T4）: 背中の後ろの白と金の揺りかごに、桃と白に脈打つ結晶のハート"""
    m = _bp('backpack_resurrection_core')
    strut(m)
    m.mbox('body', (-5.0, 15.8, 1.25), (-4.02, 19.0, 6.0), 'b_white', out='b_white_gold', top='g_gold')   # 左右の柱
    m.mbox('body', (-4.5, 17.3, 5.85), (-2.2, 17.7, 6.15), 'b_white')                                     # 揺りかごへの横木
    cz = 6.0
    m.mbox('body', (-2.25, 15.4, cz - 0.27), (-1.85, 19.8, cz + 0.27), 'g_gold')                          # 揺りかご
    m.box('body', (-2.25, 19.8, cz - 0.22), (2.25, 20.2, cz + 0.22), 'b_white', top='g_gold')
    m.box('body', (-2.25, 15.0, cz - 0.22), (2.25, 15.4, cz + 0.22), 'g_gold')
    m.box('body', (-0.3, 20.2, cz - 0.15), (0.3, 20.9, cz + 0.15), 'glow_gold')
    m.box('body', (-1.2, 16.5, cz - 0.4), (1.2, 18.7, cz + 0.4), 'crystal')                               # ハート
    m.mbox('body', (-1.45, 18.0, cz - 0.35), (-0.1, 19.2, cz + 0.35), 'crystal')
    m.box('body', (-0.6, 15.8, cz - 0.3), (0.6, 16.5, cz + 0.3), 'crystal')
    fx = m.bone('fx_pulse', 'body', (0, 17.5, cz))                                                       # 脈打つ光（発光だけ）
    m.box(fx, (-1.65, 16.6, cz - 0.08), (-1.55, 18.9, cz + 0.08), 'glow_pink')
    m.box(fx, (1.55, 16.6, cz - 0.08), (1.65, 18.9, cz + 0.08), 'glow_pink')
    m.box(fx, (-1.0, 19.3, cz - 0.08), (1.0, 19.4, cz + 0.08), 'glow_white')
    m.box(fx, (-0.5, 15.55, cz - 0.08), (0.5, 15.65, cz + 0.08), 'glow_white')


def backpack_accelerator():
    """アクセラレーター（T4）: 背中の左右に縦の輪のコイル 2 つ（電気の青い光）と山形のひれ"""
    m = _bp('backpack_accelerator')
    strut(m)
    cy, cz, xc = 17.5, 4.8, 5.5
    m.mbox('body', (-5.25, 17.1, 1.25), (-4.02, 17.9, 4.4), 'b_dark')                                     # 輪の軸の支え
    m.mbox('body', (-6.1, 17.1, 4.4), (-4.02, 17.9, 5.2), 'b_dark', out='glow_blue')                      # 軸
    for k in range(10):
        dx = 0.05 if k % 2 else 0.0
        sr, sl = m.mbone(f'coil_{k}_{{s}}', 'body', (-xc, cy, cz), (k * 36, 0, 0))
        m.box(sr, (-xc - 0.45 - dx, cy + 2.6, cz - 1.0), (-xc + 0.45 + dx, cy + 3.2, cz + 1.0), 'b_dark', bottom='glow_blue', top='a_blue')
        m.box(sl, (xc - 0.45 - dx, cy + 2.6, cz - 1.0), (xc + 0.45 + dx, cy + 3.2, cz + 1.0), 'b_dark', bottom='glow_blue', top='a_blue')
    for key, a in (('a', 35), ('b', -35)):                                                               # 山形のひれ（外へ V）
        fr, fl = m.mbone(f'chev_{key}_{{s}}', 'body', (-6.0, cy, cz), (0, 0, a))
        m.box(fr, (-6.0, cy - 0.2, cz - 0.2), (-7.9, cy + 0.2, cz + 0.2), 'b_white', top='glow_blue')
        m.box(fl, (6.0, cy - 0.2, cz - 0.2), (7.9, cy + 0.2, cz + 0.2), 'b_white', top='glow_blue')


def backpack_command_link():
    """コマンドリンク（T4）: 右の高いアンテナの柱、左のレーダーの皿（回る）、光る立体の表示板。紺と金"""
    m = _bp('backpack_command_link')
    strut(m)
    m.box('body', (-5.6, 14.5, 1.25), (-4.02, 17.6, 2.8), 'v_dark', out='g_gold')                         # 右の台
    m.box('body', (-5.2, 17.6, 1.65), (-4.6, 30.0, 2.25), 'v_dark')                                       # アンテナの柱
    for y in (20.0, 23.5, 27.0):
        m.box('body', (-5.35, y, 1.5), (-4.45, y + 0.4, 2.4), 'g_gold')
    m.box('body', (-5.3, 30.0, 1.55), (-4.5, 30.6, 2.35), 'glow_gold')
    m.box('body', (4.02, 14.5, 1.25), (5.9, 17.6, 2.8), 'v_dark', out='g_gold')                           # 左の台
    m.box('body', (5.3, 17.6, 1.8), (5.9, 26.5, 2.4), 'v_dark')                                           # 皿の柱
    d = m.bone('spin_y_dish', 'body', (5.6, 26.5, 2.1))
    t = m.bone('dish_tilt', d, (5.6, 26.5, 2.1), (-30, 0, 0))
    m.box(t, (4.6, 26.5, 1.1), (6.6, 26.9, 3.1), 'g_gold', top='lens', bottom='v_dark')
    m.box(t, (5.45, 26.9, 1.95), (5.75, 27.6, 2.25), 'v_dark')
    m.box(t, (5.42, 27.6, 1.92), (5.78, 27.9, 2.28), 'glow_gold')
    hp = m.bone('fx_holo', 'body', (-6.5, 22.0, 1.9))                                                    # 立体の表示板（発光だけ）
    m.box(hp, (-7.9, 21.0, 1.88), (-5.45, 23.2, 1.94), 'glow_cyan')
    m.box(hp, (-7.9, 23.3, 1.88), (-5.45, 23.4, 1.94), 'glow_gold')


def backpack_folding_cannon():
    m = model('backpack_folding_cannon')
    strut(m)
    cr, cl = m.mbone('cannon_{s}', 'body', (-4.8, 16.0, 2.1), (0, 0, -8))               # 頭から離れるよう外へ少し傾ける
    for b, s in ((cr, -1), (cl, 1)):
        def B(x0, y0, z0, x1, y1, z1, mat, **f):
            m.box(b, (s * x0, y0, z0), (s * x1, y1, z1), mat, **f)
        B(4.0, 14.0, 1.25, 5.6, 18.6, 3.0, 'b_dark', out='b_dark_vline', back='b_vent')    # 尾栓
        B(4.4, 18.6, 1.7, 5.2, 31.5, 2.5, 'b_grey', out='b_dark_vline')                    # 長い砲身
        B(4.3, 22.5, 1.6, 5.3, 23.5, 2.6, 'b_dark')                                        # 折りたたみの関節
        B(4.25, 31.5, 1.55, 5.35, 32.6, 2.65, 'b_dark', top='nozzle')                      # 砲口
        B(5.2, 19.5, 1.85, 5.5, 28.0, 2.35, 'b_white')                                     # 側面のレール
        B(5.5, 20.0, 1.95, 5.6, 27.5, 2.25, 'glow_cyan')


def backpack_ecm_pod():
    m = model('backpack_ecm_pod')
    strut(m)
    m.mbox('body', (-5.82, 15.02, 1.25), (-4.02, 19.02, 3.62), 'b_grey', out='b_dark_line', back='b_vent')
    # -x 側: お皿のアンテナ（ゆっくり回る）
    m.box('body', (-5.22, 19.02, 2.12), (-4.62, 19.82, 2.72), 'b_dark')
    d = m.bone('spin_y_dish', 'body', (-4.92, 19.8, 2.42))
    t = m.bone('dish_tilt', d, (-4.92, 19.8, 2.42), (-30, 0, 0))
    m.box(t, (-5.92, 19.82, 1.42), (-3.92, 20.22, 3.42), 'b_white', top='lens', bottom='b_grey')
    m.box(t, (-5.07, 20.22, 2.27), (-4.77, 21.02, 2.57), 'b_dark')
    m.box(t, (-5.1, 21.02, 2.24), (-4.74, 21.3, 2.6), 'glow_cyan')
    # +x 側: 長いアンテナ
    m.box('body', (5.0, 19.02, 2.9), (5.25, 27.5, 3.15), 'b_dark')
    m.box('body', (4.97, 27.5, 2.87), (5.28, 27.9, 3.18), 'glow_cyan')
    m.box('body', (4.6, 23.0, 2.95), (5.65, 23.2, 3.1), 'b_grey')
    m.box('body', (4.75, 25.0, 2.95), (5.5, 25.2, 3.1), 'b_grey')
    m.box('body', (4.4, 17.2, 3.62), (5.4, 18.4, 3.75), 'glow_cyan')


def backpack_tachyon_drive():
    m = model('backpack_tachyon_drive')
    strut(m)
    m.mbox('body', (-5.42, 15.52, 1.25), (-4.02, 20.02, 6.02), 'b_dark', out='b_dark_line', top='b_grey')  # 輪を支える左右の柱
    m.mbox('body', (-5.12, 16.22, 6.02), (-4.32, 19.32, 6.12), 'glow_cyan')
    cy, cz = 18.5, 6.3
    ring = m.bone('spin_z_ghost_ring', 'body', (0, cy, cz))
    for k in range(12):
        seg = m.bone(f'ring_{k}', ring, (0, cy, cz), (0, 0, k * 30))
        dz = 0.05 if k % 2 else 0.0
        m.box(seg, (-1.32, cy + 4.5, cz - 0.3 - dz), (1.32, cy + 5.3, cz + 0.3 + dz), 'b_dark', bottom='glow_cyan', front='b_dark_line', back='b_dark_line')
        m.box(seg, (-0.3, cy + 5.3, cz - 0.2), (0.3, cy + 5.6, cz + 0.2), 'glow_cyan')
    inner = m.bone('spin_z_inner', 'body', (0, cy, cz), None)                         # 内側の光の輪（逆向きに回る）
    for k in range(8):
        seg = m.bone(f'inner_{k}', inner, (0, cy, cz), (0, 0, k * 45 + 22.5))
        m.box(seg, (-1.2, cy + 3.5, cz - 0.12), (1.2, cy + 3.75, cz + 0.12), 'glow_cyan')


def backpack_memento_familiar():
    m = model('backpack_memento_familiar')
    strut(m)
    m.mbox('body', (-5.22, 16.52, 1.25), (-4.02, 19.52, 2.92), 'b_white', out='b_white_violet', back='b_grey')  # 小さな巣
    m.mbox('body', (-5.02, 15.92, 1.45), (-4.22, 16.52, 2.72), 'b_dark', bottom='nozzle_violet')
    m.mbox('body', (-5.32, 17.4, 1.7), (-5.22, 18.8, 2.5), 'glow_violet')
    m.mbox('body', (-4.92, 19.52, 1.65), (-4.32, 19.82, 2.52), 'b_grey')


frame_valkyrie()
frame_valkyrie_tier(2)
frame_valkyrie_tier(3)
frame_valkyrie_tier(4)
frame_scout()
frame_maintenance()
frame_seraph()
frame_striker()
frame_aegis()
frame_tempest()
frame_helios()
frame_nemesis()
frame_luminous()
frame_fortress()
frame_mirage()
backpack_thruster()
backpack_folding_cannon()
backpack_ecm_pod()
backpack_tachyon_drive()
backpack_memento_familiar()
backpack_thruster_mk2()
backpack_thruster_mk3()
backpack_thruster_mk4()
backpack_gallia_fortress()
backpack_sub_arm()
backpack_recon_drone()
backpack_spare_battery()
backpack_repair_drone()
backpack_micro_missile()
backpack_grapple_anchor()
backpack_prism_reflector()
backpack_astral_halo()
backpack_resurrection_core()
backpack_accelerator()
backpack_command_link()


# ------------------------------------------------------------------ 書き出し
def face_dims(a, b, f):
    sx, sy, sz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    return {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}[f]


def face_uv(mat, du, dv, uoff=0):
    u, v, n = TILES[mat]
    # 細い面は板の内側（縁や光の線の無い所）の 2 ドットだけ使う
    if du >= 0.7:
        uu, us = u, n
    else:
        uu, us = u + 1, 2
    if dv >= 0.7:
        vv, vs = v, n
    else:
        vv, vs = v + 1, 2
    if mat.startswith('glow') or mat.startswith('nozzle'):
        if du < 0.7:
            uu = u + n // 2 - 1
        if dv < 0.7:
            vv = v + n // 2 - 1
    return {'uv': [uu + uoff, vv], 'uv_size': [us, vs]}


def geo_json(m, prefix='', parent_map=None, uoff=0, tw=TEX):
    out = []
    for name in m.order:
        b = m.bones[name]
        o = {'name': prefix + name, 'pivot': [round(p, 3) for p in b['pivot']]}
        par = b['parent']
        if par:
            o['parent'] = prefix + par
        elif parent_map is not None:
            o['parent'] = parent_map(name)
        if b['rotation']:
            o['rotation'] = b['rotation']
        if b['cubes']:
            cubes = []
            for a, c, fm in b['cubes']:
                size = [round(c[i] - a[i], 3) for i in range(3)]
                uv = {f: face_uv(fm[f], *face_dims(a, c, f), uoff=uoff) for f in FACES}
                cubes.append({'origin': [round(v, 3) for v in a], 'size': size, 'uv': uv})
            o['cubes'] = cubes
        out.append(o)
    return out


def wrap(ident, bones, tw=TEX, th=TEX):
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {'identifier': f'geometry.{ident}', 'texture_width': tw, 'texture_height': th,
                            'visible_bounds_width': 3, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.25, 0]},
            'bones': bones,
        }],
    }


def butterfly():
    """蝶の羽（左 16 列: 羽の付け根 u=0 → 先 u=15、前 v=0 → 後ろ v=15）と胴（右 16 列の中央）"""
    img = Image.new('RGBA', (32, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            # 前の大きな羽（上半分）と後ろの小さな羽（下半分）
            fx, fy = x / 15.0, y / 15.0
            front = ((fx - 0.42) / 0.58) ** 2 + ((fy - 0.32) / 0.34) ** 2 <= 1.0 and fx > 0.02
            back = ((fx - 0.30) / 0.36) ** 2 + ((fy - 0.74) / 0.25) ** 2 <= 1.0 and fx > 0.02
            if not (front or back):
                continue
            edge = not (((fx - 0.42) / 0.50) ** 2 + ((fy - 0.32) / 0.27) ** 2 <= 1.0 or ((fx - 0.30) / 0.29) ** 2 + ((fy - 0.74) / 0.19) ** 2 <= 1.0)
            if edge:
                c = (250, 225, 255, 255)
            else:
                k = 1.0 - fx * 0.6
                c = (int(150 + 60 * k), int(70 + 40 * k), 255, int(150 + 80 * (1 - fx)))
            if (x == 9 and y in (3, 4)) or (x == 6 and y == 11):
                c = (255, 255, 255, 255)  # 羽の模様の光点
            img.putpixel((x, y), c)
    for y in range(2, 14):
        for x in range(23, 25):
            img.putpixel((x, y), (235, 210, 255, 255))
    return img


def coplanar_check(models):
    """兵姫の面と同じ平面に重なる面（ちらつき）を探す。回していない骨同士だけ"""
    path = os.path.join(ASSETS, 'geo', 'entity', 'valkyrie_type_v.geo.json')
    vb = json.load(open(path, encoding='utf-8'))['minecraft:geometry'][0]['bones']
    boxes = []
    for b in vb:
        if b.get('rotation'):
            continue
        for c in b.get('cubes', []):
            inf = c.get('inflate', 0)
            o, s = c['origin'], c['size']
            boxes.append((b['name'], [o[i] - inf for i in range(3)], [o[i] + s[i] + inf for i in range(3)]))
    warn = 0
    for m in models:
        for name in m.order:
            b = m.bones[name]
            if b['rotation'] or b['parent'] and m.bones[b['parent']]['rotation']:
                continue
            for a, c, _ in b['cubes']:
                for vn, lo, hi in boxes:
                    for ax in range(3):
                        for val in (a[ax], c[ax]):
                            for ov in (lo[ax], hi[ax]):
                                if abs(val - ov) < 0.015:
                                    o1, o2 = [i for i in range(3) if i != ax]
                                    if min(c[o1], hi[o1]) - max(a[o1], lo[o1]) > 0.02 and min(c[o2], hi[o2]) - max(a[o2], lo[o2]) > 0.02:
                                        print(f'  coplanar: {m.name}/{name} {a}-{c} axis {ax} with valkyrie {vn}')
                                        warn += 1
    print(f'coplanar warnings: {warn}')


def main():
    geo_dir = os.path.join(ASSETS, 'geo', 'entity', 'unit')
    tex_dir = os.path.join(ASSETS, 'textures', 'entity', 'unit')
    os.makedirs(geo_dir, exist_ok=True)
    os.makedirs(tex_dir, exist_ok=True)
    for m in MODELS:
        with open(os.path.join(geo_dir, m.name + '.geo.json'), 'w', encoding='utf-8') as f:
            json.dump(wrap('unit_' + m.name, geo_json(m)), f, indent=1)
    _base.save(os.path.join(tex_dir, 'valkyrie_units.png'))
    _glow.save(os.path.join(tex_dir, 'valkyrie_units_glowmask.png'))
    butterfly().save(os.path.join(tex_dir, 'memento_butterfly.png'))
    print(f'{len(MODELS)} models, {_slot[0]} tiles')
    coplanar_check(MODELS)

    if len(sys.argv) > 1 and sys.argv[1] == 'preview':
        # 兵姫の geo に重ねた確認用（テクスチャは兵姫 128 + 見本帳 128 を横に並べる）
        out = os.path.join(ROOT, 'build', 'unit_preview')
        os.makedirs(out, exist_ok=True)
        vpath = os.path.join(ASSETS, 'geo', 'entity', 'valkyrie_type_v.geo.json')
        vtex = Image.open(os.path.join(ASSETS, 'textures', 'entity', 'valkyrie_type_v.png')).convert('RGBA')
        tex = Image.new('RGBA', (256, 128), (0, 0, 0, 0))
        tex.paste(vtex, (0, 0))
        tex.paste(_base, (128, 0))
        tex.save(os.path.join(out, 'tex.png'))
        sets = {
            'valkyrie': ['frame_valkyrie_head', 'frame_valkyrie_chest', 'frame_valkyrie_legs', 'backpack_thruster'],
            'fortress': ['frame_fortress_head', 'frame_fortress_chest', 'frame_fortress_legs', 'backpack_folding_cannon'],
            'mirage': ['frame_mirage_head', 'frame_mirage_chest', 'frame_mirage_legs', 'backpack_ecm_pod'],
            'gallia': ['backpack_gallia_fortress'],
            'tachyon': ['backpack_tachyon_drive'],
            'scout': ['frame_scout_head', 'frame_scout_chest', 'frame_scout_legs'],
            'maintenance': ['frame_maintenance_head', 'frame_maintenance_chest', 'frame_maintenance_legs'],
            'seraph': ['frame_seraph_head', 'frame_seraph_chest', 'frame_seraph_legs'],
            'striker': ['frame_striker_head', 'frame_striker_chest', 'frame_striker_legs'],
            'aegis': ['frame_aegis_head', 'frame_aegis_chest', 'frame_aegis_legs'],
            'tempest': ['frame_tempest_head', 'frame_tempest_chest', 'frame_tempest_legs'],
            'helios': ['frame_helios_head', 'frame_helios_chest', 'frame_helios_legs'],
            'nemesis': ['frame_nemesis_head', 'frame_nemesis_chest', 'frame_nemesis_legs'],
            'luminous': ['frame_luminous_head', 'frame_luminous_chest', 'frame_luminous_legs'],
            'bp_sub_arm': ['backpack_sub_arm'],
            'bp_recon_drone': ['backpack_recon_drone'],
            'bp_spare_battery': ['backpack_spare_battery'],
            'bp_repair_drone': ['backpack_repair_drone'],
            'bp_micro_missile': ['backpack_micro_missile'],
            'bp_grapple_anchor': ['backpack_grapple_anchor'],
            'bp_prism_reflector': ['backpack_prism_reflector'],
            'bp_astral_halo': ['backpack_astral_halo'],
            'bp_resurrection_core': ['backpack_resurrection_core'],
            'bp_accelerator': ['backpack_accelerator'],
            'bp_command_link': ['backpack_command_link'],
            'memento': ['backpack_memento_familiar'],
            'valkyrie_mk2': ['frame_valkyrie_mk2_head', 'frame_valkyrie_mk2_chest', 'frame_valkyrie_mk2_legs'],
            'valkyrie_mk3': ['frame_valkyrie_mk3_head', 'frame_valkyrie_mk3_chest', 'frame_valkyrie_mk3_legs'],
            'valkyrie_mk4': ['frame_valkyrie_mk4_head', 'frame_valkyrie_mk4_chest', 'frame_valkyrie_mk4_legs'],
            'thruster_mk2': ['frame_valkyrie_head', 'frame_valkyrie_chest', 'frame_valkyrie_legs', 'backpack_thruster_mk2'],
            'thruster_mk3': ['frame_valkyrie_head', 'frame_valkyrie_chest', 'frame_valkyrie_legs', 'backpack_thruster_mk3'],
            'thruster_mk4': ['frame_valkyrie_head', 'frame_valkyrie_chest', 'frame_valkyrie_legs', 'backpack_thruster_mk4'],
        }
        byname = {m.name: m for m in MODELS}
        for key, names in sets.items():
            g = json.load(open(vpath, encoding='utf-8'))
            bones = g['minecraft:geometry'][0]['bones']
            for i, n in enumerate(names):
                bones += geo_json(byname[n], prefix=f'u{i}_', parent_map=lambda nm: nm, uoff=128)
            g['minecraft:geometry'][0]['description']['texture_width'] = 256
            with open(os.path.join(out, key + '.geo.json'), 'w', encoding='utf-8') as f:
                json.dump(g, f)
        print('preview geo written to', out)


if __name__ == '__main__':
    main()
