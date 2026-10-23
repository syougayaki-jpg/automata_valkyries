"""零号機の体を OBJ の面のまま GeckoLib の骨に載せる（2026-10-21 ユーザー指定「オススメのやりかたで全部かえてみて」。OBJ は有料プランの Tripo で作った物）。

GeckoLib 4.8.4 は geo の poly_mesh を読むだけで描かないので、OBJ の面を骨ごとに分けて別のファイル（assets/automata_valkyries/mesh/lost_catastrophe.bin。gzip）に書き、
描く側（LostCatastropheRenderer）が骨を描くときに同じ骨の変換で一緒に描く。2026-10-21 ユーザー指定「顔を同じように変換して」で、首より上（顔・髪・冠・面頬）も OBJ の面にして頭の骨に載せる。
同日「あごのあたりが貫通しているのと胸の陥没したシンギュラリティを消して」: 首（襟の上まで）も頭の骨に入れ、頭の骨の回る所を首の付け根に下げた
（下を向くと黒い襟があごを突き抜けていた）。同日「胸の違和感もあるのでこっちのモデルにしましょう」で、OBJ を胸にハートの穴の無い別のモデル（fantasy winged knight。同じく Tripo）に替えた。
顔（肌）とバイザーは髪の前の縁より奥にあるので、少し前へ出す（push_face）。
頭上の光輪は、OBJ の物が頭の後ろでねじれた形（上から見て V 字、横から見て 8 の字）なので捨て、頭の上に作った輪に替えた
（2026-10-21 ユーザー指定「光輪は修正して頭の上に」）。平らな帯の輪と外・内の縁、外へ向く 8 本の棘。絵は箱の絵の場所（左上）に描く（halo_quads・paint_halo）。
光輪の骨（halo_top で後ろを少し上げて傾け、子の halo_spin で回す）に載せる。

骨分け: 面の中心から腕・脚・胴の軸（関節を結ぶ線）までの近さで決め、どれにも近くなければ背中の後ろ・外は翼、腰より下はスカート。
同じ破片（OBJ のつながった面の集まり）は面積の多数決で 1 つの骨にそろえる（布の帯が途中で裂けないように）。
テクスチャは OBJ の色を 2048 に縮めて、箱の絵（左の正方形）の右に並べる（GeckoLib は 1 体に 1 枚なので）。光るのは赤・赤紫の強い所。
光の絵は不透明（光るか光らないか）にする: GeckoLib（AutoGlowingTexture・GeoGlowingTextureMeta）は光の絵に色のある点を本体の絵から消して
光の層だけで半透明に描くので、不透明度に強弱を付けるとその分だけ透ける（2026-10-21 ユーザー指定「バイザーが透けている」の原因）。
"""
import gzip
import math
import os
import struct

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OBJ = os.path.join(HERE, 'models', 'lost_catastrophe_knight.obj')
BASECOLOR = os.path.join(HERE, 'models', 'lost_catastrophe_knight_basecolor.jpg')
K = 36.5                 # OBJ の 1 → geo の px（首 0.735 が頭の骨 26.8、肩 0.69 が 25.2、ひざ 0.30 が 11）
Z0 = 0.06                # 胴の中心の奥行き（geo の z 0 に置く）
REGION = 2048            # テクスチャの中の OBJ の色の大きさ


def to_geo(p):
    """OBJ（正面 +Z・右 -X）→ geo（正面 -Z・右 -X）の px"""
    p = np.asarray(p, float)
    return np.stack([p[..., 0] * K, p[..., 1] * K, -(p[..., 2] - Z0) * K], -1)


def geo_of(x, y, z):
    g = to_geo([x, y, z])
    return tuple(round(float(v), 2) for v in g)


# 関節（OBJ の座標。正面・横から測った。2026-10-21、モデルを替えて測り直した）
J = {
    'shoulder_r': (-0.088, 0.685, 0.045), 'elbow_r': (-0.11, 0.595, 0.05), 'wrist_r': (-0.14, 0.49, 0.065), 'tip_r': (-0.16, 0.415, 0.065),
    'hip_r': (-0.045, 0.47, 0.075), 'knee_r': (-0.038, 0.30, 0.02), 'ankle_r': (-0.016, 0.08, 0.0), 'toe_r': (-0.017, 0.0, 0.1),
    'hip_l': (0.045, 0.47, 0.085), 'knee_l': (0.034, 0.30, 0.12), 'ankle_l': (0.025, 0.08, 0.13), 'toe_l': (0.017, 0.0, 0.19), 'heel_r': (-0.02, 0.0, -0.07), 'heel_l': (0.02, 0.0, 0.06),
    'pelvis': (0.0, 0.47, 0.075), 'waist': (0.0, 0.6, 0.065), 'neck': (0.0, 0.735, 0.05), 'wing_root': (0.0, 0.66, 0.0),
    'neck_base': (0.0, 0.705, 0.05),
    'tasset_top_r': (-0.07, 0.49, 0.06), 'tasset_bot_r': (-0.15, 0.36, 0.07), 'tasset_top_l': (0.07, 0.49, 0.06), 'tasset_bot_l': (0.15, 0.36, 0.07),
}
for k_ in ('shoulder', 'elbow', 'wrist', 'tip'):
    x, y, z = J[k_ + '_r']
    J[k_ + '_l'] = (-x, y, z)

# 骨の軸と太さ（OBJ の単位）
SEGS = [
    ('arm_r', 'shoulder_r', 'elbow_r', 0.05), ('forearm_r', 'elbow_r', 'wrist_r', 0.045), ('hand_r', 'wrist_r', 'tip_r', 0.05),
    ('arm_l', 'shoulder_l', 'elbow_l', 0.05), ('forearm_l', 'elbow_l', 'wrist_l', 0.045), ('hand_l', 'wrist_l', 'tip_l', 0.05),
    # 脚の太さは前と後ろで変える（LEG_FRONT・LEG_BACK）: 後ろはふくらはぎ・足首の装甲が軸から離れていてスカートに入り
    # （2026-10-21 ユーザー指定「右足のふくらはぎ左足の足首の一部が破れたスカートに追従している」）、太くすると前のスカートの垂れが脚に入った
    # （同日「スカートの一部が左足前に追従している」）。下の数は横の太さ
    ('leg_r', 'hip_r', 'knee_r', 0.06), ('shin_r', 'knee_r', 'ankle_r', 0.06), ('shin_r', 'ankle_r', 'toe_r', 0.055),
    ('leg_l', 'hip_l', 'knee_l', 0.06), ('shin_l', 'knee_l', 'ankle_l', 0.06), ('shin_l', 'ankle_l', 'toe_l', 0.055),
    ('shin_r', 'ankle_r', 'heel_r', 0.055), ('shin_l', 'ankle_l', 'heel_l', 0.055),
    ('body', 'pelvis', 'waist', 0.085), ('chest', 'waist', 'neck', 0.1),
    # 腰の横の装甲（草摺）は腰に（手の近くなので手に入っていた。同日「腰の装甲が手に追従している」）
    ('body', 'tasset_top_r', 'tasset_bot_r', 0.07), ('body', 'tasset_top_l', 'tasset_bot_l', 0.07),
]


def pivots():
    """生成器の骨の pivot（geo の px）"""
    return {
        'head': geo_of(*J['neck_base']),
        'arm_r': geo_of(*J['shoulder_r']), 'arm_l': geo_of(*J['shoulder_l']),
        'forearm_r': geo_of(*J['elbow_r']), 'forearm_l': geo_of(*J['elbow_l']),
        'hand_r': geo_of(*J['wrist_r']), 'hand_l': geo_of(*J['wrist_l']),
        'leg_r': geo_of(*J['hip_r']), 'leg_l': geo_of(*J['hip_l']),
        'shin_r': geo_of(*J['knee_r']), 'shin_l': geo_of(*J['knee_l']),
        'wing_r': geo_of(-0.03, 0.66, 0.0), 'wing_l': geo_of(0.03, 0.66, 0.0),
        'halo_top': HALO_C, 'halo_spin': HALO_C,
    }


# 頭上の光輪（geo の px）: 中心は冠の先（約 33.8）の少し上、頭の真ん中の上。半径・帯の幅・厚さ。傾き（骨 halo_top の rotX。負で後ろが上がる）
HALO_C = (0.0, 34.9, 0.6)
HALO_R, HALO_W, HALO_T, HALO_TILT = 3.6, 0.75, 0.3, -0.25
HALO_SEGS, HALO_THORNS = 40, 8
HALO_STRIP = (0, 0, 512, 64)      # 箱の絵の場所の中の光輪の絵（x, y, 幅, 高さ。行 0-31 帯の面、32-47 縁、48-63 棘）


def halo_quads(W, H):
    """光輪の面（平らな輪。傾きは骨で付ける）。[(4 点の (x, y, z, u, v), 法線)]"""
    cx, cy, cz = HALO_C
    sx, sy, sw, sh = HALO_STRIP
    ri, ro, h = HALO_R - HALO_W / 2, HALO_R + HALO_W / 2, HALO_T / 2
    out = []

    def uv(u, v):
        return ((sx + u * sw) / W, (sy + v * sh) / H)

    def pt(r, a, y):
        return (cx + r * math.cos(a), cy + y, cz + r * math.sin(a))
    for k in range(HALO_SEGS):
        a0, a1 = 2 * math.pi * k / HALO_SEGS, 2 * math.pi * (k + 1) / HALO_SEGS
        u0, u1 = (k % 8) / 8, (k % 8 + 1) / 8                                # 帯の絵を 8 区切りでくり返す
        out.append(([(*pt(ri, a0, h), *uv(u0, 0.0)), (*pt(ro, a0, h), *uv(u0, 0.5)), (*pt(ro, a1, h), *uv(u1, 0.5)), (*pt(ri, a1, h), *uv(u1, 0.0))], (0.0, 1.0, 0.0)))
        out.append(([(*pt(ri, a0, -h), *uv(u0, 0.0)), (*pt(ri, a1, -h), *uv(u1, 0.0)), (*pt(ro, a1, -h), *uv(u1, 0.5)), (*pt(ro, a0, -h), *uv(u0, 0.5))], (0.0, -1.0, 0.0)))
        am = (a0 + a1) / 2
        for r, sg in ((ro, 1.0), (ri, -1.0)):                                # 外と内の縁
            out.append(([(*pt(r, a0, -h), *uv(u0, 0.5)), (*pt(r, a1, -h), *uv(u1, 0.5)), (*pt(r, a1, h), *uv(u1, 0.75)), (*pt(r, a0, h), *uv(u0, 0.75))],
                        (sg * math.cos(am), 0.0, sg * math.sin(am))))
    for k in range(HALO_THORNS):                                            # 外へ向く棘（輪の面の菱形。長短を交互に）
        a = 2 * math.pi * (k + 0.5) / HALO_THORNS
        L = 1.1 if k % 2 == 0 else 0.7
        d = 0.16
        q = [(*pt(ro - 0.1, a, 0), *uv(0.0, 0.875)), (*pt(ro + 0.05, a - d, 0), *uv(0.5, 0.75)), (*pt(ro + L, a, 0), *uv(1.0, 0.875)), (*pt(ro + 0.05, a + d, 0), *uv(0.5, 1.0))]
        out.append((q, (0.0, 1.0, 0.0)))
    return out


def paint_halo(img, glw):
    """光輪の絵（暗い紅の帯に明るい赤の芯の線とひび、縁は黒紅、棘は根元が暗く先が光る）"""
    sx, sy, sw, sh = HALO_STRIP
    yy, xx = np.mgrid[0:sh, 0:sw].astype(np.float32)
    u, v = xx / sw, yy / sh
    rgb = np.zeros((sh, sw, 3), np.float32)
    gl = np.zeros((sh, sw), np.float32)
    band = v < 0.5
    t = v / 0.5                                                             # 帯の内 0 → 外 1
    core = np.exp(-((t - 0.5) / 0.12) ** 2)
    crack = (np.abs(np.sin(u * 8 * math.pi * 3 + np.sin(u * 50) * 0.8) * (t - 0.5)) < 0.03) & (np.abs(t - 0.5) > 0.12)
    base = np.array([90, 8, 30], np.float32) * (0.7 + 0.3 * np.sin(t * math.pi))[..., None]
    c = base + (np.array([255, 60, 90], np.float32) - base) * core[..., None]
    c[crack] = [255, 90, 120]
    edge = (t < 0.08) | (t > 0.92)
    c[edge] = [35, 4, 14]
    rgb[band] = c[band]
    gl[band] = np.clip(core * 1.0 + crack * 0.8, 0, 1)[band]
    rim = (v >= 0.5) & (v < 0.75)
    rgb[rim] = [55, 6, 20]
    gl[rim] = 0.25
    th = v >= 0.75                                                          # 棘: 根元（u 0）が暗く、先（u 1）が光る
    tc = np.array([60, 6, 22], np.float32) + (np.array([255, 70, 110], np.float32) - np.array([60, 6, 22], np.float32)) * (u ** 1.5)[..., None]
    rgb[th] = tc[th]
    gl[th] = (u ** 2)[th]
    img[sy:sy + sh, sx:sx + sw, :3] = rgb.clip(0, 255).astype(np.uint8)
    img[sy:sy + sh, sx:sx + sw, 3] = 255
    lit = gl >= GLOW_CUT
    glw[sy:sy + sh, sx:sx + sw, :3] = (rgb * lit[..., None]).clip(0, 255).astype(np.uint8)
    glw[sy:sy + sh, sx:sx + sw, 3] = lit * 255


def load_obj(path=OBJ):
    V, VT, F = [], [], []
    with open(path, encoding='utf-8', errors='replace') as f:
        for line in f:
            t = line.split()
            if not t:
                continue
            if t[0] == 'v':
                V.append([float(a) for a in t[1:4]])
            elif t[0] == 'vt':
                VT.append([float(a) for a in t[1:3]])
            elif t[0] == 'f':
                idx = [a.split('/') for a in t[1:]]
                F.append(([int(a[0]) - 1 for a in idx], [int(a[1]) - 1 for a in idx]))
    V, VT = np.array(V), np.array(VT)
    push_face(V, VT, F)
    return V, VT, F


FACE_PUSH = 0.016        # 顔とバイザーを前へ出す量（OBJ の単位。約 0.6 px）


def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


def push_face(V, VT, F):
    """顔（肌）とバイザーを前へ出す（2026-10-21 ユーザー指定「このモデルは顔が凹んでいるのでちょっと前方にバイザーと共に出す」「バイザーが透けている」。
    顔が髪の前の縁より奥にあり、斜めから見ると髪がバイザーにかぶって透けて見えた）。点ごとに、顔の範囲の中ほど大きく・縁ほど小さく動かして
    （つなぎ目が裂けないように）、髪の灰色の点は 4 割だけ動かす"""
    x, y, z = V[:, 0], V[:, 1], V[:, 2]
    w = smooth((0.05 - np.abs(x)) / 0.02) * smooth((y - 0.735) / 0.01) * smooth((0.815 - y) / 0.015) * smooth((z - 0.075) / 0.02)
    if not w.any():
        return
    tex = np.asarray(Image.open(BASECOLOR).convert('RGB')).astype(np.float32)
    th, tw = tex.shape[:2]
    acc = np.zeros((len(V), 3))
    cnt = np.zeros(len(V))
    for vs, ts in F:
        for v, t in zip(vs, ts):
            if w[v] > 0:
                u_, v_ = VT[t]
                acc[v] += tex[min(int((1 - v_) * th), th - 1), min(int(u_ * tw), tw - 1)]
                cnt[v] += 1
    c = acc / np.maximum(cnt, 1)[:, None]
    gray = (c.max(1) - c.min(1)) < 25                                       # 髪（灰色）
    V[:, 2] += FACE_PUSH * w * np.where(gray, 0.4, 1.0)


LEG_FRONT, SHIN_FRONT, LEG_BACK = 0.045, 0.065, 0.075   # 腿の前（+Z。前にスカートの垂れがある）・すねの前（スカートは前で開いている）・後ろの太さ


def seg_dist_leg(P, a, b, r, front=LEG_FRONT):
    """脚の軸までの近さ（太さで割った値）。前は細く後ろは太い楕円の断面"""
    a, b = np.asarray(a, float), np.asarray(b, float)
    ab = b - a
    t = np.clip(((P - a) @ ab) / (ab @ ab), 0, 1)
    o = P - (a + t[:, None] * ab)
    rz = np.where(o[:, 2] > 0, front, LEG_BACK)
    return np.sqrt((o[:, 0] / r) ** 2 + (o[:, 1] / r) ** 2 + (o[:, 2] / rz) ** 2)


def seg_dist(P, a, b):
    a, b = np.asarray(a, float), np.asarray(b, float)
    ab = b - a
    t = np.clip(((P - a) @ ab) / (ab @ ab), 0, 1)
    return np.linalg.norm(P - (a + t[:, None] * ab), axis=1)


def classify(V, F, VT):
    """面ごとの骨（None は捨てる）"""
    C = np.array([V[vs].mean(0) for vs, _ in F])
    area = np.array([np.linalg.norm(np.cross(V[vs[1]] - V[vs[0]], V[vs[-1]] - V[vs[0]])) for vs, _ in F]) + 1e-9
    x, y, z = C[:, 0], C[:, 1], C[:, 2]
    best = np.full(len(F), np.inf)
    lab = np.array([''] * len(F), dtype=object)
    for name, a, b, r in SEGS:
        if name.startswith('leg_'):
            sc = seg_dist_leg(C, J[a], J[b], r)
        elif name.startswith('shin_'):
            sc = seg_dist_leg(C, J[a], J[b], r, SHIN_FRONT)
        else:
            sc = seg_dist(C, J[a], J[b]) / r
        m = sc < best
        best[m] = sc[m]
        lab[m] = name
    far = best > 1.0
    wing = far & (((z < 0.02) & (y > 0.45)) | ((np.abs(x) > 0.19) & (y > 0.45)))      # 外へ広がるスカートの裾は翼にしない
    lab[wing] = np.where(x[wing] < 0, 'wing_r', 'wing_l')
    lab[far & ~wing & (y < 0.56)] = 'skirt_b'
    lab[far & ~wing & (y >= 0.56)] = 'chest'
    # 首より上は頭の骨（首と襟の上も頭と一緒に回す）。破片ごとの多数決の前に決める（後から面ごとに上書きすると、頭の高さにかかる
    # 翼の付け根や肩の棘の先だけが頭に付いて動いた。2026-10-21 ユーザー指定「顔にも翼の一部と肩の棘の一部が追従している」）
    head = ((y > 0.735) & (np.abs(x) < 0.12) & (z > -0.05)) | ((y > 0.705) & (np.abs(x) < 0.05) & (z > -0.01))
    lab[head] = 'head'
    # 破片ごとに面積の多数決でそろえる
    par = np.arange(len(V))

    def find(a):
        while par[a] != a:
            par[a] = par[par[a]]
            a = par[a]
        return a
    for vs, _ in F:
        r0 = find(vs[0])
        for v in vs[1:]:
            q = find(v)
            if q != r0:
                par[q] = r0
    comp = np.array([find(vs[0]) for vs, _ in F])
    votes = {}
    for i, c in enumerate(comp):
        d = votes.setdefault(c, {})
        d[lab[i]] = d.get(lab[i], 0.0) + area[i]
    for i, c in enumerate(comp):
        lab[i] = max(votes[c].items(), key=lambda kv: kv[1])[0]
    # 多数決の後の直し（2026-10-21 ユーザー指定「手にもスカート一部がまだ残って」「顔の翼の一部が追従しているのがなおっていない」。
    # 部品だけを描いて確かめた: 頭の横の平らな板は翼の付け根、首の横の暗い棘は肩の棘、手から体の方へ長く伸びる帯はスカート）
    lo = {c: np.full(3, np.inf) for c in votes}
    hi = {c: np.full(3, -np.inf) for c in votes}
    for i, c in enumerate(comp):
        lo[c] = np.minimum(lo[c], C[i])
        hi[c] = np.maximum(hi[c], C[i])
    tex = np.asarray(Image.open(BASECOLOR).convert('RGB')).astype(np.int32)
    th, tw = tex.shape[:2]
    for i, (vs, ts) in enumerate(F):
        c = comp[i]
        if lab[i] == 'head':
            if 0.075 < abs(x[i]) < 0.13 and 0.735 < y[i] < 0.80 and -0.02 < z[i] < 0.012:
                lab[i] = 'wing_r' if x[i] < 0 else 'wing_l'                      # 頭の横の翼の付け根の板
            elif abs(x[i]) > 0.085 and y[i] < 0.745:
                lab[i] = 'chest'                                                # 首の横の低い所は肩
            elif abs(x[i]) > 0.08 and y[i] < 0.765:
                u_, v_ = np.mean([VT[t] for t in ts], 0)
                if tex[min(int((1 - v_) * th), th - 1), min(int(u_ * tw), tw - 1)].sum() < 200:
                    lab[i] = 'chest'                                            # 首の横の暗い肩の棘
        elif lab[i] in ('chest', 'body') and hi[c][1] > 0.745 and hi[c][2] < 0.025 and lo[c][0] * hi[c][0] > 0                 and max(abs(lo[c][0]), abs(hi[c][0])) > 0.07:
            lab[i] = 'wing_r' if x[i] < 0 else 'wing_l'                          # 背中から頭の横へ伸びる翼の内側の羽根（同日「胴体側にも翼の一部が追従している」）
        elif lab[i] == 'body' and lo[c][1] < 0.35:
            lab[i] = 'skirt_b'                                                  # 腰から裾まで垂れる長い帯はスカートと一緒に揺らす
        elif lab[i] in ('hand_r', 'hand_l') and min(abs(lo[c][0]), abs(hi[c][0])) < 0.125 and np.sign(lo[c][0]) == np.sign(hi[c][0]):
            lab[i] = 'skirt_b'                                                  # 手から体の方へ伸びるスカートの帯
        elif lab[i] in ('hand_r', 'hand_l'):
            P = V[vs]
            if abs(x[i]) < 0.135 or (len(vs) == 3 and np.ptp(P[:, 1]) > 0.03 and abs(x[i]) < 0.152):
                lab[i] = 'body'                                                 # 手の内側の、腰の装甲の縁の細い三角
    lab[(y > 0.875) & (np.abs(x) < 0.16) & (z < -0.045)] = None                    # OBJ のねじれた光輪は捨てる（作った輪に替える）
    return lab


GLOW_CUT = 0.35         # 光る所の切り（光の絵は光るか光らないかの 2 つ。半端な不透明度にすると透ける）


def glow_of(rgb):
    """光る所: 赤・赤紫の強い所（胸の核・赤い筋・翼の赤紫の板）"""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    red = np.clip((r - 1.6 * g - 40) / 80, 0, 1) * np.clip((r - 130) / 60, 0, 1)
    mag = np.clip((np.minimum(r, b) - 2.0 * g - 30) / 60, 0, 1) * np.clip((r - 120) / 60, 0, 1)
    return np.maximum(red, mag)


def build(base_tex, tex, out_mesh):
    """箱の絵（tex×tex）の右に OBJ の色を並べたテクスチャ・光の絵と、骨ごとの面のファイルを作る。戻り値は (絵, 光, 幅, 高さ)"""
    V, VT, F = load_obj()
    lab = classify(V, F, VT)
    W, H = tex + REGION, max(tex, REGION)
    col = np.asarray(Image.open(BASECOLOR).convert('RGB').resize((REGION, REGION), Image.LANCZOS)).astype(np.float32).copy()
    img = np.zeros((H, W, 4), np.uint8)
    img[:base_tex[0].shape[0], :tex] = base_tex[0]
    img[:REGION, tex:tex + REGION, :3] = col.clip(0, 255).astype(np.uint8)
    img[:REGION, tex:tex + REGION, 3] = 255
    glw = np.zeros((H, W, 4), np.uint8)
    glw[:base_tex[1].shape[0], :tex] = base_tex[1]
    g = glow_of(col) >= GLOW_CUT
    glw[:REGION, tex:tex + REGION, :3] = (col * g[..., None]).clip(0, 255).astype(np.uint8)
    glw[:REGION, tex:tex + REGION, 3] = g * 255
    G = to_geo(V)
    paint_halo(img, glw)
    by = {'halo_spin': halo_quads(W, H)}
    for i, (vs, ts) in enumerate(F):
        if lab[i] is None:
            continue
        p = V[vs]
        n = np.cross(p[1] - p[0], p[-1] - p[0]) if len(vs) == 3 else np.cross(p[2] - p[0], p[3] - p[1])
        n = n / (np.linalg.norm(n) or 1.0)
        n = (n[0], n[1], -n[2])
        P = G[vs]
        if len(vs) == 3:
            P, ts = np.vstack([P, P[2:3]]), ts + [ts[2]]
        elif len(vs) > 4:
            continue
        q = []
        for pt, t in zip(P, ts):
            u_, v_ = VT[t]
            q.append((*pt, (tex + u_ * REGION) / W, ((1 - v_) * REGION) / H))
        by.setdefault(lab[i], []).append((q, n))
    with gzip.open(out_mesh, 'wb', compresslevel=9) as f:
        f.write(struct.pack('>4sii', b'LCMS', 1, len(by)))
        for name, quads in by.items():
            nb = name.encode('utf-8')
            f.write(struct.pack('>H', len(nb)) + nb + struct.pack('>i', len(quads)))
            for q, n in quads:
                f.write(struct.pack('>3f', *n))
                for v in q:
                    f.write(struct.pack('>5f', *v))
    counts = {k: len(v) for k, v in by.items()}
    print('mesh quads by bone', counts, 'total', sum(counts.values()))
    return img, glw, W, H, by


def load_mesh(path):
    """書いた面のファイルを読む（確かめの絵用）。{骨: [(4 点の [x,y,z,u,v], 法線)]}"""
    out = {}
    with gzip.open(path, 'rb') as f:
        magic, ver, n = struct.unpack('>4sii', f.read(12))
        for _ in range(n):
            ln, = struct.unpack('>H', f.read(2))
            name = f.read(ln).decode('utf-8')
            cnt, = struct.unpack('>i', f.read(4))
            qs = []
            for _ in range(cnt):
                nrm = struct.unpack('>3f', f.read(12))
                q = [struct.unpack('>5f', f.read(20)) for _ in range(4)]
                qs.append((q, nrm))
            out[name] = qs
    return out


def label_preview(out):
    """骨分けの確かめ用（OBJ の面の番号ごとの骨の番号を npy に）"""
    V, VT, F = load_obj()
    lab = classify(V, F, VT)
    names = sorted({l for l in lab if l is not None})
    idx = np.array([names.index(l) if l is not None else 15 for l in lab])
    np.save(out, idx)
    print(dict(enumerate(names)))


if __name__ == '__main__':
    import sys
    label_preview(sys.argv[1])
