package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport;
import zcylas.totality.api.rpg.resources.state.PartitionedResourceState;
import zcylas.totality.api.rpg.resources.state.ScalarResourceState;
import zcylas.totality.networking.resource.ResourceSyncManager;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

/**
 * The one public query <b>and mutation</b> façade for the Generic Player Resource API. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §3 ("Resource service") and §12.1 for the
 * canonical interface shape.
 *
 * <p>Through Phase 2A-3C this class implemented only the query/snapshot path (canonical §27 Phase
 * 1's own scope note: "Do not migrate production resources yet"). This pass (the pre-Phase-4
 * foundation, 2026-09-15) adds the canonical mutation façade — {@link #trySpend}, {@link #drain},
 * {@link #restore}, {@link #set}, {@link #transact} — plus the central {@link #resolveMaximum}
 * path both query and mutation now share (canonical §10.2: "There must be one central resolution
 * path"). See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API_PRE_PHASE4_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-15.md}
 * for exactly what is and is not implemented.
 *
 * <p><b>Mutation only ever applies to {@code GENERIC_COMPONENT}-authority resources today.</b> Every
 * current production {@code EXTERNAL_ADAPTER} (Health, Food, Breath, and the transitional Mana,
 * Stamina, Spell Slots, Rage adapters) declares {@link ExternalResourceOperationSupport#QUERY}
 * only — a mutation attempt against any of them fails structurally with
 * {@link ResourceFailureCode#OPERATION_UNSUPPORTED_BY_AUTHORITY} rather than silently creating
 * shadow {@code GENERIC_COMPONENT} state or bypassing the adapter's real owner. This is expected,
 * not a bug: canonical Phase 4/5/6 is what will eventually let Mana/Stamina/Rage/Spell Slots mutate
 * through this façade, by either migrating their authority or by an adapter opting into
 * {@code RESTORE}/{@code DRAIN}/{@code SET} support. Neither happens in this pass.
 */
public final class PlayerResourceService {

    public static final PlayerResourceService INSTANCE = new PlayerResourceService(
            PlayerResourceRegistry.INSTANCE, ExternalPlayerResourceAdapterRegistry.INSTANCE, ResourceMaximumResolverRegistry.INSTANCE);

    /**
     * The only {@link ResourceQueryFailureReason} values an {@link ExternalPlayerResourceAdapter}
     * is trusted to report on its own authority (correction pass, Phase 2C). Every other reason
     * belongs to the registry/service/generic-state layers — {@code RESOURCE_NOT_REGISTERED},
     * {@code ADAPTER_NOT_REGISTERED}, {@code STATE_NOT_INSTANTIATED}, {@code UNSUPPORTED_MODEL},
     * {@code OPERATION_UNSUPPORTED}, {@code MAXIMUM_UNAVAILABLE}, and {@code CORRUPT_ADAPTER_SNAPSHOT}
     * itself are all things only {@code PlayerResourceService} can legitimately determine (an
     * adapter has no visibility into, for example, whether it is even registered). An adapter
     * returning one of those anyway is itself malformed output — it is not trusted at face value
     * even though its {@code resourceId} might match, and is instead turned into
     * {@code CORRUPT_ADAPTER_SNAPSHOT}. See {@link ExternalPlayerResourceAdapter#snapshot}'s Javadoc.
     */
    private static final Set<ResourceQueryFailureReason> ALLOWED_ADAPTER_FAILURE_REASONS = EnumSet.of(
            ResourceQueryFailureReason.MALFORMED_OWNER_STATE,
            ResourceQueryFailureReason.STATE_UNINITIALIZED,
            ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE);

    private final PlayerResourceRegistry registry;
    private final ExternalPlayerResourceAdapterRegistry adapters;
    private final ResourceMaximumResolverRegistry maximumResolvers;

    /** Convenience overload for the many existing call sites (tests and {@link #INSTANCE}'s own
     *  pre-2026-09-15 shape) that never need a maximum resolver — an isolated, permanently-empty
     *  registry is behaviorally identical to "no resolver registered," so every resource falls back
     *  to its authored base maximum exactly as before this pass. */
    public PlayerResourceService(PlayerResourceRegistry registry, ExternalPlayerResourceAdapterRegistry adapters) {
        this(registry, adapters, new ResourceMaximumResolverRegistry());
    }

    public PlayerResourceService(
            PlayerResourceRegistry registry,
            ExternalPlayerResourceAdapterRegistry adapters,
            ResourceMaximumResolverRegistry maximumResolvers
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.adapters = Objects.requireNonNull(adapters, "adapters");
        this.maximumResolvers = Objects.requireNonNull(maximumResolvers, "maximumResolvers");
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // QUERY (unchanged Phase 2A-3C contract; queryGenericState now routes through the shared
    // resolveMaximum path and supports PARTITIONED_POOL — see that method's own Javadoc)
    // ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * Resolves {@code resourceId} for {@code player}. Works uniformly for a server-side
     * {@code ServerPlayer} or a client-side player object: {@code EXTERNAL_ADAPTER} resources
     * (Health, Food) read values vanilla already keeps synchronized on either side, so this same
     * entry point serves both the future authoritative server callers and the client HUD (see
     * {@link ExternalPlayerResourceAdapter}'s class Javadoc for why that is safe in a query-only
     * phase). {@code GENERIC_COMPONENT} resources can only be resolved for a {@code ServerPlayer},
     * since their state lives in a server-side component; passing a client-side player for one of
     * those returns {@link ResourceQueryFailureReason#STATE_UNAVAILABLE_ON_THIS_SIDE} rather than
     * throwing.
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
        Identifier adapterId = definition.externalAdapterId().orElse(null);
        if (adapterId == null) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.ADAPTER_NOT_REGISTERED, definition.id());
        }
        Optional<ExternalPlayerResourceAdapter> adapterLookup = adapters.get(adapterId);
        if (adapterLookup.isEmpty()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.ADAPTER_NOT_REGISTERED, definition.id());
        }
        ExternalPlayerResourceAdapter adapter = adapterLookup.get();

        if (!adapter.supportedOperations().contains(ExternalResourceOperationSupport.QUERY)) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.OPERATION_UNSUPPORTED, definition.id());
        }

        ResourceQueryResult adapterResult = adapter.snapshot(player, definition);
        if (adapterResult == null) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
        }
        if (adapterResult instanceof ResourceQueryResult.Failure failure) {
            if (!failure.resourceId().equals(definition.id())
                    || !ALLOWED_ADAPTER_FAILURE_REASONS.contains(failure.reason())) {
                return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
            }
            return failure;
        }

        return switch (definition.model()) {
            case SCALAR -> {
                if (!(adapterResult instanceof ResourceQueryResult.Success success)) {
                    yield new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
                }
                yield validateExternalSnapshot(success.snapshot(), definition);
            }
            case PARTITIONED_POOL -> {
                if (!(adapterResult instanceof ResourceQueryResult.PartitionedSuccess partitionedSuccess)) {
                    yield new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
                }
                yield validatePartitionedExternalSnapshot(partitionedSuccess.snapshot(), definition);
            }
        };
    }

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
        return new ResourceQueryResult.Success(snapshot);
    }

    private static ResourceQueryResult validatePartitionedExternalSnapshot(
            PartitionedResourceSnapshot snapshot, PlayerResourceDefinition definition) {
        if (!snapshot.resourceId().equals(definition.id())) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
        }
        if (snapshot.unitScale() != definition.unitScale()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
        }
        for (Map.Entry<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> entry : snapshot.partitions().entrySet()) {
            PartitionedResourceSnapshot.ResourcePartitionSnapshot partition = entry.getValue();
            if (partition.currentUnits() < 0
                    || partition.maximumUnits() < 0
                    || partition.currentUnits() > partition.maximumUnits()) {
                return new ResourceQueryResult.Failure(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT, definition.id());
            }
        }
        return new ResourceQueryResult.PartitionedSuccess(snapshot);
    }

    private ResourceQueryResult queryGeneric(PlayerResourceDefinition definition, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, definition.id());
        }
        return queryGenericState(serverPlayer, definition, ResourceStateComponents.get(serverPlayer));
    }

    /**
     * The pure routing core for {@code GENERIC_COMPONENT} definitions, package-visible so it can
     * be unit-tested directly against a {@link PlayerResourceStateComponent} built with
     * {@code new PlayerResourceStateComponent(null)} — matching Phase 1's own precedent. {@code
     * player} may be {@code null} for a definition with no registered {@link ResourceMaximumResolver}
     * (every resolver-free resolution path never dereferences it) — pass a real {@code ServerPlayer}
     * whenever a resolver is registered for the resource under test.
     *
     * <p>As of this pass, both {@link ResourceModel#SCALAR} and {@link ResourceModel#PARTITIONED_POOL}
     * are supported (previously {@code PARTITIONED_POOL} unconditionally failed
     * {@code UNSUPPORTED_MODEL} — see {@code PlayerResourceStateComponentTest}/
     * {@code PlayerResourceServiceTest}'s updated tests for the closed gap). Both route maximum
     * resolution through {@link #resolveMaximum}, the same central path {@link #trySpend}/
     * {@link #drain}/{@link #restore}/{@link #set} use (canonical §10.2).
     */
    ResourceQueryResult queryGenericState(ServerPlayer player, PlayerResourceDefinition definition, PlayerResourceStateComponent state) {
        return switch (definition.model()) {
            case SCALAR -> queryGenericScalar(player, definition, state);
            case PARTITIONED_POOL -> queryGenericPartitioned(player, definition, state);
        };
    }

    private ResourceQueryResult queryGenericScalar(ServerPlayer player, PlayerResourceDefinition definition, PlayerResourceStateComponent state) {
        Optional<ScalarResourceState> scalar = state.getScalar(definition.id());
        if (scalar.isEmpty()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_NOT_INSTANTIATED, definition.id());
        }
        Optional<ResourceMaximum> maximum = resolveMaximum(player, definition, ResourceResolutionContext.EMPTY);
        if (maximum.isEmpty() || !(maximum.get() instanceof ResourceMaximum.Scalar scalarMax)) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE, definition.id());
        }
        ResourceSnapshot snapshot = new ResourceSnapshot(
                definition.id(), scalar.get().currentUnits(), scalarMax.effectiveUnits(), definition.unitScale());
        return new ResourceQueryResult.Success(snapshot);
    }

    private ResourceQueryResult queryGenericPartitioned(ServerPlayer player, PlayerResourceDefinition definition, PlayerResourceStateComponent state) {
        Optional<PartitionedResourceState> partitioned = state.getPartitioned(definition.id());
        if (partitioned.isEmpty()) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.STATE_NOT_INSTANTIATED, definition.id());
        }
        Optional<ResourceMaximum> maximum = resolveMaximum(player, definition, ResourceResolutionContext.EMPTY);
        if (maximum.isEmpty() || !(maximum.get() instanceof ResourceMaximum.Partitioned partitionedMax)) {
            return new ResourceQueryResult.Failure(ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE, definition.id());
        }
        TreeMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> snapshotPartitions = new TreeMap<>();
        for (Map.Entry<Integer, Long> entry : partitionedMax.effectiveByPartition().entrySet()) {
            int partition = entry.getKey();
            long max = entry.getValue();
            long current = partitioned.get().getCurrent(partition);
            snapshotPartitions.put(partition, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(current, max));
        }
        return new ResourceQueryResult.PartitionedSuccess(
                new PartitionedResourceSnapshot(definition.id(), snapshotPartitions, definition.unitScale()));
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // MAXIMUM RESOLUTION (canonical §10.2 — the one central path query AND mutation both use)
    // ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * The central maximum-resolution path (canonical §10.2, six-step order). Implemented this pass:
     * step 1 (definition-authored base) and step 2 (a registered {@link ResourceMaximumResolver}'s
     * own base/effective computation). Canonical's own numbered order ("1. Definition-authored
     * base, if any. 2. Owning strategy's base maximum.") is a literal priority list, not two
     * unordered alternatives: <b>external-review correction, 2026-09-15</b> — the original pass had
     * a registered resolver win outright over an authored base whenever both existed, the reverse
     * of what §10.2 actually specifies. A {@code SCALAR} definition's authored base now wins
     * whenever present, exactly as step 1 says; the resolver is only consulted when no authored
     * base exists (step 2, the fallback). {@code PARTITIONED_POOL} has no authored-base concept on
     * {@code PlayerResourceDefinition} (a single {@code OptionalLong} cannot represent a per-partition
     * maximum), so it always goes straight to a registered resolver, unchanged from before. Step 5
     * (hard clamp to {@code absoluteMinimum}) is applied to both paths, plus a structural sanity
     * check on a resolver's {@code PARTITIONED_POOL} result (no negative partition maxima — see
     * {@link #isValidPartitionedMaximum}). Steps 3-4 (permanent/temporary maximum modifiers) are
     * <b>not implemented</b> this pass — canonical's own §11 modifier pipeline is deliberately
     * deferred (see the task's "do not build an enormous generic formula language" instruction); a
     * resolver's returned {@code effectiveUnits} is trusted as final. Step 6 (current-value
     * reconciliation) is a separate, explicitly-invoked operation — see {@link #reconcileMaximum} —
     * never a query-time side effect.
     *
     * <p>{@code player} may be {@code null} only when no resolver is registered for {@code
     * definition.id()} (the fallback authored-base path never dereferences it).
     */
    public Optional<ResourceMaximum> resolveMaximum(ServerPlayer player, PlayerResourceDefinition definition, ResourceResolutionContext context) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        if (definition.model() == ResourceModel.SCALAR) {
            OptionalLong authored = definition.authoredBaseMaximum();
            if (authored.isPresent()) {
                long clamped = Math.max(authored.getAsLong(), definition.absoluteMinimum());
                return Optional.of(ResourceMaximum.Scalar.of(clamped));
            }
        }
        Optional<ResourceMaximumResolver> resolver = maximumResolvers.get(definition.id());
        if (resolver.isPresent()) {
            ResourceMaximum resolved = resolver.get().resolve(player, definition, context);
            if (resolved == null) {
                Totality.LOGGER.warn("[ResourceService] maximum resolver for {} returned null", definition.id());
                return Optional.empty();
            }
            return switch (definition.model()) {
                case SCALAR -> {
                    if (!(resolved instanceof ResourceMaximum.Scalar scalar)) yield Optional.empty();
                    long clamped = Math.max(scalar.effectiveUnits(), definition.absoluteMinimum());
                    yield Optional.of(new ResourceMaximum.Scalar(scalar.baseUnits(), clamped, scalar.appliedModifiers()));
                }
                case PARTITIONED_POOL -> {
                    if (!(resolved instanceof ResourceMaximum.Partitioned partitioned)) yield Optional.empty();
                    if (!isValidPartitionedMaximum(partitioned)) {
                        Totality.LOGGER.warn("[ResourceService] maximum resolver for {} returned a structurally invalid "
                                + "partitioned maximum (negative partition value present) — treating as unresolvable", definition.id());
                        yield Optional.empty();
                    }
                    yield Optional.of(partitioned);
                }
            };
        }
        return Optional.empty();
    }

    /** No negative partition maximum, in either the base or effective map — canonical gives no
     *  gameplay rule that could ever produce one, and every mutation path's clamping math
     *  (§12.2-12.4) assumes a non-negative maximum. Generic structural safety only; no Spell-Slot-
     *  specific or other resource-specific rule belongs here. */
    private static boolean isValidPartitionedMaximum(ResourceMaximum.Partitioned partitioned) {
        for (long value : partitioned.baseByPartition().values()) {
            if (value < 0) return false;
        }
        for (long value : partitioned.effectiveByPartition().values()) {
            if (value < 0) return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // MUTATION (canonical §12.1) — GENERIC_COMPONENT only; EXTERNAL_ADAPTER mutation legality is
    // gated purely on the adapter's own supportedOperations() declaration (§6 of the task).
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private static Identifier failId(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private ResourceOperationResult unknownResource(ResourceAmount requested) {
        return new ResourceOperationResult.Failure(
                ResourceFailure.of(ResourceFailureCode.UNKNOWN_RESOURCE, failId("unknown_resource")), requested, 0L);
    }

    private ResourceOperationResult modelMismatch(ResourceAmount requested) {
        return new ResourceOperationResult.Failure(
                ResourceFailure.of(ResourceFailureCode.MODEL_MISMATCH, failId("model_mismatch")), requested, 0L);
    }

    private ResourceOperationResult invalidAmount(ResourceAmount requested) {
        return new ResourceOperationResult.Failure(
                ResourceFailure.of(ResourceFailureCode.INVALID_AMOUNT, failId("invalid_amount")), requested, 0L);
    }

    /** Canonical §7.2/§26.1: "The service must reject any operation that would overflow long." No
     *  dedicated failure code exists for a raw arithmetic overflow; {@code OVERFLOW_NOT_SUPPORTED}
     *  is the closest existing vocabulary (both mean "this value cannot be represented/accepted by
     *  this operation") — a documented mapping decision, not a canonical-mandated one. */
    private ResourceOperationResult overflow(ResourceAmount requested) {
        return new ResourceOperationResult.Failure(
                ResourceFailure.of(ResourceFailureCode.OVERFLOW_NOT_SUPPORTED, failId("arithmetic_overflow")), requested, 0L);
    }

    /** Canonical §16.7/§16.8: dormant (retained-but-ungranted) state must not be usable through
     *  ordinary gameplay mutation — external-review correction, 2026-09-15. {@code set} is exempt
     *  (it already requires a privileged migration/admin cause for every call, a strict superset of
     *  this check — see {@link #set}); {@code trySpend}/{@code drain}/{@code restore} have no such
     *  privileged concept to begin with, so this check is unconditional for them. */
    private ResourceOperationResult inactiveResource(ResourceAmount requested) {
        return new ResourceOperationResult.Failure(
                ResourceFailure.of(ResourceFailureCode.RESOURCE_INACTIVE, failId("resource_inactive")), requested, 0L);
    }

    private ResourceOperationResult authorityUnsupported(ResourceAmount requested) {
        return new ResourceOperationResult.Failure(
                ResourceFailure.of(ResourceFailureCode.OPERATION_UNSUPPORTED_BY_AUTHORITY, failId("operation_unsupported_by_authority")),
                requested, 0L);
    }

    /**
     * Spends {@code cost}. Canonical §12.3: "Partial spending is forbidden by default" — either the
     * full cost commits or nothing does.
     */
    public ResourceOperationResult trySpend(ServerPlayer player, ResourceCost cost, ResourceContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(context, "context");
        Optional<PlayerResourceDefinition> definitionOpt = registry.get(cost.resourceId());
        if (definitionOpt.isEmpty()) return unknownResource(amountOf(cost));
        PlayerResourceDefinition definition = definitionOpt.get();

        if (definition.stateAuthority() == ResourceStateAuthority.EXTERNAL_ADAPTER) {
            // No ExternalResourceOperationSupport.SPEND exists (canonical §6.5's adapter contract
            // only names QUERY/RESTORE/DRAIN/SET), and an affordability-checked, all-or-nothing
            // spend cannot be safely built by delegating to DRAIN's clamp-only semantics without a
            // real adapter to prove the pre-check/commit ordering against. No current adapter
            // declares RESTORE/DRAIN/SET support at all, so this is unreachable in production today
            // regardless; kept as an explicit, honest "unsupported by this authority" rather than an
            // untested delegation guess — see the implementation report's "deferred" section.
            return externalMutationUnsupported(definition, amountOf(cost));
        }
        ResourceOperationResult result = trySpendGenericState(player, definition, cost, context, ResourceStateComponents.get(player));
        if (result.isSuccess()) {
            ResourceSyncManager.markDirty(player.getUUID(), definition.id());
        }
        return result;
    }

    /**
     * The pure {@code GENERIC_COMPONENT} routing core, package-visible so it can be unit-tested
     * directly against a {@link PlayerResourceStateComponent} built with
     * {@code new PlayerResourceStateComponent(null)} — matching {@link #queryGenericState}'s own
     * established precedent. {@code player} may be {@code null} when no
     * {@link ResourceMaximumResolver} is registered for the resource under test.
     */
    ResourceOperationResult trySpendGenericState(
            ServerPlayer player, PlayerResourceDefinition definition, ResourceCost cost, ResourceContext context, PlayerResourceStateComponent state) {
        return switch (cost) {
            case ResourceCost.Scalar scalar -> {
                if (definition.model() != ResourceModel.SCALAR) yield modelMismatch(amountOf(cost));
                if (scalar.amount() < 0) yield invalidAmount(amountOf(cost));
                yield spendGenericScalar(player, definition, scalar.amount(), context, state);
            }
            case ResourceCost.Partitioned partitioned -> {
                if (definition.model() != ResourceModel.PARTITIONED_POOL) yield modelMismatch(amountOf(cost));
                if (partitioned.amount() < 0) yield invalidAmount(amountOf(cost));
                if (partitioned.selectionPolicy() != PartitionSelectionPolicy.EXACT_TIER) {
                    // Canonical §14.2: only EXACT_TIER is implemented for ordinary player spending
                    // this pass — the other three policies are declared (PartitionSelectionPolicy)
                    // but not given selection-search logic here, matching the task's "do not
                    // hardcode Spell Slot rules into the generic service" instruction (a real
                    // AT_LEAST_TIER/eligible-tier search belongs to Phase 6's own caller logic, not
                    // this foundation).
                    yield new ResourceOperationResult.Failure(
                            ResourceFailure.of(ResourceFailureCode.OPERATION_UNSUPPORTED_BY_AUTHORITY, failId("selection_policy_unsupported")),
                            amountOf(cost), 0L);
                }
                yield spendGenericPartitioned(player, definition, partitioned.partition(), partitioned.amount(), context, state);
            }
        };
    }

    private ResourceOperationResult spendGenericScalar(
            ServerPlayer player, PlayerResourceDefinition definition, long amount, ResourceContext context, PlayerResourceStateComponent state) {
        Optional<ScalarResourceState> scalarOpt = state.getScalar(definition.id());
        ResourceAmount requested = ResourceAmount.scalar(definition.id(), amount);
        if (scalarOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, failId("resource_not_instantiated")), requested, 0L);
        }
        if (!scalarOpt.get().active()) {
            return inactiveResource(requested);
        }
        Optional<Long> maxOpt = resolveScalarMaximum(player, definition);
        if (maxOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.MAXIMUM_ZERO, failId("maximum_unresolvable")), requested, 0L);
        }
        ScalarResourceState scalar = scalarOpt.get();
        long current = scalar.currentUnits();
        long max = maxOpt.get();
        if (current < amount) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.insufficientResource(failId("insufficient_resource"), amount, current), requested, 0L);
        }
        ResourceSnapshot before = new ResourceSnapshot(definition.id(), current, max, definition.unitScale());
        scalar.setCurrentUnits(current - amount);
        ResourceSnapshot after = new ResourceSnapshot(definition.id(), current - amount, max, definition.unitScale());
        return new ResourceOperationResult.Success(before, after, requested, amount, 0L);
    }

    private ResourceOperationResult spendGenericPartitioned(
            ServerPlayer player, PlayerResourceDefinition definition, int partition, long amount, ResourceContext context, PlayerResourceStateComponent state) {
        Optional<PartitionedResourceState> poolOpt = state.getPartitioned(definition.id());
        ResourceAmount requested = ResourceAmount.partitioned(definition.id(), partition, amount);
        if (poolOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, failId("resource_not_instantiated")), requested, 0L);
        }
        if (!poolOpt.get().active()) {
            return inactiveResource(requested);
        }
        Optional<ResourceMaximum> maxOpt = resolveMaximum(player, definition, ResourceResolutionContext.EMPTY);
        if (maxOpt.isEmpty() || !(maxOpt.get() instanceof ResourceMaximum.Partitioned partitionedMax)) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.MAXIMUM_ZERO, failId("maximum_unresolvable")), requested, 0L);
        }
        if (!partitionedMax.effectiveByPartition().containsKey(partition)) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.ofPartition(ResourceFailureCode.INVALID_TIER, failId("invalid_tier"), partition), requested, 0L);
        }
        PartitionedResourceState pool = poolOpt.get();
        long current = pool.getCurrent(partition);
        if (current < amount) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.insufficientResource(failId("insufficient_resource"), amount, current), requested, 0L);
        }
        PartitionedResourceSnapshot before = partitionedSnapshotOf(definition, pool, partitionedMax);
        pool.setCurrent(partition, current - amount);
        PartitionedResourceSnapshot after = partitionedSnapshotOf(definition, pool, partitionedMax);
        return new ResourceOperationResult.PartitionedSuccess(before, after, requested, amount, 0L);
    }

    /**
     * External or ongoing loss. Canonical §12.2/§12.3: clamps at minimum, partial application is
     * normal (never forbidden the way an unaffordable spend is).
     */
    public ResourceOperationResult drain(ServerPlayer player, ResourceAmount amount, ResourceContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(context, "context");
        return applyClampedDelta(player, amount, context, -1);
    }

    /** Raises current toward maximum (or overflow, if declared — not implemented this pass; see the report). */
    public ResourceOperationResult restore(ServerPlayer player, ResourceAmount amount, ResourceContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(context, "context");
        return applyClampedDelta(player, amount, context, +1);
    }

    /** {@code sign} is {@code -1} for drain (clamp at minimum), {@code +1} for restore (clamp at maximum). */
    private ResourceOperationResult applyClampedDelta(ServerPlayer player, ResourceAmount amount, ResourceContext context, int sign) {
        Optional<PlayerResourceDefinition> definitionOpt = registry.get(amount.resourceId());
        if (definitionOpt.isEmpty()) return unknownResource(amount);
        PlayerResourceDefinition definition = definitionOpt.get();

        if (definition.stateAuthority() == ResourceStateAuthority.EXTERNAL_ADAPTER) {
            ExternalResourceOperationSupport required = sign > 0
                    ? ExternalResourceOperationSupport.RESTORE : ExternalResourceOperationSupport.DRAIN;
            return dispatchExternalMutation(definition, required, amount, adapter -> sign > 0
                    ? adapter.restore(player, definition, amount, context)
                    : adapter.drain(player, definition, amount, context));
        }
        ResourceOperationResult result = applyClampedDeltaGenericState(
                player, definition, amount, sign, ResourceStateComponents.get(player));
        if (result.isSuccess()) {
            ResourceSyncManager.markDirty(player.getUUID(), definition.id());
        }
        return result;
    }

    /**
     * The pure {@code GENERIC_COMPONENT} routing core for {@link #drain}/{@link #restore},
     * package-visible for the same reason as {@link #trySpendGenericState}.
     */
    ResourceOperationResult applyClampedDeltaGenericState(
            ServerPlayer player, PlayerResourceDefinition definition, ResourceAmount amount, int sign, PlayerResourceStateComponent state) {
        if (amount.units() < 0) return invalidAmount(amount);
        if (definition.model() == ResourceModel.SCALAR) {
            if (amount.partition().isPresent()) return modelMismatch(amount);
            return applyClampedScalarDelta(player, definition, amount.units(), sign, amount, state);
        } else {
            if (amount.partition().isEmpty()) return modelMismatch(amount);
            return applyClampedPartitionedDelta(player, definition, amount.partition().getAsInt(), amount.units(), sign, amount, state);
        }
    }

    private ResourceOperationResult applyClampedScalarDelta(
            ServerPlayer player, PlayerResourceDefinition definition, long delta, int sign, ResourceAmount requested, PlayerResourceStateComponent state) {
        Optional<ScalarResourceState> scalarOpt = state.getScalar(definition.id());
        if (scalarOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, failId("resource_not_instantiated")), requested, 0L);
        }
        if (!scalarOpt.get().active()) {
            return inactiveResource(requested);
        }
        Optional<Long> maxOpt = resolveScalarMaximum(player, definition);
        if (maxOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.MAXIMUM_ZERO, failId("maximum_unresolvable")), requested, 0L);
        }
        ScalarResourceState scalar = scalarOpt.get();
        long current = scalar.currentUnits();
        long max = maxOpt.get();
        long floor = definition.absoluteMinimum();
        long rawTarget;
        try {
            rawTarget = sign > 0 ? Math.addExact(current, delta) : Math.subtractExact(current, delta);
        } catch (ArithmeticException overflowEx) {
            return overflow(requested);
        }
        long clamped = Math.max(floor, Math.min(max, rawTarget));
        long applied = Math.abs(clamped - current);

        ResourceSnapshot before = new ResourceSnapshot(definition.id(), current, max, definition.unitScale());
        scalar.setCurrentUnits(clamped);
        ResourceSnapshot after = new ResourceSnapshot(definition.id(), clamped, max, definition.unitScale());
        return new ResourceOperationResult.Success(before, after, requested, applied, 0L);
    }

    private ResourceOperationResult applyClampedPartitionedDelta(
            ServerPlayer player, PlayerResourceDefinition definition, int partition, long delta, int sign, ResourceAmount requested, PlayerResourceStateComponent state) {
        Optional<PartitionedResourceState> poolOpt = state.getPartitioned(definition.id());
        if (poolOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, failId("resource_not_instantiated")), requested, 0L);
        }
        if (!poolOpt.get().active()) {
            return inactiveResource(requested);
        }
        Optional<ResourceMaximum> maxOpt = resolveMaximum(player, definition, ResourceResolutionContext.EMPTY);
        if (maxOpt.isEmpty() || !(maxOpt.get() instanceof ResourceMaximum.Partitioned partitionedMax)) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.MAXIMUM_ZERO, failId("maximum_unresolvable")), requested, 0L);
        }
        Long max = partitionedMax.effectiveByPartition().get(partition);
        if (max == null) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.ofPartition(ResourceFailureCode.INVALID_TIER, failId("invalid_tier"), partition), requested, 0L);
        }
        PartitionedResourceState pool = poolOpt.get();
        long current = pool.getCurrent(partition);
        long floor = definition.absoluteMinimum();
        long rawTarget;
        try {
            rawTarget = sign > 0 ? Math.addExact(current, delta) : Math.subtractExact(current, delta);
        } catch (ArithmeticException overflowEx) {
            return overflow(requested);
        }
        long clamped = Math.max(floor, Math.min(max, rawTarget));
        long applied = Math.abs(clamped - current);

        PartitionedResourceSnapshot before = partitionedSnapshotOf(definition, pool, partitionedMax);
        pool.setCurrent(partition, clamped);
        PartitionedResourceSnapshot after = partitionedSnapshotOf(definition, pool, partitionedMax);
        return new ResourceOperationResult.PartitionedSuccess(before, after, requested, applied, 0L);
    }

    /**
     * Privileged correction/migration/admin/initialization write — canonical §12.2. Gated on
     * {@code context}'s cause being one of a small privileged allow-list ({@link
     * ResourceContext.CauseTypes#MIGRATION}, {@link ResourceContext.CauseTypes#ADMIN_COMMAND}) so
     * ordinary gameplay code cannot reach it merely by calling this method — canonical never spells
     * out the exact enforcement mechanism, so this is a reasoned, narrow, and documented choice (see
     * the implementation report) rather than a canonical requirement transcribed verbatim.
     */
    public ResourceOperationResult set(ServerPlayer player, ResourceTarget target, ResourceContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(context, "context");

        ResourceAmount requestedAsAmount = target.partition().isPresent()
                ? ResourceAmount.partitioned(target.resourceId(), target.partition().getAsInt(), target.absoluteUnits())
                : ResourceAmount.scalar(target.resourceId(), target.absoluteUnits());

        if (!isPrivilegedCause(context)) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.BLOCKED_BY_RULE, failId("set_requires_privileged_cause")),
                    requestedAsAmount, 0L);
        }
        Optional<PlayerResourceDefinition> definitionOpt = registry.get(target.resourceId());
        if (definitionOpt.isEmpty()) return unknownResource(requestedAsAmount);
        PlayerResourceDefinition definition = definitionOpt.get();
        if (definition.stateAuthority() == ResourceStateAuthority.EXTERNAL_ADAPTER) {
            return dispatchExternalMutation(definition, ExternalResourceOperationSupport.SET, requestedAsAmount,
                    adapter -> adapter.set(player, definition, target, context));
        }

        ResourceOperationResult result = setGenericState(player, definition, target, requestedAsAmount, ResourceStateComponents.get(player));
        if (result.isSuccess()) {
            ResourceSyncManager.markDirty(player.getUUID(), definition.id());
        }
        return result;
    }

    /** Package-visible (not {@code private}) so {@code PlayerResourceServiceMutationTest} can pin this exact
     *  allow-list directly, without needing a real component-attached {@code ServerPlayer} to reach it via {@link #set}. */
    static boolean isPrivilegedCause(ResourceContext context) {
        Identifier type = context.cause().type();
        return type.equals(ResourceContext.CauseTypes.MIGRATION) || type.equals(ResourceContext.CauseTypes.ADMIN_COMMAND);
    }

    /**
     * The pure {@code GENERIC_COMPONENT} routing core for {@link #set}, package-visible for the
     * same reason as {@link #trySpendGenericState}. Does <b>not</b> re-check the privileged-cause
     * gate — {@link #set} already enforced it before this is reached; a test exercising this
     * directly is testing state-mutation behavior, not the authorization gate itself (see
     * {@code PlayerResourceServiceMutationTest}'s "unauthorized ordinary gameplay use is rejected"
     * case, which calls the public {@link #set} instead, for that).
     */
    ResourceOperationResult setGenericState(
            ServerPlayer player, PlayerResourceDefinition definition, ResourceTarget target, ResourceAmount requestedAsAmount, PlayerResourceStateComponent state) {
        if (definition.model() == ResourceModel.SCALAR) {
            if (target.partition().isPresent()) return modelMismatch(requestedAsAmount);
            return setGenericScalar(player, definition, target.absoluteUnits(), requestedAsAmount, state);
        } else {
            if (target.partition().isEmpty()) return modelMismatch(requestedAsAmount);
            return setGenericPartitioned(player, definition, target.partition().getAsInt(), target.absoluteUnits(), requestedAsAmount, state);
        }
    }

    private ResourceOperationResult setGenericScalar(
            ServerPlayer player, PlayerResourceDefinition definition, long absoluteUnits, ResourceAmount requested, PlayerResourceStateComponent state) {
        if (absoluteUnits < 0) return invalidAmount(requested);
        Optional<ScalarResourceState> scalarOpt = state.getScalar(definition.id());
        if (scalarOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, failId("resource_not_instantiated")), requested, 0L);
        }
        Optional<Long> maxOpt = resolveScalarMaximum(player, definition);
        if (maxOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.MAXIMUM_ZERO, failId("maximum_unresolvable")), requested, 0L);
        }
        ScalarResourceState scalar = scalarOpt.get();
        long current = scalar.currentUnits();
        long max = maxOpt.get();
        long clamped = Math.max(definition.absoluteMinimum(), Math.min(max, absoluteUnits));

        ResourceSnapshot before = new ResourceSnapshot(definition.id(), current, max, definition.unitScale());
        scalar.setCurrentUnits(clamped);
        ResourceSnapshot after = new ResourceSnapshot(definition.id(), clamped, max, definition.unitScale());
        return new ResourceOperationResult.Success(before, after, requested, Math.abs(clamped - current), 0L);
    }

    private ResourceOperationResult setGenericPartitioned(
            ServerPlayer player, PlayerResourceDefinition definition, int partition, long absoluteUnits, ResourceAmount requested, PlayerResourceStateComponent state) {
        if (absoluteUnits < 0) return invalidAmount(requested);
        Optional<PartitionedResourceState> poolOpt = state.getPartitioned(definition.id());
        if (poolOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, failId("resource_not_instantiated")), requested, 0L);
        }
        Optional<ResourceMaximum> maxOpt = resolveMaximum(player, definition, ResourceResolutionContext.EMPTY);
        if (maxOpt.isEmpty() || !(maxOpt.get() instanceof ResourceMaximum.Partitioned partitionedMax)) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.MAXIMUM_ZERO, failId("maximum_unresolvable")), requested, 0L);
        }
        Long max = partitionedMax.effectiveByPartition().get(partition);
        if (max == null) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.ofPartition(ResourceFailureCode.INVALID_TIER, failId("invalid_tier"), partition), requested, 0L);
        }
        PartitionedResourceState pool = poolOpt.get();
        long current = pool.getCurrent(partition);
        long clamped = Math.max(definition.absoluteMinimum(), Math.min(max, absoluteUnits));

        PartitionedResourceSnapshot before = partitionedSnapshotOf(definition, pool, partitionedMax);
        pool.setCurrent(partition, clamped);
        PartitionedResourceSnapshot after = partitionedSnapshotOf(definition, pool, partitionedMax);
        return new ResourceOperationResult.PartitionedSuccess(before, after, requested, Math.abs(clamped - current), 0L);
    }

    private ResourceOperationResult externalMutationUnsupported(PlayerResourceDefinition definition, ResourceAmount requested) {
        Optional<ExternalPlayerResourceAdapter> adapter = definition.externalAdapterId().flatMap(adapters::get);
        // Canonical §6/§13.3: an EXTERNAL_ADAPTER resource may only mutate through its adapter's own
        // declared support — every current adapter declares QUERY only, so this always fails today.
        // No shadow GENERIC_COMPONENT state is ever created as a fallback (§6 of the task).
        if (adapter.isEmpty()) {
            return unknownResource(requested);
        }
        return authorityUnsupported(requested);
    }

    /**
     * Routes a restore/drain/set call to its {@code EXTERNAL_ADAPTER} resource's adapter, only if
     * that adapter declares {@code required} support (§6 of the task: "a mutation is only legal if
     * its adapter explicitly supports that operation... unsupported operations must fail with a
     * structured Resource failure"). Every current production adapter declares
     * {@link ExternalResourceOperationSupport#QUERY} only, so {@code invoke} is never actually
     * called in production today — this exists so a future opt-in adapter is reachable without
     * revisiting this dispatch method.
     */
    private ResourceOperationResult dispatchExternalMutation(
            PlayerResourceDefinition definition,
            ExternalResourceOperationSupport required,
            ResourceAmount requested,
            java.util.function.Function<ExternalPlayerResourceAdapter, ResourceOperationResult> invoke
    ) {
        Optional<ExternalPlayerResourceAdapter> adapter = definition.externalAdapterId().flatMap(adapters::get);
        if (adapter.isEmpty()) return unknownResource(requested);
        if (!adapter.get().supportedOperations().contains(required)) return authorityUnsupported(requested);
        ResourceOperationResult result = invoke.apply(adapter.get());
        return result != null ? result : new ResourceOperationResult.Failure(
                ResourceFailure.of(ResourceFailureCode.INTERNAL_ERROR, failId("corrupt_adapter_mutation_result")), requested, 0L);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // TRANSACTIONS (canonical §13) — resource-only, ATOMIC_ALL_OR_NOTHING, sequential same-
    // resource application via an in-memory working ledger validated before any real state write.
    // ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * Applies every operation in {@code transaction} atomically: either all commit or none do
     * (canonical §13.1/§13.2). Two operations targeting the same resource apply in submission order
     * against a working ledger seeded from real current state — nothing is written to real state
     * until every operation has validated successfully. Canonical §13.3: a transaction containing
     * any {@code EXTERNAL_ADAPTER} resource fails {@code ATOMIC_OPERATION_UNSUPPORTED} before any
     * mutation, since no {@code TransactionalExternalResourceAdapter} contract exists yet.
     */
    public ResourceTransactionResult transact(ServerPlayer player, ResourceTransaction transaction, ResourceContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(context, "context");
        ResourceTransactionResult result = transactGenericState(player, transaction, context, ResourceStateComponents.get(player));
        if (result.success()) {
            // All resources touched by a successful transaction share one commit — mark every one
            // dirty so the existing Phase 3A sync path picks up every changed value, not just the
            // last one written.
            for (Identifier resourceId : distinctResourceIds(transaction)) {
                ResourceSyncManager.markDirty(player.getUUID(), resourceId);
            }
        }
        return result;
    }

    private static java.util.Set<Identifier> distinctResourceIds(ResourceTransaction transaction) {
        java.util.Set<Identifier> ids = new java.util.LinkedHashSet<>();
        for (ResourceOperation op : transaction.operations()) {
            ids.add(resourceIdOf(op));
        }
        return ids;
    }

    /**
     * The pure routing core for {@link #transact}, package-visible for the same reason as
     * {@link #trySpendGenericState}.
     */
    ResourceTransactionResult transactGenericState(
            ServerPlayer player, ResourceTransaction transaction, ResourceContext context, PlayerResourceStateComponent state) {
        List<ResourceOperation> operations = transaction.operations();
        boolean hasSet = operations.stream().anyMatch(op -> op instanceof ResourceOperation.Set);
        if (hasSet && !isPrivilegedCause(context)) {
            return ResourceTransactionResult.failedGeneral(
                    ResourceFailure.of(ResourceFailureCode.BLOCKED_BY_RULE, failId("set_requires_privileged_cause")), 0L);
        }

        // Validation phase: resolve every definition/state/maximum up front, and simulate every
        // operation sequentially against a working ledger (never real state) so same-resource
        // operations compose deterministically and an unaffordable step anywhere fails the whole
        // transaction before any commit.
        Map<Identifier, Ledger> ledgers = new LinkedHashMap<>();
        for (int i = 0; i < operations.size(); i++) {
            ResourceOperation op = operations.get(i);
            Identifier resourceId = resourceIdOf(op);
            Optional<PlayerResourceDefinition> definitionOpt = registry.get(resourceId);
            if (definitionOpt.isEmpty()) {
                return ResourceTransactionResult.failed(
                        ResourceFailure.of(ResourceFailureCode.UNKNOWN_RESOURCE, failId("unknown_resource")), i, 0L);
            }
            PlayerResourceDefinition definition = definitionOpt.get();
            if (definition.stateAuthority() == ResourceStateAuthority.EXTERNAL_ADAPTER) {
                return ResourceTransactionResult.failed(
                        ResourceFailure.of(ResourceFailureCode.ATOMIC_OPERATION_UNSUPPORTED, failId("atomic_operation_unsupported")), i, 0L);
            }
            // Same dormant-state safety rule as trySpend/drain/restore (external-review correction,
            // 2026-09-15): a transaction whose cause is not already privileged (checked above, via
            // the Set-requires-privileged-cause gate) must not touch dormant state either. A
            // privileged transaction — one that legitimately contains a Set operation — may touch
            // dormant state, matching set()'s own migration/admin authority.
            if (state.hasState(resourceId) && !state.isActive(resourceId) && !isPrivilegedCause(context)) {
                return ResourceTransactionResult.failed(
                        ResourceFailure.of(ResourceFailureCode.RESOURCE_INACTIVE, failId("resource_inactive")), i, 0L);
            }
            Ledger ledger = ledgers.computeIfAbsent(resourceId, id -> Ledger.load(player, definition, this, state));
            if (ledger == null) {
                return ResourceTransactionResult.failed(
                        ResourceFailure.of(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, failId("resource_not_instantiated")), i, 0L);
            }
            Optional<ResourceFailure> failure = ledger.simulate(op);
            if (failure.isPresent()) {
                return ResourceTransactionResult.failed(failure.get(), i, 0L);
            }
        }

        // Commit phase: every operation validated — write every ledger's final value to real state.
        for (Ledger ledger : ledgers.values()) {
            ledger.commit(state);
        }
        return ResourceTransactionResult.success(0L);
    }

    private static Identifier resourceIdOf(ResourceOperation op) {
        return switch (op) {
            case ResourceOperation.Spend s -> s.cost().resourceId();
            case ResourceOperation.Drain d -> d.amount().resourceId();
            case ResourceOperation.Restore r -> r.amount().resourceId();
            case ResourceOperation.Set s -> s.target().resourceId();
        };
    }

    /** A single resource's working value during transaction validation — never written until commit. */
    private static final class Ledger {
        final PlayerResourceDefinition definition;
        final long floor;
        final long max;
        long working;

        private Ledger(PlayerResourceDefinition definition, long floor, long max, long working) {
            this.definition = definition;
            this.floor = floor;
            this.max = max;
            this.working = working;
        }

        static Ledger load(ServerPlayer player, PlayerResourceDefinition definition, PlayerResourceService service, PlayerResourceStateComponent state) {
            // This foundation's transaction support is scalar-only (canonical §13.2 gives no
            // partitioned-transaction example, and no test/production resource needs one yet).
            if (definition.model() != ResourceModel.SCALAR) return null;
            Optional<ScalarResourceState> scalarOpt = state.getScalar(definition.id());
            if (scalarOpt.isEmpty()) return null;
            Optional<Long> maxOpt = service.resolveScalarMaximum(player, definition);
            if (maxOpt.isEmpty()) return null;
            return new Ledger(definition, definition.absoluteMinimum(), maxOpt.get(), scalarOpt.get().currentUnits());
        }

        private static Optional<ResourceFailure> of(ResourceFailureCode code, String path) {
            return Optional.of(ResourceFailure.of(code, Identifier.fromNamespaceAndPath("totality", path)));
        }

        Optional<ResourceFailure> simulate(ResourceOperation op) {
            return switch (op) {
                case ResourceOperation.Spend s -> {
                    // This ledger is scalar-only (see load()); a Partitioned cost here is a genuine
                    // shape mismatch, not something to blindly cast — external-review correction,
                    // 2026-09-15 (the previous unchecked cast could throw ClassCastException).
                    if (!(s.cost() instanceof ResourceCost.Scalar scalarCost)) {
                        yield of(ResourceFailureCode.MODEL_MISMATCH, "model_mismatch");
                    }
                    long amount = scalarCost.amount();
                    if (amount < 0) yield of(ResourceFailureCode.INVALID_AMOUNT, "invalid_amount");
                    if (working < amount) {
                        yield Optional.of(ResourceFailure.insufficientResource(
                                Identifier.fromNamespaceAndPath("totality", "insufficient_resource"), amount, working));
                    }
                    working -= amount; // safe: 0 <= amount <= working, so the result is in [0, working]
                    yield Optional.empty();
                }
                case ResourceOperation.Drain d -> {
                    if (d.amount().partition().isPresent()) {
                        yield of(ResourceFailureCode.MODEL_MISMATCH, "model_mismatch");
                    }
                    long units = d.amount().units();
                    // Matches applyClampedDeltaGenericState's single-operation validation: a
                    // negative Drain amount used to silently invert into a Restore inside a
                    // transaction (external-review correction, 2026-09-15).
                    if (units < 0) yield of(ResourceFailureCode.INVALID_AMOUNT, "invalid_amount");
                    long target;
                    try {
                        target = Math.subtractExact(working, units);
                    } catch (ArithmeticException overflowEx) {
                        yield of(ResourceFailureCode.OVERFLOW_NOT_SUPPORTED, "arithmetic_overflow");
                    }
                    working = Math.max(floor, target);
                    yield Optional.empty();
                }
                case ResourceOperation.Restore r -> {
                    if (r.amount().partition().isPresent()) {
                        yield of(ResourceFailureCode.MODEL_MISMATCH, "model_mismatch");
                    }
                    long units = r.amount().units();
                    if (units < 0) yield of(ResourceFailureCode.INVALID_AMOUNT, "invalid_amount");
                    long target;
                    try {
                        target = Math.addExact(working, units);
                    } catch (ArithmeticException overflowEx) {
                        yield of(ResourceFailureCode.OVERFLOW_NOT_SUPPORTED, "arithmetic_overflow");
                    }
                    working = Math.min(max, target);
                    yield Optional.empty();
                }
                case ResourceOperation.Set s -> {
                    if (s.target().partition().isPresent()) {
                        yield of(ResourceFailureCode.MODEL_MISMATCH, "model_mismatch");
                    }
                    // Matches setGenericScalar's own validation, which this ledger path bypassed
                    // entirely before (external-review correction, 2026-09-15).
                    if (s.target().absoluteUnits() < 0) yield of(ResourceFailureCode.INVALID_AMOUNT, "invalid_amount");
                    working = Math.max(floor, Math.min(max, s.target().absoluteUnits()));
                    yield Optional.empty();
                }
            };
        }

        void commit(PlayerResourceStateComponent state) {
            state.getScalar(definition.id()).ifPresent(scalar -> scalar.setCurrentUnits(working));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // MAXIMUM-CHANGE RECONCILIATION (canonical §10.2 step 6 / §11.4)
    // ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * Applies {@link MaximumChangePolicy} to reconcile {@code current} against a maximum that has
     * just changed from {@code previousMaximumUnits} to the freshly-resolved value — canonical
     * §10.2 step 6 / §11.4. This is a separate, explicitly-invoked operation, never a query-time
     * side effect (a query never mutates state). The caller supplies {@code previousMaximumUnits}
     * because maximum is "not normally persisted" (canonical §10.1) — nothing stores a prior
     * resolved value for this method to diff against on its own; the caller (whichever system just
     * changed the input a resolver depends on, e.g. a level-up handler) is the one that knows a
     * change just happened and is best positioned to have captured the old value immediately before
     * making it. See the implementation report's "deferred/ambiguity" section for this reasoning.
     */
    public ResourceOperationResult reconcileMaximum(
            ServerPlayer player, Identifier resourceId, long previousMaximumUnits, MaximumChangePolicy policy, ResourceContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(context, "context");
        ResourceAmount requested = ResourceAmount.scalar(resourceId, 0);

        Optional<PlayerResourceDefinition> definitionOpt = registry.get(resourceId);
        if (definitionOpt.isEmpty()) return unknownResource(requested);
        PlayerResourceDefinition definition = definitionOpt.get();
        if (definition.stateAuthority() != ResourceStateAuthority.GENERIC_COMPONENT || definition.model() != ResourceModel.SCALAR) {
            return modelMismatch(requested);
        }
        ResourceOperationResult result = reconcileMaximumGenericState(
                player, resourceId, definition, previousMaximumUnits, policy, requested, ResourceStateComponents.get(player));
        if (result.isSuccess()) {
            ResourceSyncManager.markDirty(player.getUUID(), resourceId);
        }
        return result;
    }

    /**
     * The pure routing core for {@link #reconcileMaximum}, package-visible for the same reason as
     * {@link #trySpendGenericState}.
     */
    ResourceOperationResult reconcileMaximumGenericState(
            ServerPlayer player, Identifier resourceId, PlayerResourceDefinition definition, long previousMaximumUnits,
            MaximumChangePolicy policy, ResourceAmount requested, PlayerResourceStateComponent state) {
        Optional<ScalarResourceState> scalarOpt = state.getScalar(resourceId);
        if (scalarOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, failId("resource_not_instantiated")), requested, 0L);
        }
        Optional<Long> newMaxOpt = resolveScalarMaximum(player, definition);
        if (newMaxOpt.isEmpty()) {
            return new ResourceOperationResult.Failure(
                    ResourceFailure.of(ResourceFailureCode.MAXIMUM_ZERO, failId("maximum_unresolvable")), requested, 0L);
        }
        ScalarResourceState scalar = scalarOpt.get();
        long current = scalar.currentUnits();
        long newMax = newMaxOpt.get();
        long floor = definition.absoluteMinimum();

        long reconciled;
        long newOverflowUnits = 0L;
        try {
            reconciled = policy.reconcileCurrent(current, previousMaximumUnits, newMax, floor);
            if (policy == MaximumChangePolicy.ALLOW_OVERFLOW && current > newMax
                    && definition.capabilities().contains(ResourceCapability.OVERFLOW)) {
                newOverflowUnits = Math.addExact(scalar.overflowUnits(), Math.subtractExact(current, newMax));
            }
        } catch (ArithmeticException overflowEx) {
            return overflow(requested);
        }

        ResourceSnapshot before = new ResourceSnapshot(resourceId, current, previousMaximumUnits, definition.unitScale());
        if (policy == MaximumChangePolicy.ALLOW_OVERFLOW && current > newMax
                && definition.capabilities().contains(ResourceCapability.OVERFLOW)) {
            scalar.setOverflowUnits(newOverflowUnits);
            scalar.setCurrentUnits(newMax);
        } else if (policy == MaximumChangePolicy.ALLOW_OVERFLOW) {
            // OVERFLOW capability not declared — canonical §12.4: "No resource may remain invisibly
            // above maximum without the OVERFLOW capability," so this falls back to the safe default.
            scalar.setCurrentUnits(Math.min(current, newMax));
        } else {
            scalar.setCurrentUnits(reconciled);
        }
        ResourceSnapshot after = new ResourceSnapshot(resourceId, scalar.currentUnits(), newMax, definition.unitScale());
        return new ResourceOperationResult.Success(before, after, requested, Math.abs(after.currentUnits() - current), 0L);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // Shared helpers
    // ═══════════════════════════════════════════════════════════════════════════════════════

    private Optional<Long> resolveScalarMaximum(ServerPlayer player, PlayerResourceDefinition definition) {
        return resolveMaximum(player, definition, ResourceResolutionContext.EMPTY)
                .filter(m -> m instanceof ResourceMaximum.Scalar)
                .map(m -> ((ResourceMaximum.Scalar) m).effectiveUnits());
    }

    private static ResourceAmount amountOf(ResourceCost cost) {
        return switch (cost) {
            case ResourceCost.Scalar scalar -> ResourceAmount.scalar(scalar.resourceId(), scalar.amount());
            case ResourceCost.Partitioned partitioned ->
                    ResourceAmount.partitioned(partitioned.resourceId(), partitioned.partition(), partitioned.amount());
        };
    }

    private static PartitionedResourceSnapshot partitionedSnapshotOf(
            PlayerResourceDefinition definition, PartitionedResourceState pool, ResourceMaximum.Partitioned max) {
        TreeMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> partitions = new TreeMap<>();
        for (Map.Entry<Integer, Long> entry : max.effectiveByPartition().entrySet()) {
            int partition = entry.getKey();
            partitions.put(partition, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(pool.getCurrent(partition), entry.getValue()));
        }
        return new PartitionedResourceSnapshot(definition.id(), partitions, definition.unitScale());
    }
}
