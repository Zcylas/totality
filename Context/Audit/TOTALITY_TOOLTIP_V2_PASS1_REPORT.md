# TOTALITY — Tooltip V2 Pass 1: Universal Routing, Hybrid Classification, Rarity Coverage and Real-Item Tests

**Status:** implemented and validated. **Not committed, not pushed.** Work stopped after the review deliverables; PASS 2 was not started.

**Master reference:** `STEFAN_TOTALITY_MASTER_REFERENCE_v3.6_QA_reviewed.docx` was **unavailable again**. A filesystem-wide search found no copy. The decisions stated in the Pass 1 prompt were treated as authoritative, and no older document was substituted for them.

---

## 1. Executive summary

| Requirement | Result |
|---|---|
| Every vanilla and Totality item uses V2 | Done. Routing depends only on the stack. Two explicit compatibility exceptions keep vanilla's path: a structured tooltip component (e.g. bundle), and a hidden tooltip |
| SHIFT/CTRL never change the renderer | Done. The old content-driven gate is gone. Verified with **real simulated key holds** in all four modifier states |
| Hybrid classification (authored → exact vanilla map → reliable inference → omit) | Done: `ItemClassificationResolver`, presentation-only. 14 narrowly added subtypes |
| Every Totality item has an authored rarity | Done. **175/175** registered Totality items author a rarity; 91 were added in this pass. The dev-time guard fails startup with the registry id (demonstrated) |
| Vanilla COMMON baseline | Done: `ItemRarityResolver`. Authored overrides win; never derived from material |
| Real registered-item tests | Done. New Fabric **client GameTest**: real registries, bound components and tags, a real world, real input. **124 checks, 0 failures** |
| Real-client screenshots through the genuine inventory hover path | Done. **26 screenshots** at GUI scales 1, 2 and 4. Each shot asserts that the screen itself reported the intended slot as hovered |
| Build / JUnit / dedicated server | `clean build` passes; **1917 JUnit tests, 0 failures**; dedicated server `Done`, `EXIT=0` |

---

## 2. Implementation changes

### 2.1 Routing: `client/tooltip/TooltipRouting.java` (new)
`TooltipRouting.of(ItemStack)` returns one of four routes:

| Route | When |
|---|---|
| `NONE` | empty stack |
| `VANILLA_HIDDEN` | `DataComponents.TOOLTIP_DISPLAY.hideTooltip()`. Vanilla shows nothing, and V2 must not bring it back |
| `VANILLA_TOOLTIP_COMPONENT` | `stack.getTooltipImage().isPresent()` |
| `TOTALITY` | everything else |

- `TotalityTooltipRenderer.isEligible(stack)` is now simply `TooltipRouting.of(stack) == TOTALITY`.
- The mixin's existing `data.isEmpty()` guard is unchanged, so another mod's injected tooltip data still falls back to vanilla.
- `TotalityTooltipScrollHandler` shares the same `isEligible`. Bundles are therefore never captured by V2 wheel scrolling and keep their own interactive slot scrolling.

**Special-component investigation:**
- **Bundles, filled and empty:** always expose a `BundleTooltip` image. It is interactive (slot selection, wheel scrolling) and the V2 panel cannot host it yet, so bundles keep vanilla's path. This is a documented exception, verified in screenshots.
- **Filled map without an id:** observed routing to V2 (no tooltip image). Maps were not examined further.
- **Shulker boxes and other text-only content:** their vanilla lines reach V2 through the existing `ExternalContentContributor`.

**Removed:** the content-driven gate, which walked every contributor using the live disclosure level. That gate was the cause of the audit's CTRL renderer switch.

### 2.2 Classification: `api/core/rpgutils/rarity/ItemClassificationResolver.java` (new)
Resolution, in priority order:

1. **Authored:** `ItemComponents.classificationEntriesOf(stack)`, which also covers legacy flat lists and the old `item_type` component. It always wins, including per-stack components, and is never merged with inferred data.
2. **Exact vanilla map:**

   | Item | Classification |
   |---|---|
   | bow | WEAPON • BOW |
   | crossbow | WEAPON • CROSSBOW |
   | trident | WEAPON • TRIDENT |
   | mace | WEAPON • MACE |
   | shears, fishing rod, brush | TOOL |

3. **Reliable inference** (first match wins):

   | Source | Classification |
   |---|---|
   | `#minecraft:swords` | WEAPON • SWORD |
   | `#minecraft:spears` | WEAPON • SPEAR |
   | `#c:tools/bow` / `crossbow` / `mace` | WEAPON • BOW / CROSSBOW / MACE |
   | `#minecraft:pickaxes` / `axes` / `shovels` / `hoes` | TOOL • PICKAXE / AXE / SHOVEL / HOE |
   | `#minecraft:head_armor` / `chest_armor` / `leg_armor` / `foot_armor` | ARMOR • HELMET / CHESTPLATE / LEGGINGS / BOOTS |
   | `instanceof PotionItem` (including splash and lingering) | POTION |
   | has `DataComponents.FOOD` | FOOD |
   | a `BlockItem` named as its block (`block.*` description id) | BLOCK |
   | `#c:gems` / `#c:ingots` | MATERIAL • GEM / MATERIAL • INGOT |

   Seeds, redstone, string and similar are named `item.*`, so they are *not* called BLOCK. This relies on vanilla's own naming contract (`useBlockDescriptionPrefix`), not on registry-name substrings.
4. **Otherwise nothing**, never a placeholder: for example a stick, an enchanted book or a coin.

The resolver is presentation-only. Gameplay code keeps using the authored-only `ItemComponents.classificationsOf` (e.g. `FuelContributor`), so classification never creates gameplay behaviour or statistics.

- **Taxonomy additions** (`ClassificationTypes`, 14 subtypes): `sword`, `spear`, `shovel`, `hoe`, `bow`, `crossbow`, `trident`, `mace`, `helmet`, `chestplate`, `leggings`, `boots`, `gem`, `ingot`. They are localised through `ModEnglishLangProvider`, and datagen regenerated `en_us.json` with only these 14 keys. No `ItemType` categories were added.
- `MetadataContributor` now reads the two resolvers. The renderer already took rarity and classification only from its sections, so theme, plaque, name animation and category/subtype lines are unchanged.

### 2.3 Rarity: `ItemRarityResolver.java` and `RarityCoverage.java` (new)
- **`ItemRarityResolver.resolve(stack)`:**
  - The authored `RarityComponent` wins. That includes exact vanilla overrides such as `VanillaItemPresentation` and per-stack components.
  - Otherwise a `minecraft:` item resolves to **Standard COMMON**.
  - Otherwise the result is empty (other mods' items show no rarity).
  - Vanilla's own `DataComponents.RARITY` is deliberately ignored: vanilla raises rarity when an item is enchanted, and that bump must not leak in. Tested.
  - The palette, colours, animation and localisation are unchanged.
- **`RarityCoverage`:** on `SERVER_STARTED` it lists Totality items whose default components lack `totality:rarity`. Default components are only bound after server data loads in 26.2, so the check can't run at registration.
  - In a **development environment** it throws `IllegalStateException` naming the registry ids.
  - In **production** it only logs, so players never crash.
  - Registered in `Totality.registerInits()`.
  - **Demonstrated:** I temporarily removed the wrench's rarity and started the dev dedicated server. Startup failed with `Totality items registered without an authored ItemRarity (...): [totality:wrench]` (`logs/rarity_guard_demo_crash_report.txt`). The file was restored byte-for-byte, and the crash report that run wrote into `run/crash-reports/` was removed (§8).
- **Registration helpers:**
  - `registerFluidTank`, `registerEnergyCell` and `registerCable` now take an explicit `ItemRarity`.
  - The six potion helpers author one documented family constant, `ALCHEMY_POTION_RARITY = COMMON`.
  - Plain `registerBlock(..., true)` call sites now use the existing `Item.Properties` overload with an authored rarity. It produces the same `BlockItem` with the same block description prefix.
- **Writ boundary:** untouched. Writ contract rank is not `ItemRarity`, and nothing maps it onto the ladder.

### 2.4 Input: `util/TotalityKeyHelper.java`
The modifier readers now use vanilla's `InputConstants.isKeyDown(window, key)`, which in normal play is exactly `glfwGetKey(window, key) == PRESS`, instead of calling GLFW directly. Behaviour is identical for players. The change is needed so simulated input in the client GameTest is observed like a real keyboard; without it, real-key modifier tests are impossible.

### 2.5 Test infrastructure: `build.gradle` + `src/gametest/`
- `fabricApi.configureTests { createSourceSet; modId "totality-gametest"; enableClientGameTests; enableGameTests=false; eula=false; clearRunDirectory }`.
- Run with **`./gradlew runClientGameTest`**. It uses the disposable `build/run/clientGameTest` directory with a fresh world and never touches `run/`.
- **It is not part of `./gradlew build`.** It needs a display, so it must be run separately.
- New test mod: `src/gametest/resources/fabric.mod.json`, entrypoint `fabric-client-gametest`, plus `TooltipV2Pass1ClientGameTest`.

---

## 3. Files

**New (6)**
- `src/main/java/zcylas/totality/client/tooltip/TooltipRouting.java`
- `src/main/java/zcylas/totality/api/core/rpgutils/rarity/ItemClassificationResolver.java`
- `src/main/java/zcylas/totality/api/core/rpgutils/rarity/ItemRarityResolver.java`
- `src/main/java/zcylas/totality/api/core/rpgutils/rarity/RarityCoverage.java`
- `src/gametest/resources/fabric.mod.json`
- `src/gametest/java/zcylas/totality/gametest/TooltipV2Pass1ClientGameTest.java`

**Modified (19)**

| File | Change |
|---|---|
| `build.gradle` | client GameTest configuration |
| `src/main/java/zcylas/totality/Totality.java` | registers the rarity guard |
| `.../api/core/rpgutils/rarity/ClassificationTypes.java` | 14 subtypes |
| `.../client/tooltip/TotalityTooltipRenderer.java` | `isEligible` |
| `.../client/tooltip/contributor/MetadataContributor.java` | uses the resolvers |
| `.../datagen/ModEnglishLangProvider.java` + `src/main/generated/assets/totality/lang/en_us.json` | 14 keys |
| `.../util/TotalityKeyHelper.java` | reads keys through `InputConstants` |
| `.../init/TotalityRegistry.java` | helper rarity |
| `.../init/ModBlocks.java` | copper tank rarity |
| `.../init/blocks/AlchemyBlocks.java`, `EnergyBlocks.java`, `OreBlocks.java` | block item rarity |
| `.../init/items/EnergyItems.java`, `CurrencyItems.java`, `IngredientItems.java`, `SpellComponentItems.java`, `ToolItems.java` | item rarity |
| `src/test/.../client/tooltip/TooltipApiFoundationSourceRegressionTest.java` | 4 superseded source checks retargeted |

Line endings are preserved. The CRLF files stay CRLF; the only CR increases are the added lines.

---

## 4. Existing Totality item rarity coverage

This comes from the live registry in the client GameTest (`RARITY` lines in `test-results/totality-pass1-results.txt`).

**Totals:** 175 items. By value: COMMON 96, UNCOMMON 40, RARE 15, EPIC 3, LEGENDARY 1, BLESSED 1, CRUDE 10, CALIBRATED 5, REINFORCED 2, PROTOTYPE 2.

**Existing authored rarities were all preserved.** For example: Credits, Copper Battery and Copper Phone are CRUDE; the shurikens, grimoires and runes keep theirs; Blessed Incense is BLESSED.

**Authored in this pass (91 items):**

| Items | Rarity | Reasoning |
|---|---|---|
| wrench, copper_gear, generator, electric_furnace, copper_energy_cell, copper_cable, copper_tank | CRUDE (Industrial) | copper/basic hardware tier, matching the existing "copper hardware = CRUDE" precedent (Copper Battery, Copper Phone) |
| iron_battery, gold_battery, umbra_visor, iron_gear, gold_gear | CALIBRATED (Industrial) | next hardware grade (**provisional**) |
| diamond_battery, diamond_gear | REINFORCED (Industrial) | **provisional** |
| netherite_battery, netherite_gear | PROTOTYPE (Industrial) | **provisional**; deliberately not the top rungs |
| copper/silver/gold/platinum_coin | COMMON | **ambiguous**: a denomination-based ladder is a design call |
| raw_tin, graphite, rough_ruby, true_wheat_seeds | COMMON | ordinary materials |
| bat_guano, sulphur_dust, fur, glass_rod, component_pouch | COMMON | ordinary spell components |
| arcane_focus | UNCOMMON | matches the other `ArcaneFocusItem`s (arcane_orb, bard_guitar), already authored UNCOMMON |
| apothecary_table, blue/purple/red_mountain_flower_bush | COMMON | ordinary blocks |
| 12 ore block items (tin, graphite, lead, silver, ruby, vibranium, plus deepslate variants) | COMMON | **ambiguous for ruby and vibranium**: no higher tier assigned without support |
| 45 alchemy potions | COMMON (family constant) | **ambiguous**: a per-tier/per-effect ladder is an open decision. The D&D Potion of Healing keeps UNCOMMON |

---

## 5. Test evidence

### 5.1 JUnit (`./gradlew test --rerun`)
- 157 classes, **1917 tests, 0 failures, 0 errors, 0 skipped**.
- Four source-text checks pinned the superseded content-driven gate or "no rarity on non-copper batteries". They were retargeted, **and remain source-text checks** (they are not integration tests):
  - `isEligibleIsTheUniversalStackOnlyRoutingDecision`
  - `routingKeepsFunctionalVanillaTooltipComponentsAndHiddenTooltips`
  - `batteryTiersAuthorRarityButNoInventedWeightOrLore`, which still forbids invented weight or lore
- The net count is −1: three removed, two added.

### 5.2 Client GameTest (`./gradlew runClientGameTest`): real registered items
**124 PASS / 0 FAIL**, plus 26 screenshot entries and 175 rarity listings. Items used:
- **Vanilla:** diamond, iron ingot, iron sword, iron pickaxe, bread, iron chestplate, stone, oak planks, wheat seeds, bow, enchanted book, potion, stick, Sharpness III diamond sword, bundle (filled and empty).
- **Totality:** netherite shuriken, copper battery, pizza, petty soul gem, copper coin, credits, tin ore, copper energy cell.

| Criterion | Evidence |
|---|---|
| 1. Eligibility | 21 ordinary items → `TOTALITY` and `isEligible`; empty → `NONE`; filled and empty bundle → `VANILLA_TOOLTIP_COMPONENT`; hidden tooltip → `VANILLA_HIDDEN` |
| 2–4. Rarity, authored precedence, vanilla fallback | diamond COMMON with no component; enchanted sword stays COMMON (vanilla's own rarity is higher); stone's exact override is authored; a per-stack RARE wins; 8 Totality items match their authored values; **all 175 Totality items authored** |
| 5–6. Classification and authored precedence | 14 expected results, including gem, ingot, sword, pickaxe, food, chestplate, block, bow, potion, and omission for seeds, book and coin; battery and shuriken authored lists returned unchanged; a per-stack authored WEAPON on a diamond replaces the inference, with no merge |
| 7. Unknown metadata omitted | seeds, enchanted book, coin → no classification |
| 8. No duplicate lines | category/subtype lines are distinct for all 21 ordinary items |
| 9. No fabricated statistics | diamond, stick, coin, enchanted book, iron sword: no stat rows or group headings |
| 10. Stable eligibility under modifiers | real `holdShift`/`holdControl` in all four states: disclosure follows the keys (`DEFAULT`/`DETAILS`/`TECHNICAL`/`DETAILS_AND_TECHNICAL`), the diamond stays V2, the bundle stays vanilla |
| 11. Special components stay functional | the bundle keeps vanilla's image path and the V2 scroll capture never engages; screenshots gui2_08, gui2_17, gui1_04, gui4_04 |
| Legacy persisted data | a real `ItemStack.CODEC` decode of `"totality:rarity":"artifact"` → ANCIENT; flat `["weapon"]` → WEAPON (taking precedence over the legacy `item_type`); `item_type` alone → TOOL |
| Vanilla lines survive | the real `getTooltipLines` of the Sharpness III sword reaches V2 through ExternalContent ("Sharpness III", "7 Attack Damage", …) |

**Limitations of this evidence:**
- The GameTest runs in the dev environment with default datapacks. Classification that depends on tags could change under datapacks that edit those tags. That is intended vanilla semantics, but only default tags were tested.
- A first run failed a single check (`no_fabricated_stats.minecraft:wheat_seeds`). Seeds are a crop `BlockItem`, so the existing, authoritative Block Durability rows apply. The test's expectation was wrong, not the code; it now uses a stick, and seed behaviour is listed in §7.

---

## 6. Runtime evidence: genuine inventory hover
- The client GameTest opens a real `InventoryScreen` in a fresh singleplayer world. The items are placed server-side and synced.
- It moves the **real cursor** (`TestInput.setCursorPos`) over each slot, then asserts that `AbstractContainerScreen.hoveredSlot` is that slot before capturing.
- The tooltip therefore goes through `AbstractContainerScreenMixin` with vanilla's own original lines, not a direct renderer call with synthetic lines.
- `screenshots/SCREENSHOT_MANIFEST.md` lists all 26 shots: item, GUI scale, modifier, routing.

Observed in the screenshots:
- A plain diamond shows the V2 header with COMMON and MATERIAL • GEM.
- The vanilla sword shows WEAPON • SWORD, the durability bar, and vanilla's attribute lines.
- The enchanted sword shows "Sharpness III".
- The bundle shows vanilla's bundle tooltip, and still does with CTRL held.
- CTRL on the diamond keeps V2 and adds the technical block.
- The battery with SHIFT+CTRL is correct.
- GUI 1 and GUI 4 lay out correctly.

---

## 7. Known issues and limitations
1. **Master unavailable.** §26M and §26F.12 could not be cross-checked.
2. **Provisional or ambiguous rarities** are flagged in §4: the Industrial tier ladder for batteries, gears and the Umbra Visor; coins; alchemy potions; ruby and vibranium ores.
3. **A bodiless item ends with the header divider.** For example, a plain diamond now shows the divider followed only by footer padding. This case couldn't occur before universal routing. The presentation was not changed; this is a PASS 2 decision.
4. **Seeds and crop BlockItems** show "Block Durability 0" and a Required Tier from the existing Block Breaking contributor. This predates this pass (audit C11).
5. **Vanilla attribute lines** ("Attack Damage", "Armor") are still preserved as raw text. Replacing them is deferred to Core, as decided.
6. **Other mods' items** get V2 and inferred classification but no rarity (empty, not COMMON).
7. **Custom `TOOLTIP_STYLE` backgrounds** are not applied inside V2. This predates this pass.
8. **Filled maps** route to V2. Only a map without an id was observed; maps with data were not examined.
9. **`runClientGameTest` is not part of `build`**, and it requires a display.
10. **The rarity guard checks registered default components only,** not per-stack data.
11. **The dev-time guard throws on server start.** A future item without a rarity blocks dev startup with its id. That is intentional, but it also affects `runVerificationServer`.

---

## 8. Repository safety and runtime files
- **Pre-task:** branch `master`, HEAD `7213407`; 75 M, 1 D, 92 ??; 0 staged (`GIT_STATUS_BEFORE.txt`).
- **Post-task:** 86 M, 1 D, 98 ??; 0 staged (`GIT_STATUS_AFTER.txt`). The delta is exactly the 25 task files listed in §3.
- **The deliverables add** one more untracked file, this report (`Context/Audit/TOTALITY_TOOLTIP_V2_PASS1_REPORT.md`). The review ZIP is under the gitignored `Context/Audit/Review Bundles/`.
- **No commit, no push**, and no reset, stash, clean or branch switch.
- **`run/`:**
  - The client GameTest used `build/run/clientGameTest`. The dedicated-server runs used scratch universes.
  - The server runs rotated `run/logs` and rewrote `banned-ips.json`, `banned-players.json`, `ops.json` and `server.properties`. All were restored from backups verified before the task and re-verified identical in content and mtimes. Every `level.dat` is unchanged.
  - The guard demonstration's crash report (`run/crash-reports/crash-2026-09-24_18.55.38-server.txt`, created by this task) was copied into the bundle and removed. The directory's own mtime changed as a result; no pre-existing crash report was touched.
- **Datagen** changed only the 14 new `en_us.json` keys. The gitignored `.cache` was refreshed as usual.
- **A temporary probe test** (`src/test/java/zcylas/totality/probe/`, used to establish that plain JUnit can't bind 26.2 item components) was deleted and is not in the patch.

---

## 9. Remaining work for PASS 2 (not started)
- Decide the empty-body divider (§7.3).
- Fuel ungating: `level.fuelValues()` for every item.
- Enchantments group from `ENCHANTMENTS` / `STORED_ENCHANTMENTS`.
- Soul Gem, rune, currency and alchemy contributors.
- Energy Cell UE view.
- Review the provisional rarities in §4.
- Wider automatic wearable companion previews: the dedicated equipment pass.
- Still deferred:
  - Combat and AC, and the defense group: Core.
  - Food values: Vanilla Food Rebalance V1.
  - Material: Block Breaking V2.
  - Fluid, Diet, Writ, ALT/Codex.
  - Dedicated-server price sync.
