package zcylas.totality.client.particle.fireball;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.entity.magic.FireballVfx;

/**
 * The detonation emitter. The server delivers it as the Fireball explosion's own particle, so every client that
 * receives the blast (sound and knockback) gets it exactly once, at the blast centre. It draws nothing itself: it
 * releases the detonation ({@link FireballVfx#detonate}), then the ground shock ring and the lingering embers and smoke
 * over {@link FireballVfx#LINGER_TICKS}, and ends. A fizzle (an expired fireball) is a single small puff.
 */
public class FireballDetonationParticle extends NoRenderParticle {

    /** Emitters created and still running on this client (read by the development capture: one per blast, none left). */
    private static int created;
    private static int running;

    private final boolean fizzle;
    private final double groundY;

    FireballDetonationParticle(ClientLevel level, double x, double y, double z, boolean fizzle) {
        super(level, x, y, z);
        this.fizzle = fizzle;
        this.xd = this.yd = this.zd = 0;
        Vec3 at = new Vec3(x, y, z);
        this.groundY = FireballVfx.groundBelow(level, at, 3);
        created++;
        running++;
        if (fizzle) {
            this.lifetime = 1;
            FireballVfx.fizzle(level, at);
        } else {
            this.lifetime = FireballVfx.LINGER_TICKS;
            FireballVfx.detonate(level, at, FireballVfx.surfaceNormal(level, at), FireballProjectileEntity.BLAST_RADIUS);
        }
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
        if (!this.fizzle && this.isAlive()) {
            FireballVfx.afterDetonation(this.level, new Vec3(this.x, this.y, this.z), FireballProjectileEntity.BLAST_RADIUS,
                    this.age, this.groundY);
        }
    }
}
