# VFX Experiment 3 — Fireball V2: Phase A Design Report

Date: 2026-10-02 · Status: Phase A **reviewed**; architecture provisionally approved, explosion look **not** yet
approved (decisions in §15). Phase B1–B2 report: `TOTALITY_VFX_FIREBALL_V2_B1_B2_REPORT.md`. Nothing committed or
pushed. Target: Minecraft 26.2 (the VFX phase finishes before the 26.3 migration).
Review bundle: `Context/Audit/Review Bundles/TOTALITY_VFX_FIREBALL_V2_DESIGN_REVIEW.zip`

**Labels:** **[Confirmed]** = read in code/rules or seen in a real capture · **[Inference]** = technical interpretation
of confirmed facts · **[Proposal]** = design proposed here, needs approval · **[Future]** = an idea outside this
experiment, not designed in detail and not implemented.

Inputs read: Master Reference v3.8 (relevant: §26K.12 D&D as a reconsiderable layer, the material/focus and Wand of
Fireballs rules, cosmetic-VFX-never-affects-gameplay rule, accessibility-not-by-colour-alone); the Shooting Star VFX
audit; the VFX plan; the Experiment 1, 2 and 1&2 finalization reports; the Emissive Rendering Layer and Heat Vision V2
code; the complete Fireball implementation and its tests; the earlier Creative Test G / Fireball correction reports
(`Totality-Research/fireball-vfx`, `fireball-correction`). Git state at start: `master` at `5b04c81b`, only the known
unrelated items and the uncommitted finalization report in the working tree.

---

## 1. Summary

* **D&D 2024 Fireball** is an instantaneous 3rd-level Evocation: a *bright streak* to a chosen point within 150 ft,
  then a 20-ft-radius sphere explosion (DEX save, 8d6 Fire, half on success; +1d6 per slot above 3rd; flammable
  unattended objects start burning). The sphere spreads in straight lines from its centre (total cover blocks it) —
  2014's "spreads around corners" is gone.
* **Totality's Fireball** is a server-authoritative 1.2-block/tick projectile that detonates on first contact into a
  6-block sphere (DEX vs spell save DC, 8d6/half), ignites random blocks, and is presented by a full-bright
  camera-facing sprite plus particles. Its gameplay is sound; several deliberate and accidental differences from D&D
  are recorded in §4 — **none is changed by this experiment**.
* **The VFX problem** is the explosion: one flat 20-tick billboard sprite, offset from the real damage centre, cut off
  by walls and floors, with no light, no readable boundary, no screen response, and an aftermath dominated by vanilla
  fire blocks (§5).
* **Fireball V2 proposal:** keep every mechanic; replace the explosion with a **depth-tested 3D explosion shell**
  (procedural fire shader, temperature ramp, ragged hot rim at exactly the damage radius) centred on the true damage
  point, a **ground ring** where the sphere meets terrain, a capped flash, **Emissive Rendering Layer** glow, and a
  short, clearly cosmetic smoke-and-ember aftermath; refresh the projectile as a white-hot **bead + tail ribbon** built
  on Heat Vision V2's beam primitive. Storyboard frames (original procedural mock-ups) are in §7.
* **Shared Screen FX Service** (designed, not built): client-side, max-merged channels for shake / flash / impact
  frames, distance attenuation, priorities, global caps and a flash budget, vanilla accessibility options honoured.
  Twenty explosions produce at most the shake and flash of the nearest one (plus a small bounded bonus).
* **Targeting Preview** is recorded as a separate future API (§11).

---

## 2. D&D Fireball research

### 2.1 Sources

| source | what it supports | strength |
|---|---|---|
| AideDD, Fireball (2024 PHB entry) — https://www.aidedd.org/spell/fireball | 2024 header fields, text, scaling | secondary transcription of the 2024 PHB |
| Web search summary of the 2024 entry (World Anvil / wiki transcriptions) | 2024 wording "blossoms with a low roar into a fiery explosion", "Flammable objects … start burning", "1d6 for each spell slot level above 3" | secondary |
| D&D 5e wikidot, Fireball (2014 PHB) — https://dnd5e.wikidot.com/spell:fireball | 2014 text incl. "The fire spreads around corners." | secondary transcription of the 2014 PHB / SRD 5.1 |
| SRD 5.1 Fireball (5e.d20srd.org, fetched via search summary) — https://5e.d20srd.org/srd/spells/fireball.htm | 2014 "bright streak flashes from your pointing finger" | SRD (CC) text, page itself returned HTTP 403 to the fetcher |
| D&D Beyond free rules, spell descriptions — https://www.dndbeyond.com/sources/dnd/free-rules/spell-descriptions | official 2024 free rules; **the fetcher only received A–D**, so Fireball itself could not be read there | official, incomplete fetch |
| D&D Beyond forum quotes of the 2024 PHB "Damage Rolls" rule — https://www.dndbeyond.com/forums/dungeons-dragons-discussion/rules-game-mechanics/241340-magic-missile-one-damage-roll-or-multiple-rolls-in | 2024: "When you create a damaging effect that forces two or more targets to make saving throws against it at the same time, roll the damage once for all the targets." | quoted PHB text in a forum — verify against the book |
| 2024 Rules Glossary, Area of Effect / Sphere (search summaries of D&D Beyond rules threads) | straight-line spread from the point of origin; total cover blocks; point of origin placed on the near side of an obstruction you can't see through | secondary |
| D&D Beyond, "4 key changes to spells in the 2024 PHB" — https://www.dndbeyond.com/posts/1762-4-key-changes-to-spells-in-the-2024-players | general 2024 spell changes (no Fireball-specific change) | official article |
| Earlier internal comparison: `Totality-Research/fireball-correction/TOTALITY_FIREBALL_VFX_CORRECTION_REPORT.md` §2 (SRD 5.1 vs SRD 5.2) | consistent with all of the above | internal |

**Limitation [Confirmed]:** I could not load a primary official page containing the 2024 Fireball text itself (D&D
Beyond's free rules page truncated before "F"). Every 2024 field below agrees across the independent secondary
sources and with the earlier internal SRD 5.2 comparison. **Recommendation:** Stefan verifies §2.2 against the 2024
PHB / SRD 5.2 before any *mechanical* decision relies on it. The visual design does not depend on the uncertain parts.

### 2.2 The official rules (2024), separated from interpretation

| field | 2024 rule | 2014 difference |
|---|---|---|
| Level / school | Level 3 Evocation | "3rd-level evocation" (same) |
| Classes | Sorcerer, Wizard | same |
| Casting time | Action | "1 action" (same meaning) |
| Range | 150 feet | same |
| Components | V, S, M (a ball of bat guano and sulfur) — not consumed, no cost; a focus can replace it under the general rules | "a tiny ball" |
| Duration | Instantaneous | same |
| Visual text | "A bright streak flashes from you to a point you choose within range and then blossoms with a low roar into a fiery explosion." | "…from your pointing finger … into an explosion of flame." |
| Area | each creature in a **20-foot-radius Sphere** centred on that point | same size |
| Save / damage | DEX save; **8d6 Fire** on a failure, half on a success | same |
| Multiple targets | roll the damage **once** for all targets making the save at the same time (2024 Damage Rolls rule, forum-quoted) | 2014: roll once for all targets of one effect |
| Spread | general Area-of-Effect rule: the Sphere extends in straight lines from its point of origin; a location with no unblocked line (total cover) is not in the area | **"The fire spreads around corners."** (removed in 2024) |
| Point of origin | general rule: if you choose a point you can't see behind an obstruction, the origin appears on the near side of it | — |
| Objects | flammable objects in the area that aren't worn or carried **start burning** | "ignites flammable objects …" |
| Upcast | +1d6 per spell slot level above 3 | same |

**What the rules say about the look [Confirmed text, Inference on meaning]:**
* "A bright streak **flashes** … to a point you choose" — fast, thin, bright, instantaneous in game time (duration
  Instantaneous). It is **not** a slow, large ball of fire travelling through the air; the *explosion* is the ball.
* "then **blossoms** with a low roar" — a sudden *opening outwards* from a point, with a deep sound, i.e. a rapid
  spherical expansion, not a slowly growing cloud.
* "a fiery explosion" / "Sphere" — the effect fills a sphere; on flat ground the visible part is a dome.
* Nothing in the text implies lingering damage; burning objects are a separate consequence.

**Examples, interpretations and adaptations (not rules):** Baldur's Gate 3 renders Fireball as a fast travelling
projectile that detonates on first collision — an adaptation, like Totality's. Pop-culture "big slow fireball"
depictions are not supported by the text.

---

## 3. Audit of Totality's current Fireball [Confirmed unless marked]

### 3.1 Registration and definition
* `api/magic/spell/destruction/FireballSpell.java` — `totality:fireball`, Level 3, `SpellSchool.DESTRUCTION`,
  ACTION, V/S/M, material *bat guano + sulphur dust* (`SpellMaterial.replaceableItems`), `CastType.INSTANT`, cooldown
  **100 ticks** (line 53), `isDefault()` true. Registered in `SpellRegistry` (line 60).
* Entity `totality:fireball_projectile` (`init/ModEntities.java` 124–139): 0.5×0.5, **client tracking range 64**,
  update interval 1.
* Particles (`init/ModParticles.java` 26–29): `fireball_blast`, `fireball_smoke`, `fireball_fragment`,
  `fireball_detonation` (emitter, *always-show*), plus reused `firebolt_*` particles.

### 3.2 Casting and resources (`networking/ability/ActivateAbilityHandler.java`)
1. Entitlement re-check (line 52), cooldown check (54), casting restrictions, then a **3rd-level slot** must exist.
2. `canActivate` → `checkMaterials` (materials in inventory/pouch, or an Arcane Focus in either hand).
3. `onActivate` → `consumeMaterials` (consumed unless a focus is held — Totality's own rule, Master v3.8), spell save
   DC = 8 + proficiency + casting ability, spawns the projectile from the **server-side look direction** (entity line
   79), plays `FIRECHARGE_USE` at the caster for everyone.
4. After success: cooldown starts and **one 3rd-level slot is spent with `EXACT_TIER`** (line 131): no upcasting.

### 3.3 Projectile (`entity/magic/FireballProjectileEntity.java`)
* Speed **1.2 blocks/tick** (24 blocks/s, line 50), straight line (no gravity/drag), lifetime **100 ticks** → max
  ~120 blocks, after which it **fizzles without damage** (a puff particle).
* Each tick: block clip, then entity hit along the move; on a hit it moves to the true impact point (block hit:
  0.25 blocks back from the surface; entity: where the path enters the inflated box) and explodes.
* Client side: the same tick logic runs for prediction and the trail; the client discards its copy on a predicted
  hit (`explode()` returns early on clients).

### 3.4 Explosion and damage (`explode()`, lines 176–233)
* **No vanilla explosion.** The server emits `GameEvent.EXPLODE` and sends a `ClientboundExplodePacket` (sound
  `GENERIC_EXPLODE`, particle `fireball_detonation`, **no knockback**) to every player within **64 blocks** (line 242).
* **Ignition:** for every block position in the 6-block sphere, if fire can be placed above it, a **1-in-3** chance
  to set fire (line 201). No flammability, line-of-sight or cover test.
* **Targets:** every `LivingEntity` whose **position** is within 6 blocks of the blast centre (line 214), caster
  included (hit with no attacker).
* **Damage:** **per target**, 8d6 rolled with that target's RNG (line 222), DEX save vs the DC, success → `raw / 2f`
  (line 225, no rounding here or in `TotalityDamage`), dealt as one `TotalityDamage.hurt(…, FIRE, …, IS_AOE,
  NO_CONDITIONS)`.

### 3.5 Presentation (client)
* **Projectile renderer** (`client/renderer/entity/magic/FireballProjectileRenderer.java`): four
  `entityTranslucentEmissive` textured quads (comet tail 3.2×0.8, round core 0.75, rear bloom, halo), 4-frame
  animation, hidden for the first 1.2 blocks, grows over 2.5 blocks. Textures generated by
  `Totality-Research/fireball-correction/tools/fireball_textures.py` after `Context/References/Other/fireball.png`.
* **Cast burst / trail** (`FireballVfx.castBurst`, `trail`): Firebolt flash/sparks/wisps/embers; trail particles
  start 2.5 blocks out. Triggered by the **first client tick the client sees the entity** (line 107).
* **Detonation** (`FireballDetonationParticle` → `FireballVfx.detonate/afterDetonation`): one
  `FireballBlastParticle` — a **single camera-facing pixel-art sprite**, 8 frames over **20 ticks**, size 95 % of the
  radius, centred **0.2 × radius off the surface** (`FireballVfx` 90–91) — plus a 6.5-size flash, 40 sparks, 24
  streaks, 14 fragments, 36 wisps; a 4-tick ground ring of particles; 40 ticks of smoke and embers.
* **No** Emissive Rendering Layer use, **no** screen effect, **no** dedicated sounds beyond vanilla.

### 3.6 Networking and responsibilities
| concern | owner |
|---|---|
| cast validation, slot/material/cooldown, aim direction, flight, hit, damage, saves, ignition | **server** |
| projectile state to clients | vanilla entity tracking (64 blocks, every tick) |
| detonation presentation | explode packet → client emitter particle (within 64 blocks of the blast) |
| trail, cast burst, projectile visuals | client, from the tracked entity |

Other players see the projectile and the explosion (both replicated) **[Confirmed in code; seen in capture scene 58
"other_player"]**. There is no Fireball-specific payload.

### 3.7 Automated tests and evidence
* `FireballVfxRegressionTest` (source-level): spell identity/level/cooldown, speed, radius, damage dice, explosion is
  presentation-only, particle sprites exist, rendering stays client-side.
* `FireballVerification` (live server, 40 checks): one fireball per cast at 1.2 b/t, true impact point, damage
  exactly once per creature in radius, caster self-hit, half on save, none at 7.1 blocks, no knockback, no Force
  damage, fire placed, expiry without explosion.
* Capture scene 58 (`FireballCapture`, 570 frames): ran to completion in today's OpenGL run (finalization report).
  It logs entity/emitter counts as `info:` lines rather than PASS checks (all fireball entities back to 0; bundle
  `evidence/scene58_info_lines.txt`). Its frames are the "current V1" evidence in this report.

### 3.8 Reusable vs to be replaced
| part | verdict |
|---|---|
| Spell definition, cast handler, slot/material/cooldown, server flight, hit detection, impact point, damage/save/ignition | **keep unchanged** (mechanics) |
| Explode packet as the detonation trigger (sound + emitter at the blast centre) | **keep** as the trigger (possibly extended, §9 decision D6) |
| `FireballDetonationParticle` emitter pattern (one per client per blast, counters for captures) | **keep**, it becomes the driver of the new explosion instance |
| `FireballVfx.surfaceNormal`, `groundBelow` | **reuse** (ground ring placement, wall cases) |
| Firebolt ember/spark/wisp particles, `fireball_smoke`, `fireball_fragment` | **reuse** for trail embers and aftermath (budgeted) |
| `FireballBlastParticle` (flat sprite), the 6.5 flash particle, the particle ground ring | **replace** |
| Projectile textured quads | **replace** with bead + ribbon (keep the textures available as fallback during development) |
| `castBurst` on first sighting | **fix** (only for a fireball the client sees at its cast point, §5 V7) |

---

## 4. D&D vs Totality — differences (documentation only; no change made)

| # | topic | D&D 2024 | Totality now | kind |
|---|---|---|---|---|
| M1 | Delivery | instantaneous streak to a chosen point | 1.2 b/t projectile, detonates on first contact | deliberate adaptation (like BG3) |
| M2 | Range | 150 ft (≈ 46 blocks at 1 block = 1 m) | flies up to ~120 blocks, then fizzles with no effect | difference |
| M3 | Air burst at a chosen point | allowed | impossible (only on contact) | difference |
| M4 | Damage roll | once for all targets | **per target** (each rolls its own 8d6) | likely accidental |
| M5 | Half damage | half, rounded down (general rule) | `raw / 2f`, fractional | likely accidental |
| M6 | Area membership | a creature is in the area if the area reaches it | entity **position** within 6 blocks | approximation |
| M7 | Cover | total cover blocks the spread | none: damage and ignition **through walls** | known open decision (2014-style "around corners") |
| M8 | Objects | flammable unattended objects start burning | 1-in-3 fire on any block top in the sphere, any material, through walls | approximation |
| M9 | Upcasting | +1d6 per slot above 3 | none (EXACT_TIER slot) | not implemented |
| M10 | Material | not consumed | consumed without a focus | deliberate Totality rule (Master v3.8) |
| M11 | Cooldown | none (action economy) | 5 s | deliberate Totality adaptation |
| M12 | Visible/audible range | — | explode packet within 64 blocks; entity tracking 64 | engine limit |

**Mechanical problems recorded separately (not fixed):** M4, M5 and M7/M8 are the ones most likely to be unintended;
M2/M3 matter for a future Targeting Preview. Each needs its own approval and its own gameplay task. D&D is a content
source, not the authority (Master §26K.12), so "differs from D&D" is not automatically a defect.

---

## 5. Problems with the current VFX

Evidence: today's capture scene 58 frames (bundle `evidence/current_v1_*.png`) and the code above.

| # | problem | evidence |
|---|---|---|
| V1 | **The explosion is one flat billboard.** It has no volume and turns with the camera; it reads as a sticker, not a sphere. | `current_v1_detonation.png`, `current_v1_side.png` |
| V2 | **Cut off by walls and floors.** The sprite is depth-tested as a flat quad: against a wall a vertical cut runs through it; on the floor the bottom half disappears. | side frames 24–32; ground frames 12–16 |
| V3 | **Misplaced and mis-sized boundary.** The sprite is offset 0.2 R from the surface and drawn at 95 % R with a pixel-art edge, so the visible extent does not match the damage sphere centred on the impact. | `FireballVfx` 90–91 |
| V4 | **Too short and too uniform.** 20 ticks, eight frames, no temperature change, no break-up into smoke; a large flat flash particle (size 6.5) instead of controlled light. | `FireballBlastParticle` |
| V5 | **No light.** No emissive glow, nothing lights the surroundings at night; full-bright alpha-blended textures look pasted on. | night frames |
| V6 | **Aftermath dominated by vanilla fire blocks.** The real ignition (gameplay) is what the eye reads after 1 s; the cosmetic aftermath is thin. Fire blocks are *real* damage sources, so the design must keep cosmetic embers visually distinct from them. | detonation frames 32–60 |
| V7 | **Cast burst on first sighting.** A client that starts tracking the fireball mid-flight plays the cast ignition in mid-air and grows the projectile from that point. | entity line 107, renderer `traveled` |
| V8 | **No screen response, generic sound.** No camera shake or flash for nearby players; the vanilla explosion sound only. | code |
| V9 | **Projectile reads as a small comet** (big round head + tail). It matches the accepted Test G reference, but the D&D text and the identity goal favour a smaller, hotter *bead* with a sharp streak. | renderer |

What works and is kept: the true impact point, the client-safe emitter pattern, the restraint near the caster's view
(no trail within 2.5 blocks), and the clean server/client split.

---

## 6. Proposed Fireball V2 visual design [Proposal]

### 6.1 Identity
*"A bead of white heat that streaks out and **blooms** into a sphere of fire exactly the size of the danger."*

| | Fireball V2 | Heat Vision V2 | Fire Bolt | Eldritch Blast (future) |
|---|---|---|---|---|
| form | point bead + short streak → 6-block sphere | continuous twin beams | single small comet, no area | force beams/bolts |
| palette | white-hot → gold → orange → deep red → soot | white core, orange body, red rim | orange/yellow | violet/black (to design) |
| signature | the *blossom* and the ring on the ground | eyes, channelled | quick cantrip | crackling force |

Palette (linear-ish sRGB): white-hot `#FFF7DA`, gold `#FFC23B`, orange `#FF7A1A`, deep red `#B3240D`, soot
`#2A1A14`. Temperature drives colour over time; nothing is pure saturated red at full brightness. Accessibility: the
danger area is communicated by **shape** (sphere + ring), not by colour alone.

### 6.2 A — Casting and projectile (mechanics unchanged: 1.2 b/t, same hit rules)
| element | design | technique |
|---|---|---|
| cast ignition (0–0.15 s) | small white-gold flash in front of the casting hand (no arm animation), 6–10 sparks thrown forwards, no smoke | particles + one emissive point; client sees it **only** if it saw the cast (fix V7) |
| bead | 0.3-block white-hot core with a gold halo, slight flicker | camera-facing quad, custom shader, emissive contribution |
| streak / tail | 3–3.5-block ribbon behind the bead: white core → gold → orange → red, flowing noise *away* from the bead, tapering; min/max on-screen width and near-camera fade | **Beam primitive** (Heat Vision V2 ribbon geometry generalised with a palette parameter) |
| embers | 2–3 per tick, shed behind the bead, cooling gold → red, drifting up, ~0.6 s life | existing Firebolt ember/wisp particles, budgeted |
| glow | bead + first metre of the tail into the Emissive Rendering Layer, capped per projectile | `EmissiveSource` |
| sound | unchanged (`FIRECHARGE_USE`); a dedicated whoosh is decision D8 | — |

The faster "flash" of the D&D text is a **mechanical** change (speed) and is not proposed here; decision D2 records
it. Visually, a smaller and brighter bead with a sharp streak already reads as a streak at 24 blocks/s.

### 6.3 B — Explosion (main priority)
Centre: **the server's damage centre** (the explode packet position) — never offset. Radius: `BLAST_RADIUS` (6).

| stage | time (20 tps) | look | technique |
|---|---|---|---|
| E0 point flash | t 0–2 (0–0.1 s) | small white-gold flash at the centre, ≈ 1.5 blocks; capped screen flash request | emissive point + **Screen FX** flash (capped) |
| E1 blossom | t 0–6 (0–0.3 s) | shell bursts from 0.18 R to 1.0 R with cubic ease-out; hot white-gold core inside | **Explosion Shell primitive** (icosphere, fire shader) + emissive core |
| E2 peak | t 6–12 (0.3–0.6 s) | full sphere: rolling turbulence moving outward, **hot ragged rim at R** = the damage boundary; ground ring at full brightness | shell + ground ring + emissive rim |
| E3 cooling / break-up | t 12–24 (0.6–1.2 s) | temperature falls (gold → orange → red → soot), noise erosion opens the shell into flame tongues, slight overshoot to 1.06 R; core gone; emissive decays to 0 by t 20 | shell parameters animated by the envelope |
| A1 aftermath | t 20–80 (1–4 s) | shell gone by t 30; thin smoke puffs rise and disperse; a few embers drift and die; **no orange glow after t 24** | particles (budgeted) |

Surface handling (no "always flat terrain" assumption):
* **Ground:** the shell is clipped by terrain depth, so it reads as a dome; the **ground ring** sits where the sphere
  meets the ground (radius √(R² − h²) for a centre h above the ground) and a short scorch darkening fades inside it.
* **Wall / ceiling:** the shell is centred on the impact (0.25 blocks in front of the face) and clipped by the wall,
  so a hemisphere bulges out of it; the ring appears on the floor only if the sphere reaches it.
* **Air burst** (entity hit high up): a full sphere, no ring.
* **Caster or camera inside the sphere:** the shell is drawn back-faces-only with a camera-distance fade so the screen
  is tinted, not filled with full-brightness fire; the flash is capped by Screen FX.
* **Water / underwater:** shorter shell, steam-coloured aftermath — **[Future]** detail, not in V2 scope unless
  approved.

The ring is the honest "danger readout": it is computed from the same centre and radius as the damage, so it cannot
disagree with the gameplay sphere (cover rules aside — M7).

### 6.4 C — Aftermath
* Smoke: 12–20 `fireball_smoke` puffs over 3 s, rising 1–3 blocks, growing and thinning, soot-grey.
* Embers: ≤ 30 at t 24, decaying to 0 by t 80; tiny and dim (never flame-coloured masses).
* Particle cleanup: all cosmetic particles of an explosion end by t 80 (4 s); the emitter ends at t 80 and its
  counters return to 0 (testable like today).
* **Real fire blocks** from ignition are untouched gameplay and stay visually vanilla; the cosmetic aftermath avoids
  flame-like shapes near the ground after t 24 so it cannot be mistaken for burning terrain.

---

## 7. Animation storyboard and visual references

**Original procedural concept frames** were rendered for this report by
`Context/Tools/vfx-fireball-v2-design/render_storyboard.py` (a small numpy ray-marcher written for this task: block
floor/wall, blocky stand-in figures, the bead and tail, a ray-marched noise shell with the §6.1 temperature ramp, a
bloom imitating the Emissive Rendering Layer). **They are concept mock-ups, not in-game frames and not final art**;
every frame is stamped so. No external images, games or mods were used. The image-generation workflow used for
earlier reference boards (ChatGPT) is not available to me, so these were produced procedurally instead.

| file (bundle `concept/`) | reference for |
|---|---|
| `01_casting_and_launch.png` | casting and projectile launch |
| `02_projectile_trail.png` | projectile trail in flight |
| `03_early_explosion.png` | early explosion (t 2: flash + blossom) |
| `04_maximum_explosion.png` | maximum explosion (t 8) with ground ring; inside vs outside figures |
| `05_dissipation_aftermath.png` | dissipation and aftermath (t 40) |
| `06_wall_impact_peak.png` | wall impact: hemisphere centred on the impact |
| `07_air_burst_peak.png` | air burst: full sphere, no ring |
| `08_night_peak.png` | night readability with emissive glow |
| `09_explosion_timeline.png` | 12-frame timeline, t 0 → t 70 |

**Intended final visuals vs placeholders in the mock-ups:**
* Final intent: timing, palette, shell silhouette and rim, ring placement, surface clipping, aftermath restraint.
* Placeholder: the smooth (non-pixel) noise look — the in-game shader should quantise the noise to a Minecraft-scale
  texel grid (e.g. 1/16-block steps on the shell) so it matches Totality's pixel-art language; the stand-in figures
  and terrain; the bloom (the real layer's bloom); smoke drawn as discs (real smoke = particles).

Written storyboard (per stage: shape · colour · motion · particles · transition):
1. **Cast** (0–0.15 s): point flash at the hand · white-gold · instant on, 3-tick fade · 6–10 sparks forward ·
   bead appears 0.5 blocks ahead at full speed.
2. **Flight** (≤ 5 s): bead + 3.5-block ribbon · white → gold → orange → red along the tail · noise flows backwards
   at ~2× flight speed · 2–3 embers/tick · ends on impact (bead vanishes the same tick).
3. **Flash** (t 0–2): small sphere of light · white-gold · expands 0.5 → 1.5 blocks, fades · none · overlaps blossom.
4. **Blossom** (t 0–6): shell 0.18 R → R · white core, gold body · cubic ease-out, turbulence rolling outward · 20–30
   sparks flung to the rim · ring starts at t 2.
5. **Peak** (t 6–12): full sphere · gold/orange body, hot rim · slow outward roll · fragments, wisps at the rim ·
   temperature starts falling at t 4.
6. **Break-up** (t 12–24): ragged shell · orange → red → soot · erosion opens holes, 6 % overshoot · first smoke at
   t 18 · ring fades out by t 24.
7. **Aftermath** (t 20–80): smoke puffs and embers · grey, dim ember dots · rise and disperse · ≤ 30 embers → 0 ·
   everything gone by 4 s.

---

## 8. Shared Screen FX Service — architecture [Proposal; not implemented]

### 8.1 Goals and rules
* Reusable client infrastructure for **camera shake**, **screen flash** and optional **impact frames**, usable by any
  effect (Fireball first; Ground Slam, Lightning Bolt, Meteor Swarm later).
* **Never sums** contributions without bound: 20 simultaneous Fireballs ≈ one Fireball (+ a small bounded bonus).
* Deterministic, time-based (never frame-count based), pause-aware, frame-rate independent.
* Purely cosmetic: no gameplay effect, no server authority needed; each client computes its own response.

### 8.2 Interface sketch
```java
// client/vfx/screen/ScreenFx.java (proposed)
public final class ScreenFx {
    public static ScreenFxHandle request(ScreenFxRequest request);   // render/client thread
    public static void cancel(ScreenFxHandle handle);
}

public record ScreenFxRequest(
        Channel channel,            // SHAKE, FLASH, IMPACT_FRAME
        Vec3 origin,                // world position (null = non-spatial, e.g. self-cast)
        float intensity,            // 0..1 at the origin, before attenuation and caps
        Envelope envelope,          // attack / hold / release in seconds (shared Animation envelope)
        Falloff falloff,            // inner radius (full), outer radius (zero), curve
        Priority priority,          // AMBIENT < NORMAL < MAJOR < CINEMATIC
        Audience audience,          // EVERYONE_IN_RANGE, LOCAL_CASTER_ONLY, LOCAL_TARGET_ONLY
        @Nullable UUID caster,      // for audience checks
        @Nullable Object owner) {}  // cancel-by-owner (effect instance)
```
Fireball's use: on detonation, `SHAKE` (intensity 0.6, envelope 0.02/0.08/0.45 s, falloff 4 → 32 blocks, NORMAL),
`FLASH` (0.35, 0.0/0.04/0.18 s, falloff 6 → 40 blocks, NORMAL). Impact frames: none for Fireball by default.

### 8.3 Resolution per frame
```
for each channel:
    live = requests whose envelope is active and audience matches the local player
    v_i  = intensity_i × envelope_i(t) × falloff_i(distance to camera) × occlusionFactor_i (optional, flash only)
    sort v descending
    merged = v_0 + 0.25 × v_1 + 0.10 × v_2        // diminishing, at most 1.35 × the strongest
    merged = min(merged, channelCap) × accessibilityScale
```
* **Priority:** a higher priority request suppresses lower ones on the same channel only while it is active
  (cinematic shake replaces gameplay shake, not adds to it).
* **Shake output:** small camera rotation (yaw/pitch/roll) + translation from a smooth noise function of time,
  amplitude = merged × `MAX_SHAKE` (proposed ≤ 0.8° and ≤ 0.06 blocks), frequency 12–18 Hz decaying.
* **Flash output:** full-screen additive tint (warm colour from the requests' colours, weighted), alpha = merged ×
  `MAX_FLASH` (proposed ≤ 0.35).
* **Flash budget (anti-strobe):** a token bucket limits flash *energy*: at most ~0.5 s of flash at full cap in any
  2 s window; new flashes within 150 ms of a peak may raise but not re-trigger the peak. Twenty Fireballs cannot
  produce a long or strobing flash.
* **Global safety:** hard caps per channel, no more than 3 flash peaks per second, impact frames ≤ 1 per 2 s.

### 8.4 Accessibility
| setting | effect |
|---|---|
| vanilla **Distortion Effects** (`screenEffectScale`) | multiplies shake; 0 = no shake (Totality already uses it as "reduced motion" for the Camera) |
| vanilla **Hide Lightning Flashes** (`hideLightningFlash`) | disables full-screen flashes; replaced by a faint edge vignette ≤ 0.1 |
| `totality-vfx.properties`: `screenfx.shake`, `screenfx.flash` (0–1), `screenfx.impactFrames` (default **off**) | per-player scaling; Experiment-1-style properties until a settings screen exists |
| reduced motion (Distortion Effects = 0) | also disables impact frames and camera FOV punches |

### 8.5 Multiplayer and local-player restrictions
* Spatial requests come from already-replicated events (Fireball: the explode packet → the detonation emitter), so
  **every nearby player** gets distance-attenuated shake/flash with no new packets.
* **Impact frames** (high-contrast freeze frames, a photosensitivity risk) are allowed only for `LOCAL_CASTER_ONLY` /
  `LOCAL_TARGET_ONLY`, never for bystanders, and are off by default. Fireball's explode packet does not say who the
  caster is; if impact frames are wanted for Fireball later, a small payload is needed (decision D6).
* Spectators / camera entities: requests evaluate against the **render camera**, not the player entity.

### 8.6 Integration
* Shake: a client mixin on the camera/bob path (the audit's `GameRenderer.bobHurt` pattern) applying the merged
  rotation to the view pose; flash: a HUD layer drawn before the GUI (Fabric `HudElementRegistry`) or a full-screen
  pass after the Emissive Rendering Layer. Both are plain Blaze3D/Fabric APIs; verified on OpenGL and Vulkan before
  acceptance.
* Owned by the future VFX API (`client/vfx/screen`); the existing `PowerAttackFlash` could migrate onto it later
  (**[Future]**, not part of Fireball V2).

---

## 9. Rendering architecture, performance and budgets

### 9.1 Technique per component
| component | technique | reusable? |
|---|---|---|
| bead | camera-facing quad, procedural shader, emissive | Impact/Point primitive (shared) |
| tail | segmented camera-facing ribbon, min/max angular width, near fade, flowing noise | **Beam primitive** (from Heat Vision V2) — shared |
| embers, sparks, smoke, fragments | existing Minecraft particles (`SingleQuadParticle`), budgeted | shared particle budget |
| explosion shell | **icosphere** (≈ 320 triangles far, ≈ 1280 near) drawn additively with a fire shader (3D noise erosion, temperature ramp, fresnel rim), depth-tested reversed-Z against the world, no depth write | **Explosion Shell primitive** — shared |
| inner core / flash | emissive point + small sphere | shared |
| ground ring | flat ring quad at the ground intersection with a ring shader (V2); projected terrain decal later | precursor of **Terrain decals** |
| glow | Emissive Rendering Layer sources (bead, core, rim, ring), per-instance cap | existing layer |
| shake / flash | Shared Screen FX Service | shared |
| timings | Animation envelope (time-based, seeded) | shared |
| Fireball-specific | palette, timings, sizes, particle recipe, sounds | Fireball only |

All Fireball explosions of a frame draw as **one shell draw + one ring draw** (instance list, like Heat Vision's
"N beams, 2 draws"). Shader parameters per instance (age, seed, radius, temperature) travel in vertex attributes,
because 26.2 `RenderPipeline`s take no per-draw custom uniforms without a UBO (audit §4; Heat Vision uses vanilla
`GameTime`). Shader noise is procedural (no textures needed) and quantised to a pixel grid for the Minecraft look.

### 9.2 Distance quality and budgets
| distance to camera | shell | particles | emissive |
|---|---|---|---|
| < 24 blocks | 1280-tri shell, full particles | 100 % | full |
| 24–48 | 320-tri shell | 50 % | full |
| 48–64 | 320-tri shell, no fragments | 25 % | rim only |
| > 64 | not received (explode packet range) | — | — |

* **Particle budget:** a shared counter caps Fireball cosmetic particles (proposed 600 alive); each new explosion
  scales its recipe by `min(1, remaining / recipe)`; the shell and ring are never culled by the budget (they carry
  the gameplay readout).
* **Emissive:** per-explosion cap now; the global emissive budget is the deferred VFX API item (decision D5 proposes a
  minimal global clamp in this experiment).
* **Overdraw:** the dominant risk is a full-screen shell when the camera is inside or close to it → back-face-only +
  fade, and the max-merge of flashes.

### 9.3 Proposed benchmark (implementation phase — **no numbers are claimed now**)
Run on OpenGL and Vulkan, 1920×1080 (window-clamped), same scene for V1 and V2 in one run (A/B like Heat Vision):
1. **One Fireball**: cast → flight → wall impact, ground impact, air burst; first and third person.
2. **20 simultaneous explosions** spread 10–40 blocks in view (plus 20 behind the camera, to test culling).
3. **20 casters × 1 Fireball in flight** (trail + beads).
4. Camera **inside** the blast radius.

Measure: `TimerQuery` GPU time of shell + ring + bead/tail draws; Emissive Rendering Layer GPU time and quad count;
live particle count and peak; render-thread CPU time for geometry building (System.nanoTime around the build);
vertex buffer bytes; frame time context. Check: shake/flash merged values for case 2 never exceed 1.35× a single
explosion; everything returns to 0 after 4 s. Pass targets to be agreed at the review (decision D10), e.g.
"case 2 total Fireball GPU time well under 1 ms on the RX 6600".

---

## 10. Technical risks and compatibility questions
1. **Depth-aware soft intersections:** sampling the scene depth in a custom pipeline is unconfirmed in 26.2 (VFX
   plan, Terrain Decals). V2 works without it (hard depth clipping of the shell is acceptable and shown in the
   mock-ups); soft edges are optional.
2. **Per-instance parameters** without custom uniforms → vertex attributes (proven pattern) or a UBO (unproven).
3. **Camera inside the shell** → overdraw and blinding; mitigated as in §6.3, must be measured.
4. **Fabulous graphics / translucency targets**: additive shells drawn at `BEFORE_TRANSLUCENT_TERRAIN` vs the
   translucent target split; water and glass in front won't occlude glow (Experiment 1 limitation).
5. **Shader packs (Iris), Sodium**: untested; custom pipelines may be bypassed by shader packs.
6. **Explode packet range (64)**: players further away see no explosion; acceptable now, revisit with Meteor Swarm.
7. **Screen FX mixin placement** must coexist with Camera & Gallery's camera mixins and the Phone camera mode
   (photographs should not shake; the camera viewfinder should suppress shake).
8. **Accessibility defaults**: flash and impact-frame defaults need Stefan's decision; photosensitivity is the
   priority over spectacle.
9. **Vulkan parity**: every new pipeline verified on both backends (permanent lesson 3).
10. **Sound assets**: a "low roar" needs original sound design (no copied audio).

---

## 11. Targeting Preview API — separate future feature [Future]
Inspired by Baldur's Gate 3's AoE preview; **not part of Fireball V2**.
* **What:** while aiming a spell, show the projected area (Fireball: the 6-block sphere as a terrain ring/decal + a
  faint shell outline) and highlight creatures that would be affected.
* **Authority:** the preview must be computed by the **same common code** the server uses: the straight-line
  projectile path from the eye along the look vector → first block/entity hit → impact point (`impactPoint`) →
  membership rule (`distanceTo ≤ R`, plus cover if M7 is ever adopted). The server stays authoritative; the preview
  is advisory and labelled as a prediction (moving targets).
* **Techniques:** Terrain decals (projected ring/sphere footprint), an outline shell (Explosion Shell primitive in
  "preview" mode), entity highlight (outline or nameplate tint, not colour-only), all on the Emissive Rendering
  Layer at low intensity.
* **Open questions:** aim/hold-to-preview input; how it interacts with the Spell Radial; whether Fireball gains
  chosen-point targeting (M1–M3) — a gameplay decision, separate from the preview.

---

## 12. Planned implementation phases (after approval)
| phase | content | verification |
|---|---|---|
| B1 | Shared Screen FX Service V1 (shake + flash channels, merging, caps, budget, accessibility), dev command, no consumer yet | unit tests (merge/caps/budget), capture scene with 1/20 requests, both backends |
| B2 | Explosion Shell primitive + ground ring + Animation envelope; Fireball detonation driver replaces `FireballBlastParticle` | geometry/envelope unit tests, capture: ground/wall/air/inside, OpenGL+Vulkan |
| B3 | Projectile V2: Beam primitive generalised (palette), bead, embers; fix V7 | capture: flight, other player, first person |
| B4 | Emissive integration + per-instance caps (+ D5 global clamp if approved); aftermath recipe; particle budget; LOD | capture night/day; emitter counters back to 0 |
| B5 | Benchmark §9.3 (A/B vs V1), regression scenes 58/64/65/66/67, `FireballVerification`, report + bundle | measured numbers only |

Mechanics stay untouched in every phase unless a decision below approves a change as a separate task.

## 13. Decisions requiring approval
| # | decision | recommendation |
|---|---|---|
| D1 | Approve the visual direction (bead + streak, blossoming shell with rim, ground ring, short cosmetic aftermath, palette, timings) | approve, or mark changes on the concept frames |
| D2 | Projectile speed: keep 1.2 b/t, or a faster "streak" (gameplay change) | keep for V2; revisit with Targeting Preview |
| D3 | Pixel-quantised shader look vs smooth fire | pixel-quantised (Totality art language) |
| D4 | Keep the accepted Test G projectile textures as the V2 head, or the new bead + ribbon | bead + ribbon (identity, D&D "streak") |
| D5 | Minimal global emissive clamp inside Fireball V2, or wait for the VFX API | minimal clamp (20 explosions otherwise saturate) |
| D6 | A small Fireball FX payload (caster id, seed) instead of only the explode packet | not needed for V2; needed only for caster-only impact frames |
| D7 | Screen FX defaults: shake on (scaled by Distortion Effects), flash on (honours Hide Lightning Flashes), impact frames **off** | as listed |
| D8 | Dedicated original sounds (cast whoosh, low-roar detonation) | yes, as a separate asset task |
| D9 | Mechanical follow-ups M4 (one damage roll), M5 (round down), M7 (cover), M8 (flammability), M9 (upcasting) — separate gameplay tasks? | schedule separately; not in Fireball V2 |
| D10 | Benchmark pass targets for §9.3 | agree before B5 |

## 14. Roadmap update
`TOTALITY_VFX_EXPERIMENT_PLAN.md` § Roadmap was corrected in this task: 1. Fireball V2 (Phase A done), 2. Eldritch
Blast V2 (Baldur's Gate 3 video reference), 3. Magic Missile V2, 4. Lightning Bolt V2; further candidates; Meteor
Swarm as the eventual integration experiment; finish the VFX phase before the 26.3 migration. Targeting Preview API
is listed as a separate future feature.

## 15. Decisions recorded after the Phase A review (2026-10-02)

| # | decision |
|---|---|
| D1 | **Visual direction:** the general architecture is approved; the concept's explosion appearance is **not**. Criticism: too flat, essentially an expanding orange flame dome. B1–B2 implement a first working version and stop for visual approval. The concept frames are preliminary references, not a target. |
| D2 | **Projectile speed:** keep 1.2 blocks/tick. No gameplay change. |
| D3 | **Rendering style:** Minecraft-compatible pixel-quantised shading that keeps convincing flame movement and internal detail; not excessively blocky, not large flat-colour polygons. |
| D4 | **Projectile:** B3 will use the white-hot bead and short ribbon; the existing projectile stays unchanged during B1–B2. |
| D5 | **Global emissive brightness:** a minimal, configurable global limit is approved for **B4**, without making Heat Vision or other existing effects noticeably dimmer. Not implemented in B1–B2; B1–B2 keep a per-explosion cap and leave room for it. |
| D6 | **Networking:** reuse the existing explosion packet; add networking only for a genuine need, never for cosmetic convenience. |
| D7 | **Screen FX defaults:** shake on but subtle; flash on but controlled; Minecraft's accessibility options respected; impact frames off by default. |
| D8 | **Sound:** original cast and explosion sounds approved as a separate future asset task; the prototype does not wait for audio. |
| D9 | **Mechanical differences** (M1–M12) are separate gameplay tasks. Fireball's damage, speed, cooldown, saves, ignition, range and collision stay unchanged in this experiment. |
| D10 | **Performance:** collect initial measurements in the prototype; numerical targets are set after the first implementation is reviewed. No invented numbers. |

**Permanent VFX rule (2026-10-02):** visual effects must never change Minecraft's actual world time, weather or
global gameplay conditions purely for cinematic atmosphere. Sky darkening, flashes, colour grading and similar effects
are cosmetic, properly scoped and reversible; real gameplay effects belong to the authoritative gameplay systems. This
applies to Fireball and all future spells, especially Dark Star and Meteor Swarm. (Also recorded in the VFX plan.)

## 16. Implementation record (B1–B5, 2026-10-02) — differences from this design

Fireball V2 is implemented (B1–B5) and approved (final review 2026-10-02, decisions F1–F5); see
`TOTALITY_VFX_FIREBALL_V2_FINAL_REPORT.md`. Where
the implementation differs from §6–§9, the reason is recorded here.

| design (§6–§9) | implemented | why |
|---|---|---|
| explosion as an icosphere shell with a fresnel rim | ~80 layered, independently animated fire volumes + tongues + ground flames + ignition core, one draw (B2, B2.1 approved) | the shell concept read as a flat orange dome (D1); volumes give a real 3D, irregular mass |
| ground ring decal at the sphere's footprint | overlapping ground flames running out to the footprint | reads as fire, not as a marker; decals stay a future VFX API item |
| shell overshoot to 1.06 R | the outer flame front sits at the 6-block boundary; only cooled soot may drift past (B2.1) | never suggest a larger danger area than the damage |
| bead + Beam primitive with a palette parameter | bead + tapering streak on the shared `RibbonGeometry` (Heat Vision's ribbon generalised with a width profile); its own shader | the streak tapers and travels with the bead; Heat Vision keeps its shader and identity |
| cast flash = an emissive point and a flash particle | the bead flares white-gold as it emerges; sparks and embers only | a flash particle 2.5 blocks ahead read as a large flat square in first person |
| embers 2–3 per tick in flight | 2–3 per tick, budgeted, Fireball's own ember particle with a near-camera size limit | as designed, plus the budget |
| per-instance emissive cap; minimal global clamp (D5) | per-effect budget groups (`fireball` limited to 2.5 explosion-equivalents) plus one global cap (`glow.globalLimit`, default 6) | twenty overlapping Fireballs no longer saturate; Heat Vision is never dimmed by Fireballs |
| particle budget 600 alive, distance tiers 24/48/64 | 400 alive (recipes untouched below 200), tiers 24/48 | measured peaks stay well below; simpler |
| aftermath: smoke 12–20 puffs over 3 s, embers to 4 s | smoke ~16 puffs between 0.7 and 1.5 s from the cooling upper blast (never within 3.5 blocks of the camera), small dim embers to 2.8 s; every cosmetic particle gone by 4 s | smoke appears as the fire cools instead of over the hot fire |
| shell LOD by distance (1280/320 triangles) | not needed: the turbulence is a precomputed noise texture (B5) | one explosion 0.05 ms, twenty 0.54 ms GPU on the RX 6600 |
| a mid-flight client should not play the cast burst (V7) | the burst plays only when the caster is known to the client and the fireball is within 4.5 blocks of its eyes | fixed without networking (D6) |

Unchanged as designed: the server's explosion centre and radius, gameplay (D2, D9), the explode packet as the only
trigger (D6), Screen FX defaults (D7), the pixel-quantised look (D3).
