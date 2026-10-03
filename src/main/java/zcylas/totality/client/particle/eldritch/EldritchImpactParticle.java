package zcylas.totality.client.particle.eldritch;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.vfx.eldritch.EldritchBlastVfx;
import zcylas.totality.init.ModParticles;

/**
 * The impact event the server sends at the exact point an Eldritch Blast bolt hits (its velocity is the hit surface's
 * normal; zero = the bolt expired in the air). It draws nothing: it hands the event to {@link EldritchBlastVfx} and ends.
 */
public class EldritchImpactParticle extends NoRenderParticle {

    EldritchImpactParticle(ClientLevel level, double x, double y, double z, double nx, double ny, double nz) {
        super(level, x, y, z);
        this.lifetime = 1;
        this.xd = this.yd = this.zd = 0;
        EldritchBlastVfx.impact(level, new Vec3(x, y, z), new Vec3(nx, ny, nz));
    }

    public static void register() {
        ParticleProviderRegistry.getInstance().register(ModParticles.ELDRITCH_IMPACT, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                new EldritchImpactParticle(level, x, y, z, xd, yd, zd));
    }
}
