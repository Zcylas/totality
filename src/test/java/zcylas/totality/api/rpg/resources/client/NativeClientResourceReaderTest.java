package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.ResourceModel;
import zcylas.totality.api.rpg.resources.ResourcePolarity;
import zcylas.totality.api.rpg.resources.external.HealthResourceAdapter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests 24-28 of the Phase 3B-1 task: native Health/Food/Breath reads, no-local-player handling, and
 * rounding parity with the server-side adapter — all driven through a synthetic
 * {@link NativeResourceAccess}, with no Minecraft client required.
 */
class NativeClientResourceReaderTest {

    private static PlayerResourceDefinition scalarDefinition(Identifier id, long unitScale) {
        return PlayerResourceDefinition.builder(id, ResourceModel.SCALAR)
                .polarity(ResourcePolarity.HIGH_IS_GOOD)
                .unitScale(unitScale)
                .absoluteMinimum(0)
                .build();
    }

    private static final class FakeNativeAccess implements NativeResourceAccess {
        boolean hasLocalPlayer = true;
        float health = 13.5f;
        float maxHealth = 20f;
        int foodLevel = 14;
        int airSupply = 250;
        int maxAirSupply = 300;

        @Override public boolean hasLocalPlayer() { return hasLocalPlayer; }
        @Override public float health() { return health; }
        @Override public float maxHealth() { return maxHealth; }
        @Override public int foodLevel() { return foodLevel; }
        @Override public int airSupply() { return airSupply; }
        @Override public int maxAirSupply() { return maxAirSupply; }
    }

    // 24. Native Health query returns NATIVE_CLIENT_VIEW/FRESH.
    // 28. Native result normalization matches the existing server-adapter rounding/unit semantics.
    @Test
    void healthQueryReturnsNativeSourceFreshTrustAndMatchesAdapterRounding() {
        FakeNativeAccess access = new FakeNativeAccess();
        NativeClientResourceReader reader = new NativeClientResourceReader(access);

        PlayerResourceDefinition definition = scalarDefinition(PlayerResourceIds.HEALTH, HealthResourceAdapter.UNIT_SCALE);
        ClientResourceQueryResult result = reader.query(definition);

        assertInstanceOf(ClientResourceQueryResult.Scalar.class, result);
        ClientResourceQueryResult.Scalar scalar = (ClientResourceQueryResult.Scalar) result;
        assertEquals(ClientResourceSource.NATIVE_CLIENT_VIEW, scalar.source());
        assertEquals(ClientResourceTrust.FRESH, scalar.trust());
        assertEquals(HealthResourceAdapter.toUnits(access.health, HealthResourceAdapter.UNIT_SCALE), scalar.currentUnits());
        assertEquals(HealthResourceAdapter.toUnits(access.maxHealth, HealthResourceAdapter.UNIT_SCALE), scalar.maximumUnits());
    }

    // 25. Native Food query returns raw 0-20-scale values.
    @Test
    void foodQueryReturnsRawNativeZeroToTwentyScale() {
        FakeNativeAccess access = new FakeNativeAccess();
        NativeClientResourceReader reader = new NativeClientResourceReader(access);

        ClientResourceQueryResult result = reader.query(scalarDefinition(PlayerResourceIds.FOOD, 1L));

        ClientResourceQueryResult.Scalar scalar = (ClientResourceQueryResult.Scalar) result;
        assertEquals(14L, scalar.currentUnits());
        assertEquals(20L, scalar.maximumUnits(), "Food's native ceiling is 20, not the eventual ×5 display scale");
    }

    // 26. Native Breath query returns raw air values.
    @Test
    void breathQueryReturnsRawAirSupplyValues() {
        FakeNativeAccess access = new FakeNativeAccess();
        NativeClientResourceReader reader = new NativeClientResourceReader(access);

        ClientResourceQueryResult result = reader.query(scalarDefinition(PlayerResourceIds.BREATH, 1L));

        ClientResourceQueryResult.Scalar scalar = (ClientResourceQueryResult.Scalar) result;
        assertEquals(250L, scalar.currentUnits());
        assertEquals(300L, scalar.maximumUnits());
    }

    @Test
    void breathQueryClampsNegativeDrowningRangeToZero() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.airSupply = -10; // vanilla's drowning-timer range, not usable Breath.

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.BREATH, 1L));

        ClientResourceQueryResult.Scalar scalar = (ClientResourceQueryResult.Scalar) result;
        assertEquals(0L, scalar.currentUnits());
    }

    // 27. Native query without world/player returns NO_LOCAL_PLAYER.
    @Test
    void queryWithoutLocalPlayerReturnsNoLocalPlayer() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.hasLocalPlayer = false;

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.HEALTH, HealthResourceAdapter.UNIT_SCALE));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.NO_LOCAL_PLAYER,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // ── Phase 3B-1 external review correction (2026-07-23): malformed native source state ────────

    // Correction test 1 / requirement 1: Breath zero maximum returns MALFORMED_SOURCE_STATE.
    @Test
    void breathZeroMaximumReturnsMalformedSourceState() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.maxAirSupply = 0;

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.BREATH, 1L));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result,
                "a non-positive maximum must never become a fabricated 0/0 success");
        assertEquals(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 2 / requirement 2: Breath negative maximum returns MALFORMED_SOURCE_STATE.
    @Test
    void breathNegativeMaximumReturnsMalformedSourceState() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.maxAirSupply = -5;

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.BREATH, 1L));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 3 / requirement 3: Breath negative current with a valid maximum still clamps
    // to zero (this is `breathQueryClampsNegativeDrowningRangeToZero` above, retained unchanged).

    // Correction test 4 / requirement 4: Breath current above a valid maximum clamps to maximum.
    @Test
    void breathCurrentAboveValidMaximumClampsToMaximum() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.airSupply = 500;
        access.maxAirSupply = 300;

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.BREATH, 1L));

        ClientResourceQueryResult.Scalar scalar = (ClientResourceQueryResult.Scalar) result;
        assertEquals(300L, scalar.currentUnits());
        assertEquals(300L, scalar.maximumUnits());
    }

    // Correction test 5 / requirement 5: invalid Breath maximum never becomes a Scalar 0/0 success.
    @Test
    void invalidBreathMaximumNeverBecomesScalarZeroZeroSuccess() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.maxAirSupply = 0;

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.BREATH, 1L));

        assertFalse(result instanceof ClientResourceQueryResult.Scalar,
                "an invalid maximum must never surface as any Scalar result, valid-looking 0/0 or otherwise");
    }

    // Correction test 6 / requirement 6: non-finite Health current returns MALFORMED_SOURCE_STATE.
    @Test
    void nonFiniteHealthCurrentReturnsMalformedSourceState() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.health = Float.NaN;

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.HEALTH, HealthResourceAdapter.UNIT_SCALE));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 7 / requirement 7: non-finite Health maximum returns MALFORMED_SOURCE_STATE.
    @Test
    void nonFiniteHealthMaximumReturnsMalformedSourceState() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.maxHealth = Float.POSITIVE_INFINITY;

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.HEALTH, HealthResourceAdapter.UNIT_SCALE));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 8 / requirement 8: invalid/overflowing Health conversion returns MALFORMED_SOURCE_STATE.
    @Test
    void overflowingHealthConversionReturnsMalformedSourceState() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.maxHealth = Float.MAX_VALUE; // astronomically larger than any representable fixed-point unit count

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.HEALTH, HealthResourceAdapter.UNIT_SCALE));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 9 / requirement 9: Health current greater than represented maximum does not
    // escape as an exception or a fabricated result.
    @Test
    void healthCurrentAboveMaximumReturnsMalformedSourceStateRatherThanThrowing() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.health = 20f;
        access.maxHealth = 10f; // current > maximum, no overflow capability represented

        ClientResourceQueryResult result = assertDoesNotThrow(() -> new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.HEALTH, HealthResourceAdapter.UNIT_SCALE)));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 10 / requirement 10: Food below zero returns MALFORMED_SOURCE_STATE.
    @Test
    void foodBelowZeroReturnsMalformedSourceState() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.foodLevel = -1;

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.FOOD, 1L));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 11 / requirement 11: Food above native maximum returns MALFORMED_SOURCE_STATE.
    @Test
    void foodAboveNativeMaximumReturnsMalformedSourceState() {
        FakeNativeAccess access = new FakeNativeAccess();
        access.foodLevel = 21; // FoodResourceAdapter.NATIVE_MAXIMUM is 20

        ClientResourceQueryResult result = new NativeClientResourceReader(access)
                .query(scalarDefinition(PlayerResourceIds.FOOD, 1L));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 12 / requirement 12: valid Health/Food/Breath results remain unchanged — this
    // is exactly what healthQueryReturnsNativeSourceFreshTrustAndMatchesAdapterRounding,
    // foodQueryReturnsRawNativeZeroToTwentyScale, and breathQueryReturnsRawAirSupplyValues (above,
    // all unmodified) already prove; no new test is needed to restate them.
}
