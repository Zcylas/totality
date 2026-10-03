# Totality — Eldritch Blast V2: BG3 Visual Refinement — Report

Date: 2026-10-03 · Baseline: the uncommitted Eldritch Blast V2 state described in
`TOTALITY_ELDRITCH_BLAST_V2_IMPLEMENTATION_REPORT.md` (that report and its bundle are unchanged) · **Nothing committed, pushed or tagged.**
Review bundle: `Context/Audit/Review Bundles/TOTALITY_ELDRITCH_BLAST_V2_REFINEMENT_REVIEW.zip`

## 1. Summary

The existing V2 implementation was refined, not rebuilt. Gameplay, the travelling projectile, multi-beam (0.2 s), audio, Fireball V2 / Heat Vision V2 and the shared VFX systems are untouched. Eight files changed (5 source/shader, 3 tests).

- **Teal is now the default**; violet stays as a development palette (`/totalityvfx eldritch palette violet`).
- **Beam progression**: a white-hot discharge 6× the beam width that contracts within 90 ms (cubic falloff) into a thin, white-cored teal streak; strands at ~25 % strength; the lit beam lasts 0.12 s before it starts travelling as a ~6-block streak, and collapses in 0.12 s.
- **Release**: a dedicated release-flash term brightens and whitens the core and boosts the emissive contribution ×3, falling off as fast as the width. No screen flash; no new shake.
- **Impact**: concentrated white-hot core, long sharp spikes, 24 fast needle sparks, the ring reduced to a faint secondary element; 0.26 s instead of 0.36 s.
- **Residue**: an angular zig-zag black fracture with irregular thickness and tapered thorns on both sides that breaks up unevenly as it fades; smoke almost removed. Lifetime unchanged.

Verified in a real client on OpenGL and Vulkan (478 frames each, 6/6 in-game checks), `./gradlew build --rerun-tasks` with 2525 tests (0 failures) — run **with the private reference audio removed** to prove a clean checkout builds and runs.

## 2. Reference findings (re-examined)

Sources: `Context/References/Videos/eldritch_blast_tiktok.mp4` (primary) and `Context/References/Other/bg3_six_stage_storyboard.jpg` (panels at 3.07 / 3.27 / 3.53 / 3.67 / 3.77 / 4.03 s). YouTube not used (montage/build overlays).

Measured frame by frame (30 fps) on two casts (release ~3.50 s and ~10.47 s), teal-saturation mask across the beam's mid-path rows:

| Time after release frame | Beam width (teal px/row, median) | White core px/row | Total teal energy |
|---|---|---|---|
| 0 ms (release) | **59** | **37** | 25 150 |
| +33 ms | **8** | 8 | 9 996 |
| +66 ms | 8 | 1 | 4 132 |
| +100 ms | 0 – 9 | 0 | ~4 200 |
| +200–300 ms | 0 | 0 | ≤ 1 500 → 0 |

- The discharge contracts **~7:1 within one frame** (33 ms); the thin beam is visible for ~100–130 ms; the teal energy is gone by ~200–300 ms; the black fracture replaces it (storyboard 5 → 6) and lingers past 4.0 s.
- Colour of the beam pixels (sRGB, over a brown floor): mint/cyan-teal ~(140–165, 205–220, 180–190) — G > B > R, a near-white core.
- Impact (storyboard 4–5): a starburst of long thin streaks at the target, not a round explosion.
- Residue (storyboard 6): black, thorny, branching, irregular — like a dead bramble suspended along the path.
- Editor artefacts ignored: zoom punches 0.57 s / 0.27 s before every release, radial blur, the music drop.

## 3. Differences found in V2 and their causes → changes

| BG3 vs V2 | Cause in V2 | Change (exact) |
|---|---|---|
| Release not forceful; contraction too slow | `RELEASE_WIDTH` 2.6, `RELEASE` 0.15 s, quadratic | `RELEASE_WIDTH` **6.0**, `RELEASE` **0.09 s**, width falloff `(1-k)^3`; new `releaseFlash = (1-k)^2` |
| Beam too thick / rope-like | `BEAM_HALF_WIDTH` 0.22, strands at full strength, halo alpha 0.9 | `BEAM_HALF_WIDTH` **0.08**; strands amplitude 0.55→0.35, width 0.11→0.07, **×0.25**; glow alpha ~0.5 (0.85 during the flash); core 28 % of the ribbon (50 % at release) |
| Beam lingers like a laser | `HOLD` 0.30 s, `COLLAPSE` 0.22 s | `HOLD` **0.12 s**, `COLLAPSE` **0.12 s** |
| Release light not concentrated | emissive constant | beam glow tint ×(1 + 2·flash), emissive demand ×(1 + 2·flash); glow line 0.05→0.04 |
| Impact = round magic explosion | strong ring, broad rays (pow 16), 18 slow sparks, 0.36 s | flash `exp(-16r²)` (concentrated), ring ×0.35 and thinner, spikes pow 70/50 reaching the sprite edge, `LIFE` **0.26 s**, **24** sparks at 8–18 b/s living 0.18–0.42 s, spark width 0.05→0.03, tail 0.05→0.07 s, sprite 1.5→1.3 |
| Residue = uniform wavy line + smoke | smooth noise centreline, constant width, smoke 0.3, coarse 10-step across-quantisation | piecewise-linear zig-zag (0.32-block segments), thickness 0.09–0.25 varying, up to two tapered thorns per 0.5 block (both sides, 0.35–0.95 reach), 40-step quantisation, dissolve fade, smoke 0.06; ribbon half-width 0.40→0.55 |
| Violet | `Palette.VIOLET` default | `Palette.TEAL` default; teal mid (0.12,0.86,0.64)→(0.10,0.86,0.72), inner (0.62,1,0.86)→(0.60,1,0.90) |
| Anticipation (stage 1–2) | dark wisps 0.6 | a dark gathering ring in the first half of the 75 ms flare + wisps 0.8; still 75 ms (no gameplay delay) |

Shader parameter packing: the beam's single parameter byte now carries the release flash below 0.5 and the collapse above 0.5 (`EldritchBeamTrack.beamParam()`, unit-tested); they never overlap.

## 4. Iterations (all real client, same cameras as the V2 comparison)

1. **R1** — all changes above with `RELEASE_WIDTH` 7. Release/contraction, thin beam and impact matched the reference well; the residue broke into dotted speckles (crack thinner than the across-ribbon quantisation) and the thorns did not show; the close-range release was very large.
2. **R2 (final)** — residue only: 40-step quantisation, thicker crack and thorns, gentler dissolve; `RELEASE_WIDTH` 7→6. Result: a readable thorny fracture at close and side distances.

Two iterations; no further experiments.

## 5. Verification

- Captures: `refine_r1` (GL), `refine_r2` (GL, final look), `refine_final_vk` (**Vulkan**, final code, private audio absent). 478 frames each; checks 6/6 PASS in each: one cast = one cast + one impact sound; full cleanup; 2/3/4 beams fire 2/3/4; cleanup after 20 concurrent beams.
- Views compared before/after with identical cameras and frame indices: side, behind, target close, caster close (release), first person (HUD), far, night side, 4-beam side. OpenGL vs Vulkan final frames look identical.
- First person: one very bright, wide frame at the hand, then a thin teal streak to the crosshair; the HUD stays readable.
- Day/night: the teal core reads in daylight without a tube look; at night the beam and spikes glow, the black residue is subtle (as in BG3's dark scene).
- Tests: `./gradlew build --rerun-tasks` → BUILD SUCCESSFUL, **2525 tests, 0 failures, 2 pre-existing skips** (1 new test: release flash/param encoding; wiring test now asserts the teal default). Two of my new assertions first failed on floating-point edge values (1e-32 residue, `start+d-start < d`) — fixed with tolerances in the tests, no code change.
- Clean-checkout audio check: with `reference_private/` moved away, the build passes, the jar contains no private audio, and the Vulkan client run logs only `File totality:sounds/spell/eldritch_blast/reference_private/{cast,impact}.ogg does not exist, cannot add it to event …` (WARN) — all checks pass. The folder was restored and is still git-ignored (`!!` in `git status --ignored`).
- Accidental generated file removed: `Context/Tools/eldritch-blast-v2/__pycache__/` (it was created when I imported the sound generator during the V2 task). It never was in a patch; it is not in this one.

Performance (whole-lifecycle averages, `TimerQuery` around the single draw):

| Scene | Before (V2) GL | After GL | After Vulkan |
|---|---|---|---|
| 1 beam GPU / CPU | 0.009 / 0.004 ms | 0.010 / 0.004 ms | 0.016 / 0.003 ms |
| 20 beams GPU / CPU | 0.066 / 0.036 ms | 0.068 / 0.037 ms | 0.076 / 0.030 ms |
| Peak quads (20 beams) | 647 | 595 | 577 |

The thinner beam is **not** measurably cheaper: geometry is the same segment count, the residue shader now loops over 6 thorn candidates per fragment, and the 24 sparks add quads; differences are within run-to-run noise (~5 % for these tiny numbers). Single-beam Vulkan 0.016 ms vs GL 0.010 ms is a backend difference on tiny timings.

## 6. Remaining limitations

1. **No real buildup**: BG3's stages 1–2 last ~0.5 s on the whole body; Totality has 75 ms at the hand (instant cast kept as instructed; needs the Player Animation API and/or an approved cast delay).
2. At **very close range** (camera ~1.5 blocks from the beam) the one-frame release is a large white bar (~10 % of the screen). It matches the BG3 intent but is strong; lower `RELEASE_WIDTH` if it bothers you.
3. Per-tick captures (50 ms) show the 90 ms release in 1–2 frames; judge the snap live.
4. Our beam still follows the real projectile (0.25 s to 14 blocks) whereas BG3 is effectively instantaneous; for long shots it is a travelling streak rather than a full-length line. Making it hitscan would be a gameplay change.
5. The residue is a camera-facing ribbon: seen exactly end-on it collapses to a short mark.
6. Night: the black residue is hard to see in dark scenes (it is in BG3 too).
7. Audio untouched and still pending your listening decision (custom default, private reference option).

## 7. Files changed (refinement only)

`client/vfx/eldritch/EldritchBeamTrack.java` (timeline constants, `releaseFlash`, `beamParam()`), `EldritchImpact.java` (life, sparks), `EldritchBlastVfx.java` (widths, teal default, flash-driven glow/demand, doc), `dev/EldritchBlastDev.java` (palette sequences now film the violet option), `shaders/core/vfx_eldritch.fsh` (beam, flare, impact, residue, teal tint), tests `EldritchBeamTrackTest`, `EldritchImpactTest`, `EldritchBlastV2WiringTest`. Plus this report.

Patch: `patch/eldritch_blast_v2_refinement_only.patch` in the bundle — against the uncommitted V2 state (snapshot taken before any refinement edit). Verified: applied to that snapshot it reproduces the current files byte-for-byte. To rebuild the full state from `a77c2353`: apply the original V2 bundle's `eldritch_blast_v2_task_only.patch`, then this one.

## 8. Decisions for you

- **Approve the refined look?** (teal default, thin focused beam, spike impact, thorny residue)
- **Close-range release width** (6×) — keep, or tone down?
- **Sound** — still pending (custom vs private reference), as agreed.
- Then: commit Eldritch Blast V2 (without the private audio) → Magic Missile V2.

## 9. Commit preparation (approved 2026-10-03)

Approved as delivered: teal default (violet development option), 6× release, thin beam, refined impact and thorny residue, custom sound as the (provisional) default, instant cast, multi-beam unchanged.

One supporting change before the commit, to make a clean installation warning-free while keeping the private comparison workflow:

- The private reference sounds moved from `assets/totality/sounds/spell/eldritch_blast/reference_private/` into a **git-ignored built-in resource pack** `src/main/resources/resourcepacks/eldritch_reference_private/` (its own `pack.mcmeta` (format 88), `assets/totality/sounds.json` with the two `*_reference` events, and the two OGGs). `.gitignore` now ignores that folder instead of the old one.
- The main `sounds.json` no longer defines the two reference events (they referenced files a clean checkout does not have → "File … does not exist" warnings at every launch).
- `EldritchBlastVfx.register()` registers the pack (`ResourceLoader.registerBuiltinPack`, always enabled) **only if** the mod contains `resourcepacks/eldritch_reference_private`.
- `ModSounds.ELDRITCH_BLAST_*_REFERENCE` are created without registry registration (the client plays them by id). A registered event without a definition logged "Missing sound for event" at every launch.

Verified:
- **Clean installation** (private pack moved out of the tree): `./gradlew build --rerun-tasks` → BUILD SUCCESSFUL, 2525 tests, 0 failures, 2 skipped; the jar contains no `resourcepacks/` or reference audio; client launch logs no Eldritch warning. Selecting the development-only reference variant then logs "Unable to play unknown soundEvent" once per event (expected without the private files; never happens by default).
- **Private copy** (pack present): the client lists `totality:eldritch_reference_private` in its resource packs, no warnings, and a recording of the game's own audio stream shows the three reference casts playing at −30 to −36 dBFS, like the custom casts.
- Visual code is unchanged since the final OpenGL/Vulkan refinement captures.
