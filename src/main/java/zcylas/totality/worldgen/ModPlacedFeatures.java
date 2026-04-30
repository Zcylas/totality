package zcylas.totality.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.*;
import zcylas.totality.Totality;

import java.util.List;

public class ModPlacedFeatures {
    public static final ResourceKey<PlacedFeature> GRAPHITE_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "graphite_ore_placed")
    );
    public static final ResourceKey<PlacedFeature> LEAD_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "lead_ore_placed")
    );
    public static final ResourceKey<PlacedFeature> TIN_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "tin_ore_placed")
    );
    public static final ResourceKey<PlacedFeature> SILVER_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "silver_ore_placed")
    );
    public static final ResourceKey<PlacedFeature> RUBY_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "ruby_ore_placed")
    );

    public static void bootstrap(BootstrapContext<PlacedFeature> context){
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        context.register(GRAPHITE_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.GRAPHITE_ORE_KEY),
                List.of(
                        CountPlacement.of(8),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(-64),
                                VerticalAnchor.absolute(0)
                        ),
                        BiomeFilter.biome()
                )
        ));
        context.register(LEAD_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.LEAD_ORE_KEY),
                List.of(
                        CountPlacement.of(6),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(-32),
                                VerticalAnchor.absolute(48)
                        ),
                        BiomeFilter.biome()
                )
        ));
        context.register(TIN_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.TIN_ORE_KEY),
                List.of(
                        CountPlacement.of(8),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(0),
                                VerticalAnchor.absolute(80)
                        ),
                        BiomeFilter.biome()
                )
        ));
        context.register(SILVER_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.SILVER_ORE_KEY),
                List.of(
                        CountPlacement.of(4),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(-32),
                                VerticalAnchor.absolute(48)
                        ),
                        BiomeFilter.biome()
                )
        ));
        context.register(RUBY_ORE_PLACED_KEY, new PlacedFeature(
                configuredFeatures.getOrThrow(ModConfiguredFeatures.RUBY_ORE_KEY),
                List.of(
                        CountPlacement.of(5),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(16),
                                VerticalAnchor.absolute(64)
                        ),
                        BiomeFilter.biome()
                )
        ));
    }
}
