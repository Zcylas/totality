package zcylas.totality.api.shop;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import zcylas.totality.init.ModTags;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lazily creates and holds one {@link InMemoryMerchantRuntime} per shop identifier — the
 * temporary stand-in for per-entity merchant state (design document Section 11 step 3). Keyed by
 * {@code shopId} because that is the only merchant identity that exists before
 * {@code ProvisionerNpcEntity} (a later phase) gives individual NPCs their own runtime.
 *
 * <p>Every merchant currently uses the same accepted-tag policy
 * ({@link ModTags#PROVISIONER_BUYS}) because only the generic Provisioner archetype exists so
 * far — a real per-merchant-type policy is future work, not invented here.
 *
 * <p>Scoped by {@link MinecraftServer} (economy hardening pass, Part 3): the outer map is keyed
 * by the logical server instance, and every entry for a server is dropped when it stops. Without
 * this, a client that closes one integrated world and opens another in the same JVM would see
 * merchant Credits left over from the previous world — a real leak, since this runtime is
 * otherwise process-lifetime state.
 */
public final class MerchantRuntimeRegistry {

    /** Section 2's PLAYTEST VALUE baseline for the first Provisioner. */
    public static final long BASE_STARTING_CREDITS = 300;

    private static final Map<MinecraftServer, Map<Identifier, InMemoryMerchantRuntime>> MERCHANTS = new HashMap<>();

    private MerchantRuntimeRegistry() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(MerchantRuntimeRegistry::clearForServer);
    }

    public static MerchantRuntime getOrCreate(MinecraftServer server, Identifier shopId) {
        return MERCHANTS.computeIfAbsent(server, s -> new HashMap<>())
                .computeIfAbsent(shopId, id -> new InMemoryMerchantRuntime(
                        id, BASE_STARTING_CREDITS, Set.of(ModTags.PROVISIONER_BUYS)));
    }

    /** Non-creating lookup — used by verification to prove a check never created or touched
     *  production merchant state for a given server/shop pair. */
    public static Optional<MerchantRuntime> peek(MinecraftServer server, Identifier shopId) {
        Map<Identifier, InMemoryMerchantRuntime> perServer = MERCHANTS.get(server);
        return perServer == null ? Optional.empty() : Optional.ofNullable(perServer.get(shopId));
    }

    /** Package-visible so verification can exercise the exact lifecycle behavior the
     *  {@code SERVER_STOPPED} hook triggers, without needing to actually stop a server mid-test. */
    static void clearForServer(MinecraftServer server) {
        MERCHANTS.remove(server);
    }
}
