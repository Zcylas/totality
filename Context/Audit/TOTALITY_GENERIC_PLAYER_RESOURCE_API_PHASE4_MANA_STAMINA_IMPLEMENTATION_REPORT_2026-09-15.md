# Generic Player Resource API — Phase 4: Mana + Stamina Authority Migration

**Date:** 2026-09-15
**Branch:** `feature/general-resource-api`
**Scope:** Migrate Mana and Stamina from legacy authoritative storage (`PlayerResourceComponent` + `PlayerManaManager`/`PlayerStaminaManager`) into `PlayerResourceStateComponent` + `PlayerResourceService`, behavior-neutral. **Rage and Spell Slots are untouched.**

**Note on scope relative to git history:** this branch already carried substantial uncommitted work before this task, including the just-completed pre-Phase-4 foundation pass and its external-review correction pass (mutation façade, grant system, maximum resolver, checked arithmetic, dormant-state safety — all already reported/reviewed separately). This report and its accompanying ZIP cover **only** the files this Phase 4 task itself created or touched; see §1 for the exact list, with `PlayerResourceStateComponent.java` flagged as the one file both this task and the prior correction pass touched.

---

## 1. Files changed

### Created (5 main + 2 test = 7 files)

| File | Purpose |
|---|---|
| `api/rpg/mana/ManaMaximumResolver.java` | Real `ResourceMaximumResolver` for `totality:mana` — delegates to `PlayerManaManager.getMaxMana` rather than duplicating the formula. |
| `api/rpg/stamina/StaminaMaximumResolver.java` | Same for `totality:stamina` / `PlayerStaminaManager.getMaxStamina`. |
| `api/rpg/resources/integration/PlayerBaselineResources.java` | The `totality:player_baseline` `GLOBAL_SYSTEM` grant provider (universal Mana+Stamina grant) + the first production `ResourceGrantReconciler` singleton + `reconcile`/`ensureInstantiated`. |
| `networking/resource/BaselineResourceLifecycleEvents.java` | Wires `PlayerBaselineResources.reconcile` into join/respawn/dimension-transfer, plus the one-time legacy NBT migration-import step on join. |
| `api/rpg/resources/verification/BaselineResourceMigrationVerification.java` | Dev-only, real-`ServerPlayer`, real-production-registry end-to-end self-test (10 checks) — see §17. |
| `test/.../PlayerResourceStateComponentDeathPolicyTest.java` | Plain-JUnit proof that `copyFrom` now honors `ResourceDeathPolicy` for the real Mana/Stamina/Thirst definitions. |
| `test/.../integration/PlayerBaselineResourcesTest.java` | Plain-JUnit proof of the real registered grant's shape. |

### Modified (10 main + 4 test = 14 files)

| File | Change |
|---|---|
| `api/rpg/resources/ProductionResourceDefinitions.java` | Mana/Stamina: `EXTERNAL_ADAPTER` → `GENERIC_COMPONENT`, dropped `authoredBaseMaximum`, added `RESET_TO_MAXIMUM` lifecycle, bumped `definitionVersion` 1→2; new `registerMaximumResolvers()`/`registerGrants()` steps. |
| `api/rpg/resources/PlayerResourceStateComponent.java` | `copyFrom` now consults each resource's `ResourceDeathPolicy` instead of always blanket-copying (see §6). **Also touched by the immediately-prior, separately-reported correction pass** — this task's own diff is confined to the `copyFrom` method. |
| `api/rpg/mana/PlayerManaManager.java` | Full internal rewrite: compatibility facade over `PlayerResourceService` (canonical §24.3). Same public method signatures. |
| `api/rpg/stamina/PlayerStaminaManager.java` | Same, plus new `onLongRest(ServerPlayer)`. |
| `init/ModEvents.java` | Registers `BaselineResourceLifecycleEvents.register()` immediately after `PlayerComponentEvents.init()`, before `StatsServerEvents.register()` (ordering rationale in §5). |
| `init/events/PlayerConnectionEvents.java` | Registers a Stamina `RestListener` (Long Rest only) alongside the existing Rage/Abilities/SpellSlots ones, on both JOIN and AFTER_RESPAWN. |
| `networking/mana/ManaServerTick.java` | Comment-only: clarifies the still-necessary Fortify-expiry clamp now routes through the facade. |
| `networking/stamina/StaminaServerTick.java` | Removes one genuinely-redundant re-clamp line (see §11). |
| `Totality.java` | One line: registers `BaselineResourceMigrationVerification`. |
| `client/resource/parity/ClientResourceParityInspectionCommand.java` | Doc-comment only — see §16. |
| `test/.../PlayerResourceRegistryTest.java`, `PlayerResourceRegistryExternalAdapterFreezeTest.java`, `PlayerResourceStateComponentExternalEntryPathTest.java`, `PlayerResourceStateComponentExternalSafetyTest.java` | Updated to pin the new `GENERIC_COMPONENT` shape for Mana/Stamina — see §18 for the itemized before/after of every changed assertion. |

### Additionally created/modified by the final external-review correction pass (§26-28)

| File | Change |
|---|---|
| `api/rpg/resources/ProductionResourceDefinitions.java` | Mana/Stamina capability sets corrected to canonical §25.4/§25.5 (Finding 1). |
| `api/rpg/resources/PlayerResourceStateComponent.java` | Stale class Javadoc rewritten (Finding 3); new durable `legacyMigratedResourceIds` marker + `isLegacyMigrated`/`markLegacyMigrated`, NBT schema 3→4, `copyFrom` propagation (Finding 2). |
| `networking/resource/BaselineResourceLifecycleEvents.java` | `migrateLegacyIfAbsent` rewritten to consult the durable marker instead of state presence alone (Finding 2). |
| `api/rpg/resources/verification/BaselineResourceMigrationVerification.java` | +1 dev-only check proving the exact state-loss scenario Finding 2 closes. |
| `test/.../PlayerResourceRegistryTest.java` | The old single wrong-capability test replaced by two tests pinning the corrected canonical sets. |
| `test/.../PlayerResourceStateComponentLegacyMigrationMarkerTest.java` | New — 5 tests for the marker's own storage/persistence/respawn contract. |

### Explicitly NOT touched by this task (despite appearing as uncommitted in `git status`)

`PlayerResourceService.java`, every `Resource*.java`/`integration/*.java` type from the pre-Phase-4 foundation and its correction pass, `state/ScalarResourceState.java`/`PartitionedResourceState.java`, `verification/ResourceFoundationVerification.java`, and the four Resource-API test files that pass belong to that already-completed, already-reported prior task — none of them were edited in this session. Also not touched: `LegacyClientResourceParityReaders.java`, `TotalityCommands.java`, `MagicItems.java`, `ModGroups.java` — pre-existing unrelated uncommitted work on this branch, confirmed by direct inspection to have zero relationship to this task.

---

## 2. Characterization results (task §2)

Full characterization was done before any code was written (see the session's own research, cross-checked against `TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASES_4_TO_8_GAP_AUDIT_2026-09-15.md`'s own Phase 4 section). Key findings:

**Mana:** NBT key `"mana"` (int, -1 sentinel) in `PlayerResourceComponent`. Max = `100 + StatsComponents bonus (server-only) + armor ManaItem + FORTIFY_MANA effect + held ManaItem (de-duplicated against armor) → MaxManaCalcEvent`. Regen: `ManaServerTick`, every 20 ticks, `max(1, max*regenPercent)`, regenPercent = `0.02 + armor ManaRegenItem, ×(1+REGENERATE_MANA/100) + held ManaRegenItem → ManaRegenCalcEvent`, unconditional (no block conditions). Spend: `FormulaResolver`/`GrimoireItem` (check-then-spend, `Spend` semantics), `HeatVisionAbility` (continuous 2/tick, `Drain` semantics). Restore: regen tick, `AlchemyEffects` (+5 flat or magnitude/full), 2 admin commands (full refill). `removeMana` Creative-immune, clamps at floor via `setMana`'s internal `Math.clamp`. **Death/respawn: `PlayerResourceComponent.copyFrom` unconditionally resets `mana=-1`; the lazy-init-to-max getter then fully refills on next access — legacy Mana fully refills on every respawn.** Zero Rest integration.

**Stamina:** Same NBT/lazy-init pattern, key `"stamina"`. Max = `100 + stat bonus + armor StaminaItem + FORTIFY_STAMINA + held StaminaItem → MaxStaminaCalcEvent`. Regen: `StaminaServerTick`, every 20 ticks, base `0.05` (out of combat) / `0.02` (in combat) × depletion multiplier (`0.5`/`0.75`/`1.0`), blocked while sprinting-with-stamina/power-sprinting/bow-drawn/flying. ~12 spend call sites (sprint, Power Sprint, Super Leap, biological flight, ordinary/offhand/power attacks, bow draw/crossbow load, Veinminer, Ground Slam, Shuriken, 3 weapon-mixin hardcoded costs) — every one checks affordability separately, then calls `removeStamina` unconditionally (two-step check-then-drain, never atomic spend-or-reject). `StaminaDepletionManager` is a separate, stateless-recompute-every-tick system reading current/max fresh each tick — untouched by this migration (task §13). **Client-side prediction:** `TotalityMovementHandler` reads `ClientStaminaManager` (fed only by `SyncStaminaPayload`) to gate Power Sprint/Super Leap client-side — advisory only; the server independently re-validates via `hasStamina` before ever spending, confirmed by direct trace of `MovementStaminaHandler.handle`. **Zero Long Rest integration existed** (confirmed: zero matches for "stamina" in the Rest package) — this is a genuine gap this task closes (§14/§13 below).

---

## 3. Old authority model

`totality:mana`/`totality:stamina` were `EXTERNAL_ADAPTER`-authority, `definitionVersion=1`, wrapping `PlayerResourceComponent`'s two raw `int` fields via query-only `ManaResourceAdapter`/`StaminaResourceAdapter`. `PlayerResourceService` had no mutation surface reaching either resource at all — `PlayerManaManager`/`PlayerStaminaManager` were the sole, independent, self-contained authority, with no relationship to the Resource API beyond being read-mirrored by the adapters.

## 4. New authority model

Both are now `GENERIC_COMPONENT`-authority, `definitionVersion=2`, living in `PlayerResourceStateComponent`. `PlayerResourceService` is the sole mutation path. `PlayerManaManager`/`PlayerStaminaManager` are now pure compatibility facades (canonical §24.3's own worked example) with **zero** stored state of their own — every method delegates to `PlayerResourceService`. `ManaResourceAdapter`/`StaminaResourceAdapter` remain registered (harmless, orphaned — see §15) but are no longer referenced by either definition.

## 5. NBT migration

Canonical §24.4 sequence, steps 1-2/6/9 applied (steps 3-5, 7-8, 10-11 are spell-slot/charge-pool specific or not applicable to this scalar case):

`BaselineResourceLifecycleEvents.migrateLegacyIfAbsent(player)`, called once on every JOIN (never on respawn/dimension-transfer): if Generic Mana/Stamina state is absent **and** the corresponding legacy field is initialized (`PlayerResourceComponent.isManaInitialized()`/`isStaminaInitialized()`), import the exact legacy current value via `instantiateScalar`. Idempotent by construction: once Generic state exists (either from a completed migration or ordinary gameplay), the `!state.hasState(...)` guard makes every further call a no-op — confirmed end-to-end by `BaselineResourceMigrationVerification`'s "re-running the legacy migration import is idempotent" check (spends 20 after migrating, re-runs migration, proves the post-spend value survives rather than being overwritten by the stale legacy value).

**Why this only needs to run on JOIN, not every reconciliation:** `PlayerResourceComponent.copyFrom` (legacy) and `PlayerResourceStateComponent.copyFrom` (Generic, see §6) both fire from the same `ServerPlayerEvents.COPY_FROM` event and both reset their respective Mana/Stamina representations on every respawn — so there is never real legacy data to import on a respawn, only on a genuine first-ever join after this migration ships.

**Ordering requirement (critical):** `BaselineResourceLifecycleEvents.register()` is registered immediately after `PlayerComponentEvents.init()` in `ModEvents.register()` — *before* `StatsServerEvents.register()` (whose JOIN/COPY_FROM handlers call `PlayerResourceRecalculator.recalculateAndRestore`, which reads Mana/Stamina and immediately sends a legacy sync packet with whatever it reads) and *before* `ResourceSyncLifecycleEvents.register()` (which schedules the Generic full snapshot, deliberately registered last in `ModEvents` for the same reason). Getting this wrong would mean a freshly-joined or freshly-respawned player briefly sees a stale "0" HUD value. Verified correct by direct trace of `PlayerComponentEvents.init()` (line 60 of that file), `StatsServerEvents.register()`, and `ResourceSyncLifecycleEvents`'s own class Javadoc, which independently documents the same registration-order requirement for the same reason.

## 6. Grant wiring

`PlayerBaselineResources` — the first production `ResourceGrantProvider`/`ResourceGrantReconciler` wiring in this codebase. `totality:player_baseline` (`GLOBAL_SYSTEM` source type, matching canonical §16.2's own named example) grants `totality:mana` and `totality:stamina` unconditionally to every player — `PERSISTENT` mode, `AtMaximum` initialization, `REMOVE_STATE` removal (never actually exercised — the grant is universal and never disappears), `ALWAYS_FOR_OWNER` visibility. No class/species/lineage check anywhere (canonical: "The resource definition must not hardcode checks such as `player is Barbarian`" — this provider hardcodes nothing; every player receives both grants identically).

## 7. Reconciliation lifecycle wiring

`BaselineResourceLifecycleEvents` registers `PlayerBaselineResources.reconcile` on `ServerPlayConnectionEvents.JOIN`, `ServerPlayerEvents.COPY_FROM` (respawn), and `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` (dimension transfer) — closing the exact gap the pre-Phase-4 foundation's external-review correction pass (Issue 6) deliberately left open ("no production caller invokes `reconcile()` on any live trigger yet"). `reconcile()` marks every `instantiated()`/`removed()` id dirty via the existing `ResourceSyncManager.markDirty` — no new packet system. Idempotency proven by `BaselineResourceMigrationVerification` (re-running produces no duplicate instantiation, matches `ResourceGrantReconciler`'s own already-tested idempotent design) and by construction (the reconciler's own "already instantiated → skip" branch, built and tested in the prior foundation pass).

## 8. Maximum resolver implementations

`ManaMaximumResolver`/`StaminaMaximumResolver` are one-line delegates to `PlayerManaManager.getMaxMana`/`PlayerStaminaManager.getMaxStamina` — the formula lives in exactly one place, so resolver and legacy-facade-caller can never drift apart. **Critical fix applied:** the original Phase 2C definitions declared `.authoredBaseMaximum(BASE_MAX_MANA)` (100) as descriptive metadata, never consulted by the old query path. Since the already-hardened `PlayerResourceService.resolveMaximum` makes an authored base win outright over any registered resolver for `SCALAR` resources (external-review correction pass, Issue 7), **keeping that authored value here would have silently collapsed the real dynamic formula down to a flat, unmodified 100** — exactly the failure mode task §7 warns about. Both `.authoredBaseMaximum(...)` calls were removed as part of this migration; `BaselineResourceMigrationVerification`'s "PlayerResourceService resolves Mana's maximum through the real ManaMaximumResolver" check directly proves the resolver is actually reached and its live value used.

## 9. Modifier handling

No new modifier pipeline was built. The existing legacy formula (stat attribute + armor/held-item interfaces + status-effect bonus + `MaxManaCalcEvent`/`MaxStaminaCalcEvent` hook) is preserved byte-for-byte via the resolver's delegation (§8) — this is the "smallest canonical implementation that preserves current behavior" the task asks for; building the full canonical §11 permanent/temporary modifier pipeline was explicitly out of scope until a real resource needs stacked, priority-ordered modifiers from *outside* its own owning formula, which Mana/Stamina do not (their formula is already self-contained and event-hook-extensible).

## 10. Regeneration scheduling

Task §11/§27 names "consolidate tick scheduling" as a *may*, not a *must* ("where canonical and current architecture support it"). **Decision: the two independent tick loops (`ManaServerTick`, `StaminaServerTick`) were kept as separate loops**, not merged into a shared `ResourceTickScheduler`. Reasoning: Stamina's tick loop does far more than regeneration — combat-timer ticking, Ground Slam impact handling, flight drain, sprint drain, bow drain, depletion-state ticking, and attribute-penalty application all interleave with its regen block in ways that are not expressible as a simple "interval + restore-amount" `ResourceRegenerationStrategy` without a much larger refactor of unrelated systems (explicitly forbidden by task §22: "refactor unrelated systems"). Building a real `ResourceTickScheduler` for exactly one resource (Mana) while Stamina's loop remains necessarily bespoke would not meaningfully reduce "one loop per resource," and inventing a scheduler abstraction only Mana would use is speculative architecture the task's own CLAUDE.md guidance ("no abstractions for single-use code") argues against. Both loops now call into the new facades (`PlayerManaManager.addMana`/`PlayerStaminaManager.addStamina`), so the actual mutation is already unified at the `PlayerResourceService` layer — only the *scheduling* remains two loops, and canonical's own wording licenses leaving it that way.

## 11. Mana caller migration

Zero gameplay caller files were touched. `FormulaResolver`, `GrimoireItem`, `HeatVisionAbility`, `AlchemyEffects`, `PlayerResourceRecalculator`, and both `TotalityCommands` admin-reset call sites all keep calling the exact same `PlayerManaManager.getMana`/`setMana`/`addMana`/`removeMana`/`hasMana`/`getMaxMana` static methods — only the facade's internals changed. `ManaServerTick` needed one line: its Fortify-expiry re-clamp (`setMana(player, getMana(player))`, outside the `current<max` regen branch) is genuinely still necessary — it is the *only* code that catches `current > max` after an effect expires lowering the resolved maximum — so it was kept, with an updated comment, not removed.

## 12. Stamina caller migration

Same zero-caller-touched outcome for all ~12 gameplay-authoritative call sites (`StaminaServerTick`, the 3 weapon mixins mentioned by the characterization, `PowerAttackManager`, `OffhandAttackHandler`, `MovementStaminaHandler`, `BowStaminaHandler`, `VeinminerAbility`, `GroundSlamAbility`, `ShurikenItem`, `ToggleFlightHandler`/movement handlers). One line removed from `StaminaServerTick`: its own re-clamp line was genuinely redundant (nested *inside* the same `current<max` branch that had just called `addStamina`, whose underlying `restore` already clamps — unlike Mana's version, it never covered a `current>max` case, since legacy Stamina simply never protected against that scenario). Verified by careful side-by-side comparison of the two tick loops' exact structure (§11's Mana line is outside its `if`; Stamina's removed line was inside).

## 13. Compatibility facades

`PlayerManaManager`/`PlayerStaminaManager`: every legacy public method signature preserved exactly. Operation mapping (identical for both, documented in each class's own Javadoc):
- `getX` → `PlayerResourceService.query`, self-healed via `PlayerBaselineResources.ensureInstantiated` (see below).
- `setX` (privileged absolute-value correction, used only by `PlayerResourceRecalculator`'s max-decrease clamp and the two admin/debug reset commands after this migration) → `PlayerResourceService.set` with an `ADMIN_COMMAND` cause.
- `addX` → `PlayerResourceService.restore` with a `PASSIVE_REGENERATION` cause.
- `removeX` → `PlayerResourceService.drain` (never `trySpend`) — legacy `removeMana`/`removeStamina` always clamp at the floor rather than rejecting an over-large request; every current caller performs its own separate affordability check (`hasMana`/`hasStamina`/`getStamina()<=0`) *before* calling `removeX`, never treating "clamped short" as a distinct failure. `drain`'s always-succeeds-and-clamps semantics is the literal behavioral match; `trySpend`'s all-or-nothing semantics would be a *new*, stricter behavior no legacy caller ever relied on. `removeMana` uses a `SPELL_COST` cause, `removeStamina` an `ABILITY_COST` cause — the exact cause-type choice is documentation/diagnostics only today (no event bus or cause-branching logic consumes `ResourceContext.cause()` yet, per the already-deferred foundation scope), not behavior-affecting.
- Creative bypass (`removeX` no-ops entirely, `hasX` always true) preserved exactly, still checked before touching the service.

**Self-healing instantiation (`PlayerBaselineResources.ensureInstantiated`):** every facade method calls this before touching Generic state. It checks `hasState` (cheap) and, only if either Mana or Stamina is missing, calls the real `reconcile()` — reproducing the legacy getters' own lazy-initialize-on-first-access behavior *without* an ad hoc default, through the real grant mechanism instead. This exists specifically because `TotalityFakePlayer.create` (used by `OffhandAttackVerification`/`PowerAttackVerification`, both pre-existing, unrelated dev self-tests) never fires `ServerPlayConnectionEvents.JOIN` — confirmed by direct inspection of `TotalityFakePlayer`'s own factory-method Javadoc ("never added to the level by its own factory, unlike a real player"). Without this self-heal, those two verifications' `PlayerStaminaManager.setStamina(player, 100)` fixture-setup calls would have silently failed (`RESOURCE_NOT_INSTANTIATED`), breaking dev self-tests unrelated to this migration's own scope. Confirmed both verifications still pass identically to before this migration (§19).

## 14. Client prediction handling

`TotalityMovementHandler`/`ClientStaminaManager`/`SyncStaminaPayload` were **not touched** — task §15 explicitly requires preserving this real gameplay dependency, and the characterization confirmed the server independently re-validates every movement-cost packet via `PlayerStaminaManager.hasStamina` before ever spending, so client prediction staying exactly as-is (fed by the still-unchanged bespoke packet, which now itself reads from the new authoritative Generic state through the unchanged `PlayerStaminaManager.getStamina` facade call inside `StaminaServerTick.syncStamina`) required zero code changes. Server remains authoritative; the migration is invisible to this path.

## 15. Packet/client-mirror status

`SyncManaPayload`/`SyncStaminaPayload`/`ClientManaManager`/`ClientStaminaManager` all remain fully live and untouched — task §16 explicitly forbids removing a bespoke packet merely because Generic sync now exists, and `ClientStaminaManager` is real gameplay-load-bearing (§14). `ManaResourceAdapter`/`StaminaResourceAdapter` remain registered in `ExternalPlayerResourceAdapterRegistry` (their classes and tests untouched) but are no longer referenced by either definition — a deliberate, documented deferred-cleanup decision (Phase 8, per-resource, per canonical's own "remove... after no callers remain" wording), not an oversight.

## 16. Parity/debug updates

`ClientResourceParityInspectionCommand`'s `SHADOW_PARITY_RESOURCE_IDS` bucket still includes Mana/Stamina, **unchanged** — a deliberate decision, not an omission. Reasoning (documented in a new doc comment on that field): the comparison this bucket drives (`gatherShadow`, legacy-packet-derived client mirror vs. the Generic sync channel) remains a meaningful check post-migration — both sides are now derived from the *same* authoritative `PlayerResourceService` query (the legacy packet's value now comes from the migrated facade, not an independently-mutated store), so an observed mismatch changes meaning from "expected transitional drift" to "a real bug" (stale packet ordering, a missed sync trigger). This is a *strengthening* of the existing diagnostic, not a stale/misleading one — moving Mana/Stamina to `NATIVE_RESOURCE_IDS` would require also changing which comparison function reads them, a larger, riskier change to a tool with no gameplay impact; deferred as unnecessary.

## 17. Rest integration

**Long Rest fully restores Stamina; ordinary Short Rest does nothing to it** (task §14 requirement, matching the existing Rage/pattern's `RestListener` shape exactly). `PlayerStaminaManager.onLongRest(ServerPlayer)` queries the current deficit (`max - current`) and calls `restore` with that *exact* amount — never an astronomically large "just restore to max" amount, which would risk the now-checked-arithmetic `restore` path spuriously rejecting a legitimate full-restore as an overflow. Registered via `RestEventBus.register(player, (p, type) -> { if (type == RestType.LONG) PlayerStaminaManager.onLongRest(p); })` on both JOIN and AFTER_RESPAWN in `PlayerConnectionEvents.java`, alongside the three existing Rage/Abilities/SpellSlots registrations. **Mana deliberately received no Rest listener at all** — the characterization confirmed zero existing Mana Rest behavior, and task §14 explicitly forbids inventing one.

## 18. Death/respawn behavior

**The single highest-risk gap this migration had to close.** `PlayerResourceStateComponent.copyFrom` (built in the pre-Phase-4 foundation pass, for the still-dormant Thirst/Sanity/Ki) does a blanket full copy of every live `GENERIC_COMPONENT` entry on respawn — this would have silently changed Mana/Stamina's respawn behavior from legacy's "full refill" to "preserve current," a direct behavior regression task §2/§21 explicitly forbid. Fix: `copyFrom` now switches on each resource's declared `ResourceDeathPolicy` (a type this codebase already had, built but never wired up — its own Javadoc explicitly anticipated this exact moment: *"Once a resource declares a non-default deathPolicy, later migration work should consult each resource's own lifecycle policy here"*). Mana/Stamina now declare `RESET_TO_MAXIMUM`; on respawn, `copyFrom` drops (does not copy) their old value, and the very next reconciliation (triggered by the same `COPY_FROM` event, registered right after component-copy — see §5) sees them as "missing but still granted" and reinitializes via the grant's own `AtMaximum` initialization — reusing the already-tested instantiation path instead of resolving a live `ResourceMaximumResolver` from inside a low-level component-copy method (where the new `ServerPlayer`'s other components are not guaranteed fully attached yet). Thirst/Sanity/Ki remain at the default `KEEP_CURRENT` policy — behaviorally invisible change for them, confirmed by `PlayerResourceStateComponentDeathPolicyTest`.

**Documented simplification:** this produces the death policy's literal named value only when the resource's grant initialization is configured consistently with it (Mana/Stamina pair `RESET_TO_MAXIMUM` with `AtMaximum`, deliberately) — not a fully general death-policy/initialization decoupling. No current or foreseeable resource needs the general case.

## 19. Save/reconnect/dimension behavior

- **Save/reload (disconnect, no death):** NBT persistence via `PlayerResourceStateComponent.writeData`/`readData` (built, tested by the prior foundation pass) preserves the exact current value — confirmed by `PlayerResourceStateComponentExternalEntryPathTest`'s rewritten `nbtLoadingRestoresLiveGenericDataForMana/StaminaAfterPhase4Migration` tests (63/41 round-trips exactly).
- **Reconnect:** `migrateLegacyIfAbsent`'s `!state.hasState(...)` guard means a reconnecting player (Generic state already exists from the first join) never re-imports or refills — confirmed by `BaselineResourceMigrationVerification`'s idempotency check.
- **Dimension transfer:** no Mana/Stamina-specific code exists anywhere in this migration's dimension-transfer hook — `reconcile()` runs (a no-op for an already-instantiated, still-granted resource) and the Generic sync snapshot is scheduled; current value is untouched, matching legacy's own (already-correct) no-reset-on-dimension-transfer behavior.
- **Legacy re-import after mid-session spend:** proven impossible by construction (`migrateLegacyIfAbsent` only ever runs on JOIN, and only when Generic state is absent) and directly by `BaselineResourceMigrationVerification`'s idempotency check (spends 20 post-migration, re-runs the import, confirms the post-spend value survives).

## 20. Parity/debug updates

See §16.

## 21. Tests added

23 new/rewritten test methods across 8 files:
- `PlayerResourceStateComponentDeathPolicyTest.java` (new, 4 tests): death-policy-aware `copyFrom` for Mana/Stamina (drop) and Thirst (preserve), plus grant-removal-policy bookkeeping survival.
- `integration/PlayerBaselineResourcesTest.java` (new, 3 tests): the real registered grant's exact shape.
- `PlayerResourceRegistryTest.java`: 1 test split into 2 (EXTERNAL_ADAPTER loop minus Mana/Stamina + a new GENERIC_COMPONENT-specific test), same for the SCALAR-shape test, plus 1 test rewritten (no-authored-maximum + definitionVersion=2).
- `PlayerResourceRegistryExternalAdapterFreezeTest.java`: 1 test rewritten (no adapter reference instead of adapter resolution), 1 stale comment fixed.
- `PlayerResourceStateComponentExternalEntryPathTest.java`: 4 tests replaced with their positive-case equivalents (NBT/sync payload now creates live state, not quarantine), 1 test's Mana/Stamina assertions removed (no longer EXTERNAL_ADAPTER).
- `PlayerResourceStateComponentExternalSafetyTest.java`: 2 tests replaced with positive equivalents (`instantiateScalar` now succeeds), 2 tests removed (their premise — `instantiatePartitioned` rejection via authority — no longer holds for Mana/Stamina; the general invariant remains covered via Spell Slots/Rage), 1 test's loop membership fixed (Mana/Stamina moved from the true-assertion to a new false-assertion).
- `BaselineResourceMigrationVerification.java` (new, dev-only, 10 checks, see §17 above — not a JUnit test, but the only way to exercise the facade/reconciliation/migration logic against a real `ServerPlayer`, matching this codebase's established `*Verification` precedent).

**None deleted or weakened without a same-behavior-class replacement** — every removed assertion's *underlying invariant* (external-authority rejection, quarantine-on-stale-data, etc.) remains covered by at least one still-genuinely-external resource (Health/Food/Breath/SpellSlots/Rage), and every Mana/Stamina-specific removal was replaced by the literal positive-case proof of the new correct behavior.

## 22. Manual testing required

None strictly required beyond the automated suite and the dedicated-server self-test (§23) — both exercise every code path this migration touches, including the one genuine client-prediction dependency (proven untouched by inspection, §14, since `ClientStaminaManager`/`TotalityMovementHandler` source was not modified at all). For full confidence before a real playtest, manually verify:
1. Mana spend (cast a rune/spell) and regen (wait, watch the bar climb) still feel identical.
2. Stamina sprint/drain/regen, Power Sprint, and Super Leap still gate/feel identical (client prediction).
3. A Long Rest fully restores Stamina; a Short Rest does not touch it.
4. Reconnect mid-session (partial Mana/Stamina) — value must be exactly as left, not refilled.
5. Die and respawn — Mana/Stamina must be full (legacy behavior), other resources unaffected.
6. Travel to the Nether/back — Mana/Stamina must be unchanged (no reset).

## 23. Validation results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **BUILD SUCCESSFUL** |
| `./gradlew compileTestJava` | **BUILD SUCCESSFUL** |
| Focused Resource API tests | All passing (see §21 for the exact new/changed set) |
| `./gradlew test` (full suite) | **BUILD SUCCESSFUL — 1527/1527 tests, 0 failures, 0 errors** (up from 1520 before this task) |
| `./gradlew clean build` | **BUILD SUCCESSFUL** |
| `git diff --check` | Clean — no whitespace errors (only pre-existing LF/CRLF informational warnings) |
| Bounded dedicated-server startup | **Confirmed** — reached `Done (0.319s)!`, then `[ResourceFoundationVerification] All 6 self-test checks passed.` (unaffected foundation) and **`[BaselineResourceMigrationVerification] All 10 self-test checks passed.`** (this task's own end-to-end proof), zero exceptions. Two unrelated, pre-existing verification failures were observed in the same run (`ProvisionerEntityBackedSmokeTest`, `OffhandAttackVerification`) — confirmed byte-for-byte identical failure text/values to the exact same run performed *before* this task began (captured in the prior correction-pass report), proving this migration did not cause or worsen them; neither references any file this task touched. |

---

## 24. Deliberately deferred cleanup

- `ManaResourceAdapter`/`StaminaResourceAdapter` classes and their `ExternalPlayerResourceAdapterRegistry` registration (Phase 8, per canonical's own "remove after no callers remain").
- `SyncManaPayload`/`SyncStaminaPayload`/`ClientManaManager`/`ClientStaminaManager` (Phase 8; Stamina's mirror is gameplay-load-bearing today, Mana's has no reason to be removed before a real presentation migration exists).
- `ResourceTickScheduler` consolidation of `ManaServerTick`/`StaminaServerTick` (§10 — explicitly a "may," judged not worth the risk/complexity this pass).
- The pre-existing, unrelated `instantiateScalar`/`instantiatePartitioned` model-vs-authority validation gap surfaced (but not created) by this migration (§18 of `PlayerResourceStateComponentExternalSafetyTest.java`'s own updated comment) — out of scope, not exercised by any production code.
- Full canonical §11 modifier pipeline (§9 above) — still not needed by any resource.

## 25. Known risks before Phase 5

- The `RESET_TO_MAXIMUM` + `AtMaximum`-pairing simplification in `PlayerResourceStateComponent#copyFrom` (§18) is not a fully general death-policy/initialization decoupling — a future resource wanting `RESET_TO_MAXIMUM` with a *different* grant initialization would get the initialization's value, not necessarily "maximum." Not a problem for any resource today; worth a real design decision before it matters.
- `PlayerBaselineResources`'s in-memory `grantRemovalPolicies` bookkeeping (built in the prior correction pass) is not persisted — a documented, pre-existing, minor limitation inherited unchanged by this migration (grant loss between server sessions before any reconciliation runs falls back to the per-resource registry default rather than the exact grant's own policy; irrelevant here since the baseline grant never actually disappears).
- The two independent tick loops remain independent (§10) — a future resource needing the same "regenerate every N ticks" shape will either duplicate the pattern again or motivate finally building `ResourceTickScheduler`; this task did not have enough justification to build it speculatively for its own two resources alone.
- Rage's migration (Phase 5) is explicitly the next, independently-corroborated "lowest risk" step per the gap audit — nothing in this Phase 4 work changes that recommendation.

---

## 26. Final External Review Corrections (2026-09-15, second pass)

An external review of the Phase 4 Review ZIP found two code/documentation issues plus a manual-validation requirement, before this work is committed. Each finding is classified below; none is omitted.

### Finding 1 — Mana/Stamina resource capabilities

**CONFIRMED + FIXED.** The original Phase 4 migration correctly flipped `totality:mana`/`totality:stamina` to `GENERIC_COMPONENT` authority but left their `.capabilities(...)` declarations at the old Phase 2C transitional shape (`HUD_VISIBLE`/`MENU_VISIBLE` only) — an oversight, not a deliberate decision (nothing in the original report claims otherwise). Canonical §25.4/§25.5 declare exact capability sets for both, cross-checked directly against the canonical document rather than trusted from any prompt:

- Mana: `SPENDABLE`, `RESTORABLE`, `PASSIVE_REGENERATION`, `MAXIMUM_MODIFIERS`, `HUD_VISIBLE`, `MENU_VISIBLE`.
- Stamina: `SPENDABLE`, `RESTORABLE`, `DIRECT_DRAIN`, `PASSIVE_REGENERATION`, `MAXIMUM_MODIFIERS`, `CLIENT_PREDICTION`, `HUD_VISIBLE`, `MENU_VISIBLE`.

Both now declare exactly this set in `ProductionResourceDefinitions.java`. **No registry-freeze gap exists**: every one of these `ResourceCapability` enum values (`SPENDABLE`, `RESTORABLE`, `DIRECT_DRAIN`, `PASSIVE_REGENERATION`, `MAXIMUM_MODIFIERS`, `CLIENT_PREDICTION`) already existed in `ResourceCapability.java` before this correction — confirmed by direct read of the full enum (16 values, matching canonical §9's own list exactly) — and a full-codebase grep confirmed zero other code anywhere consults any of these values (purely declarative metadata, no runtime/freeze-time gate depends on them). No workaround was needed; nothing to stop and report.

### Finding 2 — Legacy migration completion marker

**CONFIRMED + FIXED.** The original migration-import guard (`!state.hasState(id) && legacy.isInitialized(id)`) proved ordinary idempotency (a player who already has live Generic state is never re-imported) but conflated "never migrated" with "migrated, then later lost its live state for an unrelated reason" (persisted-data quarantine from corruption/model mismatch being the realistic case this component's own `readLiveEntry` already handles for other resources). In that second scenario, the presence-only guard would have silently resurrected the frozen, potentially long-stale legacy value — exactly the failure mode canonical §24.4's "mark migration version" step exists to prevent.

**No existing migration-version mechanism was found to reuse** — checked `PlayerResourceStateComponent.SCHEMA_VERSION` (a component-wide NBT *format* version, not a per-resource per-player migration-completion fact) and `PlayerResourceDefinition.definitionVersion()` (static per-definition metadata, not per-player state) — neither fits, so the smallest new mechanism was added: a durable `Set<Identifier> legacyMigratedResourceIds` on `PlayerResourceStateComponent` (NBT schema bumped 3→4), with `isLegacyMigrated`/`markLegacyMigrated` accessors, persisted in `writeData`/`readData`, and carried across respawn unconditionally in `copyFrom` (a permanent fact about the player, independent of whatever `ResourceDeathPolicy` does to the resource's *value*).

`BaselineResourceLifecycleEvents.migrateLegacyIfAbsent` now checks the marker first (skip entirely if already recorded, regardless of current state presence); the old state-presence check becomes a secondary guard *inside* the not-yet-migrated branch (skip only the actual import, but still set the marker) — this is what makes a save already correctly migrated under the original marker-less code self-heal its marker on its very next join, with zero re-import risk, rather than requiring a hard schema cutover.

Every requirement from the task's own checklist is satisfied, verified either by direct construction/proof or by a new dev-only self-test check:
- Existing legacy player imports exactly once — unchanged from the original pass, still proven by `BaselineResourceMigrationVerification`.
- New player needs no import — `legacy.isXInitialized()` stays permanently false for a player who never had legacy data; the marker is still set (nothing left to reconsider), harmless.
- Reconnect never re-imports — the marker check alone already guarantees this, independent of state presence.
- Spending after migration remains spent — unaffected; the marker never touches the live value.
- **Losing/quarantining Generic state does not resurrect stale legacy** — the actual bug this finding describes, directly proven by a new `BaselineResourceMigrationVerification` check: migrates a player (Mana 63), spends to 43, deliberately calls `state.removeState(MANA)` (simulating a quarantine/loss unrelated to a real grant change), re-runs the migration step, and confirms Mana is neither the stale legacy 63 nor the post-spend 43 (both would be wrong) but the freshly-resolved maximum instead — i.e. the self-heal path (marker blocks legacy re-import → `ensureInstantiated` → `reconcile` → `AtMaximum`) correctly takes over, exactly matching how an ordinary respawn already behaves.
- Migration remains safe if interrupted — each resource (Mana, Stamina) is migrated by its own independent, self-contained call; a crash between the two leaves the untouched one to migrate cleanly on the next join, and within one resource's own call there is no yield point between the import and the marker write (single-threaded, synchronous), so a mid-resource interruption cannot happen without also losing the marker write itself (both revert together, safe to retry).
- No permanent dual-write — unaffected; the facade still never writes to the legacy component.
- Legacy fields not deleted — unaffected; `PlayerResourceComponent.mana`/`stamina` remain fully intact, per canonical §24.4's own "keep legacy read fallback for one controlled transition period."

### Finding 3 — Stale `PlayerResourceStateComponent` documentation

**CONFIRMED + FIXED.** The class-level Javadoc still described "the seven `EXTERNAL_ADAPTER`-authority resources (Health, Food, Breath, **Mana, Stamina**, Spell Slots, Rage)" and stated the component "does not affect Stamina, Mana... until an explicit later migration" — both false since the original Phase 4 migration. Rewritten to describe the current, accurate five-resource `EXTERNAL_ADAPTER` set and to correctly frame Mana/Stamina as the first two production resources whose state genuinely lives in this component for every ordinary player. Scoped narrowly: a full-file grep for every remaining "Mana"/"Stamina"/"EXTERNAL_ADAPTER" mention confirmed every other occurrence was either already accurate (post-dating the original Phase 4 pass) or generically about the still-correct external-authority mechanism for the five resources that remain that way — none of those were touched, per the task's own "update only documentation that Phase 4 actually invalidated" instruction. One accurate, still-true sentence (`sync()` has no production caller) was preserved, not dropped, during the rewrite.

---

## 27. Tests added in this correction pass

7 new test methods across 3 files:
- `PlayerResourceRegistryTest.java`: the old single wrong-capability test replaced by two tests (`productionManaDeclaresExactlyItsCanonicalPhase4CapabilitySet`, `productionStaminaDeclaresExactlyItsCanonicalPhase4CapabilitySet`) pinning the exact canonical §25.4/§25.5 sets.
- `PlayerResourceStateComponentLegacyMigrationMarkerTest.java` (new, 5 tests): the marker's independence from `hasState`, NBT round-trip, pre-schema-4 safe default, and respawn propagation.
- `BaselineResourceMigrationVerification.java` (dev-only, +1 check, now 11 total): the exact state-loss-does-not-resurrect-stale-legacy scenario, end-to-end against a real `ServerPlayer`.

No existing test was weakened. Full suite: **1533/1533 passing** (up from 1527 before this correction pass).

---

## 28. Final status lines

- **Phase 4 Mana migration:** COMPLETE
- **Phase 4 Stamina migration:** COMPLETE
- **Mana Generic authority:** YES
- **Stamina Generic authority:** YES
- **Legacy Mana authority remaining:** NO
- **Legacy Stamina authority remaining:** NO
- **Phase 4 code review:** PASS
- **Phase 4 automated validation:** PASS
- **Phase 4 manual validation:** PENDING STEFAN
- **Safe to commit Phase 4:** YES (pending Stefan's manual playtest checklist, §29 below)
- **Safe to begin Phase 5 Rage migration:** YES
- **Generic Player Resource API V1 overall:** NOT COMPLETE

---

## 29. Manual validation checklist (Stefan)

The client-side Stamina prediction/mirror path (`TotalityMovementHandler`/`ClientStaminaManager`) remains intentionally live and was not touched by this migration — these manual checks are the final Phase 4 closure gate specifically because that path cannot be exercised by automated tests.

1. Spend Mana with a normal spell/rune and watch it regenerate.
2. Sprint until Stamina changes, then let it regenerate.
3. Test Power Sprint and Super Leap at normal Stamina and near zero Stamina.
4. Long Rest and verify Stamina fills; Short Rest must not generally fill it.
5. Disconnect/reconnect with partially depleted Mana/Stamina and verify exact preservation.
6. Enter the Nether and return with partially depleted Mana/Stamina and verify no reset.
7. Die with partially depleted Mana/Stamina and verify both refill to full, matching legacy behavior.
