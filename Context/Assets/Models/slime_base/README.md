# Small Slime — shared base model

Shared, recolourable base for all elemental Small Slimes (Hydro sheet = shape reference, not colour).

## Files
| File | What |
|---|---|
| `slime_base.bbmodel` | Editable Blockbench project ("Generic Model"/free format, per-face UVs — same setup as Totality's Forest Boar) |
| `slime_base.png` | 128×128 grayscale texture |
| `export/SlimeBaseGeometry.java` | Generated Minecraft `ModelPart` geometry (Mojang mappings, 26.x), per-face UVs, `RENDER_SCALE` |
| `make_slime.py` | Generates the body shape (cuboid list) and the texture |
| `export_java.py` | Regenerates the Java geometry from the `.bbmodel` (top/bottom UV fix 2026-10-02, see below) |
| `review/` | Screenshots and `slime_base_review_sheet.png` |

## Model
- Rest size 20 × 15 × 18 units (W × H × D); `RENDER_SCALE = 0.64` makes it **0.6 blocks** tall (reference scale).
- Front-view silhouette measured from the Hydro sheet: widest at ~37 % height, flattened base ~0.65 of max width,
  rounded dome. Horizontal sections are near-circular; front/back are levelled at |z| = 9 (flat face area).
- **41 body cuboids + 2 eye plates**, axis-aligned, no per-cube rotation (ModelPart-compatible).
  Body cuboids are nested and may overlap; all faces use planar-projected UVs, so coincident faces show identical
  texels (in Minecraft they render identically; Blockbench's preview can show faint hairline seams on them because of
  its UV edge inset).
- Hierarchy: `root` (pivot on the ground, centre) → `body`, `eyes` → `eye_right` (+x), `eye_left` (−x).
  Front faces −z (north), Minecraft entity convention.

## Texture / recolouring
- Body: six planar projections (front, back, right, left, top, bottom) shaded from the smooth blob: soft top-front-left
  light, lighter lower body, darker rim, one gloss highlight. No Hydro colour or water pattern.
- Eyes: own 6×12 region (oval, dark rim, light interior) and own model group, so body and eyes can be tinted or
  re-textured independently (e.g. element-coloured body + rim, cream eye interior).
- Base tone ~150–235 for the body so multiply-tinting keeps contrast.

## Poses (preview only, not baked into the asset)
Scale on `root` (pivot on the ground):
- idle: 1.0 / 1.0 / 1.0
- jump (airborne): x/z 0.86, y 1.22, lifted ~5 units
- landing (compressed): x/z 1.2, y 0.72

## Variant add-ons (later)
Add as separate groups under `root` (e.g. `addon_top`, `addon_sides`); the top of the dome is at y 15, the face
area is x −5…5, y 3…9 on z = −9.

## Export note — top/bottom UVs (fixed 2026-10-02)
The first export swapped the top and bottom faces: vanilla's `DOWN` polygon (index 0) is the cuboid's visual **top**
(model y points down), and the exporter gave it the Blockbench `down` rectangle, so in game the dome top showed the
bottom view and the base the top view. `export_java.py` now assigns `flip(up)` to polygon 0 and `flip(down)` to
polygon 1, matching Blockbench. Only these two UV rectangles per body cuboid changed (41 × 2); vertices, sides and eyes
are identical. `SlimeBaseGeometry.java` SHA-256: `ea0948d4…0fcf7371` → `34d024c6…68538fdb68d0c1e7692125ce5cf78`.
The `.bbmodel` and `slime_base.png` are unchanged.

Element surface details for the in-game Small Slimes are separate motif masks
(`Context/Tools/slime-motifs/make_slime_motifs.py`), composited at resource load; this texture is not edited.
