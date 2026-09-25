# TOTALITY — Block Breaking V2 Readiness and Complete Block Coverage Audit

**Type:** strictly read-only audit, 2026-09-24. No source, asset, data, test, configuration, world or run file was modified. See the confirmation in §16.

**Authority limitation:** `STEFAN_TOTALITY_MASTER_REFERENCE_v3.6_QA_reviewed.docx` is **unavailable**; a filesystem-wide search found no copy.
- §26L, §26M and §26K could not be read or cross-checked.
- Design intent below comes from the accepted decisions reproduced in the task prompt and from the review records in `Context/Audit/`, clearly labelled as such.
- The repository is the authority for what is implemented.

Exhaustive data lives in the review ZIP's CSV files. This report summarises it.

---

## 1. Executive summary

**The Block Breaking V2 engine is implemented and connected end to end:**
- server-authoritative discrete strikes with contact-frame re-raycasting;
- profiled tools, target effectiveness, Impact, Efficiency, Haste and the speed cap;
- Power bands, per-position persistent damage with lazy recovery, crack sync;
- vanilla terminal destruction (drops, Silk Touch, Fortune), the bare-hand harvest grant, successful-impact wear with vanilla Unbreaking, and Mining XP.

**The data is almost entirely interim:**

| | Count |
|---|---|
| Registered blocks (1,196 vanilla from the live 26.2 registry + 30 Totality) | 1,226 |
| Relevant denominator (6 air/fluid technical entries excluded) | 1,220 |
| Deliberately authored durability (6 EXPLICIT + 36 via the `#logs_that_burn` FAMILY) | 42 (3.4%) |
| Blocks Totality strikes that still use the `hardness × 100/1.5` FALLBACK | 981 (80.4%) |
| SPECIAL (182 hardness-0 blocks left to vanilla instant break; 15 unbreakable) | 197 |

- **Required Mining Tier is never authored.** 100% of it comes from vanilla `requiresCorrectToolForDrops` plus the `needs_*_tool` tags.
- **Tool tiers are vanilla-derived.** They are probed from each material's vanilla pickaxe at class initialisation.

**Top issues**
1. The **stale-target cadence** defect exists: the schedule is fixed at swing start from the start target (§7).
2. **Gold** is unprofiled. It runs the legacy V1 path at 72 damage per strike on stone plus STR on every swing, with tier 1, no Impact and no tooltip (§6).
3. **Totality tag gaps.** The vibranium ores are in no mining tag, so every tool is a WRONG_TOOL on them and the required tier is only 1. The Totality ores are not in any vanilla `*_ores` tag.
4. **Force Tolerance** is shown for profiled tools but only used mechanically by the legacy path. Copper has no tolerance entry.
5. **No mining JUnit tests exist.** All mining verification is the 1,924-line live suite, which is correctly opt-in gated.
6. **Six other systems destroy blocks outside the strike pipeline:** Veinminer, Heat Vision, Ground Slam, the Break and Smelt runes, and the harvest handler.

**Already sound:** a global live-verification opt-in gate **already exists and is applied** to every world-mutating suite (§8).

---

## 2. Current implementation status

| Feature | Status | Owning code | Evidence |
|---|---|---|---|
| Discrete server strikes and swing scheduling | Implemented and verified (historical live) | `PlayerMiningManager.tickSession/tryStartSwing` | V1 closeout; PASS6 `MiningVerification` 240/240 |
| Contact-frame server re-raycast | Implemented and verified | `PlayerMiningManager.contactNow/pickBlock` | V1 retarget test (damage) |
| Mining Damage / Speed (profiled) | Implemented and verified | `MiningSourceProfile`, `ResolvedMiningSource`, `MiningDamageCalculator`, `MiningSpeedCalculator` | PASS6 |
| Fractional cadence (carry accumulator) | Implemented | `PlayerMiningManager.scheduleProfiledCycle` | PASS3–6 records |
| Mining Tier (tool) | Implemented, **vanilla-derived** | `MiningTier.ofTool` probe → `MiningSourceProfile` | code |
| Required Mining Tier (block) | Implemented, **fallback only** | `MiningTier.fallbackRequiredTier` | code |
| Target effectiveness | Implemented and verified (pickaxe/axe/shovel; hoe not wired) | `TargetEffectiveness` | PASS records |
| Block Durability | Implemented; **42 authored, rest fallback** | `BlockDurability`, `BlockDurabilityDefinitions` | code |
| Placed-block Integrity, persistence, lazy recovery | Implemented and verified | `BlockDamageStorage` (SavedData `totality:block_damage`; recovery after 600 ticks at 1/1200 of max per tick) | V1 |
| Crack sync | Implemented | `BlockDamageStorage.syncTo/sweep` (40-tick sweep, join/respawn sync) | V1 |
| Harvest qualification | Implemented and verified | `ServerPlayerGameModeMixin` (refuses vanilla START/STOP; wraps `hasCorrectToolForDrops` with `HarvestGrant`) | V1 tests |
| Terminal destruction, drops, Silk Touch, Fortune | Implemented (vanilla `ServerPlayerGameMode.destroyBlock`) | `BlockBreaking.destroy` / `destroyCharging1` | V1/PASS6 |
| Successful-impact wear and Unbreaking | Implemented and verified | `PlayerMiningManager.strike` → `ItemStack.hurtAndBreak` | PASS6 `DurabilityRegression` 12/12 |
| Mining XP | Implemented and verified (decoupled) | `MiningSkillEvents` via `PlayerBlockBreakEvents.AFTER` | PASS6 live check |
| Power Mining | Implemented (profiled zones; legacy force stress for unprofiled) | `PlayerMiningPower`, `MiningTuning` | PASS records |
| Bare-hand mining | Implemented (damage 1 + STR; tier (DEX−8)/4) | `PlayerMiningPower`, `HarvestGrant` | V1 |
| Impact | Implemented (profiled tools only, via the generated tag) | `MiningDamageCalculator`, `mining_damage.json` | PASS records |
| Efficiency / Haste | Implemented | `MiningSpeedCalculator` | PASS records |
| Tooltip V2 | Implemented (fallback-sourced block data) | `BlockDurabilityContributor`, `MiningToolContributor` | Tooltip V2 reviews |
| Gold tools | **Provisional / legacy** | `ResolvedMiningSource` legacy branch | code |
| Drills / powered sources | **Missing** (none exist) | — | code |

---

## 3. Existing architecture and owning code

All anchors are verified; see `SOURCE_REFERENCE_INDEX.md` (43/43).

**Swing lifecycle**
1. The client sends `MiningIntentPayload` (hold, stop, power start/release/cancel).
2. `PlayerMiningManager` runs a per-player `Session` with an IDLE → WINDUP → RECOVERY state machine on `END_SERVER_TICK`.
3. At swing start, `tryStartSwing` picks the target and schedules the swing. Profiled tools go through `scheduleProfiledCycle`; others use the legacy `cadenceRatio`. It sends `MiningSwingPayload(windup, recovery)` for the client animation.
4. At the contact frame, `contactNow` re-raycasts. Identical-source checking (`MiningSourceIdentity`) voids a swing when the tool was swapped.
5. `strike` runs `PlayerMiningPower.compute`, then `BlockBreaking.applyImpact`:
   - a permission gate (`MiningPermissions`: reach, spawn protection, `mayInteract`, `AttackBlockCallback`);
   - an ineffective result below the required tier;
   - otherwise Integrity is reduced in `BlockDamageStorage`;
   - at 0, vanilla `destroyBlock` runs, with wear normalised to 1 and a `HarvestGrant` for bare hands.

**Ownership:** `MiningOwnership.owns` requires survival mode, a held item without `PIERCING_WEAPON`, and `destroySpeed > 0`. Everything else stays vanilla, including instant-break blocks and creative mode.

---

## 4. Complete block coverage statistics

Full data: `COMPLETE_BLOCK_COVERAGE.csv`, 1,226 rows with one registry ID each; `COVERAGE_VALIDATION.txt`.

| Category | Definition used | Vanilla | Totality | Total |
|---|---|---|---|---|
| EXPLICIT | `BlockDurability.register` (stone, cobblestone, diorite, andesite, dirt, grass_block = 100) | 6 | 0 | 6 |
| FAMILY | `registerMaterial(#minecraft:logs_that_burn, 100)` | 36 | 0 | 36 |
| FALLBACK | struck by Totality, durability = hardness × 66.67, tier = vanilla-derived | 956 | 25 | 981 |
| SPECIAL | hardness 0 (vanilla instant) or < 0 (unbreakable) | 192 | 5 | 197 |
| MISSING | no intentional path at all | 0 | 0 | 0 |
| UNVERIFIED | static evidence insufficient | 0 | 0 | 0 |
| NOT APPLICABLE | air, cave_air, void_air, water, lava, bubble_column | 6 | 0 | 6 |

**Reconciliation**
- The vanilla set equals the live registry probe exactly.
- There are no duplicate IDs.
- Every ID appears in exactly one family candidate.
- 79 blocks have no BlockItem, and all are present in the inventory.
- 178 rows carry MULTIBLOCK or STATE flags. Of those, 59 multi-block and 88 state-dependent cases are blocks Totality strikes; the rest are instant or unbreakable.

**Notes**
- **Granite:** it resolves to 100 **only** through the fallback formula (1.5 × 66.67). It is FALLBACK, not authored, as required.
- **FALLBACK values:** they span 6.67 (moss, candles) to 3,666.67 (reinforced deepslate). The most common are 133.33 (160 blocks), 200 (151) and 100 (134).
- **MISSING is 0 by strict definition,** but 981 FALLBACK blocks still require deliberate authoring.
- **Authoring gaps:** three blocks require the correct tool but have no `mineable/*` tag: `minecraft:cobweb` (sword/shears) and `totality:vibranium_ore` / `totality:deepslate_vibranium_ore`.
- **Totality's 30 blocks** come from source (`init/blocks/*`, `ModBlocks`). `chalk` and `true_wheat_crop` have no BlockItem. `registerFlowerIngredient` has zero call sites.

---

## 5. Material-family findings

Full data: `BLOCK_FAMILY_CANDIDATES.csv`. 61 candidates; membership comes from actual vanilla or Totality tags, or from block class, never from name substrings.

- **Implemented family:** only `LOGS_OVERWORLD` (`#logs_that_burn`, 36 blocks, uniformly 100).
- **New proposed, tag-backed candidates** (examples):

  | Candidate | Tag |
  |---|---|
  | NATURAL_STONE | `stone_ore_replaceables` |
  | DEEPSLATE_TUFF | `deepslate_ore_replaceables` |
  | ORES_* | eight vanilla ore tags |
  | METAL_GEM_STORAGE | `beacon_base_blocks` |
  | SOIL_DIRT | `dirt` |
  | SEDIMENT_SAND | `sand` |
  | PLANKS, LEAVES, WOOL, TERRACOTTA, ICE, SNOW, NYLIUM, WART_BLOCKS | their own tags |
  | STEMS_NETHER | `crimson_stems`, `warped_stems` |
  | NETHER_STONE | `base_stone_nether` |
  | STONE_BRICKS, FENCES, WALLS, SLABS, STAIRS, DOORS, TRAPDOORS, SIGNS, BANNERS, CANDLES, BEDS, SHULKER_BOXES, ANVILS | their own tags |
  | FRAGILE_PLANTS | `flowers`, `crops`, `saplings`, `replaceable`, … |

  Class-backed candidates: GLASS, CONCRETE_POWDER, GLAZED_TERRACOTTA, COPPER_BLOCKS, TORCHES, BUTTONS, PRESSURE_PLATES, CARPETS, HEADS, CORAL, INFESTED, POTTED_PLANTS, TECHNICAL_OPERATOR, FLUIDS.

  Totality: TOTALITY_ORES, TOTALITY_NATURAL_STONE, TOTALITY_MACHINES_TECHNICAL, TOTALITY_RITUAL, TOTALITY_WORKSTATION, TOTALITY_FRAGILE.
- **367 blocks are UNASSIGNED** because no tag or class groups them. Examples: polished stones, sandstone, bricks, obsidian, gravel, clay, glowstone, bookshelves, shelves, pistons, rails, mushroom blocks, soul sand/soil, sponge, redstone parts, crafting stations, chests.
- **Several candidates span hardness or tier ranges.** Examples: construction tags such as `slabs` mix wood and stone; ORES_IRON mixes stone and deepslate variants. Uniform per-family durability would be wrong, so these need exact overrides or sub-families. That is flagged per family.
- **Minimum foundation (proposal):** a profile record `{family, blockDurability, requiredTier, preferredToolOverride?, specialKind?}`.
  - Tag-keyed families plus per-block overrides, fed through the **existing** `BlockDurability` resolution order: exact → family → fallback.
  - Block Durability stays distinct from any future Structural Strength, density or thermal property. Nothing is collapsed into one number; that API is out of scope.

## 6. Tool coverage findings

Full data: `TOOL_PROFILE_COVERAGE.csv`.

- **Profiled (18 items: Wood, Stone, Copper, Iron, Diamond, Netherite × pickaxe/axe/shovel):** Damage and Speed are authored and match the accepted baseline exactly (20/1.50, 35/2.00, 40/2.10, 50/2.25, 75/2.40, 100/2.50).
  - **Tier is vanilla-derived:** wood 1, stone 2, copper 2, iron 3, diamond 4, netherite 4. Netherite is not above diamond, and TIER_MAX 5 is unused.
  - Impact is supported through the generated tag.
  - The tooltip shows Damage, Speed and Tier, plus SHIFT provenance and Force Tolerance.
- **Gold (pickaxe/axe/shovel) is LEGACY and unprofiled:**
  - **Damage:** vanilla `ToolMaterial.GOLD` speed 12 × 6 = **72** on its correct blocks, 6 elsewhere. The STR modifier is added on **every** swing.
  - **Cadence:** the legacy V1 `cadenceRatio` swing timing. The absolute speed is UNVERIFIED statically, because it depends on the swing animation duration.
  - **Tier:** probe 1. **Force Tolerance:** 2.0, and it is *used* here.
  - There is no Impact support, no target-effectiveness multiplier and no tooltip Mining group.
  - Vanilla Gold durability is 32.
  - **Decisions needed:** Damage, Speed, Tier, whether Gold gets a WRONG_TOOL penalty like other tools, whether it keeps a "fast but fragile" identity, and whether it gets Impact.
- **Hoes and swords** have a TOOL component, so they are legacy tool sources. They probe Tier 0 and can only damage tier-0 blocks. Hoe effectiveness is not wired, and neither has a profile or a tooltip.
- **Shears:** legacy, tier 0. **Bare hands:** 1 + STR, tier from DEX. **Spears:** excluded by `PIERCING_WEAPON`. **Drills:** none exist.
- **Force Tolerance** is mechanically used only on the legacy path: `PlayerMiningPower` overload and `toolStress`. For profiled tools it is display-only. **Copper** has no repair-material entry and falls back to the stone-tier 3.5.
- **Remaining vanilla-derived Tier logic to author:**
  - `MiningTier.ofTool` (used to build `MiningSourceProfile`);
  - `MiningTier.fallbackRequiredTier` (every block);
  - the `forceTolerance` tier fallback.

## 7. Stale-target cadence investigation (not fixed)

**Confirmed in code.**

**Where the state comes from**
- **Scheduling happens once, at swing start.** `tryStartSwing` raycasts, reads `targetState`, and computes the swing's windup and recovery from it (`scheduleProfiledCycle`, or the legacy `cadenceRatio`). `effectiveMiningSpeed` applies `TargetEffectiveness.speedMultiplier` for that start target.
- **At contact, only the damage is re-resolved.** `contactNow` → `pickBlock` → `strike` uses the new block for damage, tier and effectiveness, but `recoveryTicks` is **not** recomputed.
- **The new target's cadence starts only at the next swing,** when RECOVERY completes and `tryStartSwing` runs again.
- **What survives a target change:** `speedCarryTicks` (sub-tick), `recoveryTicks`, and the client animation durations already sent.

**Example (Netherite Pickaxe, Grass → Stone):**
- The swing is scheduled at wrong-tool speed: 2.5 × 0.5 = 1.25/s, a 16-tick cycle (windup ≈ 5, recovery ≈ 11, assuming the default 6-tick swing duration; UNVERIFIED).
- Contact hits Stone at full 100 damage, but the next contact comes ≈ 13 ticks later instead of 8.
- The effect is **bounded to one cycle**, and it also applies in reverse: start on Stone, retarget to Grass, and one fast wrong-tool contact results.
- Historical V1 testing covered retarget *damage* only.

**Smallest correction (proposal, Pass 1):**
- At contact, for normal swings with a hit, recompute the cycle from the contact target and set `recovery = max(MIN_RECOVERY_TICKS, newCycle − windupSpent)`.
- Optionally re-send `MiningSwingPayload`.
- The contact moment, one strike per swing, damage semantics and Power swings stay unchanged.

**Exploit review**
- Starting on a fast target and finishing on a slow one gains at most one windup.
- A miss (no block) keeps the scheduled cycle.
- A tool swap already voids the swing.
- Reach and permission checks are unchanged.

## 8. Verifier and world-isolation investigation

Full data: `VERIFIER_REGISTRATION_AUDIT.csv`, 22 entries.

- **The global opt-in exists:** `VerificationReporter.liveWorldVerificationEnabled()` requires the dev environment **and** `-Dtotality.liveWorldVerification=true`, and logs a warning. `runVerificationServer` uses `build/verification-run` and deletes its world before each launch.
- **All 15 world-mutating suites early-return on this gate:**
  - Mining (setBlock ×51, BlockDamageStorage)
  - DurabilityRegression
  - PowerAttack
  - OffhandAttack
  - MerchantSell (inventories)
  - Provisioner (spawns)
  - TradingScreen
  - eight resource and food suites
- **Dev-only but not live-gated:** SoulGemSystem (in-memory stacks), ItemValue (an unregistered fake-player object used as a pricing context), and four client `runIfDev` suites. No world edits were found by inspection; that is runtime-UNVERIFIED.
- **Other SERVER_STARTED hooks** (RarityCoverage, ItemValue/Shop/Ritual/Provisioner registries) are registry loads, not verifiers.
- **So ordinary dev play does not mutate the world through verifiers** (static evidence).
- **Remaining work (Pass 2):** a source-regression guard that keeps this true, and moving the pure checks to JUnit.
- This is unrelated to the historical save delay, which was a separate toolchain diagnosis.

## 9. Tooltip V2 integration readiness

| Field | Contributor | Source today |
|---|---|---|
| Block Durability (max) | `BlockDurabilityContributor` | `BlockDurability.resolveStatic`; FALLBACK for 981 blocks |
| Required Mining Tier | same | vanilla-derived fallback |
| Effective Tool | same | `BlockTags.MINEABLE_WITH_*` (pickaxe/axe/shovel; not hoe) |
| Material / family | — | missing (no data exists) |
| Mining Damage / Speed / Tier | `MiningToolContributor` | profiled data (Tier vanilla-derived); Gold none |
| Force Tolerance | `MiningToolContributor` (SHIFT) | display-only for profiled tools |

- **Integrity is correctly not shown on stacks.** The tooltip shows maximum Block Durability only; placed Integrity lives only in `BlockDamageStorage` and is never shown on a stack.
- **A quirk:** hardness-0 BlockItems (e.g. seeds) show "Block Durability 0" even though Totality never strikes them.
- **Later integration points:** Pass 6 swaps the contributor data to the profile API and adds Gold once profiled.

## 10. Special-block handling

Full data: `SPECIAL_BLOCK_CASES.csv`, 564 rows.

- **Unbreakable (15):** bedrock, command blocks, structure/jigsaw/test blocks, barrier, light, portals, end portal frame, moving_piston. These should become an explicit PROTECTED or UNBREAKABLE profile.
- **Instant (182, vanilla-owned):** flowers, crops, torches, redstone wire, etc. They should become an explicit INSTANT profile. Decision: whether any should become fragile struck blocks.
- **Multi-block (59):** doors, tall plants, beds, pistons.
  - **Current behaviour:** per-position fallback durability, so each half accumulates damage separately. Vanilla terminal destroy removes both halves.
  - The orphaned damage entry is purged by the block-id check or the sweep.
  - **Needs a decision:** an owner-position rule to avoid split damage.
- **State-dependent (88):** double slabs, snow layers, candles, sea pickles, turtle eggs, crop age. Durability is state-independent today.
- **Block entities (172, FALLBACK):** containers drop their contents through vanilla destroy.
- **Special interaction** (39 struck blocks; e.g. vault and trial spawner at 3,333; reinforced deepslate at 3,667; spawner at 333; cake at 33; beehive at 40; infested blocks; TNT is instant) needs explicit review.
- **Fluids** are not targetable: the server raycast excludes fluids.
- **Six systems destroy blocks outside Block Breaking and ignore Integrity:** Veinminer, Heat Vision, Ground Slam, the Break and Smelt runes, and the harvest handler. Decision: whether they stay outside the system.

## 11. Current balance and calibration evidence

Full data: `CURRENT_CALIBRATION_CALCULATIONS.csv`, 416 rows. These are **calculated current behaviour** using approximately strikes ÷ speed, and are not targets.

| Block (current durability, tier) | Iron Pickaxe | Netherite Pickaxe | Wrong tool (Netherite Axe) | Gold Pickaxe (legacy) |
|---|---|---|---|---|
| Stone (100 explicit, T1) | 2 strikes, 0.89 s | 1 strike, 0.40 s | 10 strikes, 8.0 s | 2 strikes (72 damage), time UNVERIFIED |
| Deepslate diamond ore (300 fallback, T3) | 6 strikes, 2.67 s | 3 strikes, 1.20 s | 30 strikes, 24.0 s | ineffective (T1 < 3) |
| Iron block (333 fallback, T2) | 7 strikes, 3.11 s | 4 strikes, 1.60 s | 34 strikes, 27.2 s | ineffective |
| Obsidian (3,333 fallback, T4) | ineffective | 34 strikes, 13.6 s | 334 strikes, 267 s | ineffective |
| Ancient debris (2,000 fallback, T4) | ineffective | 20 strikes, 8.0 s | 200 strikes, 160 s | ineffective |
| Oak log (100 family, T0) | wrong tool: 20 strikes, 17.8 s | wrong tool: 10 strikes, 8.0 s | correct: 1 strike, 0.40 s | 17 strikes (6 damage) |
| Vibranium ore (300 fallback, T1) | WRONG_TOOL: 60 strikes, 53 s | WRONG_TOOL: 30 strikes, 24 s | WRONG_TOOL: 30 strikes, 24 s | 50 strikes |

**Planning structure for the future dataset** (targets deliberately not proposed):
- For each family, specify two separate targets: **strikes-to-break** and **seconds-to-break**, per tool tier.
- Include the wrong-tool and under-tier outcomes, with Gold as its own row.
- Use vanilla hardness **only** as comparison evidence.

## 12. Open design decisions for Stefan
1. **Family structure:** accept the tag- and class-backed candidates, and decide how to split mixed-range tags (slabs, stairs, ores by host stone).
2. **Durability targets:** strikes-to-break and seconds-to-break per family and tier, and Netherite versus Diamond tier (both 4 today; TIER_MAX 5 unused).
3. **Special kinds:** which instant blocks stay instant; the multi-block owner rule; state scaling (slabs, snow, candles); the unbreakable versus protected list.
4. **Gold:** Damage, Speed, Tier, wrong-tool behaviour, Impact support, identity.
5. **Hoes, swords and shears** as mining sources: wire hoe effectiveness, or leave them on the legacy path?
6. **Legacy STR on every swing** for unprofiled tools: keep or remove?
7. **Force Tolerance:** retire it from the profiled tooltip, or give it profiled meaning; the Copper entry.
8. **Totality ores:** vibranium tier and tool tags; ruby tier (currently needs diamond); membership in `c:`/vanilla ore tags.
9. **Systems that bypass the strike pipeline:** should they respect Integrity?
10. **Existing damaged blocks** keep the `max` from when they were first damaged. What is the migration rule when a profile changes?

## 13. Proposed implementation sequence

See `IMPLEMENTATION_PLAN.md` for scope, dependencies, acceptance criteria, tests, evidence, risks and exclusions.

| Pass | Work | When |
|---|---|---|
| 1 | Stale-target cadence | now |
| 2 | Verifier hygiene (the gate already exists) | combinable with Pass 1 |
| 3 | Minimal profile foundation | after decisions 1–3 |
| 4 | Tool completion and authored tiers | after decisions 4–7 |
| 5 | Complete baseline data | in family batches |
| 6 | Tooltip integration | after 3–5 |
| 7 | Isolated live and manual regression | — |
| 8 | Final coverage verification and V2 closure | — |

## 14. Verification limitations
- **Master unavailable.**
- **Nothing was executed against a world.** The vanilla inventory comes from a scratch-only JVM bootstrap of the 26.2 registry. Tag membership comes from the jar's own data files, plus Totality's generated tag files.
- **Totality's block count comes from source,** not a runtime registry.
- Resolution values replicate `BlockDurability.resolveStatic` statically. Runtime `getDestroySpeed(level,pos)` overrides are UNVERIFIED; none are known for vanilla `BlockBehaviour`.
- The legacy Gold and bare-hand cadence (swing animation duration) is UNVERIFIED.
- Fluid non-targetability rests on static reasoning.
- The historical live results (PASS6) were not re-run.

## 15. Pre/post Git status

| | Branch / HEAD | Status | Staged |
|---|---|---|---|
| Pre | `master` / `7213407be695aff44a3f9f7747a726f7fd6f33bf` | 86 M, 1 D, 106 ?? | 0 |
| Post (before the deliverables) | unchanged | identical | 0 |

The deliverables add this report (untracked). The ZIP is in the gitignored `Context/Audit/Review Bundles/`.

## 16. Confirmation
- **No source files were modified:**
  - every file's mtime under `src/` was fingerprinted before and after the audit;
  - no gameplay, API, registration, test, asset, configuration or existing documentation file was changed.
- **No Gradle task or datagen was run.** The only execution was the scratch JVM registry probe.
- **No client, server or verifier was run.**
- **No world, save, `run/` directory, setting or log was touched.**
- **Nothing was committed or pushed.**
