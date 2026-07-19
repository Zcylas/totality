package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each test constructs its own {@link ExternalPlayerResourceAdapterRegistry} instance rather than
 * using {@link ExternalPlayerResourceAdapterRegistry#INSTANCE}, matching
 * {@code PlayerResourceRegistryTest}'s Phase 1 precedent — isolated registries don't share mutable
 * static state across the whole test JVM.
 */
class ExternalPlayerResourceAdapterRegistryTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    /** A minimal fake adapter — deterministic, ignores the player argument entirely. */
    private static ExternalPlayerResourceAdapter fakeAdapter(Identifier id, long current, long max) {
        return adapterWithSupport(id, current, max, Set.of(ExternalResourceOperationSupport.QUERY));
    }

    private static ExternalPlayerResourceAdapter adapterWithSupport(
            Identifier id, long current, long max, Set<ExternalResourceOperationSupport> support) {
        return new ExternalPlayerResourceAdapter() {
            @Override public Identifier id() { return id; }
            @Override public Optional<ResourceSnapshot> snapshot(Player player, PlayerResourceDefinition definition) {
                return Optional.of(new ResourceSnapshot(id, current, max, 1));
            }
            @Override public Set<ExternalResourceOperationSupport> supportedOperations() {
                return support;
            }
            @Override public ExternalResourceClientMirrorMode clientMirrorMode() {
                return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
            }
        };
    }

    @Test
    void uniqueRegistrationSucceedsAndIsRetrievable() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        ExternalPlayerResourceAdapter adapter = fakeAdapter(id("fake"), 10, 20);

        registry.register(adapter);

        assertTrue(registry.isRegistered(id("fake")));
        assertSame(adapter, registry.get(id("fake")).orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    void duplicateAdapterIdIsRejectedAndDoesNotReplace() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        ExternalPlayerResourceAdapter first = fakeAdapter(id("fake"), 10, 20);
        registry.register(first);

        ExternalPlayerResourceAdapter second = fakeAdapter(id("fake"), 999, 999);

        assertThrows(IllegalArgumentException.class, () -> registry.register(second));
        assertSame(first, registry.get(id("fake")).orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    void nullAdapterIsRejected() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    void malformedAdapterWithNullIdIsRejected() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        ExternalPlayerResourceAdapter malformed = new ExternalPlayerResourceAdapter() {
            @Override public Identifier id() { return null; }
            @Override public Optional<ResourceSnapshot> snapshot(Player player, PlayerResourceDefinition definition) {
                throw new UnsupportedOperationException();
            }
            @Override public Set<ExternalResourceOperationSupport> supportedOperations() {
                return Set.of(ExternalResourceOperationSupport.QUERY);
            }
            @Override public ExternalResourceClientMirrorMode clientMirrorMode() {
                return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
            }
        };
        assertThrows(NullPointerException.class, () -> registry.register(malformed));
    }

    @Test
    void safeLookupForUnknownIdReturnsEmptyNotException() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        assertTrue(registry.get(id("nonexistent")).isEmpty());
        assertFalse(registry.isRegistered(id("nonexistent")));
    }

    @Test
    void freezeBlocksFurtherRegistration() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        registry.register(fakeAdapter(id("before_freeze"), 1, 2));

        assertFalse(registry.isFrozen());
        registry.freeze();
        assertTrue(registry.isFrozen());

        assertThrows(IllegalStateException.class, () -> registry.register(fakeAdapter(id("after_freeze"), 1, 2)));
        assertTrue(registry.isRegistered(id("before_freeze")));
        assertFalse(registry.isRegistered(id("after_freeze")));
    }

    @Test
    void isolatedRegistriesDoNotContaminateOneAnother() {
        ExternalPlayerResourceAdapterRegistry registryA = new ExternalPlayerResourceAdapterRegistry();
        ExternalPlayerResourceAdapterRegistry registryB = new ExternalPlayerResourceAdapterRegistry();

        registryA.register(fakeAdapter(id("only_in_a"), 1, 2));

        assertTrue(registryA.isRegistered(id("only_in_a")));
        assertFalse(registryB.isRegistered(id("only_in_a")));
        assertEquals(1, registryA.size());
        assertEquals(0, registryB.size());

        registryA.freeze();
        assertTrue(registryA.isFrozen());
        assertFalse(registryB.isFrozen());
        // registryB is unaffected and still accepts registration.
        assertDoesNotThrow(() -> registryB.register(fakeAdapter(id("only_in_b"), 1, 2)));
    }

    @Test
    void nullOperationSupportSetIsRejected() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        ExternalPlayerResourceAdapter adapter = adapterWithSupport(id("null_support"), 1, 2, null);
        assertThrows(NullPointerException.class, () -> registry.register(adapter));
    }

    @Test
    void emptyOperationSupportSetIsRejected() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        ExternalPlayerResourceAdapter adapter = adapterWithSupport(id("empty_support"), 1, 2, Set.of());
        assertThrows(IllegalArgumentException.class, () -> registry.register(adapter));
    }

    @Test
    void operationSupportSetContainingANullEntryIsRejected() {
        // Set.of(...) can never contain null, so a null entry can only arise from a malformed
        // adapter using a mutable Set implementation — HashSet permits exactly one null element.
        Set<ExternalResourceOperationSupport> malformed = new HashSet<>();
        malformed.add(ExternalResourceOperationSupport.QUERY);
        malformed.add(null);

        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        ExternalPlayerResourceAdapter adapter = adapterWithSupport(id("null_entry"), 1, 2, malformed);
        assertThrows(IllegalArgumentException.class, () -> registry.register(adapter));
    }

    @Test
    void adapterMissingQuerySupportIsRejectedAtRegistration() {
        // Phase 2A is query-only — every registered adapter must declare QUERY. RESTORE alone
        // (with no QUERY) is exactly the "adapter missing QUERY" case the correction pass names.
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        ExternalPlayerResourceAdapter adapter = adapterWithSupport(
                id("no_query"), 1, 2, Set.of(ExternalResourceOperationSupport.RESTORE));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> registry.register(adapter));
        assertTrue(ex.getMessage().contains("QUERY"));
        assertFalse(registry.isRegistered(id("no_query")));
    }

    @Test
    void nullClientMirrorModeIsRejected() {
        ExternalPlayerResourceAdapterRegistry registry = new ExternalPlayerResourceAdapterRegistry();
        Identifier adapterId = id("null_mirror_mode");
        ExternalPlayerResourceAdapter adapter = new ExternalPlayerResourceAdapter() {
            @Override public Identifier id() { return adapterId; }
            @Override public Optional<ResourceSnapshot> snapshot(Player player, PlayerResourceDefinition definition) {
                throw new UnsupportedOperationException();
            }
            @Override public Set<ExternalResourceOperationSupport> supportedOperations() {
                return Set.of(ExternalResourceOperationSupport.QUERY);
            }
            @Override public ExternalResourceClientMirrorMode clientMirrorMode() { return null; }
        };
        assertThrows(NullPointerException.class, () -> registry.register(adapter));
    }
}
