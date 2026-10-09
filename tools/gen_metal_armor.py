"""
プレイヤー用の固有金属の防具（アストライト・フォノニウム・セルリアス）の見た目を作る（2026-10-09）。

    python tools/gen_metal_armor.py

出力: textures/item/<金属>_<部位>.png（16x16）と textures/models/armor/<金属>_layer_1.png / _layer_2.png（64x32）。
形と陰影はバニラの鉄の防具（.mcsrc/assets の iron_*）の明るさを使い、色は各金属のインゴットの色の段（暗い → 明るい）に置き換える。
明るい所の一部に金属ごとの光る差し色（アストライトは水色・フォノニウムは金・セルリアスは淡い緑）を入れる。
"""
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
VANILLA = os.path.join(ROOT, '.mcsrc', 'assets', 'minecraft', 'textures')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries', 'textures')

# 暗い → 明るいの色の段（インゴットの色から）と、差し色
METALS = {
    'astrite': (['#0d131d', '#1d2d44', '#2c4060', '#3d5a80', '#7ca7c9', '#b8d4e8'], '#6af0ff'),   # 暗い紺の鋼
    'phononium': (['#140005', '#370617', '#5a0a1a', '#9d0208', '#e63946', '#fff3b0'], '#ffd166'),
    'ceruleas': (['#06221f', '#0f4c4a', '#1f7a78', '#3fb0a8', '#8fe0d4', '#e8fff8'], '#c8f7dc'),  # 青緑（水の色）
}
PARTS = ['helmet', 'chestplate', 'leggings', 'boots']


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def ramp(colors, t):
    """t（0〜1）を色の段の間で補間する"""
    t = max(0.0, min(1.0, t)) * (len(colors) - 1)
    i = min(int(t), len(colors) - 2)
    f = t - i
    a, b = rgb(colors[i]), rgb(colors[i + 1])
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def recolor(src, colors, accent, accent_every):
    img = Image.open(src).convert('RGBA')
    px = img.load()
    w, h = img.size
    lums = [0.299 * r + 0.587 * g + 0.114 * b for (r, g, b, a) in (img.getpixel((x, y)) for y in range(img.height) for x in range(img.width)) if a > 0]
    lo, hi = (min(lums), max(lums)) if lums else (0, 255)
    out = Image.new('RGBA', (w, h))
    op = out.load()
    n = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            t = (0.299 * r + 0.587 * g + 0.114 * b - lo) / max(1.0, hi - lo)
            c = ramp(colors, t)
            if t > 0.78:  # 明るい所に差し色をところどころ
                n += 1
                if n % accent_every == 0:
                    c = rgb(accent)
            op[x, y] = c + (a,)
    return out


def main():
    os.makedirs(os.path.join(OUT, 'item'), exist_ok=True)
    os.makedirs(os.path.join(OUT, 'models', 'armor'), exist_ok=True)
    for metal, (colors, accent) in METALS.items():
        for part in PARTS:
            recolor(os.path.join(VANILLA, 'item', f'iron_{part}.png'), colors, accent, 5).save(os.path.join(OUT, 'item', f'{metal}_{part}.png'))
        for layer in (1, 2):
            recolor(os.path.join(VANILLA, 'models', 'armor', f'iron_layer_{layer}.png'), colors, accent, 9).save(
                os.path.join(OUT, 'models', 'armor', f'{metal}_layer_{layer}.png'))
        print(metal, 'done')


if __name__ == '__main__':
    main()
