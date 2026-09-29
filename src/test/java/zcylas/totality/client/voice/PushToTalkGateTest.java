package zcylas.totality.client.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.client.voice.PushToTalkGate.Action.*;

/** Vanilla's F3 debug shortcuts (F3+B: hitboxes) take precedence over B push-to-talk, in every order of presses. */
class PushToTalkGateTest {

    /** One tick in a world, no screen, focused. */
    private static PushToTalkGate.Action tick(PushToTalkGate g, boolean b, boolean f3, boolean listening) {
        return g.update(b, f3, true, false, true, listening);
    }

    @Test
    void ordinaryPressStillStartsVoice() {
        PushToTalkGate g = new PushToTalkGate();
        assertEquals(NONE, tick(g, false, false, false));
        assertEquals(PRESS, tick(g, true, false, false));
        assertEquals(NONE, tick(g, true, false, true), "held: one press only");
        assertEquals(NONE, tick(g, false, false, true));
        assertEquals(PRESS, tick(g, true, false, false), "a fresh press starts again");
    }

    @Test
    void f3HeldThenBNeverStartsVoice() {
        PushToTalkGate g = new PushToTalkGate();
        tick(g, false, true, false);
        assertEquals(NONE, tick(g, true, true, false), "F3+B is vanilla's hitbox toggle");
        assertEquals(NONE, tick(g, true, true, false));
        assertEquals(NONE, tick(g, true, false, false), "releasing F3 while B is held does not start voice late");
        assertEquals(NONE, tick(g, true, false, false));
        assertEquals(NONE, tick(g, false, false, false));
        assertEquals(PRESS, tick(g, true, false, false), "a fresh B press works again");
    }

    @Test
    void bAndF3InTheSameTickNeverStartVoice() {
        PushToTalkGate g = new PushToTalkGate();
        tick(g, false, false, false);
        assertEquals(NONE, tick(g, true, true, false));
        assertTrue(g.suppressed());
    }

    @Test
    void f3PressedWhileListeningCancelsOnceAndNeverResumes() {
        PushToTalkGate g = new PushToTalkGate();
        tick(g, false, false, false);
        assertEquals(PRESS, tick(g, true, false, false));
        assertEquals(NONE, tick(g, true, false, true));
        assertEquals(CANCEL, tick(g, true, true, true), "the partial utterance is cancelled, not recognized");
        assertEquals(NONE, tick(g, true, true, false), "cancelled once");
        assertEquals(NONE, tick(g, true, false, false), "releasing F3 with B held does not restart it");
        assertEquals(NONE, tick(g, false, false, false));
        assertEquals(PRESS, tick(g, true, false, false));
    }

    @Test
    void f3AloneAndOtherF3ChordsLeaveVoiceAlone() {
        PushToTalkGate g = new PushToTalkGate();
        assertEquals(NONE, tick(g, false, true, false));
        assertEquals(NONE, tick(g, false, true, false));
        assertFalse(g.suppressed(), "F3 without B suppresses nothing");
        assertEquals(PRESS, tick(g, true, false, false));
    }

    @Test
    void theExistingGuardsStillApply() {
        PushToTalkGate g = new PushToTalkGate();
        assertEquals(NONE, g.update(true, false, true, true, true, false), "no press with a screen open");
        assertEquals(NONE, g.update(false, false, true, false, true, false));
        assertEquals(NONE, g.update(true, false, true, false, false, false), "no press without focus");
    }
}
