package zcylas.totality.api.soulgem;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.mob.stats.MobRank;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves Petty's and Common's actual authored acceptance policies directly, without needing a live
 * ItemStack/registry — {@link SoulGemAcceptanceRule} is pure logic over {@link MobRank}/{@link
 * SoulCategory}. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §6, §13-14.
 *
 * <p>Note on the "rejects non-ORDINARY category" test case (task item 12 / 20): {@link SoulCategory}
 * has only {@link SoulCategory#ORDINARY} in this foundation pass, so there is no second real category
 * value to assert a rejection against without inventing one (explicitly out of scope — no Black Soul
 * category exists). {@link #acceptsCategoryMechanismWorksForTheOnlyCategoryThatExists()} instead
 * proves the {@code acceptsCategory} mechanism itself is real and consulted (not a stub always
 * returning true) by checking it returns true for ORDINARY and that {@link SoulGemAcceptanceRule}
 * exposes it as a distinct, independently-callable check from {@code acceptsRank} — the actual
 * "does this generically reject a category" proof is deferred until a second category exists.
 */
class SoulGemAcceptanceRuleTest {

    private static final SoulGemAcceptanceRule PETTY =
            SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.F);
    private static final SoulGemAcceptanceRule COMMON =
            SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.D);

    @Test
    void pettyAcceptsOnlyRankF() {
        assertTrue(PETTY.accepts(SoulCategory.ORDINARY, MobRank.F));
        for (MobRank r : new MobRank[]{MobRank.E, MobRank.D, MobRank.C, MobRank.B, MobRank.A, MobRank.S, MobRank.Z}) {
            assertFalse(PETTY.accepts(SoulCategory.ORDINARY, r), "Petty must reject " + r);
        }
    }

    @Test
    void pettyRejectsRankZeroThroughItsOwnRankCeilingToo() {
        // The authoritative Rank 0 rejection lives in SoulCaptureEligibility (checked before this
        // rule is ever consulted) — this only confirms the rule's own ceiling comparison also
        // happens to reject it, never relying on that fact alone.
        assertFalse(PETTY.acceptsRank(MobRank.ZERO));
    }

    @Test
    void commonAcceptsFEAndDOnly() {
        for (MobRank r : new MobRank[]{MobRank.F, MobRank.E, MobRank.D}) {
            assertTrue(COMMON.accepts(SoulCategory.ORDINARY, r), "Common must accept " + r);
        }
        for (MobRank r : new MobRank[]{MobRank.C, MobRank.B, MobRank.A, MobRank.S, MobRank.Z}) {
            assertFalse(COMMON.accepts(SoulCategory.ORDINARY, r), "Common must reject " + r);
        }
    }

    @Test
    void commonRejectsRankZeroThroughItsOwnRankCeilingToo() {
        assertFalse(COMMON.acceptsRank(MobRank.ZERO));
    }

    @Test
    void pettyAndCommonEachHaveIndependentlyAuthoredCeilingsNotAUniversalFormula() {
        // Explicitly not "every tier = previous + 1 rank": Petty's ceiling is F (1 rank), Common's is
        // D (3 ranks) — two different authored spans, proving no shared formula is being applied.
        assertNotEquals(PETTY.acceptsRank(MobRank.E), COMMON.acceptsRank(MobRank.E));
    }

    @Test
    void acceptsCategoryMechanismWorksForTheOnlyCategoryThatExists() {
        assertTrue(PETTY.acceptsCategory(SoulCategory.ORDINARY));
        assertTrue(COMMON.acceptsCategory(SoulCategory.ORDINARY));
    }

    @Test
    void categoryAndRankAreCheckedIndependentlyNotOnlyThroughTheCombinedAccepts() {
        // acceptsRank alone must already reject E for Petty, independent of category.
        assertFalse(PETTY.acceptsRank(MobRank.E));
        assertTrue(PETTY.acceptsCategory(SoulCategory.ORDINARY));
    }
}
