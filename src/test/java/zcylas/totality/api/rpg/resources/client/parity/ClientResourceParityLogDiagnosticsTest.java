package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-2C: {@link ClientResourceParityLogDiagnostics} is the pure formatter producing the SLF4J
 * parameterized template plus bounded argument array actually passed to {@code
 * Totality.LOGGER.debug(...)} at the thin client-only boundary ({@code ClientResourceParityLogObserver}).
 * This test proves the template placeholder count matches the argument array length exactly, and
 * that the bounded content is exactly what the observation carries — never more.
 */
class ClientResourceParityLogDiagnosticsTest {

    private static ClientResourceParityObservation persistentObservation(long firstMismatchTick, long lastObservedTick) {
        return new ClientResourceParityObservation(
                PlayerResourceIds.MANA,
                ClientResourceParityClassification.PERSISTENT_MISMATCH,
                new ClientResourceParitySummary.Scalar(40, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1),
                firstMismatchTick,
                lastObservedTick,
                3,
                false);
    }

    private static ClientResourceParityObservation exactMatchObservation(long lastObservedTick) {
        return new ClientResourceParityObservation(
                PlayerResourceIds.MANA,
                ClientResourceParityClassification.EXACT_MATCH,
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1),
                null,
                lastObservedTick,
                1,
                false);
    }

    private static int placeholderCount(String template) {
        return template.split("\\{\\}", -1).length - 1;
    }

    @Test
    void entryTemplatePlaceholderCountMatchesArgsLength() {
        Object[] args = ClientResourceParityLogDiagnostics.entryArgs(persistentObservation(5, 7));
        assertEquals(placeholderCount(ClientResourceParityLogDiagnostics.ENTRY_TEMPLATE), args.length);
    }

    @Test
    void recoveryTemplatePlaceholderCountMatchesArgsLength() {
        Object[] args = ClientResourceParityLogDiagnostics.recoveryArgs(exactMatchObservation(9));
        assertEquals(placeholderCount(ClientResourceParityLogDiagnostics.RECOVERY_TEMPLATE), args.length);
    }

    @Test
    void entryArgsContainExactBoundedFields() {
        var observation = persistentObservation(5, 7);
        Object[] args = ClientResourceParityLogDiagnostics.entryArgs(observation);

        assertEquals(PlayerResourceIds.MANA, args[0]);
        assertEquals(ClientResourceParitySummaryText.format(observation.genericSummary()), args[1]);
        assertEquals(ClientResourceParitySummaryText.format(observation.legacySummary()), args[2]);
        assertEquals(5L, args[3]);
        assertEquals(7L, args[4]);
    }

    @Test
    void recoveryArgsContainExactBoundedFields() {
        var observation = exactMatchObservation(9);
        Object[] args = ClientResourceParityLogDiagnostics.recoveryArgs(observation);

        assertEquals(PlayerResourceIds.MANA, args[0]);
        assertEquals(ClientResourceParityClassification.EXACT_MATCH, args[1]);
        assertEquals(ClientResourceParitySummaryText.format(observation.genericSummary()), args[2]);
        assertEquals(ClientResourceParitySummaryText.format(observation.legacySummary()), args[3]);
        assertEquals(9L, args[4]);
    }

    @Test
    void entryArgsNeverIncludesRawObservationOrCollectionReference() {
        Object[] args = ClientResourceParityLogDiagnostics.entryArgs(persistentObservation(1, 2));
        for (Object arg : args) {
            assertTrue(!(arg instanceof ClientResourceParityObservation), "must never leak the raw observation record");
            assertTrue(!(arg instanceof ClientResourceParitySummary), "must never leak a raw typed summary — only its bounded text form");
        }
    }
}
