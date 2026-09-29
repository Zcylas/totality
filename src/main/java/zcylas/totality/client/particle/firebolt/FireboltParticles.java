package zcylas.totality.client.particle.firebolt;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.core.particles.SimpleParticleType;
import zcylas.totality.init.ModParticles;

/** Client registration of the Firebolt particle providers (sprites: assets/totality/particles/firebolt_*.json). */
public final class FireboltParticles {

    private FireboltParticles() {}

    public static void register() {
        ParticleProviderRegistry r = ParticleProviderRegistry.getInstance();
        kind(r, ModParticles.FIREBOLT_EMBER, FireboltParticle.Kind.EMBER);
        kind(r, ModParticles.FIREBOLT_WISP, FireboltParticle.Kind.WISP);
        kind(r, ModParticles.FIREBOLT_SPARK, FireboltParticle.Kind.SPARK);
        kind(r, ModParticles.FIREBOLT_STREAK, FireboltParticle.Kind.STREAK);
        r.register(ModParticles.FIREBOLT_FLASH, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                // the flash's x velocity carries its size (0.8 for a cast, 1.6 for an impact); it does not move
                new FireboltParticle(level, x, y, z, 0, 0, 0, sprites, FireboltParticle.Kind.FLASH, random).sized((float) Math.max(0.3, xd)));
        r.register(ModParticles.FIREBOLT_IMPACT, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                new FireboltImpactParticle(level, x, y, z, xd, yd, zd));
    }

    private static void kind(ParticleProviderRegistry r, SimpleParticleType type, FireboltParticle.Kind kind) {
        r.register(type, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
                new FireboltParticle(level, x, y, z, xd, yd, zd, sprites, kind, random));
    }
}
