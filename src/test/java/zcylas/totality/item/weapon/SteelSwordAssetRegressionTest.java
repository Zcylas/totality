package zcylas.totality.item.weapon;

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
 * Creative Test H, the Steel Sword asset: the existing item keeps its identity and stats; its model is plain
 * axis-aligned cuboids (no meshes, no rotations) with in-range UVs on a 128 x 128 texture, and its item definition
 * shows a flat 32 x 32 icon in inventory slots and the 3D model everywhere else.
 */
class SteelSwordAssetRegressionTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/totality");
    private static final Path GEN = Path.of("src/main/generated/assets/totality");

    @Test
    void theExistingItemKeepsItsIdentityAndStats() throws Exception {
        String items = Files.readString(Path.of("src/main/java/zcylas/totality/init/items/BasicWeaponItems.java"));
        assertTrue(items.contains("public static final SkyrimSwordItem STEEL_SWORD = TotalityRegistry.registerItem(\n            \"steel_sword\","));
        assertTrue(items.contains("Dice.D8, 1, DamageTypes.SLASHING, AbilityScore.STR,"));
        assertTrue(items.contains(".durability(768)"));
    }

    @Test
    void theModelIsPlainCuboidsWithInRangeUvsAndAllDisplayContexts() throws Exception {
        JsonObject model = JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/steel_sword.json"))).getAsJsonObject();
        assertEquals("totality:item/steel_sword", model.getAsJsonObject("textures").get("0").getAsString());
        var elements = model.getAsJsonArray("elements");
        assertTrue(elements.size() >= 20 && elements.size() <= 40, "enough cuboids for the design, no micro-cuboid swarm: " + elements.size());
        for (var el : elements) {
            JsonObject e = el.getAsJsonObject();
            assertFalse(e.has("rotation"), "axis-aligned cuboids only");
            for (var face : e.getAsJsonObject("faces").entrySet()) {
                for (var uv : face.getValue().getAsJsonObject().getAsJsonArray("uv")) {
                    double v = uv.getAsDouble();
                    assertTrue(v >= 0 && v <= 16, e.get("name") + " " + face.getKey() + " uv " + v);
                }
            }
        }
        for (String ctx : new String[]{"thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
                "firstperson_lefthand", "ground", "gui", "fixed"}) {
            assertTrue(model.getAsJsonObject("display").has(ctx), ctx);
        }
        BufferedImage texture = ImageIO.read(ASSETS.resolve("textures/item/steel_sword.png").toFile());
        assertEquals(128, texture.getWidth());
        assertEquals(128, texture.getHeight());
    }

    @Test
    void inventorySlotsShowTheFlatIconAndEverythingElseTheModel() throws Exception {
        JsonObject def = JsonParser.parseString(Files.readString(GEN.resolve("items/steel_sword.json"))).getAsJsonObject().getAsJsonObject("model");
        assertEquals("minecraft:select", def.get("type").getAsString());
        assertEquals("minecraft:display_context", def.get("property").getAsString());
        JsonObject gui = def.getAsJsonArray("cases").get(0).getAsJsonObject();
        assertEquals("gui", gui.get("when").getAsString());
        assertEquals("totality:item/steel_sword_icon", gui.getAsJsonObject("model").get("model").getAsString());
        assertEquals("totality:item/steel_sword", def.getAsJsonObject("fallback").get("model").getAsString());
        BufferedImage icon = ImageIO.read(ASSETS.resolve("textures/item/steel_sword_icon.png").toFile());
        assertEquals(32, icon.getWidth());
        assertEquals(32, icon.getHeight());
    }
}
