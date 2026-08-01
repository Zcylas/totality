package zcylas.totality.api.rpg.resources;

import zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization;

import java.util.Objects;

/**
 * Declares what happens to a resource's state on death, across logout/dimension transfer, and
 * when it is first instantiated. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §17.2 for
 * the field shape (field name/order matches canonically) and §16.6 for the
 * {@link ResourceGrantInitialization} contract itself.
 *
 * {@link #DEFAULT} is still the only instance used by any production definition: none of the seven
 * {@code EXTERNAL_ADAPTER}-authority resources (Health, Food, Breath, Mana, Stamina, Spell Slots,
 * Rage) or the three {@code GENERIC_COMPONENT}-authority dormant resources added by the dormant
 * Resource Registration pass (Thirst, Sanity, Ki) override {@code .lifecycle(...)}. It remains
 * inert for all ten today, for two different reasons: the seven {@code EXTERNAL_ADAPTER} resources
 * never enter {@link PlayerResourceStateComponent}'s live state map at all, so this policy
 * structurally cannot apply to them; the three dormant {@code GENERIC_COMPONENT} resources
 * <i>could</i> enter that map, but nothing currently instantiates or grants any of them, so this
 * policy — including {@link #DEFAULT}'s {@code AtMaximum} initialization — has no state to act on
 * yet. Registering a definition never itself executes lifecycle behavior; it only becomes
 * behaviorally relevant once a future grant provider explicitly instantiates state for one of
 * these three. Real per-resource policies are chosen when a {@code GENERIC_COMPONENT} resource is
 * actually migrated/granted — see canonical §29.2, which explicitly requires preserving each
 * existing resource's current behavior rather than inventing a new death rule during migration.
 */
public record ResourceLifecyclePolicy(
        ResourceDeathPolicy deathPolicy,
        boolean persistThroughLogout,
        boolean persistThroughDimensionChange,
        boolean persistTemporaryModifiers,
        ResourceGrantInitialization initializationPolicy
) {
    public static final ResourceLifecyclePolicy DEFAULT = new ResourceLifecyclePolicy(
            ResourceDeathPolicy.KEEP_CURRENT,
            true,
            true,
            false,
            new ResourceGrantInitialization.AtMaximum()
    );

    public ResourceLifecyclePolicy {
        Objects.requireNonNull(deathPolicy, "deathPolicy");
        Objects.requireNonNull(initializationPolicy, "initializationPolicy");
    }
}
