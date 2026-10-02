#!/usr/bin/env python3
"""VFX Experiment 1: pixel comparisons of the glow capture screenshots (scene 66).

usage: compare_glow_frames.py <screenshots dir> <tag> <out dir>

For each pair it prints how many pixels changed and where, and writes an amplified difference image:
  * 01_baseline_no_sources vs 12_restored_no_sources  -> must be identical (glow layer leaves no trace)
  * 02_normal_glow_off     vs 03_emissive_glow_on     -> differences only around the test objects
  * 02_normal_glow_off     vs 08_intensity_0          -> intensity 0 runs no passes: identical to "off"
It also reports, for the 02/03 pair, the largest change among pixels that are far (> R px) from every pixel the
glow changed strongly, i.e. whether the glow spreads onto unrelated bright surfaces (snow, lava, glowstone, wall).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def load(d, tag, name):
    prefix = f"notification_v2_GLW_{tag + '_' if tag else ''}{name}"
    matches = sorted(Path(d).glob(prefix + "*.png"))
    if not matches:
        raise SystemExit(f"missing {prefix}*.png")
    return np.asarray(Image.open(matches[-1]).convert("RGB")).astype(np.int16)


def compare(a, b, label, out, threshold=2):
    diff = np.abs(a - b).max(axis=2)
    changed = diff > threshold
    n = int(changed.sum())
    msg = f"{label}: max channel difference {int(diff.max())}, pixels changed by > {threshold}: {n} " \
          f"({100.0 * n / diff.size:.3f}% of {diff.shape[1]}x{diff.shape[0]})"
    if n:
        ys, xs = np.nonzero(changed)
        msg += f", bounding box x {xs.min()}-{xs.max()}, y {ys.min()}-{ys.max()}"
    print(msg)
    Image.fromarray(np.clip(diff * 8, 0, 255).astype(np.uint8)).save(out)
    return diff


def far_from_glow(diff, strong=24, radius=40):
    """Largest difference among pixels more than `radius` px away from every strongly changed pixel."""
    core = diff > strong
    if not core.any():
        return int(diff.max()), 0
    # Dilate the strong-change mask with a separable max filter (square neighbourhood).
    m = core.copy()
    for axis in (0, 1):
        acc = m.copy()
        for s in range(1, radius + 1):
            acc |= np.roll(m, s, axis=axis) | np.roll(m, -s, axis=axis)
        m = acc
    outside = ~m
    return int(diff[outside].max()) if outside.any() else 0, int(outside.sum())


# Bright vanilla surfaces of the scene-66 stage at 1920x1080 (x0, y0, x1, y1), chosen away from the test objects, the
# animated lava / sea lantern textures and the lava smoke particles. Scaled for other resolutions.
REGIONS = {
    "snow foreground": (0, 820, 1920, 1080),
    "white wall (upper left)": (450, 100, 800, 380),
    "glowstone (left side)": (300, 590, 450, 665),
}


def regions(a, b, label):
    h, w = a.shape[:2]
    sx, sy = w / 1920.0, h / 1080.0
    for name, (x0, y0, x1, y1) in REGIONS.items():
        r = (slice(int(y0 * sy), int(y1 * sy)), slice(int(x0 * sx), int(x1 * sx)))
        d = np.abs(a[r] - b[r]).max(axis=2)
        print(f"  {label} | {name}: max channel difference {int(d.max())}, mean {d.mean():.4f}")


def main():
    d, tag, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    out.mkdir(parents=True, exist_ok=True)
    base, restored = load(d, tag, "01_baseline_no_sources"), load(d, tag, "12_restored_no_sources")
    off, on, zero = load(d, tag, "02_normal_glow_off"), load(d, tag, "03_emissive_glow_on"), load(d, tag, "08_intensity_0")
    compare(base, restored, "baseline vs restored (no sources)", out / f"diff_{tag}_baseline_vs_restored.png")
    d23 = compare(off, on, "glow off vs glow on (same objects)", out / f"diff_{tag}_off_vs_on.png")
    compare(off, zero, "glow off vs intensity 0", out / f"diff_{tag}_off_vs_intensity0.png")
    regions(base, restored, "baseline vs restored")
    regions(off, on, "glow off vs on")
    night_off, night_on = load(d, tag, "09_night_glow_off"), load(d, tag, "10_night_glow_on")
    compare(night_off, night_on, "night: glow off vs on", out / f"diff_{tag}_night_off_vs_on.png")
    regions(night_off, night_on, "night glow off vs on")
    worst, count = far_from_glow(d23)
    print(f"glow off vs on: largest change more than 40 px from the glow core: {worst} (over {count} pixels)")


if __name__ == "__main__":
    main()
