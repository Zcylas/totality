package zcylas.totality.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.feature.Feature;
import zcylas.totality.Totality;
import zcylas.totality.worldgen.feature.MoundFeature;
import zcylas.totality.worldgen.feature.MoundFeatureConfiguration;

public class ModFeatures {
    public static final Feature<MoundFeatureConfiguration> MOUND = Registry.register(
            BuiltInRegistries.FEATURE,
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "mound"),
            new MoundFeature(MoundFeatureConfiguration.CODEC));

    public static void register() {}

    private ModFeatures() {}
}
