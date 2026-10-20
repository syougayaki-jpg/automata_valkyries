"""スライム湧かなくする君 slime_ward の 16x16 の絵（2026-10-20）。鉄の枠の中に、スライムの顔と、それを打ち消す赤い禁止の印（丸と斜線）。

    python tools/gen_slime_ward.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/textures/block")

PALETTE = {
    "o": (58, 62, 70, 255),     # 枠の影
    "f": (150, 156, 166, 255),  # 鉄の枠
    "F": (198, 204, 212, 255),  # 枠の光
    "g": (112, 196, 92, 255),   # スライム
    "G": (78, 150, 64, 255),    # スライムの影
    "l": (170, 232, 150, 255),  # スライムの光
    "k": (34, 70, 30, 255),     # 目と口
    "r": (226, 40, 52, 255),    # 禁止の印
    "R": (150, 20, 30, 255),    # 印の影
}

ROWS = [
    "FFFFFFFFFFFFFFFo",
    "FffffffffffffffO",
    "Ff..rrrrrrrr..fo",
    "Ff.rrRggggRrr.fo",
    "Ffrrlgggggg.rrfo",
    "FfrRglgggg.rRrfo",
    "Ffrgkkggkr.Rgrfo",
    "Ffrgkkgg.rRggrfo",
    "Ffrggggg.rrggrfo",
    "Ffrgggg.rRggGrfo",
    "FfrGgkkrRkkgGrfo",
    "FfrrGGrRGGGGrrfo",
    "Ff.rrRGGGGGRrr.o",
    "Ff..rrrrrrrr..fo",
    "Fffffffffffffffo",
    "oooooooooooooooo",
]


def main():
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(ROWS):
        assert len(row) == 16, (y, len(row))
        for x, ch in enumerate(row):
            if ch == ".":
                ch = "G"                                   # 印の外の地は暗いスライム
            if ch == "O":
                ch = "o"
            img.putpixel((x, y), PALETTE[ch])
    img.save(os.path.join(OUT, "slime_ward.png"))


if __name__ == "__main__":
    main()
