# VFX Experiment 3 — Fireball V2: Phase B2.1 Focused Explosion Refinement

Date: 2026-10-02 · Status: **refinement implemented and stopped for review.** B3–B5 have not started. Nothing has
been committed or pushed, and the Incense commit `ad70d9d1` and all unrelated work are untouched. Minecraft 26.2.
Review bundle: `Context/Audit/Review Bundles/TOTALITY_VFX_FIREBALL_V2_B2_REFINEMENT_REVIEW.zip`

**Labels:** **[Measured]** = recorded in this task (real client, automated test, build) · **[Inference]** =
interpretation of measured data · **[B1–B2]** = evidence from the earlier phase, not re-collected here.

---

## 1. Summary

The B2 architecture is preserved. It is still one depth-tested, premultiplied draw of about 80 independently
animated, procedurally shaded fire volumes, plus tongues, ground licks and an ignition core. The refinement changes
only **layout, motion and a few shading parameters**:

* **A. Violent initial expansion.** Each body volume is now *blasted* outward: its speed decays exponentially
  instead of following a shared 0.30 s ease-out. The outer flame front is fastest (about 90 % of its travel by
  0.10 s), the middle layer follows and the core trails. Fast volumes are stretched along their outward direction,
  and there is a short shock heat, strong in the core and weak at the front. Tongues and ground licks shoot out
  roughly twice as fast. The explosion now reads as **blast → turbulent burning → break-up** rather than fire
  that grows in place.
* **B. Cohesive outer front.** The middle and outer layers are spread evenly over the sphere (a jittered Fibonacci
  lattice) instead of in random directions, which caused clumps and gaps. The outer front has more and larger
  volumes, a ragged, partly eroded edge through which the gold heart shows, and wider, overlapping ground licks.
  The silhouette stays irregular, and there is no shell.
* **C. Apparent radius.** The outer front no longer reserves room for late growth and drift, so its visible edge
  sits at the 6-block boundary. The strict containment rule (no hot fire past the damage radius) is unchanged and
  still enforced by the same test. **The dense body's median reach rose from 4.43–4.50 to 5.07 blocks.**
* **Performance stayed in line.** On OpenGL, one explosion takes 0.302 → 0.308 ms GPU and twenty take
  2.769 → 2.796 ms, both within run-to-run variation. Vulkan: 0.310 → 0.323 ms and 2.827 → 2.852 ms. A first
  attempt cost +15–19 %, and I recovered that by trimming fire area (§7).
* A **development-only radius marker** (`/totalityvfx fireball marker on|off`) and a **development-only
  slow-motion clock** were added for this review. Both are unreachable outside a development environment.

## 2. Diagnosis of B2 (before changing anything)

Real-client frames and a new model metric (§5) showed three causes:

| problem | cause in B2 |
|---|---|
| "rising mass of fire" rather than a detonation | every volume used the same 0.30 s cubic ease-out and grew from 45 % size *in step*, so the whole mass swelled uniformly; volumes faded in over 40 ms, which hid the first frames |
| separate groups around the central mass | directions were uniformly random, so 32 small outer volumes (1.2–2.0 blocks) on a hemisphere left large gaps and clumps; ground licks were narrow and separate |
| body reads 4.5–5.5 of 6 blocks | the quad-inside-radius rule reserved headroom for late growth (×1.18) and drift (×1.04) on **every** layer, so the outer front sat at about 4.9 blocks with small visible cores |

## 3. What changed and why

All changes are in `FireExplosion` (the model), plus the stretch in `FireExplosionRenderer` and one shader line.
Source diffs against the B2 code are in the bundle's `diffs/`.

### A. Expansion

| | B2 | B2.1 | why |
|---|---|---|---|
| body travel | `easeOut3(age / 0.30 s)` for all | `1 − e^(−age/τ)`, τ = 0.30 s × {0.35, 0.22, 0.15} ±20 % (core 105 ms, middle 66 ms, outer **45 ms**) | a blast: maximum speed at once, then burning in place; differential speeds (front leads, core trails) avoid uniform growth |
| travel done at 0.10 s | 70 % (all layers) | core 61 %, middle 78 %, **outer 89 %** | the front reaches the boundary within about 0.1 s |
| size at launch | 45 % | 35 % | more contrast between the compact ignition and the expanded mass |
| outward stretch (new) | — | up to 1.4 / 2.0 / 2.6× (core / middle / outer) at launch, fading with speed; along the volume's outward direction projected on screen; foreshortened when the motion is toward the camera | visible outward momentum (streaks), most noticeable close up and from inside |
| fade-in | 40 ms | 12 ms | the 40 ms fade hid the launch |
| shock heat (new) | — | heat × (1 + {0.35, 0.18, 0.05} e^(−age/50 ms)) per layer | a white-hot ignition against an orange expanding front (contrast between ignition and expansion) |
| ignition core | 1.2 → 3.4 blocks, linear over 0.10 s, fades 0.03–0.12 s | 1.0 → 4.0 blocks, exponential (τ 30 ms), fades 0.02–0.11 s | a sharper flash |
| tongues | reach full length in 0.22 s, 0.75–0.95 R, start ≤ 40 ms | **0.12 s**, 0.80–0.97 R, start ≤ 20 ms | jets that lead the blast |
| ground licks | run out in 0.30 s, start ≥ 20 ms | **0.18 s**, start ≥ 10 ms | the ground front moves with the blast |

Unchanged: hold-then-cool temperatures, cooling constant, lives, erosion timing, break-up, soot buoyancy, the
total duration (≤ 1.45 s; `totalSeconds` ≤ 1.6 by test), the Screen FX values and the emissive part.

### B. Cohesion

| | B2 | B2.1 |
|---|---|---|
| layer split (80 volumes) | 22 % core / 38 % middle / 40 % outer, uniformly random directions (mirrored out of the hit surface) | 18 % / 34 % / **48 %** (14 / 27 / 39); middle and outer on a **jittered Fibonacci lattice** over the sphere, or over the hemisphere outside the hit surface, turned randomly per explosion; core random |
| outer volume size | 1.2–2.0 blocks | 1.6–2.6 blocks (neighbours overlap) |
| middle volume size | 2.2–3.4 blocks | 2.0–2.9 blocks (mostly hidden behind the front now; trimmed for cost) |
| outer front edge | clean until erosion starts | base erosion 0.18 (middle 0.05): a ragged, holed front through which the hot heart stays visible (preserves the temperature layers) |
| ground licks | 0.9–1.4 wide, ring at 0.80–0.95 | **1.5–2.2 wide** (overlapping), ring at 0.84–0.96, height clamped to the sphere |
| shader | density edge 0.80 | 0.84, plus a soft fade at the quad border (no straight cut edges when the warped flame reaches the quad edge) |

Preserved: white-gold ignition, gold heart, orange body, red edges, cooling soot, irregular flame shapes, the
break-up animation, and the wall, floor and air handling (mirroring and obstruction are the same code).

### C. Radius

* The outer front no longer grows late (×1.18) or drifts outward (×1.04); those remain on the inner layers, which
  billow. So the outer volumes sit right at `R − ½ quad`, jittered inward by 0–12 % for a ragged silhouette.
* The outward stretch holds the leading edge in place: the renderer pulls the volume's centre back by exactly the
  stretch, so the farthest visible point is still within `distance + ½ quad` (triangle inequality). The containment
  bound is therefore unchanged.
* Ground-lick tips are clamped inside the sphere. B2 relied on the ring fraction for this.
* **Containment is unchanged:** `FireExplosionTest.fireNeverReachesPastTheDamageRadius` (20 seeds, every 0.02 s,
  every element with heat ≥ 0.3) passes unmodified. Only cooled soot may drift past the radius, as before.
  The server-side radius (6) is untouched.

## 4. Development tooling (development environment only)

* **Radius marker:** `/totalityvfx fireball marker on|off` or `FireballV2Dev.setMarker`. Vanilla `Gizmos` lines are
  drawn each client tick around every live explosion: the exact silhouette circle of the 6-block damage sphere as
  seen from the camera (cyan), and its equator (green). `FireballV2Dev` is only registered when
  `VerificationReporter.isDevEnvironment()` (asserted in `FireballV2WiringTest`), so the marker cannot appear in
  normal play. A text label was tried but text gizmos did not render in this context, so it was removed.
* **Slow-motion clock:** `FireExplosionRenderer.setTimeScale`, forced to 1 outside development (asserted). The
  capture uses 0.25× to film the first 0.3 s at 12.5 ms per frame.
* **Capture set:** scene 69 with `-Dtotality.fireball.v2.refine=true`. The *same* set ran on B2 and B2.1: real
  casts (ground, wall, high angle, night, open air, camera at 7 and 3 blocks), isolated explosions without gameplay
  fire, a camera inside an isolated explosion, the marker from the front and from above, two 4× slow-motion
  expansions, then the 1 / 20 explosion measurements and the cleanup checks. Isolated sequences now clear
  leftover particles first: debris and fire-block smoke from earlier casts drew over the larger B2.1 fire.
* **Media:** `Context/Tools/vfx-fireball-v2/make_refinement_media.py <before run> <after run> <out>` builds the
  side-by-side sheets and GIFs.

**Baseline method:** B2 was re-captured with the final capture code by temporarily swapping in a snapshot of the B2
explosion sources. The snapshot reproduced the B2 model metrics exactly (§5). B2.1 was then restored, and the full
build and tests re-ran afterwards.

## 5. Apparent explosion radius [Measured]

**Model metric** (`FireExplosionShapeTest`, ground hit, 12 seeds, 600 directions over the upper hemisphere; each hot
element approximated by its dense visible core): the farthest dense, hot fire along each direction.

| t (s) | B2 median | **B2.1 median** | B2 p90 | **B2.1 p90** | shell ≥ 5.0 blocks covered B2 → B2.1 | ≥ 5.5 B2 → B2.1 |
|---|---|---|---|---|---|---|
| 0.05 | 2.02 | **3.62** | 2.38 | 3.99 | 0 → 0 % | 0 → 0 % |
| 0.10 | 3.22 | **4.66** | 3.72 | **5.11** | 0 → 17 % | 0 → 0 % |
| 0.15 | 3.94 | **4.94** | 4.62 | 5.68 | 0 → 46 % | 0 → 16 % |
| 0.30 | 4.43 | **5.07** | 5.42 | 5.80 | 34 → **54 %** | 5 → **22 %** |
| 0.45 | 4.50 | **5.07** | 5.46 | 5.80 | 35 → **54 %** | 7 → **22 %** |
| 0.60 | 4.40 | **4.90** | 5.45 | 5.45 | 32 → 44 % | 7 → 7 % |
| 0.90 | 2.11 | 1.87 | 2.60 | 2.47 | 0 → 0 % | 0 → 0 % |

The test now guards the refinement: p90 ≥ 4.8 at 0.10 s, median ≥ 4.9 and coverage of the 5-block shell ≥ 45 % at
0.30 s.

**Real client with the 6-block marker** (`05_radius_marker_front`, `06_radius_marker_top`, `01_slowmo_expansion_marker`,
`anim_radius_marker_top.gif`): in B2 the fire fills about two-thirds of the marker dome, and from above it leaves a
wide empty ring. In B2.1 the ragged edge of the fire reaches the cyan outline from the front and fills the circle
from above, while staying inside it. **[Inference]** It now communicates the full radius without suggesting a larger
one. From about 0.45 s, as the front cools to red and erodes, the visible top sits about 0.5–1 block below the
outline.

## 6. Before/after visual comparison (real client)

All images are Minecraft client screenshots: scene 69 refine set, runs `b2base_gl` (B2) and `b21_gl` (B2.1),
OpenGL, 1880×1052 window. Vulkan sheets are in `media_vulkan/`. Every sheet has B2 on top and B2.1 below; every GIF
is side by side. Frames are one per game tick, so normal GIFs play at real time (20 fps). The slow-motion GIFs
play at 0.25×.

| requested | files |
|---|---|
| 1 beginning of expansion | `01_slowmo_expansion_marker.jpg`, `02_slowmo_expansion_ground_view.jpg` (12.5 ms steps), `anim_slowmo_4x_marker.gif`, `anim_slowmo_4x_ground_view.gif`, `full_*_beginning_0.10s.jpg` |
| 2 maximum expansion | `full_*_maximum_0.30s.jpg`, `full_*_maximum_0.45s.jpg`, sheets 03–04 |
| 3 cooling and break-up | `full_*_cooling_0.90s.jpg`, `full_*_breakup_1.20s.jpg`, sheets 03–04 (0.45–1.20 s) |
| 4 high angle | `07_high_angle.jpg`, `anim_high_angle.gif`, `06_radius_marker_top.jpg` |
| 5 wall | `08_wall.jpg`, `anim_wall.gif` |
| 6 ground | `03_ground_real_cast.jpg`, `anim_ground_real_cast.gif` |
| 7 night | `09_night.jpg`, `anim_night.gif` |
| 8 without the gameplay fire blocks | `04_ground_isolated_no_fire_blocks.jpg`, `anim_ground_isolated.gif`, `full_*_v2_ground_isolated_*.jpg` |
| 9 six-block marker | `05_radius_marker_front.jpg`, `06_radius_marker_top.jpg`, `01_slowmo_expansion_marker.jpg`, `anim_radius_marker_top.gif`, `anim_slowmo_4x_marker.gif` |
| 10 camera near / inside | `11_camera_near_7.jpg`, `12_camera_inside_3.jpg`, `13_camera_inside_isolated.jpg`, `anim_camera_inside_3.gif` |
| also | `10_open_air.jpg`; `iterations/` (look-development passes, §8) |

What the frames show **[Inference from the frames]**:

* **Expansion:** in slow motion, B2 is a small blob that swells for about 0.2 s. B2.1's front is already half the
  dome height at 0.05 s and reaches the outline by about 0.1 s. Behind it the core keeps billowing. At real speed
  the first frame after the flash already shows the mass thrown outward, and the radial streaks are clearly visible
  from inside the blast (`13_camera_inside_isolated`).
* **Cohesion:** the outer ring of separate small flames (B2, 0.30–0.60 s) is gone. The front is one connected,
  ragged mass that runs into the ground licks, with red edges and holes through which the gold heart shows. From
  above (`06`, `07`) B2 starts as a cluster of separate pieces, while B2.1 fills the circle as one mass.
* **Cooling and break-up** follow the same sequence and timing as B2: red, then soot, then holes, then gone by
  about 1.4 s.
* **Wall, confined and air:** the fire stays on the open side of the wall and in the bunker it fills the room and
  spills out of the doorway, as in B2 (full regression run `b21full_gl`). An air burst is a full, rounder ball.

## 7. Performance [Measured, RX 6600, Mesa 26.2.3, 1880×1052]

GPU = `TimerQuery` around the single explosion draw, averaged over 120 ticks of looping client-only explosions at
real speed, from the same camera. Each row is one capture run.

| run | backend | 1 explosion GPU | 20 explosions GPU | CPU (20) | elements avg / peak (20) |
|---|---|---|---|---|---|
| B2 `b2base_gl` (final baseline) | OpenGL | 0.302 ms | 2.769 ms | 0.252 ms | 1,464 / 1,887 |
| B2 `b21base_gl` (first baseline, same code, earlier) | OpenGL | 0.307 ms | 2.629 ms | 0.265 ms | 1,476 / 1,889 |
| B2 `fb2f_gl` **[B1–B2]** | OpenGL | 0.317 ms | 2.742 ms | 0.245 ms | 1,478 / 1,890 |
| B2.1 first attempt (outer 1.6–2.8, middle 2.2–3.2) | OpenGL | 0.353 ms | 3.131 ms | 0.273 ms | 1,448 / 1,891 |
| **B2.1 final `b21_gl`** | OpenGL | **0.308 ms** | **2.796 ms** | 0.291 ms | 1,430 / 1,892 |
| B2.1 final, full regression run `b21full_gl` | OpenGL | 0.317 ms | 2.866 ms | 0.269 ms | 1,465 / 1,890 |
| B2 `b2base_vk` | Vulkan | 0.310 ms | 2.827 ms | 0.276 ms | 1,497 / 1,890 |
| **B2.1 final `b21_vk`** | Vulkan | **0.323 ms** | **2.852 ms** | 0.282 ms | 1,456 / 1,892 |

* **Run-to-run variation** for identical B2 code is 2.63–2.77 ms (20) and 0.30–0.32 ms (1). The final B2.1 is within
  it on OpenGL. On Vulkan it is +4 % (1) and +1 % (20).
* The first attempt cost +15–19 %, because the larger outer front added about 13 % of shaded fire area. The middle
  layer is now mostly behind the front, so it was trimmed (2.2–3.2 → 2.0–2.9) and the outer range tightened
  (–2.8 → –2.6). That brought the area back to B2's level at a model-metric cost of 0.03 blocks in median reach.
* Twenty real casts (whole sequence, tick rate 4): 0.911 ms in B2.1 vs 0.868 ms **[B1–B2]**. One draw call and
  peak vertex buffers are unchanged.
* The future targets (0.25 ms / 1.5 ms) are not addressed here, as the brief asked (B4–B5).

## 8. Look-development passes [Measured, real client, superseded code]

| pass | change | what the real client showed |
|---|---|---|
| 1 | exponential blast, stretch, lattice, larger outer front, no outer headroom, wider licks, shock heat on all volumes, shader edge | the dome is filled and cohesive; the first ~40 ms is still a small blob (the 40 ms fade-in hid the launch); black specks over the fire (leftover particles, see §4) |
| 2 | 12 ms fade-in; outer distance jitter 0.88–1.0 and size 1.6–2.8 (ragged silhouette); particles cleared in the isolated captures | a clear launch, no specks; at 0.3–0.6 s the dense orange front **hid the gold heart** (flatter temperature layers) and the ignition was not distinct from the front |
| 3 | per-layer shock heat (core strong, front weak); base erosion on the outer (0.18) and middle (0.05) layers | the heart shows through a ragged red-edged front; white ignition against an orange front |
| final | middle 2.0–2.9, outer 1.6–2.6 (cost, §7) | visually equivalent to pass 3 |

`iterations/` has the B2 / pass 2 / pass 3 strips for the isolated explosion and the slow-motion front.

## 9. Technical verification

| check | result | evidence |
|---|---|---|
| Supported, backend-agnostic APIs | unchanged pipeline (Blaze3D `RenderPipeline`, `RenderPass`, `MappableRingBuffer`); the marker uses vanilla `Gizmos`; no GL calls, no networking | `FireballV2WiringTest.noDirectOpenGlAndNoNetworking` **[Measured]** |
| Reversed-Z depth | pipeline unchanged: `GREATER_THAN_OR_EQUAL`, no depth write | `FireballV2WiringTest.reversedZPremultipliedOneDraw`; fire hidden behind terrain and walls in the wall, bunker and high-angle frames on both backends **[Measured]** |
| No fire through solid terrain | obstruction and mirroring code unchanged; `wallsStopTheFire` and `aSurfaceHitBulgesOutOfTheSurface` pass; wall and bunker frames | **[Measured]** |
| Camera-inside protection | near fade and inside dimming unchanged; the isolated camera-inside sequence (3 blocks) shows the world through the fire throughout | `13_camera_inside_isolated.jpg`, `12_camera_inside_3.jpg` **[Measured]** |
| Cleanup | `PASS: cleanup: no explosion left`, `no Screen FX request left`, `detonation emitters all finished` in all six runs (B2 and B2.1 on both backends, plus the full regression run) | `logs/` **[Measured]** |
| Accessibility | Screen FX unchanged; the full regression run's Screen FX checks are identical to B1–B2 (nearby shake 0.450 / flash 0.500, distant 0 / 0.005, behind the camera 0.450 / 0.150; Screen FX-off sequence recorded) | `logs/b21full_gl-hologram-capture.log` **[Measured]** |
| No world time, weather or game rules modified | no new world access in the explosion code (the model still uses only obstruction and ground probes; the marker only reads the camera and live explosions); the capture's own `/time` and `/weather` commands are test staging | code review, `ScreenFx` test unchanged |
| Gameplay untouched | no tracked file changed in this task (`git status` lists the same modified tracked files as before the task; every change is in the untracked B2 files); `FireballProjectileEntity`, `FireballSpell`, `FireballV2` style values and Screen FX values unchanged; server radius 6 | **[Measured]** |
| Development-only tooling | slow-motion clock forced to 1 outside development; marker in the development-only dev package | `FireballV2WiringTest` (2 new assertions) **[Measured]** |
| Automated tests | **2,458 tests, 0 failures, 0 errors** (2 skipped, pre-existing) after a full `./gradlew build --rerun-tasks`; the explosion tests are 8 (`FireExplosionTest`) + 1 (`FireExplosionShapeTest`, new) + 5 (`FireballV2WiringTest`) | `logs/build_final.txt`, `logs/test_summary.txt` **[Measured]** |
| OpenGL and Vulkan | real-client runs on both: OpenGL 4.6 Mesa 26.2.3 and Vulkan 1.4.354 radv | `logs/*-backend.txt` **[Measured]** |
| Code style | no inline fully qualified names in changed Java files (scanned); no wildcard imports | **[Measured]** |

Test changes: `theBodyReachesFullSizeAtTheEndOfTheExpansion` (B2: early reach < 0.6 × peak at 0.05 s) was replaced
by `theFlameFrontBlastsOutWithinATenthOfASecond` (compact at 0.01 s, front ≥ 0.85 × peak at 0.10 s, peak ≥ 0.9 R),
because the B2 assertion encoded the slow growth this task removes. `fastVolumesStretchOutwardThenSettle` is new.
All other B2 tests are unmodified.

## 10. Remaining visual issues and open points

1. **The silhouette approaches the hemisphere outline at peak.** That is unavoidable if the fire is to show the
   full 6 blocks, since the damage volume *is* a hemisphere on the ground. The edge is ragged at about 0.5–1 block
   scale (jitter, erosion, tongues) and it is not a smooth shell, but from a distance at 0.2–0.4 s the overall form
   reads as a dome of fire. If Stefan finds it too regular, the single knob is the outer-front inward jitter
   (0.88–1.0 R − ½ quad): widening it to 0.80–1.0 gives a lumpier outline at the cost of radius read-out.
2. **At real speed the first frame (≤ 50 ms) already shows ~60 % of the expansion.** The ordering (flash, then
   front, then billowing core) is only fully visible in slow motion. At 20–60 fps it reads as an instant, violent
   blast. **[Inference]** This is the intended "tremendous energy" read, but it should be judged in the GIFs.
3. **Leftover and debris particles draw over the fire.** Burnt fragments, sparks and fire-block smoke render after
   the explosion, and with B2.1's larger, lower fire they are more visible over it (black specks in real casts,
   large dark squares close to the camera). This is pre-existing particle behaviour and is left for B4 (particle
   budget and aftermath).
4. Gameplay fire blocks still dominate the camera-inside view and the aftermath (unchanged ignition, D9).
5. The outward stretch is subtle at gameplay distances. It is most visible close up, from inside, and in slow motion.
6. Twenty simultaneous explosions are still about 2.8 ms (B4–B5 optimisation; targets 0.25 / 1.5 ms are not
   addressed).
7. The B1–B2 open items O2–O5 (performance targets, wall containment versus damage through walls, emissive budget,
   Screen FX settings file) are unchanged. O1 (radius) is addressed by this refinement and awaits visual approval.

## 11. Files

**Changed in this task (all untracked B2 files; no tracked file touched):**
`client/vfx/explosion/FireExplosion.java` (kinematics, layout, stretch, radius, shock heat, erosion, licks),
`FireExplosionRenderer.java` (outward stretch in `emit`, development-only `setTimeScale`, read-only `active()`),
`assets/totality/shaders/core/vfx_fire.fsh` (body density edge 0.80 → 0.84 and quad-border fade),
`explosion/dev/FireballV2Dev.java` (radius marker and `marker on|off` command),
`explosion/dev/FireballV2Capture.java` (refine set, slow-motion and marker sequences, particle clearing for isolated
explosions, measurement and cleanup factored into `measureAndClean`; the quick set is now isolated-only),
tests `FireExplosionTest` (one test replaced, one added), `FireballV2WiringTest` (+2 assertions).

**New:** `src/test/.../explosion/FireExplosionShapeTest.java`, `Context/Tools/vfx-fireball-v2/make_refinement_media.py`,
this report.

**Not changed:** `FireballV2` (style record values, Screen FX values), `FireExplosionStyle`, the Screen FX service,
the Emissive Rendering Layer, Heat Vision V2, the projectile, `FireballSpell`, networking, Incense.
The VFX plan and the Master Reference were not updated (not requested).

## 12. Decisions for Stefan

| # | decision |
|---|---|
| R1 | Approve the refined explosion's look (expansion, cohesion, radius), or ask for another small tuning pass |
| R2 | Silhouette regularity at peak (§10.1): keep it, or widen the outer jitter (lumpier but reads slightly smaller) |
| R3 | Proceed to B3 (projectile V2), B4 (emissive, aftermath, particle budgets, including §10.3) and B5 (optimisation, benchmarks) |
