package zcylas.totality.api.rpg.resources.client;

import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;

/**
 * A client-side reader strategy for one or more Resources — the client-side counterpart to
 * {@code ExternalPlayerResourceAdapter}. Implementations are expected to be pure with respect to
 * Minecraft/client state: they should read through an injected access interface
 * ({@link NativeResourceAccess}, {@link GenericSyncResourceAccess}) rather than touching
 * {@code Minecraft.getInstance()} or client networking directly, so reader logic remains
 * unit-testable without launching a client. See {@code NativeClientResourceReader} and
 * {@code GenericSyncClientResourceReader}.
 */
public interface ClientResourceReader {

    /**
     * Answers a query for {@code definition}. Must never throw for an ordinary "no value available"
     * case — return a structured {@link ClientResourceQueryResult.Unavailable} instead. Must never
     * fabricate a numeric value for a Resource this reader cannot currently answer for.
     */
    ClientResourceQueryResult query(PlayerResourceDefinition definition);
}
