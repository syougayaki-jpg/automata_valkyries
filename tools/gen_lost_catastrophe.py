"""
裏ボス 戦術兵姫零号機【マリスライズ・ロストカタストロフィー】の GeckoLib モデルとテクスチャ。

    python tools/gen_lost_catastrophe.py

出力（assets/automata_valkyries/ の下）:
  geo/entity/lost_catastrophe.geo.json
  textures/entity/lost_catastrophe.png            … ドット絵（1 px を 8 テクセル、目・口は 16 テクセル）。面ごとの UV を棚詰め
  textures/entity/lost_catastrophe_glowmask.png   … 光る所だけ（本体と同じ大きさ。GeckoLib は大きさが違うと落ちる）

2026-10-21 ユーザー指定「オススメのやりかたで全部かえてみて」: 体（首から下・翼・スカート）は Tripo（有料プラン）で作った OBJ
  （tools/models/lost_catastrophe_knight.obj と色の絵。同日「胸の違和感もあるのでこっちのモデルにしましょう」で替えた）の面のまま骨に載せる（tools/lc_objmesh.py。mesh/lost_catastrophe.bin とテクスチャの右の 2048 四方）。
  骨の pivot は OBJ の関節に合わせる。箱で作るのは頭・大剣・黒い渦だけ（MESH = False で下の箱の体に戻る）。

2026-10-21 ユーザー指定「ピクセルアートよりで造形だけ細かくして全部作り直しで」（調べは docs/RESEARCH_CUTE_MODELS.md §6・§7）:
  イエススティーブモデル（YSM）のかわいいモデルの作りで全部作り直した。
  - 形: 箱だけ（多角形の網は使わない）。正面の輪郭は左右対の傾けた箱（V のあご・くびれ・腰の張り）、横の輪郭は X に扇に回した幅の広い板（胸・尻）。
    面は少なく角度は大きく（ローポリの多角形に見える）。髪の房・棘・破れ布・羽根・光輪・鎖は形を絵で抜いた薄い板。
  - 絵: ドット絵寄り（陰は 2〜3 段、1 テクセルの縁取り、光る線）。
  - 顔（トキ・エレンの YSM モデルの作り）: 平らな広い正面に、下寄りに離した大きなつり目。白目・虹彩・ハイライト・立体のまつげ（顔より前へ出た
    小さな箱の帯と目尻のはね）・眉・口（3 種）を別の骨にし、まばたき（目をつぶし、まつげを下ろす）・視線（バネで遅れて追い、層ごとに視差）・
    ジト目・表情は描く側（client/LostCatastropheModel）で動かす。小さな V のあご、ぷくっとした頬、頬の赤みの斜線、八重歯。
  - 約 5 頭身（2026-10-21 質問で決定）。面頬はふだん額の上へ上げる（同）。
見た目の割り振りは、ゲームの中の絵（光輪 3 つ・両脇の大剣・右手の大きな黒い渦・右の刃の翼と左の瓦礫の翼）と三面図（髪・冠・面頬・装甲・スカート）による。
一般的な意匠だけを使い、特定の作品の形・絵は写さない（DESIGN_VALKYRIE_VISUAL_DB §8、RESEARCH_CUTE_MODELS §6.1）。
座標は GeckoLib（Bedrock）の geo（px）。正面は -Z、右は -X。足の裏 0、髪の上 約 34。
骨の名前と pivot は Java 側（client/LostCatastropheModel・entity/zero/LostCatastropheEntity）と一致させること:
  root > body（腰 0,19.6,0）> chest（0,22.4,0）> head（首 0,26.8,0）> crown / visor / hair_back / halo_top（0,36.6,0）/ ahoge / mouth_n・mouth_o・mouth_s
                                                     eye_r（白目。つぶす）> iris_r（視線）> hl_r、lash_r（まつげ。下ろす）、brow_r。左も同じ
                         > chest > arm_r（-3,25,0）> forearm_r（-3.1,20.7,0）> hand_r（-3.15,17.1,-0.1）> vortex > vortex_in、左も同じ
                         > chest > wing_r / wing_l、halo_r / halo_l
                         > body > skirt_b（長いスカート全体）/ skirt_f / skirt_r / skirt_l / tails（空）、sword_r / sword_l、chain_r / chain_l
         root > leg_r（-1,18.4,0）> shin_r（-1,11,0）、左も同じ
動かす骨には初期の向きを付けない（傾きは箱ごとの回転で付ける）。
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

import lc_objmesh

# 2026-10-21 ユーザー指定「オススメのやりかたで全部かえてみて」: 体（首から下・翼・スカート）は OBJ の面のまま骨に載せる（tools/lc_objmesh.py）。
# 箱で作るのは両脇の大剣・右手の黒い渦だけ。同日「顔を同じように変換して」で頭（顔・髪・冠・面頬・光輪）も OBJ の面にした（FACE_MESH）。
# MESH = False にすると全部箱の前の形に戻り、FACE_MESH = False にすると頭だけ箱（YSM 風の目・まばたき）に戻る
MESH = True
FACE_MESH = True
KEEP_BOXES = ()                    # 2026-10-21 ユーザー指定「剣も渦もなくして」: 両脇の大剣と右手の黒い渦も外し、箱は使わない（骨だけ残す）

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries')
IDENT = 'lost_catastrophe'
DENS, DENS_HD = 8, 16      # 1 px あたりのテクセル（ドット絵寄り。目・口・頬は細かく）
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
HD = ('eye_w', 'iris', 'hl_c', 'hl_d', 'lash', 'brow', 'mouth_n', 'mouth_o', 'mouth_s', 'blush', 'nose')


def hx(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=np.float32)


# ------------------------------------------------------------------ 形
class Model:
    def __init__(self):
        self.bones = []
        self.by = {}

    def bone(self, name, parent=None, pivot=(0, 0, 0)):
        b = {'name': name, 'parent': parent, 'pivot': list(pivot), 'cubes': []}
        self.bones.append(b)
        self.by[name] = b
        return name

    def box(self, bone, a, c, mat, rot=None, pivot=None, faces=None):
        x0, x1 = sorted((a[0], c[0]))
        y0, y1 = sorted((a[1], c[1]))
        z0, z1 = sorted((a[2], c[2]))
        fm = {f: mat for f in FACES}
        if faces:
            fm.update(faces)
        cube = {'a': (x0, y0, z0), 'c': (x1, y1, z1), 'mat': fm}
        if rot and any(abs(r) > 1e-6 for r in rot):
            cube['rot'] = [round(r, 3) for r in rot]
            cube['pivot'] = list(pivot) if pivot else [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2]
        self.by[bone]['cubes'].append(cube)


def Rx(a):
    a = math.radians(a)
    return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])


def Ry(a):
    a = math.radians(a)
    return np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])


def Rz(a):
    a = math.radians(a)
    return np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])


MIRROR = np.diag([-1.0, 1.0, 1.0])
VORTEX_C = (-3.4, 11.3, -0.6)        # 黒い渦の中心（休みの姿勢の右手の先。骨の pivot）
if MESH:                             # OBJ の手首に合わせて、手からのずれは同じに
    _h = lc_objmesh.pivots()['hand_r']
    VORTEX_C = (round(_h[0] - 0.25, 2), round(_h[1] - 5.8, 2), round(_h[2] - 0.5, 2))
HALO_R, HALO_L = (-7.6, 31.6, 1.8), (7.6, 30.8, 1.8)
EYE_X, EYE_Y, EYE_W, EYE_H = 1.12, 28.85, 1.3, 1.5     # 目（大きな頭の正面の下寄りに、離して置く）
FACE_Z = -2.3                                          # 顔の正面


def euler(R):
    """回転の行列を geo の箱の回転（x, y, z 度）にする。geo の回転は Rz(-z)・Ry(+y)・Rx(-x)（GeckoLib と tools/preview_geo.py と同じ）"""
    b = math.asin(max(-1.0, min(1.0, -R[2][0])))
    if abs(math.cos(b)) > 1e-6:
        a = math.atan2(R[2][1], R[2][2])
        g = math.atan2(R[1][0], R[0][0])
    else:
        a = math.atan2(-R[1][2], R[1][1])
        g = 0.0
    return (-math.degrees(a), math.degrees(b), -math.degrees(g))


def obox(m, bone, center, size, R, mat, faces=None):
    """中心・大きさ・向き（回転の行列）で置く箱"""
    c = np.asarray(center, float)
    s = np.asarray(size, float) / 2
    m.box(bone, tuple(c - s), tuple(c + s), mat, euler(np.asarray(R, float)), tuple(c), faces)


def rod(m, bone, p0, p1, w, t, mat, roll=0.0, faces=None):
    """p0 から p1 への細長い箱（長さの向きは X。roll は長さのまわりの回し）。幅 w（Y）・厚さ t（Z）"""
    p0, p1 = np.asarray(p0, float), np.asarray(p1, float)
    d = p1 - p0
    L = float(np.linalg.norm(d))
    if L < 1e-6:
        return
    u = d / L
    yaw = math.degrees(math.atan2(-u[2], u[0]))
    pitch = math.degrees(math.asin(max(-1.0, min(1.0, u[1]))))
    R = Ry(yaw) @ Rz(pitch) @ Rx(roll)
    obox(m, bone, (p0 + p1) / 2, (L, w, t), R, mat, faces)


def flat(m, bone, p0, p1, w, t, mat, normal_hint=(0, 0, -1)):
    """p0 から p1 への板。広い面が normal_hint の向き（に近い向き）を向く。長さの向きは X（付け根が p0）"""
    p0, p1 = np.asarray(p0, float), np.asarray(p1, float)
    d = p1 - p0
    L = float(np.linalg.norm(d))
    if L < 1e-6:
        return
    ex = d / L
    nh = np.asarray(normal_hint, float)
    ez = nh - ex * np.dot(nh, ex)
    if np.linalg.norm(ez) < 1e-6:
        ez = np.cross(ex, [0, 1, 0])
    ez = ez / np.linalg.norm(ez)
    ey = np.cross(ez, ex)
    R = np.stack([ex, ey, ez], axis=1)
    obox(m, bone, (p0 + p1) / 2, (L, w, t), R, mat + '~', faces={'south': mat})


def slab(m, bone, p0, p1, width, thick, mat, splay=0.0, xc=0.0, back=False):
    """横から見た輪郭の線分 p0→p1（(y, z)、上から下へ）に沿う、幅 width（X）・厚さ thick の板（胸・尻の扇）。
    外を向く面が north、厚さは内へ。back は後ろ（+Z）向きの面（尻）。splay は Z の回し（外への開き）"""
    p0 = np.array([xc, p0[0], p0[1]], float)
    p1 = np.array([xc, p1[0], p1[1]], float)
    L = float(np.linalg.norm(p0 - p1))
    ey = (p0 - p1) / L
    ex = np.array([-1.0 if back else 1.0, 0.0, 0.0])
    ez = np.cross(ex, ey)
    R = Rz(splay) @ np.stack([ex, ey, ez], axis=1)
    obox(m, bone, (p0 + p1) / 2 + R @ np.array([0, 0, thick / 2]), (width, L + 0.06, thick), R, mat)


def chain(m, bone, a, b, sag, n):
    """a から b へ垂れる鎖（輪を描いた細い板を、たわみに沿って並べる。輪の形は絵で抜く）"""
    a, b = np.asarray(a, float), np.asarray(b, float)
    pts = []
    for k in range(n + 1):
        t = k / n
        p = a + (b - a) * t
        p[1] -= sag * 4 * t * (1 - t)
        pts.append(p)
    for k in range(n):
        flat(m, bone, pts[k], pts[k + 1] + (pts[k + 1] - pts[k]) * 0.08, 0.36, 0.03, 'chain_p')


def panel(m, bone, a_deg, r0, ytop, L, w, f, mat, t=0.08, zc=0.0):
    """腰のまわりの角度 a（0 = 正面 -Z、正 = +X の側）に吊るした布の板。外へ f 度広がる。裾の中心と向きを返す"""
    a = math.radians(a_deg)
    top = np.array([r0 * math.sin(a), ytop, -r0 * math.cos(a) + zc])
    R = Ry(-a_deg) @ Rx(f)
    center = top + R @ np.array([0.0, -L / 2, 0.0])
    obox(m, bone, center, (w, L, t), R, mat)
    return top + R @ np.array([0.0, -L, 0.0]), R


def hang(m, bone, top, L, w, R, mat, t=0.05):
    """top から下へ吊るす板（髪の房・破れ布など。向き R、長さの向きは下）"""
    top = np.asarray(top, float)
    obox(m, bone, top + R @ np.array([0.0, -L / 2, 0.0]), (w, L, t), R, mat)


def decal(m, bone, center, w, h, mat, rot=None):
    """顔の前に貼る薄い板（目・口・頬など。正面 -Z を向き、形の外は絵で抜く。他の面は透明）"""
    obox(m, bone, center, (w, h, 0.01), rot if rot is not None else np.eye(3), 'x', faces={'north': mat})


def build():
    """形（2026-10-21 ユーザー指定「ピクセルアートよりで造形だけ細かくして全部作り直しで」。YSM のかわいいモデルの作り）"""
    m = Model()
    m.bone('root')
    m.bone('body', 'root', (0, 19.6, 0))
    m.bone('chest', 'body', (0, 22.4, 0))
    m.bone('head', 'chest', (0, 26.8, 0))
    for n, p in (('crown', (0, 33.2, 0)), ('visor', (0, 28.9, -2.9)), ('hair_back', (0, 32.0, 1.8)), ('halo_top', (0, 36.6, 0)),
                 ('ahoge', (0.3, 34.0, -0.6)), ('mouth_n', (0, 27.35, FACE_Z)), ('mouth_o', (0, 27.35, FACE_Z)), ('mouth_s', (0, 27.35, FACE_Z))):
        m.bone(n, 'head', p)
    m.bone('halo_spin', 'halo_top', (0, 36.6, 0))                                       # 光輪を自分の軸で回す（OBJ の光輪。傾きは halo_top）
    eye_pivot_y = EYE_Y - EYE_H / 2 + 0.35                 # 目をつぶす中心（下寄り。上のまぶたが下りてくるように）
    for sd, sx in (('r', -1), ('l', 1)):
        m.bone('eye_' + sd, 'head', (sx * EYE_X, eye_pivot_y, FACE_Z))
        m.bone('iris_' + sd, 'eye_' + sd, (sx * EYE_X, EYE_Y, FACE_Z))
        m.bone('hl_' + sd, 'iris_' + sd, (sx * EYE_X, EYE_Y, FACE_Z))
        m.bone('lash_' + sd, 'head', (sx * EYE_X, EYE_Y + EYE_H / 2, FACE_Z))
        m.bone('brow_' + sd, 'head', (sx * EYE_X, 30.25, FACE_Z))
    m.bone('arm_r', 'chest', (-3.0, 25.0, 0)); m.bone('arm_l', 'chest', (3.0, 25.0, 0))
    m.bone('forearm_r', 'arm_r', (-3.1, 20.7, 0)); m.bone('forearm_l', 'arm_l', (3.1, 20.7, 0))
    m.bone('hand_r', 'forearm_r', (-3.15, 17.1, -0.1)); m.bone('hand_l', 'forearm_l', (3.15, 17.1, -0.1))
    m.bone('vortex', 'hand_r', VORTEX_C); m.bone('vortex_in', 'vortex', VORTEX_C)          # 右手の黒い渦（特異点コア。描く側で回す）
    m.bone('wing_r', 'chest', (-1.2, 23.8, 1.3)); m.bone('wing_l', 'chest', (1.2, 23.8, 1.3))
    m.bone('halo_r', 'chest', HALO_R); m.bone('halo_l', 'chest', HALO_L)                    # 右肩の上の赤い光輪・左肩の上の紫の光輪
    for n, p in (('skirt_f', (0, 19.6, -1.6)), ('skirt_b', (0, 19.6, 0)), ('skirt_r', (-2.0, 19.6, 0)), ('skirt_l', (2.0, 19.6, 0)), ('tails', (0, 19.4, 1.5)),
                 ('sword_r', (-7.0, 15, 0)), ('sword_l', (7.0, 15, 0)), ('chain_r', (-2.3, 19.2, 0)), ('chain_l', (2.3, 19.2, 0))):
        m.bone(n, 'body', p)
    m.bone('leg_r', 'root', (-1.0, 18.4, 0)); m.bone('leg_l', 'root', (1.0, 18.4, 0))
    m.bone('shin_r', 'leg_r', (-1.0, 11.0, 0)); m.bone('shin_l', 'leg_l', (1.0, 11.0, 0))
    rnd = random.Random(7)

    # ---- 頭（大きな箱の平らな正面＋V のあご（左右対の傾けた箱）＋ぷくっとした頬）
    m.box('head', (-2.4, 27.6, -2.3), (2.4, 32.0, 2.3), 'skin', faces={'north': 'face'})
    for sx in (-1, 1):
        obox(m, 'head', (sx * 1.05, 27.4, -0.29), (2.4, 1.4, 4.0), Rz(sx * 18), 'skin', faces={'north': 'face_lo'})
        obox(m, 'head', (sx * 2.42, 28.05, -1.7), (0.55, 1.9, 0.9), Ry(-sx * 22) @ Rz(sx * 8.6), 'skin')
    # 目（白目・虹彩・ハイライト・立体のまつげ・眉）。まつげは顔より前へ出る小さな箱の帯で、目尻で外の上へはねる（つり目）
    for sd, sx in (('r', -1), ('l', 1)):
        cx = sx * EYE_X
        decal(m, 'eye_' + sd, (cx, EYE_Y, FACE_Z - 0.012), EYE_W, EYE_H, 'eye_w~' if sx > 0 else 'eye_w')
        decal(m, 'iris_' + sd, (cx + sx * 0.04, EYE_Y - 0.08, FACE_Z - 0.024), 0.95, 1.22, 'iris')
        decal(m, 'hl_' + sd, (cx + 0.2, EYE_Y + 0.26, FACE_Z - 0.036), 0.42, 0.42, 'hl_c')     # 光は両目とも見る人の左上から
        decal(m, 'hl_' + sd, (cx - 0.24, EYE_Y - 0.42, FACE_Z - 0.036), 0.2, 0.2, 'hl_d')
        pts = []
        for k in range(8):                                                             # 上まつげの弧（目頭 → 目尻。目尻が上がる）
            u = k / 7
            x = cx + sx * (-0.68 + 1.36 * u)
            y = EYE_Y + EYE_H / 2 - 0.36 + 0.13 * math.sin(math.pi * u) + 0.12 * u
            pts.append((x, y, 0.16 + 0.14 * u))
        for k in range(7):
            (x0, y0, t0), (x1, y1, t1) = pts[k], pts[k + 1]
            ang = math.degrees(math.atan2(y1 - y0, x1 - x0))
            L = math.hypot(x1 - x0, y1 - y0) + 0.06
            obox(m, 'lash_' + sd, ((x0 + x1) / 2, (y0 + y1) / 2, FACE_Z - 0.16), (L, (t0 + t1) / 2, 0.3), Rz(ang), 'lash')
        ox, oy, _ = pts[-1]
        for k, (ang, L, t) in enumerate(((38, 0.5, 0.17), (62, 0.36, 0.14))):         # 目尻のはね
            a = math.radians(ang)
            d = np.array([sx * math.cos(a), math.sin(a), 0])
            obox(m, 'lash_' + sd, np.array([ox, oy - 0.04 * k, FACE_Z - 0.2]) + d * L / 2, (L, t, 0.26), Rz(sx * ang if sx > 0 else 180 - ang), 'lash')
        for k in range(2):                                                             # 下まつげ（目尻の側だけ）
            x = cx + sx * (0.32 + 0.24 * k)
            obox(m, 'lash_' + sd, (x, EYE_Y - EYE_H / 2 + 0.08 + 0.05 * k, FACE_Z - 0.06), (0.26, 0.07, 0.1), Rz(sx * (10 + 18 * k)), 'lash')
        obox(m, 'brow_' + sd, (cx - sx * 0.22, 30.22, FACE_Z - 0.08), (0.62, 0.15, 0.14), Rz(-sx * 4), 'brow')           # 眉（細い弧）
        obox(m, 'brow_' + sd, (cx + sx * 0.3, 30.27, FACE_Z - 0.08), (0.55, 0.13, 0.14), Rz(-sx * 14), 'brow')
        decal(m, 'head', (cx + sx * 0.05, 27.95, FACE_Z - 0.008), 0.95, 0.38, 'blush')      # 頬の赤みの斜線
    decal(m, 'head', (0.06, 28.25, FACE_Z - 0.008), 0.14, 0.12, 'nose')
    decal(m, 'mouth_n', (0, 27.35, FACE_Z - 0.03), 0.62, 0.32, 'mouth_n')               # 口: ふだん（小さな「へ」と八重歯）・開いた口（ギザ歯）・笑み
    decal(m, 'mouth_o', (0, 27.3, FACE_Z - 0.03), 0.62, 0.5, 'mouth_o')
    decal(m, 'mouth_s', (0, 27.35, FACE_Z - 0.03), 0.62, 0.32, 'mouth_s')
    m.box('chest', (-0.5, 25.6, -0.45), (0.5, 27.4, 0.55), 'skin')                       # 首
    m.box('chest', (-0.62, 26.0, -0.57), (0.62, 26.45, 0.67), 'armor')                   # 黒い首輪と赤い核
    obox(m, 'chest', (0, 26.2, -0.62), (0.36, 0.36, 0.1), Rz(45), 'core')

    # 面頬（ゴーグル。顔の前に浮かぶ赤い帯と銀の縁。ふだんは描く側で額の上へ上げる）
    m.box('visor', (-1.9, 28.3, -3.1), (1.9, 29.45, -2.98), 'visor')
    for sx in (-1, 1):
        obox(m, 'visor', (sx * 2.5, 28.88, -2.55), (1.4, 1.0, 0.12), Ry(sx * 48), 'visor')
        flat(m, 'visor', (sx * 2.9, 29.2, -1.95), (sx * 3.4, 29.7, -0.95), 0.55, 0.1, 'visor_w', normal_hint=(sx, 0, 0))
    m.box('visor', (-1.95, 29.42, -3.14), (1.95, 29.6, -2.96), 'silver')
    m.box('visor', (-1.95, 28.16, -3.14), (1.95, 28.32, -2.96), 'silver')

    # ---- 髪（外殻の箱を後ろ・横・上に分け、上の縁を傾けた板で面取りして丸く。前髪・横髪（姫カット）・はねる房・後ろの房・アホ毛は板）
    m.box('head', (-2.7, 31.5, -2.62), (2.7, 32.6, 2.7), 'hair')                          # 頭頂
    for sx in (-1, 1):
        m.box('head', (sx * 2.38, 26.7, -1.7), (sx * 2.78, 31.9, 2.7), 'hair')            # 横
    m.box('head', (-2.7, 26.5, 2.05), (2.7, 31.9, 2.78), 'hair')                         # 後ろ
    m.box('head', (-0.7, 33.25, -0.6), (0.7, 33.6, 0.8), 'hair')                          # つむじ
    for k in range(10):                                                                   # 頭頂から四方へ流れる房（外殻の箱の角を隠して丸く。YSM の髪の作り）
        a = math.radians(k * 36 + 18)
        d = np.array([math.sin(a), 0.0, -math.cos(a)])
        top = np.array([0.0, 33.55, 0.1]) + d * 0.3
        droop = 22 + 6 * math.sin(k * 2.1)                                                 # 水平から下へ垂れる角度
        hang(m, 'head', top, 3.3, 1.6, Ry(-k * 36 - 18) @ Rx(90 - droop), 'lock', t=0.2)
    for sx in (-1, 1):                                                                    # 横の外殻に重ねる房（角度と長さをばらばらに）
        for k, (z, yb, ang) in enumerate(((-1.0, 27.2, 4), (0.0, 26.6, -3), (1.0, 26.9, 6), (2.0, 27.4, -5))):
            hang(m, 'head', (sx * 2.9, 32.3, z), 32.3 - yb, 1.2, Ry(-sx * 90) @ Rz(ang) @ Rx(-5), 'lock', t=0.2)
    for k, x in enumerate((-2.25, -1.75, -1.2, -0.62, -0.05, 0.55, 1.12, 1.7, 2.22)):      # 前髪（後ろの列。眉にかかる）
        L = 2.05 + 0.35 * math.sin(k * 2.3) ** 2
        hang(m, 'head', (x, 32.6, -2.72 + 0.06 * x * x), L, 0.82, Ry(x * 9) @ Rz(x * 5 + rnd.uniform(-5, 5)) @ Rx(-8), 'lock', t=0.25)
    for k, x in enumerate((-1.95, -1.4, -0.85, -0.3, 0.3, 0.88, 1.45, 2.0)):              # 前髪（前の列。房の先がばらばら）
        L = 2.3 + 0.5 * math.sin(k * 1.7 + 0.5) ** 2
        hang(m, 'head', (x, 32.75, -2.86 + 0.06 * x * x), L, 0.7, Ry(x * 10) @ Rz(x * 7 + rnd.uniform(-8, 8)) @ Rx(-10), 'lock', t=0.22)
    for sx in (-1, 1):                                                                    # 横の房（姫カット。顔の横を前から縁取り、あごの下まで）
        for k, (x, z, yb, yaw, w) in enumerate(((2.28, -2.0, 25.9, 15, 0.95), (2.62, -1.3, 25.6, 35, 1.0), (2.85, -0.3, 25.9, 70, 0.95))):
            hang(m, 'head', (sx * x, 32.0, z), 32.0 - yb, w, Ry(-sx * yaw) @ Rz(sx * (2 + 3 * k)), 'lock', t=0.24)
        for k, (y, z, ang) in enumerate(((27.0, -0.6, 42), (27.6, 0.6, 52), (26.6, 1.7, 34))):   # 外へはねる毛先
            hang(m, 'head', np.array([sx * 2.8, y + 1.3, z]), 2.0, 0.9, Ry(-sx * (35 + 25 * k)) @ Rz(sx * ang), 'lock', t=0.2)
        for k, (z, ang) in enumerate(((-0.8, 52), (0.8, 40))):                                # 頭の横の上ではねる房
            hang(m, 'head', np.array([sx * 2.3, 32.9, z]), 1.5, 0.8, Ry(-sx * (30 + 30 * k)) @ Rz(sx * (180 - ang)), 'lock', t=0.2)
    for k in range(9):                                                                    # 後ろの房（うなじで外へ跳ねる。揺れる）
        x = -2.4 + k * 0.6
        hang(m, 'hair_back', (x, 32.6, 2.84 + 0.05 * (k % 2)), 6.1 + 0.6 * math.sin(k * 1.9), 0.95, Ry(x * 6) @ Rz(-x * 7 + rnd.uniform(-6, 6)) @ Rx(10 + 4 * (k % 2)), 'lock', t=0.24)
    hang(m, 'ahoge', (0.3, 34.0, -0.6), 1.5, 0.55, Ry(20) @ Rx(160) @ Rz(-12), 'lock', t=0.18)      # アホ毛（2 枚で弧。揺れる）
    hang(m, 'ahoge', np.array([0.3, 34.0, -0.6]) + Ry(20) @ Rx(160) @ Rz(-12) @ np.array([0, -1.35, 0]), 1.1, 0.45, Ry(20) @ Rx(105) @ Rz(-20), 'lock', t=0.16)
    # 冠（頭頂のティアラ。前の帯と、絵で抜いた棘の板。真ん中が高く赤い石）
    for a_ in (-40, 0, 40):
        a = math.radians(a_)
        obox(m, 'crown', (2.25 * math.sin(a), 32.8, -2.25 * math.cos(a) + 0.15), (1.8, 0.32, 0.18), Ry(-a_) @ Rx(-30), 'crown')
    for x, h, w in ((0.0, 2.1, 0.85), (-0.8, 1.3, 0.6), (0.8, 1.3, 0.6), (-1.5, 0.95, 0.5), (1.5, 0.95, 0.5)):
        flat(m, 'crown', (x, 32.85, -2.2 + 0.2 * abs(x)), (x * 1.12, 32.85 + h, -2.28 - 0.15 * h + 0.2 * abs(x)), w, 0.1, 'spike_c')
    obox(m, 'crown', (0, 33.2, -2.36), (0.42, 0.42, 0.12), Rz(45), 'gem')
    obox(m, 'halo_top', (0, 36.6, 0.1), (5.4, 0.02, 5.4), Rx(-16), 'ring_r')             # 頭上の光輪（輪を描いた 1 枚の板）

    # ---- 胴（胸の箱・肩の傾き・くびれ（左右対の傾けた板で V に細る）・腰（左右対で張り出す））
    m.box('chest', (-1.9, 22.4, -1.15), (1.9, 25.4, 1.1), 'bodice', faces={'north': 'chest_f'})
    for sx in (-1, 1):
        obox(m, 'chest', (sx * 1.45, 25.35, -0.02), (1.5, 0.5, 2.15), Rz(sx * 24), 'bodice')            # 肩へ下がる傾き
        obox(m, 'body', (sx * 1.5, 21.55, -0.1), (0.55, 2.6, 2.0), Rz(-sx * 13), 'bodice')               # くびれ（上が外）
        obox(m, 'body', (sx * 1.6, 19.75, 0.0), (0.6, 2.2, 2.35), Rz(sx * 15), 'bodice')                 # 腰の張り（下が外）
    m.box('body', (-1.3, 20.4, -0.95), (1.3, 22.5, 0.9), 'bodice', faces={'north': 'bodice_f'})
    m.box('body', (-1.7, 18.5, -1.2), (1.7, 20.5, 1.25), 'bodice', faces={'north': 'bodice_f'})
    for sx in (-1, 1):                                                                    # 胸（幅の広い板 4 枚を横から見て扇に。外へ少し開く。上は肌、下は装甲の杯）
        prof = [(24.45, -1.12), (23.95, -1.72), (23.2, -1.98), (22.62, -1.75), (22.38, -1.2)]
        mats = ('skin', 'cup_t', 'cup', 'cup_b')
        for k in range(4):
            slab(m, 'chest', prof[k], prof[k + 1], 1.78, 0.42, mats[k], splay=-sx * 6, xc=sx * 0.92)
    for k in range(5):                                                                    # 背骨の装甲（節）
        y = 24.6 - k * 1.25
        bone = 'chest' if y > 22.4 else 'body'
        zb = 1.1 if y > 22.4 else (0.9 if y > 20.4 else 1.25)
        obox(m, bone, (0, y, zb + 0.12), (0.7, 0.95, 0.24), Rx(-8), 'armor_s')
    for sx in (-1, 1):                                                                    # 尻（片方 2 枚の板。スカートの下）
        slab(m, 'body', (19.6, 1.2), (18.7, 1.75), 1.6, 0.4, 'bodice', xc=sx * 0.85, back=True)
        slab(m, 'body', (18.7, 1.75), (18.0, 1.3), 1.6, 0.4, 'bodice', xc=sx * 0.85, back=True)
    m.box('chest', (-1.05, 25.2, 0.2), (1.05, 26.4, 1.05), 'armor_s')                     # 高い襟（後ろ）
    for sx in (-1, 1):
        obox(m, 'chest', (sx * 0.95, 25.8, -0.35), (0.25, 1.1, 1.2), Ry(-sx * 25), 'armor_s')
        flat(m, 'chest', (sx * 0.75, 25.4, -0.75), (sx * 1.25, 26.5, -0.6), 0.75, 0.08, 'spike_a')

    # ---- 肩当て（重ねた板を外の下へ傾けて段にせず扇に。外の上へ反る棘）
    for sx, bone in ((-1, 'arm_r'), (1, 'arm_l')):
        cx = sx * 3.0
        for k, (w, d, ang, dy) in enumerate(((2.5, 2.7, 18, 0.0), (2.2, 2.5, 38, -0.45), (1.9, 2.3, 58, -0.95))):
            obox(m, bone, (cx + sx * (0.2 + 0.42 * k), 25.55 + dy, 0.0), (w, 0.42, d), Rz(-sx * ang), 'armor_s' if k == 0 else 'armor')
        flat(m, bone, (cx + sx * 0.3, 25.8, -0.5), (cx + sx * 1.3, 27.6, -0.6), 0.9, 0.12, 'spike_a')
        flat(m, bone, (cx + sx * 0.6, 25.6, 0.5), (cx + sx * 1.9, 27.0, 0.6), 0.8, 0.12, 'spike_a')

    # ---- 腕（上腕の箱・肘の輪・籠手（手首で外へ開く 4 枚の板）・刃・手と爪）
    for sx, a, f, h in ((-1, 'arm_r', 'forearm_r', 'hand_r'), (1, 'arm_l', 'forearm_l', 'hand_l')):
        cx = sx * 3.0
        m.box(a, (cx - 0.6, 20.9, -0.6), (cx + 0.6, 25.0, 0.6), 'armor', faces={('west' if sx > 0 else 'east'): 'armor_g'})
        m.box(f, (sx * 3.08 - 0.68, 20.3, -0.68), (sx * 3.08 + 0.68, 21.0, 0.68), 'silver')
        m.box(f, (sx * 3.1 - 0.68, 17.5, -0.68), (sx * 3.1 + 0.68, 20.4, 0.68), 'armor_s')
        for k, (nx, nz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
            c = np.array([sx * 3.1 + nx * 0.72, 17.55, nz * 0.72])
            R = (Rz(nx * 16) if nx else Rx(-nz * 16))                                   # 手首の側が外へ開く
            obox(m, f, c, (1.45 if nz else 0.14, 1.0, 0.14 if nz else 1.45), R, 'armor')
        flat(m, f, (sx * 3.85, 18.0, 0), (sx * 4.4, 20.3, 0.1), 0.7, 0.08, 'blade', normal_hint=(0, 0, -1))
        m.box(h, (sx * 3.15 - 0.38, 16.0, -0.55), (sx * 3.15 + 0.38, 17.15, 0.35), 'armor')
        for k in range(4):                                                              # 爪の指（広げる）
            z = -0.42 + k * 0.24
            spread = (k - 1.5) * 0.14
            mid = (sx * (3.22 + 0.08 * abs(k - 1.5)), 15.3, z - 0.2 + spread)
            rod(m, h, (sx * 3.15, 16.1, z - 0.1), mid, 0.15, 0.15, 'claw')
            rod(m, h, mid, (sx * (3.28 + 0.12 * abs(k - 1.5)), 14.5, z - 0.38 + spread * 1.5), 0.12, 0.12, 'claw')
        rod(m, h, (sx * 2.82, 16.4, -0.45), (sx * 2.66, 15.65, -0.8), 0.15, 0.15, 'claw')

    # ---- 脚（腿の箱＋前の板・膝当て＋棘・すね＋ふくらはぎの板・足首・かかとの高い靴）
    for sx, lb, sb in ((-1, 'leg_r', 'shin_r'), (1, 'leg_l', 'shin_l')):
        cx = sx * 1.0
        m.box(lb, (cx - 0.92, 11.6, -0.92), (cx + 0.92, 18.8, 0.92), 'armor')
        obox(m, lb, (cx, 15.0, -0.98), (1.5, 5.6, 0.16), Rx(4), 'armor_s')
        obox(m, lb, (cx + sx * 0.75, 14.6, 0.0), (0.5, 6.4, 1.7), Rz(sx * 3), 'armor')
        obox(m, sb, (cx, 11.1, -0.62), (1.45, 1.35, 1.0), Rx(-10), 'silver')               # 膝当て
        flat(m, sb, (cx, 10.9, -1.05), (cx, 12.6, -1.6), 0.75, 0.1, 'spike_a')
        m.box(sb, (cx - 0.64, 4.2, -0.68), (cx + 0.64, 10.6, 0.68), 'armor', faces={'north': 'armor_g'})
        obox(m, sb, (cx, 8.2, 0.62), (1.15, 3.6, 0.5), Rx(-8), 'armor')                   # ふくらはぎ
        m.box(sb, (cx - 0.56, 2.3, -0.6), (cx + 0.56, 4.3, 0.6), 'armor_s')
        m.box(sb, (cx - 0.62, 2.0, -0.66), (cx + 0.62, 2.45, 0.66), 'silver')             # 足首の輪
        rod(m, sb, (cx, 2.1, -0.05), (cx, 0.2, -1.3), 0.75, 0.55, 'armor')                 # 甲（前へ下がる）
        flat(m, sb, (cx, 0.35, -1.2), (cx, -0.35, -1.95), 0.42, 0.1, 'spike_a', normal_hint=(0, 1, 0))
        rod(m, sb, (cx, 1.5, 0.3), (cx, -0.75, 0.5), 0.22, 0.22, 'silver')                 # ヒール

    # ---- 腰: 前の V 字の黒い板・横の草摺
    for sx in (-1, 1):
        flat(m, 'skirt_f', (sx * 1.55, 20.1, -1.3), (sx * 0.12, 16.7, -1.65), 1.4, 0.12, 'armor_g')
        sk = 'skirt_r' if sx < 0 else 'skirt_l'
        obox(m, sk, (sx * 2.15, 19.1, 0.1), (0.2, 1.6, 2.5), Rz(sx * 32), 'armor_s')
    # ---- 長いスカート（板の輪を 2 段: 腰から外へ大きく広がる短い段と、ほぼまっすぐ下がる長い段。前は開いて脚が見え、後ろは長く引く。
    #      白が主で、裾は暗い灰と紫へ。とがった破れ。内側に暗い布。揺らすのは skirt_b の骨 1 つ）
    N = 18
    for k in range(N):
        a = -180 + (k + 0.5) * 360 / N
        if abs(a) < 36:
            continue
        sa, back = abs(math.sin(math.radians(a))), min(1.0, max(0.0, (abs(a) - 40) / 120))
        r0 = math.hypot(2.0 * math.sin(math.radians(a)), 1.55 * math.cos(math.radians(a)))
        f1 = 36 + 18 * sa
        w1 = 2 * math.pi * (r0 + 1.0) / N * 1.45
        hem1, R1 = panel(m, 'skirt_b', a, r0, 19.75, 3.0, w1, f1, 'cloth_w', zc=0.05)
        r1 = math.hypot(hem1[0], hem1[2] - 0.05)
        yend = 2.4 - 2.6 * back + 0.5 * math.sin(k * 1.7) ** 2
        f2 = 8 + 5 * sa
        L2 = (hem1[1] + 0.3 - yend) / math.cos(math.radians(f2))
        w2 = 2 * math.pi * (r1 + 1.2) / N * 1.42
        hem2, R2 = panel(m, 'skirt_b', a, r1 - 0.06, hem1[1] + 0.3, L2, w2, f2, 'cloth_s', zc=0.05)
        if k % 2 == 0:
            panel(m, 'skirt_b', a + 5, r1 - 0.4, hem1[1] + 0.1, L2 + 1.0, w2 * 0.85, f2 - 2, 'tatter', t=0.06, zc=0.05)
        for jj in range(2):                                                              # 裾から垂れるとがった破れ（黒と紫）
            offx = (jj - 0.5) * w2 * 0.45 + rnd.uniform(-0.15, 0.15)
            Lt = rnd.uniform(1.4, 3.0) * (0.8 + 0.5 * back)
            p0 = hem2 + R2 @ np.array([offx, 0.5, 0.06])
            Rt = R2 @ Rx(rnd.uniform(-6, 10)) @ Rz(rnd.uniform(-12, 12))
            obox(m, 'skirt_b', p0 + Rt @ np.array([0, -Lt / 2, 0]), (w2 * rnd.uniform(0.3, 0.42), Lt, 0.05), Rt, 'tatter')

    # ---- 光輪（右肩の上に赤、左肩の上に紫。輪を描いた 1 枚の板）
    obox(m, 'halo_r', HALO_R, (6.6, 0.02, 6.6), Rz(-30) @ Rx(-45), 'ring_r')
    obox(m, 'halo_l', HALO_L, (6.6, 0.02, 6.6), Rz(30) @ Rx(-45), 'ring_v')
    # ---- 翼（ゲームの中の絵: 右の背は細い刃が外の上へ放つ刃の翼、左の背は灰の装甲の板の継ぎはぎと暗い紫の羽根の瓦礫の翼）
    def frame(sx):
        root = np.array([sx * 1.2, 23.6, 1.45])
        sw = Ry(-sx * 12)

        def W(x, y, z=0.0):
            return root + sw @ np.array([sx * x, y, z])

        def ang(phi):
            return sw @ Rz(phi if sx > 0 else 180 - phi)
        return W, ang, sw

    W, ang, sw = frame(-1)                                                                   # 右: 刃の翼
    rod(m, 'wing_r', W(0.0, 0.0, 0.1), W(3.0, 3.2, 0.1), 1.1, 0.9, 'armor_g')
    obox(m, 'wing_r', W(3.0, 3.2, 0.1), (1.2, 1.2, 1.0), sw @ Rz(45), 'silver')
    obox(m, 'wing_r', W(3.0, 3.2, 0.1), (0.5, 0.5, 1.1), sw @ Rz(45), 'core')
    obox(m, 'wing_r', W(0.5, 0.0, 0.0), (2.4, 2.8, 1.2), sw, 'armor_s')
    for k in range(9):
        phi = 8 + k * 9.5
        L = 9.0 + 6.0 * math.sin(math.pi * (k + 1) / 10) + rnd.uniform(-0.6, 0.6)
        d = np.array([math.cos(math.radians(phi)), math.sin(math.radians(phi))])
        c = np.array([3.0, 3.2]) + d * (L / 2 + 0.2)
        mat = 'wing_b' if k % 3 != 2 else 'wing_p'
        obox(m, 'wing_r', W(c[0], c[1], 0.2 + 0.03 * k), (L, 1.0 if mat == 'wing_b' else 1.7, 0.14), ang(phi), mat + '~', faces={'south': mat})
    for k in range(4):                                                                      # 下へ短い刃
        phi = -18 - k * 14
        L = 5.0 + rnd.uniform(-0.5, 1.0)
        d = np.array([math.cos(math.radians(phi)), math.sin(math.radians(phi))])
        c = np.array([1.2, -0.6]) + d * L / 2
        obox(m, 'wing_r', W(c[0], c[1], 0.3), (L, 0.8, 0.12), ang(phi), 'wing_b~', faces={'south': 'wing_b'})

    W, ang, sw = frame(1)                                                                    # 左: 瓦礫の翼（外の上へ広く）
    arm = [(0.0, 0.0), (5.2, 4.8), (9.6, 9.0), (12.8, 13.0)]
    for k in range(3):
        (x0, y0), (x1, y1) = arm[k], arm[k + 1]
        rod(m, 'wing_l', W(x0, y0, 0.1), W(x1, y1, 0.1), 1.0 - k * 0.15, 0.8 - k * 0.1, 'armor_g')
        obox(m, 'wing_l', W(x1, y1, 0.1), (1.0, 1.0, 1.0), sw @ Rz(45), 'silver')
        obox(m, 'wing_l', W(x1, y1, 0.1), (0.42, 0.42, 1.1), sw @ Rz(45), 'core')
    for row, (n_, z0, sc) in enumerate(((13, 0.25, 1.0), (8, 0.55, 0.7))):
        for k in range(n_):
            t = (k + 0.5 * row) / (n_ - 1 + 0.5 * row)
            seg = min(2, int(t * 3))
            u = t * 3 - seg
            (x0, y0), (x1, y1) = arm[seg], arm[seg + 1]
            px, py = x0 + (x1 - x0) * u, y0 + (y1 - y0) * u
            phi = -10 + 52 * t + rnd.uniform(-5, 5)
            L = (6.5 + 6.0 * t + rnd.uniform(-0.8, 0.8)) * sc
            mat = 'wing_p' if (k + row) % 3 != 1 else 'wing_v'
            pw = (2.8 if mat == 'wing_p' else 1.6) * (1.0 - 0.2 * t)
            d = np.array([math.cos(math.radians(phi)), math.sin(math.radians(phi))])
            c = np.array([px, py]) + d * (L / 2 - 0.4)
            obox(m, 'wing_l', W(c[0], c[1], z0 + (0.15 if mat == 'wing_v' else 0.0) + 0.012 * k), (L, pw, 0.16), ang(phi), mat + '~', faces={'south': mat})
    for k in range(6):                                                                      # 下の紫の羽根
        phi = -25 - k * 9
        L = 5.5 + 3.0 * math.sin(math.pi * (k + 1) / 7)
        d = np.array([math.cos(math.radians(phi)), math.sin(math.radians(phi))])
        c = np.array([1.5, -1.5]) + d * L / 2
        mat = 'wing_v' if k % 2 else 'wing_p'
        obox(m, 'wing_l', W(c[0], c[1], 0.3 + 0.05 * k), (L, 1.4 if mat == 'wing_v' else 2.0, 0.15), ang(phi), mat + '~', faces={'south': mat})
    obox(m, 'wing_l', W(0.5, 0.0, 0.0), (2.4, 2.8, 1.2), sw, 'armor_s')
    for k in range(7):                                                                      # 浮かぶ瓦礫
        sz = rnd.uniform(0.5, 1.3)
        p = W(rnd.uniform(13.0, 18.0), rnd.uniform(4.0, 16.0), rnd.uniform(-0.8, 1.6))
        obox(m, 'wing_l', p, (sz, sz * rnd.uniform(0.5, 1.2), sz * rnd.uniform(0.6, 1.4)), Ry(rnd.uniform(0, 90)) @ Rx(rnd.uniform(0, 90)) @ Rz(rnd.uniform(0, 90)), 'armor_s')
    chain(m, 'wing_l', W(5.2, 4.8, 0.6), W(9.5, 0.0, 0.9), 2.4, 12)                           # 左の翼から垂れる鎖
    chain(m, 'wing_l', W(9.6, 9.0, 0.6), W(14.0, 3.5, 0.9), 1.8, 12)
    # ---- 両脇に浮かぶ大剣（刃を外の下へ斜めに向ける。灰の刃・赤い光の縁・暗い柄と赤い柄頭）
    for sx, bone in ((-1, 'sword_r'), (1, 'sword_l')):
        hilt = np.array([sx * 7.0, 19.0, -0.6])
        tip = np.array([sx * 11.2, 2.0, 0.4])
        d = (tip - hilt) / np.linalg.norm(tip - hilt)
        flat(m, bone, hilt + d * 1.6, tip, 2.4, 0.3, 'sword', normal_hint=(0, 0, -1))
        rod(m, bone, hilt + d * 1.3, hilt + d * 1.8, 3.8, 0.6, 'armor_s')
        rod(m, bone, hilt - d * 1.4, hilt + d * 1.3, 0.48, 0.48, 'armor')
        obox(m, bone, hilt - d * 1.7, (0.7, 0.7, 0.7), Rz(45), 'core')
        chain(m, bone, hilt + d * 1.8, hilt + d * 1.8 + np.array([sx * 1.5, -5.0, 0.6]), 0.4, 7)
    for sx, bone in ((-1, 'chain_r'), (1, 'chain_l')):                                       # 腰から下がる鎖
        chain(m, bone, (sx * 2.25, 19.3, -0.7), (sx * 1.7, 18.9, 1.75), 2.8, 11)
    # ---- 右手の黒い渦（特異点コア。上半身ほどの大きさ。赤と黒の渦の板を重ね、外の板に赤い稲妻、まわりに赤いデータの欠片）
    vc = np.array(VORTEX_C)
    for k, (rr, z, mat, bone) in enumerate(((6.4, 0.0, 'vortex_o', 'vortex'), (5.0, -0.08, 'vortex', 'vortex_in'), (3.3, -0.16, 'vortex', 'vortex'), (1.6, -0.24, 'vortex_c', 'vortex_in'))):
        obox(m, bone, vc + np.array([0, 0, z]), (rr * 2, rr * 2, 0.04), Rz(k * 37), mat)
    for k in range(14):
        a = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(4.5, 7.8)
        s_ = rnd.uniform(0.2, 0.5)
        obox(m, 'vortex', vc + np.array([r * math.cos(a), r * math.sin(a), rnd.uniform(-0.4, 0.3)]), (s_, s_, 0.1), np.eye(3), 'halo_r')
    if MESH:
        piv = lc_objmesh.pivots()
        sub = set()                                                                 # 頭とその子は箱のまま（FACE_MESH なら頭も OBJ）
        for b in m.bones:
            if not FACE_MESH and (b['name'] == 'head' or b['parent'] in sub):
                sub.add(b['name'])
        for b in m.bones:
            if b['name'] in piv:
                b['pivot'] = list(piv[b['name']])
            if b['name'] not in sub and b['name'] not in KEEP_BOXES:
                b['cubes'] = []
        if not FACE_MESH:
            m.box('head', (-0.5, 25.8, -0.45), (0.5, 27.6, 0.55), 'skin')            # 首（OBJ の首の上と頭をつなぐ）
    return m


# ------------------------------------------------------------------ 塗り（ドット絵寄り。陰は 2〜3 段、1 テクセルの縁取り、光る線）
def face_dims(a, c, f):
    sx, sy, sz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
    return {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}[f]


THIN = ('lock', 'spike_a', 'spike_c', 'spike_h', 'visor_w', 'wing_v', 'wing_p', 'wing_b', 'sword', 'blade', 'tatter', 'cloth_s', 'cloth_w', 'cloth_d',
        'ring_r', 'ring_v', 'chain_p')


def paint(mat, f, w, h, dw, dh, seed):
    """1 つの面をテクセルで塗る（w×h テクセル、dw×dh px）。RGBA と発光を返す"""
    if mat.endswith('~'):                                          # 面の向きで左右が逆に貼られる面: 塗ってから左右を返す
        img, gl = paint(mat[:-1], f, w, h, dw, dh, seed)
        return img[:, ::-1].copy(), gl[:, ::-1].copy()
    X, Y = np.meshgrid(np.arange(w), np.arange(h))
    U, V = (X + 0.5) / w, (Y + 0.5) / h
    rs = np.random.RandomState(seed & 0x7FFFFFFF)
    rgb = np.zeros((h, w, 3), np.float32)
    alpha = np.full((h, w), 255.0, np.float32)
    glow = np.zeros((h, w, 4), np.float32)
    broad = f in ('north', 'south')
    edge = (X == 0) | (X == w - 1) | (Y == 0) | (Y == h - 1)

    def put_glow(mask, col, k=1.0):
        mk = np.clip(np.asarray(mask, np.float32), 0, 1) * k
        glow[..., :3] = np.maximum(glow[..., :3], col * mk[..., None])
        glow[..., 3] = np.maximum(glow[..., 3], 255 * mk)

    def fill(c):
        return np.ones((h, w, 1), np.float32) * hx(c)

    def cel(base, light, shade, outline=None, top_rows=1, shade_rows=None):
        """上の 1 行を明るく、下と右を暗く、縁を濃く（ドット絵の箱）"""
        r = fill(base)
        sr = shade_rows if shade_rows is not None else max(1, h // 4)
        r[Y >= h - sr] = hx(shade)
        r[(X == w - 1) & (Y > 0)] = hx(shade)
        r[Y < top_rows] = hx(light)
        r[(X == 0) & (Y < h - sr)] = hx(light) * 0.5 + hx(base) * 0.5
        if outline is not None:
            r[edge] = hx(outline)
        return r

    if mat == 'x':
        rgb = fill(0)
        alpha[:] = 0
    elif mat in ('skin', 'face', 'face_lo'):
        rgb = fill(0xF7E4E4)
        if f in ('east', 'west', 'south') or mat == 'face_lo':
            rgb[Y >= h - max(1, h // 5)] = hx(0xEFD0D6)
        if f == 'down':
            rgb = fill(0xE4C0C8)
        if mat == 'face':                                            # 前髪の落とす淡い陰（上の 2 行）
            rgb[Y < max(2, h // 8)] = hx(0xE6CCD8)
    elif mat == 'blush':                                             # 頬の赤み（淡い楕円と斜線 3 本）
        r_ = ((U - 0.5) / 0.5) ** 2 + ((V - 0.5) / 0.5) ** 2
        inside = r_ <= 1
        rgb = fill(0xF4AABA)
        hatch = ((X + Y) % 4 == 0) & (np.abs(U - 0.5) < 0.35)
        rgb[hatch] = hx(0xE07890)
        alpha[~inside] = 0
        alpha[inside & ~hatch & ((X + Y) % 2 == 1)] = 0                # まばらに（ドット絵の透け）
    elif mat == 'nose':
        rgb = fill(0xD8AEBA)
    elif mat in ('mouth_n', 'mouth_s', 'mouth_o'):
        rgb = fill(0x8A3A50)
        alpha[:] = 0
        cx = w // 2
        if mat == 'mouth_n':                                         # 小さな「へ」の口と八重歯
            yl = h // 2 - 1
            for x in range(cx - 4, cx + 4):
                yy = yl + (1 if abs(x - cx + 0.5) > 2.5 else 0) - (1 if x == cx + 1 else 0) * 0
                alpha[yy, x] = 255
            for yy in range(yl + 1, yl + 3):
                alpha[yy, cx + 2] = 255
                rgb[yy, cx + 2] = hx(0xFFFFFF)
        elif mat == 'mouth_s':                                       # 笑み（下へ弧）
            for x in range(cx - 4, cx + 4):
                yy = h // 2 - 1 + (0 if abs(x - cx + 0.5) > 2.5 else 1)
                alpha[yy, x] = 255
        else:                                                        # 開いた口とギザ歯
            r_ = ((X - (w - 1) / 2) / (w * 0.3)) ** 2 + ((Y - (h - 1) / 2) / (h * 0.42)) ** 2
            inside = r_ <= 1
            rgb = fill(0x5A1C30)
            rgb[Y >= h * 0.65] = hx(0xD86A84)                         # 舌
            teeth = inside & (Y <= h * 0.38) & (((X - w // 2) % 3) != 1) & (Y >= h * 0.12)
            rgb[teeth] = hx(0xFFFFFF)
            alpha[inside] = 255
    elif mat == 'eye_w':                                             # 白目（目尻がとがるアーモンド形。上はまぶたの影）
        xx = U * 2 - 1
        top = 0.12 + 0.18 * xx ** 2 - 0.06 * xx
        bot = 0.92 - 0.12 * xx ** 2
        inside = (V >= top) & (V <= bot) & (np.abs(xx) <= 0.98)
        rgb = fill(0xF8F4FA)
        rgb[V < top + 0.22] = hx(0xD8CCE6)
        alpha[~inside] = 0
    elif mat == 'iris':                                              # 虹彩（縦の 4 段の赤、縦長の瞳、下の明るい三日月、縁は濃く）
        r_ = ((U - 0.5) / 0.5) ** 2 + ((V - 0.5) / 0.5) ** 2
        inside = r_ <= 1.0
        rgb = fill(0x3A0816)
        rgb[V >= 0.3] = hx(0x9A1028)
        rgb[V >= 0.55] = hx(0xE0283E)
        rgb[(V >= 0.78) & (np.abs(U - 0.5) < 0.32)] = hx(0xFF7A8C)
        rgb[r_ > 0.72] = hx(0x2A0610)
        pup = ((U - 0.5) / 0.16) ** 2 + ((V - 0.45) / 0.26) ** 2 <= 1
        rgb[pup] = hx(0x1A0008)
        alpha[~inside] = 0
        put_glow(inside & (V >= 0.55), hx(0xFF2A48), 0.55)
    elif mat == 'hl_c':                                              # ハイライト（白い十字の星）
        c = (w - 1) / 2
        star = ((np.abs(X - c) <= 0.6) & (np.abs(Y - c) <= c)) | ((np.abs(Y - c) <= 0.6) & (np.abs(X - c) <= c)) | ((np.abs(X - c) <= 1.2) & (np.abs(Y - c) <= 1.2))
        rgb = fill(0xFFFFFF)
        alpha[~star] = 0
        put_glow(star, hx(0xFFFFFF), 0.8)
    elif mat == 'hl_d':
        r_ = ((U - 0.5) / 0.5) ** 2 + ((V - 0.5) / 0.5) ** 2
        rgb = fill(0xFFFFFF)
        alpha[r_ > 1] = 0
        put_glow(r_ <= 1, hx(0xFFFFFF), 0.7)
    elif mat == 'lash':
        rgb = fill(0x1E1622)
        rgb[Y == h - 1] = hx(0x3A2840)
    elif mat == 'brow':
        rgb = fill(0x7E7494)
        rgb[Y == 0] = hx(0x9A90B0)
    elif mat in ('hair', 'hair_hl'):                                 # 髪（地・陰・深い陰の 3 段、縦の毛筋、毛先の側は暗く、天使の輪）
        rgb = fill(0xE6E4F0)
        off = rs.randint(0, 4)
        rgb[(X + off) % 4 == 0] = hx(0xC8C2DC)
        if f in ('north', 'south', 'east', 'west'):
            rgb[V > 0.72] = hx(0xB6B0CE)
            rgb[(V > 0.72) & ((X + off) % 4 == 0)] = hx(0x8C84AC)
        if f == 'up':
            rgb = fill(0xF2F0F8)
        if f == 'down':
            rgb = fill(0x9A92B8)
        if mat == 'hair_hl' and f == 'north':                        # 天使の輪（途切れる白い帯）
            band = (np.abs(V - 0.45) < 0.12) & ((X // 2 + seed) % 5 != 0)
            rgb[band] = hx(0xFFFFFF)
    elif mat == 'lock':                                              # 房（先へとがる。縁は淡い藤色、真ん中の毛筋、先は暗く）
        taper = np.where(V < 0.45, 1.0, np.clip(1 - (V - 0.45) / 0.55, 0, 1) ** 0.8)
        half = 0.5 * taper
        lean = 0.06 * (rs.rand() - 0.5) * V * 4
        q = U - 0.5 - lean
        inside = np.abs(q) <= half + 0.5 / w
        rgb = fill(0xE6E4F0)
        rgb[np.abs(q) < 0.6 / w] = hx(0xC8C2DC)
        rgb[V > 0.6] = hx(0xB6B0CE)
        rgb[(V > 0.82)] = hx(0x9A92B8)
        rim = inside & (np.abs(q) > half - 1.0 / w)
        rgb[rim] = hx(0xA49CC0)
        rgb[(V < 0.12)] = hx(0xF4F2FA)
        if broad:
            alpha[~inside] = 0
        else:
            alpha[:] = 0
    elif mat in ('armor', 'armor_g', 'armor_s', 'bodice', 'bodice_f', 'cup', 'cup_t', 'cup_b', 'chest_f'):
        if mat == 'armor_s':
            rgb = cel(0x5E5A74, 0x8A86A2, 0x46435A, 0x24212E)
        elif mat.startswith('cup'):                                   # 胸の杯は縁取りを付けない（板ごとの線で段に見えるので）
            rgb = cel(0x3E3A4E, 0x5C5872, 0x2A2736, None, shade_rows=max(1, h // 3))
        else:
            rgb = cel(0x3E3A4E, 0x5C5872, 0x2A2736, 0x1A1722)
        if mat == 'armor_g':                                          # マゼンタの光る線（長い向きの真ん中）
            line = (X == w // 2) if h >= w else (Y == h // 2)
            rgb[line] = hx(0xFF3A9A)
            put_glow(line, hx(0xFF2A9A))
        if mat == 'armor_s' and broad and w > 4 and h > 4:            # 内側の細いマゼンタの縁
            ring_ = ((X == 1) | (X == w - 2) | (Y == 1) | (Y == h - 2)) & ~edge
            rgb[ring_] = hx(0xFF4AA8)
            put_glow(ring_, hx(0xFF2A9A), 0.8)
        if mat in ('bodice_f', 'chest_f'):                            # 胴の前の 2 本のマゼンタの波線
            for c0, ph in ((0.27, 0), (0.73, 2)):
                xl = np.round((c0 + 0.035 * np.sin((Y + ph) * 0.7)) * w - 0.5).astype(int)
                ln = X == xl
                rgb[ln] = hx(0xFF3A9A)
                put_glow(ln, hx(0xFF2A9A), 0.9)
        if mat == 'chest_f':                                          # 胸元の肌（襟元は広く、胸の谷間へ V に細る）と縁
            half = 0.36 * np.clip((1 - V) * 1.6, 0, 1) + 0.04
            sk = np.abs(U - 0.5) <= half
            rgb[sk] = hx(0xF7E4E4)
            rim = sk & ~(np.abs(U - 0.5) <= half - 1.2 / w)
            rgb[rim] = hx(0xFF4AA8)
            put_glow(rim, hx(0xFF2A9A), 0.9)
            rgb[sk & (V < 0.12)] = hx(0xEFD0D6)
        if mat == 'cup_t':                                            # 胸の杯の上の縁（マゼンタ）
            rgb[Y <= 0] = hx(0xFF4AA8)
            put_glow(Y <= 0, hx(0xFF2A9A), 0.9)
            rgb[(Y == 1)] = hx(0x5C5872)
        if mat == 'cup_b':
            rgb = rgb * 0.82
    elif mat == 'silver':
        rgb = cel(0xC6C8D8, 0xF2F4FC, 0x9496AA, 0x6E7088)
    elif mat == 'crown':
        rgb = cel(0x3A3848, 0x6A6880, 0x2A2834, 0x16141C)
    elif mat in ('spike_a', 'spike_c', 'spike_h', 'visor_w'):        # 棘の板（付け根から先へ細る三角。flat で長さは X）
        half = 0.5 * (1 - U) ** 0.9
        q = V - 0.5
        inside = np.abs(q) <= half + 0.5 / h
        lit = q < 0
        if mat == 'spike_a':
            rgb = np.where(lit[..., None], hx(0x6A6982), hx(0x4E4C62))
            rim = inside & (np.abs(q) > half - 1.0 / h)
            rgb[rim] = hx(0xFF4AA8)
            put_glow(rim, hx(0xFF2A9A), 0.8)
        elif mat == 'spike_c':
            rgb = np.where(lit[..., None], hx(0x5A5870), hx(0x34323F))
        elif mat == 'spike_h':
            rgb = np.where(lit[..., None], hx(0xE6E4F0), hx(0xB6B0CE))
        else:
            rgb = np.where(lit[..., None], hx(0xFF5A70), hx(0xD0102E))
            put_glow(inside, hx(0xFF2040))
        if broad:
            alpha[~inside] = 0
        else:
            alpha[:] = 0
    elif mat == 'visor':
        rgb = fill(0xD0102E)
        rgb[(Y >= h * 0.3) & (Y <= h * 0.62)] = hx(0xFF5A70)
        rgb[(Y >= h * 0.42) & (Y <= h * 0.5)] = hx(0xFFC8D0)
        rgb[(Y % 3 == 0) & ~((Y >= h * 0.42) & (Y <= h * 0.5))] *= 0.82
        rgb[(Y == 0) | (Y == h - 1)] = hx(0x2A1820)
        put_glow((Y > 0) & (Y < h - 1), hx(0xFF2040))
    elif mat in ('core', 'gem'):
        c = (w - 1) / 2
        d_ = np.abs(X - c) + np.abs(Y - c)
        rgb = fill(0xFF1A3A if mat == 'core' else 0xFF2A5A)
        rgb[d_ > c * 0.8] = hx(0x9A0A22)
        rgb[d_ < c * 0.35] = hx(0xFFE0E8)
        put_glow(np.ones_like(X), hx(0xFF1A3A))
    elif mat in ('ring_r', 'ring_v'):                                # 光輪（輪を描いた板。輪の外と内は透明。ところどころ途切れる）
        col = hx(0xFF2A44) if mat == 'ring_r' else hx(0xB060FF)
        uu, vv = (U - 0.5) * 2, (V - 0.5) * 2
        r_ = np.sqrt(uu * uu + vv * vv)
        th = np.arctan2(vv, uu)
        gap = np.sin(th * 5 + (seed % 7)) > 0.94
        band = (r_ > 0.82) & (r_ < 0.97) & ~gap
        if f in ('up', 'down'):
            rgb = np.ones((h, w, 1), np.float32) * col
            rgb[(r_ > 0.87) & (r_ < 0.92)] = hx(0xFFFFFF) * 0.6 + col * 0.4
            put_glow(band, col)
            alpha[~band] = 0
        else:
            alpha[:] = 0
    elif mat == 'chain_p':                                           # 鎖（輪を交互に、正面と横向きに。外は透明）
        period = max(2, int(round(0.6 * w / max(dw, 1e-3))))
        ph = (X % (2 * period)) < period
        ly = np.abs(V - 0.5) * 2
        lx = np.abs(((X % period) + 0.5) / period - 0.5) * 2
        ring = ph & ((np.abs(np.sqrt(lx ** 2 + ly ** 2 / 0.6) - 0.75) < 0.3))
        bar = ~ph & (ly < 0.3)
        rgb = fill(0x4A4A58)
        rgb[ly < 0.25] = hx(0x6A6A7C)
        alpha[~(ring | bar)] = 0
        if not broad:
            alpha[:] = 0
    elif mat in ('cloth_w', 'cloth_s', 'cloth_d', 'tatter'):
        pleat = (X % 4 == 0)
        if mat == 'tatter':
            rgb = fill(0x1C1622)
            rgb[V > 0.45] = hx(0x34204A)
            rgb[V > 0.75] = hx(0x5A2A80)
            rgb[pleat] *= 0.8
            if broad and dh > 1.0:
                teeth = max(1, int(round(dw / 0.45)))
                tri = np.abs(((U * teeth) % 1.0) - 0.5) * 2
                alpha[V > 1 - 0.55 * (1 - tri)] = 0
        elif mat == 'cloth_d':
            rgb = fill(0x2A2632)
            rgb[pleat] = hx(0x1E1A26)
        else:
            rgb = fill(0xEEEEF4)
            rgb[pleat] = hx(0xC8C8D8)
            rgb[((X + 1) % 4 == 0)] = hx(0xFFFFFF)
            if mat == 'cloth_s':                                      # 裾へ暗い灰と紫へ（ドットの市松で 2 段ににじむ）
                d1 = (V > 0.62) & ((X + Y) % 2 == 0) | (V > 0.7)
                rgb[d1] = hx(0x6A6478)
                rgb[(V > 0.78) & ((X + Y) % 2 == 0) | (V > 0.84)] = hx(0x3A2E48)
                rgb[V > 0.92] = hx(0x5A2A80)
                if broad and dh > 6:                                  # 裾を破れた形に抜く（ドットのぎざぎざ）
                    cols = np.cumsum(rs.uniform(0.2, 0.6, 128))
                    rag = np.interp(U * dw, cols - cols[0], rs.uniform(0.0, 3.2, 128))
                    alpha[V * dh > dh - 0.12 - rag] = 0
        if not broad and mat != 'cloth_d':
            alpha[:] = 0
    elif mat in ('wing_v', 'wing_p', 'wing_b', 'sword', 'blade', 'claw'):
        long_x = dw >= dh
        L, Wd = (dw, dh) if long_x else (dh, dw)
        t = U if long_x else V
        q = ((V if long_x else U) - 0.5) * Wd
        if mat in ('blade', 'claw'):
            t = 1 - t if not long_x else t
        half = Wd / 2 * np.clip(np.where(t < 0.8, 1.0, 1 - (t - 0.8) / 0.2), 0, 1) * (0.55 + 0.45 * np.sqrt(np.clip(t * 6, 0, 1)))
        px = Wd / (h if long_x else w)
        inside = np.abs(q) <= half + px * 0.5
        de = half - np.abs(q)
        if mat == 'wing_p':
            upper = Wd / 2 - np.clip((t - 0.55) / 0.45, 0, 1) * Wd * 0.92
            inside = (q <= upper) & (q >= -Wd / 2)
            de = np.minimum(upper - q, q + Wd / 2)
        if f in ('up', 'down') or Wd < 0.12 or mat == 'claw':
            inside = np.ones_like(inside)
            de = np.full_like(U, 1.0)
        lit = q < 0
        rim = inside & (de < px * 1.1)
        if mat == 'wing_v':
            rgb = np.where(lit[..., None], hx(0x6A34A0), hx(0x4A2470))
            rgb[t < 0.3] = hx(0x2A1A3C)
            rgb[rim] = hx(0xC080FF)
            put_glow(rim, hx(0xB060FF))
        elif mat == 'wing_p':
            rgb = np.where(lit[..., None], hx(0x9294A6), hx(0x6E7082))
            seg_ = ((t * L) % 2.2) < (2.2 / L / max(t.max(), 1e-3)) * 0 + 0.12
            rgb[seg_] = hx(0x4E5062)
            rgb[rim] = hx(0x34364A)
            seam = (np.abs(q + Wd * 0.2) < px * 0.6) & (np.sin(t * L * 3 + seed) > 0.2)
            rgb[seam] = hx(0xB060FF)
            put_glow(seam, hx(0xB040FF), 0.8)
        elif mat == 'claw':
            rgb = cel(0x8C8E9C, 0xC6C8D8, 0x5E6070)
        else:
            rgb = np.where(lit[..., None], hx(0xC6C8D8), hx(0x9496AA))
            rgb[np.abs(q) < px * 0.6] = hx(0x6E7088)
            rgb[rim] = hx(0xFF3A5A)
            put_glow(rim, hx(0xFF2040))
        alpha[~inside] = 0
        if not broad and mat != 'claw':
            alpha[:] = 0
    elif mat in ('halo_r',):
        rgb = fill(0xFF2A44)
        put_glow(np.ones_like(X), hx(0xFF2A44))
    elif mat in ('vortex', 'vortex_o', 'vortex_c'):                  # 黒い渦（ドットの渦巻き。赤と黒の 3 段、外の板に赤い稲妻）
        u = (U - 0.5) * 2
        v = (V - 0.5) * 2
        r_ = np.sqrt(u * u + v * v)
        th = np.arctan2(v, u)
        if broad:
            arms = np.sin(3 * th + 7.0 * np.log(r_ + 0.06))
            rgb = fill(0x0C0004)
            rgb[arms > 0.2] = hx(0x6A0418)
            rgb[arms > 0.6] = hx(0xE8103A)
            rgb[(arms > 0.92) & (r_ < 0.85)] = hx(0xFF9AB0)
            put_glow(arms > 0.6, hx(0xE8103A), 0.9)
            a_ = r_ < 0.95
            if mat == 'vortex_o':
                for b in range(7):
                    a0 = rs.uniform(-math.pi, math.pi)
                    tb = a0 + np.sin(r_ * 9 + b) * 0.25
                    dth = np.abs(np.angle(np.exp(1j * (th - tb))))
                    bolt = (dth * r_ * w < 1.2) & (r_ > 0.35) & (r_ < 1.02)
                    rgb[bolt] = hx(0xFFC8D4)
                    put_glow(bolt, hx(0xFF4060))
                    a_ = a_ | bolt
            if mat == 'vortex_c':
                ring = np.abs(r_ - 0.82) < 0.1
                rgb = fill(0x050003)
                rgb[ring] = hx(0xFF3050)
                put_glow(ring, hx(0xFF3050))
            alpha[~a_] = 0
        else:
            rgb = fill(0x200008)
            alpha[:] = 0
    else:
        rgb = fill(0xFF00FF)
    out = np.zeros((h, w, 4), np.float32)
    out[..., :3] = np.clip(rgb, 0, 255)
    out[..., 3] = alpha
    if not broad and mat in THIN:
        out[..., 3] = 0
    glow[out[..., 3] == 0] = 0
    return out.astype(np.uint8), glow.astype(np.uint8)


# ------------------------------------------------------------------ 詰め込み・書き出し
def dens_of(mat):
    return DENS_HD if mat.rstrip('~') in HD else DENS


def faces_of(m):
    out = []
    for bi, b in enumerate(m.bones):
        for ci, cube in enumerate(b['cubes']):
            for f in FACES:
                dw, dh = face_dims(cube['a'], cube['c'], f)
                mat = cube['mat'][f]
                d = dens_of(mat)
                w = 1 if mat == 'x' else max(1, int(math.ceil(dw * d - 1e-6)))
                h = 1 if mat == 'x' else max(1, int(math.ceil(dh * d - 1e-6)))
                out.append((bi, ci, f, dw, dh, mat, w, h))
    return out


def pack(faces, tex):
    """棚詰め（高い順に左から並べ、溢れたら次の棚）。見えない面は左上の透明な 1 テクセルを指す。入りきらなければ None"""
    order = sorted(range(len(faces)), key=lambda i: -faces[i][7])
    place = {}
    x, y, shelf = 0, 4, 0
    for i in order:
        if faces[i][5] == 'x':
            place[i] = (1, 1, 1, 1)
            continue
        w, h = faces[i][6], faces[i][7]
        if x + w > tex:
            x, y, shelf = 0, y + shelf, 0
        if y + h > tex:
            return None
        place[i] = (x, y, w, h)
        x += w
        shelf = max(shelf, h)
    return place


def noise(w, h, seed, scale):
    """なめらかな値のノイズ（粗い乱数の格子を拡大）"""
    rs = np.random.RandomState(seed & 0x7FFFFFFF)
    gw, gh = max(2, int(w / scale) + 2), max(2, int(h / scale) + 2)
    base = rs.rand(gh, gw).astype(np.float32)
    img = Image.fromarray((base * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(img, dtype=np.float32) / 255.0


def rift_texture():
    """空の裂け目の中（textures/entity/zero_rift.png。描く側 client/LostCatastropheRenderer が縦長の板に貼る）: 暗い宇宙に赤と紫の星雲、星、
    縁ほど赤く燃える（ゲームの中の絵の背景の裂け目。2026-10-20 ユーザー指定「裂け目と残骸を形態変化にとっておく」）"""
    W, H = 256, 1024
    X, Y = np.meshgrid(np.arange(W, dtype=np.float32) / W, np.arange(H, dtype=np.float32) / H)
    n1 = noise(W, H, 101, 48.0)
    n2 = noise(W, H, 202, 18.0)
    n3 = noise(W, H, 303, 7.0)
    neb = np.clip(n1 * 0.6 + n2 * 0.3 + n3 * 0.1, 0, 1)
    red = np.clip((neb - 0.42) / 0.3, 0, 1)
    purple = np.clip((n2 - 0.5) / 0.3, 0, 1) * (1 - red * 0.5)
    rgb = hx(0x07030E) * np.ones((H, W, 1), np.float32)
    rgb = rgb + hx(0x8A1030) * red[..., None] + hx(0x5A1C8A) * purple[..., None] * 0.8
    rgb = rgb + hx(0xFF6080) * (np.clip((neb - 0.7) / 0.15, 0, 1) ** 2)[..., None] * 0.6
    rs = np.random.RandomState(5)
    for _ in range(420):                                              # 星
        x, y = rs.randint(0, W), rs.randint(0, H)
        b_ = rs.uniform(0.5, 1.0)
        rgb[y, x] = np.maximum(rgb[y, x], 255 * b_)
        if b_ > 0.9 and 0 < x < W - 1 and 0 < y < H - 1:
            rgb[y, x - 1] = np.maximum(rgb[y, x - 1], 140)
            rgb[y, x + 1] = np.maximum(rgb[y, x + 1], 140)
            rgb[y - 1, x] = np.maximum(rgb[y - 1, x], 140)
            rgb[y + 1, x] = np.maximum(rgb[y + 1, x], 140)
    edge = np.minimum(X, 1 - X)                                        # 縁ほど赤く燃える
    burn = np.clip(1 - edge / 0.16, 0, 1) ** 1.5
    rgb = rgb * (1 - burn[..., None] * 0.7) + hx(0xC0102A) * burn[..., None] * 0.9
    out = np.zeros((H, W, 4), np.uint8)
    out[..., :3] = np.clip(rgb, 0, 255)
    out[..., 3] = 255
    Image.fromarray(out, 'RGBA').save(os.path.join(ASSETS, 'textures', 'entity', 'zero_rift.png'), compress_level=9)


def main():
    rift_texture()
    m = build()
    faces = faces_of(m)
    tex = 512
    place = pack(faces, tex)
    while place is None:
        tex *= 2
        place = pack(faces, tex)
    print(f'{IDENT}: {len(m.bones)} bones, {sum(len(b["cubes"]) for b in m.bones)} cubes, {len(faces)} faces, tex {tex}')
    tw, th = tex, tex
    base = np.zeros((tex, tex, 4), np.uint8)
    glow = np.zeros((tex, tex, 4), np.uint8)
    for i, fc in enumerate(faces):
        bi, ci, f, dw, dh, mat, w, h = fc
        if mat == 'x':
            continue
        u, v, w, h = place[i]
        img, gl = paint(mat, f, w, h, max(dw, 1e-3), max(dh, 1e-3), hash((bi, ci, f)) & 0xFFFFFF)
        base[v:v + h, u:u + w] = img
        glow[v:v + h, u:u + w] = gl
    out = []
    k = 0
    lookup = {}
    for bi, b in enumerate(m.bones):
        for ci, cube in enumerate(b['cubes']):
            for f in FACES:
                lookup[(bi, ci, f)] = place[k]
                k += 1
    for bi, b in enumerate(m.bones):
        o = {'name': b['name'], 'pivot': [round(p, 3) for p in b['pivot']]}
        if b['parent']:
            o['parent'] = b['parent']
        if b['cubes']:
            cubes = []
            for ci, cube in enumerate(b['cubes']):
                uv = {}
                for f in FACES:
                    u, v, w, h = lookup[(bi, ci, f)]
                    uv[f] = {'uv': [u, v], 'uv_size': [w, h]}
                co = {'origin': [round(t, 3) for t in cube['a']], 'size': [round(cube['c'][i] - cube['a'][i], 3) for i in range(3)], 'uv': uv}
                if 'rot' in cube:
                    co['pivot'] = [round(t, 3) for t in cube['pivot']]
                    co['rotation'] = cube['rot']
                cubes.append(co)
            o['cubes'] = cubes
        out.append(o)
    if MESH:                                                                         # 箱の絵の右に OBJ の色を並べ、骨ごとの面を書く
        os.makedirs(os.path.join(ASSETS, 'mesh'), exist_ok=True)
        base, glow, tw, th, _ = lc_objmesh.build((base, glow), tex, os.path.join(ASSETS, 'mesh', IDENT + '.bin'))
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{IDENT}', 'texture_width': tw, 'texture_height': th,
                        'visible_bounds_width': 10, 'visible_bounds_height': 10, 'visible_bounds_offset': [0, 3, 0]},
        'bones': out}]}
    with open(os.path.join(ASSETS, 'geo', 'entity', IDENT + '.geo.json'), 'w', encoding='utf-8', newline='\n') as fo:
        json.dump(geo, fo, separators=(',', ':'))
    Image.fromarray(base, 'RGBA').save(os.path.join(ASSETS, 'textures', 'entity', IDENT + '.png'), compress_level=9)
    Image.fromarray(glow, 'RGBA').save(os.path.join(ASSETS, 'textures', 'entity', IDENT + '_glowmask.png'), compress_level=9)   # 本体と同じ大きさ（GeckoLib は違うと落ちる）
    print('wrote geo and textures')


if __name__ == '__main__':
    main()
