package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport;
import zcylas.totality.api.rpg.resources.state.ScalarResourceState;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The one public query façade for the Generic Player Resource API. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §3 ("Resource service").
 *
 * Phase 2A implements <b>only</b> the query/snapshot path (canonical §27 Phase 1's own scope note:
 * "Do not migrate production resources yet" plus the Phase 2A task's explicit exclusion of
 * restore/drain/spend/set/transactions). {@link #query} is guaranteed to:
 * <ul>
 *     <li>Resolve the registered definition, routing {@code EXTERNAL_ADAPTER} definitions to their
 *         adapter and {@code GENERIC_COMPONENT} definitions to {@link PlayerResourceStateComponent}.</li>
 *     <li>Never instantiate state as a side effect (a miss returns {@link ResourceQueryFailureReason#STATE_NOT_INSTANTIATED},
 *         it does not call {@code instantiateScalar}).</li>
 *     <li>Never write NBT, never send a packet, never mutate Health or Food.</li>
 *     <li>Return a structured {@link ResourceQueryResult.Failure} for an unknown resource,
 *         unavailable state, an adapter that does not declare {@code QUERY} support, an
 *         unresolvable maximum, or a structurally malformed adapter snapshot — never a null
 *         pointer and never a fabricated zero/invented snapshot.</li>
 * </ul>
 */
public final class PlayerResourceService {

    public static final PlayerResourceService INSTANCE =
            new PlayerResourceService(PlayerResourceRegistry.INSTANCE, ExternalPlayerResourceAdapterRegistry.INSTANCE);

    private final PlayerResourceRegistry registry;
    private final ExternalPlayerResourceAdapterRegistry adapters;

    public PlayerResourceService(PlayerResourceRegistry registry, ExternalPlayerResourceAdapterRegistry adapters) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.adapters = Objects.requireNonNull(adapters, "adapters");
    }

    /**
     * Resolves {@code resourceId} for {@code player}. Works uniformly for a server-side
     * {@code ServerPlayer} or a client-side player object: {@code EXTERNAL_ADAPTER} resources
     * (Health, Food) read values vanilla already keeps synchronized on either side, so this same
     * entry point serves both the future authoritative server callers and the client HUD (see
     * {@link ExternalPlayerResourceAdapter}'s class Javadoc for why that is safe in a query-only
     * phase). {@code GENERIC_COMPONENT} resources can only be resolved for a {@code ServerPlayer},
     * since their state lives in a server-side component; passing a client-side player for one of
     * those returns {@link ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE} rather than
     * throwing (Phase 2A registers no {@code GENERIC_COMPONENT} resource in production, but the
     * routing contract must still hold for future resources).
     */
    public ResourceQueryResult query(Player player, Identifier resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        Optional<PlayerResourceDefinition> definition = registry.get(resourceId);
        if (definition.isEmpty()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.RESOURCE_NOT_REGISTERED, resourceId);
        }
        return queryDefinition(definition.get(), player);
    }

    private ResourceQueryResult queryDefinition(PlayerResourceDefinition definition, Player player) {
        return switch (definition.stateAuthority()) {
            case EXTERNAL_ADAPTER -> queryExternal(definition, player);
            case GENERIC_COMPONENT -> queryGeneric(definition, player);
        };
    }

    private ResourceQueryResult queryExternal(PlayerResourceDefinition definition, Player player) {
        // ResourceSnapshot has no partition representation — only ResourceModel.SCALAR is
        // representable through the external query path (mirrors queryGenericState's own
        // UNSUPPORTED_MODEL check for the generic side).
        if (definition.model() != ResourceModel.SCALAR) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.UNSUPPORTED_MODEL, definition.id());
        }

        Identifier adapterId = definition.externalAdapterId().orElse(null);
        if (adapterId == null) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.ADAPTER_NOT_REGISTERED, definition.id());
        }
        Optional<ExternalPlayerResourceAdapter> adapterLookup = adapters.get(adapterId);
        if (adapterLookup.isEmpty()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.ADAPTER_NOT_REGISTERED, definition.id());
        }
        ExternalPlayerResourceAdapter adapter = adapterLookup.get();

        // Defensive: registry-time registration already requires QUERY support (see
        // ExternalPlayerResourceAdapterRegistry#register), but this does not call the adapter on
        // that trust alone — an unsupported operation must fail structurally rather than execute.
        if (!adapter.supportedOperations().contains(ExternalResourceOperationSupport.QUERY)) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.OPERATION_UNSUPPORTED, definition.id());
        }

        Optional<ResourceSnapshot> snapshot = adapter.snapshot(player, definition);
        if (snapshot == null) {
            // A misbehaving adapter returned a null Optional reference instead of a real one —
            // distinct from the adapter correctly returning Optional.empty() below. Guarded
            // defensively (never expected from a well-formed adapter) rather than trusted blindly.
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
        }
        if (snapshot.isEmpty()) {
            // The adapter inspected its owner and explicitly declined — a typed signal, not a
            // null-as-control-flow shortcut. See ExternalPlayerResourceAdapter#snapshot's Javadoc.
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, definition.id());
        }
        return validateExternalSnapshot(snapshot.get(), definition);
    }

    /**
     * Verifies an adapter's returned snapshot is structurally consistent with the definition that
     * was queried before it is ever treated as a successful result. A malformed snapshot is never
     * silently rewritten or reattributed to a different resource — it fails structurally, naming
     * the resource whose adapter produced it.
     */
    private static ResourceQueryResult validateExternalSnapshot(ResourceSnapshot snapshot, PlayerResourceDefinition definition) {
        if (!snapshot.resourceId().equals(definition.id())) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
        }
        if (snapshot.unitScale() != definition.unitScale()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
        }
        if (snapshot.maximumUnits() < definition.absoluteMinimum()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
        }
        // currentUnits()/maximumUnits() are already primitive `long`s — always representable by
        // definition; no separate numeric-range check is meaningful at this layer. Any conversion
        // overflow is caught earlier, at the adapter's own unit-conversion boundary (see
        // HealthResourceAdapter#toUnits), before a ResourceSnapshot can even be constructed.
        return new ResourceQueryResult.Success(snapshot);
    }

    private ResourceQueryResult queryGeneric(PlayerResourceDefinition definition, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, definition.id());
        }
        return queryGenericState(definition, ResourceStateComponents.get(serverPlayer));
    }

    /**
     * The pure routing core for {@code GENERIC_COMPONENT} definitions, package-visible so it can
     * be unit-tested directly against a {@link PlayerResourceStateComponent} built with
     * {@code new PlayerResourceStateComponent(null)} — matching Phase 1's own precedent for
     * testing this component without a real {@code ServerPlayer} — rather than requiring the
     * Minecraft runtime just to exercise the routing/failure-reason logic.
     */
    ResourceQueryResult queryGenericState(PlayerResourceDefinition definition, PlayerResourceStateComponent state) {
        if (definition.model() != ResourceModel.SCALAR) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.UNSUPPORTED_MODEL, definition.id());
        }
        Optional<ScalarResourceState> scalar = state.getScalar(definition.id());
        if (scalar.isEmpty()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_NOT_INSTANTIATED, definition.id());
        }
        // No ResourceMaximumResolver framework exists yet (Phase 1/2A scope), so the only
        // currently-legitimate source of a generic resource's maximum is an authored one declared
        // directly on the definition. Falling back to absoluteMinimum here would fabricate a
        // maximum (commonly 0) that no owning system ever actually declared — a query must fail
        // structurally instead of returning a successful but invented snapshot.
        OptionalLong authoredMaximum = definition.authoredBaseMaximum();
        if (authoredMaximum.isEmpty()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE, definition.id());
        }
        ResourceSnapshot snapshot = new ResourceSnapshot(
                definition.id(), scalar.get().currentUnits(), authoredMaximum.getAsLong(), definition.unitScale());
        return new ResourceQueryResult.Success(snapshot);
    }
}
