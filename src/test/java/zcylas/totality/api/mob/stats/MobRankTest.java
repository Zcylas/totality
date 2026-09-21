package zcylas.totality.api.mob.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the canonical Mob Rank order and the explicit, non-ordinal comparison/parsing surface added
 * for the Soul Gem foundation pass. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §4-5.
 */
class MobRankTest {

    @Test
    void canonicalOrderIsFLessThanEveryOtherRankInSequence() {
        MobRank[] ascending = {MobRank.F, MobRank.E, MobRank.D, MobRank.C, MobRank.B,
                MobRank.A, MobRank.S, MobRank.Z, MobRank.ZERO};
        for (int i = 0; i < ascending.length - 1; i++) {
            assertTrue(ascending[i].order() < ascending[i + 1].order(),
                    ascending[i] + " must be strictly weaker than " + ascending[i + 1]);
        }
    }

    @Test
    void zeroIsAboveZNotBelowF() {
        assertTrue(MobRank.ZERO.isStrongerThan(MobRank.Z));
        assertTrue(MobRank.ZERO.isStrongerThan(MobRank.F));
        assertFalse(MobRank.ZERO.isWeakerThan(MobRank.F));
    }

    @Test
    void isAtMostAndIsAtLeastAreInclusive() {
        assertTrue(MobRank.F.isAtMost(MobRank.F));
        assertTrue(MobRank.D.isAtMost(MobRank.D));
        assertFalse(MobRank.E.isAtMost(MobRank.F));
        assertTrue(MobRank.F.isAtLeast(MobRank.F));
        assertFalse(MobRank.F.isAtLeast(MobRank.E));
    }

    @Test
    void compareStrengthMatchesOrder() {
        assertTrue(MobRank.F.compareStrength(MobRank.E) < 0);
        assertTrue(MobRank.S.compareStrength(MobRank.A) > 0);
        assertEquals(0, MobRank.C.compareStrength(MobRank.C));
    }

    @Test
    void getIdReturnsZeroStringOnlyForTheZeroConstant() {
        assertEquals("0", MobRank.ZERO.getId());
        for (MobRank r : MobRank.values()) {
            if (r != MobRank.ZERO) assertEquals(r.name(), r.getId());
        }
    }

    @Test
    void fromIdParsesEveryCanonicalIdentifier() {
        assertEquals(MobRank.F, MobRank.fromId("F"));
        assertEquals(MobRank.E, MobRank.fromId("E"));
        assertEquals(MobRank.D, MobRank.fromId("D"));
        assertEquals(MobRank.C, MobRank.fromId("C"));
        assertEquals(MobRank.B, MobRank.fromId("B"));
        assertEquals(MobRank.A, MobRank.fromId("A"));
        assertEquals(MobRank.S, MobRank.fromId("S"));
        assertEquals(MobRank.Z, MobRank.fromId("Z"));
        assertEquals(MobRank.ZERO, MobRank.fromId("0"));
    }

    @Test
    void fromIdIsCaseInsensitiveMatchingTheOldValueOfToUpperCaseBehavior() {
        assertEquals(MobRank.E, MobRank.fromId("e"));
        assertEquals(MobRank.S, MobRank.fromId("s"));
    }

    @Test
    void fromIdFallsBackToEOnUnrecognizedInputPreservingTheOldMobStatBlockDefault() {
        assertEquals(MobRank.E, MobRank.fromId("not-a-rank"));
        assertEquals(MobRank.E, MobRank.fromId(""));
        assertEquals(MobRank.E, MobRank.fromId(null));
    }

    @Test
    void fromOrderRoundTripsEveryRank() {
        for (MobRank r : MobRank.values()) {
            assertEquals(r, MobRank.fromOrder(r.order()), "order " + r.order() + " must decode back to " + r);
        }
    }

    @Test
    void fromOrderFallsBackToEForAnUnknownOrderValue() {
        assertEquals(MobRank.E, MobRank.fromOrder(999));
        assertEquals(MobRank.E, MobRank.fromOrder(-1));
    }

    @Test
    void everyRankHasADistinctOrderValue() {
        MobRank[] all = MobRank.values();
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                assertNotEquals(all[i].order(), all[j].order(), all[i] + " and " + all[j] + " must not share an order value");
            }
        }
    }
}
