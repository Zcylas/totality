package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.resources.*;

import java.util.*;

/**
 * The canonical §16.6/§16.7/§16.12 grant reconciliation engine: "Which resources should this
 * player actually possess right now, and from which authoritative sources?" — turned into real
 * {@link PlayerResourceStateComponent} instantiation/removal.
 *
 * <p><b>Deliberately stateless between calls.</b> Canonical §16.3: "Derived grants should normally
 * be recalculated from their owning systems... Persist a grant only when its source is itself a
 * durable independent grant." Rather than persisting a second "which sources currently own this
 * resource" store, this reconciler recomputes the full live grant set from every registered
 * {@link ResourceGrantProvider} on every call and compares it against
 * {@link PlayerResourceStateComponent#instantiatedResourceIds()} (which resources currently
 * <i>have</i> state) — a resource newly present in the grant set that lacks state is a first
 * acquisition (instantiate); a resource with state that is no longer present in the grant set has
 * had its last grant source removed (apply the resource's declared {@link ResourceRemovalPolicy}).
 * This makes reconciliation naturally idempotent (canonical §16.12: "must be idempotent and
 * batched... must not repeatedly initialize, refill, delete, and recreate the same pool") with no
 * extra bookkeeping: re-running it with an unchanged grant set is a no-op for every resource, since
 * "already instantiated" and "still granted" both stay true.
 *
 * <p><b>No production {@link ResourceGrantProvider} is registered against {@link ResourceGrantRegistry#INSTANCE}
 * by this foundation pass</b> — per the task's explicit scope ("Do not make Rage, Mana, Stamina,
 * Ki, Thirst, etc. live through this mechanism yet"). This class is exercised end-to-end only by
 * dev-only test fixtures — see {@code TestResourceGrantFixtures} (test sources) and
 * {@code DevOnlyResourceGrantVerification} (a development-environment-gated production self-test,
 * matching this codebase's established {@code *Verification}/{@code *SelfTest} precedent).
 */
public final class ResourceGrantReconciler {

    private final PlayerResourceRegistry registry;
    private final ResourceGrantRegistry grantRegistry;
    private final ResourceGrantPolicyRegistry policyRegistry;
    private final PlayerResourceService service;

    public ResourceGrantReconciler(
            PlayerResourceRegistry registry,
            ResourceGrantRegistry grantRegistry,
            ResourceGrantPolicyRegistry policyRegistry,
            PlayerResourceService service
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.grantRegistry = Objects.requireNonNull(grantRegistry, "grantRegistry");
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry");
        this.service = Objects.requireNonNull(service, "service");
    }

    /** The outcome of one reconciliation pass — which resources were instantiated or removed, for tests/diagnostics. */
    public record ReconciliationResult(List<Identifier> instantiated, List<Identifier> removed) {
        public ReconciliationResult {
            instantiated = List.copyOf(instantiated);
            removed = List.copyOf(removed);
        }
        static ReconciliationResult empty() { return new ReconciliationResult(List.of(), List.of()); }
    }

    /**
     * Re-evaluates every registered provider's grants for {@code player} and reconciles
     * {@code state} to match — canonical §16.12's trigger list (class/species/equipment/etc.
     * changes, login, respawn, dimension transfer, definition reload) is the caller's
     * responsibility to invoke this on; the reconciler itself has no tick loop or event
     * subscription (none is wired by this foundation pass — see the implementation report).
     *
     * <p>{@code player} may be {@code null} in tests whose {@link ResourceGrantProvider}s and
     * {@link zcylas.totality.api.rpg.resources.ResourceMaximumResolver}s never dereference it —
     * matching {@code PlayerResourceService}'s own {@code *GenericState} nullable-player
     * convention and this codebase's established {@code new PlayerChargesComponent(null)} pattern.
     * Production callers always supply a real {@code ServerPlayer}.
     */
    public ReconciliationResult reconcile(ServerPlayer player, PlayerResourceStateComponent state) {
        Objects.requireNonNull(state, "state");

        Map<Identifier, List<ResourceGrant>> grantsByResource = new LinkedHashMap<>();
        for (ResourceGrantProvider provider : grantRegistry.providers()) {
            for (ResourceGrant grant : provider.getResourceGrants(player)) {
                grantsByResource.computeIfAbsent(grant.resourceId(), id -> new ArrayList<>()).add(grant);
            }
        }

        List<Identifier> instantiated = new ArrayList<>();
        for (Map.Entry<Identifier, List<ResourceGrant>> entry : grantsByResource.entrySet()) {
            Identifier resourceId = entry.getKey();
            Optional<PlayerResourceDefinition> definitionOpt = registry.get(resourceId);
            if (definitionOpt.isEmpty()) {
                Totality.LOGGER.warn("[ResourceGrantReconciler] a provider granted unregistered resource {}", resourceId);
                continue;
            }
            PlayerResourceDefinition definition = definitionOpt.get();
            if (definition.stateAuthority() != ResourceStateAuthority.GENERIC_COMPONENT) {
                // Canonical §16.2: "External universal grants expose the resource without creating
                // Generic-component state. Their owning systems remain authoritative." — a grant for
                // an EXTERNAL_ADAPTER resource (e.g. totality:health_system -> Health) is informational
                // only here; there is no PlayerResourceStateComponent entry to create or remove.
                continue;
            }
            ResourceGrant winner = selectWinningGrant(entry.getValue(), policyRegistry.get(resourceId).aggregation());
            if (winner == null) continue; // unsupported aggregation policy for this call — logged inside selectWinningGrant

            if (state.hasState(resourceId)) {
                // Already instantiated — the stored value is never refilled by re-reconciliation
                // (canonical §16.6). Two things still need to happen on every pass though: (1)
                // remember the currently-winning grant's own removal policy (canonical §16.2), since
                // the reconciler is stateless and the grant itself will no longer be visible by the
                // time its source actually disappears — see applyRemoval below and the field's own
                // Javadoc on PlayerResourceStateComponent; (2) reactivate a resource that had gone
                // dormant (PRESERVE_DORMANT/RESET_AND_PRESERVE) now that it is genuinely granted
                // again — canonical §16.6: "must not refill it," so only the active flag flips, not
                // the value.
                state.setGrantRemovalPolicy(resourceId, winner.removalPolicy());
                if (!state.isActive(resourceId)) {
                    state.setActive(resourceId, true);
                }
                continue;
            }

            Optional<Identifier> instantiatedId = instantiateFromGrant(player, definition, winner, state);
            instantiatedId.ifPresent(id -> {
                state.setGrantRemovalPolicy(id, winner.removalPolicy());
                instantiated.add(id);
            });
        }

        List<Identifier> removed = new ArrayList<>();
        for (Identifier resourceId : List.copyOf(state.instantiatedResourceIds())) {
            if (grantsByResource.containsKey(resourceId)) continue; // still granted by at least one source
            Optional<PlayerResourceDefinition> definitionOpt = registry.get(resourceId);
            if (definitionOpt.isEmpty()) continue; // orphan-handling elsewhere owns this case
            // The removal policy that actually governed this resource is the last winning grant's
            // own declared policy (captured above while that grant was still visible), not a
            // resource-wide default — canonical §16.2 declares removal policy per grant, not per
            // resource. Fall back to the per-resource registry default only when nothing was ever
            // captured (e.g. state instantiated outside the grant system, or lost across a server
            // restart before any reconciliation ran — see the field's own Javadoc).
            ResourceRemovalPolicy removalPolicy = state.getGrantRemovalPolicy(resourceId)
                    .orElseGet(() -> policyRegistry.get(resourceId).removal());
            if (applyRemoval(state, resourceId, removalPolicy)) {
                removed.add(resourceId);
                state.clearGrantRemovalPolicy(resourceId);
            }
        }

        return new ReconciliationResult(instantiated, removed);
    }

    /**
     * Canonical §16.4. {@code SINGLE_OWNER}/{@code HIGHEST_PRIORITY_SOURCE}: highest {@code priority}
     * wins (ties broken by {@code sourceId} for determinism — canonical: "resolve deterministically,
     * log diagnostics, never silently sum"). {@code SHARED_RESOURCE}: any valid grant may seed the
     * first instantiation (there is only ever one shared pool regardless of which source triggered
     * creation) — the first grant in provider-registration order wins, since all sources are
     * compatible by definition of this policy. {@code SEPARATE_INSTANCES} is declared but not
     * implemented this pass (canonical: "No current Rage, Ki, Solar Charge, Chakra, Reiatsu, or
     * Cursed Energy design requires separate instances" — nothing needs it yet); a resource
     * registered under it is logged and skipped rather than mis-handled as SINGLE_OWNER.
     */
    private ResourceGrant selectWinningGrant(List<ResourceGrant> grants, ResourceGrantAggregationPolicy policy) {
        return switch (policy) {
            case SINGLE_OWNER, HIGHEST_PRIORITY_SOURCE -> grants.stream()
                    .max(Comparator.comparingInt(ResourceGrant::priority)
                            .thenComparing(g -> g.sourceId().toString(), Comparator.reverseOrder()))
                    .orElse(null);
            case SHARED_RESOURCE -> grants.get(0);
            case SEPARATE_INSTANCES -> {
                Totality.LOGGER.warn("[ResourceGrantReconciler] SEPARATE_INSTANCES is not implemented this pass — "
                        + "skipping {} grant(s) for {}", grants.size(), grants.get(0).resourceId());
                yield null;
            }
        };
    }

    private Optional<Identifier> instantiateFromGrant(
            ServerPlayer player, PlayerResourceDefinition definition, ResourceGrant grant, PlayerResourceStateComponent state) {
        Identifier resourceId = definition.id();
        long initial = switch (grant.initialization()) {
            case ResourceGrantInitialization.AtMinimum ignored -> definition.absoluteMinimum();
            case ResourceGrantInitialization.AtMaximum ignored -> {
                Optional<Long> max = resolveScalarMax(player, definition);
                if (max.isEmpty()) {
                    Totality.LOGGER.warn("[ResourceGrantReconciler] cannot resolve maximum for {} — deferring instantiation", resourceId);
                    yield Long.MIN_VALUE; // sentinel: signals "could not resolve," handled below
                }
                yield max.get();
            }
            case ResourceGrantInitialization.AtFraction fraction -> {
                Optional<Long> max = resolveScalarMax(player, definition);
                if (max.isEmpty()) yield Long.MIN_VALUE;
                long computed;
                try {
                    computed = Math.floorDiv(Math.multiplyExact(max.get(), fraction.numerator()), fraction.denominator());
                } catch (ArithmeticException overflowEx) {
                    Totality.LOGGER.warn("[ResourceGrantReconciler] AtFraction {}/{} overflowed for {} — deferring instantiation",
                            fraction.numerator(), fraction.denominator(), resourceId);
                    yield Long.MIN_VALUE;
                }
                // Defense-in-depth clamp: a fraction >1 (deliberate or an authoring mistake) must
                // not seed a value above the resolved maximum, and floorDiv can't go below 0 given
                // the record's own numerator>=0/denominator>0 validation, but the floor is clamped
                // explicitly anyway for symmetry with every other initialization branch here.
                yield Math.max(definition.absoluteMinimum(), Math.min(max.get(), computed));
            }
            case ResourceGrantInitialization.AtAbsolute absolute -> {
                if (!absolute.amount().resourceId().equals(resourceId)) {
                    Totality.LOGGER.warn("[ResourceGrantReconciler] AtAbsolute targets {} but the grant is for {} — deferring instantiation",
                            absolute.amount().resourceId(), resourceId);
                    yield Long.MIN_VALUE;
                }
                if (absolute.amount().partition().isPresent()) {
                    // This foundation's grant instantiation is scalar-only (see the model() check
                    // below) — a partitioned AtAbsolute amount can never be honored here.
                    Totality.LOGGER.warn("[ResourceGrantReconciler] AtAbsolute amount for {} is partitioned but grant "
                            + "instantiation is scalar-only this pass — deferring instantiation", resourceId);
                    yield Long.MIN_VALUE;
                }
                if (absolute.amount().units() < 0) {
                    Totality.LOGGER.warn("[ResourceGrantReconciler] AtAbsolute amount for {} is negative ({}) — deferring instantiation",
                            resourceId, absolute.amount().units());
                    yield Long.MIN_VALUE;
                }
                Optional<Long> max = resolveScalarMax(player, definition);
                if (max.isEmpty()) yield Long.MIN_VALUE;
                // A value outside [absoluteMinimum, max] is rejected-by-deferral rather than
                // silently clamped: canonical gives no clamp-vs-reject rule for a malformed
                // authored AtAbsolute amount, and silently clamping could mask a real authoring
                // bug (see the correction report's "External Review Corrections" section for this
                // reasoning) — everywhere else in this method, an unresolvable input defers
                // instantiation with a logged warning rather than guessing at a corrected value.
                if (absolute.amount().units() < definition.absoluteMinimum() || absolute.amount().units() > max.get()) {
                    Totality.LOGGER.warn("[ResourceGrantReconciler] AtAbsolute amount {} for {} is outside [{}, {}] — deferring instantiation",
                            absolute.amount().units(), resourceId, definition.absoluteMinimum(), max.get());
                    yield Long.MIN_VALUE;
                }
                yield absolute.amount().units();
            }
            case ResourceGrantInitialization.PreserveExisting ignored ->
                // Canonical §16.6 describes this for re-acquisition after PRESERVE_DORMANT/
                // RESET_AND_PRESERVE, where prior state already exists. On a genuine first-ever
                // instantiation there is nothing to preserve — falling back to the safe minimum
                // rather than fabricating a "preserved" value that was never actually there.
                    definition.absoluteMinimum();
            case ResourceGrantInitialization.Custom custom -> {
                Totality.LOGGER.warn("[ResourceGrantReconciler] Custom initialization strategy {} has no registered "
                        + "handler this pass — deferring instantiation of {}", custom.strategyId(), resourceId);
                yield Long.MIN_VALUE;
            }
        };
        if (initial == Long.MIN_VALUE) return Optional.empty();

        // This foundation's grant instantiation is scalar-only (mirroring PlayerResourceService's
        // transact() — canonical gives no partitioned-grant example, and no test/production
        // resource needs one yet).
        if (definition.model() != ResourceModel.SCALAR) {
            Totality.LOGGER.warn("[ResourceGrantReconciler] PARTITIONED_POOL grant instantiation is not "
                    + "implemented this pass — skipping {}", resourceId);
            return Optional.empty();
        }
        state.instantiateScalar(resourceId, initial);
        return Optional.of(resourceId);
    }

    private Optional<Long> resolveScalarMax(ServerPlayer player, PlayerResourceDefinition definition) {
        return service.resolveMaximum(player, definition, ResourceResolutionContext.EMPTY)
                .filter(m -> m instanceof ResourceMaximum.Scalar)
                .map(m -> ((ResourceMaximum.Scalar) m).effectiveUnits());
    }

    /**
     * Canonical §16.7. {@code REMOVE_STATE} (default) deletes state outright. {@code PRESERVE_DORMANT}
     * retains current state but marks it inactive (canonical: "retain current state but make it
     * inactive, unavailable, and normally invisible") — {@link PlayerResourceStateComponent#isActive}
     * is now consulted by every ordinary-gameplay mutation path in {@link
     * zcylas.totality.api.rpg.resources.PlayerResourceService} (external-review correction pass,
     * 2026-09-15: dormant state was previously still fully spendable, a real safety gap). {@code
     * RESET_AND_PRESERVE} has no owner-supplied reset value this pass (canonical explicitly does not
     * specify one generically) so the value itself is left untouched, but it is still marked
     * inactive — "preserve" implies the same dormancy as {@code PRESERVE_DORMANT}, and leaving it
     * fully spendable and permanently active would be the identical safety gap this correction pass
     * exists to close, just for one more policy value. {@code CONVERT} and {@code CUSTOM} require an
     * owner-supplied handler canonical does not specify generically — logged and left untouched
     * (including active status) rather than guessed at.
     */
    private boolean applyRemoval(PlayerResourceStateComponent state, Identifier resourceId, ResourceRemovalPolicy policy) {
        return switch (policy) {
            case REMOVE_STATE -> {
                state.removeState(resourceId);
                yield true;
            }
            case PRESERVE_DORMANT -> {
                state.setActive(resourceId, false);
                yield false;
            }
            case RESET_AND_PRESERVE -> {
                Totality.LOGGER.warn("[ResourceGrantReconciler] removal policy RESET_AND_PRESERVE has no owner-supplied "
                        + "reset value this pass — preserving {}'s current value but marking it dormant rather than "
                        + "resetting it", resourceId);
                state.setActive(resourceId, false);
                yield false;
            }
            case CONVERT, CUSTOM -> {
                Totality.LOGGER.warn("[ResourceGrantReconciler] removal policy {} is not implemented this pass — "
                        + "leaving {} untouched", policy, resourceId);
                yield false;
            }
        };
    }
}
