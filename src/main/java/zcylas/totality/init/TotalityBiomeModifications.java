package zcylas.totality.init;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import zcylas.totality.worldgen.ModPlacedFeatures;

public final class TotalityBiomeModifications {

    public static void register() {
        registerOres();
        registerFlowers();
        registerWhitestone();
        registerNaturalBlocks();
    }

    private static void registerOres() {
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.GRAPHITE_ORE_PLACED_KEY
        );
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.LEAD_ORE_PLACED_KEY
        );
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.TIN_ORE_PLACED_KEY
        );
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.SILVER_ORE_PLACED_KEY
        );
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.RUBY_ORE_PLACED_KEY
        );
    }

    private static void registerFlowers() {
        // Blue Mountain Flower — plains, forests, meadows
        BiomeModifications.addFeature(
                BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)
                        .and(ctx -> {
                            String path = ctx.getBiomeKey().identifier().getPath();
                            return path.contains("plains")
                                    || path.contains("meadow")
                                    || (path.contains("forest")
                                    && !path.contains("birch")
                                    && !path.contains("dark"));
                        }),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.BLUE_MOUNTAIN_FLOWER_BUSH_PLACED_KEY
        );
        // Purple Mountain Flower — taiga, snowy biomes
        BiomeModifications.addFeature(
                BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)
                        .and(ctx -> {
                            String path = ctx.getBiomeKey().identifier().getPath();
                            return path.contains("taiga")
                                    || path.contains("snowy")
                                    || path.contains("frozen");
                        }),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.PURPLE_MOUNTAIN_FLOWER_BUSH_PLACED_KEY
        );
        // Red Mountain Flower — forests, jungles, warm biomes
        BiomeModifications.addFeature(
                BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)
                        .and(ctx -> {
                            String path = ctx.getBiomeKey().identifier().getPath();
                            return path.contains("jungle")
                                    || path.contains("birch")
                                    || path.contains("dark_forest")
                                    || path.contains("savanna");
                        }),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.RED_MOUNTAIN_FLOWER_BUSH_PLACED_KEY
        );
    }

    private static void registerWhitestone() {
        // Whitestone — everywhere
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.WHITESTONE_PLACED_KEY
        );
        // Whitestone — mountains more common
        BiomeModifications.addFeature(
                BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)
                        .and(ctx -> {
                            String path = ctx.getBiomeKey().identifier().getPath();
                            return path.contains("mountain")
                                    || path.contains("peak")
                                    || path.contains("highland");
                        }),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.WHITESTONE_PLACED_KEY
        );
        // Flecked Whitestone — everywhere rare
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.LOCAL_MODIFICATIONS,
                ModPlacedFeatures.FLECKED_WHITESTONE_PLACED_KEY
        );
    }

    private static void registerNaturalBlocks() {
        // Limestone
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.LIMESTONE_PLACED_KEY
        );
    }

    private TotalityBiomeModifications() {}
}