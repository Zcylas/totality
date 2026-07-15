# TOTALITY_GENERIC_PLAYER_RESOURCE_API

**Status:** CANONICAL — implementation-ready architectural specification  
**Date:** 2026-07-13  
**Project:** Totality  
**Target:** Minecraft 26.1.2, Fabric, Mojang mappings, Java 25  
**Primary implementation package:** `zcylas.totality.api.rpg.resources`

---

## 0. DOCUMENT AUTHORITY AND SOURCE ORDER

This document is the canonical design for Totality's Generic Player Resource API.

When reconciling this document with older plans or existing code, use the following authority order:

1. This document.
2. `TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md`.
3. The latest dedicated documents for the owning system, especially:
   - `TOTALITY_REST_AND_FATIGUE.txt`
   - `TOTALITY_RPG_CLASS_SPELL_ARCHITECTURE.md`
   - `TOTALITY_FOOD_ECOSYSTEM_CURRENT(1).txt`
   - `TOTALITY_SHARED_CROSS_SYSTEM_FOUNDATIONS.txt`
   - `TOTALITY_DISEASE_SPELL_INTERVENTION_DESIGN.txt`
4. `TOTALITY_IMPLEMENTATION_AUDIT_REPORT.md` as the authoritative snapshot of the code audited on 2026-07-12, including its 2026-07-13 addendum.
5. Older session notes only as historical implementation context.

The implementation audit establishes that Totality already has several resource-like systems with incompatible shapes:

- `PlayerResourceComponent` and `PlayerResourceRecalculator` for current/max RPG resources.
- Dedicated Mana and Stamina managers and tick logic.
- `SpellSlotComponent`, storing slots separately by level.
- `PlayerChargesComponent`, already used successfully for Barbarian Rage charges.
- Dedicated resource-specific packets and client mirrors.
- Rest recovery through a priority-ordered `RestEventBus`.
- The shared Totality component framework for persistence, respawn copying, and synchronization.

The audit also establishes that:

- Warlock Pact Magic has no live player pool.
- Monk Ki has no backing resource.
- Stamina is not restored on Long Rest.
- Wizard Arcane Recovery is absent.
- Fatigue/Rest Need is intentionally unresolved and unimplemented.
- Standard spell-slot consumption is already correctly committed only after a spell actually succeeds.
- Barbarian Rage's existing Short Rest `+1` and Long Rest full recharge behavior works and must be preserved.
- The short-timescale Stamina-derived combat `ExhaustionManager` is not the future long-term Fatigue/Rest Need system.

This API must therefore unify shared infrastructure without flattening every resource into the same behavior.

---

## 1. PURPOSE

The Generic Player Resource API provides one shared framework for registering, storing, querying, modifying, persisting, synchronizing, and presenting player-owned resources.

It must support meaningfully different resource families, including:

- Mana.
- Stamina.
- Rage charges.
- Ki.
- Chakra.
- Solar Charge.
- Standard spell slots.
- Warlock Pact Magic slots.
- Thirst.
- Rest Need.
- Sanity.
- Future class, subclass, covenant, species, origin, lineage, equipment, survival, and quest resources.

The API exists to remove duplicated infrastructure. It does **not** make every resource behave alike.

### 1.1 Universal versus source-specific resources

Totality has two ownership categories.

#### Universal player resources

These belong to every ordinary player as part of baseline gameplay:

- HP/Health.
- Food/Hunger.
- Stamina.
- Mana.
- Thirst.
- Temperature.
- Rest Need/Fatigue, with its final public name and identifier decided by the dedicated API design.
- Sanity.

Mana is universal because it primarily supports Totality's future Ars Nouveau-like general magic system. It is not restricted to D&D classes or the RPG class framework.

All universal values are first-class registered resources for querying, presentation, conditions, events, and cross-API integration. They do not all use the same storage authority.

Implementation boundary:

- Stamina and Mana are globally granted and stored by the Generic Resource component during ordinary player initialization.
- Thirst, Rest Need/Fatigue, and Sanity are globally granted and may use Generic Resource state when their owning systems are implemented and enabled.
- HP is registered as a first-class externally backed resource, while Health/Combat remains authoritative for damage, healing, absorption, death, and attributes.
- Food is registered as a first-class externally backed resource, while Food/Hunger remains authoritative for food level, saturation, exhaustion, eating rules, and vanilla compatibility.
- Temperature is registered as a first-class externally backed resource, while Survival/Temperature remains authoritative for environmental sampling, body-temperature simulation, thresholds, and consequences.

#### Source-specific resources

These exist only when an authoritative class, species, lineage, discipline, transformation, quest, or other source grants them.

Examples:

- Rage for Barbarians.
- Ki for Monks.
- Solar Charge for Kryptonians.
- Chakra for players who gain access to Ninjutsu.
- Reiatsu for relevant Shinigami progression.
- Cursed Energy for relevant Jujutsu progression.
- Future class-, species-, lineage-, universe-, or discipline-specific pools.

A player may possess several source-specific resources simultaneously. A Kryptonian Barbarian may have Rage and Solar Charge; a Monk/Barbarian multiclass may have Ki and Rage.

### 1.2 Core design rule

The generic API owns:

- Resource identifiers and registration.
- Static definition lookup.
- Mutable player resource state.
- Generic value and maximum operations.
- Atomic validation and mutation.
- Clamping and optional overflow.
- Maximum modifiers.
- Persistence and migration.
- Death and respawn handling.
- Server-authoritative synchronization.
- Generic presentation metadata.
- Generic events and operation results.
- Integration contracts used by classes, species, abilities, Rest, Survival, equipment, effects, food, and other systems.

The owning gameplay system retains:

- Why the player has the resource.
- How its maximum is calculated.
- Which actions cost it.
- Whether an action is eligible.
- What restores or drains it.
- What depletion means.
- What thresholds do.
- Which rest activity qualifies.
- Which environment conditions apply.
- Which food or liquid is safe.
- Which spell slot tier may be used.
- Which class, species, or entitlement activates it.
- Any lore-specific or feature-specific rules.

### 1.3 One authority per axis

A consumer must not duplicate another system's truth.

Examples:

- Thirst stores hydration state, but Fluid Material and Safety determine whether water is contaminated, saline, purified, or otherwise safe.
- The Resource API stores Stamina, but Movement and Combat decide when sprinting, flight, blocking, bow use, or attacks should spend it.
- The Resource API stores Pact Magic slots, but `WarlockClass` decides the player's pact-slot tier and count.
- The Resource API stores Ki, but `MonkClass` and Rest decide whether a meditation qualifies for full recovery.
- The Resource API stores spell slots, but the D&D Spell API owns class access, known/prepared state, target checks, components, upcasting, and successful-cast commitment.
- The Resource API may store Rest Need, but Rest decides whether a Long Rest is restorative.
- The Resource API may expose Sanity state, but the future Dream/Sanity system owns its thresholds and consequences.

---

## 2. NON-GOALS

The Generic Player Resource API is not:

- A replacement for the Ability API.
- A replacement for the D&D Spell API.
- A replacement for Rest sessions or Rest eligibility.
- A replacement for the future Unlock/Access/Entitlement API.
- A universal Survival condition component.
- A generic status-effect system.
- A cooldown system.
- A wallet or economy transaction system.
- A replacement for the authoritative Health, Food/Hunger, Temperature, Air, Armor, or Experience implementations. Health, Food, and Temperature may be first-class resource views while their owning systems retain authority.
- A replacement for player XP/level, class levels, class-allocation points, skill XP/levels, mastery points/unlocks, attribute scores, unspent attribute points, Feats, Codex discovery, quest progress, or other permanent progression state.
- A universal numeric-value API merely because several systems display numbers.
- A replacement for ability cooldowns, concentration, single-feature once-per-rest usage flags, item-bound charges, phone battery state, relationship progression, or Economy balances.
- A formula language capable of expressing every class and species mechanic in JSON.
- A reason to convert every counter or boolean into a player resource.
- A requirement that every resource has a bar.
- A requirement that every resource regenerates.
- A requirement that every resource has a conventional current/max scalar.
- A reason to merge immediate Stamina, long-term Rest Need, derived Fatigue tiers, and possible Sleep Debt into one concept.

A value belongs in this API only when it represents a reusable, player-owned, bounded or structured runtime pool/state that multiple systems need to query or change through a consistent contract.

### 2.1 Qualification matrix

| Value/state | Canonical owner |
|---|---|
| Mana, Stamina, Thirst, Rest Need/Fatigue, Sanity, Rage, Ki, Chakra, Solar Charge, Reiatsu, Cursed Energy | First-class Generic Player Resources using Generic Resource state, with rules supplied by their owning systems |
| HP/Health | First-class Generic Player Resource view backed by Health/Combat authority |
| Food/Hunger | First-class Generic Player Resource view backed by Food/Hunger authority |
| Temperature | First-class Generic Player Resource view backed by Survival/Temperature authority |
| Standard spell slots, Pact Magic slots, Hit Dice | Generic Player Resource API using partitioned pools |
| Player XP and player level | Player progression/level component |
| Class levels and available class-allocation points | Class progression component |
| Skill XP and skill levels | Skills component |
| Mastery points and mastery unlocks | Masteries component |
| Attribute scores and unspent attribute points | Stats/Attributes component |
| Ability cooldown or one ability's private uses | Ability or class-feature state |
| Concentration | D&D Spell/Concentration component |
| Magic-item charges or Phone battery | Item/Phone stack state |
| Credits and dimensional currencies | Economy/Wallet/transaction APIs |
| Codex discovery and Knowledge/Unlock state | Codex and Unlock/Entitlement systems |

Spendability alone does not make something a Generic Player Resource. Attribute points, mastery points, class points, and currencies are spendable, but their spending permanently changes progression, ownership, or economic history and therefore remains in their dedicated transactional systems.

A single ability's `3 uses per Long Rest` also does not automatically become a player resource. It belongs in Ability/class-feature state unless the uses form a named shared player pool consumed by several features or systems.

---

## 3. HIGH-LEVEL ARCHITECTURE

The API has six layers:

1. **Definitions** — immutable registered resource metadata.
2. **State authority** — either Generic Resource component state or an authoritative external-system adapter.
3. **Resolvers and strategies** — code-owned behavior referenced by identifiers.
4. **Resource service** — the one public query and mutation façade.
5. **External change bridge** — converts owner-side Health, Food, Temperature, or future external changes into resource snapshots/events/synchronization.
6. **Presentation and synchronization** — derived snapshots sent to clients.

```text
PlayerResourceRegistry
    └── PlayerResourceDefinition (immutable)

PlayerResourceStateComponent
    └── Map<Identifier, ResourceState> (internally backed mutable player state)

ExternalPlayerResourceAdapterRegistry
    ├── totality:health -> HealthResourceAdapter
    ├── totality:food -> FoodResourceAdapter
    └── totality:temperature -> TemperatureResourceAdapter

ResourceStrategyRegistry
    ├── MaximumResolver
    ├── AvailabilityResolver
    ├── RegenerationStrategy
    ├── DeathHandler
    └── CustomStateCodec, only where required

PlayerResourceService
    ├── route query to internal state or external adapter
    ├── validate
    ├── prepare transaction
    ├── commit mutation
    ├── publish events
    └── mark dirty for batched sync

ExternalResourceChangeBridge
    └── owner-side changes -> resource snapshot/event/sync invalidation

ResourceSyncManager
    ├── full snapshots
    ├── revisioned deltas
    └── optional prediction reconciliation
```

### 3.1 Preserve the existing package

The current code already uses `api/rpg/resources/`. Do not create a competing singular `resource` package and do not move unrelated code merely for naming aesthetics.

Recommended structure:

```text
api/rpg/resources/
    PlayerResourceRegistry.java
    PlayerResourceDefinition.java
    PlayerResourceService.java
    PlayerResourceStateComponent.java
    ResourceComponents.java
    ResourceModel.java
    ResourceCapability.java
    ResourcePolarity.java
    ResourceLifecyclePolicy.java
    ResourceSnapshot.java
    ResourceOperation.java
    ResourceOperationResult.java
    ResourceFailure.java
    ResourceCause.java
    ResourceContext.java
    ResourceTransaction.java
    ResourceTransactionResult.java

api/rpg/resources/state/
    ResourceState.java
    ScalarResourceState.java
    PartitionedResourceState.java
    OrphanedResourceState.java

api/rpg/resources/external/
    ResourceStateAuthority.java
    ExternalPlayerResourceAdapter.java
    TransactionalExternalResourceAdapter.java
    ExternalPlayerResourceAdapterRegistry.java
    ExternalResourceChangeBridge.java
    ExternalResourceOperationSupport.java
    ExternalResourceClientMirrorMode.java

api/rpg/resources/modifier/
    ResourceMaximumModifier.java
    ResourceMaximumModifierOperation.java
    ResourceCostModifier.java
    ResourceCostModifierOperation.java
    ResourceRateModifier.java
    ResourceRateModifierOperation.java
    ResourceModifierSource.java
    MaximumChangePolicy.java
    ResourceModifierRegistry.java

api/rpg/resources/dependency/
    ResourceDependency.java
    ResourceDependencySet.java
    ResourceInvalidationBus.java
    ResourceProgressionView.java

api/rpg/resources/strategy/
    ResourceStrategyRegistry.java
    ResourceMaximumResolver.java
    ResourceAvailabilityResolver.java
    ResourceRegenerationStrategy.java
    ResourceDeathHandler.java

api/rpg/resources/event/
    ResourceEvents.java
    ResourceChangeAttempt.java
    ResourceChanged.java
    ResourceMaximumChanged.java
    ResourceThresholdCrossed.java
    ResourceActivationChanged.java

api/rpg/resources/client/
    ClientResourceRegistry.java
    ClientResourceState.java
    ClientResourceManager.java
    ResourceDisplayConversion.java
    ResourceValueFormatter.java
    ResourceValueFormatterRegistry.java
    ResourceDisplayContext.java

api/rpg/resources/integration/
    ResourceGrantProvider.java
    ResourceGrant.java
    ResourceGrantSourceType.java
    ResourceGrantAggregationPolicy.java
    ResourceRemovalPolicy.java
    ResourceVisibilityPolicy.java
    ResourceModifierProvider.java
    ResourceCostProvider.java
    ResourceRestIntegration.java
```

Exact class decomposition may be adjusted during implementation, but the ownership and behavior described in this document must remain intact.

---

## 4. STATIC DEFINITIONS VERSUS MUTABLE PLAYER STATE

### 4.1 Static definition

A resource definition describes what a resource **is**, not what one player currently has.

Recommended canonical shape:

```java
public record PlayerResourceDefinition(
        Identifier id,
        ResourceModel model,
        ResourcePolarity polarity,
        Optional<ResourceTargetRange> targetRange,
        ResourceStateAuthority stateAuthority,
        Optional<Identifier> externalAdapterId,
        long unitScale,
        long absoluteMinimum,
        OptionalLong authoredBaseMaximum,
        Set<ResourceCapability> capabilities,
        Set<ResourceDependency> dependencies,
        Identifier maximumResolverId,
        Optional<Identifier> availabilityResolverId,
        Optional<Identifier> regenerationStrategyId,
        ResourceLifecyclePolicy lifecycle,
        ResourcePresentationDefinition presentation,
        int definitionVersion
) {}
```

This shape is conceptual. Java implementation may use a builder to avoid unwieldy constructors.

Definitions are:

- Immutable after registration.
- Namespaced.
- Available on both logical sides where presentation requires them.
- Not stored in player NBT.
- Not copied per player.
- Not allowed to contain player references.
- Not allowed to contain screen instances, lambdas, or arbitrary unserializable runtime objects.

### 4.2 State authority

Every registered resource declares one authority:

```java
public enum ResourceStateAuthority {
    GENERIC_COMPONENT,
    EXTERNAL_ADAPTER
}
```

#### `GENERIC_COMPONENT`

The Generic Player Resource component stores current state.

Examples include Mana, Stamina, Thirst, Rest Need/Fatigue, Sanity, Rage, Ki, Chakra, Solar Charge, spell slots, Pact Magic slots, and Hit Dice.

#### `EXTERNAL_ADAPTER`

Another system owns the state, but the resource is still registered and exposed through `PlayerResourceService`.

Canonical V1 examples:

- Health: Health/Combat authority.
- Food: Food/Hunger authority.
- Temperature: Survival/Temperature authority.

Externally backed does **not** mean presentation-only or second-class. These resources may be queried through the same snapshot API, used in conditions and HUD/menu presentation, observed through generic events, and targeted by integrations when their adapters support the requested operation.

It does mean:

- Their values are not persisted inside `PlayerResourceStateComponent`.
- Generic death/copy/migration rules do not overwrite owner authority.
- Unsupported generic operations fail rather than bypass owner mechanics.
- The owner must notify `ExternalResourceChangeBridge` when state changes outside `PlayerResourceService`.

### 4.3 Mutable internally backed state

One player component stores all internally backed resource instances.

```java
public final class PlayerResourceStateComponent
        implements SyncedComponent, CopyableComponent<PlayerResourceStateComponent> {

    private int schemaVersion;
    private long revision;
    private final Map<Identifier, ResourceState> states;
    private final Map<ResourceModifierKey, ResourceModifier> persistentModifiers;
    private final Map<Identifier, OrphanedResourceState> orphanedStates;
}
```

Player state stores only authoritative mutable information, such as:

- Current value.
- Current values per partition.
- Persistent overflow, when explicitly supported.
- Regeneration remainder needed to avoid fractional loss.
- Last authoritative spend/update time only when needed for exploit-safe behavior.
- Persisted temporary modifiers whose source requires them to survive logout.
- Resource-specific durable state approved by the resource model.
- Migration/schema version.
- Revision.

Player state must not persist:

- Display names.
- Icons.
- Colors.
- Calculated maximums.
- Derived availability.
- Derived visibility.
- Current class/species formulas.
- Equipment-derived modifiers that can be recalculated.
- Effect-derived modifiers that already live in the effect system.
- Duplicated unlock/entitlement truth.
- A cached result merely because it is convenient.

### 4.4 Lazy instantiation

A registered resource definition represents a resource type understood by the game. It does **not** mean every player owns that resource.

Player state is normally created only when an authoritative owning system supplies a valid `ResourceGrant`.

State is created when at least one of these becomes true:

- A class, subclass, species, origin, lineage, discipline, covenant, transformation, quest, equipment source, status effect, or global system grants the resource.
- A migration imports legitimate legacy state.
- An authorized administrative or migration path explicitly requests creation.
- A persisted independent grant or upgrade requires the state to exist.
- The definition is explicitly granted globally by an enabled system such as Survival.

A read-only query must not silently grant or instantiate a resource. Opening a HUD, menu, tooltip, debug screen, or generic resource list must not create NBT entries.

A modifier targeting a resource does not automatically grant it unless that source explicitly also supplies a grant. For example, a Chakra-increasing lineage may modify Chakra only when another source has granted the Chakra pool.

### 4.5 Canonical grant rule

> A registered resource definition represents a resource type available to the game, not a resource owned by every player. A player receives a resource only through an active `ResourceGrant` supplied by an authoritative owning system. Ungranted resources are not instantiated, regenerated, synchronized, displayed, or processed for that player.

Examples:

- `BarbarianClass` grants Rage charges.
- `MonkClass` grants Ki.
- `KryptonianSpecies` grants Solar Charge.
- A future Ninjutsu discipline may grant Chakra.
- A future Shinigami lineage or discipline may grant Reiatsu.
- A future Jujutsu discipline may grant Cursed Energy.
- Survival may globally grant Thirst while Survival is enabled.

---

## 5. IDENTIFIERS AND REGISTRATION

### 5.1 Resource identifiers

All resources use `Identifier`.

Canonical initial identifiers:

```text
totality:health
totality:food
totality:temperature
totality:mana
totality:stamina
totality:rage
totality:ki
totality:chakra
totality:solar_charge
totality:spell_slots
totality:pact_magic_slots
totality:hit_dice
totality:thirst
totality:rest_need  # provisional placeholder; final name/ID decided by dedicated Rest Need/Fatigue design
totality:sanity
```

The exact public ID for an existing Rage pool should preserve its current identifier where possible. Do not rename a persisted identifier merely to make this list visually uniform. If the existing Rage `CHARGE_ID` differs, either:

- Keep that ID as canonical; or
- Register an explicit one-time alias migration.

### 5.2 Registry behavior

`PlayerResourceRegistry.register(definition)` must:

- Reject duplicate IDs.
- Reject invalid unit scales.
- Require effective maximum to be greater than absolute minimum.
- Allow signed lower bounds for resources such as Temperature.
- Require a valid target range for `TARGET_RANGE` polarity.
- Reject unsupported capability/model combinations.
- Require a registered external adapter when `stateAuthority == EXTERNAL_ADAPTER`.
- Reject an external adapter ID on `GENERIC_COMPONENT` resources.
- Verify referenced strategy IDs during registry freeze.
- Verify client-required presentation data.
- Freeze structural fields before players are loaded.

`PlayerResourceRegistry.get(id)` returns an optional definition. Normal game code must not assume every arbitrary ID exists.

### 5.3 Structural fields may not hot-swap

A datapack reload may update safe presentation and tuning fields, but it must not silently change:

- `ResourceModel`.
- `ResourceStateAuthority`.
- External adapter identity.
- `unitScale`.
- State codec.
- Tier key semantics.
- Persistence layout.
- Identifier.
- The meaning of current value.

Changing any structural field requires a definition version increase and an explicit migration.

### 5.4 Orphaned resources

If player NBT contains a resource whose definition is absent:

- Preserve its raw state as `OrphanedResourceState`.
- Do not expose it to ordinary gameplay.
- Do not delete it on save.
- Log a rate-limited warning with the player and resource ID.
- Restore it if the definition returns and its version is compatible.
- Require an explicit migration or admin cleanup when incompatible.

This prevents data loss when an optional module, datapack, class pack, or future addon is temporarily removed.

---

## 6. RESOURCE MODELS

The API supports two authoritative storage models in V1 and one presentation-only segmentation concept.

### 6.1 `SCALAR`

A bounded numeric value.

Examples:

- Health.
- Food.
- Temperature.
- Mana.
- Stamina.
- Rage.
- Ki.
- Chakra.
- Solar Charge.
- Thirst.
- Rest Need.
- Sanity.

```java
public final class ScalarResourceState implements ResourceState {
    long currentUnits;
    long overflowUnits;
    long regenerationRemainder;
}
```

The same model can represent:

- Discrete integers, using `unitScale = 1`.
- Fractional continuous values, using a fixed-point scale such as `1000`.
- Pip resources, where the value is integral and presentation uses pips.
- Accumulating negative-condition meters, using polarity metadata.

### 6.2 `PARTITIONED_POOL`

A resource containing independent current/max counts by an integer partition key.

Examples:

- Standard spell slots, partitioned by spell level `1..10`.
- Pact Magic slots, where the active partition normally equals the current pact-slot level.
- Hit Dice, partitioned by die size such as `6`, `8`, `10`, or `12`.
- A future resource with independently consumable numeric bands.

```java
public final class PartitionedResourceState implements ResourceState {
    Int2LongMap currentByPartition;
    Int2LongMap overflowByPartition;
}
```

The term **partition** is intentionally neutral:

- The D&D Spell API interprets a partition as a spell-slot tier.
- The Rest/Health system interprets a partition as a Hit Die size.
- Another owner may interpret it as a band, rank, or numeric category.

V1 uses integer partition keys because all known requirements are naturally numeric and compact. Do not invent identifier-keyed partitions until real content requires them.

Every definition using this model must provide an owner-specific partition descriptor so UI, commands, failures, and logs display `Level 3 slot` or `d10 Hit Die` rather than exposing a meaningless raw integer.

### 6.3 Segmented presentation is not a third storage model

Equal pips do not require a special state class.

Rage `3/5` and Ki `7/10` are scalar integer resources presented as pips.

A separate segmented state is justified only if segments have distinct identities or partial-fill mechanics. No current Totality resource requires that complexity.

### 6.4 Slot-like resources must not be flattened

Standard spell slots must remain separately tracked per level. They must not become:

- One pooled "spell energy" number.
- One generic charge count.
- Mana with differently priced spells.
- Cooldowns.
- A scalar with inferred slot levels.

The existing successful-cast consumption semantics must survive migration: a failed or no-effect spell does not consume a slot.

### 6.5 Externally backed first-class resources

Health, Food, and Temperature are first-class registered resources, but their owning systems remain authoritative.

This avoids both bad extremes:

- They are not omitted from the shared API merely because Minecraft already has state for them.
- Their state is not duplicated inside `PlayerResourceStateComponent`.

```java
public interface ExternalPlayerResourceAdapter {
    ResourceSnapshot snapshot(
            ServerPlayer player,
            PlayerResourceDefinition definition,
            ResourceContext context
    );

    Set<ExternalResourceOperationSupport> supportedOperations();

    ResourceOperationResult apply(
            ServerPlayer player,
            ResourceOperation operation,
            ResourceContext context
    );

    ExternalResourceClientMirrorMode clientMirrorMode();
}
```

Optional atomic support:

```java
public interface TransactionalExternalResourceAdapter
        extends ExternalPlayerResourceAdapter {

    PreparedExternalResourceOperation prepare(...);
    CommitResult commit(PreparedExternalResourceOperation prepared);
    void rollback(PreparedExternalResourceOperation prepared);
}
```

Rules:

- All operations delegate to the authoritative owner.
- An adapter may support query while rejecting spend, drain, restore, or direct set.
- A mixed atomic transaction may include an external resource only when its adapter provides required prepare/commit guarantees.
- Otherwise the service returns `ATOMIC_OPERATION_UNSUPPORTED` before any mutation.
- External changes performed directly by the owner must call `ExternalResourceChangeBridge`.
- Native client synchronization may be reused where reliable, or the adapter may request generic deltas. Do not double-apply both.

#### Health

`totality:health` exposes current and maximum HP.

Health/Combat still owns damage sources, mitigation, healing rules, invulnerability, absorption, death, attribute-driven maximum health, combat events, persistence, and entity synchronization.

Generic behavior:

- Query returns native authoritative Health values plus the registered ×5 display conversion.
- Restore delegates to canonical Health healing in native mechanical units.
- Generic direct drain is not equivalent to damage and is rejected unless an authorized Health integration supplies a valid damage/cost operation.
- Direct set is restricted to admin, migration, respawn, or Health-owned contexts.
- Absorption is not silently added to current HP; it is optional Health presentation metadata/overlay and uses the Health formatter when shown numerically.
- Mob health bars and floating damage/healing use the same Health formatter.

#### Food

`totality:food` exposes primary Food/Hunger level and maximum.

Food/Hunger remains authoritative for food level, saturation, exhaustion, eating eligibility, natural regeneration/starvation interaction, persistence, and vanilla compatibility.

Generic behavior:

- Query returns native authoritative Food values plus the registered ×5 display conversion.
- Restore and drain delegate to Food/Hunger in native mechanical units.
- Cooking, Diet, effects, diseases, Rest, and Survival may use the shared façade for primary Food changes.
- Saturation and exhaustion remain owner-specific state/metadata.
- Diet quality, meal history, nutrients, variety, and long-term consequences remain in Diet/Food ecosystem APIs.
- Food item and Cooking tooltips format mechanical restoration through the Food formatter, so `6` displays as `30`.

#### Temperature

`totality:temperature` exposes body-temperature state through a scalar target-range resource.

Survival/Temperature owns environmental sampling, simulation, comfort/safe/danger thresholds, consequences, persistence, and death behavior.

Generic behavior:

- Query, presentation, conditions, and events are supported.
- Ordinary consumers do not spend or restore Temperature.
- Owner-authorized effects may warm/cool through the adapter.
- Temperature may use signed values.
- Presentation uses `TARGET_RANGE`: the comfort band is desirable, not the maximum.

Air, Armor durability, Experience, and other vanilla values remain outside this first-class set unless later adopted.

---

## 7. NUMERIC REPRESENTATION

### 7.1 Fixed-point authoritative values

Do not use floating-point values as persisted or network-authoritative resource state.

Use signed `long` units.

```text
mechanical value = stored units / definition.unitScale
display value    = presentation.displayConversion(mechanical value)
```

`unitScale` controls authoritative fixed-point precision. `displayConversion` is presentation-only and must never alter damage, healing, Food restoration, affordability, clamping, persistence, or other mechanics.

Examples:

- Rage: scale `1`, current `3`.
- Ki: scale `1`, current `8`.
- Stamina: scale `1` initially if all existing costs are integral; display conversion `1:1`.
- Health: native mechanical Health units; display conversion `5:1`.
- Food: native mechanical Food units; display conversion `5:1`.
- Solar Charge: scale `1000` if sunlight charging needs fractional rates.
- Rest Need: scale selected by its future owning design.
- Sanity: scale selected by its future owning design.

Reasons:

- No cumulative floating-point drift.
- Deterministic server/client reconciliation.
- Exact persistence.
- Safe fractional regeneration through remainder accumulation.
- Easier transaction validation.

### 7.2 Bounds

Every scalar resource has:

- `absoluteMinimum`, normally `0`.
- A derived effective maximum.
- Optional overflow limits.

The service must reject any operation that would overflow `long`.

Use checked arithmetic (`Math.addExact`, `Math.multiplyExact`) or equivalent guarded helpers.

### 7.3 Invalid numbers

Public APIs accepting display-space decimals must normalize to units at the boundary and reject:

- NaN.
- Infinity.
- Negative costs where a cost must be positive.
- Values not exactly representable under the resource scale, unless explicit rounding is requested.
- Values exceeding configured hard safety limits.

Core internal operations accept authoritative unit-space `long` values to avoid repeated conversion.

### 7.4 Mechanical values versus display values

Gameplay always reads and mutates mechanical values. Presentation converts them for the player.

```text
Health:
  mechanical maximum 20
  display conversion ×5
  displayed maximum 100

Health damage:
  mechanical damage 4
  floating displayed damage 20

Food:
  mechanical maximum 20
  display conversion ×5
  displayed maximum 100

Bread:
  mechanical Food restoration 6
  displayed tooltip restoration 30

Mana:
  mechanical/displayed maximum 100

Stamina:
  mechanical/displayed maximum 100
```

Rules:

- Snapshots, operations, costs, conditions, events, modifiers, healing, damage, and Food restoration use mechanical units.
- Display conversion applies only when values or deltas are shown to players.
- Percentages use mechanical current/maximum; a linear visual multiplier does not change the percentage.
- Conversion uses deterministic rational/fixed-point arithmetic.
- One registered formatter owns rounding, decimals, signs, magnitude formatting, and localization.
- HUD, menus, mob health bars, floating damage/healing, item tooltips, Cooking screens, and Diet previews must share that formatter.
- No consumer hardcodes `×5`.
- The existing Health display class must become, wrap, or delegate to the registered Health formatter so scaling is applied exactly once.
- Gameplay JSON uses mechanical values unless a field explicitly declares display-space authoring and converts at load time.
- Clients never submit display-space amounts as authoritative mutations.

---

## 8. POLARITY AND VALUE MEANING

Not every resource has "full is good."

```java
public enum ResourcePolarity {
    HIGH_IS_GOOD,
    HIGH_IS_BAD,
    TARGET_RANGE,
    NEUTRAL
}
```

Examples:

- Mana: `HIGH_IS_GOOD`.
- Stamina: `HIGH_IS_GOOD`.
- Rage charges: `HIGH_IS_GOOD`.
- Solar Charge: `HIGH_IS_GOOD`.
- Food: `HIGH_IS_GOOD`.
- Health: `HIGH_IS_GOOD`.
- Temperature: `TARGET_RANGE`.
- Rest Need: probably `HIGH_IS_BAD`.
- Sanity: likely `HIGH_IS_GOOD`, but the future Sanity design owns its exact thresholds.

### 8.1 Canonical Thirst direction

Existing adopted language says "Thirst drains" and drinks restore it. Therefore V1 uses:

```text
ID: totality:thirst
Meaning of current value: available hydration
Polarity: HIGH_IS_GOOD
Display label: Thirst
```

This preserves the established behavior even though "Hydration" would be a more literal internal name.

All code and tooltips must avoid ambiguous phrases such as "add thirst" when they mean dehydration. Preferred operation wording:

- `restore(totality:thirst, amount)` for drinking.
- `drain(totality:thirst, amount)` for heat, verbal casting, and rest.

A future rename to `totality:hydration` would require migration and is not necessary.

### 8.2 Target-range resources

Some constant state meters are healthiest near a target band rather than at minimum or maximum.

```java
public record ResourceTargetRange(
        long preferredMinimumUnits,
        long preferredMaximumUnits,
        OptionalLong warningMinimumUnits,
        OptionalLong warningMaximumUnits
) {}
```

Temperature is the canonical example.

Rules:

- The preferred range lies inside the absolute bounds.
- A target-range resource may use signed values.
- Generic percentages must not imply that maximum Temperature is desirable.
- HUD emphasis is derived from distance to preferred/warning ranges.
- The owning system defines thresholds and consequences.
- Use owner verbs such as `warm`, `cool`, or `adjust` where restore/drain wording is ambiguous.

---

## 9. CAPABILITIES AND OPTIONAL EXTENSIONS

A resource definition declares only capabilities it actually supports.

```java
public enum ResourceCapability {
    SPENDABLE,
    RESTORABLE,
    DIRECT_DRAIN,
    PASSIVE_REGENERATION,
    PASSIVE_DECAY,
    MAXIMUM_MODIFIERS,
    TEMPORARY_MAXIMUM_MODIFIERS,
    OVERFLOW,
    PARTITIONED_SPENDING,
    COST_MODIFIERS,
    RATE_MODIFIERS,
    CLIENT_PREDICTION,
    OFFLINE_PROGRESSION,
    THRESHOLD_EVENTS,
    HUD_VISIBLE,
    MENU_VISIBLE
}
```

Capabilities are feature declarations, not complete behavior.

Complex behavior is supplied through small optional strategy interfaces.

### 9.1 Maximum resolver

```java
@FunctionalInterface
public interface ResourceMaximumResolver {
    ResourceMaximum resolve(ServerPlayer player,
                            PlayerResourceDefinition definition,
                            ResourceResolutionContext context);
}
```

The resolver returns base/effective structural maximum data before generic modifiers.

The owning system registers the resolver.

Examples:

- Mana resolver reads relevant stats/class features.
- Stamina resolver reads END/STR and existing progression.
- Ki resolver reads Monk level.
- Standard spell-slot resolver reads multiclass spellcasting progression.
- Pact Magic resolver reads Warlock level.
- Solar Charge resolver may use species progression.
- Rage resolver reads Barbarian level and features.

### 9.2 Availability resolver

```java
@FunctionalInterface
public interface ResourceAvailabilityResolver {
    ResourceAvailability resolve(ServerPlayer player,
                                 PlayerResourceDefinition definition,
                                 ResourceContext context);
}
```

This answers whether the resource can currently be used, not whether it exists.

Examples:

- A species power resource disabled by Kryptonite.
- Ki unavailable before Monk grants it.
- Solar Charge locked during a suppressing effect.
- Spell slots available but a specific spell still fails its own casting validation.
- Thirst globally disabled by server difficulty settings.

### 9.3 Regeneration strategy

```java
public interface ResourceRegenerationStrategy {
    ResourceTickDecision tick(ServerPlayer player,
                              ResourceSnapshot snapshot,
                              ResourceTickContext context);
}
```

The strategy proposes a generic operation. It must not mutate state directly.

Simple strategies may be data-configured by rate/delay/condition IDs. Complex strategies remain code-owned.

### 9.4 Death handler

Most resources use standard lifecycle policy. A custom handler exists only for lore-specific cases that cannot be expressed safely by the standard policy.

### 9.5 Partition descriptor

Partitioned pools register an owner-specific descriptor:

```java
public interface ResourcePartitionDescriptor {
    boolean isValidPartition(int partition);
    Component displayName(int partition);
    String debugName(int partition);
    Comparator<Integer> displayOrder();
}
```

Examples:

- Spell slots display partition `3` as `Level 3`.
- Hit Dice display partition `10` as `d10`.

The descriptor labels and validates partitions. It does not calculate current/max values or decide spending rules.

---

## 10. CURRENT VALUE AND MAXIMUM VALUE

### 10.1 Current is stored; maximum is derived

For ordinary resources:

- Current value is persistent mutable state.
- Maximum is calculated from the current player build and active modifiers.
- Maximum is included in snapshots and sync.
- Maximum is not normally persisted.

For partitioned resources:

- Current counts are stored per partition.
- Maximum counts are resolved per partition.
- Partitions with zero maximum may remain absent from compact state.

### 10.2 Base maximum calculation

Maximum resolution order:

1. Definition-authored base, if any.
2. Owning strategy's base maximum.
3. Permanent derived modifiers.
4. Temporary maximum modifiers.
5. Hard clamps and safety bounds.
6. Current-value reconciliation according to the operation's maximum-change policy.

There must be one central resolution path. Managers must not recalculate maximum independently and then overwrite each other.

### 10.3 Maximum snapshots

```java
public record ScalarMaximum(
        long baseUnits,
        long effectiveUnits,
        List<ResourceMaximumModifierSummary> appliedModifiers
) implements ResourceMaximum {}

public record PartitionedMaximum(
        Int2LongMap baseByPartition,
        Int2LongMap effectiveByPartition
) implements ResourceMaximum {}
```

Full modifier detail is server-side by default. Clients receive only what menus/tooltips require.

### 10.4 Recalculation triggers

Recalculate affected resources when relevant sources change:

- Attribute score or modifier changes.
- Character/class level changes.
- Class, subclass, covenant, species, origin, or lineage changes.
- Equipment changes.
- Status effect changes.
- Mastery/feature grants.
- Server configuration reload.
- Definition reload.
- Resource maximum modifier added, removed, or expired.

Avoid recalculating every resource every tick.

Use declared dependencies and explicit invalidation:

```java
public sealed interface ResourceDependency {
    record PlayerLevel() implements ResourceDependency {}
    record Attribute(Identifier attributeId) implements ResourceDependency {}
    record ClassLevel(Identifier classId) implements ResourceDependency {}
    record TotalClassLevel() implements ResourceDependency {}
    record SkillLevel(Identifier skillId) implements ResourceDependency {}
    record Mastery(Identifier masteryId) implements ResourceDependency {}
    record Ancestry() implements ResourceDependency {}
    record Equipment() implements ResourceDependency {}
    record StatusEffects() implements ResourceDependency {}
    record ServerRule(Identifier ruleId) implements ResourceDependency {}
    record Custom(Identifier dependencyId) implements ResourceDependency {}
}
```

```java
ResourceInvalidationBus.invalidate(
        player,
        new ResourceDependency.Attribute(TotalityAttributes.ENDURANCE)
);

ResourceInvalidationBus.invalidate(
        player,
        new ResourceDependency.ClassLevel(TotalityClasses.MONK)
);
```

### 10.5 Progression dependency contract

Attributes, Skills, masteries, player level, class levels, and their point pools remain authoritative in their existing progression components. The Resource API reads them through an immutable `ResourceProgressionView`; it never stores copies.

```java
public interface ResourceProgressionView {
    int playerLevel();
    int effectiveAttributeScore(Identifier attributeId);
    int effectiveAttributeModifier(Identifier attributeId);
    int classLevel(Identifier classId);
    int totalAllocatedClassLevels();
    int skillLevel(Identifier skillId);
    boolean hasMastery(Identifier masteryId);
}
```

Rules:

- Read the final effective attribute score/modifier from the Stats system. Do not reconstruct `base + spent + origin + class + item` independently.
- Read the specific class level needed by a resource. Rage uses Barbarian level; Ki uses Monk level; Pact Magic uses Warlock level.
- The current Totality class progression has a shared class-level allocation pool, not an independent 30-level cap for every class. Resource formulas must not assume `30 per class`.
- Skills and masteries may modify maximum, cost, regeneration/decay rate, grant state, or availability through explicit providers.
- Player level may influence universal-resource formulas or unlock grant sources, but the Resource API does not award, store, or cap player XP.
- Skill XP must be awarded by the action/Skill system after a successful qualifying action, not merely because a resource was spent. Otherwise players could repeatedly burn/refill a pool to farm Skill or player XP.
- A maximum increase caused by level-up does not automatically refill the newly added capacity. The owning progression/class system must explicitly choose whether to preserve current, add the gained difference, or fill.
- The Resource API must remain agnostic to the unresolved level-up attribute-point cadence. Changing that cadence or a level cap must not require a resource-state migration.
- Progression resolver queries are read-only and must not spend points, award XP, unlock masteries, grant class levels, or recursively mutate resources.

Dependency invalidation is selective. An Alchemy skill increase should not recalculate Rage unless Rage explicitly declares that dependency.

---

## 11. RESOURCE MODIFIERS

### 11.1 Modifier shape

```java
public record ResourceMaximumModifier(
        Identifier resourceId,
        Identifier modifierId,
        Identifier sourceId,
        ResourceMaximumModifierOperation operation,
        long amount,
        int priority,
        ModifierPersistence persistence,
        OptionalLong expiresAtGameTime
) {}
```

The full unique key is at least `(resourceId, modifierId, sourceId)` so independent sources do not overwrite each other accidentally.

### 11.2 Supported operations

```java
public enum ResourceMaximumModifierOperation {
    ADD_FLAT,
    ADD_MULTIPLIED_BASE,
    MULTIPLY_TOTAL,
    SET_MINIMUM,
    SET_MAXIMUM
}
```

Resolution order:

1. Base.
2. `ADD_FLAT`.
3. `ADD_MULTIPLIED_BASE`.
4. `MULTIPLY_TOTAL`.
5. `SET_MINIMUM`.
6. `SET_MAXIMUM`.

Within a stage, sort deterministically by:

1. Priority.
2. Source ID.
3. Modifier ID.

`SET_MAXIMUM` is restricted to high-priority system rules and should not be common equipment data.

### 11.3 Permanent versus temporary

"Permanent" does not automatically mean "persist this modifier."

Prefer derived modifiers:

- Class level bonus: derive from `PlayerClassComponent`.
- Species bonus: derive from `PlayerAncestryComponent`.
- Equipment bonus: derive from equipped items.
- Status effect bonus: derive from active effects.

Persist a modifier only when its source is itself an independently persisted grant with no stronger owner, for example:

- A permanent quest blessing.
- A consumed upgrade item.
- A one-time trained capacity increase.

Temporary modifiers may persist through logout only when their source duration also persists. Never let logout remove a penalty or extend a buff accidentally.

### 11.4 Maximum change reconciliation

Every maximum-changing operation declares a policy:

```java
public enum MaximumChangePolicy {
    CLAMP_CURRENT,
    PRESERVE_RATIO,
    PRESERVE_DEFICIT,
    ALLOW_OVERFLOW
}
```

Definitions may choose a default, but callers may use a stricter policy where authorized.

- `CLAMP_CURRENT`: current becomes `min(current, newMax)`. Default and safest.
- `PRESERVE_RATIO`: maintains percentage, with deterministic rounding.
- `PRESERVE_DEFICIT`: maintains missing amount.
- `ALLOW_OVERFLOW`: current above maximum moves to explicit overflow if supported.

Examples:

- Removing a Stamina-max item: `CLAMP_CURRENT`.
- A transformation that doubles both current and max: `PRESERVE_RATIO`.
- A temporary max-HP-style buff concept: often `PRESERVE_DEFICIT`, but Health is outside V1.
- Solar overcharge: explicit `ALLOW_OVERFLOW`.

No resource may remain invisibly above maximum without the `OVERFLOW` capability.

### 11.5 Cost modifiers

Costs may depend on attributes, levels, skills, masteries, equipment, effects, class features, species traits, and temporary conditions.

Use a separate cost-modifier pipeline rather than overloading maximum modifiers:

```java
public record ResourceCostModifier(
        Identifier resourceId,
        Identifier modifierId,
        Identifier sourceId,
        Optional<Identifier> actionTag,
        ResourceCostModifierOperation operation,
        long amount,
        int priority
) {}
```

```java
public enum ResourceCostModifierOperation {
    ADD_FLAT,
    ADD_MULTIPLIED_BASE,
    MULTIPLY_TOTAL,
    SET_MINIMUM,
    SET_MAXIMUM
}
```

Cost resolution order:

1. The owning action/spell/ability supplies its authored base cost.
2. The owning system applies action-specific scaling such as upcasting or charged use.
3. Generic cost modifiers are collected from progression, equipment, effects, species, class features, and other registered providers.
4. Deterministic operations are applied.
5. The result is clamped to safe bounds.
6. The exact resolved cost is validated and committed through the transaction service.

Rules:

- Preview and commit must use the same resolver and source set.
- The server recomputes cost at commit; the client never submits the authoritative amount.
- A modifier may reduce a cost to zero only when its definition explicitly permits zero.
- A cost cannot become negative or turn into restoration.
- Cost modifiers do not grant a resource.
- A Skill/mastery may reduce Stamina or Mana costs without becoming resource state itself.
- Changing a progression/equipment/effect dependency invalidates cached previews.

### 11.6 Regeneration and decay rate modifiers

Regeneration/decay strategies produce a base rate. A separate rate-modifier pipeline adjusts that rate:

```java
public record ResourceRateModifier(
        Identifier resourceId,
        Identifier modifierId,
        Identifier sourceId,
        ResourceRateModifierOperation operation,
        long amount,
        int priority
) {}
```

```java
public enum ResourceRateModifierOperation {
    ADD_FLAT,
    ADD_MULTIPLIED_BASE,
    MULTIPLY_TOTAL,
    SET_MINIMUM,
    SET_MAXIMUM
}
```

Rate modifiers may come from:

- Attributes.
- Skill levels or masteries.
- Equipment.
- Status effects.
- Class/species features.
- Environment-derived owner logic.
- Rest, food, disease, or other temporary conditions.

The owning resource strategy still decides whether regeneration/decay is currently allowed. A rate modifier cannot bypass `not regenerating while sprinting`, `no sunlight`, suppression, or another strategy condition unless it also supplies an explicit availability/condition override.

---

## 12. SPENDING, DRAINING, RESTORATION, SETTING, AND OVERFLOW

### 12.1 All normal mutations use the service

```java
public interface PlayerResourceService {
    ResourceSnapshot snapshot(ServerPlayer player, Identifier resourceId);

    ResourceOperationResult trySpend(
            ServerPlayer player,
            ResourceCost cost,
            ResourceContext context);

    ResourceOperationResult restore(
            ServerPlayer player,
            ResourceAmount amount,
            ResourceContext context);

    ResourceOperationResult drain(
            ServerPlayer player,
            ResourceAmount amount,
            ResourceContext context);

    ResourceOperationResult set(
            ServerPlayer player,
            ResourceTarget target,
            ResourceContext context);

    ResourceTransactionResult transact(
            ServerPlayer player,
            ResourceTransaction transaction,
            ResourceContext context);
}
```

Do not expose mutable state maps to gameplay systems.

### 12.2 Spend versus drain

Use distinct semantics:

- **Spend**: an intentional cost for an action. Requires affordability unless partial spending is explicitly allowed.
- **Drain**: an external or ongoing loss. Clamps at minimum unless depletion policy causes another action.
- **Restore**: raises current toward maximum or overflow.
- **Set**: privileged correction, migration, initialization, admin, or controlled transformation.
- **Transfer**: not in V1 unless real player-to-player resource transfer content requires it.

This distinction improves events, tooltips, debugging, and exploit review.

### 12.3 Partial operations

Partial spending is forbidden by default.

A cost either commits in full or fails.

Partial restoration and drain are normal because clamping may reduce the applied amount.

The result reports both requested and applied units.

### 12.4 Overflow

Overflow is opt-in.

Definition requirements:

- `OVERFLOW` capability.
- Maximum overflow amount or overflow resolver.
- Overflow decay/consumption rule.
- Explicit display behavior.
- Death and persistence policy.

Overflow must be stored separately from normal current state.

Examples:

- A future Solar Charge overcharge.
- A temporary effect granting surplus Ki.
- Not ordinary Mana restoration.
- Not a hidden way to avoid clamping on equipment removal.

### 12.5 Depletion

The generic service detects transitions to minimum and publishes an event. The owning system determines consequences.

Examples:

- Stamina depletion: Movement stops sprint/flight and Combat may apply short-timescale exhaustion.
- Mana depletion: spells fail affordability.
- Rage charge depletion: no new Rage activation.
- Solar Charge depletion: species abilities deactivate or become unavailable.
- Thirst depletion: Survival applies penalties.
- Sanity threshold crossing: future Sanity system responds.

The Resource API must not contain hardcoded depletion consequences.

---

## 13. ATOMIC RESOURCE TRANSACTIONS

### 13.1 Motivation

An action may cost multiple resources:

- Mana and Stamina.
- Ki and a spell slot.
- Solar Charge and Stamina.
- Multiple slot-like or charge pools.
- Resource cost plus a future cross-domain item/currency cost.

No operation may spend one cost and then fail another.

### 13.2 Resource-only transaction

```java
public record ResourceTransaction(
        List<ResourceOperation> operations,
        TransactionMode mode,
        Optional<UUID> requestNonce
) {}
```

V1 transaction mode:

```java
ATOMIC_ALL_OR_NOTHING
```

Validation phase:

1. Resolve definitions.
2. Resolve activation and availability.
3. Resolve current/max snapshots.
4. Validate every amount/tier.
5. Run cancellable pre-validation hooks.
6. Build immutable prepared deltas.
7. Verify revision has not changed.

Commit phase:

1. Apply all prepared deltas.
2. Increment player resource revision once.
3. Mark changed resources dirty.
4. Publish non-cancellable post events.
5. Send one batched delta packet.

### 13.3 Cross-domain transactions

The future Economy Transaction API may coordinate resources, items, currencies, quest tokens, or other domains.

This API should expose a prepared operation token:

```java
PreparedResourceTransaction prepare(...);
CommitResult commit(PreparedResourceTransaction prepared);
void rollback(PreparedResourceTransaction prepared);
```

However, V1 must not pretend to solve arbitrary cross-domain rollback before the shared transaction coordinator is designed.

Until then:

- Transactions containing only internally backed resources are fully atomic.
- Externally backed resources participate atomically only through a `TransactionalExternalResourceAdapter`.
- A mixed transaction lacking adapter-level prepare/commit support fails before mutation with `ATOMIC_OPERATION_UNSUPPORTED`.
- Ability/spell code validates non-resource prerequisites before resource commit.
- The final resource commit occurs as late as possible.
- Spells preserve the existing rule: consume the selected slot only after actual successful effect resolution.
- Any cross-domain operation needing true atomicity must wait for or use an explicit coordinator.

### 13.4 Idempotency

Client-originated mutation requests include a nonce.

The server keeps a short bounded cache of recent request outcomes per player and action channel. A duplicate request returns the prior result without spending twice.

---

## 14. PARTITIONED AND SLOT-LIKE OPERATIONS

### 14.1 Partitioned cost

```java
public record PartitionedResourceCost(
        Identifier resourceId,
        int partition,
        long amount,
        PartitionSelectionPolicy selectionPolicy
) implements ResourceCost {}
```

V1 selection policies:

```java
EXACT_TIER
AT_LEAST_TIER
LOWEST_ELIGIBLE_TIER
HIGHEST_ELIGIBLE_TIER
```

### 14.2 Spell slot rule

Player-cast spells use `EXACT_TIER`, implemented as an exact numeric partition selection.

The D&D Spell API maps the selected slot tier directly to the partition key. The player deliberately selects the slot tier. The server validates:

- The spell may be cast at that tier.
- The tier exists.
- A slot remains.
- The cast otherwise succeeds.

Do not automatically spend a higher slot merely because the minimum tier is empty.

`AT_LEAST_TIER` may be used by NPC automation or carefully authored effects, but ordinary player spellcasting must not use it.

### 14.3 Standard spell slots

`totality:spell_slots` uses `PARTITIONED_POOL`.

Maximum is resolved by the existing multiclass Full/Half/Third caster logic.

The resource framework stores current slot counts. The D&D Spell API owns:

- Minimum spell level.
- Player-selected upcast level.
- Whether the spell scales.
- Class access.
- Known/prepared state.
- Components.
- Targeting.
- Successful resolution.

Long Rest restoration is invoked by the spell/class integration listener, not by Rest core.

Wizard Arcane Recovery is a Wizard-owned algorithm that restores an eligible subset through `PlayerResourceService`.

### 14.4 Pact Magic

`totality:pact_magic_slots` is a separate `PARTITIONED_POOL`.

It must not be merged into standard multiclass slots.

Typical state:

- One active pact tier determined by Warlock level.
- Several current/max slots at that tier.
- Full restore on qualifying Short Rest and Long Rest.

`WarlockClass` owns:

- Grant and activation.
- Maximum slot count.
- Pact slot tier.
- Spell access.
- Rest listener registration.
- Any future invocations or patron modifications.

The Generic Resource API owns only the pool and operations.

---

## 15. REGENERATION, DECAY, AND PERIODIC CHANGE

### 15.1 No universal regeneration assumption

Each resource chooses one of:

- No passive change.
- Passive regeneration.
- Passive decay.
- Context-driven event changes.
- Rest-only recovery.
- Custom scheduled strategy.

Examples:

- Stamina: passive regeneration.
- Mana: passive regeneration if its current design requires it.
- Rage: no passive regeneration.
- Ki: no assumed passive regeneration; class rules own it.
- Standard spell slots: Rest/features only.
- Pact Magic: Rest only.
- Thirst: passive drain plus event-driven drain/restore.
- Rest Need: passive accumulation plus activity events.
- Solar Charge: context-driven sunlight charging.
- Sanity: future system-owned events, not generic ticking by default.

### 15.2 Central scheduler

Replace one independent full-player loop per resource with a shared scheduler where practical.

```text
ResourceTickScheduler
    - runs once per server tick
    - groups strategies by interval
    - evaluates only players with active instances
    - applies operations through PlayerResourceService
    - batches sync once per tick
```

A resource may register intervals such as:

- Every tick.
- Every 5 ticks.
- Every 20 ticks.
- Every 100 ticks.
- Event-only.

Avoid a design where adding 30 resources creates 30 loops over every online player.

### 15.3 Fractional accumulation

For fixed-point or low-rate changes:

- Strategy returns exact unit deltas when possible.
- Remainders may be accumulated per resource.
- Do not round a small positive regeneration to `1` every interval unless the owning balance rule explicitly intends that minimum.
- Do not let repeated truncation erase slow drain/regeneration.

### 15.4 Regen delay

A simple regeneration profile may support:

- Delay after spend.
- Delay after drain.
- Combat blocking.
- Movement/action blocking.
- Rate multiplier.
- Source-specific pause reasons.

This is a capability, not mandatory fields on every resource.

### 15.5 Offline progression

Default: **no offline regeneration or decay**.

A resource enabling offline progression must define:

- Time source.
- Maximum elapsed period.
- Whether server downtime counts.
- Whether logout in a dangerous state counts.
- How clock rollback is handled.
- What events are emitted on login.
- Whether death/respawn interrupts it.

Use capped elapsed duration and persisted server/game timestamps. Never trust an unbounded client or local wall-clock delta.

Thirst and Rest Need should not gain offline behavior until their owning designs explicitly decide it.

---

## 16. RESOURCE GRANTS, OWNERSHIP, VISIBILITY, ACTIVATION, AND AVAILABILITY

Registration is global; ownership is player-specific.

Rage, Ki, Solar Charge, Chakra, Reiatsu, Cursed Energy, and similar resources may be registered once for the game while remaining completely absent from players who do not possess the relevant class, species, lineage, discipline, transformation, or other source.

These concepts are separate axes and must not be collapsed into one enum or boolean.

### 16.1 Lifecycle axes

For a resource:

1. **Registered** — a definition exists in the game registry.
2. **Entitled** — the player has unlocked or is permitted to use the granting feature.
3. **Granted** — at least one currently valid authoritative source supplies the resource to this player.
4. **Instantiated** — mutable state exists for the player.
5. **Active** — the resource is currently relevant to the player's active build, class set, form, or mode.
6. **Visible** — the UI is permitted to reveal it.
7. **Available** — the player may currently spend, restore, charge, or otherwise use it.
8. **Locked** — a current rule blocks some or all interaction.
9. **Server-enabled** — configuration permits the owning system.

A registered but ungranted resource is not a locked player resource. It is simply not part of that player's resource set.

```text
Ordinary non-Barbarian:
  Rage registered globally
  Rage not granted
  No Rage state, regeneration, packet, HUD entry, or menu entry

Barbarian:
  Rage granted by BarbarianClass
  Rage instantiated, active, visible, and available

Kryptonian Barbarian:
  Rage granted by BarbarianClass
  Solar Charge granted by KryptonianSpecies
  Both coexist as independent resources
```

### 16.2 Grant providers

Authoritative owning systems expose grants:

```java
public interface ResourceGrantProvider {
    Collection<ResourceGrant> getResourceGrants(ServerPlayer player);
}
```

```java
public record ResourceGrant(
        Identifier resourceId,
        Identifier sourceId,
        ResourceGrantSourceType sourceType,
        ResourceGrantMode mode,
        ResourceGrantInitialization initialization,
        ResourceRemovalPolicy removalPolicy,
        ResourceVisibilityPolicy visibilityPolicy,
        int priority
) {}
```

The exact implementation may use a builder, but every grant requires:

- Resource ID.
- Stable source ID.
- Source type.
- Whether the grant is persistent, conditional, or temporary.
- How this acquisition initializes a newly created resource instance.
- What happens when the source disappears.
- Visibility behavior.
- Deterministic priority when competing grants require it.

Canonical source types:

```java
public enum ResourceGrantSourceType {
    CLASS,
    SUBCLASS,
    SPECIES,
    ORIGIN,
    LINEAGE,
    DISCIPLINE,
    COVENANT,
    TRANSFORMATION,
    ABILITY,
    EQUIPMENT,
    STATUS_EFFECT,
    QUEST,
    GLOBAL_SYSTEM,
    CUSTOM
}
```

Examples:

```text
BarbarianClass       -> totality:rage
MonkClass            -> totality:ki
KryptonianSpecies    -> totality:solar_charge
NinjutsuDiscipline   -> totality:chakra
Shinigami source     -> totality:reiatsu
Jujutsu source       -> totality:cursed_energy
SurvivalSystem       -> totality:thirst
```

The resource definition must not hardcode checks such as `player is Barbarian`. `BarbarianClass` owns that decision and supplies the grant. The Resource API consumes grants without becoming a class, species, lineage, or discipline framework.

Universal resources use stable `GLOBAL_SYSTEM` grant sources. For example:

```text
totality:health_system   -> Health through external adapter
totality:food_system     -> Food through external adapter
totality:player_baseline -> Mana and Stamina
totality:survival        -> Thirst and Temperature
totality:rest_system     -> Rest Need/Fatigue
totality:sanity_system   -> Sanity
```

External universal grants expose the resource without creating Generic-component state. Their owning systems remain authoritative.

### 16.3 Grant identity and source tracking

Grants are tracked by source, not by one persisted `hasResource` boolean.

Canonical identity:

```text
(resourceId, sourceId)
```

Example:

```text
resourceId = totality:chakra
sourceId   = totality:ninjutsu_discipline
```

This allows several systems to affect one pool correctly:

```text
Ninjutsu discipline:
    grants Chakra ownership

Jinchuriki lineage:
    modifies Chakra maximum

Equipment:
    modifies Chakra regeneration

Status effect:
    suppresses Chakra use
```

The lineage, equipment, and effect do not create separate Chakra pools unless a dedicated design explicitly requires that.

Derived grants should normally be recalculated from their owning systems. Persist a grant only when its source is itself a durable independent grant, such as a quest reward or consumed permanent unlock.

### 16.4 Grant aggregation policy

Each resource definition declares how multiple valid grants aggregate:

```java
public enum ResourceGrantAggregationPolicy {
    SINGLE_OWNER,
    SHARED_RESOURCE,
    HIGHEST_PRIORITY_SOURCE,
    SEPARATE_INSTANCES
}
```

#### `SINGLE_OWNER`

One canonical owner grants the resource; other systems may modify it.

Recommended for Rage, Ki, and probably Solar Charge.

Unexpected competing grants resolve deterministically, log diagnostics, and must never silently sum.

#### `SHARED_RESOURCE`

Several compatible sources grant access to one shared pool.

Possible future uses include several magical classes contributing to Mana, or a discipline and lineage jointly establishing Chakra. Maximum contribution math remains owner-defined.

#### `HIGHEST_PRIORITY_SOURCE`

Only the highest-priority valid grant controls activation or base structure.

Useful for transformations or borrowed powers that replace a weaker version.

#### `SEPARATE_INSTANCES`

Use only when gameplay genuinely requires independent pools sharing one definition. Prefer separate resource IDs for mechanically distinct pools.

It requires an explicit instance key:

```java
public record ResourceInstanceKey(
        Identifier resourceId,
        Identifier instanceId
) {}
```

No current Rage, Ki, Solar Charge, Chakra, Reiatsu, or Cursed Energy design requires separate instances.

### 16.5 Entitlement ownership

The future Unlock/Access/Entitlement API owns whether a player has unlocked or may select the granting feature.

The Resource API integrates through a read-only view:

```java
ResourceEntitlementView entitlementView(
        ServerPlayer player,
        Identifier resourceId,
        Identifier grantSourceId
);
```

Canonical flow:

```text
Entitlement API:
    Monk is unlocked

Class system:
    Monk is in the active class build

MonkClass:
    grants totality:ki

Resource API:
    instantiates, stores, modifies, synchronizes, and presents Ki
```

Entitlement alone does not necessarily create a live resource. A player may have unlocked a class or transformation without currently using it.

Until the Entitlement API exists, current class/species/lineage/discipline components may act as temporary grant authorities. Do not persist a second entitlement system inside resource state.

### 16.6 Grant acquisition and initialization

When the first valid grant appears:

1. Validate the definition and grant source.
2. Resolve aggregation.
3. Reuse a legitimate existing state only when the acquisition policy permits it.
4. Otherwise create state.
5. Resolve effective maximum.
6. Apply the acquisition-specific initialization.
7. Mark activation and visibility dirty.
8. Publish `ResourceGrantAddedEvent`.
9. Synchronize only when visible.

Initialization is supplied by the grant/acquisition context rather than being locked only in the static definition:

```java
public sealed interface ResourceGrantInitialization {
    record AtMinimum() implements ResourceGrantInitialization {}
    record AtMaximum() implements ResourceGrantInitialization {}
    record AtFraction(long numerator, long denominator)
            implements ResourceGrantInitialization {}
    record AtAbsolute(ResourceAmount amount)
            implements ResourceGrantInitialization {}
    record PreserveExisting() implements ResourceGrantInitialization {}
    record Custom(Identifier strategyId)
            implements ResourceGrantInitialization {}
}
```

The resource definition may provide a default, while the authoritative grant source may override it for a specific acquisition.

Canonical examples:

- New-player Mana: `AtMaximum`.
- New-player Stamina: `AtMaximum`.
- Newly unlocked Rage or Ki: generally `AtMaximum`.
- A quest that grants a damaged, unstable, drained, or partially awakened resource: `AtFraction`, `AtAbsolute`, or `Custom`.
- A temporary borrowed resource: owner-defined, potentially partial.
- A negative-condition resource such as future Rest Need/Fatigue: generally `AtMinimum`, subject to its dedicated design.
- Solar Charge: decided by the Kryptonian acquisition event; it is not globally assumed to begin full.

Initialization happens only when creating or explicitly reinitializing an instance. Re-evaluating the same grant on login, respawn, dimension transfer, or build recalculation must not refill it.

A grant's starting value must be server-authored. Clients and quest dialogue packets may request an acquisition action but may not submit the authoritative initial amount.

### 16.7 Losing a grant source

Every grant declares a removal policy:

```java
public enum ResourceRemovalPolicy {
    REMOVE_STATE,
    PRESERVE_DORMANT,
    RESET_AND_PRESERVE,
    CONVERT,
    CUSTOM
}
```

- `REMOVE_STATE`: delete state after the final grant disappears. This is the default for class-, species-, lineage-, and discipline-specific pools when their ownership is actually removed, preventing unused dormant-state accumulation during testing or future respeccing.
- `PRESERVE_DORMANT`: retain current state but make it inactive, unavailable, and normally invisible. Use only when the design explicitly requires state to survive loss of the final grant.
- `RESET_AND_PRESERVE`: keep a dormant instance but reset it to an owner-defined value. This is also opt-in rather than a default.
- `CONVERT`: convert through an explicit owner-supplied migration or transaction.
- `CUSTOM`: requires an owner handler and acceptance tests.

Canonical distinction:

```text
Temporary inactivity:
  The grant still exists.
  The resource may become inactive/hidden.
  State is retained.

Actual ownership removal:
  The final grant disappears.
  Default source-specific policy is REMOVE_STATE.
  Reacquiring it later performs a new acquisition using the new grant's initialization.
```

Examples:

- Leaving a transformation while still owning it: normally deactivate while preserving the grant/state.
- Removing a class through a future respec: normally remove that class resource state.
- Testing with class/species commands: removing the source should not leave dozens of dormant pools.
- A special story resource that must remember prior depletion after being lost: explicitly use `PRESERVE_DORMANT`.

No implicit conversion is allowed between Mana, Ki, Chakra, Reiatsu, Cursed Energy, Rage, Solar Charge, or spell slots.

For shared grants, losing one source must not remove the pool while another valid ownership grant remains.

### 16.8 Activation

Activation means a granted resource is currently part of the live build, form, or enabled subsystem.

Grant and activation are not identical:

- A transformation resource may remain granted but inactive outside the form.
- Temporarily disabling a class feature does not necessarily remove its ownership grant.
- A Survival resource may remain granted and active whenever the subsystem is enabled.
- A temporary borrowed feature may activate a resource without permanent entitlement.
- A future respec that actually removes the final source grant normally removes that source-specific resource state.

The API must not infer activation from `current > 0`.

### 16.9 Availability and locking

Availability is derived after grant and activation.

Possible unavailability reasons:

```text
totality:resource_unavailable
totality:suppressed_by_kryptonite
totality:requires_meditation
totality:class_feature_inactive
totality:wrong_transformation
totality:anti_magic
totality:server_system_disabled
```

Structured failure must distinguish:

- Not granted.
- Granted but inactive.
- Active but unavailable.
- Locked by a rule.
- Insufficient current value.

Visibility does not authorize spending.

### 16.10 Visibility policy

```java
public enum ResourceVisibilityPolicy {
    WHEN_GRANTED,
    WHEN_ACTIVE,
    WHEN_AVAILABLE,
    ALWAYS_FOR_OWNER,
    MENU_ONLY,
    HIDDEN,
    CUSTOM
}
```

Default for class-, species-, lineage-, and discipline-specific resources:

```text
WHEN_ACTIVE
```

Ungranted resources should normally be absent from:

- HUD selection.
- Character resource menus.
- Live-resource tooltips.
- Resource-state packets.
- Generic affordability lists.
- Ordinary client debug data.

They should not appear as a large list of locked resources:

```text
Rage — Locked
Ki — Locked
Chakra — Locked
Reiatsu — Locked
Cursed Energy — Locked
Solar Charge — Locked
```

A progression or catalogue screen may preview future resources by querying class/species/lineage/discipline metadata. That does not create live player state.

### 16.11 Secret-resource privacy

When existence or value is secret, do not send the resource with `hidden=true`.

Omit:

- State.
- Current and maximum.
- Grant source.
- Lock or suppression reason.
- Stable placeholders that reveal it.

Clients must not infer hidden resources from ordering, empty slots, or packet counts.

### 16.12 Grant reconciliation triggers

Re-evaluate grants when:

- Class/subclass changes.
- Species/origin/lineage changes.
- Discipline/covenant changes.
- Transformation starts or ends.
- Quest grant changes.
- Equipment or grant-producing effect changes.
- A global subsystem is enabled or disabled.
- Definitions reload.
- Login, respawn, dimension transfer, or migration completes.

Reconciliation must be idempotent and batched. One build change must not repeatedly initialize, refill, delete, and recreate the same pool.

### 16.13 Canonical ownership examples

```text
HP/Health
  Universal first-class resource
  State authority: EXTERNAL_ADAPTER
  Authoritative owner: Health/Combat
  HUD role: CORE_CONSTANT

Food/Hunger
  Universal first-class resource
  State authority: EXTERNAL_ADAPTER
  Authoritative owner: Food/Hunger
  HUD role: CORE_CONSTANT

Temperature
  Universal first-class resource when Survival/Temperature is enabled
  State authority: EXTERNAL_ADAPTER
  Authoritative owner: Survival/Temperature
  HUD role: SURVIVAL_CONSTANT

Stamina
  Universal grant during player initialization
  Initial value: full

Mana
  Universal grant during player initialization
  Initial value: full
  Not restricted to RPG classes

Thirst
  Universal grant when Survival/Thirst is implemented and enabled
  Initial value: owner-defined, normally full
  HUD role: SURVIVAL_CONSTANT

Rest Need/Fatigue
  Universal grant when implemented and enabled
  Public name, identifier, range, and initialization finalized by its dedicated design

Sanity
  Universal grant when implemented and enabled
  Initialization finalized by its dedicated design
  HUD role: SURVIVAL_CONSTANT

Rage
  Owner: BarbarianClass
  Aggregation: SINGLE_OWNER
  Removal after final ownership grant disappears: REMOVE_STATE
  Temporary inactivity while still granted: preserve state
  Initial value: normally full
  Visibility: WHEN_ACTIVE

Ki
  Owner: MonkClass
  Aggregation: SINGLE_OWNER
  Removal after final ownership grant disappears: REMOVE_STATE
  Temporary inactivity while still granted: preserve state
  Initial value: normally full
  Visibility: WHEN_ACTIVE

Solar Charge
  Owner: KryptonianSpecies
  Aggregation: SINGLE_OWNER
  Removal after final ownership grant disappears: REMOVE_STATE unless its later species design explicitly preserves it
  Initial value: acquisition-specific
  Visibility: WHEN_ACTIVE

Chakra
  Primary ownership grant: future Ninjutsu access/discipline or equivalent source
  Pool: normally one shared Chakra pool
  Uzumaki bloodline: maximum modifier, not a second pool
  Jinchuriki state: additional maximum modifier or owner-defined contribution, not a second pool
  Aggregation: SHARED_RESOURCE only if several legitimate ownership sources are later required
  Visibility: WHEN_ACTIVE

Reiatsu
  Owner: future Shinigami lineage, discipline, or equivalent source
  Aggregation/removal: explicitly decided by its dedicated design
  Visibility: WHEN_ACTIVE

Cursed Energy
  Owner: future Jujutsu lineage, discipline, or equivalent source
  Aggregation/removal: explicitly decided by its dedicated design
  Visibility: WHEN_ACTIVE
```

The generic framework supports these without hardcoding Naruto, Bleach, Jujutsu Kaisen, DC, D&D, or any other universe.

---

## 17. PERSISTENCE, COPY, DEATH, AND RESPAWN

### 17.1 Component strategy

Use one `PlayerResourceStateComponent` registered through the existing Totality component framework.

Recommended component-level respawn strategy:

```text
RespawnStrategy.ALWAYS_COPY
```

Then apply each resource's death policy explicitly during respawn/death processing.

Reason: different resources need different policies, while the component framework's strategy applies to the whole component.

### 17.2 Lifecycle policy

```java
public record ResourceLifecyclePolicy(
        ResourceDeathPolicy deathPolicy,
        boolean persistThroughLogout,
        boolean persistThroughDimensionChange,
        boolean persistTemporaryModifiers,
        ResourceInitializationPolicy initializationPolicy
) {}
```

Standard death policies:

```java
KEEP_CURRENT
RESET_TO_MINIMUM
RESET_TO_MAXIMUM
SET_TO_AUTHORED_VALUE
CLEAR_OVERFLOW
CUSTOM
```

Policies may combine a current-value rule with overflow/modifier cleanup.

Examples requiring owner decisions:

- Mana: likely keep or restore according to existing death behavior.
- Stamina: likely reset/restore, but preserve current implementation until explicitly changed.
- Rage charges: preserve existing behavior unless class design changes.
- Solar Charge: species-owned death rule.
- Thirst: Survival-owned death rule.
- Rest Need: Rest-owned death rule.
- Spell slots: preserve existing death/respawn behavior during migration.
- Temporary transformation resources: often reset/remove.

This document does not silently invent those balance rules.

### 17.3 Persistence content

`PlayerResourceStateComponent` persists only `GENERIC_COMPONENT` resources.

Health, Food, Temperature, and other externally backed resources persist through their authoritative owners. The Generic Resource component must not serialize cached copies.

Persist internally backed state:

```text
resource_schema_version
resource_revision
states:
  resource_id:
    model
    state_version
    current / partition map
    overflow
    regen remainder
    approved timing fields
persistent modifiers
orphaned states
```

Do not persist computed maximum.

### 17.4 Read validation

On NBT read:

- Clamp impossible negative values where the definition forbids them.
- Check arithmetic bounds.
- Validate tier range.
- Preserve unknown fields where feasible for forward compatibility.
- Move unknown resources to orphan storage.
- Run version migrations before normal reconciliation.
- Log corruption without crashing the entire player load when safe recovery is possible.

### 17.5 Dimension changes

Dimension transfer must not reset resources or double-apply initialization.

Full state may be resynchronized after transfer, but initialization hooks must be idempotent.

### 17.6 Logout exploit prevention

Persist every field needed to prevent relog exploits, including:

- Current resource values.
- Relevant regen delays.
- Persistent timed modifiers.
- Rest Need once implemented.
- Any resource lock intended to survive relog.
- Short Rest count remains in the Rest system, not this component.

Do not use disconnect as an implicit reset.

---

## 18. SERVER AUTHORITY, CLIENT PREDICTION, AND SYNCHRONIZATION

### 18.1 Server authority

Only the server mutates authoritative state.

Clients send intent:

- Activate ability.
- Cast spell at selected tier.
- Toggle movement mode.
- Use item.
- Select rest option.

Clients do not send:

- "Subtract 10 Stamina."
- "Set Mana to 50."
- "Restore a slot."
- "My maximum is 200."
- "I am entitled to Ki."

The server re-evaluates all costs and conditions.

### 18.2 Full and delta synchronization

A full resource snapshot combines internally backed state with all visible externally backed resource snapshots.

Send a full resource snapshot:

- On join.
- After respawn.
- After dimension transfer.
- After migration.
- After structural definition reload.
- When a debug/admin resync is requested.

Send revisioned deltas:

- At most once per server tick per player for ordinary changes.
- Containing all resources changed that tick.
- Including new revision.
- Including current, effective max, overflow, active/available flags, and allowed reason summaries.
- Omitting secret resources.

### 18.3 Batched dirty tracking

Do not send one packet per Stamina point.

`PlayerResourceService` marks resource IDs dirty. External owners report before/after snapshots or an invalidation token through `ExternalResourceChangeBridge`; the bridge advances the unified resource-view revision and marks the registered ID dirty. `ResourceSyncManager` flushes after server logic completes for the tick.

Where the client already receives reliable native state, an adapter may use `NATIVE_CLIENT_STATE` rather than duplicate the value in generic packets. HUD and menus still consume the unified resource view.

This avoids double synchronization for Health and Food while preserving one presentation/query contract.

### 18.4 Client prediction

Prediction is opt-in and presentation-only.

Suitable examples:

- Stamina drain from local sprinting.
- Stamina drain from locally initiated biological flight.
- Smooth interpolation of frequent continuous bars.

Unsuitable examples:

- Spell slot consumption.
- Pact Magic slot consumption.
- Rage charge recovery.
- Rest completion.
- Thirst consequences.
- Sanity changes.
- Hidden resources.
- Random or target-dependent costs.

Prediction contract:

1. Client predicts an approved deterministic delta.
2. Request carries nonce and last known revision.
3. Server evaluates actual state.
4. Server sends confirmed revision/delta.
5. Client reconciles and corrects smoothly or immediately depending presentation.
6. Client prediction never enables an action the server rejected.

A resource must have `CLIENT_PREDICTION` capability and a registered prediction policy.

### 18.5 Stale revisions

Server mutation requests may include a client revision for diagnostics, but ordinary actions should not fail solely because a cosmetic client snapshot is slightly stale.

The server must still validate against current authoritative state.

Prepared server-side transactions, however, must fail with `STALE_REVISION` if state changed between prepare and commit.

---

## 19. PRESENTATION METADATA

### 19.1 Presentation definition

```java
public record ResourcePresentationDefinition(
        String nameTranslationKey,
        String shortNameTranslationKey,
        String descriptionTranslationKey,
        Identifier icon,
        ResourceDisplayType displayType,
        ResourceDisplayGroup displayGroup,
        ResourceHudRole hudRole,
        ResourceDisplayConversion displayConversion,
        Identifier valueFormatterId,
        Identifier styleToken,
        int priority,
        boolean showWhenFull,
        boolean showWhenEmpty,
        boolean showWhenInactive,
        boolean showNumericText,
        boolean smoothChanges
) {}
```

### 19.2 Mechanical-to-display conversion

```java
public record ResourceDisplayConversion(
        long numerator,
        long denominator
) {
    public static final ResourceDisplayConversion IDENTITY =
            new ResourceDisplayConversion(1, 1);
}
```

```text
display value = mechanical value × numerator / denominator
```

The denominator must be positive.

```java
public interface ResourceValueFormatter {
    Component formatCurrent(ResourceSnapshot snapshot, ResourceDisplayContext context);
    Component formatMaximum(ResourceSnapshot snapshot, ResourceDisplayContext context);
    Component formatPair(ResourceSnapshot snapshot, ResourceDisplayContext context);
    Component formatDelta(
            PlayerResourceDefinition definition,
            long mechanicalDeltaUnits,
            ResourceDeltaPresentationKind kind,
            ResourceDisplayContext context
    );
}
```

Canonical conversions:

```text
totality:health  5 / 1
totality:food    5 / 1
totality:mana    1 / 1
totality:stamina 1 / 1
```

The existing Totality Health display class is canonical behavior. It should be registered as or refactored into the shared Health formatter used by player HP, mob health bars, floating damage/healing, and other Health displays.

Food uses the same architecture: native Food mechanics remain unchanged, while HUD and tooltips show the converted value. A food restoring `6` mechanical Food displays `30`.

The formatter service may be reused for mobs and items without making them holders of `PlayerResourceStateComponent`.

### 19.3 Display types

```java
BAR
PIPS
SLOTS
NUMBER
STATE_INDICATOR
HIDDEN
```

Examples:

- Health: `BAR`.
- Food: `BAR`.
- Mana: `BAR`.
- Stamina: `BAR`.
- Rage: `PIPS`.
- Ki: `PIPS`.
- Solar Charge: `BAR`.
- Chakra: likely `BAR`.
- Standard spell slots: `SLOTS`.
- Pact Magic: `SLOTS` or compact pips plus tier label.
- Thirst: `BAR`.
- Rest Need: TBD by Rest design.
- Sanity: likely `BAR` or state indicator, TBD.
- Temperature: `BAR` or `STATE_INDICATOR`, using target-range presentation rather than high-is-good percentage.

### 19.4 Display groups and HUD roles

Display group describes the layout family:

```java
CORE_HEALTH
CORE_MANA
CORE_STAMINA
CORE_FOOD
CONTEXTUAL_POWER
SPELLCASTING
SURVIVAL_LEFT
SURVIVAL_RIGHT
MENU_ONLY
DEBUG
```

HUD role describes whether an element is structurally constant or conditionally inserted:

```java
public enum ResourceHudRole {
    CORE_CONSTANT,
    SURVIVAL_CONSTANT,
    CONTEXTUAL_ACCESS,
    CONTEXTUAL_RELEVANCE,
    MENU_ONLY,
    NONE
}
```

These concepts are separate:

- `displayGroup` answers **where** an eligible element belongs.
- `hudRole` answers **when and whether** it participates in the live HUD.
- Grant, activation, availability, and visibility still determine whether a resource is eligible.

### 19.5 Constant HUD layers

Totality's canonical HUD contains stable constant channels that are never displaced by class-, species-, lineage-, discipline-, or transformation-specific resources.

#### Core constant layer

- HP/Health.
- Mana.
- Stamina.
- Food/Hunger.

#### Survival constant layer

Once their owning systems are implemented and enabled:

- Thirst.
- Temperature.
- Sanity.

Rest Need/Fatigue is universal, but its exact live-HUD role remains deferred to its dedicated design because its final model, name, thresholds, and presentation are unresolved.

All listed constant values are first-class registered resources, even when externally backed:

```text
Health
  resource ID: totality:health
  authority: Health/Combat external adapter
  HUD role: CORE_CONSTANT

Mana
  authority: Generic Resource component
  HUD role: CORE_CONSTANT

Stamina
  authority: Generic Resource component
  HUD role: CORE_CONSTANT

Food/Hunger
  resource ID: totality:food
  authority: Food/Hunger external adapter
  HUD role: CORE_CONSTANT

Thirst
  authority: Generic Resource component / Survival owner
  HUD role: SURVIVAL_CONSTANT

Temperature
  resource ID: totality:temperature
  authority: Survival/Temperature external adapter
  HUD role: SURVIVAL_CONSTANT

Sanity
  authority: Generic Resource component / Sanity owner
  HUD role: SURVIVAL_CONSTANT
```

Constant means:

- The resource owns a stable HUD channel or reserved position.
- Its numeric display uses the registered conversion/formatter instead of exposing raw native units.
- Contextual resources cannot displace it.
- It remains eligible whenever its baseline system and HUD layer are enabled.
- Full, empty, comfortable, or stable state may reduce visual emphasis but does not surrender its structural slot.
- Temporary unavailability does not remove it.
- The HUD may use compact, expanded, faded, or icon-only variants without reclassifying it as contextual.

The Resource API supplies state and presentation metadata. The HUD API owns layout, side, spacing, responsive collapse, animation, and overlap resolution.

### 19.6 Contextual power-resource layer

Rage, Ki, Solar Charge, Chakra, Reiatsu, Cursed Energy, and similar source-specific pools belong to the contextual HUD layer.

They are HUD candidates only when all required conditions are satisfied:

1. The definition is registered.
2. The player has a valid grant.
3. State is instantiated.
4. The resource is active when its visibility policy requires activation.
5. It is visible to the player.
6. The relevant HUD/resource subsystem is enabled.

They do not appear merely because their definitions exist globally.

```text
Non-Barbarian
  Rage definition exists
  No Rage grant
  No Rage HUD candidate

Barbarian
  Rage grant active
  Rage appears in the contextual power area

Monk/Barbarian
  Ki and Rage are both contextual candidates

Kryptonian Barbarian
  Solar Charge and Rage are both contextual candidates
```

`CONTEXTUAL_ACCESS` means the resource may appear whenever the player can access it, even while full or currently unused. This is the normal role for Rage, Ki, Solar Charge, Chakra, Reiatsu, and Cursed Energy.

`CONTEXTUAL_RELEVANCE` is reserved for a resource whose dedicated design intentionally shows it only while recently changed, selected, equipped, transformed, endangered, or otherwise relevant. It must not be used merely to save screen space when the player routinely needs the information.

A contextual resource becoming unavailable does not necessarily disappear. Solar Charge suppressed by Kryptonite may remain visible but locked so the player understands why the associated powers fail. Grant/visibility policy and the owning design decide this; the HUD must not infer it from `current == 0`.

### 19.7 Multiple contextual resources

A player may legitimately possess several contextual resources through multiclassing, species, lineage, disciplines, equipment, transformations, or temporary grants.

The Resource API exposes **all eligible contextual candidates**. It never enforces “one special resource per player.”

The HUD presentation manager owns capacity and selection. It may use:

- Several contextual slots when space permits.
- One primary contextual slot plus cycling or expansion.
- A player-pinned resource.
- The resource required by the currently selected ability or spell.
- Recently changed resources.
- Stable class/species priority.
- A compact overflow indicator or expandable panel.

Conceptual selection state:

```java
public record PlayerContextualHudSelection(
        Optional<Identifier> pinnedPrimary,
        List<Identifier> playerOrder,
        List<Identifier> recentActivityOrder
) {}
```

Selection rules:

1. Filter to granted, visible, and eligible candidates.
2. Respect player pin/order where valid.
3. Prefer the resource required by the actively selected action when appropriate.
4. Use deterministic fallback priority.
5. Never hide a cost-critical resource without another immediate inspection path.
6. Removing a grant removes its candidate on the next authoritative HUD update.
7. HUD selection never grants, activates, restores, spends, or otherwise mutates resource state.

The exact number and geometry of contextual slots belong to the HUD design. The Generic Resource API supplies metadata and snapshots, not screen layout.

### 19.8 Canonical current HUD intent

- HP, Mana, Stamina, and Food/Hunger are constant HUD elements.
- Health and Food visually align to the same 100 baseline as Mana and Stamina through presentation conversion, without changing native mechanics.
- Rage and Ki use contextual pips.
- Solar Charge and Chakra use contextual bars.
- Reiatsu and Cursed Energy choose bar/pip treatment in their dedicated designs but remain contextual.
- Standard and Pact Magic slots use spellcasting presentation and do not replace Mana.
- Future Survival indicators may occupy their planned left/right hotbar areas according to their dedicated APIs.
- Player XP and level remain progression presentation, not Resource definitions.

The API exposes resource presentation candidates. The HUD owns layout and composes them with Health, Food, Experience, progression, temperature, and other non-resource views.

### 19.9 Menus and tooltips

Menu snapshot may include:

- Display-formatted current/max derived from authoritative mechanical values.
- Clearly labelled raw/mechanical values only in developer diagnostics.
- Tier counts.
- Regeneration/decay summary.
- Active maximum modifier summaries.
- Locked/unavailable reason translation keys.
- Source category: class, species, survival, item, other.
- Recovery hints supplied by the owning system.

Do not ask the Resource API to generate lore or class-feature descriptions.


Canonical tooltip rules:

- Food/Cooking stores mechanical Food restoration.
- Tooltips format that value through `totality:food`.
- Bread restoring `6` mechanical Food displays `+30 Food`.
- Saturation, quality, nutrients, condition, and Diet information remain separate owner-defined lines.
- Ability and spell cost tooltips likewise format mechanical costs through the target resource formatter.

---

## 20. EVENTS AND CALLBACKS

### 20.1 Event phases

Use a strict two-phase event model.

#### Pre-validation

`ResourceChangeAttemptEvent`

- Server-side.
- Cancellable.
- Read-only current snapshot.
- May add a structured failure reason.
- Must not mutate the same resource directly.
- Used for global suppressions, immunities, or policy checks.

#### Post-commit

`ResourceChangedEvent`

- Server-side.
- Non-cancellable.
- Includes before/after snapshots and actual applied delta.
- Used by achievements, HUD activity tracking, abilities, quests, analytics, and consequences.

### 20.2 Additional events

```text
ResourceMaximumChangedEvent
ResourceDepletedEvent
ResourceFilledEvent
ResourceOverflowChangedEvent
ResourceThresholdCrossedEvent
ResourceActivationChangedEvent
ResourceAvailabilityChangedEvent
ResourceDefinitionReloadedEvent
ResourceMigrationEvent
```

Only publish transition events when a boundary is actually crossed.

### 20.3 Cause and context

```java
public record ResourceCause(
        Identifier type,
        Optional<Identifier> sourceId,
        Optional<UUID> actorId
) {}

public record ResourceContext(
        ResourceCause cause,
        Optional<Identifier> abilityId,
        Optional<Identifier> spellId,
        Optional<Identifier> itemId,
        Optional<Identifier> restActivityId,
        Optional<BlockPos> position,
        Set<Identifier> flags,
        Optional<UUID> requestNonce
) {}
```

Common cause IDs:

```text
totality:ability_cost
totality:spell_cost
totality:movement_cost
totality:passive_regeneration
totality:environment_drain
totality:food_restore
totality:drink_restore
totality:short_rest
totality:long_rest
totality:class_feature
totality:species_feature
totality:equipment_modifier
totality:status_effect
totality:death
totality:migration
totality:admin_command
```

### 20.4 Reentrancy

Listeners must not recursively mutate the same resource inside a pre-event.

Nested post-event operations are queued until the current transaction finishes.

Protect against infinite event loops by tracking operation depth and repeated cause/resource pairs.

### 20.5 Listener failure

A post-event listener exception must:

- Be logged with resource, cause, and listener identity.
- Not roll back an already committed resource transaction.
- Not prevent synchronization.
- Not prevent remaining independent listeners unless the event bus's safety policy requires isolation.

Pre-event exceptions should fail closed for privileged costs and return `INTERNAL_ERROR`, rather than allowing free use.

---

## 21. CONDITIONS AND FAILURE RESULTS

### 21.1 Structured failure

```java
public record ResourceFailure(
        ResourceFailureCode code,
        Identifier reasonId,
        OptionalLong requiredUnits,
        OptionalLong availableUnits,
        OptionalInt partition
) {}
```

Canonical failure codes:

```java
UNKNOWN_RESOURCE
MODEL_MISMATCH
RESOURCE_NOT_INSTANTIATED
RESOURCE_NOT_ENTITLED
RESOURCE_INACTIVE
RESOURCE_UNAVAILABLE
RESOURCE_LOCKED
SYSTEM_DISABLED
INVALID_AMOUNT
INVALID_TIER
INSUFFICIENT_RESOURCE
NO_ELIGIBLE_TIER
MAXIMUM_ZERO
OVERFLOW_NOT_SUPPORTED
PARTIAL_OPERATION_FORBIDDEN
OPERATION_UNSUPPORTED_BY_AUTHORITY
ATOMIC_OPERATION_UNSUPPORTED
STALE_REVISION
DUPLICATE_REQUEST
BLOCKED_BY_RULE
MIGRATION_REQUIRED
CORRUPT_STATE
INTERNAL_ERROR
```

### 21.2 Result

```java
public record ResourceOperationResult(
        boolean success,
        ResourceFailure failure,
        ResourceSnapshot before,
        ResourceSnapshot after,
        ResourceAmount requested,
        ResourceAmount applied,
        long revision
) {}
```

Failure results should be suitable for:

- Server logic.
- Localized UI feedback.
- Debug logging.
- Tests.
- Network response.

Do not return only `boolean`.

### 21.3 Conditions

The Resource API may reference registered condition IDs for simple availability and regeneration checks.

It must not implement the full future UnlockRequirement tree.

Examples of conditions:

```text
totality:not_sprinting
totality:not_drawing_bow
totality:not_in_combat
totality:in_direct_sunlight
totality:has_monk_class
totality:rest_activity_meditation
totality:not_suppressed
```

Complex condition composition should eventually use the shared Unlock/Condition infrastructure once designed.

---

## 22. INTEGRATIONS

### 22.1 Ability integration

An ability defines a `ResourceCost` or computes one server-side.

Ability flow:

1. Validate ability unlocked/equipped/eligible.
2. Validate target/context.
3. Prepare resource transaction.
4. Execute effect.
5. Commit at the effect's defined commitment point.
6. Return structured outcome.

For instant deterministic abilities, commit may occur before effect execution after all failure checks pass.

For effects that may resolve to no target/no effect, follow the spell pattern: commit only after success.

Ownership boundary:

- A named shared pool consumed by several abilities/features belongs in the Resource API: Mana, Stamina, Rage, Ki, Chakra, Solar Charge.
- A cooldown belongs in the Ability API.
- One ability's private uses-per-rest normally belong in Ability/class-feature state.
- A once-per-Long-Rest feature flag such as Wizard Arcane Recovery's own availability is feature state; the spell slots it restores are resources.
- Concentration belongs in `ConcentrationComponent`.
- Item-bound charges belong to the item stack/component, even when Rest restores them.
- A future Gallifreyan regeneration count could use the Resource API because it would be a player-owned, species-granted finite pool crossing death events, but that mechanic is not adopted by this document.

Do not migrate every integer field into `PlayerResourceStateComponent` merely because it decreases.

### 22.2 D&D Spell integration

The D&D Spell API owns casting.

Resource API supports:

- Standard slot lookup and exact-tier spending.
- Pact Magic slot lookup and spending.
- Optional Mana costs for non-slot magic systems, without merging them.
- Structured affordability display.

Spell slots are not cooldowns and are not Mana.

### 22.3 Attributes, Skills, player level, class level, and masteries

These systems are **inputs and grant/modifier sources**, not Generic Player Resources.

Current architectural relationship:

```text
Stats/Attributes
    -> effective scores/modifiers
    -> resource maximum/cost/rate calculations

Player progression
    -> player level and available class-allocation points
    -> grant eligibility and universal-resource scaling where authored

Class progression
    -> per-class levels
    -> Rage/Ki/Pact Magic/Hit Dice grants and maximums

Skills/Masteries
    -> unlocks and modifiers
    -> resource max/cost/rate/availability when authored

Generic Resource API
    -> stores and mutates the resulting runtime pools
```

Current Totality progression facts that callers may read—but must not duplicate as Resource constants—include:

- Character level cap: 150.
- Skill level cap: 100.
- Class progression: up to 30 allocated class levels shared across the player's multiclass build, currently derived from player progression at one available class point per five player levels.
- Attributes use the authoritative layered Stats result.
- Mastery points are a separate progression budget awarded through player progression.

The Resource API remains valid if any cap, cadence, attribute formula, XP curve, or class-allocation rule changes later.

Examples:

- CON influences HP through Health/Stats, not by storing CON in resource state.
- END/STR may influence Stamina maximum.
- INT or future general-magic progression may influence Mana.
- Barbarian level determines Rage maximum.
- Monk level determines Ki maximum.
- Warlock level determines Pact Magic partition and count.
- Every allocated class level contributes one Hit Die of its class-authored size.
- A mastery may reduce ability Stamina cost or improve Mana regeneration.
- A future Ninjutsu skill may improve Chakra use without owning the Chakra state.

Do not award Skill XP from `ResourceChangedEvent` alone. Award it from the successful action that caused the spend, using the action's own anti-farming rules.

### 22.4 Class integration

Each canonical class implementation class owns its resource definitions and hooks.

Examples:

- `BarbarianClass` activates Rage, resolves maximum charges, and registers rest behavior.
- `MonkClass` activates Ki, resolves maximum, defines costs, and handles qualifying meditation.
- `WarlockClass` activates Pact Magic slots and resolves pact tier/count.
- `WizardClass` owns Arcane Recovery and standard slot access.
- Every class implementation declares its Hit Die size and contributes partitions to the shared Hit Dice pool.

Class-level resource formulas read the player's actual per-class allocation. Totality's 30 class levels are shared across the multiclass build; the API must not calculate Rage, Ki, Pact Magic, or Hit Dice as though every class independently had 30 levels.

Shared registries may support this, but class implementation classes remain authoritative organizers.

### 22.5 Species/origin/lineage integration

A species or origin may:

- Grant a resource.
- Activate it.
- Supply a maximum resolver.
- Supply regeneration/charging conditions.
- Supply modifiers.
- Register depletion consequences through its ability implementation.

Examples:

- Kryptonian species module owns Solar Charge sunlight rules and Kryptonite suppression.
- A Naruto lineage/module may own Chakra formulas and jutsu costs.
- Viltrumite physiology continues using Stamina for movement unless a later design creates a distinct resource.

### 22.6 Rest integration

Rest core publishes rest completion/context events. It does not enumerate resources.

Owning listeners call Resource API.

Examples:

- Stamina module: full restore on valid Long Rest.
- Rage/Barbarian: `+1` on Short Rest; full on Long Rest.
- Warlock: full Pact Magic on Short or Long Rest.
- Monk: full Ki on qualifying meditation; full on Long Rest.
- Wizard: Arcane Recovery feature performs limited restoration; Long Rest restores eligible standard slots.
- Health/Class integration: Short Rest may spend Hit Dice to heal; Long Rest restores the Hit Dice pool according to the adopted rule.
- Survival: Rest causes Thirst drain.
- Magic item module: restores authored item-bound charges through item state, not the player Resource component.

Rest context should include:

```text
RestType
duration
activity
completion quality
whether restorative
location/comfort summary
```

The owning listener decides qualification. The Resource API merely applies the resulting operation.

### 22.7 Food, Cooking, Diet, and drink integration

Food is a first-class registered resource backed by Food/Hunger.

Canonical consumption flow:

1. Cooking/Food resolves item, portion, processing, quality, and contents.
2. Fluid Material and Safety resolve liquids, contamination, salinity, and toxicity.
3. Diet resolves meal history, variety, quality, nutrients, and long-term consequences.
4. Food/Hunger applies primary Food change, saturation, exhaustion, and vanilla rules.
5. Thirst/Survival applies hydration restoration or drain.
6. Other owners apply Mana, Stamina, Temperature, disease, effect, or resource changes.
7. External Food changes are bridged into Generic Resource events/presentation.
8. Post-events report actual applied changes.

Rules:

- Cooking and Diet may query `totality:food` through `PlayerResourceService`.
- The Food adapter delegates mutation to Food/Hunger in mechanical units.
- Cooking screens and item tooltips use the shared Food formatter.
- Resource API does not own saturation, exhaustion, meal history, nutrients, quality, variety, or diet streaks.
- A generic Food restore event does not prove that Diet history was recorded.
- A drink must not restore Thirst before Safety/Fluid resolution.

### 22.8 Health integration

Health is a first-class registered resource backed by Health/Combat.

Integrations may query HP, use health percentage in conditions, request authorized healing, and observe generic Health change events.

They may not treat `drain health` as ordinary subtraction, bypass damage/death hooks, fold absorption into base HP, or persist another HP copy.

Hit Dice and Rest calculate healing intent; Health applies it.

### 22.9 Temperature integration

Temperature is a first-class target-range resource backed by Survival/Temperature.

Environment, equipment, food/drink, wetness, shelter, effects, species, and abilities supply inputs to Temperature. The owner calculates state, applies warming/cooling, publishes changes, and owns thresholds/consequences.

Other systems may react to Temperature snapshots or threshold events for Stamina, Thirst, Rest, disease, spells, and abilities. They must not calculate a second body-temperature value.

### 22.10 Environment integration

Environment produces context. Resource-owning systems consume it.

Examples:

- Survival reads heat/desert context and drains Thirst.
- Temperature system reads Environment and applies Stamina consequences.
- Kryptonian species reads sunlight context and charges Solar Charge.

Resource API must not scan biomes, sky visibility, shelter, weather, temperature, or dimensions independently.

### 22.11 Equipment integration

Equipment may provide:

- Maximum modifiers.
- Regeneration/decay rate modifiers.
- Cost modifiers.
- Availability overrides.
- Presentation selection hints.

Equipment state is the source of truth. Resource state does not persist equipment-derived modifiers.

On equipment change:

1. Invalidate affected resource maxima/strategies.
2. Recalculate.
3. Reconcile current according to policy.
4. Sync one batched delta.

### 22.12 Status effects

Effects may:

- Add temporary max modifiers.
- Multiply regeneration.
- Block spending.
- Apply periodic drain.
- Provide overflow.
- Suppress activation.

Effect duration remains in the status-effect system. Avoid persisting a duplicate timer in resource state.

### 22.13 Commands and debugging

Admin/debug commands must use `PlayerResourceService`, not mutate component fields.

Recommended commands:

```text
/totality resource list <player>
/totality resource get <player> <id>
/totality resource set <player> <id> <value> [partition]
/totality resource add <player> <id> <value> [partition]
/totality resource fill <player> <id>
/totality resource empty <player> <id>
/totality resource resync <player>
/totality resource migrate <player>
/totality resource inspect-modifiers <player> <id>
```

Every command mutation uses cause `totality:admin_command`.

---

## 23. DATA-DRIVEN VERSUS CODE-OWNED CONFIGURATION

### 23.1 Data-driven fields

Safe candidates for JSON/datapack definitions:

- ID.
- Translation keys.
- Icon.
- Display type/group/HUD role/style token.
- Priority.
- Unit scale.
- Absolute minimum.
- Authored base maximum for simple fixed resources.
- Capabilities.
- Simple overflow cap.
- Lifecycle policy.
- Maximum-change default.
- Strategy identifiers.
- State authority (`GENERIC_COMPONENT` or `EXTERNAL_ADAPTER`).
- External adapter identifier.
- Target-range presentation metadata.
- Rational display conversion metadata.
- Value formatter identifier.
- Simple regeneration profile parameters.
- Simple threshold presentation metadata.
- Definition version.

### 23.2 Code-owned behavior

Keep these code-owned:

- Health, Food/Hunger, and Temperature external adapters.
- Damage, healing, eating, saturation, exhaustion, and body-temperature semantics.
- Attribute formulas.
- Class level tables.
- Multiclass slot calculation.
- Pact Magic tier/count logic.
- Arcane Recovery allocation.
- Qualifying meditation.
- Rage progression.
- Environment sampling.
- Sunlight/Kryptonite logic.
- Water contamination and salinity.
- Rest Need accumulation formulas until designed.
- Sanity transitions.
- Spell upcasting and target resolution.
- Complex depletion consequences.
- Cross-domain transactions.
- Anything requiring arbitrary player/game state access.

### 23.3 Strategy IDs

JSON may refer to registered code:

```json
{
  "id": "totality:stamina",
  "model": "scalar",
  "maximum_resolver": "totality:stamina_from_attributes",
  "regeneration_strategy": "totality:stamina_default",
  "presentation": {
    "display_type": "bar",
    "display_group": "primary_combat"
  }
}
```

Missing strategy IDs fail definition validation. Do not silently fall back to no behavior for core resources.

### 23.4 Reload behavior

Presentation and simple tuning may reload.

On reload:

1. Validate all definitions atomically.
2. Reject the reload if a core definition becomes structurally incompatible.
3. Swap the registry only after all definitions pass.
4. Recalculate affected players.
5. Reconcile values.
6. Send updated definitions/snapshots.
7. Publish reload event.

---

## 24. EXISTING IMPLEMENTATION AND MIGRATION

### 24.1 Preserve behavior before refactoring

The first migration phase must be behavior-neutral.

Do not combine migration with balance changes to:

- Mana maximum.
- Mana regeneration.
- Stamina costs.
- Stamina regeneration.
- Spell-slot tables.
- Rage maximum charges.
- Rest recovery amounts.
- Death behavior.

Any balance change must be a separate explicit commit and test.

### 24.2 Legacy systems to adapt

Existing known systems:

- `PlayerResourceComponent`.
- `PlayerResourceRecalculator`.
- `PlayerManaManager`.
- `PlayerStaminaManager`.
- Mana tick/sync code.
- Stamina tick/sync code.
- `SpellSlotComponent`.
- `SpellSlotRecalculator`.
- `PlayerChargesComponent`.
- `ChargeComponents`.
- Rage's existing `CHARGE_ID`.
- Dedicated Mana/Stamina/slot client mirrors and packets.
- HUD `ISecondaryResource` or equivalent resource presentation hooks.

### 24.3 Compatibility facades

Keep old public managers temporarily.

Example:

```java
public final class PlayerStaminaManager {
    public static int getStamina(ServerPlayer player) {
        return Math.toIntExact(
            PlayerResourceService.get().snapshot(player, STAMINA_ID).currentUnits()
        );
    }

    public static void removeStamina(ServerPlayer player, int amount) {
        PlayerResourceService.get().drain(
            player,
            ResourceAmount.scalar(STAMINA_ID, amount),
            ResourceContexts.legacyStamina()
        );
    }
}
```

Add deprecation comments only after all call sites can migrate safely.

### 24.4 NBT migration

Component schema migration must be idempotent.

Recommended sequence:

1. Load new resource component.
2. If `totality:mana` is absent and legacy Mana data exists, import it.
3. If `totality:stamina` is absent and legacy Stamina data exists, import it.
4. If `totality:spell_slots` is absent and legacy `SpellSlotComponent` data exists, import every current tier exactly.
5. If a charge pool is absent in new state and legacy `PlayerChargesComponent` contains it, import by existing pool ID.
6. Mark migration version.
7. Recalculate maximums through canonical resolvers.
8. Reconcile only invalid values; otherwise preserve current amounts.
9. Save new format.
10. Keep legacy read fallback for one controlled transition period.
11. Do not indefinitely dual-write two authoritative stores.

If migration is interrupted, rerunning must not duplicate values or restore consumed slots/charges.

### 24.5 Spell-slot migration decision

Canonical end state:

- Standard slots live as `PARTITIONED_POOL` resource state.
- Pact Magic lives as a separate `PARTITIONED_POOL`.
- D&D Spell API interacts through an adapter/service.
- `SpellSlotComponent` becomes a compatibility facade or is removed after migration.

Safe implementation sequence permits `SpellSlotComponent` to remain the authoritative backing store temporarily behind a `PartitionedResourceAdapter`, provided there is never dual authority.

### 24.6 Charge-pool migration

The current Rage implementation proves that generic identified charge pools are useful.

Preserve:

- Existing pool ID.
- Current value.
- Maximum.
- Short Rest `+1`.
- Long Rest full.
- Persistence behavior.
- HUD pip behavior.

Move recharge metadata out of mutable player state where possible. Recharge rules belong to Barbarian/class integration, not to generic state.

`PlayerChargesComponent.registerWithRestBus()` is currently a dead no-op according to the audit and may be removed after the new integration is active.

### 24.7 Mana and Stamina migration

Initial definitions must reproduce current formulas and timing.

Do not infer formulas from names. Claude Code must inspect current:

- `PlayerResourceRecalculator`.
- `PlayerManaManager`.
- `PlayerStaminaManager`.
- Mana/Stamina tick handlers.
- Movement stamina costs.
- Bow stamina handler.
- Combat exhaustion integration.
- Dedicated sync payloads.
- HUD readers.

Historical code shows Stamina being drained by biological flight, sprinting, and bow use, with regeneration blocked by activity and modified by the current combat exhaustion system. Treat that as a pattern to preserve only after checking the latest code.

### 24.8 Network migration

Phase transition:

1. Generic component exists server-side while old packets remain.
2. Old client managers read generic server state through adapters.
3. Generic snapshot packet is added.
4. HUD/menu switches to `ClientResourceManager`.
5. Dedicated resource packets stop being sent.
6. Old packet handlers are removed after no callers remain.

Do not remove dedicated packets before all screens/HUD elements have migrated.

### 24.9 Never migrate combat Exhaustion into Rest Need

`api/rpg/combat/exhaustion/ExhaustionManager` represents immediate Stamina-derived combat condition.

Future Rest Need/Fatigue remains distinct.

No migration, shared ID, shared state, or automatic threshold mapping is allowed without a later dedicated design decision.

---

## 25. EXAMPLE CANONICAL IMPLEMENTATIONS

### 25.1 Health

```text
ID: totality:health
Model: SCALAR
State authority: EXTERNAL_ADAPTER
Adapter: totality:health
Polarity: HIGH_IS_GOOD
Display: BAR / CORE_HEALTH
Display conversion: 5 / 1
Mechanical baseline: 20
Displayed baseline: 100
HUD role: CORE_CONSTANT
Ownership: universal
Owner: Health/Combat
```

The API exposes current/max HP, conditions, events, and presentation. Health/Combat owns damage, healing, absorption, death, attributes, persistence, and synchronization semantics.

All player-facing Health values use the shared ×5 formatter. Native Health and damage mechanics are unchanged.

### 25.2 Food

```text
ID: totality:food
Model: SCALAR
State authority: EXTERNAL_ADAPTER
Adapter: totality:food
Polarity: HIGH_IS_GOOD
Display: BAR / CORE_FOOD
Display conversion: 5 / 1
Mechanical baseline: 20
Displayed baseline: 100
HUD role: CORE_CONSTANT
Ownership: universal
Owner: Food/Hunger
```

The primary value maps to authoritative Food/Hunger level. Saturation, exhaustion, eating rules, Diet history, nutrients, and quality remain owner-specific.

All player-facing primary Food values use the shared ×5 formatter. A mechanical restoration of `6` displays as `30`.

### 25.3 Temperature

```text
ID: totality:temperature
Model: SCALAR
State authority: EXTERNAL_ADAPTER
Adapter: totality:temperature
Polarity: TARGET_RANGE
Display: BAR or STATE_INDICATOR / SURVIVAL_RIGHT
HUD role: SURVIVAL_CONSTANT
Ownership: universal when Temperature is enabled
Owner: Survival/Temperature
```

Temperature may use signed values and a comfort range. Maximum is not interpreted as best. The owner defines scale, bounds, thresholds, simulation, persistence, death behavior, and consequences.

These examples define architecture and ownership. Numerical balance remains owned by the relevant system unless already locked elsewhere.

### 25.4 Mana

```text
ID: totality:mana
Model: SCALAR
Polarity: HIGH_IS_GOOD
Scale: preserve current implementation
Display: BAR / CORE_MANA
Display conversion: 1 / 1
Baseline displayed maximum: 100
Capabilities:
  SPENDABLE
  RESTORABLE
  PASSIVE_REGENERATION
  MAXIMUM_MODIFIERS
  HUD_VISIBLE
  MENU_VISIBLE
Ownership: universal global grant
Owner: Mana/general magic system
Initial value for ordinary new players: full
```

Owner responsibilities:

- Base maximum formula.
- Attribute/class scaling.
- Regeneration rate and delay.
- Spell/ability costs.
- Thirst penalties affecting Mana.
- Death behavior.

Generic API responsibilities:

- Current storage.
- Maximum modifier application.
- Spend/restore.
- Tick scheduling.
- Persistence/sync.
- UI snapshot.

Mana and D&D spell slots remain separate.

### 25.5 Stamina

```text
ID: totality:stamina
Model: SCALAR
Polarity: HIGH_IS_GOOD
Scale: preserve current integral behavior unless fractional costs require change
Display: BAR / CORE_STAMINA
Display conversion: 1 / 1
Baseline displayed maximum: 100
Capabilities:
  SPENDABLE
  RESTORABLE
  DIRECT_DRAIN
  PASSIVE_REGENERATION
  MAXIMUM_MODIFIERS
  CLIENT_PREDICTION
  HUD_VISIBLE
  MENU_VISIBLE
Ownership: universal global grant
Owner: Stamina/RPG system
Initial value for ordinary new players: full
```

Owners:

- Movement drains for sprint, power sprint, super leap, biological flight.
- Combat drains for attacks, block, bow draw, and future actions.
- Stamina module defines regeneration and pause conditions.
- Combat exhaustion system reacts to immediate depletion.
- Rest integration restores fully on valid Long Rest and does nothing generally on Short Rest.
- Temperature/Survival may apply heat penalties through registered operations/modifiers.

Exploit prevention:

- Server validates actual movement state.
- No client-submitted drain amount.
- Regen and drains are batched.
- Landing/flight state logic must never exit the entire online-player processing loop accidentally.
- Creative bypass is explicit server policy, not inferred client-side.

### 25.6 Barbarian Rage

```text
ID: preserve current Rage charge pool ID
Model: SCALAR
Scale: 1
Polarity: HIGH_IS_GOOD
Display: PIPS / SECONDARY_POWER
Capabilities:
  SPENDABLE
  RESTORABLE
  MAXIMUM_MODIFIERS
  HUD_VISIBLE
  MENU_VISIBLE
Owner: BarbarianClass
```

Rules preserved:

- Rage activation spends one charge.
- Short Rest restores exactly one, clamped to max.
- Long Rest restores all.
- No passive regeneration.
- Maximum is Barbarian-owned.
- Rage duration and active Rage state remain Ability/class state, not the charge resource itself.

### 25.7 Monk Ki

```text
ID: totality:ki
Model: SCALAR
Scale: 1
Polarity: HIGH_IS_GOOD
Display: PIPS / SECONDARY_POWER
Owner: MonkClass
```

`MonkClass` owns:

- Grant level.
- Maximum by Monk level/features.
- Ki costs.
- Which features consume Ki.
- Qualifying Short Rest meditation.
- Full Long Rest restoration.
- Any future Ki regeneration or special subclass modification.

Rest core does not contain `if monk`.

### 25.8 Standard spell slots

```text
ID: totality:spell_slots
Model: PARTITIONED_POOL
Tiers: 1..10
Polarity: HIGH_IS_GOOD
Display: SLOTS / SPELLCASTING
Owner: D&D Spell/Class architecture
```

Rules:

- Max per spell level from existing multiclass slot recalculator.
- Current per spell level persistent.
- Player deliberately selects exact cast tier.
- Cantrips consume no slot.
- Failed/no-effect casts consume no slot.
- Long Rest restores eligible slots.
- Arcane Recovery is a Wizard feature, not a generic passive restore.
- Higher-level auto-spend is forbidden for ordinary player casting.

### 25.9 Warlock Pact Magic

```text
ID: totality:pact_magic_slots
Model: PARTITIONED_POOL
Polarity: HIGH_IS_GOOD
Display: SLOTS / SPELLCASTING
Owner: WarlockClass
```

Rules:

- Separate from standard slots.
- Active tier and max count from Warlock progression.
- All Pact slots normally share the same tier.
- Full Short Rest and Long Rest recovery.
- Warlock spells may choose Pact or other eligible slots only according to D&D Spell API rules.
- No generic conversion between standard and Pact slots.

### 25.10 Hit Dice

```text
ID: totality:hit_dice
Model: PARTITIONED_POOL
Partitions: class-authored Hit Die sizes, normally 6, 8, 10, and 12
Polarity: HIGH_IS_GOOD
Display: SLOTS or PIPS / MENU_ONLY or Rest screen
Grant owner: Class progression system
Aggregation: SHARED_RESOURCE
```

Every allocated class level contributes one maximum Hit Die to the partition matching that class's Hit Die size.

Example:

```text
Wizard 5 / Barbarian 3
    d6 partition maximum: 5
    d12 partition maximum: 3
```

The Class system owns:

- Which die size each class uses.
- Contributions from each allocated class level.
- Recalculation after multiclass changes.
- Any class feature that alters Hit Dice.

The Rest/Health integration owns:

- Whether the current Short Rest permits spending Hit Dice.
- Player choice of an available die partition.
- Server-side die roll.
- CON modifier and other healing modifiers.
- Applying healing to authoritative Health.
- Preventing a die spend when the player is already at full HP or the rest/action is invalid.

Generic Resource API owns:

- Current and maximum counts per die-size partition.
- Spending one selected die.
- Persistence, sync, clamping, and restoration.
- Long Rest restoration when invoked by the owning listener.

The adopted Rest rule currently restores all Hit Dice on a valid Long Rest. This document preserves that rule without placing Rest timing or Health healing logic inside the Resource API.

When class allocation changes:

- Recalculate each die-size maximum.
- Clamp removed excess dice.
- Do not automatically fill newly added dice unless the Class/level-up design explicitly requests it.
- Preserve remaining counts in unaffected partitions.

### 25.11 Thirst

```text
ID: totality:thirst
Model: SCALAR
State authority: GENERIC_COMPONENT
Polarity: HIGH_IS_GOOD
Display: BAR / SURVIVAL_RIGHT
Capabilities:
  RESTORABLE
  DIRECT_DRAIN
  PASSIVE_DECAY
  THRESHOLD_EVENTS
  HUD_VISIBLE
  MENU_VISIBLE
Owner: Survival/Thirst system
```

Adopted behavior:

- Slowly drains over time.
- Drains faster from verbal-component spellcasting and repeated verbal casting.
- Drains faster in desert/extreme heat.
- Short and Long Rest cause loss; exact values remain TBD.
- Drinks restore it after Fluid Material/Safety resolution.
- Ocean water may be dehydrating because salinity is composition, not merely contamination.
- Penalties primarily affect spellcasting, Mana, and verbal use.
- Environment input must come from EnvironmentService once available.

The generic API does not define any rate or water safety rule.

### 25.12 Rest Need and Fatigue

Potential resource:

```text
ID: totality:rest_need
Model: SCALAR
Polarity: HIGH_IS_BAD
Owner: Rest/Fatigue system
```

Canonical boundary:

- Stamina is immediate exertion.
- Rest Need is long-term sleep need.
- Fatigue is a player-facing derived condition/tier.
- Sleep Debt may or may not become a second stored value.

Implementation status:

- The generic API must be able to store and sync Rest Need.
- Do not implement final ranges, rates, thresholds, penalties, forced sleep, Short Rest reduction, magical sleep, construct behavior, or Sleep Debt from this document.
- Those choices remain explicitly TBD in the Rest/Fatigue design.
- Do not automatically create four resources/bars.

### 25.13 Solar Charge

```text
ID: totality:solar_charge
Model: SCALAR
Polarity: HIGH_IS_GOOD
Display: BAR / SECONDARY_POWER
Owner: Kryptonian species implementation
```

Species owner defines:

- Sunlight charging.
- Dimension/star differences.
- Kryptonite suppression.
- Ability costs.
- Max progression.
- Depletion consequences.
- Death behavior.
- Optional overflow.

Environment supplies sunlight context. Resource API stores and transacts.

### 25.14 Chakra

```text
ID: totality:chakra
Model: SCALAR
Polarity: HIGH_IS_GOOD
Display: BAR / SECONDARY_POWER
Owner: relevant lineage/species/class discipline
```

This document does not invent Chakra formulas, hand-sign rules, nature transformations, or regeneration.

### 25.15 Sanity

```text
ID: totality:sanity
Model: likely SCALAR, final decision owned by future Sanity design
State authority: GENERIC_COMPONENT unless its dedicated design requires otherwise
HUD role: SURVIVAL_CONSTANT
Display: intended Survival-left HUD area, exact type TBD
```

The API is structurally capable, but no threshold, drain, restoration, dream, disease, or consequence rules are canonical here.

---

## 26. EDGE CASES AND EXPLOIT PREVENTION

Implementation must explicitly cover:

### 26.1 Mutation safety

- Reject negative spend amounts.
- Reject overflow arithmetic.
- Reject model mismatch.
- Clamp restoration/drain correctly.
- Require explicit partial-spend permission.
- Do not mutate unknown/orphaned resources through normal gameplay.
- Do not let callbacks mutate prepared state.
- Do not expose raw mutable maps.

### 26.2 Duplicate and replayed requests

- Use request nonces.
- Cache recent outcomes.
- Never spend twice because a packet was retried.
- Validate player/action ownership.

### 26.3 Simultaneous operations

- Resolve all costs against one revision.
- Atomic resource transaction.
- Deterministic same-tick ordering.
- Recalculate max before affordability when dependencies changed.
- Mark sync after final state only.

### 26.4 Maximum changes

- Removing modifiers cannot leave hidden illegal current.
- Overflow requires explicit support.
- Ratio preservation rounds deterministically.
- Partition maximum decreases clamp each partition independently.
- Restoring a partition never exceeds its effective maximum.

### 26.5 Logout and reconnect

- No free refills.
- No reset of locks intended to persist.
- No bypass of regen delay.
- No duplicate offline progression.
- No loss of temporary modifiers whose source persists.
- No conversion of combat Exhaustion into Rest Need.

### 26.6 Death and respawn

- Apply one resource death policy exactly once.
- Copy before applying death reset.
- Full sync after final state.
- Do not reinitialize/grant twice.
- Preserve orphan state.
- Verify dimension transfer does not trigger death policy.

### 26.7 Rest

- Resource recovery occurs only for a valid event supplied by Rest.
- A non-restorative Long Rest must not fire full-recovery operations.
- Resource API does not reset Short Rest counts.
- Repeated Long Rest recovery exploit prevention belongs to Rest Need/eligibility.
- Rage/slots/Ki/Pact handlers must be idempotent per rest completion ID.

### 26.8 Spells

- Exact selected tier.
- Successful-cast commitment.
- No slot on no effect.
- No auto-upcast.
- No client authority.
- Standard and Pact pools separate.
- Restore algorithms cannot create tiers the player has zero maximum in unless the feature explicitly allows it.

### 26.9 Hidden resources

- No secret-state packet leakage.
- No tooltip enumeration.
- No debug data to ordinary clients.
- Commands respect permission levels.

### 26.10 Configuration and reload

- Structural definition changes rejected without migration.
- Atomic registry swap.
- Player states reconciled once.
- No resource reset merely because presentation JSON reloaded.

### 26.11 Performance

- No per-resource full-player loop.
- No packet per point.
- No maximum recalculation every tick.
- Cache immutable definitions.
- Invalidate derived maxima selectively.
- Bound nonce caches, modifier lists, orphan storage warnings, and event recursion.

---

## 27. IMPLEMENTATION PHASES

### Phase 0 — Code reconnaissance and frozen behavior tests

Before refactoring:

- Inspect current latest source, not historical pasted code.
- Document exact Mana fields/formulas/tick intervals.
- Document exact Stamina fields/formulas/costs/tick intervals.
- Document `PlayerResourceComponent` NBT.
- Document `SpellSlotComponent` NBT and packet format.
- Document `PlayerChargesComponent` NBT and Rage ID.
- Document authoritative Stats, player-level, per-class-level, Skills, and Masteries APIs consumed by current resource formulas.
- Document the existing Health display class, all current ×5 consumers, and exact rounding behavior.
- Document current Food HUD/tooltips before introducing the Food ×5 formatter.
- Document every implemented class's Hit Die size or explicitly record that the class data does not yet contain it.
- Document death/respawn behavior for each.
- Add characterization tests for current behavior.
- Record representative player NBT fixtures.

No gameplay behavior change.

### Phase 1 — Core registry, definitions, state, and service

Implement:

- Registry.
- Scalar and partitioned models.
- Internal state component.
- State-authority routing.
- External adapter registry and external change bridge.
- Snapshots.
- Structured operation results.
- Atomic resource-only transactions.
- Modifier framework.
- Lifecycle policy.
- Events.
- Server-side tests.

Do not migrate production resources yet.

### Phase 2 — Adapters over existing resources

Register definitions for:

- Health through an external adapter.
- Food through an external adapter.
- Mana.
- Stamina.
- Standard spell slots.
- Existing charge pools/Rage.

Create adapters so generic queries can view existing authoritative stores.

This proves model compatibility without dual-writing.

### Phase 3 — Generic synchronization and client presentation

Implement:

- Full snapshot packet.
- Revisioned batched delta.
- Client registry/state manager.
- HUD/menu adapters.
- Optional Stamina prediction.

Keep old packets until every consumer is migrated.

### Phase 4 — Migrate Mana and Stamina

- Import NBT.
- Switch authoritative mutation to service.
- Preserve formulas and costs.
- Consolidate tick scheduling.
- Preserve combat exhaustion behavior.
- Add Stamina Long Rest listener.
- Remove old duplicate state only after validation.

### Phase 5 — Migrate Rage/charge pools

- Import pools by exact ID.
- Preserve current/max and rest behavior.
- Move recharge ownership to class listeners.
- Switch HUD pips.
- Remove dead rest-bus registration method.
- Keep compatibility facade temporarily.

### Phase 6 — Migrate standard spell slots

- Import every tier.
- Preserve multiclass maximum resolver.
- Preserve successful-cast commitment.
- Preserve level 1–10 capacity.
- Switch spell UI and Rest listener.
- Keep adapter facade until all callers move.

### Phase 7 — Implement missing resources

In dependency order:

1. Hit Dice pool and HP/Short-Rest integration, because the audit identifies HP/Hit Dice as the highest-priority missing Rest recovery foundation.
2. Pact Magic through `WarlockClass`.
3. Ki through `MonkClass`.
4. Species resources needed by current content, such as Solar Charge.
5. Thirst and the Temperature external adapter when Survival implementation begins.
6. Rest Need/Fatigue only after its unresolved design choices are locked.
7. Sanity with `SURVIVAL_CONSTANT` HUD role after its dedicated design.

### Phase 8 — Cleanup

- Remove legacy packets.
- Remove duplicate stores.
- Remove deprecated managers after no callers remain.
- Remove dead no-op methods.
- Remove one-release migration fallback after test worlds have been upgraded.
- Update audit/documentation map.

---

## 28. ACCEPTANCE TESTS

All tests are server-authoritative unless explicitly client-side.

### 28.1 Registry

- Register unique scalar definition succeeds.
- Duplicate ID fails.
- Invalid scale fails.
- Partitioned definition with scalar-only capability mismatch fails.
- Missing strategy fails freeze.
- Missing external adapter fails freeze.
- `GENERIC_COMPONENT` definition with an external adapter ID fails.
- Signed Temperature lower bound with a valid target range succeeds.
- Invalid Temperature target range fails.
- Display conversion with a zero or negative denominator fails.
- Health and Food `5/1` conversions validate.
- Safe presentation reload succeeds.
- Structural model change without migration fails.

### 28.2 Resource grants and ownership

- Registering Rage does not instantiate Rage for a non-Barbarian.
- Registering Ki does not instantiate Ki for a non-Monk.
- Registering Solar Charge does not instantiate Solar Charge for a non-Kryptonian.
- A Barbarian grant creates Rage once and does not refill it during repeated reconciliation.
- A Monk grant creates Ki once and does not refill it on login or dimension transfer.
- A Kryptonian Barbarian can possess Rage and Solar Charge simultaneously.
- Removing the final ordinary class/species/discipline grant deletes the source-specific resource state.
- Temporary inactivity while the grant remains preserves the current state.
- Reacquiring a removed resource performs a new acquisition and uses that grant's initialization rather than restoring stale state.
- An explicitly `PRESERVE_DORMANT` story resource retains state only when its design requests it.
- Removing one of several `SHARED_RESOURCE` grants does not remove the pool while another remains.
- Conflicting `SINGLE_OWNER` grants resolve deterministically and produce diagnostics.
- A maximum modifier does not grant an otherwise ungranted resource.
- An equipment grant using `REMOVE_STATE` leaves no persistent usable pool after unequip.
- Ungranted resources are omitted from ordinary client packets and HUD/menu lists.
- A progression catalogue may preview an ungranted resource without creating live state.
- Health and Food are universally exposed through their external owners without creating Generic-component state.
- New-player Mana and Stamina initialize full.
- A quest grant may initialize a resource at an authored fraction or absolute value.
- Reprocessing the same quest/grant does not refill the resource.
- A client cannot choose its authoritative acquisition value.
- One Chakra ownership grant plus Uzumaki and Jinchuriki modifiers produces one enlarged pool, not three pools.
- Reconciliation is idempotent across login, respawn, dimension change, and reload.

### 28.3 Progression boundaries and dependencies

- Player XP, player level, class levels, class points, Skill XP/levels, mastery points/unlocks, attribute scores, and unspent attribute points are absent from resource NBT.
- A resource maximum resolver reads the authoritative effective attribute value rather than rebuilding bonus layers.
- Changing END invalidates Stamina when declared without recalculating unrelated Rage.
- Changing Monk level invalidates Ki and Hit Dice contributions without changing Barbarian Rage.
- A mastery may modify resource cost/rate without becoming a resource state entry.
- Cost preview and commit use the same progression/modifier pipeline.
- A level-up maximum increase does not refill unless the owner explicitly requests it.
- Changing the attribute-point cadence or level cap does not require resource-state migration.
- Resource spending alone does not award Skill XP or player XP.
- Failed actions that spend nothing award no resource-derived progression.
- The shared 30-class-level allocation is read per class; no resolver assumes 30 levels independently in every class.
- XP, class level, Skill, mastery, and attribute HUD elements render from their own snapshots rather than fake resource definitions.

### 28.4 Scalar operations

- Spend exact amount succeeds.
- Spend below zero fails.
- Spend more than current fails without mutation.
- Restore clamps at max.
- Drain clamps at minimum.
- Set requires privileged path.
- Result reports requested/applied/before/after.
- Revision increments once per transaction.

### 28.5 Atomic transactions

- Two-resource cost succeeds together.
- One insufficient resource causes no cost to be spent.
- Duplicate nonce does not spend twice.
- Prepared transaction fails on stale revision.
- Post events fire only after commit.

### 28.6 Maximum modifiers

- Flat/additive/multiplicative ordering is deterministic.
- Modifier removal recalculates.
- `CLAMP_CURRENT` works.
- `PRESERVE_RATIO` rounds consistently.
- `PRESERVE_DEFICIT` works.
- Overflow is rejected without capability.
- Expired modifier is removed once.
- Equipment-derived modifier is not duplicated after relog.
- Skill/mastery cost modifiers apply deterministically.
- Client cost preview and server commit resolve the same cost from the same revision.
- A client-supplied cost amount is ignored.
- Cost cannot become negative or become restoration.
- A cost reaches zero only when explicitly allowed.
- Rate modifiers cannot bypass a regeneration strategy's blocked condition.

### 28.7 Partitioned pools

- Current/max tracked independently per partition.
- Exact-tier spend uses selected tier.
- Empty selected tier fails even when higher tier has slots.
- Explicit eligible-partition policy chooses deterministically.
- Restore clamps per partition.
- Partition maximum reduction clamps correctly.
- Level 10 survives persistence.

### 28.8 Spell slots

- Successful spell consumes exactly one selected slot.
- Precondition failure consumes none.
- Runtime no-effect consumes none.
- Cantrip consumes none.
- Long Rest restores eligible standard slots.
- Standard and Pact pools do not affect each other.
- Existing multiclass table output is unchanged.

### 28.9 Hit Dice

- A single-class character receives one maximum Hit Die per allocated class level in the correct die-size partition.
- A multiclass character keeps independent counts for each die size.
- Wizard 5 / Barbarian 3 resolves to five d6 and three d12 maximum dice.
- Selecting a d10 cannot spend a d8 partition.
- A full-health player cannot accidentally spend a Hit Die.
- An invalid or interrupted Short Rest cannot spend a Hit Die through the completion action.
- A valid spend rolls on the server and heals through the Health authority.
- Long Rest restores all Hit Dice according to the adopted Totality rule.
- Removing class levels clamps only affected partitions.
- Gaining a class level does not automatically fill the new die unless the level-up owner explicitly requests it.
- Save/reload and dimension transfer preserve every partition.
- Hit Dice are synchronized as `d6`, `d8`, `d10`, and `d12`, not unlabeled raw integers.

### 28.10 Rage

- Existing player migrates current charges exactly.
- Activation spends one.
- Insufficient charge fails.
- Short Rest restores one.
- Long Rest restores all.
- Multiple handling of same rest completion does not double-restore.
- Pip HUD matches current/max.

### 28.11 Stamina

- Existing current/max migrates exactly.
- Sprint drains according to current behavior.
- Bow drain behavior unchanged.
- Biological flight drain behavior unchanged.
- Regen pause behavior unchanged.
- Combat exhaustion reaction unchanged.
- Long Rest fills Stamina.
- Short Rest does not generally restore.
- Client prediction correction cannot enable continued sprint at zero.
- One player's landing/flight update cannot skip processing other players.

### 28.12 Pact Magic

- Warlock receives correct active tier/count for tested levels.
- Pact slots are separate.
- Short Rest fills.
- Long Rest fills.
- Non-Warlock has no entitled/visible Pact pool unless temporarily granted.
- Multiclass standard-slot calculation remains unchanged.

### 28.13 Ki

- Monk grant activates Ki.
- Maximum follows Monk resolver.
- Ki feature spends correctly.
- Ordinary Short Rest without qualification does not fill.
- Qualifying meditation fills.
- Long Rest fills.
- Non-Monk hidden/not entitled.

### 28.14 Thirst boundary

- Passive drain applies.
- Heat modifier comes from supplied Environment context.
- Verbal casting emits drain through Survival integration.
- Rest emits loss through Survival integration.
- Drinking restores only after liquid/safety resolution.
- Unsafe/saline result is not invented by Resource API.
- Depletion event is consumed by Survival, not hardcoded.

### 28.15 Externally backed universal resources

- Health, Food, and Temperature use the same snapshot service as internal resources.
- Their values are absent from Generic Resource NBT.
- Health snapshot matches authoritative mechanical current/max.
- Native `20` Health displays as `100`.
- Mechanical damage `4` displays as `20`.
- Health restore delegates to Health; generic drain cannot bypass damage.
- Absorption is not added to base HP.
- Food snapshot matches authoritative mechanical Food level.
- Native `20` Food displays as `100`.
- Mechanical Food restoration `6` displays as `30`.
- Food mutation delegates without duplicating saturation/exhaustion.
- Temperature supports signed state and target-range evaluation.
- Owner-side changes invalidate generic snapshots through the bridge.
- Native and generic synchronization are not double-applied.
- Existing Health display code and the generic formatter do not both apply ×5.
- Mechanical conditions, damage, healing, Food restoration, costs, and percentages remain unchanged.
- Rounding matches across HUD, menus, mob bars, floating text, and tooltips.
- A mixed atomic transaction with a non-transactional adapter fails before mutation.
- Death/respawn/logout do not let Generic Resource overwrite external state.

### 28.16 Constant and contextual HUD presentation

- Health, Mana, Stamina, and Food retain core constant HUD channels.
- Their normal baselines display as `100` without changing native Health/Food mechanics.
- Thirst, Temperature, and Sanity retain survival constant channels when enabled.
- Rest Need/Fatigue receives no final HUD role before its dedicated design.
- Rage, Ki, Chakra, Solar Charge, Reiatsu, and Cursed Energy cannot displace constant channels.
- Ungranted contextual resources never appear merely because registered.
- A Kryptonian Barbarian exposes Solar Charge and Rage as contextual candidates.
- A Monk/Barbarian exposes Ki and Rage.
- Pinning/hiding a contextual resource changes presentation only.
- Constant resources remain structurally eligible when full, comfortable, unavailable, or de-emphasized.
- Temperature rendering does not treat maximum as desirable.
- HUD changes never mutate grants, state, availability, or persistence.

### 28.17 Persistence and lifecycle

- Save/reload preserves current.
- Dimension transfer preserves state.
- Death applies each configured policy once.
- Respawn sends final full snapshot.
- Orphan internally backed resource survives save/load and restores when definition returns.
- External resources persist only through their authoritative owners.
- Generic save/load creates no cached Health, Food, or Temperature entries.
- Migration rerun is idempotent.
- Legacy data is not reimported after consumption.
- No relog refill or regen-delay bypass.

### 28.18 Synchronization

- Join exposes one unified view containing visible internal and external resources.
- Same-tick changes batch into one delta where generic sync is used.
- Native-client-mirrored external resources are not double-applied.
- Revision ordering rejects older client deltas.
- Missing delta triggers full resync path.
- Hidden resources omitted.
- Definition presentation reload updates client without resetting value.
- Dedicated old packet and generic packet never produce double application during transition.

### 28.19 Performance

Test with a representative stress harness:

- 100 simulated online players.
- Mana and Stamina active for all.
- Several secondary resources.
- Frequent Stamina changes.
- No per-resource packet spam.
- No full maximum recalculation every tick.
- Scheduler cost scales with active strategies, not every registered definition.
- Bounded event recursion and nonce memory.

---

## 29. UNRESOLVED CHOICES THAT MUST REMAIN EXPLICIT

The API is implementation-ready, but the following gameplay or final-tuning choices are not silently decided here.

### 29.1 Exact unit scales

Recommended default:

- `1` for current integral resources.
- `1000` for new fractional continuous resources.

Claude Code must preserve existing Mana/Stamina precision during migration. Do not rescale without exact conversion tests.

### 29.2 Existing death behavior

The audit did not canonically lock death reset rules for every existing resource. Preserve current behavior during migration and record it. Any new rule requires Stefan's decision.

### 29.3 Offline progression

Default is none. No current resource is granted offline progression by this document.

### 29.4 Rest Need/Fatigue model and name

The API supports a future universal scalar resource for long-term rest need. `totality:rest_need` is only a provisional placeholder in this document. Its final public name, identifier, range, rates, thresholds, penalties, partial sleep behavior, Sleep Debt relationship, magical sleep rules, construct behavior, display conversion, formatter, and HUD role remain owned by the dedicated Rest Need/Fatigue API design.

### 29.5 Sanity model

Sanity is universal and its HUD role is locked as `SURVIVAL_CONSTANT` when the system is implemented and enabled.

Its exact storage scale, thresholds, drain/restoration rules, events, consequences, and visual display type remain owned by the dedicated Sanity design.

### 29.6 Structural datapack reload

Canonical recommendation: presentation and safe tuning reload; model/scale/partition semantics require restart+migration. This should be retained unless a real authoring workflow proves hot structural reload necessary.

### 29.7 Cross-domain transaction coordinator

The Resource API supplies prepare/commit primitives but does not finalize item/economy rollback architecture. The future shared transaction design owns that.

### 29.8 Multiple secondary HUD resources

The API exposes all. A dedicated HUD selection policy must decide which one is shown in limited HUD space. Do not store "only one active resource" as a gameplay limitation.

### 29.9 Health, Food, and Temperature detailed semantics

Health, Food, and Temperature are first-class externally backed resources in V1. Their owners remain authoritative.

The following remain owned by dedicated systems:

- Whether HP-cost abilities are adopted and how they interact with damage/death.
- Whether absorption receives a separate registered resource/overlay contract.
- Whether saturation or another Food subvalue becomes independently exposed.
- Temperature scale, bounds, comfort ranges, thresholds, persistence, and consequences.

These do not block the external-adapter foundation.

---

## 30. CANONICAL DECISIONS SUMMARY

The following decisions are locked by this document:

1. Use one generic resource framework, not one behavior model.
2. Store internally backed resources in one player component and expose externally backed resources through authoritative adapters.
3. Health, Food, and Temperature are first-class registered resources despite retaining Health/Combat, Food/Hunger, and Survival/Temperature authority.
4. Separate authoritative mechanical units from player-facing display values.
5. Use one registered rational conversion and formatter per resource; no visual consumer hardcodes scaling.
6. Health and Food use ×5 display conversion while native mechanics remain unchanged.
7. Mana and Stamina use identity conversion and normally display a baseline maximum of 100.
8. Mob health bars, floating damage/healing, Food tooltips, Cooking screens, and Diet previews reuse the shared formatters.
9. Use scalar and partitioned-pool models.
10. Use neutral numeric partitions for spell levels and Hit Die sizes.
11. Treat pips as scalar presentation.
12. Use `TARGET_RANGE` polarity for Temperature-like state.
13. Keep standard spell slots separate from Mana and Pact Magic separate from standard slots.
14. Treat Hit Dice as a class-derived partitioned resource while Health remains externally authoritative.
15. Preserve successful-cast-only slot consumption.
16. Route external operations through adapters and reject unsupported operations.
17. Require transactional adapter support before external resources join atomic transactions.
18. Apply deterministic maximum, cost, and rate modifier pipelines.
19. HP, Food, Stamina, Mana, Thirst, Temperature, Rest Need/Fatigue, and Sanity are universal.
20. Health, Mana, Stamina, and Food use constant core HUD channels.
21. Thirst, Temperature, and Sanity use constant survival HUD channels when implemented and enabled.
22. Contextual resources never displace constant HUD channels.
23. Mana is universal and not limited to RPG classes.
24. Progression values remain in Stats, player/class progression, Skills, and Masteries.
25. Progression feeds grants, maximums, costs, rates, and availability through declared dependencies.
26. Resource spending alone never awards XP.
27. Separate registered, entitled, granted, instantiated, active, visible, available, locked, and enabled state.
28. A globally registered definition does not grant source-specific resources to everyone.
29. Use authoritative source-based grants with acquisition, aggregation, visibility, and removal policies.
30. Default to removing source-specific state when final ownership disappears while preserving temporary inactivity.
31. Do not process or display ungranted source-specific resources.
32. Use one shared Chakra pool by default; Uzumaki/Jinchuriki modify it.
33. Keep cooldowns, private uses, Concentration, item charges, Phone battery, currencies, and progression budgets in their owners.
34. Integrate with Entitlement rather than duplicating it.
35. Let Rest emit context while owning systems apply changes.
36. Let Health, Food, Diet, Temperature, Environment, Classes, Species, Skills, Stats, Abilities, and Spells retain their rules.
37. Persist internal state only; external state remains in its owner.
38. Preserve unknown internal resource state.
39. Batch sync and reuse native external mirrors where appropriate.
40. Preserve existing Mana, Stamina, slots, Rage, Health, and Food behavior during migration.
41. Do not merge combat Exhaustion with long-term Rest Need/Fatigue.
42. Implement Health/Food adapters, Hit Dice/HP Rest, Pact Magic, and Ki through their owning systems.
43. Support future Chakra, Reiatsu, Cursed Energy, and similar pools without universe hardcoding.
44. Do not invent unresolved Rest Need/Fatigue, Sanity, Temperature, Survival, attribute-cadence, or class-progression rules.
---

## 31. CLAUDE CODE HANDOFF CHECKLIST

Before coding:

- Read the current repository files named in Phase 0.
- Read the latest audit and post-audit decisions.
- Confirm exact persisted IDs and NBT keys.
- Create characterization tests.
- Make no balance changes in the foundation commit.

Foundation implementation is complete only when:

- Scalar and partitioned resources both work.
- Internal and external authorities route through one service.
- Health, Food, and a test Temperature adapter produce first-class snapshots without duplicate state.
- Health and Food conversions produce `20 -> 100`, and Food delta `6 -> 30`, without altering mechanics.
- Player HP, mob bars, and floating damage/healing share the Health formatter without double scaling.
- Spell-slot and Hit-Dice partition descriptors both work.
- Constant and contextual HUD candidate classification works without duplicating HP, Food, or progression state.
- Progression dependencies invalidate selectively without duplicating progression state.
- Transactions are atomic.
- Modifiers and maximum reconciliation work.
- Persistence, orphan preservation, death policies, and migration versions work.
- Full and delta sync work.
- Legacy adapters can expose current resources without dual authority.
- Tests in Sections 28.1–28.16 pass.

Migration is complete only when:

- Existing worlds preserve Mana, Stamina, Rage charges, and every spell-slot tier.
- Existing gameplay costs and regeneration behave identically unless changed in a separate approved design.
- Rage still restores `+1` on Short Rest and all on Long Rest.
- Failed/no-effect spells still consume no slot.
- No old and new component simultaneously author the same value.
- Player XP/level, class levels/points, Skills/masteries, and Attributes remain in their existing authoritative components.
- Health, Food, and Temperature remain in authoritative owners with no duplicate Generic-component values.
- Dedicated legacy packets and managers are removed only after all callers migrate.

New resource work begins only after the foundation and migration tests pass.

---

**END OF CANONICAL DOCUMENT**
