#!/usr/bin/env python3
"""Camera & Gallery V1: contact sheets from REAL capture screenshots (no drawing beyond labels and borders).

usage: python3 Context/Tools/camera-gallery-v1/make_contact_sheets.py <capture game dir> <output dir>

Each sheet is a grid of downscaled real screenshots with their file names underneath, so a whole animation or a set
of states can be reviewed at once. The source screenshots themselves are never modified.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

SHEETS = {
    "shutter_animation": ["cam_10_shutter_t01"] + [f"cam_10_shutter_cont_t{i:02d}" for i in range(1, 15)] + ["cam_11_after_shutter_thumbnail"],
    "zoom": ["cam_02_viewfinder_1x", "cam_04_zoom_2x", "cam_06_zoom_05x", "cam_07_wheel_07x", "cam_08_wheel_14x"],
    "zoom_transition_2x": [f"cam_03_zoom_to_2x_t{i}" for i in range(1, 6)],
    "zoom_transition_05x": [f"cam_05_zoom_to_05x_t{i:02d}" for i in range(1, 6)],
    "open": ["cam_00_home_main"] + [f"cam_01_open_t{i:02d}" for i in range(1, 5)] + ["cam_02_viewfinder_1x"],
    "cursor_and_modes": ["cam_30_alt_hover_2x", "cam_31_scan_mode", "cam_32_scan_unavailable_message", "cam_33_shortcut_hover"],
    "navigation": ["cam_40_gallery_from_camera", "cam_41_back_in_camera", "cam_42_home_after_camera", "cam_50_home_secondary_page",
                   "cam_51_gallery_grid", "cam_52_gallery_scrolled", "cam_62_home_returned_page1"],
    "viewer": ["cam_53_viewer", "cam_54_viewer_next", "cam_55_viewer_favourite", "cam_56_rename_editing", "cam_57_renamed",
               "cam_58_delete_confirm", "cam_59_after_delete", "cam_60_gallery_favourites", "cam_61_gallery_all_named"],
    "gui_scales": [f"cam_{k}_gui{g}" for g in (4, 3, 2, 1) for k in ("70_viewfinder", "71_gallery", "72_viewer")],
    "final_corrections": [f"cam_{k}_gui{g}" for g in (4, 3, 2, 1) for k in ("70b_scan", "72b_rename")],
    "reduced_motion": ["cam_63_reduced_t01"] + [f"cam_63_reduced_cont_t{i:02d}" for i in range(1, 5)],
    "movement": ["cam_09_before_shutter", "cam_12_pig_unharmed", "cam_20_walking_photo", "cam_21_flying_photo"],
    "persist_isolation": ["cam_90_persisted_gallery", "cam_91_persisted_camera_shortcut", "cam_95_isolated_empty_gallery",
                          "cam_96_isolated_camera_empty_shortcut"],
}

TILE_W = 640
COLUMNS = 3


def find(shots: Path, stem: str):
    for prefix in ("notification_v2_", ""):
        p = shots / f"{prefix}{stem}.png"
        if p.exists():
            return p
    return None


def sheet(shots: Path, out: Path, name: str, stems):
    found = [(s, find(shots, s)) for s in stems]
    found = [(s, p) for s, p in found if p]
    if not found:
        return None
    first = Image.open(found[0][1])
    tile_h = round(TILE_W * first.height / first.width)
    label_h = 22
    rows = (len(found) + COLUMNS - 1) // COLUMNS
    img = Image.new("RGB", (COLUMNS * (TILE_W + 8) + 8, rows * (tile_h + label_h + 8) + 8), (16, 20, 26))
    draw = ImageDraw.Draw(img)
    for i, (stem, path) in enumerate(found):
        x = 8 + (i % COLUMNS) * (TILE_W + 8)
        y = 8 + (i // COLUMNS) * (tile_h + label_h + 8)
        shot = Image.open(path).convert("RGB").resize((TILE_W, tile_h), Image.LANCZOS)
        img.paste(shot, (x, y))
        draw.text((x + 2, y + tile_h + 4), stem, fill=(200, 215, 230))
    target = out / f"contact_{name}.png"
    img.save(target)
    return target


def main():
    game_dir, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    shots = game_dir / "screenshots"
    for name, stems in SHEETS.items():
        t = sheet(shots, out, name, stems)
        print(name, "->", t)


if __name__ == "__main__":
    main()
