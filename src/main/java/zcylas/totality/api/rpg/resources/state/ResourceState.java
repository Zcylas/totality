package zcylas.totality.api.rpg.resources.state;

import zcylas.totality.api.rpg.resources.ResourceModel;

/**
 * A player's mutable state for one internally backed ({@code GENERIC_COMPONENT}-authority)
 * resource. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §6.
 *
 * Deliberately excludes state for a resource whose definition is unknown or model-mismatched —
 * that case is preserved separately as {@link OrphanedResourceState} so it never crashes gameplay
 * lookups (canonical §5.4).
 */
public sealed interface ResourceState permits ScalarResourceState, PartitionedResourceState {
    ResourceModel model();
}
