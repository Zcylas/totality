package zcylas.totality.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Builds the Overworld's real multi-noise climate parameter list: every vanilla point replayed
 * unchanged via Mojang's own {@link OverworldBiomeBuilder} (the exact class vanilla's own datagen
 * uses), plus real Swamp climate points remapped to {@code totality:flooded_forest}.
 * <p>
 * This is the second design here, not the first — a runtime-query overlay (intercepting
 * {@code MultiNoiseBiomeSource.getNoiseBiome}'s return value instead of touching the registered
 * parameter list) was tried and reverted. Studied Biomes O' Plenty's and Terralith's actual working
 * source/data as reference: both confirm real biome mods add genuine {@code Climate.ParameterPoint}
 * entries, never runtime-only overrides. The reason is structural, confirmed via bytecode:
 * {@code MultiNoiseBiomeSource.collectPossibleBiomes()} — which {@code /locate biome} and anything
 * else that pre-filters "what can this biome source even produce" reads from — derives strictly
 * from {@code this.parameters().values()}, the registered list. A runtime-only overlay never
 * touches that list, so {@code /locate} (and anything else relying on it) can never find the biome
 * no matter how common it actually is on the ground; confirmed empirically across two live tests.
 * <p>
 * The split itself: giving Flooded Forest just a rare-variant-sized slice (BOP's own Sunflower
 * Plains/Ice Spikes analogs claim only ~15-20% of their base biome's weirdness range) was the
 * wrong target — those are deliberately small easter eggs, not a proper biome. Flooded Forest gets
 * the entire contiguous middle chunk of Swamp's real weirdness bands instead (~86% of Swamp's total
 * span); Swamp keeps the two small bands at the extreme ends — still real and locatable, just
 * smaller. See {@link #remapSwampSubrangeToFloodedForest}.
 * <p>
 * <b>Why this can't be a plain datapack/JSON override:</b> {@code multi_noise_biome_source_
 * parameter_list/overworld.json}'s codec requires a {@code "preset"} field resolved against
 * {@code Preset.BY_NAME}, a closed, hardcoded Java map containing only
 * {@code minecraft:overworld}/{@code minecraft:nether} — confirmed by an actual failed load
 * ({@code IllegalStateException: No key preset in MapLike[...]}) when a hand-written direct-list
 * override was tried first. (Terralith gets around this by depending on Lithostitched, which mixins
 * its own extra {@code lithostitched:biomes} key into the same file's decode step — a real
 * dependency we don't want. We instead substitute this whole class's output in place of vanilla's
 * at the actual object-construction point; see {@link MultiNoiseBiomeSourceParameterListMixin}.)
 */
public final class TotalityOverworldClimate {

    /**
     * OverworldBiomeBuilder().addBiomes(...) point count. Bumped 7593 → 7594 for MC 26.2: vanilla
     * added a new {@code addUndergroundBiome(..., Biomes.SULFUR_CAVES)} call in
     * {@code addUndergroundBiomes} (one new underground biome, one new point) — confirmed by diffing
     * decompiled {@code OverworldBiomeBuilder} between 26.1.2 and 26.2. Not a Swamp point, so
     * {@link #remapSwampSubrangeToFloodedForest} passes it through untouched.
     */
    private static final int EXPECTED_VANILLA_POINT_COUNT = 7594;
    private static final String EXPECTED_VANILLA_FINGERPRINT = "bb4b08fa93763640a6bee504512f81780460d3d9dfcf196b04a5df5c231eb434";

    /**
     * Real vanilla Swamp points whose weirdness band isn't one of the two extreme-edge bands
     * ({@code weirdness().min() > -9333} and {@code weirdness().max() < 9333}) are remapped —
     * i.e. the entire contiguous middle chunk of Swamp's 7 real weirdness bands. Derived from
     * Swamp's actual point data (see {@link #buildPoints}'s caller), not hand-picked literal
     * bounds, so it stays a predictable, non-overlapping sub-range instead of arbitrary
     * nearby-but-distinct points.
     */
    private static final int EXPECTED_SWAMP_REMAP_COUNT = 10;

    private TotalityOverworldClimate() {}

    public static Climate.ParameterList<Holder<Biome>> buildParameterList(HolderGetter<Biome> biomeGetter) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> resolved = buildPoints().stream()
                .map(pair -> Pair.<Climate.ParameterPoint, Holder<Biome>>of(
                        pair.getFirst(), biomeGetter.getOrThrow(pair.getSecond())))
                .collect(Collectors.toList());
        return new Climate.ParameterList<>(resolved);
    }

    @SuppressWarnings("unchecked")
    private static void replayVanillaOverworldBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        try {
            java.lang.reflect.Method addBiomes = OverworldBiomeBuilder.class.getDeclaredMethod("addBiomes", Consumer.class);
            addBiomes.setAccessible(true);
            addBiomes.invoke(new OverworldBiomeBuilder(), mapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to reflectively call OverworldBiomeBuilder.addBiomes — "
                    + "did MC 26.1.2's OverworldBiomeBuilder API change?", e);
        }
    }

    private static List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> buildPoints() {
        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> vanillaPoints = new ArrayList<>();
        replayVanillaOverworldBiomes(vanillaPoints::add);

        verifyVanillaReplay(vanillaPoints);

        return remapSwampSubrangeToFloodedForest(vanillaPoints);
    }

    /**
     * Swamp's 14 real points (7 weirdness bands x 2 depth values) split into one contiguous
     * middle chunk (5 bands, ~86% of total weirdness span) plus two small edge bands at the
     * extremes. The middle chunk's points become Flooded Forest; Swamp keeps the two edge bands.
     * <p>
     * Within that middle chunk, each point is <i>further</i> split by continentalness at
     * {@link OverworldBiomeBuilder#FAR_INLAND_START} rather than remapped whole. Confirmed via
     * javap on {@code OverworldBiomeBuilder}'s real Swamp-placing call: vanilla's own
     * continentalness span there is {@code span(inlandContinentalness, farInlandContinentalness)}
     * — near-inland all the way through the *entire* far-inland band (0.3 to 1.0, the majority
     * of Swamp's total continentalness reach by area). Far-inland is deep-interior climate
     * territory, the same neighborhood highland/mountain biomes' climate cells (different erosion
     * bands) occupy; a live test after remapping Swamp's whole span showed unflooded high terrain
     * inside the biome's own footprint plus poor border blending. Checked whether BOP/Terralith
     * guard their own wet-biome water rule against this with a {@code SurfaceRules.steep()} or
     * erosion check — neither does; both simply never reach that far inland in the first place
     * (their swamp-analog call sites are gated to a narrower reach than vanilla's real Swamp).
     * Splitting at the far-inland boundary here does the same: Flooded Forest keeps the
     * coastal/near/mid-inland portion (also the better thematic fit — the reference image reads
     * as lowland/near-water, not deep-interior swamp), Swamp keeps the far-inland remainder
     * exactly as vanilla already placed it (unremarkable there, since normal Swamp isn't
     * exclusively water and doesn't fight its own surface rule the way Flooded Forest's
     * heavy-water-coverage rule does against naturally-elevated terrain).
     */
    private static List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> remapSwampSubrangeToFloodedForest(
            List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> vanillaPoints) {
        long farInlandCutoff = Math.round(OverworldBiomeBuilder.FAR_INLAND_START * 10000f);

        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> result = new ArrayList<>(vanillaPoints.size() + EXPECTED_SWAMP_REMAP_COUNT);
        int remapped = 0;
        for (Pair<Climate.ParameterPoint, ResourceKey<Biome>> pair : vanillaPoints) {
            Climate.ParameterPoint point = pair.getFirst();
            ResourceKey<Biome> biome = pair.getSecond();
            boolean isExtremeEdgeBand = point.weirdness().max() <= -9333L || point.weirdness().min() >= 9333L;
            if (biome != Biomes.SWAMP || isExtremeEdgeBand) {
                result.add(pair);
                continue;
            }

            Climate.Parameter continentalness = point.continentalness();
            if (continentalness.min() >= farInlandCutoff) {
                // Already entirely far-inland — leave as real Swamp untouched.
                result.add(pair);
                continue;
            }

            result.add(Pair.of(
                    withContinentalness(point, continentalness.min(), Math.min(continentalness.max(), farInlandCutoff)),
                    ModBiomes.FLOODED_FOREST));
            remapped++;

            if (continentalness.max() > farInlandCutoff) {
                // Keep the deep far-inland remainder as real Swamp, same as vanilla.
                result.add(Pair.of(withContinentalness(point, farInlandCutoff, continentalness.max()), Biomes.SWAMP));
            }
        }

        if (EXPECTED_SWAMP_REMAP_COUNT >= 0 && remapped != EXPECTED_SWAMP_REMAP_COUNT) {
            throw new IllegalStateException(
                    "Swamp climate remap count changed (expected " + EXPECTED_SWAMP_REMAP_COUNT
                            + ", got " + remapped + ") — review TotalityOverworldClimate's Swamp climate "
                            + "remap before trusting this Overworld biome placement.");
        }

        long distinctPoints = result.stream().map(Pair::getFirst).distinct().count();
        if (distinctPoints != result.size()) {
            throw new IllegalStateException("Duplicate climate parameter points after Swamp remap — "
                    + "review TotalityOverworldClimate before trusting this Overworld biome placement.");
        }

        return result;
    }

    private static Climate.ParameterPoint withContinentalness(Climate.ParameterPoint point, long min, long max) {
        return new Climate.ParameterPoint(point.temperature(), point.humidity(), new Climate.Parameter(min, max),
                point.erosion(), point.depth(), point.weirdness(), point.offset());
    }

    /**
     * Fails loudly (not silently) if a future MC update changes {@link OverworldBiomeBuilder}'s
     * output shape in a way this class doesn't account for.
     */
    private static void verifyVanillaReplay(List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> vanillaPoints) {
        String fingerprint = fingerprint(vanillaPoints);

        if (vanillaPoints.size() != EXPECTED_VANILLA_POINT_COUNT) {
            throw new IllegalStateException(
                    "OverworldBiomeBuilder point count changed (expected " + EXPECTED_VANILLA_POINT_COUNT
                            + ", got " + vanillaPoints.size() + ") — review TotalityOverworldClimate "
                            + "before trusting this Overworld biome placement.");
        }
        if (!EXPECTED_VANILLA_FINGERPRINT.equals(fingerprint)) {
            throw new IllegalStateException(
                    "OverworldBiomeBuilder climate values changed (fingerprint mismatch) — review "
                            + "TotalityOverworldClimate before trusting this Overworld biome placement.");
        }
    }

    /**
     * Canonicalizes and sorts every point before hashing, so the fingerprint reflects real
     * parameter changes rather than an incidental change in {@link OverworldBiomeBuilder}'s
     * iteration order.
     */
    private static String fingerprint(List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> points) {
        List<String> canonical = points.stream()
                .map(pair -> canonicalize(pair.getFirst()) + "|" + pair.getSecond().identifier())
                .sorted(Comparator.<String>naturalOrder())
                .collect(Collectors.toList());
        String joined = String.join("\n", canonical);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String canonicalize(Climate.ParameterPoint point) {
        return canonicalize(point.temperature()) + ","
                + canonicalize(point.humidity()) + ","
                + canonicalize(point.continentalness()) + ","
                + canonicalize(point.erosion()) + ","
                + canonicalize(point.depth()) + ","
                + canonicalize(point.weirdness()) + ","
                + point.offset();
    }

    private static String canonicalize(Climate.Parameter parameter) {
        return parameter.min() + ":" + parameter.max();
    }
}
