"""ヴォイド・リポジトリの検索で使う日本語の読み（2026-10-29 ユーザー指定「このMODとバニラだけでも日本語対応して」。質問で: ひらがなで漢字も当たる・ローマ字で探せる）。

このMODとバニラの ja_jp の item.* / block.* の名前に、ひらがなの読みとローマ字（ヘボン式）を付けて
src/main/resources/assets/automata_valkyries/search/readings_ja.json に書く（{"翻訳キー": ["ひらがな", "romaji"]}）。
読みは pykakasi（辞書で漢字をかなに直す）で作るので、たまに違う読みになる（例: 剣 → つるぎ）。直したい物は OVERRIDES に足す。

使い方: pip install pykakasi してから python tools/gen_search_readings.py
（バニラの ja_jp は ForgeGradle の素材の置き場 ~/.gradle/caches/forge_gradle/assets から読む。runClient を一度動かしてあれば置いてある）
"""
import json
import os
import sys

import pykakasi

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MOD_LANG = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/lang/ja_jp.json")
OUT = os.path.join(ROOT, "src/main/resources/assets/automata_valkyries/search/readings_ja.json")
ASSETS = os.path.expanduser("~/.gradle/caches/forge_gradle/assets")

# 辞書の読みが違う物（名前の一部 → 読み）。名前に含まれていれば、その部分をこの読みにしてから残りを読む
OVERRIDES = {
    "剣": "けん",
}


def vanilla_lang():
    best = None
    for name in os.listdir(os.path.join(ASSETS, "indexes")):
        index = json.load(open(os.path.join(ASSETS, "indexes", name), encoding="utf-8"))
        entry = index.get("objects", {}).get("minecraft/lang/ja_jp.json")
        if entry:
            best = entry["hash"]
    if best is None:
        sys.exit("vanilla ja_jp.json was not found under " + ASSETS)
    return json.load(open(os.path.join(ASSETS, "objects", best[:2], best), encoding="utf-8"))


def main():
    kks = pykakasi.kakasi()
    names = {}
    names.update({k: v for k, v in vanilla_lang().items()})
    names.update(json.load(open(MOD_LANG, encoding="utf-8")))
    out = {}
    for key, name in sorted(names.items()):
        if not (key.startswith("item.") or key.startswith("block.")) or key.endswith(".desc") or "%" in name:
            continue
        if key.count(".") > 2 and not key.startswith("item.minecraft.potion") and not key.startswith("item.minecraft.splash_potion") \
                and not key.startswith("item.minecraft.lingering_potion") and not key.startswith("item.minecraft.tipped_arrow"):
            continue  # 説明などの下位のキーは除く（ポーションと矢は効果ごとの名前なので残す）
        text = name
        for word, reading in OVERRIDES.items():
            text = text.replace(word, reading)
        parts = kks.convert(text)
        hira = "".join(p["hira"] for p in parts).lower()
        roma = "".join(p["hepburn"] for p in parts).lower()
        if hira == name.lower() and roma == name.lower():
            continue  # かなも漢字も無い（英字だけ）
        out[key] = [hira, roma]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    print(len(out), "readings ->", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
