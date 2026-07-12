package zcylas.totality.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

/**
 * Config for {@link MoundFeature}. {@code treeCandidates} is present from the start (not added in
 * a later config-format change) — {@code MOUND_BARE_ISLAND} just uses an empty list, {@code
 * MOUND_TREE_ISLAND} populates it. Tree candidates are resolved once at {@code ModConfiguredFeatures}
 * bootstrap time via a {@code HolderGetter}, never at placement time (features are singletons that
 * may execute concurrently across chunk-gen threads, and {@code FeaturePlaceContext} has no registry
 * access anyway).
 */
public record MoundFeatureConfiguration(
        int minRadius,
        int maxRadius,
        List<Holder<ConfiguredFeature<?, ?>>> treeCandidates
) implements FeatureConfiguration {
    public static final Codec<MoundFeatureConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("min_radius").forGetter(MoundFeatureConfiguration::minRadius),
            Codec.INT.fieldOf("max_radius").forGetter(MoundFeatureConfiguration::maxRadius),
            ConfiguredFeature.CODEC.listOf().fieldOf("tree_candidates").forGetter(MoundFeatureConfiguration::treeCandidates)
    ).apply(instance, MoundFeatureConfiguration::new));
}
