# Totality Tooltip API — Architecture Audit

A full trace of the custom tooltip renderer, its item integrations, and the exact reason some items get the complete Totality panel while others fall back to vanilla — with a migration plan for the next phase.

## Metadata

- **Inspected branch:** `feature/general-resource-api`
- **Inspected commit:** `6998d3222ddc5274a6fca7e0dbd83c71fc3b7408`
- **Commit subject:** "Add healing potion and combat feedback"
- **Date of inspection:** 2026-07-30
- **Read-only status:** This audit is a read-only architecture inspection. No production or test files were modified, no implementation work was performed, no commit was created, and nothing was pushed as part of producing this document (see confirmation at the end of this report).

---

## 1. Verified branch and commit

`git rev-parse HEAD` returned `6998d3222ddc5274a6fca7e0dbd83c71fc3b7408` on branch `feature/general-resource-api`, matching the expected checkpoint exactly. Commit subject confirmed: *"Add healing potion and combat feedback."*

The working tree has pre-existing, uncommitted changes that predate this audit and were left untouched: generated datagen JSON (loot tables, recipes, noise settings) and a **comment-only** diff in `TooltipColors.java` (three `// Normal Rarities` / `// Special Cases` / etc. section comments added, no logic changed). Several untracked review-bundle archives and screenshots also sit outside git tracking. None of this was created, modified, staged, or committed during this audit.

## 2. Executive summary

Totality's tooltip system is genuinely two systems wearing one name. The **panel shell** — frame, icon, animated name, rarity badge, type badge, separator, footer — is real, generic, data-driven infrastructure: it activates for any item carrying three shared data components (`RarityComponent`, `ItemTypeComponent`, `LoreComponent`) and needs no per-item code. But everything *inside* that shell is assembled by a fixed, hand-written sequence inside one 300-line static method, `TotalityTooltipRenderer.render()`, which calls exactly two hardcoded "block" classes (energy/weight stats, weapon stats) and one duck-typed extension hook (`TooltipExtension`) — in that order, with no registry, no discovery, and no way to add a third block without editing the renderer itself.

The Copper vs. Netherite Battery split has nothing to do with class hierarchy, capability interfaces, or renderer logic — **all five battery tiers are the same `BatteryItem` class implementing the same `UEItem` capability.** Copper simply received a `RarityComponent` + `ItemTypeComponent` at registration; Iron, Gold, Diamond, and Netherite did not, so they never clear the one gate (`stack.has(ItemComponents.getRarity())`) that routes an item into the custom renderer at all, and fall through to a hand-written legacy `appendHoverText`. This is the cheapest possible bug to fix and the clearest evidence that the "generic shell" layer already works as intended — it just wasn't applied consistently at registration time.

The Grimoire and Weapon integrations are each a different flavor of the same underlying limitation: real generic mechanics (component-driven shell, capability-driven stat block) doing most of the work, with one narrow custom hook per special case. Nothing here is a "true" contributor system — there is no registration list, no ordering, no disclosure levels, and no composition of multiple contributors on one item. The `ItemRarity` enum currently miscodes `ARTIFACT` as a rarity rung rather than a separate classification, and has no `ANCIENT` constant at all. There is zero automated test coverage for any part of the tooltip system. Full details follow; recommendations begin at §17.

## 3. Exact Tooltip API files and responsibilities

| File | Responsibility |
|---|---|
| `client/tooltip/TooltipExtension.java` | One-method interface: `void addTooltipLines(ItemStack, Font, List<Component>)`. The only per-item "contribution" hook that exists. |
| `client/tooltip/TotalityTooltipRenderer.java` | The orchestrator. Single public entry point `render(graphics, font, stack, x, y)`; owns layout math, theme resolution, and the fixed draw sequence. |
| `client/tooltip/TotalityIcons.java` | Custom glyph font (`totality:icons`) + `icon()`/`iconLabel()` component builders (FLAME, ENERGY, WEIGHT, DAMAGE, PROPERTIES, RANGE, STAMINA). |
| `client/tooltip/renderer/TooltipAnimator.java` | 20 static `draw*Text(...)` methods, one per rarity, time-based per-character animation (shine/wave/glitch/etc.). |
| `client/tooltip/renderer/TooltipFrameRenderer.java` | `drawBorder(...)` — flat border for every rarity, plus an optional textured overlay. `frameFor(ItemRarity)` only has entries for `LEGENDARY` and `COMMON`; the other 18 rarities get no overlay texture at all. |
| `client/tooltip/renderer/TooltipPainter.java` | Drawing primitives: background gradient, separator, footer dots, text, badge (unused — badges are drawn inline in the renderer instead), item icon, `wrapText()`, small diamond glyph. Also holds a private, unused, duplicate `isShiftDown()`. |
| `client/tooltip/renderer/TooltipStatBlock.java` | Energy / fuel / weight stat box. Gates: `instanceof UEItem`, `ItemType == FUEL`, `stack.has(WEIGHT)` — genuinely capability-driven. |
| `client/tooltip/renderer/TooltipWeaponBlock.java` | Dice/damage/properties/range/stamina block. Gate: `instanceof TotalityWeaponItem` only — no vanilla fallback. |
| `client/tooltip/theme/TooltipBorderStyle.java` | Plain `int` constants: 20 rarity border styles + 16 `TYPE_*` badge/border styles. |
| `client/tooltip/theme/TooltipColors.java` | `forRarity()` (exhaustive, all 20 covered) and `forType()` (has a default fallback). |
| `client/tooltip/theme/TooltipTheme.java` | 15-field record + one static factory per rarity (21 factories for 20 constants — `common()` doubles as the default). |

Supporting pieces outside the package: `api/core/rpgutils/rarity/ItemComponents.java` (registers `RARITY`, `ITEM_TYPE`, `LORE`, `WEIGHT` as persistent, network-synced data components), `ItemRarity.java`, `ItemType.java`, and `mixin/client/AbstractContainerScreenMixin.java` (the sole render entry point).

## 4. Complete tooltip render flow

There is no Fabric `ItemTooltipCallback` or any event-bus registration anywhere in the repo — the entire mechanism is one Mixin redirect.

1. Vanilla `AbstractContainerScreen.extractTooltip(GuiGraphicsExtractor, int, int)` runs during screen render (vanilla method, not in this repo).
2. `mixin/client/AbstractContainerScreenMixin.onSetTooltip(...)` — a `@Redirect` on the call to `GuiGraphicsExtractor.setTooltipForNextFrame(...)` inside `extractTooltip`. Reads the hovered slot's stack via `@Shadow Slot hoveredSlot`.

```java
var rarityType = ItemComponents.getRarity();
if (rarityType != null && !stack.isEmpty() && stack.has(rarityType)) {
    TotalityTooltipRenderer.render(graphics, font, stack, x, y);
    return;                              // vanilla `text` list dropped entirely
}
graphics.setTooltipForNextFrame(font, text, data, x, y, backgroundTexture);
```

This single component-presence check is **the** gate deciding custom vs. vanilla for every item in the game. It is a data check, not an `instanceof` item-class check.

3. `TotalityTooltipRenderer.render(...)` reads `RarityComponent` / `ItemTypeComponent` / `LoreComponent` off the stack, resolves a `TooltipTheme` via an exhaustive switch on `ItemRarity`, then in order: wraps lore text against a provisional width; collects extension lines via `stack.getItem() instanceof TooltipExtension`; computes total panel size; clamps X/Y to screen edges (not height); draws background + border; draws icon + animated title (dispatch by rarity); draws rarity badge + type badge inline; draws separator; calls `TooltipWeaponBlock.draw()` if `hasWeaponStats()`; draws collected extension lines; calls `TooltipStatBlock.draw()` if `hasStats()`; draws lore lines pinned above the footer; draws the hardcoded "Totality" credit line + footer dots.

Everything is drawn **immediately** (synchronous `graphics.fill`/`text` calls), not queued through vanilla's tooltip compositor — the custom path fully bypasses `setTooltipForNextFrame`.

### Client-only enforcement

Nothing in `client/tooltip/**` carries an explicit `@Environment(EnvType.CLIENT)` annotation. Client-only-ness is enforced two other ways: every file has a hard compile dependency on client-only classes (`Minecraft`, `GuiGraphicsExtractor`, `Font`), and the sole mixin is registered in the `"client"` bucket of `totality.mixins.json`, so Fabric Loader never applies it dedicated-server-side. The package only *reads* data components that were set server-side/at-registration and synced via stream codecs — it never mutates gameplay state.

## 5. Current extension/contributor architecture

Classifying the three named integrations against the taxonomy in the brief:

| Integration | Classification | Discovery mechanism |
|---|---|---|
| Panel shell (frame/icon/name/badges/lore/footer) | Genuinely data-component-driven | Component presence (`Rarity`/`ItemType`/`Lore`) — real, reusable, item-class-agnostic |
| Grimoire (tier + spell state) | Shared extension interface, implemented per item | `instanceof TooltipExtension` |
| Copper Battery (energy/weight) | Capability-driven hardcoded block | `instanceof UEItem` inside `TooltipStatBlock` (not registered — hand-called from `render()`) |
| Netherite Shuriken (weapon stats) | Item-class-specific renderer | `instanceof TotalityWeaponItem` inside `TooltipWeaponBlock`, no fallback |
| Ring of Protection | Item-local tooltip assembly | Fully custom `addTooltipLines()` override, duplicates shared logic |

None of these is a **true registered contributor**. There is no list of contributors that can be iterated, reordered, or extended without editing `TotalityTooltipRenderer.render()` directly — new semantic content must fit into one of exactly three slots: a shared data component (rarity/type/lore/weight), the single `TooltipExtension.addTooltipLines` hook (raw `List<Component>`, no structure — no headings, no badges, no disclosure level), or a brand-new hardcoded block wired into `render()` by hand. Multiple contributors *can* compose on one item today (e.g. a weapon gets both `TooltipWeaponBlock` and its `TooltipExtension` attunement line), but only because the renderer happens to call both — not because of any composition contract.

## 6. Grimoire integration trace

`item/magic/GrimoireItem.java` extends vanilla `Item` directly and implements `TooltipExtension` itself — notably **not** via the shared `TotalityItem` base interface that most other Totality items use, so Grimoires get no free attunement line. One class, one `int maxTier` field (not an enum), shared by all three tiers:

```java
@Override
public void addTooltipLines(ItemStack stack, Font font, List<Component> lines) {
    lines.add(Component.literal("Tier " + toRoman(maxTier))...);
    GrimoireCaster caster = stack.getOrDefault(MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
    if (!caster.spellName().isEmpty()) lines.add(Component.literal("Active: " + caster.spellName())...);
    else lines.add(Component.literal("No spell active")...);
}
```

Registration (`init/items/MagicItems.java`) hardcodes rarity/lore per tier as literal constructor arguments — Novice = `RARE` + tier 1, Apprentice = `EPIC` + tier 2, Archmage = `LEGENDARY` + tier 3 — all three sharing `ItemType.MAGICAL`. There is no `GrimoireTier` enum enforcing the tier↔rarity correlation; it's convention only.

The "selected spell" state is a plain `String` (`GrimoireCaster.spellName()`) persisted in a data component and set elsewhere via networking payloads — the tooltip reads only this narrow accessor, never reaching into the underlying `ArcaneFormula`/rune internals. That's a reasonably narrow seam: a future Spell API could enrich `GrimoireCaster` (icon, mana cost, description) and the tooltip code wouldn't need to change, as long as it kept reading through the same accessor.

> **Verdict:** A good model for "one hook, tier-parameterized," but not a preview of a true contributor system — it's a single opaque list of `Component` lines with no internal structure, discovered only via `instanceof`, and it bypasses the shared attunement default that every other equipment-like item gets for free.

## 7. Battery-family comparison and root cause

Every tier — Copper, Iron, Gold, Diamond, Netherite — is the exact same Java class, `item/energy/BatteryItem.java`, implementing the exact same `UEItem` capability interface. Registered in `init/items/EnergyItems.java`:

| Registry ID | Class | Capacity | In/Out | Rarity+Type+Weight set? | Custom panel? |
|---|---|---|---|---|---|
| `copper_battery` | `BatteryItem` | 48,000 | 32 / 32 | Yes (CRUDE / BATTERY) | Yes |
| `iron_battery` | `BatteryItem` | 320,000 | 32 / 32 | No | No |
| `gold_battery` | `BatteryItem` | 128,000 | 128 / 128 | No | No |
| `diamond_battery` | `BatteryItem` | 1,000,000 | 256 / 256 | No | No |
| `netherite_battery` | `BatteryItem` | 5,000,000 | 512 / 512 | No | No |

> **Root cause:** Copper's registration attaches `.component(RARITY, CRUDE)` + `.component(ITEM_TYPE, BATTERY)` + `WEIGHT` + `LORE`. The other four tiers are registered with a bare `new Item.Properties()` — no rarity component at all. Since the mixin's only gate is `stack.has(ItemComponents.getRarity())`, those four stacks never qualify for `TotalityTooltipRenderer` and fall through to `BatteryItem`'s own legacy, hand-written `appendHoverText` — which is exactly where "Energy: 0 / 5M UE", the segmented bar, "Inactive," and "Hold SHIFT for details" come from. This is not a class difference, not an interface difference, and not a renderer bug — it is a per-item registration omission.

`TooltipStatBlock.hasStats()` already gates purely on `instanceof UEItem`, which every tier satisfies. Adding the same four components to Iron/Gold/Diamond/Netherite's registration calls (no code changes to `BatteryItem`, the mixin, or any renderer class) is sufficient to route all five tiers through the identical custom panel.

### Shift-key precision toggle

Neither the legacy nor the custom path uses `TooltipFlag.isAdvanced()` or any shared disclosure object. Both independently poll raw GLFW: `BatteryItem.isShiftDown()` (legacy path) and `TooltipStatBlock.isShiftDown()` (custom path) are byte-for-byte identical, duplicated code. Compact format uses `UEFormat.energy()` (e.g. "5M"); Shift-down swaps to raw longs with " UE"/" UE/t" suffixes.

## 8. Weapon tooltip integration trace

The Netherite Shuriken is an instance of the shared `item/base_weapons/ShurikenItem.java` (`Item → TotalityThrownWeaponItem → ShurikenItem`), registered with `Dice.D8`, dice count 2 (→ "2d8"). Damage type, Finesse, Light, throw range (20/60 ft) are all instance overrides on the interface's default methods — not a data component, not a static registry.

`client/tooltip/renderer/TooltipWeaponBlock.java` renders dice box, damage type, STR/DEX or Finesse badges, property badges, category/range, and stamina cost — but every entry point is gated the same way:

```java
public static boolean hasWeaponStats(ItemStack stack) {
    return stack.getItem() instanceof TotalityWeaponItem;
}
```

Attunement is *not* part of this block — it comes generically through `TotalityItem`'s shared `TooltipExtension` default (weapons override `requiresAttunement() → false` unless a magic weapon overrides it further).

> **Already exists:** Combat math already solved exactly this problem. `api/rpg/combat/weapon/WeaponDataResolver.java` checks `instanceof TotalityWeaponItem` first, then falls back to a 34-entry static `Map<Item, WeaponData>` in `VanillaWeaponStats.java` covering every vanilla sword, axe, pickaxe, shovel, hoe, bow, crossbow, trident, and mace with dice/ability/damage-type/finesse data. This is the exact "TotalityWeaponItem-first, vanilla-map-fallback" adapter the tooltip layer needs — **it already exists, it's just never consulted by `TooltipWeaponBlock`**.

`VanillaWeaponTypes.java` similarly resolves weapon type/stamina cost for vanilla items via item-tag fallback (`ModTags.ONE_HANDED_WEAPONS`/`TWO_HANDED_WEAPONS`) when the item isn't a `TotalityWeaponItem` — a second existing fallback pattern the tooltip layer doesn't use either.

## 9. Rarity, classification, value, weight, attunement

`api/core/rpgutils/rarity/ItemRarity.java`, in full:

```java
// Standard Progression
COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHICAL, ARTIFACT,
// Special
FORBIDDEN, CURSED, QUEST,
// Religious
BLESSED, SACRED, CELESTIAL, DIVINE, GODFORGED,
// Industrial
CRUDE, CALIBRATED, REINFORCED, PROTOTYPE, OVERCHARGED, MASTERWORK;
```

> **Confirmed gap:** The comment block literally groups `ARTIFACT` under `// Standard Progression` alongside Common→Mythical, and every switch in `TotalityTooltipRenderer` (theme resolution, border style, animated title) and in `TooltipColors.forRarity()` treats `ARTIFACT` as one more rung of the same ladder — not as the separate classification the intended design calls for. There is also **no `ANCIENT` constant at all**, despite the intended ladder naming Ancient as the top standard tier.

`ItemType` is a fully separate enum (BLOCK/DECORATIVE, STANDARD/CONSUMABLE/MATERIAL/POTION/FUEL/FOOD/INGREDIENT/CURRENCY, WEAPON/ARMOR/TOOL, INDUSTRIAL/CABLE/BATTERY/MACHINE/COMPONENT, MAGICAL/REAGENT/RITUAL) driving the single classification badge — one value per item, no multi-badge composition (a hypothetical future magical weapon could only show one type badge, not both).

**Value/economy:** a real pricing system exists (`api/economy/value/ItemPricingService`, `ItemValueRegistry`, `ItemValueRule`) but nothing in the tooltip renderer reads it — there is no "Value" line anywhere in `TotalityTooltipRenderer` today. **Weight** is handled correctly and generically — `WeightComponent`, read via component presence in `TooltipStatBlock`, alongside rarity/type/lore in the "genuinely generic" tier of the system. **Durability** is not drawn by the custom renderer at all (vanilla's own durability bar renders on the inventory slot, a separate path, unaffected either way).

**Attunement** is handled by two different code paths simultaneously: the shared `TotalityItem.addTooltipLines()` default (reusable, correct), and at least one full manual duplicate — `RingOfProtectionItem` copies the same three-line attunement logic verbatim *and* hardcodes `"+1 bonus to AC and saving throws"` as a literal string, disconnected from its own `getAcBonus()==1` / `getSaveBonus()==1` values that sit two lines above it in the same file. This is a concrete, present-day instance of the "one-off tooltip code in every new item" pattern the roadmap wants to eliminate.

## 10. Progressive-disclosure behavior

Only Shift is wired anywhere in the tooltip pipeline — a repo-wide search for Ctrl-key detection inside `client/tooltip/**` returns nothing, and the custom renderer never receives or checks a `TooltipFlag` at all (vanilla's own Ctrl+F3+H "advanced tooltips" flag exists elsewhere in the mod, e.g. `UEArmorItem`, but is never plumbed into `TotalityTooltipRenderer`).

> **Fragmentation:** Shift-down is checked via **four separate, byte-identical, hand-rolled GLFW polls**: `BatteryItem.isShiftDown()`, `EnergyCellItem.isShiftDown()`, `TooltipStatBlock.isShiftDown()`, and a private unused copy in `TooltipPainter`. A proper centralized helper already exists — `util/TotalityKeyHelper.java` (`isShiftPressed()`, `isCtrlPressed()`, `isAltPressed()`, `isKeyDown()`) — but it's used in exactly one place mod-wide (`ComponentPouchScreen`) and never adopted by the tooltip system, despite already supporting Ctrl.

Compact-vs-exact formatting exists only for energy (`UEFormat.energy()` vs. raw longs) and only inside `TooltipStatBlock`/the legacy `BatteryItem` path — there is no general-purpose compact/exact formatter or shared "disclosure context" object. No tooltip text in the custom panel tells the player which key to hold (the "Hold SHIFT for details" hint only exists in the legacy `appendHoverText` path — ironically only shown on the battery tiers that *don't* get the custom panel).

## 11. Wrapping, bounds, and scrolling

`TooltipPainter.wrapText()` is a naive greedy word-wrap (space-split only), applied *only* to lore text. Panel width is computed from the title first (clamped to [160, 180]), then possibly widened by lore-line width — but lore is wrapped against the pre-widening width, so the final draw width and the wrap width can disagree in edge cases.

> **No viewport, no scroll:** `panelH` is an unbounded sum of every section's height. Only X/Y *position* is clamped to screen edges — height is never clamped, clipped, or scrolled. A tall combination (many lore lines + weapon block + stat block + several extension lines) can be positioned at `panelY = 6` and still draw past the bottom of the screen with no scissor test. There is no mouse-wheel handling anywhere in `client/tooltip/**`. Vanilla's own tooltip positioning system is not reused — the renderer reimplements its own clamping independently.

## 12. Vanilla and third-party compatibility

> **Full replacement, not merge:** Whenever the custom path fires, the vanilla-assembled `text` list — which already contains enchantment lines, dyed-item info, trim info, attribute modifiers, durability text, and any other mod's appended tooltip lines — is captured as a parameter and **never read or forwarded**. The `Optional<TooltipComponent> data` parameter (map previews, banner patterns, bundle contents) is discarded the same way. This matches `CreditsItem`'s own doc comment: "this mod's tooltip rendering is fully custom and never calls vanilla's hover-text pipeline." For items *without* a rarity component, the vanilla path is 100% untouched — but that's simply because no Totality item has reached that content yet, not evidence of a deliberate preservation policy.

This is currently invisible because no Totality item yet carries enchantments or dye/trim data in practice — but it becomes a real, visible bug the moment any Totality weapon or armor piece becomes enchantable, dyeable, or trimmed.

## 13. Existing tests and coverage gaps

The mod has an extensive test suite — 89 files under `src/test/java`, almost entirely covering the Generic Player Resource API, combat rolls, spell slots, and economy/sync logic. **Zero of them reference the tooltip package, `ItemRarity`, `ItemType`, `TooltipStatBlock`, `TooltipWeaponBlock`, or any of the battery/grimoire/weapon items examined here.** Tooltip correctness today is verified entirely by manual screenshot inspection — which is exactly how the Copper/Netherite discrepancy in this brief was found in the first place.

> **Recommended first regression test:** A pure data assertion needing no rendering at all: for every registered `BatteryItem` (or, more generally, every item implementing `UEItem`), assert `stack.has(ItemComponents.getRarity()) && stack.has(ItemComponents.getItemType())`. This would have caught the exact bug this audit was commissioned to explain, and generalizes to "every item with capability X must carry the components needed to reach the custom renderer."

## 14. Authoritative AC implementation

`api/rpg/combat/ArmorClass.java` — `calculate(ServerPlayer)`:

```java
int base = (armor == null)
    ? unarmoredAc(player, stats, dexMod)       // 10 + DEX, or a pluggable
                                                 // UnarmoredDefenseRegistry formula
    : armor.baseAc() + cappedDex(armor.type()); // 10 + Σ(per-piece AC) + capped DEX

return base + (isHoldingShield(player) ? 2 : 0)   // flat, vanilla ShieldItem in offhand
            + EquipmentComponents.get(player).getAcBonus(); // attunement-gated ring sum
```

DEX cap by armor weight class is confirmed in code (LIGHT = full DEX, MEDIUM = `min(dexMod, 2)`, HEAVY = 0), driven by the single heaviest piece worn across HEAD/CHEST/LEGS/FEET, not per-piece. Vanilla armor's AC comes from a static `VanillaArmorStats.PieceStats(ac, type, properties)` lookup table keyed by vanilla `Item`; Totality-authored armor uses `TotalityArmorItem.getAcBonus()`, gated on `ArmorCategory.isArmored()` (CLOTHING/null is transparent to both AC and Unarmored Defense). No spell-effect AC modifier exists in code yet.

`RingOfProtectionItem` (`getAcBonus() = 1`, `getSaveBonus() = 1`) is the only concrete AC-granting item shipped today — it is real, not hypothetical. No body-armor (LIGHT/MEDIUM/HEAVY) `TotalityArmorItem` has been authored yet; only vanilla armor currently contributes armored AC in practice. No `TooltipArmorBlock` exists, and no AC number is shown on any item tooltip today.

> **Recommended label:** Both concrete per-item sources — `VanillaArmorStats.PieceStats.ac()` and `TotalityArmorItem.getAcBonus()` (whose own Javadoc literally says "AC bonus granted when attuned and equipped") — represent an additive delta, not the full effective AC. `RingOfProtectionItem`'s own shipped tooltip text already says "bonus to AC." The label that matches the codebase's own vocabulary is **"AC Bonus"** — not "AC" or "Base AC," either of which would misleadingly suggest the full computed value (which is only ever produced by `ArmorClass.calculate()`, never per item).

Vanilla's `Attributes.ARMOR`/`ARMOR_TOUGHNESS` lines are suppressed for any item with a rarity component (silently dropped, never re-added) and shown unmodified for anything without one — including today's plain vanilla armor. Separately, `InventoryItemDetail.drawStats()` — a different UI, the custom inventory side panel, not the hover tooltip — already reads those same vanilla attributes and displays "Armor"/"Toughness" rows with an equipped-delta comparison, so the raw data is already surfaced elsewhere in Totality UI; it just isn't in the hover tooltip.

## 15. Vanilla weapon migration approach

Don't rewrite `TooltipWeaponBlock`'s draw logic — introduce a small resolver that mirrors `WeaponDataResolver.Resolved` exactly: check `instanceof TotalityWeaponItem` first, else look up `VanillaWeaponStats.get(item)` (already populated for all 34 vanilla tools/weapons), else fall back to unarmed. Change `TooltipWeaponBlock.hasWeaponStats()`/`draw()` to consult that resolved record instead of the raw `instanceof` check. `VanillaWeaponStats.WeaponData` today only carries damage die/ability/damage-type/finesse — it would need a modest extension (or a parallel map) to carry Thrown/Light/Heavy/Reach/Versatile/range for vanilla items, or phase 1 can simply omit those badges for vanilla-resolved weapons and show dice/type/ability only. No per-material (wooden/stone/iron/gold/diamond/netherite) tooltip class is ever needed — one adapter path covers all of them, exactly as it already does for combat.

## 16. Vanilla armor migration using AC, not Armor

Mirror the same shape as §15: an `ArmorTooltipData.resolve(ItemStack)` helper that checks `TotalityArmorItem` first, else `VanillaArmorStats.get(item)` (already populated with AC/type/properties per vanilla piece). Display the resolved value under the label **"AC Bonus"** (see §14). Suppress the redundant vanilla `Attributes.ARMOR` line only when this resolved value is present — using the same registration-component fix pattern as batteries (§7) so vanilla armor also gains a rarity/type component and routes through the custom renderer — while leaving unrelated vanilla lines (enchantments, trims) preserved per the compatibility policy in §17. Shield's flat +2 is best modeled as its own small badge ("+2 AC · Shield") rather than folded into the armor-piece block, since it's driven by a completely different slot and an unconditional additive rule. Once a generic AC-bonus tooltip line exists, `RingOfProtectionItem`'s hand-written `"+1 bonus to AC and saving throws"` string should be deleted in favor of deriving the same text from `getAcBonus()`/`getSaveBonus()` automatically — closing the exact drift risk flagged in §9.

## 17. Recommended future Tooltip API architecture

Keep the boundary the brief already proposes, and note that the codebase has good raw material for every layer of it:

- **Gameplay API / item data exposes semantic values** — already true for rarity, type, lore, weight (data components) and for energy, weapon, armor stats (capability interfaces: `UEItem`, `TotalityWeaponItem`, `TotalityArmorItem`). Nothing here needs to change.
- **Tooltip contributor converts values into semantic sections** — today this step is skipped: contributors write raw `List<Component>` lines or hand-draw pixels directly. Introduce a small typed `TooltipSection` model (header, badge row, stat row, stat block, divider, lore block) so contributed content has structure the renderer can lay out and disclose uniformly, instead of being either "opaque text lines" (`TooltipExtension`) or "a whole hand-drawn block" (`TooltipStatBlock`/`TooltipWeaponBlock`) with nothing in between.
- **Renderer owns ordering, spacing, colors, wrapping, disclosure, scrolling** — already true for the shell; needs extending so it also governs contributed sections, rather than each block re-implementing its own height estimation, its own shift-key poll, and its own badge drawing.

## 18. Recommended section model and ordering

Keep the existing visual order (header → badges → separator → weapon block → extension lines → stat block → lore → footer) since it already reads well and matches the screenshots. Replace the ad-hoc mix of "raw component lines" and "hand-drawn blocks" with one small closed set of section types, each owning its own `height(font)` and `draw(graphics, font, x, y, width)` — eliminating the duplicated height-estimation pattern currently split across `TooltipStatBlock.estimateHeight()` and `TooltipWeaponBlock.estimateHeight()`. Sections should be able to declare a disclosure level (default/shift/ctrl) so an individual stat row — not just an entire block — can be hidden or reformatted per level.

## 19. Recommended contributor discovery/registration model

Capability/interface-based, not a heavyweight central registry — the existing capability checks (`UEItem`, `TotalityWeaponItem`, `TotalityArmorItem`, `TooltipExtension`) already work well and shouldn't be thrown out. What's missing is turning the currently-hardcoded call sequence inside `render()` into *declared, ordered data*: a short list of `TooltipSectionProvider` entries, built once at mod init, each given `(ItemStack, DisclosureLevel)` and returning the sections it wants to contribute (internally still using the same `instanceof`/capability checks as today). This makes adding a fourth, fifth, or sixth block (armor, attunement, food/nutrition, etc.) a matter of appending to a list rather than editing the renderer's core method — while keeping the parts of the current design (component-driven shell, capability-driven blocks) that already work.

## 20. Recommended renderer responsibilities

The renderer should own: the panel shell (unchanged — already correct); layout math driven generically off the section list rather than bespoke per-block estimation methods; a single disclosure-level query per frame (via `TotalityKeyHelper`, extended with a `DisclosureLevel` enum, computed once and passed down — retiring all four duplicate GLFW polls); a real scroll/viewport contract (bounded visible height, mouse-wheel-driven offset, clipped draw region) so tall combinations of sections can no longer overflow the screen silently; and a "preserve unknown vanilla/third-party lines" policy — appending whatever the mixin currently discards (vanilla `text`/`TooltipComponent` `data`) as its own trailing section instead of dropping it.

## 21. Recommended migration phases

1. **Data-parity fixes** — battery registration gap (§7), `ItemRarity.ARTIFACT` reclassification (§9). Zero renderer changes, immediate visible payoff, and removes confounds before any structural refactor.
2. **Section-model + disclosure refactor** — introduce `TooltipSection`/`TooltipSectionProvider`, migrate the two existing blocks (stat, weapon) and the extension hook onto it, centralize disclosure via `TotalityKeyHelper`, add the scroll/viewport contract. No new gameplay content — pure infrastructure, validated against items that already work today.
3. **Vanilla weapon + armor adapters** — the `WeaponTooltipData`/`ArmorTooltipData` resolvers described in §15–16, reusing `VanillaWeaponStats`/`VanillaArmorStats`, which already exist for combat math.
4. **Broader migration** — tools, food, potions, enchanted books, functional blocks, plus the "other APIs" listed in the brief, each as a new section provider.

## 22. Suggested first vertical slice

Recommend: **all five battery tiers** (validates the registration-parity fix and the "any `UEItem` gets the battery section" principle that already holds in code), **all three Grimoire tiers** (validates migrating `TooltipExtension` content into the new section model without losing tier-specific state), the **Netherite Shuriken** (validates migrating a dedicated block onto the new contract before vanilla-adapter complexity is introduced), and the **standalone D&D Potion of Healing** (currently has no tooltip integration at all — a clean test of whether a brand-new item slots into the new model without one-off code).

Defer to the next slice: the vanilla sword-family adapter, vanilla armor-family adapter, Shield, and Ring of Protection's text-derivation fix — all four depend on the resolvers from §15–16, which in turn depend on the section-model refactor landing first. Bundling all eight into one slice would conflate "does the new architecture work" with "is vanilla data-mapping coverage complete," which are separable risks worth validating independently.

## 23. Risks and architectural debt

- **Silent content loss.** Any Totality item with a rarity component that later becomes enchantable, dyeable, or trimmed will silently lose that vanilla tooltip content today — currently invisible only because no such item exists yet.
- **Zero test coverage** on a visually load-bearing system — the exact bug this audit explains would not have been caught by anything currently in `src/test/java`.
- **Duplicated primitives** — four copies of shift-key polling, two competing badge-drawing implementations (inline in the renderer vs. unused `TooltipPainter.drawBadge`) — small individually, but exactly the pattern that compounds as more items are added.
- **Unfinished frame art** — `TooltipFrameRenderer` only has texture overlays for 2 of 20 rarities; not urgent, but worth not mistaking for a bug later.
- **`ItemRarity` data-model debt** — cheap to correct now, expensive once dozens of items reference `ItemRarity.ARTIFACT` expecting rarity-tier semantics.
- **Copy-paste as the path of least resistance** — `RingOfProtectionItem` shows what happens today when a contributor needs anything beyond the two hardcoded blocks: it reimplements shared logic by hand rather than composing it. The new contributor model should make "call the shared default" easier than "reimplement it."
- **Latent overflow** — no current item is tall enough to hit the missing scroll/viewport contract, but the moment weapon + stat + lore + multiple extension lines combine on one item, it will.

## 24. Exact files likely to change during future implementation

| File | Expected change |
|---|---|
| `client/tooltip/TotalityTooltipRenderer.java` | Orchestration shrinks to a thin composition over the section-provider list |
| `client/tooltip/TooltipExtension.java` | Superseded/extended by the richer `TooltipSection` contract |
| `client/tooltip/renderer/TooltipStatBlock.java`, `TooltipWeaponBlock.java` | Become section providers; duplicated height-estimation removed |
| `client/tooltip/renderer/TooltipPainter.java` | Gains scroll/viewport clipping; dead `isShiftDown()` removed |
| `client/tooltip/renderer/TooltipFrameRenderer.java` | Frame-texture coverage backlog (separate art task) |
| `api/core/rpgutils/rarity/ItemRarity.java` | ARTIFACT reclassification; possible ANCIENT addition |
| `init/items/EnergyItems.java` | Add RARITY/ITEM_TYPE/WEIGHT/LORE to Iron/Gold/Diamond/Netherite batteries |
| `item/energy/BatteryItem.java` | Retire legacy `appendHoverText` once all tiers migrated |
| `item/magic/GrimoireItem.java` | Route through `TotalityItem` instead of `TooltipExtension` directly |
| `item/equipment/RingOfProtectionItem.java` | Derive tooltip text from `getAcBonus()`/`getSaveBonus()` instead of literal strings |
| `api/rpg/combat/weapon/WeaponDataResolver.java`, `VanillaWeaponStats.java` | Reused (read-only) by the new weapon tooltip resolver |
| `api/rpg/combat/ArmorClass.java`, `armor/VanillaArmorStats.java` | Reused (read-only) by the new armor tooltip resolver |
| `util/TotalityKeyHelper.java` | Adopted as the single shift/ctrl source for tooltip disclosure |
| `mixin/client/AbstractContainerScreenMixin.java` | Extended to forward preserved vanilla lines instead of discarding them |
| *New files* | `TooltipSection`/`TooltipSectionProvider` contract, `WeaponTooltipData`/`ArmorTooltipData` resolvers, `DisclosureLevel` enum |

## 25. Questions that cannot be answered from the repository

- Whether "Ancient" is meant to replace `ARTIFACT`'s current slot in the ladder, or sit above Mythical as a new tier with Artifact demoted to pure classification — no code or comment records the intended final shape.
- Whether Totality intends authored body armor (`LIGHT`/`MEDIUM`/`HEAVY` `TotalityArmorItem`) to exist at all, or whether vanilla armor via `VanillaArmorStats` is meant to remain the long-term "worn armor" path.
- Whether the economy "Value" line belongs on every item's default tooltip or only in shop-context tooltips — `ItemPricingService`/`ItemValueRegistry` read as shop-oriented, and it's unclear if they're meant to double as a general tooltip source.
- Whether a future Ctrl/advanced view should be independent of vanilla's native F3+H "advanced tooltips" toggle, or gated by it.
- Whether multi-classification items (a future magical weapon, an enchantable Totality armor piece) are expected to show more than one `ItemType` badge at once — today's model supports exactly one.

## 26. Confirmation

**No changes made.** No files were modified, staged, committed, or pushed during this audit. Every command executed was read-only: `git status`/`diff`/`log`/`rev-parse`, file reads, greps, and four read-only research agents. The pre-existing uncommitted working-tree state noted in §1 predates this session and was left exactly as found.

Specifically, confirmed as of the completion of this audit:

- **No production or test files were modified.**
- **No implementation work was performed.**
- **No commit was created.**
- **Nothing was pushed.**
- **Phase 3C (Generic Player Resource API) was not started.** This audit is scoped entirely to the Tooltip API and its item integrations; it does not touch, plan the start of, or otherwise advance Phase 3C work.

---

*Totality Tooltip API architecture audit · read-only inspection · `feature/general-resource-api @ 6998d322`*
