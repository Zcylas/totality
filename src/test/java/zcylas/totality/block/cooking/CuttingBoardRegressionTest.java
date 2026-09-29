package zcylas.totality.block.cooking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creative Test F, the Cutting Board: wiring, generated data and model construction (the live placement, chopping,
 * persistence and breaking checks are CuttingBoardVerification on the server; rendering is the client capture).
 */
class CuttingBoardRegressionTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final Path RES = Path.of("src/main/resources/assets/totality");
    private static final Path GEN = Path.of("src/main/generated");

    /** Source text with LF line endings (several of these sources are CRLF). */
    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p)).replace("\r\n", "\n");
    }

    @Test
    void blockBlockEntityRendererAndSuiteAreRegistered() throws Exception {
        assertTrue(read(J + "init/blocks/CookingBlocks.java").contains("\"cutting_board\",\n            CuttingBoardBlock::new"));
        assertTrue(read(J + "init/ModBlocks.java").contains("CookingBlocks.register();"));
        assertTrue(read(J + "init/ModBlockEntities.java").contains("CuttingBoardBlockEntity::new,\n                    CookingBlocks.CUTTING_BOARD"));
        assertTrue(read(J + "TotalityClient.java").contains("ModBlockEntities.CUTTING_BOARD,\n                CuttingBoardRenderer::new"));
        assertTrue(read(J + "Totality.java").contains("CuttingBoardVerification.register();"));
        assertTrue(read(J + "init/ModGroups.java").contains("output.accept(CookingBlocks.CUTTING_BOARD);"));
    }

    @Test
    void v1RecipeIsGarlicToFourClovesWithASwordAndNoFutureSystemsYet() throws Exception {
        String be = read(J + "blockentity/cooking/CuttingBoardBlockEntity.java");
        assertTrue(be.contains("ingredient.is(SKIngredientItems.GARLIC) ? new ItemStack(SKIngredientItems.GARLIC_CLOVE, 4)"));
        assertTrue(be.contains("ingredient.shrink(1);"), "one ingredient per chop");
        assertTrue(be.contains("if (!result.isEmpty()) Block.popResourceFromFace(serverLevel, worldPosition, Direction.UP, result);"),
                "output that does not fit is dropped on the board, never discarded");
        assertTrue(read(J + "block/cooking/CuttingBoardBlock.java").contains("stack.is(ItemTags.SWORDS)"), "temporary cutting tool: any sword");
        assertFalse(be.contains("Random") || be.contains("roll"), "V1 chopping is deterministic (the DEX check is future work)");
        assertTrue(be.contains("preRemoveSideEffects"), "contents drop when the board is removed");
    }

    @Test
    void generatedDataDropsItselfFacesFourWaysAndIsAxeMineable() throws Exception {
        assertTrue(Files.readString(GEN.resolve("data/totality/loot_table/blocks/cutting_board.json")).contains("\"name\": \"totality:cutting_board\""));
        JsonObject variants = JsonParser.parseString(Files.readString(GEN.resolve("assets/totality/blockstates/cutting_board.json")))
                .getAsJsonObject().getAsJsonObject("variants");
        assertEquals(4, variants.size());
        assertFalse(variants.getAsJsonObject("facing=north").has("y"));
        assertEquals(90, variants.getAsJsonObject("facing=east").get("y").getAsInt());
        assertEquals(180, variants.getAsJsonObject("facing=south").get("y").getAsInt());
        assertEquals(270, variants.getAsJsonObject("facing=west").get("y").getAsInt());
        assertTrue(Files.readString(GEN.resolve("data/minecraft/tags/block/mineable/axe.json")).contains("\"totality:cutting_board\""));
        assertTrue(Files.readString(GEN.resolve("assets/totality/lang/en_us.json")).contains("\"block.totality.cutting_board\": \"Cutting Board\""));
        assertTrue(Files.readString(GEN.resolve("assets/totality/items/cutting_board.json")).contains("totality:block/cutting_board"));
    }

    @Test
    void modelIsSevenCuboidsFourteenByEightByTwoWithARealHangingHole() throws Exception {
        JsonArray elements = JsonParser.parseString(Files.readString(RES.resolve("models/block/cutting_board.json")))
                .getAsJsonObject().getAsJsonArray("elements");
        assertEquals(7, elements.size());
        float minX = 16, maxX = 0, minZ = 16, maxZ = 0, maxY = 0;
        boolean[][] covered = new boolean[16][16];
        for (var e : elements) {
            JsonArray from = e.getAsJsonObject().getAsJsonArray("from"), to = e.getAsJsonObject().getAsJsonArray("to");
            minX = Math.min(minX, from.get(0).getAsFloat());
            maxX = Math.max(maxX, to.get(0).getAsFloat());
            minZ = Math.min(minZ, from.get(2).getAsFloat());
            maxZ = Math.max(maxZ, to.get(2).getAsFloat());
            maxY = Math.max(maxY, to.get(1).getAsFloat());
            assertEquals(0, from.get(1).getAsFloat());
            for (int x = from.get(0).getAsInt(); x < to.get(0).getAsInt(); x++)
                for (int z = from.get(2).getAsInt(); z < to.get(2).getAsInt(); z++) covered[x][z] = true;
        }
        assertEquals(14, maxX - minX, "14 px long");
        assertEquals(8, maxZ - minZ, "8 px wide");
        assertEquals(2, maxY, "2 px thick");
        for (int x = 12; x < 14; x++) for (int z = 7; z < 9; z++) assertFalse(covered[x][z], "hanging hole open at " + x + "," + z);
        for (int x = 11; x < 15; x++) assertTrue(covered[x][6] && covered[x][9], "handle rails frame the hole");
        assertTrue(covered[11][7] && covered[14][8], "handle neck and end close the frame");
    }
}
