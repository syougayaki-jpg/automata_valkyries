"""
クロノ・ゲイザーの雑魚 3 種（索敵ドローン・自爆クモボット・重歩行戦車）の GeckoLib モデルとテクスチャを作る（2026-10-07）。

    python tools/gen_chrono_gazers.py

出力（assets/automata_valkyries/ の下）:
  geo/entity/<id>.geo.json                     … format 1.12.0、identifier geometry.<id>
  textures/entity/<id>.png（＋ _glowmask.png）  … 128x128。面ごとの UV を自動で詰め、色の板で塗る（光る所は発光マスクにも）

アニメーションの json は無い（Java のモデルが骨をコードで動かす）ので、骨の名前と pivot は Java 側と一致させること:
  chrono_scout_drone  : root > body > eye / rotor_left / rotor_right / antenna
  chrono_spider_bot   : root > body > core / leg_0..leg_5（leg_0〜2 が右 -X の前→後ろ、leg_3〜5 が左 +X の前→後ろ）
  chrono_heavy_walker : root > hull > cannon、root > leg_left / leg_right
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

    def box(self, bone, a, c, mat, **faces):
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
        self.by[bone]['cubes'].append(((x0, y0, z0), (x1, y1, z1), fm))

    def mbox(self, bone, a, c, mat, mirror_bone=None, **faces):
        """左右対称（-x 側を渡す。mirror_bone があれば +x 側はその骨へ）"""
        self.box(bone, a, c, mat, **faces)
        self.box(mirror_bone or bone, (-a[0], a[1], a[2]), (-c[0], c[1], c[2]), mat, **faces)


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
            for a, c, fm in b['cubes']:
                uv = {}
                for f in FACES:
                    dw, dh = face_dims(a, c, f)
                    w, h = max(1, int(math.ceil(dw - 1e-6))), max(1, int(math.ceil(dh - 1e-6)))
                    uu, vv = atlas.paint(fm[f], w, h)
                    uv[f] = {'uv': [uu, vv], 'uv_size': [w, h]}
                cubes.append({'origin': [round(v, 3) for v in a], 'size': [round(c[i] - a[i], 3) for i in range(3)], 'uv': uv})
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


if __name__ == '__main__':
    os.makedirs(os.path.join(ASSETS, 'geo', 'entity'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'textures', 'entity'), exist_ok=True)
    for fn in (scout_drone, spider_bot, heavy_walker):
        write(fn())
