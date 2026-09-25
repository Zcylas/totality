package zcylas.totality.client.mining;

/**
 * First-person mining presentation state and timeline. Pure math: no Minecraft imports, so it is unit-checkable
 * and the render mixin only asks it "what transform now?". It holds NO gameplay authority: it is told the
 * schedule (the server's real timings for a normal swing; the deterministic power timings for a power swing)
 * and it only draws it. Contact is a fixed point on that schedule, so the visual contact is the same tick the
 * server contacts (delayed by the network in the same way as the damage number).
 *
 * <pre>
 *  NORMAL        : draw-back/lift  ->  committed stroke  -> CONTACT ->  recovery          (windUp + recovery ticks)
 *  POWER_CHARGE  : lift into a big raised pose, held while Alt+LMB stays down
 *  POWER_STRIKE  : release -> heavy stroke -> CONTACT                                      (power windUp ticks)
 *  POWER_RECOVERY: heavy recovery, then settle to the neutral hand pose
 *  RETURN        : cancellation: ease from wherever we are to neutral
 * </pre>
 * Every mode start blends from the pose currently shown, so nothing ever snaps.
 */
public final class MiningHandAnimation {

    public enum Mode { NONE, NORMAL, POWER_CHARGE, POWER_STRIKE, POWER_RECOVERY, RETURN }

    /** Broad visual class only. HAND = literal bare hand / non-tool item: a dedicated ONE-arm strike (never a two-arm combo:
     *  one visual strike = one Mining Impact). */
    public enum Style { PICK, AXE, SHOVEL, HAND }

    /** Translation (x sideways, y up, z forward/back) in vanilla item units and rotations in degrees;
     *  pitch is positive when the tool is drawn back/up and negative during the strike. */
    public record Transform(float x, float y, float z, float pitch, float yaw, float roll) {
        public static final Transform ZERO = new Transform(0, 0, 0, 0, 0, 0);

        public static Transform lerp(Transform a, Transform b, float t) {
            return new Transform(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t,
                    a.pitch + (b.pitch - a.pitch) * t, a.yaw + (b.yaw - a.yaw) * t, a.roll + (b.roll - a.roll) * t);
        }

        public boolean isFinite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                    && Float.isFinite(pitch) && Float.isFinite(yaw) && Float.isFinite(roll);
        }
    }

    /** ALL animation constants (provisional feel values). */
    public static final class Tuning {
        private Tuning() {}
        /** Share of the pre-contact time spent drawing back before the committed stroke. */
        public static final float DRAW_FRACTION = 0.4f;
        /** Ticks to blend from whatever is currently shown into a newly started mode. */
        public static final float BLEND_TICKS = 2f;
        public static final float CHARGE_IN_TICKS = 8f;
        /** Extra visual ticks after the (gameplay) power recovery to settle back to neutral. */
        public static final float POWER_SETTLE_TICKS = 5f;
        public static final float RETURN_TICKS = 6f;
        /** Tiny contact punch (units backwards along the tool), 0 disables it. */
        public static final float CONTACT_PUNCH = 0.02f;
        public static final float PUNCH_TICKS = 3f;
        /** Bare-hand normal punch only: how far the fist carries on past contact (units forward) before recovering. */
        public static final float PUNCH_FOLLOW_THROUGH = 0.05f;
        /** Share of the recovery time the follow-through takes. */
        public static final float PUNCH_FOLLOW_FRACTION = 0.35f;
        /** Pose sets [x, y, z, pitch, yaw, roll] per broad tool class. */
        private static final float[][][] POSES = {
                // PICK: overhead / diagonal strike
                {{0.02f, 0.14f, 0.14f, 42, 0, 6}, {-0.05f, -0.16f, -0.34f, -72, 0, -10}, {0.04f, 0.26f, 0.24f, 68, 0, 10}, {-0.10f, -0.28f, -0.46f, -92, 0, -16}},
                // AXE: stronger sideways chop
                {{0.10f, 0.10f, 0.10f, 30, 0, 22}, {-0.14f, -0.10f, -0.30f, -60, 0, -28}, {0.14f, 0.20f, 0.20f, 52, 0, 34}, {-0.20f, -0.22f, -0.42f, -80, 0, -40}},
                // SHOVEL: forward-down scoop
                {{0.00f, 0.06f, 0.16f, 25, 0, 0}, {0.00f, -0.22f, -0.36f, -55, 0, 0}, {0.00f, 0.16f, 0.26f, 48, 0, 0}, {0.00f, -0.34f, -0.46f, -78, 0, 0}},
                // HAND, applied about the arm's shoulder pivot. NORMAL = a compact chambered FORWARD PUNCH: a small pull back
                // (RAISED = chamber), then the fist drives straight forward toward the block (STRIKE, almost no downward tilt).
                // POWER (unchanged): a much larger chamber (arm far back and up with a shoulder twist), then one heavy strike.
                {{0.05f, 0.02f, 0.20f, 10, 3, 3}, {-0.02f, 0.00f, -0.55f, -14, -4, -4}, {0.10f, 0.30f, 0.55f, 72, 14, 20}, {-0.14f, -0.24f, -0.70f, -78, -16, -22}},
        };
    }

    private enum Pose { RAISED, STRIKE, CHARGED, HEAVY }

    // ── easing ───────────────────────────────────────────────────────────────────────────────
    public static float clamp01(float v) { return Float.isNaN(v) ? 0f : Math.max(0f, Math.min(1f, v)); }
    public static float smoothstep(float t) { t = clamp01(t); return t * t * (3f - 2f * t); }
    public static float easeInCubic(float t) { t = clamp01(t); return t * t * t; }
    public static float easeOutCubic(float t) { t = clamp01(t); float u = 1f - t; return 1f - u * u * u; }

    private static Transform pose(Style style, Pose kind) {
        float[] p = Tuning.POSES[style.ordinal()][kind.ordinal()];
        return new Transform(p[0], p[1], p[2], p[3], p[4], p[5]);
    }

    // ── state ────────────────────────────────────────────────────────────────────────────────
    private Mode mode = Mode.NONE;
    private Style style = Style.PICK;
    private int windUp;
    private int recovery;
    private int age;                       // ticks since this mode began
    private float blendAge;                // ticks since the blend-in began
    private float blendDuration;
    private Transform blendFrom = Transform.ZERO;

    public Mode mode() { return mode; }
    public Style style() { return style; }
    public boolean isActive() { return mode != Mode.NONE; }
    public int age() { return age; }

    /** Tick (from the start of the swing) at which visual contact happens, or -1 when this mode has none. */
    public int contactTick() {
        return mode == Mode.NORMAL || mode == Mode.POWER_STRIKE ? windUp : -1;
    }

    /** Total ticks of the current timed mode (NORMAL = windUp + recovery); 0 for untimed modes. */
    public int durationTicks() {
        return switch (mode) {
            case NORMAL -> windUp + recovery;
            case POWER_STRIKE -> windUp;
            case POWER_RECOVERY -> Math.round(recovery + Tuning.POWER_SETTLE_TICKS);
            case RETURN -> Math.round(Tuning.RETURN_TICKS);
            default -> 0;
        };
    }

    private void begin(Mode next, float blend) {
        blendFrom = sample(0f);
        mode = next;
        age = 0;
        blendAge = 0f;
        blendDuration = blend;
    }

    /** A normal swing on the server's real schedule. Efficiency/Haste/Fatigue arrive as shorter/longer ticks. */
    public void startNormal(Style style, int windUpTicks, int recoveryTicks) {
        this.style = style;
        this.windUp = Math.max(1, windUpTicks);
        this.recovery = Math.max(1, recoveryTicks);
        begin(Mode.NORMAL, Tuning.BLEND_TICKS);
    }

    /** The server corrected the recovery of the normal swing in flight (its actual target differed); ignored otherwise. */
    public void correctRecovery(int recoveryTicks) {
        if (mode == Mode.NORMAL) this.recovery = Math.max(1, recoveryTicks);
    }

    public void startPowerCharge(Style style) {
        this.style = style;
        begin(Mode.POWER_CHARGE, Tuning.BLEND_TICKS);
    }

    /** Release of a power hold: heavy strike on the deterministic power schedule. Ignored unless charging. */
    public void releasePower(int windUpTicks, int recoveryTicks) {
        if (mode != Mode.POWER_CHARGE) return;
        this.windUp = Math.max(1, windUpTicks);
        this.recovery = Math.max(1, recoveryTicks);
        begin(Mode.POWER_STRIKE, 0f);      // the strike itself starts from the held charged pose
    }

    /** Cancellation (Alt released, screen opened, item changed, ownership lost...): ease back to neutral. */
    public void cancel() {
        if (mode == Mode.NONE || mode == Mode.RETURN) return;
        begin(Mode.RETURN, Tuning.RETURN_TICKS);
    }

    public void reset() {
        mode = Mode.NONE; age = 0; blendAge = 0f; blendDuration = 0f; blendFrom = Transform.ZERO;
    }

    public void tick() {
        if (mode == Mode.NONE) return;
        age++;
        blendAge++;
        switch (mode) {
            case NORMAL -> { if (age >= windUp + recovery) mode = Mode.NONE; }
            case POWER_STRIKE -> { if (age >= windUp) { mode = Mode.POWER_RECOVERY; age = 0; blendAge = 0f; blendDuration = 0f; } }
            case POWER_RECOVERY -> { if (age >= durationTicks()) mode = Mode.NONE; }
            case RETURN -> { if (age >= durationTicks()) mode = Mode.NONE; }
            default -> { }
        }
    }

    // ── timeline ─────────────────────────────────────────────────────────────────────────────
    private Transform target(float t) {
        Transform rest = Transform.ZERO;
        switch (mode) {
            case NORMAL: {
                float drawEnd = Math.max(0.5f, Tuning.DRAW_FRACTION * windUp);
                if (drawEnd > windUp - 0.25f) drawEnd = windUp * 0.5f;
                if (t < drawEnd) return Transform.lerp(rest, pose(style, Pose.RAISED), easeOutCubic(t / drawEnd));
                if (t < windUp) return Transform.lerp(pose(style, Pose.RAISED), pose(style, Pose.STRIKE), easeInCubic((t - drawEnd) / (windUp - drawEnd)));
                if (style == Style.HAND) return punchRecover(pose(style, Pose.STRIKE), t - windUp, recovery);
                return recover(pose(style, Pose.STRIKE), t - windUp, recovery);
            }
            case POWER_CHARGE:
                return Transform.lerp(rest, pose(style, Pose.CHARGED), easeOutCubic(t / Tuning.CHARGE_IN_TICKS));
            case POWER_STRIKE:
                return Transform.lerp(pose(style, Pose.CHARGED), pose(style, Pose.HEAVY), easeInCubic(t / windUp));
            case POWER_RECOVERY:
                return recover(pose(style, Pose.HEAVY), t, recovery + Tuning.POWER_SETTLE_TICKS);
            default:
                return rest;
        }
    }

    /** Recovery of the bare-hand punch: no back-kick; the fist first carries slightly forward past contact, then returns. */
    private static Transform punchRecover(Transform from, float sinceContact, float length) {
        Transform t = Transform.lerp(from, Transform.ZERO, easeOutCubic(sinceContact / Math.max(1f, length)));
        float follow = Tuning.PUNCH_FOLLOW_THROUGH
                * (float) Math.sin(Math.PI * clamp01(sinceContact / Math.max(0.5f, Tuning.PUNCH_FOLLOW_FRACTION * length)));
        return new Transform(t.x, t.y, t.z - follow, t.pitch, t.yaw, t.roll);
    }

    private static Transform recover(Transform from, float sinceContact, float length) {
        Transform t = Transform.lerp(from, Transform.ZERO, easeOutCubic(sinceContact / Math.max(1f, length)));
        float punch = Tuning.CONTACT_PUNCH * clamp01(1f - sinceContact / Tuning.PUNCH_TICKS);
        return new Transform(t.x, t.y, t.z + punch, t.pitch, t.yaw, t.roll);
    }

    /** The transform to render now. {@code partialTick} is clamped to [0,1]. Never NaN. */
    public Transform sample(float partialTick) {
        if (mode == Mode.NONE) return Transform.ZERO;
        float pt = clamp01(partialTick);
        Transform target = target(age + pt);
        if (blendDuration > 0f && blendAge + pt < blendDuration) {
            target = Transform.lerp(blendFrom, target, smoothstep((blendAge + pt) / blendDuration));
        }
        return target.isFinite() ? target : Transform.ZERO;
    }

    /** Normalised progress 0..1 through the current TIMED mode (charge is untimed and returns its ease-in). */
    public float progress(float partialTick) {
        int d = durationTicks();
        if (mode == Mode.POWER_CHARGE) return clamp01((age + clamp01(partialTick)) / Tuning.CHARGE_IN_TICKS);
        return d <= 0 ? 0f : clamp01((age + clamp01(partialTick)) / d);
    }
}
