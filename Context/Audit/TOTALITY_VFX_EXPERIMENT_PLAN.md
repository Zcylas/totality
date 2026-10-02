# Totality VFX Experiments — Plan and Roadmap

Date: 2026-10-01 (updated 2026-10-02: finalization; Fireball V2 Phase A to B5) · Status: **Experiments 1 and 2
completed and approved**, committed locally. **Experiment 3 (Fireball V2): implemented (B1-B5), approved and committed
locally (not pushed)** (`TOTALITY_VFX_FIREBALL_V2_FINAL_REPORT.md`; earlier phases:
`TOTALITY_VFX_FIREBALL_V2_DESIGN_REPORT.md`, `TOTALITY_VFX_FIREBALL_V2_B1_B2_REPORT.md`,
`TOTALITY_VFX_FIREBALL_V2_B2_REFINEMENT_REPORT.md`). The rest of the roadmap is planning only.
Source of the ideas: `Context/Audit/TOTALITY_SHOOTING_STAR_VFX_AUDIT.md` (§7.3, §8, §10).

## Purpose of this prototype phase

The audit found that the most valuable VFX techniques are general graphics techniques, but the inspiration mod's
**integration** (a private raw-OpenGL post stack) cannot be used by Totality. Minecraft 26.2 has both an OpenGL and a
Vulkan backend, and raw GL calls bypass both Blaze3D and its state tracking.

This phase answers one question per experiment, using **only Minecraft's supported Blaze3D APIs**
(`RenderPipeline`, `RenderPass`, `CommandEncoder`, `GpuTexture`, `TimerQuery`) and Fabric events:

> Can Totality build the technique as an original, reusable, backend-agnostic system, at a cost we can measure and
> control?

Rules for every experiment:

* Original code, shaders and assets only. Nothing from the Shooting Star Demo is copied.
* Isolated, small, and off unless something uses it. No changes to unrelated systems.
* Each experiment records **confirmed** results (tested) separately from **inferences** and **future** ideas.
* Every custom shader or render pass is verified on **both** OpenGL and Vulkan before it can be recommended.
* Every experiment ends with a report, screenshots and a review bundle, and then stops for review. It is committed
  locally only after approval (never pushed as part of the experiment).

---

## Terminology (2026-10-02)

The system built in Experiment 1, first called the "Shared Emissive Bloom System", is named the
**Totality Emissive Rendering Layer** (short: *Emissive Rendering Layer*). This is the approved, official term.

* The **Emissive Rendering Layer** is the shared contribution layer. Effects submit emissive information (light they
  give off) into one Totality-only buffer per frame, depth-tested against the world, under shared settings
  (on/off, intensity, quality).
* **Bloom** is one *feature* of the layer: the blur chain and composite that turn the emissive buffer into a glow
  around the sources. Bloom is not the name of the system. Later features may read the same emissive information
  (for example light-spill tinting or heat-shimmer masks) without changing how effects contribute.
* Code names from the prototype (`EmissiveGlow`, `EmissiveSource`, `EmissiveBloomRenderer`) are **kept** for now
  (decision 2026-10-02: no naming refactor). `EmissiveBloomRenderer` correctly names the bloom feature. Renaming the
  facade is a decision for the future VFX API.

## Experiment 1 — Totality Emissive Rendering Layer — completed, approved

Report: `TOTALITY_VFX_EMISSIVE_BLOOM_EXPERIMENT_REPORT.md` (the file keeps its original name).

**Goal.** A reusable glow pipeline. Totality effects contribute *emissive* information to a shared, Totality-only
buffer; one shared bloom pass per frame (the layer's bloom feature) turns it into a controlled glow that is added to
the image.

**Outcome.** All six success criteria were met (report §1, §8, §9): object-only vs object + glow, only Totality
sources bloom, configurable intensity with "off" restoring the normal image, no GL calls and identical results on
OpenGL and Vulkan, measured GPU cost (≈ 0.06–0.15 ms on an RX 6600), and no regressions in Fireball, Phone and
Camera & Gallery.

**Decision (2026-10-02): approved as the shared prototype.** Kept as is: the shared emissive buffer, the bloom
implementation, the OpenGL and Vulkan compatibility, and the prototype class names.

**Future improvements (deferred, not part of the approved prototype):**

1. Generalised effect ownership and automatic lifecycle management (sources owned by effect instances, expiry).
2. Global and per-effect brightness budgeting.
3. Graphics quality tiers and settings-screen integration.
4. Optional reduction of glow bleeding around occluding geometry (depth-aware upsample).
5. Additional compatibility testing (Fabulous graphics, integrated GPUs, other vendors, Sodium/Iris if supported).

## Experiment 2 — Heat Vision V2 — completed, approved

Report: `TOTALITY_VFX_HEAT_VISION_V2_EXPERIMENT_REPORT.md`.

**Goal.** Make an existing ability significantly more impressive on the new foundation while staying performant and
maintainable.

**Outcome.** Eye-based twin beams with a white-hot core, orange body and red edge, animated energy flow, ignition and
fade, an impact hotspot with particles, near-camera fading and screen-space width limits, and a glow from the
Emissive Rendering Layer. Verified on OpenGL and Vulkan; ≈ 0.003 / 0.011 ms for one player's beams plus the shared
layer pass. The server-side ability is unchanged. The investigation confirmed that the pre-V2 beam's
`LESS_THAN_OR_EQUAL` depth test was inverted under reversed-Z.

**Decision (2026-10-02): approved as a successful prototype.** Kept as is: the corrected reversed-Z depth handling,
eye-based origins, the colour structure, energy flow, ignition/fade, impact hotspot and particles, near-camera fade and
width limits, the Emissive Rendering Layer integration, and the server-side gameplay. No further renderer rewrite or
new visual design.

`HeatVisionClassicRenderer` stays **for now, exclusively as a development-only comparison and debugging tool**. It
is reachable only from `/totalityvfx heatvision classic` and capture scene 67 (both registered only in a Fabric
development environment), and `HeatVisionBeamRenderer.setClassic` ignores the switch outside a development
environment. A source-level test enforces both.

**Deferred (recorded, not implemented):**

1. **Multiplayer visibility (ability synchronisation).** Other players cannot see Heat Vision: no beam state reaches
   other clients. Future task: a small "channelling Heat Vision" sync so watchers render the beams with the same V2
   code (it already draws any number of beams per frame).
2. **Optimistic client activation.** The client sets its channelling flag when the key is pressed, so it may display
   Heat Vision before (or even though) the server accepts activation (not Kryptonian, no mana). Future task, together
   with item 1: drive the presentation from server-confirmed ability state.
3. **Global emissive brightness budget** — part of the future VFX API (see Experiment 1, item 2).

---

## Permanent technical lessons (for all future VFX work)

1. **Reversed-Z.** Minecraft 26.2 clears depth to 0 and nearer means larger (default test `GREATER_THAN_OR_EQUAL`).
   Custom rendering must account for this. Verify the intended occlusion (for example with a side-on probe behind an
   occluder) instead of reusing an existing depth test blindly: the pre-V2 Heat Vision test was silently inverted.
2. **Supported, backend-agnostic APIs only.** Custom render passes use Blaze3D (`RenderPipeline`, `RenderPass`,
   `CommandEncoder`, `GpuTexture`, `TimerQuery`) and Fabric events. No raw OpenGL (`org.lwjgl.opengl`, `GlTexture`,
   `glId()`).
3. **Test on both backends.** Every custom render path is run on OpenGL and on Vulkan (force Vulkan with the launch
   argument `--graphicsBackend vulkan`; the `options.txt` value alone was not reliable on a fresh game directory).
4. **Share expensive post-processing.** Prefer one shared pass (such as the Emissive Rendering Layer's bloom) over
   duplicating expensive passes per ability. Effects contribute; the shared pass runs once per frame.
5. **Reusable techniques, distinct identities.** Techniques and primitives are shared, but every spell and ability
   keeps its own visual identity (shape, colour, timing, motion).
6. **Measured vs estimated.** Performance claims must distinguish actual measurements (GPU timestamps, logged buffer
   sizes, with hardware and resolution) from estimates and extrapolations.
7. **Atmosphere is cosmetic.** Visual effects must never change Minecraft's actual world time, weather or global
   gameplay conditions purely for cinematic atmosphere. Sky darkening, flashes, colour grading and similar effects are
   cosmetic, properly scoped and reversible; real gameplay effects belong to the authoritative gameplay systems. This
   applies to every spell, especially Dark Star and Meteor Swarm (decision 2026-10-02).
8. **Bake procedural noise when it dominates.** Fireball's explosion cost 0.29 ms (1) / 2.6 ms (20) almost entirely in
   per-pixel value-noise octaves; baking the same fbm into a tileable texture (three fetches instead of 16 octaves)
   cut it to about 0.05 / 0.54 ms with no visible change (Fireball V2 B5). Measure first, then bake.
9. **Bound everything on screen near the camera.** Particles, emissive quads and billboards need an angular size
   limit and a near-camera fade: without one, a spark or glow quad a block from the eye becomes a large flat square.

## Candidate components of the future VFX API (planning only)

These are candidates distilled from Experiments 1 and 2 and the audit. The full VFX API is **not** being built yet.

| component | from | role |
|---|---|---|
| **Emissive Rendering Layer** | Experiment 1 (exists) | Shared emissive contribution buffer with bloom as its first feature; future lifecycle ownership, brightness budget, quality tiers |
| **Beam primitive** | Experiment 2; generalised in Experiment 3 | Camera-facing segmented ribbon with min/max angular width, near-camera fade, UV along the length, shader-driven energy flow; now a shared `client/vfx/ribbon/RibbonGeometry` with a width profile (Heat Vision's beams, Fireball's tapering streak) |
| **Impact primitive** | Experiment 2 | Camera-facing hotspot pulled towards the camera, optional particles, emissive contribution |
| **Animation envelope** | Experiment 2 | Time-based ignite/sustain/fade envelopes (never frame-count based) |
| **Shared Screen FX** | Audit §8; implemented in Experiment 3 (B1) | Camera shake, flashes, impact frames with explicit stacking (max-merge, distance falloff, priorities) and accessibility limits |
| **Emissive brightness budget** | Experiment 3 (B4) | Per-effect budget groups plus one final global cap in the Emissive Rendering Layer (`EmissiveBudget`) |
| **Layered fire volumes** | Experiment 3 (B2-B5) | `FireExplosion`: many procedurally shaded, independently animated volumes in one draw, turbulence from a precomputed noise texture |
| **Particle budget** | Experiment 3 (B4) | Shared live-particle budget with distance quality and a near-camera size limit (Fireball's own today; candidate for all spells) |
| **Terrain decals** | Audit §8 | Projected, depth-reading decals for ritual circles, markers, scorch marks and targeting indicators |

---

## Roadmap (revised 2026-10-02, order corrected at Fireball V2 Phase A — planning notes, not implementation instructions)

The next experiments improve **actual Totality content** while each introduces reusable VFX techniques.

1. **Experiment 3 — Fireball V2.** Research its D&D rules, redesign its projectile and explosion, and develop the
   **Shared Screen FX Service** (the first Screen FX consumer). Phase A (research and design), B1 (Screen FX), B2 and
   B2.1 (explosion, approved), B3 (projectile), B4 (emissive integration, particles, aftermath) and B5 (optimisation,
   final testing) delivered 2026-10-02. **Approved and committed locally (2026-10-02).** The next development task
   is undecided (decision F5).
2. **Experiment 4 — Eldritch Blast V2 (planned).** Improve the existing spell, with the video references in
   `Context/References/Videos/` (`eldritch_blast_youtube`, `eldritch_blast_tiktok`; for study, not for copying).
   Not started; whether it comes next is undecided.
3. **Experiment 5 — Magic Missile V2.** Guided magical projectiles: trajectories, trails and impact effects.
4. **Experiment 6 — Lightning Bolt V2.** Improve the existing spell's lightning rendering and impacts.
5. **Further candidates:** Ground Slam; Portals and Gates; Globe of Invulnerability; Dark Star; **Plane Shift V1**.
   * Plane Shift V1 is intended to support Overworld → Nether travel and travel back from the Nether. Its broader
     functionality is deferred. It may serve as the introduction of a minimal **Player Animation API** for casting
     animations.

These plans remain flexible; the exact order after Fireball V2 can still change.

**Separate future feature — Targeting Preview API V1** (not part of any experiment above until scheduled): a
BG3-inspired preview of a spell's projected area and affected creatures, computed with the same common code as the
server-authoritative mechanics and drawn with Terrain decals and other shared VFX techniques. Recorded in the Fireball
V2 design report §11; when to implement it is a separate decision.

**Meteor Swarm** remains the eventual large-scale integration experiment (timelines, seed-deterministic paths,
batched rendering, impacts, Screen FX, decals). It has no fixed experiment number.

### Order and gates

1. Each experiment stops for review; it is committed locally only after approval.
2. **Finish the planned VFX experiments before migrating Totality to Minecraft 26.3.**
3. After the migration, return to other major development work, including **Codex, Scan and Technology**.
