"""
侵食基地（2026-10-15）の雑魚 5 種・中ボス 3 体・位相アンカーのコンデンサーの GeckoLib モデルとテクスチャを作る。
作りと色の決まりは tools/gen_chrono_gazers.py と同じ（その Model・write・MAT を使う。暗いガンメタル・紫の差し色・冷たい赤の発光）。

    python tools/gen_chrono_base.py

骨の名前と pivot は Java 側（client/ChronoBaseModel）と一致させること:
  pulse_phalanx   : root > base、root > turret > guns > barrels、turret > dish
  siege_cannon    : root > base、root > mount > tube
  siren_eye       : root > body > eye / siren / fins
  chrono_guardian : root > body > shield / rod、root > leg_0..leg_3（前右・前左・後右・後左）
  mimic_container : root > crate > lid > teeth、root > leg_0..leg_3
  crane_loader    : root > tread_left / tread_right、root > body > cab / forks、body > boom > arm > hook
  outpost_watcher : root > body > head / tower > dish、body > gun_left / gun_right、root > leg_left / leg_right
  foundry_master  : root > base、root > torso > core / arm_0..arm_3
  phase_condenser : root > pylon、root > core、root > ring
  bastion_keeper  : root > body > head > eye、body > halo、body > plate_0..plate_3（2026-10-19 浮遊要塞の中ボス）
  chrono_interceptor : root > body > wing_left / wing_right / engine（2026-10-19 浮遊要塞の迎撃機）
  delay_node      : root > core、root > ring_a、root > ring_b（2026-10-19 浮遊要塞の時間干渉機）
座標は geo の座標（px、16px = 1 ブロック）。正面は -Z、右は -X
"""
import math
import os

import gen_chrono_gazers as g

M = g.Model
g.MAT.update({
    'pglow': (0xB060FF, 0xE0C0FF, 0x7A30C0, 'glow', True),     # 位相アンカーの紫の光
    'yellow': (0xD0A020, 0xF0C840, 0x8A6A10, 'plain', False),  # 荷役機の警告色
    'hazard': (0xD0A020, 0xF0C840, 0x23262E, 'stripe', False),  # 黄色の上の帯（stripe は紫の帯なので、地を黄にして使う）
    'wood': (0x6A5030, 0x8A6A40, 0x4A3820, 'panel', False),     # ミミックの外の木箱
    'teeth': (0xE8E4DC, 0xFFFFFF, 0xBFB8AC, 'plain', False),
    'tread': (0x15171C, 0x2C3038, 0x08090C, 'vent', False),     # 履帯（横の溝）
    'orange': (0xFF8030, 0xFFC080, 0xC04010, 'glow', True),     # 溶接・溶鉱の光
    'white': (0xE0E4EC, 0xF8FAFC, 0x9AA0AE, 'panel', False),    # 浮遊要塞の白い装甲
    'whiteb': (0xE0E4EC, 0xF8FAFC, 0xA8AEBA, 'rivet', False),
    'cglow': (0x7FE4FF, 0xD8F8FF, 0x2A9AC0, 'glow', True),     # 迎撃機の推進器の水色
    'gold': (0xD8B040, 0xFFE08A, 0x8A6A20, 'plain', False),     # 守護機の光輪の金の縁
})


def pulse_phalanx():
    m = M('pulse_phalanx')
    m.bone('root')
    m.bone('base', 'root')
    m.box('base', (-9, 0, -9), (9, 3, 9), 'armor', top='plate')               # 土台の円盤（角）
    m.box('base', (-6, 3, -6), (6, 9, 6), 'plate', sides='vent', front='vent')
    m.box('base', (-7, 8.5, -7), (7, 10, 7), 'purple')
    m.bone('turret', 'root', (0, 10, 0))
    m.box('turret', (-7, 10, -6), (7, 18, 6), 'armor', front='plate', top='plate')
    m.box('turret', (-7.4, 13, -6.4), (7.4, 14, 6.4), 'stripe')
    m.box('turret', (-2, 17.9, -3), (2, 19.2, 1), 'eye', sides='dark', top='dark')  # 照準の赤い目
    m.bone('dish', 'turret', (0, 19, 4))
    m.box('dish', (-0.5, 19, 3.5), (0.5, 23, 4.5), 'metal')
    m.box('dish', (-4, 22, 4.5), (4, 27, 5.2), 'edge', front='plate')          # レーダーの皿
    m.box('dish', (-0.7, 24, 3.6), (0.7, 25.2, 4.6), 'glow')
    m.bone('guns', 'turret', (0, 15, -4))
    m.bone('barrels', 'guns', (0, 15, -4))
    for s in (-1, 1):
        m.box('guns', (s * 8.6, 13, -6), (s * 6.8, 17, 3), 'metal')            # 砲身の箱
        for k in (-1, 1):
            m.box('barrels', (s * 7.7 - 0.6 + k * 0.6, 14.4 + k * 0.6, -16), (s * 7.7 + 0.6 + k * 0.6, 15.6 + k * 0.6, -6), 'dark', front='hole')
        m.box('barrels', (s * 8.8, 13.6, -15.5), (s * 6.6, 16.4, -14.5), 'edge', front='glow')
    return m


def siege_cannon():
    m = M('siege_cannon')
    m.bone('root')
    m.bone('base', 'root')
    m.box('base', (-12, 0, -12), (12, 3, 12), 'armor', top='plate')
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box('base', (sx * 13 - 2, 0, sz * 13 - 2), (sx * 13 + 2, 2, sz * 13 + 2), 'metal')  # 脚の留め具
    m.box('base', (-8, 3, -8), (8, 7, 8), 'plate', sides='stripe')
    m.bone('mount', 'root', (0, 7, 0))
    m.box('mount', (-9, 7, -6), (-5, 18, 6), 'armor', out='plate')               # 砲架の左右の板
    m.box('mount', (5, 7, -6), (9, 18, 6), 'armor', out='plate')
    m.box('mount', (-5, 7, -5), (5, 10, 5), 'metal')
    m.bone('tube', 'mount', (0, 14, 0))
    m.box('tube', (-5, 9, -5), (5, 19, 6), 'plate', top='armor')                  # 尾栓
    m.box('tube', (-4, 10, -24), (4, 18, -5), 'metal', top='edge')                 # 太い砲身
    m.box('tube', (-4.6, 9.4, -25), (4.6, 18.6, -22), 'edge', front='hole')        # 砲口
    m.box('tube', (-4.4, 9.6, -16), (4.4, 18.4, -14), 'purple')
    m.box('tube', (-1.2, 18, -12), (1.2, 19.4, -4), 'glow')
    return m


def siren_eye():
    m = M('siren_eye')
    m.bone('root')
    m.bone('body', 'root', (0, 8, 0))
    m.box('body', (-6, 2, -6), (6, 14, 6), 'armor', front='plate')                # 丸みのある胴（箱を重ねる）
    m.box('body', (-7, 4, -5), (7, 12, 5), 'plate')
    m.box('body', (-5, 1, -5), (5, 15, 5), 'metal')
    m.box('body', (-5, 3, -7), (5, 13, -6), 'dark')
    m.bone('eye', 'body', (0, 8, -7))
    m.box('eye', (-3.5, 4.5, -7.6), (3.5, 11.5, -6.8), 'eye', sides='dark', top='dark', bottom='dark')  # 大きな一つ目
    m.bone('siren', 'body', (0, 15, 0))
    m.box('siren', (-2, 15, -2), (2, 16, 2), 'metal')
    m.box('siren', (-3, 16, -1), (3, 18.5, 1), 'glow', sides='dark')              # 回る警告灯
    m.box('siren', (-1, 16, -3), (1, 18.5, 3), 'pglow', front='dark', back='dark')
    m.bone('fins', 'body', (0, 8, 0))
    m.mbox('fins', (-11, 6, -2), (-7, 10, 3), 'purple')
    m.mbox('fins', (-12, 7, -1), (-10.8, 9, 2), 'glow')
    return m


def chrono_guardian():
    m = M('chrono_guardian')
    m.bone('root')
    m.bone('body', 'root', (0, 22, 0))
    m.box('body', (-11, 16, -10), (11, 30, 10), 'armor', top='plate', back='vent')
    m.box('body', (-12, 22, -11), (12, 24, 11), 'stripe')
    m.box('body', (-4, 30, -5), (4, 35, 3), 'plate')                               # 頭
    m.box('body', (-3, 31.5, -5.6), (3, 33, -5), 'slit', sides='dark')
    m.bone('shield', 'body', (10, 24, -6))                                         # 左腕の大盾（+X が左）
    m.box('shield', (11, 18, -8), (14, 28, -4), 'metal')
    m.box('shield', (2, 8, -16), (20, 34, -13), 'armor', front='plate', back='dark')  # 大きな盾の板
    m.box('shield', (3, 20, -16.6), (19, 22, -15.9), 'purple', front='pglow')
    m.bone('rod', 'body', (-11, 24, 0))                                            # 右腕のスタンロッド
    m.box('rod', (-15, 18, -3), (-11, 28, 3), 'plate')
    m.box('rod', (-14.4, 6, -1), (-12, 18, 1), 'metal')
    m.box('rod', (-14, -6, -0.8), (-12.4, 6, 0.8), 'dark')
    m.box('rod', (-14.6, -8, -1.4), (-11.8, -5, 1.4), 'glow')                      # 電極
    for i, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        name = f'leg_{i}'
        m.bone(name, 'root', (sx * 9, 18, sz * 8))
        m.box(name, (sx * 9 - 2.5, 12, sz * 8 - 2.5), (sx * 9 + 2.5, 19, sz * 8 + 2.5), 'metal')
        m.box(name, (sx * 13 - 2, 2, sz * 11 - 2), (sx * 13 + 2, 13, sz * 11 + 2), 'plate', out='armor')
        m.box(name, (sx * 13 - 3, 0, sz * 11 - 3.5), (sx * 13 + 3, 2, sz * 11 + 3.5), 'dark', top='edge')
    return m


def mimic_container():
    m = M('mimic_container')
    m.bone('root')
    m.bone('crate', 'root', (0, 2, 0))
    m.box('crate', (-9, 2, -9), (9, 14, 9), 'wood')                                 # 木箱の胴
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box('crate', (sx * 9 - 1, 2, sz * 9 - 1), (sx * 9 + 1, 14, sz * 9 + 1), 'metal')  # 角の金具
    m.box('crate', (-9.4, 7, -9.4), (9.4, 8.4, 9.4), 'metal')                       # 鉄の帯
    m.box('crate', (-7, 12, -8.6), (7, 13.6, 8.6), 'teeth', top='dark', sides='teeth')  # 下の歯（隠れている）
    m.bone('lid', 'crate', (0, 14, 9))                                              # 蓋（後ろの縁が蝶番）
    m.box('lid', (-9.4, 14, -9.4), (9.4, 18, 9.4), 'wood', top='plate')
    m.box('lid', (-9.6, 15.4, -9.6), (9.6, 16.6, 9.6), 'metal')
    m.box('lid', (-1.5, 13, -9.8), (1.5, 15.5, -9.2), 'edge')                       # 錠前
    m.bone('teeth', 'lid', (0, 14, 0))
    m.box('teeth', (-7, 13, -8.6), (7, 14, 8.6), 'teeth', bottom='dark')            # 上の歯
    m.box('teeth', (-4, 13.2, -8.7), (-2, 13.8, -8.5), 'eye')
    m.box('teeth', (2, 13.2, -8.7), (4, 13.8, -8.5), 'eye')
    for i, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        name = f'leg_{i}'
        m.bone(name, 'root', (sx * 7, 3, sz * 7))
        m.box(name, (sx * 7 - 1.2, -6, sz * 7 - 1.2), (sx * 7 + 1.2, 3, sz * 7 + 1.2), 'metal')
        m.box(name, (sx * 9 - 1.2, -8, sz * 9 - 1.2), (sx * 9 + 1.2, -5, sz * 9 + 1.2), 'dark', bottom='glow')
    return m


def crane_loader():
    m = M('crane_loader')
    m.reuse = {'tread', 'metal'}
    m.bone('root')
    for name, s in (('tread_right', -1), ('tread_left', 1)):
        m.bone(name, 'root', (s * 15, 6, 0))
        m.box(name, (s * 20, 0, -22), (s * 10, 12, 22), 'tread', top='metal')
        for k in range(-2, 3):
            m.box(name, (s * 20.4, 3, k * 8 - 3), (s * 9.6, 9, k * 8 + 3), 'dark', top='dark')  # 転輪
        m.box(name, (s * 20.6, 9, -20), (s * 9.4, 12.4, 20), 'yellow', out='hazard')
    m.bone('body', 'root', (0, 12, 0))
    m.box('body', (-12, 12, -18), (12, 28, 18), 'yellow', top='plate', back='vent')
    m.box('body', (-12.4, 18, -18.4), (12.4, 20, 18.4), 'hazard')
    m.box('body', (-10, 28, 6), (10, 34, 18), 'armor', back='vent')                     # 機関部
    m.bone('cab', 'body', (6, 28, -8))
    m.box('cab', (0, 28, -16), (12, 42, -2), 'plate', front='dark', top='armor')        # 運転席（無人。中に赤い目）
    m.box('cab', (1.5, 34, -16.6), (10.5, 39, -15.9), 'slit', sides='dark')
    m.bone('forks', 'body', (0, 14, -18))
    m.box('forks', (-11, 12, -21), (11, 30, -18), 'metal', front='dark')                # マスト
    for s in (-1, 1):
        m.box('forks', (s * 8 - 2, 12, -42), (s * 8 + 2, 14, -21), 'edge', top='metal')  # 2 本のフォーク
    m.bone('boom', 'body', (-6, 34, 6))                                                 # クレーン（回る）
    m.box('boom', (-10, 34, 2), (-2, 40, 10), 'yellow', sides='hazard')
    m.bone('arm', 'boom', (-6, 39, 6))
    m.box('arm', (-8, 37, -30), (-4, 41, 8), 'yellow', top='hazard')                    # 前へ伸びる腕
    m.box('arm', (-8.4, 36.6, -31), (-3.6, 41.4, -28), 'metal')
    m.bone('hook', 'arm', (-6, 37, -29))
    m.box('hook', (-6.4, 22, -29.4), (-5.6, 37, -28.6), 'dark')                          # 鎖
    m.box('hook', (-8, 18, -31), (-4, 22, -27), 'metal', bottom='glow')                 # 吊り鉤
    return m


def outpost_watcher():
    m = M('outpost_watcher')
    m.bone('root')
    m.bone('body', 'root', (0, 34, 0))
    m.box('body', (-12, 30, -9), (12, 50, 9), 'armor', top='plate', back='vent')
    m.box('body', (-12.4, 40, -9.4), (12.4, 42, 9.4), 'stripe')
    m.box('body', (-8, 26, -7), (8, 31, 7), 'metal')
    m.bone('head', 'body', (0, 50, -2))
    m.box('head', (-6, 50, -8), (6, 57, 4), 'plate', top='armor')
    m.box('head', (-5, 52, -8.6), (5, 54.2, -7.9), 'slit', sides='dark')
    m.box('head', (-1, 57, -1), (1, 60, 1), 'glow')
    m.bone('tower', 'body', (0, 50, 7))
    m.box('tower', (-2, 48, 6), (2, 76, 10), 'metal', sides='stripe')                  # 背の通信塔
    m.box('tower', (-5, 60, 5), (5, 62, 11), 'edge')
    m.box('tower', (-4, 70, 5.5), (4, 71.5, 10.5), 'edge')
    m.box('tower', (-0.8, 76, 7.2), (0.8, 80, 8.8), 'glow')
    m.bone('dish', 'tower', (0, 66, 8))
    m.box('dish', (-7, 63, 10), (7, 70, 11), 'edge', back='plate')                       # 回るレーダー
    m.box('dish', (-1, 65.5, 9), (1, 67.5, 10), 'glow')
    for name, s in (('gun_right', -1), ('gun_left', 1)):
        m.bone(name, 'body', (s * 15, 44, 0))
        m.box(name, (s * 19, 40, -5), (s * 12, 48, 5), 'armor', top='purple', out='plate')  # 肩
        m.box(name, (s * 18, 28, -4), (s * 13, 40, 4), 'plate')
        for k in (-1, 1):
            m.box(name, (s * 16.5 - 0.9 + k * 1.2, 30, -20), (s * 16.5 + 0.9 + k * 1.2, 32, -4), 'dark', front='hole')  # 連装の銃身
        m.box(name, (s * 18.5, 29, -21), (s * 12.5, 33, -19.5), 'edge', front='glow')
    for name, s in (('leg_right', -1), ('leg_left', 1)):
        m.bone(name, 'root', (s * 7, 28, 0))
        m.box(name, (s * 11, 16, -4), (s * 3, 29, 4), 'armor', out='stripe')
        m.box(name, (s * 10, 4, -3.5), (s * 4, 17, 3.5), 'plate', front='armor')
        m.box(name, (s * 11.5, 0, -9), (s * 2.5, 4, 5), 'metal', top='edge')
    return m


def foundry_master():
    m = M('foundry_master')
    m.bone('root')
    m.bone('base', 'root')
    m.box('base', (-20, 0, -20), (20, 4, 20), 'armor', top='plate')                     # 台座
    m.box('base', (-14, 4, -14), (14, 12, 14), 'plate', sides='vent')
    m.box('base', (-15, 11, -15), (15, 13, 15), 'purple')
    m.bone('torso', 'root', (0, 13, 0))
    m.box('torso', (-13, 13, -12), (13, 40, 12), 'armor', top='plate', back='vent')
    m.box('torso', (-13.4, 26, -12.4), (13.4, 28, 12.4), 'stripe')
    m.box('torso', (-6, 40, -7), (6, 48, 5), 'plate')                                   # 頭（監督の目）
    m.box('torso', (-5, 43, -7.6), (5, 45.2, -6.9), 'slit', sides='dark')
    for sx in (-1, 1):
        m.box('torso', (sx * 10 - 2, 40, 6), (sx * 10 + 2, 52, 10), 'metal', top='orange')  # 背の煙突
    m.bone('core', 'torso', (0, 30, -12))
    m.box('core', (-4, 26, -13.6), (4, 34, -12), 'pglow', sides='dark')                 # 胸の紫の核
    # 4 本の腕: 0 = 右上の溶接機、1 = 左上のプレスの槌、2 = 右下の掴み爪、3 = 左下のドリル
    for i, (sx, y) in enumerate(((-1, 36), (1, 36), (-1, 22), (1, 22))):
        name = f'arm_{i}'
        m.bone(name, 'torso', (sx * 13, y, 0))
        m.box(name, (sx * 13 - 3, y - 3, -3), (sx * 13 + 3, y + 3, 3), 'metal')
        m.box(name, (sx * 20 - 2.5, y - 2.5, -14), (sx * 15 - 2.5 * sx + 2.5, y + 2.5, 2), 'plate', top='armor')  # 前へ伸びる腕
        tx = sx * 18
        if i == 0:
            m.box(name, (tx - 1, y - 1, -22), (tx + 1, y + 1, -14), 'dark')
            m.box(name, (tx - 1.4, y - 1.4, -24), (tx + 1.4, y + 1.4, -22), 'orange')      # 溶接の火口
        elif i == 1:
            m.box(name, (tx - 5, y - 5, -22), (tx + 5, y + 5, -14), 'armor', front='edge')  # プレスの槌
        elif i == 2:
            for k in (-1, 1):
                m.box(name, (tx - 1 + k * 3, y - 1, -22), (tx + 1 + k * 3, y + 1, -14), 'edge')  # 爪
        else:
            for k in range(3):
                r = 3.6 - k * 1.1
                m.box(name, (tx - r, y - r, -16 - k * 3), (tx + r, y + r, -13 - k * 3), 'edge' if k % 2 else 'metal')  # ドリル
    return m


def phase_condenser():
    m = M('phase_condenser')
    m.bone('root')
    m.bone('pylon', 'root')
    m.box('pylon', (-7, 0, -7), (7, 3, 7), 'armor', top='plate')
    m.box('pylon', (-4, 3, -4), (4, 18, 4), 'plate', sides='stripe')
    m.box('pylon', (-5, 36, -5), (5, 40, 5), 'armor')
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box('pylon', (sx * 4 - 1, 18, sz * 4 - 1), (sx * 4 + 1, 36, sz * 4 + 1), 'metal')  # 核を囲む 4 本の柱
    m.box('pylon', (-1, 40, -1), (1, 46, 1), 'pglow')
    m.bone('core', 'root', (0, 27, 0))
    m.box('core', (-3, 22, -3), (3, 32, 3), 'pglow')                                    # 紫に光る核
    m.bone('ring', 'root', (0, 27, 0))
    for k in range(8):
        a = k * math.pi / 4
        x, z = 7 * math.cos(a), 7 * math.sin(a)
        m.box('ring', (x - 1, 26, z - 1), (x + 1, 28, z + 1), 'edge', top='pglow')        # 回る輪
    return m


def bastion_keeper():
    m = M('bastion_keeper')
    m.bone('root')
    m.bone('body', 'root', (0, 34, 0))
    m.box('body', (-10, 22, -9), (10, 46, 9), 'white', top='whiteb', back='vent')          # 胴（浮かぶ白い装甲）
    m.box('body', (-11, 33, -10), (11, 35, 10), 'purple')
    m.box('body', (-7, 14, -7), (7, 22, 7), 'armor', bottom='pglow')                         # 下の推進器
    m.box('body', (-4, 10, -4), (4, 14, 4), 'pglow', sides='dark')
    m.box('body', (-12, 40, -6), (-10, 46, 6), 'whiteb')                                     # 肩の張り出し
    m.box('body', (10, 40, -6), (12, 46, 6), 'whiteb')
    m.bone('head', 'body', (0, 46, 0))
    m.box('head', (-6, 46, -7), (6, 54, 5), 'whiteb', top='white')
    m.bone('eye', 'head', (0, 50, -7))
    m.box('eye', (-3, 48, -7.8), (3, 52, -6.9), 'pglow', sides='dark')                       # 紫の単眼
    m.bone('halo', 'body', (0, 44, 9))                                                       # 背の光輪（前後の軸で回る）
    for k in range(12):
        a = k * math.pi / 6
        x, y = 15 * math.cos(a), 44 + 15 * math.sin(a)
        m.box('halo', (x - 2, y - 2, 9), (x + 2, y + 2, 11), 'gold' if k % 3 else 'pglow')
    for i in range(4):                                                                       # 周りに浮く 4 枚の盾板
        name = f'plate_{i}'
        m.bone(name, 'body', (0, 30, 0))
        m.box(name, (-5, 20, -22), (5, 38, -19), 'white', front='whiteb', back='dark')
        m.box(name, (-1, 24, -22.6), (1, 34, -21.9), 'pglow')
    return m


def chrono_interceptor():
    m = M('chrono_interceptor')
    m.bone('root')
    m.bone('body', 'root', (0, 5, 0))
    m.box('body', (-3, 2, -14), (3, 7, 10), 'armor', top='plate')                            # 胴
    m.box('body', (-2, 2.5, -19), (2, 6, -14), 'metal', front='edge')                        # 機首
    m.box('body', (-1.6, 6.8, -10), (1.6, 8.4, -3), 'slit', sides='dark')                    # 赤い目の窓
    m.box('body', (-0.6, 7, 4), (0.6, 13, 10), 'purple')                                     # 垂直尾翼
    for name, s_ in (('wing_right', -1), ('wing_left', 1)):
        m.bone(name, 'body', (s_ * 3, 4, 0))
        m.box(name, (s_ * 3, 3, -4), (s_ * 20, 5, 8), 'plate', top='armor')                  # 三角翼（段で）
        m.box(name, (s_ * 3, 3.2, -9), (s_ * 10, 4.8, -4), 'plate')
        m.box(name, (s_ * 19, 2.6, 4), (s_ * 21, 5.4, 9), 'edge', front='glow')              # 翼端の灯
        m.box(name, (s_ * 6, 2.2, 0), (s_ * 16, 2.8, 2), 'purple')
    m.bone('engine', 'body', (0, 4.5, 11))
    m.box('engine', (-2.5, 2.5, 10), (2.5, 6.5, 12), 'cglow', sides='dark')                  # 推進器の光
    return m


def delay_node():
    m = M('delay_node')
    m.bone('root')
    m.bone('core', 'root', (0, 13, 0))
    m.box('core', (-5, 1, -5), (5, 4, 5), 'armor', bottom='pglow')                           # 砂時計の下の皿
    m.box('core', (-3, 4, -3), (3, 10, 3), 'pglow', sides='dark')                            # 下の玉
    m.box('core', (-1, 10, -1), (1, 16, 1), 'metal')                                         # くびれ
    m.box('core', (-3, 16, -3), (3, 22, 3), 'pglow', sides='dark')                           # 上の玉
    m.box('core', (-5, 22, -5), (5, 25, 5), 'armor', top='plate')
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box('core', (sx * 4 - 0.7, 4, sz * 4 - 0.7), (sx * 4 + 0.7, 22, sz * 4 + 0.7), 'edge')  # 4 本の柱
    m.box('core', (-1.4, 12, -4.6), (1.4, 14, -3.9), 'eye')
    for name, r, y in (('ring_a', 9, 13), ('ring_b', 11, 13)):
        m.bone(name, 'root', (0, y, 0))
        for k in range(12):
            a = k * math.pi / 6
            x, z = r * math.cos(a), r * math.sin(a)
            m.box(name, (x - 1, y - 0.8, z - 1), (x + 1, y + 0.8, z + 1), 'edge' if k % 3 else 'pglow')
    return m


if __name__ == '__main__':
    os.makedirs(os.path.join(g.ASSETS, 'geo', 'entity'), exist_ok=True)
    os.makedirs(os.path.join(g.ASSETS, 'textures', 'entity'), exist_ok=True)
    for fn in (pulse_phalanx, siege_cannon, siren_eye, chrono_interceptor, delay_node):
        g.write(fn())
    g.TEX = 256  # 大きな機体は 256 の画像に詰める
    for fn in (chrono_guardian, mimic_container, phase_condenser):
        g.write(fn())
    g.TEX = 512  # 中ボスは面が大きいので 512
    for fn in (crane_loader, outpost_watcher, foundry_master, bastion_keeper):
        g.write(fn())
    g.TEX = 128
