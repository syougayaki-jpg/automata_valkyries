"""
事象観測体アカシック・プローブのマイクラ風の見た目（2026-10-26 ユーザー指定「アカシックちゃんのモデルをマイクラ風に これにして」→「完全にイラストの通りにして」）。

    python tools/gen_akashic_mc.py

見本の画像（tools/ref/akashic_probe_mc_ref.png、ユーザーの絵）の正面から、顔・胴・白衣の裾・脚・ブーツ・ヘッドホンの色を拾い、
見えない横・後ろ・上は同じ色で描き足す。見本の絵は 1 面 8 ドットより細かいので、2 倍の細かさ（1 ドット = 2x2）で作る。

出力（textures/entity/）。どちらも 1024x512（モデルの UV は 128x64 で、その 8 倍。体は 2 倍の細かさで作って 4 倍に広げ、顔だけは見本を 8 倍の細かさで写す）:
  akashic_probe_mc.png      左の 64x64 はプレイヤーのスキン（太い腕＝Steve の並び。上着などの外側の層は空ける）、
                            右半分は足した箱: ヘッドホンの耳当て 2 つ・白衣の裾・袖口の表示 2 つ・ブーツ 2 つ
  akashic_probe_mc_glow.png 光る所（目・袖口の表示・ヘッドホンの灯り・胸の印）
"""
import colorsys
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
REF = os.path.join(ROOT, 'tools', 'ref', 'akashic_probe_mc_sheet.png')   # 2026-10-26「これ参考にやって」の見本の表（正面・頭の上と横）
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries', 'textures', 'entity')
K = 2  # 1 ドット = K x K

# 見本の表（akashic_probe_mc_sheet.png）の各面の範囲（画像の画素。左上・右下）。正面は真ん中の立ち姿、頭の上と横（ヘッドホンの側）は左上の頭の帯から
REF_BOXES = {
    'head': (431, 48, 593, 198),
    'head_top': (137, 15, 222, 65),
    'head_side': (242, 65, 345, 150),
    'body': (450, 198, 580, 360),
    'skirt': (450, 360, 580, 437),
    'leg_r': (450, 437, 515, 490),     # 太もも（裾とブーツの間）
    'leg_l': (515, 437, 580, 490),
    'boot_r': (450, 490, 515, 572),
    'boot_l': (515, 490, 580, 572),
    'arm_r': (383, 200, 445, 378),     # 絵の左側の腕＝モデルの右腕
    'arm_l': (585, 200, 640, 378),
    'cuff_r': (383, 318, 445, 362),
    'cuff_l': (585, 318, 640, 362),
    'phone': (265, 90, 325, 135),
}

PAL = {
    'coat': (242, 244, 248), 'coat2': (214, 220, 230), 'coat3': (176, 184, 198), 'shirt': (66, 68, 80), 'ink': (34, 36, 46),
    'pink': (226, 64, 160), 'blue': (72, 140, 220), 'skin': (242, 216, 204), 'skin2': (226, 190, 182), 'sock': (230, 166, 200),
    'hood': (226, 230, 238), 'hood2': (196, 202, 214), 'hood3': (160, 168, 184), 'hairP': (238, 160, 214), 'hairC': (150, 216, 232),
    'band': (58, 60, 76), 'phone': (40, 42, 58), 'phone2': (58, 60, 82), 'display': (30, 36, 50), 'dotC': (90, 220, 240), 'dotP': (240, 96, 190),
    'boot': (58, 60, 72), 'boot2': (76, 78, 92), 'strap': (232, 232, 236), 'stripe': (214, 30, 150), 'sole': (110, 40, 84),
}


class Sheet:
    def __init__(self):
        self.img = Image.new('RGBA', (128 * K, 64 * K), (0, 0, 0, 0))
        self.ref = Image.open(REF).convert('RGB')

    def px(self, x, y, c):
        self.img.putpixel((x, y), (c + (255,)) if len(c) == 3 else c)

    def sample(self, name, w, h):
        x0, y0, x1, y1 = REF_BOXES[name]
        out = []
        for j in range(h):
            row = []
            for i in range(w):
                cx, cy = x0 + (i + 0.5) * (x1 - x0) / w, y0 + (j + 0.5) * (y1 - y0) / h
                ps = [self.ref.getpixel((int(cx) + dx, int(cy) + dy)) for dx in (-1, 0, 1) for dy in (-1, 0, 1)]
                row.append(tuple(sum(p[k] for p in ps) // 9 for k in range(3)))
            out.append(row)
        return out

    def face(self, u, v, w, h, fill):
        """u, v, w, h はモデルの UV の単位。fill(i, j, W, H) は細かい画素ごと"""
        W, H = w * K, h * K
        for j in range(H):
            for i in range(W):
                c = fill(i, j, W, H) if callable(fill) else fill
                if c is not None:
                    self.px(u * K + i, v * K + j, PAL[c] if isinstance(c, str) else c)

    def box(self, u, v, w, h, d, faces):
        pos = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
               'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}
        for k, f in faces.items():
            x, y, ww, hh = pos[k]
            self.face(x, y, ww, hh, f)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def clean(data, ring, dark, fill):
    """見本の切り抜きの縁（ring 画素）に写り込んだ暗い背景を fill に置き換える"""
    h, w = len(data), len(data[0])
    for j in range(h):
        for i in range(w):
            if (i < ring or j < ring or i >= w - ring) and lum(data[j][i]) < dark:
                data[j][i] = fill
    return data


def to_pink(c):
    """水色の髪の画素を桃に（右の横は見本に無いので、左の横から作る）"""
    h, sat, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    if 0.45 < h < 0.58 and sat > 0.15:
        r, g, b = colorsys.hsv_to_rgb(0.89, sat, v)
        return int(r * 255), int(g * 255), int(b * 255)
    return c


def grid(data):
    return lambda i, j, W, H: data[j * len(data) // H][i * len(data[0]) // W]


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def build():
    s = Sheet()
    rnd = random.Random(7)

    # ---- 頭: 正面は見本から。まわりは白いフード（上にヘッドホンのバンド）と、前寄りに髪の房 ----
    head = clean(s.sample('head', 16, 16), 2, 90, PAL['hood2'])

    def hood(i, j, W, H):
        if j == H - 1 or i in (0, W - 1):
            return 'hood3'
        return 'hood2' if i in (1, W - 2) or j == 0 else 'hood'

    def side_r(i, j, W, H):                                                  # 右の横（前寄り＝右端に桃の髪）
        if i >= W - 4 and 3 <= j <= 12:
            return 'hairP' if (i + j) % 3 else shade(PAL['hairP'], 0.85)
        return hood(i, j, W, H)

    def side_l(i, j, W, H):                                                  # 左の横（前寄り＝左端に水色の髪）
        if i <= 3 and 3 <= j <= 12:
            return 'hairC' if (i + j) % 3 else shade(PAL['hairC'], 0.85)
        return hood(i, j, W, H)

    def top(i, j, W, H):
        if 6 <= j <= 9:
            return 'band' if (i + j) % 5 else 'phone2'                        # ヘッドホンのバンド
        if j >= H - 3:
            return 'hairP' if i < W // 2 else 'hairC'
        return hood(i, j, W, H)

    head_top = clean(s.sample('head_top', 16, 16), 1, 60, PAL['hood2'])
    side = clean(s.sample('head_side', 16, 16), 1, 60, PAL['hood2'])
    mirror = [[to_pink(c) for c in reversed(row)] for row in side]
    s.box(0, 0, 8, 8, 8, {'front': grid(head), 'right': grid(mirror), 'left': grid(side), 'top': grid(head_top), 'back': hood, 'bottom': 'skin2'})

    # ---- 胴: 正面は見本から（開いた白衣・黒いインナー・桃の印・桃と青の胸章）。後ろ・横は白衣 ----
    body = clean(s.sample('body', 16, 24), 1, 60, PAL['coat3'])

    def coat_back(i, j, W, H):                                               # 縫い目と端の陰だけの、なめらかな白衣
        if i in (0, W - 1):
            return 'coat3'
        if i in (1, W - 2, W // 2 - 1, W // 2):
            return 'coat2'
        return 'coat'

    def coat_side(i, j, W, H):
        return 'coat3' if i in (0, W - 1) else ('coat2' if i in (1, W - 2) else 'coat')

    def shoulders(i, j, W, H):
        return 'ink' if 5 <= i <= 10 and j >= H - 4 else 'coat'

    s.box(16, 16, 8, 12, 4, {'front': grid(body), 'back': coat_back, 'right': coat_side, 'left': coat_side, 'top': shoulders, 'bottom': 'shirt'})

    # ---- 腕（太い 4x12x4）: 白衣の袖。左腕の外側に桃・白・青の章。手は肌（袖口は別の箱） ----
    def sleeve(badge):
        def f(i, j, W, H):
            if j >= H - 3:
                return 'skin' if j < H - 1 else 'skin2'
            if badge and 3 <= i <= 5 and 4 <= j <= 11:
                return 'pink' if j < 7 else ('coat' if j < 8 else 'blue')
            if i in (0, W - 1):
                return 'coat3'
            return 'coat2' if i in (1, W - 2) else 'coat'
        return f

    for u, v, outer, name in ((40, 16, 'right', 'arm_r'), (32, 48, 'left', 'arm_l')):
        front = clean(s.sample(name, 8, 24), 1, 50, PAL['coat3'])
        faces = {'front': grid(front), 'back': sleeve(False), 'right': sleeve(False), 'left': sleeve(False), 'top': 'coat', 'bottom': 'skin'}
        faces[outer] = sleeve(True)
        s.box(u, v, 4, 12, 4, faces)

    # ---- 脚: 太ももは見本から（裾の下）、裾の中は肌。下はブーツの箱が覆う ----
    for name, (u, v) in (('leg_r', (0, 16)), ('leg_l', (16, 48))):
        thigh = clean(s.sample(name, 8, 4), 1, 90, PAL['skin2'])

        def leg(i, j, W, H, t=thigh):
            if j < 12:
                return 'skin'
            if j < 16:
                return t[j - 12][i]
            return 'boot'

        def leg_side(i, j, W, H):
            return 'skin' if j < 15 else ('sock' if j < 16 else 'boot')

        s.box(u, v, 4, 12, 4, {'front': leg, 'back': leg_side, 'right': leg_side, 'left': leg_side, 'top': 'skin', 'bottom': 'sole'})

    # ---- 足した箱（右半分） ----
    # ヘッドホンの耳当て（2x4x4）: 外側は見本の桃・紫・青・水色の模様
    cup = clean(s.sample('phone', 8, 8), 1, 30, PAL['phone'])
    s.box(64, 0, 2, 4, 4, {'right': grid(cup), 'left': 'phone', 'front': 'phone2', 'back': 'phone2', 'top': 'phone', 'bottom': 'phone'})
    s.box(64, 8, 2, 4, 4, {'left': grid(cup), 'right': 'phone', 'front': 'phone2', 'back': 'phone2', 'top': 'phone', 'bottom': 'phone'})

    # 白衣の裾（9x6x5）: 正面は見本から、後ろ・横は白衣に桃と青の帯
    skirt = clean(s.sample('skirt', 18, 12), 1, 60, PAL['coat3'])

    def hem(i, j, W, H):
        if j >= H - 2:
            return 'pink' if (i // 4) % 2 == 0 else 'blue'
        if j == H - 3:
            return 'coat3'
        return coat_back(i, j, W, H)

    s.box(64, 16, 9, 6, 5, {'front': grid(skirt), 'back': hem, 'right': hem, 'left': hem, 'top': 'coat', 'bottom': 'shirt'})

    # 袖口（4x4x4）: 上に桃・青・灰の帯、下は紺の表示に水色の棒グラフと桃の点
    def cuff(seed):
        r = random.Random(seed)
        bars = [r.randint(1, 4) for _ in range(16)]

        def f(i, j, W, H):
            if j <= 1:
                return ('pink', 'blue', 'coat3', 'pink')[(i // 2) % 4]
            if j == 2:
                return 'ink'
            if i % 2 == 1 and j >= H - 1 - bars[i] and j < H - 1:
                return 'dotC'
            if i % 5 == 0 and j == 3:
                return 'dotP'
            return 'display'
        return f

    for u, seed, name in ((64, 1, 'cuff_r'), (80, 2, 'cuff_l')):
        f = cuff(seed)
        s.box(u, 28, 4, 4, 4, {'front': grid(clean(s.sample(name, 8, 8), 1, 25, PAL['display'])), 'back': f, 'right': f, 'left': f, 'top': 'coat', 'bottom': 'display'})

    # ブーツ（4x4x4）: 正面は見本から（白い帯・桃の線・白いつま先・赤紫の靴底）、横と後ろは同じ並び
    for name, u in (('boot_r', 64), ('boot_l', 80)):
        front = s.sample(name, 8, 8)

        def boot_side(i, j, W, H):
            if j == H - 1:
                return 'sole'
            if j == 3:
                return 'stripe'
            return 'boot' if (i + j) % 4 else 'boot2'

        s.box(u, 36, 4, 4, 4, {'front': grid(front), 'back': boot_side, 'right': boot_side, 'left': boot_side, 'top': 'boot', 'bottom': 'sole'})

    # ---- 光る所: 目・袖口・ヘッドホンの外側・胸の印の、鮮やかな色の画素だけ ----
    glow = Image.new('RGBA', s.img.size, (0, 0, 0, 0))
    regions = [(8, 8, 8, 8, 0.45), (20, 20, 8, 12, 0.6),                      # 顔（目）・胴の正面（胸の印）
               (64, 28, 32, 8, 0.4), (64, 0, 12, 16, 0.4)]                     # 袖口・ヘッドホン
    for u, v, w, h, sat in regions:
        for y in range(v * K, (v + h) * K):
            for x in range(u * K, (u + w) * K):
                r, g, b, a = s.img.getpixel((x, y))
                if a == 0:
                    continue
                hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                if ss > sat and vv > 0.55:
                    glow.putpixel((x, y), (r, g, b, 255))
    return s.img, glow


# 顔（2026-10-26 ユーザー指定「顔を完全にこれと一致するように」）: 見本の表の顔の範囲をそのまま、さらに 4 倍の細かさ（1 ドット = 8x8）で写す
FACE_BOX = (428, 44, 596, 203)
HI = 4


# 見本の顔は 1 マス約 8.4 画素の 20x19 マス（AI の絵を拡大したもので、マスの縁がにじむ）。マスの真ん中の色の中央値で 1 マス 1 色に戻してから写す
FACE_CELLS = (20, 19)
# 光らせる目のマス（列・行の範囲。左目は桃、右目は水色）。この中の、鮮やかな色と白いハイライトのマスを丸ごと光らせる
EYE_CELLS = ((4, 8, 12, 14), (11, 16, 12, 14))


def face_cells(ref):
    import statistics
    x0, y0, x1, y1 = FACE_BOX
    cols, rows = FACE_CELLS
    out = []
    for r in range(rows):
        row = []
        for c in range(cols):
            ax, bx = x0 + (c + 0.25) * (x1 - x0) / cols, x0 + (c + 0.75) * (x1 - x0) / cols
            ay, by = y0 + (r + 0.25) * (y1 - y0) / rows, y0 + (r + 0.75) * (y1 - y0) / rows
            ps = [ref.getpixel((x, y)) for x in range(int(ax), int(bx) + 1) for y in range(int(ay), int(by) + 1)]
            c3 = tuple(int(statistics.median(p[k] for p in ps)) for k in range(3))
            corner = (c < 2 or c >= cols - 2) and (r < 2 or r >= rows - 2)
            gray = colorsys.rgb_to_hsv(*(v / 255 for v in c3))[1] < 0.08 and lum(c3) < 190
            if corner and (lum(c3) < 70 or gray):
                c3 = PAL['hood2']                                             # 切り抜きの角に写った背景（黒と市松の灰）はフードの色に
            row.append(c3)
        out.append(row)
    return out


def eye_lit(c, r, color):
    for c0, c1, r0, r1 in EYE_CELLS:
        if c0 <= c <= c1 and r0 <= r <= r1:
            h, sat, v = colorsys.rgb_to_hsv(color[0] / 255, color[1] / 255, color[2] / 255)
            return (sat > 0.2 and v > 0.6) or lum(color) > 235
    return False


def hi_res(img, glow):
    """全体を 4 倍にして、頭の正面だけ見本の顔（1 マス 1 色に戻したもの）を細かく写す（UV は同じなので、ほかの所は見た目が変わらない）。
    目は 2026-10-26「目がちょっとよごれている」で、マスごとに丸ごと光らせる（前は画素ごとで、光る所と光らない所がまだらだった）"""
    big = img.resize((img.width * HI, img.height * HI), Image.NEAREST)
    big_glow = glow.resize((glow.width * HI, glow.height * HI), Image.NEAREST)
    n = 8 * K * HI                                                            # 頭の正面の 1 辺の画素
    x0, y0 = 8 * K * HI, 8 * K * HI                                           # 頭の正面は UV の (8, 8)
    cells = face_cells(Image.open(REF).convert('RGB'))
    cols, rows = FACE_CELLS
    for y in range(n):
        for x in range(n):
            r, c = y * rows // n, x * cols // n
            color = cells[r][c]
            big.putpixel((x0 + x, y0 + y), color + (255,))
            big_glow.putpixel((x0 + x, y0 + y), color + (255,) if eye_lit(c, r, color) else (0, 0, 0, 0))
    return big, big_glow


def main():
    img, glow = hi_res(*build())
    img.save(os.path.join(OUT, 'akashic_probe_mc.png'))
    glow.save(os.path.join(OUT, 'akashic_probe_mc_glow.png'))
    print('saved')


if __name__ == '__main__':
    main()
