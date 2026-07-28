package zcylas.totality.api.rpg.resources.client.parity;

/**
 * Pure formatter for the bounded DEBUG diagnostics Phase 3B-2C logs on a persistent-mismatch
 * entry/recovery transition — mirrors {@code ClientResourceSyncRejectionDiagnostics}'s precedent
 * (bounded metadata only, no Minecraft/Fabric/logging dependency, directly unit-testable) but
 * returns an SLF4J parameterized template plus a bounded argument array rather than one eagerly
 * concatenated string, so the impure {@code Totality.LOGGER.debug(template, args)} call at the
 * client-only boundary never performs its own string concatenation.
 *
 * <p>Deliberately narrow content: resource id, previous/current classification context implied by
 * which template is used, the two already-bounded {@link ClientResourceParitySummary} renderings
 * (via {@link ClientResourceParitySummaryText}), and tick metadata already safely exposed by
 * {@link ClientResourceParityObservation}. Never a player UUID, world path, coordinate, inventory
 * contents, or other unrelated gameplay state.
 */
public final class ClientResourceParityLogDiagnostics {

    public static final String ENTRY_TEMPLATE =
            "Resource parity entered persistent mismatch: resource={} generic={} legacy={} firstMismatchTick={} lastObservedTick={}";

    public static final String RECOVERY_TEMPLATE =
            "Resource parity recovered from persistent mismatch: resource={} classification={} generic={} legacy={} lastObservedTick={}";

    /** Bounded argument array matching {@link #ENTRY_TEMPLATE}'s five placeholders, in order. */
    public static Object[] entryArgs(ClientResourceParityObservation observation) {
        return new Object[] {
                observation.resourceId(),
                ClientResourceParitySummaryText.format(observation.genericSummary()),
                ClientResourceParitySummaryText.format(observation.legacySummary()),
                observation.firstMismatchTick(),
                observation.lastObservedTick()
        };
    }

    /** Bounded argument array matching {@link #RECOVERY_TEMPLATE}'s five placeholders, in order. */
    public static Object[] recoveryArgs(ClientResourceParityObservation observation) {
        return new Object[] {
                observation.resourceId(),
                observation.classification(),
                ClientResourceParitySummaryText.format(observation.genericSummary()),
                ClientResourceParitySummaryText.format(observation.legacySummary()),
                observation.lastObservedTick()
        };
    }

    private ClientResourceParityLogDiagnostics() {}
}
