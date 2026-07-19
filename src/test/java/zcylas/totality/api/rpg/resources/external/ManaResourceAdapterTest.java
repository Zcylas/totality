package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.resources.PlayerResourceComponent;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure logic for Mana, tested directly against {@link ManaResourceAdapter#resolve}/{@link ManaResourceAdapter#normalize}
 * rather than through a real {@code ServerPlayer} (constructing one outside the Minecraft runtime is
 * impractical — matching {@code BreathResourceAdapterTest}'s established precedent).
 * {@link ManaResourceAdapter#snapshot} itself (including the client/server routing it adds on top)
 * is exercised end-to-end only by the manual smoke-test checklist in the Phase 2C report.
 */
class ManaResourceAdapterTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "mana");

    private static ResourceSnapshot successSnapshot(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.Success.class, result);
        return ((ResourceQueryResult.Success) result).snapshot();
    }

    private static ResourceQueryFailureReason failureReason(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        return ((ResourceQueryResult.Failure) result).reason();
    }

    @Test
    void manaAdapterSupportsOnlyQuery() {
        assertTrue(ManaResourceAdapter.INSTANCE.supportedOperations().contains(ExternalResourceOperationSupport.QUERY));
        assertEquals(1, ManaResourceAdapter.INSTANCE.supportedOperations().size(),
                "Mana is query-only in Phase 2C — no RESTORE/DRAIN/SPEND/SET support");
        assertEquals(ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION,
                ManaResourceAdapter.INSTANCE.clientMirrorMode());
    }

    @Test
    void adapterIdMatchesTheManaResourceId() {
        assertEquals(ID, ManaResourceAdapter.INSTANCE.id());
        assertEquals(ID, ManaResourceAdapter.ID);
    }

    @Test
    void baseMaxManaIsOneHundred() {
        // PlayerManaManager.BASE_MAX_MANA — the audited baseline before any bonus.
        assertEquals(100, PlayerManaManager.BASE_MAX_MANA);
    }

    // ── resolve(): initialized-check-before-maximum-calculation ordering ───────────────────

    @Test
    void resolveNeverInvokesTheMaximumSupplierWhenUninitialized() {
        PlayerResourceComponent component = new PlayerResourceComponent(null);
        assertFalse(component.isManaInitialized(), "sanity: a fresh component starts uninitialized");
        AtomicInteger invocations = new AtomicInteger();

        ResourceQueryResult result = ManaResourceAdapter.resolve(ID, component, () -> {
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
        component.setMana(42);
        assertTrue(component.isManaInitialized());
        AtomicInteger invocations = new AtomicInteger();

        ResourceQueryResult result = ManaResourceAdapter.resolve(ID, component, () -> {
            invocations.incrementAndGet();
            return 100;
        }, 1);

        assertEquals(1, invocations.get(), "the maximum supplier must be invoked exactly once for initialized state");
        ResourceSnapshot snapshot = successSnapshot(result);
        assertEquals(42L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void resolvePreservesCurrentAndDynamicMaximumForInitializedState() {
        PlayerResourceComponent component = new PlayerResourceComponent(null);
        component.setMana(150);

        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.resolve(ID, component, () -> 180, 1));

        assertEquals(150L, snapshot.currentUnits());
        assertEquals(180L, snapshot.maximumUnits());
    }

    @Test
    void resolveDoesNotMutateTheComponent() {
        PlayerResourceComponent component = new PlayerResourceComponent(null);
        component.setMana(37);

        ManaResourceAdapter.resolve(ID, component, () -> 100, 1);
        // Also exercise the uninitialized path against a separate fresh component.
        PlayerResourceComponent freshComponent = new PlayerResourceComponent(null);
        ManaResourceAdapter.resolve(ID, freshComponent, () -> 100, 1);

        assertEquals(37, component.getMana(), "resolve must never write the component it read");
        assertFalse(freshComponent.isManaInitialized(), "resolve must never initialize an uninitialized component");
        assertEquals(-1, freshComponent.getMana());
    }

    // ── normalize(): pure clamp/validate core ───────────────────────────────────────────────

    @Test
    void fullManaNormalizesToCurrentEqualsMaximum() {
        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.normalize(ID, 100, 100, 1));
        assertEquals(100L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void partialManaNormalizesUnchanged() {
        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.normalize(ID, 37, 100, 1));
        assertEquals(37L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void zeroManaNormalizesToZero() {
        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.normalize(ID, 0, 100, 1));
        assertEquals(0L, snapshot.currentUnits());
    }

    @Test
    void dynamicBonusMaximumIsRespected() {
        // A stat/equipment/effect-bumped maximum (e.g. base 100 + 20 INT bonus + armor bonus).
        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.normalize(ID, 150, 180, 1));
        assertEquals(150L, snapshot.currentUnits());
        assertEquals(180L, snapshot.maximumUnits());
    }

    @Test
    void currentAboveMaximumPassesThroughUnclampedMatchingExistingOwnerPermissiveness() {
        // PlayerResourceRecalculator / ManaServerTick both intentionally allow current to transiently
        // exceed maximum (e.g. immediately after a Fortify effect expires) until their own next
        // clamping pass — the adapter must not silently "repair" that existing, accepted behavior.
        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.normalize(ID, 120, 100, 1));
        assertEquals(120L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void zeroMaximumProducesMalformedOwnerStateFailure() {
        // Reachable in practice (unlike Breath's case): a sufficiently negative INT modifier can
        // drive PlayerManaManager.getMaxMana to zero or below — see PlayerStatsCharacterizationTest
        // .negativeEndAndIntModifiersProduceNegativeStaminaAndManaBonuses.
        ResourceQueryResult result = ManaResourceAdapter.normalize(ID, 0, 0, 1);
        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void negativeMaximumProducesMalformedOwnerStateFailure() {
        ResourceQueryResult result = ManaResourceAdapter.normalize(ID, 0, -30, 1);
        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void negativeCurrentOtherThanTheUninitializedSentinelProducesMalformedOwnerStateFailure() {
        // PlayerResourceComponent.isManaInitialized() itself treats every negative value as
        // "uninitialized" (its own `mana >= 0` contract), so normalize's only real caller (resolve)
        // never passes a negative current — tested defensively here anyway, the same way
        // BreathResourceAdapterTest tests values no real Player would produce.
        ResourceQueryResult result = ManaResourceAdapter.normalize(ID, -7, 100, 1);
        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void unitScaleIsAlwaysOne() {
        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.normalize(ID, 50, 100, 1));
        assertEquals(1L, snapshot.unitScale());
    }

    @Test
    void resultIsAnExactIntegerNoFloatConversion() {
        // Mana is an int-native legacy quantity end to end (PlayerResourceComponent.mana is an int)
        // — the normalization core must never round-trip through float/double.
        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.normalize(ID, 63, 100, 1));
        assertEquals(63L, snapshot.currentUnits());
        assertEquals(100L, snapshot.maximumUnits());
    }

    @Test
    void resourceIdIsPreservedOnTheSnapshot() {
        ResourceSnapshot snapshot = successSnapshot(ManaResourceAdapter.normalize(ID, 50, 100, 1));
        assertEquals(ID, snapshot.resourceId());
    }
}
