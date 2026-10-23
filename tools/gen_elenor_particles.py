"""エレノアの攻撃演出の粒子の絵（白。出すときに色を掛ける）を作る（2026-10-23）。

    python tools/gen_elenor_particles.py [確認用の拡大画像の出力先]

粒子: リボン（4 コマでねじれる）・花びら（2 種）・花（4 コマで開く）・レースの輪（地面と平行）・光の筋・幕（緞帳の房）
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'automata_valkyries')
TEX = os.path.join(ROOT, 'textures', 'particle')
DEF = os.path.join(ROOT, 'particles')
SS = 8  # 細かく描いてから縮める（縁を滑らかに）


def big(size):
    return Image.new('RGBA', (size * SS, size * SS), (0, 0, 0, 0))


def shrink(im, size):
    return im.resize((size, size), Image.LANCZOS)


def save(name, im):
    im.save(os.path.join(TEX, name + '.png'))
    return im


def ribbon(frame):
    """斜めに流れるリボンの帯。ねじれで幅が波打ち、裏側は少し暗い（4 コマで位相がずれる）"""
    size = 16
    im = big(size)
    d = ImageDraw.Draw(im)
    s = size * SS
    steps = 60
    pts_top, pts_bot, shades = [], [], []
    for i in range(steps + 1):
        u = i / steps
        x = s * (0.12 + 0.76 * u)
        y = s * (0.78 - 0.56 * u) + math.sin(u * math.pi * 2.0 + frame * 0.6) * s * 0.06
        twist = math.cos(u * math.pi * 2.2 + frame * math.pi / 2)
        w = s * 0.13 * (0.25 + 0.75 * abs(twist))
        nx, ny = 0.56, 0.76  # 帯に垂直な向き（おおよそ）
        pts_top.append((x - nx * w, y - ny * w))
        pts_bot.append((x + nx * w, y + ny * w))
        shades.append(255 if twist > 0 else 190)
    for i in range(steps):
        poly = [pts_top[i], pts_top[i + 1], pts_bot[i + 1], pts_bot[i]]
        v = shades[i]
        d.polygon(poly, fill=(v, v, v, 255))
    # 中央の光沢の筋
    for i in range(steps):
        if shades[i] == 255:
            mx = (pts_top[i][0] + pts_bot[i][0]) / 2
            my = (pts_top[i][1] + pts_bot[i][1]) / 2
            d.ellipse((mx - SS * 0.5, my - SS * 0.5, mx + SS * 0.5, my + SS * 0.5), fill=(255, 255, 255, 255))
    return shrink(im, size)


def petal(kind):
    """花びら: 先が切れ込んだしずく形。中心が明るく、縁が少し暗い"""
    size = 16
    im = big(size)
    d = ImageDraw.Draw(im)
    s = size * SS
    cx, cy = s / 2, s / 2
    pts = []
    for i in range(80):
        a = math.pi * 2 * i / 80
        r = s * 0.42 * (0.55 + 0.45 * math.cos(a)) ** 0.7
        if kind == 1:
            r *= 0.85 + 0.15 * math.sin(a * 2)
        x = cx + r * math.cos(a) * 0.62
        y = cy + r * math.sin(a) - s * 0.08
        pts.append((x, y))
    d.polygon(pts, fill=(205, 205, 205, 255))
    inner = [(cx + (x - cx) * 0.7, cy + (y - cy) * 0.7) for x, y in pts]
    d.polygon(inner, fill=(255, 255, 255, 255))
    # 先の切れ込み
    d.polygon([(cx - s * 0.05, cy - s * 0.42), (cx + s * 0.05, cy - s * 0.42), (cx, cy - s * 0.3)], fill=(0, 0, 0, 0))
    im = im.rotate(35 if kind == 0 else -20, resample=Image.BICUBIC)
    return shrink(im, size)


def bloom(frame):
    """花: 5 枚の花びらが蕾から開く（4 コマ）。花びらの縁は灰色、中心は白"""
    size = 32
    im = big(size)
    d = ImageDraw.Draw(im)
    s = size * SS
    cx, cy = s / 2, s / 2
    open_ = [0.25, 0.5, 0.78, 1.0][frame]
    for k in range(5):
        a0 = math.pi * 2 * k / 5 - math.pi / 2
        length = s * 0.44 * open_
        width = s * 0.16 * (0.5 + 0.5 * open_)
        pts = []
        for i in range(40):
            t = i / 39
            along = length * t
            side = width * math.sin(t * math.pi) * (1 if True else 1)
            pts.append((along, side))
        for i in range(39, -1, -1):
            t = i / 39
            pts.append((length * t, -width * math.sin(t * math.pi)))
        ca, sa = math.cos(a0), math.sin(a0)
        poly = [(cx + x * ca - y * sa, cy + x * sa + y * ca) for x, y in pts]
        d.polygon(poly, fill=(200, 200, 200, 255))
        inner = [(cx + (px - cx) * 0.78, cy + (py - cy) * 0.78) for px, py in poly]
        d.polygon(inner, fill=(255, 255, 255, 255))
    r = s * 0.07
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(170, 170, 170, 255))
    return shrink(im, size)


def lace():
    """レースの輪: 縁が波打つ帯に、小さな穴の模様（地面と平行に広がる）"""
    size = 64
    im = big(size)
    d = ImageDraw.Draw(im)
    s = size * SS
    cx, cy = s / 2, s / 2
    outer, inner = s * 0.48, s * 0.36
    pts_o, pts_i = [], []
    n = 360
    for i in range(n + 1):
        a = math.pi * 2 * i / n
        ro = outer - s * 0.02 * (1 - abs(math.sin(a * 12)))  # 外側の波（スカラップ）
        pts_o.append((cx + ro * math.cos(a), cy + ro * math.sin(a)))
        pts_i.append((cx + inner * math.cos(a), cy + inner * math.sin(a)))
    d.polygon(pts_o, fill=(255, 255, 255, 230))
    d.polygon(pts_i, fill=(0, 0, 0, 0))
    # 穴の模様
    for i in range(24):
        a = math.pi * 2 * i / 24
        r = (outer + inner) / 2
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        h = s * 0.018
        d.ellipse((x - h, y - h, x + h, y + h), fill=(0, 0, 0, 0))
    for i in range(48):
        a = math.pi * 2 * (i + 0.5) / 48
        r = inner + s * 0.015
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        h = s * 0.008
        d.ellipse((x - h, y - h, x + h, y + h), fill=(255, 255, 255, 255))
    return shrink(im, size)


def streak():
    """光の筋: 細い横線。両端は細く消え、真ん中が明るい（roll で向き）"""
    size = 32
    im = Image.new('RGBA', (size * SS, size * SS), (0, 0, 0, 0))
    px = im.load()
    s = size * SS
    for y in range(s):
        for x in range(s):
            u = (x - s / 2) / (s / 2)
            v = (y - s / 2) / (s * 0.06)
            a = max(0.0, 1 - u * u) * math.exp(-v * v)
            if a > 0.01:
                px[x, y] = (255, 255, 255, int(255 * min(1.0, a * 1.2)))
    return shrink(im, size)


def curtain():
    """緞帳: 縦のひだのある幕と、下の房飾り"""
    size = 32
    im = big(size)
    d = ImageDraw.Draw(im)
    s = size * SS
    for x in range(int(s * 0.1), int(s * 0.9)):
        u = (x - s * 0.1) / (s * 0.8)
        fold = 0.65 + 0.35 * math.cos(u * math.pi * 8)
        v = int(150 + 105 * fold)
        bottom = s * (0.8 + 0.04 * math.sin(u * math.pi * 8))
        d.line([(x, s * 0.04), (x, bottom)], fill=(v, v, v, 255))
    for i in range(10):  # 房
        x = s * (0.14 + 0.08 * i)
        d.polygon([(x - s * 0.025, s * 0.8), (x + s * 0.025, s * 0.8), (x, s * 0.95)], fill=(230, 230, 230, 255))
    d.rectangle((s * 0.08, s * 0.02, s * 0.92, s * 0.07), fill=(255, 255, 255, 255))
    return shrink(im, size)


def main():
    os.makedirs(TEX, exist_ok=True)
    made = {}
    made['fx_ribbon'] = [save('fx_ribbon_%d' % f, ribbon(f)) for f in range(4)]
    made['fx_petal'] = [save('fx_petal_%d' % k, petal(k)) for k in range(2)]
    made['fx_bloom'] = [save('fx_bloom_%d' % f, bloom(f)) for f in range(4)]
    made['fx_lace'] = [save('fx_lace', lace())]
    made['fx_streak'] = [save('fx_streak', streak())]
    made['fx_curtain'] = [save('fx_curtain', curtain())]
    for name, frames in made.items():
        names = [name + '_%d' % i for i in range(len(frames))] if len(frames) > 1 else [name]
        with open(os.path.join(DEF, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'textures': ['automata_valkyries:' + n for n in names]}, f, indent=2)
            f.write('\n')
    if len(sys.argv) > 1:  # 確認用: 全部を暗い背景に並べて拡大
        cells = [im for frames in made.values() for im in frames]
        sheet = Image.new('RGBA', (len(cells) * 132, 132), (40, 20, 50, 255))
        for i, im in enumerate(cells):
            tinted = Image.new('RGBA', im.size, (255, 120, 190, 0))
            tinted.putalpha(im.getchannel('A'))
            mixed = Image.composite(Image.blend(tinted, im, 0.45), im, im.getchannel('A'))
            sheet.alpha_composite(mixed.resize((128, 128), Image.NEAREST), (i * 132 + 2, 2))
        sheet.save(sys.argv[1])
    print('ok', {k: len(v) for k, v in made.items()})


if __name__ == '__main__':
    main()
