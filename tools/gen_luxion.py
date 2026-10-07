"""
AEC 級ボス 最光速『ルクシオン』の GeckoLib モデルとテクスチャを作る（2026-10-08）。

    python tools/gen_luxion.py

出力: geo/entity/luxion.geo.json、textures/entity/luxion.png（＋ _glowmask.png、128x128）。
箱・色の板・面ごとの UV の自動の詰め込みは tools/gen_chrono_gazers.py のものを使う（色を足すだけ）。
アニメーションの json は無い（Java のモデルが骨をコードで動かす）ので、骨の名前と pivot は Java 側と一致させること:
  root > body（腰 0,20,0）> head（首 0,32,0）> tail（兜の後ろ）/ crest（動かさない）
                          > right_arm（肩 -5.5,30,0）> blade（拳）> blade_tilt（前・下へ傾ける。動かさない）
                          > left_arm（肩 5.5,30,0）
                          > thrusters（背中の上 0,28,2.5）> wing_right（-2.5,29,3）/ wing_left（2.5,29,3）（翼の開閉。傾きは *_tilt に）
                          > cannon（右肩の上の光の槍 -4,33,2。rotX で上下）
       > right_leg（-2.2,20,0）/ left_leg（2.2,20,0）
動かす骨には初期の向きを付けない（傾きは子の骨 blade_tilt・fin_*・calf_fin_* に付ける）。
見た目: 細身の機械の騎士。クロノ・ゲイザーのガンメタルに白金の装甲（白 0xF4F0E4・金の縁 0xE8C060）、
冷たい赤の一つ目のバイザー・関節・刃の縁、兜の後ろから流れる白金の光の尾（発光）、背中とふくらはぎの後退したひれ。
2026-10-08 に強化: 重ねた広い胸・大きな肩当て・太い前腕と脛・足首の噴射口、背中の大きな光の翼（金の桁に白金の光の羽 4 枚ずつ）、右肩の上の長い光の槍。
一般的なメカの考え方だけを使い、特定の機体の形は写さない（DESIGN_VALKYRIE_VISUAL_DB §8）。
"""
import importlib.util
import os

_spec = importlib.util.spec_from_file_location('gen_chrono_gazers', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gen_chrono_gazers.py'))
g = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(g)

g.MAT.update({
    'wplate': (0xF4F0E4, 0xFFFFFF, 0xC8C0A8, 'panel', False),   # 白金の装甲
    'wsmooth': (0xF4F0E4, 0xFFFFFF, 0xC8C0A8, 'plain', False),
    'gold': (0xE8C060, 0xFADC78, 0x966E1E, 'plain', False),
    'champ': (0xD8C890, 0xEEE2B0, 0xA89860, 'rivet', False),    # 金がかった板
    'wglow': (0xFFF4CC, 0xFFFFFF, 0xE8C060, 'glow', True),      # 白金の光（尾・刃）
})


def luxion():
    m = g.Model('luxion')
    m.bone('root')
    m.bone('body', 'root', (0, 20, 0))
    # ---- 胴: 細い腰の上に重ねた広い胸の装甲（赤いコアは残す）
    m.box('body', (-3.0, 19.0, -1.8), (3.0, 21.5, 1.8), 'metal', front='vent')                 # 腰
    m.mbox('body', (-3.4, 16.4, -2.25), (-0.3, 21.0, -1.85), 'wplate', front='champ')         # 前の草摺
    m.mbox('body', (-3.7, 16.9, -1.6), (-3.05, 21.0, 1.6), 'champ', out='gold')               # 横の草摺
    m.box('body', (-2.2, 21.4, -1.4), (2.2, 24.05, 1.4), 'dark')                               # 細い胴
    m.box('body', (-4.2, 24.0, -2.4), (4.2, 30.5, 2.4), 'wplate', front='wsmooth', back='metal')   # 胸
    m.mbox('body', (-4.5, 27.4, -3.0), (-0.65, 30.2, -2.35), 'wplate', front='champ', top='gold')  # 胸の重ねた上の板
    m.mbox('body', (-3.6, 24.4, -2.75), (-1.0, 26.0, -2.35), 'champ')                          # 胸の下の板
    m.box('body', (-4.4, 29.9, -2.6), (4.4, 30.7, 2.6), 'gold')                                # 金の縁
    m.box('body', (-0.85, 25.9, -2.95), (0.85, 27.9, -2.4), 'gold')                            # コアの枠
    m.box('body', (-0.6, 26.2, -3.05), (0.6, 27.6, -2.9), 'glow')                              # 胸の赤い光
    m.box('body', (-2.8, 30.55, -1.9), (2.8, 31.7, 1.9), 'champ', top='gold')                  # 襟
    m.box('body', (-0.8, 31.6, -0.8), (0.8, 32.3, 0.8), 'metal')                               # 首

    # ---- 頭: 一つ目の赤いバイザーと後ろへ流れる金の飾り（自前の形）
    m.bone('head', 'body', (0, 32, 0))
    m.box('head', (-1.9, 32.0, -2.1), (1.9, 36.2, 2.0), 'wplate', top='gold', back='champ')    # 兜
    m.box('head', (-1.55, 33.6, -2.45), (1.55, 34.4, -2.05), 'slit', sides='dark', top='dark', bottom='dark')   # 赤い一つ目のバイザー
    m.box('head', (-1.2, 32.0, -2.35), (1.2, 33.1, -2.05), 'champ')                            # 口元の覆い
    m.mbox('head', (-2.25, 33.2, -0.8), (-1.85, 35.6, 1.8), 'gold')                           # 横のひれ
    cr = m.bone('crest', 'head', (0, 35.9, -0.6), (-28, 0, 0))                                  # 後ろへ流れる金の飾り（長く）
    m.box(cr, (-0.3, 35.9, -1.0), (0.3, 40.6, -0.3), 'gold')
    m.box(cr, (-0.18, 40.6, -0.9), (0.18, 41.4, -0.4), 'wglow')
    m.bone('tail', 'head', (0, 35, 2.0))                                                       # 光の尾（兜の後ろから垂れる）
    for i, (w, y0, y1, z0, z1) in enumerate(((0.9, 33.0, 35.2, 2.0, 2.6), (0.8, 30.5, 33.2, 2.55, 3.1), (0.7, 27.5, 30.7, 3.0, 3.55),
                                             (0.55, 24.5, 27.7, 3.45, 3.95), (0.4, 21.5, 24.7, 3.85, 4.25), (0.25, 19.6, 21.7, 4.15, 4.45))):
        m.box('tail', (-w, y0, z0), (w, y1, z1), 'wglow')

    # ---- 腕: 大きく重ねた肩当て・太い前腕
    for name, s in (('right_arm', -1), ('left_arm', 1)):
        m.bone(name, 'body', (s * 5.5, 30, 0))
        m.box(name, (s * 8.2, 28.4, -2.2), (s * 4.4, 31.6, 2.2), 'wplate', top='gold', out='champ')   # 肩当て
        m.box(name, (s * 8.6, 30.5, -1.8), (s * 5.0, 32.4, 1.8), 'champ', top='gold')              # 上に重ねた板
        m.box(name, (s * 8.85, 27.2, -1.6), (s * 8.15, 30.8, 1.6), 'gold')                         # 外の金の板
        m.box(name, (s * 6.3, 24.5, -0.9), (s * 4.7, 28.45, 0.9), 'metal')                         # 上腕
        m.box(name, (s * 6.5, 23.8, -1.0), (s * 4.5, 24.8, 1.0), 'dark', front='glow', out='glow')  # 肘（赤い光）
        m.box(name, (s * 7.0, 18.3, -1.6), (s * 4.0, 23.85, 1.6), 'wplate', out='champ')            # 太い前腕
        m.box(name, (s * 7.15, 22.4, -1.75), (s * 3.85, 23.0, 1.75), 'gold')                        # 金の輪
        m.box(name, (s * 7.2, 19.2, -0.6), (s * 6.95, 21.8, 0.6), 'glow')                           # 前腕の赤い線
        m.box(name, (s * 6.2, 16.8, -1.0), (s * 4.8, 18.35, 1.0), 'dark')                           # 拳
    m.bone('blade', 'right_arm', (-5.5, 17.8, 0))                                               # 光の剣（拳から前・下へ）
    t = m.bone('blade_tilt', 'blade', (-5.5, 17.8, 0), (-60, 0, 0))
    m.box(t, (-5.85, 15.2, -0.35), (-5.15, 18.3, 0.35), 'dark')                                 # 柄
    m.box(t, (-6.6, 14.8, -1.0), (-4.4, 15.25, 1.0), 'gold', bottom='glow')                     # 鍔
    m.box(t, (-5.85, -5.2, -0.2), (-5.15, 14.8, 0.2), 'wglow')                                  # 白金の刃（20px）
    m.box(t, (-5.62, -5.0, -0.28), (-5.38, 14.6, -0.19), 'glow')                                # 赤い刃の縁（切る側だけ）
    m.box(t, (-5.7, -6.0, -0.12), (-5.3, -5.15, 0.12), 'wglow')                                 # 切っ先

    # ---- 右肩の上の長い光の槍（たたんで背負う。砲口は前）
    m.bone('cannon', 'body', (-4, 33, 2))
    m.box('cannon', (-4.8, 32.2, 1.0), (-3.2, 34.0, 3.4), 'metal', top='gold')                  # 台
    m.box('cannon', (-4.5, 32.6, -9.0), (-3.5, 33.6, 1.05), 'wplate', top='champ')              # 前の砲身
    m.box('cannon', (-4.6, 32.5, 3.35), (-3.4, 33.7, 7.6), 'metal', back='vent')                # 後ろの砲身
    m.box('cannon', (-4.8, 32.3, -10.2), (-3.2, 33.9, -8.95), 'gold', front='eye')              # 砲口（赤い光）
    m.box('cannon', (-4.65, 32.9, -8.5), (-4.55, 33.3, 0.5), 'glow')                            # 光の線
    m.box('cannon', (-4.75, 32.45, -3.2), (-3.25, 33.75, -2.4), 'gold')

    # ---- 背中: 推進器・後退したひれ・光の翼
    m.bone('thrusters', 'body', (0, 28, 2.5))
    m.box('thrusters', (-2.6, 24.2, 2.15), (2.6, 30.0, 3.2), 'metal', back='vent', top='gold')
    m.mbox('thrusters', (-1.7, 23.7, 2.3), (-0.6, 24.25, 2.9), 'glow')                          # 噴射口
    for name, s in (('fin_right', -1), ('fin_left', 1)):
        f = m.bone(name, 'thrusters', (s * 1.85, 28.5, 2.8), (20, s * 20, 0))
        m.box(f, (s * 2.0, 27.0, 3.2), (s * 1.7, 29.5, 6.2), 'wplate', out='gold')
        m.box(f, (s * 2.05, 27.1, 5.6), (s * 1.65, 27.4, 6.3), 'glow')
    for name, s in (('wing_right', -1), ('wing_left', 1)):
        m.bone(name, 'thrusters', (s * 2.5, 29, 3))                                             # 翼の根元（コードで開閉）
        m.box(name, (s * 3.3, 28.0, 2.9), (s * 1.9, 30.2, 4.1), 'metal', top='gold')             # 根元の関節
        wt = m.bone(name + '_tilt', name, (s * 2.5, 29, 3), (0, s * -10, s * -38))              # 外・上・後ろへ
        m.box(wt, (s * 2.6, 28.6, 3.0), (s * 12.5, 29.6, 3.7), 'metal', top='gold', bottom='dark')   # 翼の骨組み
        m.box(wt, (s * 3.0, 29.55, 3.08), (s * 12.2, 30.0, 3.62), 'gold')                         # 金の桁
        m.box(wt, (s * 12.4, 28.4, 2.95), (s * 13.3, 30.2, 3.75), 'gold', out='glow')             # 先の飾り
        for k, (x0, rel, ln) in enumerate(((4.6, -18, 12.0), (7.2, -36, 13.5), (9.8, -56, 13.0), (12.2, -76, 11.0))):
            z = 3.05 + k * 0.17
            fb = m.bone(f'{name}_feather_{k}', wt, (s * x0, 29.0, z + 0.1), (0, 0, s * -rel))    # 光の羽（扇に下へ広がる）
            m.box(fb, (s * (x0 - 0.3), 27.9, z - 0.05), (s * (x0 + 0.9), 30.1, z + 0.25), 'gold')   # 羽の付け根
            m.box(fb, (s * (x0 + 0.9), 28.0, z), (s * (x0 + 0.9 + ln * 0.6), 30.0, z + 0.2), 'wglow')       # 光の羽（幅 2）
            m.box(fb, (s * (x0 + 0.9 + ln * 0.6), 28.3, z + 0.01), (s * (x0 + 0.9 + ln * 0.88), 29.7, z + 0.19), 'wglow')
            m.box(fb, (s * (x0 + 0.9 + ln * 0.88), 28.6, z + 0.02), (s * (x0 + 0.9 + ln), 29.4, z + 0.18), 'wglow')   # 羽の先（細く）

    # ---- 脚: 太い脛当て・膝の装甲・足首の噴射口
    for name, s in (('right_leg', -1), ('left_leg', 1)):
        m.bone(name, 'root', (s * 2.2, 20, 0))
        m.box(name, (s * 3.4, 12.5, -1.3), (s * 1.0, 20.2, 1.3), 'metal')                        # 太もも
        m.box(name, (s * 3.7, 13.6, -1.7), (s * 0.7, 19.0, -1.25), 'wplate', top='gold')         # 太ももの装甲
        m.box(name, (s * 3.75, 14.0, -1.2), (s * 3.35, 18.6, 1.2), 'champ')                      # 太ももの横の板
        m.box(name, (s * 3.6, 11.3, -2.15), (s * 0.8, 13.7, -1.4), 'champ', front='wplate')      # 膝の装甲
        m.box(name, (s * 2.8, 11.8, -2.3), (s * 1.6, 12.6, -2.1), 'glow')                        # 膝の赤い光
        m.box(name, (s * 3.7, 2.4, -1.7), (s * 0.7, 11.35, 1.6), 'wplate', front='champ')        # 太い脛当て
        m.box(name, (s * 3.85, 9.4, -1.85), (s * 0.55, 10.0, 1.75), 'gold')
        m.box(name, (s * 3.2, 1.2, -1.0), (s * 1.2, 2.45, 1.0), 'metal')                         # 足首
        m.box(name, (s * 2.8, 1.5, 1.55), (s * 1.6, 2.7, 2.3), 'dark', back='glow')              # 足首の噴射口
        m.box(name, (s * 3.6, 0.0, -3.6), (s * 0.8, 1.3, 1.6), 'wplate', top='gold')             # 尖った足
        c = m.bone('calf_fin_' + name.split('_')[0], name, (s * 2.2, 8.0, 1.6), (25, 0, 0))     # ふくらはぎの後退したひれ
        m.box(c, (s * 2.35, 6.0, 1.6), (s * 2.05, 9.5, 4.3), 'wplate', out='gold')
    return m


if __name__ == '__main__':
    g.write(luxion())
