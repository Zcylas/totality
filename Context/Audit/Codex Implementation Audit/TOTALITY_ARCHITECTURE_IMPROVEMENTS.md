# Totality Architecture Improvements

Audit date: 2026-07-15. Recommendations are evidence-based and do not authorize implementation.

## 1. Executive summary

The recommended sequence is: migrate to 26.2 and establish regression tests; generalize the already-shared resource value store; implement source-aware entitlements; harden component lifecycle and packets; then complete spell/class/equipment/economy foundations. Do not redesign mature vertical slices without an acceptance test.

## 2. Highest-priority improvements

### A1. Establish component schema versions and migration tests

- **Category/Urgency/Difficulty:** Persistence/synchronization improvement; Required before dependent API; Architectural.
- **Affected/evidence:** all player systems; `api/core/component/*`, `mixin/MixinServerPlayer.java`.
- **Current behavior/problem:** components serialize directly into player NBT; no explicit repository-wide schema migration mechanism was located. Broad API replacement risks unreadable or silently defaulted saves.
- **Suggested improvement/benefit:** version the component envelope or each persistent component and test old fixtures; enables safe 26.2/resource/UAE migrations.
- **Risk/dependencies/migration:** high migration sensitivity; define backward-compatibility policy first.
- **Confidence:** high.

### A2. Generalize resource definitions and behavior around the existing shared store

- **Category/Urgency/Difficulty:** Architectural blocker; Required before dependent API; Architectural.
- **Affected/evidence:** generic resources, mana, stamina, rest; `api/rpg/resources/*`, `api/rpg/mana/*`, `api/rpg/stamina/*`, dedicated ticks/packets.
- **Current behavior/problem:** `PlayerResourceComponent` already owns mana/stamina values and managers delegate to it, but fixed fields, bespoke calculations, client managers and packet/HUD paths are not yet registry-driven.
- **Suggested improvement/benefit:** execute the remaining canonical Generic Player Resource phases while preserving the completed storage consolidation; use compatibility adapters only where needed.
- **Risk/dependencies/migration:** high; preserve saved values and HUD behavior; do after 26.2 baseline.
- **Confidence:** high.

### A3. Implement source-aware entitlement ownership

- **Category/Urgency/Difficulty:** Architectural blocker; Required before dependent API; Architectural.
- **Affected/evidence:** abilities, spells, classes, origins, equipment; distributed grant APIs and canonical U/A/E design.
- **Current behavior/problem:** grants cannot uniformly answer who granted a feature, when it is visible/available, or how removal revokes only the correct source.
- **Suggested improvement/benefit:** implement the adopted U/A/E API and migrate one domain at a time; prevents duplicate grants and destructive revocation.
- **Risk/dependencies/migration:** preserve existing unlocks; requires human-approved mapping of legacy grants.
- **Confidence:** high.

## 3. Improvements required before upcoming foundational APIs

### A4. Define and test death/respawn ownership across inventories

- **Category/Urgency/Difficulty:** Persistence/synchronization improvement; Required before dependent API; Large.
- **Affected/evidence:** custom `EquipmentComponents`, accessory menu, player-component respawn strategies, vanilla inventory and Credits item.
- **Current/problem:** mechanisms exist separately; no single proven matrix covers death drops, KeepInventory, Soulbound, attuned items and starter gear.
- **Improvement/benefit:** specify an item-by-location policy and add automated/manual death tests before Soulbound/outfitting.
- **Risk/dependencies/migration:** duplication/loss risk; depends on final design intent.
- **Confidence:** medium-high.

### A5. Make spell authorization server-owned and source-aware

- **Category/Urgency/Difficulty:** Architectural blocker; Required before dependent API; Large.
- **Affected/evidence:** `Spell`, `SpellRegistry`, selection/cast handlers, class/ancestry/ability systems.
- **Current/problem:** casting infrastructure is substantial but access/known/prepared ownership remains incomplete/temporary.
- **Improvement/benefit:** central server authorization queried by every cast; negative tests for unknown, unavailable, unprepared, wrong-source and removed grants.
- **Risk/dependencies/migration:** can invalidate development spell access; depends on U/A/E and class architecture.
- **Confidence:** high.

### A6. Separate concrete tank behavior from the planned Fluid API contract

- **Category/Urgency/Difficulty:** Extensibility improvement; Required before dependent API; Medium.
- **Affected/evidence:** `api/industrial/fluid/*`, `blockentity/fluid/*`, Fabric `FluidStorage.SIDED` lookup in `Totality.java`.
- **Current/problem:** a useful tank vertical slice exists, but it should not accidentally become the universal contract for plumbing, processing, vehicles and environmental fluids.
- **Improvement/benefit:** document the current storage/transfer boundary and design only missing cross-system contracts.
- **Risk/dependencies/migration:** avoid needless rewrite of working Fabric Transfer integration.
- **Confidence:** high.

## 4. Confirmed defects

### A7. Complete concentration cleanup callbacks

- **Category/Urgency/Difficulty:** Confirmed defect; Recommended soon; Medium.
- **Affected/evidence:** `api/magic/spell/ConcentrationComponent.java` TODO states active spell cleanup is not notified.
- **Current/problem:** ending concentration can leave spell-owned effects without an owner cleanup callback.
- **Improvement/benefit:** require idempotent cleanup through the active spell/effect handle and test damage, replacement, death and logout.
- **Risk/dependencies/migration:** cleanup ordering may change current spell behavior.
- **Confidence:** high.

## 5. Architecture and ownership issues

### A8. Replace first-pass quest-specific branching with objective handlers when a second distinct quest needs it

- **Category/Urgency/Difficulty:** Extensibility improvement; Important but not blocking; Medium.
- **Evidence/current/problem:** `QuestManager.java` explicitly hardcodes first-pass objectives; this will scale poorly and mixes templates with quest identity branching.
- **Improvement/benefit:** introduce registered objective evaluators only when concrete new objective types arrive.
- **Risk/dependencies/migration:** premature abstraction risk; preserve existing quest saves.
- **Confidence:** high.

### A9. Give merchant runtime state durable identity

- **Category/Urgency/Difficulty:** Architectural risk; Recommended soon; Large.
- **Evidence/current/problem:** `MerchantRuntimeRegistry.java` calls itself a temporary stand-in; per-server memory does not prove per-entity/restart identity.
- **Improvement/benefit:** attach durable state to merchant identity/saved data with explicit unload/reload semantics.
- **Risk/dependencies/migration:** existing live stock must be migrated or reset intentionally.
- **Confidence:** high.

## 6. Persistence and synchronization risks

### A10. Add a lifecycle conformance suite for every component

- **Category/Urgency/Difficulty:** Testing/verification improvement; Recommended soon; Large.
- **Evidence:** central component registry and varied `RespawnStrategy`/sync implementations.
- **Improvement:** table-driven tests for new player, save/load, death, dimension change, reconnect, invalid/missing NBT and content removal.
- **Benefit/risk:** catches silent loss/desync; test harness work only, low production risk.
- **Dependencies/migration/confidence:** pair with A1; high.

## 7. Networking and server-authority risks

### A11. Centralize packet context validation

- **Category/Urgency/Difficulty:** Architectural risk; Recommended soon; Medium.
- **Affected/evidence:** BE side-mode packets and grimoire update packets in `TotalityServerPacketHandlers.java`; many feature handlers.
- **Current/problem:** validation is handler-specific; some paths visibly check type but code inspection did not prove distance/menu ownership or semantic bounds.
- **Improvement/benefit:** reusable validators for held slot, open menu, position reach/chunk, registry ID, count/range and entitlement; add hostile-packet tests.
- **Risk/dependencies/migration:** overly strict checks can break legitimate UI; inventory normal flows first.
- **Confidence:** medium-high.

## 8. Performance and scalability risks

### A12. Profile global player/passive tick loops before expanding content

- **Category/Urgency/Difficulty:** Performance improvement; Future consideration; Medium.
- **Evidence/current:** `Totality.registerPassiveTicker()` iterates every server player and every unlocked ability each tick; mana, stamina, condition and rest ticks also run.
- **Problem:** negligible at current scale may become material with many players/unlocks.
- **Improvement:** measure first; if demonstrated, index active/passive abilities or schedule lower-frequency work.
- **Risk:** premature caching creates invalidation bugs; no change without profiling.
- **Confidence:** high that loop exists, low that it is currently costly.

## 9. Extensibility and API-design improvements

Complete class-specific feature architecture before adding more classes; expose ability-check resolution as the shared boundary rather than duplicating rolls; keep rarity presentation separate from mechanical quality per `TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md`; and introduce magical-property scoring only with Enchantment/Smithing/Dispel use cases.

## 10. Data-driven-content opportunities

Expand data driving where a loader already proves value: item values, shops, assortments, dialogue, quests, mob stats and ritual recipes. Do not data-drive class logic or spell execution merely for uniformity; extract stable schemas only after two concrete content variants.

## 11. Testing and verification improvements

Convert critical dev verifications into deterministic tests where possible; add dedicated-server startup, packet-negative, save-fixture, multiplayer rest/trade, merchant restart and fresh-world worldgen smoke tests. Keep renderer checks client-only.

## 12. Version-migration and maintenance risks

### A13. Create a 26.2 migration gate before foundation changes

- **Category/Urgency/Difficulty:** Testing/verification improvement; Critical; Large.
- **Evidence:** declared 26.1.2 versions; extensive mixins, worldgen internals, render internals, payload codecs, Transfer API and Loom snapshot.
- **Current/problem:** combining mapping migration with resource/UAE/spell rewrites obscures regressions.
- **Improvement/benefit:** update versions in a separate future task, compile, dedicated/client start, load old save copy, exercise mixin/worldgen/UI matrix, then freeze baseline.
- **Risk/dependencies/migration:** migration itself is high risk; no dependent API should land simultaneously.
- **Confidence:** high.

## 13. Low-risk cleanup

Resolve placeholder `fabric.mod.json` metadata and placeholder icons only in separate scoped tasks. Do not bulk-delete apparently unused assets/classes before runtime reachability testing.

## 14. Recommendations deliberately deferred

No speculative ECS rewrite, dependency-injection framework, universal codec abstraction, per-tick caching, or conversion of every registry to JSON is recommended. Architecture/building, electricity/plumbing/sewage, vehicles and environmental simulation need dedicated design and concrete use cases first.

## 15. Prioritized implementation sequence

1. 26.2 migration gate and regression matrix (A13).
2. Component schema/lifecycle tests (A1, A10).
3. Generic resource migration (A2).
4. U/A/E implementation and legacy grant migration (A3).
5. Server spell authorization and concentration cleanup (A5, A7).
6. Death/equipment/Soulbound policy (A4).
7. Merchant durable state and packet hardening (A9, A11).
8. Class/origin/background/outfitting completion.
9. Fluid-dependent production systems, then farming/cooking/alchemy/medicine/disease.
