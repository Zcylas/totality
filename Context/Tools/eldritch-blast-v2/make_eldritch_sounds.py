#!/usr/bin/env python3
"""Eldritch Blast V2 — original Totality sound design (custom variant).

Synthesises the spell's two sounds from scratch (no recorded or extracted material), deterministic per seed:

  cast   — played where the bolt is first seen: a 75 ms in-drawn swell (anticipation), the release crack, a sub thump,
           a short force punch, an inharmonic descending "eldritch" tone (ring-modulated), a high zing, a decaying
           crackle and a faint formant "whisper" (the patron), in a small room.
  impact — played at the hit point: a bigger crack and thump, a shattering force burst, a spark crackle tail and a
           short downward zing.

Layer structure follows the reference analysis in TOTALITY_ELDRITCH_BLAST_V2_IMPLEMENTATION_REPORT.md §4 (release
burst, then descending tonal chirps, then crackle). Output is mono 44.1 kHz (Minecraft only attenuates and pans mono
sounds), peak-normalised to -1 dBFS; three variants per sound (Minecraft picks one at random each time).

Usage:  python3 make_eldritch_sounds.py <out_dir> [--ogg]
        (WAVs always; --ogg also encodes <name>.ogg with ffmpeg/libvorbis q6.)
Requires only numpy (+ ffmpeg for --ogg).
"""
import math
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

SR = 44100


# ── primitives ──────────────────────────────────────────────────────────────

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def exp_sweep(f0, f1, seconds, n):
    """Frequency per sample: exponential glide f0 -> f1 over `seconds`, then held."""
    t = np.arange(n) / SR
    k = np.clip(t / seconds, 0.0, 1.0)
    return f0 * (f1 / f0) ** k


def osc(freq):
    """Sine with a per-sample frequency array."""
    return np.sin(2 * np.pi * np.cumsum(freq) / SR)


def svf(x, cutoff, q=0.7, mode="bp"):
    """Chamberlin state-variable filter; `cutoff` may be a scalar or a per-sample array."""
    n = len(x)
    fc = np.broadcast_to(np.asarray(cutoff, dtype=float), (n,))
    f = 2 * np.sin(np.pi * np.clip(fc, 10, SR / 6) / SR)
    damp = 1.0 / q
    low = band = 0.0
    out = np.empty(n)
    for i in range(n):
        high = x[i] - low - damp * band
        band += f[i] * high
        low += f[i] * band
        out[i] = band if mode == "bp" else (low if mode == "lp" else high)
    return out


def fft_band(x, lo, hi):
    """Zero-phase brick-ish band filter with soft (raised-cosine) edges, for static bands."""
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    g = np.ones_like(f)
    if lo > 0:
        g *= np.clip((f - lo * 0.7) / (lo * 0.3), 0, 1)
    if hi < SR / 2:
        g *= np.clip((hi * 1.3 - f) / (hi * 0.3), 0, 1)
    return np.fft.irfft(spec * g, n)


def env_exp(n, start, tau, attack=0.002):
    t = np.arange(n) / SR - start
    e = np.where(t < 0, 0.0, np.exp(-np.maximum(t, 0) / tau))
    a = np.clip(t / attack, 0, 1)
    return e * a


def place(dst, src, at):
    """Adds `src` at `at` seconds, its last quarter faded out (a layer never ends in a click or a bump)."""
    i = int(at * SR)
    m = min(len(src), len(dst) - i)
    if m > 0:
        layer = np.array(src[:m], dtype=float)
        f = max(min(m, int(0.008 * SR)), m // 4)
        layer[-f:] *= np.cos(np.linspace(0, np.pi / 2, f)) ** 2
        dst[i:i + m] += layer


def db(v):
    return 10 ** (v / 20)


def crackle(rng, n, start, rate0, rate1, dur, lo, hi, grain_ms=(0.6, 2.5)):
    """Poisson clicks whose rate glides rate0 -> rate1 over `dur`, each a short band-limited noise grain."""
    out = np.zeros(n)
    t = start
    while t < start + dur:
        k = (t - start) / dur
        rate = rate0 * (rate1 / rate0) ** k
        t += rng.exponential(1.0 / rate)
        if t >= start + dur:
            break
        g = int(rng.uniform(*grain_ms) * SR / 1000)
        grain = rng.standard_normal(g) * np.hanning(g) * rng.uniform(0.3, 1.0) * (1 - k) ** 0.7
        place(out, grain, t)
    return fft_band(out, lo, hi)


def reverb(x, wet=0.18, size=1.0):
    """Small Schroeder room: 4 parallel combs + 2 series allpasses."""
    def comb(sig, d, g):
        y = np.copy(sig)
        for i in range(d, len(y)):
            y[i] += g * y[i - d]
        return y

    def allpass(sig, d, g):
        y = np.zeros_like(sig)
        buf = np.zeros_like(sig)
        for i in range(len(sig)):
            xd = buf[i - d] if i >= d else 0.0
            buf[i] = sig[i] + g * xd
            y[i] = xd - g * buf[i]
        return y

    pad = np.concatenate([x, np.zeros(int(0.35 * SR))])
    w = sum(comb(pad, int(d * size), g) for d, g in ((1116, 0.78), (1188, 0.77), (1277, 0.75), (1356, 0.74))) / 4
    w = allpass(allpass(w, 225, 0.5), 556, 0.5)
    w = fft_band(w, 250, 9000)
    out = pad * (1 - wet) + w * wet
    return out[:len(x)]


def finish(x, seconds_fade=0.12):
    x = fft_band(x, 30, 18000)
    x = np.tanh(x * 1.4) / np.tanh(1.4)  # gentle saturation glues the layers
    f = int(seconds_fade * SR)
    x[-f:] *= np.linspace(1, 0, f) ** 2
    x[:16] *= np.linspace(0, 1, 16)
    return x / (np.max(np.abs(x)) + 1e-9) * db(-1.0)


# ── the two sounds ──────────────────────────────────────────────────────────

def cast(seed, pitch=1.0):
    rng = np.random.default_rng(seed)
    dur, t0 = 1.0, 0.075  # t0: the release (the bolt appears ~1 tick after the cast sound starts)
    n = int(dur * SR)
    mix = np.zeros(n)

    # 1. In-drawn swell: noise through a rising band-pass, cut at the release (anticipation).
    m = int(t0 * SR)
    swell = svf(rng.standard_normal(m), exp_sweep(450, 5200, t0, m), q=1.6)
    swell *= (np.arange(m) / m) ** 2.2
    swell[-int(0.004 * SR):] *= np.linspace(1, 0, int(0.004 * SR))
    place(mix, swell * db(-9), 0)

    # 2. Release crack: a very short broadband transient.
    g = int(0.012 * SR)
    crack = fft_band(rng.standard_normal(g), 1000, 9000) * np.exp(-np.arange(g) / (0.0028 * SR))
    place(mix, crack * db(0), t0)

    # 3. Sub thump: pitch-dropping sine.
    k = int(0.35 * SR)
    thump = osc(exp_sweep(96 * pitch, 41 * pitch, 0.14, k)) * env_exp(k, 0, 0.085, 0.003)
    place(mix, thump * db(-3), t0)

    # 4. Force punch: mid band noise.
    k = int(0.25 * SR)
    punch = fft_band(rng.standard_normal(k), 260, 1300) * env_exp(k, 0, 0.055, 0.002)
    place(mix, punch * db(-7), t0)

    # 5. Eldritch tone: three inharmonic partials gliding down, vibrato, ring-modulated at 41 Hz.
    k = int(0.8 * SR)
    t = np.arange(k) / SR
    base = exp_sweep(640 * pitch, 330 * pitch, 0.48, k)
    vib = 1 + 0.015 * np.sin(2 * np.pi * 23 * t + rng.uniform(0, 6.28))
    tone = sum(a * osc(base * r * vib) for r, a in ((1.0, 1.0), (1.414, 0.6), (2.19, 0.35)))
    ring = 0.5 + 0.5 * np.sin(2 * np.pi * 41 * t)
    tone *= (0.45 + 0.55 * ring) * env_exp(k, 0, 0.13, 0.012)
    place(mix, tone * db(-11), t0 + 0.004)

    # 6. High zing: a bright sweep down.
    k = int(0.35 * SR)
    zing = osc(exp_sweep(3300 * pitch, 1450 * pitch, 0.24, k)) * env_exp(k, 0, 0.075, 0.004)
    place(mix, zing * db(-22), t0 + 0.006)

    # 7. Crackle: dense at the release, thinning out over half a second.
    place(mix, crackle(rng, n, 0.0, 420, 25, 0.55, 1800, 7000) * db(-12), t0 + 0.01)

    # 8. Whisper: breath noise through two moving formants ("haa" -> "ooh"), very low.
    k = int(0.55 * SR)
    br = rng.standard_normal(k)
    f1 = svf(br, exp_sweep(760, 420, 0.5, k), q=6)
    f2 = svf(br, exp_sweep(1250, 820, 0.5, k), q=7)
    whisper = (f1 + 0.6 * f2) * np.sin(np.pi * np.clip(np.arange(k) / k, 0, 1)) ** 1.5
    place(mix, whisper * db(-24), t0 + 0.03)

    # 9. Low tail: a dark sine hum under the beam.
    k = int(0.6 * SR)
    hum = osc(np.full(k, 55.0 * pitch)) * env_exp(k, 0, 0.13, 0.02)
    place(mix, hum * db(-16), t0)

    return finish(reverb(mix, wet=0.16))


def impact(seed, pitch=1.0):
    rng = np.random.default_rng(seed)
    dur = 0.9
    n = int(dur * SR)
    mix = np.zeros(n)
    t0 = 0.0

    g = int(0.018 * SR)
    crack = fft_band(rng.standard_normal(g), 700, 9000) * np.exp(-np.arange(g) / (0.004 * SR))
    place(mix, crack * db(0), t0)

    k = int(0.4 * SR)
    thump = osc(exp_sweep(82 * pitch, 34 * pitch, 0.16, k)) * env_exp(k, 0, 0.11, 0.002)
    place(mix, thump * db(-2), t0)

    # Shatter: force burst with a rough (randomly amplitude-modulated) texture.
    k = int(0.45 * SR)
    t = np.arange(k) / SR
    rough = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * np.cumsum(rng.uniform(60, 160, k)) / SR))
    shatter = fft_band(rng.standard_normal(k), 550, 4200) * rough * env_exp(k, 0, 0.1, 0.002)
    place(mix, shatter * db(-6), t0 + 0.002)

    place(mix, crackle(rng, n, 0.0, 650, 18, 0.65, 2500, 8000, (0.4, 1.8)) * db(-11), t0 + 0.008)

    k = int(0.5 * SR)
    base = exp_sweep(900 * pitch, 380 * pitch, 0.3, k)
    tt = np.arange(k) / SR
    tone = (osc(base) + 0.5 * osc(base * 1.52)) * (0.4 + 0.6 * (0.5 + 0.5 * np.sin(2 * np.pi * 37 * tt)))
    place(mix, tone * env_exp(k, 0, 0.13, 0.006) * db(-12), t0 + 0.004)

    return finish(reverb(mix, wet=0.2, size=0.9), 0.15)


# ── output ──────────────────────────────────────────────────────────────────

VARIANTS = {
    "cast": [(cast, 11, 1.0), (cast, 23, 0.96), (cast, 37, 1.04)],
    "impact": [(impact, 41, 1.0), (impact, 53, 0.95), (impact, 67, 1.05)],
}


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    out = Path(sys.argv[1])
    ogg = "--ogg" in sys.argv
    out.mkdir(parents=True, exist_ok=True)
    for name, variants in VARIANTS.items():
        for i, (fn, seed, pitch) in enumerate(variants, 1):
            x = fn(seed, pitch)
            stem = f"{name}{i}"
            write_wav(out / f"{stem}.wav", x)
            if ogg:
                subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(out / f"{stem}.wav"), "-c:a", "libvorbis",
                                "-q:a", "6", str(out / f"{stem}.ogg")], check=True)
            rms = 20 * math.log10(float(np.sqrt(np.mean(x ** 2))) + 1e-12)
            print(f"{stem}: {len(x) / SR:.2f} s, RMS {rms:.1f} dBFS")


if __name__ == "__main__":
    main()
