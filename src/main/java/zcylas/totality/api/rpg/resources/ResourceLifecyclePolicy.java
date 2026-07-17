package zcylas.totality.api.rpg.resources;

import zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization;

import java.util.Objects;

/**
 * Declares what happens to a resource's state on death, across logout/dimension transfer, and
 * when it is first instantiated. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §17.2 for
 * the field shape (field name/order matches canonically) and §16.6 for the
 * {@link ResourceGrantInitialization} contract itself.
 *
 * {@link #DEFAULT} is the only instance used by this Phase 1 patch (no production resource is
 * registered yet, so no resource-specific policy exists to differentiate). Real per-resource
 * policies are chosen when each resource is actually migrated — see canonical §29.2, which
 * explicitly requires preserving each existing resource's current behavior rather than inventing
 * a new death rule during migration.
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
