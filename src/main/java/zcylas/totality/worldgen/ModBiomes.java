package zcylas.totality.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import zcylas.totality.Totality;

/**
 * {@code totality:flooded_forest}. Trees-on-mounds, fallen logs' visual pairing with actual water,
 * and moss carpet (milestones 7-8) still aren't fully tuned — this has baseline underground
 * content, vanilla-reused vegetation, the two fallen-log placed features, and terrain-only islands
 * via {@link zcylas.totality.worldgen.feature.MoundFeature} (see {@code MOUND_BARE_ISLAND_KEY}).
 * <p>
 * Vegetation starts from selected {@code BiomeDefaultFeatures} helpers rather than the full
 * {@code addSwampVegetation}-adjacent bundle, reviewed in-game and pruned toward "flooded
 * woodland" rather than "reskinned Swamp": {@code addFerns} (ferns), {@code addSwampVegetation}
 * (covers lily pads + sugar cane directly), {@code addDefaultMushrooms}, {@code addSwampClayDisk}
 * (clay/gravel patches beneath water). Fallen logs reuse vanilla's own {@code TreeFeatures
 * .FALLEN_OAK_TREE}/{@code FALLEN_BIRCH_TREE} configured features, wrapped as placed features in
 * {@link ModPlacedFeatures} since those are {@code ConfiguredFeature} keys, not placed ones.
 * <p>
 * Mob spawns staged deliberately: frogs + slime now (land-only, matches what actually exists to
 * stand on), fish/squid deferred until milestone 7 once there's real water to test them against.
 * Slime needs three things together, not just one — confirmed via {@code BiomeTags} inspection:
 * the {@link net.minecraft.tags.BiomeTags#ALLOWS_SURFACE_SLIME_SPAWNS} tag (set in
 * {@link ModBiomeTagProvider}), an explicit {@link MobSpawnSettings.SpawnerData} entry, and the
 * {@link EnvironmentAttributes#SURFACE_SLIME_SPAWN_CHANCE} attribute — the attribute alone does
 * nothing without the tag and the spawn-list entry.
 */
public class ModBiomes {
    public static final ResourceKey<Biome> FLOODED_FOREST = ResourceKey.create(
            Registries.BIOME, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "flooded_forest"));

    public static void bootstrap(BootstrapContext<Biome> context) {
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);

        BiomeGenerationSettings.Builder generationSettings = new BiomeGenerationSettings.Builder(placedFeatures, carvers);
        // Not addDefaultCarversAndLakes — that bundles in a surface lava lake, wrong for a wet
        // biome. Caves/canyons plus the underground-only lava lake, no surface one.
        generationSettings.addCarver(Carvers.CAVE);
        generationSettings.addCarver(Carvers.CAVE_EXTRA_UNDERGROUND);
        generationSettings.addCarver(Carvers.CANYON);
        generationSettings.addFeature(GenerationStep.Decoration.LAKES, MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND);
        // Small dry mounds, re-enabled now that flooding comes from TotalityOverworldSurfaceRules
        // instead of MoundFeature digging (that produced real bugs: seams, inconsistent depth,
        // exposed walls) — MoundFeature's only job now is raising small dry patches above the
        // surface-rule-placed water, run early (LAKES step) so later vegetation sees the corrected
        // heightmap. Tree islands registered first so they claim first pick of eligible flooded
        // columns; bare islands (higher count) then fill in the remaining columns afterward — gives
        // a natural mix of tree-topped and plain islands instead of an all-or-nothing split.
        generationSettings.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.MOUND_TREE_ISLAND_PLACED_KEY);
        generationSettings.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.MOUND_BARE_ISLAND_PLACED_KEY);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generationSettings);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generationSettings);
        BiomeDefaultFeatures.addDefaultOres(generationSettings);

        BiomeDefaultFeatures.addFerns(generationSettings);
        BiomeDefaultFeatures.addSwampVegetation(generationSettings);
        BiomeDefaultFeatures.addDefaultMushrooms(generationSettings);
        BiomeDefaultFeatures.addSwampClayDisk(generationSettings);
        generationSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.FALLEN_OAK_PLACED_KEY);
        generationSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.FALLEN_BIRCH_PLACED_KEY);

        MobSpawnSettings.Builder mobSpawns = new MobSpawnSettings.Builder()
                .addSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityType.FROG, 2, 5))
                .addSpawn(MobCategory.MONSTER, 1, new MobSpawnSettings.SpawnerData(EntityType.SLIME, 1, 2));

        context.register(FLOODED_FOREST, new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.7f)
                .downfall(0.9f)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .waterColor(0x3F8E82) // clear blue-green, distinct from vanilla Swamp's murky tint
                        .grassColorOverride(0x7BAF6B)
                        .foliageColorOverride(0x6FA05C)
                        .build())
                // FOG_COLOR/FOG_START_DISTANCE: light general atmospheric haze, not literal ground
                // mist (that's a future particle-pipeline feature). Tunable during verification.
                .setAttribute(EnvironmentAttributes.FOG_COLOR, 0xC8D8C0)
                .setAttribute(EnvironmentAttributes.FOG_START_DISTANCE, 16.0f)
                .setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, 0x1F6E68)
                .setAttribute(EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE, 0.15f)
                .mobSpawnSettings(mobSpawns.build())
                .generationSettings(generationSettings.build())
                .build());
    }

    private ModBiomes() {}
}
