"""ガリアフォートの弾のモデル（2026-10-10 ユーザー指定「副砲とミサイルの弾を自作して搭載」）。

  geo/entity/gallia_shell.geo.json   … 副砲の徹甲弾（尖った弾頭・紫の弾帯・赤く光る曳光の尾）
  geo/entity/gallia_missile.geo.json … ミサイル（八角形の胴・段々の弾頭と赤い探知の目・紫の帯と蒼い光の筋・前の小翼と後ろの大きな安定翼・噴射口と光る炎）
  textures/entity/gallia_shell.png / gallia_missile.png（＋ _glowmask.png）

どちらも機首を +Y に向けて、付け根（0, 0, 0）を真ん中に作る（描画 client/GalliaAmmoRenderer が飛ぶ向きへ +Y を回す）。
材質（色）はガリアフォートと同じ（gen_gallia_fort.py の MAT）。塗り・詰め込みは gen_chrono_gazers.py の write を使う。
"""
import math

import gen_chrono_gazers as g
import gen_gallia_fort  # noqa: F401  ガリアフォートの材質（MAT）を足す


def oct_y(m, bone, y0, y1, r, mat, x=0.0, z=0.0):
    """Y の向きの八角柱（内接円の半径 r、y0〜y1）。長さ 2r・幅 2r tan22.5° の板を Y のまわりに 45 度ずつ 4 枚重ねる"""
    w = r * math.tan(math.radians(22.5))
    for a in (0, 45, 90, 135):
        m.box(bone, (x - r, y0, z - w), (x + r, y1, z + w), mat, rot=(0, a, 0) if a else None, pivot=(x, (y0 + y1) / 2, z))


def fins(m, bone, y0, y1, r_in, r_out, thick, mat, n=4, offset=0.0, sweep=0.0):
    """胴のまわりに等しく n 枚の板の翼（y0〜y1、胴の半径 r_in から r_out まで、厚み thick）。sweep は後ろへ傾ける角度（度）"""
    for k in range(n):
        a = offset + 360.0 * k / n
        m.box(bone, (r_in, y0, -thick / 2), (r_out, y1, thick / 2), mat, rot=(0, a, sweep) if (a or sweep) else None, pivot=(0, (y0 + y1) / 2, 0))


def shell():
    m = g.Model('gallia_shell')
    m.bone('root')
    m.bone('body', 'root', (0, 0, 0))
    oct_y(m, 'body', -3.0, 3.0, 1.5, 'heavy')                   # 弾の胴（暗い鋼）
    oct_y(m, 'body', -1.6, -0.6, 1.7, 'purple')                 # 紫の弾帯
    oct_y(m, 'body', 1.2, 1.6, 1.62, 'gblue')                   # 蒼い光の筋
    oct_y(m, 'body', 3.0, 4.8, 1.2, 'metal')                    # 弾頭（段々に尖る）
    oct_y(m, 'body', 4.8, 6.0, 0.8, 'metal')
    oct_y(m, 'body', 6.0, 6.9, 0.4, 'edge')
    oct_y(m, 'body', -4.0, -3.0, 1.2, 'dark')                   # 尾のすぼまり
    oct_y(m, 'body', -5.6, -4.0, 0.8, 'glow')                   # 赤く光る曳光の尾
    return m


def missile():
    m = g.Model('gallia_missile')
    m.bone('root')
    m.bone('body', 'root', (0, 0, 0))
    oct_y(m, 'body', -8.0, 8.0, 2.2, 'heavy')                   # 胴
    for y in (-5.5, 4.0):                                       # 紫の帯
        oct_y(m, 'body', y, y + 1.0, 2.35, 'purple')
    for k in range(4):                                          # 胴の 4 方の蒼い光の筋
        a = math.radians(45 + 90 * k)
        cx, cz = 2.2 * math.cos(a), 2.2 * math.sin(a)
        m.box('body', (cx - 0.25, -4.0, cz - 0.25), (cx + 0.25, 3.5, cz + 0.25), 'gblue')
    oct_y(m, 'body', 8.0, 10.6, 1.75, 'armor')                  # 弾頭（段々に尖る）
    oct_y(m, 'body', 10.6, 12.6, 1.15, 'metal')
    oct_y(m, 'body', 12.6, 14.0, 0.6, 'metal')
    oct_y(m, 'body', 14.0, 14.7, 0.32, 'glow')                  # 赤く光る探知の目
    fins(m, 'body', 5.0, 7.2, 2.0, 3.3, 0.35, 'armor', offset=45.0)          # 前の小翼（尾の翼と 45 度ずらす）
    fins(m, 'body', -8.6, -3.4, 2.0, 4.8, 0.45, 'heavy', sweep=0.0)          # 後ろの大きな安定翼
    fins(m, 'body', -8.4, -7.6, 4.2, 5.0, 0.6, 'edge')                       # 翼の先の重り
    oct_y(m, 'body', -10.0, -8.0, 1.6, 'dark')                  # 噴射口
    oct_y(m, 'body', -9.6, -8.6, 1.75, 'purple')
    m.bone('flame', 'root', (0, -10, 0))                        # 噴射の炎（描画で伸び縮み）
    oct_y(m, 'flame', -12.5, -10.0, 1.2, 'glow')
    oct_y(m, 'flame', -14.5, -12.5, 0.7, 'gblue')
    return m


if __name__ == '__main__':
    g.TEX = 128
    for model in (shell(), missile()):
        g.write(model)
