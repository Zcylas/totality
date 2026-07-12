package zcylas.totality.worldgen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Overrides {@code minecraft:overworld}'s noise settings, keeping every field from vanilla's real
 * object except {@code surfaceRule}, which gets one small biome-gated branch prepended:
 * {@code SurfaceRules.sequence(ourRule, vanilla.surfaceRule())} — our rule only ever fires on
 * columns whose biome carries {@link ModTags#IS_FLOODED_BIOME}; every other biome falls through to
 * vanilla's real, completely unmodified rule tree untouched.
 * <p>
 * <b>Why a surface rule and not a post-placement digging Feature:</b> an earlier version
 * ({@code MoundFeature}) dug/flooded columns after normal terrain generation finished, and produced
 * real, unfixable-at-that-layer bugs — seams where the flood didn't quite match neighboring
 * terrain, inconsistent water tone, exposed walls from digging into what turned out to be solid
 * ground. Studied Biomes O' Plenty's and Terralith's real, working wet biomes as reference (neither
 * has a lake/dig feature at all): both drive their water purely through a surface rule extending
 * vanilla's own swamp-puddle mechanic, evaluated as part of the same top-down surface pass as every
 * neighboring biome — there's no second, disconnected pass to desync from what's next to it. This
 * reuses the same mechanism, but through {@link ModNoises#FLOODED_FOREST_COVERAGE_KEY}, our own 2D
 * noise field, rather than the vanilla {@code Noises.SWAMP} field Swamp's own puddle rule reads —
 * decoupled so its scale/threshold can be tuned independently for "mostly water, occasional dry
 * ground" instead of inheriting whatever vanilla's puddle rule happens to need. Confirmed empirically
 * (BOP's Marsh/Floodplain reuse the same shared vanilla field unmodified and inherit the same
 * region-to-region coverage inconsistency small single-octave fields have over small sample areas —
 * no reference mod solves this, so this is our own field to tune). "Islands" fall out of that noise
 * pattern directly — no custom terrain-shaping code needed for it.
 * <p>
 * <b>Why this can be a plain {@code DataProvider}, unlike {@link TotalityOverworldClimate}'s
 * {@code MultiNoiseBiomeSourceParameterList} override:</b> that one needed a mixin because
 * {@code Preset.BY_NAME} is a closed Java map with no data-driven extension point.
 * {@code NoiseGeneratorSettings} has no such indirection — it decodes as a plain record via its own
 * {@code DIRECT_CODEC}, so a full, direct file override works the same way the biome/feature
 * providers already do (still can't register via {@code RegistrySetBuilder} though — vanilla's own
 * {@code NoiseGeneratorSettings.bootstrap} claims the same key in the same merged builder used for
 * datagen, so this has to be a standalone provider like {@code TotalityOverworldClimate} was
 * originally, reading vanilla's real object from the resolved registries rather than reconstructing
 * it by hand).
 */
public class TotalityOverworldSurfaceRules implements DataProvider {
    /**
     * Threshold against {@link ModNoises#FLOODED_FOREST_COVERAGE_KEY} — {@code noiseCondition(noise,
     * t)} matches wherever the sampled value is {@code >= t}, so a lower threshold means more of the
     * distribution counts as water. History (against the old shared {@code Noises.SWAMP} field):
     * started at 0.0 (~50% coverage, read as too sparse), then -0.35, then -0.7 — intended to read as
     * near-total coverage, but actual in-game coverage still varied wildly by region (some areas
     * nearly all water, others almost entirely dry) because a single-octave field's local spatial
     * average has high variance over small sample windows; this was never actually a threshold
     * problem. Kept at the same value on the new dedicated field as a starting point — needs fresh
     * screenshots to see whether decoupling from the shared field changes the picture before tuning
     * further.
     */
    private static final double WATER_NOISE_THRESHOLD = -0.7D;

    private final FabricPackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registriesFuture;

    public TotalityOverworldSurfaceRules(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        this.output = output;
        this.registriesFuture = registriesFuture;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        return registriesFuture.thenCompose(provider -> {
            NoiseGeneratorSettings vanilla = provider.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();

            // Would prefer to resolve ModTags.IS_FLOODED_BIOME here so future flooded biomes need
            // no code change — but tag JSON binding isn't available yet at this point in datagen
            // ("can't be dereferenced during construction", confirmed empirically). Hardcoded list
            // for now; revisit once a later-stage resolution point is found.
            List<ResourceKey<Biome>> floodedBiomes = List.of(ModBiomes.FLOODED_FOREST);

            SurfaceRules.RuleSource combined = floodedBiomes.isEmpty()
                    ? vanilla.surfaceRule()
                    : SurfaceRules.sequence(buildFloodedBiomeWaterRule(floodedBiomes, vanilla.seaLevel()), vanilla.surfaceRule());

            NoiseGeneratorSettings modified = new NoiseGeneratorSettings(
                    vanilla.noiseSettings(), vanilla.defaultBlock(), vanilla.defaultFluid(),
                    vanilla.noiseRouter(), combined, vanilla.spawnTarget(), vanilla.seaLevel(),
                    vanilla.disableMobGeneration(), vanilla.aquifersEnabled(), vanilla.oreVeinsEnabled(),
                    vanilla.useLegacyRandomSource());

            Path path = output.createRegistryElementsPathProvider(Registries.NOISE_SETTINGS)
                    .json(NoiseGeneratorSettings.OVERWORLD);

            return DataProvider.saveStable(cachedOutput, provider, NoiseGeneratorSettings.DIRECT_CODEC, modified, path);
        });
    }

    /**
     * Mirrors vanilla's own swamp-puddle rule structure (studied via Biomes O' Plenty's extension
     * of it, same shape): only at the true surface ({@code ON_FLOOR}), only in a flooded biome,
     * only in the single-block band just below sea level, only where our own coverage noise field
     * crosses the threshold — force water; everywhere else, fall through untouched.
     */
    private static SurfaceRules.RuleSource buildFloodedBiomeWaterRule(List<ResourceKey<Biome>> floodedBiomes, int seaLevel) {
        @SuppressWarnings("unchecked")
        ResourceKey<Biome>[] biomeArray = floodedBiomes.toArray(new ResourceKey[0]);

        SurfaceRules.ConditionSource atOrAboveFloor = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(seaLevel - 1), 0);
        SurfaceRules.ConditionSource belowSeaLevel = SurfaceRules.not(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(seaLevel), 0));

        return SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                SurfaceRules.ifTrue(SurfaceRules.isBiome(biomeArray),
                        SurfaceRules.ifTrue(atOrAboveFloor,
                                SurfaceRules.ifTrue(belowSeaLevel,
                                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(ModNoises.FLOODED_FOREST_COVERAGE_KEY, WATER_NOISE_THRESHOLD),
                                                SurfaceRules.state(Blocks.WATER.defaultBlockState()))))));
    }

    @Override
    public String getName() {
        return "Totality Overworld Surface Rules";
    }
}
