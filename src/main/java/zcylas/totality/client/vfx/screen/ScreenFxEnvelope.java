package zcylas.totality.client.vfx.screen;

/**
 * Time-based envelope in seconds: rises over {@code attack}, holds full strength for {@code hold}, then eases out over
 * {@code release}. Never frame-count based.
 */
public record ScreenFxEnvelope(double attack, double hold, double release) {

    public ScreenFxEnvelope {
        attack = Math.max(0.0, attack);
        hold = Math.max(0.0, hold);
        release = Math.max(0.0, release);
    }

    public double duration() {
        return attack + hold + release;
    }

    /** Strength 0..1 at {@code t} seconds after the request started (0 before it starts and after it ends). */
    public double value(double t) {
        if (t < 0.0 || t > duration()) return 0.0;
        if (t < attack) return t / attack;
        if (t <= attack + hold) return 1.0;
        if (release <= 0.0) return 0.0;
        double x = 1.0 - (t - attack - hold) / release;
        return x * x * (3.0 - 2.0 * x);
    }
}
