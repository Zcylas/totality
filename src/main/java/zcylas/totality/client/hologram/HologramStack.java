package zcylas.totality.client.hologram;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Lifecycle of System holograms: one on display, the ones it interrupted (suspended, most recent
 * first) and the ones waiting (by priority, then arrival). Free of rendering and Minecraft types; the
 * owner drives it from the client thread with {@link #tick} and a nanosecond clock that times the
 * open/close animations.
 *
 * <ul>
 *   <li>A new hologram that strictly outranks the displayed one interrupts it: the displayed one is
 *       suspended with its remaining time and resumes, re-projecting, once the interrupter is gone.</li>
 *   <li>Otherwise it waits. When the display frees up, the best of "most recently suspended" and
 *       "first in queue" is shown; on equal priority the suspended one returns first.</li>
 *   <li>A hologram with the same {@link HologramSpec#key()} as the displayed one updates it in place
 *       (content, lifetime); one waiting or suspended is replaced and re-evaluated.</li>
 * </ul>
 */
public final class HologramStack {

    public static final long OPEN_NANOS = 380_000_000L;
    public static final long CLOSE_NANOS = 260_000_000L;
    public static final int MAX_WAITING = 16;

    public enum Phase { OPENING, SHOWN, CLOSING }

    public enum ShowResult { SHOWN, INTERRUPTED, QUEUED, UPDATED }

    public enum CloseReason { ACTION, DISMISSED, EXPIRED, CANCELLED }

    /** Owner callbacks, e.g. for sounds. Called on the thread that drives the stack. */
    public interface Listener {
        default void opened(Entry entry, boolean resumed) {}
        default void updated(Entry entry) {}
        default void closing(Entry entry, CloseReason reason) {}
        default void dropped(Entry entry) {}
    }

    /** One hologram's live state. Render-only fields are owned by the renderer. */
    public static final class Entry {
        HologramSpec spec;
        Phase phase = Phase.OPENING;
        long phaseStartNanos;
        long refreshNanos = Long.MIN_VALUE;
        long suspendedNanos = Long.MIN_VALUE;
        int ticksRemaining;
        boolean resumed;
        @Nullable CloseReason closeReason;

        // Render-owned presentation state (client thread).
        float displayedHeight = -1;
        @Nullable String hoveredAction;
        long hoverStartNanos;
        /** Set when the panel appears or changes under a resting crosshair; cleared once the aim leaves every button. */
        boolean hoverSuppressed = true;
        boolean aimedAt;
        float aimX = Float.NaN;
        float aimY = Float.NaN;
        @Nullable String pressedAction;
        long pressedNanos = Long.MIN_VALUE;
        @Nullable HologramVoice.Status voiceStatus;
        String voiceText = "";
        long voiceNanos = Long.MIN_VALUE;

        Entry(HologramSpec spec) {
            this.spec = spec;
            this.ticksRemaining = spec.lifetimeTicks();
        }

        public HologramSpec spec() { return spec; }
        public Phase phase() { return phase; }
        public int ticksRemaining() { return ticksRemaining; }
        public boolean resumed() { return resumed; }
        public @Nullable CloseReason closeReason() { return closeReason; }
        public @Nullable String hoveredAction() { return hoveredAction; }
        public boolean aimedAt() { return aimedAt; }
    }

    private final LongSupplier clock;
    private final Listener listener;
    private @Nullable Entry active;
    private final Deque<Entry> suspended = new ArrayDeque<>();
    private final List<Entry> waiting = new ArrayList<>();

    public HologramStack(LongSupplier nanoClock, Listener listener) {
        this.clock = nanoClock;
        this.listener = listener;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public @Nullable Entry active() { return active; }

    /** The most recently interrupted hologram, shown paused beside the active one. */
    public @Nullable Entry suspendedTop() { return suspended.peekFirst(); }

    public int suspendedCount() { return suspended.size(); }

    public int waitingCount() { return waiting.size(); }

    public boolean isEmpty() { return active == null && suspended.isEmpty() && waiting.isEmpty(); }

    public long now() {
        return clock.getAsLong();
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    public ShowResult show(HologramSpec spec) {
        String key = spec.key();
        if (active != null && active.spec.key().equals(key)) {
            active.spec = spec;
            active.ticksRemaining = spec.lifetimeTicks();
            active.refreshNanos = now();
            active.hoverSuppressed = true;
            active.voiceStatus = null;
            if (active.phase == Phase.CLOSING) setPhase(active, Phase.OPENING);
            active.closeReason = null;
            listener.updated(active);
            return ShowResult.UPDATED;
        }
        removeWaitingOrSuspended(key);
        Entry entry = new Entry(spec);
        if (active == null) {
            activate(entry, false);
            return ShowResult.SHOWN;
        }
        if (spec.priority().outranks(active.spec.priority()) && active.phase != Phase.CLOSING) {
            Entry interrupted = active;
            interrupted.suspendedNanos = now();
            suspended.addFirst(interrupted);
            activate(entry, false);
            return ShowResult.INTERRUPTED;
        }
        enqueue(entry);
        return ShowResult.QUEUED;
    }

    /** Starts closing whatever has this key, or drops it if it is not on display. */
    public boolean dismiss(String key, CloseReason reason) {
        if (active != null && active.spec.key().equals(key)) {
            beginClose(active, reason);
            return true;
        }
        return removeWaitingOrSuspended(key);
    }

    /** Starts closing the displayed hologram. */
    public boolean dismissActive(CloseReason reason) {
        if (active == null || active.phase == Phase.CLOSING) return false;
        beginClose(active, reason);
        return true;
    }

    /** Drops everything immediately without animation (disconnect, reset). */
    public void clear() {
        if (active != null) listener.dropped(active);
        for (Entry e : suspended) listener.dropped(e);
        for (Entry e : waiting) listener.dropped(e);
        active = null;
        suspended.clear();
        waiting.clear();
    }

    /**
     * One client tick. Advances animation phases from the clock and, when {@code visible}, the displayed
     * hologram's lifetime (paused while it is being aimed at).
     */
    public void tick(boolean visible) {
        Entry a = active;
        if (a == null) return;
        long elapsed = now() - a.phaseStartNanos;
        switch (a.phase) {
            case OPENING -> {
                if (elapsed >= OPEN_NANOS) setPhase(a, Phase.SHOWN);
            }
            case SHOWN -> {
                if (visible && !a.aimedAt) {
                    if (a.spec.lifetimeTicks() > 0 && --a.ticksRemaining <= 0) {
                        a.ticksRemaining = 0;
                        beginClose(a, CloseReason.EXPIRED);
                    }
                }
            }
            case CLOSING -> {
                if (elapsed >= CLOSE_NANOS) {
                    active = null;
                    promoteNext();
                }
            }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void activate(Entry entry, boolean resumed) {
        active = entry;
        entry.resumed = resumed;
        entry.closeReason = null;
        entry.aimedAt = false;
        entry.hoveredAction = null;
        entry.hoverSuppressed = true;
        setPhase(entry, Phase.OPENING);
        listener.opened(entry, resumed);
    }

    private void beginClose(Entry entry, CloseReason reason) {
        if (entry.phase == Phase.CLOSING) return;
        entry.closeReason = reason;
        entry.aimedAt = false;
        entry.hoveredAction = null;
        setPhase(entry, Phase.CLOSING);
        listener.closing(entry, reason);
    }

    private void promoteNext() {
        Entry s = suspended.peekFirst();
        Entry q = waiting.isEmpty() ? null : waiting.getFirst();
        if (s == null && q == null) return;
        if (s != null && (q == null || !q.spec.priority().outranks(s.spec.priority()))) {
            suspended.removeFirst();
            activate(s, true);
        } else {
            waiting.removeFirst();
            activate(q, false);
        }
    }

    private void enqueue(Entry entry) {
        int i = 0;
        while (i < waiting.size() && !entry.spec.priority().outranks(waiting.get(i).spec.priority())) i++;
        waiting.add(i, entry);
        if (waiting.size() > MAX_WAITING) listener.dropped(waiting.removeLast());
    }

    private boolean removeWaitingOrSuspended(String key) {
        boolean removed = false;
        for (Iterator<Entry> it = waiting.iterator(); it.hasNext(); ) {
            if (it.next().spec.key().equals(key)) { it.remove(); removed = true; }
        }
        for (Iterator<Entry> it = suspended.iterator(); it.hasNext(); ) {
            if (it.next().spec.key().equals(key)) { it.remove(); removed = true; }
        }
        return removed;
    }

    private void setPhase(Entry entry, Phase phase) {
        entry.phase = phase;
        entry.phaseStartNanos = now();
    }
}
