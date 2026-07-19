# TOTALITY RESOURCE API — PHASE 2B: BREATH EXTERNAL ADAPTER — IMPLEMENTATION REPORT

**Date:** 2026-07-19
**Branch:** `feature/general-resource-api`
**Starting commit:** `0cd2b66` ("Add health and food resource adapters") — confirmed via `git log -1` and `git status` before any change; `git diff --cached` was empty and Phase 2A was already committed.
**Scope:** `src/main/java/zcylas/totality/**`, `src/test/java/zcylas/totality/**` only. `/Inspiration Mods` was excluded from every search performed in this phase.

---

## 1. Exact scope

Add `totality:breath` as the third `EXTERNAL_ADAPTER`-authority production resource, wrapping vanilla air supply (`Entity.getAirSupply()`/`setAirSupply(int)`/`getMaxAirSupply()`), following exactly the Health/Food precedent from Phase 2A: query-only, no Totality-owned storage, no generic packet, vanilla remains fully authoritative. No breathing gameplay change, no HUD change, no vacuum/smoke/choking/species-breathing mechanics. Temperature remains explicitly out of Resource API scope (unchanged standing decision, not revisited).

## 2. Starting state verification

- Branch: `feature/general-resource-api`.
- `HEAD`: `0cd2b66`, "Add health and food resource adapters" — the commit that landed Phase 2A (confirmed by prior-session's staged files matching this commit's content).
- `git diff --cached`: empty.
- Initial `git status --short` unrelated inventory (pre-existing, not touched by this phase): 21 modified generated JSON files under `src/main/generated/data/**` (worldgen noise settings, block loot tables, gear recipes), 4 untracked PNGs under `Context/Trading Test/`, untracked `src/main/generated/.cache/`, and (once created during this session's build/test runs) untracked `logs/`. All of these remain in the exact same state at the end of this phase — see §12.

## 3. Vanilla air supply audit (Minecraft 26.2 mapped source)

Extracted directly from `minecraft-merged-043a8b3edf-26.2-sources.jar` (`Entity.java`, `LivingEntity.java`, `Player.java`, `MobEffectUtil.java`), not from memory or older mappings.

| Question | Finding |
|---|---|
| Current-air getter/setter | `Entity.getAirSupply()` / `Entity.setAirSupply(int)`, backed by a `SynchedEntityData` field (`DATA_AIR_SUPPLY_ID`). |
| Max-air getter | `Entity.getMaxAirSupply()` — virtual, non-final, **not overridden for `Player`**; default implementation returns the constant `Entity.TOTAL_AIR_SUPPLY = 300`. |
| Vanilla baseline maximum | `300` (15 seconds at 20 ticks/second). |
| Can max differ by entity/state? | Yes in principle — `getMaxAirSupply()` is virtual and a future entity type could override it — but no vanilla or Totality code currently overrides it for `Player`. The adapter reads it live on every query rather than caching `300`, so this is future-proof without extra work. |
| Can current air go negative? | **Yes.** `LivingEntity.baseTick()` decrements air by 1/tick while submerged and unable to breathe, and does **not** clamp at zero — air continues down to `-20`. |
| How does negative air participate in drowning? | At `-20`, `shouldTakeDrowningDamage()` returns `true`, 2 drowning damage is dealt, and air is reset to exactly `0`. The `[-20, 0)` range is vanilla's own drowning-damage timer, not usable breath capacity. |
| How does air replenish? | `+4/tick` (capped at `getMaxAirSupply()`) whenever the entity is not submerged, or is submerged but `MobEffectUtil.shouldEffectsRefillAirsupply(...)` returns `true`. |
| Water Breathing / Conduit Power interaction | Both cause `shouldEffectsRefillAirsupply` to return `true`, overriding Breath of the Nautilus's own suppression logic; a `canBreatheUnderwater()`-tagged entity bypasses drowning entirely. None of this is touched by the adapter — it only reads the resulting `getAirSupply()`/`getMaxAirSupply()` values after vanilla has already applied all of this. |
| Synchronization | Native `SynchedEntityData`, automatically sent to tracking/owning clients exactly like vanilla health — no Totality packet exists or was added. |
| Persistence | Vanilla entity NBT, a short `"Air"` field — entirely vanilla, untouched by Totality. |
| Respawn / logout / dimension travel | Respawn creates a fresh entity (air defaults to max via the constructor); dimension travel and logout/rejoin use vanilla's own entity-data carryover. None of this is Totality-owned. |
| Which vanilla HUD element renders bubbles? | `VanillaHudElements.AIR_BAR`. |
| Does Totality currently touch it? | No — confirmed via full-source grep: `TotalityHudRenderer` replaces `HEALTH_BAR`/`ARMOR_BAR`/`FOOD_BAR` with no-ops but never references `AIR_BAR`. |
| Does any Totality system modify air supply? | No — confirmed via full-source grep for `getAirSupply`/`setAirSupply`/`AIR_SUPPLY`: zero matches outside this phase's own new adapter. |

This confirms and supersedes-with-citations the earlier read-only "Oxygen-to-Breath Audit Addendum" in the readiness document (which reached the same conclusions without direct source citations); no finding here contradicts that addendum.

## 4. Files created

| File | Purpose |
|---|---|
| `src/main/java/zcylas/totality/api/rpg/resources/external/BreathResourceAdapter.java` | The Breath external adapter itself: `id()`, `snapshot(Player, PlayerResourceDefinition)`, `supportedOperations()` (`QUERY` only), `clientMirrorMode()` (`NATIVE_SYNCHRONIZATION`), and the package-visible pure `normalize(Identifier, int rawCurrent, int rawMaximum, long unitScale)` helper. |
| `src/test/java/zcylas/totality/api/rpg/resources/external/BreathResourceAdapterTest.java` | 15 tests against `normalize(...)` directly — see §9. |

## 5. Files modified

| File | Change |
|---|---|
| `PlayerResourceIds.java` | Added `BREATH`/`BREATH_ADAPTER` (both `totality:breath`). |
| `ResourceQueryFailureReason.java` | Added `MALFORMED_OWNER_STATE` — a new, distinct failure reason for "the adapter correctly declined" (empty `Optional`), separated from the pre-existing `CORRUPT_ADAPTER_SNAPSHOT` ("the adapter tried and got it wrong," a malformed non-empty snapshot or a misbehaving `null` `Optional` reference). |
| `ExternalPlayerResourceAdapter.java` | **Breaking interface change**: `snapshot(Player, PlayerResourceDefinition)` now returns `Optional<ResourceSnapshot>` instead of a raw `ResourceSnapshot`. This is the "smallest coherent extension" the task asked for if the existing snapshot interface could not express adapter failure directly — it could not: there was no way for `HealthResourceAdapter`/`FoodResourceAdapter`'s always-succeeds contract to also express Breath's "owner state is unrepresentable" case without either inventing a sentinel value (rejected — the task explicitly forbids null-as-control-flow, and a magic sentinel is the same anti-pattern with extra steps) or this Optional-returning contract change. |
| `PlayerResourceService.java` | `queryExternal(...)` now distinguishes three cases: a literal `null` `Optional` reference (misbehaving adapter → `CORRUPT_ADAPTER_SNAPSHOT`), `Optional.empty()` (adapter correctly declined → `MALFORMED_OWNER_STATE`), and `Optional.of(snapshot)` (routed into the pre-existing `validateExternalSnapshot` structural checks, unchanged). |
| `HealthResourceAdapter.java` / `FoodResourceAdapter.java` | Updated to the new `Optional<ResourceSnapshot>` return type; both always return `Optional.of(...)` — neither adapter has an owner-state case that can fail (vanilla `Player.getHealth()`/`getFoodData()` cannot themselves be malformed the way a hypothetical non-positive `getMaxAirSupply()` could). No behavior change. |
| `ProductionResourceDefinitions.java` | Registers `BreathResourceAdapter.INSTANCE` (after Health/Food, before `freeze()`) and the `totality:breath` definition (after Food, before the registry's own `freeze(...)` call) — see §7 for the exact fields. Deliberately registers **no** formatter for Breath (see §8). |
| 8 test files | Updated for the interface change and/or extended with Breath-specific coverage — see §9. |

No other production file was touched. No HUD file was touched (confirmed no test required it — see §8).

## 6. Adapter architecture

```java
public Optional<ResourceSnapshot> snapshot(Player player, PlayerResourceDefinition definition) {
    return normalize(definition.id(), player.getAirSupply(), player.getMaxAirSupply(), definition.unitScale());
}

static Optional<ResourceSnapshot> normalize(Identifier resourceId, int rawCurrent, int rawMaximum, long unitScale) {
    if (rawMaximum <= 0) {
        return Optional.empty();
    }
    long clampedCurrent = Math.max(0, Math.min(rawCurrent, rawMaximum));
    return Optional.of(new ResourceSnapshot(resourceId, clampedCurrent, rawMaximum, unitScale));
}
```

**Normalization policy:** generic Breath exposes the *usable reserve*, not vanilla's raw drowning-cadence counter. `current = clamp(rawAirSupply, 0, liveMaximum)`. A negative raw value (mid-drown, per §3) normalizes to `0` — it never becomes a negative generic Breath value, since the negative range is vanilla owner-specific metadata that the generic resource model has no concept of. The maximum used is **always the live, freshly-read `player.getMaxAirSupply()`**, never the definition's `authoredBaseMaximum()` — matching Health's existing precedent of never trusting the authored value for the live query path.

**Malformed-owner-state path:** if `rawMaximum <= 0` — never actually produced by `Player` today (its `getMaxAirSupply()` is unoverridden and always returns `300`), but the method is virtual and a future entity type could theoretically override it into something degenerate — `normalize` returns `Optional.empty()`, and `PlayerResourceService` turns that into a structured `ResourceQueryFailureReason.MALFORMED_OWNER_STATE` failure rather than fabricating a `0/0` or `0/300` snapshot.

**Never mutates:** `BreathResourceAdapter` calls only `getAirSupply()`/`getMaxAirSupply()` — no `setAirSupply` call exists anywhere in the adapter, and `supportedOperations()` declares only `QUERY`, which `ExternalPlayerResourceAdapterRegistry.register` enforces is non-empty and which `PlayerResourceService` re-checks defensively at query time (both pre-existing Phase 2A guarantees, automatically inherited).

## 7. Production registry entry

```java
PlayerResourceRegistry.INSTANCE.register(
        PlayerResourceDefinition.builder(PlayerResourceIds.BREATH, ResourceModel.SCALAR)
                .polarity(ResourcePolarity.HIGH_IS_GOOD)
                .externalAdapter(PlayerResourceIds.BREATH_ADAPTER)
                .unitScale(1)
                .absoluteMinimum(0)
                .authoredBaseMaximum(BreathResourceAdapter.VANILLA_BASELINE_MAXIMUM) // 300
                .capabilities(ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                .build());
```

No `SPENDABLE`/`RESTORABLE`/`DIRECT_DRAIN` capability — Breath is query-only, identical capability shape to Health/Food. `authoredBaseMaximum` is descriptive metadata only; the live query path (§6) never consults it.

**Final production registry contents (after this phase):** exactly `totality:health`, `totality:food`, `totality:breath` — all `ResourceModel.SCALAR`, all `ResourceStateAuthority.EXTERNAL_ADAPTER`, all `ResourcePolarity.HIGH_IS_GOOD`, all frozen deterministically at `ProductionResourceDefinitions.register()` (called once from `Totality.registerApi()`, before any player can join). No `totality:oxygen`, `totality:air`, or `totality:temperature` definition exists — verified by test (§9).

**Final external-adapter registry contents:** exactly `HealthResourceAdapter.INSTANCE`, `FoodResourceAdapter.INSTANCE`, `BreathResourceAdapter.INSTANCE` — verified by test (§9).

## 8. Presentation scope

- `VanillaHudElements.AIR_BAR` was not replaced, redrawn, suppressed, or moved. `TotalityHudRenderer` was not modified — no test required it to be (§3 already confirmed zero pre-existing Totality touchpoints on `AIR_BAR`, and this phase added none).
- No `ResourceValueFormatter` was registered for `totality:breath` — deliberate. Breath's final presentation unit (seconds, percentage, pips — undecided) is out of scope for this phase and deferred to a future HUD-presentation phase, per the task's explicit instruction not to invent one speculatively. Verified by test (`productionRegistryHasNoBreathFormatter`).
- Breath legitimately has `HUD_VISIBLE`/`MENU_VISIBLE` capabilities declared on its definition even though nothing currently renders it — this mirrors the canonical design's separation between "this resource is presentable" (capability) and "here is how to present it" (formatter registration), which is a deliberate two-step gate, not an oversight.

## 9. Tests

All 214 pre-existing tests preserved unchanged in intent (8 of the 14 modified test files were touched only to adapt to the `Optional<ResourceSnapshot>` interface change, with zero behavioral change to what they assert). Added/changed coverage:

| File | Tests | Coverage |
|---|---|---|
| `BreathResourceAdapterTest` (new) | 15 | `normalize()` pure logic: full=max, partial, zero, negative→0 (incl. `Integer.MIN_VALUE`), above-max clamp, dynamic (non-300) maximums, zero/negative maximum → `Optional.empty()`, no float conversion, unit scale always 1, resource id preserved, `supportedOperations()`/`clientMirrorMode()`, adapter id constant. |
| `PlayerResourceRegistryTest` | +5 net (2 renamed/expanded, 3 new) | `productionSingletonContainsExactlyHealthFoodAndBreath` (was "...HealthAndFood...", now asserts size 3 + Breath's `EXTERNAL_ADAPTER` authority), `allThreeProductionDefinitionsAreExternalScalarResources` (new), `productionSingletonHasNoOxygenAirOrTemperatureDefinition` (was "...NoTemperatureOrBreathDefinition...", now checks `oxygen`/`air`/`temperature` absence since Breath is now legitimately present), `productionBreathDeclaresExactlyHudVisibleAndMenuVisibleCapabilities` (new), `productionBreathDeclaresAuditedVanillaBaselineMaximum` (new). |
| `PlayerResourceRegistryExternalAdapterFreezeTest` | +2 | `productionBreathDefinitionResolvesItsRegisteredAdapter`, `productionAdapterRegistryContainsExactlyHealthFoodAndBreath` (asserts adapter registry size 3, frozen). |
| `PlayerResourceStateComponentExternalSafetyTest` | +2 net (2 new, 2 renamed to include Breath) | `instantiateScalarRejectsBreath`, `instantiatePartitionedRejectsBreath`; `isRegisteredExternalAdapterAuthorityIsTrueOnlyForHealthAndFood` → `...HealthFoodAndBreath` now also asserts Breath is recognized; `registrationAloneCreatesNoPlayerState` now also asserts `!hasState(BREATH)`. |
| `PlayerResourceStateComponentExternalEntryPathTest` | +2 | `nbtLoadingQuarantinesStaleGenericDataForBreath`, `applyingASyncPayloadCannotCreateLiveBreathGenericState` — both mirror the existing Health/Food stale-NBT and stale-sync-packet quarantine tests exactly. `externalSnapshotsAlwaysComeFromTheAdapterNeverFromGenericState` and `healthAndFoodNeverAppearAsOrdinaryLiveComponentStatesOnAFreshComponent` extended to also assert Breath. |
| `ResourceValueFormatterRegistryTest` | +1 | `productionRegistryHasNoBreathFormatter` (see §8). |
| `PlayerResourceServiceTest` | +1 net (1 new, 1 renamed) | `nullAdapterSnapshotProducesCorruptAdapterSnapshotFailure` → renamed `emptyAdapterSnapshotProducesMalformedOwnerStateFailure` (now correctly asserts `MALFORMED_OWNER_STATE` for the legitimate-decline case, matching the interface change); new `nullOptionalReferenceFromAdapterProducesCorruptAdapterSnapshotFailure` covers the still-defended misbehaving-adapter case (a literal `null` `Optional` reference) separately, so both failure reasons remain independently tested. |
| `ExternalPlayerResourceAdapterRegistryTest`, other interface-adaptation-only files | 0 net new | Anonymous adapter implementations updated to the `Optional<ResourceSnapshot>` signature; assertions unchanged. |

Query-routing through `PlayerResourceService` for Breath is covered structurally (adapter resolution, capability/authority checks, registry-exactness) rather than by constructing a real `Player`/`ServerPlayer` in a JUnit environment — this matches the established Health/Food precedent (`HealthResourceAdapterConversionTest`'s own Javadoc: "constructing one outside the Minecraft runtime is impractical... the actual adapter [is] exercised through a manual client smoke test"). `BreathResourceAdapter.snapshot()`'s only non-pure work is two vanilla getter calls (`getAirSupply()`, `getMaxAirSupply()`) delegated straight into the already-tested `normalize(...)` core — the manual smoke test below is what actually exercises `snapshot()` end-to-end against a real player.

**No real underwater runtime test was performed by Claude Code in this session** — no client/game harness was run here; the automated tests above are pure-logic/routing coverage only. §11's manual smoke-test checklist was the plan for Stefan to execute that verification separately, and it has since been executed and passed — see "Final Status — Ready to Commit" at the end of this report.

**Test count: 214 → 240** (26 new/net-added tests).

## 10. Build and validation results

- `./gradlew compileJava` — **BUILD SUCCESSFUL**.
- `./gradlew test` — **240/240 passed, 0 failures.**
- `./gradlew runDatagen` — **BUILD SUCCESSFUL**, log confirms `Caching: total files: 349, old count: 349, new count: 349, removed stale: 0, written: 0`. No generated JSON changed as a result of this phase; the 21 pre-existing modified generated files in the working tree were already present before this phase started and are unrelated (confirmed via `git status --short` diff before/after running datagen — byte-identical).
- `./gradlew build -x runDatagen` — **BUILD SUCCESSFUL** (compile, test, jar, sourcesJar, assemble, check all succeeded).
- `git diff --cached` — empty throughout; nothing was staged or committed at any point in this phase.

## 11. Manual smoke-test checklist (for Stefan — not executed by Claude Code; no GUI/game-client access available in this session)

1. Launch the 26.2 client and join a test world.
2. Swim underwater and confirm the vanilla air-bubble HUD still counts down exactly as before (no visual change expected).
3. Confirm drowning still deals damage at the same point it always did (air reaching `-20`).
4. Confirm Water Breathing (potion/effect) still fully suppresses air loss underwater, unchanged.
5. Confirm a Conduit's Conduit Power still refills air, unchanged. (Not tested in the executed pass — see "Final Status — Ready to Commit"; non-blocking, since Water Breathing exercises the same underlying `MobEffectUtil.shouldEffectsRefillAirsupply` code path.)
6. Confirm a turtle-shell-equivalent `canBreatheUnderwater()` case (if any exists in Totality content) still bypasses drowning entirely, unchanged.
7. Surface and confirm air replenishes at the same visual rate as before.
8. Run `/totality inspect` (or the equivalent debug/query surface, if one exists for the Resource API by the time this is tested) against yourself while at full air, partial air, and mid-drowning (negative raw air) — confirm the reported Breath value is `full`, `partial`, and `0` respectively, never negative.
9. Confirm no console/log error or exception appears related to `totality:breath`, `BreathResourceAdapter`, or `PlayerResourceService` at any point during steps 1–8.
10. Save and close the world; confirm no crash or NBT-write error.
11. Reopen the world and confirm vanilla air behavior resumes normally, with no Resource API NBT or synchronization error. (Deliberately not claiming a specific current-air value must persist through logout/world-reopening — see the "Corrected persistence wording" note in "Final Status — Ready to Commit" for why.)
12. Confirm HP, Hunger, Stamina, Mana, and Rage all display and behave exactly as they did after Phase 2A — no regression introduced by this phase's changes to shared files (`PlayerResourceService`, `ExternalPlayerResourceAdapter`, `ResourceQueryFailureReason`).
13. Confirm the vanilla air-bubble HUD's pre-existing cosmetic positioning quirk (noted in the original Oxygen-to-Breath audit addendum, relative to Totality's relocated Food bar) is unchanged — neither better nor worse — since this phase deliberately did not touch HUD rendering.
14. Confirm no new Totality HUD element related to Breath appears anywhere (none should — none was added).
15. Confirm dying while at negative air (mid-drown) still respawns normally, with no Resource-API-related error.
16. Confirm traveling between dimensions (e.g., Overworld ↔ Nether) does not disrupt air behavior.
17. A second clean shutdown completes normally with `BUILD SUCCESSFUL`-equivalent (no crash) after all of the above.

**Status:** ~~not executed by Claude Code (no GUI/display or game-client tool available in this session)~~ **SUPERSEDED — executed by Stefan on the real 26.2 client and PASSED.** See "Final Status — Ready to Commit" below for the full result.

## 12. Known limitations

- As with Phase 2A, `snapshot()`'s two vanilla getter calls are not exercised by an automated NBT/game-runtime test — only the pure `normalize(...)` core is. This is the same, previously-documented and accepted limitation (no Fabric GameTest harness exists yet in this repo).
- `getMaxAirSupply()`'s virtual/overridable nature means a hypothetical future entity type with a non-positive override would correctly produce `MALFORMED_OWNER_STATE` — this is defensive-but-currently-unreachable code, documented as such in `BreathResourceAdapter`'s Javadoc rather than silently assumed impossible.
- No presentation/display formatter exists yet for Breath (deliberate, see §8) — any future HUD or menu surface wanting to show Breath numerically will need that decided and registered first.

## 13. Explicit scope exclusions confirmed absent from the diff

No vacuum, smoke, choking, buried-alive, toxic-atmosphere, or non-oxygen-species-breathing mechanic was added. No Breath restore/drain/spend transaction, modifier, or maximum-resolver was added. No hazard warning, custom sound, or screen flash was added. No change to drowning damage, replenishment rate, Water Breathing, Conduit Power, bubble columns, the Respiration enchantment, or any helmet/equipment interaction. No change to death, respawn, logout, or dimension-travel air behavior. No change to native sync. No change to the vanilla air HUD. Confirmed by code review of the full diff (§4–§5) and by the test suite in §9 explicitly proving no generic state can ever be created for `totality:breath`.

## 14. Recommended next Resource API slice (recommendation only, not implemented)

With Health, Food, and Breath now established as the external-adapter pattern, the next natural slice is either (a) migrating Stamina/Mana off `PlayerResourceComponent` into the generic `GENERIC_COMPONENT` shape (the readiness audit's own §11 sequencing, item 5), which is a larger, behavior-preserving migration rather than a new wrap-only adapter; or (b) a Breath HUD-presentation phase deciding and registering the deferred display formatter from §8, if Stefan wants Breath surfaced somewhere Totality-specific (e.g. the character menu) ahead of the Stamina/Mana migration. Both are recommendations only — no implementation decision is made here.

---

## Final Status — Ready to Commit (2026-07-19)

This section is the authoritative, current status of Phase 2B, superseding every earlier "not executed"/"pending" manual-test statement in this report (§9, §11).

### Manual smoke test — PASSED

Stefan manually tested the final Phase 2B implementation (240/240 automated tests) on the real Minecraft 26.2 client against the existing test world. Result: **passed**. Confirmed:

- The Minecraft 26.2 client launched successfully.
- The existing test world loaded successfully.
- No Resource API component, NBT, query, or synchronization errors appeared.
- No air bubbles appeared unexpectedly while on land.
- Vanilla air bubbles appeared normally underwater.
- Air depleted normally underwater.
- Air refilled normally after surfacing.
- Drowning damage began normally after air depletion.
- Water Breathing prevented air depletion as expected.
- No new Totality Breath bar or number appeared anywhere.
- Health, Hunger, Stamina, Mana, and Rage remained unchanged.
- Save and exit completed normally.
- Reopening the same world produced no Resource API error, and vanilla breathing behavior resumed normally.
- Dimension travel did not produce a breathing or Resource API regression.
- Death and respawn restored ordinary breathing behavior without errors.
- Final shutdown completed cleanly.
- No exception was observed for `totality:breath`, `BreathResourceAdapter`, `PlayerResourceService`, or `resource_state`.

**Conduit Power was not tested.** This is non-blocking for Phase 2B: the critical native no-depletion code path (`MobEffectUtil.shouldEffectsRefillAirsupply`, §3) was already exercised through Water Breathing, since both Water Breathing and Conduit Power drive that same underlying vanilla check — Water Breathing coverage is representative of that path. Conduit Power specifically remains untested and should be confirmed in a future pass if it ever becomes load-bearing for a Totality-specific feature.

This is a manual playtest checklist, not exhaustive verification of every code path or every possible air-value combination — it confirms the specific behaviors listed above, on the specific test world used, on this one pass.

### Corrected persistence wording

An earlier draft of §3 and the §11 checklist risked implying two claims at once — that vanilla air is persisted, and that logout/rejoin necessarily resets it to full — which cannot both be true in general. This has been corrected throughout: §3's audit table describes persistence factually (a vanilla NBT `"Air"` field, entity-data carryover across logout/dimension travel), and §11 item 11 now asks only to confirm **vanilla air behavior resumes normally, with no Resource API NBT or synchronization error** on reopening — it does not claim a specific current-air value must survive logout/world-reopening. Respawn is documented separately and unambiguously: a fresh entity is created on respawn, which restores a fresh, normal (full) air reserve via the vanilla constructor — that is a distinct case from logout/rejoin of the same still-alive entity.

### Final Phase 2B status

- **Automated tests:** 240/240 passed (`./gradlew test`).
- **Compile:** succeeded (`./gradlew compileJava`).
- **Datagen:** wrote 0 files (`./gradlew runDatagen`, `written: 0`).
- **Full build:** succeeded (`./gradlew build -x runDatagen`).
- **Manual smoke test:** passed (see above). Conduit Power not tested; non-blocking.
- **Phase 2B is ready to commit.**

### Scope boundaries carried forward unchanged

- Breath remains a **query-only external adapter** over vanilla air supply — `totality:breath` wraps `Player.getAirSupply()`/`getMaxAirSupply()`. The adapter never mutates its owner.
- **Vanilla owns** synchronization, persistence, depletion, refill, drowning, Water Breathing, Conduit Power, death, and respawn behavior — none of it was touched or reimplemented by this phase.
- **No custom Breath HUD was added.** `VanillaHudElements.AIR_BAR` remains the only Breath-related rendering.
- **No Breath gameplay mechanic was changed.** No vacuum, smoke, choking, toxic-atmosphere, or species-breathing mechanic exists yet.
- The canonical closed design (`TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`) was not modified by this phase or by recording this status — only implementation-status documents (this report, the readiness audit, the review bundle) were updated.
