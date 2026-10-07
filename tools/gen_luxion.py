"""
AEC 級ボス 最光速『ルクシオン』の GeckoLib モデルとテクスチャを作る（2026-10-08）。

    python tools/gen_luxion.py

出力: geo/entity/luxion.geo.json、textures/entity/luxion.png（＋ _glowmask.png、128x128）。
箱・色の板・面ごとの UV の自動の詰め込みは tools/gen_chrono_gazers.py のものを使う（色を足すだけ）。
アニメーションの json は無い（Java のモデルが骨をコードで動かす）ので、骨の名前と pivot は Java 側と一致させること:
  root > body（腰 0,20,0）> head（首 0,32,0）> tail（兜の後ろ）
                          > right_arm（肩 -5.5,30,0）> blade（拳）> blade_tilt（前・下へ傾ける。動かさない）
                          > left_arm（肩 5.5,30,0）
                          > thrusters（背中の上 0,28,2.5）
       > right_leg（-2.2,20,0）/ left_leg（2.2,20,0）
動かす骨には初期の向きを付けない（傾きは子の骨 blade_tilt・fin_*・calf_fin_* に付ける）。
見た目: 細身の機械の騎士。クロノ・ゲイザーのガンメタルに白金の装甲（白 0xF4F0E4・金の縁 0xE8C060）、
冷たい赤の一つ目のバイザー・関節・刃の縁、兜の後ろから流れる白金の光の尾（発光）、背中とふくらはぎの後退したひれ。
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
    m.box('body', (-3.0, 19.0, -1.8), (3.0, 21.5, 1.8), 'metal', front='vent')                 # 腰
    m.mbox('body', (-3.2, 16.8, -2.2), (-0.3, 21.0, -1.85), 'wplate', front='champ')          # 前の草摺
    m.box('body', (-2.2, 21.4, -1.4), (2.2, 24.05, 1.4), 'dark')                               # 細い胴
    m.box('body', (-3.6, 24.0, -2.2), (3.6, 30.5, 2.2), 'wplate', front='wsmooth', back='metal')   # 胸
    m.box('body', (-3.8, 29.8, -2.4), (3.8, 30.6, 2.4), 'gold')                                # 金の縁
    m.box('body', (-2.6, 24.6, -2.35), (2.6, 25.2, -2.15), 'gold')
    m.box('body', (-0.7, 26.5, -2.5), (0.7, 27.9, -2.15), 'glow')                              # 胸の赤い光
    m.box('body', (-2.6, 30.55, -1.8), (2.6, 31.6, 1.8), 'champ')                              # 襟
    m.box('body', (-0.8, 31.55, -0.8), (0.8, 32.25, 0.8), 'metal')                             # 首

    m.bone('head', 'body', (0, 32, 0))
    m.box('head', (-1.9, 32.0, -2.1), (1.9, 36.2, 2.0), 'wplate', top='gold', back='champ')    # 兜
    m.box('head', (-1.55, 33.6, -2.45), (1.55, 34.4, -2.05), 'slit', sides='dark', top='dark', bottom='dark')   # 赤い一つ目のバイザー
    m.box('head', (-1.2, 32.0, -2.35), (1.2, 33.1, -2.05), 'champ')                            # 口元の覆い
    m.box('head', (-0.3, 35.8, -1.6), (0.3, 37.3, 1.5), 'gold')                                # 後ろへ流れる飾り
    m.mbox('head', (-2.25, 33.2, -0.8), (-1.85, 35.6, 1.8), 'gold')                           # 横のひれ
    m.bone('tail', 'head', (0, 35, 2.0))                                                       # 光の尾（兜の後ろから垂れる）
    for i, (w, y0, y1, z0, z1) in enumerate(((0.9, 33.0, 35.2, 2.0, 2.6), (0.8, 30.5, 33.2, 2.55, 3.1), (0.7, 27.5, 30.7, 3.0, 3.55),
                                             (0.55, 24.5, 27.7, 3.45, 3.95), (0.4, 21.5, 24.7, 3.85, 4.25), (0.25, 19.6, 21.7, 4.15, 4.45))):
        m.box('tail', (-w, y0, z0), (w, y1, z1), 'wglow')

    for name, s in (('right_arm', -1), ('left_arm', 1)):
        m.bone(name, 'body', (s * 5.5, 30, 0))
        m.box(name, (s * 7.2, 28.8, -1.8), (s * 4.2, 31.2, 1.8), 'wplate', top='gold', out='champ')   # 肩当て
        m.box(name, (s * 6.3, 24.5, -0.9), (s * 4.7, 29.0, 0.9), 'metal')                          # 上腕
        m.box(name, (s * 6.5, 23.8, -1.0), (s * 4.5, 24.8, 1.0), 'dark', front='glow', out='glow')  # 肘（赤い光）
        m.box(name, (s * 6.6, 18.5, -1.2), (s * 4.4, 23.85, 1.2), 'wplate', out='champ')            # 前腕
        m.box(name, (s * 6.7, 22.6, -1.3), (s * 4.3, 23.1, 1.3), 'gold')
        m.box(name, (s * 6.2, 17.0, -1.0), (s * 4.8, 18.55, 1.0), 'dark')                          # 拳
    m.bone('blade', 'right_arm', (-5.5, 17.8, 0))                                               # 光の剣（拳から前・下へ）
    t = m.bone('blade_tilt', 'blade', (-5.5, 17.8, 0), (-60, 0, 0))
    m.box(t, (-5.85, 15.2, -0.35), (-5.15, 18.3, 0.35), 'dark')                                 # 柄
    m.box(t, (-6.6, 14.8, -1.0), (-4.4, 15.25, 1.0), 'gold', bottom='glow')                     # 鍔
    m.box(t, (-5.85, -5.2, -0.2), (-5.15, 14.8, 0.2), 'wglow')                                  # 白金の刃（20px）
    m.box(t, (-5.62, -5.0, -0.28), (-5.38, 14.6, -0.19), 'glow')                                # 赤い刃の縁（切る側だけ）
    m.box(t, (-5.7, -6.0, -0.12), (-5.3, -5.15, 0.12), 'wglow')                                 # 切っ先

    m.bone('thrusters', 'body', (0, 28, 2.5))                                                   # 背中の推進器と後退したひれ
    m.box('thrusters', (-2.2, 24.5, 2.15), (2.2, 29.5, 2.9), 'metal', back='vent', top='gold')
    m.mbox('thrusters', (-1.6, 24.0, 2.3), (-0.6, 24.55, 2.8), 'glow')                          # 噴射口
    for name, s in (('fin_right', -1), ('fin_left', 1)):
        f = m.bone(name, 'thrusters', (s * 1.85, 28.5, 2.8), (20, s * 20, 0))
        m.box(f, (s * 2.0, 27.0, 2.8), (s * 1.7, 29.5, 6.2), 'wplate', out='gold')
        m.box(f, (s * 2.05, 27.1, 5.6), (s * 1.65, 27.4, 6.3), 'glow')

    for name, s in (('right_leg', -1), ('left_leg', 1)):
        m.bone(name, 'root', (s * 2.2, 20, 0))
        m.box(name, (s * 3.4, 12.5, -1.3), (s * 1.0, 20.2, 1.3), 'metal')                        # 細い太もも
        m.box(name, (s * 3.6, 13.5, -1.6), (s * 0.8, 19.0, -1.25), 'wplate')                     # 太ももの装甲
        m.box(name, (s * 3.0, 11.6, -1.5), (s * 1.4, 12.8, -0.9), 'glow')                        # 膝（赤い光）
        m.box(name, (s * 3.3, 2.5, -1.3), (s * 1.1, 12.55, 1.3), 'wplate', front='champ')        # 脛当て
        m.box(name, (s * 3.45, 9.6, -1.45), (s * 0.95, 10.1, 1.45), 'gold')
        m.box(name, (s * 3.0, 1.2, -1.0), (s * 1.4, 2.6, 1.0), 'metal')                          # 足首
        m.box(name, (s * 3.4, 0.0, -3.4), (s * 1.0, 1.3, 1.4), 'wplate', top='gold')             # 尖った足
        c = m.bone('calf_fin_' + name.split('_')[0], name, (s * 2.2, 8.0, 1.3), (25, 0, 0))     # ふくらはぎの後退したひれ
        m.box(c, (s * 2.35, 6.0, 1.3), (s * 2.05, 9.5, 4.0), 'wplate', out='gold')
    return m


if __name__ == '__main__':
    g.write(luxion())
