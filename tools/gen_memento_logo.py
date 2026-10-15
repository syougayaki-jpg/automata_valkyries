"""メメントモリの大技「死蝶終葬『メメント・モリ』」の専用ロゴ（2026-10-15 ユーザー指定「専用ロゴを作って表示して」）。

透明な背景に、紫・桃色・水色の光の帯、左右の光の機械の蝶（翅の縁が光り、デジタルのモザイクの四角が散る）、
游明朝の「死蝶終葬」と『メメント・モリ』、英語の小さな添え書き。描画は client/MementoSpellLogo。

    python tools/gen_memento_logo.py
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/textures/gui/memento_spell_logo.png")
FONTS = "C:/Windows/Fonts"

W, H, SS = 1024, 320, 2          # 出来上がりの大きさと、なめらかにするための拡大率
SW, SH = W * SS, H * SS

VIOLET = (164, 92, 255)
PALE = (232, 208, 255)
PINK = (255, 140, 235)
CYAN = (143, 230, 255)
WHITE = (255, 255, 255)

random.seed(20261015)


def layer():
    return Image.new("RGBA", (SW, SH), (0, 0, 0, 0))


def glow(img, radius, strength=1.0):
    """img をぼかして光にする（strength 倍の濃さ）"""
    g = img.filter(ImageFilter.GaussianBlur(radius * SS))
    if strength != 1.0:
        a = np.asarray(g).astype(np.float32)
        a[..., 3] = np.clip(a[..., 3] * strength, 0, 255)
        g = Image.fromarray(a.astype(np.uint8), "RGBA")
    return g


def tint(mask, color, alpha=1.0):
    """L のマスクを color の RGBA にする"""
    out = Image.new("RGBA", mask.size, color + (0,))
    out.putalpha(mask.point(lambda v: int(v * alpha)))
    return out


def gradient_fill(mask, top, bottom, y0, y1):
    """マスクの形を、上 top → 下 bottom の縦の色の移り変わりで塗る"""
    ys = np.clip((np.arange(SH) - y0) / max(1, (y1 - y0)), 0, 1)[:, None]
    rgb = np.zeros((SH, SW, 3), np.float32)
    for c in range(3):
        rgb[..., c] = top[c] * (1 - ys) + bottom[c] * ys
    a = np.asarray(mask).astype(np.float32)
    return Image.fromarray(np.dstack([rgb, a]).astype(np.uint8), "RGBA")


def lerp_color(stops, t):
    t = min(1.0, max(0.0, t)) * (len(stops) - 1)
    i = min(int(t), len(stops) - 2)
    k = t - i
    return tuple(int(stops[i][c] * (1 - k) + stops[i + 1][c] * k) for c in range(3))


def band():
    """真ん中の横の光の帯（左から桃色・紫・水色。上下と左右の端で薄れる）"""
    xs = np.linspace(0, 1, SW)[None, :]
    ys = np.linspace(-1, 1, SH)[:, None]
    stops = [PINK, VIOLET, CYAN]
    rgb = np.zeros((1, SW, 3), np.float32)
    for x in range(SW):
        rgb[0, x] = lerp_color(stops, x / (SW - 1))
    rgb = np.repeat(rgb, SH, axis=0)
    vert = np.exp(-(ys / 0.34) ** 2)
    horiz = np.sin(np.pi * xs) ** 1.6
    a = 120 * vert * horiz
    return Image.fromarray(np.dstack([rgb, a]).astype(np.uint8), "RGBA")


def rules(img):
    """上下の細い線と菱形の飾り"""
    d = ImageDraw.Draw(img)
    for y, col in ((0.17, PINK), (0.83, CYAN)):
        yy = int(H * y * SS)
        for x in range(int(SW * 0.08), int(SW * 0.92), 2):
            t = (x - SW * 0.08) / (SW * 0.84)
            a = int(200 * max(0.0, math.sin(math.pi * t)) ** 0.7)
            d.line([(x, yy), (x + 2, yy)], fill=col + (a,), width=SS)
        for x in (0.08, 0.5, 0.92):
            cx, r = int(SW * x), 7 * SS
            d.polygon([(cx, yy - r), (cx + r, yy), (cx, yy + r), (cx - r, yy)], outline=WHITE + (230,), fill=col + (160,))


# 蝶の翅の形（付け根を原点、外向き +x、上が -y。単位は大きさに対する割合）
FORE = [(0.0, -0.02), (0.18, -0.40), (0.55, -0.62), (0.95, -0.55), (0.88, -0.30), (0.55, -0.08), (0.12, 0.02)]
HIND = [(0.0, 0.04), (0.42, 0.10), (0.70, 0.30), (0.62, 0.55), (0.40, 0.66), (0.28, 0.95), (0.16, 0.62), (0.03, 0.22)]


def butterfly(img, cx, cy, size, tilt, edge, fill):
    """光の機械の蝶: 半透明の翅、光る縁、付け根から折れて伸びる回路の線、菱形の放射口、モザイクの四角"""
    fill_l, line_l = layer(), layer()
    df, dl = ImageDraw.Draw(fill_l), ImageDraw.Draw(line_l)
    ct, st = math.cos(tilt), math.sin(tilt)

    def pt(x, y, side):
        x *= side
        return (cx + (x * ct - y * st) * size, cy + (x * st + y * ct) * size)

    for side in (-1, 1):
        for wing, shade in ((FORE, 1.0), (HIND, 0.8)):
            poly = [pt(x, y, side) for x, y in wing]
            df.polygon(poly, fill=fill + (int(80 * shade),))
            dl.line(poly + [poly[0]], fill=edge + (255,), width=3 * SS, joint="curve")
            # 回路: 付け根から外へ、途中で折れて翅の先へ
            tip = wing[len(wing) // 2 - (1 if wing is FORE else 0)]
            knee = (tip[0] * 0.45, tip[1] * 0.15)
            dl.line([pt(0.05, 0.0, side), pt(*knee, side), pt(tip[0] * 0.85, tip[1] * 0.85, side)], fill=WHITE + (170,), width=SS * 2)
        node = pt(0.55, -0.35, side)
        r = size * 0.05
        dl.polygon([(node[0], node[1] - r), (node[0] + r * 0.8, node[1]), (node[0], node[1] + r), (node[0] - r * 0.8, node[1])],
                   outline=WHITE + (255,), fill=edge + (220,))
        # モザイクの四角（翅のまわりへ散る）
        for _ in range(16):
            x, y = random.uniform(0.3, 1.25), random.uniform(-0.75, 0.9)
            p = pt(x, y, side)
            s = random.choice((4, 6, 8, 10)) * SS
            a = random.randint(70, 200)
            df.rectangle([p[0] - s / 2, p[1] - s / 2, p[0] + s / 2, p[1] + s / 2], fill=random.choice((edge, PALE, fill)) + (a,))
    # 体
    dl.line([pt(0, -0.32, 1), pt(0, 0.40, 1)], fill=WHITE + (255,), width=4 * SS)
    dl.ellipse([cx - 6 * SS, cy - size * 0.36 - 6 * SS, cx + 6 * SS, cy - size * 0.36 + 6 * SS], fill=WHITE + (255,))
    img.alpha_composite(glow(line_l, 10, 1.6))
    img.alpha_composite(fill_l)
    img.alpha_composite(glow(line_l, 3))
    img.alpha_composite(line_l)


def text_mask(text, font, cx, cy, spacing=0):
    """文字の形のマスク（中心 cx, cy。字の間を spacing 足す）"""
    m = Image.new("L", (SW, SH), 0)
    d = ImageDraw.Draw(m)
    widths = [d.textlength(ch, font=font) for ch in text]
    total = sum(widths) + spacing * (len(text) - 1)
    x = cx - total / 2
    for ch, w in zip(text, widths):
        d.text((x, cy), ch, font=font, fill=255, anchor="lm")
        x += w + spacing
    return m


def titled_text(img, mask, top, bottom, y0, y1, glow_color, glow_radius):
    """影・色の光・縦の色の移り変わりの塗り・細い白い縁で文字を重ねる"""
    shadow = tint(mask.filter(ImageFilter.GaussianBlur(6 * SS)), (20, 0, 40), 0.75)
    img.alpha_composite(shadow)
    img.alpha_composite(glow(tint(mask, glow_color), glow_radius, 1.5))
    img.alpha_composite(glow(tint(mask, CYAN), glow_radius * 0.3, 0.8))
    edge = mask.filter(ImageFilter.MaxFilter(3 if SS < 2 else 5))
    img.alpha_composite(tint(edge, WHITE, 0.85))
    img.alpha_composite(gradient_fill(mask, top, bottom, y0, y1))


def main():
    img = layer()
    img.alpha_composite(band())
    rules(img)
    butterfly(img, SW * 0.115, SH * 0.48, SH * 0.31, -0.18, PINK, VIOLET)
    butterfly(img, SW * 0.885, SH * 0.48, SH * 0.31, 0.18, CYAN, VIOLET)

    mincho = ImageFont.truetype(os.path.join(FONTS, "yumindb.ttf"), int(112 * SS))
    title_y = SH * 0.40
    m = text_mask("死蝶終葬", mincho, SW / 2, title_y, spacing=18 * SS)
    titled_text(img, m, WHITE, (255, 196, 244), title_y - 56 * SS, title_y + 56 * SS, PINK, 14)

    sub = ImageFont.truetype(os.path.join(FONTS, "yumindb.ttf"), int(46 * SS))
    sub_y = SH * 0.69
    m2 = text_mask("『メメント・モリ』", sub, SW / 2, sub_y, spacing=4 * SS)
    titled_text(img, m2, (230, 250, 255), (178, 236, 255), sub_y - 23 * SS, sub_y + 23 * SS, VIOLET, 9)

    latin = ImageFont.truetype(os.path.join(FONTS, "georgia.ttf"), int(15 * SS))
    m3 = text_mask("FINAL  RITE  OF  THE  DEATH  BUTTERFLY", latin, SW / 2, SH * 0.905, spacing=3 * SS)
    img.alpha_composite(glow(tint(m3, VIOLET), 4))
    img.alpha_composite(tint(m3, PALE, 0.95))

    out = img.resize((W, H), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print("wrote", OUT, out.size)


if __name__ == "__main__":
    main()
