package zcylas.totality.client.resource.parity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityObservation;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityOutcome;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParitySummary;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Phase 3B-2B test-plan items 37-39: the read-only observation access boundary — both the
 * structural shape (via reflection, no Minecraft/client behavior invoked) and, per the 2026-07-25
 * external-review correction, the actual read behavior (seeded through the same-package
 * package-private {@link ClientResourceParityCoordinator#tracker()} accessor, never {@code
 * coordinator.tick()} or {@code Minecraft.getInstance()}, so this still runs safely without a
 * running client).
 */
class ClientResourceParityObservationsTest {

    @AfterEach
    void clearCoordinatorState() {
        ClientResourceParityCoordinator.tracker().clearAll();
    }

    @Test
    void exposesOnlyLatestAndSnapshotAsPublicApi() {
        List<Method> publicMethods = Arrays.stream(ClientResourceParityObservations.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toList();
        assertEquals(2, publicMethods.size(), "expected exactly latest(...) and snapshot() to be public");
        assertTrue(publicMethods.stream().anyMatch(m -> m.getName().equals("latest")
                && m.getReturnType().equals(Optional.class)));
        assertTrue(publicMethods.stream().anyMatch(m -> m.getName().equals("snapshot")
                && m.getReturnType().equals(Map.class)));
    }

    @Test
    void declaresNoMutatingMethod() {
        for (Method method : ClientResourceParityObservations.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertTrue(
                    !name.contains("clear") && !name.contains("set") && !name.contains("mutate")
                            && !name.contains("apply") && !name.contains("observe"),
                    "unexpected mutating-sounding method exposed: " + method.getName());
        }
    }

    @Test
    void coordinatorTrackerAccessorIsNotPublic() throws NoSuchMethodException {
        Method tracker = ClientResourceParityCoordinator.class.getDeclaredMethod("tracker");
        assertTrue(!Modifier.isPublic(tracker.getModifiers()),
                "ClientResourceParityCoordinator.tracker() must never be public — only same-package "
                        + "read-view code (ClientResourceParityObservations) may call it directly");
    }

    // ── External-review correction (2026-07-25): behavioral tests via the package-private ──────
    // ── tracker() accessor — no coordinator.tick()/Minecraft.getInstance() call anywhere here. ──

    @Test
    void latestReturnsTheExactObservationSeededOnTheTracker() {
        var expected = ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.MANA, 0, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(50, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(50, 100, 0, 1), false);

        Optional<ClientResourceParityObservation> actual = ClientResourceParityObservations.latest(PlayerResourceIds.MANA);

        assertTrue(actual.isPresent());
        assertEquals(expected, actual.orElseThrow());
    }

    @Test
    void latestIsEmptyForAResourceNeverObserved() {
        assertTrue(ClientResourceParityObservations.latest(PlayerResourceIds.RAGE).isEmpty());
    }

    @Test
    void snapshotContainsEverySeededObservation() {
        ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.MANA, 0, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1),
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1), false);
        ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.STAMINA, 0, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(2, 2, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 2, 0, 1), false);

        Map<net.minecraft.resources.Identifier, ClientResourceParityObservation> snapshot =
                ClientResourceParityObservations.snapshot();

        assertTrue(snapshot.containsKey(PlayerResourceIds.MANA));
        assertTrue(snapshot.containsKey(PlayerResourceIds.STAMINA));
        assertEquals(2, snapshot.size());
    }

    @Test
    void snapshotIsImmutable() {
        ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.MANA, 0, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1),
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1), false);
        var snapshot = ClientResourceParityObservations.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(PlayerResourceIds.RAGE, null));
    }

    @Test
    void snapshotIsDetachedFromLaterTrackerChanges() {
        ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.MANA, 0, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1),
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1), false);
        var snapshotA = ClientResourceParityObservations.snapshot();
        assertEquals(1, snapshotA.size());

        ClientResourceParityCoordinator.tracker().clearAll();
        ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.STAMINA, 0, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(2, 2, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 2, 0, 1), false);

        // snapshotA was built before the clear/re-observe above and must not reflect it.
        assertEquals(1, snapshotA.size());
        assertTrue(snapshotA.containsKey(PlayerResourceIds.MANA));
        assertNotSame(snapshotA, ClientResourceParityObservations.snapshot());
    }
}
