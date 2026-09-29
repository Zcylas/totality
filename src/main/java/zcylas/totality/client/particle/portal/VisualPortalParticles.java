package zcylas.totality.client.particle.portal;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.core.particles.SimpleParticleType;
import zcylas.totality.init.ModParticles;

/** Client registration of the visual portal's particles (sprites: assets/totality/particles/visual_portal_*.json). */
public final class VisualPortalParticles {

    private VisualPortalParticles() {}

    public static void register() {
        ParticleProviderRegistry r = ParticleProviderRegistry.getInstance();
        kind(r, ModParticles.VISUAL_PORTAL_MOTE, VisualPortalParticle.Kind.MOTE);
        kind(r, ModParticles.VISUAL_PORTAL_CONVERGE, VisualPortalParticle.Kind.CONVERGE);
        kind(r, ModParticles.VISUAL_PORTAL_SCATTER, VisualPortalParticle.Kind.SCATTER);
    }

    private static void kind(ParticleProviderRegistry r, SimpleParticleType type, VisualPortalParticle.Kind kind) {
        r.register(type, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                new VisualPortalParticle(level, x, y, z, xd, yd, zd, sprites, kind, random));
    }
}
