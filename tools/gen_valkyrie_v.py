"""
戦術兵姫 Type-V のモデル（GeckoLib geo.json）とテクスチャ（128x128、表情ごと＋発光マスク）を生成する（2026-10-04）。

    python tools/gen_valkyrie_v.py

出力（assets/automata_valkyries/ の下）:
  geo/entity/valkyrie_type_v.geo.json
  textures/entity/valkyrie_type_v.png              … 通常の表情
  textures/entity/valkyrie_type_v_<表情>.png       … smile / shy / angry / sad / surprise / sleepy / shutdown / blink
  textures/entity/valkyrie_type_v*_glowmask.png    … 光る所だけ（GeckoLib の AutoGlowingTexture の決まりの名前）

決め事は docs/DESIGN_VALKYRIE_VISUAL_DB.md §11。骨組みはエレノア（geo/entity/elenor.geo.json）を土台に、
同じ骨の名前（root / body / head / hair_upper / hair_mid / hair_tip / side_lock_* / ribbon / *_arm / *_leg）を使う。
身長 約 27px（約 1.7 ブロック）・頭 5px（髪込みで約 5 頭身）。テクスチャは 1px に 2 ドット（面ごとの UV）、
目は独立した板（eyes の骨）で 1 つ 5x8 ドットの水平なジト目（目尻だけ下がる）。胸のコアは独立した骨（core）で、星空の絵（描画側で星が流れる効果を重ねる）。
UV の置き場所は自動で詰める。形を変えたら実行し直すだけでよい。
"""
import json
import math
import os
import random
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries')
NAME = 'valkyrie_type_v'
TEX = 128
K = 2  # 1px あたりのドット数（目だけは別）

# ------------------------------------------------------------------ 色（§11.1: 白の外装・黒紺のスーツ・シアンの発光・銀白の髪）
PAL = {
    # 髪（銀白。光・地・影・濃い影＋毛先のシアン）
    'h': 0xFBFCFE, 'H': 0xE6EAF2, 'M': 0xCDD3E0, 'N': 0xAAB2C6, 'n': 0x8E97AE, 't': 0xD4F4FB, 'T': 0xA4E7F4,
    # 肌
    'S': 0xFCE6DA, 's': 0xF1CDBC, 'B': 0xF8C4C2,
    # 目（まつ毛・下まつ毛・白目・瞳の 3 段・瞳孔・光）
    'L': 0x222A40, 'l': 0x4A5675, 'W': 0xFFFFFF, 'I': 0x0B5A82, 'J': 0x1A9CC6, 'i': 0x45E8FF, 'p': 0x06304A, 'G': 0xF2FEFF, 'g': 0xA8F6FF,
    # スーツ（黒紺）: 地・影・光の筋
    'D': 0x1F273B, 'd': 0x151B2A, 'P': 0x313D5C, 'q': 0x0E121D,
    # 外装（白）: 地・影・縁の濃い影・継ぎ目
    'A': 0xF4F6FA, 'a': 0xD4DAE5, 'Z': 0x9AA3B6, 'z': 0x6E788E,
    # 発光（シアン）
    'C': 0x3BE8FF, 'c': 0x9BF5FF,
    # コアの星空（深い紺・紫の星雲・青の星雲）
    'V': 0x0A0B24, 'U': 0x34207A, 'Q': 0x14408C, 'X': 0xFF7BF0,
}
GLOW = set('CcigGVUQX')
CLOSED = ('shutdown', 'blink', 'smile')  # 瞳が無い表情


# ------------------------------------------------------------------ 形
class Cube:
    def __init__(self, origin, size, paint, key=None, inflate=0.0, tex=None, only=None):
        self.origin, self.size, self.paint, self.key, self.inflate, self.only = origin, size, paint, key, inflate, only
        self.t = tex or [max(1, math.ceil(s * K - 1e-6)) for s in size]  # x, y, z のドット数


class Bone:
    def __init__(self, name, parent, pivot, cubes=(), rotation=None):
        self.name, self.parent, self.pivot, self.cubes, self.rotation = name, parent, pivot, list(cubes), rotation


def fill(c):
    return lambda x, y, w, h: c


def faces(**kw):
    """面ごとの塗り（north / south / east / west / up / down、side = 東西、all = 残り全部）"""
    base = kw.get('all', fill('M'))
    side = kw.get('side', base)
    return {
        'north': kw.get('north', base), 'south': kw.get('south', base),
        'east': kw.get('east', side), 'west': kw.get('west', side),
        'up': kw.get('up', base), 'down': kw.get('down', base),
    }


# ---- 髪（アニメ塗り: 地の上に毛束の線、上に天使の輪、下と端ほど影）
STRANDS = 'HMHNHhMHNMHHMNHMhH'  # 列ごとの毛束の色（不規則に並べて細かく見せる）
K_HAIR = 3                     # 前髪・横髪の小さな箱は 1px に 3 ドット


def k3(size):
    return [max(1, math.ceil(v * K_HAIR - 1e-6)) for v in size]

def hair(x, y, w, h, tip=False, ring=False, dark_bottom=True):
    if tip:
        cut = (0, 3, 1, 2, 4, 1, 0, 2)[(x * 3) % 8]
        if y >= h - cut:
            return '.'
        if y >= h - cut - 2:
            return 'T' if y >= h - cut - 1 else 't'
    if ring and 1 <= y <= 2 and (x % 4) != 3:
        return 'h'
    tone = STRANDS[(x * 5) % len(STRANDS)]
    if tone == 'h' and y > h // 2:
        tone = 'H'  # 光の筋は上半分だけ
    if x in (0, w - 1) and tone in 'hH':
        tone = 'M'
    if dark_bottom and not tip and y >= h - 2:
        tone = {'h': 'H', 'H': 'M', 'M': 'N', 'N': 'n'}[tone]
    return tone


HAIR = lambda x, y, w, h: hair(x, y, w, h)
HAIR_RING = lambda x, y, w, h: hair(x, y, w, h, ring=True)
HAIR_TIP = lambda x, y, w, h: hair(x, y, w, h, tip=True)
HAIR_FLAT = lambda x, y, w, h: hair(x, y, w, h, dark_bottom=False)


def hair_jagged(x, y, w, h):
    """下端がぎざぎざの髪（横のボリューム・後頭部）"""
    cut = (0, 2, 1, 3, 1, 0, 2)[x % 7]
    if y >= h - cut:
        return '.'
    return hair(x, y, w, h)


def bangs(x, y, w, h):
    """髪の外殻の正面: 前髪（毛束の先がとがる。顔の上半分まで）"""
    lim = (6, 5, 4, 5, 6, 6, 5, 4, 5, 6)[x % 10]
    if y < lim:
        return hair(x, y, w, h, ring=True, dark_bottom=False) if y < lim - 1 else 'N'
    return '.'


def tuft(x, y, w, h):
    """前髪の房: 先が三角にとがる"""
    half = (w - 1) / 2
    if y > (h - 1) * (1 - abs(x - half) / (half + 0.5)) + 0.5:
        return '.'
    if y >= h - 2:
        return 'N'
    return 'h' if (x == int(half) and y < h // 2) else ('M' if x % 2 else 'H')


def shell_side(x, y, w, h):
    """髪の外殻の横: 頬の前を空ける"""
    if x >= w - 3 and y >= 6:
        return '.'
    return hair(x, y, w, h, ring=True)


def lock(x, y, w, h):
    """横髪: 先が細くなってシアンに染まる"""
    if y >= h - 3:
        if x in (0, w - 1) and y >= h - 2:
            return '.'
        return 'T' if y == h - 1 else 't'
    if x == 0:
        return 'N'
    if y in (1, 2) and x % 2 == 1:
        return 'h'
    return 'H' if x % 2 else ('M' if x % 4 == 2 else 'N')


# ---- 顔の地（口は無い）。頬の赤みは表情で
def face_base(blush):
    def p(x, y, w, h):
        if y <= 1:
            return 'H' if (x + y) % 3 else 'M'
        if blush and y == 9 and x in (1, 2, 7, 8):
            return 'B'
        if x in (0, w - 1) and y >= 6:
            return 's'
        return 'S'
    return p


def head_side(x, y, w, h):
    return 'S' if (x >= w - 4 and y >= 4) else hair(x, y, w, h, ring=True)


def head_down(x, y, w, h):
    return 'S' if y < h // 2 else 'H'


# ---- 目（EYE_W x EYE_H ドット。right = 本人の右目 = 正面から見て左。mirror で左目）
# 2026-10-04 ユーザー指定: 光は小さく、瞳の解像度を下げ、丸くしすぎず、ジト目（上まぶたが平ら）にする（四角の中でも可愛く見える）。
# 同日「目尻を下げたまま水平にジト目に保ち目の横幅を減らして」「もう一幅だけ狭めて」: まぶたは水平、目尻の 1 列だけ 1 段下げる。横は 5 ドット
EYE_W, EYE_H = 5, 8
# 2026-10-05 ユーザー指定「戦術兵姫はプレイヤーより少し小さいので、素体の目を比率だけ大きく」: 体の比（プレイヤー 32px / 兵姫 27px）だけ目を大きくする。
# ドット数（解像度）は変えず、目の中心の位置もそのまま
EYE_SCALE = 32.0 / 27.0
EYE_PX_H = 1.75 * EYE_SCALE  # 目の板の高さ（px）
EYE_Y0 = 22.875 - EYE_PX_H / 2  # 目の板の下端（中心 22.875 は前と同じ）
ROW = EYE_PX_H / EYE_H     # 目の 1 ドットの大きさ（px）
EYE_WIDTH = EYE_W * ROW    # 目の板の横幅（px）
# まぶた: (中央の段, 傾き)。傾きは 1 列あたりの段数で、正で鼻側が下がる（怒り）、負で目尻が下がる（悲しみ）
LID = {
    'normal': (3.0, 0.0), 'shy': (3.0, 0.0), 'surprise': (1.0, 0.0), 'sleepy': (4.0, 0.0),
    'angry': (3.0, 0.45), 'sad': (2.8, -0.4),
}


def eye_grid(expr, mirror):
    W_, H_ = EYE_W, EYE_H
    c0 = (W_ - 1) / 2
    g = [['.'] * W_ for _ in range(H_)]
    def put(x, y, c):
        if 0 <= x < W_ and 0 <= y < H_:
            g[y][x] = c
    if expr == 'smile':  # ^ の形の閉じた目
        mid = (W_ - 1) / 2
        for x in range(W_):
            put(x, int(round(3 + abs(x - mid))), 'L')
    elif expr in ('shutdown', 'blink'):  # 水平に閉じた目（目尻だけ下がる）
        for x in range(W_):
            put(x, 5, 'L')
        put(0, 6, 'L')
    else:
        lid, slope = LID[expr]
        droop = expr != 'angry'  # 目尻の 1 列を 1 段下げる（下がり目）
        # 視線と光は画面の向きでそろえる（左目は後で左右反転するので、先に逆へずらす）。2026-10-04 ユーザー指定「瞳のハイライトを左か右どっちかに統一」: 光は正面から見て左
        look = (1 if expr == 'shy' else 0) * (-1 if mirror else 1)
        top_of = lambda x: int(round(lid + slope * (x - c0))) + (1 if (droop and x == 0) else 0)
        ix0, ix1 = max(1, 1 + look), min(W_ - 2, W_ - 2 + look)
        for x in range(W_):
            top = top_of(x)
            put(x, top, 'L')  # 上まつ毛（水平のジト目のまぶた）
            for y in range(top + 1, H_):
                last = y == H_ - 1
                if x in (0, W_ - 1) or not (ix0 <= x <= ix1):
                    if not last:
                        put(x, y, 'W')
                    continue
                if last and x in (ix0, ix1):
                    put(x, y, 'W')  # 下の角を落として四角すぎない形に
                    continue
                depth = y - top
                c = 'I' if depth == 1 else ('J' if depth == 2 else 'i')
                if last:
                    c = 'g'
                if (ix0 + ix1) // 2 <= x <= (ix0 + ix1 + 1) // 2 and depth in (1, 2) and not last:
                    c = 'p'
                put(x, y, c)
        put(0, top_of(0) + 1, 'L')  # 目尻のはね（下へ）
        if expr != 'sleepy':  # 小さな光（1 ドット）
            hx = ix1 if mirror else ix0  # 反転後に正面から見て左の列
            hy = top_of(hx) + 1
            if g[hy][hx] not in '.LW':
                put(hx, hy, 'G')
    if mirror:
        g = [row[::-1] for row in g]
    return lambda x, y, w, h: g[y][x]


# ---- スーツ・外装（立体感の陰影）
def suit_shade(x, y, w, h):
    """円筒に見える陰影: 端は影、中央寄りに光の筋"""
    if x in (0, w - 1):
        return 'd'
    if w >= 6 and x == w // 3:
        return 'P'
    return 'D'


def suit_line(x, y, w, h):
    """陰影＋中央の発光線"""
    if x == w // 2 and 0 < y < h - 1:
        return 'C'
    return suit_shade(x, y, w, h)


def chest_front(x, y, w, h):
    if y == 0:
        return 'q'
    if x in (0, w - 1):
        return 'd'
    if (x == 2 or x == w - 3) and 2 <= y <= h - 3:
        return 'P'  # 胸の切り替えの線（ボディライン）
    return 'D'


def chest_back(x, y, w, h):
    if x == w // 2 or x == w // 2 - 1:
        return 'C' if 1 <= y <= h - 2 else 'd'
    return suit_shade(x, y, w, h)


def waist_front(x, y, w, h):
    if x in (0, 1, w - 2, w - 1):
        return 'd'  # くびれの影
    return 'P' if x == w // 2 and y % 2 == 0 else 'D'


def hips_front(x, y, w, h):
    if x in (0, w - 1):
        return 'd'
    if y >= 2 and (x == 2 or x == w - 3):
        return 'P'  # 脚の付け根の線
    return 'D'


def bust_front(x, y, w, h):
    if y == 0:
        return 'P'
    if y == h - 1:
        return 'q'  # 下の影
    if x in (0, w - 1):
        return 'd'
    return 'D'


def armor(x, y, w, h):
    if y == 0:
        return 'A'
    if y == h - 1:
        return 'Z'
    if x in (0, w - 1):
        return 'a'
    return 'A'


def armor_light(x, y, w, h):
    """外装に小さな発光の窓"""
    if x == w // 2 and y == h // 2:
        return 'C'
    return armor(x, y, w, h)


def armor_line(x, y, w, h):
    if x == w // 2 and 1 <= y <= h - 3:
        return 'C'
    if x == w // 2 and y == h - 2:
        return 'z'
    return armor(x, y, w, h)


def armor_panel(x, y, w, h):
    """継ぎ目のある外装板（細かい線で平たさを消す）"""
    if y == h // 3 and 0 < x < w - 1:
        return 'z'
    if x == w // 2 and y > h // 3:
        return 'a'
    return armor(x, y, w, h)


def vent(x, y, w, h):
    if 0 < y < h - 1 and x % 2 == 1 and 0 < x < w - 1:
        return 'q'
    return armor(x, y, w, h)


def joint(x, y, w, h):
    return 'z' if (x + y) % 2 else 'Z'


def skin_hand(x, y, w, h):
    return 's' if (y == h - 1 or x in (0, w - 1)) else 'S'


def core_stars():
    """胸のコア: ネオンの星空（描画側でエンドポータルのように星が流れる効果を重ねる）。縁は強く光り、中は星雲のにじみと少しの星"""
    stars = {(2, 2): 'G', (5, 3): 'c', (3, 5): 'G', (6, 6): 'c', (4, 1): 'X', (1, 4): 'c'}
    def p(x, y, w, h):
        if x in (0, w - 1) or y in (0, h - 1):
            return 'c' if (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)) else 'C'
        if (x, y) in stars:
            return stars[(x, y)]
        d = math.hypot(x - w / 2 + 0.5, y - h / 2 + 0.5)
        if d < 1.3:
            return 'Q'
        if d < 2.4 and (x + y) % 2 == 0:
            return 'U'
        return 'V'
    return p


def ribbon(x, y, w, h):
    return 'C' if y == 0 or y == h - 1 else ('d' if x in (0, w - 1) else 'D')


def sensor(x, y, w, h):
    if 1 <= y <= h - 2 and 1 <= x <= w - 2:
        return 'C' if (x + y) % 2 == 0 or w <= 3 else 'c'
    return 'A' if y > 0 else 'a'


# ------------------------------------------------------------------ 骨と箱（単位は px、y は上、-z が正面、-x が本人の右）
def build(expr):
    blush = expr in ('smile', 'shy')
    B = [Bone('root', None, [0, 0, 0])]
    body = [
        # 胴: 腰（広い）→ くびれ → 胸（ボディライン）
        Cube([-2.5, 11.9, -1.4], [5.0, 2.5, 2.8], faces(all=suit_shade, north=hips_front, south=hips_front, down=fill('q'))),
        Cube([-1.95, 14.4, -1.15], [3.9, 2.0, 2.3], faces(all=suit_shade, north=waist_front, side=suit_line)),
        Cube([-2.3, 16.4, -1.35], [4.6, 4.2, 2.7], faces(all=suit_shade, north=chest_front, south=chest_back, side=suit_line, up=fill('d'))),
        Cube([-1.8, 16.9, -2.05], [3.6, 1.7, 0.7], faces(all=fill('D'), north=bust_front, up=fill('P'), down=fill('q'))),
        # 首と高い襟（前に発光のタブ）
        Cube([-0.65, 20.6, -0.65], [1.3, 0.9, 1.3], faces(all=fill('S'), down=fill('s'))),
        Cube([-1.0, 20.4, -1.0], [2.0, 0.8, 2.0], faces(all=armor_panel, up=fill('a'), down=fill('Z'))),
        Cube([-0.4, 20.45, -1.2], [0.8, 0.55, 0.2], faces(all=fill('a'), north=fill('C'))),
        # コアの白い枠（上下左右）
        Cube([-1.25, 20.5, -1.9], [2.5, 0.3, 0.55], faces(all=armor, north=fill('A'))),
        Cube([-1.25, 18.4, -2.1], [2.5, 0.3, 0.75], faces(all=armor, north=fill('a'))),
        Cube([-1.25, 18.7, -1.9], [0.35, 1.8, 0.55], faces(all=armor, north=lambda x, y, w, h: 'C' if y == h // 2 else 'A'), key='core_side'),
        Cube([0.9, 18.7, -1.9], [0.35, 1.8, 0.55], faces(all=armor, north=lambda x, y, w, h: 'C' if y == h // 2 else 'A'), key='core_side'),
        # ベルト・バックル・腰の装甲板と小さなカートリッジ
        Cube([-2.6, 13.8, -1.5], [5.2, 0.6, 3.0], faces(all=armor, north=lambda x, y, w, h: 'z' if x % 4 == 3 else 'A', up=fill('a'), down=fill('Z'))),
        Cube([-0.55, 13.7, -1.7], [1.1, 0.8, 0.3], faces(all=fill('a'), north=armor_light)),
        Cube([-2.95, 11.6, -1.1], [0.45, 2.2, 2.2], faces(all=armor_panel, east=armor_line, west=armor_line), key='hip_plate'),
        Cube([2.5, 11.6, -1.1], [0.45, 2.2, 2.2], faces(all=armor_panel, east=armor_line, west=armor_line), key='hip_plate'),
        Cube([-3.2, 12.2, 0.1], [0.3, 0.9, 0.6], faces(all=fill('Z'), side=armor_light), key='cartridge'),
        Cube([2.9, 12.2, 0.1], [0.3, 0.9, 0.6], faces(all=fill('Z'), side=armor_light), key='cartridge'),
        # 背中: 背部ユニットの取り付け板（発光の点 2 つ）と腰の排気口
        Cube([-1.3, 17.6, 1.35], [2.6, 2.2, 0.4], faces(all=armor, south=lambda x, y, w, h: 'C' if (y == h // 2 and x in (1, w - 2)) else armor_panel(x, y, w, h))),
        Cube([-1.2, 14.4, 1.15], [2.4, 1.4, 0.45], faces(all=armor, south=vent)),
    ]
    B.append(Bone('body', 'root', [0, 12.5, 0], body))
    B.append(Bone('core', 'body', [0, 19.6, -1.6], [
        Cube([-0.9, 18.7, -1.8], [1.8, 1.8, 0.45], faces(all=fill('C'), north=core_stars()), tex=[8, 8, 2]),
    ]))
    B.append(Bone('head', 'body', [0, 21.3, 0], [
        Cube([-2.5, 21.3, -2.5], [5, 5, 5], faces(north=face_base(blush), south=HAIR_RING, side=head_side, up=HAIR, down=head_down)),
        Cube([-2.5, 21.3, -2.5], [5, 5, 5], faces(north=bangs, south=HAIR_RING, side=shell_side, up=HAIR_RING, down=fill('.')), key='shell', inflate=0.45),
        # 髪のボリューム: 頭頂・後頭部のふくらみ・横のふくらみ
        Cube([-3.1, 25.6, -2.7], [6.2, 1.4, 5.8], faces(all=HAIR_RING, down=fill('N'), north=lambda x, y, w, h: hair(x, y, w, h, ring=True) if y < h - 1 else 'N')),
        Cube([-3.0, 21.0, 2.2], [6.0, 5.0, 1.4], faces(all=hair_jagged, up=fill('H'), north=fill('N'))),
        Cube([-3.75, 20.0, -2.25], [1.15, 6.0, 4.9], faces(all=hair_jagged, up=fill('H')), key='hair_side'),
        Cube([2.6, 20.0, -2.25], [1.15, 6.0, 4.9], faces(all=hair_jagged, up=fill('H')), key='hair_side'),
        # 前髪の房（とがった毛先）
        # 前髪の根元（額の上で前へ張り出す厚み）と 5 つの房（交互に前後へずらして重ねる）
        Cube([-3.0, 24.9, -3.2], [6.0, 1.5, 0.7], faces(all=HAIR_RING, down=fill('N'), north=lambda x, y, w, h: hair(x, y, w, h, ring=True, dark_bottom=False)), tex=k3([6.0, 1.5, 0.7])),
        Cube([-0.7, 23.5, -3.25], [1.4, 2.3, 0.45], faces(all=fill('M'), north=tuft, south=fill('.'), up=fill('H')), key='tuft_c', tex=k3([1.4, 2.3, 0.45])),
        Cube([-1.95, 23.75, -3.1], [1.4, 2.1, 0.45], faces(all=fill('M'), north=tuft, south=fill('.'), up=fill('H')), key='tuft_m', tex=k3([1.4, 2.1, 0.45])),
        Cube([0.55, 23.75, -3.1], [1.4, 2.1, 0.45], faces(all=fill('M'), north=tuft, south=fill('.'), up=fill('H')), key='tuft_m', tex=k3([1.4, 2.1, 0.45])),
        Cube([-3.1, 23.3, -3.15], [1.4, 2.6, 0.45], faces(all=fill('M'), north=tuft, south=fill('.'), up=fill('H')), key='tuft_s', tex=k3([1.4, 2.6, 0.45])),
        Cube([1.7, 23.3, -3.15], [1.4, 2.6, 0.45], faces(all=fill('M'), north=tuft, south=fill('.'), up=fill('H')), key='tuft_s', tex=k3([1.4, 2.6, 0.45])),
    ]))
    # 目: 顔の下半分に置いた独立の板（1 つ EYE_W x EYE_H ドット）。目の中心は左右 ±1.175px
    EX = {-1: -1.175 - EYE_WIDTH / 2, 1: 1.175 - EYE_WIDTH / 2}  # 目の板の左端
    B.append(Bone('eyes', 'head', [0, 22.9, -2.5], [
        Cube([EX[-1], EYE_Y0, -2.55], [EYE_WIDTH, EYE_PX_H, 0.01], faces(all=fill('.'), north=eye_grid(expr, False)), tex=[EYE_W, EYE_H, 1], only='north'),
        Cube([EX[1], EYE_Y0, -2.55], [EYE_WIDTH, EYE_PX_H, 0.01], faces(all=fill('.'), north=eye_grid(expr, True)), tex=[EYE_W, EYE_H, 1], only='north'),
    ]))
    # 立体のまつ毛（2026-10-04 ユーザー指定「まつ毛を薄く出して立体感を」）: 目の板より少し前に出た細い棒と、目尻のはね。
    # 位置は通常の表情（ジト目のまぶた = 目の 3 段目）。表情ごとの上下・傾きは描画側で骨を動かす（LASH_POSE）
    lash_y = EYE_Y0 + EYE_PX_H - LID['normal'][0] * ROW  # 通常のまぶたの高さ
    for side, sx in (('right', -1), ('left', 1)):
        ex0 = EX[sx]
        outer = ex0 if sx < 0 else ex0 + EYE_WIDTH
        B.append(Bone(f'lash_{side}', 'eyes', [ex0 + EYE_WIDTH / 2, lash_y - 0.1, -2.6], [
            Cube([ex0 - 0.05, lash_y - 0.19, -2.72], [EYE_WIDTH + 0.1, 0.19, 0.17], faces(all=fill('L'), up=fill('l'), down=fill('L')), key='lash_bar'),
        ], rotation=[-12, 0, 0]))
        B.append(Bone(f'lash_flick_{side}', f'lash_{side}', [outer, lash_y - 0.19, -2.62], [
            Cube([outer - 0.14, lash_y - 0.5, -2.7], [0.28, 0.36, 0.12], faces(all=fill('L'), up=fill('l')), key='lash_flick'),
        ], rotation=[0, 0, -1.0 * 30 * sx]))
    for side, sx in (('right', -1), ('left', 1)):
        B.append(Bone(f'ear_{side}', 'head', [3.7 * sx, 23.5, 0], [
            Cube([3.7 * sx - 0.25, 22.6, -0.9], [0.5, 1.8, 1.8], faces(all=fill('a'), side=sensor, north=fill('A'), up=fill('A')), key='sensor'),
            Cube([3.7 * sx - 0.15, 24.2, -0.1], [0.3, 0.5, 2.3], faces(all=fill('a'), south=fill('C'), up=fill('A')), key='fin'),
            Cube([3.7 * sx - 0.2, 22.3, -0.4], [0.4, 0.4, 0.8], faces(all=fill('Z'), side=fill('c')), key='fin_low'),
        ], rotation=[-14, 0, 0]))
        lock_x = -3.65 if sx < 0 else 2.15
        cubes = [
            Cube([lock_x, 17.6, -2.75], [1.5, 7.6, 1.4], faces(all=lock, up=fill('H'), down=fill('T')), key='lock', tex=k3([1.5, 7.6, 1.4])),
            Cube([lock_x + (0.3 if sx < 0 else 0.5), 18.6, -2.95], [0.7, 6.2, 0.4], faces(all=lock, up=fill('H'), down=fill('T')), key='lock_front', tex=k3([0.7, 6.2, 0.4])),
            Cube([lock_x + (-0.35 if sx < 0 else 1.25), 18.2, -2.4], [0.6, 6.4, 0.9], faces(all=lock, up=fill('H'), down=fill('T')), key='lock_back', tex=k3([0.6, 6.4, 0.9])),
        ]
        if sx < 0:  # 右の横髪に小さな髪留め
            cubes.append(Cube([lock_x - 0.05, 23.1, -3.0], [1.6, 0.45, 0.3], faces(all=fill('a'), north=lambda x, y, w, h: 'C' if x == w // 2 else 'A')))
        B.append(Bone(f'side_lock_{side}', 'head', [2.9 * sx, 25.0, -2.0], cubes))
    B.append(Bone('ribbon', 'head', [0, 23.0, 3.7], [
        Cube([-0.5, 22.5, 3.55], [1, 1.0, 0.6], faces(all=fill('D'), north=fill('d'), south=lambda x, y, w, h: 'C' if (x, y) == (w // 2, h // 2) else 'D')),
        Cube([-2.3, 22.2, 3.5], [1.8, 1.6, 0.5], faces(all=ribbon, side=fill('D')), key='ribbon_loop'),
        Cube([0.5, 22.2, 3.5], [1.8, 1.6, 0.5], faces(all=ribbon, side=fill('D')), key='ribbon_loop'),
    ]))
    # 後ろ髪: 広く厚く、外側に毛束を重ねて輪郭をふくらませる
    # 後ろ髪は頭に付ける（頭の向きに追従。2026-10-04 ユーザー指定）
    B.append(Bone('hair_upper', 'head', [0, 24.5, 2.3], [
        Cube([-3.3, 17.5, 1.4], [6.6, 7.0, 1.9], faces(all=HAIR, up=fill('H'))),
        Cube([-3.8, 18.0, 1.6], [1.0, 6.5, 1.5], faces(all=HAIR_FLAT, up=fill('H')), key='strand_u'),
        Cube([2.8, 18.0, 1.6], [1.0, 6.5, 1.5], faces(all=HAIR_FLAT, up=fill('H')), key='strand_u'),
    ]))
    B.append(Bone('hair_mid', 'hair_upper', [0, 17.5, 2.3], [
        Cube([-3.3, 12.5, 1.45], [6.6, 5.0, 1.8], faces(all=HAIR_FLAT, up=fill('H'))),
        Cube([-3.9, 12.0, 1.6], [1.1, 6.0, 1.4], faces(all=hair_jagged, up=fill('H')), key='strand_m'),
        Cube([2.8, 12.0, 1.6], [1.1, 6.0, 1.4], faces(all=hair_jagged, up=fill('H')), key='strand_m'),
    ]))
    B.append(Bone('hair_tip', 'hair_mid', [0, 12.5, 2.3], [
        Cube([-3.2, 7.8, 1.5], [6.4, 4.7, 1.6], faces(all=HAIR_TIP, up=fill('H'), down=fill('T'))),
    ]))
    for side, sx in (('right', -1), ('left', 1)):
        ax = -4.2 if sx < 0 else 2.3   # 腕の外側の x（右）/ 内側の x（左）
        B.append(Bone(f'{side}_arm', 'body', [3.25 * sx, 20.1, 0], [
            Cube([ax - 0.2, 19.4, -1.15], [2.3, 1.3, 2.3], faces(all=armor_panel, up=fill('A'), down=fill('z')), key='shoulder'),
            Cube([ax, 16.2, -0.95], [1.9, 4.0, 1.9], faces(all=suit_shade, side=suit_line, up=fill('D'), down=fill('d')), key='upper_arm'),
            Cube([ax + 0.1, 15.9, -0.85], [1.7, 0.6, 1.7], faces(all=joint), key='elbow'),
            Cube([ax + 0.05, 12.6, -0.9], [1.8, 3.4, 1.8], faces(all=suit_shade, up=fill('D'), down=fill('d')), key='forearm'),
            Cube([ax - 0.05, 12.6, -1.0], [2.0, 0.9, 2.0], faces(all=armor_light, up=fill('a'), down=fill('Z')), key='cuff'),
            Cube([ax + 0.2, 11.0, -0.8], [1.5, 1.6, 1.6], faces(all=skin_hand, up=fill('S'), down=fill('s')), key='hand'),
        ]))
        lx = -2.55 if sx < 0 else 0.05
        B.append(Bone(f'{side}_leg', 'root', [1.3 * sx, 12.5, 0], [
            Cube([lx, 6.4, -1.3], [2.5, 6.1, 2.6], faces(all=suit_shade, side=suit_line, up=fill('d'), down=fill('d')), key='thigh'),
            Cube([lx - 0.1, 9.6, -1.4], [2.7, 0.5, 2.8], faces(all=armor_light, up=fill('a'), down=fill('Z')), key='thigh_band'),
            Cube([lx - 0.05, 0.9, -1.4], [2.6, 5.4, 2.8], faces(all=armor_panel, north=armor_line, up=fill('a'), down=fill('Z')), key='greave'),
            Cube([lx + 0.2, 5.5, -1.75], [2.1, 1.3, 0.45], faces(all=armor, north=armor_panel), key='greave_flare'),
            Cube([lx + 0.45, 6.3, -1.7], [1.6, 1.5, 0.55], faces(all=armor, north=armor_light), key='knee'),
            Cube([lx - 0.1, 0, -2.3], [2.7, 0.9, 3.5], faces(all=armor, north=lambda x, y, w, h: 'z' if y == h - 1 else 'A', up=fill('A'), down=fill('z')), key='foot'),
            Cube([lx + 0.65, 0.25, 1.2], [1.2, 0.6, 0.5], faces(all=fill('Z'), south=lambda x, y, w, h: 'C' if 0 < x < w - 1 else 'q'), key='heel'),
        ]))
    return B


# ------------------------------------------------------------------ UV の詰め込み（同じ key の箱は同じ所を使う）
def block_size(c):
    tx, ty, tz = c.t
    if c.only == 'north':
        return tx, ty
    return 2 * tz + 2 * tx, tz + ty


def layout(bones):
    blocks = {}
    for b in bones:
        for i, c in enumerate(b.cubes):
            c.key = c.key or f'{b.name}#{i}'
            blocks.setdefault(c.key, block_size(c))
    order = sorted(blocks, key=lambda k: (-blocks[k][1], -blocks[k][0]))
    shelves = []  # [y, height, next_x]
    pos = {}
    for k in order:
        w, h = blocks[k]
        for s in shelves:
            if s[1] >= h and s[2] + w <= TEX:
                pos[k] = (s[2], s[0])
                s[2] += w
                break
        else:
            y = shelves[-1][0] + shelves[-1][1] if shelves else 0
            if y + h > TEX:
                raise SystemExit(f'texture full at {k}')
            shelves.append([y, h, w])
            pos[k] = (0, y)
    return pos


def face_rects(c, u, v):
    tx, ty, tz = c.t
    if c.only == 'north':
        return {'north': (u, v, tx, ty)}
    return {
        'up': (u + tz, v, tx, tz), 'down': (u + tz + tx, v, tx, tz),
        'east': (u, v + tz, tz, ty), 'north': (u + tz, v + tz, tx, ty),
        'west': (u + tz + tx, v + tz, tz, ty), 'south': (u + 2 * tz + tx, v + tz, tx, ty),
    }


def paint(bones, pos):
    img = [[None] * TEX for _ in range(TEX)]
    done = set()
    for b in bones:
        for c in b.cubes:
            if c.key in done:
                continue
            done.add(c.key)
            for f, (x0, y0, w, h) in face_rects(c, *pos[c.key]).items():
                fn = c.paint[f]
                for yy in range(h):
                    for xx in range(w):
                        ch = fn(xx, yy, w, h)
                        if ch != '.':
                            img[y0 + yy][x0 + xx] = ch
    return img


def geo(bones, pos):
    out = []
    for b in bones:
        o = {'name': b.name, 'pivot': b.pivot}
        if b.parent:
            o['parent'] = b.parent
        if b.rotation:
            o['rotation'] = b.rotation
        if b.cubes:
            cubes = []
            for c in b.cubes:
                uv = {}
                for f, r in face_rects(c, *pos[c.key]).items():
                    # 全部透明な面は書かない（描かれない）
                    if all(c.paint[f](x, y, r[2], r[3]) == '.' for y in range(r[3]) for x in range(r[2])):
                        continue
                    uv[f] = {'uv': [r[0], r[1]], 'uv_size': [r[2], r[3]]}
                cube = {'origin': [round(v, 3) for v in c.origin], 'size': [round(v, 3) for v in c.size], 'uv': uv}
                if c.inflate:
                    cube['inflate'] = c.inflate
                cubes.append(cube)
            o['cubes'] = cubes
        out.append(o)
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {'identifier': f'geometry.{NAME}', 'texture_width': TEX, 'texture_height': TEX,
                            'visible_bounds_width': 2.5, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.0, 0]},
            'bones': out,
        }],
    }


# ------------------------------------------------------------------ PNG
def write_png(path, img, glow_only=False, glow=GLOW):
    raw = bytearray()
    for row in img:
        raw.append(0)
        for ch in row:
            if ch is None or (glow_only and ch not in glow):
                raw += b'\x00\x00\x00\x00'
            else:
                c = PAL[ch]
                raw += bytes(((c >> 16) & 255, (c >> 8) & 255, c & 255, 255))
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', TEX, TEX, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)


# 表情ごとの立体のまつ毛の動き（描画側で lash_right / lash_left の骨に掛ける）: (上下 px, 鼻側を下げる角度 度, 見せるか)
# 目の 1 段 = ROW px。eye_grid のまぶたの段と合わせる
# 上下はモデルの位置（通常のまぶた）からの差、角度は鼻側を下げる向きを正とした絶対値（通常は目尻が下がる負の角度）
def _pose(lid, slope, show=True):
    return ((LID['normal'][0] - lid) * ROW, math.degrees(math.atan(slope)), show)


LASH_POSE = {e: _pose(*LID[e]) for e in LID}
LASH_POSE.update({'shutdown': _pose(5.0, 0.0), 'blink': _pose(5.0, 0.0), 'smile': _pose(3.0, 0.0, False)})  # ^ の目はテクスチャだけで描く

EXPRESSIONS = ['normal', 'smile', 'shy', 'angry', 'sad', 'surprise', 'sleepy', 'shutdown', 'blink']


def main():
    tex_dir = os.path.join(ASSETS, 'textures', 'entity')
    pos = None
    for expr in EXPRESSIONS:
        bones = build(expr)
        if pos is None:
            pos = layout(bones)
            with open(os.path.join(ASSETS, 'geo', 'entity', f'{NAME}.geo.json'), 'w', encoding='utf-8', newline='\n') as f:
                json.dump(geo(bones, pos), f, indent='\t')
                f.write('\n')
        else:
            layout(bones)  # key を振る
        img = paint(bones, pos)
        stem = NAME if expr == 'normal' else f'{NAME}_{expr}'
        write_png(os.path.join(tex_dir, f'{stem}.png'), img)
        write_png(os.path.join(tex_dir, f'{stem}_glowmask.png'), img, glow_only=True)
    used = max(y + 1 for y in range(TEX) if any(img[y]))
    print(f'wrote {NAME}: {len(EXPRESSIONS)} expressions, texture rows used {used}/{TEX}')


if __name__ == '__main__':
    main()
