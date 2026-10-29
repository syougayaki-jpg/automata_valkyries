"""
時間を扱うクロノリンク・マシン（2026-10-29 ユーザー指定「時間を扱う機械を直接追加して」。時間加速器・時間巻き戻し機）の前面の絵とモデル・ブロックの状態を作る。

    python tools/gen_time_machines.py

テザー・パイロンの前面（ティアごとの枠・地の色。T2 からは動く絵）を土台に、真ん中の 8x8 の印だけ描き替える。
  chrono_accelerator … 時計の文字盤と速く回る針（加速）
  chrono_rewinder    … 砂時計（巻き戻し）
出力（assets/automata_valkyries/ の下）: textures/block/<id>_front_t<n>[_on].png（＋ .mcmeta）、models/block/<id>_t<n>[_on].json、
blockstates/<id>.json、models/item/<id>.json
"""
import json
import os
import shutil

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries')
TEX = os.path.join(ASSETS, 'textures', 'block')
NS = 'automata_valkyries'

# 8x8 の印: b = 地、a = 光の色（枠の発光色の暗い方）、g = 明るい部品
GLYPHS = {
    'chrono_accelerator': [
        '..aaaa..',
        '.a.g..a.',
        'a..g...a',
        'a..g...a',
        'a...gg.a',
        'a......a',
        '.a....a.',
        '..aaaa..',
    ],
    'chrono_rewinder': [
        '.gggggg.',
        '..gaag..',
        '...ag...',
        '...gg...',
        '..g..g..',
        '.g.aa.g.',
        '.gaaaag.',
        '.gggggg.',
    ],
}


def front(machine, tier, on):
    src = os.path.join(TEX, f'tether_pylon_front_t{tier}{"_on" if on else ""}.png')
    img = Image.open(src).convert('RGBA')
    frames = img.height // 16
    for f in range(frames):
        oy = f * 16
        bg = img.getpixel((4, 4 + oy))
        accent = img.getpixel((12, 6 + oy))   # 枠の内側の明るい縦線（発光色）
        dim = tuple(int(bg[i] * 0.45 + accent[i] * 0.55) for i in range(3)) + (255,)
        bright = img.getpixel((7, 6 + oy))
        if on:
            bright = accent
            dim = tuple(int(bg[i] * 0.2 + accent[i] * 0.8) for i in range(3)) + (255,)
        pal = {'b': bg, 'a': dim, 'g': bright}
        for y, row in enumerate(GLYPHS[machine]):
            for x, c in enumerate(row):
                img.putpixel((4 + x, 4 + y + oy), pal['b'] if c == '.' else pal[c])
    name = f'{machine}_front_t{tier}{"_on" if on else ""}'
    img.save(os.path.join(TEX, name + '.png'))
    meta = src + '.mcmeta'
    if os.path.exists(meta):
        shutil.copyfile(meta, os.path.join(TEX, name + '.png.mcmeta'))
    return name


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def main():
    for machine in GLYPHS:
        variants = {}
        for tier in (1, 2, 3, 4):
            for on in (False, True):
                tex = front(machine, tier, on)
                model = f'{machine}_t{tier}{"_on" if on else ""}'
                write(os.path.join(ASSETS, 'models', 'block', model + '.json'), {
                    'parent': 'minecraft:block/orientable',
                    'textures': {'front': f'{NS}:block/{tex}', 'side': f'{NS}:block/chrono_side_t{tier}', 'top': f'{NS}:block/chrono_top_t{tier}'}})
                for facing, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                    v = {'model': f'{NS}:block/{model}'}
                    if rot:
                        v['y'] = rot
                    variants[f'facing={facing},lit={"true" if on else "false"},tier={tier}'] = v
        write(os.path.join(ASSETS, 'blockstates', machine + '.json'), {'variants': variants})
        write(os.path.join(ASSETS, 'models', 'item', machine + '.json'), {
            'parent': f'{NS}:block/{machine}_t1',
            'overrides': [{'predicate': {f'{NS}:tier': t}, 'model': f'{NS}:block/{machine}_t{t}'} for t in (2, 3, 4)]})
    print('time machines written')


if __name__ == '__main__':
    main()
