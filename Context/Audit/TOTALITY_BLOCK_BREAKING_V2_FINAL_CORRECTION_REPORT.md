# TOTALITY — Block Breaking V2, Final Correction Pass
## SPECIAL Classification and Tooltip V2

**Date:** 2026-09-25 · **Target:** Minecraft 26.2 / Fabric / Java 25 · **Branch/HEAD:** `master` / `7213407` (unchanged; nothing committed, staged or pushed)

**Review bundle:** `Context/Audit/Review Bundles/TOTALITY_BLOCK_BREAKING_V2_FINAL_CORRECTION_REVIEW.zip`
**Updated coverage:** `Context/Audit/TOTALITY_BLOCK_BREAKING_V2_FINAL_CORRECTION_BLOCK_PROFILE_COVERAGE.csv` (full registry, 1,226 rows)

**Baseline:** `src/` was snapshotted before editing. `TASK_ONLY.patch` is computed against it, and applying it reproduces the current `src/` exactly (verified with `diff -r`).

This is a correction and regression pass only. It does not rebalance, redesign, integrate fictional materials, or claim the V2 milestone is closed.

---

## 1. Four SPECIAL blocks

**The change:** `minecraft:repeater`, `minecraft:comparator`, `minecraft:end_rod` and `minecraft:scaffolding` are now exact-id **SPECIAL**, meaning vanilla-owned. The code is `VanillaBlockProfiles.special()`.

**Their authored HP is removed at the source, not overridden.** Previously they were:
- Repeater and Comparator: 25, tool-neutral;
- End Rod: 50, tool-neutral, shared with Glowstone;
- Scaffolding: 50, Axe.

Glowstone keeps its 50 HP, tool-neutral row, now authored on its own line.

**Resolved profile, in every block state:**
- classification SPECIAL (authored at the BLOCK layer);
- no material;
- durability comes only from the hardness fallback, which is 0 for hardness 0;
- nothing authored, so no dormant number is left.

**Finding: the old HP was not fully dormant.** The mutation run below reinstated the pre-pass profiles. A direct Totality impact on a Repeater through `BlockBreaking.applyImpact` then returned **BROKEN**. The previous state flagged these rows as "INERT: … authored HP is data only". That held for player mining, which vanilla owns at hardness 0, but not for the non-player impact path. With SPECIAL, the same 1000-damage impact returns **INVALID**.

**Scope kept:**
- no other hardness-0 block changed;
- no finite-HP ownership was introduced for hardness-0 blocks;
- the coverage report's INERT guard is kept. It now flags nothing, but would flag any future authored hardness-0 row.

## 2. Tooltip V2: single-level enchantments

**The rule:** `EnchantmentsContributor.value(level, max, details)` shows no numeral for a single-level enchantment at its only level. This is the same rule vanilla's `Enchantment.getFullname` applies (`max == 1 && level == 1`).

| | DEFAULT | SHIFT |
|---|---|---|
| Mending, Silk Touch, the curses | name only | name only (no `I / I`) |
| Multi-level, e.g. Efficiency, Unbreaking, Protection | `V`, `III`, `IV` | `V / V`, `III / III`, `IV / IV` |
| Over-levelled single-level (e.g. Mending II via commands) | actual level shown (not hidden) | `II / I` |

**Curses: the name is now red, as well as any level.** The row's level value was the only red element before, and it is now usually empty. The narrowest fix was an optional `labelColor` on `TooltipSection.StatRow`:
- `DEFAULT_LABEL_COLOR = 0` means the renderer's shared secondary label colour. Every existing row keeps that, since all existing constructors default to it.
- Curse rows set it to the existing curse red, `0xFFFF5555`.
- The renderer uses it for one-line and wrapped labels.

**Not changed:**
- enchantment levels, storage, effects and compatibility;
- the dedup of vanilla's raw lines, since the component vanilla prints is unchanged. It is re-verified: no raw line is duplicated.

## 3. Tool-neutral row wrapping

**The defect.** `TotalityTooltipRenderer.drawIconStatRow` mismatched the layout when the row did not fit on one line:
- `layout()` reserved `max(18px icon row, label lines)` before the value;
- but drawing started the value one text row (10px) down, at the left edge, inside the icon row.

"No preferred tool" therefore sat under the pickaxe sprite and overlapped it. That is the Pass 3 screenshot, included in the bundle as `BEFORE_…`.

**The fix (the wrapped branch only):**
- The first label line is centred on the icon exactly like the one-line row.
- The value starts below both the full icon row and the full wrapped label, in the **right-aligned value column** where every one-line value sits.
- Layout and drawing now share `iconRowLabelTop` and `iconRowValueTop`, so the reserved height and the drawn position cannot diverge again.

**Kept as is:**
- the wording "No preferred tool";
- icons, colours and spacing;
- the one-line layout.

**Result by window and scale:**
- **Wide windows (1920×1080, GUI 1–4):** the panel sizes to the row, so it stays on one line.
- **854×480, GUI 2 (the original condition):** it wraps cleanly with no overlap.
- **854×480, GUI 1:** it fits on one line.

---

## 4. Verification

| Suite | Result |
|---|---|
| `./gradlew build compileGametestJava --rerun-tasks` | **BUILD SUCCESSFUL, exit 0**; JUnit 173 classes, **1,996 tests, 0 failures, 0 errors, 0 skipped** (+4) |
| Live, disposable `runVerificationServer`, existing gate, **stopped gracefully via `stop` (Gradle exit 0)** | **every suite passed**; MiningVerification **372/372** (+4) |
| Mutation (pre-pass `VanillaBlockProfiles` reinstated, then restored byte-identical) | exactly the **4 new checks fail** (4/372); all 368 earlier checks still pass |
| Client, disposable `runClientGameTest`, real world (exit 0) | TooltipV2Pass1 162/0, TooltipV2Enchantments 47/0, Pass2 11/0, Pass3 23/0, Pass4A 5/0, Pass4B 5/0, **FinalCorrection 40/0** |

**Live checks (new):**
1. All four are exact-id SPECIAL in every state, with no authored HP and no material.
2. A 1000-damage Totality impact is INVALID. No hand, Pickaxe, Axe, Shovel or Hoe owns them, no record accumulates, and the block remains.
3. **Every other registry row is field-for-field identical to the accepted Pass 3R coverage CSV.** That covers every ordinary block's values and the 28 Totality Core deferrals. Only the four changed, and all four are now SPECIAL. The mutation run also shows the live coverage matched Pass 3R exactly before this pass, so Passes 4A/4B moved nothing.
4. Coverage counts are listed in the table below.

**Client checks (new, in a real world):**
- **The four blocks' tooltips:**
  - no Block Durability, Required Mining Tier or Effective Tool row from **any** registered contributor, in DEFAULT or SHIFT+CTRL;
  - the engine profile is SPECIAL.
- **The Pass 3 agreement check still runs:** Tooltip V2 rows equal the engine profile for **every** BlockItem, so tooltip and mining use the same authority.
- **Unaffected rows:** Glass keeps 50 / Tier 0 / "No preferred tool", Stone keeps 100 / Pickaxe, and Glowstone keeps 50 / neutral.
- **Vanilla breaking in survival:** each of the four breaks instantly (`getDestroyProgress` = ∞, i.e. vanilla's hardness-0 value), drops its own item, and leaves no Totality record.
- **Enchantments, DEFAULT and SHIFT:**
  - Silk Touch and Mending show no numeral; Efficiency and Unbreaking keep theirs;
  - Curse of Binding and Curse of Vanishing have red names and no numeral, while Protection stays non-red;
  - non-curse labels keep the default colour;
  - no raw vanilla line is duplicated;
  - the stack's actual levels are unchanged.

**JUnit (new):**
- `EnchantmentLevelTextTest`: the single-level rule. Multi-level numerals need a bootstrapped client, so they are checked in the GameTest.
- `TotalityTooltipRendererIconRowWrapTest`: the wrapped value clears the 16px sprite and every label line; a single wrapped label is centred like the one-line row.
- `FinalCorrectionSpecialSourceRegressionTest`: the four are named only in `special(...)`, never in an `ordinary(...)` HP line.

**Existing test updated.** `TooltipV2EnchantmentsClientGameTest` expected `Mending I`, which this pass intentionally supersedes. It now expects `Mending`, and its row-text helper trims the empty value. Everything else in it is unchanged.

### Registry coverage summary (live 26.2 registry, 1,226 ids)

| Category | Pass 3R / 4A / 4B | Final correction |
|---|---|---|
| ACCEPTED_AUTHORED | 844 | **840** (−4) |
| SPECIAL | 333 | **337** (+4) |
| UNBREAKABLE | 15 | 15 |
| NOT_APPLICABLE | 6 | 6 |
| COMPAT_FALLBACK (Totality, deferred to Core) | 28 | 28 (rows identical) |
| UNRESOLVED_DESIGN | 0 | 0 |

The bundle's `coverage/DIFF_VS_PASS3R.txt` lists the before/after rows. There are exactly four, and no ids were added or lost.

## 5. Screenshots (real client, genuine inventory hover)
- **Tool-neutral row, Glass:**
  - 1920×1080 at GUI 1/2/3/4: one line;
  - **854×480 at GUI 2: wrapped, no overlap**, with the Pass 3 `BEFORE_` shot for comparison;
  - 854×480 at GUI 1;
  - Stone's Pickaxe row at 854×480 GUI 2, for comparison.
- **The four SPECIAL blocks (GUI 2):** header only, no mining rows.
- **Enchantments:**
  - Silk Touch + Mending pickaxe at DEFAULT, SHIFT and GUI 4;
  - a book with two curses (red names, no numeral) at DEFAULT and SHIFT.

## 6. Observations (not changed)
- **Pointless SHIFT offer:** an item whose only enchantments are single-level at level 1 still offers the SHIFT panel, and SHIFT shows nothing more there. It would take a disclosure-contract change to avoid, which is out of scope.
- **Tight one-line fit:** on wide panels the label and value fit with the renderer's standard 4px minimum gap ("Effective Tool  No preferred tool"). This is the existing one-line convention.
- **Curse styling:** the label and the level are both red. The name is not additionally bold or otherwise styled.

## 7. Remaining manual verification checklist
- [ ] **Four SPECIAL blocks in survival:** place and break a Repeater, Comparator, End Rod and Scaffolding. Check the break is instant, there is no crack overlay and each drops its item. Check Repeater delay and Comparator mode clicks still work, and Scaffolding climbing, stacking and bottom-break collapse still work.
- [ ] **Hover the four blocks** in the creative and survival inventories: no mining rows.
- [ ] **Tool-neutral row:** hover Glass or Wool at your normal window size and GUI scale, including Auto. Check it reads cleanly on one line or wrapped under the label.
- [ ] **Single-level enchantments:** Mending, Silk Touch, Infinity, Channeling, Multishot and the curses, with and without SHIFT, on tools and books. Check curse names are red.
- [ ] **Multi-level numerals:** confirm they are unchanged on real loot (e.g. Sharpness V, Protection IV).
- [ ] **Carried over from earlier passes:**
  - mining cadence and animation feel;
  - Gold wear;
  - shared door and bed cracks;
  - Copper Chest inventory after transformations;
  - the Pass 4B physical transformations (anvil, rain or dripstone cauldron, lightning rod, grass spread).

## 8. Changed files (task-only; `CHANGED_FILES.txt`, 11 files)
**Modified:**
- `api/mining/VanillaBlockProfiles.java`: the four moved to `special()`; their HP lines removed.
- `api/mining/MiningVerification.java`: 4 live checks, including the Pass 3R coverage diff.
- `client/tooltip/section/TooltipSection.java`: optional `StatRow.labelColor`.
- `client/tooltip/TotalityTooltipRenderer.java`: label colour; wrapped icon-row geometry.
- `client/tooltip/contributor/EnchantmentsContributor.java`: single-level rule; red curse names.
- `src/gametest/.../TooltipV2EnchantmentsClientGameTest.java`: the superseded `Mending I` expectation.
- `src/gametest/resources/fabric.mod.json`: registers the new client test.

**New:**
- `src/gametest/.../BlockBreakingFinalCorrectionClientGameTest.java`
- `src/test/.../FinalCorrectionSpecialSourceRegressionTest.java`
- `src/test/.../EnchantmentLevelTextTest.java`
- `src/test/.../TotalityTooltipRendererIconRowWrapTest.java`

**Repository safety:**
- no reset, clean, stash, branch switch, stage, commit or push;
- `git status`: the only new entries are this pass's new test files and the new coverage CSV and report under `Context/Audit/`;
- unrelated modifications and personal saves are untouched;
- runs happened only in `build/verification-run` and `build/run/clientGameTest`.

**Stopping here for review.** Whether V2 is closed is your call; this report does not declare it.
