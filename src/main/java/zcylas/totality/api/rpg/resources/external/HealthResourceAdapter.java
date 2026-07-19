package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Wraps vanilla {@link Player#getHealth()}/{@link Player#getMaxHealth()}. Health/Combat remains
 * fully authoritative for damage, healing, absorption, death, and attributes — this adapter only
 * ever reads. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §25.1.
 *
 * <h2>Fixed-point scale and rounding rule</h2>
 * Health is backed by a vanilla {@code float} and Totality code can apply fractional Health
 * changes (e.g. {@code ConditionServerTick}'s {@code heal(0.2f)}). The registered
 * {@code totality:health} definition uses {@link #UNIT_SCALE} = {@value #UNIT_SCALE} fixed-point
 * units per mechanical Health point — the canonical recommendation for fractional continuous
 * values (§7.1) — giving three decimal digits of precision, well beyond any fractional Health
 * amount used anywhere in this codebase.
 *
 * <p><b>Documented rounding rule: nearest unit, halves away from zero</b> (matches
 * {@link zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion}'s own rounding
 * rule end-to-end, so no intermediate step silently switches convention). Correction pass note:
 * the original Phase 2A implementation used {@code Math.round((double) value * scale)} followed by
 * an overflow check comparing the already-rounded {@code long}-valued result against
 * {@code Long.MIN_VALUE}/{@code Long.MAX_VALUE} — that check is ineffective, because
 * {@link Math#round(double)} itself already <em>saturates</em> out-of-range input to
 * {@code Long.MIN_VALUE}/{@code Long.MAX_VALUE} rather than signalling overflow, so the comparison
 * could never observe an out-of-range value. {@link #toUnits} now converts through
 * {@link BigDecimal} instead: {@code new BigDecimal((double) mechanicalHealth)} captures the
 * <em>exact</em> binary value of the float (no truncation, no intermediate rounding), the scale
 * multiplication is exact integer arithmetic, {@link RoundingMode#HALF_UP} implements "nearest,
 * ties away from zero" correctly for both positive and negative values, and
 * {@link BigDecimal#longValueExact()} throws {@link ArithmeticException} on genuine overflow
 * instead of silently saturating.
 */
public final class HealthResourceAdapter implements ExternalPlayerResourceAdapter {

    public static final Identifier ID = PlayerResourceIds.HEALTH_ADAPTER;

    /** Fixed-point units per mechanical Health point. Must match the registered {@code totality:health} definition's {@code unitScale}. */
    public static final long UNIT_SCALE = 1000L;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public ResourceSnapshot snapshot(Player player, PlayerResourceDefinition definition) {
        long current = toUnits(player.getHealth(), definition.unitScale());
        long maximum = toUnits(player.getMaxHealth(), definition.unitScale());
        return new ResourceSnapshot(definition.id(), current, maximum, definition.unitScale());
    }

    @Override
    public Set<ExternalResourceOperationSupport> supportedOperations() {
        return Set.of(ExternalResourceOperationSupport.QUERY);
    }

    @Override
    public ExternalResourceClientMirrorMode clientMirrorMode() {
        return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
    }

    /**
     * Converts a mechanical Health {@code float} to fixed-point units. Rejects non-finite input
     * and {@code unitScale < 1}; rounds to the nearest unit with ties away from zero
     * ({@link RoundingMode#HALF_UP}); throws {@link ArithmeticException} on genuine overflow
     * rather than saturating or truncating. See the class Javadoc for why this is not implemented
     * with {@code Math.round(double)}.
     */
    public static long toUnits(float mechanicalHealth, long unitScale) {
        if (!Float.isFinite(mechanicalHealth)) {
            throw new IllegalArgumentException("Health value must be finite, was " + mechanicalHealth);
        }
        if (unitScale < 1) {
            throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
        }
        // new BigDecimal(double) captures the EXACT binary value of the widened float — no
        // truncation, no Double.toString()-style re-rounding (unlike BigDecimal.valueOf(double)).
        BigDecimal exact = new BigDecimal((double) mechanicalHealth);
        BigDecimal scaled = exact.multiply(BigDecimal.valueOf(unitScale));
        BigDecimal rounded = scaled.setScale(0, RoundingMode.HALF_UP);
        try {
            return rounded.longValueExact();
        } catch (ArithmeticException overflow) {
            throw new ArithmeticException(
                    "Health unit conversion overflow for value " + mechanicalHealth
                            + " at unitScale " + unitScale + " (rounded to " + rounded + ", outside long range)");
        }
    }

    /** Inverse of {@link #toUnits}, used only to verify deterministic round-tripping in tests. */
    public static float toMechanical(long units, long unitScale) {
        return (float) ((double) units / (double) unitScale);
    }

    public static final HealthResourceAdapter INSTANCE = new HealthResourceAdapter();
}
