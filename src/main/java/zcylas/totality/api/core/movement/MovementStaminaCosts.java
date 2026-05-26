package zcylas.totality.api.core.movement;

public final class MovementStaminaCosts {

    public static final int NORMAL_SPRINT_DRAIN_INTERVAL_TICKS = 3;
    public static final int POWER_SPRINT_COST = 2;
    public static final int SUPER_LEAP_COST = 18;
    public static final int FLIGHT_TOGGLE_MINIMUM = 5;
    public static final int FLIGHT_DRAIN_INTERVAL_TICKS = 3;
    public static final int FLIGHT_DRAIN_COST = 1;
    public static final int POWER_SPRINT_DRAIN_INTERVAL_TICKS = 5;
    // ── Ground Slam ─────────────────────────────────────────────────────────────

    public static final int GROUND_SLAM_COST = 20;
    public static final int GROUND_SLAM_COOLDOWN_TICKS = 40;

    public static final double GROUND_SLAM_MIN_FALL_DISTANCE = 3.0D;
    public static final double GROUND_SLAM_DOWNWARD_VELOCITY = -1.8D;

    public static final double GROUND_SLAM_BASE_RADIUS = 2.5D;
    public static final double GROUND_SLAM_RADIUS_PER_FALL_BLOCK = 0.12D;
    public static final double GROUND_SLAM_RADIUS_PER_STR_MOD = 0.15D;
    public static final double GROUND_SLAM_MAX_RADIUS = 7.0D;

    public static final float GROUND_SLAM_BASE_DAMAGE = 4.0F;
    public static final float GROUND_SLAM_DAMAGE_PER_FALL_BLOCK = 1.25F;
    public static final float GROUND_SLAM_DAMAGE_PER_STR_MOD = 2.0F;
    public static final float GROUND_SLAM_MAX_DAMAGE = 80.0F;

    public static final float GROUND_SLAM_BASE_SELF_DAMAGE = 2.0F;
    public static final float GROUND_SLAM_SELF_DAMAGE_PER_FALL_BLOCK = 1.35F;
    public static final float GROUND_SLAM_SELF_DAMAGE_CON_FLAT_REDUCTION = 0.5F;
    public static final double GROUND_SLAM_SELF_DAMAGE_CON_PERCENT_PER_MOD = 0.04D;
    public static final double GROUND_SLAM_SELF_DAMAGE_MAX_CON_PERCENT_REDUCTION = 0.75D;

    public static final int GROUND_SLAM_BASE_CRATER_RADIUS = 1;
    public static final int GROUND_SLAM_MAX_CRATER_RADIUS = 5;

    public static final float GROUND_SLAM_BASE_HARDNESS_LIMIT = 1.5F;
    public static final float GROUND_SLAM_HARDNESS_PER_STR_MOD = 0.35F;
    public static final float GROUND_SLAM_HARDNESS_PER_FALL_BLOCK = 0.08F;
    public static final float GROUND_SLAM_MAX_HARDNESS_LIMIT = 6.0F;

    private MovementStaminaCosts() {}
}