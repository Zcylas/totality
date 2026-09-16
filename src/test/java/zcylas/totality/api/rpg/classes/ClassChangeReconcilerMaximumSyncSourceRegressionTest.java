package zcylas.totality.api.rpg.classes;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the Phase 6 correction pass (2026-09-16, level-up regression) — a
 * source-text sentinel, not a runtime proof, matching {@code
 * BarbarianRageAbilityMaximumSyncSourceRegressionTest}'s own established precedent for the same
 * constraint: {@link ClassChangeReconciler}'s reconciliation methods need a real {@code ServerPlayer}
 * to exercise end-to-end against the real, per-player {@code ResourceSyncManager} dirty-tracking
 * state, which is private static and exposes no test-safe inspection point.
 *
 * <p>This proves {@code reconcileScalar}/{@code reconcilePartitioned} route every maximum-driven
 * current change through {@link zcylas.totality.api.rpg.resources.PlayerResourceService}'s own
 * {@code reconcileMaximum}/{@code restore}/{@code drain} — the exact primitives already proven
 * elsewhere ({@code PlayerResourceService}'s own mutation tests, and {@code
 * BarbarianRageAbilityMaximumSyncSourceRegressionTest}'s "already-proven" reasoning) to mark the
 * resource dirty for the Generic sync path on every successful call, never a raw {@code
 * ScalarResourceState}/{@code PartitionedResourceState} mutation that would silently bypass sync.
 * Real end-to-end proof that a level-up's server-side current/maximum values themselves come out
 * correct is covered by {@code StandardSpellSlotMigrationVerification} (dev-only, real {@code
 * ServerPlayer}, real {@code AddClassLevelHandler}-shaped level-up sequence).
 */
class ClassChangeReconcilerMaximumSyncSourceRegressionTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/zcylas/totality/api/rpg/classes/ClassChangeReconciler.java");

    private static String read() throws Exception {
        assertTrue(Files.exists(SOURCE), "expected to find source file at " + SOURCE);
        return Files.readString(SOURCE);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "expected to locate method: " + signature);
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, "expected to locate the end of method: " + signature);
        return source.substring(start, end);
    }

    @Test
    void reconcileScalarRoutesThroughTheSyncMarkingReconcileMaximumPrimitive() throws Exception {
        String body = methodBody(read(), "private static void reconcileScalar(");
        assertTrue(body.contains("PlayerResourceService.INSTANCE.reconcileMaximum("),
                "reconcileScalar must apply a maximum change through PlayerResourceService.reconcileMaximum "
                        + "(which already marks the resource dirty for the Generic sync path on success), not a "
                        + "raw ScalarResourceState mutation that would bypass sync — this is what guarantees the "
                        + "client does not need to reconnect to see a changed maximum.");
    }

    @Test
    void reconcilePartitionedRoutesThroughTheSyncMarkingDrainAndRestorePrimitives() throws Exception {
        String body = methodBody(read(), "private static void reconcilePartitioned(");
        assertTrue(body.contains("PlayerResourceService.INSTANCE.restore("),
                "reconcilePartitioned must grant newly gained capacity through PlayerResourceService.restore "
                        + "(already proven to mark the resource dirty on success), not a raw PartitionedResourceState "
                        + "mutation — otherwise a newly unlocked tier would stay invisible to the client until an "
                        + "unrelated sync or a reconnect, reproducing the exact reported bug.");
        assertTrue(body.contains("PlayerResourceService.INSTANCE.drain("),
                "reconcilePartitioned must clamp a shrinking partition through PlayerResourceService.drain, for "
                        + "the same sync-marking reason.");
    }

    @Test
    void bothReconciliationMethodsSkipUntouchedResourcesRatherThanUnconditionallyMarkingDirty() throws Exception {
        // Guards against a well-intentioned but wasteful "fix": every GENERIC_COMPONENT resource must
        // not be marked dirty on every class change regardless of whether its maximum actually moved
        // — only reconcileScalar/reconcilePartitioned's own no-op guards should decide that.
        String source = read();
        String scalarBody = methodBody(source, "private static void reconcileScalar(");
        assertTrue(scalarBody.contains("if (current <= newMax && previousMaximumUnits == newMax) return;"),
                "reconcileScalar must skip reconciliation entirely when nothing moved, rather than "
                        + "unconditionally calling reconcileMaximum (and therefore marking dirty) on every class "
                        + "change for every scalar GENERIC_COMPONENT resource.");
    }

    @Test
    void reconcilePartitionedExplicitlyMarksDirtyOnAMaximumOnlyChangeWithNoCurrentMutation() throws Exception {
        // Cleanup pass (2026-09-16): a partition's maximum can genuinely move while its reconciled
        // current stays the same (e.g. an already-empty partition whose shrinking maximum still clamps
        // to the same floor value) — no drain/restore call runs in that case, so nothing would mark
        // the resource dirty without this explicit fallback. Must only fire when a real maximum change
        // was detected AND no partition was actually mutated this pass (see the next test).
        String body = methodBody(read(), "private static void reconcilePartitioned(");
        assertTrue(body.contains("if (maximumChanged && !mutated) {"),
                "reconcilePartitioned must explicitly mark the resource dirty when a genuine maximum "
                        + "change produced zero reconciled-current delta for every partition — otherwise the "
                        + "client's cached maximum silently goes stale with no packet ever sent, until an "
                        + "unrelated sync or a reconnect.");
        assertTrue(body.contains("ResourceSyncManager.markDirty(player.getUUID(), resourceId);"),
                "the maximum-only fallback must mark the SAME resourceId the ordinary drain/restore path "
                        + "would have marked, not a different/derived id.");
    }

    @Test
    void reconcilePartitionedNeverForceMarksDirtyWhenNoMaximumGenuinelyChanged() throws Exception {
        // Guards against a well-intentioned but wasteful "fix" the same way the scalar test above does:
        // the maximumChanged flag must only ever be set from a real previousMax comparison, never
        // unconditionally — otherwise every partitioned GENERIC_COMPONENT resource would be marked
        // dirty on every ordinary class change regardless of whether anything moved.
        String body = methodBody(read(), "private static void reconcilePartitioned(");
        assertTrue(body.contains("if (previousMaximumUnits != newMax) maximumChanged = true;"),
                "maximumChanged must be derived from an actual previousMaximumUnits/newMax comparison per "
                        + "partition, not set unconditionally.");
        assertTrue(body.contains("? previousMax.effectiveByPartition().getOrDefault(partition, newMax)")
                        && body.contains(": newMax;"),
                "previousMaximumUnits must default to the freshly resolved newMax when previousMax is null "
                        + "(the degenerate no-real-before case) — otherwise maximumChanged could spuriously "
                        + "trip on every ordinary class change that never captured a real 'before' snapshot.");
    }
}
