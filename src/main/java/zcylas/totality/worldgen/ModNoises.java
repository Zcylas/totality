package zcylas.totality.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import zcylas.totality.Totality;

/**
 * Custom noise fields, registered the same way vanilla's own {@code NoiseData} bootstraps
 * {@link net.minecraft.world.level.levelgen.Noises}. A new key under our own namespace has no
 * duplicate-registration conflict with vanilla's bootstrap (unlike the overworld biome/surface-rule
 * overrides elsewhere in this package, which have to be manual {@code DataProvider}s specifically
 * because they reuse vanilla's own keys) — this can register normally through
 * {@code RegistrySetBuilder}.
 */
public class ModNoises {
    /**
     * Drives {@code TotalityOverworldSurfaceRules}' flooded-biome water rule. Previously reused
     * vanilla's {@code Noises.SWAMP} (the same field swamp puddles read) — decoupled to our own key
     * so its threshold/scale can be tuned for "mostly water, occasional dry ground" without being
     * constrained by whatever vanilla's swamp puddle rule needs. Same shape as vanilla's swamp
     * puddle field for now (single octave, firstOctave -2 — see {@code data/minecraft/worldgen/
     * noise/surface_swamp.json}), kept as a known-good starting point rather than guessed values.
     */
    public static final ResourceKey<NormalNoise.NoiseParameters> FLOODED_FOREST_COVERAGE_KEY = ResourceKey.create(
            Registries.NOISE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "flooded_forest_coverage"));

    public static void bootstrap(BootstrapContext<NormalNoise.NoiseParameters> context) {
        context.register(FLOODED_FOREST_COVERAGE_KEY, new NormalNoise.NoiseParameters(-2, 1.0));
    }

    private ModNoises() {}
}
