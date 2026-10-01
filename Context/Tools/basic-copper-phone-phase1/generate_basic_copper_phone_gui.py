#!/usr/bin/env python3
"""
Basic Copper Phone redesign, Phase 1: generates the opened phone's casing sprites and the Phone
equipment-slot icon. Replaces generate_crude_phone_gui.py (Crude Phone Screen V2) for these files.

Every copper colour is sampled from (and asserted against) the UNCHANGED item texture
textures/item/basic_copper_phone.png, which stays the device's identity. Output is original pixel art at
1 art pixel = 1 GUI pixel (crisp at every integer GUI scale), deterministic.

  textures/gui/sprites/phone/crude/frame.png    nine-slice casing: stepped round corners, lit/shaded rim,
                                                an engraved groove (face plate), corner screws, 2 px bezel
  textures/gui/sprites/phone/crude/speaker.png  the top speaker slot (kept from the item)
  textures/gui/sprites/phone/crude/camera.png   front camera lens left of the speaker
  textures/gui/sprites/phone/crude/grille.png   recessed microphone slot centred on the chin
  textures/gui/sprites/phone/os/lock.png        padlock glyph (white, tinted in game); an OS glyph, moved
  textures/gui/sprites/container/slot/phone.png Phone equipment-slot icon (16x16, #555555 line art,
                                                like ring/pouch/back)

Usage: python3 generate_basic_copper_phone_gui.py <totality repo root>
"""
import json
import sys
from pathlib import Path

from PIL import Image

# Frame geometry, GUI pixels (must match PhoneDeviceStyle.CRUDE).
W, H = 40, 80
LEFT, RIGHT, TOP, BOTTOM = 8, 8, 18, 18
RADIUS = [4, 2, 1, 1]  # transparent pixels per row from each outer corner (stepped round corner)


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


OUTLINE = "#35190f"
BEZEL = "#12100e"
# The item's copper colours grouped by where they sit on the item (weights = how often they appear).
REGIONS = {
    "lit": [("#f69771", 5), ("#f99974", 4), ("#e78460", 3)],
    "face": [("#d3714f", 6), ("#c66d4d", 3), ("#c46849", 2), ("#c06648", 2)],
    "shade": [("#b45f44", 4), ("#ab583e", 3), ("#9e4f36", 2)],
    "deep": [("#88412c", 3), ("#5c2b1c", 2), ("#50281a", 1)],
    # Revision 1: the face plate uses only the item's two dominant face colours (less small-scale noise).
    "face_calm": [("#d3714f", 8), ("#c66d4d", 3)],
}
# Revision 1: patina speckle rates on the face plate (were 4% dark, 1.5% light).
SPECKLE_DARK, SPECKLE_LIGHT = 0.02, 0.993


def check_palette(item_path):
    im = Image.open(item_path).convert("RGBA")
    present = {im.getpixel((x, y))[:3] for y in range(im.height) for x in range(im.width) if im.getpixel((x, y))[3]}
    for c in [OUTLINE, BEZEL] + [c for region in REGIONS.values() for c, _ in region]:
        assert rgb(c) in present, f"colour {c} not in the item texture"


def h32(*v):
    """Small deterministic hash -> [0, 1)."""
    x = 2166136261
    for n in v:
        x ^= (n + 0x9E3779B9) & 0xFFFFFFFF
        x = (x * 16777619) & 0xFFFFFFFF
        x ^= x >> 13
    return (x & 0xFFFF) / 65536.0


def weighted(region, *seed):
    total = sum(w for _, w in REGIONS[region])
    r = h32(*seed) * total
    for c, w in REGIONS[region]:
        r -= w
        if r < 0:
            return rgb(c)
    return rgb(REGIONS[region][-1][0])


def cut(x, y, w, h):
    """True when (x, y) is outside the stepped round corner; 'edge' when it is on the corner's outline."""
    for dy in range(len(RADIUS)):
        for (cx, cy) in ((x, y), (w - 1 - x, y), (x, h - 1 - y), (w - 1 - x, h - 1 - y)):
            if cy == dy and cx < RADIUS[dy]:
                return True
    return False


def frame():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            if LEFT <= x < W - LEFT and TOP <= y < H - BOTTOM:
                continue
            if cut(x, y, W, H):
                continue
            dl, dr, dt, db = x, W - 1 - x, y, H - 1 - y
            # Outline: the outermost opaque pixel in either direction.
            if (x == 0 or y == 0 or x == W - 1 or y == H - 1
                    or cut(x - 1, y, W, H) or cut(x + 1, y, W, H) or cut(x, y - 1, W, H) or cut(x, y + 1, W, H)):
                px[x, y] = rgb(OUTLINE) + (255,)
                continue
            # Bezel: 2 px of near-black around the display opening.
            if (LEFT - 2 <= x < W - RIGHT + 2) and (TOP - 2 <= y < H - BOTTOM + 2):
                px[x, y] = rgb(BEZEL) + (255,)
                continue
            d = min(dl, dr, dt, db)
            # Engraved groove 3 px in: the rim and the face plate read as two parts of a rugged casing.
            if d == 3:
                px[x, y] = rgb("#88412c") + (255,)
                continue
            if d == 4 and (dl == 4 or dt == 4):
                # Lit lower lip of the groove on the top/left (light comes from the top left, as on the item).
                px[x, y] = rgb("#e78460") + (255,)
                continue
            if d <= 2:
                # Rim: lit on the top/left, shaded on the right, darkest along the bottom edge.
                if dt == d or dl == d:
                    region = "lit"
                elif db == d:
                    region = "deep" if d == 1 else "shade"
                else:
                    region = "shade"
            else:
                region = "face"
                # The chin's face darkens slightly toward the bottom, without the old noisy band.
                if db < BOTTOM and db <= 5:
                    region = "shade"
            c = weighted("face_calm" if region == "face" else region, x, y)
            r = h32(x, y, 7)
            if region == "face" and r < SPECKLE_DARK:
                c = rgb("#9e4f36")
            elif region == "face" and r > SPECKLE_LIGHT:
                c = rgb("#f99974")
            px[x, y] = c + (255,)
    # Corner screws on the face plate (fixed corner patches of the nine-slice).
    for sx, sy in ((5, 6), (W - 7, 6), (5, H - 8), (W - 7, H - 8)):
        px[sx, sy] = rgb("#5c2b1c") + (255,)
        px[sx + 1, sy] = rgb("#88412c") + (255,)
        px[sx, sy + 1] = rgb("#88412c") + (255,)
        px[sx + 1, sy + 1] = rgb("#f69771") + (255,)
    return img


def speaker():
    w, h = 18, 4
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for x in range(1, w - 1):
        px[x, 0] = rgb("#5c2b1c") + (255,)
        px[x, 3] = rgb("#f69771") + (200,)
    for y in (1, 2):
        for x in range(w):
            px[x, y] = rgb(OUTLINE if x in (0, w - 1) else BEZEL) + (255,)
    return img


def camera():
    rows = [".oo.", "oLBo", "oBBo", ".oo."]
    img = Image.new("RGBA", (4, 4), (0, 0, 0, 0))
    col = {"o": rgb(OUTLINE), "B": rgb(BEZEL), "L": (60, 96, 120)}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in col:
                img.putpixel((x, y), col[ch] + (255,))
    return img


def grille():
    w, h = 12, 4
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for x in range(1, w - 1):
        px[x, 0] = rgb("#88412c") + (255,)
        px[x, 3] = rgb("#e78460") + (220,)
    for y in (1, 2):
        px[0, y] = rgb("#88412c") + (255,)
        px[w - 1, y] = rgb("#e78460") + (220,)
        for x in range(1, w - 1):
            # Holes alternate with the plate: a perforated grille, not a button.
            px[x, y] = rgb(BEZEL if x % 2 == 1 else "#5c2b1c") + (255,)
    return img


def lock():
    rows = [".###.", "#...#", "#...#", "#####", "##.##", "##.##", "#####"]
    img = Image.new("RGBA", (5, 7), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x, y), (255, 255, 255, 255))
    return img


# Equipment slot icon: the same single-colour 1 px line art as ring/pouch/back (#555555 on transparent).
PHONE_SLOT = [
    "................",
    ".....######.....",
    "....#......#....",
    "....#..##..#....",
    "....#......##...",
    "....#.####.##...",
    "....#.#..#.#....",
    "....#.#..#.#....",
    "....#.#..#.#....",
    "....#.#..#.#....",
    "....#.####.#....",
    "....#......#....",
    "....#..##..#....",
    ".....######.....",
    "................",
    "................",
]


def phone_slot():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(PHONE_SLOT):
        assert len(row) == 16
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x, y), (85, 85, 85, 255))
    return img


def mcmeta(path, scaling):
    Path(str(path) + ".mcmeta").write_text(json.dumps({"gui": {"scaling": scaling}}, indent=2) + "\n")


def main():
    root = Path(sys.argv[1])
    tex = root / "src/main/resources/assets/totality/textures"
    check_palette(tex / "item/basic_copper_phone.png")
    crude = tex / "gui/sprites/phone/crude"
    os_dir = tex / "gui/sprites/phone/os"
    crude.mkdir(parents=True, exist_ok=True)
    os_dir.mkdir(parents=True, exist_ok=True)
    frame().save(crude / "frame.png")
    mcmeta(crude / "frame.png", {"type": "nine_slice", "width": W, "height": H,
                                 "border": {"left": LEFT, "top": TOP, "right": RIGHT, "bottom": BOTTOM},
                                 "stretch_inner": False})
    speaker().save(crude / "speaker.png")
    camera().save(crude / "camera.png")
    grille().save(crude / "grille.png")
    lock().save(os_dir / "lock.png")
    phone_slot().save(tex / "gui/sprites/container/slot/phone.png")
    print("ok")


if __name__ == "__main__":
    main()
