#!/usr/bin/env python3
"""Fireball V2 design phase: original procedural concept/storyboard frames (NOT in-game captures, NOT final art).

A tiny numpy ray tracer: a sky, a block-grid floor, optional wall, a blocky stand-in figure, the Fireball bead and
tail, and the explosion shell (ray-marched procedural noise, temperature colour ramp), plus a screen-space bloom that
imitates what the Totality Emissive Rendering Layer adds. All shapes, colours and timings come from the Fireball V2
design report (TOTALITY_VFX_FIREBALL_V2_DESIGN_REPORT.md section 6); nothing is taken from any other game or mod.

usage: python3 render_storyboard.py <out_dir>
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, H = 960, 540
FOV = math.radians(70)
R = 6.0  # blast radius in blocks (Totality: 6 blocks, D&D: 20-ft-radius sphere)

# Temperature ramp (design report palette): soot -> deep red -> orange -> gold -> white-hot
RAMP_T = np.array([0.00, 0.22, 0.45, 0.68, 0.86, 1.00])
RAMP_C = np.array([[0.10, 0.06, 0.05], [0.45, 0.07, 0.03], [0.85, 0.20, 0.04],
                   [1.00, 0.48, 0.10], [1.00, 0.76, 0.23], [1.00, 0.97, 0.85]])


def ramp(t):
    t = np.clip(t, 0, 1)
    out = np.empty(t.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(t, RAMP_T, RAMP_C[:, c])
    return out


# ── 3D value noise / fbm ─────────────────────────────────────────────────────
def _hash(ix, iy, iz):
    h = (ix * 374761393 + iy * 668265263 + iz * 1274126177) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) / 4294967295.0


def vnoise(p):
    i = np.floor(p).astype(np.int64)
    f = p - i
    u = f * f * (3 - 2 * f)
    n = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (u[..., 0] if dx else 1 - u[..., 0]) * (u[..., 1] if dy else 1 - u[..., 1]) * (u[..., 2] if dz else 1 - u[..., 2])
                n = n + w * _hash(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz)
    return n


def fbm(p, octaves=4):
    s, a, tot = 0.0, 0.5, 0.0
    for _ in range(octaves):
        s = s + a * vnoise(p)
        tot += a
        p = p * 2.03 + 17.1
        a *= 0.5
    return s / tot


# ── Camera / scene ───────────────────────────────────────────────────────────
class Cam:
    def __init__(self, pos, target):
        self.pos = np.array(pos, float)
        f = np.array(target, float) - self.pos
        self.f = f / np.linalg.norm(f)
        self.r = np.cross([0, 1, 0], self.f); self.r /= np.linalg.norm(self.r)
        self.u = np.cross(self.f, self.r)
        th = math.tan(FOV / 2)
        xs = (np.arange(W) + 0.5) / W * 2 - 1
        ys = 1 - (np.arange(H) + 0.5) / H * 2
        X, Y = np.meshgrid(xs * th * W / H, ys * th)
        d = self.f + X[..., None] * self.r + Y[..., None] * self.u
        self.dirs = d / np.linalg.norm(d, axis=-1, keepdims=True)

    def project(self, p):
        v = np.asarray(p, float) - self.pos
        z = v @ self.f
        th = math.tan(FOV / 2)
        x = (v @ self.r) / z / (th * W / H)
        y = (v @ self.u) / z / th
        return (x + 1) / 2 * W, (1 - y) / 2 * H, z


def background(cam, night=False, wall_z=None):
    d = cam.dirs
    sky_top = np.array([0.05, 0.06, 0.14]) if night else np.array([0.42, 0.60, 0.95])
    sky_hor = np.array([0.10, 0.11, 0.20]) if night else np.array([0.72, 0.82, 0.98])
    t = np.clip(d[..., 1] * 2.5, 0, 1)[..., None]
    img = sky_hor * (1 - t) + sky_top * t
    depth = np.full((H, W), np.inf)
    # floor y = 0: stone-brick-like grid
    with np.errstate(divide='ignore', invalid='ignore'):
        tg = -cam.pos[1] / d[..., 1]
    hit = (d[..., 1] < 0) & (tg > 0)
    p = cam.pos + d * tg[..., None]
    gx, gz = np.mod(p[..., 0], 1.0), np.mod(p[..., 2], 1.0)
    edge = (gx < 0.05) | (gz < 0.05)
    base = 0.50 + 0.06 * _hash(np.floor(p[..., 0]).astype(np.int64), 7, np.floor(p[..., 2]).astype(np.int64))
    floor = np.stack([base, base, base * 1.02], -1) * np.where(edge, 0.72, 1.0)[..., None]
    if night:
        floor *= 0.28
    fog = np.clip(tg / 90, 0, 1)[..., None]
    floor = floor * (1 - fog) + sky_hor * fog
    img = np.where(hit[..., None], floor, img)
    depth = np.where(hit, tg, depth)
    floor_pt = np.where(hit[..., None], p, np.nan)
    if wall_z is not None:  # a deepslate wall facing the camera, x in [-9, 9], y in [0, 7]
        with np.errstate(divide='ignore', invalid='ignore'):
            tw = (wall_z - cam.pos[2]) / d[..., 2]
        pw = cam.pos + d * tw[..., None]
        wh = (tw > 0) & (np.abs(pw[..., 0]) < 9) & (pw[..., 1] > 0) & (pw[..., 1] < 7) & (tw < depth)
        bx, by = np.mod(pw[..., 0], 1.0), np.mod(pw[..., 1], 1.0)
        wb = 0.22 + 0.05 * _hash(np.floor(pw[..., 0]).astype(np.int64), np.floor(pw[..., 1]).astype(np.int64), 3)
        wall = np.stack([wb, wb, wb * 1.08], -1) * np.where((bx < 0.05) | (by < 0.05), 0.7, 1.0)[..., None]
        if night:
            wall *= 0.35
        img = np.where(wh[..., None], wall, img)
        depth = np.where(wh, tw, depth)
    return img, depth, floor_pt


def box(cam, img, depth, lo, hi, color):
    """Axis-aligned box (slab test), flat shaded per face; used for the stand-in figure."""
    d = cam.dirs
    with np.errstate(divide='ignore', invalid='ignore'):
        t1 = (np.array(lo) - cam.pos) / d
        t2 = (np.array(hi) - cam.pos) / d
    tn = np.max(np.minimum(t1, t2), -1)
    tf = np.min(np.maximum(t1, t2), -1)
    hit = (tf > tn) & (tn > 0) & (tn < depth)
    axis = np.argmax(np.minimum(t1, t2), -1)
    shade = np.choose(axis, [0.8, 1.0, 0.65])
    img[hit] = np.array(color) * shade[hit][..., None]
    depth[hit] = tn[hit]


def figure(cam, img, depth, at, facing=1.0):
    x, z = at
    box(cam, img, depth, (x - 0.25, 0, z - 0.12), (x + 0.25, 0.75, z + 0.12), (0.22, 0.24, 0.36))   # legs
    box(cam, img, depth, (x - 0.25, 0.75, z - 0.12), (x + 0.25, 1.5, z + 0.12), (0.35, 0.18, 0.45))  # robe
    box(cam, img, depth, (x - 0.22, 1.5, z - 0.22), (x + 0.22, 1.95, z + 0.22), (0.72, 0.55, 0.42))  # head
    box(cam, img, depth, (x + 0.25, 1.3, z - 0.1), (x + 0.45, 1.45, z + 0.1 + 0.55 * facing), (0.35, 0.18, 0.45))  # casting arm


# ── Effects ──────────────────────────────────────────────────────────────────
def shell(cam, depth, centre, radius, temp, erosion, t_anim, steps=28, core=0.0, fade=1.0):
    """Ray-marched explosion shell. temp: 0..1 overall heat; erosion: 0 (solid) .. 1 (fully broken up).
    Returns emitted RGB (additive) and occlusion alpha (smoke darkening)."""
    c = np.array(centre, float)
    d = cam.dirs
    oc = cam.pos - c
    b = np.einsum('ijk,k->ij', d, oc)
    cc = oc @ oc - (radius * 1.12) ** 2
    disc = b * b - cc
    m = disc > 0
    em = np.zeros((H, W, 3))
    alpha = np.zeros((H, W))
    if not m.any():
        return em, alpha
    sq = np.sqrt(np.where(m, disc, 0))
    t0 = np.maximum(-b - sq, 0.05)
    t1 = np.minimum(-b + sq, depth)       # depth test: the world in front clips the shell (reversed-Z in game)
    m &= t1 > t0
    idx = np.nonzero(m)
    dd = d[idx]
    a0, a1 = t0[idx], t1[idx]
    acc = np.zeros((len(a0), 3))
    trans = np.ones(len(a0))
    dt = (a1 - a0) / steps
    for s in range(steps):
        tt = a0 + (s + 0.5) * dt
        p = cam.pos + dd * tt[:, None]
        q = (p - c) / radius
        rr = np.linalg.norm(q, axis=1)
        n = fbm(q * 3.4 + np.array([0, -t_anim * 0.9, t_anim * 0.4]), 4)
        n2 = fbm(q * 6.0 + t_anim * 1.7 + 5.0, 2)
        # shell profile: dense near r ~ 0.85..1.0, ragged boundary from noise, hollowing as it ages
        edge = 1.0 + 0.10 * (n - 0.5)
        inner = 0.35 + 0.45 * erosion
        prof = np.clip((edge - rr) / 0.12, 0, 1) * np.clip((rr - inner * (0.6 + 0.4 * n)) / 0.25, 0, 1)
        dens = prof * np.clip(((n * 0.7 + n2 * 0.3) - 0.36) * 2.6 - erosion * 0.9 + 0.35, 0, 1) * 2.6 * fade
        if core > 0:
            dens = dens + core * np.clip(1 - rr / 0.45, 0, 1) * 3.0
        heat = np.clip(temp * (0.32 + 0.55 * n2 + 0.40 * (1 - rr)) + core * np.clip(1 - rr / 0.5, 0, 1)
                       - 0.25 * erosion * rr, 0, 1)
        col = ramp(heat) * (0.20 + 1.50 * heat[:, None] ** 2.0)
        k = dens * dt * 0.75
        acc += trans[:, None] * col * k[:, None]
        trans *= np.exp(-k * (0.35 + 0.65 * erosion))
    em[idx] = acc
    alpha[idx] = (1 - trans) * (0.55 + 0.30 * erosion)   # dense flame and, later, soot hide what is behind
    return em, alpha


def glow_point(cam, depth, p, radius_px, color, strength=1.0):
    x, y, z = cam.project(p)
    if z <= 0:
        return np.zeros((H, W, 3))
    yy, xx = np.mgrid[0:H, 0:W]
    r2 = ((xx - x) ** 2 + (yy - y) ** 2) / max(radius_px, 1) ** 2
    vis = 1.0 if (0 <= int(y) < H and 0 <= int(x) < W and depth[int(y), int(x)] >= z - 0.5) else 0.0
    return np.exp(-r2)[..., None] * np.array(color) * strength * vis


def streak(cam, depth, head, tail_len, direction, width, t_anim):
    """Bead + tail as a ribbon: emission by closest distance of each view ray to the segment."""
    dvec = np.array(direction, float); dvec /= np.linalg.norm(dvec)
    a = np.array(head, float)
    b = a - dvec * tail_len
    d = cam.dirs
    u = b - a
    w0 = cam.pos - a
    aa = np.einsum('ijk,ijk->ij', d, d); bb = np.einsum('ijk,k->ij', d, u); cc = u @ u
    dd_ = np.einsum('ijk,k->ij', d, w0); ee = u @ w0
    den = aa * cc - bb * bb
    sc = np.clip((bb * ee - cc * dd_) / np.where(np.abs(den) < 1e-9, 1e-9, den), 0, None)
    tc = np.clip((aa * ee - bb * dd_) / np.where(np.abs(den) < 1e-9, 1e-9, den), 0, 1)
    pr = cam.pos + d * sc[..., None]
    ps = a + u * tc[..., None]
    dist = np.linalg.norm(pr - ps, axis=-1)
    occl = sc < depth
    s = tc  # 0 at head .. 1 at tail end
    wid = width * (1 - 0.75 * s)
    n = fbm(np.stack([s * 9 - t_anim * 6, dist * 3, np.full_like(s, t_anim)], -1), 3)
    core = np.exp(-(dist / (wid * 0.35)) ** 2)
    body = np.exp(-(dist / wid) ** 2) * (0.6 + 0.8 * n)
    fade = (1 - s) ** 1.4
    heat = np.clip(core * 1.1 + body * 0.5, 0, 1) * (0.55 + 0.45 * (1 - s))
    em = ramp(heat) * ((core * 2.2 + body * 0.9) * fade * occl)[..., None]
    return em


def embers(cam, depth, pts, color, size=1.6, strength=1.0):
    em = np.zeros((H, W, 3))
    yy, xx = np.mgrid[0:H, 0:W]
    for p, s in pts:
        x, y, z = cam.project(p)
        if z <= 0.3 or not (0 <= x < W and 0 <= y < H) or depth[int(y), int(x)] < z:
            continue
        rad = max(size * s * 30 / z, 0.8)
        x0, x1, y0, y1 = int(max(x - 4 * rad, 0)), int(min(x + 4 * rad + 1, W)), int(max(y - 4 * rad, 0)), int(min(y + 4 * rad + 1, H))
        g = np.exp(-(((xx[y0:y1, x0:x1] - x) ** 2 + (yy[y0:y1, x0:x1] - y) ** 2) / rad ** 2))
        em[y0:y1, x0:x1] += g[..., None] * np.array(color) * strength
    return em


def ground_ring(floor_pt, centre, radius, height_above, strength, scorch, t_anim):
    """Where the sphere meets the floor: a hot rim at the true damage boundary + a darkened scorch disc."""
    if height_above >= radius:
        return np.zeros((H, W, 3)), np.zeros((H, W))
    rg = math.sqrt(radius ** 2 - height_above ** 2)
    dx = np.nan_to_num(floor_pt[..., 0] - centre[0], nan=1e4)
    dz = np.nan_to_num(floor_pt[..., 2] - centre[2], nan=1e4)
    r = np.sqrt(dx * dx + dz * dz)
    ang = np.arctan2(dz, dx)
    n = fbm(np.stack([np.cos(ang) * 3, np.sin(ang) * 3, np.full_like(r, t_anim)], -1), 3)
    rim = np.exp(-((r - rg) / 0.28) ** 2) * (0.6 + 0.7 * n)
    em = ramp(np.clip(rim * 0.9, 0, 1)) * (rim * strength)[..., None]
    dark = np.clip(1 - r / (rg * 0.95), 0, 1) ** 0.6 * scorch * (0.6 + 0.4 * n)
    return em, dark


def bloom(em, strength=0.9):
    """Cheap imitation of the Emissive Rendering Layer bloom: a few downsampled box blurs added together."""
    out = np.zeros_like(em)
    img = Image.fromarray(np.clip(em / 4 * 255, 0, 255).astype(np.uint8))
    for k, wgt in ((4, 0.5), (8, 0.35), (16, 0.25), (32, 0.18)):
        small = img.resize((W // k, H // k), Image.BILINEAR).resize((W, H), Image.BILINEAR)
        out += np.asarray(small, float) / 255 * 4 * wgt
    return out * strength


def compose(base, em, smoke_alpha=None, smoke_col=(0.10, 0.06, 0.05), flash=0.0):
    img = base.copy()
    if smoke_alpha is not None:
        img = img * (1 - smoke_alpha[..., None]) + np.array(smoke_col) * smoke_alpha[..., None]
    tot = em + bloom(em, 0.6)
    img = img + tot
    img = 1 - np.exp(-img * 1.15)          # soft shoulder (as the layer's composite does)
    img = img + flash
    return Image.fromarray(np.clip(img * 255, 0, 255).astype(np.uint8))


# ── Frames ───────────────────────────────────────────────────────────────────
def font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def label(im, title, lines, tag="CONCEPT MOCK-UP - procedural, not in-game, not final art"):
    dr = ImageDraw.Draw(im, 'RGBA')
    dr.rectangle((0, 0, W, 30), fill=(0, 0, 0, 170))
    dr.text((10, 6), title, fill=(255, 220, 160), font=font(18))
    y = H - 14 - 18 * len(lines)
    dr.rectangle((0, y - 8, W, H), fill=(0, 0, 0, 150))
    for i, l in enumerate(lines):
        dr.text((10, y + 18 * i), l, fill=(235, 235, 235), font=font(14))
    dr.text((W - 10, 8), tag, fill=(255, 120, 120), font=font(12), anchor="ra")
    return im


def rng_points(seed, n, centre, spread, up=0.0):
    r = np.random.default_rng(seed)
    pts = []
    for _ in range(n):
        v = r.normal(size=3); v /= np.linalg.norm(v)
        pts.append((np.array(centre) + v * spread * r.random() ** 0.5 + np.array([0, up * r.random(), 0]), 0.5 + r.random()))
    return pts


def frame_cast(out):
    cam = Cam((-5.5, 2.4, 1.0), (1.5, 1.5, 9.0))
    img, depth, floor = background(cam)
    figure(cam, img, depth, (0.0, 2.0))
    origin = np.array([0.35, 1.38, 2.75])
    head = origin + np.array([0.15, 0.03, 2.6])
    em = glow_point(cam, depth, origin, 14, (1.0, 0.8, 0.45), 1.6)             # launch flash at the hand
    em += embers(cam, depth, rng_points(3, 26, origin + [0, 0.1, 0.3], 0.7, up=0.4), (1.0, 0.55, 0.15), 0.9, 1.2)
    em += streak(cam, depth, head, 2.3, (0.06, 0.01, 1), 0.16, 0.4)
    em += glow_point(cam, depth, head, 9, (1.0, 0.92, 0.7), 2.4)
    label(compose(img, em), "1  Casting + launch (t = 0 to 0.15 s)",
          ["A short, bright ignition at the casting hand (0.15 s), sparks thrown forwards, and the bead leaving at",
           "full speed. No new arm animation (the Player Animation API is out of scope). Emissive: hand flash + bead."]).save(out)


def frame_trail(out):
    cam = Cam((-3.2, 2.0, 6.0), (1.0, 2.0, 10.0))
    img, depth, floor = background(cam)
    head = np.array([2.4, 2.1, 11.0])
    dirv = np.array([0.45, 0.0, 1.0])
    em = streak(cam, depth, head, 3.6, dirv, 0.22, 1.3)
    em += glow_point(cam, depth, head, 11, (1.0, 0.95, 0.8), 2.8)
    r = np.random.default_rng(9)
    trail = []
    dn = dirv / np.linalg.norm(dirv)
    for i in range(40):
        s = r.random() ** 0.7 * 5.5
        trail.append((head - dn * s + r.normal(size=3) * 0.12 * (1 + s * 0.4) + [0, 0.05 * s, 0], 0.4 + 0.5 * r.random()))
    em += embers(cam, depth, trail, (1.0, 0.5, 0.12), 0.7, 1.4)
    label(compose(img, em), "2  Projectile trail (in flight, 1.2 blocks/tick)",
          ["White-hot bead (0.3 blocks) with a gold-orange tail ribbon about 3.5 blocks long, flowing noise, tapering",
           "to red; ember motes shed behind it, cooling and drifting up. Min/max on-screen width from Heat Vision V2."]).save(out)


def explosion_frame(out, title, lines, tk, cam_pos=(0.0, 2.6, -4.0), centre=(0.0, 1.0, 14.0), wall=None,
                    night=False, show_figures=True, floor_y_under=0.0):
    cam = Cam(cam_pos, (centre[0], max(centre[1], 2.0), centre[2]))
    img, depth, floor = background(cam, night=night, wall_z=wall)
    if show_figures:
        figure(cam, img, depth, (centre[0] - 4.2, centre[2] - 1.5), -1)    # inside the radius
        figure(cam, img, depth, (centre[0] + 7.6, centre[2] - 6.0), -1)    # outside the radius (7.6 blocks to the side)
    # Timeline (ticks at 20 tps): flash 0-2, blossom 0-6 (ease-out), peak 6-12, breakup 12-24, smoke after
    s = min(tk / 6.0, 1.0)
    grow = 1 - (1 - s) ** 3
    radius = R * (0.18 + 0.82 * grow) * (1 + 0.06 * max(0, tk - 6) / 18)
    temp = 1.0 if tk < 4 else max(0.2, 1.0 - (tk - 4) / 22)
    erosion = 0.0 if tk < 10 else min(1.0, (tk - 10) / 16)
    core = max(0.0, 1 - tk / 7)
    fade = 1.0 if tk < 20 else max(0.0, 1 - (tk - 20) / 10)
    em, sm = shell(cam, depth, centre, radius, temp, erosion, tk * 0.05, core=core, fade=fade) if fade > 0 else \
        (np.zeros((H, W, 3)), np.zeros((H, W)))
    flash = 0.0
    if tk <= 2:   # point flash: small and brief; the screen flash is a capped Shared Screen FX contribution
        em += glow_point(cam, depth, centre, (22 + 10 * tk) * W / 960, (1.0, 0.92, 0.72), 1.4 - 0.4 * tk)
        flash = 0.05 * (1 - tk / 3)
    h = centre[1] - floor_y_under
    ring_strength = (1.4 if 4 <= tk <= 14 else 0.6 if tk < 4 else max(0.0, 1.4 - (tk - 14) / 10)) * (0.9 if tk >= 2 else 0)
    gr, dark = ground_ring(floor, centre, radius, h, ring_strength, min(1.0, tk / 10) * 0.55, tk * 0.05)
    img = img * (1 - dark[..., None] * 0.7)
    em += gr
    if tk >= 6:
        n_emb = 70 if tk < 24 else max(0, int(30 - (tk - 24) * 0.6))
        em += embers(cam, depth, rng_points(11 + tk, n_emb, centre, radius * 1.05, up=min(4.0, tk / 10)),
                     (1.0, 0.45, 0.1), 0.8, 1.2 if tk < 24 else 0.5)
    smoke = sm
    if tk >= 18:  # dispersing smoke puffs (particles), cosmetic only
        smoke = np.clip(sm + _smoke_mask(cam, depth, centre, tk), 0, 0.8)
    label(compose(img, em, smoke, flash=flash), title, lines).save(out)


def _smoke_mask(cam, depth, centre, tk):
    """Smoke puffs as soft discs (stand-in for smoke particles) rising and thinning out."""
    r = np.random.default_rng(5)
    yy, xx = np.mgrid[0:H, 0:W]
    a = np.zeros((H, W))
    life = min(1.0, (tk - 18) / 10) * max(0.0, 1 - (tk - 18) / 70)
    for _ in range(26):
        v = r.normal(size=3); v[1] = abs(v[1]) * 0.6; v /= np.linalg.norm(v)
        p = np.array(centre) + v * R * (0.3 + 0.6 * r.random()) * (1 + (tk - 18) / 80) + [0, (tk - 18) * 0.09 + r.random(), 0]
        x, y, z = cam.project(p)
        if z <= 0.5 or depth[int(np.clip(y, 0, H - 1)), int(np.clip(x, 0, W - 1))] < z:
            continue
        rad = (1.3 + 0.8 * r.random()) * 300 / z * (1 + (tk - 18) / 60) * W / 960
        a += np.exp(-(((xx - x) ** 2 + (yy - y) ** 2) / rad ** 2)) * 0.16 * life
    return a


def timeline_strip(out, ticks, cam_pos=(0.0, 2.6, -4.0), centre=(0.0, 1.0, 14.0)):
    global W, H
    W0, H0 = W, H
    W, H = 480, 270
    tiles = []
    try:
        for tk in ticks:
            p = out + f".tmp_{tk}.png"
            explosion_frame(p, "", [], tk, cam_pos, centre, show_figures=True)
            tiles.append((tk, Image.open(p).copy()))
            os.remove(p)
    finally:
        W, H = W0, H0
    cols = 4
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * 480, rows * 292), 'black')
    dr = ImageDraw.Draw(sheet)
    for k, (tk, im) in enumerate(tiles):
        x, y = (k % cols) * 480, (k // cols) * 292
        sheet.paste(im.crop((0, 0, 480, 270)), (x, y + 22))
        dr.text((x + 6, y + 3), f"tick {tk}  ({tk / 20:.2f} s)", fill=(255, 220, 160), font=font(15))
    dr.text((cols * 480 - 8, rows * 292 - 16), "CONCEPT MOCK-UP - procedural, not in-game", fill=(255, 120, 120), font=font(12), anchor="ra")
    sheet.save(out)


def main():
    if len(sys.argv) > 2 and sys.argv[2] == "peak":
        explosion_frame(f"{sys.argv[1]}/04_maximum_explosion.png", "4", [], 8); return
    out = sys.argv[1] if len(sys.argv) > 1 else "storyboard"
    os.makedirs(out, exist_ok=True)
    frame_cast(f"{out}/01_casting_and_launch.png")
    frame_trail(f"{out}/02_projectile_trail.png")
    explosion_frame(f"{out}/03_early_explosion.png", "3  Early explosion: flash + blossom (tick 2, 0.10 s)",
                    ["Detonation at the true impact point: a white-gold point flash (2 ticks, capped screen flash) and the",
                     "shell bursting outwards, ease-out, reaching the full 6-block radius at tick 6. Damage is applied at tick 0."], 2)
    explosion_frame(f"{out}/04_maximum_explosion.png", "4  Maximum explosion (tick 8, 0.40 s): the damage sphere",
                    ["Full 6-block sphere; hot ragged rim = the damage boundary. Where it meets the floor, a hot ring marks",
                     "the area on the ground. Figure left: inside (takes damage). Figure right: 7.6 blocks out (safe)."], 8)
    explosion_frame(f"{out}/05_dissipation_aftermath.png", "5  Dissipation + aftermath (tick 40, 2.0 s)",
                    ["Shell cooled to red and broken up into soot; smoke puffs rise and thin out, a few embers drift. No",
                     "orange glow after tick 24: cosmetic only, nothing implies damage. Real fire blocks are gameplay."], 40)
    explosion_frame(f"{out}/06_wall_impact_peak.png", "6  Wall impact (tick 8): the sphere is centred on the impact point",
                    ["Against a wall the shell is clipped by the wall's depth, so it reads as a half-sphere bulging out",
                     "of the impact; the floor ring shows the reach on the ground. No billboard cut-off at the wall."], 8,
                    cam_pos=(-10.0, 3.0, 4.0), centre=(0.0, 1.2, 17.75), wall=18.0)
    explosion_frame(f"{out}/07_air_burst_peak.png", "7  Air burst (tick 8): a full sphere, no ground ring",
                    ["Hitting a flying target or a tall creature high above the floor: the whole sphere is visible.",
                     "The ring appears only where the sphere actually reaches the floor (here it does not)."], 8,
                    centre=(0.0, 8.5, 18.0), show_figures=False)
    explosion_frame(f"{out}/08_night_peak.png", "8  Night (tick 8): emissive glow lights the scene",
                    ["Same timing at night. The Emissive Rendering Layer gives the halo; brightness is capped per",
                     "explosion so 20 at once stay readable (global budget = approval item)."], 8, night=True)
    timeline_strip(f"{out}/09_explosion_timeline.png", [0, 2, 4, 6, 8, 12, 18, 24, 30, 40, 55, 70])
    print("frames written to", out)


if __name__ == "__main__":
    main()
