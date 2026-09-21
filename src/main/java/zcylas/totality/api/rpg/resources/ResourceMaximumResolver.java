package zcylas.totality.api.rpg.resources;

import net.minecraft.server.level.ServerPlayer;

/**
 * Owner-registered strategy that derives a resource's structural maximum from code-owned gameplay
 * logic instead of a fixed {@code authoredBaseMaximum} — canonical
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §9.1, exact signature. "The resolver returns
 * base/effective structural maximum data before generic modifiers. The owning system registers the
 * resolver." See {@link ResourceMaximumResolverRegistry} for registration and
 * {@link PlayerResourceService}'s central resolution path (canonical §10.2: "There must be one
 * central resolution path").
 */
@FunctionalInterface
public interface ResourceMaximumResolver {
    ResourceMaximum resolve(ServerPlayer player, PlayerResourceDefinition definition, ResourceResolutionContext context);
}
