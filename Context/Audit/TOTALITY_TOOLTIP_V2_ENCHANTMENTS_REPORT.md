# TOTALITY — Tooltip V2 Enchantments Integration

**Status:** implemented and validated. This is a presentation-only content integration. **Not committed, not pushed.** No other item-family integration was started, and the future Enchanting API was not implemented.

**Master reference:** `STEFAN_TOTALITY_MASTER_REFERENCE_v3.6_QA_reviewed.docx` is **still unavailable**; a filesystem-wide search found no copy. The Tooltip V2 and Enchanting sections could not be consulted. The task prompt's decisions were applied, and no older document was substituted.

---

## 1. Implementation summary

**What the player now sees**
- An enchanted item, or an enchanted book, shows the existing **ENCHANTMENTS** group, under its own heading and divider, with one semantic row per enchantment.
  - **DEFAULT:** the localised name and actual level, e.g. `Sharpness  V`, `Mending  I`.
  - **SHIFT/DETAILS:** the level against the enchantment's registered maximum, e.g. `III / V`.
  - **Curses** are marked with a red level and are never styled as ordinary enchantments.
- The raw vanilla enchantment lines those rows represent are **no longer shown twice**. All other original text survives: attributes, lore and item-specific lines.

**What is unchanged**
- Rarity, classification, routing, the header, lore and the footer.
- All gameplay.

### How it works

- **`TooltipEnchantments`** (new, package-private) reads Minecraft's own authoritative data. There is no Tooltip V2 enchantment table.
  - **Components:** `DataComponents.STORED_ENCHANTMENTS` (enchanted books) and `DataComponents.ENCHANTMENTS`, each only when the stack's `TooltipDisplay.shows(type)`, exactly as vanilla does.
  - **Ordering** is taken from 26.2's own bytecode for `ItemEnchantments#addToTooltip`: entries in the `#minecraft:tooltip_order` enchantment tag first, then the rest.
  - **Per entry:** the registry `Holder<Enchantment>`, the actual level, `holder.is(EnchantmentTags.CURSE)`, and `vanillaLine = Enchantment.getFullname(holder, level)`. That last one is the exact component vanilla prints.
  - **Names** come from the registry's `Enchantment.description()`. This covers vanilla and data-driven Totality enchantments alike (verified with `totality:impact`).
  - **Levels** use vanilla's `enchantment.level.N` keys, with plain digits beyond vanilla's table.
- **`EnchantmentsContributor`** (new): `bodyGroup` → `TooltipGroups.ENCHANTMENTS`.
  - It emits one existing `StatRow` per entry: label = name, value = level, or `level / max` under DETAILS.
  - Value colour is `0xFFB9A8F0`, or red `0xFFFF5555` for curses.
  - Rows are `WHEN_IDENTIFIED`, the same gate the raw lines carried, so an identification or knowledge system that conceals them keeps them concealed.
  - `availableDisclosureLevels` offers DETAILS only when there is at least one row, so the SHIFT panel appears only on enchanted items.
  - No new renderer, section type or heading. Group ordering is untouched (ENCHANTMENTS is an ordinary group at priority 900).
- **`TooltipContributorRegistry`:** the contributor is registered after `HealingPotionContributor`. External and Technical remain last.
- **Dedup in `ExternalContentContributor`:**
  - Investigation: in 26.2, each enchantment line vanilla prints is exactly `Enchantment.getFullname(holder, level)`, i.e. `description().copy()` merged with GRAY (or RED for `#curse`), plus `" " + enchantment.level.N` unless the enchantment is single-level at level 1.
  - A raw original line is dropped only if it is **`Component.equals`** to one of the represented entries' `vanillaLine`: same translatable contents, arguments, siblings and style. Each represented entry removes at most one line.
  - Nothing is matched by rendered text. A line that does not match exactly (e.g. another mod rewriting it) is preserved.
  - Verified on real lines: see §4.

### Disclosure contract
| State | Enchantment rows | Renderer |
|---|---|---|
| DEFAULT | name + level | V2 |
| SHIFT (DETAILS) | name + `level / max` | V2 |
| CTRL (TECHNICAL) | name + level; the existing TechnicalInfo block is unchanged | V2 |
| SHIFT+CTRL | name + `level / max`, plus the technical block | V2 |

### Gameplay boundaries respected
No change to enchantment effects, enchanting tables, anvils, acquisition or learning, costs, capacity, Soul Trap or Soul Gems, Dispel Magic, balance, rarity, or combat and mining code. `ItemRarityResolver` is untouched: an enchanted vanilla sword stays COMMON even though vanilla's own rarity says RARE, and an enchanted Netherite Shuriken keeps its authored EPIC.

---

## 2. Changed-file manifest (task-only, 9 files)

**New (3)**
- `src/main/java/zcylas/totality/client/tooltip/contributor/TooltipEnchantments.java`
- `src/main/java/zcylas/totality/client/tooltip/contributor/EnchantmentsContributor.java`
- `src/gametest/java/zcylas/totality/gametest/TooltipV2EnchantmentsClientGameTest.java`

**Modified (6)**

| File | Change |
|---|---|
| `src/main/java/zcylas/totality/client/tooltip/contributor/TooltipContributorRegistry.java` | +1 line: registration |
| `src/main/java/zcylas/totality/client/tooltip/contributor/ExternalContentContributor.java` | exact-component dedup, plus its javadoc updated |
| `src/test/java/zcylas/totality/client/tooltip/contributor/TooltipContributorRegistryTest.java` | contributor registered |
| `src/test/java/zcylas/totality/client/tooltip/contributor/TooltipContributorBodyGroupsTest.java` | group = ENCHANTMENTS |
| `src/gametest/java/zcylas/totality/gametest/TooltipV2Pass1ClientGameTest.java` | see below |
| `src/gametest/resources/fabric.mod.json` | registers the new client GameTest entrypoint |

In the Pass 1 GameTest, the check `vanilla_lines.enchantment_preserved` asserted the raw "Sharpness" line passes through as external text, which this task intentionally supersedes. It was **retargeted, not deleted**, into two checks:
- `vanilla_lines.unrelated_preserved`: attribute lines survive.
- `vanilla_lines.enchantment_represented_once`: one `Sharpness III` row, and no raw duplicate.

The patch baseline is each file's contents at the start of this task, including the earlier uncommitted Tooltip V2 work; it is not HEAD.

---

## 3. Automated test results

| Suite | Result |
|---|---|
| `./gradlew clean build` | passes |
| `./gradlew test --rerun` | 157 classes, **1917 tests, 0 failures, 0 errors, 0 skipped** |
| Client GameTest `./gradlew runClientGameTest` | both tests in one run, `GAMETEST_EXIT=0` |
| — `TooltipV2EnchantmentsClientGameTest` (new) | **47 PASS / 0 FAIL**, 17 screenshots |
| — `TooltipV2Pass1ClientGameTest` (regression) | **162 PASS / 0 FAIL**, 33 screenshots |
| Dedicated server (dev) | `Done (1.898s)`, `EXIT=0` |

---

## 4. Client GameTest evidence

The test uses real registered stacks and the real enchantment registry, with the item's genuine `getTooltipLines(...)` as the original lines, never name-only input.

| # | Requirement | Result |
|---|---|---|
| 1 | Ordinary enchanted sword | Iron Sword + Sharpness V → one row `Sharpness V` in ENCHANTMENTS |
| 2, 6 | Multiple enchantments and actual levels | Diamond Sword → `Sharpness V, Looting III, Unbreaking III, Mending I` (4 rows), in **vanilla's own tooltip order** (vanilla line indices 1, 2, 3, 4 ascending) |
| 3 | Enchanted book (stored) | `Curse of Binding I, Protection IV` from `STORED_ENCHANTMENTS` |
| 4 | Curses | book (Binding) and chestplate (Vanishing): curse rows red, the other rows not red |
| — | Totality enchantment | Iron Pickaxe + `totality:impact` III → `Impact III` (data-driven, localised) |
| 5 | No enchantments | plain Diamond Sword → no rows, and no SHIFT offer from this contributor |
| — | Concealment | `TooltipDisplay` hiding `ENCHANTMENTS` → no rows **and** no raw lines |
| 8 | No duplicated lines | for 5 items, no ENCHANTMENTS row name appears in the external lines. The whole document (all contributors together) has exactly 4 rows for 4 enchantments |
| 9 | Unrelated vanilla text | lore `"Engraved by a village smith"` and the `Attack Damage`/`Armor` attribute lines preserved |
| 7 | Rarity | enchanted vanilla sword resolves COMMON (vanilla's own: RARE); enchanted shuriken keeps EPIC |
| 10 | DEFAULT / SHIFT / CTRL / SHIFT+CTRL (real key holds) | Sharpness value `V` / `V / V` / `V` / `V / V`; 4 rows in every state; renderer stays V2 for the enchanted sword and stays original for the enchanted external item |
| 11 | External routing | enchanted `gametest_external:sample_relic` → `EXTERNAL_ITEM`; vanilla still prints its own enchantment line for it |

**Pass 1 regression:** all 162 checks pass, including the retargeted enchantment check (`rows=[Sharpness III]`, and no raw Sharpness in the external lines).

## 5. Real-client screenshots
There are 17 screenshots through the genuine inventory hover path at **GUI 1, 2 and 4**. `screenshots/SCREENSHOT_MANIFEST.md` gives the item, scale, modifier and route for each.

- **GUI 2:**
  - Sharpness V sword; multiple enchantments with lore.
  - Enchanted book with a curse; chestplate with Curse of Vanishing.
  - Unenchanted sword (no group).
  - Totality `Impact`; enchanted Totality shuriken (EPIC kept).
  - Enchanted external item (original tooltip).
  - SHIFT, CTRL and SHIFT+CTRL on the multi-enchanted sword.
- **GUI 1 and GUI 4:** multi-enchanted sword, book, cursed chestplate.

Observed:
- The ENCHANTMENTS heading and divider sit in ordinary group order, before the DURABILITY bottom section.
- No enchantment appears twice.
- The lore and attribute lines remain.
- The SHIFT panel highlights and the values change to `level / max`.

---

## 6. Known limitations
1. **Master unavailable** (see top).
2. **Curse marking is on the level value.** A curse's *name* stays in the standard secondary label colour because `StatRow`'s label colour is fixed; only its level value is red. The name itself reads "Curse of …". Colouring the label would need a wider change to the shared `StatRow` type, which was deliberately not done.
3. **Mending I and similar.** Rows always show the actual level, including level I of single-level enchantments, where vanilla omits it.
4. **SHIFT detail is limited to the registered maximum level.** No descriptions, costs or requirements exist in authoritative data, so none are shown (the future Enchanting API owns them).
5. **The dedup relies on exact component equality.** If another mod or resource rewrites an enchantment line, it is preserved rather than removed, so it could then show alongside the row. That is safe by design.
6. **Blank separator.** Vanilla's own blank line before "When in Main Hand:" is preserved as it always was. After dedup it can be the first external line, which renders as the usual small gap.
7. **The ENCHANTMENTS group icon** is still the temporary vanilla `enchanted_book` art flagged in earlier slices.
8. **`runClientGameTest` is not part of `build`,** and it needs a display.

## 7. Repository state
| | Branch / HEAD | Status | Staged |
|---|---|---|---|
| **Pre-task** | `master` / `7213407` | 86 M, 1 D, 102 ?? | 0 |
| **Post-task** | unchanged | 86 M, 1 D, 105 ?? | 0 |

- **Delta:** the 3 new files. The 6 modified files were already modified or untracked from earlier uncommitted Tooltip V2 work, and remain so.
- **Deliverables:** this report adds one more untracked file. The ZIP is in the gitignored `Context/Audit/Review Bundles/`.
- **Unrelated work preserved:** every edited file was snapshotted before its first edit. No other file changed (checked by mtime and by the git delta). No reset, clean, stash, branch switch, commit or push.
- **`run/`:**
  - The client GameTest ran in `build/run/clientGameTest`, and the dedicated-server smoke in a scratch universe.
  - The smoke run's `run/logs` rotation and four rewritten top-level files were restored from the verified pre-task backups and re-verified identical in content and mtime.
  - Every `level.dat` is unchanged.

**No commit and no push were performed.**
