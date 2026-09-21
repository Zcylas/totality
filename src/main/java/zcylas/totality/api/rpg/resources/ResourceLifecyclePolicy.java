package zcylas.totality.api.rpg.resources;

import zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization;

import java.util.Objects;

/**
 * Declares what happens to a resource's state on death, across logout/dimension transfer, and
 * when it is first instantiated. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §17.2 for
 * the field shape (field name/order matches canonically) and §16.6 for the
 * {@link ResourceGrantInitialization} contract itself.
 *
 * {@link #DEFAULT} is used by every production definition except Mana/Stamina and Food, which
 * override it with an explicit {@code RESET_TO_MAXIMUM} death policy (Mana/Stamina: Phase 4
 * migration, 2026-09-15; Food: 2026-09-17 migration, matching vanilla's own "hunger always refills
 * to full on respawn" behavior exactly). The four remaining {@code EXTERNAL_ADAPTER}-authority
 * resources (Health, Breath, Spell Slots — Mana/Stamina having exited this list in Phase 4, Rage in
 * Phase 5, Food in the 2026-09-17 migration) never enter {@link PlayerResourceStateComponent}'s live
 * state map at all, so this policy structurally cannot apply to them. {@code totality:rage} (Phase 5 migration, 2026-09-15) is {@code GENERIC_COMPONENT}-authority
 * and uses {@link #DEFAULT} deliberately, not by omission — its {@code KEEP_CURRENT} death policy
 * already matches legacy Rage's own {@code copyFrom} (a blanket preserve-all-pools copy) exactly, so
 * no override is needed. The three {@code GENERIC_COMPONENT}-authority dormant resources added by
 * the dormant Resource Registration pass (Thirst, Sanity, Ki) also use {@link #DEFAULT}, but remain
 * inert: nothing currently instantiates or grants any of them, so this policy — including {@link
 * #DEFAULT}'s {@code AtMaximum} initialization — has no state to act on yet. Registering a
 * definition never itself executes lifecycle behavior; it only becomes behaviorally relevant once a
 * grant provider explicitly instantiates state. Real per-resource policies are chosen when a
 * {@code GENERIC_COMPONENT} resource is actually migrated/granted — see canonical §29.2, which
 * explicitly requires preserving each existing resource's current behavior rather than inventing a
 * new death rule during migration.
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
