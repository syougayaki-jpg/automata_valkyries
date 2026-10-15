"""英雄機の呼び出しの材料になるまれなドロップの 16x16 の絵（2026-10-15）。フォトン剣の欠片と同じく、濃い紺の縁取り・明るい中身・きらめきの点。

- 歪曲障壁の核片 distortion_core_shard: 欠けた青いガラスの結晶。中に三原色（赤・緑・青）の光の粒が揺らぐ
- 黒死蝶の繭 black_death_cocoon: 糸で下がる暗い紫の繭。殻の継ぎ目が桃色と水色に光る（中の光の蝶）

    python tools/gen_signal_drops.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/textures/item")

SHARD = {
    "palette": {
        ".": (0, 0, 0, 0),
        "o": (22, 26, 52, 255),     # 縁取り
        "e": (220, 240, 255, 255),  # 明るい縁
        "c": (170, 215, 255, 255),  # 淡い青
        "b": (86, 146, 255, 255),   # 青いガラス
        "d": (52, 92, 200, 255),    # 影の青
        "r": (255, 84, 84, 255),    # 三原色の光（赤）
        "g": (84, 255, 130, 255),   # （緑）
        "u": (120, 150, 255, 255),  # （青）
        "w": (255, 255, 255, 255),  # きらめき
    },
    "rows": [
        "................",
        "...........w....",
        ".......oo.......",
        "......oeeo...w..",
        ".....oecbeo.....",
        "....oecbbdbo....",
        "....oebrbbdo....",
        "...oebbbbgbdo...",
        "...oecbbbbbdo...",
        "..oebgbbbrbdo...",
        "..oebbbubbdo....",
        "...obbbbbddo....",
        "...oebbbddo.....",
        "....oddddo......",
        ".....oooo.......",
        "................",
    ],
}

COCOON = {
    "palette": {
        ".": (0, 0, 0, 0),
        "s": (210, 200, 232, 255),  # 吊り下げる糸
        "o": (24, 12, 42, 255),     # 縁取り
        "k": (74, 42, 116, 255),    # 暗い紫の殻
        "h": (132, 96, 190, 255),   # 殻の明るい所
        "n": (48, 26, 80, 255),     # 殻の影
        "p": (255, 140, 235, 255),  # 光る継ぎ目（桃色）
        "c": (143, 230, 255, 255),  # 光る継ぎ目（水色）
        "w": (255, 255, 255, 255),  # きらめき
    },
    "rows": [
        ".......s........",
        ".......s........",
        "......ooo.......",
        ".....ohkko..w...",
        "....ohkpkno.....",
        "....ohkkcno.....",
        "...ohkpkkkno....",
        "...ohkkkpkno....",
        "...ohckkkkno....",
        "...ohkkpkcno....",
        "....okkkkno.....",
        "....okkcknno....",
        ".....oknno......",
        "..w...ooo.......",
        "................",
        "................",
    ],
}


def paint(name, spec):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y, row in enumerate(spec["rows"]):
        assert len(row) == 16, (name, y, len(row))
        for x, ch in enumerate(row):
            px[x, y] = spec["palette"][ch]
    path = os.path.join(OUT, name + ".png")
    img.save(path)
    print("wrote", path)


def main():
    os.makedirs(OUT, exist_ok=True)
    paint("distortion_core_shard", SHARD)
    paint("black_death_cocoon", COCOON)


if __name__ == "__main__":
    main()
