# Petty Soul Gem — Implementation Report

**Date:** 2026-09-15
**Scope:** Small, isolated content addition. No Enchanting/Soul Trap/soul-storage system was started or implied by this change.

---

## Summary

Added `totality:petty_soul_gem` as a normal, registered, inert Totality item using the model/texture already present in the worktree (placed there via the Blockbench/Astra workflow). No new item class, component, or gameplay system was created — it is a plain vanilla `Item` carrying Totality's existing rarity/tooltip/lore components, following the exact pattern used by the Grimoire/Ring/Arcane Focus items in `MagicItems.java`.

---

## Files created / changed

| File | Change |
|---|---|
| `src/main/resources/assets/totality/models/item/petty_soul_gem.json` | **Corrected** (not authored by me) — the texture reference was `totality:petty_soul_gem` in both the `"0"` and `"particle"` keys, which does not match this repo's established texture-path convention (`totality:item/<name>`, confirmed against the working `iron_sword.json`/`asauchi.json` references) and would not have resolved at runtime. Fixed to `totality:item/petty_soul_gem`. No geometry, UV, or display-transform data was touched. |
| `src/main/resources/assets/totality/textures/item/petty_soul_gem.png` | Pre-existing (already present in the worktree, added by a prior/parallel session — not modified). |
| `src/main/java/zcylas/totality/init/items/MagicItems.java` | Added the `PETTY_SOUL_GEM` registration (plain `Item`, `Common` rarity, `MAGICAL` classification, lore, tooltip profile opt-in). |
| `src/main/java/zcylas/totality/datagen/ModModelProvider.java` | Added the item-model-definition datagen entry pointing at the hand-authored model, same pattern as `iron_sword`/`asauchi`. |
| `src/main/java/zcylas/totality/datagen/ModEnglishLangProvider.java` | Added the English display name. |
| `src/main/java/zcylas/totality/init/ModGroups.java` | Added the item to the existing **Magic** creative tab's display list. |
| `src/main/generated/assets/totality/items/petty_soul_gem.json` | Datagen output (new) — item-definition JSON pointing to `totality:item/petty_soul_gem`. |
| `src/main/generated/assets/totality/lang/en_us.json` | Datagen output (regenerated) — adds `"item.totality.petty_soul_gem": "Petty Soul Gem"`. |

No item class, component, recipe, loot table, tag, or Enchanting-API file was created.

---

## Existing patterns inspected before implementing

- **Item registration:** `TotalityRegistry.registerItem(name, factory, properties)` (`init/TotalityRegistry.java`) — handles registry-key creation and asserts the translation key matches convention.
- **Modeled items:** `iron_sword.json`/`asauchi.json` (`assets/totality/models/item/`) — full Blockbench-exported models, hand-placed under `src/main/resources` (not datagen-owned), wired via a single `generators.itemModelOutput.accept(ITEM, ItemModelUtils.plainModel(Identifier...))` line in `ModModelProvider.java`. Used as the direct template for the Soul Gem's model wiring.
- **Rarity/tooltip/lore infrastructure:** `ItemComponents` (`api/core/rpgutils/rarity/`) — `RARITY`/`ITEM_TYPE`/`LORE`/`CLASSIFICATIONS`/`TOOLTIP_PROFILE` data components, all persistent + network-synced. `ItemRarity.COMMON` is part of the existing "Standard Progression" rarity ladder. `TooltipProfileComponent.STANDARD` is the explicit, current opt-in marker for Totality's custom tooltip renderer (its own Javadoc recommends new registrations set it explicitly rather than relying on the legacy rarity-only fallback).
- **Recent full registration examples:** `MagicItems.java` (Grimoires, Ring of Protection, Arcane Foci) — the exact component-chaining pattern (`.component(ItemComponents.getTooltipProfile(), ...).component(ItemComponents.getRarity(), ...).component(ItemComponents.getItemType(), ...).component(ItemComponents.getClassifications(), ...).component(ItemComponents.getLore(), ...)`) was reused verbatim for the Soul Gem.
- **Creative groups:** `ModGroups.java` — the Magic tab already lists Grimoires, Ring of Protection, Arcane Foci, and the full Rune set.
- **Item tags:** checked `ModItemTagProvider.java` — only Grimoires carry `ModTags.SPECIAL` (Rings/Arcane Foci/Runes do not); the Soul Gem follows the majority pattern and gets no tag.

No new registration, rendering, or component architecture was created — everything reused existing infrastructure exactly as designed.

---

## Registry ID

`totality:petty_soul_gem`

## Player-facing name

**Petty Soul Gem** (`item.totality.petty_soul_gem`)

## Rarity

**Common** (`ItemRarity.COMMON`), via `RarityComponent` — Totality's existing canonical rarity system, not a vanilla color or an invented mechanism.

## Chosen item group

**Magic** (`itemGroup.totality.magic`) — the existing tab already used for Grimoires, Ring of Protection, Arcane Foci, and Runes. A Soul Gem is a magic trinket/material in exactly the same sense as those, and no Enchanting-specific tab exists (none was created for this task, per instructions).

## Classification

`ItemType.MAGICAL`, applied via both `ItemTypeComponent` (legacy field, kept for pre-existing fallback compatibility) and `ClassificationsComponent.of(ItemType.MAGICAL)` (current system) — same dual-write pattern every other `MagicItems` entry uses.

---

## Final player-facing lore

> *"The smallest of the conventional soul gems, its facets barely wide enough to cradle a soul at all — enough to hold only the faintest sparks of life, and nothing more."*

Written after researching (not copying) UESP's description of the Elder Scrolls soul-gem hierarchy: souls scale Petty → Lesser → Common → Greater → Grand (plus Black, for sapient/humanoid souls), and Petty gems are canonically the smallest tier, only able to hold the very weakest creature souls. The lore line communicates "smallest/weakest tier, holds only minor souls" without naming or promising Soul Trap, capture, filling, or any mechanic that doesn't exist yet in this codebase.

### Web sources consulted

- [Skyrim:Soul Gems — UESP Wiki](https://en.uesp.net/wiki/Skyrim:Soul_Gems)
- [Skyrim:Souls — UESP Wiki](https://en.uesp.net/wiki/Skyrim:Souls)

---

## Model / texture paths

- Model (hand-authored, Blockbench export): `src/main/resources/assets/totality/models/item/petty_soul_gem.json`
- Texture: `src/main/resources/assets/totality/textures/item/petty_soul_gem.png` (64×64 RGBA, matches the model's declared `texture_size`)
- Generated item definition: `src/main/generated/assets/totality/items/petty_soul_gem.json` → `{"model": {"type": "minecraft:model", "model": "totality:item/petty_soul_gem"}}`

Both assets were already present in the worktree (from the Blockbench/Astra export) — nothing was invented. The only asset edit made was correcting the model's internal texture reference from `totality:petty_soul_gem` to `totality:item/petty_soul_gem` so it actually resolves against the texture's real resource path; this is a path correction required for the existing export to function under this repo's established convention, not a geometry change.

---

## Item behavior (intentionally inert)

Plain vanilla `Item` — no custom class. No soul capacity, capture, Soul Trap interaction, filled/empty NBT state, filled variant, enchantment fuel behavior, soul ownership, mob classification, recipe, loot table, worldgen, merchant stock, or quest integration was added. No Enchanting API class exists or was touched. The item is obtainable today only via creative inventory / `/give`.

---

## Validation results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **BUILD SUCCESSFUL** |
| `./gradlew runDatagen` | **BUILD SUCCESSFUL** — `written: 2` (new item-definition JSON + updated lang file); no errors |
| `./gradlew test` | **BUILD SUCCESSFUL** — 1442 tests, 0 failures, 0 errors (full suite, unaffected by this change) |
| `./gradlew clean build` | **BUILD SUCCESSFUL** (compile, datagen, test, jar, sourcesJar, check, all green) |
| `git diff --check` | Clean — no whitespace errors introduced |
| Item registration | Succeeds — `TotalityRegistry.registerItem`'s own translation-key assertion passed silently during every run above |
| English name generated | Confirmed in `src/main/generated/assets/totality/lang/en_us.json`: `"item.totality.petty_soul_gem": "Petty Soul Gem"` |
| Model/texture resolve, no missing-model warning | **Confirmed directly** — launched the dev client (`./gradlew runClient`) and captured its resource-pack reload log. `totality:item/petty_soul_gem` does **not** appear among the reload's "Missing textures in model" / "Missing block model" warnings, while several *other*, pre-existing Totality items (`blessed_incense`, `shinigami_robe`, `incense`, `credits`, and even `asauchi`'s particle reference) do — proving the check is meaningful and that this item's model+texture resolve cleanly. |
| Dedicated server startup not broken | **Confirmed directly** — launched `./gradlew runServer` against the existing dev world; it reached `Done (0.312s)! For help, type "help"` with zero item-registration errors or exceptions anywhere in the log. (Some pre-existing, unrelated dev self-test failures were observed — `ProvisionerEntityBackedSmokeTest` and `OffhandAttackVerification`, both about NPC/combat mechanics with no relationship to items or rarity/tooltip infrastructure — these are stale dev-world-state issues that predate this change; not investigated further, out of this task's scope.) |
| Item appears in the Magic creative tab | Added via `output.accept(MagicItems.PETTY_SOUL_GEM)`; not independently screenshotted (see below). |
| Live in-inventory render | **Not independently screenshotted** — I have no tool for interacting with a native game window. However, the dev client launched during validation is/was actually open and fully loaded (reached the main menu) with the resource pack confirmed clean for this item; you're welcome to check it live (creative inventory search "Petty Soul Gem" or the Magic tab) if the window is still open, or launch fresh. |

---

## Everything intentionally deferred to the future Enchanting system

Per the task's explicit scope boundary, none of the following were implemented, stubbed, or scaffolded:

- Soul capacity / soul-size logic
- Soul capture / Soul Trap interaction
- Filled vs. empty NBT/component state
- A "Filled Petty Soul Gem" variant
- Enchantment fuel / spending behavior
- Soul ownership tracking
- Mob soul-size classification
- Crafting recipes
- Loot table entries
- World generation placement
- Merchant/Provisioner stock entries
- Quest integration
- Any Enchanting API class (`SoulGemItem`, `SoulGemComponent`, `SoulData`, `ResourceGrantProvider`-style ownership, etc.)

The item is deliberately a plain `Item` so a later Enchanting pass can attach whatever behavior it needs cleanly, without this task having encoded assumptions about that future system's shape.
