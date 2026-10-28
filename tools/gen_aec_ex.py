"""ガリアフォート EX・メメントモリ EX の絵（2026-10-27 ユーザー指定「色違い」）: 元の絵を塗り替える。

- ガリアフォート EX（黒鉄と赤熱）: 鋼の灰 → 暖かみのある黒鉄、青・紫の光 → 赤熱の橙、赤 → 明るい紅
- メメントモリ EX（白と金の光体＝反転した明るい死蝶）: 紫がかった黒い体 → 象牙の白（明暗は残す）、紫・水色・桃色の光 → 金と白

使い方: python tools/gen_aec_ex.py
"""
import colorsys
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "automata_valkyries", "textures", "entity")


def gallia(px, glow):
    r, g, b, a = px
    if a == 0:
        return px
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    deg = h * 360
    if s < 0.15:  # 鋼 → 黒鉄
        nh, nl, ns = 15 / 360, l * 0.62, 0.10
    elif 180 <= deg <= 290:  # 青・紫の光 → 赤熱の橙
        nh, nl, ns = 20 / 360, min(0.75, l * 1.05 + 0.05), 1.0
    elif deg >= 320 or deg <= 15:  # 赤 → 明るい紅
        nh, nl, ns = 355 / 360, min(0.7, l * 1.1), 1.0
    else:
        nh, nl, ns = h, l * 0.8, s
    nr, ng, nb = colorsys.hls_to_rgb(nh, nl, ns)
    return (round(nr * 255), round(ng * 255), round(nb * 255), a)


def memento(px, glow):
    r, g, b, a = px
    if a == 0:
        return px
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    if l < 0.45 and not glow:  # 紫がかった黒い体 → 象牙の白
        nh, nl, ns = 40 / 360, 0.62 + l * 0.85, 0.22
    elif s < 0.15:  # 白 → 白
        nh, nl, ns = 45 / 360, l, 0.2
    else:  # 光 → 金（明るい所ほど白に寄せる）
        nh, nl, ns = 44 / 360, min(0.82, 0.35 + l * 0.55), 0.95 - max(0.0, l - 0.7) * 1.5
    nr, ng, nb = colorsys.hls_to_rgb(nh, nl, max(0.0, min(1.0, ns)))
    return (round(nr * 255), round(ng * 255), round(nb * 255), a)


def convert(src, dst, fn, glow):
    im = Image.open(os.path.join(TEX, src)).convert("RGBA")
    out = Image.new("RGBA", im.size)
    out.putdata([fn(p, glow) for p in im.getdata()])
    out.save(os.path.join(TEX, dst))
    print("wrote", dst)


if __name__ == "__main__":
    convert("galliafort.png", "galliafort_ex.png", gallia, False)
    convert("galliafort_glowmask.png", "galliafort_ex_glowmask.png", gallia, True)
    convert("memento_mori.png", "memento_mori_ex.png", memento, False)
    convert("memento_mori_glowmask.png", "memento_mori_ex_glowmask.png", memento, True)
