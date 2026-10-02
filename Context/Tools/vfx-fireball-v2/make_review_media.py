#!/usr/bin/env python3
"""Fireball V2 B1-B2 review media from REAL-CLIENT capture frames (scene 69, prefix FB2_).

Builds contact sheets and animated GIFs (one frame per game tick = 20 fps real time) from
build/vfx-glow-capture-<run>/screenshots, using the per-frame "fx:" log lines to find the detonation frame.

usage: python3 make_review_media.py <screenshots_dir> <hologram-capture.log> <out_dir> <tag>
"""
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

shots, log, out, tag = sys.argv[1:5]
os.makedirs(out, exist_ok=True)


def font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


FX = {}
for line in open(log, encoding='utf-8', errors='replace'):
    m = re.match(r'fx: FB2_(\S+)_(\d\d) explosions=(\d+) elements=(\d+)', line)
    if m:
        FX[(m.group(1), int(m.group(2)))] = (int(m.group(3)), int(m.group(4)))


def frame(seq, i):
    p = f'{shots}/notification_v2_FB2_{seq}_{i:02d}.png'
    return Image.open(p).convert('RGB') if os.path.exists(p) else None


def detonation(seq, ref=None):
    """First frame with a live V2 explosion (V1 sequences use the matching V2 sequence's index)."""
    key = ref or seq
    for i in range(64):
        if FX.get((key, i), (0, 0))[0] > 0:
            return i
    return 0


def sheet(name, rows, w=480, label_px=18):
    """rows: list of (row label, [(tile label, image)])"""
    cols = max(len(r[1]) for r in rows)
    h = w * 1080 // 1920
    img = Image.new('RGB', (cols * w + 150, len(rows) * (h + label_px)), (16, 16, 18))
    dr = ImageDraw.Draw(img)
    for r, (rlabel, tiles) in enumerate(rows):
        y = r * (h + label_px)
        dr.text((6, y + label_px + h // 2 - 8), rlabel, fill=(255, 220, 160), font=font(15))
        for c, (tlabel, im) in enumerate(tiles):
            x = 150 + c * w
            if im is not None:
                img.paste(im.resize((w, h)), (x, y + label_px))
            dr.text((x + 4, y + 2), tlabel, fill=(230, 230, 230), font=font(13))
    dr.text((img.width - 8, img.height - 16), f'REAL CLIENT ({tag})', fill=(140, 255, 140), font=font(12), anchor='ra')
    img.save(f'{out}/{name}.png')


def stage_row(seq, label, offsets, ref=None):
    d = detonation(seq, ref)
    return (label, [(f'{t:+.2f} s' if t else 'detonation', frame(seq, d + o)) for o, t in [(o, o * 0.05) for o in offsets]])


OFF = [1, 3, 6, 9, 12, 16, 20, 24]
rows = [stage_row('v1_ground', 'V1 ground', OFF, 'v2_ground'), stage_row('v2_ground', 'V2 ground', OFF)]
if (('v2_ground_isolated', 1) in FX):
    rows.append(stage_row('v2_ground_isolated', 'V2 alone\n(no ignition)', OFF))
sheet('01_v1_vs_v2_ground', rows)
sheet('02_v1_vs_v2_wall', [stage_row('v1_wall', 'V1 wall', OFF, 'v2_wall'), stage_row('v2_wall', 'V2 wall', OFF)])
sheet('03_v1_vs_v2_night', [stage_row('v1_night', 'V1 night', OFF, 'v2_night'), stage_row('v2_night', 'V2 night', OFF),
                            stage_row('v2_night_no_emissive', 'V2 night,\nno emissive', OFF, 'v2_night')])
sheet('04_angles_air_confined', [stage_row('v2_high_angle', 'V2 high angle', OFF), stage_row('v2_air', 'V2 open air', OFF),
                                 stage_row('v2_bunker', 'V2 confined', OFF)])
sheet('05_camera_near_inside_screenfx_off', [stage_row('v2_camera_near_7', 'camera 7 blocks', OFF),
                                             stage_row('v2_camera_inside_3', 'camera inside\n(3 blocks)', OFF),
                                             stage_row('v2_ground_screenfx_off', 'Screen FX off', OFF, 'v2_ground')])
sheet('06_twenty_simultaneous', [stage_row('v2_twenty', '20 real casts', OFF)])

# Stage key frames (requested: beginning, maximum, cooling), full resolution
for seq in ['v2_ground', 'v2_ground_isolated']:
    d = detonation(seq)
    for o, name in [(2, 'beginning_0.10s'), (7, 'maximum_0.35s'), (12, 'maximum_0.60s'), (18, 'cooling_0.90s'), (24, 'breakup_1.20s')]:
        im = frame(seq, d + o)
        if im is not None:
            im.save(f'{out}/full_{seq}_{name}.png')


def gif(seq, name, ref=None, crop=None, n=34):
    d = max(0, detonation(seq, ref) - 3)
    ims = []
    for i in range(d, min(d + n, 64)):
        im = frame(seq, i)
        if im is None:
            break
        if crop:
            im = im.crop(crop)
        ims.append(im.resize((640, 360)).convert('P', palette=Image.ADAPTIVE, colors=192))
    if ims:
        ims[0].save(f'{out}/{name}.gif', save_all=True, append_images=ims[1:], duration=50, loop=0)


CROP = (240, 60, 1680, 870)  # 16:9 window around the explosion in the ground/night views
gif('v1_ground', 'anim_v1_ground', 'v2_ground', CROP)
gif('v2_ground', 'anim_v2_ground', None, CROP)
gif('v2_ground_isolated', 'anim_v2_ground_isolated', None, CROP)
gif('v2_wall', 'anim_v2_wall')
gif('v2_night', 'anim_v2_night', None, CROP)
gif('v2_high_angle', 'anim_v2_high_angle')
print('media written to', out)
