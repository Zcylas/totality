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

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?,?>> context){
        RuleTest stoneReplaceables = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslateReplaceables = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);

        List<OreConfiguration.TargetBlockState> graphiteOreTargets = List.of(
                OreConfiguration.target(stoneReplaceables, OreBlocks.GRAPHITE_ORE.defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, OreBlocks.DEEPSLATE_GRAPHITE_ORE.defaultBlockState())
        );

        context.register(GRAPHITE_ORE_KEY, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(graphiteOreTargets, 8)
        ));
    }
}
