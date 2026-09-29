package zcylas.totality.init.items;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Raw Meat exists as a Common, non-edible ingredient with its texture, model, name and Food-tab listing. */
class RawMeatItemRegressionTest {

    private static String read(String path) throws Exception {
        Path p = Path.of(path);
        assertTrue(Files.exists(p), "expected " + p);
        return Files.readString(p);
    }

    @Test
    void registeredAsACommonIngredientThatIsNotEdible() throws Exception {
        String src = read("src/main/java/zcylas/totality/init/items/IngredientItems.java");
        int start = src.indexOf("RAW_MEAT = TotalityRegistry.registerItem(");
        assertTrue(start >= 0);
        String block = src.substring(start, src.indexOf(");", start));
        assertTrue(block.contains("\"raw_meat\""));
        assertTrue(block.contains("new RarityComponent(ItemRarity.COMMON)"));
        assertTrue(block.contains("new ItemTypeComponent(ItemType.INGREDIENT)"));
        assertTrue(block.contains("stacksTo(64)"));
        assertFalse(block.contains(".food("), "raw meat is not edible until a cooking pass authors its values");
    }

    @Test
    void hasATextureModelNameAndFoodTabListing() throws Exception {
        BufferedImage tex = ImageIO.read(Path.of("src/main/resources/assets/totality/textures/item/raw_meat.png").toFile());
        // 48x48: the Minecraft-style reference's native grid (divisible by 16, so mipmapping is unaffected).
        assertEquals(48, tex.getWidth());
        assertEquals(48, tex.getHeight());
        assertTrue(read("src/main/generated/assets/totality/models/item/raw_meat.json").contains("totality:item/raw_meat"));
        assertTrue(read("src/main/generated/assets/totality/lang/en_us.json").contains("\"item.totality.raw_meat\": \"Raw Meat\""));
        String groups = read("src/main/java/zcylas/totality/init/ModGroups.java");
        int food = groups.indexOf("itemGroup.totality.food");
        assertTrue(food >= 0 && groups.indexOf("output.accept(IngredientItems.RAW_MEAT);", food) > food, "listed in the Food tab");
    }
}
