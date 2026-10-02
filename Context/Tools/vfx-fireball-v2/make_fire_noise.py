#!/usr/bin/env python3
"""Fireball V2 (B5): the precomputed fire noise texture used by core/vfx_fire.fsh.

The explosion shader used to evaluate four 4-octave value-noise fbm fields per pixel (16 value-noise lookups, 64
hashes). This bakes the same kind of field into a tileable texture so the shader needs about three texture fetches:

  R, G, B  three independent 4-octave value-noise fbm fields, each exactly the shader's fbm():
           value noise on an integer lattice with smoothstep interpolation, octaves at x2 frequency with x0.5 amplitude,
           normalised by the sum of amplitudes (0.9375)
  A        255

Tileable: lattice period 16 at the base octave (32 px per lattice unit), so the shader samples fbm(p) as
texture(Sampler0, p / 16). Deterministic (fixed seed).

usage: python3 make_fire_noise.py <out.png>
"""
import sys

import numpy as np
from PIL import Image

SIZE = 512
PERIOD = 16
OCTAVES = 4


def value_noise(lattice):
    """Value noise of a periodic lattice (n x n random values) sampled on a SIZE x SIZE grid, smoothstep-interpolated."""
    n = lattice.shape[0]
    coords = np.arange(SIZE) * n / SIZE
    i0 = np.floor(coords).astype(int)
    f = coords - i0
    u = f * f * (3.0 - 2.0 * f)
    i1 = (i0 + 1) % n
    x0, x1, ux = i0[None, :], i1[None, :], u[None, :]
    y0, y1, uy = i0[:, None], i1[:, None], u[:, None]
    a = lattice[y0, x0]
    b = lattice[y0, x1]
    c = lattice[y1, x0]
    d = lattice[y1, x1]
    return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy


def fbm(rng):
    total = np.zeros((SIZE, SIZE))
    amplitude, period = 0.5, PERIOD
    for _ in range(OCTAVES):
        total += amplitude * value_noise(rng.random((period, period)))
        amplitude *= 0.5
        period *= 2
    return total / 0.9375


def main():
    out = sys.argv[1]
    rng = np.random.default_rng(20261002)
    channels = [fbm(rng) for _ in range(3)]
    rgba = np.stack(channels + [np.ones((SIZE, SIZE))], axis=-1)
    Image.fromarray(np.clip(np.round(rgba * 255), 0, 255).astype(np.uint8), 'RGBA').save(out)
    for k, ch in enumerate(channels):
        print(f'channel {"RGB"[k]}: mean {ch.mean():.3f}, min {ch.min():.3f}, max {ch.max():.3f}')
    print('written', out)


if __name__ == '__main__':
    main()
