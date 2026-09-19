# Totality Generic Player Resource API — Phases 4-8 Gap Audit

**Date:** 2026-09-15
**Branch:** feature/general-resource-api
**HEAD:** 73380f9 ("feat: add wearable Shinigami Robe"), plus one uncommitted working-tree fix from this same session (see §16)
**Scope:** Read-only audit. No production code was modified, no bugs were fixed, nothing was migrated or removed to produce this report.
**Primary authority:** `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` (2026-07-13, 4512 lines) — read in full. Cross-checked against `Context/Audit/TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md` for grant-model overlap, and against current source under `src/main/java/zcylas/totality/`.

---

## 1. Executive Summary

- **Actual current implementation phase: Phase 3 complete (including the 3B/3C sub-work), Phase 4 has not started.** The canonical roadmap is Phase 0 through Phase 8, not "Phase 3C then done." What the September audits called "Phase 3C" is the tail end of canonical Phase 3 ("Generic synchronization and client presentation"). Everything from Phase 4 onward — actually migrating Mana, Stamina, Rage, and Spell Slots to Generic-owned mutable state — has not begun.
- **Generic Player Resource API V1 is NOT implementation-complete**, and the gap is not cosmetic: `PlayerResourceService` (read in full this session) has **zero mutation methods** — no `trySpend`, `restore`, `drain`, `set`, or `transact`, only `query`. The canonical "one public query and mutation façade" is, today, a query façade only. Every actual spend/regen/persist/clamp operation for every resource that matters (Mana, Stamina, Rage, Spell Slots) still runs entirely through pre-existing legacy managers that have no awareness of the Generic API beyond a single harmless `markDirty` notification call each.
- **Largest gaps, in order of significance:**
  1. No mutation API exists on `PlayerResourceService` at all (Phase 1/4/5/6 foundation work).
  2. No `ResourceGrantProvider` system exists (canonical §16) — every current "who gets this resource" decision is legacy, ad-hoc code, not a Resource API concept.
  3. Mana, Stamina, and Rage have zero migration/import code toward `PlayerResourceStateComponent`, and — by design — structurally *cannot* gain one without first being redefined away from `EXTERNAL_ADAPTER` authority.
  4. Phase 7 (missing resources) has one scaffolding-only registration commit (Thirst/Sanity/Ki, post-dating 3C) that is out of the canonical's own dependency order (Hit Dice and Pact Magic, canonical's top two priorities, are untouched).
  5. Phase 8 cleanup has not started: every legacy packet, manager, and the canonical document's own named dead-code example (`PlayerChargesComponent.registerWithRestBus()`) is still present, unremoved.
- **Legacy client mirrors are still architecturally required today** — not because of nostalgia, but because (a) they remain the sole gameplay-authoritative source for two things (Stamina's client-side movement-gating prediction, and everything server-side for all four resources), and (b) the parity/debug tooling built in Phase 3B exists specifically to compare Generic-vs-legacy, so removing a legacy mirror before its resource actually migrates would blind that tooling for that resource. They stop being required only once the corresponding Phase 4/5/6 migration actually moves authority into `PlayerResourceStateComponent` — which is Phase 8's own stated precondition ("remove... after no callers remain").
- **The recent Rage dimension-sync bug should NOT be fixed with another lifecycle patch.** This is now the *second* missing-lifecycle-event bug found in the same legacy `PlayerChargesComponent` client mirror (JOIN, now fixed this session; dimension-change, still open) — both are the same underlying symptom: a hand-maintained legacy sync list that the newer, more robust Generic sync channel doesn't share the same weakness with (it already has explicit, correctly-ordered JOIN/respawn/dimension-change resync, built once in Phase 3A). A third patch would keep treating a symptom of duplicate authority rather than the cause. The canonical document's own words: "duplicate authoritative stores should not remain indefinitely." This bug is better read as evidence for prioritizing Phase 5 than as a standalone defect — see §16 and §14.

---

## 2. Canonical Intended Architecture

*(Extracted from `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` in full; section numbers refer to that document.)*

**Six-layer architecture (§3):** Definitions → State authority → Resolvers/strategies → Resource service → External change bridge → Presentation/sync.

- **`PlayerResourceRegistry`** (§5.2): registers `PlayerResourceDefinition`s; rejects duplicates/invalid scales; requires an adapter for `EXTERNAL_ADAPTER` resources; freezes before players load.
- **`PlayerResourceDefinition`** (§4.1): immutable, describes "what a resource **is**, not what one player currently has" — never per-player state.
- **`PlayerResourceStateComponent`** (§4.3): "One player component stores all internally backed resource instances" — `GENERIC_COMPONENT` resources only, never display metadata or cached derived values.
- **`PlayerResourceService`**: canonical diagram wording — **"the one public query and mutation façade."** Interface (§12.1): `snapshot`, `trySpend` (all-or-nothing by default), `restore`, `drain`, `set` (privileged only), `transact` (atomic multi-resource). "Transfer" is explicitly out of V1 scope.
- **`GENERIC_COMPONENT` vs `EXTERNAL_ADAPTER`** (§4.2): internally-backed resources (state lives in `PlayerResourceStateComponent`) vs. externally-backed (another system owns the state; Resource API only exposes/queries it, never duplicates it, never overrides owner authority).
- **Resolvers/strategies** (§9): `ResourceMaximumResolver`, `ResourceAvailabilityResolver`, `ResourceRegenerationStrategy`, `ResourceDeathHandler`, `ResourcePartitionDescriptor` — code-owned, registered by the owning system, referenced by ID.
- **Grants** (§16.2): `ResourceGrantProvider` interface (`getResourceGrants(ServerPlayer)`), `ResourceGrant` records (resourceId, sourceId, sourceType, mode, initialization, removalPolicy, visibilityPolicy, priority). Canonical rule (§4.5): "A player receives a resource only through an active `ResourceGrant` supplied by an authoritative owning system... Ungranted resources are not instantiated, regenerated, synchronized, displayed, or processed."
- **Maximum resolution** (§9.1, §10.2): one central resolution path — authored base → owning strategy → permanent modifiers → temporary modifiers → hard clamps → current-value reconciliation. "Managers must not recalculate maximum independently and then overwrite each other."
- **Mutation operations** (§12): Spend (intentional, affordability-checked) vs. Drain (external/ongoing) vs. Restore vs. Set (privileged/migration only) — distinct semantics, all routed through the service.
- **Persistence** (§17): `PlayerResourceStateComponent` persists only `GENERIC_COMPONENT` state; never computed maximums; respawn via `RespawnStrategy.ALWAYS_COPY` then per-resource death policy.
- **Migration** (§24.4): an explicit 11-step NBT import sequence (import Mana if absent → import Stamina → import every spell-slot tier → import charge pools by exact ID → recalculate maximums → reconcile only invalid values → "keep legacy read fallback for one controlled transition period" → **"do not indefinitely dual-write two authoritative stores"**).
- **Death/respawn lifecycle** (§17.2): `ResourceLifecyclePolicy` record (deathPolicy, persistThroughLogout, persistThroughDimensionChange, persistTemporaryModifiers, initializationPolicy).
- **Dimension-change lifecycle** (§17.5): "Dimension transfer must not reset resources or double-apply initialization. Full state may be resynchronized after transfer, but initialization hooks must be idempotent."
- **Generic synchronization** (§18): server-authoritative; full snapshot on join/respawn/dimension transfer/migration/reload/admin resync; batched revisioned deltas at most once/tick.
- **Client-side Resource state** (§3.1, §18.4): `ClientResourceRegistry`/`ClientResourceState`/`ClientResourceManager`/display-conversion classes; opt-in presentation-only prediction (Stamina sprint drain suitable; spell slots/Pact Magic/Rage recovery/Rest/Thirst/Sanity explicitly unsuitable for prediction).
- **Compatibility facades** (§24.3, exact heading): "Keep old public managers temporarily" — worked example has `PlayerStaminaManager.getStamina/removeStamina` *delegating to* `PlayerResourceService`. §24.5 (spell slots): "`SpellSlotComponent` becomes a compatibility facade or is removed after migration." §24.6 (charges): "Keep compatibility facade temporarily."
- **Legacy cleanup** (§27 Phase 8, verbatim bullets): "Remove legacy packets. Remove duplicate stores. Remove deprecated managers after no callers remain. Remove dead no-op methods. Remove one-release migration fallback after test worlds have been upgraded." Also explicitly names `PlayerChargesComponent.registerWithRestBus()` (§24.6) as a known dead no-op eligible for removal "after the new integration is active."

**Internally-backed resources** (§4.2, §1.1, §2.1 qualification matrix): "Mana, Stamina, Thirst, Rest Need/Fatigue, Sanity, Rage, Ki, Chakra, Solar Charge, spell slots, Pact Magic slots, and Hit Dice." Mana is explicitly called out as *universal but still internally-backed* (§1.1: "Mana is universal because it primarily supports Totality's future Ars Nouveau-like general magic system... not restricted to D&D classes").

**Externally-backed resources** (§4.2, §6.5): "Canonical V1 examples: Health (Health/Combat authority), Food (Food/Hunger authority), Temperature (Survival/Temperature authority)." Rationale (§6.5): "This avoids both bad extremes: they are not omitted from the shared API merely because Minecraft already has state for them [and] their state is not duplicated inside `PlayerResourceStateComponent`." Explicitly: "Externally backed does **not** mean presentation-only or second-class."

**Grant/ownership vocabulary** (§16.1, verbatim): Registered → Entitled → Granted → Instantiated → Active → Visible → Available → Locked → Server-enabled (9 axes). "A registered but ungranted resource is not a locked player resource. It is simply not part of that player's resource set." Worked grant-provider examples (§16.2): `BarbarianClass → totality:rage`, `MonkClass → totality:ki`, `KryptonianSpecies → totality:solar_charge`, `NinjutsuDiscipline → totality:chakra`, Shinigami source → `totality:reiatsu`, Jujutsu source → `totality:cursed_energy`. Universal resources use `GLOBAL_SYSTEM` sources (`totality:player_baseline` for Mana+Stamina, `totality:survival` for Thirst+Temperature, etc.). "The resource definition must not hardcode checks such as `player is Barbarian`."

**Design-closure statement:** The Resource API document itself **never calls itself "closed"** — grepped in full, no such self-declaration exists. The "closed" characterization comes entirely from the *later* Entitlement document's own header: `"Depends on: ... the closed Generic Player Resource API"` and `"TOTALITY_GENERIC_PLAYER_RESOURCE_API.md — closed and not redesigned here."` This audit treats the Resource design as closed per that later document's framing (consistent with §14 of the task that requested this audit), but the source of that framing is worth being precise about.

**Entitlement/Resource API relationship** (Entitlement doc §9.1, verbatim): "Entitlement decides whether a source-specific resource or feature is authorized. Generic Player Resource API owns the live resource state." The two systems are chained (Entitlement grant → class integration calls Resource API → Resource API grant/instantiate), not merged. Neither document claims Entitlement's grant model supersedes or replaces the Resource API's own `ResourceGrantProvider`/`ResourceGrant` — they cooperate through a one-way read-only integration point (`ResourceEntitlementView`).

---

## 3. Git / Implementation Timeline

Full commit history on `feature/general-resource-api` after Phase 3C (`69717c6`, 2026-07-31):

| Commit | Date | Touches Generic Resource API? | What it actually did |
|---|---|---|---|
| `07d5200` | 2026-07-31 | No (cosmetic only) | HUD/chat layout polish — explicitly does not touch `ClientResourcePresentationResolver` or resource wiring (confirmed in the Sept 15 resume audit). |
| `9ac1a92` | 2026-08-01 | No | Stamina Exhaustion→Depletion rename + join-lifecycle fix — touches `StaminaServerTick`/`StaminaDepletionManager`, both legacy, zero Resource-API references. |
| `4b29d27` | 2026-08-01 | **Yes — the only one** | "Register dormant player resources" — adds `totality:thirst`/`totality:sanity`/`totality:ki` as `GENERIC_COMPONENT` definitions to `ProductionResourceDefinitions`/`PlayerResourceIds`. This is **registration-only** (canonical's "Registered" axis) — no grant provider, no instantiation, no persistence exercised, no sync exercised. It is Phase-7-*adjacent* scaffolding, not actual Phase 7 implementation, and it is out of canonical's own Phase 7 dependency order (see §7). |
| `76e88ed` | 2026-08-01 | No | Disables vanilla passive Health regen via two mixins — Health stays fully vanilla-authoritative; no Resource-API code touched. |
| `2a82709`, `73380f9` | 2026-09-10 | No | Cosmetic equipment (Asauchi, Shinigami Robe), ~5.5-week gap after prior commit. |

**Conclusion: no commit after Phase 3C implements any part of canonical Phase 4, 5, 6, or 8.** The only Resource-API-touching work in this window is the Thirst/Sanity/Ki registration pass, which maps to (an incomplete slice of) canonical Phase 7.

**Uncommitted, this session:** a fix for the Rage `0/0`-on-reconnect bug (added `ChargeComponents.PLAYER_CHARGES.sync(...)` to the JOIN handler, and corrected `PlayerChargesComponent.applySyncPacket`'s existing-pool branch to also adopt an updated maximum) — working-tree only, not yet committed. This is a legacy bug fix, not a migration; it does not change any classification in this report. See §16 for how it relates to the newly-reported dimension-change symptom.

---

## 4. Phase 4 — Mana/Stamina Audit

**Classification: NOT STARTED.**

| Question | Mana | Stamina |
|---|---|---|
| Current state stored | `PlayerResourceComponent.mana` (int, default -1), component `totality:resources` | `PlayerResourceComponent.stamina`, same component |
| Maximum calculated | `PlayerManaManager.getMaxMana`: base 100 + stat bonus + armor/held `ManaItem` + `FORTIFY_MANA` effect + `MaxManaCalcEvent` | `PlayerStaminaManager.getMaxStamina`: base 100 + stat bonus + armor/held `StaminaItem` + `FORTIFY_STAMINA` effect + `MaxStaminaCalcEvent` |
| Spend/drain | `PlayerManaManager.removeMana`, called from `FormulaResolver`/`GrimoireItem` (rune casting, gated on cast success), `HeatVisionAbility` (channel drain) | `PlayerStaminaManager.removeStamina`, called from ~12 sites: `StaminaServerTick`, 4 weapon mixins, `PowerAttackManager`, `OffhandAttackHandler`, `MovementStaminaHandler`, `BowStaminaHandler`, `VeinminerAbility`, `GroundSlamAbility`, `ShurikenItem`, `ToggleFlightHandler` |
| Restore | `AlchemyEffects` (potions), `PlayerResourceRecalculator`/`TotalityCommands` (clamp/full-restore) | Same pattern |
| Regeneration | `ManaServerTick`, every 20 ticks, `END_SERVER_TICK` | `StaminaServerTick`, every tick (drains) / every 20 ticks (regen), combat-state- and depletion-aware |
| Persists it | `PlayerResourceComponent.writeData`/`readData` (NBT keys `"mana"`/`"stamina"`) | Same component |
| Does `PlayerResourceStateComponent` contain real state? | **No** — structurally rejected. Mana/Stamina are `EXTERNAL_ADAPTER`-authority; `instantiateScalar` throws `IllegalArgumentException` for any `EXTERNAL_ADAPTER` id. | Same |
| Does `PlayerResourceService` mutate it? | **No** — `PlayerResourceService` has no mutation methods at all (confirmed by reading the full class this session: only `query()` exists). | Same |
| Managers wrap Generic, or Generic wraps managers? | **Generic wraps the manager** — `ManaResourceAdapter`/`StaminaResourceAdapter` are query-only adapters reading the legacy component directly; the reverse (manager delegating to Generic, per canonical §24.3's own worked example) does not exist. | Same |
| NBT migration code | **None found anywhere** — no reference to `PlayerResourceStateComponent`/`totality:resource_state` in `PlayerResourceComponent.java`, `PlayerManaManager.java`, or `PlayerStaminaManager.java`. | Same |
| Dual writing / duplicate state | No dual-write (confirmed — the only cross-reference is a one-line `ResourceSyncManager.markDirty(...)` notification call inside `setMana`/`setStamina`, explicitly commented as non-authoritative). No duplicate *storage* exists because nothing has ever been imported into the new component. | Same |
| Legacy bespoke packets still sent | **Yes, continuously** — `SyncManaPayload` sent every regen tick and every mutation | **Yes, continuously** — `SyncStaminaPayload` sent throughout `StaminaServerTick` |
| Legacy client managers still populated | **Yes** — `ClientManaManager` | **Yes** — `ClientStaminaManager` |
| Would gameplay break if legacy manager/packet vanished today? | **Yes** — removes the only spend/regen/formula logic for rune casting, Heat Vision, alchemy restoration; HUD would freeze at last value (no independent Generic sync channel exists for presentation without it) | **Yes, more severely** — breaks sprint, power attack, bow draw, every special-weapon cost, flight, `StaminaDepletionManager`'s entire state machine, **and** `TotalityMovementHandler`'s direct client-side gameplay gate for Power Sprint/Super Leap (this one is not presentation-fallback — it's a real, unmigrated gameplay read of `ClientStaminaManager`) |

Canonical Phase 4 bullets (§27) vs. reality: "Import NBT" — not done. "Switch authoritative mutation to service" — not done (service has no mutation surface). "Preserve formulas and costs" — trivially true only because nothing has changed. "Consolidate tick scheduling" — not done (two separate tick loops, `ManaServerTick`/`StaminaServerTick`, remain). "Old managers become compatibility facades" — **false**; the managers are not facades over anything, they are still the sole implementation. "Remove old duplicate state only after validation" — not applicable, nothing to remove yet.

---

## 5. Phase 5 — Rage/Charge-Pool Audit

**Classification: NOT STARTED**, with one incidental canonical bullet already true for unrelated historical reasons.

- **Is `PlayerChargesComponent` still authoritative for Rage?** Yes, fully — `registerPool`/`consume`/`restore`/`setMax`/`updatePoolMax`/`ensurePool`/`onRest` are the only mutation surface, all real, all persisted (`writeData`/`readData`, component `totality:player_charges`).
- **Does `PlayerResourceStateComponent` own any Rage state?** No — same structural rejection as Mana/Stamina (`totality:rage` is `EXTERNAL_ADAPTER`-authority; `instantiateScalar` refuses it).
- **Does `PlayerResourceService` mutate Rage?** No — no mutation methods exist on the service at all.
- **Does `RageResourceAdapter` merely query the old component?** Yes — confirmed `supportedOperations() = {QUERY}` only, reads `PlayerChargesComponent.getAllPools().get(CHARGE_ID)`, never mutates.
- **Does Generic Rage sync serialize a snapshot of the legacy component, or Generic-owned state?** **A live re-query of the legacy component**, pushed over the newer wire protocol. `ResourceSyncManager` flushes whatever `RageResourceAdapter.snapshot()` reads from `PlayerChargesComponent` at that tick — there is no independent Generic-owned Rage value anywhere; it is a re-broadcast, not a second authority.
- **Migration/import code from `PlayerChargesComponent`?** None exists, and — same as Mana/Stamina/Spell Slots — none is structurally possible today without first redefining Rage away from `EXTERNAL_ADAPTER` authority.
- **Is the legacy charge client mirror required by real production gameplay?** No — `BarbarianRageAbility.canActivate`/`onActivate` run entirely server-side (`ServerPlayer`). Every production reader of the *client-side* `PlayerChargesComponent` is presentation or diagnostic only: `TotalityClient.java` (Rage secondary HUD, fallback-only via `ClientResourcePresentationResolver`), `ClassTab.java` (same pattern), `LegacyClientResourceParityReaders.java` (diagnostic parity tool). **Would removing it break anything other than debug/fallback code? No.**
- **Is `PlayerChargesComponent` intended to remain for other, non-Resource charge pools?** The class itself is architecturally generic (`Map<Identifier, ChargePool>`) and its own characterization test's Javadoc frames it as "a useful, generically Identifier-keyed owner pattern that may hold several independently identified pools at once" — so the *container pattern* plausibly outlives this migration for future non-Resource-API charge pools. That is a separate question from whether **Rage specifically** should keep its authority there — canonical Phase 5 clearly intends Rage's authority to move to Generic state, with `PlayerChargesComponent` surviving only "temporarily" as a compatibility facade (§24.6) for the transition period, not permanently as Rage's owner.
- **Recharge ownership already at "class listeners"?** Yes, incidentally — `BarbarianClass.register()`'s `ClassLevelUpRegistry.register(...)` callback already calls `BarbarianRageAbility.updateChargePool(player)` on every level-up. This happens to already satisfy one Phase 5 bullet ("move recharge ownership to class listeners"), but it predates and is independent of any Resource-API migration work — it was always the existing legacy design, not a migration deliverable.

**Dimension-transfer `legacy=0/0` in light of the cleanup plan:** Not fixed in this audit, per instruction. The structural cause (confirmed this session, not merely inferred): none of the 17 legacy client-mirrored components in this codebase — `ChargeComponents` included — register an `AFTER_CLIENT_LEVEL_CHANGE` resync hook, while the Generic Resource sync path explicitly does (`ResourceSyncLifecycleEvents`' `AFTER_PLAYER_CHANGE_LEVEL` + `TotalityClient.java`'s client-side clear-and-wait, both built in Phase 3A specifically to handle this). This is the *same class* of gap as the JOIN bug fixed earlier this session (a hand-maintained lifecycle list missing an event), just triggered by a different event. **Recommendation for direction, not action:** finishing the Phase 5 migration and letting the legacy Rage mirror become a genuinely temporary compatibility facade (per canonical §24.6) — rather than adding a third piecemeal lifecycle patch to a mirror that canonical Phase 8 intends to retire — is the better-evidenced direction. See §16 for the full reasoning.

---

## 6. Phase 6 — Spell Slot Audit

**Classification: NOT STARTED**, with the existing-behavior-preservation bullets already true for the same reason as Phase 5 (they were never broken, not because of migration work).

- **Is `SpellSlotComponent` still authoritative?** Yes — `int[10] maxSlots`/`usedSlots`, `useSlot`/`restoreAll`/`restoreSome`/`recalculate`, NBT-persisted (`"max_0".."max_9"`, `"used_0".."used_9"`).
- **Does Generic Resource state persist slot tiers?** No — same `EXTERNAL_ADAPTER` structural rejection.
- **Does `PlayerResourceService` perform slot spending/restoration?** No.
- **Generic only queries/syncs the existing component?** Confirmed — `StandardSpellSlotsResourceAdapter`'s own Javadoc states it "never calls `recalculate`, `useSlot`, `restoreAll`, `restoreSome`, or anything that could trigger `sync()`"; `supportedOperations() = {QUERY}` only.
- **Old packet/client managers still used?** Spell slots have **no bespoke packet class** (unlike Mana/Stamina) — sync rides the shared generic `ComponentSync` channel, feeding `ClientSpellSlotManager`. Still actively pushed on every `sync()` call (recalculate/useSlot/restoreAll all trigger it). Consumer: `SpellRadialScreen.java`, presentation-fallback-only via `ClientResourcePresentationResolver` (its own code comment: "Display only: this never mutates or recalculates slot state, and the actual cast/spend path is unchanged").
- **Does casting still mutate `SpellSlotComponent` directly?** Yes, exclusively — `ActivateAbilityHandler.java` is the only production `useSlot(` call site.
- **Successful-cast-only consumption — preserved, confirmed by direct code trace:** `hasSlot(...)` is checked *before* `ability.onActivate(...)` runs (eligibility gate, no mutation); `useSlot(...)` is only called afterward, gated on `castSucceeded = Spell.didCastSucceed()`. A failed/interrupted cast never reaches `useSlot`. This behavior must be preserved by any future Phase 6 work — it already is, and nothing in this audit threatens it.
- **Rest restoration:** `SpellSlotComponent implements RestListener`; Long Rest → `restoreAll()` (all levels); Short Rest → no-op for standard slots (reserved for the not-yet-built Pact Magic pool).
- **Any Generic NBT migration for spell slots?** None — and, like Mana/Stamina/Rage, none is structurally possible today (`instantiatePartitioned` also rejects `EXTERNAL_ADAPTER` ids).
- **Does the current partitioned Generic representation contain authoritative mutable state, or only a synchronized/query view?** **Query view only.** `PartitionedResourceSnapshot`/`ResourceQueryResult.PartitionedSuccess` are read-only result shapes; nothing writes through them.

Canonical Phase 6 bullets (§27) vs. reality: "Import every tier" — not done. "Preserve multiclass maximum resolver" — untouched, still `SpellSlotRecalculator`/`SpellSlotTable`. "Preserve successful-cast commitment" — true today, unrelated to migration (see above). "Switch spell UI and Rest listener" — UI presentation switched in Phase 3C (display only); the actual Rest listener is still the legacy `SpellSlotComponent.onRest`. "Keep adapter facade until all callers move" — the adapter exists, but it's a facade *around* the legacy authority, not a facade *for* a new Generic authority that callers have actually moved to.

---

## 7. Phase 7 — Missing Resource Audit

Classification per canonical's own axes (§16.1: registered/entitled/granted/instantiated/active/visible/available/locked), not "should this exist":

| Resource | Registered? | Instantiated? | Persisted? | Synchronized? | Queryable? | Mutable via `PlayerResourceService`? | Genuinely playable? |
|---|---|---|---|---|---|---|---|
| **Thirst** | Yes (`totality:thirst`, `GENERIC_COMPONENT`, min 0/max 100) | **No** — no grant provider anywhere calls `instantiateScalar` | No (nothing to persist) | No (query fails `STATE_NOT_INSTANTIATED`, omitted from every wire packet) | Query structurally succeeds only after instantiation, which never happens | No (no mutation API exists at all) | **No** |
| **Sanity** | Yes (same shape as Thirst) | No | No | No | Same | No | **No** |
| **Ki** | Yes, but **deliberately has no `authoredBaseMaximum` at all** — canonical comment: "set by Monk level/features... no resolver framework exists yet" | No | No | No | Query would fail `MAXIMUM_UNAVAILABLE` even if instantiated | No | **No** |
| **Hit Dice** | **Not registered.** Grep across `src/main/java` for "Hit Dice"/"HitDice": zero implementation hits (the only matches are unrelated Javadoc/enum-name coincidences in `PlayerResourceService.java`/`ResourceModel.java`, not a Hit Dice system). | — | — | — | — | — | **Absent** |
| **Pact Magic** | **Not registered.** Zero matches anywhere. Confirmed indirectly too: `SpellSlotRecalculator` explicitly excludes `WARLOCK` from its combined-caster-level formula, per its own design (a separate, still-unbuilt pool). | — | — | — | — | — | **Absent** |
| **Temperature** | **Not registered as an adapter.** The two grep hits (`ProductionResourceDefinitions.java`, `PlayerResourceIds.java`) are the same comment already found in the September audit: `totality:fatigue` and `totality:temperature` are noted as "deliberately absent." No `TemperatureResourceAdapter` class exists. | — | — | — | — | — | **Absent** |
| **Solar Charge** | **Not registered. Zero matches anywhere.** `HeatVisionAbility` (the one Kryptonian ability that exists) currently spends **Mana**, not a dedicated Solar Charge resource — confirmed by direct trace (`getMana`/`removeMana` calls in `HeatVisionAbility.java`). | — | — | — | — | — | **Absent** |
| **Chakra** | **Not registered.** Zero matches for real implementation; only 2 incidental particle-animation doc-comment mentions (`VortexAnimation.java:23`, `TorusAnimation.java:36`), reconfirmed from the September audit. | — | — | — | — | — | **Absent** |
| **Reiryoku/Reiatsu/Cursed Energy** | **Not registered.** Zero matches. | — | — | — | — | — | **Absent** |

**Canonical Phase 7's own priority order** (§27, verbatim): "1. Hit Dice pool and HP/Short-Rest integration ... the highest-priority missing Rest recovery foundation. 2. Pact Magic through `WarlockClass`. 3. Ki through `MonkClass`. 4. Species resources ... such as Solar Charge. 5. Thirst and the Temperature external adapter when Survival implementation begins. 6. Rest Need/Fatigue only after its unresolved design choices are locked. 7. Sanity."

**The actual `4b29d27` commit registered items 3, 5 (Thirst only, no Temperature adapter), and 7 — while skipping items 1, 2, 4, and 6 entirely**, and even the three it touched are registration-only scaffolding, not "genuinely playable" by canonical's own bar. This is real, out-of-canonical-order work — worth naming plainly rather than crediting as "Phase 7 in progress."

---

## 8. Phase 8 — Legacy Cleanup Audit

**Classification: NOT STARTED.** Inventory of every legacy Resource-related path:

### Mana
| Path | Classification |
|---|---|
| `PlayerManaManager` | Still authoritative and required |
| `PlayerResourceComponent` (legacy store) | Still authoritative and required |
| `SyncManaPayload` + handler | Still authoritative and required (presentation depends on it; no independent Generic sync exists for display without it) |
| `ClientManaManager` | Fallback-only for presentation (via `ClientResourcePresentationResolver`); parity-diagnostic reader also depends on it |

### Stamina
| Path | Classification |
|---|---|
| `PlayerStaminaManager` | Still authoritative and required |
| `PlayerResourceComponent` (shared with Mana) | Still authoritative and required |
| `SyncStaminaPayload` + `StaminaServerTick` | Still authoritative and required |
| `ClientStaminaManager` | **Still gameplay-authoritative, not merely fallback** — `TotalityMovementHandler` reads it directly (not through the resolver) to gate Power Sprint/Super Leap client-side. Removing it today would break real client gameplay prediction, not just display. |

### Rage
| Path | Classification |
|---|---|
| `PlayerChargesComponent` | Still authoritative and required (server-side) |
| Charge sync packet (`ComponentSync` channel, not a bespoke class) | Still authoritative and required |
| Client charge mirror (`ChargeComponents` client-side) | Fallback/parity only (see §5) — safe candidate for removal, but only after Phase 5 actually migrates Rage's authority, not before |
| `LegacyClientResourceParityReaders.rageSummary()` | Parity/debug only |
| `PlayerChargesComponent.registerWithRestBus()` | **Completely dead** — a no-op, called from nowhere, explicitly named by the canonical document itself (§24.6) as removable "after the new integration is active." Still present today, unremoved, confirmed by direct read of current source. |

### Spell Slots
| Path | Classification |
|---|---|
| `SpellSlotComponent` | Still authoritative and required |
| `ComponentSync` channel (shared, no bespoke packet class) | Still authoritative and required |
| `ClientSpellSlotManager` | Fallback-only for presentation (`SpellRadialScreen` via resolver); parity-diagnostic reader also depends on it |
| `StandardSpellSlotsResourceAdapter` | Not legacy — this IS the Generic-API-side adapter, query-only, correctly scoped |

**Nothing has been deleted.** No candidate in this inventory has actually lost a caller yet, because no migration (Phase 4/5/6) has moved any authority away from it. Per canonical's own Phase 8 precondition ("remove... after no callers remain"), none of these are safe to remove *today* — that classification will only change resource-by-resource, as each one's Phase 4/5/6 migration actually completes.

---

## 9. `PlayerResourceStateComponent` Audit

Read in full this session (`src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceStateComponent.java`).

- **Which resources have persisted state inside it today?** None. Zero production writes.
- **Which resources can theoretically be instantiated?** Only the three `GENERIC_COMPONENT` definitions — Thirst, Sanity, Ki. The seven `EXTERNAL_ADAPTER` resources (Health, Food, Breath, Mana, Stamina, Spell Slots, Rage) are structurally, permanently barred from this component (`rejectExternalAuthority` throws) — this is intended enforcement, not a gap.
- **Which production code calls `instantiateScalar`?** None.
- **Which production code calls `instantiatePartitioned`?** None.
- **Which production code calls a mutation method?** None exist to call — this class itself has no spend/drain/restore methods either; it only has `instantiateScalar`/`instantiatePartitioned` (creation) and `removeState` (deletion), plus pure query getters.
- **Does anything call its `sync()`?** No — confirmed by the class's own Javadoc ("Nothing in production code calls `sync()` yet, so this component never sends a network packet") and by grep.
- **Does anything exercise its persistence/death lifecycle?** No — `writeData`/`readData`/`copyFrom` are all fully implemented and would work correctly if called, but nothing in production ever populates `states` in the first place, so these paths are exercised only by unit tests, not real play.
- **Is it currently effectively dormant infrastructure?** Yes, entirely — every capability described above is implemented and (per unit tests) correct, but zero of it has ever run against a real player in this codebase.
- **Does its schema already support the canonical future migration?** Largely yes — schema v2 already handles scalar + partitioned state, overflow, regeneration remainder, and orphan quarantine (stale/mismatched persisted data preserved rather than discarded) exactly as canonical §5.4/§17.4 describe. This is a genuine, well-built foundation; it is unused, not broken.
- **Obvious missing pieces before it becomes the real Player Resource Host:** (1) no `ResourceGrantProvider` to ever call `instantiateScalar`/`instantiatePartitioned`; (2) no `ResourceMaximumResolver` framework (confirmed absent — `PlayerResourceService.queryGenericState` can only use an authored, static maximum, which is why Ki has no maximum at all); (3) `PlayerResourceService` has no mutation methods to route through this component even once state exists; (4) no NBT migration/import path from any legacy store.

---

## 10. `PlayerResourceService` Audit

Read in full this session (`src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceService.java`, 258 lines).

| Canonical capability (§12.1) | Status |
|---|---|
| `snapshot`/query | **Implemented and used** — `query(Player, Identifier)` is the entire public API surface. Routes `EXTERNAL_ADAPTER` definitions to their adapter, `GENERIC_COMPONENT` (scalar only) to `PlayerResourceStateComponent`. Never instantiates as a side effect; never fabricates a value on failure — returns one of 9 named `ResourceQueryFailureReason`s instead. |
| `trySpend` | **Absent.** No method of this name or shape exists anywhere in the class. |
| `restore` | **Absent.** |
| `drain` | **Absent.** |
| `set` | **Absent.** |
| `transact` (atomic multi-resource) | **Absent.** |
| Modifiers (permanent/temporary maximum modifiers, §11) | **Absent** — no modifier framework exists in this class or its supporting types. |
| Max recalculation | **Partially present, narrowly** — `queryGenericState` can only use a static `authoredBaseMaximum`; there is no `ResourceMaximumResolver` invocation path at all (the resolver interface concept from §9.1 has no implementation). |
| Availability resolution | **Absent** — no `ResourceAvailabilityResolver` concept implemented. |
| Grants | **Absent** — the service has no awareness of grants; it only knows whether state happens to already exist. |
| Event publishing (§20) | **Absent** — no event phases, no listener framework. |

**Confirms the September audit's characterization, more precisely than before:** it is not merely that "mutation APIs exist but are unused" — **mutation APIs do not exist in source at all.** `PlayerResourceService` today is exactly the Phase 2A-scoped query/snapshot path its own class Javadoc says it is (the Javadoc literally quotes the Phase 1/2A task's own scope note: "Do not migrate production resources yet... exclusion of restore/drain/spend/set/transactions"), unchanged since. The September audit's "query + sync + presentation" description remains fully accurate.

---

## 11. Grant/Ownership Audit

- **Is there a real `ResourceGrantProvider` system?** No. Grep for `ResourceGrantProvider`/`GrantProvider` across all of `src/main/java`: zero matches. The only related file is `api/rpg/resources/integration/ResourceGrantInitialization.java` — a **different** concept (a sealed interface describing an initial-value strategy: `AtMinimum`/`AtMaximum`/`AtFraction`/`AtAbsolute`/`PreserveExisting`/`Custom`), not a "who gets granted this resource" provider. It answers "what value does a newly-instantiated resource start at," not "should this player have this resource at all."
- **What grants universal Mana/Stamina today?** Nothing explicit — both lazy-initialize to max on first `getMana`/`getStamina` call for any player (a side effect buried inside the legacy manager's getter, not a `ResourceGrant`). There is no record of *why* a player has Mana; they simply always do, implicitly, the first time anything asks.
- **What grants Rage only to Barbarians?** Legacy, ad-hoc code — `SelectClassHandler.handle(...)` calls `BarbarianRageAbility.registerChargePool(player)` directly inside an `if (classId.equals(TotalityClasses.BARBARIAN_ID))` branch. This is exactly the pattern canonical §16.2 explicitly forbids for the *target* state ("The resource definition must not hardcode checks such as `player is Barbarian`") — but note this hardcoded check lives in `SelectClassHandler`, not in the resource definition itself, so it is not literally violating that rule; it simply predates having any `ResourceGrantProvider` abstraction to use instead.
- **What grants Ki to Monks?** Nothing — Monk class content does not currently exist in this codebase in a form that could grant anything, and Ki has no maximum resolver regardless.
- **What would currently instantiate Thirst/Sanity?** Nothing — confirmed absence of any caller (§9 above).
- **Are definitions incorrectly instantiated globally?** No — the opposite problem exists (nothing is instantiated at all, for anyone, ever, for the three `GENERIC_COMPONENT` resources).
- **Is Rage's Generic representation available for a non-Barbarian?** No — `RageResourceAdapter` correctly returns `STATE_UNINITIALIZED` when the sparse `PlayerChargesComponent` pool map has no entry for `barbarian_rage`, which is the case for any player who never had `registerChargePool` called for them (i.e., every non-Barbarian). This is structurally correct today, even without a formal grant system, because the underlying legacy map is naturally sparse.
- **Have later Entitlement API decisions superseded any original grant design?** No — per §2 above, the Entitlement document explicitly does not own resource values and defers to the Resource API's own (still-unbuilt) grant model once it exists; the two are declared as adjacent, not merged, and neither document claims supersession of the Resource API's own `ResourceGrantProvider` concept.

**Overall: the entire canonical §16 grant/ownership model — the full registered/entitled/granted/instantiated/active/visible/available/locked lifecycle, `ResourceGrantProvider`, `ResourceGrant` records, aggregation/removal/visibility policies — has zero implementation.** Every "who has this resource" decision in the current codebase is legacy, resource-specific, ad-hoc code that predates and has no relationship to the Resource API.

---

## 12. Canonical Design vs. Current Implementation Matrix

| Canonical capability | Intended end state | Current implementation | Status | Evidence | Required migration |
|---|---|---|---|---|---|
| Central internal state component | `PlayerResourceStateComponent` holds all `GENERIC_COMPONENT` resource state | Exists, schema-complete, zero production use | Implemented, unused | §9 | Needs a grant provider to ever populate it |
| Scalar state | `ScalarResourceState` | Implemented | Implemented, unused | §9 | Same |
| Partitioned state | `PartitionedResourceState` | Implemented | Implemented, unused | §9 | Same |
| Query service | `PlayerResourceService.query` | Implemented, in production use | **Complete** | §10 | None |
| Mutation service | `trySpend`/`restore`/`drain`/`set`/`transact` | None exist | **Absent** | §10 | Phase 1/4/5/6 foundation work |
| Atomic transactions | Cross- and single-resource | None exist | **Absent** | §10 | Same |
| Maximum resolver | `ResourceMaximumResolver`, central resolution order | No implementation; only a static authored value is usable | **Absent** | §9 | Needed before Ki or any leveled/formula-driven Generic resource can work |
| Modifiers | Permanent/temporary maximum + cost/regen-rate modifiers | None | **Absent** | §10 | Phase 4+ |
| Grant system | `ResourceGrantProvider`/`ResourceGrant` | None; only the unrelated `ResourceGrantInitialization` (initial-value strategy) exists | **Absent** | §11 | Needed for any Phase 4-7 resource to become genuinely playable |
| Persistence | `PlayerResourceStateComponent` NBT | Implemented, exercised only by tests | Implemented, unused in production | §9 | Same as state component |
| Legacy NBT import | 11-step §24.4 sequence | None | **Absent** | §4, §5, §6 | Phase 4/5/6 each need their own import step |
| Orphan-state preservation | Quarantine stale/mismatched persisted data | Implemented and correct | **Complete** (for the dormant path) | §9 | None |
| Death/respawn | `ResourceLifecyclePolicy` per resource | Implemented (`DEFAULT` only, unused by any live resource) | Implemented, unused | §9 | None until a resource actually lives there |
| Dimension transfer | Must not reset/double-init; full resync after | Implemented correctly for the Generic sync channel; **not implemented at all** for any of the four legacy client mirrors | Partial (new channel only) | §5, §16 | Phase 4/5/6 retire the legacy mirrors that lack this |
| Server→client full sync | On join/respawn/dimension/migration/reload/resync | Implemented (Phase 3A) | **Complete** | §3 (prior audit) | None |
| Deltas | Revisioned, batched, ≤1/tick | Implemented (Phase 3A) | **Complete** | Same | None |
| Generic client view | `ClientResourceService` façade | Implemented (Phase 3B) | **Complete** | Same | None |
| Presentation migration | Consumers prefer Generic, fall back to legacy | Implemented (Phase 3C) | **Complete** | Prior Sept audit | None |
| Legacy packet retirement | Remove after no callers remain | Not started — all four legacy packets/channels still fully live and, for Stamina, gameplay-load-bearing | **Not started** | §8 | Phase 8, after 4/5/6 |
| Legacy manager retirement | Same | Not started | **Not started** | §8 | Same |
| Mana migration | Generic-owned state, service-mutated | Not started | **Not started** | §4 | Phase 4 |
| Stamina migration | Same | Not started | **Not started** | §4 | Phase 4 |
| Rage migration | Same | Not started | **Not started** | §5 | Phase 5 |
| Spell Slot migration | Same | Not started | **Not started** | §6 | Phase 6 |
| Dormant resource instantiation | Grant provider populates Thirst/Sanity/Ki | Registered only, never instantiated | **Registered, not instantiated** | §7, §9 | Grant provider + (for Ki) a maximum resolver |

---

## 13. Exact Remaining Migration Work

In dependency order, each independently reviewable:

1. **Build the mutation façade on `PlayerResourceService`** (`trySpend`/`restore`/`drain`/`set`, plus at minimum a single-resource atomic transaction path) — a pure foundation change, no resource migrates yet. Nothing downstream can happen without this.
2. **Build a minimal `ResourceGrantProvider` mechanism** and wire `PlayerResourceStateComponent.instantiateScalar`/`sync()` into it for at least one resource, to prove the previously-dormant persistence/sync paths actually work end-to-end in production (not just in tests).
3. **Build a minimal `ResourceMaximumResolver` framework** — required before Ki (no static maximum) or any future leveled/formula-driven `GENERIC_COMPONENT` resource can be queried successfully.
4. **Migrate Mana and Stamina** (Phase 4) — redefine both from `EXTERNAL_ADAPTER` to `GENERIC_COMPONENT` authority, import existing NBT, switch `PlayerManaManager`/`PlayerStaminaManager` to become compatibility facades delegating to the service (per canonical §24.3's own worked example), consolidate the two separate tick loops if appropriate. Highest gameplay surface area (12+ Stamina call sites) — do this only after step 1-3 are proven.
5. **Migrate Rage** (Phase 5) — canonical's own text (`TOTALITY_GENERIC_PLAYER_RESOURCE_API_IMPLEMENTATION_READINESS.md:417`, cited by the canonical-extraction agent) independently calls this "lowest-risk migration given the existing shape's closeness to the target." Also removes the now-twice-patched legacy mirror lifecycle problem at its root, and the explicitly-named dead code (`registerWithRestBus()`).
6. **Migrate standard Spell Slots** (Phase 6) — preserve the successful-cast-only consumption ordering and the multiclass maximum resolver exactly (already verified correct in §6; the migration must not touch this behavior).
7. **Retire the now-orphaned legacy packets/managers/mirrors** for whichever of Mana/Stamina/Rage/SpellSlots have completed migration (Phase 8, per-resource, not all at once) — including updating or retiring the parity/debug tooling's comparison target for each retired resource, since its job for that resource is now done.
8. **Promote Thirst/Sanity/Ki** from scaffolding to real, using the now-proven grant-provider/maximum-resolver machinery from steps 2-3.
9. **New resources (Reiryoku/Chakra/etc.)** only after the above, using the by-then-proven pattern — not before, and not by reopening the closed design.
10. **Food 0-100** — separately, since canonical explicitly keeps Food `EXTERNAL_ADAPTER`-authority (Food/Hunger system remains owner); this is a different, unrelated project (a native Totality food authority replacing vanilla `FoodData`), not a Resource-API migration step at all.

---

## 14. Recommended Implementation Order

The task's own proposed sequence is **correct and matches this audit's independent findings**, with two adjustments:

1. Finish Generic internal mutation/state infrastructure (§13 steps 1-3) — **confirmed necessary first step**, not optional; nothing else in the list is possible without it.
2. Migrate Mana/Stamina — **confirmed as Phase 4**, matches canonical order exactly.
3. Migrate Rage — **confirmed as Phase 5**, and independently corroborated as "lowest risk" by the earlier readiness-audit document.
4. Migrate Spell Slots — **confirmed as Phase 6**.
5. Eliminate redundant bespoke client synchronization — **confirmed as Phase 8**, but only per-resource, immediately after that resource's own migration, not as one giant final pass; canonical's own Phase 8 wording ("remove... after no callers remain") supports doing this incrementally.
6. Promote Thirst/Sanity/Ki — matches canonical Phase 7, **but note canonical's own priority order puts Hit Dice and Pact Magic ahead of Ki**, and Sanity last. If the team wants to follow canonical's stated reasoning exactly rather than the task's proposed order, Hit Dice/Pact Magic should be reconsidered before or alongside Ki — this is a real divergence between the task's proposed order and the canonical document's own explicit ordering, worth a deliberate decision rather than silent adoption of either.
7. Add new resources (Reiryoku/Chakra) later — confirmed correct; zero scaffolding exists for either today, so this is a clean addition to the (unmodified) closed design, not a foundation change.
8. Food 0-100 separately — confirmed correct; canonical explicitly keeps Food `EXTERNAL_ADAPTER`-authority, so this is architecturally a different project, not a Resource-API phase at all.

**No contradiction was found between the current implementation and the canonical design that would require reopening the closed architecture.** Every gap found in this audit is an *incompleteness* (work not yet done) rather than evidence the canonical design is wrong or unimplementable — including the mutation-façade absence, the grant-provider absence, and the Ki maximum-resolver absence, all of which canonical already anticipated and specified.

---

## 15. Risks / Save Compatibility

- **No save-format risk exists yet** — because nothing has been migrated, there is no legacy-vs-new NBT format collision to worry about today. This risk becomes live only once Phase 4/5/6 work actually begins; canonical's own §24.4 migration sequence (11 steps, idempotent-on-rerun, no dual-write) should be followed exactly when that starts.
- **Stamina carries the highest migration risk** of the three Phase 4/5/6 candidates — not because its data model is complex, but because of the sheer number of gameplay-authoritative call sites (12+) and the one confirmed non-presentation client-side gameplay read (`TotalityMovementHandler`'s Power Sprint/Super Leap gating). Any Phase 4 work must explicitly re-verify that client-side gate against the new architecture, not just the server-side spend paths.
- **The parity/debug tooling (`/totalitydebug resource parity`) will need per-resource retirement**, not a single final removal — as each resource's legacy mirror is retired in its own Phase 8 pass, that resource's comparison line in the parity tool loses its purpose and should be removed or repurposed at that time, not left dangling or removed prematurely.
- **`registerWithRestBus()` is safe to remove independently, any time** — canonical itself says so, it is already fully dead, and removing it requires no migration precondition. (Not removed by this audit, per instructions — noted here as a genuinely zero-risk cleanup item for whenever cleanup work begins.)

---

## 16. Uncertainties

- **The Rage dimension-change `legacy=0/0` symptom's exact mechanism** (why the client-side `PlayerChargesComponent` resets to empty specifically on dimension change) was inferred structurally this session — from the fact that `PlayerComponentEvents.attachClientComponentsTo` only attaches a fresh component when the container has none, combined with every Resource-API-owned class (but zero legacy-owned classes) explicitly registering `AFTER_CLIENT_LEVEL_CHANGE` handlers — rather than confirmed by directly tracing the exact vanilla/Fabric mixin that recreates the client entity's component container on a dimension change. The reasoning is strong and consistent with the reported evidence, but this specific mechanism was not independently verified line-by-line.
- **This session's uncommitted Rage JOIN-sync fix** (see §3) is not a migration and doesn't change any Phase 4-8 classification, but it does mean the "before reconnect" half of the user's original two-part evidence (legacy `0/0` on reconnect) is already resolved as of this session — only the dimension-change half remains open, exactly as the task's own "recent manual evidence" section already stated.
- **Whether `ManaServerTick` and `StaminaServerTick` should be "consolidated" (canonical §27 Phase 4 bullet: "consolidate tick scheduling")** was not evaluated for feasibility in this audit — it's named in canonical but its exact intended scope (one shared tick loop? one shared scheduler abstraction?) isn't spelled out further in the sections read, and doing so wasn't necessary to establish Phase 4's NOT STARTED classification.
- **The exact vanilla/vendor mechanism behind `TotalityMovementHandler`'s client-side Stamina read** (why this one consumer was deliberately left un-migrated in Phase 3C, versus everything else) is documented as an intentional design boundary (client-side gameplay prediction should never go through a presentation-only resolver) rather than an oversight — this is stated confidently based on the Phase 3C report's own scope language and the consumer's own code comments, not from a canonical-document citation specifically calling out movement prediction by name (canonical §18.4 does generically list "suitable for prediction: Stamina drain from sprint/flight," which is consistent with, but not a direct citation for, this specific architectural boundary).
- **Whether any Phase 0 characterization-test work (canonical's own first roadmap step, "frozen behavior tests" for exact current Mana/Stamina/SpellSlot/Rage formulas before any migration) already exists** was not exhaustively checked — Phase 1's own test suite (100+ tests per its implementation report) covers the *new* API's own classes, but whether a dedicated "pin today's legacy Mana/Stamina/SpellSlot/Rage formula behavior" characterization suite already exists (as opposed to the Rage-specific one found and extended this session, `PlayerChargesRageCharacterizationTest`) was not confirmed for Mana/Stamina/SpellSlot specifically.

---

## 17. Final Classification

| Item | Status |
|---|---|
| Resource API design | Closed (per the later Entitlement document's own characterization — the Resource API document itself never self-declares closure) |
| Resource API foundation implementation (Phase 1) | Complete |
| Phase 2 (adapters over existing resources) | Complete |
| Phase 3 (sync + client presentation, including 3B/3C sub-work) | Complete |
| Phase 4 (Mana/Stamina migration) | **Not started** |
| Phase 5 (Rage migration) | **Not started** |
| Phase 6 (Spell Slot migration) | **Not started** |
| Phase 7 (missing resources) | **Scaffolding only, out of canonical order** — Thirst/Sanity/Ki registered (2026-08-01), none instantiated/playable; Hit Dice, Pact Magic, Temperature, Solar Charge, Chakra, Reiryoku/Reiatsu, Cursed Energy all fully absent |
| Phase 8 (cleanup) | **Not started** — zero legacy packets/managers/mirrors/dead code removed |
| Generic Player Resource API V1 overall | **Not implementation-complete.** Phases 0-3 (foundation, adapters, sync, presentation) are genuinely done and solid. Phases 4-8 (the actual migration of authority, which is most of what makes this a "Generic" Resource API rather than a read-only dashboard over four separate legacy systems) have not begun beyond one out-of-order scaffolding commit. |

---

*This report was compiled from three parallel research passes this session (canonical design extraction, Mana/Stamina/SpellSlot legacy-internals trace) plus direct reading of `PlayerResourceService.java`, `PlayerResourceStateComponent.java`, `PlayerChargesComponent.java`, `PlayerConnectionEvents.java`, `PlayerComponentEvents.java`, and targeted verification greps, all cross-checked against each other. No disagreements were found between passes. No production code was modified to produce this report.*
