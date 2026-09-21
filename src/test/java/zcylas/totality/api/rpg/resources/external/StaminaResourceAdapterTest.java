package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceComponent;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure logic for Stamina, tested directly against {@link StaminaResourceAdapter#resolve}/{@link StaminaResourceAdapter#normalize}.
 * Mirrors {@link ManaResourceAdapterTest} exactly — see that class's Javadoc for the full rationale
 * behind each test.
 */
class StaminaResourceAdapterTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "stamina");

    private static ResourceSnapshot successSnapshot(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.Success.class, result);
        return ((ResourceQueryResult.Success) result).snapshot();
    }

    private static ResourceQueryFailureReason failureReason(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        return ((ResourceQueryResult.Failure) result).reason();
    }

    @Test
    void staminaAdapterSupportsOnlyQuery() {
        assertTrue(StaminaResourceAdapter.INSTANCE.supportedOperations().contains(ExternalResourceOperationSupport.QUERY));
        assertEquals(1, StaminaResourceAdapter.INSTANCE.supportedOperations().size(),
                "Stamina is query-only in Phase 2C — no RESTORE/DRAIN/SPEND/SET support");
        assertEquals(ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION,
                StaminaResourceAdapter.INSTANCE.clientMirrorMode());
    }

    @Test
    void adapterIdMatchesTheStaminaResourceId() {
        assertEquals(ID, StaminaResourceAdapter.INSTANCE.id());
        assertEquals(ID, StaminaResourceAdapter.ID);
    }

    @Test
    void baseMaxStaminaIsOneHundred() {
        assertEquals(100, PlayerStaminaManager.BASE_MAX_STAMINA);
    }

    // ── resolve(): initialized-check-before-maximum-calculation ordering ───────────────────

    @Test
    void resolveNeverInvokesTheMaximumSupplierWhenUninitialized() {
        PlayerResourceComponent component = new PlayerResourceComponent(null);
        assertFalse(component.isStaminaInitialized(), "sanity: a fresh component starts uninitialized");
        AtomicInteger invocations = new AtomicInteger();

        ResourceQueryResult result = StaminaResourceAdapter.resolve(ID, component, () -> {
            invocations.incrementAndGet();
            return 100;
        }, 1);

        assertEquals(ResourceQueryFailureReason.STATE_UNINITIALIZED, failureReason(result));
        assertEquals(ID, ((ResourceQueryResult.Failure) result).resourceId());
        assertEquals(0, invocations.get(), "the maximum supplier must not be invoked for uninitialized state");
    }

    @Test
    void resolveInvokesTheMaximumSupplierExactlyOnceWhenInitialized() {
        PlayerResourceComponent component = new PlayerResourceComponent(null);
        component.setStamina(41);
        assertTrue(component.isStaminaInitialized());
        AtomicInteger invocations = new AtomicInteger();

        ResourceQueryResult result = StaminaResourceAdapter.resolve(ID, component, () -> {
            invocations.incrementAndGet();
            return 100;
        }, 1);

        assertEquals(1, invocations.get(), "the maximum supplier must be invoked exactly once for initialized state");
        ResourceSnapshot snapshot = successSnapshot(result);
        assertEquals(41L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void resolvePreservesCurrentAndDynamicMaximumForInitializedState() {
        PlayerResourceComponent component = new PlayerResourceComponent(null);
        component.setStamina(140);

        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.resolve(ID, component, () -> 170, 1));

        assertEquals(140L, snapshot.currentUnits());
        assertEquals(170L, snapshot.maximumUnits());
    }

    @Test
    void resolveDoesNotMutateTheComponent() {
        PlayerResourceComponent component = new PlayerResourceComponent(null);
        component.setStamina(41);

        StaminaResourceAdapter.resolve(ID, component, () -> 100, 1);
        PlayerResourceComponent freshComponent = new PlayerResourceComponent(null);
        StaminaResourceAdapter.resolve(ID, freshComponent, () -> 100, 1);

        assertEquals(41, component.getStamina(), "resolve must never write the component it read");
        assertFalse(freshComponent.isStaminaInitialized(), "resolve must never initialize an uninitialized component");
        assertEquals(-1, freshComponent.getStamina());
    }

    // ── normalize(): pure clamp/validate core ───────────────────────────────────────────────

    @Test
    void fullStaminaNormalizesToCurrentEqualsMaximum() {
        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.normalize(ID, 100, 100, 1));
        assertEquals(100L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void partialStaminaNormalizesUnchanged() {
        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.normalize(ID, 41, 100, 1));
        assertEquals(41L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void zeroStaminaNormalizesToZero() {
        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.normalize(ID, 0, 100, 1));
        assertEquals(0L, snapshot.currentUnits());
    }

    @Test
    void dynamicBonusMaximumIsRespected() {
        // A stat/equipment/effect-bumped maximum (e.g. base 100 + 20 END bonus + armor bonus).
        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.normalize(ID, 140, 170, 1));
        assertEquals(140L, snapshot.currentUnits());
        assertEquals(170L, snapshot.maximumUnits());
    }

    @Test
    void currentAboveMaximumPassesThroughUnclampedMatchingExistingOwnerPermissiveness() {
        // PlayerResourceRecalculator / StaminaServerTick both intentionally allow current to
        // transiently exceed maximum until their own next clamping pass — the adapter must not
        // silently "repair" that existing, accepted behavior.
        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.normalize(ID, 115, 100, 1));
        assertEquals(115L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void zeroMaximumProducesMalformedOwnerStateFailure() {
        // Reachable in practice: a sufficiently negative END modifier can drive
        // PlayerStaminaManager.getMaxStamina to zero or below.
        ResourceQueryResult result = StaminaResourceAdapter.normalize(ID, 0, 0, 1);
        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void negativeMaximumProducesMalformedOwnerStateFailure() {
        ResourceQueryResult result = StaminaResourceAdapter.normalize(ID, 0, -30, 1);
        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void negativeCurrentOtherThanTheUninitializedSentinelProducesMalformedOwnerStateFailure() {
        // PlayerResourceComponent.isStaminaInitialized() itself treats every negative value as
        // "uninitialized" (its own `stamina >= 0` contract), so normalize's only real caller
        // (resolve) never passes a negative current — tested defensively here anyway, matching
        // BreathResourceAdapterTest's precedent for values no real Player would produce.
        ResourceQueryResult result = StaminaResourceAdapter.normalize(ID, -3, 100, 1);
        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void unitScaleIsAlwaysOne() {
        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.normalize(ID, 50, 100, 1));
        assertEquals(1L, snapshot.unitScale());
    }

    @Test
    void resultIsAnExactIntegerNoFloatConversion() {
        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.normalize(ID, 77, 100, 1));
        assertEquals(77L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void resourceIdIsPreservedOnTheSnapshot() {
        ResourceSnapshot snapshot = successSnapshot(StaminaResourceAdapter.normalize(ID, 50, 100, 1));
        assertEquals(ID, snapshot.resourceId());
    }
}
