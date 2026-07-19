package zcylas.totality.api.rpg.resources;

import zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization;

import java.util.Objects;

/**
 * Declares what happens to a resource's state on death, across logout/dimension transfer, and
 * when it is first instantiated. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §17.2 for
 * the field shape (field name/order matches canonically) and §16.6 for the
 * {@link ResourceGrantInitialization} contract itself.
 *
 * {@link #DEFAULT} is still the only instance used by any production definition as of Phase 2A:
 * {@code totality:health}/{@code totality:food} both use it (neither builder call overrides
 * {@code .lifecycle(...)}), but it is effectively inert for either — both are
 * {@code EXTERNAL_ADAPTER}-authority, so they never enter {@link PlayerResourceStateComponent}'s
 * live state map at all, and this policy only governs that map's death/logout/dimension-change
 * copy behavior. Real per-resource policies are chosen when each {@code GENERIC_COMPONENT}
 * resource is actually migrated — see canonical §29.2, which explicitly requires preserving each
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
