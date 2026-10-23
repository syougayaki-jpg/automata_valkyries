"""零の墓標への紅の門の膜（2026-10-20 ユーザー指定「裏ボスをつくりますか」。時の狭間の奥の門）の 16x16 のブロックの絵。

  zero_portal        眠っている紅の門の膜（32 枚のアニメーション: 黒い闇の上を暗い紅と紫の渦がゆっくり回り、灰の粒が瞬く。半透明）
  zero_portal_awake  目覚めた紅の門の膜（紅と薄紅が明るく、逆向きに速く渦巻く。金の時の門と見分けがつくように）

    python tools/gen_zero_portal.py
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/textures/block")
FRAMES = 32


def mix(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def membrane(name, speed, base, mid, top, power, alpha, spark, frametime):
    rnd = random.Random(20261020)
    ash = [(rnd.randint(1, 14), rnd.randint(1, 14), rnd.random()) for _ in range(12)]
    im = Image.new("RGBA", (16, 16 * FRAMES), (0, 0, 0, 0))
    px = im.load()
    for f in range(FRAMES):
        t = f / FRAMES * math.tau * speed
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                a = math.atan2(y - 7.5, x - 7.5)
                arm = (math.sin(-2 * a - r * 0.95 + t) + 1) / 2           # 逆向きに回る 2 本の渦の腕（時の門は 3 本）
                ring = (math.sin(r * 1.6 - t * 1.5) + 1) / 2 * 0.25        # 外へ流れる波紋
                glow = max(0.0, 1.0 - r / 9.5)
                k = min(1.0, arm ** power * (0.5 + 0.5 * glow) + ring * glow)
                c = mix(base, mid, k * 2) if k < 0.5 else mix(mid, top, (k - 0.5) * 2)
                px[x, y + 16 * f] = c + (int(alpha[0] + alpha[1] * k),)
        for sx, sy, ph in ash:  # 瞬く灰の粒
            if (math.sin(t * 2 + ph * math.tau) + 1) / 2 > 0.6:
                px[sx, sy + 16 * f] = spark
    im.save(os.path.join(OUT, name + ".png"))
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as meta:
        meta.write('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": true\n  }\n}\n' % frametime)


def main():
    membrane("zero_portal", 1, (10, 4, 10), (110, 14, 40), (150, 60, 170), 2.2, (180, 50), (190, 180, 190, 255), 2)
    membrane("zero_portal_awake", 2, (40, 6, 20), (220, 30, 60), (255, 190, 210), 1.5, (205, 45), (255, 240, 245, 255), 1)


if __name__ == "__main__":
    main()
