package zcylas.totality.init;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Totality's particle types (registered on both sides; client rendering lives in client/particle).
 * <p>Firebolt: embers, flame wisps, cross sparks, directional streaks, a burst flash, and the impact emitter the server
 * sends at the exact hit point (its velocity carries the surface normal; zero = a fizzle on expiry).
 * <p>Fireball: the detonation sphere, smoke puffs, charred fragments, and the detonation emitter delivered as the blast's
 * own explosion particle (x velocity 1 = detonation, -1 = the fizzle of an expired fireball).
 * <p>Visual portal test: floating pixel cubes (ambient), converging (opening) and scattering (collapse).
 */
public final class ModParticles {

    public static final SimpleParticleType FIREBOLT_EMBER = register("firebolt_ember", false);
    public static final SimpleParticleType FIREBOLT_WISP = register("firebolt_wisp", false);
    public static final SimpleParticleType FIREBOLT_SPARK = register("firebolt_spark", false);
    public static final SimpleParticleType FIREBOLT_STREAK = register("firebolt_streak", false);
    public static final SimpleParticleType FIREBOLT_FLASH = register("firebolt_flash", true);
    public static final SimpleParticleType FIREBOLT_IMPACT = register("firebolt_impact", true);
    public static final SimpleParticleType FIREBALL_BLAST = register("fireball_blast", true);
    public static final SimpleParticleType FIREBALL_SMOKE = register("fireball_smoke", false);
    public static final SimpleParticleType FIREBALL_FRAGMENT = register("fireball_fragment", false);
    public static final SimpleParticleType FIREBALL_DETONATION = register("fireball_detonation", true);
    public static final SimpleParticleType VISUAL_PORTAL_MOTE = register("visual_portal_mote", false);
    public static final SimpleParticleType VISUAL_PORTAL_CONVERGE = register("visual_portal_converge", true);
    public static final SimpleParticleType VISUAL_PORTAL_SCATTER = register("visual_portal_scatter", true);

    private ModParticles() {}

    private static SimpleParticleType register(String name, boolean alwaysShow) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, name),
                FabricParticleTypes.simple(alwaysShow));
    }

    public static void register() {}
}
