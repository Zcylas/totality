package zcylas.totality.block.alchemy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creative Test I, the Jueyun Chili and its plant: registration, classification, the two-state bush wiring, generated
 * blockstate/models/lang, assets, and the absence of crop/farming/worldgen additions (the live harvest, regrowth,
 * placement, breaking, eating and persistence checks are JueyunChiliVerification on the server).
 */
class JueyunChiliRegressionTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final Path RES = Path.of("src/main/resources/assets/totality");
    private static final Path GEN = Path.of("src/main/generated");

    /** Source text with LF line endings (several of these sources are CRLF). */
    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p)).replace("\r\n", "\n");
    }

    private static JsonObject json(Path p) throws Exception {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @Test
    void itemIsAFoodIngredientRestoringTotalityFood() throws Exception {
        String items = read(J + "init/items/SKIngredientItems.java");
        assertTrue(items.contains("\"jueyun_chili\",\n            properties -> new TotalityFoodItem(properties, 4, 1.0F, 1.6F)"),
                "edible through the Totality Food resource: 4 Food, +1.0 temporary Saturation, 1.6 s");
        assertTrue(items.contains("ClassificationsComponent.of(ItemType.FOOD, ItemType.INGREDIENT))"), "FOOD • INGREDIENT");
        assertTrue(items.contains("new RarityComponent(ItemRarity.COMMON)"));
        String lang = Files.readString(GEN.resolve("assets/totality/lang/en_us.json"));
        assertTrue(lang.contains("\"item.totality.jueyun_chili\": \"Jueyun Chili\""));
        assertTrue(lang.contains("\"block.totality.jueyun_chili_plant\": \"Jueyun Chili Plant\""));
        assertTrue(read(J + "init/ModGroups.java").contains(
                "output.accept(AlchemyBlocks.JUEYUN_CHILI_PLANT);\n                            output.accept(SKIngredientItems.JUEYUN_CHILI);"));
    }

    @Test
    void plantIsAMountainFlowerBushWithWildPlantProperties() throws Exception {
        String block = read(J + "block/alchemy/JueyunChiliPlantBlock.java");
        assertTrue(block.contains("public class JueyunChiliPlantBlock extends MountainFlowerBushBlock {"),
                "reuses the bush's harvested property, harvest, regrowth and bone meal");
        assertTrue(block.contains("return SKIngredientItems.JUEYUN_CHILI;"));
        assertTrue(block.contains("return level.getBlockState(pos.below()).is(BlockTags.SUPPORTS_VEGETATION);"));
        assertTrue(block.contains("boolean fruiting = !state.getValue(HARVESTED);")
                && block.contains("if (fruiting && !level.isClientSide()) {"), "the pick sound is server-side only");
        assertFalse(block.contains("IntegerProperty") || block.contains("CropBlock") || block.contains("FARMLAND"),
                "not a crop: no age stages, no farmland rule");
        String blocks = read(J + "init/blocks/AlchemyBlocks.java");
        assertTrue(blocks.contains("JueyunChiliPlantBlock JUEYUN_CHILI_PLANT = TotalityRegistry.registerBlock(\n            \"jueyun_chili_plant\""));
        assertTrue(blocks.contains("                    .noCollision()\n                    .randomTicks()\n                    .instabreak()\n"
                        + "                    .sound(SoundType.GRASS)\n                    .pushReaction(PushReaction.DESTROY),\n"
                        + "            new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))\n    );\n\n"
                        + "    public static void register() {}"),
                "placeable block item, bush properties, destroyed by pistons");
        // the bush base class itself is untouched: the two harvest states are its boolean property
        String bush = read(J + "api/core/util/MountainFlowerBushBlock.java");
        assertTrue(bush.contains("BooleanProperty HARVESTED = BooleanProperty.create(\"harvested\")"));
        assertTrue(bush.contains("random.nextInt(50) == 0"));
    }

    @Test
    void blockstateHasExactlyTheTwoHarvestStatesOnCrossedPlanes() throws Exception {
        JsonObject variants = json(GEN.resolve("assets/totality/blockstates/jueyun_chili_plant.json")).getAsJsonObject("variants");
        assertEquals(2, variants.size());
        assertEquals("totality:block/plant/jueyun_chili/jueyun_chili_plant_fruiting",
                variants.getAsJsonObject("harvested=false").get("model").getAsString());
        assertEquals("totality:block/plant/jueyun_chili/jueyun_chili_plant_picked",
                variants.getAsJsonObject("harvested=true").get("model").getAsString());
        for (String s : new String[]{"fruiting", "picked"}) {
            JsonObject model = json(RES.resolve("models/block/plant/jueyun_chili/jueyun_chili_plant_" + s + ".json"));
            assertEquals("minecraft:block/cross", model.get("parent").getAsString());
            assertEquals("totality:block/plant/jueyun_chili/jueyun_chili_plant_" + s,
                    model.getAsJsonObject("textures").get("cross").getAsString());
        }
        JsonObject plantItem = json(GEN.resolve("assets/totality/models/item/jueyun_chili_plant.json"));
        assertEquals("totality:block/plant/jueyun_chili/jueyun_chili_plant_picked",
                plantItem.getAsJsonObject("textures").get("layer0").getAsString());
    }

    /** Asset-agnostic (the approved plant art is Astra's, 2026-09-29): what matters is the two states' contract. */
    @Test
    void theTwoPlantStatesShareOneCanvasAndOnlyTheFruitingOneBearsChilis() throws Exception {
        BufferedImage full = ImageIO.read(RES.resolve("textures/block/plant/jueyun_chili/jueyun_chili_plant_fruiting.png").toFile());
        BufferedImage bare = ImageIO.read(RES.resolve("textures/block/plant/jueyun_chili/jueyun_chili_plant_picked.png").toFile());
        assertEquals(full.getWidth(), full.getHeight(), "square");
        assertEquals(full.getWidth(), bare.getWidth(), "both states on the same canvas");
        assertEquals(full.getHeight(), bare.getHeight());
        assertEquals(0, full.getWidth() & (full.getWidth() - 1), "power-of-two size (mip-safe)");
        int redInFull = 0, redInBare = 0, opaqueBare = 0;
        for (int y = 0; y < full.getHeight(); y++) {
            for (int x = 0; x < full.getWidth(); x++) {
                int a = full.getRGB(x, y), b = bare.getRGB(x, y);
                assertTrue((a >>> 24) == 0 || (a >>> 24) == 255, "cutout alpha only");
                assertTrue((b >>> 24) == 0 || (b >>> 24) == 255, "cutout alpha only");
                if (isRed(a)) redInFull++;
                if (isRed(b)) redInBare++;
                if ((b >>> 24) != 0) opaqueBare++;
            }
        }
        assertTrue(redInFull >= 10 * Math.max(1, redInBare), "the fruiting state carries the red chilis: " + redInFull + " vs " + redInBare);
        assertTrue(redInBare < opaqueBare / 20, "the picked state has no fruit (only incidental reddish bark)");
    }

    private static boolean isRed(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (argb >>> 24) != 0 && r > 150 && g < 110 && b < 90;
    }

    @Test
    void itemTextureMatchesTheReferenceColours() throws Exception {
        BufferedImage im = ImageIO.read(RES.resolve("textures/item/jueyun_chili.png").toFile());
        assertEquals(48, im.getWidth());
        assertEquals(48, im.getHeight());
        int red = 0, green = 0, flame = 0, clear = 0;
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                int p = im.getRGB(x, y);
                int a = p >>> 24, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                if (a == 0) { clear++; continue; }
                assertEquals(255, a, "cutout alpha only");
                if (r > 170 && g < 100 && b < 100) red++;
                else if (g > 110 && r < 120 && b < 80) green++;
                else if (r > 220 && g > 120 && b < 110) flame++;
            }
        }
        assertTrue(red > 300, "red body " + red);
        assertTrue(green >= 6, "green stalk " + green);
        assertTrue(flame >= 20, "orange/yellow lower tips " + flame);
        assertTrue(clear > 900, "transparent surround " + clear);
    }

    @Test
    void noCropFarmingOrWorldgenAdditions() throws Exception {
        assertFalse(Files.exists(GEN.resolve("data/totality/loot_table/blocks/jueyun_chili_plant.json")),
                "bush rule: no loot table (the fruit only comes from harvesting)");
        assertFalse(Files.exists(GEN.resolve("data/totality/recipe/jueyun_chili.json")));
        for (String tag : new String[]{"crops", "maintains_farmland"}) {
            assertFalse(Files.readString(GEN.resolve("data/minecraft/tags/block/" + tag + ".json")).contains("jueyun"));
        }
        try (var files = Files.walk(GEN.resolve("data/totality/worldgen"))) {
            assertTrue(files.noneMatch(p -> p.toString().contains("jueyun")), "no world generation yet");
        }
        assertFalse(read(J + "init/TotalityBiomeModifications.java").contains("JUEYUN"));
        assertTrue(read(J + "Totality.java").contains("JueyunChiliVerification.register();"));
    }
}
