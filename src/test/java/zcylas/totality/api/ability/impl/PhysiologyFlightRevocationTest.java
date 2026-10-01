package zcylas.totality.api.ability.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Losing a flight physiology ends biological flight only when it was the last flight authority. Game-mode
 *  flight (Creative/Spectator) is preserved separately by {@code PlayerMovementComponent#endBiologicalFlight}. */
class PhysiologyFlightRevocationTest {

    @Test
    void endsBiologicalFlightOnlyWhenThisWasTheLastFlightAuthority() {
        assertTrue(PhysiologyPassive.shouldEndBiologicalFlight(true, false), "only flight source lost");
        assertFalse(PhysiologyPassive.shouldEndBiologicalFlight(true, true), "another accessible source still grants flight");
        assertFalse(PhysiologyPassive.shouldEndBiologicalFlight(false, false), "a physiology without flight never ends flight");
    }
}
