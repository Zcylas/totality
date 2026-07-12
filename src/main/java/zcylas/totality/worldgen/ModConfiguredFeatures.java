package zcylas.totality.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.ReplaceBlobsFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceSphereConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import zcylas.totality.Totality;
import zcylas.totality.init.blocks.AlchemyBlocks;
import zcylas.totality.init.blocks.NaturalBlocks;
import zcylas.totality.init.blocks.OreBlocks;
import zcylas.totality.init.blocks.WhitestoneBlocks;
import zcylas.totality.worldgen.feature.MoundFeatureConfiguration;

import java.util.List;

public class ModConfiguredFeatures {
    // Ores
    public static final ResourceKey<ConfiguredFeature<?, ?>> GRAPHITE_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID,"graphite_ore"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> TIN_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID,"tin_ore"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> LEAD_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID,"lead_ore"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVER_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID,"silver_ore"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> RUBY_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID,"ruby_ore"));

    // Mountain Flowers
    public static final ResourceKey<ConfiguredFeature<?, ?>> BLUE_MOUNTAIN_FLOWER_BUSH_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID,"blue_mountain_flower_bush"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> PURPLE_MOUNTAIN_FLOWER_BUSH_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID,"purple_mountain_flower_bush"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> RED_MOUNTAIN_FLOWER_BUSH_KEY  = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID,"red_mountain_flower_bush"));
    // Whitestone
    public static final ResourceKey<ConfiguredFeature<?, ?>> WHITESTONE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "whitestone"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> FLECKED_WHITESTONE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "flecked_whitestone"));
    //Natural Blocks
        //Limestone
    public static final ResourceKey<ConfiguredFeature<?, ?>> LIMESTONE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "limestone"));

    // Flooded Forest
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOUND_BARE_ISLAND_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mound_bare_island"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOUND_TREE_ISLAND_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mound_tree_island"));

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?,?>> context){
        //Ores
        RuleTest stoneReplaceables = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslateReplaceables = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);

        List<OreConfiguration.TargetBlockState> graphiteOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.GRAPHITE_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_GRAPHITE_ORE.defaultBlockState()));
        List<OreConfiguration.TargetBlockState> leadOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.LEAD_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_LEAD_ORE.defaultBlockState()));
        List<OreConfiguration.TargetBlockState> tinOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.TIN_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_TIN_ORE.defaultBlockState()));
        List<OreConfiguration.TargetBlockState> silverOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.SILVER_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_SILVER_ORE.defaultBlockState()));
        List<OreConfiguration.TargetBlockState> rubyOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.RUBY_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_RUBY_ORE.defaultBlockState()));

        context.register(GRAPHITE_ORE_KEY, new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(graphiteOreTargets, 6)));
        context.register(LEAD_ORE_KEY, new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(leadOreTargets, 6)));
        context.register(TIN_ORE_KEY, new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(tinOreTargets, 8)));
        context.register(SILVER_ORE_KEY, new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(silverOreTargets, 3)));
        context.register(RUBY_ORE_KEY, new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(rubyOreTargets, 3)));

        // Mountain Flowers
        context.register(BLUE_MOUNTAIN_FLOWER_BUSH_KEY, new ConfiguredFeature<>(
                Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(BlockStateProvider.simple(AlchemyBlocks.BLUE_MOUNTAIN_FLOWER_BUSH))));
        context.register(PURPLE_MOUNTAIN_FLOWER_BUSH_KEY, new ConfiguredFeature<>(
                Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(BlockStateProvider.simple(AlchemyBlocks.PURPLE_MOUNTAIN_FLOWER_BUSH))));
        context.register(RED_MOUNTAIN_FLOWER_BUSH_KEY, new ConfiguredFeature<>(
                Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(BlockStateProvider.simple(AlchemyBlocks.RED_MOUNTAIN_FLOWER_BUSH))));
        //Whitestone
        context.register(WHITESTONE_KEY, new ConfiguredFeature<>(Feature.REPLACE_BLOBS,
                new ReplaceSphereConfiguration(
                        Blocks.STONE.defaultBlockState(),
                        WhitestoneBlocks.WHITESTONE.defaultBlockState(),
                        UniformInt.of(6,12) // blob radius
                )));
        context.register(FLECKED_WHITESTONE_KEY, new ConfiguredFeature<>(Feature.REPLACE_BLOBS,
                new ReplaceSphereConfiguration(
                        WhitestoneBlocks.WHITESTONE.defaultBlockState(),
                        WhitestoneBlocks.FLECKED_WHITESTONE.defaultBlockState(),
                        UniformInt.of(1, 2) // small blobs, replacing whitestone only
                )));
        //Natural Blocks
            //Limestone
        context.register(LIMESTONE_KEY, new ConfiguredFeature<>(Feature.REPLACE_BLOBS,
                new ReplaceSphereConfiguration(
                        Blocks.STONE.defaultBlockState(),
                        NaturalBlocks.LIMESTONE.defaultBlockState(),
                        UniformInt.of(6, 12)
                )));

        // Flooded Forest — bare mound, no tree candidates. MOUND_TREE_ISLAND below shares this same
        // Feature/config type (milestone 7) with a populated candidate list; the two are registered
        // as independent placed features so each gets its own rarity, rather than making tree growth
        // a per-mound coin flip inside one placement. Small (1-2 radius) — reference image shows
        // many small, tightly-packed islands rather than fewer large ones; density is tuned via the
        // placed feature's count.
        context.register(MOUND_BARE_ISLAND_KEY, new ConfiguredFeature<>(
                ModFeatures.MOUND, new MoundFeatureConfiguration(1, 2, List.of())));

        // Tree candidates are resolved once here (bootstrap time), never inside MoundFeature.place()
        // — FeaturePlaceContext has no registry access, and features are singletons that may run
        // concurrently across chunk-gen threads. Baked straight into the config as already-resolved
        // Holders, same pattern vanilla's own RandomFeatureConfiguration uses.
        HolderGetter<ConfiguredFeature<?, ?>> treeLookup = context.lookup(Registries.CONFIGURED_FEATURE);
        List<Holder<ConfiguredFeature<?, ?>>> moundTreeCandidates = List.of(
                treeLookup.getOrThrow(TreeFeatures.SWAMP_OAK),
                treeLookup.getOrThrow(TreeFeatures.BIRCH));
        context.register(MOUND_TREE_ISLAND_KEY, new ConfiguredFeature<>(
                ModFeatures.MOUND, new MoundFeatureConfiguration(1, 2, moundTreeCandidates)));
    }
}