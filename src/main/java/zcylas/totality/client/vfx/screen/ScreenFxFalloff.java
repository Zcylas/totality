package zcylas.totality.client.vfx.screen;

/**
 * Distance attenuation: full strength within {@code inner} blocks of the origin, smoothly down to zero at {@code outer}.
 * {@link #NONE} applies no attenuation (non-spatial requests).
 */
public record ScreenFxFalloff(double inner, double outer) {

    public static final ScreenFxFalloff NONE = new ScreenFxFalloff(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    public ScreenFxFalloff {
        inner = Math.max(0.0, inner);
        outer = Math.max(inner, outer);
    }

    public double factor(double distance) {
        if (distance <= inner) return 1.0;
        if (distance >= outer) return 0.0;
        double x = 1.0 - (distance - inner) / (outer - inner);
        return x * x * (3.0 - 2.0 * x);
    }
}
