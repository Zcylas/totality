"""Generates the Small Slime base geometry (cuboid list) and its 128x128 grayscale texture.

    python3 make_slime.py   -> slime_base.png, slime_geometry.json

Body: a blob voxelised at 1 model unit (20 wide, 20 deep, 15 tall; front view profile measured from the Hydro
reference sheet), covered by nested cuboids symmetric about the vertical axis. Cuboids may overlap: every face is
UV-mapped by planar projection (front/back/left/right/top/bottom views of the blob), so coincident faces show
identical texels.

Texture regions (texel x, y, w, h), 2 texels per model unit:
  front (north) (0,0) 40x30     back (south) (40,0) 40x30     east (+x) (80,0) 40x30
  west (-x)     (0,30) 40x30    top (40,30) 40x40             bottom (80,30) 40x40
  eye front     (0,64) 6x12, oval with transparent corners
  transparent   (10,64) 2x2, used by the eye plates' back and edge faces (the plates sit flush on the body)
Body and eyes are separate regions (and separate model groups) so either can be recoloured on its own.
"""
import json
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image

R, HT, M = 10, 15, 2.2  # half width/depth, height, superellipse exponent of the horizontal sections
# Front and back are levelled at |z| = 9 (18 deep; the reference top view is ~0.93 as deep as wide). This gives the
# flat face area the eyes sit flush on, as in the reference sheet's Minecraft construction.
DEPTH = 9.0
TPU = 2  # texels per unit
# Front-view width (fraction of max) against height fraction, measured from the reference silhouette.
PROF_H = [0, .022, .059, .096, .133, .17, .207, .244, .28, .32, .43, .467, .504, .54, .578, .615, .652, .689, .726,
          .763, .80, .837, .874, .911, .948, .985, 1.0]
PROF_W = [.64, .68, .78, .82, .87, .92, .95, .965, .985, 1, 1, .994, .976, .965, .947, .923, .906, .865, .823, .776,
          .729, .659, .582, .494, .37, .18, 0]


# Smoothed profile (the raw measurements are piecewise linear; kinks would show as bands in the shading).
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


# --- Geometry: voxels, then a small cover of centred boxes. ---
N = 2 * R
vox = np.zeros((N, HT, N), bool)
for j in range(HT):
    for i in range(N):
        for k in range(N):
            vox[i, j, k] = inside(-R + i + 0.5, j + 0.5, -R + k + 0.5)

cands = set()
for y in range(HT):
    q = vox[R:, y, R:]
    for a in range(1, R + 1):
        b = 0
        while b < R and q[:a, b].all():
            b += 1
        if b == 0:
            break
        cands.add((a, b))
boxes = []
for a, b in sorted(cands):
    fits = [y for y in range(HT) if vox[R - a:R + a, y, R - b:R + b].all()]
    run = [fits[0]]
    for y in fits[1:] + [None]:
        if y is not None and y == run[-1] + 1:
            run.append(y)
            continue
        boxes.append((a, b, run[0], run[-1] + 1))
        if y is not None:
            run = [y]


def fill(bx, arr):
    a, b, y0, y1 = bx
    arr[R - a:R + a, y0:y1, R - b:R + b] = True


cover = np.zeros_like(vox)
chosen = []
while (vox & ~cover).any():
    best = max(boxes, key=lambda bx: (~cover[R - bx[0]:R + bx[0], bx[2]:bx[3], R - bx[1]:R + bx[1]]).sum())
    chosen.append(best)
    fill(best, cover)
for bx in list(chosen):
    rest = np.zeros_like(vox)
    for o in chosen:
        if o is not bx:
            fill(o, rest)
    if (rest == vox).all():
        chosen.remove(bx)
check = np.zeros_like(vox)
for bx in chosen:
    fill(bx, check)
assert (check == vox).all()
chosen.sort(key=lambda bx: (bx[2], -bx[0] * bx[1]))

# Eyes: 3x6 plates centred 3.5 units either side of the middle, y 3..9 (reference: 2.75x5.7 at +-3.15, y 3.5..9.2),
# snapped to whole cells so the whole plate lies on the levelled front (z = -9).
EYE_X = (2, 5)  # |x| range
EYE_Y = (3, 9)
EYE_FRONT = -DEPTH
for sx in (1, -1):
    for x in range(EYE_X[0], EYE_X[1]):
        cell = R + (x if sx > 0 else -x - 1)
        for y in range(*EYE_Y):
            assert R - np.where(vox[cell, y, :])[0].min() == DEPTH, "eye area must be flat"

# --- Texture: shade the smooth blob as seen in each projection. ---
LIGHT = np.array([0.45, 0.75, -0.5])
LIGHT /= np.linalg.norm(LIGHT)
VIEW = np.array([0.25, 0.35, -1.0])
VIEW /= np.linalg.norm(VIEW)
HALF = (LIGHT + VIEW) / np.linalg.norm(LIGHT + VIEW)


def normal(x, y, z, e=0.05):
    def f(px, py, pz):
        r = max(radius(min(max(py, 0), HT)), 1e-3)
        return (abs(px) / r) ** M + (abs(pz) / r) ** M - 1
    g = np.array([f(x + e, y, z) - f(x - e, y, z), f(x, y + e, z) - f(x, y - e, z), f(x, y, z + e) - f(x, y, z - e)])
    n = np.linalg.norm(g)
    if n == 0 or y > HT - 0.4:  # the apex, where the section shrinks to a point
        return np.array([0, 1.0, 0])
    return g / n


def surface(axis, sign, a, b):
    """Point on the blob along a ray parallel to `axis` from the `sign` side, through (a, b) in the other two axes."""
    lo, hi = 0.0, 12.0 if axis != 1 else HT
    if axis == 1 and sign < 0:
        return np.array([a, 0.0, b]) if inside(a, 0.01, b) else None

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


rng = random.Random(4127)
img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
px = img.load()
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def shade(p, n, view_axis_dir):
    diff = max(0.0, float(n @ LIGHT))
    spec = max(0.0, float(n @ HALF)) ** 40
    glow = 1 - p[1] / HT  # light passing through the lower body
    v = 148 + 72 * diff + 36 * glow
    rim = 1 - abs(float(n @ view_axis_dir))
    v -= 45 * rim ** 2
    return v, spec


def paint(ox, oy, w, h, sample):
    vals = {}
    for tx in range(w):
        for ty in range(h):
            s = sample(tx + 0.5, ty + 0.5)
            if s is not None:
                vals[tx, ty] = s
    mask = set(vals)
    for (tx, ty), (v, spec) in vals.items():
        edge = any((tx + dx, ty + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            v -= 30
        q = 6
        v = round((v + (BAYER[ty % 4][tx % 4] / 16 - 0.5) * q) / q) * q + rng.randint(-2, 2)
        if spec > 0.55 and not edge:
            v = 250
        elif spec > 0.3 and not edge:
            v = max(v, 228)
        g = int(max(60, min(252, v)))
        px[ox + tx, oy + ty] = (g, g, g, 255)
    # Pad outside the silhouette with the nearest painted texel (hidden faces, filtering safety).
    for tx in range(w):
        for ty in range(h):
            if (tx, ty) in mask:
                continue
            best = min(mask, key=lambda m: (m[0] - tx) ** 2 + (m[1] - ty) ** 2)
            px[ox + tx, oy + ty] = px[ox + best[0], oy + best[1]]


def side_view(axis, sign, u_of, view_dir):
    """u_of(u) -> horizontal model coordinate; rows run from y = HT (row 0) down to 0."""
    def sample(u, v):
        y = HT - v / TPU
        h = u_of(u / TPU)
        p = surface(axis, sign, h, y) if axis == 2 else surface(axis, sign, y, h)
        if p is None:
            return None
        return shade(p, normal(*p), view_dir)
    return sample


# axis 2 = z rays (front/back), axis 0 = x rays (east/west); surface() takes the two remaining coordinates in order.
paint(0, 0, 40, 30, side_view(2, -1, lambda u: R - u, np.array([0, 0, -1.0])))   # north: left = +x
paint(40, 0, 40, 30, side_view(2, 1, lambda u: u - R, np.array([0, 0, 1.0])))    # south: left = -x
paint(80, 0, 40, 30, side_view(0, 1, lambda u: R - u, np.array([1.0, 0, 0])))    # east: left = +z
paint(0, 30, 40, 30, side_view(0, -1, lambda u: u - R, np.array([-1.0, 0, 0])))  # west: left = -z


def top_sample(u, v):
    x, z = u / TPU - R, v / TPU - R
    p = surface(1, 1, x, z)
    return None if p is None else shade(p, normal(*p), np.array([0, 1.0, 0]))


def bottom_sample(u, v):
    """Seen from below: the flat base, and around it the underside of the bulge (lowest surface point per column)."""
    x, z = R - u / TPU, v / TPU - R
    ys = [t for t in np.arange(0.01, HT, 0.05) if inside(x, t, z)]
    if not ys:
        return None
    if ys[0] < 0.02:  # flat base resting on the ground
        d = math.hypot(x, z) / radius(0.01)
        return 182 + 18 * (1 - d) - 12 * d ** 4, 0.0
    p = np.array([x, ys[0], z])
    v, _ = shade(p, normal(*p), np.array([0, -1.0, 0]))
    return v - 18, 0.0  # in the body's own shadow


paint(40, 30, 40, 40, top_sample)
paint(80, 30, 40, 40, bottom_sample)

# --- Eyes: oval plate, dark rim, light interior with a soft lower shade and a top highlight. ---
EYE = [
    "..##..",
    ".#hh#.",
    "#hwww#",
    "#wwww#",
    "#wwww#",
    "#wwww#",
    "#wwww#",
    "#wwws#",
    "#wwws#",
    "#swss#",
    ".#ss#.",
    "..##..",
]
tone = {"#": 58, "h": 255, "w": 240, "s": 214}
for y, row in enumerate(EYE):
    for x, ch in enumerate(row):
        if ch != ".":
            g = tone[ch]
            px[x, 64 + y] = (g, g, g, 255)

out = Path(__file__).parent
img.save(out / "slime_base.png")
body = [[[-a, y0, -b], [a, y1, b]] for a, b, y0, y1 in chosen]
(out / "slime_geometry.json").write_text(json.dumps({
    "body": body,
    "eyes": {"x": EYE_X, "y": EYE_Y, "front": EYE_FRONT},
    "size": [2 * R, HT, 2 * R],
}))
print(len(body), "body cuboids")
