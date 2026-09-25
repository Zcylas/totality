package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Block Breaking V2 Pass 4B wiring: every remaining accepted transformation runs through the ONE existing transaction;
 * the chunk-boundary hardening checks each neighbour individually and no longer uses a broad surrounding-chunk check;
 * nested transactions do not delete an already-migrated record. Behaviour is verified live and in a real client world.
 */
class Pass4BTransformationSourceRegressionTest {

    private static String read(String p) throws Exception { return Files.readString(Path.of(p)); }

    private static final String MIXINS = "src/main/java/zcylas/totality/mixin/mining/";

    @Test
    void everyPass4BHookIsACommonMixinThatUsesTheExistingTransaction() throws Exception {
        String config = read("src/main/resources/totality.mixins.json");
        int common = config.indexOf("\"mixins\""), client = config.indexOf("\"client\"");
        for (String m : new String[]{"ConcreteSolidificationMixin", "ItemCombinerMenuAccessor", "AnvilDegradationMixin", "SpongeAbsorptionMixin",
                "CoralDeathMixin", "CauldronContentsMixin", "CauldronFillingMixin", "LayeredCauldronEmptyingMixin",
                "EnvironmentalTransformationMixin", "GrassSpreadTransformationMixin"}) {
            int at = config.indexOf("\"mining." + m + "\"");
            assertTrue(at > common && at < client, m + " must be a common mixin");
            if (!m.endsWith("Accessor")) assertTrue(read(MIXINS + m + ".java").contains("BlockDamageStorage.transaction("), m + " must use the transaction");
        }
    }

    @Test
    void concreteHookOnlyWrapsConcretePowderShapeUpdates() throws Exception {
        String src = read(MIXINS + "ConcreteSolidificationMixin.java");
        assertTrue(src.contains("if (!(blockState.getBlock() instanceof ConcretePowderBlock) || !(level instanceof ServerLevel serverLevel)) {"));
        assertFalse(src.contains("onLand") && src.contains("@WrapMethod(method = \"onLand\""), "landing at another position is not a transformation");
    }

    @Test
    void perPositionTransactionsForSpreadAndLightning() throws Exception {
        assertTrue(read(MIXINS + "GrassSpreadTransformationMixin.java").contains("BlockDamageStorage.transaction(level, pos, () -> original.call(level, pos, state))"),
                "each spread/decay call is its own transaction at the position it changes");
        String lightning = read(MIXINS + "EnvironmentalTransformationMixin.java");
        assertTrue(lightning.contains("{\"clearCopperOnLightningStrike\", \"lambda$randomStepCleaningCopper$0\"}"));
        assertTrue(lightning.contains("BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(level, pos, state))"));
    }

    @Test
    void chunkBoundaryHardeningAndNestedSafety() throws Exception {
        String storage = read("src/main/java/zcylas/totality/api/mining/BlockDamageStorage.java");
        int tied = storage.indexOf("private static java.util.Set<BlockPos> tiedPositions(");
        String body = storage.substring(tied, storage.indexOf("\n    }\n", tied));
        assertFalse(body.contains("hasChunksAt"), "no broad surrounding-chunk check");
        assertTrue(storage.contains("if (e.block.equals(key(after))) continue;"), "a nested transaction's migration is not undone");
        String profiles = read("src/main/java/zcylas/totality/api/mining/BlockProfiles.java");
        assertTrue(profiles.contains("reader.hasChunkAt(pos)"), "each owner/partner neighbour is checked individually");
    }

    @Test
    void pass4BPairsComeFromTheAcceptedFamiliesOnly() throws Exception {
        String src = read("src/main/java/zcylas/totality/api/mining/VanillaTransformations.java");
        for (String s : new String[]{"Blocks.CONCRETE_POWDER.pick(color), Blocks.CONCRETE.pick(color)", "pair(Blocks.ANVIL, Blocks.CHIPPED_ANVIL)",
                "pair(Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL)", "pair(Blocks.SPONGE, Blocks.WET_SPONGE)", "_coral_block\"), block(\"dead_",
                "WeatheringCopper.getFirst(weathered)", "pair(Blocks.GRASS_BLOCK, Blocks.DIRT)", "pair(Blocks.DIRT, Blocks.MYCELIUM)"}) {
            assertTrue(src.contains(s), s);
        }
        assertFalse(src.contains("pair(Blocks.WET_SPONGE, Blocks.SPONGE)"), "no existing-block wet->dry path exists in 26.2");
    }
}
