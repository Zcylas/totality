package zcylas.totality.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import zcylas.totality.worldgen.ModBiomes;

import java.util.List;

/**
 * Builds a small dry mound within {@code totality:flooded_forest}. Flooding is no longer this
 * feature's job — {@code TotalityOverworldSurfaceRules} floods the biome globally via a surface
 * rule now (an earlier version dug/flooded per-mound after the fact and produced real bugs: seams,
 * inconsistent water depth, exposed walls; see that class's doc for why). This just needs to raise
 * a small patch of ground above the water the surface rule already placed, tall enough to stay dry
 * regardless of the shared swamp noise field's per-column decision.
 */
public class MoundFeature extends Feature<MoundFeatureConfiguration> {

    private static final int SEA_LEVEL_BAND = 3;
    private static final int MOUND_CREST_MIN = 1;
    private static final int MOUND_CREST_MAX = 2;

    public MoundFeature(Codec<MoundFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<MoundFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        MoundFeatureConfiguration config = context.config();

        if (!validateLocation(level, chunkGenerator, origin)) {
            return false;
        }

        int radius = config.minRadius() + random.nextInt(config.maxRadius() - config.minRadius() + 1);
        int seaLevel = chunkGenerator.getSeaLevel();

        BlockPos crest = createMound(level, chunkGenerator, random, origin, radius, seaLevel);
        if (crest != null) {
            placeOptionalTree(level, chunkGenerator, random, config, crest);
        }
        return true;
    }

    private boolean validateLocation(WorldGenLevel level, ChunkGenerator chunkGenerator, BlockPos origin) {
        if (!isColumnInFloodedForest(level, chunkGenerator, origin.getX(), origin.getZ())) {
            return false;
        }
        int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
        if (Math.abs(surfaceY - chunkGenerator.getSeaLevel()) > SEA_LEVEL_BAND) {
            return false;
        }
        return isColumnFlooded(level, origin.getX(), origin.getZ());
    }

    /**
     * True if the surface rule already placed water directly above the real seafloor at this
     * column. Runs after {@code TotalityOverworldSurfaceRules} has already built the chunk's
     * surface, so this reads its result rather than re-deriving the same swamp-noise condition a
     * second time. Without this check, a mound was willing to build anywhere within
     * {@link #SEA_LEVEL_BAND} of sea level regardless of whether that ground was ever flooded —
     * carpeting ordinary dry terrain (and, on slopes, producing a checkerboard/waffle terracing
     * artifact) with mounds that had nothing to rise out of.
     */
    private static boolean isColumnFlooded(WorldGenLevel level, int x, int z) {
        int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        return level.getBlockState(new BlockPos(x, floorY + 1, z)).is(Blocks.WATER);
    }

    /**
     * Per-column biome check, not just the {@code PlacedFeature}'s origin-level {@code BiomeFilter}
     * — that only gates the placement's origin, not every column this feature itself writes to, so
     * a mound near a border could otherwise bleed into a neighboring biome. Biomes are sampled
     * three-dimensionally, so always query at a consistent surface-level position.
     * <p>
     * {@code OCEAN_FLOOR_WG}, not {@code WORLD_SURFACE_WG} — the latter counts water as "the
     * surface," so for any naturally-underwater column it reports a height near sea level instead
     * of the real seafloor below. Every "where's the actual ground" query in this class needs the
     * water-ignoring variant, or mound columns end up as thin slabs floating over the real terrain.
     */
    private static boolean isColumnInFloodedForest(WorldGenLevel level, ChunkGenerator chunkGenerator, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        int queryY = Math.max(surfaceY, chunkGenerator.getSeaLevel());
        return level.getBiome(new BlockPos(x, queryY, z)).is(ModBiomes.FLOODED_FOREST);
    }

    /**
     * Builds a small irregular mound cresting 1-2 blocks above sea level. Returns the crest
     * position for tree placement, or null if no column qualified.
     * <p>
     * Two things make this look organic rather than a flat-topped circular disc (the original
     * version, which only randomized the outer ring and used one uniform height for the whole
     * mound — read as visibly artificial): the skip chance is a smooth function of distance from
     * center across the *entire* footprint, not just the rim, giving a lumpy, non-circular outline;
     * and the crest height itself varies per column (taller near the center, occasionally a block
     * shorter near the edge) instead of one flat plateau height for every column.
     * <p>
     * Every per-column random decision below is drawn from a {@code RandomSource} seeded by that
     * column's own world position ({@link Mth#getSeed(int, int, int)}), never from the shared
     * {@code context.random()}. When two mound placements land close enough for their footprints to
     * overlap — routine at this feature's placement density — the old shared-random draws gave the
     * two calls independent, disagreeing answers for the same shared columns, which is exactly what
     * a checkerboard/waffle seam is: two out-of-sync coin flips at every boundary column. Reseeding
     * per position turns the same column into a pure function of (position, edge factor) for every
     * caller, so overlapping mounds now agree and merge into one coherent shape instead of fighting.
     */
    private BlockPos createMound(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random,
                                  BlockPos origin, int radius, int seaLevel) {
        int maxCrestHeight = seaLevel + MOUND_CREST_MAX;
        int minCrestHeight = seaLevel + MOUND_CREST_MIN;
        BlockPos crest = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;

                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                RandomSource columnRandom = RandomSource.create(Mth.getSeed(x, 0, z));

                float edgeFactor = (float) (dist / radius);
                if (edgeFactor > 0.4f && columnRandom.nextFloat() < (edgeFactor - 0.4f) * 1.2f) continue;

                if (!isColumnInFloodedForest(level, chunkGenerator, x, z)) continue;

                int localCrest = (edgeFactor > 0.6f && columnRandom.nextBoolean()) ? minCrestHeight : maxCrestHeight;

                int currentHeight = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                if (currentHeight >= localCrest) continue; // already high enough, leave alone
                // validateLocation only bounds the origin's own depth — a column elsewhere in this
                // mound's footprint can sit over genuinely deep water even when the origin doesn't
                // (real lake floors aren't flat). Raising a column regardless of how deep its own
                // floor is produces a tall, thin dirt pillar instead of a mound with a real base —
                // exactly what showed up once this feature got tested over an actual deep lake rather
                // than the shallow ponds used earlier. Skip any column too deep to plausibly be part
                // of the same shallow mound the origin validated against.
                if (seaLevel - currentHeight > SEA_LEVEL_BAND) continue;

                for (int y = currentHeight + 1; y < localCrest; y++) {
                    level.setBlock(cursor.set(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
                }
                level.setBlock(cursor.set(x, localCrest, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                cursor.set(x, localCrest + 1, z);
                if (!level.getBlockState(cursor).isAir()) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                }

                if (crest == null || (dx == 0 && dz == 0)) {
                    crest = new BlockPos(x, localCrest + 1, z);
                }
            }
        }
        return crest;
    }

    /**
     * Weighted-picks a tree candidate and grows it directly via {@code ConfiguredFeature.place},
     * not a {@code Holder<PlacedFeature>} — a {@code PlacedFeature} would rerun its own
     * count/rarity/heightmap/biome placement modifiers, which is wrong for "grow exactly one tree
     * at this exact spot" and risks the tree landing somewhere other than the mound.
     */
    private void placeOptionalTree(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random,
                                    MoundFeatureConfiguration config, BlockPos crest) {
        List<Holder<ConfiguredFeature<?, ?>>> candidates = config.treeCandidates();
        if (candidates.isEmpty()) return;
        Holder<ConfiguredFeature<?, ?>> chosen = candidates.get(random.nextInt(candidates.size()));
        chosen.value().place(level, chunkGenerator, random, crest);
    }
}
