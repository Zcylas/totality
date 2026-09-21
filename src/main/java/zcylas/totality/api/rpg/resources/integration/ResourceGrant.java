package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * One authoritative source's claim to supply a resource to one player — canonical
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §16.2, exact field shape. Identity for
 * deduplication/tracking purposes is {@code (resourceId, sourceId)} (§16.3), not object identity —
 * two grants with the same pair are the same claim even if reconstructed fresh on every
 * reconciliation call (canonical §16.3: "Derived grants should normally be recalculated from their
 * owning systems," not persisted as a second store of truth).
 */
public record ResourceGrant(
        Identifier resourceId,
        Identifier sourceId,
        ResourceGrantSourceType sourceType,
        ResourceGrantMode mode,
        ResourceGrantInitialization initialization,
        ResourceRemovalPolicy removalPolicy,
        ResourceVisibilityPolicy visibilityPolicy,
        int priority
) {
    public ResourceGrant {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(initialization, "initialization");
        Objects.requireNonNull(removalPolicy, "removalPolicy");
        Objects.requireNonNull(visibilityPolicy, "visibilityPolicy");
    }
}
