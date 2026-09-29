package zcylas.totality.entity.vehicle;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creative Test D, the default skateboard: its wiring, assets and construction rules (the live checks are
 * SkateboardVerification on the server and the scene 54 capture in the client).
 */
class SkateboardRegressionTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final Path RES = Path.of("src/main/resources/assets/totality");
    private static final Path GEN = Path.of("src/main/generated/assets/totality");

    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p));
    }

    private static BufferedImage image(String rel) throws Exception {
        return ImageIO.read(RES.resolve(rel).toFile());
    }

    private static boolean contains(BufferedImage im, int rgb) {
        for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) if ((im.getRGB(x, y) & 0xFFFFFF) == rgb) return true;
        return false;
    }

    @Test
    void registeredAsAVehicleItemAndEntity() throws Exception {
        assertTrue(read(J + "init/ModEntities.java").contains("Identifier.fromNamespaceAndPath(Totality.MOD_ID, \"skateboard\")"));
        assertTrue(read(J + "init/items/VehicleItems.java").contains("\"skateboard\", SkateboardItem::new, new Item.Properties().stacksTo(1)"));
        assertTrue(read(J + "init/ModItems.java").contains("VehicleItems.register();"));
        assertTrue(read(J + "init/ModGroups.java").contains("output.accept(VehicleItems.SKATEBOARD);"));
        assertTrue(read(J + "TotalityClient.java").contains("EntityRenderers.register(ModEntities.SKATEBOARD, SkateboardRenderer::new);"));
        String mixins = read("src/main/resources/totality.mixins.json");
        assertTrue(mixins.contains("\"client.AvatarRendererSkateboardMixin\"") && mixins.contains("\"client.HumanoidModelSkateboardMixin\""));
        String lang = Files.readString(GEN.resolve("lang/en_us.json"));
        assertTrue(lang.contains("\"item.totality.skateboard\": \"Skateboard\"") && lang.contains("\"entity.totality.skateboard\": \"Skateboard\""));
        assertTrue(Files.exists(GEN.resolve("items/skateboard.json")) && Files.exists(GEN.resolve("models/item/skateboard.json")));
        assertFalse(read(J + "init/items/VehicleItems.java").contains("Recipe"), "development-accessible only: no recipe yet");
    }

    @Test
    void fourSeparatelySkinnableTexturesInTheReferencePalette() throws Exception {
        for (String part : new String[]{"deck", "grip", "trucks", "wheels"}) {
            BufferedImage im = image("textures/entity/skateboard/" + part + ".png");
            assertEquals(64, im.getWidth(), part);
            assertEquals(64, im.getHeight(), part);
        }
        assertTrue(contains(image("textures/entity/skateboard/grip.png"), 0x2D2D2D), "grip #2D2D2D");
        assertTrue(contains(image("textures/entity/skateboard/deck.png"), 0xCBA36B), "deck wood #CBA36B");
        assertTrue(contains(image("textures/entity/skateboard/trucks.png"), 0xBCBCBC), "trucks #BCBCBC");
        assertTrue(contains(image("textures/entity/skateboard/wheels.png"), 0xE6E0D0), "wheels #E6E0D0");
        BufferedImage icon = image("textures/item/skateboard.png");
        assertEquals(16, icon.getWidth());
        assertEquals(0, icon.getRGB(0, 0) >>> 24, "transparent background");
    }

    @Test
    void cuboidsAndPlanesOnlyWithSeparateRegionsAndCulledFaces() throws Exception {
        String model = read(J + "client/renderer/entity/skateboard/SkateboardModel.java");
        assertFalse(model.contains("Mesh ") || model.contains("addVertex"), "cuboids and planes only");
        for (String part : new String[]{"\"deck\"", "\"grip\"", "\"truck_mounts\"", "\"hangers\"", "\"wheels\"", "\"deck_frame\""}) {
            assertTrue(model.contains(part), part);
        }
        assertTrue(model.contains("LayerDefinition.create(mesh, 64, 64)"));
        String renderer = read(J + "client/renderer/entity/skateboard/SkateboardRenderer.java");
        assertTrue(renderer.contains("RenderTypes.entityCutoutCull("), "one-sided grip planes need back-face culling");
        for (String tex : new String[]{"texture(\"deck\")", "texture(\"grip\")", "texture(\"trucks\")", "texture(\"wheels\")"}) {
            assertTrue(renderer.contains(tex), tex);
        }
    }

    @Test
    void theRiderStandsOnTheGrip() {
        assertEquals(6.05F / 16.0F, SkateboardEntity.GRIP_TOP, 1e-6, "feet on the grip, 6.05 px up");
        assertTrue(SkateboardEntity.STANCE_YAW > 45.0F && SkateboardEntity.STANCE_YAW < 90.0F, "side-on stance");
    }
}
