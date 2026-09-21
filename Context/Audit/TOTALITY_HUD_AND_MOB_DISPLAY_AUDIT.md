# Totality HUD, Contextual Interaction, Combat Overlay, and Mob Display Audit

**Date:** 2026-07-31
**Type:** Audit only — no production or test code was modified.

**Labeling key used throughout this document:**
- **[SOURCE]** — a fact read directly from current repository source, verified by this audit.
- **[SCREENSHOT]** — an observation drawn from the user's textual description of four screenshots (the image files themselves were not available to this audit environment — see §6).
- **[REQUIREMENT]** — a user-authored design requirement, stated as given, not independently justified.
- **[RECOMMENDATION]** — an audit conclusion or proposal. Not yet implemented.
- **[HYPOTHESIS]** — plausible but not directly proven from source or a screenshot; flagged so it is never mistaken for a verified fact.

---

## 1. Audit purpose

Produce a code-level, source-verified inventory and assessment of Totality's current player HUD (Health/Mana/Stamina/Food bars, AC, secondary resources), the contextual interaction prompt ("[Z] Harvest"), combat overlays (floating combat text, notifications), and the Mob Display (top-center target panel) — to support a small, contained HUD cleanup (bar geometry only) without starting the full HUD redesign, Food 0-100, or a future Mob Health/Mob HUD API. This document is audit output only; no HUD/Mob Display/Resource/Tooltip file was changed.

## 2. Starting branch

**[SOURCE]** `feature/general-resource-api`

## 3. Starting HEAD and subject

**[SOURCE]** HEAD `69717c6be85b891c6dea0fe7b0751cab4da73b3c`, subject `Migrate client resource presentation consumers` (the Phase 3C finalization commit).

## 4. Local/remote synchronization

**[SOURCE]** `git fetch origin feature/general-resource-api` confirmed `origin/feature/general-resource-api` at the identical commit `69717c6be85b891c6dea0fe7b0751cab4da73b3c` — local and origin were synchronized before this audit began.

## 5. Starting repository-wide status

**[SOURCE]** `git status --short` at the start of this audit showed only the already-known, pre-existing unrelated set:
- `build.gradle` (modified)
- 18 modified `src/main/generated/data/totality/loot_table/blocks/*.json` + `overworld.json` + 4 `recipe/*.json` files (22 total generated JSON files)
- 26 untracked `Context/Audit/Review Bundles/*.zip` (prior review bundles, including the just-closed Phase 3C bundle)
- 3 untracked `Context/Audit/Image References/Tooltip*.png`
- 4 untracked `Context/Trading Test/trade_screen*.png`
- `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/` (untracked)

No unexplained HUD-related file was present. No HUD production file (`TotalityHudRenderer.java`, `MobHealthBarHud.java`, `TotalityClient.java`, any context-HUD file, `NotificationManager.java`, `CombatTextRenderer.java`) appeared as modified. Checkpoint confirmed — audit proceeded.

## 6. Visual references available or unavailable

**[SOURCE]** No image attachments were present in this conversation turn, and no matching new screenshot files were found in the session scratchpad or in `Context/Audit/Image References/` (that folder contains only pre-existing Tooltip and ChatGPT-image files unrelated to this HUD audit). **The four HUD screenshots were not directly inspected.** All visual conclusions in this report are derived from the user's written description and are labeled **[SCREENSHOT]**, never presented as directly observed pixels.

## 7. Screenshot-derived observations

**[SCREENSHOT]**, cross-checked against source where possible (cross-check noted inline):

1. **Screenshot 1 (Normal HUD)**: AC 13 lower-left; three horizontal bars (red/blue/green) with external current/max text to the right of the left stack and — per source geometry (§10-12) — the Food bar's external text sits to the *left* of its own right-anchored frame, i.e. toward screen center. Cross-check: three left bars = Health (red), Mana (blue), Stamina (green) — **[SOURCE]** confirmed by fill-sprite identifiers `HUD_HEALTH_FILL`/`HUD_MANA_FILL`/`HUD_STAMINA_FILL` and their stacking order (Stamina bottom, Mana middle, Health top) in `TotalityHudRenderer.register()`. The long narrow dark bar above the hotbar and the brown lower-right bar are addressed in §16/§17.
2. **Screenshot 2 (Contextual interaction)**: `[Z] Harvest` centered near the crosshair — **[SOURCE]** confirmed to be `AbilityContextHud.renderPrompt`, see §19.
3. **Screenshots 3/4 (Mob Display)**: a top-center panel showing name-only in one state and name + red bar + "75 / 100" in another, with top-left combat feedback visible in the expanded state — **[SOURCE]** confirmed to be `MobHealthBarHud`'s compact vs. in-combat states (§20-23) and, for the top-left text, most consistent with `NotificationManager`'s fixed top-left anchor (§25) rather than `CombatTextRenderer`'s world-projected floating numbers (also §25) — this attribution is **[HYPOTHESIS]** since the exact screenshot pixels were not inspected.

## 8. Complete active HUD element inventory

**[SOURCE]** Confirmed active by tracing an actual registration call reachable from `TotalityClient.onInitializeClient()`/`registerRenderers()`. Each entry lists its registration proof.

| # | Element | Class | Registered via |
|---|---|---|---|
| 1 | Health/Mana/Stamina/Food bars, AC label, offhand attack indicator, secondary-resource pips, context-HUD dispatch | `TotalityHudRenderer` | `TotalityHudRenderer.register()` ← `registerRenderers()` |
| 2 | Rest countdown text | `RestHud` | `RestHud.register()` ← `registerRenderers()` |
| 3 | Top-left stacked notifications | `NotificationManager` | `NotificationManager.register()` ← `registerRenderers()` |
| 4 | Power-attack full-screen flash | `PowerAttackFlash` | `.register()` ← `registerRenderers()` |
| 5 | Quest tracker | `QuestTrackerHud` | `.register()` ← `registerRenderers()` |
| 6 | Mob target name/health panel | `MobHealthBarHud` | `.register()` ← `registerRenderers()`; tick via `ClientTickEvents.END_CLIENT_TICK.register(client -> MobHealthBarHud.tick())` ← `onInitializeClient()` |
| 7 | Floating world-space combat numbers | `CombatTextRenderer` | `.register()` ← `registerRenderers()` (registers only a tick callback); **rendering itself is not a `HudElementRegistry` layer** — it is invoked directly from `GuiExtractRenderStateMixin` (a `@Mixin(Gui.class)` injecting into `extractRenderState`) calling `CombatTextRenderer.onExtractGui(...)` |
| 8 | Heat Vision beam | `HeatVisionBeamRenderer` | `.register()` ← `registerRenderers()` |
| 9 | Ability-equip context prompt (`[Z] <label>`) | `AbilityContextHud` | called inline from `TotalityHudRenderer`'s render lambda — **not its own `HudElementRegistry` entry** |
| 10 | Grimoire slot/spell display | `MagicContextHud` | called inline from `TotalityHudRenderer`'s render lambda — **not its own `HudElementRegistry` entry** |
| 11 | Rage pips (secondary resource) | `ISecondaryResource` (anonymous, in `TotalityClient`) + `TotalityHudRenderer.drawSecondaryResources` | data registered via `SecondaryResourceRegistry.register(...)` ← `registerRenderers()`; **drawn** inline inside `TotalityHudRenderer`'s render lambda, not its own layer |
| 12 | Spell cast progress bar | `CastBarHud` | `CastBarHud.register()` ← `onInitializeClient()` (called separately from `registerRenderers()`, later in init order) |

**[SOURCE] Confirmed NOT active (present in source, but never registered/called — dead code):**
- `SecondaryResourceHud` (`client/hud/resource/SecondaryResourceHud.java`) — declares its own `register()` calling `HudElementRegistry.addLast("totality:secondary_resource", ...)`, an apparent earlier implementation of the same pip/bar rendering `TotalityHudRenderer.drawSecondaryResources` now performs inline. **Grep-confirmed zero call sites of `SecondaryResourceHud.register()` anywhere in the codebase.** Not wired to `TotalityClient` or anywhere else. Dead code — must not be assumed active.
- `MobHealthBarHud.getRank(LivingEntity)` — a private HP-ratio-based "E"–"S" string method, defined but never called anywhere in the file (the live rank text comes from `MobRank.values()[mobData.rankOrdinal()]` in `buildName`, a different code path). Dead code within an otherwise-active class.
- `MobHealthBarHud.COLOR_BOSS` — declared, never returned by `getThreatColor`. Dead constant.
- The "extra bars" (Mana/Poise) branch implied by `showExtraBars`/`COLOR_MANA_FILL`/`COLOR_POISE_FILL`/`COLOR_MANA_BG`/`COLOR_POISE_BG` — these colors are declared and `showExtraBars` is computed and consumed by `getPanelW`/`getPanelH` (reserving layout space), **but `render()` contains no code that actually draws a mana or poise bar.** Since `showExtraBars` additionally requires `perception >= 3` and `getPerceptionMasteryLevel()` is hardcoded to return `0` (`// TODO: read from ClientMasteryManager`), this branch is currently unreachable at runtime *and* has no rendering implementation even if it were reached. Confirmed dead/unfinished, not merely gated.

## 9. Render registration and order map

**[SOURCE]** All `HudElementRegistry.addLast(...)` calls found in the codebase, with the call site that invokes each `.register()`:

| Layer ID | Registered from | Call order in `TotalityClient` |
|---|---|---|
| `totality:totality_hud` | `TotalityHudRenderer.register()` | 1st (`registerRenderers()`) |
| `totality:rest_countdown` | `RestHud.register()` | 2nd |
| `totality:notifications` | `NotificationManager.register()` | 3rd |
| (PowerAttackFlash's own ID, not re-verified by name) | `PowerAttackFlash.register()` | 4th |
| (QuestTrackerHud's own ID) | `QuestTrackerHud.register()` | 5th |
| `totality:mob_health_bar` | `MobHealthBarHud.register()` | 6th |
| (HeatVisionBeamRenderer's own ID) | `HeatVisionBeamRenderer.register()` | 7th |
| (CastBarHud's own ID) | `CastBarHud.register()` | later, in `onInitializeClient()` proper (not `registerRenderers()`) |

`CombatTextRenderer` is **not** in this table — see §8 row 7; it draws via a `Gui.extractRenderState` mixin injection, not via `HudElementRegistry`, so its ordering relative to the table above is a separate mixin-injection-point question, **not verified from source in this pass — [HYPOTHESIS]** that it draws at whatever point vanilla's own `Hud.extractRenderState` call falls relative to `HudElementRegistry`'s consolidated layer list (this is an architectural detail of Fabric API's `HudLayer`/`Gui` integration this audit did not trace further).

Vanilla elements `HudElementRegistry.replaceElement`'d to a no-op: **only** `VanillaHudElements.HEALTH_BAR`, `ARMOR_BAR`, `FOOD_BAR` **[SOURCE]**. Confirmed by decompiling the installed `fabric-rendering-v1-25.3.1` jar's `VanillaHudElements` class that the full vanilla element catalog additionally includes `MISC_OVERLAYS, CROSSHAIR, SPECTATOR_MENU, HOTBAR, AIR_BAR, MOUNT_HEALTH, INFO_BAR, EXPERIENCE_LEVEL, HELD_ITEM_TOOLTIP, SPECTATOR_TOOLTIP, MOB_EFFECTS, BOSS_BAR, SLEEP, DEMO_TIMER, SCOREBOARD, OVERLAY_MESSAGE, TITLE_AND_SUBTITLE, CHAT, PLAYER_LIST, SUBTITLES` — **none of these are replaced, cancelled, or otherwise touched anywhere in the codebase** (grep-confirmed zero references to `BOSS_BAR`, and zero further `replaceElement` calls beyond the three listed). `HOTBAR` (which vanilla uses for both the hotbar and the offhand item slot) is untouched — offhand rendering is 100% vanilla (see §12).

Because `HudElementRegistry.addLast` appends to a shared ordered layer list at registration time, later-registered Totality layers draw **on top of** earlier ones where they geometrically overlap. In particular `MobHealthBarHud` (6th) draws after `NotificationManager` (3rd) — if their regions overlap, the Mob Display's opaque panel background paints over notification text, not the reverse (see §29 collision matrix).

## 10. Player-bar implementation findings

**[SOURCE]** All four main bars (Health, Mana, Stamina, Food) and the AC label are drawn by `TotalityHudRenderer`'s single `HudElementRegistry.addLast(HUD_ID, ...)` lambda, using two structurally-identical private draw helpers: `drawBarSmooth` (left-anchored, fill left→right, text to the right of the frame) and `drawBarMirroredSmooth` (right-anchored, fill right→left, text to the left of the frame). Two more helpers, `drawBar`/`drawBarMirrored`, exist with the same geometry but take a raw `value/maxValue` pair instead of a `SmoothValue` — **grep-confirmed these two are never called anywhere**; dead code (an earlier, non-animated version superseded by the `*Smooth` variants).

Stacking order (top to bottom, left side): Health → Mana → Stamina → (bottom). Right side: only Food, at the same Y as Health (`hpY`).

## 11. Current dimensions and formulas

**[SOURCE]**, all constants read directly from `TotalityHudRenderer.java`:

- Frame draw size: `BG_WIDTH = 83`, `BG_HEIGHT = 8` (both left and right bars share this).
- Frame texture reference size passed to `blitSprite`: `BG_PNG_W = 83`, `BG_PNG_H = 8` — i.e. the sprite is referenced and drawn 1:1 at its declared logical size.
- **Actual native PNG resolution** (measured directly from the asset file, not from the Java constants): `bar_background.png` is **166×16** — exactly double the 83×8 logical size the code uses. This is a deliberate high-resolution ("@2x") art asset: the texture can be rendered crisply up to 166×16 logical pixels without any upscaling blur; anything larger would need to upscale the source pixels.
- Fill draw size: `DRAW_FILL_W = 73`, `DRAW_FILL_H = 4`; fill texture reference: `FILL_PNG_W = 73`, `FILL_PNG_H = 4`. **Actual native PNG resolution** for the four fill sprites: `health_filled.png` 144×8, `mana_filled.png` 144×8, `stamina_filled.png` 144×8, `hunger_filled.png` 144×8 — again exactly double the logical 73×4 (Δ1 px: one has 145×8, immaterial, see §17 asset note).
- Fill inset within the frame: `FILL_OFFSET_X = 9`, `FILL_OFFSET_Y = 2` — the fill region starts 9px right / 2px down from the frame's own top-left, leaving a 1px margin on the fill's right/bottom (`9 + 73 = 82`, one pixel short of `BG_WIDTH = 83`; `2 + 4 = 6`, two pixels short of `BG_HEIGHT = 8`, split unevenly — 2px top margin, 2px bottom margin, since the frame is 8px tall and fill is 4px starting at y=2).
- **No `.mcmeta` scaling file exists for any HUD sprite** (`bar_background.png`, `*_filled.png`, `rage_pip*.png`) — confirmed by directory listing. With no `nine_slice`/`tile` scaling block declared, Minecraft's `GuiSpriteScaling` defaults to plain **stretch**: the entire source image is resampled to fill the destination rectangle. There is **no texture-independent fill region** and **no nine-slice** — see §21 for what this means for resizing.
- Left-column anchor: `leftX = 6` (constant, left screen edge).
- Right-column anchor: `rightX = screenW - BG_WIDTH - 6` (Food frame's left edge).
- Vertical stack (bottom-up): `staminaY = screenH - BOTTOM_MARGIN - BG_HEIGHT` (`BOTTOM_MARGIN = 2`); `manaY = staminaY - BAR_SPACING - BG_HEIGHT` (`BAR_SPACING = 3`); `hpY = manaY - BAR_SPACING - BG_HEIGHT`. Food uses `hpY` (same row as Health, right side).
- Left-bar text origin: `x = frameX + BG_WIDTH + 4`, `y = frameY + (BG_HEIGHT - font.lineHeight) / 2` (vertically centered in the frame).
- Right-bar (Food) text origin: `x = frameX - textWidth - 4` (right-aligned ending 4px left of the frame), same Y centering.
- AC label: `x = leftX = 6`, `y = hpY - font.lineHeight - 2` (directly above the Health bar), color `0xFF00CCFF`, drawn via `graphics.text(..., true)` (drop-shadow enabled).
- Secondary resources (Rage pips): drawn starting at `y = hpY + BG_HEIGHT + BAR_SPACING` (just below the Food bar), right-edge-anchored at `rightEdgeX = screenW - 6`; pip size `10×10`, gap `2`, one row per registered resource, `y` advances by `pipSz + 3` per row for PIPS or `barH + 3` for BAR-type.

## 12. Offhand/hotbar conflict analysis

**[SOURCE]** confirmed facts:
- The offhand item slot is rendered entirely by vanilla's `HOTBAR` HUD element (part of the same hotbar draw call as the main hand's 9 slots) — Totality never replaces, moves, or hides `VanillaHudElements.HOTBAR` (§9). Offhand rendering is unmodified vanilla.
- **[HYPOTHESIS]**, general Minecraft/Fabric knowledge not re-derived from decompiled vanilla source in this pass: vanilla renders the offhand slot immediately adjacent to one side of the 182px-wide hotbar core, on the side opposite the player's configured main attack arm (default: main arm = Right → offhand renders to hotbar's **left**; if the player sets Main Hand = Left in Options, offhand renders to hotbar's **right**). Because this is a per-player, changeable client option, **either** of Totality's two bar columns can be the one whose external text is nearest the offhand slot, depending on that setting — this audit does not assume a single fixed side.
- The bars' own **frames** are anchored far from center (`leftX = 6`; `rightX = screenW - 89`) and, at any GUI scale in common use, do **not** geometrically reach the horizontally-centered hotbar. **The conflict is specifically the *external current/max text*, not the bar frames themselves** — confirmed by the formulas in §11: left-side text starts at `x ≈ 93` and grows rightward (toward center) by the text's rendered width; right-side (Food) text starts at `x ≈ screenW - 93` and grows leftward (toward center) by its own width. Both are anchored near the screen edge but extend *toward* the horizontally-centered hotbar, and their reach is directly proportional to how many digits/characters the current/max text contains.
- Worked example at a plausible GUI-scaled width (**[HYPOTHESIS]**, since exact GUI scale in the screenshot is unknown): at `screenW = 480` (a common result of GUI Scale settings at 1920×1080), the hotbar's core spans roughly `screenW/2 - 91` to `screenW/2 + 91` = `149` to `331`. Left text starting at `x = 93` with a string like `"110 / 110"` (~9 characters, vanilla default font ≈4-6px/char with spacing ≈ 45-54px) ends around `x ≈ 138-147` — right at or just short of the hotbar's left edge (`149`). Food's text ending 4px left of its frame (`rightX = 391` for `screenW=480`) and extending left by the same ~45-54px starts around `x ≈ 333-342` — right at or just past the hotbar's right edge (`331`). **This is consistent with, but does not by itself prove, the user's observed crowding** — it is presented as a formula-derived, GUI-scale-dependent risk, not a pixel-exact reproduction of the screenshot.
- **Conclusion**: the bars themselves do not overlap the offhand slot; their *external numeric text* is the entire source of the reported crowding, and the risk scales directly with (a) GUI scale (higher scale → smaller `screenW` in GUI-space → hotbar edges closer to the bar text) and (b) how many digits the current/max values have.

## 13. Proposed embedded-number layout

**[RECOMMENDATION]**, not implemented. Move the current/max text **inside** the frame, replacing the external left/right text draws with a single centered (or frame-edge-aligned — see rationale below) text draw positioned within the existing `BG_WIDTH × BG_HEIGHT` frame, likely requiring the frame to grow to accommodate both the fill bar and legible text without collision (item 17 in the audit list — "whether the fill can obscure text"). Two concrete sub-approaches, both preserving the existing stretch-rendered single-image sprites (no nine-slice work required, per §21):
- **(a) Text overlays the fill, right-aligned within the frame, high-contrast color + shadow.** Simplest change (no new draw call ordering issue since text already draws with `true` for drop-shadow — see §11), but risks item 17 ("fill can obscure text") when the bar is near-full and the fill color is light, since text and fill share the same vertical band. A slightly darker semi-transparent backing strip under the text (partially answering item 18 — "no such backing exists today", confirmed by re-reading `drawBarSmooth`/`drawBarMirroredSmooth`: there is no darkening rectangle behind the text anywhere in current code) would mitigate this.
- **(b) Reserve a fixed non-filling right-hand region within the taller/wider frame for the numbers, visually separated from the fill by the frame's own border art**, similar to `MobHealthBarHud`'s existing in-bar text placement (`MobHealthBarHud` already draws "75 / 100"-style text centered *inside* its own HP bar, over the fill, using a plain white `0xFFFFFFFF` color with drop shadow and no backing — this is a directly comparable, currently-working precedent for "numbers inside a fill bar" elsewhere in the same codebase, worth reusing its exact text color/shadow choice for consistency).

## 14. Preferred target geometry

**[RECOMMENDATION]** — concrete values, with formulas (not fixed to 1919×1079) where the audit specifically asks for one:

- **Bar frame width**: keep the existing `166×16` native asset's headroom in mind; a modest, native-resolution-respecting increase from the current `83×8` logical size to **`96×12`** (a ~16% width, 50% height increase) stays well under half of the double-resolution native asset (`166÷2=83`, `16÷2=8` — so 96×12 is already *above* a clean 1:2 downscale of the existing PNG and would need the source art re-exported at higher native resolution to stay crisp; flagged as an implementation-time asset-export dependency, not just a constant change).
- **Bar frame height**: `12` (up from `8`) — the minimum increase that gives embedded text (line height ~8-9px at default font) comfortable vertical padding above/below without the frame feeling oversized.
- **Interior/fill width**: proportionally, keep the existing `9px` left inset and roughly a `1px` right margin: `interior width ≈ frameWidth - 10` (was `73` of `83`; at `96` that is `86`).
- **Interior/fill height**: `interior height ≈ frameHeight - 6` to keep the current 2px top / 2px bottom margins proportionally similar at a taller frame — at `12` that is `6` (up from `4`), still leaving headroom for the text.
- **Distance from screen edge**: keep `leftX = 6` / mirrored `6` on the right — this was never the source of crowding (§12) and does not need to change.
- **Distance from hotbar**: not a currently-modeled constant (the bars are Y-anchored relative to screen bottom/each other, not to the hotbar directly) — **[RECOMMENDATION]**: continue anchoring off screen bottom/previous bar rather than introducing a new hotbar-relative anchor, since the hotbar's own screen position already varies with GUI scale identically to the bars.
- **Vertical gap between left bars**: keep `BAR_SPACING = 3` — unrelated to the crowding problem and already visually adequate per the user's "fundamentally fine" framing.
- **Text baseline**: vertically centered within the (now taller) frame — formula `y = frameY + (frameHeight - font.lineHeight) / 2`, identical pattern to today, just against the new `frameHeight`.
- **Text horizontal alignment**: **[RECOMMENDATION]** right-aligned within the frame's interior (ending a fixed `~3-4px` from the frame's right edge for the left-side bars, and left-aligned similarly for the mirrored Food bar) rather than centered — right/left edge alignment keeps the number visually anchored to a consistent screen position frame-to-frame even as digit count changes (110/110 vs 78/110 vs a future 3-digit value), whereas centering would make the text visibly shift left/right as string width changes.
- **AC position relative to the left stack**: unchanged — directly above the Health bar (`hpY - font.lineHeight - 2`), `x = leftX`. The source already labels this "testing — replaced during HUD redesign" (§15), so this audit recommends leaving its exact position alone during the small cleanup and only shifting it vertically by whatever delta the taller Health bar frame introduces (i.e. `hpY` moves up as frame heights grow, and the AC label formula already tracks `hpY` automatically).
- **Food position relative to the right side**: unchanged anchor logic (`screenW - frameWidth - 6`), just against the new `frameWidth`.
- **Safe offhand clearance**: **[RECOMMENDATION]** formula, not a fixed pixel count (since offhand position depends on GUI scale and the player's Main Hand setting, per §12): ensure `leftTextEndX = leftX + frameWidth + textWidth("999 / 999") < screenW/2 - 91` and `foodTextStartX = (screenW - frameWidth - 6) - textWidth("999 / 999") > screenW/2 + 91` at the **smallest supported GUI-scaled width** the mod targets (this repository does not appear to define a minimum-supported GUI-scaled width constant — flagged as an open question, §40). Embedding the numbers inside the frame (§13) removes this constraint's dependency on text width entirely, which is the primary reason it is the recommended fix over merely repositioning external text.

## 15. AC findings

**[SOURCE]**: `AC 13`-style text is computed live, client-side only, by `TotalityHudRenderer.calculateClientAC(Minecraft)` — **not read from any synced Resource or server value**. It replicates the server's Armor Class formula independently: sums `VanillaArmorStats.PieceStats.ac()` across equipped Head/Chest/Legs/Feet, applies a Dex-modifier cap based on the heaviest equipped piece's `ArmorClass.ArmorType` (Light: full Dex; Medium: Dex capped at +2; Heavy: no Dex), falls back to Barbarian Unarmored Defense (`10 + STR + CON` mod) or plain `10 + Dex` mod when unarmored, adds `+2` for an equipped `ShieldItem`, and adds `ClientEquipmentManager.getAcBonus(uuid)` (ring/equipment bonuses). The source code's own comment explicitly labels this element **"testing — replaced during HUD redesign"** — i.e. its author already expects it to be temporary/placeholder, independent of this audit. Positioned directly above the Health bar, left-anchored, unchanged by this audit's recommendations beyond tracking the Health bar's new Y position.

## 16. XP/level findings

**[SOURCE]**: Totality does **not** touch vanilla's `EXPERIENCE_LEVEL` HUD element at all (§9) — the "long narrow dark bar above the hotbar" the user describes is, with high confidence, **vanilla's own XP bar + level number**, rendered unmodified. **[HYPOTHESIS]** (plausible, not screenshot-confirmed): it reads as a mostly-empty dark strip because the player's *vanilla* XP is low/zero — Totality's own RPG leveling is a wholly separate system.

Totality's own character level (`ClientStatsManager.getLevel()`) and XP bar are shown **only** inside the Character screen's Overview tab (`OverviewTab.drawLevelPanel`) — **confirmed absent from the HUD entirely**; there is no in-HUD element for Totality's RPG level today.

## 17. Lower-right icon findings

**[SOURCE]**: the "two circular icons" are the **Rage secondary-resource pips**, rendered by `TotalityHudRenderer.drawSecondaryResources` using `TotalityGuiSprites.HUD_RAGE_PIP` / `HUD_RAGE_PIP_SPENT` (`hud/rage_pip.png`, `hud/rage_pip_spent.png`, both measured at 26×26 native pixels — the pip is drawn as a 10×10 destination rectangle, well within a clean downscale of the 26×26 source). **Directly viewing `rage_pip.png` confirms it is a round/circular glyph** (a red-and-orange circular gem/eye icon), not a square — matching "circular icons" exactly.
- Current value: only the Rage `ISecondaryResource` (registered in `TotalityClient.registerRenderers()`) is ever registered — there is exactly one secondary resource in production today, so "two icons" corresponds to a Barbarian whose Rage maximum is `2` (each pip = one Rage point, filled vs. spent per the `current`/`max` comparison in `drawSecondaryResources`).
- Warning states/animation/hover: **none found in source.** `drawSecondaryResources` only branches on filled-vs-not (two static sprites); no animation, no tooltip/hover text, no numeric label anywhere near the pips.
- Visibility logic: `shouldShow(client)` gates on `ClientClassManager.hasClass() && Barbarian`; additionally filtered at the call site by `r.getMax(client) > 0` — so it is hidden entirely for non-Barbarians or a genuinely-zero-max Rage pool, never shown as an empty/fabricated row.
- Overlap with Food bar/offhand: pips render at `y = hpY + BG_HEIGHT + BAR_SPACING` (a fixed row below Food, same right-edge anchor `screenW - 6`) — **geometrically below** the Food bar, not overlapping it vertically. Horizontal reach depends on pip count × 12px — for the observed 2-pip case this is only ~22px, far short of reaching the hotbar; **not currently a crowding contributor** for the case shown in the screenshots, though a class with a much larger Rage maximum would extend further left.
- Minor unrelated asset note (not part of any active render path): `hud/hunger_fill.png` exists in the resource tree alongside the actually-used `hud/hunger_filled.png` and is **grep-confirmed unreferenced by any Java source** — an orphaned duplicate asset, harmless but worth removing in a future cleanup pass (not this one).

## 18. Contextual Resource findings

**[SOURCE]**: "Contextual Resource" in this codebase = the `ISecondaryResource` system (§17), currently populated with exactly one entry (Rage). It coexists with the four main bars by rendering **below** them in the same right-hand column, using its own pip/bar layout independent of `BG_WIDTH`/`BG_HEIGHT` (pip size 10, gap 2 — separate constants from the main bars). This is Phase 3C-migrated (uses `ClientResourcePresentationResolver`, confirmed §30) and unaffected by the proposed embedded-number bar change (§13), since it doesn't share the main bars' frame/text code paths at all today. **[RECOMMENDATION]**: the small cleanup should simply preserve the existing `y = hpY + BG_HEIGHT + BAR_SPACING` relative anchor so secondary resources continue to sit immediately below the (now taller) Food bar without a separate migration.

## 19. Context interaction display findings

**[SOURCE]** — this is the "[Z] Harvest" system, fully traced:
- **Exact class/renderer**: `AbilityContextHud.render`/`renderPrompt`, called inline from `TotalityHudRenderer`'s render lambda (not its own `HudElementRegistry` layer).
- **Target detection**: **not performed by `AbilityContextHud` itself.** It asks the currently-**equipped** ability (`ClientAbilityManager.getEquippedAbility()`) for a context via the `ClientAbilityContext.getContext(Minecraft, LocalPlayer)` interface. For `HarvestAbility` specifically, that implementation raycasts using `Minecraft.hitResult` (the vanilla crosshair pick) and checks `HarvestRegistry.handlers()` for a handler whose `canHarvest(BlockState)` returns true; returns `null` (hides the prompt) otherwise.
- **Interaction registration**: `HarvestAbility implements ClientAbilityContext` and also extends the base `Ability` class (server-side `onActivate` lives in the same file).
- **Action priority**: `AbilityContextHud` only ever asks the single **equipped** ability slot — there is no ranking/priority among multiple candidate actions; whichever ability is equipped is the only one that can show a prompt.
- **Key-binding label generation**: **hardcoded literal `"Z"`** in `renderPrompt` — **does not read `ModKeybinds.USE_ABILITY`'s actual bound key.** `ModKeybinds.USE_ABILITY` does default to `GLFW_KEY_Z`, so the label is correct out of the box, but **[CONCRETE DEFECT — SOURCE-CONFIRMED]**: if the player rebinds "Use Ability" away from Z in Options → Controls, this prompt will continue to display the wrong key. This is the one genuine defect this audit found in the contextual-interaction system.
- **Generic or Harvest-specific**: fully generic — any `Ability` that is `Type.ACTIVE` and implements `ClientAbilityContext` gets this same prompt treatment; Harvest is simply the one currently equipped in the screenshot.
- **Multiple actions/choices**: not supported — single equipped-ability slot only, no list/menu.
- **Hold actions/progress**: no hold-progress visualization in `AbilityContextHud` itself (a plain static text draw every frame the context is non-null).
- **Server validation**: **yes, confirmed** — `HarvestAbility.onActivate(ServerPlayer, AbilityContext)` independently re-resolves the `BlockState` at the given `BlockPos` server-side and re-checks `HarvestRegistry.handlers()` before calling `handler.harvest(...)`; the client-side prompt/context is advisory only.
- **Visibility clearing on target change**: implicit and per-frame — the whole prompt pipeline re-evaluates every render call; when the ability's `getContext` returns `null` (target no longer valid), the prompt simply stops drawing that frame. No stale-state bug found.
- **Conflicts**: `AbilityContextHud` draws at `y = screenH/2 + 16` (just below the crosshair) — no direct geometric conflict found with the Mob Display (top-center), Notifications (top-left), or `MagicContextHud`'s Grimoire display (`screenH - 48`, near the hotbar). Not evaluated against vanilla subtitles/spell-targeting UI in this pass — **[HYPOTHESIS]**, not exhaustively checked.
- **Future foundation for multiple carcass actions**: none exists today — `AbilityContext`/`ClientAbilityContext` model exactly one action per equipped-ability slot; a multi-choice interaction (e.g. several carcass-processing options) would need a new data shape (a list of contexts, not one) and a new renderer, not an extension of the current single-prompt code.

**[RECOMMENDATION]**: per the task's own framing, leave the visual presentation of this prompt unchanged in the small cleanup — the user has stated it is acceptable. The hardcoded `"Z"` label is a genuine, narrow, independently-fixable defect **[RECOMMENDATION]** worth a small follow-up (reading `ModKeybinds.USE_ABILITY.getTranslatedKeyMessage()` or equivalent instead of the literal string) — but it lives in a different file (`AbilityContextHud.java`) than the four-bar geometry work and is not proposed as part of this cleanup's scope; left for the user to decide whether/when to schedule it.

**[RECOMMENDATION]** Future owning APIs (not implemented now):
- **Generic Interaction API**: a multi-choice, priority-ranked successor to the current single-equipped-ability model — needed before "several actions on one carcass" is possible.
- **Hunting API / Carcass Processing API**: would own the actual harvest/processing action set and their individual `AbilityContext`-equivalents.
- **Container interaction / Dialogue interaction / Companion interaction**: each would need their own context-prompt population if/when they want a crosshair prompt at all — none currently route through `AbilityContextHud`.

## 20. Mob Display architecture

**[SOURCE]**: `MobHealthBarHud`, registered via `HudElementRegistry.addLast` (render) + a separate `ClientTickEvents.END_CLIENT_TICK` registration (`MobHealthBarHud.tick()`, decrementing `combatTimer`). Two independent target-tracking mechanisms feed a single `getDisplayTarget` resolver:
- `crosshairTarget`: recomputed **every render frame** from `mc.crosshairPickEntity`, restricted to `LivingEntity` that is not a `Player`, within `24` blocks.
- `combatTarget`: set externally by `MobHealthBarHud.onPlayerHitMob(LivingEntity)`, called from three sites — `MinecraftAttackMixin` (client-predicted attack, ×2 injection points) and `CombatTextClientHandler` (server-confirmed hit via `CombatTextPayload`, only when the payload's attacker is the local player). Persists for `COMBAT_DISPLAY_TICKS = 80` ticks (4 seconds), decremented by `tick()`, cleared to `null` when the timer expires.
- `getDisplayTarget`: **combat target takes priority** over crosshair target whenever it is set, alive, timer > 0, and within 24 blocks — regardless of whether the player is still looking at it. Falls back to `crosshairTarget` otherwise.

## 21. Compact Mob Display behavior

**[SOURCE]**: the compact (name-only) state is what renders whenever `showBars` is false. `showBars = inCombat || perception >= 1`; `getPerceptionMasteryLevel()` is **hardcoded to return `0`** (`// TODO: read from ClientMasteryManager`), so today `showBars` is equivalent to `inCombat` alone — the compact state is simply "target acquired but not currently in combat." Compact panel is sized `NAME_H + PANEL_PAD_Y*2` tall, width clamped `80-160px` based on measured name width + 24px padding.

## 22. Expanded Mob Display behavior

**[SOURCE]**: appears when `inCombat` is true (§20's `isInCombat`: target is the active `combatTarget` with time remaining, OR the target is a `Mob` whose AI target is *some* `Player` — not necessarily the local one, OR `target.getLastHurtByMob() instanceof Player`). Adds: a smoothed (`SmoothValue`, 150ms) HP fill bar (`BAR_H = 9`), current/max HP text centered *inside* the bar (not external — already the pattern §13 recommends for the main bars), computed through the shared `RpgDisplayUtils.toDisplayHp` (the same ×5-style Health formatter the player's own bar uses — a source comment explicitly documents this was a correction from a previous hardcoded `* 5`), a rank suffix (`showRank = inCombat`, appending `" [" + MobRank name + "]"` — this is exactly the "old E–Z rank display" the user's future design intends to replace; **note**: the live `MobRank` enum today only defines **six** members, `E, D, C, B, A, S` — there is no `Z` member in current source, despite the shorthand "E–Z" — see §24), and, when `mobData != null`, an "AC ##" line (`showAc = inCombat`, comment: `// testing — gate with perception mastery later`).

## 23. Mob Display target lifecycle

**[SOURCE]**: `crosshairTarget` clears itself every frame it's not the vanilla-picked entity (no staleness possible). `combatTarget` clears only via the 80-tick timer or `!isAlive()`/`>24` blocks distance checks inside `getDisplayTarget` — **[SOURCE-CONFIRMED GAP]**: there is no explicit invisibility, disguise, or "can the player currently see this entity" check anywhere in `getDisplayTarget`/`isInCombat` beyond the distance and alive checks; an invisible-but-alive `combatTarget` within 24 blocks would continue to display for the remainder of its 4-second window. **[HYPOTHESIS]**: this has not been observed at runtime in this audit and may be inconsequential in practice (a 4-second window is short), but it is a real gap in the source logic as written, not merely a theoretical one.

## 24. Mob Display data ownership

**[SOURCE]**: `mobData` comes from `MobStatsClientCache.get(entityId)` — a simple `Map<Integer, MobClientData>` populated by a (not traced further in this HUD-scoped audit) server sync payload, holding `level, rankOrdinal, ac, rarityOrdinal`. `MobRank` (`api/mob/stats/MobRank.java`) is a plain 6-member enum (`E, D, C, B, A, S`) each carrying only a display `color` — no stat/spawn/loot coupling found in this enum itself (consistent with the user's future-design requirement that an authored Rank stay a separate visual label). `SpawnRarity` is a separate enum (used for the name-prefix logic — "rare phenotype"-style distinction already exists structurally as a *different* enum from `MobRank`, consistent with the user's stated intent to keep "rare phenotype, Apex-like variant, Predator-like encounter state" as separate concepts, though this audit did not fully enumerate `SpawnRarity`'s members).

## 25. Combat feedback/notification findings

**[SOURCE]**: two structurally distinct systems exist, both worth documenting separately since the task's framing (and the screenshot description) refers to a single "top-left combat feedback":

- **`NotificationManager`** — genuinely top-left-anchored (`PADDING_X = 4, PADDING_Y = 4`), stacks up to `MAX_NOTIFICATIONS = 5` lines, `LINE_HEIGHT = 11`, wraps to `min(180, guiWidth - 4 - 8)` px via `Font.split` (never a raw character-count wrap), 80-tick lifetime with a 20-tick linear fade tail, no fade-in. Populated by `NotificationManager.add(...)`, called from a client packet handler on `SendNotificationPayload` (not traced further, out of this audit's HUD-file scope). Has zero coupling to `MobHealthBarHud`'s geometry by explicit design — its own source comment states the 180px preferred width is "independent of MobHealthBarHud (which can reach ~320 scaled pixels near top-center)," i.e. a *prior author was already aware of this exact potential collision* when choosing the notification width, though the comment addresses width only, not the vertical (Y) overlap risk analyzed in §29.
- **`CombatTextRenderer`** — **not** a fixed-position system; it projects each `CombatTextEntry`'s **world position** (the position of the entity/location the combat event occurred at, rising by `life * 1.2` blocks over its lifetime) into screen space via the camera's view/projection matrices, captured through `GuiExtractRenderStateMixin`. It will visually appear wherever that world point projects to — which, for a mob near the top-center Mob Display, could plausibly land near the top of the screen, but this is inherently scene-dependent, not a fixed anchor.
- **Given the screenshot's plain description ("Top-left combat feedback")**, `NotificationManager` is the better source-grounded match for a *reliably* top-left element — **[HYPOTHESIS]**, since the screenshots were not directly inspected, this attribution is not certain; `CombatTextRenderer` cannot be ruled out for that specific frame.
- **Collision with AC or other HUD elements**: `NotificationManager` (`x` starting at 4, up to 5 lines × 11px = up to ~59px tall from y=4) sits in the extreme top-left corner. `AC`'s label is bottom-left (tracks `hpY`, near screen bottom), so **no AC collision**. The more relevant collision is with `MobHealthBarHud` — see §29.
- **Wrapping/lifetime ownership**: both fully owned by `NotificationManager` itself (pure, unit-tested — `NotificationManagerLayoutTest`, `NotificationWrappingSourceRegressionTest`); this audit's cleanup does not need to touch either.
- **[RECOMMENDATION]**: leave both systems unchanged for the small cleanup; the four-bar embedded-number change does not alter their geometry, and no defect was found in either.

## 26. Vanilla HUD replacement/coexistence findings

**[SOURCE]**, consolidated from §9: only `HEALTH_BAR`, `ARMOR_BAR`, `FOOD_BAR` are replaced (no-op). `AIR_BAR` (vanilla breath bubbles), `HOTBAR`, `EXPERIENCE_LEVEL`, `BOSS_BAR`, `MOB_EFFECTS`, `MOUNT_HEALTH`, and every other vanilla element remain fully vanilla, unmodified, and un-coordinated with Totality's own HUD layers (no code anywhere reads or reacts to `BOSS_BAR`'s presence — confirmed by a zero-result grep). This is a direct, source-confirmed collision candidate for the Mob Display specifically (§29).

## 27. GUI-scale findings

**[SOURCE]**: every constant in `TotalityHudRenderer`, `MobHealthBarHud`, `NotificationManager`, and the context-HUD classes is expressed in **GUI-scaled pixels** (`graphics.guiWidth()`/`guiHeight()` or `client.getWindow().getGuiScaledWidth()/Height()`), the same coordinate space vanilla's own HUD uses — so all of Totality's HUD already scales consistently with the user's chosen GUI Scale setting, including relative to the hotbar (which is also GUI-scaled). No element was found using raw framebuffer pixels. This means the crowding risk in §12 is driven by GUI Scale (smaller `guiWidth` at higher scale settings brings the centered hotbar closer to the edge-anchored bars in GUI-space), not by physical display resolution directly.

## 28. Resolution findings

**[SOURCE/RECOMMENDATION mix]**: because every layout formula in scope is a function of `guiWidth`/`guiHeight` (never a hardcoded `1920`/`1080`), the geometry findings and recommendations in §11-14 generalize to any resolution at any GUI Scale — this audit deliberately expressed all recommended values as formulas relative to `screenW`/`screenH`/existing constants rather than fixed numbers tied to 1919×1079, per the task's explicit instruction.

## 29. Collision matrix

**[SOURCE for geometry/registration facts; RECOMMENDATION for mitigation]**

| Elements involved | Current likelihood | Severity | Small-cleanup mitigation | Future owning system |
|---|---|---|---|---|
| Left bars' external text vs. offhand/hotbar left edge | Medium-High at high GUI scale or long text (§12) | Medium (visual only, no gameplay impact) | **A** — embed text inside frame (§13) | — |
| Food bar's external text vs. offhand/hotbar right edge | Medium-High at high GUI scale or long text (§12) | Medium | **A** — embed text inside frame (§13) | — |
| `NotificationManager` (top-left, up to ~184×59px) vs. `MobHealthBarHud` panel (top-center, can span down to `x ≈ screenW/2 - 120` at its 240px-wide in-combat width) | **Geometrically plausible at common GUI scales** — derived directly from both classes' own formulas, not screenshot-confirmed | Medium (Mob Display draws *after* Notifications in registration order, §9, so it would visually paint over notification text where they overlap) | Out of scope for the four-bar cleanup (different files); **B** — leave both unchanged this pass, flag for the future Mob HUD API | Mob Health/Mob HUD API |
| `MobHealthBarHud` (top-center, `PANEL_TOP_Y=6`) vs. vanilla `BOSS_BAR` (top-center, vanilla-owned, zero coordination in source) | **Confirmed zero coordination exists** (§26) — actual overlap depends on whether a boss-bar-having entity is ever also the crosshair/combat target, not verified at runtime this pass | Potentially high (both are opaque top-center panels) | **C** — defer; needs the future Mob Health/Mob HUD API to own boss-bar-aware layout | Mob Health/Mob HUD API |
| `AbilityContextHud` (`screenH/2+16`) vs. vanilla subtitles/spell targeting UI | Not verified this pass | Unknown | **B** — leave unchanged, no defect found in this pass | — |
| Secondary-resource pips vs. Food bar | Low — pips render strictly below Food's row, and only reach ~22px wide at the observed 2-pip case (§17) | Low | **B** — no change needed | — |
| Multiple notifications (5 stacked, ~59px tall) vs. `AbilityContextHud`/`MagicContextHud` | Low — both context prompts sit lower on screen (`screenH/2+16`, `screenH-48`) | Low | **B** | — |
| A wider `MobHealthBarHud` panel (extra-bars state, currently unreachable — §8) vs. Notifications | Currently **zero** (dead/unreachable code path) | N/A while unreachable | **B** — no action; already inert | Mob Health/Mob HUD API, if this path is ever revived |

This audit did not find evidence requiring a general layout-collision engine — the matrix above is small and each row has a clear, narrow owner; per the task's own framing, a complex layout engine is not justified by what was found.

## 30. Resource Phase 3C boundary verification

**[SOURCE]**, re-confirmed directly against current `HEAD` (not assumed from the prior task's memory):
- Mana HUD value: `TotalityHudRenderer.java:121-125` calls `ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.MANA, ...)`. **Confirmed.**
- Stamina HUD value: `TotalityHudRenderer.java:116-120`, same pattern with `PlayerResourceIds.STAMINA`. **Confirmed.**
- Rage/secondary-resource presentation: `TotalityClient.java`'s `ISecondaryResource.getCurrent/getMax` call `ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.RAGE, ...)`. **Confirmed** — this is the completed Phase 3C path, unchanged by this audit.
- FRESH/PENDING_RESYNC: unchanged — `ClientResourcePresentationResolver.resolveScalar` still branches only on the query result's Java type (`Scalar` vs `Unavailable`), never on the `trust` field, so both trust levels are still treated identically and preferred over legacy fallback (re-read from `ClientResourcePresentationResolver.java`, unmodified since Phase 3C finalization).
- Legacy fallback: still present and unmodified — `ClientStaminaManager`/`ClientManaManager`/legacy Rage readers are passed as the resolver's fallback suppliers, unchanged.
- No fabricated 0/0: unchanged — the resolver's `Unavailable` branch still defers to the caller-supplied legacy supplier, never a hardcoded literal.
- Health: **[SOURCE]** `TotalityHudRenderer.java:159-165` — uses `PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.HEALTH)` (the *server-shared* façade, not `ClientResourcePresentationResolver`), confirming Health is still native-backed and outside the Phase 3C Generic-view migration scope, exactly as required.
- Food: **[SOURCE]** `TotalityHudRenderer.java:192-198` — same `PlayerResourceService.INSTANCE.query(player, PlayerResourceIds.FOOD)` pattern. Native-backed, confirmed.
- No Resource mutation/packet calls: grep-confirmed `TotalityHudRenderer.java` contains only `.query`/`resolveScalar` calls — no `apply`, `send`, `spend`, `restore`, or packet construction anywhere in the file.
- `TotalityMovementHandler`: grep-confirmed zero references to `ClientResourcePresentationResolver` or `ClientResourceService`; still reads `ClientStaminaManager.getStamina()` directly for its three gameplay gates (Power Sprint eligibility/guard, Super Leap guard), unchanged.

**This audit proposes no change to any of the above.** The embedded-number layout recommendation (§13) only touches *where* the already-resolved `current`/`max` numbers are drawn on screen — it does not touch how those numbers are obtained.

## 31. Health native-authority verification

See §30 — confirmed native-backed via `PlayerResourceService` + `RpgDisplayUtils.toDisplayHp`/formatter fallback, bar fill still reads `client.player.getHealth()/getMaxHealth()` directly (unchanged, native ratio, not routed through any Resource query for the *fill percentage* — only the displayed number goes through the formatter/fallback path).

## 32. Food native-authority verification

See §30 — confirmed native-backed. Bar fill still reads `client.player.getFoodData().getFoodLevel()` directly (native 0-20 ratio, `hungerPct = hunger / 20.0` — **unchanged, still the native scale**, confirming Food 0-100 has not been started anywhere in current source). Displayed number goes through the same `PlayerResourceService` + formatter/fallback pattern as Health, using the `ResourceDisplayConversion.HEALTH_FOOD` factor (**[SOURCE]** read directly: `new ResourceDisplayConversion(5, 1)` — a native-to-display multiplier of ×5, giving the 20→100 scale seen in the screenshot's "100/100" Food reading), applied only at the display-formatting layer, never to the underlying native 0-20 value or the fill ratio.

## 33. Exact small-cleanup recommendations

**[RECOMMENDATION]** — decision **A** (change in the small HUD cleanup):
1. `TotalityHudRenderer`'s four main bars (`drawBarSmooth`/`drawBarMirroredSmooth`): grow the frame per §14, move current/max text inside the frame per §13, using the `MobHealthBarHud` in-bar-text pattern as the closest existing precedent for color/shadow treatment.
2. Delete the two now-fully-dead legacy helpers `drawBar`/`drawBarMirrored` (confirmed unused, §10) **only if** the cleanup pass touches this file anyway — not a prerequisite, but a natural small tidy-up while already editing the same methods' siblings. (Left as an option, not a requirement, since the task scope is bar geometry, not general cleanup.)

## 34. Exact elements to leave unchanged

**[RECOMMENDATION]** — decision **B**:
- `AbilityContextHud` visual presentation ("[Z] Harvest") — user-confirmed acceptable; the one defect found (hardcoded "Z" label) is independently schedulable, not bundled here.
- `MagicContextHud` (Grimoire slot display).
- `NotificationManager` and `CombatTextRenderer` (no defect found; no coupling to the bar-geometry change).
- `RestHud`, `QuestTrackerHud`, `PowerAttackFlash`, `HeatVisionBeamRenderer`, `CastBarHud` — not in scope, no finding against any of them.
- Secondary-resource pips (Rage) — geometry already independent of the four bars' frame change (§18).
- AC's exact formula/position (only its Y-tracking of `hpY` moves automatically).

## 35. Exact responsibilities deferred to owning APIs

**[RECOMMENDATION]** — decision **C**:
- `MobHealthBarHud`'s rank display (`MobRank` E-S text) → replaced by the future authored visual threat tier (Common…Ancient) under the future Mob Health/Mob HUD API — not implemented now.
- `MobHealthBarHud`'s dead "extra bars" (Mana/Poise) reservation and its `getPerceptionMasteryLevel()` stub → resolved when a real Mastery API and/or Mob HUD API exists; currently inert, no action needed today beyond documentation.
- `MobHealthBarHud`'s complete lack of `BOSS_BAR` coordination → owned by the future Mob Health/Mob HUD API.
- `MobHealthBarHud`'s dead `getRank()`/`COLOR_BOSS` → candidates for deletion whenever the Mob HUD API rewrite happens; not touched now.
- A multi-choice contextual-interaction model (several carcass actions) → Generic Interaction API / Hunting API / Carcass Processing API, per §19.
- `AbilityContextHud`'s hardcoded key label → a narrow, independent fix; not assigned to any specific future API, just flagged.

## 36. Exact responsibilities deferred to full redesign

**[RECOMMENDATION]** — decision **D**:
- Any new art assets beyond what the existing double-resolution PNGs already support without upscaling (§14).
- Any animation beyond the existing `SmoothValue` fill-lerp (already present, unchanged).
- A general HUD layout/collision engine (§29 explicitly found this unnecessary given the small number of real collision candidates).
- Repositioning bars relative to the hotbar with a new anchor system, changing bar *count*, or changing which bars exist.
- Thirst/Temperature bars (already stubbed as `// TODO` comments in source, not started).

## 37. Likely production files for a future implementation pass

**[RECOMMENDATION]**, based on this audit's file inventory:
- `src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java` (bar geometry, primary target).
- `src/main/java/zcylas/totality/client/gui/TotalityGuiSprites.java` (only if new/re-exported sprite identifiers are needed).
- `src/main/resources/assets/totality/textures/gui/sprites/hud/*.png` (only if native resolution needs re-export beyond the existing double-resolution headroom, §14).
- Not expected to require changes: `TotalityClient.java`, `MobHealthBarHud.java`, any Resource-API file, any Tooltip file (all out of this cleanup's scope per §33/§34).

## 38. Likely tests for a future implementation pass

**[RECOMMENDATION]**: none of `TotalityHudRenderer`, `MobHealthBarHud`, `AbilityContextHud`, `MagicContextHud`, `SecondaryResourceHud`/`SecondaryResourceRegistry`, `RestHud`, or `CombatTextRenderer` have any existing test coverage today (grep-confirmed zero matches in `src/test/java` for any of these class names, aside from the unrelated Phase 3C presentation-resolver regression test which only references `TotalityHudRenderer`'s *source text*, not its runtime behavior). A future geometry-change pass would most naturally add:
- A small set of pure-function tests if the bar-geometry math is extracted into a testable helper (mirroring `NotificationManager.effectiveWidth`'s existing pure-function pattern) — e.g. "text fits within frame width for the longest expected current/max string," "frame dimensions stay non-negative at minimum GUI-scaled width."
- A source-regression sentinel test (mirroring `Phase3CConsumerMigrationSourceRegressionTest`'s established pattern) confirming Health/Food remain native-backed and the Resource-façade call sites are unchanged, since `TotalityHudRenderer`'s render lambda itself cannot be executed under plain JUnit (documented constraint, repeated across this codebase's existing `*SourceRegressionTest` files).

## 39. Risks

**[RECOMMENDATION]**:
- Growing the bar frame beyond the existing 2x-native-resolution asset headroom (§14) would blur the sprite on upscale — an asset-export dependency, not just a code change.
- The plain-stretch (no nine-slice) rendering means any non-uniform width/height change stretches the border art non-uuniformly; a future nine-slice conversion (out of this cleanup's scope) would be the more scalable long-term answer if further growth is wanted later.
- The `NotificationManager`/`MobHealthBarHud` top-of-screen collision (§29) is real per the formulas but was not runtime-verified; a future Mob HUD API pass should runtime-verify it, not assume it away.
- The invisibility/disguise gap in Mob Display target retention (§23) is a real source-level gap that has not been runtime-tested; low urgency given the short 4-second window, but should be tracked.

## 40. Open questions

**[RECOMMENDATION]**:
- Is there a defined minimum-supported GUI-scaled width for Totality's HUD? No such constant was found in source — needed to make §14's "safe offhand clearance" formula concrete rather than conditional.
- Should the small cleanup also fix `AbilityContextHud`'s hardcoded "Z" label, or is that explicitly out of scope for this pass? (§19)
- Should the orphaned `hud/hunger_fill.png` and the dead `drawBar`/`drawBarMirrored`/`SecondaryResourceHud`/`MobHealthBarHud.getRank`/`COLOR_BOSS` be deleted now or left for a dedicated cleanup pass? This audit found them but recommends no action beyond documentation, per the task's no-implementation instruction.
- Exact GUI scale and resolution used in the four screenshots — unknown; would sharpen the crowding analysis in §12 from formula-derived to pixel-exact if available.

## 41. Recommended implementation sequence

**[RECOMMENDATION]**:
1. Confirm/author the higher-resolution bar-background and fill sprites needed for the target frame size (§14), or confirm the modest increase chosen fits the existing double-resolution assets without re-export.
2. Change `TotalityHudRenderer`'s bar constants and the two `*Smooth` draw helpers to embed text inside the frame (§13), reusing the existing `SmoothValue`/fill-percentage logic untouched.
3. Re-run the existing full test suite (no HUD tests exist today, so this step primarily guards against an unrelated regression, not HUD-specific coverage).
4. Manual in-client visual validation at multiple GUI scales, with an offhand item equipped on both Main Hand settings, and with multi-digit current/max values, per §27/§28.
5. Leave Mob Display, contextual interaction, and Notification/CombatText systems untouched in this pass, per §34/§35.

## 42. Confirmation that no production or test code changed

**[SOURCE]**: this audit created exactly two new files (`Context/Audit/TOTALITY_HUD_AND_MOB_DISPLAY_AUDIT.md` and, after this report, the accompanying review bundle zip). No file under `src/main/java`, `src/main/resources`, or `src/test/java` was edited, created, or deleted.

## 43. Confirmation that Food 0-100 was not started

**[SOURCE]**: confirmed by §32 — `hungerPct = hunger / 20.0` (native 0-20 scale) remains unchanged in `TotalityHudRenderer.java`; no file touching `FoodResourceAdapter`, `ResourceDisplayConversion`, or any Food-scale constant was modified.

## 44. Confirmation that no full HUD redesign was started

**[SOURCE]**: no production HUD file was modified (§42). All redesign-scale items are explicitly deferred (§36) and were not begun.

## 45. Final repository-wide status

**[SOURCE]**: `git status --short`, captured after this report and its review bundle were written, is identical to §5's starting status **plus exactly two new untracked entries**:
```
?? Context/Audit/Review Bundles/TOTALITY_HUD_AND_MOB_DISPLAY_AUDIT_BUNDLE.zip
?? Context/Audit/TOTALITY_HUD_AND_MOB_DISPLAY_AUDIT.md
```
Every other line (the 22 modified generated JSON files, `build.gradle`, the 26 pre-existing untracked review bundles, the Tooltip/trade-screen screenshots, `log4j-dev.xml`, `logs/`, `src/main/generated/.cache/`) is byte-for-byte the same set as §5 — confirmed by direct comparison, not assumption. No production or test file (`src/main/java`, `src/main/resources`, `src/test/java`) appears anywhere in the status output.

## 46. Confirmation that nothing was committed or pushed

**[SOURCE]**: no `git add`, `git commit`, or `git push` command was run at any point during this audit. `git log -1` still reports `69717c6be85b891c6dea0fe7b0751cab4da73b3c Migrate client resource presentation consumers` as HEAD, identical to §3.
