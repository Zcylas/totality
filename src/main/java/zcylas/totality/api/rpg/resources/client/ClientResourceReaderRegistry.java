package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of {@link ClientResourceReader} strategies, keyed by Resource id. Mirrors
 * {@code PlayerResourceRegistry}'s duplicate-rejection convention: registration never silently
 * replaces an existing reader.
 */
public final class ClientResourceReaderRegistry {

    private final Map<Identifier, ClientResourceReader> readers = new LinkedHashMap<>();

    public synchronized void register(Identifier resourceId, ClientResourceReader reader) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(reader, "reader");
        if (readers.containsKey(resourceId)) {
            throw new IllegalArgumentException("Duplicate client Resource reader for id: " + resourceId);
        }
        readers.put(resourceId, reader);
    }

    public Optional<ClientResourceReader> get(Identifier resourceId) {
        return Optional.ofNullable(readers.get(resourceId));
    }
}
