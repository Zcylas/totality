#!/usr/bin/env python3
"""Eldritch Blast V2 A/V timing from a real-time capture run (scene 70 with -Dtotality.eldritch.audio=true).

Inputs: the capture log (cast/impact events with wall-clock ms) and the game-only audio recording made by
record_game_audio.sh (+ its .start file). For every logged event it finds the first transient in the recording after the
event (high-passed 5 ms envelope crossing 30 % of the window peak) and prints the audio onset relative to the event.
The cast sound's crack sits 75 ms into the file (= the visual release); the impact sound starts at its transient.
Usage: analyse_av_timing.py <hologram-capture.log> <game_audio.wav>     (numpy only)
"""
import re
import sys
import wave

import numpy as np


def load(path):
    with wave.open(path) as w:
        sr, ch, n = w.getframerate(), w.getnchannels(), w.getnframes()
        raw = w.readframes(n)
        width = w.getsampwidth()
    a = np.frombuffer(raw, dtype=np.int16 if width == 2 else np.int32).astype(np.float64)
    return a.reshape(-1, ch).mean(1) / (2 ** (8 * width - 1)), sr


def main():
    log, wav = sys.argv[1], sys.argv[2]
    start = int(open(wav + ".start").read().strip())
    x, sr = load(wav)
    hp = np.diff(x, prepend=x[0])  # crude high-pass: transients stand out
    win = int(0.005 * sr)
    env = np.sqrt(np.convolve(hp ** 2, np.ones(win) / win, mode="same"))
    events = []
    variant = "CUSTOM"
    for line in open(log):
        if "run: sound REFERENCE" in line:
            variant = "REFERENCE"
        if "run: sound CUSTOM" in line:
            variant = "CUSTOM"
        for kind, wall in re.findall(r"(cast|impact) t=[0-9.]+ wall=(\d+)", line):
            events.append((kind, int(wall), variant))
    seen = set()
    rows = []
    for kind, wall, variant in events:
        if (kind, wall) in seen:
            continue
        seen.add((kind, wall))
        t = (wall - start) / 1000.0
        i0, i1 = int((t - 0.05) * sr), int((t + 0.45) * sr)
        if i0 < 0 or i1 > len(env):
            continue
        seg = env[i0:i1]
        k = int(np.argmax(seg > 0.3 * seg.max()))
        onset = (i0 + k) / sr - t
        rows.append((kind, variant, t, onset))
        print(f"{kind:6s} {variant:9s} event@{t:7.3f}s  audio onset {onset * 1000:+6.0f} ms")
    casts = [r for r in rows if r[0] == "cast"]
    impacts = [r for r in rows if r[0] == "impact"]
    for name, rs in (("cast", casts), ("impact", impacts)):
        for v in ("CUSTOM", "REFERENCE"):
            o = [r[3] * 1000 for r in rs if r[1] == v]
            if o:
                print(f"summary {name} {v}: n={len(o)} median onset {np.median(o):+.0f} ms (min {min(o):+.0f}, max {max(o):+.0f})")


if __name__ == "__main__":
    main()
