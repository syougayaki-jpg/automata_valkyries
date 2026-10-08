"""
兵姫の収納テレポーター（valkyrie_storage）のブロックの見た目を作る（2026-10-09）。

    python tools/gen_valkyrie_storage.py

出力: textures/block/valkyrie_storage_front.png（転送門。8 コマの動き、.mcmeta つき）・_side.png・_top.png・_bottom.png（16x16）。
色は兵姫用充電台と同じガンメタルの地にホログラムのシアン（救済通信機・収納の画面と同じ発光の色）。
正面は金属の枠の奥に、渦を巻きながら奥へ吸い込まれるシアンと紫の転送の光。横は縦のパネルと光の筋・状態の灯り 3 つ、
上は兵姫が立つ転送の台（同心の光の輪）、下は放熱の格子。
"""
import json
import math
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'automata_valkyries', 'textures', 'block')

DARK = (0x12, 0x14, 0x19)
BASE = (0x1C, 0x1F, 0x26)
MID = (0x26, 0x2A, 0x33)
HI = (0x3A, 0x3F, 0x4A)
CYAN = (0x3B, 0xE8, 0xFF)
CYAN_MID = (0x25, 0x79, 0x8D)
CYAN_DIM = (0x14, 0x4A, 0x5B)
VIOLET = (0x9A, 0x6C, 0xFF)
WHITE = (0xE8, 0xFC, 0xFF)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def metal(rng):
    """ガンメタルの地（わずかなざらつき）"""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = rng.randint(-3, 3)
            img.putpixel((x, y), tuple(max(0, c + n) for c in BASE) + (255,))
    return img


def frame(img):
    """外の縁（暗い）と、上と左の面取りの光"""
    for i in range(16):
        img.putpixel((i, 0), DARK + (255,))
        img.putpixel((i, 15), DARK + (255,))
        img.putpixel((0, i), DARK + (255,))
        img.putpixel((15, i), DARK + (255,))
    for i in range(1, 15):
        img.putpixel((i, 1), HI + (255,))
        img.putpixel((1, i), MID + (255,))


def side(rng):
    img = metal(rng)
    frame(img)
    for y in range(2, 14):                       # 縦のパネルの継ぎ目
        img.putpixel((5, y), DARK + (255,))
        img.putpixel((10, y), DARK + (255,))
    for y in range(3, 13):                       # 真ん中の光の筋（上ほど明るい）
        c = mix(CYAN, CYAN_MID, (y - 3) / 9)
        img.putpixel((7, y), c + (255,))
        img.putpixel((8, y), mix(c, CYAN_DIM, 0.5) + (255,))
    for i, x in enumerate((2, 3, 4)):            # 状態の灯り 3 つ
        img.putpixel((x, 3), (CYAN if i < 2 else VIOLET) + (255,))
    for x in range(11, 15, 2):                   # 放熱の溝
        for y in range(10, 14):
            img.putpixel((x, y), DARK + (255,))
    return img


def top(rng):
    img = metal(rng)
    frame(img)
    for y in range(16):                          # 同心の光の輪（兵姫が立つ転送の台）
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if 5.6 <= r < 6.6:
                img.putpixel((x, y), CYAN_MID + (255,))
            elif 3.4 <= r < 4.3:
                img.putpixel((x, y), CYAN + (255,))
            elif r < 1.6:
                img.putpixel((x, y), WHITE + (255,))
            elif r < 2.4:
                img.putpixel((x, y), CYAN_DIM + (255,))
    for (x, y) in ((7, 1), (8, 1), (1, 7), (1, 8)):  # 四方の位置合わせの印
        img.putpixel((x, y), CYAN + (255,))
    img.putpixel((14, 7), CYAN + (255,)); img.putpixel((14, 8), CYAN + (255,))
    img.putpixel((7, 14), CYAN + (255,)); img.putpixel((8, 14), CYAN + (255,))
    return img


def bottom(rng):
    img = metal(rng)
    frame(img)
    for y in range(3, 13, 2):                    # 放熱の格子
        for x in range(3, 13):
            img.putpixel((x, y), DARK + (255,))
    return img


def front(rng, frames=8):
    """金属の枠の奥で、渦を巻きながら奥へ吸い込まれる転送の光（frames コマ）"""
    sheet = Image.new('RGBA', (16, 16 * frames))
    base = metal(rng)
    frame(base)
    for f in range(frames):
        img = base.copy()
        phase = f / frames * math.tau
        for y in range(3, 14):
            for x in range(3, 13):
                cx, cy = x - 7.5, y - 8.0
                r = math.hypot(cx / 1.0, cy / 1.15)
                a = math.atan2(cy, cx)
                swirl = 0.5 + 0.5 * math.sin(3 * a - 1.6 * r + phase * 2)
                depth = max(0.0, 1.0 - r / 5.6)                       # 真ん中ほど明るい
                col = mix(CYAN_DIM, CYAN, swirl * 0.8 + depth * 0.4)
                col = mix(col, VIOLET, max(0.0, 0.35 - depth) * (1 - swirl))  # 縁は紫がかる
                col = mix(col, WHITE, max(0.0, depth - 0.7) * 2.5)
                img.putpixel((x, y), col + (255,))
        for x in range(2, 14):                    # 門の枠（上下の金属の梁と光る縁）
            img.putpixel((x, 2), HI + (255,))
            img.putpixel((x, 14), DARK + (255,))
        for y in range(2, 15):
            img.putpixel((2, y), MID + (255,))
            img.putpixel((13, y), DARK + (255,))
        lamp = CYAN if f % 4 < 2 else CYAN_MID      # 上の梁の点滅する灯り
        img.putpixel((7, 2), lamp + (255,))
        img.putpixel((8, 2), lamp + (255,))
        sheet.paste(img, (0, 16 * f))
    return sheet


def main():
    rng = random.Random(4096)
    os.makedirs(OUT, exist_ok=True)
    front(rng).save(os.path.join(OUT, 'valkyrie_storage_front.png'))
    with open(os.path.join(OUT, 'valkyrie_storage_front.png.mcmeta'), 'w', encoding='utf-8') as f:
        json.dump({'animation': {'frametime': 2, 'interpolate': True}}, f, indent=2)
        f.write('\n')
    side(rng).save(os.path.join(OUT, 'valkyrie_storage_side.png'))
    top(rng).save(os.path.join(OUT, 'valkyrie_storage_top.png'))
    bottom(rng).save(os.path.join(OUT, 'valkyrie_storage_bottom.png'))
    print('valkyrie_storage: front (8 frames), side, top, bottom')


if __name__ == '__main__':
    main()
