"""戦術級コードの演出の素材（textures/fx/*.png）を作る。Python + numpy + PIL。

  glow.png     64x64   柔らかい丸い光（白→透明。頂点の色で染めて足し算で描く）
  flare.png    64x64   十字に伸びる光（強い光の芯）
  flame.png    32x512  炎のパラパラ漫画 16 コマ（縦に並ぶ。黄白の芯→橙→赤→透明）
  smoke.png    64x64   煙のかたまり（白。頂点の色で暗くして半透明で描く）
  petal.png    32x32   花びら（白。頂点の色で染める。葉脈と柔らかい縁）
  streak.png   16x64   縦に細長い光の筋（焼夷弾の尾・光線の芯）

python tools/gen_tactical_fx.py
"""
import math
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'automata_valkyries', 'textures', 'fx')
rng = np.random.default_rng(20261007)


def save(arr, name):
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name))


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return (x + 0.5) / w * 2 - 1, (y + 0.5) / h * 2 - 1


def value_noise(w, h, scale, octaves=4, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        s = scale * (2 ** o)
        gw, gh = int(math.ceil(w / s)) + 2, int(math.ceil(h / s)) + 2
        g = r.random((gh, gw)).astype(np.float32)
        y, x = np.mgrid[0:h, 0:w].astype(np.float32)
        gx, gy = x / s, y / s
        x0, y0 = gx.astype(int), gy.astype(int)
        fx, fy = gx - x0, gy - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        v = (g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx) * (1 - fy) + (g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx) * fy
        out += v * amp
        total += amp
        amp *= 0.5
    return out / total


def glow():
    x, y = grid(64, 64)
    d = np.sqrt(x * x + y * y)
    a = np.clip(1 - d, 0, 1) ** 2.2
    a += np.exp(-d * d * 18) * 0.6
    a = np.clip(a, 0, 1)
    img = np.stack([np.ones_like(a), np.ones_like(a), np.ones_like(a), a], -1)
    save(img, 'glow.png')


def flare():
    x, y = grid(64, 64)
    d = np.sqrt(x * x + y * y)
    core = np.exp(-d * d * 30)
    cross = np.exp(-np.abs(y) * 40) * np.clip(1 - np.abs(x), 0, 1) ** 1.5 + np.exp(-np.abs(x) * 40) * np.clip(1 - np.abs(y), 0, 1) ** 1.5
    halo = np.clip(1 - d, 0, 1) ** 3 * 0.35
    a = np.clip(core + cross * 0.8 + halo, 0, 1)
    save(np.stack([np.ones_like(a)] * 3 + [a], -1), 'flare.png')


def flame():
    frames, fw, fh = 16, 32, 32
    sheet = np.zeros((fh * frames, fw, 4), np.float32)
    base = value_noise(fw, fh * 4, 6, 4, seed=3)
    for f in range(frames):
        x, y = grid(fw, fh)
        u = (y + 1) / 2  # 0 = 上、1 = 下
        off = int(f * fh * 4 / frames)
        n = np.roll(base, -off, axis=0)[:fh, :]
        width = 0.15 + 0.75 * u ** 0.8
        shape = np.clip(1 - np.abs(x + (n - 0.5) * 0.5 * (1 - u)) / width, 0, 1)
        heat = np.clip(shape * (0.35 + 0.9 * u) + (n - 0.5) * 0.6 - (1 - u) * 0.25, 0, 1)
        r = np.clip(heat * 1.6, 0, 1)
        g = np.clip(heat * 1.25 - 0.25, 0, 1)
        b = np.clip(heat * 1.4 - 0.85, 0, 1)
        a = np.clip(heat * 1.8, 0, 1) * np.clip(u * 3, 0, 1)
        sheet[f * fh:(f + 1) * fh] = np.stack([r, g, b, a], -1)
    save(sheet, 'flame.png')


def smoke():
    x, y = grid(64, 64)
    d = np.sqrt(x * x + y * y)
    n = value_noise(64, 64, 8, 5, seed=7)
    a = np.clip(1 - d * (0.8 + n * 0.6), 0, 1) ** 1.5 * (0.6 + 0.4 * n)
    save(np.stack([np.ones_like(a) * (0.85 + 0.15 * n)] * 3 + [a], -1), 'smoke.png')


def petal():
    x, y = grid(32, 32)
    # 細長いしずく形（上が尖り、下が丸い）
    yy = (y + 1) / 2
    width = np.sin(np.clip(yy, 0, 1) * math.pi) ** 0.7 * (0.55 + 0.25 * yy)
    inside = np.clip(1 - np.abs(x) / np.maximum(width, 1e-3), 0, 1)
    a = np.clip(inside * 3, 0, 1) * (yy > 0.02)
    vein = np.exp(-np.abs(x) * 40) * 0.25
    shade = 0.82 + 0.18 * inside - vein
    save(np.stack([shade, shade, shade, a], -1), 'petal.png')


def streak():
    x, y = grid(16, 64)
    u = (y + 1) / 2
    a = np.exp(-x * x * 12) * u ** 1.5
    save(np.stack([np.ones_like(a)] * 3 + [np.clip(a, 0, 1)], -1), 'streak.png')


if __name__ == '__main__':
    glow()
    flare()
    flame()
    smoke()
    petal()
    streak()
    print('written to', os.path.normpath(OUT))
