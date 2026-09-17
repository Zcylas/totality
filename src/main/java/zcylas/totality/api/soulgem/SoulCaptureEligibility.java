package zcylas.totality.api.soulgem;

import zcylas.totality.api.mob.stats.MobRank;

/**
 * The soul-capture eligibility gate every Soul Gem shares, checked BEFORE any vessel's own {@link
 * SoulGemAcceptanceRule}. {@link MobRank#ZERO} (Family Rank) is categorically uncapturable by any
 * ordinary Soul Gem in this foundation — this is a narrative/apex classification rejection, not a
 * vessel-capacity failure, so it must never depend on whether a particular gem's rank ceiling
 * happens to also reject it. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §8.
 */
public final class SoulCaptureEligibility {

    public static boolean isEligibleForCapture(MobRank rank) {
        return rank != MobRank.ZERO;
    }

    private SoulCaptureEligibility() {}
}
