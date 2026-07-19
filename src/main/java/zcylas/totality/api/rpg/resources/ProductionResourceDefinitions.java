package zcylas.totality.api.rpg.resources;

import zcylas.totality.api.rpg.resources.external.BreathResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.external.FoodResourceAdapter;
import zcylas.totality.api.rpg.resources.external.HealthResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ManaResourceAdapter;
import zcylas.totality.api.rpg.resources.external.StaminaResourceAdapter;
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
 * <p>Phase 2C adds {@code totality:mana} and {@code totality:stamina} — also {@code EXTERNAL_ADAPTER}-
 * authority and query-only, but <b>transitionally</b> so: both wrap the legacy-authoritative
 * {@link PlayerResourceComponent} store via {@link ManaResourceAdapter}/{@link StaminaResourceAdapter}
 * rather than a truly independent system the way vanilla owns Health/Food/Breath. Both are
 * registered at {@code definitionVersion = 1}; a future migration to {@code GENERIC_COMPONENT}
 * authority requires an explicit version increase and a real migration step, never a silent
 * structural hot-swap of what these two ids mean. Both use {@link ResourceDisplayConversion#IDENTITY}
 * (their legacy storage is already a plain integer, unlike Health's fixed-point float conversion),
 * unlike Health/Food they declare no formatter dependency on each other, and unlike Breath they do
 * declare a full {@link ResourcePresentationDefinition} (canonical §19.8 already treats Mana/Stamina
 * as constant HUD elements, matching Health/Food/Hunger) — but the existing Totality-drawn HUD bars
 * are left completely untouched by this phase; see {@code TOTALITY_RESOURCE_API_PHASE_2C_MANA_STAMINA_ADAPTERS_IMPLEMENTATION_REPORT.md}
 * for why (no generic synchronization exists yet for either resource, so nothing could safely
 * consume this presentation metadata client-side today regardless).
 */
public final class ProductionResourceDefinitions {

    public static void register() {
        registerAdapters();
        registerDefinitions();
        registerFormatters();
    }

    private static void registerAdapters() {
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(HealthResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(FoodResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(BreathResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(ManaResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.register(StaminaResourceAdapter.INSTANCE);
        ExternalPlayerResourceAdapterRegistry.INSTANCE.freeze();
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

        // Mana/Stamina: transitional EXTERNAL_ADAPTER over the legacy PlayerResourceComponent store
        // (see the class Javadoc). definitionVersion is explicit at 1 — a future GENERIC_COMPONENT
        // migration must bump it, never silently redefine what totality:mana/totality:stamina mean.
        // The authored maximum below is each legacy manager's own BASE_MAX_* constant — purely
        // descriptive, exactly like Health/Food/Breath's authoredBaseMaximum; the live query path
        // (ManaResourceAdapter/StaminaResourceAdapter#snapshot) always reads the manager's actual,
        // bonus-inclusive PlayerManaManager.getMaxMana/PlayerStaminaManager.getMaxStamina and never
        // consults this value. No SPENDABLE/RESTORABLE/DIRECT_DRAIN capability — query-only.
        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.MANA, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .externalAdapter(PlayerResourceIds.MANA_ADAPTER)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        .authoredBaseMaximum(zcylas.totality.api.rpg.mana.PlayerManaManager.BASE_MAX_MANA)
                        .capabilities(ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                        .presentation(new ResourcePresentationDefinition(
                                ResourceDisplayConversion.IDENTITY,
                                ResourceDisplayType.BAR,
                                ResourceHudRole.CORE_CONSTANT))
                        .definitionVersion(1)
                        .build());

        PlayerResourceRegistry.INSTANCE.register(
                PlayerResourceDefinition.builder(PlayerResourceIds.STAMINA, ResourceModel.SCALAR)
                        .polarity(ResourcePolarity.HIGH_IS_GOOD)
                        .externalAdapter(PlayerResourceIds.STAMINA_ADAPTER)
                        .unitScale(1)
                        .absoluteMinimum(0)
                        .authoredBaseMaximum(zcylas.totality.api.rpg.stamina.PlayerStaminaManager.BASE_MAX_STAMINA)
                        .capabilities(ResourceCapability.HUD_VISIBLE, ResourceCapability.MENU_VISIBLE)
                        .presentation(new ResourcePresentationDefinition(
                                ResourceDisplayConversion.IDENTITY,
                                ResourceDisplayType.BAR,
                                ResourceHudRole.CORE_CONSTANT))
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
        ResourceValueFormatterRegistry.INSTANCE.freeze();
    }

    private ProductionResourceDefinitions() {}
}
