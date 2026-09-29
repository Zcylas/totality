package zcylas.totality.entity.portal;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.init.ModParticles;

/**
 * The visual portal's particles, spawned by each client from its own copy of the entity (common code: it only adds
 * particles, and only runs on the client). All are the floating pixel cubes of the reference sheet's layer 5:
 * <ul>
 *   <li>opening: a scattered cloud that converges into the oval over the first half second;</li>
 *   <li>stable: a few motes shed from the rim, drifting outward and fading;</li>
 *   <li>collapse: motes drawn in at the start, then the portal breaks into a burst of scattered pixels.</li>
 * </ul>
 */
public final class VisualPortalVfx {

    /** The oval's semi-axes in blocks at size 1 (13 x 19 texels at 16 texels a block). */
    public static final float HALF_WIDTH = 13.0F / 16.0F;
    public static final float HALF_HEIGHT = 19.0F / 16.0F;
    /** The tick of the collapse at which the portal breaks apart (VisualPortalRenderer shrinks it to nothing by then). */
    public static final int BREAK_TICK = 14;
    /** Lifetime of a converging mote (VisualPortalParticle): its velocity is chosen to arrive exactly then. */
    public static final int CONVERGE_TICKS = 12;

    private VisualPortalVfx() {}

    static void clientTick(VisualPortalEntity portal) {
        Level level = portal.level();
        RandomSource random = level.getRandom();
        Vec3[] axes = VisualPortalEntity.basis(portal.getYRot(), portal.getXRot());
        Vec3 c = portal.position();
        float size = portal.size();
        int age = portal.phaseAge();
        switch (portal.phase()) {
            case OPENING -> {
                if (age <= 7) {
                    // Scattered pixels outside the oval converge onto points inside it (they arrive as it fills in).
                    for (int i = 0; i < 6; i++) {
                        double th = random.nextDouble() * Math.PI * 2;
                        Vec3 from = point(c, axes, size, th, 1.25 + random.nextDouble() * 0.9, (random.nextDouble() - 0.5) * 1.2);
                        Vec3 to = point(c, axes, size, th + (random.nextDouble() - 0.5), random.nextDouble() * 0.95, 0);
                        Vec3 v = to.subtract(from).scale(1.0 / CONVERGE_TICKS);
                        level.addParticle(ModParticles.VISUAL_PORTAL_CONVERGE, from.x, from.y, from.z, v.x, v.y, v.z);
                    }
                }
                if (age > 10) ambient(level, random, c, axes, size);
            }
            case STABLE -> ambient(level, random, c, axes, size);
            case COLLAPSING -> {
                if (age <= 6 && random.nextInt(2) == 0) {
                    // Loose motes are pulled back in.
                    double th = random.nextDouble() * Math.PI * 2;
                    Vec3 from = point(c, axes, size, th, 1.2 + random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.6);
                    Vec3 v = c.subtract(from).scale(1.0 / CONVERGE_TICKS);
                    level.addParticle(ModParticles.VISUAL_PORTAL_CONVERGE, from.x, from.y, from.z, v.x, v.y, v.z);
                }
                if (age == BREAK_TICK) breakApart(level, random, c, axes, size);
            }
        }
    }

    private static void ambient(Level level, RandomSource random, Vec3 c, Vec3[] axes, float size) {
        if (random.nextFloat() > 0.7F * size) return;
        double th = random.nextDouble() * Math.PI * 2;
        double r = 1.0 + random.nextDouble() * 0.25;
        Vec3 p = point(c, axes, size, th, r, (random.nextDouble() - 0.5) * 0.3);
        Vec3 out = p.subtract(c).normalize().scale(0.008 + random.nextDouble() * 0.012);
        level.addParticle(ModParticles.VISUAL_PORTAL_MOTE, p.x, p.y, p.z, out.x, out.y + 0.004, out.z);
    }

    private static void breakApart(Level level, RandomSource random, Vec3 c, Vec3[] axes, float size) {
        int n = (int) (46 * Math.max(1.0F, size));
        for (int i = 0; i < n; i++) {
            double th = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * 0.45;
            Vec3 p = point(c, axes, size, th, r, (random.nextDouble() - 0.5) * 0.2);
            double speed = 0.015 + Math.pow(random.nextDouble(), 1.5) * 0.2;   // a scattered cloud, not a ring
            Vec3 v = p.subtract(c).normalize().scale(speed)
                    .add(axes[2].scale((random.nextDouble() - 0.5) * 0.12));
            level.addParticle(ModParticles.VISUAL_PORTAL_SCATTER, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /**
     * A point in the portal's plane at elliptical radius {@code r} (1 = the rim) and angle {@code th}, {@code depth}
     * blocks in front of (or behind) it.
     */
    private static Vec3 point(Vec3 c, Vec3[] axes, float size, double th, double r, double depth) {
        return c.add(axes[0].scale(Math.cos(th) * r * HALF_WIDTH * size))
                .add(axes[1].scale(Math.sin(th) * r * HALF_HEIGHT * size))
                .add(axes[2].scale(depth));
    }
}
