package zcylas.totality.client.particle.fireball;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import zcylas.totality.init.ModParticles;

/** Client registration of the Fireball particle providers (sprites: assets/totality/particles/fireball_*.json). */
public final class FireballParticles {

    private FireballParticles() {}

    public static void register() {
        ParticleProviderRegistry r = ParticleProviderRegistry.getInstance();
        r.register(ModParticles.FIREBALL_BLAST, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                // the blast's x velocity carries the radius it blooms out to; it does not move
                new FireballBlastParticle(level, x, y, z, (float) Math.max(1.0, xd), sprites));
        r.register(ModParticles.FIREBALL_SMOKE, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                new FireballParticle(level, x, y, z, xd, yd, zd, sprites, FireballParticle.Kind.SMOKE, random));
        r.register(ModParticles.FIREBALL_FRAGMENT, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                new FireballParticle(level, x, y, z, xd, yd, zd, sprites, FireballParticle.Kind.FRAGMENT, random));
        r.register(ModParticles.FIREBALL_DETONATION, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                new FireballDetonationParticle(level, x, y, z, xd < 0));
    }
}
