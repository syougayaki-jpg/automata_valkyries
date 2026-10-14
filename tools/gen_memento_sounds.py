"""
メメントモリの演出の効果音を合成して .ogg に書き出す（2026-10-14 ユーザー指定の演出「音」。録音ではなく波形の計算で作る）。
鈴・ガラス・電子音のような幻想的な音。部品は gen_valkyrie_sounds.py のものを使う。

    python tools/gen_memento_sounds.py          # 全部作り直す
    python tools/gen_memento_sounds.py moon     # 名前に moon を含むものだけ

出力: src/main/resources/assets/automata_valkyries/sounds/memento/<名前>.ogg（モノラル 44.1kHz）と sounds.json の memento.<名前>、字幕（lang）。
  /playsound automata_valkyries:memento.<名前> master @s で聞ける。Java は MementoMoriEntity.sfx("<名前>")
必要なもの: pip install numpy soundfile
"""
import os
import sys
import zlib

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_valkyrie_sounds import (SR, ASSETS, secs, ramp, tone, noise, highpass, lp2, env, fade, mix, at, reverb, metal, norm,
                                 write_json, set_lang)

OUT = os.path.join(ASSETS, 'sounds', 'memento')


def ping(f, d, decay, rng):
    """ガラスの小さな鈴（澄んだ高い音。少しずれた倍音）"""
    return metal(d, [f, f * 2.76, f * 5.4], decay, rng, 0.002)


def sparkles(d, rng, n, lo, hi, start=0.0, end=None, curve=1.0, up=False):
    """きらめき: d 秒の中に n 個の小さな鈴（start〜end に、curve > 1 で後ろほど密に）。up なら後ろほど高い"""
    end = d if end is None else end
    out = np.zeros(int(SR * d))
    for k in range(n):
        u = rng.uniform(0, 1) ** (1.0 / curve)
        t0 = start + (end - start) * u
        f = (lo + (hi - lo) * u) * rng.uniform(0.9, 1.1) if up else rng.uniform(lo, hi)
        p = ping(f, 0.5, rng.uniform(0.05, 0.18), rng) * rng.uniform(0.3, 1.0)
        out += at(p, t0, d)
    return out


def airy(d, rng, low=1500, high=7000):
    """空気の流れるような淡い雑音"""
    return lp2(highpass(noise(d, rng), low), high)


def bell(f, d, decay, rng):
    """大きな鐘（低い基音と、鐘らしい半端な倍音）"""
    return metal(d, [f, f * 2.0, f * 2.4, f * 3.0, f * 4.2, f * 5.4, f * 6.8], decay, rng, 0.001)


def gather(rng):
    """登場: 光の蝶が集まる（だんだん密になって上がっていくきらめきと、満ちていく空気）"""
    d = 3.0
    sp = sparkles(d, rng, 90, 1400, 4200, 0.0, 2.6, curve=2.0, up=True)
    air = airy(d, rng) * np.sin(np.pi * np.linspace(0, 1, int(SR * d))) ** 2 * 0.25
    pad_ = tone(ramp(220, 440, d), d) * np.linspace(0, 1, int(SR * d)) ** 2 * 0.15
    return mix(sp, air, pad_)


def form(rng):
    """登場: 体が形になる（澄んだ和音の花開き）"""
    d = 2.0
    chord = [554.4, 659.3, 830.6, 987.8, 1318.5]
    out = np.zeros(int(SR * d))
    for k, f in enumerate(chord):
        out += at(metal(d, [f, f * 2.0, f * 3.01], 0.9, rng, 0.001), k * 0.05, d) * (1.0 - k * 0.1)
    return mix(out, sparkles(d, rng, 25, 2500, 5000, 0.0, 0.6) * 0.5)


def charge(rng):
    """昇華: 胸の核に光が集まる（上がっていくうなりと、速くなるきらめき）"""
    d = 3.0
    n = int(SR * d)
    f = ramp(110, 880, d, 1.6)
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(ramp(4, 18, d)) / SR)
    hum = (tone(f, d) + 0.4 * tone(f * 1.5, d) + 0.25 * tone(f * 2.01, d, 'tri')) * trem * np.linspace(0.2, 1, n) * 0.4
    return mix(hum, sparkles(d, rng, 70, 1800, 5200, 0.2, 3.0, curve=2.5, up=True) * 0.7)


def ascend(rng):
    """昇華: 白い閃光で光体になる（大きな鐘と満ちる光、降るきらめき）"""
    d = 4.0
    b = bell(220, d, 2.2, rng) * 0.8 + bell(330, d, 1.8, rng) * 0.5
    swell = airy(d, rng, 800, 9000) * env(int(SR * d), 0.02, 0.9) * 0.6
    choir = sum(tone(f, d) for f in (440, 554.4, 659.3, 880)) * env(int(SR * d), 0.3, 1.6) * 0.18
    return mix(b, swell, choir, sparkles(d, rng, 80, 1500, 6000, 0.05, 3.0, curve=0.6))


def cast(rng):
    """技の構え: 魔法陣が開く（上がる 4 つのガラスの音）"""
    d = 1.2
    notes = [1046.5, 1318.5, 1568.0, 2093.0]
    out = np.zeros(int(SR * d))
    for k, f in enumerate(notes):
        out += at(ping(f, 0.9, 0.35, rng), k * 0.07, d)
    return mix(out, airy(d, rng, 3000, 9000) * env(int(SR * d), 0.01, 0.25) * 0.15)


def bit(rng):
    """蝶ビットが飛び立つ（羽ばたきの震えと、下がる電子音）"""
    d = 0.35
    n = int(SR * d)
    flutter = airy(d, rng, 800, 4000) * (0.5 + 0.5 * np.sin(2 * np.pi * 38 * secs(d))) * env(n, 0.005, 0.1)
    zap = tone(ramp(2400, 900, d, 0.5), d) * env(n, 0.002, 0.06) * 0.5
    return mix(flutter, zap)


def shot(rng):
    """蝶弾: 小さな水晶の音"""
    d = 0.3
    f = rng.uniform(2200, 2800)
    return mix(ping(f, d, 0.08, rng), tone(ramp(f * 1.5, f, d), d) * env(int(SR * d), 0.001, 0.03) * 0.3)


def cage_lock(rng):
    """蝶の檻が閉じる（ガラスの鈴の群れが一度に鳴る）"""
    d = 1.2
    out = np.zeros(int(SR * d))
    for f in (880, 1174.7, 1396.9, 1760, 2349.3):
        out += at(ping(f, 1.0, 0.45, rng), rng.uniform(0, 0.03), d)
    return mix(out, tone(ramp(300, 150, d), d) * env(int(SR * d), 0.005, 0.3) * 0.3)


def cage_release(rng):
    """一斉突撃の合図（吸い込む風と、下がるガラスの滑り）"""
    d = 1.0
    n = int(SR * d)
    whoosh = airy(d, rng, 500, 6000) * np.concatenate([np.linspace(0, 1, n // 3) ** 2, np.exp(-np.linspace(0, 5, n - n // 3))])
    gliss = tone(ramp(3200, 700, d, 0.6), d) * env(n, 0.25, 0.25) * 0.4
    return mix(whoosh, gliss)


def moon_bell(rng):
    """黒死の月の数え（深い鐘）"""
    d = 2.5
    return mix(bell(110, d, 1.8, rng), bell(165, d, 1.2, rng) * 0.4, sparkles(d, rng, 12, 2000, 4000, 0.0, 0.4) * 0.3)


def moon_fall(rng):
    """黒死の月が納める（低い響きの落ちと、降る光のきらめき）"""
    d = 3.5
    n = int(SR * d)
    boom = tone(ramp(70, 28, d, 0.4), d) * env(n, 0.005, 0.8) * 1.2
    crack = airy(d, rng, 200, 5000) * env(n, 0.002, 0.25) * 0.8
    b = bell(146.8, d, 1.6, rng) * 0.6
    fall = sparkles(d, rng, 90, 1200, 5500, 0.05, 2.8, curve=0.5)
    return mix(boom, crack, b, fall)


def shatter(rng):
    """光輪が割れる（ガラスの砕ける高い音の群れ）"""
    d = 1.4
    n = int(SR * d)
    hit = airy(d, rng, 2500, 12000) * env(n, 0.001, 0.08)
    shards = sparkles(d, rng, 60, 2500, 7000, 0.0, 0.5, curve=0.4)
    return mix(hit, shards)


def scatter(rng):
    """撃破・退場: 光の蝶になってほどけて舞い上がる（上がって消えていくきらめきと、淡い和音）"""
    d = 4.5
    n = int(SR * d)
    sp = sparkles(d, rng, 140, 1200, 6500, 0.0, 3.8, curve=0.8, up=True)
    chord = sum(tone(f, d) for f in (329.6, 415.3, 493.9, 659.3)) * env(n, 0.6, 1.8) * 0.12
    air = airy(d, rng) * np.sin(np.pi * np.linspace(0, 1, n)) * 0.2
    return mix(sp, chord, air)


# 名前 → (関数, 音量（最大振幅）, 残響の長さ, 残響の量, 英語の字幕, 日本語の字幕)
SOUNDS = {
    'gather': (gather, 0.8, 1.0, 0.3, 'Butterflies of light gather', '光の蝶が集まる'),
    'form': (form, 0.8, 1.2, 0.3, 'Memento Mori takes form', 'メメントモリが形になる'),
    'charge': (charge, 0.8, 0.8, 0.25, 'Light gathers into a core', '核に光が集まる'),
    'ascend': (ascend, 0.95, 1.5, 0.35, 'Memento Mori ascends into light', 'メメントモリが光体へ昇華する'),
    'cast': (cast, 0.7, 0.8, 0.3, 'A magic circle opens', '魔法陣が開く'),
    'bit': (bit, 0.6, 0.3, 0.15, 'A butterfly bit takes flight', '蝶ビットが飛び立つ'),
    'shot': (shot, 0.45, 0.3, 0.15, 'Butterfly bullet chimes', '蝶弾が鳴る'),
    'cage_lock': (cage_lock, 0.8, 0.8, 0.3, 'Butterfly cage closes', '蝶の檻が閉じる'),
    'cage_release': (cage_release, 0.85, 0.5, 0.2, 'Butterflies charge', '蝶が一斉に突っ込む'),
    'moon_bell': (moon_bell, 0.9, 1.5, 0.35, 'The death moon tolls', '黒死の月が鳴る'),
    'moon_fall': (moon_fall, 1.0, 1.5, 0.3, 'The death moon descends', '黒死の月が納める'),
    'shatter': (shatter, 0.8, 0.8, 0.25, 'The halo shatters', '光輪が割れる'),
    'scatter': (scatter, 0.85, 1.5, 0.35, 'Memento Mori scatters into butterflies', 'メメントモリが光の蝶になって散る'),
}


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    os.makedirs(OUT, exist_ok=True)
    for name, (fn, peak, size, wet, _, _) in SOUNDS.items():
        if only and only not in name:
            continue
        rng = np.random.default_rng(zlib.crc32(f'memento:{name}'.encode()))
        x = fn(rng)
        if wet > 0:
            x = reverb(x, rng, size, wet)
        x = fade(norm(x, peak))
        sf.write(os.path.join(OUT, f'{name}.ogg'), x.astype(np.float32), SR, format='OGG', subtype='VORBIS')
        print('wrote', name)

    def sounds(data):
        for key in [k for k in data if k.startswith('memento.')]:
            del data[key]
        for name in SOUNDS:
            data[f'memento.{name}'] = {'subtitle': f'subtitles.automata_valkyries.memento.{name}', 'sounds': [f'automata_valkyries:memento/{name}']}
    write_json(os.path.join(ASSETS, 'sounds.json'), sounds)
    for lang, idx in (('en_us', 4), ('ja_jp', 5)):
        set_lang(os.path.join(ASSETS, 'lang', f'{lang}.json'),
                 {f'subtitles.automata_valkyries.memento.{name}': entry[idx] for name, entry in SOUNDS.items()})
    print('sounds.json and subtitles updated')


if __name__ == '__main__':
    main()
