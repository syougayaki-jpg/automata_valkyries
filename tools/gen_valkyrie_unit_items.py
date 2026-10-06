"""
戦術兵姫の装備ユニット（外装フレーム・背部ユニット・ロジックチップ、2026-10-06）のアイテム絵（16x16）とアイテムモデルを作る。

    python tools/gen_valkyrie_unit_items.py [プレビューの出力先.png]

出力（assets/automata_valkyries/ の下）:
  textures/item/<id>.png      … 36 種
  models/item/<id>.json       … item/generated

絵は文字の格子で描く。8 文字の行は左半分で、右半分は左右反転して影の色に寄せる（左上から光が当たる）。
色の決まり: 地は紺・ガンメタル、発光はシアン（#3BE8FF）。
  ヴァルキリー = 白銀＋シアン / フォートレス = 重いガンメタル＋橙 / ミラージュ = 黒＋紫・桃の発光線 /
  ガリア・フォートレス（背部ユニット）= 暗いガンメタル＋金の発電機と、まわりを回る金縁の盾
"""
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries')
TEX_DIR = os.path.join(ASSETS, 'textures', 'item')
MODEL_DIR = os.path.join(ASSETS, 'models', 'item')

CYAN = 0x3BE8FF

# 右半分に写すときの影の寄せ方
SHADE = {'h': 'b', 'b': 's'}


def rgb(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)


def grid(rows):
    out = []
    for r in rows:
        if len(r) == 8:
            r = r + ''.join(SHADE.get(c, c) for c in reversed(r))
        assert len(r) == 16, (r, len(r))
        out.append(r)
    while len(out) < 16:
        out.append('.' * 16)
    assert len(out) == 16
    return out


def paint(rows, pal, img=None):
    img = img or Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, r in enumerate(grid(rows)):
        for x, c in enumerate(r):
            if c == '.':
                continue
            img.putpixel((x, y), rgb(pal[c]))
    return img


# ------------------------------------------------------------------ 色

PAL_VALKYRIE = {'o': 0x1C2233, 'h': 0xF2F5FA, 'b': 0xC6CEDC, 's': 0x8992A8, 'g': CYAN, 'a': 0x9FF4FF,
                'v': 0x1A7FB0, 'V': 0xD4FBFF}
PAL_FORTRESS = {'o': 0x14171D, 'h': 0x7A8391, 'b': 0x59616D, 's': 0x3A4049, 'g': 0xFF9A2E, 'a': 0xFFB860,
                'v': 0xE0661A, 'V': 0xFFE2B4}
PAL_MIRAGE = {'o': 0x060609, 'h': 0x3B3550, 'b': 0x25212F, 's': 0x15131B, 'g': 0xC566FF, 'a': 0xFF6AD5,
              'v': 0x6A2CC8, 'V': 0xFFB8F0}
PAL_GALLIA = {'o': 0x05060A, 'h': 0x5C616E, 'b': 0x3D424D, 's': 0x262930, 'A': 0xE8C060, 'g': 0xFF4060,
              'n': 0x101640}
PAL_UNIT = {'o': 0x0E1220, 'h': 0x5E6A86, 'b': 0x3B455C, 's': 0x252C3D, 'g': CYAN, 'a': 0xA8F6FF,
            'V': 0xF0FEFF, 'k': 0x1A2034}
# ハイマニューバスラスターの上位: Mk-III は白い翼、Ⅳ は白＋金＋白シアンの発光
PAL_THRUSTER3 = dict(PAL_UNIT, **{'h': 0x7C8AA8, 'b': 0x56627E, 's': 0x343D52, 'w': 0xE6ECF6, 'a': 0x6FF0FF})
PAL_THRUSTER4 = {'o': 0x141428, 'h': 0xF4F7FF, 'b': 0xC4CCDE, 's': 0x8A93AE, 'g': 0x9FF8FF, 'a': 0xC8FFFF,
                 'V': 0xFFFFFF, 'w': 0xF8FAFF, 'A': 0xFFD25A}
PAL_MEMENTO = dict(PAL_UNIT, **{'p': 0x8A3CFF, 'P': 0xD49BFF, 'w': 0xFFFFFF, 'k': 0x0A0812, 'g': 0xB060FF})

# ------------------------------------------------------------------ 外装フレーム

FRAME_SHAPES = {
    'valkyrie': {
        # シャープな白銀のヘルメット＋ヘッドセット（耳当てにシアン）、額に V の稜線
        'head': [
            '......og',
            '.....ohg',
            '....ohhg',
            '...ohhhh',
            '..ohhhhh',
            '..ohhhhh',
            '.oohhhhh',
            'oaoooooo',
            'ogovvvVv',
            'ogovvvvv',
            'oaoohhhh',
            '.o.ohhhh',
            '...oshhh',
            '....osss',
            '.....ooo',
        ],
        'chest': [
            '........',
            '.oooo...',
            'ohhhhooo',
            'ohhhhhhh',
            'ohhohhgg',
            'ohhohgVV',
            'ohaohhgg',
            'ohaohhhg',
            'ooooohhh',
            '...ohhhh',
            '...ohgoh',
            '...ohhhh',
            '...osshh',
            '...ossss',
            '...ooooo',
        ],
        'legs': [
            '........',
            '..oooooo',
            '..ohhhhg',
            '..ohhhhh',
            '..ohhhoo',
            '..ohhho.',
            '..ohgho.',
            '..ohhho.',
            '..ohhho.',
            '..oaaao.',
            '..ohhho.',
            '..ohgho.',
            '..ohhho.',
            '.ohhhho.',
            '.oooooo.',
        ],
    },
    'fortress': {
        # 角張った重装ヘルメット、橙の細いスリット
        'head': [
            '........',
            '........',
            '..oooooo',
            '.ohhhhha',
            'ohhhhhha',
            'ohhhhhhh',
            'ohhhhhhh',
            'ohoooooo',
            'ohoggggg',
            'ohoooooo',
            'ohhhshhh',
            'ohhoshhh',
            'ohhoshhh',
            'oaaossss',
            'oooooooo',
        ],
        # 巨大な肩部装甲
        'chest': [
            '........',
            'oooooo..',
            'ohhhhhoo',
            'ohhhhhhh',
            'oaaaahhh',
            'ooooohhh',
            '.ohoohhg',
            '.ohoohgV',
            '.oooohhg',
            '...ohhhh',
            '...ohsoh',
            '...ohsoh',
            '...ohhhh',
            '...oaass',
            '...ooooo',
        ],
        'legs': [
            '........',
            '.ooooooo',
            '.ohhhhhh',
            '.oaaaaaa',
            '.ohhhhoo',
            'oohhhho.',
            'ohhhhho.',
            'ohhshho.',
            'ohhhhho.',
            'oaaaaao.',
            'ohhhhho.',
            'ohshsho.',
            'ohhhhho.',
            'ohhhhho.',
            'ooooooo.',
        ],
    },
    'mirage': {
        # 軽装のサイバー猫耳ヘッドギア＋紫の発光帯＋ゴーグル
        'head': [
            '........',
            '..o.....',
            '.oao....',
            '.oaao...',
            '.oaahooo',
            '.ohhhhhh',
            'ohhhhhhh',
            'ohhhhhhh',
            'oggggggg',
            'ohovvvvV',
            'ohovvvvv',
            '.ohooooo',
            '..ohhhhh',
            '...ossss',
            '....oooo',
        ],
        # 細身の発光ラインスーツ（線が胸の中心へ集まる）
        'chest': [
            '........',
            '..ooo...',
            '.ohhhooo',
            '.ohhhhhh',
            '.ohghhhh',
            '.ohhghhh',
            '.ohohghh',
            '.ogohhgh',
            '.ooohhhg',
            '...ohhhg',
            '...ohhga',
            '...ohghh',
            '...oghhh',
            '...ossss',
            '...ooooo',
        ],
        'legs': [
            '........',
            '...ooooo',
            '...ohhhg',
            '...ohhhh',
            '...ohgoo',
            '...ohgo.',
            '...ohho.',
            '...ogho.',
            '...ohgo.',
            '...ohao.',
            '...ohgo.',
            '...ogho.',
            '...ohgo.',
            '..ohhho.',
            '..ooooo.',
        ],
    },
}

FRAME_PALS = {'valkyrie': PAL_VALKYRIE, 'fortress': PAL_FORTRESS, 'mirage': PAL_MIRAGE}

# ヴァルキリーフレーム Mk-II〜Ⅳ（アストライア、2026-10-06）: Mk-I の絵の行を差し替えて飾りを足す（{部位: {行: 新しい行}}）
# Mk-II = 光の線を増やす / Mk-III = 飾り・アンテナ・肩のひれ・膝当て（銀とシアン） / Ⅳ = 金の冠・重ねた肩当て・前垂れ・白シアンの光
PAL_VALKYRIE4 = dict(PAL_VALKYRIE, **{'A': 0xFFD25A, 'g': 0x9FF8FF, 'a': 0xD8FFFF})
VALKYRIE_TIER_ROWS = {
    2: {
        'head': {5: '..ohgggg', 12: '...oshgg'},
        'chest': {3: 'ohghhhhh', 11: '...ogggg'},
        'legs': {3: '..oggggg', 8: '..ohgho.'},
    },
    3: {
        'head': {0: '..o...og', 1: '..ao.ohg', 2: '...aohhg', 5: '..ohgggg', 12: '...oshgg'},
        'chest': {0: 'oo......', 1: 'oaooo...', 3: 'ohghhhhh', 11: '...ogggg', 12: '...oaahh'},
        'legs': {3: '..oggggg', 8: '.oohgho.', 9: '.oaaaao.', 10: '.oohhho.'},
    },
    4: {
        'head': {0: '...A..oA', 1: '..aAAoAA', 2: '...AoAAA', 5: '..ohgggg', 7: 'oAoooooo', 10: 'oAoohhhh', 12: '...oshgg'},
        'chest': {0: 'oo......', 1: 'oAAAA...', 2: 'oAAAAooo', 3: 'ohghhhhh', 11: '...ogggg', 12: '...oAAAA', 13: '...ohgsg'},
        'legs': {2: '..oAAAAA', 3: '..oggggg', 8: '.oohgho.', 9: '.oAAAAo.', 10: '.oohhho.', 13: '.oAAAAo.'},
    },
}


def valkyrie_tier_rows(tier, part):
    rows = list(FRAME_SHAPES['valkyrie'][part])
    for i, r in VALKYRIE_TIER_ROWS[tier][part].items():
        rows[i] = r
    return rows

# ------------------------------------------------------------------ 背部ユニット

# ガリア・フォートレス（T3 の背部ユニット、2026-10-06 に全身外装から変更）: 中央の発電機（赤い芯）のまわりを 4 枚の金縁の盾が回る
GALLIA = [
    '......oooo......',
    '.....ohhAbo.....',
    '.....ohAAbo.....',
    '......obbo......',
    '.oo..........oo.',
    'ohho..oooo..ohbo',
    'ohAo.ohAAbo.oAbo',
    'oAAo.oAggAo.oAAo',
    'ohAo.oAggAo.oAbo',
    'ohbo.obAAso.ohso',
    '.oo...oooo...oo.',
    '......oooo......',
    '.....ohhAbo.....',
    '.....ohAAbo.....',
    '......obbo......',
]

THRUSTER = [
    '........',
    '.ooooo..',
    '.ohhho..',
    '.ohghooo',
    '.ohghohh',
    '.ohghohg',
    '.ohghohh',
    '.ohghooo',
    '.ohhho..',
    '.oshso..',
    'oshhhso.',
    'ossssso.',
    '..aga...',
    '...g....',
]

# ハイマニューバスラスターの上位（2026-10-06）。ティアが上がるほどノズル・翼が増え、色は白・金・白シアンの発光へ寄る
# Mk-II: 大きくなった双発＋外の小さなひれ（地の色は Mk-I と同じ）
THRUSTER_MK2 = [
    '..ooooo.',
    '..ohhho.',
    '.oohaho.',
    'ohohghoo',
    'ohohghoh',
    'oaohghog',
    'ooohghoh',
    '..ohghoo',
    '..ohgho.',
    '.oohhho.',
    'ohoshso.',
    'oaossso.',
    'oo.aga..',
    '...aga..',
    '....g...',
]

# Mk-III: 後ろへ流れる翼のような推進翼（白い翼に光の筋）＋三連ノズル
THRUSTER_MK3 = [
    'oo......',
    'owo.....',
    'oawooooo',
    '.oawhgho',
    '..owhgho',
    'oo.ohgho',
    'owoohgho',
    'oawohgho',
    '.oawhgho',
    '..owhgho',
    '...ohhho',
    '..oshhso',
    '..osssso',
    '....aga.',
    '.....g..',
]

# Ⅳ（ヴァルキュリア）: 大きく広がる光の翼（白い羽に金の縁、白シアンの光の羽）＋中央の金の推進器
THRUSTER_MK4 = [
    'A.......',
    'oA......',
    'owAoo...',
    'owaAwoo.',
    '.owaaAwo',
    '.owwaaAo',
    '..owwaao',
    '..oowwoA',
    '...ooohh',
    '..oAoohg',
    '.oaAoohV',
    '..oo.ohg',
    '.....osA',
    '.....aVa',
    '......a.',
]

FOLDING_CANNON = [
    '..oo....',
    '.ogho...',
    '.ohho...',
    '.ohho...',
    '.ohho...',
    'ooaaoo..',
    'ohhhhooo',
    'ohsshhhh',
    'ohhhhhgg',
    'ohsshhga',
    'ohhhhhhh',
    'oosshhhh',
    '.ooooooo',
]

ECM_POD = [
    '........',
    '......og',
    '.......o',
    '.oo....o',
    '.oaoo..o',
    '..oaaooo',
    '...ohhhh',
    '....oooo',
    '...ohhhh',
    '..ohhhhh',
    '..ohgggg',
    '..ohhhhh',
    '..oshhhh',
    '...ossss',
    '....oooo',
]

MEMENTO = [
    '..oooooooooooo..',
    '.ohhhhhhhhhhbbo.',
    '.ohkkkkkkkkkkso.',
    '.ohkkkPkkPkkkso.',
    '.ohpPPkkkkPPpso.',
    '.ohpPPPkkPPPpso.',
    '.ohppPPwwPPppso.',
    '.ohkppPwwPppkso.',
    '.ohkkppwwppkkso.',
    '.ohkpPpwwpPpkso.',
    '.ohkkppkkppkkso.',
    '.ohkkkkkkkkkkso.',
    '.ohggggggggggso.',
    '.osssssssssssso.',
    '..oooooooooooo..',
]


def tachyon():
    """光る輪のエンジン（中心は暗い軸、輪はシアン、外へ残像の筋）"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 7.3:
                if d > 6.4:
                    img.putpixel((x, y), rgb(0x0E1220))
                elif d > 4.6:
                    # 輪: 内ほど白く
                    k = 1 - abs(d - 5.5) / 0.9
                    c0 = (0x3B, 0xE8, 0xFF)
                    c = tuple(int(c0[i] + (255 - c0[i]) * max(0.0, k) * 0.7) for i in range(3))
                    img.putpixel((x, y), c + (255,))
                elif d > 3.9:
                    img.putpixel((x, y), rgb(0x0E1220))
                elif d > 2.0:
                    shade = 0x5E6A86 if (x + y) < 15 else 0x3B455C
                    img.putpixel((x, y), rgb(shade))
                elif d > 1.2:
                    img.putpixel((x, y), rgb(0x1A7FB0))
                else:
                    img.putpixel((x, y), rgb(0xF0FEFF))
    # 輪の中の切れ目（回っている感じ）
    for (x, y) in [(2, 7), (13, 8), (7, 2), (8, 13)]:
        img.putpixel((x, y), rgb(0x1A9CC6))
    return img


# ------------------------------------------------------------------ ロジックチップ

CHIP_GLYPHS = {
    'precision': ({'r': 0xFF3B4E, 'R': 0xFFB0B8}, [
        '...rr...',
        '.rrRRrr.',
        '.r....r.',
        'rR.RR.Rr',
        'rR.RR.Rr',
        '.r....r.',
        '.rrRRrr.',
        '...rr...',
    ]),
    'intercept': ({'r': 0x2F6BFF, 'R': 0x7FB0FF, 'w': 0xE0F0FF}, [
        '.rrrrrr.',
        'rRRRRRRr',
        'rRRwwRRr',
        'rRRwwRRr',
        '.rRRRRr.',
        '.rRRRRr.',
        '..rRRr..',
        '...rr...',
    ]),
    'absolute': ({'r': 0xFFC23A, 'k': 0x141A2C}, [
        '...rr...',
        '..rrrr..',
        '..rkkr..',
        '.rrkkrr.',
        '.rrrrrr.',
        'rrrkkrrr',
        'rrrrrrrr',
        '........',
    ]),
    'resource_saver': ({'r': 0x3BE070, 'R': 0x9CFFB8}, [
        '...rr...',
        '.rrrrrr.',
        '.r....r.',
        '.r....r.',
        '.rRRRRr.',
        '.rRRRRr.',
        '.rRRRRr.',
        '.rrrrrr.',
    ]),
    # 2026-10-06 追加: 役割（前衛・後衛・救護）、攻撃の選び方、固定砲台
    # ヴァンガード: 盾＋前への山形
    'vanguard': ({'r': 0x2C7CFF, 'R': 0x8CC4FF, 'w': 0xD8ECFF}, [
        'rrrr....',
        'rRRr.w..',
        'rRRr..w.',
        'rRRr...w',
        'rRRr...w',
        '.rRr..w.',
        '..rr.w..',
        '........',
    ]),
    # リアガード: 照準の前に小さな人影
    'rearguard': ({'r': 0x58D838, 'R': 0xB8FF9C, 'w': 0xFFFFFF}, [
        '...rrr..',
        '..r.R.r.',
        '.r..R..r',
        '.rRR.RRr',
        '.w..R..r',
        'www.R.r.',
        '.w.rrr..',
        'w.w.....',
    ]),
    # メディック: 白い十字（赤の縁）＋弾薬の粒
    'medic': ({'r': 0xFF3B4E, 'w': 0xFFFFFF, 'a': 0xFFE08A, 'A': 0xC89A3A}, [
        '..rrr...',
        '..rwr...',
        'rrrwrrr.',
        'rwwwwwr.',
        'rrrwrrr.',
        '..rwr..a',
        '..rrr..A',
        '.......A',
    ]),
    # アタックセレクト: 左に一太刀、右にチャージの渦（間に仕切り）
    'attack_mode': ({'r': 0xFF8A1E, 'R': 0xFFD08A, 'd': 0x5A6478}, [
        '...d....',
        'R..drrrr',
        'R..d...r',
        '.R.drR.r',
        '.R.dr..r',
        '..Rdrrrr',
        '..Rd....',
        '...d....',
    ]),
    # タレット: 据え付けの砲台と照準
    'turret': ({'r': 0x9A4CFF, 'R': 0xD2A6FF, 'w': 0xFFFFFF}, [
        '.....R.R',
        '......w.',
        '.....R.R',
        '....r...',
        '..rrr...',
        '.rRRRr..',
        'rrrrrrr.',
        '.r...r..',
    ]),
}


def chip(kind):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    pin, pin_s = rgb(0xC8CCD6), rgb(0x7C8292)
    # 足（四辺）
    for i in (4, 6, 9, 11):
        img.putpixel((i, 1), pin_s); img.putpixel((i, 2), pin)
        img.putpixel((i, 13), pin); img.putpixel((i, 14), pin_s)
        img.putpixel((1, i), pin_s); img.putpixel((2, i), pin)
        img.putpixel((13, i), pin); img.putpixel((14, i), pin_s)
    # 本体
    for y in range(3, 13):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 12)
            if edge:
                c = 0x0B0F1A
            elif x == 4 or y == 4:
                c = 0x34405C
            elif x == 11 or y == 11:
                c = 0x151B2B
            else:
                c = 0x1E263C
            img.putpixel((x, y), rgb(c))
    # 角の表示灯（シアン）
    img.putpixel((4, 4), rgb(CYAN))
    pal, rows = CHIP_GLYPHS[kind]
    pal = dict(pal)
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c == '.':
                continue
            img.putpixel((4 + x, 4 + y), rgb(pal[c]))
    return img


# ------------------------------------------------------------------ まとめ

def build():
    items = {}
    for t in ('valkyrie', 'fortress', 'mirage'):
        for part, rows in FRAME_SHAPES[t].items():
            items[f'frame_{t}_{part}'] = paint(rows, FRAME_PALS[t])
    for tier in (2, 3, 4):
        for part in ('head', 'chest', 'legs'):
            items[f'frame_valkyrie_mk{tier}_{part}'] = paint(valkyrie_tier_rows(tier, part), PAL_VALKYRIE4 if tier == 4 else PAL_VALKYRIE)
    items['backpack_thruster'] = paint(THRUSTER, PAL_UNIT)
    items['backpack_thruster_mk2'] = paint(THRUSTER_MK2, PAL_UNIT)
    items['backpack_thruster_mk3'] = paint(THRUSTER_MK3, PAL_THRUSTER3)
    items['backpack_thruster_mk4'] = paint(THRUSTER_MK4, PAL_THRUSTER4)
    items['backpack_folding_cannon'] = paint(FOLDING_CANNON, PAL_UNIT)
    items['backpack_ecm_pod'] = paint(ECM_POD, PAL_UNIT)
    items['backpack_tachyon_drive'] = tachyon()
    items['backpack_gallia_fortress'] = paint(GALLIA, PAL_GALLIA)
    items['backpack_memento_familiar'] = paint(MEMENTO, PAL_MEMENTO)
    for k in CHIP_GLYPHS:
        items[f'chip_{k}'] = chip(k)
    return items


def main():
    items = build()
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    for name, img in items.items():
        img.save(os.path.join(TEX_DIR, name + '.png'))
        with open(os.path.join(MODEL_DIR, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'parent': 'minecraft:item/generated',
                       'textures': {'layer0': f'automata_valkyries:item/{name}'}}, f, indent=2)
            f.write('\n')
    print(f'{len(items)} items written')
    if len(sys.argv) > 1:
        scale, cols = 8, 7
        rows = (len(items) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * (16 * scale + 8), rows * (16 * scale + 8)), (70, 70, 78, 255))
        for i, img in enumerate(items.values()):
            big = img.resize((16 * scale, 16 * scale), Image.NEAREST)
            sheet.alpha_composite(big, ((i % cols) * (16 * scale + 8), (i // cols) * (16 * scale + 8)))
        sheet.save(sys.argv[1])


if __name__ == '__main__':
    main()
