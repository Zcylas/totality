package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * The server-side Entitlement API facade: every query and every mutation of player entitlement state goes
 * through here (canonical §7.1 — there is no client packet that unlocks anything).
 *
 * <p>After each committed transaction it fires {@link EntitlementEvents#MUTATION}, recomputes availability of
 * tracked types and fires {@link EntitlementEvents#AVAILABILITY_CHANGED} for every key that gained or lost
 * availability, and pushes the owner's display view when it changed. Nested mutations triggered by event
 * listeners are allowed up to a fixed depth and then rejected, so a listener loop cannot recurse forever.
 */
public final class EntitlementService {

    public static final EntitlementService INSTANCE = new EntitlementService(new EntitlementEngine(EntitlementCatalog.INSTANCE));

    static final int MAX_TRANSACTION_DEPTH = 8;
    private static final int EXPIRY_INTERVAL_TICKS = 20;
    private static final int PROVIDER_RETRY_INTERVAL_TICKS = 100;

    private final EntitlementEngine engine;
    private int tickCounter;

    public EntitlementService(EntitlementEngine engine) {
        this.engine = engine;
    }

    public EntitlementEngine engine() {
        return engine;
    }

    public PlayerEntitlementState state(ServerPlayer player) {
        return EntitlementComponents.get(player).state();
    }

    public EntitlementClock clock(ServerPlayer player) {
        return new EntitlementClock(state(player).ledger().onlineTicks(), player.level().getGameTime(), System.currentTimeMillis());
    }

    public EntitlementQueryContext context(ServerPlayer player, EntitlementQueryContext.Purpose purpose) {
        return new EntitlementQueryContext(player, purpose, clock(player));
    }

    // ══ Queries ═══════════════════════════════════════════════════════════════

    public EntitlementDecision query(ServerPlayer player, EntitlementKey key, Identifier actionId,
                                     EntitlementQueryContext.Purpose purpose) {
        return engine.evaluate(state(player), key, actionId, context(player, purpose));
    }

    /** Execution-time revalidation of a protected action. Always evaluated fresh. */
    public EntitlementDecision checkServerAction(ServerPlayer player, EntitlementKey key, Identifier actionId) {
        return checkServerAction(player, key, actionId, OptionalLong.empty());
    }

    /** As above; a stale client revision turns a state-change denial into {@code stale_client_state}. */
    public EntitlementDecision checkServerAction(ServerPlayer player, EntitlementKey key, Identifier actionId,
                                                 OptionalLong clientRevision) {
        return engine.checkServerAction(state(player), key, actionId,
                context(player, EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT), clientRevision);
    }

    public boolean isAllowed(ServerPlayer player, EntitlementKey key, Identifier actionId) {
        return checkServerAction(player, key, actionId).allowed();
    }

    /** Keys of a type that currently allow the action — cached until the revision or a read dependency changes,
     *  so per-tick callers (passive abilities) do not re-evaluate anything. */
    public Set<EntitlementKey> accessible(ServerPlayer player, Identifier typeId, Identifier actionId) {
        return engine.accessible(state(player), typeId, actionId,
                context(player, EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT));
    }

    public List<EntitlementDisplaySnapshot> displayView(ServerPlayer player) {
        return engine.displayView(state(player), context(player, EntitlementQueryContext.Purpose.UI_PREVIEW));
    }

    // ══ Mutations ═════════════════════════════════════════════════════════════

    public EntitlementMutationResult grantPermanentFact(ServerPlayer player, EntitlementKey key, PermanentEntitlementFact fact,
                                                        GrantSourceRef source, Identifier acquisitionMethodId,
                                                        boolean progressionEligible) {
        if (tooDeep(player)) return depthRejection();
        return commit(player, engine.addPermanentFact(state(player), key, fact, source, acquisitionMethodId,
                progressionEligible, clock(player)));
    }

    public EntitlementMutationResult revokePermanentFact(ServerPlayer player, EntitlementKey key, PermanentEntitlementFact fact,
                                                         GrantSourceRef revokedBy, Identifier reasonCode) {
        if (tooDeep(player)) return depthRejection();
        return commit(player, engine.revokePermanentFact(state(player), key, fact, revokedBy, reasonCode, clock(player)));
    }

    public EntitlementMutationResult addGrant(ServerPlayer player, EntitlementGrant grant) {
        if (tooDeep(player)) return depthRejection();
        return commit(player, engine.addGrant(state(player), grant, clock(player)));
    }

    public EntitlementMutationResult removeGrant(ServerPlayer player, UUID grantId, GrantSourceRef expectedSource) {
        if (tooDeep(player)) return depthRejection();
        return commit(player, engine.removeGrant(state(player), grantId, expectedSource, clock(player)));
    }

    public EntitlementMutationResult removeGrantsFrom(ServerPlayer player, GrantSourceRef source) {
        if (tooDeep(player)) return depthRejection();
        return commit(player, engine.removeGrantsFrom(state(player), source));
    }

    public EntitlementMutationResult addSuspension(ServerPlayer player, EntitlementSuspension suspension) {
        if (tooDeep(player)) return depthRejection();
        return commit(player, engine.addSuspension(state(player), suspension, clock(player)));
    }

    public EntitlementMutationResult removeSuspension(ServerPlayer player, UUID suspensionId, GrantSourceRef expectedSource) {
        if (tooDeep(player)) return depthRejection();
        return commit(player, engine.removeSuspension(state(player), suspensionId, expectedSource, clock(player)));
    }

    public EntitlementMutationResult convertGrantToPermanent(ServerPlayer player, UUID grantId, PermanentEntitlementFact fact,
                                                             Identifier acquisitionMethodId) {
        if (tooDeep(player)) return depthRejection();
        return commit(player, engine.convertGrantToPermanent(state(player), grantId, fact, acquisitionMethodId, clock(player)));
    }

    /** Re-collects one provider's grants from its source system and applies the source-scoped diff. A provider
     *  that throws is marked unverified: its previous grants are kept on record but withheld (see
     *  {@link EntitlementEngine#reconcile}); it is retried periodically and on every reconciliation. */
    public EntitlementEngine.ReconciliationResult reconcileProvider(ServerPlayer player, Identifier providerId) {
        EntitlementGrantProvider provider = engine.catalog().provider(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown entitlement provider " + providerId));
        EntitlementEngine.ReconciliationResult result = engine.reconcile(state(player), provider, player, clock(player));
        commit(player, result.mutation());
        return result;
    }

    public void reconcileAll(ServerPlayer player) {
        for (EntitlementGrantProvider provider : engine.catalog().providers()) {
            reconcileProvider(player, provider.providerId());
        }
    }

    /**
     * Called by an owning system when state a condition or contributor reads has changed (canonical §5.7).
     * Cached results that read it are dropped, and availability is always recomputed from current state — a
     * change must be reported even when nothing about it had been cached yet.
     */
    public void invalidate(ServerPlayer player, EntitlementDependencyKey changed) {
        state(player).invalidate(changed);
        afterChange(player);
    }

    // ══ Lifecycle ═════════════════════════════════════════════════════════════

    /**
     * Records the current availability without firing events. Call once after the join / respawn
     * reconciliation, so restoring a player's state is not reported as acquisitions.
     */
    public void baselineAvailability(ServerPlayer player) {
        engine.baselineAvailability(state(player), context(player, EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT));
        syncView(player);
    }

    /** Reports grants dropped by the death copy once the respawned player exists. */
    public void reportDeathRemovals(ServerPlayer player) {
        PlayerEntitlementState state = state(player);
        if (state.pendingDeathRemovals.isEmpty()) return;
        UUID tx = UUID.randomUUID();
        List<EntitlementGrant> removed = List.copyOf(state.pendingDeathRemovals);
        state.pendingDeathRemovals.clear();
        for (EntitlementGrant grant : removed) {
            EntitlementEvents.MUTATION.invoker().onMutation(player,
                    new EntitlementChange.GrantRemoved(grant, EntitlementChange.RemovalReason.DEATH), tx);
        }
    }

    /** Advances each online player's ONLINE_TICKS clock, expires time-limited grants and suspensions, and
     *  periodically retries providers whose last collection failed. */
    public void tick(MinecraftServer server) {
        if (++tickCounter % EXPIRY_INTERVAL_TICKS != 0) return;
        boolean retry = tickCounter % PROVIDER_RETRY_INTERVAL_TICKS == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!EntitlementComponents.ENTITLEMENTS.isProvidedBy((ComponentProvider) player)) continue;
            PlayerEntitlementState state = state(player);
            state.ledger.advanceOnlineTicks(EXPIRY_INTERVAL_TICKS);
            commit(player, engine.expire(state, clock(player)));
            if (retry) {
                for (Identifier providerId : List.copyOf(state.failedProviders.keySet())) reconcileProvider(player, providerId);
            }
        }
    }

    // ══ Commit pipeline ═══════════════════════════════════════════════════════

    private EntitlementMutationResult commit(ServerPlayer player, EntitlementMutationResult result) {
        if (!result.changed()) return result;
        PlayerEntitlementState state = state(player);
        state.transactionDepth++;
        try {
            for (EntitlementChange change : result.changes()) {
                EntitlementEvents.MUTATION.invoker().onMutation(player, change, result.transactionId());
            }
            afterChange(player);
        } finally {
            state.transactionDepth--;
        }
        return result;
    }

    private void afterChange(ServerPlayer player) {
        List<EntitlementEngine.AvailabilityChange> changes = engine.recomputeAvailability(state(player),
                context(player, EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT));
        // Losses first, so an owner sees "lost A" before "gained B" when one source replaces another.
        for (EntitlementEngine.AvailabilityChange change : changes) {
            if (!change.available()) EntitlementEvents.AVAILABILITY_CHANGED.invoker()
                    .onAvailabilityChanged(player, change.key(), change.actionId(), false);
        }
        for (EntitlementEngine.AvailabilityChange change : changes) {
            if (change.available()) EntitlementEvents.AVAILABILITY_CHANGED.invoker()
                    .onAvailabilityChanged(player, change.key(), change.actionId(), true);
        }
        syncView(player);
    }

    private void syncView(ServerPlayer player) {
        PlayerEntitlementState state = state(player);
        int hash = displayView(player).hashCode();
        if (state.lastViewHash != null && state.lastViewHash == hash) return;
        state.lastViewHash = hash;
        EntitlementComponents.ENTITLEMENTS.sync((ComponentProvider) player);
    }

    private boolean tooDeep(ServerPlayer player) {
        if (state(player).transactionDepth < MAX_TRANSACTION_DEPTH) return false;
        Totality.LOGGER.error("[Entitlement] Rejected nested mutation for {}: transaction depth limit reached (listener loop?)",
                player.getName().getString());
        return true;
    }

    private static EntitlementMutationResult depthRejection() {
        return EntitlementMutationResult.rejected(EntitlementReasons.SERVER_DENIED, "transaction depth limit", UUID.randomUUID());
    }
}
