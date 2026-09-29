package zcylas.totality.block.alchemy;

import com.google.gson.JsonArray;
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
 * Creative Test E2, the Garlic crop and Garlic Clove: registration, generated loot/recipe/blockstate/tags and assets
 * (the live planting, growth, bone meal, loot sampling and harvest checks are GarlicCropVerification on the server).
 */
class GarlicCropRegressionTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final Path RES = Path.of("src/main/resources/assets/totality");
    private static final Path GEN = Path.of("src/main/generated");

    /** Source text with LF line endings (several of these sources are CRLF). */
    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p)).replace("\r\n", "\n");
    }

    private static JsonObject json(String rel) throws Exception {
        return JsonParser.parseString(Files.readString(GEN.resolve(rel))).getAsJsonObject();
    }

    @Test
    void cropAndCloveAreRegisteredAndWired() throws Exception {
        String blocks = read(J + "init/blocks/AlchemyBlocks.java");
        assertTrue(blocks.contains("GarlicCropBlock GARLIC_CROP = TotalityRegistry.registerBlock(\n            \"garlic_crop\""));
        assertTrue(blocks.contains(".pushReaction(PushReaction.DESTROY),\n            false\n    );"), "no block item, like vanilla crops");
        String items = read(J + "init/items/SKIngredientItems.java");
        assertTrue(items.contains("\"garlic_clove\",\n            properties -> new BlockItem(AlchemyBlocks.GARLIC_CROP, properties)"));
        assertTrue(items.contains("ClassificationsComponent.of(ItemType.SEED, ItemType.INGREDIENT, ItemType.FOOD)"));
        assertTrue(items.contains(".food(INGREDIENT_FOOD).useItemDescriptionPrefix()"));
        assertTrue(items.contains("\"garlic\",\n            GarlicItem::new"), "the existing Garlic item stays");
        assertTrue(read(J + "block/alchemy/GarlicCropBlock.java").contains("return SKIngredientItems.GARLIC_CLOVE;"));
        assertTrue(read(J + "init/ModGroups.java").contains("output.accept(SKIngredientItems.GARLIC_CLOVE);"));
        assertTrue(read(J + "Totality.java").contains("GarlicCropVerification.register();"));
        assertTrue(read(J + "api/core/rpgutils/rarity/ItemType.java").contains("    SEED,\n"));
    }

    @Test
    void lootTableGivesACloveUntilMatureThenGarlicWithBinomialFortune() throws Exception {
        JsonObject loot = json("data/totality/loot_table/blocks/garlic_crop.json");
        assertEquals("minecraft:explosion_decay", loot.getAsJsonArray("functions").get(0).getAsJsonObject().get("function").getAsString());
        JsonArray pools = loot.getAsJsonArray("pools");
        assertEquals(1, pools.size(), "one pool: exactly one kind of drop per break");
        JsonObject alternatives = pools.get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("minecraft:alternatives", alternatives.get("type").getAsString());
        JsonArray children = alternatives.getAsJsonArray("children");
        JsonObject mature = children.get(0).getAsJsonObject();
        assertEquals("totality:garlic", mature.get("name").getAsString());
        JsonObject condition = mature.getAsJsonArray("conditions").get(0).getAsJsonObject();
        assertEquals("7", condition.getAsJsonObject("properties").get("age").getAsString());
        JsonObject bonus = mature.getAsJsonArray("functions").get(0).getAsJsonObject();
        assertEquals("minecraft:binomial_with_bonus_count", bonus.get("formula").getAsString());
        assertEquals("minecraft:fortune", bonus.get("enchantment").getAsString());
        assertEquals(0, bonus.getAsJsonObject("parameters").get("extra").getAsInt());
        assertEquals(0.5714286, bonus.getAsJsonObject("parameters").get("probability").getAsDouble(), 1e-7);
        JsonObject immature = children.get(1).getAsJsonObject();
        assertEquals("totality:garlic_clove", immature.get("name").getAsString());
        assertFalse(immature.has("functions"), "an immature crop returns exactly one Clove");
        assertEquals(2, children.size());
    }

    @Test
    void oneGarlicCraftsIntoFourClovesAndThereIsNoReverseRecipe() throws Exception {
        JsonObject recipe = json("data/totality/recipe/garlic_clove.json");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals(1, recipe.getAsJsonArray("ingredients").size());
        assertEquals("totality:garlic", recipe.getAsJsonArray("ingredients").get(0).getAsString());
        assertEquals("totality:garlic_clove", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(4, recipe.getAsJsonObject("result").get("count").getAsInt());
        assertFalse(Files.exists(GEN.resolve("data/totality/recipe/garlic.json")));
    }

    @Test
    void eightStagesEachWithItsOwnCrossedPlaneModelAndTexture() throws Exception {
        JsonObject variants = json("assets/totality/blockstates/garlic_crop.json").getAsJsonObject("variants");
        assertEquals(8, variants.size());
        for (int age = 0; age < 8; age++) {
            assertEquals("totality:block/crop/garlic/garlic_stage" + age,
                    variants.getAsJsonObject("age=" + age).get("model").getAsString());
            JsonObject model = JsonParser.parseString(Files.readString(
                    RES.resolve("models/block/crop/garlic/garlic_stage" + age + ".json"))).getAsJsonObject();
            assertEquals("totality:block/template/cross_crop", model.get("parent").getAsString());
            BufferedImage im = ImageIO.read(RES.resolve("textures/block/crop/garlic/garlic_stage" + age + ".png").toFile());
            assertEquals(16, im.getWidth());
            assertEquals(16, im.getHeight());
        }
        String template = Files.readString(RES.resolve("models/block/template/cross_crop.json"));
        assertTrue(template.contains("\"from\": [ 0.8, -1, 8 ]") && template.contains("\"from\": [ 8, -1, 0.8 ]"),
                "two crossed planes sunk 1 px, onto the 15 px farmland");
        assertTrue(top("7") < top("0"), "the mature stage is taller than the first");
    }

    private static int top(String stage) throws Exception {
        BufferedImage im = ImageIO.read(RES.resolve("textures/block/crop/garlic/garlic_stage" + stage + ".png").toFile());
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if ((im.getRGB(x, y) >>> 24) != 0) return y;
        return 16;
    }

    @Test
    void cloveItemAssetsLangAndCropTags() throws Exception {
        BufferedImage clove = ImageIO.read(RES.resolve("textures/item/garlic_clove.png").toFile());
        BufferedImage garlic = ImageIO.read(RES.resolve("textures/item/garlic.png").toFile());
        assertEquals(garlic.getWidth(), clove.getWidth(), "same canvas and texel density as the Garlic bulb");
        assertEquals(garlic.getHeight(), clove.getHeight());
        assertTrue(Files.exists(GEN.resolve("assets/totality/items/garlic_clove.json")));
        String lang = Files.readString(GEN.resolve("assets/totality/lang/en_us.json"));
        assertTrue(lang.contains("\"item.totality.garlic_clove\": \"Garlic Clove\"") && lang.contains("\"block.totality.garlic_crop\": \"Garlic\""));
        assertTrue(Files.readString(GEN.resolve("data/minecraft/tags/block/crops.json")).contains("\"totality:garlic_crop\""));
        assertTrue(Files.readString(GEN.resolve("data/minecraft/tags/block/maintains_farmland.json")).contains("\"totality:garlic_crop\""));
    }
}
