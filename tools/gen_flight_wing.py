"""光翼の飛翔機（tool_flight_wing、2026-10-15）の 16x16 のアイテムの絵。

真ん中に白く光る菱形の核と銀の枠、左右へ広がる光の翼（付け根の紫から先の水色へ。縁は明るく、羽の筋が 2 本）。

    python tools/gen_flight_wing.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/textures/item/tool_flight_wing.png")

# 記号ごとの色（RGBA）。. は透明
PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (40, 26, 70, 255),      # 縁取りの濃い紫
    "v": (150, 92, 255, 255),    # 翼の付け根の紫
    "m": (150, 170, 255, 255),   # 紫と水色の間
    "c": (143, 230, 255, 255),   # 翼の先の水色
    "e": (226, 248, 255, 255),   # 翼の縁の明るい光
    "f": (110, 70, 210, 255),    # 羽の筋
    "s": (184, 188, 204, 255),   # 銀の枠
    "d": (90, 94, 112, 255),     # 銀の影
    "w": (255, 255, 255, 255),   # 核の白
    "p": (255, 190, 245, 255),   # 核のまわりの桃色の光
}

# 左半分（8 列）を描き、右半分は左右を返して写す
LEFT = [
    "........",
    "oo......",
    "oeeo....",
    "oceeoo..",
    ".occeeo.",
    ".ocmcfeo",
    "..omcfmo",
    "..omfvvs",
    "..omvvsp",
    "..ocmvds",
    ".ocmfvo.",
    ".ocmfo..",
    "oeco....",
    "oeo.....",
    "oo......",
    "........",
]


def main():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y, row in enumerate(LEFT):
        for x, ch in enumerate(row):
            px[x, y] = PALETTE[ch]
            px[15 - x, y] = PALETTE[ch]
    # 真ん中の 2 列（7・8 列目）は核: 銀の枠に桃色の光と白い芯
    px[7, 8], px[8, 8] = PALETTE["w"], PALETTE["w"]
    px[7, 7], px[8, 7] = PALETTE["p"], PALETTE["p"]
    px[7, 9], px[8, 9] = PALETTE["p"], PALETTE["p"]
    px[7, 6], px[8, 6] = PALETTE["s"], PALETTE["s"]
    px[7, 10], px[8, 10] = PALETTE["s"], PALETTE["s"]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
