"""ルクシオン EX の絵（2026-10-27 ユーザー指定「色違い」）: ルクシオンの絵（luxion.png・luxion_glowmask.png）を黒と紅金に塗り替える。

- 白金の装甲（明るい暖色）→ 黒に近い暗い紫がかった鋼（明暗の差は残す）
- 金の縁・飾り → 少し深い金のまま
- 黒い下地 → わずかに紅を帯びた黒
- 赤・青の差し色 → 紅
- 発光（glowmask）: 白金の光 → 紅、金の光 → 金のまま

使い方: python tools/gen_luxion_ex.py
"""
import colorsys
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "automata_valkyries", "textures", "entity")


def recolor(px, glow):
    r, g, b, a = px
    if a == 0:
        return px
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    deg = h * 360
    warm = 12 <= deg <= 75
    if warm and (l >= 0.78 or (l >= 0.6 and s < 0.55)):  # 白金・淡い装甲 → 黒鋼（発光は紅）
        if glow:
            nh, nl, ns = 350 / 360, 0.5 + (l - 0.6) * 0.6, 0.95
        else:
            nh, nl, ns = 300 / 360, 0.08 + (l - 0.6) * 0.5, 0.12
    elif warm and l >= 0.30:  # 金 → 深い金
        nh, nl, ns = 42 / 360, l * 0.92, min(1.0, s * 1.15 + 0.05)
    elif s < 0.18:  # 黒・灰 → 紅を帯びた黒
        nh, nl, ns = 350 / 360, l * 0.85, 0.18
    else:  # 差し色 → 紅
        nh, nl, ns = 350 / 360, max(0.35, min(0.6, l)), 0.9
    nr, ng, nb = colorsys.hls_to_rgb(nh, nl, ns)
    return (round(nr * 255), round(ng * 255), round(nb * 255), a)


def convert(src, dst, glow):
    im = Image.open(os.path.join(TEX, src)).convert("RGBA")
    out = Image.new("RGBA", im.size)
    out.putdata([recolor(p, glow) for p in im.getdata()])
    out.save(os.path.join(TEX, dst))
    print("wrote", dst)


if __name__ == "__main__":
    convert("luxion.png", "luxion_ex.png", False)
    convert("luxion_glowmask.png", "luxion_ex_glowmask.png", True)
