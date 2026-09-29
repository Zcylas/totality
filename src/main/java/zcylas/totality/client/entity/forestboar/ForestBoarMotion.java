package zcylas.totality.client.entity.forestboar;

import zcylas.totality.entity.animal.ForestBoarEntity.Behavior;

/**
 * Per-boar animation bookkeeping on the client: the gait phase and the blend weight of every animation layer.
 * <ul>
 *   <li><b>Gait phase</b> advances by the distance the boar actually moves along its facing (plus a little while it
 *       turns on the spot), divided by the stride the keyframes need for planted hooves not to slide. So the legs
 *       always match the ground speed, forwards or backwards, at any movement speed.</li>
 *   <li><b>Walk or run</b> is chosen by the synced behaviour (flee and charge run), crossfading over ~0.25 s; both
 *       gaits share the phase, so the crossfade keeps the feet in step.</li>
 *   <li><b>State layers</b> (graze, sniff, alert, wind-up, charge) ease in and out; the generic idle only plays when
 *       the boar is peaceful and standing, so it never overrides a state animation.</li>
 * </ul>
 * Plain arithmetic only (no rendering classes), updated once per rendered frame from interpolated positions.
 */
final class ForestBoarMotion {

    /** Ground speed (blocks per tick) at which the gait reaches its full amplitude; slower, it fades out. */
    static final float FULL_GAIT_SPEED = 0.03F;
    /** How far the hooves are from the body's turning centre, in blocks: turning on the spot steps the legs. */
    static final float TURN_FOOT_RADIUS = 0.35F;
    /** A jump of more than this (teleport, first sighting) resets the tracker instead of spinning the legs. */
    static final float TELEPORT_DISTANCE = 2.0F;

    private final float walkStride;
    private final float runStride;
    private boolean started;
    private double lastX;
    private double lastZ;
    private float lastYaw;
    private float lastAge;

    Behavior behavior = Behavior.WANDER;
    /** When the one-shot layers last started, so each keeps playing forward while it fades out. */
    float sniffSince;
    float alertSince;
    float windupSince;
    float phase;
    float move;
    float run;
    float idle = 1.0F;
    float graze;
    float sniff;
    float alert;
    float windup;
    float charge;
    float groundSpeed;

    ForestBoarMotion(float walkStride, float runStride) {
        this.walkStride = walkStride;
        this.runStride = runStride;
    }

    void update(double x, double z, float bodyYawDegrees, float ageInTicks, Behavior now) {
        if (!started || Math.hypot(x - lastX, z - lastZ) > TELEPORT_DISTANCE || ageInTicks < lastAge) {
            started = true;
            lastX = x;
            lastZ = z;
            lastYaw = bodyYawDegrees;
            lastAge = ageInTicks;
            enter(now, ageInTicks);
            return;
        }
        if (now != behavior) enter(now, ageInTicks);
        float dt = ageInTicks - lastAge;
        double dx = x - lastX, dz = z - lastZ;
        double yaw = Math.toRadians(bodyYawDegrees);
        double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);         // Minecraft yaw 0 faces +z
        double turn = Math.abs(wrapDegrees(bodyYawDegrees - lastYaw)) * Math.PI / 180.0 * TURN_FOOT_RADIUS;
        double travel = forward >= 0 ? forward + turn : forward - turn;
        float stride = walkStride + (runStride - walkStride) * run;
        phase = (float) (((phase + travel / stride) % 1.0 + 1.0) % 1.0);
        lastX = x;
        lastZ = z;
        lastYaw = bodyYawDegrees;
        lastAge = ageInTicks;
        if (dt <= 0.0F) return;

        groundSpeed = (float) ((Math.abs(forward) + turn) / dt);
        move = ease(move, clamp01(groundSpeed / FULL_GAIT_SPEED), dt, 2.0F, 4.0F);
        run = ease(run, now.running() ? 1.0F : 0.0F, dt, 4.0F, 6.0F);
        idle = ease(idle, now == Behavior.WANDER ? 1.0F : 0.0F, dt, 6.0F, 3.0F);
        graze = ease(graze, now == Behavior.GRAZE ? 1.0F : 0.0F, dt, 9.0F, 6.0F);
        sniff = ease(sniff, now == Behavior.SNIFF ? 1.0F : 0.0F, dt, 3.0F, 6.0F);
        alert = ease(alert, now == Behavior.ALERT ? 1.0F : 0.0F, dt, 1.0F, 6.0F);
        windup = ease(windup, now == Behavior.CHARGE_WINDUP ? 1.0F : 0.0F, dt, 2.0F, 3.0F);
        charge = ease(charge, now == Behavior.CHARGE ? 1.0F : 0.0F, dt, 2.0F, 6.0F);
    }

    float walkWeight() { return move * (1.0F - run); }
    float runWeight() { return move * run; }
    /** The generic idle only while peaceful and standing still. */
    float idleWeight() { return idle * (1.0F - move); }
    /** Head tracking gives way to the poses that aim the head themselves. */
    float lookWeight() { return 1.0F - Math.max(Math.max(graze, sniff), Math.max(windup, charge)); }
    static long millisSince(float since, float ageInTicks) { return (long) (Math.max(0.0F, ageInTicks - since) * 50.0F); }

    private void enter(Behavior now, float ageInTicks) {
        behavior = now;
        switch (now) {
            case SNIFF -> sniffSince = ageInTicks;
            case ALERT -> alertSince = ageInTicks;
            case CHARGE_WINDUP -> windupSince = ageInTicks;
            default -> { }
        }
    }

    /** Exponential approach with separate rise and fall time constants, in ticks. */
    static float ease(float value, float target, float dt, float riseTicks, float fallTicks) {
        float tau = target > value ? riseTicks : fallTicks;
        return value + (target - value) * (1.0F - (float) Math.exp(-dt / tau));
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    private static double wrapDegrees(double d) {
        d %= 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
