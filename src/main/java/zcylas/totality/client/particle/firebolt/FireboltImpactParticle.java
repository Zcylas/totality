package zcylas.totality.client.particle.firebolt;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.entity.magic.FireboltVfx;

/**
 * The impact emitter the server sends to every nearby client at the exact point a Firebolt hits (its velocity is the
 * hit surface's normal). It draws nothing itself: it releases the impact burst ({@link FireboltVfx#impactBurst}) and
 * a little afterglow over the next few ticks, then ends. A zero normal is a fizzle (the bolt expired in the air).
 */
public class FireboltImpactParticle extends NoRenderParticle {

    private final Vec3 normal;
    private final boolean fizzle;

    FireboltImpactParticle(ClientLevel level, double x, double y, double z, double nx, double ny, double nz) {
        super(level, x, y, z);
        Vec3 n = new Vec3(nx, ny, nz);
        this.fizzle = n.lengthSqr() < 1.0E-6;
        this.normal = this.fizzle ? new Vec3(0, 1, 0) : n.normalize();
        this.lifetime = 5;
        this.xd = this.yd = this.zd = 0;
        if (this.fizzle) FireboltVfx.fizzle(level, new Vec3(x, y, z));
        else FireboltVfx.impactBurst(level, new Vec3(x, y, z), this.normal);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.fizzle && this.age <= 3) FireboltVfx.afterglow(this.level, new Vec3(this.x, this.y, this.z), this.normal);
    }
}
