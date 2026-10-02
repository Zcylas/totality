#!/usr/bin/env python3
"""Generates the Small Slime element motif masks (Small Slimes V1 final textures, 2026-10-02).

    python3 make_slime_motifs.py <repo root>
      -> src/main/resources/assets/totality/textures/entity/slime_test/motifs/<element>.png  (128x128 RGBA)

Every detail is defined on the slime's 3D body surface and evaluated for every texel of the shared texture layout
(front/back/east/west side views and the top view, 2 texels per model unit; the bottom view is left untouched), so a
spot, patch or line lands on the same place of the body on every stepped cuboid face that shows it.

Mask encoding (read by SlimePaletteMapper at resource load; alpha 0 = no detail):
  red   = 128 + tone * 255         tone shift added to the gray value before the element palette (patches, spots)
  green = 128 + height * 255       offset of the secondary-ramp height (irregular moss edge, drippy snow cap)
  blue  = overlay * 255            amount of the element's overlay colour laid over the result (snow, spores, lines)

The blob shape functions below are copied from Context/Assets/Models/slime_base/make_slime.py (which must NOT be run
or imported: it regenerates the approved texture). Coordinates are Blockbench's: x right, y up (0..15), front = -z.
Deterministic: fixed seeds, no time or randomness from the environment.
"""
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# ── Blob shape (copied from make_slime.py) ────────────────────────────────────────────────────────────────────────
R, HT, M = 10, 15, 2.2
DEPTH = 9.0
TPU = 2
PROF_H = [0, .022, .059, .096, .133, .17, .207, .244, .28, .32, .43, .467, .504, .54, .578, .615, .652, .689, .726,
          .763, .80, .837, .874, .911, .948, .985, 1.0]
PROF_W = [.64, .68, .78, .82, .87, .92, .95, .965, .985, 1, 1, .994, .976, .965, .947, .923, .906, .865, .823, .776,
          .729, .659, .582, .494, .37, .18, 0]
_FINE_H = np.linspace(-0.2, 1.2, 1401)
_FINE_W = np.interp(_FINE_H, PROF_H, PROF_W, left=PROF_W[0], right=0.0)
_K = np.exp(-0.5 * (np.arange(-60, 61) / 25.0) ** 2)
_FINE_W = np.convolve(_FINE_W, _K / _K.sum(), mode="same")
_FINE_W[_FINE_H > 1.0] = 0.0


def radius(y):
    if y < 0 or y > HT:
        return 0.0
    return R * float(np.interp(y / HT, _FINE_H, _FINE_W))


def inside(x, y, z):
    r = radius(y)
    return r > 0 and abs(z) <= DEPTH and (abs(x) / r) ** M + (abs(z) / r) ** M <= 1


def normal(x, y, z, e=0.05):
    def f(px, py, pz):
        r = max(radius(min(max(py, 0), HT)), 1e-3)
        return (abs(px) / r) ** M + (abs(pz) / r) ** M - 1
    g = np.array([f(x + e, y, z) - f(x - e, y, z), f(x, y + e, z) - f(x, y - e, z), f(x, y, z + e) - f(x, y, z - e)])
    n = np.linalg.norm(g)
    if n == 0 or y > HT - 0.4:
        return np.array([0, 1.0, 0])
    return g / n


def surface(axis, sign, a, b):
    lo, hi = 0.0, 12.0 if axis != 1 else HT

    def pt(t):
        p = [a, b]
        p.insert(axis, t * sign if axis != 1 else t)
        return p
    if axis == 1:
        ys = [t for t in np.arange(HT, 0, -0.05) if inside(a, t, b)]
        if not ys:
            return None
        lo, hi = ys[0], ys[0] + 0.05
        for _ in range(30):
            mid = (lo + hi) / 2
            if inside(*pt(mid)):
                lo = mid
            else:
                hi = mid
        return np.array(pt(lo))
    if not inside(*pt(0)):
        return None
    for _ in range(30):
        mid = (lo + hi) / 2
        if inside(*pt(mid)):
            lo = mid
        else:
            hi = mid
    return np.array(pt(lo))


# ── Texel -> surface point, for the regions the body draws (layout of make_slime.py) ───────────────────────────────
def side_points(ox, oy, axis, sign, u_of):
    out = {}
    for tx in range(40):
        for ty in range(30):
            y = HT - (ty + 0.5) / TPU
            h = u_of((tx + 0.5) / TPU)
            p = surface(axis, sign, h, y) if axis == 2 else surface(axis, sign, y, h)
            if p is not None:
                out[(ox + tx, oy + ty)] = p
    return out


def top_points():
    out = {}
    for tx in range(40):
        for ty in range(40):
            x, z = (tx + 0.5) / TPU - R, (ty + 0.5) / TPU - R
            p = surface(1, 1, x, z)
            if p is not None:
                out[(40 + tx, 30 + ty)] = p
    return out


def used_texels(bbmodel):
    """Texels whose centre lies in a body cuboid's north/south/east/west/up face rectangle (Blockbench face UVs; the
    same rule as SlimeTexelHeights). Details are only written there; the bottom view is left untouched."""
    import json
    used = set()
    for e in json.loads(Path(bbmodel).read_text())["elements"]:
        if e["name"].startswith("eye"):
            continue
        for face in ("north", "south", "east", "west", "up"):
            u0, v0, u1, v1 = e["faces"][face]["uv"]
            for tx in range(int(min(u0, u1)), int(math.ceil(max(u0, u1)))):
                for ty in range(int(min(v0, v1)), int(math.ceil(max(v0, v1)))):
                    if min(u0, u1) <= tx + 0.5 <= max(u0, u1) and min(v0, v1) <= ty + 0.5 <= max(v0, v1):
                        used.add((tx, ty))
    return used


def all_points():
    pts = {}
    pts.update(side_points(0, 0, 2, -1, lambda u: R - u))     # front (north)
    pts.update(side_points(40, 0, 2, 1, lambda u: u - R))     # back (south)
    pts.update(side_points(80, 0, 0, 1, lambda u: R - u))     # east (+x)
    pts.update(side_points(0, 30, 0, -1, lambda u: u - R))    # west (-x)
    pts.update(top_points())
    return {k: (p, normal(*p)) for k, p in pts.items()}


# ── Helpers for surface features ────────────────────────────────────────────────────────────────────────────────
def on_surface(direction, y):
    """The surface point at height y in the horizontal direction (dx, dz)."""
    dx, dz = direction
    n = math.hypot(dx, dz)
    dx, dz = dx / n, dz / n
    lo, hi = 0.0, 12.0
    for _ in range(40):
        mid = (lo + hi) / 2
        if inside(dx * mid, y, dz * mid):
            lo = mid
        else:
            hi = mid
    return np.array([dx * lo, y, dz * lo])


def frame(n):
    """Tangent axes (right, up) of a surface normal; 'up' follows +y where possible."""
    up = np.array([0.0, 1.0, 0.0])
    if abs(n @ up) > 0.95:
        up = np.array([0.0, 0.0, -1.0])
    right = np.cross(up, n)
    right /= np.linalg.norm(right)
    return right, np.cross(n, right)


def eye_area(p):
    """The flat face behind the eye plates (no details there)."""
    return p[2] < -8.0 and 1.6 <= abs(p[0]) <= 5.4 and 2.6 <= p[1] <= 9.4


def value_noise(seed):
    rnd = random.Random(seed)
    grid = {}

    def at(i, j, k):
        key = (i, j, k)
        if key not in grid:
            grid[key] = rnd.random()
        return grid[key]

    def noise(p, scale):
        x, y, z = p / scale
        i, j, k = math.floor(x), math.floor(y), math.floor(z)
        fx, fy, fz = x - i, y - j, z - k
        sx, sy, sz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
        v = 0.0
        for a in (0, 1):
            for b in (0, 1):
                for c in (0, 1):
                    w = (sx if a else 1 - sx) * (sy if b else 1 - sy) * (sz if c else 1 - sz)
                    v += w * at(i + a, j + b, k + c)
        return v
    return noise


class Stamp:
    """A pixel-art decal on the surface: rows of '#' (full) and '+' (half), one character = one texel (0.5 unit)."""

    def __init__(self, rows, centre, n):
        self.rows, self.c, self.n = rows, centre, n
        self.r, self.u = frame(n)
        self.h, self.w = len(rows), len(rows[0])

    def at(self, p, pn):
        if pn @ self.n < 0.35:
            return 0.0
        d = p - self.c
        if abs(d @ self.n) > 2.5:
            return 0.0
        col = math.floor((d @ self.r) * TPU + self.w / 2)
        row = math.floor(-(d @ self.u) * TPU + self.h / 2)
        if 0 <= row < self.h and 0 <= col < self.w:
            return {"#": 1.0, "+": 0.5}.get(self.rows[row][col], 0.0)
        return 0.0


class HexPatch:
    """An angular stone patch: a hexagon in the surface's tangent plane, its size varied by noise."""

    def __init__(self, centre, size, noise):
        self.c, self.size, self.noise = centre, size, noise
        self.n = normal(*centre)
        self.r, self.u = frame(self.n)

    def at(self, p):
        d = p - self.c
        if np.linalg.norm(d) > self.size * 1.6:
            return False
        a, b = abs(d @ self.r), abs(d @ self.u)
        hexd = max(a * 0.866 + b * 0.5, b)
        return hexd <= self.size * (0.85 + 0.3 * self.noise(p, 1.2))


def blob(p, centre, radius_units, irregular=None):
    d = np.linalg.norm(p - centre)
    if irregular is not None:
        d *= 0.8 + 0.4 * irregular(p, 1.6)
    return d <= radius_units


# ── Element designs (Small Slimes only; see the final-texture report) ─────────────────────────────────────────────
SWIRL = [  # an Archimedean wind curl with a trailing tail
    "..........##.",
    "...........##",
    "....####....#",
    "..###..##...#",
    "..#.....##...",
    ".##......#...",
    ".#...##..#...",
    ".#...#..##...",
    ".#...####...#",
    ".##........##",
    "..##......##.",
    "...###...##..",
    ".....#####...",
]
SNOWFLAKE = [
    "...#...",
    ".#.#.#.",
    "..###..",
    "###.###",
    "..###..",
    ".#.#.#.",
    "...#...",
]


def anemo(p, n):
    for st in ANEMO_STAMPS:
        v = st.at(p, n)
        if v:
            return 0.06, 0.0, 0.75 * v
    return None


def geo(p, n):
    edge = 0.05 * (GEO_NOISE(p, 2.2) - 0.5) * 2   # rough rock edge of the dark dome
    tone = 0.0
    for patch in GEO_DARK:
        if patch.at(p):
            tone = -0.20
    for c, r in GEO_LIGHT:
        if blob(p, c, r, GEO_NOISE):
            tone = 0.17
    return tone, edge, 0.0


def electro(p, n):
    if n[2] > -0.3:
        return None
    x, y = abs(p[0]), p[1]
    line = (abs(y - 10.75) < 0.26 and 2.5 <= x <= 6.25) or (abs(x - 6.0) < 0.26 and 4.25 <= y <= 11.0) \
        or (abs(x - 2.75) < 0.26 and 10.5 <= y <= 12.75)
    if line:
        return 0.0, 0.0, 0.85
    return None


def dendro(p, n):
    hf = p[1] / HT
    az = math.atan2(p[2], p[0])
    edge = 0.06 * math.sin(3 * az + 0.7) + 0.04 * math.sin(7 * az + 2.1) + 0.05 * (DENDRO_NOISE(p, 1.8) - 0.5) * 2
    for a in DENDRO_DRIPS:
        da = math.atan2(math.sin(az - a), math.cos(az - a))
        if abs(da) < 0.16:
            edge += 0.10 * (1 - abs(da) / 0.16)
    tone, overlay = 0.0, 0.0
    if hf > 0.52:
        for c, r in DENDRO_MOSS:
            if blob(p, c, r, DENDRO_NOISE):
                tone = -0.14
        for c in DENDRO_SPORES:
            if np.linalg.norm(p - c) < 0.55:
                overlay = 0.8
    return tone, edge, overlay


def hydro(p, n):
    if p[1] / HT < 0.5:
        return None
    for c, r in HYDRO_MOTTLE:
        if blob(p, c, r, HYDRO_NOISE):
            return -0.07, 0.0, 0.0
    return None


def pyro(p, n):
    dark = PYRO_NOISE(p, 3.2)
    light = PYRO_NOISE(p + np.array([11.0, 3.0, 5.0]), 2.6)
    if dark > 0.66 and p[1] > 1.5:
        return -0.17, 0.0, 0.0    # darker molten patches
    if light > 0.70 and p[1] < 8.5:
        return 0.08, 0.0, 0.30    # warm yellow highlights in the lower body
    return None


def cryo(p, n):
    az = math.atan2(p[2], p[0])
    edge = 0.03 * math.sin(5 * az + 0.4) + 0.015 * math.sin(11 * az + 1.3)
    for a, depth in CRYO_DRIPS:
        da = math.atan2(math.sin(az - a), math.cos(az - a))
        if abs(da) < 0.18:
            edge += depth * 0.5 * (1 + math.cos(math.pi * da / 0.18))   # rounded scallop
    overlay = 0.0
    for st in CRYO_FLAKES:
        v = st.at(p, n)
        if v:
            overlay = 0.9 * v
    for c in CRYO_DOTS:
        if np.linalg.norm(p - c) < 0.62:
            overlay = 0.85
    return 0.0, edge, overlay


def design_setup():
    global ANEMO_STAMPS, GEO_NOISE, GEO_DARK, GEO_LIGHT, DENDRO_NOISE, DENDRO_DRIPS, DENDRO_MOSS, DENDRO_SPORES
    global HYDRO_NOISE, HYDRO_MOTTLE, PYRO_NOISE, CRYO_DRIPS, CRYO_FLAKES, CRYO_DOTS
    # Anemo: one swirl on each side, a little toward the front (visible from the front three-quarter and the sides).
    ANEMO_STAMPS = []
    for sx in (1, -1):
        c = on_surface((sx, -0.45), 7.0)
        rows = SWIRL if sx > 0 else [r[::-1] for r in SWIRL]
        ANEMO_STAMPS.append(Stamp(rows, c, normal(*c)))
    # Geo: dark angular patches around the upper body / dome edge, light stone spots on the lower body.
    GEO_NOISE = value_noise(31)
    rnd = random.Random(7)
    GEO_DARK, GEO_LIGHT = [], []
    for i in range(16):
        a = 2 * math.pi * i / 16 + rnd.uniform(-0.15, 0.15)
        y = rnd.uniform(7.5, 11.5)
        c = on_surface((math.cos(a), math.sin(a)), y)
        if not eye_area(c):
            GEO_DARK.append(HexPatch(c, rnd.uniform(1.1, 1.6), GEO_NOISE))
    for i in range(14):
        a = 2 * math.pi * i / 14 + rnd.uniform(-0.2, 0.2)
        y = rnd.uniform(1.0, 6.0)
        c = on_surface((math.cos(a), math.sin(a)), y)
        if not eye_area(c):
            GEO_LIGHT.append((c, rnd.uniform(1.0, 1.6)))
    # Dendro: irregular moss edge with a few downward tongues, darker moss blotches and light spore dots on the green.
    DENDRO_NOISE = value_noise(53)
    rnd = random.Random(11)
    DENDRO_DRIPS = [rnd.uniform(-math.pi, math.pi) for _ in range(5)]
    DENDRO_MOSS, DENDRO_SPORES = [], []
    for i in range(9):
        a = 2 * math.pi * i / 9 + rnd.uniform(-0.25, 0.25)
        c = on_surface((math.cos(a), math.sin(a)), rnd.uniform(9.5, 13.0))
        if not eye_area(c):
            DENDRO_MOSS.append((c, rnd.uniform(1.0, 1.6)))
    for i in range(22):
        a = rnd.uniform(-math.pi, math.pi)
        c = on_surface((math.cos(a), math.sin(a)), rnd.uniform(9.0, 13.8))
        if not eye_area(c):
            DENDRO_SPORES.append(c)
    # Hydro: a few large, very soft darker patches on the upper body.
    HYDRO_NOISE = value_noise(71)
    rnd = random.Random(13)
    HYDRO_MOTTLE = []
    for i in range(6):
        a = 2 * math.pi * i / 6 + rnd.uniform(-0.3, 0.3)
        c = on_surface((math.cos(a), math.sin(a)), rnd.uniform(9.5, 13.0))
        HYDRO_MOTTLE.append((c, rnd.uniform(2.0, 2.8)))
    PYRO_NOISE = value_noise(97)
    # Cryo: wavy cap edge with drips, snow dots just below the cap, snowflakes on both sides and the back.
    rnd = random.Random(17)
    CRYO_DRIPS = [(rnd.uniform(-math.pi, math.pi), rnd.uniform(0.04, 0.07)) for _ in range(7)]
    CRYO_FLAKES = []
    for direction, y in (((1, -0.2), 4.2), ((-1, -0.2), 4.2), ((0.3, 1), 5.0)):
        c = on_surface(direction, y)
        CRYO_FLAKES.append(Stamp(SNOWFLAKE, c, normal(*c)))
    CRYO_DOTS = []
    for i in range(9):
        a = 2 * math.pi * i / 9 + rnd.uniform(-0.2, 0.2)
        c = on_surface((math.cos(a), math.sin(a)), rnd.uniform(7.2, 8.6))
        if not eye_area(c):
            CRYO_DOTS.append(c)


DESIGNS = {"anemo": anemo, "geo": geo, "electro": electro, "dendro": dendro, "hydro": hydro, "pyro": pyro, "cryo": cryo}


def encode(tone, height, overlay):
    r = int(round(np.clip(128 + tone * 255, 0, 255)))
    g = int(round(np.clip(128 + height * 255, 0, 255)))
    b = int(round(np.clip(overlay * 255, 0, 255)))
    return r, g, b


def main(repo):
    out_dir = Path(repo) / "src/main/resources/assets/totality/textures/entity/slime_test/motifs"
    out_dir.mkdir(parents=True, exist_ok=True)
    design_setup()
    used = used_texels(Path(repo) / "Context/Assets/Models/slime_base/slime_base.bbmodel")
    points = {k: v for k, v in all_points().items() if k in used}
    for name, fn in DESIGNS.items():
        img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
        px = img.load()
        for (tx, ty), (p, n) in points.items():
            if eye_area(p):
                continue
            res = fn(p, n)
            if res is None:
                continue
            r, g, b = encode(*res)
            if (r, g, b) != (128, 128, 0):
                px[tx, ty] = (r, g, b, 255)
        img.save(out_dir / f"{name}.png", optimize=True)
        print(name, int((np.asarray(img)[..., 3] > 0).sum()), "detail texels")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
