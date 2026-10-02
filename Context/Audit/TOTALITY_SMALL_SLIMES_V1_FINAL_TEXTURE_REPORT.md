# Totality — Small Slimes V1: UV Approval and Final Elemental Textures

**Date:** 2026-10-03  
**Entity:** `totality:slime_test` (development test entity)  
**Status:** **APPROVED 2026-10-03** after the final V1 visual review: the final textures, palettes and UV correction are accepted, and the original dark eye rims stay the default (the element rims remain a dev-only comparison). Committed locally as one commit and not pushed.  
**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_SMALL_SLIMES_V1_FINAL_TEXTURE_REVIEW.zip`

Evidence labels used below:
- **[REAL]** — real Minecraft 26.2 client screenshots or logs (OpenGL and Vulkan, AMD RX 6600, Mesa 26.2.3).
- **[TEST]** — JUnit.
- **[SIM]** — software preview, used only for design iteration and the source-artwork sheet. It is never used as visual evidence.

---

## 1. Executive summary

1. **Option A is applied.** `export_java.py` now gives polygon 0 (the visual top) `flip(up)` and polygon 1 (the visual bottom) `flip(down)`. The geometry was regenerated and copied into the game. Only those two UV rectangles per body cuboid changed (41 × 2); vertices, sides and eyes are byte-identical (§4). The temporary UV comparison code is gone, and a permanent mapping test replaces it.
2. **All seven Small Slimes have final textures.** Each is the approved palette plus original pixel-art details: a **motif mask** per element, composited at resource load. It only uses the Small-Slime-only detail list (§2), so there are no Large-only features and no geometry. The model, the palettes and the grayscale texture are unchanged.
3. **The details are defined on the 3D body surface**, not per texture region. A patch, spot or line therefore lands in the same place on every stepped cuboid face that shows it, and the real screenshots show no seams or face mismatches (§9).
4. **Geo, Dendro and Cryo got the extra attention requested:**
   - **Geo:** angular dark stone patches and a rough edge on the dark dome.
   - **Dendro:** an irregular moss edge with moss tongues, darker blotches and spore dots.
   - **Cryo:** a scalloped snow cap, snow dots and three six-armed snowflakes.
5. **Eyes:**
   - **Unchanged by default.** The element-coloured rims exist only as a dev comparison option (`Eyes:"element"`); they are **not final** and wait for your approval (§7).
   - **The extra eye batch is gone** in the textured modes. Body and eyes now render in one pass with one texture, whichever eye style is chosen.
6. **Verification:** everything below passed.
   - **Build:** the full `./gradlew build --rerun-tasks` passed: **2506 tests, 0 failures**, 2 skipped (pre-existing).
   - **Captures:** **66 real captures per backend** on OpenGL and on Vulkan.
   - **Reloads:** 3 reloads plus a resource-pack override of a motif mask, on both backends.
   - **Performance:** within noise of the tint method on OpenGL, about +0.18 ms at 200 slimes on Vulkan.
   - **Patches:** both apply and build cleanly on a clean copy of HEAD.
   - **Reference assets:** the Blockbench project and the grayscale texture are byte-identical.
7. **Nothing needed a change to the approved geometry or the agreed design.**

---

## 2. Small versus Large findings (applied)

The research is in the UV decision report, §2. This task applied it as follows.

| Element | Implemented (Small Slime, texture only) | Excluded — geometry (future, §12) | Excluded — Large only |
|---|---|---|---|
| Anemo | Two pale wind curls, on the left and right sides | Small wings | Oversized wings, dome nubs |
| Geo | Dark angular patches on the upper body, a rough dark dome edge, light stone spots on the lower body | Rock shards, clover sprouts | Rock carapace / shield |
| Electro | Thin lavender bracket lines from the top down beside the eyes | Antenna with a bulb | Bold circuit pattern, Mutant |
| Dendro | Irregular moss edge with tongues, darker moss blotches, light spore dots | Fern / grass fronds | Red flower, yellow flowers |
| Hydro | Very soft darker mottling on the upper body only | — | Horn nubs |
| Pyro | Darker molten patches, warm yellow highlights on the lower body | Flames / glow (VFX phase) | Horn nubs, glow halo |
| Cryo | Wavy, scalloped snow-cap edge; white snow dots; three small six-armed snowflakes (two sides, back) | Ice crystals at the cap rim | Spiked ice crown |

**Still not available:** the "Small Slime Variants" / "Small Hydro Slime" concept sheets. The Genshin Archive renders, linked and not redistributed, were the reference. No Genshin asset was copied or traced; every detail is procedural or hand-placed pixel art drawn for this model.

---

## 3. Approved palette integration

- **Unchanged since the approval:** `anemo`, `electro`, `hydro`, `pyro` and `grayscale`, plus the approved `geo` (stone v3), `dendro` (v2) and `cryo` (snow cap). Values are in `SlimePalettes.java` and in UV report §3.
- **The details work on top of the palette and never replace it:**
  - **Tone** shifts the gray value *before* the gradient map, so details always use the element's own ramp colours and lighting.
  - **Edge** moves the height-based secondary ramp (Geo dome, Dendro moss, Cryo snow cap), so the boundary becomes irregular without a new colour.
  - **Overlay** lays the element's overlay colour on top, for snow, spores and the lines/curls. It is shaded by the texel's gray value, so it keeps the body's light-to-dark form.
- **[TEST]** A neutral mask produces exactly the palette-only texture (`neutralMaskEqualsThePaletteOnly`).

---

## 4. UV investigation and correction (Option A)

**Change** (`Context/Assets/Models/slime_base/export_java.py`, line 57):

```python
uvs = flip(fc["up"]["uv"]) + flip(fc["down"]["uv"]) + fc["east"]["uv"] + fc["north"]["uv"] + fc["west"]["uv"] + fc["south"]["uv"]
```

| File | SHA-256 before | SHA-256 after |
|---|---|---|
| `export_java.py` | `bce34790ab71c28e5232645c7fd0c268dc9e5c05b8471ef3d83375d7223ba8c3` | `f291f6cb246775a3cf7b47e53092648578a7bd2bf96059ef3550aee6b6100b13` |
| `export/SlimeBaseGeometry.java` | `ea0948d4b956cd55c5605c44a7adbcec9570ed5169d7607e251441e70fcf7371` | `34d024c6f500390539fd3d2c4d7789bbe7268538fdb68d0c1e7692125ce5cf78` |
| `src/.../client/entity/slime/SlimeBaseGeometry.java` | `ea0948d4…0fcf7371` | `34d024c6…1e7692125ce5cf78` (byte-identical copy of the export) |
| `slime_base.bbmodel` | `4ae3d16d…65873caf9` | unchanged |
| `slime_base.png` (asset and in-repo copy) | `996f2800…272ea6ce` | unchanged |
| `make_slime.py`, `slime_geometry.json`, `review/*` | — | unchanged (`evidence/slime_base_assets_sha256_{pre,post}task.txt`) |
| `README.md` | `33d2dd2c…` | `02c86c59…` (documents the fix) |

**Only the intended UVs changed:**
- The regenerated export's code body equals the previously reviewed temporary `SlimeBaseGeometryUvFix`.
- The diff touches only the `uv(...)` arguments of polygons 0 and 1 on the 41 body cuboids.
- **[TEST]** The new permanent test `SlimeUvMappingTest` checks that all 164 top-face and 164 bottom-face vertices land on their Blockbench projections (164/164 each), and that polygon 0 is the visual top and samples the top view.

**Removed:**
- `SlimeBaseGeometryUvFix.java`
- `SlimeUvMappingComparisonTest.java`
- the entity's `UvFix` NBT and data flag
- `SlimeTexelHeights.forUvFixModel`

**Updated for the new mapping:**
- `SlimeTestRegressionTest`, which pins the new hash.
- `SlimePaletteTest`: the top-view centre is now the dome top (height 1.0), and the bottom view is the base.

**Option A′ was not done.**

---

## 5. Texture-art approach

**Pipeline** (as approved: resource-load-time motif compositing):

```
slime_base.png (grayscale, unchanged)
   │  + SlimeTexelHeights (height per body texel, from the model's UVs)
   │  + palette (approved)                               ┐
   │  + motifs/<element>.png  (RGBA mask, resource)      ├─ SlimePaletteMapper (pure function)
   │  + eye colours (only with Eyes:"element")           ┘
   ▼
SlimePaletteTexture (ReloadableTexture, in memory, rebuilt on every resource reload)
```

**Mask format** (128×128 RGBA, same layout as the base texture; alpha 0 means no detail):

| Channel | Meaning |
|---|---|
| red | `128 + tone × 255`: gray shift before the palette (patches, spots) |
| green | `128 + height × 255`: shift of the secondary-ramp height (moss edge, snow cap edge, rough dome) |
| blue | `overlay × 255`: amount of the element overlay colour (snow, spores, lines) |

**Generator:** `Context/Tools/slime-motifs/make_slime_motifs.py`.
- It is deterministic and documented in the file header.
- It copies the blob shape functions from `make_slime.py`. That script is never run or imported, because it would regenerate the approved texture.
- **Coherence across faces:** each texel of the front, back, east, west and top views is mapped to the point on the smooth body it shows. Every design is a function of that 3D point and normal, so a detail is consistent across all stepped faces that display it.
- **Coverage:** details are written only on texels a body face actually uses, from the Blockbench face rectangles. Eye areas and the hidden bottom view are left alone.
- **Hand-drawn pixel art:** the stamped decals (Anemo curl, Cryo snowflake) are drawn as pixel grids in the script and projected onto the surface.
- **[TEST]** `masksOnlyMarkBodyTexelsAwayFromTheEyes`.
- **Resource packs:** they can override any `motifs/<element>.png`. A missing or wrongly sized mask falls back to the palette only, with a log warning (**[TEST]**, plus a **[REAL]** pack test in §10).

---

## 6. Element by element

The source-artwork sheet (`sheets/00_source_artwork_masks.png`) shows each mask's channels and the composed texture. Real close-ups are in `sheets/04` and `sheets/05`.

| Element | Mask texels | Details |
|---|---|---|
| **Anemo** | 142 | Two 13×13 pixel-art wind curls (Archimedean curl with a tail), mirrored on the left and right sides at mid-height, slightly toward the front. Pale overlay at 75 %, tone +0.06. Kept subtle, like the reference. |
| **Geo** | 4511 | Sixteen **angular hexagonal stone patches** (tone −0.20, size varied by noise) around the upper body and the dome edge. Fourteen light irregular **stone spots** on the lower body (+0.17). A ±0.05 noisy edge makes the dark dome boundary look like rough rock rather than a smooth gradient. |
| **Electro** | 148 | Thin lavender **bracket lines** (1 texel, overlay 85 %) on front-facing texels: a horizontal top stroke and a vertical drop on each side of the face, plus short top ticks. Small-Slime brackets only; no circuit pattern. |
| **Dendro** | 4665 | An **irregular moss edge**: two sine waves plus noise, with five moss tongues reaching down. Nine darker **moss blotches** (−0.14) and 22 light **spore dots** (overlay 80 %), all on the green upper body. |
| **Hydro** | 650 | Six very soft darker **watery mottles** (−0.07) on the upper half only. It stays the plainest element, as in the reference, and nothing looks like snow or ice. |
| **Pyro** | 1352 | Darker **molten patches** (−0.17, noise-shaped) over the body; warm **yellow highlights** (+0.08, 30 % overlay) on the lower body. No emissive and no flames (VFX phase). |
| **Cryo** | 4624 | A **wavy snow-cap edge** with seven rounded scallops dipping down. Nine white **snow dots** just below the cap. Three **six-armed snowflakes** (7×7 pixel art), on the left, right and back. |

**Iteration notes** (simulator previews, then verified in the real client):
- **First pass:**
  - **Anemo:** the 9×9 curl was illegible, so it was redrawn at 13×13.
  - **Geo:** round blobs plus grain read as dirt, so they were replaced by angular patches.
  - **Pyro:** thin band lines read as cracks, so they were replaced by molten patches.
  - **Cryo:** sharp drips read as icicles, so they were replaced by rounded scallops.
- **Second pass:** the "body texel" test was tightened to the Blockbench face rectangles after a test caught Geo detail on one texel no face samples.

---

## 7. Optional eye-colour comparison (not final)

**What exists:**
- **Dev option:** `Eyes:"element"` (NBT; default `"plain"`) recolours only the 6×12 eye texels by their four gray levels: rim 58 → rim colour, 214 → glow, 240 → interior, 255 → interior lightened 50 %. It uses the measured reference colours from UV report §2.
- **Unchanged:** the geometry; nothing is emissive.
- **[TEST]** `elementEyesRecolourOnlyTheEyeTexels` checks that only eye texels change, that transparent corners stay transparent, and that the dark rim becomes the rim colour.
- **[REAL]** Real comparisons are in `sheets/06_eyes_plain_vs_element.png` (close-ups) and `sheets/07_lineup_eyes.png` (lineup).

**My observations:**
- **Warm-rim elements** (Anemo, Geo, Dendro, Pyro, with amber or red-orange rims): the eyes become much softer and friendlier, closer to the Genshin look, and lose the "cartoon black outline" contrast. Even in the lineup (about 3.7 blocks) the eyes contrast noticeably less on Pyro (orange rim on an orange body) and Anemo. Element eyes were not captured at 8+ blocks.
- **Hydro and Cryo** (blue rims): the rims blend into the body. That looks good up close but gives the face less contrast.
- **Electro** (violet rim): close to the body colour, but still readable thanks to the light interior.

**Recommendation:** keep the plain dark rims for V1 gameplay readability. Alternatively, approve element rims with a darker rim value (for example the rim colour at about 60 % brightness) as a follow-up. **Not finalised; your decision.**

**Batch merge:**
- **Before:** the textured modes drew the body with the generated texture plus an `EyesLayer` with the grayscale texture, two submissions per slime.
- **Now:** palette and plain modes render the **full model (body + eyes) in one pass** with one generated texture. The eyes are already in that texture, either plain (original texels kept) or element-coloured. Only the `tint` comparison mode still uses the body model plus the untinted `EyesLayer`.
- **Quality:** unchanged. The eye texels are byte-identical in plain mode (**[TEST]**), and the screenshots look identical.

---

## 8. Architecture and performance

**New or changed classes** (`client/entity/slime`, `entity/dev`):
- `SlimeMotif` (element + overlay colour → mask id) and `SlimeEyeColours` (rim, glow, interior).
- `SlimePalettes.motifFor(variant)` and `eyesFor(variant)`.
- `SlimePaletteMapper`: palette + optional mask + optional eye colours.
- `SlimePaletteTexture`: one per combination (`<element>`, `<element>_plain`, `<element>_eyes`), registered lazily and rebuilt on reload. The old GPU texture is closed on each rebuild (**[REAL]** reload checks).
- `SlimeTestRenderer`: single-pass for palette and plain modes, full model versus body model.
- `SlimeTestModel.full()/body()/eyes()`.
- `SlimeTestRenderMode` gains `PLAIN` (palette only, for before/after).
- `SlimeTestEyeStyle` (`plain` | `element`), with entity NBT `Eyes`.

**Costs:**
- **Texture generation:** at load or reload only, never per frame: 128×128 texels per texture, plus one 128×128 mask read.
- **Memory:** 64 KiB per generated texture. Seven are in use in gameplay, plus the dev comparisons.

**Performance [REAL]:** scene 96.
- **Setup:** 0/50/100/200 visible slimes, `tint` (two-pass V1 method) versus `palette` (final single-pass), 2 repetitions, about 10 s each, 1880×1012, render distance 12.
- **Results:** pooled medians below; full tables in `evidence/perf_summary_{opengl,vulkan}.md`.

| Backend | Slimes | Tint frame ms | Final frame ms | Δ final − tint | Δ GPU solid-features ms |
|---|---|---|---|---|---|
| OpenGL | 50 | 1.561 | 1.530 | −0.031 | +0.006 |
| OpenGL | 100 | 2.375 | 2.248 | −0.127 | −0.007 |
| OpenGL | 200 | 4.169 | 3.988 | −0.182 | +0.048 |
| Vulkan | 50 | 1.935 | 1.843 | −0.092 | +0.051 |
| Vulkan | 100 | 2.758 | 2.876 | +0.118 | +0.053 |
| Vulkan | 200 | 4.816 | 4.997 | +0.181 | +0.094 |

**Reading the results:**
- **OpenGL:** the final textures are within run-to-run noise of the tint method. The per-repetition differences change sign.
- **Vulkan:** about **+0.1 to +0.2 ms per frame at 100–200 slimes**, consistent across both repetitions. The CPU features cost is +0.02 ms. The likely reason is more distinct entity textures than in tint mode (several generated textures versus one grayscale texture), so batches split by texture. In a normal world, with a handful of slimes, this is negligible.
- **Eye style:** element eyes were not measured separately. They use the same model, the same pass and the same number of textures per slime; only the texel colours differ.

---

## 9. Full screenshot comparisons [REAL]

All OpenGL screenshots are in `screenshots/opengl/` (66 plus 7 performance). `screenshots/vulkan/` holds the Vulkan lineups, reload and pack shots and two performance shots (22). The Vulkan close-ups duplicate OpenGL pixel for pixel on the slimes, so they were left out to keep the bundle size down. The sheets are made from these screenshots.

| Sheet | Content |
|---|---|
| `00_source_artwork_masks.png` | [SIM composite] Mask channels and the composed texture per element |
| `01_lineup_front_gray_before_final.png` | Grayscale, then before (palette only), then final; front lineup |
| `02_lineups_before_after.png` | Before/after: front, three-quarter, high, gameplay 8 blocks |
| `03_lineups_all_sides.png` | Final: front, three-quarter, side, rear, high, 14 blocks |
| `04_element_closeups.png` | Each element: front, three-quarter, high |
| `05_element_before_after.png` | Each element, plain versus final, front and high |
| `06_eyes_plain_vs_element.png` / `07_lineup_eyes.png` | Optional eye-rim comparison |
| `08_lighting.png` | Day, shade, dusk, night |
| `09_opengl_vs_vulkan.png` | Same scenes on both backends |
| `10_reload_resource_pack.png` | 3 reloads; motif-pack override on, removed, on Vulkan |

**Observations:**
- **Coherence:** the patches, spots and caps continue cleanly across the stepped cuboid layers in the side, three-quarter and high views. No details are cut off at face borders and there are no mismatched faces.
- **Gameplay distance (8 and 14 blocks):** all seven are clearly distinct.
  - **Geo:** the dark dome and patches read as stone.
  - **Dendro:** the moss cap reads as such.
  - **Cryo:** the snow cap and its scallops read.
  - **Pyro:** the patches read.
  - **Anemo, Electro and Hydro:** their finer details (curls, brackets, mottling) become subtle, as intended for these elements; their identity comes from the palette.
- **Lighting:**
  - **Day and shade:** all details readable.
  - **Dusk:** fine.
  - **Night:** Hydro and Cryo remain the closest pair, as noted in earlier reports. Cryo's snow cap now separates them better than the palette alone did.
- **OpenGL versus Vulkan:** the slimes render identically. The only differing pixels are the background (water and landscape). Both the grayscale and final lineups differ by the same 0.72 %, so the slimes contribute no difference.

---

## 10. Build, tests and reload

**Build:**
- **Full build:** `./gradlew build --rerun-tasks --offline` → **BUILD SUCCESSFUL**, **2506 tests, 0 failures, 0 errors, 2 skipped** (pre-existing skips). `evidence/gradle_build.log`.
- **Slime tests** (30, all pass):

| Class | Tests | Status |
|---|---|---|
| `SlimePaletteTest` | 12 | updated |
| `SlimeMotifTest` | 7 | new |
| `SlimeUvMappingTest` | 2 | new, permanent |
| `SlimeTestRegressionTest` | 4 | hash and texture-dir rule updated |
| `SlimeTestVariantTest` | 5 | `plain` mode, eye style, `Eyes` NBT |

**Reloads [REAL], on both backends** (`evidence/hologram-capture-{opengl,vulkan}.log`):
- **F3+T × 3:** each time, the same set of 21 registered textures (every palette, plain and eye combination the earlier scenes had created) was reused, the old GPU textures were closed and each texture was regenerated exactly once (PASS × 3).
- **Resource pack:** a pack replacing `motifs/geo.png` with an empty mask was selected and the resources reloaded. Geo then showed the palette only, and removing the pack restored the details (PASS × 2). See sheet 10.

**Other checks:**
- **Generator determinism:** regenerating the masks gives byte-identical PNGs (`evidence/motifs_sha256.txt`).
- **Patches:** applied with `git apply` to a clean `git archive HEAD`, they match the working tree byte for byte. That copy builds, and its 2506 tests pass. `evidence/verify_apply_build.log`.
- **Clean-up:** the temporary capture scenes (95/96) are removed. `HologramCapture.java` is back to its pre-task hash `9535767b…`, and `SlimePaletteCapture.java` is not in the tree. The evidence copy is in `tools/`.

---

## 11. Remaining limitations

- **Hydro versus Cryo at night:** this is inherent to the two blue palettes. The snow cap helps; a stronger fix would be a lighter Cryo body or a night-only tweak.
- **Fine details on Anemo and Electro** are mostly a close-range feature. Thicker strokes would make them read better at 8+ blocks but drift from the delicate reference look.
- **Vulkan:** about +0.1–0.2 ms at 100–200 slimes versus the tint method. Batching all elements in one atlas texture would remove it, but isn't worth doing at V1 counts.
- **Masks are tied to the current UV layout.** If the model or texture layout changes, run the generator again (one command).
- **Eye rims** are pending your decision (§7).
- **Still a dev entity:** `totality:slime_test` has no AI, animation or per-element entity types. That is unchanged by this task.

---

## 12. Future geometry recommendations (not done)

Small-Slime-only add-ons, as separate groups under `root`:

| Element | Add-on |
|---|---|
| Anemo | Two small wings on the sides at about y 8, the curls sitting under them |
| Geo | 2–3 dark rock shards on the dome, plus an optional clover sprout |
| Electro | An antenna stalk on top with a small bulb (bulb emissive in the VFX phase) |
| Dendro | 2–3 fern or grass fronds on top, leaning back |
| Cryo | Small ice crystals spaced along the snow-cap rim |
| Hydro, Pyro | No geometry. Pyro gets flames and glow in the VFX phase |

The dome top is at y 15; the face area is x −5…5, y 3…9 on z −9 (README).

---

## 13. Exact files changed (this task, patch 02)

| Status | File |
|---|---|
| M | `Context/Assets/Models/slime_base/export_java.py` (Option A, line 57) |
| M | `Context/Assets/Models/slime_base/export/SlimeBaseGeometry.java` (regenerated) |
| M | `Context/Assets/Models/slime_base/README.md` (fix documented) |
| A | `Context/Tools/slime-motifs/make_slime_motifs.py` |
| M | `src/main/java/zcylas/totality/client/entity/slime/SlimeBaseGeometry.java` (copy of the export) |
| D | `src/main/java/zcylas/totality/client/entity/slime/SlimeBaseGeometryUvFix.java` |
| A | `src/main/java/zcylas/totality/client/entity/slime/SlimeEyeColours.java` |
| A | `src/main/java/zcylas/totality/client/entity/slime/SlimeMotif.java` |
| M | `.../client/entity/slime/SlimePalette.java` (`lerp` package-private) |
| M | `.../client/entity/slime/SlimePaletteMapper.java` |
| M | `.../client/entity/slime/SlimePaletteTexture.java` |
| M | `.../client/entity/slime/SlimePalettes.java` (`motifFor`, `eyesFor`) |
| M | `.../client/entity/slime/SlimeTestModel.java` |
| M | `.../client/entity/slime/SlimeTestRenderState.java` |
| M | `.../client/entity/slime/SlimeTestRenderer.java` |
| M | `.../client/entity/slime/SlimeTexelHeights.java` (UV-fix variant removed) |
| M | `src/main/java/zcylas/totality/entity/dev/SlimeTestEntity.java` (`Eyes`; `UvFix` removed) |
| A | `src/main/java/zcylas/totality/entity/dev/SlimeTestEyeStyle.java` |
| M | `src/main/java/zcylas/totality/entity/dev/SlimeTestRenderMode.java` (`PLAIN`) |
| A | `src/main/resources/assets/totality/textures/entity/slime_test/motifs/{anemo,geo,electro,dendro,hydro,pyro,cryo}.png` |
| A | `src/test/java/zcylas/totality/client/entity/slime/SlimeMotifTest.java` |
| A | `src/test/java/zcylas/totality/client/entity/slime/SlimeUvMappingTest.java` |
| D | `src/test/java/zcylas/totality/client/entity/slime/SlimeUvMappingComparisonTest.java` |
| M | `src/test/java/zcylas/totality/client/entity/slime/SlimePaletteTest.java` |
| M | `src/test/java/zcylas/totality/entity/dev/SlimeTestRegressionTest.java` |
| M | `src/test/java/zcylas/totality/entity/dev/SlimeTestVariantTest.java` |

**Totals:**
- **Patch 02:** 32 files, +1107 / −443.
- **Patch 01:** the earlier uncommitted slime work (V1 integration, palettes, refinement), HEAD → pre-task state, 43 files. It is kept separate as `patches/01_preexisting_slime_work_before_this_task.patch`.

**Not touched:**
- `HologramCapture.java` (restored to its pre-task state).
- `slime_base.bbmodel` and `slime_base.png`.
- All non-slime files.
- The unrelated uncommitted work in the tree.

---

## 14. Questions

1. **Final V1 look:** do you approve the seven final textures (sheets 02–05) as the Small Slime V1 appearance?
2. **Eyes:** choose one:
   - **(a)** keep the plain dark rims (recommended for readability);
   - **(b)** adopt the measured element rims as shown;
   - **(c)** element rims darkened to about 60 % brightness, as a follow-up comparison.
3. **Anemo and Electro detail strength:** keep them subtle (current), or make the strokes thicker for readability at distance?
4. **Hydro and Cryo at night:** accept as is, or try a small Cryo body adjustment in a later pass?
5. **Commit:** once approved, should the slime work be committed as one commit, or as two (patch 01 = V1 integration and palettes, patch 02 = final textures)?

After this review, development moves to **Eldritch Blast V2**, then **Magic Missile V2**.
