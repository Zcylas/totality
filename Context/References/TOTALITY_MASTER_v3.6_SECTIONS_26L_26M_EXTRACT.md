# Extract — Totality Master v3.6 QA reviewed, §§26L and 26M

Convenience text extract of an older source. Later accepted Block Breaking V2 decisions supersede older status/numerical statements; consult the current ledger first. Original: `STEFAN_TOTALITY_MASTER_REFERENCE_v3.6_QA_reviewed.docx`.


## 26L. v3.5 Tooltip V2 Preparation, Block Breaking V2 Finalization, Block/Material Profiles, and Development-Toolchain Reconciliation - 21-23 September 2026

HISTORICAL v3.5 STATUS / AUTHORITY: Section 26L was the newest authority for its post-v3.4 topics at that checkpoint; §26M supersedes its preliminary Tooltip V2 and current-work-status wording but §26L remains the authority for unaffected Block Breaking V2, verifier isolation, Block/Material Profiles and toolchain subjects. It preserves the accepted Block Breaking V1 strike architecture while recording the V2 authored-stat/balance implementation, PASS5/PASS6 validation, the new complete-vanilla-block closure requirement, Tooltip API V2 preparation, the Block/Material Profile direction, verifier-world contamination, and the slow-save toolchain diagnosis. It supersedes older provisional mining formulas and current-work-order/toolchain statements where they conflict. It does NOT supersede Section 26K's Daily/Reward/Gate/Voice/RPG-foundation material or older owning sections outside these topics.


## 26L.1 Block Breaking V2 status and architecture retained from V1

IMPLEMENTED / RETAINED: V2 is an authored-stat, balance, durability, and integration evolution of the accepted V1 mining engine, not a second mining engine. Totality still uses discrete complete physical strikes rather than vanilla continuous progress; holding LMB schedules repeated swings, and the server owns impact timing and re-raycasts at the contact frame using the current player position, eye/rotation, reach, held source, and target.

IMPLEMENTED / RETAINED: Block Durability remains maximum structural HP and Block Integrity remains the current shared HP for the block position. Damaged state is shared between miners, persisted, lazily recovers, maps to vanilla crack stages 0-9, and synchronizes on join/respawn through the existing BlockDamageStorage path.

IMPLEMENTED / RETAINED: Mining Damage, Mining Tier, and Mining Speed are separate axes. Tier is capability, Damage is structural damage per impact, and Speed is sustained impact cadence. Final break still routes through the authoritative vanilla ServerPlayerGameMode destruction path so normal loot tables, Silk Touch/Fortune, block-specific behavior, stats/events, and compatible tool behavior remain available.

IMPLEMENTED / RETAINED: source identity ignores legitimate durability mutation but detects a materially different stack/configuration/enchantment source. Exact-position HarvestGrant remains narrowly scoped to the terminal Totality break so qualified non-tools can receive the intended vanilla drop without globally changing Player.hasCorrectToolForDrops.

IMPLEMENTED / VERIFIED WEAR RULE: a successful damaging conventional mining impact requests one base item-durability wear; MISS / DENIED / INEFFECTIVE / cancelled impacts request zero. The terminal break avoids double wear. Mining wear continues through vanilla ItemStack.hurtAndBreak, so Unbreaking applies through the normal item-damage path.

LOCKED EFFECTIVENESS PRINCIPLE: Source Tier < Required Tier resolves INEFFECTIVE, causes zero Integrity damage, and pays no normal impact cost. If Tier qualifies, the wrong profiled tool may still physically damage the block at the authored wrong-tool penalties; Totality deliberately does not copy vanilla's under-tier "break slowly but lose drops" rule.

LOCKED PROGRESSION CONTINUITY: Mining continues to advance through mining in the current use-leveling progression path. Exact long-term naming/ownership between Skill and Profession remains a later taxonomy question; PASS6 did not remove or redirect Mining XP, and future V2 cleanup must not silently disable it.


## 26L.2 Current authored conventional tool profiles - accepted V2 values

ACCEPTED CURRENT VALUES for conventional Pickaxe / Axe / Shovel profiles, excluding Gold: Wood = 20 Mining Damage, 1.50 impacts/s; Stone = 35, 2.00/s; Copper = 40, 2.10/s; Iron = 50, 2.25/s; Diamond = 75, 2.40/s; Netherite = 100, 2.50/s.

LOCKED FAMILY RULE FOR THIS BASELINE: a material's Pickaxe, Axe, and Shovel share the same authored Mining Damage, Mining Speed, and material capability tier unless a later tool-specific reason is deliberately authored. Effectiveness still comes from the target/tool-family relationship, not from giving every tool family unrelated material stats.

OPEN / NEEDS FINAL V2 AUTHORING: Gold is still on the legacy/unbalanced path and must receive deliberate Totality values before V2 closes. Mining Tier is also still partly derived from vanilla Pickaxe capability at startup/resolution for these profiles; the final V2 baseline should author Tier explicitly rather than relying indefinitely on vanilla-derived probing.

CURRENT INTERIM BLOCK DATA: Stone, Cobblestone, Diorite, and Andesite are authored at 100 Block Durability; ordinary burnable overworld logs/wood/stripped wood are authored at 100; Dirt and Grass Block are authored at 100. Granite may currently resolve to 100 through fallback, but that coincidence is NOT an authored final Granite decision.


## 26L.3 Tool effectiveness, Impact, Efficiency, Haste, and cadence

ACCEPTED TARGET EFFECTIVENESS: correct preferred/effective tool = Damage x1.00 and Speed x1.00. Wrong profiled tool, after passing the Tier gate, = Damage x0.10 and Speed x0.50. There is no additional wrong-tool durability penalty beyond the ordinary successful-impact wear.

ACCEPTED SPEED ORDER: Base Mining Speed + Efficiency -> Haste multiplier -> environmental modifiers/current cap -> target-effectiveness multiplier. The current ordinary cap is 10.0 impacts/s (two ticks per impact), so the x0.50 wrong-tool effectiveness caps a wrong profiled tool at 5.0 impacts/s.

ACCEPTED EFFICIENCY SPEED BONUSES: Efficiency I +0.5 impacts/s; II +1.0; III +2.0; IV +3.5; V +5.0. Efficiency changes cadence only and does not increase structural Mining Damage.

ACCEPTED HASTE FORMULA: after the additive Efficiency step, Haste multiplies speed by 1 + 0.20 x displayed Haste level; Haste II therefore multiplies the pre-Haste cadence by 1.4. The fractional cadence accumulator is retained so non-integer speeds are represented rather than rounded into coarse whole-tick tiers.

IMPLEMENTED / VERIFIED CADENCE FIX: the earlier scheduler off-by-one error was corrected and sustained impacts now follow the intended accumulator/cycle timing. First-impact latency remains a separate feel/presentation question from sustained Mining Speed.

ACCEPTED IMPACT ENCHANTMENT: Impact I/II/III/IV/V adds +5/+10/+15/+20/+25 Mining Damage respectively, affects mining only, and is compatible with Efficiency. The generated supported-tool tag covers the authored Pickaxe/Axe/Shovel set (18 current items) while Gold remains excluded until its V2 profile is finalized.

REGRESSION TARGET EXAMPLES for 100-Durability Dirt/Grass: wrong-tool Netherite Pickaxe = 10 hits; Netherite Pickaxe + Impact V = 8; Diamond Pickaxe = 14; Diamond + Impact V = 10. Correct Shovel baseline hit counts are Wood 5, Stone 3, Copper 3, Iron 2, Diamond 2, Netherite 1; Diamond + Impact V is also a one-hit case. These are accepted consequences of the current V2 values and useful test anchors, not a universal law for every future block.


## 26L.4 Power Mining V2 bands, STR, and wear

LOCKED V2 POWER RULE: STR affects Alt/Power Mining only; ordinary mining no longer receives a normal STR damage bonus from the profiled tool path. STR modifier is floor((STR - 10) / 2).

WHITE: normal authored tool damage; no STR bonus; extra Power wear 0. GREEN: +1 x STR modifier Mining Damage; extra Power wear 0. ORANGE: +2 x STR modifier Mining Damage; extra wear max(0, 1 x STR modifier). RED: +5 x STR modifier Mining Damage; extra wear max(0, 5 x STR modifier). A successful damaging conventional impact still requests the one base durability wear in every band.

WEAR EXAMPLE: at STR 20 (modifier +5), requested total conventional-tool wear is White 1, Green 1, Orange 6, Red 26. MISS / DENIED / INEFFECTIVE / cancelled impacts still request zero. This is requested wear before vanilla durability mechanics such as Unbreaking resolve the actual item-damage result.

DAMAGE ORDER: Base Tool Damage + Impact enchantment + Power STR contribution, then target Damage effectiveness. The explicit Power-state boolean allows a real WHITE Power band rather than collapsing the safe band into GREEN.

FORCE TOLERANCE STATUS: Force Tolerance remains a real advanced mining-source stat and is shown as advanced tooltip information, but the current profiled V2 Power bands/wear no longer use the old V1 tolerance/load formula as their governing rule. Final authored Force Tolerance behavior remains to be reconciled during the complete V2 source/profile pass.

PRESENTATION STATUS: the current four-zone Power meter is temporary and can visually overpaint segment boundaries in some fills; the intended future radial/circular force HUD remains deferred. Bare-hand Power body-strain behavior was not redesigned by these profiled-tool changes and retains the existing V1 path pending later reconciliation.


## 26L.5 PASS5 / PASS6 implementation and verification checkpoint

PASS5 IMPLEMENTED: wrong profiled tool Damage multiplier changed from 0.25 to 0.10 while Speed remained x0.50; Dirt/Grass became authored 100-Durability targets; Shovels received the same material progression including Copper; Impact support expanded through the generated Pickaxe/Axe/Shovel tag; the four real Power bands replaced the prior WHITE->GREEN collapse; normal/WHITE floating damage became visually white while GREEN remained distinct.

PASS5 TOOLTIP/RENDER FIXES: the scrollbar gutter is no longer reserved during the width decision, fixing the unnecessary wrapping case; Totality tooltip drawing is deferred into the vanilla topmost tooltip stratum so JEI does not overpaint it, without adding a JEI dependency. A datagen startup failure caused by eager ItemStack/Tier probing before data components were bound was corrected by making the profile probe lazy at resolution time.

PASS6 MELEE DURABILITY REGRESSION FIX: Totality melee durability now obtains the live vanilla Weapon component itemDamagePerAttack and calls ItemStack.hurtAndBreak with the actual EquipmentSlot after the Totality attack result is confirmed. This restores vanilla durability processing/Unbreaking to the custom melee path and removes the former hard-coded offhand durability decrement. Mining already used the vanilla hurtAndBreak path.

PASS6 TOOLTIP COMPACTION: body text and stat icons use the same 0.875 content scale; gaps were tightened while preserving scrolling, width/wrapping logic, content-driven eligibility, and the JEI/topmost rendering behavior. Manual screenshots were judged improved enough to defer broader aesthetics to Tooltip V2/Core rather than keep polishing V1.

REVIEW EVIDENCE: the full PASS6 review bundle that was actually inspected contained 18 entries, was 116,067 bytes, and had SHA-256 0d7517e0e6e4bdd16d7df6cea1c79a3ba0f592b10640ca6efa186984cdbb527c. Treat the review conclusions here as evidence from that concrete bundle rather than an unverified implementation summary.

TESTED / VERIFIED CHECKPOINT: the PASS6 bundle reported 1774/1774 build tests; live DurabilityRegressionVerification 12/12, MiningVerification 240/240, and PowerAttackVerification 12/12. OffhandAttackVerification remained 3/5 with its historical pre-existing failure signature. Manual testing accepted Impact/Efficiency/Haste behavior and the current tooltip cleanup.

MANUAL OFFHAND SCOPE CLARIFICATION: Pickaxe/Axe/Shovel cannot be meaningfully exercised as offhand attack weapons in the current dual-wield combat implementation, so manual offhand mining-tool melee durability is not a required user test. Automated coverage remains the appropriate check for that path.

NONBLOCKING REVIEW NOTE: custom melee wear is tied to the Totality confirmed-hit/contact result rather than proving that target HP actually changed. That is acceptable for the current physical-contact semantics, but comments/tests should not overstate it as exact vanilla postHurtEnemy equivalence if this area is revisited.


## 26L.6 Remaining Block Breaking V2 correctness issue - stale target cadence

IMPLEMENTED BUT NEEDS CORRECTION: with a profiled tool such as a Netherite Pickaxe, beginning a swing while aimed at a wrong target such as Grass and then returning to a correct target such as Stone can leave the Stone cadence feeling much slower, as though the previous target's wrong-tool multiplier is still governing recovery.

ROOT CAUSE IDENTIFIED BY CODE INSPECTION: the swing cycle is scheduled from the target state seen when the cycle begins, including TargetEffectiveness speedMultiplier, while the authoritative impact correctly re-raycasts and may hit a different block. The already-scheduled recovery/cadence can therefore retain the stale target's speed relationship even though damage is applied to the new target.

FIX REQUIREMENT: make a small retarget/cadence correction so a changed target does not carry an inappropriate old effectiveness cadence through subsequent swings. Preserve discrete complete strikes, server-authoritative impact re-raycasts, and exploit resistance; do not turn the fix into continuous vanilla break progress or client-owned timing.

SEPARATION: this stale-cadence bug is session/swing-state behavior and does not persist across world reload. It is unrelated to the slow Saving World investigation recorded in Section 26L.10.


## 26L.7 V2 closure requirement - complete vanilla block and tool baseline

LOCKED V2 FINISH LINE: Block Breaking V2 does NOT close after a handful of calibration blocks. Before closure, author a final-for-now baseline for all relevant vanilla Minecraft 26.2 blocks so later Totality materials, Architecture, machines, and dimensional content have a coherent reference foundation.

REQUIRED BLOCK DATA FOR THE V2 SLICE: Block Durability; Required Mining Tier; Effective Tool family (Pickaxe / Axe / Shovel / none / authored special); breakability/special behavior; sensible material/material-family assignment; and exact block overrides where a family default is inappropriate.

DO NOT FORCE EVERY BLOCK INTO STRUCTURAL HP PLAY: plants, fragile decorations, fluids, unbreakable blocks, special-interaction blocks, and similar cases may need authored instant/special/unbreakable behavior rather than arbitrary durability numbers merely because the profile can store them.

TOTALITY, NOT VANILLA, IS THE BALANCE AUTHORITY: vanilla hardness/tool rules are useful familiarity and time-calibration evidence, but they are not binding. Gold Block, Iron Block, Diamond Block, Obsidian, and other materials may deliberately differ from vanilla when Totality's own physical/material/progression logic calls for it.

BALANCE METHOD REMAINS TWO-DIMENSIONAL: author desired strikes-to-break and desired seconds-to-break separately. Use block/material families to reduce arbitrary one-off numbers, then author exact overrides where needed. Avoid deriving the entire new dataset from one vanilla-hardness formula.

TOOL FINISH LINE: finalize all vanilla conventional Pickaxe/Axe/Shovel material profiles, including proper Gold values, explicit authored Mining Tiers, and an initial Force Tolerance baseline if that property is ready enough to author. Preserve the accepted Damage/Speed values above unless new all-block evidence reveals a real contradiction.


## 26L.8 Block/Material Profile foundation and Architecture relationship

WORKING DESIGN - NOT YET CLOSED: the complete block baseline should not become hundreds of mining-specific Java conditionals. Introduce a reusable Block/Material Profile foundation sufficient for the V2 mining slice, while deliberately leaving the full future physical/Architecture API open for its dedicated design pass.

LIKELY V2 PROFILE SLICE: Material or Material Family; Block Durability; Required Mining Tier; Effective Tool; Breakability; and authored special overrides. Exact class/API names, registries, inheritance rules, datapack schema, and full physical-property set remain design work rather than locked by this consolidation.

IMPORTANT SEPARATION: Block Durability is not automatically Structural Strength. Hardness, toughness, brittleness, density, structural strength, thermal behavior, moisture behavior, fire performance, acoustics, electrical properties, radiation shielding, fluid permeability, decay/corrosion, and similar future properties may influence one another but remain distinct concepts owned by the appropriate profile/system.

ARCHITECTURE INTEGRATION DIRECTION: later Architecture/Building Fabric/Building Services should be able to consume shared static material/block profiles rather than invent duplicate material facts. Architecture still owns buildings, rooms, layered assemblies, structural graphs, services, dynamic conditions, environmental state, maintenance, and higher-order building behavior.

PERFORMANCE DIRECTION REMAINS CONSISTENT WITH Section 26G.9: static shared properties belong in definitions/profiles; finite local block state belongs in BlockState where appropriate; complex mutable individuality belongs only in Block Entities that need it; building/network/regional state belongs in managers/graphs rather than per-block ticking.

WORKING CROSS-REALITY MATERIAL IDENTITY DIRECTION: the same family name across realities does not have to mean the same substance. Future material identity may distinguish Family, Variant, Provenance/Reality, Form, and resolved physical/special properties - for example Terra Genesis Copper versus Marvel Copper - so recipes/tools/Architecture can ask for the appropriate family, exact variant, provenance, or property without flattening every universe into one material definition.

DO NOT OVER-DESIGN YET: provenance-preserving alloys, forms, identification, tool-head resolution, and other material-system extensions remain future work. V2 should establish only the profile foundation genuinely needed to author and consume the complete vanilla mining baseline cleanly.


## 26L.9 Historical Tooltip API V1 -> V2 preparation (completion in §26M)

CURRENT IMPLEMENTATION TRUTH: the existing custom tooltip/classification system should now be called Tooltip API V1. It already has rarity/type/lore infrastructure, custom rendering, scrolling, stat/icon presentation, content-driven eligibility in the current local implementation, and the PASS5/PASS6 wrapping/JEI/body-scale fixes. Scrolling is present now and must not be described as a future feature.

V2 ORDERING DECISION: design/finish Tooltip API V2 BEFORE authoring the entire final vanilla block dataset. The purpose is not to let the UI own block data; it is to establish what semantic information the player-facing contract needs so the Block/Material Profile can expose the right stable fields without later redesigning hundreds of entries.

V2 ARCHITECTURE DIRECTION: semantic/data contributors -> a generic tooltip content model -> presentation/rendering. Onboard item families through profile/tag/data/semantic contributors rather than one bespoke renderer per item. Exact-item overrides remain available only where an item genuinely needs unique behavior.

CONTENT-SCALING RULE TO FIX IN V2: compact/stat sections should explicitly opt into scaled presentation rather than relying on a default that causes unknown/new sections to be scaled automatically. This is a V2 API hygiene point from the PASS6 review, not a request to reopen the accepted V1 visual cleanup.

PRESENTATION HIERARCHY - DIRECTION, NOT FINAL PIXELS: larger item/model presentation where useful -> colored item name -> rarity -> Category/Type -> primary stats -> secondary/vanilla stats -> special properties -> lore near the bottom -> compact footer. Major separators and spacing should support readability; exact colors/dimensions/ornament remain open until the broader Core/UI visual language is chosen.

RARITY PRESENTATION DIRECTION: rarity may influence frame/corner treatment, glow/effects, name animation, or model/icon presentation without rearranging the semantic hierarchy. Exact ornament/effect rules remain open until the shared visual language is chosen.

3D / MODEL PREVIEW DIRECTION: Stefan specifically likes the inspiration-mod presentation where tools, armor, and similar equipment receive a larger rendered/model preview. Totality should independently implement the presentation concept rather than copy code. Simple materials/ingredients/food may keep the ordinary sprite; tools, weapons, armor/equipment, machines, and blocks can opt into larger sprite/item-model/block-model presentation where it materially helps.

WORKING PRESENTATION MODES discussed conceptually include standard sprite, large sprite, item model, block model, and possibly later equipment/entity preview. These labels are NOT locked enum/API names; the locked point is conditional presentation chosen by semantic item needs rather than one forced header for every item.

BLOCK TOOLTIP CONTRIBUTOR DIRECTION: BlockItems should be able to present relevant stable information such as Material, Block Durability, Effective Tool, and Required Mining Tier while omitting irrelevant rows. Current Integrity belongs to later placed-block inspection/context rather than pretending the ItemStack owns world damage state.

MINING TOOL CONTRIBUTOR DIRECTION: normal practical rows remain effective Mining Damage, Mining Speed, and Mining Tier; Shift/advanced information can expose provenance/contributions and Force Tolerance. The tooltip displays/query-resolves these semantics but does not become their source of truth.

UNIVERSAL ITEM DURABILITY - PLANNED CONTRIBUTOR: any damageable item category (tools, weapons, armor, shields, equipment, future devices) may eventually expose a compact durability row/bar such as current/max. Do not apply it to ordinary BlockItems merely because blocks have structural Durability, and avoid duplicating enchantment text such as Unbreaking in the advanced provenance section without a real reason.

CORE BOUNDARY: do not throw away Tooltip V1 or prematurely lock the final visual identity during V2. The future Totality Core redesign will revisit D&D-specific tooltip assumptions such as the current weapon block and will choose the broader UI typography/colors/panels/icons/spacing language once rather than redesigning each screen independently.


## 26L.10 Slow Saving World regression - diagnosed as development-toolchain behavior

RESOLVED INVESTIGATION, NOT A GAMEPLAY FIX: Loom/runClient integrated worlds developed abnormally long final "Saving World" delays, including fresh Superflat tests. Packaged Totality running as a normal JAR through Prism saved in roughly vanilla time, while vanilla Minecraft 26.2/26.3 and a Fabric control also saved normally.

THREAD-DUMP EVIDENCE: during a slow development shutdown, the Server thread was waiting in CompletableFuture.join -> ChunkMap.saveAllChunks -> ServerChunkCache.save -> ServerLevel.save -> MinecraftServer.saveAllChunks/stopServer, while an IO worker was actively in FileChannel/RegionFile/RegionFileStorage/IOWorker write code. The delay was therefore real overworld chunk-region IO, not merely a frozen title screen or an exception hidden after shutdown.

ISOLATION RESULT: disabling the live verifier registrations did not remove the slow save, and removing JEI did not remove it. Lowering render/simulation distance also did not remove the original abnormal delay. These experiments separated the save regression from verifier contamination and ordinary view-distance tuning.

KEY TOOLCHAIN RESULT: after moving the Gradle wrapper from 9.5.1 to 9.7, development saves returned to normal one-second-class behavior with Loom 1.17. A separate Gradle 9.7 + Loom 1.18 test also saved in roughly one-to-two seconds. A later fresh high-distance (view 12 / simulation 8) Gradle-9.7 test also saved in about one second; the one long high-distance save occurred immediately after increasing the distances and loading additional chunks just before exit.

CONCLUSION: do not modify Totality mining, persistence, SavedData, block profiles, or gameplay code to "fix" this resolved save delay. The evidence isolates the abnormal behavior to the older development toolchain / Gradle 9.5.1 interaction rather than the packaged mod.

CURRENT DEVELOPMENT BASELINE / HISTORY: the final save-regression tests kept Minecraft 26.2, Java 25, Fabric Loader 0.19.5, and Fabric API 0.161.0+26.2 while the Gradle wrapper was corrected to 9.7. The final Loom line is intentionally not locked here because both 1.17 and 1.18 were tested successfully with Gradle 9.7. Historically, the validated older 26.2 line used Loader 0.19.3, Fabric API 0.154.2+26.2, Loom 1.17-SNAPSHOT/1.17.x, Gradle 9.5.1, and Java 25. On 19 September the working tree recorded a Loader 0.19.3 -> 0.19.5 and Fabric API 0.154.2 -> 0.160.0 bump before the later runtime reached Fabric API 0.161.0+26.2. The previous Minecraft 26.1.2 checkpoint used Loader 0.19.2, Fabric API 0.146.0+26.1.2, and Loom 1.16-SNAPSHOT.

LOOM 1.18 COMPATIBILITY TEST: setting loom_version=1.18-SNAPSHOT resolved to Loom 1.18.2 but could not load under Gradle 9.5.1 because the plugin declared Gradle plugin API 9.7.0. After upgrading the wrapper to Gradle 9.7, Loom 1.18 worked. Loom 1.17 also remained fast under Gradle 9.7, so Loom 1.17 itself is not identified as the save-delay cause.

VERSION STATUS: Gradle 9.7 is the corrected local development baseline from this investigation unless later evidence changes it. The final chosen Loom line is not locked by this consolidation; both 1.17 and 1.18 were tested successfully with Gradle 9.7. If the project adopts 1.18 for the ongoing baseline, prefer a pinned stable release such as 1.18.2 rather than an indefinitely moving 1.18-SNAPSHOT.


## 26L.11 Live verification suites - separate world-contamination problem

OPEN / NEEDS CORRECTION: development verification suites can mutate ordinary manual-test worlds and can leave visible artifacts such as changed blocks or dropped entities/items. Fresh-world observations included unexpected Cobblestone and Rotten Flesh. The investigation did not safely prove one single verifier as the sole source, so do not attribute every artifact to one class without an audit.

TEMPORARY LOCAL STATE: while isolating the save regression, the main live Verification.register calls in Totality.java were commented out across the mining/trading/combat/resource/migration/Food/Soul-Gem suites. The slow save persisted, proving that verifier execution was not the root cause of the Saving World regression.

REQUIRED FIX: do NOT simply restore destructive verification to automatic execution in every development integrated world. Add one explicit global opt-in gate (for example a development system property such as totality.runLiveVerifications=true or an equivalent dedicated run/test switch) so ordinary manual runClient playtests do not automatically execute world-mutating integration suites.

AUDIT REQUIREMENT: search all registration paths, not only Totality.java, for Verification.register, runIfDev, SERVER_STARTED, or equivalent automatic hooks. Classify each verifier by world mutation; ensure temporary blocks/entities/drops/SavedData are restored/removed in finally-style cleanup where a live-world test remains necessary; prefer dedicated test/run environments for destructive cases.

RESTORE POLICY: after the audit/gate exists, restore verifier registrations behind that explicit opt-in path and keep normal build/JUnit verification separate. This is a development-environment hygiene fix, not a change to Block Breaking gameplay rules.


## 26L.12 Skills screen restoration and later Core/UI boundary

PRESERVED PLAN: the historical dedicated SkillsMenuScreen should be restored functionally rather than replaced by an empty Skills tab. The latest useful pre-deletion source is the final parent before commit 68f7939, at 5722310fa9391fa5100540315e518424f4382e0d. Restore/adapt it to current APIs/navigation/unlocks without aesthetic redesign first.

FUNCTIONAL-FIRST SCOPE: preserve the two-state skill overview/mastery flow, skill-level/XP/mastery information, navigation, and unlock behavior. Old colors and vanilla item icons are acceptable during the restoration; do not delay correctness to redesign the screen visually.

VISUAL UNIFICATION LATER: Tooltip V2 can improve the immediate tooltip header/presentation contract, but final cross-screen typography/colors/panels/icons/spacing/transitions should wait for the larger Totality Core/UI pass so Skills, Character, Tooltip, Phone, and other screens are not independently restyled into incompatible visual systems.


## 26L.13 Historical v3.5 handoff ledger and next-work order (superseded by §26M.19)

HISTORICAL v3.5 HANDOFF RULE (SUPERSEDED BY §26M.19): The previous chat order began with v3.5 / §26L for Block Breaking V2, preliminary Tooltip V2, profiles, verifier and toolchain; then §§26K, 26J, 26I, 26H and the owning sources. For v3.6 begin with §26M, which records the accepted Tooltip presentation and current Notice Board/Writ decisions; consult §26L afterward for unaffected Block Breaking, verifier, profile and toolchain status.

HISTORICAL v3.5 PLAN - DONE THROUGH ACCEPTED PRESENTATION, COVERAGE AUDIT STILL OPEN: NEXT PASS 1 - TOOLTIP API V2: use a dedicated fresh chat/pass. Evolve the existing V1 foundation rather than replacing it; settle semantic contributors/content model/presentation, improve the top/header, investigate conditional large 3D/model previews, define BlockItem/mining-tool contributors, and preserve scrolling/current V1 capabilities.

NEXT PASS 2 - BLOCK BREAKING V2 FINALIZATION: after Tooltip V2 is sufficiently defined, use a dedicated Block Breaking finalization pass. Fix stale-target cadence; add verifier isolation/cleanup; establish the V2 Block/Material Profile foundation; author the complete final-for-now vanilla 26.2 block baseline and final conventional tool profiles including Gold/Tier/initial Force Tolerance where ready; integrate the profiles into Tooltip V2; run automated/live/manual regression; then close V2.

DO NOT REOPEN ACCEPTED BALANCE WITHOUT EVIDENCE: Impact, Efficiency, Haste, current conventional non-Gold Damage/Speed values, wrong-tool x0.10/x0.50 effectiveness, the 10 impacts/s cap, and four Power bands are accepted current V2 values. Adjust them only if the complete all-block pass or regression testing reveals an actual contradiction/problem.

AFTER V2: functionally restore the historical SkillsMenuScreen if it has not already been restored, then proceed toward the larger Totality Core redesign. The Core pass may revisit D&D-derived universal assumptions and the final shared UI language, but it should consume rather than casually discard the closed/validated foundations that remain compatible.

MAINTENANCE DEBT STILL EXISTS OUTSIDE THIS V2 SCOPE: the Component Pouch SORT server-authority exploit, dead EnergyFaceConfig payloads, empty block_damage.dat cleanup, historical Offhand verification issue, and earlier audit debt remain real. Keep them visible, but do not confuse them with the resolved save-world regression or use them as a reason to broaden the Tooltip/Block Breaking passes into unrelated refactors.


## 26M. v3.6 Tooltip V2 Completion and Notice Board/Writ Design Reconciliation - 23-24 September 2026

STATUS / AUTHORITY: Newest authority for the topics addressed here. This section consolidates accepted design decisions, distinct implementation review checkpoints, in-game presentation corrections and current deferrals from the present long-running chat. It overrides inconsistent v3.5/§26G.18/§26L.9 and dedicated Notice Board PDF language, while preserving all compatible earlier code and design boundaries. Do not mistake an accepted design for implemented code, or a review-bundle verification for an actual repository commit.


## 26M.1 Evidence, chronology, and status discipline

SOURCE BASIS: Full v3.5 Master; current conversation and its preserved earlier-turn summary; dedicated TOTALITY_NOTICE_BOARD_API_DESIGN.pdf; attached presentation/header/body/bottom/final-correction review ZIPs and their reports, tests, patches and screenshots; direct in-game screenshot feedback. The separate repository was not modified or pulled for this consolidation.

EVIDENCE LEVEL: Review reports record Gradle/test/server and live-client results, and the prior chat review explicitly accepted the final presentation. The present consolidation reads those artifacts; it does not independently run the entire Gradle project. The newest final-correction bundle reports 1,918 passing tests, zero failures/errors/skips, a clean build, dedicated-server exit 0, 15 real-client screenshots at GUI scales 1/2/4, a reproducible 12-file task-only patch and removed temporary harness.

STATUS: Tooltip V2 universal presentation/header/body/bottom/final corrections: IMPLEMENTED / REVIEWED / ACCEPTED-CLOSED for those slices. Tooltip V2 all-item-family coverage: NOT STARTED. Block Breaking V2 and other §26L work retain their separately recorded status; no new implementation-completion claim is inferred from the tooltip bundles.

WORKTREE: Latest bundle explicitly says no commit or push; related local changes may be uncommitted. Nothing here authorizes Git cleanup or merging; all future work must preserve unrelated local edits, worlds, run files and test artifacts.


## 26M.2 Tooltip V2 implementation milestones and accepted review sequence

PRESENTATION / PREVIEW SLICE: Real custom renderer, item/model preview, adaptive sizing and companion-card direction implemented; report: 1,818 tests, zero failures. Subsequent corrective follow-up fixed AUTO behavior and explicit true BLOCK_MODEL; report: 1,821 tests, zero failures.

HEADER REFINEMENT: 56-pixel preview hierarchy, centered name, slim pointed framed rarity plaque with existing rarity theme, paired classification lines and one header/body divider; skull/pumpkin companion-head overlap and unnecessary block-preview resolution corrected. Report: 1,845 tests, zero failures; reviewed and accepted before the body work.

BODY FRAMEWORK: Semantic registry and merging of contributor sections under one heading, ordering, reusable full-color icon capable heading painter and in-place details; report: 1,887 tests, zero failures; reviewed and accepted before bottom work.

BOTTOM PRESENTATION: Stable textured group icons, PROPERTIES consolidation, Energy/Durability bars, bottom ordering, lore, footer, detached modifier cards and GUI-scale typography; report: 1,908 tests, zero failures. In-game GUI scales 1, 2, 4 and source/screenshots reviewed.

FINAL CORRECTIONS: Exact integer-derived bar fill and overflow-safe percentages, hidden scroll indicators, existing Weight icon with lossless meaningful decimal display, battery I/O shifted into DETAILS. Report: 1,918 tests, zero failures. Prior review accepted/closed this slice; item-family coverage has deliberately not begun.


## 26M.3 Universal Tooltip V2 header and preview - implemented

HEADER ORDER: One 56 px large stack-specific preview; centered item name (existing rarity name animation); centered rarity plaque; centered classification lines; single ornamental divider to body. Rarity affects the vignette/frame/plaque palette without reshuffling semantic hierarchy. No plaque or classification placeholder is invented for absent data.

PREVIEW MODES: Authored AUTO, SPRITE, ITEM_MODEL and explicit BLOCK_MODEL; static by default with authorable SLOW_ROTATE; block inventory AUTO should use the item model, while BLOCK_MODEL truly renders an authored block-model preview and is resolved only when explicitly required. Stack-specific PIP/preview caching remains; actual equipment companion card stays separate on the left.

PLAQUE: A slim one-shape pointed banner with 1 px frame and fading flourishes for every rarity theme; uses existing rarity color and a readable lightened label. All existing 21 ItemRarity variants remain covered. Ancient remains #3ADBC4; no palette replacement. Dark FORBIDDEN heading lines remain faint; do not silently recolor the established tier.

CLASSIFICATION MODEL: Existing ItemType remains the category; registered namespaced type Identifier supplies specificity; examples TOOL • AXE and WEAPON • TWO-HANDED. Every authored pair has its own centered line; legacy category-only entries still share a line, e.g. BATTERY • ENERGY. Registration validates authored types; persisted old entries and unregistered legacy type values remain safely readable. No blanket item-family migration is implied by screenshot-only harness entries.

COMPANION CARD: For armor, the virtual-player preview shows the hovered gear without mutating real equipment. HEAD preview now accounts for worn skull/pumpkin special layers, not only armor equipment. Avoid reintroducing a stacked pumpkin/helmet error.


## 26M.4 Semantic body groups, ordering, and disclosure - implemented foundation

GROUP AUTHORITY: Registered stable namespaced group IDs, localization, icon, default priority and optional authored group_order. Contributors produce semantic content; renderer merges same-group entries, deduplicates repeated content, uses deterministic ordering and never creates an empty group or drops the heading merely because it is the only group.

GROUP PRESENTATION: Centered unit of full-color resource-pack-overridable texture icon plus uppercase group name, symmetric 1 px outward-fading rarity-colored divider lines. Icon assets are never accidentally tinted; custom stat-row glyphs/icons retain their existing semantics. Stable built-ins include MINING, COMBAT, MAGIC, EFFECTS, ABILITIES, ENCHANTMENTS, PROPERTIES, REQUIREMENTS, ENERGY, DURABILITY and other registered semantics.

PROPERTIES: One universal totality:properties group reuses properties.png; Block Durability, Required Mining Tier, Effective Tool and Fuel can appear here as relevant. No separate block_properties heading. Structural Block Durability is NOT an ItemStack wear bar; current placed-block Integrity remains outside the unplaced item tooltip. No invented Material row when the owning Block/Material Profile does not expose it.

ORDER: Classification can lead the relevant ordinary group and authored group_order overrides ordinary priority; groups then follow default priorities. Bottom mechanical groups follow ordinary groups, in order Requirements -> Energy -> Durability where present; lore and footer follow. A group-order override cannot promote a bottom group into the ordinary region. Abilities, Enchantments and Properties are independent semantic headings, not conflated.

DEFAULT / SHIFT / CTRL: Default reports effective values; SHIFT/DETAILS reveals truthful breakdown immediately beneath each relevant value (mining base/material/attribute provenance, exact resource figures and item I/O rates where authored). CTRL/TECHNICAL is a provisional in-place technical layer. SHIFT+CTRL reveals both once in appropriate positions. Do not duplicate effective stats or turn the disclosure into a separate tooltip. ALT/INTERACT is reserved for future Codex integration and is never offered yet.

CLASSIFICATION AND SOURCE TRUTH: Tooltip queries the owning combat, mining, resource, value and other APIs; it does not calculate or persist authoritative gameplay state. Keep absent and unimplemented fields omitted rather than fabricate compatibility data.


## 26M.5 Energy, Durability, requirements, lore and footer - accepted behavior

RESOURCE PRESENTATION: A centered heading/icon, slim bar and centered current/max amount plus percentage. Normal display uses compact values that round down instead of making non-full resources appear full; SHIFT may show exact amounts. Energy uses the canonical UE blue family, darkening on depletion rather than green/yellow/red. Durability uses green, orange at or below 50%, red at or below 20%; the thresholds are a local presentation choice, not a systemic balance rule.

PRECISE RESOURCE MATH: ResourceGauge carries exact long current/max values. Pixel fill checks zero/full by integers; partial amounts are clamped to 1..(interiorWidth - 1), so 999,999,999/1,000,000,000 UE never draws full. Percentage uses overflow-safe BigInteger-based flooring; zero is 0%, full is 100%, nonzero below one percent is shown as <1%, and partial charge never reports 100%. Same exact path serves Durability; underlying Energy authority/storage is unchanged.

BATTERY DISCLOSURE: Normal ENERGY has bar, figures and applicable status (e.g. Inactive); I/O Rate such as 32 / 32 UE/t appears only with SHIFT, directly below figures, not with CTRL alone and exactly once with SHIFT+CTRL. UEItem defaults to DETAILS for I/O; another item can expressly author always-visible rates. No battery-only renderer branch.

ENERGY + DURABILITY: If an item has both they appear in that order. No currently authored item showed both in the report; combination was tested but not photographed. Future Energy consumers should not invent an either/or limitation.

LORE: After mechanical groups with a visible gap, smaller readable italic/quieter text retains authored formatting. At GUI scale 1 the body/lore typography uses a pixel-safe full scale to avoid lost pixel rows; GUI 2+ retains the smaller preferred presentation where pixel coverage is adequate.

FOOTER: Weight at left as existing totality:icons WEIGHT glyph plus numeric value (1.0 -> 1, 1.5 -> 1.5, 0.25 -> 0.25); Content Origin centered (explicit persistent/network-synced authored provenance such as Skyrim or Bleach, NOT source of acquisition or registry namespace); Price at right in Credits where authoritative ItemValueRegistry value is available. Absent fields omitted; no invented Free/0 or fixed Totality attribution. Center wraps to avoid collision.

SCROLL / MODIFIER CARDS: Adaptive tooltip sizing, bounded positioning, body clipping and mouse-wheel scrolling remain. Deliberately NO visible scrollbar, gutter or Scroll: More hint; cut-off content alone signals overflow. Detached bounded SHIFT Details and CTRL Technical cards appear only when applicable, held state highlighted; ALT card reserved but not offered. Tooltip and cards remain reachable at GUI 1/2/4 and near screen edges.


## 26M.6 Tooltip V2 known limits and next coverage phase

IMPLEMENTED / CONTENT INCOMPLETE: Universal tooltip presentation is accepted, but individual vanilla and Totality item-family coverage has not been audited. Next perform a READ-ONLY repository and authored-content inventory to classify already integrated families, partially covered contributors, missing families and integrations waiting for owning APIs. Do not mark this phase complete or implement blindly.

DEFERRED / OWNER API: Full mining/block data after Block Breaking V2 and Block/Material Profiles; Architecture assemblies/material; Fluid/Thirst and Fluid Tank; Diet/nutrition; future Energy devices and consumer-specific technical values; Notice Board Writ tooltip after its own V1; ALT Codex actions once Codex exists. Item values are currently NOT synced to remote clients: dedicated-server Price omitted rather than guessed, to revisit with Economy.

STABLE GROUP ART: Group assets use stable resource paths and full-color untinted texture rendering; preserve the universal properties.png and stable combat.png resource paths so future original art can replace their PNGs without code changes. Existing stat-row icons are separate. The temporary magic/durability/effects icons were corrected to 16x16 to avoid dropped alternate pixels when rendered.

PRE-EXISTING ASSET/TECHNICAL DEBT: Missing model textures for Credit Chip/credits, incense, blessed incense, Shinigami robe and asauchi (also ritual_dais_active block model warning); technical CTRL identifiers may hard-wrap mid-token. Group art including copied vanilla placeholders needs original replacement before distribution, not silent shipping. Source reports note a few downsized temporary icons and FORBIDDEN dark-color contrast. No actual in-game Material row without a source.

WRIT TOOLTIP PROFILE - DEFERRED UNTIL NOTICE BOARD V1: Use this same universal Tooltip V2, not a Writ-only second renderer. Potential paired identity WRIT • GATHERING or another authored contract kind; show assessed/known rank, issuer, objectives/progress, rewards, conditions, submission method, optional deadline and status. SHIFT can reveal truthful expanded terms/original rank where knowledge permits; hidden actual threat must never leak. ALT interaction waits for Codex. Actual field schema and integration depend on Quest/Writ APIs.

OTHER FUTURE PRESENTATION: An authorable prominent operational status near the header is only a possible future option and not part of the accepted bottom slice; do not invent status data or move the present Energy status without a targeted later decision.

VALIDATION BOUNDARY: Last bundle reports build 1,918/0, dedicated server clean and 15 screenshots at GUI 1/2/4. QA harness and hooks removed, supplied pre-/post-task patch match, run side effects restored from backups. No commit/push and no item-family pass. Actual existing local worktree status must be rechecked before any later edit.


## 26M.7 Cross-system road map, assets, and workflow decisions from this chat

NEAR-TERM ORDER: After Tooltip V2 universal presentation, run item-family coverage audit/integration; then continue Block Breaking V2 finalization, Notification API V2 and Daily Quest V1; optional Notice Board V1, Codex V1, then Fluid API V1/Thirst V1 (Tank as first concrete Fluid API vertical slice). Exact sequencing can be adjusted by dependency and current repository truth; do not treat parked systems as implemented.

CODE VS DESIGN: Claude Code implements through narrow task prompts and review ZIPs; design decisions here are not repository changes. Keep unrelated working-tree files, saved worlds, logs/settings and snapshots untouched. Require task-only diff, pre-task snapshots, tests and real screenshots. Scratch game/server environments preferred. Tests and screenshots are not equivalent to the later item-family audit.

ASSET WORKFLOW: Existing Totality image-reference guide governs Minecraft cuboid/box-based Blockbench work; no mesh implementation assets, use minimal practical cuboids and clean pixel textures. Writ design art is a separate future asset deliverable; the discussion and screenshot concepts are not proof that the final eight rank PNGs have been produced.

UI BOUNDARY: A later Totality Core/UI pass chooses common typography, panels and broad visual language for Skills/Character/Tooltip/Phone. Do not independently restyle every screen or reintroduce old D&D-only assumptions while preserving already validated semantic contracts.


## 26M.8 Writ ranks, actual item artwork, and absolute difficulty - new canonical direction

RANK TAXONOMY: Writ rank describes the assessed absolute danger/complexity/importance of the contract, not player-relative difficulty or item rarity. Rank is independent of objective category. A level-100 character may breeze through E work without changing its rank. Ranking may account for environment, consequence and complexity, not only enemy level or quantity.

CURRENT SET: Full conceptual rank set F, E, D, C, B, A, S, Z; F-B constitute Normal generation and A/S/Z Special. Seven requested final texture variants are F/E/D/C/B/A/S plus unranked. Z remains an eligible Special rank, but its definitive wax-seal color and artwork are OPEN: implementation must explicitly handle Z with legible, intentionally authored presentation before exposing a Z posting; do not invent a canonical grey seal or quietly make Z player-level-locked because its art is unfinished.

ART / UNIVERSAL SEAL: One identical 64x64 transparent pixel-art parchment silhouette across variants: same decorative details, ribbon, text position, dimensions, seal placement and wax-seal geometry, border and highlights. ONLY wax color and central rank letter change. Unranked parchment has no seal. Use one shared parchment and seal source-layer template to avoid visual drift; legibility at actual 16 px inventory size matters.

SEAL PALETTE: F Common #888888; E Uncommon #55AA55; D Rare #5588FF; C Epic #AA55FF; B Legendary #FFAA00; A Mythical #FF5555; S Ancient #3ADBC4. Rarity colors are reused for visual rank coding only: contract rank != the Writ item rarity and neither determines the other mechanically. Z color and graphic are unresolved.

NO GENERIC LEVEL GATE: A player at level 10 who discovers an A/S/Z Writ may take it. Rank distribution must not rubber-band to nearby player level and boards are not beginner/level-cap zones. Authored contract requirements are legitimate (eligibility, faction/skill, specific provenance or world state) ONLY when meaningful to that job, not automatically by rank.

OBJECTIVE SEMANTICS: A soldier wants an iron sword with Sharpness V: delivering an existing legitimate qualifying sword can fulfill the Writ, even if the player could not personally forge/enchant it. Creating, buying or finding a qualifying item are alternative routes unless the authored contract specifically demands self-manufacture. Actual item predicates/quality/provenance are checked by the owning Quest/item systems, not via an implicit skill gate.


## 26M.9 Revised daily generation, independent capacities, and retention - supersedes old board defaults

GENERATION: Each Minecraft day a configurable number of Writ generation attempts occurs. Each attempt first rolls Normal vs rare Special, then chooses a rank by a separate weighted roll within F-B or A/S/Z. No numeric weights, global number of new postings or hard capacities were settled. BoardProfile/context/issuer select sensible authored content without player-level rubber-banding.

CAPACITY: Normal postings and Special postings have INDEPENDENT configurable capacities while sharing one visual Notice Board. Urgent Notices have their existing separate event-driven reserved capacity. A Special result consumes a daily generation attempt, NOT a Normal storage slot. This supersedes the old interpretation of one undifferentiated five-normal-plus-urgent physical layout; do not quietly hardcode two or three Special slots.

NORMAL RETENTION: F-B rotate on normal daily replenishment, subject to explicit authored/world-incident override. Posting expiry is separate from an accepted quest deadline. Claimed/abandoned/failed normal postings do not force instant rerolls; ordinary restocking follows the configured daily process.

SPECIAL RETENTION: A/S/Z outlast ordinary daily refreshes; an ordinary new Writ cannot evict them. When Special capacity is full and a new eligible Special needs space, remove the oldest eligible Special posting first (except any explicit authored incident protection/expiry rule). Merely reaching capacity does not by itself delete a page; claim can remove it earlier.

URGENT: Event-driven reserved Urgent Notice stays separate from rank category. A Special Writ is NOT inherently an Urgent Notice, and an E-rank Gate emergency may be urgent. Existing severity cooldown/event rules remain compatible unless later explicitly amended.

LIFETIME OVERRIDES: Writs bound to live incidents can persist beyond their ordinary rank-group timing. Example E-rank Gate-clearing job can remain about one Minecraft week while its Gate threatens breach; the Gate incident deadline owns the actual countdown. The illustration is not a universal seven-day law.

OPEN / TUNING: Daily attempt count, Normal/Special odds, weights within each rank family, Normal/Special/urgent capacities, board-profile content mixes and Special replacement exemptions are not numerically finalized; tune after GUI/assets/content and real tests. Do not promote the v3.5 five-plus-one mockup or assistant-proposed two Special slots to canon.


## 26M.10 Physical wooden Notice Board screen - V1 chosen layout

MAIN VIEW: Right-click opens a PHYSICAL wooden-framed pinboard, not the v3.5 split list/detail journal. Actual 64x64 rank-marked Writ ITEM textures are pinned directly to the wood (nearest-neighbor scaling); no substitute icon/card redesign. Organic but restrained variation of pin position/paper placement (semi-structured), with pixel-safe rotation or primarily position variation, avoids blurry text.

READ FLOW: Click a posted Writ to open a paper/parchment details popup over the dimmed board; read title, rank, issuer, objectives, rewards, availability, distinct quest/world deadline, claim/turn-in rules and any applicable conditions. Reading alone never accepts; Take Writ explicitly starts the acceptance transaction; Close/Back leaves world and quest unchanged. A long popup may scroll independently.

HORIZONTAL ZONES: Top FIXED Urgent Notice region, middle independent HORIZONTAL scrolling Special row, bottom independent VERTICAL scrolling Normal grid. Mouse wheel acts on the hovered scroll region; opening/closing the popup preserves both positions. The board is one continuous wood surface, not three separate modern inventory windows.

PRESENTATION STATE: Same server-authoritative posting state drives the GUI and physical block page display. Claimed exclusive pages disappear on successful claim; empty positions show wood/pins. Distinct urgent presentation may use trim/seal/signage. There is no requirement that all capacities fit onscreen at once because each of the two main sections scrolls independently.

CAPACITY BEFORE PIXELS: Do not lock Special capacity until the physical screen has been tested. Earlier assistant mockups with a Z grey badge, three Special cards or various slot counts were illustrative, not assets or accepted numbers.


## 26M.11 Physical Writ lifecycle, ownership, abandonment and multiplayer

ACCEPTANCE: Player first reads and explicitly takes a physical custom Quest Writ. The Quest instance remains authoritative; Writ references instance/posting/originating board and owner. Atomic claim/eligibility/inventory check plus grant/board update prevents orphan quests or duplicate Writs. Player-owned by default; board listings shared. Retain EXCLUSIVE_PLAYER, EXCLUSIVE_PARTY, MULTIPLE_PLAYERS, WORLD_SHARED posting claim policies.

ACTIVE INVENTORY: Active Writ is owner-bound and death-persistent; may move within own inventory, not be normally thrown away, passed to another player or placed in ordinary chests/barrels/hoppers/bundles/shulkers/item frames. Ender Chest is explicitly DISALLOWED in V1; later consider player-bound Writ/Document Pouch and optional Ender Chest support. Do not equate this administrative restriction with a magical Soulbound enchantment.

DROP CONFIRMATION: Any normal Q/Ctrl+Q/inventory-drop attempt on active Writ prompts Abandon Contract? Cancel changes nothing. Confirm atomically abandons the Quest and CONSUMES/DESTROYS the Writ; no dropped voided item spawns. Failure and abandonment remain distinct. Natural item loss or a bug does not silently abandon authoritative quest; reissue after validation is possible. Unaccepted/finished documents follow authored lifecycle, not an active-abandon command.

MEMENTO: Distinct nonfunctional abandoned/completed contract keepsakes were only a possible LATER authored idea; do not make every voided Writ pickupable or implement mementos in V1.

TRANSFER / PARTY FUTURE: Accepted contract transfer policy already reserved: NON_TRANSFERABLE, TRANSFERABLE, TRANSFERABLE_WITH_ISSUER_APPROVAL, PARTY_TRANSFER_ONLY. Defers until Quest/Writ/target/reward/party/progress/transfer history can change atomically. Party/world posting claim policy does NOT itself make an accepted Writ transferable. Full party-aware acceptance can follow Party API.


## 26M.12 Contract identity, amendments, issuer boundaries and completion routes

IDENTITIES: Commissioner/issuer, the board delivering/publishing the posting, delivery source, turn-in destination, authoritative quest instance and (where applicable) world incident are separate. A.E.G.I.S. may publish/administer its own Writ remotely; civilians/guards/guilds can issue contracts through a board without the board fictionally owning their work.

ASSESSMENT: Assessed/advertised rank can be wrong (Red Gate-type surprise). Underlying actual incident threat is independent of player level; discovery updates knowledge/quest information without silently altering the stamped physical contract or leaking hidden truth through normal/SHIFT/CTRL or item texture. Original assessed rank remains recorded.

OFFICIAL AMENDMENT: For local physical Writs, new seal/rank/terms change only upon official issuer/Registrar amendment/reissue and the player's review/acceptance. A.E.G.I.S. can authorize and deliver a remote automatic document/texture change, but revised contract terms still require player agreement. Recorded originally-issued rank remains available as history (e.g. SHIFT when appropriate/known).

REVISED TERMS: An incorrect rank or materially harder work can lead to a revised contract including reward, objectives or supplies. Do not force a higher-risk contract silently. Future negotiation may happen BEFORE acceptance, DURING work after discoveries, and AFTER completion where justified; no guaranteed raise merely by asking. Issuer authorization and funding matter.

TURN-IN: Each contract authors valid methods: Quest GUI, RETURN_TO_BOARD, issuer, recipient, NPC, any valid authority, AUTOMATIC or CUSTOM, including staged delivery and progress/payment idempotency. V1 operates through automated board/Quest routes compatible with existing APIs; NPC turn-in and social negotiation wait for owning systems.

REGISTERED EVIDENCE: Server-side Quest progress, specific item predicates and stable target/incident identity must prevent reward duplication; Writ copies/reissues cannot mint a second payment. Existing identified targets use stable target handles rather than only a transient entity UUID. Not every routine kill-count requires a proof item.


## 26M.13 Consequential Writs and Gate incident integration - contract-side design now, live effects later

WORLD-LINKED MODEL: Some postings describe a persistent world incident rather than generating the incident only after player acceptance. For an authored E-rank Gate-clearing Writ, the actual Gate spawns when the posting is created and both refer to the SAME persistent incident ID. Someone can encounter and resolve the Gate without ever reading or accepting the Writ.

OWNERSHIP: Gate API owns Gate spawn/state/breach deadline; World Event owns/executes Dungeon Break lifecycle, invasion and aftermath; Quest API owns accepted or retrospective objective/reward authority; Notice Board owns advertisement, visibility, claim and publication. Board disappearance, Writ abandonment or player logout must not silently despawn a live incident.

EXAMPLE FLOW: A seven-day E Gate remains public longer than ordinary E; player/NPC may clear it. If nobody succeeds and the Gate breaches, the original clear-Gate posting ends/fails by its authored rules, monsters may enter the world through the Dungeon Break, and a NEW containment commission appears in the separate Urgent slot with revised objectives and compensation. It is not a magical transform of the old signed Writ.

NPC ACTIVITY - FUTURE: Important persistent mercenary NPCs may genuinely accept/attempt contracts; lightweight local simulation can represent additional adventurers. An S posting near a powerful mercenary might be taken the next day, while a remote village retains its S longer. Acceptance != completion; NPCs may succeed, fail, retreat, vanish, request help. Player and NPC claims of exclusive listings are shared/atomic; only actor capability/interest/availability and incident context, not level-based player protection, drive their choices.

V1 BOUNDARY: Preserve authored incident reference, retention/expiry, consequence/resolution metadata and ability to interoperate with external Quest/world event later. Do NOT claim Gate spawning, breach AI, NPC contracts, actual invasions, incident-based retrospective payment or full consequences are implemented by Notice Board V1 before their owning APIs and trustworthy incident evidence exist.


## 26M.14 Retrospective completion, proofs and late claims - future integration

FUTURE RETROSPECTIVE WORLD CLAIM: A player who independently clears a posted Gate may later report through an appropriately connected Notice Board or Registrar and collect eligible compensation without having accepted the Writ first. This requires persistent incident identity, trustworthy evidence of contribution/resolution and an idempotent claim/payment record. It is NOT a promised Notice Board V1 capability while Gate/incident integration is unavailable. Existing authored Quest API retroactive predicates for ordinary objectives remain valid; some tasks still require acceptance or a specific post-activation act.

CLAIM WINDOW: For future retrospective claims, an applicable contract may author a post-completion claim period, including an indefinite period. An ordinary V1 contract does not automatically gain retrospective payment. Posting visibility, event expiry, accepted quest deadline and retrospective reward-claim period are separate. An expired board page does not by itself cancel an already accepted quest.

RETIRED POSTING ACCESS: The future retrospective claim route must not require the old page to remain in a visible slot. Preserve authoritative incident/posting history, evidence of participation and paid-once state for the authored claim period; an appropriately connected board or authority can consult it after refresh or claim. Exact archived-incident presentation and reporting/claim UI remain OPEN. Do not implement or promise this behavior in V1 without the owning incident and payment systems.

DELAYED VERIFICATION: Over time, evidence may be lost, witnesses may depart and competing claims may appear. Verification difficulty should reflect actual evidence and issuer records, not a universal +1/day tax. A.E.G.I.S. official recorded identity can remain straightforward; a village Registrar may demand witness, item proof or investigation.

AFTER WINDOW: Individual issuer policy may enforce strict denial, a late appeal, discretionary compensation or other authored result. Future truthful Persuasion can secure review, Deception can attempt fraudulent claims with consequences; strong official proof may obviate a social roll. Details belong to Dialogue, Quest, Relationship/Crime and Economy rather than board-local invented rolls.

FUTURE / NOT V1 NPC FEATURE: Human Registrar evidence hearings, negotiation and late appeals are future features. So is automated Gate-incident retrospective compensation until the relevant owning systems and verified participation records exist. V1 may use already-supported, authored Quest retroactivity for ordinary objective checks, but cannot invent evidence or promise late reward claims.


## 26M.15 Registrar, A.E.G.I.S., Economy and implementation scope

V1 AUTOMATED: A functional wooden board displays authored/generative daily postings, rank-marked Writs, shared claims, selected paper popup, explicit Take Writ, Quest progress verification, compatible automatic/board turn-in, rewards, abandonment, persistence and refreshed/urgent posting slots. Use existing Quest API; do not require a Mob API or a human to make V1 work.

REGISTRAR LATER: A real optional NPC, not one required beside every small board. May register/reissue/complete work, provide information, receive evidence and reports, amend misclassified Writs, negotiate pay/supplies, handle disputes and authorize transfers where permitted. NPC has limited knowledge/authority/funds; should not know hidden actual ranks magically. Existing Dialogue API owns interactions and social checks.

A.E.G.I.S. AUTOMATED CASE: A.E.G.I.S. can manage/validate/reissue through remote infrastructure, including accepted seal-texture updates on an official amendment; ordinary civil issuers/Registrar may require a physical interaction or approval. No global assumption that every rural issuer owns a Phone or instant communications.

DEPENDENCIES FOR FULL REGISTRAR: Mob API/NPC foundations; Dialogue API polish; Economy API revisit for issuer funds, credit authority, advance/staged/bonus/revised payment, ledgers, compensation authorization, no double payouts and price/value correctness. World incident/Gate and possible Party/Relationship/Crime work are separately owned. Do not merge these APIs into Notice Board V1.

TASK ORDER / STATUS: Notice Board V1 design is parked while Tooltip V2 item-family coverage and nearer API slices proceed. This chapter is a design update, not proof a Notice Board block, Registrar NPC, gate-invasion subsystem, or Writ item has been implemented in the repository. Gate-linked retrospective claims and their evidence/payment ledger are future cross-API work, not a V1 automatic-board promise.


## 26M.16 Superseded-rule reconciliation and open decisions

SUPERSEDED / GENERATION: Dedicated Notice Board PDF §11 player-level-based generation/eligibility and v3.5 E-Z old rank wording yield to F-B Normal vs A/S/Z Special and absolute difficulty; authored job-specific requirements remain. Old page-gating at levels is NOT a universal rank rule.

SUPERSEDED / SLOTS: Old five normal slots plus Urgent as the only visual layout is replaced by independent configurable Normal/Special capacities plus reserved Urgent, all displayed on a unified wooden board. Five normal remains a historical initial target only, not a newly accepted final default.

SUPERSEDED / LIFETIME: Fixed A-Z three Minecraft days yields to persistent Special postings with oldest-eligible-Special replacement; authored consequential posting overrides may last according to incident and its deadline. Normal F-B daily rotation remains the baseline. Posting visibility and accepted quest deadline were always distinct.

SUPERSEDED / GUI: v3.5 split list on left/details on right becomes physical wood pinboard using actual item textures, parchment popup, fixed Urgent at top, horizontal independently scrolling Special row and vertical independently scrolling Normal grid.

OPEN / VISUAL: Full Z color/seal art, actual finalized Writ texture-sheet delivery, exact screen pixel coordinates, chosen wood/pin/popup original assets, item-sized rendering scale and final Writ tooltip profile await future asset/UI work. No unaccepted capacity/probability/amount should be guessed.

OPEN / CONTENT AND OWNER APIS: BoardProfile pools, random weights, board capacities, special replacement exceptions, specific consequential posting templates, future NPC simulation/individual behavior, proof and fraud semantics, archived-incident evidence and claim-once records, claims/appeals rules, final Economy ledger and Dialog/social outcomes require separate design/implementation slices.

OPEN / URGENT COLLISION: A Dungeon Break calls for a NEW Urgent containment contract, but the reserved Urgent slot may already be occupied or on cooldown. Priority, queueing and publication timing are not settled; do not assume silently deleting the existing notice or cancelling the world event. The Gate/World Event outcome must remain authoritative even if the board cannot display its notice immediately.

PRESERVED: boardId/BoardProfile, actual shared world postings, atomic server acceptance, author-controlled content pools and claim policy, separate issuer/turn-in identities, authoritative Quest instances and stable heavyweight targets, no immediate reroll exploits and reserved Urgent lifecycle remain valid wherever not explicitly superseded.


## 26M.17 Source-bundle inventory and evidence provenance

Relevant review artifacts supplied with this conversation (file names are evidence identifiers, not a promise that every ZIP has been committed into the repository): tooltip-v2-presentation-slice-review.zip; tooltip-v2-presentation-followup-review.zip; tooltip-v2-header-refinement-review.zip; tooltip-v2-body-framework-review.zip; tooltip-v2-bottom-presentation-review.zip; tooltip-v2-final-corrections-review.zip. The dedicated TOTALITY_NOTICE_BOARD_API_DESIGN.pdf is the older detailed Notice Board source. Newer explicit conversation decisions recorded in this section supersede its conflicting examples/defaults.

Toolbox note: do not ship Mojang texture copies used as provisional icon placeholders without original replacements. Real-client screenshots were made with temporary showcase scaffolding; latest bundle reports cleanup/removal and restored run/ side effects. These reports support the specific stated checkpoint, not all possible current repository behavior.


## 26M.18 Current verification and unresolved-issue ledger

CLOSED / PRESENTATION: Header, semantic body and bottom presentation accepted; 1,918/0 latest reported fresh suite. Reported dedicated server pass and live GUI-scale 1/2/4 images. GUI 1 pixel-row artifact addressed; scrolling still works without scrollbars. No new presentation revision requested after final-correction acceptance.

OPEN / CONTENT: Tooltip V2 per-family audit first; compare vanilla and Totality coverage; existing Skyrim ingredients, food, mining, block Properties, equipment, Energy, Writs and all actual current families against real registered contributors, then distinguish data-authority dependencies. Avoid pretending all family integrations were already completed.

OPEN / MULTIPLAYER PRICE: ItemValueRegistry base value does not reach dedicated-server clients, so Price is intentionally absent there pending Economy/sync integration; do not render misinformation.

OPEN / ASSETS: Model missing-texture warnings; replace provisional copied icons before distribution; final Writ variants and Z art unresolved; Gate-linked retrospective claims await authoritative incident evidence and payment integration. CTRL long registry-id wraps mid-word; technical display policy provisional. ALT not enabled until Codex; Fluid/Diet/Architecture semantic rows require source APIs.

OPEN / EARLIER WORK: Block Breaking V2 stale-target cadence and complete authored vanilla block/material baseline, verifier-world opt-in isolation and other §26L status remain separate. v3.6 does not infer their closure from Tooltip completion.


## 26M.19 v3.6 handoff: immediate next action and parked work

CURRENT HANDOFF: Start a fresh Totality chat from this v3.6 Master, reading Section 26M FIRST for accepted Tooltip V2 presentation and updated Notice Board decisions; then Section 26L for current Block Breaking V2/profile/verifier/toolchain state, 26K for Daily/Reward/Gate/Voice/RPG baseline, 26J for Class/Resource/Food/MobRank/Soul Gems, and owning deep documents where necessary. Past dates and status snapshots elsewhere are historical unless explicitly preserved.

NEXT TASK (READ ONLY): Tooltip V2 item-family coverage audit. Inspect actual repo and relevant docs; enumerate existing vanilla/Totality families and their contributors, actual missing/partial integrations and owner-API dependencies. Review findings before requesting implementation; the audit has NOT started as of this v3.6 update.

PARKED: Notice Board V1 design is conceptually developed, including physical Writ textures and GUI, rank generation, capacity, lifecycle and future consequential/Registrar extensions. No design decision about unselected rank odds/capacity has been made; later resume one question at a time and avoid re-asking already answered master questions.

REPO SAFETY: The last accepted Tooltip correction review states no commit/push. Never discard unrelated work, world files, run state, settings or logs; use narrow task-only patches and verified scratch worlds when future implementation resumes.

