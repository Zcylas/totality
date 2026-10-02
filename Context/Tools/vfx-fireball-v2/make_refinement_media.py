#!/usr/bin/env python3
"""Fireball V2 B2.1 before/after review media from two REAL-CLIENT capture runs of scene 69's refine set
(-Dtotality.fireball.v2.refine=true): the B2 baseline and the B2.1 refinement, same cameras and casts.

Builds before/after contact sheets and side-by-side animated GIFs (one frame per game tick = 20 fps real time;
the slow-motion sequences are played at 0.25x, so each frame is 12.5 ms of explosion time).

usage: python3 make_refinement_media.py <before_run_dir> <after_run_dir> <out_dir> [tag]
  run dirs are capture game dirs (containing screenshots/ and hologram-capture.log)
"""
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

before, after, out = sys.argv[1:4]
tag = sys.argv[4] if len(sys.argv) > 4 else ''
os.makedirs(out, exist_ok=True)


def font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def fx_table(run):
    fx = {}
    for line in open(f'{run}/hologram-capture.log', encoding='utf-8', errors='replace'):
        m = re.match(r'fx: FB2_(\S+)_(\d\d) explosions=(\d+) elements=(\d+)', line)
        if m:
            fx[(m.group(1), int(m.group(2)))] = int(m.group(3))
    return fx


RUNS = {'B2': (before, fx_table(before)), 'B2.1': (after, fx_table(after))}


def frame(run, seq, i):
    p = f'{RUNS[run][0]}/screenshots/notification_v2_FB2_{seq}_{i:02d}.png'
    return Image.open(p).convert('RGB') if os.path.exists(p) else None


def detonation(run, seq):
    fx = RUNS[run][1]
    for i in range(64):
        if fx.get((seq, i), 0) > 0:
            return i
    return 0


def sheet(name, rows, w=400, label_px=18, crop=None):
    """rows: list of (row label, [(tile label, image)])"""
    cols = max(len(r[1]) for r in rows)
    cw, ch = (crop[2] - crop[0], crop[3] - crop[1]) if crop else (1920, 1080)
    h = w * ch // cw
    img = Image.new('RGB', (cols * w + 120, len(rows) * (h + label_px)), (16, 16, 18))
    dr = ImageDraw.Draw(img)
    for r, (rlabel, tiles) in enumerate(rows):
        y = r * (h + label_px)
        dr.text((6, y + label_px + h // 2 - 8), rlabel, fill=(255, 220, 160), font=font(15))
        for c, (tlabel, im) in enumerate(tiles):
            x = 120 + c * w
            if im is not None:
                img.paste((im.crop(crop) if crop else im).resize((w, h)), (x, y + label_px))
            dr.text((x + 4, y + 2), tlabel, fill=(230, 230, 230), font=font(13))
    dr.text((img.width - 8, img.height - 16), f'REAL CLIENT {tag}', fill=(140, 255, 140), font=font(12), anchor='ra')
    img.save(f'{out}/{name}.jpg', quality=90)


def stage_rows(seq, offsets, dt=0.05, crop=None):
    rows = []
    for run in RUNS:
        d = detonation(run, seq)
        rows.append((run, [(f'{(o + 1) * dt:.3f} s' if dt < 0.05 else f'+{o * dt:.2f} s', frame(run, seq, d + o)) for o in offsets]))
    return rows


OFF = [1, 2, 3, 6, 9, 12, 18, 24]
CROP = (240, 60, 1680, 870)
sheet('01_slowmo_expansion_marker', stage_rows('v2_slowmo_marker', [0, 1, 3, 5, 7, 11, 15, 23], 0.0125))
sheet('02_slowmo_expansion_ground_view', stage_rows('v2_slowmo', [0, 1, 3, 5, 7, 11, 15, 23], 0.0125, CROP), crop=CROP)
sheet('03_ground_real_cast', stage_rows('v2_ground', OFF, crop=CROP), crop=CROP)
sheet('04_ground_isolated_no_fire_blocks', stage_rows('v2_ground_isolated', OFF, crop=CROP), crop=CROP)
sheet('05_radius_marker_front', stage_rows('v2_marker_front', [2, 4, 6, 9, 12, 18]))
sheet('06_radius_marker_top', stage_rows('v2_marker_top', [2, 4, 6, 9, 12, 18]))
sheet('07_high_angle', stage_rows('v2_high_angle', OFF))
sheet('08_wall', stage_rows('v2_wall', OFF))
sheet('09_night', stage_rows('v2_night', OFF, crop=CROP), crop=CROP)
sheet('10_open_air', stage_rows('v2_air', OFF))
sheet('11_camera_near_7', stage_rows('v2_camera_near_7', [1, 2, 3, 5, 8, 12, 18, 24]))
sheet('12_camera_inside_3', stage_rows('v2_camera_inside_3', [1, 2, 3, 5, 8, 12, 18, 24]))
sheet('13_camera_inside_isolated', stage_rows('v2_camera_inside_isolated', [0, 1, 2, 4, 7, 11, 17, 23]))

# Full-resolution stage frames of the refined explosion (beginning, maximum, cooling, break-up)
for seq in ['v2_ground_isolated']:
    for o, name in [(2, 'beginning_0.10s'), (6, 'maximum_0.30s'), (9, 'maximum_0.45s'), (18, 'cooling_0.90s'), (24, 'breakup_1.20s')]:
        for run in RUNS:
            im = frame(run, seq, detonation(run, seq) + o)
            if im is not None:
                im.save(f'{out}/full_{run.replace(".", "")}_{seq}_{name}.jpg', quality=90)


def gif(seq, name, crop=None, n=34, pre=2):
    """Side-by-side B2 | B2.1 animation."""
    ims = []
    starts = {run: max(0, detonation(run, seq) - pre) for run in RUNS}
    for k in range(n):
        tiles = []
        for run in RUNS:
            im = frame(run, seq, starts[run] + k)
            if im is None:
                break
            tiles.append(((im.crop(crop) if crop else im).resize((512, 288)), run))
        if len(tiles) < 2:
            break
        canvas = Image.new('RGB', (1028, 308), (16, 16, 18))
        dr = ImageDraw.Draw(canvas)
        for j, (t, run) in enumerate(tiles):
            canvas.paste(t, (j * 516, 20))
            dr.text((j * 516 + 6, 3), f'{run}  {"previous" if run == "B2" else "refined"}', fill=(255, 220, 160), font=font(14))
        canvas.paste(Image.new('RGB', (4, 308), (60, 60, 60)), (512, 0))
        ims.append(canvas.convert('P', palette=Image.ADAPTIVE, colors=128))
    if ims:
        ims[0].save(f'{out}/{name}.gif', save_all=True, append_images=ims[1:], duration=50, loop=0)


gif('v2_slowmo_marker', 'anim_slowmo_4x_marker', n=40, pre=0)
gif('v2_slowmo', 'anim_slowmo_4x_ground_view', CROP, n=40, pre=0)
gif('v2_ground', 'anim_ground_real_cast', CROP)
gif('v2_ground_isolated', 'anim_ground_isolated', CROP, pre=0)
gif('v2_marker_top', 'anim_radius_marker_top', pre=0)
gif('v2_high_angle', 'anim_high_angle')
gif('v2_wall', 'anim_wall')
gif('v2_night', 'anim_night', CROP)
gif('v2_camera_inside_3', 'anim_camera_inside_3', n=30)
print('media written to', out)
