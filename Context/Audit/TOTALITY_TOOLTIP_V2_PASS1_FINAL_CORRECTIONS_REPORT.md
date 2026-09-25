# TOTALITY — Tooltip V2 Pass 1 Final Corrections
## Routing scope, header divider removal and Industrial rarity alignment

**Status:** all three corrections are implemented and validated, and PASS 1 is functionally complete. **Not committed, not pushed.** PASS 2 was not started.

**Master reference:** `STEFAN_TOTALITY_MASTER_REFERENCE_v3.6_QA_reviewed.docx` is **still unavailable**; a filesystem-wide search found no copy. §26M and §26F.12 could not be read. The decisions in the final-corrections prompt were applied as authoritative, and no older document was substituted.

---

## 1. Executive summary

| Correction | Result |
|---|---|
| **A. Routing scope** | Universal V2 now covers only the `minecraft` and `totality` item namespaces. Third-party items keep their original tooltip, get no Totality rarity or classification, and have no scroll capture. This is one routing authority (`TooltipRouting`) shared by the renderer entry point, `isEligible` and wheel scrolling |
| **B. Header divider** | The universal header/body divider is **removed entirely**, including its reserved 8px band. The first semantic group heading supplies the only divider under the header, and a bodiless item simply ends after its header and footer |
| **C. Industrial rarity** | Batteries and gears follow Copper CRUDE → Iron CALIBRATED → Gold PROTOTYPE → Diamond OVERCHARGED → Netherite MASTERWORK. Umbra Visor stays CALIBRATED. Gameplay statistics are unchanged (verified on real registrations) |
| **Validation** | clean build passes; **1917 JUnit tests, 0 failures**; **client GameTest 161 PASS / 0 FAIL** on real registered items with **33 real inventory-hover screenshots** at GUI 1/2/4; dedicated server `Done`, `EXIT=0`; **175/175** Totality items author a rarity |

---

## 2. Exact changes

### A. Routing: `client/tooltip/TooltipRouting.java`
- **New route `EXTERNAL_ITEM`.** An item whose actual registry namespace (`BuiltInRegistries.ITEM.getKey(item).getNamespace()`) is neither `minecraft` nor `totality` keeps its original vanilla/mod tooltip.
- **Order:** `NONE` (empty) → `EXTERNAL_ITEM` → `VANILLA_HIDDEN` (hidden tooltip) → `VANILLA_TOOLTIP_COMPONENT` (e.g. bundle) → `TOTALITY`.
- **One authority, no duplicate mechanism.** The existing entry point (`AbstractContainerScreenMixin`, which also keeps its `data.isEmpty()` guard for injected tooltip components), `TotalityTooltipRenderer.isEligible` and `TotalityTooltipScrollHandler` all already go through `TooltipRouting`.
- **Stack-only.** Routing never reads SHIFT/CTRL.
- **Not rated.** `ItemRarityResolver` already resolves third-party items to empty; no change was needed.

### B. Header divider: `client/tooltip/TotalityTooltipRenderer.java`
- **Removed:**
  - the `TooltipDividerPainter.draw(...)` call;
  - `separatorH` (8px) and `separatorY`.
- **Layout now:**
  - `chromeH = headerH + footerH`;
  - `bodyTop = panelY + headerH`;
  - `availableBodyHeight(maxViewportH, headerH, footerH)`.
- **Spacing:**
  - **Header → first group:** `HEADER_BOTTOM_GAP` (2) plus the heading's own `GAP_ABOVE` (3) = 5px. This is the same rhythm used between groups (`ROW_GAP` 2 + 3).
  - **Bodiless item:** header gap (2) plus footer gap and padding (3 + 3) = **8px**, matching the 8px top padding, with no orphan band.
- **One compensating spacing fix, found during screenshot review.**
  - *The problem:* a body that **starts with unheaded content**, e.g. a lore-only item (soul gem, phone), began about 2px under the classification line. The divider band used to hide this.
  - *The fix:* `UNHEADED_BODY_LEAD = TooltipGroupHeadingPainter.GAP_ABOVE` (3px) is applied only in that case, so the first content always starts at the same offset whether or not it is a group heading.
  - It is spacing only, with no separator.
- **Unchanged:** group dividers, full-colour icons, ordering and merging, preview size, centred name and plaque, classification lines, lore, footer, detached modifier cards, invisible scrolling, rarity colours and animation.
- **Comments** describing the old divider were updated.

### C. Industrial rarity: `init/items/EnergyItems.java`, `init/items/IngredientItems.java`

| Item | Before (Pass 1 provisional) | Now |
|---|---|---|
| copper_battery / copper_gear | CRUDE | CRUDE |
| iron_battery / iron_gear | CALIBRATED | CALIBRATED |
| gold_battery / gold_gear | CALIBRATED | **PROTOTYPE** |
| diamond_battery / diamond_gear | REINFORCED | **OVERCHARGED** |
| netherite_battery / netherite_gear | PROTOTYPE | **MASTERWORK** |
| umbra_visor | CALIBRATED | CALIBRATED (kept, provisional) |

- **Stale comment fixed:** the battery block comment in `EnergyItems` claimed "Only Copper has a canonical authored rarity … Iron/Gold/Diamond/Netherite have none". It now states the progression, and that no weight or lore is invented.
- **Unchanged:** coins COMMON; alchemy potions COMMON (family baseline); ruby and vibranium ores COMMON (provisional); `RarityCoverage` dev-time validation; colours and animation; the Writ boundary. No new ladder was added, and ARTIFACT is only read as a legacy alias.

### Tests
| File | Change |
|---|---|
| `TotalityTooltipRendererLayoutPolicyTest` | `availableBodyHeight` tests updated to the 3-argument form |
| `TooltipApiFoundationSourceRegressionTest` | `panelPaddingAndSeparatorHeightWereTightened` → `panelPaddingWasTightened` (padding assertion kept). `headerToBodySeparatorGapWasLightlyIncreased` → `universalHeaderDividerIsRemovedEntirely` (no draw call, no reserved band, chrome = header + footer, body starts at the header, lead constant). The battery test now asserts the full CRUDE→MASTERWORK progression. The routing test also pins the namespace scope |
| GameTest: `GameTestExternalItems` (new, `main` entrypoint of the **test-only** `totality-gametest` mod) | registers `gametest_external:sample_relic`, a stand-in third-party item carrying a vanilla lore line |
| GameTest: `assets/gametest_external/lang/en_us.json` (new) | its name |
| GameTest: `fabric.mod.json` | adds the `main` entrypoint |
| GameTest: `TooltipV2Pass1ClientGameTest` | external routing, modifier stability for Totality and external items, scroll non-capture while hovered, industrial progression, battery stats unchanged, third-party not rated, new screenshot set |

No test was deleted. Superseded source checks were retargeted to the accepted behaviour.

---

## 3. File manifest (task-only, 10 files)

**Modified (8)**
- `src/main/java/zcylas/totality/client/tooltip/TooltipRouting.java`
- `src/main/java/zcylas/totality/client/tooltip/TotalityTooltipRenderer.java`
- `src/main/java/zcylas/totality/init/items/EnergyItems.java`
- `src/main/java/zcylas/totality/init/items/IngredientItems.java` (CRLF preserved)
- `src/test/java/zcylas/totality/client/tooltip/TotalityTooltipRendererLayoutPolicyTest.java`
- `src/test/java/zcylas/totality/client/tooltip/TooltipApiFoundationSourceRegressionTest.java`
- `src/gametest/resources/fabric.mod.json`
- `src/gametest/java/zcylas/totality/gametest/TooltipV2Pass1ClientGameTest.java`

**New (2)**
- `src/gametest/java/zcylas/totality/gametest/GameTestExternalItems.java`
- `src/gametest/resources/assets/gametest_external/lang/en_us.json`

The baseline for `DIFF.patch` is each file's contents at the start of this task, which includes the uncommitted Pass 1 work; it is not HEAD. `ItemRarityResolver.java` was snapshotted but needed no change, so it is not in the patch.

---

## 4. Test results

### JUnit (`./gradlew test --rerun`)
157 classes, **1917 tests, 0 failures, 0 errors, 0 skipped**.

### Client GameTest (`./gradlew runClientGameTest`)
Real registrations, a fresh isolated world (`build/run/clientGameTest`) and real input: **161 PASS / 0 FAIL**.

| Area | Evidence |
|---|---|
| **Routing** | vanilla (21 ordinary items) → V2; Totality → V2; `gametest_external:sample_relic` → `EXTERNAL_ITEM`, not eligible, original lines intact (`"External Sample Relic"`, `"Original third-party lore line"`); bundle (filled and empty) → `VANILLA_TOOLTIP_COMPONENT`; hidden tooltip → `VANILLA_HIDDEN`; empty → `NONE` |
| **Modifiers** | real `holdShift`/`holdControl` in DEFAULT, SHIFT, CTRL and SHIFT+CTRL: disclosure follows the keys; diamond and shuriken stay V2; bundle stays vanilla; external stays `EXTERNAL_ITEM` |
| **Scrolling** | with the item genuinely hovered, `TotalityTooltipScrollHandler.onMouseScroll(-1.0)` returns `false` (not consumed) for the bundle, the external item, and the external item with CTRL held |
| **Rarity** | 175/175 Totality items authored; vanilla COMMON fallback; authored per-stack rarity wins; the third-party item is not rated; batteries and gears CRUDE/CALIBRATED/PROTOTYPE/OVERCHARGED/MASTERWORK; Umbra Visor CALIBRATED; legacy `"artifact"` decodes as ANCIENT |
| **Gameplay** | battery capacity and I/O read from the real registered items are exactly the pre-task values: copper 48000/32/32, iron 320000/32/32, gold 128000/128/128, diamond 1000000/256/256, netherite 5000000/512/512 |
| **Existing Pass 1 coverage** | classification inference and precedence, no duplicate lines, no fabricated statistics, vanilla enchantment lines preserved: all still pass |

**Rarity distribution** (live registry): COMMON 96, UNCOMMON 40, RARE 15, EPIC 3, LEGENDARY 1, BLESSED 1, CRUDE 10, CALIBRATED 3, PROTOTYPE 2, OVERCHARGED 2, MASTERWORK 2.

### Other checks
- **Build:** `./gradlew clean build` passes.
- **Dedicated server:** `Done (1.869s)`, `EXIT=0`. The `RarityCoverage` check runs at startup and passed silently.

### Gameplay non-changes
This task changed no battery or gear statistics, recipes or behaviour; no combat or defense code; no food restoration or balance; and no Block Breaking code. That is confirmed by the file manifest (§3) and the runtime stat checks.

---

## 5. Real-client verification

The client GameTest drives the real inventory hover path. It opens a real `InventoryScreen`, moves the real cursor over each slot, and asserts that the screen's own `hoveredSlot` is that slot before capturing. Full list: `screenshots/SCREENSHOT_MANIFEST.md`.

| Requirement | Screenshots |
|---|---|
| Plain diamond, no body | `gui{1,2,4}_01_plain_diamond_no_body`: header only, no divider, balanced 8px bottom |
| Vanilla tool with MINING | `gui{1,2,4}_02_iron_pickaxe_mining_first_group`: MINING heading directly under TOOL • PICKAXE |
| Multiple semantic groups | `gui{1,2,4}_03_shuriken_multiple_groups`: COMBAT, REQUIREMENTS, DURABILITY; only group dividers |
| Totality battery, corrected rarity | `gui{1,2,4}_04_netherite_battery_MASTERWORK`, `gui2_05_gold_battery_PROTOTYPE`, `gui2_06_diamond_battery_OVERCHARGED`, `gui2_07_netherite_gear_MASTERWORK` |
| Lore/footer, no mechanical body | `gui{1,2,4}_05`/`gui2_08_copper_phone_lore_footer_no_mechanical_body`; `gui2_09_soul_gem_lore_only_body` (lore starts at the same offset a group heading would) |
| Bundle | `gui{1,2,4}_06`/`gui2_11_bundle_vanilla_tooltip_component`: vanilla bundle image |
| Third-party item | `gui{1,2,4}_07`/`gui2_12_external_item_original_tooltip`, plus `gui2_17` (CTRL) and `gui2_19` (SHIFT+CTRL): the original vanilla-style tooltip every time |
| SHIFT/CTRL | `gui2_14_iron_pickaxe_SHIFT`, `gui2_15_iron_pickaxe_CTRL`, `gui2_16_plain_diamond_CTRL_same_renderer`, `gui2_18_battery_SHIFT_CTRL` |
| Also | `gui2_10_stone_properties_then_lore` (PROPERTIES heading under BLOCK); `gui2_13_enchanted_sword_vanilla_lines` |

Across all 33 shots, the first semantic group's divider sits directly beneath the header, and the removed divider left no empty vertical space.

---

## 6. Known limitations

1. **Master unavailable.** Industrial alignment follows the prompt's canonical cues; §26F.12 was not cross-checked.
2. **Provisional rarities remain** by decision: Umbra Visor CALIBRATED, coins COMMON, alchemy potions COMMON, ruby and vibranium ores COMMON.
3. **The authored divider style is now unused.** `TooltipProfileComponent.dividerStyle` / `TooltipPresentation.dividerStyle` and `TooltipDividerPainter` remain as API and persisted data (removing them would change a persisted component codec), but the renderer no longer draws a header divider. Whether to retire that API is a decision for later.
4. **The test-only external item** shows a missing-texture icon in inventory slots: its `minecraft:stick` item-model id did not resolve. This is cosmetic and test-only; its tooltip, which is what is under test, is correct.
5. **Seeds and crop BlockItems** still show authoritative Block Durability rows. This predates this task (audit C11).
6. **Vanilla attribute lines** are still preserved as raw text until Core. This predates this task, by decision.
7. **Maps with data** were not examined, as in Pass 1.
8. **`runClientGameTest` is not part of `build`,** and it needs a display.

## 7. Unresolved issues requiring Stefan's decision
- Whether to retire the now-unused authored divider-style API (§6.3).
- Final rarities for the provisional items (§6.2), when their content design is settled.

No functional issue is open. PASS 1's acceptance criteria are re-checked and met: routing, special components, modifier stability, hybrid classification, authored precedence, rarity coverage and validation, vanilla baseline, real-item tests, real-client screenshots, and unrelated files untouched.

---

## 8. Repository state

| | Branch / HEAD | Status | Staged |
|---|---|---|---|
| **Pre-task** | `master` / `7213407` | 86 M, 1 D, 99 ?? | 0 |
| **Post-task** | unchanged | 86 M, 1 D, 101 ?? | 0 |

- **Delta:** the two new GameTest files. The other 8 changed files were already modified or untracked from Pass 1.
- **Deliverables:** this report adds one more untracked file. The ZIP is in the gitignored `Context/Audit/Review Bundles/`.
- **Unrelated work preserved:** no reset, clean, stash, branch switch, commit or push. Every modified file was snapshotted before its first edit. No file outside the 10 in §3 changed (checked by mtime and by the git delta).
- **`run/`:**
  - The client GameTest used `build/run/clientGameTest`, and the server smoke used a scratch universe.
  - The smoke run rotated `run/logs` and rewrote four top-level files. All were restored from the verified pre-task backups and re-verified identical in content and mtime.
  - Every `level.dat` is unchanged, and no crash report was created.
- **Pre-existing temporary artifacts** were left in place, e.g. the earlier scratch universes and `build/run`.

**No commit and no push were performed.**
