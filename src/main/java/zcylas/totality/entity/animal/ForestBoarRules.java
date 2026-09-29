package zcylas.totality.entity.animal;

/**
 * The Forest Boar's behaviour numbers and decisions, kept free of Minecraft types so they can be unit-tested.
 * <p>
 * Every value here is a <b>provisional gameplay choice</b> made for the first playable version, to be tuned after
 * play-testing. None of them comes from the reference: the source only says the boar wanders and grazes, and when it
 * senses or spots a player it scatters, either running away or charging at the player before fleeing.
 */
public final class ForestBoarRules {

    private ForestBoarRules() {}

    // ── Detection ──
    /** A player this close is sensed whether or not the boar can see them. */
    public static final double PROXIMITY_RADIUS = 7.0;
    /** A player this close is spotted if the boar has line of sight and they are within its field of view. */
    public static final double SIGHT_RADIUS = 14.0;
    /** Half-angle of the field of view used for spotting, in degrees either side of the body's facing. */
    public static final double SIGHT_HALF_ANGLE_DEGREES = 70.0;
    /** Detection runs every this many ticks (it only runs in the peaceful states). */
    public static final int DETECTION_INTERVAL_TICKS = 4;

    // ── Startle and reaction ──
    /** How long the boar freezes in alert before reacting to a detected player (0.7 s). */
    public static final int ALERT_TICKS = 14;
    /** A landed hit startles it for a shorter moment (0.3 s). */
    public static final int HURT_ALERT_TICKS = 6;
    /** Chance that a startled boar charges instead of fleeing at once. */
    public static final float CHARGE_CHANCE = 0.35F;
    /** Chance that a boar hit by a player charges back once (instead of fleeing at once). */
    public static final float HURT_CHARGE_CHANCE = 0.5F;

    // ── Charge ──
    /** The wind-up before the rush (0.9 s, the length of the charge_windup animation). */
    public static final int WINDUP_TICKS = 18;
    /**
     * Movement speed multiplier while charging (x the 0.25 base movement speed). Vanilla ground speed grows with the
     * square of this, so 1.75 is about 8 blocks/s on flat ground: a burst faster than a sprinting player (5.6).
     */
    public static final double CHARGE_SPEED = 1.75;
    /** A charge is only started at a player between these horizontal distances... */
    public static final double CHARGE_MIN_START_DISTANCE = 1.5;
    public static final double CHARGE_MAX_START_DISTANCE = 12.0;
    /** ...and no more than this far above or below the boar. */
    public static final double CHARGE_MAX_HEIGHT_DIFFERENCE = 1.5;
    /** The rush carries on this far past where the player stood when it began (it does not steer). */
    public static final double CHARGE_OVERSHOOT = 2.5;
    /** Hard caps on one rush. */
    public static final double CHARGE_MAX_DISTANCE = 14.0;
    public static final int CHARGE_MAX_TICKS = 40;
    /** Ticks with (almost) no forward progress after which the rush counts as blocked. */
    public static final int CHARGE_BLOCKED_TICKS = 3;
    /** Horizontal movement per tick below which a charging boar is considered not to be making progress. */
    public static final double CHARGE_MIN_PROGRESS_PER_TICK = 0.05;
    /** How far past its visible body (ForestBoarBody) a rush still counts as contact: the tusk tips, in blocks. */
    public static final double CHARGE_CONTACT_REACH = 0.1;

    // ── Flee and escape ──
    /** Movement speed multiplier while fleeing: about 6.7 blocks/s on flat ground, so it can outrun a sprinting player. */
    public static final double FLEE_SPEED = 1.6;
    /** How far each flee path reaches, and how often it is recomputed away from the players. */
    public static final int FLEE_PATH_DISTANCE = 16;
    public static final int FLEE_REPATH_TICKS = 20;
    /** Players within this distance are what the boar runs away from. */
    public static final double FLEE_THREAT_RADIUS = 32.0;
    /** Escape: it may be removed only after fleeing this long (5 s)... */
    public static final int ESCAPE_MIN_FLEE_TICKS = 100;
    /** ...at least this far from every player... */
    public static final double ESCAPE_MIN_PLAYER_DISTANCE = 48.0;
    /** (checked every this many ticks) */
    public static final int ESCAPE_CHECK_INTERVAL_TICKS = 10;
    /** A boar that has fled this long without escaping settles down again once no player is near. */
    public static final int FLEE_CALM_TICKS = 600;
    public static final double FLEE_CALM_PLAYER_DISTANCE = 24.0;

    // ── Peaceful ──
    /** Walking speed multiplier while wandering: about 1 block/s, an unhurried walk. */
    public static final double WANDER_SPEED = 0.6;
    public static final int WANDER_RANGE = 10;
    /** Pause between peaceful activities, in ticks. */
    public static final int PAUSE_MIN_TICKS = 40;
    public static final int PAUSE_MAX_TICKS = 110;
    /** Grazing bout length, in ticks. */
    public static final int GRAZE_MIN_TICKS = 80;
    public static final int GRAZE_MAX_TICKS = 180;
    /** The sniff (Astra's animation) lasts 3 s. */
    public static final int SNIFF_TICKS = 60;
    /** Relative odds of the next peaceful activity. */
    public static final int STROLL_ODDS = 5;
    public static final int GRAZE_ODDS = 3;
    public static final int SNIFF_ODDS = 2;

    // ── Decisions ──

    /** Whether a player at this distance and bearing is detected (sensed nearby, or spotted in view with sight). */
    public static boolean detects(double distance, double bearingFromFacingDegrees, boolean lineOfSight) {
        if (distance <= PROXIMITY_RADIUS) return true;
        return distance <= SIGHT_RADIUS && lineOfSight && Math.abs(bearingFromFacingDegrees) <= SIGHT_HALF_ANGLE_DEGREES;
    }

    /** The reaction once the alert ends: charge only once per boar, only at a valid target, and only on the roll. */
    public static boolean chooseCharge(boolean alreadyCharged, boolean targetChargeable, float roll, float chance) {
        return !alreadyCharged && targetChargeable && roll < chance;
    }

    /** Whether a player at this offset can be charged. */
    public static boolean chargeable(double horizontalDistance, double heightDifference, boolean lineOfSight) {
        return lineOfSight && horizontalDistance >= CHARGE_MIN_START_DISTANCE && horizontalDistance <= CHARGE_MAX_START_DISTANCE
                && Math.abs(heightDifference) <= CHARGE_MAX_HEIGHT_DIFFERENCE;
    }

    /** How far a rush runs: through where the player stood, a little beyond, capped. */
    public static double chargeDistance(double horizontalDistanceToTarget) {
        return Math.min(horizontalDistanceToTarget + CHARGE_OVERSHOOT, CHARGE_MAX_DISTANCE);
    }

    /** Why a rush ends this tick, or {@code null} to keep going. Every ending leads to the escape. */
    public static String chargeEnd(boolean contact, double travelled, double planned, int ticks, int ticksWithoutProgress) {
        if (contact) return "hit";
        if (travelled >= planned) return "missed";
        if (ticksWithoutProgress >= CHARGE_BLOCKED_TICKS) return "blocked";
        if (ticks >= CHARGE_MAX_TICKS) return "timed out";
        return null;
    }

    /**
     * Whether a fleeing boar may be silently removed: it has fled long enough, and every player is far away and has no
     * line of sight to it ({@code nearestPlayerDistance} is infinite when there are none).
     */
    public static boolean mayEscape(boolean fleeing, int fleeTicks, double nearestPlayerDistance, boolean anyPlayerCanSee) {
        return fleeing && fleeTicks >= ESCAPE_MIN_FLEE_TICKS && nearestPlayerDistance >= ESCAPE_MIN_PLAYER_DISTANCE && !anyPlayerCanSee;
    }
}
