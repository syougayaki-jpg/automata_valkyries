"""
クロノ・ゲイザーの雑魚 5 種（索敵ドローン・自爆クモボット・重歩行戦車・仮面の観測機・クロノ・トルーパー）の GeckoLib モデルとテクスチャを作る（2026-10-07）。

    python tools/gen_chrono_gazers.py

出力（assets/automata_valkyries/ の下）:
  geo/entity/<id>.geo.json                     … format 1.12.0、identifier geometry.<id>
  textures/entity/<id>.png（＋ _glowmask.png）  … 128x128。面ごとの UV を自動で詰め、色の板で塗る（光る所は発光マスクにも）

アニメーションの json は無い（Java のモデルが骨をコードで動かす）ので、骨の名前と pivot は Java 側と一致させること:
  chrono_scout_drone  : root > body > eye / rotor_left / rotor_right / antenna
  chrono_spider_bot   : root > body > core / leg_0..leg_5（leg_0〜2 が右 -X の前→後ろ、leg_3〜5 が左 +X の前→後ろ）
  chrono_heavy_walker : root > hull > cannon、root > leg_left / leg_right
  chrono_mask_drone   : root > head > eyes / mouth / ring / cables
  chrono_trooper      : root > body > head / belly_gun / right_arm > blade / left_arm、root > right_leg / left_leg
コードで回す骨そのものには初期の向きを付けない（脚の曲がりは子の骨 *_upper / *_thigh などに付ける）。
色は敵の決まり（DESIGN_VALKYRIE_VISUAL_DB §2.4）: 暗いガンメタル・紫の差し色・冷たい赤の発光。3 種とも赤い「目」のレンズを持つ。
座標は GeckoLib（Bedrock）の geo の座標（px）。正面は -Z、右は -X。
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries')
TEX = 128


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


# 素材: (地, 明, 暗, 模様, 光るか)
MAT = {
    'plate': (0x474C59, 0x6A7080, 0x23262E, 'panel', False),
    'armor': (0x474C59, 0x6A7080, 0x2A2E37, 'rivet', False),
    'metal': (0x2C3038, 0x434854, 0x15171C, 'plain', False),
    'dark': (0x23262E, 0x353A45, 0x121418, 'plain', False),
    'edge': (0x6A7080, 0x8A90A0, 0x474C59, 'plain', False),
    'vent': (0x2C3038, 0x434854, 0x15171C, 'vent', False),
    'purple': (0x5B2A9A, 0x7A44C0, 0x3A1A66, 'plain', False),
    'stripe': (0x474C59, 0x6A7080, 0x23262E, 'stripe', False),
    'eye': (0x5A0A18, 0xFF8FA0, 0xFF2A4A, 'eye', True),
    'glow': (0xFF2A4A, 0xFF8FA0, 0xB01830, 'glow', True),
    'slit': (0x1A0508, 0xFF8FA0, 0xFF2A4A, 'slit', True),
    'porcelain': (0xE8E4DC, 0xF6F3EE, 0xBFB8AC, 'crack', False),   # 仮面の観測機の白い面
    'hole': (0x0E0F14, 0x16181E, 0x08090C, 'plain', False),
    'yglow': (0xFFE14A, 0xFFF7B0, 0xD8B020, 'glow', True),        # フォトン剣
}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
SEM = {'front': 'north', 'back': 'south', 'top': 'up', 'bottom': 'down'}


class Model:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []
        self.by = {}

    def bone(self, name, parent=None, pivot=(0, 0, 0), rot=None):
        b = {'name': name, 'parent': parent, 'pivot': list(pivot), 'rotation': list(rot) if rot else None, 'cubes': []}
        self.bones.append(b)
        self.by[name] = b
        return name

    def box(self, bone, a, c, mat, rot=None, pivot=None, **faces):
        """rot があれば箱ごとの回転（度、Bedrock の向き）。pivot を省くと箱の中心で回す"""
        x0, x1 = sorted((a[0], c[0]))
        y0, y1 = sorted((a[1], c[1]))
        z0, z1 = sorted((a[2], c[2]))
        fm = {f: mat for f in FACES}
        cx = (x0 + x1) / 2
        for k, m in faces.items():
            if k == 'out':
                k = 'east' if cx < 0 else 'west'   # GeckoLib は X を反転するので -x 側の外の面が east
            elif k == 'sides':
                fm['east'] = fm['west'] = m
                continue
            fm[SEM.get(k, k)] = m
        cube = ((x0, y0, z0), (x1, y1, z1), fm)
        if rot:
            cube += (list(rot), list(pivot) if pivot else [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2])
        self.by[bone]['cubes'].append(cube)

    def mbox(self, bone, a, c, mat, mirror_bone=None, rot=None, pivot=None, **faces):
        """左右対称（-x 側を渡す。mirror_bone があれば +x 側はその骨へ）。rot・pivot も左右反転する"""
        self.box(bone, a, c, mat, rot=rot, pivot=pivot, **faces)
        mrot = (rot[0], -rot[1], -rot[2]) if rot else None
        mpiv = (-pivot[0], pivot[1], pivot[2]) if pivot else None
        self.box(mirror_bone or bone, (-a[0], a[1], a[2]), (-c[0], c[1], c[2]), mat, rot=mrot, pivot=mpiv, **faces)


# ------------------------------------------------------------------ 塗り・詰め込み
class Atlas:
    def __init__(self, seed):
        self.base = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 0))
        self.glow = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 0))
        self.x = self.y = self.row = 0
        self.rnd = random.Random(seed)

    def alloc(self, w, h):
        if self.x + w > TEX:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + h > TEX:
            raise RuntimeError('texture full')
        u, v = self.x, self.y
        self.x += w
        self.row = max(self.row, h)
        return u, v

    def px(self, u, v, c, glow=False):
        self.base.putpixel((u, v), rgb(c) + (255,))
        if glow:
            self.glow.putpixel((u, v), rgb(c) + (255,))

    def paint(self, mat, w, h):
        base, light, dark, pat, glows = MAT[mat]
        u, v = self.alloc(w, h)
        for y in range(h):
            for x in range(w):
                c = base
                edge_l = x == 0 or y == 0
                edge_d = x == w - 1 or y == h - 1
                if pat in ('eye', 'glow', 'slit'):
                    if pat == 'eye':
                        d = math.hypot((x + 0.5 - w / 2) / (w / 2), (y + 0.5 - h / 2) / (h / 2))
                        c = light if d < 0.35 else dark if d < 0.75 else base
                        g = d < 0.75
                    elif pat == 'slit':
                        mid = abs(y + 0.5 - h / 2) < max(0.6, h * 0.25)
                        c = (light if abs(x + 0.5 - w / 2) < w * 0.2 else dark) if mid else base
                        g = mid
                    else:
                        d = math.hypot((x + 0.5 - w / 2) / max(1, w / 2), (y + 0.5 - h / 2) / max(1, h / 2))
                        c = light if d < 0.45 else base if d < 0.9 else dark
                        g = True
                    self.px(u + x, v + y, c, g)
                    continue
                if w >= 2 and h >= 2:
                    if edge_l:
                        c = light
                    elif edge_d:
                        c = dark
                    else:
                        n = self.rnd.randint(-6, 6)
                        r, gg, b = rgb(base)
                        c = (max(0, min(255, r + n)) << 16) | (max(0, min(255, gg + n)) << 8) | max(0, min(255, b + n))
                        if pat == 'panel' and (y == h // 2 or (w >= 6 and x == w // 2)):
                            c = dark
                        elif pat == 'vent' and y % 2 == 1:
                            c = 0x0C0D10
                        elif pat == 'rivet' and (x in (1, w - 2) and y in (1, h - 2)):
                            c = light
                        elif pat == 'crack' and w >= 4 and h >= 4 and (x * 7 + y * 3 + w) % 23 == 0:
                            c = dark                                     # 細いひび
                        elif pat == 'crack' and w >= 6 and x == w // 3 and 1 <= y < h - 1:
                            c = 0xD8D2C6                                 # 合わせ目
                        elif pat == 'stripe' and abs(y + 0.5 - h / 2) < max(0.6, h * 0.18):
                            c = MAT['purple'][0]
                self.px(u + x, v + y, c)
        return u, v


def face_dims(a, c, f):
    sx, sy, sz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
    return {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}[f]


def write(m):
    atlas = Atlas(sum(map(ord, m.ident)))
    out = []
    for b in m.bones:
        o = {'name': b['name'], 'pivot': [round(p, 3) for p in b['pivot']]}
        if b['parent']:
            o['parent'] = b['parent']
        if b['rotation']:
            o['rotation'] = b['rotation']
        if b['cubes']:
            cubes = []
            for cube in b['cubes']:
                a, c, fm = cube[:3]
                uv = {}
                for f in FACES:
                    dw, dh = face_dims(a, c, f)
                    w, h = max(1, int(math.ceil(dw - 1e-6))), max(1, int(math.ceil(dh - 1e-6)))
                    uu, vv = atlas.paint(fm[f], w, h)
                    uv[f] = {'uv': [uu, vv], 'uv_size': [w, h]}
                co = {'origin': [round(v, 3) for v in a], 'size': [round(c[i] - a[i], 3) for i in range(3)], 'uv': uv}
                if len(cube) > 3:
                    co['pivot'] = [round(v, 3) for v in cube[4]]
                    co['rotation'] = cube[3]
                cubes.append(co)
            o['cubes'] = cubes
        out.append(o)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{m.ident}', 'texture_width': TEX, 'texture_height': TEX,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': out}]}
    with open(os.path.join(ASSETS, 'geo', 'entity', m.ident + '.geo.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(geo, f, indent=1)
    atlas.base.save(os.path.join(ASSETS, 'textures', 'entity', m.ident + '.png'))
    atlas.glow.save(os.path.join(ASSETS, 'textures', 'entity', m.ident + '_glowmask.png'))
    print(f'{m.ident}: {len(m.bones)} bones, texture used to y={atlas.y + atlas.row}')



def write_hd(m, tex, density, painter):
    """細かい版（使う側が選ぶ。既存の 5 種は write のまま）: 面の 1px を density 枚の texel で描き、tex×tex に高さ順で詰める。
    painter(base, glow, u, v, w, h, mat, face, rnd, ctx) が面を塗る（base・glow は PIL の画像。ctx は骨の名前・箱の位置と大きさ・回転）"""
    base = Image.new('RGBA', (tex, tex), (0, 0, 0, 0))
    glow = Image.new('RGBA', (tex, tex), (0, 0, 0, 0))
    rnd = random.Random(sum(map(ord, m.ident)))
    reqs = []
    for bi, b in enumerate(m.bones):
        for ci, cube in enumerate(b['cubes']):
            a, c = cube[0], cube[1]
            for f in FACES:
                dw, dh = face_dims(a, c, f)
                w = max(1, int(math.ceil(dw * density - 1e-6)))
                h = max(1, int(math.ceil(dh * density - 1e-6)))
                reqs.append((bi, ci, f, w, h))
    order = sorted(range(len(reqs)), key=lambda i: (-reqs[i][4], -reqs[i][3], i))
    sky = [0] * tex                                   # スカイライン（列ごとの高さ）で左下から詰める（棚より隙間が少ない）
    place = {}
    for i in order:
        bi, ci, f, w, h = reqs[i]
        best = None
        for x in range(0, tex - w + 1):
            yy = max(sky[x:x + w])
            if yy + h <= tex and (best is None or yy < best[1]):
                best = (x, yy)
        if best is None:
            raise RuntimeError('texture full')
        x, yy = best
        for k in range(x, x + w):
            sky[k] = yy + h
        place[(bi, ci, f)] = (x, yy, w, h)
    y, row = max(sky), 0
    for (bi, ci, f), (u, v, w, h) in place.items():
        cube = m.bones[bi]['cubes'][ci]
        ctx = {'bone': m.bones[bi]['name'], 'origin': cube[0], 'size': tuple(cube[1][i] - cube[0][i] for i in range(3)),
               'rot': cube[3] if len(cube) > 3 else None}
        painter(base, glow, u, v, w, h, cube[2][f], f, rnd, ctx)
    out = []
    for bi, b in enumerate(m.bones):
        o = {'name': b['name'], 'pivot': [round(p, 3) for p in b['pivot']]}
        if b['parent']:
            o['parent'] = b['parent']
        if b['rotation']:
            o['rotation'] = b['rotation']
        if b['cubes']:
            cubes = []
            for ci, cube in enumerate(b['cubes']):
                a, c = cube[0], cube[1]
                uv = {}
                for f in FACES:
                    u, v, w, h = place[(bi, ci, f)]
                    uv[f] = {'uv': [u, v], 'uv_size': [w, h]}
                co = {'origin': [round(t, 3) for t in a], 'size': [round(c[i] - a[i], 3) for i in range(3)], 'uv': uv}
                if len(cube) > 3:
                    co['pivot'] = [round(t, 3) for t in cube[4]]
                    co['rotation'] = cube[3]
                cubes.append(co)
            o['cubes'] = cubes
        out.append(o)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{m.ident}', 'texture_width': tex, 'texture_height': tex,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': out}]}
    with open(os.path.join(ASSETS, 'geo', 'entity', m.ident + '.geo.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(geo, f, indent=1)
    base.save(os.path.join(ASSETS, 'textures', 'entity', m.ident + '.png'))
    glow.save(os.path.join(ASSETS, 'textures', 'entity', m.ident + '_glowmask.png'))
    print(f'{m.ident}: {len(m.bones)} bones, {tex}x{tex} at {density} texels/px, used to y={y + row}')

# ------------------------------------------------------------------ 索敵ドローン
def scout_drone():
    m = Model('chrono_scout_drone')
    m.bone('root')
    m.bone('body', 'root', (0, 5, 0))
    m.box('body', (-3.5, 2, -3.5), (3.5, 8, 3.5), 'plate', top='armor', bottom='vent')          # 丸みのあるセンサーの胴
    m.box('body', (-2.8, 1.2, -2.8), (2.8, 2.05, 2.8), 'metal', bottom='vent')
    m.box('body', (-2.8, 7.95, -2.8), (2.8, 8.8, 2.8), 'edge', top='armor')
    m.box('body', (-3.8, 4.6, -3.8), (3.8, 5.4, 3.8), 'purple')                                  # 紫の帯
    m.box('body', (-2.6, 2.6, -3.9), (2.6, 7.4, -3.45), 'dark')                                 # 目の枠
    m.mbox('body', (-6.0, 6.6, -0.5), (-3.45, 7.3, 0.5), 'metal')                               # 回転翼の腕
    m.box('body', (-1.2, 0.4, -1.2), (1.2, 1.25, 1.2), 'glow')                                  # 下のセンサー灯
    m.bone('eye', 'body', (0, 5, -4.2))
    m.box('eye', (-1.9, 3.1, -4.6), (1.9, 6.9, -3.85), 'eye', sides='dark', top='dark', bottom='dark', back='dark')  # 大きな赤いレンズ
    for side, s in (('rotor_right', -1), ('rotor_left', 1)):
        m.bone(side, 'body', (s * 6, 7, 0))
        m.box(side, (s * 5.4, 6.7, -0.6), (s * 6.6, 7.9, 0.6), 'edge', top='glow')               # 軸
        m.box(side, (s * 3.6, 7.45, -0.35), (s * 8.4, 7.62, 0.35), 'dark')                       # 平らな羽（横）
        m.box(side, (s * 5.65, 7.66, -2.4), (s * 6.35, 7.83, 2.4), 'dark')
    m.bone('antenna', 'body', (0, 8.8, 1.5))
    m.box('antenna', (-0.25, 8.8, 1.25), (0.25, 11.6, 1.75), 'metal')
    m.box('antenna', (-0.45, 11.6, 1.05), (0.45, 12.3, 1.95), 'glow')
    return m


# ------------------------------------------------------------------ 自爆クモボット
def spider_bot():
    m = Model('chrono_spider_bot')
    m.bone('root')
    m.bone('body', 'root', (0, 5, 0))
    m.box('body', (-4.0, 3.5, -5.0), (4.0, 6.0, 5.0), 'plate', top='armor', bottom='vent')      # 低く平たい六角の車体
    m.box('body', (-5.0, 3.8, -3.5), (5.0, 5.7, 3.5), 'stripe', top='armor')
    m.box('body', (-3.0, 2.9, -4.0), (3.0, 3.55, 4.0), 'vent')
    m.box('body', (-1.3, 4.1, -5.45), (1.3, 5.6, -4.95), 'eye', sides='dark', top='dark', bottom='dark')  # 前の赤い目
    m.box('body', (-2.0, 3.9, 4.95), (2.0, 5.4, 5.4), 'vent')                                   # 後ろの排気
    m.bone('core', 'body', (0, 6, 0))
    m.box('core', (-2.8, 5.9, -2.8), (2.8, 6.35, 2.8), 'edge')                                  # 台座
    m.box('core', (-2.2, 6.3, -2.2), (2.2, 7.7, 2.2), 'glow')                                   # 光る赤いコアのドーム
    m.box('core', (-1.4, 7.65, -1.4), (1.4, 8.3, 1.4), 'glow')
    # 脚: 腰（車体の横）から外・上へ上がり、膝で折れて地面へ。曲がりは子の骨（*_upper / *_lower）に付ける
    up_len, up_rise, low_len, low_drop = 3.0, 30, 6.7, -100
    zs = (-3.2, 0.0, 3.2)
    yaws = (-30, 0, 30)
    for i in range(6):
        s = -1 if i < 3 else 1                       # 0〜2 が右（-X）、3〜5 が左（+X）
        z, yaw = zs[i % 3], yaws[i % 3]
        hip = (s * 4.6, 4.8, z)
        leg = m.bone(f'leg_{i}', 'body', hip)
        m.box(leg, (s * 4.4, 4.3, z - 0.55), (s * 5.2, 5.3, z + 0.55), 'edge')                  # 腰の関節
        up = m.bone(f'leg_{i}_upper', leg, hip, (0, s * -yaw, s * -up_rise))
        kx = s * (4.6 + up_len)
        m.box(up, (s * 4.6, 4.5, z - 0.3), (kx, 5.1, z + 0.3), 'metal', top='plate')
        m.box(up, (kx - s * 0.45, 4.35, z - 0.45), (kx + s * 0.45, 5.25, z + 0.45), 'glow')     # 膝の光る点
        lo = m.bone(f'leg_{i}_lower', up, (kx, 4.8, z), (0, 0, s * -low_drop))
        m.box(lo, (kx, 4.55, z - 0.25), (kx + s * low_len, 5.05, z + 0.25), 'metal', top='plate')
        m.box(lo, (kx + s * (low_len - 0.1), 4.45, z - 0.35), (kx + s * (low_len + 0.5), 5.15, z + 0.35), 'dark')  # 足先
    return m


# ------------------------------------------------------------------ 重歩行戦車
def heavy_walker():
    m = Model('chrono_heavy_walker')
    m.bone('root')
    m.bone('hull', 'root', (0, 22, 0))
    m.box('hull', (-7.0, 20.0, -6.0), (7.0, 30.0, 6.0), 'plate', top='armor', back='vent')     # 装甲の胴
    m.box('hull', (-6.0, 21.0, -7.0), (6.0, 28.5, -5.95), 'armor')                             # 前の装甲板
    m.box('hull', (-4.0, 25.0, -7.35), (4.0, 26.6, -6.95), 'slit', sides='dark', top='dark', bottom='dark')  # 赤い目のスリット
    m.box('hull', (-6.0, 29.95, -5.0), (6.0, 31.0, 5.0), 'vent', sides='metal', front='metal', back='metal')  # 上の甲板（放熱）
    m.mbox('hull', (-7.6, 21.0, -5.0), (-6.95, 29.0, 5.0), 'stripe', out='stripe')             # 横の装甲（紫の帯）
    m.box('hull', (-5.0, 22.0, 5.95), (5.0, 28.0, 7.0), 'vent', sides='metal', top='metal', bottom='metal')   # 後ろの排気
    m.box('hull', (-5.0, 18.8, -4.0), (5.0, 20.05, 4.0), 'metal', bottom='vent')               # 腹
    m.box('hull', (-6.4, 28.5, -7.1), (6.4, 29.2, -5.9), 'purple')                             # 前の紫の縁
    m.bone('cannon', 'hull', (-4.0, 31.0, 0.0))                                                # 右上の砲（-Z へ）
    m.box('cannon', (-5.6, 31.0, -2.2), (-2.4, 33.6, 2.2), 'plate', top='armor', out='stripe')
    m.box('cannon', (-4.6, 31.8, -12.0), (-3.4, 33.0, -2.15), 'metal', top='plate')
    m.box('cannon', (-4.9, 31.5, -13.0), (-3.1, 33.3, -11.95), 'edge', front='eye')            # 砲口（赤い光）
    m.box('cannon', (-4.8, 31.7, -7.0), (-3.2, 33.1, -6.0), 'purple')                          # 砲身の紫の輪
    # 逆関節の脚: 腰 (±6,22,0) → 膝は後ろ (z 4, y 13) → 足首は前 (z -2, y 3) → 前へ広い足（地面 y 0）
    t_ang, s_rel, f_rel = 24, -55, 31
    t_len, s_len = 9.85, 11.66
    for name, s in (('leg_right', -1), ('leg_left', 1)):
        x = s * 6
        m.bone(name, 'root', (x, 22, 0))
        m.box(name, (s * 7.0, 20.5, -1.8), (s * 8.4, 23.5, 1.8), 'edge', out='purple')           # 腰の関節
        th = m.bone(name + '_thigh', name, (x, 22, 0), (t_ang, 0, 0))
        m.box(th, (x - 1.2, 22 - t_len, -1.2), (x + 1.2, 22, 1.2), 'armor', back='plate')
        ky = 22 - t_len
        m.box(th, (x - 1.4, ky - 0.75, -1.0), (x + 1.4, ky + 0.75, 1.0), 'edge', back='glow', sides='glow')   # 膝（光る点）
        sh = m.bone(name + '_shin', th, (x, ky, 0), (s_rel, 0, 0))
        m.box(sh, (x - 1.0, ky - s_len, -0.9), (x + 1.0, ky, 0.9), 'metal', front='plate')
        ay = ky - s_len
        ft = m.bone(name + '_foot', sh, (x, ay, 0), (f_rel, 0, 0))
        m.box(ft, (x - 1.2, ay - 1.25, -1.0), (x + 1.2, ay + 0.3, 1.0), 'edge')                  # 足首
        m.box(ft, (x - 2.5, ay - 2.51, -4.5), (x + 2.5, ay - 1.3, 2.5), 'armor', top='plate')    # 広い足
        m.box(ft, (x - 2.2, ay - 2.4, -5.1), (x + 2.2, ay - 1.45, -4.45), 'dark')                # つま先
    return m


# ------------------------------------------------------------------ 仮面の観測機
def mask_drone():
    """白い人の顔の仮面（目と口は暗い穴で、奥に赤い光の切れ目）。後ろは機械: 薄い輪の推進器と、垂れ下がる暗いケーブルの束"""
    m = Model('chrono_mask_drone')
    m.bone('root')
    m.bone('head', 'root', (0, 8, 0))
    z0, z1 = -1.5, -0.5                                                                       # 面の板（穴の周りを組む）
    m.box('head', (-4.5, 14.0, z0 + 0.1), (4.5, 15.0, z1), 'porcelain')                        # 額の上（丸み）
    m.box('head', (-6.0, 10.0, z0), (6.0, 14.0, z1), 'porcelain')                              # 額
    m.box('head', (-1.5, 8.5, z0), (1.5, 10.0, z1), 'porcelain')                               # 眉間
    m.mbox('head', (-6.0, 8.5, z0), (-4.0, 10.0, z1), 'porcelain')                            # 目の外
    m.box('head', (-6.0, 5.5, z0), (6.0, 8.5, z1), 'porcelain')                                # 頬・鼻の高さ
    m.box('head', (-0.6, 6.0, z0 - 0.45), (0.6, 8.6, z0), 'porcelain')                         # 鼻筋
    m.mbox('head', (-5.5, 4.5, z0), (-2.0, 5.5, z1), 'porcelain')                             # 口の横
    m.box('head', (-4.5, 2.0, z0), (4.5, 4.5, z1), 'porcelain')                                # 顎
    m.mbox('head', (-5.5, 2.8, z0 + 0.05), (-4.5, 4.5, z1 - 0.05), 'porcelain')               # 顎の角
    m.box('head', (-5.8, 2.6, z1), (5.8, 14.6, 0.5), 'hole', sides='dark', top='dark', bottom='dark', back='dark')   # 奥の暗い板（穴から見える）
    m.box('head', (-4.5, 4.0, 0.5), (4.5, 12.0, 2.5), 'plate', back='vent', top='armor')      # 後ろの機械の箱
    m.box('head', (-3.0, 3.0, 0.5), (3.0, 4.05, 2.0), 'metal')
    m.bone('eyes', 'head', (0, 9.25, -0.57))
    m.mbox('eyes', (-3.7, 8.95, -0.62), (-1.8, 9.55, -0.52), 'glow')                           # 目の奥の赤い切れ目
    m.bone('mouth', 'head', (0, 5.0, -0.57))
    m.box('mouth', (-1.7, 4.8, -0.62), (1.7, 5.2, -0.52), 'glow')
    cy, cz = 8.0, 3.2
    m.bone('ring', 'head', (0, cy, cz))                                                        # 後ろの薄い輪の推進器（+Z 向き）
    for k in range(12):
        seg = m.bone(f'ring_{k}', 'ring', (0, cy, cz), (0, 0, k * 30))
        dz = 0.04 if k % 2 else 0.0
        m.box(seg, (-1.15, cy + 3.6, cz - 0.3 - dz), (1.15, cy + 4.3, cz + 0.3 + dz), 'metal', bottom='edge')
        if k % 3 == 0:
            m.box(seg, (-0.3, cy + 3.75, cz + 0.3 + dz), (0.3, cy + 4.15, cz + 0.4 + dz), 'glow')   # 赤い光の点
    m.box('ring', (-0.6, cy - 0.6, 2.45), (0.6, cy + 0.6, cz + 0.1), 'dark', back='glow')         # 輪の軸
    m.bone('cables', 'head', (0, 4.0, 1.5))                                                     # 垂れ下がるケーブル（ベール）
    for i, (x, z, ln) in enumerate(((-3.2, 1.0, 2.6), (-2.0, 1.7, 3.0), (-0.8, 1.1, 3.2), (0.4, 1.8, 2.9), (1.6, 1.0, 3.1), (2.8, 1.6, 2.5))):
        m.box('cables', (x - 0.25, 4.0 - ln, z - 0.25), (x + 0.25, 4.05 + i * 0.01, z + 0.25), 'hole')
        if i % 2 == 0:
            m.box('cables', (x - 0.3, 4.0 - ln - 0.3, z - 0.3), (x + 0.3, 4.0 - ln, z + 0.3), 'glow')   # 先の赤い光
    return m


# ------------------------------------------------------------------ クロノ・トルーパー
def trooper():
    """人型の兵士ロボ: 赤い一つ目のバイザー、腹の扉の奥に回転式の多銃身砲、右手に黄色く光るフォトン剣"""
    m = Model('chrono_trooper')
    m.bone('root')
    m.bone('body', 'root', (0, 13, 0))
    m.box('body', (-3.5, 12.0, -2.0), (3.5, 14.2, 2.0), 'metal', front='vent')                # 腰
    m.box('body', (-3.0, 14.0, -1.0), (3.0, 19.2, 2.0), 'dark', front='vent')                 # 腹（奥へ引っ込む。砲の入る所）
    m.mbox('body', (-3.2, 14.0, -2.4), (-2.95, 19.2, 2.0), 'metal')                           # 腹の横の枠
    m.box('body', (-4.0, 19.0, -2.5), (4.0, 25.0, 2.5), 'armor', front='stripe', back='vent') # 胸
    m.box('body', (-3.0, 23.6, -2.9), (3.0, 24.6, -2.45), 'purple')                            # 胸の紫の帯
    m.box('body', (-1.0, 24.95, -1.0), (1.0, 26.05, 1.0), 'metal')                             # 首
    m.bone('head', 'body', (0, 26, 0))
    m.box('head', (-2.5, 26.0, -2.5), (2.5, 31.0, 2.5), 'plate', top='armor')
    m.box('head', (-1.9, 28.0, -2.9), (1.9, 29.1, -2.45), 'slit', sides='dark', top='dark', bottom='dark')   # 赤い一つ目のバイザー
    m.box('head', (-2.7, 27.0, -1.5), (2.7, 28.0, 1.5), 'metal')                               # 耳の装甲
    m.box('head', (-0.4, 30.95, -1.2), (0.4, 32.6, 2.0), 'purple')                             # 頭の上の紫のひれ
    m.box('head', (-0.2, 32.55, 1.2), (0.2, 33.0, 1.6), 'glow')
    m.bone('belly_gun', 'body', (0, 17, -2.5))                                                 # 回転式の多銃身砲（Z で回す）
    # 腹の砲は常に露出（2026-10-07 ユーザー指定「腹部のガトリングは常に露出するように」。扉は無くし、銃身を前へ突き出す）
    m.box('belly_gun', (-0.7, 16.3, -6.4), (0.7, 17.7, -0.95), 'dark', front='glow')           # 中心（同日「もっと前に突き出して」でさらに長く）
    for k in range(6):
        a = math.radians(k * 60 + 30)
        bx, by = 1.05 * math.cos(a), 17 + 1.05 * math.sin(a)
        m.box('belly_gun', (bx - 0.27, by - 0.27, -7.2 + (k % 2) * 0.02), (bx + 0.27, by + 0.27, -0.95), 'metal', front='hole')
    m.box('belly_gun', (-1.6, 15.4, -1.2), (1.6, 18.6, -0.98), 'edge')                        # 銃身の束ね板
    m.box('belly_gun', (-1.55, 15.45, -3.6), (1.55, 18.55, -3.3), 'edge')                     # 途中の束ね輪
    m.box('belly_gun', (-1.6, 15.4, -6.3), (1.6, 18.6, -5.9), 'edge', front='glow')           # 銃口側の束ね輪（前に赤い光）
    for name, s in (('right_arm', -1), ('left_arm', 1)):
        m.bone(name, 'body', (s * 5, 24, 0))
        m.box(name, (s * 6.6, 22.4, -2.0), (s * 3.9, 25.3, 2.0), 'armor', top='purple', out='stripe')   # 肩当て
        m.box(name, (s * 6.0, 18.0, -1.2), (s * 4.0, 22.5, 1.2), 'metal')                        # 上腕
        m.box(name, (s * 6.2, 13.5, -1.4), (s * 3.8, 18.05, 1.4), 'plate', out='armor')           # 前腕
        m.box(name, (s * 5.9, 12.0, -1.2), (s * 4.1, 13.55, 1.2), 'dark')                          # 拳
    m.bone('blade', 'right_arm', (-5.0, 12.75, 0))                                             # フォトン剣（拳から前・下へ）
    t = m.bone('blade_tilt', 'blade', (-5.0, 12.75, 0), (-60, 0, 0))
    m.box(t, (-5.4, 9.9, -0.4), (-4.6, 13.3, 0.4), 'dark')                                     # 柄
    m.box(t, (-6.0, 9.5, -0.9), (-4.0, 9.95, 0.9), 'edge', bottom='glow')                      # 鍔
    m.box(t, (-5.35, -4.5, -0.18), (-4.65, 9.5, 0.18), 'yglow')                                # 黄色く光る刃（14px）
    m.box(t, (-5.15, -5.1, -0.1), (-4.85, -4.45, 0.1), 'yglow')
    for name, s in (('right_leg', -1), ('left_leg', 1)):
        m.bone(name, 'root', (s * 2, 13, 0))
        m.box(name, (s * 3.5, 7.0, -1.6), (s * 0.4, 13.0, 1.6), 'armor', out='stripe')           # 太もも
        m.box(name, (s * 3.3, 6.2, -2.1), (s * 0.6, 7.6, -1.4), 'purple')                         # 膝当て
        m.box(name, (s * 3.3, 1.2, -1.4), (s * 0.6, 7.05, 1.4), 'plate', front='armor')           # 脛
        m.box(name, (s * 3.6, 0.0, -3.0), (s * 0.3, 1.3, 1.8), 'metal', top='edge')               # 足
    return m


if __name__ == '__main__':
    os.makedirs(os.path.join(ASSETS, 'geo', 'entity'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'textures', 'entity'), exist_ok=True)
    for fn in (scout_drone, spider_bot, heavy_walker, mask_drone, trooper):
        write(fn())
