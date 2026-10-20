"""宿命の結び糸 fate_thread の 16x16 の絵（2026-10-20）。金の小さな糸巻きから、輪になって結ばれた赤く光る糸が伸び、先にもう 1 つの結び目。濃い紺の縁取り・きらめきの点。

    python tools/gen_fate_thread.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/textures/item")

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (30, 18, 46, 255),     # 縁取り
    "y": (255, 209, 102, 255),  # 金
    "Y": (196, 140, 50, 255),   # 金の影
    "r": (255, 64, 96, 255),    # 赤い糸
    "R": (176, 30, 60, 255),    # 糸の影
    "p": (255, 170, 190, 255),  # 糸の光
    "w": (255, 255, 255, 255),  # きらめき
}

ROWS = [
    "............w...",
    "..........orro..",
    ".........orppro.",
    ".........orRRro.",
    "..........orro..",
    "..........or....",
    ".........or.....",
    "........or......",
    ".......or.......",
    "..oooo.or.......",
    ".oyyYYor........",
    ".oywYYrRo.......",
    ".oyyYYoro.......",
    ".oYYYYo.or......",
    "..oooo...orr.w..",
    "..........oo....",
]


def main():
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(ROWS):
        assert len(row) == 16, (y, len(row))
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    img.save(os.path.join(OUT, "fate_thread.png"))


if __name__ == "__main__":
    main()
