package zcylas.totality.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.*;
import zcylas.totality.Totality;

import java.util.List;

public class ModPlacedFeatures {
    //Ores
    public static final ResourceKey<PlacedFeature> GRAPHITE_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "graphite_ore_placed"));
    public static final ResourceKey<PlacedFeature> LEAD_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "lead_ore_placed"));
    public static final ResourceKey<PlacedFeature> TIN_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "tin_ore_placed"));
    public static final ResourceKey<PlacedFeature> SILVER_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "silver_ore_placed"));
    public static final ResourceKey<PlacedFeature> RUBY_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "ruby_ore_placed"));

    // Mountain Flowers
    public static final ResourceKey<PlacedFeature> BLUE_MOUNTAIN_FLOWER_BUSH_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "blue_mountain_flower_bush_placed"));
    public static final ResourceKey<PlacedFeature> PURPLE_MOUNTAIN_FLOWER_BUSH_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "purple_mountain_flower_bush_placed"));
    public static final ResourceKey<PlacedFeature> RED_MOUNTAIN_FLOWER_BUSH_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "red_mountain_flower_bush_placed"));
    //Whitestone
    public static final ResourceKey<PlacedFeature> WHITESTONE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "whitestone_placed"));
    public static final ResourceKey<PlacedFeature> FLECKED_WHITESTONE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "flecked_whitestone_placed"));
    //Natural Blocks
        //Limestone
    public static final ResourceKey<PlacedFeature> LIMESTONE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "limestone_placed"));

    // Flooded Forest
    public static final ResourceKey<PlacedFeature> FALLEN_OAK_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "fallen_oak_placed"));
    public static final ResourceKey<PlacedFeature> FALLEN_BIRCH_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "fallen_birch_placed"));
    public static final ResourceKey<PlacedFeature> MOUND_BARE_ISLAND_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mound_bare_island_placed"));
    public static final ResourceKey<PlacedFeature> MOUND_TREE_ISLAND_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mound_tree_island_placed"));

    public static void bootstrap(BootstrapContext<PlacedFeature> context){
        //Ores
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        context.register(GRAPHITE_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.GRAPHITE_ORE_KEY),
                List.of(CountPlacement.of(8), InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(0)),
                        BiomeFilter.biome())));
        context.register(LEAD_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.LEAD_ORE_KEY),
                List.of(CountPlacement.of(6), InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-32), VerticalAnchor.absolute(48)),
                        BiomeFilter.biome())));
        context.register(TIN_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.TIN_ORE_KEY),
                List.of(CountPlacement.of(8), InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(0), VerticalAnchor.absolute(80)),
                        BiomeFilter.biome())));
        context.register(SILVER_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.SILVER_ORE_KEY),
                List.of(CountPlacement.of(4), InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-32), VerticalAnchor.absolute(48)),
                        BiomeFilter.biome())));
        context.register(RUBY_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.RUBY_ORE_KEY),
                List.of(CountPlacement.of(5), InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(16), VerticalAnchor.absolute(64)),
                        BiomeFilter.biome())));

        // Mountain Flowers
        // Blue — plains, forests, meadows
        context.register(BLUE_MOUNTAIN_FLOWER_BUSH_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.BLUE_MOUNTAIN_FLOWER_BUSH_KEY),
                List.of(RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE, BiomeFilter.biome())));
        // Purple — taiga, snowy biomes
        context.register(PURPLE_MOUNTAIN_FLOWER_BUSH_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.PURPLE_MOUNTAIN_FLOWER_BUSH_KEY),
                List.of(RarityFilter.onAverageOnceEvery(8), InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE, BiomeFilter.biome())));
        // Red — forests, warm biomes
        context.register(RED_MOUNTAIN_FLOWER_BUSH_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.RED_MOUNTAIN_FLOWER_BUSH_KEY),
                List.of(RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE, BiomeFilter.biome())));
        //Whitestone
        context.register(WHITESTONE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.WHITESTONE_KEY),
                List.of(CountPlacement.of(2), InSquarePlacement.spread(),
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(128)),
                        BiomeFilter.biome())));
        context.register(FLECKED_WHITESTONE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.FLECKED_WHITESTONE_KEY),
                List.of(CountPlacement.of(4), InSquarePlacement.spread(),
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(128)),
                        BiomeFilter.biome())));
        //Natural Blocks
            //Limestone
        context.register(LIMESTONE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.LIMESTONE_KEY),
                List.of(CountPlacement.of(4), InSquarePlacement.spread(),
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(80)),
                        BiomeFilter.biome())));

        // Flooded Forest — vanilla's own fallen-log configured features (TreeFeatures.FALLEN_OAK_TREE/
        // FALLEN_BIRCH_TREE), wrapped since they're ConfiguredFeature keys, not PlacedFeature ones.
        // OCEAN_FLOOR_WG, not HEIGHTMAP_WORLD_SURFACE — the latter counts water as "the surface,"
        // so on naturally-underwater columns the log spawned floating at the water's top instead of
        // resting on the real lakebed (same root cause as MoundFeature's floating-island bug).
        PlacementModifier fallenLogHeight = HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG);
        context.register(FALLEN_OAK_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(TreeFeatures.FALLEN_OAK_TREE),
                List.of(RarityFilter.onAverageOnceEvery(10), InSquarePlacement.spread(),
                        fallenLogHeight, BiomeFilter.biome())));
        context.register(FALLEN_BIRCH_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(TreeFeatures.FALLEN_BIRCH_TREE),
                List.of(RarityFilter.onAverageOnceEvery(14), InSquarePlacement.spread(),
                        fallenLogHeight, BiomeFilter.biome())));

        // MoundFeature does its own per-column biome/sea-level validation internally (and re-derives
        // its own height via OCEAN_FLOOR_WG, ignoring the Y this placement gives it) — these
        // modifiers just decide how many attempts per chunk and roughly where to start. Bumped from
        // 4 to 14 attempts — reference image shows many small, tightly-packed islands, not sparse
        // ones; most attempts will still fail validateLocation's sea-level-band check.
        context.register(MOUND_BARE_ISLAND_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.MOUND_BARE_ISLAND_KEY),
                List.of(CountPlacement.of(14), InSquarePlacement.spread(),
                        HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG), BiomeFilter.biome())));

        // Tree islands are registered as a separate, lower-count placed feature rather than folding
        // tree growth into MOUND_BARE_ISLAND's own config — ModBiomes runs this one first in the
        // LAKES step, so it claims a minority of the eligible flooded columns for tree-topped mounds
        // before MOUND_BARE_ISLAND fills in the rest as plain ones, giving a natural mix instead of
        // every island getting (or none getting) a tree.
        context.register(MOUND_TREE_ISLAND_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.MOUND_TREE_ISLAND_KEY),
                List.of(CountPlacement.of(5), InSquarePlacement.spread(),
                        HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG), BiomeFilter.biome())));
    }
}