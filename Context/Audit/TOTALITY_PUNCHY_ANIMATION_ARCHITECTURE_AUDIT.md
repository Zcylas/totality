# TOTALITY — PUNCHY! ANIMATION ARCHITECTURE AUDIT

**Date:** 2026-08-01
**Type:** Read-only, license-gated external-mod audit. No Totality production code, tests, or configuration was modified. No commit, stage, or push occurred.
**Branch:** `feature/general-resource-api`
**Committed HEAD (unchanged throughout):** `76e88ed9a0b8c7f2b1bb8a27ed4526ff30103b23` ("Disable vanilla automatic health regeneration")

---

## 1. Executive Summary

This audit set out to examine the Punchy! Fabric mod's animation architecture for concepts transferable to a future Totality Animation API. Pre-flight and safe archive-level metadata inspection (file/directory listing, `fabric.mod.json`, `META-INF/MANIFEST.MF`, `pack.mcmeta`) completed successfully and are reported in full below. **The audit then found an explicit, unambiguous license restriction and stopped before any decompilation, disassembly, or class/resource content inspection, exactly as this task's own instructions required.**

The archive root contains `LICENSE_Punchy` — a custom "All Rights Reserved" license (also declared as `"license": "ARR"` in `fabric.mod.json`) whose Restrictions section explicitly forbids, without the author's prior written permission: copying/reproducing/redistributing the mod in whole or in part; reuploading it anywhere; **modifying, adapting, reverse engineering, or creating derivative works**; commercial exploitation; and claiming any part of it as one's own. Permitted use is limited to "personal, non-commercial purposes only." This is precisely the condition under which this task's own PRE-FLIGHT and PART 2 instructions require stopping before full decompilation.

**Consequence:** every part of this audit that depends on decompiled Java source, `javap` bytecode disassembly, mixin-configuration content, or animation-resource file content (Parts 3–14, 18–20 of the requested structure, and the class/resource content portions of Parts 4 and 27) was **not performed**. This report, the evidence index, and the class/resource inventory instead record exactly what was established from safe, pre-decompilation, archive-level metadata alone, and clearly mark every unestablished section as such rather than inferring or fabricating findings. The Totality-side sections (current-state comparison, recommended Animation API boundaries, vertical-slice candidate, targeting-checkmark note) are still delivered in full, since they are grounded in Totality's own unrestricted codebase and independent architectural reasoning — not in any Punchy!-derived evidence.

**No Punchy! risk to Totality exists from this audit having occurred:** the JAR was never executed, never added as a dependency, never loaded into this JVM, and no entrypoint was invoked. Only ZIP-level metadata (via Python's standard `zipfile` module, read-only) and four small, purely declarative text files (`fabric.mod.json`, `META-INF/MANIFEST.MF`, `pack.mcmeta`, `LICENSE_Punchy`) were read.

---

## 2. Repository Checkpoint

| Check | Value |
|---|---|
| Branch | `feature/general-resource-api` (unchanged throughout) |
| HEAD | `76e88ed9a0b8c7f2b1bb8a27ed4526ff30103b23` (unchanged throughout) |
| HEAD subject | "Disable vanilla automatic health regeneration" |
| `origin/feature/general-resource-api` | 0 ahead / 0 behind at start and end |
| Staged changes | None at start; none at end |

Pre-flight `git status --short` recorded 69 pre-existing entries (22 modified generated/build files, 47 untracked review bundles/screenshots/logs/caches/audit docs — one more than the "~68" estimate in the task prompt, fully explained by the just-completed natural-regeneration task's own review bundle, `TOTALITY_NATURAL_FOOD_REGENERATION_CORRECTION_IMPLEMENTATION_BUNDLE.zip`, remaining untracked exactly as intended after that task's commit). The natural-regeneration task's own production/test/report files were confirmed already committed (none appeared in `git status`), and the Resource API path (`src/main/java/zcylas/totality/api/rpg/resources/`) showed zero dirtiness. All entries are confirmed unchanged in §Final Verification below (in the accompanying final response).

---

## 3. JAR Identification

Single, unambiguous candidate found under `Inspiration Mods/` by filename — no other file in that tree contains "punchy" (case-insensitive) in its name; the only other JAR of note, `Terralith_26.2_v2.6.4.jar`, is a different, unrelated mod. No ambiguity to report.

- **Repository-relative path:** `Inspiration Mods/punchy-2.6.2-fabric-26.2.jar`
- **Filename:** `punchy-2.6.2-fabric-26.2.jar`
- Confirmed **not tracked by Git** (`git ls-files` empty) and **explicitly ignored** (`git check-ignore` matched `.gitignore:42:/Inspiration Mods/`).

---

## 4. Integrity and Hashes

| Field | Value |
|---|---|
| Size | 1,972,363 bytes |
| Last modified (local filesystem) | 2026-08-01 19:02:10 +0200 |
| SHA-256 | `aa6b213eb21ff050ba567bfca421cf102777dabde3fffe79fc973987a197d4a3` |
| SHA-1 | `cb40908bc5f80b5cdc7222a6c2fe0ff4650a19dd` |
| Archive integrity (`ZipFile.testzip()`) | **Pass** — `None` returned (no corrupt member) |
| Total archive entries | **487** |
| Duplicate entry paths | **0** |
| Path-traversal entries (`..`, absolute, drive-letter prefixed) | **0** |
| Nested `.jar` entries | **0** |
| Native libraries / executable scripts (`.dll`/`.so`/`.dylib`/`.exe`/`.sh`/`.bat`/`.ps1`/`.cmd`) | **0** |

Inspection performed entirely via Python's standard-library `zipfile` module in read-only mode — the archive was never extracted to disk in bulk, never executed, and no entry was opened except the four named in §5.

---

## 5. Metadata

Top-level archive structure (path listing only — obtained via `ZipFile.namelist()`, no content read beyond the four files below): `LICENSE_Punchy`, `META-INF/`, `assets/`, `fabric.mod.json`, `pack.mcmeta`, `punchy/`, `punchy.compat.mixins.json`, `punchy.fabric.mixins.json`, `punchy.mixins.json`, `resourcepacks/`.

### 5.1 `fabric.mod.json` (full content, reproduced — this is a plain declarative metadata file, not "code")

| Field | Value |
|---|---|
| Display name | Punchy |
| Mod ID | `punchy` |
| Version | `2.6.2` |
| Description | "First-person animations. Special thanks to Godku for the support." |
| Declared Minecraft version | `26.2` (exact, not a range) |
| Loader | Fabric (`fabricloader >= 0.19.3`) — confirmed by evidence, not assumed from Totality's own toolchain |
| Java requirement | `>= 25` |
| `fabric-api` dependency | `*` (any version) |
| Environment | `"client"` — **client-only mod**, no server component declared |
| Entrypoints | `client`: `punchy.fabric.PunchyFabricClient`; `modmenu`: `punchy.client.config.PunchyModMenu` |
| Mixin config files declared | `punchy.mixins.json`, `punchy.fabric.mixins.json`, `punchy.compat.mixins.json` (filenames only — **contents not inspected**, see §6) |
| Declared license | `"ARR"` (All Rights Reserved) |
| Homepage | `https://modrinth.com/mod/punchy-fpa` (recorded per task instruction; **not browsed**, per this task's local-only, no-internet scope) |
| Sources | `https://github.com/punchy-mod/punchy-wiki/wiki` (recorded, **not browsed** — also notably a wiki URL, not a source-code repository URL) |
| Author | "Punchy" |

### 5.2 `META-INF/MANIFEST.MF` (full content, reproduced — standard JAR/build-tool boilerplate)

```
Manifest-Version: 1.0
Specification-Title: Punchy
Specification-Vendor: Punchy
Implementation-Title: fabric
Implementation-Vendor: Punchy
Built-On-Minecraft: 26.2
Fabric-Mapping-Namespace: official
Fabric-Gradle-Version: 9.5.0
Fabric-Loom-Version: 1.17.13
Fabric-Mixin-Compile-Extensions-Version: 0.6.0
Fabric-Minecraft-Version: 26.2
Fabric-Tiny-Remapper-Version: 0.14.0
Fabric-Loader-Version: 0.19.3
Fabric-Mixin-Version: 0.17.3+mixin.0.8.7
Fabric-Mixin-Group: net.fabricmc
```

Notable: `Fabric-Mapping-Namespace: official` confirms this build was remapped to official Mojang mappings (the same mapping set Totality itself uses), and `Fabric-Mixin-Version: 0.17.3+mixin.0.8.7` matches the exact Mixin version already present in Totality's own dependency tree (confirmed in this session's own dedicated-server logs from the natural-regeneration task: `"SpongePowered MIXIN Subsystem Version=0.8.7"`). No `Specification-Version`/`Implementation-Version` values are populated (blank) — a minor, inert build-metadata gap, not evidence of anything architectural.

### 5.3 `pack.mcmeta` (full content, reproduced — standard resource-pack boilerplate)

```json
{
    "pack": {
        "description": "Punchy",
        "pack_format": 88,
        "min_format": 88,
        "max_format": 88
    }
}
```

Confirms the JAR bundles a resource pack at `pack_format` 88 (consistent with MC 26.2), i.e. it ships assets (textures/animations/models/sounds) directly rather than only code — expected for a first-person-animation mod, and consistent with the top-level `assets/`/`resourcepacks/` directories observed. **No file inside `assets/`/`resourcepacks/`/`punchy/` was opened, listed recursively, or read.**

### 5.4 Not established (blocked by the license gate, §6)

Per this task's own Part 1 checklist, the following require either opening the three mixin-config JSON files (which enumerate internal mixin class names — implementation detail) or inspecting `.class`/resource file content, and were therefore **not attempted**: mixins (list of classes), access wideners, required/optional/bundled dependencies beyond the four declared in `fabric.mod.json` above, whether a source archive or sources JAR is embedded, whether debug names/line tables/source-filenames/parameter metadata are present in compiled classes, and any nested-dependency package structure.

---

## 6. License and Audit Boundaries

**Evidence — `LICENSE_Punchy` (archive root), key clauses only, not reproduced in full:**

> "PUNCHY — CUSTOM LICENSE (ALL RIGHTS RESERVED)" ... "Copyright (c) 2026 Dev Punchy Man, creator of Punchy." ... "All rights reserved."
>
> **§1 Permitted Use:** "a limited, non-exclusive, non-transferable, revocable license to: Use Punchy for personal, non-commercial purposes only."
>
> **§2 Restrictions — "You may NOT, without explicit prior written permission from the author":** "Copy, reproduce, or redistribute Punchy in whole or in part"; "Reupload Punchy to any website, platform, or service"; **"Modify, adapt, reverse engineer, or create derivative works"**; "Sell, sublicense, rent, or commercially exploit Punchy"; "Claim Punchy or any part of it as your own."
>
> **§3 Distribution:** official-sources-only; unauthorized distribution "strictly prohibited."
>
> **§4 Ownership:** all rights remain with the author.
>
> **§5 Termination:** automatic on violation; requires deleting all copies.
>
> **§6 Disclaimer:** "as is," no warranty.

**Classification:** **explicit restrictive/proprietary license.** Single license found (no separate asset license, no SPDX identifier — `fabric.mod.json`'s `"license": "ARR"` is a Modrinth/Fabric convention shorthand, not an SPDX identifier since ARR is not an OSI/SPDX-recognized license string). No embedded dependency licenses were inspected (would require opening class/resource content).

**Practical audit implication (not a legal conclusion):** §2's explicit, plain-language prohibition on "reverse engineer[ing]" — combined with the scope covering "all files, assets, code, and associated content" and the "personal, non-commercial... only" permitted-use clause — means any decompilation, disassembly, or systematic architectural reconstruction of Punchy!'s implementation performed for the purpose of this professional/commercial audit falls outside the license's permitted use and squarely inside its explicit restriction. Per this task's own PRE-FLIGHT/PART 2 instructions, this is exactly the condition requiring the audit to **stop before full decompilation** and report only what was established from safe metadata.

**This audit therefore treats all of Punchy!'s code and assets as non-redistributable and not subject to architectural reverse-engineering under this pass:** no code copying occurred, no asset copying occurred, no decompiled output was produced at all (not merely "not bundled" — never generated), and no redistribution of any derived material occurred. Only the four small, plainly declarative metadata files quoted in §5 were read, on the basis that reading a mod's own self-declared loader-compatibility metadata (mod ID, version, dependency requirements, entrypoint class *names*, license declaration) is standard, non-reverse-engineering practice necessary for any basic mod-compatibility or licensing check — not an inspection of "the Product['s]" implementation, code, or assets.

---

## 7. Decompiler and Static-Analysis Method

**Not performed.** No decompiler was invoked. No temporary audit workspace was created (Part 3 of the task, "Set up temp workspace + decompile," was itself skipped once the license gate was found during Part 1). No `.class` file was extracted, disassembled, or analyzed with `javap`. This section is recorded as empty by design, not by omission.

---

## 8. Package and Resource Overview

**Top-level structure only** (path listing via `ZipFile.namelist()`, no recursive listing, no content): `LICENSE_Punchy`, `META-INF/`, `assets/`, `fabric.mod.json`, `pack.mcmeta`, `punchy/`, `punchy.compat.mixins.json`, `punchy.fabric.mixins.json`, `punchy.mixins.json`, `resourcepacks/`.

The presence of a `punchy/` top-level package directory (implying Java classes live under `punchy.*`, consistent with the entrypoint class names `punchy.fabric.PunchyFabricClient`/`punchy.client.config.PunchyModMenu` declared in `fabric.mod.json`) and separate `assets/`/`resourcepacks/` trees is the full extent of what can be said without opening the archive further. No class count, no resource count, no package hierarchy beneath the first level, and no file-type breakdown were established. See the companion class/resource inventory document, which records this same boundary explicitly rather than fabricating an inventory.

---

## 9. Animation Framework Identification

**Not established.** Determining whether Punchy! is a custom animation system or an integration layer over Player Animator / GeckoLib / AzureLib / KosmX / Figura / Satin / etc. requires inspecting `fabric.mod.json`'s dependency block for such a library (only `fabric-api` is declared — **no known third-party animation library appears as a declared Fabric dependency**, which is itself one piece of safe, positive evidence: if Punchy! depends on an external animation library, that dependency is either bundled/shaded inside the JAR rather than declared, or Punchy! is self-contained) plus inspecting imported package names inside compiled classes, which was not performed. **Confidence: low** on any framework conclusion beyond "no third-party animation library is declared as a Fabric-loader dependency."

---

## 10. Punchy!-Owned Animation Architecture

**Not established** — requires decompiled class inspection. Not attempted.

## 11. Entrypoints and Initialization

**Partially established from `fabric.mod.json` alone:** entrypoint class names are `punchy.fabric.PunchyFabricClient` (client entrypoint) and `punchy.client.config.PunchyModMenu` (Mod Menu integration entrypoint) — **names only**, confirming the mod registers with Fabric's client-side entrypoint mechanism and separately integrates with the Mod Menu config-screen convention. No class content, no initialization logic, no method bodies were inspected.

## 12. Input Handling

**Not established.** Not attempted.

## 13. Combat and Punch Flow

**Not established.** Not attempted — this is the single largest gap relative to the task's stated purpose (the mod's namesake feature), and is explicitly and honestly reported as unestablished rather than inferred from the name "Punchy" or the tagline "First-person animations."

## 14. Animation State Representation

**Not established.** Not attempted.

## 15. Controllers and Lifecycle

**Not established.** Not attempted.

## 16. Playback, Timing, and Progress

**Not established.** Not attempted.

## 17. Blending and Layering

**Not established.** Not attempted.

## 18. Priority and Interruption

**Not established.** Not attempted.

## 19. First-Person Rendering

**Not directly established from code.** The mod's own tagline, "First-person animations," and the environment declaration `"environment": "client"` together suggest first-person presentation is the mod's central feature and that it has no server-side component at all — but this is an inference from self-description, not from inspected rendering code. **Confidence: medium** (self-declared purpose), **not verified** against implementation.

## 20. Third-Person Rendering

**Not established.** No evidence either way.

## 21. Remote-Player Rendering

**Not established.** Given `"environment": "client"` (no server component declared at all), remote-player synchronization of any custom animation state is architecturally unlikely for this mod (a purely client-side mod has no server-authoritative channel to broadcast custom state to other clients through), but this is an inference from the environment declaration, not confirmed by inspecting any networking code — none was inspected. **Confidence: low-medium.**

## 22. Held Items and Arm Poses

**Not established.** Not attempted.

## 23. Mixins and Render Hooks

**Not established.** The three mixin-configuration filenames are known (`punchy.mixins.json`, `punchy.fabric.mixins.json`, `punchy.compat.mixins.json`, all declared in `fabric.mod.json`) — the `.compat.` naming strongly suggests a dedicated compatibility-shim mixin set for other mods, a common pattern, but **file contents (target classes, injection points) were not opened**, since doing so would enumerate Punchy!'s internal implementation structure.

## 24. Networking and Synchronization

**Not established.** Combined with `"environment": "client"`, no server-side networking registration is architecturally expected for this mod, but no networking code was inspected to confirm.

## 25. Multiplayer Authority

**Not established** beyond the environment declaration itself (§21).

## 26. Join, Respawn, Dimension, and Cleanup Behavior

**Not established.** Not attempted.

## 27. Animation Resource Format

**Not established.** `assets/` and `resourcepacks/` directories exist (confirmed by top-level path listing only); their internal file formats, naming scheme, and schema were not inspected.

## 28. Compatibility Risks

**Not established with Punchy!-specific evidence.** One inferable, low-confidence architectural risk based purely on the declared metadata: a client-only, first-person-animation mod that hooks player-model/arm rendering is a **generically likely collision point** with any other mod that also mixes into first-person rendering, held-item rendering, or humanoid-model pose methods (a well-known category of Fabric-mod conflict, true of this entire mod category in general, not a Punchy!-specific finding). **Confidence: low**, stated as a category-level architectural risk, not a Punchy!-specific one, since no actual mixin target was inspected.

## 29. Static Performance Concerns

**Not established.** Not attempted — requires class inspection.

## 30. Useful Architectural Concepts

**Not established from Punchy! directly.** No Punchy!-derived architectural concept can be honestly credited without decompiled evidence. See §33–36 (below) for Totality-side recommendations grounded in independent reasoning and Totality's own codebase instead.

## 31. Concepts Totality Should Not Copy

**Not established from Punchy! directly**, for the same reason. One category-level observation *is* safely evidence-backed: Punchy!'s license (§6) makes it **legally unsuitable as a source of copied code or assets under any circumstance**, independent of any architectural judgment — this is not a Punchy!-specific technical flaw, it is the single clearest and most important "should not copy" finding this audit can report, and it is fully evidence-backed by §6.

---

## 32. Totality Current-State Comparison

This section is grounded entirely in Totality's own codebase (unrestricted access) and does not depend on any Punchy!-derived evidence.

**What animation-related infrastructure already exists in Totality today**, confirmed by direct inspection this pass:

- `src/main/java/zcylas/totality/mixin/client/HumanoidModelMixin.java` — mixes into vanilla `HumanoidModel`, shadows `head`/`body`/`leftArm` model parts, manipulates pose directly (imports `net.minecraft.util.Ease`/`Mth`), reads `DualWieldTracker` state. This is direct, hand-written model-part rotation — not a generic animation system.
- `src/main/java/zcylas/totality/mixin/client/ItemInHandRendererMixin.java` and `AvatarRendererMixin.java` — further client-side render-hook mixins for held-item/avatar presentation.
- `src/main/java/zcylas/totality/mixin/client/MinecraftAttackMixin.java` — mixes into `Minecraft`, implements the Power Attack hold-timer (`POWER_ATTACK_HOLD_TICKS = 12`) directly inside a render/input-adjacent mixin, coordinating with `PowerAttackManager`, `DualWieldTracker`, `PowerAttackFlash`, and two dedicated packets (`OffhandAttackPayload`, `PowerAttackPayload`).
- `src/main/java/zcylas/totality/client/combat/DualWieldTracker.java` (client-side state tracker) and `src/main/java/zcylas/totality/networking/combat/OffhandAttackHandler.java` (server-side handler) — a bespoke, feature-specific client/server split for one specific combat feature (dual-wield offhand attacks), not a reusable channel.
- Dedicated per-feature packets already exist: `OffhandAttackPayload`, `PowerAttackPayload`, `CombatTextPayload` (floating combat text), `CombatRollNotification` — each hand-built for its own feature.
- No `Animation`-named package, class, or design document was found anywhere in `src/main/java` or `Context/` (confirmed by targeted search this pass) — there is no existing Animation API, Visual Effect API, or Targeting API of any kind; all three are pre-implementation, roadmap-level concepts as of this checkpoint.

**Determinations:**

1. **What already exists:** hand-written, per-feature `HumanoidModel`/`ItemInHandRenderer`/`Minecraft` mixins directly manipulating vanilla model parts and input state, plus bespoke client-state trackers and one-off packets per feature (dual wield, power attack).
2. **What is currently bespoke:** everything — there is no shared animation infrastructure; every combat-presentation feature (dual wield, power attack hold-flash, offhand attacks) was built as its own vertical slice with its own mixin, tracker, and packet.
3. **What currently relies on vanilla swing/pose behavior:** ordinary (non-power) melee attacks and all non-dual-wield/non-power-attack combat presentation appear to still ride vanilla's own swing/arm-pose animation untouched — `HumanoidModelMixin` only overrides specific parts under specific tracked conditions (dual wield), not a general replacement of vanilla pose logic.
4. **Which systems would benefit from a shared API:** every system named in the task's Part 15 list that currently has *no* presentation code at all yet is a clean, non-conflicting candidate — spells, abilities, transformations, Super Leap, species powers, mob/companion actions — since building each of these as its own bespoke mixin (matching the existing pattern) would multiply the exact duplication a shared Animation API exists to prevent. Combat (power attack, dual wield, offhand) is the one area that would need an explicit **migration**, not a greenfield build, since bespoke code already exists and works.
5. **Which existing code would be at risk from a future animation integration:** `HumanoidModelMixin`, `MinecraftAttackMixin`, `ItemInHandRendererMixin`, `AvatarRendererMixin`, `DualWieldTracker`, and the Power Attack hold-timer/flash pipeline — any of these that a future Animation API subsumed would need a careful, tested migration rather than a parallel rebuild, precisely because they are today's only working combat-presentation code and are already covered by this session's own `PowerAttackFlashVerification`/`OffhandAttackVerification` self-tests (confirmed present and passing in every dedicated-server run this entire session chain).
6. **Which Punchy! concepts map cleanly to Totality:** **cannot be determined** — no Punchy! architectural concept was established (§9–§31).
7. **Which Punchy! concepts do not fit Totality:** **cannot be determined** for the same reason, with the one clear exception already noted in §31: none of Punchy!'s code or assets may be copied under any circumstance, regardless of architectural fit, per its license.

---

## 33. Proposed Totality Animation API Responsibilities

Independent recommendation, **not derived from Punchy! evidence** (none was available) — grounded in Totality's own current architecture (§32) and general, widely-established game-animation-system design principles. Not implemented by this pass.

An eventual Totality Animation API should plausibly own:

| Concept | Totality use case | Evidence/reasoning | Likely owner | Phase |
|---|---|---|---|---|
| Actor-generic animation requests (Player + LivingEntity) | Every combat/spell/ability/species feature today hardcodes `Player`- or `ServerPlayer`-specific mixins; mobs/Companions have none | Independent reasoning: Totality's own roadmap already plans Companion/mob presentation (per project memory) | Animation API | Foundation |
| Animation identifiers (`Identifier`-keyed, like every other Totality registry: `PlayerResourceIds`, `ModEffects`, etc.) | Consistency with Totality's existing registry conventions (Resource API, Ability API) | Totality's own established pattern across every prior API built this session chain | Animation API | Foundation |
| Playback channels distinguishing base locomotion / upper-body action / full-body override / additive overlay | `HumanoidModelMixin` today conditionally overrides specific model parts (arms) for dual wield while other parts presumably stay vanilla — an implicit, ad hoc version of exactly this channel concept | Direct evidence from `HumanoidModelMixin`'s shadowed `head`/`body`/`leftArm` fields (§32) | Animation API | Foundation |
| Priority + interruption policy | Power Attack's 12-tick hold state (`MinecraftAttackMixin`) is itself an informal "in-progress action that can be interrupted" state machine today, reimplemented per-feature | Direct evidence from `MinecraftAttackMixin`'s `totality$holdTicks`/`totality$holdingAttack` fields | Animation API | Foundation |
| Server-authoritative gameplay, client-presentation split | Already the established pattern: `OffhandAttackHandler` (server) + `DualWieldTracker` (client) are already split this way per-feature | Direct evidence from existing combat networking package | Animation + Combat API boundary | Foundation |
| Tracking-player synchronization (broadcast to nearby players) | Every current combat packet (`OffhandAttackPayload`, `PowerAttackPayload`) presumably already solves this per-feature; a shared channel would generalize it | Inference from existing per-feature packet pattern, not directly inspected this pass | Animation API | Foundation |
| Blend-in/blend-out, speed scaling, hold-last-frame | None of these exist in Totality today in any generalized form | Independent reasoning — standard needs once more than one animation type exists (e.g. power attack transitioning to ordinary combat) | Animation API | Later phase |
| Event markers (presentation-only) vs. gameplay impact markers | Explicitly required by this task's own instructions: "Animation event markers may request or align presentation, but authoritative gameplay must remain server validated" | Task's own explicit design constraint, reinforced by Totality's existing pattern (Power Attack's actual damage/cost logic lives in `PowerAttackManager`, not in any rendering mixin) | Split: Combat/Spell/Ability API own gameplay; Animation API owns presentation timing only | Foundation |
| Late-join / dimension-transfer cleanup | No current combat-presentation state is known to persist across dimension changes; risk of "stuck" client-only visual state is a generic concern for any stateful client tracker (like `DualWieldTracker`) | Independent reasoning, informed by this session's own repeated emphasis (across the Stamina Depletion and dormant-Resource tasks) on join/disconnect/dimension-change lifecycle correctness | Animation API | Foundation |
| Fallback to vanilla behavior when an animation asset/definition is absent | Currently, if `HumanoidModelMixin`'s tracked condition is false, vanilla pose logic presumably runs unmodified already — an implicit version of this fallback exists per-feature today | Direct evidence, inferred from `HumanoidModelMixin`'s conditional-override structure | Animation API | Foundation |
| Resource-pack-provided animation definitions | Not currently needed by any existing Totality system; the existing systems are all hand-tuned constants (`POWER_ATTACK_HOLD_TICKS = 12`) not data-driven | Independent reasoning — likely premature for an initial foundation given Totality's current all-code-driven pattern | Animation API | Later phase, possibly never |

---

## 34. Proposed Ownership Boundaries

Restating the task's own explicit, already-decided boundary (not a new recommendation, but recorded here as the accepted design constraint this audit's other recommendations must respect):

- **Combat** owns attack validity, hit timing, damage, Stamina cost, and outcomes.
- **Spell API** owns spell validity, target rules, slot/resource costs, and effects.
- **Ability API** owns ability rules and effects.
- **Animation API** owns visual playback, synchronization, blending, interruption, and presentation events only.
- **Targeting API** owns target-selection sessions and selected-target presentation.
- **Visual Effect API** owns semantic effects (outlines, checkmarks, floating numbers, auras, transformation visuals) where appropriate.

An animation file/definition must never become the sole authority for damage or resource costs — event markers may *request or align* presentation timing, but authoritative gameplay must remain server-validated, exactly as Totality's own existing `PowerAttackManager`/`OffhandAttackHandler` split already demonstrates in practice (§32).

---

## 35. Potential Initial Vertical Slice

See §21 of the task (Part 21) — recommendation only, not implemented. Candidates considered: unarmed punch, power attack, blocking, shuriken throw, spell-casting gesture, Potion of Healing consumption, selected-target checkmark presentation.

**Recommendation: migrate the existing ordinary (non-power) melee swing** as the very first vertical slice — **not** a new feature, and deliberately **not** Punchy!'s own namesake "unarmed punch" feature (which this audit has zero evidence about and should not be copied regardless, per §31).

**Why this candidate, over the alternatives:**

- It is the **smallest possible gameplay-risk surface**: ordinary melee swing today rides pure vanilla animation (§32, item 3) — there is no existing bespoke Totality code to break, migrate, or risk regressing, unlike Power Attack or dual wield (which already work and are already tested by `PowerAttackFlashVerification`/`OffhandAttackVerification`).
- It **exercises the most foundational architectural assumptions** at once: one actor type (Player) → one server-authoritative trigger (an ordinary attack, already server-validated by existing Combat code) → one playback channel (upper-body/full-swing) → one tracking-player sync path → one interruption case (death) → one fallback case (vanilla swing, trivially available as the existing baseline to fall back to).
- It provides a **built-in, zero-cost fallback**: if the new Animation API path fails or is disabled, the feature can trivially do nothing extra and the player simply sees vanilla's own swing, exactly matching the task's own required fallback property — no other candidate has this property as cleanly (Power Attack's hold-flash, dual wield's arm pose, and the checkmark presentation all currently have *no* vanilla equivalent to fall back to).
- It deliberately **avoids** touching the one area with real regression risk (Power Attack/dual wield, §32 item 5) until the API has already been proven on the lowest-risk case.

The selected-target checkmark (§39, below) was considered but rejected as the *first* slice specifically because it is presentation for a system (Targeting API) that does not yet exist at all — building it first would require designing two new APIs simultaneously rather than validating one.

---

## 36. Spell and Ability Integration

Not implemented; recommendation only. Per the ownership boundary (§34), a future Spell/Ability cast would: (1) have the Spell/Ability API validate cost/slot/target rules and commit the cast (gameplay authority, unchanged from today's `Spell`/`Ability` base classes per this session's project memory); (2) separately request an Animation API playback (e.g. a casting-gesture channel) purely for presentation; (3) the Animation API's own event markers may *align* a visual/audio cue with a moment in the gesture, but the actual spell effect's resolution must remain owned by the Spell API's own server-side logic, never triggered by an animation-file marker directly — mirroring the same split Totality's own Power Attack already demonstrates between `PowerAttackManager` (gameplay) and the render-side hold/flash mixins (presentation).

## 37. Combat Integration

Not implemented; recommendation only. Per §32 item 5 and §35, Combat-owned melee/Power Attack/dual-wield/offhand presentation are the **highest-value but highest-risk** migration targets — recommended as a deliberately *later* phase than the vertical slice in §35, once the API has been proven on lower-risk ground, given that real, tested, working code already exists for all three and must not regress.

## 38. Mob and Companion Integration

Not implemented; recommendation only. No mob/Companion animation code of any kind currently exists in Totality (§32) — this is a clean, non-conflicting greenfield opportunity for the Animation API's actor-generic design (§33) to prove itself on `LivingEntity` actors, not just `Player`, without any migration risk.

## 39. Targeting Checkmark Presentation Note

Recorded per the task's explicit instruction — a design decision, not an implementation, and not derived from any Punchy! evidence:

For the future shared Spell/Ability targeting flow: activating a targeted spell/ability enters a targeting session; valid entities are selected by looking at them and left-clicking; the HUD may show progress (e.g. "TARGETS 1/3"); selected targets display a checkmark above their head; left-clicking a selected target may deselect it; right-click or the spell/ability key again confirms; a cancel input exits without casting or spending resources; **the server owns and revalidates the target list.**

Per the ownership boundary (§34): **Targeting API** owns the selection session itself; **Spell/Ability API** owns execution once confirmed; the **checkmark** is world-space presentation tied to a game state (which entities are currently selected) rather than a timed playback sequence — it is therefore a more natural fit for a **Visual Effect / Targeting Presentation layer** than for the Animation API. **This audit found no Punchy!-derived evidence either way** (no rendering code was inspected, §19–§27), so no Punchy!-sourced technical justification is offered for this placement — it follows directly from the ownership boundary already decided in §34 (Visual Effect API "owns semantic effects such as outlines, checkmarks..."), independent of this audit.

---

## 40. Version and Dependency Risks

From metadata alone (§5): Punchy! targets Minecraft `26.2` exactly (not a range) with Fabric Loader `>= 0.19.3`, Java `>= 25`, and `fabric-api` (any version) — all compatible with Totality's own toolchain versions as confirmed elsewhere in this session (Totality itself targets MC 26.2, Fabric, Mojang mappings). Since this is purely a reference/inspiration artifact never added as a dependency, no actual version-compatibility risk to Totality exists from Punchy! itself — the only relevant "risk" is the license boundary already covered in §6.

## 41. Evidence Limitations

Stated plainly and completely: **this audit could not answer the large majority of its own requested questions (Parts 5–14, 18–20, 27–31 of the original task structure)** because doing so would have required decompiling `.class` files and/or opening mixin-configuration and animation-resource files whose content constitutes exactly the "code" and "assets" the license (§6) explicitly protects from reverse engineering. Every section above that says "Not established" reflects a deliberate stop, not a failed attempt. No inference was drawn beyond what `fabric.mod.json`/`MANIFEST.MF`/`pack.mcmeta`/`LICENSE_Punchy` and the top-level archive path listing directly state.

## 42. Files Inspected

- `Inspiration Mods/punchy-2.6.2-fabric-26.2.jar` (archive-level metadata only — `zipfile.namelist()`, `zipfile.testzip()`, and four entries read in full: `fabric.mod.json`, `META-INF/MANIFEST.MF`, `pack.mcmeta`, `LICENSE_Punchy`)
- Totality's own `src/main/java/zcylas/totality/mixin/client/{HumanoidModelMixin,ItemInHandRendererMixin,AvatarRendererMixin,MinecraftAttackMixin}.java`, `src/main/java/zcylas/totality/client/combat/DualWieldTracker.java`, `src/main/java/zcylas/totality/networking/combat/{OffhandAttackHandler,OffhandAttackPayload,PowerAttackPayload,CombatTextPayload,CombatRollNotification}.java` (read for §32 current-state comparison; these are Totality's own files, unrestricted, read-only, unmodified)

## 43. Final Conclusions

1. Punchy!'s explicit "All Rights Reserved" license, with an unambiguous reverse-engineering prohibition, correctly and unavoidably halted this audit at the safe-metadata boundary — exactly the outcome this task's own instructions anticipated and required.
2. What was established: mod identity, version, exact MC/loader/Java requirements, client-only environment, entrypoint class names, three mixin-config filenames (not contents), declared license, and that no third-party animation library is declared as a Fabric dependency.
3. What was not established: essentially everything about Punchy!'s actual animation architecture, combat/punch flow, networking model, rendering hooks, and resource format — honestly reported as unknown rather than inferred.
4. Totality's own current animation-adjacent infrastructure (§32) is entirely bespoke, per-feature, and vanilla-pose-reliant outside of Power Attack/dual wield — a real, evidence-backed foundation for the recommendations in §33–39, none of which required or used any Punchy! evidence.
5. The single most load-bearing finding of this audit is legal, not technical: **no part of Punchy! may be copied into Totality under any circumstance**, independent of any architectural merit it might otherwise have had.

## 44. Recommended Next Steps

1. If Punchy!'s actual animation architecture is still considered valuable to study, obtain the author's explicit written permission for reverse-engineering/decompilation (per §6 §2) before any future audit pass attempts it — or seek a different, permissively-licensed reference mod instead.
2. Proceed with Totality's own Animation API design using §33–39 as a starting point, grounded entirely in Totality's own current code and independent reasoning, with no dependency on this audit ever being completed against Punchy! specifically.
3. Do not begin Animation API implementation, the Punchy! runtime behavior test, the Spell/Ability Targeting API, Food 0–100, or any other roadmap task as part of closing out this audit pass, per this task's own explicit stop point.
