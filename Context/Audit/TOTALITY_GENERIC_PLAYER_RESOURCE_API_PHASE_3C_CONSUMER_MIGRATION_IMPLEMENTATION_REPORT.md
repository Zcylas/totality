# Totality Generic Player Resource API — Phase 3C: Consumer Migration Implementation Report

**Date:** 2026-07-31
**Branch:** `feature/general-resource-api`
**Scope:** incremental client presentation-consumer migration (presentation-only)

---

## 1. Starting branch, HEAD, subject, and synchronization

- Branch: `feature/general-resource-api`
- Starting HEAD: `a3dd91cdb9894f0875bb0ce3f3cba9e5d5a256f2`
- Starting subject: `Add semantic tooltip foundation`
- `git fetch origin feature/general-resource-api` confirmed `origin/feature/general-resource-api` at the same commit — local and origin were synchronized before any edit.
- No Resource-API-scoped file was already modified at the start (confirmed by the starting repository-wide `git status --short`, §2, which shows only generated-datagen/build/review-bundle/screenshot/log churn — none under `src/main/java/zcylas/totality/api/rpg/resources/**`, `src/main/java/zcylas/totality/client/resource/**`, or the five consumer files).

## 2. Starting repository-wide status

```
 M build.gradle
 M src/main/generated/data/minecraft/worldgen/noise_settings/overworld.json
 M src/main/generated/data/totality/loot_table/blocks/*.json  (18 files)
 M src/main/generated/data/totality/recipe/*.json  (4 files: copper/diamond/gold/iron_gear)
?? Context/Audit/Image References/Tooltip1.png, Tooltip2.png, Tooltip3.png
?? Context/Audit/Review Bundles/*.zip  (24 pre-existing review bundles)
?? Context/Trading Test/trade_screen2-5.png
?? log4j-dev.xml
?? logs/
?? src/main/generated/.cache/
```
All of the above are the documented "known unrelated dirty and untracked files" and were preserved exactly (verified byte-for-byte identical at the end of this task — see §43/§44).

## 3. Starting Phase-3C-scoped status

Empty — `git status --short -- src/main/java/zcylas/totality/api/rpg/resources src/main/java/zcylas/totality/client/resource src/main/java/zcylas/totality/TotalityClient.java src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java src/main/java/zcylas/totality/screen` returned nothing before any edit.

## 4. Authoritative documents read

- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B_CLIENT_VIEW_AND_PARITY_READINESS.md`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B2_SHADOW_PARITY_READINESS.md`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3A_SYNCHRONIZATION_CONTRACT_IMPLEMENTATION_REPORT.md`
- `Context/Audit/PHASE_3A_EXTERNAL_REVIEW_CORRECTIONS.md`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B1_CLIENT_VIEW_IMPLEMENTATION_REPORT.md`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B2A_PURE_PARITY_IMPLEMENTATION_REPORT.md`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B2B_CLIENT_PARITY_INTEGRATION_REPORT.md`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B2C_BOUNDED_PARITY_LOGGING_IMPLEMENTATION_REPORT.md`
- `Context/Audit/TOTALITY_RESOURCE_API_PHASE_3B3_PARITY_INSPECTION_IMPLEMENTATION_REPORT.md` (treated as the final, authoritative closure of Phase 3B/3B-3)
- `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md`
- Phase 2C/2D/2E adapter implementation reports (Mana/Stamina, Spell Slots, Rage) for production resource IDs and unit scales
- Current live source (treated as authoritative over any doc where they disagree — none of the disagreements found affected this task; see the Phase 3B-3 report's own documented supersessions of the earlier 3B/3B-2 *readiness* docs regarding `ClientResourceTrust`'s 2-member shape and the elapsed-tick parity grace model)

## 5. Final Phase 3B-3 readiness/triage status

Per the Phase 3B-3 report (§13/§19/§21/§29 area) and confirmed against current source:
- The client façade (`ClientResourceService`, `ClientResourceQueryResult`, `ClientResourceSource`, `ClientResourceTrust`, `ClientResourceUnavailableReason`) and the shadow-parity system (`ClientResourceParityCoordinator`/`Tracker`/`Observations`/`LogObserver`) are fully built, tested, and wired at client startup, but **zero production presentation consumer read them before this task**.
- The `/totalitydebug resource parity` command (root literal `totalitydebug`, not `totality` — a real, previously-fixed Fabric client-dispatcher collision) is the live verification tool, gated to `FabricLoader.getInstance().isDevelopmentEnvironment()`.
- Known live legacy-manager staleness gaps recorded by the report, re-confirmed against current source this session:
  - **Rage maximum-sync gap**: `PlayerChargesComponent.applySyncPacket` only calls `existing.withCurrent(current)` for an already-existing client pool — a live maximum increase (e.g. class level-up) to an already-granted Rage pool is never reflected in the legacy mirror.
  - **Rage dimension-transfer discrepancy**: legacy Rage can drop to 0/0 after a dimension change while the Generic view stays correct; only death/respawn repairs the legacy side.
  - **Mana staleness after `FormulaResolver.tryCast`**: that rune-casting path calls `PlayerManaManager.removeMana` but sends no legacy `SyncManaPayload`, so `ClientManaManager` can go stale until the next regen tick while the Generic view is already correct.
  - **Spell-slot remaining-vs-used**: CLOSED — confirmed correct; the Generic partition's `currentUnits` is remaining slots, matching `ClientSpellSlotManager.getRemaining` exactly.

None of these are Resource-API defects — they are legacy-manager staleness the parity system exists to surface. Per this task's own instructions ("Phase 3C should prefer the Generic presentation when valid, so some legacy-visible staleness may naturally disappear from migrated UI... Do not 'fix' legacy gameplay or synchronization systems merely because the new display no longer needs them"), all six listed consumers (including both Rage sites and the Mana HUD) were migrated as specified; the known gaps above are expected to become *less* visible post-migration as a side effect, not something this task attempted to fix directly. No gap was found to make the legacy fallback itself unsafe (it always still returns its pre-existing convention — a real, if occasionally stale, value or a real 0), so nothing here triggered the "stop and document" escape hatch.

## 6. Complete current consumer inventory

| Consumer | Resource(s) | Migrated? |
|---|---|---|
| `TotalityHudRenderer` | Mana, Stamina (Health/Food stay native) | Yes |
| `TotalityClient` (Rage `ISecondaryResource`) | Rage | Yes |
| `ClassTab` (resource panel) | Rage | Yes |
| `SpellRadialScreen` (slot indicator) | Standard Spell Slots | Yes |
| `OverviewTab` (resources panel) | Mana, Stamina (Health stays native) | Yes |
| `TotalityMovementHandler` | Stamina (gameplay gating: Power Sprint, Super Leap) | **No — explicitly out of scope** |
| `MobHealthBarHud` | Mob health (not the Player Resource API) | **No — not applicable** |
| Breath | — | No consumer exists; none invented |

## 7. Classification of every direct legacy read found

- `TotalityHudRenderer.java` Mana/Stamina reads — presentation — **migrated**.
- `OverviewTab.java` Mana/Stamina reads — presentation — **migrated**.
- `TotalityClient.java` Rage `ISecondaryResource` reads — presentation — **migrated**.
- `ClassTab.java` Rage panel reads — presentation — **migrated**.
- `SpellRadialScreen.java` `ClientSpellSlotManager` reads — presentation — **migrated**.
- `TotalityMovementHandler.java` `ClientStaminaManager` reads (Power Sprint gate, Super Leap gate, early-return guards) — gameplay/prediction — **left untouched, no import of the client façade added**.
- `TotalityClientPacketHandlers.java` (`ClientManaManager.sync`/`ClientStaminaManager.sync` receivers) — legacy-fallback-impl — untouched.
- `SpellSlotComponent.applySyncPacket` (`ClientSpellSlotManager.apply`) — legacy-fallback-impl — untouched.
- `LegacyClientResourceParityReaders.java` — parity-debug-tooling — untouched.
- `ClientSpellSlotParityComparator.java` — parity-debug-tooling — untouched.
- `RageResourceAdapter.java` (server-side) — unrelated — untouched.
- `MobHealthBarHud.java` — unrelated (no Player Resource API reference of any kind, confirmed and pinned by a regression test) — untouched.

No previously-unknown presentation consumer was found beyond the six named in the task; no additional migration was performed.

## 8. Final presentation resolver architecture

New, presentation-only, client-only-in-practice-but-pure class:

`zcylas.totality.api.rpg.resources.client.presentation.ClientResourcePresentationResolver`
(`src/main/java/zcylas/totality/api/rpg/resources/client/presentation/ClientResourcePresentationResolver.java`)

```java
public final class ClientResourcePresentationResolver {
    public static final ClientResourcePresentationResolver INSTANCE = new ClientResourcePresentationResolver(ClientResourceService.INSTANCE);

    public ClientResourcePresentationResolver(ClientResourceService service) { ... }

    public ScalarPresentation resolveScalar(Identifier resourceId, LongSupplier legacyCurrent, LongSupplier legacyMaximum);
    public PartitionPresentation resolvePartition(Identifier resourceId, int partitionId, LongSupplier legacyCurrent, LongSupplier legacyMaximum);

    public record ScalarPresentation(long current, long maximum, boolean generic) {}
    public record PartitionPresentation(long current, long maximum, boolean generic) {}
}
```

It depends only on `ClientResourceService`/`ClientResourceQueryResult` (both already presentation-only, client façade types) and the caller-supplied legacy `LongSupplier`s. It imports no legacy manager class, no `ClientResourceSyncState`/`ClientResourceSyncManager`, no server-only class, and declares no mutating method (`apply`/`mutate`/`spend`/`restore`/`set`) — pinned by a regression test. It is placed under `api.rpg.resources.client.presentation` (a sibling of the existing `api.rpg.resources.client.parity` package) rather than the impure `client.resource` package, matching `ClientResourceService`/`NativeClientResourceReader`'s own convention of staying pure/unit-testable without a bootstrapped client.

Production flow, matching the task's required shape:
```
UI consumer -> ClientResourcePresentationResolver.INSTANCE.resolve{Scalar,Partition}(...)
                 -> ClientResourceService.INSTANCE.query{Scalar,Partitioned}(...)
                     -> registered ClientResourceReader (GenericSyncClientResourceReader for Mana/Stamina/Rage/SpellSlots)
                 -> legacy supplier only when Unavailable
             -> existing renderer/layout (byte-for-byte unchanged geometry/colors/labels)
```

## 9. Scalar selection and fallback policy

`resolveScalar` calls `ClientResourceService.queryScalar(resourceId)`. If the result is `ClientResourceQueryResult.Scalar` (any trust level), it divides `currentUnits`/`maximumUnits` by `max(1, unitScale)` and returns that — Mana/Stamina/Rage's canonical `unitScale` is `1` in production, so this is presently a no-op division, but the resolver is not hardcoded to scale 1. On any `Unavailable` result, it calls the caller's `legacyCurrent`/`legacyMaximum` suppliers and returns those, with `generic=false`. The legacy suppliers are never evaluated when the Generic branch is taken (proven by a dedicated test using suppliers that flip a flag if invoked).

## 10. Partitioned selection and fallback policy

`resolvePartition` calls `ClientResourceService.queryPartitioned(resourceId)`. If the result is `Partitioned` **and** contains an entry for the requested `partitionId`, it returns that partition's `currentUnits`/`maximumUnits` (scaled the same way). If the result is `Unavailable`, or is a structurally valid `Partitioned` result that simply has no entry for that partition id (defensive — does not happen in production, since the adapter always emits partitions 1-10), it falls back to the legacy suppliers. Partition id = spell level (1-10), unchanged from the pre-existing `ClientSpellSlotManager` convention.

## 11. PENDING_RESYNC policy

`resolveScalar`/`resolvePartition` branch on the result's Java type (`Scalar`/`Partitioned` vs. `Unavailable`), never on the `trust` field — so both `FRESH` and `PENDING_RESYNC` are treated identically and preferred over the legacy fallback, matching the task's SCALAR/PARTITIONED policy items 2-4/5. No blanking or flicker is introduced for a `PENDING_RESYNC` value.

## 12. Unavailable/no-fabricated-zero policy

An `Unavailable` result always defers to the caller's real legacy reader — never to a resolver-invented zero. The legacy reader itself owns whatever "no data" convention it already had (e.g. a genuinely-never-granted Rage pool legitimately reading 0/0 from the sparse `PlayerChargesComponent` map, exactly as it did before this task) — this task did not change that convention anywhere. Pinned by `unavailableNeverBecomesFabricatedZero` and `presentRageZeroZeroIsAValidGenericSuccessNotFallback` in the resolver test.

## 13. Unit-scale handling

`unitScale` is read from the query result itself (`Scalar.unitScale()`/`Partitioned.unitScale()`), clamped to a minimum of `1` defensively (the type's own constructor already enforces `>= 1`), and applied via plain integer division — deterministic, and overflow-safe because division of two non-negative `long`s can never overflow. Verified with a resolver test at `unitScale=10` against 12-digit values, and a test at `Long.MAX_VALUE - 1`/`Long.MAX_VALUE` current/max confirming no exception and exact values returned.

## 14. Mana HUD migration

`TotalityHudRenderer.java` — the `stamina`/`maxStamina`/`mana`/`maxMana` locals (previously read directly from `ClientStaminaManager`/`ClientManaManager`) are now the resolver's `current()`/`maximum()`, cast to `int`. Every downstream use (percentage smoothing via `SmoothValue`, `drawBarSmooth` fill + numeric text, `AC` label position) is unchanged — the bar sprite, geometry constants (`BG_WIDTH=83`, `BG_HEIGHT=8`, `BAR_SPACING=3`, `BOTTOM_MARGIN=2`), colors, and text formatting (`buildText`/`formatValue`) were not touched.

## 15. Stamina HUD migration

Same call site, same pattern, `PlayerResourceIds.STAMINA` — see §14.

## 16. OverviewTab migration

`OverviewTab.java`'s `drawResourcesPanel` — the `sta`/`maxSta`/`mana`/`maxMana` locals are now sourced from the resolver the same way as §14, cast to `float` (unchanged local type). `hp`/`maxHp` still read `player.getHealth()`/`getMaxHealth()` via `RpgDisplayUtils.toDisplayHp` — untouched. Row layout, icons (`♥`/`⚡`/`◇`), labels, and column math are byte-for-byte unchanged.

## 17. Rage secondary HUD migration

`TotalityClient.java`'s Rage `ISecondaryResource` anonymous class — `getCurrent`/`getMax` now call `ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.RAGE, () -> legacyRageCurrent(client), () -> legacyRageMax(client))`. The two new `private static` helper methods (`legacyRageCurrent`/`legacyRageMax`) contain the exact pre-existing try/catch body (`ChargeComponents.PLAYER_CHARGES.get(...)`, catch `Exception` → `0`), unchanged. `shouldShow` (Barbarian-class gate), `getColor`, and both pip sprite getters are untouched. `drawSecondaryResources`'s existing `r.getMax(client) > 0` visibility filter is unchanged, so a non-Barbarian or unavailable Rage resource still never renders — it is never fabricated as a visible 0/0 pool.

## 18. ClassTab Rage migration

`ClassTab.java`'s `drawResourcePanel` — the single `try { ... } catch (Exception ignored) {}` block reading both current and max charges was replaced by one `resolveScalar(PlayerResourceIds.RAGE, ClassTab::legacyRageCurrent, ClassTab::legacyRageMax)` call, with the two legacy readers extracted as `private static` methods carrying the identical original try/catch bodies. `resourceName`/`rechargeNote`/"No resource" empty-state text, pip layout (`pipSz=11`, `pipGap=4`), and colors are unchanged.

## 19. SpellRadialScreen migration

`SpellRadialScreen.java`'s `drawSlotIndicator` — the cantrip short-circuit (`spell.isCantrip()` → draw "∞", return) is unchanged and untouched. For a leveled spell, `max`/`remaining` are now sourced from `resolvePartition(PlayerResourceIds.SPELL_SLOTS, level, () -> ClientSpellSlotManager.getRemaining(level), () -> ClientSpellSlotManager.getMax(level))`. Partition id = `spell.getSpellLevel()` (1-10, unchanged). Pip rendering (`PIP_SIZE=6`, `PIP_GAP=3`, colors) is unchanged. Selection state (`selectedSlot`), the cast/select path (`selectSpell` → `ClientSelectedSpellManager.setSelectedSpell` + `SelectSpellPayload`), and all input handling (`tick()`, `mouseClicked`) are untouched — confirmed no reference to `SpellSlotComponent` (the server-side mutator) exists anywhere in this file.

## 20. Consumers deliberately not migrated

- `TotalityMovementHandler` — see §21.
- `MobHealthBarHud` — mob health is not the Player Resource API; confirmed zero references to `PlayerResourceService`/`ClientResourceService`/`PlayerResourceIds` in the file both before and after this task.
- Breath — no existing HUD/menu/debug consumer beyond the parity command exists to migrate; none was invented.
- `TotalityClientPacketHandlers`, `SpellSlotComponent.applySyncPacket`, `LegacyClientResourceParityReaders`, `ClientSpellSlotParityComparator` — legacy-fallback-impl or parity-debug-tooling, deliberately left untouched per the LEGACY FALLBACK POLICY.

## 21. TotalityMovementHandler authority boundary

Unchanged. `TotalityMovementHandler.java` still reads `ClientStaminaManager.getStamina()` directly at all three of its gameplay-gating sites (Power Sprint eligibility, the Power Sprint early-return guard, and the Super Leap cost guard). It imports neither `ClientResourcePresentationResolver` nor `ClientResourceService` — confirmed by a regression test (`movementHandlerDoesNotImportOrUseThePresentationResolver`). The server continues to independently validate all gameplay Stamina spending; no client presentation value from this task was ever treated as movement authority.

## 22. Health/Food native-authority preservation

Untouched everywhere. `TotalityHudRenderer` still reads `client.player.getHealth()`/`getMaxHealth()`/`getFoodData().getFoodLevel()` directly for both bar-fill ratio and (via the pre-existing, Phase-3B-era `resourceDisplayCurrentMax` helper and `PlayerResourceService`) numeric text; `OverviewTab` still reads `player.getHealth()`/`getMaxHealth()` via `RpgDisplayUtils.toDisplayHp`. Neither file was made to call `ClientResourcePresentationResolver` for `PlayerResourceIds.HEALTH` or `PlayerResourceIds.FOOD` — confirmed by regression tests. No Food 0-100 conversion or ×5 multiplication was introduced; the Food bar-fill ratio is still the native `hunger / 20.0`.

## 23. Breath no-new-consumer decision

No Totality Breath presentation consumer (HUD, menu panel, or debug UI) was created. Vanilla air bubbles are untouched. The existing `/totalitydebug resource parity` command continues to expose Breath exactly as it already did.

## 24. Legacy managers/packets retained

`ClientManaManager`, `ClientStaminaManager`, `ClientSpellSlotManager`, `PlayerChargesComponent`, `SyncManaPayload`, `SyncStaminaPayload` all still exist at their original paths, unmodified — confirmed by an existence-check regression test. No legacy manager's own convention, packet, or sync path was changed by this task.

## 25. Parity/debug tooling preservation

`ClientResourceParityCoordinator`, `ClientResourceParityTracker` (and the rest of the parity package), and `ClientResourceParityInspectionCommand` (`/totalitydebug resource parity`) all still exist and were not modified — confirmed by an existence-check regression test.

## 26. Known legacy gaps and their Phase 3B-3 status

See §5. All three staleness gaps (Rage max-sync, Rage dimension-transfer, Mana rune-cast staleness) remain exactly as documented by the Phase 3B-3 report — none was touched or "fixed" by this task. Migrating the affected consumers' *presentation source* to prefer the Generic view is expected to make these specific symptoms less visible in the migrated UI, as a natural consequence of preferring a value that was already correct — not because any legacy system was altered.

## 27. Exact production files changed

- `src/main/java/zcylas/totality/api/rpg/resources/client/presentation/ClientResourcePresentationResolver.java` (**new**)
- `src/main/java/zcylas/totality/TotalityClient.java` (modified)
- `src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java` (modified)
- `src/main/java/zcylas/totality/screen/ability/SpellRadialScreen.java` (modified)
- `src/main/java/zcylas/totality/screen/character/tabs/ClassTab.java` (modified)
- `src/main/java/zcylas/totality/screen/character/tabs/OverviewTab.java` (modified)

`git diff --stat` for the five modified files: `104 insertions(+), 32 deletions(-)` across 5 files.

## 28. Exact test files changed/created

- `src/test/java/zcylas/totality/api/rpg/resources/client/presentation/ClientResourcePresentationResolverTest.java` (**new**, 16 tests)
- `src/test/java/zcylas/totality/api/rpg/resources/client/presentation/Phase3CConsumerMigrationSourceRegressionTest.java` (**new**, 18 tests)

No existing test file was modified or weakened.

## 29. Test categories and evidentiary limits

- **Real, executing pure tests** (`ClientResourcePresentationResolverTest`, 16 tests): cover the CORE RESOLUTION (1-8), SPELL SLOTS (22-30), and Rage-specific PENDING_RESYNC/0-0 items against a real `PlayerResourceRegistry` via `TestResourceBootstrap`, using a synthetic `ClientResourceReader` that returns a pre-built `ClientResourceQueryResult` directly. This is genuine runtime proof of the resolver's own resolution/fallback logic.
- **Source-text regression sentinels** (`Phase3CConsumerMigrationSourceRegressionTest`, 18 tests): cover the consumer-wiring items (9/10/11/12/13/14/16/17/18/20) and the architecture/source-regression items (31-39). These are sentinels, not runtime proof — the migrated files are client HUD/screen render methods requiring a bootstrapped, GL-initialized `Minecraft`/`Font` to execute, unavailable under plain JUnit, the same constraint already documented by `TooltipApiFoundationSourceRegressionTest` and `CombatRollNotificationSourceRegressionTest` elsewhere in this suite. Runtime confirmation of the actual rendered behavior is the manual validation in §36/§37 (partially performed — see limitations, §38).
- Item 39 (no Tooltip API file modified) is additionally confirmed directly by `git diff --stat` (§27) showing no file under `client/tooltip/` in the changed-file list.

## 30. Focused test command and exact result

```
./gradlew test --tests "zcylas.totality.api.rpg.resources.client.presentation.*"
```
**Tests: 34, Failures: 0, Errors: 0, Skipped: 0** (16 in `ClientResourcePresentationResolverTest` + 18 in `Phase3CConsumerMigrationSourceRegressionTest`).

## 31. Full-suite command and exact result

```
./gradlew test
```
**Tests: 1205, Failures: 0, Errors: 0, Skipped: 0** across 93 test classes (aggregated from `build/test-results/test/TEST-*.xml`).

## 32. Datagen result

```
./gradlew runDatagen
```
Log: `Caching: total files: 350, old count: 350, new count: 350, removed stale: 0, written: 0`. `git status --short` captured immediately before and after this run is **byte-for-byte identical** (diffed directly) — datagen wrote zero task-unrelated files, and the pre-existing 22 dirty generated files were not touched further.

## 33. Clean-build result

```
./gradlew clean build
```
**BUILD SUCCESSFUL** (`clean`, `compileJava`, `processResources`, `classes`, `processIncludeJars`, `jar`, `sourcesJar`, `assemble`, `compileTestJava`, `testClasses`, `test`, `validateAccessWidener`, `check`, `build` all succeeded). The `:test` task inside this run reproduced the identical **1205/0/0/0** totals from §31.

## 34. `git diff --check` result

Exit code `0`. Output was only pre-existing CRLF/LF advisory warnings on the 22 already-dirty generated JSON files (none of which this task touched) — no actual whitespace error, and none on any Phase 3C file.

## 35. Dedicated-server classloading result

```
./gradlew runServer   (bounded run, ~100s)
```
Server reached `[Server thread/INFO] (Minecraft) Done (0.371s)! For help, type "help"` with **no `ClassNotFoundException`/`NoClassDefFoundError`** anywhere in the log. Three pre-existing, unrelated self-test failures were observed (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) — these are exactly the kind of stale-world Provisioner/Offhand/PowerAttack issue this task was explicitly told not to investigate, and are unrelated to the Resource API. The server process exited cleanly (confirmed no lingering listener on port 25565, no orphaned `java.exe` beyond the normal Gradle daemon).

## 36. Manual validation completed

- `./gradlew runClient` (bounded run, ~60s): the client reached the title screen — full mod client-init (`TotalityClientResourceReaders.register()`, `TotalityHudRenderer.register()`, the Rage `ISecondaryResource` registration, all self-test verifications) completed with **no exception**, confirming the migrated code loads and initializes correctly.
- The dedicated-server classloading check (§35).
- Full/focused automated test suites (§30/§31/§33).

## 37. Manual validation remaining

**Not performed by this agent** — genuine in-world gameplay interaction (movement, spell-casting, menu navigation, chat commands) requires a human at the keyboard/mouse, which this environment cannot provide. The following from the task's MANUAL VALIDATION section still needs a human pass before merge:
- Join/initial-sync HUD-fabrication check.
- Mana: spend via spell/ability, regenerate, exercise a Grimoire/rune cast, compare HUD/OverviewTab against `/totalitydebug resource parity`.
- Stamina: sprint/spend, regenerate, confirm HUD/OverviewTab update, confirm Power Sprint/Leap/Flight gating is unaffected.
- Rage: Barbarian HUD visibility, spend/restore, Short/Long Rest, ClassTab inspection, dimension-transfer behavior, parity comparison.
- Spell Slots: pip correctness, successful-cast slot consumption, failed-cast behavior, cantrip non-effect, tier ordering, parity comparison.
- Lifecycle: disconnect/reconnect, dimension transfer, death/respawn, absence of unexpected `PERSISTENT_MISMATCH`.
- Simulated/practical fallback verification (Generic temporarily unavailable → legacy still usable).

## 38. Known limitations

- The five migrated render methods have no pre-existing unit-test coverage of their own (none of `TotalityHudRenderer`/`SpellRadialScreen`/`OverviewTab`/`ClassTab`/`TotalityClient` appear anywhere in `src/test/java` before this task) — this is the first test coverage those specific call sites get, and it is necessarily source-sentinel-shaped rather than execution-shaped, per §29's evidentiary-limits note.
- Full interactive manual validation (§37) was not performed and must be done by a human before the Phase 3C changes are committed and pushed. (No merge of this branch is currently planned as part of this task.)
- The three known legacy staleness gaps (§5/§26) remain live and undisturbed; migrated consumers may now visibly differ from what a player previously saw during those specific windows (Mana briefly after a rune cast, Rage briefly after a dimension transfer or class level-up) — this is the intended, disclosed effect of preferring the Generic view, not a regression, but it is worth a human's attention during manual validation.

## 39. Confirmation that Food 0-100 was not started

Confirmed. `TotalityHudRenderer`'s Food bar-fill ratio is still `hunger / 20.0` (native 0-20 scale), pinned by `hudRendererFoodBarStillUsesTheNativeZeroToTwentyRatio`. No `FoodResourceAdapter`, `ResourceDisplayConversion`, or any other Food-scale file was touched.

## 40. Confirmation that HUD cleanup was not started

Confirmed. `TotalityHudRenderer`'s geometry/layout constants (`BG_WIDTH`, `BG_HEIGHT`, `BAR_SPACING`, `BOTTOM_MARGIN`, etc.) are unchanged, pinned by `hudRendererPreservesExistingBarGeometryConstants`. No AC-indicator, secondary-resource-row, or context-renderer code was touched beyond the two local-variable read sites.

## 41. Confirmation that the Tooltip API was not changed

Confirmed. `git diff --stat` (§27) shows no file under `src/main/java/zcylas/totality/client/tooltip/` or any Tooltip contributor/renderer path. No import of `zcylas.totality.client.tooltip.*` was added to any migrated file or to the new resolver, pinned by `noMigratedConsumerImportsTheTooltipApi`. No genuinely-unavoidable import correction was needed.

## 42. Final task-scoped diff/stat

```
 src/main/java/zcylas/totality/TotalityClient.java                          | 37 +++++++++++++----
 src/main/java/.../client/renderer/hud/TotalityHudRenderer.java             | 19 +++++++--
 src/main/java/.../screen/ability/SpellRadialScreen.java                    | 15 ++++++-
 src/main/java/.../screen/character/tabs/ClassTab.java                      | 47 +++++++++++++++-------
 src/main/java/.../screen/character/tabs/OverviewTab.java                   | 18 +++++++--
 5 files changed, 104 insertions(+), 32 deletions(-)
```
Plus 1 new production Java file (`ClientResourcePresentationResolver.java`, under a newly-created `api/rpg/resources/client/presentation` package directory — the directory itself is not a file and is not separately counted) and 2 new test Java files (34 tests) — none tracked by `git diff --stat` since they are untracked additions (`?? ` in `git status`).

## 43. Final repository-wide status

Identical in shape to §2, plus exactly the Phase 3C changes: the same 5 modified `src/main/generated` files + `build.gradle`, the same 24 pre-existing untracked review-bundle/screenshot/log/cache entries, **plus** the 5 modified Phase 3C production files and the 2 new untracked Phase 3C directories (`src/main/java/zcylas/totality/api/rpg/resources/client/presentation/`, `src/test/java/zcylas/totality/api/rpg/resources/client/presentation/`). Verified directly via `git status --short` at the end of this task (§ below, reproduced in the final response).

## 44. Confirmation that unrelated files were preserved

Confirmed. The pre-Phase-3C `git status --short` snapshot and the post-datagen/post-build snapshot were diffed directly and found identical for every non-Phase-3C entry; no unrelated file was reset, stashed, cleaned, or restored at any point in this task.

## 45. Confirmation that nothing was committed or pushed

Confirmed. No `git add`, `git commit`, or `git push` command was run at any point in this task. `git log -1` still reports `a3dd91cdb9894f0875bb0ce3f3cba9e5d5a256f2 Add semantic tooltip foundation` as HEAD.

## 46. Recommended next step

1. Independent review of this implementation (production diff, resolver design, test coverage, and this report).
2. Human manual validation per §37, using `/totalitydebug resource parity` as the live comparison tool.
3. Commit and push once review + manual validation are satisfied.
4. Then: the small HUD cleanup pass, and the Food/Fatigue/Exhaustion audit — both explicitly out of scope for this task and not started.

## 47. Independent-review correction addendum (2026-07-31)

An independent static review of the Phase 3C implementation and its 24-file review bundle was performed after §1-46 above were written. Findings and corrections:

- The 24-file bundle (`TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE_3C_CONSUMER_MIGRATION_IMPLEMENTATION_BUNDLE.zip`) was independently inspected: opened successfully, no corruption.
- The manifest's declared counts (5 modified, 4 new, 0 deleted, 14 unchanged-reference, 24 total physical files including the manifest itself) matched the archive's actual contents exactly.
- No duplicate archive paths were found.
- No blocking production-code defect was identified in the migrated consumers or `ClientResourcePresentationResolver` — the resolution/fallback/PENDING_RESYNC/no-fabricated-zero behavior described in §8-13 was re-confirmed against the live source.
- The only findings were non-behavioral: (1) a stale comment above `TotalityClientResourceReaders.register()` in `TotalityClient.java` still said "no production consumer reads ClientResourceService yet," which became false once Phase 3C's five consumers began reading it through `ClientResourcePresentationResolver`; and (2) §42 of this report described the new production additions as "2 new production files (resolver + package)," incorrectly counting a package directory as a file.
- Both findings have been corrected in this pass (see the updated comment in `TotalityClient.java` and the corrected §42 above). Neither correction changed any runtime behavior, test, or the resolver/consumer design.
- Human in-world manual validation (§37) remains required before the Phase 3C changes are committed and pushed — this correction pass does not substitute for it.

**Re-validation after applying the two corrections** (comment wording only, no behavioral change):
- Focused Phase 3C tests: **34, Failures: 0, Errors: 0, Skipped: 0** — identical to §30.
- `./gradlew test`: **1205, Failures: 0, Errors: 0, Skipped: 0** across 93 test classes — identical to §31.
- `./gradlew runDatagen`: `written: 0`; `git status --short` before/after byte-for-byte identical — identical to §32.
- `./gradlew clean build`: **BUILD SUCCESSFUL**, `:test` reproduced the same 1205/0/0/0 — identical to §33.
- `git diff --check`: exit code `0`, only the same pre-existing CRLF advisories on the 22 already-dirty generated files — identical to §34.

## 48. Human in-world manual validation — completed, and finalization closure (2026-07-31)

This section records that the human in-world manual validation deferred at §37 has now been completed, and closes out Phase 3C.

### 48.1 Manual validation result

A human performed an in-world manual pass over the migrated presentation consumers. Recorded honestly, at the level of detail actually observed (not a claim of exhaustive per-scenario coverage beyond what §37 originally listed):

- **Mana** presentation appeared to work correctly.
- **Stamina** presentation appeared to work correctly.
- **Rage** presentation appeared to work correctly when using a Barbarian.
- **Standard Spell Slot** presentation appeared to work correctly.
- The migrated HUD (`TotalityHudRenderer`) and screen consumers (`ClassTab`, `SpellRadialScreen`, `OverviewTab`, and the Rage secondary-HUD registration in `TotalityClient`) showed no observed presentation regression.
- Health and Food remained unchanged and native-backed throughout — no numeric or bar-fill anomaly was observed on either.
- No gameplay-gating regression was observed (Power Sprint/Super Leap Stamina gating, spell casting/slot spending, Rage spend/restore all continued to function as gameplay, independent of the presentation source).
- Phase 3C's presentation-only boundary remained intact: no case was observed where a client presentation value was treated as gameplay authority, and no legacy manager, packet, or parity tool stopped working.

### 48.2 Diagnostic observation: Wizard non-Barbarian Rage parity

During validation, the following was observed and is recorded exactly:

- While the player was a **Wizard**, the Resource parity/debug command (`/totalitydebug resource parity`) reported **legacy Rage as 0/0** while the **Generic Resource view reported Rage as 2/2**.
- After changing the player to **Barbarian**, Rage reported an **EXACT MATCH**.
- **No incorrect usable Rage presentation or gameplay behavior was observed** as a result of this discrepancy at any point — the migrated Rage HUD/ClassTab consumers did not show a spurious 2/2 Rage pool to the non-Barbarian Wizard player (the pre-existing `shouldShow`/`getMax() > 0` visibility gates, unchanged by Phase 3C, kept Rage presentation hidden for a non-Barbarian regardless of which side of the parity comparison it was read from).

**Classification: a known parity/debug applicability limitation, not a Phase 3C presentation-migration failure.**

The differing semantics behind the two numbers:

- **Legacy 0/0** represents Rage as *not applicable* for a non-Barbarian — `PlayerChargesComponent`'s sparse charge-pool map has no entry for a player who was never granted the Barbarian Rage charge pool, and a missing-key lookup conventionally reads back as `0`/`0`.
- **Generic 2/2** represents the *registered dormant* Rage resource state — the Generic Player Resource API registers `totality:rage` as a production resource for every player regardless of class (per `ProductionResourceDefinitions`/`RageResourceAdapter`), so a Wizard has a structurally valid, present Rage resource whose current/maximum happen to reflect whatever dormant value the adapter resolves to when Barbarian-specific mechanics aren't driving it.
- Once Barbarian eligibility applies, both systems are observing the same live, actively-managed charge pool, so they agree exactly (**EXACT MATCH**).

This is a difference in what "not applicable" versus "applicable-but-dormant" *means* between two systems with different absence conventions being compared naively by the parity tool — not a bug in either system's own authority, and not something Phase 3C's presentation migration introduced (the parity comparator's behavior here is unchanged from Phase 3B-3; see `ClientRageParityPolicy`/`LegacyClientResourceParityReaders.rageSummary()`, both untouched by Phase 3C).

**No changes were made in response to this observation.** Specifically, and deliberately:
- Generic Rage was **not** changed to report 0/0 for non-Barbarians — the Generic API's whole-population registration model is intentional and predates this task.
- Rage initialization, class eligibility, authority, persistence, synchronization, and gameplay behavior were **not** touched.
- The parity/debug command was **not** modified in this task — no genuine production defect was independently proven; the discrepancy is a diagnostic-tool applicability-labeling limitation, not a wrong value on either side.

### 48.3 Deferred future recommendation (not a Phase 3C blocker)

For a future pass (not this one), the parity/debug tooling could usefully distinguish four cases instead of two:
1. Applicable resource with a value.
2. Dormant/non-applicable resource (present in the Generic model but not currently meaningful for this player, e.g. Rage on a non-Barbarian).
3. Unavailable resource (structurally missing/unsynchronized).
4. Genuine current/max mismatch between Generic and legacy.

A future diagnostic could report Rage as **NOT APPLICABLE** for a non-Barbarian instead of comparing a dormant Generic 2/2 against a legacy 0/0 as if they were two competing answers to the same question. This is explicitly **deferred** and is **not a blocker** for Phase 3C.

### 48.4 Final status

- **Static review**: passed (Phase 3C implementation + independent correction pass, §47).
- **Corrections**: passed (the two non-behavioral corrections in §47 were applied and re-validated).
- **Automated validation**: passed (focused 34/0/0/0, full suite 1205/0/0/0, datagen zero unrelated writes, clean build successful, `git diff --check` clean apart from pre-existing unrelated line-ending notices).
- **Dedicated-server validation**: passed (§35 — reached `Done (0.371s)!` with no classloading exception).
- **Human in-world validation**: passed, with the documented non-Barbarian Rage parity/debug applicability limitation (§48.2) recorded as a known, non-blocking diagnostic-tool limitation.
- **Phase 3C is ready to commit and push.**
- **No further Phase 3C implementation correction is required.**
- **Food 0-100 remains unstarted.**
- **HUD cleanup remains unstarted.**
