# VFX Experiment 3 — Fireball V2: Phase B1–B2 Report

Date: 2026-10-02 · Status: **B1 (Shared Screen FX Service V1) and B2 (first playable explosion) implemented —
stopped for visual review.** B3–B5 not started. Nothing committed or pushed. Minecraft 26.2.
Review bundle: `Context/Audit/Review Bundles/TOTALITY_VFX_FIREBALL_V2_B1_B2_REVIEW.zip`

**Labels:** **[Measured]** = recorded in this task (real client, automated test, build) · **[Inference]** =
interpretation of measured data · **[Proposal]** = not implemented, needs a decision.

Decisions D1–D10 and the permanent "atmosphere is cosmetic" rule are recorded in
`TOTALITY_VFX_FIREBALL_V2_DESIGN_REPORT.md` §15 and `TOTALITY_VFX_EXPERIMENT_PLAN.md` (lesson 7).

---

## 1. Summary

* **B1:** the Shared Screen FX Service V1 is reusable client infrastructure (not Fireball-specific). It provides
  camera shake and screen flash with time-based envelopes, distance falloff, ownership/expiry/cancellation,
  priorities, a diminishing merge capped at 1.35 × the strongest request, hard channel caps, an anti-strobe flash
  budget, and accessibility (Minecraft's Distortion Effects and Hide Lightning Flashes, plus Totality settings).
  Impact frames are a reserved channel, off by default, and have no renderer yet. Verified by 14 unit tests and
  16 real-client checks on **OpenGL and Vulkan**.
* **B2:** Fireball's flat V1 blast sprite is replaced by a **layered 3D fire explosion**. It is not a dome or a
  shell: 80 independently animated fire volumes in three temperature layers, flame tongues, ground licks and an
  ignition core. All are procedurally shaded (pixel-quantised, posterised temperature palette, domain-warped
  turbulence, growing erosion) and drawn in **one** depth-tested draw at the **server's explosion centre**.
  Verified in the real client on both backends across ground, wall, open air, confined space, night, a high angle,
  camera near and camera inside.
* **Gameplay is unchanged.** Damage, speed, cooldown, saves, ignition, range and collision are untouched, and so is
  the projectile renderer. The existing explosion packet is reused; nothing was added to networking.
* **Measured cost (RX 6600, 1880×1052):** one explosion **0.32 ms** GPU and 0.015 ms CPU. Twenty simultaneous
  explosions **2.74 ms (OpenGL) / 2.90 ms (Vulkan)** GPU and 0.25–0.28 ms CPU. Always one draw call. This is the main
  open performance item (§9).

## 2. B1 — Shared Screen FX Service V1

### 2.1 Architecture (`client/vfx/screen/`)

| class | role |
|---|---|
| `ScreenFxRequest` (+ `ScreenFxChannel`, `ScreenFxPriority`, `ScreenFxAudience`, `ScreenFxEnvelope`, `ScreenFxFalloff`) | one cosmetic request: channel, world origin (or none), intensity 0–1, attack/hold/release seconds, inner/outer falloff, priority, audience (everyone in range / subject only), flash colour, owner |
| `ScreenFxMixer` | pure logic, fully unit-tested: live requests, expiry, cancel by handle or owner, the per-channel merge, caps, flash budget and onset limiter; resolving twice for the same time returns the same frame |
| `ScreenFx` | facade: request/cancel, clock, settings, accessibility, rendering hooks, diagnostics |
| `ScreenFxSettings` | `config/totality-screenfx.properties`: `shake` and `flash` (0–1, default 1), `impactFrames` (default false) |
| `mixin/client/vfx/GameRendererScreenFxMixin` | shake: a small view rotation added at **every exit** of `GameRenderer.bobHurt` (`@At("RETURN")`, because the method returns early when the player is not hurt) |
| HUD layer `totality:screen_fx_flash` | flash: a full-screen tint drawn **first** in the HUD, under the hotbar and chat |
| `screen/dev/ScreenFxDev` | dev-only `/totalityvfx screenfx …` and capture scene 68 |

**Merge rule (per channel, per frame):** `v = intensity × envelope(t) × falloff(distance)`. A flash whose origin is
behind the camera is multiplied by 0.3. Only the highest live priority counts. The three strongest contributions
are merged as `v0 + 0.25·v1 + 0.10·v2` (so at most 1.35 × v0), then capped at 1.0 and multiplied by the
accessibility scale. Output: shake up to **0.8°** of rotation; flash up to **35 % alpha**.

**Flash anti-strobe:** a token bucket of **0.5 s** of full-cap flash, refilled at **0.25 s per s**, plus an onset
limiter: a new rise at most every 1/3 s.

**Clock:** client game time plus the partial tick, in seconds. It is time-based, follows `/tick rate`, and freezes
while paused. Nothing is shown while the game is paused, with no world, or while **Totality's Camera is open**, so
the viewfinder and photographs never shake or flash. Only the rendered view moves: the player's rotation, aim,
crosshair target and the server are untouched. The service never touches world time, weather or game rules (a test
checks for this).

**Deviation from the Phase A proposal:** the settings live in their own file. The Emissive Rendering Layer rewrites
`totality-vfx.properties` whole when it saves, which would erase added keys.

### 2.2 Results

Unit tests (`ScreenFxMixerTest` 13, `ScreenFxSettingsTest` 1): all pass **[Measured]**. Real client, scene 68:
**16/16 PASS on OpenGL and 16/16 on Vulkan** **[Measured]**. Values are from the OpenGL run; Vulkan matches within
sampling.

| case | result |
|---|---|
| one request (shake 0.45, flash 0.5, 4 blocks) | peak shake 0.447; it expires after its envelope |
| 20 identical simultaneous requests | shake 0.604 = **1.350 ×** the single 0.447; flash 0.378 = 1.35 × 0.280 |
| 20 spread over 4–40 blocks | shake 0.607 = **1.349 ×** the strongest (0.450) |
| priorities (NORMAL 0.5 + CINEMATIC 0.2) | 0.200 while the cinematic request is live; 0.500 after `cancelOwner` |
| distance 3 / 10 / 25 / 50 blocks | shake 0.450 / 0.433 / 0.184 / **0**; flash 0.480 / 0.474 / 0.171 / **0** |
| flash 5 blocks behind the camera | 0.144 (= 0.3 × 0.48) |
| strobe: a 0.8 flash every 2 ticks for 3 s | **8 onsets** (limit ≤ 10); energy **1.19 s at cap** (budget 1.25; unlimited demand would be ~2.4) |
| Totality settings 0 / 0.5 | 0 / exactly half (0.225) |
| Distortion Effects 0 + Hide Lightning Flashes | shake 0, flash 0 |
| cancel by handle; clean end | nothing live, output 0 |

Fireball in the real client (scene 69, real casts): camera 7 blocks away → shake 0.45, flash 0.50; ~45 blocks →
shake 0, flash 0.005; 6 blocks behind the camera → shake 0.45, flash 0.15 (OpenGL) / 0.08 (Vulkan, a different
sampled tick of a 0.18 s flash). **[Measured]**

## 3. B2 — rendering architecture

```
server (unchanged): explode() → damage, ignition, ClientboundExplodePacket(centre, sound, fireball_detonation)
client: FireballDetonationParticle (one per client per blast)
          ├─ V2 (default): FireballV2.detonate(level, centre)
          │     ├─ FireballVfx.detonateDebris   sparks, heat streaks, burnt fragments (existing particles)
          │     ├─ FireExplosionRenderer.spawn  layered explosion at the packet centre, radius = BLAST_RADIUS (6)
          │     └─ ScreenFx                      shake 0.45 (6→40 blocks), flash 0.5 (8→48 blocks, 0.18 s)
          └─ V1 (development only, setLegacyExplosion): the old sprite + shock ring, for same-run A/B
        then 40 ticks of the existing smoke/embers (V2: without the old particle shock ring)
LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN → FireExplosionRenderer.render
   evaluate every live explosion → sort all elements back to front → one vertex buffer → ONE draw
   pipeline totality:pipeline/vfx_fire: core/vfx_fire.{vsh,fsh}, POSITION_TEX_COLOR quads,
   premultiplied alpha, depth GREATER_THAN_OR_EQUAL (reversed-Z), no depth write, no culling
   hottest elements → small capped EmissiveSource quads → existing Emissive Rendering Layer bloom
```

| class | role |
|---|---|
| `client/vfx/explosion/FireExplosion` | **generic** primitive: lays out and animates the elements (pure model; the world is consulted only at creation through obstruction and ground probes, so the rules are unit-tested) |
| `FireExplosionStyle` | per-spell numbers (Fireball: `FireballV2.STYLE`) |
| `FireExplosionRenderer` | pipeline, lifecycle, sorting, vertex building, the single draw, GPU timer, statistics, emissive part |
| `client/particle/fireball/FireballV2` | Fireball-specific glue: style, Screen FX values, detonation |
| `explosion/dev/FireballV2Dev`, `FireballV2Capture` | dev-only `/totalityvfx fireball …` (V1/V2, emissive on/off, test and loop explosions, timing) and capture scene 69 |

**Element encoding:** each element is one quad. Body volumes and the core face the camera and spin. Tongues and
ground licks are axial billboards (along their outward or up axis). The UV packs the corner, the element kind and
its aspect, and the number of pixel cells. The vertex colour packs heat, erosion, seed and opacity. The shader uses
vanilla `GameTime` for motion, so no custom uniforms are needed.

## 4. How the flat-dome look was avoided

Development went through **six real-client iterations** (bundle `iterations/`, real frames from superseded code):

| iteration | what the real client showed | change |
|---|---|---|
| 1 | a cluster of separate round orange balls: circular silhouettes, flat colour per volume, soot as heavy dark balloons | — |
| 2 | **one coherent turbulent mass** with flame-like edges, a hot heart and soot veins | domain-warped, billowing (abs-fbm) silhouettes; internal temperature from turbulence; dark soot veins; larger overlapping volumes; layers cool at different rates; lighter translucent soot |
| 3 | ground licks lay flat like orange smears; outer reach short | separate rising flame tips for the licks; outer flame front pushed out |
| 4 | outer front already deep red at the 0.35 s "peak" | a **hold-then-cool** temperature model (core holds longest, outer front shortest) |
| 5 | now too hot: an almost uniform yellow-white mass (a permanently bright centre) | lower per-layer heat; gentler glow curve; longer lives |
| 6 (final) | gold heart → orange body → red edges → soot; break-up visible until ~1.3 s | gentler late erosion, later fade, a denser outer front of smaller volumes, longer tongues; then the camera-inside dimming (§7) |

Techniques that made the difference **[Measured: visible in the real-client iterations]**:
* **Many volumes instead of one shell.** The explosion has a real 3D silhouette from every angle (high angle, side,
  wall, air) and depth between regions. The volumes face the camera individually, but the mass does not.
* **Per-volume temperature over time.** The core holds heat longest and the outer front cools first, giving
  distinct temperature regions that evolve.
* **Domain-warped billowing noise** for silhouettes and internal turbulence; noise scrolls upward and volumes
  spin, giving rolling motion.
* **Erosion** that opens holes and tears the edges as each volume burns out, so the mass breaks apart instead of
  fading as a whole.
* **Premultiplied alpha.** Hot gas adds light while cooled soot is dark and covers what is behind it, so bloom and
  daylight do not wash the fire to white.
* **Pixel quantisation** of 10 texels per block and **10 posterised temperature bands**: Minecraft-scale texels
  that keep flowing detail. The look is not large flat polygons.

## 5. Actual visual changes vs Fireball V1 (real client)

| | V1 | V2 |
|---|---|---|
| form | one camera-facing pixel sprite, 8 frames | 80 volumes in 3 layers + 12 tongues + up to 30 ground licks + core |
| centre | offset 0.2 R off the surface | the server's explosion centre (packet position) |
| walls and floors | the sprite is cut flat by them | the mass bulges out of the hit surface (directions mirrored); every volume stops at the first solid block (fire does not pass through walls) |
| air burst | same sprite | a full 3D cloud, no ground fire |
| temperature | fixed frames | white-gold ignition → gold heart → orange → deep red → soot, per region |
| ground | particle shock ring | flame licks running out across the ground to the sphere's footprint, only where the sphere reaches the ground |
| light | none | small capped emissive contribution (bloom only, **not** world lighting: no block is lit) |
| screen | none | subtle shake + controlled flash through the Shared Screen FX Service |
| duration | sprite ~1.0 s | fire gone by ~1.45 s; the existing smoke/embers continue as before (aftermath finalised in B4) |

**Visual boundary:** a strict rule (`FireExplosionTest.fireNeverReachesPastTheDamageRadius`, 20 seeds, every 0.02 s)
keeps every hot element's **quad**, its farthest possible visible pixel, inside the 6-block radius. Only cooled
soot (heat < 0.3) may drift above it (cosmetic). As a result the dense body *reads* about 4.5–5.5 blocks; the outer
tongues and ground licks mark up to ~5.7. **[Inference from the frames]** V2 never suggests a *larger* radius than
the damage, but it reads somewhat *smaller* than V1's oversized sprite (decision O1).

## 6. Storyboard changes (from the Phase A timing)

| stage | Phase A | implemented |
|---|---|---|
| flash | 0–0.1 s | ignition core 0–0.12 s; Screen FX flash 0.18 s (attack 0, hold 0.03, release 0.15) |
| expansion | 0–0.3 s | 0.30 s ease-out (unchanged) |
| maximum | 0.3–0.6 s | heat holds: core ~0.56 s, middle ~0.46 s, outer ~0.40 s (new hold-then-cool model, added in iteration 4) |
| cooling / break-up | 0.6–1.2 s | cooling after the hold (constant 0.42 s); erosion from ~0.4 of a volume's life; fading from 0.88 of a 1.10–1.28 s life |
| shell gone | ~1.5 s | **≤ 1.45 s** (last volume; `totalSeconds` ≤ 1.6 by test) |
| ground ring | a ring decal | replaced by **ground licks**: flames spreading over the ground (D9 brief), only when the sphere reaches it |

## 7. Environments verified (real client, OpenGL and Vulkan)

Ground, wall, high angle, open air (a target 9 blocks up: no ground fire), confined (into a stone bunker through
its doorway: the fire fills it and spills out of the door), night with and without the emissive part, camera
7 blocks away, camera **inside** (3 blocks), Screen FX off, and 20 real casts at once. **[Measured]**

**Camera inside:** the first evidence run showed the screen filled with yellow-white fire for ~0.15 s. Fixed: the
near-camera fade widened to 3.5 blocks, and while the camera is inside an explosion's radius that explosion is
dimmed (to 35 % at the centre, 100 % at the edge). In the final run the world stays visible through the fire from
0.15 s; the 0.05 s ignition is bright but not opaque. The fire blocks then filling the lower view are the unchanged
gameplay ignition around the camera.

## 8. Screenshots and animation (bundle `media/`)

All from **real-client** capture scene 69 (final code, run `fb2f_gl`; Vulkan sheets in `media_vulkan/`; stills are JPEG q92 copies of the PNG screenshots, file names otherwise as listed). One frame
per game tick, so the GIFs play at real time (20 fps). The window is clamped to 1880×1052.

| requested | file |
|---|---|
| 1 V1 explosion; 2 V2 explosion | `01_v1_vs_v2_ground.png` (V1, V2, and V2 alone without the gameplay fire blocks), `anim_v1_ground.gif`, `anim_v2_ground.gif`, `anim_v2_ground_isolated.gif` |
| 3 beginning of expansion | `full_v2_ground_beginning_0.10s.png`, `full_v2_ground_isolated_beginning_0.10s.png` |
| 4 maximum | `full_*_maximum_0.35s.png`, `full_*_maximum_0.60s.png` |
| 5 cooling and break-up | `full_*_cooling_0.90s.png`, `full_*_breakup_1.20s.png` |
| 6 another angle | `04_angles_air_confined.png` (high angle, air, confined), `anim_v2_high_angle.gif` |
| 7 against a wall | `02_v1_vs_v2_wall.png`, `anim_v2_wall.gif` |
| 8 night | `03_v1_vs_v2_night.png` (incl. V2 without emissive), `anim_v2_night.gif` |
| 9 shake and flash disabled | `05_camera_near_inside_screenfx_off.png` (row 3; shake 0 / flash 0 logged per frame) |
| also | `06_twenty_simultaneous.png` (20 real casts) |

Shake cannot be seen in still frames; its values are logged per frame (`fx:` lines in the capture logs).

## 9. Preliminary performance [Measured, RX 6600, Mesa 26.2.3, 1880×1052]

GPU = `TimerQuery` around the explosion draw, averaged over all frames of the window. CPU = evaluate + sort + vertex
build per frame. "Loop" = client-only explosions that restart when they end (steady state at real speed).

| case | GPU OpenGL | GPU Vulkan | CPU | elements (avg / peak) | draws | vertex buffers |
|---|---|---|---|---|---|---|
| 1 explosion (loop, 3,591 / 4,471 samples) | **0.317 ms** | **0.325 ms** | 0.015 ms | 75 / 93 | 1 | 686 KiB (3-slot ring, sized by the peak) |
| 20 explosions (loop, 867 / 789 samples) | **2.742 ms** | **2.896 ms** | 0.25–0.28 ms | 1,478 / 1,890 | 1 | 686 KiB |
| 20 real casts (whole sequence incl. flight, tick rate 4) | 0.868 ms | 0.867 ms | 0.28–0.29 ms | peak 2,440 | 1 | 686 KiB |

* Particles (vanilla counter): a single explosion keeps roughly 130–260 particles alive (debris, the existing
  smoke/embers, vanilla fire-block smoke); after 20 real casts the count was 1,289 / 1,397 when sampled at the end of
  the sequence (not a peak measurement).
* Frame rates (context only, not GPU-bound measurements): ~1,165 / 891 fps with one loop explosion, 539 / 243 fps
  with 20 (OpenGL / Vulkan).
* **[Inference]** The cost is fragment-bound (many large, overlapping, procedurally shaded volumes, each with
  ~3 fbm evaluations of 4 octaves). Twenty simultaneous explosions at ~2.8 ms is acceptable on this GPU but heavy
  for weaker ones. Candidate B4/B5 optimisations, not done (per brief): 3 octaves, distance LOD (fewer, larger
  volumes beyond ~24 blocks), coarser cells at distance, dropping fully eroded fragments, an element budget across
  explosions. Targets to be set by Stefan (D10).

## 10. Verification

| check | result |
|---|---|
| `./gradlew build` (final code) | **BUILD SUCCESSFUL**, **2456 tests, 0 failures**, 2 skipped (before: 2430; +26 new: Screen FX 14, FireExplosion 7, wiring 5) |
| Scenes 68 + 69, **OpenGL** (`fb2f_gl`) | exit 0, **19 PASS, 0 FAIL** (16 Screen FX + 3 cleanup: no explosion left, no Screen FX request left, all detonation emitters finished) |
| Scenes 68 + 69, **Vulkan** (`fb2f_vk`, `--graphicsBackend vulkan`, radv) | exit 0, **19 PASS, 0 FAIL**, same visuals |
| Regression, OpenGL: Fireball 58, Phone 64, Camera & Gallery 65, Emissive Rendering Layer 66, Heat Vision 67 | exit 0, **188 PASS, 0 FAIL** |
| Fireball gameplay | `FireballVfxRegressionTest` (speed, radius, dice, save, ignition, explode packet) passes unchanged; the projectile entity and renderer files are unmodified |
| Code style | no inline fully qualified names in new/changed files (scanned) |
| Not verified in a capture | Screen FX while Totality's Camera is open (suppression is enforced in code and by `FireballV2WiringTest`, not filmed); dedicated server; multiplayer with a second real client; Fabulous graphics, shader packs, other GPUs |

## 11. Bugs and remaining limitations

1. **Twenty-explosion GPU cost** (~2.8 ms) — see §9.
2. **The body reads smaller than the radius** (≈ 4.5–5.5 of 6 blocks) because of the strict boundary rule (O1).
3. **Gameplay fire blocks dominate the aftermath** and the camera-inside view (unchanged ignition, D9). The isolated
   sequences show the effect without them.
4. **Debris sparks flying at the camera** show as large flat squares (existing Firebolt spark sprites) — B4
   particle budget/aftermath.
5. **Fire is contained by walls** while damage still passes through them (M7). The visuals follow physics, not the
   current mechanic (O3).
6. **Emissive contribution is subtle** (night with/without is nearly identical) — deliberate until the B4 budget.
7. Per-volume billboards can show a slight "turning" when the camera orbits very close; not noticeable at
   gameplay distances in the captures.
8. The shake is verified numerically and by code; still frames cannot show it.

## 12. Files

**New:** `client/vfx/screen/{ScreenFx, ScreenFxMixer, ScreenFxRequest, ScreenFxChannel, ScreenFxPriority,
ScreenFxAudience, ScreenFxEnvelope, ScreenFxFalloff, ScreenFxFrame, ScreenFxSettings}.java`,
`client/vfx/screen/dev/ScreenFxDev.java`, `mixin/client/vfx/GameRendererScreenFxMixin.java`,
`client/vfx/explosion/{FireExplosion, FireExplosionStyle, FireExplosionRenderer}.java`,
`client/vfx/explosion/dev/{FireballV2Dev, FireballV2Capture}.java`, `client/particle/fireball/FireballV2.java`,
`assets/totality/shaders/core/vfx_fire.{vsh,fsh}`, tests `client/vfx/screen/{ScreenFxMixerTest,
ScreenFxSettingsTest}`, `client/vfx/explosion/{FireExplosionTest, FireballV2WiringTest}`,
`Context/Tools/vfx-fireball-v2/make_review_media.py`.

**Modified:** `TotalityClient.java` (+2 imports, +3 lines: registration), `totality.mixins.json` (+1 client mixin),
`FireballDetonationParticle.java` (V2 dispatch, dev-only legacy switch), `FireballVfx.java` (+`detonateDebris`,
+`afterDetonation` overload without the shock ring; V1 methods kept), the design report (§15) and the VFX plan
(status, lesson 7).

**Unchanged on purpose:** `FireballProjectileEntity`, `FireballSpell`, `FireballProjectileRenderer`, the Emissive
Rendering Layer, Heat Vision, networking.

## 13. Outstanding decisions for Stefan

| # | decision | note |
|---|---|---|
| O1 | Visual approval of the explosion; should the visible fire reach closer to the full 6 blocks (relax the strict quad rule to the visible edge, ≈ +0.5 block) or stay conservative? | conservative now |
| O2 | Performance targets for 1 and 20 explosions (D10), and whether to optimise in B4 or B5 | 20 = ~2.8 ms now |
| O3 | Fire contained by walls vs damage through walls (M7) | visual containment now |
| O4 | Keep the explosion's emissive part minimal until the B4 global limit (D5)? | yes, now |
| O5 | Screen FX settings in their own file (`totality-screenfx.properties`) | as implemented |
| O6 | Proceed to B3 (projectile bead + ribbon), B4 (aftermath, emissive budget, particle budget) and B5 (benchmark), or refine the explosion further first | stopped here |
