"""geo.json とテクスチャを、ゲームを起動せずに正面・横・斜め・後ろから見た絵にする（形と塗りの確かめ用。2026-10-20）。

    python tools/preview_geo.py lost_catastrophe [出力.png] [拡大] ['{"arm_r": [rotX, rotY, rotZ], ...}']（骨の回し。GeckoLib の値）

骨は休みの姿勢（骨の回転なし）。面ごとの陰はゲームと同じ 2 つの光で付け、面の裏も描く（2026-10-21）。左右はゲームと同じ（正面の絵ではモデルの右 -X が見る人の左）。箱ごとの回転は GeckoLib と同じ Rz(-z)・Ry(+y)・Rx(-x)。正射影で、面ごとにテクスチャを
アフィン変換して貼り、奥行きの記録で近い面だけを描く（抜いた所は描かない）
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries')
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])     # ゲームのエンティティの光（Lighting の DIFFUSE_LIGHT_0/1。正面 -Z を向けたとき）
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


def rot_matrix(r):
    x, y, z = [math.radians(a) for a in r]
    Rx = np.array([[1, 0, 0], [0, math.cos(-x), -math.sin(-x)], [0, math.sin(-x), math.cos(-x)]])
    Ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    Rz = np.array([[math.cos(-z), -math.sin(-z), 0], [math.sin(-z), math.cos(-z), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def faces_of(cube):
    (x0, y0, z0) = cube['origin']
    sx, sy, sz = cube['size']
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    # 面: (左上, 右上, 左下) のテクスチャの角に当たる点と、外向きの法線
    return {
        'north': ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0), (0, 0, -1)),
        'south': ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1), (0, 0, 1)),
        'east': ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0), (-1, 0, 0)),
        'west': ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1), (1, 0, 0)),
        'up': ((x1, y1, z1), (x0, y1, z1), (x1, y1, z0), (0, 1, 0)),
        'down': ((x1, y0, z0), (x0, y0, z0), (x1, y0, z1), (0, -1, 0)),
    }


def bone_mats(geo, pose):
    """骨ごとの変換（4x4）。pose は {骨: (rotX, rotY, rotZ)}（GeckoLib の setRotX など、ラジアン）。
    geo の中では Rz(-rotZ)・Ry(-rotY)・Rx(rotX) を pivot のまわりに掛ける（GeckoLib は X を返して描くため）"""
    by = {b['name']: b for b in geo['bones']}
    out = {}

    def mat(name):
        if name in out:
            return out[name]
        b = by[name]
        M = np.eye(4)
        if b.get('parent'):
            M = mat(b['parent']).copy()
        r = pose.get(name)
        if r:
            rx, ry, rz = r[:3]
            R = rot_matrix((-math.degrees(rx), -math.degrees(ry), math.degrees(rz)))
            if len(r) >= 6:                                         # 拡縮（GeckoLib の setScaleX など。回してから掛ける）
                R = R @ np.diag(r[3:6])
            p = np.array(b.get('pivot', [0, 0, 0]), float)
            T = np.eye(4)
            T[:3, :3] = R
            T[:3, 3] = p - R @ p
            M = M @ T
        out[name] = M
        return M
    for b in geo['bones']:
        mat(b['name'])
    return out


def load_mesh(ident):
    """OBJ の面のまま骨に載せた体（tools/lc_objmesh.py。2026-10-21）。無ければ None"""
    path = os.path.join(ASSETS, 'mesh', ident + '.bin')
    if not os.path.exists(path):
        return None
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import lc_objmesh
    return lc_objmesh.load_mesh(path)


def draw_mesh(mesh, mats, view, tex, scale, ox, oy, img, zbuf):
    """面（4 点。三角は最後の点が重なる）を 2 つの三角に分けて、テクスチャを点ごとに引いて描く"""
    T = np.asarray(tex, dtype=np.float32)
    th, tw = T.shape[:2]
    H, W = zbuf.shape
    for bone, quads in mesh.items():
        if bone not in mats:
            continue
        BM = mats[bone]
        for q, nrm in quads:
            P = np.array([v[:3] for v in q])
            UV = np.array([v[3:] for v in q])
            P = (view @ (BM[:3, :3] @ P.T + BM[:3, 3:4])).T
            nm = BM[:3, :3] @ np.array(nrm)
            light = min(1.0, 0.4 + 0.6 * (max(0.0, float(np.dot(nm, L0))) + max(0.0, float(np.dot(nm, L1)))))
            light = max(light, min(1.0, 0.4 + 0.6 * (max(0.0, float(np.dot(-nm, L0))) + max(0.0, float(np.dot(-nm, L1))))) * 0.8)
            sx = P[:, 0] * scale + ox
            sy = -P[:, 1] * scale + oy
            for a, b, c in ((0, 1, 2), (0, 2, 3)):
                xs, ys, zs = sx[[a, b, c]], sy[[a, b, c]], P[[a, b, c], 2]
                x0, x1 = max(int(xs.min()), 0), min(int(xs.max()) + 1, W - 1)
                y0, y1 = max(int(ys.min()), 0), min(int(ys.max()) + 1, H - 1)
                if x1 < x0 or y1 < y0:
                    continue
                d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
                if abs(d) < 1e-9:
                    continue
                gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
                l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
                l2 = 1 - l0 - l1
                m = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
                if not m.any():
                    continue
                z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
                sub = zbuf[y0:y1 + 1, x0:x1 + 1]
                m &= z < sub
                if not m.any():
                    continue
                uv = UV[[a, b, c]]
                u = l0 * uv[0, 0] + l1 * uv[1, 0] + l2 * uv[2, 0]
                v = l0 * uv[0, 1] + l1 * uv[1, 1] + l2 * uv[2, 1]
                col = T[np.clip((v * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)]
                m &= col[..., 3] > 127
                sub[m] = z[m]
                img[y0:y1 + 1, x0:x1 + 1][m] = (col[..., :3][m] * light).clip(0, 255).astype(np.uint8)


def render(geo, tex, yaw, scale, size, pose=None, mesh=None):
    mats = bone_mats(geo, pose or {})
    canvas = Image.new('RGBA', size, (24, 22, 34, 255))
    cy_ = math.radians(yaw)
    view = np.array([[math.cos(cy_), 0, math.sin(cy_)], [0, 1, 0], [-math.sin(cy_), 0, math.cos(cy_)]])
    polys = []
    tw, th = tex.size
    for b in geo['bones']:
        BM = mats[b['name']]
        for cube in b.get('cubes', []):
            R = rot_matrix(cube['rotation']) if 'rotation' in cube else np.eye(3)
            piv = np.array(cube.get('pivot', [0, 0, 0]), dtype=float)
            for f, (p0, p1, p2, nrm) in faces_of(cube).items():
                uv = cube['uv'][f]
                pts = [view @ (BM[:3, :3] @ (R @ (np.array(p, float) - piv) + piv) + BM[:3, 3]) for p in (p0, p1, p2)]
                nm = BM[:3, :3] @ (R @ np.array(nrm, float))
                nm = nm / (np.linalg.norm(nm) or 1.0)
                # ゲームの面ごとの陰（エンティティの 2 つの光。裏も描く: GeckoLib は面の裏を省かない）
                light = min(1.0, 0.4 + 0.6 * (max(0.0, float(np.dot(nm, L0))) + max(0.0, float(np.dot(nm, L1)))))
                polys.append((0, pts, uv, light))
    W, H = size
    ox, oy = W / 2, H * 0.94
    img = np.asarray(canvas, dtype=np.uint8)[..., :3].copy()
    zbuf = np.full((H, W), np.inf)                                  # 奥行きの記録（ゲームと同じく、近い面だけを描く。抜いた所は描かない）
    for depth, pts, uv, light in polys:
        (u, v), (w, h) = uv['uv'], uv['uv_size']
        if w <= 0 or h <= 0:
            continue
        s = [(p[0] * scale + ox, -p[1] * scale + oy) for p in pts]   # ゲームと同じ左右（geo の -X がモデルの右）
        A = np.array([[s[1][0] - s[0][0], s[2][0] - s[0][0]], [s[1][1] - s[0][1], s[2][1] - s[0][1]]])
        if abs(np.linalg.det(A)) < 1e-6:
            continue
        Ai = np.linalg.inv(A)
        # 画面 → テクスチャの (a, b)（0..1）。逆写像の係数
        xs = [q[0] for q in s] + [s[1][0] + s[2][0] - s[0][0]]
        ys = [q[1] for q in s] + [s[1][1] + s[2][1] - s[0][1]]
        bx0, by0, bx1, by1 = int(min(xs)) - 1, int(min(ys)) - 1, int(max(xs)) + 2, int(max(ys)) + 2
        bw, bh = bx1 - bx0, by1 - by0
        if bw <= 0 or bh <= 0 or bw > 4000 or bh > 4000:
            continue
        # 画面の位置から奥行き（正射影なので面の上で一次）
        Mz = np.array([[s[0][0], s[0][1], 1], [s[1][0], s[1][1], 1], [s[2][0], s[2][1], 1]])
        try:
            za, zb_, zc = np.linalg.solve(Mz, [pts[0][2], pts[1][2], pts[2][2]])
        except np.linalg.LinAlgError:
            continue
        crop = tex.crop((u, v, u + w, v + h))
        # 出力の (X, Y) → 入力 (a*w, b*h)
        c00, c01 = Ai[0, 0] * w, Ai[0, 1] * w
        c10, c11 = Ai[1, 0] * h, Ai[1, 1] * h
        dx, dy = bx0 - s[0][0], by0 - s[0][1]
        coeffs = (c00, c01, c00 * dx + c01 * dy, c10, c11, c10 * dx + c11 * dy)
        patch = crop.transform((bw, bh), Image.AFFINE, coeffs, resample=Image.NEAREST)   # ドット絵はゲームと同じく最近傍で
        mask = Image.new('L', (bw, bh), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).polygon([(x - bx0, y - by0) for x, y in zip(xs[:2] + [xs[3], xs[2]], ys[:2] + [ys[3], ys[2]])], fill=255)
        a = np.minimum(np.asarray(patch.split()[3]), np.asarray(mask))
        rgb = (np.asarray(patch, dtype=np.float32)[..., :3] * light).clip(0, 255).astype(np.uint8)
        x0c, y0c = max(bx0, 0), max(by0, 0)
        x1c, y1c = min(bx0 + bw, W), min(by0 + bh, H)
        if x1c <= x0c or y1c <= y0c:
            continue
        sub = (slice(y0c - by0, y1c - by0), slice(x0c - bx0, x1c - bx0))
        YY, XX = np.mgrid[y0c:y1c, x0c:x1c]
        zz = za * (XX + 0.5) + zb_ * (YY + 0.5) + zc
        win = (a[sub] > 127) & (zz < zbuf[y0c:y1c, x0c:x1c])
        zbuf[y0c:y1c, x0c:x1c][win] = zz[win]
        img[y0c:y1c, x0c:x1c][win] = rgb[sub][win]
    if mesh:
        draw_mesh(mesh, mats, view, tex, scale, ox, oy, img, zbuf)
    return Image.fromarray(img).convert('RGBA')


def main():
    ident = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'build', f'preview_{ident}.png')
    scale = float(sys.argv[3]) if len(sys.argv) > 3 else 18
    geo = json.load(open(os.path.join(ASSETS, 'geo', 'entity', ident + '.geo.json'), encoding='utf-8'))['minecraft:geometry'][0]
    tex = Image.open(os.path.join(ASSETS, 'textures', 'entity', ident + '.png')).convert('RGBA')
    tw, th = geo['description']['texture_width'], geo['description']['texture_height']
    if tex.size != (tw, th):
        tex = tex.resize((tw, th))
    size = (int(scale * 34), int(scale * 52))
    pose = json.loads(sys.argv[4]) if len(sys.argv) > 4 else {}
    mesh = load_mesh(ident)
    views = [render(geo, tex, yaw, scale, size, pose, mesh) for yaw in (0, 35, 90, 180)]
    sheet = Image.new('RGBA', (size[0] * len(views), size[1]))
    for i, v in enumerate(views):
        sheet.paste(v, (i * size[0], 0))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print('wrote', out)


if __name__ == '__main__':
    main()
