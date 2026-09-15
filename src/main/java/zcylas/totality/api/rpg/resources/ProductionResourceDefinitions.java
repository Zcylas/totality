package zcylas.totality.api.rpg.resources;

import zcylas.totality.api.rpg.resources.external.BreathResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.external.FoodResourceAdapter;
import zcylas.totality.api.rpg.resources.external.HealthResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ManaResourceAdapter;
import zcylas.totality.api.rpg.resources.external.RageResourceAdapter;
import zcylas.totality.api.rpg.resources.external.StaminaResourceAdapter;
import zcylas.totality.api.rpg.resources.external.StandardSpellSlotsResourceAdapter;
import zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion;
import zcylas.totality.api.rpg.resources.presentation.ResourceDisplayType;
import zcylas.totality.api.rpg.resources.presentation.ResourceHudRole;
import zcylas.totality.api.rpg.resources.presentation.ResourcePresentationDefinition;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatter;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatterRegistry;

/**
 * Registers the Generic Player Resource API's production content — {@code totality:health},
 * {@code totality:food} (Phase 2A), and {@code totality:breath} (Phase 2B), all
 * {@code EXTERNAL_ADAPTER}-authority query-only resources — and freezes every registry involved at
 * a deterministic point during mod initialization, before any player can join. See
 * {@code Totality.registerApi()}, which calls {@link #register()} once, after every other Phase 1
 * foundation class has had a chance to load.
 *
 * Nothing here creates player state: registering a definition and its adapter is pure metadata
 * registration (canonical §4.4 — a definition existing does not mean any player owns it), and
 * Health/Food/Breath never gain {@link PlayerResourceStateComponent} entries because their
 * authority is {@code EXTERNAL_ADAPTER}, not {@code GENERIC_COMPONENT} (see
 * {@link PlayerResourceStateComponent#instantiateScalar}'s rejection of external definitions).
 *
 * Breath deliberately declares no {@link ResourcePresentationDefinition} — unlike Health/Food's
 * shared {@code 5/1} formatter, no canonical Breath presentation unit (seconds, percentage, pip
 * count, ...) is defined anywhere yet. Inventing one here would be a speculative formatter the
 * Phase 2B task explicitly forbids; that decision is deferred to a future HUD-presentation phase.
 * Breath remains contextually visible today through vanilla's own unmodified air-bubble HUD.
 *
 * <p>Phase 2C originally added {@code totality:mana} and {@code totality:stamina} as transitional
 * {@code EXTERNAL_ADAPTER}-authority, query-only resources wrapping the legacy-authoritative {@link
 * PlayerResourceComponent} store via {@code ManaResourceAdapter}/{@code StaminaResourceAdapter}. The
 * Phase 4 Mana/Stamina migration (2026-09-15) redefines both as {@code GENERIC_COMPONENT}-authority
 * (the builder default; no {@code .externalAdapter(...)} call) at {@code definitionVersion = 2} —
 * see {@link #registerMaximumResolvers}/{@link #registerGrants} for the real {@link
 * zcylas.totality.api.rpg.resources.ResourceMaximumResolver}/{@link
 * zcylas.totality.api.rpg.resources.integration.ResourceGrantProvider} wiring this migration adds,
 * and {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE4_MANA_STAMINA_IMPLEMENTATION_REPORT_2026-09-15.md}
 * for the full migration. {@code ManaResourceAdapter}/{@code StaminaResourceAdapter} remain
 * registered (see {@link #registerAdapters}'s own Javadoc for why) but are no longer referenced by
 * either definition. Both still use {@link ResourceDisplayConversion#IDENTITY} and a full {@link
 * ResourcePresentationDefinition} (canonical §19.8 treats Mana/Stamina as constant HUD elements,
 * matching Health/Food/Hunger) — the existing Totality-drawn HUD bars remain untouched; presentation
 * metadata for these two still has no client consumer.
 *
 * <p>Phase 2D adds {@code totality:spell_slots} — the Resource API's first {@code PARTITIONED_POOL}
 * production resource, also {@code EXTERNAL_ADAPTER}-authority and query-only, transitionally
 * wrapping the legacy-authoritative {@code SpellSlotComponent} array store via {@link
 * StandardSpellSlotsResourceAdapter} exactly the way Mana/Stamina wrap {@code PlayerResourceComponent}.
 * Registered at {@code definitionVersion = 1}. Declares no {@code authoredBaseMaximum}: that field is
 * a single {@code long} (inherently scalar-shaped) and cannot represent ten independent per-level
 * maxima — see the Phase 2D report's "authoredBaseMaximum omitted" section. Declares only {@link
 * ResourceCapability#MENU_VISIBLE}, not {@link ResourceCapability#HUD_VISIBLE}: spell slots belong in
 * the spell radial / a future Spells app / character screens, not the ordinary resource-bar HUD.
 * Also deliberately declares no {@code SPENDABLE}/{@code RESTORABLE}/{@code PARTITIONED_SPENDING}
 * capability — matching every other Phase 2A/2B/2C adapter's precedent of declaring no mutation
 * capability regardless of whether the legacy owner can itself mutate the value, since this adapter's
 * {@code supportedOperations()} is {@code QUERY}-only; see the Phase 2D report for the full reasoning.
 * {@code ResourceDisplayType.SLOTS}/{@code ResourceHudRole.MENU_ONLY} presentation is declared for
 * definition-shape completeness even though nothing consumes it yet (Phase 2D adds no UI); no {@link
 * zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatter} is registered for it, since
 * that interface's {@code toDisplayCurrent}/{@code toDisplayMaximum} methods take a single scalar
 * {@code ResourceSnapshot} and have no partitioned counterpart yet.
 *
 * <p>Phase 2E originally added {@code totality:rage} as a transitional {@code EXTERNAL_ADAPTER}-
 * authority, query-only resource wrapping one entry ({@code BarbarianRageAbility.CHARGE_ID}) of the
 * legacy generically-keyed {@code PlayerChargesComponent} charge-pool map via {@code
 * RageResourceAdapter}. The Phase 5 Rage migration (2026-09-15) redefines it as {@code
 * GENERIC_COMPONENT}-authority (the builder default; no {@code .externalAdapter(...)} call) at
 * {@code definitionVersion = 2} — see {@link #registerMaximumResolvers}/{@link #registerGrants} for
 * the real {@link zcylas.totality.api.ability.impl.barbarian.RageMaximumResolver}/{@link
 * zcylas.totality.api.rpg.resources.integration.BarbarianRageResources} wiring this migration adds,
 * and {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API_PHASE5_RAGE_IMPLEMENTATION_REPORT_2026-09-15.md}
 * for the full migration. {@code RageResourceAdapter} remains registered (see {@link
 * #registerAdapters}'s own Javadoc) but is no longer referenced by the definition. {@code SCALAR}
 * model unchanged (canonical §6.1/§6.3/§25.6 are explicit Rage is scalar, not partitioned).
 * Deliberately no {@code .authoredBaseMaximum(...)} — {@link
 * zcylas.totality.api.ability.impl.barbarian.RageMaximumResolver} (registered below) now resolves
 * the real, class-level-dependent maximum, and keeping the old descriptive {@code 2} literal here
 * would silently short-circuit it for SCALAR resources exactly the way Mana/Stamina's own
 * {@code authoredBaseMaximum} would have (see that migration's own Javadoc paragraph above).
 *
 * <p>The dormant Resource Registration pass adds {@code totality:thirst}, {@code totality:sanity},
 * and {@code totality:ki}: {@code GENERIC_COMPONENT}-authority (the builder default; no
 * {@code .externalAdapter(...)} call), the first of their kind in production. No adapter is
 * registered for any of the three (see {@link #registerAdapters()}, unchanged). No capabilities and
 * no {@link ResourcePresentationDefinition} are declared for any of them: {@code HUD_VISIBLE}/
 * {@code MENU_VISIBLE} currently have zero consumers anywhere in the codebase (nothing iterates the
 * registry by capability to decide what to render), so declaring them would be provably inert today,
 * but doing so would still misrepresent these three as "available for display" before any owning
 * system, grant provider, or client reader exists. Lifecycle is left at
 * {@link ResourceLifecyclePolicy#DEFAULT} (at-maximum initialization) for Thirst/Sanity, matching
 * this pass's authoritative "at maximum when explicitly instantiated" intent without needing an
 * explicit override; Ki's own future initialization policy is undecided and left to the Ki API. See
 * {@code TOTALITY_DORMANT_RESOURCE_REGISTRATION_IMPLEMENTATION_REPORT.md} for the full readiness
 * matrix, including why {@code totality:fatigue} and {@code totality:temperature} are deliberately
 * absent. Nothing here grants, instantiates, persists, synchronizes, or displays any of the three.
 */
public final class ProductionResourceDefinitions {

    public static void register() {
        registerAdapters();
        registerDefinitions();
        registerMaximumResolvers();
        registerGrants();
        registerFormatters();
    }

    /**
     * {@code ManaResourceAdapter}/{@code StaminaResourceAdapter} remain registered here even though
     * neither definition references them anymore as of the Phase 4 migration (2026-09-15), and
     * {@code RageResourceAdapter} joins them as of the Phase 5 migration (2026-09-15, same date) — a
     * deliberate, documented deferred-cleanup decision, not an oversight. Removing them is a Phase 8
     * concern (canonical: "remove... after no callers remain," per-resource, not all at once); their
     * own unit tests still exercise their (now-unreachable-from-production) {@code resolve}/{@code
     * normalize} logic directly, and leaving the registration in place costs nothing (an
     * unreferenced adapter is simply never looked up).
     */
    private static void registerAdapters() {
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(HealthResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(FoodResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(BreathResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(ManaResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(StaminaResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(StandardSpellSlotsResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(RageResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.freeze();
    }

    /** Phase 4 migration: real resolvers for the first two GENERIC_COMPONENT resources that need one.
     *  Phase 5 adds Rage's, delegating to {@code BarbarianRageAbility.getMaxRage} the same way Mana/
     *  Stamina's delegate back to their own manager classes. */
    private static void registerMaximumResolvers() {
        ResourceMaximumResolverRegistry.INSTANCE.register(PlayerResourceIds.MANA, zcylas.totality.api.rpg.mana.ManaMaximumResolver.INSTANCE);
        ResourceMaximumResolverRegistry.INSTANCE.register(PlayerResourceIds.STAMINA, zcylas.totality.api.rpg.stamina.StaminaMaximumResolver.INSTANCE);
        ResourceMaximumResolverRegistry.INSTANCE.register(PlayerResourceIds.RAGE, zcylas.totality.api.ability.impl.barbarian.RageMaximumResolver.INSTANCE);
    }

    /** Phase 4 migration: the first production grant provider — see {@code PlayerBaselineResources}.
     *  Phase 5 adds the second, Barbarian-class-gated one — see {@code BarbarianRageResources}. */
    private static void registerGrants() {
        zcylas.totality.api.rpg.resources.integration.PlayerBaselineResources.register();
        zcylas.totality.api.rpg.resources.integration.BarbarianRageResources.register();
    }

    private static void registerDefinitions() {
        // Both Health and Food are player-visible constant HUD resources, so both declare
        // HUD_VISIBLE and MENU_VISIBLE (canonical §9's feature-declaration capabilities). Neither
        // declares a mutation capability (SPENDABLE, RESTORABLE, DIRECT_DRAIN, ...): Phase 2A's
        // generic adapters remain query-only regardless of whether their owning vanilla systems
        // (Health/Combat, Food/Hunger) can themselves change the underlying value — see the
        // correction pass's "Align capabilities and presentation metadata" section.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.HEALTH, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .externalAdapter(PlayerResourceIds.HEALTH_ADAPTER)
                        .unitScale(HealthResourceAdapter.UNIT_SCALE)
                        .absoluteMinimum(0)
                        // Descriptive baseline only (canonical §25.1: "Mechanical baseline: 20") — the
                        // live query path (HealthResourceAdapter.snapshot) always reads the player's
                        // real, attribute-scaled player.getMaxHealth() and never consults this value.
                        .authoredBaseMaximum(20 * HealthResourceAdapter.UNIT_SCALE)
                        .capabilities(ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                        .presentation(new ResourcePresentationDefinition(
                                ResourceDisplayConversion.HEALTH_FOOD,
                                ResourceDisplayType.BAR,
                                ResourceHudRole.CORE_CONSTANT))
                        .build());

        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.FOOD, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .externalAdapter(PlayerResourceIds.FOOD_ADAPTER)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        // Unlike Health, vanilla Food genuinely has one fixed ceiling (FoodResourceAdapter.NATIVE_MAXIMUM);
                        // still descriptive only — the live query path reads getFoodLevel() directly.
                        .authoredBaseMaximum(FoodResourceAdapter.NATIVE_MAXIMUM)
                        .capabilities(ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                        .presentation(new ResourcePresentationDefinition(
                                ResourceDisplayConversion.HEALTH_FOOD,
                                ResourceDisplayType.BAR,
                                ResourceHudRole.CORE_CONSTANT))
                        .build());

        // Breath: HUD_VISIBLE/MENU_VISIBLE only, no mutation capability, and deliberately no
        // .presentation(...) — see the class Javadoc for why. The authored maximum below is the
        // audited vanilla baseline (Entity.TOTAL_AIR_SUPPLY = 300); the live query path
        // (BreathResourceAdapter.snapshot) always reads the player's actual, dynamically-resolved
        // player.getMaxAirSupply() and never consults this descriptive value, exactly like Health's
        // authoredBaseMaximum never being consulted by the live query path.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.BREATH, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .externalAdapter(PlayerResourceIds.BREATH_ADAPTER)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        .authoredBaseMaximum(BreathResourceAdapter.VANILLA_BASELINE_MAXIMUM)
                        .capabilities(ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                        .build());

        // Mana/Stamina: Phase 4 migration (2026-09-15) — GENERIC_COMPONENT authority (the builder
        // default; no .externalAdapter(...) call), bumped to definitionVersion 2 (never a silent
        // structural hot-swap of what totality:mana/totality:stamina mean). Deliberately NO
        // .authoredBaseMaximum(...): PlayerResourceService.resolveMaximum makes an authored base win
        // outright over any registered resolver for SCALAR resources, so keeping the old descriptive
        // BASE_MAX_MANA/BASE_MAX_STAMINA literal here would silently short-circuit
        // ManaMaximumResolver/StaminaMaximumResolver (registered below) and collapse the real
        // dynamic formula (stats + equipment + effects + event hooks) down to a flat 100 — exactly
        // the bug this migration's own task instructions warn against. RESET_TO_MAXIMUM death policy
        // paired with AtMaximum grant initialization reproduces legacy's exact "full refill on
        // respawn" behavior (PlayerResourceComponent.copyFrom always reset both to -1/uninitialized,
        // and PlayerManaManager/PlayerStaminaManager's lazy-init-to-max getters did the rest) — see
        // PlayerResourceStateComponent#copyFrom's own death-policy handling.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.MANA, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        // Canonical §25.4's exact declared capability set for totality:mana — a real
                        // authoritative GENERIC_COMPONENT resource, not the query-only transitional
                        // shape Phase 2C originally declared (HUD_VISIBLE/MENU_VISIBLE only). Final
                        // external-review correction pass, 2026-09-15.
                        .capabilities(ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE,
                                ResourceCapability.PASSIVE_REGENERATION, ResourceCapability.MAXIMUM_MODIFIERS,
                                ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                        .presentation(new ResourcePresentationDefinition(
                                ResourceDisplayConversion.IDENTITY,
                                ResourceDisplayType.BAR,
                                ResourceHudRole.CORE_CONSTANT))
                        .lifecycle(new ResourceLifecyclePolicy(
                                ResourceDeathPolicy.RESET_TO_MAXIMUM, true, true, false,
                                new zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization.AtMaximum()))
                        .definitionVersion(2)
                        .build());

        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.STAMINA, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        // Canonical §25.5's exact declared capability set for totality:stamina.
                        // DIRECT_DRAIN reflects the ~12 continuous/environmental drain callers
                        // (sprint, flight, bow draw, ...); CLIENT_PREDICTION reflects
                        // TotalityMovementHandler's real, server-re-validated Power Sprint/Super Leap
                        // gate. Final external-review correction pass, 2026-09-15.
                        .capabilities(ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE,
                                ResourceCapability.DIRECT_DRAIN, ResourceCapability.PASSIVE_REGENERATION,
                                ResourceCapability.MAXIMUM_MODIFIERS, ResourceCapability.CLIENT_PREDICTION,
                                ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                        .presentation(new ResourcePresentationDefinition(
                                ResourceDisplayConversion.IDENTITY,
                                ResourceDisplayType.BAR,
                                ResourceHudRole.CORE_CONSTANT))
                        .lifecycle(new ResourceLifecyclePolicy(
                                ResourceDeathPolicy.RESET_TO_MAXIMUM, true, true, false,
                                new zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization.AtMaximum()))
                        .definitionVersion(2)
                        .build());

        // Standard spell slots: transitional EXTERNAL_ADAPTER over the legacy SpellSlotComponent
        // store (see the class Javadoc). PARTITIONED_POOL model — the Resource API's first. No
        // .authoredBaseMaximum(...) (scalar-shaped field, cannot represent ten per-level maxima).
        // No HUD_VISIBLE — presentation is MENU_ONLY (spell radial / future Spells app), not the
        // ordinary resource-bar HUD. No SPENDABLE/RESTORABLE/PARTITIONED_SPENDING capability — this
        // adapter is query-only, exactly like every other Phase 2A/2B/2C adapter. unitScale uses the
        // adapter's own canonical StandardSpellSlotsResourceAdapter.UNIT_SCALE constant rather than a
        // second, independently-maintained literal — the adapter always produces a snapshot at that
        // scale regardless of what this definition declares, so the two must stay in lockstep.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.SPELL_SLOTS, ResourceModel.PARTITIONED_POOL)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .externalAdapter(PlayerResourceIds.SPELL_SLOTS_ADAPTER)
                        .unitScale(StandardSpellSlotsResourceAdapter.UNIT_SCALE)
                        .absoluteMinimum(0)
                        .capabilities(ResourceCapability.MENU_VISIBLE)
                        .presentation(new ResourcePresentationDefinition(
                                ResourceDisplayConversion.IDENTITY,
                                ResourceDisplayType.SLOTS,
                                ResourceHudRole.MENU_ONLY))
                        .definitionVersion(1)
                        .build());

        // Rage: Phase 5 migration (2026-09-15) — GENERIC_COMPONENT authority (the builder default;
        // no .externalAdapter(...) call), bumped to definitionVersion 2. SCALAR model unchanged.
        // Deliberately NO .authoredBaseMaximum(...) — see the class Javadoc's Phase 5 paragraph.
        // No .lifecycle(...) override: ResourceLifecyclePolicy.DEFAULT's KEEP_CURRENT death policy
        // already matches legacy Rage's own copyFrom (blanket preserve-all-pools) exactly — unlike
        // Mana/Stamina, which needed an explicit RESET_TO_MAXIMUM override. unitScale keeps using
        // RageResourceAdapter.UNIT_SCALE (still 1) for continuity even though the adapter no longer
        // backs this definition, since nothing else defines a canonical Rage unit scale.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.RAGE, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .unitScale(RageResourceAdapter.UNIT_SCALE)
                        .absoluteMinimum(0)
                        // Canonical §25.6's exact declared capability set for totality:rage.
                        .capabilities(ResourceCapability.SPENDABLE, ResourceCapability.RESTORABLE,
                                ResourceCapability.MAXIMUM_MODIFIERS, ResourceCapability.HUD_VISIBLE,
                                ResourceCapability.MENU_VISIBLE)
                        .presentation(new ResourcePresentationDefinition(
                                ResourceDisplayConversion.IDENTITY,
                                ResourceDisplayType.PIPS,
                                ResourceHudRole.CONTEXTUAL_ACCESS))
                        .definitionVersion(2)
                        .build());

        // Thirst: dormant, GENERIC_COMPONENT-authority (builder default), no adapter, no
        // capabilities, no presentation. absoluteMinimum 0 / authoredBaseMaximum 100 matches this
        // pass's authoritative intent (canonical §25.11 settles SCALAR/GENERIC_COMPONENT/HIGH_IS_GOOD
        // but not an exact numeric range; this pass's own task instructions supply 0-100). Lifecycle
        // is left at ResourceLifecyclePolicy.DEFAULT (AtMaximum) — already exactly "at maximum when
        // explicitly instantiated," no override needed. No owning system exists yet, so nothing ever
        // calls instantiateScalar for this id; it stays permanently dormant until the Thirst API adds
        // a grant provider.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.THIRST, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        .authoredBaseMaximum(100)
                        .definitionVersion(1)
                        .build());

        // Sanity: dormant, same shape as Thirst above — GENERIC_COMPONENT-authority, no adapter, no
        // capabilities, no presentation, default (AtMaximum) lifecycle. Canonical §25.15 hedges
        // "likely SCALAR"/"likely HIGH_IS_GOOD" pending the future Sanity design; this pass's task
        // instructions settle those as the registered values without inventing anything the canonical
        // doc actively contradicts. No owning system exists yet, so this stays permanently dormant
        // until the Sanity API adds a grant provider.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.SANITY, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        .authoredBaseMaximum(100)
                        .definitionVersion(1)
                        .build());

        // Ki: dormant, GENERIC_COMPONENT-authority, no adapter, no capabilities, no presentation.
        // Deliberately declares NO authoredBaseMaximum (OptionalLong.empty(), the builder default) —
        // canonical §25.7 states Ki's maximum is set "by Monk level/features," i.e. genuinely dynamic,
        // and no ResourceMaximumResolver framework exists yet (PlayerResourceService's own
        // MAXIMUM_UNAVAILABLE failure reason exists precisely for this "no maximum-resolver framework
        // exists yet" case, per its Javadoc). Hardcoding e.g. 100 here would be exactly the invented
        // placeholder maximum this pass is required not to add; omitting it instead means any future
        // query (once the Ki API exists and actually instantiates state) fails structurally with
        // MAXIMUM_UNAVAILABLE rather than fabricating a maximum — see
        // PlayerResourceServiceTest.genericScalarWithoutAnAuthoredMaximumFailsStructurallyRatherThanFabricatingOne
        // for the exact existing, already-tested behavior this relies on, and this pass's own
        // KiDormantRegistrationTest for the resource-specific proof. Not universally owned: since no
        // grant provider exists for any GENERIC_COMPONENT resource yet, Ki is exactly as dormant as
        // Thirst/Sanity above — "conditional ownership" is simply the future Ki API's own grant
        // provider deciding who gets it, not something this definition needs to encode.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.KI, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        .definitionVersion(1)
                        .build());

        PlayerResourceRegistry.INSTANCE.freeze(ExternalPlayerResourceAdapterRegistry.INSTANCE);
    }

    private static void registerFormatters() {
        ResourceValueFormatterRegistry.INSTANCE.register(
                ResourceValueFormatter.ofConversion(PlayerResourceIds.HEALTH, ResourceDisplayConversion.HEALTH_FOOD));
        ResourceValueFormatterRegistry.INSTANCE.register(
                ResourceValueFormatter.ofConversion(PlayerResourceIds.FOOD, ResourceDisplayConversion.HEALTH_FOOD));
        ResourceValueFormatterRegistry.INSTANCE.register(
                ResourceValueFormatter.ofConversion(PlayerResourceIds.MANA, ResourceDisplayConversion.IDENTITY));
        ResourceValueFormatterRegistry.INSTANCE.register(
                ResourceValueFormatter.ofConversion(PlayerResourceIds.STAMINA, ResourceDisplayConversion.IDENTITY));
        // totality:spell_slots deliberately has no registered formatter — see the class Javadoc.
        ResourceValueFormatterRegistry.INSTANCE.register(
                ResourceValueFormatter.ofConversion(PlayerResourceIds.RAGE, ResourceDisplayConversion.IDENTITY));
        ResourceValueFormatterRegistry.INSTANCE.freeze();
    }

    private ProductionResourceDefinitions() {}
}
