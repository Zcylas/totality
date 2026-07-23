package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.ResourceModel;

import java.util.Objects;
import java.util.Optional;

/**
 * The one public, presentation-only client Resource query façade (Phase 3B-1). See
 * {@code TOTALITY_RESOURCE_API_PHASE_3B_CLIENT_VIEW_AND_PARITY_READINESS.md} §5/§6.
 *
 * <p>Uses the canonical {@link PlayerResourceRegistry}/{@link PlayerResourceDefinition} metadata to
 * resolve a Resource's model and existence — never a second, hardcoded database of Resource shape.
 * Delegates the actual value lookup to a reader-strategy registry ({@link ClientResourceReaderRegistry}):
 * a native reader for Health/Food/Breath, a generic-synchronized reader for Mana/Stamina/spell
 * slots/Rage (see {@code zcylas.totality.client.resource.TotalityClientResourceReaders} for
 * production wiring).
 *
 * <p>This class cannot mutate Resource authority or Phase 3A synchronization state: it never calls
 * an apply/clear/resync-request method on anything, and every reader it can be wired to is
 * constrained by the read-only {@link NativeResourceAccess}/{@link GenericSyncResourceAccess}
 * contracts, neither of which declares a mutating method.
 */
public final class ClientResourceService {

    public static final ClientResourceService INSTANCE =
            new ClientResourceService(PlayerResourceRegistry.INSTANCE, new ClientResourceReaderRegistry());

    private final PlayerResourceRegistry registry;
    private final ClientResourceReaderRegistry readers;

    public ClientResourceService(PlayerResourceRegistry registry, ClientResourceReaderRegistry readers) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.readers = Objects.requireNonNull(readers, "readers");
    }

    /** Registers {@code reader} for {@code resourceId}. Throws on a duplicate id — never replaces silently. */
    public void registerReader(Identifier resourceId, ClientResourceReader reader) {
        readers.register(resourceId, reader);
    }

    /**
     * General query: resolves the canonical model from the registry and returns the matching scalar
     * or partitioned result, or a structured {@link ClientResourceQueryResult.Unavailable}.
     */
    public ClientResourceQueryResult query(Identifier resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        Optional<PlayerResourceDefinition> definitionLookup = registry.get(resourceId);
        if (definitionLookup.isEmpty()) {
            return ClientResourceQueryResult.unavailable(resourceId, ClientResourceUnavailableReason.RESOURCE_UNREGISTERED);
        }
        PlayerResourceDefinition definition = definitionLookup.get();
        Optional<ClientResourceReader> readerLookup = readers.get(resourceId);
        if (readerLookup.isEmpty()) {
            return ClientResourceQueryResult.unavailable(resourceId, ClientResourceUnavailableReason.CLIENT_SOURCE_NOT_CONFIGURED);
        }
        ClientResourceQueryResult result = readerLookup.get().query(definition);
        return validateShape(result, definition);
    }

    /**
     * Typed scalar query. A Resource whose canonical model is {@code PARTITIONED_POOL} returns
     * {@code MODEL_MISMATCH} immediately — checked against the definition before any reader ever
     * runs, since asking for the wrong shape is a caller error independent of synchronization state.
     */
    public ClientResourceQueryResult queryScalar(Identifier resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        Optional<PlayerResourceDefinition> definitionLookup = registry.get(resourceId);
        if (definitionLookup.isPresent() && definitionLookup.get().model() != ResourceModel.SCALAR) {
            return ClientResourceQueryResult.unavailable(resourceId, ClientResourceUnavailableReason.MODEL_MISMATCH);
        }
        ClientResourceQueryResult result = query(resourceId);
        if (result instanceof ClientResourceQueryResult.Partitioned) {
            return ClientResourceQueryResult.unavailable(resourceId, ClientResourceUnavailableReason.MODEL_MISMATCH);
        }
        return result;
    }

    /** Typed partitioned query — the {@link #queryScalar} counterpart. */
    public ClientResourceQueryResult queryPartitioned(Identifier resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        Optional<PlayerResourceDefinition> definitionLookup = registry.get(resourceId);
        if (definitionLookup.isPresent() && definitionLookup.get().model() != ResourceModel.PARTITIONED_POOL) {
            return ClientResourceQueryResult.unavailable(resourceId, ClientResourceUnavailableReason.MODEL_MISMATCH);
        }
        ClientResourceQueryResult result = query(resourceId);
        if (result instanceof ClientResourceQueryResult.Scalar) {
            return ClientResourceQueryResult.unavailable(resourceId, ClientResourceUnavailableReason.MODEL_MISMATCH);
        }
        return result;
    }

    /**
     * Defense-in-depth: verifies a reader's returned shape actually matches the definition it was
     * asked about, exactly like {@code PlayerResourceService}'s own adapter-output validation. A
     * reader is never trusted to have answered correctly on its own say-so alone.
     */
    private static ClientResourceQueryResult validateShape(ClientResourceQueryResult result, PlayerResourceDefinition definition) {
        if (result == null) {
            return ClientResourceQueryResult.unavailable(definition.id(), ClientResourceUnavailableReason.CLIENT_SOURCE_NOT_CONFIGURED);
        }
        if (!result.resourceId().equals(definition.id())) {
            return ClientResourceQueryResult.unavailable(definition.id(), ClientResourceUnavailableReason.MODEL_MISMATCH);
        }
        if (result instanceof ClientResourceQueryResult.Scalar && definition.model() != ResourceModel.SCALAR) {
            return ClientResourceQueryResult.unavailable(definition.id(), ClientResourceUnavailableReason.MODEL_MISMATCH);
        }
        if (result instanceof ClientResourceQueryResult.Partitioned && definition.model() != ResourceModel.PARTITIONED_POOL) {
            return ClientResourceQueryResult.unavailable(definition.id(), ClientResourceUnavailableReason.MODEL_MISMATCH);
        }
        return result;
    }
}
