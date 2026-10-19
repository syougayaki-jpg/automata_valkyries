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
  bastion_altar_top / _side / _spent_top / _spent_side  浮遊要塞の封印の召喚台（2026-10-19。上は金の魔法陣と紫の封印の鎖、使い終わると光が消える）
  ruler_portal      眠れる時の門（深い藍の面に金の時計の目盛りと針）

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

    for spent in (False, True):  # 封印の召喚台
        suffix = "_spent" if spent else ""
        im = img()  # 上: 金の円と、紫の十字の鎖（使い終わると灰色）
        px = im.load()
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                if x in (0, 15) or y in (0, 15):
                    px[x, y] = DL if (x == 0 or y == 0) else DD
                elif 5.2 <= r <= 6.4:
                    px[x, y] = (120, 110, 90, 255) if spent else (255, 209, 102, 255)
                elif r < 2.2:
                    px[x, y] = (70, 70, 80, 255) if spent else (226, 196, 255, 255)
                elif (x in (7, 8) or y in (7, 8)) and r < 5.2:
                    px[x, y] = (60, 62, 70, 255) if spent else (176, 96, 255, 255)
                else:
                    px[x, y] = shade(D, rnd.randint(-4, 4))
        im.save(os.path.join(OUT, "bastion_altar" + suffix + "_top.png"))
        im = panel(D, DS, DL, DD, rnd)  # 横: 暗いパネルに金の帯と紫の目
        px = im.load()
        for x in range(1, 15):
            px[x, 3] = (120, 110, 90, 255) if spent else (255, 209, 102, 255)
            px[x, 12] = px[x, 3]
        for y in range(6, 10):
            for x in range(6, 10):
                px[x, y] = (60, 62, 70, 255) if spent else (176, 96, 255, 255)
        im.save(os.path.join(OUT, "bastion_altar" + suffix + "_side.png"))

    im = img()  # 眠れる時の門
    px = im.load()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            k = (math.sin(r * 1.3) + 1) / 2
            px[x, y] = (int(10 + 20 * k), int(16 + 30 * k), int(50 + 60 * k), 255)
            a = math.atan2(y - 7.5, x - 7.5)
            if 6.0 <= r <= 7.2 and abs(math.sin(a * 6)) < 0.3:
                px[x, y] = (255, 209, 102, 255)  # 12 の目盛り
    for t in range(1, 6):
        px[7 + (t if t < 4 else 3), 7] = (255, 240, 176, 255)  # 短針
        px[7, 7 - t] = (255, 240, 176, 255)  # 長針
    im.save(os.path.join(OUT, "ruler_portal.png"))
    print("wrote 15 textures to", OUT)


if __name__ == "__main__":
    main()
