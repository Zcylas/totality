# Tooltip Overhaul — Inspiration Audit for Totality Tooltip API V2

Date: 2026-09-23 · Scope: read / analyse / report only. No Totality code was changed.

**Label legend** (used throughout so nothing reads as a locked decision):

| label | meaning |
|---|---|
| **[TO]** | Observed Tooltip Overhaul behaviour (from the JAR: metadata, mixin config/refmap, `javap` bytecode, resources) |
| **[Interp]** | My interpretation of that behaviour (inferences, especially where intermediary names had to be decoded) |
| **[Suggest]** | Suggested inspiration for Totality — open for discussion, not decided |
| **[Totality]** | Existing, working or already-accepted Totality direction (from the repository or the task brief) |

Method: no decompiler exists locally and none was installed; the analysis uses `javap -c -p -v` over the
extracted classes (scratch directory, outside the repository), the mixin refmap (which preserves the
readable target names), string/float constant pools, and the bundled resources. Intermediary names from
1.21.5 were decoded from well-known vanilla identities; where that decoding is an inference it is marked
**[Interp]**. The mod was **not** run: it targets Minecraft 1.21.5 and would need a separate environment
and downloads; Totality's working setup and `run/world` were not touched.

---

## 1. Executive Summary

* **[TO]** Tooltip Overhaul (TO) 1.2.0 is a **presentation skin over vanilla's already-built tooltip**. It
  cancels vanilla's generic `GuiGraphics.renderTooltipInternal`, re-wraps the vanilla
  `List<ClientTooltipComponent>`, and redraws it through a fixed stack of visual *layers* (background,
  icon, text, divider, frames, animated effects, overlay) plus an optional **second side panel** holding a
  spinning item or an armor-stand preview. It has **no semantic content model**: it never knows what a
  "damage" or "durability" row is — it restyles lines vanilla (or other mods) already produced.
* **[Interp]** Its genuinely useful ideas for Totality are few but real:
  1. **data-driven selector → presentation mapping** (exact item / tag / whole namespace / all, loadable
     from config *and resource packs*, per-selector overrides of every visual knob);
  2. **category-selected previews** (the preview panel is chosen by data components: tool/weapon-like vs
     equippable), which matches Totality's "different semantic categories choose different presentation";
  3. **per-hovered-stack caching** of resolved style, with animation clock + scroll reset on change;
  4. a **layer/z-band** renderer decomposition;
  5. awareness that **GUI-item optimisers break rotated item previews** (it ships ModernFix/Flerovium
     compatibility).
* **[Interp]** Totality V1 is already **architecturally ahead** of TO in almost everything that matters
  for V2's content contract: semantic contributors → sealed section model → renderer; disclosure levels
  (Default/Shift/Ctrl) and knowledge visibility; content-driven eligibility; scroll viewport with identity
  and reset rules; JEI z-order deferral; structured-`TooltipComponent` preservation; compact-width policy.
  Nothing in TO justifies replacing working V1 infrastructure.
* **[Interp] Critical 26.2 finding for the model preview:** TO's preview technique (3D `PoseStack` rotation
  wrapped around the normal `GuiGraphics.renderItem`, and an armor stand drawn through
  `EntityRenderDispatcher.render`) **does not port to 26.2**. In 26.2 the GUI pose is a 2D
  `Matrix3x2fStack`, GUI items are pre-rendered into a `GuiItemAtlas` and blitted as 2D quads, and real 3D
  GUI content goes through **picture-in-picture (PIP) renderers**. Totality's Fabric API
  (0.161.0+26.2 → `fabric-rendering-v1` 25.3.3) ships a supported
  **`PictureInPictureRendererRegistry`**, so a true model preview has a clean path — but it is a different
  mechanism from TO's.
* **[Interp]** TO's side-panel preview widens the tooltip horizontally. That conflicts with Totality's
  accepted V1 principle *"scale vertically, not horizontally"* (see `Context/Audit/Image References/`).
  If V2 adopts larger previews, placement inside the vertical layout (header or a preview band) fits
  Totality better than a side panel.
* **Things to actively avoid:** cancelling vanilla's *generic* tooltip entry point for every tooltip,
  hovered-item *inference* heuristics, global static renderer state, hard-coded vanilla item ID lists,
  a constructed client-level entity in a static field, per-frame `ItemStack` copies, always-on animated
  background effects, and a very large free-form config surface.

---

## 2. Local Mod Identification

| field | value |
|---|---|
| Artifact | `Inspiration Mods/tooltipoverhaul-fabric-1.21.5-1.2.0.jar` (JAR only; no sources, notes or screenshots). `Inspiration Mods/` is git-ignored (`.gitignore:42`) |
| SHA-256 | `166f943134452db0f5982fa5a1c8c630e796e4290ef762c6e1ebf6cc814a9b14` |
| Name / id / version | "Tooltip overhaul" · `tooltipoverhaul` · 1.2.0 |
| Authors / license | Xylonity, ModderG · GNU GPLv3 (homepage/sources fields are Fabric example-mod placeholders) |
| Minecraft / loader | 1.21.5 · Fabric (loader ≥ 0.16.10, `fabric-api` any, Java ≥ 21). Built with Loom 1.10.5, intermediary mappings. A shared `TooltipPlatform` service (`META-INF/services`) indicates a multi-loader codebase with a Fabric platform implementation. |
| Entrypoints | client `dev.xylonity.tooltipoverhaul.TooltipOverhaulFabric`; `jei_mod_plugin` `compat.TooltipOverhaulJeiPlugin` |
| Bundled libraries | Night Config core + TOML (shaded under `dev/xylonity/tooltipoverhaul/nightconfig/`; manifest class-path `toml-3.6.6.jar core-3.6.6.jar`) |
| Mixins (client) | `AbstractContainerScreenMixin` (accessor `hoveredSlot`), `ClientTextTooltipAccessor` (`text`), `EntityRenderDispatcherAccessor` (invoker for private `render(Entity,…)`), `GuiGraphicsAccessor` (`bufferSource`), `GuiGraphicsMixin` + `GuiGraphicsItemMixin` (both `@Inject HEAD` into `GuiGraphics.renderTooltipInternal`, the former `cancellable`), `MouseHandlerMixin` (`@Inject HEAD cancellable` into `onScroll`), `ScreenRecorderMixin` (`@Inject TAIL` into `Screen.render`, EMI hover recording). `defaultRequire: 1`. |
| Config | TOML file via a reflection-driven `AutoConfig`/`ConfigEntry` wrapper with comment/default/range generation and a scheduled background thread (`TooltipOverhaul-ConfigSchedule`) |
| Data | `assets/tooltipoverhaul/tooltipoverhaul/custom_frames.json` (default frames) + `config/…/custom_frames.json` + frame files from resource packs |
| Assets | 18 overlay/frame PNGs + 1 GUI PNG, 10 lang files (only 4 keys: Common/Uncommon/Rare/Epic rarity names). Not extracted, not reused. |
| Compat | JEI (plugin + hover holder), EMI (deferred hover, screen recorder), FTB Quests (screen skip), Apotheosis (hook), ModernFix + Flerovium (item-render optimisations) |
| Rendering entry points | `GuiGraphicsMixin.enhancedtooltips$coreRenderer` → `TooltipWrapper.wrap` → `TooltipContext.of` → `TooltipRenderer.render(ctx)` → cancel vanilla |

Full identification, mixin/refmap, listing, class-signature, config/schema and asset inventories are in
the review bundle (`inventory/`).

---

## 3. Tooltip Overhaul Feature Inventory

### Visual structure
* **[TO]** One *main panel* drawn by layers in fixed order: `BackgroundLayer` → `IconBackgroundLayer` →
  `IconLayer` → `TextLayer` → `DividerLineLayer` → `InnerFrameLayer` → `EffectLayer` → `OverlayLayer`.
  Tooltips with no stack (buttons etc., when `SHOW_TOOLTIP_WITHOUT_STACK`) use Background → Text →
  InnerFrame.
* **[TO]** Z-ordering via a `LayerDepth` band enum: `BACKGROUND`, `BACKGROUND_INNER_FRAME`,
  `BACKGROUND_TEXT`, `BACKGROUND_RENDERS`, `BACKGROUND_EFFECT`, `BACKGROUND_OVERLAY`.
* **[TO]** Header: icon (with icon-background variants: focus, slot, slot-border, void), title and a
  "rating" line (vanilla rarity name, or a per-frame custom rating/colour); configurable X alignment and
  absolute offsets for title, rating and description.
* **[TO]** Divider line (configurable colour, can be disabled); inner-frame styles (glint, gradient,
  static overlays); textured outer frames (optionally animated vertical strips).
* **[TO]** Size: `TooltipRenderer.calculateSize(font, components, rating, …)` measures the vanilla
  component list + rating text; position clamped to the screen (`Math.max/min` against screen size).
* **[TO]** Scrolling exists (see §7). No footer concept.

### Item preview
* **[TO]** Header icon with appear animations (`barrel_roll`, `bounce`, `fan_in`), optional continuous
  spin, configurable size.
* **[TO]** **Second panel** (side panel) with either a *rotating item* (`DefaultRotatingItem`) or an
  *armor stand wearing the item* (`DefaultArmorStand`). Chosen by data components (§6).
* No entity/mob preview, no block-entity special handling, no per-model animation beyond what the item
  renderer itself does.

### Content
* **[TO]** None of its own. Attributes, enchantments, durability, food, lore, etc. appear **only as the
  lines vanilla/other mods already put in the tooltip**. `TooltipWrapper` re-wraps plain text lines
  (`ClientTextTooltip`) to a screen-derived max width and passes non-text components through.
* **[TO]** No key-hold advanced details; no Shift/Ctrl/Alt polling anywhere in the mod.

### Input / interaction
* **[TO]** Mouse-wheel scrolling of an overflowing tooltip, with velocity/inertia.
* No pinning, comparison, expand/collapse or alternate views.

### Rarity / theming
* **[TO]** Built-in styles `COMMON`, `UNCOMMON`, `RARE`, `EPIC` (from vanilla's 4-value `Rarity`), plus
  `LEGENDARY` and `CHAOS` reachable only via frame data; palette strings per style in config.
* **[TO]** Animated background effects: cinders, metal shining, rim light, ripples, sonar, stars
  (scissor + fills every frame; some keep particle object lists).

### Compatibility
* **[TO]** JEI/EMI hover integration (proxies to find the hovered ingredient when no slot is hovered),
  FTB Quests skip, Apotheosis hook, ModernFix/Flerovium item-render compatibility, creative inventory.

### Configuration
* **[TO]** ~35 global TOML options (toggles, positions, paddings, alignments, icon size/animation/speed,
  second-panel size/speed/offset, divider colour, per-rarity palettes, "disable scrolling", "show tooltip
  without stack", enable tiered/armor preview).
* **[TO]** `custom_frames.json` entries select by `items`, `tags`, `namespace`, or `*`/`all`, and override
  almost every global option per selector (`disableTooltip`, `showSecondPanel`, rating text/colour,
  positions, paddings, icon behaviour, divider, effects, gradient/border types, background colour,
  scrolling). Loaded from the config folder and from resource packs; duplicates overwrite with a warning.

### Performance
* **[TO]** Resolved style cached per hovered stack (`updateStyle`, compared with `ItemStack.matches`).
* **[TO]** Per frame: component re-wrap, size calculation, all layers, effects, and for previews an
  `ItemStack.copy()` (armor stand re-equipped every frame).

---

## 4. Rendering / Hook Architecture

Answers to the brief's architecture questions:

1. **Interception** — **[TO]** `@Inject(at=HEAD, cancellable)` into
   `GuiGraphics.renderTooltipInternal(Font, List<ClientTooltipComponent>, x, y, ClientTooltipPositioner,
   ResourceLocation)`; if `TooltipRenderer.render` returns true, vanilla is cancelled. This is vanilla's
   *generic* tooltip path — **every** tooltip in every screen passes through it.
2. **Replace vs append** — **[TO]** replaces the whole drawing; the *content* is still vanilla's list.
3. **Mixins** — yes, 8 (listed in §2).
4. **Hooked classes** — `GuiGraphics` (×2 + accessor), `AbstractContainerScreen` (accessor),
   `ClientTextTooltip` (accessor), `EntityRenderDispatcher` (invoker), `MouseHandler`, `Screen`.
5. **Dimensions** — **[TO]** measured from the vanilla component list + rating text + paddings; the second
   panel is a separately offset box (config/frame X/Y offsets), not part of the main panel's width.
6. **Internal content representation** — **[TO]** `List<ClientTooltipComponent>` (vanilla's
   post-formatting objects) + an optional `CustomFrameData`. No intermediate model.
7. **Generic content/component model** — **[TO]** no.
8. **Strongly typed sections** — **[TO]** no; only "title" vs "rest" vs "rating".
9. **Categories inferred dynamically** — **[TO]** only for the preview (data components) and style
   (vanilla rarity); otherwise by frame selectors.
10. **Interfaces/providers/registries** — **[TO]** `ITooltipLayer` + "bridge" interfaces per visual role
    (`ITooltipIcon`, `ITooltipRotatingItem`, `ITooltipArmorStand`, `ITooltipEffect`, …) assembled by a
    `TooltipStyleBuilder`; `Styles` holds six prebuilt styles. It is a *style* registry, not a *content*
    registry.
11. **Hardcoded item types** — **[TO]** the bundled frames are hand-curated vanilla item-ID lists (82 IDs,
    0 tags).
12. **Custom/modded items** — **[TO]** automatically restyled (they flow through the same vanilla path);
    customisation via frame JSON selectors (namespace/tag/item).
13. **Preview selection** — §6.
14. **Scrolling** — §7.
15. **Optional/advanced sections** — **[TO]** none.
16. **Layouts** — **[TO]** one layout, parameterised by config/frame offsets.
17. **Coupling of rendering and data** — **[TO]** there is no data extraction; rendering is coupled to
    vanilla's *already-rendered* representation (`ClientTooltipComponent`, `FormattedCharSequence`).
    `TooltipWrapper` even converts flattened char sequences back to formatted text to re-split them.
18. **Version fragility** — **[Interp]** high: `renderTooltipInternal` signature, `ClientTextTooltip.text`,
    `GuiGraphics.bufferSource`, the private `EntityRenderDispatcher.render` overload, `PoseStack`-based GUI
    posing, and 1.21.5 data-component names are all version-specific; most changed in the 1.21.6+ GUI
    render-state rewrite that 26.2 inherits.
19. **Heavy vanilla internals** — the hovered-item inference (below), accessors, and the 3D pose tricks.
20. **Poor scaling for Totality** — see §12.

**Hovered-item inference** — **[TO]** `renderTooltipInternal` receives no `ItemStack`, so
`GuiGraphicsItemMixin` *guesses* it: skip FTB Quests screens; if the screen is an
`AbstractContainerScreen`/creative screen (or JEI/EMI-like), read `hoveredSlot`; otherwise ask the EMI
proxy by mouse position, then the JEI proxy; store the result in a field on `GuiGraphics`.
**[Interp]** This is inherently heuristic: a tooltip for a *different* element drawn while a slot is
hovered can be mis-attributed.

**[Totality] contrast:** V1 hooks the container screen's own tooltip call
(`AbstractContainerScreen.extractTooltip` → `@Redirect` of `setTooltipForNextFrame`), where the stack is
*known* (`hoveredSlot`), opts in per item, falls back to vanilla for structured `TooltipComponent`s, and
defers its draw into the same top stratum vanilla uses (`GuiGraphicsExtractor.extractDeferredElements`
TAIL) — fixing JEI z-order without cancelling anything globally.

---

## 5. Content Architecture

* **[TO]** Content = vanilla component list. Layers read `TooltipContext.getComponents()` and draw them;
  the title is the first component, the "rating" is computed (`computeRating`, vanilla rarity name or
  frame override), and a "description" position exists for the remaining lines.
* **[TO]** Per-item customisation is *visual* only (frame data), never content.
* **[Interp]** Because TO has no content model, every advanced Totality need — primary vs secondary stats,
  Shift provenance, knowledge-gated rows, block vs item durability distinctions, lore placement — is
  simply outside its design. That is not a flaw for TO's purpose (skin any modpack), but it means **TO
  offers no content-architecture lessons beyond "keep presentation data-driven."**
* **[Totality]** V1 already has the content contract TO lacks: `TooltipContributor` →
  `TooltipDocument` of sealed `TooltipSection` records (`Header`, `RarityBadge`, `ClassificationBadges`,
  `Heading`, `StatRow`, `StatBlock`, `IconStatRow` with item/effect icons, `ProgressBar`,
  `PropertyBadges`, `Description`, `Requirement`, `ExternalContent`, `TechnicalInfo`, `ProvenanceGroup`),
  each carrying `visibility` and `minDisclosure`; renderer-owned footer; contributors
  (`Metadata`, `Weapon`, `MiningTool`, `BlockDurability`, `Energy`, `Fuel`, `Attunement`, `Weight`,
  `Grimoire`, `HealingPotion`, legacy adapter, external content, technical info).

---

## 6. Large Item / 3D Model Preview Deep Dive

### What TO does
* **Selection — [TO]** `TooltipRenderer.render` shows the second panel when the frame data sets
  `showSecondPanel`, or when the stack's *item default components* contain one of two data components,
  each gated by a config toggle: one tied to `TIERED_ITEMS_RENDERER` and one tied to
  `ARMOR_ITEMS_RENDERER`. **[Interp]** From the config names and the armor-stand code (which reads the
  *same* component's `slot()` to equip the stand), the armor one is `EQUIPPABLE`; the tiered one is a
  tool/weapon component (`TOOL`/`WEAPON`) — the exact 1.21.5 intermediary field could not be decoded
  without mappings.
* **Rotating item — [TO]** `DefaultRotatingItem` calls the **ordinary GUI item renderer**
  `GuiGraphics.renderItem(stack, x, y)` inside pose transforms: translate to the panel centre and the
  layer's z band; `Axis.YP` rotation by `Util.calcRotY(time)` (continuous spin, period from config/frame,
  default constant 8000 ms); a fixed −45° Z tilt; uniform `scale(SCALE)`; translate(−8, −8, −150) to
  centre the 16×16 item. So it reuses **the baked GUI item model and GUI display transform** — block
  items therefore show their GUI block model; "generated" (flat) items show their extruded sprite, which
  goes edge-on and thin during the spin. No separate model, no render target, no scissor.
* **Armor stand — [TO]** `DefaultArmorStand` lazily builds one `ArmorStand` against
  `Minecraft.getInstance().level` and keeps it in a **static field**; sets arms/base-plate/visibility
  flags; **every frame** clears all equipment slots and equips `stack.copy()` into the slot named by the
  item's equippable component; applies X 30°, Y −45° + spin, scale; sets up entity lighting (a vanilla
  `Lighting` call) and renders via the private `EntityRenderDispatcher.render(...)` invoker into
  `GuiGraphics`'s buffer source at the layer's z band.
* **Header icon — [TO]** `DefaultIcon` renders the item with appear animations and optional spin,
  configurable size.
* **Overlap — [TO]** avoided by placing the preview in its own offset panel (config/frame X/Y), not by
  clipping; text never shares the preview's space.
* **Optimiser compatibility — [TO]** `ModernFixCompat` checks ModernFix's
  `mixin.perf.faster_item_rendering` and Flerovium's `itemBackFaceCulling` and pushes/pops a "return
  original render" mode around the preview draw. **[Interp]** Those optimisers cache flat items or cull
  back faces, which breaks a *rotated* item — a real, general risk for any spinning-item preview.

### Why it does not port to 26.2 — **[Interp]**, verified against the 26.2 jar and Fabric API
* 26.2's `GuiGraphicsExtractor.pose()` is a 2D `org.joml.Matrix3x2fStack` — there is no GUI 3D pose.
* GUI items (`graphics.item(...)`) go through `GuiItemAtlas`/`DynamicAtlasAllocator`: rendered once into a
  texture atlas, then drawn as 2D quads. Scaling such an icon is a 2D scale of a pre-rendered image.
* 3D content in GUIs uses **picture-in-picture** renderers (`net.minecraft.client.gui.render.pip`):
  `GuiEntityRenderer` (behind `graphics.entity(EntityRenderState, scale, translation, rotation, …)` with
  quaternion rotations), `OversizedItemRenderer`, `GuiBookModelRenderer`, `GuiBannerResultRenderer`,
  `GuiSkinRenderer`. A PIP renderer draws into an off-screen texture (`renderToTexture`) that is then
  blitted into the GUI.
* Totality's Fabric API 0.161.0+26.2 bundles `fabric-rendering-v1` 25.3.3 containing
  **`PictureInPictureRendererRegistry`** — a supported way to register a custom PIP renderer.

### Useful concept for Totality — **[Suggest]**
| conceptual mode | 26.2 mechanism (to validate in a spike) | cost | notes |
|---|---|---|---|
| standard sprite | existing `graphics.item(stack, x, y)` (V1 header today) | lowest | default for materials, ingredients, food, simple items |
| large sprite | same atlas item with a 2D pose scale in the header | low | **open question:** atlas resolution vs. GUI scale → possible softness/pixelation at 2–3×; test before committing |
| item model | custom PIP renderer (registered via Fabric) rendering the item model with a chosen transform/rotation | medium | true 3D; weapons/tools/equipment/machines; must handle optimiser caveats TO discovered |
| block model | same PIP path, block-item/GUI transform or block-state model | medium | blocks, machines |
| equipment preview (later) | `graphics.entity(...)` with an `EntityRenderState` (vanilla PIP), not a live entity | medium-high | avoid TO's static client-level `ArmorStand` |

* Selection should come from **semantic classification/profile data** (Totality `ClassificationsComponent`
  / tooltip profile / tags), with exact-item override — the same idea as TO's component-based selection,
  but through Totality's own semantics.
* Rotation should be **optional and calm** (static 3/4 view by default; spin only where a design decision
  wants it), and never required to read information.
* Place the preview **inside the vertical layout** (header or a dedicated preview band above the stats),
  so the "scale vertically" principle holds; reserve its height in layout measurement.

---

## 7. Scrolling / Input Behaviour

**[Totality] V1 already has scrolling** (`TooltipScrollController`, `TotalityTooltipScrollHandler`,
`MouseHandlerMixin.onScroll` HEAD-cancel only when a Totality tooltip is visible *and* overflowing; header
and footer fixed, body clipped by a scissor viewport; slim scrollbar; reset on item change, re-clamp on
disclosure change, reset on screen close; target identity = screen + slot reference).

**[TO]** `TooltipScrollState` is global static state with `scroll`, `maxScroll`, `velocity`,
`accumulated`, `lastNs`; `onRawScroll` adds impulse, `tick()` integrates velocity with a nanosecond delta,
a speed cap and a decay constant; `MouseHandler.onScroll` is cancelled whenever `shouldCaptureScroll()`;
`resetIfInactive()` after each render; style change (new stack) resets scroll. The header offset
(`LAST_HEADER_ABS`) is excluded from scrolling.

Lessons (no new feature implied):
* **[Suggest]** Smooth/inertial scrolling is a *feel* option; V1's direct offset is simpler and more
  predictable. If ever wanted, keep it presentation-only inside the existing controller.
* **[Interp]** TO's global static scroll state has no target identity — exactly the class of bug V1's
  screen+slot identity already solved. Keep V1's approach.
* **[Interp]** TO exposes a per-item `disableScrolling`; V1 has no need — content-driven overflow
  decides.

Modifier keys: **[TO]** none. **[Totality]** V1: Shift → Details, Ctrl → Technical
(`TooltipDisclosureLevel`), footer hints.

---

## 8. Compatibility Strategy

* **[TO]** Broad reach by hooking the *generic* tooltip path, then patching the consequences: item
  inference via JEI/EMI proxies, EMI screen recording, FTB Quests skip, Apotheosis hook, optimiser
  toggles.
* **[Interp]** Every compat module exists because the hook point is too generic. Totality's narrower hook
  (container-screen tooltip call where the stack is known, explicit opt-in, vanilla fallback for
  structured components, deferral into vanilla's own top stratum) needs far fewer patches.
* **[Suggest] safest 26.2 hooks for V2** (all already used or available):
  * keep V1's `AbstractContainerScreen.extractTooltip` redirect + `extractDeferredElements` deferral;
  * register preview renderers through Fabric's `PictureInPictureRendererRegistry`, not mixins;
  * keep scroll capture conditional (visible + overflowing) as V1 does.
* **[Suggest] version-fragile / invasive in TO — avoid:** cancelling `renderTooltipInternal`-equivalents
  for all tooltips; accessors into `ClientTextTooltip`/`GuiGraphics` internals; invoking the private
  `EntityRenderDispatcher.render`; re-wrapping flattened `FormattedCharSequence`s.
* **Open V2 question:** whether Totality tooltips should ever appear outside container screens (JEI/EMI
  ingredient lists, creative search, custom Totality screens). If yes, prefer each surface passing a
  *known* stack into the Totality pipeline over TO-style inference.

---

## 9. Performance Observations

* **[TO]** Good: resolved style cached per stack; frame data resolved once per stack; animation clock
  reset on change.
* **[TO]** Per-frame costs: re-wrapping all text components, size calculation, 8+ layers, animated
  effects (scissor + fills; particle lists), `ItemStack.copy()` and full re-equip of the static armor
  stand, 3D item render.
* **[Totality] V1 note (observation, not a request):** each frame the container-screen mixin calls
  `TotalityTooltipRenderer.isEligible`, which runs contributors until it finds a visible non-header section
  (`TotalityTooltipRenderer.java:155`), and `render` then runs the full contributor pass again (`:193`) —
  so an eligible item's contributors run up to twice per frame, uncached. TO's per-stack caching idea is
  the relevant lesson.
* **[Suggest] cache** per (stack identity via `isSameItemSameComponents`, disclosure level, knowledge
  state, GUI scale / font / language): the built `TooltipDocument`, the laid-out section heights and wrap
  results, the resolved theme, and the chosen presentation mode.
* **[Suggest] recompute** only time-based animation (name animation, optional preview rotation), scroll
  offset, and anything flagged live (e.g. energy level if it can change while hovered — decide per
  contributor).
* **[Suggest] preview cost:** a PIP render is an off-screen pass each frame it changes; a static preview
  can reuse its texture while the stack is unchanged (PIP state equality), whereas a spinning one
  re-renders every frame. Default to static.

---

## 10. Tooltip Overhaul vs Totality Tooltip V1 (vs possible V2)

| feature | Tooltip Overhaul | Totality V1 | classification |
|---|---|---|---|
| Semantic content model | none (vanilla component list) | sealed `TooltipSection` document from contributors | **V1 HAS DIFFERENT/BETTER APPROACH** |
| Contributors / providers | style bridges only | `TooltipContributor` registry, ordered, applicability-self-checked | **V1 BETTER** |
| Eligibility | every tooltip (global) | explicit opt-in profile + content-driven | **V1 BETTER** |
| Structured `TooltipComponent`s | kept, restyled | vanilla fallback (known limitation) | **POSSIBLY USEFUL LATER** (embed inside panel) |
| Rarity | vanilla 4-tier + frame "rating" text | `ItemRarity` families, badge, themes, name animation | **ALREADY IN V1** (V1 richer) |
| Category / type | none | `ClassificationsComponent` badges | **ALREADY IN V1** |
| Lore | vanilla lines | `Description` section + lore placement | **ALREADY IN V1** |
| Stat/icon rows | none | `StatRow`/`StatBlock`/`IconStatRow`/`ProgressBar` | **ALREADY IN V1** |
| Provenance / advanced | none | `ProvenanceGroup`, Shift/Ctrl disclosure | **ALREADY IN V1** |
| Knowledge gating | none | `TooltipKnowledgeView` | **ALREADY IN V1** |
| Wrapping | re-wraps vanilla text to screen-derived width | `Font.split` against final width; badge packing; compact width | **V1 BETTER** |
| Scrolling | global static, inertial | viewport with identity/reset, conditional capture | **ALREADY IN V1**; inertia = POSSIBLY USEFUL LATER |
| Footer | none | renderer-owned footer + hints | **ALREADY IN V1** |
| JEI/EMI | inference proxies | deferred-stratum draw fixes JEI z-order | **V1 DIFFERENT/BETTER** for current scope |
| Layer/z-band renderer structure | yes | renderer + frame/painter/animator split | **USEFUL V2 INSPIRATION** (clearer layer roles) |
| Data-driven selectors (item/tag/namespace/all), resource-pack loadable | yes (visual only) | components/profiles in code | **USEFUL V2 INSPIRATION** (for presentation mode/theme overrides) |
| Per-stack style cache | yes | not cached | **USEFUL V2 INSPIRATION** |
| Category-selected large preview | yes (side panel, data components) | 16 px header icon | **USEFUL V2 INSPIRATION** (concept only; different 26.2 mechanism) |
| Rotating item via 3D pose | yes | — | **NOT SUITABLE** (does not port to 26.2) |
| Armor stand via live entity | yes | — | **ACTIVELY AVOID** (use PIP/`EntityRenderState` if ever) |
| Header icon appear animations | barrel roll / bounce / fan-in | — | **POSSIBLY USEFUL LATER** (subtle, rarity-driven at most) |
| Textured/animated frames | yes | theme/border styles, rarity animation | **POSSIBLY USEFUL LATER** (Core/UI redesign territory) |
| Animated background effects | 6 kinds | — | **ACTIVELY AVOID** as default (readability/perf); maybe for top rarities only |
| Free-form positions/paddings config | ~35 options | constants | **NOT SUITABLE** |
| Hand-curated vanilla item lists | yes | — | **ACTIVELY AVOID** |
| Non-item tooltips restyled | optional | no | **NOT SUITABLE** now (Core/UI question) |
| Optimiser (ModernFix/Flerovium) awareness | yes | — | **USEFUL V2 INSPIRATION** (risk to test for any model preview) |

---

## 11. Useful Ideas for Tooltip V2 — **[Suggest]**

1. **Presentation-mode selection from semantics + data.** A resolver maps item → presentation mode
   (standard sprite / large sprite / item model / block model / later equipment) using Totality
   classifications, tooltip profile, tags, with exact-item override — mirroring TO's
   item/tag/namespace selectors, but for *mode and theme*, never content.
2. **Optional data-driven overrides** (JSON/resource-pack) for presentation knobs only (mode, theme
   variant, frame/ornament), if designers want content-free tuning without code.
3. **Per-hover cache** of document, layout and resolved presentation, invalidated by stack/disclosure/
   knowledge/scale changes.
4. **Explicit layer roles** in the renderer (background → frame → header/preview → body → overlay/footer)
   so rarity-driven treatments (frame, corners, glow, name animation, preview treatment) plug into named
   roles instead of branching inside one large renderer.
5. **Preview placement within the vertical flow** with reserved, measured height — preserving the V1
   "scale vertically" rule while enabling larger visuals.
6. **Treat preview robustness as a first-class risk:** test flat items, block items, oversized models,
   special renderers (banners, shields, heads, tridents), and item-render optimisers.

---

## 12. Ideas to Reject / Avoid — **[Interp]** with reasons

| idea | reason |
|---|---|
| Hooking/cancelling vanilla's generic tooltip renderer for all tooltips | too invasive, version-fragile, forces item inference and a pile of compat patches |
| Inferring the hovered item from screen type / JEI / EMI heuristics | mis-attribution risk; Totality already has the stack at its hook |
| Rendering content from vanilla's post-formatting `ClientTooltipComponent`s | renderer ends up owning presentation of data it cannot understand; blocks semantic sections, disclosure, provenance |
| Global static renderer/scroll state | no target identity; the bug class V1 already fixed |
| Hand-curated vanilla item-ID lists for styling | does not scale; Totality has classifications/profiles/tags |
| Side-panel preview | horizontal growth conflicts with "scale vertically, not horizontally"; duplicates the header icon |
| Live `ArmorStand` bound to the client level in a static field, re-equipped every frame | stale-level risk across world changes, per-frame allocation, private-method invoker |
| 3D `PoseStack` spinning of GUI items | does not exist in 26.2's 2D GUI pose; PIP is the supported path |
| Always-on animated background effects (cinders, ripples, sonar…) | readability/accessibility and per-frame cost; conflicts with the calm, compact V1 language; if ever, rarity-gated and subtle |
| ~35 free-form position/padding/alignment options | layout should be owned by the design system (Core/UI), not user-tuned pixels |
| Mandatory continuous rotation | motion without information; distracting in dense inventories |
| Restyling non-item tooltips (buttons etc.) | belongs to the later Core/UI redesign, not Tooltip V2 |

---

## 13. Suggested Tooltip V2 Architectural Lessons — **[Suggest]**

### A. Content model (conceptual primitives, no class names locked)
Header (name, rarity, category/type) · Preview (mode + target, no pixels) · Text line · Stat row ·
Stat group · Icon + text row · Progress / durability bar · Property badges · Separator (semantic, e.g.
"section break") · Lore block · Requirement / warning · Advanced section (disclosure-gated) ·
Provenance breakdown · External/third-party lines · Footer (renderer-owned, as in V1).
Most already exist in V1 (`TooltipSection`); V2 mainly adds **Preview**, clarifies **primary vs
secondary** stat grouping, and formalises **advanced sections**.

### B. Semantic contributors
Contributors describe *what*, never *how* (V1 rule — keep it). Each queries its system's source of truth
and emits sections: mining (effective damage/speed/tier; Shift provenance incl. Force Tolerance), block/
material (Material, Block Durability, Effective Tool, Required Mining Tier — never placed-block
Integrity, which is world state), item durability (current/max + bar; distinct from Block Durability),
food, weapon, armor, enchantment, rarity/classification, energy, custom items. Items contribute via
profiles/tags/registries/data first; exact-item contributors only when genuinely needed.

### C. Presentation modes
* standard sprite — materials, ingredients, ordinary food, most simple items;
* large sprite — notable simple items where a bigger silhouette helps (open: resolution);
* item model — weapons, tools, equipment, devices;
* block model — placeable blocks, machines;
* equipment/entity preview — later, only if a clear need appears.
Mode chosen by semantics + override; rarity may *treat* the preview (frame/glow) but not change which
information is shown or its order.

### D. Advanced information
Normal view: effective primary values and essential identity. Shift (Details): provenance (named real
sources: Base Tool, material, enchantment, module/reinforcement, buffs), secondary stats, Force Tolerance.
Ctrl (Technical): ids, component counts, raw values (V1 already). The tooltip *queries and presents*; it
never becomes the source of truth.

### E. Scrolling
Keep V1's controller. Possible later polish: inertial feel; ensure preview height is part of fixed chrome
or of the scrollable body by an explicit rule (open question).

### F. Compatibility
Keep V1's hook + deferral; use Fabric `PictureInPictureRendererRegistry` for model previews; keep
conditional scroll capture; decide separately whether/where Totality tooltips appear outside container
screens (pass known stacks, don't infer).

### G. Performance
Cache document/layout/presentation per hover state; recompute only animation/scroll/live values; prefer
static previews (PIP texture reuse) over spinning; measure the contributor double-pass in V1.

---

## 14. Open Questions for Stefan + ChatGPT

1. Where should a large preview live: header (bigger icon beside name/badges) or a preview band between
   header and stats? Does it scroll with the body or stay fixed?
2. Is rotation ever wanted, or only a static 3/4 model view? If rotation, per rarity or per category?
3. Should "large sprite" exist at all, given possible atlas-resolution softness on 26.2 — or jump straight
   from standard sprite to model preview?
4. Which categories get item-model vs block-model previews by default, and who can override (profile
   field, tag, JSON)?
5. Should presentation overrides be data-driven (JSON/resource packs) or code-only for now?
6. Should V2 embed structured `TooltipComponent`s (bundles, maps, other mods) inside the Totality panel
   instead of falling back to vanilla?
7. Should Totality tooltips appear in JEI/EMI/creative search/custom screens, and via which known-stack
   entry points?
8. Durability presentation: one generic "item durability" row/bar for all damageable items — shown in
   default view, or only when damaged?
9. How much rarity treatment is allowed to move (name animation, frame glow) before readability suffers?
10. Is inertial scrolling desirable, or is V1's direct offset the intended feel?

(Note: no dedicated "Tooltip V2 preparation" or "Master Reference" document was found in the repository;
this audit used the task brief as the statement of V2 direction plus the V1 reports and
`Context/Audit/Image References/`.)

---

## 15. Recommended Topics for the Next Tooltip V2 Design Discussion

1. Lock the **content primitives** list (§13A) and which are new vs existing V1 sections.
2. Define the **primary vs secondary vs advanced** split per contributor family (mining tool, block item,
   damageable item, weapon, armor, food, energy).
3. Decide **preview placement and modes** (§14 Q1–Q4), then run a **26.2 PIP spike** (item model, block
   model, flat item, oversized item, special renderers, optimiser compatibility, cost).
4. Decide the **presentation-override mechanism** (profile/tag/JSON) and its precedence.
5. Define **caching/invalidation** for the tooltip document and layout.
6. Agree the **rarity treatment boundary**: which visual roles rarity may influence (frame, corners, glow,
   name animation, preview treatment) and which it may not (content, order).
7. Decide the **surface scope**: container screens only vs. recipe viewers/custom screens.

---

### Appendix — key evidence references (TO, intermediary names decoded where marked)
* Hook: `mixin/GuiGraphicsMixin#enhancedtooltips$coreRenderer` (`@Inject HEAD cancellable`,
  `renderTooltipInternal`); `mixin/GuiGraphicsItemMixin#tooltipsOverhaul$captureHovered`.
* Pipeline: `client/TooltipRenderer#render(TooltipContext)`, `#calculateSize`, `#updateStyle`,
  `#computeRating`; static `LAYERS_MAIN/LAYERS_SECOND/LAYERS_EMPTY`, `lastStack`, `startMs`, `ELAPSED`.
* Previews: `client/style/renderer/DefaultRotatingItem#renderDefault` (Y spin, −45° Z tilt, scale,
  `GuiGraphics.renderItem`); `DefaultArmorStand` (static `ArmorStand stand`, equippable slot,
  `EntityRenderDispatcherAccessor`); `DefaultIcon` (`barrel_roll`/`bounce`/`fan_in`).
* Scrolling: `client/TooltipScrollState` (velocity/nanoTime), `mixin/MouseHandlerMixin`.
* Data: `client/frame/CustomFrameLoader/Manager/Data(Deserializer)`; `custom_frames.json`.
* Compat: `compat/proxy/{ScreenTypeProxy,JeiProxy,EmiProxy}`, `compat/modernfix/ModernFixCompat`.
* 26.2 facts: `GuiGraphicsExtractor.pose(): Matrix3x2fStack`; `net.minecraft.client.gui.render.GuiItemAtlas`;
  `net.minecraft.client.gui.render.pip.*`; Fabric `PictureInPictureRendererRegistry`
  (fabric-rendering-v1 25.3.3+515ac5339e, part of fabric-api 0.161.0+26.2).
