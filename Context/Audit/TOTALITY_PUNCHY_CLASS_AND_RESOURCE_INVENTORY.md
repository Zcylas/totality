# TOTALITY — Punchy! Class and Resource Inventory

**Status: minimal by design.** A full class/resource inventory (per-package class counts, resource file listings, animation/model/sound file paths) requires either recursively listing archive contents well below the top level or opening individual resource files for content/format inspection. Once the explicit reverse-engineering-prohibited license (`LICENSE_Punchy`, see the main audit report §6 and the evidence index §4) was found, this audit limited itself to the single top-level path listing already captured in the evidence index, on the judgment that even a full recursive path listing of `punchy/`'s internal package structure would begin to reveal Punchy!'s internal code organization beyond basic compatibility metadata. This document exists to record that boundary explicitly, per the task's own "do not silently fill gaps" instruction, rather than to omit the deliverable.

## What is known (top-level only, path names, no content)

| Path | Type | Notes |
|---|---|---|
| `LICENSE_Punchy` | file | License text (fully read — see evidence index §4) |
| `META-INF/` | directory | Contains `MANIFEST.MF` (fully read) |
| `assets/` | directory | Not listed recursively; presumed to hold client resources (textures/models/sounds/animations) per standard Fabric resource-pack convention and this mod's own `pack.mcmeta` |
| `fabric.mod.json` | file | Mod descriptor (fully read) |
| `pack.mcmeta` | file | Resource-pack descriptor (fully read) |
| `punchy/` | directory | Presumed Java package root for classes under `punchy.*`, based only on the entrypoint class names declared in `fabric.mod.json` (`punchy.fabric.PunchyFabricClient`, `punchy.client.config.PunchyModMenu`) — not confirmed by listing `.class` files |
| `punchy.compat.mixins.json` | file | Mixin config filename only; content not read |
| `punchy.fabric.mixins.json` | file | Mixin config filename only; content not read |
| `punchy.mixins.json` | file | Mixin config filename only; content not read |
| `resourcepacks/` | directory | Not listed recursively |

Total archive entries (all depths, counted mechanically by `zipfile.namelist()` without reading any path string beyond the top level shown above): **487**.

## What is explicitly not established

- Class count, class names, or package structure beneath `punchy/`.
- Any file under `assets/` or `resourcepacks/` — no filenames, no formats, no counts.
- Contents of the three mixin-configuration files — target classes, injection points, or mixin class names.
- Whether any animation definition format (JSON state machines, GeckoLib `.animation.json`, Player Animator format, or a custom format) is present — this would require opening files under `assets/`/`resourcepacks/`, not performed.
- Texture, sound, or model file paths and counts.

## Why this boundary was chosen

The task's own Part 1 instructions frame `fabric.mod.json`-level metadata inspection as occurring "before decompilation" and therefore safe; the discovered license's Restrictions clause targets "the Product" as a whole ("all files, assets, code, and associated content") for its reverse-engineering prohibition. Reading four small, standard, purpose-declarative files (mod descriptor, JAR manifest, resource-pack descriptor, and the license itself) was judged consistent with ordinary, non-reverse-engineering compatibility inspection. Enumerating or opening the mod's actual asset/class tree was judged to cross into inspecting "the Product['s]" implementation content itself, which the license does not permit without the author's prior written permission. This is a conservative, evidence-preserving line — the alternative (fabricating or guessing an inventory) would violate the task's own explicit instruction not to silently fill gaps.
