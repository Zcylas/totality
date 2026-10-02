package zcylas.totality.entity.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.init.ModParticles;

import java.util.function.Predicate;

/**
 * Fireball's particle recipes, after Context/References/Other/fireball.png and the D&D spell ("a bright streak flashes
 * from you to a point you choose and then blossoms with a low roar into a fiery explosion" filling a 20-foot-radius
 * sphere): the cast ignition, the travel trail, the detonation sphere that blooms out to the blast radius with a ground
 * shock ring, and its lingering embers and smoke. Reuses Firebolt's ember / wisp / spark / streak / flash particles
 * and adds the blast sphere, smoke puffs and charred fragments. Only ever called on clients (the projectile's client
 * tick and the detonation emitter); it only adds particles, so it is safe in common code.
 *
 * <p>Fireball V2 (VFX Experiment 3, B3-B4) uses its own sparks, embers and heat streaks (near-camera size limit) under
 * {@link FireballParticleBudget}: a restrained cast flash, an ember trail behind the projectile's streak (the streak
 * itself is drawn by the projectile renderer), debris that flies out past the fire front, and a cooling aftermath of
 * smoke and embers that starts as the fire cools and is gone by four seconds. The V1 recipes stay for the
 * development-only A/B comparison ({@link #setLegacyProjectile}, {@code FireballDetonationParticle.setLegacyExplosion}).
 */
public final class FireballVfx {

    /** How long the detonation emitter keeps adding lingering embers and smoke. */
    public static final int LINGER_TICKS = 40;
    /** Fireball V2: the emitter's aftermath (smoke until tick 30, embers until 56; every particle gone by tick 80). */
    public static final int V2_LINGER_TICKS = 56;
    static final int V2_SMOKE_FROM = 14;
    static final int V2_SMOKE_UNTIL = 30;
    static final int V2_EMBERS_FROM = 18;
    /** No aftermath smoke puff closer than this to the viewer (with the camera inside the blast, puffs would fill it). */
    static final double V2_SMOKE_CLEARANCE = 3.5;
    /** A client that first sees a fireball this close to its caster's eyes saw the cast (about three ticks of flight). */
    static final double CAST_SEEN_DISTANCE = 4.5;

    /** Development only: the V1 projectile (renderer, cast burst and trail) for A/B comparison. */
    private static boolean legacyProjectile;
    /** Cast bursts played on this client (development capture: a fireball first seen mid-flight plays none). */
    private static int castBursts;

    private FireballVfx() {}

    /** Development only: use the V1 projectile look (ignored outside a development environment). */
    public static void setLegacyProjectile(boolean on) {
        legacyProjectile = on && VerificationReporter.isDevEnvironment();
    }

    public static boolean legacyProjectile() {
        return legacyProjectile;
    }

    public static int castBursts() {
        return castBursts;
    }

    /**
     * True when {@code fireball}, first seen at {@code at}, is a fresh cast: its caster is known to this client and the
     * fireball is still within {@link #CAST_SEEN_DISTANCE} of the caster's eyes. A fireball that comes into tracking
     * range mid-flight (or whose caster this client does not know) is not, and plays no cast ignition.
     */
    public static boolean sawCast(FireballProjectileEntity fireball, Vec3 at) {
        Entity owner = fireball.getOwner();
        return owner != null && owner.getEyePosition().distanceTo(at) <= CAST_SEEN_DISTANCE;
    }

    /** Distance from {@code at} to the nearest player (the camera, on a client; spectators count), for the particle budget. */
    static double viewDistance(Level level, Vec3 at) {
        Player p = level.getNearestPlayer(at.x, at.y, at.z, -1.0, (Predicate<Entity>) null);
        return p == null ? Double.MAX_VALUE : p.getEyePosition().distanceTo(at);
    }

    /**
     * Ignition where the orb first shows, 2.5 blocks in front of the caster's eyes (closer, it fills a first-person
     * view): a flash and a fountain of flame, sparks and embers leaning forward and up, all moving away from the caster.
     */
    public static void castBurst(Level level, Vec3 at, Vec3 dir) {
        castBursts++;
        if (!legacyProjectile) {
            castBurstV2(level, at, dir);
            return;
        }
        RandomSource r = level.getRandom();
        Vec3 p0 = at.add(dir.scale(2.5));
        level.addParticle(ModParticles.FIREBOLT_FLASH, p0.x, p0.y, p0.z, 0.55, 0, 0);
        for (int i = 0; i < 7; i++) {
            Vec3 v = cone(r, dir.add(0, 1.1, 0).normalize(), 0.7).scale(0.16 + r.nextDouble() * 0.16);
            level.addParticle(ModParticles.FIREBOLT_SPARK, p0.x, p0.y, p0.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 3; i++) {
            Vec3 p = p0.add(jitter(r, 0.25));
            Vec3 v = dir.scale(0.05).add(jitter(r, 0.03)).add(0, 0.07 + r.nextDouble() * 0.05, 0);
            level.addParticle(ModParticles.FIREBOLT_WISP, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 5; i++) {
            Vec3 v = dir.scale(0.06).add(jitter(r, 0.06)).add(0, 0.05, 0);
            level.addParticle(ModParticles.FIREBOLT_EMBER, p0.x, p0.y, p0.z, v.x, v.y, v.z);
        }
    }

    /**
     * Fireball V2's cast ignition: a few sparks thrown forwards and a couple of embers where the bead emerges (2.5 blocks
     * out, clear of a first-person view); no smoke, no flame wisps. The white-gold flash itself is the bead flaring as
     * it emerges (the projectile renderer); a flash particle there read as a large flat square in first person.
     */
    private static void castBurstV2(Level level, Vec3 at, Vec3 dir) {
        RandomSource r = level.getRandom();
        Vec3 p0 = at.add(dir.scale(2.5));
        for (int i = 0; i < 7; i++) {
            Vec3 v = cone(r, dir.add(0, 0.35, 0).normalize(), 0.5).scale(0.25 + r.nextDouble() * 0.2);
            level.addParticle(ModParticles.FIREBALL_SPARK, p0.x, p0.y, p0.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 3; i++) {
            Vec3 v = dir.scale(0.06).add(jitter(r, 0.04)).add(0, 0.03, 0);
            level.addParticle(ModParticles.FIREBALL_EMBER, p0.x, p0.y, p0.z, v.x, v.y, v.z);
        }
    }

    /**
     * One tick of flight from {@code from} to {@code to} (1.2 blocks): a restrained trail behind the bead — ember motes,
     * flame wisps, sparks, heat streaks along the path, and now and then a burnt fragment (no smoke in flight).
     */
    public static void trail(Level level, Vec3 from, Vec3 to, Vec3 dir) {
        if (!legacyProjectile) {
            trailV2(level, from, to, dir);
            return;
        }
        RandomSource r = level.getRandom();
        for (int i = 0; i < 3; i++) {
            Vec3 p = lerp(from, to, r.nextDouble()).add(jitter(r, 0.2));
            Vec3 v = dir.scale(-0.04 - r.nextDouble() * 0.04).add(jitter(r, 0.03));
            level.addParticle(ModParticles.FIREBOLT_EMBER, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 2; i++) {
            Vec3 p = lerp(from, to, r.nextDouble()).add(jitter(r, 0.15));
            Vec3 v = dir.scale(-0.05).add(jitter(r, 0.02)).add(0, 0.03, 0);
            level.addParticle(ModParticles.FIREBOLT_WISP, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        if (r.nextFloat() < 0.5F) {
            Vec3 p = lerp(from, to, r.nextDouble());
            Vec3 v = cone(r, dir.scale(-1), 1.2).scale(0.1 + r.nextDouble() * 0.12);
            level.addParticle(ModParticles.FIREBOLT_SPARK, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 2; i++) {
            Vec3 p = lerp(from, to, r.nextDouble()).add(jitter(r, 0.3));
            Vec3 v = dir.scale(0.12 + r.nextDouble() * 0.1);
            level.addParticle(ModParticles.FIREBOLT_STREAK, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        if (r.nextFloat() < 0.08F) {
            Vec3 p = lerp(from, to, r.nextDouble());
            Vec3 v = cone(r, dir.scale(-1), 1.0).scale(0.08);
            level.addParticle(ModParticles.FIREBALL_FRAGMENT, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /**
     * Fireball V2's trail for one tick of flight: two or three embers shed behind the streak (cooling gold to red,
     * drifting up, about a second) and now and then a spark; no wisps, streaks or fragments (the projectile renderer
     * draws the streak). Budgeted by distance and load.
     */
    private static void trailV2(Level level, Vec3 from, Vec3 to, Vec3 dir) {
        RandomSource r = level.getRandom();
        double distance = viewDistance(level, to);
        int embers = FireballParticleBudget.allow(2 + r.nextInt(2), distance);
        for (int i = 0; i < embers; i++) {
            Vec3 p = lerp(from, to, r.nextDouble()).subtract(dir.scale(0.5)).add(jitter(r, 0.12));
            Vec3 v = dir.scale(-0.03 - r.nextDouble() * 0.03).add(jitter(r, 0.02)).add(0, 0.02, 0);
            level.addParticle(ModParticles.FIREBALL_EMBER, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        if (r.nextDouble() < FireballParticleBudget.scaleChance(0.25, distance)) {
            Vec3 p = lerp(from, to, r.nextDouble());
            Vec3 v = cone(r, dir.scale(-1), 1.0).scale(0.08 + r.nextDouble() * 0.08);
            level.addParticle(ModParticles.FIREBALL_SPARK, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /**
     * The detonation (tick 0): a brief, intense flash, then the blast sphere bursting out to the damage radius in about
     * three ticks; sparks, heat streaks and burnt fragments flung outwards, flame wisps thrown to the sphere's edge.
     * The camera-facing sphere is drawn a fifth of the radius out from the surface hit (centred on a wall or floor it
     * would be half buried in it) at 95 % of the radius, so it still reads as the 6-block sphere; the damage centre is
     * the impact point itself.
     */
    public static void detonate(Level level, Vec3 at, Vec3 normal, double radius) {
        RandomSource r = level.getRandom();
        Vec3 c = at.add(normal.scale(radius * 0.2));
        level.addParticle(ModParticles.FIREBALL_BLAST, c.x, c.y, c.z, radius * 0.95, 0, 0);
        level.addParticle(ModParticles.FIREBOLT_FLASH, c.x, c.y, c.z, 6.5, 0, 0);
        Vec3 bias = normal.scale(0.35);
        for (int i = 0; i < 40; i++) {
            Vec3 v = sphere(r).add(bias).normalize().scale(0.35 + r.nextDouble() * 0.45);
            level.addParticle(ModParticles.FIREBOLT_SPARK, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 24; i++) {
            Vec3 v = sphere(r).add(bias).normalize().scale(0.6 + r.nextDouble() * 0.4);
            level.addParticle(ModParticles.FIREBOLT_STREAK, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 14; i++) {
            Vec3 v = sphere(r).add(normal.scale(0.8)).normalize().scale(0.25 + r.nextDouble() * 0.3);
            level.addParticle(ModParticles.FIREBALL_FRAGMENT, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 36; i++) {
            // friction 0.86 over ~9 ticks carries a wisp ~7x its start speed: these reach the sphere's edge
            Vec3 v = sphere(r).scale(radius * 0.8 / 7.0 * (0.7 + r.nextDouble() * 0.5));
            level.addParticle(ModParticles.FIREBOLT_WISP, c.x, c.y, c.z, v.x, v.y, v.z);
        }
    }

    /**
     * Fireball V2's debris (the fire itself is the layered explosion drawn on the client): bright sparks and heat streaks
     * fast enough to fly out past the flame front within about 0.15 s, biased away from the surface hit, so they read
     * as debris thrown out of the blast rather than specks laid over it. No dark fragments (over the fire they read as
     * black squares) and no flash particle. Budgeted by distance and load.
     */
    public static void detonateDebris(Level level, Vec3 at, Vec3 normal) {
        RandomSource r = level.getRandom();
        double distance = viewDistance(level, at);
        Vec3 bias = normal.scale(0.35);
        int sparks = FireballParticleBudget.allow(22, distance);
        for (int i = 0; i < sparks; i++) {
            Vec3 v = sphere(r).add(bias).normalize().scale(0.9 + r.nextDouble() * 0.5);
            level.addParticle(ModParticles.FIREBALL_SPARK, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        int streaks = FireballParticleBudget.allow(10, distance);
        for (int i = 0; i < streaks; i++) {
            Vec3 v = sphere(r).add(bias).normalize().scale(1.1 + r.nextDouble() * 0.4);
            level.addParticle(ModParticles.FIREBALL_STREAK, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    /**
     * Fireball V2's aftermath on emitter tick {@code age} (the fire itself is gone by about 1.45 s): smoke rises from the
     * upper, cooling part of the blast between ticks {@link #V2_SMOKE_FROM} and {@link #V2_SMOKE_UNTIL} (about 16 puffs,
     * never in front of the hot fire's early peak), then a thinning scatter of small, dim embers drifting up from the
     * burnt area until {@link #V2_LINGER_TICKS}. No flame wisps after the fire (they would read as burning ground, which
     * only the real fire blocks are). Every particle has ended by tick 80 (4 s).
     */
    public static void aftermathV2(Level level, Vec3 at, double radius, int age, double groundY) {
        RandomSource r = level.getRandom();
        double distance = viewDistance(level, at);
        if (age >= V2_SMOKE_FROM && age <= V2_SMOKE_UNTIL && FireballParticleBudget.allow(1, distance) > 0 && r.nextFloat() < 0.95F) {
            Vec3 d = sphere(r);
            if (d.y < 0) d = new Vec3(d.x, -d.y, d.z);
            Vec3 p = at.add(d.add(0, 0.6, 0).normalize().scale(radius * (0.35 + 0.45 * r.nextDouble())));
            if (viewDistance(level, p) >= V2_SMOKE_CLEARANCE) level.addParticle(ModParticles.FIREBALL_SMOKE, p.x, p.y, p.z, d.x * 0.01, 0.035 + r.nextDouble() * 0.025, d.z * 0.01);
        }
        if (age >= V2_EMBERS_FROM) {
            float left = 1.0F - (float) (age - V2_EMBERS_FROM) / (V2_LINGER_TICKS - V2_EMBERS_FROM);
            int embers = FireballParticleBudget.allow(Math.round(3 * left * left + r.nextFloat() * left), distance);
            for (int i = 0; i < embers; i++) {
                Vec3 p = at.add(flat(r).scale(radius * 0.8 * Math.sqrt(r.nextDouble())));
                double y = Double.isNaN(groundY) ? p.y + (r.nextDouble() - 0.5) * radius : groundY + 0.3 + r.nextDouble() * 1.5;
                level.addParticle(ModParticles.FIREBALL_EMBER, p.x, y, p.z, (r.nextDouble() - 0.5) * 0.02, 0.025 + r.nextDouble() * 0.03,
                        (r.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    /**
     * The ticks after the detonation: for the first 4 a light shock ring of flame runs out across the ground (a
     * supporting effect under the sphere, when there is ground under the blast); then embers and smoke keep rising
     * from the burnt area, thinning out.
     */
    public static void afterDetonation(Level level, Vec3 at, double radius, int age, double groundY) {
        RandomSource r = level.getRandom();
        if (age <= 4 && !Double.isNaN(groundY)) {
            double ring = radius * (0.3 + 0.7 * age / 4.0);
            int n = 5 + age * 2;
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                Vec3 p = new Vec3(at.x + Math.cos(a) * ring, groundY + 0.15, at.z + Math.sin(a) * ring);
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(0.08);
                level.addParticle(i % 3 == 0 ? ModParticles.FIREBOLT_WISP : ModParticles.FIREBOLT_EMBER,
                        p.x, p.y, p.z, out.x, 0.02, out.z);
            }
        }
        float left = 1.0F - (float) age / LINGER_TICKS;
        if (age >= 3 && r.nextFloat() < 0.4F + 0.5F * left) {
            Vec3 p = at.add(flat(r).scale(radius * 0.55 * r.nextDouble())).add(0, r.nextDouble() * 1.2, 0);
            level.addParticle(ModParticles.FIREBALL_SMOKE, p.x, p.y, p.z, 0, 0.03 + r.nextDouble() * 0.02, 0);
        }
        int embers = Math.round(4 * left);
        for (int i = 0; i < embers; i++) {
            Vec3 p = at.add(flat(r).scale(radius * 0.7 * r.nextDouble()));
            double y = Double.isNaN(groundY) ? p.y : groundY + 0.2 + r.nextDouble() * 0.6;
            level.addParticle(i == 0 && age < 20 ? ModParticles.FIREBOLT_WISP : ModParticles.FIREBOLT_EMBER,
                    p.x, y, p.z, 0, 0.03 + r.nextDouble() * 0.03, 0);
        }
    }

    /** An expired fireball gutters out in the air: a flash, a smoke puff and a few embers. */
    public static void fizzle(Level level, Vec3 at) {
        RandomSource r = level.getRandom();
        level.addParticle(ModParticles.FIREBOLT_FLASH, at.x, at.y, at.z, 1.2, 0, 0);
        level.addParticle(ModParticles.FIREBALL_SMOKE, at.x, at.y, at.z, 0, 0.03, 0);
        for (int i = 0; i < 8; i++) {
            Vec3 v = jitter(r, 0.08).add(0, 0.03, 0);
            level.addParticle(i < 3 ? ModParticles.FIREBOLT_WISP : ModParticles.FIREBOLT_EMBER, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    /** The surface the blast went off against: the direction away from the nearest solid neighbour (default up). */
    public static Vec3 surfaceNormal(Level level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        Direction best = null;
        double bestGap = 0.8;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.getBlockState(n).isCollisionShapeFullBlock(level, n)) continue;
            double gap = switch (d.getAxis()) {
                case X -> d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos.getX() + 1 - at.x : at.x - pos.getX();
                case Y -> d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos.getY() + 1 - at.y : at.y - pos.getY();
                case Z -> d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos.getZ() + 1 - at.z : at.z - pos.getZ();
            };
            if (gap < bestGap) {
                bestGap = gap;
                best = d;
            }
        }
        return best == null ? new Vec3(0, 1, 0) : Vec3.atLowerCornerOf(best.getOpposite().getUnitVec3i());
    }

    /** The top of the first solid block at most {@code depth} blocks under {@code at}, or NaN when there is none. */
    public static double groundBelow(Level level, Vec3 at, int depth) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i <= depth; i++) {
            BlockPos p = pos.below(i);
            if (level.getBlockState(p).isCollisionShapeFullBlock(level, p)) return p.getY() + 1.0;
        }
        return Double.NaN;
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    private static Vec3 jitter(RandomSource r, double s) {
        return new Vec3((r.nextDouble() - 0.5) * 2 * s, (r.nextDouble() - 0.5) * 2 * s, (r.nextDouble() - 0.5) * 2 * s);
    }

    private static Vec3 sphere(RandomSource r) {
        double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3(s * Math.cos(a), z, s * Math.sin(a));
    }

    private static Vec3 flat(RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2;
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    /** A random unit vector within {@code spread} radians of {@code axis}. */
    private static Vec3 cone(RandomSource r, Vec3 axis, double spread) {
        Vec3 a = axis.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : axis.normalize();
        Vec3 ref = Math.abs(a.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = a.cross(ref).normalize(), w = a.cross(u);
        double theta = r.nextDouble() * spread, phi = r.nextDouble() * Math.PI * 2;
        return a.scale(Math.cos(theta)).add(u.scale(Math.sin(theta) * Math.cos(phi))).add(w.scale(Math.sin(theta) * Math.sin(phi)));
    }
}
