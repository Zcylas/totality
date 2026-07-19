package zcylas.totality.api.rpg.resources;

import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.external.FoodResourceAdapter;
import zcylas.totality.api.rpg.resources.external.HealthResourceAdapter;
import zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion;
import zcylas.totality.api.rpg.resources.presentation.ResourceDisplayType;
import zcylas.totality.api.rpg.resources.presentation.ResourceHudRole;
import zcylas.totality.api.rpg.resources.presentation.ResourcePresentationDefinition;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatter;
import zcylas.totality.api.rpg.resources.presentation.ResourceValueFormatterRegistry;

/**
 * Registers the Generic Player Resource API's Phase 2A production content — exactly
 * {@code totality:health} and {@code totality:food}, both {@code EXTERNAL_ADAPTER}-authority
 * query-only resources — and freezes every registry involved at a deterministic point during mod
 * initialization, before any player can join. See {@code Totality.registerApi()}, which calls
 * {@link #register()} once, after every other Phase 1 foundation class has had a chance to load.
 *
 * Nothing here creates player state: registering a definition and its adapter is pure metadata
 * registration (canonical §4.4 — a definition existing does not mean any player owns it), and
 * Health/Food never gain {@link PlayerResourceStateComponent} entries because their authority is
 * {@code EXTERNAL_ADAPTER}, not {@code GENERIC_COMPONENT} (see {@link PlayerResourceStateComponent#instantiateScalar}'s
 * rejection of external definitions).
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

        PlayerResourceRegistry.INSTANCE.freeze(ExternalPlayerResourceAdapterRegistry.INSTANCE);
    }

    private static void registerFormatters() {
        ResourceValueFormatterRegistry.INSTANCE.register(
                ResourceValueFormatter.ofConversion(PlayerResourceIds.HEALTH, ResourceDisplayConversion.HEALTH_FOOD));
        ResourceValueFormatterRegistry.INSTANCE.register(
                ResourceValueFormatter.ofConversion(PlayerResourceIds.FOOD, ResourceDisplayConversion.HEALTH_FOOD));
        ResourceValueFormatterRegistry.INSTANCE.freeze();
    }

    private ProductionResourceDefinitions() {}
}
