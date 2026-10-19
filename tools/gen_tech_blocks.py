"""近未来的な建材（2026-10-15 ユーザー指定「近未来的な建材」）と、侵食基地の転送ゲートの 16x16 のブロックの絵。

  tech_panel_white  白い金属パネル（継ぎ目とリベット）
  tech_panel_dark   暗い金属パネル
  tech_grating      網目の床（穴は透ける。モデルは cutout）
  tech_hazard       警告の帯（黄と黒の斜め縞に金属の縁）
  tech_glass        強化ガラス（薄い青の板と金属の枠。cutout）
  tech_light_line   光る線の入った壁（暗いパネルに水色の光の線）
  tech_light_panel  照明パネル（白く光る面と枠）
  tech_phase_panel  紫に光るパネル（暗いパネルに紫の菱形）
  tech_sky_panel    空を映す照明パネル（地下の通路の天井。青空と雲の面と枠）
  chrono_gate       クロノ転送ゲート（暗い枠の中で紫の渦が光る）
  ruler_portal      眠れる時の門の膜（2026-10-19。32 枚のアニメーション: 藍の星空の上を金と紫の渦が回り、星が瞬く。半透明）
  ruler_gate_frame  時の門の枠（白い大理石に金の象嵌と紫の小さな宝石）
  ruler_gate_keystone 時の門の要石（金の時計の文字盤、紫の芯）

    python tools/gen_tech_blocks.py
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/textures/block")


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def panel(base, seam, light, dark, rnd):
    """継ぎ目（4 分割）・縁の明暗・四隅のリベット・少しのむら"""
    im = img()
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = shade(base, rnd.randint(-4, 4))
            if x == 0 or y == 0:
                c = light
            elif x == 15 or y == 15:
                c = dark
            elif x in (7, 8) and 1 <= y <= 14:
                c = seam if x == 7 else shade(light, -20)
            px[x, y] = c
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (5, 2), (10, 13)):
        px[x, y] = light
        px[x + 1, y + 1] = dark
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    rnd = random.Random(20261015)
    W, WS, WL, WD = (222, 226, 232, 255), (170, 176, 186, 255), (248, 250, 252, 255), (150, 156, 168, 255)
    D, DS, DL, DD = (50, 54, 64, 255), (28, 30, 36, 255), (84, 90, 104, 255), (22, 24, 30, 255)

    panel(W, WS, WL, WD, rnd).save(os.path.join(OUT, "tech_panel_white.png"))
    panel(D, DS, DL, DD, rnd).save(os.path.join(OUT, "tech_panel_dark.png"))

    im = img()  # 網目の床
    px = im.load()
    for y in range(16):
        for x in range(16):
            frame = x in (0, 15) or y in (0, 15)
            bar = x % 4 == 0 or y % 4 == 0
            if frame:
                px[x, y] = DL if (x == 0 or y == 0) else DD
            elif bar:
                px[x, y] = shade(D, 10 if (x + y) % 2 else 0)
    im.save(os.path.join(OUT, "tech_grating.png"))

    im = img()  # 警告の帯
    px = im.load()
    for y in range(16):
        for x in range(16):
            if y in (0, 15):
                px[x, y] = DL if y == 0 else DD
            else:
                px[x, y] = (232, 190, 40, 255) if ((x + y) // 4) % 2 == 0 else (30, 32, 38, 255)
    im.save(os.path.join(OUT, "tech_hazard.png"))

    im = img()  # 強化ガラス
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = (120, 130, 148, 255) if (x == 0 or y == 0) else (80, 88, 104, 255)
            elif x == y + 3 or x == y + 4:
                px[x, y] = (230, 245, 255, 150)  # 光の映り込み
            else:
                px[x, y] = (160, 210, 240, 60)
    im.save(os.path.join(OUT, "tech_glass.png"))

    im = panel(D, DS, DL, DD, rnd)  # 光る線の入った壁
    px = im.load()
    for x in range(1, 15):
        px[x, 7] = (120, 230, 255, 255)
        px[x, 8] = (200, 248, 255, 255)
        px[x, 6] = shade(px[x, 6], 18)
        px[x, 9] = shade(px[x, 9], 18)
    im.save(os.path.join(OUT, "tech_light_line.png"))

    im = img()  # 照明パネル
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = WD
            elif x in (1, 14) or y in (1, 14):
                px[x, y] = (210, 220, 230, 255)
            else:
                d = math.hypot(x - 7.5, y - 7.5) / 8
                v = int(255 - 18 * d)
                px[x, y] = (v, v, min(255, v + 4), 255)
    im.save(os.path.join(OUT, "tech_light_panel.png"))

    im = panel(D, DS, DL, DD, rnd)  # 紫に光るパネル
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if 4.0 <= d <= 5.0:
                px[x, y] = (176, 96, 255, 255)
            elif d < 2.5:
                px[x, y] = (226, 196, 255, 255)
    im.save(os.path.join(OUT, "tech_phase_panel.png"))

    im = img()  # 空を映す照明パネル
    px = im.load()
    clouds = [(4, 5, 3.2), (11, 9, 2.6), (7, 12, 2.0)]
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = WD
                continue
            t = y / 15
            c = (int(110 + 60 * t), int(170 + 50 * t), 255, 255)
            for cx, cy, r in clouds:
                if math.hypot((x - cx) * 0.7, y - cy) < r:
                    c = (245, 248, 255, 255)
            px[x, y] = c
    im.save(os.path.join(OUT, "tech_sky_panel.png"))

    im = img()  # クロノ転送ゲート
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px[x, y] = DL if (x == 0 or y == 0) else DD
            elif x in (1, 14) or y in (1, 14):
                px[x, y] = (255, 42, 74, 255) if (x + y) % 3 == 0 else D
            else:
                a = math.atan2(y - 7.5, x - 7.5)
                r = math.hypot(x - 7.5, y - 7.5)
                k = (math.sin(a * 3 + r * 1.1) + 1) / 2
                px[x, y] = (int(60 + 140 * k), int(20 + 60 * k), int(120 + 135 * k), 255)
    im.save(os.path.join(OUT, "chrono_gate.png"))

    frames = 32  # 眠れる時の門の膜（縦に 32 枚並べたアニメーション）
    im = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    px = im.load()
    stars = [(rnd.randint(1, 14), rnd.randint(1, 14), rnd.random()) for _ in range(14)]
    for f in range(frames):
        t = f / frames * math.tau
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                a = math.atan2(y - 7.5, x - 7.5)
                arm = (math.sin(3 * a - r * 0.85 + t) + 1) / 2          # 回る 3 本の渦の腕
                glow = max(0.0, 1.0 - r / 9.0)                          # 真ん中ほど明るい
                k = arm ** 2.2 * (0.55 + 0.45 * glow)
                base = (14, 10, 46)
                violet = (130, 60, 210)
                gold = (255, 214, 120)
                if k < 0.5:
                    c = tuple(int(base[i] + (violet[i] - base[i]) * k * 2) for i in range(3))
                else:
                    c = tuple(int(violet[i] + (gold[i] - violet[i]) * (k - 0.5) * 2) for i in range(3))
                px[x, y + 16 * f] = c + (int(190 + 50 * k),)
        for sx, sy, ph in stars:  # 瞬く星
            tw = (math.sin(t * 2 + ph * math.tau) + 1) / 2
            if tw > 0.55:
                px[sx, sy + 16 * f] = (255, 255, 240, 255)
    im.save(os.path.join(OUT, "ruler_portal.png"))
    with open(os.path.join(OUT, "ruler_portal.png.mcmeta"), "w", encoding="utf-8", newline="\n") as meta:
        meta.write('{\n  "animation": {\n    "frametime": 2,\n    "interpolate": true\n  }\n}\n')

    GOLD, GOLD_L, GOLD_D = (232, 184, 72, 255), (255, 226, 140, 255), (150, 108, 34, 255)
    im = img()  # 時の門の枠: 白い大理石に金の象嵌
    px = im.load()
    for y in range(16):
        for x in range(16):
            v = 228 + rnd.randint(-6, 6) - (8 if (x * 7 + y * 3) % 11 == 0 else 0)  # 大理石の筋
            px[x, y] = (v, v, min(255, v + 6), 255)
            if x in (0, 15) or y in (0, 15):
                px[x, y] = GOLD_D if (x == 15 or y == 15) else GOLD_L
            elif x in (2, 13) or y in (2, 13):
                px[x, y] = GOLD
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = (176, 96, 255, 255)
    for x, y in ((7, 6), (6, 7), (9, 8), (8, 9)):
        px[x, y] = (226, 196, 255, 255)
    im.save(os.path.join(OUT, "ruler_gate_frame.png"))

    im = img()  # 時の門の要石: 金の時計の文字盤
    px = im.load()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            if x in (0, 15) or y in (0, 15):
                px[x, y] = GOLD_D if (x == 15 or y == 15) else GOLD_L
            elif r > 7.0:
                px[x, y] = (236, 236, 242, 255)
            elif r > 6.0:
                px[x, y] = GOLD
            elif r > 4.8 and abs(math.sin(a * 6)) < 0.35:
                px[x, y] = GOLD_L  # 12 の目盛り
            elif r < 1.6:
                px[x, y] = (226, 196, 255, 255)
            else:
                px[x, y] = (40, 24, 86, 255)
    for i in range(1, 5):
        px[7, 7 - i] = GOLD_L       # 長針
    for i in range(1, 4):
        px[7 + i, 7] = GOLD_L       # 短針
    im.save(os.path.join(OUT, "ruler_gate_keystone.png"))
    print("wrote 13 textures to", OUT)


if __name__ == "__main__":
    main()
