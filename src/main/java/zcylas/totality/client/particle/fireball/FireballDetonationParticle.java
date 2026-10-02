package zcylas.totality.client.particle.fireball;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.entity.magic.FireballVfx;

/**
 * The detonation emitter. The server delivers it as the Fireball explosion's own particle, so every client that
 * receives the blast (sound and knockback) gets it exactly once, at the blast centre. It draws nothing itself: it
 * releases the detonation, Fireball V2's layered explosion ({@link FireballV2}), then the cooling aftermath of smoke and
 * embers over {@link FireballVfx#V2_LINGER_TICKS} ({@link FireballVfx#aftermathV2}), and ends. A fizzle (an expired
 * fireball) is a single small puff.
 *
 * <p>Development only: {@link #setLegacyExplosion} switches back to the V1 detonation (flat blast sprite and shock
 * ring, {@link FireballVfx#detonate}) for same-run before/after comparisons.
 */
public class FireballDetonationParticle extends NoRenderParticle {

    /** Emitters created and still running on this client (read by the development capture: one per blast, none left). */
    private static int created;
    private static int running;
    /** Development only: use the V1 detonation for A/B comparison. */
    private static boolean legacy;

    private final boolean fizzle;
    private final boolean v1;
    private final double groundY;

    FireballDetonationParticle(ClientLevel level, double x, double y, double z, boolean fizzle) {
        super(level, x, y, z);
        this.fizzle = fizzle;
        this.v1 = legacy;
        this.xd = this.yd = this.zd = 0;
        Vec3 at = new Vec3(x, y, z);
        this.groundY = FireballVfx.groundBelow(level, at, 3);
        created++;
        running++;
        if (fizzle) {
            this.lifetime = 1;
            FireballVfx.fizzle(level, at);
        } else {
            this.lifetime = v1 ? FireballVfx.LINGER_TICKS : FireballVfx.V2_LINGER_TICKS;
            if (v1) {
                FireballVfx.detonate(level, at, FireballVfx.surfaceNormal(level, at), FireballProjectileEntity.BLAST_RADIUS);
            } else {
                FireballV2.detonate(level, at);
            }
        }
    }

    /** Development only: detonate with the V1 look (ignored outside a development environment). */
    public static void setLegacyExplosion(boolean on) {
        legacy = on && VerificationReporter.isDevEnvironment();
    }

    public static boolean legacyExplosion() {
        return legacy;
    }

    public static int created() {
        return created;
    }

    public static int running() {
        return running;
    }

    @Override
    public void remove() {
        if (this.isAlive()) running--;
        super.remove();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fizzle || !this.isAlive()) return;
        Vec3 at = new Vec3(this.x, this.y, this.z);
        if (this.v1) {
            FireballVfx.afterDetonation(this.level, at, FireballProjectileEntity.BLAST_RADIUS, this.age, this.groundY);
        } else {
            FireballVfx.aftermathV2(this.level, at, FireballProjectileEntity.BLAST_RADIUS, this.age, this.groundY);
        }
    }
}
