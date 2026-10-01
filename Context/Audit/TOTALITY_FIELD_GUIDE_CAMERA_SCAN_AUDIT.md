# TOTALITY — Field Guide Camera & Scan Audit

- **Date:** 2026-10-01.
- **Purpose:** study the Field Guide inspiration mod before Camera & Gallery V1, and prepare the later Scan mode and Codex work.
- **Scope:** research only. Nothing from Field Guide was copied into Totality: no code, no assets, no shaders.

---

## 0. Sources examined and how

| Source | What it is | How I examined it |
|---|---|---|
| `Inspiration Mods/fieldguide-1.20.0+26.3-fabric.jar` | The **actual released JAR** (`fabric.mod.json`: id `fieldguide`, version `1.20.0+26.3`, Minecraft `~26.3`, **MIT**, authors "Evan, Kobber") | Unzipped to a scratch directory. Read the mixin configs, `fabric.mod.json`, the asset list and the class list. `javap -p` (signatures) and `javap -c` (bytecode) on the scan, render, network, config and compat classes. No decompiler is available offline, so findings marked **[JAR]** come from signatures, constant pools and bytecode call sequences |
| `Inspiration Mods/Field-Guide-26.1/` | Source tree, `gradle.properties` version **1.7.7**, Minecraft **26.1.2**. An **older branch** than the JAR | Read the Java sources for the scan pipeline, server verification, progress storage, rendering, mixins and docs. Findings marked **[SRC]** |

**Version caveat.** The JAR (1.20.0, MC 26.3) is several releases newer than the source tree (1.7.7, MC 26.1). The JAR has many classes the source tree lacks, for example:
- `DiscoveryOverlayRenderer`, `AfterLevelOverlay`, `ScanContextHelper`;
- a **lens** item (`ModItems` registers `"lens"`);
- `holdsLens`, `wearsLens`, `isRightClickHeld`, `getDiscoveryDistance` on `FieldGuideScanManager`.

When I describe source-level behaviour, I confirmed it against the JAR's bytecode where possible. Where I could not, the item is marked **assumption**.

**Licence.** MIT (`LICENSE`, "Copyright (c) 2025 Evan Bowness"; the JAR's `fabric.mod.json` also says `"license":"MIT"`). MIT would allow reuse with attribution. Totality's policy for inspiration mods is study-only, though, so **no code, textures or shaders were copied**. Every Totality class in V1 is written independently. Field Guide's approach informs §6–§7 only at the level of ideas.

---

## 1. Headline findings

1. **Field Guide has no photography of any kind. (Verified.)**
   - There is no image capture, no framebuffer readback and no `Screenshot`/`NativeImage` use anywhere in the JAR's classes. A `grep` across all `.class` files for `Screenshot`, `NativeImage`, `takeScreenshot` and `mainRenderTarget` returns nothing. **[JAR]**
   - "Photographs" exist only as an optional bridge to the third-party **Exposure** mod's photograph *items*:
     - **[SRC]** In 1.7.7, `FieldGuidePhotographScreen`, `FieldGuidePhotographWidget` and the `SET_PHOTOGRAPH`/`REMOVE_PHOTOGRAPH` packet actions are entirely **commented out**.
     - **[JAR]** In 1.20.0, `compat/exposure/ExposureCompat.isPhotographItem` and every `ClientExposureCompat` query compile to a constant return (`iconst; ireturn`), so it is stubbed. A `ServerConfig.exposureUnlockViaPhotograph` flag exists, and the `exposure/missing_photograph.png` / `add_photo` sprites ship, but the bridge is inert in this build.
2. **Scanning is a "look at it with a spyglass and hold" mechanic, with no camera interface. (Verified.)**
   - There is no viewfinder, no zoom of its own, no shutter and no mode switch. The player uses the **vanilla spyglass** (or, in 1.20.0, the mod's **lens** item, or the naked eye if the server enables it). Field Guide adds a reticle icon and a world-space highlight on the target.
3. **Discovery is client-detected and server-verified. (Verified.)**
   - The client ray-traces and times the scan, then sends `ScanUnlockPacket(entryId, variantId, scannedTargetId, blockPos?, entityId)`.
   - The server re-checks scanning permission, distance, target existence and category membership (`ScanVerifier`) before unlocking.
   - Progress lives **per world/server and per player UUID**, as files under the world save.

The practical consequence for Totality: **Field Guide offers nothing to reuse for Camera V1 photography.** It is a useful reference for the future **Scan → Codex** pipeline (§6), and for a few things to avoid (§7).

---

## 2. Camera / scanning interface

| Question | Finding | Evidence |
|---|---|---|
| Is there a camera UI? | **No.** No screen or overlay owns the view. Scanning happens in ordinary first-person play while the spyglass is used | **[SRC]** `FieldGuideScanManager.isUsingSpyglass` → `player.isScoping() \|\| useItem is #spyglasses`; **[JAR]** the same plus `holdsLens`/`wearsLens`/`holdsFieldGuide` |
| Zoom | **Vanilla spyglass zoom only** (the spyglass's own FOV modifier and overlay). The mod adds no FOV control | No `Camera`/`GameRenderer` FOV mixins in either mixin config **[JAR]** |
| Reticle / progress | A 32×32 icon strip `textures/gui/scanning.png` (6 frames: 4 progress frames, "done", "out of range"), drawn at screen centre (with configurable offset) | **[SRC]** `FieldGuideClient.renderScanningIcon`. **[JAR]** `mixin/client/GuiMixin` (Fabric config) injects into `Gui.extractRenderState` and calls `FieldGuideClient.renderScanningIcon` |
| Highlight of the target | The scanned entity or block is **re-rendered with a custom shader** that tints it and reveals it bottom-to-top as the scan progresses. Out-of-range targets pulse red | **[SRC]** `ScanOverlayRenderer` packs the reveal height and RGBA into the light-coords int (`getPackedScanLightCoords`). `ScanNodeCollector` wraps every `submitModel`/`submitBlockModel` call with a wrapped `RenderType`. `fieldguide_scan.fsh` discards fragments above `scanLimitY` and brightens a thin edge band. **[JAR]** `LevelRendererMixin` → `fieldguide$renderOverlays(CameraRenderState, double)`, plus `ScanNodeCollectorFabricMixin`, `FullbrightNodeCollector` and `RenderTypeAccessor`/`RenderSetupAccessor`/`TextureBindingAccessor` |
| Nearby-discovery hints (1.20.0) | `DiscoveryOverlayRenderer` periodically gathers *unscanned* scannable blocks and entities near the player. It checks that they are inside the frustum and partly visible (`sightClear`, `isBlockPartiallyVisible`) and draws a fading, pulsing highlight. Caps: `MAX_FOUND`, `MAX_RENDERED`, `RECOMPUTE_INTERVAL` | **[JAR]** signatures and constants; I did not read the bodies line by line (**assumption** on the exact visuals) |
| Sounds | Scan start: `VILLAGER_WORK_CARTOGRAPHER` (0.4). Unlock: `EXPERIENCE_ORB_PICKUP` (0.25). Both can be turned off in the client config | **[SRC]** `FieldGuideScanManager`; **[JAR]** `ClientConfig.playScanningSound` / `playUnlockSound` reads |
| Unlock feedback | A toast (`FieldGuideToast`) and a "quick open" of the new entry if the guide key is pressed soon afterwards | **[SRC]** docs `usage.mdx`; classes present in **[JAR]** |

## 3. How targets are detected and identified

**[SRC]** (`FieldGuideRaytracer.processScanning`), structure confirmed in **[JAR]**:

1. **Ray** from the player's eye along the view vector, **256 blocks**.
   - `ProjectileUtil.getEntityHitResult` finds pickable, non-spectator entities in a box swept along the ray.
   - `level.clip(OUTLINE, Fluid.NONE)` finds blocks. **Replaceable** blocks (grass, flowers) are **stepped through** with repeated clips, so the "real" block behind them can be found. The first hit is kept as a fallback, so the plant itself can still be the target.
2. **Nearest wins** between entity and block. An `EnderDragonPart` maps to its parent. An `ItemEntity` maps to its *item*.
3. **Entry resolution:**
   - Redirect tables, so variants or aliases share an entry.
   - **Composite disambiguation:** for multi-block entries such as "Oak Tree", a 9×9×9 neighbourhood is scored by how many child blocks match, and the result is cached per position. In 1.20.0 it moved to `ScanContextHelper.traceTreeCanopy` / `sampleNearbyBlocks`.
4. **Eligibility:**
   - The entry must belong to a category.
   - It must not already be unlocked, unless an un-scanned **variant** is visible (`needsVariantScan`).
   - It must not be tagged `fieldguide:kill_to_unlock` or `eat_to_unlock`; those need a gameplay action, not a scan.
5. **Range check:** the hit distance against the active range (spyglass / lens / naked-eye / field-guide distances from `ServerConfig`). Out of range shows the red "out of range" state.
6. **Dwell timer** (`FieldGuideScanState`):
   - Holding the same target adds +1 per tick.
   - Losing it removes −2 per tick, so a brief wobble isn't fatal.
   - Completion at `scanSpeed × 20` ticks. `FADE_DURATION` is 10 ticks for the completion fade.
   - A new target resets the timer and plays the start sound.

**Hitbox minimum (1.20.0):** `ServerConfig.minScanHitboxSize` exists **[JAR]**. Presumably it enlarges tiny targets for picking (**assumption**).

## 4. Interaction with entities and blocks

- **Read-only.** Scanning never changes the world: no damage, no AI interruption, no block updates. **[SRC]**
- The **server** re-reads world state for verification (`ScanVerifier`) **[SRC]**:
  - entity by network id, not a spectator, within range, of a valid type for the category, and its type id matches the client's `scannedTargetId`;
  - block loaded, within range + 4 blocks of slack, valid for the category, and its id matches.
- Successful scans fire advancement triggers (`FieldGuideTriggers.SCAN_ENTITY`, `ENTRY_UNLOCKED`, `CATEGORY_COMPLETED`), optional XP (`grantXpOnScan`, `xpAmountOnScan`) and optional **server commands** (global, per category, per entry). **[JAR]** `ServerConfig` fields; **[SRC]** `UnlockRewards`.

## 5. Input, viewfinder, camera movement, discovery records

| Topic | Finding |
|---|---|
| Input | **[JAR]** `SCAN_KEY` (optional key mapping; when bound, scanning also requires holding it) and **`isRightClickHeld()` = `options.keyUse.isDown()`**: scanning piggybacks on holding *use* (the spyglass's own use action). Attack, block-breaking and other conflicts never arise, because the spyglass's use action already owns right-click. The guide opens with `B` (`OPEN_GUIDE_KEY`) |
| Viewfinder rendering | None of its own. The vanilla spyglass overlay plus the centre icon and the in-world highlight |
| Camera movement | Unmodified vanilla (spyglass sensitivity reduction applies through `isScoping`) |
| Real image capture? | **No.** Simulated "photographs" only through Exposure items, stubbed in this build (§1) |
| Discovery records | **Server-side, per player, per world:** `<world>/fieldguide_progress/` (one file per player UUID; legacy `fieldguide_data/progress.dat` migrated) **[SRC]** `FieldGuideProgressManager`. Each entry keeps unlocked variants, custom name and description, **photograph reference (an Exposure item NBT string)**, selected variant, discovery real time and game time, and a "seen" flag **[SRC]** `PlayerFieldGuideProgress` |
| Client sync | `ProgressUpdatePacket` (full sync or per-entry resync, with a builder), `SyncCategoriesPacket`, `SyncConfigPacket` (the server config wins), `MarkSeenPacket`, `UpdateEntryDataPacket` (name, description, variant; photograph actions dead) **[JAR]** |
| Information system | Datapack-driven categories and entries (`data/<ns>/fieldguide/...`), with auto-population for modded entities (`AutoPopulateRegistry`), loot and biome panels, a journal, and resource-pack-overridable text |

---

## 6. Recommendations for Totality's Scan mode and Codex

These are proposals for our next discussion. **None of them is implemented in V1.**

### 6.1 Keep the parts of Field Guide's model that fit Totality

1. **Client proposes, server decides.**
   - The Camera's Scan mode should only *nominate* a target (entity network id, or block position plus state id) and send a request.
   - A **server-side Codex service** re-validates line of sight, range and eligibility, and records the discovery.
   - This mirrors `ScanVerifier` and matches our established rules: the Operator Mode server re-checks per request, and the Entitlement API is server-authoritative.
2. **Store discoveries server-side, keyed by player UUID, inside the world save,** like `fieldguide_progress/`. Ideally use our existing player-data or attachment persistence rather than loose files.
   - Discoveries then follow the world or server.
   - They never depend on a local photograph (see §6.3).
3. **Dwell timer with forgiving decay** (+1/tick, −2/tick off-target). It reads well in a viewfinder because the "lock" builds while the player steadies the phone. Show progress **in the Camera viewfinder** (ring around a reticle) instead of a floating centre icon.
4. **Ray-trace that steps through replaceable plants** and **resolves composite subjects** (trees, structures) by sampling nearby blocks. This is good prior art for "what is the player actually photographing".
5. **Data-driven entries** (datapack JSON) with redirects and variants, so Codex content can be authored without code.
6. **Explicit action-gated entries** (`kill_to_unlock`, `eat_to_unlock` equivalents), so Scan never trivialises gameplay discoveries.

### 6.2 Use Totality's own Camera instead of the spyglass

- Scan mode lives **inside Camera** (mode selector already present in V1). It reuses the same **zoom system** (`CameraZoom`) and the same **input model**:
  - the Camera owns left-click;
  - no attack or use leaks through;
  - Alt shows the cursor.
- The scan range can scale with zoom (2× extends range), and future Phone tiers can raise the range or the zoom cap. That replaces Field Guide's per-item distance table.
- A **world-space highlight** of the target (Field Guide's reveal shader) is visually strong, but it cost Field Guide a custom shader, `RenderType` wrapping and three accessor mixins into render internals. For Totality I recommend a cheaper first step:
  - a **screen-space bracket** around the target's projected bounding box;
  - a progress ring in the viewfinder.

  Consider a shader effect only after review.

### 6.3 Photographs and discoveries stay independent (agreed design)

> **Architectural rule (recorded in the implementation report §2.1): Codex discoveries must never depend on Camera photograph storage.** A scan may optionally save a photograph and associate its stable id with a discovery, but deleting, losing or corrupting that photograph must never remove or invalidate the discovery.

- A Scan should **create a Codex discovery record server-side**. Optionally, it can also save an ordinary local photograph through the V1 pipeline, with `mode = "scan"` and the discovery id in the photo's metadata (the V1 schema already has `mode` and an extensible metadata map; see the implementation report §5).
- **Deleting a photograph never deletes a discovery**:
  - the Codex record lives on the server;
  - the photo only references the discovery, never the reverse;
  - the Codex never needs the image file to exist.
- If the Codex later shows "your photo of this subject", it should look the photo up by discovery id in the *local* gallery and degrade gracefully when the photo is missing. Field Guide's `missing_photograph` placeholder is the right idea.

### 6.4 Multiplayer and anti-cheat notes

- Server-side validation must use **server** positions and **server-side** line-of-sight. Field Guide gives 4 blocks of slack on block range because client and server positions differ slightly.
- Rate-limit scan requests per player. Field Guide doesn't appear to (**assumption**: no limiter class found in the JAR).
- Never trust a client-sent entry id alone. Derive the entry from the verified target on the server. Field Guide checks that the client's entry matches the target; deriving is simpler and safer.

---

## 7. Approaches unsuitable for Totality

| Field Guide approach | Why it doesn't fit |
|---|---|
| Scanning through the vanilla spyglass and `keyUse` | Totality's Camera is a Phone app with its own viewfinder, zoom and input model. Tying scanning to an item's use action would conflict with the Camera owning the mouse |
| Exposure-mod photographs as items with NBT stored in progress | Our photographs are **local PNG files** (V1 brief) and must not be stored or transferred by servers. Embedding a photo reference *inside* the server's discovery record would couple the two, against §6.3 |
| Loose JSON/NBT files per player in the world folder, written by a bespoke manager | Workable, but Totality already has its own persistence conventions (components and attachments). Codex data should use them |
| Server config that silently overrides client options (`SyncConfigPacket`) for presentation settings | Fine for gameplay rules, but presentation (sounds, overlays) should stay client-side in Totality |
| Custom-shader target highlight via `RenderType` wrapping and light-coord packing | A clever trick but brittle: it depends on render internals (three accessor mixins), and Iris/shader-pack compatibility is unclear. Prefer screen-space UI first (§6.2) |
| Naked-eye scanning with no device | Contradicts the Phone-as-instrument design; Scan should require the Camera app (and possibly a tier) |
| Composite scoring over a fixed 9×9×9 cube on every new block position | Fine for a few entries, but it scales poorly with a large Codex. Precompute the candidate composite set per block type, and cap the samples |

---

## 8. What this audit changed in the V1 implementation

- **Confirmed** that we must build real photography ourselves. The V1 capture is Totality's own: a GPU readback of the main render target between the world pass and the GUI pass (implementation report §4).
- **Kept the Scan seam:**
  - `CameraMode.SCAN` exists and is selectable, but shows "Scan — coming with the Codex" and does nothing;
  - the photo metadata schema reserves `mode` and an open `extra` map for a future discovery id;
  - the zoom system is mode-agnostic.
- **No** fake discoveries, progression or Codex entries were added.
