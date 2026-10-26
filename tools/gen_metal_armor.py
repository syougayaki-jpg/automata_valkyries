"""
プレイヤー用の固有金属の防具（アストライト・フォノニウム・セルリアス。2026-10-09）と上位合金の防具（2026-10-26）の見た目を作る。

    python tools/gen_metal_armor.py

出力: textures/item/<金属>_<部位>.png（16x16）と textures/models/armor/<金属>_layer_1.png / _layer_2.png（64x32）。
形と陰影はバニラの鉄の防具（.mcsrc/assets の iron_*）の明るさを使い、色は各金属のインゴットの色の段（暗い → 明るい）に置き換える。
明るい所の一部に金属ごとの光る差し色（アストライトは水色・フォノニウムは金・セルリアスは淡い緑）を入れる。
"""
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
VANILLA = os.path.join(ROOT, '.mcsrc', 'assets', 'minecraft', 'textures')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries', 'textures')

# 暗い → 明るいの色の段（インゴットの色から）と、差し色
METALS = {
    'astrite': (['#0d131d', '#1d2d44', '#2c4060', '#3d5a80', '#7ca7c9', '#b8d4e8'], '#6af0ff'),   # 暗い紺の鋼
    'phononium': (['#140005', '#370617', '#5a0a1a', '#9d0208', '#e63946', '#fff3b0'], '#ffd166'),
    'ceruleas': (['#06221f', '#0f4c4a', '#1f7a78', '#3fb0a8', '#8fe0d4', '#e8fff8'], '#c8f7dc'),  # 青緑（水の色）
}
# 上位合金 4 種（2026-10-26 ユーザー指定「他の合金のインゴットの防具も追加して」）。一段上の防具なので形はダイヤの防具から
ALLOYS = {
    'luminal_sterling': (['#0c1e28', '#1e3a4a', '#3f7f96', '#8cd6e8', '#c8f2f8', '#ffffff'], '#fff6a8'),   # 光る銀青（超伝導）
    'chrono_fortress': (['#0e0a16', '#1a1426', '#3a2e52', '#5a4a7a', '#8070a6', '#b8a8dc'], '#ff5a7a'),    # 暗い紫の重鋼
    'tachyon_accel': (['#160404', '#2a0a0a', '#7a2a14', '#a8401e', '#e07a2e', '#ffd27a'], '#7af0ff'),      # 赤橙（加速）
    'eclipse_biometal': (['#050805', '#101a12', '#24402a', '#3a6a44', '#5ea06a', '#9ce0a6'], '#e0a0ff'),   # 深緑（生体金属）
}
# ステラ・クロノダイム（2026-10-26。一番上の防具なので形はネザライトの防具から）
STELLA = {
    'stellachronodyme': (['#05050f', '#0a0a1e', '#24225a', '#3e3a7e', '#7a6ed0', '#f4f0ff'], '#ffe8a0'),   # 夜空の紫に星の金
}
PARTS = ['helmet', 'chestplate', 'leggings', 'boots']


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def ramp(colors, t):
    """t（0〜1）を色の段の間で補間する"""
    t = max(0.0, min(1.0, t)) * (len(colors) - 1)
    i = min(int(t), len(colors) - 2)
    f = t - i
    a, b = rgb(colors[i]), rgb(colors[i + 1])
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def recolor(src, colors, accent, accent_every):
    img = Image.open(src).convert('RGBA')
    px = img.load()
    w, h = img.size
    lums = [0.299 * r + 0.587 * g + 0.114 * b for (r, g, b, a) in (img.getpixel((x, y)) for y in range(img.height) for x in range(img.width)) if a > 0]
    lo, hi = (min(lums), max(lums)) if lums else (0, 255)
    out = Image.new('RGBA', (w, h))
    op = out.load()
    n = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            t = (0.299 * r + 0.587 * g + 0.114 * b - lo) / max(1.0, hi - lo)
            c = ramp(colors, t)
            if t > 0.78:  # 明るい所に差し色をところどころ
                n += 1
                if n % accent_every == 0:
                    c = rgb(accent)
            op[x, y] = c + (a,)
    return out


def main():
    os.makedirs(os.path.join(OUT, 'item'), exist_ok=True)
    os.makedirs(os.path.join(OUT, 'models', 'armor'), exist_ok=True)
    for table, base in ((METALS, 'iron'), (ALLOYS, 'diamond'), (STELLA, 'netherite')):
        for metal, (colors, accent) in table.items():
            for part in PARTS:
                recolor(os.path.join(VANILLA, 'item', f'{base}_{part}.png'), colors, accent, 5).save(os.path.join(OUT, 'item', f'{metal}_{part}.png'))
            for layer in (1, 2):
                recolor(os.path.join(VANILLA, 'models', 'armor', f'{base}_layer_{layer}.png'), colors, accent, 9).save(
                    os.path.join(OUT, 'models', 'armor', f'{metal}_layer_{layer}.png'))
            print(metal, 'done')


if __name__ == '__main__':
    main()
