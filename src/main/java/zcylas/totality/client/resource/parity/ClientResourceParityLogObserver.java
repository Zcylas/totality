package zcylas.totality.client.resource.parity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityLogDiagnostics;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityLogTransition;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityLogTransitionTracker;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityObservation;

import java.util.Map;

/**
 * Phase 3B-2C client-only bounded diagnostic logging. A separate observer of {@link
 * ClientResourceParityObservations}'s existing read-only snapshot — per that class's own Javadoc,
 * this is its intended first consumer, and it deliberately never modifies {@link
 * ClientResourceParityCoordinator}, {@code ClientResourceParityPoll}, or {@code
 * ClientResourceParityTracker}. Diagnostics only: never mutates a Resource, never influences
 * gameplay, rendering, or Resource availability, and never changes the stored parity observation
 * it reads.
 *
 * <p>Registered against {@code END_CLIENT_TICK} strictly after {@code
 * ClientResourceParityCoordinator.tick()} (see {@code TotalityClient.java}) so each tick's
 * diagnostic pass reads that same tick's freshest observations.
 *
 * <h2>Episode scope is the play connection, not the parity tracker's own tighter lifecycle
 * (external-review correction)</h2>
 * A persistent-mismatch <i>logging episode</i> answers "has this resource's persistent mismatch
 * already been reported for the current connection," which is a coarser question than {@code
 * ClientResourceParityTracker}'s own "what do the two sides currently show" state. The two must not
 * share a reset trigger:
 * <ul>
 *   <li>{@link #clear()} is registered only against {@code ClientPlayConnectionEvents.JOIN} and
 *       {@code DISCONNECT} (see {@code TotalityClient.java}) — a genuine new connection/session. It
 *       never fabricates a recovery log (it is {@code void}, never calls {@link #tick()}/{@code
 *       classify}), and a persistent observation in the new connection is always treated as a fresh
 *       entry.</li>
 *   <li>{@link #clear()} is deliberately <b>not</b> registered against {@code
 *       ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE} or any {@code LocalPlayer}-replacement path.
 *       {@code ClientResourceParityCoordinator.clear()} (unmodified, unchanged by this correction)
 *       still runs on both of those — it must, since the parity <i>tracker</i>'s stale generic/
 *       legacy comparison values are meaningless across a dimension swap or a respawn's fresh
 *       {@code LocalPlayer}. But once the tracker rebuilds its observations from scratch and a
 *       resource's mismatch turns out to be the very same continuing real-world condition (the
 *       confirmed live case: Rage staying 1/2 generic vs. 0/0 legacy across a Nether round-trip),
 *       this observer's own episode memory must still remember "already reported" so the rebuilt
 *       {@code PERSISTENT_MISMATCH} observation does not read as a second, spurious entry. Symmetrically,
 *       a later respawn-triggered recovery (legacy Rage restored to 1/2, tracker rebuilds to {@code
 *       EXACT_MATCH}) must still be able to correlate against that same still-active episode to
 *       produce its one recovery line — which requires the episode memory to have survived the
 *       tracker's own intervening clear, not been wiped alongside it.</li>
 * </ul>
 *
 * <p>Only logs a transition <i>into</i> or <i>out of</i> a persistent-mismatch episode — never a
 * per-tick reminder for a steady-state persistent mismatch, and never anything for a classification
 * observed outside such a transition. The exact per-classification episode-continuation rules
 * (second external-review correction: {@code GENERIC_NOT_READY} and {@code TRANSITIONAL_MISMATCH}
 * do <b>not</b> close an already-active episode — they represent observation uncertainty or an
 * ordinary within-grace mismatch, not a real recovery, which matters because the parity tracker's
 * rebuild after a dimension change routes through exactly this sequence for a mismatch that never
 * actually resolved) are owned entirely by {@link ClientResourceParityLogTransitionTracker#classify},
 * the single transition-memory owner for this diagnostic (never duplicated here) — see that method's
 * own Javadoc for the complete, exhaustive rule set.
 */
@Environment(EnvType.CLIENT)
public final class ClientResourceParityLogObserver {

    private static final ClientResourceParityLogTransitionTracker<Identifier> TRANSITIONS =
            new ClientResourceParityLogTransitionTracker<>();

    private ClientResourceParityLogObserver() {}

    /** Called once per {@code END_CLIENT_TICK}. Reads the current bounded four-resource snapshot
     *  and logs at most one bounded DEBUG line per resource whose classification just transitioned
     *  into or out of {@code PERSISTENT_MISMATCH}. Never throws into the client tick loop — a
     *  malformed/unexpected observation for one resource is swallowed so it cannot prevent the
     *  remaining resources from being processed. */
    public static void tick() {
        for (Map.Entry<Identifier, ClientResourceParityObservation> entry : ClientResourceParityObservations.snapshot().entrySet()) {
            processObservation(entry.getKey(), entry.getValue());
        }
    }

    private static void processObservation(Identifier resourceId, ClientResourceParityObservation observation) {
        try {
            ClientResourceParityLogTransition transition = decide(resourceId, observation);
            switch (transition) {
                case ENTERED_PERSISTENT_MISMATCH -> Totality.LOGGER.debug(
                        ClientResourceParityLogDiagnostics.ENTRY_TEMPLATE,
                        ClientResourceParityLogDiagnostics.entryArgs(observation));
                case RECOVERED_FROM_PERSISTENT_MISMATCH -> Totality.LOGGER.debug(
                        ClientResourceParityLogDiagnostics.RECOVERY_TEMPLATE,
                        ClientResourceParityLogDiagnostics.recoveryArgs(observation));
                case NONE -> { }
            }
        } catch (RuntimeException e) {
            // Diagnostics must never break the client tick loop — a formatting/logging failure for
            // one resource's observation is swallowed rather than propagated, and never fabricates
            // or discards transition memory (decide() already committed before this try body could
            // fail; only the logging call itself is guarded).
        }
    }

    /**
     * The exact deterministic decision {@link #tick()} itself uses for one resource's observation —
     * a narrow package-private seam (second external-review correction) so this package's own tests
     * can assert the precise {@link ClientResourceParityLogTransition} produced for each intermediate
     * observation in a rebuild sequence (e.g. the confirmed live {@code GENERIC_NOT_READY} → {@code
     * TRANSITIONAL_MISMATCH} → {@code PERSISTENT_MISMATCH} dimension-change rebuild), rather than
     * inferring whether a log occurred only from {@link #transitions()}'s final active/inactive
     * state — which alone cannot distinguish "no event happened" from "a recovery immediately
     * followed by a fresh entry," since both can leave the same final boolean. Delegates entirely to
     * {@link ClientResourceParityLogTransitionTracker#classify}; performs no logging itself and does
     * not introduce a second owner of transition state.
     */
    static ClientResourceParityLogTransition decide(Identifier resourceId, ClientResourceParityObservation observation) {
        return TRANSITIONS.classify(resourceId, observation.classification());
    }

    /** Connection-scoped reset — forgets every resource's transition memory. Registered only against
     *  {@code ClientPlayConnectionEvents.JOIN}/{@code DISCONNECT} (external-review correction: no
     *  longer against {@code AFTER_CLIENT_LEVEL_CHANGE} — see this class's Javadoc for why episode
     *  memory must survive a dimension change/{@code LocalPlayer} replacement within the same
     *  connection). Never itself emits a diagnostic. */
    public static void clear() {
        TRANSITIONS.clearAll();
    }

    /** Package-private accessor reserved for this package's own tests — mirrors {@link
     *  ClientResourceParityCoordinator#tracker()}'s established pattern. Never made public. */
    static ClientResourceParityLogTransitionTracker<Identifier> transitions() {
        return TRANSITIONS;
    }
}
