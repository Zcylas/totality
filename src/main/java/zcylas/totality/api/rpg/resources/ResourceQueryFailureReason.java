package zcylas.totality.api.rpg.resources;

/**
 * Why {@link PlayerResourceService#query} could not produce a {@link ResourceSnapshot}. A query
 * never fabricates a zero snapshot to paper over one of these — see canonical §5.4/§21 and the
 * Phase 2A task's "Unknown resources and unavailable state must produce an explicit structured
 * failure or safe empty result... not a null pointer or fabricated zero snapshot."
 */
public enum ResourceQueryFailureReason {
    /** No {@link PlayerResourceDefinition} is registered under the requested id. */
    RESOURCE_NOT_REGISTERED,

    /**
     * The definition is {@code EXTERNAL_ADAPTER}-authority but no adapter is registered under its
     * declared {@code externalAdapterId}. Should not occur for any definition that passed
     * {@link PlayerResourceRegistry#freeze(zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry)}
     * validation; guarded defensively anyway rather than trusting that invariant blindly.
     */
    ADAPTER_NOT_REGISTERED,

    /**
     * The definition is {@code GENERIC_COMPONENT}-authority, but the player has no instantiated
     * state for it yet (canonical §4.4: a read-only query must not silently instantiate one).
     */
    STATE_NOT_INSTANTIATED,

    /**
     * The definition is {@code GENERIC_COMPONENT}-authority, but generic player state can only be
     * resolved for a {@code ServerPlayer} (it lives in a server-side component); the caller passed
     * a client-side {@link net.minecraft.world.entity.player.Player}.
     */
    STATE_UNAVAILABLE_ON_THIS_SIDE,

    /**
     * The definition is {@code GENERIC_COMPONENT}-authority with {@link ResourceModel#PARTITIONED_POOL},
     * which has no {@link ResourceSnapshot} representation yet — Phase 2A's query path only
     * resolves {@link ResourceModel#SCALAR} generic state (no partitioned resource is registered
     * by this phase; see the Phase 2A task's scope exclusions).
     */
    UNSUPPORTED_MODEL,

    /**
     * The resolved adapter does not declare {@link zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport#QUERY}
     * support. {@link PlayerResourceRegistry#freeze(zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry)}
     * registration already requires every adapter to declare {@code QUERY}, so this should not
     * occur in practice — checked defensively at query time anyway rather than trusting that
     * invariant blindly (correction pass: "make PlayerResourceService check operation support
     * before calling an adapter"). Deliberately distinct from {@link #ADAPTER_NOT_REGISTERED} —
     * the adapter exists and is registered, it simply does not support this operation.
     */
    OPERATION_UNSUPPORTED,

    /**
     * The definition is {@code GENERIC_COMPONENT}-authority and its state is instantiated, but it
     * has no resolvable maximum ({@code authoredBaseMaximum} is absent and no maximum-resolver
     * framework exists yet). A query must not fabricate a maximum (e.g. by falling back to
     * {@code absoluteMinimum}, which commonly produces a fabricated {@code 0}) — it fails
     * structurally instead until a real {@code ResourceMaximumResolver} phase exists.
     */
    MAXIMUM_UNAVAILABLE,

    /**
     * An {@code EXTERNAL_ADAPTER}'s {@code snapshot(...)} returned a present but structurally
     * invalid result — a mismatched resource id, a mismatched unit scale, or a maximum below the
     * definition's {@code absoluteMinimum}. The query never silently rewrites a malformed snapshot
     * to "fix" it or reattributes it to a different resource; it fails structurally and names the
     * adapter at fault so the bug is visible instead of silently corrupting a display or a future
     * decision made from the snapshot. Distinct from {@link #MALFORMED_OWNER_STATE}: this reason
     * means the adapter tried to answer and got it wrong; that one means the adapter looked at its
     * owner and correctly declined to answer at all.
     */
    CORRUPT_ADAPTER_SNAPSHOT,

    /**
     * An {@code EXTERNAL_ADAPTER} inspected its authoritative owner and found a state it cannot
     * represent as a valid snapshot (e.g. vanilla reporting a non-positive maximum for a resource
     * that requires a positive one) — introduced in Phase 2B for {@code BreathResourceAdapter},
     * whose owner's maximum ({@code Entity.getMaxAirSupply()}) is a virtual method future entity
     * types could theoretically override into something degenerate. The adapter returns
     * {@link java.util.Optional#empty()} from {@code snapshot(...)} rather than fabricating a
     * clamped-to-nothing or otherwise invented value; this reason is never used as ordinary control
     * flow — it fires only when the owner's actual state is genuinely unrepresentable.
     */
    MALFORMED_OWNER_STATE
}
