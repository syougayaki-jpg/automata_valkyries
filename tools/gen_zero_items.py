"""零号機の素体 zero_frame と零号機再起動パッチ item_zero_reboot_patch の 16x16 の絵（2026-10-20）。

    python tools/gen_zero_items.py

素体: 暗い銀の縁の黒い寝台型の外殻に、眠った零号機（銀白の髪・とげの冠・赤い胸のコア・赤紫の継ぎ目）が横たわる。
パッチ: 黒い砲金色のカートリッジに赤く光る表示窓と、星の記憶の欠片（ピンクの星）、銀の端子。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/textures/item")

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (14, 12, 18, 255),     # 縁取り
    "k": (34, 36, 44, 255),     # 黒い砲金
    "K": (58, 61, 72, 255),     # 砲金の光
    "s": (126, 132, 144, 255),  # 暗い銀
    "S": (190, 196, 208, 255),  # 銀
    "h": (244, 245, 248, 255),  # 髪の光
    "H": (200, 204, 214, 255),  # 髪の地
    "f": (252, 230, 218, 255),  # 肌
    "r": (255, 40, 88, 255),    # 赤の発光
    "R": (150, 14, 40, 255),    # 赤の影
    "p": (255, 143, 176, 255),  # 赤の光
    "m": (255, 96, 208, 255),   # 赤紫
    "w": (255, 255, 255, 255),  # きらめき
}

FRAME = [
    "................",
    "...s.s.s........",
    "..oSsSsSo.......",
    ".ohhHHHhHo......",
    ".oHffffHHo......",
    ".oHrRrRHo.......",
    "..offffo........",
    ".ookkkkooo......",
    "okKkrrkKkKo.....",
    "okkkrpkkkkKo....",
    "okmkkkkkmkkKo...",
    "oKkkkkkkkkmkKo..",
    ".okKmkkkKkkkkKo.",
    "..okkkksskkkmkko",
    "...oossSSsskkkso",
    "......ooooooooo.",
]

PATCH = [
    "................",
    ".....oooooo.....",
    "....oKKKKKKo....",
    "....oKSSSSKo....",
    "...ookkkkkkoo...",
    "...oKrrrrrrKo...",
    "...oKrpmmprKo...",
    "...oKrmwwmrKo...",
    "...oKrpmmprKo...",
    "...oKrrrrrrKo...",
    "...okkkkkkkko...",
    "...okRkRkRkko...",
    "...ookkkkkkoo...",
    "....oSsSsSso....",
    "....oSoSoSoo....",
    ".....o.o.o......",
]


def draw(rows, name):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        assert len(row) == 16, (name, y, len(row))
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    img.save(os.path.join(OUT, name + ".png"))


def main():
    draw(FRAME, "zero_frame")
    draw(PATCH, "item_zero_reboot_patch")


if __name__ == "__main__":
    main()
