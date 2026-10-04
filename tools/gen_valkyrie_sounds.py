"""
戦術兵姫の効果音を合成して .ogg に書き出す（2026-10-04、録音ではなく波形の計算で作る）。

    python tools/gen_valkyrie_sounds.py            # 全部作り直す
    python tools/gen_valkyrie_sounds.py boost      # 名前に boost を含むものだけ

出力: src/main/resources/assets/automata_valkyries/sounds/valkyrie/<名前>_<a|b|c>.ogg（モノラル 44.1kHz、Minecraft で位置から聞こえる）
sounds.json と字幕（lang）も合わせて書き直す。各音は 3 つの違い（a/b/c）があり、
  /playsound automata_valkyries:valkyrie.<名前>.a master @s   … その 1 つだけ（聞き比べ用）
  /playsound automata_valkyries:valkyrie.<名前> master @s     … 3 つから毎回ランダム（ゲームで使う）
音を変えるときは下の各関数の数値（周波数・長さ・減衰）を直して実行し直す。
必要なもの: pip install numpy soundfile
"""
import json
import os
import sys
import zlib

import numpy as np
import soundfile as sf

SR = 44100
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'automata_valkyries')
OUT = os.path.join(ASSETS, 'sounds', 'valkyrie')


# ---------------------------------------------------------------- 基本の部品

def secs(d):
    return np.arange(int(SR * d)) / SR


def ramp(a, b, d, curve=1.0):
    """a から b へ d 秒で変わる値（curve > 1 で後半に急、< 1 で前半に急）"""
    x = np.linspace(0, 1, int(SR * d)) ** curve
    return a + (b - a) * x


def osc(freq, kind='sine'):
    """周波数（定数か配列）の発振。配列なら長さ = 配列の長さ"""
    phase = 2 * np.pi * np.cumsum(freq) / SR
    if kind == 'sine':
        return np.sin(phase)
    frac = (phase / (2 * np.pi)) % 1.0
    if kind == 'saw':
        return 2 * frac - 1
    if kind == 'square':
        return np.where(frac < 0.5, 1.0, -1.0)
    if kind == 'tri':
        return 4 * np.abs(frac - 0.5) - 1
    raise ValueError(kind)


def tone(freq, d, kind='sine'):
    f = np.full(int(SR * d), float(freq)) if np.isscalar(freq) else freq
    return osc(f, kind)


def noise(d, rng):
    return rng.uniform(-1, 1, int(SR * d))


def lowpass(x, cutoff):
    """1 次のローパス（cutoff は定数か x と同じ長さの配列）。2 回かけると急になる"""
    c = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    a = 1.0 - np.exp(-2 * np.pi * np.clip(c, 1, SR / 2.2) / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def lp2(x, cutoff):
    return lowpass(lowpass(x, cutoff), cutoff)


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, low, high):
    return lp2(highpass(x, low), high)


def env(n, attack, decay, sustain=0.0, release=None):
    """アタック → 指数で減衰（sustain の高さまで）。長さは n サンプル"""
    t = np.arange(n) / SR
    e = np.where(t < attack, t / max(attack, 1e-4), sustain + (1 - sustain) * np.exp(-(t - attack) / max(decay, 1e-4)))
    if release:
        r = int(SR * release)
        e[-r:] *= np.linspace(1, 0, r)
    return e


def fade(x, inn=0.003, out=0.01):
    """ぷつっと鳴らないよう、頭と尻を少し絞る"""
    x = x.copy()
    a, b = int(SR * inn), int(SR * out)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def pad(x, d):
    """後ろに無音を足して長さ d 秒にそろえる（残響の尾のため）"""
    n = int(SR * d)
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])[:max(n, len(x))]


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def at(x, start, total):
    """x を start 秒から置いた total 秒の列"""
    out = np.zeros(int(SR * total))
    s = int(SR * start)
    e = min(len(out), s + len(x))
    out[s:e] = x[:e - s]
    return out


def reverb(x, rng, size=0.35, wet=0.18):
    """指数で減る雑音を畳み込んだ簡単な残響（機械の中・広い所の響き）"""
    n = int(SR * size)
    ir = rng.uniform(-1, 1, n) * np.exp(-np.arange(n) / (SR * size / 5))
    ir = lowpass(ir, 6000)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    total = len(x) + n
    y = np.fft.irfft(np.fft.rfft(x, total) * np.fft.rfft(ir, total), total)
    return np.concatenate([x, np.zeros(n)]) * (1 - wet) + y * wet


def metal(d, partials, decay, rng, detune=0.0):
    """金属の澄んだ響き（倍音が整数倍でない部分音の和。高い部分音ほど早く消える）"""
    t = secs(d)
    out = np.zeros(len(t))
    for i, p in enumerate(partials):
        f = p * (1 + rng.uniform(-detune, detune))
        out += np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t / (decay / (1 + 0.5 * i))) / (1 + i * 0.6)
    return out


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m < 1e-9 else x * (peak / m)


# ---------------------------------------------------------------- 各音（v = 0/1/2 で a/b/c の違い）

def boost(v, rng):
    """ブーストダッシュ: 鋭い吸気 → 噴射の風切り（上へ掃く雑音）＋低い押し出し"""
    d = [0.65, 0.55, 0.8][v]
    top = [4200, 5200, 3600][v]
    n = noise(d, rng)
    jet = bandpass(n, ramp(500, 1600, d), ramp(1500, top, d, 0.5)) * env(len(n), 0.015, [0.22, 0.16, 0.3][v])
    thump = tone(ramp([70, 85, 60][v], 35, 0.25), 0.25) * env(int(SR * 0.25), 0.003, 0.07)
    whine = tone(ramp(600, [1400, 1800, 1100][v], d), d, 'saw')
    whine = lp2(whine, 3000) * env(len(whine), 0.02, 0.12) * 0.18
    return mix(jet * 1.2, thump * 0.9, whine)


def thruster(v, rng):
    """ホバーの噴射（1 秒ほど続く低い轟きと羽ばたくような揺らぎ）"""
    d = [1.0, 1.2, 0.9][v]
    n = noise(d, rng)
    rumble = lp2(n, [420, 520, 360][v]) * 2.5
    hiss = bandpass(n, 1500, [5000, 6500, 4500][v]) * 0.35
    flutter = 1 + 0.35 * np.sin(2 * np.pi * [28, 34, 22][v] * secs(d))
    e = np.minimum(1, secs(d) / 0.08) * np.minimum(1, (d - secs(d)) / 0.25)
    return (rumble + hiss) * flutter * e


def land(v, rng):
    """着地の噴射（低い衝撃と、下へ抜ける噴射の息）"""
    d = 0.6
    thump = tone(ramp([55, 65, 48][v], 30, 0.3), 0.3) * env(int(SR * 0.3), 0.002, 0.09)
    n = noise(d, rng)
    puff = bandpass(n, 300, ramp([3000, 3800, 2500][v], 600, d)) * env(len(n), 0.005, 0.14)
    clank = metal(0.2, [900, 1430, 2210], 0.05, rng) * 0.25
    return mix(thump * 1.2, puff, clank)


def dodge(v, rng):
    """回避のステップ（短い風切りが下へ抜け、足元のスラスターがぱしっと鳴る）"""
    d = [0.28, 0.24, 0.32][v]
    n = noise(d, rng)
    whoosh = bandpass(n, ramp(1800, 500, d), ramp(6000, 1500, d)) * np.sin(np.pi * np.linspace(0, 1, len(n))) ** [1.5, 2.0, 1.2][v]
    snap = bandpass(noise(0.03, rng), 2000, 9000) * env(int(SR * 0.03), 0.001, 0.008)
    return mix(whoosh, snap * 0.8)


def servo(v, rng, d):
    """関節のサーボ（倍音の多い唸りが上がって落ち着く。歯車の細かい刻み）"""
    t = secs(d)
    base = [420, 520, 360][v]
    f = base + (base * 1.1) * np.sin(np.pi * np.minimum(1, t / d)) ** 0.7
    f *= 1 + 0.01 * np.sin(2 * np.pi * 9 * t)
    whine = lp2(tone(f, d, 'saw') + 0.5 * tone(f * 2.01, d, 'square'), 2500)
    ticks = np.zeros(len(t))
    step = int(SR / [60, 75, 48][v])
    ticks[::step] = 1
    ticks = bandpass(ticks * rng.uniform(0.5, 1, len(t)), 2500, 8000) * 6
    e = np.minimum(1, t / 0.02) * np.minimum(1, (d - t) / 0.06)
    return (whine * 0.6 + ticks) * e


def servo_short(v, rng):
    return servo(v, rng, [0.3, 0.25, 0.35][v])


def servo_long(v, rng):
    return servo(v, rng, [0.8, 0.7, 0.95][v])


def armor_lock(v, rng):
    """装甲が嵌まる音（硬い 2 回の留め金と低い衝撃、空気の抜ける音）"""
    total = 0.7
    click = lambda: metal(0.12, [[1800, 2650, 3900], [2100, 3150, 4600], [1500, 2300, 3500]][v], 0.03, rng, 0.02)
    thunk = tone(ramp(120, 60, 0.15), 0.15) * env(int(SR * 0.15), 0.002, 0.04)
    hiss = bandpass(noise(0.35, rng), 2500, 9000) * env(int(SR * 0.35), 0.01, 0.1) * 0.35
    gap = [0.09, 0.07, 0.12][v]
    return mix(at(click(), 0, total), at(thunk, 0, total) * 0.8, at(click() * 0.8, gap, total), at(hiss, gap + 0.05, total))


def core_startup(v, rng):
    """コアの起動（上がっていく和音の分散と、満ちていく唸り、きらめき）"""
    d = [1.6, 1.4, 1.9][v]
    root = [261.6, 293.7, 246.9][v]
    notes = [1, 1.26, 1.5, 2][:] if v != 2 else [1, 1.19, 1.5, 2, 2.52]
    out = np.zeros(int(SR * d))
    for i, m in enumerate(notes):
        s = 0.18 * i
        nd = d - s
        x = (tone(root * m, nd) + 0.3 * tone(root * m * 2, nd, 'tri')) * env(int(SR * nd), 0.02, 0.6, 0.35, 0.3)
        out += at(x, s, d) * 0.35
    hum = tone(ramp(55, 110, d), d, 'saw')
    hum = lp2(hum, ramp(200, 1500, d)) * np.linspace(0, 1, len(hum)) ** 1.5 * 0.5
    sparkle = highpass(noise(d, rng), 6000) * (rng.uniform(0, 1, int(SR * d)) > 0.9985) * 8
    sparkle = lowpass(sparkle, 12000)
    return fade(mix(out, hum, sparkle * np.linspace(0.2, 1, int(SR * d))), out=0.3)


def charge(v, rng):
    """チャージ（音程が上がり、震えが速くなる）"""
    d = [1.5, 1.2, 2.0][v]
    t = secs(d)
    f = ramp([200, 260, 160][v], [1200, 1500, 1000][v], d, 1.6)
    trem = 1 + 0.5 * np.sin(2 * np.pi * np.cumsum(ramp(4, [28, 34, 22][v], d)) / SR)
    x = (tone(f, d) + 0.35 * lp2(tone(f * 0.5, d, 'saw'), 2000)) * trem
    return fade(x * np.minimum(1, t / 0.1), out=0.05)


def beam_fire(v, rng):
    """光線の発射（高い所から急に落ちる電気の一閃と、残る唸り）"""
    d = [0.8, 0.65, 1.0][v]
    zap = tone(ramp([3200, 4000, 2600][v], [500, 700, 380][v], 0.25, 0.4), 0.25, 'saw')
    zap = lp2(zap, 6000) * env(len(zap), 0.002, 0.08)
    n = noise(d, rng)
    blast = bandpass(n, 400, ramp(7000, 1200, d)) * env(len(n), 0.002, 0.12)
    buzz = tone(ramp(180, 120, d), d, 'square') * (1 + 0.4 * np.sin(2 * np.pi * 60 * secs(d)))
    buzz = lp2(buzz, 1800) * env(len(buzz), 0.01, [0.25, 0.18, 0.35][v]) * 0.3
    return mix(zap, blast * 0.8, buzz)


def blade_swing(v, rng):
    """光の刃の振り（唸りの音程が振りに合わせて上がって戻る＋風切り）"""
    d = [0.35, 0.3, 0.45][v]
    t = secs(d)
    sweep = np.sin(np.pi * t / d)
    f = [210, 250, 180][v] * (1 + 0.55 * sweep)
    hum = (tone(f, d, 'saw') * 0.6 + tone(f * 1.5, d) * 0.4)
    hum = lp2(hum, 1200 + 2500 * sweep) * sweep ** 0.8
    air = bandpass(noise(d, rng), 900, 5000) * sweep ** 2 * 0.6
    return mix(hum, air)


def discharge(v, rng):
    """放電（ばちばちと不規則に弾ける音）"""
    d = [0.5, 0.4, 0.7][v]
    n = int(SR * d)
    clicks = np.zeros(n)
    k = [70, 90, 55][v]
    for _ in range(k):
        i = rng.integers(0, n - 400)
        clicks[i:i + rng.integers(20, 300)] += rng.uniform(-1, 1) * rng.uniform(0.3, 1)
    crack = highpass(clicks + 0.25 * noise(d, rng), 1200) * env(n, 0.002, d / 3)
    hum = tone(ramp(120, 90, d), d, 'saw') * env(n, 0.005, d / 4) * 0.2
    return mix(crack, lp2(hum, 900))


def shield_up(v, rng):
    """障壁の展開（下から澄んだ和音がせり上がり、きらめく）"""
    d = [0.7, 0.6, 0.9][v]
    t = secs(d)
    out = np.zeros(len(t))
    for m in ([1, 1.5, 2.0], [1, 1.26, 1.5, 2.0], [1, 1.5, 2.25])[v]:
        out += tone(ramp(220 * m, 440 * m, d, 0.4), d) / 3
    out *= env(len(out), 0.05, 0.3, 0.2, 0.15)
    shimmer = bandpass(noise(d, rng), 5000, 10000) * np.sin(np.pi * t / d) * 0.25
    return mix(lp2(out, ramp(400, 5000, d)), shimmer)


def shield_hit(v, rng):
    """障壁に当たる（短い衝撃と、膜が震える澄んだ響き）"""
    d = 0.5
    imp = bandpass(noise(0.05, rng), 300, 6000) * env(int(SR * 0.05), 0.001, 0.012)
    ring = metal(d, [[620, 990, 1460, 2210], [740, 1180, 1750, 2600], [520, 830, 1240, 1900]][v], 0.12, rng, 0.01)
    wob = 1 + 0.3 * np.sin(2 * np.pi * 14 * secs(d))
    return mix(imp * 1.2, ring * wob * 0.7)


def ui_beep(v, rng):
    """画面の操作音（短い電子音）"""
    f = [1800, 2200, 1500][v]
    x = tone(f, 0.07, ['sine', 'tri', 'sine'][v]) * env(int(SR * 0.07), 0.002, 0.025)
    return x


def ui_confirm(v, rng):
    """換装の完了（上がる 2 音）"""
    a, b = [(1320, 1760), (1175, 1568), (1480, 1976)][v]
    k = ['square', 'tri', 'sine'][v]
    n1 = lp2(tone(a, 0.12, k), 5000) * env(int(SR * 0.12), 0.003, 0.05)
    n2 = lp2(tone(b, 0.25, k), 5000) * env(int(SR * 0.25), 0.003, 0.1)
    return mix(at(n1, 0, 0.4), at(n2, 0.1, 0.4))


def low_power(v, rng):
    """電力不足の警告（下がる 2 音を 2 回）"""
    a, b = [(880, 660), (988, 740), (784, 587)][v]
    beep = lambda f: lp2(tone(f, 0.14, 'square'), 3000) * env(int(SR * 0.14), 0.004, 0.08)
    total = 1.0
    return mix(at(beep(a), 0, total), at(beep(b), 0.17, total), at(beep(a), 0.5, total), at(beep(b), 0.67, total)) * 0.8


def shutdown(v, rng):
    """機能停止（唸りが落ちていき、こもって消え、最後にかちり）"""
    d = [1.5, 1.2, 1.9][v]
    f = ramp([300, 360, 260][v], 35, d, 0.6)
    hum = tone(f, d, 'saw') + 0.4 * tone(f * 1.5, d)
    hum = lp2(hum, ramp(3000, 150, d, 0.7)) * np.linspace(1, 0, len(hum)) ** 0.8
    click = metal(0.08, [1200, 1900], 0.02, rng) * 0.4
    return mix(hum, at(click, d - 0.05, d + 0.1))


def emote_happy(v, rng):
    """喜び（速く上がる 3 音のさえずり）"""
    notes = [(1047, 1319, 1568), (1175, 1480, 1760), (988, 1245, 1661)][v]
    total = 0.32
    out = np.zeros(int(SR * total))
    for i, f in enumerate(notes):
        x = tone(ramp(f * 0.97, f * 1.03, 0.08), 0.08) * env(int(SR * 0.08), 0.003, 0.03)
        out += at(x, i * 0.07, total)
    return out


def emote_sad(v, rng):
    """しょんぼり（ゆっくり下がる 2 音）"""
    a, b = [(660, 494), (587, 440), (740, 554)][v]
    n1 = tone(ramp(a, a * 0.97, 0.25), 0.25, 'tri') * env(int(SR * 0.25), 0.02, 0.15)
    n2 = tone(ramp(b, b * 0.9, 0.4), 0.4, 'tri') * env(int(SR * 0.4), 0.02, 0.25)
    return lp2(mix(at(n1, 0, 0.7), at(n2, 0.22, 0.7)), 3000)


def emote_surprise(v, rng):
    """驚き（ぴょんと跳ね上がる短い音）"""
    d = [0.22, 0.18, 0.26][v]
    x = tone(ramp([600, 700, 520][v], [2000, 2400, 1800][v], d, 0.5), d) * env(int(SR * d), 0.003, d / 3)
    return x


def wake(v, rng):
    """スリープからの復帰（やわらかく満ちて、2 回の電子音）"""
    d = 1.0
    swell = tone(ramp(160, 320, 0.6), 0.6) * np.sin(np.pi * np.linspace(0, 1, int(SR * 0.6))) ** 2
    swell = lp2(swell + 0.3 * tone(ramp(320, 640, 0.6), 0.6, 'tri'), 2500) * 0.6
    f1, f2 = [(1320, 1760), (1175, 1568), (1480, 1976)][v]
    blip = lambda f: tone(f, 0.07) * env(int(SR * 0.07), 0.002, 0.03)
    return mix(at(swell, 0, d), at(blip(f1), 0.62, d) * 0.6, at(blip(f2), 0.74, d) * 0.6)


# 名前 → (関数, 音量（最大振幅）, 残響の量, 英語の字幕, 日本語の字幕)
SOUNDS = {
    'boost': (boost, 0.9, 0.15, 'Valkyrie boosts', '兵姫がブーストする'),
    'thruster': (thruster, 0.8, 0.1, 'Valkyrie thrusters roar', '兵姫のスラスターが噴く'),
    'land': (land, 0.9, 0.12, 'Valkyrie lands', '兵姫が着地する'),
    'dodge': (dodge, 0.85, 0.1, 'Valkyrie dodges', '兵姫が回避する'),
    'servo_short': (servo_short, 0.5, 0.08, 'Servos whir', 'サーボが動く'),
    'servo_long': (servo_long, 0.5, 0.08, 'Servos whir', 'サーボが動く'),
    'armor_lock': (armor_lock, 0.85, 0.15, 'Armor locks into place', '装甲が嵌まる'),
    'core_startup': (core_startup, 0.8, 0.25, 'Luminal core starts up', 'ルミナルコアが起動する'),
    'charge': (charge, 0.75, 0.15, 'Valkyrie charges', '兵姫がチャージする'),
    'beam_fire': (beam_fire, 0.9, 0.2, 'Valkyrie fires a beam', '兵姫が光線を撃つ'),
    'blade_swing': (blade_swing, 0.8, 0.12, 'Energy blade swings', '光の刃が振られる'),
    'discharge': (discharge, 0.85, 0.12, 'Electricity crackles', '放電する'),
    'shield_up': (shield_up, 0.75, 0.25, 'Barrier deploys', '障壁が展開する'),
    'shield_hit': (shield_hit, 0.85, 0.2, 'Barrier is struck', '障壁に当たる'),
    'ui_beep': (ui_beep, 0.45, 0.0, 'Interface beeps', '端末が鳴る'),
    'ui_confirm': (ui_confirm, 0.5, 0.1, 'Loadout confirmed', '換装が完了する'),
    'low_power': (low_power, 0.6, 0.1, 'Low power warning', '電力不足の警告'),
    'shutdown': (shutdown, 0.8, 0.2, 'Valkyrie shuts down', '兵姫が機能停止する'),
    'emote_happy': (emote_happy, 0.5, 0.12, 'Valkyrie chirps happily', '兵姫が喜ぶ'),
    'emote_sad': (emote_sad, 0.5, 0.15, 'Valkyrie droops', '兵姫がしょんぼりする'),
    'emote_surprise': (emote_surprise, 0.5, 0.1, 'Valkyrie is startled', '兵姫が驚く'),
    'wake': (wake, 0.6, 0.2, 'Valkyrie wakes up', '兵姫が起動する'),
}
VARIANTS = 'abc'


def write_json(path, update):
    data = {}
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            data = json.load(f)
    update(data)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


def set_lang(path, entries):
    """lang の行だけを書き換える・足す（ファイルの他の行の並び・書式はそのまま）"""
    with open(path, encoding='utf-8', newline='') as f:
        text = f.read()
    nl = '\r\n' if '\r\n' in text else '\n'
    lines = text.split(nl)
    for key, value in entries.items():
        line = '  ' + json.dumps(key, ensure_ascii=False) + ': ' + json.dumps(value, ensure_ascii=False)
        hit = [i for i, l in enumerate(lines) if l.strip().startswith(json.dumps(key, ensure_ascii=False) + ':')]
        if hit:
            i = hit[0]
            lines[i] = line + (',' if lines[i].rstrip().endswith(',') else '')
        else:
            end = max(i for i, l in enumerate(lines) if l.strip() == '}')
            last = max(i for i in range(end) if lines[i].strip())
            if not lines[last].rstrip().endswith(','):
                lines[last] = lines[last].rstrip() + ','
            lines.insert(last + 1, line)
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(nl.join(lines))
    json.loads(nl.join(lines))  # 壊れていないか確かめる


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    os.makedirs(OUT, exist_ok=True)
    for name, (fn, peak, wet, _, _) in SOUNDS.items():
        if only and only not in name:
            continue
        for v, tag in enumerate(VARIANTS):
            rng = np.random.default_rng(zlib.crc32(f'{name}:{v}'.encode()))
            x = fn(v, rng)
            if wet > 0:
                x = reverb(x, rng, 0.3, wet)
            x = fade(norm(x, peak))
            sf.write(os.path.join(OUT, f'{name}_{tag}.ogg'), x.astype(np.float32), SR, format='OGG', subtype='VORBIS')
        print('wrote', name)

    def sounds(data):
        for key in [k for k in data if k.startswith('valkyrie.')]:
            del data[key]
        for name in SOUNDS:
            sub = f'subtitles.automata_valkyries.valkyrie.{name}'
            data[f'valkyrie.{name}'] = {'subtitle': sub, 'sounds': [f'automata_valkyries:valkyrie/{name}_{t}' for t in VARIANTS]}
            for t in VARIANTS:
                data[f'valkyrie.{name}.{t}'] = {'subtitle': sub, 'sounds': [f'automata_valkyries:valkyrie/{name}_{t}']}
    write_json(os.path.join(ASSETS, 'sounds.json'), sounds)

    for lang, idx in (('en_us', 3), ('ja_jp', 4)):
        set_lang(os.path.join(ASSETS, 'lang', f'{lang}.json'),
                 {f'subtitles.automata_valkyries.valkyrie.{name}': entry[idx] for name, entry in SOUNDS.items()})
    print('sounds.json and subtitles updated')


if __name__ == '__main__':
    main()
