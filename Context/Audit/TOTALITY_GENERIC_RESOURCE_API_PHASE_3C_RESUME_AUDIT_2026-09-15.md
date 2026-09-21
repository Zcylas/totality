# Totality Generic Player Resource API — Phase 3C Resume Audit

**Date:** 2026-09-15
**Branch:** feature/general-resource-api
**HEAD:** 73380f9 ("feat: add wearable Shinigami Robe")
**Scope:** Read-only audit. No code was modified to produce this report.
**Method:** Reconstructed from `git log` on this branch, the ~90 implementation-report documents in `Context/Audit/`, and direct reading of current production source under `src/main/java/zcylas/totality/`. Three parallel research passes were cross-checked against each other and against current code; every claim below traces to a specific commit or file.

---

## 1. Executive Summary

- **The premise that "Phase 3C was believed not to have started" is wrong.** Phase 3C (commit `69717c6`, "Migrate client resource presentation consumers", 2026-07-31) landed, and it is **complete** — not partial. All six consumers named in the original brief, plus a seventh (`OverviewTab`), are migrated to read through `ClientResourcePresentationResolver`.
- **Last confirmed completed phase: Phase 3C**, not 3B-3. The 3B-3 checkpoint (`6a2a2b5`) closed Phase 3B and left a readiness matrix showing 3 of 6 consumers "blocked." Phase 3C's own report explicitly overrode that framing — it migrated the blocked three anyway, judging the underlying staleness gaps to be legacy-system symptoms that consumer migration would shrink, not something that needed fixing first.
- **The three historical blockers do not currently manifest as live problems**, with one caveat:
  - **Mana/FormulaResolver**: the specific code path the old blocker worried about (`FormulaResolver.tryCast()`) is dead code — zero production callers. The real mana-spend entry point (`GrimoireItem.use()`) is server-gated before any mana check runs. Not an issue.
  - **Rage maximum sync**: the server-side clamp (`PlayerChargesComponent.updatePoolMax`) exists and is wired to the level-up path that's the only thing that changes Rage's max. Confirmed correct at the source. **Not independently re-verified**: whether the client-side `applySyncPacket` path (the thing the 3B-3 report specifically worried about) correctly reflects a live max increase — flagged as a residual uncertainty, see §11.
  - **Rage dimension transfer**: a real structural asymmetry exists — the *legacy* bespoke Rage sync channel has no dimension-change resync hook, but the *generic* sync channel does. Because consumers now prefer the generic result, the user-visible symptom is masked. The underlying legacy gap is still there; it would resurface if a consumer ever reverted to legacy-first.
- **The Generic Resource API today is a query + sync + presentation layer, not a mutation authority.** All 7 active resources (Health/Food/Breath/Mana/Stamina/SpellSlots/Rage) are query-only. Every gram of actual spend/regen/clamp logic still lives in the pre-existing legacy managers (`PlayerManaManager`, `PlayerStaminaManager`, `SpellSlotComponent`, `PlayerChargesComponent`). This is a real architectural ceiling worth naming explicitly before scoping further work.
- **Biggest actual blockers going forward are not Phase 3C** (it's done) but: (1) Thirst/Sanity/Ki are registered IDs with **zero backing implementation** — no adapter, no grant provider, no instantiation, and Ki has no maximum at all; (2) Food/Hunger has **no** 0-100 native authority — `totality:food` is a thin display-only ×5 multiplier over vanilla's real 0-20 `FoodData`; (3) Reiryoku/Chakra don't exist anywhere except two unrelated particle-animation doc-comment mentions.
- **The D&D Potion of Healing vertical slice was built** (commit `6998d32`, 2026-07-30, *before* Phase 3C) and is **fully standalone** — it doesn't touch the Resource API or even the existing Alchemy API. It validated nothing reusable for the Resource API; it's parallel content, not a precursor.
- Your own memory record (`project_resource_api_readiness_audit` in `MEMORY.md`) is stale — it describes "Phase 3A implemented, NOT yet committed" and doesn't mention 3B-1 through 3C at all. The repository is five phases and ~9 weeks ahead of that memory snapshot.

---

## 2. Git / Phase Reconstruction

### 2.1 Chronological commit table

| # | Commit | Date | Phase | Description |
|---|---|---|---|---|
| 1 | `debfcee` | 2026-07-18 | **Phase 1** | Inert registry/definition/state scaffolding. Zero resources registered, zero behavior change. |
| 2 | `0cd2b66` | 2026-07-19 | **Phase 2A** | Health + Food become the first real (query-only, `EXTERNAL_ADAPTER`) resources. |
| 3 | `03c85a8` | 2026-07-19 | **Phase 2B** | Breath added as a third query-only adapter. No HUD change. |
| 4 | `fbb836a` | 2026-07-19 | **Phase 2C** | Mana + Stamina added as *transitional* adapters over the legacy `PlayerResourceComponent`. |
| 5 | `1bc199f` | 2026-07-20 | **Phase 2D** | Spell Slots added — first `PARTITIONED_POOL` resource; extends the query model itself. |
| 6 | `ab8c534` | 2026-07-20 | **Phase 2E** | Rage added — completes the 7-resource registry. |
| 7 | `0085750` | 2026-07-22 | **Phase 3A** | Server→client sync contract: full/delta payloads, dirty tracking, resync protocol. |
| 8 | `2fbdee9` | 2026-07-23 | **Phase 3B-1** | Client-side query façade (`ClientResourceService`). **No production consumer wired yet.** |
| 9 | `4b3d7ce` | 2026-07-25 | **Phase 3B-2A** | Pure shadow-parity comparison model. No integration. |
| 10 | `5ee736c` | 2026-07-25 | **Phase 3B-2B** | Wires the parity comparators to real client managers; diagnostic-only. |
| 11 | `59916f1` | 2026-07-28 | **Phase 3B-2C** | Bounded DEBUG logging for parity mismatches. |
| 12 | `6a2a2b5` | 2026-07-29 | **Phase 3B-3 (checkpoint)** | `/totalitydebug resource parity` command; closes Phase 3B with a per-consumer readiness matrix (3 ready, 3 blocked). |
| 13 | `6998d32` | 2026-07-30 | *unrelated* | D&D Potion of Healing (standalone) + combat notification/Bless fixes. |
| 14 | `a3dd91c` | 2026-07-31 | *unrelated* | Semantic tooltip foundation. |
| 15 | `69717c6` | 2026-07-31 | **Phase 3C** | Consumer migration — all 6 known consumers + OverviewTab wired to `ClientResourcePresentationResolver`. |
| 16 | `07d5200` | 2026-07-31 | *cosmetic follow-on* | HUD/chat layout polish. Explicitly does not touch the resolver or resource wiring. |
| 17 | `9ac1a92` | 2026-08-01 | *follow-on* | Stamina Exhaustion→Depletion rename + join-lifecycle fix. |
| 18 | `4b29d27` | 2026-08-01 | **Post-3C, Resource-API-adjacent** | "Register dormant player resources" — adds Thirst/Sanity/Ki as scaffolding-only IDs. New scope, not part of Phase 1–3C. |
| 19 | `76e88ed` | 2026-08-01 | *follow-on* | Disables vanilla passive Health regen (mixins), unrelated to the Resource API's own logic. |
| 20 | `2a82709`, `73380f9` | 2026-09-10 | *unrelated* | Asauchi model, Shinigami Robe — cosmetic equipment, ~5.5-week gap after prior commit. |

Evidence: `Context/Audit/TOTALITY_RESOURCE_API_PHASE_1_IMPLEMENTATION_REPORT.md` through `..._PHASE_3B3_PARITY_INSPECTION_IMPLEMENTATION_REPORT.md`, `Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE_3C_CONSUMER_MIGRATION_IMPLEMENTATION_REPORT.md`, `Context/Audit/TOTALITY_DORMANT_RESOURCE_REGISTRATION_IMPLEMENTATION_REPORT.md`.

### 2.2 What each phase actually delivered

- **Phase 1** — `PlayerResourceRegistry`, `PlayerResourceDefinition`, `PlayerResourceStateComponent` (new component `totality:resource_state`), `ResourceGrantInitialization`. 17 new files, zero resources registered.
- **Phase 2A–2E** — one resource family added per commit: Health/Food (2A, native adapters), Breath (2B, adds `Optional<ResourceSnapshot>` failure modeling), Mana/Stamina (2C, explicitly "transitional" adapters over the pre-existing legacy component), Spell Slots (2D, first partitioned resource, extends the query model), Rage (2E, completes the 7-resource set).
- **Phase 3A** — the sync contract: full/delta wire snapshots, per-player dirty tracking, resync-request protocol, one-line dirty-mark hooks added into the legacy managers' setters.
- **Phase 3B-1** — the client query façade, registered but **unconsumed** — this is the exact gap Phase 3C closes.
- **Phase 3B-2A/2B/2C** — a shadow-parity diagnostic subsystem: pure comparators → wired to real client state → bounded logging. Never a gameplay data source, always diagnostic.
- **Phase 3B-3** — the human-facing verification tool (`/totalitydebug resource parity`) and the closing readiness matrix that the original task brief quoted (accurately, as a snapshot of that moment).
- **Phase 3C** — the actual UI migration. See §5.

---

## 3. Current Architecture

*(package root: `zcylas.totality.api.rpg.resources`, plus `zcylas.totality.networking.resource` and `zcylas.totality.client.resource`)*

- **`PlayerResourceIds`** — stable `Identifier` constants: `HEALTH, FOOD, BREATH, MANA, STAMINA, SPELL_SLOTS, RAGE` (7 active) + `THIRST, SANITY, KI` (3 dormant). Comment explicitly notes `totality:fatigue` and `totality:temperature` are deliberately absent.
- **`PlayerResourceDefinition`** — immutable record describing a resource's model (scalar/partitioned), authority, unit scale, min/max, capabilities, and lifecycle. Never mutated after registration.
- **`PlayerResourceRegistry`** — singleton registry with structural validation on `register()` and a `freeze()` step that cross-validates every `EXTERNAL_ADAPTER` definition actually has a registered adapter.
- **`PlayerResourceService`** — the server-side query façade. `query(Player, Identifier)` routes to either an external adapter's `snapshot()` or `PlayerResourceStateComponent`'s generic-component state. Never fabricates a value on failure — returns one of 9 named `ResourceQueryFailureReason`s instead.
- **`PlayerResourceStateComponent`** — the storage for `GENERIC_COMPONENT`-authority resources (i.e., Thirst/Sanity/Ki today). Registered under a **new** component id `totality:resource_state`, distinct from the legacy `totality:resources` component that still owns Mana/Stamina. **Nothing in production code calls its `sync()`, `instantiateScalar`, or `instantiatePartitioned` methods** — confirmed dormant.
- **`ProductionResourceDefinitions`** — the single registration entry point (`register()`), called once from mod init. Registers all 10 definitions, 7 adapters, freezes both registries.
- **`ResourceLifecyclePolicy`** — a policy record (death/persistence/init behavior). **All 10 resources use the unmodified `DEFAULT`** — no resource currently overrides it, and it's inert in practice since the 7 active resources never enter the component's storage and the 3 dormant ones have no state to act on.
- **External adapters** (`api/rpg/resources/external/*`) — 7 query-only classes, one per active resource, each wrapping a pre-existing legacy source: `HealthResourceAdapter`/`FoodResourceAdapter`/`BreathResourceAdapter` wrap vanilla directly (`clientMirrorMode = NATIVE_SYNCHRONIZATION`); `ManaResourceAdapter`/`StaminaResourceAdapter`/`StandardSpellSlotsResourceAdapter`/`RageResourceAdapter` wrap Totality's own legacy managers (`clientMirrorMode = LEGACY_BESPOKE_SYNCHRONIZATION`).
- **Server sync layer** (`networking/resource/*`) — `ResourceSyncManager` runs once per server tick, sends full/delta payloads to each player. Its eligibility gate excludes only the 3 `NATIVE_SYNCHRONIZATION` resources (Health/Food/Breath — vanilla already syncs those); the 4 `LEGACY_BESPOKE_SYNCHRONIZATION` resources (Mana/Stamina/SpellSlots/Rage) **are** synced generically, in parallel with their own existing bespoke packets. `ResourceSyncLifecycleEvents` schedules a full resync on join, respawn, **and dimension change**.
- **Client query façade** (`api/rpg/resources/client/*`, Phase 3B) — `ClientResourceService`, the trusted public client-side query layer, backed by `NativeClientResourceReader` (Health/Food/Breath) and `GenericSyncClientResourceReader` (Mana/Stamina/SpellSlots/Rage, reading the Phase 3A wire state).
- **`ClientResourcePresentationResolver`** (Phase 3C) — the one shared presentation entry point. `resolveScalar`/`resolvePartition` prefer any valid generic result over a caller-supplied legacy fallback, falling back only when the generic result is genuinely `Unavailable`. Never mutates.
- **Parity/diagnostic subsystem** (~20 classes under `client/resource/parity/`) — continuous shadow comparison between the generic and legacy client views, classifying drift (`EXACT_MATCH`/`TRANSITIONAL_MISMATCH`/`PERSISTENT_MISMATCH`/etc.), surfaced only through the dev-only `/totalitydebug resource parity` command.
- **Debug tooling** — `ClientResourceParityInspectionCommand` registers `/totalitydebug resource parity` (deliberately *not* `/totality ...` — an earlier attempt under that root broke server command routing; documented in-file as a confirmed regression that was fixed). Registered only in dev environments — structurally absent from normal launches.

---

## 4. Registered Resource Inventory

| Resource ID | Model | Current value source | Max value source | Lifecycle status | Persisted? | Synced to client? | Wraps |
|---|---|---|---|---|---|---|---|
| `totality:health` | Scalar | `player.getHealth()` | `player.getMaxHealth()` | Active (native adapter) | No — vanilla owns it | Native only | Vanilla |
| `totality:food` | Scalar | `getFoodData().getFoodLevel()` | fixed `20` | Active (native adapter) | No — vanilla owns it | Native only | Vanilla `FoodData` (0-20) |
| `totality:breath` | Scalar | `getAirSupply()`, clamped `[0,max]` | `getMaxAirSupply()` | Active (native adapter) | No — vanilla owns it | Native only | Vanilla air supply |
| `totality:mana` | Scalar | legacy `PlayerResourceComponent.getMana()` | `PlayerManaManager.getMaxMana()` | Active, transitional adapter | No — legacy component owns it | **Yes**, generic sync in parallel with bespoke packet | `PlayerResourceComponent`/`PlayerManaManager` |
| `totality:stamina` | Scalar | legacy `PlayerResourceComponent.getStamina()` | `PlayerStaminaManager.getMaxStamina()` | Active, transitional adapter | No | Yes, parallel | Same legacy component |
| `totality:spell_slots` | **Partitioned** (10 levels) | `SpellSlotComponent` per-level max−used | per-level max, same component | Active adapter | No | Yes, parallel | `SpellSlotComponent` |
| `totality:rage` | Scalar | `PlayerChargesComponent` pool for `barbarian_rage` | same pool's max | Active adapter | No | Yes, parallel | `PlayerChargesComponent` |
| `totality:thirst` | Scalar | **none** | `100` (declared, unreachable) | **Dormant — scaffolding only** | No | No (never instantiated → omitted from every packet) | Nothing |
| `totality:sanity` | Scalar | **none** | `100` (declared, unreachable) | **Dormant — scaffolding only** | No | No | Nothing |
| `totality:ki` | Scalar | **none** | **none at all** (no `authoredBaseMaximum`) | **Dormant — least complete** | No | No | Nothing |

**Consumers, known issues, per resource:** see §5 and §6 below for Mana/Stamina/Breath/SpellSlots/Rage; Health/Food consumers are the main HUD health/hunger bars (unaudited in this pass — out of scope, no known issues reported anywhere in the source history). **Ki** additionally has no maximum-resolution framework of any kind (`ResourceMaximumResolver` does not exist) — a query for it today would fail `MAXIMUM_UNAVAILABLE` even if state existed.

**Resources you didn't list but that exist:** none beyond the 10 above. No Fatigue/Temperature/Nutrition resource exists (`PlayerResourceIds`' own comment confirms Fatigue/Temperature were deliberately excluded from this registration pass).

---

## 5. Phase 3C Consumer Audit

| Consumer | File | Status | Evidence |
|---|---|---|---|
| Stamina HUD | `client/renderer/hud/TotalityHudRenderer.java:185-189` | **Migrated** | `ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.STAMINA, ...)`, legacy `ClientStaminaManager` as fallback only |
| Mana HUD | same file, `:190-194` | **Migrated** | Same pattern, `ClientManaManager` fallback |
| Breath presentation | — | **Not applicable** | No custom Breath UI consumer exists at all, before or after 3C — `TotalityHudRenderer.register()` only replaces Health/Armor/Food bars; vanilla's own bubble HUD is untouched and is what players see. The "READY" classification at the 3B-3 checkpoint meant "trivial if built," not "already built." |
| Spell-radial Spell Slots | `screen/ability/SpellRadialScreen.java:164-176` | **Migrated** | `resolvePartition(SPELL_SLOTS, level, ...)`, `ClientSpellSlotManager` fallback |
| Rage secondary HUD | `TotalityClient.java:224-251`, rendered via `client/hud/resource/SecondaryResourceHud.java` | **Migrated** | `ISecondaryResource` impl's getters both call `resolveScalar(RAGE, ...)` |
| Class-tab Rage display | `screen/character/tabs/ClassTab.java:360-364` | **Migrated** | Same pattern, `ChargeComponents` fallback |
| *(bonus, not in original 6)* OverviewTab Mana/Stamina | `screen/character/tabs/OverviewTab.java` | **Migrated** | Confirmed present in the 3C commit's diff and via source grep |
| *(explicitly out of scope)* Stamina-gated movement | `TotalityMovementHandler` | **Intentionally not migrated** | Gameplay authority stays on `ClientStaminaManager` — Phase 3C was presentation-only by design |

**Why the previously-"blocked" three now work:** the 3B-3 checkpoint's blocker framing was about *legacy-side* staleness (a rune-cast path that spends Mana without a legacy sync packet; a Rage-max-sync concern). Phase 3C's actual policy decision (stated in its own report and echoed in a `TotalityClient.java:142-149` code comment) was to migrate to the generic, resolver-mediated view regardless — because that view is fed by the **parallel generic sync channel** (§3), which is independent of the legacy packet path the blockers were worried about. Concretely: `RageResourceAdapter.snapshot()` *would* fail client-side if called directly, but no production code calls it that way — the client instead reads the already-synced generic wire state through `GenericSyncClientResourceReader`. This is a real, traceable fix mechanism, not just a relabeling.

**Old 3B-3 classifications are no longer accurate as a live picture of the app** — they were correct as a snapshot of the pre-3C code, but Phase 3C changed the facts on the ground for all three "blocked" items the same day.

---

## 6. Historical Blocker Verification

### Mana / FormulaResolver
- `FormulaResolver.tryCast()` — the method the old concern named — is **dead code**: zero callers anywhere in `src/main/java`.
- The real mana-spend entry point, `GrimoireItem.use()`, returns success and exits **before** touching mana at all when called client-side (`if (level.isClientSide()) return InteractionResult.SUCCESS;` precedes the mana check). The mana check only ever runs against a genuine `ServerPlayer`.
- `PlayerManaManager`'s getters/setters are themselves guarded (`!(player instanceof ServerPlayer)` → safe no-op), so there's no crash path even if something did call them client-side.
- Client Mana display is resolver-driven (§5) and fed by the parallel generic sync channel, independent of the bespoke packet.
- **Conclusion: this blocker does not manifest in current code.** It may have referred to a code path that was never actually live in production, or was fixed silently as part of the adapter work.

### Rage maximum synchronization
- Source of truth: `PlayerChargesComponent`, attached directly to `ServerPlayer`.
- `PlayerChargesComponent.updatePoolMax(id, newMax)` clamps `current = min(current, newMax)` on every call, and is invoked from `BarbarianRageAbility.updateChargePool()`, which fires on every Barbarian class level-up (the only path that changes Rage's max) — and this clamp call also triggers the pool's own `sync()`.
- **Server-side clamp: confirmed correct and reachable.**
- **Not independently verified**: whether the client's `applySyncPacket` handling of that resulting packet correctly reflects an *increased* max (the specific angle the 3B-3 report flagged). This is the one open thread from the historical blocker list — see §11.

### Rage dimension transfer
- The **generic** sync channel has an explicit, carefully-ordered dimension-change resync (`ResourceSyncLifecycleEvents` on `AFTER_PLAYER_CHANGE_LEVEL`, paired with a client-side clear on `AFTER_CLIENT_LEVEL_CHANGE` that's provably ordered to not race the server's resync).
- The **legacy** bespoke Rage channel has **no** dimension-change resync hook anywhere — confirmed by reading the join/respawn sync call sites, which don't include a dimension-change case for `ChargeComponents.PLAYER_CHARGES`.
- Because production consumers now prefer the generic result (§5), this asymmetry is currently **masked, not eliminated**. The server-side value itself is never actually lost on dimension change (the component lives on the same `ServerPlayer` instance across the transition) — this was a *display* desync risk, and the risk should no longer be user-visible given the migration, but the underlying legacy gap remains latent.
- No commit message, test name, or code comment was found confirming this bug was ever specifically observed/tracked by name — the conclusion above is inferred from tracing the current wiring, not from a rediscovered ticket.

---

## 7. Potion of Healing Audit

- **Implemented**: commit `6998d32`, "Add healing potion and combat feedback", 2026-07-30 — chronologically **between** Phase 3B-3 (`6a2a2b5`) and Phase 3C (`69717c6`).
- **Fully standalone.** Confirmed both by its own implementation report ("unrelated to the Generic Player Resource API in every respect — no Resource API parity/synchronization file was touched") and by reading current source: `HealingPotionItem.java` has zero imports of any Resource API class and heals via a direct vanilla `LivingEntity.heal(float)` call.
- **Also independent of the pre-existing Alchemy API** — a deliberate design constraint. It's a plain `Item` (not `AlchemyPotionItem`), with its own `HealingAmount`/`HealingRollResult` types (deliberately not reusing the combat package's `DamageRoll`), registered under a distinct id (`totality:dnd_potion_of_healing`) to avoid colliding with the existing Alchemy `totality:potion_of_healing`. A follow-up review-correction pass even removed an indirect Alchemy dependency from its client rendering.
- **Did not validate or introduce any reusable Resource behavior** — it doesn't touch resource definitions, adapters, sync, or the persistence layer at all.
- **Not a deliberate precursor to Phase 3C.** Neither its implementation report nor its review-correction report references the parity system, the consumer-migration concern, or informs any Phase 3C decision. It reads as parallel "standalone test content," incidental to the Resource API timeline rather than a gate for it.

---

## 8. Food / TotalityFoodItem Audit

- **Vanilla `FoodData` (0-20) is still fully authoritative.** The only two Totality mixins touching it (`FoodDataNaturalRegenerationMixin`, `ServerPlayerPeacefulRegenerationMixin`, both 34 lines, commit `76e88ed`) *only* suppress the two automatic passive-heal branches (Food-driven regen, Peaceful auto-heal). They explicitly do **not** touch the starvation branch, saturation/hunger depletion, or the vanilla `NATURAL_HEALTH_REGENERATION` gamerule.
- **No 0-100 migration code exists.** Grep for `NutritionProfile`, `FoodDefinition`, `TotalityFoodItem` across all of `src/main/java`: **zero matches for all three.** None of these classes exist yet.
- **`totality:food` in the Resource API is a display-only wrapper**, not a second scale: `FoodResourceAdapter` reads `getFoodData().getFoodLevel()` directly, hardcodes max = 20 (there's no vanilla `getMaxFoodLevel()`), and the HUD's apparent "0-100" presentation is a `ResourceDisplayConversion.HEALTH_FOOD` **×5 display multiplier**, not a stored second value. The mechanical range a player actually has is still vanilla's 0-20.
- **Food restoration is 100% vanilla** — every custom food item uses plain `Item.Properties().food(FoodProperties...)`; no Totality mixin or wiring intercepts eating.
- **No `TotalityFoodItem` class or equivalent exists.** Custom food items found (True Wheat, Salmon Roe, Rock Warbler Egg, Garlic, Mountain Flowers, etc.) are plain `Item` subclasses using vanilla `FoodProperties` — none carry Totality-specific nutrition metadata.
- **Gap list for a true 0-100 native Food authority** (factual, not prescriptive):
  - `NutritionProfile`/`FoodDefinition`/`TotalityFoodItem` — none exist.
  - `FoodResourceAdapter` (and the two regen mixins) would need to stop reading vanilla `FoodData` directly and read a new Totality-owned store instead.
  - The current ×5 display conversion would need to become a real stored value, not a presentation trick.
  - Food would need to move from `EXTERNAL_ADAPTER` authority to something like `GENERIC_COMPONENT` (the pattern Thirst/Sanity/Ki already use, but currently unbacked) if it's to be Totality-owned.
  - No custom food item currently carries anything beyond vanilla `FoodProperties` to migrate from.

---

## 9. Dormant Resource Audit

| ID | Completeness | Evidence |
|---|---|---|
| `totality:thirst` | **Placeholder shape only.** Has `min=0`/`max=100` metadata; no adapter, no grant provider, nothing ever instantiates state for it. | `ProductionResourceDefinitions.java:273-280` |
| `totality:sanity` | **Placeholder shape only.** Identical treatment to Thirst. | `ProductionResourceDefinitions.java:288-295` |
| `totality:ki` | **Least complete** — deliberately has **no maximum at all** (`OptionalLong.empty()`), documented in-code as "set by Monk level/features... no resolver framework exists yet." Would structurally fail `MAXIMUM_UNAVAILABLE` even if state existed. | `ProductionResourceDefinitions.java:312-318` |

- `PlayerResourceIds.java` comment explicitly states `totality:fatigue` and `totality:temperature` were considered and deliberately excluded from this registration pass — they're not even scaffolded.
- **Reiryoku**: zero matches anywhere in `src/main/java`.
- **Chakra**: exactly 2 matches, both incidental doc-comment examples in unrelated particle-animation classes (`VortexAnimation.java:23`, `TorusAnimation.java:36` — example use-case lists, not implementations). No Chakra resource, component, or ability exists.
- Given the two most recent commits on this branch (Sep 10) add Bleach-themed equipment (Asauchi, Shinigami Robe), **Reiryoku may become newly relevant soon** even though nothing exists for it today — worth flagging as a likely future ask, not a current gap to close.

---

## 10. Recommended Exact Resume Point

**There is nothing to "resume" in Phase 3C — it's done.** The actual resume point is: *decide what comes after Phase 3C*, and separately, *close one residual uncertainty before calling Resource API V1 fully closed*.

**Immediate (small, closes the loop on 3C):**
1. Do a short manual playtest specifically of Mana HUD, Rage secondary HUD, and Class-tab Rage — including a dimension change and a Barbarian level-up while Raging — to confirm the "should no longer be visible" conclusions in §6 hold in practice. The 3B-3 report's own manual validation only covered Stamina and Spell Slots as `EXACT_MATCH`; Mana/Rage were migrated *without* a stated fresh manual re-validation afterward.
2. Optionally verify `ClientChargesComponent.applySyncPacket` (or equivalent) correctly applies an *increased* max, closing the one open thread from §6's Rage-max discussion.

**These two are legitimate closure criteria for calling "Generic Player Resource API V1" done**, alongside what's already true: all production consumers migrated, sync/parity tooling built and functioning, structural validation on registration.

**Next phase (new scope, not a 3C continuation — recommend treating as its own phase, tentatively "Phase 4"):**
Pick one of three independent tracks; they don't need to land together:
- **Track A — Promote dormant resources to real.** Needs, in order: a `ResourceGrantProvider`/grant mechanism (currently doesn't exist — `instantiateScalar` is never called in production), wiring `PlayerResourceStateComponent.sync()` into the server tick (currently never called), and — only for Ki — a `ResourceMaximumResolver` framework tied to Monk level/features (which in turn depends on Monk class design existing). **Recommend Thirst/Sanity before Ki** — they have fixed maxima and no class-system dependency.
- **Track B — Food 0-100 native migration.** Larger and more foundational than Track A; touches vanilla-integration mixins directly. Should probably follow, not precede, a decision on whether Food becomes `GENERIC_COMPONENT`-authority (matching the Thirst/Sanity pattern) since that pattern will get its first real production exercise in Track A.
- **Track C — Reiryoku/Chakra.** Don't start yet — no scaffolding exists, and (per the recent Bleach-content commits) design intent may still be forming. Worth a short design conversation before any code, given Track A will establish the actual pattern for adding a new "class-gated resource with a level-dependent maximum" (Ki) that Reiryoku would likely reuse.

**Do not bundle these into one commit or phase** — they have different dependencies (Track A blocks nothing else; Track B is independent; Track C should wait on Track A's Ki work to validate the maximum-resolver pattern first).

---

## 11. Uncertainties

- **Rage client-side max-sync application** (`applySyncPacket` correctly reflecting an *increased* max) was not independently re-verified — only the server-side clamp/trigger path was confirmed. This is the one genuinely open thread from the original blocker list.
- No commit, test name, or comment was found that names the "Rage dimension-transfer bug" as a specifically observed and fixed issue — §6's conclusion is inferred from tracing current sync wiring, not from a rediscovered ticket. Treat the structural asymmetry as fact; treat "definitely fixed in practice" as a reasoned inference pending a manual dimension-change test.
- The ~10 unread files in the parity/diagnostic subsystem (`client/resource/parity/*`) were identified by name/import but not individually read in full — the overall shape (continuous shadow comparison, bounded logging) is well-evidenced from the files that were read, but exact per-file responsibilities there are not independently confirmed.
- `client/resource/TotalityClientResourceReaders.java` (the production wiring of native/generic readers into `ClientResourceService`) was located but not opened directly — its role is inferred from other files' references to it.
- Whether `PlayerResourceStateComponent.instantiateScalar`/`instantiatePartitioned` would work correctly if a future grant provider called them (structural readiness, not "does it currently happen") was not tested — only confirmed that nothing currently calls them.
- Test suites (40+ files mirroring the audited packages, reporting "N/N passing" in the various implementation reports) were not independently run or re-diffed in this audit — figures are taken from the reports as-written.
- `FormulaResolver.tryCast()` being dead code was confirmed via production-source grep only; `src/test` was not checked for a unit test that might exercise it in isolation (irrelevant to the "does this block real players" question this audit was actually answering, but worth noting for completeness).

---

*Report compiled from three parallel research passes (phase/history reconstruction, current architecture/inventory, consumer/blocker/food audit), each independently reading source and cross-checked against the others where their scopes overlapped. No disagreements were found between the three passes' findings.*
