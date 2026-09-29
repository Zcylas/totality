package zcylas.totality.entity.vehicle;

import net.minecraft.util.Mth;

/**
 * The skateboard's ride physics for one tick, in the board's own frame and free of world access (so it can be tested
 * on its own). Speeds are in blocks per tick along the board's heading; turn rates in degrees per tick.
 * <ul>
 *   <li>Pushing (forward input) accelerates towards {@link #MAX_PUSH_SPEED}, less the faster the board already goes;
 *       it never slows a board that is faster (down a drop, say).</li>
 *   <li>Braking (back input) takes {@link #BRAKE_DECELERATION} off every tick down to a stop; the board does not
 *       reverse.</li>
 *   <li>Coasting loses a small share of its speed to rolling resistance, less on slippery ground (ice).</li>
 *   <li>Steering eases the turn rate towards its target: a full turn rate when rolling, a slower kick-turn when
 *       (nearly) stopped.</li>
 *   <li>The wheels grip sideways: lateral velocity mostly dies away every tick on the ground.</li>
 * </ul>
 */
public final class SkateboardMotion {

    /** Top speed by pushing (sprinting is 0.28). */
    public static final double MAX_PUSH_SPEED = 0.34;
    public static final double PUSH_ACCELERATION = 0.014;
    public static final double BRAKE_DECELERATION = 0.02;
    /** Share of speed lost per tick when coasting on ordinary ground (friction 0.6) and on ice (0.98). */
    public static final double ROLLING_RESISTANCE = 0.008;
    public static final double ICE_ROLLING_RESISTANCE = 0.002;
    /** Below this speed the board counts as stopped: it turns at the kick-turn rate. */
    public static final double ROLLING_SPEED = 0.08;
    public static final float MAX_TURN_RATE = 6.0F;
    public static final float KICK_TURN_RATE = 3.0F;
    public static final float TURN_RESPONSE = 0.35F;
    /** Share of lateral velocity kept per tick on the ground. */
    public static final double SIDE_GRIP = 0.15;
    /** Share of speed and turn kept per tick in the air (no pushing, braking or steering). */
    public static final double AIR_DRAG = 0.99;
    public static final float AIR_TURN_DAMPING = 0.85F;

    private SkateboardMotion() {}

    /**
     * The new speed along the heading. {@code forward} is the rider's forward input (vanilla {@code zza}: 1 pushing,
     * -1 braking, 0 none); {@code slipperiness} the ground block's friction (0.6 ordinary, 0.98 ice).
     */
    public static double speed(double speed, float forward, boolean onGround, float slipperiness) {
        if (!onGround) return speed * AIR_DRAG;
        if (forward < 0.0F) {
            return speed > 0.0 ? Math.max(0.0, speed - BRAKE_DECELERATION) : Math.min(0.0, speed + BRAKE_DECELERATION);
        }
        double resistance = slipperiness > 0.9F ? ICE_ROLLING_RESISTANCE : ROLLING_RESISTANCE;
        double coasted = speed * (1.0 - resistance);
        if (forward > 0.0F && coasted < MAX_PUSH_SPEED) {
            double headroom = 1.0 - Math.max(0.0, coasted) / MAX_PUSH_SPEED;
            coasted = Math.min(MAX_PUSH_SPEED, coasted + PUSH_ACCELERATION * forward * (0.35 + 0.65 * headroom));
        }
        return coasted;
    }

    /**
     * The new turn rate (degrees per tick, negative to the left, as vanilla boats turn). {@code strafe} is the rider's
     * sideways input (vanilla {@code xxa}: 1 left, -1 right).
     */
    public static float turnRate(float turnRate, float strafe, double speed, boolean onGround) {
        if (!onGround) return turnRate * AIR_TURN_DAMPING;
        float rate = Math.abs(speed) < ROLLING_SPEED ? KICK_TURN_RATE : MAX_TURN_RATE;
        float target = -strafe * rate;
        return turnRate + (target - turnRate) * TURN_RESPONSE;
    }

    /** The lateral velocity left after the wheels' grip (on the ground) or air drag. */
    public static double sideSpeed(double sideSpeed, boolean onGround) {
        return sideSpeed * (onGround ? SIDE_GRIP : AIR_DRAG);
    }

    /** How far the deck leans into a turn (degrees, positive to the right) for a turn rate. */
    public static float lean(float turnRate) {
        return Mth.clamp(turnRate * 2.0F, -12.0F, 12.0F);
    }
}
