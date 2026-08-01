package zcylas.totality.networking.resource;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.PlayerResourceService;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceStateAuthority;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.external.ExternalResourceClientMirrorMode;
import zcylas.totality.api.rpg.resources.sync.PlayerResourceSyncState;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceSyncProtocol;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The single server-side orchestration boundary for the Phase 3A generic Resource synchronization
 * contract: per-player dirty tracking, coalescing, revisioning, and packet dispatch. Runs entirely
 * in parallel with every existing Mana/Stamina/SpellSlot/Rage sync path — see
 * {@code TOTALITY_RESOURCE_API_PHASE_3A_SYNCHRONIZATION_CONTRACT_IMPLEMENTATION_REPORT.md} for the
 * full contract this implements.
 *
 * <p>Only resources whose generic-sync eligibility is {@code true} (see
 * {@link #isEligibleForGenericSync}) ever appear in a full/delta packet — an
 * {@link ExternalResourceClientMirrorMode#NATIVE_SYNCHRONIZATION} resource (Health/Food/Breath)
 * already has a reliable client-side value through vanilla's own sync and is deliberately excluded
 * here, so native and generic synchronization are never double-applied (canonical §18.3).
 */
public final class ResourceSyncManager {

    private static final Map<UUID, PlayerResourceSyncState> STATES = new HashMap<>();

    private ResourceSyncManager() {}

    public static void markDirty(UUID playerId, Identifier resourceId) {
        stateFor(playerId).markDirty(resourceId);
    }

    /** Alias for {@link #markDirty} — used by call sites whose intent is "this resource became
     *  unavailable/invalid" rather than "this resource's value changed". Both funnel through the
     *  same requery-and-diff path at flush time, since only a fresh query can determine whether the
     *  resource is now actually absent. */
    public static void invalidate(UUID playerId, Identifier resourceId) {
        markDirty(playerId, resourceId);
    }

    public static void scheduleFullSnapshot(UUID playerId) {
        stateFor(playerId).scheduleFullSnapshot();
    }

    public static void onDisconnect(UUID playerId) {
        STATES.remove(playerId);
    }

    private static PlayerResourceSyncState stateFor(UUID playerId) {
        return STATES.computeIfAbsent(playerId, id -> new PlayerResourceSyncState());
    }

    /** Called once per server tick, after ordinary server logic — see
     *  {@code ResourceSyncServerTick}. Cheap no-op for every player with no pending work: one
     *  hashmap lookup plus an empty-set check, no resource queries. */
    public static void flush(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerResourceSyncState state = STATES.get(player.getUUID());
            if (state == null || !state.hasPendingWork()) {
                continue;
            }
            if (state.isFullSnapshotPending()) {
                sendFull(player, state);
            } else {
                sendDelta(player, state);
            }
        }
    }

    private static void sendFull(ServerPlayer player, PlayerResourceSyncState state) {
        Map<Identifier, PlayerResourceSyncState.ResourceOutcome> outcomes = queryAllEligible(player);
        PlayerResourceSyncState.FullBatch batch = state.applyFull(outcomes);
        state.bumpRevision();
        ServerPlayNetworking.send(player, new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, state.revision(), batch.scalars(), batch.partitioned()));
    }

    private static void sendDelta(ServerPlayer player, PlayerResourceSyncState state) {
        Map<Identifier, PlayerResourceSyncState.ResourceOutcome> outcomes = new LinkedHashMap<>();
        for (Identifier id : state.dirtyIds()) {
            // A dirty id that is unregistered or ineligible for generic sync (e.g. Health/Food/
            // Breath, which vanilla already reliably mirrors) is simply left out of the queried
            // outcomes map: computeDeltaAndApply below treats a missing outcome as "nothing to do
            // for this id" and still clears it from the dirty set, so it never lingers as pending
            // forever, but it can never be queried or upserted through the delta path either — the
            // exact same eligibility rule the full-snapshot path already applies (see
            // isEligibleForGenericSync).
            if (!isEligibleForGenericSync(id)) {
                continue;
            }
            outcomes.put(id, queryOutcome(player, id));
        }
        long base = state.revision();
        PlayerResourceSyncState.DeltaBatch batch = state.computeDeltaAndApply(outcomes);
        if (batch.isEmpty()) {
            // A dirty mark whose fresh value matched what was already synchronized — no packet, no
            // revision bump (canonical §18.3 / acceptance test "unchanged snapshots do not generate
            // unnecessary deltas").
            return;
        }
        state.bumpRevision();
        ServerPlayNetworking.send(player, new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, base, state.revision(),
                batch.upsertScalars(), batch.upsertPartitioned(), batch.invalidated()));
    }

    private static Map<Identifier, PlayerResourceSyncState.ResourceOutcome> queryAllEligible(ServerPlayer player) {
        Map<Identifier, PlayerResourceSyncState.ResourceOutcome> result = new LinkedHashMap<>();
        for (PlayerResourceDefinition definition : PlayerResourceRegistry.INSTANCE.all()) {
            if (!isEligibleForGenericSync(definition)) {
                continue;
            }
            result.put(definition.id(), queryOutcome(player, definition.id()));
        }
        return result;
    }

    /**
     * The single shared eligibility gate for every generic-sync path (full snapshot, delta upsert,
     * and — by extension, since an ineligible id is never queried/upserted at all — invalidation).
     * An id with no registered {@link PlayerResourceDefinition} at all (unknown, or e.g. a stale
     * dirty mark left over from a since-unregistered definition) is discarded safely here rather than
     * ever reaching a query. Package-private (rather than {@code private}) solely so
     * {@code ResourceSyncManagerEligibilityTest} can exercise this decision directly against the
     * real production registry — it needs no {@code ServerPlayer}, unlike the query paths that call
     * it.
     */
    static boolean isEligibleForGenericSync(Identifier resourceId) {
        return PlayerResourceRegistry.INSTANCE.get(resourceId)
                .map(ResourceSyncManager::isEligibleForGenericSync)
                .orElse(false);
    }

    /**
     * A resource participates in the generic sync packets only when nothing else already reliably
     * mirrors it to the client. {@code GENERIC_COMPONENT} resources always qualify — as of the
     * dormant Resource Registration pass, three production definitions actually use this authority
     * ({@code totality:thirst}/{@code totality:sanity}/{@code totality:ki}), but all three remain
     * dormant and uninstantiated (no grant provider exists for any of them yet): a definition being
     * eligible here does not mean a value is available to synchronize — an uninstantiated
     * {@code GENERIC_COMPONENT} resource queries to {@code STATE_NOT_INSTANTIATED}, which becomes an
     * {@code AbsentOutcome} and is omitted from the wire entirely (see {@code PlayerResourceSyncState
     * #applyFull}), never a fabricated snapshot. {@code EXTERNAL_ADAPTER} resources qualify unless
     * their adapter declares {@link ExternalResourceClientMirrorMode#NATIVE_SYNCHRONIZATION}.
     * Resources currently declaring {@link ExternalResourceClientMirrorMode#LEGACY_BESPOKE_SYNCHRONIZATION}
     * (Mana, Stamina, spell slots, Rage) DO qualify — the legacy bespoke packet remains their real
     * client source of truth in Phase 3A, but the new generic packets are sent in parallel so the
     * contract is exercised end-to-end ahead of the Phase 3B migration.
     */
    private static boolean isEligibleForGenericSync(PlayerResourceDefinition definition) {
        if (definition.stateAuthority() != ResourceStateAuthority.EXTERNAL_ADAPTER) {
            return true;
        }
        Optional<Identifier> adapterId = definition.externalAdapterId();
        if (adapterId.isEmpty()) {
            return false;
        }
        Optional<ExternalPlayerResourceAdapter> adapter =
                ExternalPlayerResourceAdapterRegistry.INSTANCE.get(adapterId.get());
        return adapter.isPresent() && adapter.get().clientMirrorMode() != ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
    }

    private static PlayerResourceSyncState.ResourceOutcome queryOutcome(ServerPlayer player, Identifier resourceId) {
        ResourceQueryResult result = PlayerResourceService.INSTANCE.query(player, resourceId);
        return switch (result) {
            case ResourceQueryResult.Success success ->
                    new PlayerResourceSyncState.ScalarOutcome(ResourceScalarWireSnapshot.from(success.snapshot()));
            case ResourceQueryResult.PartitionedSuccess partitionedSuccess ->
                    new PlayerResourceSyncState.PartitionedOutcome(
                            ResourcePartitionedWireSnapshot.from(partitionedSuccess.snapshot()));
            case ResourceQueryResult.Failure failure -> {
                logIfUnexpected(failure);
                yield new PlayerResourceSyncState.AbsentOutcome();
            }
        };
    }

    /** STATE_UNINITIALIZED/STATE_NOT_INSTANTIATED are routine (e.g. every non-Barbarian querying
     *  Rage) and never logged. Every other failure reason indicates a real registry/adapter problem
     *  and is logged at DEBUG with only the resource id and reason enum — never adapter-internal
     *  exception text. */
    private static void logIfUnexpected(ResourceQueryResult.Failure failure) {
        if (failure.reason() == ResourceQueryFailureReason.STATE_UNINITIALIZED
                || failure.reason() == ResourceQueryFailureReason.STATE_NOT_INSTANTIATED) {
            return;
        }
        Totality.LOGGER.debug("Resource sync omitted {} : {}", failure.resourceId(), failure.reason());
    }
}
