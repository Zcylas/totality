package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.rpg.classes.PlayerChargesComponent;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceModel;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure logic for Rage, tested directly against {@link RageResourceAdapter#resolve} rather than
 * through a real {@code ServerPlayer} (constructing one outside the Minecraft runtime is
 * impractical — matching {@code ManaResourceAdapterTest}/{@code StandardSpellSlotsResourceAdapterTest}'s
 * established precedent). {@link RageResourceAdapter#snapshot} itself (including the client/server
 * routing it adds on top) is exercised end-to-end only by the manual smoke-test checklist in the
 * Phase 2E report, plus the production client-side routing test in {@code
 * PlayerResourceRegistryExternalAdapterFreezeTest}.
 */
class RageResourceAdapterTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "rage");
    private static final Identifier CHARGE_ID = BarbarianRageAbility.CHARGE_ID;

    private static ResourceSnapshot successSnapshot(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.Success.class, result);
        return ((ResourceQueryResult.Success) result).snapshot();
    }

    private static ResourceQueryFailureReason failureReason(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        return ((ResourceQueryResult.Failure) result).reason();
    }

    @Test
    void adapterSupportsOnlyQuery() {
        assertTrue(RageResourceAdapter.INSTANCE.supportedOperations().contains(ExternalResourceOperationSupport.QUERY));
        assertEquals(1, RageResourceAdapter.INSTANCE.supportedOperations().size(),
                "Rage is query-only in Phase 2E — no RESTORE/DRAIN/SET support");
        assertEquals(ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION,
                RageResourceAdapter.INSTANCE.clientMirrorMode());
    }

    @Test
    void adapterIdMatchesTheRageResourceId() {
        assertEquals(ID, RageResourceAdapter.INSTANCE.id());
        assertEquals(ID, RageResourceAdapter.ID);
    }

    @Test
    void unitScaleConstantIsExactlyOne() {
        assertEquals(1L, RageResourceAdapter.UNIT_SCALE);
    }

    @Test
    void legacyBackingKeyIsUnchangedAndDistinctFromTheResourceId() {
        // The Resource API resource id and the legacy charge-pool key are deliberately different,
        // independent identifiers — neither is renamed by this phase.
        assertEquals(Identifier.fromNamespaceAndPath("totality", "barbarian_rage"), CHARGE_ID);
        assertNotEquals(ID, CHARGE_ID);
    }

    // ── resolve(): missing Rage pool entry semantics (missing-component is not directly ────────
    // ── unit-tested here — see RageResourceAdapter's class Javadoc and the Phase 2E report §17) ─

    @Test
    void freshComponentWithNoRagePoolReturnsStateUninitialized() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        assertTrue(component.getAllPools().isEmpty(), "sanity: a fresh component has no pools at all");

        ResourceQueryResult result = RageResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.STATE_UNINITIALIZED, failureReason(result));
    }

    @Test
    void anUnrelatedPoolAloneStillReturnsStateUninitializedForRage() {
        // A charge map that has some other pool, but no barbarian_rage entry, must still report
        // Rage as not-yet-granted — presence of an unrelated pool must not be mistaken for presence
        // of Rage's own pool.
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(Identifier.fromNamespaceAndPath("totality", "some_other_pool"), 5, RestType.LONG, -1);

        ResourceQueryResult result = RageResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.STATE_UNINITIALIZED, failureReason(result));
    }

    // ── resolve(): valid present states ──────────────────────────────────────────────────────

    @Test
    void presentZeroZeroPoolIsAValidSuccess() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 0, RestType.SHORT, 1);

        ResourceSnapshot snapshot = successSnapshot(RageResourceAdapter.resolve(ID, component));

        assertEquals(0L, snapshot.currentUnits());
        assertEquals(0L, snapshot.maximumUnits());
    }

    @Test
    void fullPoolIsAValidSuccess() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 4, RestType.SHORT, 1);

        ResourceSnapshot snapshot = successSnapshot(RageResourceAdapter.resolve(ID, component));

        assertEquals(4L, snapshot.currentUnits());
        assertEquals(4L, snapshot.maximumUnits());
    }

    @Test
    void partiallySpentPoolIsAValidSuccess() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 5, RestType.SHORT, 1);
        component.consume(CHARGE_ID);
        component.consume(CHARGE_ID);

        ResourceSnapshot snapshot = successSnapshot(RageResourceAdapter.resolve(ID, component));

        assertEquals(3L, snapshot.currentUnits());
        assertEquals(5L, snapshot.maximumUnits());
    }

    @Test
    void zeroCurrentPositiveMaximumIsAValidSuccessRepresentingFullySpentCharges() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 2, RestType.SHORT, 1);
        component.consume(CHARGE_ID);
        component.consume(CHARGE_ID);

        ResourceSnapshot snapshot = successSnapshot(RageResourceAdapter.resolve(ID, component));

        assertEquals(0L, snapshot.currentUnits());
        assertEquals(2L, snapshot.maximumUnits());
    }

    // ── resolve(): malformed owner state ──────────────────────────────────────────────────────

    /**
     * Reflection is unavoidable here: every public {@link PlayerChargesComponent} mutator
     * ({@code registerPool}, {@code ensurePool}, {@code consume}, {@code restore}, {@code
     * updatePoolMax}, {@code setMax}) already enforces internally-consistent values, so the only way
     * to construct the malformed-owner-state scenarios below (corrupted persisted NBT, essentially)
     * is to write a malformed {@code ChargePool} directly into the private backing map.
     */
    @SuppressWarnings("unchecked")
    private static void injectMalformedPoolDirectly(PlayerChargesComponent component, Identifier id, int current, int max) throws Exception {
        Field poolsField = PlayerChargesComponent.class.getDeclaredField("pools");
        poolsField.setAccessible(true);
        Map<Identifier, PlayerChargesComponent.ChargePool> pools =
                (Map<Identifier, PlayerChargesComponent.ChargePool>) poolsField.get(component);
        pools.put(id, new PlayerChargesComponent.ChargePool(current, max, RestType.SHORT, 1));
    }

    @Test
    void negativeCurrentProducesMalformedOwnerStateFailure() throws Exception {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        injectMalformedPoolDirectly(component, CHARGE_ID, -1, 4);

        ResourceQueryResult result = RageResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void negativeMaximumProducesMalformedOwnerStateFailure() throws Exception {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        injectMalformedPoolDirectly(component, CHARGE_ID, 0, -1);

        ResourceQueryResult result = RageResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void currentGreaterThanMaximumProducesMalformedOwnerStateFailureWithNoException() throws Exception {
        // Unlike Mana/Stamina's adapters (which permit a transient current > live-recomputed
        // maximum), Rage has no live maximum computation at query time — normal legacy behavior
        // cannot produce this without corrupted data, so it is always rejected.
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        injectMalformedPoolDirectly(component, CHARGE_ID, 9, 4);

        ResourceQueryResult result = RageResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    // ── resolve(): purity and exactness ──────────────────────────────────────────────────────

    @Test
    void resolveDoesNotMutateTheComponent() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 5, RestType.SHORT, 1);
        component.consume(CHARGE_ID);

        RageResourceAdapter.resolve(ID, component);

        assertEquals(4, component.getCurrent(CHARGE_ID), "resolve must never write the component it read");
        assertEquals(5, component.getMax(CHARGE_ID));
    }

    @Test
    void repeatedResolveCallsProduceIdenticalResults() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 6, RestType.SHORT, 1);
        component.consume(CHARGE_ID);

        ResourceSnapshot first = successSnapshot(RageResourceAdapter.resolve(ID, component));
        ResourceSnapshot second = successSnapshot(RageResourceAdapter.resolve(ID, component));

        assertEquals(first.currentUnits(), second.currentUnits());
        assertEquals(first.maximumUnits(), second.maximumUnits());
    }

    @Test
    void resourceIdAndUnitScaleArePreservedOnTheSnapshot() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 3, RestType.SHORT, 1);

        ResourceSnapshot snapshot = successSnapshot(RageResourceAdapter.resolve(ID, component));

        assertEquals(ID, snapshot.resourceId());
        assertEquals(RageResourceAdapter.UNIT_SCALE, snapshot.unitScale());
    }

    @Test
    void currentAndMaximumValuesArePreservedExactly() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 6, RestType.SHORT, 1);
        component.consume(CHARGE_ID);
        component.consume(CHARGE_ID);
        component.consume(CHARGE_ID);

        ResourceSnapshot snapshot = successSnapshot(RageResourceAdapter.resolve(ID, component));

        assertEquals(3L, snapshot.currentUnits());
        assertEquals(6L, snapshot.maximumUnits());
    }

    // ── boundaries: unrelated pools, recharge metadata, no active-state leakage ─────────────

    @Test
    void unrelatedChargePoolsAreIgnoredAndNeverAffectTheRageSnapshot() {
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(CHARGE_ID, 4, RestType.SHORT, 1);
        component.registerPool(Identifier.fromNamespaceAndPath("totality", "some_future_pool"), 99, RestType.LONG, -1);
        component.consume(CHARGE_ID);

        ResourceSnapshot snapshot = successSnapshot(RageResourceAdapter.resolve(ID, component));

        assertEquals(3L, snapshot.currentUnits());
        assertEquals(4L, snapshot.maximumUnits());
    }

    @Test
    void onlyBarbarianRageChargeIdIsEverRead() {
        // A pool registered under any id other than BarbarianRageAbility.CHARGE_ID must never be
        // mistaken for Rage's own pool, even if it is the only pool present.
        PlayerChargesComponent component = new PlayerChargesComponent(null);
        component.registerPool(Identifier.fromNamespaceAndPath("totality", "not_rage"), 4, RestType.SHORT, 1);

        ResourceQueryResult result = RageResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.STATE_UNINITIALIZED, failureReason(result));
    }

    // ── snapshot(): client-side routing (no real Player construction needed here) ───────────

    @Test
    void nullPlayerIsTreatedAsNotAServerPlayerAndReturnsStateUnavailableOnThisSide() {
        PlayerResourceDefinition definition = PlayerResourceDefinition
                .builder(ID, ResourceModel.SCALAR)
                .externalAdapter(ID)
                .unitScale(1)
                .authoredBaseMaximum(2)
                .build();

        ResourceQueryResult result = RageResourceAdapter.INSTANCE.snapshot(null, definition);

        assertEquals(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, failureReason(result));
    }
}
