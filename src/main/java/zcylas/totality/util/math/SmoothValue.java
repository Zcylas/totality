package zcylas.totality.util.math;

/**
 * Real-time millisecond-based lerp tracker.
 *
 * <p>Usage: call {@link #set(double)} to change the target, {@link #tick()} once
 * per frame (or per game tick if you prefer), then read {@link #current()} for
 * the interpolated value.
 *
 * <p>Good for: HP/Stamina/Mana bar animations, skill-node hover highlights,
 * tooltip fade-ins, any value that should glide to its target rather than snap.
 *
 * <p>Ported from CreativeCore {@code SmoothValue} (team.creative.creativecore).
 */
public class SmoothValue {

    /** Target value being lerped toward. */
    protected double aimed;
    /** Current interpolated value. */
    protected double current;
    /** Value at the moment {@link #set(double)} was last called. */
    protected double before;
    /** System.currentTimeMillis() timestamp of the last {@link #set} call, or 0 if idle. */
    protected long timestamp;

    /** Duration of the transition in milliseconds. */
    public final long time;

    public SmoothValue(long time, double initialValue) {
        this.time = time;
        setStart(initialValue);
    }

    public SmoothValue(long time) {
        this(time, 0);
    }

    /** Instantly snap to {@code value} with no animation. */
    public void setStart(double value) {
        this.aimed   = value;
        this.current = value;
        this.before  = value;
        this.timestamp = 0;
    }

    /** Animate from current value toward {@code aimed + value}. */
    public void add(double value) {
        set(aimed + value);
    }

    /** Begin a transition toward {@code value}. */
    public void set(double value) {
        this.timestamp = System.currentTimeMillis();
        this.aimed  = value;
        this.before = this.current;
    }

    /**
     * Advance the interpolation. Call this every client frame (or tick).
     * When the transition duration has elapsed the value snaps to {@link #aimed}
     * and the animator goes idle.
     */
    public void tick() {
        if (timestamp != 0) {
            long now = System.currentTimeMillis();
            if (timestamp + time <= now) {
                current   = aimed;
                before    = current;
                timestamp = 0;
            } else {
                current = before + (aimed - before) * ((now - timestamp) / (double) time);
            }
        }
    }

    /** The current interpolated value. */
    public double current() {
        return current;
    }

    /** The target value being lerped toward. */
    public double aimed() {
        return aimed;
    }

    /** {@code true} while a transition is in progress. */
    public boolean isAnimating() {
        return timestamp != 0;
    }
}
