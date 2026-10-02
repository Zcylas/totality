package zcylas.totality.entity.magic;

/**
 * Fireball V2's shared cosmetic particle budget (client only in practice; plain counters, so it lives beside the
 * recipes in common code). Every Fireball particle reports its creation and removal; a recipe asks {@link #allow} how
 * many of its particles to spawn. The explosion and projectile geometry never go through the budget (they carry the
 * gameplay readout); only decorative sparks, embers, streaks and smoke do.
 *
 * <ul>
 *   <li><b>Distance:</b> full recipes within {@link #NEAR} blocks of the nearest player (the camera), half to
 *       {@link #MID}, a quarter beyond.</li>
 *   <li><b>Load:</b> below half of {@link #MAX_ALIVE} live particles a recipe is untouched; above it, it shrinks
 *       linearly to nothing at {@link #MAX_ALIVE}, so repeated casts and several casters cannot pile particles up.</li>
 * </ul>
 */
public final class FireballParticleBudget {

    public static final int MAX_ALIVE = 400;
    public static final double NEAR = 24.0;
    public static final double MID = 48.0;

    private static int alive;
    private static long created;

    private FireballParticleBudget() {}

    /** How many of a recipe's {@code wanted} particles to spawn {@code distance} blocks from the camera. */
    public static int allow(int wanted, double distance) {
        return allow(wanted, distance, alive);
    }

    static int allow(int wanted, double distance, int liveParticles) {
        if (wanted <= 0) return 0;
        double lod = distance <= NEAR ? 1.0 : distance <= MID ? 0.5 : 0.25;
        double load = Math.clamp((MAX_ALIVE - liveParticles) / (MAX_ALIVE * 0.5), 0.0, 1.0);
        return Math.min((int) Math.round(wanted * lod * load), Math.max(0, MAX_ALIVE - liveParticles));
    }

    /** A chance-based spawn (e.g. 0.5 per tick) under the same distance and load scaling. */
    public static double scaleChance(double chance, double distance) {
        double lod = distance <= NEAR ? 1.0 : distance <= MID ? 0.5 : 0.25;
        return chance * lod * Math.clamp((MAX_ALIVE - alive) / (MAX_ALIVE * 0.5), 0.0, 1.0);
    }

    public static void onCreated() {
        alive++;
        created++;
    }

    public static void onRemoved() {
        if (alive > 0) alive--;
    }

    /** The particle engine was cleared (world change, development clear): nothing is alive any more. */
    public static void reset() {
        alive = 0;
    }

    public static int alive() {
        return alive;
    }

    public static long created() {
        return created;
    }
}
