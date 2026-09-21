package zcylas.totality.api.soulgem;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.mob.stats.MobRank;

import static org.junit.jupiter.api.Assertions.*;

class SoulCaptureEligibilityTest {

    @Test
    void rankZeroIsNeverEligible() {
        assertFalse(SoulCaptureEligibility.isEligibleForCapture(MobRank.ZERO));
    }

    @Test
    void everyOtherRankIsEligible() {
        for (MobRank r : new MobRank[]{MobRank.F, MobRank.E, MobRank.D, MobRank.C,
                MobRank.B, MobRank.A, MobRank.S, MobRank.Z}) {
            assertTrue(SoulCaptureEligibility.isEligibleForCapture(r), r + " must be eligible");
        }
    }
}
