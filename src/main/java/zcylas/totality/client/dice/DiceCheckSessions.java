package zcylas.totality.client.dice;

import zcylas.totality.networking.dice.DiceCheckRequestPayload;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The client's record of its player-facing dice checks: which sessions it was asked to show, which one is on screen,
 * which were closed before their result, and which are finished — and the queue of checks waiting for the screen.
 * It decides where a result goes, so only this client's own check is ever presented, once:
 * <ul>
 *   <li>its open screen → the screen (which accepts the first result only);</li>
 *   <li>its screen was closed after rolling but before the result → the action bar, once;</li>
 *   <li>anything else (another session, a repeated delivery, a finished or never-seen session) → ignored.</li>
 * </ul>
 * A check that arrives while another dice check is on screen waits in the queue and is offered again every tick until
 * no screen is open, so it neither replaces a dialogue resumed by its predecessor nor gets stranded behind one.
 * Pure logic (no client classes).
 */
public final class DiceCheckSessions {

    public enum Delivery { SCREEN, ACTION_BAR, IGNORE }

    private enum State { QUEUED, SHOWN, CLOSED_AWAITING_RESULT, DONE }

    private final Map<UUID, State> sessions = new HashMap<>();
    private final Deque<DiceCheckRequestPayload> queue = new ArrayDeque<>();

    /** A check request arrived: true = show it now; false = it was queued (a dice screen is busy) or is a repeat. */
    public boolean request(DiceCheckRequestPayload payload, boolean screenBusy) {
        if (sessions.containsKey(payload.sessionId())) return false;
        if (screenBusy) {
            sessions.put(payload.sessionId(), State.QUEUED);
            queue.add(payload);
            return false;
        }
        sessions.put(payload.sessionId(), State.SHOWN);
        return true;
    }

    /** The next queued check to show, if the screen is free (called every tick). */
    public DiceCheckRequestPayload next(boolean screenFree) {
        if (!screenFree || queue.isEmpty()) return null;
        DiceCheckRequestPayload next = queue.poll();
        sessions.put(next.sessionId(), State.SHOWN);
        return next;
    }

    /** The session's screen closed; {@code rolled} = its click was sent, {@code resulted} = it had its result. */
    public void closed(UUID session, boolean rolled, boolean resulted) {
        if (sessions.get(session) != State.SHOWN) return;
        sessions.put(session, rolled && !resulted ? State.CLOSED_AWAITING_RESULT : State.DONE);
    }

    /** Where a result for {@code session} goes; {@code screenShowsIt} = the open screen is that session's. */
    public Delivery deliver(UUID session, boolean screenShowsIt) {
        State state = sessions.get(session);
        if (state == State.SHOWN && screenShowsIt) {
            sessions.put(session, State.DONE);
            return Delivery.SCREEN;
        }
        if (state == State.CLOSED_AWAITING_RESULT) {
            sessions.put(session, State.DONE);
            return Delivery.ACTION_BAR;
        }
        return Delivery.IGNORE;
    }

    public int queued() {
        return queue.size();
    }

    /** Disconnect: forget everything (the server drops its pending checks too). */
    public void clear() {
        sessions.clear();
        queue.clear();
    }
}
