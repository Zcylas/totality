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
 * Tests 24, 26-28 of the Phase 3B-1 task: native Health/Breath reads, no-local-player handling, and
 * rounding parity with the server-side adapter — all driven through a synthetic
 * {@link NativeResourceAccess}, with no Minecraft client required.
 *
 * <p><b>Corrected 2026-09-17 (real-client manual test correction):</b> the former Food tests (25,
 * plus corrections 10/11) are removed — this reader no longer answers {@code totality:food} queries
 * at all (see {@link NativeClientResourceReader}'s own Javadoc). Replaced by
 * {@link #foodIsNoLongerAnsweredByTheNativeReaderAfterTheFoodMigration} below, which pins the
 * opposite: a Food query reaching this reader must fail rather than silently succeed with a stale
 * vanilla-domain value.
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

    // 25 (corrected 2026-09-17): Food is no longer answered by this reader at all — see
    // foodIsNoLongerAnsweredByTheNativeReaderAfterTheFoodMigration below, near the malformed-source
    // correction tests, for the direct regression proof.

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

    // Corrected 2026-09-17: the former "Food below zero"/"Food above native maximum" correction
    // tests 10/11 are removed along with this reader's Food-handling branch — see
    // foodIsNoLongerAnsweredByTheNativeReaderAfterTheFoodMigration below.

    // Correction test 12 / requirement 12: valid Health/Breath results remain unchanged — this is
    // exactly what healthQueryReturnsNativeSourceFreshTrustAndMatchesAdapterRounding and
    // breathQueryReturnsRawAirSupplyValues (above, both unmodified) already prove; no new test is
    // needed to restate them.

    // ── 2026-09-17 real-client manual test correction: Food is no longer this reader's job ────────

    /**
     * The direct regression proof for the real-client HUD bug: {@code totality:food} must never be
     * answered by this reader again, at any {@code foodLevel} value, since Food is now
     * {@code GENERIC_COMPONENT} authority and this reader only knows vanilla's lossy 0-20 mirror.
     * Before this correction, this exact query would have returned a "valid-looking" {@code Scalar}
     * (current=14, maximum=20) that silently won over the real Generic value wherever it was
     * queried — which is exactly what happened in production. It must now fail closed instead.
     */
    @Test
    void foodIsNoLongerAnsweredByTheNativeReaderAfterTheFoodMigration() {
        FakeNativeAccess access = new FakeNativeAccess();
        NativeClientResourceReader reader = new NativeClientResourceReader(access);

        ClientResourceQueryResult result = reader.query(scalarDefinition(PlayerResourceIds.FOOD, 1L));

        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result,
                "a Food query must never again surface as a Scalar from this reader — that is the exact bug "
                        + "the real-client HUD test found (20/20 and 10/20 instead of 100/100 and 50/100)");
        assertEquals(ClientResourceUnavailableReason.CLIENT_SOURCE_NOT_CONFIGURED,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }
}
