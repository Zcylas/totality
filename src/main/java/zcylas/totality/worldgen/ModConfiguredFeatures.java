package zcylas.totality.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import zcylas.totality.Totality;
import zcylas.totality.init.blocks.OreBlocks;

import java.util.List;

public class ModConfiguredFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> GRAPHITE_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID,"graphite_ore")
    );
    public static final ResourceKey<ConfiguredFeature<?, ?>> TIN_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID,"tin_ore")
    );
    public static final ResourceKey<ConfiguredFeature<?, ?>> LEAD_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID,"lead_ore")
    );
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVER_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID,"silver_ore")
    );
    public static final ResourceKey<ConfiguredFeature<?, ?>> RUBY_ORE_KEY = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID,"ruby_ore")
    );

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?,?>> context){
        RuleTest stoneReplaceables = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslateReplaceables = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);

        List<OreConfiguration.TargetBlockState> graphiteOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.GRAPHITE_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_GRAPHITE_ORE.defaultBlockState())
        );
        List<OreConfiguration.TargetBlockState> leadOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.LEAD_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_LEAD_ORE.defaultBlockState())
        );
        List<OreConfiguration.TargetBlockState> tinOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.TIN_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_TIN_ORE.defaultBlockState())
        );
        List<OreConfiguration.TargetBlockState> silverOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.SILVER_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_SILVER_ORE.defaultBlockState())
        );
        List<OreConfiguration.TargetBlockState> rubyOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.RUBY_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_RUBY_ORE.defaultBlockState())
        );

        context.register(GRAPHITE_ORE_KEY, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(graphiteOreTargets, 6)
        ));
        context.register(LEAD_ORE_KEY, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(leadOreTargets, 6)
        ));
        context.register(TIN_ORE_KEY, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(tinOreTargets, 8)
        ));
        context.register(SILVER_ORE_KEY, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(silverOreTargets, 3)
        ));
        context.register(RUBY_ORE_KEY, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(rubyOreTargets, 3)
        ));
    }
}
