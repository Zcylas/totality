package zcylas.totality.api.rpg.resources.presentation;

/**
 * Presentation-only, mechanical-to-display value conversion. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §7.4/§19.2.
 *
 * <pre>
 * display value = mechanical value × numerator / denominator
 *               = (units / unitScale) × numerator / denominator
 *               = units × numerator / (unitScale × denominator)
 * </pre>
 *
 * Operates entirely on integer fixed-point {@code units} (never a {@code float}/{@code double})
 * so the result is deterministic and reproducible server/client, with checked multiplication
 * ({@link Math#multiplyExact}) guarding against overflow and round-half-away-from-zero as the one
 * documented rounding rule. This conversion is presentation-only: it must never be used to derive
 * an authoritative mechanical value (damage, healing, Food restoration, affordability, clamping,
 * persistence).
 */
public record ResourceDisplayConversion(long numerator, long denominator) {

    public static final ResourceDisplayConversion IDENTITY = new ResourceDisplayConversion(1, 1);

    /** Health and Food both use this — see canonical §19.2's "Canonical conversions" table. */
    public static final ResourceDisplayConversion HEALTH_FOOD = new ResourceDisplayConversion(5, 1);

    public ResourceDisplayConversion {
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be positive, was " + denominator);
        }
        if (numerator < 0) {
            throw new IllegalArgumentException("numerator must not be negative, was " + numerator);
        }
    }

    /**
     * Converts a raw fixed-point unit amount (at the given {@code unitScale}) to its display
     * value, rounding half-away-from-zero. {@code unitScale} and {@link #denominator()} are both
     * strictly positive, so the combined divisor can never be zero.
     */
    public long convertUnitsToDisplay(long units, long unitScale) {
        if (unitScale < 1) {
            throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
        }
        long scaledNumerator = Math.multiplyExact(units, numerator);
        long scaledDenominator = Math.multiplyExact(unitScale, denominator);
        return roundHalfAwayFromZero(scaledNumerator, scaledDenominator);
    }

    /**
     * Rounds {@code numerator / denominator} (denominator always positive) to the nearest
     * integer, ties away from zero.
     *
     * <p>Correction pass note: the original implementation computed this by negating a negative
     * numerator ({@code Math.negateExact(numerator)}) before dividing. That negation itself
     * overflows for {@code numerator == Long.MIN_VALUE} even when the true divided-and-rounded
     * result is perfectly representable (e.g. {@code Long.MIN_VALUE / 2} is exactly
     * {@code -4611686018427387904}, well within range) — an unnecessary, avoidable exception.
     * This implementation instead uses {@link Math#floorDiv(long, long)}/{@link Math#floorMod(long, long)}
     * (both defined and overflow-free for any {@code long} dividend with a positive divisor,
     * {@code Long.MIN_VALUE} included) and compares the remainder against {@code denominator - remainder}
     * rather than doubling the remainder, so the tie-check itself cannot overflow either. The only
     * remaining overflow sources are the checked multiplications in {@link #convertUnitsToDisplay}
     * that produce this method's inputs — this method itself never throws for any valid
     * {@code (numerator, denominator > 0)} pair.
     */
    private static long roundHalfAwayFromZero(long numerator, long denominator) {
        long quotient = Math.floorDiv(numerator, denominator);
        long remainder = Math.floorMod(numerator, denominator);
        // remainder is in [0, denominator); compares 2*remainder against denominator without ever
        // computing 2*remainder (which could overflow for a remainder near Long.MAX_VALUE/2).
        long comparison = Math.subtractExact(remainder, Math.subtractExact(denominator, remainder));
        if (comparison > 0) {
            return quotient + 1;
        }
        if (comparison < 0) {
            return quotient;
        }
        // Exact tie: away from zero means toward +infinity for a non-negative numerator, toward
        // -infinity (i.e. keep the floor quotient) for a negative one.
        return numerator >= 0 ? quotient + 1 : quotient;
    }

    /**
     * Inverse of {@link #convertUnitsToDisplay} in real-number (non-fixed-point) space: given a
     * display-space value, returns the mechanical value this conversion implies
     * ({@code displayValue * denominator / numerator}). Intended for simple compatibility call
     * sites that work in floating mechanical units rather than authoritative fixed-point units
     * (e.g. {@code RpgDisplayUtils.toVanillaHp}) — it is not used by, and must not be used to
     * derive, any authoritative fixed-point unit value.
     *
     * @throws ArithmeticException if {@link #numerator()} is {@code 0} — a zero-numerator
     *         conversion always displays {@code 0} regardless of mechanical value, so it has no
     *         well-defined inverse.
     */
    public double invertToMechanical(double displayValue) {
        if (numerator == 0) {
            throw new ArithmeticException("Cannot invert a conversion with a zero numerator");
        }
        return displayValue * denominator / (double) numerator;
    }
}
