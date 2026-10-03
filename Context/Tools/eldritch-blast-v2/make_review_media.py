#!/usr/bin/env python3
"""Eldritch Blast V2 review media from the capture runs (scene 70) and the reference clips.

usage: make_review_media.py <out_dir> <v1 capture dir> <v2 capture dir> <reference videos dir>
Writes: videos/<sequence>.mp4 (one frame per game tick at 20 fps = real game speed; V1 and V2), sheets/*.jpg
(V1 vs V2 frame strips, BG3 reference vs V2, multi-beam, palettes, emissive on/off). Needs Pillow and ffmpeg.
"""
import glob
import os
import re
import subprocess
import sys

from PIL import Image, ImageDraw

OUT, V1, V2, REF = sys.argv[1:5]
W, H = 480, 270


def frames(cap, label, seq):
    return sorted(glob.glob(f"{cap}/screenshots/notification_v2_EB2_{label}_{seq}_[0-9][0-9].png"))


def video(cap, label, seq, name):
    fs = frames(cap, label, seq)
    if not fs:
        return
    os.makedirs(f"{OUT}/videos", exist_ok=True)
    pattern = fs[0][:-6] + "%02d.png"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", "20", "-i", pattern, "-vf", "scale=1280:-2",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", f"{OUT}/videos/{name}.mp4"], check=True)


def tile(img, text):
    im = img.convert("RGB").resize((W, H))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 8 + 7 * len(text), 16], fill=(0, 0, 0))
    d.text((4, 3), text, fill=(255, 255, 255))
    return im


def strip(rows, out):
    cols = max(len(r[1]) for r in rows)
    o = Image.new("RGB", (W * cols, H * len(rows)))
    for r, (title, cells) in enumerate(rows):
        for c, (path, text) in enumerate(cells):
            if path and os.path.exists(path):
                o.paste(tile(Image.open(path), f"{title} {text}"), (c * W, r * H))
    os.makedirs(f"{OUT}/sheets", exist_ok=True)
    o.save(f"{OUT}/sheets/{out}", quality=86)


def cells(cap, label, seq, idx):
    fs = frames(cap, label, seq)
    return [(fs[i] if i < len(fs) else None, f"t+{i * 50}ms") for i in idx]


SEQS = ["side", "behind", "target_close", "caster_close", "wall", "far", "first_person", "third_person", "night_side",
        "night_target_close"]
V2_ONLY = ["multi2_side", "multi3_side", "multi4_side", "multi4_behind", "multi4_target_close", "teal_side",
           "teal_target_close", "teal_behind", "side_no_emissive"]
for s in SEQS:
    video(V1, "v1", s, f"v1_{s}")
    video(V2, "v2", s, f"v2_{s}")
for s in V2_ONLY:
    video(V2, "v2", s, f"v2_{s}")

idx = [0, 1, 2, 3, 4, 5, 7, 10, 14]
for s in SEQS:
    strip([("V1 " + s, cells(V1, "v1", s, idx)), ("V2 " + s, cells(V2, "v2", s, idx))], f"v1_vs_v2_{s}.jpg")
strip([(f"V2 {s}", cells(V2, "v2", s, [0, 2, 4, 6, 8, 10, 13, 16, 20, 26])) for s in
       ["multi2_side", "multi3_side", "multi4_side", "multi4_behind", "multi4_target_close"]], "multi_beam.jpg")
strip([(f"V2 {s}", cells(V2, "v2", s, [1, 2, 3, 4, 5, 7, 10])) for s in
       ["side", "teal_side", "target_close", "teal_target_close", "behind", "teal_behind"]], "palette_violet_vs_teal.jpg")
strip([(f"V2 {s}", cells(V2, "v2", s, [1, 2, 3, 4, 5, 7, 10])) for s in ["side", "side_no_emissive"]], "emissive_on_off.jpg")
strip([(f"V2 {s}", cells(V2, "v2", s, [1, 2, 3, 4, 5, 7, 10])) for s in ["night_side", "night_target_close", "far", "wall"]],
      "night_far_wall.jpg")

# BG3 reference (TikTok, the clean baseline cast) next to V2 teal, stage by stage.
os.makedirs(f"{OUT}/sheets/ref_frames", exist_ok=True)
stages = [("charge", 3.30), ("release", 3.50), ("thinning", 3.63), ("impact sparks", 3.73), ("residue", 4.30)]
ref_cells = []
for name, t in stages:
    p = f"{OUT}/sheets/ref_frames/tiktok_{t:.2f}.png"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(t), "-i", f"{REF}/eldritch_blast_tiktok.mp4", "-frames:v", "1",
                    "-vf", "crop=480:270:48:350", p], check=True)
    ref_cells.append((p, name))
side, close = frames(V2, "v2", "teal_side"), frames(V2, "v2", "teal_target_close")
v2_cells = [(side[1], "flare (side)"), (side[2], "release (side)"), (side[3], "beam (side)"), (close[4], "impact"), (close[8], "residue")]
strip([("BG3 ref (TikTok)", ref_cells), ("V2 teal", v2_cells)], "bg3_reference_vs_v2.jpg")
print("media written to", OUT)
