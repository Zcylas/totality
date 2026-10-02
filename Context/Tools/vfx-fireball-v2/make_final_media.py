#!/usr/bin/env python3
"""Fireball V2 final review media (B3-B5) from a REAL-CLIENT capture run of scene 69's final set
(-Dtotality.fireball.v2.final=true, prefix FB2_).

One frame per game tick: normal GIFs play at real time (20 fps); the projectile slow motion plays the same frames at
5 fps (0.25x); the explosion slow motion was captured with the development 0.25x explosion clock and plays at 20 fps.

usage: python3 make_final_media.py <run_dir> <out_dir> <tag> [--sheets-only]
"""
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

run, out, tag = sys.argv[1:4]
SHEETS_ONLY = '--sheets-only' in sys.argv
os.makedirs(out, exist_ok=True)


def font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


FX = {}
for line in open(f'{run}/hologram-capture.log', encoding='utf-8', errors='replace'):
    m = re.match(r'fx: FB2_(\S+)_(\d\d) explosions=(\d+)', line)
    if m:
        FX[(m.group(1), int(m.group(2)))] = int(m.group(3))


def frame(seq, i):
    p = f'{run}/screenshots/notification_v2_FB2_{seq}_{i:02d}.png'
    return Image.open(p).convert('RGB') if os.path.exists(p) else None


def detonation(seq):
    """First frame with a live V2 explosion; V1 sequences use the matching V2 sequence (same cast, same flight)."""
    key = seq.replace('v1_', 'v2_', 1)
    for i in range(100):
        if FX.get((key, i), 0) > 0:
            return i
    return 0


def sheet(name, rows, w=360, label_px=18, crop=None):
    cols = max(len(r[1]) for r in rows)
    cw, ch = (crop[2] - crop[0], crop[3] - crop[1]) if crop else (1880, 1052)
    h = w * ch // cw
    img = Image.new('RGB', (cols * w + 150, len(rows) * (h + label_px)), (16, 16, 18))
    dr = ImageDraw.Draw(img)
    for r, (rlabel, tiles) in enumerate(rows):
        y = r * (h + label_px)
        dr.text((6, y + label_px + h // 2 - 8), rlabel, fill=(255, 220, 160), font=font(14))
        for c, (tlabel, im) in enumerate(tiles):
            x = 150 + c * w
            if im is not None:
                img.paste((im.crop(crop) if crop else im).resize((w, h)), (x, y + label_px))
            dr.text((x + 4, y + 2), tlabel, fill=(230, 230, 230), font=font(12))
    dr.text((img.width - 8, img.height - 16), f'REAL CLIENT {tag}', fill=(140, 255, 140), font=font(12), anchor='ra')
    img.save(f'{out}/{name}.jpg', quality=88)


def row(seq, label, offsets):
    """Frames relative to the detonation (negative = projectile in flight)."""
    d = detonation(seq)
    tiles = []
    for o in offsets:
        t = 'impact' if o == 0 else f'{o * 0.05:+.2f} s'
        tiles.append((t, frame(seq, d + o)))
    return (label, tiles)


FLIGHT = [-12, -8, -4, -1, 0, 1, 3, 6, 9, 14, 20, 30, 45, 60]
sheet('01_v1_v2_whole_spell_side', [row('v1_spell_side', 'V1 side', FLIGHT), row('v2_spell_side', 'V2 side', FLIGHT)])
sheet('02_v1_v2_whole_spell_third_person', [row('v1_spell_third_person', 'V1 third person', FLIGHT),
                                            row('v2_spell_third_person', 'V2 third person', FLIGHT)])
FP = [-18, -14, -10, -6, -3, -1, 0, 2, 5, 10]
sheet('03_v1_v2_first_person', [row('v1_first_person', 'V1 first person', FP), row('v2_first_person', 'V2 first person', FP)])
NIGHT = [-6, -2, 0, 1, 2, 4, 7, 12, 20, 40]
sheet('04_night_v1_v2_layer_off', [row('v1_night_side', 'V1 night', NIGHT), row('v2_night_side', 'V2 night', NIGHT),
                                   row('v2_night_side_layer_off', 'V2 night,\nemissive\nlayer off', NIGHT)])
ENV = [-4, 0, 1, 3, 6, 10, 18, 30, 50]
sheet('05_environments', [row('v2_far', 'far (36 blocks)', ENV), row('v2_air', 'airborne', ENV),
                          row('v2_camera_near_7', 'camera 7 blocks', ENV), row('v2_camera_inside_3', 'camera inside\n(3 blocks)', ENV),
                          row('v2_screenfx_off', 'Screen FX off', ENV)])
sheet('06_multiple', [row('v2_multi_flight', '5 in flight', [-10, -6, -3, 0, 2, 5, 10, 20, 40]),
                      row('v2_twenty', '20 at once', [-6, -2, 0, 2, 5, 10, 20, 35, 55])])
sheet('07_midflight_sighting', [('first seen\nmid-flight', [(f'+{i * 0.05:.2f} s', frame('v2_midflight_sighting', i)) for i in range(0, 10)])])

# Full-resolution day / night stills of the whole spell (V2)
for seq, tag2 in [('v2_spell_side', 'day'), ('v2_night_side', 'night')]:
    d = detonation(seq)
    for o, name in [(-6, 'flight'), (0, 'impact'), (2, 'detonation_0.10s'), (7, 'maximum_0.35s'), (18, 'cooling_0.90s'),
                    (30, 'breakup_smoke_1.50s'), (50, 'aftermath_2.50s')]:
        im = frame(seq, d + o)
        if im is not None:
            im.save(f'{out}/full_{tag2}_{name}.jpg', quality=90)

if SHEETS_ONLY:
    print('sheets written to', out)
    sys.exit(0)


def gif(name, seqs, start, n, size=(448, 252), duration=50, crop=None):
    ims = []
    for k in range(n):
        tiles = []
        for seq, label in seqs:
            im = frame(seq, start(seq) + k)
            if im is None:
                break
            tiles.append(((im.crop(crop) if crop else im).resize(size), label))
        if len(tiles) < len(seqs):
            break
        w, h = size
        canvas = Image.new('RGB', (len(tiles) * (w + 4) - 4, h + 20), (16, 16, 18))
        dr = ImageDraw.Draw(canvas)
        for j, (t, label) in enumerate(tiles):
            canvas.paste(t, (j * (w + 4), 20))
            dr.text((j * (w + 4) + 6, 3), label, fill=(255, 220, 160), font=font(14))
        ims.append(canvas.convert('P', palette=Image.ADAPTIVE, colors=128))
    if ims:
        ims[0].save(f'{out}/{name}.gif', save_all=True, append_images=ims[1:], duration=duration, loop=0)


def from_start(seq):
    return 0


# The complete spell at real speed: cast, launch, flight, impact, detonation, maximum, cooling, aftermath, cleanup.
gif('anim_complete_spell_v2_side', [('v2_spell_side', 'Fireball V2 - complete spell, real speed (side)')], from_start, 80, size=(640, 360))
gif('anim_complete_spell_v2_third_person', [('v2_spell_third_person', 'Fireball V2 - third person, real speed')], from_start, 92,
    size=(640, 360))
gif('anim_v1_v2_side', [('v1_spell_side', 'V1'), ('v2_spell_side', 'V2')], from_start, 80)
gif('anim_v1_v2_first_person', [('v1_first_person', 'V1 first person'), ('v2_first_person', 'V2 first person')], from_start, 40)
gif('anim_v1_v2_night', [('v1_night_side', 'V1 night'), ('v2_night_side', 'V2 night')], from_start, 80)
# Slow motion: the projectile's flight and impact (one tick per frame at 5 fps = 0.25x), the explosion at 0.25x.
gif('anim_slowmo_projectile_0.25x', [('v2_spell_side', 'V2 projectile, 0.25x')], lambda s: max(0, detonation(s) - 14), 22,
    size=(640, 360), duration=200)
gif('anim_slowmo_explosion_0.25x', [('v2_slowmo_explosion', 'V2 explosion, 0.25x (development clock)')], from_start, 64,
    size=(640, 360))
gif('anim_camera_inside', [('v2_camera_inside_3', 'camera inside (3 blocks)')], lambda s: max(0, detonation(s) - 3), 60, size=(512, 288))
gif('anim_multiple', [('v2_multi_flight', '5 in flight'), ('v2_twenty', '20 at once')], from_start, 60)
print('media written to', out)
