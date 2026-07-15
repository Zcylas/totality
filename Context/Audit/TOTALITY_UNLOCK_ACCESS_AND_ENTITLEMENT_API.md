# TOTALITY_UNLOCK_ACCESS_AND_ENTITLEMENT_API

**Status:** CANONICAL — implementation-ready V1 design  
**Design date:** 2026-07-13  
**Owner policy confirmation:** 2026-07-13  
**Depends on:** Totality component/persistence/sync framework; the closed Generic Player Resource API  
**Primary consumers:** Classes, species/origins, abilities, D&D spells, phone apps/services, quests/dialogue, Cooking recipe knowledge, equipment, transformations, account/services, research, dimensions, future progression systems

---

## Document authority and source basis

This document finalizes the previously-undesigned **Unlock, Access and Entitlement API** identified as a Phase 2A shared foundation in `TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md`.

It was designed against the latest relevant material, with later decisions treated as authoritative:

- `TOTALITY_POST_AUDIT_DESIGN_DECISIONS.md`
- `TOTALITY_IMPLEMENTATION_AUDIT_REPORT.md`
- `TOTALITY_RPG_CLASS_SPELL_ARCHITECTURE.md`
- `TOTALITY_GENERIC_PLAYER_RESOURCE_API.md` — closed and not redesigned here
- `TOTALITY_SHARED_CROSS_SYSTEM_FOUNDATIONS.txt`
- `TOTALITY_DISEASE_SPELL_INTERVENTION_DESIGN.txt`
- `TOTALITY_FOOD_ECOSYSTEM_CURRENT.txt`
- `TOTALITY_PHONE_PLATFORM.txt`
- `TOTALITY_DIALOGUE_RELATIONSHIP_DESIGN.md`
- `TOTALITY_ECONOMY_BANKING_TRADING.txt`

Older planning notes and the superseded Cooking archive were used only to identify historical intent or migration hazards. They are not implementation authority.

---

# 0. Executive decision summary

The Entitlement API is the shared, server-authoritative layer that answers:

> **What content is this player allowed to know about, see, learn, unlock, select, equip, prepare, install, purchase, enter, activate, or use — and why?**

It does **not** collapse these questions into one `unlocked` boolean.

The canonical model is a **federated decision layer**:

1. The Entitlement API owns common identities, definitions, permanent generic unlock records, grant provenance, suspensions, requirement evaluation, structured decisions, synchronization views, events, migration, and debugging.
2. Domain systems remain authoritative for their richer state:
   - Codex owns discovery level and discovered information.
   - D&D Spell owns known/prepared spell state and casting mechanics.
   - Cooking owns `RecipeKnowledgeComponent` and its `UNKNOWN / GUIDED / LEARNED` semantics.
   - Inventory/equipment owns item possession and equipment state.
   - Economy owns accounts and account tiers.
   - Phone owns the physical phone's model, tier, setup, installed apps, and layout.
   - Quests own quest and stage progress.
   - Classes/species/origins/transformations own which source-based grants should currently exist.
   - Generic Player Resource API owns resource state after a resource is granted.
3. The Entitlement API reads those systems through registered contributors and evaluates one consistent access decision without duplicating their data.
4. Independently permanent earned facts are stored. Class-, subclass-, covenant-, species-, origin-, item-, effect-, and transformation-bound content is normally derived or reconciled from its active source and disappears from player-facing entitlement/knowledge state when that source ends. It does not accumulate as dormant hidden progression unless the content explicitly creates an independent permanent acquisition.
5. Visibility is content-authored. Ordinary non-secret progression content may usually be visible-but-locked, while quest, secret, spoiler-sensitive, or hardware-gated content may remain hidden until its reveal rule is met. No global default overrides the owning design's final decision.
6. Recipe learning and phone-app lifecycle policies are domain-specific. The Entitlement API provides the common decision contract without imposing one universal acquisition, visibility, installation, downgrade, or retention rule.
7. A content use request is never trusted because the client displayed it as available. The server evaluates it again at execution time.

The most important canonical distinction is:

> **Codex discovery is information. Entitlement is permission. Discovery never automatically grants permission to use content.**

---

# 0A. Current implementation findings this API must correct

The implementation audit and direct code material establish the following baseline.

## Existing flat ability access

`AbilityComponent` currently stores:

- `Set<Identifier> unlocked`
- cooldowns
- favorites
- one equipped ability

`hasAbility(id)` is a flat membership check. It cannot explain whether access came from a class, species, origin, quest, item, transformation, debug mode, or another source. Removing one source can therefore incorrectly remove access supplied by another source, while source-bound abilities can become permanent accidentally.

## Default spell access is development scaffolding

Every registered spell currently reports `isDefault() == true`, and `AbilityComponent.ensureDefaultAbilitiesUnlocked()` adds every default ability to every player during construction and load.

This may remain temporarily for development, but it is not final design. It must become an explicit non-progression debug grant provider, disabled by default in normal play.

## Known/prepared spell state does not exist

A flat ability-unlocked set is currently the only spell ownership gate. There is no authoritative known-spell or prepared-spell distinction yet. This API provides the shared access contract, but the dedicated D&D Spell implementation must still own those richer states.

## Class grant mechanisms conflict

The code currently has two non-interoperating directions:

- `ClassData.startingAbilities`, `SubclassData.startingAbilities`, and `CovenantData.grantedAbilities`, which are unused.
- `ClassFeatureRegistry`, currently used only by Barbarian, with additional ad hoc grant code.

The later canonical decision says each class implementation class is authoritative. This document finalizes the role of the registry:

> `ClassFeatureRegistry` may remain as the static definition/catalog layer. `BarbarianClass`, `WizardClass`, `WarlockClass`, `MonkClass`, and future class implementations are the authoritative grant providers. Static registries do not directly mutate player entitlement state.

The unused competing starting/granted ability fields are superseded and should be removed after migration adapters are in place.

## Origin grants are currently the only fully-wired ancestry grant path

`OriginData.startingAbilities` is consumed by `SelectAncestryHandler`, which directly writes to `AbilityComponent.unlock`. This must become a source-bound Origin grant so changing an origin removes only that origin's contribution.

## Phone access is fragmented and partly client-only

Phone apps are currently hardcoded in the app-grid screen. The Banking App uses `bank_app_unlocked`, while several other apps are placeholders. Locked app opening is not consistently revalidated by the server.

Phone app access must move to entitlement decisions. Phone hardware state remains on the phone; permanent app unlocks do not live as arbitrary narrative flags or as the sole truth on one item stack.

## Quest and narrative state already exist, but access is mixed into them

`QuestProgressComponent` and `NarrativeFlagsComponent` already persist and synchronize quest/progression facts. They are valid requirement and grant sources. They must not become a permanent dumping ground for unrelated access booleans. `bank_app_unlocked` is the confirmed example of a pure access flag that should migrate, while flags such as `chef_first_loaf_complete` remain legitimate narrative/history facts.

## Codex and the general requirement tree are designed but unimplemented

The canonical Codex design already separates discovery from permission and defines server-authoritative discovery progress, but the audit found no current Codex/Discovery code footprint. The earlier generic `UnlockRequirement` `AllOf / AnyOf / Condition` tree is also unimplemented. This API finalizes and extends it.

## Cooking supplies an important precedent

Cooking's current design stores only permanent `LEARNED` recipes. `GUIDED` is derived from an active guide quest and disappears automatically when that quest ends. Cooking and recipe knowledge are not currently implemented in the audited code, so this is an integration contract rather than a migration of live recipe data. This pattern becomes the general rule:

> Store durable facts; derive or reconcile temporary/source-bound access.

## Schematics, techniques, licenses, research, and dimensional access have no shared current authority

No audited general system currently owns these future access categories. They therefore use the generic typed entitlement contracts until a richer dedicated system exists. When one does, it becomes the state authority and contributes its state rather than duplicating it.

---

# 1. Scope and terminology

## 1.1 What this API owns

The Entitlement API owns:

- Typed entitlement identities.
- Entitlement type registration.
- Optional entitlement definitions and presentation metadata.
- Permanent generic `KNOWN` and `UNLOCKED` facts when no richer domain component owns them.
- Persistent leased grants that do not already have another authoritative source store.
- Runtime grant indexing and provenance.
- Explicit suspension and administrative revocation overlays.
- Requirement expressions and condition evaluation.
- Operation-specific access queries.
- Structured access decisions and denial explanations.
- Server-authoritative mutation services.
- Display-safe synchronization views and revisions.
- Entitlement events and cache invalidation hooks.
- Legacy migration and orphan handling.
- Debug grants that are isolated from progression.

## 1.2 What this API does not own

It does not own:

- Codex discovery contents, discovery levels, records, or sections.
- Quest state or narrative history.
- Class levels, species/origin selection, relationship values, or account balances.
- Spell slots, Rage, Ki, Stamina, Mana, Solar Charge, or any other resource values.
- Spell preparation limits, spellbook contents, or spell casting execution.
- Ability effects, cooldown mechanics, targeting, animation, or resource spending.
- Item inventories, item durability, equipment slots, or attunement mechanics.
- Phone item model/tier/setup/layout data.
- Recipe execution, Cooking rolls, or station validation.
- Research progress calculations.
- Dimension teleportation or portal mechanics.
- Runtime effect resolution after access has been approved.

It may **query** those systems and include their state in a decision. It may not create a second competing source of truth.

## 1.3 Canonical terms

### Discovered

The player has learned information **about** the content through Codex/Discovery or another designated knowledge-of-existence system.

Discovery may have levels such as `UNKNOWN`, `RUMORED`, `OBSERVED`, `STUDIED`, and `MASTERED`.

Discovery does not imply that the player may use, craft, equip, learn, or enter the content.

### Visible

The content may be shown to the player in the current interface and context.

Visibility is derived. It can depend on discovery, quest state, class, story secrecy, UI policy, or an explicit reveal grant.

Visible content may still be locked.

### Known

The player has learned the spell, recipe, technique, schematic instructions, research result, or other actionable knowledge.

`KNOWN` is not universally stored by the Entitlement component:

- D&D Spell owns known spells, including whether knowledge is independent or scoped to a currently active class/source.
- Cooking and other recipe-producing APIs own their learned-recipe semantics.
- A future Research API may own completed research knowledge.
- Generic techniques or schematics without a richer owner may use the Entitlement component's permanent `KNOWN` fact.

Source-bound knowledge is not retained merely for history. When its only owning source is removed, it disappears from normal player-facing knowledge and menus. Only an explicitly authored independent permanent acquisition survives source loss.

### Owned

The player currently owns or possesses the required source, item, account, license, book, schematic object, phone, subscription, or other ownership evidence.

Ownership is normally derived from the owning system. The Entitlement component does not mirror the inventory, account system, or phone item.

### Unlocked

The player has permanently earned an authorization independent of the continued presence of the teaching or reward source.

Examples:

- A quest permanently unlocks the Bank app.
- A consumed technique manual permanently unlocks a technique.
- A story milestone permanently unlocks dimensional travel.

An unlock remains earned when a temporary use requirement becomes false. It may become unavailable, but it is not silently erased.

### Granted

An active source currently contributes access.

Examples:

- Barbarian class grants Rage-related features.
- A species grants an innate ability.
- An equipped ring grants flight.
- A transformation grants temporary abilities.
- A story effect temporarily enables or disables a service.

A grant is source-bound unless explicitly converted into a permanent fact.

### Available

Current conditions allow the requested operation now.

Availability is always derived and operation-specific. A spell may be available to view but not prepare, or prepared but not cast because of a missing slot, anti-magic condition, component, or target.

### Equipped, prepared, installed, selected, or attuned

The player or owning system has selected content for active use.

These are not synonyms for entitlement. Selection state belongs to the owning system and is read by the Entitlement API through an adapter.

### Temporarily accessible

The requested operation is authorized only because at least one active temporary or source-bound path currently exists, and no durable path independently authorizes it.

Losing that source removes that path. Other sources may still keep the content accessible.

### Suspended

The underlying knowledge or unlock still exists, but specified operations are temporarily blocked.

Examples:

- A story state disables banking.
- An anti-magic zone suspends spell use.
- A broken phone prevents app opening.

Suspension does not delete progression.

### Revoked

A durable fact or grant has been deliberately removed by an authoritative operation.

Revocation is not the normal consequence of a temporary requirement becoming false. Permanent revocation must be explicit, auditable, and uncommon.

## 1.4 One authority per axis

A query may combine several axes, but each axis has one authority.

Example — Rage:

- Entitlement API: the player is authorized to possess/use the Rage feature.
- Generic Player Resource API: current/max Rage state.
- `BarbarianClass`: how Rage is granted, generated, consumed, restored, and scaled.
- Ability/Combat: activation and combat effects.
- HUD: rendering.

No layer duplicates another layer's truth.

---

# 2. Entitlement identities and types

## 2.1 Typed identity

Every entitlement-controlled subject uses a typed key:

```java
public record EntitlementKey(
        Identifier typeId,
        Identifier contentId
) {}
```

Examples:

```text
(type = totality:ability,          content = totality:flight)
(type = totality:spell,            content = dnd:fireball)
(type = totality:class_feature,    content = totality:rage)
(type = totality:phone_app,        content = totality:bank)
(type = totality:recipe,           content = totality:bread)
(type = totality:service,          content = totality:premium_delivery)
(type = totality:dimension_access, content = totality:nether_gateway)
```

The type is not a Java enum. New systems register new namespaced type identifiers without editing the core API.

## 2.2 Entitlement type registry

```java
public record EntitlementTypeDefinition(
        Identifier id,
        Identifier defaultAuthorizationPolicyId,
        Set<Identifier> supportedActionIds,
        Identifier ownerSystemId,
        boolean supportsPermanentKnown,
        boolean supportsPermanentUnlock
) {}
```

`EntitlementTypeRegistry` is code-registered. It defines semantics and integration, not every content entry.

Initial type IDs should include:

- `totality:ability`
- `totality:spell`
- `totality:class_feature`
- `totality:species_feature`
- `totality:phone_app`
- `totality:recipe`
- `totality:schematic`
- `totality:technique`
- `totality:research_node`
- `totality:service`
- `totality:account_feature`
- `totality:dimension_access`
- `totality:dialogue_option`
- `totality:quest_access`
- `totality:equipment_permission`

This list is registration data, not a closed enum.

## 2.3 Content-owned entitlement keys

An existing content registry should expose its entitlement key rather than forcing duplicate definitions.

Recommended capability:

```java
public interface EntitlementControlledContent {
    EntitlementKey entitlementKey();
}
```

Examples:

- `Ability` exposes an ability key.
- `Spell` exposes a spell key.
- `PhoneAppDefinition` exposes a phone-app key.
- `CookingRecipeDefinition` exposes a recipe key.

A content registry and the Entitlement catalog may be linked by an adapter. There must not be two independently editable IDs for the same content.

## 2.4 Optional entitlement definition

Simple content can use type defaults. Content needing explicit visibility, requirements, messages, or retention uses an immutable definition:

```java
public record EntitlementDefinition(
        EntitlementKey key,
        String nameTranslationKey,
        String descriptionTranslationKey,
        Identifier iconId,
        Identifier categoryId,
        Set<Identifier> tags,
        Identifier authorizationPolicyId,
        EntitlementRuleSet rules,
        EntitlementPresentation presentation,
        EntitlementRetentionPolicy retentionPolicy
) {}
```

Data-driven definitions use codecs and data-safe identifiers/translation keys. They contain no raw `Item`, `Block`, screen instance, class object, or callback.

Canonical retention modes are intentionally small:

```java
public enum EntitlementRetentionPolicy {
    SOURCE_BOUND,
    EXPLICIT_PERMANENT_ACQUISITION,
    DOMAIN_OWNED
}
```

- `SOURCE_BOUND`: ordinary access/knowledge exists only while a validated source exists. Removing the source removes the player-facing state.
- `EXPLICIT_PERMANENT_ACQUISITION`: a validated transaction may create an independent permanent fact; ordinary grants still do not become permanent automatically.
- `DOMAIN_OWNED`: the richer owning API decides retention and contributes the effective state. Spell knowledge and recipe knowledge normally use this mode.

## 2.5 Tags

Tags group content for queries and UI without deciding access by themselves.

Examples:

- `totality:spells/wizard`
- `totality:abilities/movement`
- `totality:phone_apps/banking`
- `totality:services/dimensional`
- `totality:recipes/baking`

Tags are not provenance. A Wizard tag does not grant a spell; `WizardClass` supplies the grant/eligibility contribution.

## 2.6 Action identifiers

Queries are operation-specific and use namespaced identifiers rather than a giant enum.

Core actions:

- `totality:view`
- `totality:learn`
- `totality:unlock`
- `totality:select`
- `totality:prepare`
- `totality:equip`
- `totality:install`
- `totality:purchase`
- `totality:activate`
- `totality:use`
- `totality:enter`
- `totality:open_service`
- `totality:start_quest`

Future systems may register additional actions.

## 2.7 Authorization policies

Different content types do not share one automatic formula. Definitions reference a registered policy:

```java
public interface EntitlementAuthorizationPolicy {
    Identifier id();

    AuthorizationEvaluation evaluate(
            EntitlementQueryContext context,
            EntitlementSnapshot snapshot,
            Identifier actionId
    );
}
```

Initial policies:

- `totality:unlock_or_grant`
- `totality:known_or_grant`
- `totality:owned_or_grant`
- `totality:requirements_only`
- `totality:dnd_spell_access`
- `totality:phone_app_access`
- `totality:custom`

Policies are code-owned because they define security and semantics. A datapack may select a registered safe policy but may not inject arbitrary code.

---

# 3. Player entitlement state

## 3.1 Persistent component

```java
public final class PlayerEntitlementComponent
        implements SyncedComponent, CopyableComponent<PlayerEntitlementComponent> {

    private int dataVersion;
    private long revision;

    private final Map<EntitlementKey, PermanentEntitlementRecord> permanent;
    private final Map<UUID, PersistentGrantRecord> persistentGrants;
    private final Map<EntitlementKey, List<EntitlementSuspension>> suspensions;
    private final Map<EntitlementKey, OrphanedEntitlementRecord> orphaned;
}
```

The raw component is server authority. It should not be blindly synchronized in full if doing so would reveal hidden content.

Use `RespawnStrategy.ALWAYS_COPY` for durable facts and persistent grants. Grant lifetime policy may still cause individual grants to end on death.

## 3.2 Permanent facts

Only stable cross-domain facts belong here:

```java
public enum PermanentEntitlementFact {
    KNOWN,
    UNLOCKED
}
```

This small enum represents stable semantic axes, not content categories. It is intentionally not an extensible list of every gameplay system.

```java
public record PermanentEntitlementRecord(
        EnumMap<PermanentEntitlementFact, PermanentFactRecord> facts
) {}

public record PermanentFactRecord(
        long acquiredAt,
        GrantSourceRef primarySource,
        Identifier acquisitionMethodId,
        Set<AcquisitionEvidence> additionalEvidence,
        boolean progressionEligible
) {}
```

When a richer domain component owns the fact, the Entitlement component does not duplicate it. A contributor exposes the domain fact to queries.

## 3.3 Runtime grant index

The active grant index is a server runtime projection:

```java
public final class RuntimeEntitlementGrantIndex {
    Map<EntitlementKey, Map<UUID, EntitlementGrant>> grantsByEntitlement;
    Map<GrantSourceRef, Set<UUID>> grantsBySource;
}
```

It combines:

- Reconciled grants from class/species/origin/equipment/effect/transformation providers.
- Persistent leased grants loaded from the player component.
- Session/debug/script grants.

The index is rebuilt or reconciled on login and relevant source changes. It is not the only source of truth for the source itself.

## 3.4 Grant record

```java
public record EntitlementGrant(
        UUID grantId,
        EntitlementKey entitlement,
        GrantSourceRef source,
        Set<Identifier> authorizedActionIds,
        Set<TemporaryFactContribution> factContributions,
        GrantLifetime lifetime,
        Optional<GrantExpiry> expiry,
        int priority,
        boolean revealContent,
        boolean progressionEligible,
        Set<Identifier> tags
) {}
```

```java
public record GrantSourceRef(
        Identifier sourceTypeId,
        Identifier sourceId,
        Optional<UUID> sourceInstanceId
) {}
```

Examples:

```text
sourceType = totality:class,          sourceId = totality:barbarian
sourceType = totality:origin,         sourceId = totality:viltrumite
sourceType = totality:equipment,      sourceId = totality:ring_of_flight, instanceId = item UUID
sourceType = totality:transformation, sourceId = totality:kryptonian_empowered_form
sourceType = totality:quest,          sourceId = totality:mobile_banking
sourceType = totality:debug,          sourceId = totality:universal_spell_access
```

Source IDs are descriptive provenance, not a substitute for source validation.

## 3.5 Grant lifetime

```java
public enum GrantLifetime {
    WHILE_SOURCE_ACTIVE,
    SESSION,
    UNTIL_DEATH,
    PERSISTENT_LEASE,
    UNTIL_EXPLICITLY_REMOVED
}
```

```java
public record GrantExpiry(
        ExpiryClock clock,
        long expiresAt
) {}

public enum ExpiryClock {
    ONLINE_TICKS,
    SERVER_GAME_TIME,
    REAL_TIME_UTC
}
```

Most equipment, effect, transformation, class, species, and active-quest grants use `WHILE_SOURCE_ACTIVE` and are derived/reconciled rather than serialized.

## 3.6 Temporary fact contributions

A grant may temporarily contribute a semantic fact without making it permanent:

```java
public enum TemporaryFactContribution {
    KNOWN
}
```

Use this sparingly. A borrowed manual may temporarily make instructions known. Ordinary class or equipment access should authorize actions rather than pretending the content is permanently unlocked.

## 3.7 Suspensions

```java
public record EntitlementSuspension(
        UUID suspensionId,
        EntitlementKey entitlement,
        GrantSourceRef source,
        Set<Identifier> blockedActionIds,
        Identifier reasonCode,
        Optional<GrantExpiry> expiry,
        int priority,
        boolean hideInsteadOfDisable
) {}
```

Suspensions block operations without deleting `KNOWN` or `UNLOCKED` facts.

A story-state service shutdown should be a suspension or failed availability requirement, not a destructive revocation.

## 3.8 Grant priority

Priority does **not** mean a numerically higher grant deletes or overrides lower grant sources.

Canonical rules:

- Access is normally authorized if any valid path authorizes the requested action.
- All source records remain independently removable.
- Priority selects a primary UI explanation or resolves policy-specific alternatives.
- Suspensions are evaluated separately according to their blocked actions.
- A source cannot revoke another source's grant by claiming higher priority.

## 3.9 Stackability

Entitlement grants do not stack power by default.

Two flight sources mean redundant authorization paths, not double flight speed. Two spell-access sources do not double spell slots. Two Rage grants do not double Rage maximum.

If ranks, potency, maximums, charges, or modifiers stack, the owning system handles them. It may model ranks as separate entitlement keys or query all provenance paths, but the Entitlement core only determines authorization.

## 3.10 Multiple sources

If class, item, and transformation all grant the same ability:

- Three source records exist.
- Removing the item removes only the equipment grant.
- Access remains while at least one other valid source authorizes the operation.
- UI may display “Available from Barbarian and Empowered Form”.

## 3.11 Temporary-to-permanent conversion

Conversion is an explicit transaction:

```java
EntitlementMutationResult convertGrantToPermanent(
        ServerPlayer player,
        UUID grantId,
        PermanentEntitlementFact fact,
        Identifier acquisitionMethodId
);
```

The service validates the source, writes the permanent fact, fires the permanent event, and then optionally removes the temporary grant.

A grant never becomes permanent merely because it remained active for a long time.

## 3.12 Character creation

Character creation performs one server transaction after the final choices are confirmed:

1. Persist class/species/origin selections in their owning components.
2. Reconcile their grant providers.
3. Apply any explicitly permanent character-creation facts.
4. Recalculate dependent resources and features.
5. Sync one coherent result.

Preview screens may evaluate hypothetical entitlement contexts without mutating player state.

## 3.13 Lifecycle behavior

### Death and respawn

- Permanent facts persist.
- Persistent leases persist unless `UNTIL_DEATH`.
- Equipment/effect/form grants are reconciled from the post-respawn state.
- Active abilities whose only authorization path ended are cancelled by their owning system.

### Logout and reconnect

- Permanent facts persist.
- `SESSION` grants end.
- `ONLINE_TICKS` pauses unless the source defines otherwise.
- `REAL_TIME_UTC` may expire while offline.
- Derived grants are rebuilt from current source state.

### Dimension change

- Durable state remains.
- Dimension/environment requirements are invalidated and re-evaluated.
- Dimension-scoped grants are reconciled.

### Class, subclass, covenant, species, or origin change

- Remove all grants and source-scoped knowledge owned by the old source reference.
- Reconcile the new source.
- Do not retain dormant class/ancestry content merely because the player previously tried that class, subclass, covenant, species, or origin.
- Only independently permanent facts created by an explicit authored acquisition survive.
- If the new source grants the same entitlement key, access may appear uninterrupted, but provenance changes from the old source to the new one. For example, changing from an Elf to a Dwarf removes the Elf Darkvision grant and the Dwarf provider independently grants the same Darkvision key.

---

# 4. Grant sources and provenance

## 4.1 Source type registry

Grant source types are namespaced registrations, not an enum.

Initial source types:

- `totality:class`
- `totality:subclass`
- `totality:covenant`
- `totality:patron`
- `totality:species`
- `totality:origin`
- `totality:quest`
- `totality:dialogue`
- `totality:advancement`
- `totality:item`
- `totality:equipment`
- `totality:status_effect`
- `totality:transformation`
- `totality:relationship`
- `totality:phone_hardware`
- `totality:phone_software`
- `totality:account_tier`
- `totality:recipe_book`
- `totality:research`
- `totality:admin`
- `totality:debug`
- `totality:scripted_event`
- `totality:legacy_migration`

## 4.2 Grant providers

```java
public interface EntitlementGrantProvider {
    Identifier providerId();

    void collectGrants(
            ServerPlayer player,
            EntitlementGrantCollector collector
    );
}
```

Providers must be deterministic for a given authoritative source state. Collection must not mutate the source system.

Examples:

- `BarbarianClass` contributes active class and level-threshold grants.
- `PlayerAncestryComponent` adapter contributes species/origin grants.
- Equipment provider scans validated equipped slots and item instances.
- Transformation provider contributes grants from active forms.
- Quest provider contributes stage-bound temporary grants.

## 4.3 Reconciliation

```java
public interface EntitlementReconciler {
    ReconciliationResult reconcileProvider(
            ServerPlayer player,
            Identifier providerId,
            ReconcileReason reason
    );
}
```

Reconciliation computes a provider's desired grants and diffs them against that provider's current grant set.

It must be:

- Idempotent.
- Source-scoped.
- Safe to run on login, respawn, reload, and migration.
- Unable to remove grants belonging to another provider.

## 4.4 Permanent learning survives the teacher

If an NPC, consumed book, quest reward, or research completion permanently teaches content, the teaching transaction writes `KNOWN` or `UNLOCKED`.

Losing access to the teacher, book, faction, or quest giver does not erase what was permanently learned.

If access is intended to last only while possessing a source, use a source-bound grant instead of permanent learning.

## 4.5 UI provenance

Every structured decision may expose display-safe provenance:

```java
public record EntitlementSourceSummary(
        Identifier sourceTypeId,
        Identifier sourceId,
        Optional<String> displayTranslationKey,
        boolean temporary,
        boolean progressionEligible
) {}
```

Examples:

- “Granted by Origin: Viltrumite”
- “Requires Wizard level 5”
- “Temporarily available while Ring of Flight is equipped”
- “Suspended by story state”
- “Debug access — progression disabled”

Hidden story sources may provide a redacted summary such as “Unavailable due to current circumstances”.

## 4.6 Debug provenance

Debug grants must use `sourceTypeId = totality:debug`, `progressionEligible = false`, and a visible debug marker for operators.

Debug access must not:

- Award discovery or progression milestones.
- Permanently learn/unlock content.
- Satisfy achievements that require legitimate acquisition.
- Be serialized into normal progression exports.
- Remain enabled by default in production.

The current universal spell access becomes a debug grant provider controlled by an explicit server development setting. It no longer writes every spell into permanent player state.

---

# 5. Requirement system

## 5.1 Finalized requirement expression

The earlier generic `UnlockRequirement` concept is superseded by the finalized general type:

```java
public sealed interface EntitlementRequirement
        permits AllOf, AnyOf, Not, Condition {

    record AllOf(List<EntitlementRequirement> children)
            implements EntitlementRequirement {}

    record AnyOf(List<EntitlementRequirement> children)
            implements EntitlementRequirement {}

    record Not(EntitlementRequirement child)
            implements EntitlementRequirement {}

    record Condition(Identifier conditionId)
            implements EntitlementRequirement {}
}
```

This preserves the prior `AllOf / AnyOf / Condition` design and adds required `NOT` support.

Validation rules:

- Definitions are acyclic.
- Empty `AllOf` is true.
- Empty `AnyOf` is invalid unless explicitly allowed as an always-false sentinel.
- `Not` requires an authored failure message to avoid confusing inverse explanations.
- Missing condition IDs fail closed on the server.

## 5.2 Condition definitions and evaluator strategies

A tree leaf references a registered condition definition:

```java
public interface EntitlementConditionType<C> {
    Identifier id();
    MapCodec<C> codec();

    RequirementEvaluation evaluate(
            ServerPlayer player,
            C configuration,
            EntitlementQueryContext context
    );
}

public record EntitlementConditionDefinition<C>(
        Identifier id,
        Identifier conditionTypeId,
        C configuration,
        String failureTranslationKey,
        DisclosurePolicy disclosurePolicy,
        Set<EntitlementDependencyKey> dependencies
) {}
```

The registered condition type owns the codec for its configuration. The datapack definition is decoded into a typed immutable configuration; runtime evaluation never receives an untyped `Object` or arbitrary executable script. The registry may store decoded definitions as `EntitlementConditionDefinition<?>` and dispatch through the matching registered type.

Examples of evaluator IDs:

- `totality:player_level`
- `totality:class_level`
- `totality:has_class`
- `totality:has_subclass`
- `totality:species`
- `totality:origin`
- `totality:attribute_at_least`
- `totality:skill_level`
- `totality:has_mastery`
- `totality:quest_state`
- `totality:relationship_at_least`
- `totality:owns_item`
- `totality:item_equipped`
- `totality:resource_at_least`
- `totality:phone_model`
- `totality:phone_tier_at_least`
- `totality:account_tier_at_least`
- `totality:discovery_level_at_least`
- `totality:known_entitlement`
- `totality:dimension`
- `totality:time_window`
- `totality:environment_tag`
- `totality:has_status_effect`
- `totality:cooldown_ready`
- `totality:choice_not_taken`

## 5.3 Requirement ownership categories

```java
public record EntitlementRuleSet(
        Optional<EntitlementRequirement> visibility,
        Optional<EntitlementRequirement> acquisition,
        Optional<EntitlementRequirement> persistentEligibility,
        Map<Identifier, EntitlementRequirement> actionRequirements
) {}
```

### Visibility requirements

Decide whether the player may see the entry or its full details.

A failed visibility requirement returns `HIDDEN`, not merely disabled.

Visibility is authored per content or domain. A recommended ordinary progression presentation is visible-but-locked, but this is not mandatory. Quest-only, secret, spoiler-sensitive, narrative, and hardware-tier content may remain hidden until a reveal condition is satisfied. The owning design has final authority for each entry.

### Acquisition requirements

Checked when the player attempts to learn or permanently unlock content.

Once a permanent acquisition commits, acquisition requirements are not continuously enforced unless the definition explicitly uses persistent eligibility too.

### Persistent eligibility requirements

Checked whenever access is queried. If they later fail, the content remains known/unlocked but becomes unavailable or suspended.

Example: a permanently unlocked dimensional service remains earned after the player equips a phone that is too weak to open it; the unlock remains, while current availability fails. Source-bound class or ancestry content does not use this mechanism merely to preserve dormant history.

### Action requirements

Checked for specific operations such as prepare, equip, purchase, activate, use, enter, or open service.

## 5.4 Unlock-time versus use-time

Canonical rule:

> Failing a temporary use-time requirement never silently deletes a permanent unlock.

Examples:

- Insufficient Mana denies activation; it does not forget the spell.
- Removing a phone blocks opening an app; it does not erase the app unlock.
- Dropping below a temporary skill buff may prevent using a schematic; it does not remove the schematic knowledge.
- Leaving the required dimension may disable an ability; it does not revoke progression.

## 5.5 Resources and cooldowns

The requirement engine may read resource availability and cooldown readiness for preflight/UI decisions.

It does not spend resources or commit cooldowns. The owning action service revalidates and performs the atomic mutation.

Example cast flow:

1. Entitlement query says the spell is accessible and currently appears castable.
2. Spell service revalidates entitlement, target, components, restrictions, and slot/resource availability.
3. Spell executes.
4. Slot/resource is consumed only after the spell's established success commitment point.

## 5.6 Requirement evaluation result

```java
public record RequirementEvaluation(
        boolean satisfied,
        Identifier reasonCode,
        Optional<String> messageTranslationKey,
        List<RequirementEvaluation> children,
        Set<EntitlementDependencyKey> dependenciesRead,
        DisclosurePolicy disclosurePolicy
) {}
```

`AllOf` returns all displayable failures. `AnyOf` returns alternative paths. Hidden conditions may be redacted.

## 5.7 Dependency invalidation

Requirement evaluators declare dependencies so the system does not reevaluate every entitlement every tick.

Examples:

- `class:totality:wizard`
- `class_level:totality:wizard`
- `skill:totality:cooking`
- `quest:totality:mobile_banking`
- `equipment_slot:totality:ring`
- `resource:totality:mana`
- `phone:tier`
- `world:dimension`

When a dependency changes, only affected cached/subscribed decisions are invalidated.

---

# 6. Query and result API

## 6.1 Query

```java
public record EntitlementQuery(
        EntitlementKey entitlement,
        Identifier actionId,
        EntitlementQueryPurpose purpose,
        EntitlementQueryContext context
) {}

public enum EntitlementQueryPurpose {
    SERVER_ENFORCEMENT,
    UI_PREVIEW,
    TOOLTIP,
    DIALOGUE_CONDITION,
    DEBUG_EXPLAIN
}
```

Runtime context may include a target, item stack, equipment slot, phone stack, merchant, dimension, block position, or screen/session token. It is code-owned and not serialized into definitions.

## 6.2 Snapshot

```java
public record EntitlementSnapshot(
        EntitlementKey entitlement,
        Optional<Identifier> discoveryLevelId,
        boolean visible,
        boolean known,
        boolean owned,
        boolean permanentlyUnlocked,
        boolean activelyGranted,
        boolean selected,
        boolean available,
        boolean temporarilyAccessible,
        boolean suspended,
        boolean debugOnly,
        List<EntitlementSourceSummary> sources
) {}
```

This is derived. It is never the authoritative stored state.

## 6.3 Decision

```java
public record EntitlementDecision(
        EntitlementDecisionKind decision,
        Identifier primaryReasonCode,
        EntitlementSnapshot snapshot,
        List<RequirementEvaluation> failures,
        List<AuthorizationPath> successfulPaths,
        long playerEntitlementRevision,
        boolean serverAuthoritative
) {
    public boolean allowed() {
        return decision == EntitlementDecisionKind.ALLOWED;
    }
}

public enum EntitlementDecisionKind {
    ALLOWED,
    DENIED,
    HIDDEN
}
```

`allowed()` is only a convenience over a structured result. Core code should retain and propagate the full decision.

## 6.4 Core reason codes

Reason codes are namespaced identifiers so future systems can add their own.

Core codes:

- `totality:allowed`
- `totality:hidden`
- `totality:locked`
- `totality:not_known`
- `totality:known_but_unavailable`
- `totality:missing_requirement`
- `totality:temporarily_suspended`
- `totality:not_prepared`
- `totality:not_equipped`
- `totality:not_installed`
- `totality:missing_item`
- `totality:missing_ownership`
- `totality:missing_resource`
- `totality:wrong_class`
- `totality:wrong_subclass`
- `totality:wrong_species`
- `totality:wrong_origin`
- `totality:expired_grant`
- `totality:cooldown`
- `totality:server_denied`
- `totality:debug_only`
- `totality:unregistered_content`
- `totality:stale_client_state`

## 6.5 Authorization paths

```java
public record AuthorizationPath(
        Identifier pathTypeId,
        Optional<GrantSourceRef> source,
        boolean durable,
        boolean temporary,
        boolean progressionEligible,
        List<RequirementEvaluation> supportingRequirements
) {}
```

A decision may show multiple successful paths. This is essential for safely removing one source while another remains.

## 6.6 Deterministic primary reason

When several failures exist, select a primary reason in this order:

1. Hidden/redacted policy.
2. Explicit suspension or server policy denial.
3. No authorization basis: not known/unlocked/owned/granted.
4. Selection state: not prepared/equipped/installed.
5. Persistent eligibility failure.
6. Missing ownership/item.
7. Missing resource/cooldown/use-time condition.
8. Other requirement failure.

The full failure list remains available when disclosure allows it.

## 6.7 Network and dialogue use

Server response packets should include:

- Request correlation ID.
- Entitlement key and action.
- Decision kind and reason code.
- Current revision.
- Display-safe failure summaries.
- Optional refreshed display snapshot.

Dialogue conditions use the same query service. A dialogue choice being visible client-side does not authorize its server action.

---

# 7. Server authority and synchronization

## 7.1 Server-only mutations

Only server code may:

- Add `KNOWN` or `UNLOCKED` facts.
- Add/remove persistent grants.
- Add/remove suspensions.
- Convert temporary access to permanent facts.
- Reconcile source grants.
- Execute migration.

There is no client packet named “unlock entitlement”. Clients send requests to perform an authored action; the server determines whether that action grants anything.

## 7.2 Server-side checks

The server must revalidate entitlement for:

- Ability activation.
- Spell selection, preparation, and casting.
- Phone app/service opening.
- Dialogue actions and shop opening.
- Quest acceptance when gated.
- Recipe learning and any restricted recipe action.
- Equipment or attunement.
- Dimension entry/teleport.
- Research completion/claim.
- Admin/debug commands.

## 7.3 Client data model

Do not synchronize the full raw grant ledger or hidden entitlement IDs by default.

The client receives display views relevant to:

- Open menus.
- HUD/favorites.
- Currently equipped/prepared content.
- Visible locked content the design intentionally reveals.

```java
public record EntitlementDisplaySnapshot(
        EntitlementKey key,
        Identifier displayStateId,
        Identifier primaryReasonCode,
        boolean selectable,
        boolean temporary,
        List<DisplayRequirementSummary> requirements,
        long revision
) {}
```

## 7.4 Menu subscriptions

An open menu registers a server-side entitlement view subscription by category/type. Relevant dependency changes push deltas to that menu.

Examples:

- Ability screen subscribes to visible abilities.
- Spellbook subscribes to spells relevant to the active class/build.
- Phone grid subscribes to phone apps and services.
- Dialogue session subscribes only for its lifetime.

Closing the menu ends the subscription.

## 7.5 Revisioning and stale clients

Each player has a monotonically increasing entitlement revision.

The client may send the revision it used for an action. A mismatch does not automatically reject a still-valid request; the server reevaluates. If denied because state changed, return `totality:stale_client_state` plus the current reason/snapshot.

## 7.6 Replay and forgery prevention

- Clients cannot specify grant source provenance.
- Acquisition requests use server-created session/action tokens where appropriate.
- Consumed-item learning uses an atomic inventory transaction.
- Quest reward claims are idempotent and keyed by reward instance.
- Network correlation IDs are not authority.
- Duplicate grant IDs are idempotent no-ops when payloads match and rejected when they conflict.
- Debug commands require server permission and create debug-tagged grants.

## 7.7 Open-service tokens

Security-sensitive screens such as banking, trading, remote delivery, or dimensional services should use a short-lived server session token created after entitlement validation.

Every state-changing packet validates:

- Session token.
- Player identity.
- Service entitlement again.
- Current account/phone/story requirements.

A screen remaining open after access is suspended does not preserve authority.

---

# 8. Events and extension points

## 8.1 Mutation events

Fire after successful committed mutation unless explicitly named `Before`.

- `EntitlementKnownEvent`
- `EntitlementUnlockedEvent`
- `EntitlementPermanentFactRevokedEvent`
- `EntitlementGrantAddedEvent`
- `EntitlementGrantRemovedEvent`
- `EntitlementGrantExpiredEvent`
- `EntitlementSuspensionChangedEvent`

Events include player, entitlement key, source, transaction ID, and progression eligibility.

## 8.2 Derived-state events

- `EntitlementAvailabilityChangedEvent`
- `EntitlementVisibilityChangedEvent`
- `EntitlementTemporaryAccessChangedEvent`
- `EntitlementSelectionChangedEvent`
- `EntitlementRequirementDependencyChangedEvent`

These should be emitted for subscribed or actively used entitlements, not by scanning the entire catalog every tick.

## 8.3 Discovery event bridge

Codex remains authoritative. When discovery changes, Codex emits its existing discovery/progression events. An Entitlement bridge invalidates relevant visibility and discovery requirements and may emit `EntitlementDiscoveryViewChangedEvent`.

The Entitlement API does not write Codex progress merely because content was unlocked.

## 8.4 Provider extension points

```java
public interface EntitlementStateContributor {
    void contributeState(
            ServerPlayer player,
            EntitlementKey key,
            EntitlementStateCollector collector
    );
}
```

Contributors expose:

- Discovery level.
- Known state.
- Ownership evidence.
- Prepared/equipped/installed/selected state.
- Domain-specific suspension state.

Additional extension registries:

- `EntitlementTypeRegistry`
- `EntitlementDefinitionRegistry`
- `EntitlementAuthorizationPolicyRegistry`
- `EntitlementConditionTypeRegistry`
- `EntitlementGrantProviderRegistry`
- `EntitlementStateContributorRegistry`
- `EntitlementPresentationResolverRegistry`

## 8.5 Event-loop safety

Grant and fact mutations occur through an `EntitlementTransaction`. Events generated by one transaction carry its ID. Reentrant attempts to repeat the same mutation are idempotent or rejected, preventing quest-event-unlock loops.

---

# 9. Integration boundaries

## 9.1 Generic Player Resource API

Entitlement decides whether a source-specific resource or feature is authorized. Generic Player Resource API owns the live resource state.

Example:

1. `MonkClass` provider grants the Monk/Ki entitlement.
2. Entitlement availability changes.
3. Monk integration asks the Resource API to instantiate/recalculate Ki.
4. Resource API stores current/max Ki.
5. Monk rules spend/restore it.

Entitlement does not store Ki.

## 9.2 Class API

Each class implementation class is authoritative for progression grants.

- Class level changes trigger that class provider reconciliation.
- `ClassFeatureRegistry` is static feature metadata/catalog only.
- The class implementation decides which feature keys and spell-access paths exist at each level.
- Ordinary class/subclass/covenant features and class-scoped learning exist only while the source remains active. Removing or replacing the source removes them from player-facing access and knowledge rather than leaving a dormant backlog.
- Permanent teaching is allowed only when the feature explicitly creates an independent permanent acquisition.

## 9.3 Species and Origin API

Species/origin selections remain in `PlayerAncestryComponent`.

Their provider grants innate abilities/features while that ancestry is active. Direct writes to `AbilityComponent.unlock` are replaced.

Changing ancestry removes every grant from the old species/origin source. Nothing is retained merely because the player once had that ancestry. If the new ancestry grants the same key, such as Darkvision, the new provider grants it independently and access continues through the new provenance.

If an ancestry feature permanently teaches content rather than innately supplying it, that exceptional behavior must be authored explicitly as an independent permanent acquisition.

## 9.4 D&D Spell access and preparation

D&D Spell owns:

- Spell lists and class eligibility semantics.
- Known spells.
- Spellbooks.
- Prepared spells.
- Preparation limits.
- Casting requirements and restrictions.
- Targeting, components, slot use, and successful-cast commitment.

Entitlement supplies:

- Typed spell identity.
- Cross-source eligibility/grant paths.
- Permanent acquisition hooks where appropriate.
- Common requirements and structured query results.
- UI explanations and server access checks.

A calculated spell summary may say discovered, eligible, acquired, known, prepared, and castable, but those axes are not collapsed into one stored state.

Class-, subclass-, patron-, covenant-, or ancestry-scoped spell knowledge must be removable with its source and must not accumulate as hidden dormant knowledge when a player experiments with many builds. A spell survives source loss only when its acquisition rule explicitly creates independent permanent knowledge. Prepared entries whose knowledge/access disappears are immediately unusable and are cleaned up according to the Spell API's selection rules.

## 9.5 Ability system

Ability system retains:

- Cooldowns.
- Favorites.
- Equipped ability selection.
- Activation/effect logic.
- Passive ticking and removal callbacks.

Entitlement becomes the authority for whether the player possesses and may equip/activate an ability.

`AbilityComponent.hasAbility` becomes a deprecated adapter to an Entitlement query. `unlocked` stops being authoritative after migration.

## 9.6 Skills and Masteries

Skills/masteries expose requirement conditions and may provide grants.

Examples:

- Mining 100 + Veinminer mastery grants/equips Veinminer access.
- A schematic requires Smithing 60 to use.

The Entitlement API does not store skill XP or mastery points.

## 9.7 Codex and Discovery

Codex owns information discovery. Entitlement reads it for visibility or requirements.

Canonical non-equivalence:

- Discovered recipe, not learned: visible in Codex/Cookbook but has no learned benefit.
- Learned hidden technique, not discovered: usable through its authored source while Codex presentation may remain hidden.
- Observed dimension gate, not unlocked: player knows it exists but cannot enter.

## 9.8 Quests

Quest system owns active/completed/stage state.

Quests may:

- Satisfy requirements.
- Supply stage-bound grants.
- Permanently award `KNOWN` or `UNLOCKED` facts.
- Apply suspensions during story states.

Narrative flags remain narrative facts. Flags used only as access booleans should be migrated into entitlements.

## 9.9 Dialogue

Dialogue queries entitlement for option visibility and availability. Dialogue actions revalidate server-side.

`OpenShopAction(shopId)` must validate the service/shop entitlement and current relationship/quest/story conditions before creating a trade session.

Relationship tiers remain Relationship API state and are requirement inputs, not entitlement storage.

## 9.10 Phone apps

Phone owns:

- Physical phone stack.
- Model.
- Hardware tier/frame.
- Setup state.
- Installed apps.
- Favorites/layout.

Entitlement owns or aggregates:

- Permanent app unlocks where the app is authored as player progression.
- Quest/account/service grants.
- App visibility and open-service decisions.

Phone behavior is defined per app, not by one universal lifecycle rule. Each app definition or code-owned policy specifies:

- Whether it is visible while locked, hidden until discovered/unlocked, or hidden until compatible hardware exists.
- Whether access is a permanent player unlock, an account/service grant, a quest-stage grant, bundled phone software, or another source.
- Whether installation transfers to a replacement phone, must be repeated, or is automatically bundled.
- Whether an incompatible downgrade keeps a dormant installation, uninstalls it, or hides it.
- Whether a hardware-tier failure is shown as locked/unavailable or removes the app from that phone's UI.

The Bank app is the canonical visible-but-locked example: Banker dialogue permanently unlocks it. Other apps may not appear at all until the player uses a sufficiently high-tier phone. Permanent progression is not stored only on one item stack, but installation and downgrade behavior remain app/Phone policy.

## 9.11 Cooking and recipe knowledge

Cooking's current model remains canonical:

- `LEARNED` is stored in `RecipeKnowledgeComponent.learnedRecipes`.
- `GUIDED` is derived from an active guide quest.
- `UNKNOWN` is the default.

The Cooking contributor exposes effective state to Entitlement queries. Entitlement does not replace this richer component.

Discovery of a recipe does not automatically learn it.

There is no universal Totality recipe-learning rule. Cooking, Carpentry, Architecture, and every future recipe-producing API define their own acquisition, guidance, experimentation, book/manual, retention, and use semantics, then expose the resulting state through an entitlement contributor.

Vanilla recipe-book unlocks are genuine authored recipe unlocks, not mere UI visibility. The vanilla recipe authority remains the source of truth; its adapter exposes the corresponding learned/unlocked state without duplicating it in `PlayerEntitlementComponent`.

## 9.12 Equipment and items

Inventory/equipment owns possession, slot state, durability, and attunement.

Equipment provider grants access only from validated equipped/attuned item instances. Source instance UUID prevents one item grant from being confused with another copy.

When an item breaks, is removed, or becomes unattuned, its source grants disappear.

## 9.13 Economy and account tiers

Economy owns account existence, balance, tier, limits, interest, and transactions.

Entitlement uses account state as requirements and exposes service access.

A Premium account may grant service entitlements, but the account tier remains Economy authority.

## 9.14 Research

Research API owns progress, experiments, prerequisites, and completed nodes.

Completing a node may:

- Permanently learn a schematic/technique.
- Permanently unlock a service.
- Supply access while a research institution/faction membership remains active.

The research definition states which outcome it creates.

## 9.15 Transformations and status effects

Transformation/effect systems provide source-bound grants and suspensions.

They own duration, form state, stat replacement, visuals, and effect mechanics. Entitlement only reports access and emits loss events so active mechanics can stop safely.

---

# 10. Spell and ability verification examples

## 10.1 Wizard learns and prepares a spell

**Fireball states:**

1. Codex may reveal that Fireball exists: discovered/visible.
2. `WizardClass` makes Fireball eligible for the Wizard at the authored level.
3. The player copies Fireball into their spellbook through a Spell API transaction: known.
4. The player prepares Fireball: prepared.
5. During combat, entitlement query for `use` confirms eligibility + known + prepared and no suspension.
6. Spell service validates slot, components, target, concentration restrictions, and effect conditions.
7. Slot is committed only after the cast succeeds according to the existing spell success contract.

Learning is not preparation. Preparation is not current castability.

## 10.2 Warlock gains a patron spell

`WarlockClass` and the selected patron/covenant provider contribute the patron spell path.

The feature definition must explicitly state whether the patron spell:

- Is added to an eligible list only.
- Is automatically known.
- Is temporarily granted while the covenant remains active.

The Entitlement API does not assume one rule for every patron. The chosen authored behavior produces the appropriate grant and/or Spell API knowledge mutation.

Pact Magic slots remain Generic Resource/Warlock ownership.

## 10.3 Species grants an innate ability

Viltrumite Origin provides `totality:viltrumite_physiology` and flight-related access through an Origin source grant.

- It is granted, not permanently unlocked.
- Changing origin removes the Viltrumite source grants and removes Viltrumite-only content from player-facing state.
- If the new ancestry or another item grants the same key, access remains through that independent source.
- Species ability effects remain Ability/Species implementation.

## 10.4 Class feature at a level threshold

When Barbarian reaches the feature's class level:

1. Class level dependency changes.
2. `BarbarianClass` provider reconciles.
3. The feature grant is added with source `class:barbarian`.
4. Entitlement availability event fires.
5. If it creates a resource, the class/resource integration recalculates it.

Dropping or replacing the class removes the class grant but does not touch unrelated sources.

## 10.5 Temporary transformation grants flight

An active transformation contributes a `use/activate/equip` authorization path for flight.

When the form ends:

- Transformation grant is removed.
- Entitlement emits temporary-access loss.
- Flight owner safely ends active flight.
- Exact fall-protection/grace behavior belongs to Movement/Transformation design.

## 10.6 Equipped item grants an ability

A ring's equipment provider creates a grant using the exact item instance UUID.

- In inventory: owned but not granted if the definition requires equipped state.
- Equipped and valid: granted and available.
- Broken/removed/unattuned: only that instance's grant disappears.

## 10.7 Banker dialogue permanently unlocks the Bank app

The authored Banker dialogue executes an idempotent server transaction:

1. Validate the dialogue session, NPC, option, and any story prerequisites.
2. Add `UNLOCKED` for `(phone_app, bank)` with Dialogue provenance identifying the Banker dialogue/action.
3. Fire the unlock event.
4. Remove/stop using the old pure access flag after migration.
5. Phone app opening still requires the app's authored phone conditions and an active bank account.

A different app may be permanently unlocked by a quest using the same transaction pattern with Quest provenance.

## 10.8 Recipe discovered but not learned

Bread is observed or revealed through Codex:

- Discovered: yes.
- Visible: yes.
- Learned: no.
- Cooking effective state: `UNKNOWN`, unless an active guide quest supplies `GUIDED`.
- Attempt behavior and penalty are owned by Cooking.

## 10.9 Schematic owned but skill-gated

The player possesses a Smithing schematic item:

- Owned: yes.
- Known: only if possession itself teaches or grants knowledge according to the definition.
- Use action: denied until Smithing skill requirement is met.
- If the schematic is consumed to learn it, consumption + permanent `KNOWN` write is atomic.

## 10.10 Debug grant

Development universal spell access provides a grant source:

```text
source = totality:debug / totality:universal_spell_access
progressionEligible = false
```

The UI marks it as debug-only. It does not write known spells, discoveries, Codex completion, permanent unlocks, achievements, quest conditions, research progress, first-use records, or any other normal-progression evidence. It cannot be converted into a permanent fact through ordinary progression APIs and does not survive when debug mode is disabled.

## 10.11 Multiple sources and partial removal

Flight is granted by Origin and an equipped ring.

- Remove ring: ring grant removed; Origin path remains.
- Change Origin: Origin grant removed; ring path remains if still equipped.
- Remove both: access ends.

No source removes another source's grant record.

---

# 11. Phone and service access examples

## 11.1 Visible but locked app

The Bank app is visible in its authored phone context before the player completes the required Banker dialogue.

Decision for `view`: allowed.  
Decision for `open_service`: denied with `totality:locked`.  
UI shows the authored lock explanation.

Another app may instead define `view` as hidden until the phone meets a hardware-tier requirement. Both behaviors use the same query API.

## 11.2 Installed but phone tier too low

Downgrade behavior is app-authored. For an app configured to preserve dormant installation:

- Installed: yes.
- Unlocked: yes.
- Available: no.
- Reason: phone tier requirement.

A different app may be automatically uninstalled or hidden on incompatible hardware. The Entitlement API reports the policy result; Phone owns the installation mutation. No universal downgrade rule is imposed.

## 11.3 Banking requires an account

Bank app unlock alone does not create a bank account.

Open requires:

- Equipped and set-up phone.
- App unlocked.
- App installed, if installation is required by the phone model.
- Compatible phone tier.
- Economy reports an active account.
- No story/service suspension.

## 11.4 Premium services

Premium delivery uses an account-tier requirement. The phone does not store a duplicate `premium` flag.

If the account is downgraded, the app remains known/unlocked but the premium operation becomes unavailable.

## 11.5 Story disables a service

A narrative event applies a service suspension to banking operations.

- Bank app remains unlocked and visible.
- Account remains owned.
- Opening or transactions return `temporarily_suspended`.
- UI may show a redacted story message.

## 11.6 Shop option gated by relationship or quest

Dialogue choice `trade_special_stock` uses an `AnyOf` or `AllOf` requirement authored for the NPC:

```text
AllOf(
  relationship_at_least(friend),
  quest_stage_at_least(supply_chain, 3)
)
```

The dialogue server evaluates it again before `OpenShopAction` creates a trade session.

## 11.7 Dimensional service

Opening Dimensional Transit requires:

```text
AllOf(
  permanent unlock from dimensional quest,
  compatible phone model/tier,
  active account/service eligibility,
  not currently story-suspended
)
```

Entering the dimension separately validates the destination/portal operation. Opening an app is not itself teleport authority.

---

# 12. Data-driven versus code-owned behavior

## 12.1 Data-driven

Safe data-driven elements:

- Entitlement key.
- Translation keys.
- Icon/category/tags.
- Registered authorization-policy ID.
- Requirement expression trees.
- Condition definition IDs and codec-safe configuration.
- Visibility rules.
- Basic lock messages.
- Static class-level feature mappings consumed by class implementations.
- Static phone app requirements.
- Static schematic/recipe/research relationships.

## 12.2 Code-owned

Code-owned behavior:

- Authorization policies.
- Requirement condition-type implementations.
- Dynamic class feature reconciliation.
- Equipment and item-instance grants.
- Transformation/status-effect grants.
- Runtime service security.
- Spell known/prepared semantics.
- Resource mutation and cost commitment.
- Complex relationship/story logic.
- Transactional item consumption.
- Active effect cancellation.

## 12.3 Why not fully data-drive the API

Access is security-sensitive and cross-system. A universal untyped JSON condition map would be difficult to validate and easy to exploit.

The canonical compromise is:

- Immutable, codec-validated data describes content and references known strategy IDs.
- Registered Java strategies perform authoritative runtime behavior.
- Complex systems remain explicit owners.

## 12.4 Load validation

At datapack/reload time validate:

- Registered type, policy, evaluator, action, and content IDs.
- Requirement tree cycles and invalid empty composites.
- Unsupported permanent facts for a type.
- Missing translation keys/icons as warnings.
- Grant mappings to unregistered content.
- Visibility leaks caused by references to hidden content.
- Duplicate definitions with conflicting owners.

Security failures fail closed. Non-security presentation failures may use placeholders.

---

# 13. Migration plan

Migration must be staged. Existing systems remain operational through adapters until their direct checks are replaced.

## Phase M0 — Inventory and diagnostics

Add a read-only `/totality entitlement audit <player>` command that reports:

- Legacy unlocked abilities/spells.
- Equipped/favorite entries.
- Class/species/origin state.
- Phone flags.
- Quest/narrative access flags.
- Unknown/unregistered IDs.

No mutation yet.

## Phase M1 — Core API in parallel

Implement registries, component, service, requirement tree, query result, grant index, reconciliation, and debug tooling.

Keep `AbilityComponent.hasAbility` behavior behind a compatibility adapter while logging disagreements between old and new results.

## Phase M2 — Ability flat unlocked set

For each legacy unlocked non-spell ability:

- First reconstruct class, subclass, covenant, species, origin, equipment, quest, or other known provenance and migrate it as source-bound access.
- Do not convert recognizable source-bound content into permanent progression.
- Only unexplained legacy entries that appear to represent a genuine independent player unlock fall back to permanent `UNLOCKED` with source `legacy_migration:ability_component`; flag them in diagnostics for review.
- Preserve favorites and equipped selection in `AbilityComponent`.
- Validate selection against Entitlement after migration.

Do not immediately delete the old set. Mark migrated data version and maintain a read adapter for one transition release.

Long-term:

- Remove `unlocked` as authority.
- Keep cooldowns, favorites, and equipped ability in Ability state.
- Deprecate `unlock/forget/hasAbility` and route them through Entitlement services or queries.

## Phase M3 — Default-unlocked spells

Do **not** migrate all current default spells into permanent knowledge/unlocks.

Because universal spell access was development scaffolding and had no real learning system, converting it would permanently contaminate every save.

Migration rule:

- Spells present solely because `isDefault()` is true are ignored as progression.
- Any future explicitly-authored spell acquisition evidence is migrated by a dedicated allowlist/versioned migration.
- Universal testing access moves to the debug grant provider.
- Remove `ensureDefaultAbilitiesUnlocked()` after the provider is active.

## Phase M4 — Class/subclass/covenant features

- Implement each class as an `EntitlementGrantProvider`.
- Keep/refactor `ClassFeatureRegistry` as static definitions/catalog.
- Remove direct/ad hoc player unlock writes from selection, login, and level-up handlers.
- Remove `ClassData.startingAbilities`, `SubclassData.startingAbilities`, and `CovenantData.grantedAbilities` after adapters and tests.

## Phase M5 — Species and Origin

Replace `SelectAncestryHandler -> AbilityComponent.unlock` with ancestry state mutation followed by Origin/Species provider reconciliation.

Existing selected ancestry remains unchanged. Grants become source-bound.

## Phase M6 — Spell known/prepared integration

Add the dedicated Spell components/services required by class design.

- Spell contributor reports independent and source-scoped known/prepared state.
- Class/subclass/patron/covenant/ancestry removal deletes its source-scoped spell knowledge and invalid prepared entries instead of leaving dormant hidden spell collections.
- Entitlement spell policy combines eligibility, known, preparation, grants, and requirements.
- Casting handler queries server-side before existing target/component/slot logic.

## Phase M7 — Phone apps and services

- Migrate `bank_app_unlocked` to permanent Bank app `UNLOCKED` with Banker-dialogue legacy provenance where exact historical dialogue evidence is unavailable.
- Retain a one-version read fallback for old saves.
- Remove the flag after successful migration.
- Keep `has_account` or its successor in Economy; it is not an app entitlement.
- Replace hardcoded client-only app gates with server entitlement queries.
- Add per-app visibility, access-source, installation-transfer, hardware-tier, and downgrade policies.
- Refine the proposed `PhoneState.explicitlyUnlockedApps`: genuine permanent unlocks move to player entitlement state; Phone state stores installed apps/layout/model/tier/setup. Apps supplied by hardware, accounts, quests, or services remain source-derived rather than being forced into permanent player unlocks.

## Phase M8 — Recipe knowledge

Keep each recipe-producing API as its own knowledge authority. Cooking retains `RecipeKnowledgeComponent.learnedRecipes`; Carpentry, Architecture, vanilla crafting, and future domains may use their own components or native recipe authorities.

Implement contributors that expose each domain's authored state. For Cooking this includes:

- `KNOWN` when `LEARNED`.
- Temporary guided access when an active bound guide quest produces `GUIDED`.
- Discovery separately from Codex.

Add a vanilla recipe-book adapter that treats explicitly unlocked vanilla recipes as genuine normal recipe unlocks. No destructive cross-domain rewrite is needed.

## Phase M9 — Quest and narrative flags

Classify existing flags:

- Narrative/history fact: remains a flag.
- Quest/stage state: remains Quest authority.
- Pure access boolean: migrate to an entitlement and remove after compatibility period.

Quest rewards call idempotent Entitlement mutation transactions rather than directly editing arbitrary components.

## Phase M10 — Debug/admin commands

Replace direct `AbilityComponent.unlock` debug commands with:

- Session debug grant.
- Persistent debug grant.
- Explicit permanent admin unlock command with warning and audit log.

Debug grants are always non-progression and cannot satisfy discovery, Codex, achievement, quest, research, learning, or first-use progression checks. The explicit permanent admin command is a separate administrative mutation, never an automatic debug-grant conversion.

## Phase M11 — Cleanup

After all call sites use the Entitlement service:

- Remove legacy flat checks.
- Remove pure access narrative flags.
- Remove dead class grant fields.
- Remove client-only authority checks.
- Turn compatibility disagreement logs into test assertions.

---

# 14. Edge cases and exploit prevention

## 14.1 Class or subclass changes

Reconcile old and new providers. Old source grants and source-scoped knowledge disappear from normal player-facing state. Only explicitly independent permanent facts remain. Use is denied immediately if no path remains, preventing hidden dormant accumulation when a player tests many classes.

## 14.2 Equipment breaks or is removed

Equipment source instance grant disappears. Active ability owner receives access-loss event and stops the effect safely.

## 14.3 Transformation ends during an active ability

Entitlement ends authorization. The owning transformation/ability system performs safe shutdown. The Entitlement API does not invent movement or damage rules.

## 14.4 Prepared spell loses access

Server casting is denied immediately.

If the spell's knowledge was source-scoped and that source ended, the Spell API removes the prepared entry from the normal player-facing prepared state; it must not leave a dormant hidden backlog. If the player still has independently permanent knowledge and only a temporary use requirement failed, the Spell API may retain the prepared selection as unavailable. In every case, a stale entry is never usable.

## 14.5 Logout during a temporary grant

Behavior follows grant lifetime:

- Source-bound: rebuilt from source state on login.
- Session: removed.
- Persistent lease: persisted and expiry checked.
- Online-tick expiry: pauses offline.
- Real-time expiry: may expire offline.

## 14.6 Two systems grant the same feature

Both records remain. Removing one source cannot remove the other.

## 14.7 Incorrect source removal

Removal APIs require the exact source reference or grant UUID. Provider reconciliation is source-scoped. Cross-provider deletion is rejected and logged.

## 14.8 Replay of unlock packet

No client unlock packet exists. Reward/acquisition requests are idempotent and validated against server sessions, item possession, quest state, and transaction IDs.

## 14.9 Quest rollback

- Stage-bound grant: automatically disappears when stage no longer applies.
- Permanent quest reward: remains by default because it was committed as permanent.
- Testing/admin rollback may explicitly revoke it through an audited mutation.
- A later quest may explicitly remove, suspend, steal, or replace the relevant item/service/access as part of authored narrative behavior. That quest action performs the actual domain mutation or compensating entitlement transaction.

Quest rollback never blindly deletes every entitlement mentioning that quest.

## 14.10 Consumed learning item

Consume item and write permanent knowledge in one server transaction. On failure, neither occurs. Replaying the request cannot consume another item or duplicate the fact.

## 14.11 Phone upgrade or downgrade

Permanent app unlocks remain unless an app was never authored as permanent player progression. Installed state, transfer, reinstallation, hiding, and incompatible-downgrade behavior follow that app's Phone policy. Tier requirements recalculate availability. A downgrade does not silently delete a genuine permanent unlock, but it may hide, deactivate, or uninstall the app according to authored rules.

## 14.12 Removed content definition

Persistent records are retained as orphaned data:

- Not usable.
- Not normally synchronized.
- Visible in admin diagnostics.
- Restored automatically if the same registered key returns and passes migration.

Do not silently discard save data during a mod update.

## 14.13 Legacy entitlement data

Unknown legacy IDs enter orphan quarantine. Migration reports counts and source files/keys where possible.

## 14.14 Unregistered grant reference

Reject the grant, log provider/source/key, and continue safely. Do not create invisible ad hoc keys.

## 14.15 Hidden information leakage

Failure messages and sync views obey disclosure policy. A hidden quest, species, dimension, or story condition may return a generic lock message instead of its real identifier.

## 14.16 Availability race

A UI decision is only a preview. Server action evaluates again against current source state. This handles item removal, resource spending, class change, effect expiry, or story suspension between display and click.

## 14.17 Definition reload

Datapack reload:

1. Build and validate a new immutable catalog.
2. Reject invalid security definitions before swap.
3. Atomically swap catalog.
4. Invalidate affected caches.
5. Reconcile providers.
6. Push display deltas to subscribed menus.

---

# 15. Implementation phases and acceptance tests

## Phase 1 — Core identity, catalog, and requirement engine

Implement:

- `EntitlementKey`
- Type registry
- Definition registry
- Action IDs
- Authorization policy registry
- Final requirement tree
- Condition definitions/evaluators
- Structured requirement results
- Definition load validation

### Acceptance tests

- Typed keys with identical content IDs do not collide.
- New type registers without editing a core enum.
- `AllOf`, `AnyOf`, and `Not` evaluate correctly.
- Missing condition type fails closed.
- Hidden failure details are redacted.
- Cyclic definitions are rejected.

## Phase 2 — Player component, transactions, grant index, provenance

Implement:

- Permanent facts
- Persistent grants
- Suspensions
- Runtime grant index
- Source-scoped reconciliation
- Revisions
- Mutation transactions/events
- Orphan storage

### Acceptance tests

- Permanent `KNOWN`/`UNLOCKED` survives save/load, death, respawn, and dimension change.
- Source-bound temporary grant disappears with source.
- Persistent lease expires by selected clock.
- Two sources coexist and removing one preserves access.
- Reconciliation is idempotent.
- Provider cannot remove another provider's grant.
- Explicit revocation is audited.

## Phase 3 — Query service and server synchronization

Implement:

- State contributors
- Snapshot assembly
- Authorization policies
- Structured decisions
- Display subscriptions/deltas
- Server request validation
- Service session tokens

### Acceptance tests

- Query distinguishes visible, known, owned, unlocked, granted, selected, and available.
- Known-but-unavailable returns the correct structured reason.
- UI receives only visible/relevant entries.
- Stale client is denied or refreshed correctly.
- Forged client request cannot grant or open a protected service.
- Open menu updates when a dependency changes.

## Phase 4 — Ability and debug migration

Implement:

- Ability entitlement type/policy.
- Ability state contributor for equipped/favorites/cooldowns.
- Legacy unlocked migration.
- Debug universal-spell provider.
- Server activation query.

### Acceptance tests

- Non-spell legacy ability migrates once.
- Default spells do not become permanent.
- Debug spell access disappears when debug mode disables.
- Debug access cannot award progression.
- Equipped ability becomes unusable when last source disappears.
- Cooldowns/favorites remain intact.

## Phase 5 — Class, subclass, covenant, species, and origin providers

Implement:

- Per-class providers.
- Static `ClassFeatureRegistry` catalog role.
- Level dependency invalidation.
- Species/origin providers.
- Removal of direct ability unlock writes.

### Acceptance tests

- Class feature appears exactly at its threshold.
- Class removal removes only class grants.
- Subclass replacement reconciles correctly.
- Covenant/patron grant has correct provenance.
- Species/origin ability is source-bound.
- Changing ancestry removes the old source completely; a shared key remains only if the new ancestry independently grants it.
- Testing multiple classes/ancestries does not create dormant hidden feature backlogs.
- Character creation commits selections and grants atomically.

## Phase 6 — Spell known/prepared integration

Implement with the dedicated Spell work:

- Spell knowledge contributor.
- Preparation contributor.
- D&D spell authorization policy.
- Class/patron/species spell paths.
- Server-side preparation and cast checks.

### Acceptance tests

- Wizard can know a spell without preparing it.
- Prepared unknown spell is rejected.
- Eligible but unknown spell is not castable.
- Known/prepared spell without slot/resource is unavailable, not forgotten.
- Removing the class/patron/ancestry removes source-scoped spell knowledge and invalidates preparation unless the spell was explicitly learned independently.
- Patron spell follows its authored list/known/grant rule.
- Species innate spell works without incorrectly joining the Wizard spellbook.
- Existing successful-cast slot commitment remains intact.

## Phase 7 — Equipment, effects, and transformations

Implement instance-aware providers and cancellation hooks.

### Acceptance tests

- Equipped item grants only while valid/equipped.
- Two copies have distinct source instance IDs.
- Item break removes only its grant.
- Status effect expiry removes access.
- Transformation end stops active granted ability safely.
- Death policies behave per lifetime.

## Phase 8 — Phone apps, accounts, services, dialogue

Implement:

- Phone app/service types.
- App definitions and policies.
- `bank_app_unlocked` migration.
- Phone/account/relationship/quest conditions.
- Server-side app and `OpenShopAction` validation.

### Acceptance tests

- Bank app can be visible but locked before Banker dialogue.
- A different app can remain hidden until a compatible phone tier exists.
- Unlocked app can be unavailable due to phone tier.
- Replacement/reinstallation and downgrade behavior follow the app's authored policy.
- Genuine permanent app unlock survives phone replacement/upgrade.
- Banking requires an account.
- Premium operation uses Economy account tier.
- Story suspension closes or disables an open service.
- Relationship/quest-gated shop option is checked server-side.
- Dimensional service requires both quest unlock and phone compatibility.

## Phase 9 — Cooking, Codex, schematics, research, and future adapters

Implement:

- Cooking recipe-knowledge contributor.
- Codex discovery contributor.
- Generic schematic/technique permanent facts where no richer owner exists.
- Research provider hooks.

### Acceptance tests

- Discovered recipe is not automatically learned unless that recipe domain explicitly authors discovery as acquisition.
- `GUIDED` exists only while guide quest is active for Cooking.
- Learned Cooking recipe persists after quest ends.
- Vanilla recipe-book unlock is treated as a genuine authored recipe unlock.
- A Carpentry or Architecture recipe follows its own API's learning rules rather than Cooking's rules.
- Consumed schematic learning is atomic.
- Owned schematic can still fail skill requirement.
- Codex hidden content is not leaked through entitlement sync.

## Phase 10 — Cleanup and regression hardening

Remove legacy direct checks and flags after a compatibility period.

### Acceptance tests

- No production call site uses `AbilityComponent.unlocked` as authority.
- No ordinary spell relies on `isDefault()` for progression.
- No phone app trusts client-only gating.
- No class/origin selection directly writes permanent ability access unless explicitly authored as permanent.
- Legacy saves migrate without losing valid abilities, favorites, phone progression, recipes, or quest state.
- Logout, death, respawn, restart, and dimension-change integration tests pass.

---

# Canonical decisions established by this document

1. Entitlement is a federated, server-authoritative decision layer, not one universal boolean component.
2. Discovery, visibility, knowledge, ownership, permanent unlocking, grants, selection, and availability are distinct axes.
3. Codex discovery is never automatically permission to use content.
4. Entitlement content uses `EntitlementKey(typeId, contentId)`; content types are namespaced registrations, not a closed enum.
5. The finalized reusable requirement model is `AllOf / AnyOf / Not / Condition`, with registered condition definitions and structured results.
6. Permanent facts are stored; source-bound grants are derived/reconciled wherever another system owns the source.
7. Multiple grant sources coexist independently. Removing one cannot remove another.
8. Entitlement grants authorize access but do not stack effect strength, resources, ranks, or charges.
9. Temporary requirements can suspend use but do not erase permanent progression.
10. D&D Spell and Cooking retain their richer known/prepared/recipe knowledge components and contribute state to the common query layer.
11. Each class implementation is the authoritative grant provider. `ClassFeatureRegistry` is a static catalog, not player-state authority.
12. Universal default spell access becomes an explicit non-progression debug provider, disabled by default in normal play.
13. Phone app access is authored per app. Genuine permanent app unlocks are player progression; phone item state owns model, tier, setup, installation, layout, transfer, and downgrade behavior. Some apps may remain hidden until compatible hardware exists.
14. Recipe acquisition/learning is owned per recipe-producing API. Vanilla recipe-book unlocks are genuine authored unlocks; Cooking's `UNKNOWN / GUIDED / LEARNED` model is not imposed on Carpentry, Architecture, or future recipe domains.
15. Source-bound class, subclass, covenant, species, origin, patron, item, effect, and transformation content disappears from normal player-facing state when its source ends. Shared features remain only through another independently active source.
16. Ordinary progression content may be visible-but-locked, but visibility is content-authored and quest/secret/hardware-gated content may remain hidden.
17. Debug grants are completely isolated from normal progression and cannot be converted through ordinary progression paths.
18. Client UI is advisory. Every protected action is revalidated on the server.
19. Hidden content and requirement details are synchronized only when disclosure policy permits them.
20. Legacy content IDs are preserved as orphaned records rather than silently deleted.

---

# Owner-confirmed policy decisions (2026-07-13)

The following decisions were confirmed after the first canonical draft and are part of this document's authority:

- Visibility is decided per content. Visible-but-locked is a common ordinary-progression presentation, not a mandatory universal default.
- Source-bound class, ancestry, covenant/patron, equipment, effect, and transformation content is removed from player-facing state when its source ends. It is not retained as dormant hidden history.
- Shared features such as Darkvision remain only when the replacement source independently grants the same entitlement key.
- Permanent quest rewards survive ordinary rollback; explicit quest/admin/testing mutations may revoke, suspend, steal, or replace them.
- Recipe learning is owned per API, and vanilla recipe-book unlocks are legitimate authored unlocks.
- Phone app visibility, unlock source, installation transfer, hardware gating, and downgrade behavior are authored per app.
- Debug grants are completely isolated from normal progression.

---

# Superseded older mechanisms and documents

The following are superseded as access authority:

- `AbilityComponent.unlocked` and `hasAbility` as the universal source of ability/spell access.
- `AbilityComponent.ensureDefaultAbilitiesUnlocked()` as normal progression.
- Every spell being permanently/default accessible outside explicit debug mode.
- Direct `OriginData.startingAbilities -> AbilityComponent.unlock` mutation.
- Unused `ClassData.startingAbilities`, `SubclassData.startingAbilities`, and `CovenantData.grantedAbilities` as competing grant mechanisms.
- Pure access narrative flags such as `bank_app_unlocked` after migration.
- Client-only phone app lock checks.
- The earlier generic name/shape `UnlockRequirement`; its intent is preserved and finalized as `EntitlementRequirement` with `NOT`, condition definitions, disclosure, dependencies, and structured evaluation.
- The proposed `PhoneState.explicitlyUnlockedApps` as the sole permanent access store. Phone state may keep installation/configuration references, while permanent unlock authority lives in player entitlement/domain state.
- Any linear stored `SpellAccessState` that attempts to collapse eligibility, discovery, acquisition, known/prepared, and castability.

The superseded Cooking archive remains non-canonical. The current Food Ecosystem specification remains authoritative for recipe knowledge and Codex design.

---

# Intentionally deferred questions

These are not required to implement the Entitlement core and remain owned by dedicated designs:

- Exact known/prepared limits, preparation counts, spellbook mechanics, and acquisition methods for each spellcasting class. Source-scoped knowledge removal versus explicitly independent permanent learning is no longer deferred.
- Exact patron spell semantics per patron.
- Exact class/species/origin feature content and balance. Source-bound retention behavior is no longer deferred.
- Exact UI animation/message when a source-scoped prepared spell is removed. The removal rule itself is canonical; no dormant hidden preparation backlog is retained after its knowledge source ends.
- Exact phone models, tier capacities, installation limits, upgrade recipes, and each app's authored transfer/downgrade policy. The requirement that these policies are per-app is canonical.
- Exact Relationship scale, tiers, gain/loss rules, romance, spouse, and NPC ceiling representation.
- Exact research progression mechanics.
- Exact transformation shutdown consequences such as flight fall grace.
- Exact dimensional travel mechanics after access is approved.
- Whether individual future systems need richer ranked entitlements; the V1 core intentionally does not stack potency.
- Exact UI layout and wording beyond structured reason/provenance support.

---

# APIs that depend on this one

Direct dependencies:

- Per-class implementation architecture and class feature completion.
- Species and Origin grants.
- Final D&D Spell access, known/prepared, patron, and innate spell integration.
- Ability acquisition/equipment/activation.
- Phone app and remote service gating.
- Dialogue action and relationship-aware option gating.
- Quest reward unlocks and story suspensions.
- Cooking recipe knowledge presentation/integration.
- Schematics, techniques, licenses, and research.
- Equipment/attunement-granted abilities.
- Transformations and status-effect grants.
- Economy account-tier services.
- Dimensional access and future progression gates.

Indirect dependency:

- Generic Player Resource consumers that require a source-specific resource to exist. The Resource API itself remains independently closed.

---

# Reopening criteria

This API should be reopened only if a real implementation or new system reveals that one of these assumptions is insufficient:

1. A content system cannot represent its access with typed keys, registered policies, requirements, contributors, and source-bound grants.
2. A real gameplay need requires ranked/stacked entitlement semantics that cannot correctly remain in the owning system.
3. Cross-player, party, faction, settlement, or world-level entitlements require authority beyond player state.
4. Delegated/shared ownership — lending, party licenses, guild research, account families — requires a new principal model.
5. Offline entitlement changes or external account services require a different persistence/security model.
6. A new privacy requirement makes display subscriptions insufficient.
7. The final Spell or Research designs prove that the federated contributor model creates unavoidable duplicated authority.
8. A migration discovers existing access state that cannot be safely classified as permanent fact, source-bound grant, domain state, suspension, or orphan.

Adding more abilities, spells, resources, recipes, apps, classes, species, schematics, services, or dimensions through the existing extension points is **not** a reason to reopen the API.

---

# End of canonical document
