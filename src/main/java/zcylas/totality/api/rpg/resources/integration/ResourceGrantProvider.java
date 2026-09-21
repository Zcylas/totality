package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * An authoritative owning system's live view of which {@link ResourceGrant}s it currently supplies
 * to a player — canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §16.2, exact signature.
 * "The resource definition must not hardcode checks such as {@code player is Barbarian}.
 * {@code BarbarianClass} owns that decision and supplies the grant."
 *
 * <p>Implementations are expected to derive their answer live from their own existing authority
 * (e.g. a future {@code BarbarianClass} provider reading {@code ClassComponents.get(player)
 * .hasClass(BARBARIAN_ID)}) rather than persisting a second copy of that fact — see canonical
 * §16.3/§16.5. No production provider is registered by this foundation pass; see
 * {@link ResourceGrantRegistry}.
 */
@FunctionalInterface
public interface ResourceGrantProvider {
    Collection<ResourceGrant> getResourceGrants(ServerPlayer player);
}
