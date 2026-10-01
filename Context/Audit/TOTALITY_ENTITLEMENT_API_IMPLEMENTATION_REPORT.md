# TOTALITY — Entitlement API Implementation Report

**Date:** 2026-10-01
**Starting revision:** `ed5f8cdea3a71d5327f71f75be99700930826f80` (`master`, "feat: creative tests A-K, voice/notification/HUD APIs, Skills menu, v1.2.0")
**Canonical specification:** `Context/Audit/TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API.md` (read in full; not rewritten)
**Revision:** 4 — revision 2's review corrections (§0), the flight-state follow-up (§0.6) and the Creative/Spectator flight follow-up (§0.7). Earlier bundles: `TOTALITY_ENTITLEMENT_API_REVIEW.zip` (rev 1), `_R2.zip` (rev 2), `_R3.zip` (rev 3).
**Status:** implemented and corrected, uncommitted, awaiting review. Nothing was committed or pushed; Codex was not started.

Pre-existing working-tree changes present before this task and **not part of it**: deleted `Context/References/TOTALITY_MASTER_v3.6_SECTIONS_26L_26M_EXTRACT.md`, untracked `Context/References/{Anime Screenshots, Mob Hud V1, Notification API V2, Other}/`, untracked `Context/References/STEFAN_TOTALITY_MASTER_REFERENCE_v3.8_final_audited.docx`.

---

## 0. Revision 2 — review corrections

Every correction is covered by automated tests. "U" = JUnit, "L" = live-world suite on fake players with real components and handlers, "S" = source regression. Items that still need a real Minecraft client are listed in §10.3.

### 0.1 Safe failure behaviour (correction 1)

**Problem.** A provider that threw kept its previous grants *and kept authorizing with them*; a contributor that threw was ignored, which could silently drop a suspension it would have reported.

**Now.**
- **Providers** (`EntitlementEngine.reconcile`): a provider that throws is marked **unverified**. Its previous grants stay on record (nothing erased, kept for diagnostics and recovery) but are **withheld**: they authorize nothing until the provider succeeds again. Decisions that relied only on them return `totality:source_unverified` (display state `KNOWN_UNAVAILABLE`). Other providers, session/persisted grants and permanent facts are unaffected. Becoming unverified bumps the revision, so caches clear and availability events fire (an active ability that depended on it is stopped by its owner). Repeated failures update a diagnostic record (error, first/last time, count) without revision churn. Failed providers are retried every 5 s and on every reconciliation; a success clears the failure and applies the fresh projection (a stale grant is never revived).
- **Contributors**: a contributor that throws while contributing to a decision fails that decision **closed** — `HIDDEN` for client-facing purposes, `DENIED source_unverified` on the server, never cached, so the next query retries. A contributor whose enumeration throws adds no candidates. Stored facts and grants are never touched.
- **Diagnostics**: `/totality entitlement audit` lists unverified providers, withheld grants and contributor failures.
- **Tests**: `SafeFailureTest` (U, 7 tests), covering a failure with existing grants (denied, kept on record, other sources and permanent progression unaffected), counted retries without churn, recovery, recovery with changed source state, a failure on first collection, a contributor that could have suspended access (denied/hidden, fact intact), enumeration failure, and session grants unaffected by an unrelated provider failure.

### 0.2 Domain-owned enumeration (correction 2)

`EntitlementStateContributor.enumerate(typeId, context)` (default: none) lets an owning API list the keys it knows. Bulk queries (`accessible`) and the client display view include them, and each key is still decided by the normal pipeline (policy, contributor state, requirements). Nothing is copied into the ledger. The contributor's `dependencies()` are folded into cached bulk results, so the owner's change signal refreshes them.

**Tests** (`DomainEnumerationAndInvalidationTest`, U):
- A hypothetical Technology API type (`DOMAIN_OWNED`, `known_or_grant`) with knowledge held only in the "API" is accessible with no Entitlement fact and no grant, and learning more is picked up through the owner's dependency key.
- A domain-owned spell is enumerated but still blocked by its use requirement (`known_but_unavailable`), and Entitlement still refuses to store its knowledge.

### 0.3 Debug-only progression (correction 3)

**Traced execution paths.** I traced the full path debug-only access can reach. Debug access is granted to the player, the ability runs through `ActivateAbilityHandler` → `Ability.onActivate`, or through channel/toggle/passive ticks in `AbilityServerTick`, or through Veinminer's block-break hook. Damage goes through `TotalityDamage.hurt(target, caster, …)`, which becomes `playerAttack(caster)`. From there the ordinary progression it can reach is:
- **One-Handed skill XP**: `OneHandedSkillHandler` awards XP on *any* damage attributed to the player while a one-handed weapon is held. Spell damage counts, so a debug-cast spell earned skill XP and, through skill level-ups, character XP. **Confirmed reachable.**
- **Mining skill XP**: Veinminer awards it in its break hook and in its per-tick channel.
- **Delayed spell damage**: `SpellBoltEntity` hits (bolt spells, Crown of Stars motes) and the `FireballProjectileEntity` blast happen in the projectile's own tick, after the cast has returned.
- **Quest objectives**: none are triggered by combat or spells today; `completeObjective` is guarded defensively.

**Safeguard — `ProgressionContext`** (new, `api/entitlement`):
- **Producers** run debug-only execution in a per-player non-progression scope:
  - `ActivateAbilityHandler` (uses the decision's `debugOnly`);
  - channel start in `ToggleAbilityHandler`;
  - passive, toggle and channel ticks in `AbilityServerTick` and in the second passive ticker in `Totality`;
  - Veinminer's XP award.
- **Inheritance**: entities the player spawns and owns inside the scope inherit it (`ENTITY_LOAD` hook). Because spell damage is attributed to the caster rather than the projectile, the hit code of `SpellBoltEntity` and `FireballProjectileEntity` re-enters the owner's scope with `runForEntity`.
- **Consumers (existing sinks)**: `PlayerSkillsComponent.addSkillXp` (covers every skill and the character XP that skill level-ups award), `OneHandedSkillHandler`, and `QuestManager.completeObjective`.
- **Tests**:
  - Scope semantics (U `ProgressionContextTest`).
  - Wiring of every producer and consumer (S).
  - **L end-to-end**: a debug-only activation through the real `ActivateAbilityHandler` and the real One-Handed handler deals damage but earns 0 XP; its projectile inherits the context; a later hit by that projectile earns 0 XP; skill XP inside the scope is refused. **Controls**: the identical action through a legitimate grant earns XP, its projectile is not marked, and its later hit earns XP. This proves the guard discriminates and does not just block everything.

**Not protected, documented as the contract for future systems** (javadoc of `ProgressionContext`):
- Vanilla advancements and vanilla experience orbs from mob kills. These are vanilla systems; covering them needs a combat-attribution decision.
- Context markers on spawned entities are runtime-only (not saved).
- Future Codex discovery, achievements, research, kill-based quest objectives and first-use records must check `inNonProgressionScope` / `suppresses` before awarding.

### 0.4 Access-loss and client lifecycle (correction 4)

- **Flight**: `PhysiologyPassive.onPassiveRemoved` now grounds the player only when the lost physiology granted flight, the player is not in Creative/Spectator, and no other accessible ability still grants flight. Traits use distinct modifier ids, so the remaining physiology's traits are untouched. Tests: U (`PhysiologyFlightRevocationTest`) and L (switching Kryptonian → Viltrumite keeps flight; losing the last source in Survival grounds; losing it in Creative keeps Creative flight).
- **Client state**: `ClientEntitlementView` is cleared on client JOIN and DISCONNECT, and the client ability view (`ClientAbilityManager`) on DISCONNECT, so a previous server's or world's access is never displayed. A dimension change keeps the same server session and view. Tests: U (`ClientEntitlementViewTest`), S (registration).
- **Invalidation**: `EntitlementService.invalidate` now always recomputes availability from current state. The availability diff moved into `EntitlementEngine.recomputeAvailability`. Previously a change was reported only if a cached entry had been dropped, so changes read by uncacheable decisions were never reported. Tests: U (a gain and a loss reported with nothing cached, no report without change, nothing before the baseline), S.

### 0.5 Scope kept

- Universal spell access stays **off by default**; local development opts in with `/totality entitlement debug universal_spells true` or `-Dtotality.entitlement.debugUniversalSpellAccess=true`.
- Compatibility data (legacy ability list, `bank_app_unlocked`) is retained.
- No changes to Classes, Spells, Phone, Codex or Technology, and no unrelated cleanup.

### 0.6 Revision 3 — flight state consistency (R2 follow-up)

**Problem.** When the final biological flight source was lost, `PhysiologyPassive.onPassiveRemoved` cleared Minecraft's `flying` / `mayfly` flags but left `PlayerMovementComponent.activelyFlying` set. `StaminaServerTick` gates both the flight stamina drain and the flight regen block solely on `movement.isActivelyFlying() && !player.isCreative()`. A player who lost flight mid-air therefore kept draining stamina, and could not regenerate, until landing. The client also kept believing it was flying.

**Fix (one file, `PhysiologyPassive`).** In the same last-source case as before (Survival/Adventure, the lost passive granted flight, no other accessible ability grants flight), biological flight now ends through the component's own `setActivelyFlying(false)`. That method clears `activelyFlying`, `flying` and `mayfly` together, updates abilities and syncs the movement state to the client. The cases where flight must stay are unchanged:
- another flight source remains;
- Creative or Spectator — neither the movement state nor game-mode flight is touched;
- Elytra gliding is not affected by these flags.

**Tests.**
- L (`EntitlementLiveVerification`, real `PlayerMovementComponent`):
  - flying in Survival engages the drain gate;
  - Kryptonian → Viltrumite keeps `activelyFlying`, `flying` and `mayfly`;
  - losing the last source clears all three, and the drain gate turns off;
  - Creative and Spectator each keep `mayfly` and `activelyFlying`.
- S: the passive uses `setActivelyFlying(false)` and no flag-only grounding remains; `setActivelyFlying` keeps state and flags consistent; `StaminaServerTick`'s drain and regen block are gated only on `isActivelyFlying()`.
- U: the existing `shouldRevokeFlight` decision test.

**Stamina drain.** Fake players are not in the server player list, so the live suite cannot run `StaminaServerTick` over them. It asserts the exact condition that tick uses (`isActivelyFlying() && !isCreative()`), and the source regression pins that this is the only gate. Watching the stamina bar on a real client is listed in §10.3.

**Out of scope, unchanged:** physiologies also grant Power Sprint; its state (`powerSprinting`) is still cleared by the existing per-tick sprint checks, not by passive removal.

### 0.7 Revision 4 — Creative / Spectator flight edge cases (R3 follow-up)

**Investigation.** New live checks were run first against the unchanged R3 code. **5 of 42 failed**, confirming two defects:

| Check (R3 code) | Observed state |
|---|---|
| Spectator, biological flight active: no flight drain | `drains=true` — only Creative was excluded |
| Spectator: last physiology lost → biological state ends | `activelyFlying=true`, drain and regen block still on |
| Spectator → Survival: nothing stale persists | `activelyFlying=true` with `mayfly=false`; drain and regen block still on |
| Creative: last physiology lost → biological state ends | `activelyFlying=true`, regen still blocked |
| Creative → Survival: nothing stale persists | `activelyFlying=true` with `mayfly=false`; drain and regen block still on |

1. **Spectator drained stamina for biological flight.** If stamina reached 0, `StaminaServerTick` would call `setActivelyFlying(false)`, which also clears Spectator's own `flying`/`mayfly`.
2. **R3 kept `activelyFlying` after the last source was lost in Creative/Spectator.** With nothing authorizing it, it blocked regen (and drained in Spectator), and it survived into a later return to Survival as an unauthorized flight state.

Normal Survival flight, the alternative-source case and Elytra gliding passed on R3 and still pass.

**Fix (smallest correction):**
- `PlayerMovementComponent.endBiologicalFlight()` (new): in Creative/Spectator it clears only `activelyFlying` (and syncs), leaving the game mode's flight permissions untouched; otherwise it is exactly the previous `setActivelyFlying(false)`.
- `PhysiologyPassive.onPassiveRemoved` calls it whenever the lost passive granted flight and no other accessible ability does — now in **every** game mode (`shouldEndBiologicalFlight(grantedFlight, otherFlightSource)`).
- `StaminaServerTick`: the flight drain also excludes Spectator. Both flight gates are now named methods (`drainsForBiologicalFlight`, `blocksRegenForBiologicalFlight`) that the tick uses directly and the tests call; their semantics are unchanged apart from the Spectator exclusion.

**Tests.**
- L: 12 flight checks on the real movement component and the real `StaminaServerTick` gates — all four requested scenarios, including both game modes and the return to Survival, Elytra gliding, normal Survival flight and the alternative source. 42/42 after the fix.
- U: `PhysiologyFlightRevocationTest` updated.
- S: the passive calls `endBiologicalFlight()`; Creative/Spectator keep their permissions; the tick uses the named gates; Spectator is excluded from the drain.

**Not changed (pre-existing, noted for a decision):**
- While a flight source is still present, biological flight in Creative/Spectator still blocks regen. Making regen follow the drain rule would be a one-line change.
- Switching game mode while biologically flying with a source still present lets vanilla reset `mayfly` while `activelyFlying` stays set (authorized, but inconsistent until landing). This is the existing game-mode-switch behaviour of the movement system and is out of scope.

---

## 1. Summary

The Entitlement API is implemented as a working, server-authoritative foundation and is already the access authority for every existing ability and spell, plus the Bank phone app.

**Implemented**
- Core (`api/entitlement`, `api/entitlement/requirement`): typed `EntitlementKey`, extensible registration of types / actions / Grant Source types / authorization policies / condition types / condition definitions / per-content definitions / grant providers / state contributors, with cross-registry validation; the `AllOf / AnyOf / Not / Condition` requirement engine with tri-state fail-closed evaluation, disclosure redaction, dependency tracking and a validated codec; the persisted player ledger (permanent facts, persisted grants, suspensions, orphan quarantine, migration markers, bounded audit log, revision, online-tick clock) on the existing component framework; the runtime grant index with deterministic, idempotent, provider-scoped reconciliation; operation-specific structured decisions; mutation transactions with post-commit events; availability tracking; an owner-only display-view sync.
- Integrations: baseline, Class (Barbarian), Origin/Species and Mastery grant providers; the debug-only universal spell provider (off by default); server revalidation in every ability packet handler (activate, toggle/channel, equip, select spell, favorite); Ability-system shutdown on access loss; versioned, idempotent legacy migrations (flat ability set, `bank_app_unlocked`); Bank app permanent unlock with quest provenance and audited admin reset; `/totality entitlement` audit / explain / admin / debug commands.
- Safety (revision 2): fail-safe providers/contributors, domain-owned enumeration, the progression-context contract with safeguards at existing sinks, flight-preserving access loss, client view lifecycle, unconditional availability recomputation (§0).
- Verification: 79 new JUnit tests and a 42-check live-world suite on the disposable verification server. Full build and all 28 live suites pass (§10).

**Partially implemented / not implemented** — see §11. Headline items: no per-menu view subscriptions (the whole owner view is pushed on change), no open-service session tokens (no security-sensitive phone service exists yet), no datapack loading of definitions, and no Codex / Technology / Cooking / Research / equipment / transformation integrations (those systems do not exist or are not yet designed).

---

## 2. Reconciliation: canonical specification vs. current code and new Totality decisions

### 2.1 Where the 2026-07-13 specification no longer matched the code (audited, not assumed)

| Spec statement | Actual code at `ed5f8cd` | Consequence |
|---|---|---|
| `ClassFeatureRegistry` is "currently used only by Barbarian" | It has **no registrations at all**. Barbarian's Rage/Unarmored Defense were granted ad hoc in `SelectClassHandler` and patched back in on every join in `PlayerConnectionEvents`. | `BarbarianClass` now registers its own rule with the class provider; both ad hoc writes removed. `ClassFeatureRegistry` left untouched. |
| Every spell reports `isDefault()` | True — and **non-spell** abilities also do (Harvest, Rest, Ground Slam). | Non-spell defaults are a genuine character baseline (new `totality:baseline` source); only spells go to the debug provider. |
| Origin grants are the only fully wired ancestry path | True, plus `/totality showancestry` also forgot abilities directly. A **Mastery → ability** path (Veinminer, `UnlockMasteryHandler`, `learnmastery`/`forgetmastery`) also wrote the flat set. | Ancestry and Mastery providers; all direct writes removed. |
| Phone app locks are client-only | True. The phone Bank app is **display-only** (balance view); deposits/withdrawals happen at the Banker teller, whose handler has no session validation. | Bank tile now reads the server's entitlement view; no server-side Bank-app action exists to revalidate. Teller validation is Economy scope (reported, not changed). |
| (not in spec) | `ToggleAbilityHandler` started channeled abilities **without any ownership check**. | Fixed: starting a channel is revalidated. |
| (not in spec) | Ability component syncs were sent to every **tracking** player, and the client applies any `abilities` payload to its *own* view. | Fixed by restricting to the owner (one-line `shouldSyncWith`); the new entitlement component is owner-only too. |
| (not in spec) | `Ability.onPassiveRemoved` was **never called**; forgetting an origin passive left its traits applied. | The Ability system now runs its own shutdown when the last access path ends. |
| (not in spec) | Passive abilities are ticked **twice per tick** (`AbilityServerTick` and `Totality.registerPassiveTicker`). | Pre-existing, **not changed** — reported only. |

### 2.2 Superseded assumptions (brief §2) and how the implementation honours them

- **A — D&D uses Totality.** Core has no D&D concept. The canonical `totality:dnd_spell_access` and `totality:phone_app_access` policies were deliberately **not** put in Core; only four content-agnostic policies exist (`unlock_or_grant`, `known_or_grant`, `owned_or_grant`, `requirements_only`). Spells are a generic `totality:spell` type with no level, slot, preparation or class-list semantics.
- **B — Classes are optional.** A classless character simply has no class grants. Each class held is evaluated independently against its own stored level; no overall-level formula or class-level cap is referenced. `PlayerClassComponent` is read through an adapter; the Class/Level design is untouched.
- **C — Species ≠ Origin.** `totality:species` and `totality:origin` are separate Grant Source types with independent provenance. Today's `OriginData` entries are species variants (e.g. Pureblood Viltrumite) whose `startingAbilities` carry innate abilities; the adapter attributes those to `totality:origin` as a **compatibility** mapping and does not establish it as permanent architecture. Species-level grants have a registration hook but no current content.
- **D — Spell Slots go away in Spells V2.** Entitlement has no slot dependency. `ActivateAbilityHandler` keeps its existing slot pre-check and successful-cast-only spend, now *after* the entitlement check. Spell knowledge is `DOMAIN_OWNED`: Entitlement **refuses** to store permanent spell facts, leaving that to Spells V2. Default spell access is an explicit debug grant.
- **E — Future systems.** Only extension contracts were added (types, policies, condition types, providers, contributors). No Codex, Technology, Research, Cooking, phone-model or dimension rules were invented.
- **F — Terminology.** `GrantSourceRef` / `GrantSourceTypes` model the **Grant Source** (Class, Origin, quest…). Content Source (D&D, Skyrim…) is not modelled and no tooltip/Content Source code was touched.

---

## 3. Architecture

```
api/entitlement/                      Core — no Totality gameplay knowledge
  EntitlementKey, EntitlementActions, EntitlementReasons, GrantSourceTypes, GrantSourceRef
  EntitlementTypeDefinition, EntitlementDefinition, EntitlementRuleSet, EntitlementRetentionPolicy
  EntitlementAuthorizationPolicy, EntitlementPolicies, EntitlementStateContributor, EntitlementGrantProvider
  EntitlementCatalog              all registries + cross-registry validation (INSTANCE + isolated test catalogs)
  EntitlementGrant, GrantLifetime, EntitlementClock(.Expiry), EntitlementSuspension
  PermanentEntitlementFact, PermanentFactRecord, EntitlementAuditEntry, OrphanedEntitlementRecord
  EntitlementLedger               persisted state + lenient codec
  PlayerEntitlementState          ledger + runtime grant index + caches
  EntitlementEngine               PURE decisions, mutations, reconciliation, expiry (no ServerPlayer)
  EntitlementService              ServerPlayer facade: clocks, commit pipeline, events, availability, sync
  EntitlementDecision, EntitlementSnapshot, AuthorizationPath, EntitlementDisplayState, EntitlementDisplaySnapshot
  EntitlementChange, EntitlementMutationResult, EntitlementEvents
  ProgressionContext              non-progression scope contract for debug-only execution (rev. 2)
  PlayerEntitlementComponent, EntitlementComponents
  requirement/                    EntitlementRequirement (+codec), RequirementEvaluator, RequirementEvaluation,
                                  EntitlementConditionType, EntitlementConditionDefinition, DisclosurePolicy,
                                  EntitlementDependencyKey, DisplayRequirementSummary
  client/ClientEntitlementView    advisory client copy of the owner's display view
api/entitlement/integration/          Totality adapters (the only place that knows abilities/classes/phone)
  TotalityEntitlements            registration, join/respawn sequence, change hooks, ability shutdown listener
  AbilityEntitlements             ability/spell types, keys, checks, accessible-set adapter
  Baseline/Class/Ancestry/Mastery grant providers, DebugSpellAccessProvider
  AbilitySelectionContributor, TotalityConditionTypes, PhoneAppEntitlements
  LegacyAbilityMigration, EntitlementCommands, EntitlementLiveVerification (dev-only)
```

The engine/service split is deliberate: every rule (requirements, policies, reconciliation, lifetimes, retention, debug isolation, disclosure, caching) lives in `EntitlementEngine`, which takes a nullable player only through the query context — so the whole rule set is unit-testable without a server. `EntitlementService` adds the `ServerPlayer`, the three clocks, events and synchronization.

---

## 4. Created and modified files

**Created — production (60 files, 5,394 lines):** everything under `src/main/java/zcylas/totality/api/entitlement/` (listed in §3): 38 core, 8 `requirement`, 1 `client`, 13 `integration` (including the dev-only `EntitlementLiveVerification`).

**Created — tests (14 files, 1,805 lines):** `src/test/java/zcylas/totality/api/entitlement/`
`EntitlementTestFixture`, `EntitlementIdentityAndCatalogTest`, `RequirementEngineTest`, `GrantProvenanceTest`, `EntitlementDecisionTest`, `EntitlementPersistenceLifecycleTest`, `EntitlementSecurityTest`, `integration/EntitlementIntegrationTest`, `integration/EntitlementWiringSourceRegressionTest`; revision 2: `SafeFailureTest`, `DomainEnumerationAndInvalidationTest`, `ProgressionContextTest`, `client/ClientEntitlementViewTest`, and `src/test/java/zcylas/totality/api/ability/impl/PhysiologyFlightRevocationTest`.

**Created — documentation:** this report.

**Modified (33 files, all surgical; CRLF files kept CRLF).** Revision 1 (23 files):

| File | Change |
|---|---|
| `Totality.java` | Register `TotalityEntitlements` and the live suite; passive ticker reads the accessible set. |
| `api/ability/AbilityComponent.java` | Flat `unlocked` → frozen `legacyUnlocked` (read/written unchanged); removed `unlock`/`forget`/default-unlock; `hasAbility` = deprecated adapter over Entitlement; `getAccessibleAbilities()`; sync sends only accessible abilities/selections; owner-only sync; selection changes invalidate the cache; selections no longer pruned on load. |
| `api/ability/AbilityRegistry.java` | Removed `defaults()` (orphaned by this change). |
| `api/ability/AbilityServerTick.java`, `networking/movement/MovementStaminaHandler.java` | Read the accessible set. |
| `networking/ability/{Activate,Equip,SelectSpell,Toggle}AbilityHandler.java` | Operation-specific server revalidation (`activate`, `equip`, `select`; toggle start newly checked). |
| `networking/ancestry/SelectAncestryHandler.java` | Direct forget/unlock replaced by ancestry-provider reconciliation. |
| `networking/classes/SelectClassHandler.java` | Ad hoc Barbarian unlocks removed (class provider via `ClassChangeReconciler`). |
| `networking/skills/UnlockMasteryHandler.java` | Direct unlock replaced by mastery-provider reconciliation. |
| `api/rpg/classes/ClassChangeReconciler.java` | The existing universal class-mutation seam also reconciles class grants. |
| `api/rpg/classes/barbarian/BarbarianClass.java` | Registers its own grant rule (Rage, Unarmored Defense). |
| `init/events/PlayerConnectionEvents.java` | Entitlement join sequence before any sync; respawn sequence; removed the Barbarian join patch. |
| `init/TotalityCommands.java` | `/totality entitlement …` subtree; `unlockability`/`forgetability`/`showancestry`/`learnmastery`/`forgetmastery`/`resetall` routed through Entitlement. |
| `init/TotalityClientSyncListeners.java` | Client listener for the entitlement display view. |
| `api/quest/QuestManager.java` | Banker hand-off writes the permanent Bank app unlock (quest provenance) instead of `bank_app_unlocked`; admin full reset revokes it (audited). |
| `screen/phone/PhoneAppGridScreen.java` | Bank tile reads `ClientEntitlementView`. |
| `screen/character/tabs/{Spells,Abilities}Tab.java` | Dropped the client-side `isDefault() ||` access shortcut. |
| `api/rpg/resources/verification/{CrownOfStars…,StandardSpellSlotMigration}Verification.java` | Test spell access via session debug grants instead of `abilities.unlock`. |

Revision 2 (8 more files, plus further edits to `Totality.java`, `AbilityServerTick`, `ActivateAbilityHandler`, `ToggleAbilityHandler`, `QuestManager`):

| File | Change |
|---|---|
| `TotalityClient.java` | Clear the entitlement view on JOIN/DISCONNECT and the ability view on DISCONNECT. |
| `networking/ability/ClientAbilityManager.java` | `clear()` (delegates to the existing `sync` with empty data). |
| `api/ability/impl/PhysiologyPassive.java` | End biological flight only when nothing else authorizes it (`shouldRevokeFlight`); rev 3: via `PlayerMovementComponent.setActivelyFlying(false)` so movement state, flags and stamina drain stay consistent. |
| `api/ability/impl/VeinminerAbility.java` | XP award runs in the scope when Veinminer is debug-only. |
| `api/rpg/skills/core/PlayerSkillsComponent.java` | `addSkillXp` refuses awards inside a non-progression scope. |
| `api/rpg/skills/core/OneHandedSkillHandler.java` | Skips XP when `ProgressionContext.suppresses(player, source)`. |
| `entity/magic/SpellBoltEntity.java`, `entity/magic/FireballProjectileEntity.java` | Hit / blast damage re-enters the caster's scope via `runForEntity` (body re-indented, logic unchanged). |
| `Totality.java`, `api/ability/AbilityServerTick.java` | Passive / toggle / channel ticks run in the scope when debug-only. |
| `networking/ability/ActivateAbilityHandler.java`, `ToggleAbilityHandler.java` | Activation / channel start run in the scope when debug-only. |
| `api/quest/QuestManager.java` | `completeObjective` refuses inside a scope. |

Revision 4 (2 more files, plus `PhysiologyPassive`):

| File | Change |
|---|---|
| `api/core/movement/PlayerMovementComponent.java` | `endBiologicalFlight()`: clears only biological state in Creative/Spectator, otherwise `setActivelyFlying(false)`. |
| `networking/stamina/StaminaServerTick.java` | Flight drain also excludes Spectator; drain and regen gates are named methods (semantics otherwise unchanged). |

---

## 5. Persistence, synchronization and server authority

- **Component:** `PlayerEntitlementComponent` (`totality:entitlements`) on the existing framework. Only the `EntitlementLedger` is saved (`ValueOutput.store("ledger", CODEC, …)`). Provider grants are never saved; they are rebuilt on join/respawn from their source systems.
- **Lenient codec:** each list entry decodes independently; an undecodable or future-format entry is kept verbatim in an `unreadable` section and written back unchanged. Nothing is silently dropped.
- **Orphans:** on load, records for unregistered content move to quarantine (never usable, never synced, shown by `audit`) and are restored automatically when the content is registered again.
- **Respawn:** custom respawn strategy — death drops `UNTIL_DEATH` grants (reported once the new entity exists); End return (lossless) keeps them; facts, persisted grants, suspensions, session grants carry over; providers re-reconcile in `AFTER_RESPAWN`.
- **Logout:** session grants and provider grants vanish with the component; `ONLINE_TICKS` expiry pauses (the counter is persisted and only advances online); `REAL_TIME_UTC` keeps running.
- **Revision:** persisted, monotonic, bumped on every committed change; cleared caches on bump.
- **Sync:** the raw ledger never leaves the server. The owner receives a display view: non-hidden `EntitlementDisplaySnapshot`s of client-view types (currently `phone_app`) with display state, reason code and disclosure-filtered requirement summaries — no provenance, no hidden ids. It is re-sent only when it changes. Ability access reaches the client through the existing ability sync, which now carries exactly the accessible set.
- **Authority:** no client→server packet can create, convert or remove an entitlement. Every protected ability action revalidates with `SERVER_ENFORCEMENT` (never cached) at execution time. The API supports `stale_client_state` for callers that send their last-seen revision; the existing ability payloads do not carry one (wire format unchanged).

---

## 6. Requirement evaluation and grant reconciliation

**Requirements.** `AllOf` (empty = true), `AnyOf` (empty = invalid), `Not` (requires an authored message), `Condition` (registered definition id). Evaluation is tri-state: `SATISFIED / UNSATISFIED / ERROR`. Missing conditions, throwing evaluators, empty `AnyOf` and over-deep trees are `ERROR`, which no combinator turns into success — including `Not`. Invalid condition definitions are refused at registration, so references to them fail closed. `AllOf` reports every failure; `AnyOf` reports the alternatives. Disclosure: `PUBLIC` (id + message), `REDACTED` (generic "unavailable" message, no id), `SECRET` (removed from client views); a `Not` inherits its child's secrecy. Condition types declare dependency keys and cacheability.

**Decision order** (deterministic, matches canonical §6.6): unregistered content → unsupported action → visibility (`HIDDEN`; held content is always visible to its holder; hide-suspensions hide) → explicit suspension (`temporarily_suspended`) → policy basis (`locked` / `not_known` / `missing_ownership`, with the acquisition requirement as the explanation) → persistent eligibility (durable paths only; `known_but_unavailable`, never forgets) → per-action requirement → `ALLOWED` with every authorization path. Display states: `HIDDEN, LOCKED, KNOWN_UNAVAILABLE, AVAILABLE_PERMANENT, AVAILABLE_SOURCE_BOUND, SUSPENDED, MISSING_REQUIREMENT`.

**Source failures (rev. 2).** Grants of an unverified provider are withheld (not erased) and explain a denial as `source_unverified`; a throwing contributor fails its decision closed; a throwing enumeration contributes no candidates (§0.1).

**Domain enumeration (rev. 2).** Candidates for bulk queries are permanent facts, grants, and keys enumerated by the owning domain's contributors (§0.2).

**Caching.** UI/tooltip decisions and per-type accessible sets are cached per revision and invalidated by revision bumps or targeted `invalidate(dependency)`; decisions that read uncacheable conditions or time-limited grants/suspensions are never cached. Per-tick consumers (passive ticking, movement modes, the debug-only check) read cached results. Server enforcement is always fresh. Any invalidation recomputes availability from current state, so changes are reported even when nothing about them was cached (§0.4).

**Reconciliation.** A provider's desired grants get deterministic ids from `(provider, source, key)`; the engine diffs them against **that provider's** current grants only. Same input twice ⇒ no change, no revision bump. Grants stamped with another provider, a source type the provider did not declare, a non-`WHILE_SOURCE_ACTIVE` lifetime, unregistered content or unregistered actions are rejected and logged. Provider grants cannot be removed by id; explicit removal of session/persisted grants requires the exact source; suspensions likewise. Multiplicity is redundancy only — decisions carry paths and provenance, never a magnitude. A grant becomes permanent only via `convertGrantToPermanent`, which refuses debug/non-progression grants. Permanent facts are refused for `SOURCE_BOUND` and `DOMAIN_OWNED` content and for debug provenance; acquisitions are idempotent and keep their original provenance; revocations are audited.

---

## 7. Existing gameplay integrations

| Area | Status | Behaviour |
|---|---|---|
| Ability access | **Implemented** | Entitlement is the authority. `AbilityComponent` keeps cooldowns, favorites, equipped ability, selected spell, toggles, channeling. |
| Server ability activation | **Implemented** | Activate / equip / select-spell / channel-start revalidated; favorite and Veinminer/Ground-Slam checks use the `hasAbility` adapter (`use`). |
| Access loss | **Implemented** | `AVAILABILITY_CHANGED` → the Ability system stops channels, toggles off, runs `onPassiveRemoved`. |
| Class grants | **Implemented (Barbarian)** | Rage + Unarmored Defense while the class is held, provenance `class:totality:barbarian`. Wizard/Warlock/Monk grant no abilities today, so they register no rules. Subclass provenance supported; covenant/patron not modelled (no content). |
| Species/Origin grants | **Implemented** | Origin `startingAbilities` (Viltrumite, Kryptonian) with origin provenance; species hook. Ancestry change swaps provenance and removes the old source. |
| Mastery grants | **Implemented** | Veinminer while its mastery rank ≥ 1. |
| Baseline abilities | **Implemented** | Harvest, Rest, Ground Slam for every character. |
| Spell access | **Implemented as debug** | Debug-only universal access (§9); no permanent spell knowledge anywhere. |
| Bank app | **Implemented** | Visible-but-locked; permanent unlock (quest provenance) on the Banker hand-off; legacy flag migrated; client tile reads the view. `has_account` is **not** added as an open requirement (behaviour preserved). |
| Other phone apps | **Not implemented** | Remain hardcoded placeholders; no phone models/tiers invented. |

---

## 8. Save migration and backward compatibility

- **Flat ability set (M2/M3), per id, versioned via ledger markers, idempotent.** Runs on join after all providers reconcile:
  - spell with `isDefault()` → **ignored as progression** (never permanent, even if debug access is on);
  - currently granted by a provider → source-bound, nothing stored;
  - recognizably source-bound (Class/Species/Origin/Ancestry/Mastery) but no current source → **not migrated** (stale; no provenance fabricated);
  - non-default spell → not migrated (no acquisition evidence);
  - unexplained non-source-bound ability → permanent `UNLOCKED`, source `legacy_migration:ability_component`, **non-progression**, flagged (no current ability falls in this bucket);
  - unregistered id → quarantined, left unprocessed so it migrates automatically if the content returns.
- The legacy list is **kept and written back unchanged** for one transition release; favorites, equipped ability and selected spell are **kept** (inaccessible ones are simply not shown or usable).
- **`bank_app_unlocked` (M7), migration id `totality:bank_app_flag_v1`.** Flag ≥ 1 → permanent Bank `UNLOCKED` (`legacy_migration:bank_app_unlocked`). The flag is left in place (no longer read) so an older build still works.
- Ambiguous cases are reported in the log and by `/totality entitlement audit`.

---

## 9. Security considerations

- No client mutation path; all access checks for protected actions run server-side at execution time; client data is advisory.
- Debug isolation: debug provenance forces `progressionEligible = false`; debug grants are session-only or provider-derived, never persisted, never convertible, never permanent. Universal spell access is **off by default** (`-Dtotality.entitlement.debugUniversalSpellAccess=true` or `/totality entitlement debug universal_spells true`, runtime only).
- Fail-closed requirements, unregistered policies deny, unregistered content is denied (hidden in UI).
- Cross-owner removal rejected and audited; duplicate grant ids are no-ops or rejected.
- Disclosure-filtered, owner-only sync; fixed the pre-existing ability-sync leak to tracking players.
- Listener loops are bounded (nested transaction depth 8, then rejected).
- Unverifiable sources never authorize: failed providers' grants are withheld, failed contributors fail decisions closed (§0.1).
- Debug-only execution runs in a non-progression scope honoured by every existing Totality progression sink (§0.3).
- Admin permanent unlocks are audited and marked non-progression.

---

## 10. Tests and verification results (all actually run on 2026-10-01)

### 10.1 Results

| Check | Command | Result |
|---|---|---|
| Baseline (before any change) | `./gradlew build` | BUILD SUCCESSFUL — 2,272 tests, 0 failures, 2 skipped |
| Revision 1 | `./gradlew build` | BUILD SUCCESSFUL — 2,331 tests, 0 failures |
| Revision 2 | `./gradlew build`; live server | 2,350 tests, 0 failures; 28/28 suites, Entitlement 33/33 |
| Revision 3 | `./gradlew build`; live server | 2,351 tests, 0 failures; 28/28 suites, Entitlement 36/36 |
| Revision 4, **before** the fix (new checks on R3 code) | `./gradlew runVerificationServer` | Entitlement **5/42 failed** (the §0.7 table) — issue confirmed |
| **Revision 4 full build** | `./gradlew build` | **BUILD SUCCESSFUL — 2,351 tests (2,272 + 79 new), 0 failures, 0 errors, 2 skipped**; all 228 result files freshly written |
| **Revision 4 live-world suites** | `./gradlew runVerificationServer` (verbose), stopped with `stop` via stdin | **BUILD SUCCESSFUL, exit 0**; all **28** suites passed, including `EntitlementLiveVerification` **42/42** |

No pre-existing failures and no regressions. The 4 ERROR lines in the server log are the Provisioner suite's intentional negative-path checks.

During revision 2 one live check failed on the first run: the *control* strike earned no XP. Its cause was a test defect — a raw `playerAttack` is cancelled by `VanillaDamageInterceptor` and replaced by a d20 roll that missed. While fixing it I found that spell damage is attributed to the caster, not to the projectile, so projectile marking alone could not protect delayed spell hits. That led to `runForEntity` in `SpellBoltEntity` and `FireballProjectileEntity`. The test now uses the real spell damage path (`TotalityDamage.hurt`) and both controls pass.

### 10.2 Independently tested behaviour (automated)

| Area | Evidence |
|---|---|
| Identity, registration, requirements, disclosure, codec | U: `EntitlementIdentityAndCatalogTest`, `RequirementEngineTest` |
| Multi-source grants, idempotent and provider-scoped reconciliation, conversion, multiplicity | U: `GrantProvenanceTest` |
| All decision states, stale client, caching, display view wire format without leaks | U: `EntitlementDecisionTest` |
| Persistence, quarantine, malformed records, death / respawn / logout, expiry clocks | U: `EntitlementPersistenceLifecycleTest`; L: real NBT round trip |
| Debug isolation, retention, duplicate ids, audit, suspension ownership | U: `EntitlementSecurityTest`; L: debug toggle |
| Provider / contributor / enumeration failures fail safe and recover | U: `SafeFailureTest` |
| Domain-owned enumeration, availability changes without prior cache | U: `DomainEnumerationAndInvalidationTest` |
| Debug-only execution earns no progression; legitimate execution does | U: `ProgressionContextTest`; S: every producer/sink; **L: end-to-end with controls** |
| Flight kept for Creative/Spectator and other sources, ended for the last source with consistent movement state and drain gate | U: `PhysiologyFlightRevocationTest`; S: movement/stamina gating; L: six flight checks on the real movement component |
| Client view cleared across sessions | U: `ClientEntitlementViewTest`; S: JOIN/DISCONNECT registration |
| Class / Origin / Mastery / baseline providers, legacy and Bank migrations, server revalidation, access-loss shutdown | U: `EntitlementIntegrationTest`; S: `EntitlementWiringSourceRegressionTest`; L: class, origin, migration, Bank checks |

### 10.3 Still requires a real Minecraft client (manual)

These need a real client connection, real UI and real projectile flight, which the dedicated server with fake players cannot provide:

1. **Join on an existing pre-change save** (`./gradlew runClient`): `/totality entitlement audit` shows the legacy list, ignored development spells, baseline / Class / Origin provenance; favorites unchanged.
2. **Spell tab and debug toggle**: Spells tab empty by default; `/totality entitlement debug universal_spells true` shows and casts spells (`explain totality:spell totality:fireball totality:activate` shows `[DEBUG]`); `false` hides them again.
3. **Debug casting earns nothing, in real flight**: with debug access on and a sword in hand, cast Fire Bolt and Fireball at a mob. One-Handed XP in the Skills menu must not change. With debug off and the same spell legitimately granted (`/totality entitlement unlock` is refused for spells; use a class/quest grant once Spells V2 exists), XP changes as before. The live suite covers the code path with a stand-in entity, not real bolt/fireball flight.
4. **Class and origin changes**: Barbarian → another class removes Rage / Unarmored Defense and ends an active Rage. Kryptonian with Heat Vision channelling → Viltrumite stops the channel and keeps flight. In Creative, any origin change keeps creative flight.
4a. **Flight stamina (rev 3)**: in Survival, fly as Kryptonian high in the air and watch the stamina bar drain; run `/totality showancestry` mid-air and pick a non-flying origin. The player must start falling, the HUD must stop showing flight, and stamina must stop draining and start regenerating immediately rather than on landing. Repeat as Kryptonian → Viltrumite: flight continues and drain continues.
4b. **Creative/Spectator (rev 4)**: fly biologically, switch to Spectator, `/totality showancestry` and pick a non-flying origin — Spectator flight continues and the stamina bar does not drain. Switch to Survival: no flight HUD, stamina regenerates. Repeat in Creative.
5. **Phone**: before Mobile Banking the Bank tile is locked; after the Banker hand-off it unlocks immediately and stays unlocked after relog; `/totality reset quest mobile_banking` locks it again (shown in `audit`).
6. **Session change**: unlock the Bank app on world A, disconnect, join world B where it is locked; the tile must show locked immediately.
7. **Death and respawn**: abilities restored; `audit` unchanged except provider grants re-listed.

---

## 11. Known limitations, deferred work and open issues

**Partially implemented**
- Display synchronization pushes the owner's whole (small) view on change; per-menu subscriptions (§7.4) are not implemented.
- `stale_client_state` is supported by the API but no existing payload sends a revision.
- Definitions, condition definitions and rule sets are code-registered; the requirement-tree codec exists, but there is no datapack loader or reload swap (§12, §14.17).
- Only five condition types exist (`has_class`, `class_level_at_least`, `species`, `origin`, `narrative_flag_at_least`); no current content uses them. Narrative-flag conditions are uncacheable (flags have no change signal). A key whose decision is uncacheable is re-evaluated on each bulk query.
- Events: one sealed `MUTATION` event plus `AVAILABILITY_CHANGED` instead of the canonical list of event classes. `GrantAdded` events also fire when join reconciliation rebuilds grants; listeners must not treat them as new acquisitions (availability events are suppressed until the post-join baseline).
- **Progression context**: it covers Totality's own sinks (skill XP and the character XP from skill level-ups, One-Handed damage XP, quest objectives) and the spell entities that exist today (`SpellBoltEntity`, `FireballProjectileEntity`). Not covered:
  - vanilla advancements and vanilla XP orbs from kills;
  - entity markers after a reload (markers are runtime-only);
  - melee by a player whose *passive* (e.g. a debug-granted physiology) only boosts damage — the hit is the player's own action.
  Future spell entities and progression systems must adopt the contract.
- **Failures**: a provider that keeps failing is retried every 5 s and logs one error per failure episode; contributor failures are retried on every query and logged once per contributor.

**Not implemented (systems absent or undesigned)**
- Open-service session tokens (§7.7); dimension-change invalidation (no dimension conditions); equipment/attunement, status-effect and transformation providers; covenant/patron provider; Codex, Cooking, Research, schematic, vanilla recipe-book contributors (the enumeration contract now exists for them); phone models/tiers/installation/downgrade policies; persistent debug grants.
- Phase M11 cleanup (retained on purpose): legacy `unlocked` list and `bank_app_unlocked` flag are still written; the unused `ClassData.startingAbilities`, `SubclassData.startingAbilities` and `CovenantData.grantedAbilities` fields remain; `ClassFeatureRegistry` is unchanged.

**Pre-existing issues found, not changed**
- Passive abilities ticked twice per tick (`AbilityServerTick` + `Totality.registerPassiveTicker`); both now apply the same progression scope.
- `BankTellerHandler` deposit/withdraw has no teller-session validation (Economy scope).
- `OneHandedSkillHandler` awards One-Handed XP for *any* damage attributed to a player holding a one-handed weapon, including legitimately cast spells; only the debug case was changed.

---

## 12. Deliberate deviations from the canonical specification

1. **No D&D policies in Core** (`dnd_spell_access`, `phone_app_access` omitted) — required by the new "D&D uses Totality" decision; owning systems register their own.
2. **Spell knowledge is `DOMAIN_OWNED`** — Entitlement refuses permanent spell facts until Spells V2 owns them; prevents any migration from contaminating saves. Spells V2 exposes its knowledge through a contributor with `enumerate`.
3. **Single catalog object** instead of seven registry classes — one place for cross-registry validation; the same registration points exist as methods.
4. **Condition types return `boolean`**; the core builds the structured `RequirementEvaluation` from the registered definition (message, disclosure, dependencies). Simpler for implementers, same result shape.
5. **Tri-state requirement outcome** (adds `ERROR`) so `Not` cannot invert a broken condition into a pass.
6. **Held content is always visible to its holder**; visibility rules only hide content the player does not hold (a learned-but-undiscovered technique stays usable — §9.7).
7. **Only types with real content are registered** (`ability`, `spell`, `phone_app`) rather than the canonical list of 15; the others register when their systems exist.
8. **Added `totality:baseline` and `totality:mastery` Grant Source types** for the character baseline and the existing Mastery → ability path, which the spec did not anticipate.
9. **Spec fields omitted as unused today:** `TemporaryFactContribution`, grant `tags`, `additionalEvidence`, presentation/icon/category metadata.
10. **Legacy flag and list are kept (not removed after migration)** for one transition release so an older build can still read the save.
11. **Admin permanent unlock is marked non-progression** — it was not legitimately earned.
12. **(rev. 2) `totality:source_unverified`** reason code and the unverified-provider state are additions; the spec defines no failure semantics for providers or contributors.
13. **(rev. 2) Enumeration lives on `EntitlementStateContributor`** rather than a separate registry, because the same owning API supplies both the state and the list.
14. **(rev. 2) `ProgressionContext`** is a new cross-system contract implementing §4.6 / §10.10 ("debug access must not award progression") at execution time; `progressionEligible` alone only covered Entitlement's own records.

---

## Decisions for the owner

1. **Multiclassing into Barbarian now grants Rage and Unarmored Defense** (previously only a first-class Barbarian got Rage), consistent with the Rage resource, which was already granted on holding the class.
2. **When to perform M11 cleanup** (stop writing the legacy list and flag; remove the unused class grant fields).
3. **Should the Bank app's `open_service` require an open account** (`has_account`)? Not added, to preserve current behaviour.
4. **(rev. 2) Vanilla advancements / XP orbs from debug-cast kills** are not suppressed. Covering them needs a decision on kill attribution (combat scope).
5. **(rev. 2) Legitimately cast spells still earn One-Handed XP while a sword is held** (pre-existing). Should spell damage stop counting toward One-Handed?
6. **(rev. 4) Should biological flight in Creative/Spectator, with a valid source, still block stamina regen?** Unchanged; a one-line change if not.

Resolved by your review: universal spell access stays off by default.
