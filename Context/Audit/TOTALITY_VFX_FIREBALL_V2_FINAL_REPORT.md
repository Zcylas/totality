# VFX Experiment 3 — Fireball V2: Final Report (B1–B5)

Date: 2026-10-02 · Status: **Fireball V2 implemented (B1–B5), verified and approved (final review, decisions F1–F5,
§13); committed locally (`feat: implement Fireball V2 and shared screen VFX`), not pushed.** Minecraft 26.2. The
Master Reference update is handled separately (handoff: `TOTALITY_VFX_MASTER_UPDATE_HANDOFF.md`).
Incense (`ad70d9d1`) and all unrelated work are untouched.
Review bundle: `Context/Audit/Review Bundles/TOTALITY_VFX_FIREBALL_V2_FINAL_REVIEW.zip`

**Labels:** **[Measured]** = recorded in this session (real client, automated test, live server, build) ·
**[Earlier]** = evidence from B1–B2.1 (their reports) · **[Inference]** = interpretation of measured data ·
**[Estimate]** = not measured.

---

## 1. Summary

| phase | content | status |
|---|---|---|
| A | D&D research, audit, design (`TOTALITY_VFX_FIREBALL_V2_DESIGN_REPORT.md`) | reviewed |
| B1 | Shared Screen FX Service V1 | **approved** (unchanged since) |
| B2 / B2.1 | layered 3D fire explosion; refined expansion, cohesion, 6-block readout | **approved** (silhouette, expansion, temperatures and architecture kept) |
| B3 | Projectile V2: white-hot bead, gold-orange halo, tapering fire streak, ember trail, cast flare, impact collapse; mid-flight cast-burst fix (V7) | implemented, this session |
| B4 | Emissive integration with a per-effect budget plus a global cap; Fireball's own particles with a near-camera size limit; debris retimed; cooling aftermath; shared particle budget; cleanup | implemented, this session |
| B5 | Bottleneck measured; turbulence moved to a precomputed noise texture; full verification on OpenGL and Vulkan; regressions; live gameplay verification | implemented, this session |

Headline results **[Measured, RX 6600, Mesa 26.2.3, 1880×1052 window]**:

* **Explosion GPU:** one explosion **0.053 ms** (OpenGL) / **0.058 ms** (Vulkan), down from 0.308 / 0.323 ms in B2.1.
  Twenty sustained explosions **0.546 / 0.505 ms**, down from 2.796 / 2.852 ms. Both provisional targets (0.25 ms
  and 1.5 ms) are met, with no visible change to the approved explosion (§7).
* **Projectile:** one draw for every projectile; 20 in flight cost 0.002–0.007 ms GPU and 0.022 ms CPU.
* **Gameplay unchanged:** 41/41 live `FireballVerification` checks pass (damage, saves, radius, impact point,
  ignition, lifetime, no knockback). The gameplay source assertions pass too.
* **Cleanup:** after ten repeated casts and after every scenario, no explosion, projectile, Fireball particle, Screen
  FX request, emitter or emissive source remains, on both backends (11/11 capture checks each).
* **Tests and build:** 2,476 tests, 0 failures, `./gradlew build --rerun-tasks` successful.

## 2. The approved explosion (B2.1, kept)

About 80 independently animated fire volumes in three temperature layers, plus flame tongues, ground flames and an
ignition core, drawn in one depth-tested (reversed-Z) premultiplied draw at the server's explosion centre. The outer
flame front is blasted outward (≈90 % of its travel by 0.10 s), spread evenly over the sphere, sits at the 6-block
boundary, and never shows hot fire past it. White-hot ignition, then a gold heart, orange body, red edges and cooling
soot; procedural turbulence and erosion; fire gone by about 1.45 s; walls stop the fire; the camera-inside dimming is
kept. Details: `TOTALITY_VFX_FIREBALL_V2_B1_B2_REPORT.md`, `TOTALITY_VFX_FIREBALL_V2_B2_REFINEMENT_REPORT.md`.

The only appearance-related changes to it in B3–B5:
* the emissive factor rose from 0.25 to 0.45, with a white-gold glow for the ignition core (B4, §5);
* the turbulence comes from a noise texture instead of per-pixel value-noise octaves (B5, §7). The same kind of field
  is visually equivalent (`optimisation/` in the bundle).

## 3. B3 — Projectile V2

### 3.1 Design as implemented

| element | implementation |
|---|---|
| bead | camera-facing quad (half-size 0.42 blocks, limited to 0.006–0.06 rad on screen), shader `core/vfx_fireball_projectile`: a compact white-hot core inside a flickering gold-orange halo, pixel-quantised |
| streak | up to 3.2 blocks behind the bead: a **tapering camera-facing ribbon** on the shared `client/vfx/ribbon/RibbonGeometry` (Heat Vision V2's ribbon, generalised with a width profile). White-hot only at the head, then gold, orange and red; energy streams backwards; the tail breaks into flickering tongues |
| ember trail | 2–3 of Fireball's own small, dim embers per tick, shed behind the streak and drifting up, under the particle budget; an occasional spark |
| glow | the bead's core and the first metre of the streak go into the Emissive Rendering Layer (group `fireball`, demand 0.25 each, limited on screen) |
| casting → flight | a fireball whose cast this client saw is hidden for its first 0.6 blocks (clear of a first-person view) and grows over the next 2 blocks. Its bead **flares white-gold as it emerges** (the cast flash), and the streak never reaches back past the cast point. Sparks and embers are thrown forwards |
| flight → impact | when the projectile is removed, its streak collapses into the last bead position over 0.12 s while the bead flares and fades, bridging into the explosion's ignition core |
| identity | Heat Vision: continuous twin beams from the eyes, additive, constant width. Fire Bolt: small pixel comet (Firebolt particles). **Fireball:** a single bead with a short tapering streak, premultiplied (it reads in daylight), the explosion's palette |

Mechanics are unchanged: 1.2 blocks per tick, the same collision, range, lifetime and damage (asserted by
`FireballVfxRegressionTest` and verified live, §9).

### 3.2 Cast burst for clients that start tracking mid-flight (V7)

The cast burst played on the first client tick that saw the entity, so a client that started tracking a fireball
mid-flight showed an ignition in mid-air. Now the burst plays only if the client knows the fireball's caster and the
fireball is within 4.5 blocks of the caster's eyes (about three ticks of flight). Otherwise no burst plays, the
projectile is drawn complete at once, and the trail has no cast-point clearance. No networking was added (D6).
**[Measured]** The final capture spawns a fireball whose caster is 30+ blocks away and whose first client sighting is
in view. The check "a fireball first seen mid-flight plays no cast burst" passes on both backends, and the frames show
the full streak from the first frame (`media/07_midflight_sighting.jpg`). This is a **simulated** sighting (the
stand-in caster is not a tracked player), not a second real client.

### 3.3 Issues found and fixed during B3 [Measured, real client]

| issue | fix |
|---|---|
| in daylight the streak washed out to near-white | white-hot only at the head, more coverage (premultiplied alpha 0.75); gold, orange and red now read on bright terrain |
| first person: the cast flash particle at 2.5 blocks became a large flat square | flash particle removed in V2; the flash is now the bead's emergence flare |
| first person: the bead's emissive quad became a large glowing square a block from the eyes | glow quad limited to 0.015 rad on screen, plus the near fade |
| third person (back camera): the projectile was hidden for its whole flight | not a renderer fault: vanilla's third-person camera sits on the aim line, so anything flying along it projects behind the caster's head. The capture now turns the view slightly after the cast to film the flight; in normal play this is inherent to third-person aiming (§10) |
| the particle budget treated a spectator camera as "far" (vanilla `getNearestPlayer(…, false)` skips spectators) | distance to any player, spectators included |

### 3.4 Old renderer kept for comparison

`FireballProjectileRenderer` (V1 comet sprites) and the V1 cast burst, trail and detonation are kept behind
`/totalityvfx fireball legacy on` (`FireballV2Dev.setLegacy`). They are forced off outside a development environment
(asserted in `FireballV2WiringTest`), so V1 cannot become active in normal play.

## 4. Screen FX architecture (B1, unchanged)

Shake and flash requests merge per channel (`v0 + 0.25·v1 + 0.10·v2`, at most 1.35 × the strongest), with distance
falloff, priorities, an anti-strobe flash budget and accessibility support (Distortion Effects, Hide Lightning
Flashes, and Totality's own settings in `totality-screenfx.properties`). Nothing shows while paused or while
Totality's Camera is open. Fireball requests shake 0.45 (6 → 40 blocks) and flash 0.5 (8 → 48 blocks, 0.18 s).
**[Measured]** The repeated-cast run (ten casts, one every 0.5 s) keeps shake ≤ 0.358 and flash ≤ 0.473 throughout.
Nothing accumulates, and no request is left afterwards. Screen FX off: shake 0, flash 0 logged per frame. Camera and
Gallery regression scenes (65) pass; see §9.

## 5. B4 — Emissive integration and brightness management

### 5.1 Contributions

| source | emissive part | group / demand |
|---|---|---|
| projectile | bead core (white-gold, ≤ 0.16 blocks, limited on screen) and the first metre of the streak (orange) | `fireball`, 0.25 per projectile |
| explosion ignition core | white-gold, a third of the core's size | `fireball` |
| explosion hot regions | elements with heat ≥ 0.55, orange-gold, about 20 % of their size (tongues 25 %) | `fireball`, the explosion's summed weight ÷ 58 (its brightest moment, measured from the model by `FireExplosionTest`), at most 1 |
| Heat Vision (unchanged look) | beam cores and hotspot | `heat_vision`, 0.5 per beam (no limit) |

The glow is smaller than the fire it comes from, so it haloes the white-hot centre and gold heart without washing out
the orange body, red edges or soot. Night captures with the layer on and off show the same fire detail, with a soft
warm halo when it is on (`media/04_night_v1_v2_layer_off.jpg`) **[Measured]**. This is screen-space glow, not world
lighting: no block is lit by Fireball's emissive part. The brighter floor at night comes from the real gameplay fire
blocks.

### 5.2 Global brightness management (decision D5): per-effect budget plus a final global cap

`client/vfx/glow/EmissiveBudget`, resolved once per frame before the sources emit:

1. Each source states its **demand** (`EmissiveSource.emissiveDemand()`, in "full effects") and optionally a
   **budget group** (`budgetGroup()`). Both default to 0 / none, so existing sources are unchanged.
2. A group with a limit is scaled to fit it: `scale = min(1, limit / demand)`. Fireball's group limit is **2.5**
   (one or two Fireballs keep full glow; twenty share the light of 2.5).
3. The capped demands of all groups are summed. Only if the total exceeds **`glow.globalLimit`** (new setting in
   `totality-vfx.properties`, default 6, clamped 1–64) is everything scaled by `limit / total`.
4. The scale multiplies every quad colour the source adds (`EmissiveBuffer.setScale`); sources need no knowledge of it.

Why this is enough: the per-group limit stops a crowded effect from saturating the screen and from crowding out
others. Fireball (≤ 2.5) plus Heat Vision (≤ 1) stays below the default global limit, so **Fireballs never dim Heat
Vision**. A priority system is not needed. The design is compatible with the future VFX API (groups are strings;
limits are set by the effect).
**[Measured]** Twenty simultaneous casts: Fireball demand up to 11.0, group scale down to 0.23, total held at 2.5.
One cast: scale 1.00. Unit tests: `EmissiveBudgetTest` (4) and `EmissiveGlowSettingsTest` (+1).
**Side effect [Measured]:** Heat Vision's synthetic 258-beam stress test (demand 129) now triggers the global cap, so
its glow is dimmed in that test. Normal play (2 beams) is unaffected.

## 6. B4 — Particles and aftermath

### 6.1 Problems from the B2.1 review and their fixes

| problem | cause | fix |
|---|---|---|
| sparks became large flat squares flying at the camera | Firebolt particles have a fixed world size | Fireball's **own** spark, ember and streak particle types (Firebolt's sprites; Firebolt itself is untouched). Every Fireball particle keeps its on-screen half-size ≤ 0.012 rad (smoke ≤ 0.035) and fades out within 0.6–2 blocks of the camera (smoke 1.5–4) |
| debris and particles overlaid on the explosion | dark burnt fragments flew through the bright fire (black specks); smoke spawned inside the fire from 0.15 s; slow sparks stayed inside the fire | no fragments in V2; sparks and streaks are fast enough (0.9–1.5 blocks/tick) to fly out past the flame front within ~0.15 s, so they read as debris thrown out; smoke only starts as the fire cools (0.7 s) and comes from the upper blast |
| (found this session) aftermath smoke spawning around a camera inside the blast became large grey squares | puffs spawned within the camera's surroundings | no puff within 3.5 blocks of the viewer, plus the smoke limits above |

**Depth:** particles still draw after the explosion (the fire writes no depth). The overlay problem is solved by
timing and recipe, not by moving the explosion's draw point. The approved occlusion behaviour, verified with
reversed-Z, stays unchanged.

### 6.2 Aftermath timeline (as implemented)

| time | what |
|---|---|
| 0 | ignition core, debris (22 sparks + 10 streaks, budgeted), Screen FX |
| 0–1.45 s | the explosion (unchanged) |
| 0.7–1.5 s | ~16 smoke puffs rise from the upper, cooling part of the blast and thin out |
| 0.9–2.8 s | small, dim embers drift up from the burnt area, thinning out; no flame wisps after the fire (they would look like burning ground, which only the real fire blocks are) |
| ≤ 4 s | every cosmetic particle has ended (lifetimes bounded; the capture's cleanup checks confirm 0 alive) |

The real fire blocks from ignition are gameplay and unchanged. **[Measured]** In near and inside views most of the
smoke after 1 s is vanilla fire-block smoke: 600–900 particles in total, of which Fireball's own peak at 50–66
(§10).

### 6.3 Particle budget

`FireballParticleBudget` (common code, counters only): at most 400 live Fireball particles. Below 200 recipes are
untouched; above, they shrink linearly to nothing at 400. Distance quality: full within 24 blocks of the nearest
player, half to 48, a quarter beyond. The explosion and projectile geometry never go through it.
**[Measured]** Peaks: one cast 66; five in flight 220; twenty at once 381 (cap held); ten repeated casts 198. Zero
after every scenario. Unit tests: `FireballParticleBudgetTest` (3).

### 6.4 Cleanup

| resource | released | verified |
|---|---|---|
| explosion instances | when finished (≤ 1.6 s) | capture check, both backends |
| projectile tracks (incl. impact collapse) | 0.12 s after removal | capture check "no projectile tracked" |
| emissive contributions | sources unregister when nothing is drawn | "no emissive source left" |
| Screen FX requests | expire | "no Screen FX request left" |
| detonation emitters | 56 ticks | "every detonation emitter finished" |
| Fireball particles | lifetimes; counter reset on world change | "no Fireball particle alive" |
| GPU buffers | ring buffers reused; the glow layer frees its targets after 10 s idle (Experiment 1) | code |

## 7. B5 — Performance optimisation

### 7.1 Bottleneck

The explosion's cost was fragment shading. Per body-volume pixel, the shader evaluated four 4-octave value-noise fbm
fields: 16 value-noise lookups, 64 hashes. CPU (evaluate + sort + build) was 0.02 ms for one explosion and 0.3 ms for
twenty, with one draw call and about 1,450 quads for twenty, so vertex work and draw count were not the problem
**[Measured, B2.1 and the unoptimised V2]**.

### 7.2 The optimisation

The fbm is baked into a tileable 512×512 RGBA texture (`textures/vfx/fire_noise.png`, generated deterministically by
`Context/Tools/vfx-fireball-v2/make_fire_noise.py`). Its three independent channels each hold exactly the shader's
fbm: value noise with smoothstep interpolation, 4 octaves, normalised. The shader samples `fbm(p)` as
`texture(Sampler0, p/16)`: the two warp fields in one fetch (r, g), billow (b) and detail (r) in one fetch each. The
pipeline gained the standard `BindGroupLayouts.SAMPLER0` and a repeat-linear sampler, both backend-agnostic Blaze3D
APIs. Projectile shading stays procedural (it is tiny on screen).

### 7.3 Results [Measured; same camera and scenario; GPU = `TimerQuery` around the draw, averaged at real speed]

| explosion GPU | B2.1 (OpenGL / Vulkan) | V2 unoptimised (OpenGL) | V2 optimised, perf-only run (OpenGL) | **V2 final (OpenGL / Vulkan)** | target |
|---|---|---|---|---|---|
| 1 explosion | 0.308 / 0.323 ms | 0.291 ms (`b5p0`) / 0.298 ms (`b4f1`) | 0.055 ms (`b5p1`) | **0.053 / 0.058 ms** | ≤ 0.25 ms ✔ |
| 20 sustained explosions | 2.796 / 2.852 ms | 2.595 / 2.591 ms | 0.536 ms | **0.546 / 0.505 ms** | ≤ 1.5 ms ✔ |

* **Visual check [Measured]:** the same B2.1 comparison set (isolated, marker front and top, slow motion) was
  re-captured with the texture. Silhouette, expansion, palette, cooling and break-up match the approved B2.1 frames,
  and no tiling or blur is visible at full resolution (`optimisation/`). Remaining differences come from the B4
  emissive increase and from the capture's spawn position varying between runs.
* Not needed, so not done: distance LOD, fewer or smaller distant volumes, overdraw reduction, skipping eroded
  elements, cross-explosion element budgets. The candidates stay documented (B1–B2 report §9).
* Run-to-run variation of identical code is about ±5 % for explosions and up to ±20 % in synthetic heavy loads (Heat
  Vision's 256-beam case).

## 8. Final performance report [Measured unless labelled]

Hardware: AMD RX 6600, Mesa 26.2.3 (OpenGL 4.6 core / Vulkan 1.4.354 radv), window 1880×1052 (clamped by the
desktop), uncapped fps, real speed (tick rate 20). Runs: `b5f_gl`, `b5f_vk` (final capture), `b5p0_gl` / `b5p1_gl`
(perf-only before / after the optimisation).

| item | OpenGL | Vulkan | notes |
|---|---|---|---|
| explosion, 1 (GPU / CPU) | 0.053 / 0.018 ms | 0.058 / 0.015 ms | 1 draw, ~70 elements (peak 93) |
| explosions, 20 sustained (GPU / CPU) | 0.546 / 0.336 ms | 0.505 / 0.318 ms | 1 draw, ~1,450 elements (peak 1,892) |
| projectiles, 20 in flight (GPU / CPU) | 0.002 / 0.022 ms | 0.007 / 0.022 ms | 1 draw, 187–220 quads. The projectiles were 10–60 blocks away (small on screen); a close-up projectile costs more, but it is a single small quad and ribbon **[Estimate: well under 0.05 ms]** |
| Emissive Rendering Layer (whole shared layer) | 0.079–0.111 ms | 0.105–0.127 ms | shared infrastructure: the fixed blur chain dominates. Experiment 1 measured 0.06–0.15 ms for its own scenes. Fireball's **incremental** share is its emitted quads (≤ ~100 a frame) **[Inference: negligible versus the fixed chain]** |
| Shared Screen FX | not separately measurable | | CPU: a few requests merged per frame; GPU: one HUD quad for the flash, a view rotation for shake **[Inference: negligible]** |
| draw calls | explosion 1, projectile 1, glow layer passes as in Experiment 1 | same | independent of the number of Fireballs |
| buffers | explosion vertex ring 686 KiB (3 slots, sized by the largest frame seen), projectile ring 61 KiB, noise texture 1 MiB on the GPU (512²×4 B; 367 KB PNG) | same | |
| particles (Fireball's own, peak) | 66 (one cast), 220 (5 in flight), 381 (20 at once), 0 afterwards | same order | vanilla total incl. fire-block smoke 600–1,200 |
| fps (context only, not a GPU measurement) | ~1,450 (1 explosion), ~990 (20) | ~965 / ~713 | |

**Shared infrastructure vs Fireball's incremental cost:** the glow layer and Screen FX are shared and run once per
frame whatever the number of effects. Fireball's own GPU cost is its explosion and projectile draws above.

## 9. Verification

### 9.1 Real client (final code) [Measured]

Capture scene 69, final set (`-Dtotality.fireball.v2.final=true`), the **same set on OpenGL (`b5f_gl`) and Vulkan
(`b5f_vk`)**: 11/11 checks pass on each, 1,158 frames each.

| requested | covered by |
|---|---|
| first person, third person, distances | `v2/v1_first_person`, `v2/v1_spell_third_person`, side view at ~7 blocks, `v2_far` (~36 blocks) |
| walls and entities | first-person wall shot; airborne impact on an armour-stand target; projectile→entity impact rules verified live |
| day and night | all day sequences; `v2/v1_night_side` |
| with the completed explosion | every sequence is a real cast through `FireballSpell.onActivate` |
| ground, wall, airborne impact | side/third person (ground), first person (wall), `v2_air` |
| camera near / inside | `v2_camera_near_7`, `v2_camera_inside_3` |
| multiple explosions / projectiles in flight | `v2_twenty` (20 at once), `v2_multi_flight` (5 fanned) |
| Screen FX disabled | `v2_screenfx_off` (shake 0, flash 0 logged) |
| Emissive Rendering Layer disabled | `v2_night_side_layer_off` |
| repeated casts and cleanup | ten casts at real speed + six cleanup checks |
| V1 vs V2 | the same cameras with `legacy on` |
| slow motion | projectile (one tick per frame at 0.25×), explosion (development 0.25× clock) |

Reversed-Z: both new pipelines use `GREATER_THAN_OR_EQUAL` with no depth write (asserted). Terrain and walls hide the
projectile and fire in the frames; the fire stays on the open side of the wall.

### 9.2 Regressions [Measured]

| system | evidence | result |
|---|---|---|
| Fireball gameplay | live `FireballVerification` (`runVerificationServer`, graceful `stop`, Gradle exit 0) | **41/41 PASS**; all 28 live suites pass, 0 failures |
| Fireball V1 capture scene, Phone, Camera & Gallery, Emissive Layer, Heat Vision, Screen FX | scenes 58, 64, 65, 66, 67, 68 in one OpenGL run (`b5regr_gl`) | **204 PASS, 0 FAIL** |
| Heat Vision timing | scene 67 re-run (`b5hv2_gl`) vs Experiment 2 (`hvafter_gl`) | 2 beams 0.003 ms (unchanged); 64 test beams 0.036 ms (identical); 256 test beams 0.233 vs 0.206 ms (within the observed run-to-run spread, 0.206–0.277 ms) |
| Camera compatibility | scene 65 checks (Camera viewfinder and photographs) and the Screen FX suppression while the Camera is open (B1 code, unchanged; `FireballV2WiringTest`) | pass |
| automated tests | `./gradlew build --rerun-tasks` | **2,476 tests, 0 failures, 0 errors** (2 skipped, pre-existing) |

New tests: `EmissiveBudgetTest` (4), `FireballParticleBudgetTest` (3), `RibbonGeometryTest` (3),
`FireballProjectileTrackTest` (3), `FireExplosionTest.oneExplosionsGlowDemandPeaksAtOne`,
`EmissiveGlowSettingsTest` (+1), `FireballV2WiringTest` (+3: projectile pipeline, dev-only V1, cast-burst gating,
budget group), `FireballVfxRegressionTest` (new particle definitions).

### 9.3 Multiplayer

**No genuine multiplayer test with a second real client was run.** Verified instead:
* code level: everything is client-side from replicated state (entity tracking, the explode packet); no networking
  was added;
* a simulated other caster (a stand-in fake player whose fireball this client first sees mid-flight): no cast burst,
  the projectile drawn complete;
* the live server suite covers the server side with fake players.

No claims are made for shader packs, Fabulous graphics, other GPUs or drivers, or resolutions above the
window-clamped 1880×1052.

## 10. Remaining limitations

1. **Third-person aiming hides the projectile behind the caster's head** while it flies straight along the
   crosshair. This is inherent to vanilla's back camera, and visible as soon as the caster turns.
2. **Gameplay fire blocks dominate the aftermath and the near/inside views.** Their vanilla smoke (dark puffs) far
   outnumbers Fireball's cosmetic particles. Unchanged ignition (D9); a separate gameplay decision.
3. **Particles draw over the fire** where they overlap it, because the fire writes no depth. It is mitigated by
   timing and recipe, not solved in general.
4. The **impact transition** uses the client's own removal of the projectile. If the client did not predict the hit,
   the collapse starts one tick before the explode packet arrives **[Inference; not observed as a problem]**.
5. **Fire contained by walls while damage passes through them** (M7): unchanged, separate decision.
6. **The emissive budget is per screen, not per area:** twenty explosions far apart share the same 2.5 limit as
   twenty overlapping ones.
7. Heat Vision's synthetic 258-beam test is dimmed by the global cap (§5.2).
8. Not tested: genuine multiplayer, other GPUs, shader packs, Fabulous graphics, above 1880×1052.

## 11. Future opportunities for the VFX API

* **Ribbon primitive** (`RibbonGeometry`): already shared by Heat Vision and Fireball; Eldritch Blast and Magic
  Missile are natural next users.
* **Emissive budget groups**: every future effect declares a demand and a group; area-aware budgets could come later.
* **Layered volume primitive** (`FireExplosion` + noise texture): smoke, poison clouds and Meteor Swarm impacts with
  different palettes.
* **Shared particle budget and near-camera limits**: generalise `FireballParticleBudget` and the angular size limit
  to all spell particles (Firebolt still uses fixed-size particles).
* **Precomputed noise textures** as a shared asset for every procedural effect.
* **Terrain decals** for scorch marks and the Targeting Preview (not started).

## 12. Files (Fireball V2 overall; this session marked ●)

**New:** `client/vfx/screen/*`, `mixin/client/vfx/GameRendererScreenFxMixin` (B1); `client/vfx/explosion/*`,
`client/particle/fireball/FireballV2`, `shaders/core/vfx_fire.{vsh,fsh}` (B2/B2.1);
● `client/vfx/projectile/{FireballProjectileVfx, FireballProjectileTrack}`, ● `client/vfx/ribbon/RibbonGeometry`,
● `client/vfx/glow/EmissiveBudget`, ● `entity/magic/FireballParticleBudget`,
● `client/vfx/explosion/dev/FireballV2FinalCapture`, ● `shaders/core/vfx_fireball_projectile.fsh`,
● `textures/vfx/fire_noise.png`, ● `particles/fireball_{spark,ember,streak}.json`, tests (§9.2),
● tools `make_fire_noise.py`, `make_final_media.py`.

**Modified (tracked):** `TotalityClient` (registration: `ScreenFx.register()`, ● `FireballV2.register()`),
`totality.mixins.json` (B1), `FireballDetonationParticle` (V2 dispatch; ● V2 aftermath and lifetime), `FireballVfx`
(● V2 recipes, cast detection, budget, development V1 switch; V1 recipes kept), ● `FireballProjectileEntity` (client
branch only: `castSeen`), ● `FireballProjectileRenderer` (V1 drawn only in the development comparison),
● `FireballParticle` / `FireballParticles` (new kinds, size limit, budget), ● `ModParticles` (3 types),
● `HeatVisionGeometry` (delegates to `RibbonGeometry`, identical output; its tests unchanged and passing),
● `HeatVisionEmissive` (demand + group only), ● `EmissiveSource` / `EmissiveBuffer` / `EmissiveGlow` /
`EmissiveBloomRenderer` / `EmissiveGlowSettings` (budget, `glow.globalLimit`), ● the VFX plan, ● the design report
(§16), and the dev tooling (`FireballV2Dev`, `FireballV2Capture`).

**Unchanged:** `FireballSpell`, the server half of `FireballProjectileEntity` (speed, collision, damage, saves,
radius, ignition, lifetime, packet), the Screen FX service, Heat Vision's look, Incense, networking.

## 13. Decisions (final review, 2026-10-02)

| # | decision |
|---|---|
| F1 | **Approved.** Fireball V2 as a whole (projectile, explosion, particles, Screen FX, emissive integration) |
| F2 | **Kept:** the provisional emissive budgets — Fireball group limit 2.5, global limit 6 (`glow.globalLimit`) |
| F3 | **Kept:** Fireball V1 (renderer and recipes) exclusively as a development-only comparison |
| F4 | **Unchanged gameplay.** Ignition / fire-block aftermath (D9), wall cover (M7) and the other mechanical differences remain separate future decisions |
| F5 | The next development task is undecided (to be discussed in the next ChatGPT conversation) |
