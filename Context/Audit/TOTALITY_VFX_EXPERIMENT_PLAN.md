# Totality VFX Experiments — Plan and Roadmap

Date: 2026-10-01 (updated 2026-10-02, finalization) · Status: **Experiment 1 completed and approved**,
committed locally. Heat Vision V2 (Experiment 2) and the revised roadmap follow in the next commit.
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
