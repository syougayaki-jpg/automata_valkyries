"""AEC 級英雄機「弾幕特化【黒死蝶 / メメントモリ】」の形とテクスチャ（2026-10-10 ユーザー指定「巨大な機械の黒い蝶」→「生物的な目はなく機械的な冷たい印象で」）。

  geo/entity/memento_mori.geo.json
  textures/entity/memento_mori.png（＋ _glowmask.png）… 512x512。塗り・詰め込みは gen_chrono_gazers.py の write を使う

形: 機械仕掛けの黒い蝶。実寸の半分で作り、描画（client/MementoMoriRenderer）で 2 倍にする（翼を広げて約 10 ブロック）。
生き物らしい目は付けず、冷たい機械の印象にする: 光は冷たい青紫と白（第 2 形態だけ赤）。
  root > body（角張った装甲の胸と、冷たく光る核。面取りした頭に横一文字の感知器の細い光、触角の代わりの金属の柱。
              腹は排気口のある装甲板の節と、先の金属の放射器。脚の代わりに折りたたんだ角張った支柱）
       > wing_fl / wing_fr / wing_hl / wing_hr（前翅・後翅。左右。r は -X 側）… 第 1 形態の冷たい青紫の光
       > wing_fl_red など（同じ形で赤く光る翅。第 2 形態で入れ替える）
  翅は薄い水平の板（厚み 0.3）で、上下の面に翅の形の絵を描き、形の外は透けて抜ける（描画は entityCutoutNoCull）。
  翅の絵: 黒い鋼の板を、まっすぐな継ぎ目で区切った格子のパネル。直角に折れる回路の光の線、目玉の代わりの菱形の放射口（光る縁と暗い芯）、
          外の縁に光る点の列、縁の光る線
"""
import math

import gen_chrono_gazers as g

g.TEX = 512
COLD, COLD_L, COLD_D = 0x7A8CFF, 0xDCE4FF, 0x3A4AB0                     # 冷たい青紫の光（明るい所・暗い所）
g.MAT.update({
    'shell': (0x14161E, 0x2A2E3A, 0x07080C, 'rivet', False),     # 胸・頭の黒い装甲（鋲）
    'plate': (0x1C1F2A, 0x343A4A, 0x0C0E14, 'panel', False),     # 腹の板
    'joint': (0x4A505E, 0x7A8292, 0x22262E, 'plain', False),     # 柱・関節の金属
    'vent': (0x101218, 0x262A34, 0x050608, 'vent', False),       # 排気口
    'cold': (COLD, COLD_L, COLD_D, 'glow', True),                # 冷たく光る
    'white': (0xE8EEFF, 0xFFFFFF, 0xA8B4D8, 'glow', True),       # 白く光る
    'crimson': (0xFF2A4A, 0xFFB0C0, 0xA0102A, 'glow', True),     # 赤く光る（第 2 形態）
    'sensor': (0x05060A, COLD_L, COLD, 'slit', True),            # 横一文字の感知器（暗い地に細い光）
})

_orig_paint = g.Atlas.paint


def _wing_inside(x, y, kind):
    """翅の形（x: 付け根 0 → 先 1、y: 前の縁 0 → 後ろ 1）。中なら True"""
    if kind == 'fore':
        # 前翅: 三角に近い形。前の縁は付け根の少し後ろから先へ向かって前へ、外の縁は先から付け根の後ろへ斜めに下がる
        front = 0.30 * (1.0 - x) ** 1.4
        back = 1.0 - 0.88 * x ** 1.5
        return front <= y <= back and x <= 0.99
    # 後翅: 丸い扇の形と、外の縁から後ろへ伸びる燕尾
    front = 0.02 + 0.06 * x
    back = 0.50 + 0.38 * math.sin(math.pi * min(1.0, x / 0.86))
    if 0.50 <= x <= 0.66:
        back = max(back, 1.0 - 5.0 * abs(x - 0.58))                   # 燕尾
    return front <= y <= back and x <= 0.86


def _paint_wing(atlas, u, v, w, h, kind, flip, red, vflip=False):
    base, seam, light = 0x0C0E14, 0x262A36, 0x343A4A
    glow = 0xFF2A4A if red else COLD
    glow_l = 0xFFB0C0 if red else COLD_L

    def coords(i, j):
        x = ((w - 1 - i) if flip else i) / max(1, w - 1)
        y = ((h - 1 - j) if vflip else j) / max(1, h - 1)
        return x, y

    def inside(i, j):
        if not (0 <= i < w and 0 <= j < h):
            return False
        x, y = coords(i, j)
        return _wing_inside(x, y, kind)

    node = (0.55, 0.40, 0.11) if kind == 'fore' else (0.42, 0.48, 0.10)       # 菱形の放射口（中心 x, y、大きさ）
    cw, ch = max(3, w // 5), max(3, h // 4)                                     # 格子のパネルの大きさ
    for j in range(h):
        for i in range(w):
            if not inside(i, j):
                continue
            x, y = coords(i, j)
            n = atlas.rnd.randint(-2, 2)
            r, gg, b = g.rgb(base)
            c = (max(0, r + n) << 16) | (max(0, gg + n) << 8) | max(0, b + n)
            on = False
            # 格子のパネル: まっすぐな継ぎ目と、継ぎ目の明るい縁（板の重なり）
            if i % cw == 0 or j % ch == 0:
                c = seam
            elif i % cw == 1 or j % ch == 1:
                c = light
            # 直角に折れる回路の光の線: 付け根から外へ 2 本、途中で前後へ折れる
            for k, (yy, xb) in enumerate(((0.32, 0.38), (0.64, 0.62))):
                y_line = yy + (0.12 if x > xb else 0.0) * (1 if k == 0 else -1)
                if abs(y - y_line) * h < 0.6 and 0.05 < x < xb + 0.3:
                    c, on = glow, True
                if abs(x - xb) * w < 0.6 and min(yy, y_line) - 0.01 <= y <= max(yy, y_line) + 0.01:
                    c, on = glow, True
            # 菱形の放射口（目玉の紋の代わり）: 光る縁と暗い芯、芯の小さな白い光
            d = abs(x - node[0]) * 1.3 + abs(y - node[1])
            if d < node[2]:
                if d > node[2] * 0.66:
                    c, on = glow, True
                elif d > node[2] * 0.3:
                    c = 0x1A1E28
                else:
                    c, on = glow_l, True
            # 外の縁の光る点の列と、縁の光る線
            edge_d = 0
            for k in range(1, 4):
                if not (inside(i - k, j) and inside(i + k, j) and inside(i, j - k) and inside(i, j + k)):
                    edge_d = k
                    break
            if edge_d == 3 and (i + j) % 4 == 0 and x > 0.35:
                c, on = glow_l, True
            if edge_d == 1:
                c, on = glow, True
            atlas.px(u + i, v + j, c, on)


def _paint(self, mat, w, h):
    if mat == 'none':
        return self.alloc(w, h)
    if isinstance(mat, str) and mat.startswith('wing:'):
        _, kind, flip, red, vflip = mat.split(':')
        u, v = self.alloc(w, h)
        _paint_wing(self, u, v, w, h, kind, flip == '1', red == '1', vflip == '1')
        return u, v
    return _orig_paint(self, mat, w, h)


g.Atlas.paint = _paint

# 翅の大きさ（px。実寸の半分）: 前翅の長さ・幅、後翅の長さ・幅、付け根の高さ
FORE_L, FORE_W, HIND_L, HIND_W = 38.0, 30.0, 30.0, 28.0
BODY_Y = 12.0
UP_VFLIP, DOWN_VFLIP = '1', '0'   # 上の面・下の面の絵の前後の向き（プレビューで確かめた）


def wing(m, name, sx, kind, red):
    """翅 1 枚: 付け根（胸の横）から外（sx）へ、薄い水平の板。上の面と下の面に翅の絵（下の面は前後が逆なので向きを変える）"""
    root_x = sx * 3.0
    length, width = (FORE_L, FORE_W) if kind == 'fore' else (HIND_L, HIND_W)
    z0 = -14.0 if kind == 'fore' else 2.0                                   # 前翅は前、後翅は後ろ
    m.bone(name, 'root', (root_x, BODY_Y, z0 + (8 if kind == 'fore' else 4)))
    x0, x1 = sorted((root_x, root_x + sx * length))
    flip = '1' if sx < 0 else '0'
    up = 'wing:%s:%s:%d:%s' % (kind, flip, 1 if red else 0, UP_VFLIP)
    down = 'wing:%s:%s:%d:%s' % (kind, flip, 1 if red else 0, DOWN_VFLIP)
    m.box(name, (x0, BODY_Y - 0.15, z0), (x1, BODY_Y + 0.15, z0 + width), 'none', up=up, down=down)
    if not red:                                                             # 付け根の関節の箱と、翅の前の縁の骨（金属）
        j0, j1 = sorted((root_x, root_x + sx * 4))
        m.box(name, (j0, BODY_Y - 1.2, z0 + (5 if kind == 'fore' else 1)), (j1, BODY_Y + 1.2, z0 + (11 if kind == 'fore' else 6)), 'joint')
        r0, r1 = sorted((root_x + sx * 2, root_x + sx * length * 0.75))
        m.box(name, (r0, BODY_Y + 0.15, z0 + 0.6), (r1, BODY_Y + 0.75, z0 + 1.6), 'joint')


def mementomori():
    m = g.Model('memento_mori')
    m.reuse = {'wing:%s:%s:%s:%s' % (k, f, r, v) for k in ('fore', 'hind') for f in '01' for r in '01' for v in '01'}   # 翅の上下の面は同じ絵を使い回す
    m.bone('root')
    m.bone('body', 'root', (0, BODY_Y, 0))
    # 胸: 角張った黒い装甲の塊（上の段・横の張り出し）と、背の冷たく光る核、横の光る線
    m.box('body', (-3.5, BODY_Y - 3, -8), (3.5, BODY_Y + 3.5, 2), 'shell', top='plate')
    m.box('body', (-2.6, BODY_Y + 3.5, -7), (2.6, BODY_Y + 4.4, 1), 'plate')
    m.box('body', (-4.2, BODY_Y - 1.5, -6), (4.2, BODY_Y + 1.5, -1), 'shell')                 # 翅の付け根の張り出し
    m.box('body', (-1.6, BODY_Y + 3.0, -5.5), (1.6, BODY_Y + 4.8, -2.5), 'cold')              # 背の核
    m.box('body', (-0.6, BODY_Y + 4.8, -4.6), (0.6, BODY_Y + 5.3, -3.4), 'white')             # 核の白い芯
    for sx in (-1, 1):
        a0, a1 = sorted((sx * 4.2, sx * 4.5))
        m.box('body', (a0, BODY_Y - 0.6, -6), (a1, BODY_Y + 0.3, -1), 'cold')                # 張り出しの横の光る線
    # 頭: 面取りした装甲の頭（段々に細く）、横一文字の感知器の細い光（目は付けない）
    m.box('body', (-2.8, BODY_Y - 2.0, -11.5), (2.8, BODY_Y + 2.4, -8), 'shell')
    m.box('body', (-2.2, BODY_Y - 1.4, -13.0), (2.2, BODY_Y + 1.8, -11.5), 'shell')
    m.box('body', (-1.4, BODY_Y - 0.8, -14.0), (1.4, BODY_Y + 1.0, -13.0), 'joint')
    m.box('body', (-2.3, BODY_Y + 0.2, -13.08), (2.3, BODY_Y + 0.9, -12.9), 'sensor', north='sensor')
    # 触角の代わり: 頭の上の 2 本の金属の柱（まっすぐ前上へ）と、先の小さな冷たい光
    for sx in (-1, 1):
        for k in range(4):
            y = BODY_Y + 2.4 + k * 1.8
            z = -10.5 - k * 2.2
            a0, a1 = sorted((sx * (1.0 + k * 0.5), sx * (1.5 + k * 0.5)))
            m.box('body', (a0, y, z - 0.25), (a1, y + 1.9, z + 0.25), 'joint')
        t0, t1 = sorted((sx * 2.7, sx * 3.5))
        m.box('body', (t0, BODY_Y + 9.6, -19.6), (t1, BODY_Y + 10.4, -18.8), 'cold')
    # 腹: 後ろへ細くなる 6 つの装甲板の節（横に排気口）、節の間に冷たく光る輪、先の金属の放射器
    for k in range(6):
        hw = 3.0 - k * 0.38
        z = 2 + k * 3.4
        m.box('body', (-hw, BODY_Y - hw * 0.8, z), (hw, BODY_Y + hw * 0.8, z + 3.0), 'plate', top='shell', sides='vent')
        m.box('body', (-hw * 0.85, BODY_Y - hw * 0.65, z + 3.0), (hw * 0.85, BODY_Y + hw * 0.65, z + 3.4), 'cold')
    m.box('body', (-0.9, BODY_Y - 0.9, 22.4), (0.9, BODY_Y + 0.9, 24.0), 'joint')
    m.box('body', (-0.5, BODY_Y - 0.5, 24.0), (0.5, BODY_Y + 0.5, 25.0), 'white')            # 放射器の白い光
    # 脚の代わり: 胸の下に折りたたんだ角張った支柱（3 対）
    for sx in (-1, 1):
        for k, z in enumerate((-6.5, -3.0, 0.5)):
            a0, a1 = sorted((sx * 2.5, sx * 5.0))
            m.box('body', (a0, BODY_Y - 3.8, z - 0.5), (a1, BODY_Y - 3.0, z + 0.5), 'joint')
            b0, b1 = sorted((sx * 4.4, sx * 5.4))
            m.box('body', (b0, BODY_Y - 5.6, z - 0.5), (b1, BODY_Y - 3.0, z + 0.5), 'shell')
    # 翅（冷たい青紫の光の翅と、同じ形で赤く光る翅）
    for red in (False, True):
        suffix = '_red' if red else ''
        for sx, side in ((1, 'l'), (-1, 'r')):
            wing(m, 'wing_f' + side + suffix, sx, 'fore', red)
            wing(m, 'wing_h' + side + suffix, sx, 'hind', red)
    return m


if __name__ == '__main__':
    g.write(mementomori())
