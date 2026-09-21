package zcylas.totality.api.ability.impl.barbarian;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the Phase 5 external-review correction (2026-09-15, finding 1): a
 * source-text sentinel, not a runtime proof — {@link BarbarianRageAbility#updateChargePool} needs a
 * real {@code ServerPlayer} to exercise end-to-end against the real, per-player {@code
 * ResourceSyncManager} dirty-tracking state, which is private static and exposes no test-safe
 * inspection point (matching the same constraint {@code PlayerConnectionEventsChargeSyncSourceRegressionTest}
 * documents for {@code ServerPlayConnectionEvents.JOIN}). Real end-to-end proof that the resolved
 * maximum itself actually changes across a level threshold is covered by
 * {@code BarbarianRageMigrationVerification} (dev-only, real {@code ServerPlayer}).
 *
 * <p>Before this correction, {@code updateChargePool} only called {@code
 * BarbarianRageResources.ensureInstantiated} — a no-op for an already-instantiated resource — so a
 * Barbarian crossing a class-level threshold that raised Rage's resolved maximum (e.g. 2/2 -> 2/3)
 * never caused {@code totality:rage} to be requeried by the Generic sync path, leaving the client
 * stuck at the stale maximum until an unrelated mutation or a full snapshot happened to catch up.
 */
class BarbarianRageAbilityMaximumSyncSourceRegressionTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/zcylas/totality/api/ability/impl/barbarian/BarbarianRageAbility.java");

    private static String read() throws Exception {
        assertTrue(Files.exists(SOURCE), "expected to find source file at " + SOURCE);
        return Files.readString(SOURCE);
    }

    @Test
    void updateChargePoolMarksTotalityRageDirtyForTheGenericSyncPath() throws Exception {
        String source = read();
        int methodStart = source.indexOf("public static void updateChargePool(ServerPlayer player) {");
        assertTrue(methodStart >= 0, "expected to locate the updateChargePool method body");
        int methodEnd = source.indexOf("}", methodStart);
        String methodBody = source.substring(methodStart, methodEnd);

        assertTrue(methodBody.contains("ResourceSyncManager.markDirty(player.getUUID(), PlayerResourceIds.RAGE)"),
                "updateChargePool must mark totality:rage dirty so a maximum-only change (e.g. a class-level "
                        + "threshold crossing) is requeried and delivered to the client by the Generic sync path — "
                        + "mirroring PlayerResourceRecalculator.recalculate/recalculateAndRestore's own "
                        + "maximum-only-change seam for Mana/Stamina. Without this, ensureInstantiated alone is a "
                        + "no-op for an already-instantiated resource, and the client can remain stuck at a stale "
                        + "maximum indefinitely.");
    }

    @Test
    void updateChargePoolDoesNotMutateCurrentMerelyToForceASync() throws Exception {
        // Guards against a well-intentioned but forbidden "fix": the task is explicit that current
        // must never be mutated/refilled merely to trigger a client sync.
        String source = read();
        int methodStart = source.indexOf("public static void updateChargePool(ServerPlayer player) {");
        int methodEnd = source.indexOf("}", methodStart);
        String methodBody = source.substring(methodStart, methodEnd);

        assertFalse(methodBody.contains("trySpend") || methodBody.contains(".restore(") || methodBody.contains(".set("),
                "updateChargePool must never mutate totality:rage's current value merely to force a sync");
    }
}
