# The Shooting Star Demo — Advanced VFX Technical Audit for Totality

Date: 2026-10-01 · Scope: research only. No Totality code, dependency, rendering system or asset was changed;
nothing was committed or pushed.

Subject: `Inspiration Mods/the-shooting-star-demo-1.0.5-demo.jar` (SHA-256 `54056912…bfb731fde0787ef85c7c0f7ad83e4e72bfbf15ae`),
"The Shooting Star (Demo)" 1.0.5 by rimuru.dev, **All Rights Reserved**.

**Label legend** (used throughout):

| label | meaning |
|---|---|
| **[Conf]** | Confirmed: read directly from the JAR (metadata, `javap` bytecode / constant pools / annotations, or the plain-text GLSL resources) |
| **[Interp]** | A reasonable technical inference from confirmed evidence (e.g. decoding intermediary names, the purpose of a value) |
| **[Hypo]** | An unverified hypothesis, mostly about runtime cost; needs profiling before anyone relies on it |
| **[Totality]** | A fact about the Totality repository or its Minecraft 26.2 environment, checked during this audit |
| **[Suggest]** | An original recommendation for Totality, open for discussion, not a decision |

Evidence references like **(N06)** point to the review bundle's `inspection-notes/NN_*.txt`. Diagrams referenced as
**D1–D7** are in the bundle's `diagrams/ARCHITECTURE_DIAGRAMS.md`.

---

## 0. Method and limitations (short form; full list in §11)

* The outer JAR was extracted to the session scratch directory (outside the repository). The original file was only
  read. It is a multi-loader wrapper holding three inner builds: Fabric 1.21.11, Fabric 1.20.1 and Forge 1.20.1.
  The **Fabric 1.21.11** build was the primary subject, and the Fabric 1.20.1 build was used for comparison. (N01, N02)
* The code is **not obfuscated** (`dev.aek.shootingstardemo.*` names are intact; Minecraft references are in
  intermediary). No decompiler is installed locally, and I did not install one. The analysis uses `javap -p -c -v
  -constants` (JDK 25), plus a small script that summarises each method's call graph, field accesses and constants
  from the bytecode listing.
* The 12 post-processing shaders and 4 item shaders ship as **plain GLSL text**. I read them to understand the
  techniques. This audit and its bundle describe them only in original words and pseudocode, with counts derived
  mechanically from the source (N07).
* **The mod was not run.** Running it would need a separate Minecraft 1.21.11 Fabric client with downloads and a GPU
  session, and §6 shows its renderer would not run on Totality's 26.2 anyway. So there are **no screenshots and no
  benchmarks**. Every performance statement is labelled [Conf] (a property of the code), [Interp] (arithmetic from
  confirmed formats) or [Hypo].
* Totality's environment was checked read-only: `gradle.properties`, `run/options.txt`, a source survey, and `javap`
  over Loom's deobfuscated `minecraft-merged-deobf-26.2.jar` (N08).

---

## 1. Executive technical summary

**The headline:** almost none of the mod's impressive visuals are particles, models or textures. **They are
analytic, per-pixel images computed in fullscreen fragment shaders**. Each shader reconstructs the 3D world position
of every pixel from Minecraft's depth buffer, then *mathematically intersects the view ray* with the effect's shapes:
cylinders for beams, segments for lasers, spheres for stars and planets, signed-distance 2D shapes projected onto the
terrain for magic circles and HUD rings. Colour comes from distance-to-shape falloff functions, procedural noise and
blackbody-style colour ramps. The result is lit, occluded and anti-aliased without drawing a single triangle in the
world. [Conf] (N07)

The supporting systems, all **[Conf]**:

1. **A private raw-OpenGL post-processing stack** (`client.render.PostFxRenderer`). It runs at the end of
   `LevelRenderer.renderLevel`, snapshots the main colour and depth buffers, runs a chain of fullscreen passes at a
   dynamically scaled resolution, adds a 5-level HDR bloom that blooms **only the light the effects added**, and
   composites the result straight back into Minecraft's main colour texture. It bypasses Blaze3D entirely: it gets
   GL texture names through `GlTexture.glId()` and calls LWJGL `GL11`–`GL33` directly, saving and restoring GL state
   around itself. (N05, N06)
2. **A deterministic, timeline-driven effect model** (`client.fx.SpellFx`). The server sends one small packet per
   cast: origin, target, yaw, 64-bit seed, targets and flags. Every client then evaluates the whole effect as a
   **pure function of `age + partialTick` and the seed**, using trapezoid "window" envelopes and fixed integer
   keyframes that mirror the server's. There is no per-frame simulation state. (N04, N06)
3. **A screen-effect parameter bag** (`client.render.ScreenFx`): shake, chromatic aberration, zoom blur, flash,
   vignette, bloom, grading, grain, anime **"impact frames"**, and dormant freeze, afterimage and rewind features.
   Simultaneous effects merge into it mostly with `Math.max`, so ten explosions do not shake the screen ten times
   harder. (N06)
4. **A cinematic camera director** (`client.cinematic.*`). Shots are camera-pose functions with cut or sweep
   blends. A mixin takes over `Camera.setup`, the FOV and the first-person hand, with block-collision clipping.
   It plays only for the caster. During it, the effect swaps its world shader for **fully procedural space scenes**,
   including a sphere-traced, PBR-shaded "railgun" (`star_gun.fsh`, up to 170 march steps per pixel). (N06, N07)
5. **Server-side terrain destruction synchronised with the visuals** (`star.Carving`). Whole chunk sections are
   *swapped for empty ones*, rim blocks are written directly into the sections, and chunks are relit and re-sent,
   time-sliced at 12–24 chunks per tick. The shader hides the moment of transition. (N06)
6. **Vanilla particles play a minor supporting role:** dust and block-debris bursts at fixed ages. There is no
   custom particle engine. (N06)

**Relevance to Totality:**

* The **techniques** are general and mostly portable: depth reconstruction, analytic ray–shape glow, SDF decals on
  terrain, added-light bloom, max-merged screen parameters, timeline envelopes, seed-deterministic client effects,
  impact frames, and dynamic resolution.
* The **integration is not portable as built.** All 12 fullscreen shaders are byte-identical between the mod's
  1.20.1 and 1.21.11 builds, so the GLSL itself is version-agnostic. But every Java hook it uses was renamed or
  re-signatured in 26.2. Much more important, **Minecraft 26.2 ships a user-selectable Vulkan backend**, and raw GL
  calls cannot work there. Totality would need to rebuild any such pipeline on Blaze3D's backend-agnostic
  `RenderPipeline` / `RenderPass` / `PostChain` APIs. [Totality] (N08)
* **Multi-entity scaling is the mod's weak point.** Each active effect adds a fullscreen pass, so cost grows with
  *screen pixels × number of effects*, not with how big each effect looks. The demo is built for one spectacular
  cast at a time; there is a hard cap of 24 active effects and a 600-block range cull, and nothing else.
  For Totality's "twenty mobs casting at once" scenario I recommend a **hybrid**: cheap geometry and particle layers
  per effect, plus **one shared, fixed-cost post chain**, with fullscreen analytic passes reserved for a few
  "hero" moments. (§6–§9)

---

## 2. Mod identification and dependencies

| item | finding |
|---|---|
| Container | Multi-loader "all-versions" JAR: outer `fabric.mod.json` (id `shooting_star_demo_multi`) nests two Fabric builds through `jars`; outer `META-INF/mods.toml` uses `modLoader = "lowcodefml"` and carries the Forge 1.20.1 build in `META-INF/jarjar/` [Conf] (N01) |
| Inner builds | `…+mc1.21.11.jar` (9.6 MB), `…+mc1.20.1.jar` (9.6 MB), `…+forge-1.20.1.jar` (9.6 MB) [Conf] |
| Target studied | Fabric, Minecraft `~1.21.11`, Java ≥ 21, Fabric Loader ≥ 0.17.0, `fabric-api: *`; built with Loom 1.16.3 / Loader 0.19.3 / Mixin 0.17.3, mapping namespace intermediary [Conf] |
| Entry points | `dev.aek.shootingstardemo.ShootingStarDemo` (main), `…client.ShootingStarDemoClient` (client) [Conf] |
| Mixins | 5 client mixins in 1.21.11 (`LevelRendererMixin`, `GameRendererMixin`, `CameraMixin`, `AvatarPoseMixin`, `PlayerModelPoseMixin`); the 1.20.1 build adds `GuiMixin` and `SoundEngineMixin` and lacks `AvatarPoseMixin` [Conf] (N05) |
| Access widener | Declared, but empty [Conf] |
| Third-party libraries | **None bundled.** Only Fabric API (networking, HUD element registry, `RenderStateDataKey`, item groups, lifecycle events) and the game's own LWJGL, JOML and fastutil [Conf] |
| Animation frameworks | **None** (no GeckoLib or Player Animator). Player arm poses use a custom render-state data key plus a `PlayerModel.setupAnim` mixin [Conf] |
| Size | 170 files: 87 classes, 16 shader files (≈ 120 KB GLSL), 25 OGG sounds, ≈ 8.4 MB of real astronomical imagery (NASA, ESO and Solar System Scope, credited in `CREDITS.txt` files) [Conf] (N02) |
| Content | One item (Stellar Remote), two skills: **SS-01 The Shooting Star** (526-tick timeline, 1200-tick cooldown, 200-block strike radius) and **SS-04 Seven Stars** (560-tick timeline, 7 craters in the Big Dipper pattern linked by 22-wide, 48-deep trenches) [Conf] (N04) |
| Safety gates | `GpuCheck` refuses software renderers (llvmpipe, swiftshader, …) and requires ≥ 16 texture units, ≥ 4096 px textures and renderable RGBA16F. `ShaderWarmupScreen` compiles every FX program at the title screen within a 45 ms-per-frame budget [Conf] |

Package map (from signatures, N03):

```
dev.aek.shootingstardemo
├─ magic/, registry/, net/        item, skills, cooldowns, 3 payloads (cast, cooldown, spell FX)
├─ spell/                         ActiveSpell base, SpellEngine (server tick), teleport/evacuation, utilities
├─ star/                          ShootingStar, SevenStars, Carving (terrain), Erasure (kill), Dipper (star data)
└─ client/
   ├─ fx/                         FxManager, SpellFx base, StarFx, SevenStarsFx, *Path (camera/space maths), HUDs
   ├─ render/                     PostFxRenderer, ShaderProgram, FxTextures, ScreenFx, GpuCheck, ShaderWarmup
   ├─ cinematic/                  CutsceneDirector, Cutscene, Shot, CameraPose, per-skill cutscene scripts
   ├─ item/                       RemoteRenderer (procedural held item, custom RenderPipelines)
   ├─ magic/                      skill menu, keys, HUD
   └─ mixin/                      5 mixins (N05)
```

---

## 3. Rendering architecture

### 3.1 Where it plugs into Minecraft [Conf] (N05, N06, D2)

| hook (1.21.11, decoded from intermediary [Interp]) | purpose |
|---|---|
| `LevelRenderer.renderLevel` **@HEAD** | Copy this frame's view and projection matrices |
| `LevelRenderer.renderLevel` **@RETURN** | `FxManager.render`: the whole post-FX stack runs here, after the world and before the hand and GUI |
| `GameRenderer.bobHurt` @HEAD | Apply camera shake and roll to the view `PoseStack` |
| `GameRenderer.getFov` @RETURN | Cinematic FOV override |
| `GameRenderer.renderItemInHand` @HEAD | Hide the first-person hand during cutscenes |
| `Camera.setup` @TAIL | Cinematic camera: blend to the shot pose, clip against blocks, force detached view |
| `AvatarRenderer.extractRenderState` @TAIL → `PlayerModel.setupAnim` @TAIL | Carry a `CasterPose` through the render state and rotate the arms |

The HUD is wrapped through Fabric's `HudElementRegistry.replaceElement`, which hides vanilla HUD elements during
cutscenes and draws the effect HUDs and cast titles. [Conf]

### 3.2 Lifecycle: create → update → render → destroy [Conf]

* **Create.** `SpellFxPayload` arrives → `FxManager.start` → `StarFx`/`SevenStarsFx` constructor (seeds a
  `Random`, derives a float seed for the shaders) → `FxManager.add`. The active list is capped at
  `MAX_ACTIVE = 24`; the oldest entry is evicted with `onRemoved`.
* **Update (per tick).** `FxManager.tick` → `SpellFx.tick` → `onTick(age)` plays sounds and spawns particles at
  fixed ages. Camera trauma decays by 0.035 per tick. An effect is removed when `age ≥ duration`, and `onRemoved`
  stops its looping "film" sounds.
* **Render (per frame).** `passes(frame, list)` and `screen(frame, ScreenFx)` are called only when the camera is
  within `range()` of the effect's focus (default 600 blocks). Both are **stateless evaluations** of
  `t = age + partial`.
* **Destroy.** The effect is dropped from the list, and that is all. The **renderer's GPU resources are global and
  persistent**: render targets are reallocated only on resize, programs are cached for the session, and the
  cinematic textures (~190 MB with mips, §4.3) are never deleted because `FxTextures.clear()` has no callers.
  The 64-frame "tape" ring is the only resource freed automatically, after 600 idle frames. (N09)

### 3.3 Timeline coordination [Conf] (D4)

* **Server and client share integer keyframes.** For example, The Shooting Star uses `ARMED 5, PRESS 7, MARK 9,
  EVAC 31, FIRE 314, IMPACT 352, COLLAPSE 462, GONE 486, DURATION 526`, defined as constants in both
  `star.ShootingStar` and `client.fx.StarFx`. Gameplay events (damage, carving) happen on the server at those
  ticks; the client draws the matching visuals at the same ticks, with no further packets. (N04)
* **Continuous visuals are pure envelope maths.** A `window(t, a, b, c, d)` helper produces a smooth 0→1→0
  trapezoid. Each visual parameter is a product of such windows, smoothsteps and exponentials of `t`. The cinematic
  has its own sub-timeline (`StarPath`: `DISSOLVE 58 … IMPACT 352`), with camera distances interpolated through
  monotone piecewise-cubic (PCHIP) tables in log space, so the camera can travel from metres to kiloparsecs smoothly.
* **Consequence:** because any frame can be computed directly from `t`, there are no accumulated simulation errors,
  late joiners only need `age`, and features like the dormant "rewind" become feasible. The cost is that these are
  *authored animations*: they do not react to the world after the cast, except by sampling the depth buffer.

### 3.4 How the components interact [Conf]

```
SpellFx ── passes() ──► PostPass(program name, uniform-setter lambda) ─┐
        ── screen() ──► ScreenFx (max-merged scalars)                  ├─► PostFxRenderer (one per client)
        ── onTick() ──► vanilla sounds + particles                     │
        ── hud()    ──► 2D overlays via GuiGraphics                    │
CutsceneDirector ◄── subject()/timeline ── camera/FOV/hand/HUD hiding ─┘
```

* An effect describes its GPU work as data: an ordered list of `PostPass` records (a program name plus a lambda that
  sets uniforms through a small `Uniforms` interface). `PostFxRenderer` owns all GL objects.
* Uniforms are generic (`P0..P3`, `Pts[16]` as `vec4`, plus named vectors). Seven Stars sends **all seven nodes'
  state in one `vec4[16]` array** and draws them in **one** pass.

### 3.5 Batching and draw calls [Conf]

* **One draw call per pass.** Each pass is a single fullscreen triangle generated from `gl_VertexID`, with no vertex
  buffer.
* Per frame, the fixed work is two snapshot blits, the bloom chain (10 draws), one sceneLow blit and one composite.
  Each active effect then adds one pass: `star_beam` or `stars_world` in world view; `star_space` plus `star_gun` and
  `star_rays` at the "gun" beat of the cinematic.
* There is **no instancing and no geometry batching**; it is not needed, because the world effects contain no
  geometry. Shared work (bloom, composite) is done **once per frame** no matter how many effects are active.
* The held item is the exception: it uses the vanilla `RenderPipeline` / `RenderType` system with procedural quads
  (§4.5).

### 3.6 Own rendering infrastructure [Conf]

The mod introduces a self-contained mini-engine on raw OpenGL:

* a GLSL loader with `#include` expansion, `#line` resets for readable errors, and an automatically prepended
  `common.glsl`;
* a uniform-location cache per program;
* a `GlState` capture and restore covering FBOs, program, VAO, 16 texture units with their samplers, viewport,
  enable flags and masks;
* render targets: snapshot colour and depth with the depth format matched to Minecraft's, two ping-pong RGBA8
  targets, sceneLow, hold, step, and RGBA16F bloom down/up chains;
* asynchronous texture decoding on a daemon thread, with channel packing (three greyscale maps in one RGB texture),
  mipmaps and 8× anisotropy when available;
* a GPU capability gate (`GpuCheck`) and a title-screen shader warm-up with a per-frame time budget;
* a dynamic-resolution governor (§6.1);
* exception containment: any exception in the post stack is logged, GL state is restored, and the frame renders
  without effects.

### 3.7 CPU and GPU resource management [Conf] / [Interp]

* **CPU.** Per active effect per frame: a few dozen float uniforms and a lambda. The GL state capture costs about
  60 `glGet*` calls per frame [Interp]. Particle spawning happens at a handful of keyframes.
* **GPU memory.** About 69 MB of persistent render targets at 1080p, 93 MB at 1440p and 149 MB at 4K, plus about
  190 MB of cinematic textures once loaded, plus a 33–133 MB tape buffer only while rewind is used (N09). All of
  these figures are arithmetic from confirmed formats, not measurements.

---

## 4. Detailed effect investigations

### 4.1 SS-01 "The Shooting Star" — the world-space strike (`star_beam.fsh`)

**What it shows.** A red targeting laser rises from the target into the sky; HUD-like rings and brackets appear *on
the terrain*; a countdown runs; a star-like glint descends; a thick red-white energy column slams down with a ground
shock ring; the surrounding terrain is lit red; the crater walls glow like cooling lava; the column collapses.

**Systems and resources.** Server `star.ShootingStar` handles gameplay and `star.Carving` the crater. The client
uses `client.fx.StarFx` (timeline, sounds, particles, `ScreenFx`), `star_beam.fsh` plus `common.glsl` and
`star.glsl`, the post stack, and `StarHud` for 2D overlays. [Conf]

**The technique, step by step** (original description, all [Conf] from the GLSL):

1. For each pixel: reconstruct the view ray and the scene hit point from depth. Pixels where depth is about 1.0 are
   "sky".
2. **Laser line.** The shortest distance between the view ray and a vertical segment from the target up to a great
   height, kept only where that point is nearer than the scene. A minimum width proportional to
   `distance × pixel angle` keeps the line at least about one pixel wide at any range, so it never shimmers or
   vanishes. Core and halo use exponential falloffs, with a fast double-sine flicker.
3. **Ground marking.** Rings, rotating brackets, a crosshair, a radar-sweep wedge, tick marks and a contracting
   pulse, all evaluated from the pixel's *horizontal distance and angle from the target* on the reconstructed
   terrain surface. They wrap over any terrain shape like a projected decal, with no decal geometry.
4. **Falling star.** The target point raised by a height that eases toward the ground, projected to the screen, and
   drawn as a Gaussian core plus 4 straight and 4 diagonal exponential "diffraction spikes" and a halo. This is a
   lens-flare sprite done analytically.
5. **The column.** An analytic **ray-vs-vertical-cylinder** intersection, clipped to the scene depth and to an
   animated "foot" height (the column descends from the sky over ~0.14 units of the impact clock, seconds [Interp]). Inside, colour depends on the
   distance from the axis (white-hot core → red → deep red rim), modulated by fBm streaks that scroll downward
   quickly, plus banding and a bright skin at the cylinder boundary. The result reads as a volumetric column for the
   cost of one intersection.
6. **Fake lighting.** Terrain outside the column is multiplied toward red by an inverse-distance falloff, with an
   additive glow near the wall. A shock ring expands along the ground at ~160 blocks per unit of the impact clock (per second [Interp]).
7. **Crater dressing.** Where the depth buffer shows *sky inside the beam radius while looking down*, meaning the
   carved terrain has exposed the void, the shader paints an "abyss" gradient. Crater walls near the rim get a
   blackbody glow broken up by fBm "cracks" that cools with depth.
8. **Output.** A soft-knee tone curve. The pass writes opaque colour (no blending); bloom later picks up the added
   light.

**Animation control.** `StarFx.worldPass` turns `t` into about 15 scalars (laser strength, ring strength, star
fall, column strength, impact time, heat, and so on) using `window` and `smooth` envelopes on the shared tick
keyframes. `StarFx.screen` adds vignette, shake, aberration, zoom blur, a bloom boost, an impact-frame sequence at
impact, and an orange flash at collapse. Sounds are scheduled at fixed ages. [Conf]

**Component interaction.** The GPU column is the "real" visual. Server carving removes blocks during
`COLLAPSE`–`GONE`; the shader's abyss and wall-heat terms keep the hole looking consistent while chunk packets
arrive. A burst of vanilla dust and block-debris particles adds physical "stuff" at impact. [Conf] / [Interp]

**Performance.** One fullscreen pass with about 5 texture reads and moderate maths per pixel, run for every pixel
at FX resolution even when the beam covers 2% of the screen [Conf]. It is cheap for one instance; scaling is
discussed in §6. Visible from up to 600 blocks.

**Lessons for Totality.** Ray-vs-primitive glow with a pixel-width floor; terrain-projected ring and decal maths;
red-shifting the terrain as fake light; envelope-driven timing; distance clipping against the scene depth.

### 4.2 SS-04 "Seven Stars" — seven strikes, terrain HUD and constellation links (`stars_world.fsh`)

**What it shows.** The sky darkens to night with a procedural star field. Seven violet points lock on one by one
in the Big Dipper pattern scaled onto the ground, each with a hexagonal marker, rings and a glowing *numeric
readout* drawn on the terrain (its star's real distance in light-years). Stars fall one after another; each impact
flashes, raises a column and sends out a shock ring. Glowing "circuit traces" then burn along the seven
constellation edges, and a finale lights the whole constellation in the sky.

**Systems.** `star.SevenStars` (server: craters, then trenches via `Carving`), `star.Dipper` (real right
ascension, declination and distances of the seven stars, flattened onto the ground), `client.fx.SevenStarsFx`,
`SevenStarsPath`, `SevenStarsHud`, and `stars_world.fsh`. [Conf]

**Technique highlights** [Conf]:

* **All seven instances in one pass.** Per-node state (position, time since landing, radius, fall progress, lock
  progress) is packed into a 16 × `vec4` uniform array. The shader loops over 7 nodes and 7 edges. *The cost is one
  fullscreen pass however many sub-impacts are on screen.* This is the most scalable pattern in the mod.
* **SDF text and HUD on terrain.** Hexagon outlines, ring segments and seven-segment digits are evaluated as 2D
  signed distances in the ground plane around each node. Anti-aliasing is scaled by
  `distance / max(|ray.y|, 0.08)`, so the lines stay crisp at grazing angles.
* **"Circuit" link burn.** Along each edge, a trace is drawn in the edge's local frame (along and across). A moving
  hot head reveals it, and periodic hashed branches and "via" rings are added, then fade as it cools.
* **Night-sky takeover** without touching vanilla sky rendering: only sky pixels are darkened and over-painted.

**Animation control.** `SevenStarsFx.fall(t, i)` and `lock(t, i)` stagger each node by fixed tick steps
(`LOCK_STEP 4`, `LAND_STEP 16`, `LINK_STEP 5`). Particles: on landing, a loop of **360 iterations per crater**
emits block-debris and dust; embers and link sparks follow (N06). [Conf]

**Performance.** One pass that is heavier in ALU than §4.1 but has the same texture traffic. The particle bursts
are the CPU-side spike: up to about 7 × 360 iterations staggered over roughly 100 ticks. [Conf] / [Hypo] for the
cost.

**Lessons.** Pack many sub-effects into one pass through uniform arrays; procedural text and HUD on terrain without
textures or decals; staggered keyframes from one seed.

### 4.3 The cinematic: camera director + procedural space + SDF railgun (`star_space`, `stars_space`, `star_gun`, `star_rays`)

**What it shows (caster only, optional).** The camera leaves the player, the world dissolves through cloud-like
wisps into space, and the camera flies past the Earth (with atmosphere, night lights and clouds), the Moon, the
planets and Saturn's rings out to the galaxy. A giant mechanical railgun charges with arcs and coronas and fires;
the shot chases back to Earth through warp streaks; a brief hand-drawn "ink" look appears at 12 fps; and the shot
cuts back to the impact in the world.

**Systems.** `cinematic.CutsceneDirector`, `Cutscene`, `Shot` (`cut` / `sweep` with blend-in), `CameraPose`
(position, look, FOV, roll), `StarCutscenes` / `SevenStarsCutscenes` (shot lists as lambdas of
`Subject`, `t`, `partial`), `StarPath` (real units: km, AU and kly constants; a log-space distance table; a
Grand-Canyon-latitude site on a rotating Earth), and `FxTextures` (NASA, ESO and Solar System Scope imagery). [Conf]

**Techniques** [Conf]:

* **Camera takeover with collision.** The shot pose is blended with the gameplay camera by a weight (fade in 6
  ticks, out 14, quick fade on skip). The camera position is raycast-clipped so it never ends up inside blocks. FOV
  and roll are overridden, and the hand and HUD are hidden. Cutscenes have priorities and can be queued. A config
  file stores whether cutscenes are enabled and automatic.
* **World → space dissolve.** The space pass samples the world image and blends with a polar fBm "wisp" mask driven
  by the timeline, plus a white cloud layer. Its alpha output tells bloom which areas are fully synthetic.
* **Space renderer.** Panorama and procedural star fields with three hashed layers per cell size and twinkle;
  analytic ray-sphere planets with texture-atlas lookups and LOD chosen from the pixel footprint; a ray-plane
  Saturn ring; an Earth surface with day/night blend, clouds, a height-derived normal, an ocean glint, and a
  12-step single-scattering atmosphere; a textured galaxy disc.
* **Railgun.** A signed-distance model built from boxes, prisms, tori, 17 repeated coils and radiators. It is
  rendered by **over-relaxed sphere tracing** (step multiplier 1.35 with a fallback) inside a bounding box, with a
  hit threshold that grows with distance × pixel size, two soft-shadow marches, 5-tap ambient occlusion,
  GGX/Smith/Schlick PBR and procedural panel seams. Charge, arcs, inflow, flash and the shot are additive analytic
  glows.
* **Screen-space god rays** (`star_rays`): two 40-tap radial blurs toward the projected key light and the muzzle.
* **"Animated on twos."** `ScreenFx.stepFps` makes the renderer hold a frame and re-present it until a fixed
  interval passes, which gives the stepped anime look. The ink interlude also quantises its own time to 12 fps.

**Performance.** This is where the mod is genuinely expensive: up to ~250 signed-distance evaluations per pixel in
the railgun shots [Interp], about 190 MB of textures loaded on first use and kept for the session, and 80 texture
taps per pixel for god rays. It is shown to **one player at a time** (the caster), and the dynamic-resolution
governor exists mainly for these shots [Interp].

**Lessons.** A shot or blend camera director with collision clipping is directly useful for Totality boss intros
and ultimates. Long procedural sequences can be authored as functions of `t`. A full raymarched cinematic is a
**specialist tool**, not a reusable VFX technique (§8).

### 4.4 Screen layer: bloom, composite and "impact frames" (`bloom_down`, `bloom_up`, `composite`)

**What it shows.** Strong glow around every energy element; the frame flashes into stylised two-tone or halftone
"manga panel" images for a few frames at impact; shake, chromatic fringing, zoom blur, desaturation, vignette and
grain. [Conf]

**Techniques** [Conf]:

* **Added-light bloom.** On the first downsample, the bloom input is *(FX result − original scene)*, keeping only
  what the effects brightened, with a soft knee. Vanilla bright surfaces (snow, sky, lava) therefore **do not
  bloom**, and the glow stays a property of the magic. A "synthetic" weight switches to ordinary threshold bloom for
  fully CG cinematic frames. Then come five downsample levels with a 13-tap filter and a tent-filter upsample chain,
  stored in RGBA16F.
* **Detail-restoring upscale.** When FX resolution is below native, the composite multiplies the upscaled FX image
  by `(full-res scene) / (low-res scene)`, clamped. Untouched world detail stays sharp at native resolution while
  the effect itself is rendered small.
* **Auto exposure.** 16 taps from the bloom chain dim the whole image when the added light is large.
* **Impact frames.** The image is classified per pixel as brighter or darker than its local mean (8 taps),
  edge-detected from luminance differences ("ink"), and given a 45°-rotated halftone dot screen for mid-tones. Each
  "mode" picks a palette: black and white, inverted, red, violet, gold, navy-yellow, a spectral ring, a glitch with
  band slides, block knocks and channel split, or a hexagonal grid. `SpellFx.impactSequence` plays a list of modes
  for a few ticks each. **This is the anime "impact frame" look, done entirely in the composite.**
* **Max-merged parameters.** Every effect's `screen()` raises fields with `Math.max`, so overlapping effects do not
  accumulate unbounded shake or flash.

**Performance.** Shared, once per frame. The base composite is cheap; zoom blur adds about 40 reads per pixel and
impact frames about 12–40, but both last only a few frames. [Conf] / [Interp]

**Lessons.** Added-light bloom and max-merged screen parameters are the two most transferable ideas in the whole
mod, cheap and high-impact. Impact frames are a strong stylistic signature with very small cost.

### 4.5 The held item: Stellar Remote (`client.item.RemoteRenderer`, `core/armillary_glow`, `core/remote_screen`)

**What it shows.** A detailed clamshell remote that opens, presses its button, shows an animated screen, and emits
rotating glyph rings and a glow. [Conf]

**Techniques** [Conf]:

* A vanilla **`SpecialModelRenderer`** selected by the item-model JSON (`minecraft:special`) outside GUI/shelf
  contexts; the inventory icon is a normal flat model.
* Geometry is **procedural quads** (box, prism, plane, corner and billboard helpers) emitted each frame. There is no
  model file.
* Two custom **`RenderPipeline`s** registered through the vanilla API: additive `BlendFunction.LIGHTNING`, no depth
  write, no culling, and a vertex format with position, texture and colour. Their shaders use the
  `DynamicTransforms` UBO. Because these pipelines take no custom uniforms, **animation parameters are smuggled
  through UV offsets**: the fragment shader decodes an integer "page" from `uv.x` to select the variant and phase.
* Opening and pressing animations are driven by a wall-clock "strike time" and eased curves.
* The 1.20.1 build uses JSON core-shader programs instead. Only these four item shaders differ between versions
  (N02).

**Lessons.** This is the one part of the mod already built on the same API family Totality uses for
`HeatVisionBeamRenderer`. Encoding per-draw parameters in vertex attributes (UV or colour) is a practical trick for
animated `RenderPipeline` effects without per-draw uniforms.

### 4.6 Terrain destruction synchronised with the visuals (`star.Carving`, server)

**What it does.** It removes a cylinder of 200-block radius, or seven craters plus 22 × 48 trenches, within a
couple of seconds of server time, without a block-by-block update storm. [Conf]

**Technique** [Conf]:

1. Affected chunks are sorted by distance from the centre, nearest first, and processed **12 per tick**
   (ShootingStar) or **24 per tick** (SevenStars trenches). Chunk tickets keep them loaded while the spell runs.
2. Each 16³ section **fully inside** the cut is replaced by a fresh empty `LevelChunkSection` object, which is O(1)
   per section. Partial sections are edited with the section's own `setBlockState`: no neighbour updates, no block
   entity churn, no per-block packets.
3. Heightmaps are primed, the light engine is updated, and a full chunk-plus-light packet is re-sent to every player
   tracking the chunk.
4. A pluggable floor and surface (`Carving.Floor`, `Carving.Surface`) shapes the crater floor and paints the
   surface with the mod's own `STAR_CORE` and `STAR_TRACE` blocks, which emit light and use animated textures.
5. The mod respects a "griefing" game-rule check and a "spared" position so the caster's footing survives. [Conf]
   / [Interp]

**Lessons.** For large Totality craters (Meteor Swarm, boss slams), *section swapping plus time-slicing plus a
visual that hides the transition* is the right pattern. But it bypasses block updates (water, gravity blocks,
redstone, block entities), and full chunk resends are bandwidth-heavy (§6.3).

---

## 5. Shader, particle and animation findings

### 5.1 Which mechanism produces which effect

| mechanism (from the brief) | used? | where (evidence) |
|---|---|---|
| Vanilla particles | **Yes, minor** | `DustParticleOptions` and `ParticleTypes.BLOCK` debris at fixed ages: impacts, landing (360 per crater), embers, link sparks, evacuation (server `sendParticles`) [Conf] (N06) |
| Modified or custom particle engine | **No** | No `Particle` subclasses or particle registrations [Conf] (N03) |
| Custom geometry or animated models | **Only the held item** | Procedural quads in `RemoteRenderer`; no entity models, no GeckoLib [Conf] |
| GPU shaders | **Yes, central** | 12 fullscreen FX programs plus 4 item shaders [Conf] (N07) |
| Custom rendering passes | **Yes** | Own post stack after `renderLevel`, ping-pong targets, bloom chain, composite [Conf] |
| Screen-space or post-processing | **Yes** | Everything in §4.4, god rays, dissolve, stepped frame rate [Conf] |
| Procedural animation | **Yes** | All motion is closed-form in `t`; fBm-driven streaks and cracks; seeded per cast [Conf] |
| Texture animation techniques | **Some** | Interpolated `.mcmeta` animation on the two light-emitting blocks; real imagery sampled with manual LOD; channel-packed aux map [Conf] |
| Lighting, transparency, distortion, compositing | **Yes, faked analytically** | Terrain re-tinting as light; no blending (each pass is opaque); chromatic aberration, zoom blur and shake are UV distortions in the composite [Conf] |
| Camera manipulation or screen effects | **Yes** | Cinematic director, trauma shake through `bobHurt`, FOV override [Conf] |

### 5.2 Shader findings worth remembering (original paraphrase) [Conf] (N07)

* **Depth reconstruction is the foundation.** Inverse projection and inverse view turn every pixel into a world
  position. Effects can then be occluded by terrain, wrap over terrain, and re-light terrain.
* **Pixel-footprint anti-aliasing.** Each pass estimates the angular size of a pixel once, and every line width or
  edge softness is scaled by `distance × pixel angle`. Thin features stay at least one pixel wide and never alias
  or vanish at range.
* **Lorentzian and exponential halos** (`w²/(d²+w²)`, `exp(-d/w)`) instead of textures for glow. They are
  resolution-independent and cheap.
* **Cheap noise.** Hash-based value noise with 4–5 octaves of fBm; no noise textures.
* **Ordered early-outs.** Bounding checks (height window, radius ×1.6, box hit) skip expensive maths for most
  pixels.
* **A soft-knee tone curve** keeps HDR-ish additive glows from clipping harshly in the RGBA8 targets.
* **No blending anywhere in the post stack.** Each pass reads the previous image and writes a complete new one.
  The order is explicit and there is no overdraw.

### 5.3 Animation findings [Conf]

* **Caster pose.** `CasterPose(right xyz, left xyz, weight)` is keyframed per effect by age (for example the arm is
  raised at about tick 8–16 and held to about 496, then released) and passed from `extractRenderState` to
  `setupAnim` through Fabric's `RenderStateDataKey`. This is the render-state-safe way to pose players on modern
  versions.
* **Camera.** The shot list is evaluated as `pose(subject, t)`; blends use smoothstep and ease-in-out.
  `CameraPose.lerp` blends position, look, FOV and roll.
* **Titles and HUD.** `CastTitles` (70-tick life) shows letter-spaced titles; `StarHud` and `SevenStarsHud` draw
  2D telemetry such as range readouts, tags projected from 3D and corner brackets.

---

## 6. Performance analysis

### 6.1 Optimisations the author actually uses [Conf]

| optimisation | detail |
|---|---|
| Zero-cost idle | `FxManager.render` returns before touching GL when nothing is active and no shake remains |
| Distance cull | Effects beyond `range()` (600 blocks) contribute no passes; the network broadcast radius is 512 blocks |
| Hard cap | `MAX_ACTIVE = 24`, with oldest-first eviction |
| Dynamic resolution | FX passes run at `min(native, ~2.3 Mpx × budget)`; the budget adapts from 25% to 100% from measured GPU time (target 16 ms), drops immediately when over and recovers smoothly; 1/16 quantisation avoids reallocating every frame |
| Detail-restoring upscale | Low-resolution FX are recombined with a full-resolution scene ratio |
| Single pass per effect | Seven Stars draws 7 strikes in 1 pass using a uniform array |
| Shared tail | Bloom and composite run once per frame regardless of the number of effects |
| Analytic shapes | No geometry upload, no overdraw from stacked transparent quads |
| Shader early-outs | Bounding volumes and windows skip most pixels' heavy maths |
| Cheap textures | Channel packing (3 maps in 1), mipmaps with manual LOD selection, async decode off the render thread |
| Warm-up | All programs compiled at the title screen with a 45 ms-per-frame budget, avoiding first-cast hitches |
| Terrain | Section swapping, time slicing and nearest-first ordering |
| Network | One payload per cast; all motion derived client-side from the seed |
| Lazy buffers | The rewind tape is allocated on demand and freed after 600 idle frames |

### 6.2 Anti-patterns and risks [Conf] / [Interp]

* **Cost does not follow screen coverage** [Conf]. A distant beam covering 1% of the screen still runs its shader on
  100% of the FX pixels, because early-outs only reduce maths, not reads or writes. This is the core reason the
  architecture does not scale to many casters.
* **`glFinish()` for GPU timing** [Conf] on every 8th frame while effects are active. This forces a full CPU/GPU
  sync and can cause periodic micro-stutter [Hypo for magnitude]. 26.2 has a proper `GpuQuery` / `TimerQuery`
  abstraction (N08).
* **The governor targets 16 ms for the FX section alone** [Conf]. On a 60 Hz target that is a whole frame, so the
  effects can halve the frame rate before the governor reacts [Interp].
* **Global GL state capture and restore** [Conf]: robust, but about 60 queries per frame, and it assumes nobody
  else (for example a shader-pack mod) owns those bindings [Interp]. No compatibility handling for Iris or Sodium
  was found [Conf: no references].
* **About 190 MB of textures never freed** [Conf: no callers of `clear`].
* **Full chunk resends for craters** [Interp]: a 200-block radius touches about 490 chunk columns, each re-sent to
  every watcher.
* **Silencing vanilla sound categories** during the impact window [Conf] is a strong artistic choice that would
  affect other gameplay in a shared server.

### 6.3 What happens with 20 casters (the Totality question)

| layer | cost per extra simultaneous effect | verdict |
|---|---|---|
| Fullscreen world pass (`star_beam` / `stars_world`) | +1 fullscreen pass at FX resolution: about 37 MB of memory traffic per frame at 2.3 Mpx [Interp], plus ALU proportional to all pixels | **Expensive at N ≥ ~5–10 on integrated or mid GPUs [Hypo]**; the governor will degrade resolution for *everything* |
| Bloom and composite | Unchanged (shared) | **Scales well** |
| `ScreenFx` merge | Unchanged (max) | **Scales well**, and avoids stacking |
| Vanilla particles | Linear; 360 per crater burst | Moderate; capped only by vanilla's particle limit [Interp] |
| Cinematic (space, railgun) | Only for the caster; one at a time by priority | N/A to crowds, but very heavy for that one viewer |
| Server carving and chunk resends | Linear in craters × watchers | **Expensive** for a Meteor Swarm used by many mobs [Interp] |
| Network FX payload | ~90 bytes per cast | Negligible |

Things that need profiling before anyone relies on them [Hypo]: the real GPU milliseconds of `star_beam` and
`stars_world` at 1080p on Totality's target hardware; the stall cost of `glFinish` at this cadence; particle CPU at
7 × 360 bursts; chunk-packet bandwidth for one 200-radius carve with 1, 5 and 20 watchers.

---

## 7. Minecraft version compatibility

### 7.1 Verified facts [Conf] / [Totality] (N02, N08)

* **Totality:** Minecraft **26.2**, Fabric Loader 0.19.5, Loom 1.18.2, Fabric API 0.161.0+26.2, mod 1.2.0. The dev
  client's `options.txt` has `preferredGraphicsBackend:"opengl"`.
* **Minecraft 26.2 has two graphics backends:** `GlBackend` and `VulkanBackend`, chosen through
  `PreferredGraphicsApi` (`DEFAULT`, `OPENGL`, `VULKAN`). `DEFAULT` tries OpenGL first and falls back to Vulkan;
  `VULKAN` tries Vulkan first. Players can switch this in options. The game also forces OpenGL after a crashed
  startup.
* `GlTexture.glId()` **still exists** in 26.2, but only on the OpenGL backend.
* **Every vanilla hook the mod uses changed shape in 26.2:**

  | 1.21.11 (mod) | 26.2 |
  |---|---|
  | `LevelRenderer.renderLevel(..., Camera, view, projection, cullingProjection, ...)` | `LevelRenderer.render(..., CameraRenderState, Matrix4fc, ...)` plus a frame graph (`addMainPass(FrameGraphBuilder, ...)`) |
  | `Camera.setup(level, entity, ...)` | `Camera.update(DeltaTracker)` and `Camera.extractRenderState(CameraRenderState, float)` |
  | `GameRenderer.bobHurt(PoseStack, float)`, `getFov(...)`, `renderItemInHand(float, boolean, Matrix4f)` | `bobHurt(CameraRenderState, PoseStack)`, `renderItemInHand(CameraRenderState, float, Matrix4fc)`; no `getFov` method found under that name |
  | `AvatarRenderer.extractRenderState(Avatar, …)` | `extractRenderState(AvatarlikeEntity, AvatarRenderState, float)` |

* 26.2 offers backend-agnostic alternatives: `PostChain` (`load`, `addToFrame(FrameGraphBuilder, ...)`,
  `process`), `CommandEncoder.createRenderPass(...)` / `RenderPassDescriptor`, `copyTextureToTexture`, and
  `GpuQuery` / `GpuQueryPool` / `TimerQuery`.
* **Totality today uses no raw OpenGL and has no custom shader resource files.** Its effects use vanilla
  `RenderPipelines` (for example `HeatVisionBeamRenderer` registers a pipeline derived from
  `DEBUG_FILLED_SNIPPET`) and custom particles (Firebolt, Fireball).
* All 12 FX `.fsh`/`.glsl` files are **byte-identical** between the mod's 1.20.1 and 1.21.11 builds; only the item
  core shaders differ.

### 7.2 Classification of techniques

| class | techniques |
|---|---|
| **General graphics, version-independent** | Depth-based world reconstruction; analytic ray–primitive glow; pixel-footprint anti-aliasing; SDF decals and text; fBm and hash noise; blackbody ramps; added-light bloom (13-tap down, tent up); detail-restoring upscale; impact-frame stylisation; sphere tracing; atmosphere scattering; envelope timelines; seed determinism; max-merge of screen parameters; dynamic resolution |
| **Depends on specific Minecraft rendering APIs** | Where to inject (the end of world rendering → 26.2 frame graph); access to the main colour and depth targets; camera override (26.2 `CameraRenderState`); player arm posing (render-state extraction); `SpecialModelRenderer` items; `RenderPipeline` blend modes; HUD element replacement |
| **Depends on Fabric features** | `RenderStateDataKey`, `HudElementRegistry`, networking payloads, `PlayerLookup.around`; all present in Totality's Fabric API line [Interp: verify exact names at implementation time] |
| **Hard to adapt to 26.2** | **The raw-GL post stack as built.** It cannot run under the Vulkan backend, and under OpenGL it would fight Blaze3D's own state tracking. Rebuilding it on `RenderPipeline` / `RenderPass` / `PostChain` is required; multi-pass ping-pong, half-float targets and depth sampling must be re-expressed in those APIs. Mixin targets must be re-derived for 26.2 |

### 7.3 Proposed adaptation approaches [Suggest]

1. **Do not port the raw-GL renderer.** If Totality wants a post stack, build it from backend-agnostic Blaze3D
   objects: `GpuTexture` targets through the graphics resource allocator, `RenderPass` per fullscreen pass, and a
   `RenderPipeline` per program. Alternatively, express the shared tail (bloom and composite) as a data-driven
   `PostChain` injected into the 26.2 frame graph after the main pass.
2. **Prefer geometry passes for per-effect visuals** (Totality already does this for Heat Vision). They inherit
   depth testing, work on both backends and cost in proportion to on-screen pixels.
3. **Read depth through a frame-graph resource**, not `glBlitFramebuffer`, if terrain decals or soft intersections
   are needed.
4. **Test both backends** (`preferredGraphicsBackend: opengl` and `vulkan`) as a standing acceptance criterion for
   any custom shader work.

---

## 8. What can Totality learn? Practical recommendations [Suggest]

### 8.1 Techniques ranked by visual value ÷ cost for many simultaneous users

| rank | technique | why |
|---|---|---|
| 1 | **Seed-deterministic, timeline-evaluated client effects** (one packet per cast) | Near-zero network cost; late joiners can catch up; no drift between clients |
| 2 | **Max-merged screen parameters** with distance falloff | Prevents shake and flash stacking when 20 mobs cast |
| 3 | **Added-light (emissive-only) bloom, once per frame** | The single biggest "magic" look; fixed cost; vanilla brights don't bloom |
| 4 | **Camera-facing beam meshes with analytic radial falloff in the fragment shader** | The `star_beam` column look, but cost follows coverage; works with `RenderPipeline` on both backends |
| 5 | **Pixel-footprint minimum width** for beams, rings and lightning | Removes shimmer and disappearance at distance for almost no cost |
| 6 | **Terrain decals (SDF rings, runes, cracks) on a projected box reading depth** | Ritual circles, impact scorches and AoE telegraphs without block changes |
| 7 | **Impact frames** (a few frames, local player only) | Anime punch at negligible cost |
| 8 | **Uniform-array batching** (N instances in one draw) | For instanced auras, rune swarms and meteor glints |
| 9 | **Dynamic resolution for heavy screen passes**, measured with GPU timer queries (not `glFinish`) | A safety net for hero effects |
| 10 | **Section-swap terrain carving + time slicing + visual cover** | Large craters without block-update storms; use sparingly |
| — | Sphere-traced hero models, procedural space flights, 190 MB of imagery | Spectacular but bespoke; one viewer at a time; not a general VFX tool |

### 8.2 Application notes

* **Fire Bolt / Fireball.** Keep Totality's existing particle-based projectiles. Add a camera-facing *core glow*
  quad (analytic radial falloff, additive) and an emissive contribution to a shared bloom buffer. Impact: a
  short-lived terrain decal (scorch ring plus heat-crack noise that cools), max-merged shake, and an impact frame
  only when the local player is the caster or the target. [Suggest]
* **Meteor Swarm.** Batch every falling meteor of one cast into a single draw (instance data in an array or vertex
  attributes): glint, trail ribbon, then impact rings, following the Seven Stars pattern. Telegraph the impacts with
  decal circles. If terrain is removed, use small time-sliced carves or a crater "stamp" rather than per-block
  explosions. [Suggest]
* **Lightning.** Generate a branched polyline from the cast seed (deterministic on every client), render it as
  ribbons with the pixel-width floor, flicker it with an envelope, and give it a brief terrain light tint (decal or
  a dynamic-light approximation) plus a flash in the shared screen layer. [Suggest]
* **Heat Vision and continuous beams.** Totality's `HeatVisionBeamRenderer` already uses a `RenderPipeline`. The
  `star_beam` lessons apply directly: a radial core-to-rim colour ramp evaluated per fragment from the distance to
  the beam axis; scrolling fBm streaks along the axis; a hit-point decal and glint where the beam meets terrain; a
  minimum on-screen width. Each beam remains one draw call. [Suggest]
* **Ki, Reiatsu and auras.** Prefer a mesh shell or billboarded flame sheets around the entity with noise-scrolled
  alpha in the fragment shader, plus emissive bloom. Avoid fullscreen passes per aura. For crowds, drop to
  "impostor" level: a single soft billboard plus particles. [Suggest]
* **Large explosions.** A fixed-cost recipe: shared bloom spike, max-merged shake, expanding shock ring decal,
  debris particles capped per explosion and globally, a brief zoom-blur or chromatic pulse only within a radius of
  the camera. [Suggest]
* **Ritual effects.** Procedural SDF circles drawn into a terrain decal (rings, rune bands, star polygons) are a
  perfect match: zero textures, infinitely sharp, animatable by `t`. Write *original* glyph and rune generators
  in Totality's own style. [Suggest]
* **Boss abilities and large encounters.** Reserve the "hero tier" (§9.4): cinematic camera shots (with a player
  opt-out), screen stylisation, and optionally one fullscreen analytic pass, for boss phase transitions only. Every
  other boss attack uses world layers. [Suggest]

### 8.3 What not to adopt, and why

* **Raw OpenGL post-processing.** It breaks under 26.2's Vulkan backend and conflicts with Blaze3D state tracking.
* **One fullscreen pass per effect instance.** Cost scales with screen pixels × instance count, so it does not suit
  crowds.
* **`glFinish`-based GPU timing.** It causes CPU/GPU stalls; use timer queries.
* **Never-freed large textures.** Bound VRAM and release resources after an idle period.
* **Silencing vanilla sound categories** on a shared server: too intrusive outside a single-player showcase.
* **Bypassing block updates for terrain** without a policy for fluids, falling blocks, block entities and claims.

---

## 9. Proposed original Totality VFX architecture (for future consideration) [Suggest]

This is an original design informed by the findings, not a description of the mod. See D5–D7.

### 9.1 Principles

1. **The server decides, clients depict.** Gameplay stays server-authoritative, matching Totality's existing
   ability and entitlement direction. A cast sends one `VfxEvent` and clients evaluate the visuals
   deterministically.
2. **Effects are data plus a timeline.** Each visual parameter is a pure function of `t` (envelopes and curves),
   so effects can be scrubbed in a dev tool, replayed in captures (Totality already has an in-game capture
   workflow) and joined late.
3. **Layered, budgeted rendering.** Each effect is composed of layers with known cost classes. A director grants or
   denies layers according to a global budget and the quality profile.
4. **Backend-agnostic only.** Everything goes through Blaze3D `RenderPipeline` / `RenderPass` / `PostChain`, and is
   verified on both OpenGL and Vulkan.

### 9.2 Components (original pseudocode)

```
VfxEvent            { typeId, seed, startTick, anchors[] (pos | entityId | bone), params{}, flags }
VfxDefinition       { id, duration, layers[], keyframes, importanceBase }
Timeline            t -> { window(a,b,c,d), ease, curve tables }      // pure; no state
Layer (interface)   cost class: PARTICLE | GEOMETRY | DECAL | SCREEN | CAMERA | AUDIO
    prepare(t, instance, lod) -> draws | particle requests | screen contributions
VfxDirector (client)
    onEvent(e):   instance = new VfxInstance(definition(e.typeId), e)
    eachTick():   advance instances; emit audio/particle keyframes; retire finished
    eachFrame():
        for instance in active:
            score = importance(instance)      // local player > boss > nearby > far; on-screen size; age
            lod   = profile.ladder(score, globalBudgetRemaining)   // HERO/FULL/REDUCED/IMPOSTOR/CULLED
            instance.layers.filter(lod).forEach(prepare)
        GeometryBatcher.flush()               // group by pipeline/material; instance arrays where possible
        DecalRenderer.flush()                 // projected boxes, depth-read
        ScreenLayer.resolve(maxMerge(contributions))   // ONE shared post chain: emissive bloom + composite
        CameraLayer.apply(highestPriorityCamera())
```

### 9.3 How it connects to existing Totality systems [Interp: names from the source survey]

* Spells (`FireboltSpell`, `FireballSpell`) and abilities (`HeatVisionAbility`) remain authoritative. Their
  existing `*Vfx` coordinators (`FireboltVfx`, `FireballVfx`, `VisualPortalVfx`) would become `VfxDefinition`s or
  emit `VfxEvent`s.
* Existing particle classes become `PARTICLE` layers; `HeatVisionBeamRenderer` becomes the first `GEOMETRY` beam
  layer.
* The capture workflow can render a timeline at fixed `t` values for deterministic review screenshots.

### 9.4 Quality settings and scalability

| setting | Low | Medium | High | Ultra |
|---|---|---|---|---|
| Shared bloom | off (glow sprites only) | half-res, 3 levels | 5 levels | 5 levels + lens dirt |
| Decals | telegraphs only | on, ≤ 8 | on, ≤ 32 | on, ≤ 64 |
| Particles per effect | 25% | 50% | 100% | 100% |
| Screen stylisation (impact frames, aberration) | off | local player only | local player only | local player + bosses |
| Hero passes and cinematics | off | opt-in | on (opt-out) | on |
| Dynamic resolution for SCREEN layer | n/a | on | on | on |

The global caps are independent of quality: maximum active instances per tier, a per-frame geometry budget, a
particle budget, and a decal budget. Excess instances drop one rung down the ladder (D7) instead of disappearing,
and "culled" instances keep advancing their timelines so they reappear in sync.

### 9.5 Trade-offs to discuss

* **Geometry vs fullscreen analytic.** Geometry costs in proportion to coverage and handles crowds; fullscreen
  analytic gives volumetric looks and terrain interaction "for free" but has a fixed per-instance cost. The hybrid
  keeps fullscreen analytic work for the hero tier.
* **Depth-read decals** need access to scene depth in the 26.2 frame graph, which has an integration cost that must
  be verified.
* **Determinism vs reactivity.** Pure timelines do not react to the world after the cast; reactive parts (homing,
  collisions) should stay entity-driven, as Firebolt and Fireball are today.

---

## 10. Techniques that deserve further experimentation (no implementation in this task)

Ordered by value for effort. Each could be a small, isolated creative test in Totality's established style.
I did not write a separate roadmap file because this ordered list carries the same information.

1. **Emissive-only bloom on 26.2**: effects write to an emissive target; one shared downsample and upsample chain;
   composite. Verify on OpenGL **and** Vulkan, and measure with timer queries.
2. **Beam fragment shading on the existing Heat Vision pipeline**: radial core ramp, scrolling noise, pixel-width
   floor, hit glint. Compare before and after captures.
3. **Projected terrain decal with SDF ritual circle**: an original rune generator, depth-read projected box.
   Confirms the frame-graph depth access path.
4. **`ScreenFx`-style max-merge service**: shake, flash and aberration with distance falloff; stress test with 20
   simultaneous Fireball impacts.
5. **Impact-frame composite** for the local player's own heavy hits (2–4 frames).
6. **Seed-deterministic Meteor Swarm prototype**: one event, N meteors batched in one draw, staggered keyframes.
7. **Cinematic shot director** with block-collision clipping and player opt-out, for a boss intro.
8. **Time-sliced section carving** in the disposable verification world, measuring chunk-packet bandwidth with
   1, 5 and 20 watchers.

---

## 11. Unknowns and limitations

* **Not run.** No runtime screenshots, frame captures (e.g. RenderDoc) or timings exist for this audit. Visual
  descriptions are reconstructed from code and shaders, and may differ in detail from what a player sees. I used no
  promotional material as evidence.
* **No decompiler.** Bytecode-level reading is reliable for call graphs, constants and control-flow landmarks, but
  some arithmetic inside long methods was summarised rather than reconstructed exactly (for example the precise
  per-frame envelope products in `StarFx.worldPass`).
* **Intermediary decoding** of vanilla targets is [Interp]. It is strongly corroborated by parameter names in the
  local-variable tables but was not checked against a 1.21.11 mapping file, which was not available offline.
* **The 1.20.1 and Forge builds** were compared only at file-list and shader level, not class by class.
* **Iris, Sodium and other shader-pack interaction** was not testable; the mod contains no compatibility code for
  them.
* **Which bloom level feeds auto exposure** and the exact `snapshotDepth` handling for every depth format are
  [Interp].
* **Totality-side names** in §9.3 come from a file-name survey, not a code review.
* **Performance numbers** in §3.7, §6 and N09 are arithmetic estimates; all GPU-time claims are [Hypo].
* **Legal.** This audit treats the JAR as confidential reference material. Reading shader text and disassembling
  classes for interoperability-style research is described here only at the level of mechanisms. The bundle
  contains identifiers, counts, constants and original descriptions, not code, shaders, textures, models or sounds.

---

## 12. Intellectual property boundaries (how this audit complied)

* The original JAR was not modified, copied into the repository or added to the bundle. All extraction happened in
  the session scratch directory outside the repository.
* No decompiled source, bytecode listing, shader source, texture, model, sound or animation data from the mod is
  included in this document or the review bundle. The bundle contains file names and sizes, class and member
  signatures, numeric constants, decoded GL enums, derived counts and original prose and diagrams.
* All pseudocode and diagrams are original. Techniques are named by their general graphics names (sphere tracing,
  ray–cylinder intersection, fBm, tent upsampling, and so on), which are public-domain knowledge.
* The bundled astronomical imagery is third-party (NASA, ESO and Solar System Scope, with their own licences, listed
  in the mod's `CREDITS.txt`). It is mentioned only as a fact about the mod and is not recommended for reuse here.
* Recommendations call for **original implementations**: original glyphs, palettes, timings, shapes and code.

---

## Appendix A — Evidence index

| note | content |
|---|---|
| N01 `01_identification.txt` | Hashes, outer and inner metadata, mixin configs, toolchain, credits summary |
| N02 `02_jar_inventory.txt` | File list and sizes; 1.21.11 vs 1.20.1 file and shader comparison |
| N03 `03_class_signatures.txt` | `javap -p` declarations (no bodies) |
| N04 `04_constants_and_timelines.txt` | All named constants, skill table |
| N05 `05_mixin_targets_and_gl_constants.txt` | Mixin targets, intermediary decoding, GL enum decoding |
| N06 `06_execution_path_trace.txt` | Key press → server → broadcast → client → per-frame render path |
| N07 `07_shader_inventory.txt` | Per-program sizes, fetch and loop counts, technique map (original wording) |
| N08 `08_totality_26.2_compatibility.txt` | Totality versions, 26.2 backends, hook signatures, alternative APIs, Totality render survey |
| N09 `09_resource_cost_estimates.txt` | Render-target and texture memory arithmetic, per-pixel work, N-effect scaling |
| `diagrams/ARCHITECTURE_DIAGRAMS.md` | D1–D4 observed architecture; D5–D7 proposed Totality architecture |
