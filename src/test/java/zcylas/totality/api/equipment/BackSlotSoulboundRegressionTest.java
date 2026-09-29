package zcylas.totality.api.equipment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Back slot, custom-equipment death rules, Soulbound and the Cape of the Mountebank: the wiring and data that the live
 * suites (BackSlotVerification on the server, BackSlotCapture / CapeCapture in the client) rely on.
 */
class BackSlotSoulboundRegressionTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final Path RES = Path.of("src/main/resources");
    private static final Path GEN = Path.of("src/main/generated");

    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p));
    }

    @Test
    void backIsAnIndependentSlotOfTheExistingComponent() throws Exception {
        assertEquals(6, PlayerEquipmentComponent.IDX_BACK);
        assertEquals(7, PlayerEquipmentComponent.SLOT_COUNT);
        for (int idx : new int[]{PlayerEquipmentComponent.IDX_BELT, PlayerEquipmentComponent.IDX_RING_1, PlayerEquipmentComponent.IDX_RING_2,
                PlayerEquipmentComponent.IDX_AMULET, PlayerEquipmentComponent.IDX_POUCH, PlayerEquipmentComponent.IDX_PHONE}) {
            assertNotEquals(PlayerEquipmentComponent.IDX_BACK, idx);
        }
        String menu = read(J + "menu/equipment/AccessoryInventoryMenu.java");
        assertTrue(menu.contains("PlayerEquipmentComponent.IDX_BACK, RING_SLOT_X, BACK_Y, backIcon,\n                TotalityBackItem::isBackEquipment"));
        assertNotNull(ImageIO.read(RES.resolve("assets/totality/textures/gui/sprites/container/slot/back.png").toFile()));
    }

    @Test
    void theBackSlotGrantsNothingByItself() throws Exception {
        String component = read(J + "api/equipment/PlayerEquipmentComponent.java");
        // AC/save bonuses still come from the two ring slots only.
        assertEquals(2, component.split("new int\\[]\\{ IDX_RING_1, IDX_RING_2 }", -1).length - 1);
        String backItem = read(J + "api/equipment/TotalityBackItem.java");
        assertTrue(backItem.contains("super(properties);"), "accessory constructor: no armor category");
        String cape = read(J + "item/equipment/CapeOfTheMountebankItem.java").replaceAll("(?s)/\\*.*?\\*/", "");   // code only
        for (String forbidden : new String[]{"teleport", "Teleport", "DimensionDoor", "getAcBonus", "onEquippedTick", "ParticleTypes"}) {
            assertFalse(cape.contains(forbidden), "the cape is wearable art only: " + forbidden);
        }
    }

    @Test
    void customEquipmentDropsOnActualDeathUnlessKeepInventory() throws Exception {
        String mixin = read(J + "mixin/PlayerEquipmentDeathDropMixin.java");
        assertTrue(mixin.contains("@Inject(method = \"dropEquipment\", at = @At(\"TAIL\"))"));
        assertTrue(mixin.contains("!level.getGameRules().get(GameRules.KEEP_INVENTORY)"));
        assertTrue(mixin.contains("EquipmentComponents.get(player).dropOnDeath()"));
        String component = read(J + "api/equipment/PlayerEquipmentComponent.java");
        assertTrue(component.contains("public void dropOnDeath()"));
        assertFalse(component.substring(component.indexOf("public void dropOnDeath()")).split("\n    }\n")[0].contains("ATTUNED"),
                "Attunement never protects an item from dropping");
        String mixins = Files.readString(RES.resolve("totality.mixins.json"));
        for (String m : List.of("\"PlayerEquipmentDeathDropMixin\"", "\"InventorySoulboundMixin\"", "\"client.AvatarRendererBackEquipmentMixin\"")) {
            assertTrue(mixins.contains(m), m);
        }
    }

    @Test
    void theVerificationNoLongerExpectsAnOrdinaryCapeToSurviveDeath() throws Exception {
        String suite = read(J + "api/equipment/BackSlotVerification.java");
        assertFalse(suite.contains("respawn copy keeps the Back slot"), "the old expectation encoded the keep-everything bug");
        assertTrue(suite.contains("death: the ordinary Cape (though attuned), Phone and Pouch item drop; the Soulbound Ring does not"));
        assertTrue(suite.contains("keepInventory: the ordinary Cape and Ring are carried over"));
    }

    @Test
    void soulboundIsARegisteredEnchantmentForTheEquipmentItems() throws Exception {
        JsonObject def = JsonParser.parseString(Files.readString(GEN.resolve("data/totality/enchantment/soulbound.json"))).getAsJsonObject();
        assertEquals(1, def.get("max_level").getAsInt());
        assertEquals("#totality:enchantable/soulbound", def.get("supported_items").getAsString());
        assertFalse(def.has("effects"), "Soulbound is read on death; it has no effect components");
        String tag = Files.readString(GEN.resolve("data/totality/tags/item/enchantable/soulbound.json"));
        for (String item : List.of("totality:basic_copper_phone", "totality:ring_of_protection", "totality:cape_of_the_mountebank")) {
            assertTrue(tag.contains(item), item);
        }
        assertTrue(read(J + "init/ModEnchantments.java").contains("context.register(SOULBOUND"));
        assertTrue(Files.readString(GEN.resolve("assets/totality/lang/en_us.json")).contains("\"enchantment.totality.soulbound\": \"Soulbound\""));
        // No acquisition yet: not in any vanilla enchanting-table, loot or trade tag.
        Path vanillaTags = GEN.resolve("data/minecraft/tags/enchantment");
        if (Files.isDirectory(vanillaTags)) {
            try (var files = Files.list(vanillaTags)) {
                for (Path f : files.toList()) assertFalse(Files.readString(f).contains("totality:soulbound"), f.toString());
            }
        }
    }

    @Test
    void capeAssetsExistAtMinecraftDensity() throws Exception {
        BufferedImage model = ImageIO.read(RES.resolve("assets/totality/textures/entity/equipment/cape_of_the_mountebank.png").toFile());
        BufferedImage icon = ImageIO.read(RES.resolve("assets/totality/textures/item/cape_of_the_mountebank.png").toFile());
        assertEquals(128, model.getWidth());
        assertEquals(64, model.getHeight());
        assertEquals(64, icon.getWidth(), "a purpose-drawn 64 x 64 icon");
        assertEquals(64, icon.getHeight());
        assertEquals(0, icon.getRGB(0, 0) >>> 24, "transparent background");
        // the lowest centre segment's outer face (plane at 0,15; 9 x 8 x 0: face at u 9..17, v 15..22) is painted,
        // and its hem is tattered (cut out)
        assertTrue((model.getRGB(13, 16) >>> 24) == 255, "lowest back segment painted");
        int cut = 0;
        for (int x = 9; x < 18; x++) if ((model.getRGB(x, 22) >>> 24) == 0) cut++;
        assertTrue(cut >= 3, "tattered hem");
        // where a rear column overlaps the centre column (one texel column each side) both carry the same texels; the
        // rear segments (outer faces at u 18 + w and 34 + w) are 6, 7 and 8 wide
        int[][] segments = {{0, 8, 6}, {8, 7, 7}, {15, 8, 8}};
        for (int[] seg : segments) {
            for (int v = seg[0]; v < seg[0] + seg[1]; v++) {
                assertEquals(model.getRGB(9, v), model.getRGB(18 + 2 * seg[2] - 1, v), "left overlap texel matches, row " + v);
                assertEquals(model.getRGB(17, v), model.getRGB(34 + seg[2], v), "right overlap texel matches, row " + v);
            }
        }
        String layer = read(J + "client/renderer/equipment/BackEquipmentLayer.java");
        assertTrue(layer.contains("LayerDefinition.create(mesh, 128, 64)"));
        // a plane's outer face and lining share their vertices: only back-face culling keeps them from Z-fighting
        assertTrue(layer.contains("RenderTypes.entityCutoutCull(CAPE_TEXTURE)"), "cape faces are culled");
        assertFalse(layer.contains("Mesh ") || layer.contains("addVertex"), "cuboids and planes only");
    }
}
