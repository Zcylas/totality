package zcylas.totality.client.dice;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.DiceRollContext;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.client.dice.DiceCheckSessions.Delivery;
import zcylas.totality.networking.dice.DiceCheckRequestPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dice UI V2 hardening (Fix A): a queued check survives its predecessor resuming a dialogue and opens once the screen is
 * free; foreign, repeated and stale results are never presented; a result whose screen closed after rolling is shown
 * once on the action bar.
 */
class DiceCheckSessionsTest {

    private static DiceCheckRequestPayload check(String name) {
        return new DiceCheckRequestPayload(UUID.randomUUID(),
                new DiceRollContext(name, "Charisma Check", Dice.D20, 12, RollType.NORMAL, List.of()));
    }

    @Test
    void aQueuedCheckWaitsForTheResumedDialogueAndThenOpensExactlyOnce() {
        DiceCheckSessions s = new DiceCheckSessions();
        DiceCheckRequestPayload first = check("Persuasion"), second = check("Insight");
        assertTrue(s.request(first, false), "1. the first check opens (over the dialogue that asked for it)");
        assertFalse(s.request(second, true), "2. a second check arrives while the first is on screen: queued");
        assertEquals(1, s.queued());
        assertNull(s.next(false), "the first screen is still open");
        assertEquals(Delivery.SCREEN, s.deliver(first.sessionId(), true));
        s.closed(first.sessionId(), true, true); // 3. the first check completes (Continue)
        // 4. its callback resumed the NPC dialogue: a DialogueScreen is open, so the queued check must wait, not replace it
        for (int tick = 0; tick < 200; tick++) assertNull(s.next(false), "never replaces the resumed dialogue");
        assertEquals(1, s.queued(), "and is not dropped while waiting");
        // 5. the player leaves the dialogue: the queued check opens, once
        assertSame(second, s.next(true));
        assertNull(s.next(true), "opened once");
        assertEquals(0, s.queued());
        assertEquals(Delivery.SCREEN, s.deliver(second.sessionId(), true), "its own result reaches its screen");
        assertEquals(Delivery.IGNORE, s.deliver(second.sessionId(), true), "a duplicate delivery is ignored");
    }

    @Test
    void aRepeatedRequestNeverOpensOrQueuesTwice() {
        DiceCheckSessions s = new DiceCheckSessions();
        DiceCheckRequestPayload a = check("A");
        assertTrue(s.request(a, false));
        assertFalse(s.request(a, false), "the same session again: ignored");
        assertFalse(s.request(a, true));
        assertEquals(0, s.queued());
        DiceCheckRequestPayload b = check("B");
        assertFalse(s.request(b, true));
        assertFalse(s.request(b, true));
        assertEquals(1, s.queued(), "queued once");
    }

    @Test
    void foreignDuplicateAndStaleResultsAreNeverPresented() {
        DiceCheckSessions s = new DiceCheckSessions();
        DiceCheckRequestPayload open = check("Open");
        s.request(open, false);
        assertEquals(Delivery.IGNORE, s.deliver(UUID.randomUUID(), false), "another session's result: no screen, no action bar");
        assertEquals(Delivery.IGNORE, s.deliver(open.sessionId(), false), "its result while another screen shows: not presented");
        assertEquals(Delivery.SCREEN, s.deliver(open.sessionId(), true));
        assertEquals(Delivery.IGNORE, s.deliver(open.sessionId(), true), "duplicate");
        s.closed(open.sessionId(), true, true);
        assertEquals(Delivery.IGNORE, s.deliver(open.sessionId(), false), "stale: after the screen presented and closed");
        DiceCheckRequestPayload queued = check("Queued");
        s.request(queued, true);
        assertEquals(Delivery.IGNORE, s.deliver(queued.sessionId(), false), "a queued (never rolled) check cannot have a result yet");
    }

    @Test
    void aScreenClosedAfterRollingButBeforeItsResultShowsTheResultOnceOnTheActionBar() {
        DiceCheckSessions s = new DiceCheckSessions();
        DiceCheckRequestPayload c = check("Closed early");
        s.request(c, false);
        s.closed(c.sessionId(), true, false); // closed any way before the result; removed() still rolled
        assertEquals(Delivery.ACTION_BAR, s.deliver(c.sessionId(), false), "the legitimate result is not lost");
        assertEquals(Delivery.IGNORE, s.deliver(c.sessionId(), false), "and shown once");
        s.clear();
        assertEquals(Delivery.IGNORE, s.deliver(c.sessionId(), false), "disconnect forgets everything");
        assertEquals(0, s.queued());
    }

    @Test
    void wiring() throws Exception {
        String handler = Files.readString(Path.of("src/main/java/zcylas/totality/networking/dice/DiceRollResultClientHandler.java")).replace("\r\n", "\n");
        assertTrue(handler.contains("switch (SESSIONS.deliver(payload.sessionId(), shows))"));
        assertTrue(handler.contains("SESSIONS.next(client.player != null && client.gui.screen() == null)"), "drained every tick when no screen is open");
        assertFalse(handler.contains("openNextQueued"), "no longer only at the moment a dice screen closes");
        String screen = Files.readString(Path.of("src/main/java/zcylas/totality/screen/dice/DiceRollScreen.java"));
        assertTrue(screen.contains("if (show.requestRoll(Util.getMillis())) sendClick();\n        super.removed();\n"
                + "        DiceRollResultClientHandler.closed(sessionId, show.rollRequested(), show.result() != null);"),
                "Esc/any close before rolling still rolls, and the close is recorded");
    }
}
